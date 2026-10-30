# Unabhängiger P3-Voraudit

**Status: AUDIT — kein G3-PASS. P3 ist nicht abgeschlossen.**

- Basis-SHA: `0e20e6b23fbcbdc3315f58d6e3c467fea8ee3d94`
- Worktree: `/tmp/interlis-java-P3-audit-0e20e6b-nopy`
- Arbeitsbaum: sauber (`git status --porcelain` leer); eigene Proben ausschliesslich unter ignoriertem `build/probe/`
- G2-Referenz: unveränderter Produktstand `1371841`; `git diff --name-only 1371841..HEAD | grep -v '^docs/'` ist leer. Die beiden jüngeren Commits ändern nur Dokumente.
- Dieser Audit ändert weder Produkt-, Test-, Build- noch Spezifikationsdateien. Kein Commit, kein Push, keine Veröffentlichung.

Die historischen `docs/evidence/p3/p3-*-summary.json` werden **nicht** als aktuelle P3-Freigabe gewertet. Sie sind teils ausdrücklich überholt (siehe P3-AUD-15) und widersprechen dem heutigen Code und den heutigen Gate-Abhängigkeiten.

## 1. Kurzurteil

Der Compiler- und Workspace-Kern ist für den G2-Stand unverändert nutzbar. Für P3 fehlen aber weiterhin die vier zugesagten Lieferstrecken in wesentlichen Teilen:

- **WP30 (CLI/Legacy/Formatierung):** kein Legacy-ili2c-Profil, keine Konfigurationspräzedenz, kein CLI-/Engine-Eltern-Kind-Lauf; Formatierung verletzt die explizite Zeilenendenwahl bei geschützten mehrzeiligen Kommentaren/Token.
- **WP31 (vollständiges LSP/Browser):** LSP analysiert synchron im Dokumentpfad, hat kein Latest-wins/Scheduler/Abbruch; ungültige `didChange`-Bereiche werden geklemmt statt abgewiesen; ungeöffnete Dokumente erzeugen interne JSON-RPC-Fehler mit Stacktrace; Worker-Restart verliert Mehrquellen-/Konfigurations-/Handle-Zustand; Bridge-Limits werden nur angekündigt, nicht durchgesetzt.
- **WP32 (Korpus/Messungen):** der eingefrorene Korpus umfasst 13 von 353 Indexeinträgen, enthält keine Imports/Closure und ist kein vollständiger offizieller Geo-Korpus; Browser-Benchmarks überspringen Modelle und melden den Lauf trotzdem als bestanden; echte Safari-/WebImage-Korpusmessungen fehlen; Sollbudgets, 1000-Edit-, Speicher- und Artefaktgrössenmessungen fehlen.
- **WP33 (SDK/Release):** `releaseCheck` ist kein P3-Freigabegate über Linux x64, Windows x64 und macOS arm64, enthält weder Javadoc noch Versionskonsistenzprüfung; die einzige P3-Versionsevidenz ist veraltet.

Damit ist **G3 offen**. Die fachlichen Nutzerprofile aus `docs/g2/COMPILER_NORM_DECISIONS.md` bleiben unverändert gültig; dieser Audit schlägt keine Profiländerung vor.

## 2. Tatsächlich geprüfte Umgebung und Kommandos

### 2.1 Umgebung

| Bestandteil | Tatsächlich vorhanden |
|---|---|
| Betriebssystem | macOS 15.8.1, Darwin 24.6.0, arm64 |
| JDK | Temurin 25.0.3+9-LTS, `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem` |
| Gradle | Wrapper 9.8.1 |
| Node | v24.22.0 unter `/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node` |
| ili2c-Referenz | 5.6.9, `/Users/stefan/.cache/interlis-java/ili2c.jar`, SHA-256 `37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e` |
| echtes Safari | Safari 27.0, `safaridriver` vorhanden (`/System/Cryptexes/App/usr/bin/safaridriver`) |
| Playwright | Paket im Repository gepinnt (`1.64.1`), aber `interlis-wasm-teavm/node_modules` **nicht vorhanden**; Browserverträge benötigen zuerst `npm install` |
| GraalVM Web Image | **nicht vorhanden**: `GRAALVM_HOME` leer, kein `native-image` gefunden |
| Binaryen | **nicht vorhanden**: `BINARYEN_HOME` leer, kein `wasm-as` nachgewiesen |
| Linux x64 / Windows x64 | **nicht vorhanden**; es wurde kein Plattformlauf behauptet |

Die Browser-, Safari- und WebImage-Pfade wurden in diesem Voraudit **nicht ausgeführt**. Das ist keine Scope-Streichung: Die fehlenden Nachweise bleiben offene P3-Mussstellen.

### 2.2 Direkt beobachtete Kommandos

```text
git rev-parse HEAD
-> 0e20e6b23fbcbdc3315f58d6e3c467fea8ee3d94

git status --porcelain
-> (leer)

JAVA_HOME=... ./gradlew --no-daemon :interlis-cli:installDist :interlis-lsp:installDist
-> BUILD SUCCESSFUL in 14s

JAVA_HOME=... ./gradlew --no-daemon \
  :interlis-cli:test --tests guru.interlis.cli.CliApplicationTest \
  :interlis-lsp:test --tests guru.interlis.lsp.LspFeatureTest
-> BUILD SUCCESSFUL in 7s
-> CliApplicationTest: 9 Tests, 0 failures, 0 errors, 0 skipped
-> LspFeatureTest: 9 Tests, 0 failures, 0 errors, 0 skipped

JAVA_HOME=... ./gradlew --no-daemon :interlis-compiler:javadoc
-> BUILD SUCCESSFUL in 9s
-> 100 Javadoc-Warnungen allein im Compiler-Modul (kein Gate)
```

