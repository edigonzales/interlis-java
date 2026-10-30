# Unabhängiger P3-1-Folge-Review – CLI und Formatierung (r2)

**Status: PASS für P3-1. Kein G3-PASS. P3-2 bis P3-5 werden hier nicht abgenommen.**

- Kandidat: `9108b0387d9f1b82f506d8dc64e301e8522137dd`
- Basis: `0e20e6b23fbcbdc3315f58d6e3c467fea8ee3d94`
- Arbeitsbaum: `/private/tmp/interlis-java-P3-1-r2-9108b03`
- Sauberkeit: `git rev-parse HEAD` lieferte exakt den Kandidaten; `git status --porcelain=v1` war vor Beginn, zwischen den Proben und nach allen Proben leer; `git diff --check` war leer.
- Umfang: AC-P3-01 bis AC-P3-04 kumulativ sowie P3-AUD-01 bis P3-AUD-04 aus `docs/p3/reviews/P3-voraudit.md`. Zusätzlich vollständige Prüfung von F1 bis F5 aus dem unveränderten Erstbericht `docs/p3/reviews/P3-1-a096d7e-FAIL.md`, der Windows-UTF-8-Korrektur und der Evidenzpfadänderungen.
- Keine Änderungen an Produkt, Tests, Spezifikation oder versionierter Evidenz; kein Commit, kein Push, keine neue Delegation. Die OS-Python-Sperre wurde eingehalten: kein Python/pip/uv, auch nicht ad hoc; verwendet wurden ausschliesslich Java, Node und Shell. Der vorhandene `tools/verification/without-python.cjs`-Runner wurde nicht verschachtelt.
- Eigene Proben ausschliesslich unter dem ignorierten `build/probe/r2/`; der Bericht liegt ausserhalb des Checkouts.

Der unveränderte Erstbericht wurde gelesen und seine Findings F1 bis F5 am aktuellen Kandidaten geprüft. Der Bericht selbst wurde nicht verändert; seine SHA-256 ist `57af1a36336f3440d1c7ffd41250aa488a3de6b3d3b495684888a4c2f79acb35` und stimmt mit den Statusangaben überein.

## 1. Kurzurteil

F1 bis F5 sind am aktuellen Kandidaten geschlossen. Die beiden früheren P3-1-Blocker F1 (URI-Redaktion) und F2 (Dateialias vor dem Schreiben) sind mit tatsächlichen Prozessproben behoben. F3 bis F5 sind bereinigt. Die Windows-UTF-8-Korrektur wirkt über den ausgelieferten Launcher und hält Unicode auch bei erzwungener `windows-1252`-Streamkodierung. Die Evidenzpfadänderungen sind portabel und durch einen eigenen Evidenztest gedeckt.

| Kriterium | Verdikt | Kern |
|---|---|---|
| AC-P3-01 | **PASS** | Legacy-Profil, 44 Optionen, 32 Ablehnungen, getrennte Streams/Exits, Datei ohne Unterbefehl |
| AC-P3-02 | **PASS** | Präzedenz/Herkunft, Redaktion, Eltern-/Kindlauf, ehrliche Capabilities |
| AC-P3-03 | **PASS** | Formatter semantikerhaltend, idempotent, wieder parsebar |
| AC-P3-04 | **PASS** | Atomare Einzeldatei, Alias-Vorprüfung, Teilartefakte, Fehler-/Flush-/Abbruchpfade |
| P3-AUD-01 | **PASS** | Legacy-CLI-Profil tatsächlich belegt |
| P3-AUD-02 | **PASS** | Konfiguration/Laufvertrag, Herkunft nicht leckend |
| P3-AUD-03 | **PASS** | Gate-Priorität und vollständige Gründe |
| P3-AUD-04 | **PASS** | Zeilenendenpolitik, Werterhalt, Idempotenz |

