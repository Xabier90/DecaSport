import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    // Datos de conexion: cambia la contrasena por la tuya
    private static final String URL = "jdbc:postgresql://localhost:1234/decasport";
    private static final String USUARIO = "postgres";
    private static final String CONTRASENA = "yelingham";

    private static final Scanner sc = new Scanner(System.in);

    // Usuario que ha iniciado sesion
    private static int idUsuarioActual;
    private static String rolActual;

    // Un producto dentro de un pedido
    private record Linea(int idProducto, String nombre, double precio, int cantidad) {}

    // ------------------------------------------------------------
    // PROGRAMA PRINCIPAL
    // ------------------------------------------------------------
    public static void main(String[] args) {
        try (Connection con = DriverManager.getConnection(URL, USUARIO, CONTRASENA)) {
            System.out.println("Conectado a la base de datos decasport");
            crearAdminPorDefecto(con);
            menuInicial(con);
        } catch (SQLException e) {
            System.out.println("Error con la base de datos: " + e.getMessage());
        }
    }

    // Si no hay ningun administrador, crea uno: admin / admin123
    private static void crearAdminPorDefecto(Connection con) throws SQLException {
        String sql = "SELECT COUNT(*) FROM usuarios u JOIN roles r ON r.id_rol = u.id_rol "
                   + "WHERE r.nombre_rol = 'administrador'";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            if (rs.getInt(1) == 0) {
                crearUsuario(con, "admin", "admin123", "administrador");
                System.out.println("Se ha creado el administrador por defecto: admin / admin123");
            }
        }
    }

    // ------------------------------------------------------------
    // MENU INICIAL: iniciar sesion o registrarse
    // ------------------------------------------------------------
    private static void menuInicial(Connection con) {
        while (true) {
            System.out.println("\n=== DECASPORT ===");
            System.out.println("1. Iniciar sesion");
            System.out.println("2. Registrarse como cliente");
            System.out.println("0. Salir");
            int op = leerEntero("Opcion: ");
            try {
                switch (op) {
                    case 1 -> {
                        if (iniciarSesion(con)) {
                            if (rolActual.equals("cliente")) menuCliente(con);
                            else menuPersonal(con);
                        }
                    }
                    case 2 -> registrarCliente(con);
                    case 0 -> {
                        System.out.println("Hasta luego");
                        return;
                    }
                    default -> System.out.println("Opcion no valida");
                }
            } catch (SQLException e) {
                System.out.println("Error en la base de datos: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------
    // MENU DEL ADMINISTRADOR Y DEL TRABAJADOR
    // ------------------------------------------------------------
    private static void menuPersonal(Connection con) {
        boolean esAdmin = rolActual.equals("administrador");
        while (true) {
            System.out.println("\n=== MENU " + rolActual.toUpperCase() + " ===");
            System.out.println("1. Ver productos");
            System.out.println("2. Anadir producto");
            System.out.println("3. Editar producto");
            System.out.println("4. Borrar producto");
            if (esAdmin) System.out.println("5. Crear usuario (trabajador o administrador)");
            System.out.println("0. Cerrar sesion");
            int op = leerEntero("Opcion: ");
            try {
                switch (op) {
                    case 1 -> verProductos(con);
                    case 2 -> anadirProducto(con);
                    case 3 -> editarProducto(con);
                    case 4 -> borrarProducto(con);
                    case 5 -> {
                        if (esAdmin) crearUsuarioPersonal(con);
                        else System.out.println("Opcion no valida");
                    }
                    case 0 -> {
                        return;
                    }
                    default -> System.out.println("Opcion no valida");
                }
            } catch (SQLException e) {
                System.out.println("Error en la base de datos: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------
    // MENU DEL CLIENTE: solo puede ver y comprar
    // ------------------------------------------------------------
    private static void menuCliente(Connection con) {
        while (true) {
            System.out.println("\n=== MENU CLIENTE ===");
            System.out.println("1. Ver productos");
            System.out.println("2. Hacer un pedido");
            System.out.println("0. Cerrar sesion");
            int op = leerEntero("Opcion: ");
            try {
                switch (op) {
                    case 1 -> verProductos(con);
                    case 2 -> hacerPedido(con);
                    case 0 -> {
                        return;
                    }
                    default -> System.out.println("Opcion no valida");
                }
            } catch (SQLException e) {
                System.out.println("Error en la base de datos: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------
    // USUARIOS: registro e inicio de sesion
    // ------------------------------------------------------------
    private static void registrarCliente(Connection con) {
        System.out.print("Nombre de usuario: ");
        String usuario = sc.nextLine().trim();
        System.out.print("Contrasena: ");
        String pass = sc.nextLine();
        System.out.print("Nombre completo: ");
        String nombre = sc.nextLine().trim();
        System.out.print("Email: ");
        String email = sc.nextLine().trim();

        if (usuario.isEmpty() || pass.isEmpty() || nombre.isEmpty()) {
            System.out.println("Usuario, contrasena y nombre son obligatorios");
            return;
        }

        try {
            con.setAutoCommit(false);
            int idUsuario = crearUsuario(con, usuario, pass, "cliente");
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO clientes (id_usuario, nombre, email) VALUES (?, ?, ?)")) {
                ps.setInt(1, idUsuario);
                ps.setString(2, nombre);
                ps.setString(3, email);
                ps.executeUpdate();
            }
            con.commit();
            System.out.println("Registro completado. Ya puedes iniciar sesion");
        } catch (SQLException e) {
            rollback(con);
            if ("23505".equals(e.getSQLState())) {
                System.out.println("Ese nombre de usuario ya existe");
            } else {
                System.out.println("Error al registrar: " + e.getMessage());
            }
        } finally {
            activarAutoCommit(con);
        }
    }

    private static boolean iniciarSesion(Connection con) throws SQLException {
        System.out.print("Usuario: ");
        String usuario = sc.nextLine().trim();
        System.out.print("Contrasena: ");
        String pass = sc.nextLine();

        String sql = "SELECT u.id_usuario, r.nombre_rol FROM usuarios u "
                   + "JOIN roles r ON r.id_rol = u.id_rol "
                   + "WHERE u.nombre_usuario = ? AND u.contrasena = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ps.setString(2, cifrar(pass));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    idUsuarioActual = rs.getInt("id_usuario");
                    rolActual = rs.getString("nombre_rol");
                    System.out.println("Bienvenido, " + usuario + " (" + rolActual + ")");
                    return true;
                }
            }
        }
        System.out.println("Usuario o contrasena incorrectos");
        return false;
    }

    // Solo el administrador puede crear trabajadores y otros administradores
    private static void crearUsuarioPersonal(Connection con) throws SQLException {
        System.out.print("Nombre de usuario: ");
        String usuario = sc.nextLine().trim();
        System.out.print("Contrasena: ");
        String pass = sc.nextLine();
        System.out.println("Rol: 1. trabajador   2. administrador");
        int op = leerEntero("Opcion: ");
        String rol = (op == 2) ? "administrador" : "trabajador";

        if (usuario.isEmpty() || pass.isEmpty()) {
            System.out.println("Usuario y contrasena son obligatorios");
            return;
        }
        try {
            crearUsuario(con, usuario, pass, rol);
            System.out.println("Usuario " + usuario + " creado como " + rol);
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                System.out.println("Ese nombre de usuario ya existe");
            } else {
                throw e;
            }
        }
    }

    private static int crearUsuario(Connection con, String nombre, String pass, String nombreRol)
            throws SQLException {
        String sql = "INSERT INTO usuarios (nombre_usuario, contrasena, id_rol) "
                   + "VALUES (?, ?, (SELECT id_rol FROM roles WHERE nombre_rol = ?)) "
                   + "RETURNING id_usuario";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ps.setString(2, cifrar(pass));
            ps.setString(3, nombreRol);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // La contrasena se guarda cifrada (SHA-256), nunca en texto normal
    private static String cifrar(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(texto.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------
    // PRODUCTOS
    // ------------------------------------------------------------
    private static void verProductos(Connection con) throws SQLException {
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

    private static void anadirProducto(Connection con) throws SQLException {
        System.out.print("Nombre: ");
        String nombre = sc.nextLine().trim();
        System.out.print("Categoria: ");
        String categoria = sc.nextLine().trim();
        double precio = leerDecimal("Precio: ");
        System.out.println("Almacen: 1. Espana   2. Francia");
        String almacen = (leerEntero("Opcion: ") == 2) ? "Francia" : "España";
        int cantidad = leerEntero("Cantidad inicial en stock: ");

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
        } catch (SQLException e) {
            rollback(con);
            System.out.println("No se ha podido anadir el producto: " + e.getMessage());
        } finally {
            activarAutoCommit(con);
        }
    }

    private static void editarProducto(Connection con) throws SQLException {
        int id = leerEntero("ID del producto a editar: ");

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
        String nombre = sc.nextLine().trim();
        if (nombre.isEmpty()) nombre = nombreActual;

        System.out.print("Categoria [" + (categoriaActual == null ? "" : categoriaActual) + "]: ");
        String categoria = sc.nextLine().trim();
        if (categoria.isEmpty()) categoria = categoriaActual;

        System.out.print("Precio [" + precioActual + "]: ");
        String textoPrecio = sc.nextLine().trim().replace(',', '.');
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

    private static void borrarProducto(Connection con) throws SQLException {
        int id = leerEntero("ID del producto a borrar: ");
        System.out.print("Seguro que quieres borrarlo? (s/n): ");
        if (!sc.nextLine().trim().equalsIgnoreCase("s")) {
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
                System.out.println("Producto borrado");
            }
        } catch (SQLException e) {
            rollback(con);
            if ("23503".equals(e.getSQLState())) {
                System.out.println("No se puede borrar: el producto ya aparece en pedidos, compras o montajes");
            } else {
                System.out.println("No se ha podido borrar: " + e.getMessage());
            }
        } finally {
            activarAutoCommit(con);
        }
    }

    // ------------------------------------------------------------
    // PEDIDOS (cliente): al comprar se resta el stock
    // ------------------------------------------------------------
    private static void hacerPedido(Connection con) throws SQLException {
        int idCliente = obtenerIdCliente(con);
        if (idCliente == -1) {
            System.out.println("No se ha encontrado tu ficha de cliente");
            return;
        }

        List<Linea> carrito = new ArrayList<>();
        verProductos(con);

        while (true) {
            int idProducto = leerEntero("ID del producto a comprar (0 para terminar): ");
            if (idProducto == 0) break;
            int cantidad = leerEntero("Cantidad: ");
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
        System.out.print("Confirmar pedido? (s/n): ");
        if (!sc.nextLine().trim().equalsIgnoreCase("s")) {
            System.out.println("Pedido cancelado");
            return;
        }

        // Todo el pedido va en una transaccion: o se guarda entero o no se guarda nada
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
                if (!descontarStock(con, l.idProducto(), l.cantidad())) {
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
            rollback(con);
            System.out.println("No se ha podido hacer el pedido: " + e.getMessage());
        } finally {
            activarAutoCommit(con);
        }
    }

    // Resta la cantidad del stock. Devuelve false si no hay suficiente en ningun almacen.
    // FOR UPDATE bloquea la fila para que dos pedidos a la vez no se pisen.
    private static boolean descontarStock(Connection con, int idProducto, int cantidad) throws SQLException {
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

    private static int obtenerIdCliente(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id_cliente FROM clientes WHERE id_usuario = ?")) {
            ps.setInt(1, idUsuarioActual);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    // ------------------------------------------------------------
    // AYUDAS
    // ------------------------------------------------------------
    private static int leerEntero(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Escribe un numero entero");
            }
        }
    }

    private static double leerDecimal(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            try {
                return Double.parseDouble(sc.nextLine().trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                System.out.println("Escribe un numero (por ejemplo 19.99)");
            }
        }
    }

    private static void rollback(Connection con) {
        try {
            con.rollback();
        } catch (SQLException ignorada) {
        }
    }

    private static void activarAutoCommit(Connection con) {
        try {
            con.setAutoCommit(true);
        } catch (SQLException ignorada) {
        }
    }
}