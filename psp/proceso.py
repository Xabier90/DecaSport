"""
proceso.py - PROGRAMA PADRE (proceso) de DecaSport.

    python proceso.py              menú de consola + servidor de sockets
    python proceso.py --servidor   solo servidor de sockets (Ctrl+C o SIGTERM para parar)
    python proceso.py --cliente    cliente de consola que se conecta al servidor

Lanza subproceso.py (el hijo) con Popen y se comunica con él por PIPE:
  - Popen no bloquea; stdin/stdout/stderr redirigidos con PIPE; datos con communicate().
  - TimeoutExpired -> cancelación en 2 fases: señal suave (SIGTERM en Linux, CTRL_BREAK en
    Windows) y, si el hijo la ignora, kill().
  - SIGINT/SIGTERM -> apagado ordenado (servidor, hijos activos).
Además, ipc_pipe.py lanza un proceso por departamento (multiprocessing) y se comunica con
ellos por multiprocessing.Pipe (opciones 6 y 7 del menú y comando INFORME por sockets).

Los productos y el stock se leen de PostgreSQL (la misma base de datos que el programa Java).

Secciones: 1 Configuración · 2 Inventario · 3 Procesos hijo · 4 Negocio ·
           5 Servidor de sockets · 6 Cliente · 7 Menú · 8 Main y señales
"""
from __future__ import annotations

import argparse
import logging
import os
import signal
import socket
import subprocess
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path
from typing import Sequence

import psycopg2

import ipc_pipe

log = logging.getLogger("proceso")

# ======================================================================
# 1. CONFIGURACIÓN
# ======================================================================
PYTHON = sys.executable
EN_WINDOWS = os.name == "nt"
RUTA_HIJO = Path(__file__).resolve().with_name("subproceso.py")
RUTA_AUDITORIA = Path(__file__).resolve().with_name("auditoria.log")

HOST = "127.0.0.1"
PUERTO = 5000
MAX_LINEA = 1024            # bytes máximos por comando recibido
TIMEOUT_CLIENTE = 120.0     # segundos de inactividad antes de cerrar un cliente

SERVICIOS = ("stock", "albaran", "factura", "crm")
TIMEOUT_SERVICIO = 5.0      # segundos máximos de espera en communicate()
GRACIA_TERMINATE = 2.0      # segundos entre terminate() (fase 1) y kill() (fase 2)
TIMEOUT_APAGADO = 5.0

# Mismos datos de conexión que el programa Java
DATOS_BD = dict(host="192.168.28.159", port=5432, dbname="decasport",
                user="postgres", password="decasport123")


def configurar_logging(nivel: int = logging.INFO) -> None:
    formato = "%(asctime)s %(levelname)-7s [%(threadName)s] %(message)s"
    logging.basicConfig(level=nivel, format=formato, stream=sys.stderr, force=True)


_lock_auditoria = threading.Lock()


def auditar(evento: str) -> None:
    """Añade una línea a auditoria.log con flush() explícito (seguro entre hilos)."""
    try:
        with _lock_auditoria, open(RUTA_AUDITORIA, "a", encoding="utf-8") as f:
            f.write(f"{time.strftime('%Y-%m-%d %H:%M:%S')} {evento}\n")
            f.flush()
    except OSError as exc:
        log.error("no se pudo escribir la auditoría: %s", exc)


# ======================================================================
# 2. INVENTARIO (PostgreSQL, la misma base de datos que el Java)
# ======================================================================
@dataclass(frozen=True)
class Producto:
    nombre: str
    stock: int
    precio: Decimal


