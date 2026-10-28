# Modulo J02: Collections, generics, Optional

Obiettivo: `List`, `Map`, `Set`, `Optional`, generics e `Iterable`.
È il vocabolario dei service: `Uni<List<Pool>>`, `rows.iterator()`, `onItem().ifNull().failWith(...)`.

Regole del modulo: niente stream (arrivano in J04): usa cicli e i metodi delle collection.
Per i dati di prova usa questo record (mettilo in ogni file che lo richiede):

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

## ex00: List

**File:** `Ex00.java`

- `static List<Pool> filterByCity(List<Pool> pools, String city)`: ignora maiuscole/minuscole
- `static List<Pool> sortByCapacityDesc(List<Pool> pools)`: **non modifica** la lista in ingresso

`filterByCity(pools, "roma")` → id `[1, 3, 5]`; `sortByCapacityDesc(pools)` → id `[1, 2, 3, 4, 5]`
(a parità di capienza resta l'ordine originale: l'ordinamento è stabile).

Trappola: `List.of(...)` è **immutabile**. Prova a farne `sort(...)` direttamente e leggi
l'eccezione. Poi copia la lista in un `ArrayList` e ordina quella.
Da usare: `Comparator.comparingInt(...)` e `.reversed()`.

---

## ex01: Map

**File:** `Ex01.java`

- `static Map<String, Integer> countByCity(List<Pool> pools)` con `Map.merge`
- `static Map<String, List<Pool>> groupByCity(List<Pool> pools)` con `computeIfAbsent`

Usa una `TreeMap` così l'ordine delle chiavi è prevedibile.
`countByCity(pools)` deve valere `{Milano=1, Roma=3, Torino=1}`.
Perché `map.get(...)` può ritornare `null` e come lo eviti (`getOrDefault`)?

---

## ex02: Set

**File:** `Ex02.java`

- `static Set<String> uniqueEmails(List<String> emails)`: toglie spazi ai bordi, converte in minuscolo
  ed elimina i doppioni **mantenendo l'ordine di inserimento**
- `static boolean hasDuplicates(List<String> emails)` sfruttando il valore ritornato da `Set.add`

`uniqueEmails(List.of("A@x.it", "a@x.it ", " B@x.it"))` → `["a@x.it", "b@x.it"]`.
Differenza tra `HashSet`, `LinkedHashSet` e `TreeSet`: quando usi quale?

---

## ex03: Optional

**File:** `Ex03.java`

- `static Optional<Pool> findById(List<Pool> pools, int id)`
- `static Pool getOrThrow(List<Pool> pools, int id)`: se manca lancia `NotFoundException("Piscina <id>")`
- `static String cityOf(List<Pool> pools, int id)`: la città, oppure `"sconosciuta"`

Vietato `Optional.get()` e vietato `isPresent()` seguito da `get()`: usa `orElseThrow`,
`map`, `orElse`. È lo stesso schema di `onItem().ifNull().failWith(...)` del modulo Quarkus m02.

---

## ex04: Generics

**File:** `Ex04.java`

- `class Box<T>` con `T get()` e `<R> Box<R> map(Function<T, R> f)`
- `static <T> List<T> filter(List<T> in, Predicate<T> test)`
- `static <T extends Comparable<T>> T maxOf(List<T> list)` (lista vuota → `NoSuchElementException`)

```java
new Box<>("abc").map(String::length).get()    // 3
maxOf(List.of(3, 9, 2))                       // 9
maxOf(List.of("a", "zz", "b"))                // "zz"
```

`Box` è un contenitore con un solo valore: lo riscriverai in J04 come `Maybe` e `Lazy`.
Domanda: cosa significa `T extends Comparable<T>`?

---

## ex05: Iterable

**File:** `Ex05.java`

Scrivi `record Row(int id, String name)` e `class RowSet implements Iterable<Row>` costruita con
`new RowSet(new Row(1, "a"), new Row(2, "b"))`. Implementa **a mano** l'`Iterator<Row>`
(`hasNext`, `next`; se non ci sono più elementi `next` lancia `NoSuchElementException`).

Verifica che funzioni nel ciclo `for (Row r : rows)`. È esattamente il motivo per cui nel progetto
reale puoi scrivere `for (Row r : rows)` su un `RowSet` di PgPool.

---

## Verifica

- Sai spiegare la differenza tra `ArrayList` e `LinkedList`, e tra `HashMap` e `TreeMap`.
- Sai dire perché `Map.of(...)` e `List.of(...)` non accettano `null` e sono immutabili.
- Sai spiegare cosa fa il compilatore con `for (X x : collezione)`.

