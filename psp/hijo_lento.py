"""Hijo de prueba que OBEDECE: al recibir la senal de parada, sale de forma ordenada."""
import signal
import sys
import time


def parar(signum, frame):
    print("Hijo lento: parada ordenada recibida", file=sys.stderr)
    sys.stderr.flush()
    sys.exit(3)


signal.signal(signal.SIGTERM, parar)
if hasattr(signal, "SIGBREAK"):
    signal.signal(signal.SIGBREAK, parar)

sys.stdin.readline()
print("Empezando una tarea muy larga...")
sys.stdout.flush()
for _ in range(60):
    time.sleep(1)
print("Tarea terminada")