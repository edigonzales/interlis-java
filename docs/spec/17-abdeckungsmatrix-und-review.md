# Abdeckungsmatrix, Anforderungsindex und Dokumentreview

## 1. Zweck und Prüfstatus

Dieses Kapitel verbindet die ursprünglichen Ziele mit den konkreten Fachverträgen, Arbeitspaketen und Abnahmen. Es enthält den vollständigen Index der **285 eindeutig nummerierten Anforderungen** des Pakets. Die erläuternden Klassen-, Methoden-, Fachregel- und Testverträge in den jeweiligen Kapiteln gehören zusätzlich zur Spezifikation; ihre Verbindlichkeit hängt nicht davon ab, ob jeder einzelne Satz eine eigene ID besitzt.

Die hier dokumentierte Prüfung betrifft das **Spezifikationspaket**. Sie ist kein Nachweis, dass der neue Compiler oder die späteren Werkzeuge bereits implementiert sind. Die tatsächlich ausgeführte kleine Syntax-/WASM-Probe ist separat unter `evidence/wasm-spike/` dokumentiert. Ihr Umfang wird in Kapitel 10 ausdrücklich von den noch auszuführenden Compiler-, Browser- und Web-Image-Abnahmen abgegrenzt.

Die Kapitel 00 bis 18 sind die 19 Fach- und Steuerungsdokumente. Zusammen mit README und STARTPROMPT ergeben sie **21 Markdown-Hauptdokumente**. Hinzu kommen die Arbeitsregeln im Root-`AGENTS.md` und in `docs/spec/AGENTS.md`, insgesamt also **23 Markdown-Dateien zu Spezifikation und Agentensteuerung**. Das Gesamtpaket ergänzt reproduzierbare Referenzevidenz. Die zusätzlich angebotene zusammengeführte Markdown-Ausgabe enthält denselben Inhalt einschliesslich der beiden Arbeitsregeldateien und dient dem Lesen beziehungsweise der Verarbeitung als einzelnes Dokument.

## 2. Abdeckung der Nutzeranforderungen

| Ausgangsanforderung | Konkretisierung im Entwurf | Massgebliche Kapitel | Nachweis beziehungsweise Meilenstein |
|---|---|---|---|
| Java-Stack von Grund auf erneuern | Neue Modul- und Datenverträge; vorhandene Implementierungen als Referenzen für Verhalten und Fachsemantik | [01](01-zielbild-und-anforderungen.md), [02](02-architektur-und-modulgrenzen.md), [15](15-entscheidungen-risiken-und-quellen.md) | Architekturgrenzen ab G0/G1; Funktionsumfang bis G5 |
| Fähigkeiten von ili2c, iox-ili, ilivalidator und ili2db erhalten | Gemeinsames semantisches Modell; getrennte Compiler-, Transfer-, Validierungs- und DB-Dienste | [05](05-metamodell-und-typensystem.md), [12](12-transfer-validierung-und-datenbanken.md) | Compiler G2/G3, Transfer und Validierung G4, Datenbanken G5 |
| Mit dem Compiler beginnen und das Gesamtziel behalten | Vollständige P0/P1-Strecke, anschliessend fachliche P2-Familien; spätere Verbraucheranforderungen bleiben verbindlich | [00](00-auftrag-und-agentenregeln.md), [14](14-implementierungsplan-und-arbeitspakete.md), [STARTPROMPT](STARTPROMPT.md) | WP-00 bis WP-17 mit belegtem Status; keine leeren Zukunftsmodule |
| Benutzer sollen wenig Veränderung bemerken | Benannte CLI-/Diagnose-/Exportprofile, gemessene Exitcodes und Streams, nachvollziehbare Abweichungen | [09](09-diagnostik-logging-und-telemetrie.md), [13](13-tests-kompatibilitaet-und-qualitaet.md), [18](18-cli-sdk-und-kompatibilitaetsadapter.md) | Reale Prozess- und Exportvergleiche in G3 |
| ili2db-Tabellen und Schemen müssen kompatibel bleiben | Historische Namen, Mappingstrategien und gespeicherte Metadaten sind explizite Verträge; vorhandene Zuordnungen sind massgeblich | [12](12-transfer-validierung-und-datenbanken.md), [13](13-tests-kompatibilitaet-und-qualitaet.md) | Alt→Neu und Neu→Alt mit echten Operationen auf derselben Datenbank in G5 |
| Elaboriertes strukturiertes Logging | Unveränderliche Diagnosen, stabile Codes und Ursachen; davon getrennte RunEvents, explizite Eltern-/Kindkontexte, JSON-/Legacyrenderer | [09](09-diagnostik-logging-und-telemetrie.md), [16](16-api-register-und-vertragstests.md) | Ursachen-, Reihenfolge-, Observer- und Lifecycle-Vertragstests ab G1 |
| Nur INTERLIS 2.3 und 2.4 | Gemeinsame LanguageVersion; versionsspezifische Syntax, Builtins, Semantik und Capabilitymatrix | [04](04-quelltext-syntax-und-parser.md), [05](05-metamodell-und-typensystem.md), [06](06-kompilierung-und-semantik.md) | Positive und negative Versionsfälle; vollständige Featurematrix in G2 |
| Aktuelles ANTLR und geostandards-Grammatik verwenden | Gepinnter Ausgangsstand, Generator-/Runtimeabgleich, kontrollierte Grammatikentwicklung, eigener Syntaxbaum | [03](03-build-und-repository.md), [04](04-quelltext-syntax-und-parser.md), [15](15-entscheidungen-risiken-und-quellen.md) | Reproduzierbare Generierung, EOF-/Recovery-/Versionsfälle und kein Generator im Runtimeclasspath |
| Best Practices und moderne Java-Konstrukte | Java 25 ohne Preview; Records, sealed Varianten, exhaustive Behandlung, defensive Collections und gezielte Fehlerzustände | [02](02-architektur-und-modulgrenzen.md), [05](05-metamodell-und-typensystem.md), [16](16-api-register-und-vertragstests.md) | Tatsächlich kompilierende API-Beispiele, Ownership- und Plattformverträge |
| Schnitte und Toolaufbau neu wählen dürfen | Reiner Kern, portabler Workspace, explizite Hostbeschaffung, dünne CLI-/LSP-/Browseradapter | [02](02-architektur-und-modulgrenzen.md), [07](07-workspace-inkrementalitaet-und-lsp.md), [08](08-modellaufloesung-und-repositories.md) | Azyklischer Modulgraph, kein verborgenes IO, dieselbe fachliche Engine |
| Java-Metamodell als TransferDescription-Nachfolge | Typisierter SemanticGraph und SemanticQueries; CompiledSchema als geprüfte Sicht auf ausgewählte Wurzeln und Abhängigkeiten | [05](05-metamodell-und-typensystem.md), [06](06-kompilierung-und-semantik.md), [16](16-api-register-und-vertragstests.md) | Fachliche Modellgoldens, Quellherkunft, Auswahl- und Snapshotreferenztests |
| Mehr als ein Alles-oder-nichts-Batchcompiler | SyntaxSnapshot, AnalysisSnapshot und CompiledSchema mit unterschiedlichen Nutzungsverträgen | [04](04-quelltext-syntax-und-parser.md), [06](06-kompilierung-und-semantik.md) | Navigation auf defektem Editorstand; striktes Gate bleibt korrekt geschlossen |
| Teilweise parsen und kompilieren, inkrementell arbeiten | Dateibezogener Syntaxcache, abhängige semantische Wiederverwendung, negative Abhängigkeiten, überprüfte Fingerprints | [07](07-workspace-inkrementalitaet-und-lsp.md), [08](08-modellaufloesung-und-repositories.md) | Jeder geprüfte Editierzwischenstand entspricht einer frischen Analyse |
| IDE und LSP unterstützen | Revisionsgeprüfte Updates, Capture/Accept-Vertrag, Hintergrundanalyse, Sprachabfragen und bedingte Veröffentlichung | [07](07-workspace-inkrementalitaet-und-lsp.md) | Echte LSP-Nachrichten, veraltete Ergebnisse, Rename-/Completion-/Positionsfälle |
| Modelle visualisieren | UI-neutrales DiagramDocument aus derselben Semantik, partielle Zustände und revisionsgebundene SourceAnchors | [11](11-visualisierung-und-projektionen.md) | Graphgoldens und Quellnavigation ab G1; Rendererintegration in G3 |
| Vorhandene Mermaid-, GLSP-/Sprotty- und PlantUML-Arbeit berücksichtigen | Getrennte Projektion und Renderer; bestehende Java-/Clientkomponenten als Integrationskandidaten | [11](11-visualisierung-und-projektionen.md), [15](15-entscheidungen-risiken-und-quellen.md) | Derselbe Diagrammgraph wird ohne zweite Namens-/Typsemantik verwendet |
| TeaVM und GraalVM für WASM ernsthaft ausprobieren | Laufender TeaVM-Ausgangspunkt, Gradle-Portierung, identische Semantik und konkreter Web-Image-Untersuchungsauftrag | [10](10-webassembly-und-portabilitaet.md), [14](14-implementierungsplan-und-arbeitspakete.md) | JVM-/Nodevergleich, Chrome/Firefox/echtes Safari im gewählten Produktionspfad; realer Web-Image-Versuch mit Ergebnis |
| WASM soll Architektur früh beeinflussen | Keine Hostabhängigkeiten im Kern; explizite Daten-/Session-/Handle-/Abbruchverträge und exakte Wire-Zahlen | [02](02-architektur-und-modulgrenzen.md), [09](09-diagnostik-logging-und-telemetrie.md), [10](10-webassembly-und-portabilitaet.md) | Gleichheits-, Unicode-, Zahlen-, Lebensdauer- und Worker-Neustarttests ab G1 |
| Vorhandene Compilertestsuite nutzen | Gepinnte Suite, bewusste Auswahl des 2.3/2.4-Umfangs, getrennte PASS/XFAIL/ERROR-Behandlung, zusätzliche semantische Goldens | [13](13-tests-kompatibilitaet-und-qualitaet.md), [15](15-entscheidungen-risiken-und-quellen.md) | Referenzfälle in WP-01; vollständige Norm-/Feature-/Suitebilanz in G2 |
| ilic-fork, Downstreamprojekte und GeoWerkstatt auswerten | Herkunfts- und Quellenregister mit konkreten übernommenen Architekturideen und Grenzen | [15](15-entscheidungen-risiken-und-quellen.md), [07](07-workspace-inkrementalitaet-und-lsp.md), [11](11-visualisierung-und-projektionen.md), [12](12-transfer-validierung-und-datenbanken.md) | Entscheidungen sind mit Quellen und eigenen Abnahmen verbunden; fremde Architektur wird nicht ungeprüft kopiert |
| Geeignete Repo-, Paket- und Toolnamen | Arbeitsname interlis-java, Group und Packages guru.interlis; vertraute CLI-Namen als kompatible Profile | [01](01-zielbild-und-anforderungen.md), [02](02-architektur-und-modulgrenzen.md), [03](03-build-und-repository.md), [18](18-cli-sdk-und-kompatibilitaetsadapter.md) | Konsistente Artefakte, Imports, Distributionen und SDK-Beispiele |
| Gradle bevorzugen und Build einfach halten | Ein Groovy-Multiprojektbuild mit Wrapper, wenigen Konventionen und tatsächlich benötigten Modulen | [03](03-build-und-repository.md), [14](14-implementierungsplan-und-arbeitspakete.md) | Frischer Checkout und einfache Lifecycle-Tasks; kein aktiver paralleler Maven-/Ant-Build |
| Tiefgehende Spezifikation für einen Coding-Agenten bis zu Methoden/Klassen | Öffentliche Fabriken und Signaturen, interne Verantwortungen, Dateninvarianten, Vor-/Nachbedingungen, Zustands- und Fehlerverträge | [04](04-quelltext-syntax-und-parser.md) bis [12](12-transfer-validierung-und-datenbanken.md), [16](16-api-register-und-vertragstests.md), [18](18-cli-sdk-und-kompatibilitaetsadapter.md) | Kompilierende SDK-Vertragstests und fachliche Positiv-/Negativfälle; Fortschritt nach Anforderungs-IDs |