class Inventario:
    """Lee y cambia el stock en PostgreSQL."""

    def __init__(self) -> None:
        # RLock: una operación compuesta que ya tiene el lock puede llamar a otras.
        self.lock = threading.RLock()
        self._con = psycopg2.connect(**DATOS_BD)

    def obtener(self, id_prod: int) -> Producto | None:
        with self.lock, self._con, self._con.cursor() as cur:
            cur.execute(
                "SELECT p.nombre, COALESCE(SUM(s.cantidad), 0), p.precio "
                "FROM productos p LEFT JOIN stock s ON s.id_producto = p.id_producto "
                "WHERE p.id_producto = %s GROUP BY p.id_producto", (id_prod,))
            fila = cur.fetchone()
        if fila is None:
            return None
        return Producto(fila[0], int(fila[1]), Decimal(str(fila[2])))

    def fijar_stock(self, id_prod: int, stock: int) -> None:
        with self.lock:
            actual = self.obtener(id_prod)
            self.ajustar(id_prod, stock - actual.stock)

    def ajustar(self, id_prod: int, delta: int) -> None:
        if delta == 0:
            return
        with self.lock, self._con, self._con.cursor() as cur:
            if delta > 0:
                # Suma al primer almacén del producto (o crea uno en España)
                cur.execute(
                    "UPDATE stock SET cantidad = cantidad + %s WHERE id_stock = "
                    "(SELECT id_stock FROM stock WHERE id_producto = %s ORDER BY id_stock LIMIT 1)",
                    (delta, id_prod))
                if cur.rowcount == 0:
                    cur.execute("INSERT INTO stock (id_producto, almacen, cantidad) "
                                "VALUES (%s, 'España', %s)", (id_prod, delta))
            else:
                # Resta repartiendo entre almacenes, empezando por el que más tiene
                resto = -delta
                cur.execute("SELECT id_stock, cantidad FROM stock WHERE id_producto = %s "
                            "AND cantidad > 0 ORDER BY cantidad DESC FOR UPDATE", (id_prod,))
                for id_stock, cantidad in cur.fetchall():
                    if resto == 0:
                        break
                    quitar = min(resto, cantidad)
                    cur.execute("UPDATE stock SET cantidad = cantidad - %s WHERE id_stock = %s",
                                (quitar, id_stock))
                    resto -= quitar

    def cerrar(self) -> None:
        try:
            self._con.close()
        except psycopg2.Error:
            pass


# ======================================================================
# 3. PROCESOS HIJO (Popen + PIPE + communicate + cancelación en 2 fases)
# ======================================================================
class ErrorServicio(Exception):
    """No se pudo lanzar o comunicar con un subproceso."""


@dataclass(frozen=True)
class Resultado:
    servicio: str
    pid: int | None
    codigo: int | None
    salida: str
    errores: str
    cancelado: bool = False
    fase_cancelacion: str = ""   # "", "terminate" o "kill"

    @property
    def ok(self) -> bool:
        return self.codigo == 0 and not self.cancelado


