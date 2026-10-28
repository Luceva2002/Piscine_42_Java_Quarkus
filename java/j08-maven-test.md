# Modulo J08: Maven e test con JUnit 5

Obiettivo: finora hai usato `java ExNN.java`. Un progetto vero (come il tuo `pom.xml` Quarkus) ha
dipendenze, un ciclo di build e test automatici. Qui crei un piccolo progetto Maven **da zero**,
senza Quarkus, e scrivi test con JUnit 5. In Quarkus l'unica differenza è `@QuarkusTest`.

Da questo modulo in poi puoi usare librerie esterne (Maven le scarica).

Requisiti: `mvn -v` deve rispondere (Maven 3.9+) e `java -version` deve dire 21.

Questo modulo **non** usa i file standalone: i soluzioni sono file di progetto (`pom.xml`, `src/...`).

---

## ex00: Il primo progetto

Crea la cartella `mini-app/` con questa struttura (la convenzione Maven è fissa):

```
mini-app/
  pom.xml
  src/main/java/it/synergica/Slug.java
  src/test/java/it/synergica/SlugTest.java
```

Scrivi il `pom.xml` con:

- `groupId` `it.synergica`, `artifactId` `mini-app`, `version` `1.0.0-SNAPSHOT`
- `maven.compiler.release` = 21 e `project.build.sourceEncoding` = UTF-8 (come proprietà)
- la dipendenza `org.junit.jupiter:junit-jupiter` (scope `test`)
- `maven-surefire-plugin` (serve a eseguire i test JUnit 5)

Poi: `mvn -q compile` deve finire senza errori (anche con la sola classe `Slug` vuota).

Domande:
1. In che cartella finiscono i `.class`?
2. Cosa fa `mvn test` rispetto a `mvn compile`? E `mvn package`?
3. Nel tuo `pom.xml` Quarkus, cosa fa il `<dependencyManagement>` con il BOM `quarkus-bom`?
   Perché le dipendenze Quarkus **non** hanno `<version>`?

---

## ex01: Il primo test

**File:** `src/main/java/it/synergica/Slug.java`

`public static String of(String text)`: trasforma un titolo in uno "slug" per un URL.

- minuscolo, spazi (e gruppi di caratteri non alfanumerici) → un solo `-`
- niente `-` all'inizio o alla fine
- `null` → `IllegalArgumentException`

`Slug.of("  Festa di Natale 2026!  ")` → `"festa-di-natale-2026"`

**File:** `src/test/java/it/synergica/SlugTest.java`

Scrivi almeno 3 test con `@Test` e `assertEquals`, `assertThrows`. Lancia `mvn test`.

Rompi di proposito un test e leggi il rapporto di Surefire: capisci **cosa** era atteso e **cosa** è arrivato.

---

## ex02: Test parametrizzati

Aggiungi la dipendenza `org.junit.jupiter:junit-jupiter-params` (lo stesso numero di versione!)
oppure usa direttamente l'aggregatore `junit-jupiter` che la include già.

Riscrivi i tuoi test con `@ParameterizedTest` + `@CsvSource` (almeno 6 casi: accenti, simboli, spazi multipli, vuoto...).
Per gli accenti decidi tu la regola (ad esempio `"è"` → `"e"`) e implementala con `java.text.Normalizer`.

---

## ex03: Il ciclo di vita dei test

Crea `PoolRepoTest` per una classe `PoolRepo` in memoria (`add`, `findById`, `count`).

Usa `@BeforeEach` per creare un repo **nuovo** prima di ogni test, e verifica che l'ordine di esecuzione
dei test non conti (ogni test deve funzionare da solo). Prova anche `@Disabled` e `@DisplayName`.

Domanda: cosa succede ai tuoi test se il repo è `static` e condiviso? Fai l'esperimento.

---

## ex04: Mockare senza librerie

Una `NotificationService` dipende dall'interfaccia `Mailer` (`void send(String to, String body)`).
Scrivi un `FakeMailer` (nel test) che registra le chiamate in una lista, e verifica che `service.welcome("a@b.it")`
mandi **una** mail con il destinatario giusto.

Questo è ciò che in Quarkus fai con `@InjectMock` (Mockito). Capisci prima la versione manuale.

---

## ex05: Dal progetto Maven a Quarkus (lettura)

Apri il `pom.xml` del progetto reale e rispondi per iscritto:

1. Quale plugin trasforma il progetto in un'applicazione Quarkus (`quarkus-maven-plugin`) e quali goal usa?
2. Perché ci sono `quarkus-junit5` e `rest-assured` nello scope `test`?
3. Cosa cambierebbe in `SlugTest` per farlo diventare un `@QuarkusTest`? (Nulla di necessario: perché in quel caso è uno spreco?)
4. Cosa fa `mvn quarkus:dev` che `mvn test` non fa?

---

## Verifica

- Sai creare da zero un progetto Maven, compilare, testare, impacchettare.
- Sai scrivere test unitari che non dipendono da altri test.
- Sai spiegare cosa fa un BOM.

<br><br><br>

---

# Soluzioni

Guardale solo **dopo** aver provato. Qui non c'è la convenzione `// ExNN.java`: sono file di progetto.

## ex00

`pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>it.synergica</groupId>
  <artifactId>mini-app</artifactId>
  <version>1.0.0-SNAPSHOT</version>

  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <junit.version>5.11.4</junit.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>${junit.version}</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.5.2</version>
      </plugin>
    </plugins>
  </build>
</project>
```

Risposte:

1. In `target/classes` (main) e `target/test-classes` (test).
2. `mvn compile` compila solo `src/main`. `mvn test` compila anche i test e li esegue. `mvn package` fa tutto
   questo e poi crea il jar in `target/`. Sono fasi di un **ciclo** (`compile` → `test` → `package`): chiedere la fase
   successiva esegue anche le precedenti.
