# Modulo J07: Concorrenza

Obiettivo: thread, executor, `CompletableFuture`, e soprattutto capire perché **bloccare** il thread
sbagliato rallenta tutto. È la base di `Uni`, di `@Blocking` e dell'event loop di Vert.x.

Regole del modulo: solo `java.util.concurrent`. Gli esercizi con i tempi usano margini larghi
(sleep da 300 ms): se un controllo temporale fallisce, rilancialo prima di cercare un bug.

Helper utili (mettili in ogni file che li usa):

```java
static void sleep(long ms) {
    try { Thread.sleep(ms); }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
}
static long ms(long startNanos) { return (System.nanoTime() - startNanos) / 1_000_000; }
```

---

## ex00: Thread e race condition

**File:** `Ex00.java`

Due thread incrementano 100.000 volte ciascuno lo stesso contatore.

1. con un `static int` normale: stampa il risultato (di solito **meno** di 200.000: perché?)
2. con `synchronized`: deve essere esattamente 200.000
3. con `AtomicInteger`: deve essere esattamente 200.000

Da usare: `new Thread(runnable)`, `start()`, `join()`.
Perché `counter++` non è un'operazione "atomica"?

---

## ex01: ExecutorService

**File:** `Ex01.java`

Con un pool fisso da 4 thread sottometti 10 `Callable<Integer>` che calcolano `n * n` (per `n` da 1 a 10)
e somma i risultati dei `Future` (deve fare 385).
Poi sottometti un `Callable` che lancia `IllegalStateException` e verifica che `future.get()` lanci
`ExecutionException` con la causa originale.

Usa `try-with-resources` sull'`ExecutorService` (da Java 19 è `AutoCloseable`).

---

## ex02: CompletableFuture

**File:** `Ex02.java`

1. `supplyAsync(() -> "pool")` → `thenApply(String::toUpperCase)` → `thenCompose(s -> supplyAsync(() -> s + "!"))`
   deve produrre `"POOL!"` (`join()` per aspettare)
2. un `supplyAsync` che lancia `IllegalStateException("boom")` seguito da `exceptionally(...)` che
   ritorna `"default:boom"`

Attenzione: nell'`exceptionally` l'eccezione che arriva può essere avvolta in `CompletionException`:
recupera la causa vera.

Corrispondenza con Mutiny: `thenApply` ≈ `onItem().transform`, `thenCompose` ≈ `onItem().transformToUni`,
`exceptionally` ≈ `onFailure().recoverWithItem`.

---

## ex03: Operazioni in parallelo

**File:** `Ex03.java`

`slow(String name)` aspetta 300 ms e ritorna `name`.

1. **sequenziale**: `slow("a") + slow("b") + slow("c")` richiede almeno 900 ms
2. **parallelo**: tre `supplyAsync(..., pool)` su un pool da 3 thread, combinati con `thenCombine`,
   richiedono meno di 800 ms e danno `"abc"`

Corrispondenza con Mutiny: `Uni.combine().all().unis(...)`.

---

## ex04: Simulare l'event loop

**File:** `Ex04.java`

Un **event loop** è un solo thread che serve tutte le richieste. Simulalo con
`Executors.newSingleThreadExecutor()`.

1. Sottometti 3 task che fanno `sleep(300)` **direttamente sul loop**, poi misura la latenza di un task
   vuoto sottomesso subito dopo (`loop.submit(() -> {}).get()`): sarà almeno ~800 ms
2. Rifai tutto ma, dentro i task del loop, **delega** lo `sleep` a un pool di worker
   (`workers.submit(() -> sleep(300))`): la latenza del task vuoto scende sotto i 250 ms

È esattamente la differenza tra bloccare l'event loop e usare `@Blocking` (m03/ex04 del modulo Quarkus).

---

## ex05: Virtual thread

**File:** `Ex05.java`

1. Avvia un thread virtuale con `Thread.ofVirtual().start(...)` e verifica `isVirtual()`
2. Con `Executors.newVirtualThreadPerTaskExecutor()` lancia **10.000** task che fanno `sleep(100)`
   e incrementano un `AtomicInteger`: alla fine il contatore vale 10.000 e il tutto è finito in meno di 3 secondi

Con 10.000 thread normali del sistema operativo succederebbe lo stesso? Perché i virtual thread
sono così economici quando aspettano?

---

## ex06: Un archivio thread-safe

**File:** `Ex06.java`

