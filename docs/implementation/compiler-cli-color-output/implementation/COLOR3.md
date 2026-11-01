# COLOR3 – Releaseintegration und kumulativer Abschluss

BASE_SHA: `7524356c9689654ce7a4ffc95f523af937b906b9` (unabhängiges COLOR2-PASS).
Umfang C3-01–10, abschliessend kumulativ C1/C2. Implementiert und lokal geprüft;
Release-/Plattform-/Browsergesamtabnahme und unabhängiger COLOR3-Review sind offen.

## Implementierung und konkrete lokale Beobachtung

CompilerCliProcesses enthält46 echte Fälle statt26: Farbmodi, NO_COLOR/TERM,
explizite Overrides, beide Optionsschreibweisen, falsche/doppelte Optionen,
DE/EN, alle5 Befehle, JSONbyteparität sowie getrennte format-/diagram-Ausgaben.
Auf macOS6 zusätzliche echte script(1)-PTY-/Redirectfälle, einschliesslich
stdout-PTY mit stderr-Datei. Headless Linux/Windows behaupten keine Interaktivität.
Jeder reguläre Prozesslauf kopiert die echte Installation in einen externen
Pfad mit Leerzeichen/Umlaut/Emoji; optional prüft derselbe Checker eine separat
entpackte Distribution. Keine Testclasspath-Abkürzung für den Produktlauncher.
Der bestehende separate Native-Encodingfall startet die Produktmodule mit
abweichenden stdout/stderr-Einstellungen, weiterhin UTF-8.

Der Checker prüft Farbparität mit strenger SGR-Whitelist sowie Resets und echte
Kanal-/Exitverträge erneut, auch beim Einlesen der Prozessberichte in
PlatformProcesses. Fehlende Fälle, ANSI in JSON/Fachausgabe, Cursorbefehle,
falsche Exits und fehlende Resets haben aktive Negativtests. Releaseprüfer
verlangen mindestens1230 Tests/229 Suites, alle46 Fälle und nachgeprüfte Farbe;
ANSI in maschinellen Plattformberichten, fehlende Farbevidenz, fremde SHAs,
fehlende/übersprungene Daten und manipulierte Inventare werden abgewiesen.
Keine bestehende Pflichtprüfung oder Norm-/Conformance-Erwartung abgeschwächt.

### Tatsächlich gefundener Auslieferungsfehler und begrenzte Korrektur

Die erste echte ZIP-Probe aus einem Emoji-Installationspfad scheiterte bereits
bei Main-Class-Ladung. JAR-Hashes waren identisch; JarFile und URI-basierter
URLClassLoader fanden Main.class. Der JDK25.0.3-Klassenpfadloader verwendete
hingegen CESU-8 (`%ed%a0%bd%ed%b8%80`) für das Supplementary-Zeichen und meldete
`Error decoding percent encoded characters`. Ein direkter Modulpfadaufruf
startete dieselben unmodifizierten JARs erfolgreich. Rohlogs unter
`build/review/compiler-cli-color/external/`, einschliesslich classloader.debug.

Die Gradle-Launcher starten nun die vorhandenen automatischen Module mit
`--module-path .../lib --add-modules ALL-MODULE-PATH --module
interlis.compiler.cli/guru.interlis.cli.Main`. Keine module-info-Dateien,
Core-/SDK-API-Änderung oder neue Abhängigkeit. Die bestehende Windows-
Unicode-Argumentbrücke bleibt bestehen. Die Templates werden vor Ersetzung
exakt erkannt; unbekannte Vorlagen führen zu Buildfehlern statt stillen Skripten.
Die SDK-JARs bleiben als gewöhnliche Klassenpfadbibliotheken nutzbar.

## Tatsächliche lokale Prüfungen vor Kandidatencommit

Temurin25.0.3+9, Gradle9.8.1, macOS arm64, gepinntes Node24.22.0 für Nodegates;
akzeptierte Läufe unter originalem vererbtem Python-OS-Guard, keine Pythonhilfe.
Rohpfade unter `build/review/compiler-cli-color/`.

- `./gradlew check compilerCliCheck releaseEvidenceCheckerTest pythonFreeCheck
  :interlis-compiler-cli:distZip -PnodeExecutable=<Node24.22.0> --rerun-tasks
  --no-parallel --max-workers=2 --console=plain`: Exit0/59s,58 Tasks ausgeführt,
  CLI100 JUnit und Verification25 ohne Fehler/Skips, Nodechecker100 PASS,
 46 reale CLI-Fälle und6 macOS-PTYfälle, Pythonquellprüfung1465 Inputs.
  `color3-precommit-gate.log`, `color3-full-check-guard.json` passed/0.
  Ein früherer gezielter Lauf ohne Nodeparameter wurde korrekt durch den
  bestehenden Pin mit Node22 statt24 abgewiesen; der erfolgreiche Lauf nutzte
  den richtigen vorhandenen Runtimepfad, keinen Pinwechsel.
