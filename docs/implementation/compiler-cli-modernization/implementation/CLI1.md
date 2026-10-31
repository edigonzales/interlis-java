# CLI1 – eigenständiger Programmeinstieg

Basis der ganzen Phase und Auftragsbasis:
`c3d736ac82365524835adef8df40789f3fa3ae60` (sauberer main-Stand).
Arbeitszweig `codex/compiler-cli-modernization`. Kriterien CLI1-01–09;
zugehörige Verträge CLI-001–007, COMPAT-001–003, LOG-005, BUILD-003/006/009,
GOAL-007/011 und SDK-001–003. Keine Änderung der Sprach-/Normakzeptanz.
Zusätzlich BUILD-005: Die tatsächlich beobachtete api→antlr-Vererbung des Gradleplugins ist gezielt gelöst; Generator/Runtime bleiben beide4.13.2. Das neue ParserRuntimeDependencyContractTest und die reale SDK-JAR-Inventur sichern die Grenze. Vorherbeleg: runtime-dependencies-before.log und ursprüngliche Distributionsliste, kein erfundener historischer Test-RED.

## Umsetzung und erwartete Beobachtung

Alle fünf Source-Sets nach interlis-compiler-cli verschoben. applicationName
und Distributionsbasis explizit interlis-compiler; CLI-JAR bleibt eindeutig
interlis-compiler-cli-0.2.0-SNAPSHOT.jar neben der Compilerbibliothek.
Einziger Prozesseinstieg Main; die fünf Legacyklassen und der ausschliesslich
für das Legacybanner verwendete ToolVersionRenderer sind entfernt. Gemeinsame
Helfer besitzen keine Legacyparameter/-zustände. Globale Hilfe/Version sind
Standardaufrufe; fehlender/ungültiger Unterbefehl ergibt Exit2 mit konkretem
check-Beispiel vor Modell-IO. Exits0/1/2/3/4 und Lifecycle-/Flushvertrag bleiben.

Gradle-/Conformance-/SDK-/Plattform-/Releasepfade verwenden die neue Distribution.
Der Lieferchecker verlangt neue Launcher/JARs und verwirft alte Launcher und
compat/ili2c.jar. SDK-ZIP-Prüfung kontrolliert zusätzlich die realen Einträge.
Standardprozesse laufen im fremden Verzeichnis mit Leerzeichen/Umlaut/Emoji,
führendem Minus, Imports, Syntax-/Semantik-/IO-Fehlern und einer Gegenkonfiguration
mit windows-1252-Nativstreams; die Produktausgabe bleibt UTF-8.

Aktive Spezifikation und P3-Profil-/SDK-Anleitung sind nachgeführt. Historische
Implementierungs-/Reviewberichte bleiben unverändert. Alte G2-Quellenlocator
werden nur für interlis-cli/src/ und interlis-cli/build/test-results/ explizit
auf das neue Modul abgebildet; der historische A1-Baselineinput bleibt bytegleich.
Bestehende aktive Spezifikations-/Buildpins und Zeilenlocator sind mechanisch
synchronisiert; keine Norm-/Original-/Kontrollpins oder Outcomes geändert.

## Testdelta

| Einordnung | Betroffene Tests / Verhalten |
|---|---|
| REMOVE_LEGACY_ONLY | P3LegacyCliContractTest: alte Hilfe-/Bannerkanäle, quiet/without-warnings/trace-Optionen, modeldir-Inventarisierung und Unsupported-Legacykatalog; LegacyCliProcesses/LegacyCliProcessesTest: reine Bedienparität, 54 alte Prozesse, JAR-Einstieg. Die Tasks/Artefakte entfallen vollständig. |
| MIGRATE_STANDARD | CompilerCliIdentityContractTest: Hilfe/Version, Bedienfehler vor Compilerstart, explizite Imports, kein Nachbarschaftsscan, Warnungsdaten/-policy, Unicode/Minusdateien; CompilerCliProcesses/CompilerCliProcessesTest: 19 benannte Standardprozesse einschliesslich Syntax/Semantik, fehlender Datei/Import, mehrere Modelle, --, fremdem CWD und erzwungener abweichender nativer Streamkodierung. Laufbeobachtung weiter über P3CliLifecycleContractTest. |
| KEEP_REFERENCE | ReferenceProcesses, NormativeConformance, SemanticComparison, LegacyOidCompatibilityTest und LegacySourceMetadataTest; echte externe ili2c-Prozesse und unveränderte fachliche Erwartungen. |
| RELOCATE_ONLY | Übrige reguläre, Evidenz-, Host-/Hosttestquellen, SDK-/LSP-/Workspace-/Compilerverträge; nur nötige Modul-/Distributionslocator angepasst. |

## Verifikation

