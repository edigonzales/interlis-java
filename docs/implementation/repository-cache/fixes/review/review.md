# Unabhängige Nachprüfung der beiden P2-Cachekorrekturen – PASS

## 1. Auftrag, Kandidat und Methodik

- Geprüfter Kandidat: `4040da3c8f7fb359ba340dbff4a2985c4add2025`
  (isolierter Snapshot, Worktree
  `build/review/repository-cache/fixes-r1/worktree`).
- Basis: `cf585ec1ea2270bafed4083a8ebfa2e68de05133`.
- Geprüfter Baum: `044684634a6403c89ac1505527044fd3ed92f18c`.
- Reviewer: OpenCode 2.0.28, Provider `deepseek`, Modell
  `deepseek/deepseek-flash` (DeepSeek V4.1 Flash 1017), direkte API über
  `api.deepseek.com`. Kein Provider-/Modellfallback, keine Delegation.
- Laufzeit: Temurin JDK 25.0.3 ohne Preview, Node 22.13.0, die
  hashgebundene Distribution unter `build/reviewer/distribution`.
- Keine getrackten Dateien geändert, keine Commits/Pushes, keine Secrets oder
  Anmeldedaten gelesen. Berichte nur unter `output/`, Scratch nur unter
  `build/reviewer/counterprobes/`.
- Kein Gradle, keine vollständigen Suites, kein Korpus-/Benchmark-/Browser-/
  Plattform-/Releasegate, kein Python/pip/uv. Die vorhandenen Rohbelege wurden
  auditiert; ergänzend liefen wenige eigene CLI-/HTTP-Gegenproben.

Der Kandidat ist ein isolierter Snapshot, kein Commit des Benutzerbranchs.
`git status --porcelain` war vor und nach der Prüfung leer. Der Hinweis
«unabhängige Nachprüfung noch ausstehend» in `docs/implementation-status.md`
ist erwartungsgemäss noch nicht auf PASS gesetzt und wurde nicht verändert.

## 2. Audit der vorhandenen Rohbelege und Bindungen

### 2.1 Quell-, Test- und JAR-Hashes

Eigenes Auditprogramm
`build/reviewer/counterprobes/own-probes/hash-audit.mjs` (Node, Exit 0):

| Prüfung | Ergebnis |
|---|---|
| `files_sha256` aus `product-manifest.json` | 288/288 vorhanden und korrekt, 0 Abweichungen, 0 fehlend |
| `test_sources_sha256` | 10/10 korrekt, 0 Abweichungen |
| `final_runtime_jars_sha256` im Distributionsverzeichnis | 7/7 korrekt, 0 Abweichungen, keine zusätzlichen JARs |

Damit stimmen die tatsächlich geprüften Quellen und die ausgelieferten JARs mit
dem Manifest überein. Zusätzlich zeigt `javap` auf
`interlis-repository-0.2.0-SNAPSHOT.jar` die neuen privaten Methoden
`isStrongEtag`/`entityTag` und die Trennung `unavailable`/
`unavailableIndexes`; der Bytecode enthält den Bedingungsheader
`If-None-Match` und keinen `If-Modified-Since`-Request. Die Distribution
verhält sich in den unten dokumentierten Proben entsprechend.

### 2.2 RED-/GREEN-Belege

- RED: `evidence/red-RepositoryCacheRegressionTest.xml` enthält 7 Tests,
  7 Failures, 0 Errors, 0 Skips. Die Fehlermeldungen passen zu den alten
  Befunden: optionaler Site-Miss `NOT_FOUND` statt `LOAD_FAILURE`, alter
  Cachebyte bei 304 sowie unentdeckte schwache Validatoren. `red.log` zeigt
  den erwarteten Gradle-Fehlschlag vor der Produktkorrektur.
- GREEN: Die zehn gezielten XML-Suiten unter
  `evidence/interlis-repository/` und `evidence/interlis-compiler-cli/`
  summieren sich zu **68 Tests, 0 Failures, 0 Errors, 0 Skips**. Die
  Testnamen der XMLs wurden gegen die Testquellen abgeglichen; es fehlen
  keine Methoden.
