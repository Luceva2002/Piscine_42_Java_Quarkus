# Modulo J05: Annotazioni e reflection

Obiettivo: capire cosa sono `@Path`, `@GET`, `@Inject`, `@Claim`. Un'annotazione è solo un'**etichetta**:
il comportamento lo dà qualcun altro che la legge. Qui costruirai a mano quel "qualcun altro",
con la reflection. Quarkus fa la stessa cosa, ma **a tempo di build** invece che a runtime.

Regole del modulo: niente librerie esterne. Ogni esercizio è un file autonomo con la propria `main`.
Dichiara nello stesso file le annotazioni e le classi di prova, dopo la classe `ExNN`.

---

## ex00: Le annotazioni che già conosci

**File:** `Ex00.java` (nessun test: leggi gli errori)

Scrivi una piccola classe e rompila di proposito, annotando cosa dice il compilatore:

1. `@Override` su un metodo con il nome sbagliato (`tostring()` invece di `toString()`)
2. `@FunctionalInterface` su un'interfaccia con **due** metodi astratti
3. `@Deprecated` su un metodo, e chiamalo da un altro: che avviso ottieni?
4. `@SuppressWarnings("deprecation")` per far sparire l'avviso

Domanda: queste annotazioni cambiano il comportamento del programma o vengono lette dal compilatore?

---

## ex01: Creare e leggere un'annotazione

**File:** `Ex01.java`

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Route {
    String method();
    String path();
}
```

Classe `Hello` con tre metodi pubblici: `hello()` annotato `@Route(method = "GET", path = "/hello")`,
`echo()` annotato `@Route(method = "POST", path = "/echo")` e `notExposed()` senza annotazione.

Scrivi `static List<String> collectRoutes(Class<?> type)` che con la reflection
(`getDeclaredMethods`, `getAnnotation`) ritorna, **in ordine alfabetico**, stringhe del tipo
`"GET /hello -> hello"`.

L'ordine di `getDeclaredMethods()` non è garantito: ecco perché si ordina.

---

## ex02: Retention

**File:** `Ex02.java`

Dichiara tre annotazioni senza elementi: una con `@Retention(RetentionPolicy.RUNTIME)`, una con
`RetentionPolicy.SOURCE` e una **senza** `@Retention`. Mettile tutte e tre su una classe.
Verifica con `isAnnotationPresent` quali sono visibili a runtime.

Domanda: a cosa serve `SOURCE`? E perché in Quarkus, dove molte annotazioni sono lette durante
la build, il costo della reflection a runtime sparisce?

---

## ex03: Invocare un metodo per reflection

**File:** `Ex03.java`

Riusa `@Route` e scrivi `static Object dispatch(Object controller, String httpMethod, String path)`:
cerca il metodo annotato con quel verbo e quel path e lo invoca con `Method.invoke`.
Se nessun metodo corrisponde lancia `NoSuchElementException("404 ...")`.

Se il metodo invocato lancia un'eccezione, `invoke` la avvolge in `InvocationTargetException`:
**scartala** e rilancia la causa originale.

---

## ex04: Un mini router con prefisso e parametri

**File:** `Ex04.java`

Aggiungi:

```java
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)      @interface Controller { String value(); }
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER) @interface PathVar   { String value(); }
```

e una classe `Router` con `register(Object controller)` e `Object handle(String httpMethod, String path)`.

- il path completo è `@Controller.value()` + `@Route.path()` (come `@Path` di classe + `@Path` di metodo)
- i segmenti `{nome}` del path diventano parametri: `@PathVar("id") Integer id` riceve il valore
  convertito in `Integer`, qualunque altro tipo viene passato come `String`
- nessuna route trovata → `NoSuchElementException`

```java
@Controller("/pools")
class PoolController {
    @Route(method = "GET", path = "")                  public String list() { return "lista"; }
    @Route(method = "GET", path = "/{id}")             public String get(@PathVar("id") Integer id) { return "pool " + id; }
    @Route(method = "GET", path = "/{id}/photo/{name}") public String photo(@PathVar("id") Integer id,
                                                                             @PathVar("name") String name) { return id + ":" + name; }
}
```

`router.handle("GET", "/pools/42")` → `"pool 42"`; `handle("GET", "/pools/7/photo/a.png")` → `"7:a.png"`.

Suggerimento: trasforma `{nome}` in un gruppo regex con nome (`(?<nome>[^/]+)`) e usa
`Matcher.matches()` e `Matcher.group("nome")`.

---

## ex05: Iniezione di dipendenze per reflection

**File:** `Ex05.java`

```java
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) @interface Inject {}
```

`Injector.inject(Object target, Map<Class<?>, Object> beans)`: per ogni campo annotato `@Inject`
(anche **privato**: `setAccessible(true)`) assegna il bean il cui tipo corrisponde al tipo del campo.
Bean mancante → `IllegalStateException("nessun bean per <Tipo>")`.

```java
class PoolResource { @Inject private PoolService service; String call() { return service.name(); } }
```

Domanda: perché in Quarkus puoi lasciare il campo package-private senza `private` e senza
setter? (Risposta guidata: leggi cosa dice la guida CDI di ArC sui campi `@Inject`.)

---

## Verifica

- Sai spiegare cosa fa un'annotazione da sola (niente) e chi le dà un significato.
- Sai spiegare perché `@RequestScoped` + `@Claim` ha bisogno che qualcuno crei l'oggetto per te.
- Sai spiegare in due frasi la differenza tra reflection a runtime e processing a build time.

<br><br><br>

---

# Soluzioni

Guardale solo **dopo** aver provato.

## ex00

Errori tipici:

1. `@Override` su `tostring()`: `error: method does not override or implement a method from a supertype`
2. `@FunctionalInterface` con due metodi: `error: Unexpected @FunctionalInterface annotation ... is not a functional interface`
3. Chiamare un metodo `@Deprecated`: `Note: ... uses or overrides a deprecated API` (con `-Xlint:deprecation` vedi il dettaglio)
4. `@SuppressWarnings("deprecation")` fa sparire la nota

Queste annotazioni sono lette **dal compilatore** (non cambiano il comportamento del programma):
controllano che il codice sia coerente. `@Path` e `@Inject` sono lette da altro codice (framework).

## ex01

```java
// Ex01.java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.*;

