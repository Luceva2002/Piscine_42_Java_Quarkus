# Modulo J06: I/O e file

Obiettivo: leggere e scrivere file, byte e stream, encoding. Nel backend: `Files.readAllBytes(file.filePath())`,
`StreamingOutput` per CSV/xlsx, nomi dei file di migrazione Flyway.

Regole del modulo: usa `java.nio.file` (`Path`, `Files`). Ogni test lavora in una **cartella temporanea**
(`Files.createTempDirectory`) e la pulisce alla fine, anche in caso di errore (`finally`).

---

## ex00: Testo su file

**File:** `Ex00.java`

- `static void writeLines(Path p, List<String> lines) throws IOException`
- `static List<String> readLines(Path p) throws IOException`

Poi **aggiungi** una riga in coda (`StandardOpenOption.APPEND`) e verifica che le righe siano diventate 4.
Verifica anche `Files.exists`, `Files.isRegularFile` e `getFileName()`.
Quale encoding usano di default `Files.write` e `Files.readAllLines`?

---

## ex01: Byte e copie

**File:** `Ex01.java`

Crea un file con **tutti i 256 valori di byte** (0..255) e scrivi `static void copy(Path from, Path to)`
con `readAllBytes` + `write`. Verifica con `Files.mismatch(a, b) == -1` (significa "identici").
Poi rifai la copia con `Files.copy(..., StandardCopyOption.REPLACE_EXISTING)`.

Perché `readAllBytes` va bene per un'immagine di evento ma sarebbe un problema per un file da 4 GB?

---

## ex02: Leggere in streaming

**File:** `Ex02.java`

`static int[] countLinesAndWords(Path p) throws IOException` ritorna `{righe, parole}` leggendo **una riga
alla volta** con `BufferedReader` dentro un `try-with-resources`.

Il file `"ciao a tutti\n\nfine\n"` ha 3 righe (la seconda è vuota) e 4 parole.

---

## ex03: `StreamingOutput` e CSV

**File:** `Ex03.java`

```java
@FunctionalInterface
interface StreamingOutput {
    void write(OutputStream os) throws IOException;
}
```

- `static StreamingOutput csv(List<Pool> pools)` scrive l'intestazione `id,name,city` e una riga per piscina
  con **nome e città tra virgolette** e le virgolette interne raddoppiate (regola del CSV)
- `static byte[] render(StreamingOutput out)` usa un `ByteArrayOutputStream` per ottenere i byte

Per `new Pool(2, "Il \"Delfino\"", "Milano")` la riga è `2,"Il ""Delfino""","Milano"`.
È lo stesso schema dell'endpoint `exportGuests` del progetto reale.

---

## ex04: Encoding

**File:** `Ex04.java`

Con la stringa `"perché"`:

1. quanti byte occupa in UTF-8 e quanti in ISO-8859-1? (verifica con `getBytes`)
2. cosa ottieni decodificando i byte UTF-8 **come** ISO-8859-1? (`"perchÃ©"`)
3. codifica i byte UTF-8 in Base64, decodificali e ricostruisci la stringa

Specifica **sempre** il `Charset` (`StandardCharsets.UTF_8`): è l'origine dei bug con le lettere accentate.

---

## ex05: I file di migrazione Flyway

**File:** `Ex05.java`

Flyway ordina i file `V<numero>__<descrizione>.sql` per **numero**, non per nome (`V10` viene dopo `V2`).

- `record Migration(int version, String description)` con `static Optional<Migration> parse(String fileName)`
  (regex `^V(\d+)__(.+)\.sql$`; gli underscore della descrizione diventano spazi; nome non valido → `Optional.empty()`)
- `static List<Migration> listMigrations(Path dir) throws IOException`: legge la cartella con
  `Files.newDirectoryStream`, ignora i file non validi, ordina per versione e lancia
  `IllegalStateException` se ci sono due file con la **stessa** versione

Esempio: file `V10__event_guest.sql`, `V2__seed_pool.sql`, `V1__create_pool.sql`, `README.md`, `V3_oops.sql`
→ versioni `[1, 2, 10]` (gli ultimi due sono ignorati), la prima descrizione è `"create pool"`.

