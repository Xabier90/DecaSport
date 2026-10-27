import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

public class Informes {

    // ============================================================
    // MENU DE INFORMES DEL ADMINISTRADOR (puede ver todos)
    // ============================================================
    public static void menuInformes(Connection con) throws SQLException {
        while (true) {
            System.out.println("\n=== INFORMES ===");
            System.out.println("1. Informe general");
            System.out.println("2. Informe de CRM");
            System.out.println("3. Informe de Ventas");
            System.out.println("4. Informe de Compras");
            System.out.println("5. Informe de Inventario");
            System.out.println("6. Informe de Fabricacion");
            System.out.println("0. Volver");
            int op = Utilidades.leerEntero("Opcion: ");
            switch (op) {
                case 1 -> informeGeneral(con);
                case 2 -> informeCrm(con);
                case 3 -> informeVentas(con);
                case 4 -> informeCompras(con);
                case 5 -> informeInventario(con);
                case 6 -> informeFabricacion(con);
                case 0 -> {
                    return;
                }
                default -> System.out.println("Opcion no valida");
            }
        }
    }

    // ============================================================
    // INFORMES POR DEPARTAMENTO
    // ============================================================

    // CRM: clientes y oportunidades de venta
    public static void informeCrm(Connection con) throws SQLException {
        if (!Usuarios.exigir("crm")) return;
        cabecera("INFORME DE CRM");
        tabla(con, "CLIENTES POR PAIS",
                "SELECT COALESCE(pais, '(sin pais)') AS pais, COUNT(*) AS clientes "
                + "FROM clientes GROUP BY 1 ORDER BY 2 DESC",
                "Todavia no hay clientes");
        conversionOportunidades(con);
        tabla(con, "OPORTUNIDADES POR ESTADO",
                "SELECT estado, COUNT(*) AS cantidad FROM oportunidades GROUP BY estado ORDER BY 2 DESC",
                "Todavia no hay oportunidades");
        tabla(con, "RANKING DE COMERCIALES (oportunidades aceptadas)",
                "SELECT COALESCE(u.nombre_usuario, '-') AS comercial, COUNT(*) AS total, "
                + "COUNT(*) FILTER (WHERE o.estado = 'Aceptada') AS aceptadas "
                + "FROM oportunidades o LEFT JOIN usuarios u ON u.id_usuario = o.id_usuario "
                + "GROUP BY 1 ORDER BY 3 DESC, 2 DESC",
                "Todavia no hay oportunidades");
        tabla(con, "CLIENTES SIN NINGUN PEDIDO (para contactar)",
                "SELECT c.id_cliente, c.nombre, COALESCE(c.email, '-') AS email FROM clientes c "
                + "WHERE NOT EXISTS (SELECT 1 FROM pedidos p WHERE p.id_cliente = c.id_cliente) "
                + "ORDER BY c.id_cliente",
                "Todos los clientes tienen algun pedido");
        pie();
    }

    // VENTAS: pedidos y facturacion
    public static void informeVentas(Connection con) throws SQLException {
        if (!Usuarios.exigir("ventas")) return;
        cabecera("INFORME DE VENTAS");
        resumenFacturacion(con);
        ventasPorMes(con);
        productosMasVendidos(con);
        tabla(con, "PEDIDOS POR ESTADO",
                "SELECT COALESCE(estado, '-') AS estado, COUNT(*) AS pedidos FROM pedidos GROUP BY 1 ORDER BY 2 DESC",
                "Todavia no hay pedidos");
        tabla(con, "PEDIDOS DE CLIENTES ESPERANDO STOCK",
                "SELECT p.id_pedido AS pedido, c.nombre AS cliente, p.fecha, pr.nombre AS producto, "
                + "l.cantidad AS pedidas FROM pedidos p JOIN clientes c ON c.id_cliente = p.id_cliente "
                + "JOIN lineas_pedido l ON l.id_pedido = p.id_pedido JOIN productos pr ON pr.id_producto = l.id_producto "
                + "WHERE p.estado = '" + Inventario.ESPERANDO + "' ORDER BY p.id_pedido",
                "No hay pedidos esperando stock");
        tabla(con, "TOP 5 CLIENTES POR FACTURACION",
                "SELECT c.nombre AS cliente, COUNT(f.id_factura) AS facturas, ROUND(SUM(f.total)::numeric, 2) AS total_eur "
                + "FROM facturas f JOIN pedidos p ON p.id_pedido = f.id_pedido "
                + "JOIN clientes c ON c.id_cliente = p.id_cliente "
                + "GROUP BY c.id_cliente, c.nombre ORDER BY 3 DESC LIMIT 5",
                "Todavia no hay facturas");
        pie();
    }