class ServicioHijo:
    """Un subproceso con su ciclo de vida: iniciar -> comunicar -> cerrar."""

    _activos: set[ServicioHijo] = set()
    _bloqueo = threading.Lock()

    def __init__(self, nombre: str, timeout: float | None = None, gracia: float | None = None):
        if nombre not in SERVICIOS:
            raise ValueError(f"servicio desconocido: {nombre!r}")
        self.nombre = nombre
        self.timeout = TIMEOUT_SERVICIO if timeout is None else timeout
        self.gracia = GRACIA_TERMINATE if gracia is None else gracia
        self._proc: subprocess.Popen[str] | None = None
        self._comunicado = False

    @property
    def pid(self) -> int | None:
        return self._proc.pid if self._proc else None

    def iniciar(self) -> None:
        """Lanza el subproceso sin esperar a que termine (no bloqueante)."""
        if self._proc is not None:
            raise ErrorServicio(f"'{self.nombre}' ya está iniciado")
        try:
            self._proc = subprocess.Popen(
                [PYTHON, str(RUTA_HIJO), self.nombre],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                encoding="utf-8",
                errors="replace",
                # En Windows, grupo propio: permite mandarle CTRL_BREAK solo a este hijo
                creationflags=subprocess.CREATE_NEW_PROCESS_GROUP if EN_WINDOWS else 0,
            )
        except OSError as exc:
            raise ErrorServicio(f"no se pudo lanzar '{self.nombre}': {exc}") from exc
        with self._bloqueo:
            self._activos.add(self)
        log.info("hijo '%s' lanzado con PID=%s", self.nombre, self._proc.pid)

    @staticmethod
    def _senal_suave(proc: subprocess.Popen[str]) -> None:
        """Fase 1: pide al hijo que termine por las buenas (el hijo puede ignorarla).

        Linux: terminate() = SIGTERM. Windows: terminate() mata sin avisar, así que la
        fase 1 manda CTRL_BREAK_EVENT (SIGBREAK en el hijo); kill() queda para la fase 2.
        """
        if EN_WINDOWS:
            try:
                proc.send_signal(signal.CTRL_BREAK_EVENT)
                return
            except (OSError, ValueError) as exc:
                log.debug("CTRL_BREAK no disponible (%s): se usa terminate()", exc)
        proc.terminate()

    def sigue_vivo(self) -> bool:
        return self._proc is not None and self._proc.poll() is None

    def comunicar(self, entrada: str) -> Resultado:
        """Envía 'entrada' por stdin y recoge stdout/stderr con communicate()."""
        if self._proc is None:
            raise ErrorServicio(f"'{self.nombre}' no está iniciado")
        if self._comunicado:
            raise ErrorServicio(f"'{self.nombre}' ya se comunicó (un uso por proceso)")
        self._comunicado = True
        proc = self._proc
        cancelado, fase = False, ""

        try:
            salida, errores = proc.communicate(input=entrada, timeout=self.timeout)
        except subprocess.TimeoutExpired:
            log.warning("'%s' (PID=%s) superó %.1f s", self.nombre, proc.pid, self.timeout)
            salida, errores, fase = self._cancelar(proc)
            cancelado = True
        except OSError as exc:
            self.cerrar()
            raise ErrorServicio(f"fallo de E/S con '{self.nombre}': {exc}") from exc
        except BaseException:
            self.cerrar()          # KeyboardInterrupt, SystemExit...: nunca dejamos huérfanos
            raise

        self._desregistrar()
        for linea in (errores or "").strip().splitlines():
            log.info("stderr[%s]: %s", self.nombre, linea)
        return Resultado(self.nombre, proc.pid, proc.returncode,
                         (salida or "").strip(), (errores or "").strip(), cancelado, fase)

    def _cancelar(self, proc: subprocess.Popen[str]) -> tuple[str, str, str]:
        """Tras un TimeoutExpired: fase 1 señal suave; si no responde, fase 2 kill()."""
        fase = "terminate"
        log.warning("fase 1: señal de terminar a '%s' (PID=%s)", self.nombre, proc.pid)
        try:
            self._senal_suave(proc)
        except OSError as exc:
            log.debug("terminate() ya no aplica: %s", exc)
        try:
            salida, errores = proc.communicate(timeout=self.gracia)
        except subprocess.TimeoutExpired:
            fase = "kill"
            log.error("fase 2: '%s' (PID=%s) ignora terminate(); kill()", self.nombre, proc.pid)
            try:
                proc.kill()
            except OSError as exc:
                log.debug("kill() ya no aplica: %s", exc)
            try:
                salida, errores = proc.communicate(timeout=self.gracia)
            except subprocess.TimeoutExpired:
                log.critical("'%s' (PID=%s) no muere tras kill()", self.nombre, proc.pid)
                salida, errores = "", ""
        return salida or "", errores or "", fase

    def cerrar(self, cerrar_tuberias: bool = True) -> None:
        """Cierre idempotente: terminate -> kill.

        cerrar_tuberias=False se usa si OTRO hilo está dentro de communicate(): cerrarle las
        tuberías por debajo daría 'Bad file descriptor'. Al morir el hijo, ese communicate()
        detecta EOF y termina solo.
        """
        proc = self._proc
        if proc is None:
            return
        try:
            if proc.poll() is None:
                try:
                    self._senal_suave(proc)
                    proc.wait(timeout=self.gracia)
                except subprocess.TimeoutExpired:
                    log.error("'%s' (PID=%s) ignora terminate(); kill()", self.nombre, proc.pid)
                    try:
                        proc.kill()
                        proc.wait(timeout=self.gracia)
                    except (OSError, subprocess.TimeoutExpired) as exc:
                        log.critical("no se pudo matar '%s' (PID=%s): %s", self.nombre, proc.pid, exc)
                except OSError:
                    pass           # ya había terminado
        finally:
            if cerrar_tuberias:
                for tuberia in (proc.stdin, proc.stdout, proc.stderr):
                    if tuberia and not tuberia.closed:
                        try:
                            tuberia.close()
                        except OSError:
                            pass
            self._desregistrar()

    def __enter__(self) -> ServicioHijo:
        return self

    def __exit__(self, *exc_info) -> None:
        self.cerrar()

    def _desregistrar(self) -> None:
        with self._bloqueo:
            self._activos.discard(self)

    @classmethod
    def terminar_todos(cls) -> int:
        """Cierra todos los hijos vivos (apagado por SIGINT/SIGTERM)."""
        with cls._bloqueo:
            pendientes = list(cls._activos)
        for hijo in pendientes:
            log.warning("apagado: cerrando '%s' (PID=%s)", hijo.nombre, hijo.pid)
            hijo.cerrar(cerrar_tuberias=False)   # el hilo propietario cierra sus tuberías
        return len(pendientes)


