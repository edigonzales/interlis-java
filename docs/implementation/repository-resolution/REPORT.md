# Repositoryauflösung und gemeinsame Diagnosen

Stand: 01.11.2026. Gezielter Folgeauftrag zu WP-12, WP-26 und WP-30,
implementiert und im unten abgegrenzten Umfang verifiziert. Ausgangsstand:
sauberer Arbeitsbaum auf `76df3815973597280845a9bb575fa399e9773ccc`.
Commit und Push erfolgen auf anschliessenden ausdrücklichen Nutzerauftrag;
kein Release. Die früheren Phasenabnahmen werden nicht neu behauptet.

## Ergebnis

Die beiden Kommandos des gemeldeten Falls bestehen mit **Exit 0**. Ausgeführt
mit Java 25 über den neu gebauten und am bisherigen Ort entpackten Launcher:

```text
./interlis-compiler-cli/build/distributions/interlis-compiler-0.2.0-SNAPSHOT/bin/interlis-compiler check --repository https://models.interlis.ch --verbose /Users/stefan/tmp/SO_DSBJD_eBauSO_Rahmenmodell_20240807.ili
./interlis-compiler-cli/build/distributions/interlis-compiler-0.2.0-SNAPSHOT/bin/interlis-compiler check --repository https://models.geo.admin.ch --verbose /Users/stefan/tmp/SO_DSBJD_eBauSO_Rahmenmodell_20240807.ili
```

Beide wählen `GeometryCHLV95_V1` **2017-12-04**, dazu `Units` 2012-02-20 und
`CoordSys` 2026-02-25. Die Quellen können sich durch die originalen
HTTP-/HTTPS-Verknüpfungen unterscheiden; es gibt keine erfundene Gleichsetzung
dieser Identitäten. Suchweg und Auswahl erscheinen mit `--verbose` auf stderr.
[Originalstreams Mutter](evidence/mother.stderr),
[Originalstreams Direktrepository](evidence/direct.stderr),
[Kommandos, Exits, Input-/Artefaktpins und JSON-Auswahl](evidence/process-results.json).

Beschaffung wird getrennt von Sprachgültigkeit geprüft. Für die Nutzerdatei
ist auch das anschliessende Compilegate erfolgreich; die allgemeine Aussage
„Repositoryauflösung erfolgreich“ garantiert weiterhin kein gültiges Schema.

## Implementierter Umfang und Verträge

RES-001–009/011/012, DIAG-001–009, CLI-001–007 und LOG-002/003/005/008:

- Lazy Repositorybesuche nach `RepositoryVisitor`/`ModelFinder`: konfigurierte
  Repositories, Elternketten, Unterrepositories mit deren Elternketten, FIFO,
  Zyklusschutz und Suchende beim ersten passenden Treffer. Kein vollständiger
  Netzwerkscan vor einem Treffer, kein implizites Standardrepository.
- Vorgängerketten und `browseOnly` nach ili2c 5.6.9; rohe Versionsstrings und
  Indexreihenfolge bleiben erhalten. Exakte Versionen und Quellenfilter werden
  zuerst angewendet. Beschädigte Ketten folgen der ausdrücklich freigegebenen
  Referenzauswahl und erzeugen sichtbare Warnungen. `precursorVersion` war schon
  vorher richtig geschrieben und eingelesen; es fehlte seine Verwendung.
- Sichere Siteauswertung ohne DTD/Entities, kanonische Dateiidentitäten,
  Abbruch-/Arbeits-/Diagnose-/Textlimits. Leere standardkonforme Indizes sind als
  Mutterrepository zulässig. Remote-Sites führen keine lokalen Dateizugriffe ein.
- Typisierte Ablehnungen behalten Quellen, Kandidatenmetadaten, geordnete
  Schritte, Diagnosen und verfügbare Texte. Fehlende Imports verwenden echte
  UTF-16-Fundstellen; ursprüngliche Quellen behalten ihre tatsächliche Revision.
  Parserfehler in Downloads werden mit deren Texten sichtbar, ohne einen
  fehlgeschlagenen Batch im Benutzerworkspace zu installieren.
- Zentraler Katalog v2: bestehende RES-Codes mit optionalem ausführlichem Detail,
  neue 0008/0009 für Anpassungen und Identitäts-/Auswahlkonflikte. Gemeinsame
  DE-/EN-Darstellung und Diagnose-JSON. Der zusätzliche Reason-Formatter gilt
  ausschliesslich für Repositorycodes; semantische Reason-Werte bleiben erhalten.
- Qualifizierte Folgefehler fehlgeschlagener Imports tragen `causeId` und
  `dependsOn`. Nur die Textprojektion blendet sie aus; unabhängige Fehler und
  vollständige JSON-/API-Diagnosen bleiben erhalten.
