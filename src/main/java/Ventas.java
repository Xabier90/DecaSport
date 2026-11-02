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

    // CRM: da de alta un cliente (un contacto, sin usuario para entrar en el programa)
    public static void crearCliente(Connection con) throws SQLException {
        if (!Usuarios.exigir("crm")) return;
        System.out.print("Nombre completo: ");
        String nombre = Utilidades.leerLinea().trim();
        if (nombre.isEmpty()) {
            System.out.println("El nombre es obligatorio");
            return;
        }
        System.out.print("Email (opcional): ");
        String email = Utilidades.leerLinea().trim();
        System.out.print("Telefono (opcional): ");
        String telefono = Utilidades.leerLinea().trim();
        System.out.print("Pais (opcional): ");
        String pais = Utilidades.leerLinea().trim();
        if (telefono.length() > 20 || nombre.length() > 100 || email.length() > 100 || pais.length() > 50) {
            System.out.println("Algun dato es demasiado largo. No se ha creado el cliente");
            return;
        }

        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO clientes (nombre, email, telefono, pais) VALUES (?, ?, ?, ?) RETURNING id_cliente")) {
            ps.setString(1, nombre);
            ps.setString(2, email.isEmpty() ? null : email);
            ps.setString(3, telefono.isEmpty() ? null : telefono);
            ps.setString(4, pais.isEmpty() ? null : pais);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                System.out.println("Cliente creado con el ID " + rs.getInt(1)
                        + ". Ya le puedes hacer oportunidades de venta");
            }
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

        // El orden importa: primero lo que apunta a los pedidos y despues los pedidos
        String pedidosDelCliente = "(SELECT id_pedido FROM pedidos WHERE id_cliente = ?)";
        String[] sentencias = {
            "DELETE FROM oportunidades WHERE id_cliente = ?",
            "DELETE FROM facturas WHERE id_pedido IN " + pedidosDelCliente,
            "DELETE FROM fabricacion WHERE id_pedido IN " + pedidosDelCliente,
            "DELETE FROM lineas_pedido WHERE id_pedido IN " + pedidosDelCliente,
            "DELETE FROM pedidos WHERE id_cliente = ?"
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
                   + "COALESCE(SUM(l.cantidad * l.precio), 0) "
                   + "FROM pedidos p JOIN clientes c ON c.id_cliente = p.id_cliente "
                   + "LEFT JOIN lineas_pedido l ON l.id_pedido = p.id_pedido "
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
        listarOportunidades(con, false);
    }

    // Con soloAbiertas=true solo salen las que esperan respuesta del cliente
    private static boolean listarOportunidades(Connection con, boolean soloAbiertas) throws SQLException {
        // Si ya es un pedido se muestra el precio con el que se vendio; si no, el precio actual
        String sql = "SELECT o.id_oportunidad, c.nombre, p.nombre, o.cantidad, o.cantidad * COALESCE("
                   + "(SELECT l.precio FROM lineas_pedido l WHERE l.id_pedido = o.id_pedido LIMIT 1), p.precio), "
                   + "COALESCE(u.nombre_usuario, '-'), o.fecha, o.estado, o.id_pedido "
                   + "FROM oportunidades o JOIN clientes c ON c.id_cliente = o.id_cliente "
                   + "JOIN productos p ON p.id_producto = o.id_producto "
                   + "LEFT JOIN usuarios u ON u.id_usuario = o.id_usuario "
                   + (soloAbiertas ? "WHERE o.estado = 'Abierta' " : "")
                   + "ORDER BY o.id_oportunidad";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println(soloAbiertas ? "\n--- OPORTUNIDADES ABIERTAS ---" : "\n--- OPORTUNIDADES DE VENTA ---");
            boolean hay = false;
            while (rs.next()) {
                hay = true;
                String pedido = rs.getString(9) == null ? "" : " | pedido " + rs.getString(9);
                System.out.printf("Oportunidad %d | %s | %d x %s = %.2f EUR | comercial: %s | %s | %s%s%n",
                        rs.getInt(1), rs.getString(2), rs.getInt(4), rs.getString(3), rs.getDouble(5),
                        rs.getString(6), rs.getString(7), rs.getString(8), pedido);
            }
            if (!hay) System.out.println(soloAbiertas ? "No hay oportunidades abiertas" : "No hay oportunidades");
            return hay;
        }
    }

    // ============================================================
    // ENVIO Y FACTURA (como en Odoo: dos pasos de dos departamentos)
    //   'Listo envio' --(Inventario valida el albaran: resta el stock)--> 'Enviado'
    //   'Enviado'     --(Ventas emite la factura)-----------------------> 'Facturado'
    // ============================================================

    // Pedidos en un estado con su cliente y total. Devuelve {id -> total} de los que salen
    private static java.util.Map<Integer, Double> listarPedidosEnEstado(Connection con, String estado, String titulo)
            throws SQLException {
        String sql = "SELECT p.id_pedido, c.nombre, p.fecha, COALESCE(SUM(l.cantidad), 0), "
                   + "COALESCE(SUM(l.cantidad * l.precio), 0) "
                   + "FROM pedidos p JOIN clientes c ON c.id_cliente = p.id_cliente "
                   + "LEFT JOIN lineas_pedido l ON l.id_pedido = p.id_pedido "
                   + "WHERE p.estado = ? AND NOT EXISTS (SELECT 1 FROM facturas f WHERE f.id_pedido = p.id_pedido) "
                   + "GROUP BY p.id_pedido, c.nombre ORDER BY p.id_pedido";
        java.util.Map<Integer, Double> totales = new java.util.LinkedHashMap<>();
        System.out.println("\n--- " + titulo + " ---");
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, estado);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    totales.put(rs.getInt(1), rs.getDouble(5));
                    System.out.printf("Pedido %d | %s | %s | %d unidad(es) | %.2f EUR%n",
                            rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getDouble(5));
                }
            }
        }
        return totales;
    }

    // Al entrar en Inventario: avisa de los pedidos que esperan a que se envien
    public static void avisarEnviosPendientes(Connection con) throws SQLException {
        int n;
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM pedidos WHERE estado = 'Listo envio'")) {
            rs.next();
            n = rs.getInt(1);
        }
        if (n > 0) {
            System.out.println("\n!! AVISO: hay " + n + " pedido(s) listos para enviar. "
                    + "Validalos en 'Validar un envio (albaran de salida)'");
        }
    }

    // INVENTARIO: valida el albaran de salida. Resta el stock y el pedido queda 'Enviado'
    public static void validarEnvioMenu(Connection con) throws SQLException {
        if (!Usuarios.exigir("inventario")) return;
        java.util.Map<Integer, Double> pedidos = listarPedidosEnEstado(con, "Listo envio", "PEDIDOS LISTOS PARA ENVIAR");
        if (pedidos.isEmpty()) {
            System.out.println("No hay pedidos listos para enviar");
            return;
        }
        int id = Utilidades.leerEntero("ID del pedido a enviar (0 = cancelar): ");
        if (id == 0) return;
        if (!pedidos.containsKey(id)) {
            System.out.println("Ese pedido no esta en la lista");
            return;
        }
        List<Linea> lineas = cargarLineas(con, id);
        for (Linea l : lineas) System.out.println("   - " + l.cantidad() + " x " + l.nombre());
        if (!Utilidades.preguntarSiNo("Validar el albaran y enviar el pedido " + id + "?")) {
            System.out.println("Cancelado");
            return;
        }
        try {
            con.setAutoCommit(false);
            for (Linea l : lineas) {
                if (!Inventario.descontarStockTotal(con, l.idProducto(), l.cantidad())) {
                    con.rollback();
                    System.out.println("No hay stock suficiente de " + l.nombre() + " para enviar el pedido " + id);
                    return;
                }
            }
            cambiarEstadoPedido(con, id, "Enviado");
            con.commit();
            System.out.println("[INVENTARIO] Albaran validado: pedido " + id + " ENVIADO. Ventas ya lo puede facturar");
        } catch (SQLException e) {
            Utilidades.rollback(con);
            System.out.println("No se ha podido enviar el pedido: " + e.getMessage());
            return;
        } finally {
            Utilidades.activarAutoCommit(con);
        }
        // Si algun producto que compramos se ha quedado sin stock, se pregunta si se pide mas
        for (Linea l : lineas) Inventario.comprobarAgotado(con, l.idProducto());
    }

    // VENTAS: factura los pedidos que ya ha enviado Inventario
    public static void facturarPedido(Connection con) throws SQLException {
        if (!Usuarios.exigir("ventas")) return;
        java.util.Map<Integer, Double> totales = listarPedidosEnEstado(con, "Enviado", "PEDIDOS ENVIADOS SIN FACTURAR");
        if (totales.isEmpty()) {
            System.out.println("No hay pedidos enviados sin facturar (Inventario los envia en 'Validar un envio')");
            return;
        }
        int id = Utilidades.leerEntero("ID del pedido a facturar (0 = cancelar): ");
        if (id == 0) return;
        if (!totales.containsKey(id)) {
            System.out.println("Ese pedido no esta en la lista");
            return;
        }
        if (!Utilidades.preguntarSiNo(String.format("Se facturara el pedido %d por %.2f EUR. Confirmar?",
                id, totales.get(id)))) {
            System.out.println("Cancelado");
            return;
        }
        facturar(con, id, totales.get(id));
    }

    // Emite la factura y deja el pedido 'Facturado'
    private static void facturar(Connection con, int idPedido, double total) throws SQLException {
        try {
            con.setAutoCommit(false);
            int idFactura;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO facturas (id_pedido, fecha, total) VALUES (?, CURRENT_DATE, ?) RETURNING id_factura")) {
                ps.setInt(1, idPedido);
                ps.setDouble(2, total);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    idFactura = rs.getInt(1);
                }
            }
            cambiarEstadoPedido(con, idPedido, "Facturado");
            con.commit();
            System.out.printf("[VENTAS] Factura %d emitida por %.2f EUR: pedido %d FACTURADO%n", idFactura, total, idPedido);
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
            String nombre;
            double precio;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT nombre, precio FROM productos WHERE id_producto = ?")) {
                ps.setInt(1, idProducto);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("No existe ese producto");
                        continue;
                    }
                    nombre = rs.getString(1);
                    precio = rs.getDouble(2);
                }
            }
            // Disponible = stock - lo reservado por pedidos en espera - lo que ya hay en el carrito
            // (asi los pedidos que llevan mas tiempo esperando se sirven primero)
            int yaEnCarrito = 0;
            for (Linea l : carrito) if (l.idProducto() == idProducto) yaEnCarrito += l.cantidad();
            int disponible = Math.max(0, Inventario.stockTotal(con, idProducto)
                    - Inventario.reservado(con, idProducto) - yaEnCarrito);
            int cantidad = Utilidades.leerEntero("Cantidad (disponibles ahora: " + disponible + "): ");
            if (cantidad <= 0) {
                System.out.println("La cantidad debe ser mayor que 0");
                continue;
            }
            if (cantidad > disponible) {
                System.out.println("Ahora mismo solo hay " + disponible + " unidad(es) disponibles de " + nombre);
                if (!Utilidades.preguntarSiNo("Lo pides igualmente? Tu pedido esperara y se tramitara "
                        + "AUTOMATICAMENTE en cuanto haya stock")) {
                    continue;
                }
            }
            carrito.add(new Linea(idProducto, nombre, precio, cantidad));
            System.out.println("Anadido: " + cantidad + " x " + nombre);
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
                    "INSERT INTO pedidos (id_cliente, estado) VALUES (?, ?) RETURNING id_pedido")) {
                ps.setInt(1, idCliente);
                ps.setString(2, Inventario.ESPERANDO);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    idPedido = rs.getInt(1);
                }
            }
            for (Linea l : carrito) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO lineas_pedido (id_pedido, id_producto, cantidad, precio) VALUES (?, ?, ?, ?)")) {
                    ps.setInt(1, idPedido);
                    ps.setInt(2, l.idProducto());
                    ps.setInt(3, l.cantidad());
                    ps.setDouble(4, l.precio());
                    ps.executeUpdate();
                }
            }
            // Mismo flujo que un pedido del CRM: se reserva o se lanza la compra/fabricacion
            confirmarPedido(con, idPedido);
            con.commit();
            System.out.printf("Pedido %d realizado. Total: %.2f EUR. Puedes seguirlo en 'Mis pedidos'%n", idPedido, total);
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
    // PEDIDOS EN ESPERA DE STOCK
    // ============================================================

    // Si hay stock para todo el pedido (respetando a los pedidos mas antiguos sin enviar, tambien
    // los del CRM), el pedido queda 'Listo envio' con sus unidades reservadas. El stock no se
    // resta hasta que Inventario valida el envio
    private static boolean intentarServir(Connection con, int idPedido) throws SQLException {
        for (Linea l : agruparPorProducto(cargarLineas(con, idPedido))) {
            if (l.cantidad() > disponible(con, l.idProducto(), idPedido)) return false;
        }
        cambiarEstadoPedido(con, idPedido, "Listo envio");
        return true;
    }

    // Un producto puede estar en varias lineas del pedido: se suman sus cantidades
    private static java.util.Collection<Linea> agruparPorProducto(List<Linea> lineas) {
        java.util.Map<Integer, Linea> porProducto = new java.util.LinkedHashMap<>();
        for (Linea l : lineas) {
            porProducto.merge(l.idProducto(), l, (a, b) ->
                    new Linea(a.idProducto(), a.nombre(), a.precio(), a.cantidad() + b.cantidad()));
        }
        return porProducto.values();
    }

    // Unidades del producto reservadas por pedidos sin enviar mas antiguos que este
    // (de clientes o del CRM): se sirven por orden de llegada
    private static int reservadoAntesDe(Connection con, int idProducto, int idPedido) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COALESCE(SUM(l.cantidad), 0) FROM lineas_pedido l "
                + "JOIN pedidos pe ON pe.id_pedido = l.id_pedido "
                + "WHERE pe.estado IN (" + Inventario.ESTADOS_RESERVA + ") "
                + "AND l.id_producto = ? AND pe.id_pedido < ?")) {
            ps.setInt(1, idProducto);
            ps.setInt(2, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // Se llama cada vez que entra stock: tramita, por orden de llegada, los pedidos que ya se pueden servir
    public static void procesarPedidosEnEspera(Connection con) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        List<String> clientes = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT p.id_pedido, c.nombre FROM pedidos p JOIN clientes c ON c.id_cliente = p.id_cliente "
                + "WHERE p.estado = ? ORDER BY p.id_pedido")) {
            ps.setString(1, Inventario.ESPERANDO);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                    clientes.add(rs.getString(2));
                }
            }
        }
        for (int i = 0; i < ids.size(); i++) {
            try {
                con.setAutoCommit(false);
                if (intentarServir(con, ids.get(i))) {
                    con.commit();
                    System.out.println("[VENTAS] Ya hay stock para el pedido " + ids.get(i) + " de " + clientes.get(i)
                            + " que estaba esperando: TRAMITADO AUTOMATICAMENTE (listo para enviar)");
                } else {
                    con.rollback();
                }
            } catch (SQLException e) {
                Utilidades.rollback(con);
                System.out.println("No se ha podido tramitar el pedido " + ids.get(i) + ": " + e.getMessage());
            } finally {
                Utilidades.activarAutoCommit(con);
            }
        }
    }

    // CLIENTE: cancelar uno de sus pedidos que todavia espera material (no se habia restado nada)
    public static void cancelarPedidoEnEspera(Connection con) throws SQLException {
        int idCliente = obtenerIdCliente(con);
        if (idCliente == -1) {
            System.out.println("No se ha encontrado tu ficha de cliente");
            return;
        }
        mostrarPedidosDeCliente(con, idCliente, true);
        int id = Utilidades.leerEntero("ID del pedido a cancelar (0 = volver): ");
        if (id == 0) return;
        if (!Utilidades.preguntarSiNo("Seguro que quieres cancelar el pedido " + id + "?")) return;
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE pedidos SET estado = 'Cancelado' WHERE id_pedido = ? AND id_cliente = ? AND estado = ?")) {
            ps.setInt(1, id);
            ps.setInt(2, idCliente);
            ps.setString(3, Inventario.ESPERANDO);
            if (ps.executeUpdate() == 0) {
                System.out.println("Ese pedido no es tuyo o ya no esta esperando material");
                return;
            }
        }
        // Lo que se iba a fabricar para este pedido ya no hace falta
        try (PreparedStatement ps = con.prepareStatement("UPDATE fabricacion SET estado = 'Cancelada' "
                + "WHERE id_pedido = ? AND estado NOT IN ('Terminada', 'Cancelada')")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        System.out.println("Pedido " + id + " cancelado");
    }

    // ============================================================
    // HISTORIAL DE COMPRAS
    // ============================================================

    // CLIENTE: sus propios pedidos
    public static void misPedidos(Connection con) throws SQLException {
        int idCliente = obtenerIdCliente(con);
        if (idCliente == -1) {
            System.out.println("No se ha encontrado tu ficha de cliente");
            return;
        }
        mostrarPedidosDeCliente(con, idCliente, false);
    }

    // CRM y VENTAS: resumen de lo que ha comprado cada cliente y el detalle de uno
    public static void verComprasDeClientes(Connection con) throws SQLException {
        if (!Usuarios.tieneAcceso("crm") && !Usuarios.tieneAcceso("ventas")) {
            System.out.println("No tienes permiso para ver las compras de los clientes");
            return;
        }
        String sql = "SELECT c.id_cliente, c.nombre, COALESCE(u.nombre_usuario, '-'), "
                   + "COUNT(DISTINCT pe.id_pedido) FILTER (WHERE pe.estado <> 'Cancelado'), "
                   + "COALESCE(SUM(l.cantidad) FILTER (WHERE pe.estado <> 'Cancelado'), 0), "
                   + "COALESCE((SELECT SUM(f.total) FROM facturas f JOIN pedidos p2 ON p2.id_pedido = f.id_pedido "
                   + "WHERE p2.id_cliente = c.id_cliente), 0), "
                   + "COUNT(DISTINCT pe.id_pedido) FILTER (WHERE pe.estado = '" + Inventario.ESPERANDO + "') "
                   + "FROM clientes c LEFT JOIN usuarios u ON u.id_usuario = c.id_usuario "
                   + "LEFT JOIN pedidos pe ON pe.id_cliente = c.id_cliente "
                   + "LEFT JOIN lineas_pedido l ON l.id_pedido = pe.id_pedido "
                   + "GROUP BY c.id_cliente, c.nombre, u.nombre_usuario ORDER BY 6 DESC, c.id_cliente";
        System.out.println("\n--- COMPRAS POR CLIENTE ---");
        boolean hay = false;
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                hay = true;
                System.out.printf("%d. %s (usuario: %s) | %d pedido(s) | %d unidad(es) | facturado: %.2f EUR%s%n",
                        rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getDouble(6),
                        rs.getInt(7) > 0 ? " | " + rs.getInt(7) + " esperando material" : "");
            }
        }
        if (!hay) {
            System.out.println("No hay clientes");
            return;
        }
        int id = Utilidades.leerEntero("ID del cliente para ver el detalle (0 = volver): ");
        if (id == 0) return;
        if (!Utilidades.existe(con, "SELECT 1 FROM clientes WHERE id_cliente = ?", id)) {
            System.out.println("No existe ese cliente");
            return;
        }
        mostrarPedidosDeCliente(con, id, false);
    }

    // Pedidos de un cliente con sus productos. soloEnEspera=true: solo los que esperan stock
    private static void mostrarPedidosDeCliente(Connection con, int idCliente, boolean soloEnEspera) throws SQLException {
        String sql = "SELECT pe.id_pedido, pe.fecha, pe.estado, "
                   + "(SELECT MIN(f.id_factura) FROM facturas f WHERE f.id_pedido = pe.id_pedido), "
                   + "pr.nombre, l.cantidad, l.cantidad * l.precio "
                   + "FROM pedidos pe JOIN lineas_pedido l ON l.id_pedido = pe.id_pedido "
                   + "JOIN productos pr ON pr.id_producto = l.id_producto "
                   + "WHERE pe.id_cliente = ? " + (soloEnEspera ? "AND pe.estado = ? " : "")
                   + "ORDER BY pe.id_pedido DESC, pr.nombre";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            if (soloEnEspera) ps.setString(2, Inventario.ESPERANDO);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println(soloEnEspera ? "\n--- PEDIDOS ESPERANDO MATERIAL ---" : "\n--- HISTORIAL DE PEDIDOS ---");
                int pedidoActual = -1;
                double totalPedido = 0;
                double totalCliente = 0;
                while (rs.next()) {
                    int idPedido = rs.getInt(1);
                    if (idPedido != pedidoActual) {
                        if (pedidoActual != -1) System.out.printf("      Total del pedido: %.2f EUR%n", totalPedido);
                        pedidoActual = idPedido;
                        totalPedido = 0;
                        String factura = rs.getString(4) == null ? "" : " | factura " + rs.getString(4);
                        System.out.printf("%nPedido %d | %s | %s%s%n", idPedido, rs.getString(2),
                                estadoLegible(rs.getString(3)), factura);
                    }
                    System.out.printf("   - %d x %s = %.2f EUR%n", rs.getInt(6), rs.getString(5), rs.getDouble(7));
                    totalPedido += rs.getDouble(7);
                    if (!"Cancelado".equals(rs.getString(3))) totalCliente += rs.getDouble(7);
                }
                if (pedidoActual == -1) {
                    System.out.println(soloEnEspera ? "No hay pedidos esperando material" : "Todavia no hay pedidos");
                    return;
                }
                System.out.printf("      Total del pedido: %.2f EUR%n", totalPedido);
                if (!soloEnEspera) System.out.printf("%nTOTAL COMPRADO (sin cancelados): %.2f EUR%n", totalCliente);
            }
        }
    }

    // Texto claro del estado de un pedido para el historial
    private static String estadoLegible(String estado) {
        if (estado == null) return "-";
        return switch (estado) {
            case "Listo envio" -> "Preparado (pendiente de envio)";
            case "Enviado" -> "Enviado (pendiente de factura)";
            case Inventario.ESPERANDO -> "Esperando material (se tramitara automaticamente)";
            case "Facturado" -> "Enviado y facturado";
            default -> estado;
        };
    }

    // ============================================================
    // CRM: OPORTUNIDADES DE VENTA
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

        // 2. Producto: se puede ofrecer cualquiera del catalogo
        String sqlCatalogo = "SELECT p.id_producto, p.nombre, p.precio, "
                + "COALESCE((SELECT SUM(s.cantidad) FROM stock s WHERE s.id_producto = p.id_producto), 0), "
                + "CASE WHEN EXISTS (SELECT 1 FROM componentes_fabricacion c WHERE c.id_producto = p.id_producto) "
                + "THEN 'lo fabricamos' ELSE 'proveedor: ' || COALESCE(pv.nombre, '(sin asignar)') END "
                + "FROM productos p LEFT JOIN proveedores pv ON pv.id_proveedor = p.id_proveedor "
                + "ORDER BY p.id_producto";
        boolean hay = false;
        System.out.println("\n--- CATALOGO DE PRODUCTOS ---");
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sqlCatalogo)) {
            while (rs.next()) {
                hay = true;
                System.out.printf("%d. %s - %.2f EUR - stock: %d (%s)%n", rs.getInt(1), rs.getString(2),
                        rs.getDouble(3), rs.getInt(4), rs.getString(5));
            }
        }
        if (!hay) {
            System.out.println("No hay productos en el catalogo. Inventario debe crearlos primero");
            return;
        }
        int idProducto = Utilidades.leerEntero("ID del producto a ofrecer (0 = cancelar): ");
        if (idProducto == 0) return;
        String nombreProducto = null;
        double precio = 0;
        try (PreparedStatement ps = con.prepareStatement("SELECT nombre, precio FROM productos WHERE id_producto = ?")) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    nombreProducto = rs.getString(1);
                    precio = rs.getDouble(2);
                }
            }
        }
        if (nombreProducto == null) {
            System.out.println("No existe ese producto");
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
        System.out.printf("%n>> Oportunidad %d: propuesta para %s: %d x %s = %.2f EUR%n",
                idOportunidad, nombreCliente, cantidad, nombreProducto, precio * cantidad);

        // 4. El cliente decide (o se lo piensa y la oportunidad queda abierta)
        decidirOportunidad(con, idOportunidad);
    }

    // CRM: dar respuesta a una oportunidad que se quedo abierta
    public static void responderOportunidad(Connection con) throws SQLException {
        if (!Usuarios.puedeCrearOportunidad()) {
            System.out.println("No tienes permiso para gestionar oportunidades de venta");
            return;
        }
        if (!listarOportunidades(con, true)) return;
        int id = Utilidades.leerEntero("ID de la oportunidad (0 = cancelar): ");
        if (id == 0) return;
        if (!Utilidades.existe(con, "SELECT 1 FROM oportunidades WHERE id_oportunidad = ? AND estado = 'Abierta'", id)) {
            System.out.println("Esa oportunidad no existe o ya esta cerrada");
            return;
        }
        decidirOportunidad(con, id);
    }

    private static void decidirOportunidad(Connection con, int idOportunidad) throws SQLException {
        System.out.println("Respuesta del cliente:   1. Acepta   2. Rechaza   3. Se lo piensa (queda abierta)");
        int op;
        do {
            op = Utilidades.leerEntero("Opcion: ");
        } while (op < 1 || op > 3);
        if (op == 3) {
            System.out.println(">> La oportunidad queda ABIERTA. Respondela despues desde el menu de CRM");
        } else if (op == 2) {
            cambiarEstadoOportunidad(con, idOportunidad, "Rechazada");
            System.out.println(">> El cliente RECHAZA la propuesta. Oportunidad cerrada");
        } else {
            int idPedido = aceptarOportunidad(con, idOportunidad);
            if (idPedido > 0) confirmarPedido(con, idPedido);
        }
    }

    // Oportunidad aceptada: se crea el pedido de venta y se enlaza con la oportunidad. Devuelve el pedido o -1
    private static int aceptarOportunidad(Connection con, int idOportunidad) {
        try {
            con.setAutoCommit(false);
            int idPedido;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO pedidos (id_cliente, fecha, estado) "
                    + "SELECT id_cliente, CURRENT_DATE, ? FROM oportunidades WHERE id_oportunidad = ? "
                    + "RETURNING id_pedido")) {
                ps.setString(1, Inventario.ESPERANDO);
                ps.setInt(2, idOportunidad);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    idPedido = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO lineas_pedido (id_pedido, id_producto, cantidad, precio) "
                    + "SELECT ?, o.id_producto, o.cantidad, p.precio FROM oportunidades o "
                    + "JOIN productos p ON p.id_producto = o.id_producto WHERE o.id_oportunidad = ?")) {
                ps.setInt(1, idPedido);
                ps.setInt(2, idOportunidad);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE oportunidades SET estado = 'Aceptada', id_pedido = ? WHERE id_oportunidad = ?")) {
                ps.setInt(1, idPedido);
                ps.setInt(2, idOportunidad);
                ps.executeUpdate();
            }
            con.commit();
            System.out.println(">> El cliente ACEPTA la propuesta");
            System.out.println("[VENTAS] Pedido de venta " + idPedido + " creado");
            return idPedido;
        } catch (SQLException e) {
            Utilidades.rollback(con);
            System.out.println("No se ha podido crear el pedido: " + e.getMessage());
            return -1;
        } finally {
            Utilidades.activarAutoCommit(con);
        }
    }

    // ============================================================
    // CONFIRMAR UN PEDIDO: el mismo flujo para los del CRM y los de clientes (como en Odoo)
    //   - hay stock libre de todo  -> 'Listo envio' (las unidades quedan reservadas)
    //   - falta algo               -> 'Esperando material' y se lanza solo lo que falta:
    //        producto que se compra   -> pedido de compra 'Pendiente' (Compras registra la llegada)
    //        producto que se fabrica  -> orden de fabricacion (Fabricacion la empieza y la termina)
    // Cuando entra stock, procesarPedidosEnEspera pasa a 'Listo envio' los que ya se pueden servir
    // ============================================================
    private static void confirmarPedido(Connection con, int idPedido) throws SQLException {
        if (intentarServir(con, idPedido)) {
            System.out.println(">> Pedido " + idPedido + " LISTO PARA ENVIAR (hay stock de todo). Siguiente paso: "
                    + "Inventario > 'Validar un envio' y despues Ventas > 'Facturar un pedido enviado'");
            return;
        }
        for (Linea l : agruparPorProducto(cargarLineas(con, idPedido))) {
            int faltan = faltanParaPedido(con, l, idPedido);
            if (faltan <= 0) continue;
            if (Inventario.seFabrica(con, l.idProducto())) {
                Inventario.crearOrdenParaPedido(con, idPedido, l.idProducto(), l.nombre(), faltan);
            } else {
                Inventario.pedirProductoParaPedido(con, l.idProducto(), l.nombre(), faltan);
            }
        }
        System.out.println(">> Pedido " + idPedido + " ESPERANDO MATERIAL. Pasara solo a 'Listo envio' cuando "
                + "Compras registre la llegada o Fabricacion termine la orden");
    }

    // Unidades que hay que conseguir: lo que piden este pedido y los mas antiguos sin enviar,
    // menos el stock y lo que ya viene en camino (compras u ordenes de fabricacion abiertas)
    private static int faltanParaPedido(Connection con, Linea l, int idPedido) throws SQLException {
        int demanda = reservadoAntesDe(con, l.idProducto(), idPedido) + l.cantidad();
        return demanda - Inventario.stockTotal(con, l.idProducto()) - Inventario.enCamino(con, l.idProducto());
    }

    // Stock que puede usar el pedido: sus propias unidades no cuentan como reservadas
    private static int disponible(Connection con, int idProducto, int idPedido) throws SQLException {
        return Math.max(0, Inventario.stockTotal(con, idProducto) - reservadoAntesDe(con, idProducto, idPedido));
    }

    private static List<Linea> cargarLineas(Connection con, int idPedido) throws SQLException {
        List<Linea> lineas = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT l.id_producto, p.nombre, l.precio, l.cantidad FROM lineas_pedido l "
                + "JOIN productos p ON p.id_producto = l.id_producto WHERE l.id_pedido = ? ORDER BY l.id_producto")) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lineas.add(new Linea(rs.getInt(1), rs.getString(2), rs.getDouble(3), rs.getInt(4)));
                }
            }
        }
        return lineas;
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
}
