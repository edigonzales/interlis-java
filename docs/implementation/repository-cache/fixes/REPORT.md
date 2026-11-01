# Korrektur der zwei P2-Cachebefunde

Auftrag vom 01.11.2026, Ausgangscommit
`cf585ec1ea2270bafed4083a8ebfa2e68de05133`.
WP-26, RES-004/008–010/013; CLI-Fehlerklassifikation als betroffener Verbraucher.
Die ursprünglichen unabhängigen FAIL-Berichte unter
[reviews/cf585ec](../reviews/cf585ec/REVIEW.md) bleiben unverändert erhalten.

## Korrekturen

- **Bytegenaue Wiederverwendung:** Nur syntaktisch starke ETags werden als
  If-None-Match gesendet. Eine 304-Antwort muss denselben starken ETag als
  eindeutigen Feldwert bestätigen; sonst wird einmal unbedingt nachgeladen.
  Dies deckt auch den Übergang von starkem zu schwachem ETag, fehlende/abweichende
  Bestätigung und fehlende/korrupte Cachebytes ab. Schwache ETags und
  Last-Modified allein bewirken einen vollständigen GET. So gelten dieselben
  Originalbyte-Pins, Locks und CHANGED_SOURCE-Prüfungen wie bei frischen Reads.
  Last-Modified bleibt kompatible Metadaten; starke bestätigte ETags sparen
  weiterhin Inhaltsübertragungen. Kein neuer öffentlicher API-/Lockvertrag.
- **Optionale Sites:** RepositorySearch hält die erforderlichen Indexquellen
  separat fest. Für die Fehlerklassifikation bei erfolgloser Suche zählen nur
  deren Ladeausfälle bzw. echte Indexfehler. Optionale Site-Lade-/Parsefehler
  bleiben Warnungen. Die vollständige unavailable-Map bleibt unverändert für
  Lockprovenienz und Abschlussprüfung verfügbar. Ein fehlendes Modell ergibt
  NOT_FOUND/VERSION_MISMATCH, ein fehlender erforderlicher Index weiterhin
  LOAD_FAILURE/OFFLINE mit seiner Quelle. Die CLI erhält INPUT_UNRESOLVED statt
  OPERATIONAL_FAILURE, wenn lediglich das Modell fehlt.

Die Last-Modified-Erwartung des bestehenden HTTP-Cachetests wurde bewusst
angepasst: Kein If-Modified-Since für bytegenaue Beschaffung. Grundlage sind die
reproduzierten P2-Befunde und RFC 9110; es wird kein Fehler durch gelockerte
Testdiagnosen verborgen. Fachvertrag/API-Register, Abnahmen, Arbeitspaket,
Entscheidung, Matrix, CLI-Dokumentation und README sind synchronisiert.

## Eigene Verifikation

Java 25 ohne Preview, Repositorywrapper Gradle 9.8.1, macOS arm64.

1. `RepositoryCacheRegressionTest`: **RED – sieben Tests, sieben erwartete
   Fehlschläge**, tatsächlicher Gradle-Exit 1 vor der Produktkorrektur.
   [Log](evidence/red.log), [Original-XML](evidence/red-RepositoryCacheRegressionTest.xml).
2. Nach Korrektur: sieben Regressionstests erfolgreich. Anschliessend gezielte
   bestehende Host-/CLI-Verträge und neue CLI-Gegenprobe: **68 Tests in zehn
   Suites, null Failures/Errors/Skips**, Exit 0. Die letzte Präzisierung des
   deterministischen Server-Testtakts (Date und Last-Modified in derselben
   logischen Sekunde) wurde nur mit den sieben Regressionstests erneut geprüft.
3. `installDist` aktualisiert die lokale Distribution. Keine Korpus-, Benchmark-,
   Browser-, Plattform- oder Releasevollprüfung: Diese Nacharbeit betrifft
   JVM-Hostbeschaffung und die davon abhängige CLI-Klassifikation.

```sh
./gradlew :interlis-repository:test --tests '*RepositoryCacheRegressionTest' --console=plain
./gradlew :interlis-repository:test \
  --tests '*RepositoryCacheRegressionTest' --tests '*RepositoryCacheContractTest' \
  --tests '*HttpSourceLoaderTest' --tests '*RepositoryClosureContractTest' \
  --tests '*RepositorySearchContractTest' --tests '*ResolutionLockContractTest' \
  :interlis-compiler-cli:test --tests '*DefaultRepositoryCacheTest' \
  --tests '*RepositoryCliContractTest' --tests '*RepositoryDiagnosticsContractTest' \
  --tests '*SemanticRepositoryCommandsTest' :interlis-compiler-cli:installDist --console=plain
./gradlew :interlis-repository:test --tests '*RepositoryCacheRegressionTest' --console=plain
```