Die gezielten Tests belegen den bestehenden Teilumfang, nicht die P3-Abnahme. Der äussere OpenCode-/Launcher-Transport wird hier nicht als erfolgreich behauptet; bewertet werden nur die oben direkt beobachteten Prozesse.

### 2.3 Eigene Gegenproben

Alle Proben liegen unter `build/probe/` (ignoriert).

| Probe | Ergebnis |
|---|---|
| `ili2c --help`, `ili2c -v` | Referenz 5.6.9 liefert Hilfe bzw. Version mit Exit 0 |
| `interlis-cli --help`, `-v`, `-oXSD`, `base24.ili` | jeweils Exit 2, `unknown command`, keine Legacy-Hilfe/Version |
| `build/probe/CliPriorityProbe.java` | `ENVIRONMENT_FAILURE + INCOMPLETE_SUPPORT` wird zu `INCOMPLETE_ANALYSIS` statt `OPERATIONAL_FAILURE` |
| `format --line-ending crlf` auf mehrzeiligem Kommentar | Ausgabe enthält `crlf=8` und `lf=2` (zwei Zeilenenden verletzen die explizite Wahl) |
| LSP `didChange` mit Bereich `line 99..0` | Bereich wird geklemmt, `TRAILING` wird angehängt, danach `ILI-SYN-0004`; keine Betriebskanalmeldung |
| LSP `hover` auf nie geöffnetes Dokument | JSON-RPC `-32603` „Internal error“ mit Java-Stacktrace im `data`-Feld |

## 3. Findings nach Lieferstrecke

Die IDs sind Audit-IDs, keine neuen Spezifikations-IDs. Alle Findings sind **P3-Muss** für G3, sofern nicht ausdrücklich als Evidenzfinding markiert.

### WP30 — CLI, Legacyprofil, Streams, Exitcodes, Konfiguration, RunContext, Formatierung

#### P3-AUD-01 — Das Legacy-ili2c-CLI-Profil fehlt

**Anforderungen:** CLI-003, CLI-004, COMPAT-001, COMPAT-002, GOAL-011; Kapitel 18 §2/§4.

**Befund:** `DefaultCompilerCli.execute` kennt nur die eigenen Befehle `version`, `capabilities`, `check`, `compile`, `analyze`, `diagram`, `format` (`interlis-cli/src/main/java/guru/interlis/cli/DefaultCompilerCli.java:77-85`). `parseFiles` kennt nur die neuen Optionen (`:464-533`). Die in Kapitel 18 verlangten Profilklassen `LegacyCliProfile`, `CommandSpec`, `DefaultCommandLineParser`, `ConfigurationLoader`, `InvocationPlanner`, `OutputPlanner`, `CliOutputWriter`, `LegacyExitCodePolicy` und `ToolVersionRenderer` existieren im Produktcode nicht; `grep` über `*.java` findet keine Definition.

Die gepinnte Referenz `ili2c 5.6.9` bietet unter anderem `-h|--help`, `-v|--version`, `-u|--usage`, `--no-auto`, `--modeldir`, `--proxy`, `--proxyPort`, `--quiet`, `--without-warnings`, `--trace`, `--log`, `--xtflog`, `--with-predefined` sowie die Ausgabemodi `-o0`, `-o1`, `-o2`, `-oXSD`, `-oGML`, `-oILIGML2`, `-oFMT`, `-oIMD`, `-oIMD16`, `-oNLS`, `-oUML`, `-oIOM`. Ein Aufruf `ili2c file1.ili file2.ili` ist das dokumentierte Grundmuster.

**Erwartetes Verhalten:** Für jede erfasste Legacyoption muss das Profil entweder eine echte Unterstützung oder eine begründete, getestete Ablehnung mit Referenzmatrix enthalten. Hilfe und Version müssen ohne Compilerinitialisierung funktionieren. Das Grundmuster „Dateien ohne Unterbefehl“ muss dem freigegebenen Profil entsprechen. Streams und Exitcodes müssen gegen die gepinnte Referenz belegt sein.

**Gegenprobe:**

```text
interlis-cli --help    -> exit 2, "unknown command: --help"
interlis-cli -v        -> exit 2, "unknown command: -v"
interlis-cli -oXSD     -> exit 2, "unknown command: -oXSD"
interlis-cli base24.ili-> exit 2, "unknown command: testdata/models/base24.ili"
ili2c -v               -> exit 0, Version 5.6.9-...
```

**Gezielter Test:** Prozessmatrix mit mindestens Hilfe, Version, leerer Invocation, unbekannter Option, einer Datei, mehreren Dateien, `--modeldir`, `--without-warnings`, `--quiet`, `--log` und je einem Generator-/Ausgabemodus; erwartet werden entweder Referenzgleichheit oder eine explizite, dokumentierte Ablehnung. Die Matrix muss im `releaseCheck` fail-closed hängen.

#### P3-AUD-02 — Konfigurationspräzedenz und CLI-Eltern-/Engine-Kindlauf fehlen