Es gibt keine offenen P3-1-MUSS-Findings. Der PASS ist nicht bedingt. Die Plattformmatrix Linux x64/Windows x64/macOS arm64 und die übrigen P3-5-Nachweise bleiben ausserhalb dieses Teilgates und für G3 offen.

## 2. Umgebung und tatsächlich ausgeführte Kommandos

| Bestandteil | Tatsächlich verwendet |
|---|---|
| Betriebssystem | macOS 15.8.1, Darwin 24.6.0, arm64 |
| JDK | Temurin `25.0.3+9-LTS`, `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem` |
| Gradle | Repository-Wrapper 9.8.1 |
| Node | `v24.22.0` unter `/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node` |
| ili2c-Referenz | 5.6.9, `/Users/stefan/.cache/interlis-java/ili2c.jar`, SHA-256 `37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e` |
| GraalVM / Binaryen | Pfade vorhanden (`.../25.0.3-graal`, `.../binaryen-version_133`), für diesen CLI-Review nicht benötigt und nicht ausgeführt |
| Browser | `interlis-wasm-teavm/node_modules` nicht vorhanden; kein Browser-/Safari-/WebImage-Lauf |

Ausgeführte Kommandos und Resultate:

```text
git rev-parse HEAD
-> 9108b0387d9f1b82f506d8dc64e301e8522137dd

git status --porcelain=v1
-> (leer, vor und nach allen Proben)

JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem ./gradlew --no-daemon \
  :interlis-cli:test :interlis-compiler:test \
  --tests 'guru.interlis.compiler.FormatterContractTest' \
  --tests 'guru.interlis.compiler.P3FormatterPolicyContractTest'
-> Exit 0, BUILD SUCCESSFUL in 16s
-> CLI: 72 Tests in 10 Suites, 0 Failures/Errors/Skips
-> Compiler (Filter): 7 Tests in 2 Suites, 0 Failures/Errors/Skips

JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem ./gradlew --no-daemon \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
  check legacyCliCheck sdkProcessCheck
-> Exit 0, BUILD SUCCESSFUL in 32s
-> LEGACY_CLI_PASS 54 actual replacement processes; 44 inventoried options
-> SDK_PROCESS_PASS 9 commands
-> PYTHON_FREE_SOURCE_PASS 1324 inputs
-> Aggregat der vorhandenen Test-XMLs: 208 Suites, 1103 Tests, 0 Failures/Errors/Skips

shasum -a 256 /Users/stefan/.cache/interlis-java/ili2c.jar
-> 37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e
```

Der breite `check`-Lauf enthielt einzelne `UP-TO-DATE`-Tasks (u. a. `:interlis-cli:test`, `:interlis-wasm-webimage:check`, `conformanceWrapperTest`); die CLI- und Compiler-Suiten wurden zuvor bzw. im Lauf tatsächlich ausgeführt. Die 1103 Testfälle sind ein Aggregat der vorhandenen XML-Resultate, nicht die Behauptung, jeder Modultask sei in diesem Lauf frisch gelaufen.

Nicht ausgeführt: `releaseCheck`, `checkPortable`, `checkBrowser`, `conformanceCheck`, Safari, WebImage, Linux x64 und Windows x64. Diese Pfade sind für den fokussierten P3-1-Review nicht verlangt und werden hier weder als bestanden noch als fehlend abgerechnet.

## 3. Abnahme AC-P3-01 bis AC-P3-04

### AC-P3-01 – Legacy-ili2c-Profil: **PASS**

Belege:

