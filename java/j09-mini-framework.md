# Modulo J09: Mini-framework reattivo (progetto finale Java)

Obiettivo: metti insieme lambda (J04), generics (J02), eccezioni (J03) e concorrenza (J07) costruendo
**da te** un mini `Uni`: la classe `Promise<T>`. Alla fine capisci cosa fa `Uni` in `AdminResource`,
perché `flatMap` serve per concatenare query SQL e perché un `Uni` non fa niente finché qualcuno non si "sottoscrive".

Regole: file autonomo `ExNN.java`; ogni esercizio **ripete** la classe `Promise` (la completi
passo per passo). Scheletro della classe, da estendere esercizio dopo esercizio:

```java
import java.util.concurrent.*;
import java.util.function.*;

class Promise<T> {
    private final Supplier<CompletableFuture<T>> source;
    private Promise(Supplier<CompletableFuture<T>> source) { this.source = source; }
    // ...
}
```

L'idea chiave: `Promise` **non contiene un valore**, contiene una *ricetta* (`Supplier`) per ottenere un `CompletableFuture`.
La ricetta viene eseguita solo quando chiami `await()`. È la differenza fra `CompletableFuture` (parte subito) e `Uni` (è pigro).

---

## ex00: `supply`, `map`, `await`

**File:** `Ex00.java`

- `static <T> Promise<T> supply(Supplier<T> s)` (esegue `s` su un altro thread con `CompletableFuture.supplyAsync`)
- `<R> Promise<R> map(Function<? super T, ? extends R> f)`
- `T await()`: aspetta il risultato con `join()`

Se la ricetta lancia un'eccezione, `await()` deve rilanciare **l'eccezione originale** (non una `CompletionException`).
`supply(() -> 20).map(x -> x + 1).map(x -> "n=" + x).await()` → `"n=21"`.

---

## ex01: Pigrizia

**File:** `Ex01.java`

Verifica che nulla venga eseguito prima di `await()`:

```java
AtomicInteger runs = new AtomicInteger();
Promise<Integer> p = Promise.supply(runs::incrementAndGet).map(x -> x * 10);
// qui runs.get() == 0
p.await();  // runs == 1
p.await();  // runs == 2 : ogni await riesegue la ricetta
```

Domanda: come si chiamano nella documentazione Mutiny le "sottoscrizioni" e cosa succede se due
client si sottoscrivono allo stesso `Uni`?

---

## ex02: `flatMap`

**File:** `Ex02.java`

`<R> Promise<R> flatMap(Function<? super T, Promise<R>> f)`: il passo successivo ritorna **a sua volta** una `Promise`.

Simula due query: `findPoolId(String name)` e `findEvents(int poolId)` (entrambe `Promise`, con una piccola pausa).
`findPoolId("Blu").flatMap(Ex02::findEvents).await()` → `List.of("gara", "festa")`.
Con `map` invece di `flatMap` che tipo ottieni? Scrivilo in un commento.

È il pattern di `AdminResource`: `pool.query(...).execute().flatMap(rows -> pool.query(...).execute())`.

---

## ex03: Errori: `failed`, `recover`

**File:** `Ex03.java`

- `static <T> Promise<T> failed(Throwable t)`
- `Promise<T> recover(Function<Throwable, T> f)`: se la catena fallisce, ritorna il valore di `f`
  (all'`f` arriva l'eccezione **vera**, già scartata dalla `CompletionException`)

Verifica che: l'eccezione di un `supply` salti i `map` successivi, che `recover` la catturi, e che un `recover`
su una catena riuscita **non** cambi il risultato.

Mutiny: `onFailure().recoverWithItem(...)`.

---

## ex04: `combine`, in parallelo

**File:** `Ex04.java`

`static <A, B, R> Promise<R> combine(Promise<A> a, Promise<B> b, BiFunction<? super A, ? super B, ? extends R> f)`

Due `supply` che dormono 300 ms l'uno: combinati devono completare in meno di 550 ms (si avviano insieme).
Mutiny: `Uni.combine().all().unis(a, b).asTuple()`.

---

## ex05: Mini applicazione

**File:** `Ex05.java`

Metti tutto insieme in una piccola applicazione a livelli (come `Resource` → `Service` → `Repo`), con `Promise` come "Uni":

- `record Pool(int id, String name)`, `record Response(int status, String body)`
- `PoolRepo`: `insert(String name)` e `findById(int id)` → `Promise<Pool>`; `findById` fallisce con
  `NotFoundException("pool <id>")` se manca (eccezione tua, `RuntimeException`)