**Anforderungen:** CLI-005, Kapitel 09 §5, Kapitel 18 §3.1/§3.3; RunContext-/RunLifecycle-Verträge.

**Befund:** `CliEnvironment` enthält nur Arbeitsverzeichnis, stdin/stdout/stderr und eine Environment-Map (`interlis-cli/src/main/java/guru/interlis/cli/CliEnvironment.java:10-11`). Es gibt keine `RunContextFactory`, keinen `RunObserver`, keine Konfigurationsdatei-API und keinen Herkunftsnachweis für aufgelöste Einstellungen. `DefaultCompilerCli` ruft `workspace.compile(...)` ohne `RunContext` auf (`DefaultCompilerCli.java:203`) und `workspace.analyze(...)` ebenfalls ohne Kontext (`:247-249`). Damit fehlt der in Kapitel 18 §3.1 verlangte Elternlauf für Beschaffung/Analyse/Ausgabe und der frische Kindkontext für die Engine. Ein späterer Ausgabe-/Flushfehler kann so nicht als eigener Elternlaufabschluss modelliert werden; der vorhandene Flush-Check (`:58-64`) ist nur eine globale Nachkontrolle.

**Erwartetes Verhalten:** Präzedenz CLI-Argument > explizite Konfigurationsdatei > Profildefault, mit Herkunft je Einstellung und Konfliktfällen. Der CLI-Host erzeugt einen Elternkontext, die Engine einen Kindkontext; Start/Ende erfolgen genau einmal und werden über den injizierten Observer beobachtet.

**Gezielter Test:** Konfigurationskonflikt mit drei Quellen und erwartetem Herkunftsreport; `RunContextPropagationTest` mit injizierter Factory/Observer für genau einen Eltern- und einen Kindlauf; erfolgreicher Compile mit anschliessendem Ausgabefehler ergibt Kindlauf erfolgreich, Elternlauf `OPERATIONAL_FAILURE`.

#### P3-AUD-03 — `CliOutcomeMapper` verletzt die spezifizierte Priorität

**Anforderungen:** CLI-004, Kapitel 18 §2; Kapitel 13 §10 (`CliOutcomeMappingTest`).

**Befund:** `CliOutcomeMapper.classify` prüft `INCOMPLETE_SUPPORT` vor `MISSING_INPUT`/`AMBIGUOUS_INPUT`/`WARNINGS_REJECTED`/`ENVIRONMENT_FAILURE` (`interlis-cli/src/main/java/guru/interlis/cli/CliOutcomeMapper.java:24-39`). Kapitel 18 §2 legt die Reihenfolge `ENVIRONMENT_FAILURE` → `OPERATIONAL_FAILURE`, `INCOMPLETE_SUPPORT` → `INCOMPLETE_ANALYSIS`, `MISSING_INPUT`/`AMBIGUOUS_INPUT` → `INPUT_UNRESOLVED`, `SOURCE_ERRORS` → `SOURCE_REJECTED`, alleinige `WARNINGS_REJECTED` → `WARNINGS_REJECTED` fest. Der Code setzt `WARNINGS_REJECTED` zudem vor `ENVIRONMENT_FAILURE`.

**Gegenprobe:** `build/probe/CliPriorityProbe.java` erzeugt eine synthetische `RejectionSummary` mit `ENVIRONMENT_FAILURE` und `INCOMPLETE_SUPPORT` auf einer echten Analyse und ruft den Mapper auf:

```text
actual reasons=[SOURCE_ERRORS]        (nur Ausgangsanalyse)
mapper=INCOMPLETE_ANALYSIS
spec-priority-expected=OPERATIONAL_FAILURE
```

**Erwartetes Verhalten:** Bei mehreren Gründen gilt die dokumentierte Standardpriorität; alle Gründe bleiben im strukturierten Ergebnis sichtbar.

**Gezielter Test:** `CliOutcomeMappingTest` mit jeder Einzelursache und mit Kombinationen aller sechs `RejectionReason`-Werte; erwartet `OPERATIONAL_FAILURE` bei `ENVIRONMENT_FAILURE + INCOMPLETE_SUPPORT`.

#### P3-AUD-04 — Explizite Zeilenendenwahl wird bei mehrzeiligen Kommentaren/Token verletzt

**Anforderungen:** FMT-001, FMT-003, Kapitel 18 §6.1.

**Befund:** `DefaultModelFormatter` schützt Zeilen, die von mehrzeiligen Token/Kommentaren berührt werden (`interlis-compiler/src/main/java/guru/interlis/format/DefaultModelFormatter.java:61-72`), und verwendet für diese Zeilen beim Zusammensetzen das ursprüngliche Zeilenende statt der gewählten Option:

```java
String ending = protectedLines.contains(i) ? lines.get(i).ending()
        : lineEnding != null ? lineEnding ...
```

(`DefaultModelFormatter.java:129-131`). Damit ist `FormatOptions.LineEnding.CRLF` bzw. `LF` bei geschützten Zeilen nicht wirksam; die Ausgabe ist gemischt.

**Gegenprobe:** Eingabe mit mehrzeiligem Blockkommentar, Aufruf:

```text
interlis-cli format --line-ending crlf build/probe/multiline.ili
```

Beobachtung:

```text
{ bytes: 148, crlf: 8, lf: 2 }
```

Die Ausgabe enthält weiterhin zwei reine LF-Zeilenenden innerhalb des geschützten Kommentars.