## 3. Konsistenz der Methoden- und Klassenverträge

Der Querreview hat insbesondere die Übergänge zwischen den Fachkapiteln geprüft. Folgende Entscheidungen sind im Paket vereinheitlicht:

| Geprüfter Übergang | Verbindliches Ergebnis | Zugehöriger Nachweis im Implementierungsauftrag |
|---|---|---|
| Gemeinsame Typen und Modulgrenzen | Ein Besitzer pro Typ; model enthält keine Referenz auf Compiler-, Workspace- oder Hostklassen | CoreDependencyBoundaryTest und API-Register |
| Öffentliche Erzeugung | SyntaxServices, CompilerEngines, Workspaces und DiagramProjectors sind konkrete öffentliche Einstiege mit nutzbaren Defaults | PublicSdkConstructionTest |
| Compilerlauf und Logging | RunContext ist unveränderliche Metadaten; der vollständige Engineaufruf besitzt genau einen Compilerlauf; Hosts können Elternläufe besitzen | RunContextPropagationTest und CLI-Abschlussfall |
| Gate und Adapter | RejectionSummary stammt aus dem Gate und bleibt bis CLI/Bridge unterscheidbar; Adapter rekonstruieren die Gateentscheidung nicht | CompilationOutcomeContractTest und Bridge-Matrix |
| Analyse und gültiges Schema | Ein gültiges Schema veröffentlicht nur seine geprüfte Closure; unabhängige fehlerhafte Modelle bleiben ausserhalb dieser Sicht | CompiledSchemaSelectionTest |
| Quellen und Fingerprints | Exakter Text und tatsächliche Bindungen werden geprüft; ein behaupteter Resolverhash ist allein kein Wiederverwendungsbeweis | SourceSetBindingVerificationTest und FreshVsIncrementalContractTest |
| Determinismus | Quellen-/Diagnoseordnung ist UTF-16-lexikografisch und unabhängig von Map-Einfügereihenfolge; fachliche Transferordnung bleibt erhalten | CanonicalSourceOrderTest und semantische Goldens |
| Resolver und Workspace | PreparedClosure ist öffentlich über WorkspaceCapture erreichbar; Beschaffung bleibt Hostaufgabe | Offline-/PendingFetch-/Closuretests |
| Hintergrundanalyse und Wiederverwendung | acceptAnalysis übernimmt nur konsistente aktuelle Ergebnisse atomar; veraltete Ergebnisse verändern Cache und DependencyIndex nicht | DetachedAnalysisAcceptanceTest |
| Snapshots und Verbraucher | Öffentliche symbolbezogene Abfragen prüfen SymbolRef mit SnapshotId; das gilt auch für TransferPlan | CrossSnapshotReferenceTest und Planverträge |
| Abbruch und fehlende Implementierung | Ressourcenlimit, Nutzerabbruch, fehlende Implementierung und ungültige Sprache bleiben verschiedene Ursachen | Budget-, Coverage- und Outcome-Vertragstests |
| Overlays | SemanticOverlays des Compilers und verbraucherspezifische SchemaOverlays besitzen getrennte Eingaben und Fingerprints | Overlay-/Plan- und Parallelverwendungstests |
| Projektion und Auswahl | DiagramOptions.defaults benötigt eine konkrete selection; Renderer greifen auf denselben fachlichen Graphen zu | DiagramProjectionContractTest |
| Generatoren und CLI-Erfolg | Zusagte Modellformate besitzen ModelExporter-Verträge; CLI-Erfolg entsteht erst nach Export und Flush | ModelExportContractTest und CliCompletionAfterExportTest |
| Gradle und Plattformgates | Node-Vertragstests, reale Browserprüfung und Web-Image-Untersuchung sind unterschiedliche Aufgaben mit eigenem Ergebnis | checkPortable, checkBrowser und phasenspezifisches releaseCheck |
| Agentenregeln und aktueller Auftrag | Rootregeln gelten für das Repository, zusätzliche Spezifikationsregeln für dessen Dokumentpflege; Lesen aktiviert keine Implementierung | Scope- und Hierarchiereview beider AGENTS-Dateien mit Kapitel 00 und STARTPROMPT |

Diese Aufzählung ist kein unveränderliches Inventar aller späteren privaten Hilfsklassen. Der Agent darf die interne Implementierung vereinfachen, muss dabei aber dieselben beobachtbaren fachlichen Verträge, Reihenfolgen, Fehler und Lebensdauern erhalten. Eine Umbenennung eines gemeinsamen öffentlichen Typs erfordert die gemeinsame Aktualisierung aller betroffenen Kapitel, Beispiele und Tests gemäss API-006.

## 4. Dokumentprüfung und Grenzen ihrer Aussage

Für die Auslieferung werden die folgenden Eigenschaften des Pakets geprüft:

