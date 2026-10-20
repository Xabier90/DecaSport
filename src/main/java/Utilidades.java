import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

public class Utilidades {

    private static final Scanner sc = new Scanner(System.in);

    // ---------- TECLADO ----------
    public static String leerLinea() {
        if (!sc.hasNextLine()) {
            System.out.println("\nEntrada cerrada. Saliendo...");
            System.exit(0);
        }
        return sc.nextLine();
    }

    public static int leerEntero(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            try {
                return Integer.parseInt(leerLinea().trim());
            } catch (NumberFormatException e) {
                System.out.println("Escribe un numero entero");
            }
        }
    }

    // Enter deja el valor actual. Repite si es menor que el minimo.
    public static int leerEnteroOpcional(String mensaje, int actual, int minimo) {
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

    public static double leerDecimal(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            try {
                return Double.parseDouble(leerLinea().trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                System.out.println("Escribe un numero (por ejemplo 19.99)");
            }
        }
    }

    public static boolean preguntarSiNo(String mensaje) {
        while (true) {
            System.out.print(mensaje + " (s/n): ");
            String r = leerLinea().trim().toLowerCase();
            if (r.equals("s") || r.equals("si")) return true;
            if (r.equals("n") || r.equals("no")) return false;
            System.out.println("Responde s o n");
        }
    }

    // ---------- BASE DE DATOS ----------
    public static boolean existe(Connection con, String sql, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void rollback(Connection con) {
        try {
            con.rollback();
        } catch (SQLException ignorada) {
        }
    }

    public static void activarAutoCommit(Connection con) {
        try {
            con.setAutoCommit(true);
        } catch (SQLException ignorada) {
        }
    }
}