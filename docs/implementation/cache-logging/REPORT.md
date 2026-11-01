# Einheitliche Cachemeldungen pro Datei

Stand 01.11.2026, Basis `59141fa88a642d2c8201970f978fd7befe288f26`.
WP-26/30; RES-013, DIAG-002/003/006, LOG-002/003/005/008 und CLI-001–007.
Umfang und Abnahme: [CL-01–08](EXECUTION_PLAN.md).

## Implementierter Stand

Quellenbezogene `RepositoryCacheObservation`-Werte ergänzen die bisherigen
Enum-Callbacks. `HostRepositories.createObserved` und
`RepositoryClosureService.observed` sind additive Einstiege; bisherige
Konstruktoren einschliesslich Access mit drei Parametern bleiben verfügbar.
HTTP sowie Offlinepfade mit Metadaten und mit Digest/Lock melden ihre tatsächlich
angefragte Quelle. Observerexceptions beeinflussen keine Ladeentscheidung.
Vor Veröffentlichung werden Benutzerinformationen, Query und Fragment entfernt.
Die optionale Anzeigeadresse ist keine Quellen-/Cacheidentität; Header fehlen.

`RepositoryCacheRenderer` verwendet den vorhandenen ConsoleDiagnosticRenderer,
zentralen DE-/EN-Formatter, Sanitierung und die bestehende Farbpolicy.
Katalog v3 ergänzt die rein präsentierte INFO-Meldung ILI-RES-0010 mit event/source.
Die kurzlebigen Darstellungswerte werden weder im SourceSet noch in Snapshots,
Compilerdiagnosen, Gate, Warnungspolitik oder Diagnose-JSON veröffentlicht.

Nur `--verbose` ohne Diagnose-JSON aktiviert die Ausgabe auf stderr. Jede
Beobachtung wird ausgegeben, auch ein wiederholter Zugriff bei Abschlussprüfung.
Die vorhandenen RunEvent.CacheLookup-Ereignisse bleiben erhalten. Download
behauptet keine Speicherung; CACHE_MISS verspricht keinen Retry und behauptet
keinen fehlenden Körper, da auch unsichere 304-Antworten diesen Grund liefern.
WRITE_UNAVAILABLE bleibt eine einmalige Warnung, ohne doppelte Infomeldung.
Die Cachekorrekturen von 59141fa (starke ETags, optionale Site) sind unverändert.

Beispiel der Darstellung bei Deutsch:

```text
Information [ILI-RES-0010] Cachezugriff für Quelle 'http://127.0.0.1:PORT/Basis.ili': Inhalt aus Cache verwendet (HTTP 304).
```

## Tatsächliche Verifikation

macOS arm64, Temurin 25.0.3 ohne Preview, Wrapper Gradle 9.8.1 und Node24.22.0.
**69 gezielte JUnit-Fälle in 14 Suites, null Fehler/Errors/Skips**. Die finale
Legacy-Konstruktorergänzung wurde nur mit RepositoryCacheObservationTest erneut
geprüft; dessen XML ersetzt die frühere Version. Andere erfolgreiche XMLs sind
unverändert wiederverwendet. [Manifest](evidence/manifest.json),
[XMLs](evidence/junit/), [gezielter Lauf](evidence/targeted.log),
[abschliessende CLI-Prüfung](evidence/final-cli.log),
[Legacy-Einstiege](evidence/legacy-constructors.log).

Ausgeführte Taskgruppen:

```sh
./gradlew :interlis-repository:test --tests '*RepositoryCacheObservationTest' --tests '*RepositoryCacheRegressionTest' --tests '*RepositoryCacheContractTest' --tests '*HttpSourceLoaderTest' :interlis-model:test --tests '*Diagnostic*' --tests '*VersionManifestTest' :interlis-compiler-cli:test --tests '*RepositoryCacheRendererTest' --tests '*VerboseRepositoryCacheTest' --tests '*DefaultRepositoryCacheTest' --tests '*CliApplicationTest' --tests '*ColorOutputContractTest' --tests '*VerboseDiagnosticsContractTest' :interlis-compiler-cli:installDist --console plain
./gradlew :interlis-compiler-cli:test --tests '*RepositoryCacheRendererTest' --tests '*VerboseRepositoryCacheTest' --tests '*DefaultRepositoryCacheTest' --tests '*ColorOutputContractTest' --console plain
./gradlew :interlis-repository:test --tests '*RepositoryCacheObservationTest' --console plain
./gradlew :interlis-compiler-cli:installDist :interlis-compiler-cli:test --tests '*CliApplicationTest' --tests '*VerboseDiagnosticsContractTest' :interlis-wasm-teavm:repositoryDiagnosticCheck -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node --console plain
```

Die Tests decken Quellenbereinigung, fehlende/korrupte Bytes, unbestätigte und
zweite 304, Observerfehler, alte Enum-/Access-/Closure-Einstiege, no-store,
Offline mit/ohne Lock, optionale Site/erforderlichen Index, Standard/Verbose,
DE/EN, Farbe Auto/Always/Never, Diagnose-JSON und unveränderte Exits/Gate ab.
Die sieben jüngsten Cachekorrekturtests sind eingeschlossen.

