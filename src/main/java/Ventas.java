import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class Ventas {

    // Un producto dentro de un pedido
    private record Linea(int idProducto, String nombre, double precio, int cantidad) {}

    // ============================================================
    // VER DATOS
    // ============================================================
    public static void verClientes(Connection con) throws SQLException {
        String sql = "SELECT id_cliente, nombre, COALESCE(email, '-'), COALESCE(telefono, '-'), "
                   + "COALESCE(pais, '-') FROM clientes ORDER BY id_cliente";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- CLIENTES ---");
            boolean hay = false;
            while (rs.next()) {
                hay = true;
                System.out.printf("%d. %s | %s | %s | %s%n",
                        rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5));
            }
            if (!hay) System.out.println("No hay clientes");
        }
    }
    // Borra un cliente con sus pedidos, facturas, oportunidades y su usuario (CRM o administrador)
    public static void borrarCliente(Connection con) throws SQLException {
        if (!Usuarios.exigir("crm")) return;
        verClientes(con);
        int id = Utilidades.leerEntero("ID del cliente a borrar (0 = cancelar): ");
        if (id == 0) return;
        if (!Utilidades.existe(con, "SELECT 1 FROM clientes WHERE id_cliente = ?", id)) {
            System.out.println("No existe ese cliente");
            return;
        }
        if (!Utilidades.preguntarSiNo("Se borraran tambien sus pedidos, facturas y oportunidades. Seguro?")) {
            System.out.println("Cancelado");
            return;
        }

        String pedidosDelCliente = "(SELECT id_pedido FROM pedidos WHERE id_cliente = ?)";
        String[] sentencias = {
            "DELETE FROM facturas WHERE id_pedido IN " + pedidosDelCliente,
            "DELETE FROM lineas_pedido WHERE id_pedido IN " + pedidosDelCliente,
            "DELETE FROM pedidos WHERE id_cliente = ?",
            "DELETE FROM oportunidades WHERE id_cliente = ?"
        };

        try {
            con.setAutoCommit(false);

            // Guardamos su usuario para borrarlo al final
            int idUsuario = -1;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT id_usuario FROM clientes WHERE id_cliente = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        idUsuario = rs.getInt(1);
                        if (rs.wasNull()) idUsuario = -1;
                    }
                }
            }

            // Borramos lo que depende del cliente
            for (String sql : sentencias) {
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }
            }

            // Borramos el cliente
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM clientes WHERE id_cliente = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            // Borramos su usuario para que ya no pueda iniciar sesion
            if (idUsuario != -1) {
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM usuarios WHERE id_usuario = ?")) {
                    ps.setInt(1, idUsuario);
                    ps.executeUpdate();
                }
            }

            con.commit();
            System.out.println("Cliente borrado");
        } catch (SQLException e) {
            Utilidades.rollback(con);
            System.out.println("No se ha podido borrar el cliente: " + e.getMessage());
        } finally {
            Utilidades.activarAutoCommit(con);
        }
    }

    public static void verPedidos(Connection con) throws SQLException {
        String sql = "SELECT p.id_pedido, c.nombre, p.fecha, COALESCE(p.estado, '-'), "
                   + "COALESCE(SUM(l.cantidad * pr.precio), 0) "
                   + "FROM pedidos p JOIN clientes c ON c.id_cliente = p.id_cliente "
                   + "LEFT JOIN lineas_pedido l ON l.id_pedido = p.id_pedido "
                   + "LEFT JOIN productos pr ON pr.id_producto = l.id_producto "
                   + "GROUP BY p.id_pedido, c.nombre ORDER BY p.id_pedido";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- PEDIDOS DE VENTA ---");
            boolean hay = false;
            while (rs.next()) {
                hay = true;
                System.out.printf("Pedido %d | %s | %s | %s | %.2f EUR%n",
                        rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getDouble(5));
            }
            if (!hay) System.out.println("No hay pedidos");
        }
    }

    public static void verFacturas(Connection con) throws SQLException {
        String sql = "SELECT f.id_factura, f.id_pedido, COALESCE(c.nombre, '-'), f.fecha, f.total "
                   + "FROM facturas f LEFT JOIN pedidos p ON p.id_pedido = f.id_pedido "
                   + "LEFT JOIN clientes c ON c.id_cliente = p.id_cliente ORDER BY f.id_factura";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- FACTURAS ---");
            boolean hay = false;
            while (rs.next()) {
                hay = true;
                System.out.printf("Factura %d | pedido %d | %s | %s | %.2f EUR%n",
                        rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getDouble(5));
            }
            if (!hay) System.out.println("No hay facturas");
        }
    }

    public static void verOportunidades(Connection con) throws SQLException {
        String sql = "SELECT o.id_oportunidad, c.nombre, p.nombre, o.cantidad, "
                   + "COALESCE(u.nombre_usuario, '-'), o.fecha, o.estado "
                   + "FROM oportunidades o JOIN clientes c ON c.id_cliente = o.id_cliente "
                   + "JOIN productos p ON p.id_producto = o.id_producto "
                   + "LEFT JOIN usuarios u ON u.id_usuario = o.id_usuario ORDER BY o.id_oportunidad";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- OPORTUNIDADES DE VENTA ---");
            boolean hay = false;
            while (rs.next()) {
                hay = true;
                System.out.printf("Oportunidad %d | %s | %d x %s | comercial: %s | %s | %s%n",
                        rs.getInt(1), rs.getString(2), rs.getInt(4), rs.getString(3),
                        rs.getString(5), rs.getString(6), rs.getString(7));
            }
            if (!hay) System.out.println("No hay oportunidades");
        }
    }

    // ============================================================
    // FACTURAR PEDIDOS (departamento de ventas)
    // ============================================================
    // Pedidos que se pueden facturar:
    //  - 'pendiente'  : pedidos hechos por clientes (el stock ya se resto al hacer el pedido)
    //  - 'Listo envio': pedidos de la cadena del CRM ya fabricados (el stock se resta al enviarlos)
    public static void facturarPedido(Connection con) throws SQLException {
        if (!Usuarios.exigir("ventas")) return;
        String sql = "SELECT p.id_pedido, c.nombre, p.fecha, p.estado, "
                   + "COALESCE(SUM(l.cantidad * pr.precio), 0) "
                   + "FROM pedidos p JOIN clientes c ON c.id_cliente = p.id_cliente "
                   + "LEFT JOIN lineas_pedido l ON l.id_pedido = p.id_pedido "
                   + "LEFT JOIN productos pr ON pr.id_producto = l.id_producto "
                   + "WHERE p.estado IN ('pendiente', 'Listo envio') "
                   + "AND NOT EXISTS (SELECT 1 FROM facturas f WHERE f.id_pedido = p.id_pedido) "
                   + "GROUP BY p.id_pedido, c.nombre ORDER BY p.id_pedido";
        java.util.Map<Integer, Double> totales = new java.util.HashMap<>();
        java.util.Map<Integer, String> estados = new java.util.HashMap<>();
        System.out.println("\n--- PEDIDOS PENDIENTES DE FACTURAR ---");
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                totales.put(rs.getInt(1), rs.getDouble(5));
                estados.put(rs.getInt(1), rs.getString(4));
                System.out.printf("Pedido %d | %s | %s | %s | %.2f EUR%n",
                        rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getDouble(5));
            }
        }
        if (totales.isEmpty()) {
            System.out.println("No hay pedidos pendientes de facturar");
            return;
        }
        int id = Utilidades.leerEntero("ID del pedido a facturar (0 = cancelar): ");
        if (id == 0) return;
        if (!totales.containsKey(id)) {
            System.out.println("Ese pedido no esta en la lista");
            return;
        }
        double total = totales.get(id);
        if (!Utilidades.preguntarSiNo(String.format("Se enviara y facturara el pedido %d por %.2f EUR. Confirmar?", id, total))) {
            System.out.println("Cancelado");
            return;
        }

        try {
            con.setAutoCommit(false);
            // Los pedidos de la cadena todavia no han restado el stock: se resta al enviarlos
            if ("Listo envio".equals(estados.get(id))) {
                List<Linea> lineas = new ArrayList<>();
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT l.id_producto, p.nombre, p.precio, l.cantidad FROM lineas_pedido l "
                        + "JOIN productos p ON p.id_producto = l.id_producto WHERE l.id_pedido = ?")) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            lineas.add(new Linea(rs.getInt(1), rs.getString(2), rs.getDouble(3), rs.getInt(4)));
                        }
                    }
                }
                for (Linea l : lineas) {
                    if (!Inventario.descontarStockTotal(con, l.idProducto(), l.cantidad())) {
                        con.rollback();
                        System.out.println("No hay stock suficiente de " + l.nombre() + " para enviar el pedido");
                        return;
                    }
                }
            }
            int idFactura;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO facturas (id_pedido, fecha, total) VALUES (?, CURRENT_DATE, ?) RETURNING id_factura")) {
                ps.setInt(1, id);
                ps.setDouble(2, total);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    idFactura = rs.getInt(1);
                }
            }
            cambiarEstadoPedido(con, id, "Facturado");
            con.commit();
            System.out.printf("Factura %d emitida por %.2f EUR. El pedido %d queda 'Facturado'%n", idFactura, total, id);
        } catch (SQLException e) {
            Utilidades.rollback(con);
            System.out.println("No se ha podido facturar el pedido: " + e.getMessage());
        } finally {
            Utilidades.activarAutoCommit(con);
        }
    }

    // ============================================================
    // PEDIDO DEL CLIENTE
    // ============================================================
    public static void hacerPedido(Connection con) throws SQLException {
        int idCliente = obtenerIdCliente(con);
        if (idCliente == -1) {
            System.out.println("No se ha encontrado tu ficha de cliente");
            return;
        }

        List<Linea> carrito = new ArrayList<>();
        Inventario.verProductos(con);

        while (true) {
            int idProducto = Utilidades.leerEntero("ID del producto a comprar (0 para terminar): ");
            if (idProducto == 0) break;
            int cantidad = Utilidades.leerEntero("Cantidad: ");
            if (cantidad <= 0) {
                System.out.println("La cantidad debe ser mayor que 0");
                continue;
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT nombre, precio FROM productos WHERE id_producto = ?")) {
                ps.setInt(1, idProducto);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("No existe ese producto");
                        continue;
                    }
                    carrito.add(new Linea(idProducto, rs.getString(1), rs.getDouble(2), cantidad));
                    System.out.println("Anadido: " + cantidad + " x " + rs.getString(1));
                }
            }
        }

        if (carrito.isEmpty()) {
            System.out.println("Pedido vacio");
            return;
        }

        double total = 0;
        System.out.println("\n--- RESUMEN DEL PEDIDO ---");
        for (Linea l : carrito) {
            System.out.printf("%d x %s = %.2f EUR%n", l.cantidad(), l.nombre(), l.precio() * l.cantidad());
            total += l.precio() * l.cantidad();
        }
        System.out.printf("TOTAL: %.2f EUR%n", total);
        if (!Utilidades.preguntarSiNo("Confirmar pedido?")) {
            System.out.println("Pedido cancelado");
            return;
        }

        try {
            con.setAutoCommit(false);
            int idPedido;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO pedidos (id_cliente) VALUES (?) RETURNING id_pedido")) {
                ps.setInt(1, idCliente);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    idPedido = rs.getInt(1);
                }
            }

            for (Linea l : carrito) {
                if (!Inventario.descontarStockTotal(con, l.idProducto(), l.cantidad())) {
                    con.rollback();
                    System.out.println("No hay stock suficiente de " + l.nombre() + ". Pedido cancelado");
                    return;
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO lineas_pedido (id_pedido, id_producto, cantidad) VALUES (?, ?, ?)")) {
                    ps.setInt(1, idPedido);
                    ps.setInt(2, l.idProducto());
                    ps.setInt(3, l.cantidad());
                    ps.executeUpdate();
                }
            }

            con.commit();
            System.out.printf("Pedido %d realizado. Total: %.2f EUR%n", idPedido, total);
        } catch (SQLException e) {
            Utilidades.rollback(con);
            System.out.println("No se ha podido hacer el pedido: " + e.getMessage());
        } finally {
            Utilidades.activarAutoCommit(con);
        }
    }

    private static int obtenerIdCliente(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id_cliente FROM clientes WHERE id_usuario = ?")) {
            ps.setInt(1, Usuarios.idUsuarioActual);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    // ============================================================
    // CRM: OPORTUNIDAD DE VENTA + CADENA AUTOMATICA
    // ============================================================
    public static void crearOportunidad(Connection con) throws SQLException {
        if (!Usuarios.puedeCrearOportunidad()) {
            System.out.println("No tienes permiso para crear oportunidades de venta");
            return;
        }
        // 1. Cliente
        verClientes(con);
        int idCliente = Utilidades.leerEntero("ID del cliente (0 = cancelar): ");
        if (idCliente == 0) return;
        String nombreCliente = null;
        try (PreparedStatement ps = con.prepareStatement("SELECT nombre FROM clientes WHERE id_cliente = ?")) {
            ps.setInt(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) nombreCliente = rs.getString(1);
            }
        }
        if (nombreCliente == null) {
            System.out.println("No existe ese cliente");
            return;
        }

        // 2. Producto (solo los que fabricamos)
        String sqlFab = "SELECT p.id_producto, p.nombre, p.precio, COALESCE(SUM(s.cantidad), 0) "
                      + "FROM productos p LEFT JOIN stock s ON s.id_producto = p.id_producto "
                      + "WHERE EXISTS (SELECT 1 FROM componentes_fabricacion c WHERE c.id_producto = p.id_producto) "
                      + "GROUP BY p.id_producto ORDER BY p.id_producto";
        boolean hay = false;
        System.out.println("\n--- PRODUCTOS QUE FABRICAMOS ---");
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sqlFab)) {
            while (rs.next()) {
                hay = true;
                System.out.printf("%d. %s - %.2f EUR - stock: %d%n", rs.getInt(1), rs.getString(2),
                        rs.getDouble(3), rs.getInt(4));
            }
        }
        if (!hay) {
            System.out.println("No hay productos con componentes definidos. Inventario debe crearlos primero");
            return;
        }
        int idProducto = Utilidades.leerEntero("ID del producto a ofrecer (0 = cancelar): ");
        if (idProducto == 0) return;
        String nombreProducto = null;
        String categoria = null;
        double precio = 0;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT nombre, categoria, precio FROM productos p WHERE id_producto = ? "
                + "AND EXISTS (SELECT 1 FROM componentes_fabricacion c WHERE c.id_producto = p.id_producto)")) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    nombreProducto = rs.getString(1);
                    categoria = rs.getString(2);
                    precio = rs.getDouble(3);
                }
            }
        }
        if (nombreProducto == null) {
            System.out.println("Ese producto no existe o no es un producto de fabricacion");
            return;
        }
        int cantidad = Utilidades.leerEntero("Cantidad (1-1000): ");
        if (cantidad < 1 || cantidad > 1000) {
            System.out.println("La cantidad debe estar entre 1 y 1000");
            return;
        }

        // 3. Guardar la oportunidad
        int idOportunidad;
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO oportunidades (id_cliente, id_producto, cantidad, id_usuario, estado) "
                + "VALUES (?, ?, ?, ?, 'Abierta') RETURNING id_oportunidad")) {
            ps.setInt(1, idCliente);
            ps.setInt(2, idProducto);
            ps.setInt(3, cantidad);
            ps.setInt(4, Usuarios.idUsuarioActual);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                idOportunidad = rs.getInt(1);
            }
        }

        // 4. El cliente decide
        System.out.printf("%n>> Propuesta para %s: %d x %s = %.2f EUR%n", nombreCliente, cantidad, nombreProducto,
                precio * cantidad);
        if (!Utilidades.preguntarSiNo("El cliente va a aprobar tu propuesta?")) {
            cambiarEstadoOportunidad(con, idOportunidad, "Rechazada");
            System.out.println(">> El cliente RECHAZA la propuesta. Oportunidad cerrada");
            return;
        }
        cambiarEstadoOportunidad(con, idOportunidad, "Aceptada");
        System.out.println(">> El cliente ACEPTA la propuesta");
        ejecutarCadena(con, idCliente, idProducto, categoria, precio, cantidad);
    }

    // Pedido -> stock -> (compras) -> fabricacion -> envio -> factura
    private static void ejecutarCadena(Connection con, int idCliente, int idProducto,
                                       String categoria, double precio, int cantidad) throws SQLException {
        // PASO 1: pedido de venta
        int idPedido;
        try {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO pedidos (id_cliente, fecha, estado) VALUES (?, CURRENT_DATE, 'Confirmado') "
                    + "RETURNING id_pedido")) {
                ps.setInt(1, idCliente);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    idPedido = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO lineas_pedido (id_pedido, id_producto, cantidad) VALUES (?, ?, ?)")) {
                ps.setInt(1, idPedido);
                ps.setInt(2, idProducto);
                ps.setInt(3, cantidad);
                ps.executeUpdate();
            }
            con.commit();
        } catch (SQLException e) {
            Utilidades.rollback(con);
            System.out.println("No se ha podido crear el pedido: " + e.getMessage());
            return;
        } finally {
            Utilidades.activarAutoCommit(con);
        }
        System.out.println("[VENTAS] Pedido de venta " + idPedido + " creado");

        // PASO 2: stock del producto terminado
        int faltan = Math.max(0, cantidad - Inventario.stockTotal(con, idProducto));
        if (faltan == 0) {
            System.out.println("[INVENTARIO] Hay stock suficiente. No hace falta fabricar");
        } else {
            System.out.println("[INVENTARIO] Stock insuficiente: hay que fabricar " + faltan + " unidad(es)");
            cambiarEstadoPedido(con, idPedido, "En fabricacion");

            // PASO 3: componentes. Si faltan, se crea el pedido de compra
            List<Inventario.Comp> comps = Inventario.cargarComponentes(con, idProducto, false);
            if (comps.isEmpty()) {
                detenerCadena(con, idPedido, "Falta material", "El producto no tiene componentes definidos");
                return;
            }
            for (Inventario.Comp c : comps) {
                int necesario = c.necesaria() * faltan;
                int deficit = necesario - c.stock();
                if (deficit <= 0) {
                    System.out.println("[INVENTARIO] Componente suficiente: " + c.nombre()
                            + " (hay " + c.stock() + ", hacen falta " + necesario + ")");
                    continue;
                }
                System.out.println("[COMPRAS] Falta componente: " + c.nombre() + " (hay " + c.stock()
                        + ", hacen falta " + necesario + ")");
                int idCompra = Inventario.crearCompra(con, c.id(), deficit);
                if (idCompra < 0) {
                    detenerCadena(con, idPedido, "Falta material", "No se pudo crear el pedido de compra");
                    return;
                }
                System.out.println("[COMPRAS] Pedido de compra " + idCompra + " creado: " + deficit + " x " + c.nombre());
                if (!Utilidades.preguntarSiNo("   El proveedor ACEPTA el pedido de compra?")) {
                    Inventario.actualizarEstadoCompra(con, idCompra, "Rechazada");
                    detenerCadena(con, idPedido, "Falta material", "El proveedor rechazo la compra");
                    return;
                }
                Inventario.actualizarEstadoCompra(con, idCompra, "Aceptada");
                if (!Utilidades.preguntarSiNo("   Ha LLEGADO ya el material al almacen?")) {
                    detenerCadena(con, idPedido, "Falta material",
                            "Material pendiente de recibir (usa la opcion 'Recibir pedido de compra')");
                    return;
                }
                if (!Inventario.recibirCompra(con, idCompra)) {
                    detenerCadena(con, idPedido, "Falta material", "No se pudo registrar la recepcion");
                    return;
                }
                System.out.println("[INVENTARIO] Material recibido y sumado al stock del componente");
            }

            // PASO 4: ordenes de fabricacion (1 orden = 1 unidad)
            System.out.println("[FABRICACION] Creando " + faltan + " orden(es) de fabricacion...");
            List<Integer> ordenes = new ArrayList<>();
            for (int i = 0; i < faltan; i++) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO fabricacion (id_pedido, id_producto, tipo, id_usuario, fecha, estado) "
                        + "VALUES (?, ?, ?, ?, CURRENT_DATE, 'En proceso') RETURNING id_montaje")) {
                    ps.setInt(1, idPedido);
                    ps.setInt(2, idProducto);
                    ps.setString(3, tipoDe(categoria));
                    ps.setInt(4, Usuarios.idUsuarioActual);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        ordenes.add(rs.getInt(1));
                    }
                }
            }
            if (!Utilidades.preguntarSiNo("   Se han TERMINADO de montar las " + faltan + " unidad(es)?")) {
                detenerCadena(con, idPedido, "En fabricacion",
                        "Fabricacion en proceso (termina las ordenes con la opcion 'Terminar orden de fabricacion')");
                return;
            }
            for (int idMontaje : ordenes) {
                if (!Inventario.terminarMontaje(con, idMontaje)) {
                    detenerCadena(con, idPedido, "En fabricacion", "No se pudo terminar una orden de fabricacion");
                    return;
                }
            }
            System.out.println("[FABRICACION] Ordenes terminadas. Stock actualizado");
        }

        // PASO 5: envio y factura
        cambiarEstadoPedido(con, idPedido, "Listo envio");
        if (!Utilidades.preguntarSiNo("[ENVIO] Confirmas el envio del pedido " + idPedido + " al cliente?")) {
            System.out.println(">> Pedido listo para envio. Ventas lo puede enviar y facturar despues (opcion 'Facturar un pedido pendiente')");
            return;
        }
        try {
            con.setAutoCommit(false);
            if (!Inventario.descontarStockTotal(con, idProducto, cantidad)) {
                con.rollback();
                detenerCadena(con, idPedido, "Listo envio", "No hay stock suficiente para enviar");
                return;
            }
            int idFactura;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO facturas (id_pedido, fecha, total) VALUES (?, CURRENT_DATE, ?) RETURNING id_factura")) {
                ps.setInt(1, idPedido);
                ps.setDouble(2, precio * cantidad);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    idFactura = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE pedidos SET estado = 'Facturado' WHERE id_pedido = ?")) {
                ps.setInt(1, idPedido);
                ps.executeUpdate();
            }
            con.commit();
            System.out.printf("[VENTAS] Pedido enviado. Factura %d emitida por %.2f EUR%n", idFactura, precio * cantidad);
            System.out.println(">> CADENA COMPLETADA CON EXITO");
        } catch (SQLException e) {
            Utilidades.rollback(con);
            System.out.println("No se ha podido completar el envio: " + e.getMessage());
        } finally {
            Utilidades.activarAutoCommit(con);
        }
    }

    private static void detenerCadena(Connection con, int idPedido, String estado, String motivo) {
        try {
            cambiarEstadoPedido(con, idPedido, estado);
        } catch (SQLException e) {
            System.out.println("No se ha podido actualizar el pedido: " + e.getMessage());
        }
        System.out.println(">> Cadena detenida: " + motivo);
        System.out.println("   El pedido " + idPedido + " queda como '" + estado + "'");
    }

    private static void cambiarEstadoPedido(Connection con, int idPedido, String estado) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE pedidos SET estado = ? WHERE id_pedido = ?")) {
            ps.setString(1, estado);
            ps.setInt(2, idPedido);
            ps.executeUpdate();
        }
    }

    private static void cambiarEstadoOportunidad(Connection con, int id, String estado) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE oportunidades SET estado = ? WHERE id_oportunidad = ?")) {
            ps.setString(1, estado);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    // El campo 'tipo' de fabricacion tiene 10 caracteres: bici o snow
    private static String tipoDe(String categoria) {
        String c = categoria == null ? "" : categoria.toLowerCase();
        if (c.contains("bici")) return "bici";
        if (c.contains("snow") || c.contains("tabla")) return "snow";
        return c.isEmpty() ? "otro" : (c.length() > 10 ? c.substring(0, 10) : c);
    }
}