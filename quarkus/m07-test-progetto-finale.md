# Modulo 07: Test e progetto finale

---

## ex00: Il primo test

**File:** `src/test/java/piscina/rest/PoolResourceTest.java`

```java
@QuarkusTest
class PoolResourceTest {
    @Test
    void listReturns200() {
        given().when().get("/pools").then().statusCode(200);
    }
}
```

Esegui con `quarkus test` o `mvn test`.
Gli endpoint ora richiedono token: usa `@TestSecurity(user = "alice", roles = "admin")`
(estensione `quarkus-test-security`) per simulare l'utente.

---

## ex01: Un test per ogni status code

Per `/pools/{id}` scrivi test che verificano:
- 200 con id esistente (e corpo JSON corretto con `body("name", equalTo(...))`)
- 404 con id inesistente
- 401 senza utente
- 403 con ruolo sbagliato

---

## ex02: Test di scrittura

Test di `POST`, `PUT`, `DELETE`: dopo ogni scrittura rileggi con una `GET`
per verificare che il DB sia cambiato. Ogni test deve lasciare il DB come l'ha trovato
(o creare i suoi dati con nomi unici).

---

## ex03: Test di transazione

Verifica che una prenotazione che fallisce non lasci righe in `booking` e non
incrementi `pool.booked`.

---

## Progetto finale: mini AdminResource

Riproduci in piccolo la struttura del backend reale. Dominio: **eventi e ospiti**.

Migrazioni: `event` (`id`, `title`, `status`, `image bytea`) e `guest`
(`id`, `event_id` FK, `first_name`, `last_name`, `email`, `phone`).

Resource `/admin`, tutta con `@RolesAllowed("admin")`, tutti i metodi con `Uni`:

| Endpoint | Cosa fa |
|---|---|
| `POST /admin/events` (multipart: `file` + `event` JSON) | crea evento con immagine |
| `PUT /admin/events/{id}/registration` (body: status) | apre/chiude le iscrizioni |
| `PUT /admin/events/{id}/close` | chiude l'evento |
| `POST /admin/events/{id}/guests` | aggiunge ospite |
| `PUT /admin/guests/{id}` | modifica ospite |
| `DELETE /admin/events/{eventId}/guests/{guestId}` | cancella ospite |
| `GET /admin/guests/{id}` | dettaglio |
| `POST /admin/events/{id}/guests/search` (body: filtri) | lista filtrata e paginata |
| `POST /admin/events/{id}/guests/export` | xlsx degli ospiti |

Regole di business (come nel progetto vero):
- l'email dell'ospite non puo' essere quella dell'admin loggato
- l'email deve essere valida e il dominio non vuoto (bean `EmailValidator`)
- il telefono viene normalizzato togliendo gli spazi
- niente iscrizioni a eventi chiusi (409)
- errori con codice + messaggio tramite `CustomException` e mapper

Vincoli di struttura: la resource non ha SQL e non ha logica oltre
a leggere parametri e claim; service e repository separati; tutte le query con `$n`;
operazioni composte in transazione; test per ogni endpoint.

## Consegna finale

- [ ] Ogni endpoint funziona con `curl`
- [ ] Test verdi con `mvn test`
- [ ] Nessun `await()` negli endpoint
- [ ] Puoi spiegare a voce ogni riga della resource e di un metodo del service
- [ ] Confronta il tuo progetto con `AdminResource.java` reale: cosa hai fatto diversamente e perche'?

<br><br><br>

---

# Soluzioni

Guardale solo **dopo** aver provato.

## Dipendenze di test

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-test-security</artifactId>
    <scope>test</scope>
</dependency>
```

Import usati negli esempi:
```java
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
```

## ex00

```java
@QuarkusTest
@TestSecurity(user = "alice", roles = "admin")
class PoolResourceTest {

    @Test
    void listReturns200() {
        given().when().get("/pools").then().statusCode(200);
    }
}
```

## ex01

```java
@QuarkusTest
class PoolReadTest {

    @Test
    @TestSecurity(user = "alice", roles = "admin")
    void getExisting() {
        given().when().get("/pools/1")
                .then().statusCode(200).body("name", notNullValue());
    }

