# Unabhängige Prüfung COLOR1

Phase: `COLOR1`  
Basis-SHA: `a25c1a4086df9c87959cb6ca45b9727bbd552a2a`  
Geprüfter Kandidaten-SHA: `792811e2bb1b7186c3eecc4a1a4f3930469a1c19`  
Tatsächlicher HEAD im Review-Worktree: `792811e2bb1b7186c3eecc4a1a4f3930469a1c19`  
Clean-Status: `git status --porcelain` leer; keine produktiven Dateien oder bestehenden Tests verändert. Alle Gegenproben liegen ausschliesslich unter dem ignorierten `build/review/compiler-cli-color/COLOR1-r1/`.

OpenCode: `v2.0.28`  
Effektiver Provider: `deepseek`  
Effektives Modell: `deepseek/deepseek-flash`  
API-Host: `api.deepseek.com` (`https://api.deepseek.com`)  
Zuordnung: `DeepSeek-V4.1-Flash-1017` gemäss `build/review/compiler-cli-color/preflight/provider.json`; keine OpenRouter-/Zen-/Go-/Fallback-Konfiguration verwendet.  
Guard: geerbtes äusseres Guardmanifest `build/reports/python-free/environment.json`, `guardPid=43224`, `gitHead=792811e2bb1b7186c3eecc4a1a4f3930469a1c19`, `dirty=""`, Status zum Berichtszeitpunkt `running` (dieser laufende Reviewprozess selbst). Kein zweiter Guard gestartet, kein Guardfehler beobachtet; `pythonFreeCheck` lief innerhalb der bestehenden Sperre.

Prüfumgebung: macOS 15.8.1 arm64, JDK Temurin 25.0.3+9, Gradle 9.8.1 Wrapper, Node 24.22.0 für den geforderten Gate-Lauf.  
Produktartefakt: `interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler` aus dem frischen `installDist`; Rohberichte: `build/review/compiler-cli-color/COLOR1-r1/required-gate.log`, `build/reports/p3/compiler-cli/processes.json`, `build/reports/python-free/source-check.json`.

## Kriterien

| ID | Status | Eigener Beleg | Finding |
|---|---|---|---|
| C1-01 | erfüllt | `ColorOutputContractTest.bothSpellingsAndTerminatorForEveryDiagnosticCommand` prüft `--color=MODE` gegen `--color MODE` für `check`, `compile`, `analyze`, `diagram`, `format`, inklusive `--`-Datei `--color=literal.ili`. Zusätzlich reale Prozesse: beide Schreibweisen liefern für alle fünf Befehle bytegleiche stdout/stderr und gleiche Exits; `format` mit Syntaxfehler: Exit 1, `ESC[31mERRORESC[0m`. | keines |
| C1-02 | erfüllt | `invalidOptionsFailBeforeSourceOrConfigurationIo` deckt fehlend, leer, Grossschreibung, ungültigen Wert, Doppelnennung gemischt/mit Leerzeichen, `--color --json`, Help/Version/Capabilities sowie `--diagnostic-language=de` ab. Reale Prozesse: alle Varianten Exit 2, stdout leer; `check --color=bad definitely-missing.ili` Exit 2 statt Quellladefehler. `help` dokumentiert `--color=auto\|always\|never or --color MODE`; Help selbst lehnt `--color` mit Exit 2 ab (zulässig). | keines |
| C1-03 | erfüllt | `exhaustivePolicyTable` prüft 144 Kombinationen aus Modus, injizierter stderr-Fähigkeit, Maschinenausgabe, `NO_COLOR` (fehlend/leer/`1`) und `TERM` (fehlend/`xterm`/`dumb`/`DuMb`). Eigener `PolicyProbe` ergänzt 9 nicht identische Kombinationen, u. a. `NO_COLOR=0`, `TERM=DUMB`, `ALWAYS` bei terminal=false, `ALWAYS` bei Maschinenausgabe. | keines |
| C1-04 | erfüllt | `CliEnvironment` bietet den 7-Argument-Record und behält 5-/6-Argument-Konstruktoren; `ColorOutputContractTest.capabilitySurvivesEffectiveEnvironmentAndJsonIsIdentical` prüft die Defaults false. Alle 93 CLI-JUnit-Tests grün; `format-utf8`-Prozess mit `windows-1252`-Properties grün. | keines |
| C1-05 | erfüllt | Kein `System.console()`; `TerminalCapabilities.system()` prüft den geerbten FD2 mit festem `/bin/test -t 2` (keine Shell, keine Modellargumente, Timeout 2 s, Fail-closed). Reale PTY-Proben: stderr=PTY/stdout=Datei → ANSI; stdout=PTY/stderr=Datei → kein ANSI; Pipe → kein ANSI. `/bin/test -t 2` lieferte innerhalb von `script` Exit 0 und bei stderr-Datei Exit 1. | keines |
| C1-06 | erfüllt | `fixedPaletteAndResetOnly` prüft alle Rollen und den Reset; reale Bytes: `ESC[31mERRORESC[0m`, `ESC[33mWARNINGESC[0m`, `ESC[33mWarnungESC[0m`; disabled exakt Originaltext; strenger SGR-Strip ohne fremde ESC-Sequenzen. | keines |
| C1-07 | erfüllt | Reale Prozesse mit semantischem Fehler: `auto` in Pipe und bei `2>file` (stdout weiter PTY) ohne SGR; `always` auf demselben Pfad mit SGR; `never` nie. `auto` mit beiden Kanälen im PTY und ohne `NO_COLOR` färbt; mit `NO_COLOR=1`, `NO_COLOR=0`, `TERM=dumb`/`DUMB` nicht; `always` überstimmt `NO_COLOR`/`TERM`. JSON und Dateiausgaben bleiben plain. | keines |
| C1-08 | erfüllt | Diff berührt nur `interlis-compiler-cli` und Dokumentation; keine Build-/Dependency-/Compiler-/LSP-/Bridge-Vertragsänderung. `pythonFreeCheck`: `PYTHON_FREE_SOURCE_PASS 1459 inputs`. Keine Python-/Interpreterabhängigkeit, kein neues Terminal-/CLI-Framework. | keines |

