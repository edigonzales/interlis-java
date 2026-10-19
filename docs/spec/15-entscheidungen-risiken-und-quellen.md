# Entscheidungen, offene Nachweise und Quellen

## 1. Status und Evidenzgrenze

Die Spezifikation hat Stand 18. Oktober 2026. Sie verbindet Anforderungen aus dem Gespräch mit Repositoryanalyse und einem tatsächlich ausgeführten kleinen Portabilitätsversuch. Die neue Compilerimplementierung, ihre hier beschriebenen Java-APIs, ein Gradle-Produktbuild und die vollständigen Freigabetests sind zukünftige Arbeit.

Ausgeführt wurde der Syntax-/Java-Kompatibilitätsspike auf JVM, TeaVM-WasmGC in Node und TeaVM-JavaScript in Node. Die Rohresultate liegen unter [results.json](../evidence/wasm-spike/results.json), die Anleitung unter [Spike-README](../evidence/wasm-spike/README.md), das vollständige ursprüngliche Quellpaket unter [Spike-ZIP](../evidence/interlis-java-wasm-spike.zip). Seine Maven-Konfiguration ist historischer Versuchsaufbau, kein Buildentscheid für das neue Produkt.

Nicht dadurch bewiesen sind vollständige INTERLIS 2.3/2.4-Semantik, Modellauflösung, feine Inkrementalität, Browserausführung, Web Image, volle Klassenbibliothek, reale grosse Modellkorpora oder DB-Kompatibilität. Kapitel 10 und 14 geben dafür konkrete nächste Abnahmen.

## 2. Architekturentscheidungen

| ADR | Entscheidung | Begründung und Konsequenz | Wiederaufnahme nur bei |
|---|---|---|---|
| ADR-001 | Monorepo `interlis-java`, Namespace `guru.interlis` | gemeinsamer Modellvertrag und gemeinsame Freigaben; bekannte Toolnamen können bleiben | expliziter Produkt-/Namensentscheidung |
| ADR-002 | Java 25 ohne Preview | moderne stabile Sprachmittel, im Syntaxspike konkret portiert | nachgewiesener Zielplattformblockade mit Variantenvergleich |
| ADR-003 | Gradle/Groovy/Wrapper | Nutzerpräferenz, Modulgraph, Parser/Tests/WASM in einem Build | konkretem unlösbarem Integrationsproblem; kein spontaner Mavenwechsel |
| ADR-004 | ANTLR 4 als interne Syntaxbasis | vorhandene Grammatik und Werkzeuge; eigene öffentliche Syntax schützt übrige Architektur | gemessenem Recovery-/Performanceproblem mit überprüftem Alternativparser |
| ADR-005 | SyntaxSnapshot, AnalysisSnapshot und CompiledSchema | Editorzustand und validierte Verbraucherbasis gleichzeitig darstellbar | Änderung muss alle drei Nutzungskategorien erhalten |
| ADR-006 | Immutable veröffentlichte Graphen, Builder intern | deterministische Wiederverwendung und paralleles Lesen | belegtem Problem mit gleichwertigem Zustandsvertrag |
| ADR-007 | CompiledSchema-Interface im model, interne finale Engineimplementierung | azyklischer Modulgraph, unkomplizierte Konstruktion über Gate | neuem öffentlichen Vertragsbedarf |
| ADR-008 | Hostbeschaffung ausserhalb des Compilers | Offline-/Browser-/Testfähigkeit und reproduzierbare Inputs | keine Ausnahme ohne explizite neue Hostgrenze |
| ADR-009 | Diagnosen als Daten, Betriebsereignisse separat | Legacydarstellung, LSP und JSON aus derselben Ursache | Schemaevolution mit Migration |
| ADR-010 | TeaVM zuerst weiterführen, Web Image real vergleichen | erster Weg praktisch belegt, zweiter ernsthaft untersucht | Ergebnis der P1-Vergleichsmatrix |
| ADR-011 | ganze geänderte Datei zuerst neu parsen, Semantikcache graduell verfeinern | einfacher korrekter Einstieg mit erhaltenem Optimierungsspielraum | gemessenen Engpässen oder Editoranforderung |
| ADR-012 | neutrale Diagrammprojektion und vorhandene Renderer | gemeinsame Fachsemantik, bestehende Arbeit nutzbar | konkretem Rendering-/Interaktionsbedarf |
| ADR-013 | Transfer-/Validation-/SchemaPlan | Verbraucher erhalten explizite kompakte Laufzeitverträge | fachlicher neuer Verbraucheranforderung |
| ADR-014 | DB-Profile und gespeicherte Zuordnungen massgeblich | Drop-in-Datenbankkompatibilität | expliziter Migration mit entsprechender Freigabe |
| ADR-015 | alte Java-Binärkompatibilität **zurückgestellt** | kein Legacy-Adapter im aktuellen P3-Umfang; saubere neue API bleibt vorrangig | konkretem Bestandsanwendung-Auftrag mit unveränderbarem API-Bedarf |
| ADR-016 | P3 ohne Modellgeneratoren; Quellformatierung als API/CLI | IlisMeta07/16 und Generatorformate sind nicht Produktziel; Formatierung ist ein eigenständiger, testbarer Verbraucher | neuem Generator-/Exportauftrag mit Format-/Capabilitymatrix |
| ADR-017 | P3-Diagrammintegration TypeScript-only; C++/C-ABI/iox nicht P3 | bestehende Browser-/Diagrammschemas sind der konkrete Integrationspunkt; native iox-/C-ABI-Verbraucher gehören zu P4 oder einem eigenen Auftrag | gepinntem C++/C-ABI-Verbraucherauftrag |
| ADR-018 | Performance-Korpus `https://geo.so.ch/models` | realer, öffentlich verfügbarer INTERLIS-Modellbestand; Index und Modelle werden per SHA-256 gepinnt | nicht reproduzierbarem oder lizenzrechtlich ungeeignetem Korpus |

