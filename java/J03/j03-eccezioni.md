# Modulo J03: Eccezioni

Obiettivo: `try/catch/finally`, checked contro unchecked, eccezioni custom, `try-with-resources`,
catene di cause. È il modo in cui il backend segnala gli errori (`CustomException`, mapper, `throws IOException`).

Regole del modulo: niente stream. Ogni esercizio è un file `ExNN.java` autonomo.

---

## ex00: `try / catch / finally`

**File:** `Ex00.java`

- `static int parseOrDefault(String s, int def)`: converte con `Integer.parseInt`; se la stringa non è un
  numero (o è `null`) ritorna `def`
- `static List<String> order(boolean fail)`: dentro `try / catch / finally` aggiunge a una lista i
  messaggi `"try"`, `"fine try"`, `"catch"`, `"finally"` nell'ordine in cui vengono eseguiti

| Chiamata | Lista |
|---|---|
| `order(false)` | `["try", "fine try", "finally"]` |
| `order(true)` (il `try` lancia `IllegalStateException`) | `["try", "catch", "finally"]` |

Domanda: il blocco `finally` viene eseguito anche se nel `try` c'è un `return`?

---

## ex01: Checked exception

**File:** `Ex01.java`

`static byte[] readAll(Path p) throws IOException` con `Files.readAllBytes`.
In `main` crea un file temporaneo (`Files.createTempFile`), scrivici 4 byte, leggili e **cancellalo
in un `finally`**.
Poi leggi un file che non esiste e cattura **prima** `NoSuchFileException` e poi `IOException`:
perché l'ordine dei `catch` conta?

Domanda: perché `newEvent` del progetto reale dichiara `throws IOException`, mentre
`throw new CustomException(...)` non ha bisogno di nessuna dichiarazione?

---

## ex02: Gerarchia di eccezioni

**File:** `Ex02.java`

```java
class AppException extends RuntimeException { ... }          // ha un campo code
class NotFoundException extends AppException { ... }         // code "error.not_found"
class ConflictException extends AppException { ... }         // code "error.conflict"
```

Poi `static int httpStatus(Throwable t)` che usa lo **switch con pattern matching**:
`NotFoundException` → 404, `ConflictException` → 409, qualsiasi altra `AppException` → 400,
tutto il resto → 500.

È la stessa logica di un exception mapper di Quarkus. Cosa succede se nel `switch` metti
`AppException` **prima** di `NotFoundException`? Leggi l'errore del compilatore.

---

## ex03: `try-with-resources`

**File:** `Ex03.java`

Classe `Res implements AutoCloseable` che registra su una lista statica `"open <nome>"` quando viene
creata e `"close <nome>"` quando viene chiusa. Un flag permette di far **fallire** la chiusura.

1. `try (Res a = ...; Res b = ...) { log.add("body"); }` deve produrre
   `["open A", "open B", "body", "close B", "close A"]` (chiusura in ordine inverso)
2. se il corpo lancia `RuntimeException("body fallito")` **e** anche `close` fallisce, l'eccezione
   che arriva è quella del corpo e l'altra è in `getSuppressed()`

---

## ex04: Eccezioni dentro le lambda

**File:** `Ex04.java`

Una lambda per `Function<T, R>` non può lanciare checked exception. Scrivi:

```java
@FunctionalInterface
interface ThrowingFunction<T, R> { R apply(T t) throws Exception; }

static <T, R> Function<T, R> unchecked(ThrowingFunction<T, R> f)
```

`unchecked` ritorna una `Function` normale che, se `f` lancia un `IOException`, lo avvolge in
`UncheckedIOException`; per altre checked exception usa `RuntimeException`; le `RuntimeException`
passano così come sono.

```java
Function<String, Long> size = unchecked(s -> Files.size(Path.of(s)));
size.apply("/non/esiste")      // UncheckedIOException con causa NoSuchFileException
```

---

## ex05: Catena di cause

**File:** `Ex05.java`

