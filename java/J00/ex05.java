// Ex05.java
import java.util.List;

public class ex05 {

    static String buildWhere(List<String> columns) {
        if (columns.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("WHERE ");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(" AND ");
            sb.append(columns.get(i)).append(" = $").append(i + 1);
        }
        return sb.toString();
    }

    static String buildInsert(String table, List<String> columns) {
        StringBuilder cols = new StringBuilder();
        StringBuilder vals = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                cols.append(", ");
                vals.append(", ");
            }
            cols.append(columns.get(i));
            vals.append("$").append(i + 1);
        }
        return "INSERT INTO " + table + " (" + cols + ") VALUES (" + vals + ")";
    }

    public static void main(String[] args) {
        check(buildWhere(List.of("city", "open")).equals("WHERE city = $1 AND open = $2"));
        check(buildWhere(List.of()).equals(""));
        check(buildInsert("pool", List.of("name", "city", "capacity"))
                .equals("INSERT INTO pool (name, city, capacity) VALUES ($1, $2, $3)"));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}