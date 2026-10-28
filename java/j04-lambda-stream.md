# Modulo J04: Lambda e stream

Obiettivo: interfacce funzionali, method reference, stream, e capire perché Mutiny
(`flatMap`, `onItem().transform`, `Uni`) funziona come funziona.

Per i dati di prova usa questo record e questa lista:

```java
record Pool(int id, String name, String city, int capacity, boolean open) {}

List<Pool> pools = List.of(
    new Pool(1, "Acquacenter", "Roma", 120, true),
    new Pool(2, "Blu Club", "Milano", 80, true),
    new Pool(3, "Delfino", "Roma", 60, false),
    new Pool(4, "Nettuno", "Torino", 60, true),
    new Pool(5, "Zeta", "Roma", 40, true));
```

---

## ex00: Le interfacce funzionali standard

**File:** `Ex00.java`

Dichiara una variabile per ciascuna di queste e invocala:

| Tipo | Valore | Verifica |
|---|---|---|
| `Function<String, Integer>` | lunghezza della stringa (`String::length`) | `apply("abc")` → 3 |
| `Predicate<String>` | `String::isBlank` | `test("  ")` → `true`; con `negate()` il contrario |
| `Supplier<String>` | `() -> "ciao"` | `get()` → `"ciao"` |
| `Consumer<String>` | aggiunge a una lista (`lista::add`) | dopo `accept("x")` la lista contiene `"x"` |
| `BiFunction<Integer, Integer, Integer>` | `Integer::sum` | `apply(2, 3)` → 5 |
| `UnaryOperator<String>` | `String::toUpperCase` | `apply("a")` → `"A"` |

Poi `len.andThen(n -> n * 2).apply("abc")` deve valere 6.
Domanda: cos'è un'interfaccia funzionale e perché una lambda può "diventare" una `Function`?

---

## ex01: La tua interfaccia funzionale

**File:** `Ex01.java`

```java
@FunctionalInterface
interface Transformer<A, B> {
    B apply(A a);
    default <C> Transformer<A, C> andThen(Transformer<B, C> next) { ... }
}
```

Implementa `andThen` e verifica una pipeline costruita solo con method reference:

```java
Transformer<String, String> trim = String::trim;
Transformer<String, Integer> pipeline = trim.andThen(String::toUpperCase).andThen(String::length);
pipeline.apply("  abc ")    // 3
```

---

## ex02: Stream di base

**File:** `Ex02.java`

- `static List<String> openNamesIn(List<Pool> pools, String city)`: nomi delle piscine **aperte** in
  quella città, ordinati alfabeticamente → per `"Roma"`: `["Acquacenter", "Zeta"]`
- `static int totalCapacity(List<Pool> pools)` con `mapToInt` → 360
- `static boolean anyClosed(List<Pool> pools)` con `anyMatch` → `true`

Da usare: `stream()`, `filter`, `map`, `sorted`, `toList()`.
Cosa succede se crei lo stream ma **non** chiami nessuna operazione terminale?

---

## ex03: Collectors

**File:** `Ex03.java`

