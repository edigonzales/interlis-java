# Unabhängiger P3-1-Review – CLI und Formatierung

**Status: FAIL – kein P3-1-PASS. Kein G3-PASS. P3-2 bis P3-5 werden hier nicht abgenommen.**

- Kandidat: `a096d7ea938de4ac143dc09d86583c021734fe75`
- Basis: `0e20e6b23fbcbdc3315f58d6e3c467fea8ee3d94`
- Arbeitsbaum: `/private/tmp/interlis-java-P3-1-a096d7e`
- Sauberkeit: `git rev-parse HEAD` lieferte den Kandidaten; `git status --porcelain=v1` war vor Beginn und nach allen Proben leer. Die eigenen Proben liegen ausschliesslich unter dem ignorierten `build/probe/P3-1/`; der Reviewbericht liegt ausserhalb des Checkouts unter `/Users/stefan/sources/interlis-java/build/review/P3-1/review.md`.
- Umfang: AC-P3-01 bis AC-P3-04 sowie P3-AUD-01 bis P3-AUD-04, kumulativ. Kein finales G3. P3-2 bis P3-5, insbesondere die Plattformmatrix Linux x64/Windows x64/macOS arm64, bleiben ausserhalb dieses Teilgates und für G3 offen.
- Keine Änderungen an Produkt, Tests, Spezifikation oder versionierter Evidenz; kein Commit, kein Push, keine neue Delegation. Die OS-Python-Sperre wurde eingehalten; verwendet wurden ausschliesslich Java, Node und Shell.

## 1. Kurzurteil

Die vier fachlichen Kernbeobachtungen sind weitgehend umgesetzt: Das Legacy-Profil ist gegen die tatsächliche ili2c-5.6.9-Hilfe inventarisiert, die Konfigurationspräzedenz und der Eltern-/Kindlauf funktionieren, die Gate-Priorität ist korrekt, und die Formatierung normalisiert gewöhnliche Kommentare, bewahrt werttragende Zeilenenden mit Hinweis und schreibt atomar.

Zwei konkrete P3-1-MUSS-Verträge sind jedoch noch offen:

1. **F1:** `--explain-config` redigiert URI-Zugangsdaten und Queries nicht für gültige URIs ohne Host (`file:`, `mailto:`) und nicht für syntaktisch ungültige URI-Werte. Dadurch gelangen Markierungen wie `marked-secret` in die Herkunftsausgabe. Das verletzt die P3-1-Vorgabe in Kapitel 18: „URI-Zugangsdaten und Queries werden redigiert.“
2. **F2:** Mehrfach genannte identische Formatquellen werden nur über `Path`-Gleichheit erkannt. Auf dem hier verwendeten case-insensitiven macOS-Dateisystem sind `Model.ili` und `model.ili` dieselbe Datei; der Lauf schreibt die erste Datei und scheitert erst an der zweiten mit `source changed since it was read`, statt vor jedem Schreiben einen Nutzungsfehler zu liefern.

Damit ist P3-1 als Lieferung **FAIL**, obwohl die AC-Kernfälle und die vorhandenen Tests grün sind. Ein bedingter PASS mit offenen relevanten MUSS-Findings ist nicht zulässig.

| Kriterium | Verdikt | Kern |
|---|---|---|
| AC-P3-01 | **PASS** | Legacy-Profil, Optionsinventar, Ablehnungen, Streams/Exits, Datei-ohne-Unterbefehl |
| AC-P3-02 | **FAIL** | Präzedenz und Eltern-/Kindlauf grün; Herkunftsausgabe verletzt die Redaktionsvorgabe (F1) |
| AC-P3-03 | **PASS** | Formatter-API semantikerhaltend, idempotent, wieder parsebar |
| AC-P3-04 | **FAIL** | Atomare Einzeldatei und Fehlerpfade grün; identische Formatquellen werden bei Case-Aliasen nicht vor dem Schreiben abgewiesen (F2) |
| P3-AUD-01 | **PASS** | Legacy-CLI-Profil ist tatsächlich belegt |
| P3-AUD-02 | **FAIL** | Konfigurationspräzedenz und Laufvertrag grün; Herkunftsreport undicht (F1) |
| P3-AUD-03 | **PASS** | Gate-Priorität und vollständige Gründe |
| P3-AUD-04 | **PASS** | Zeilenendenpolitik, Werterhalt, Idempotenz |

