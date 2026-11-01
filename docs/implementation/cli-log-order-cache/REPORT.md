# Chronologische CLI-Meldungen und identity-Cache

WP-26/30; RES-009/013, CLI-004/006, DIAG-002/003/006, LOG-004/005/008.
Ausgangsstand: `573eed9fe49bb8869db04ad7d20fb7728d2ede36`, sauberer Arbeitsbaum.
Umgesetzt und im folgenden Umfang verifiziert; kein Commit, Push oder Release.

## Implementierung und Verträge

- ConsoleDiagnosticRenderer beginnt jede Hauptmeldung mit dem sprachunabhängigen
  INFO/WARNING/ERROR/HINT, dann Code, optionale revisionstreue Position und Text.
  DE-/EN-Texte, UTF-16, Farben, Sanitierung und eingerückte Ergänzungen bleiben erhalten.
- RepositoryResolutionObservation mit SearchStep und VerificationStarted sowie
  additive resolve-Überladung mit aufrufgebundenem synchronem Observer. LOOKUP
  erscheint vor Indexzugriff, SELECTED nach Kandidatenwahl. Keine historische
  Wiedergabe von Lockschritten und kein nachträglicher Textdump. Werfende Observer
  ändern das Ergebnis nicht; CancellationToken und Workspace-Revision bleiben massgeblich.
- RepositoryResolutionRenderer verwendet die zentralen Katalogdefinitionen und
  ConsoleDiagnosticRenderer. Katalog v4 ergänzt ILI-RES-0011–0013 als positionslose
  INFO-Werte nur zur Hostpräsentation. JSON-/Lockprovenienz, Compilerdiagnosen,
  Warnungspolitik und Gate enthalten keine zusätzlichen Betriebsinfos.
- Tatsächliche Online-Abschlussprüfungen werden einmal vor ihren Zugriffen
  angekündigt; kein Ereignis offline oder bei bereits vollständiger lokaler Closure.
  Prüfung auf Quellenänderung und negative Provenienz bleiben erhalten.
- Jeder HTTP-GET fordert identity an, einschliesslich Redirects und Retries.
  Vary wird über alle Feldzeilen/Kommalisten ohne Grossschreibung ausgewertet;
  ausschliesslich Accept-Encoding ist zusätzlich wiederverwendbar. Andere Felder,
  Stern und no-store bleiben ausgeschlossen. Unerwartete Content-Codings auf
  200/304 ergeben vor Dekodierung/Publikation wertfreien IO_FAILURE.
  Keine Dekomprimierung, neue Cacheversion oder Lockmigration. Starke identische
  ETags, Digests, Zielbindung, Pins, Locks und Retryanzahl bleiben erhalten.

Fachverträge Kapitel08 §16, 09 und 18, API-Register16 sowie Abnahme13,
Arbeitspakete14 und Matrix17 synchronisiert. README und CLI_PROFILE sowie
betroffene Test-/Prozesshelfererwartungen auf die ausdrücklich gewählten festen
Präfixe und Katalog v4 angepasst. Keine semantischen Goldens/XFAILs geändert.

## Tatsächliche Nachweise

Java25.0.3 (auch Gradle-Launcher/Daemon), Gradle9.8.1, Node24.22.0,
macOS15.8.1/aarch64. Details: [Toolchain](evidence/toolchain.json).
Alle Kommandos mit tatsächlichen Exits: [executions.json](evidence/executions.json).

| Prüfung | Ergebnis | Originalbeleg |
|---|---|---|
| Neue Regressionen vor Produktionskorrektur | 7 Fälle, 6 Fehler, Exit1 | [RED-Log](evidence/red.log), [XML](evidence/red/) |
| Dieselben drei neuen Testsuites nach Korrektur | 7 Fälle, keine Fehler/Skips, Exit0 | [Initiales GREEN](evidence/green-initial.log) |
| Gezielte JVM-Abnahme | 126 Fälle / 24 Suites, keine Fehler/Skips, Exit0 | [Log](evidence/targeted.log), [XML](evidence/targeted/), [Bilanz](evidence/test-summary.json) |
| JS/WasmGC unter Node24 | zwei tatsächliche Proben PASS, Exit0 | [Build-/Lauflog](evidence/distribution-portable.log), [Rohresultat](evidence/portable.json) |
| Kontrollierte reale CLI-Prozesse | 20/20 Erwartungen erfüllt, alle tatsächlichen Exits erfasst | [Log](evidence/processes.log), [Resultate/HTTP/JAR-Hashes](evidence/processes.json) |
| Öffentliches Screenshot-Modell | vier Prozesse Exit0; beide warmen Läufe ohne vollständigen Download | [Originalausgaben und Eingabehash](evidence/public.json) |

