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

    // ============================================================
    // VER DATOS
    // ============================================================
    public static void verProductos(Connection con) throws SQLException {
        String sql = "SELECT p.id_producto, p.nombre, p.categoria, p.precio, "
                   + "COALESCE(SUM(s.cantidad), 0) AS stock "
                   + "FROM productos p LEFT JOIN stock s ON s.id_producto = p.id_producto "
                   + "GROUP BY p.id_producto ORDER BY p.id_producto";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- PRODUCTOS ---");
            boolean hay = false;
            while (rs.next()) {
                hay = true;
                System.out.printf("%d. %s (%s) - %.2f EUR - stock: %d%n",
                        rs.getInt("id_producto"), rs.getString("nombre"),
                        rs.getString("categoria"), rs.getDouble("precio"), rs.getInt("stock"));
            }
            if (!hay) System.out.println("No hay productos");
        }
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
        String sql = "SELECT c.id_compra, COALESCE(pv.nombre, '-'), COALESCE(cf.nombre, p.nombre, '-'), "
                   + "c.cantidad, c.fecha, COALESCE(c.estado, '-') "
                   + "FROM compras c LEFT JOIN proveedores pv ON pv.id_proveedor = c.id_proveedor "
                   + "LEFT JOIN componentes_fabricacion cf ON cf.id_componente = c.id_componente "
                   + "LEFT JOIN productos p ON p.id_producto = c.id_producto ORDER BY c.id_compra";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- PEDIDOS DE COMPRA ---");
            boolean hay = false;
            while (rs.next()) {
                hay = true;
                System.out.printf("Compra %d | %s | %d x %s | %s | %s%n",
                        rs.getInt(1), rs.getString(2), rs.getInt(4), rs.getString(3),
                        rs.getString(5), rs.getString(6));
            }
            if (!hay) System.out.println("No hay pedidos de compra");
        }
    }

    public static void verFabricacion(Connection con) throws SQLException {
        String sql = "SELECT f.id_montaje, f.id_pedido, COALESCE(p.nombre, '-'), COALESCE(f.tipo, '-'), "
                   + "COALESCE(u.nombre_usuario, '-'), f.fecha, COALESCE(f.estado, '-') "
                   + "FROM fabricacion f LEFT JOIN productos p ON p.id_producto = f.id_producto "
                   + "LEFT JOIN usuarios u ON u.id_usuario = f.id_usuario ORDER BY f.id_montaje";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- ORDENES DE FABRICACION ---");
            boolean hay = false;
            while (rs.next()) {
                hay = true;
                String pedido = rs.getString(2) == null ? "-" : rs.getString(2);
                System.out.printf("Orden %d | pedido %s | %s (%s) | %s | %s | %s%n",
                        rs.getInt(1), pedido, rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7));
            }
            if (!hay) System.out.println("No hay ordenes de fabricacion");
        }
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
    // PRODUCTOS (solo administrador)
    // ============================================================
    public static void anadirProducto(Connection con) throws SQLException {
        if (!Usuarios.exigirAdmin()) return;
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

        try {
            con.setAutoCommit(false);
            int idProducto;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO productos (nombre, categoria, precio) VALUES (?, ?, ?) RETURNING id_producto")) {
                ps.setString(1, nombre);
                ps.setString(2, categoria);
                ps.setDouble(3, precio);
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
        if (!Usuarios.exigirAdmin()) return;
        int id = Utilidades.leerEntero("ID del producto a editar: ");

        String nombreActual;
        String categoriaActual;
        double precioActual;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT nombre, categoria, precio FROM productos WHERE id_producto = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("No existe ese producto");
                    return;
                }
                nombreActual = rs.getString(1);
                categoriaActual = rs.getString(2);
                precioActual = rs.getDouble(3);
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

        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE productos SET nombre = ?, categoria = ?, precio = ? WHERE id_producto = ?")) {
            ps.setString(1, nombre);
            ps.setString(2, categoria);
            ps.setDouble(3, precio);
            ps.setInt(4, id);
            ps.executeUpdate();
            System.out.println("Producto actualizado");
        }
    }

    public static void borrarProducto(Connection con) throws SQLException {
        if (!Usuarios.exigirAdmin()) return;
        int id = Utilidades.leerEntero("ID del producto a borrar: ");
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
    // COMPONENTES DE FABRICACION (solo administrador)
    // ============================================================
    public static void anadirComponente(Connection con) throws SQLException {
        if (!Usuarios.exigirAdmin()) return;
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
        if (!Usuarios.exigirAdmin()) return;
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
        if (!Usuarios.exigirAdmin()) return;
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
        if (!Usuarios.exigirAdmin()) return;
        verComponentes(con);
        int id = Utilidades.leerEntero("ID del componente a comprar (0 = cancelar): ");
        if (id == 0) return;
        int cantidad = Utilidades.leerEntero("Cantidad a comprar: ");
        if (cantidad <= 0) {
            System.out.println("La cantidad debe ser mayor que 0");
            return;
        }
        int idCompra = crearCompra(con, id, cantidad);
        if (idCompra > 0) System.out.println("Pedido de compra " + idCompra + " creado (estado: Pendiente)");
    }

    // Crea el pedido de compra de un componente. Devuelve su ID o -1 si no se pudo.
    public static int crearCompra(Connection con, int idComponente, int cantidad) throws SQLException {
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
        if (!Usuarios.exigirAdmin()) return;
        verCompras(con);
        int id = Utilidades.leerEntero("ID del pedido de compra recibido (0 = cancelar): ");
        if (id == 0) return;
        if (recibirCompra(con, id)) System.out.println("Material recibido: stock del componente actualizado");
    }

    // Marca la compra como recibida y suma las unidades al stock del componente
    public static boolean recibirCompra(Connection con, int idCompra) {
        try {
            con.setAutoCommit(false);
            String estado;
            int idComponente;
            boolean sinComponente;
            int cantidad;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT estado, id_componente, cantidad FROM compras WHERE id_compra = ? FOR UPDATE")) {
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
                con.rollback();
                System.out.println("Ese pedido no esta ligado a ningun componente (es un pedido antiguo)");
                return false;
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE componentes_fabricacion SET stock_actual = stock_actual + ? WHERE id_componente = ?")) {
                ps.setInt(1, cantidad);
                ps.setInt(2, idComponente);
                ps.executeUpdate();
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
    public static void terminarOrdenMenu(Connection con) throws SQLException {
        if (!Usuarios.exigirAdmin()) return;
        verFabricacion(con);
        int id = Utilidades.leerEntero("ID de la orden a terminar (0 = cancelar): ");
        if (id == 0) return;
        if (terminarMontaje(con, id)) System.out.println("Orden terminada: componentes gastados y producto anadido al stock");
    }

    // Gasta los componentes y suma 1 unidad del producto al stock
    public static boolean terminarMontaje(Connection con, int idMontaje) {
        try {
            con.setAutoCommit(false);
            String estado;
            int idProducto;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT estado, id_producto FROM fabricacion WHERE id_montaje = ? FOR UPDATE")) {
                ps.setInt(1, idMontaje);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        con.rollback();
                        System.out.println("No existe esa orden de fabricacion");
                        return false;
                    }
                    estado = rs.getString(1);
                    idProducto = rs.getInt(2);
                }
            }
            if ("Terminada".equals(estado)) {
                con.rollback();
                System.out.println("Esa orden ya estaba terminada");
                return false;
            }
            List<Comp> comps = cargarComponentes(con, idProducto, true);
            for (Comp c : comps) {
                if (c.stock() < c.necesaria()) {
                    con.rollback();
                    System.out.println("Falta componente: " + c.nombre() + " (hay " + c.stock()
                            + ", hacen falta " + c.necesaria() + "). Compralo y vuelve a intentarlo");
                    return false;
                }
            }
            for (Comp c : comps) {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE componentes_fabricacion SET stock_actual = stock_actual - ? WHERE id_componente = ?")) {
                    ps.setInt(1, c.necesaria());
                    ps.setInt(2, c.id());
                    ps.executeUpdate();
                }
            }
            sumarStockProducto(con, idProducto, 1);
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE fabricacion SET estado = 'Terminada' WHERE id_montaje = ?")) {
                ps.setInt(1, idMontaje);
                ps.executeUpdate();
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