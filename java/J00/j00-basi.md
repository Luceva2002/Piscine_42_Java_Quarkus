# Modulo J00: Basi del linguaggio

Obiettivo: stringhe, array, `switch`, tipi wrapper. Sono le cose che usi in `checkValidInfo`
e in ogni service.

Regole del modulo: niente stream e niente lambda (arrivano in J04). Solo cicli e `if`.

Ogni esercizio è un file `ExNN.java` con una `main`, da lanciare con `java ExNN.java`.
Per verificare usa l'helper `check` descritto nel README.

---

## ex00: Saluto da riga di comando

**File:** `Ex00.java`

`java Ex00.java Luca` stampa `ciao Luca`.
Senza argomenti stampa su **stderr** `uso: java Ex00.java <nome>` e termina con exit code 1.

Da usare: `String[] args`, `System.err`, `System.exit`.
Controlla l'exit code con `echo $?`.

---

## ex01: Normalizzare il telefono

**File:** `Ex01.java`

Scrivi `static String normalizePhone(String phone)` che toglie **tutti** gli spazi.

| Input | Output |
|---|---|
| `"333 123 4567"` | `"3331234567"` |
| `"  +39 06 1234 "` | `"+39061234"` |
| `null` | `null` |

Fallo in due modi: con `trim` + `split` + `String.join` (come nel progetto reale) e con
`replaceAll("\\s+", "")`. Cosa succede con due spazi di fila se splitti su `" "` e non su `"\\s+"`?

---

## ex02: Estrarre il dominio

**File:** `Ex02.java`

`static String extractDomain(String email)` ritorna la parte dopo la `@`.
Se l'email non ha **esattamente una** `@` con qualcosa prima e qualcosa dopo, lancia
`IllegalArgumentException("Dominio non valido")`.

| Input | Risultato |
|---|---|
| `"a@b.it"` | `"b.it"` |
| `"abc"`, `"a@"`, `"@b.it"`, `"a@@b.it"`, `null` | eccezione |

Attenzione: `"a@".split("@")` scarta la stringa vuota finale. Leggi cosa fa il secondo
parametro `limit` di `split` e usa `-1`.

---

## ex03: Array

**File:** `Ex03.java`

Senza usare `Arrays` né stream:

- `static int max(int[] a)`: l'elemento più grande; array vuoto → `IllegalArgumentException`
- `static int[] reverse(int[] a)`: **un nuovo array** con gli elementi al contrario
- `static int count(int[] a, int x)`: quante volte compare `x`

---

## ex04: Switch come espressione

**File:** `Ex04.java`

`static String statusName(int code)`: `1` → `"iscrizioni aperte"`, `2` → `"iscrizioni chiuse"`,
`3` → `"evento chiuso"`, qualsiasi altro valore → `IllegalArgumentException`.

Usa la forma `switch (code) { case 1 -> ...; default -> ... }` che **ritorna un valore**.
Poi riscrivilo con `if / else if` e confronta.

---

## ex05: Costruire query con i placeholder

**File:** `Ex05.java`

Con `StringBuilder` e un ciclo (niente `+` dentro il ciclo):

- `static String buildWhere(List<String> columns)`
- `static String buildInsert(String table, List<String> columns)`

| Chiamata | Risultato |
|---|---|
| `buildWhere(List.of("city", "open"))` | `"WHERE city = $1 AND open = $2"` |
| `buildWhere(List.of())` | `""` |
| `buildInsert("pool", List.of("name", "city", "capacity"))` | `"INSERT INTO pool (name, city, capacity) VALUES ($1, $2, $3)"` |

È la stessa logica che userai nel modulo Quarkus m02 per i filtri dinamici di `PgPool`.

---

## ex06: `int` contro `Integer`

**File:** `Ex06.java`

Prima **scrivi su carta** cosa stampa ogni riga, poi verifica con `check`:

1. `Integer a = 127, b = 127;` → `a == b` ?
2. `Integer c = 1000, d = 1000;` → `c == d` ? e `c.equals(d)` ?
3. `Integer missing = null; int x = missing;` → cosa succede?
4. `String s = new String("a");` → `s == "a"` ? e `s.equals("a")` ?

Nel progetto reale i parametri sono `Integer eventId`: perché un `Integer` può essere
`null` e un `int` no?

---

## Verifica

- Sai spiegare perché le stringhe sono immutabili e cosa ritorna `trim()`.
- Sai dire quando usare `==` e quando `equals`.
- Sai spiegare l'esito di tutti i casi dell'ex06.

<br><br><br>

---

# Soluzioni

Guardale solo **dopo** aver provato.

## ex00

```java
// Ex00.java
public class Ex00 {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("uso: java Ex00.java <nome>");
            System.exit(1);
        }
        System.out.println("ciao " + args[0]);
    }
}
```