**Erwartetes Verhalten:** Die gewählte Zeilenendenpolitik gilt für die gesamte Ausgabe; `PRESERVE` bleibt die einzige Ausnahme. Die Formatierung bleibt deterministisch und idempotent.

**Gezielter Test:** Mehrzeiliger Blockkommentar und mehrzeiliges String-Token, je `LF`, `CRLF` und `PRESERVE`; für `LF`/`CRLF` darf kein entgegengesetztes Zeilenende vorkommen. Zusätzlich Tokenfolge und Re-Parse prüfen.

### WP31 — vollständiges asynchrones LSP, Abbruch, Konfiguration, Completion/Tokens, Browser-Worker

#### P3-AUD-05 — LSP analysiert synchron im Dokumentpfad; kein Latest-wins, kein Scheduler, kein Analyseabbruch

**Anforderungen:** LSP-011, INC-022, Kapitel 07 §6.3; Kapitel 14 WP-31.

**Befund:** `didOpen` und `didChange` sind `synchronized` und rufen unmittelbar `analyze(...)` auf (`interlis-lsp/src/main/java/guru/interlis/lsp/InterlisTextDocumentService.java:113-144`). `analyze` ruft `workspace.analyze(..., CancellationToken.none())` auf (`:547-557`). Es gibt keinen `AnalysisScheduler`, keinen `PublicationController`, keine Dokumentgeneration und keinen abgetrennten `capture`/`engine.analyze`/`acceptAnalysis`-Pfad. Der einzige Executor ist der Compile-Command-Executor (`InterlisWorkspaceService.java:36-41,111-160`); er hilft dem Dokumentpfad nicht. Die LSP-Anfragen `hover`, `definition`, `completion`, `references`, `rename`, `formatting` verwenden ebenfalls `CancellationToken.none()` (z. B. `InterlisTextDocumentService.java:177-178,194-195,215-217`).

**Erwartetes Verhalten:** Die Notification-Queue nimmt Änderungen schnell an; Hintergrundanalysen laufen auf einem begrenzten Executor; ältere Läufe werden supersediert und nach Möglichkeit abgebrochen; nur ein `ACCEPTED`-Snapshot der aktuellen Dokumentgeneration wird veröffentlicht. Ein laufender Request-Abbruch muss in den Compiler gelangen oder über den dokumentierten Worker-Abbruch erfolgen.

**Gezielter Test:** Langsame Engine mit Latch; `didOpen` A, dann `didChange` B, während A blockiert. Erwartet: Notification kehrt zurück, A wird abgebrochen/supersediert, B wird allein veröffentlicht; eine parallele Hover-Anfrage bleibt bedienbar; danach ist die Sitzung funktionsfähig.

#### P3-AUD-06 — Ungültige `didChange`-Bereiche werden geklemmt und still angewandt

**Anforderungen:** LSP-004, LSP-016, Kapitel 07 §6.3.

**Befund:** `materialize` klemmt Start-/Endoffset auf die Textlänge (`InterlisTextDocumentService.java:611-616`); `offsetAt` klemmt Zeile und Spalte (`:627-633`). Kapitel 07 §6.3 verlangt dagegen: Bei ungültigen Client-Änderungsbereichen darf der Server nicht raten, muss den Dokumentzustand als unsynchronisiert markieren, den konkreten Fehler über den Betriebskanal melden und eine vollständige Resynchronisation verlangen; bis dahin sind schreibende Sprachoperationen abzulehnen.

**Gegenprobe:** LSP-Prozess, `didChange` mit `start.line=99, end.line=0`, Text `TRAILING`. Beobachtung:

```text
diagnostics: eine ILI-SYN-0004 "trailing ... value=TRAILING"
betriebskanal: []  (keine window/logMessage/window/showMessage)
compile: outcome=rejected, reasons=[SOURCE_ERRORS]
```

Der Server hat den ungültigen Bereich also angenommen und den Text verändert.

**Erwartetes Verhalten:** Keine Mutation; typisierter Fehler auf dem Betriebskanal; Dokument als unsynchronisiert markieren; schreibende Operationen bis zur Resynchronisation ablehnen.

**Gezielter Test:** `start > end`, Zeile ausserhalb des Dokuments, Spalte hinter dem Zeilenende, überlappende Deltas und Delta vor `didOpen`; jeweils Zustand, Fehlermeldung und anschliessende Schreiboperation prüfen.

#### P3-AUD-07 — Anfrage auf ungeöffnetes Dokument erzeugt internen JSON-RPC-Fehler mit Stacktrace

**Anforderungen:** LSP-016, Kapitel 07 §6.3; Kapitel 18 §5 (Protokoll-/Compiler-/interne Fehler trennen).

**Befund:** `requireState` wirft bei fehlendem Dokument eine `IllegalStateException` (`InterlisTextDocumentService.java:538-545`). LSP4J wandelt sie in `-32603 Internal error`; der Java-Stacktrace landet im `data`-Feld der Antwort. Es gibt keinen `LspErrorMapper`.

**Gegenprobe:** `textDocument/hover` auf `file:///tmp/never-opened.ili`:

```json
{
  "error": {
    "code": -32603,
    "message": "Internal error.",
    "data": "java.lang.IllegalStateException: document is not open: ...\n\tat guru.interlis.lsp.InterlisTextDocumentService.requireState(...)"
  }
}
```

