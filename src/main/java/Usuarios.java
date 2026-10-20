import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Usuarios {

    // Usuario que ha iniciado sesion
    public static int idUsuarioActual;
    public static String rolActual;

    // ---------- PERMISOS ----------
    public static boolean esAdmin() {
        return "administrador".equals(rolActual);
    }

    public static boolean puedeCrearOportunidad() {
        return esAdmin() || "comercial".equals(rolActual);
    }

    public static boolean exigirAdmin() {
        if (!esAdmin()) {
            System.out.println("No tienes permiso para hacer esto. Solo el administrador puede.");
            return false;
        }
        return true;
    }

    // ---------- ADMIN POR DEFECTO ----------
    public static void crearAdminPorDefecto(Connection con) throws SQLException {
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
        System.out.println("Rol: 1. trabajador   2. comercial   3. administrador");
        int op = Utilidades.leerEntero("Opcion: ");
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