- `PoolService`: `create(String name)` → fallisce con `IllegalArgumentException("name")` se vuoto o solo spazi, altrimenti inserisce `name.trim()`;
  `get(int id)` → delega al repo
- `PoolResource`: `post(String name)` → `Promise<Response>` con `201` e body `"<id>:<name>"`; `get(int id)` con `200` e il nome
- mappa gli errori in un'unica funzione `status(Throwable)`: `NotFoundException` → 404, `IllegalArgumentException` → 400, altro → 500
  (è il lavoro di un `ExceptionMapper`)

Test: `post("  Blu ")` → `201 "1:Blu"`, `get(1)` → `200 "Blu"`, `get(99)` → `404 "pool 99"`, `post("   ")` → `400 "name"`.

---

## Verifica

- Sai spiegare perché `Uni` è pigro, e cosa significa "sottoscriversi".
- Sai spiegare `map` contro `flatMap` con un esempio SQL.
- Sai dove, nel tuo `AdminResource`, avviene ognuna delle operazioni di questo modulo.

<br><br><br>

---

# Soluzioni

Guardale solo **dopo** aver provato.

## ex00

```java
// Ex00.java
import java.util.concurrent.*;
import java.util.function.*;

public class Ex00 {

    public static void main(String[] args) {
        String s = Promise.supply(() -> 20).map(x -> x + 1).map(x -> "n=" + x).await();
        check(s.equals("n=21"));

        Promise<Integer> bad = Promise.supply(() -> {
            if (true) throw new IllegalStateException("boom");
            return 1;
        });
        try {
            bad.map(x -> x + 1).await();
            throw new AssertionError();
        } catch (IllegalStateException e) {
            check(e.getMessage().equals("boom"));
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

class Promise<T> {
    private final Supplier<CompletableFuture<T>> source;

    private Promise(Supplier<CompletableFuture<T>> source) {
        this.source = source;
    }

    static <T> Promise<T> supply(Supplier<T> s) {
        return new Promise<>(() -> CompletableFuture.supplyAsync(s));
    }

    <R> Promise<R> map(Function<? super T, ? extends R> f) {
        return new Promise<>(() -> source.get().thenApply(f));
    }

    T await() {
        try {
            return source.get().join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw new RuntimeException(cause);
        }
    }
}
```

`await()` scarta la `CompletionException` perché è un dettaglio interno di `CompletableFuture`: l'utente vuole
l'errore del suo codice.

## ex01

```java
// Ex01.java
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

public class Ex01 {

    public static void main(String[] args) {
        AtomicInteger runs = new AtomicInteger();
        Promise<Integer> p = Promise.supply(runs::incrementAndGet).map(x -> x * 10);
        check(runs.get() == 0);          // nulla e' partito

        check(p.await() == 10);
        check(runs.get() == 1);

        check(p.await() == 20);          // la ricetta e' stata rieseguita
        check(runs.get() == 2);
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

class Promise<T> {
    private final Supplier<CompletableFuture<T>> source;

    private Promise(Supplier<CompletableFuture<T>> source) {
        this.source = source;
    }

    static <T> Promise<T> supply(Supplier<T> s) {
        return new Promise<>(() -> CompletableFuture.supplyAsync(s));
    }

    <R> Promise<R> map(Function<? super T, ? extends R> f) {
        return new Promise<>(() -> source.get().thenApply(f));
    }

    T await() {
        try {
            return source.get().join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw new RuntimeException(cause);
        }
    }
}
```

In Mutiny, `uni.subscribe()...` avvia il calcolo; ogni sottoscrizione riesegue la pipeline (per un `Uni` freddo).
Il punto: costruire la catena non ha effetti collaterali, per questo si può restituire un `Uni` da un metodo e
lasciare che sia il framework (Quarkus REST) a sottoscriverlo quando arriva la richiesta.

## ex02

