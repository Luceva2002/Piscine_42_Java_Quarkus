# Modulo 00: REST senza database

Obiettivo: padroneggiare annotazioni JAX-RS. Nessun DB, i dati stanno in memoria.

Cartella: `piscina/rest/` — file consentiti: solo `.java` indicati.

---

## ex00: Hello

**File:** `HelloResource.java`

`GET /hello` ritorna il testo `hello piscina` con status 200.

```bash
curl -i localhost:8080/hello
```

Da usare: `@Path`, `@GET`, `@Produces(MediaType.TEXT_PLAIN)`.

---

## ex01: Saluta per nome

**File:** `GreetResource.java`

`GET /greet/{name}` ritorna `ciao <name>`.

```bash
curl localhost:8080/greet/Luca     # ciao Luca
```

Da usare: `@PathParam`. Poi riscrivilo con `@RestPath` e confronta.

---

## ex02: Query param con default

**File:** `GreetResource.java` (stessa classe)

`GET /greet?name=Luca&lang=it|en`. Se `lang` manca, vale `it`.
`en` ritorna `hello <name>`.

Da usare: `@QueryParam`, `@DefaultValue`.

---

## ex03: Calcolatrice

**File:** `MathResource.java`

`GET /math/sum?a=2&b=3` ritorna `5`.
`GET /math/div?a=10&b=0` ritorna **400** con un messaggio di errore, non 500.

Da usare: `Response.status(...).entity(...).build()`.
Domanda da saper rispondere: perche' un `throw` non gestito produce 500?

---

## ex04: Body JSON

**File:** `Pool.java` (POJO con `id`, `name`, `city`), `PoolResource.java`

`POST /pools` riceve un JSON e ritorna lo stesso oggetto con un `id` assegnato.

```bash
curl -X POST localhost:8080/pools -H 'Content-Type: application/json' \
  -d '{"name":"Acquacenter","city":"Roma"}'
```

Da usare: `@Consumes`, `@Produces`, parametro senza annotazione = body.
Prova a inviare un JSON rotto e leggi lo status.

---

## ex05: CRUD in memoria

**File:** `PoolResource.java`

Gestisci una `List<Pool>` (o `Map`) dentro la classe.

| Richiesta | Risposta |
|---|---|
| `GET /pools` | lista, 200 |
| `GET /pools/{id}` | oggetto, 200, oppure 404 |
| `POST /pools` | creato, **201** con header `Location` |
| `PUT /pools/{id}` | aggiornato, 200, oppure 404 |
| `DELETE /pools/{id}` | **204**, oppure 404 |
| `GET /pools?city=Roma` | solo le piscine di Roma |

Attenzione: la resource e' di default `@Singleton`-like per Quarkus REST, quindi
lo stato in un campo e' condiviso tra richieste. Pensa a cosa succede con due
richieste contemporanee (usa `ConcurrentHashMap` / `AtomicInteger`).

---

## Verifica

- Tutti i `curl` sopra danno l'output atteso.
- Sai spiegare la differenza tra `@Path` di classe e di metodo.
- Sai dire quando serve `@Consumes` e quando e' inutile (GET, DELETE).

<br><br><br>

---

# Soluzioni

Guardale solo **dopo** aver provato. Confrontale, non copiarle.

## ex00

```java
@Path("/hello")
public class HelloResource {
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "hello piscina";
    }
}
```

## ex01 + ex02

```java
@Path("/greet")
@Produces(MediaType.TEXT_PLAIN)
public class GreetResource {

    @GET
    @Path("/{name}")
    public String greet(@PathParam("name") String name) {
        return "ciao " + name;
    }

    @GET
    public String greetQuery(@QueryParam("name") String name,
                             @QueryParam("lang") @DefaultValue("it") String lang) {
        return "en".equals(lang) ? "hello " + name : "ciao " + name;
    }
}
```

Con `@RestPath String name` il nome del parametro Java diventa il nome del path param
(serve `<parameters>true</parameters>` nel compiler plugin, gia' nel pom).

## ex03

```java
@Path("/math")
@Produces(MediaType.TEXT_PLAIN)
public class MathResource {

    @GET
    @Path("/sum")
    public int sum(@QueryParam("a") int a, @QueryParam("b") int b) {
        return a + b;
    }

    @GET
    @Path("/div")
    public Response div(@QueryParam("a") int a, @QueryParam("b") int b) {
        if (b == 0) {
            return Response.status(400).entity("divisione per zero").build();
        }
        return Response.ok(a / b).build();
    }
}
```

Risposta alla domanda: un'eccezione non gestita non ha un mapper, quindi Quarkus la
tratta come errore del server (500). Il 400 e' una scelta tua: "il client ha sbagliato".

## ex04 + ex05

```java
public class Pool {
    public Integer id;
    public String name;
    public String city;
}
```

```java
@Path("/pools")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PoolResource {

    private final Map<Integer, Pool> pools = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger();

    @GET
    public List<Pool> list(@QueryParam("city") String city) {
        return pools.values().stream()
                .filter(p -> city == null || city.equalsIgnoreCase(p.city))
                .toList();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") int id) {
        Pool p = pools.get(id);
        return p == null ? Response.status(404).build() : Response.ok(p).build();
    }

    @POST
    public Response create(Pool p) {
        p.id = seq.incrementAndGet();
        pools.put(p.id, p);
        return Response.created(URI.create("/pools/" + p.id)).entity(p).build();
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") int id, Pool p) {
        if (!pools.containsKey(id)) {
            return Response.status(404).build();
        }
        p.id = id;
        pools.put(id, p);
        return Response.ok(p).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") int id) {
        return pools.remove(id) == null
                ? Response.status(404).build()
                : Response.noContent().build();
    }
}
```

All'ex04 il `POST` puo' ritornare 200; dall'ex05 in poi ritorna 201 con `Location`.
`ConcurrentHashMap` e `AtomicInteger` servono perche' la resource e' condivisa tra le richieste.