## 2. Umgebung und tatsächlich ausgeführte Kommandos

| Bestandteil | Tatsächlich verwendet |
|---|---|
| Betriebssystem | macOS 15.8.1, Darwin 24.6.0, arm64 |
| JDK | Temurin `25.0.3+9-LTS`, `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem` |
| Gradle | Repository-Wrapper 9.8.1 |
| Node | v24.22.0 unter `/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node` |
| ili2c-Referenz | 5.6.9, JAR-SHA-256 `37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e` |
| GraalVM / Binaryen | explizite Pfade vorhanden (`.../25.0.3-graal/bin/native-image`, `.../binaryen-version_133/bin/wasm-as`), für P3-1 nicht benötigt und nicht ausgeführt |
| Browser | `interlis-wasm-teavm/node_modules` nicht vorhanden; kein Browser-/Safari-/WebImage-Lauf, für diesen fokussierten CLI-Review nicht verlangt |

Ausgeführte Kommandos und Resultate:

```text
git rev-parse HEAD
-> a096d7ea938de4ac143dc09d86583c021734fe75

git status --porcelain=v1
-> (leer, vor und nach den Proben)

JAVA_HOME=... ./gradlew --no-daemon \
  :interlis-cli:test :interlis-compiler:test \
  --tests 'guru.interlis.compiler.FormatterContractTest' \
  --tests 'guru.interlis.compiler.P3FormatterPolicyContractTest'
-> Exit 0
-> CLI-Suite vollständig: 67 Tests, 0 Fehler, 0 Skips
-> FormatterContractTest: 5 Tests; P3FormatterPolicyContractTest: 2 Tests; 0 Fehler

JAVA_HOME=... ./gradlew --no-daemon check legacyCliCheck sdkProcessCheck
-> Exit 0
-> 1097 JUnit-/Evidenz-/Assertions-/Host-Tests in den XML-Resultaten, 0 Fehler, 0 Skips
-> LEGACY_CLI_PASS 54 actual replacement processes; 44 inventoried options
-> SDK_PROCESS_PASS 9 commands
-> PYTHON_FREE_SOURCE_PASS 1322 inputs
-> Bericht: build/reports/p3/legacy/cli-comparison.json

shasum -a 256 /Users/stefan/.cache/interlis-java/ili2c.jar
-> 37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e
```

Nicht ausgeführt: `checkPortable`, `checkBrowser`, `conformanceCheck`, `releaseCheck`, Safari, WebImage, Linux x64, Windows x64. Diese Pfade sind für den fokussierten P3-1-Review nicht verlangt; sie werden hier weder als bestanden noch als fehlend abgerechnet. Die Plattformnachweise bleiben für G3 offen.

## 3. Abnahme AC-P3-01 bis AC-P3-04

### AC-P3-01 – Legacy-ili2c-Profil: **PASS**

Belege:

- `interlis-cli/src/main/java/guru/interlis/cli/LegacyCliProfile.java` definiert `ili2c-check/5.6.9-v1`, die Referenzversion, den SHA-256-Pin, 12 unterstützte Schreibweisen und 32 ausdrücklich abgelehnte Optionen.
- `Ili2cInvocationPlanner.java` parst vor jeder Host-IO, behandelt `-h/--help/-u/--usage`, `-v/--version`, `--no-auto`, `--modeldir`, `--quiet`, `--without-warnings`, `--trace`, `--`, lokale Verzeichnisse, `%ILI_DIR`, `%JAR_DIR` und die 10 000-Einträge-Grenze.
- `Main.java` leitet einen Aufruf ohne Unterbefehl (Dateien, `-h`, `-v`, unbekannte Option) an das Legacy-Profil; `Ili2cMain` und `bin/ili2c`/`compat/ili2c.jar` sind ausgeliefert.
- `LegacyCliProcesses.java` startet 54 tatsächliche Ersatzprozesse und für 19 Vergleichsfälle echte Referenzprozesse; alle 32 abgelehnten Optionen werden separat als Ersatzprozess ausgeführt.
- Die unabhängige Referenzhilfe-Prüfung ergab exakt 44 Optionen; die Inventarliste des Berichts ist deckungsgleich. Die Referenzversion, der JAR-Hash und die Hilfe-/Versionsausgabe auf stderr wurden direkt beobachtet.
- Die dokumentierten Abweichungen (fehlende Datei, unbekannte Option, leerer Aufruf, blosser Modellname) sind im Profil und im Bericht ausdrücklich benannt und mit tatsächlichen Referenz-Exits belegt.