```java
// Ex02.java
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public class Ex02 {

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    static Promise<Integer> findPoolId(String name) {
        return Promise.supply(() -> {
            sleep(30);
            return name.equals("Blu") ? 7 : -1;
        });
    }

    static Promise<List<String>> findEvents(int poolId) {
        return Promise.supply(() -> {
            sleep(30);
            return poolId == 7 ? List.of("gara", "festa") : List.of();
        });
    }

    public static void main(String[] args) {
        List<String> events = findPoolId("Blu").flatMap(Ex02::findEvents).await();
        check(events.equals(List.of("gara", "festa")));

        check(findPoolId("Altro").flatMap(Ex02::findEvents).await().isEmpty());

        // con map ottieni Promise<Promise<List<String>>>: andrebbe "scartato" a mano
        Promise<Promise<List<String>>> nested = findPoolId("Blu").map(Ex02::findEvents);
        check(nested.await().await().size() == 2);
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

class Promise<T> {
    private final Supplier<CompletableFuture<T>> source;

    private Promise(Supplier<CompletableFuture<T>> source) {
        this.source = source;
    }

    static <T> Promise<T> supply(Supplier<T> s) {
        return new Promise<>(() -> CompletableFuture.supplyAsync(s));
    }

    <R> Promise<R> map(Function<? super T, ? extends R> f) {
        return new Promise<>(() -> source.get().thenApply(f));
    }

    <R> Promise<R> flatMap(Function<? super T, Promise<R>> f) {
        return new Promise<>(() -> source.get().thenCompose(t -> f.apply(t).source.get()));
    }

    T await() {
        try {
            return source.get().join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw new RuntimeException(cause);
        }
    }
}
```

`map(f)`: `f` ritorna un **valore**. `flatMap(f)`: `f` ritorna a sua volta una `Promise` (come una seconda query
che dipende dal risultato della prima) e il risultato viene "appiattito". In `AdminResource` è proprio così
che si passa dall'`INSERT` dell'ospite al successivo `SELECT`.

## ex03

```java
// Ex03.java
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

public class Ex03 {

    public static void main(String[] args) {
        AtomicInteger mapRuns = new AtomicInteger();
        Promise<Integer> failing = Promise.<Integer>supply(() -> {
                    throw new IllegalStateException("boom");
                })
                .map(x -> {
                    mapRuns.incrementAndGet();
                    return x + 1;
                });

        int recovered = failing.recover(ex -> {
            check(ex instanceof IllegalStateException);   // non CompletionException
            return -1;
        }).await();
        check(recovered == -1);
        check(mapRuns.get() == 0);                        // il map e' stato saltato

        check(Promise.supply(() -> 5).recover(ex -> -1).await() == 5);

        int fromFailed = Promise.<Integer>failed(new IllegalArgumentException("x"))
                .recover(ex -> ex.getMessage().length()).await();
        check(fromFailed == 1);
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

class Promise<T> {
    private final Supplier<CompletableFuture<T>> source;

    private Promise(Supplier<CompletableFuture<T>> source) {
        this.source = source;
    }

    static <T> Promise<T> supply(Supplier<T> s) {
        return new Promise<>(() -> CompletableFuture.supplyAsync(s));
    }

    static <T> Promise<T> failed(Throwable t) {
        return new Promise<>(() -> CompletableFuture.failedFuture(t));
    }

    <R> Promise<R> map(Function<? super T, ? extends R> f) {
        return new Promise<>(() -> source.get().thenApply(f));
    }

    <R> Promise<R> flatMap(Function<? super T, Promise<R>> f) {
        return new Promise<>(() -> source.get().thenCompose(t -> f.apply(t).source.get()));
    }

    Promise<T> recover(Function<Throwable, T> f) {
        return new Promise<>(() -> source.get().exceptionally(ex -> f.apply(unwrap(ex))));
    }

    private static Throwable unwrap(Throwable t) {
        return (t instanceof CompletionException && t.getCause() != null) ? t.getCause() : t;
    }

    T await() {
        try {
            return source.get().join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw new RuntimeException(cause);
        }
    }
}
```

Un errore in una catena salta tutti i passaggi successivi finché qualcuno lo gestisce (`recover`):
è lo stesso modello di `.onFailure()` e `ExceptionMapper`.

## ex04

```java
// Ex04.java
import java.util.concurrent.*;
import java.util.function.*;

public class Ex04 {

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    static Promise<String> slow(String v) {
        return Promise.supply(() -> {
            sleep(300);
            return v;
        });
    }

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        String r = Promise.combine(slow("a"), slow("b"), (x, y) -> x + y).await();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        check(r.equals("ab"));
        check(ms >= 290 && ms < 550);
        System.out.println("OK (" + ms + " ms)");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

class Promise<T> {
    private final Supplier<CompletableFuture<T>> source;

    private Promise(Supplier<CompletableFuture<T>> source) {
        this.source = source;
    }

    static <T> Promise<T> supply(Supplier<T> s) {
        return new Promise<>(() -> CompletableFuture.supplyAsync(s));
    }

    static <A, B, R> Promise<R> combine(Promise<A> a, Promise<B> b, BiFunction<? super A, ? super B, ? extends R> f) {
        return new Promise<>(() -> {
            CompletableFuture<A> fa = a.source.get();   // le due partono insieme...
            CompletableFuture<B> fb = b.source.get();
            return fa.thenCombine(fb, f);               // ...e si uniscono qui
        });
    }

    T await() {
        try {
            return source.get().join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw new RuntimeException(cause);
        }
    }
}
```

