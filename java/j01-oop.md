# Modulo J01: Programmazione a oggetti

Obiettivo: classi, POJO, `equals`/`hashCode`, record, interfacce, enum, immutabilità.
Sono i mattoni dei model (`GuestRegistration`, `FendiEvent`) e dei bean (`EmailValidator`).

Regole del modulo: niente stream, niente lambda. Ogni esercizio è un file `ExNN.java` autonomo
(le classi di supporto vanno nello stesso file, dopo la classe `ExNN`).

---

## ex00: La prima classe

**File:** `Ex00.java`

Classe `Pool` con campi **privati** `id`, `name`, `city`, `capacity`, un costruttore, i getter
(`getName()`, ...) e `toString()`. La capienza negativa lancia `IllegalArgumentException`.

```java
Pool p = new Pool(1, "Blu Club", "Milano", 80);
p.toString()   // "Pool{id=1, name='Blu Club', city='Milano', capacity=80}"
```

I campi sono `final`: cosa cambia rispetto a non metterlo?

---

## ex01: POJO come nel progetto reale

**File:** `Ex01.java`

Scrivi due versioni di un ospite:

1. `GuestPublic`: campi **pubblici** `firstName`, `lastName`, `email`, `phone` (stringhe),
   `Integer id` e `int guests`, nessun costruttore scritto da te
2. `GuestBean`: campo privato `email` con `getEmail()` / `setEmail(...)`

Verifica i valori di default dei campi appena creati (`id` e `phone` sono `null`, `guests` è `0`).
Domanda: perché Jackson (JSON ↔ oggetto) ha bisogno di un costruttore senza argomenti?

---

## ex02: `equals` e `hashCode`

**File:** `Ex02.java`

Due `Guest` sono uguali se hanno la stessa email, **ignorando maiuscole e minuscole**.

```java
new Guest("A@x.it").equals(new Guest("a@X.IT"))   // true
```

Verifica con un `HashSet`: inserendo `"A@x.it"`, `"a@X.IT"` e `"c@x.it"` devono restare 2 elementi.
Regola da saper spiegare: due oggetti uguali devono avere lo stesso `hashCode`.

---

## ex03: Record

**File:** `Ex03.java`

`record PoolDto(String name, String city, int capacity)` con:

- un **compact constructor** che rifiuta nome vuoto e capienza negativa (`IllegalArgumentException`)
- un metodo statico `PoolDto.of(name, city)` che usa capienza 0

Verifica `equals`, `toString` e gli accessor (`dto.name()`, non `getName()`).
Differenza con la classe dell'ex00: cosa genera il compilatore per te?

---

## ex04: Interfacce e polimorfismo

**File:** `Ex04.java`

```java
interface Validator<T> {
    boolean isValid(T value);
    default String name() { return getClass().getSimpleName(); }
}
```

Scrivi tre implementazioni per `String`: `NotBlankValidator`, `EmailFormatValidator`
(una `@`, qualcosa prima, un punto nel dominio), `PhoneValidator` (opzionale `+` e da 6 a 15 cifre).
Poi `static List<String> failures(String value, List<Validator<String>> validators)` che ritorna
i `name()` dei validatori che **falliscono**.

```java
failures("abc", List.of(new NotBlankValidator(), new EmailFormatValidator()))
// ["EmailFormatValidator"]
```

---

## ex05: Enum

**File:** `Ex05.java`

`enum EventStatus` con tre valori e un codice intero: `REGISTRATION_OPEN(1)`,
`REGISTRATION_CLOSED(2)`, `EVENT_CLOSED(3)`.

- `int code()`
- `static EventStatus fromCode(int code)`: codice sconosciuto → `IllegalArgumentException`
- `boolean acceptsGuests()`: vero solo per `REGISTRATION_OPEN`

È il modo corretto di rappresentare lo `statusId` che nel progetto reale viaggia come `Integer`.

---

## ex06: Immutabilità e `static`

**File:** `Ex06.java`

Classe `final` `Event` con `title` e `status` finali e metodi "wither" che **non modificano**
l'oggetto ma ne ritornano uno nuovo: `withTitle(String)`, `withStatus(int)`.
Aggiungi un contatore `static int created` che conta quante istanze sono state costruite
(`Event.created()`).

Domanda: perché un oggetto immutabile è più sicuro da condividere tra richieste concorrenti?

---

## Verifica

