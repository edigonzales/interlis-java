# Unabhängiger Review CLI2

- **Phase:** CLI2 – Auftragsrelevanz und verständliche Diagnoseausgabe
- **Phasenbasis-SHA:** `bd42bb940af646f938c74bf5f9768cf9ad7eda8a`
- **Auftragsbasis-SHA:** `c3d736ac82365524835adef8df40789f3fa3ae60`
- **Geprüfter Kandidaten-SHA:** `65ad26529bde5fd9e9834b336481d98c94079ff9`
- **Tatsächlicher Review-HEAD und Zustand:** `65ad26529bde5fd9e9834b336481d98c94079ff9`, Baum `897ded627fd9b0331a3fa54884c7909ba934492b`; `git status --short` vor und nach der Abnahme leer, keine Produkt-/Test-/Git-Mutation. Einzige zusätzliche nicht versionierte Vorbereitung war ein ignorierter `interlis-wasm-teavm/node_modules`-Verzeichnisbaum aus dem vorhandenen Dependency-Cache (siehe Gateumgebung).
- **OpenCode / Provider / Modell / API-Host:** OpenCode `2.0.28`; Provider `deepseek`; Modell `deepseek-flash` (kanonische ID `deepseek/deepseek-v4.1-flash`, freigegebene Zuordnung DeepSeek-V4.1-Flash-1017); Host `https://api.deepseek.com`. Belege: `build/review/compiler-cli/preflight/provider.json`, geerbtes Guardmanifest `build/reports/python-free/environment.json` (dort `--model deepseek/deepseek-flash`).
- **Umgebung:** macOS 24.6.0 arm64; Temurin `25.0.3+9`; Gradle-Wrapper `9.8.1`; Node `v24.22.0`. Der Review lief im Standaloneprozess unter der geerbten Python-Sperre (Guard-PID `11776`, PATH-Wrapper, Probes `python/python3/pip/pip3/pypy/uv` = ENOENT, absolute `/usr/bin/python3` und `/usr/bin/pip3` = EPERM). Es wurden keine Python-/pip-Aufrufe ausgeführt; der äussere Guard-Exit war zum Reviewzeitpunkt noch nicht feststellbar (`status: running`) und wird nicht behauptet.
- **Artefakte:** gebaute Installation `interlis-compiler-cli/build/install/interlis-compiler/`, SDK-ZIP `build/releases/interlis-java-sdk-0.2.0-SNAPSHOT.zip` (SHA-256 `b39c9e6b3a3a7f475f65be899ee33626378d7cfa4a851c692dfbca96b67faea3`), gebauter Launcher `interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler`.
- **Eigene Gegenproben/Rohdaten:** `build/review/compiler-cli/CLI2-r1/counterprobes/`, `.../counterprobes/results/`, `.../counterprobes/java/`, `.../gate.log`, `.../gate1-missing-node.log`, `.../gate2-symlink.log`, `.../junit-summary.json`.

## Gateumgebung und tatsächliches Pflichtgate

Der Kandidatenworktree enthielt die ignorierte Node-Dependency `interlis-wasm-teavm/node_modules` nicht. Der erste Lauf des geforderten Gates scheiterte deshalb vor der Produktprüfung in `:interlis-wasm-teavm:buildBrowserTools` mit `Cannot find module 'esbuild'` (`gate1-missing-node.log`). Ein Symlink auf den vorhandenen Cache wurde von `ProfileFingerprint.current` als untracked Quelle gelesen und scheiterte mit `IOException: Is a directory` (`gate2-symlink.log`). Danach wurde der vorhandene, gepinnte Dependency-Cache als **ignoriertes Verzeichnis** `interlis-wasm-teavm/node_modules/` bereitgestellt (keine Produktdatei, `git check-ignore` bestätigt `node_modules/`). Damit lief das vollständige Pflichtgate:

```sh
./gradlew --no-daemon check checkPortable conformanceCheck pythonFreeCheck \
  semanticComparison sdkProcessCheck sdkArchiveCheck compilerCliCheck \
  releaseEvidenceCheckerTest \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
  --rerun-tasks --console=plain
```

**Ergebnis:** `BUILD SUCCESSFUL`, **Exit 0, 3m40s, 72/72 actionable Tasks ausgeführt** (`gate.log`). Die optionalen TeaVM-C-/Emscripten-/Disassembly-Tasks (`emscriptenStubWasmGC`, `emscriptenWasmGC`, `disasmWasmGC`) wurden mangels dieser Eingaben erwartungsgemäss `SKIPPED`; kein JUnit- oder Node-Pflichttest ist übersprungen.