    // COMPRAS: pedidos de compra y proveedores
    public static void informeCompras(Connection con) throws SQLException {
        if (!Usuarios.exigir("compras")) return;
        cabecera("INFORME DE COMPRAS");
        tabla(con, "PEDIDOS DE COMPRA POR ESTADO",
                "SELECT COALESCE(estado, '-') AS estado, COUNT(*) AS pedidos, SUM(cantidad) AS unidades "
                + "FROM compras GROUP BY 1 ORDER BY 2 DESC",
                "Todavia no hay pedidos de compra");
        tabla(con, "UNIDADES COMPRADAS POR PROVEEDOR",
                "SELECT COALESCE(pv.nombre, '-') AS proveedor, COUNT(*) AS pedidos, SUM(c.cantidad) AS unidades "
                + "FROM compras c LEFT JOIN proveedores pv ON pv.id_proveedor = c.id_proveedor "
                + "GROUP BY 1 ORDER BY 3 DESC",
                "Todavia no hay pedidos de compra");
        tabla(con, "PEDIDOS DE COMPRA POR MES",
                "SELECT TO_CHAR(fecha, 'YYYY-MM') AS mes, COUNT(*) AS pedidos, SUM(cantidad) AS unidades "
                + "FROM compras GROUP BY 1 ORDER BY 1",
                "Todavia no hay pedidos de compra");
        tabla(con, "PRODUCTOS AGOTADOS QUE HAY QUE PEDIR AL PROVEEDOR",
                "SELECT p.nombre AS producto, COALESCE(pv.nombre, '(sin proveedor)') AS proveedor, "
                + "CASE WHEN EXISTS (SELECT 1 FROM compras c WHERE c.id_producto = p.id_producto "
                + "AND c.id_componente IS NULL AND c.estado IN ('Pendiente', 'Aceptada')) "
                + "THEN 'si' ELSE 'no' END AS pedido_en_camino "
                + "FROM productos p LEFT JOIN proveedores pv ON pv.id_proveedor = p.id_proveedor "
                + "WHERE NOT EXISTS (SELECT 1 FROM componentes_fabricacion cf WHERE cf.id_producto = p.id_producto) "
                + "AND COALESCE((SELECT SUM(s.cantidad) FROM stock s WHERE s.id_producto = p.id_producto), 0) = 0 "
                + "ORDER BY p.nombre",
                "No hay productos agotados");
        tabla(con, "COMPONENTES QUE HAY QUE COMPRAR (bajo minimo)",
                "SELECT cf.nombre AS componente, cf.stock_actual AS stock, cf.stock_minimo AS minimo, "
                + "cf.stock_minimo - cf.stock_actual AS faltan, COALESCE(pv.nombre, '(sin proveedor)') AS proveedor "
                + "FROM componentes_fabricacion cf LEFT JOIN proveedores pv ON pv.id_proveedor = cf.id_proveedor "
                + "WHERE cf.stock_actual < cf.stock_minimo ORDER BY 4 DESC",
                "No hay que comprar nada: todo esta por encima del minimo");
        pie();
    }

    // INVENTARIO: stock de productos y componentes
    public static void informeInventario(Connection con) throws SQLException {
        if (!Usuarios.exigir("inventario")) return;
        cabecera("INFORME DE INVENTARIO");
        tabla(con, "RESUMEN DEL ALMACEN",
                "SELECT COUNT(DISTINCT p.id_producto) AS productos, COALESCE(SUM(s.cantidad), 0) AS unidades, "
                + "ROUND(COALESCE(SUM(s.cantidad * p.precio), 0)::numeric, 2) AS valor_stock_eur "
                + "FROM productos p LEFT JOIN stock s ON s.id_producto = p.id_producto",
                "No hay productos");
        tabla(con, "STOCK POR CATEGORIA",
                "SELECT COALESCE(p.categoria, '-') AS categoria, COALESCE(SUM(s.cantidad), 0) AS unidades "
                + "FROM productos p LEFT JOIN stock s ON s.id_producto = p.id_producto GROUP BY 1 ORDER BY 2 DESC",
                "No hay productos");
        tabla(con, "PRODUCTOS SIN STOCK",
                "SELECT p.id_producto, p.nombre FROM productos p "
                + "LEFT JOIN stock s ON s.id_producto = p.id_producto "
                + "GROUP BY p.id_producto, p.nombre HAVING COALESCE(SUM(s.cantidad), 0) = 0 ORDER BY p.id_producto",
                "Todos los productos tienen stock");
        componentesBajoMinimo(con);
        pie();
    }