- Globale Beschaffungswarnungen werden über `WorkspaceConfiguration` ins SourceSet
  übernommen. Das bestehende Compiler-Gate entscheidet über Warnungsablehnung;
  `analyze` erhält kein zusätzliches Gate. Hostablehnungen werden nicht als
  angebliche Compiler-Gate-Ergebnisse ausgegeben.
- v2-Locks frieren Index-/Sitemetadaten, gewählte Identitäten und Suchentscheidungen
  ein. Replay prüft die Metadaten und bindet die eingefrorenen Identitäten direkt;
  keine neue automatische Versionsentscheidung. Auch ein nachträglich erweiterter
  Workspace-Lock kann frisch/offline abgespielt werden. V1-Replay und bisherige
  Konstruktoren bleiben erhalten. Ausgabedateien dürfen keine verwendeten oder
  als nicht verfügbar beobachteten Repositorymetadaten überschreiben.
- `CliExecutionResult` liefert den Beschaffungslock und die Suchschritte.
  Diagnose-JSON enthält zusätzlich `resolution.steps/models`. `diagram --format
  json` bleibt ein Diagrammartefaktformat und führt kein `--json` ein.

Compiler und reiner Workspace-Resolver bleiben ohne Host-IO. Echte
Workspace-Mehrdeutigkeit, Originalbindungen, atomare Übernahme, Pins und
Header-/Digestprüfung bleiben erhalten. LSP und Browser erhalten keine neue
Remote-Konfiguration. Es entstehen keine Produktionsabhängigkeiten auf ili2c
oder zusätzliche Frameworks.

Die Verträge sind in den Spezifikationskapiteln 08/09/13–18 synchronisiert.
Originale Referenzquellen und Prüfsummen: [reference.json](reference.json).
Referenzcommit `bf16026e83f2377153b2e818a4f3838f0a423722`, Jar-SHA256
`37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e`.
Die drei zunächst untersuchten Upstreamdateien sind mit diesem Stand bytegleich.
Die grossen offiziellen Indexfixtures liegen verlustfrei komprimiert unter
`interlis-repository/src/test/resources/repository-parity/`, mit Original-URLs,
Bytezahlen und SHA-256 in `pins.json`.

## Tatsächlich ausgeführte Prüfungen

Toolchain: macOS arm64, Temurin 25.0.3 ohne Preview, Wrapper Gradle 9.8.1,
Node 24.22.0 für die portable Abnahme. Die lokale Distribution und das SDK-Archiv
wurden gebaut, aber nicht veröffentlicht.

Die aufbewahrten erfolgreichen JUnit-XMLs umfassen **158 Tests ohne Fehler oder
Skips**, keine pauschale Gesamtrepositoryabnahme:

| Modul / gezielter Vertrag | Tests |
|---|---:|
| model: Diagnoseformatter einschliesslich Hint-Argumenten und Reason-Isolation | 5 |
| compiler: Import-, Original- und Request-Diagnoseverträge | 57 |
| workspace: Konfiguration, Capture, Isolation und bestehende Resolvergrenzen | 23 |
| repository: bestehende und neue Host-/Index-/Lock-/Fixtureverträge | 54 |
| CLI: Repository-, Diagnose- und gemeinsame semantische Befehlsverträge | 19 |

[XMLs](evidence/junit/), [Inventar und Quellhashes](evidence/manifest.json).
Weitere bestehende CLI-Tests bestanden im anfänglichen breiteren Lauf; dessen
veraltete Katalogversionsassertion wurde gezielt korrigiert und nachgeprüft.
Dieser Lauf wird nicht nachträglich als vollständiger grüner Lauf ausgewiesen.

Ausgeführte Kommandogruppen, jeweils mit `JAVA_HOME` auf Temurin 25.0.3:

```sh
./gradlew :interlis-model:test --tests '*Diagnostic*' :interlis-workspace:test :interlis-repository:test :interlis-compiler-cli:test
./gradlew :interlis-compiler:test --tests '*Import*' --tests '*RequestDiagnosticsContractTest' --tests '*TranslationSelectionContractTest'
./gradlew :interlis-repository:test :interlis-compiler-cli:test --tests '*Repository*' --tests '*CliApplicationTest' repositoryReferenceCheck
./gradlew :interlis-model:test --tests '*DiagnosticMessageFormatterTest' :interlis-compiler-cli:installDist sdkArchiveCheck
./gradlew :interlis-wasm-teavm:repositoryDiagnosticCheck -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node
./gradlew compilerCliCheck sdkArchiveCheck -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node
./gradlew :interlis-compiler-cli:distZip
```