Die JVM-Abnahme umfasst 11 Modellfälle/3 Suites, 54 Repositoryfälle/8 Suites
und 61 CLI-Fälle/13 Suites. Sie prüft alle Präfixe mit/ohne Position, DE/EN,
Farbe, veraltete Revisionen und Frames; aktuelle Ereignis-/Zugriffsreihenfolge,
fehlende Modelle, Compilerfehler, Abbruch, Observerfehler und parallele Aufrufe.
Die Cachefälle sichern Varianten/Headernormalisierung, 200/304, Instanzwechsel,
Offline, no-store, unerwartete Codings und Redirect-/Retryheader. Vorhandene
ETag-, Digest-, Pin-/Lock- und Quellenänderungsregressionen wurden gezielt ausgeführt.

Die 20 Prozesse prüfen Kalt-/Warmlauf, alle vier Befehle, Deutsch, kompakte
Ausgabe, JSON-Parität, Offline mit/ohne Lock, keine ausstehende Arbeit, fehlendes
Modell, Compilerfehler, --no-cache/no-store, unerwartete Komprimierung, Farben
und Katalogversion. Der kontrollierte Kaltlauf überträgt zwei Inhalte, danach
folgen zwei 304; warme Aufrufe haben vier 304 und keine Inhaltsübertragung.
Offline-Lockschritte werden nicht als aktuelle Suche angezeigt. Der Prozesshelfer
liegt unter tools/verification/cli-log-order-cache-processes.cjs.

Das unveränderte Screenshot-Modell wurde über die neu entpackte Distribution
mit https://models.geo.admin.ch/ und http://models.interlis.ch/ geprüft, jeweils
mit separatem Cache unter build/cli-log-order-cache/public-cache. Beide initialen
Läufe haben sechs Downloads und sechs 304, beide warmen Läufe null Downloads
und zwölf 304. GeometryCHLV95_V1 2017-12-04, Units 2012-02-20 und CoordSys
2026-02-25 werden wie zuvor ausgewählt. Der bestehende Benutzer-Cache wurde
durch diese Gegenproben nicht verändert.

## Auslieferungsstand und Grenzen

installDist und distZip wurden erstellt. Die generierte entpackte Distribution
unter interlis-compiler-cli/build/distributions/interlis-compiler-0.2.0-SNAPSHOT
wurde aus installDist aktualisiert und für alle vier öffentlichen Prozesse
verwendet. [Manifest](evidence/manifest.json) bindet Produktions-/Testquellen,
Prozesshelfer, ZIP und JARs; der Basiscommit allein bezeichnet nicht den neuen
uncommitteten Produktstand. Nach den Produktnachweisen wurden ausschliesslich
Dokumentation und Evidenz ergänzt.

Die beiden Emscripten-Tasks und Wasm-Disassemblierung waren im vorhandenen
TeaVM-Build SKIPPED; JS- und WasmGC-Erzeugung und die beiden Node-Proben wurden
tatsächlich ausgeführt. Keine übersprungenen JUnit-Tests. Dies ist kein
Browsernachweis. Vorbestehende Gradle-Deprecationhinweise stehen im Originallog.

Keine für diesen Auftrag fälligen Prüfungen oder Blocker offen. Kein vollständiger
Korpus-, Benchmark-, Browser-, Plattform- oder Releasevollauf; diese ändern sich
durch den abgegrenzten Host-/Präsentationsauftrag nicht und wurden gemäss
Teststrategie nicht pauschal wiederholt. Kein unabhängiger externer Review für
diesen Folgeauftrag beauftragt oder als durchgeführt behauptet.
Nächster fachlicher Schritt dieses Auftrags: keiner; Änderungen stehen zur
Durchsicht im Arbeitsbaum bereit.