---

## Verifica

- Sai spiegare la differenza tra leggere tutto in memoria e leggere in streaming.
- Sai dire perché `Files.readAllBytes` è bloccante (e perché non va sull'event loop: lo vedrai in J07).
- Sai spiegare cosa succede se leggi un file UTF-8 con l'encoding sbagliato.

<br><br><br>

---

# Soluzioni

Guardale solo **dopo** aver provato.

## ex00

```java
// Ex00.java
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class Ex00 {

    static void writeLines(Path p, List<String> lines) throws IOException {
        Files.write(p, lines);
    }

    static List<String> readLines(Path p) throws IOException {
        return Files.readAllLines(p);
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("piscina");
        Path f = dir.resolve("lista.txt");
        try {
            writeLines(f, List.of("uno", "due", "tre"));
            check(readLines(f).equals(List.of("uno", "due", "tre")));

            Files.writeString(f, "x\n", StandardOpenOption.APPEND);
            check(readLines(f).size() == 4);
            check(readLines(f).get(3).equals("x"));

            check(Files.exists(f));
            check(Files.isRegularFile(f));
            check(f.getFileName().toString().equals("lista.txt"));
        } finally {
            Files.deleteIfExists(f);
            Files.deleteIfExists(dir);
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

Entrambi usano UTF-8 (dal JDK 18 è anche l'encoding predefinito della JVM).

## ex01

```java
// Ex01.java
import java.io.IOException;
import java.nio.file.*;

public class Ex01 {

    static void copy(Path from, Path to) throws IOException {
        byte[] data = Files.readAllBytes(from);
        Files.write(to, data);
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("piscina");
        Path a = dir.resolve("a.bin");
        Path b = dir.resolve("b.bin");
        Path c = dir.resolve("c.bin");
        try {
            byte[] data = new byte[256];
            for (int i = 0; i < 256; i++) data[i] = (byte) i;
            Files.write(a, data);

            copy(a, b);
            check(Files.size(b) == 256);
            check(Files.mismatch(a, b) == -1);

            Files.copy(a, c, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(a, c, StandardCopyOption.REPLACE_EXISTING);   // seconda volta: nessun errore
            check(Files.mismatch(a, c) == -1);

            Files.write(c, new byte[]{1});
            check(Files.mismatch(a, c) == 0);                         // differiscono dal primo byte
        } finally {
            Files.deleteIfExists(a);
            Files.deleteIfExists(b);
            Files.deleteIfExists(c);
            Files.deleteIfExists(dir);
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

`readAllBytes` carica **tutto il file in memoria** (un `byte[]` lungo quanto il file): ok per
un'immagine di poche centinaia di KB, non per file enormi. In quel caso si copiano
a blocchi con gli stream.

## ex02

```java
// Ex02.java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;

public class Ex02 {

    static int[] countLinesAndWords(Path p) throws IOException {
        int lines = 0;
        int words = 0;
        try (BufferedReader reader = Files.newBufferedReader(p)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines++;
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    words += trimmed.split("\\s+").length;
                }
            }
        }
        return new int[]{lines, words};
    }

    public static void main(String[] args) throws Exception {
        Path f = Files.createTempFile("piscina", ".txt");
        try {
            Files.writeString(f, "ciao a tutti\n\nfine\n");
            int[] result = countLinesAndWords(f);
            check(result[0] == 3);
            check(result[1] == 4);
        } finally {
            Files.deleteIfExists(f);
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

## ex03

```java
// Ex03.java
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Ex03 {

    static String esc(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    static StreamingOutput csv(List<Pool> pools) {
        return os -> {
            Writer w = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
            w.write("id,name,city\n");
            for (Pool p : pools) {
                w.write(p.id() + "," + esc(p.name()) + "," + esc(p.city()) + "\n");
            }
            w.flush();
        };
    }

    static byte[] render(StreamingOutput out) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        out.write(bos);
        return bos.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        List<Pool> pools = List.of(new Pool(1, "Blu, Club", "Roma"), new Pool(2, "Il \"Delfino\"", "Milano"));
        String text = new String(render(csv(pools)), StandardCharsets.UTF_8);
        check(text.equals("id,name,city\n1,\"Blu, Club\",\"Roma\"\n2,\"Il \"\"Delfino\"\"\",\"Milano\"\n"));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

@FunctionalInterface
interface StreamingOutput {
    void write(OutputStream os) throws IOException;
}

record Pool(int id, String name, String city) {}
```

Il flush finale è importante: `BufferedWriter` tiene i dati in un buffer, e se non lo svuoti i
byte non arrivano mai all'`OutputStream`.

## ex04

```java
// Ex04.java
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Ex04 {

    public static void main(String[] args) {
        String s = "perché";   // "perché"

        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        byte[] latin1 = s.getBytes(StandardCharsets.ISO_8859_1);
        check(s.length() == 6);
        check(utf8.length == 7);     // la "e" accentata occupa 2 byte in UTF-8
        check(latin1.length == 6);   // 1 byte per carattere in ISO-8859-1

        String mojibake = new String(utf8, StandardCharsets.ISO_8859_1);
        check(mojibake.equals("perchÃ©"));   // "perchÃ©"
        check(new String(utf8, StandardCharsets.UTF_8).equals(s));

        String b64 = Base64.getEncoder().encodeToString(utf8);
        check(b64.equals("cGVyY2jDqQ=="));
        check(new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8).equals(s));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}
```

Il "mojibake" `perchÃ©` è quello che vedi quando un testo UTF-8 viene interpretato come Latin-1.
Nei sorgenti qui si usa `é` per non dipendere dall'encoding del terminale.

## ex05

```java
// Ex05.java
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Ex05 {

    static List<Migration> listMigrations(Path dir) throws IOException {
        List<Migration> out = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
            for (Path p : files) {
                Migration.parse(p.getFileName().toString()).ifPresent(out::add);
            }
        }
        Collections.sort(out);
        for (int i = 1; i < out.size(); i++) {
            if (out.get(i).version() == out.get(i - 1).version()) {
                throw new IllegalStateException("versione duplicata: " + out.get(i).version());
            }
        }
        return out;
    }

    static void touch(Path dir, String... names) throws IOException {
        for (String n : names) Files.createFile(dir.resolve(n));
    }

    static void cleanup(Path dir) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
            for (Path p : files) Files.delete(p);
        }
        Files.delete(dir);
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("piscina");
        try {
            touch(dir, "V10__event_guest.sql", "V2__seed_pool.sql", "V1__create_pool.sql", "README.md", "V3_oops.sql");
            List<Migration> migrations = listMigrations(dir);
            check(migrations.size() == 3);
            check(migrations.get(0).version() == 1);
            check(migrations.get(0).description().equals("create pool"));
            check(migrations.get(1).version() == 2);
            check(migrations.get(2).version() == 10);
            check(migrations.get(2).description().equals("event guest"));
        } finally {
            cleanup(dir);
        }

        Path dup = Files.createTempDirectory("piscina");
        try {
            touch(dup, "V2__a.sql", "V2__b.sql");
            try {
                listMigrations(dup);
                throw new AssertionError();
            } catch (IllegalStateException expected) {
                // ok
            }
        } finally {
            cleanup(dup);
        }
        check(Migration.parse("V1__x.txt").isEmpty());
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

record Migration(int version, String description) implements Comparable<Migration> {
    private static final Pattern NAME = Pattern.compile("^V(\\d+)__(.+)\\.sql$");

    static Optional<Migration> parse(String fileName) {
        Matcher m = NAME.matcher(fileName);
        if (!m.matches()) return Optional.empty();
        return Optional.of(new Migration(Integer.parseInt(m.group(1)), m.group(2).replace('_', ' ')));
    }

    @Override
    public int compareTo(Migration other) {
        return Integer.compare(version, other.version);
    }
}
```

Ordinare per **stringa** metterebbe `V10__...` prima di `V2__...`: ecco perché Flyway
(e questo esercizio) estrae il numero e confronta numeri.