    @Test
    @TestSecurity(user = "alice", roles = "admin")
    void getMissing() {
        given().when().get("/pools/999999").then().statusCode(404);
    }

    @Test
    void withoutUser() {
        given().when().get("/pools/1").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "bob", roles = "user")
    void wrongRole() {
        given().when().delete("/pools/1").then().statusCode(403);
    }
}
```

Il test del 403 presuppone che la `DELETE` richieda il ruolo `admin`.

## ex02

```java
@Test
@TestSecurity(user = "alice", roles = "admin")
void createUpdateDelete() {
    String name = "T-" + UUID.randomUUID();

    int id = given().contentType("application/json")
            .body("{\"name\":\"" + name + "\",\"city\":\"Roma\",\"capacity\":10}")
            .when().post("/pools")
            .then().statusCode(201)
            .extract().path("id");

    given().when().get("/pools/" + id)
            .then().statusCode(200).body("city", is("Roma"));

    given().contentType("application/json")
            .body("{\"name\":\"" + name + "\",\"city\":\"Milano\",\"capacity\":20}")
            .when().put("/pools/" + id)
            .then().statusCode(200);

    given().when().get("/pools/" + id)
            .then().body("city", is("Milano"));

    given().when().delete("/pools/" + id).then().statusCode(204);
    given().when().get("/pools/" + id).then().statusCode(404);
}
```

## ex03

```java
@Test
@TestSecurity(user = "alice", roles = "admin")
void failedBookingLeavesNoTrace() {
    int id = createPool(1); // helper: crea una piscina con capienza 1 e ritorna l'id

    given().contentType("application/json").body("{\"email\":\"a@x.it\"}")
            .when().post("/pools/" + id + "/book").then().statusCode(200);

    // la piscina e' piena: la seconda deve fallire
    given().contentType("application/json").body("{\"email\":\"b@x.it\"}")
            .when().post("/pools/" + id + "/book").then().statusCode(409);

    // nessuna riga in piu', nessun incremento
    given().when().get("/pools/" + id).then().body("booked", is(1));
}
```

Perche' `booked` risulti nel JSON, aggiungi il campo `booked` al `Pool` e alla `fromRow`.

---

## Progetto finale: scheletro

### Migrazioni

`V10__event_guest.sql`
```sql
CREATE TABLE event (
    id     serial PRIMARY KEY,
    title  text NOT NULL,
    status int  NOT NULL DEFAULT 1,   -- 1 iscrizioni aperte, 2 iscrizioni chiuse, 3 evento chiuso
    image  bytea
);

CREATE TABLE guest (
    id         serial PRIMARY KEY,
    event_id   int  NOT NULL REFERENCES event(id),
    first_name text NOT NULL,
    last_name  text NOT NULL,
    email      text NOT NULL,
    phone      text,
    UNIQUE (event_id, email)
);
```

### Resource

```java
@Path("/admin")
@RequestScoped
@RolesAllowed("admin")
public class AdminResource {

    @Inject AdminService adminService;
    @Inject EmailValidator emailValidator;

    @Inject @Claim("email") String email;

    @POST
    @Path("/events")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Blocking
    public Uni<Boolean> newEvent(
            @RestForm("file") FileUpload file,
            @RestForm("event") @PartType(MediaType.APPLICATION_JSON) Event event) throws IOException {
        event.image = Files.readAllBytes(file.filePath());
        return adminService.newEvent(event);
    }

    @PUT
    @Path("/events/{id}/registration")
    public Uni<Boolean> setRegistration(@RestPath Integer id, Integer status) {
        return adminService.setStatus(id, status);
    }

    @PUT
    @Path("/events/{id}/close")
    public Uni<Boolean> closeEvent(@RestPath Integer id) {
        return adminService.setStatus(id, 3);
    }

    @POST
    @Path("/events/{id}/guests")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Boolean> addGuest(@RestPath Integer id, Guest guest) {
        checkGuest(guest);
        return adminService.addGuest(id, guest);
    }

    @PUT
    @Path("/guests/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Boolean> editGuest(@RestPath Integer id, Guest guest) {
        checkGuest(guest);
        return adminService.editGuest(id, guest);
    }

