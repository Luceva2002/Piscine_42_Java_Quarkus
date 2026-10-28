# Modulo 02: Flyway e SQL reattivo con PgPool

Obiettivo: le 4 operazioni base sul DB con `Uni`.
Da qui la resource non ha mai SQL.

Import utili:
```java
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
```

---

## ex00: Le migrazioni

**File:** `src/main/resources/db/migration/V1__create_pool.sql`, `V2__seed_pool.sql`

V1: tabella `pool` con `id` (serial, PK), `name` (text, not null),
`city` (text, not null), `capacity` (int, default 0), `open` (boolean, default true).
V2: inserisci 3 piscine di esempio.

Verifica con `quarkus dev`: nei log deve comparire la migrazione applicata.
Regola Flyway: un file gia' applicato **non si modifica**. Prova a cambiarlo e leggi l'errore.

---

## ex01: SELECT di tutte le righe

**File:** `PoolRepository.java`, `PoolService.java`, `PoolResource.java`

`GET /pools` ritorna `Uni<List<Pool>>` leggendo dal DB.

Scheletro:
```java
client.query("SELECT id, name, city, capacity, open FROM pool ORDER BY id")
      .execute()
      .map(rows -> { /* itera e costruisci i Pool */ });
```

Scrivi un metodo `Pool fromRow(Row row)` e riusalo negli esercizi seguenti.
Per iterare un `RowSet<Row>` si usa un `for (Row r : rows)`.

---

## ex02: SELECT per id (con 404)

`GET /pools/{id}` ritorna il `Pool`, o 404 (con il mapper di m01) se non esiste.

```java
client.preparedQuery("SELECT ... FROM pool WHERE id = $1").execute(Tuple.of(id))
```

Come capisci che non c'e' nessuna riga? `rows.size()` oppure `rows.iterator().hasNext()`.
Il 404 va generato **dentro la catena** `Uni`, non con un `if` fuori.

---

## ex03: INSERT con RETURNING

`POST /pools` inserisce e ritorna il `Pool` completo con l'`id` generato, status **201**.

```sql
INSERT INTO pool (name, city, capacity) VALUES ($1, $2, $3) RETURNING id, name, city, capacity, open
```

Con `RETURNING` ottieni la riga appena creata in una sola query.
Da fare: controlla che i campi obbligatori non siano nulli (400 se mancano).

---

## ex04: UPDATE

`PUT /pools/{id}` modifica name, city, capacity. Ritorna `Uni<Boolean>`:
`true` se `rows.rowCount() > 0`. Se `false`, lancia il 404.

Domanda: perche' `rowCount()` vale 0 se l'id non esiste e non c'e' nessun errore SQL?

---

## ex05: DELETE

`DELETE /pools/{id}` ritorna 204, oppure 404 se non esisteva.

Poi implementa il **soft delete**: aggiungi una migrazione `V3__soft_delete.sql`
con la colonna `deleted_at timestamptz`, cambia il DELETE in UPDATE e filtra le
righe cancellate in tutte le SELECT.

---

## ex06: Filtri dinamici

`GET /pools?city=Roma&open=true&minCapacity=50`.
Tutti i parametri sono opzionali. Costruisci la query aggiungendo le clausole
`AND ...` solo per i filtri presenti, **sempre con parametri `$n`** (niente concatenazione
dei valori).

Suggerimento: una `List<Object>` per i valori e un contatore per l'indice `$n`.

---

## Verifica

- Riavvia l'app: i dati restano. Cancella il DB di dev: Flyway ricrea tutto.
- Provi `city=' OR 1=1 --` come filtro: non deve succedere nulla di strano. Spiega perche'.
- Sai spiegare perche' ci sono due URL (JDBC per Flyway, reactive per PgPool).

<br><br><br>

---

# Soluzioni

Guardale solo **dopo** aver provato.

## ex00

`V1__create_pool.sql`
```sql
CREATE TABLE pool (
    id       serial PRIMARY KEY,
    name     text    NOT NULL,
    city     text    NOT NULL,
    capacity int     NOT NULL DEFAULT 0,
    open     boolean NOT NULL DEFAULT true
);
```

`V2__seed_pool.sql`
```sql
INSERT INTO pool (name, city, capacity) VALUES
    ('Acquacenter', 'Roma', 120),
    ('Blu Club', 'Milano', 80),
    ('Delfino', 'Torino', 60);
```

Se modifichi V1 dopo l'avvio, Flyway segnala che il checksum non coincide.
Si corregge aggiungendo `V3__...sql`, mai riscrivendo il passato.

## ex01 - ex05

```java
public class Pool {
    public Integer id;
    public String name;
    public String city;
    public int capacity;
    public boolean open;
}
```

