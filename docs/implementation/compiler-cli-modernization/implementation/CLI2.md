# CLI2 – auftragsbezogene, verständliche Diagnostik

Phasenbasis **bd42bb940af646f938c74bf5f9768cf9ad7eda8a**; Auftragsbasis
c3d736ac82365524835adef8df40789f3fa3ae60. Kriterien CLI2-01–11,
DIAG-001–009, SEM-017/019, CLI-001/006 und gemeinsame API-Verträge.
CLI1-PASS bleibt auf ed25d5311346e429d201f5792ff8ff464577f3e7 belegt.

## Umsetzung und Beobachtung

RequestDiagnosticSelector ermittelt einmal den kanonisch geordneten Teilbestand
für Roots/Closure. AnalysisSnapshot veröffentlicht ihn immutable als
requestDiagnostics(); sein Konstruktor verlangt beide Listen und validiert die
geordnete Teilfolge. CompilationGate, check/compile und der Compile-Schritt von
diagram verwenden dieselbe Auswahl. diagnostics(), Workspace, LSP und das
WASM-/Browserprotokoll behalten die vollständige Sicht. Keine Dateinamenfilter
oder zweite Gateentscheidung in der CLI.

Semantik und Warnungen werden modellbezogen ausgewählt, auch bei zwei Modellen
in einer Datei. Lexer-/Parserfehler in benötigten Dateien bleiben konservativ
relevant; fremde Dateien nicht. Root-/Closurediagnosen, globale unzuordenbare
Eingabefehler und bisherige globale Overlay-/Translationkonfigurationsfehler
bleiben erhalten. Die Warnungsablehnung betrifft nur relevante Warnungen.

DiagnosticMessageFormatter, DiagnosticLanguage und DiagnosticMessage liegen im
reinen Diagnostikbereich von interlis-model: immutable DE-/EN-Templates,
explizite Sprache, keine IO/Localeänderung. Der Test iteriert den tatsächlichen
Katalog samt jedem Argumentplatz. Unbekannte externe Keys/Codes erhalten einen
neutralen Fallback; fehlende Argumente ein sichtbares ?. Exakte Zahlen und
sicher dargestellte Steuerzeichen bleiben erhalten. Der Binder erfindet keine
Importursache für einen bloss unaufgelösten Namen.

--diagnostic-language de|en gilt bei allen fünf Fachkommandos; EN ist unabhängig
vom Host Default. Ungültige Werte sind vor Host-IO Bedienfehler. Console- und
JSON-Ausgabe verwenden denselben Formatter. JSON ergänzt message/hint,
RelatedLocations und vorhandene Ursachen-/Abhängigkeitsbezüge; bestehende
kanonische Felder und Zahlendarstellung bleiben gleich. Ein Sprachvergleich
entfernt ausschliesslich Präsentationsfelder und vergleicht das übrige JSON.

GraphBuilder behält im bestehenden Namensindex die erste tatsächliche
Deklaration. Identische Akzeptanzregeln/Reihenfolge; Namenskonflikte tragen
zusätzlich deren echten RelatedRange. Keine Änderung der Primärargumente oder
Norminterpretation. Der Renderer rechnet nur bei passender expliziter Revision
in 1-basierte UTF-16-Positionen um; andernfalls URI/Revision/Offsets ohne IO.

Das Office-Original ist bytegleich SHA256
fbb1bccc0e84795284f9fb1a3c0be9e6db516c3c4b8ae43ad2f856cf70948431.
Reale EN-/DE-Prozesse liefern beide Exit1, Primary279:5/Related272:9,
ILI-SEM-0010 und eine verständliche Erklärung des Namenskonflikts mit Hinweis.
Eine separat erzeugte minimale OfficeControl.ili mit CLASS Contact statt des
kollidierenden Namens besteht; das Original wird nicht verändert.

## Tatsächliches RED und GREEN

Rohdaten: build/review/compiler-cli/CLI2/red/results.json (Basis oben),
getrennte stdout/stderr-Dateien und identische Eingaben. Drei echte Prozesse:

| Fall | RED | GREEN |
|---|---|---|
| Good.ili + Bad.ili, --root Good | Exit0 mit fremdem ILI-SEM-0001 | Exit0, beide Streams leer |
| Mixed.ili, Good + Bad, --root Good | Exit0 mit fremdem ILI-SEM-0001 | Exit0, beide Streams leer |
| Warnings.ili, Good + Other, --warnings reject --root Good | Exit1 wegen fremdem ILI-SYN-0007 | Exit0, beide Streams leer |

