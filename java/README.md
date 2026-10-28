# Piscina Java: le basi che servono per Quarkus

Stile 42: ogni modulo ha degli esercizi (`ex00`, `ex01`, ...) con una consegna precisa.
In fondo a ogni file c'è la sezione **Soluzioni**: guardala solo dopo aver provato davvero.

## Moduli

| Modulo | Tema | Dove lo ritrovi nel backend Quarkus |
|---|---|---|
| j00 | Basi: stringhe, array, `switch`, wrapper (`Integer`) | `checkValidInfo`, `split`, `String.join`, parametri `Integer` |
| j01 | OOP: classi, record, interfacce, enum | model/POJO (`GuestRegistration`, `LvmhUser`), `EmailValidator` |
| j02 | Collections, generics, `Optional`, `Iterable` | `List<T>`, `Map`, `RowSet`, `ifNull().failWith(...)` |
| j03 | Eccezioni | `CustomException`, `throws IOException`, exception mapper |
| j04 | Lambda e stream | `flatMap(isValid -> ...)`, `map`, tutto Mutiny |
| j05 | Annotazioni e reflection | `@Path`, `@Inject`, `@Claim`: come funzionano davvero |
| j06 | I/O e file | `Files.readAllBytes`, `StreamingOutput`, file Flyway |
| j07 | Concorrenza | event loop, `@Blocking`, `Uni` contro `Future` |
| j08 | Maven e JUnit | `pom.xml`, BOM, `@QuarkusTest` |
| j09 | Progetto: mini-Quarkus | DI + router + `Promise` (mini `Uni`) tutto insieme |

Ordine consigliato: j00 → j09. I moduli j00-j05 sono quelli che sbloccano la parte Quarkus.

## Setup

Serve solo JDK 21 e un editor. Nessun framework, nessuna libreria esterna fino a j08.

```bash
java -version      # deve dire 21
```

## Come si lavora

- Un esercizio = un file `ExNN.java` con una classe pubblica `ExNN` che ha una `main`.
- Si lancia così, senza compilare a mano: `java Ex00.java` (con argomenti: `java Ex00.java Luca`).
- **Ogni file è autonomo.** Non compilarli tutti insieme con `javac *.java`: file diversi
  definiscono classi con lo stesso nome (`Pool`, `Route`, ...).
- Per verificare, scrivi nella `main` dei `check(...)` sugli esempi della consegna e stampa `OK`
  alla fine. Aggiungi in fondo alla classe:

```java
static void check(boolean cond) {
    if (!cond) throw new AssertionError();
}
```

- Se un `check` fallisce, ottieni uno stack trace con il numero di riga: leggilo, non ignorarlo.

## Regole generali

- Niente copia-incolla da AI. Scrivi, rompi, leggi gli errori, correggi.
- Ogni modulo dice cosa è vietato (es. niente stream fino a j04): rispettalo, serve a imparare.
- La documentazione ufficiale è il primo posto dove guardare: docs.oracle.com/en/java/javase/21/docs/api
- Un esercizio è passato solo se: funziona sugli esempi, gestisce i casi limite (`null`, vuoto)
  e sai spiegare ad alta voce ogni riga che hai scritto.
- Nelle soluzioni i messaggi di errore sono in italiano senza accenti: è voluto, per evitare
  problemi di encoding nel terminale.