Con `flatMap(a -> b)` le due attese sarebbero **in sequenza** (600 ms), perché `b` parte solo quando `a` è finita.
`combine` avvia entrambe le ricette prima di attendere.

## ex05

```java
// Ex05.java
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

public class Ex05 {

    public static void main(String[] args) {
        PoolResource res = new PoolResource(new PoolService(new PoolRepo()));

        Response created = res.post("  Blu ").await();
        check(created.status() == 201);
        check(created.body().equals("1:Blu"));

        Response found = res.get(1).await();
        check(found.status() == 200);
        check(found.body().equals("Blu"));

        Response missing = res.get(99).await();
        check(missing.status() == 404);
        check(missing.body().equals("pool 99"));

        Response bad = res.post("   ").await();
        check(bad.status() == 400);
        check(bad.body().equals("name"));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

record Pool(int id, String name) {}

record Response(int status, String body) {}

class NotFoundException extends RuntimeException {
    NotFoundException(String message) {
        super(message);
    }
}

class PoolRepo {
    private final Map<Integer, Pool> data = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger();

    Promise<Pool> insert(String name) {
        return Promise.supply(() -> {
            int id = seq.incrementAndGet();
            Pool p = new Pool(id, name);
            data.put(id, p);
            return p;
        });
    }

    Promise<Pool> findById(int id) {
        return Promise.supply(() -> {
            Pool p = data.get(id);
            if (p == null) throw new NotFoundException("pool " + id);
            return p;
        });
    }
}

class PoolService {
    private final PoolRepo repo;

    PoolService(PoolRepo repo) {
        this.repo = repo;
    }

    Promise<Pool> create(String name) {
        if (name == null || name.isBlank()) {
            return Promise.failed(new IllegalArgumentException("name"));
        }
        return repo.insert(name.trim());
    }

    Promise<Pool> get(int id) {
        return repo.findById(id);
    }
}

class PoolResource {
    private final PoolService service;

    PoolResource(PoolService service) {
        this.service = service;
    }

    Promise<Response> post(String name) {
        return service.create(name)
                .map(p -> new Response(201, p.id() + ":" + p.name()))
                .recover(PoolResource::toResponse);
    }

    Promise<Response> get(int id) {
        return service.get(id)
                .map(p -> new Response(200, p.name()))
                .recover(PoolResource::toResponse);
    }

    // il lavoro di un ExceptionMapper
    static Response toResponse(Throwable ex) {
        if (ex instanceof NotFoundException) return new Response(404, ex.getMessage());
        if (ex instanceof IllegalArgumentException) return new Response(400, ex.getMessage());
        return new Response(500, "errore interno");
    }
}

class Promise<T> {
    private final Supplier<CompletableFuture<T>> source;

    private Promise(Supplier<CompletableFuture<T>> source) {
        this.source = source;
    }

    static <T> Promise<T> supply(Supplier<T> s) {
        return new Promise<>(() -> CompletableFuture.supplyAsync(s));
    }

    static <T> Promise<T> failed(Throwable t) {
        return new Promise<>(() -> CompletableFuture.failedFuture(t));
    }

    <R> Promise<R> map(Function<? super T, ? extends R> f) {
        return new Promise<>(() -> source.get().thenApply(f));
    }

    <R> Promise<R> flatMap(Function<? super T, Promise<R>> f) {
        return new Promise<>(() -> source.get().thenCompose(t -> f.apply(t).source.get()));
    }

    Promise<T> recover(Function<Throwable, T> f) {
        return new Promise<>(() -> source.get().exceptionally(ex -> f.apply(unwrap(ex))));
    }

    private static Throwable unwrap(Throwable t) {
        return (t instanceof CompletionException && t.getCause() != null) ? t.getCause() : t;
    }

    T await() {
        try {
            return source.get().join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw new RuntimeException(cause);
        }
    }
}
```

Nota il tipo: `recover(PoolResource::toResponse)` funziona perché `toResponse` ha la firma
`Throwable -> Response`, cioè `Function<Throwable, Response>`.
