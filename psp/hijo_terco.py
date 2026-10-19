"""Hijo de prueba TERCO: ignora la senal de parada, asi que el padre tiene que forzarlo."""
import signal
import sys
import time

signal.signal(signal.SIGTERM, signal.SIG_IGN)
if hasattr(signal, "SIGBREAK"):
    signal.signal(signal.SIGBREAK, signal.SIG_IGN)

sys.stdin.readline()
print("Soy terco: no hago caso a las senales")
sys.stdout.flush()
for _ in range(60):
    time.sleep(1)
print("Tarea terminada")