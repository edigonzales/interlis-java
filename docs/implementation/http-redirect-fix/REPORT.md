# HTTP-Redirectfehler – gezielte Korrektur

WP-26/30, RES-009, CLI-001–007; REDIR-01–05. Ausgangsbefund OBS-02 im
unveränderten Netzwerkpolicy-Review auf 53eb35743ee3e51afa809f74daea575c5a32ccf5.
Benutzerbranch zu Beginn 958f186, Netzwerkpolicy bereits im Arbeitsbaum; alle
vorhandenen Änderungen erhalten. Keine neue API, Diagnose oder Formatversion.

## Implementierung

Nur HttpSourceLoader ändert sein Produktionsverhalten: IllegalArgumentException
wird genau beim initialen URI-Parsen, bei HttpRequest.newBuilder und beim Auflösen
der Redirect-Location in SourceLoadException(IO_FAILURE) mit festem wertfreiem
Text überführt. Keine pauschale RuntimeException-Abbildung über den Loader.
Die ursprüngliche Ausnahme bleibt als interne Ursache; öffentliche Diagnosewerte
und Texte geben ungültige Locationwerte nicht aus. Gültige relative Redirects,
Schemeprüfung, Redirectlimit, Timeout, Abbruch und Cacheverhalten bleiben erhalten.
Fachvertrag/Abnahme in Kapitel08/13 präzisiert, keine API-Migration erforderlich.

## Gezielte Nachweise

Java25.0.3 einschliesslich Gradle-Launcher/Daemon, Gradle9.8.1, macOS arm64;
Node22.13.0 ausschliesslich als lokaler Prozesshelfer. Originaldaten unter evidence/.

- Vor Korrektur: fünf neue Loaderfälle ausgeführt, **vier Fehler RED** durch rohe
  IllegalArgumentException. Original-XML, Log, alter Quellenhash und unveränderter
  neuer Testquellenhash unter evidence/red/ und red-source.json.
- Nach Korrektur: **26 Repositoryfälle in vier Suites**, keine Fehler/Skips:
  HttpRedirectFailureTest, HttpSourceLoaderTest, HttpNetworkPolicyTest und
  RepositoryCacheRegressionTest. Sie prüfen URI-/Requestfehler, sichere Texte,
  fehlende Teilcachepublikation, relative Redirects und die berührten bestehenden
  Limits/Abbruch-/Timeout-/Cacheverträge. Die Komponententests liefen erfolgreich
  im Gruppenlauf green.log; dessen Gesamtexit war wegen einer CLI-Testannahme 1.
- CLI: **ein Testfall mit acht Kombinationen** (zwei fehlerhafte Ziele mal vier
  Befehle), keine Fehler/Skips nach Korrektur des Testorakels auf den bestehenden
  OPERATIONAL_FAILURE-Vertrag und strukturierte reason=IO_FAILURE-Diagnose.
  Keine Produktionsänderung am CLI oder Formatter. cli-green-r2.log Exit0.
- **Elf reale Distributionsprozesse** erfüllen ihre Erwartungen: acht ungültige
  Ziel-/Befehlkombinationen mit Exit1, Unsupported-Scheme/Exit1,
  Redirectlimit/Exit1 und erfolgreicher relativer Redirect/Exit0. Keine Geheimnisse
  oder rohe Exception im öffentlichen Output. Finale Kommandos/Exits und sieben
  JAR-Hashes unter evidence/processes.json; processes-final.log Exit0.

Kommandos (jeweils JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem):

```sh
./gradlew :interlis-repository:test --tests '*HttpRedirectFailureTest' --console=plain
./gradlew :interlis-repository:test --tests '*HttpRedirectFailureTest' --tests '*HttpSourceLoaderTest' --tests '*HttpNetworkPolicyTest' --tests '*RepositoryCacheRegressionTest' :interlis-compiler-cli:test --tests '*MalformedRepositoryRedirectTest' :interlis-compiler-cli:installDist --console=plain
./gradlew :interlis-compiler-cli:test --tests '*MalformedRepositoryRedirectTest' :interlis-compiler-cli:installDist --console=plain
node tools/verification/http-redirect-processes.cjs
```

