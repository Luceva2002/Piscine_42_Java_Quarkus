# Modulo 06: Upload e download di file

Obiettivo: multipart in ingresso, file generati in uscita.

Estensione da aggiungere per xlsx: `io.quarkiverse.poi:quarkus-poi`
(guarda la versione nel `pom.xml` del progetto reale).

---

## ex00: Upload semplice

**File:** `UploadResource.java`

`POST /upload` multipart con un campo `file`. Ritorna nome originale e dimensione in byte.

```bash
curl -F "file=@foto.png" localhost:8080/upload
```

Da usare: `@RestForm("file") FileUpload file`, `@Consumes(MULTIPART_FORM_DATA)`,
`file.fileName()`, `file.size()`.

---

## ex01: Upload con parte JSON

`POST /pools/{id}/photo`: campo `file` (immagine) e campo `meta` (JSON con `title`).

```java
@RestForm("meta") @PartType(MediaType.APPLICATION_JSON) PhotoMeta meta
```

```bash
curl -F "file=@foto.png" -F 'meta={"title":"vasca"};type=application/json' ...
```

Salva i byte in una colonna `bytea` (migrazione nuova). Leggi il file con
`Files.readAllBytes(file.filePath())`.
Questa chiamata e' bloccante: come la gestisci? (ripensa a m03/ex04: `@Blocking`)

---

## ex02: Download dell'immagine

`GET /pools/{id}/photo` ritorna i byte con `Content-Type` corretto, 404 se manca.
Da usare: `Response.ok(bytes).type("image/png")`.

---

## ex03: Export CSV in streaming

`GET /pools/export.csv` genera un CSV di tutte le piscine con `StreamingOutput`.

```java
StreamingOutput out = os -> { /* scrivi righe su os */ };
return Response.ok(out).header("Content-Disposition", "attachment; filename=pools.csv").build();
```

Prima la SELECT (Uni), poi la scrittura. Attenzione agli apici e alle virgole nei valori.

---

## ex04: Export xlsx

Come ex03 ma in formato Excel con Apache POI (`XSSFWorkbook`):
intestazione in grassetto, una riga per piscina, larghezza colonne automatica.
Content-Type: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.

---

## Verifica

- Il file scaricato si apre in Excel/LibreOffice senza errori.
- Sai spiegare perche' `Files.readAllBytes` non deve girare sull'event loop.

<br><br><br>

---

# Soluzioni

Guardale solo **dopo** aver provato.

## ex00

```java
@Path("/upload")
public class UploadResource {

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> upload(@RestForm("file") FileUpload file) {
        return Map.of("name", file.fileName(), "size", file.size());
    }
}
```

## ex01

`V6__pool_photo.sql`
```sql
ALTER TABLE pool ADD COLUMN photo bytea, ADD COLUMN photo_type text;
```

```java
public record PhotoMeta(String title) {}
```

```java
@POST
@Path("/pools/{id}/photo")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Blocking // Files.readAllBytes e' bloccante: via dall'event loop
public Uni<Boolean> uploadPhoto(
        @RestPath Integer id,
        @RestForm("file") FileUpload file,
        @RestForm("meta") @PartType(MediaType.APPLICATION_JSON) PhotoMeta meta) throws IOException {

    byte[] bytes = Files.readAllBytes(file.filePath());

    Tuple params = Tuple.tuple()
            .addBuffer(Buffer.buffer(bytes)) // io.vertx.mutiny.core.buffer.Buffer
            .addString(file.contentType())
            .addInteger(id);

    return client.preparedQuery("UPDATE pool SET photo = $1, photo_type = $2 WHERE id = $3")
            .execute(params)
            .map(rows -> rows.rowCount() > 0);
}
```

```bash
curl -F "file=@foto.png" -F 'meta={"title":"vasca"};type=application/json' \
  -H "Authorization: Bearer $TOKEN" localhost:8080/pools/1/photo
```

## ex02

```java
@GET
@Path("/pools/{id}/photo")
public Uni<Response> photo(@RestPath Integer id) {
    return client.preparedQuery("SELECT photo, photo_type FROM pool WHERE id = $1")
            .execute(Tuple.of(id))
            .map(rows -> {
                if (!rows.iterator().hasNext()) return Response.status(404).build();
                Row r = rows.iterator().next();
                Buffer photo = r.getBuffer("photo");
                if (photo == null) return Response.status(404).build();
                return Response.ok(photo.getBytes()).type(r.getString("photo_type")).build();
            });
}
```

## ex03

```java
@GET
@Path("/export.csv")
@Produces("text/csv")
public Uni<Response> csv() {
    return repo.search(null, null, null).map(pools -> {
        StreamingOutput out = os -> {
            Writer w = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
            w.write("id,name,city,capacity,open\n");
            for (Pool p : pools) {
                w.write(p.id + "," + esc(p.name) + "," + esc(p.city) + ","
                        + p.capacity + "," + p.open + "\n");
            }
            w.flush();
        };
        return Response.ok(out)
                .header("Content-Disposition", "attachment; filename=pools.csv")
                .build();
    });
}

private static String esc(String s) {
    return "\"" + s.replace("\"", "\"\"") + "\"";
}
```

`/pools/export.csv` convive con `/pools/{id}`: il percorso letterale ha la precedenza.
Racchiudere i valori tra apici e raddoppiare gli apici interni e' la regola del CSV per
gestire virgole e virgolette nel testo.

## ex04

Metti questo codice in una classe a parte: `Row` di POI e `Row` di Vert.x hanno lo stesso nome.

```java
public class PoolXlsx {

    public static StreamingOutput write(List<Pool> pools) {
        return os -> {
            try (XSSFWorkbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("piscine");

                Font font = wb.createFont();
                font.setBold(true);
                CellStyle bold = wb.createCellStyle();
                bold.setFont(font);

                String[] head = {"id", "name", "city", "capacity", "open"};
                org.apache.poi.ss.usermodel.Row h = sheet.createRow(0);
                for (int i = 0; i < head.length; i++) {
                    Cell c = h.createCell(i);
                    c.setCellValue(head[i]);
                    c.setCellStyle(bold);
                }

                int rowIdx = 1;
                for (Pool p : pools) {
                    org.apache.poi.ss.usermodel.Row r = sheet.createRow(rowIdx++);
                    r.createCell(0).setCellValue(p.id);
                    r.createCell(1).setCellValue(p.name);
                    r.createCell(2).setCellValue(p.city);
                    r.createCell(3).setCellValue(p.capacity);
                    r.createCell(4).setCellValue(p.open);
                }
                for (int i = 0; i < head.length; i++) sheet.autoSizeColumn(i);

                wb.write(os);
            }
        };
    }
}
```

```java
@GET
@Path("/export.xlsx")
@Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
public Uni<Response> xlsx() {
    return repo.search(null, null, null).map(pools ->
            Response.ok(PoolXlsx.write(pools))
                    .header("Content-Disposition", "attachment; filename=pools.xlsx")
                    .build());
}
```

Perche' `Files.readAllBytes` non va sull'event loop: legge dal disco e aspetta. Finche'
aspetta, quel thread non serve nessun'altra richiesta.
