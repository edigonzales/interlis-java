# ili2c-Prüfaufrufe – Profil ili2c-check/5.6.9-v1

P3 qualifiziert Compiler-Prüfaufrufe. Generatoren, GUI, Repositoryadministration
und die historische Java-API sind nicht Bestandteil dieses Profils.
Die unabhängige P3-1-Abnahme steht noch aus; dies dokumentiert den implementierten
und lokal geprüften Umfang, keinen abgeschlossenen G3-Nachweis.

Referenz: ili2c `5.6.9-bf16026e83f2377153b2e818a4f3838f0a423722`,
JAR-SHA-256 `37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e`.
Beschaffung/Pins: `ReferenceProcesses`, offizielles Archiv
[ili2c-5.6.9.zip](https://downloads.interlis.ch/ili2c/ili2c-5.6.9.zip).
Der Referenzcompiler wird ausschliesslich extern zur Verifikation gestartet;
er ist keine Laufzeitabhängigkeit des Produkts.

## Einstieg und Optionen

Die Distribution enthält `bin/ili2c`, `bin/ili2c.bat` sowie
`compat/ili2c.jar` mit Main-Class `guru.interlis.cli.Ili2cMain`.
Der normale Launcher erkennt zusätzlich bisherige Standardkommandos.
In-process: `new DefaultCompilerCli(LegacyCliProfile.ili2c569())` mit
injiziertem `CliEnvironment`. Alle Varianten verwenden dieselbe Compilerengine.

| Unterstützte Schreibweisen | Wirkung |
|---|---|
| `-h`, `--help`, `-u`, `--usage` | Profilhilfe auf stderr, Exit 0 |
| `-v`, `--version` | Eigene Toolidentität und Referenzprofil getrennt, stderr, Exit 0 |
| `-o0` | Modellprüfung ohne Generator |
| `--no-auto` | Nur ausdrücklich geladene Dateien; keine Suchverzeichnisinventarisierung |
| `--modeldir VALUE` | Semikolonliste lokaler Verzeichnisse/HTTP-Repositories; `%ILI_DIR` und `%JAR_DIR` |
| `--quiet` | Laufbanner unterdrücken; Fehler bleiben sichtbar |
| `--without-warnings` | Warnungstext unterdrücken; kanonische Diagnosen und Gate unverändert |
| `--trace` | Tatsächliche Lauf-/Phasenereignisse auf stderr |
| `--` | Ende der Optionen; danach auch Dateinamen mit führendem Minus |

Ohne `--modeldir`: `%ILI_DIR;http://models.interlis.ch/;%JAR_DIR`.
Dateien in lokalen Verzeichnissen werden nur inventarisiert; Header/Imports
bindet die gemeinsame Engine. Grenze: 10 000 Verzeichniseinträge/Quellen,
typisierter RESOURCE_LIMIT-Abbruch. Modellnamen sind eine explizite Erweiterung:
ein blosser Name wird im Inventar/Repository aufgelöst. Direkte HTTP-Dateiargumente
sind ausserhalb des Profils; HTTP wird über `--modeldir` und Modellnamen genutzt.

Die 32 übrigen Optionen des tatsächlich gelesenen Referenzhilfetexts werden vor
Host-IO ausdrücklich mit Exit 1 abgelehnt:

| Kategorie | Optionen |
|---|---|
| Generator/Transformation | `-o1`, `-o2`, `-oXSD`, `-oGML`, `-oILIGML2`, `-oFMT`, `-oIMD`, `-oIMD16`, `-oNLS`, `-oUML`, `-oIOM`, `--nlsxml`, `--lang`, `--trafoDiff`, `--trafoFactor`, `--trafoEpsg`, `--trafoImports`, `--trafoNewModel`, `--out`, `--with-predefined` |
| Repositoryadministration | `--check-repo-ilis`, `--clone-repos`, `--listModels`, `--listAllModels`, `--listModels2`, `--listAllModels2`, `--listData` |
| Weitere Hostoptionen | `--proxy`, `--proxyPort`, `--log`, `--xtflog`, `--translation` |

## Ergebnis- und Textprofil

Prüfaufrufe halten stdout leer. Banner, Diagnose und Trace stehen auf stderr.
Erfolg ergibt Exit 0, jede Ablehnung, Hoststörung, Nutzungsfehler und Abbruch
Exit 1. Signal-/Timeoutbeendigung ist kein fachlicher Erfolg.
Kanonische Diagnosen stammen aus der gemeinsamen Engine; Error/Warning/Info
werden gerendert. Quellbereiche und Meldungsidentitäten werden nicht durch
nachgebildete Referenztexte ersetzt.

| Beobachtung im externen Referenzprozess | Festgelegtes Ersatzverhalten |
|---|---|
| Versionsbanner nennt ili2c | Eigene interlis-java-Version, Referenzprofil separat |
| Zeitstempel und historische Diagnoseformulierungen | Kein Anspruch auf bytegleichen Text; stabile kanonische Diagnostik |
| Fehlende Quelldatei: Meldung, aber Exit 0 | OPERATIONAL_FAILURE, Exit 1 |
| Unbekannte Option mit gültiger Datei: Meldung, danach Exit 0 | Nutzungsfehler vor IO, Exit 1 |
| Fehlender `--modeldir`-Wert: interner Arrayfehler | Nutzungsfehler ohne Stacktrace, Exit 1 |
| Leerer headful Aufruf startet GUI; 20-Sekunden-Probe abgebrochen | Headless Nutzungsfehler, Exit 1 |
| Blosser Modellname: keine Datei, Exit 1 | Explizite Erweiterung: eindeutiger Modellname im Suchinventar zulässig |
| Historische Sprachakzeptanz | Die freigegebenen [Norm-/Kompatibilitätsentscheidungen](../g2/COMPILER_NORM_DECISIONS.md) gelten |

Der Prozessvergleich prüft Exit, getrennte Streams und Diagnosefreiheit von
internen Abstürzen. Er vergleicht keine irreführend normalisierten Volltexte.
Die obigen Unterschiede sind Teil der Profilqualifikation.

## Reproduktion und aktueller Nachweis

`./gradlew legacyCliCheck` baut zuerst die Distribution und startet tatsächlich
54 Ersatzprozesse sowie die zugehörigen Referenzprozesse. 44 Optionsschreibweisen
werden gegen die echte Referenzhilfe inventarisiert; die 32 ausdrücklich
abgelehnten Optionen werden jeweils separat ausgeführt. Der JAR-Einstieg wird
ebenfalls gestartet. Lokale Imports, Unicode-/Leerzeichenpfade, Quiet,
Warnungsfilter, Trace, Fehler und Namensauswahl sind enthalten.

Tatsächlicher lokaler Erfolg am 30. Oktober 2026: macOS arm64, Temurin 25.0.3.
Rohbericht: `build/reports/p3/legacy/cli-comparison.json`, Log:
`build/reports/p3/P3-1/current-jvm-cli-r2.log`. Der Bericht enthält echte Kommandos,
Exits, beide Streams, Laufzeiten, Runtime und Artefakt-/Quellhashes.
Negativcanaries verhindern ein PASS bei falschen Exits, verschmutzten Streams,
fehlenden Referenzprozessen oder erfolgreicher Unsupported-Option.
Linux x64 und Windows x64 werden zusätzlich im neuen GitHub-Workflow ausgeführt;
deren Ergebnis ist bis zum tatsächlichen Lauf offen.