    // FABRICACION: ordenes de montaje
    public static void informeFabricacion(Connection con) throws SQLException {
        if (!Usuarios.exigir("fabricacion")) return;
        cabecera("INFORME DE FABRICACION");
        tabla(con, "ORDENES POR ESTADO",
                "SELECT COALESCE(estado, '-') AS estado, COUNT(*) AS ordenes FROM fabricacion GROUP BY 1 ORDER BY 2 DESC",
                "Todavia no hay ordenes de fabricacion");
        tabla(con, "UNIDADES FABRICADAS POR PRODUCTO (ordenes terminadas)",
                "SELECT COALESCE(p.nombre, '-') AS producto, COUNT(*) AS ordenes, SUM(f.cantidad) AS unidades "
                + "FROM fabricacion f LEFT JOIN productos p ON p.id_producto = f.id_producto "
                + "WHERE f.estado = 'Terminada' GROUP BY 1 ORDER BY 3 DESC",
                "Todavia no se ha terminado ninguna orden");
        tabla(con, "ORDENES SIN TERMINAR",
                "SELECT f.id_montaje AS orden, p.nombre AS producto, f.cantidad AS unidades, "
                + "COALESCE('pedido ' || f.id_pedido, 'para stock') AS destino, f.fecha "
                + "FROM fabricacion f JOIN productos p ON p.id_producto = f.id_producto "
                + "WHERE f.estado NOT IN ('Terminada', 'Cancelada') ORDER BY f.id_montaje",
                "No hay ordenes pendientes");
        tabla(con, "ORDENES POR TIPO",
                "SELECT COALESCE(tipo, '-') AS tipo, COUNT(*) AS ordenes FROM fabricacion GROUP BY 1 ORDER BY 2 DESC",
                "Todavia no hay ordenes de fabricacion");
        tabla(con, "PEDIDOS ESPERANDO A FABRICACION",
                "SELECT id_pedido, fecha, estado FROM pedidos WHERE estado = 'En fabricacion' ORDER BY id_pedido",
                "No hay pedidos esperando");
        pie();
    }

    // ============================================================
    // FUNCIONES DE APOYO PARA IMPRIMIR
    // ============================================================
    private static void cabecera(String titulo) {
        System.out.println("\n========== " + titulo + " ==========");
    }

    private static void pie() {
        System.out.println("\n===================================================");
    }

