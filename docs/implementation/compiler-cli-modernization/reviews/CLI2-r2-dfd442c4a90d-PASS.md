# Unabhängiger Review CLI2 (R2)

- **Phase:** CLI2 – Auftragsrelevanz und verständliche Diagnoseausgabe
- **Phasenbasis-SHA:** `bd42bb940af646f938c74bf5f9768cf9ad7eda8a`
- **Auftragsbasis-SHA:** `c3d736ac82365524835adef8df40789f3fa3ae60`
- **Geprüfter Kandidaten-SHA:** `dfd442c4a90d04a489bc48f1a6cbd52fc623435e`
- **Tatsächlicher Review-HEAD und Zustand:** `dfd442c4a90d04a489bc48f1a6cbd52fc623435e`; `git status --short` vor und nach der Abnahme leer. Keine Produkt-, Test-, Spezifikations- oder Git-Mutation; alle Schreibungen ausschliesslich unter `build/review/compiler-cli/CLI2-r2/`. Die gepinnten Node-Abhängigkeiten lagen als reale ignorierte Verzeichniskopie `interlis-wasm-teavm/node_modules/` vor (kein Symlink, `git check-ignore` bestätigt `node_modules/`).
- **OpenCode / Provider / Modell / API-Host:** OpenCode `2.0.28`; Provider `deepseek`; Modell `deepseek-flash`; Host `https://api.deepseek.com` (Belege: `build/review/compiler-cli/preflight/provider.json`, geerbtes Guardmanifest `build/reports/python-free/environment.json` mit `--model deepseek/deepseek-flash`).
- **Umgebung und Artefakte:** macOS arm64; Temurin `25.0.3+9`; Gradle-Wrapper `9.8.1`; Node `v24.22.0`. Gebaute Installation `interlis-compiler-cli/build/install/interlis-compiler/`; Launcher `.../bin/interlis-compiler`; SDK-ZIP-SHA-256 `dbbb76dcbe74d4b50aa46e7ef6a27016d0a57c903e13aceee09bea75271d831b`.
- **Eigene Rohdaten:** `build/review/compiler-cli/CLI2-r2/gate.log`, `.../junit-summary.json`, `.../counterprobes/results/`, `.../counterprobes/java/r2-probe.log`.

## Kriterienprüfung