Restgrenze: Der Prozessnachweis ist lokal macOS arm64. Linux x64 und Windows x64 bleiben P3-5/G3.

### AC-P3-02 – Konfiguration, Herkunft und Laufvertrag: **FAIL**

Die funktionalen Kernteile sind belegt:

- `ConfigurationLoader.java` liest ausschliesslich `--config FILE` als UTF-8-Properties, prüft bekannte Schlüssel, Duplikate, leere/ungültige Werte und liefert CLI-vor-Datei-vor-Profildefault mit `Origin`.
- `P3ConfigurationContractTest` prüft CLI-Override, Dateiherkunft, Profildefault, unbekannte/doppelte/ungültige Schlüssel und die URI-Redaktion für den getesteten HTTPS-Fall.
- `P3CliLifecycleContractTest` zeichnet einen Eltern- und einen Kindlauf auf, prüft genau einen Abschluss, die Reihenfolge und den Flushfehler nach erfolgreichem Compilerkind.
- Die eigene Prozessprobe bestätigt: `warnings=reject` aus der Datei ergibt `WARNINGS_REJECTED` (Exit 1, JSON-Status), `--warnings allow` auf der CLI ergibt Exit 0; `--explain-config` zeigt `CONFIGURATION_FILE` bzw. `CLI`.
- Die eigene In-Process-Probe bestätigt: Ein erfolgreicher `format --write` mit anschliessendem Flushfehler endet `OPERATIONAL_FAILURE`, der fertiggestellte Dateiartefakt bleibt in `CliExecutionResult.artifacts()`, der Elternlauf ist `Rejected[OPERATIONAL_FAILURE]`, es gibt genau einen Start und einen Abschluss und keine temporäre Datei.

Der MUSS-Bruch liegt in der Herkunftsausgabe: **F1**. `--explain-config` gibt für `file:///tmp/x?token=marked-secret`, für `mailto:user:marked-secret@example.org?token=marked-secret` und für den syntaktisch ungültigen Wert `https://user:marked-secret@example.org/%` den geheimen Wert unverändert aus; dasselbe gilt für einen `repository=`-Wert aus einer Konfigurationsdatei. Das widerspricht der P3-1-Vorgabe, URI-Zugangsdaten und Queries zu redigieren. Wegen dieses offenen MUSS-Findings ist AC-P3-02 nicht abnehmbar.

### AC-P3-03 – Formatter-API: **PASS**

Belege:

- `DefaultModelFormatter.java` normalisiert bei expliziter LF-/CRLF-Wahl gewöhnliche mehrzeilige Kommentare; Zeilenenden innerhalb werttragender Literale und mehrzeiliger Metaattributwerte werden mit `VALUE_LINE_ENDING_PRESERVED` bewahrt.
- `FormatOptions.LineEnding` dokumentiert die Ausnahme für werttragende mehrzeilige Literale/Metadaten; `PRESERVE` bewahrt die ursprünglichen Zeilenenden.
- `FormatterContractTest` (5 Tests) und `P3FormatterPolicyContractTest` (2 Tests) sind grün: 2.3/2.4, LF/CRLF/PRESERVE, Idempotenz, Re-Parse, Kommentar-/Unicode-Erhalt, Metadatenwerte.
- Eigene Prozessprobe: Eine mehrzeilige `!!@ fn.return`-Metadatenquelle mit LF wurde mit `--line-ending crlf` formatiert: 150 Bytes, 5 CRLF, 1 LF; der LF liegt ausschliesslich im werttragenden Metadatenwert; stderr meldet `VALUE_LINE_ENDING_PRESERVED`; der zweite Lauf ist bytegleich idempotent; `check` auf dem Ergebnis ist Exit 0.
- Die P3-AUD-04-Gegenprobe aus dem Voraudit (mehrzeiliger Kommentar mit gemischten Zeilenenden) ist damit für gewöhnliche Kommentare behoben; die Werterhalt-Ausnahme ist in Kapitel 18 präzisiert und durch die Tests gedeckt.

