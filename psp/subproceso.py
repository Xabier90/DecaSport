"""
subproceso.py - PROGRAMA HIJO (subproceso) de DecaSport.

Lo lanza proceso.py con Popen. Protocolo de entrada/salida:
  stdin  <- petición (el padre cierra stdin al terminar de escribir)
  stdout -> resultado (una línea)
  stderr -> diagnóstico (PID/PPID) y errores
  código de salida: 0 ok | 1 uso incorrecto | 2 datos inválidos | 143 cierre por SIGTERM/SIGBREAK

Uso:  python subproceso.py <stock|albaran|factura|crm>

Para probar la cancelación desde el padre, la petición puede ser:
  __LENTO__  tarda 30 s pero atiende la señal de terminar -> basta la fase 1
  __SORDO__  tarda 30 s e IGNORA la señal de terminar     -> hace falta kill() (fase 2)
La señal de terminar es SIGTERM en Linux y SIGBREAK (CTRL_BREAK) en Windows.
"""
from __future__ import annotations

import os
import random
import signal
import sys
import time
from decimal import ROUND_HALF_UP, Decimal, InvalidOperation

import psycopg2

EXIT_OK = 0
EXIT_USO = 1
EXIT_DATOS = 2
EXIT_SIGTERM = 143          # 128 + 15: cierre ordenado tras SIGTERM
DURACION_SIMULADA = 30.0

IVA = Decimal("0.21")
CENTIMO = Decimal("0.01")

# Señales que el padre usa como "termina por las buenas" (SIGBREAK solo existe en Windows)
SENALES_TERMINAR = [s for s in (getattr(signal, "SIGTERM", None), getattr(signal, "SIGBREAK", None))
                    if s is not None]

# Mismos datos de conexión que el programa Java
DATOS_BD = dict(host="192.168.28.159", port=5432, dbname="decasport",
                user="postgres", password="decasport123")


class ErrorDatos(ValueError):
    """Los datos recibidos por stdin no tienen el formato esperado."""


# ----------------------------------------------------------------------
# Servicios (reciben texto y devuelven texto)
# ----------------------------------------------------------------------
def _enteros(datos: str, cantidad: int) -> list[int]:
    partes = datos.split()
    if len(partes) != cantidad:
        raise ErrorDatos(f"se esperaban {cantidad} valores y llegaron {len(partes)}")
    try:
        valores = [int(p) for p in partes]
    except ValueError as exc:
        raise ErrorDatos("los valores deben ser enteros") from exc
    if any(v < 0 for v in valores):
        raise ErrorDatos("no se admiten valores negativos")
    return valores


def servicio_stock(datos: str) -> str:
    """'stock_actual cantidad_pedida' -> 'OK nuevo_stock' o 'ERROR ...'."""
    actual, pedido = _enteros(datos, 2)
    if pedido > actual:
        return f"ERROR sin stock (hay {actual}, piden {pedido})"
    return f"OK {actual - pedido}"


def servicio_albaran(datos: str) -> str:
    """'id_producto cantidad'."""
    id_prod, cantidad = _enteros(datos, 2)
    return f"ALB-{random.randint(1000, 9999)} producto={id_prod} unidades={cantidad}"


def servicio_factura(datos: str) -> str:
    """'cantidad precio_unitario'. Usa Decimal para no perder céntimos."""
    partes = datos.split()
    if len(partes) != 2:
        raise ErrorDatos(f"se esperaban 2 valores y llegaron {len(partes)}")
    try:
        cantidad, precio = int(partes[0]), Decimal(partes[1])
    except (ValueError, InvalidOperation) as exc:
        raise ErrorDatos("cantidad o precio no válidos") from exc
    if cantidad <= 0 or not precio.is_finite() or precio < 0:
        raise ErrorDatos("cantidad o precio fuera de rango")
    base = (cantidad * precio).quantize(CENTIMO, ROUND_HALF_UP)
    iva = (base * IVA).quantize(CENTIMO, ROUND_HALF_UP)
    return f"FAC-{random.randint(1000, 9999)} base={base} iva={iva} total={base + iva}"