def ejecutar(nombre: str, entrada: str, timeout: float | None = None,
             gracia: float | None = None) -> Resultado:
    """Lanza un hijo, intercambia datos y lo cierra. Lanza ErrorServicio si falla el lanzamiento."""
    with ServicioHijo(nombre, timeout, gracia) as hijo:
        hijo.iniciar()
        return hijo.comunicar(entrada)


def ejecutar_en_paralelo(peticiones: Sequence[tuple[str, str]]) -> list[Resultado]:
    """Ejecuta varios servicios a la vez; los plazos corren en paralelo, no en serie."""

    def tarea(peticion: tuple[str, str]) -> Resultado:
        nombre, entrada = peticion
        try:
            return ejecutar(nombre, entrada)
        except ErrorServicio as exc:
            return Resultado(nombre, None, None, "", str(exc))

    with ThreadPoolExecutor(max_workers=max(1, len(peticiones))) as pool:
        return list(pool.map(tarea, peticiones))


# ======================================================================
# 4. NEGOCIO
# ======================================================================
AYUDA = "ERROR comando no válido (STOCK id | VENDER id cantidad | CLIENTE id | INFORME | SALIR)"


def _motivo(r: Resultado) -> str:
    if r.cancelado:
        return f"'{r.servicio}' cancelado por timeout (fase {r.fase_cancelacion})"
    if r.errores:
        return r.errores.splitlines()[-1]
    return f"'{r.servicio}' terminó con código {r.codigo}"


