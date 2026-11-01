# Unabhängige Prüfung COLOR3

Phase / Basis-SHA / geprüfter Kandidaten-SHA / tatsächlicher HEAD / clean-Status:
`COLOR3` / `7524356c9689654ce7a4ffc95f523af937b906b9` (COLOR2-PASS) /
`65a1571b3de14550d4bf596a9e4481861f9c843a` /
`65a1571b3de14550d4bf596a9e4481861f9c843a`; Review-Worktree detached und
`git status --porcelain` leer. Der Hauptbaum `/Users/stefan/sources/interlis-java`
steht auf demselben SHA und ist ebenfalls clean. `assignmentBaseSha`:
`a25c1a4086df9c87959cb6ca45b9727bbd552a2a`.

OpenCode-Version, effektiver Provider, Modell, direkter API-Host, Guard-Status:
OpenCode `v2.0.28`; Provider `deepseek`; Modell `deepseek/deepseek-flash`;
direkter API-Host `https://api.deepseek.com`; Zuordnung
`DeepSeek-V4.1-Flash-1017` gemäss
`build/review/compiler-cli-color/preflight/provider.json`; Sitzung
`ses_e68ecfd9efferkBLboAuEqCs9O`. Keine OpenRouter-/Zen-/Go-/Proxy-/Fallback-
Konfiguration. Der geerbte äussere Python-Guard
(`tools/verification/without-python.cjs`) blieb der einzige Guard; es wurde kein
zweiter Guard gestartet oder überschrieben. Der nach dem Nutzer-Resume aktive
Reviewer-Guard (PID 70449) ist aktiv (8 Probes: PATH-ENOENT und absolute EPERM)
und wird erst nach diesem Prozess finalisiert. Der vor dem Resume beendete
Guard (PID 55490) ist unverändert in `guard-before-user-resume.json` erhalten.
Der separat abgeschlossene Originalguard des Codex-Gates
`65a1571-release-guard.json` ist `passed`, Exit `0`; er wurde nur gelesen.

Prüfumgebung, JDK/OS, tatsächliche Produktartefakte:
macOS 15.8.1 arm64 (Apple M1, 8 logische CPUs, 16 GiB); Temurin JDK
`25.0.3+9-LTS`; Gradle `9.8.1` Wrapper; gepinntes Node `v24.22.0`; GraalVM
`25.0.3`; Binaryen `version_133`. Gearbeitet wurde ausschliesslich im detached
Review-Worktree. Produkt-, Test- und Git-Metadaten des Kandidaten wurden nicht
verändert; alle Reviewartefakte liegen unter
`build/review/compiler-cli-color/COLOR3-r3/` bzw. als temporäre, ignorierte
Build-Ausgaben. Für den kumulativen Release-Nachweis wurden die vollständigen
Rawreports des bereits mit Exit 0 abgeschlossenen Codex-Gates am exakt gleichen
SHA unabhängig nachgerechnet; der eigene redundante zweite Vollauf wurde auf
aktuelle Nutzerweisung bewusst abgebrochen (siehe unten) und niemals als
erfolgreich behandelt.

## Aktuelle Nutzersteuerung und abgebrochener redundanter Lauf

Die aktuelle Nutzerweisung lautet, keine doppelten/dreifachen vollständigen
Korpus-, Performance-, Referenz-, Benchmark- oder Release-Gates durchzuführen,
sondern die bereits vorhandenen, exakt SHA-gebundenen Rohdaten unabhängig zu
prüfen und nur für konkrete offene Fragen gezielt zu testen. Sie hat Vorrang vor
den früheren Ausführungswiederholungspflichten aus Plan, Prompt und alter
Request-Fassung. Deshalb wurde der eigene zweite vollständige Lauf

```sh
cd /Users/stefan/sources/interlis-java/build/review/compiler-cli-color/COLOR3-r3-65a1571b3de1/worktree
./gradlew --no-daemon releaseCheckP3 referenceCheck originalViewReferenceCheck \
  viewReferenceCheck unitReferenceCheck semanticComparison jvmBenchmark \
  :interlis-compiler-cli:distZip \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
  -PgraalVmHome=/Users/stefan/.sdkman/candidates/java/25.0.3-graal \
  -PbinaryenHome=/Users/stefan/.cache/interlis-java/runtime/binaryen-version_133 \
  --rerun-tasks --no-parallel --max-workers=2 --console=plain
```

