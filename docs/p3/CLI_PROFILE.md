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

`./gradlew compilerCliCheck` baut die neue Distribution und prüft tatsächliche
Prozesse mit Pfad-/Unicode-/Stream-/Fehlerfällen einschliesslich einer JVM mit
abweichender nativer Streamkodierung. `sdkProcessCheck` und `sdkArchiveCheck`
prüfen die Installation und einen unabhängigen Verbraucher aus dem SDK-ZIP.

Sprach-/Norm-/Metamodellvergleiche mit echtem externem ili2c bleiben erhalten.
Referenz 5.6.9, JAR-SHA-256
`37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e`.
Der Referenzcompiler und Testhilfen gehören nicht zur Produktdistribution.
Der aktuelle Umsetzungs-/Abnahmestand steht ausschliesslich im
[CLI-Status](../implementation/compiler-cli-modernization/STATUS.md).
