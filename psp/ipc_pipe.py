"""
ipc_pipe.py - COMUNICACIÓN ENTRE PROCESOS CON multiprocessing.Pipe (DecaSport).

El proceso padre lanza un proceso trabajador por departamento (multiprocessing.Process)
y habla con cada uno por su propia tubería dúplex (multiprocessing.Pipe):

    padre ──("CONSULTAR",)──────────────▶ trabajador
    padre ◀──("LISTO", pid)───────────── trabajador   (al arrancar)
    padre ◀──("OK", dep, título, datos)── trabajador   (o "ERROR", dep, mensaje)
    padre ──("FIN",)────────────────────▶ trabajador
    padre ◀──("ADIOS", pid)───────────── trabajador   (cierre ordenado)

  - Los trabajadores consultan PostgreSQL A LA VEZ: el informe tarda lo que el más lento.
  - El padre espera con multiprocessing.connection.wait() (sin bucles de espera activa)
    y con un plazo máximo; si un trabajador no responde a tiempo, cancelación en 2 fases:
    terminate() y, si sigue vivo, kill().
  - Pase lo que pase (error, timeout, Ctrl+C) ningún proceso queda huérfano: el bloque
    finally cierra todas las tuberías y espera o mata a todos los trabajadores.
"""
from __future__ import annotations

import logging
import multiprocessing as mp
import os
import time
from dataclasses import dataclass, field
from multiprocessing.connection import Connection, wait

log = logging.getLogger("ipc_pipe")

# Departamento -> (título del dato, consulta SQL que devuelve UNA fila)
CONSULTAS: dict[str, tuple[str, str]] = {
    "crm": ("Oportunidades abiertas | aceptadas",
            "SELECT COUNT(*) FILTER (WHERE estado = 'Abierta'), "
            "COUNT(*) FILTER (WHERE estado = 'Aceptada') FROM oportunidades"),
    "ventas": ("Facturas emitidas | total facturado (EUR)",
               "SELECT COUNT(*), ROUND(COALESCE(SUM(total), 0)::numeric, 2) FROM facturas"),
    "compras": ("Pedidos de compra pendientes | unidades",
                "SELECT COUNT(*), COALESCE(SUM(cantidad), 0) FROM compras WHERE estado = 'Pendiente'"),
    "inventario": ("Unidades en stock | valor del stock (EUR)",
                   "SELECT COALESCE(SUM(s.cantidad), 0), "
                   "ROUND(COALESCE(SUM(s.cantidad * p.precio), 0)::numeric, 2) "
                   "FROM productos p LEFT JOIN stock s ON s.id_producto = p.id_producto"),
    "fabricacion": ("Órdenes en proceso | terminadas",
                    "SELECT COUNT(*) FILTER (WHERE estado = 'En proceso'), "
                    "COUNT(*) FILTER (WHERE estado = 'Terminada') FROM fabricacion"),
}

TIMEOUT_ARRANQUE = 10.0   # segundos máximos para que un trabajador diga "LISTO"


# ======================================================================
# 1. PROCESO TRABAJADOR (se ejecuta en otro proceso)
# ======================================================================
def trabajador(departamento: str, conexion: Connection, datos_bd: dict, dormir: float = 0.0) -> None:
    """Atiende órdenes del padre por la tubería hasta recibir "FIN".

    Debe estar al nivel del módulo: en Windows multiprocessing arranca el hijo con 'spawn'
    e importa este módulo para encontrar la función.
    """
    import psycopg2   # se importa en el hijo: cada proceso abre su propia conexión

    pid = os.getpid()
    try:
        conexion.send(("LISTO", pid))
        while True:
            orden = conexion.recv()          # bloquea hasta que el padre escribe
            if orden[0] == "FIN":
                conexion.send(("ADIOS", pid))
                return
            if orden[0] != "CONSULTAR":
                conexion.send(("ERROR", departamento, f"orden desconocida: {orden[0]!r}"))
                continue
            if dormir:                       # solo para probar el timeout del padre
                time.sleep(dormir)
            titulo, sql = CONSULTAS[departamento]
            try:
                con = psycopg2.connect(**datos_bd)
                try:
                    with con.cursor() as cur:
                        cur.execute(sql)
                        fila = cur.fetchone()
                finally:
                    con.close()
                conexion.send(("OK", departamento, titulo, tuple(str(v) for v in fila)))
            except psycopg2.Error as exc:
                conexion.send(("ERROR", departamento, f"base de datos: {str(exc).strip()}"))
    except (EOFError, BrokenPipeError, ConnectionResetError):
        pass                                 # el padre cerró su extremo: terminamos sin ruido
    except KeyboardInterrupt:
        pass                                 # Ctrl+C llega a todo el grupo: manda el padre
    finally:
        conexion.close()


# ======================================================================
# 2. LADO DEL PADRE
# ======================================================================
@dataclass
class ResultadoIPC:
    departamento: str
    pid: int | None = None
    ok: bool = False
    titulo: str = ""
    datos: tuple[str, ...] = ()
    error: str = ""
    fase_cancelacion: str = ""               # "", "terminate" o "kill"
    codigo_salida: int | None = None


