"""Prueba de la cancelacion en dos fases con un tiempo limite de 3 segundos."""
from principal import lanzar_proceso

print("=== PRUEBA 1: hijo que obedece la senal ===")
lanzar_proceso("hijo_lento.py", "\n", 3)
print("El programa principal sigue funcionando\n")

print("=== PRUEBA 2: hijo que ignora la senal (hay que forzarlo) ===")
lanzar_proceso("hijo_terco.py", "\n", 3)
print("El programa principal sigue funcionando")