3. Il BOM (`quarkus-bom`) è un elenco di **versioni compatibili** tra loro per tutte le librerie Quarkus.
   Importandolo in `dependencyManagement`, ogni dipendenza può omettere `<version>` e Maven usa quella del BOM,
   evitando miscele incompatibili. Cambiare versione di Quarkus = cambiare una sola proprietà.

## ex01

`src/main/java/it/synergica/Slug.java`:

```java
package it.synergica;

import java.util.Locale;

public final class Slug {

    private Slug() {}

    public static String of(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text is null");
        }
        String s = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return s;
    }
}
```

`src/test/java/it/synergica/SlugTest.java`:

```java
package it.synergica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SlugTest {

    @Test
    void basic() {
        assertEquals("festa-di-natale-2026", Slug.of("  Festa di Natale 2026!  "));
    }

    @Test
    void collapsesSeparators() {
        assertEquals("a-b-c", Slug.of("a---b   c"));
    }

    @Test
    void nullIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Slug.of(null));
    }
}
```

`assertEquals(atteso, ottenuto)`: l'**atteso** va per primo; se li scambi i messaggi di errore risultano invertiti.

## ex02

Versione con accenti (`Normalizer` scompone `è` in `e` + accento, poi si eliminano gli accenti):

```java
package it.synergica;

import java.text.Normalizer;
import java.util.Locale;

public final class Slug {

    private Slug() {}

    public static String of(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text is null");
        }
        String noAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return noAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
```

```java
package it.synergica;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlugParamTest {

    @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "Festa di Natale|festa-di-natale",
            "  tanti   spazi  |tanti-spazi",
            "Perché sì?|perche-si",
            "Città & Co.|citta-co",
            "A1 B2|a1-b2",
            "---|''"
    })
    void slug(String input, String expected) {
        assertEquals(expected, Slug.of(input));
    }
}
```

In `@CsvSource` il valore `''` indica la stringa vuota (invece `null` è un campo senza niente tra i separatori).

## ex03

```java
package it.synergica;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PoolRepo {
    private final Map<Integer, String> data = new HashMap<>();
    private int seq = 0;

    public int add(String name) {
        data.put(++seq, name);
        return seq;
    }

    public Optional<String> findById(int id) {
        return Optional.ofNullable(data.get(id));
    }

    public int count() {
        return data.size();
    }
}
```

```java
package it.synergica;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PoolRepoTest {

    private PoolRepo repo;

    @BeforeEach
    void setUp() {
        repo = new PoolRepo();      // nuovo prima di OGNI test
    }

    @Test
    @DisplayName("un repo nuovo e' vuoto")
    void startsEmpty() {
        assertEquals(0, repo.count());
    }

    @Test
    void addAndFind() {
        int id = repo.add("Blu");
        assertEquals("Blu", repo.findById(id).orElseThrow());
        assertEquals(1, repo.count());
    }

    @Test
    void missingId() {
        assertTrue(repo.findById(42).isEmpty());
    }

    @Test
    @Disabled("esempio di test saltato")
    void notYet() {
        fail("non dovrebbe girare");
    }
}
```

Con un repo `static` condiviso, `startsEmpty` fallirebbe o no a seconda dell'ordine in cui JUnit esegue i test:
sono i test "flaky" (instabili). JUnit crea di default **una nuova istanza della classe di test per ogni metodo**,
proprio per scoraggiare lo stato condiviso.

## ex04

```java
package it.synergica;

public interface Mailer {
    void send(String to, String body);
}
```

```java
package it.synergica;

public class NotificationService {
    private final Mailer mailer;

    public NotificationService(Mailer mailer) {
        this.mailer = mailer;
    }

    public void welcome(String email) {
        mailer.send(email, "Benvenuto!");
    }
}
```

```java
package it.synergica;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    static class FakeMailer implements Mailer {
        final List<String> sent = new ArrayList<>();

        @Override
        public void send(String to, String body) {
            sent.add(to + "|" + body);
        }
    }

    @Test
    void welcomeSendsOneMail() {
        FakeMailer mailer = new FakeMailer();
        new NotificationService(mailer).welcome("a@b.it");
        assertEquals(List.of("a@b.it|Benvenuto!"), mailer.sent);
    }
}
```

Il service riceve il `Mailer` dal costruttore: questo è il motivo per cui si preferisce l'iniezione da costruttore
a `new` interni (in Quarkus `@Inject` fa lo stesso lavoro, e `@InjectMock` sostituisce il bean con un mock).

## ex05

Risposte guida:

1. `quarkus-maven-plugin` con i goal `build` (crea l'applicazione ottimizzata, es. `target/quarkus-app/`),
   `generate-code` e `generate-code-tests`. Il goal `dev` avvia la modalità sviluppo.
2. `quarkus-junit5` fornisce `@QuarkusTest` (avvia l'applicazione vera per il test). `rest-assured` serve a
   chiamare gli endpoint HTTP e verificare le risposte, senza dover scrivere un client a mano.
3. `SlugTest` non ha dipendenze CDI, non tocca HTTP né DB: con `@QuarkusTest` pagheresti l'avvio di tutto Quarkus
   (secondi) senza guadagno. Si usa `@QuarkusTest` solo quando il test ha bisogno del container (CDI, endpoint, datasource).
4. `mvn quarkus:dev` avvia l'app con **live reload**: modifichi il codice e la richiesta successiva usa quello nuovo;
   in più esegue i test in modo continuo (premendo `r`). `mvn test` esegue i test una volta e termina.