| ID | erfüllt / verletzt / nicht prüfbar | eigener Beleg | Finding |
|---|---|---|---|
| CLI2-01 | **erfüllt** | `RequestDiagnosticSelector.select(...)` wird genau einmal in `CompilationRun` (Zeile 113) erzeugt und im Snapshot veröffentlicht; `CompilationGate` liest `analysis.requestDiagnostics()` (Zeile 45); `DefaultCompilerCli` verwendet für `check`/`compile` dieselbe Liste (Zeilen 615–616) und für den Compile-Schritt von `diagram` `succeeded.analysis().requestDiagnostics()` (Zeilen 386–406). Keine Dateinamen-/Pfadsemantik in der CLI. `RequestDiagnosticsContractTest` 10/10, `DiagnosticPresentationContractTest` 7/7. | – |
| CLI2-02 | **erfüllt** | `AnalysisSnapshot` speichert beide Listen `List.copyOf`, prüft den geordneten Teilbestand im Konstruktor und veröffentlicht `diagnostics()` unverändert. `clear()`-Unveränderlichkeit, Root-/Revisionsbindung und Fresh/Reuse sind getestet. LSP `InterlisTextDocumentService:1043` und WASM-Bridge `CompilerSessionFacade:396` verwenden weiterhin `diagnostics()` vollständig. | – |
| CLI2-03 | **erfüllt** | Eigene Prozesse: gültiges `Good` mit fremder beschädigter `Foreign.ili` als zusätzliche verfügbare Quelle → `check` Exit 0, stdout/stderr leer, JSON `diagnostics:[]`; `analyze` zeigt den fremden `ILI-SYN-0004` (`counterprobes/results/cp2-*`). Eigener Reuse-Probe C: zwei Modelle in `shared.ili` plus fremde beschädigte `noise.ili`; Root `Good` → Request leer, Root `Bad` → nur `ILI-SEM-0001`, kein Fremd-Syntaxfehler (`counterprobes/java/r2-probe.log`). | – |
| CLI2-04 | **erfüllt** | F1-Fix unabhängig bestätigt: explizit angeforderte Quelle ohne Modell (`check NoModel.ili`) liefert Exit 1, sichtbares `ILI-SYN-0004` auf stderr und nicht-leeres JSON; `analyze`/`diagram` zeigen es ebenfalls (CP1, `r2-probe.log` Fall A). Ein tatsächlich aufgelöster, aber beschädigter Import behält seine Syntaxdiagnose (`request=[ILI-SYN-0004, ILI-RES-0004]`); ohne Binding bleibt die Datei unberücksichtigt und `ILI-RES-0001` sichtbar (Fall B). Missing/ambiguous Roots/Imports und Translation-/Overlayfälle sind über `RequestDiagnosticsContractTest` und die CLI-Prozesse abgedeckt. Keine Dateinamensheuristik: `--root Lost Lost.ili` liefert `ILI-RES-0001` und gerade nicht `ILI-SYN-0004` (CP3). | – |
| CLI2-05 | **erfüllt** | Eigener Reuse-Probe C: Good→Bad→Good mit `AnalysisReuse` und Revision 1→2; Request-Auswahl folgt Root/Revision, keine stale Auswahl, kein Fremd-Leck. `RequestDiagnosticsContractTest` prüft Rootwechsel/Revision/Fresh-Reuse und Warnungsablehnung (fremde Warnung löst bei `Good` kein `WARNINGS_REJECTED` aus); `P3CliLifecycleContractTest` 8/8. | – |
| CLI2-06 | **erfüllt** | `DiagnosticMessageFormatterTest` iteriert den tatsächlichen Katalog samt jedem Argumentplatz in DE/EN (4/4, 0 Fallbacks/`{...}`); Default EN, explizites `--diagnostic-language de` in echten Prozessen, ungültige Werte Exit 2 vor IO. DE/EN-Office und Unicode/CRLF-Ausgabe ohne rohe Keys/`SCOPE_`-Dumps (CP4, Office). | – |
| CLI2-07 | **erfüllt** | Office-Original unverändert (`SHA-256 fbb1bccc…48431` vor/nach). Unabhängiger Prozess: Exit 1, Primary `279:5`, Related `272:9`, `ILI-SEM-0010`, DE/EN-Namenskonflikt mit Hinweis. Separate positive Kontrolle `OfficeControl.ili` (CLASS Contact) Exit 0 (`counterprobes/results/office-*`). | – |
| CLI2-08 | **erfüllt** | Eigene Prozesse mit CRLF/Emoji/Bidi im Pfad und `DiagnosticPresentationContractTest` prüfen UTF-16-Positionen, RelatedLocation, fehlende/veraltete SourceRevision (`@rev (UTF-16 a..b)`), exakte Zahlen und Escaping ohne Renderer-IO. F2 unabhängig RED→GREEN: identischer Formatter-Input enthält am R1-Artefakt rohe `U+200E/U+200F/U+061C`, am Kandidaten `\u200e\u200f\u061c` (`red-r1-bidi.log` vs. `green-r2-bidi.log`). | – |
| CLI2-09 | **erfüllt** | Office/Unicode-JSON mit Node parsebar; nach Entfernen von `message`/`hint` sind DE/EN kanonisch identisch (IDs, Codes, Arguments, Ranges, RelatedLocations), `canonicalEqual:true`. `--json`-Erfolg schreibt genau ein JSON auf stdout ohne Banner; Fehlerfall schreibt Diagnosen auf stderr. Keine Norm-/XFAIL-/Suite-/Quellenpin-Änderung (Conformance-Suite `88576ffc…`, 21 unveränderte Normbewertungen). | – |
| CLI2-10 | **erfüllt** | Eigene Prozesse: `diagram`-Ablehnung schreibt Diagnose auf stderr und lässt stdout leer; `format`/`format --check` und erfolgreiche `diagram`-Fachausgabe bleiben auf stdout. Geladenes CLI-Prozessprofil 19/19, `P3CliLifecycleContractTest` 8/8, `P3AtomicOutputContractTest` 8/8; Flush-/Abbruchpfade unverändert im Gate. | – |
| CLI2-11 | **erfüllt** | Vollständiges Pflichtgate Exit 0, 3m31s, 72/72 Tasks ausgeführt (`gate.log`). JUnit 1217 Tests/227 Suites, 0 Failures/Errors/Skips; Conformance 577 PASS/16 XFAIL/0 FAIL/XPASS/ERROR (21 Normbewertungen, Suite-Pin unverändert); semantischer Vergleich 4 Fälle/8 Mutationen/8 Portable-Projektionen; SDK 9/9; Compiler-CLI 19/19 am Kandidaten; SDK-Archiv 11 Einträge, nur neue Launcher; Pythonfreiheit 1445 Inputs; JS/WasmGC je 3560 + 24 Fälle bestanden (`gitCommit` = Kandidat, `dirty:false`). Mehrere eigene neue Gegenproben (CP1–CP5, R2-Probe A–D) bestanden. CLI1-Verträge erhalten: `settings.gradle` ohne Alias, `Main` einziger Standardeingang, Installation/SDK ohne `interlis-cli`/`ili2c`/`compat`, reale Hilfe/Version/Bedienfehler. | – |