Ausgewertete Rohreports:

| Prüfung | Ergebnis | Rohbeleg |
|---|---|---|
| JUnit gesamt | **1214 Tests / 227 Suites, 0 Failures, 0 Errors, 0 Skips** | `junit-summary.json`, `build/test-results/**/TEST-*.xml` |
| Node-Releasechecker | **95 Tests, 0 Failures/Skips** | `gate.log` |
| Portable JS / WasmGC | **je 3558 Stabilisationsfälle + 24 Corpusregressionen, status passed** | `interlis-wasm-teavm/build/js-contract-result.json`, `.../wasm-contract-result.json` |
| Browser-Client / Diagrammadapter | **18/18** und **4/4**, keine Fehler/Skips | `gate.log` |
| Conformance (externer Java-Runner) | **577 PASS / 16 XFAIL / 0 FAIL / 0 XPASS / 0 ERROR**, `overallResult PASS`, Roh-Runner-Exit 1 unverändert | `build/reports/conformance/conformance-summary.json`, `conformance-log.txt` |
| Semantischer Vergleich | **4 reale Referenz-/JVM-/JS-/WasmGC-Fälle, 8 Mutationsgegenproben, 8 portable Projektionen, status passed** | `build/reports/g2/semantic-comparison.json` |
| SDK-Prozesse | **9/9 bestanden** | `build/reports/sdk/sdk-process-comparison.json` |
| Compiler-CLI-Prozesse | **19/19 bestanden**, `gitCommit` = Kandidat | `build/reports/p3/compiler-cli/processes.json` |
| SDK-Archivverbraucher | **bestanden**, 11 Einträge, ZIP-SHA `b39c9e…faea3` | `build/reports/sdk/archive-consumer.json`, `sdk-inventory.txt` |
| Pythonfreiheit | **1444 Inputs, passed** | `build/reports/python-free/source-check.json` |
| Neue Formatter/Selektions-/CLI-Tests | `RequestDiagnosticsContractTest` 8, `DiagnosticMessageFormatterTest` 4, `DiagnosticPresentationContractTest` 6, `CompilerCliIdentityContractTest` 15, `P3CliLifecycleContractTest` 8, `P3AtomicOutputContractTest` 8 – alle 0 Fehler/Skips | jeweilige `TEST-*.xml` |

## Kriterienprüfung