ADR-001 ist eine Namensfestlegung dieses Entwurfs; eine Namens-/Domainregistrierung oder Repositoryanlage wurde nicht vorgenommen. `ilinext` bleibt die benannte Alternative, wird aber nicht parallel in Java-Packages verwendet.

## 3. Wesentliche Risiken mit konkreten Gegenmassnahmen

| Risiko | Folge | Früher Nachweis / Gegenmassnahme |
|---|---|---|
| Parser akzeptiert viel, Semantik fehlt | scheinbar grüner, fachlich unbrauchbarer Compiler | Coveragegate und semantische Goldens ab P1 |
| Grammatik 2.4 verschluckt 2.3-Bezeichner/Versionen | unbeabsichtigte Sprachabweichung | Version-/Lexer-/EOF-Matrix, keine alleinige Headerprüfung |
| Recovery erzeugt plausiblen aber falschen Baum | falsche Navigation oder falsche Freigabe | error/missing-Knoten, Ursachenbezug, Gate; Dirty-Editor-Fälle |
| Symbolidentität wird mit FQN gleichgesetzt | Rename-/Diagramm-/Cachefehler | snapshotgebundene Refs, konservative Wiedererkennung, Duplikattests |
| Cache invalidiert nur vorhandene Imports | fehlendes Modell bleibt trotz neuer Quelle unbekannt | negative Abhängigkeiten und Resolverrevision |
| Immutable Modell verliert Präzision/Reihenfolge | spätere Transfer-/DB-Inkompatibilität | genaue Literale, effectiveMemberTests, Planprojektionen früh |
| WASM unterstützt einzelne JDK-APIs anders | späte Architekturänderungen | gleiche Semantikfälle auf Zielruntimes, Dependencybudget |
| Browserworker kann Abbruch nicht empfangen | blockierte IDE bei grossem Lauf | reale Abbruchstrategie mit Yield/SharedFlag/Workerersatz prüfen |
| Legacylogs werden nur kosmetisch angenähert | bestehende Prozesse interpretieren Fehler falsch | Profilierte Streams/Exitcodes/Kataloge, keine Text-Rückwärtsanalyse |
| Datenbank sieht ähnlich aus, Metadaten weichen ab | spätere Updates/Exporte scheitern | alter/neuer Stack in beiden Richtungen auf realen DB-Profilen |
| Aufbau enthält zu viele Frameworks/Module | langsamer Start und schwierige Portierung | keine leeren Zukunftsmodule, kleine Gradle-Konventionen |
| Gegenreferenz enthält eigene Fehler | falsch festgeschriebene Sprachregeln | Normprüfung plus begründete Differenz, keine blind übernommene Baseline |