## Echte eigene Gegenproben

Die äussere Shell hatte in dieser Ausführungsumgebung kein nutzbares `/dev/tty` (`device not configured`); deshalb wurden die PTY-Proben mit `script -q /dev/null ... </dev/null` erzeugt. Innerhalb von `script` war `tty` ein echtes `/dev/ttysNNN` und `/bin/test -t 2` lieferte 0. Es wurde keine gemockte `TerminalCapabilities` als TTY-Beleg verwendet.

### 1. Policy-Konflikt (nicht im Kandidatentest enthalten)

`java -cp interlis-compiler-cli/build/classes/java/main:build/.../policy-classes PolicyProbe` → Exit 0, 9/9 `POLICY_PROBE_PASS`:

- `ALWAYS`, terminal=false, `NO_COLOR=0`, `TERM=DUMB`, machine=false → true
- `ALWAYS`, terminal=true, machine=true → false
- `AUTO`, terminal=true, `NO_COLOR=0`, `TERM=xterm-256color` → false
- `AUTO`, terminal=true, `NO_COLOR=""`, `TERM=xterm-256color` → true
- `AUTO`, terminal=true, `TERM=DuMb` → false
- `AUTO`, terminal=false, `TERM=xterm-256color` → false
- `AUTO`, terminal=false, ohne Env → false
- `NEVER`, terminal=true → false
- `AUTO`, terminal=true, machine=true → false

### 2. Reale Terminal-/Redirectproben (`interlis-compiler`, Exit/ANSI)

Eingabe: `Bad.ili` mit `value:Missing` (Exit 1, `ILI-SEM-0001`).

