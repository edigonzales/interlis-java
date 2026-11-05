# Testauswahl, Nachweise und Retests nach Review

## 1. Grundsatz

**Teste eine konkrete offene Frage, nicht pauschal jede vorhandene Suite.** Die vollständige fachliche Abdeckung bleibt verbindlich, ihre unnötige Wiederholung nicht. Ein neuer Commit, eine neue Phase oder eine Reviewrunde ist für sich kein neuer Produktinput.

Drei verschiedene Identitäten erfassen:

- **Sourcekandidat:** Core-/Extension-SHA, Trees, Dirtyzustand des tatsächlichen Laufs.
- **Relevanter Produktinput:** Quellen/Descriptors, wirkliche Dependencies, Generator-/Compileoptionen, Ressourcen und Fixtures für den jeweiligen Vertrag.
- **Ausführungsartefakt:** konkrete JAR-/Bundle-/Runtime-/JS-/Wasm-Hashes und Launch-/Testkonfiguration.

Ein alter Test kann weiterhin gelten, wenn die für seine Aussage relevanten Inputs/Bytes unverändert sind. Er bleibt ein **alter tatsächlicher Lauf**, nicht ein neuer Test am aktuellen SHA. Originalreport nicht umschreiben. Eine neue Kandidatenzuordnung ist ein separater abgeleiteter Nachweis.

## 2. Pflichtumfang einmal über den finalen Stand

B braucht echte Ausführung:

- normale JVM-Regressionsmenge aller zehn Projekte, mit vollständigen ursprünglichen logischen Invocations und neuen Tests;
- aktuelle Compilercontracts mit ihren benötigten echten Produzentenreports;
- modulare Graph-/Boundary-/White-Box-Proben;
- kleine reale CLI-/LSP-/SDK-Fälle und echter Extension-Host mit neuer Startlogik;
- JS-/WasmGC- und Web-Image-Consumerfälle nach neuem Artefaktweg und nach relevanter Modularisierung;
- harte Negativtests der Artefakt-/Report-/Snapshot-/Releaseprüfer;
- zwei saubere JVM-Artefakterzeugungen zum Reproduzierbarkeitsvergleich, nicht zwei gesamte Testkampagnen.

Die Gesamtmenge kann aus B5-Batchläufen und späteren selektiven Nachläufen bestehen, wenn ihre Inputs noch gültig sind. Keine pauschale dritte Vollrunde in B7 und keine vierte beim unabhängigen Review. Abweichende Reportsuitezahlen durch JUnit-Modulcontainer sind erlaubt, verlorene Parameterfälle/Assertions nicht.

Die volle 347-Closure-Performance-, sämtliche Browser-/Safari- und die komplette P3-Plattformmatrix bleiben im Releaseprofil verpflichtend. Für diesen Migrationsauftrag nur ausführen, wenn ein tatsächlicher geänderter Vertrag oder Befund die breite Prüfung erforderlich macht. Ein bestehender grüner anderer Stand erfüllt keine neuen Releasebytes. Die Spez schreibt keine P3-Veröffentlichung vor.

## 3. Kompakte Testmatrix

Die folgenden IDs sind Fallgruppen, keine vorab behaupteten bestehenden Tasknamen. Verwende vorhandene passende Tests und ergänze nur fehlende Gegenproben.