```java
@ApplicationScoped
public class PoolRepository {

    static final String COLS = "id, name, city, capacity, open";

    @Inject
    PgPool client;

    static Pool fromRow(Row r) {
        Pool p = new Pool();
        p.id = r.getInteger("id");
        p.name = r.getString("name");
        p.city = r.getString("city");
        p.capacity = r.getInteger("capacity");
        p.open = r.getBoolean("open");
        return p;
    }

    public Uni<List<Pool>> findAll() {
        return client.query("SELECT " + COLS + " FROM pool ORDER BY id")
                .execute()
                .map(rows -> {
                    List<Pool> list = new ArrayList<>();
                    for (Row r : rows) list.add(fromRow(r));
                    return list;
                });
    }

    public Uni<Pool> findById(Integer id) {
        return client.preparedQuery("SELECT " + COLS + " FROM pool WHERE id = $1")
                .execute(Tuple.of(id))
                .map(rows -> rows.iterator().hasNext()
                        ? fromRow(rows.iterator().next())
                        : null);
    }

    public Uni<Pool> insert(Pool p) {
        return client.preparedQuery(
                        "INSERT INTO pool (name, city, capacity) VALUES ($1, $2, $3) RETURNING " + COLS)
                .execute(Tuple.of(p.name, p.city, p.capacity))
                .map(rows -> fromRow(rows.iterator().next()));
    }

    public Uni<Boolean> update(Integer id, Pool p) {
        return client.preparedQuery(
                        "UPDATE pool SET name = $1, city = $2, capacity = $3 WHERE id = $4")
                .execute(Tuple.of(p.name, p.city, p.capacity, id))
                .map(rows -> rows.rowCount() > 0);
    }

    public Uni<Boolean> delete(Integer id) {
        return client.preparedQuery("DELETE FROM pool WHERE id = $1")
                .execute(Tuple.of(id))
                .map(rows -> rows.rowCount() > 0);
    }
}
```

```java
@ApplicationScoped
public class PoolService {
    @Inject PoolRepository repo;

    public Uni<List<Pool>> list() { return repo.findAll(); }

    public Uni<Pool> get(Integer id) {
        return repo.findById(id)
                .onItem().ifNull().failWith(() -> new NotFoundBusinessException("Piscina " + id));
    }

    public Uni<Pool> create(Pool p) {
        if (p.name == null || p.name.isBlank() || p.city == null || p.city.isBlank()) {
            throw new CustomException("error.invalid_pool", "Nome e citta' obbligatori");
        }
        return repo.insert(p);
    }

    public Uni<Boolean> update(Integer id, Pool p) {
        return repo.update(id, p).map(ok -> {
            if (!ok) throw new NotFoundBusinessException("Piscina " + id);
            return true;
        });
    }

    public Uni<Boolean> delete(Integer id) {
        return repo.delete(id).map(ok -> {
            if (!ok) throw new NotFoundBusinessException("Piscina " + id);
            return true;
        });
    }
}
```

```java
@Path("/pools")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PoolResource {
    @Inject PoolService service;

    @GET
    public Uni<List<Pool>> list() { return service.list(); }

    @GET @Path("/{id}")
    public Uni<Pool> get(@PathParam("id") Integer id) { return service.get(id); }

    @POST
    public Uni<Response> create(Pool p) {
        return service.create(p).map(created ->
                Response.created(URI.create("/pools/" + created.id)).entity(created).build());
    }

    @PUT @Path("/{id}")
    public Uni<Boolean> update(@PathParam("id") Integer id, Pool p) { return service.update(id, p); }

    @DELETE @Path("/{id}")
    public Uni<Response> delete(@PathParam("id") Integer id) {
        return service.delete(id).map(ok -> Response.noContent().build());
    }
}
```

Risposte:
- `UPDATE` su un id inesistente e' una query valida che tocca 0 righe: il DB non da
  errore, quindi sta a te leggere `rowCount()`.
- Il 404 dell'ex02 e' dentro la catena grazie a `onItem().ifNull().failWith(...)`.
- Due URL: Flyway usa JDBC (bloccante, parte all'avvio), `PgPool` usa il driver Vert.x
  non bloccante.

## ex05 (soft delete)

`V3__soft_delete.sql`
```sql
ALTER TABLE pool ADD COLUMN deleted_at timestamptz;
```

```java
// delete
"UPDATE pool SET deleted_at = now() WHERE id = $1 AND deleted_at IS NULL"
// tutte le SELECT
"... FROM pool WHERE deleted_at IS NULL ..."
```

## ex06

```java
public Uni<List<Pool>> search(String city, Boolean open, Integer minCapacity) {
    StringBuilder sql = new StringBuilder("SELECT " + COLS + " FROM pool WHERE deleted_at IS NULL");
    List<Object> params = new ArrayList<>();

    if (city != null) {
        params.add(city);
        sql.append(" AND city = $").append(params.size());
    }
    if (open != null) {
        params.add(open);
        sql.append(" AND open = $").append(params.size());
    }
    if (minCapacity != null) {
        params.add(minCapacity);
        sql.append(" AND capacity >= $").append(params.size());
    }
    sql.append(" ORDER BY id");

    return client.preparedQuery(sql.toString())
            .execute(Tuple.from(params))
            .map(rows -> {
                List<Pool> list = new ArrayList<>();
                for (Row r : rows) list.add(fromRow(r));
                return list;
            });
}
```

Nella stringa finiscono solo `$1`, `$2`...: i valori viaggiano a parte nel `Tuple`.
Per questo `city=' OR 1=1 --` viene trattato come un normale nome di citta' e non trova nulla.