- `LegacyCliProfile.ili2c569()` definiert `ili2c-check/5.6.9-v1`, die Referenzversion `5.6.9-bf16026e83f2377153b2e818a4f3838f0a423722` und den SHA-256-Pin `37d94573…f49e`.
- Das Profil enthält 12 unterstützte Schreibweisen und 32 ausdrücklich abgelehnte Optionen. Die 44 Einträge sind vollständig.
- Eine eigene Inventur der tatsächlichen Referenzhilfe ergab exakt diese 44 Optionen. Die Extraktion aus `java -jar ili2c.jar --help` (Hilfe auf stderr, stdout leer) war deckungsgleich mit `optionInventory` des Berichts; es gab keine zusätzliche, nicht inventarisierte Option.
- `Ili2cInvocationPlanner` parst vor jeder Host-IO, behandelt `-h/--help/-u/--usage`, `-v/--version`, `--no-auto`, `--modeldir`, `--quiet`, `--without-warnings`, `--trace`, `--`, lokale Verzeichnisse, `%ILI_DIR`, `%JAR_DIR` und die 10 000-Einträge-Grenze.
- `Main` leitet einen Aufruf ohne Standardunterbefehl (Dateien, `-h`, `-v`, unbekannte Option) an das Legacy-Profil; `Ili2cMain`, `bin/ili2c`, `bin/ili2c.bat` und `compat/ili2c.jar` sind ausgeliefert.
- `legacyCliCheck` startet 54 tatsächliche Ersatzprozesse; 19 Fälle werden gegen echte Referenzprozesse verglichen. Alle 32 abgelehnten Optionen werden separat als Ersatzprozess ausgeführt und enden mit Exit 1.
- Beobachtete Referenzabweichungen (fehlende Datei, unbekannte Option, leerer Aufruf, blosser Modellname) sind im Profil und im Bericht ausdrücklich benannt und mit tatsächlichen Referenz-Exits belegt.
- Eigene Stichproben: `--modeldir 'a;;b'` und `--modeldir <reguläre Datei>` enden jeweils mit Exit 1, ohne Java-Stacktrace.

Restgrenze: Der Prozessnachweis ist lokal macOS arm64. Linux x64 und Windows x64 bleiben P3-5/G3.

### AC-P3-02 – Konfiguration, Herkunft und Laufvertrag: **PASS**

Belege:

- `ConfigurationLoader` liest ausschliesslich `--config FILE` als UTF-8-Properties (maximal 1 MiB), prüft bekannte Schlüssel, Duplikate, leere/ungültige Werte und liefert CLI-vor-Datei-vor-Profildefault mit `Origin`.
- `P3ConfigurationContractTest` (7 Tests) prüft CLI-Override, Dateiherkunft, Profildefault, unbekannte/doppelte/ungültige Schlüssel sowie die Redaktion. Die Prozessproben bestätigen die Präzedenz und die Herkunftsausgabe.
- `P3CliLifecycleContractTest` (8 Tests) zeichnet einen Eltern- und einen Kindlauf auf, prüft genau einen Abschluss, die Reihenfolge, den Flushfehler nach erfolgreichem Compilerkind, die Analyse ohne Schemaausgabe, den konkreten Abbruchgrund und parallele Aufrufe mit unabhängigen Eltern/Kindern.
- Eigene Prozessprobe: `warnings=reject` aus der Datei ergibt `WARNINGS_REJECTED`; `--warnings allow` auf der CLI ergibt Exit 0. `--explain-config` zeigt `CONFIGURATION_FILE` bzw. `CLI`.
- Eigene In-Process-Probe (über die vorhandenen Vertragstests): Flushfehler nach erfolgreichem Schreiben ergibt `OPERATIONAL_FAILURE`, das fertiggestellte Artefakt bleibt in `CliExecutionResult.artifacts()`, der Elternlauf ist `Rejected[OPERATIONAL_FAILURE]`, es gibt genau einen Start und einen Abschluss und keine temporäre Datei.
- F1 ist geschlossen: `--explain-config` redigiert URI-Zugangsdaten und Queries für hostlose, opake und fehlerhafte URIs sowie für Werte aus CLI und Konfigurationsdatei. Die eigenen Proben A1–A4 und die Nachstellung der drei ursprünglichen F1-Werte fanden kein `marked-secret` mehr.
- Capabilities nennen weiterhin `qualification: P3_OPEN`; die in `a096d7e` nach `features` verschobenen Sprachmerkmale sind durch den G2-Abschluss (AC2-03/04/06) und die im `check`-Lauf grüne Compiler-Suite gedeckt. Es wird keine P3-1-Abnahme dieser Merkmale behauptet.