| ID | Phase | Prüfung | Erwartete Aussage |
|---|---|---|---|
| T01 | B0 | Zwei echte Module + JDK25/Jenesis-Pin | reale modulare Kompilierung, nicht dekorativer Descriptor |
| T02 | B0/B5 | package-private White-Box mit Patch | Produktklasse ist named, Assertion und Parameterfälle laufen |
| T03 | B0/B5 | Fehlassertion und leere Auswahl | tatsächlicher Failure/Empty wird nicht PASS |
| T04 | B0/B5 | Fremdes Internal-Package | Compiler-/Laufzeitnegativprobe scheitert am richtigen Zugriff |
| T05 | B1 | normalisierter Resultindex | fehlende/duplizierte/fremde Outputs abgelehnt |
| T06 | B1 | Quelle/Fixture/Config ändern, löschen, wiederholen | echte Schritte invalidiert; unveränderte Schritte wiederverwendbar |
| T07 | B1/B4 | normaler JVMbuild ohne Gradle/Node | keine versteckte Consumer-/Toolchainpflicht |
| T08 | B1 | Contractproducer/-consumer | konkrete neue Reports mit identischer Input-/Runbindung |
| T09 | B1 | Lockreentry/Clean-Sentinels | keine Deadlocks oder Quellenlöschung |
| T10 | B2 | echtes immutable Snapshotexport | vollständige erwartete Runtime und auflösbare POMs |
| T11 | B2 | JAR+Manifestzeile manipulieren, Originallink bleibt | höherer Producerbezug verhindert Scheinlegitimation |
| T12 | B2 | Traversal/Symlink/Extra-/Missing-Datei | frühes sicheres Fail |
| T13 | B2 | zwei Snapshots gleicher GAV, warmer Resolver | richtige neue Bytes, kein stale SNAPSHOT |
| T14 | B2 | eigener globaler Maven-/Gradlealtbestand | kann defekten Snapshot nicht reparieren |
| T15 | B2/B7 | echte JS-/WasmGC-Erzeugung und kleine Laufzeitfälle | neue Produzentbytes werden tatsächlich konsumiert |
| T16 | B2/B7 | echter Web-Image-Build und Nodeprobe | gleiche Übergabe, kein alter gemeinsamer Gradlebuild |
| T17 | B3 | stageCore und reiner Import | normaler Weg nutzt Jenesis, Import startet keinen Builder |
| T18 | B3/B6 | Editorinventar/Renderer/Runtime defekt | unverändert fail closed, keine falsche Version/Fallback |
| T19 | B3/B6 | echter Extension-Host | Server, Diagnose und Navigation/Diagramm funktionieren |
| T20 | B4 | JVM-Gradleentfernung/Taskinventar | keine verlorene Funktion, keine aktive JVM-Parallelspur |
| T21 | B5 | zehn Deskriptoren, Splits, GAV/POM/Services | korrekter modularer und Maven-/Classpathvertrag |
| T22 | B5 | Testpatch kollidiert mit Produktklasse/-resource | Build lehnt unzulässige Schattenklasse ab |
| T23 | B5/B6 | benannter SDKconsumer ohne Testfreigaben | API/Readability und echte Produktmodule |
| T24 | B6 | CLI modular, Unicode/Whitespace/stdin/JSON/Exit | bisheriges Nutzerverhalten erhalten |
| T25 | B6 | LSP modular initialize bis shutdown/restart | JSON-RPC/stdout-Vertrag erhalten |
| T26 | B6/B7 | echtes Runtimeimage ohne fremdes Java | gebündelte JVM/Services/HTTP/TLS funktionsfähig |
| T27 | B7 | Releasevalidator: fehlend/stale/fremd/Skip | kein grüner Gate durch Pfadmigration/Reportprojektion |
| T28 | B7 | zwei saubere JVM-Lieferungen + gezielte Byteänderung | echte Reproduzierbarkeit und harter Unterschiedsfehler |
| T29 | B7 | Prodlieferung/Launchargs auf Test-/Toolleaks | kein Testpatch, JUnit, Verification oder Compilerplugin |
| T30 | B7/R | Kandidatenpaar+Bundlebindung | Review und tatsächliche Lieferinputs gehören zusammen |

## 4. Veränderung → kleinster ausreichender Retest

| Änderung nach Review | Notwendig | Normalerweise nicht notwendig |
|---|---|---|
| Nur Text/Links/Status ohne ausführbare Verträge | Dokument-/Link-/Diffprüfung; neues Kandidatenpaar korrekt beziehen | JVM-/Wasm-/GUI-/Performance-Volltests |
| Reportparser/Selectionvalidator | betroffene positive/negative Fixtures; kleiner echter Reportproducer/consumer | gesamte Compilersemantik oder Backendneubau |
| Snapshot-/Manifestguard | Tamper-/Traversal-/Origin-/Cachefälle; ein passender realer Consumerimport | jedes Frontend und alle Compilerfälle |
| Export-POM oder Resolverkonfiguration | Auflösung aus leerem/warmem Kontext, Closure-/Versions-/Hashvergleich; betroffene Consumer | Tests völlig unabhängiger Module |
| `module-info` exports/requires/opens | betroffener Graph + echter benannter Consumer + passende Grenz-/Reflectionprobe; abhängige Kompilierung | unveränderte komplette historische Korpora |
| Gemeinsamer Produktionsdescriptor mit breiter Wirkung | alle tatsächlich betroffenen Nachfahren, deren relevante normale Tests und echte Starts | rein metadatenfremde Validator-/Dokumenttests erneut |
| Testpatchargs/-selektion | White-Box-Sentinel, Fehl-/Empty-/Parameterfälle und betroffene Tests/Contracts | Neubau unveränderter Produkt-JARs |
| Launcher/Windowsquoting/LSPlaunch | echte betroffene CLI-/LSP-/Extensionprozesse auf geeigneten Hosts | alle ANTLR-/Parser-Unit-Tests |
| Nur Extension-Staging | Unit-/Integritäts-/Importfälle plus kleiner echter Stage; Hostsmoke bei veränderter Lieferung | volle Wasm- oder Compilerperformance |
| Engine-/JDK-/Dependencywechsel | zunächst ausser Scope; falls ausdrücklich neu beauftragt, breite relevante Baseline | kein stilles Durchwinken als Kleinstfix |
| Backendcompileclasspath/-pluginargs | Codegen und kleine echte JS/Wasm/Web-Image-Läufe des betroffenen Backends | normale JVM-Tests ohne geänderte Produktinputs |
| JAR-/Runtimepayload wirklich verändert | betroffene reale Verbraucher an genau den neuen Bytes | bloss alte Reports auf neuen Hash umetikettieren |