`AppException` con un costruttore che accetta anche la causa. Poi:

`static int parseId(String s)` che converte con `Integer.parseInt` e, in caso di
`NumberFormatException`, lancia `AppException("error.invalid_id", "id non valido: " + s, e)`.

Verifica che `getCause()` sia la `NumberFormatException` originale e guarda come lo mostra
`e.printStackTrace()` (la riga `Caused by:`). Perché non bisogna **perdere** la causa?

---

## Verifica

- Sai spiegare la differenza tra checked e unchecked e perché Quarkus REST/Mutiny usano
  soprattutto unchecked.
- Sai spiegare l'ordine dei `catch` (dal più specifico al più generale).
- Sai dire cosa fa `try-with-resources` e perché è meglio di `finally` + `close()`.

<br><br><br>

---

# Soluzioni

Guardale solo **dopo** aver provato.

## ex00

```java
// Ex00.java
import java.util.*;

public class Ex00 {

    static int parseOrDefault(String s, int def) {
        if (s == null) return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static List<String> order(boolean fail) {
        List<String> log = new ArrayList<>();
        try {
            log.add("try");
            if (fail) throw new IllegalStateException("boom");
            log.add("fine try");
        } catch (IllegalStateException e) {
            log.add("catch");
        } finally {
            log.add("finally");
        }
        return log;
    }

    public static void main(String[] args) {
        check(parseOrDefault("42", -1) == 42);
        check(parseOrDefault(" 7 ", -1) == 7);
        check(parseOrDefault("abc", -1) == -1);
        check(parseOrDefault(null, -1) == -1);
        check(order(false).equals(List.of("try", "fine try", "finally")));
        check(order(true).equals(List.of("try", "catch", "finally")));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

Sì: `finally` viene eseguito anche con un `return` nel `try` (il valore di ritorno è già calcolato).
Mettere un `return` dentro il `finally` è una pessima idea perché nasconde eccezioni e altri `return`.

## ex01

```java
// Ex01.java
import java.io.IOException;
import java.nio.file.*;

public class Ex01 {

    static byte[] readAll(Path p) throws IOException {
        return Files.readAllBytes(p);
    }