<br><br><br>

---

# Soluzioni

Guardale solo **dopo** aver provato.

## ex00

```java
// Ex00.java
import java.util.*;

public class Ex00 {

    static List<Pool> filterByCity(List<Pool> pools, String city) {
        List<Pool> out = new ArrayList<>();
        for (Pool p : pools) {
            if (p.city().equalsIgnoreCase(city)) out.add(p);
        }
        return out;
    }

    static List<Pool> sortByCapacityDesc(List<Pool> pools) {
        List<Pool> copy = new ArrayList<>(pools);
        copy.sort(Comparator.comparingInt(Pool::capacity).reversed());
        return copy;
    }

    static List<Integer> ids(List<Pool> pools) {
        List<Integer> out = new ArrayList<>();
        for (Pool p : pools) out.add(p.id());
        return out;
    }

    public static void main(String[] args) {
        List<Pool> pools = List.of(
                new Pool(1, "Acquacenter", "Roma", 120, true),
                new Pool(2, "Blu Club", "Milano", 80, true),
                new Pool(3, "Delfino", "Roma", 60, false),
                new Pool(4, "Nettuno", "Torino", 60, true),
                new Pool(5, "Zeta", "Roma", 40, true));

        check(ids(filterByCity(pools, "roma")).equals(List.of(1, 3, 5)));
        check(ids(sortByCapacityDesc(pools)).equals(List.of(1, 2, 3, 4, 5)));
        check(ids(pools).equals(List.of(1, 2, 3, 4, 5)));   // l'originale non e' cambiata
        try {
            pools.sort(Comparator.comparingInt(Pool::capacity));
            throw new AssertionError();
        } catch (UnsupportedOperationException expected) {
            // List.of e' immutabile
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

record Pool(int id, String name, String city, int capacity, boolean open) {}
```

## ex01

```java
// Ex01.java
import java.util.*;

public class Ex01 {

    static Map<String, Integer> countByCity(List<Pool> pools) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Pool p : pools) {
            counts.merge(p.city(), 1, Integer::sum);
        }
        return counts;
    }

    static Map<String, List<Pool>> groupByCity(List<Pool> pools) {
        Map<String, List<Pool>> groups = new TreeMap<>();
        for (Pool p : pools) {
            groups.computeIfAbsent(p.city(), k -> new ArrayList<>()).add(p);
        }
        return groups;
    }

    public static void main(String[] args) {
        List<Pool> pools = List.of(
                new Pool(1, "Acquacenter", "Roma", 120, true),
                new Pool(2, "Blu Club", "Milano", 80, true),
                new Pool(3, "Delfino", "Roma", 60, false),
                new Pool(4, "Nettuno", "Torino", 60, true),
                new Pool(5, "Zeta", "Roma", 40, true));

        check(countByCity(pools).equals(Map.of("Milano", 1, "Roma", 3, "Torino", 1)));
        Map<String, List<Pool>> groups = groupByCity(pools);
        check(groups.get("Roma").size() == 3);
        check(groups.get("Napoli") == null);
        check(groups.getOrDefault("Napoli", List.of()).isEmpty());
        check(new ArrayList<>(groups.keySet()).equals(List.of("Milano", "Roma", "Torino")));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

record Pool(int id, String name, String city, int capacity, boolean open) {}
```

`merge(key, 1, Integer::sum)`: se la chiave manca inserisce `1`, altrimenti somma il vecchio valore
e `1`. `get` ritorna `null` per una chiave assente: `getOrDefault` evita il controllo.

## ex02

```java
// Ex02.java
import java.util.*;

public class Ex02 {

    static Set<String> uniqueEmails(List<String> emails) {
        Set<String> unique = new LinkedHashSet<>();
        for (String e : emails) {
            unique.add(e.trim().toLowerCase(Locale.ROOT));
        }
        return unique;
    }

    static boolean hasDuplicates(List<String> emails) {
        Set<String> seen = new HashSet<>();
        for (String e : emails) {
            if (!seen.add(e.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public static void main(String[] args) {
        check(new ArrayList<>(uniqueEmails(List.of("A@x.it", "a@x.it ", " B@x.it")))
                .equals(List.of("a@x.it", "b@x.it")));
        check(hasDuplicates(List.of("a@x.it", "A@X.IT")));
        check(!hasDuplicates(List.of("a@x.it", "b@x.it")));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

`HashSet`: veloce, nessun ordine. `LinkedHashSet`: veloce e ricorda l'ordine di inserimento.
`TreeSet`: ordinato per valore, un po' più lento. `Set.add` ritorna `false` se l'elemento c'era già.

## ex03

```java
// Ex03.java
import java.util.*;

