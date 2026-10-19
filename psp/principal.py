"""Proceso padre de DecaSport: lanza procesos hijo y controla su ciclo de vida.

- Hijo 1 (subprocess): comunicacion por entrada y salida estandar.
- Hijo 2 (multiprocessing): comunicacion por Pipe.
- Tiempo limite con cancelacion en dos fases (parada ordenada y, si no, forzada).
- Si el padre se interrumpe con Ctrl+C, cierra los hijos antes de salir.
"""
import logging
import os
import signal
import subprocess
import sys
from multiprocessing import Pipe, Process

from aviso_stock import vigilar_stock

TIEMPO_LIMITE = 10   # segundos que esperamos a un hijo
ESPERA_PARADA = 3    # segundos que damos a un hijo para parar por las buenas

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("principal")

# Hijos que siguen vivos: sirven para cerrarlos si el padre se interrumpe
hijos_popen = []
hijos_proceso = []


# ------------------------------------------------------------
# CANCELACION EN DOS FASES
# ------------------------------------------------------------
def parar_popen(hijo):
    """Fase 1: parada ordenada (senal). Fase 2: si no obedece, kill."""
    if hijo.poll() is not None:
        return
    log.warning("Pidiendo al hijo %s que pare...", hijo.pid)
    if os.name == "nt":
        hijo.send_signal(signal.CTRL_BREAK_EVENT)
    else:
        hijo.terminate()
    try:
        hijo.wait(timeout=ESPERA_PARADA)
        log.info("El hijo %s ha parado de forma ordenada", hijo.pid)
    except subprocess.TimeoutExpired:
        log.error("El hijo %s no obedece. Forzando cierre...", hijo.pid)
        hijo.kill()
        hijo.wait()


def parar_proceso(proceso):
    """Igual que parar_popen, pero para procesos de multiprocessing."""
    if not proceso.is_alive():
        return
    log.warning("Pidiendo al proceso %s que pare...", proceso.pid)
    proceso.terminate()
    proceso.join(timeout=ESPERA_PARADA)
    if proceso.is_alive():
        log.error("El proceso %s no obedece. Forzando cierre...", proceso.pid)
        proceso.kill()
        proceso.join()


def detener_todos():
    """Cierra todos los hijos que sigan vivos."""
    for hijo in list(hijos_popen):
        parar_popen(hijo)
        hijo.communicate()
        hijos_popen.remove(hijo)
    for proceso in list(hijos_proceso):
        parar_proceso(proceso)
        hijos_proceso.remove(proceso)


# ------------------------------------------------------------
# HIJO CON SUBPROCESS (stdin / stdout)
# ------------------------------------------------------------
def lanzar_proceso(script, entrada, limite):
    """Lanza un script como proceso hijo, le manda 'entrada' y devuelve su salida.

    Devuelve None si el hijo falla o supera el tiempo limite.
    """
    # En Windows el hijo necesita su propio grupo para poder recibir CTRL_BREAK
    flags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
    hijo = subprocess.Popen(
        [sys.executable, script],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        creationflags=flags,
    )
    hijos_popen.append(hijo)
    log.info("Hijo arrancado: %s (PID %s)", script, hijo.pid)

    try:
        salida, errores = hijo.communicate(input=entrada, timeout=limite)
    except subprocess.TimeoutExpired:
        log.warning("%s supera el limite de %s segundos", script, limite)
        parar_popen(hijo)
        hijo.communicate()
        hijos_popen.remove(hijo)
        return None

    hijos_popen.remove(hijo)
    if hijo.returncode != 0:
        log.error("%s termino con error (codigo %s): %s", script, hijo.returncode, errores.strip())
        return None
    return salida


def lanzar_informe(minimo):
    return lanzar_proceso("informe_stock.py", f"{minimo}\n", TIEMPO_LIMITE)


# ------------------------------------------------------------
# HIJO CON MULTIPROCESSING (Pipe)
# ------------------------------------------------------------
def lanzar_aviso_con_pipe(minimo, limite=TIEMPO_LIMITE):
    """Lanza el aviso de stock bajo y devuelve el mensaje (diccionario) que envia por el Pipe."""
    extremo_padre, extremo_hijo = Pipe()
    proceso = Process(target=vigilar_stock, args=(extremo_hijo, minimo))
    proceso.start()
    extremo_hijo.close()  # el padre solo lee: cerrar este extremo permite detectar si el hijo muere
    hijos_proceso.append(proceso)
    log.info("Proceso con Pipe arrancado (PID %s)", proceso.pid)

    try:
        if extremo_padre.poll(limite):
            mensaje = extremo_padre.recv()
            proceso.join(timeout=ESPERA_PARADA)
            parar_proceso(proceso)
            hijos_proceso.remove(proceso)
            return mensaje

        log.warning("El proceso con Pipe supera el limite de %s segundos", limite)
        parar_proceso(proceso)
        hijos_proceso.remove(proceso)
        return None
    except EOFError:
        log.error("El hijo cerro el Pipe sin enviar nada")
        parar_proceso(proceso)
        hijos_proceso.remove(proceso)
        return None
    finally:
        extremo_padre.close()


# ------------------------------------------------------------
# PROGRAMA PRINCIPAL
# ------------------------------------------------------------
def pedir_minimo():
    while True:
        texto = input("Stock minimo para el informe: ").strip()
        try:
            return int(texto)
        except ValueError:
            print("Escribe un numero entero")


def main():
    print("=== PROGRAMA PRINCIPAL ===")
    try:
        minimo = pedir_minimo()

        print("\n--- 1. Informe (hijo con subprocess) ---")
        resultado = lanzar_informe(minimo)
        if resultado is None:
            print("No se ha podido obtener el informe")
        else:
            print(resultado)

        print("--- 2. Aviso de stock bajo (hijo con Pipe) ---")
        mensaje = lanzar_aviso_con_pipe(minimo)
        if mensaje is None:
            print("No se ha podido obtener el aviso")
        elif mensaje["tipo"] == "resultado":
            productos = mensaje["productos"]
            print(f"AVISO: {len(productos)} producto(s) con poco stock: {', '.join(productos)}")
        else:
            print("El hijo ha comunicado un error: " + mensaje["detalle"])

        print("\nEl programa principal sigue funcionando")
    except KeyboardInterrupt:
        log.warning("Interrumpido con Ctrl+C. Cerrando los hijos...")
        sys.exit(130)
    finally:
        detener_todos()


if __name__ == "__main__":
    main()