OpenCode-Preflight: v2.0.28; auth list zeigt gespeichertes DeepSeek, tatsächlicher
Modellkatalog deepseek/deepseek-flash und direkter Endpoint api.deepseek.com;
[offizielle Zuordnung](https://api-docs.deepseek.com/quick_start/pricing/)
am31.10.2026: DeepSeek-V4.1-Flash-1017. Privater Standalone-Bereitschaftslauf:
READY, ProzessExit0, Guardpassed mit PATH-ENOENT und absolutem EPERM.
Rohbelege build/review/compiler-cli/preflight/.

Gezielt: JAVA_HOME auf vorhandenes Temurin25.0.3, Repositorywrapper9.8.1,
:interlis-compiler-cli:test :interlis-compiler-cli:verificationTest
:interlis-compiler-cli:installDist, Exit0/14s. Neuer Launcher real gebaut.

Vollständiges CLI1-Gate unter vorhandenem OS-Guard und Node24.22.0:

```sh
node tools/verification/without-python.cjs ./gradlew --no-daemon \
  check conformanceCheck pythonFreeCheck sdkProcessCheck sdkArchiveCheck \
  compilerCliCheck releaseEvidenceCheckerTest \
  :interlis-compiler-cli:installDist :interlis-compiler-cli:distZip \
  -PnodeExecutable=<vorhandenes Node24.22.0> --rerun-tasks --console=plain
```

Erster Lauf: Exit1, zwei veraltete aktive Dokument-/Buildpins; frische
SDK9/CompilerCLI19, 94 Releasechecker-Tests und externer Rohbefund
577PASS/1XFAIL/15FAIL/0ERROR/0XPASS. Die Pinursachen sind korrigiert;
keine fachliche Erwartung angepasst. Log build/review/compiler-cli/cli1-gate.log.
R2-Gate: Exit0/2m43s/64 ausgeführte Tasks. Nach der Generatorbereinigung
meldete der nächste Lauf ausschliesslich die veraltete Testmethodenzahl749;
die neue reale Methode ergibt750. Der aktive Entdeckungszähler und sein Pin
sind nachgeführt, der gezielte g2EvidenceTest besteht (Exit0/34s).

Abschliessender vollständiger Arbeitsstandlauf: **Exit0/3m21s, 64/64 Tasks
ausgeführt**, 1196 JUnit-Fälle/224 XML-Suites, jeweils0 Failures/Errors/Skips;
94 Node-Checkerfälle/0 Failures/Skips, 1433 Pythonfrei-Inputs. Projektconformance
577PASS/16XFAIL/0FAIL/ERROR/XPASS, 21 unveränderte Normkontrollen. SDK9,
CompilerCLI19, SDK-Consumer samt javac/Launcher aus echtem ZIP erfolgreich.
SDK-ZIP-SHA256:1844b69855a44ea468b7f33f91af6a7b43c96723529db3524ce8a7b6848e5bd9.
Neue Installation enthält nur sechs Produkt-JARs und antlr4-runtime; CLI-JAR
enthält weder entfernte Entries noch Verifikationsklassen. Office-SHA erneut
bestätigt. Rohdaten build/review/compiler-cli/cli1-gate-r4.log,
cli1-gate-r4-guard.json (passed/Exit0), cli1-junit-counts.json.
Die unabhängige Kandidatenabnahme ist noch ausstehend.

CLI2/CLI3 beginnen erst nach unabhängigem CLI1-PASS. Vollständige Portable-/
Browser-/Safari-/WebImage-/Performance-/Plattform-/Reproduzierbarkeitspflichten
bleiben für ihre bezeichneten Phasen fällig und sind hier nicht als ausgeführt
behauptet. Keine Veröffentlichung, kein Push, Merge oder Release.

## Unabhängiger R1-FAIL und Nacharbeit

Kandidat beca84286ba964de7c5ccbf941f4c48c2c3c0055, DeepSeek direkt/OpenCode2.0.28,
frischer Worktree und identische saubere Scratch-Kopie. ReviewprozessExit0,
Guardpassed/Exit0, Produkt unverändert. Unveränderter Bericht:
[CLI1-r1-beca84286ba9-FAIL.md](../reviews/CLI1-r1-beca84286ba9-FAIL.md),
SHA256 226081f6d0ca1490e975f9e264fb3a95306713f5cf62e2ba70a7436396624104.
Eigenes unabhängiges Gate Exit0/2m41s/64 Tasks, 1196 JUnit ohne Fehler/Skips,
94 Nodefälle, Conformance577/16, SDK9/Standard19. Trotzdem FAIL: F1 SDK-ZIP
Unixlauncher0644, direkter Start Exit126, bisher durch setExecutable maskiert;
F2 zwei aktive Legacy-API-Dokumentstellen. Keine fehlenden CLI1-Pflichtprüfungen.

F1-Korrektur: SDK-Zip speichert ausschliesslich die beiden neuen Launcher
explizit0755, inklusive benanntem Taskinput für zuverlässigen Neubau bei
vorherigen lokalen Ausgaben. Unter Unix extrahiert der SDK-Consumer mit dem
vorhandenen nativen /usr/bin/unzip und startet ohne Reparatur; Windows behält
die reguläre Java-Extraktion. Geladene Einträge werden weiterhin auf sichere
Pfade, Namen, Hashes und reguläre Dateien geprüft. Der Lieferinventarchecker
verwirft unter POSIX nicht ausführbare Launcher; neue Negativprobe.

Tatsächliches RED mit dem alten Original-SDK in isolierter Kopie: der korrigierte
Consumer scheitert mit Exit1 und Delivered SDK Unix launcher is not executable
(cli1-f1-canary-red2.log). Tatsächliches GREEN: sdkArchiveCheck Exit0/8s,
SDK-ZIP b6efcbc6d6c73350bf70c19869025fce1776d6a5200da3ea4db30d162a112bae,
zipinfo zeigt beide Launcher0755 (cli1-f1-green3.log). Kein chmod im Checker.
F2: aktive API-Migration und Architekturrolle korrigiert, aktuelle
Conformance-Reproduktionskommandos nachgeführt; historische Resultate unverändert.
Vollständiges Nacharbeitsgate: **Exit0/2m33s/64 von64 Tasks ausgeführt**,
1196 JUnit/224 XML ohne Failures/Errors/Skips, **95** Node-Checkerfälle ohne
Fehler/Skips, Conformance577PASS/16XFAIL/0FAIL/ERROR/XPASS und Norm21, SDK9/
CompilerCLI19, nativer SDK-Consumer ohne chmod. Log cli1-rework-gate.log,
Guard cli1-rework-gate-guard.json (passed/Exit0), Counts cli1-rework-junit-counts.json.
R2-Abnahme ist noch ausstehend.

## R2-FAIL: F1/F2 geschlossen, F3 korrigiert

R2 am Produkt9a8c4087591a06f1f6b9a43a71dee07623ef7f12: ProzessExit0,
Guardpassed/Exit0, unveränderter Kandidat.
[Unveränderter Bericht](../reviews/CLI1-r2-9a8c4087591a-FAIL.md),
SHA256 1c3ca08b55c31b4f03663690d3ab42d07ec115d896ea31cfc94a4eb0ecbfc86c.
Alle funktionalen Kriterien bestanden; F1/F2 unabhängig geschlossen, inklusive
mutierter0644-SDK-ZIP-Kopie (echter Consumer Exit1) und restaurierter positiver
Kontrolle. Vollständiges unabhängiges Gate Exit0/2m31s/64 Tasks, JUnit1196/224,
Node95, Norm21, Conformance577/16, SDK9/CLI19 ohne offene technische Fehler.
Einziger neuer Muss-Befund F3: das aktive format-Beispiel verwendete versehentlich
den Modulnamen als Programm. Das eine Token ist auf interlis-compiler korrigiert,
aktive Spezifikationspins sind nachgeführt. Breiter Audit der aktuellen Aufruf-
beispiele findet keine weiteren Modul-/Programmnamenverwechslungen. Das Programm
aus dem tatsächlichen korrigierten Dokumentbeispiel wurde ausgeführt: format
Exit0, stdout mit formatierter Base-Quelle, stderr leer; Rohbeleg
build/review/compiler-cli/f3-example/result.json. Keine Produktcode-/Normänderung.
Vollständiges Nacharbeitsgate: Exit0/3m21s/64 ausgeführte Tasks; JUnit1196/224
und Node95 ohne Fehler/Skips, Conformance577/16, Norm21, SDK9/CLI19 und nativer
SDK-Consumer. Log cli1-r3-gate.log, Guard cli1-r3-gate-guard.json (passed/Exit0),
Counts cli1-r3-junit-counts.json. Frischer R3-Review ausstehend.

## Unabhängiger CLI1-Abschluss

**CLI1-01–09: PASS** am Produkt ed25d5311346e429d201f5792ff8ff464577f3e7.
[Unveränderter R3-Review](../reviews/CLI1-r3-ed25d5311346-PASS.md), SHA256
3ae6cebb9a3eed70f2874ec439d26040f261295bbf84b9cfba1789718726a967.
F1/F2/F3 geschlossen. Eigener vollständiger Reviewer-Lauf Exit0/2m36s/64Tasks,
1196 JUnit/224 XML und Node95 ohne Fehler/Skips, Conformance577/16, Norm21,
SDK9/CLI19, direkter SDK-Start und neue tatsächliche Negativ-/Netz-/Importproben.
Äusserer ProzessExit0, abgeschlossenes Guardmanifest passed/Exit0; Kandidaten-
HEAD/Baum und identischer Scratch-Baum sauber. Maschinenprüfung siehe
build/review/compiler-cli/CLI1-r3-ed25d5311346/acceptance-audit.json.
Dieser Ablagecommit ändert ausschliesslich Bericht/Status. CLI2 startet danach
automatisch; keine CLI2-/CLI3-Abnahme wird vorweggenommen.