class Negocio:
    def __init__(self, inventario: Inventario) -> None:
        self.inventario = inventario

    def consultar_stock(self, id_prod: int) -> str:
        producto = self.inventario.obtener(id_prod)
        if producto is None:
            return "ERROR producto no existe"
        return f"{producto.nombre}: {producto.stock} uds."

    def vender(self, id_prod: int, cantidad: int) -> str:
        if cantidad <= 0:
            return "ERROR la cantidad debe ser positiva"
        if self.inventario.obtener(id_prod) is None:
            return "ERROR producto no existe"

        # Sección crítica: el stock no puede cambiar entre la consulta y la actualización.
        with self.inventario.lock:
            producto = self.inventario.obtener(id_prod)
            r_stock = ejecutar("stock", f"{producto.stock} {cantidad}")
            if not r_stock.ok:
                return f"ERROR servicio de stock: {_motivo(r_stock)}"
            if not r_stock.salida.startswith("OK"):
                return r_stock.salida
            self.inventario.fijar_stock(id_prod, int(r_stock.salida.split()[1]))

        # Albarán y factura son independientes: se ejecutan a la vez en dos procesos.
        albaran, factura = ejecutar_en_paralelo([
            ("albaran", f"{id_prod} {cantidad}"),
            ("factura", f"{cantidad} {producto.precio}"),
        ])
        if not (albaran.ok and factura.ok):
            self.inventario.ajustar(id_prod, +cantidad)          # compensación: se devuelve el stock
            fallos = "; ".join(_motivo(r) for r in (albaran, factura) if not r.ok)
            auditar(f"VENTA_REVERTIDA producto={id_prod} cantidad={cantidad} motivo={fallos}")
            return f"ERROR venta revertida: {fallos}"

        auditar(f"VENTA producto={id_prod} cantidad={cantidad} "
                f"{albaran.salida.split()[0]} {factura.salida.split()[0]}")
        return f"OK venta | {albaran.salida} | {factura.salida}"

    def consultar_cliente(self, id_cliente: int) -> str:
        r = ejecutar("crm", str(id_cliente))
        if not r.ok:
            return f"ERROR servicio CRM: {_motivo(r)}"
        return r.salida if r.salida.startswith("ERROR") else f"OK {r.salida}"

    def probar_cancelacion(self, modo: str) -> str:
        """Demuestra el protocolo de 2 fases: 'lento' (basta terminate) o 'sordo' (hace falta kill)."""
        peticion = "__LENTO__" if modo == "lento" else "__SORDO__"
        r = ejecutar("stock", peticion, timeout=1.5, gracia=1.0)
        auditar(f"PRUEBA_CANCELACION modo={modo} fase={r.fase_cancelacion} codigo={r.codigo}")
        return f"Cancelado en fase '{r.fase_cancelacion}' (PID={r.pid}, código de salida={r.codigo})"

    def informe_paralelo(self) -> str:
        """Un proceso por departamento, comunicados con el padre por multiprocessing.Pipe."""
        resultados = ipc_pipe.informe_paralelo(DATOS_BD, timeout=TIMEOUT_SERVICIO,
                                               gracia=GRACIA_TERMINATE)
        correctos = sum(r.ok for r in resultados)
        auditar(f"INFORME_PIPE procesos={len(resultados)} correctos={correctos}")
        return ipc_pipe.formatear(resultados)

    def probar_timeout_pipe(self) -> str:
        """El trabajador de 'ventas' se queda bloqueado: el padre lo cancela en 2 fases."""
        resultados = ipc_pipe.informe_paralelo(DATOS_BD, timeout=1.5, gracia=1.0,
                                               departamentos=["inventario", "ventas"],
                                               dormir={"ventas": 30.0})
        for r in resultados:
            if r.fase_cancelacion:
                auditar(f"PRUEBA_TIMEOUT_PIPE departamento={r.departamento} "
                        f"fase={r.fase_cancelacion} codigo={r.codigo_salida}")
        return ipc_pipe.formatear(resultados)

    def despachar(self, linea: str) -> str:
        """Interpreta un comando del protocolo de sockets. Nunca lanza: devuelve siempre una línea."""
        partes = linea.split()
        if not partes:
            return AYUDA
        orden, args = partes[0].upper(), partes[1:]
        try:
            if orden == "STOCK" and len(args) == 1:
                return self.consultar_stock(int(args[0]))
            if orden == "VENDER" and len(args) == 2:
                return self.vender(int(args[0]), int(args[1]))
            if orden == "CLIENTE" and len(args) == 1:
                return self.consultar_cliente(int(args[0]))
            if orden == "INFORME" and not args:
                # el protocolo es de una línea por respuesta
                return self.informe_paralelo().replace("\n", " || ")
        except ValueError:
            return "ERROR los argumentos deben ser números enteros"
        except ErrorServicio as exc:
            log.error("fallo de servicio: %s", exc)
            return f"ERROR {exc}"
        except psycopg2.Error as exc:
            log.error("fallo de base de datos: %s", exc)
            return "ERROR de base de datos"
        except Exception:                                        # un hilo de cliente jamás debe morir
            log.exception("error inesperado al procesar %r", linea)
            return "ERROR interno del servidor"
        return AYUDA