nach den bereits abgeschlossenen Teilen kontrolliert beendet. Der Lauf hat
**keinen** `GATE_EXIT` und **keinen** vollständigen Release-/Performance-/
Referenz-/Benchmark-PASS. Log:
`build/review/compiler-cli-color/COLOR3-r3/reviewer-gate.log`; Abbruchbelege:
`user-scope-change.json` und `guard-before-user-resume.json` im externen Parent.
Die dort dokumentierten eigenen Prozesse (Gradle, Firefox, Node-Korpus) wurden
mit SIGTERM beendet; die Shell ist `ESRCH`; es blieb kein eigener Testprozess
laufen. Der äussere Originalprozess wurde mit Exit 1/Abbruch beendet; der
Gradle-Lauf selbst hat keinen eigenen `GATE_EXIT`. Dieser Lauf wird unten nur
als `interruptedRun` geführt und ist **kein Pass-Beleg** für C3-05/C3-06.

## Kriterien

| ID | Erfüllt / verletzt / nicht prüfbar | eigener Beleg | Finding |
|---|---|---|---|
| C1-01 | Erfüllt | `ColorOutputContractTest.bothSpellingsAndTerminatorForEveryDiagnosticCommand`; eigene Parser-Sonde `REVIEWER_COLOR_PROBE_PASS checks=3619` für alle fünf Befehle, `--color=MODE` = `--color MODE`, `--`-Datei unverändert. | – |
| C1-02 | Erfüllt | Missing/Invalid/Duplicate/Help/Exit2 in `invalidOptionsFailBeforeSourceOrConfigurationIo` und eigener Parser-/Prozesssonde; `check --config --color=always missing.ili` Exit 2 mit „--config has a missing value“; `help/version/capabilities --color=always` Exit 2; keine Modell-/Repository-IO. | – |
| C1-03 | Erfüllt | `exhaustivePolicyTable` plus eigene 360-Kombinationen (AUTO/ALWAYS/NEVER, Terminal ja/nein, Maschinenoutput ja/nein, `NO_COLOR` absent/leer/`1`/`0`/`x`, `TERM` absent/xterm/dumb/DuMb/DUMB/leer) — exakt die dokumentierte Priorität. | – |
| C1-04 | Erfüllt | `CliEnvironment` mit injizierter `TerminalCapabilities`; 5-/6-Argument-Konstruktoren kompatibel und default `false`; Capability wird in `DefaultCompilerCli.run` unverändert kopiert; eigene Prozessläufe nutzen die reale Host-Capability. | – |
| C1-05 | Erfüllt | `TerminalCapabilities.system()` prüft POSIX-FD 2 mit `/bin/test -t 2` (kein `System.console()`/stdout-Rückschluss); eigener PTY-Test: stderr-PTY + stdout-PTY → AUTO farbig; stdout-PTY + stderr-Datei → AUTO ohne ESC; Linux/Windows-CI meldet `realPty:false` ohne erfundenen TTY-Erfolg. | – |
| C1-06 | Erfüllt | `AnsiStyler` mit exakter Palette `0/31/33/34/36/90`, Reset pro Bereich, disabled exakt Originaltext; eigener Styler-/Renderer-Scanner lässt nur diese SGR zu. | – |
| C1-07 | Erfüllt | 50 reale Prozesse im Codex-Gate und 50 im eigenen frischen Lauf; eigene Sonde `REVIEWER_PROCESS_PROBE_PASS checks=222`; `auto` in Pipe/`2>file` ohne SGR, `always` auf demselben Pfad mit SGR, `never` ohne; Resets und strikte SGR-Whitelist. | – |
| C1-08 | Erfüllt | Keine neue Abhängigkeit/Framework; nur CLI-Darstellungscode, `startScripts`-Launcher und Dokumentation; Compiler-/Model-/LSP-/Bridge-Verträge unverändert; keine Python-/Interpreterabhängigkeit. | – |
| C2-01 | Erfüllt | DE/EN, Severity, Code, Primär-/RelatedLocation, Hint und SourceRevision bleiben inhaltsgleich; Office-Realfall `279:5`/`272:9`; eigene 3619-Check-Renderersonde und strikte Plainprojektion. | – |
| C2-02 | Erfüllt | ERROR `31`, WARNING `33`, INFO `36`, HINT `34`, LOCATION `36`, CODE `90`, Marker in Severity-Farbe; jede Sequenz mit Reset; eigene Always-Ausgaben und `ColorOutputContractTest`-Palette. | – |
| C2-03 | Erfüllt | Eigener strikter Stripper (nur `0/31/33/34/36/90`, sonst Fehler) ergibt für alle Severities/Sprachen, Office, Evil, Format-Reject und Prozesse exakt `strip(always) == plain`; 3619 Renderer- und 222 Prozesschecks. | – |
| C2-04 | Erfüllt | Frame nur bei exakt passender SourceId/Revision/gültigem UTF-16-Bereich; stale/missing/foreign/zu kurze Quelle und `endOffset > length` liefern keinen Frame; kein `Files`/`Path`/Loader im Renderer; eigener Office-Range-Rekonstruktionstest. | – |
| C2-05 | Erfüllt | ASCII/Tab/CRLF: unabhängig berechnete Caretspalte; Emoji, kombinierende Zeichen, CJK, geteilte/lone Surrogate und unklare Breiten erzeugen sichtbaren UTF-16-Fallback statt falschem Caret; `stdout`-Kanal bleibt UTF-16-Position. | – |
| C2-06 | Erfüllt | Mehrzeilige Ranges, Nullspanne/Einfügepunkt, 2-Millionen-Zeichen-Zeile, sehr lange Kontrollzeichenfolgen: maximal 3 Quellzeilen/160 Displayspalten, `^`/`~`, `[span continues]`, Ellipsen, bounded Zeit/Ausgabe; `RenderOptions`-Grenzen getestet. | – |
| C2-07 | Erfüllt | Eigene bösartige `Evil.ili` mit `ESC[2J`, OSC 8/BEL, NUL, C1 `0085/009B`, Bidi `202E/2066`, CRLF und Tab/Emoji; reale und gerenderte Ausgaben enthalten keine rohen Steuerzeichen, sichtbare `\u001b…`-Escapes, nur Styler-SGR; SourceId/Argument ebenfalls escaped. | – |
| C2-08 | Erfüllt | `--json --color=always|never|auto` bytegleich, kein ANSI/C1/Bidi im JSON, stderr leer; eigene JSON-Dateien und Prozessfälle; `--diagnostic-language de` bytegleich. | – |
| C2-09 | Erfüllt | `format`-/`diagram`-stdout `never`/`always` bytegleich, keine ANSI; `format --write`/Lock-/Atomic-Tests unverändert; `help/version/capabilities --color` Exit 2 ohne ANSI; fachliche Exits 0/1/2 erhalten. | – |
| C2-10 | Erfüllt | `testdata/cli-modernization/original/SO_AGI_DataCatalog_Base_20260529.ili` SHA-256 vor/nach `fbb1bccc0e84795284f9fb1a3c0be9e6db516c3c4b8ae43ad2f856cf70948431`; reale DE-Diagnose `279:5`,`272:9`, RelatedLocation, Hint, Exit 1; keine hartcodierten Dateinamen/Zeilen im Produktcode. | – |
| C2-11 | Erfüllt | Frische JVM-Gesamtzahl `1232 Tests/229 Suites/0 Failures/0 Errors/0 Skips` im eigenen Lauf; Root-Isolation, Foreign-Warnings, Missing-Import, Renderer-/UTF-8-Canaries, Office-Original, Evidence-/G2-Suites grün. | – |
| C3-01 | Erfüllt | `installDist`, SDK-ZIP und `:interlis-compiler-cli:distZip` enthalten `bin/interlis-compiler` und `.bat`, keine Legacy-Einträge; distZip-Hash `d888784944f56828f70a5182adf7533ed3eabb5fb087764c6c63896e145dbe4d`; eigener Lauf aus Unicode-Pfad mit Leerzeichen/Emoji (`external-local ä 😀`) und CI-Linux/Windows-Installationen mit UTF-8-Pfaden. | – |
| C3-02 | Erfüllt | Eigene echte `script(1)`-PTY-Sonde mit 6 Fällen: AUTO auf stderr-PTY farbig, AUTO mit stdout-PTY/stderr-Datei farbfrei, ALWAYS auch in Datei farbig, NEVER nie; Codex-Gate 6 reale macOS-PTY-/Redirectfälle; CI headless ohne TTY-Behauptung. | – |
| C3-03 | Erfüllt | Reale 50-Case-Profile für `check`, `compile`, `analyze`, `diagram`, `format`; Diagnosen stderr, JSON/Artefakte stdout; eigene JSON-/Format-/Diagramm-Bitgleichheit; SDK-/Atomic-/Lock-/Abbruchtests unverändert. | – |
| C3-04 | Erfüllt | Unit- und Prozessabdeckung für DE/EN, Text/JSON, `NO_COLOR`, `TERM`, TTY/Non-TTY, AUTO/ALWAYS/NEVER; reale positive und negative Fälle; keine Unit-Fälschung eines echten TTY. | – |
| C3-05 | Erfüllt | Der Codex-Gate-Rohsatz ist am finalen SHA `65a1571…` mit SourceFingerprint `7dd87623…` als `BUILD SUCCESSFUL in 57m 28s`, 106 Tasks (50 executed, 56 up-to-date) und `release/result.json` `status:passed` abgeschlossen; unabhängig nachgerechnet: 830 Roh-, 117 Browser-/Referenz-/Benchmark- und 60 Performance-Budget-Checks, `evaluate` PASS (347 Roots/6 Runtimes/2000 Edits/0 Skips/0 Semantikmismatches). Der eigene redundante Lauf wurde auf Nutzerweisung abgebrochen und nicht als PASS verwendet. | – |
| C3-06 | Erfüllt | Rawbelege: 1232/229 JVM, 50 CLI + 6 PTY, SDK 9, LSP 53, TeaVM JS/Wasm 3560+24, Node-Client 18/Diagramm 4, Chrome/Firefox/WebKit/Safari 3560+24 mit Transport 29/Client 7, WebImage 3 Browser, UI 3, Korpus 347/6/2000 unter Budgets, Conformance 577/16/0, Pythonfreiheit 1466 Inputs; keine Tests deaktiviert. | – |
| C3-07 | Erfüllt | CI-Run `41300835686` `head_sha=65a1571…`, Workflow `.github/workflows/p3-platforms.yml`, beide Jobs `completed/success`; Linux-/Windows-Artefakte mit GitHub-Digest `5ced5b65…`/`b13b1bb1…`; Report-SHA, eingebettete Proofs, alle 229 XML-Hashes, SourceFingerprint und `compilerCliColorVerified` unabhängig verifiziert; macOS-Plattformreport lokal. | – |
| C3-08 | Erfüllt | Eigene `REVIEWER_NEGATIVE_CANARY_PASS checks=333`: 6 Product-Preflight-Mutationen und 16 unabhängige Evidence-Mutationen (falscher SHA, Digest, Skip, fehlende/ANSI-Fälle) werden abgewiesen; `releaseEvidenceCheckerTest` 100 PASS; `CompilerCliProcessesTest` aktive Negativmutationen; `distribution-files`/`node-junit` Negativtests. | – |
| C3-09 | Erfüllt | `README`, Hilfe (`--color=…`, Sourceframes, JSON/Artefakte plain), `docs/p3/CLI_PROFILE.md`, `docs/p3/SDK_RELEASE_PROFILE.md` und COLOR-`STATUS.md` beschreiben das implementierte Verhalten; historische CLI1–CLI3-Berichte/Abnahmen unverändert. | – |
| C3-10 | Erfüllt | Unabhängiger kumulativer OpenCode-Review mit 29/29 erfüllten IDs, eigenen Gegenproben und ohne offenes Muss-Finding. | – |