## Eigene Gegenproben

Alle Inputs/Outputs unter `build/review/compiler-cli/CLI2-r2/counterprobes/`. Arbeitsverzeichnis `counterprobes/foreign work dir 😀` (Leerzeichen, Umlaut, Emoji); Launcher `interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler`.

### CP1 – F1-Fixbestätigung: angeforderte Quelle ohne Modell (mit RED am R1-Artefakt)

Input `NoModel.ili`: `INTERLIS 2.4;\r\nGARBAGE\r\n`.

- Kandidat `check NoModel.ili` → **Exit 1**, stderr `ILI-SYN-0004` (`:2:1`), stdout leer (`cp1-check.*`).
- Kandidat `check --json` → **Exit 1**, parsebares JSON mit genau `ILI-SYN-0004`, Reason `[SOURCE_ERRORS, MISSING_INPUT]` (`cp1-json.*`).
- Kandidat `analyze` → Exit 0 mit voller Sicht (`cp1-analyze.*`); `diagram` → Exit 1, Diagnose auf stderr, stdout leer (`cp1-diagram.*`).
- Dasselbe Input am R1-Artefakt `65ad265…`: **Exit 1, stderr leer, JSON `diagnostics:[]`** (`red-r1-cp1-*`). Damit ist der F1-Unterschied am gleichen Eingabefall belegt.

### CP2 – Neue fachliche Gegenprobe: fremde verfügbare Syntaxquelle darf den Root nicht belasten

Input `Good.ili` (gültiges `MODEL Good`) plus `Foreign.ili` (`INTERLIS 2.4;\r\nGARBAGE\r\n`), Aufruf `check --root Good Good.ili Foreign.ili`.

- **Erwartet:** Exit 0, beide Streams leer; die fremde Datei ist verfügbar, aber weder Root noch Import.
- **Tatsächlich:** Exit 0, stdout/stderr leer, JSON `{"status":"ok","diagnostics":[]}`; `analyze --root Good` zeigt den fremden `ILI-SYN-0004` (volle Sicht erhalten) (`cp2-*`). Der F1-Fix weitet die Auswahl also nicht auf beliebige verfügbare Dateien aus.

### CP3 – Neue fachliche Gegenprobe: ByName ohne Quellidentität rät nicht über den Dateinamen

Input `Lost.ili` (`INTERLIS 2.4;\r\nGARBAGE\r\n`), Aufruf `check --root Lost Lost.ili`.

- **Erwartet:** klarer Auflösungsfehler, kein stilles Erfolgs-/Fehlerbild und keine Dateinamensheuristik.
- **Tatsächlich:** Exit 1, stderr `ERROR [ILI-RES-0001] The requested or imported model 'Lost' could not be found.`; `ILI-SYN-0004` erscheint bewusst nicht, weil keine Modellidentität/Binding vorliegt (`cp3-*`). Das entspricht der geforderten Trennung „explizit angefordert/aufgelöst“ vs. „ohne Identität/Binding verfügbar“.