@dataclass
class _Trabajador:
    departamento: str
    proceso: mp.Process
    conexion: Connection
    resultado: ResultadoIPC = field(init=False)

    def __post_init__(self) -> None:
        self.resultado = ResultadoIPC(self.departamento)


def _cancelar(t: _Trabajador, gracia: float) -> None:
    """Cancelación en 2 fases: terminate() -> espera 'gracia' -> kill()."""
    p = t.proceso
    if not p.is_alive():
        return
    t.resultado.fase_cancelacion = "terminate"
    log.warning("fase 1: terminate() a '%s' (PID=%s)", t.departamento, p.pid)
    p.terminate()
    p.join(gracia)
    if p.is_alive():
        t.resultado.fase_cancelacion = "kill"
        log.error("fase 2: '%s' (PID=%s) sigue vivo; kill()", t.departamento, p.pid)
        p.kill()
        p.join(gracia)


def _cerrar(t: _Trabajador, gracia: float) -> None:
    """Cierre ordenado: "FIN" -> esperar "ADIOS" -> join; si no responde, 2 fases."""
    try:
        if t.proceso.is_alive() and not t.resultado.fase_cancelacion:
            t.conexion.send(("FIN",))
            if t.conexion.poll(gracia):
                t.conexion.recv()            # ("ADIOS", pid)
    except (EOFError, BrokenPipeError, ConnectionResetError, OSError):
        pass
    finally:
        t.conexion.close()
    t.proceso.join(gracia)
    _cancelar(t, gracia)
    t.resultado.codigo_salida = t.proceso.exitcode


def informe_paralelo(datos_bd: dict, timeout: float = 5.0, gracia: float = 2.0,
                     departamentos: list[str] | None = None,
                     dormir: dict[str, float] | None = None) -> list[ResultadoIPC]:
    """Lanza un proceso por departamento, les pide su dato y recoge las respuestas por Pipe.

    timeout: plazo total para recibir todas las respuestas.
    dormir:  {departamento: segundos} para simular un trabajador bloqueado (pruebas).
    """
    departamentos = departamentos or list(CONSULTAS)
    dormir = dormir or {}
    trabajadores: list[_Trabajador] = []
    try:
        # 1. Crear tubería + proceso por departamento
        for dep in departamentos:
            extremo_padre, extremo_hijo = mp.Pipe(duplex=True)
            proceso = mp.Process(target=trabajador, name=f"ipc-{dep}",
                                 args=(dep, extremo_hijo, datos_bd, dormir.get(dep, 0.0)),
                                 daemon=True)
            proceso.start()
            extremo_hijo.close()             # el padre solo usa su extremo: así detecta EOF
            t = _Trabajador(dep, proceso, extremo_padre)
            trabajadores.append(t)

        # 2. Saludo inicial y orden de consulta
        for t in trabajadores:
            if not t.conexion.poll(TIMEOUT_ARRANQUE):
                t.resultado.error = "no arrancó a tiempo"
                continue
            mensaje = t.conexion.recv()      # ("LISTO", pid)
            t.resultado.pid = mensaje[1]
            log.info("trabajador '%s' listo con PID=%s", t.departamento, t.resultado.pid)
            t.conexion.send(("CONSULTAR",))

        # 3. Recoger respuestas según llegan, con plazo total
        pendientes = {t.conexion: t for t in trabajadores if not t.resultado.error}
        limite = time.monotonic() + timeout
        while pendientes:
            restante = limite - time.monotonic()
            if restante <= 0:
                break
            for conexion in wait(list(pendientes), timeout=restante):
                t = pendientes.pop(conexion)
                try:
                    respuesta = conexion.recv()
                except (EOFError, ConnectionResetError):
                    t.resultado.error = "el proceso terminó sin responder"
                    continue
                if respuesta[0] == "OK":
                    _, _, t.resultado.titulo, t.resultado.datos = respuesta
                    t.resultado.ok = True
                else:
                    t.resultado.error = respuesta[2]

        # 4. Los que no respondieron a tiempo: cancelación en 2 fases
        for t in pendientes.values():
            log.warning("'%s' (PID=%s) superó %.1f s", t.departamento, t.proceso.pid, timeout)
            t.resultado.error = f"sin respuesta en {timeout:.1f} s: cancelado"
            _cancelar(t, gracia)
    finally:
        # 5. Cierre de todo, también si hubo una excepción o Ctrl+C
        for t in trabajadores:
            _cerrar(t, gracia)
    return [t.resultado for t in trabajadores]


def formatear(resultados: list[ResultadoIPC]) -> str:
    lineas = ["--- INFORME EN PARALELO (multiprocessing.Pipe) ---"]
    for r in resultados:
        if r.ok:
            lineas.append(f"[{r.departamento.upper():<11}] PID={r.pid} | {r.titulo}: {' | '.join(r.datos)}")
        else:
            fase = f" (fase {r.fase_cancelacion})" if r.fase_cancelacion else ""
            lineas.append(f"[{r.departamento.upper():<11}] PID={r.pid} | ERROR {r.error}{fase}")
    return "\n".join(lineas)