- Die finale `final-regressions.log` startet nur
  `:interlis-repository:test --tests '*RepositoryCacheRegressionTest'`.
  Deren XML (7 Tests, 0 Fehler) ist später datiert als die neun
  wiederverwendeten Suiten. Das entspricht der berichteten gezielten
  Ersetzung genau einer Suite; es wurde kein vollständiger Modullauf
  behauptet.
- `green-summary.json` stimmt mit der Summe der XMLs überein
  (68/10/0/0/0).

### 2.3 Umfang des Produkt-/Test-/Spezifikationsdiffs

Der Diff gegen `cf585ec` beschränkt sich auf:

- `HttpSourceLoader`, `RepositorySearch`, `RepositoryClosureService`
  (Produktion),
- `RepositoryCacheRegressionTest` neu, eine Erwartung in
  `RepositoryCacheContractTest` und eine CLI-Gegenprobe in
  `DefaultRepositoryCacheTest`,
- die zugehörigen Kapitel 08, 13, 14, 15, 16, 17, 18, README,
  Implementierungsstatus und die historischen Review-/Evidenzdateien.

Es gibt keine Änderung an öffentlichen Signaturen, Lockformaten oder
fachlichen CLI-Verträgen. Die Änderung von `RepositorySearch` ist
package-privat, die neuen Methoden in `HttpSourceLoader` sind privat.

## 3. Bewertung der Kriterien

| Kriterium | Status | Begründung |
|---|---|---|
| FIX-01 | **fulfilled** | Nur syntaktisch starke ETags (`"..."`, keine `W/`-Form, gültige `etagc`-Zeichen) werden als `If-None-Match` an das zur gecachten Herkunft passende Redirectziel gesendet. Eine 304 wird nur bei genau einem identischen ETag-Feldwert akzeptiert; fehlender, schwacher, abweichender oder mehrdeutiger Wert sowie fehlende Cachebytes führen zu genau einem unbedingten Retry. Eine zweite unbrauchbare 304 endet als Ladefehler. Starke bestätigte Hits sparen weiterhin Bodies. |
| FIX-02 | **fulfilled** | Eigene Proben mit konstantem schwachen ETag und mit Last-Modified in derselben Sekunde: Änderung im Lesefenster ergibt `CHANGED_SOURCE`; alter Lock ergibt online `LOCK_MISMATCH`; expliziter Quellenpin ergibt `PIN_MISMATCH`; exakter Offline-Lockreplay liefert die alten Bytes mit 0 HTTP-Requests. Schwache ETags/Last-Modified werden nicht als Bytebeweis verwendet. |
| FIX-03 | **fulfilled** | Fehlendes optionales `ilisite.xml` offline: Warnung `ILI-RES-0008` (SITE_UNAVAILABLE/OFFLINE), Fehler `ILI-RES-0001` NOT_FOUND, Exit 1, 0 HTTP. Nach Entfernen des erforderlichen Index-Bodys: `ILI-RES-0003` LOAD_FAILURE/OFFLINE mit Quelle `.../ilimodels.xml`. Malformed Site-XML: Warnung SITE_UNAVAILABLE/INVALID_INDEX, aber NOT_FOUND statt INDEX_FAILURE. Die CLI-Gegenprobe im Test prüft zusätzlich `INPUT_UNRESOLVED`. |
| FIX-04 | **fulfilled** | Die optionale Site bleibt in `RepositorySearch.unavailable()` und damit in Lock-/Abschlussprovenienz; nur die Fehlerklassifikation verwendet `unavailableIndexes()`. Erforderliche Index-/Modellausfälle bleiben echte Fehler; Repositorywahl, no-store, Vary, Abbruch-, Grössen-, Warnungs- und Gategrenzen wurden nicht verändert. Die bestehenden 68 gezielten Vertragstests bleiben grün. |
| FIX-05 | **fulfilled** | Rohbelege, 288 Produktions-/Build-Hashes, 10 Testquellen und 7 JARs wurden unabhängig geprüft. RED 7/7 und GREEN 68/10/0/0/0 sind konsistent. Eigene CLI-/HTTP-Gegenproben reproduzieren die alten Fehlerpfade am neuen Produkt. Spezifikation, API-Register, Abnahmen, Entscheidung, Matrix, CLI-Dokumentation und README sind synchron; keine erfundenen Pflichtbelege. |