**Erwartetes Verhalten:** Typisierter Protokollfehler (z. B. `InvalidParams`/`RequestFailed`) ohne Stacktrace und ohne Offenlegung von Implementierungsdetails. Interne Fehler bleiben echten internen Defekten vorbehalten.

**Gezielter Test:** `hover`, `definition`, `completion`, `documentSymbol`, `formatting`, `semanticTokens/full` und `prepareRename` auf einer nie geöffneten URI; erwartet wird ein stabiler, nicht-interner Fehlercode und eine intakte Sitzung.

#### P3-AUD-08 — Completion verliert `isIncomplete`, Ersetzungs-/Einfügetext und Scope-/Sprachfilter

**Anforderungen:** LSP-007, LSP-013, Kapitel 07 §5.1.

**Befund:** Der LSP-Adapter übernimmt nur `candidate.label()`, `kind` und `detail` (`InterlisTextDocumentService.java:218-224`). `CompletionResult.incomplete()` und `replacementRange()` werden ignoriert; `CompletionItem.insertText` wird nicht gesetzt. `LanguageQueryService.complete` filtert ausschliesslich nach Präfix und synthetischen Namen, sortiert alphabetisch und berücksichtigt weder Scope/Sichtbarkeit noch Sprachversion (`interlis-compiler/src/main/java/guru/interlis/compiler/query/LanguageQueryService.java:127-137`). Kapitel 07 §5.1 verlangt Scope, Sprachversion, stabile Kategoriesortierung und ausdrückliche Unvollständigkeit.

**Erwartetes Verhalten:** `CompletionList.isIncomplete` wird gesetzt, wenn das Limit erreicht ist; Einfügetext und Ersetzungsbereich werden transportiert; Kandidaten sind auf den belegten Scope und die Sprachversion begrenzt.

**Gezielter Test:** Limit 1 bei mehreren Kandidaten → `isIncomplete=true`; 2.3-Modell darf keine ausschliesslich 2.4-gültigen Symbole anbieten; ein lokales Topic darf keine gleichnamigen Symbole aus fremdem Scope anbieten.

#### P3-AUD-09 — Browser-Worker-Restart stellt den vollständigen Zustand nicht wieder her; Abbruch-API fehlt

**Anforderungen:** WASM-009, WASM-011, Kapitel 10 §4/§6.2; Kapitel 14 WP-31.

**Befund:** `client.js` hält nur eine Quelle `memory:/browser.ili` und einen einzelnen `schemaId`/`snapshotId` (`interlis-wasm-teavm/src/main/web/client.js:46-56,92-119`). `restartWorker` beendet den Worker, leert `pending`, setzt den Zustand zurück und öffnet ausschliesslich den aktuellen Textarea-Inhalt neu (`:58-69`). Mehrere Quellen, Konfiguration/Overlays, gehaltene Snapshots und alte Handles werden nicht wiederhergestellt. `worker.js` kennt nur `onmessage` und `handle`; es gibt keinen Abbruchkanal, keine Epochenprüfung und kein `cancel` (`interlis-wasm-teavm/src/main/web/worker.js:14-23`). Eine öffentliche `CompilerClient`-API mit `cancel`/`dispose` gemäss Kapitel 10 §6.1 fehlt im ausgelieferten Webclient.

**Erwartetes Verhalten:** Der Host besitzt eine bestätigte Kopie aller Quellen und der Konfiguration; nach einem Worker-Neustart wird der vollständige Zustand in einer neuen Sitzung aufgebaut. Alte Snapshot-/Schema-Handles werden ungültig; neue IDs werden gemeldet. Laufende Anfragen können abgebrochen werden (`notStarted`, `cooperative`, `workerRestarted`, `alreadyCompleted`, `unknownRequest`).

**Gezielter Test:** Zwei Quellen mit Import, Overlay-Konfiguration und einem gehaltenen alten Snapshot; Worker neu starten; erwartet werden gleiche Analyse-/Query-Ergebnisse für beide Quellen, gültige neue Handles, `SNAPSHOT_EXPIRED` für alte Handles und ein `cancelled`-Ergebnis für eine laufende Anfrage.

#### P3-AUD-10 — Bridge-Limits und Snapshot-/Schema-Retention werden nicht durchgesetzt

**Anforderungen:** WASM-009, WASM-015, Kapitel 10 §6.4; Kapitel 10 §5.5.

**Befund:** `SessionRegistry.open` legt `maxSources`, `maxRetainedSnapshots` und `maxMessageUtf16Length` als feste Antwortwerte ab (`interlis-wasm-bridge/src/main/java/guru/interlis/bridge/SessionRegistry.java:64-71`), prüft aber nur die Sitzungszahl (`MAX_SESSIONS=8`, `:26-40`). `CompilerSessionFacade` hält Snapshots und Schemas in unbeschränkten `LinkedHashMap`s (`CompilerSessionFacade.java:62-63`) und fügt bei jedem `analyze`/`compile` weitere Einträge hinzu (`:141,165-177`). `releaseSnapshot` entfernt nur (`:248-252`); es gibt keinen `SnapshotRegistry`, keine Pin-/Retention-Zählung und keine Eviction-Policy. `BridgeCodec.decode` parst die vollständige JSON-Nachricht, bevor irgendeine Längenprüfung möglich wäre (`BridgeCodec.java:21-47`); `maxMessageUtf16Length` wird nirgends geprüft.

