# Modulo 03: Mutiny

Obiettivo: comporre operazioni asincrone senza bloccare.
Gli esercizi usano il service di m02.

Prima di iniziare leggi: smallrye.io/smallrye-mutiny (sezioni `Uni` e "Handling failures").

---

## ex00: map

**File:** `UniLab.java` + `GET /lab/map/{id}`

Prendi un `Pool` dal DB e ritorna solo il nome in maiuscolo: `Uni<String>`.
Usa `onItem().transform(...)` (o `map`). Spiega perche' qui non serve `flatMap`.

---

## ex01: flatMap in sequenza

`POST /pools/{id}/clone`: leggi la piscina, poi inserisci una copia con
nome `<nome> (copia)`.

Prima operazione: SELECT. Seconda: INSERT. La seconda dipende dal risultato della prima:
questo e' il caso del `flatMap` (`onItem().transformToUni`).
Se la piscina non esiste: 404 e nessun INSERT.

---

## ex02: Il fallimento e' un valore

**File:** `UniLab.java`

Scrivi un endpoint che lancia una `CustomException` in tre posti diversi:
1. prima della catena `Uni`
2. dentro un `map`
3. dentro un `flatMap`

Per ognuno scrivi nel commento cosa arriva al client e perche' e' uguale (o diverso).
Poi usa `onFailure().recoverWithItem(...)` per rispondere con un valore di default
e `onFailure().transform(...)` per convertire un'eccezione in un'altra.

---

## ex03: Operazioni in parallelo

`GET /stats` ritorna un JSON con:
- numero totale di piscine
- numero di piscine aperte
- capienza media

Tre query indipendenti, lanciate **insieme** con:

```java
Uni.combine().all().unis(u1, u2, u3).asTuple()
```

Confronta con la versione sequenziale a `flatMap`: quando e' meglio una e quando l'altra?

---

## ex04: Mai bloccare

**File:** `BlockingLab.java`

1. Crea un endpoint che ritorna `Uni<String>` e dentro fa `Thread.sleep(3000)`.
   Chiamalo 5 volte in parallelo con `curl`. Guarda i log: su quale thread gira?
2. Aggiungi `@Blocking` e riprova.
3. Riscrivilo senza bloccare con `Uni.createFrom().item("x").onItem().delayIt().by(Duration.ofSeconds(3))`.

Scrivi nel commento quale thread (`vert.x-eventloop-*` o `executor-thread-*`) vedi e cosa significa.

---

## ex05: Validare dentro la catena

Riscrivi `POST /pools` in modo che:
1. valida il body (nome non vuoto, capienza >= 0)
2. controlla che non esista gia' una piscina con stesso nome e citta' (SELECT)
3. solo allora fa l'INSERT

Tutto in un'unica catena `Uni`, senza `if` fuori dalla catena che chiamano il DB.

---

## Verifica

- Sai disegnare su carta la catena dell'ex01 con i tipi di ogni passaggio.
- Sai dire la differenza tra `transform` (valore) e `transformToUni` (un altro `Uni`).
- Nessun `await()` nel codice degli endpoint.

<br><br><br>

---

# Soluzioni

Guardale solo **dopo** aver provato.

## ex00

```java
@GET
@Path("/lab/map/{id}")
public Uni<String> upper(@PathParam("id") Integer id) {
    return service.get(id).onItem().transform(p -> p.name.toUpperCase());
}
```

`transform` riceve un valore e ne ritorna un altro: nessun `Uni` annidato, quindi niente `flatMap`.

## ex01

```java
public Uni<Pool> clone(Integer id) {
    return get(id) // se non esiste fallisce e la catena si ferma qui
            .onItem().transformToUni(p -> {
                Pool copy = new Pool();
                copy.name = p.name + " (copia)";
                copy.city = p.city;
                copy.capacity = p.capacity;
                return repo.insert(copy); // Uni<Pool>
            });
}
```

Tipi: `Uni<Pool>` (SELECT) -> `transformToUni` -> `Uni<Pool>` (INSERT). Con `transform` avresti
ottenuto `Uni<Uni<Pool>>`.

## ex02