- Alle 21 Hauptdokumente sind als UTF-8 lesbar und im Inhaltsverzeichnis aufgeführt beziehungsweise als Einstieg erkennbar.
- Die zwei zusätzlichen AGENTS-Dateien haben eindeutige Zielpfade und Zuständigkeiten. Ihre Regeln sind mit Kapitel 00, den Fachverträgen und dem ausdrücklich zu aktivierenden Startprompt vereinbar.
- Alle 285 Anforderungsdefinitionen haben eine eindeutige ID sowie ausgefüllte Phase und Abnahme. Verweise auf Anforderungs-IDs lösen auf vorhandene Definitionen auf.
- Relative Markdown-Dateiverweise zeigen auf vorhandene Dateien. Verweise auf die Syntaxevidenz bleiben nach der beschriebenen Übernahme nach `docs/spec/` und `docs/evidence/` gültig.
- Codeblöcke sind geschlossen. Die vollständigen JSON-Beispiele sind syntaktisch parsebar. Java-Signaturblöcke mit bewusst ausgelassenen Bodies oder Imports werden nicht als bereits kompilierter Produktcode ausgegeben.
- Das API-Register, die Fachkapitel und die Arbeitspakete verwenden die vereinbarten gemeinsamen Typen und die zuletzt beschriebenen Lifecycle-/Capture-/Gate-Verträge.
- Die ZIP-Ausgabe enthält die Hauptdokumente, beide AGENTS-Dateien und die Referenzevidenz; interne Erstellungsdateien und Uploadhilfen gehören nicht zum Lieferumfang. Die Archivintegrität wird separat geprüft.

Nicht durch einen Markdownreview nachweisbar sind Sprachkonformität, korrekte Recovery bei allen Fehlern, Threadingverhalten, Speicherverbrauch, Browserkompatibilität, reale Exporttreue oder Datenbankinteroperabilität. Dafür existieren die konkreten Implementierungsaufträge und Tests. Auch die hier vorgeschlagenen Ressourcenstartwerte sind keine gemessenen Leistungszusagen.

Die referenzierten Upstreamstände sind in Kapitel 15 festgehalten. Die Spezifikation bildet ihre ausgewerteten Eigenschaften und die daraus getroffenen Entwurfsentscheidungen ab. Sie behauptet keine erneute Ausführung sämtlicher fremder CI-Pipelines oder eine vollständige Prüfung aller Versionen dieser Projekte.

## 5. Pflege des Anforderungsnachweises im Repository

Der Coding-Agent führt den tatsächlichen Umsetzungsstatus ausserhalb dieses statischen Inhaltsindex nach den Regeln von Kapitel 00 und 13. Eine sinnvolle Statuszeile enthält Anforderungs-ID, Status, zugehöriges Arbeitspaket, Implementierungsstelle, konkrete Fälle, zuletzt ausgeführten Befehl und Ergebnisnachweis. Ein Anforderungsindex allein ist kein Erfüllungsnachweis.

Teilweise erfüllte Anforderungen mit mehreren Phasen bleiben als solche erkennbar. Beispielsweise kann die Overlaygrenze in P1 korrekt sein, während die vollständige Verbraucherplanung erst P4 liefert. Die Angabe `P1/P4` bedeutet nicht, dass nach dem ersten Teil sämtliche zukünftigen Verpflichtungen erledigt sind. `optional/P3` bezeichnet den ausdrücklich optionalen alten Java-API-Adapter, nicht eine optionale allgemeine CLI-/Datenbankkompatibilität.

Bei einer neuen Anforderung wird eine neue freie ID ihres Bereichs vergeben. Bestehende IDs werden nicht für andere Bedeutungen wiederverwendet. Entfällt eine Anforderung aufgrund einer bewussten Scopeentscheidung, bleiben Entscheidung, Begründung und Auswirkungen nachverfolgbar. Ein Agent darf keinen offenen Nachweis durch Löschen der Anforderung oder Umdeuten eines Fehlerfalls in einen Erfolg beseitigen.

## 6. Vollständiger Anforderungsindex

Der Index gruppiert die 285 Anforderungen nach ihrem fachlichen Besitzer. Die verlinkten Fachkapitel enthalten jeweils den vollständigen normativen Text und die Methodenverträge. Die nachstehende Abnahmespalte ist eine lesbare Wiederholung des ursprünglichen Abnahmehinweises; im Fall einer späteren Änderung ist die Definition im Fachkapitel massgeblich und der Index wird synchronisiert. Die Einträge sind Anforderungen an die Umsetzung, keine Aussagen über bereits bestandene Tests.

<!-- REQUIREMENT-INDEX:START -->

### AGENT – Arbeitsregeln für den Coding-Agenten