### AC-P3-03 – Formatter-API: **PASS**

Belege:

- `DefaultModelFormatter` normalisiert bei expliziter LF-/CRLF-Wahl gewöhnliche mehrzeilige Kommentare. Zeilenenden innerhalb werttragender Literale und mehrzeiliger Metaattributwerte werden mit `VALUE_LINE_ENDING_PRESERVED` bewahrt; `PRESERVE` bewahrt alle ursprünglichen Zeilenenden.
- Die Änderung ist präzise: Für geschützte Kommentarzeilen wird beim Zusammensetzen die gewählte Zeilenendeoption verwendet; nur `valueLineEndings` behält das Originalzeichen. Die frühere P3-AUD-04-Ursache ist damit beseitigt.
- `FormatterContractTest` (5 Tests) und `P3FormatterPolicyContractTest` (2 Tests) sind grün: 2.3/2.4, LF/CRLF/PRESERVE, Idempotenz, Re-Parse, Kommentar-/Unicode-Erhalt und Metadatenwerte.
- Eigene Prozessprobe D: Eine Quelle mit mehrzeiligem Kommentar und `END M.` auf der letzten geschützten Kommentarzeile ergab bei `--line-ending crlf` 4 CRLF und 0 reine LF; der zweite Lauf war bytegleich idempotent; `check` auf dem Ergebnis war Exit 0.
- Die frühere Metadatenprobe aus dem Erstbericht bleibt gültig: Ein werttragender LF im Metawert bleibt mit Hinweis erhalten, während die übrige Ausgabe CRLF verwendet.

### AC-P3-04 – Atomare CLI-Ausgabe und Fehlerpfade: **PASS**

Belege:

- `CliOutputWriter` staged im Zielverzeichnis, prüft Abbruch nach dem Staging und unmittelbar vor der Ersetzung, vergleicht die gelesenen Originalbytes, verweigert symbolische Ziele, entfernt temporäre Dateien bei Fehlern und verwendet `ATOMIC_MOVE` ohne nichtatomaren Fallback.
- `P3AtomicOutputContractTest` (8 Tests) prüft fehlenden atomaren Move, IO-Fehler, Abbruch nach Staging, externen Edit, Rechteerhalt, spätere Batch-Fehler mit genau den fertiggestellten Dateien, einen unerwarteten Schreibdefekt mit `FailureId` ohne Stacktrace sowie Alias-Vorprüfungen.
- F2 ist geschlossen: `OutputPlanner` erkennt Case-, Hardlink-, Symlink-, Symlink-Verzeichnis- und `file:`-URI-Aliase über `toRealPath`, `fileKey` und `Files.isSameFile` vor jedem Schreiben. Die ursprüngliche Reproduktion `format --write Model.ili model.ili` endet nun mit Exit 2, `Model.ili` bleibt unverändert, es bleibt keine `.interlis-output-*`-Datei zurück.
- Eigene Prozessproben B: Symlink-Verzeichnis-Alias, `file:`-URI-Alias und Symlink-Datei-Alias enden jeweils mit Exit 2 und unveränderten Quellen; zwei inhaltlich gleiche, aber verschiedene Dateien werden weiterhin beide formatiert (Exit 0), es gibt also keine Über-Ablehnung.
- Eigene Prozessprobe C: Eine schreibgeschützte Zieldatei (Modus 444) wird atomar ersetzt, der Modus bleibt 444, es bleibt keine temporäre Datei zurück.
- Eigene Prozessprobe H: Bei einem Syntaxfehler in einer von zwei `--write`-Quellen endet der Lauf mit Exit 1; die gültige Datei bleibt unverändert, es wird keine temporäre Datei zurückgelassen. Die Formatierung wird also vor jedem Schreiben vollständig geprüft.
- Eigene Prozessprobe (Erstbericht): `format --write First.ili Link.ili` mit einer zweiten Symlink-Quelle endet Exit 1; die erste Datei ist formatiert, der Symlink bleibt Symlink, das reale Ziel bleibt unverändert, keine temporäre Datei.
- Flush-/Abbruch-/Teilartefaktverhalten ist durch `P3CliLifecycleContractTest` und `P3AtomicOutputContractTest` gedeckt: Ein späterer Ausgabefehler ergibt keinen Erfolg; bereits fertiggestellte Dateien bleiben als Artefakte sichtbar; eine atomare Mehrdateitransaktion wird nicht behauptet.