## 4. Eigene Gegenproben

Treiber:
`node build/reviewer/counterprobes/own-probes/run-probes.cjs` (Exit 0) und
`node build/reviewer/counterprobes/own-probes/malformed-site.cjs` (Exit 0).
Die Kindprozesse verwendeten
`build/reviewer/distribution/bin/interlis-compiler`, `HOME`/`-Duser.home`
auf jeweils frische Scratch-Homes und lokale Node-HTTP-Server.
Rohdaten: `own-probes/results.json`, `own-probes/http.jsonl`,
`own-probes/malformed-site/results.json`.

Exakte Kindkommandos (jeweils mit `W=/Users/stefan/sources/interlis-java/build/review/repository-cache/fixes-r1/worktree`,
`C=$W/build/reviewer/counterprobes/own-probes/work` und gesetztem
`HOME`/`JAVA_OPTS=-Duser.home=...`):

```text
$W/build/reviewer/distribution/bin/interlis-compiler check --root Basis  --repository http://127.0.0.1:52404/optional/  --json
$W/build/reviewer/distribution/bin/interlis-compiler check --root Absent --repository http://127.0.0.1:52404/optional/  --offline --json
# nach Löschen des Index-Bodys im selben Home:
$W/build/reviewer/distribution/bin/interlis-compiler check --root Absent --repository http://127.0.0.1:52404/optional/  --offline --json
$W/build/reviewer/distribution/bin/interlis-compiler check --root Dep --repository http://127.0.0.1:52404/weak-change/ --cache $C/home-weak-change/.interlis-cache --write-lock $C/weak-change.lock.xml --json
$W/build/reviewer/distribution/bin/interlis-compiler check --root Dep --repository http://127.0.0.1:52404/weak-lock/   --cache $C/home-weak-lock/.interlis-cache   --write-lock $C/weak-lock.lock.xml --json
$W/build/reviewer/distribution/bin/interlis-compiler check --root Dep --repository http://127.0.0.1:52404/weak-lock/   --cache $C/home-weak-lock/.interlis-cache   --read-lock  $C/weak-lock.lock.xml --json
$W/build/reviewer/distribution/bin/interlis-compiler check --root Dep --repository http://127.0.0.1:52404/weak-lock/   --cache $C/home-weak-lock/.interlis-cache   --source-pin http://127.0.0.1:52404/weak-lock/Dep.ili c9d7a67a9b8fad53411634b070a446dae5a2803b72d6953b10c7dd44de6d5a19 --json
$W/build/reviewer/distribution/bin/interlis-compiler check --root Dep --repository http://127.0.0.1:52404/weak-lock/   --cache $C/home-weak-lock/.interlis-cache   --read-lock  $C/weak-lock.lock.xml --offline --json
$W/build/reviewer/distribution/bin/interlis-compiler check --root Dep --repository http://127.0.0.1:52404/lm-change/   --cache $C/home-lm-change/.interlis-cache   --write-lock $C/lm-change.lock.xml --json
$W/build/reviewer/distribution/bin/interlis-compiler check --root Dep --repository http://127.0.0.1:52404/strong/      --cache $C/home-strong/.interlis-cache      --write-lock $C/strong.lock.xml --json
$W/build/reviewer/distribution/bin/interlis-compiler check --root Dep --repository http://127.0.0.1:52404/strong/      --cache $C/home-strong/.interlis-cache      --json
$W/build/reviewer/distribution/bin/interlis-compiler check --root Dep --repository http://127.0.0.1:52404/strong/      --cache $C/home-strong/.interlis-cache      --json
$W/build/reviewer/distribution/bin/interlis-compiler check --root Dep --repository http://127.0.0.1:52404/strong/      --cache $C/home-strong/.interlis-cache      --json
$W/build/reviewer/distribution/bin/interlis-compiler check --root Absent --repository http://127.0.0.1:52482/repo/   --json
```

Die beiden `strong`-Wiederholungen nach dem ersten Lauf sind die Läufe
«unbestätigte 304» und «stark→schwach»; der Serverzustand wurde dazwischen
gezielt umgeschaltet. Die vier `strong`-Kindläufe exiteten 0, die
`weak-change`-/`lm-change`-Läufe 1, die optionalen Offline-Läufe 1
(davon der erforderliche Indexfall 1), der malformed-Site-Lauf 1.