### AC-P3-04 – Atomare CLI-Ausgabe und Fehlerpfade: **FAIL**

Die Kernfälle sind grün:

- `CliOutputWriter.java` staged im Zielverzeichnis, prüft Abbruch nach dem Staging und unmittelbar vor der Ersetzung, vergleicht die gelesenen Originalbytes, verweigert symbolische Ziele, entfernt temporäre Dateien bei Fehlern und verwendet `ATOMIC_MOVE` ohne nichtatomaren Fallback.
- `P3AtomicOutputContractTest` (6 Tests) prüft fehlenden atomaren Move, IO-Fehler, Abbruch nach Staging, externen Edit, Rechteerhalt, spätere Batch-Fehler mit genau den fertiggestellten Dateien sowie einen unerwarteten Schreibdefekt mit `FailureId` ohne Stacktrace.
- Eigene Prozessprobe: `format --write First.ili Link.ili` (zweite Quelle ein Symlink) endet Exit 1; `First.ili` ist formatiert, `Link.ili` bleibt Symlink, das reale Ziel bleibt unverändert, es bleibt keine `.interlis-output-*`-Datei zurück.
- Eigene Probe: POSIX-Rechte 640 bleiben nach `format --write` erhalten; keine temporäre Datei.
- Eigene In-Process-Probe: Flushfehler nach erfolgreichem Schreiben ergibt `OPERATIONAL_FAILURE` mit erhaltenem Artefakt und Elternlauf `Rejected[OPERATIONAL_FAILURE]`.

Der MUSS-Bruch liegt in der Vorabprüfung identischer Formatquellen: **F2**. `OutputPlanner` vergleicht nur `Path`-Objekte. Auf dem case-insensitiven macOS-Dateisystem sind `Model.ili` und `model.ili` dieselbe Datei; `format --write Model.ili model.ili` schreibt die erste Datei und scheitert erst an der zweiten mit `source changed since it was read` (Exit 1), statt vor jedem Schreiben mit einem Nutzungsfehler (Exit 2) abzubrechen. Die Spezifikation verlangt für mehrfach genannte identische Formatquellen ausdrücklich einen Nutzungsfehler. Wegen dieses offenen MUSS-Findings ist AC-P3-04 nicht abnehmbar.

## 4. P3-AUD-01 bis P3-AUD-04

| Audit-ID | Verdikt | Beleg / offene Stelle |
|---|---|---|
| P3-AUD-01 | **PASS** | 44 Optionen tatsächlich inventarisiert; 32 Ablehnungen separat ausgeführt; Hilfe/Version ohne Compilerinitialisierung; Dateien ohne Unterbefehl; Streams/Exits gegen echte Referenz; Abweichungen dokumentiert. |
| P3-AUD-02 | **FAIL** | Präzedenz, Herkunft, Eltern-/Kindlauf und Flushfehler sind grün; **F1** verletzt die Redaktionsvorgabe der Herkunftsausgabe. |
| P3-AUD-03 | **PASS** | `CliOutcomeMapper` prüft `ENVIRONMENT_FAILURE` vor `INCOMPLETE_SUPPORT` vor `MISSING/AMBIGUOUS_INPUT` vor `SOURCE_ERRORS` vor alleinigem `WARNINGS_REJECTED`; `P3OutcomePriorityContractTest` prüft alle 63 nichtleeren Kombinationen; alle Gründe bleiben über `rejectionSummary` sichtbar. |
| P3-AUD-04 | **PASS** | Gewöhnliche mehrzeilige Kommentare werden bei LF/CRLF normalisiert; werttragende Literale/Metadaten bleiben mit Hinweis erhalten; `PRESERVE` bewahrt; idempotent und wieder parsebar. |

## 5. Eigene unabhängige Gegenproben

Alle Proben liegen unter `build/probe/P3-1/` (ignoriert) und sind nicht Teil des Produkts oder der Testsuite.

