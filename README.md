# Piscina Java + Quarkus

Due percorsi, in stile 42: moduli piccoli, tanti esercizi, soluzioni in fondo a ogni pagina.

```
piscina/
  java/      j00..j09   Java puro (file singoli: java ExNN.java)
  quarkus/   m00..m07   Quarkus REST + PgPool + Flyway + Mutiny
```

## Ordine di studio

1. **Java j00 → j04** (basi, OOP, collezioni, eccezioni, lambda/stream). Senza questi non si legge nessuna riga di `AdminResource`.
2. **Quarkus m00-m01** (endpoint, CDI). Servono j01 e j05.
3. **Java j05** (annotazioni e reflection) in parallelo a m00: capisci cosa sono `@Path`, `@GET`, `@Inject`.
4. **Quarkus m02** (Flyway e `PgPool`) con **Java j06** (I/O e file di migrazione).
5. **Java j07 + j09** (concorrenza e mini-`Uni`) *prima* di **Quarkus m03-m04** (Mutiny e transazioni).
6. **Quarkus m05-m06** (sicurezza, file), poi **Java j08** (Maven e test) prima di **m07** (test e progetto finale).

## Da Java a Quarkus: cosa serve per cosa

| Quarkus | Prerequisito Java |
|---|---|
| m00 endpoint, `@Path`, `@GET`, `@POST` | j01 (classi, record), j05 (annotazioni) |
| m01 CDI, `@Inject`, `@ApplicationScoped` | j01 (interfacce), j05 (reflection, DI) |
| m02 Flyway, `PgPool`, SQL | j00 (stringhe, placeholder), j06 (file di migrazione) |
| m03 Mutiny, `Uni`, `flatMap`, `@Blocking` | j04 (lambda), j07 (event loop), j09 (`Promise`) |
| m04 transazioni, `withTransaction` | j03 (eccezioni, try-with-resources), j09 (`recover`) |
| m05 sicurezza, JWT, `@RolesAllowed` | j05 (annotazioni), j03 (gerarchia di eccezioni) |
| m06 upload, `StreamingOutput`, xlsx | j06 (I/O, stream) |
| m07 test e progetto finale | j08 (Maven, JUnit) |

## Come lavorare

- Java: `java ExNN.java` (JDK 21). Un esercizio finisce quando stampa `OK`.
- Niente copia-incolla da AI: scrivi, sbaglia, leggi l'errore. Le soluzioni sono in fondo alla pagina: guardale solo dopo aver provato.
- Nei moduli Quarkus ogni esercizio ha il suo endpoint da chiamare con `curl`; il database di test parte con `mvn quarkus:dev` (Dev Services).
- Ogni sera: riscrivi a mente una funzione del giorno senza guardare.

Leggi i `README.md` dentro `java/` e `quarkus/` per setup e regole di ciascun percorso.