## 4. Gepinnte Repositorybefunde

Die folgenden Commitstände stammen aus der vorausgegangenen Analyse dieses Gesprächs. Sie dienen als reproduzierbare Ausgangsreferenzen; ein Agent kann bewusst einen neueren Stand wählen, muss dann Pin und Vergleich aktualisieren. `main`/`master`-Links in Fachkapiteln erleichtern das Lesen, diese Tabelle fixiert die untersuchte Version.

| Quelle | Untersuchte Revision | Bedeutung |
|---|---|---|
| [claeis/ili2c](https://github.com/claeis/ili2c/tree/cbccff35fc98fdfa370e42e3bb39bb4edd70a658) | `cbccff35fc98fdfa370e42e3bb39bb4edd70a658` | Grammatik, Metamodell, CLI, semantische Regeln |
| [claeis/iox-ili](https://github.com/claeis/iox-ili/tree/2807992c45bb10d18f44ea4a882fc19f6dd1e4cb) | `2807992c45bb10d18f44ea4a882fc19f6dd1e4cb` | tatsächliche Validator-/Transferverbraucher |
| [claeis/ilivalidator](https://github.com/claeis/ilivalidator/tree/cee7ee7994062128f635f182592fbdfed62f9519) | `cee7ee7994062128f635f182592fbdfed62f9519` | Toolorchestrierung und Bedienverträge |
| [claeis/ili2db](https://github.com/claeis/ili2db/tree/ed9e7c20c9148b02d42e1846e2c5c555341aa257) | `ed9e7c20c9148b02d42e1846e2c5c555341aa257` | Namens-/Vererbungsabbildung und Metadatenschema |
| [edigonzales/ilic-fork](https://github.com/edigonzales/ilic-fork/tree/c601f1c9d88eb68f67991fd05fc4653884d104c7) | `c601f1c9d88eb68f67991fd05fc4653884d104c7` | Session/Snapshots/Diagnostik, CI und Downstreamgrenzen |
| [geostandards-ch/interlis-antlr4](https://github.com/geostandards-ch/interlis-antlr4/tree/f8f97238c2540a49915f8bb97ee99bbf434b74da) | `f8f97238c2540a49915f8bb97ee99bbf434b74da` | Parsergrundlage und tatsächliche Spikegrammatik |
| [geostandards-ch/ili2ogc](https://github.com/geostandards-ch/ili2ogc/tree/dcc23fac1f879e8ad3340ed1b208072b1a245564) | `dcc23fac1f879e8ad3340ed1b208072b1a245564` | zusätzliche Lexerlogik, Modellverarbeitung, Fallmaterial |
| [edigonzales/interlis-lsp](https://github.com/edigonzales/interlis-lsp/tree/65864b3cd74e7cd8a2ff8e066331e717cebc517e) | `65864b3cd74e7cd8a2ff8e066331e717cebc517e` | vorhandene Java-LSP-/Diagrammarbeit, Dirty-Parsing-Erfahrung |
| [edigonzales/interlis-language-tools](https://github.com/edigonzales/interlis-language-tools/tree/f27801bfebf0fdac82a19e016f4c347ae2b6f8fb) | `f27801bfebf0fdac82a19e016f4c347ae2b6f8fb` | TS-Sprachdienst-/Browser-/Diagramm-Adapter |
| [edigonzales/iox-cpp](https://github.com/edigonzales/iox-cpp/tree/c0ea225cdf950c146420c3d65fe5f4a69a479397) | `c0ea225cdf950c146420c3d65fe5f4a69a479397` | kompakte Transferbeschreibungen, kanonische/übersetzte Namen |
| [edigonzales/duckdb-interlis](https://github.com/edigonzales/duckdb-interlis/tree/5345d58cab34ad5e49994b055868826fca33783e) | `5345d58cab34ad5e49994b055868826fca33783e` | Downstreamverbraucher und explizite Versionskopplung |
| [GeoW.Interlis.Tools](https://github.com/geowerkstatt/GeoW.Interlis.Tools/tree/02f89943b08030b898c56da6eb9b9cb7bdc361f7) | `02f89943b08030b898c56da6eb9b9cb7bdc361f7` | .NET-Compiler für INTERLIS 2.4, Teilregeln und präzise Referenzen |
| [GeoWerkstatt VS-Code-Erweiterung](https://github.com/geowerkstatt/vsc_interlis2_extension/tree/64946f218d4f60d9347b3e94c871d8749de360b0) | `64946f218d4f60d9347b3e94c871d8749de360b0` | Sprachdienste, GraphDocument, Cytoscape |
| [GeoWerkstatt Modellbrowser](https://github.com/geowerkstatt/interlis-model-browser/tree/f4a90689341d6acf3aa3b8c6ed2605587535044b) | `f4a90689341d6acf3aa3b8c6ed2605587535044b` | Repositorymetadaten-/Suchanwendung |

Ein Quellcommit allein identifiziert noch kein ausführbares Legacy-CLI-Artefakt mit sämtlichen Abhängigkeiten. Die Kompatibilitätsprofile müssen zusätzlich die tatsächlich verwendeten JARs/Container, Hashes, Buildoptionen und Versionausgaben fixieren.

## 5. Normen und technische Primärquellen

| Thema | Quelle | Verwendung |
|---|---|---|
| INTERLIS 2.3/2.4 | [Offizielle Dokumentation](https://www.interlis.ch/dokumentation/interlis-2) | Sprachfassungen und normative Regeln |
| Metamodelle | [IlisMeta07/IlisMeta16](https://www.interlis.ch/modelle/metamodell) | semantische Vollständigkeit; kein P3-Exportziel |
| Externe Compilersuite | [Codeberg-Suite](https://codeberg.org/edigonzales/interlis-compiler-testsuite), [ilic-CI](https://github.com/edigonzales/ilic-fork/blob/c601f1c9d88eb68f67991fd05fc4653884d104c7/.github/workflows/ci.yml) | gepinnte Akzeptanzfälle |
| Suitebewertung | [ilic-Conformance](https://github.com/edigonzales/ilic-fork/blob/c601f1c9d88eb68f67991fd05fc4653884d104c7/docs/conformance.md) | 610 Gesamtfälle inkl. 1.0; dokumentierter Stand 599 PASS / 11 XFAIL, hier nicht neu getestet |
| ANTLR 4 | [Release 4.13.2](https://github.com/antlr/antlr4/releases/tag/4.13.2), [DefaultErrorStrategy](https://www.antlr.org/api/Java/org/antlr/v4/runtime/DefaultErrorStrategy.html) | Toolpin und Recoverymechanismen |
| Java 25 | [Sprachänderungen](https://docs.oracle.com/en/java/javase/25/language/java-language-changes-summary.html) | stabile Sprachmittel, keine Previewpflicht |
| Gradle | [Kompatibilität](https://docs.gradle.org/current/userguide/compatibility.html), [Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html), [ANTLR](https://docs.gradle.org/current/userguide/antlr_plugin.html) | Buildgrenzen und P0-Nachweise |
| TeaVM | [Gradle](https://teavm.org/docs/tooling/gradle.html), [0.16](https://www.teavm.org/docs/release-notes/0.16.0.html), [0.13](https://teavm.org/docs/release-notes/0.13.0.html), [Klassenbibliothek](https://teavm.org/docs/runtime/java-classes.html) | aktuelle Werkzeuganbindung und Portabilitätsgrenzen |
| TeaVM / WASI | [0.14-Releasehinweise](https://www.teavm.org/docs/release-notes/0.14.0.html) | Entfernung älterer Wasm-/WASI-Wege berücksichtigen |
| Graal Web Image | [Handbuch](https://www.graalvm.org/latest/reference-manual/web-image/), [API](https://www.graalvm.org/latest/reference-manual/web-image/api-guide/), [javac-Demo](https://github.com/graalvm/graalvm-demos/blob/master/web-image/javac/README.md) | experimenteller Java-zu-Wasm-Weg und Adapter |
| LSP | [LSP-3.17-Spezifikation](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/) | Versions-/Positions-/Nachrichtenverträge |
| Roslyn | [Syntaxanalyse](https://learn.microsoft.com/en-us/dotnet/csharp/roslyn-sdk/get-started/syntax-analysis) | konzeptionelle Referenz für verlustfreie, fehlertolerante Syntax |
| OTel | [Log-Datenmodell](https://opentelemetry.io/docs/specs/otel/logs/data-model/) | optionaler Host-Telemetrieadapter |
| Xtext | [Runtimekonzepte](https://eclipse.dev/Xtext/documentation/303_runtime_concepts.html) | bewertete Alternative, keine gewählte Abhängigkeit |

Die externe Compilersuite war im untersuchten CI-Stand auf `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3` gepinnt. Ihre Akzeptanztests sind nicht mit einer allgemeinen XTF-Datenvalidierungssuite gleichzusetzen. P0 liest den konkret beschafften Umfang und erzeugt die eigene versionsgefilterte Fallliste.

## 6. Welche Ideen aus welchem Projekt stammen

Aus ilic werden vor allem Session-/Snapshot-/Diagnostikgrenzen übernommen. Die dortige Dateiparse-Wiederverwendung ist nicht automatisch eine feingranulare semantische Incremental Engine. Aus dem Java-LSP stammen wichtige Editorfälle und Renderer. Aus interlis-language-tools stammt ein möglicher Browser-/Diagramm-Unterbau mit explizit anderer Adapteroberfläche. Aus GeoWerkstatt stammen starke Referenzideen für segmentgenaue Namensverweise und ein neutrales Diagrammgraphmodell. Aus iox-cpp stammt ein anschaulicher Beleg für kompakte Verbraucherdeskriptoren.

Diese Herkunft rechtfertigt keine automatische Gleichsetzung der Implementierungen. Ein C++-ABI, ein TypeScript-DTO und ein Java-Metamodell bleiben unterschiedliche Verträge. Jede Wiederverwendung braucht einen konkreten Adapter und seine Paritätsprüfung.

## 7. Entscheidungsprotokoll während der Implementierung

Eine neue Entscheidung ergänzt ID, Problem, betrachtete Varianten, Entscheidung, betroffene Anforderungen, Abnahmetests und Migrationsfolgen. Reine Klassenumbenennungen benötigen keinen langen Aufsatz, aber ein konsistentes Register. Eine Änderung von Sprachsemantik, Snapshotgültigkeit, DB-Abbildung, Build-Tool oder Plattformzusage benötigt eine ausdrücklich erkennbare Entscheidung.

Gepinnte Fakten werden bei einem Update erneut gelesen. Ein alter erfolgreicher Spike beweist keine neue Bibliotheksversion. Ebenso darf ein neuer Toolchainfehler nicht rückwirkend als Beweis ausgegeben werden, dass eine zuvor getestete Konfiguration nie funktioniert habe.