GREEN: build/review/compiler-cli/CLI2/green/results.json; wirkliche gebaute
Installation interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler.
Die erste explorative Probe mit syntaktisch defektem fremdem Modell wurde
getrennt als initial-with-foreign-syntax.json erhalten; sie ist kein Beleg für
eine Unterdrückung benötigter Dateisyntaxfehler.

## Prüfungen

Gezielt: Java25.0.3, Gradle9.8.1, :interlis-model:test :interlis-compiler:test
:interlis-compiler-cli:test :interlis-compiler-cli:installDist; targeted3.log,
Exit0/46s. Frühere Entwicklungsfehler (Testkonstruktor DecimalArgument,
Codeformat externer Probe, alte Textassertion, kanonisierte Resolver-ID) sind
in initial-targeted.log/targeted.log/targeted2.log sichtbar; nicht als PASS gezählt.

Neue öffentliche Vertragsfälle: RequestDiagnosticsContractTest (beide Sichten,
Warnungspolitik, Dateisyntax, Imports/Roots, globale Fehler, Fresh/Reuse und
Revisionen, Translation-/Overlayoriginale, echte Namenskonfliktranges),
DiagnosticMessageFormatterTest (ganzer Katalog/Argumente, Fallback, Zahlen,
Steuerzeichen, Konflikttext) und DiagnosticPresentationContractTest (echte
CliApplication, Streams, Sprachwahl vor IO, unverändertes Original/Kontrolle,
JSON-Identität, UTF-16/CRLF/Emoji und fehlende/veraltete Quellen).
Bestehende Lifecycle-/Flush-/IO-/Abbruch-/LSP-/SDK-Tests bleiben erhalten.

Zehn zusätzliche gemeinsame Portablefälle für INTERLIS2.3/2.4 prüfen echte
Rootwechsel, volle IDEdiagnostik, fremde Warnungen mit REJECT_WARNINGS,
importierte Fehler, fremde defekte Dateien und unveränderte Konfliktargumente.
Der tatsächliche Suiteumfang steigt 3548→3558; nur aktive exakte Zähler im
Releasechecker und Safari-Runner samt negativer Gegenprobe werden nachgeführt.
Keine alte Erwartung, Norm-/Quellenpin oder XFAIL geändert.

Kapitel06/09/16/17/18 und API-Migrationsnotiz sind synchronisiert. Bestehende
aktive Spezifikations-/Testquellenhashes und Codepointmethodenanker sind gezielt
auf dieselben Anforderungen/Assertions nachgeführt; keine historischen Reports
umgeschrieben. Das testMethods-Inventar bleibt eine Discoveryzahl, kein PASS.

Vollständiges Pflichtgate und unabhängige Abnahme werden unten nach ihrem
wirklichen Abschluss ergänzt. Bis dahin ist CLI2 nicht abgenommen.

Zusätzliche gebaute Prozesse: extra/results.json, fünf Fälle bestanden:
gemeinsame gültige Abhängigkeit mit fremdem defektem Importeur (Exit0/leere
Streams), fehlerhafte echte Abhängigkeit, mehrdeutiger Import, fehlender Root
(ohne erfundene Range) und fehlender Import (je Exit1/sichtbare Ursache).

Die ersten vollständigen Gateversuche blieben rot wegen der aktiven
GraphBuilder-/Vordefiniertenkatalog-Quellenbindungen. Nachgeführt werden nur
Hash/Codepointoffset/Zeile unveränderter verknüpfter Codeabschnitte und deren
abhängige Inventarhashes. Der direkte Java-Gegencheck des unveränderten
CatalogueEvidence.predefined besteht in anchor-probe.log. Keine fachliche
Assertion oder Normerwartung wurde abgeschwächt.

## Abgeschlossenes lokales Pflichtgate

Tatsächlich ausgeführt (Node24.22.0 absolut, JAVA_HOME Temurin25.0.3):

```sh
node tools/verification/without-python.cjs ./gradlew --no-daemon \
  check checkPortable conformanceCheck pythonFreeCheck semanticComparison \
  sdkProcessCheck sdkArchiveCheck compilerCliCheck releaseEvidenceCheckerTest \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
  --rerun-tasks --continue --console=plain
```

Rohlog gate3.log: **Exit0, 4m57s, 72 Tasks ausgeführt**. Finale Sperre:
gate3-environment.json, status passed/exitCode0. Die optionalen TeaVM-C-
Emscripten-/Disassemblytasks sind mangels solcher Eingaben erwartungsgemäss
SKIPPED; kein Pflichttest ist übersprungen.