# ======================================================================
# 5. SERVIDOR DE SOCKETS (un hilo por cliente)
# ======================================================================
class ServidorSockets:
    def __init__(self, negocio: Negocio, host: str = HOST, puerto: int = PUERTO) -> None:
        self._negocio = negocio
        self._direccion = (host, puerto)
        self._parar = threading.Event()
        self._socket: socket.socket | None = None
        self._hilo: threading.Thread | None = None
        self._conexiones: set[socket.socket] = set()
        self._bloqueo = threading.Lock()

    def iniciar(self) -> None:
        """Abre el puerto (lanza OSError si está ocupado) y acepta clientes en segundo plano."""
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            sock.bind(self._direccion)
            sock.listen()
        except OSError:
            sock.close()
            raise
        sock.settimeout(0.5)          # accept() no bloquea para siempre: así se puede parar
        self._socket = sock
        self._hilo = threading.Thread(target=self._aceptar, name="servidor", daemon=True)
        self._hilo.start()
        log.info("escuchando en %s:%s", *self._direccion)

    def _aceptar(self) -> None:
        assert self._socket is not None
        while not self._parar.is_set():
            try:
                conexion, direccion = self._socket.accept()
            except socket.timeout:
                continue
            except OSError:
                break                 # el socket se cerró en detener()
            with self._bloqueo:
                self._conexiones.add(conexion)
            threading.Thread(target=self._atender, args=(conexion, direccion),
                             name=f"cliente-{direccion[1]}", daemon=True).start()

    def _atender(self, conexion: socket.socket, direccion: tuple) -> None:
        log.info("cliente conectado: %s:%s", *direccion)
        conexion.settimeout(TIMEOUT_CLIENTE)
        try:
            with conexion, conexion.makefile("rw", encoding="utf-8", newline="\n") as f:
                while not self._parar.is_set():
                    linea = f.readline(MAX_LINEA)
                    if not linea:
                        break         # el cliente cerró la conexión
                    linea = linea.strip()
                    if linea.upper() == "SALIR":
                        break
                    f.write(self._negocio.despachar(linea) + "\n")
                    f.flush()
        except socket.timeout:
            log.info("cliente %s:%s inactivo: cerrado", *direccion)
        except OSError as exc:
            log.info("cliente %s:%s desconectado: %s", *direccion, exc)
        finally:
            with self._bloqueo:
                self._conexiones.discard(conexion)
            log.info("cliente desconectado: %s:%s", *direccion)

    def detener(self) -> None:
        self._parar.set()
        if self._socket is not None:
            self._socket.close()
        with self._bloqueo:
            conexiones = list(self._conexiones)
        for conexion in conexiones:   # desbloquea los hilos que esperan en readline()
            try:
                conexion.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
        if self._hilo is not None:
            self._hilo.join(timeout=TIMEOUT_APAGADO)
        log.info("servidor detenido")


# ======================================================================
# 6. CLIENTE DE CONSOLA
# ======================================================================
def ejecutar_cliente(host: str, puerto: int) -> int:
    try:
        conexion = socket.create_connection((host, puerto), timeout=5)
    except OSError as exc:
        print(f"No se pudo conectar con {host}:{puerto}: {exc}", file=sys.stderr)
        return 1

    conexion.settimeout(30)   # una venta puede tardar: 3 subprocesos
    with conexion, conexion.makefile("rw", encoding="utf-8", newline="\n") as f:
        print("Conectado a DecaSport. Comandos: STOCK id | VENDER id cantidad | CLIENTE id | INFORME | SALIR")
        while True:
            try:
                comando = input("> ").strip()
            except (EOFError, KeyboardInterrupt):
                comando = "SALIR"
            if not comando:
                continue
            try:
                f.write(comando + "\n")
                f.flush()
                if comando.upper() == "SALIR":
                    return 0
                respuesta = f.readline()
            except (OSError, ValueError) as exc:
                print(f"Conexión perdida: {exc}", file=sys.stderr)
                return 1
            if not respuesta:
                print("El servidor cerró la conexión.", file=sys.stderr)
                return 1
            print(respuesta.strip())


# ======================================================================
# 7. MENÚ DE CONSOLA
# ======================================================================
MENU = """
=== DecaSport ERP/CRM ===
1. Consultar stock
2. Realizar venta (stock + albarán + factura)
3. Consultar cliente (CRM)
4. Probar cancelación fase 1 (terminate)
5. Probar cancelación fase 2 (kill)
6. Informe en paralelo (un proceso por departamento + Pipe)
7. Probar timeout con Pipe (cancelación en 2 fases)
0. Salir"""


def _leer_entero(texto: str) -> int | None:
    try:
        return int(input(texto))
    except ValueError:
        print("Introduce un número entero válido")
        return None