- Sai spiegare la differenza tra campo `public` e getter/setter, e quando va bene l'uno o l'altro.
- Sai dire cosa succede in un `HashSet` se sovrascrivi `equals` ma non `hashCode`.
- Sai dire quando preferire un `record` a una classe.

<br><br><br>

---

# Soluzioni

Guardale solo **dopo** aver provato.

## ex00

```java
// Ex00.java
public class Ex00 {

    public static void main(String[] args) {
        Pool p = new Pool(1, "Blu Club", "Milano", 80);
        check(p.getName().equals("Blu Club"));
        check(p.getCapacity() == 80);
        check(p.toString().equals("Pool{id=1, name='Blu Club', city='Milano', capacity=80}"));
        try {
            new Pool(2, "X", "Y", -1);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
            // ok
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

class Pool {
    private final int id;
    private final String name;
    private final String city;
    private final int capacity;

    Pool(int id, String name, String city, int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("capienza negativa");
        this.id = id;
        this.name = name;
        this.city = city;
        this.capacity = capacity;
    }

    int getId() { return id; }
    String getName() { return name; }
    String getCity() { return city; }
    int getCapacity() { return capacity; }

    @Override
    public String toString() {
        return "Pool{id=" + id + ", name='" + name + "', city='" + city + "', capacity=" + capacity + "}";
    }
}
```

Con `final` il campo si assegna una sola volta (nel costruttore): l'oggetto non può essere
modificato dopo la creazione.

## ex01

```java
// Ex01.java
public class Ex01 {

    public static void main(String[] args) {
        GuestPublic g = new GuestPublic();
        check(g.id == null);
        check(g.phone == null);
        check(g.guests == 0);
        g.email = "a@x.it";
        check(g.email.equals("a@x.it"));

        GuestBean b = new GuestBean();
        check(b.getEmail() == null);
        b.setEmail("b@x.it");
        check(b.getEmail().equals("b@x.it"));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

class GuestPublic {
    public Integer id;
    public String firstName;
    public String lastName;
    public String email;
    public String phone;
    public int guests;
}

class GuestBean {
    private String email;

    String getEmail() { return email; }
    void setEmail(String email) { this.email = email; }
}
```

Jackson crea l'oggetto con il costruttore vuoto e poi riempie i campi (direttamente se
sono pubblici, con i setter altrimenti). Se la classe non ha un costruttore vuoto non sa
come istanziarla. I tipi `Integer` partono da `null`, gli `int` da `0`.

## ex02

```java
// Ex02.java
import java.util.*;

public class Ex02 {

    public static void main(String[] args) {
        Guest a = new Guest("A@x.it");
        Guest b = new Guest("a@X.IT");
        Guest c = new Guest("c@x.it");
        check(a.equals(b));
        check(a.hashCode() == b.hashCode());
        check(!a.equals(c));
        check(!a.equals(null));
        Set<Guest> set = new HashSet<>(List.of(a, b, c));
        check(set.size() == 2);
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

class Guest {
    private final String email;

    Guest(String email) {
        this.email = email;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Guest g)) return false;
        return email.equalsIgnoreCase(g.email);
    }

    @Override
    public int hashCode() {
        return email.toLowerCase(Locale.ROOT).hashCode();
    }
}
```

Se sovrascrivi solo `equals`, due ospiti "uguali" finiscono in "bucket" diversi dell'`HashSet`
perché hanno `hashCode` diversi, e il set li tratta come elementi distinti.

## ex03

```java
// Ex03.java
public class Ex03 {

    public static void main(String[] args) {
        PoolDto a = new PoolDto("Blu", "Roma", 3);
        PoolDto b = new PoolDto("Blu", "Roma", 3);
        check(a.equals(b));
        check(a.hashCode() == b.hashCode());
        check(a.toString().equals("PoolDto[name=Blu, city=Roma, capacity=3]"));
        check(a.name().equals("Blu"));
        check(PoolDto.of("Delfino", "Torino").capacity() == 0);
        try {
            new PoolDto(" ", "Roma", 1);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            new PoolDto("Blu", "Roma", -5);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
            // ok
        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

record PoolDto(String name, String city, int capacity) {

    PoolDto {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("nome obbligatorio");
        if (capacity < 0) throw new IllegalArgumentException("capienza negativa");
    }

    static PoolDto of(String name, String city) {
        return new PoolDto(name, city, 0);
    }
}
```

Il compilatore genera per un record: costruttore, accessor (`name()`), `equals`, `hashCode`,
`toString`. I campi sono sempre `final`.