XML-Auswertung gate3-junit.json: **1214 Tests/227 Suites, 0Failures/Errors/Skips**.
Node-Checker95/95, Client18/18, Diagramm4/4, jeweils keine Fehler/Skips.
TeaVM-JS und WasmGC je3558 Stabilisationsfälle plus24 Corpusregressionen,
status passed. Das sind lokale Entwicklungsberichte am geänderten Arbeitsbaum
mit Basis-HEAD bd42bb…, keine als sauber umetikettierten Commitberichte.
Der unabhängige Review baut den daraus committeten Kandidaten erneut sauber.

Conformance593: 577PASS/16XFAIL/0FAIL/XPASS/ERROR; tatsächlicher externer
Runnerexit1 bleibt erhalten und wird nur durch die bestehenden unveränderten
Normentscheidungen bewertet. Semantischer Vergleich: 4 reale Referenz-/JVM-
Fälle, 8 JS-/WasmGC-Projektionen und8 Mutationsgegenproben, bestanden.
SDK9/CLI19 reale Prozesse bestanden; entpackter SDK-Verbraucher bestanden,
SDK-SHA256 b39c9e6b3a3a7f475f65be899ee33626378d7cfa4a851c692dfbca96b67faea3.

CLI2-01–11 sind implementiert und lokal geprüft; unabhängige Abnahme noch offen.
CLI3-Pflichtläufe einschliesslich realer Browser/Web Image/Plattformmatrix sind
noch nicht als Folgeabnahme ausgeführt. Nächster Schritt: unabhängiger Review
am sauberen Kandidaten, erst nach belegtem PASS automatisch CLI3.

## Unabhängiger R1: FAIL und Nacharbeit F1/F2

Geprüfter Kandidat65ad26529bde5fd9e9834b336481d98c94079ff9; unveränderter
Bericht reviews/CLI2-r1-65ad26529bde-FAIL.md, SHA256
bca0bf5ff4d29fcdf14bf21a951aee77849240a3d52fcaec629ddd3458fd551d.
OpenCode2.0.28/DeepSeek direkt/deepseek-flash; äusserer ProzessExit0 und
finaler Guardpassed/0, HEAD/Produktquellen sauber. Das unabhängige Gate
bestand3m40s/72Tasks,1214/227JUnit ohne Fehler/Skips, aber die eigenen Proben
belegten F1/CLI2-04 und F2/CLI2-08. Deshalb kein CLI2-PASS.

Die beiden ersten Reviewer-Gateversuche scheiterten an fehlenden gepinnten
Node-Abhängigkeiten beziehungsweise einer ungetrackten node_modules-Verknüpfung
in der strikten Quelleninventur. Nach einer echten ignorierten Dependencykopie
bestand das volle Gate. Die Rohlogs gate1-missing-node.log/gate2-symlink.log
bleiben erhalten. Das sind keine verschwiegenen erfolgreichen Läufe.

Tatsächliches eigenes RED am weiterhin installierten65ad265-Artefakt:
rework-r2/red-65ad265.json. check auf INTERLIS2.4;GARBAGE liefert Exit1/leeres
stderr, JSONdiagnostics leer. Öffentliche API mit explizit aufgelöster, beschädigter
Importquelle: full=[ILI-SYN-0004,ILI-RES-0004], request=[ILI-RES-0004].
Formatter: bidiRaw=true für U+200E/U+200F/U+061C.

F1: Die einmalige Auswahl erhält zusätzlich explizite BySource-Roots und
ByName-Quellhinweise für konservative Syntaxrelevanz, auch ohne rekonstruierbare
Modelldeklaration. ImportBinder behält die SourceId einer tatsächlich aufgelösten
Importquelle bei fehlender/beschädigter versprochener Deklaration. Unverbundene
Dateien werden nicht anhand ihres Dateinamens einem fehlenden Import zugeordnet:
Im entsprechenden R1-Teilfall ohne Binding bleibt ILI-RES-0001 sichtbar, die
fremde Dateisyntax gehört weiterhin zur vollen Analyse. Neue positive/negative
Vertragstests unterscheiden diese Fälle ausdrücklich; keine CLI-Heuristik oder
Parser-Recovery-/Sprachänderung. Der Fachvertrag in Kapitel06 ist präzisiert.