| Fall | Ergebnis |
|---|---|
| `auto`, stdout/stderr Pipe | Exit 1, stdout 0 B, stderr 254 B, ESC 0 |
| `always`, Pipe | Exit 1, stderr 263 B, ESC 2 (`ESC[31mERRORESC[0m`), +9 B exakt Reset/Start |
| `never`, Pipe | Exit 1, stderr 254 B, ESC 0 |
| `auto`, stdout PTY + stderr Datei | Exit 1, stderr-Datei ESC 0 (kein falscher TTY-Negativbeweis) |
| `always`, stdout PTY + stderr Datei | Exit 1, stderr-Datei ESC 2 |
| `never`, stdout PTY + stderr Datei | Exit 1, stderr-Datei ESC 0 |
| `auto`, stderr PTY + stdout Datei, `NO_COLOR` unset | Exit 1, PTY-Ausgabe ESC 2, stdout-Datei 0 B |
| `auto`, beide Kanäle PTY, `NO_COLOR` unset | Exit 1, ESC 2 |
| `auto`, beide Kanäle PTY, `NO_COLOR=1` | Exit 1, ESC 0 |
| `auto`, beide Kanäle PTY, `NO_COLOR=0` | Exit 1, ESC 0 |
| `auto`, beide Kanäle PTY, `NO_COLOR=""` | Exit 1, ESC 2 |
| `auto`, beide Kanäle PTY, `TERM=dumb` / `DUMB` | Exit 1, ESC 0 |
| `always`, beide Kanäle PTY, `NO_COLOR=0`, `TERM=DUMB` | Exit 1, ESC 2 |
| `always`, stderr Datei, `NO_COLOR=1`, `TERM=dumb` | Exit 1, stderr-Datei ESC 2 |

### 3. Sprachen, JSON und maschinenlesbare Ausgaben

- `check --json --color=always Bad.ili`: Exit 1, stdout 896 B, stderr 0 B, stdout ESC 0; `node` parst gültiges JSON, Diagnose `ILI-SEM-0001`.
- `--json` mit `auto`, `always`, `never`: stdout bytegleich, stderr 0 B, kein ESC.
- `--json --color=always --diagnostic-language de`: Exit 1, stdout/stderr bytegleich zu `never`, stderr 0 B, gültiges JSON mit deutscher Meldung.
- `--json` in PTY mit `auto`: kein ESC, gültiges JSON.
- `diagram --format json --color=always/never` mit gültigem Modell: stdout bytegleich, 0 B stderr, kein ESC.
- `format --write --color=always/never` mit gültigem Modell: Ausgabedateien bytegleich, stdout bytegleich, 0 B stderr, ESC 0.
- `compile --explain-config --color=always`: stdout ohne ANSI, stderr 0 B. (always/never unterscheiden sich erwartungsgemäss im Konfigurationseintrag `--color`, nicht durch ANSI.)
- `help`: Exit 0, Text enthält `--color=auto|always|never or --color MODE`; `help --color=always`, `version --color=always`, `capabilities --color=always`: jeweils Exit 2, stdout leer.
- `check`, `compile`, `analyze`, `diagram`, `format` mit gültiger Quelle und `--color=always`: kein stderr, Exit 0 (format: formatierter Text).

### 4. Fehler-/Duplikatmatrix real

`--color` fehlend, `--color=`, `--color=ALWAYS`, `--color bad`, `--color=auto --color never`, `--color auto --color=never`, `--color --json`, `--diagnostic-language=de` sowie Help/Version/Capabilities mit `--color=always`: alle Exit 2, stdout leer, keine Quellladung. `check --color=bad definitely-missing.ili` ergibt Exit 2 (nicht den Exit-1-Pfad einer fehlenden Datei).

### 5. Severity-/Resetbytes

`check --color=always Bad.ili`: `...:68: ESC[31mERRORESC[0m [ILI-SEM-0001] ...`  
`check --root Other --warnings reject --color=always Warnings.ili`: `...: ESC[33mWARNINGESC[0m [ILI-SYN-0007] ...`  
`... --diagnostic-language de`: `ESC[33mWarnungESC[0m`.  
`--color=never`: identischer Text ohne ESC.  
`stripAllowedSgr(always) == never`: für die realen Fälle bytegleich.

## Durchgeführte Gates

| Kommando | Exit | Tests/Ergebnis | Report |
|---|---:|---|---|
| `./gradlew --no-daemon :interlis-compiler-cli:test compilerCliCheck pythonFreeCheck -PnodeExecutable=.../node-v24.22.0-darwin-arm64/bin/node --rerun-tasks --console=plain` | 0 | 93 JUnit-Tests, 0 Failures, 0 Errors, 0 Skips; `COMPILER_CLI_PROCESSES_PASS 26`; `PYTHON_FREE_SOURCE_PASS 1459 inputs` | `build/review/compiler-cli-color/COLOR1-r1/required-gate.log`; `build/reports/p3/compiler-cli/processes.json` (`gitCommit=792811...`); `build/reports/python-free/source-check.json` (`status=passed`) |
| `git diff --check` | 0 | keine Ausgabe | Arbeitsbaum |
| `git status --porcelain` | 0 | leer | Arbeitsbaum |
| `git rev-parse HEAD` | 0 | `792811e2bb1b7186c3eecc4a1a4f3930469a1c19` | Arbeitsbaum |
| `java ... PolicyProbe` | 0 | 9/9 `POLICY_PROBE_PASS` | `build/review/.../counterprobes/policy-probe.out` |
| `script ... pty-probe.sh` und separate `script`-Läufe | 0 | siehe Gegenproben; echte PTYs | `build/review/.../counterprobes/pty-wrapper.out`, `pty.*.cap`, `stderr-tty.cap` |
| `node -e 'JSON.parse(...)'` | 0 | JSON gültig, keine ANSI | `build/review/.../counterprobes/json.always.out`, `json.de.*.out` |