| ID | erfüllt / verletzt / nicht prüfbar | eigener Beleg | Finding |
|---|---|---|---|
| CLI2-01 | **erfüllt** | `RequestDiagnosticSelector.select(...)` wird genau einmal in `CompilationRun` vor der Snapshot-/Gateveröffentlichung aufgerufen; `CompilationGate` liest `analysis.requestDiagnostics()`; `DefaultCompilerCli.diagnostics(...)` verwendet für Succeeded/Rejected dieselbe Liste; keine Pfad-/Dateinamensemantik in der CLI. Code: `RequestDiagnosticSelector.java`, `CompilationRun.java:113`, `CompilationGate.java:45`, `DefaultCompilerCli.java:615-617`; Vertrag: `RequestDiagnosticsContractTest` 8/8. | – |
| CLI2-02 | **erfüllt** | `AnalysisSnapshot` speichert beide Listen immutable (`List.copyOf`), validiert den geordneten Teilbestand im Konstruktor und veröffentlicht `diagnostics()` unverändert. `RequestDiagnosticsContractTest` prüft `clear()`-Unveränderlichkeit, Root-/Revisionsbindung, Fresh/Reuse. LSP (`InterlisTextDocumentService:1043`) und WASM-Bridge (`CompilerSessionFacade:396`) verwenden weiterhin `diagnostics()` vollständig. | – |
| CLI2-03 | **erfüllt** | Eigene Prozesse: `check --root Alpha` auf einer Datei mit gültigem Alpha und semantisch defektem Beta → Exit 0, stdout und stderr leer; `check --root Beta` → Exit 1 mit `ILI-SEM-0001`; `analyze --root Alpha` zeigt den fremden Fehler; `check --json --root Alpha` liefert `diagnostics:[]`. Zusätzlich fremde Quelle/Fremdimport in `imports-check`/`imports-analyze`. | – |
| CLI2-04 | **verletzt** | Import-/Root-/Missing-Fälle sind sichtbar (`imports-check`, `imports-missing`); aber ein Parserfehler in einer explizit angeforderten Rootquelle bzw. einer benötigten Abhängigkeit ohne rekonstruierbare Modelldeklaration wird aus der Request-Sicht entfernt: `check` liefert Exit 1 und **leeres stderr**, `--json` `diagnostics:[]` / `diagnosticIds:[]`; `analyze` und `format` zeigen `ILI-SYN-0004`. Belege: `counterprobes/results/no-model-check.stderr`, `no-model-json.stdout`, `no-model-analyze.stderr`, `imported-no-model-check.stderr`, `java/no-model-probe.log`. | **F1** |
| CLI2-05 | **erfüllt** | Eigener Java-Probe `ReuseProbe`: Good→Bad→Good und Revision 1→2 mit `AnalysisReuse`; Request-Auswahl bleibt root-/revisionskorrekt, frische und wiederverwendete Analyse stimmen überein, keine stale Diagnostics. Warnungsprobe: fremde Warnung löst bei Root Alpha kein `WARNINGS_REJECTED` aus, die gewählte Warnung bei Root Other schon; `analyze` behält die volle Warnung. `P3CliLifecycleContractTest` 8/8. | – |
| CLI2-06 | **erfüllt** | `DiagnosticMessageFormatterTest` iteriert den tatsächlichen Katalog, alle definierten Argumentplätze und beide Sprachen; 0 rohe Keys/`{...}`/Fallback; Default EN und explizite DE-Ausgabe in eigenen Prozessen; Office DE/EN ohne `SCOPE_`/`diagnostic.`-Dump. Der eine gefundene Escaping-Rest ist separat CLI2-08/F2. | – |
| CLI2-07 | **erfüllt** | Office-Original unverändert (SHA-256 vor/nach `fbb1bccc…48431`); `check` Exit 1, `ILI-SEM-0010`, Primary `279:5`, Related `272:9`, deutsche/englische Namenskonflikt-Erklärung mit Hinweis; separate positive Kontrolle `OfficeControl.ili` Exit 0. | – |
| CLI2-08 | **verletzt** | Eigener Renderer-Probe: UTF-16/CRLF-Positionen, RelatedLocation, fehlende/stale SourceRevision und ANSI-/Steuerzeichen-Escaping funktionieren. Aber `DiagnosticMessageFormatter.safeText` lässt die Bidi-Steuerzeichen `U+200E`, `U+200F`, `U+061C` roh (nur `U+202A–E`/`U+2066–9` werden escaped); `BidiRendererProbe` zeigt die Rohcodepoints in der Ausgabe. Das widerspricht der aktualisierten Vorgabe „Zeilen-/Bidi-Steuerung … sichtbar escaped“. | **F2** |
| CLI2-09 | **erfüllt** | Office- und Unicode-JSON sind mit `node` parsebar; nach Entfernen von `message`/`hint` sind DE und EN kanonisch identisch (IDs, Codes, Arguments, Range, RelatedLocations). `--json`-Erfolg schreibt nur ein JSON auf stdout, keine Banner; Unicode/CRLF-Pfad liefert korrekte Offsets; keine Norm-/XFAIL-Lockerung. | – |
| CLI2-10 | **erfüllt** | Eigene Prozesse: `format` schreibt ausschliesslich formatierten Text auf stdout, stderr leer; `format --check` Exit 1 mit `would format` auf stderr und leerem stdout; `diagram` schreibt Mermaid auf stdout, stderr leer; ungültiges Diagramm Exit 1, Diagnose auf stderr, stdout leer. Flush-/Abbruch-/Batchfälle: `P3CliLifecycleContractTest` 8/8, `P3AtomicOutputContractTest` 8/8. | – |
| CLI2-11 | **erfüllt** | Das vollständige Pflichtgate ist Exit 0/72 Tasks; JUnit 1214/227 ohne Fehler/Skips; Conformance-, SDK-, Portable- und Semantiknachweise wie oben. Es bestehen mehrere eigene neue Gegenproben (Multi-Model, echter Import, Reuse, Office, Unicode/CRLF, SDK-Archiv). Die zwei offenen Muss-Findings betreffen CLI2-04/08. CLI1-Regression siehe unten. | F1, F2 separat |

## Eigene Gegenproben