## Echte eigene Gegenproben

1. **Renderer/Policy/Parser – 3619 Checks.**
   `ReviewerColorProbe.java` gegen die lokal gebaute und die CI-gebaute Distribution
   (gleicher SHA) geprüft: beide Optionsschreibweisen für fünf Befehle,
   `--`-Semantik, fehlende/falsche/doppelte Werte, Inline-Wert an jeder
   Optionsposition, benutzerdefinierte Aritäten 0/2, 360 Policy-Kombinationen,
   exakte SGR-Palette/Reset, Legacy-Overloads, DE/EN, alle Severities,
   ASCII/Tab/CRLF/Emoji/kombinierende/CJK/Surrogate, stale/missing/foreign,
   Null-/Mehrzeilenranges, lange Zeilen und bösartige SourceId/Argumente.
   Ergebnis: `REVIEWER_COLOR_PROBE_PASS checks=3619`; Log
   `probes/renderer-probe.log`.

2. **Reale Prozesse – 222 Checks.**
   `process-probe.cjs` mit lokaler und CI-Distribution in Pfaden mit
   Leerzeichen/Umlaut/Emoji: `auto`/Pipe, `2>file`, `NO_COLOR`/`TERM`,
   explizite Overrides, JSON-Bitgleichheit/stderr-frei, `format`-/`diagramm`-
   stdout-Bitgleichheit, Inline-Option, ESC/RLO-Usage-/Formatpfad,
   UTF-8 mit windows-1252-JAVA_OPTS, Office-Original und Evil-Quelle.
   Ergebnis: `REVIEWER_PROCESS_PROBE_PASS checks=222`.