public class Ex03 {

    static Optional<Pool> findById(List<Pool> pools, int id) {
        for (Pool p : pools) {
            if (p.id() == id) return Optional.of(p);
        }
        return Optional.empty();
    }

    static Pool getOrThrow(List<Pool> pools, int id) {
        return findById(pools, id).orElseThrow(() -> new NotFoundException("Piscina " + id));
    }

    static String cityOf(List<Pool> pools, int id) {
        return findById(pools, id).map(Pool::city).orElse("sconosciuta");
    }

    public static void main(String[] args) {
        List<Pool> pools = List.of(
                new Pool(1, "Acquacenter", "Roma", 120, true),
                new Pool(2, "Blu Club", "Milano", 80, true));

        check(findById(pools, 2).isPresent());
        check(findById(pools, 99).isEmpty());
        check(getOrThrow(pools, 1).name().equals("Acquacenter"));
        check(cityOf(pools, 2).equals("Milano"));
        check(cityOf(pools, 99).equals("sconosciuta"));
        try {
            getOrThrow(pools, 99);
            throw new AssertionError();
        } catch (NotFoundException e) {
            check(e.getMessage().equals("Piscina 99"));
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

record Pool(int id, String name, String city, int capacity, boolean open) {}

class NotFoundException extends RuntimeException {
    NotFoundException(String message) {
        super(message);
    }
}
```

`orElseThrow(Supplier)` riceve una lambda: l'eccezione viene creata solo se serve davvero.

## ex04

```java
// Ex04.java
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class Ex04 {

    static <T> List<T> filter(List<T> in, Predicate<T> test) {
        List<T> out = new ArrayList<>();
        for (T item : in) {
            if (test.test(item)) out.add(item);
        }
        return out;
    }

    static <T extends Comparable<T>> T maxOf(List<T> list) {
        if (list.isEmpty()) throw new NoSuchElementException("lista vuota");
        T best = list.get(0);
        for (T item : list) {
            if (item.compareTo(best) > 0) best = item;
        }
        return best;
    }

    public static void main(String[] args) {
        check(new Box<>("abc").map(String::length).get() == 3);
        check(maxOf(List.of(3, 9, 2)) == 9);
        check(maxOf(List.of("a", "zz", "b")).equals("zz"));
        check(filter(List.of(1, 2, 3, 4), n -> n % 2 == 0).equals(List.of(2, 4)));
        try {
            maxOf(new ArrayList<Integer>());
            throw new AssertionError();
        } catch (NoSuchElementException expected) {
            // ok
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }

    <R> Box<R> map(Function<T, R> f) {
        return new Box<>(f.apply(value));
    }
}
```

`T extends Comparable<T>` significa: "T può essere qualunque tipo che sa confrontarsi con
altri T" (cioè ha `compareTo`). Senza questo vincolo non potresti chiamare `compareTo` su `item`.

## ex05

```java
// Ex05.java
import java.util.Iterator;
import java.util.NoSuchElementException;

public class Ex05 {

    public static void main(String[] args) {
        RowSet rows = new RowSet(new Row(1, "a"), new Row(2, "b"), new Row(3, "c"));
        int sum = 0;
        StringBuilder names = new StringBuilder();
        for (Row r : rows) {
            sum += r.id();
            names.append(r.name());
        }
        check(sum == 6);
        check(names.toString().equals("abc"));
        check(rows.size() == 3);

        Iterator<Row> it = rows.iterator();
        it.next();
        it.next();
        it.next();
        check(!it.hasNext());
        try {
            it.next();
            throw new AssertionError();
        } catch (NoSuchElementException expected) {
            // ok
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

record Row(int id, String name) {}

class RowSet implements Iterable<Row> {
    private final Row[] rows;

    RowSet(Row... rows) {
        this.rows = rows;
    }

    int size() {
        return rows.length;
    }

    @Override
    public Iterator<Row> iterator() {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < rows.length;
            }

            @Override
            public Row next() {
                if (!hasNext()) throw new NoSuchElementException();
                return rows[index++];
            }
        };
    }
}
```

Il compilatore trasforma `for (Row r : rows)` in: `Iterator<Row> it = rows.iterator(); while (it.hasNext()) { Row r = it.next(); ... }`.
Per questo basta implementare `Iterable` per poter usare il `for` esteso.
