# Compiler-CLI – aktueller Standardvertrag

Der [CLI-Folgeauftrag](../implementation/compiler-cli-modernization/EXECUTION_PLAN.md)
ersetzt die frühere ili2c-Bedienparität. Historische P3-Abnahmen bleiben in
`reviews/` und `implementation/` unverändert auf ihren ursprünglichen SHAs gültig.

Modul `interlis-compiler-cli`, Programm `interlis-compiler`, einziger Entry
`guru.interlis.cli.Main`. Installation:
`interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler`
(Windows `.bat`), im SDK `cli/bin/interlis-compiler`. Keine alten Launcher,
Aliase, Wrapper oder `compat/ili2c.jar`. Bibliotheksmodul `interlis-compiler`
bleibt separat.

Befehle: `help`, `version`, `capabilities`, `check`, `compile`, `analyze`,
`diagram`, `format`. `--help`/`-h` und `--version` sind globale Kurzformen.
Beispiel: `interlis-compiler check --root Root Root.ili Base.ili`.
Alle lokalen Importdateien ausdrücklich angeben oder ein indexiertes Repository
verwenden; kein impliziter Verzeichnisscan. `--` beendet Optionen.

Exit 0 Erfolg, 1 fachliche/operative Ablehnung, 2 Bedien-/Konfigurationsfehler,
3 interner Fehler, 4 Abbruch. Nackte Dateien und alte Optionen werden vor IO
abgelehnt. Hilfe/Version gehen nach stdout. `check`/`compile`: Diagnosen stderr,
Erfolg stdout leer; `--json` liefert ein JSON-Dokument auf stdout.
`format`/`diagram` behalten ihre Fachausgaben; Hoststreams sind UTF-8.
Die `diagram`-Hilfe empfiehlt `--format json`; `--json` wird dort nicht als
nutzbare Option aufgelistet. Ein entsprechender Fehlaufruf verweist weiterhin
mit Exit2 auf `--format json`.

`--diagnostic-language de|en` gilt bei `check`, `compile`, `analyze`, `diagram`,
`format`, Default EN unabhängig vom Host. Ungültige Werte ergeben Exit2 vor IO.
`check`/`compile` und der Compile-Schritt von `diagram` verwenden die gemeinsame
immutable `AnalysisSnapshot.requestDiagnostics()`-Sicht; `analyze` behält die volle
Diagnostik. DE/EN ändern nur Prosa/Hinweise, keine IDs/Codes/Argumente/Ranges.
Namenskonflikte zeigen die wirkliche kollidierende Deklaration. JSON ergänzt
`message`/`hint`, RelatedLocations und Ursachenbezüge strukturiert.

`--color=auto|always|never` oder `--color MODE` gilt bei denselben fünf
Diagnosebefehlen; keine pauschale `--option=value`-Grammatik. Doppelte/falsche
Werte ergeben Exit2 vor Modell-/Repository-IO. AUTO ist Default und prüft auf
POSIX den tatsächlichen stderr-FD2, niemals stdout. Unbekannte Fähigkeit oder
Windows: plain. Nichtleeres NO_COLOR und TERM=dumb (case-insensitive) unterdrücken
AUTO; explizites ALWAYS/NEVER hat Vorrang. JSON und Fachartefakte bleiben plain.
Textdiagnosen zeigen standardmässig nur ihre Kopfzeile. `--verbose` ergänzt
Quellframes, verwandte Stellen und Hinweise bei allen fünf Diagnosebefehlen.
Ausführliche Textdiagnosen zeigen maximal3 Quellzeilen/160 Displayspalten aus der exakten
übergebenen Revision; Kontrollen werden escaped, unsichere Unicodebreiten
explizit ohne exakten Caret dargestellt. Bibliotheksrenderer bleiben per Default
farbfrei, CliEnvironment ist für bestehende Konstruktoraufrufe kompatibel.

`./gradlew compilerCliCheck` baut die neue Distribution und prüft tatsächliche
Prozesse mit Pfad-/Unicode-/Stream-/Fehlerfällen einschliesslich einer JVM mit
abweichender nativer Streamkodierung. `sdkProcessCheck` und `sdkArchiveCheck`
prüfen die Installation und einen unabhängigen Verbraucher aus dem SDK-ZIP.

Sprach-/Norm-/Metamodellvergleiche mit echtem externem ili2c bleiben erhalten.
Referenz 5.6.9, JAR-SHA-256
`37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e`.
Der Referenzcompiler und Testhilfen gehören nicht zur Produktdistribution.
Der aktuelle Umsetzungs-/Abnahmestand steht ausschliesslich im
[CLI-Status](../implementation/compiler-cli-modernization/STATUS.md); der neue
Farbumfang wird separat im [COLOR-Status](../implementation/compiler-cli-color-output/STATUS.md) geführt.