Die Taskoptionen stehen jeweils direkt nach ihrem Modultest; kein versehentlicher
vollständiger Modullauf. [Gezielter Lauf](evidence/targeted-green.log),
[finale Regressionen](evidence/final-regressions.log),
[Bilanz](evidence/green-summary.json). Die XMLs der finalen Regressionen ersetzen
nur diese Suite; die unveränderten anderen neun Suites werden wiederverwendet.
Die ursprünglichen FAIL-Nachweise und die frühere 101-Testbilanz behalten ihren
historischen Produktstand; sie werden nicht als frisch auf der Korrektur ausgegeben.

[Produktmanifest](evidence/product-manifest.json) bindet 288 Produktions-/Builddateien,
zehn Testquellen und sieben Distributions-JARs an den tatsächlichen geprüften
Arbeitsinhalt. Die Tests liefen vor Erzeugung des isolierten Review-Snapshots auf
dem Arbeitsbaum; der Snapshot muss bytegleich zu diesen Hashes sein.

## Unabhängige Nachprüfung

**PASS** auf dem isolierten Review-Snapshot
`4040da3c8f7fb359ba340dbff4a2985c4add2025` (Baum
`044684634a6403c89ac1505527044fd3ed92f18c`), Basis cf585ec.
OpenCode 2.0.28, Provider deepseek, Modell deepseek-flash; die zum Prüfzeitpunkt
geprüfte offizielle Modelltabelle ordnet diesen direkten API-Namen
DeepSeek V4.1 Flash 1017 zu. API-Host api.deepseek.com, kein Fallback.
Sitzung `ses_e67d81305ffe9UZQIixUVVbXt2`.

Alle fünf Kriterien FIX-01–05 sind erfüllt, beide früheren P2-Befunde ausdrücklich
als behoben bewertet; keine neuen Findings oder fehlenden Pflichtbelege.
Der Reviewer hat die 68 Tests anhand der Original-XMLs/Logs und der 288
Produktions-/Build-, zehn Test- und sieben JAR-Hashes unabhängig auditiert.
Zusätzlich eigene CLI-/HTTP-Proben für optionalen Site-Miss, erforderlichen
Indexfehler, malformed Site, schwache ETags, Last-Modified, Online-Lock/Pin,
exaktes Offline-Replay sowie starke Treffer und unbestätigte 304 ausgeführt.
Starker Wiederholungslauf: vier Requests, null Bodyantworten; Offline-Replay:
null HTTP. Keine Gradle-/Korpus-/Benchmark-/Browser-/Releasewiederholung durch
den Reviewer.

OpenCode-Prozess regulär beendet: tatsächlicher Exit 0, Guard passed/Exit 0,
Reviewworktree sauber und auf dem Kandidaten. Exportierte Assistantnachrichten
verwenden ausschliesslich deepseek/deepseek-flash. [Unveränderter Bericht](review/review.md),
[Urteil](review/verdict.json), [Session-/Prozess-/Hashbindung](review/run-manifest.json).
Originale Gegenproben und Ergebnisse liegen unter review/counterprobes;
vollständige Transkripte/Scratch unter build/review/repository-cache/fixes-r1.

Nach dem eingefrorenen Snapshot wurden ausschliesslich Abschlussnachweise und
Statusdokumentation ergänzt. Produkt- und Testdateien entsprechen weiterhin
bytegleich dem geprüften Manifest. Die früheren FAIL-Berichte bleiben erhalten.
Keine fälligen Prüfungen oder Blocker dieses Korrekturauftrags offen.
Keine weitere Implementierung für diese Nacharbeit erforderlich. Commit und Push
auf main nach origin folgen auf ausdrücklichen Nutzerauftrag.

Für den Review wurde ein isolierter Snapshotcommit erzeugt; der Benutzerbranch
und sein Index wurden dabei nicht verändert. Der anschliessende Commit und Push
auf main nach origin sind ausdrücklich vom Nutzer beauftragt. Kein Release.
