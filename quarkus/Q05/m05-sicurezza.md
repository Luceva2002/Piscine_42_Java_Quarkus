# Modulo 05: Sicurezza con OIDC

Obiettivo: proteggere gli endpoint e leggere l'utente dal token.

Setup: l'estensione `quarkus-oidc` con Dev Services avvia da sola un Keycloak di sviluppo
(serve Docker). Leggi la guida "OIDC Bearer token authentication" e la sezione Dev Services.

---

## ex00: Endpoint protetto

**File:** `SecureResource.java`

`GET /secure/ping` risponde `pong` solo con un token valido.
Senza token: 401.

```bash
curl -i localhost:8080/secure/ping
```

Da usare: `@Authenticated`. Ottieni un token dalla Dev UI (`localhost:8080/q/dev-ui`).

---

## ex01: Ruoli

Aggiungi due endpoint:
- `GET /secure/user`: ruolo `user` o `admin`
- `GET /secure/admin`: solo `admin`

Da usare: `@RolesAllowed`. Prova con token diversi (utenti `alice` e `bob` in dev).
Che status ottieni con token valido ma ruolo sbagliato? (401 o 403? Spiega la differenza.)

---

## ex02: Leggere il token

**File:** `MeResource.java`

`GET /me` ritorna un JSON con `username`, `email`, `firstName`, `lastName` letti dal token.

Fallo in due modi:
1. con `@Claim("preferred_username")` ecc. su campi di una classe `@RequestScoped`
2. con `@Inject JsonWebToken jwt` e `jwt.getClaim("email")`

Perche' con `@Claim` la classe non puo' essere un singleton?

---

## ex03: Regola di business sul token

Riprendi il controllo del progetto reale: in `POST /pools/{id}/book` rifiuta con
400 (`error.user_email_prohibited`) se l'email nel body e' uguale a quella dell'utente nel token.

---

## ex04: Pool per utente

Aggiungi `owner` (username) alla tabella `pool` con una nuova migrazione.
Alla creazione prendi il valore dal token. `GET /pools/mine` ritorna solo le piscine
dell'utente loggato.

---

## Verifica

- Nessun endpoint dei moduli 02-04 e' piu' accessibile senza token.
- Sai spiegare cosa fa Quarkus tra l'arrivo della richiesta e l'esecuzione del tuo metodo.

<br><br><br>

---

# Soluzioni

Guardale solo **dopo** aver provato.

## ex00

```java
@Path("/secure")
@Produces(MediaType.TEXT_PLAIN)
public class SecureResource {

    @GET
    @Path("/ping")
    @Authenticated // io.quarkus.security.Authenticated
    public String ping() {
        return "pong";
    }
}
```

Con Dev Services gli utenti di prova sono in genere `alice` (ruoli `admin`, `user`) e `bob`
(ruolo `user`). La Dev UI ha una sezione OIDC da cui ottieni il token.

```bash
TOKEN=...   # incolla il token
curl -i -H "Authorization: Bearer $TOKEN" localhost:8080/secure/ping
```

## ex01

```java
@GET
@Path("/user")
@RolesAllowed({"user", "admin"})
public String user() { return "user ok"; }

@GET
@Path("/admin")
@RolesAllowed("admin")
public String admin() { return "admin ok"; }
```

- **401**: non sei autenticato (token assente o non valido).
- **403**: sei autenticato ma il tuo ruolo non basta. Bob su `/secure/admin` riceve 403.

## ex02

```java
@Path("/me")
@RequestScoped
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class MeResource {

    @Inject @Claim("preferred_username") String username;
    @Inject @Claim("email") String email;
    @Inject @Claim("given_name") String firstName;
    @Inject @Claim("family_name") String lastName;

    @GET
    public Map<String, String> me() {
        return Map.of("username", username, "email", email,
                      "firstName", firstName, "lastName", lastName);
    }
}
```

`Map.of` non accetta `null`: se un claim manca, l'endpoint va in errore. Usa `HashMap` se i
claim possono mancare.

Versione 2:
```java
@Inject JsonWebToken jwt;

@GET
public Map<String, Object> me2() {
    String email = jwt.getClaim("email");
    return Map.of("email", email, "username", jwt.getName());
}
```

Con `@Claim` il valore e' una stringa fissata alla creazione del bean. Un singleton verrebbe
creato una volta sola con il token di una richiesta, e lo userebbero tutti gli altri utenti.
Con `@RequestScoped` il bean nasce a ogni richiesta, con il suo token.

## ex03

```java
@POST
@Path("/pools/{id}/book")
@Consumes(MediaType.APPLICATION_JSON)
public Uni<Boolean> book(@PathParam("id") Integer id, BookingRequest req) {
    String myEmail = jwt.getClaim("email");
    if (myEmail.equalsIgnoreCase(req.email)) {
        throw new CustomException("error.user_email_prohibited", "Email utente non valida per ospite");
    }
    return bookingService.book(id, req.email);
}
```

## ex04

`V5__pool_owner.sql`
```sql
ALTER TABLE pool ADD COLUMN owner text;
```

```java
// repository
public Uni<Pool> insert(Pool p, String owner) {
    return client.preparedQuery(
                    "INSERT INTO pool (name, city, capacity, owner) VALUES ($1, $2, $3, $4) RETURNING " + COLS)
            .execute(Tuple.of(p.name, p.city, p.capacity, owner))
            .map(rows -> fromRow(rows.iterator().next()));
}

public Uni<List<Pool>> findByOwner(String owner) {
    return client.preparedQuery("SELECT " + COLS + " FROM pool WHERE owner = $1 AND deleted_at IS NULL ORDER BY id")
            .execute(Tuple.of(owner))
            .map(rows -> { /* come findAll */ });
}

// resource
@GET @Path("/mine")
public Uni<List<Pool>> mine() { return service.findByOwner(jwt.getName()); }
```

`@Path("/mine")` e `@Path("/{id}")` coesistono: nel matching JAX-RS il percorso letterale
vince sul template.

Se non hai ancora esteso `COLS` con `owner`, la `fromRow` continua a funzionare: le colonne
che non leggi vengono ignorate.
