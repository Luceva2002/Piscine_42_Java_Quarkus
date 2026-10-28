# Modulo 04: Transazioni

Obiettivo: operazioni composte che riescono tutte o nessuna.

Migrazione preliminare `V4__booking.sql`: tabella `booking`
(`id` serial PK, `pool_id` int FK su `pool`, `email` text, `created_at` timestamptz default now())
e colonna `pool.booked` (int, default 0).

---

## ex00: Il problema

**File:** `BookingService.java`

`POST /pools/{id}/book` con body `{"email": "..."}`:
1. INSERT in `booking`
2. UPDATE `pool SET booked = booked + 1`

Scrivilo **senza** transazione, due query separate. Poi fai fallire apposta
la seconda (nome colonna sbagliato) e guarda lo stato del DB. Cos'e' rimasto a meta'?

---

## ex01: withTransaction

Riscrivi l'ex00 con:

```java
client.withTransaction(conn ->
    conn.preparedQuery("INSERT ...").execute(Tuple.of(...))
        .flatMap(r -> conn.preparedQuery("UPDATE ...").execute(Tuple.of(...)))
);
```

Ripeti il test del fallimento: ora il DB non deve avere la riga in `booking`.
Nota: dentro la transazione si usa `conn`, **non** `client`.

---

## ex02: Capienza

Prima di prenotare controlla `booked < capacity`. Se la piscina e' piena: 409.
Il controllo e l'INSERT stanno nella stessa transazione.

Domanda: cosa succede se due richieste arrivano nello stesso istante sull'ultimo posto?
Prova a riprodurlo con due `curl` in parallelo (`&`).
Risolvilo con `SELECT ... FOR UPDATE` oppure con una UPDATE condizionale
(`UPDATE pool SET booked = booked + 1 WHERE id = $1 AND booked < capacity`) e controllando `rowCount()`.

---

## ex03: Cancellazione di prenotazione

`DELETE /bookings/{id}`: cancella la prenotazione **e** decrementa `booked`
sulla piscina giusta, in una transazione. Serve una query di lettura iniziale per
sapere a quale piscina appartiene (`DELETE ... RETURNING pool_id`).

---

## Verifica

- Sai spiegare cosa significa rollback e quando Mutiny lo esegue (quando l'`Uni` fallisce).
- Dopo ogni test di fallimento hai controllato il DB con `psql` o un client SQL.

<br><br><br>

---

# Soluzioni

Guardale solo **dopo** aver provato.

## Migrazione

`V4__booking.sql`
```sql
CREATE TABLE booking (
    id         serial PRIMARY KEY,
    pool_id    int         NOT NULL REFERENCES pool(id),
    email      text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE pool ADD COLUMN booked int NOT NULL DEFAULT 0;
```

## ex00 (senza transazione, il difetto)

```java
public Uni<Boolean> bookBad(Integer poolId, String email) {
    return client.preparedQuery("INSERT INTO booking (pool_id, email) VALUES ($1, $2)")
            .execute(Tuple.of(poolId, email))
            .flatMap(r -> client.preparedQuery("UPDATE pool SET bookedX = booked + 1 WHERE id = $1") // colonna sbagliata
                    .execute(Tuple.of(poolId)))
            .map(r -> true);
}
```

La seconda query fallisce ma la prima e' gia' stata confermata: resta una riga in `booking`
senza che `booked` sia stato incrementato.

## ex01

```java
public Uni<Boolean> book(Integer poolId, String email) {
    return client.withTransaction(conn ->
            conn.preparedQuery("INSERT INTO booking (pool_id, email) VALUES ($1, $2)")
                    .execute(Tuple.of(poolId, email))
                    .flatMap(r -> conn.preparedQuery("UPDATE pool SET booked = booked + 1 WHERE id = $1")
                            .execute(Tuple.of(poolId)))
                    .map(r -> r.rowCount() > 0));
}
```

Se un passaggio fallisce, l'`Uni` fallisce e `withTransaction` fa rollback da solo.
Se tutto va bene, fa commit alla fine.

## ex02

```java
public Uni<Boolean> book(Integer poolId, String email) {
    return client.withTransaction(conn ->
            conn.preparedQuery(
                            "UPDATE pool SET booked = booked + 1 "
                          + "WHERE id = $1 AND open AND booked < capacity")
                    .execute(Tuple.of(poolId))
                    .flatMap(r -> {
                        if (r.rowCount() == 0) {
                            return Uni.createFrom().<Boolean>failure(
                                    new ConflictException("error.pool_full", "Piscina piena o chiusa"));
                        }
                        return conn.preparedQuery("INSERT INTO booking (pool_id, email) VALUES ($1, $2)")
                                .execute(Tuple.of(poolId, email))
                                .map(x -> true);
                    }));
}
```

```java
public class ConflictException extends RuntimeException {
    public final String code;
    public ConflictException(String code, String message) { super(message); this.code = code; }
}

// nel file dei mapper
@ServerExceptionMapper
public Response map(ConflictException e) {
    return Response.status(409).entity(new ErrorResponse(e.code, e.getMessage())).build();
}
```

Perche' funziona con due richieste sull'ultimo posto: l'`UPDATE` blocca la riga fino al
commit. La seconda richiesta aspetta, poi rivaluta `booked < capacity` con il valore
aggiornato, trova 0 righe e riceve 409. Il pericolo era fare prima una `SELECT` e poi
un `UPDATE` separato: tra le due entrambe vedrebbero "c'e' posto".

Se vuoi distinguere 404 da 409, fai prima una `SELECT` sull'id nella stessa transazione.

## ex03

```java
public Uni<Boolean> cancel(Integer bookingId) {
    return client.withTransaction(conn ->
            conn.preparedQuery("DELETE FROM booking WHERE id = $1 RETURNING pool_id")
                    .execute(Tuple.of(bookingId))
                    .flatMap(rows -> {
                        if (!rows.iterator().hasNext()) {
                            return Uni.createFrom().<Boolean>failure(
                                    new NotFoundBusinessException("Prenotazione " + bookingId));
                        }
                        Integer poolId = rows.iterator().next().getInteger("pool_id");
                        return conn.preparedQuery("UPDATE pool SET booked = booked - 1 WHERE id = $1")
                                .execute(Tuple.of(poolId))
                                .map(r -> true);
                    }));
}
```