**29 reale Distributionsprozesse** mit lokalem HTTP-Server und isoliertem
Home/Cache: erste Downloads, warme 304-Treffer aller vier Befehle, kompakte
Ausgabe, DE, identisches Diagnose-JSON mit/ohne Verbose, beide Offlinepfade,
optionale Site, ungültige/zweite 304, no-cache/no-store, Farbe und Katalogversion.
[Unveränderte Kommandos, Streams, Exits und HTTP-Status](evidence/processes.json),
[Prozesslog](evidence/processes-final.log).
Reproduktion: Node24.22.0 mit JAVA_HOME auf Java25 aus dem Root:
`node tools/verification/cache-log-processes.cjs` nach installDist.

**JS- und WasmGC-Gegenprobe**: Wireversion 1.0/Fähigkeiten bleiben erhalten;
Compilerdiagnosen tragen keine Hostcacheinformationen und behalten ihre
Importursachen. [Build-/Tasklog](evidence/final-build-portable.log),
[finale Probe](evidence/portable-final.log),
[Quell-/Artefaktbindung](evidence/portable.json).
Die direkte finale Probe verwendet dieselben bytegleichen gebauten Artefakte;
sie aktualisiert die Bindung nach der ausschliesslichen Legacy-Testergänzung.
Der JVM-Versionsvertrag bestätigt den neuen Diagnosekatalog v3; die Bridge
führt dafür kein zusätzliches Wirefeld ein. Node ist kein Browsernachweis.

Ein erster Testcompile scheiterte an einem mehrdeutigen statischen TOKEN-Import
im neuen Test; die expliziten Imports wurden korrigiert. Keine fachlichen
Erwartungen zum Caching wurden gelockert. Die anschliessenden gezielten Läufe
sind erfolgreich. Es wurden keine Korpus-/Benchmark-/Browser-/Plattform- oder
Releasevollprüfungen wiederholt; sie sind für diesen Präsentationsauftrag nicht fällig.

## Reviewstatus

Unabhängiger Review **PASS** für Kandidat
`27a3bfa42e55d63b165ec5d78ec1df896290dbe4`; CL-01–08 abgeschlossen,
keine offenen Muss-Findings. [Unveränderter Bericht](review/review.md),
[Urteil](review/verdict.json), [äussere Prozess-/Modellbindung](review/run-manifest.json).
OpenCode 2.0.28 im separaten sauberen Checkout, tatsächlicher ProzessExit 0,
Guard passed/Exit 0. Der finale bereinigte Sessionexport belegt ausschliesslich
deepseek/deepseek-flash; keine Delegation oder Modellwechsel. Direkter API-Host
und Versionszuordnung entsprechen dem unten beschriebenen Preflight.

Der Reviewer hat 622 Produkt-/Builddateien, 14 Testquellen und sieben JARs
gegen die Originalmanifeste sowie alle 69 JUnit-Fälle/14 Suites und 29
Distributionsprozesse auditiert. Eigene Gegenproben: 22 reale CLI-Fälle,
SanitizeProbe mit 26 und LoaderProbe mit 14 Prüfungen, erfolgreich.
[Unveränderte Gegenproben und Resultate](review/probes/),
[tatsächliche Probeprozesse](review/probe-processes.json).
Ein rein historischer Hinweis auf Katalog v2 wurde als nicht blockierende
redaktionelle Beobachtung eingeordnet; die aktuelle normative Version ist v3.

Die Bereitschaftsprobe über OpenCode2.0.28/DeepSeek direkt ist mit tatsächlichem
Exit0 und Guard passed beendet. Sessionmodell deepseek/deepseek-flash;
effektiver DeepSeek-Endpunkt api.deepseek.com ohne abweichenden Konfigurations-
override. Die aktuelle offizielle [Modelltabelle](https://api-docs.deepseek.com/quick_start/pricing/)
ordnet deepseek-flash DeepSeek-V4.1-Flash-1017 zu.
[Providerdaten](evidence/preflight/provider.json),
[Prozess](evidence/preflight/process-result.json),
[Guard](evidence/preflight/guard.json),
[bereinigter Sessionexport](evidence/preflight/session.json).

Das Distributionsarchiv wurde abschliessend mit `:interlis-compiler-cli:distZip`
erfolgreich aktualisiert und im bisherigen Distributionsverzeichnis entpackt;
alle enthaltenen Bibliotheken sind bytegleich zum geprüften installDist.
[Buildlog](evidence/distribution-final.log), [Archivbindung](evidence/distribution.json).
Nach dem Kandidaten ändern sich ausschliesslich Abschlussnachweise und Status;
keine weitere Produktkorrektur war erforderlich. Keine offenen Pflichtprüfungen
oder Blocker dieses Auftrags. Kein Push oder Release im Umfang dieses Auftrags.