    // Ejecuta una consulta y la imprime como tabla (los nombres de columna salen de la consulta)
    private static void tabla(Connection con, String titulo, String sql, String siVacio) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- " + titulo + " ---");
            ResultSetMetaData md = rs.getMetaData();
            int columnas = md.getColumnCount();
            StringBuilder cab = new StringBuilder();
            for (int i = 1; i <= columnas; i++) {
                if (i > 1) cab.append(" | ");
                cab.append(md.getColumnLabel(i).toUpperCase());
            }
            boolean hay = false;
            while (rs.next()) {
                if (!hay) {
                    System.out.println(cab);
                    hay = true;
                }
                StringBuilder fila = new StringBuilder();
                for (int i = 1; i <= columnas; i++) {
                    if (i > 1) fila.append(" | ");
                    String valor = rs.getString(i);
                    fila.append(valor == null ? "-" : valor);
                }
                System.out.println(fila);
            }
            if (!hay) System.out.println(siVacio);
        }
    }

    // Informe estadistico automatico: todos los datos salen de consultas a la base de datos
    public static void informeGeneral(Connection con) throws SQLException {
        if (!Usuarios.exigirAdmin()) return;
        System.out.println("\n========== INFORME ESTADISTICO DECASPORT ==========");
        resumenFacturacion(con);
        ventasPorMes(con);
        productosMasVendidos(con);
        conversionOportunidades(con);
        componentesBajoMinimo(con);
        System.out.println("\n===================================================");
    }

    // 1. Totales de facturacion: numero, suma, media y maximo
    private static void resumenFacturacion(Connection con) throws SQLException {
        String sql = "SELECT COUNT(*), COALESCE(SUM(total), 0), COALESCE(AVG(total), 0), "
                   + "COALESCE(MAX(total), 0) FROM facturas";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            System.out.println("\n--- RESUMEN DE FACTURACION ---");
            System.out.printf("Facturas emitidas: %d%n", rs.getInt(1));
            System.out.printf("Total facturado:   %.2f EUR%n", rs.getDouble(2));
            System.out.printf("Factura media:     %.2f EUR%n", rs.getDouble(3));
            System.out.printf("Factura mayor:     %.2f EUR%n", rs.getDouble(4));
        }
    }

    // 2. Facturacion agrupada por mes
    private static void ventasPorMes(Connection con) throws SQLException {
        String sql = "SELECT TO_CHAR(fecha, 'YYYY-MM') AS mes, COUNT(*), SUM(total) "
                   + "FROM facturas GROUP BY mes ORDER BY mes";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- FACTURACION POR MES ---");
            boolean hay = false;
            while (rs.next()) {
                hay = true;
                System.out.printf("%s | %d factura(s) | %.2f EUR%n",
                        rs.getString(1), rs.getInt(2), rs.getDouble(3));
            }
            if (!hay) System.out.println("Todavia no hay facturas");
        }
    }

    // 3. Los 5 productos con mas unidades pedidas
    private static void productosMasVendidos(Connection con) throws SQLException {
        String sql = "SELECT p.nombre, SUM(l.cantidad) AS unidades, SUM(l.cantidad * p.precio) AS importe "
                   + "FROM lineas_pedido l JOIN productos p ON p.id_producto = l.id_producto "
                   + "JOIN pedidos pe ON pe.id_pedido = l.id_pedido WHERE pe.estado <> 'Cancelado' "
                   + "GROUP BY p.id_producto, p.nombre ORDER BY unidades DESC LIMIT 5";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- TOP 5 PRODUCTOS MAS PEDIDOS ---");
            int puesto = 0;
            while (rs.next()) {
                puesto++;
                System.out.printf("%d. %s | %d uds. | %.2f EUR%n",
                        puesto, rs.getString(1), rs.getInt(2), rs.getDouble(3));
            }
            if (puesto == 0) System.out.println("Todavia no hay pedidos");
        }
    }

    // 4. CRM: porcentaje de oportunidades aceptadas y rechazadas
    private static void conversionOportunidades(Connection con) throws SQLException {
        String sql = "SELECT COUNT(*), "
                   + "COUNT(*) FILTER (WHERE estado = 'Aceptada'), "
                   + "COUNT(*) FILTER (WHERE estado = 'Rechazada') "
                   + "FROM oportunidades";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            int total = rs.getInt(1);
            int aceptadas = rs.getInt(2);
            int rechazadas = rs.getInt(3);
            System.out.println("\n--- CRM: OPORTUNIDADES DE VENTA ---");
            if (total == 0) {
                System.out.println("Todavia no hay oportunidades");
                return;
            }
            System.out.printf("Total: %d | Aceptadas: %d (%.1f %%) | Rechazadas: %d (%.1f %%)%n",
                    total, aceptadas, aceptadas * 100.0 / total,
                    rechazadas, rechazadas * 100.0 / total);
        }
    }

    // 5. Inventario: cuantos componentes estan por debajo del minimo
    private static void componentesBajoMinimo(Connection con) throws SQLException {
        String sql = "SELECT COUNT(*) FROM componentes_fabricacion WHERE stock_actual < stock_minimo";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            int bajos = rs.getInt(1);
            System.out.println("\n--- INVENTARIO ---");
            System.out.println(bajos == 0
                    ? "Todos los componentes estan por encima del stock minimo"
                    : bajos + " componente(s) por debajo del stock minimo: hay que comprar");
        }
    }
}