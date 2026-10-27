import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class Inventario {

    // Un componente de fabricacion (fila de componentes_fabricacion)
    public record Comp(int id, String nombre, int necesaria, int stock, Integer idProveedor) {}

    // Estado de un pedido de cliente que espera a que llegue stock
    public static final String ESPERANDO = "Esperando stock";

    // Subconsultas para un producto 'p': stock total y unidades que esperan los pedidos de clientes
    private static final String STOCK_DE_P =
            "COALESCE((SELECT SUM(s.cantidad) FROM stock s WHERE s.id_producto = p.id_producto), 0)";
    private static final String RESERVADO_DE_P =
            "COALESCE((SELECT SUM(l.cantidad) FROM lineas_pedido l JOIN pedidos pe ON pe.id_pedido = l.id_pedido "
            + "WHERE pe.estado = '" + ESPERANDO + "' AND l.id_producto = p.id_producto), 0)";

    // ============================================================
    // VER DATOS
    // ============================================================
    public static void verProductos(Connection con) throws SQLException {
        String sql = "SELECT p.id_producto, p.nombre, p.categoria, p.precio, "
                   + STOCK_DE_P + ", " + RESERVADO_DE_P + " FROM productos p ORDER BY p.id_producto";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- PRODUCTOS ---");
            boolean hay = false;
            while (rs.next()) {
                hay = true;
                int stock = rs.getInt(5);
                int reservado = rs.getInt(6);
                String estado;
                if (stock == 0 && reservado == 0) estado = "AGOTADO (se puede reservar)";
                else if (reservado > 0) estado = "stock: " + stock + " | reservadas para pedidos en espera: " + reservado;
                else estado = "stock: " + stock;
                System.out.printf("%d. %s (%s) - %.2f EUR - %s%n",
                        rs.getInt(1), rs.getString(2), rs.getString(3), rs.getDouble(4), estado);
            }
            if (!hay) System.out.println("No hay productos");
        }
    }

    // Unidades de un producto que esperan los pedidos de clientes sin stock
    public static int reservado(Connection con, int idProducto) throws SQLException {
        return Utilidades.buscarId(con, "SELECT COALESCE(SUM(l.cantidad), 0) FROM lineas_pedido l "
                + "JOIN pedidos pe ON pe.id_pedido = l.id_pedido "
                + "WHERE pe.estado = '" + ESPERANDO + "' AND l.id_producto = ?", idProducto);
    }

    public static void verComponentes(Connection con) throws SQLException {
        String sql = "SELECT cf.id_componente, p.nombre, cf.nombre, COALESCE(cf.variante, '-'), "
                   + "cf.cantidad_necesaria, cf.stock_actual, cf.stock_minimo, "
                   + "COALESCE(pv.nombre, '(sin proveedor)') "
                   + "FROM componentes_fabricacion cf "
                   + "JOIN productos p ON p.id_producto = cf.id_producto "
                   + "LEFT JOIN proveedores pv ON pv.id_proveedor = cf.id_proveedor "
                   + "ORDER BY p.id_producto, cf.id_componente";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- COMPONENTES DE FABRICACION ---");
            boolean hay = false;
            while (rs.next()) {
                hay = true;
                int stock = rs.getInt(6);
                int minimo = rs.getInt(7);
                System.out.printf("%d. [%s] %s (%s) | necesarios por unidad: %d | stock: %d | minimo: %d | proveedor: %s%s%n",
                        rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), stock, minimo, rs.getString(8),
                        stock < minimo ? "   <-- BAJO MINIMO: hay que comprar" : "");
            }
            if (!hay) System.out.println("No hay componentes definidos");
        }
    }

    public static void verCompras(Connection con) throws SQLException {
        listarCompras(con, false);
    }

    // Lista los pedidos de compra. Con soloAbiertas=true, solo los que todavia no han llegado
    private static boolean listarCompras(Connection con, boolean soloAbiertas) throws SQLException {
        String sql = "SELECT c.id_compra, COALESCE(pv.nombre, '-'), "
                   + "CASE WHEN c.id_componente IS NULL THEN 'producto: ' || p.nombre "
                   + "ELSE 'pieza: ' || cf.nombre || ' (para ' || p.nombre || ')' END, "
                   + "c.cantidad, c.fecha, COALESCE(c.estado, '-') "
                   + "FROM compras c LEFT JOIN proveedores pv ON pv.id_proveedor = c.id_proveedor "
                   + "LEFT JOIN componentes_fabricacion cf ON cf.id_componente = c.id_componente "
                   + "LEFT JOIN productos p ON p.id_producto = c.id_producto "
                   + (soloAbiertas ? "WHERE c.estado IN ('Pendiente', 'Aceptada') " : "")
                   + "ORDER BY c.id_compra";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println(soloAbiertas ? "\n--- PEDIDOS DE COMPRA EN CAMINO ---" : "\n--- PEDIDOS DE COMPRA ---");
            boolean hay = false;
            while (rs.next()) {
                hay = true;
                System.out.printf("Compra %d | %s | %d x %s | %s | %s%n",
                        rs.getInt(1), rs.getString(2), rs.getInt(4), rs.getString(3),
                        rs.getString(5), rs.getString(6));
            }
            if (!hay) System.out.println(soloAbiertas ? "No hay pedidos de compra en camino" : "No hay pedidos de compra");
            return hay;
        }
    }

    public static void verFabricacion(Connection con) throws SQLException {
        listarOrdenes(con, false);
    }

    // Con soloAbiertas=true solo salen las ordenes que estan sin terminar
    private static boolean listarOrdenes(Connection con, boolean soloAbiertas) throws SQLException {
        String sql = "SELECT f.id_montaje, f.id_pedido, COALESCE(p.nombre, '-'), f.cantidad, "
                   + "COALESCE(u.nombre_usuario, '-'), f.fecha, COALESCE(f.estado, '-') "
                   + "FROM fabricacion f LEFT JOIN productos p ON p.id_producto = f.id_producto "
                   + "LEFT JOIN usuarios u ON u.id_usuario = f.id_usuario "
                   + (soloAbiertas ? "WHERE f.estado NOT IN ('Terminada', 'Cancelada') " : "")
                   + "ORDER BY f.id_montaje";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println(soloAbiertas ? "\n--- ORDENES DE FABRICACION SIN TERMINAR ---" : "\n--- ORDENES DE FABRICACION ---");
            boolean hay = false;
            while (rs.next()) {
                hay = true;
                String destino = rs.getString(2) == null ? "para stock" : "para el pedido " + rs.getString(2);
                System.out.printf("Orden %d | %d x %s | %s | creada por %s | %s | %s%n",
                        rs.getInt(1), rs.getInt(4), rs.getString(3), destino,
                        rs.getString(5), rs.getString(6), rs.getString(7));
            }
            if (!hay) System.out.println(soloAbiertas ? "No hay ordenes sin terminar" : "No hay ordenes de fabricacion");
            return hay;
        }
    }

    // Productos que tienen piezas definidas, con su stock y cuantas unidades se pueden montar ya
    private static boolean listarProductosFabricables(Connection con) throws SQLException {
        String sql = "SELECT p.id_producto, p.nombre, " + STOCK_DE_P + ", "
                   + "MIN(cf.stock_actual / cf.cantidad_necesaria), " + RESERVADO_DE_P + " "
                   + "FROM productos p JOIN componentes_fabricacion cf ON cf.id_producto = p.id_producto "
                   + "GROUP BY p.id_producto, p.nombre ORDER BY p.id_producto";
        System.out.println("\n--- PRODUCTOS QUE SE FABRICAN ---");
        boolean hay = false;
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                hay = true;
                int faltan = rs.getInt(5) - rs.getInt(3);
                System.out.printf("%d. %s | en stock: %d | se pueden montar ya: %d%s%n",
                        rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4),
                        faltan > 0 ? " | FALTAN " + faltan + " para pedidos de clientes en espera" : "");
            }
        }
        if (!hay) System.out.println("Ningun producto tiene piezas definidas (las define Inventario en 'Anadir un componente')");
        return hay;
    }

    // COMPRAS: productos que hay que pedir (agotados o que faltan para pedidos de clientes)
    // y piezas por debajo del minimo
    public static void verLoQueHayQueComprar(Connection con) throws SQLException {
        boolean hayProductos = false;
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(SQL_PRODUCTOS_A_PEDIR)) {
            System.out.println("\n--- PRODUCTOS QUE HAY QUE PEDIR AL PROVEEDOR ---");
            while (rs.next()) {
                hayProductos = true;
                System.out.printf("%d. %s | stock: %d | %s | proveedor: %s%s%n",
                        rs.getInt(1), rs.getString(2), rs.getInt(5), motivo(rs.getInt(5), rs.getInt(6)),
                        rs.getString(3), rs.getInt(4) > 0 ? " | ya hay un pedido de compra en camino" : "");
            }
            if (!hayProductos) System.out.println("No hay productos que pedir");
        }

        String sql = "SELECT cf.id_componente, p.nombre, cf.nombre, cf.stock_actual, cf.stock_minimo, "
                   + "cf.stock_minimo - cf.stock_actual, COALESCE(pv.nombre, '(sin proveedor)') "
                   + "FROM componentes_fabricacion cf "
                   + "JOIN productos p ON p.id_producto = cf.id_producto "
                   + "LEFT JOIN proveedores pv ON pv.id_proveedor = cf.id_proveedor "
                   + "WHERE cf.stock_actual < cf.stock_minimo ORDER BY 6 DESC";
        boolean hayPiezas = false;
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- PIEZAS DE FABRICACION BAJO MINIMO ---");
            while (rs.next()) {
                hayPiezas = true;
                System.out.printf("%d. [%s] %s | stock: %d | minimo: %d | faltan: %d | proveedor: %s%n",
                        rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getInt(5), rs.getInt(6), rs.getString(7));
            }
            if (!hayPiezas) System.out.println("Todas las piezas estan por encima del minimo");
        }

        if ((hayProductos || hayPiezas) && Utilidades.preguntarSiNo("\nQuieres hacer un pedido de compra ahora?")) {
            crearCompraManual(con);
        }
    }

    // Productos que no se fabrican y estan agotados o no llegan para los pedidos en espera.
    // Columnas: id, nombre, proveedor, compras en camino, stock, reservado
    private static final String SQL_PRODUCTOS_A_PEDIR =
            "SELECT * FROM (SELECT p.id_producto, p.nombre, COALESCE(pv.nombre, '(sin proveedor)'), "
            + "(SELECT COUNT(*) FROM compras c WHERE c.id_producto = p.id_producto "
            + "AND c.id_componente IS NULL AND c.estado IN ('Pendiente', 'Aceptada')) AS en_camino, "
            + STOCK_DE_P + " AS stock, " + RESERVADO_DE_P + " AS reservado "
            + "FROM productos p LEFT JOIN proveedores pv ON pv.id_proveedor = p.id_proveedor "
            + "WHERE NOT EXISTS (SELECT 1 FROM componentes_fabricacion cf WHERE cf.id_producto = p.id_producto)) t "
            + "WHERE stock = 0 OR reservado > stock ORDER BY id_producto";

    private static String motivo(int stock, int reservado) {
        if (reservado > stock) {
            return "FALTAN " + (reservado - stock) + " para pedidos de clientes en espera";
        }
        return "AGOTADO";
    }

    // Al entrar en Compras: avisa de lo que falta y no tiene pedido en camino, y pregunta si se pide
    public static void avisarAgotados(Connection con) throws SQLException {
        List<int[]> aPedir = new ArrayList<>();   // {id, unidades que faltan para pedidos}
        List<String> nombres = new ArrayList<>();
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(SQL_PRODUCTOS_A_PEDIR)) {
            while (rs.next()) {
                if (rs.getInt(4) == 0) {
                    aPedir.add(new int[]{rs.getInt(1), Math.max(0, rs.getInt(6) - rs.getInt(5))});
                    nombres.add(rs.getString(2) + " -> " + motivo(rs.getInt(5), rs.getInt(6)));
                }
            }
        }
        if (aPedir.isEmpty()) return;
        System.out.println("\n!! AVISO: hay " + aPedir.size() + " producto(s) que hay que pedir al proveedor:");
        for (String n : nombres) System.out.println("   - " + n);
        if (!Utilidades.preguntarSiNo("Quieres pedirlos ahora?")) return;
        for (int i = 0; i < aPedir.size(); i++) {
            if (Utilidades.preguntarSiNo("Pedir '" + nombres.get(i) + "'?")) {
                pedirAlProveedor(con, aPedir.get(i)[0], aPedir.get(i)[1]);
            }
        }
    }

    // FABRICACION: piezas que lleva un producto y cuantas unidades se pueden montar
    public static void verPiezasDeProducto(Connection con) throws SQLException {
        if (!listarProductosFabricables(con)) return;
        int idProducto = Utilidades.leerEntero("ID del producto (0 = cancelar): ");
        if (idProducto == 0) return;
        List<Comp> piezas = cargarComponentes(con, idProducto, false);
        if (piezas.isEmpty()) {
            System.out.println("Ese producto no tiene piezas definidas");
            return;
        }
        int sePuedenMontar = Integer.MAX_VALUE;
        System.out.println("\n--- PIEZAS PARA MONTAR 1 UNIDAD ---");
        for (Comp c : piezas) {
            System.out.printf("%s | necesarias: %d | en stock: %d | %s%n", c.nombre(), c.necesaria(), c.stock(),
                    c.stock() >= c.necesaria() ? "OK" : "FALTAN PIEZAS");
            sePuedenMontar = Math.min(sePuedenMontar, c.stock() / c.necesaria());
        }
        System.out.println("Con el stock actual se pueden montar " + sePuedenMontar + " unidad(es)");
    }

    public static void verProveedores(Connection con) throws SQLException {
        String sql = "SELECT id_proveedor, nombre, COALESCE(email, '-'), COALESCE(telefono, '-') "
                   + "FROM proveedores ORDER BY id_proveedor";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- PROVEEDORES ---");
            boolean hay = false;
            while (rs.next()) {
                hay = true;
                System.out.printf("%d. %s | %s | %s%n", rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4));
            }
            if (!hay) System.out.println("No hay proveedores");
        }
    }

    // ============================================================
    // PRODUCTOS (departamento de inventario o administrador)
    // ============================================================
    public static void anadirProducto(Connection con) throws SQLException {
        if (!Usuarios.exigir("inventario")) return;
        System.out.print("Nombre: ");
        String nombre = Utilidades.leerLinea().trim();
        System.out.print("Categoria: ");
        String categoria = Utilidades.leerLinea().trim();
        double precio = Utilidades.leerDecimal("Precio: ");
        System.out.println("Almacen: 1. Espana   2. Francia");
        String almacen = (Utilidades.leerEntero("Opcion: ") == 2) ? "Francia" : "España";
        int cantidad = Utilidades.leerEntero("Cantidad inicial en stock: ");

        if (nombre.isEmpty() || precio <= 0 || cantidad < 0) {
            System.out.println("Datos no validos: nombre obligatorio, precio mayor que 0 y cantidad no negativa");
            return;
        }
        verProveedores(con);
        int idProveedor = Utilidades.leerEntero("ID del proveedor que nos lo vende (0 = ninguno, lo fabricamos): ");
        if (idProveedor != 0 && !Utilidades.existe(con, "SELECT 1 FROM proveedores WHERE id_proveedor = ?", idProveedor)) {
            System.out.println("No existe ese proveedor");
            return;
        }

        try {
            con.setAutoCommit(false);
            int idProducto;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO productos (nombre, categoria, precio, id_proveedor) VALUES (?, ?, ?, ?) "
                    + "RETURNING id_producto")) {
                ps.setString(1, nombre);
                ps.setString(2, categoria);
                ps.setDouble(3, precio);
                if (idProveedor == 0) ps.setNull(4, Types.INTEGER);
                else ps.setInt(4, idProveedor);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    idProducto = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO stock (id_producto, almacen, cantidad) VALUES (?, ?, ?)")) {
                ps.setInt(1, idProducto);
                ps.setString(2, almacen);
                ps.setInt(3, cantidad);
                ps.executeUpdate();
            }
            con.commit();
            System.out.println("Producto anadido con el ID " + idProducto);
            System.out.println("Si se fabrica en la empresa, define sus piezas en 'Anadir componente'");
        } catch (SQLException e) {
            Utilidades.rollback(con);
            System.out.println("No se ha podido anadir el producto: " + e.getMessage());
        } finally {
            Utilidades.activarAutoCommit(con);
        }
    }

    public static void editarProducto(Connection con) throws SQLException {
        if (!Usuarios.exigir("inventario")) return;
        verProductos(con);
        int id = Utilidades.leerEntero("ID del producto a editar (0 = cancelar): ");
        if (id == 0) return;

        String nombreActual;
        String categoriaActual;
        double precioActual;
        int proveedorActual;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT nombre, categoria, precio, id_proveedor FROM productos WHERE id_producto = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("No existe ese producto");
                    return;
                }
                nombreActual = rs.getString(1);
                categoriaActual = rs.getString(2);
                precioActual = rs.getDouble(3);
                proveedorActual = rs.getInt(4);
            }
        }

        System.out.println("(Pulsa Enter para dejar el valor actual)");
        System.out.print("Nombre [" + nombreActual + "]: ");
        String nombre = Utilidades.leerLinea().trim();
        if (nombre.isEmpty()) nombre = nombreActual;

        System.out.print("Categoria [" + (categoriaActual == null ? "" : categoriaActual) + "]: ");
        String categoria = Utilidades.leerLinea().trim();
        if (categoria.isEmpty()) categoria = categoriaActual;

        System.out.print("Precio [" + precioActual + "]: ");
        String textoPrecio = Utilidades.leerLinea().trim().replace(',', '.');
        double precio = precioActual;
        if (!textoPrecio.isEmpty()) {
            try {
                precio = Double.parseDouble(textoPrecio);
            } catch (NumberFormatException e) {
                System.out.println("Precio no valido. No se ha cambiado nada");
                return;
            }
        }
        if (precio <= 0) {
            System.out.println("El precio debe ser mayor que 0. No se ha cambiado nada");
            return;
        }
        int idProveedor = Utilidades.leerEnteroOpcional("ID proveedor (0 = ninguno)", proveedorActual, 0);
        if (idProveedor != 0 && !Utilidades.existe(con, "SELECT 1 FROM proveedores WHERE id_proveedor = ?", idProveedor)) {
            System.out.println("No existe ese proveedor. No se ha cambiado nada");
            return;
        }

        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE productos SET nombre = ?, categoria = ?, precio = ?, id_proveedor = ? WHERE id_producto = ?")) {
            ps.setString(1, nombre);
            ps.setString(2, categoria);
            ps.setDouble(3, precio);
            if (idProveedor == 0) ps.setNull(4, Types.INTEGER);
            else ps.setInt(4, idProveedor);
            ps.setInt(5, id);
            ps.executeUpdate();
            System.out.println("Producto actualizado");
        }
    }

    public static void borrarProducto(Connection con) throws SQLException {
        if (!Usuarios.exigir("inventario")) return;
        verProductos(con);
        int id = Utilidades.leerEntero("ID del producto a borrar (0 = cancelar): ");
        if (id == 0) return;
        if (!Utilidades.preguntarSiNo("Seguro que quieres borrarlo?")) {
            System.out.println("Cancelado");
            return;
        }

        try {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM stock WHERE id_producto = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            int borrados;
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM productos WHERE id_producto = ?")) {
                ps.setInt(1, id);
                borrados = ps.executeUpdate();
            }
            if (borrados == 0) {
                con.rollback();
                System.out.println("No existe ese producto");
            } else {
                con.commit();
                System.out.println("Producto borrado (y sus componentes de fabricacion)");
            }
        } catch (SQLException e) {
            Utilidades.rollback(con);
            if ("23503".equals(e.getSQLState())) {
                System.out.println("No se puede borrar: el producto ya aparece en pedidos, compras, montajes u oportunidades");
            } else {
                System.out.println("No se ha podido borrar: " + e.getMessage());
            }
        } finally {
            Utilidades.activarAutoCommit(con);
        }
    }

    // ============================================================
    // COMPONENTES DE FABRICACION (departamento de inventario o administrador)
    // ============================================================
    public static void anadirComponente(Connection con) throws SQLException {
        if (!Usuarios.exigir("inventario")) return;
        verProductos(con);
        int idProducto = Utilidades.leerEntero("ID del producto que usa este componente (0 = cancelar): ");
        if (idProducto == 0) return;
        if (!Utilidades.existe(con, "SELECT 1 FROM productos WHERE id_producto = ?", idProducto)) {
            System.out.println("No existe ese producto");
            return;
        }
        System.out.print("Nombre del componente (ej. Manillar, Tabla, Anclaje): ");
        String nombre = Utilidades.leerLinea().trim();
        System.out.print("Variante, opcional (ej. 156 cm, talla 42, 29 pulgadas): ");
        String variante = Utilidades.leerLinea().trim();
        int necesaria = Utilidades.leerEntero("Unidades necesarias para montar 1 producto: ");
        int stock = Utilidades.leerEntero("Stock actual: ");
        int minimo = Utilidades.leerEntero("Stock minimo (avisa de comprar por debajo): ");
        if (nombre.isEmpty() || necesaria <= 0 || stock < 0 || minimo < 0) {
            System.out.println("Datos no validos: nombre obligatorio, necesarias mayor que 0, stock y minimo no negativos");
            return;
        }
        verProveedores(con);
        int idProveedor = Utilidades.leerEntero("ID del proveedor (0 = ninguno): ");
        if (idProveedor != 0 && !Utilidades.existe(con, "SELECT 1 FROM proveedores WHERE id_proveedor = ?", idProveedor)) {
            System.out.println("No existe ese proveedor");
            return;
        }

        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO componentes_fabricacion "
                + "(id_producto, nombre, variante, cantidad_necesaria, stock_actual, stock_minimo, id_proveedor) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, idProducto);
            ps.setString(2, nombre);
            ps.setString(3, variante.isEmpty() ? null : variante);
            ps.setInt(4, necesaria);
            ps.setInt(5, stock);
            ps.setInt(6, minimo);
            if (idProveedor == 0) ps.setNull(7, Types.INTEGER);
            else ps.setInt(7, idProveedor);
            ps.executeUpdate();
            System.out.println("Componente anadido");
        }
    }

    public static void editarComponente(Connection con) throws SQLException {
        if (!Usuarios.exigir("inventario")) return;
        verComponentes(con);
        int id = Utilidades.leerEntero("ID del componente a editar (0 = cancelar): ");
        if (id == 0) return;

        String nombre;
        String variante;
        int necesaria;
        int stock;
        int minimo;
        int idProveedor;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT nombre, variante, cantidad_necesaria, stock_actual, stock_minimo, id_proveedor "
                + "FROM componentes_fabricacion WHERE id_componente = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("No existe ese componente");
                    return;
                }
                nombre = rs.getString(1);
                variante = rs.getString(2) == null ? "" : rs.getString(2);
                necesaria = rs.getInt(3);
                stock = rs.getInt(4);
                minimo = rs.getInt(5);
                idProveedor = rs.getInt(6);
            }
        }

        System.out.println("(Pulsa Enter para dejar el valor actual)");
        System.out.print("Nombre [" + nombre + "]: ");
        String t = Utilidades.leerLinea().trim();
        if (!t.isEmpty()) nombre = t;
        System.out.print("Variante [" + variante + "]: ");
        t = Utilidades.leerLinea().trim();
        if (!t.isEmpty()) variante = t;
        necesaria = Utilidades.leerEnteroOpcional("Unidades necesarias por producto", necesaria, 1);
        stock = Utilidades.leerEnteroOpcional("Stock actual", stock, 0);
        minimo = Utilidades.leerEnteroOpcional("Stock minimo", minimo, 0);
        idProveedor = Utilidades.leerEnteroOpcional("ID proveedor (0 = ninguno)", idProveedor, 0);
        if (idProveedor != 0 && !Utilidades.existe(con, "SELECT 1 FROM proveedores WHERE id_proveedor = ?", idProveedor)) {
            System.out.println("No existe ese proveedor. No se ha cambiado nada");
            return;
        }

        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE componentes_fabricacion SET nombre = ?, variante = ?, cantidad_necesaria = ?, "
                + "stock_actual = ?, stock_minimo = ?, id_proveedor = ? WHERE id_componente = ?")) {
            ps.setString(1, nombre);
            ps.setString(2, variante.isEmpty() ? null : variante);
            ps.setInt(3, necesaria);
            ps.setInt(4, stock);
            ps.setInt(5, minimo);
            if (idProveedor == 0) ps.setNull(6, Types.INTEGER);
            else ps.setInt(6, idProveedor);
            ps.setInt(7, id);
            ps.executeUpdate();
            System.out.println("Componente actualizado");
        }
    }

    public static void borrarComponente(Connection con) throws SQLException {
        if (!Usuarios.exigir("inventario")) return;
        verComponentes(con);
        int id = Utilidades.leerEntero("ID del componente a borrar (0 = cancelar): ");
        if (id == 0) return;
        if (!Utilidades.preguntarSiNo("Seguro que quieres borrarlo?")) {
            System.out.println("Cancelado");
            return;
        }
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM componentes_fabricacion WHERE id_componente = ?")) {
            ps.setInt(1, id);
            System.out.println(ps.executeUpdate() == 0 ? "No existe ese componente" : "Componente borrado");
        } catch (SQLException e) {
            if ("23503".equals(e.getSQLState())) {
                System.out.println("No se puede borrar: el componente aparece en pedidos de compra");
            } else {
                throw e;
            }
        }
    }

    // ============================================================
    // COMPRAS
    // ============================================================
    public static void crearCompraManual(Connection con) throws SQLException {
        if (!Usuarios.exigir("compras")) return;
        System.out.println("Que quieres comprar?   1. Un producto terminado   2. Una pieza de fabricacion");
        int tipo = Utilidades.leerEntero("Opcion (0 = cancelar): ");
        if (tipo == 1) {
            verProductos(con);
            int id = Utilidades.leerEntero("ID del producto a comprar (0 = cancelar): ");
            if (id == 0) return;
            if (!Utilidades.existe(con, "SELECT 1 FROM productos WHERE id_producto = ?", id)) {
                System.out.println("No existe ese producto");
                return;
            }
            pedirAlProveedor(con, id, Math.max(0, reservado(con, id) - stockTotal(con, id)));
        } else if (tipo == 2) {
            verComponentes(con);
            int id = Utilidades.leerEntero("ID de la pieza a comprar (0 = cancelar): ");
            if (id == 0) return;
            int cantidad = leerCantidadCompra(0);
            if (cantidad <= 0) return;
            int idCompra = crearCompraComponente(con, id, cantidad);
            if (idCompra < 0) return;
            System.out.println("[COMPRAS] Pedido de compra " + idCompra + " creado (estado: Pendiente)");
            if (tramitarCompra(con, idCompra)) System.out.println("[INVENTARIO] Stock de la pieza actualizado");
        } else if (tipo != 0) {
            System.out.println("Opcion no valida");
        }
    }

    // Si 'sugerida' > 0 (lo que falta para pedidos de clientes), pulsar Enter la acepta
    private static int leerCantidadCompra(int sugerida) {
        int cantidad = sugerida > 0
                ? Utilidades.leerEnteroOpcional("Cuantas unidades pides? (faltan " + sugerida
                        + " para pedidos de clientes, Enter = " + sugerida + ")", sugerida, 0)
                : Utilidades.leerEntero("Cuantas unidades pides? ");
        if (cantidad <= 0) System.out.println("La cantidad debe ser mayor que 0");
        return cantidad;
    }

    // Pregunta cuantas unidades de un producto terminado se piden, crea la compra y la tramita
    private static void pedirAlProveedor(Connection con, int idProducto, int sugerida) throws SQLException {
        int cantidad = leerCantidadCompra(sugerida);
        if (cantidad <= 0) return;
        int idCompra = crearCompraProducto(con, idProducto, cantidad);
        if (idCompra < 0) return;
        System.out.println("[COMPRAS] Pedido de compra " + idCompra + " creado (estado: Pendiente)");
        if (tramitarCompra(con, idCompra)) {
            System.out.println("[INVENTARIO] Stock actualizado: ahora hay " + stockTotal(con, idProducto) + " unidad(es)");
            Ventas.procesarPedidosEnEspera(con);
        }
    }

    // Crea el pedido de compra de un producto terminado a su proveedor. Devuelve su ID o -1 si no se pudo.
    public static int crearCompraProducto(Connection con, int idProducto, int cantidad) throws SQLException {
        int idProveedor = proveedorDeProducto(con, idProducto);
        if (idProveedor < 0) return -1;
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO compras (id_proveedor, id_producto, cantidad, fecha, estado) "
                + "VALUES (?, ?, ?, CURRENT_DATE, 'Pendiente') RETURNING id_compra")) {
            ps.setInt(1, idProveedor);
            ps.setInt(2, idProducto);
            ps.setInt(3, cantidad);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // Proveedor que nos vende el producto. Si no tiene, se pide y se guarda. -1 si se cancela
    public static int proveedorDeProducto(Connection con, int idProducto) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id_proveedor, nombre FROM productos WHERE id_producto = ?")) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("No existe ese producto");
                    return -1;
                }
                int id = rs.getInt(1);
                if (!rs.wasNull()) return id;
                System.out.println("El producto '" + rs.getString(2) + "' no tiene proveedor asignado");
            }
        }
        verProveedores(con);
        int idProveedor = Utilidades.leerEntero("ID del proveedor que lo vende (0 = cancelar): ");
        if (idProveedor == 0) return -1;
        if (!Utilidades.existe(con, "SELECT 1 FROM proveedores WHERE id_proveedor = ?", idProveedor)) {
            System.out.println("No existe ese proveedor");
            return -1;
        }
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE productos SET id_proveedor = ? WHERE id_producto = ?")) {
            ps.setInt(1, idProveedor);
            ps.setInt(2, idProducto);
            ps.executeUpdate();
        }
        System.out.println("Proveedor guardado en el producto para las proximas compras");
        return idProveedor;
    }

    // Crea el pedido de compra de un componente. Devuelve su ID o -1 si no se pudo.
    public static int crearCompraComponente(Connection con, int idComponente, int cantidad) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id_producto, id_proveedor, nombre FROM componentes_fabricacion WHERE id_componente = ?")) {
            ps.setInt(1, idComponente);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("No existe ese componente");
                    return -1;
                }
                int idProducto = rs.getInt(1);
                int idProveedor = rs.getInt(2);
                if (rs.wasNull()) {
                    System.out.println("El componente '" + rs.getString(3) + "' no tiene proveedor asignado. Editalo primero");
                    return -1;
                }
                try (PreparedStatement ins = con.prepareStatement(
                        "INSERT INTO compras (id_proveedor, id_producto, id_componente, cantidad, fecha, estado) "
                        + "VALUES (?, ?, ?, ?, CURRENT_DATE, 'Pendiente') RETURNING id_compra")) {
                    ins.setInt(1, idProveedor);
                    ins.setInt(2, idProducto);
                    ins.setInt(3, idComponente);
                    ins.setInt(4, cantidad);
                    try (ResultSet r2 = ins.executeQuery()) {
                        r2.next();
                        return r2.getInt(1);
                    }
                }
            }
        }
    }

    public static void recibirCompraMenu(Connection con) throws SQLException {
        if (!Usuarios.exigir("compras")) return;
        if (!listarCompras(con, true)) return;
        int id = Utilidades.leerEntero("ID del pedido de compra recibido (0 = cancelar): ");
        if (id == 0) return;
        if (recibirCompra(con, id)) {
            System.out.println("Material recibido: stock actualizado");
            Ventas.procesarPedidosEnEspera(con);
        }
    }

    // ============================================================
    // CONSEGUIR MATERIAL DEL PROVEEDOR (lo usan Compras y la cadena del CRM)
    // ============================================================

    // El proveedor acepta? Ha llegado? Devuelve true si el material ya esta en el almacen
    public static boolean tramitarCompra(Connection con, int idCompra) throws SQLException {
        String estado = null;
        try (PreparedStatement ps = con.prepareStatement("SELECT estado FROM compras WHERE id_compra = ?")) {
            ps.setInt(1, idCompra);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) estado = rs.getString(1);
            }
        }
        if ("Pendiente".equals(estado)) {
            if (!Utilidades.preguntarSiNo("   El proveedor ACEPTA el pedido de compra " + idCompra + "?")) {
                actualizarEstadoCompra(con, idCompra, "Rechazada");
                System.out.println("[COMPRAS] El proveedor rechaza el pedido de compra " + idCompra);
                return false;
            }
            actualizarEstadoCompra(con, idCompra, "Aceptada");
        }
        if (!Utilidades.preguntarSiNo("   Ha LLEGADO ya el material del pedido de compra " + idCompra + "?")) {
            System.out.println("[COMPRAS] Pedido de compra " + idCompra + " en camino. Cuando llegue, "
                    + "registralo en Compras > 'Registrar la llegada de un pedido'");
            return false;
        }
        return recibirCompra(con, idCompra);
    }

    // Consigue 'cantidad' unidades de un producto que compramos hecho.
    // Si ya hay un pedido de compra en camino se usa ese. True si el material ya esta en el almacen
    public static boolean reponerProducto(Connection con, int idProducto, int cantidad) throws SQLException {
        int idCompra = Utilidades.buscarId(con, "SELECT id_compra FROM compras WHERE id_producto = ? "
                + "AND id_componente IS NULL AND estado IN ('Pendiente', 'Aceptada') ORDER BY id_compra LIMIT 1", idProducto);
        if (idCompra > 0) {
            System.out.println("[COMPRAS] Ya hay un pedido de compra en camino para este producto: " + idCompra);
        } else {
            idCompra = crearCompraProducto(con, idProducto, cantidad);
            if (idCompra < 0) return false;
            System.out.println("[COMPRAS] Pedido de compra " + idCompra + " creado: " + cantidad + " unidad(es)");
        }
        return tramitarCompra(con, idCompra);
    }

    // Lo mismo para una pieza de fabricacion
    public static boolean reponerComponente(Connection con, Comp c, int cantidad) throws SQLException {
        int idCompra = Utilidades.buscarId(con, "SELECT id_compra FROM compras WHERE id_componente = ? "
                + "AND estado IN ('Pendiente', 'Aceptada') ORDER BY id_compra LIMIT 1", c.id());
        if (idCompra > 0) {
            System.out.println("[COMPRAS] Ya hay un pedido de compra en camino para " + c.nombre() + ": " + idCompra);
        } else {
            idCompra = crearCompraComponente(con, c.id(), cantidad);
            if (idCompra < 0) return false;
            System.out.println("[COMPRAS] Pedido de compra " + idCompra + " creado: " + cantidad + " x " + c.nombre());
        }
        return tramitarCompra(con, idCompra);
    }

    // Despues de vender: si un producto que compramos hecho se ha quedado sin stock,
    // pregunta si se pide mas al proveedor
    public static void comprobarAgotado(Connection con, int idProducto) throws SQLException {
        if (Usuarios.esCliente() || stockTotal(con, idProducto) > 0 || seFabrica(con, idProducto)) return;
        String nombre = "";
        try (PreparedStatement ps = con.prepareStatement("SELECT nombre FROM productos WHERE id_producto = ?")) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) nombre = rs.getString(1);
            }
        }
        System.out.println("\n[INVENTARIO] '" + nombre + "' se ha quedado SIN STOCK");
        int enCamino = Utilidades.buscarId(con, "SELECT id_compra FROM compras WHERE id_producto = ? "
                + "AND id_componente IS NULL AND estado IN ('Pendiente', 'Aceptada') LIMIT 1", idProducto);
        if (enCamino > 0) {
            System.out.println("[COMPRAS] Ya hay un pedido de compra en camino (" + enCamino + ")");
            return;
        }
        if (Utilidades.preguntarSiNo("Quieres pedir mas al proveedor?")) {
            pedirAlProveedor(con, idProducto, 0);
        } else {
            System.out.println("De acuerdo. Compras lo vera en 'Ver lo que hay que comprar'");
        }
    }

    public static boolean seFabrica(Connection con, int idProducto) throws SQLException {
        return Utilidades.existe(con, "SELECT 1 FROM componentes_fabricacion WHERE id_producto = ?", idProducto);
    }

    public static int stockComponente(Connection con, int idComponente) throws SQLException {
        return Math.max(0, Utilidades.buscarId(con,
                "SELECT stock_actual FROM componentes_fabricacion WHERE id_componente = ?", idComponente));
    }

    // Marca la compra como recibida y suma las unidades al stock (de la pieza o del producto terminado)
    public static boolean recibirCompra(Connection con, int idCompra) {
        try {
            con.setAutoCommit(false);
            String estado;
            int idComponente;
            boolean sinComponente;
            int cantidad;
            int idProducto;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT estado, id_componente, cantidad, id_producto FROM compras WHERE id_compra = ? FOR UPDATE")) {
                ps.setInt(1, idCompra);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        con.rollback();
                        System.out.println("No existe ese pedido de compra");
                        return false;
                    }
                    estado = rs.getString(1);
                    idComponente = rs.getInt(2);
                    sinComponente = rs.wasNull();
                    cantidad = rs.getInt(3);
                    idProducto = rs.getInt(4);
                }
            }
            if ("Recibida".equals(estado)) {
                con.rollback();
                System.out.println("Ese pedido ya estaba recibido");
                return false;
            }
            if ("Rechazada".equals(estado)) {
                con.rollback();
                System.out.println("Ese pedido fue rechazado por el proveedor");
                return false;
            }
            if (sinComponente) {
                // Compra de un producto terminado: va directo al stock del almacen
                sumarStockProducto(con, idProducto, cantidad);
            } else {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE componentes_fabricacion SET stock_actual = stock_actual + ? WHERE id_componente = ?")) {
                    ps.setInt(1, cantidad);
                    ps.setInt(2, idComponente);
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE compras SET estado = 'Recibida' WHERE id_compra = ?")) {
                ps.setInt(1, idCompra);
                ps.executeUpdate();
            }
            con.commit();
            return true;
        } catch (SQLException e) {
            Utilidades.rollback(con);
            System.out.println("No se ha podido registrar la recepcion: " + e.getMessage());
            return false;
        } finally {
            Utilidades.activarAutoCommit(con);
        }
    }

    public static void actualizarEstadoCompra(Connection con, int id, String estado) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE compras SET estado = ? WHERE id_compra = ?")) {
            ps.setString(1, estado);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    // ============================================================
    // FABRICACION
    // ============================================================
    // Fabricacion crea una orden para tener unidades en stock (sin pedido de un cliente)
    public static void crearOrdenMenu(Connection con) throws SQLException {
        if (!Usuarios.exigir("fabricacion")) return;
        if (!listarProductosFabricables(con)) return;
        int idProducto = Utilidades.leerEntero("ID del producto a fabricar (0 = cancelar): ");
        if (idProducto == 0) return;
        if (!seFabrica(con, idProducto)) {
            System.out.println("Ese producto no tiene piezas definidas, asi que no se puede fabricar");
            return;
        }
        int cantidad = Utilidades.leerEntero("Unidades a fabricar: ");
        if (cantidad <= 0) {
            System.out.println("La cantidad debe ser mayor que 0");
            return;
        }
        if (!piezasSuficientes(con, idProducto, cantidad)) {
            System.out.println("Aviso: la orden se crea, pero no se podra terminar hasta que lleguen las piezas");
        }
        int id = crearOrden(con, null, idProducto, cantidad);
        System.out.println("[FABRICACION] Orden " + id + " creada: " + cantidad + " unidad(es) (estado: Pendiente)");
    }

    // Crea una orden de fabricacion. idPedido = null si es para stock. Devuelve su ID
    public static int crearOrden(Connection con, Integer idPedido, int idProducto, int cantidad) throws SQLException {
        String categoria = null;
        try (PreparedStatement ps = con.prepareStatement("SELECT categoria FROM productos WHERE id_producto = ?")) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) categoria = rs.getString(1);
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO fabricacion (id_pedido, id_producto, cantidad, tipo, id_usuario, fecha, estado) "
                + "VALUES (?, ?, ?, ?, ?, CURRENT_DATE, 'Pendiente') RETURNING id_montaje")) {
            if (idPedido == null) ps.setNull(1, Types.INTEGER);
            else ps.setInt(1, idPedido);
            ps.setInt(2, idProducto);
            ps.setInt(3, cantidad);
            ps.setString(4, tipoDe(categoria));
            ps.setInt(5, Usuarios.idUsuarioActual);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // Comprueba las piezas para montar 'unidades'. Si faltan, ofrece pedirlas al proveedor
    private static boolean piezasSuficientes(Connection con, int idProducto, int unidades) throws SQLException {
        boolean todoBien = true;
        for (Comp c : cargarComponentes(con, idProducto, false)) {
            int necesario = c.necesaria() * unidades;
            int deficit;
            while ((deficit = necesario - stockComponente(con, c.id())) > 0) {
                System.out.println("[FABRICACION] Falta pieza: " + c.nombre() + " (hacen falta " + necesario
                        + ", faltan " + deficit + ")");
                if (!Utilidades.preguntarSiNo("   Pedir las " + deficit + " que faltan al proveedor?")
                        || !reponerComponente(con, c, deficit)) {
                    todoBien = false;
                    break;
                }
            }
        }
        return todoBien;
    }

    public static void terminarOrdenMenu(Connection con) throws SQLException {
        if (!Usuarios.exigir("fabricacion")) return;
        if (!listarOrdenes(con, true)) return;
        int id = Utilidades.leerEntero("ID de la orden a terminar (0 = cancelar): ");
        if (id == 0) return;
        String abierta = "FROM fabricacion WHERE id_montaje = ? AND estado NOT IN ('Terminada', 'Cancelada')";
        int idProducto = Utilidades.buscarId(con, "SELECT id_producto " + abierta, id);
        if (idProducto < 0) {
            System.out.println("Esa orden no existe o ya esta cerrada");
            return;
        }
        int cantidad = Utilidades.buscarId(con, "SELECT cantidad " + abierta, id);
        if (!piezasSuficientes(con, idProducto, cantidad)) {
            System.out.println("No se puede terminar la orden: faltan piezas. Vuelve a intentarlo cuando lleguen");
            return;
        }
        if (terminarMontaje(con, id)) {
            System.out.println("Orden terminada: piezas gastadas y " + cantidad + " unidad(es) sumadas al stock");
            Ventas.procesarPedidosEnEspera(con);
        }
    }

    // Solo se cancelan las ordenes para stock: las de un pedido las espera un cliente
    public static void cancelarOrdenMenu(Connection con) throws SQLException {
        if (!Usuarios.exigir("fabricacion")) return;
        if (!listarOrdenes(con, true)) return;
        int id = Utilidades.leerEntero("ID de la orden a cancelar (0 = cancelar): ");
        if (id == 0) return;
        if (!Utilidades.existe(con, "SELECT 1 FROM fabricacion WHERE id_montaje = ? "
                + "AND estado NOT IN ('Terminada', 'Cancelada')", id)) {
            System.out.println("Esa orden no existe o ya esta cerrada");
            return;
        }
        int idPedido = Utilidades.buscarId(con, "SELECT COALESCE(id_pedido, -1) FROM fabricacion WHERE id_montaje = ?", id);
        if (idPedido > 0) {
            System.out.println("Esa orden es del pedido " + idPedido + " de un cliente: no se puede cancelar");
            return;
        }
        if (!Utilidades.preguntarSiNo("Seguro que quieres cancelar la orden " + id + "?")) return;
        try (PreparedStatement ps = con.prepareStatement("UPDATE fabricacion SET estado = 'Cancelada' WHERE id_montaje = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        System.out.println("Orden " + id + " cancelada (no se ha gastado ninguna pieza)");
    }

    // Gasta las piezas de toda la orden y suma sus unidades al stock del producto
    public static boolean terminarMontaje(Connection con, int idMontaje) {
        try {
            con.setAutoCommit(false);
            String estado;
            int idProducto;
            int cantidad;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT estado, id_producto, cantidad FROM fabricacion WHERE id_montaje = ? FOR UPDATE")) {
                ps.setInt(1, idMontaje);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        con.rollback();
                        System.out.println("No existe esa orden de fabricacion");
                        return false;
                    }
                    estado = rs.getString(1);
                    idProducto = rs.getInt(2);
                    cantidad = rs.getInt(3);
                }
            }
            if ("Terminada".equals(estado) || "Cancelada".equals(estado)) {
                con.rollback();
                System.out.println("Esa orden ya estaba " + estado.toLowerCase());
                return false;
            }
            List<Comp> comps = cargarComponentes(con, idProducto, true);
            for (Comp c : comps) {
                if (c.stock() < c.necesaria() * cantidad) {
                    con.rollback();
                    System.out.println("Falta pieza: " + c.nombre() + " (hay " + c.stock()
                            + ", hacen falta " + c.necesaria() * cantidad + "). Compralas y vuelve a intentarlo");
                    return false;
                }
            }
            for (Comp c : comps) {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE componentes_fabricacion SET stock_actual = stock_actual - ? WHERE id_componente = ?")) {
                    ps.setInt(1, c.necesaria() * cantidad);
                    ps.setInt(2, c.id());
                    ps.executeUpdate();
                }
            }
            sumarStockProducto(con, idProducto, cantidad);
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE fabricacion SET estado = 'Terminada' WHERE id_montaje = ?")) {
                ps.setInt(1, idMontaje);
                ps.executeUpdate();
            }
            // Si era la ultima orden del pedido, el pedido queda listo para que Ventas lo envie y facture
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE pedidos SET estado = 'Listo envio' WHERE estado = 'En fabricacion' "
                    + "AND id_pedido = (SELECT id_pedido FROM fabricacion WHERE id_montaje = ?) "
                    + "AND NOT EXISTS (SELECT 1 FROM fabricacion f WHERE f.id_pedido = pedidos.id_pedido "
                    + "AND f.estado <> 'Terminada')")) {
                ps.setInt(1, idMontaje);
                if (ps.executeUpdate() > 0) {
                    System.out.println("Todas las ordenes del pedido estan terminadas: el pedido queda 'Listo envio'");
                }
            }
            con.commit();
            return true;
        } catch (SQLException e) {
            Utilidades.rollback(con);
            System.out.println("No se ha podido terminar la orden: " + e.getMessage());
            return false;
        } finally {
            Utilidades.activarAutoCommit(con);
        }
    }

    // ============================================================
    // STOCK: funciones de apoyo
    // ============================================================
    public static int stockTotal(Connection con, int idProducto) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COALESCE(SUM(cantidad), 0) FROM stock WHERE id_producto = ?")) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // Resta del almacen que tenga suficiente. False si ninguno tiene.
    public static boolean descontarStock(Connection con, int idProducto, int cantidad) throws SQLException {
        int idStock;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id_stock FROM stock WHERE id_producto = ? AND cantidad >= ? "
                + "ORDER BY cantidad DESC LIMIT 1 FOR UPDATE")) {
            ps.setInt(1, idProducto);
            ps.setInt(2, cantidad);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                idStock = rs.getInt(1);
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE stock SET cantidad = cantidad - ? WHERE id_stock = ?")) {
            ps.setInt(1, cantidad);
            ps.setInt(2, idStock);
            ps.executeUpdate();
        }
        return true;
    }

    // Resta repartiendo entre almacenes (usar dentro de una transaccion)
    public static boolean descontarStockTotal(Connection con, int idProducto, int cantidad) throws SQLException {
        List<int[]> filas = new ArrayList<>();
        int total = 0;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id_stock, cantidad FROM stock WHERE id_producto = ? AND cantidad > 0 "
                + "ORDER BY cantidad DESC FOR UPDATE")) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    filas.add(new int[]{rs.getInt(1), rs.getInt(2)});
                    total += rs.getInt(2);
                }
            }
        }
        if (total < cantidad) return false;
        int resto = cantidad;
        for (int[] f : filas) {
            if (resto == 0) break;
            int quitar = Math.min(resto, f[1]);
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE stock SET cantidad = cantidad - ? WHERE id_stock = ?")) {
                ps.setInt(1, quitar);
                ps.setInt(2, f[0]);
                ps.executeUpdate();
            }
            resto -= quitar;
        }
        return true;
    }

    private static void sumarStockProducto(Connection con, int idProducto, int cantidad) throws SQLException {
        int filas;
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE stock SET cantidad = cantidad + ? WHERE id_stock = "
                + "(SELECT id_stock FROM stock WHERE id_producto = ? ORDER BY id_stock LIMIT 1)")) {
            ps.setInt(1, cantidad);
            ps.setInt(2, idProducto);
            filas = ps.executeUpdate();
        }
        if (filas == 0) {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO stock (id_producto, almacen, cantidad) VALUES (?, 'España', ?)")) {
                ps.setInt(1, idProducto);
                ps.setInt(2, cantidad);
                ps.executeUpdate();
            }
        }
    }

    // El campo 'tipo' de fabricacion tiene 10 caracteres
    private static String tipoDe(String categoria) {
        String c = categoria == null ? "" : categoria.toLowerCase();
        if (c.contains("bici")) return "bici";
        if (c.contains("snow") || c.contains("tabla")) return "snow";
        return c.isEmpty() ? "otro" : (c.length() > 10 ? c.substring(0, 10) : c);
    }

    // Lee los componentes de un producto. Con bloquear=true se bloquean las filas
    public static List<Comp> cargarComponentes(Connection con, int idProducto, boolean bloquear) throws SQLException {
        List<Comp> lista = new ArrayList<>();
        String sql = "SELECT id_componente, nombre, cantidad_necesaria, stock_actual, id_proveedor "
                   + "FROM componentes_fabricacion WHERE id_producto = ? ORDER BY id_componente"
                   + (bloquear ? " FOR UPDATE" : "");
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int prov = rs.getInt(5);
                    Integer idProv = rs.wasNull() ? null : prov;
                    lista.add(new Comp(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4), idProv));
                }
            }
        }
        return lista;
    }
}