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
    private static final String URL = "jdbc:postgresql://192.168.28.159:5432/decasport";
    private static final String USUARIO = "postgres";
    private static final String CONTRASENA = "decasport123";

    private static final Scanner sc = new Scanner(System.in);

    // Usuario que ha iniciado sesion (el rol se lee de la base de datos al entrar)
    private static int idUsuarioActual;
    private static String rolActual;

    // Un producto dentro de un pedido
    private record Linea(int idProducto, String nombre, double precio, int cantidad) {}

    // Un componente de fabricacion (fila de la tabla componentes_fabricacion)
    private record Comp(int id, String nombre, int necesaria, int stock, Integer idProveedor) {}

    // ------------------------------------------------------------
    // PERMISOS SEGUN EL ROL (el rol viene de la base de datos)
    //   administrador : puede hacerlo todo
    //   comercial     : puede ver todo y crear oportunidades de venta
    //   trabajador    : solo puede ver datos
    //   cliente       : ve productos y hace pedidos (menu aparte)
    // Si quieres cambiar los permisos, solo tienes que tocar estas funciones.
    // ------------------------------------------------------------
    private static boolean esAdmin() {
        return "administrador".equals(rolActual);
    }

    private static boolean puedeCrearOportunidad() {
        return esAdmin() || "comercial".equals(rolActual);
    }

    // Se usa al principio de cada accion que modifica datos (aunque el menu no la muestre)
    private static boolean exigirAdmin() {
        if (!esAdmin()) {
            System.out.println("No tienes permiso para hacer esto. Solo el administrador puede.");
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------
    // PROGRAMA PRINCIPAL
    // ------------------------------------------------------------
    public static void main(String[] args) {
        try (Connection con = DriverManager.getConnection(URL, USUARIO, CONTRASENA)) {
            System.out.println("Conectado a la base de datos decasport");
            crearTablas(con);
            crearAdminPorDefecto(con);
            menuInicial(con);
        } catch (SQLException e) {
            System.out.println("Error con la base de datos: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------
    // CREAR TABLAS NUEVAS (solo se crean si todavia no existen)
    // ------------------------------------------------------------
    private static void crearTablas(Connection con) throws SQLException {
        String[] sentencias = {
            // Roles que usa el programa (si ya existen no se duplican)
            "INSERT INTO roles (nombre_rol) SELECT 'administrador' "
                + "WHERE NOT EXISTS (SELECT 1 FROM roles WHERE nombre_rol = 'administrador')",
            "INSERT INTO roles (nombre_rol) SELECT 'trabajador' "
                + "WHERE NOT EXISTS (SELECT 1 FROM roles WHERE nombre_rol = 'trabajador')",
            "INSERT INTO roles (nombre_rol) SELECT 'comercial' "
                + "WHERE NOT EXISTS (SELECT 1 FROM roles WHERE nombre_rol = 'comercial')",
            "INSERT INTO roles (nombre_rol) SELECT 'cliente' "
                + "WHERE NOT EXISTS (SELECT 1 FROM roles WHERE nombre_rol = 'cliente')",

            // NUEVA TABLA: componentes de fabricacion.
            // Cada fila es una pieza que necesita un producto (bici o tabla de snow).
            //  - cantidad_necesaria: piezas que se gastan para montar 1 unidad
            //  - stock_actual: piezas que hay ahora mismo en el almacen
            //  - stock_minimo: por debajo de esto se avisa de que hay que comprar
            "CREATE TABLE IF NOT EXISTS componentes_fabricacion ("
                + "id_componente SERIAL PRIMARY KEY, "
                + "id_producto INTEGER NOT NULL REFERENCES productos(id_producto) ON DELETE CASCADE, "
                + "nombre VARCHAR(100) NOT NULL, "
                + "variante VARCHAR(50), "
                + "cantidad_necesaria INTEGER NOT NULL DEFAULT 1 CHECK (cantidad_necesaria > 0), "
                + "stock_actual INTEGER NOT NULL DEFAULT 0 CHECK (stock_actual >= 0), "
                + "stock_minimo INTEGER NOT NULL DEFAULT 0 CHECK (stock_minimo >= 0), "
                + "id_proveedor INTEGER REFERENCES proveedores(id_proveedor))",

            // La tabla compras ahora puede indicar QUE componente se compra y en que estado esta
            "ALTER TABLE compras ADD COLUMN IF NOT EXISTS id_componente INTEGER "
                + "REFERENCES componentes_fabricacion(id_componente)",
            "ALTER TABLE compras ADD COLUMN IF NOT EXISTS estado VARCHAR(20) DEFAULT 'Pendiente'",

            // NUEVA TABLA: oportunidades de venta (CRM)
            "CREATE TABLE IF NOT EXISTS oportunidades ("
                + "id_oportunidad SERIAL PRIMARY KEY, "
                + "id_cliente INTEGER NOT NULL REFERENCES clientes(id_cliente), "
                + "id_producto INTEGER NOT NULL REFERENCES productos(id_producto), "
                + "cantidad INTEGER NOT NULL CHECK (cantidad > 0), "
                + "id_usuario INTEGER REFERENCES usuarios(id_usuario), "
                + "fecha DATE NOT NULL DEFAULT CURRENT_DATE, "
                + "estado VARCHAR(20) NOT NULL DEFAULT 'Abierta')"
        };
        try (Statement st = con.createStatement()) {
            for (String s : sentencias) st.execute(s);
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
    // MENU DEL PERSONAL (administrador, comercial y trabajador)
    // Cada uno ve solo las opciones que su rol le permite.
    // ------------------------------------------------------------
    private static void menuPersonal(Connection con) {
        boolean admin = esAdmin();
        boolean comercial = puedeCrearOportunidad();
        while (true) {
            System.out.println("\n=== MENU " + rolActual.toUpperCase() + " ===");
            System.out.println("1. Ver productos");
            System.out.println("2. Ver clientes");
            System.out.println("3. Ver pedidos de venta");
            System.out.println("4. Ver facturas");
            System.out.println("5. Ver componentes de fabricacion");
            System.out.println("6. Ver pedidos de compra");
            System.out.println("7. Ver ordenes de fabricacion");
            System.out.println("8. Ver proveedores");
            System.out.println("9. Ver oportunidades de venta");
            if (comercial) System.out.println("10. Crear oportunidad de venta (simula toda la cadena)");
            if (admin) {
                System.out.println("11. Anadir producto");
                System.out.println("12. Editar producto");
                System.out.println("13. Borrar producto");
                System.out.println("14. Anadir componente de fabricacion");
                System.out.println("15. Editar componente de fabricacion");
                System.out.println("16. Borrar componente de fabricacion");
                System.out.println("17. Crear pedido de compra");
                System.out.println("18. Recibir pedido de compra (suma al stock del componente)");
                System.out.println("19. Terminar orden de fabricacion");
                System.out.println("20. Crear usuario (trabajador, comercial o administrador)");
            }
            System.out.println("0. Cerrar sesion");
            int op = leerEntero("Opcion: ");
            try {
                switch (op) {
                    case 1 -> verProductos(con);
                    case 2 -> verClientes(con);
                    case 3 -> verPedidos(con);
                    case 4 -> verFacturas(con);
                    case 5 -> verComponentes(con);
                    case 6 -> verCompras(con);
                    case 7 -> verFabricacion(con);
                    case 8 -> verProveedores(con);
                    case 9 -> verOportunidades(con);
                    case 10 -> crearOportunidad(con);
                    case 11 -> anadirProducto(con);
                    case 12 -> editarProducto(con);
                    case 13 -> borrarProducto(con);
                    case 14 -> anadirComponente(con);
                    case 15 -> editarComponente(con);
                    case 16 -> borrarComponente(con);
                    case 17 -> crearCompraManual(con);
                    case 18 -> recibirCompraMenu(con);
                    case 19 -> terminarOrdenMenu(con);
                    case 20 -> crearUsuarioPersonal(con);
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
        String usuario = leerLinea().trim();
        System.out.print("Contrasena: ");
        String pass = leerLinea();
        System.out.print("Nombre completo: ");
        String nombre = leerLinea().trim();
        System.out.print("Email: ");
        String email = leerLinea().trim();

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

    // Comprueba usuario y contrasena en la base de datos y guarda su ROL
    private static boolean iniciarSesion(Connection con) throws SQLException {
        System.out.print("Usuario: ");
        String usuario = leerLinea().trim();
        System.out.print("Contrasena: ");
        String pass = leerLinea();

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

    // Solo el administrador puede crear trabajadores, comerciales y otros administradores
    private static void crearUsuarioPersonal(Connection con) throws SQLException {
        if (!exigirAdmin()) return;
        System.out.print("Nombre de usuario: ");
        String usuario = leerLinea().trim();
        System.out.print("Contrasena: ");
        String pass = leerLinea();
        System.out.println("Rol: 1. trabajador   2. comercial   3. administrador");
        int op = leerEntero("Opcion: ");
        String rol = (op == 3) ? "administrador" : (op == 2) ? "comercial" : "trabajador";

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
    // VER DATOS (lo pueden usar todos los empleados)
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

    private static void verClientes(Connection con) throws SQLException {
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

    private static void verPedidos(Connection con) throws SQLException {
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

    private static void verFacturas(Connection con) throws SQLException {
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

    // Muestra los componentes y AVISA de los que estan por debajo del minimo
    private static void verComponentes(Connection con) throws SQLException {
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

    private static void verCompras(Connection con) throws SQLException {
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

    private static void verFabricacion(Connection con) throws SQLException {
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

    private static void verProveedores(Connection con) throws SQLException {
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

    private static void verOportunidades(Connection con) throws SQLException {
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

    // ------------------------------------------------------------
    // PRODUCTOS (solo administrador)
    // ------------------------------------------------------------
    private static void anadirProducto(Connection con) throws SQLException {
        if (!exigirAdmin()) return;
        System.out.print("Nombre: ");
        String nombre = leerLinea().trim();
        System.out.print("Categoria: ");
        String categoria = leerLinea().trim();
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
            System.out.println("Si se fabrica en la empresa, define sus piezas en 'Anadir componente'");
        } catch (SQLException e) {
            rollback(con);
            System.out.println("No se ha podido anadir el producto: " + e.getMessage());
        } finally {
            activarAutoCommit(con);
        }
    }

    private static void editarProducto(Connection con) throws SQLException {
        if (!exigirAdmin()) return;
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
        String nombre = leerLinea().trim();
        if (nombre.isEmpty()) nombre = nombreActual;

        System.out.print("Categoria [" + (categoriaActual == null ? "" : categoriaActual) + "]: ");
        String categoria = leerLinea().trim();
        if (categoria.isEmpty()) categoria = categoriaActual;

        System.out.print("Precio [" + precioActual + "]: ");
        String textoPrecio = leerLinea().trim().replace(',', '.');
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
        if (!exigirAdmin()) return;
        int id = leerEntero("ID del producto a borrar: ");
        System.out.print("Seguro que quieres borrarlo? (s/n): ");
        if (!leerLinea().trim().equalsIgnoreCase("s")) {
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
            rollback(con);
            if ("23503".equals(e.getSQLState())) {
                System.out.println("No se puede borrar: el producto ya aparece en pedidos, compras, montajes u oportunidades");
            } else {
                System.out.println("No se ha podido borrar: " + e.getMessage());
            }
        } finally {
            activarAutoCommit(con);
        }
    }

    // ------------------------------------------------------------
    // COMPONENTES DE FABRICACION (solo administrador)
    // ------------------------------------------------------------
    private static void anadirComponente(Connection con) throws SQLException {
        if (!exigirAdmin()) return;
        verProductos(con);
        int idProducto = leerEntero("ID del producto que usa este componente (0 = cancelar): ");
        if (idProducto == 0) return;
        if (!existe(con, "SELECT 1 FROM productos WHERE id_producto = ?", idProducto)) {
            System.out.println("No existe ese producto");
            return;
        }
        System.out.print("Nombre del componente (ej. Manillar, Tabla, Anclaje): ");
        String nombre = leerLinea().trim();
        System.out.print("Variante, opcional (ej. 156 cm, talla 42, 29 pulgadas): ");
        String variante = leerLinea().trim();
        int necesaria = leerEntero("Unidades necesarias para montar 1 producto: ");
        int stock = leerEntero("Stock actual: ");
        int minimo = leerEntero("Stock minimo (avisa de comprar por debajo): ");
        if (nombre.isEmpty() || necesaria <= 0 || stock < 0 || minimo < 0) {
            System.out.println("Datos no validos: nombre obligatorio, necesarias mayor que 0, stock y minimo no negativos");
            return;
        }
        verProveedores(con);
        int idProveedor = leerEntero("ID del proveedor (0 = ninguno): ");
        if (idProveedor != 0 && !existe(con, "SELECT 1 FROM proveedores WHERE id_proveedor = ?", idProveedor)) {
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
            if (idProveedor == 0) ps.setNull(7, java.sql.Types.INTEGER);
            else ps.setInt(7, idProveedor);
            ps.executeUpdate();
            System.out.println("Componente anadido");
        }
    }

    private static void editarComponente(Connection con) throws SQLException {
        if (!exigirAdmin()) return;
        verComponentes(con);
        int id = leerEntero("ID del componente a editar (0 = cancelar): ");
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
                idProveedor = rs.getInt(6);   // 0 si no tiene
            }
        }

        System.out.println("(Pulsa Enter para dejar el valor actual)");
        System.out.print("Nombre [" + nombre + "]: ");
        String t = leerLinea().trim();
        if (!t.isEmpty()) nombre = t;
        System.out.print("Variante [" + variante + "]: ");
        t = leerLinea().trim();
        if (!t.isEmpty()) variante = t;
        necesaria = leerEnteroOpcional("Unidades necesarias por producto", necesaria, 1);
        stock = leerEnteroOpcional("Stock actual", stock, 0);
        minimo = leerEnteroOpcional("Stock minimo", minimo, 0);
        idProveedor = leerEnteroOpcional("ID proveedor (0 = ninguno)", idProveedor, 0);
        if (idProveedor != 0 && !existe(con, "SELECT 1 FROM proveedores WHERE id_proveedor = ?", idProveedor)) {
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
            if (idProveedor == 0) ps.setNull(6, java.sql.Types.INTEGER);
            else ps.setInt(6, idProveedor);
            ps.setInt(7, id);
            ps.executeUpdate();
            System.out.println("Componente actualizado");
        }
    }

    private static void borrarComponente(Connection con) throws SQLException {
        if (!exigirAdmin()) return;
        verComponentes(con);
        int id = leerEntero("ID del componente a borrar (0 = cancelar): ");
        if (id == 0) return;
        if (!preguntarSiNo("Seguro que quieres borrarlo?")) {
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

    // ------------------------------------------------------------
    // COMPRAS Y FABRICACION A MANO (solo administrador)
    // ------------------------------------------------------------
    private static void crearCompraManual(Connection con) throws SQLException {
        if (!exigirAdmin()) return;
        verComponentes(con);
        int id = leerEntero("ID del componente a comprar (0 = cancelar): ");
        if (id == 0) return;
        int cantidad = leerEntero("Cantidad a comprar: ");
        if (cantidad <= 0) {
            System.out.println("La cantidad debe ser mayor que 0");
            return;
        }
        int idCompra = crearCompra(con, id, cantidad);
        if (idCompra > 0) System.out.println("Pedido de compra " + idCompra + " creado (estado: Pendiente)");
    }

    // Crea el pedido de compra de un componente. Devuelve su ID o -1 si no se pudo.
    private static int crearCompra(Connection con, int idComponente, int cantidad) throws SQLException {
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
                boolean sinProveedor = rs.wasNull();
                if (sinProveedor) {
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

    private static void recibirCompraMenu(Connection con) throws SQLException {
        if (!exigirAdmin()) return;
        verCompras(con);
        int id = leerEntero("ID del pedido de compra recibido (0 = cancelar): ");
        if (id == 0) return;
        if (recibirCompra(con, id)) System.out.println("Material recibido: stock del componente actualizado");
    }

    // Marca la compra como recibida y suma las unidades al stock del componente
    private static boolean recibirCompra(Connection con, int idCompra) {
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
            rollback(con);
            System.out.println("No se ha podido registrar la recepcion: " + e.getMessage());
            return false;
        } finally {
            activarAutoCommit(con);
        }
    }

    private static void terminarOrdenMenu(Connection con) throws SQLException {
        if (!exigirAdmin()) return;
        verFabricacion(con);
        int id = leerEntero("ID de la orden a terminar (0 = cancelar): ");
        if (id == 0) return;
        if (terminarMontaje(con, id)) System.out.println("Orden terminada: componentes gastados y producto anadido al stock");
    }

    // Termina una orden: gasta los componentes y suma 1 unidad del producto al stock.
    // Si falta algun componente, no hace nada y avisa de cual falta.
    private static boolean terminarMontaje(Connection con, int idMontaje) {
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
            rollback(con);
            System.out.println("No se ha podido terminar la orden: " + e.getMessage());
            return false;
        } finally {
            activarAutoCommit(con);
        }
    }

    // ------------------------------------------------------------
    // CRM: OPORTUNIDAD DE VENTA + CADENA AUTOMATICA
    // ------------------------------------------------------------
    private static void crearOportunidad(Connection con) throws SQLException {
        if (!puedeCrearOportunidad()) {
            System.out.println("No tienes permiso para crear oportunidades de venta");
            return;
        }
        // 1. Cliente
        verClientes(con);
        int idCliente = leerEntero("ID del cliente (0 = cancelar): ");
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

        // 2. Producto (solo los que tienen componentes definidos, es decir, los que fabricamos)
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
            System.out.println("No hay productos con componentes definidos. El administrador debe crearlos primero");
            return;
        }
        int idProducto = leerEntero("ID del producto a ofrecer (0 = cancelar): ");
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
        int cantidad = leerEntero("Cantidad (1-1000): ");
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
            ps.setInt(4, idUsuarioActual);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                idOportunidad = rs.getInt(1);
            }
        }

        // 4. El cliente decide (lo respondes tu en pantalla)
        System.out.printf("%n>> Propuesta para %s: %d x %s = %.2f EUR%n", nombreCliente, cantidad, nombreProducto,
                precio * cantidad);
        boolean acepta = preguntarSiNo("El cliente va a aprobar tu propuesta?");
        if (!acepta) {
            cambiarEstadoOportunidad(con, idOportunidad, "Rechazada");
            System.out.println(">> El cliente RECHAZA la propuesta. Oportunidad cerrada");
            return;
        }
        cambiarEstadoOportunidad(con, idOportunidad, "Aceptada");
        System.out.println(">> El cliente ACEPTA la propuesta");
        ejecutarCadena(con, idCliente, idProducto, nombreProducto, categoria, precio, cantidad);
    }

    // Pedido -> stock -> (compras) -> fabricacion -> envio -> factura
    private static void ejecutarCadena(Connection con, int idCliente, int idProducto, String nombreProducto,
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
            rollback(con);
            System.out.println("No se ha podido crear el pedido: " + e.getMessage());
            return;
        } finally {
            activarAutoCommit(con);
        }
        System.out.println("[VENTAS] Pedido de venta " + idPedido + " creado");

        // PASO 2: stock del producto terminado
        int faltan = Math.max(0, cantidad - stockTotal(con, idProducto));
        if (faltan == 0) {
            System.out.println("[INVENTARIO] Hay stock suficiente. No hace falta fabricar");
        } else {
            System.out.println("[INVENTARIO] Stock insuficiente: hay que fabricar " + faltan + " unidad(es)");
            cambiarEstadoPedido(con, idPedido, "En fabricacion");

            // PASO 3: componentes. Si faltan, se crea el pedido de compra
            List<Comp> comps = cargarComponentes(con, idProducto, false);
            if (comps.isEmpty()) {
                detenerCadena(con, idPedido, "Falta material", "El producto no tiene componentes definidos");
                return;
            }
            for (Comp c : comps) {
                int necesario = c.necesaria() * faltan;
                int deficit = necesario - c.stock();
                if (deficit <= 0) {
                    System.out.println("[INVENTARIO] Componente suficiente: " + c.nombre()
                            + " (hay " + c.stock() + ", hacen falta " + necesario + ")");
                    continue;
                }
                System.out.println("[COMPRAS] Falta componente: " + c.nombre() + " (hay " + c.stock()
                        + ", hacen falta " + necesario + ")");
                int idCompra = crearCompra(con, c.id(), deficit);
                if (idCompra < 0) {
                    detenerCadena(con, idPedido, "Falta material", "No se pudo crear el pedido de compra");
                    return;
                }
                System.out.println("[COMPRAS] Pedido de compra " + idCompra + " creado: " + deficit + " x " + c.nombre());
                if (!preguntarSiNo("   El proveedor ACEPTA el pedido de compra?")) {
                    actualizarEstadoCompra(con, idCompra, "Rechazada");
                    detenerCadena(con, idPedido, "Falta material", "El proveedor rechazo la compra");
                    return;
                }
                actualizarEstadoCompra(con, idCompra, "Aceptada");
                if (!preguntarSiNo("   Ha LLEGADO ya el material al almacen?")) {
                    detenerCadena(con, idPedido, "Falta material",
                            "Material pendiente de recibir (usa la opcion 'Recibir pedido de compra')");
                    return;
                }
                if (!recibirCompra(con, idCompra)) {
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
                    ps.setInt(4, idUsuarioActual);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        ordenes.add(rs.getInt(1));
                    }
                }
            }
            if (!preguntarSiNo("   Se han TERMINADO de montar las " + faltan + " unidad(es)?")) {
                detenerCadena(con, idPedido, "En fabricacion",
                        "Fabricacion en proceso (termina las ordenes con la opcion 'Terminar orden de fabricacion')");
                return;
            }
            for (int idMontaje : ordenes) {
                if (!terminarMontaje(con, idMontaje)) {
                    detenerCadena(con, idPedido, "En fabricacion", "No se pudo terminar una orden de fabricacion");
                    return;
                }
            }
            System.out.println("[FABRICACION] Ordenes terminadas. Stock actualizado");
        }

        // PASO 5: envio y factura
        cambiarEstadoPedido(con, idPedido, "Listo envio");
        if (!preguntarSiNo("[ENVIO] Confirmas el envio del pedido " + idPedido + " al cliente?")) {
            System.out.println(">> Pedido listo para envio. Queda pendiente de enviar");
            return;
        }
        try {
            con.setAutoCommit(false);
            if (!descontarStockTotal(con, idProducto, cantidad)) {
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
            rollback(con);
            System.out.println("No se ha podido completar el envio: " + e.getMessage());
        } finally {
            activarAutoCommit(con);
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

    private static void actualizarEstadoCompra(Connection con, int id, String estado) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE compras SET estado = ? WHERE id_compra = ?")) {
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
        if (!leerLinea().trim().equalsIgnoreCase("s")) {
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
    // STOCK Y COMPONENTES: funciones de apoyo
    // ------------------------------------------------------------
    // Stock total de un producto sumando todos los almacenes
    private static int stockTotal(Connection con, int idProducto) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COALESCE(SUM(cantidad), 0) FROM stock WHERE id_producto = ?")) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // Resta unidades repartiendolas entre los almacenes si hace falta (debe usarse dentro de una transaccion)
    private static boolean descontarStockTotal(Connection con, int idProducto, int cantidad) throws SQLException {
        List<int[]> filas = new ArrayList<>();   // {id_stock, cantidad}
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

    // Suma unidades al stock de un producto (en su primer almacen, o crea uno en Espana)
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

    // Lee los componentes de un producto. Con bloquear=true se bloquean las filas (para gastarlas)
    private static List<Comp> cargarComponentes(Connection con, int idProducto, boolean bloquear) throws SQLException {
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

    private static boolean existe(Connection con, String sql, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ------------------------------------------------------------
    // AYUDAS
    // ------------------------------------------------------------
    // Lee una linea. Si se cierra el teclado, el programa termina sin errores feos.
    private static String leerLinea() {
        if (!sc.hasNextLine()) {
            System.out.println("\nEntrada cerrada. Saliendo...");
            System.exit(0);
        }
        return sc.nextLine();
    }

    private static int leerEntero(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            try {
                return Integer.parseInt(leerLinea().trim());
            } catch (NumberFormatException e) {
                System.out.println("Escribe un numero entero");
            }
        }
    }

    // Igual que leerEntero, pero Enter deja el valor actual. Repite si es menor que el minimo.
    private static int leerEnteroOpcional(String mensaje, int actual, int minimo) {
        while (true) {
            System.out.print(mensaje + " [" + actual + "]: ");
            String t = leerLinea().trim();
            if (t.isEmpty()) return actual;
            try {
                int n = Integer.parseInt(t);
                if (n >= minimo) return n;
                System.out.println("Debe ser " + minimo + " o mas");
            } catch (NumberFormatException e) {
                System.out.println("Escribe un numero entero");
            }
        }
    }

    private static double leerDecimal(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            try {
                return Double.parseDouble(leerLinea().trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                System.out.println("Escribe un numero (por ejemplo 19.99)");
            }
        }
    }

    // Pregunta si/no y repite hasta recibir una respuesta valida
    private static boolean preguntarSiNo(String mensaje) {
        while (true) {
            System.out.print(mensaje + " (s/n): ");
            String r = leerLinea().trim().toLowerCase();
            if (r.equals("s") || r.equals("si")) return true;
            if (r.equals("n") || r.equals("no")) return false;
            System.out.println("Responde s o n");
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