## 4. P3-AUD-01 bis P3-AUD-04

| Audit-ID | Verdikt | Beleg / offene Stelle |
|---|---|---|
| P3-AUD-01 | **PASS** | 44 Optionen tatsächlich inventarisiert; 32 Ablehnungen separat ausgeführt; Hilfe/Version ohne Compilerinitialisierung; Dateien ohne Unterbefehl; Streams/Exits gegen echte Referenz; Abweichungen dokumentiert. |
| P3-AUD-02 | **PASS** | Präzedenz, Herkunft, Eltern-/Kindlauf und Flushfehler grün; F1-Redaktion für CLI- und Dateiwerte geschlossen; Capabilities bleiben `P3_OPEN`. |
| P3-AUD-03 | **PASS** | `CliOutcomeMapper` prüft `ENVIRONMENT_FAILURE` vor `INCOMPLETE_SUPPORT` vor `MISSING/AMBIGUOUS_INPUT` vor `SOURCE_ERRORS` vor alleinigem `WARNINGS_REJECTED`; `P3OutcomePriorityContractTest` prüft alle 63 nichtleeren Kombinationen; alle Gründe bleiben über `rejectionSummary` sichtbar. |
| P3-AUD-04 | **PASS** | Gewöhnliche mehrzeilige Kommentare werden bei LF/CRLF normalisiert; werttragende Literale/Metadaten bleiben mit Hinweis erhalten; `PRESERVE` bewahrt; idempotent und wieder parsebar. |

## 5. F1 bis F5, Windows-UTF-8 und Evidenzpfade

| Finding | Status | Beleg am Kandidaten |
|---|---|---|
| F1 URI-Redaktion | **geschlossen** | `safeValue` redigiert hostlose Query-URIs, opake URIs und fehlerhafte URI-Werte; CLI- und Dateikonfiguration geprüft. Eigene Werte `file:///tmp/x?token=…`, `mailto:…`, `https://user:…@…/%`, scheme-relativ `//user:…@…?…`, relative Query und percent-encoded Userinfo: kein `marked-secret` in stdout/stderr. |
| F2 Dateialias | **geschlossen** | `OutputPlanner` prüft Realpfade, FileKeys und `sameFile` vor dem Schreiben; Case-, Hardlink-, Symlink-, Symlink-Verzeichnis- und `file:`-URI-Aliase werden mit Exit 2 abgewiesen; die ursprüngliche Case-Reproduktion verändert keine Datei. |
| F3 Testnamen | **geschlossen** | Kapitel 13 nennt nun `P3CliLifecycleContractTest` und `P3OutcomePriorityContractTest`. Kapitel 16 §8 bezeichnet seine Namen ausdrücklich als „zu implementierende Tests“ und erhebt keinen Ausführungsanspruch; der dortige `RunContextPropagationTest`-Eintrag ist damit keine falsche Existenzbehauptung. |
| F4 Environmenttabelle | **geschlossen** | Kapitel 18 nennt die tatsächlichen Ressourcen: Arbeitsverzeichnis, stdin/stdout/stderr, immutable Environmentwerte und `CompilerServices` (Uhr/RunObserver/RunContextFactory); keine nicht vorhandenen Repositorydienste oder Profillader. |
| F5 Diagramm-`rootCount` | **geschlossen** | `DefaultCompilerCli` setzt `invocation.rootCount = roots.size()` im Diagrammpfad; der Elternlauf meldet die tatsächlich angeforderten Wurzeln. Eigene Beobachtung: `diagram --root M` meldet im Elternabschluss `Succeeded[rootCount=1, …compiledSchemaProduced=true]`. |
| Windows-UTF-8 | **geschlossen (lokal)** | `CliEnvironment.system()` umhüllt stdout/stderr mit expliziten UTF-8-`PrintStream`s, ohne die globalen Streams zu ersetzen oder zu schliessen. Eigene Launcher-Probe mit `JAVA_OPTS=-Dstdout.encoding=windows-1252 -Dstderr.encoding=windows-1252` erhält `Grüezi 😀`; Exit 0, stderr leer. Die tatsächliche Windows-x64-Ausführung wird hier nicht wiederholt. |
| Evidenzpfade | **geschlossen (lokal)** | `RuntimeReports`/`CatalogueEvidence` normalisieren Pfade portabel und akzeptieren Unix-, Windows- und UNC-Locator sowie die beiden Launcherformen, ohne die Kommando-/Exit-/Hashprüfungen abzuschwächen. `RuntimeReportPathsTest` ist im `evidenceTest` grün. Die GitHub-Läufe auf Windows/Linux werden hier nicht neu ausgeführt. |