27 unterschiedliche JUnit-Fälle in fünf Suites; keine doppelten Fallzählungen.
Tatsächliche Zwischenfehler und Gesamtexits bleiben unter executions.json
nachvollziehbar: zunächst falsche CLI-Klassifikation im neuen Test, danach eine
Textannahme bei der lokalisierten Diagrammausgabe. Zwei vorzeitig gestartete
Prozessversuche verwendeten noch die alte Distribution, weil der fehlgeschlagene
Testvorgänger installDist verhinderte; diese gelten ausdrücklich nicht als neue
Produktnachweise. Erst der finale Prozesslauf bindet die korrigierten JARs.
Keine Goldens, XFAILs, Originalpins oder bestehenden Diagnoseerwartungen geändert.

Kein breiterer Lauf fällig: keine Änderung an Compilersemantik, Bridge, Browser,
Buildlogik, Netzwerkkonfiguration oder API. Frühere unveränderte Nachweise werden
nicht wiederholt. Kein Korpus-/Benchmark-/Browser-/Releasevollauf, Push oder Release.

## Unabhängiger Review

Verwendet wurde ein sauberer separater Snapshotcheckout, direkter DeepSeek-Provider
api.deepseek.com und OpenCode2.0.28/deepseek-flash mit Python-freiem Guard.
**PASS für REDIR-01–05, keine offenen Muss-Findings** auf Kandidat
7fa83ef1bfdec7f8dacc2925c5c27dd4cfa891f3, Tree
4a2a597b38dc4d35ac3818f1a0bca6295fe16d46. Regulärer ProzessExit0, Guard
passed/Exit0, sauberer Checkout vor und nach Review. Alle 29 Assistantnachrichten
im bereinigten Sessionexport verwenden ausschliesslich deepseek/deepseek-flash.

Der Reviewer bestätigt den Produktionsdiff in genau einer Datei mit drei engen
Fehlergrenzen, die unveränderten übrigen Produktionsdateien, 944 gebundene
Produkt-/Test-/Vertragsdateien, 23 Originalevidenzdateien und sieben JARs ohne
Hashabweichung. RED/GREEN und tatsächliche Zwischenfehler sind unabhängig
auditiert; 27 JUnit-Fälle und elf finale Distributionsprozesse nachvollzogen.
Die eigene neue RedirectCounterProbe mit abweichender ungültiger Location
und gültigem relativem Redirect besteht (javac/java jeweils Exit0).

[Unveränderter Reviewbericht](review/review.md), [Urteil](review/verdict.json),
[Session-/Prozess-/Hashbindung](review/run-manifest.json),
[ursprüngliche Gegenprobe](review/probes/RedirectCounterProbe.java) und
[tatsächliche Reviewerkommandos/-ausgaben](review/probe-processes.json).
Nicht blockierende Beobachtungen betreffen ausschliesslich die bereits erklärte
lokalisierte Diagrammdarstellung und die transparent verworfenen alten
Distributionsversuche. Keine weitere Produktkorrektur nach dem Kandidaten.

## Abschluss

OBS-02 ist behoben. Keine offenen Pflichtprüfungen, Muss-Findings oder Blocker
dieses Auftrags; kein weiterer fachlicher Schritt für REDIR-01–05 nötig.
Historische Netzwerkpolicy-Reports/Reviewbelege bleiben unverändert. Nach dem
Kandidaten werden nur Abschlussstatus und zusätzliche Nachweise ergänzt.
Aktueller Produkt-/Testinhalt und Distribution wurden abschliessend gegen das
Kandidatenmanifest geprüft. Benutzerbranch/Index unverändert; Implementierung
im Arbeitsbaum und im isolierten Review-Snapshotcommit, kein Push oder Release.