`PoolStore` con `ConcurrentHashMap<Integer, String>` e `AtomicInteger` per gli id:
`create(name)`, `get(id)` (ritorna `Optional`), `delete(id)`, `size()`.
8 thread creano 1000 piscine ciascuno: alla fine `size() == 8000` e gli **id sono tutti distinti**.

È la versione corretta dello stato condiviso del modulo Quarkus m00/ex05 (una resource è condivisa tra richieste).

---

## Verifica

- Sai spiegare race condition, `synchronized` e variabili atomiche.
- Sai spiegare perché bloccare l'event loop ferma *tutte* le richieste e come si evita.
- Sai dire la differenza tra `thenApply` e `thenCompose`.

<br><br><br>

---

# Soluzioni

Guardale solo **dopo** aver provato.

## ex00

```java
// Ex00.java
import java.util.concurrent.atomic.AtomicInteger;

public class Ex00 {

    static int plain = 0;
    static int guarded = 0;
    static final Object LOCK = new Object();
    static final AtomicInteger atomic = new AtomicInteger();

    static void runTwo(Runnable task) throws InterruptedException {
        Thread a = new Thread(task);
        Thread b = new Thread(task);
        a.start();
        b.start();
        a.join();
        b.join();
    }

    public static void main(String[] args) throws Exception {
        runTwo(() -> {
            for (int i = 0; i < 100_000; i++) plain++;
        });
        System.out.println("plain = " + plain + " (di solito < 200000)");

        runTwo(() -> {
            for (int i = 0; i < 100_000; i++) {
                synchronized (LOCK) {
                    guarded++;
                }
            }
        });
        check(guarded == 200_000);

        runTwo(() -> {
            for (int i = 0; i < 100_000; i++) atomic.incrementAndGet();
        });
        check(atomic.get() == 200_000);
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

`counter++` sono tre passi (leggi, somma, scrivi): due thread possono leggere lo stesso valore e
scrivere entrambi `valore + 1`, perdendo un incremento.

## ex01

```java
// Ex01.java
import java.util.*;
import java.util.concurrent.*;

public class Ex01 {

    public static void main(String[] args) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int n = 1; n <= 10; n++) {
                final int x = n;
                futures.add(pool.submit(() -> x * x));
            }
            int sum = 0;
            for (Future<Integer> f : futures) {
                sum += f.get();
            }
            check(sum == 385);

            Callable<Integer> failing = () -> {
                throw new IllegalStateException("boom");
            };
            Future<Integer> bad = pool.submit(failing);
            try {
                bad.get();
                throw new AssertionError();
            } catch (ExecutionException e) {
                check(e.getCause() instanceof IllegalStateException);
                check(e.getCause().getMessage().equals("boom"));
            }
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

`Future.get()` blocca il thread chiamante finché il risultato non c'è: va bene qui in `main`, ma
è proprio la cosa che **non** devi fare sull'event loop.

## ex02

```java
// Ex02.java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class Ex02 {

    static Throwable unwrap(Throwable t) {
        return (t instanceof CompletionException && t.getCause() != null) ? t.getCause() : t;
    }

    public static void main(String[] args) {
        CompletableFuture<String> chain = CompletableFuture.supplyAsync(() -> "pool")
                .thenApply(String::toUpperCase)
                .thenCompose(s -> CompletableFuture.supplyAsync(() -> s + "!"));
        check(chain.join().equals("POOL!"));

        CompletableFuture<String> failing = CompletableFuture.<String>supplyAsync(() -> {
                    throw new IllegalStateException("boom");
                })
                .exceptionally(ex -> "default:" + unwrap(ex).getMessage());
        check(failing.join().equals("default:boom"));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

`thenApply(f)`: `f` ritorna un valore. `thenCompose(f)`: `f` ritorna un altro `CompletableFuture`,
che viene "appiattito" (come `flatMap`). Un errore dentro un passaggio viene incapsulato in
`CompletionException`.

## ex03

```java
// Ex03.java
import java.util.concurrent.*;

public class Ex03 {

