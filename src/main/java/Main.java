import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Main {

    // Datos de conexion: cambia la contrasena por la tuya
    private static final String URL = "jdbc:postgresql://localhost:5432/decasport";
    private static final String USUARIO = "postgres";
    private static final String CONTRASENA = "decasport123";

    public static void main(String[] args) {
        try (Connection con = DriverManager.getConnection(URL, USUARIO, CONTRASENA)) {
            System.out.println("Conectado a la base de datos decasport");
            prepararBaseDeDatos(con);
            Usuarios.crearUsuariosPorDefecto(con);
            Usuarios.corregirRolesAntiguos(con);
            // Por si ha entrado stock desde otro programa (por ejemplo el de Python)
            Ventas.procesarPedidosEnEspera(con);
            menuInicial(con);
        } catch (SQLException e) {
            System.out.println("Error con la base de datos: " + e.getMessage());
        }
    }

    // Crea las tablas y roles nuevos solo si todavia no existen
    private static void prepararBaseDeDatos(Connection con) throws SQLException {
        String[] sentencias = {
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
                + "estado VARCHAR(20) NOT NULL DEFAULT 'Abierta')",
            // Pedido de venta que genero la oportunidad al aceptarse (trazabilidad CRM -> Ventas)
            "ALTER TABLE oportunidades ADD COLUMN IF NOT EXISTS id_pedido INTEGER REFERENCES pedidos(id_pedido)",

            // La tabla antigua solo dejaba fabricar 'bici' o 'snow': asi se puede fabricar cualquier producto
            "ALTER TABLE fabricacion DROP CONSTRAINT IF EXISTS montajes_tipo_check",
            // Una orden puede ser para un pedido o para tener stock (sin pedido), y de varias unidades
            "ALTER TABLE fabricacion ALTER COLUMN id_pedido DROP NOT NULL",
            "ALTER TABLE fabricacion ADD COLUMN IF NOT EXISTS cantidad INTEGER NOT NULL DEFAULT 1 CHECK (cantidad > 0)",
            "ALTER TABLE fabricacion ALTER COLUMN estado SET DEFAULT 'Pendiente'"
        };
        try (Statement st = con.createStatement()) {
            for (String s : sentencias) st.execute(s);

            // Roles: administrador, cliente y uno por cada departamento
            String[] roles = {"administrador", "cliente", "crm", "ventas", "compras", "inventario", "fabricacion"};
            for (String rol : roles) {
                st.execute("INSERT INTO roles (nombre_rol) SELECT '" + rol + "' "
                        + "WHERE NOT EXISTS (SELECT 1 FROM roles WHERE nombre_rol = '" + rol + "')");
            }
        }
    }

    // ============================================================
    // AYUDAS PARA DIBUJAR LOS MENUS (asi todos se ven iguales)
    // ============================================================
    private static void titulo(String texto) {
        System.out.println();
        System.out.println("==================================================");
        System.out.println("  " + texto);
        System.out.println("==================================================");
    }

    private static void seccion(String texto) {
        System.out.println(" " + texto);
    }

    private static void opcion(int numero, String texto) {
        System.out.printf("   %d. %s%n", numero, texto);
    }

    private static int pedirOpcion(String textoSalir) {
        System.out.println(" --------------------------------------------------");
        System.out.println("   0. " + textoSalir);
        return Utilidades.leerEntero("Elige una opcion: ");
    }

    private static String tituloDepartamento(String departamento) {
        return "MENU " + departamento + "   (usuario: " + Usuarios.nombreActual + ")";
    }

    // El administrador vuelve a su menu; el trabajador cierra sesion
    private static String textoSalida() {
        return Usuarios.esAdmin() ? "Volver al menu de administrador" : "Cerrar sesion";
    }

    // ============================================================
    // MENU INICIAL
    // ============================================================
    private static void menuInicial(Connection con) {
        while (true) {
            titulo("DECASPORT ERP");
            opcion(1, "Iniciar sesion");
            opcion(2, "Registrarme como cliente");
            int op = pedirOpcion("Salir del programa");
            try {
                switch (op) {
                    case 1 -> {
                        if (Usuarios.iniciarSesion(con)) abrirMenuSegunRol(con);
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

    // Cada usuario entra directamente en el menu de su departamento
    private static void abrirMenuSegunRol(Connection con) {
        switch (Usuarios.rolActual) {
            case "administrador" -> menuAdministrador(con);
            case "crm" -> menuCrm(con);
            case "ventas" -> menuVentas(con);
            case "compras" -> menuCompras(con);
            case "inventario" -> menuInventario(con);
            case "fabricacion" -> menuFabricacion(con);
            case "cliente" -> menuCliente(con);
            default -> System.out.println("Tu usuario no tiene ningun departamento asignado. "
                    + "Pide al administrador que te asigne uno.");
        }
    }

    // ============================================================
    // ADMINISTRADOR: entra en cualquier departamento y gestiona usuarios
    // ============================================================
    private static void menuAdministrador(Connection con) {
        while (true) {
            titulo("MENU ADMINISTRADOR   (usuario: " + Usuarios.nombreActual + ")");
            seccion("DEPARTAMENTOS (entras como si fueras ese trabajador)");
            opcion(1, "CRM");
            opcion(2, "Ventas");
            opcion(3, "Compras");
            opcion(4, "Inventario");
            opcion(5, "Fabricacion");
            seccion("ADMINISTRACION");
            opcion(6, "Informes (general y de cada departamento)");
            opcion(7, "Crear un usuario");
            opcion(8, "Cambiar el departamento de un usuario");
            int op = pedirOpcion("Cerrar sesion");
            try {
                switch (op) {
                    case 1 -> menuCrm(con);
                    case 2 -> menuVentas(con);
                    case 3 -> menuCompras(con);
                    case 4 -> menuInventario(con);
                    case 5 -> menuFabricacion(con);
                    case 6 -> Informes.menuInformes(con);
                    case 7 -> Usuarios.crearUsuarioPersonal(con);
                    case 8 -> Usuarios.cambiarDepartamento(con);
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

    // ============================================================
    // MENUS DE CADA DEPARTAMENTO
    // Cada trabajador solo ve las opciones de su trabajo
    // ============================================================

    // CRM: relacion con los clientes y oportunidades de venta
    private static void menuCrm(Connection con) {
        while (true) {
            titulo(tituloDepartamento("CRM"));
            seccion("CLIENTES");
            opcion(1, "Ver clientes");
            opcion(2, "Ver lo que ha comprado cada cliente");
            opcion(3, "Borrar un cliente");
            seccion("OPORTUNIDADES DE VENTA");
            opcion(4, "Ver oportunidades");
            opcion(5, "Crear una oportunidad (lanza pedido, compra/fabricacion y factura)");
            opcion(6, "Responder a una oportunidad abierta");
            seccion("INFORMES");
            opcion(7, "Informe de CRM");
            int op = pedirOpcion(textoSalida());
            try {
                switch (op) {
                    case 1 -> Ventas.verClientes(con);
                    case 2 -> Ventas.verComprasDeClientes(con);
                    case 3 -> Ventas.borrarCliente(con);
                    case 4 -> Ventas.verOportunidades(con);
                    case 5 -> Ventas.crearOportunidad(con);
                    case 6 -> Ventas.responderOportunidad(con);
                    case 7 -> Informes.informeCrm(con);
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

    // VENTAS: pedidos de los clientes y facturas
    private static void menuVentas(Connection con) {
        while (true) {
            titulo(tituloDepartamento("VENTAS"));
            seccion("PEDIDOS DE VENTA");
            opcion(1, "Ver pedidos de venta");
            opcion(2, "Ver lo que ha comprado cada cliente");
            opcion(3, "Facturar un pedido pendiente");
            opcion(4, "Reanudar un pedido parado (falta material o sin enviar)");
            seccion("FACTURAS");
            opcion(5, "Ver facturas");
            seccion("INFORMES");
            opcion(6, "Informe de Ventas");
            int op = pedirOpcion(textoSalida());
            try {
                switch (op) {
                    case 1 -> Ventas.verPedidos(con);
                    case 2 -> Ventas.verComprasDeClientes(con);
                    case 3 -> Ventas.facturarPedido(con);
                    case 4 -> Ventas.reanudarPedido(con);
                    case 5 -> Ventas.verFacturas(con);
                    case 6 -> Informes.informeVentas(con);
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

    // COMPRAS: proveedores y pedidos de compra de componentes
    private static void menuCompras(Connection con) {
        // Nada mas entrar se avisa de los productos que se han quedado sin stock
        try {
            Inventario.avisarAgotados(con);
        } catch (SQLException e) {
            System.out.println("Error en la base de datos: " + e.getMessage());
        }
        while (true) {
            titulo(tituloDepartamento("COMPRAS"));
            seccion("PROVEEDORES");
            opcion(1, "Ver proveedores");
            seccion("PEDIDOS DE COMPRA");
            opcion(2, "Ver lo que hay que comprar (productos agotados y piezas bajo minimo)");
            opcion(3, "Ver pedidos de compra");
            opcion(4, "Crear un pedido de compra (producto terminado o pieza)");
            opcion(5, "Registrar la llegada de un pedido (suma al stock)");
            seccion("INFORMES");
            opcion(6, "Informe de Compras");
            int op = pedirOpcion(textoSalida());
            try {
                switch (op) {
                    case 1 -> Inventario.verProveedores(con);
                    case 2 -> Inventario.verLoQueHayQueComprar(con);
                    case 3 -> Inventario.verCompras(con);
                    case 4 -> Inventario.crearCompraManual(con);
                    case 5 -> Inventario.recibirCompraMenu(con);
                    case 6 -> Informes.informeCompras(con);
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

    // INVENTARIO: catalogo de productos, stock y componentes
    private static void menuInventario(Connection con) {
        while (true) {
            titulo(tituloDepartamento("INVENTARIO"));
            seccion("PRODUCTOS");
            opcion(1, "Ver productos y su stock");
            opcion(2, "Anadir un producto");
            opcion(3, "Editar un producto");
            opcion(4, "Borrar un producto");
            seccion("COMPONENTES DE FABRICACION");
            opcion(5, "Ver componentes");
            opcion(6, "Anadir un componente");
            opcion(7, "Editar un componente");
            opcion(8, "Borrar un componente");
            seccion("INFORMES");
            opcion(9, "Informe de Inventario");
            int op = pedirOpcion(textoSalida());
            try {
                switch (op) {
                    case 1 -> Inventario.verProductos(con);
                    case 2 -> Inventario.anadirProducto(con);
                    case 3 -> Inventario.editarProducto(con);
                    case 4 -> Inventario.borrarProducto(con);
                    case 5 -> Inventario.verComponentes(con);
                    case 6 -> Inventario.anadirComponente(con);
                    case 7 -> Inventario.editarComponente(con);
                    case 8 -> Inventario.borrarComponente(con);
                    case 9 -> Informes.informeInventario(con);
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

    // FABRICACION: ordenes de montaje en el taller
    private static void menuFabricacion(Connection con) {
        while (true) {
            titulo(tituloDepartamento("FABRICACION"));
            seccion("ORDENES DE FABRICACION");
            opcion(1, "Ver ordenes de fabricacion");
            opcion(2, "Crear una orden de fabricacion (para stock)");
            opcion(3, "Terminar una orden (gasta piezas y suma el producto al stock)");
            opcion(4, "Cancelar una orden");
            seccion("PIEZAS");
            opcion(5, "Ver las piezas de un producto y cuantos se pueden montar");
            seccion("INFORMES");
            opcion(6, "Informe de Fabricacion");
            int op = pedirOpcion(textoSalida());
            try {
                switch (op) {
                    case 1 -> Inventario.verFabricacion(con);
                    case 2 -> Inventario.crearOrdenMenu(con);
                    case 3 -> Inventario.terminarOrdenMenu(con);
                    case 4 -> Inventario.cancelarOrdenMenu(con);
                    case 5 -> Inventario.verPiezasDeProducto(con);
                    case 6 -> Informes.informeFabricacion(con);
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

    // CLIENTE: catalogo y pedidos propios
    private static void menuCliente(Connection con) {
        while (true) {
            titulo("MENU CLIENTE   (usuario: " + Usuarios.nombreActual + ")");
            opcion(1, "Ver catalogo de productos");
            opcion(2, "Hacer un pedido");
            opcion(3, "Mis pedidos");
            opcion(4, "Cancelar un pedido que espera stock");
            int op = pedirOpcion("Cerrar sesion");
            try {
                switch (op) {
                    case 1 -> Inventario.verProductos(con);
                    case 2 -> Ventas.hacerPedido(con);
                    case 3 -> Ventas.misPedidos(con);
                    case 4 -> Ventas.cancelarPedidoEnEspera(con);
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