- `static Map<String, Long> countByCity(List<Pool> pools)` con `groupingBy` e `counting`
  (con `TreeMap::new` per l'ordine) → `{Milano=1, Roma=3, Torino=1}`
- `static double averageCapacity(List<Pool> pools)` con `averagingInt` → 72.0
- `static String namesJoined(List<Pool> pools)` con `joining(", ")` →
  `"Acquacenter, Blu Club, Delfino, Nettuno, Zeta"`
- `static Map<Boolean, Long> openVsClosed(List<Pool> pools)` con `partitioningBy` → `{false=1, true=4}`

---

## ex04: `flatMap`, `reduce`, `Optional`

**File:** `Ex04.java`

```java
record Guest(String email) {}
record Event(String title, List<Guest> guests) {}
```

- `static List<String> allEmails(List<Event> events)`: tutte le email di tutti gli eventi, senza doppioni, ordinate
  (`flatMap`, `map`, `distinct`, `sorted`)
- `static Optional<Pool> biggest(List<Pool> pools)` con `max(Comparator...)`
- `static int totalCapacity(List<Pool> pools)` con `reduce`
- `static Optional<String> openCityOf(List<Pool> pools, int id)`: la città della piscina `id`
  **solo se è aperta**; altrimenti `Optional.empty()`. Usa `Optional.flatMap`.

Differenza tra `map` e `flatMap`: `map` applica una funzione che ritorna un **valore**, `flatMap` una
che ritorna un **contenitore** e lo "appiattisce". È la stessa differenza tra `transform` e
`transformToUni` in Mutiny.

---

## ex05: Costruisci `Maybe` e `Lazy`

**File:** `Ex05.java`

Questo è l'esercizio più importante del modulo: costruisci a mano due mini `Uni`.

**1. `Maybe<T>`**: un valore che può mancare.

- `static <T> Maybe<T> of(T v)`, `static <T> Maybe<T> empty()`
- `<R> Maybe<R> map(Function<? super T, ? extends R> f)`: se vuoto, **non** chiama `f`
- `<R> Maybe<R> flatMap(Function<? super T, Maybe<R>> f)`
- `T orElse(T other)`

```java
Maybe.of("12").flatMap(s -> parse(s)).map(n -> n + 1).orElse(-1)    // 13
Maybe.of("x").flatMap(s -> parse(s)).map(n -> n + 1).orElse(-1)     // -1
```
(`parse` ritorna `Maybe<Integer>`, vuoto se la stringa non è un numero.)

**2. `Lazy<T>`**: un calcolo **descritto ma non eseguito**.

- `static <T> Lazy<T> of(Supplier<T> s)`, `map`, `flatMap`, `T get()`

```java
int[] calls = {0};
Lazy<Integer> l = Lazy.of(() -> { calls[0]++; return 20; })
        .map(x -> x + 1)
        .flatMap(x -> Lazy.of(() -> x * 2));
// calls[0] == 0: nessun calcolo e' partito
l.get()   // 42, e solo ora calls[0] == 1
```

Perché è importante: un `Uni` è **pigro** come `Lazy`. Costruire la catena non esegue niente:
parte tutto quando qualcuno si "iscrive" (in Quarkus lo fa il framework dopo che hai ritornato l'`Uni`).

---

## ex06: Closure

**File:** `Ex06.java`

1. Prova a scrivere `int total = 0; List.of(1, 2, 3).forEach(n -> total += n);` e leggi l'errore del compilatore
2. Risolvi con `AtomicInteger` (e poi con un array `int[]`)
3. `static Supplier<Integer> counter(int start)`: ogni chiamata a `get()` ritorna `start`, `start + 1`, ...

Regola da saper spiegare: una lambda può usare variabili locali solo se sono *effectively final*. Perché?

---

## Verifica

- Sai disegnare su carta la catena `Lazy.of(...).map(...).flatMap(...)` con i tipi di ogni passaggio.
- Sai spiegare perché lo stream non fa nulla senza un'operazione terminale.
- Sai spiegare la differenza tra `map` e `flatMap`.

<br><br><br>

---

# Soluzioni

Guardale solo **dopo** aver provato.

## ex00

```java
// Ex00.java
import java.util.*;
import java.util.function.*;

public class Ex00 {

    public static void main(String[] args) {
        Function<String, Integer> len = String::length;
        Predicate<String> blank = String::isBlank;
        Supplier<String> greeting = () -> "ciao";
        List<String> log = new ArrayList<>();
        Consumer<String> sink = log::add;
        BiFunction<Integer, Integer, Integer> sum = Integer::sum;
        UnaryOperator<String> upper = String::toUpperCase;

        check(len.apply("abc") == 3);
        check(blank.test("  "));
        check(blank.negate().test("a"));
        check(greeting.get().equals("ciao"));
        sink.accept("x");
        check(log.equals(List.of("x")));
        check(sum.apply(2, 3) == 5);
        check(upper.apply("a").equals("A"));
        check(len.andThen(n -> n * 2).apply("abc") == 6);
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

Un'interfaccia funzionale ha **un solo metodo astratto**. Una lambda (o una method reference) è
un modo compatto di scrivere un'implementazione di quel metodo.

## ex01

```java
// Ex01.java
public class Ex01 {

    public static void main(String[] args) {
        Transformer<String, String> trim = String::trim;
        Transformer<String, Integer> pipeline = trim.andThen(String::toUpperCase).andThen(String::length);
        check(pipeline.apply("  abc ") == 3);
        check(trim.andThen(String::toUpperCase).apply(" ab ").equals("AB"));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

@FunctionalInterface
interface Transformer<A, B> {
    B apply(A a);

    default <C> Transformer<A, C> andThen(Transformer<B, C> next) {
        return a -> next.apply(apply(a));
    }
}
```

## ex02

```java
// Ex02.java
import java.util.*;

public class Ex02 {

    static List<String> openNamesIn(List<Pool> pools, String city) {
        return pools.stream()
                .filter(p -> p.open() && p.city().equalsIgnoreCase(city))
                .map(Pool::name)
                .sorted()
                .toList();
    }

    static int totalCapacity(List<Pool> pools) {
        return pools.stream().mapToInt(Pool::capacity).sum();
    }

    static boolean anyClosed(List<Pool> pools) {
        return pools.stream().anyMatch(p -> !p.open());
    }

    public static void main(String[] args) {
        List<Pool> pools = List.of(
                new Pool(1, "Acquacenter", "Roma", 120, true),
                new Pool(2, "Blu Club", "Milano", 80, true),
                new Pool(3, "Delfino", "Roma", 60, false),
                new Pool(4, "Nettuno", "Torino", 60, true),
                new Pool(5, "Zeta", "Roma", 40, true));

        check(openNamesIn(pools, "Roma").equals(List.of("Acquacenter", "Zeta")));
        check(totalCapacity(pools) == 360);
        check(anyClosed(pools));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

record Pool(int id, String name, String city, int capacity, boolean open) {}
```

Uno stream senza operazione terminale (`toList`, `sum`, `collect`, `forEach`, ...) non esegue
niente: le operazioni intermedie (`filter`, `map`, ...) sono solo una descrizione, come in `Lazy`.

## ex03

```java
// Ex03.java
import java.util.*;
import java.util.stream.Collectors;

public class Ex03 {

    static Map<String, Long> countByCity(List<Pool> pools) {
        return pools.stream().collect(
                Collectors.groupingBy(Pool::city, TreeMap::new, Collectors.counting()));
    }

    static double averageCapacity(List<Pool> pools) {
        return pools.stream().collect(Collectors.averagingInt(Pool::capacity));
    }

    static String namesJoined(List<Pool> pools) {
        return pools.stream().map(Pool::name).collect(Collectors.joining(", "));
    }

    static Map<Boolean, Long> openVsClosed(List<Pool> pools) {
        return pools.stream().collect(
                Collectors.partitioningBy(Pool::open, Collectors.counting()));
    }

    public static void main(String[] args) {
        List<Pool> pools = List.of(
                new Pool(1, "Acquacenter", "Roma", 120, true),
                new Pool(2, "Blu Club", "Milano", 80, true),
                new Pool(3, "Delfino", "Roma", 60, false),
                new Pool(4, "Nettuno", "Torino", 60, true),
                new Pool(5, "Zeta", "Roma", 40, true));

        check(countByCity(pools).equals(Map.of("Milano", 1L, "Roma", 3L, "Torino", 1L)));
        check(averageCapacity(pools) == 72.0);
        check(namesJoined(pools).equals("Acquacenter, Blu Club, Delfino, Nettuno, Zeta"));
        check(openVsClosed(pools).equals(Map.of(false, 1L, true, 4L)));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

record Pool(int id, String name, String city, int capacity, boolean open) {}
```

## ex04

```java
// Ex04.java
import java.util.*;

public class Ex04 {

    static List<String> allEmails(List<Event> events) {
        return events.stream()
                .flatMap(e -> e.guests().stream())
                .map(Guest::email)
                .distinct()
                .sorted()
                .toList();
    }

    static Optional<Pool> biggest(List<Pool> pools) {
        return pools.stream().max(Comparator.comparingInt(Pool::capacity));
    }

    static int totalCapacity(List<Pool> pools) {
        return pools.stream().reduce(0, (acc, p) -> acc + p.capacity(), Integer::sum);
    }

    static Optional<Pool> findById(List<Pool> pools, int id) {
        return pools.stream().filter(p -> p.id() == id).findFirst();
    }

    static Optional<String> openCityOf(List<Pool> pools, int id) {
        return findById(pools, id)
                .flatMap(p -> p.open() ? Optional.of(p.city()) : Optional.empty());
    }

    public static void main(String[] args) {
        List<Pool> pools = List.of(
                new Pool(1, "Acquacenter", "Roma", 120, true),
                new Pool(2, "Blu Club", "Milano", 80, true),
                new Pool(3, "Delfino", "Roma", 60, false));
        List<Event> events = List.of(
                new Event("e1", List.of(new Guest("a@x.it"), new Guest("b@x.it"))),
                new Event("e2", List.of(new Guest("b@x.it"), new Guest("c@x.it"))));

        check(allEmails(events).equals(List.of("a@x.it", "b@x.it", "c@x.it")));
        check(biggest(pools).get().id() == 1);
        check(biggest(List.of()).isEmpty());
        check(totalCapacity(pools) == 260);
        check(openCityOf(pools, 1).equals(Optional.of("Roma")));
        check(openCityOf(pools, 3).isEmpty());
        check(openCityOf(pools, 99).isEmpty());
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

record Guest(String email) {}

record Event(String title, List<Guest> guests) {}

record Pool(int id, String name, String city, int capacity, boolean open) {}
```

## ex05

```java
// Ex05.java
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public class Ex05 {

    static Maybe<Integer> parse(String s) {
        try {
            return Maybe.of(Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return Maybe.empty();
        }
    }

    public static void main(String[] args) {
        // Maybe
        check(Maybe.of(2).map(x -> x * 10).orElse(0) == 20);
        int[] mapCalls = {0};
        check(Maybe.<Integer>empty().map(x -> {
            mapCalls[0]++;
            return x;
        }).orElse(7) == 7);
        check(mapCalls[0] == 0);
        check(Maybe.of("12").flatMap(s -> parse(s)).map(n -> n + 1).orElse(-1) == 13);
        check(Maybe.of("x").flatMap(s -> parse(s)).map(n -> n + 1).orElse(-1) == -1);

        // Lazy
        int[] calls = {0};
        Lazy<Integer> l = Lazy.of(() -> {
                    calls[0]++;
                    return 20;
                })
                .map(x -> x + 1)
                .flatMap(x -> Lazy.of(() -> x * 2));
        check(calls[0] == 0);
        check(l.get() == 42);
        check(calls[0] == 1);
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

class Maybe<T> {
    private final T value;

    private Maybe(T value) {
        this.value = value;
    }

    static <T> Maybe<T> of(T v) {
        return new Maybe<>(Objects.requireNonNull(v));
    }

    static <T> Maybe<T> empty() {
        return new Maybe<>(null);
    }

    <R> Maybe<R> map(Function<? super T, ? extends R> f) {
        return value == null ? empty() : of(f.apply(value));
    }

    <R> Maybe<R> flatMap(Function<? super T, Maybe<R>> f) {
        return value == null ? empty() : f.apply(value);
    }

    T orElse(T other) {
        return value == null ? other : value;
    }
}

class Lazy<T> {
    private final Supplier<T> supplier;

    private Lazy(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    static <T> Lazy<T> of(Supplier<T> supplier) {
        return new Lazy<>(supplier);
    }

    <R> Lazy<R> map(Function<? super T, ? extends R> f) {
        return new Lazy<>(() -> f.apply(supplier.get()));
    }

    <R> Lazy<R> flatMap(Function<? super T, Lazy<R>> f) {
        return new Lazy<>(() -> f.apply(supplier.get()).get());
    }

    T get() {
        return supplier.get();
    }
}
```

`Lazy.map` non chiama `f`: costruisce **un nuovo `Lazy`** che chiamerà `f` quando qualcuno farà `get()`.
Questa è l'idea di Mutiny: `Uni.map(...)` descrive un passaggio, la sottoscrizione fa partire la catena.
Nota anche che con `Lazy` ogni `get()` rifà il calcolo da zero (come un `Uni` "freddo").

## ex06

```java
// Ex06.java
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class Ex06 {

    static Supplier<Integer> counter(int start) {
        AtomicInteger state = new AtomicInteger(start);
        return () -> state.getAndIncrement();
    }

    public static void main(String[] args) {
        // int total = 0;
        // List.of(1, 2, 3).forEach(n -> total += n);
        // errore: la variabile usata in una lambda deve essere final o effectively final

        AtomicInteger total = new AtomicInteger();
        List.of(1, 2, 3).forEach(n -> total.addAndGet(n));
        check(total.get() == 6);

        int[] box = {0};
        List.of(1, 2, 3).forEach(n -> box[0] += n);
        check(box[0] == 6);

        Supplier<Integer> c = counter(5);
        check(c.get() == 5);
        check(c.get() == 6);
        check(c.get() == 7);
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

La lambda cattura la **variabile locale**, che vive nello stack e sparirebbe a fine metodo; per
questo Java la copia e ti impone che non cambi mai (altrimenti avresti due valori diversi).
Un array o un `AtomicInteger` sono oggetti sull'heap: la **referenza** è `final`, il contenuto
può cambiare.