3. **Echte PTY – 6 Fälle.**
   `/usr/bin/script` erzeugt einen echten Kind-PTY; `test -t 1 && test -t 2`
   belegt ihn. `auto` auf stderr-PTY farbig, `auto` bei stdout-PTY/stderr-Datei
   farbfrei, `always` in der Datei farbig, `never` nie. Ergebnis:
   `REVIEWER_PTY_PROBE_PASS`; `probes/pty-local/pty-results.json`. Die
   Review-Shell hat kein nutzbares `/dev/tty`; `/dev/null` als script-stdin
   erzeugt weiterhin einen echten PTY (wie der Produktchecker).

4. **Negativcanaries – 333 Checks.**
   `negative-canary.cjs` prüft zuerst die echten CI-Rohbelege und mutiert dann
   isoliert: falscher Kandidat/Run-Head, fehlende Plattform, Digest-/Report-SHA,
   SourceFingerprint, Test-/Suite-Zahl, Skip, fehlender/fremder Prozess,
   fehlende Farbverifikation, ANSI im Maschinenreport, inkonsistenter Proof.
   Ergebnis: `REVIEWER_NEGATIVE_CANARY_PASS checks=333`.

5. **Office-Original.**
   Hash vor/nach `fbb1bccc0e84795284f9fb1a3c0be9e6db516c3c4b8ae43ad2f856cf70948431`;
   DE `279:5`, Related `272:9`, „Kollidierende Deklaration“, Hinweis, Exit 1;
   `strip(always)==never` (828 Zeichen, 18 ESC-Bytes vor Strip).