Zusätzlich ignoriert `CompilerSessionFacade.analyze` das Ergebnis von `workspace.acceptAnalysis(capture, analysis)` (`:140`) und registriert den Snapshot unabhängig von `ACCEPTED`/`STALE_NO_COMMIT`. Der Bridge-Analysepfad übergibt keinen `RunContext`; `CancellationToken.none()` wird verwendet (`:136,160`).

**Erwartetes Verhalten:** Überschreitungen liefern `LIMIT_EXCEEDED` vor einem Commit; explizit gehaltene Handles werden nicht still entfernt; ein neues Ergebnis, das wegen gehaltener Snapshots nicht registriert werden kann, wird abgelehnt. Nur ein `ACCEPTED`-Ergebnis darf einen aktuellen Snapshotzeiger setzen. Die Nachrichtenlänge wird vor dem vollständigen Dekodieren geprüft.

**Gezielter Test:** 33 kompilierte Snapshots bei angekündigtem Limit 32 → `LIMIT_EXCEEDED`; Freigabe eines alten Handles erlaubt danach die Registrierung; eine Nachricht über 4 MiB UTF-16 wird vor dem Dekodieren abgelehnt; ein `STALE_NO_COMMIT`-Capture wird nicht als aktueller Snapshot registriert.

### WP32 — eingefrorener Geo-Korpus, reale Laufzeitmatrix, Messungen und Budgets

#### P3-AUD-11 — Der Korpus ist ein 13-Modell-Seed, nicht der vollständige offizielle Geo-Korpus mit Import-Closure

**Anforderungen:** TEST-013, Kapitel 13 §8, Kapitel 14 WP-32; GOAL-012.

**Befund:** `docs/evidence/p3/geo-corpus.json` wählt ausdrücklich „thirteen standalone, compilable 2.3/2.4 models“ und nennt die Bytes „cached outside the repository“ (`:174-175`). `GeoCorpus.verify` erzwingt genau 13 Modelle (`interlis-cli/src/verification/java/guru/interlis/cli/verification/GeoCorpus.java:26`) und schreibt selbst „historical seed corpus, not full P3“ (`:32`). Der gepinnte `ilimodels.xml`-Index enthält 353 `<File>`-Einträge; die 13 ausgewählten Dateien sind ca. 3,7 % davon. Keine der 13 Dateien enthält eine `IMPORTS`-Deklaration; Import-Closures werden damit überhaupt nicht gemessen. `docs/evidence/p3/performance-summary.md:11-12` bestätigt: „still a seed, not a production-scale corpus“.

**Erwartetes Verhalten:** Ein eingefrorener, lokal reproduzierbarer Korpus des offiziellen Geo-Bestands mit aufgelösten Import-Closures; jede benötigte Abhängigkeit ist gepinnt und offline verfügbar. Die Auswahl darf nicht auf „standalone“ beschränkt werden, nur weil Closures fehlen.

**Gezielter Test:** `geoCorpus` muss den vollständigen freigegebenen Modellbestand samt transitiven Importen prüfen; ein Modell mit externem Import darf ohne Netz und ohne manuell fehlende Datei kompilieren; ein entferntes Importziel muss als `MISSING_INPUT` erscheinen.

#### P3-AUD-12 — Browser-Benchmarks werten übersprungene Modelle als bestandenen Lauf; echte Safari-/WebImage-Korpusmatrix fehlt

**Anforderungen:** TEST-008, TEST-013, WASM-006, WASM-018, Kapitel 10 §7.1/§7.3, Kapitel 13 §8/§12.

**Befund:** `browser-benchmark.cjs` überspringt `IliValidGwr_V1_0` in jedem Browser und auf WebKit zusätzlich alle Modelle ausser `SO_FunctionsExt` (`interlis-wasm-teavm/browser-benchmark.cjs:53-60`) und setzt den Browserstatus trotzdem auf `passed` (`:79`). Die vorhandene Evidenz bestätigt 1/1/12 Skips (`docs/g2/STATUS.md:47-48`). `docs/evidence/p3/performance-summary.md:22,41-44` nennt die WebKit-Fälle „skipped“. `p3-4-summary.json` setzt `status: "VERIFIED"` und `"browserModels": 3` trotz dieser Lücken (`:4,12,21-24`). Der Performance-Pfad `performanceCheck` enthält nur `jvmBenchmark`, `wasmBenchmark`, `browserBenchmark` (`build.gradle:126-130`); echte Safari- und WebImage-Korpusmessungen fehlen. `browserBenchmark` führt nur Chromium/Firefox/WebKit aus (`browser-benchmark.cjs:44`), nicht Safari.

**Erwartetes Verhalten:** Ein übersprungenes Pflichtmodell ist kein PASS. Die zugesagte Matrix Chromium/Firefox/echtes Safari/WebImage muss für den freigegebenen Korpus tatsächlich laufen; nicht verfügbare Umgebungen werden als `BLOCKED`/`NOT_RUN` geführt und halten G3 offen. Ein `status:"passed"` mit Skip-Liste ist unzulässig.

**Gezielter Test:** Benchmark bricht mit Exitcode ≠ 0 ab, sobald ein Modell für einen Pflichtbrowser `skipped` ist; separater Safari-Lauf über denselben Korpus; separater WebImage-Lauf über denselben Korpus; Ergebnismanifest enthält Browser-/Runtimeversion und explizite Skip-/Blocked-Gründe.

#### P3-AUD-13 — Sollbudgets sowie 1000-Edit-, Speicher- und Artefaktgrössenmessungen fehlen