### CP4 – Bidi-/CRLF-/Unicode-Pfad, DE/EN und JSON am realen Prozess

Datei `bidi-<LRM><RLM><ALM>.ili` (CRLF, Emoji-Kommentar, `TOPIC Office`/`CLASS Office`).

- EN/DE: Exit 1, Primary `4:1`, Related `3:36`, `ILI-SEM-0010`, lesbare Erklärung + Hinweis; einziger Unterschied ist die Präsentation (`cp4-en.*`, `cp4-de.*`).
- Der Dateipfad wird als File-URI prozentkodiert; im stderr erscheinen keine rohen Bidi-Zeichen. Die Formatter-Escaping-Garantie selbst ist zusätzlich direkt über Probe D und den RED/GREEN-Vergleich belegt.
- JSON DE/EN: parsebar; nach Entfernen von `message`/`hint` kanonisch identisch (`canonicalEqual:true`, gleiche ID/Range/Arguments/RelatedLocations) (`check-json.cjs`, `cp4-json-*`).

### CP5 – CLI1-Bedienregression am gebauten Programm

`--help`/`version` Exit 0; alte Option `-o0` Exit 2 mit „unsupported option“ vor Modell-IO; Diagnose-/Fachausgaben bleiben getrennt (`cp5-*`). Frische Installation und SDK enthalten ausschliesslich `interlis-compiler`/`.bat`.

### Java-Probe A–D (`r2-probe.log`, Exit 0)

- **A (neu):** aufgelöster Import, dessen Zielquelle eine andere gültige Modelldeklaration plus `GARBAGE` enthält → Request `[ILI-SYN-0004, ILI-RES-0004]`; die Quellzuordnung bleibt trotz fehlender versprochener Deklaration erhalten.
- **B (neu):** identische Quellen ohne Resolver-Binding → Request nur `[ILI-RES-0001]`, volle Sicht `[ILI-SYN-0004, ILI-RES-0001]`; keine Zuordnung über den Dateinamen.
- **C (neu):** Fresh/Reuse-Rootwechsel mit zusätzlicher fremder beschädigter Quelle → `Good` Request leer, `Bad` nur `ILI-SEM-0001`, kein `noise`-Syntaxleck, Revision 2 korrekt.
- **D:** Formatter escapet `U+200E/U+200F/U+061C` und CRLF; unbekannter externer Key erzeugt keinen Key-Dump.

## Ausgeführte Pflichtprüfungen

Exakt der geforderte Aufruf (ohne `--continue`), Ausgabe `build/review/compiler-cli/CLI2-r2/gate.log`:

```sh
./gradlew --no-daemon check checkPortable conformanceCheck pythonFreeCheck \
  semanticComparison sdkProcessCheck sdkArchiveCheck compilerCliCheck \
  releaseEvidenceCheckerTest \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
  --rerun-tasks --console=plain
```

**Ergebnis:** `BUILD SUCCESSFUL`, Exit **0**, **3m31s**, **72/72 actionable Tasks ausgeführt**; keine ausgelassenen Pflichttests (nur `NO-SOURCE`-Tasks).

