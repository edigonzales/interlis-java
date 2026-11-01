# Unabhängige Gegenprobe WEAK-01: schwache HTTP-Validatoren

## Umfang und Urteil

- Kandidat: `cf585ec1ea2270bafed4083a8ebfa2e68de05133`
- Basis: `fd07e194f14c8ee46debe930871a6c18fb72242d`
- Geprüfte Distribution: `build/reviewer/distribution`
  (`interlis-repository-0.2.0-SNAPSHOT.jar` SHA-256
  `e8f490a5df37518a71d5db35820d36edd3821ff2e922d7f7421510e429a572f9`,
  `interlis-compiler-cli-0.2.0-SNAPSHOT.jar` SHA-256
  `b9baaea235d4fba34d8226d9d83e09b13fffc8333a9f79a953699fa95a5f2e79`;
  beide stimmen mit `evidence/product-manifest.json` überein).
- Ergebnis: **FAIL**. Die bytegenauen Pin-/Lock- und der abschliessende
  CHANGED_SOURCE-Vertrag werden bei legitimen schwachen Validatoren nicht
  bewahrt. Der Onlinehost akzeptiert alte Cachebytes, obwohl ein korrekt
  arbeitender HTTP-Server inzwischen andere Originalbytes ausliefert. Ein
  LOCK_MISMATCH wird im Cache-/304-Pfad nicht erkannt; `--no-cache` erkennt
  dieselbe Änderung dagegen korrekt.

Es wurde kein Serverfehler simuliert: kein unveränderter starker ETag für
verschiedene Bytes, keine rückdatierte Änderung. Verwendet wurden ein
konstanter schwacher ETag für semantisch äquivalente Darstellungen und eine
Last-Modified-Angabe mit legitimer Sekundengranularität.

## Betroffene Verträge

Kapitel 08 verlangt:

- `ModelPin` enthält den SHA-256 der Originalbytes; der Host verifiziert
  tatsächliche UTF-8-Bytes und SHA-256 (Zeilen 275, 298).
- Online werden verwendete Indizes und nachgeladene Modellquellen am Ende
  nochmals verglichen; Änderungen im Lesezeitfenster ergeben CHANGED_SOURCE
  bzw. einen verletzten Pin/Lock (Zeilen 304–306).
- Online erfolgt die Abschlussprüfung als HTTP-GET mit `If-None-Match` bzw.
  `If-Modified-Since`; 304 verwendet erneut digestgeprüfte Inhalte
  (Zeilen 473–477).

Der letzte Satz ist nur dann mit dem bytegenauen Vertrag vereinbar, wenn ein
304 bytegleiche Inhalte garantiert. Für schwache ETags und Last-Modified ist
das nicht der Fall: `If-None-Match` verwendet den schwachen Vergleich
(RFC 9110 §13.1.2), und ein schwacher ETag darf für semantisch äquivalente,
aber byteverschiedene Darstellungen unverändert bleiben. `Last-Modified` ist
ohne zusätzliche Voraussetzungen kein starker Validator (RFC 9110 §8.8.2.2).
Die Implementierung unterscheidet diese Fälle nicht.

## Codepfad

- `interlis-repository/src/main/java/guru/interlis/repository/HttpSourceLoader.java:68`
  sendet `If-None-Match` für jeden gespeicherten ETag, auch für `W/"..."`.
- `HttpSourceLoader.java:69` sendet andernfalls `If-Modified-Since`.
- `HttpSourceLoader.java:83-97` übernimmt bei 304 die alten, digestgeprüften
  Cachebytes und aktualisiert nur die Metadaten.
- `RepositoryClosureService.java:471` (`verifyUnchanged`) ruft den Loader für
  jede bereits gesehene Quelle erneut auf. Bei 304 liefert der Loader erneut
  die alten Bytes; `RepositoryClosureService.java:454` vergleicht dadurch
  alte Bytes mit alten Bytes und kann die zwischenzeitliche Änderung nicht
  erkennen.
- `RepositoryClosureService.java:448` prüft den erwarteten Pin/Lock-Digest
  gegen genau diese alten Bytes. Solange der Cache den Pin enthält, ist die
  Prüfung erfolgreich, obwohl die aktuelle Remote-Darstellung abweicht.

Damit ist der 304-Pfad für die abschliessende bytegenaue Prüfung nicht
ausreichend. Die vorhandene Abnahme
`conditionalFinalCheckDetectsChangesAndNoStoreIsNotPersistedByClosure` deckt
nur den Fall ab, dass sich der ETag zwischen den Reads ändert; sie testet
weder einen konstanten schwachen ETag noch eine sekundengenaue
Last-Modified-Angabe.