public class Ex01 {

    static List<String> collectRoutes(Class<?> type) {
        List<String> out = new ArrayList<>();
        for (Method m : type.getDeclaredMethods()) {
            Route r = m.getAnnotation(Route.class);
            if (r != null) {
                out.add(r.method() + " " + r.path() + " -> " + m.getName());
            }
        }
        Collections.sort(out);
        return out;
    }

    public static void main(String[] args) {
        check(collectRoutes(Hello.class).equals(List.of("GET /hello -> hello", "POST /echo -> echo")));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Route {
    String method();
    String path();
}

class Hello {
    @Route(method = "GET", path = "/hello")
    public String hello() { return "hello"; }

    @Route(method = "POST", path = "/echo")
    public String echo() { return "echo"; }

    public String notExposed() { return "x"; }
}
```

## ex02

```java
// Ex02.java
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class Ex02 {

    public static void main(String[] args) {
        check(Tagged.class.isAnnotationPresent(AtRuntime.class));
        check(!Tagged.class.isAnnotationPresent(AtSource.class));
        check(!Tagged.class.isAnnotationPresent(AtDefault.class));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

@Retention(RetentionPolicy.RUNTIME)
@interface AtRuntime {}

@Retention(RetentionPolicy.SOURCE)
@interface AtSource {}

@interface AtDefault {}

@AtRuntime
@AtSource
@AtDefault
class Tagged {}
```

- `SOURCE`: esiste solo nel sorgente, il compilatore la scarta (es. `@Override`).
- default (`CLASS`): finisce nel file `.class` ma la JVM non la rende visibile a runtime.
- `RUNTIME`: visibile con la reflection.

Quarkus legge le annotazioni dai `.class` durante la build e genera il codice di collegamento:
a runtime non fa scansioni, per questo parte in fretta.

## ex03

```java
// Ex03.java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.NoSuchElementException;

public class Ex03 {

    static Object dispatch(Object controller, String httpMethod, String path) throws Exception {
        for (Method m : controller.getClass().getDeclaredMethods()) {
            Route r = m.getAnnotation(Route.class);
            if (r != null && r.method().equals(httpMethod) && r.path().equals(path)) {
                try {
                    return m.invoke(controller);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof Exception ex) throw ex;
                    throw e;
                }
            }
        }
        throw new NoSuchElementException("404 " + httpMethod + " " + path);
    }

    public static void main(String[] args) throws Exception {
        Hello h = new Hello();
        check(dispatch(h, "GET", "/hello").equals("hello"));
        check(dispatch(h, "POST", "/echo").equals("echo"));
        try {
            dispatch(h, "GET", "/nope");
            throw new AssertionError();
        } catch (NoSuchElementException expected) {
            check(expected.getMessage().equals("404 GET /nope"));
        }
        try {
            dispatch(h, "GET", "/boom");
            throw new AssertionError();
        } catch (IllegalStateException expected) {
            check(expected.getMessage().equals("boom"));
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Route {
    String method();
    String path();
}

class Hello {
    @Route(method = "GET", path = "/hello")
    public String hello() { return "hello"; }

    @Route(method = "POST", path = "/echo")
    public String echo() { return "echo"; }

    @Route(method = "GET", path = "/boom")
    public String boom() { throw new IllegalStateException("boom"); }
}
```

`InvocationTargetException` è un wrapper: la causa vera è `getCause()`. Se non la scarti, il
chiamante vedrebbe sempre la stessa eccezione generica invece di quella del tuo codice.

## ex04

```java
// Ex04.java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Ex04 {

    public static void main(String[] args) throws Exception {
        Router router = new Router();
        router.register(new PoolController());

        check(router.handle("GET", "/pools").equals("lista"));
        check(router.handle("GET", "/pools/42").equals("pool 42"));
        check(router.handle("GET", "/pools/7/photo/a.png").equals("7:a.png"));
        try {
            router.handle("POST", "/pools/42");
            throw new AssertionError();
        } catch (NoSuchElementException expected) {
            // ok: il verbo non corrisponde
        }
        try {
            router.handle("GET", "/altro");
            throw new AssertionError();
        } catch (NoSuchElementException expected) {
            // ok
        }
        try {
            router.handle("GET", "/pools/fail");
            throw new AssertionError();
        } catch (NumberFormatException expected) {
            // "fail" non e' un intero
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

class Router {
    private record Handler(String httpMethod, Pattern pattern, Object controller, Method method) {}

    private final List<Handler> handlers = new ArrayList<>();

    void register(Object controller) {
        Class<?> type = controller.getClass();
        Controller c = type.getAnnotation(Controller.class);
        String prefix = c == null ? "" : c.value();
        for (Method m : type.getDeclaredMethods()) {
            Route r = m.getAnnotation(Route.class);
            if (r == null) continue;
            String regex = (prefix + r.path()).replaceAll("\\{(\\w+)\\}", "(?<$1>[^/]+)");
            handlers.add(new Handler(r.method(), Pattern.compile(regex), controller, m));
        }
    }

    Object handle(String httpMethod, String path) throws Exception {
        for (Handler h : handlers) {
            if (!h.httpMethod().equals(httpMethod)) continue;
            Matcher matcher = h.pattern().matcher(path);
            if (!matcher.matches()) continue;

            Parameter[] params = h.method().getParameters();
            Object[] args = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                PathVar pv = params[i].getAnnotation(PathVar.class);
                String raw = matcher.group(pv.value());
                args[i] = params[i].getType() == Integer.class ? (Object) Integer.valueOf(raw) : raw;
            }
            try {
                return h.method().invoke(h.controller(), args);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof Exception ex) throw ex;
                throw e;
            }
        }
        throw new NoSuchElementException("404 " + httpMethod + " " + path);
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Route {
    String method();
    String path();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Controller {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@interface PathVar {
    String value();
}

@Controller("/pools")
class PoolController {
    @Route(method = "GET", path = "")
    public String list() { return "lista"; }

    @Route(method = "GET", path = "/{id}")
    public String get(@PathVar("id") Integer id) { return "pool " + id; }

    @Route(method = "GET", path = "/{id}/photo/{name}")
    public String photo(@PathVar("id") Integer id, @PathVar("name") String name) { return id + ":" + name; }
}
```

Il punto `.` nei path (es. `a.png`) dentro un **valore** va bene perché `[^/]+` accetta qualunque
carattere tranne la barra; un `.` nel **template** della route sarebbe invece un carattere jolly
della regex, e andrebbe escapato (`Pattern.quote`). Quarkus REST gestisce tutto questo con un albero
di matching costruito a build time.

## ex05

```java
// Ex05.java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Map;

public class Ex05 {

    public static void main(String[] args) throws Exception {
        PoolResource r = new PoolResource();
        Injector.inject(r, Map.of(PoolService.class, new PoolService()));
        check(r.call().equals("service"));

        PoolResource other = new PoolResource();
        try {
            Injector.inject(other, Map.of());
            throw new AssertionError();
        } catch (IllegalStateException e) {
            check(e.getMessage().equals("nessun bean per PoolService"));
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface Inject {}

class PoolService {
    String name() { return "service"; }
}

class PoolResource {
    @Inject
    private PoolService service;

    String call() { return service.name(); }
}

class Injector {
    static void inject(Object target, Map<Class<?>, Object> beans) throws IllegalAccessException {
        for (Field f : target.getClass().getDeclaredFields()) {
            if (!f.isAnnotationPresent(Inject.class)) continue;
            Object bean = beans.get(f.getType());
            if (bean == null) {
                throw new IllegalStateException("nessun bean per " + f.getType().getSimpleName());
            }
            f.setAccessible(true);
            f.set(target, bean);
        }
    }
}
```

Senza `setAccessible(true)` un campo privato lancia `IllegalAccessException`. Quarkus (ArC) a build
time genera classi che assegnano direttamente il campo: per questo i campi `@Inject` si lasciano
package-private (non `private`) e senza getter/setter, così quel codice generato può toccarli
senza reflection.