Nicht fällig in COLOR1 und deshalb nicht ausgeführt (future-gate für COLOR3): `releaseCheckP3`, `releaseEvidenceCheck`, `checkPortable`, `checkBrowser`, `platformCheck`, echte Safari-/Web-Image-/Windows-Prozesse sowie die COLOR2-Quelltextframes. `compilerCliCheck` ist der vorhandene reale CLI-Prozessprofil-Gate und lief vollständig (26 Fälle).

`git show --check HEAD` meldet zwei Trailing-Whitespace-Zeilen in `docs/implementation/compiler-cli-color-output/EXECUTION_PLAN.md:3-4` (Markdown-Hardbreaks der Auftragsdokumentvorlage). Der im Plan genannte Gate-Aufruf `git diff --check` ist Exit 0; die zwei Zeilen sind reine Dokumentation und kein Produkt-/Kriterienverstoss (OBSERVATION).

## Findings / fehlende Pflichtnachweise

Keine blockierenden Findings. `missingRequiredEvidence` ist leer.

OBSERVATIONS (nicht blockierend, kein PASS-Vorbehalt):

1. `CompilerCliProcesses` wurde nicht um Farb-/Redirect-Fälle erweitert; die realen Farbprozessnachweise des Kandidaten und dieses Reviews liegen unter ignorierten `build/review`-Scratchartefakten. Die aktuelle C1-07-Beobachtung ist durch die eigenen realen Prozesse belegt; für einen dauerhaften Regressionsschutz wäre eine Aufnahme in den Prozesschecker in einem späteren Kandidaten sinnvoll.
2. `AnsiStyler.Role` enthält noch keinen `SOURCE_MARKER`; Quelltextframes und Marker sind COLOR2 und waren in COLOR1 nicht fällig.
3. Der Implementierungsbericht nennt 1458 Python-Eingaben; der frische Rohbericht nennt 1459. Der Rohlauf ist massgeblich; keine Auswirkung auf die Abnahme.
4. `--explain-config` zeigt den neuen `--color`-Eintrag und unterscheidet sich dadurch zwischen `always`/`never`; die Ausgabe bleibt ANSI-frei (Konfigurations-Maschinenausgabe).
5. Der Implementierungsbericht formuliert, AUTO sei „nur bei beiden Terminalkanälen farbig“. Das ist zu eng: Vertragskonform und tatsächlich genügt ein interaktives stderr (FD2); bei stdout als Datei und stderr als PTY färbt AUTO (eigener Test). Die Implementierung ist korrekt, nur die Berichtsformulierung ist unpräzise.

## Unabhängiges Urteil

**PASS**

Begründung: Alle fälligen Kriterien C1-01..C1-08 sind am exakten Kandidaten `792811e2bb1b7186c3eecc4a1a4f3930469a1c19` erfüllt. Das Pflichtgate lief frisch mit Exit 0 (93 JUnit-Tests ohne Fehler/Skips, 26 reale CLI-Prozessfälle, 1459 Pythonfreiheits-Eingaben). Die eigenen realen PTY-/Redirectproben belegen die stderr-FD-2-basierte Autoerkennung ohne falschen Positivnachweis bei interaktivem stdout und umgeleitetem stderr, die explizite `always`-/`never`-Steuerung, die `NO_COLOR`-/`TERM`-Priorität, saubere SGR-Resets und ANSI-freie JSON-/Datei-/Maschinenausgaben. Es gibt kein offenes Muss-Finding und kein ANSI-Leck in den geprüften Maschinenkanälen.