**Anforderungen:** TEST-010, NFR-004, WASM-015, WASM-018, Kapitel 10 §7.3, Kapitel 13 §9.

**Befund:** Es gibt keinen versionierten Budgetkatalog. `performanceCheck` misst je Modell 1 Warmup + 3 Läufe (`JvmBenchmark.java:31-45`, `wasm-benchmark.cjs`), aber keine 1000-Edit-Sequenz, keine Batch-/Abbruchmessung im Editor, keine Retention-/Heap-Messung und keine vollständige Artefaktgrösse (nur `wasmBytes` in `performance-wasm.json`; JS-Loader, Runtime, komprimiert/unkomprimiert und Browserpaket fehlen). `docs/spec/13-tests-kompatibilitaet-und-qualitaet.md:309` und `:402-404` verlangen getrennte Soll-/Ist-Werte; `docs/spec/14-implementierungsplan-und-arbeitspakete.md:179` nennt die Budgetfreigabe ausdrücklich als WP-32-Aufgabe. Die Suche nach `1000`/`1_000` in den Workspace-/LSP-/WASM-Tests findet keinen 1000-Edit-Test.

**Erwartetes Verhalten:** Ein versioniertes Budgetmanifest trennt Sollwerte von gemessenen Werten. Gemessen werden mindestens Kaltstart, erste Analyse, wiederholte Analyse, Ein-Datei-Änderung, 1000 Edits, Speicher/Retention, Abbruch und Artefaktgrössen (unkomprimiert/komprimiert) je Zielruntime. Jede Veröffentlichung nennt Hardware, Runtime, Warmup und Messmethode.

**Gezielter Test:** `PerformanceBudgetTest` schlägt bei Überschreitung eines Sollwerts fehl; 1000-Edits-Szenario mit kleinen Cache-/Snapshot-Limits; Speicherprobe mit gehaltenen und freigegebenen Snapshots; Artefaktgrössenvergleich gegen das Budget.

### WP33 — SDK/CLI-Paket, Versionen, Javadoc, Plattformprofile, `releaseCheck`

#### P3-AUD-14 — `releaseCheck` ist kein P3-Freigabegate über die drei Plattformen und prüft Javadoc/Versionen nicht

**Anforderungen:** BUILD-002, BUILD-008, SDK-002, SDK-003, API-003, Kapitel 03 §6/§7, Kapitel 14 WP-33.

**Befund:** `releaseCheck` aggregiert `conformanceCheck`, `performanceCheck`, `check`, `checkPortable`, `checkBrowser`, `sdkProcessCheck`, `sdkPackage` und optional `webImageProbe` (`build.gradle:76-89`). Es gibt keinen Task für Linux-x64-/Windows-x64-/macOS-arm64-Pakete oder Prozessläufe; `sdkPackage` packt ausschliesslich die lokale `installDist` (`build.gradle:136-153`). `javadoc` ist nicht als Abhängigkeit enthalten; der Javadoc-Lauf des Compiler-Moduls endet zwar mit `BUILD SUCCESSFUL`, aber mit 100 Warnungen, und README/Status akzeptieren die Javadoc-Restlücke ausdrücklich als P1-Rest (`README.md:98-99`, `docs/implementation-status.md:1021-1022`). Eine Prüfung, dass das erzeugte Versionsmanifest zu den kompilierten Konstanten passt, fehlt.

**Erwartetes Verhalten:** `releaseCheck` ist fail-closed: fehlende Plattform, fehlendes Javadoc-Artefakt, fehlende/abweichende Version oder fehlendes Pflichtprofil führen zu einem Fehler, nicht zu einem Skip. Die drei zugesagten Prozessprofile werden tatsächlich gebaut und ausgeführt.

**Gezielter Test:** Release-Gate mit simuliert fehlender Linux-/Windows-Plattform, fehlendem Javadoc und absichtlich falschem Versionsmanifest; alle drei Fälle müssen den Lauf nicht-grün machen. Zusätzlich `javadoc` mit `-Xdoclint:all` als Pflicht-Task.

#### P3-AUD-15 — P3-Versionsevidenz und Teile der P3-Messevidenz sind veraltet; historische `VERIFIED`-Summaries sind keine Freigabe

**Anforderungen:** SDK-003, TEST-002, Kapitel 03 §6 (Evidenzaktualisierung), Kapitel 13 §13.

**Befund:** `docs/evidence/p3/version-manifest.json:3-4` nennt `0.1.0-SNAPSHOT` und API `0.1`; der aktuelle Code und die aktuelle CLI nennen `0.2.0-SNAPSHOT` und API `0.2` (`interlis-model/src/main/java/guru/interlis/model/VersionManifest.java:28-30`; `interlis-cli ... version --json`). `docs/evidence/p3/p3-5-summary.json` verweist weiterhin auf das 0.1-Manifest. `docs/evidence/p3/performance-wasm.json:4` wurde mit Node `v22.23.1` erzeugt, während der aktuelle Hostpin `24.22.0` ist (`gradle.properties:2`) und Node 22.23.1 laut Spezifikation wegen eines reproduzierten V8-Wasm-Stillstands ausgeschlossen bleibt. `docs/evidence/p3/p3-4-summary.json:4,21-24` trägt `VERIFIED`, obwohl es dieselben Skips und die fehlende Produktionskorpusgrösse als Limitations nennt.