## Gegenprobe 1: konstanter schwacher ETag

Lokaler Node-HTTP-Server, Modell `Dep` über `--root Dep`; der Index bleibt
unverändert. `Dep.ili` wird zuerst als A ausgeliefert, unmittelbar danach als
B. A und B unterscheiden sich nur durch eine Kommentarzeile, sind also
semantisch äquivalent, aber byte- und SHA-256-verschieden. Der Server sendet
für beide `ETag: W/"dep-logical-1"` und beantwortet ein passendes
`If-None-Match` korrekt mit 304.

- A: SHA-256 `4579d2317136de116ddc45cb7ca4d2e1b7b37c5efdee29e1b240ca6b17d5c04b`
- B: SHA-256 `84e44d568a357ca02cd23db1a1f90c7cff72be0fcb383fdf3d1577880e40b8b3`

Ablauf und Beobachtung (Details in `run-weak/`):

1. `check --write-lock --cache` (Exit 0): erste Bytes A; der Server wechselt
   danach auf B. Die Abschlussprüfung sendet
   `If-None-Match: W/"dep-logical-1"` und erhält **304**. Der Lauf meldet
   Erfolg und schreibt einen Lock mit dem Digest von **A**. Die HTTP-Zähler
   sind 4 Requests / 2 Body-Antworten; der Modell-GET am Ende ist 304.
2. `check --read-lock --cache` (Exit 0): erneute 304-Antwort für das Modell,
   Lock-Replay erfolgreich mit A, obwohl der Server aktuell B ausliefert.
3. `check --read-lock --no-cache` (Exit 1): ohne Cache wird B geladen
   (SHA-256 B) und der Lauf endet mit `LOCK_MISMATCH`.
4. Frischer Lauf `check --no-cache --write-lock` (Exit 1): A wird geladen,
   der Server wechselt auf B, die unbedingte Abschlussprüfung lädt B und
   erkennt `CHANGED_SOURCE`; es wird kein Lock geschrieben.

Die HTTP-Zähler des Gesamtlaufs: 10 Requests, 4×200, 6×304, 4 Body-Antworten.
Der `--no-cache`-Gegenlauf: 4 Requests, 4×200, 0×304, 4 Body-Antworten.

Ergebnis: Schema, Quelle und Index bleiben im schwachen Fall logisch
unverändert; die Bytes und der Lock-Digest entsprechen aber nicht mehr dem
aktuellen Remote-Stand. Ein nötiger LOCK_MISMATCH wird im Cache-/304-Pfad
nicht erkannt.

## Gegenprobe 2: Last-Modified mit Sekundengranularität

Gleicher Aufbau, aber ohne ETag. Der Server setzt beim ersten Body die
`Last-Modified`-Zeit auf die aktuelle ganze Sekunde und wechselt unmittelbar
danach von A auf B. B ist hier semantisch **nicht** äquivalent: es ergänzt
`DOMAIN D=TEXT;`. Die Änderung liegt in derselben ganzen Sekunde, daher bleibt
dieselbe `Last-Modified`-Angabe korrekt. `If-Modified-Since` mit genau dieser
Zeit beantwortet der Server korrekt mit 304.

- A: SHA-256 `4579d2317136de116ddc45cb7ca4d2e1b7b37c5efdee29e1b240ca6b17d5c04b`
- B: SHA-256 `812137280c2fe01868dba7bbc8244f12e842254a66bb9738b29c81bece93d534`
- gesendete `Last-Modified`: `Sun, 01 Nov 2026 16:43:26 GMT`

Ablauf und Beobachtung (Details in `run-lm/`):

1. `check --write-lock --cache` (Exit 0): erste Bytes A; danach B. Die
   Abschlussprüfung sendet `If-Modified-Since: Sun, 01 Nov 2026 16:43:26 GMT`
   und erhält **304**. Der Lauf meldet Erfolg und schreibt einen Lock mit dem
   Digest von **A**.
2. `check --read-lock --cache` (Exit 0): erneute 304-Antwort, Lock-Replay mit
   A erfolgreich, obwohl B aktuell ausgeliefert wird.
3. `check --read-lock --no-cache` (Exit 1): B wird geladen, `LOCK_MISMATCH`.
4. Frischer Lauf `check --no-cache --write-lock` (Exit 1): A wird geladen,
   danach B; die unbedingte Abschlussprüfung erkennt `CHANGED_SOURCE` und
   schreibt kein Lock.

Die HTTP-Zähler sind identisch zu Gegenprobe 1: 10 Requests, 4×200, 6×304,
4 Body-Antworten im Gesamtlauf; 4 Requests, 4×200, 0×304, 4 Body-Antworten im
`--no-cache`-Gegenlauf.

