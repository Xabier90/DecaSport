import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Usuarios {

    // Usuario que ha iniciado sesion
    public static int idUsuarioActual;
    public static String nombreActual;
    public static String rolActual;

    // ---------- PERMISOS ----------
    // Departamentos del ERP: cada uno tiene su propio usuario y su propio menu
    public static final String[] DEPARTAMENTOS = {"crm", "ventas", "compras", "inventario", "fabricacion"};

    public static boolean esAdmin() {
        return "administrador".equals(rolActual);
    }

    // El administrador puede entrar en todo; el resto solo en su departamento
    public static boolean tieneAcceso(String departamento) {
        return esAdmin() || departamento.equals(rolActual);
    }

    // Comprueba el permiso y avisa si no lo tiene
    public static boolean exigir(String departamento) {
        if (!tieneAcceso(departamento)) {
            System.out.println("No tienes permiso para hacer esto. Solo el departamento de "
                    + departamento.toUpperCase() + " o el administrador pueden.");
            return false;
        }
        return true;
    }

    public static boolean puedeCrearOportunidad() {
        return tieneAcceso("crm");
    }

    public static boolean exigirAdmin() {
        if (!esAdmin()) {
            System.out.println("No tienes permiso para hacer esto. Solo el administrador puede.");
            return false;
        }
        return true;
    }

    // ---------- USUARIOS POR DEFECTO ----------
    // Crea el administrador y un usuario por departamento si todavia no existen
    public static void crearUsuariosPorDefecto(Connection con) throws SQLException {
        String sql = "SELECT COUNT(*) FROM usuarios u JOIN roles r ON r.id_rol = u.id_rol "
                   + "WHERE r.nombre_rol = 'administrador'";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            if (rs.getInt(1) == 0) {
                crearUsuario(con, "admin", "admin123", "administrador");
                System.out.println("Se ha creado el administrador por defecto: admin / admin123");
            }
        }
        // Usuario de cada departamento: nombre = departamento, contrasena = departamento + "123"
        for (String dep : DEPARTAMENTOS) {
            if (!existeUsuario(con, dep)) {
                crearUsuario(con, dep, dep + "123", dep);
                System.out.println("Se ha creado el usuario del departamento: " + dep + " / " + dep + "123");
            }
        }
    }

    // Usuarios creados antes de que existieran los departamentos (rol 'trabajador' o 'comercial'):
    // se pasan a su departamento para que cada uno vea solo su menu
    public static void corregirRolesAntiguos(Connection con) throws SQLException {
        String[][] cambios = {
            {"comercial", "crm"},
            {"ventas", "ventas"},
            {"compras", "compras"},
            {"almacenero", "inventario"},
            {"operario", "fabricacion"}
        };
        String sql = "UPDATE usuarios SET id_rol = (SELECT id_rol FROM roles WHERE nombre_rol = ?) "
                   + "WHERE nombre_usuario = ? AND id_rol IN "
                   + "(SELECT id_rol FROM roles WHERE nombre_rol IN ('trabajador', 'comercial'))";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (String[] c : cambios) {
                ps.setString(1, c[1]);
                ps.setString(2, c[0]);
                if (ps.executeUpdate() > 0) {
                    System.out.println("El usuario '" + c[0] + "' pasa al departamento " + c[1].toUpperCase());
                }
            }
        }
    }

    private static boolean existeUsuario(Connection con, String nombre) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM usuarios WHERE nombre_usuario = ?")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ---------- REGISTRO E INICIO DE SESION ----------
    public static void registrarCliente(Connection con) {
        System.out.print("Nombre de usuario: ");
        String usuario = Utilidades.leerLinea().trim();
        System.out.print("Contrasena: ");
        String pass = Utilidades.leerLinea();
        System.out.print("Nombre completo: ");
        String nombre = Utilidades.leerLinea().trim();
        System.out.print("Email: ");
        String email = Utilidades.leerLinea().trim();

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
            Utilidades.rollback(con);
            if ("23505".equals(e.getSQLState())) {
                System.out.println("Ese nombre de usuario ya existe");
            } else {
                System.out.println("Error al registrar: " + e.getMessage());
            }
        } finally {
            Utilidades.activarAutoCommit(con);
        }
    }

    public static boolean iniciarSesion(Connection con) throws SQLException {
        System.out.print("Usuario: ");
        String usuario = Utilidades.leerLinea().trim();
        System.out.print("Contrasena: ");
        String pass = Utilidades.leerLinea();

        String sql = "SELECT u.id_usuario, r.nombre_rol FROM usuarios u "
                   + "JOIN roles r ON r.id_rol = u.id_rol "
                   + "WHERE u.nombre_usuario = ? AND u.contrasena = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ps.setString(2, cifrar(pass));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    idUsuarioActual = rs.getInt("id_usuario");
                    nombreActual = usuario;
                    rolActual = rs.getString("nombre_rol");
                    System.out.println("Bienvenido, " + usuario + " (" + rolActual + ")");
                    return true;
                }
            }
        }
        System.out.println("Usuario o contrasena incorrectos");
        return false;
    }

    // ---------- CREAR USUARIOS ----------
    public static void crearUsuarioPersonal(Connection con) throws SQLException {
        if (!exigirAdmin()) return;
        System.out.print("Nombre de usuario: ");
        String usuario = Utilidades.leerLinea().trim();
        System.out.print("Contrasena: ");
        String pass = Utilidades.leerLinea();
        System.out.println("Rol: 1. crm   2. ventas   3. compras   4. inventario   5. fabricacion   6. administrador");
        int op = Utilidades.leerEntero("Opcion: ");
        if (op < 1 || op > 6) {
            System.out.println("Rol no valido");
            return;
        }
        String rol = (op == 6) ? "administrador" : DEPARTAMENTOS[op - 1];

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

    // El administrador cambia el departamento (rol) de un trabajador
    public static void cambiarDepartamento(Connection con) throws SQLException {
        if (!exigirAdmin()) return;
        String sql = "SELECT u.id_usuario, u.nombre_usuario, r.nombre_rol FROM usuarios u "
                   + "JOIN roles r ON r.id_rol = u.id_rol WHERE r.nombre_rol <> 'cliente' ORDER BY u.id_usuario";
        System.out.println("\n--- USUARIOS DE LA EMPRESA ---");
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                System.out.printf("%d. %s (%s)%n", rs.getInt(1), rs.getString(2), rs.getString(3));
            }
        }
        int id = Utilidades.leerEntero("ID del usuario (0 = cancelar): ");
        if (id == 0) return;
        if (id == idUsuarioActual) {
            System.out.println("No puedes cambiar tu propio rol");
            return;
        }
        if (!Utilidades.existe(con, "SELECT 1 FROM usuarios u JOIN roles r ON r.id_rol = u.id_rol "
                + "WHERE u.id_usuario = ? AND r.nombre_rol <> 'cliente'", id)) {
            System.out.println("No existe ese usuario de la empresa");
            return;
        }
        System.out.println("Nuevo rol: 1. crm   2. ventas   3. compras   4. inventario   5. fabricacion   6. administrador");
        int op = Utilidades.leerEntero("Opcion: ");
        if (op < 1 || op > 6) {
            System.out.println("Rol no valido");
            return;
        }
        String rol = (op == 6) ? "administrador" : DEPARTAMENTOS[op - 1];
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE usuarios SET id_rol = (SELECT id_rol FROM roles WHERE nombre_rol = ?) WHERE id_usuario = ?")) {
            ps.setString(1, rol);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
        System.out.println("Rol cambiado a " + rol);
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

    // La contrasena se guarda cifrada (SHA-256)
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
}