    static String slow(String name) {
        sleep(300);
        return name;
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        String sequential = slow("a") + slow("b") + slow("c");
        long seqMs = ms(t0);
        check(sequential.equals("abc"));
        check(seqMs >= 890);

        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            long t1 = System.nanoTime();
            CompletableFuture<String> a = CompletableFuture.supplyAsync(() -> slow("a"), pool);
            CompletableFuture<String> b = CompletableFuture.supplyAsync(() -> slow("b"), pool);
            CompletableFuture<String> c = CompletableFuture.supplyAsync(() -> slow("c"), pool);
            String parallel = a.thenCombine(b, String::concat).thenCombine(c, String::concat).join();
            long parMs = ms(t1);
            check(parallel.equals("abc"));
            check(parMs < 800);
            CompletableFuture.allOf(a, b, c).join();
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

Le tre attese si sovrappongono: il tempo totale è quello della più lenta, non la somma.

## ex04

```java
// Ex04.java
import java.util.concurrent.*;

public class Ex04 {

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    static long latencyBlocking() throws Exception {
        ExecutorService loop = Executors.newSingleThreadExecutor();
        try {
            for (int i = 0; i < 3; i++) {
                loop.submit(() -> sleep(300));
            }
            long t0 = System.nanoTime();
            loop.submit(() -> {}).get();
            return ms(t0);
        } finally {
            loop.shutdownNow();
        }
    }

    static long latencyOffloaded() throws Exception {
        ExecutorService loop = Executors.newSingleThreadExecutor();
        ExecutorService workers = Executors.newFixedThreadPool(3);
        try {
            for (int i = 0; i < 3; i++) {
                loop.submit(() -> {
                    workers.submit(() -> sleep(300));
                });
            }
            long t0 = System.nanoTime();
            loop.submit(() -> {}).get();
            return ms(t0);
        } finally {
            loop.shutdownNow();
            workers.shutdownNow();
        }
    }

    public static void main(String[] args) throws Exception {
        long blocked = latencyBlocking();
        long offloaded = latencyOffloaded();
        System.out.println("bloccante: " + blocked + " ms, delegato: " + offloaded + " ms");
        check(blocked >= 800);
        check(offloaded < 250);
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

Nel primo caso il task "vuoto" resta in coda dietro tre attese da 300 ms: l'event loop non può
servirlo. Nel secondo il loop si limita a **passare il lavoro** ai worker e resta libero.
Con Quarkus REST lo stesso effetto si ottiene con `@Blocking` (o evitando del tutto il codice bloccante).

## ex05

```java
// Ex05.java
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Ex05 {

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        Thread t = Thread.ofVirtual().start(() -> sleep(10));
        t.join();
        check(t.isVirtual());

        AtomicInteger done = new AtomicInteger();
        long t0 = System.nanoTime();
        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 10_000; i++) {
                ex.submit(() -> {
                    sleep(100);
                    done.incrementAndGet();
                });
            }
        }
        long elapsed = (System.nanoTime() - t0) / 1_000_000;
        check(done.get() == 10_000);
        check(elapsed < 3000);
        System.out.println("OK (" + elapsed + " ms)");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

Un thread del sistema operativo costa un'intera pila (circa 1 MB) e 10.000 di essi sono pesanti.
Un virtual thread è un oggetto gestito dalla JVM: quando fa `sleep` o aspetta I/O viene "parcheggiato" e
il thread vero sotto di lui serve altri task.

## ex06

```java
// Ex06.java
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Ex06 {

    public static void main(String[] args) {
        PoolStore store = new PoolStore();
        Set<Integer> ids = ConcurrentHashMap.newKeySet();

        try (ExecutorService ex = Executors.newFixedThreadPool(8)) {
            for (int t = 0; t < 8; t++) {
                ex.submit(() -> {
                    for (int i = 0; i < 1000; i++) {
                        ids.add(store.create("p" + i));
                    }
                });
            }
        }

        check(store.size() == 8000);
        check(ids.size() == 8000);
        int any = ids.iterator().next();
        check(store.get(any).isPresent());
        check(store.delete(any));
        check(!store.delete(any));
        check(store.get(any).isEmpty());
        check(store.size() == 7999);
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

class PoolStore {
    private final Map<Integer, String> pools = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger();

    int create(String name) {
        int id = seq.incrementAndGet();
        pools.put(id, name);
        return id;
    }

    Optional<String> get(int id) {
        return Optional.ofNullable(pools.get(id));
    }

    boolean delete(int id) {
        return pools.remove(id) != null;
    }

    int size() {
        return pools.size();
    }
}
```

Con una `HashMap` normale e un `int` per gli id, otterresti id duplicati e inserimenti persi.
`ConcurrentHashMap` e `AtomicInteger` rendono ogni singola operazione sicura senza lock espliciti.
