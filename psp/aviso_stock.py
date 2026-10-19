"""Hijo del aviso de stock bajo. Se ejecuta como proceso aparte (multiprocessing)
y se comunica con el padre por un Pipe, enviando mensajes en forma de diccionario:

    {"tipo": "resultado", "productos": [...]}
    {"tipo": "error", "detalle": "..."}
"""
import signal

import pg8000.dbapi

from config import DB

SQL = (
    "SELECT p.nombre, SUM(s.cantidad) AS total "
    "FROM productos p JOIN stock s ON s.id_producto = p.id_producto "
    "GROUP BY p.nombre HAVING SUM(s.cantidad) < %s ORDER BY total ASC"
)


def _parar(signum, frame):
    # Convierte la senal en una salida normal para que se ejecuten los 'finally'
    raise SystemExit(3)


def vigilar_stock(extremo, minimo):
    """Consulta el stock bajo y manda el resultado al padre por el Pipe."""
    signal.signal(signal.SIGTERM, _parar)
    conexion = None
    try:
        conexion = pg8000.dbapi.connect(**DB)
        cursor = conexion.cursor()
        cursor.execute(SQL, (minimo,))
        nombres = [fila[0] for fila in cursor.fetchall()]
        extremo.send({"tipo": "resultado", "productos": nombres})
    except (pg8000.dbapi.Error, OSError) as e:
        extremo.send({"tipo": "error", "detalle": str(e).strip()})
    finally:
        if conexion is not None:
            try:
                conexion.close()
            except Exception:
                pass
        extremo.close()