- Nach Integration der externen Installationskopie:
  `./gradlew compilerCliCheck :interlis-compiler-cli:verificationTest
  pythonFreeCheck -PnodeExecutable=<Node24.22.0> --no-parallel --max-workers=2
  --console=plain`: Exit0/21s,46+6 Prozesse bestanden,25 Verificationtests,
  Guard passed/0 (`color3-external-copy.log`, `color3-copy-guard.json`).
- Frisch gebautes Modul-ZIP mit beiden tatsächlichen Launchern ausserhalb
  des Checkouts in Pfad `interlis color ä 😀 ...` entpackt:46 echte CLI-Fälle,
 6 echte PTY-/Redirectfälle und2 Office-DE-Aufrufe (ALWAYS/NEVER) bestanden.
  Vollständige Textparität nach strengem SGR-Strip, gleiche fachliche Exits,
  echter RelatedLocation-Text. `color3-external-final.log`, `external/result.json`,
  `external/processes.json`, `external/office.*`; ProzessExit0 und
  `color3-external-guard.json` passed/0. Temporäre Installation danach entfernt.
- `git diff --check`: Exit0. README, Hilfe, aktuelle CLI-/SDK-Profile nachgeführt;
  frühere Reviewberichte unverändert übernommen.

## Offene Pflichtnachweise

C3-05/06/07/10 sind noch nicht abgenommen. Aktuelle Windows-/Linux-GitHub-
Evidenz für den finalen Kandidaten, vollständiger releaseCheckP3 und unabhängiger
kumulativer Review bleiben Pflicht. Historische grüne Belege werden nicht
wiederverwendet. Auftrag erlaubt Push und Workflowstart erst nach gesonderter
Freigabe; kein Push/Merge/Release wurde vorgenommen.

Der erste weitere Schritt ist der saubere Kandidatencommit. Dessen SHA wird
anschliessend extern dokumentiert, nicht als Selbstreferenz in diesem Bericht.

## Nacharbeit nach erster realer Plattformmatrix