## 6. Eigene unabhängige Gegenproben

Alle Proben liegen unter `build/probe/r2/` (ignoriert) und sind nicht Teil des Produkts oder der Suite. Die Hauptprobe ist bewusst an Grenzen angesetzt, die die Implementierungstests nicht abdecken: scheme-relative/relative URI-Redaktion, Symlink- und `file:`-URI-Aliase, schreibgeschütztes Ziel, Kommentar-und-Code auf derselben geschützten Zeile, leere `--modeldir`-Einträge und der ausgelieferte Launcher unter erzwungener Windows-Kodierung.

| Probe | Kommando (verkürzt) | Beobachtung | Exit |
|---|---|---|---|
| A1 F1 scheme-relativ | `check --config a1.properties --explain-config` mit `repository=//user:marked-secret@example.org/models?token=marked-secret` | Ausgabe `//example.org/models?REDACTED`; kein Secret in stdout/stderr | 0 |
| A2 F1 relative Query | `check --config a2.properties --explain-config` mit `repository=relative/models?token=marked-secret` | Ausgabe `relative/models?REDACTED`; kein Secret | 0 |
| A3 F1 percent-encoded Userinfo | `check --repository 'https://user%3Amarked-secret@example.org/m' --explain-config` | kein Secret | 0 |
| A4 F1 Cache-Query | `check --repository https://example.org/models --cache 'logs?token=marked-secret' --explain-config` | Ausgabe `logs?REDACTED`; kein Secret | 0 |
| B1 F2 Symlink-Verzeichnis | `format --write Real/Model.ili AliasDir/Model.ili` | Exit 2, `duplicate formatting source`, Quelle unverändert, keine Stagingdatei | 2 |
| B2 F2 `file:`-URI-Alias | `format --write Clean.ili file:///…/Clean.ili` | Exit 2, Quelle unverändert | 2 |
| B3 F2 Symlink-Datei | `format --write Clean.ili Linked.ili` | Exit 2, Quelle unverändert, Symlink bleibt Symlink | 2 |
| B4 F2 Kontrolle | `format --write A.ili B.ili` (zwei verschiedene Dateien gleichen Inhalts) | Exit 0, beide formatiert, keine Stagingdatei | 0 |
| C Leserechte | `chmod 444 RO.ili; format --write RO.ili` | Exit 0, Modus vor/nach 444, Ziel ersetzt, keine Stagingdatei | 0 |
| D Kommentar+Code | `format --line-ending crlf D.ili` (mehrzeiliger Kommentar, letzte Kommentarzeile mit `END M.`) | 4 CRLF, 0 reine LF; zweiter Lauf bytegleich; `check` Exit 0 | 0 |
| E1 Legacy leere modeldir | `ili2c --modeldir 'a;;b' E.ili` | Exit 1, „empty model directory“, kein Stacktrace | 1 |
| E2 Legacy modeldir=Datei | `ili2c --modeldir E.ili E.ili` | Exit 1, kein Stacktrace | 1 |
| F Windows-Kodierung | `JAVA_OPTS='-Dstdout.encoding=windows-1252 -Dstderr.encoding=windows-1252' interlis-cli format F.ili` | Exit 0, `Grüezi 😀` erhalten, stderr leer | 0 |
| G Original F1 | drei Werte aus dem Erstbericht, je CLI und Konfigurationsdatei | Exit 0, kein `marked-secret`; Ausgaben `file:/tmp/x?REDACTED`, `mailto:REDACTED`, `REDACTED_URI` | 0 |
| H Original F2 | `format --write Model.ili model.ili` | Exit 2, `duplicate formatting source`, Quelle unverändert, keine Stagingdatei | 2 |
| I Syntax-Batch | `format --write Good.ili Bad.ili` und umgekehrte Reihenfolge | Exit 1, `Good.ili` unverändert, keine Stagingdatei | 1 |
| J F5 Diagramm | eigenes `DiagramRootProbe` gegen die installierte Distribution: `diagram --root M D.ili` | Elternabschluss `Succeeded[rootCount=1, analysisComplete=true, compiledSchemaProduced=true]`; Kind hat Parent-ID | 0 |