| Probe | Kommando (verkürzt) | Beobachtung | Exit |
|---|---|---|---|
| Referenzinventar | `java -jar .../ili2c.jar --help` | 44 Optionen; JAR-Hash `37d945…f49e`; Hilfe auf stderr, stdout leer | 0 |
| Referenzabweichungen | `java -jar .../ili2c.jar --no-auto /tmp/definitely-missing-p31.ili` bzw. `--no-auto --unknown-p3 /tmp/nope.ili` | Referenz liefert für fehlende Datei bzw. unbekannte Option Exit 0; `--modeldir` ohne Wert Exit 1 mit internem Fehler. Entspricht den dokumentierten Profilabweichungen. | 0/0/1 |
| Symlink-/Batch-Atomarität | `bin/interlis-cli format --write First.ili Link.ili` | Exit 1; `First.ili` formatiert; `Link.ili` Symlink; reales Ziel unverändert; 0 temporäre Dateien | 1 |
| Metadaten-Zeilenenden | `bin/interlis-cli format --line-ending crlf Meta.ili` | 150 Bytes, 5 CRLF, 1 LF im Metawert; `VALUE_LINE_ENDING_PRESERVED`; zweiter Lauf bytegleich; `check` Exit 0 | 0 |
| Konfigurationspräzedenz | `bin/interlis-cli check --config settings.properties Warning.ili`; mit `--warnings allow` | Datei `reject` → `WARNINGS_REJECTED` Exit 1; CLI `allow` → Exit 0; `--explain-config` zeigt `CONFIGURATION_FILE`/`CLI` | 1/0 |
| Flushfehler | eigenes `FlushProbe.java` gegen die installierte Distribution | `OPERATIONAL_FAILURE`; Artefakt `FORMATTED_SOURCE` bleibt; Elternlauf `Rejected[OPERATIONAL_FAILURE]`; 1 Start/1 Abschluss; 0 temporäre Dateien | 0 (Probe) |
| POSIX-Rechte | `chmod 640 Model.ili; format --write Model.ili` | Modus vor/nach 640; 0 temporäre Dateien | 0 |
| **F1 Redaktion** | `bin/interlis-cli check --repository 'file:///tmp/x?token=marked-secret' --explain-config` | Exit 0; stdout enthält `marked-secret` unverändert | 0 |
| **F1 Redaktion, config** | `settings.properties` mit `repository=https://user:marked-secret@example.org/%`; `check --config settings.properties --explain-config` | Exit 0; stdout enthält `marked-secret` unverändert | 0 |
| **F2 Case-Alias** | `bin/interlis-cli format --write Model.ili model.ili` auf case-insensitivem macOS | Exit 1; `format write failed: source changed since it was read`; `Model.ili` wurde bereits verändert | 1 |
| Diagramm-Metadaten | eigenes `DiagramProbe.java` mit `--root Survey` | `RunCompletion.Succeeded` meldet `rootCount=0`, obwohl ein Root angefordert war | 0 (Probe) |

## 6. Findings nach Priorität

### F1 – Hoch / MUSS: Herkunftsausgabe leakt URI-Zugangsdaten und Queries

**Fundstelle:** `interlis-cli/src/main/java/guru/interlis/cli/ConfigurationLoader.java:99-106`. `safeValue` redigiert nur, wenn `uri.getHost() != null` und `getRawUserInfo()` oder `getRawQuery()` gesetzt ist. Bei gültigen URIs ohne Host (`file:`, `mailto:`) und bei `URISyntaxException` wird der Rohwert zurückgegeben.

**Reproduktion:**

```text
BIN=interlis-cli/build/install/interlis-cli/bin/interlis-cli
BIN check --repository 'file:///tmp/x?token=marked-secret' --explain-config
-> Exit 0; stdout: ... "values":["file:///tmp/x?token=marked-secret"] ...

BIN check --repository 'mailto:user:marked-secret@example.org?token=marked-secret' --explain-config
-> Exit 0; stdout enthält beide "marked-secret"

printf 'repository=https://user:marked-secret@example.org/%%\n' > settings.properties
BIN check --config settings.properties --explain-config
-> Exit 0; stdout enthält "marked-secret"
```