6. **Evil-Quelle und JSON.**
   `Evil.ili` mit ESC[2J/OSC8/BEL/NUL/C1/Bidi/CRLF: keine rohen Steuerzeichen,
   sichtbare Escapes, Marker am realen Fehler; JSON `--color=always` und
   `--color=never` bytegleich, stderr 0, valides JSON; Logs
   `probes/evil/*`, `probes/json/*`.

7. **Bereits abgeschlossener Codex-Gate-Rohsatz.**
   Unabhängig nachgerechnet (nicht aus dem Audit übernommen):
   `MAIN_RAW_EVIDENCE_PASS checks=830` (SHA/Fingerprint, Release-Result,
   Originalguard, Logmarker, Auditwerte, macOS-Plattform inkl. 229 XML-Hashes,
   50/6-Prozessreport, SDK, 76 reproduzierte Dateien exact inventory,
   Conformance-Hashes, CI-Digests/-Proofs/XML-Hashes);
   `MAIN_RAW_EVIDENCE_2_PASS checks=117` (Browser/Safari/UI/WebImage,
   JS-/Wasm-/Node-Contracts, Browser-Manifest/Toolchain, Referenzen,
   JVM-Benchmark); `PERFORMANCE_RAW_BUDGET_PASS checks=60` und
   `PERFORMANCE_RAW_EVALUATE_PASS` (Produkt-`evaluate` auf den echten
   Rawreports, kein Lauf).

## Durchgeführte Gates

### Eigene frische, abgeschlossene Prüfungen dieser Session

- `:interlis-compiler-cli:test`, Gesamt-JVM: **1232 Tests/229 Suites**, 0
  Failures/Errors/Skips (eigene XMLs im Review-Worktree).
- `compilerCliCheck`: **50 reale CLI-Fälle + 6 macOS-PTY-/Redirectfälle**,
  `COMPILER_CLI_PROCESSES_PASS 50`.
- `checkPortable`/`checkBrowser`: Chromium/Firefox/WebKit/Safari je
  `3560+24`, Transport 29/Client 7, `exactDecimal`, Worker-Restart; WebImage
  Chromium/Firefox/WebKit je 3560+24; UI 3/3.
- `platformCheck`: macOS 1232/229/0, `compilerCliColorVerified=true`, 50 Cases,
  SDK-Hash `1e011a35…`.
- `sdkProcessCheck` 9 Commands, `sdkArchiveCheck`/Reproducibility **76**
  byteidentische Dateien; `conformanceCheck` **577 PASS/16 XFAIL/0 FAIL**,
  21 normative Assessments; `pythonFreeCheck` **1466** Inputs; Guard passed/0
  im ursprünglichen Codex-Gate.
- `releaseEvidenceCheckerTest`: **100** PASS, 0 Skips.
- Eigene frisch abgeschlossene Performance-Teile: JVM 347 Roots (max first
  3589 ms, warm 378 ms, cold 8357 ms, Heap 1.83 GB), WasmGC 347 (max first
  2079 ms, warm 4550 ms, cold 97 ms, RSS 0.94 GB), Chromium 347 (max first
  1655 ms, warm 1583 ms, cold 119 ms). Firefox war beim Abbruch bei 321/347;
  dieser Teil ist **nicht** vollständig und wird nicht als Performance-PASS
  verwendet. Die vollständigen Codex-Rohwerte (JVM max first 1215 ms, warm
  1791 ms, cold 961 ms; Wasm max first 1797 ms, warm 2156 ms, cold 170 ms;
  Chromium max first 1330 ms, warm 2310 ms, cold 52 ms) werden separat unten
  als Rawbeleg geprüft.

### Bereits abgeschlossenes Codex-Gate (Exit 0), unabhängig aus Rawreports geprüft

```sh
cd /Users/stefan/sources/interlis-java
./gradlew releaseCheckP3 referenceCheck originalViewReferenceCheck \
  viewReferenceCheck unitReferenceCheck semanticComparison jvmBenchmark \
  :interlis-compiler-cli:distZip \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
  -PgraalVmHome=/Users/stefan/.sdkman/candidates/java/25.0.3-graal \
  -PbinaryenHome=/Users/stefan/.cache/interlis-java/runtime/binaryen-version_133 \
  --no-parallel --max-workers=2 --console=plain --no-daemon
```

Beleg: `build/review/compiler-cli-color/color3-65a1571-release-resume.log`
(`BUILD SUCCESSFUL in 57m 28s`, 106 Tasks, 50 executed/56 up-to-date),
`65a1571-release-guard.json` `status:passed`, `exitCode:0`, exakter SHA,
`dirty:""`; `build/reports/p3/release/result.json` `status:passed`,
`sourceFingerprint:7dd87623…`; vollständige Rawreports für Performance,
Platform, CLI, SDK, Reproducibility, Conformance, Referenzen und Benchmark.
Der Lauf ist ausdrücklich der Codex-Gate-Rohsatz; die zugehörigen 56
up-to-date-Tasks sind same-SHA-Reports des vorangegangenen frischen Versuchs.
Er wird hier nicht als eigener Lauf ausgegeben, sondern unabhängig
nachgerechnet. `release/result.json` selbst verlangt zusätzlich den
abgeschlossenen äusseren Guard und diesen unabhängigen Review.

### CI

`https://github.com/edigonzales/interlis-java/actions/runs/41300835686`:
Run `completed/success`, `headSha=65a1571…`, Workflow
`.github/workflows/p3-platforms.yml`; Linux-/Windows-Jobs
`completed/success`; Artefakt-Digests
`sha256:5ced5b6580aa39faf55f2ad47d4e49a5f9b45209b4bf99650d9f9a2c87e3a7d3`
und `sha256:b13b1bb18081730f38658c8dab7be357d1e2485fc8088cb5836faf6f15656b7c`;
Report-SHAs `293e8fa7…`/`3e167037…`; beide 1232 Tests/229 Suites/0
Failures/Errors/Skips, 50 CLI-Cases, `compilerCliColorVerified=true`; alle
229 XML-Hashes je Plattform unabhängig geprüft; `sourceFingerprint` gleich
`7dd87623…`.

### Abgebrochener redundanter eigener Vollauf (nicht PASS)

Log `build/review/compiler-cli-color/COLOR3-r3/reviewer-gate.log` endet bei
`FULL_CORPUS_BROWSER firefox 321 …`; kein `GATE_EXIT`, kein vollständiges
Performance-`result.json`, kein `releaseEvidenceCheck`, keine Referenzen/
Benchmark/distZip dieses Laufs. Abbruch durch die aktuelle Nutzersteuerung;
Belege `user-scope-change.json`/`guard-before-user-resume.json` (SIGTERM,
`stillRunning:[]`). Dieser Lauf ist **kein** erfolgreicher Gate-Lauf.

## Findings / fehlende Pflichtnachweise

Keine blocking findings, keine fehlenden Pflichtnachweise für C1-01..C3-10.

Beobachtungen (nicht kriterienrelevant, kein PASS-Aufweichen):

- Der Codex-Gate-Lauf ist ein Resume-Lauf (50 executed/56 up-to-date). Die
  up-to-date-Reports stammen vom selben SHA und SourceFingerprint und wurden
  hier vollständig neu gelesen und geprüft; die aktuelle Nutzerweisung verbietet
  einen weiteren vollständigen Lauf. Der eigene redundante Lauf ist abgebrochen
  und wird nicht als Gate-PASS geführt.
- Der `views`-Referenzvergleich meldet 6 Prozessfehler des externen ili2c
  (`referenceProcessErrors=6`), während die eigenen 134 Erwartungen und die
  102 Übereinstimmungen im Report stehen; `units` hat 0 Referenzprozessfehler,
  `originalView` 0. Das ist eine nicht-normative Referenzbeobachtung
  (`scope: Own expectations verified; reference observations are not normative
  approvals`) und kein Test-Skip.
- Die Gradle-Tasks `disasmWasmGC`, `emscriptenStubWasmGC` und
  `emscriptenWasmGC` werden als optionale Build-Graph-Tasks übersprungen; es
  gibt keine übersprungenen JUnit-/Node-/Browser-Testfälle (alle `skipped=0`).
- Der Review-Shell hatte kein nutzbares `/dev/tty`; die PTY-Sonde nutzt
  `script` mit `/dev/null` als stdin und belegt den echten Kind-PTY über
  `test -t 1 && test -t 2`. Der Produktchecker nutzt denselben Mechanismus.
- Die aktuelle Reviewer-Sitzung läuft unter dem nach dem Resume aktiven
  äusseren Guard (PID 70449); ihre Finalisierung erfolgt erst nach diesem
  Prozessende. Der vor dem Resume beendete Guard (PID 55490) bleibt in
  `guard-before-user-resume.json` erhalten; der bereits abgeschlossene
  Originalguard des Codex-Gates ist `passed/exit0`.

## Unabhängiges Urteil

**PASS**

Begründung: Alle 29 kumulativen Kriterien C1-01..C3-10 sind am exakten,
sauberen Kandidaten `65a1571b3de14550d4bf596a9e4481861f9c843a` erfüllt. Die
eigenen 3619 Renderer-, 222 Prozess-, 6 echten PTY- und 333 Negativcanary-
Prüfungen sowie die frischen JVM-/Browser-/Platform-/SDK-/Reproducibility-/
Conformance-Teile dieser Session sind grün. Für den vollständigen
Release-/Performance-/Referenz-/Benchmark-Nachweis wurde der bereits mit Exit 0
abgeschlossene Codex-Gate-Rohsatz am selben SHA unabhängig nachgerechnet
(830 Roh-, 117 Browser-/Referenz-/Benchmark- und 60 Budget-Checks;
Produkt-`evaluate` PASS). Der eigene redundante zweite Vollauf wurde auf
aktuelle Nutzerweisung abgebrochen, ist klar als `interruptedRun` dokumentiert
und wird **nicht** als PASS-Beleg ausgegeben. Die Linux-/Windows-CI-Evidenz ist
SHA-, Digest-, XML- und Fingerprint-identisch. Kein ANSI-Leck, kein offener
Markerfehler, kein fehlender Reset und kein offenes Muss-Finding.