    static String describe(Path p) {
        try {
            readAll(p);
            return "ok";
        } catch (NoSuchFileException e) {
            return "mancante";
        } catch (IOException e) {
            return "errore io";
        }
    }

    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempFile("piscina", ".txt");
        try {
            Files.writeString(tmp, "ciao");
            check(readAll(tmp).length == 4);
            check(describe(tmp).equals("ok"));
        } finally {
            Files.deleteIfExists(tmp);
        }
        check(describe(Path.of("/non/esiste.txt")).equals("mancante"));
        try {
            readAll(Path.of("/non/esiste.txt"));
            throw new AssertionError();
        } catch (NoSuchFileException expected) {
            // ok
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

`NoSuchFileException` è una sottoclasse di `IOException`: se metti prima `IOException` il
`catch` successivo non verrebbe mai raggiunto e il compilatore dà errore.
`IOException` è **checked** (il compilatore ti obbliga a dichiararla o gestirla);
`CustomException` estende `RuntimeException`, quindi è **unchecked**.

## ex02

```java
// Ex02.java
public class Ex02 {

    static int httpStatus(Throwable t) {
        return switch (t) {
            case NotFoundException e -> 404;
            case ConflictException e -> 409;
            case AppException e -> 400;
            default -> 500;
        };
    }

    public static void main(String[] args) {
        check(httpStatus(new NotFoundException("x")) == 404);
        check(httpStatus(new ConflictException("x")) == 409);
        check(httpStatus(new AppException("error.custom", "x")) == 400);
        check(httpStatus(new IllegalStateException("x")) == 500);
        check(new NotFoundException("x").code().equals("error.not_found"));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

class AppException extends RuntimeException {
    private final String code;

    AppException(String code, String message) {
        super(message);
        this.code = code;
    }

    String code() {
        return code;
    }
}

class NotFoundException extends AppException {
    NotFoundException(String message) {
        super("error.not_found", message);
    }
}

class ConflictException extends AppException {
    ConflictException(String message) {
        super("error.conflict", message);
    }
}
```

Se metti `AppException` prima, il compilatore segnala che il caso `NotFoundException` è
"dominato" (non raggiungibile): i casi più specifici vanno sempre prima.

## ex03

```java
// Ex03.java
import java.util.*;

public class Ex03 {

    static final List<String> log = new ArrayList<>();

    static class Res implements AutoCloseable {
        private final String name;
        private final boolean failOnClose;

        Res(String name, boolean failOnClose) {
            this.name = name;
            this.failOnClose = failOnClose;
            log.add("open " + name);
        }

        @Override
        public void close() {
            log.add("close " + name);
            if (failOnClose) throw new IllegalStateException("close fallita " + name);
        }
    }

    public static void main(String[] args) {
        try (Res a = new Res("A", false); Res b = new Res("B", false)) {
            log.add("body");
        }
        check(log.equals(List.of("open A", "open B", "body", "close B", "close A")));

        log.clear();
        try (Res c = new Res("C", true)) {
            throw new RuntimeException("body fallito");
        } catch (RuntimeException e) {
            check(e.getMessage().equals("body fallito"));
            check(e.getSuppressed().length == 1);
            check(e.getSuppressed()[0].getMessage().equals("close fallita C"));
        }
        check(log.equals(List.of("open C", "close C")));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

Con `finally` + `close()` a mano, un'eccezione lanciata dalla `close` **sostituirebbe** quella del
corpo e perderesti l'errore originale. `try-with-resources` la tiene come "suppressed".

## ex04

```java
// Ex04.java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.function.Function;

public class Ex04 {

    static <T, R> Function<T, R> unchecked(ThrowingFunction<T, R> f) {
        return t -> {
            try {
                return f.apply(t);
            } catch (RuntimeException e) {
                throw e;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static void main(String[] args) throws Exception {
        Function<String, Long> size = unchecked(s -> Files.size(Path.of(s)));

        Path tmp = Files.createTempFile("piscina", ".txt");
        try {
            Files.writeString(tmp, "abcd");
            check(size.apply(tmp.toString()) == 4L);
        } finally {
            Files.deleteIfExists(tmp);
        }

        try {
            size.apply("/non/esiste");
            throw new AssertionError();
        } catch (UncheckedIOException e) {
            check(e.getCause() instanceof NoSuchFileException);
        }

        Function<String, Integer> boom = unchecked(s -> {
            throw new IllegalStateException("gia' unchecked");
        });
        try {
            boom.apply("x");
            throw new AssertionError();
        } catch (IllegalStateException e) {
            check(e.getMessage().equals("gia' unchecked"));
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

@FunctionalInterface
interface ThrowingFunction<T, R> {
    R apply(T t) throws Exception;
}
```

Le lambda implementano interfacce funzionali come `Function`, il cui metodo non dichiara
`throws`: per questo serve un adattatore. Nel progetto reale lo trovi ogni volta che dentro
una catena `Uni` devi chiamare codice bloccante che lancia checked exception.

## ex05

```java
// Ex05.java
public class Ex05 {

    static int parseId(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new AppException("error.invalid_id", "id non valido: " + s, e);
        }
    }

    public static void main(String[] args) {
        check(parseId("12") == 12);
        try {
            parseId("x1");
            throw new AssertionError();
        } catch (AppException e) {
            check(e.code().equals("error.invalid_id"));
            check(e.getMessage().equals("id non valido: x1"));
            check(e.getCause() instanceof NumberFormatException);
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

class AppException extends RuntimeException {
    private final String code;

    AppException(String code, String message) {
        super(message);
        this.code = code;
    }

    AppException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    String code() {
        return code;
    }
}
```

La causa serve a capire **perché** è fallita l'operazione: senza, nei log vedresti solo
"id non valido" e non sapresti che era un errore di conversione numerica.