Ergebnis: Hier ist die Auswirkung stärker als beim Kommentarfall. Der
Online-Lauf kompiliert die alte Darstellung A, während der Server bereits B
mit einer zusätzlichen Domäne ausliefert. Der Lock dokumentiert A. Ein
späterer Lauf ohne Cache oder auf einem anderen Rechner erhält B und scheitert
mit LOCK_MISMATCH. Der ursprüngliche Lauf hat die Änderung im Lesezeitfenster
nicht gemeldet, obwohl der Server korrekt und ohne rückdatierte Änderung
geantwortet hat.

## Bewertung

Die Verträge erlauben dieses Verhalten **nicht** im Sinne der bytegenauen
Quellenidentität. Kapitel 08 §13 beschreibt zwar bewusst einen konditionalen
Abschluss-GET und die Wiederverwendung digestgeprüfter Cachebytes bei 304.
Diese Regel setzt aber stillschweigend voraus, dass ein 304 bytegleiche Bytes
bedeutet. Für schwache ETags und Last-Modified ist diese Voraussetzung falsch.
Die übergeordneten Verträge aus Kapitel 08 §2/§7/§10 verlangen SHA-256 der
Originalbytes und die Erkennung von Änderungen im Lesezeitfenster. Die
Implementierung prüft nur die Bytes, die sie aus dem Cache wiederverwendet,
nicht die aktuell vom Server ausgelieferten Bytes. Damit ist die
Abschlussprüfung für diese Validatoren keine bytegenaue Prüfung.

Schweregrad: **P2**. Der Fehler ist still, tritt bei legitimen Serverantworten
auf und kann einen Online-Lauf mit veralteten Modellbytes erfolgreich
abschliessen sowie einen Lock auf veraltete Bytes schreiben. Beim
Last-Modified-Fall kann die veraltete Darstellung semantisch abweichen. Die
Wahrscheinlichkeit ist bei Last-Modified durch das Sekundenfenster begrenzt,
bei schwachen ETags durch die Semantikäquivalenz; die Vertragsverletzung ist
jedoch reproduzierbar und nicht auf einen defekten Server zurückzuführen.

## Minimale Empfehlung (nicht implementiert)

Für die bytegenaue Abschlussprüfung und für Pin-/Lock-Pfade darf ein 304 nur
dann als "unverändert" gelten, wenn ein starker Validator die Bytegleichheit
stützt. Konkret:

- Nur starke ETags für die 304-Wiederverwendung akzeptieren; schwache ETags
  und `Last-Modified` wie "kein Validator" behandeln und in der
  Abschlussprüfung unbedingt laden. Oder:
- `verifyUnchanged` und pin-/lockgebundene Prüfungen immer unbedingt laden und
  den SHA-256 der tatsächlich empfangenen Bytes vergleichen; konditionale
  Requests bleiben für die gewöhnliche Cache-Frische zulässig.
- Vertragstests ergänzen: konstanter schwacher ETag mit geändertem Kommentar
  bzw. CRLF; `Last-Modified` in derselben Sekunde mit semantisch relevanter
  Änderung; jeweils CHANGED_SOURCE bzw. LOCK_MISMATCH erwarten.
- Kapitel 08 §13 entsprechend präzisieren, damit die 304-Regel nicht als
  Freigabe für schwache Validatoren missverstanden wird.

## Reproduktion und Belege

Alle Scratch-Dateien liegen unter
`build/review/repository-cache/r1/worktree/build/reviewer/weak-validators/`:

- `server.js` – lokaler Testserver mit den Modi `weak-window` und `lm-window`.
- `scenarios/weak/{a,b}.ili`, `scenarios/lm/{a,b}.ili`, `scenarios/index.xml`.
- `run-weak/http.jsonl`, `run-weak/out{1,2,3}.json`, `run-weak/lock.xml`.
- `run-weak-nocache/http.jsonl`, `run-weak-nocache/out1.json`.
- `run-lm/http.jsonl`, `run-lm/out{1,2,3}.json`, `run-lm/lock.xml`.
- `run-lm-nocache/http.jsonl`, `run-lm-nocache/out1.json`.
- `evidence-manifest.json` – Kommandos, Exits, HTTP-Zähler, Datei-Digests und
  Jar-Hashes.

Ausgeführte Prüfungen: ausschliesslich lokale HTTP-Server und die
hashgebundene Distribution; keine Gradle-, Korpus-, Benchmark-, Browser- oder
Releaseprüfung, keine Delegation, keine Änderung verfolgter Dateien. Der
Arbeitsbaum war bei Abschluss sauber; HEAD ist
`cf585ec1ea2270bafed4083a8ebfa2e68de05133`.