Ein gemeinsamer Tool-/Runtimewechsel kann den kleinsten ausreichenden Umfang deutlich vergrössern. Es geht nicht um minimale Testzahl um jeden Preis, sondern um die kleinste Menge, die das konkrete Risiko tatsächlich abdeckt.

## 5. Wiederverwendungsprotokoll

Pro Fix ein kurzer Eintrag:

```text
Änderung/Finding:
Betroffene Dateien/Verträge:
Geänderte relevante Produkt-/Testinputs:
Erneut ausgeführte Prüfungen + warum:
Wiederverwendete Originalbelege + Input-/Bytegleichheitsbegründung:
Nicht fällige / blockierte Prüfungen:
Neue Core-/Extension-Kandidatenidentität:
```

Keine vollständigen Umgebungsdumps. Versionen, command argv, cwd, eng ausgewählte sichere Variablen, relevante Pins, Exitcode, Signal, Timeout, ursprüngliche Rohlogpfade und Hashes erfassen. Credentials und private Hostinformationen nicht unnötig speichern.

Wenn ein früheres Reportformat durch neue Technik ersetzt wird, Originaldateien beibehalten und separate nachvollziehbare Projektion anlegen. Keine neu generierten JUnit-XMLs allein aus Erfolgszählungen. Eine Projektorprüfung muss auch Fehler-/Skip-/Parameterfälle abdecken.

## 6. Plattformen und Laufzeitumgebungen

Echte macOS-/Windows-/Linuxläufe, Docker oder Emulation unterscheiden. Ein buildbarer Workflow ist kein ausgeführter Plattformtest. Ein WebKitlauf ist kein echter Safari-Nachweis. Ein auf dem Classpath getestetes Produkt wird nicht nachträglich als JPMS-getestet bezeichnet.

B hat verbindliche lokale bzw. erreichbare Backend-/Consumerintegrationsgates. Fehlende fällige Backendwerkzeuge blockieren diese Gates. Die komplette Releaseplattformmatrix kann dagegen als nicht ausgeführt bestehen bleiben, sofern dafür kein Release-PASS behauptet wird und der neue Releasevalidator die Lücke zuverlässig blockiert. Im Abschluss `implementationStatus`, `consumerIntegrationStatus`, `platformCoverage`, `releaseReadiness` getrennt berichten.

Keine neue kostenpflichtige Infrastruktur, keine globalen Browser-/JDK-Installationen, keine Aktivierung persönlicher Safari-/VS-Code-Einstellungen ohne Auftrag. Vorhandene Tools und isolierte Testprofile verwenden. Lokale Downloads nur gepinnt/erforderlich, niemals remote install-script auf Verdacht ausführen.

## 7. Prozesse und lange Werkzeuge

Innere Codegen-/Testlimits und äusserer Recorder werden passend begrenzt. Ein 60-Sekunden-Standardrecorder darf keinen ausdrücklich längeren Build abschiessen. Timeout bleibt Fehler; keine spätere grüne Zeile überschreibt den ursprünglichen Exit. Nur eigene bekannte Prozesse/Container beenden, keine globale Säuberung.

Keine blockierenden Funktionstests gleichzeitig mit schweren Compilierungen/Browservorbereitungen starten. Ein Timeout nicht wiederholen, bis zufällig Grün entsteht: zuerst Ursache klären. Ein fehlgeschlagener Versuch wird erhalten und mit der Korrektur verknüpft.

## 8. Unabhängigkeit ohne Volltestverdopplung

Der Reviewer bekommt originalgehashte Belege und aktuelle Artefakte. Er entscheidet selbst über die Aussagekraft und führt eigene kleine Gegenproben aus. Er muss nicht jede breite ursprüngliche Suite erneut ausführen, nur weil ein anderer Agent sie zuvor ausgeführt hat.

Mindestens sinnvoll: ein selbst gewählter negativer Modulzugriff, ein Snapshot-/Inventarmanipulationsfall und eine echte schmale CLI-/LSP-/Consumerprobe auf verfügbaren passenden Werkzeugen. Ein konkret fehlender wichtiger Nachweis kann zusätzliche Tests rechtfertigen. Autorisierung/Installationsrestriktionen gelten für den Reviewer genauso.