Die vollständigen Inputs und Outputs liegen unter `build/review/compiler-cli/CLI2-r1/counterprobes/`. Ausgangspunkt war jeweils der gebaute Launcher `interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler`; die SDK-Probe verwendete ausschliesslich das entpackte ZIP. Arbeitsverzeichnis war `counterprobes/foreign-work dir 😀` (Leerzeichen, Umlaut, Emoji).

### CP1 – Multi-Source/Multi-Modell mit einem Root

Input `counterprobes/multi/Good.ili`: `MODEL Alpha` gültig, `MODEL Beta` mit `MissingType`.
- `check --root Alpha Good.ili` → **Exit 0, stdout leer, stderr leer** (`multi-ok.*`).
- `check --root Beta Good.ili` → **Exit 1**, `ILI-SEM-0001` auf stderr (`multi-bad.*`).
- `analyze --root Alpha Good.ili` → **Exit 0**, der Beta-Fehler erscheint (`multi-analyze.*`).
- `check --json --root Alpha Good.ili` → `{"status":"ok","diagnostics":[]}` (`multi-json.stdout`).

Erwartung erfüllt: fremde semantische Fehler beeinflussen weder Ausgang noch gewöhnliche Compileausgabe; die volle Analyse bleibt sichtbar.

### CP2 – Echte importierte Abhängigkeit, fremde Quelle, benötigte defekte Abhängigkeit

Input `imports/Root.ili` importiert `Base`; `Base` enthält `MissingType`; `Foreign.ili` ist eine nicht importierte defekte Quelle.
- `check --root Root Root.ili Base.ili Foreign.ili` → **Exit 1**, nur der Fehler aus `Base.ili` (`imports-check.*`).
- `analyze --root Root Root.ili Base.ili Foreign.ili` → **Exit 0**, Fehler aus `Base` **und** `Foreign` (`imports-analyze.*`).
- `check --root Root Root.ili` (fehlender Import) → Exit 1, `ILI-RES-0001` sichtbar (`imports-missing.*`).
- `check --root RootDep RootDep.ili DepBroken.ili` (benötigte Abhängigkeit ohne Modell, mit `GARBAGE`) → **Exit 1, nur `ILI-RES-0001`; der Parserfehler `ILI-SYN-0004` aus `DepBroken.ili` fehlt** (`imported-no-model-check.*`). `analyze` zeigt beide. Das ist F1.

### CP3 – Warnungsbereich

Input `warnings/Warnings.ili`: gültiges `Alpha`, fremdes `Other` mit `TOPIC T(ABSTRACT, ABSTRACT)`.
- `check --warnings reject --root Alpha` → **Exit 0, stderr leer** (`warn-ok.*`).
- `check --warnings reject --root Other` → **Exit 1**, Warnung `ILI-SYN-0007` (`warn-bad.*`).
- `analyze --root Alpha` → Exit 0, die volle Warnung ist sichtbar (`warn-analyze.*`).

### CP4 – Fresh/Reuse, Rootwechsel und Revision

`counterprobes/java/ReuseProbe.java` nutzt die öffentliche Compiler-API mit `AnalysisReuse`:
Good (Rev 1) → Bad (Rev 1, reuse) → Good (Rev 1, reuse) → Good (Rev 2, reuse) → Bad (Rev 2, reuse). Ergebnis `REUSE_PROBE_PASS`: Request-Auswahl ist jeweils leer bzw. nicht leer wie erwartet; alle Request-Ranges tragen die aktuelle Revision; keine stale Auswahl (`reuse-probe.log`).

### CP5 – Office-Original, positive Kontrolle, JSON

Original-SHA vor/nach identisch `fbb1bccc0e84795284f9fb1a3c0be9e6db516c3c4b8ae43ad2f856cf70948431`. `check` EN/DE: Exit 1, Primary `279:5`, Related `272:9`, `ILI-SEM-0010`, lesbare DE-/EN-Erklärung mit Hinweis, kein `SCOPE_`/`diagnostic.`-Dump. `--json` DE/EN: gleiche kanonische Felder, nur `message`/`hint` verschieden; `relatedLocations` vorhanden. Positive Kontrolle mit `CLASS Contact` → Exit 0.

### CP6 – Unicode/CRLF-Pfad und DE/EN