Die Logs bewahren die tatsächlichen kombinierten Läufe und gezielten Nachprüfungen:
[erste Bestandsprüfung](evidence/targeted-tests.log),
[Compiler-/Diagnosegegenproben](evidence/followup-tests.log),
[Host-/Referenz-/Portableabnahme](evidence/acceptance.log),
[abschliessender Formatter und SDK-Verbraucher](evidence/formatter-sdk-check.log),
[abschliessende Portableabnahme](evidence/portable-accepted.log),
[Prozess-/SDK-Prüfung](evidence/sdk-process-tests.log),
[Distribution](evidence/distribution.log).

Zusätzliche Prozessnachweise:

- **10 differentielle Gegenproben** gegen den separaten gepinnten ili2c-Prozess:
  vier Besuchsfolgen und sechs Versionierungsfälle einschliesslich der Anzahl
  von Anpassungshinweisen. [Rohdaten](evidence/reference-results.json).
- **55 bestehende CLI-Prozessfälle und sechs PTY-/Redirectfälle** mit getrennten
  Streams und Exits. [Unveränderter Bericht](evidence/cli-processes.json).
- **16 reale Fälle der endgültigen entpackten ZIP-Distribution**, darunter beide
  Screenshot-Kommandos, strukturierte Auswahl, alle vier Befehle bei fehlendem
  Import, DE/Verbose und abhängige/unabhängige Fehler.
  [Rohdaten und Artefaktpins](evidence/process-results.json).
- **Neun SDK-Kommandos** und ein aus dem erzeugten SDK-Archiv kompilierter und
  ausgeführter externer Verbraucher, ausschliesslich gegen dessen Jars.
  [SDK-Prozesse](evidence/sdk-processes.json),
  [Archivverbraucher](evidence/sdk-archive-consumer.json).
- **Zwei portable Gegenproben**, JS und WasmGC unter Node 24.22.0:
  gleiche vollständige Diagnosen und Ursachen-IDs sowie richtige UTF-16-Positionen
  bei fehlendem Import und unabhängigem Namensfehler.
  [Artefakt-/Quellbindung und Resultat](evidence/portable-result.json).

Erfolgreiche Bereiche wurden nach Änderungen nur gezielt nachgeprüft.
Die letzte Formatterkorrektur betrifft die Isolation der neuen Repositorytexte;
die zuvor geprüften Repositoryalgorithmen und Referenzgegenproben blieben
unverändert. SDK und portable Artefakte wurden danach erneut gebaut/geprüft.

## Fehlversuche, Prüfgrenzen und Abschluss

Frühe Regressionstests schlugen wegen der bewusst ersetzten automatischen
Hostmehrdeutigkeit, angepasster Hint-Darstellung und der Katalogversion fehl.
Die erwarteten Werte wurden nur für die freigegebene Vertragsänderung angepasst.
Die Tests deckten ausserdem Replay-/Dateiidentitäts- und Quellrevisionsfälle auf;
diese wurden korrigiert und erfolgreich nachgeprüft. Alte FAIL-Logs bleiben erhalten.

Die portable Probe verwendete zunächst versehentlich das systemweite Node
22.13.0. Ihre Assertions endeten, der WasmGC-Prozess jedoch nicht: ein Lauf
wurde bewusst beendet, ein zweiter erreichte das neue Tasktimeout; eine direkte
Node-22-Gegenprobe wurde ebenfalls beendet. **Keiner dieser Läufe zählt als PASS.**
[Erster Abbruch](evidence/portable-harness-hang.log),
[Timeout im kombinierten Lauf](evidence/final-contracts.log).
Mit dem vorgesehenen Node 24.22.0 bestehen Probe und tatsächlicher Prozessabschluss
mit Exit 0. Der neue Task hängt deshalb an `checkNodeRuntime` und besitzt ein
Timeout. Die verwendeten Node-/Artefaktwerte stehen im abschliessenden Report.

Nicht ausgeführt und für diesen Folgeauftrag nicht fällig: vollständiger
Korpus, Benchmarks, Browsermatrix, Web Image, Windows-/Linux-Gesamtqualifikation,
`releaseCheck`/`releaseCheckP3` und eine neue vollständige Phasenabnahme.
Node belegt ausdrücklich keinen Browserlauf. Die ursprüngliche separate
Office-Namenskollision aus dem Screenshot gehört nicht zu diesem Repositoryfix.

Keine offenen Pflichtprüfungen oder Blocker dieses abgegrenzten Auftrags.
Nächster fachlicher Schritt: normale Compilerwartung anhand neuer konkreter
Befunde; aus diesem Auftrag bleibt kein zusätzlicher Umsetzungsschritt offen.