**Erwartetes Verhalten:** P3-Evidenz wird mit dem aktuellen Toolchain-/Versionsstand neu erzeugt und als aktuell markiert; historische Pakete bleiben historisch. Ein `VERIFIED` darf keine offenen Pflicht-Skips enthalten. `releaseCheck` prüft die erzeugte Versionsevidenz gegen die kompilierten Konstanten.

**Gezielter Test:** `VersionManifest`-Gleichheit zwischen CLI-JSON, Build-Manifest und Evidenz; Evidenz-Freshness-Check gegen `projectVersion`, Node-Pin und Korpus-Hash; `p3-*-summary` darf nicht `VERIFIED` sein, solange Pflichtfälle `skipped` sind.

## 4. Konsolidierte P3-Mussliste

| ID | Lieferstrecke | Kern der offenen Mussstelle | Blockierend für G3 |
|---|---|---|---|
| P3-AUD-01 | WP30 | Legacy-ili2c-Optionen/Profil/Hilfe/Version/Streams/Exitcodes | ja |
| P3-AUD-02 | WP30 | Konfigurationspräzedenz, CLI-Elternlauf und Engine-Kindkontext | ja |
| P3-AUD-03 | WP30 | `CliOutcomeMapper`-Priorität | ja |
| P3-AUD-04 | WP30 | Zeilenendenpolitik der Formatierung | ja |
| P3-AUD-05 | WP31 | asynchrones LSP, Latest-wins, Abbruch | ja |
| P3-AUD-06 | WP31 | ungültige `didChange`-Bereiche | ja |
| P3-AUD-07 | WP31 | LSP-Fehlermapping/keine Stacktrace-Leaks | ja |
| P3-AUD-08 | WP31 | Completion-Vollständigkeit/Scope/Sprachversion | ja |
| P3-AUD-09 | WP31 | Worker-Restart-Zustand, Handles, Abbruch | ja |
| P3-AUD-10 | WP31 | Bridge-Limits, Retention, `acceptAnalysis`, RunContext | ja |
| P3-AUD-11 | WP32 | vollständiger Korpus und Import-Closure | ja |
| P3-AUD-12 | WP32 | keine Skip-PASS; Safari/WebImage-Korpus | ja |
| P3-AUD-13 | WP32 | Sollbudgets, 1000 Edits, Speicher, Artefaktgrössen | ja |
| P3-AUD-14 | WP33 | Plattformmatrix, Javadoc, fail-closed `releaseCheck` | ja |
| P3-AUD-15 | WP33 | aktuelle Versions-/Messevidenz statt historischer `VERIFIED`-Summaries | ja |

## 5. Positive Abgrenzung (kein P3-Fortschritt, aber korrekt)

- **Keine Modellgeneratoren, kein IlisMeta07/16, kein Legacy-Java-API-Adapter, keine DB-/iox-/CABI-Adapter** im aktuellen Baum; `check --export` wird als unbekannte Option abgewiesen. Das entspricht der P3-Scope-Entscheidung.
- **G2 bleibt unberührt:** Die jüngeren Commits seit `1371841` ändern nur `docs/`; dieser Audit bewertet keine G2-Regression.
- **Bestehende P1-/G2-Teilfunktionen** wie Format-API, grundlegende LSP-Navigation/Rename, Bridge-Protokoll, TeaVM-WasmGC/JS und WebImage-Bootstrap sind vorhanden. Sie sind aber nicht die in P3 verlangte vollständige Integration.

## 6. Nicht ausgeführt / nicht behauptet

- Kein vollständiger `check`-, `checkPortable`-, `checkBrowser`-, `releaseCheck`- oder G2-Gesamtlauf; das war für diesen Voraudit nicht erforderlich.
- Kein Chromium-/Firefox-/WebKit-/Safari-Lauf; `node_modules` fehlt und ein Browserlauf hätte den Auditumfang unnötig ausgeweitet.
- Kein WebImage-Build; GraalVM und Binaryen sind in dieser Umgebung nicht vorhanden.
- Keine Linux-/Windows-Ausführung; die Plattformen sind nicht vorhanden und werden nicht als bestanden gezählt.
- Kein Commit, Push, Release oder Produkt-/Test-/Build-/Spezifikationsdiff.

## 7. Empfohlene Reihenfolge für die P3-Arbeit

1. **WP30 zuerst:** Legacyprofil und Prozessmatrix, Konfigurationspräzedenz, Eltern-/Kindlauf, `CliOutcomeMapper`-Reihenfolge, Formatter-Zeilenenden. Diese Punkte sind klein, direkt prüfbar und blockieren die CLI-Abnahme.
2. **WP31 danach:** asynchroner Scheduler mit Latest-wins/Abbruch, strikte `didChange`-Validierung, LSP-Fehlermapping, Completion-Vollständigkeit, vollständiger Worker-Restart und Bridge-Retention/Limits.
3. **WP32:** echten offiziellen Korpus mit Import-Closure einfrieren, Skip-PASS entfernen, Safari/WebImage-Messungen und Sollbudgets ergänzen.
4. **WP33 zuletzt:** Plattformpakete und Prozessläufe Linux x64/Windows x64/macOS arm64, Javadoc, Versionskonsistenz und fail-closed `releaseCheck`.

**Gesamturteil: AUDIT. G2 bleibt PASS; P3 ist mit den oben genannten P3-Mussstellen offen; G3 wird nicht erteilt.**
