# Piscina Quarkus: REST + SQL reattivo

Stile 42: ogni modulo ha degli esercizi (`ex00`, `ex01`, ...) con una consegna precisa.
Niente copia-incolla da AI: scrivi, rompi, leggi gli errori, correggi.

## Moduli

| Modulo | Tema | Cosa impari |
|---|---|---|
| m00 | REST senza DB | `@Path`, verbi, `@PathParam`, `@QueryParam`, body JSON, status code |
| m01 | CDI e service | `@Inject`, scope, `@ConfigProperty`, eccezioni e mapper |
| m02 | Flyway e PgPool | migrazioni, SELECT/INSERT/UPDATE/DELETE con `Uni` |
| m03 | Mutiny | `map`, `flatMap`, errori, `combine` |
| m04 | Transazioni | `withTransaction`, rollback |
| m05 | Sicurezza OIDC | `@RolesAllowed`, `@Claim` |
| m06 | File | multipart, `StreamingOutput`, xlsx |
| m07 | Test + progetto finale | `@QuarkusTest`, REST Assured, mini `AdminResource` |

Regola: non passare al modulo successivo finche' tutti gli esercizi del precedente
non passano i test della sezione "Verifica".

In fondo a ogni modulo c'e' la sezione **Soluzioni**. Usala solo per confrontare dopo aver
provato (almeno 30 minuti di tentativi veri): se la leggi prima, hai perso l'esercizio.

## Setup (una volta sola)

Servono: JDK 21, Maven, Docker (per il Postgres di sviluppo), Quarkus CLI.

```bash
quarkus create app synergica:piscina \
  --extension='rest,rest-jackson,reactive-pg-client,jdbc-postgresql,flyway,hibernate-validator,oidc' \
  --no-code
cd piscina
quarkus dev
```

`quarkus dev` avvia l'app con live reload. Con Docker acceso, Quarkus crea da solo
un Postgres (Dev Services): non serve configurare l'URL in sviluppo.

In `src/main/resources/application.properties`:

```properties
quarkus.flyway.migrate-at-start=true
```

Struttura consigliata:

```
src/main/java/piscina/
  rest/       # le resource (solo HTTP)
  service/    # logica e query
  model/      # POJO
  exception/  # eccezioni custom e mapper
src/main/resources/db/migration/   # V1__..., V2__...
```

## Regole generali

- Un esercizio = una resource (o un metodo) con il suo URL. Nome file nella consegna.
- La resource **non contiene SQL**: delega a un service. Da m02 in poi.
- Vietato Hibernate, Panache, JPA. Solo `PgPool` con query scritte a mano.
- Parametri sempre con `$1, $2`. Mai concatenare stringhe in una query.
- Vietato `.await().indefinitely()` nel codice degli endpoint.
- Ogni endpoint si prova con `curl` (comandi di esempio negli esercizi).
- Leggi la documentazione su quarkus.io/guides prima di chiedere aiuto.

## Come si valuta da soli

Per ogni esercizio:
1. Funziona col `curl` della consegna?
2. Gli status code sono giusti (anche nei casi di errore)?
3. Riesci a spiegare ad alta voce ogni annotazione che hai scritto?
Se manca anche una sola risposta, l'esercizio non e' passato.