`counterprobes/unicode/Unicode 😀 CRLF.ili` (CRLF, Emoji im Dateinamen und Kommentar, `TOPIC Office`/`CLASS Office`):
- DE und EN liefern dieselbe Position (`4:3`) und Related (`3:36`) und nur unterschiedliche Präsentation.
- `--json --diagnostic-language de` liefert parsebares JSON mit deutscher `message` und RelatedLocation.
- Die kanonischen JSON-Felder wurden mit Node nach Entfernen von `message`/`hint` verglichen: identisch.

### CP7 – Renderer und Bidi

`RendererProbe` (eigener Prozess, öffentliche API): UTF-16-Positionen `2:3`/`1:1` bei CRLF/Emoji; `Missing\u000a\u001b[31m` sicher escaped; fehlende und veraltete SourceRevision fallen auf `@7 (UTF-16 6..13)` zurück; DE/EN korrekt (`renderer-probe.log`).

`BidiRendererProbe` mit `U+200E/U+200F/U+061C`: `containsRawBidi=true`; die Ausgabe enthält die Rohzeichen, nicht `\u200e\u200f\u061c` (`bidi-renderer-probe.log`, `bidi-probe.log`). Das ist **F2**.

### CP8 – Frisches SDK aus fremdem Verzeichnis

SDK-ZIP nach `counterprobes/sdk extract` entpackt; CWD `counterprobes/foreign-work dir 😀`; direkter Start ohne `chmod`:
- `bin/interlis-compiler --help` → Exit 0, `interlis-compiler 0.2.0-SNAPSHOT`.
- `check` gültig → Exit 0, stdout/stderr leer.
- `check --diagnostic-language fr` → Exit 2, Bedienfehler vor IO.
- ZIP enthält nur `cli/bin/interlis-compiler`/`.bat`; keine `interlis-cli`-/`ili2c`-Launcher, kein `compat/ili2c.jar`; beide Launcher `0755` (`sdk-inventory.txt`, `sdk-zipinfo.txt`).

Ein zusätzlicher Entpackungsort mit Emoji im **Installationspfad** erzeugt `ClassNotFoundException: guru.interlis.cli.Main`; das ist die bereits im CLI1-Review dokumentierte JVM-Classpath-Grenze, nicht ein neuer CLI1/CLI2-Produktbefund. Die geforderte Probe mit Leerzeichen im Installationspfad und Unicode im Arbeitsverzeichnis besteht.

## CLI1-Regression (erhaltene Verträge)

- `settings.gradle` enthält `interlis-compiler-cli` und keinen `interlis-cli`-Alias; `git ls-files` zeigt kein altes Modul; das Bibliotheksmodul bleibt `interlis-compiler`.
- `Main` ist der einzige Standardeingang; die Legacyklassen sind nicht vorhanden. Die verbleibenden `LegacyOidCompatibilityTest`/`LegacySourceMetadataTest` sind fachliche KEEP_REFERENCE-Fälle, keine aktiven Legacybedienwege.
- Die frische Installation enthält `bin/interlis-compiler`/`.bat` und getrennte `interlis-compiler-0.2.0-SNAPSHOT.jar`/`interlis-compiler-cli-0.2.0-SNAPSHOT.jar`; das SDK-ZIP enthält ausschliesslich die neuen Launcher (SHA-256 `b39c9e…`). Keine alten Launcher/`compat/ili2c.jar`.
- Reale Prozesse: `help`, `version`, `capabilities`, nackte Datei, unbekannter Befehl, alte Option `-o0`, gültiges/fachlich ungültiges Modell, `--`, `check`/`compile`/`analyze`/`diagram`/`format`; `COMPILER_CLI_PROCESSES_PASS 19`, `SDK_PROCESS_PASS 9`.
- Conformance verwendet den neuen Launcher (`selected=593`, 577/16/0/0/0); der semantische Vergleich und die SDK-/Archivprüfungen verwenden die neuen Artefakte.

## Findings

### F1 – CLI2-04: Parserfehler in benötigten Quellen ohne rekonstruierbares Modell werden unterdrückt

**Reproduktion (frischer gebauter Launcher):**

```text
counterprobes/valid/SyntaxNoModel.ili:
INTERLIS 2.4;
GARBAGE
```