Hauptprobe `bash build/probe/r2/probe.sh`: **38 von 38 Prüfungen PASS, 0 FAIL, Exit 0** (`build/probe/r2/probe.log`). Zusätzliche Protokolle: `original-findings.log`, `syntaxbatch.log`, `diagram-root-probe.log`.

## 7. Quellbindung, Pins und Statuswahrheit

Die mechanische Bindungspflege ist konsistent und am Kandidaten tatsächlich geprüft:

- Alle 21 Dateipins in `docs/evidence/stabilization/g2-source-inventory.json` (inkl. `docs/spec/09`, `13`, `16`, `18`, Grammatiken und `BuiltinFunctions.java`) stimmen mit den tatsächlichen SHA-256 der Dateien überein.
- `g2-rule-matrix.json` bindet `g2-source-inventory.json` (`129441…c21f0`) und `g2-test-candidate-index.json` (`5e91d8…b2ba`) mit den tatsächlichen Hashes. Die historischen Zustände bleiben `MISSING_EVIDENCE` bzw. `CANDIDATE_ONLY`.
- Der aktive `testMethods`-Wert `731` stimmt mit den tatsächlich vorhandenen `@Test`-Methoden unter `interlis-compiler/src/test/java` überein (731).
- Die sechs `sourcePins` in `testdata/g2/completion-links.json` stimmen mit den tatsächlichen Dateien überein; die Prozess- und Formatter-Bindungen wurden für den neuen Kandidaten nachgeführt.
- `docs/evidence/p3/` ist unverändert; die Korpus-, Norm- und Suite-Pins wurden nicht umgeschrieben. `docs/g2/COMPILER_NORM_DECISIONS.md` ist unverändert.
- Die historischen Review-Hashes stimmen: `P3-1-a096d7e-FAIL.md` = `57af1a…9ab35`, `P3-voraudit.md` = `ef7ac8…1d2`.
- Die Capabilities-Korrektur (leere `partialFeatures`/`notImplemented`, `qualification: P3_OPEN`) ist gegen den G2-Abschluss AC2-03/04/06 und die im `check`-Lauf grüne Compiler-Suite plausibel; sie ist keine P3-1-Neuimplementierung und keine P3-Abnahme.

## 8. Findings nach Priorität

**Keine Hoch- oder Mittel-/MUSS-Findings.** Die früheren Blocker F1 und F2 sind geschlossen; F3 bis F5 sind bereinigt.

Nicht blockierende Hinweise (niedrig, vor G3 zu bereinigen, kein P3-1-MUSS):

