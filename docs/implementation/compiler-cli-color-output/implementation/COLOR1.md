# COLOR1 – Farbpolitik und echte stderr-Fähigkeit

BASE_SHA / Auftragsbasis: `a25c1a4086df9c87959cb6ca45b9727bbd552a2a`.
Umfang C1-01–08; vorhandene CLI-003/004/006, COMPAT-002, LOG-005.
Implementiert, eigener Nachweis bestanden; unabhängiges Urteil noch ausstehend.

Beide Farbschreibweisen gelten ausschliesslich für die fünf Diagnosebefehle.
Der reine Parser normalisiert nur `--color=`, prüft fehlende/falsche/doppelte
Werte vor Host-IO und erhält `--`. Die Policy priorisiert Maschinenoutput,
explizite Wahl, NO_COLOR/TERM und zuletzt die injizierte stderr-Fähigkeit.
Alte CliEnvironment-Konstruktoren bleiben quellkompatibel und konservativ;
die effektive Umgebung bewahrt die Capability. Keine neue Abhängigkeit.

POSIX: einmalige Prüfung des tatsächlich geerbten FD2 mit `/bin/test -t 2`,
Argumentliste ohne Shell, feste zwei Sekunden Obergrenze, Fehler fail closed.
Keine Schlussfolgerung aus stdout/System.console. Windows bleibt in AUTO
konservativ plain; ALWAYS ist dort unabhängig von der Detektion verfügbar.
Die feste SGR-Palette färbt zunächst die Severity, jeder Stilbereich endet
mit Reset; alte Rendereraufrufe bleiben plain. JSON läuft nicht durch Styling.

## Tatsächliche Prüfungen

JDK Temurin25.0.3+9, Gradle9.8.1 Wrapper, macOS arm64, Node22.13.0 als
Guard-/Scratchhost. Projekt-Node24.22.0 bleibt für spätere portable Gates gepinnt.
Alle hier beschriebenen Java-/Prozessläufe unter bestehender OS-Python-Sperre.

- RED auf unmodifizierter Basis im separaten Worktree: neue ColorParserRedTest-
  Gegenprobe akzeptiert `check --color=always missing.ili` nicht, 1 Test/1 Failure,
  GradleExit1. `build/review/compiler-cli-color/preflight/red-parser.log`.
- GREEN: `./gradlew :interlis-compiler-cli:test compilerCliCheck pythonFreeCheck
  --rerun-tasks --console=plain` (Guard ergänzt --no-daemon), Exit0/25s,
  93 JUnit/0 Failures/Errors/Skips, bestehende26 echte CLI-Prozesse,
  Pythonquellprüfung1458 Eingaben. Log `build/review/compiler-cli-color/color1-gate.log`.
  Fünf neue JUnitmethoden prüfen alle Parserbefehle,144 Policykombinationen,
  feste Palette/Reset, UTF-8, Capabilitykopie, kanonische Diagnosen und JSONparität.
- Sieben echte Prozessproben im frisch gebauten Launcher: AUTO/ALWAYS/NEVER
  jeweils in Pipe und mit stderr-Datei bei weiter echtem stdout-PTY, zusätzlich
  AUTO mit beiden Kanälen im PTY. Echter semantischer Fehler, Exit1,
  AUTO nur bei beiden Terminalkanälen farbig, ALWAYS auch in Datei, NEVER plain;
  strenger SGR-Strip ergibt bytegleichen Text. Scratchrunner
  `build/review/compiler-cli-color/color1-process.cjs`, Ergebnisse `process1/`.
  Regulärer Exit0, Guard passed/0 in `color1-process-guard.json`.
  Der erste PTY-Versuch mit Socket-stdin scheiterte sichtbar an tcgetattr;
  der erfolgreiche neue Lauf erbte echte PTY-stdin vom Exec-Host. Kein Mockbeleg.
- `git diff --check` bestanden. Spezifikation18/16/17 synchronisiert.

COLOR2-Frames und kumulative COLOR3-Release-/Plattform-/Browsergates sind noch
nicht implementiert beziehungsweise nicht fällig. Kein unabhängiges PASS vorweggenommen.