10 Anforderungen; Definitionen in [00-auftrag-und-agentenregeln.md](00-auftrag-und-agentenregeln.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [AGENT-001](00-auftrag-und-agentenregeln.md) | P0 | Arbeitsbericht nennt Ausgangszustand und abgegrenzte Änderungen. |
| [AGENT-002](00-auftrag-und-agentenregeln.md) | P0 | Vier Teile sind im Ergebnis nachvollziehbar; keine reine Dateiliste. |
| [AGENT-003](00-auftrag-und-agentenregeln.md) | P0 | Negativtest und Capability-/Coverage-Bericht erkennen den fehlenden Teil. |
| [AGENT-004](00-auftrag-und-agentenregeln.md) | P1 | Unabgedecktes Konstrukt erzeugt kein gültiges CompiledSchema. |
| [AGENT-005](00-auftrag-und-agentenregeln.md) | P0 | Änderung von Goldens/Baselines enthält Referenz und Begründung. |
| [AGENT-006](00-auftrag-und-agentenregeln.md) | P1 | Referenzwerkzeuge laufen ausschliesslich in Testadaptern oder explizitem Legacy-Adapter. |
| [AGENT-007](00-auftrag-und-agentenregeln.md) | P0 | Abschlussbericht verwendet keine pauschale Aussage „alles grün“ ohne Umfang. |
| [AGENT-008](00-auftrag-und-agentenregeln.md) | P0 | Neuer Agent findet Anforderungen, Status, Testkommandos und nächsten Arbeitsschritt. |
| [AGENT-009](00-auftrag-und-agentenregeln.md) | P1 | Keine zweite inkompatible Signatur desselben öffentlichen Typs. |
| [AGENT-010](00-auftrag-und-agentenregeln.md) | P0 | Jede Produktionsabhängigkeit und jedes Modul besitzt einen aktuellen Verbraucher oder eine konkrete Abnahme. |

### GOAL – Übergeordnete Produktziele

12 Anforderungen; Definitionen in [01-zielbild-und-anforderungen.md](01-zielbild-und-anforderungen.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [GOAL-001](01-zielbild-und-anforderungen.md) | P1/P2 | Versionsmatrix einschliesslich versionsabhängiger Schlüsselwörter; P1 Umfang markiert, P2 vollständig. |
| [GOAL-002](01-zielbild-und-anforderungen.md) | P1 | Dieselben Eingaben liefern dieselbe normalisierte Analyse und dieselben Diagnosecodes. |
| [GOAL-003](01-zielbild-und-anforderungen.md) | P1/P2 | Verbrauchervertragstests für Typen, Referenzen, Reihenfolgen und Projektionen. |
| [GOAL-004](01-zielbild-und-anforderungen.md) | P1 | Editiersequenzen erhalten Nachbarsymbole und präzise fehlende Elemente. |
| [GOAL-005](01-zielbild-und-anforderungen.md) | P1/P3 | Revisionstests, Cache-Nachweise und Gleichheit zur frischen Analyse. |
| [GOAL-006](01-zielbild-und-anforderungen.md) | P1/P3 | Diagramm-Goldens und Navigation; Renderer erfindet keine Bindungsregeln. |
| [GOAL-007](01-zielbild-und-anforderungen.md) | P1 | JVM-/WASM-/Browserabnahmen im selben Arbeitspaket. |
| [GOAL-008](01-zielbild-und-anforderungen.md) | P1 | Diagnose- und Eventvertragstests, Legacy-/JSON-Darstellungen. |
| [GOAL-009](01-zielbild-und-anforderungen.md) | P4/P5 | Gemeinsame Modellfingerprints und Plan-Goldens. |
| [GOAL-010](01-zielbild-und-anforderungen.md) | P5 | Alte/neue Werkzeuge lesen und verändern Datenbanken in beiden Richtungen. |
| [GOAL-011](01-zielbild-und-anforderungen.md) | P3/P5 | Gepinnte Referenzläufe und genehmigte Abweichungen. |
| [GOAL-012](01-zielbild-und-anforderungen.md) | P0/P2 | Testreports unterscheiden Akzeptanz, Semantik und bekannte Abweichungen. |

### NFR – Nichtfunktionale Anforderungen

12 Anforderungen; Definitionen in [01-zielbild-und-anforderungen.md](01-zielbild-und-anforderungen.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [NFR-001](01-zielbild-und-anforderungen.md) | P1 | Reihenfolge, Diagnostik und normalisierte Projektion bleiben über Wiederholung/Plattform gleich. |
| [NFR-002](01-zielbild-und-anforderungen.md) | P1 | Parallele Abfragen verändern weder Resultate noch Cachezustand des Snapshots. |
| [NFR-003](01-zielbild-und-anforderungen.md) | P0 | Abhängigkeitsprüfung und In-Memory-/WASM-Lauf. |
| [NFR-004](01-zielbild-und-anforderungen.md) | P1/P3 | Messverfahren mit Rohresultaten; Ziele und Messungen getrennt. |
| [NFR-005](01-zielbild-und-anforderungen.md) | P1 | Phasenabbruchtests und Worker-Verhalten. |
| [NFR-006](01-zielbild-und-anforderungen.md) | P1 | UTF-16-/Codepoint-/Zeilenmatrix mit Emojis und Umlauten. |
| [NFR-007](01-zielbild-und-anforderungen.md) | P1 | Tests mit verschiedenen JVM-Locale-/Timezone-Einstellungen. |
| [NFR-008](01-zielbild-und-anforderungen.md) | P1 | API-Vertragstests und Register. |
| [NFR-009](01-zielbild-und-anforderungen.md) | P0 | Frischer Checkout, gepinnte Toolchain und dokumentierte Kommandos. |
| [NFR-010](01-zielbild-und-anforderungen.md) | P0 | Lokale Bibliotheks-/CLI-Ausführung ohne Spring, Datenbank oder Container. |
| [NFR-011](01-zielbild-und-anforderungen.md) | P4 | Grössenkorpus und definierte Reaktion auf Budgeterschöpfung; 64 GB nicht überschritten im festgelegten Profil. |
| [NFR-012](01-zielbild-und-anforderungen.md) | P0 | Kurzer Zweck je Dependency; kein Framework nur für künftige Eventualitäten. |

### ARCH – Architekturgrenzen

10 Anforderungen; Definitionen in [02-architektur-und-modulgrenzen.md](02-architektur-und-modulgrenzen.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [ARCH-001](02-architektur-und-modulgrenzen.md) | P0 | Build-/Architekturtest liest Produktionsgraph und Importgrenzen. |
| [ARCH-002](02-architektur-und-modulgrenzen.md) | P1 | Signaturprüfung plus portable Kompilierung. |
| [ARCH-003](02-architektur-und-modulgrenzen.md) | P1 | Mutations-/Lebensdauertests einschliesslich nach Workspace.close. |
| [ARCH-004](02-architektur-und-modulgrenzen.md) | P1 | Architektur- und Fehlerpfadtests. |
| [ARCH-005](02-architektur-und-modulgrenzen.md) | P1 | Zwei isolierte Sitzungen mit abweichenden Profilen beeinflussen sich nicht. |
| [ARCH-006](02-architektur-und-modulgrenzen.md) | P1/P2 | Geerbte/überschriebene Attribute und Rollen bleiben JVM/WASM gleich. |
| [ARCH-007](02-architektur-und-modulgrenzen.md) | P1 | Änderung ohne Quelltextänderung erzwingt erforderliche Neuanalyse. |
| [ARCH-008](02-architektur-und-modulgrenzen.md) | P1/P4/P5 | UnsupportedFeature erzeugt Ergebnis mit betroffenen Elementen. |
| [ARCH-009](02-architektur-und-modulgrenzen.md) | P0 | Minimale Java-main-/WASM-Anwendung analysiert aus Strings. |
| [ARCH-010](02-architektur-und-modulgrenzen.md) | P1 | Reuse.none und Cachepfad liefern fachlich gleiche Resultate. |

### BUILD – Build und Auslieferung

10 Anforderungen; Definitionen in [03-build-und-repository.md](03-build-und-repository.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [BUILD-001](03-build-und-repository.md) | P0 | Frischer Checkout baut ohne Maven/Ant-Installation. |
| [BUILD-002](03-build-und-repository.md) | P0 | Versionen-/Wrapper-/Dependencyreport. |
| [BUILD-003](03-build-und-repository.md) | P0 | Dependencytest, keine impliziten Zwischen-JAR-Kopien. |
| [BUILD-004](03-build-und-repository.md) | P0 | Konfiguration ist deklarativ, Taskabhängigkeiten explizit. |
| [BUILD-005](03-build-und-repository.md) | P0 | Runtimeclasspath und generierter Parser geprüft. |
| [BUILD-006](03-build-und-repository.md) | P0 | Zwei saubere Läufe ohne Zeitstempel-/Pfadabhängigkeit in fachlichen Outputs. |
| [BUILD-007](03-build-und-repository.md) | P0 | Wiederholung offline ohne Live-Repositoryzugriff. |
| [BUILD-008](03-build-und-repository.md) | P1 | Releaseprofil scheitert mit konkreter Vorbedingung. |
| [BUILD-009](03-build-und-repository.md) | P0 | Lokale Distribution funktioniert ohne Zugangsdaten. |
| [BUILD-010](03-build-und-repository.md) | P0 | Gradle reproduziert die Probe; POM bleibt nur in Evidenzarchiv. |

### SRC – Quellen und Positionen

7 Anforderungen; Definitionen in [04-quelltext-syntax-und-parser.md](04-quelltext-syntax-und-parser.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [SRC-001](04-quelltext-syntax-und-parser.md) | P1 | Zwei Revisionen derselben Quelle bleiben getrennt adressierbar. |
| [SRC-002](04-quelltext-syntax-und-parser.md) | P1 | Tests mit Umlauten, Supplementary Characters, CRLF und EOF. |
| [SRC-003](04-quelltext-syntax-und-parser.md) | P1 | Roundtrip der gültigen Positionen und dokumentierte Behandlung von Zeilenumbrüchen. |
| [SRC-004](04-quelltext-syntax-und-parser.md) | P1 | Negative Offsets, falsche Quelle, falsche Revision und Ende hinter Textlänge. |
| [SRC-005](04-quelltext-syntax-und-parser.md) | P1 | Identischer UTF-16-String nach Rekonstruktion für jedes Syntaxfixture. |
| [SRC-006](04-quelltext-syntax-und-parser.md) | P1 | Keine plattformabhängige Auflösung und keine Netz-/Dateizugriffe bei Konstruktion. |
| [SRC-007](04-quelltext-syntax-und-parser.md) | P1 | Gleiche Inhalte erhalten auf allen Zielplattformen denselben Fingerprint. |

### SYN – Syntax und Parser

15 Anforderungen; Definitionen in [04-quelltext-syntax-und-parser.md](04-quelltext-syntax-und-parser.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [SYN-001](04-quelltext-syntax-und-parser.md) | P1 | Gültiges Modell plus nachlaufender Text wird abgelehnt. |
| [SYN-002](04-quelltext-syntax-und-parser.md) | P1 | Ein unzulässiges Zeichen ist sichtbar, auch wenn der Parser den Rest akzeptiert. |
| [SYN-003](04-quelltext-syntax-und-parser.md) | P1 | 1.0, 2.2 und unbekannte Versionsangaben erhalten eine erklärende Diagnose. |
| [SYN-004](04-quelltext-syntax-und-parser.md) | P2 | Positive und negative versionsbezogene Tests; keine blosse Prüfung des Headers. |
| [SYN-005](04-quelltext-syntax-und-parser.md) | P1 | Recovery allein erzeugt in keinem Modus ein gültiges Compile-Ergebnis. |
| [SYN-006](04-quelltext-syntax-und-parser.md) | P1 | `height :` zerstört nicht die umgebende Klasse oder die folgende Klasse. |
| [SYN-007](04-quelltext-syntax-und-parser.md) | P1 | Fehlstellen mit Nullbreite; Recoverysegment mit originalem Textbereich. |
| [SYN-008](04-quelltext-syntax-und-parser.md) | P1 | Architekturtest und öffentliche API-Prüfung. |
| [SYN-009](04-quelltext-syntax-und-parser.md) | P1 | Wiederholte fehlerhafte Eingaben und tiefe Verschachtelung liefern definiertes Ergebnis. |
| [SYN-010](04-quelltext-syntax-und-parser.md) | P2 | Ausdruck, Objektpfad und Attributfragment mit korrektem Offset; Restinhalt wird erkannt. |
| [SYN-011](04-quelltext-syntax-und-parser.md) | P1 | Syntax enthält Fehlerknoten; Metamodell enthält keinen ANTLR-Kontext. |
| [SYN-012](04-quelltext-syntax-und-parser.md) | P0 | Reproduzierbarer Gradlebuild, dokumentierte Herkunft und Lizenz. |
| [SYN-013](04-quelltext-syntax-und-parser.md) | P1/P2 | Coverage-Test; kein `default -> null` und kein stilles Überspringen. |
| [SYN-014](04-quelltext-syntax-und-parser.md) | P1 | Sitzungsparallelität, Duplikate und Einfügen einer vorangehenden Deklaration. |
| [SYN-015](04-quelltext-syntax-und-parser.md) | P1 | Gültige, ungültige und Editorfixtures für jede betroffene Regel. |

### META – Metamodell und Typen

17 Anforderungen; Definitionen in [05-metamodell-und-typensystem.md](05-metamodell-und-typensystem.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [META-001](05-metamodell-und-typensystem.md) | P1 | Keine Änderung über zurückgegebene Listen, Maps, Builder oder Consumer. |
| [META-002](05-metamodell-und-typensystem.md) | P1 | `CompiledSchema` nur über das Gate in Kapitel 06; Teilanalyse ohne Erfolgssiegel. |
| [META-003](05-metamodell-und-typensystem.md) | P1 | Doppelte Namen, Rename und Revisionen ergeben keine falsche Vereinigung. |
| [META-004](05-metamodell-und-typensystem.md) | P1 | Verwendung eines alten `SymbolRef` löst einen definierten API-Fehler aus. |
| [META-005](05-metamodell-und-typensystem.md) | P1/P2 | Fehlender Domänenname, `ErrorType` und legale Generic-Domäne bleiben unterscheidbar. |
| [META-006](05-metamodell-und-typensystem.md) | P2 | Katalogzeile besitzt Konstruktor-/Analyse-/Negativ-/Export- oder Querytest. |
| [META-007](05-metamodell-und-typensystem.md) | P2 | Übersetzungsfixture verändert keine Referenzidentität. |
| [META-008](05-metamodell-und-typensystem.md) | P1/P2 | Goldenvergleich mit Legacyfällen für Vererbung, Überschreibung und eingebettete Rollen. |
| [META-009](05-metamodell-und-typensystem.md) | P1 | Zahlen grösser 2^53, Skalen, Exponenten und Grenzen ohne Doubleverlust. |
| [META-010](05-metamodell-und-typensystem.md) | P1 | Fehler in unabhängiger Quelle blockiert nicht; Fehler in Importclosure blockiert. |
| [META-011](05-metamodell-und-typensystem.md) | P1 | Geerbtes Attribut führt auf Originaldeklaration zurück. |
| [META-012](05-metamodell-und-typensystem.md) | P1/P4 | Zwei unterschiedlich konfigurierte Verbraucher können dasselbe Schema parallel verwenden. |
| [META-013](05-metamodell-und-typensystem.md) | P1/P2 | Gültiger View wird nicht wegen fehlendem SQL-Exporter abgelehnt oder still verworfen. |
| [META-014](05-metamodell-und-typensystem.md) | P1/P2 | Exhaustive Variantenprüfung und sichtbare Coverage-Lücke. |
| [META-015](05-metamodell-und-typensystem.md) | P1/P2 | Parallelkompilierung beider Versionen beeinflusst keine andere Sitzung. |
| [META-016](05-metamodell-und-typensystem.md) | P1/P2 | Unzulässige Zuweisung liefert Grund und betroffene Teiltypen. |
| [META-017](05-metamodell-und-typensystem.md) | P2 | Fachliche Änderung verändert Fingerprint; Run-ID und Uhrzeit tun dies nicht. |

### SEM – Compiler und Semantik

20 Anforderungen; Definitionen in [06-kompilierung-und-semantik.md](06-kompilierung-und-semantik.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [SEM-001](06-kompilierung-und-semantik.md) | P1 | Kein Dateisystem-/HTTP-Zugriff aus Compiler- oder Modellmodul. |
| [SEM-002](06-kompilierung-und-semantik.md) | P1 | Je ein real auslösender Testfall pro Ergebniskategorie. |
| [SEM-003](06-kompilierung-und-semantik.md) | P1 | Unterdrückte oder nicht dargestellte Fehler erzeugen keinen Erfolg. |
| [SEM-004](06-kompilierung-und-semantik.md) | P1 | Zwei unabhängige Modelle, eines fehlerhaft; gezielte Kompilierung des anderen gelingt. |
| [SEM-005](06-kompilierung-und-semantik.md) | P1 | Kein Erfolg durch `null`, Defaulttyp oder ausgelassene Deklaration. |
| [SEM-006](06-kompilierung-und-semantik.md) | P2 | Gültiger Fall kompiliert ohne Runtimeimplementierung beziehungsweise Datensatzkonkretisierung. |
| [SEM-007](06-kompilierung-und-semantik.md) | P1 | Wiederholung und Plattformvergleich liefern gleiche fachliche Resultate. |
| [SEM-008](06-kompilierung-und-semantik.md) | P1/P2 | Ziel vorhanden im globalen Index, aber im fraglichen Scope unzulässig: Diagnose. |
| [SEM-009](06-kompilierung-und-semantik.md) | P1/P2 | Alias-/Vererbungszyklus und erlaubter Referenzzyklus besitzen getrennte Ergebnisse. |
| [SEM-010](06-kompilierung-und-semantik.md) | P2 | Vollständige Abdeckungsmatrix mit positiven und negativen Fällen. |
| [SEM-011](06-kompilierung-und-semantik.md) | P1 | Fresh-versus-reuse nach Editfolgen. |
| [SEM-012](06-kompilierung-und-semantik.md) | P1 | Geänderte Sprachoption oder Importrevision verursacht keinen falschen Cachetreffer. |
| [SEM-013](06-kompilierung-und-semantik.md) | P1 | Mehrere Engines/Sitzungen ohne gegenseitige Beeinflussung. |
| [SEM-014](06-kompilierung-und-semantik.md) | P1 | Limit reduziert Ergebnisabdeckung; Gate bleibt geschlossen. |
| [SEM-015](06-kompilierung-und-semantik.md) | P1 | Completion kann bekannte Symbole nutzen; Typabfrage unbekannter Teile liefert `NotAnalyzed`. |
| [SEM-016](06-kompilierung-und-semantik.md) | P1/P4 | Zwei Overlaykonfigurationen verwechseln keine Resultate oder Laufzeitcaches. |
| [SEM-017](06-kompilierung-und-semantik.md) | P1/P2 | Jede akzeptierte Abweichung besitzt Referenz, Fall und bewusste Entscheidung. |
| [SEM-018](06-kompilierung-und-semantik.md) | P1 | Architekturtest und gezielter Gate-Negativtest. |
| [SEM-019](06-kompilierung-und-semantik.md) | P1 | Identische semantische Fixtureausgabe; kein nur für Wasm vereinfachtes Modell. |
| [SEM-020](06-kompilierung-und-semantik.md) | P1/P3 | Adapterdelegation und Vergleich an identischen Modellfällen. |

### INC – Workspace und Inkrementalität

22 Anforderungen; Definitionen in [07-workspace-inkrementalitaet-und-lsp.md](07-workspace-inkrementalitaet-und-lsp.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [INC-001](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Dieselbe Sitzung auf JVM und Wasm mit virtuellen URIs. |
| [INC-002](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Konflikt- und ungültige Edit-Tests zeigen unveränderten Ausgangszustand. |
| [INC-003](07-workspace-inkrementalitaet-und-lsp.md) | P1 | No-op-Änderung und Schliessen/Wiederöffnen eines Dokuments. |
| [INC-004](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Alter Snapshot bleibt nach weiteren Änderungen identisch abfragbar. |
| [INC-005](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Editfolge mit fehlendem Attributtyp; Nachbarklasse navigierbar. |
| [INC-006](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Zähler für Parse-Aufrufe belegen Wiederverwendung ohne Timing-Annahme. |
| [INC-007](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Änderungen von Sprachversion, Optionen, Imports und Overlays invalidieren. |
| [INC-008](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Später hinzugefügtes Modell oder zweite Kandidatendeklaration aktualisiert Analyse. |
| [INC-009](07-workspace-inkrementalitaet-und-lsp.md) | P1–P3 | Normalisierter Vergleich nach jeder Editfolge. |
| [INC-010](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Kommentar-/Nachbaränderung verändert die betroffenen SymbolIds nicht. |
| [INC-011](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Duplikate, Verschieben und Löschen/Neuanlegen erzeugen nötigenfalls neue IDs. |
| [INC-012](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Cross-Snapshot- und Cross-Revision-Abfragen schlagen typisiert fehl. |
| [INC-013](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Abbruch an jeder Phasengrenze, danach frische/incrementelle Ergebnisgleichheit. |
| [INC-014](07-workspace-inkrementalitaet-und-lsp.md) | P2 | Kleine Limits erzwingen Eviction ohne Ergebnisverlust. |
| [INC-015](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Zwei unabhängige Wurzeln, eine davon ungültig. |
| [INC-016](07-workspace-inkrementalitaet-und-lsp.md) | P2 | Overlay-Fingerprint verändert Ergebnis; Cache-Eviction verändert es nicht. |
| [INC-017](07-workspace-inkrementalitaet-und-lsp.md) | P3 | Benchmark und Gleichheitsnachweis vor Aktivierung einer Optimierung. |
| [INC-018](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Ergebnis von Revision n trifft nach Revision n+1 ein. |
| [INC-019](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Gleicher Quelltext, anderer Importstand führt zu neuer Analyse. |
| [INC-020](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Zwei Sitzungen mit gleichnamigen, unterschiedlichen Modellen. |
| [INC-021](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Ein Konflikt in einer von drei Quellen verhindert jede Teilübernahme. |
| [INC-022](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Capture, paralleles Quellupdate, verspätete Analyse und anschliessender Reuse-Lauf. |

### LSP – Sprachdienste und LSP

16 Anforderungen; Definitionen in [07-workspace-inkrementalitaet-und-lsp.md](07-workspace-inkrementalitaet-und-lsp.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [LSP-001](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Keine zweite Binder-/Typprüfungsimplementierung im LSP-Modul. |
| [LSP-002](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Prozess-/Protokolltest einschliesslich sauberem Shutdown. |
| [LSP-003](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Ungespeicherte Änderung und gleichzeitige Dateimeldung. |
| [LSP-004](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Mehrere abhängige Änderungen innerhalb einer Notification. |
| [LSP-005](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Gleiche Codes/Ranges in CLI-JSON, LSP und Browser. |
| [LSP-006](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Fehler korrigieren; Client empfängt leere Liste. |
| [LSP-007](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Import, Vererbung und fehlerhafter Nachbar im Editorfixture. |
| [LSP-008](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Logausgaben und Stacktraces verunreinigen den Kanal nicht. |
| [LSP-009](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Architekturprüfung und parallele unabhängige Serverinstanzen. |
| [LSP-010](07-workspace-inkrementalitaet-und-lsp.md) | P1–P3 | Capability-Snapshot je Phase. |
| [LSP-011](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Abbruchantwort und anschliessend funktionsfähige Sitzung. |
| [LSP-012](07-workspace-inkrementalitaet-und-lsp.md) | P3 | Mehrdatei-Rename, Kollision, fehlende Quelle, Stale-Ergebnis. |
| [LSP-013](07-workspace-inkrementalitaet-und-lsp.md) | P3 | Keine Regex-Nachimplementierung semantischer Namensauflösung. |
| [LSP-014](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Modelländerung während Layout; alte Quellverweise abgelehnt. |
| [LSP-015](07-workspace-inkrementalitaet-und-lsp.md) | P2 | Importdatei extern aktualisiert, keine Clienttextänderung. |
| [LSP-016](07-workspace-inkrementalitaet-und-lsp.md) | P1 | Ungültiges JSON-RPC, ungültiges INTERLIS und interner Fehler erhalten verschiedene Antworten. |

### RES – Modellauflösung und Repositories

12 Anforderungen; Definitionen in [08-modellaufloesung-und-repositories.md](08-modellaufloesung-und-repositories.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [RES-001](08-modellaufloesung-und-repositories.md) | P1 | In-Memory- und Browserlauf; fehlende Quelle wird als PendingSourceRequest sichtbar. |
| [RES-002](08-modellaufloesung-und-repositories.md) | P1 | Modelle mit gleichem Namen/verschiedenen Versionsstrings werden korrekt gewählt oder als mehrdeutig gemeldet. |
| [RES-003](08-modellaufloesung-und-repositories.md) | P1 | ByName/BySource und Importbindung testen unterschiedliche Closure. |
| [RES-004](08-modellaufloesung-und-repositories.md) | P1 | Gleicher Katalog trotz anderer Mapiteration ergibt gleichen Trace und Resultat. |
| [RES-005](08-modellaufloesung-und-repositories.md) | P1 | Manipulierter Offset oder falsche Revision wird verworfen. |
| [RES-006](08-modellaufloesung-und-repositories.md) | P1 | Fehlender Import erscheint später ohne Edit des importierenden Textes. |
| [RES-007](08-modellaufloesung-und-repositories.md) | P1 | Veralteter Batch wird atomar abgewiesen; Editoroverride bleibt aktuell. |
| [RES-008](08-modellaufloesung-und-repositories.md) | P1 | Alle erforderlichen Modelle liegen vor; kein Netztestzugriff. |
| [RES-009](08-modellaufloesung-und-repositories.md) | P2 | Timeout, 404, 403, defekte Kodierung, Redirectzyklus und grosse Antwort separat geprüft. |
| [RES-010](08-modellaufloesung-und-repositories.md) | P1 | UTF-8/UTF-16/CRLF-/BOM-Provenienztests. |
| [RES-011](08-modellaufloesung-und-repositories.md) | P2 | Gepinnte eigene/offizielle Indexdateien; keine ad-hoc HTML-Suche als Compilerresolver. |
| [RES-012](08-modellaufloesung-und-repositories.md) | P1 | Kreisimport, Doppelimport und Modellzahlgrenze mit deterministischem Resultat. |

### DIAG – Fachliche Diagnostik

10 Anforderungen; Definitionen in [09-diagnostik-logging-und-telemetrie.md](09-diagnostik-logging-und-telemetrie.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [DIAG-001](09-diagnostik-logging-und-telemetrie.md) | P1 | Ein Syntax- und ein Semantikfehler sind ohne Logauswertung über die Java-API und WASM auslesbar. |
| [DIAG-002](09-diagnostik-logging-und-telemetrie.md) | P1 | Textänderung beeinflusst Code, Ursachenbezug und Tests auf Argumente nicht. |
| [DIAG-003](09-diagnostik-logging-und-telemetrie.md) | P1 | Tests mit CRLF, Umlauten, Emoji und globalem Auflösungsfehler. |
| [DIAG-004](09-diagnostik-logging-und-telemetrie.md) | P1 | Unterdrückte Folgefehler erzeugen keinen Erfolg; Warnungsablehnung wird nicht als Sprachfehler dargestellt. |
| [DIAG-005](09-diagnostik-logging-und-telemetrie.md) | P1 | Fehlender Import verdeckt passende Folgefehler, ein unabhängiger Literalfehler bleibt sichtbar. |
| [DIAG-006](09-diagnostik-logging-und-telemetrie.md) | P2 | Katalogvertrag und JSON-Vertragstests. |
| [DIAG-007](09-diagnostik-logging-und-telemetrie.md) | P1 | Ungültige Modelle ergeben `Rejected`; injizierter interner Defekt ergibt `Failed` mit `FailureId`. |
| [DIAG-008](09-diagnostik-logging-und-telemetrie.md) | P1 | Wiederholung und Laufzeitvergleich mit definierter ID-Normalisierung. |
| [DIAG-009](09-diagnostik-logging-und-telemetrie.md) | P1 | Adversarische Eingabe überschreitet konfiguriertes Limit kontrolliert und zeitnah. |
| [DIAG-010](09-diagnostik-logging-und-telemetrie.md) | P3 | Änderung an einem inzwischen modifizierten Dokument wird abgewiesen oder neu berechnet. |

### LOG – Logging und Laufbeobachtung

10 Anforderungen; Definitionen in [09-diagnostik-logging-und-telemetrie.md](09-diagnostik-logging-und-telemetrie.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [LOG-001](09-diagnostik-logging-und-telemetrie.md) | P0 | Abhängigkeitsprüfung und JVM-/WASM-Build. |
| [LOG-002](09-diagnostik-logging-und-telemetrie.md) | P1 | Ereignisautomat einschliesslich Exception-, Coverage- und Abbruchpfaden. |
| [LOG-003](09-diagnostik-logging-und-telemetrie.md) | P1 | Testobserver zählt Abschlussversuche, einschliesslich Observerfehlern. |
| [LOG-004](09-diagnostik-logging-und-telemetrie.md) | P1 | Werfender Observer verändert das Compile-Ergebnis nicht. |
| [LOG-005](09-diagnostik-logging-und-telemetrie.md) | P3 | Gepinnte CLI-Fixtures mit getrennt erfasstem stdout, stderr und Exitcode. |
| [LOG-006](09-diagnostik-logging-und-telemetrie.md) | P1 | LSP-Test mit parallelen Diagnosen und Betriebsereignissen. |
| [LOG-007](09-diagnostik-logging-und-telemetrie.md) | P1 | Schema- und Reihenfolgetests. |
| [LOG-008](09-diagnostik-logging-und-telemetrie.md) | P1 | Resolver- und JDBC-Tests mit markierten Testgeheimnissen. |
| [LOG-009](09-diagnostik-logging-und-telemetrie.md) | P3 | Lasttest mit begrenzter Queue und dokumentierter Verwerfungsstatistik. |
| [LOG-010](09-diagnostik-logging-und-telemetrie.md) | P1 | Zwei unabhängige Compilerinstanzen beeinflussen einander nicht. |

### WASM – WebAssembly und Portabilität

18 Anforderungen; Definitionen in [10-webassembly-und-portabilitaet.md](10-webassembly-und-portabilitaet.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [WASM-001](10-webassembly-und-portabilitaet.md) | P0–P2 | Gemeinsame Module, keine zweite INTERLIS-Semantik in JavaScript. |
| [WASM-002](10-webassembly-und-portabilitaet.md) | P0 | Frische Build-/Laufmanifestdatei mit Befehlen und Exitcodes. |
| [WASM-003](10-webassembly-und-portabilitaet.md) | P0 | Abhängigkeits-/Importprüfung. |
| [WASM-004](10-webassembly-und-portabilitaet.md) | P0–P2 | Feature-/Bibliotheksmatrix mit ausführbaren Prüfungen. |
| [WASM-005](10-webassembly-und-portabilitaet.md) | P1 | Gleichheit normalisierter JVM-/Wasm-Ausgaben. |
| [WASM-006](10-webassembly-und-portabilitaet.md) | P1 | Gepinnte Browser-/Versionskombinationen jeweils PASS; fehlende Umgebung hält G1 offen. |
| [WASM-007](10-webassembly-und-portabilitaet.md) | P1 | Build-/Laufresultat oder reproduzierbarer konkret belegter Blocker. |
| [WASM-008](10-webassembly-und-portabilitaet.md) | P1 | Grenzwerte jenseits 2^53, Dezimalskalen, Unicode und JSON-Roundtrip. |
| [WASM-009](10-webassembly-und-portabilitaet.md) | P1 | Durchgängige Mehrdatei-Editfolge im Worker. |
| [WASM-010](10-webassembly-und-portabilitaet.md) | P1 | Standardclient führt Compiler in eigenem Worker aus. |
| [WASM-011](10-webassembly-und-portabilitaet.md) | P1 | Laufender Worker kann beendet und aus Hostzustand neu aufgebaut werden. |
| [WASM-012](10-webassembly-und-portabilitaet.md) | P0 | Frische Installation benötigt keine zufällig vorhandene alte Runtime. |
| [WASM-013](10-webassembly-und-portabilitaet.md) | P1–P2 | Input-Fingerprint und Fixture-Pins im Testmanifest. |
| [WASM-014](10-webassembly-und-portabilitaet.md) | P1–P2 | Explizite Capabilities und Unsupported-Projektionsresultate. |
| [WASM-015](10-webassembly-und-portabilitaet.md) | P2 | Wiederholte Edits mit begrenztem Speicherwachstum. |
| [WASM-016](10-webassembly-und-portabilitaet.md) | P0 | Benannte Tasks/Artefakte bezeichnen ihr reales Ziel. |
| [WASM-017](10-webassembly-und-portabilitaet.md) | P1 | Compilerfixture funktioniert ohne Netzberechtigung. |
| [WASM-018](10-webassembly-und-portabilitaet.md) | P0–P3 | Rohdaten, Hardware-/Runtimeversion und Messmethode für jede veröffentlichte Zahl. |

### VIZ – Visualisierung und Projektionen

18 Anforderungen; Definitionen in [11-visualisierung-und-projektionen.md](11-visualisierung-und-projektionen.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [VIZ-001](11-visualisierung-und-projektionen.md) | P1 | Zwei Renderer zeigen dieselben Knoten, Rollen und Beziehungen. |
| [VIZ-002](11-visualisierung-und-projektionen.md) | P1 | Durchgängiges Editor-/Wasm-Fixture mit Navigation. |
| [VIZ-003](11-visualisierung-und-projektionen.md) | P1 | Fehlender Import erzeugt markierten Stub und keinen erfundenen Zieltyp. |
| [VIZ-004](11-visualisierung-und-projektionen.md) | P1 | Alte Diagrammanker werden nach einer Quelländerung geprüft. |
| [VIZ-005](11-visualisierung-und-projektionen.md) | P1 | Kommentaränderung und Einfügen einer fremden Assoziation ändern bestehende IDs nicht. |
| [VIZ-006](11-visualisierung-und-projektionen.md) | P1 | Gleichnamige Fehlerdeklarationen und Umbenennung. |
| [VIZ-007](11-visualisierung-und-projektionen.md) | P2 | N-äre und attributierte Assoziationsfixtures. |
| [VIZ-008](11-visualisierung-und-projektionen.md) | P2 | Effektive Reihenfolge stimmt mit Metamodellqueries überein. |
| [VIZ-009](11-visualisierung-und-projektionen.md) | P1–P2 | Rendererübergreifende Projektionstests für dieselben Filter. |
| [VIZ-010](11-visualisierung-und-projektionen.md) | P1 | Verschieben/Filtern verändert keine Semantik oder Quellrevision. |
| [VIZ-011](11-visualisierung-und-projektionen.md) | P1–P3 | Keine stille Auslassung n-ärer Beziehungen oder falsche Gültigkeitsaussage. |
| [VIZ-012](11-visualisierung-und-projektionen.md) | P1 | Sonderzeichen, Zeilenwechsel und rendererähnlicher Modelltext. |
| [VIZ-013](11-visualisierung-und-projektionen.md) | P1 | Gleiche Projektion ergibt byteidentischen Mermaid-/PlantUML-Text. |
| [VIZ-014](11-visualisierung-und-projektionen.md) | P1 | Kleiner geprüfter Adapter statt neuer fachlicher Rendererlogik. |
| [VIZ-015](11-visualisierung-und-projektionen.md) | P1–P3 | P1 ohne GLSP-Server betreibbar; späterer Adapter separat. |
| [VIZ-016](11-visualisierung-und-projektionen.md) | P1 | Kleines Knoten-/Kantenbudget erzeugt LIMITED, keine scheinbar vollständige Ausgabe. |
| [VIZ-017](11-visualisierung-und-projektionen.md) | P2 | Sprache wechseln verändert Labels, nicht fachliche Identität. |
| [VIZ-018](11-visualisierung-und-projektionen.md) | P1 | Änderung während Layout/Export führt zu Stale-Prüfung. |

### STACK – Transfer und Validierung

15 Anforderungen; Definitionen in [12-transfer-validierung-und-datenbanken.md](12-transfer-validierung-und-datenbanken.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [STACK-001](12-transfer-validierung-und-datenbanken.md) | P2 | Semantische Goldens gegen ausgewählte Altmodelle einschliesslich Reihenfolge, Übersetzung, Views und Rollen. |
| [STACK-002](12-transfer-validierung-und-datenbanken.md) | P1 | Gültiges Modell ergibt Schema; absichtlich begrenzter Projektor liefert `Unsupported`. |
| [STACK-003](12-transfer-validierung-und-datenbanken.md) | P1/P4 | Parallel erzeugte Projektionen beeinflussen sich nicht; veränderte Optionen ergeben passende Fingerprints. |
| [STACK-004](12-transfer-validierung-und-datenbanken.md) | P1 | API und Cachetests erlauben keine Ableitung ausschliesslich aus `ILI23`/`ILI24`. |
| [STACK-005](12-transfer-validierung-und-datenbanken.md) | P2 | Übersetzungsmodell mit abweichenden Namen wird korrekt projiziert. |
| [STACK-006](12-transfer-validierung-und-datenbanken.md) | P2 | Golden für Vererbung, überschriebenes Attribut und eingebettete Assoziationsrolle. |
| [STACK-007](12-transfer-validierung-und-datenbanken.md) | P4 | Planung meldet alle relevanten fehlenden Fähigkeiten; kein stilles Weglassen. |
| [STACK-008](12-transfer-validierung-und-datenbanken.md) | P4 | Grosser Transfer bei festem Arbeitsspeicherbudget; dokumentierte Obergrenze für ein einzelnes Objekt. |
| [STACK-009](12-transfer-validierung-und-datenbanken.md) | P4 | Adaptervertrag mit verschachtelten Strukturen, Referenzen, Reihenfolgen und undefinierten Werten. |
| [STACK-010](12-transfer-validierung-und-datenbanken.md) | P4 | Bericht mit geprüften und ausgelassenen Regelklassen. |
| [STACK-011](12-transfer-validierung-und-datenbanken.md) | P4 | Ausdrücke werden nicht erneut aus Rohtext interpretiert; exakte Grenzfalltests. |
| [STACK-012](12-transfer-validierung-und-datenbanken.md) | P4 | Geometrieadapter- und Cachetests mit Bögen, Präzision und geänderten Optionen. |
| [STACK-013](12-transfer-validierung-und-datenbanken.md) | P4 | Lasttest berücksichtigt Heap, Native Memory, Caches und Puffer; Überschreitung wird kontrolliert behandelt. |
| [STACK-014](12-transfer-validierung-und-datenbanken.md) | P2/P4 | Gültige abstrakte Domäne kompiliert; fehlende oder ungültige Transferkonkretisierung wird separat diagnostiziert. |
| [STACK-015](12-transfer-validierung-und-datenbanken.md) | P2/P4 | Verschiedene Overlays teilen keine inkorrekten Pläne oder Caches. |

### DB – Datenbankkompatibilität

10 Anforderungen; Definitionen in [12-transfer-validierung-und-datenbanken.md](12-transfer-validierung-und-datenbanken.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [DB-001](12-transfer-validierung-und-datenbanken.md) | P5 | Bidirektionale Interoperabilitätsmatrix mit gepinnten Altversionen. |
| [DB-002](12-transfer-validierung-und-datenbanken.md) | P5 | Bestehende gekürzte oder kollidierte Namen bleiben erhalten. |
| [DB-003](12-transfer-validierung-und-datenbanken.md) | P5 | Strukturelle Schema-Goldens und Metadatenvergleiche. |
| [DB-004](12-transfer-validierung-und-datenbanken.md) | P5 | `SchemaPlanner` benötigt keine schreibende Verbindung und erzeugt reviewbaren Plan. |
| [DB-005](12-transfer-validierung-und-datenbanken.md) | P5 | Capability- und Dialekttests. |
| [DB-006](12-transfer-validierung-und-datenbanken.md) | P5 | Alt→Neu und Neu→Alt mit realen Operationen. |
| [DB-007](12-transfer-validierung-und-datenbanken.md) | P5 | Absichtlich beschädigte Mappingmetadaten werden erkannt. |
| [DB-008](12-transfer-validierung-und-datenbanken.md) | P5 | Abbruch vor/nach Batch und injizierter SQL-Fehler ohne ungemeldeten Teilzustand. |
| [DB-009](12-transfer-validierung-und-datenbanken.md) | P5 | Gemessene Statement-/Roundtripzahlen und Fehlerzuordnung im Batch. |
| [DB-010](12-transfer-validierung-und-datenbanken.md) | P5 | Dieselben Eingaben unter altem Profil ergeben dieselbe vertragliche Abbildung. |

### TEST – Tests und Qualität

15 Anforderungen; Definitionen in [13-tests-kompatibilitaet-und-qualitaet.md](13-tests-kompatibilitaet-und-qualitaet.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [TEST-001](13-tests-kompatibilitaet-und-qualitaet.md) | P0 | Aktualisierte Anforderungs-/Abdeckungsmatrix. |
| [TEST-002](13-tests-kompatibilitaet-und-qualitaet.md) | P0 | Manifest und überprüfte Downloads; kein stilles `latest`. |
| [TEST-003](13-tests-kompatibilitaet-und-qualitaet.md) | P1 | Bericht mit ausgewähltem und ausgeschlossenem Umfang. |
| [TEST-004](13-tests-kompatibilitaet-und-qualitaet.md) | P1/P2 | Typ-, Bindungs-, Reihenfolge- und Constraint-Goldens. |
| [TEST-005](13-tests-kompatibilitaet-und-qualitaet.md) | P1 | Negativtests für Recovery und unvollständige Abdeckung. |
| [TEST-006](13-tests-kompatibilitaet-und-qualitaet.md) | P1 | Deterministische Editiersequenzen und normalisierter Ergebnisvergleich. |
| [TEST-007](13-tests-kompatibilitaet-und-qualitaet.md) | P1 | Maschinenlesbarer Paritätsbericht. |
| [TEST-008](13-tests-kompatibilitaet-und-qualitaet.md) | P1/P3 | Ausführbare Browsermatrix mit Laufzeitvoraussetzungen. |
| [TEST-009](13-tests-kompatibilitaet-und-qualitaet.md) | P1/P2 | Reproduzierbare Seeds und kontrollierte Ressourcenlimits. |
| [TEST-010](13-tests-kompatibilitaet-und-qualitaet.md) | P3 | Versionierter Messbericht, ohne aus kleinen Beispielen abgeleitete Produktionszusagen. |
| [TEST-011](13-tests-kompatibilitaet-und-qualitaet.md) | P1 | Überprüfter Baselineeintrag; kein automatisches Umwandeln aller FAILs in XFAIL. |
| [TEST-012](13-tests-kompatibilitaet-und-qualitaet.md) | P1 | Fixture-Metadaten mit Abschnitt/Quelle oder offenem Reviewstatus. |
| [TEST-013](13-tests-kompatibilitaet-und-qualitaet.md) | P2 | Gefrorener Quellenbestand mit vollständig aufgelöster Abhängigkeit. |
| [TEST-014](13-tests-kompatibilitaet-und-qualitaet.md) | P1 | Zustands- und Integrationstests. |
| [TEST-015](13-tests-kompatibilitaet-und-qualitaet.md) | P0/P1 | Abhängigkeits- und öffentliche API-Prüfung. |

### COMPAT – Kompatibilitätsnachweise

7 Anforderungen; Definitionen in [13-tests-kompatibilitaet-und-qualitaet.md](13-tests-kompatibilitaet-und-qualitaet.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [COMPAT-001](13-tests-kompatibilitaet-und-qualitaet.md) | P0 | Benannte Profile und Matrix; kein pauschaler Kompatibilitätsclaim. |
| [COMPAT-002](13-tests-kompatibilitaet-und-qualitaet.md) | P3 | Getrennte stdout-/stderr-/Exitcode-Fixtures. |
| [COMPAT-003](13-tests-kompatibilitaet-und-qualitaet.md) | P2 | Dokumentierte Entscheidung: Regression, bewusste Normkorrektur oder offener Fall. |
| [COMPAT-004](13-tests-kompatibilitaet-und-qualitaet.md) | P3/P4 | Integration mit gepinnten Verbrauchern; blosse Gettergleichheit genügt nicht. |
| [COMPAT-005](13-tests-kompatibilitaet-und-qualitaet.md) | P5 | Alt→Neu und Neu→Alt pro unterstütztem DB-Profil. |
| [COMPAT-006](13-tests-kompatibilitaet-und-qualitaet.md) | P5 | Mehrdeutigkeits- und Migrationsfälle mit alten Metadaten. |
| [COMPAT-007](13-tests-kompatibilitaet-und-qualitaet.md) | P1/P4 | Gültiges Schema mit absichtlich begrenzter Projektion. |

### API – Gemeinsame API-Verträge

6 Anforderungen; Definitionen in [16-api-register-und-vertragstests.md](16-api-register-und-vertragstests.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [API-001](16-api-register-und-vertragstests.md) | P0/P1 | Register, Modulgraph und reale Imports stimmen überein. |
| [API-002](16-api-register-und-vertragstests.md) | P1 | PublicSdkConstructionTest gegen Artefakte. |
| [API-003](16-api-register-und-vertragstests.md) | P1 | Vertragstest-/Javadocreview. |
| [API-004](16-api-register-und-vertragstests.md) | P1 | Outcome-/CLI-/Bridge-Matrix. |
| [API-005](16-api-register-und-vertragstests.md) | P1 | Selection-/Ownershiptests. |
| [API-006](16-api-register-und-vertragstests.md) | P1 | Kein Drift nach API-Änderung. |

### CLI – Werkzeugoberfläche

7 Anforderungen; Definitionen in [18-cli-sdk-und-kompatibilitaetsadapter.md](18-cli-sdk-und-kompatibilitaetsadapter.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [CLI-001](18-cli-sdk-und-kompatibilitaetsadapter.md) | P1 | Fachlich identische normalisierte Resultate. |
| [CLI-002](18-cli-sdk-und-kompatibilitaetsadapter.md) | P1 | In-Process-Tests fangen Resultate und Streams ohne JVM-Ende auf. |
| [CLI-003](18-cli-sdk-und-kompatibilitaetsadapter.md) | P3 | Referenzmatrix; kein stilles Ignorieren. |
| [CLI-004](18-cli-sdk-und-kompatibilitaetsadapter.md) | P3 | Prozessvergleich auf zugesagten Plattformen. |
| [CLI-005](18-cli-sdk-und-kompatibilitaetsadapter.md) | P1/P3 | Konfliktfälle und Herkunftsreport. |
| [CLI-006](18-cli-sdk-und-kompatibilitaetsadapter.md) | P1 | XML-/JSON-Export bleibt parsebar; NDJSON ohne Fremdtext. |
| [CLI-007](18-cli-sdk-und-kompatibilitaetsadapter.md) | P1/P3 | IO-Fehler/Abbruch mit vorhandener Zieldatei. |

### SDK – Java-SDK und Modellgeneratoren

4 Anforderungen; Definitionen in [18-cli-sdk-und-kompatibilitaetsadapter.md](18-cli-sdk-und-kompatibilitaetsadapter.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [SDK-001](18-cli-sdk-und-kompatibilitaetsadapter.md) | P1 | Kompilierender Beispiel-/Vertragstest ohne Dateisystem. |
| [SDK-002](18-cli-sdk-und-kompatibilitaetsadapter.md) | P1/P3 | Beispielkompilierung in CI. |
| [SDK-003](18-cli-sdk-und-kompatibilitaetsadapter.md) | P3 | Versionsmanifest/Migrationsfall. |
| [SDK-004](18-cli-sdk-und-kompatibilitaetsadapter.md) | P3 | Formatgoldens, Unsupported-Fälle und Referenzvergleich je freigegebenem Format. |

### LEGACY – Optionaler alter Java-API-Adapter

2 Anforderungen; Definitionen in [18-cli-sdk-und-kompatibilitaetsadapter.md](18-cli-sdk-und-kompatibilitaetsadapter.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [LEGACY-001](18-cli-sdk-und-kompatibilitaetsadapter.md) | optional/P3 | Echte Verbraucher benutzen materialisierten Graphen. |
| [LEGACY-002](18-cli-sdk-und-kompatibilitaetsadapter.md) | optional/P3 | Mutations-/Isolationstest. |

<!-- REQUIREMENT-INDEX:END -->