    @DELETE
    @Path("/events/{eventId}/guests/{guestId}")
    public Uni<Boolean> deleteGuest(@RestPath Integer eventId, @RestPath Integer guestId) {
        return adminService.deleteGuest(eventId, guestId);
    }

    @GET
    @Path("/guests/{id}")
    public Uni<Guest> getGuest(@RestPath Integer id) {
        return adminService.getGuest(id);
    }

    // la normalizzazione e i controlli stanno in un unico metodo privato
    private void checkGuest(Guest g) {
        if (email.equalsIgnoreCase(g.email)) {
            throw new CustomException("error.user_email_prohibited", "Email utente non valida per ospite");
        }
        if (g.phone != null) {
            g.phone = g.phone.replaceAll("\\s+", "");
        }
        if (!emailValidator.verifyEmailAddress(g.email)) {
            throw new CustomException("error.not_valid_email", "Email non valida");
        }
    }
}
```

### Service: aggiunta ospite

```java
public Uni<Boolean> addGuest(Integer eventId, Guest g) {
    return client.withTransaction(conn ->
            conn.preparedQuery("SELECT status FROM event WHERE id = $1 FOR UPDATE")
                    .execute(Tuple.of(eventId))
                    .flatMap(rows -> {
                        if (!rows.iterator().hasNext()) {
                            return Uni.createFrom().<Boolean>failure(
                                    new NotFoundBusinessException("Evento " + eventId));
                        }
                        if (rows.iterator().next().getInteger("status") != 1) {
                            return Uni.createFrom().<Boolean>failure(
                                    new ConflictException("error.registration_closed", "Iscrizioni chiuse"));
                        }
                        return conn.preparedQuery(
                                        "INSERT INTO guest (event_id, first_name, last_name, email, phone) "
                                      + "VALUES ($1, $2, $3, $4, $5)")
                                .execute(Tuple.of(eventId, g.firstName, g.lastName, g.email, g.phone))
                                .map(r -> r.rowCount() > 0);
                    }));
}
```

### Service: ricerca con filtri e paginazione

```java
public Uni<GuestList> search(Integer eventId, GuestFilters f) {
    StringBuilder where = new StringBuilder(" WHERE event_id = $1");
    List<Object> params = new ArrayList<>(List.of(eventId));

    if (f.email != null && !f.email.isBlank()) {
        params.add("%" + f.email.toLowerCase() + "%");
        where.append(" AND lower(email) LIKE $").append(params.size());
    }
    if (f.lastName != null && !f.lastName.isBlank()) {
        params.add("%" + f.lastName.toLowerCase() + "%");
        where.append(" AND lower(last_name) LIKE $").append(params.size());
    }

    String countSql = "SELECT count(*) AS v FROM guest" + where;
    List<Object> pageParams = new ArrayList<>(params);
    pageParams.add(f.pageSize);
    pageParams.add(f.page * f.pageSize);
    String pageSql = "SELECT * FROM guest" + where + " ORDER BY id LIMIT $" + (params.size() + 1)
            + " OFFSET $" + (params.size() + 2);

    Uni<Long> total = client.preparedQuery(countSql).execute(Tuple.from(params))
            .map(r -> r.iterator().next().getLong("v"));
    Uni<List<Guest>> items = client.preparedQuery(pageSql).execute(Tuple.from(pageParams))
            .map(rows -> { /* fromRow per ogni riga */ return new ArrayList<Guest>(); });

    return Uni.combine().all().unis(total, items).asTuple()
            .map(t -> new GuestList(t.getItem1(), t.getItem2()));
}
```

### Il resto

`editGuest`, `deleteGuest`, `getGuest`, `setStatus` e `newEvent` hanno la stessa forma
dei metodi del modulo 02 (`UPDATE`/`DELETE`/`SELECT` + `rowCount()` o `ifNull().failWith`).
L'export xlsx degli ospiti riusa lo schema di `PoolXlsx` del modulo 06. I test seguono il
modello dell'ex02 e dell'ex03.

Alla fine confronta con `AdminResource.java` reale: noterai che il progetto vero usa nomi di path
con verbi (`add-extra-guest`) e i claim letti anche per costruire un `LvmhUser`, mentre
qui hai separato meglio resource e service.