F2: safeText escaped auch LRM/RLM/ALM (U+200E/U+200F/U+061C). Der bestehende
Sicherheitstest prüft deren sichtbare Escapes und die Abwesenheit roher Marken.
Neue CLI-Prozesse/API-Tests prüfen die no-model-Root-/Importfälle einschliesslich
JSON und diagram. Zwei weitere echte BySource-Portablefälle (2.3/2.4) prüfen
Rejectiondiagnosen bei nicht rekonstruierbarer Modelldeklaration; Umfang nun
3560 statt3558. Nur aktive exakte Zähler/Gegenprobenzähler folgen diesem Zusatz.

GREEN: rework-r2/green.json, Exit1 mit sichtbarem ILI-SYN-0004 und parsebarem
JSONteilbestand; tatsächlich gebundener Import request=[ILI-SYN-0004,ILI-RES-0004],
bidiRaw=false. Gezielt erneut Modell/Compiler/CLI/Installation: Exit0/32s,
rework-r2/targeted.log. Vollständiges neues Gate und R2-Abnahme noch offen.

R2-Nacharbeit lokal vollständig verifiziert: derselbe Pflichtgateaufruf ohne
--continue, rework-r2/gate.log, **Exit0/3m56s/72Tasks**, finaler Guardpassed/0
(environment.json). XMLs: **1217/227,0Failures/Errors/Skips** (junit.json).
Node95+18+4 bestanden; JS/WasmGC je3560+24 bestanden. Conformance unverändert
577PASS/16XFAIL/0FAIL/XPASS/ERROR; tatsächlicher Runnerexit1 unverändert.
Semantikvergleich4Cases/8Portableprojektionen/8Mutationen bestanden.
SDK9/CLI19 und frischer Archivverbraucher bestanden; SDK-SHA256
**dbbb76dcbe74d4b50aa46e7ef6a27016d0a57c903e13aceee09bea75271d831b**.
Diese lokalen Entwicklungsberichte sind nicht als saubere Commitberichte
umetikettiert; R2 baut den neuen committeten Kandidaten separat erneut.

## Unabhängiger R2: PASS

**Produkt-SHA dfd442c4a90d04a489bc48f1a6cbd52fc623435e**, Phasenbasis unverändert.
Unveränderter Bericht [CLI2-r2-dfd442c4a90d-PASS.md](../reviews/CLI2-r2-dfd442c4a90d-PASS.md),
SHA256 **1a6162944dec6a3ba4edcb511dede84a9d48bb6ac1cee5995c038de6efafbe97**.
OpenCode2.0.28, deepseek/deepseek-flash direkt api.deepseek.com. Prozess
vollständig beendet/Exit0; finaler Guardpassed/0, Sourceprofilhash und
absolute Interpreter-EPERM-Proben geprüft. Review-HEAD exakt Produkt-SHA,
Gitstatus leer; Kandidatenquellen unverändert. Alle11IDs fulfilled, keine
offenen Muss-Findings/fehlenden Pflichtchecks; Bericht/JSON konsistent.

Eigenes volles Reviewer-Gate **Exit0/3m31s/72Tasks**; XMLs unabhängig erneut
gezählt: **1217/227,0Failures/Errors/Skips**. JS/WasmGC je3560+24,
gitCommit exakt Produkt/dirtyfalse, gleicher SourceFingerprint
648a345796779f57beb8ee96aec7cc01e7130c96b75ab3f6054d4e376929048a.
Conformance577/16/0/0/0 mit21 Normbewertungen; SDKhash exakt gleich dem
lokalen frisch gebauten dbbb76…-Archiv. SDK9/CLI19 und Semantikvergleich bestanden.

Neue unabhängige Proben: zu anderer gültiger Modelldeklaration plus GARBAGE
aufgelöste Importquelle (Syntax-/Identitätsursachen beide sichtbar), identische
Quellen ohne Binding (nur klare Missing-Ursache, kein Dateinamensraten),
Fresh/Reuse mit zusätzlicher fremder beschädigter Quelle und Revisionwechsel,
Bidi/CRLF/DE-/EN-/JSON-Prozesse, Office-Original/Kontrolle. F1/F2 zusätzlich
gegen identische Inputs am echten R1-Artefakt RED und neuen Kandidaten GREEN.
Rohdaten: build/review/compiler-cli/CLI2-r2-dfd442c4a90d/worktree/build/review/compiler-cli/CLI2-r2/.
Prüfaudit: build/review/compiler-cli/CLI2-r2-dfd442c4a90d/acceptance-audit.json.

**CLI2 abgeschlossen/PASS.** Ohne erneute Bestätigung folgt CLI3 mit sauberer
Phasenbasis nach diesem reinen Ablage-/Statuscommit. Die CLI3-Abnahme bleibt offen.
