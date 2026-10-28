# Modulo 01: CDI, service ed eccezioni

Obiettivo: separare resource e logica, capire l'iniezione e gli scope.
Ancora senza DB.

---

## ex00: Il primo service

**File:** `PoolService.java`, `PoolResource.java`

Sposta la logica di m00/ex05 in `PoolService` (`@ApplicationScoped`).
`PoolResource` contiene solo `@Inject PoolService service;` e delega.

Regola: nella resource non ci deve essere nessun `new PoolService()`.

---

## ex01: Gli scope

**File:** `CounterService.java`, `CounterResource.java`

Crea un bean con un contatore intero e un endpoint `GET /counter` che lo incrementa
e lo ritorna. Prova tre versioni:

1. `@ApplicationScoped`
2. `@RequestScoped`
3. `@Dependent`

Chiama l'endpoint 3 volte per ogni versione. Scrivi in un commento nel file cosa
vedi e perche'.

---

## ex02: Un bean di utilita'

**File:** `EmailValidator.java`

Metodo `boolean verifyEmailAddress(String email)`: vero se contiene una sola `@`,
qualcosa prima e un dominio con almeno un punto dopo.
Inietta il validator in una resource `POST /validate` che riceve il testo
dell'email nel body e ritorna `true` o `false`.

Bonus: un metodo `String extractDomain(String email)`. Cosa succede con `"abc"`?

---

## ex03: Configurazione

**File:** `application.properties`, `GreetingConfig.java`

Aggiungi `piscina.greeting=ciao dal service`.
Leggilo con `@ConfigProperty(name = "piscina.greeting")` e usalo in `GET /greet-config`.

Poi:
- aggiungi un valore di default con `defaultValue`
- sovrascrivilo da riga di comando: `quarkus dev -Dpiscina.greeting=boo`
- prova a sovrascriverlo con la variabile d'ambiente `PISCINA_GREETING`

---

## ex04: Eccezioni custom

**File:** `CustomException.java`, `ErrorResponse.java`, `ExceptionMappers.java`

`CustomException(String code, String message)` estende `RuntimeException`.
Nel mapper:

```java
@ServerExceptionMapper
public Response map(CustomException e) { ... }
```

deve ritornare **400** con JSON `{"code": "...", "message": "..."}`.
Fai lanciare l'eccezione da `GET /fail`.

Aggiungi un secondo mapper per una `NotFoundBusinessException` che ritorna 404.
Poi cambia `/pools/{id}` di m00 perche' lanci questa eccezione al posto di
costruire la `Response` a mano.

---

## Verifica

- Sai spiegare cosa fa `@Inject` e perche' non si usa `new`.
- Sai dire quando usare `@ApplicationScoped` e quando `@RequestScoped`.
- Un `throw` dentro il service arriva al client come JSON pulito, mai come 500 con stacktrace.

<br><br><br>

---

# Soluzioni

Guardale solo **dopo** aver provato.

## ex00

```java
@ApplicationScoped
public class PoolService {
    private final Map<Integer, Pool> pools = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger();

    public List<Pool> list(String city) { /* come m00 */ }
    public Pool get(int id) { /* ... */ }
    // create, update, delete
}
```

```java
@Path("/pools")
public class PoolResource {
    @Inject
    PoolService service;

    @GET
    public List<Pool> list(@QueryParam("city") String city) {
        return service.list(city);
    }
}
```

## ex01

```java
@ApplicationScoped // poi @RequestScoped, poi @Dependent
public class CounterService {
    private int count = 0;
    public int next() { return ++count; }
}

@Path("/counter")
public class CounterResource {
    @Inject CounterService counter;

    @GET
    public int get() { return counter.next(); }
}
```

Risultati dopo 3 chiamate:
- `@ApplicationScoped`: 1, 2, 3 (un'unica istanza per tutta l'app)
- `@RequestScoped`: 1, 1, 1 (istanza nuova a ogni richiesta)
- `@Dependent`: 1, 2, 3 (l'istanza vive quanto il bean che la riceve; la resource e'
  condivisa, quindi il contatore pure)

## ex02

```java
@ApplicationScoped
public class EmailValidator {

    public boolean verifyEmailAddress(String email) {
        if (email == null) return false;
        String[] parts = email.split("@", -1);
        if (parts.length != 2 || parts[0].isBlank()) return false;
        String domain = parts[1];
        return domain.contains(".") && !domain.startsWith(".") && !domain.endsWith(".");
    }

    public String extractDomain(String email) {
        return email.split("@")[1];
    }
}
```

```java
@Path("/validate")
public class ValidateResource {
    @Inject EmailValidator validator;

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public boolean validate(String email) {
        return validator.verifyEmailAddress(email);
    }
}
```

Con `"abc"`, `extractDomain` lancia `ArrayIndexOutOfBoundsException`: `split("@")` ritorna
un solo elemento. Per questo il progetto reale controlla `domain.length <= 1`.

## ex03

```properties
piscina.greeting=ciao dal service
```

```java
@ApplicationScoped
public class GreetingConfig {
    @ConfigProperty(name = "piscina.greeting", defaultValue = "ciao")
    String greeting;

    public String get() { return greeting; }
}
```

Ordine di priorita': proprieta' di sistema (`-D`) > variabili d'ambiente
(`PISCINA_GREETING`) > `application.properties`.

## ex04

```java
public class CustomException extends RuntimeException {
    public final String code;
    public CustomException(String code, String message) {
        super(message);
        this.code = code;
    }
}

public class NotFoundBusinessException extends RuntimeException {
    public NotFoundBusinessException(String message) { super(message); }
}

public record ErrorResponse(String code, String message) {}
```

```java
public class ExceptionMappers {

    @ServerExceptionMapper
    public Response map(CustomException e) {
        return Response.status(400)
                .entity(new ErrorResponse(e.code, e.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    @ServerExceptionMapper
    public Response map(NotFoundBusinessException e) {
        return Response.status(404)
                .entity(new ErrorResponse("error.not_found", e.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
```

La classe dei mapper non ha bisogno di annotazioni di classe: Quarkus trova i metodi
`@ServerExceptionMapper` da solo.