### 4.1 Optionaler Site-Miss und erforderlicher Index

Repository `http://127.0.0.1:52404/optional/`, Index enthält nur `Basis`,
`ilisite.xml` fehlt absichtlich.

| Lauf | Exit | Diagnose | HTTP Requests / Body-Antworten |
|---|---|---|---|
| Warmlauf `check --root Basis --repository ... --json` | 0 | ok | 4 / 2 |
| `check --root Absent ... --offline --json` | 1 | ILI-RES-0008 SITE_UNAVAILABLE/OFFLINE + ILI-RES-0001 NOT_FOUND | 0 / 0 |
| Index-Body gelöscht, gleicher Offline-Lauf | 1 | ILI-RES-0003 LOAD_FAILURE/OFFLINE, Quelle `.../ilimodels.xml` | 0 / 0 |

Damit ist der alte F1-Pfad behoben: Die fehlende optionale Site allein erzeugt
keine erforderliche Quellenablehnung; der echte Indexausfall bleibt ein Fehler
mit konkreter Quelle.

### 4.2 Schwacher ETag: Änderung im Lesefenster

Repository `.../weak-change/`, Modell `Dep.ili` wird zuerst als A, unmittelbar
danach als B ausgeliefert; der Server sendet konstant
`ETag: W/"dep-logical-1"` und würde ein passendes schwaches
`If-None-Match` korrekt mit 304 beantworten. A/B-SHA-256:
`c9d7a67...` / `95f5183...`.

| Lauf | Exit | Beobachtung | HTTP Requests / Body-Antworten |
|---|---|---|---|
| `check --root Dep --cache ... --write-lock ... --json` | 1 | ILI-RES-0009, Argument `reason=CHANGED_SOURCE`; kein Lock | 4 / 3 |

Die Modell-Requests waren beide **200** (A, dann B) und hatten **keinen**
`If-None-Match`-Header. Genau der alte Fehlerpfad (304 auf den schwachen ETag
und erfolgreicher Lauf mit alten Bytes) tritt nicht mehr auf.

### 4.3 Schwacher ETag: Lock, Pin und Offline-Replay

Repository `.../weak-lock/`, zunächst A, danach B, konstanter schwacher ETag.

| Lauf | Exit | Beobachtung | HTTP Requests / Body-Antworten |
|---|---|---|---|
| Erstlauf `--write-lock` | 0 | Lock mit A-SHA-256 | 4 / 3 |
| Nach Wechsel auf B `--read-lock --cache` | 1 | ILI-RES-0009, `reason=LOCK_MISMATCH` | 2 / 1 |
| `--source-pin <Dep.ili> <A-SHA>` | 1 | ILI-RES-0009, `reason=PIN_MISMATCH` | 2 / 1 |
| `--read-lock --offline --cache` | 0 | A-SHA-256, exakter Lockreplay | 0 / 0 |

Der Online-Lauf lädt B unbedingt und erkennt die Abweichung; der Offline-Lauf
verwendet die alten gespeicherten Bytes ohne Netz und ohne HTTP.

### 4.4 Last-Modified in derselben Sekunde

Repository `.../lm-change/`, kein ETag, konstante
`Last-Modified: Sun, 01 Nov 2026 16:43:26 GMT`, Modell A wechselt nach dem
ersten Body auf B.

| Lauf | Exit | Beobachtung | HTTP Requests / Body-Antworten |
|---|---|---|---|
| `check --root Dep --cache ... --write-lock ... --json` | 1 | ILI-RES-0009, `reason=CHANGED_SOURCE`; kein Lock | 4 / 3 |

Die Modell-Requests hatten **keinen** `If-Modified-Since`-Header; beide waren
200 (A, B). Last-Modified allein wird nicht als Bytebeweis verwendet.

### 4.5 Starke ETags: Hit, unbestätigte 304 und Übergang zu schwach

Repository `.../strong/`, Modell A mit `ETag: "model-v1"`.