**Erwarteter Vertrag:** Kapitel 18 P3-1: „URI-Zugangsdaten und Queries werden redigiert.“ Die Erklärung darf weder Benutzerinformationen noch Query-Werte im Klartext ausgeben. Für nicht auflösbare/ungültige URI-Werte ist ein Nutzungsfehler vor der Ausgabe zulässig; alternativ muss der gesamte URI-Wert redigiert werden. Die Redaktion gilt für CLI- und Konfigurationsdatei-Werte.

**Erwarteter Test:** Prozess-/Vertragstest mit `https://user:secret@host/path?token=secret` (bereits vorhanden), zusätzlich `file:///tmp/x?token=secret`, `mailto:...?...`, `https://user:secret@host/%` und einem `repository`-Wert aus einer Datei; Ausgabe und Exit prüfen.

### F2 – Mittel / MUSS: Case-Aliase identischer Formatquellen werden nicht vor dem Schreiben abgewiesen

**Fundstelle:** `interlis-cli/src/main/java/guru/interlis/cli/OutputPlanner.java:20-21` verwendet `new HashSet<>(sources)` über `Path`-Objekte. `DefaultCompilerCli.java:483-497` schreibt danach jede Datei einzeln.

**Reproduktion (macOS, case-insensitives APFS):**

```text
mkdir -p build/probe/P3-1/case && cd build/probe/P3-1/case
printf 'INTERLIS 2.4;\nMODEL M AT "x" VERSION "1" =\nTOPIC T =\nCLASS C =\nvalue : TEXT*10;\nEND C;\nEND T;\nEND M.\n' > Model.ili
BIN=interlis-cli/build/install/interlis-cli/bin/interlis-cli
BIN format --write Model.ili model.ili
-> Exit 1
-> stderr: format write failed: source changed since it was read
-> Model.ili wurde bereits formatiert (Originalinhalt nicht mehr vorhanden)
```

**Erwarteter Vertrag:** Kapitel 18 P3-1: „Mehrfach genannte identische Formatquellen sind Nutzungsfehler.“ Auf einem case-insensitiven Host sind `Model.ili` und `model.ili` dieselbe Quelle. Erwartet ist `USAGE_ERROR` (Exit 2) vor jedem Schreiben; keine Datei darf verändert werden. Die Prüfung sollte `Files.isSameFile` beziehungsweise `toRealPath()` verwenden und zusätzlich Hardlink-Aliase abdecken.

**Erwarteter Test:** Prozess-/Vertragstest auf case-insensitivem Dateisystem mit zwei Case-Varianten und mit einem Hardlink; erwartet Nutzungsfehler und unveränderte Zieldatei.

### F3 – Tief / Dokumentation: Kapitel 13 nennt nicht existierende Testklassen

**Fundstelle:** `docs/spec/13-tests-kompatibilitaet-und-qualitaet.md:274` nennt `RunContextPropagationTest`, `:337` nennt `CliOutcomeMappingTest`. Im Baum existieren diese Klassen nicht; die tatsächlichen Tests sind `P3CliLifecycleContractTest` und `P3OutcomePriorityContractTest` (Kapitel 16, Zeilen 695-698, nennt sie korrekt).

**Erwartung:** Testvertragsnamen in Kapitel 13 und 16 synchronisieren oder die genannten Klassen als echte Vertragstests anlegen. Kein Verhaltensfehler, aber eine offene API-/Spezifikationsinkonsistenz.

### F4 – Tief / Dokumentation: Kapitel-18-Tabelle beschreibt `CliEnvironment` breiter als der Code

**Fundstelle:** `docs/spec/18-cli-sdk-und-kompatibilitaetsadapter.md:46` nennt „Uhr, RunObserver, RunContextFactory, Repositorydienste, Profillader“. Der tatsächliche `CliEnvironment` enthält `CompilerServices` (Observer, Clock, RunContextFactory), aber keine Repositorydienste und keinen Profillader; die P3-1-Präzisierung ab Zeile 250 ergänzt nur `CompilerServices`.

**Erwartung:** Tabelle auf den tatsächlichen Vertrag zurückschneiden oder die fehlenden Injektionen tatsächlich einführen. Kein blockierender Verhaltensfehler, aber eine irreführende Rollenbeschreibung.