def servicio_crm(datos: str) -> str:
    """'id_cliente' -> busca el cliente en PostgreSQL."""
    (id_cliente,) = _enteros(datos, 1)
    try:
        con = psycopg2.connect(**DATOS_BD)
        try:
            with con.cursor() as cur:
                cur.execute("SELECT nombre, COALESCE(pais, '-') FROM clientes WHERE id_cliente = %s",
                            (id_cliente,))
                fila = cur.fetchone()
        finally:
            con.close()
    except psycopg2.Error as exc:
        raise ErrorDatos(f"no se pudo consultar la base de datos: {exc}") from exc
    if fila is None:
        return "ERROR cliente no encontrado"
    return f"{fila[0]} ({fila[1]})"


SERVICIOS = {
    "stock": servicio_stock,
    "albaran": servicio_albaran,
    "factura": servicio_factura,
    "crm": servicio_crm,
}


# ----------------------------------------------------------------------
# Entrada/salida y señales
# ----------------------------------------------------------------------
def _configurar_salida() -> None:
    """Fuerza UTF-8 en las tuberías para que padre e hijo coincidan en cualquier sistema."""
    for flujo in (sys.stdin, sys.stdout, sys.stderr):
        try:
            flujo.reconfigure(encoding="utf-8")
        except (AttributeError, ValueError):
            pass


def _configurar_senales(nombre: str) -> None:
    # Ctrl+C llega a todo el grupo de procesos: el hijo lo ignora y obedece al padre.
    signal.signal(signal.SIGINT, signal.SIG_IGN)

    def al_recibir_terminar(signum, frame):
        print(f"[{nombre}] {signal.Signals(signum).name} recibida: cierre ordenado",
              file=sys.stderr, flush=True)
        sys.exit(EXIT_SIGTERM)

    for senal in SENALES_TERMINAR:
        signal.signal(senal, al_recibir_terminar)


def _ignorar_terminar() -> None:
    for senal in SENALES_TERMINAR:
        signal.signal(senal, signal.SIG_IGN)


def _esperar(segundos: float) -> None:
    """Espera en tramos cortos: así un manejador de señal se ejecuta enseguida
    (en Windows time.sleep() largo no se interrumpe con SIGBREAK)."""
    fin = time.monotonic() + segundos
    while (restante := fin - time.monotonic()) > 0:
        time.sleep(min(0.1, restante))


def main(argv: list[str]) -> int:
    if len(argv) != 2 or argv[1] not in SERVICIOS:
        print(f"uso: subproceso.py <{'|'.join(SERVICIOS)}>", file=sys.stderr, flush=True)
        return EXIT_USO

    nombre = argv[1]
    _configurar_salida()
    _configurar_senales(nombre)

    # Diagnóstico: quién soy y quién es mi padre
    print(f"[{nombre}] PID={os.getpid()} PPID={os.getppid()}", file=sys.stderr, flush=True)

    datos = sys.stdin.read().strip()

    if datos == "__LENTO__":
        _esperar(DURACION_SIMULADA)
    elif datos == "__SORDO__":
        _ignorar_terminar()
        _esperar(DURACION_SIMULADA)
    else:
        time.sleep(random.uniform(0.2, 0.8))   # trabajo simulado

    try:
        resultado = SERVICIOS[nombre](datos)
    except ErrorDatos as exc:
        print(f"ERROR {exc}", file=sys.stderr, flush=True)
        return EXIT_DATOS

    sys.stdout.write(resultado + "\n")
    sys.stdout.flush()   # vaciado explícito: el padre recibe la línea sin esperar al cierre
    return EXIT_OK


if __name__ == "__main__":
    sys.exit(main(sys.argv))