- **O1 – Dokumentationsfrische:** `docs/p3/CLI_PROFILE.md` (Zeilen 97–104) beschreibt die erneute Windows-Ausführung noch als „bis zum Folgelauf offen“, während `docs/p3/STATUS.md` den zweiten und dritten GitHub-Lauf (Windows/Linux) als bestanden dokumentiert. Die Aussage ist historisch erklärbar, aber im Abschnitt „aktueller Nachweis“ nicht mehr aktuell.
- **O2 – Testzähler:** `docs/p3/implementation/P3-1.md` nennt für den Lauf `current-jvm-cli-r2.log` 1097 Fälle; der aktuelle Kandidat enthält durch die neuen Reviewkorrektur-Tests 1103 Fälle (208 Suites). Der genannte Rohlog ist nicht versioniert; die Zahl ist als historischer Lauf zu lesen.
- **O3 – Plattformabgrenzung:** Die dokumentierten Windows-/Linux-x64-GitHub-Läufe beziehen sich auf `ba08926`, nicht auf den aktuellen Kandidaten `9108b03`. Das ist für P3-1 unerheblich, hält aber die P3-5/G3-Plattformmatrix offen.
- **O4 – Härtung, kein Vertragsbruch:** `safeValue` redigiert Credentials und Queries, aber keine Fragmente (`#…`). Kapitel 18 verlangt derzeit nur Zugangsdaten und Queries; eine spätere Härtung könnte Fragmente ebenfalls redigieren.

Keines dieser Findings ist ein relevanter P3-1-MUSS-Blocker; der PASS ist daher nicht bedingt.

## 9. Nicht ausgeführt / nicht behauptet

- Kein `releaseCheck`, kein `conformanceCheck`, kein `checkPortable`, kein `checkBrowser`.
- Kein Chromium-/Firefox-/WebKit-/Safari-Lauf; `interlis-wasm-teavm/node_modules` fehlt.
- Kein WebImage-Build; GraalVM und Binaryen sind an den angegebenen Pfaden vorhanden, wurden aber für P3-1 nicht ausgeführt.
- Keine Linux-x64-/Windows-x64-Ausführung; die Plattformmatrix bleibt P3-5/G3. Die GitHub-Läufe werden aus den Statusangaben referenziert, nicht in dieser Sitzung wiederholt.
- Einzelne Gradle-Tasks des breiten `check`-Laufs waren `UP-TO-DATE`; die 1103 XML-Testfälle sind ein Aggregat, kein Beweis für einen vollständig frischen Lauf aller Module.
- Keine Neubewertung von G2; keine Abnahme von P3-2 bis P3-5; kein G3-PASS.
- Keine Änderung an Produkt, Tests, Spezifikation oder versionierter Evidenz; kein Commit/Push. Eigene Dateien ausschliesslich unter ignoriertem `build/probe/r2/`.

## 10. Gesamturteil

**P3-1: PASS.**

- AC-P3-01: **PASS**
- AC-P3-02: **PASS**
- AC-P3-03: **PASS**
- AC-P3-04: **PASS**
- P3-AUD-01: **PASS**
- P3-AUD-02: **PASS**
- P3-AUD-03: **PASS**
- P3-AUD-04: **PASS**

F1 bis F5 sind am Kandidaten `9108b0387d9f1b82f506d8dc64e301e8522137dd` geschlossen. Die Kernfunktionen sind durch aktuelle JVM-, CLI-, Formatter-, Atomaritäts-, Prozess- und eigene Gegenproben gedeckt. Es gibt keine offenen P3-1-MUSS-Findings. Dieses Urteil ist ausschliesslich P3-1; P3-2 bis P3-5 und das G3-Gesamtgate bleiben offen. Insbesondere die Plattformmatrix Linux x64/Windows x64/macOS arm64 und die Browser-/Safari-/WebImage-Nachweise sind nicht Teil dieses Teilgates und bleiben für G3 erforderlich.