## ex04

```java
// Ex04.java
import java.util.*;

public class Ex04 {

    static List<String> failures(String value, List<Validator<String>> validators) {
        List<String> out = new ArrayList<>();
        for (Validator<String> v : validators) {
            if (!v.isValid(value)) out.add(v.name());
        }
        return out;
    }

    public static void main(String[] args) {
        check(failures("abc", List.of(new NotBlankValidator(), new EmailFormatValidator()))
                .equals(List.of("EmailFormatValidator")));
        check(failures("a@b.it", List.of(new NotBlankValidator(), new EmailFormatValidator())).isEmpty());
        check(failures("+39061234", List.of(new PhoneValidator())).isEmpty());
        check(failures("12", List.of(new PhoneValidator())).equals(List.of("PhoneValidator")));
        check(failures(null, List.of(new NotBlankValidator())).equals(List.of("NotBlankValidator")));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

interface Validator<T> {
    boolean isValid(T value);

    default String name() {
        return getClass().getSimpleName();
    }
}

class NotBlankValidator implements Validator<String> {
    public boolean isValid(String v) {
        return v != null && !v.isBlank();
    }
}

class EmailFormatValidator implements Validator<String> {
    public boolean isValid(String v) {
        if (v == null) return false;
        String[] parts = v.split("@", -1);
        return parts.length == 2 && !parts[0].isEmpty() && parts[1].contains(".");
    }
}

class PhoneValidator implements Validator<String> {
    public boolean isValid(String v) {
        return v != null && v.matches("\\+?\\d{6,15}");
    }
}
```

I metodi che implementano un'interfaccia devono essere `public`. Il chiamante conosce solo
`Validator<String>`: è il polimorfismo (stesso principio dell'iniezione di un bean per interfaccia).

## ex05

```java
// Ex05.java
public class Ex05 {

    public static void main(String[] args) {
        check(EventStatus.fromCode(2) == EventStatus.REGISTRATION_CLOSED);
        check(EventStatus.EVENT_CLOSED.code() == 3);
        check(EventStatus.valueOf("EVENT_CLOSED") == EventStatus.EVENT_CLOSED);
        check(EventStatus.REGISTRATION_OPEN.acceptsGuests());
        check(!EventStatus.EVENT_CLOSED.acceptsGuests());
        try {
            EventStatus.fromCode(9);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
            // ok
        }
        String label = switch (EventStatus.fromCode(1)) {
            case REGISTRATION_OPEN -> "aperte";
            case REGISTRATION_CLOSED -> "chiuse";
            case EVENT_CLOSED -> "finito";
        };
        check(label.equals("aperte"));
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

enum EventStatus {
    REGISTRATION_OPEN(1),
    REGISTRATION_CLOSED(2),
    EVENT_CLOSED(3);

    private final int code;

    EventStatus(int code) {
        this.code = code;
    }

    int code() {
        return code;
    }

    boolean acceptsGuests() {
        return this == REGISTRATION_OPEN;
    }

    static EventStatus fromCode(int code) {
        for (EventStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("codice sconosciuto: " + code);
    }
}
```

Lo `switch` sull'enum non ha bisogno di `default` se copre tutti i valori: se aggiungi un valore
all'enum, il compilatore ti segnala tutti gli `switch` da aggiornare.

## ex06

```java
// Ex06.java
public class Ex06 {

    public static void main(String[] args) {
        Event e1 = new Event("Sfilata", 1);
        Event e2 = e1.withTitle("Sfilata 2027");
        check(e1.title().equals("Sfilata"));
        check(e2.title().equals("Sfilata 2027"));
        check(e2.status() == 1);
        check(Event.created() == 2);
        Event e3 = e2.withStatus(3);
        check(e2.status() == 1);
        check(e3.status() == 3);
        check(Event.created() == 3);
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}

final class Event {
    private static int created = 0;

    private final String title;
    private final int status;

    Event(String title, int status) {
        this.title = title;
        this.status = status;
        created++;
    }

    String title() { return title; }
    int status() { return status; }

    Event withTitle(String newTitle) { return new Event(newTitle, status); }
    Event withStatus(int newStatus) { return new Event(title, newStatus); }

    static int created() { return created; }
}
```

Un oggetto immutabile non cambia mai dopo la creazione, quindi più thread possono leggerlo
senza sincronizzazione. Nota che il contatore `static` invece è stato condiviso e **non** è
thread-safe: lo risolverai in J07.
