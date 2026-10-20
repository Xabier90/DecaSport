import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Main {

    // Datos de conexion: cambia la contrasena por la tuya
    private static final String URL = "jdbc:postgresql://192.168.28.159:5432/decasport";
    private static final String USUARIO = "postgres";
    private static final String CONTRASENA = "decasport123";

    public static void main(String[] args) {
        try (Connection con = DriverManager.getConnection(URL, USUARIO, CONTRASENA)) {
            System.out.println("Conectado a la base de datos decasport");
            crearTablas(con);
            Usuarios.crearAdminPorDefecto(con);
            menuInicial(con);
        } catch (SQLException e) {
            System.out.println("Error con la base de datos: " + e.getMessage());
        }
    }

    // Crea las tablas nuevas solo si todavia no existen
    private static void crearTablas(Connection con) throws SQLException {
        String[] sentencias = {
            "INSERT INTO roles (nombre_rol) SELECT 'administrador' "
                + "WHERE NOT EXISTS (SELECT 1 FROM roles WHERE nombre_rol = 'administrador')",
            "INSERT INTO roles (nombre_rol) SELECT 'trabajador' "
                + "WHERE NOT EXISTS (SELECT 1 FROM roles WHERE nombre_rol = 'trabajador')",
            "INSERT INTO roles (nombre_rol) SELECT 'comercial' "
                + "WHERE NOT EXISTS (SELECT 1 FROM roles WHERE nombre_rol = 'comercial')",
            "INSERT INTO roles (nombre_rol) SELECT 'cliente' "
                + "WHERE NOT EXISTS (SELECT 1 FROM roles WHERE nombre_rol = 'cliente')",

            "CREATE TABLE IF NOT EXISTS componentes_fabricacion ("
                + "id_componente SERIAL PRIMARY KEY, "
                + "id_producto INTEGER NOT NULL REFERENCES productos(id_producto) ON DELETE CASCADE, "
                + "nombre VARCHAR(100) NOT NULL, "
                + "variante VARCHAR(50), "
                + "cantidad_necesaria INTEGER NOT NULL DEFAULT 1 CHECK (cantidad_necesaria > 0), "
                + "stock_actual INTEGER NOT NULL DEFAULT 0 CHECK (stock_actual >= 0), "
                + "stock_minimo INTEGER NOT NULL DEFAULT 0 CHECK (stock_minimo >= 0), "
                + "id_proveedor INTEGER REFERENCES proveedores(id_proveedor))",

            "ALTER TABLE compras ADD COLUMN IF NOT EXISTS id_componente INTEGER "
                + "REFERENCES componentes_fabricacion(id_componente)",
            "ALTER TABLE compras ADD COLUMN IF NOT EXISTS estado VARCHAR(20) DEFAULT 'Pendiente'",

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

    // ---------- MENU INICIAL ----------
    private static void menuInicial(Connection con) {
        while (true) {
            System.out.println("\n=== DECASPORT ===");
            System.out.println("1. Iniciar sesion");
            System.out.println("2. Registrarse como cliente");
            System.out.println("0. Salir");
            int op = Utilidades.leerEntero("Opcion: ");
            try {
                switch (op) {
                    case 1 -> {
                        if (Usuarios.iniciarSesion(con)) {
                            if (Usuarios.rolActual.equals("cliente")) menuCliente(con);
                            else menuPersonal(con);
                        }
                    }
                    case 2 -> Usuarios.registrarCliente(con);
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

    // ---------- MENU DEL PERSONAL ----------
    private static void menuPersonal(Connection con) {
        boolean admin = Usuarios.esAdmin();
        boolean comercial = Usuarios.puedeCrearOportunidad();
        while (true) {
            System.out.println("\n=== MENU " + Usuarios.rolActual.toUpperCase() + " ===");
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
                System.out.println("21. Borrar cliente");
            }
            System.out.println("0. Cerrar sesion");
            int op = Utilidades.leerEntero("Opcion: ");
            try {
                switch (op) {
                    case 1 -> Inventario.verProductos(con);
                    case 2 -> Ventas.verClientes(con);
                    case 3 -> Ventas.verPedidos(con);
                    case 4 -> Ventas.verFacturas(con);
                    case 5 -> Inventario.verComponentes(con);
                    case 6 -> Inventario.verCompras(con);
                    case 7 -> Inventario.verFabricacion(con);
                    case 8 -> Inventario.verProveedores(con);
                    case 9 -> Ventas.verOportunidades(con);
                    case 10 -> Ventas.crearOportunidad(con);
                    case 11 -> Inventario.anadirProducto(con);
                    case 12 -> Inventario.editarProducto(con);
                    case 13 -> Inventario.borrarProducto(con);
                    case 14 -> Inventario.anadirComponente(con);
                    case 15 -> Inventario.editarComponente(con);
                    case 16 -> Inventario.borrarComponente(con);
                    case 17 -> Inventario.crearCompraManual(con);
                    case 18 -> Inventario.recibirCompraMenu(con);
                    case 19 -> Inventario.terminarOrdenMenu(con);
                    case 20 -> Usuarios.crearUsuarioPersonal(con);
                    case 21 -> Ventas.borrarCliente(con);
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

    // ---------- MENU DEL CLIENTE ----------
    private static void menuCliente(Connection con) {
        while (true) {
            System.out.println("\n=== MENU CLIENTE ===");
            System.out.println("1. Ver productos");
            System.out.println("2. Hacer un pedido");
            System.out.println("0. Cerrar sesion");
            int op = Utilidades.leerEntero("Opcion: ");
            try {
                switch (op) {
                    case 1 -> Inventario.verProductos(con);
                    case 2 -> Ventas.hacerPedido(con);
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
}