### F5 – Tief / Laufmetadaten: `diagram` meldet `rootCount=0`

**Fundstelle:** `DefaultCompilerCli.java:389-393` setzt `invocation.rootCount` im Diagrammpfad nicht; `Invocation` (Zeile 703) initialisiert `rootCount` mit 0. Die eigene Probe mit `diagram --root Survey` ergab `RunCompletion.Succeeded[rootCount=0, analysisComplete=true, compiledSchemaProduced=true]`, obwohl ein Root angefordert war.

**Erwartung:** Parent-Abschluss soll die tatsächlich angeforderten Roots melden (analog zu `compile`/`analyze`). Kein AC-Blocker, aber eine ungenaue Laufbeobachtung.

## 7. Quellbindung, historische Pins und Statuswahrheit

Die mechanische Bindungspflege ist konsistent:

- `shasum -a 256` für `docs/spec/09`, `16`, `17`, `18` stimmt mit den aktualisierten Pins in `docs/evidence/stabilization/g2-source-inventory.json` und `testdata/g2/completion-links.json` überein.
- `g2-source-inventory.json` und `g2-test-candidate-index.json` sind in `g2-rule-matrix.json` mit den tatsächlichen SHA-256 gebunden (`cfce99…`, `5e91d8…`).
- Die tatsächliche Anzahl der `@Test`-Methoden unter `interlis-compiler/src/test/java` ist 731; der aktualisierte `testMethods`-Wert ist damit wahr.
- Historische Zustände bleiben erhalten: `sourceInventory.acceptance` bleibt `MISSING_EVIDENCE`, `testCandidateIndex.status` bleibt `CANDIDATE_ONLY`; es wurde kein historischer `VERIFIED`-Status in einen neuen PASS umgeschrieben.
- `docs/evidence/p3/` und die Korpus-/Norm-Pins wurden nicht verändert. Die G2-Historie wurde in diesem Review nicht neu abgenommen; G2 wird hier weder bestätigt noch bestritten.

Hinweis zur Reproduzierbarkeit: `docs/p3/CLI_PROFILE.md` verweist auf `build/reports/p3/P3-1/current-jvm-cli-r2.log`; dieser Build-Log ist nicht versioniert und in einem frischen Checkout nicht vorhanden. Der aktuelle Lauf erzeugte `build/reports/p3/legacy/cli-comparison.json` neu. Das ist kein Produktfehler, aber die genannte Logdatei ist ohne den ursprünglichen Build nicht reproduzierbar.

## 8. Nicht ausgeführt / nicht behauptet

- Kein `releaseCheck`, kein `conformanceCheck`, kein `checkPortable`, kein `checkBrowser`.
- Kein Chromium-/Firefox-/WebKit-/Safari-Lauf; `interlis-wasm-teavm/node_modules` fehlt.
- Kein WebImage-Build; GraalVM und Binaryen sind an den angegebenen Pfaden vorhanden, wurden aber für P3-1 nicht ausgeführt.
- Keine Linux-x64-/Windows-x64-Ausführung; die Plattformmatrix bleibt P3-5/G3.
- Keine Neubewertung von G2; keine Abnahme von P3-2 bis P3-5; kein G3-PASS.
- Keine Änderung an Produkt, Tests, Spezifikation oder versionierter Evidenz; kein Commit/Push.

## 9. Gesamturteil

**P3-1: FAIL.**

- AC-P3-01: **PASS**
- AC-P3-02: **FAIL** (F1)
- AC-P3-03: **PASS**
- AC-P3-04: **FAIL** (F2)
- P3-AUD-01: **PASS**
- P3-AUD-02: **FAIL** (F1)
- P3-AUD-03: **PASS**
- P3-AUD-04: **PASS**

Die Kernfunktionen sind vorhanden und durch aktuelle JVM-, CLI-, Formatter-, Atomaritäts- und Prozessnachweise gedeckt. Für einen P3-1-PASS müssen mindestens F1 und F2 behoben und mit den oben genannten Gegenproben neu belegt werden; F3 bis F5 sollten im selben Zug bereinigt werden. Danach ist ein neuer Kandidat und ein erneuter unabhängiger P3-1-Review erforderlich. P3-2 bis P3-5 bleiben unberührt; G3 bleibt offen.