| Prüfung | Frisches Ergebnis | Rohbeleg |
|---|---|---|
| JUnit gesamt | **1217 Tests / 227 Suites, 0 Failures, 0 Errors, 0 Skips** | `build/test-results/**/TEST-*.xml`, `junit-summary.json` |
| Neue Request-Diagnostik | `RequestDiagnosticsContractTest` **10/10** | `TEST-guru.interlis.compiler.RequestDiagnosticsContractTest.xml` |
| Formatter-Katalog | `DiagnosticMessageFormatterTest` **4/4** | `TEST-guru.interlis.diagnostics.DiagnosticMessageFormatterTest.xml` |
| Neue CLI-Präsentation | `DiagnosticPresentationContractTest` **7/7** | `TEST-guru.interlis.cli.DiagnosticPresentationContractTest.xml` |
| Lifecycle/Atomarität | **8/8** und **8/8** | `TEST-guru.interlis.cli.P3CliLifecycleContractTest.xml`, `...P3AtomicOutputContractTest.xml` |
| Conformance (externer Java-Runner) | **577 PASS / 16 XFAIL / 0 FAIL / 0 XPASS / 0 ERROR**, `overallResult PASS`; Suite-Pin `88576ffc…`; 21 Normbewertungen | `build/reports/conformance/conformance-summary.json` |
| Semantischer Vergleich | **`status passed`, 4 Fälle, 8 Mutationen, 8 Portable-Projektionen** (`A3_PORTABLE_PASS`) | `build/reports/g2/semantic-comparison.json` |
| SDK-Prozesse | **9/9 bestanden**, Launcher `interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler` | `build/reports/sdk/sdk-process-comparison.json` |
| Compiler-CLI-Prozesse | **19/19 bestanden**, `gitCommit` = Kandidat | `build/reports/p3/compiler-cli/processes.json` |
| SDK-Archivverbraucher | **bestanden**, 11 Einträge, nur neue Launcher/JARs, ZIP-SHA `dbbb76…` | `build/reports/sdk/archive-consumer.json` |
| Pythonfreiheit | **1445 Inputs, passed** | `build/reports/python-free/source-check.json` |
| Portable JS / WasmGC | je **3560 Stabilisationsfälle + 24 Corpusregressionen**, `status passed`, `gitCommit` = Kandidat, `dirty:false` | `interlis-wasm-teavm/build/js-contract-result.json`, `.../wasm-contract-result.json` |

**Python-Sperrung:** Die originale geerbte Sperre blieb aktiv (`PATH`-Wrapper `/var/folders/.../interlis-without-python-o26qAL/bin`, Guard-PID 41290, Probes `python/python3/pip/pip3/pypy/uv` = `ENOENT`). Es wurde keine zweite Guardinstanz gestartet und kein Python-/pip-Aufruf ausgeführt. Das Guardmanifest stand während des Reviews noch auf `running`; der äussere Prozessabschluss wird hier nicht behauptet.

**Nicht ausgeführte fällige Prüfungen:** Keine. Die optionalen TeaVM-C-/Emscripten-/Disassembly-Tasks gehören nicht zum CLI2-Pflichtumfang; reale Browser/Safari/Web-Image-/Plattformmatrix und Linux/Windows sind CLI3-Pflichten und hier nicht vorweggenommen.

## Findings

Keine offenen Muss-Findings. Die R1-Findings sind unabhängig geschlossen:

- **F1 (CLI2-04):** `ILI-SYN-0004` in explizit angeforderten bzw. tatsächlich aufgelösten beschädigten Quellen ist sichtbar; ohne Identität/Binding wird keine Datei über den Namen geraten. RED am R1-Artefakt, GREEN am Kandidaten, plus neue Negativ-/Positiv-Gegenproben (CP1–CP3, Probe A/B).
- **F2 (CLI2-08):** `U+200E`, `U+200F`, `U+061C` werden escaped; derselbe Formatter-Input ist am R1-Artefakt roh und am Kandidaten escaped (Probe D/BidiOnly, CP4).

Keine sprachliche, normative, XFAIL-, Suite- oder Quellenpin-Lockerung. Die nachgeführten G2-Anker betreffen ausschliesslich Offsets/Hashes unveränderter verknüpfter Codeabschnitte und deren abhängige Inventarhashes; keine fachliche Erwartung wurde abgeschwächt.

## Urteil

**PASS.** Alle elf CLI2-Kriterien sind am exakten Kandidaten `dfd442c4a90d04a489bc48f1a6cbd52fc623435e` mit eigenen Belegen erfüllt; das vollständige Pflichtgate und mehrere neue Gegenproben bestehen; F1/F2 sind unabhängig geschlossen; die CLI1-Verträge bleiben erhalten. Offene Muss-Findings: keine. Fehlende Pflichtnachweise: keine. CLI3 bleibt als Folgephase offen.