def ejecutar_menu(negocio: Negocio) -> None:
    """Bucle del menú. Termina con la opción 0 o al cerrarse stdin (EOF)."""
    while True:
        print(MENU)
        try:
            opcion = input("Opción: ").strip()
            if opcion == "1":
                if (id_prod := _leer_entero("ID producto: ")) is not None:
                    print(negocio.consultar_stock(id_prod))
            elif opcion == "2":
                id_prod = _leer_entero("ID producto: ")
                cantidad = _leer_entero("Cantidad: ") if id_prod is not None else None
                if id_prod is not None and cantidad is not None:
                    print(negocio.vender(id_prod, cantidad))
            elif opcion == "3":
                if (id_cli := _leer_entero("ID cliente: ")) is not None:
                    print(negocio.consultar_cliente(id_cli))
            elif opcion == "4":
                print(negocio.probar_cancelacion("lento"))
            elif opcion == "5":
                print(negocio.probar_cancelacion("sordo"))
            elif opcion == "6":
                print(negocio.informe_paralelo())
            elif opcion == "7":
                print(negocio.probar_timeout_pipe())
            elif opcion == "0":
                return
            else:
                print("Opción no válida")
        except EOFError:
            return
        except psycopg2.Error as exc:
            print(f"Error de base de datos: {exc}")


# ======================================================================
# 8. MAIN Y SEÑALES
# ======================================================================
def _instalar_senales() -> None:
    """SIGINT y SIGTERM se convierten en KeyboardInterrupt en el hilo principal."""

    def manejador(signum, frame):
        raise KeyboardInterrupt(signal.Signals(signum).name)

    for nombre in ("SIGINT", "SIGTERM"):
        if (senal := getattr(signal, nombre, None)) is not None:
            signal.signal(senal, manejador)


def _ignorar_senales() -> None:
    """Durante el apagado no se admiten más interrupciones: el cierre debe completarse."""
    for nombre in ("SIGINT", "SIGTERM"):
        if (senal := getattr(signal, nombre, None)) is not None:
            signal.signal(senal, signal.SIG_IGN)


def _apagar(servidor: ServidorSockets, inventario: Inventario) -> None:
    pasos = (
        ("servidor", servidor.detener),
        ("hijos activos", lambda: log.info("hijos cerrados: %d", ServicioHijo.terminar_todos())),
        ("base de datos", inventario.cerrar),
    )
    for nombre, paso in pasos:        # un fallo en un paso no impide los siguientes
        try:
            paso()
        except Exception:
            log.exception("error al apagar %s", nombre)


def _argumentos(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="DecaSport ERP/CRM (PSP) - proceso padre")
    modo = parser.add_mutually_exclusive_group()
    modo.add_argument("--servidor", action="store_true", help="solo servidor, sin menú")
    modo.add_argument("--cliente", action="store_true", help="cliente de consola")
    parser.add_argument("--puerto", type=int, default=PUERTO)
    parser.add_argument("--debug", action="store_true", help="logs detallados")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = _argumentos(argv)
    if args.cliente:
        return ejecutar_cliente(HOST, args.puerto)

    configurar_logging(logging.DEBUG if args.debug else logging.INFO)
    log.info("proceso padre PID=%s PPID=%s", os.getpid(), os.getppid())

    # Conexión con la base de datos del programa Java
    try:
        inventario = Inventario()
    except psycopg2.Error as exc:
        log.error("no se pudo conectar a la base de datos decasport: %s", exc)
        return 1
    log.info("conectado a la base de datos decasport")

    _instalar_senales()
    negocio = Negocio(inventario)
    servidor = ServidorSockets(negocio, HOST, args.puerto)
    codigo = 0
    try:
        servidor.iniciar()
        if args.servidor:
            log.info("modo servidor: Ctrl+C o SIGTERM para detener")
            while True:
                time.sleep(0.5)
        else:
            ejecutar_menu(negocio)
    except KeyboardInterrupt as exc:
        log.info("interrupción recibida (%s): apagando", exc)
    except OSError as exc:
        log.error("no se pudo arrancar el servidor en el puerto %s: %s", args.puerto, exc)
        codigo = 1
    except Exception:
        log.exception("error fatal")
        codigo = 1
    finally:
        _ignorar_senales()
        _apagar(servidor, inventario)
    log.info("DecaSport cerrado")
    return codigo


if __name__ == "__main__":
    sys.exit(main())