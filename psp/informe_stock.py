"""Hijo del informe de stock.

Lee el stock minimo por la entrada estandar, consulta PostgreSQL y escribe el
informe por la salida estandar. Los errores van a la salida de errores.

Codigos de salida:
    0 -> correcto
    1 -> error de base de datos
    2 -> dato de entrada no valido
    3 -> parada pedida por el padre (senal)
"""
import signal
import sys

import pg8000.dbapi

from config import DB

SQL = (
    "SELECT p.nombre, SUM(s.cantidad) AS total "
    "FROM productos p JOIN stock s ON s.id_producto = p.id_producto "
    "GROUP BY p.nombre HAVING SUM(s.cantidad) < %s ORDER BY total ASC"
)

conexion = None


def escribir_error(texto):
    """Escribe un mensaje de error y vacia el bufer para que el padre lo reciba ya."""
    print(texto, file=sys.stderr)
    sys.stderr.flush()


def cerrar_conexion():
    """Cierra la conexion a la base de datos si esta abierta."""
    global conexion
    if conexion is not None:
        try:
            conexion.close()
        except Exception:
            pass
        conexion = None


def parada_ordenada(signum, frame):
    """Se ejecuta cuando el padre pide parar: cierra la conexion y sale limpiamente."""
    escribir_error("Hijo: parada pedida por el padre. Cerrando la conexion...")
    cerrar_conexion()
    sys.exit(3)


def registrar_senales():
    """Registra la parada ordenada para SIGTERM (Linux) y SIGBREAK (Windows)."""
    signal.signal(signal.SIGTERM, parada_ordenada)
    if hasattr(signal, "SIGBREAK"):
        signal.signal(signal.SIGBREAK, parada_ordenada)


def leer_minimo():
    """Lee el stock minimo que manda el padre por la entrada estandar."""
    texto = sys.stdin.readline().strip()
    try:
        return int(texto)
    except ValueError:
        escribir_error("ERROR: el stock minimo recibido no es un numero entero")
        sys.exit(2)


def consultar(minimo):
    """Devuelve las filas (nombre, total) de los productos con menos stock que el minimo."""
    global conexion
    conexion = pg8000.dbapi.connect(**DB)
    try:
        cursor = conexion.cursor()
        cursor.execute(SQL, (minimo,))
        return cursor.fetchall()
    finally:
        cerrar_conexion()


def main():
    registrar_senales()
    minimo = leer_minimo()

    try:
        filas = consultar(minimo)
    except (pg8000.dbapi.Error, OSError) as e:
        escribir_error("ERROR: no se pudo consultar la base de datos: " + str(e).strip())
        sys.exit(1)

    print(f"--- INFORME: PRODUCTOS CON MENOS DE {minimo} UNIDADES ---")
    if not filas:
        print("Ningun producto por debajo de ese stock")
    for nombre, total in filas:
        print(f"{nombre}: {total} unidades")
    sys.stdout.flush()


if __name__ == "__main__":
    main()