```java
// 1) prima della catena
if (id < 0) throw new CustomException("error.negative", "id negativo");

// 2) dentro un map
service.get(id).map(p -> { throw new CustomException("error.in_map", "fallito"); });

// 3) dentro un flatMap
service.get(id).flatMap(p -> Uni.createFrom().failure(new CustomException("error.in_flatmap", "fallito")));
```

Al client arriva sempre lo stesso JSON 400: un'eccezione lanciata dentro una lambda
diventa il **fallimento dell'Uni**, e Quarkus REST la passa al mapper come
farebbe con una eccezione sincrona. La differenza e' interna: nel caso 1 l'`Uni` non
viene nemmeno creato.

Recupero:
```java
service.get(id)
    .onFailure(NotFoundBusinessException.class).recoverWithItem(e -> new Pool())
    .onFailure(IllegalStateException.class)
        .transform(e -> new CustomException("error.state", e.getMessage()));
```

## ex03

```java
public Uni<Stats> stats() {
    Uni<Long> total = scalar("SELECT count(*) AS v FROM pool WHERE deleted_at IS NULL",
            r -> r.getLong("v"));
    Uni<Long> open = scalar("SELECT count(*) AS v FROM pool WHERE deleted_at IS NULL AND open",
            r -> r.getLong("v"));
    Uni<Double> avg = scalar("SELECT COALESCE(avg(capacity), 0)::float8 AS v FROM pool WHERE deleted_at IS NULL",
            r -> r.getDouble("v"));

    return Uni.combine().all().unis(total, open, avg).asTuple()
            .map(t -> new Stats(t.getItem1(), t.getItem2(), t.getItem3()));
}

private <T> Uni<T> scalar(String sql, Function<Row, T> read) {
    return client.query(sql).execute().map(rows -> read.apply(rows.iterator().next()));
}

public record Stats(long total, long open, double avgCapacity) {}
```

In parallelo quando le query sono **indipendenti** (qui vanno su connessioni diverse del pool).
Sequenziale con `flatMap` quando la seconda dipende dal risultato della prima, o quando
devono stare nella stessa transazione.

## ex04

```java
// 1) blocca l'event loop: le 5 richieste partono una dopo l'altra (3s, 6s, 9s...)
@GET @Path("/blocking-bad")
public Uni<String> bad() throws InterruptedException {
    Thread.sleep(3000);
    return Uni.createFrom().item("fatto");
}

// 2) su un worker thread: le richieste girano in parallelo
@GET @Path("/blocking-ok")
@Blocking
public Uni<String> ok() throws InterruptedException {
    Thread.sleep(3000);
    return Uni.createFrom().item("fatto");
}

// 3) non bloccante, nessun thread occupato durante l'attesa
@GET @Path("/non-blocking")
public Uni<String> nonBlocking() {
    return Uni.createFrom().item("fatto")
            .onItem().delayIt().by(Duration.ofSeconds(3));
}
```

Nei log: `vert.x-eventloop-thread-N` per 1 e 3, `executor-thread-N` per 2. L'event loop ha pochissimi
thread (di solito 2 x core): se ne blocchi uno, rallenti tutte le richieste che ci passano.

## ex05

```java
public Uni<Pool> create(Pool p) {
    return Uni.createFrom().item(p)
            .onItem().invoke(this::validate) // lancia CustomException se non valido
            .onItem().transformToUni(x -> repo.existsByNameAndCity(p.name, p.city))
            .onItem().transformToUni(exists -> exists
                    ? Uni.createFrom().<Pool>failure(
                            new CustomException("error.duplicate_pool", "Piscina gia' presente"))
                    : repo.insert(p));
}

private void validate(Pool p) {
    if (p.name == null || p.name.isBlank() || p.capacity < 0) {
        throw new CustomException("error.invalid_pool", "Dati non validi");
    }
}
```

```java
public Uni<Boolean> existsByNameAndCity(String name, String city) {
    return client.preparedQuery(
                    "SELECT 1 FROM pool WHERE name = $1 AND city = $2 AND deleted_at IS NULL")
            .execute(Tuple.of(name, city))
            .map(rows -> rows.iterator().hasNext());
}
```