Das aktuelle Prozessprofil umfasst55 echte Fälle mit23 Farb-/JSON-/Kanalgegenproben;
auf macOS kommen6 reale PTY-/Redirectfälle hinzu. Headless Windows/Linux
behaupten kein interaktives Terminal. Die bestehenden Releaseprüfer verlangen
die Mindestzahlen des [aktuellen JVM-Profils](../../testdata/verification/jvm-profile.json)
(derzeit 1411 Tests/352 Suites) sowie alle 55 Fälle und erneut validierte Farbausgaben.
Prüfung einer entpackten Distribution ausserhalb des Checkouts:
`java -cp <verification-classpath> guru.interlis.verification.CompilerCliProcesses <repo-root> <external-launcher>`.
Das ist eine Hostprüfhilfe, kein Bestandteil der Produktdistribution.

Die ausgelieferten Launcher starten dieselben unmodifizierten Runtime-JARs als
vorhandene automatische Java-Module (`interlis.compiler.cli`, ALL-MODULE-PATH und ALL-DEFAULT).
Das vermeidet die beobachtete fehlerhafte CESU-8-Pfadkodierung des JDK25-
Klassenpfadloaders bei Supplementary-Zeichen im Installationspfad. Es gibt keine
neuen module-info-Dateien, Bibliotheksverträge oder Runtimeabhängigkeiten; die
SDK-JARs bleiben unverändert auch auf dem Klassenpfad verwendbar. Die bestehende
Windows-Unicode-Argumentbrücke bleibt erhalten.
Der Windows-Launcher übergibt den Modulpfad relativ zur Installation, damit die
native Java-Argumentkonvertierung keinen Unicode-Installationspfad beschädigt.
Das ursprüngliche Aufrufverzeichnis wird über die private bestehende Unicode-
Launcherbrücke übernommen; relative Modell-/Konfigurations-/Ausgabepfade bleiben
caller-relativ. Verzeichniswechsel und Rückkehr gelten nur im gestarteten Batch-
Prozess; der fachliche CLI-Kontext ist weiterhin explizit injiziert.

Der echte Prozessfall `repository-http` lädt Index und Modell über einen lokalen
HTTP-Server und kompiliert sie aus der externen Installation. ALL-DEFAULT erhält
die bisherigen JDK-Standardmodule des Klassenpfadstarts, insbesondere java.net.http;
der Modulstart darf vorhandene Repositoryfähigkeiten nicht ausblenden.


`check`, `compile`, `analyze` und `diagram` unterstützen denselben Repository-
Optionssatz (`--repository`, Pins, Cache, Offline, Locks, Konfiguration und
Warnungspolicy) gemäss Kapitel18. `--root MODEL` ist auch ohne Datei zulässig;
kein Defaultrepository. CLI-Werte überschreiben Konfigurationswerte.
`format` benötigt ausschliesslich Syntax und lehnt Repositoryoptionen ab.
`analyze` behält die volle Diagnostik ohne Compilegate. Locks bescheinigen nur
Quellenauswahl: Ausgabe nach erfolgreicher Kompilierung, erfolgreicher
Diagrammprojektion beziehungsweise abgeschlossener Analyse. JSON bleibt mit
und ohne `--verbose` vollständig; das Flag ändert nur die Textdiagnosen.
Die Fälle `repository-http-compile`, `repository-http-analyze` und
`repository-http-diagram` ergänzen `repository-http`; `compact` und `verbose`
prüfen die beiden Darstellungen aus der gebauten Distribution.

Repositoryfortschritt wird bei `--verbose` unmittelbar in Ereignisreihenfolge
angezeigt. Eine zweite Online-Abschlussprüfung entfällt. Alle
Diagnose-/Repositorykopfzeilen verwenden `SEVERITY [CODE] [Position: ]Text`;
INFO/WARNING/ERROR/HINT sind sprachunabhängig, Texte DE/EN. Suchschritte
werden im Text nicht nachträglich wiederholt; JSON-/Lockprovenienz bleibt
erhalten. HTTP verwendet identity; der Anwendungscache verwendet24h/7Tage
und optionale Index-MD5 statt ETags/304. Vary sperrt diese lokale Verwendung
nicht; no-store bleibt ausgeschlossen. Massgeblich ist Kapitel08 §17.