```text
$ interlis-compiler check .../SyntaxNoModel.ili
EXIT=1
stdout: <leer>
stderr: <leer>

$ interlis-compiler check --json .../SyntaxNoModel.ili
EXIT=1
{"status":"rejected: RejectionSummary[reasons=[MISSING_INPUT], diagnosticIds=[], ...]","diagnostics":[]}

$ interlis-compiler analyze .../SyntaxNoModel.ili
EXIT=0
stderr: ...:2:1: ERROR [ILI-SYN-0004] Unexpected content 'GARBAGE' follows the end of the model input.

$ interlis-compiler format .../SyntaxNoModel.ili
EXIT=1
stderr: ...:2:1: ERROR [ILI-SYN-0004] ...
```

Die gleiche Lücke entsteht bei einer echten, benötigten Importdatei ohne Modell ( `RootDep.ili` importiert `Dep`; `DepBroken.ili` enthält `GARBAGE`): `check --root RootDep ...` zeigt nur `ILI-RES-0001`; `analyze` zeigt `ILI-RES-0001` **und** `ILI-SYN-0004`. Der Java-Probe `NoModelProbe` bestätigt: `only-root full=[ILI-SYN-0004] request=[]`, `imported full=[ILI-SYN-0004, ILI-RES-0001] request=[ILI-RES-0001]`.

**Erwartet:** Ein Parser-/Lexerfehler in einer explizit angeforderten Rootquelle oder einer tatsächlich benötigten Abhängigkeit bleibt in der Request-Sicht sichtbar; mindestens muss statt einer leeren Fehlerausgabe ein klarer, auftragsbezogener Grund erscheinen. **Tatsächlich:** Die Request-Auswahl enthält keinen Diagnoseeintrag; die CLI schreibt bei Exit 1 nichts auf stderr, JSON `diagnostics:[]`. Das verletzt die Vorgabe „keine unzulässige Unterdrückung von Lexer-/Parserfehlern in benötigten Quellen“ und R08 („Klarer Fehler statt leerer Erfolgs-/Fehlerausgabe“).

**Ursache:** `RequestDiagnosticSelector` prüft Syntaxfehler über `closure.sources()`. Wenn keine Modelldeklaration rekonstruiert werden kann, ist die Closure leer; `RootSelector.BySource` emittiert für eine vorhandene Quelle ohne Modell keinen Diagnoseeintrag. Der Vertragstest `requiredFileSyntaxErrorsStayVisibleEvenOutsideTheSelectedModelRange` deckt nur Quellen mit rekonstruierbarem Modell ab.

**Korrektur:** Die konservative Syntaxrelevanz muss die explizit angeforderten Rootquellen bzw. die tatsächlich bereitgestellten/benötigten Quellen umfassen (z. B. Übergabe der benötigten SourceIds an den Selektor) oder `RootSelector` muss für eine vorhandene Quelle ohne Modell eine sichtbare Diagnose erzeugen; Regressionstests für No-Model-Root und defekte importierte Abhängigkeit ergänzen.

### F2 – CLI2-08: Bidi-Marken werden nicht escapet

`DiagnosticMessageFormatter.safeText` escaped ISO-Steuerzeichen, `U+2028`/`U+2029`, `U+202A–U+202E` und `U+2066–U+2069`. Die Bidi-Marken `U+200E` (LRM), `U+200F` (RLM) und `U+061C` (ALM) bleiben roh. Eigener Probe:

```text
rendered=ERROR [ILI-SEM-0001] The name '‎‏؜' cannot be resolved at this location.
containsRawBidi=true
erwartet: Die drei Marken erscheinen als \u200e\u200f\u061c (escaped), nicht roh.
```

Die aktualisierte Spezifikation 09 verlangt, dass „Zeilen-/Bidi-Steuerung in Argumenten sichtbar escaped“ wird. Der bestehende Test prüft nur `U+202E`. **Korrektur:** `U+200E`, `U+200F` und `U+061C` in `safeText` aufnehmen und den Katalog-/Sicherheitstest entsprechend erweitern.

## Urteil

**FAIL.** Das Pflichtgate und die CLI1-Regressionen sind am Kandidaten erfolgreich, und mehrere neue fachliche Gegenproben bestehen. Zwei Muss-Kriterien sind jedoch verletzt: **CLI2-04** (F1: leere Fehlerausgabe/unterdrückter Parserfehler in benötigten Quellen ohne rekonstruierbares Modell) und **CLI2-08** (F2: nicht escapete Bidi-Marken). Damit ist keine PASS-Abnahme möglich. `missingRequiredChecks` ist leer; die beiden Findings sind offene Muss-Findings. Keine Produktdatei wurde durch den Review verändert.