## ex01

```java
// Ex01.java
public class Ex01 {

    static String normalizePhone(String phone) {
        if (phone == null) return null;
        String[] parts = phone.trim().split("\\s+");
        return String.join("", parts);
    }

    static String normalizePhone2(String phone) {
        return phone == null ? null : phone.replaceAll("\\s+", "");
    }

    public static void main(String[] args) {
        check(normalizePhone("333 123 4567").equals("3331234567"));
        check(normalizePhone("  +39 06 1234 ").equals("+39061234"));
        check(normalizePhone(null) == null);
        check(normalizePhone2("333 123 4567").equals("3331234567"));
        check(normalizePhone2("  +39 06 1234 ").equals("+39061234"));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

Se splitti su un singolo spazio, due spazi di fila producono una stringa vuota nell'array:
il risultato finale è comunque giusto perché `join("")` la ignora, ma con un altro separatore
non lo sarebbe.

## ex02

```java
// Ex02.java
public class Ex02 {

    static String extractDomain(String email) {
        if (email == null) throw new IllegalArgumentException("Dominio non valido");
        String[] parts = email.split("@", -1);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new IllegalArgumentException("Dominio non valido");
        }
        return parts[1];
    }

    public static void main(String[] args) {
        check(extractDomain("a@b.it").equals("b.it"));
        String[] invalid = {"abc", "a@", "@b.it", "a@@b.it", null};
        for (String bad : invalid) {
            try {
                extractDomain(bad);
                throw new AssertionError("doveva fallire: " + bad);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

Con `split("@")` senza `-1`, `"a@"` diventa `["a"]` (lunghezza 1) e `"a@@b.it"` diventa
`["a", "", "b.it"]` (lunghezza 3): con `-1` i casi limite restano visibili.

## ex03

```java
// Ex03.java
public class Ex03 {

    static int max(int[] a) {
        if (a.length == 0) throw new IllegalArgumentException("array vuoto");
        int best = a[0];
        for (int i = 1; i < a.length; i++) {
            if (a[i] > best) best = a[i];
        }
        return best;
    }

    static int[] reverse(int[] a) {
        int[] r = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            r[a.length - 1 - i] = a[i];
        }
        return r;
    }

    static int count(int[] a, int x) {
        int n = 0;
        for (int v : a) {
            if (v == x) n++;
        }
        return n;
    }

    public static void main(String[] args) {
        check(max(new int[]{3, 9, 2}) == 9);
        check(java.util.Arrays.equals(reverse(new int[]{1, 2, 3}), new int[]{3, 2, 1}));
        check(count(new int[]{1, 2, 1, 1}, 1) == 3);
        try {
            max(new int[0]);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
            // ok
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

## ex04

```java
// Ex04.java
public class Ex04 {

    static String statusName(int code) {
        return switch (code) {
            case 1 -> "iscrizioni aperte";
            case 2 -> "iscrizioni chiuse";
            case 3 -> "evento chiuso";
            default -> throw new IllegalArgumentException("stato sconosciuto: " + code);
        };
    }

    static String statusNameOld(int code) {
        if (code == 1) return "iscrizioni aperte";
        else if (code == 2) return "iscrizioni chiuse";
        else if (code == 3) return "evento chiuso";
        throw new IllegalArgumentException("stato sconosciuto: " + code);
    }

    public static void main(String[] args) {
        check(statusName(1).equals("iscrizioni aperte"));
        check(statusName(3).equals("evento chiuso"));
        check(statusNameOld(2).equals("iscrizioni chiuse"));
        try {
            statusName(9);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
            // ok
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

La forma con `->` non ha "fall through": non serve il `break`.

## ex05

```java
// Ex05.java
import java.util.List;

public class Ex05 {

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
```

Perché `StringBuilder` e non `+` nel ciclo: ogni `+` crea una nuova stringa, mentre
`StringBuilder` modifica un buffer interno.

## ex06

```java
// Ex06.java
public class Ex06 {

    @SuppressWarnings("StringEquality")
    public static void main(String[] args) {
        Integer a = 127, b = 127;
        check(a == b);              // stessa istanza: la cache copre -128..127

        Integer c = 1000, d = 1000;
        check(c != d);              // istanze diverse, == confronta i riferimenti
        check(c.equals(d));         // equals confronta i valori
        check(c.intValue() == d);   // con un int a sinistra, d viene "unboxato"

        Integer missing = null;
        try {
            int x = missing;        // unboxing di null
            throw new AssertionError("x=" + x);
        } catch (NullPointerException expected) {
            // ok
        }

        String s = new String("a");
        check(s != "a");            // oggetti diversi
        check(s.equals("a"));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

Con `Integer eventId` puoi controllare `eventId == null`; con `int` il valore mancante
non esiste (e un `null` unboxato lancia `NullPointerException`).