Gesonderte Nutzerfreigabe zum Branchpush und Start des bestehenden Workflows
wurde erteilt. Kandidat `6fd9d794f7cd836db79efa02d56369a679b05483` wurde gepusht;
[CI41289594757](https://github.com/edigonzales/interlis-java/actions/runs/41289594757):
Linux erfolgreich, Windows beim ersten externen Unicode-Installationsprozess
fehlgeschlagen (MalformedInputException im strengen UTF-8-Leser, kein PASS).
Eigener lokaler Plattform-/SDK-/Conformance-/Checkerlauf auf6fd9d79 bestand
Exit0/3m16s,1230 Tests; er ist kein Nachweis des nachgearbeiteten Produkts.
Rohlogs und das fehlgeschlagene Windowsartefakt bleiben unter
`build/review/compiler-cli-color/windows-6fd9d79-*` erhalten.

Der Windows-Modulpfad wird nun als ASCII-relatives `lib` innerhalb der
Installation übergeben. Die private Unicode-Launcherbrücke bewahrt das
ursprüngliche Arbeitsverzeichnis; Main übergibt es explizit an CliEnvironment,
ohne globale user.dir-/Locale-/Streamänderung. Exitcode und Verzeichnisstack
werden nach dem Java-Prozess erhalten/zurückgeführt. Relative Modell-, Config-
und Ausgabeziele bleiben im ursprünglichen Aufrufverzeichnis. Bestehende
Bridgeaufrufer ohne neuen Verzeichniswert behalten ihr bisheriges Verhalten.
Der Native-Encodingfall verwendet jetzt den echten Launcher mit festen
JAVA_OPTS statt direkter Java-Argumente in einem Unicode-Installationspfad.

HostChecks bleibt streng UTF-8; bei einem erneuten nativen Startfehler werden
begrenzt die Rohbytes als Hex diagnostiziert, statt den entscheidenden
Fehlertext durch einen Decoderstacktrace zu verlieren oder still zu ersetzen.

Nacharbeit lokal: `:interlis-compiler-cli:test compilerCliCheck
:interlis-compiler-cli:verificationTest pythonFreeCheck
:interlis-compiler-cli:distZip -PnodeExecutable=<Node24.22.0> --no-parallel
--max-workers=2 --console=plain`, Exit0/25s,100 CLI-/25 Verificationtests,
46+6 echte Prozesse und1466 Pythonfreiheitsinputs; Guard passed/0.
Log `color3-windows-fix-local.log`, Manifest `color3-windows-fix-guard.json`.
Die neue Windows-/Linux-Matrix und alle finalen Pflichtgates sind erneut nötig.

## Zurückgezogener Kandidat f766d41 und HTTP-Regression

[CI41290110830](https://github.com/edigonzales/interlis-java/actions/runs/41290110830)
bestand auf `f766d41bb2998ccb5bd088718cdb332c148bb845` für Linux und Windows:
je1230 Tests/229 Suites,46 CLI-Fälle, SDK-ZIP9c48c4c28729f3881a8ca0bd21f732d29eb7f9703e0fda66cccfd01c4e5b92dc.
Der eigene vollständige Release-/Zusatzlauf bestand nach36m13s,106 Tasks,
347 Closures auf6 Laufzeiten,2000 Änderungen und76 reproduzierten Dateien,
ProzessExit0/Guard passed0. Alle Belege bleiben erhalten, sind aber ausdrücklich
keine Gesamtfreigabe dieses nachfolgend zurückgezogenen Produkts.

Eine weitere eigene echte Repositoryprobe fand den fehlenden JDK-Modulroot:
`interlis-compiler check --repository http://127.0.0.1:9/ --root Missing`
stürzte vor fachlicher Behandlung mit `NoClassDefFoundError:
java/net/http/HttpTimeoutException` ab. Rohlog `module-http-red.log`,
Guard `module-http-red-guard.json`. Die neue Modulstartkonfiguration hatte
vorhandene JDK-Standardmodule ausgeblendet. Das war eine produktive Regression,
kein Sprach- oder Infrastrukturfehler.

Der bereits gestartete unabhängige COLOR3-R1-Review wurde darum zurückgezogen
und kontrolliert ohne Urteil/Abnahme beendet; seine eigenen laufenden Kinder
wurden beendet. Keine PASS-Aussage dieses Reviews wird akzeptiert. Session
ses_e69911512ffeMJBe03LpMiHMHF, originaler ProzessExit1 nach Unterbrechung;
`build/review/compiler-cli-color/COLOR3-r1-f766d41bb299/withdrawal.json`
enthält konkreten Befund, Originalguard und zugeordnete Prozesse. Rohsession und
unveränderter Reviewworktree bleiben erhalten. Kein Transportroulette.

Die Launcher verwenden jetzt `--add-modules ALL-MODULE-PATH,ALL-DEFAULT` und
bewahren dadurch die Standardmodulauflösung des früheren Klassenpfadstarts.
Der bestehende Prozesschecker enthält neu `repository-http`: ein echter lokaler
HTTP-Server liefert Index und Modelldatei; der reale externe Produktlauncher
muss beide abholen und die benannte Closure mit Exit0 ohne Kanalverschmutzung
kompilieren. Die aktive Plattform-/Release-Inventarmenge verlangt jetzt47 Fälle.
Keine zusätzliche Runtime oder produktive Bibliothek, keine Core-/Normänderung.

Gezielte GREEN-Prüfung nach Fix: `compilerCliCheck :interlis-compiler-cli:test
:interlis-compiler-cli:verificationTest releaseEvidenceCheckerTest pythonFreeCheck
:interlis-compiler-cli:distZip -PnodeExecutable=<Node24.22.0> --no-parallel
--max-workers=2 --console=plain`: Exit0/29s,47 CLI+6 PTY,100 CLI-/25
Verificationtests,100 Nodechecker,1466 Sourceinputs; originaler Guard passed0
(`color3-http-fix.log`, `color3-http-fix-guard.json`).
Neue Commit-/CI-/FullRelease-/unabhängige C3-Gesamtabnahme ist erforderlich.

Kandidat2d1c914d7e6733a2aa1f13e48b58abf74013d5b5 wurde nach erfolgreichen
Linux-/Windows-Profilen (CI41294662800, je1230 Tests/229 Suites/47 Prozesse)
ohne Gesamtabnahme zurückgezogen. Eigene echte Gegenproben belegten Exit1
statt UsageError2 für `check --config --color=always missing.ili` sowie rohe
ESC-/Bidi-Zeichen im Dateinamen von `format --check`. Der laufende Gesamtgate
wurde kontrolliert beendet; kein unabhängiger Review dieses Kandidaten gestartet.
Rohbelege: `build/review/compiler-cli-color/{parser-and-path-red.json,2d1c914-withdrawal.json}`.
Die Nacharbeit prüft Inline-Farbe an jeder Optionswertposition vor Host-IO, erhält
benutzerdefinierte Optionsaritäten und verwendet zentrales safeText für freie
Usage-/Formatpfadtexte. Zwei neue JUnit-Tests und drei reale Prozesse ergänzen
den aktiven Pflichtumfang auf mindestens1232 Tests/229 Suites/50 Prozesse.
Frische CI, vollständiger Gesamtgate und unabhängiger kumulativer Review bleiben fällig.

Nacharbeit GREEN: eigener Guardlauf `:interlis-compiler-cli:test :interlis-compiler-cli:verificationTest compilerCliCheck pythonFreeCheck` mit Node24/JDK25, --no-parallel/--max-workers=2: Exit0/25s,102+25 Tests ohne Fehler/Skips,50 echte CLI- und6 macOS-PTY-Fälle,1466 Pythonfreie Inputs. `node --test tools/release/evidence.test.cjs`:73/73 PASS,0 Skips. Rohlogs controls-green-r3.log/controls-green-guard.json/controls-evidence-tests.log. Vorheriger lokaler Zwischenlauf meldete korrekt fehlende Parserhilfsmethode, danach zeigte der neue Test den noch ungeschützten Usagepfad; beide behoben, keine Abnahme dieser Zwischenstände.