| Lauf | Exit | Beobachtung | HTTP Requests / Body-Antworten |
|---|---|---|---|
| Erstlauf `--write-lock` | 0 | Modell A, 200 + bestätigte 304 | 4 / 2 |
| Zweiter Lauf mit Cache | 0 | alle vier Antworten 304, Modell-SHA A | 4 / **0** |
| 304 mit `ETag: "other"` | 0 | 304 wird verworfen, unbedingt 200 A nachgeladen; erneut 304/200 | 6 / 2 |
| Übergang auf `W/"model-v2"` und Bytes B | 0 | 304 mit schwachem Tag wird verworfen, unbedingt 200 B; Endmodell B-SHA | 5 / 2 |

Der starke bestätigte 304-Hit bleibt damit wirksam (0 Body-Antworten im
Wiederholungslauf). Eine nicht identisch bestätigte 304 und der Übergang
stark→schwach geben keine alten Cachebytes als bytegenau bestätigt frei.

### 4.6 Malformed optionale Site

Repository `http://127.0.0.1:52482/repo/`, `ilimodels.xml` enthält nur
`Basis`, `ilisite.xml` antwortet mit `<Site>`.

| Lauf | Exit | Diagnose | HTTP Requests / Body-Antworten |
|---|---|---|---|
| `check --root Absent --repository ... --json` | 1 | ILI-RES-0008 SITE_UNAVAILABLE/INVALID_INDEX + ILI-RES-0001 NOT_FOUND | 2 / 2 |

Keine INDEX_FAILURE-Ablehnung allein wegen der fehlerhaften optionalen Site.

## 5. Findings

**Keine reproduzierte Verletzung der fünf Kriterien.** Es gibt keine
P1-/P2-/P3-Findings am Kandidaten.

Bewusste Grenzen dieser Prüfung, die kein Finding sind:

- Die alten FAIL-Berichte und ihre Rohdaten bleiben historische Belege; sie
  wurden nicht mit einem alten Binary erneut ausgeführt. Die neuen Proben
  prüfen den aktuellen Kandidaten und seine Distribution.
- Die 68 gezielten Tests wurden nicht erneut mit Gradle ausgeführt; ihre
  XML-/Log-Belege und die Quell-/JAR-Hashes wurden auditiert. Genau das war
  der vereinbarte Prüfumfang; breite Suiten waren ausgeschlossen.
- Ein starkes ETag ist nur ein Serverversprechen. Ein Server, der bei
  identischem starkem ETag andere Bytes liefert, ist kein durch den Client
  erkennbarer Cachefehler und wurde nicht simuliert.

## 6. Bewertung der beiden alten P2-Befunde

| Alter Befund | Status | Beleg |
|---|---|---|
| F1: fehlendes optionales `ilisite.xml` wird als erforderliche Quelle behandelt | **behoben** | Offline-Warnung + NOT_FOUND/ILI-RES-0001, 0 HTTP; erforderlicher Index weiterhin LOAD_FAILURE/OFFLINE mit Quelle; Code trennt `unavailableIndexes()` von der vollständigen `unavailable()`-Provenienz. |
| WEAK-01: schwache ETags/Last-Modified umgehen bytegenaue Prüfungen | **behoben** | Keine konditionalen Requests für schwache ETags/Last-Modified; CHANGED_SOURCE, LOCK_MISMATCH und PIN_MISMATCH reproduziert; Offline-Lockreplay exakt und netzfrei; starke ETags sparen weiterhin Bodies. |

## 7. Urteil

**PASS.** Alle fünf Kriterien FIX-01 bis FIX-05 sind erfüllt. Die Korrekturen
beheben beide alten P2-Befunde nachvollziehbar, ohne die bytegenauen Pin-/Lock-
und Abschlussverträge abzuschwächen. Das Urteil stützt sich nicht nur auf
grüne Testzahlen, sondern auf den Quell-/JAR-Hashaudit, die RED-/GREEN-Rohbelege
und die eigenen CLI-/HTTP-Gegenproben.

Kein Commit, Push, Release oder Produktchange im Reviewauftrag. Abschliessend
wurden HEAD (`4040da3c8f7fb359ba340dbff4a2985c4add2025`) und ein leerer
`git status --porcelain` erneut geprüft.
