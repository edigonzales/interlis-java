# Abdeckungsmatrix, Anforderungsindex und Dokumentreview

P3-2 konkretisiert die vorhandenen INC-018/019/022 und LSP-001–016 durch
die öffentlichen Gegenproben aus Kapitel 13 §7 und die Host-/Queryverträge
in Kapitel 07 §5–6. Kapitel 05 §3.3 und das API-Register nennen den gemeinsamen
Scopeanker und `membersOf`; Completion, Rename und Tokens verwenden denselben
veröffentlichten Graphen. Dies ergänzt Abnahmebezüge, ohne Anforderungs-IDs,
Phasenpflichten oder G2-Norm-/Kompatibilitätsprofile umzunummerieren.

L4-1 konkretisiert SYN-001–012, SEM-001–019, INC-001–017 und die bestehenden
DIAG-/LOG-/API-Verträge durch den Einzelabgleich in
`testdata/g2/completion-links.json`. Die historischen Norm-/Grammatik-/Builtin-
IDs bleiben erhalten. AC4-01–03 verlangen tatsächliche öffentliche Assertions,
einen realen A3-Semantikvergleich und ausdrückliche Profilentscheidungen;
Quelllinks und erfolgreiche Integritätsprüfungen ersetzen kein unabhängiges
Review. Für äussere Typnamen und Herausgeber gelten die am30.10.2026 gewählten
Profile in Kapitel06/15. L4-2 prüft AC4-04–07 kumulativ einschliesslich T4.
Der operative Stand steht ausschliesslich in [STATUS](../g2/STATUS.md).

L1-3 ordnet AC1-05–07 den bestehenden META-001/003/004/008/011/014/016 und
SEM-002/003/005/007/008/009/010/011/017/019/020 zu: Vererbung, finale und
abstrakte Eigenschaften, gebundene Rollen-/Attributziele, vollständige
Besitzerpfade und fachliche Reihenfolge über Imports und Fresh/Reuse.
AC1-08 verlangt die kumulative AC1-01–07- sowie T1-/T2-/T3-Abnahme desselben
Kandidaten. Dieser Eintrag ist eine Vertragszuordnung, kein Prüfstatus.

L1-1 präzisiert die bestehenden META-/SEM-Verträge für die vordefinierten
INTERLIS-Modellowner, gewöhnliche Geometriestrukturen und das ausdrücklich
freigegebene TYPE-MODEL-Profil. Kanonische Verträge bleiben in Kapitel 05/06
und API-Register 16; dieser Hinweis behauptet keine unabhängige Abnahme.

L1-2 konkretisiert META-006/008/009/011/014/016 und
SEM-003/005/010/011/014/017/019 für numerische Unterteilungen, exakte Faktoren
und die drei ausdrücklichen Kompatibilitätsprofile. Kapitel 05/06/15/16 sind
die Vertragsquellen. Neue Regressionen und vorhandene Typfamilienprüfungen
werden in der L1-2-Abnahme zusammen bewertet; dieser Index ersetzt weder
deren Ausführung noch das unabhängige Urteil.

Der laufende G2-Abschluss folgt dem [Ausführungsplan](../g2/EXECUTION_PLAN.md);
der frühere [Vertrag A–I](../evidence/stabilization/g2-completion-plan.md) bleibt historische Evidenz.
Die versionierte G2-Regelmatrix trennt den vorhandenen Implementierungs-/Teststand
von der unabhängigen Abnahme. Ein älteres VERIFIED ist ohne neues Review kein
abgeschlossener Schritt dieses Auftrags. Anforderungen behalten ihre IDs und
P3-/P4-/P5-Pflichten; A2 erfasst fehlende Regel-/Nachweiszuordnungen ausdrücklich.

## 1. Zweck und Prüfstatus

Dieses Kapitel verbindet die ursprünglichen Ziele mit den konkreten Fachverträgen, Arbeitspaketen und Abnahmen. Es enthält den vollständigen Index der **289 eindeutig nummerierten Anforderungen** des Pakets. Die erläuternden Klassen-, Methoden-, Fachregel- und Testverträge in den jeweiligen Kapiteln gehören zusätzlich zur Spezifikation; ihre Verbindlichkeit hängt nicht davon ab, ob jeder einzelne Satz eine eigene ID besitzt.

Die hier dokumentierte Prüfung betrifft das **Spezifikationspaket**. Sie ist kein Nachweis, dass der neue Compiler oder die späteren Werkzeuge bereits implementiert sind. Die tatsächlich ausgeführte kleine Syntax-/WASM-Probe ist separat unter `evidence/wasm-spike/` dokumentiert. Ihr Umfang wird in Kapitel 10 ausdrücklich von den noch auszuführenden Compiler-, Browser- und Web-Image-Abnahmen abgegrenzt.

Die Kapitel 00 bis 18 sind die 19 Fach- und Steuerungsdokumente. Zusammen mit README und STARTPROMPT ergeben sie **21 Markdown-Hauptdokumente**. Hinzu kommen die Arbeitsregeln im Root-`AGENTS.md` und in `docs/spec/AGENTS.md`, insgesamt also **23 Markdown-Dateien zu Spezifikation und Agentensteuerung**. Das Gesamtpaket ergänzt reproduzierbare Referenzevidenz. Die zusätzlich angebotene zusammengeführte Markdown-Ausgabe enthält denselben Inhalt einschliesslich der beiden Arbeitsregeldateien und dient dem Lesen beziehungsweise der Verarbeitung als einzelnes Dokument.

## 2. Abdeckung der Nutzeranforderungen

| Ausgangsanforderung | Konkretisierung im Entwurf | Massgebliche Kapitel | Nachweis beziehungsweise Meilenstein |
|---|---|---|---|
| Java-Stack von Grund auf erneuern | Neue Modul- und Datenverträge; vorhandene Implementierungen als Referenzen für Verhalten und Fachsemantik | [01](01-zielbild-und-anforderungen.md), [02](02-architektur-und-modulgrenzen.md), [15](15-entscheidungen-risiken-und-quellen.md) | Architekturgrenzen ab G0/G1; Funktionsumfang bis G5 |
| Fähigkeiten von ili2c, iox-ili, ilivalidator und ili2db erhalten | Gemeinsames semantisches Modell; getrennte Compiler-, Transfer-, Validierungs- und DB-Dienste | [05](05-metamodell-und-typensystem.md), [12](12-transfer-validierung-und-datenbanken.md) | Compiler G2/G3, Transfer und Validierung G4, Datenbanken G5 |
| Mit dem Compiler beginnen und das Gesamtziel behalten | Vollständige P0/P1-Strecke, anschliessend fachliche P2-Familien; spätere Verbraucheranforderungen bleiben verbindlich | [00](00-auftrag-und-agentenregeln.md), [14](14-implementierungsplan-und-arbeitspakete.md), [STARTPROMPT](STARTPROMPT.md) | WP-00 bis WP-17 mit belegtem Status; keine leeren Zukunftsmodule |
| Benutzer sollen wenig Veränderung bemerken | Benannte CLI-/Diagnose-/Formatierungsprofile, gemessene Exitcodes und Streams, nachvollziehbare Abweichungen | [09](09-diagnostik-logging-und-telemetrie.md), [13](13-tests-kompatibilitaet-und-qualitaet.md), [18](18-cli-sdk-und-kompatibilitaetsadapter.md) | Reale Prozess- und Ausgabevergleiche in G3 |
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
| Formatierung und CLI-Erfolg | Die Format-API besitzt deterministische, idempotente Verträge; CLI-Erfolg entsteht erst nach vollständiger Ausgabe und Flush. Modellgeneratoren sind nicht P3. | Formatter-Vertragstests und CliCompletionAfterFormatTest |
| Gradle und Plattformgates | Node-Vertragstests, reale Browserprüfung und Web-Image-Untersuchung sind unterschiedliche Aufgaben mit eigenem Ergebnis | checkPortable, checkBrowser und phasenspezifisches releaseCheck |
| Agentenregeln und aktueller Auftrag | Rootregeln gelten für das Repository, zusätzliche Spezifikationsregeln für dessen Dokumentpflege; Lesen aktiviert keine Implementierung | Scope- und Hierarchiereview beider AGENTS-Dateien mit Kapitel 00 und STARTPROMPT |

Diese Aufzählung ist kein unveränderliches Inventar aller späteren privaten Hilfsklassen. Der Agent darf die interne Implementierung vereinfachen, muss dabei aber dieselben beobachtbaren fachlichen Verträge, Reihenfolgen, Fehler und Lebensdauern erhalten. Eine Umbenennung eines gemeinsamen öffentlichen Typs erfordert die gemeinsame Aktualisierung aller betroffenen Kapitel, Beispiele und Tests gemäss API-006.

## 4. Dokumentprüfung und Grenzen ihrer Aussage

Für die Auslieferung werden die folgenden Eigenschaften des Pakets geprüft:

- Alle 21 Hauptdokumente sind als UTF-8 lesbar und im Inhaltsverzeichnis aufgeführt beziehungsweise als Einstieg erkennbar.
- Die zwei zusätzlichen AGENTS-Dateien haben eindeutige Zielpfade und Zuständigkeiten. Ihre Regeln sind mit Kapitel 00, den Fachverträgen und dem ausdrücklich zu aktivierenden Startprompt vereinbar.
- Alle 289 Anforderungsdefinitionen haben eine eindeutige ID sowie ausgefüllte Phase und Abnahme. Verweise auf Anforderungs-IDs lösen auf vorhandene Definitionen auf.
- Relative Markdown-Dateiverweise zeigen auf vorhandene Dateien. Verweise auf die Syntaxevidenz bleiben nach der beschriebenen Übernahme nach `docs/spec/` und `docs/evidence/` gültig.
- Codeblöcke sind geschlossen. Die vollständigen JSON-Beispiele sind syntaktisch parsebar. Java-Signaturblöcke mit bewusst ausgelassenen Bodies oder Imports werden nicht als bereits kompilierter Produktcode ausgegeben.
- Das API-Register, die Fachkapitel und die Arbeitspakete verwenden die vereinbarten gemeinsamen Typen und die zuletzt beschriebenen Lifecycle-/Capture-/Gate-Verträge.
- Die ZIP-Ausgabe enthält die Hauptdokumente, beide AGENTS-Dateien und die Referenzevidenz; interne Erstellungsdateien und Uploadhilfen gehören nicht zum Lieferumfang. Die Archivintegrität wird separat geprüft.

Nicht durch einen Markdownreview nachweisbar sind Sprachkonformität, korrekte Recovery bei allen Fehlern, Threadingverhalten, Speicherverbrauch, Browserkompatibilität, reale Ausgabetreue oder Datenbankinteroperabilität. Dafür existieren die konkreten Implementierungsaufträge und Tests. Auch die hier vorgeschlagenen Ressourcenstartwerte sind keine gemessenen Leistungszusagen.

Die referenzierten Upstreamstände sind in Kapitel 15 festgehalten. Die Spezifikation bildet ihre ausgewerteten Eigenschaften und die daraus getroffenen Entwurfsentscheidungen ab. Sie behauptet keine erneute Ausführung sämtlicher fremder CI-Pipelines oder eine vollständige Prüfung aller Versionen dieser Projekte.

## 5. Pflege des Anforderungsnachweises im Repository

Der Coding-Agent führt den tatsächlichen Umsetzungsstatus ausserhalb dieses statischen Inhaltsindex nach den Regeln von Kapitel 00 und 13. Eine sinnvolle Statuszeile enthält Anforderungs-ID, Status, zugehöriges Arbeitspaket, Implementierungsstelle, konkrete Fälle, zuletzt ausgeführten Befehl und Ergebnisnachweis. Ein Anforderungsindex allein ist kein Erfüllungsnachweis.

Teilweise erfüllte Anforderungen mit mehreren Phasen bleiben als solche erkennbar. Beispielsweise kann die Overlaygrenze in P1 korrekt sein, während die vollständige Verbraucherplanung erst P4 liefert. Die Angabe `P1/P4` bedeutet nicht, dass nach dem ersten Teil sämtliche zukünftigen Verpflichtungen erledigt sind. `zurückgestellt (nicht P3)` bezeichnet den derzeit nicht verfolgten Legacy-Java-API-Adapter, nicht eine optionale allgemeine CLI-/Datenbankkompatibilität.

Bei einer neuen Anforderung wird eine neue freie ID ihres Bereichs vergeben. Bestehende IDs werden nicht für andere Bedeutungen wiederverwendet. Entfällt eine Anforderung aufgrund einer bewussten Scopeentscheidung, bleiben Entscheidung, Begründung und Auswirkungen nachverfolgbar. Ein Agent darf keinen offenen Nachweis durch Löschen der Anforderung oder Umdeuten eines Fehlerfalls in einen Erfolg beseitigen.

## 6. Vollständiger Anforderungsindex

Der Index gruppiert die 289 Anforderungen nach ihrem fachlichen Besitzer. Die verlinkten Fachkapitel enthalten jeweils den vollständigen normativen Text und die Methodenverträge. Die nachstehende Abnahmespalte ist eine lesbare Wiederholung des ursprünglichen Abnahmehinweises; im Fall einer späteren Änderung ist die Definition im Fachkapitel massgeblich und der Index wird synchronisiert. Die Einträge sind Anforderungen an die Umsetzung, keine Aussagen über bereits bestandene Tests.

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
| [META-006](05-metamodell-und-typensystem.md) | P2 | Katalogzeile besitzt Konstruktor-/Analyse-/Negativ-/Export- oder Querytest, auch für Referenzzielmengen, sprachversionsabhängige Identitätsregeln sowie gebundene Einheitenkomponenten und ihre Abstraktions-/Erweiterungsregeln (`ComposedUnitContractTest`). |
| [META-007](05-metamodell-und-typensystem.md) | P2 | Übersetzungsfixture verändert keine Referenzidentität. |
| [META-008](05-metamodell-und-typensystem.md) | P1/P2 | Goldenvergleich mit Legacyfällen für Vererbung, Überschreibung und eingebettete Rollen; `MandatoryOverrideTypeContractTest` prüft frühe Typbindung, unmittelbare Überschreibungsidentität und Fehlerpfade in 2.3/2.4. |
| [META-009](05-metamodell-und-typensystem.md) | P1 | Zahlen grösser 2^53, Skalen, Exponenten und Grenzen ohne Doubleverlust. |
| [META-010](05-metamodell-und-typensystem.md) | P1 | Fehler in unabhängiger Quelle blockiert nicht; Fehler in Importclosure blockiert. |
| [META-011](05-metamodell-und-typensystem.md) | P1 | Geerbtes Attribut führt auf Originaldeklaration zurück. |
| [META-012](05-metamodell-und-typensystem.md) | P1/P4 | Zwei unterschiedlich konfigurierte Verbraucher können dasselbe Schema parallel verwenden. |
| [META-013](05-metamodell-und-typensystem.md) | P1/P2 | Gültiger View wird nicht wegen fehlendem SQL-Exporter abgelehnt oder still verworfen. |
| [META-014](05-metamodell-und-typensystem.md) | P1/P2 | Exhaustive Variantenprüfung und sichtbare Coverage-Lücke; MANDATORY-Pfade verwenden denselben gebundenen Typ wie das veröffentlichte Attribut; importierte Domains und Ausdruckstypen bleiben unabhängig von der Quelldateisortierung (`ImportedDomainTypeContractTest`). |
| [META-015](05-metamodell-und-typensystem.md) | P1/P2 | Parallelkompilierung beider Versionen beeinflusst keine andere Sitzung. |
| [META-016](05-metamodell-und-typensystem.md) | P1/P2 | Unzulässige Zuweisung liefert Grund und betroffene Teiltypen; Referenzrestriktionen werden als ganze Zielmenge geprüft. |
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
| [SEM-008](06-kompilierung-und-semantik.md) | P1/P2 | Ziel vorhanden im globalen Index, aber im fraglichen Scope unzulässig: Diagnose, einschliesslich fehlender direkter/geerbter Topic-Abhängigkeit bei Referenzzielmengen. |
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
| [COMPAT-004](13-tests-kompatibilitaet-und-qualitaet.md) | zurückgestellt (nicht P3/P4) | Eigener Legacy-Adapterauftrag; gepinnte Verbraucher erforderlich. |
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
| [CLI-006](18-cli-sdk-und-kompatibilitaetsadapter.md) | P1 | Formatierte Quellausgaben und Diagnostik bleiben trennbar; NDJSON ohne Fremdtext. |
| [CLI-007](18-cli-sdk-und-kompatibilitaetsadapter.md) | P1/P3 | IO-Fehler/Abbruch mit vorhandener Zieldatei. |

### SDK – Java-SDK

4 Anforderungen; Definitionen in [18-cli-sdk-und-kompatibilitaetsadapter.md](18-cli-sdk-und-kompatibilitaetsadapter.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [SDK-001](18-cli-sdk-und-kompatibilitaetsadapter.md) | P1 | Kompilierender Beispiel-/Vertragstest ohne Dateisystem. |
| [SDK-002](18-cli-sdk-und-kompatibilitaetsadapter.md) | P1/P3 | Beispielkompilierung in CI. |
| [SDK-003](18-cli-sdk-und-kompatibilitaetsadapter.md) | P3 | Versionsmanifest/Migrationsfall. |
| [SDK-004](18-cli-sdk-und-kompatibilitaetsadapter.md) | entfällt (P3-Scope) | Keine Generator-API; durch FMT-001..004 ersetzt. |

### FMT – Quellformatierung

4 Anforderungen; Definitionen in [18-cli-sdk-und-kompatibilitaetsadapter.md](18-cli-sdk-und-kompatibilitaetsadapter.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [FMT-001](18-cli-sdk-und-kompatibilitaetsadapter.md) | P3 | Deterministische, idempotente API ohne IO. |
| [FMT-002](18-cli-sdk-und-kompatibilitaetsadapter.md) | P3 | Kommentare/Strings/Unicode/Semantik erhalten; Fehler ohne Teilprodukt. |
| [FMT-003](18-cli-sdk-und-kompatibilitaetsadapter.md) | P3 | stdout/`--write`/`--check`/Exitcodes. |
| [FMT-004](18-cli-sdk-und-kompatibilitaetsadapter.md) | P3 | Atomisches Schreiben; vorhandene Datei bleibt bei Fehler unverändert. |

### LEGACY – Zurückgestellter alter Java-API-Adapter

2 Anforderungen; Definitionen in [18-cli-sdk-und-kompatibilitaetsadapter.md](18-cli-sdk-und-kompatibilitaetsadapter.md).

| ID | Phase | Abnahmehinweis |
|---|---|---|
| [LEGACY-001](18-cli-sdk-und-kompatibilitaetsadapter.md) | zurückgestellt (nicht P3) | Eigener Auftrag mit echten Verbrauchertests. |
| [LEGACY-002](18-cli-sdk-und-kompatibilitaetsadapter.md) | zurückgestellt (nicht P3) | Gilt erst bei Wiederaufnahme des Adapterauftrags. |

<!-- REQUIREMENT-INDEX:END -->

### Laufender View-Ausbau A–D

`ViewBindingContractTest` ergänzt die Nachweise für META-006/008/011/014,
SEM-008/010 und SYN-013/015: tatsächliche View-Mitglieder, Ableitungsausdrücke
im gewählten Schema, geordnete Basen, Selektionen und gezielte Fehlerpfade.
Der aktuelle [Lieferstatus](../evidence/stabilization/conformance-deliveries.md)
trennt Implementierung und ausgeführte Plattformnachweise. Die weiterhin
unvollständige Inspektions-/Deklarationsabdeckung bleibt offen; die Ergänzung
ändert keine P2-/P3-Abnahme.

`InspectionFactorContractTest` ergänzt WP-22/23, META-006/008/011/014,
SEM-008/010/014, SYN-013/015 und TEST-011/012 um gebundene Inspektionsfaktoren,
OF-Einschränkungen, negative Kontextfälle und identitätsbezogene
Übersetzungsgegenfälle. Geometrie-/Segmenthierarchie und die vollständige
B1–B3-Abnahme bleiben ausdrücklich offen.

`ViewInheritanceTranslationContractTest` ergänzt META-006/008/011/014,
SEM-008/010/014 sowie TEST-011/012: View-Überschreibung mit geerbten
Ableitungsalternativen, MANDATORY/TRANSIENT/Domainidentität, effektiver
Vererbungspfad und Übersetzungsziele einschliesslich AGGREGATES. Die
Originalmodell-Closure wird mitgeprüft. Zwei Workspace-Verträge ergänzen
RES-003/006 um Vorbereitung der Originalimporte und später hinzugefügte
Originale. Der Nachweis ist ein Teil von B1–B3;
P2/P3 bleiben offen. Siehe [Fortsetzung](../evidence/stabilization/view-inheritance-translations.md).


Konkretisierung TEST-011/012: Normative Referenzausnahmen besitzen eigene gepinnte
Erwartungen und positive Gegenproben; der Wrapper prüft JSON-Diagnosen,
Prozessabschluss und Vollständigkeit. Rohe Referenzresultate und normative
Bewertung bleiben getrennt (Kapitel 13). Dies ändert keine P2-/P3-Abnahmegrenze.


K2 konkretisiert WP-22/24/25; META-003/011/014, SEM-005/009/010/014/017,
SYN-004/013/015 und TEST-011/012: typisierte Laufzeitparameter, Klassenfunktionen,
getrennte Namespaces, gebundene Attributpfadtypen und Konstanten. Nachweise:
`ParameterClassFunctionContractTest` und [K2](../evidence/stabilization/k2-parameters-class-functions.md).
Offene Signatur-/METAOBJECT-Parameter und vollständige abhängige
Attributpfadargumente werden damit nicht als erledigt eingestuft.


K3 konkretisiert WP-24/25; META-003/011/014, SEM-010/014/017/019,
SYN-004/013/015 und TEST-011/012 mit strukturierten CONTEXT-Zuordnungen,
identitätsgebundenem GENERIC/DEFERRED und öffentlichen Topic-Bindungen.
`GenericContextContractTest` prüft Imports, Alternativen, Vererbung,
Übersetzungsziele, Quellreihenfolge und Wiederverwendung. P3 bleibt offen.

K4/Linienformen konkretisiert WP-20/24/25; META-003/006/008/011/014/015,
SEM-010/014/017, SYN-004/013/015, TEST-011/012 und VIZ-001/008:
strukturierte Deklarationen, gebundene Segmentziele und Geometriekomponenten,
versionsgerechter Kontrakt, Übersetzungsidentitäten und Quellqueries.
`LineFormBindingContractTest`, `GeometryTypeContractTest` und
`DiagramProjectionTest` prüfen den Teilumfang. Metadaten, Achsenreferenzen und
vollständige Inspektionsstrukturen bleiben innerhalb K4 offen; P3 bleibt offen.


K4/Metadaten und Inspektionen konkretisiert WP-20/23/24/25;
META-003/006/008/010/011/014, SEM-005/010/014/017, SYN-004/013/015,
TEST-011/012 und VIZ-001/008: gebundene Baskets, Metaobjekte, Achsen,
Unit-Parameter, Übersetzungen und Standard-Geometrieinspektionen. Die
Verträge stehen in Kapitel 04/05/11/16, Tests in `MetadataBindingContractTest`,
`GeometryInspectionContractTest` und `DiagramProjectionTest`. Eigene
Inspektionssegmente, SIGN/GRAPHIC und übrige P3-Pflichten bleiben offen.

K5 konkretisiert WP-20/23/24/25; META-003/006/008/010/011/014,
SEM-005/010/014/017, SYN-004/013/015, TEST-011/012 und VIZ-001/008:
strukturierte Grafiken, gebundene Signaturparameter und Zuweisungen,
Selektions-/Rollenpfade, Vererbung und Übersetzungen. Der unveränderte
`roadsExgm2ien`-Fall und seine OID-Kontrollfassung werden getrennt bewertet;
Nachweise und Grenzen stehen im [K5-Nachweis](../evidence/stabilization/k5-graphics.md).
Dies ersetzt keine vollständige P3-Abnahme; K6 und unabhängige Sprachfunde
bleiben offen.

K6 konkretisiert WP-20/22/25; META-003/006/009/011/014/016,
SEM-005/010/014 und TEST-011/012: exakte geordnete Umrechnungsfaktoren,
Nullfaktor-/Nullteilerdiagnosen, gebundene skalare Einheitenvererbung und
öffentliche Erweiterungsqueries. Verträge stehen in Kapitel 04/05/16,
Nachweise in `NumericUnitConversionContractTest` und der portablen Matrix.
Der [K6-Nachweis](../evidence/stabilization/k6-units.md) beschreibt den tatsächlich
geprüften Fallumfang und verbleibende Grenzen. P2/P3 bleiben bis zur gesamten
Abnahme offen.

### G2 C: Geometrietoleranz und eigene Inspektionssegmente

WP-22/24/25 konkretisiert META-003/006/009/014, SEM-005/008/010/014/017
und TEST-011/012. Kapitel 05 besitzt den effektiven Geometrievertrag und
`GeometryType.Tolerance`; Kapitel 06 die gemeinsame Bindung/Prüfung, Kapitel 16
die API-Zuordnung. `GeometryRulesContractTest`, `GeometryInspectionContractTest`
und `GeometryOriginalAcceptanceTest` prüfen Präzision, Vollständigkeit,
Vererbung, öffentliche Typrelationen, Kontextstrukturen und unveränderte
Katalogquellen. Dies ist keine Vollständigkeitszusage für alle G2-Regeln.
Aktuelle Ausführung und die separat offene 2.3-Toleranzfrage stehen in
`docs/evidence/stabilization/g2-rule-matrix.json` und im Implementierungsstatus.


### G2 D: Signatur-Verantwortung

WP-24/25, META-003/006/010/014, SEM-005/010/014/017 und TEST-011/012:
Die Verantwortungsregel und geerbte Parametertypen stehen in Kapitel 05/06,
die unveränderten API-Namen in Kapitel 16. Original- und Modell-/Querynachweise
stehen in `GraphicResponsibilityContractTest`; aktuelle Ausführung und
verbleibende G2-Abnahmen im Implementierungsstatus und in der G2-Regelmatrix.

### G2 E1: Namensbindung und einfache vordefinierte Domänen

WP-21/22/24/27, SYN-013, META-003/005/006/011/014/015 und
SEM-002/005/010/014: drei typisierte einfache INTERLIS-Domänen, FINAL,
Quellfehler statt Ersatzdomäne, vollständige qualifizierte Namen und
öffentliche Query-/Editornachweise. Verträge stehen in Kapitel 05/06/16;
`PredefinedBindingContractTest` und portable Fälle prüfen diesen Teilumfang.
Die übrigen E1–E5-Regeln bleiben einzeln in der G2-Regelmatrix offen.

### G2 E1: Funktionsprofil und METAOBJECT_TRANSLATION

WP-21/22/24/27; SYN-004/005/006/010/013/015, META-003/006/008/011/015,
SEM-005/010/014, TEST-011/012: versionsgerechte leere Deklarations-/Aufruflisten,
Recovery und Fragmente sowie die vordefinierte Übersetzungsklasse mit zwei
Pflichtattributen, getrennten UNIQUE-Regeln, gebundenen Pfaden, Herkunft,
Reihenfolge, Imports/Vererbung/Übersetzungen und öffentlichen Queries.
Verträge stehen in Kapitel 04/05/06/16; `E1FunctionMetadataContractTest`
sichert den Teilumfang. Die übrigen E1–E5-Pflichten bleiben offen.

E1-Modellkopfoptionen konkretisieren SYN-004/013/015, META-003/006/011/014/015/017,
SEM-005/010/014/017 und TEST-011/012: Originalwerte/Herkunft, Defaults,
Versionsprofile, IANA-Registerbindung, kanonischer Transfernamensraum und
Übersetzungsvergleich; Verträge 04/05/06/16 und ModelHeaderContractTest.

E1-Profile (SYN-004/013/015, SEM-005/010/014, META-003/014): DATE,
TIMEOFDAY und DATETIME sind 2.4-Syntax; die XML-Domänen bleiben in beiden
Versionen verfügbar. TYPE MODEL enthält die vorgesehenen Deklarationsfamilien;
SYMBOLOGY benötigt nur in 2.3 CONTRACTED. Dokumentarische Familienempfehlungen
für REFSYSTEM/SYMBOLOGY werden nicht als zusätzliche Quellfehler erzwungen.
Wiederholte Properties liefern ILI-SYN-0007 am zweiten Token; Warnungsablehnung
verwendet das gemeinsame Gate. EXTENDED und explizites EXTENDS am selben
Klassen-/Strukturkopf sind unzulässig. Fehlende Attributtypen nach Parser-Recovery
behalten die ursprüngliche Syntaxursache als ErrorType; sie erzeugen keine
Implementierungssperre. E1ProfileSafetyContractTest und ModelKindContractTest
prüfen diese Regeln; dies ist keine pauschale E1-/G2-Abnahme.

E2 konkretisiert META-003/006/009/014 und SEM-005/010/014 gemäss Kapitel 05/06:
DomainSymbol.mandatory(), gebundene CLASS-Domänen, unverfälschte fehlerhafte
NumericTypeSyntax und QueryResult<UnitRelation> mit NOT_A_UNIT. Verbraucher
verwenden die gemeinsame Typrelation. UnitRelationContractTest und
DomainRestrictionContractTest prüfen Modell, Queries, Fehlursachen und Edits.
Die weiterführende Normabnahme bleibt in g2-rule-matrix.json ausdrücklich offen.

E2-Präzision ist versionsabhängig: 2.3 §2.8.5 S.42 erlaubt Verfeinerung,
sofern gerundete Grenzen im Basisbereich bleiben (7.9949 ja, 7.9999 nein bei
Basismaximum 7.99). 2.4 §3.8.5 S.53 verlangt unveränderte Präzision.
`TypeRelations.canExtend(derived, base, context, language)` erhält daher einen
expliziten nullfreien LanguageVersion-Parameter; weder Quellnamen noch ein
zufällig gefundenes Modell bestimmen das Profil. Interne Verbraucher und
Vorab-API-0.2-Tests werden direkt angepasst, ohne alte Überladung.
Die 2.3-Rundung verwendet exakte Dezimalwerte und HALF_UP; Tie-Details bleiben
in der Normprüfung separat sichtbar. Allgemeine Zuweisung ist unverändert.

E2-TEXT ergänzt ohne öffentliche Signaturänderung die Literal-Längenregel aus
Kapitel 05/06 (META-003/006/014, SEM-005/010/014, TEST-011/012). Bindung und
öffentliche Typabfragen verwenden denselben Literaltyp; Quelltext, Identitäten
und UTF-16-Bereiche werden nicht normalisiert. Geprüft durch TextLengthsTest
und TextLiteralContractTest sowie die gepinnten Normalisierungseingaben.

E3 (META-003/006/008/010/014, SEM-005/008/010/014/017, SYN-004/013/015,
TEST-011/012) ergänzt StructureValueTypeSyntax in guru.interlis.syntax und
TYPE_STRUCTURE_VALUE. StructureValueType.target() ist optional; restrictions()
und targetRoots() erhalten gebundene Identitäten. Kapitel 04/05/06 definiert
Nullfreiheit, Reihenfolge, Quell-/Snapshotbezug, Funktionskontext und Fehler.
StructureValueContractTest, die bisherigen View-/Inspektionsverträge und
DomainRestrictionContractTest prüfen Verbraucher und lange Ketten.

E2-Regelverfeinerung (META-003/006/014, SEM-005/010/014, TEST-011/012):
Gemeinsame Domänen-/Attribut-/Query-Erweiterung; numerische Collectionelemente;
Koordinatendimensionen und vererbte Richtungs-/Referenzidentitäten. TEXT-NFD-Länge
gilt in 2.3 und 2.4. Einzelregeln/Nachweise stehen in der G2-Matrix; daraus folgt
keine vollständige E2- oder G2-Abnahme.

E1/E4-Regelverfeinerung: EOF und vollständige Relationsabbildung (SYN-004/013/015),
explizite Enumformale/-baumwerte sowie gemeinsame Operatorregeln und vollständige
Enum-/Geometrie-Standardsignaturen (META-003/006/008/010/014, SEM-005/010/014/017,
TEST-011/012). Einzelregeln werden in der G2-Matrix belegt; convertUnit und
areAreas-Kontexte sowie übrige E1–E5-Pflichten bleiben offen.

E4-Vertragspräzisierung zu SYN-004/013/015, SRC-002–005, META-006/011/014 und
SEM-005/010/014/020: strukturierte Einheiten an Zahlenkonstanten und zusätzliche
Funktionsargumentpfade gemäss Kapitel 04–06/16. Der tatsächliche Abnahmestand
bleibt in der G2-Regelmatrix und im Implementierungsstatus; die Präzisierung
schliesst keine noch ungeprüften Funktionskontexte.

E1/E4 zu META-006/014 und SEM-005/010: Definiertheit der Funktionsparameter und
Rückgabewerte gemäss Kapitel 04–06/16. Reine Typrelationen und erforderliche
Deklarationen bleiben unterscheidbar. Abnahme gemäss aktueller G2-Regelmatrix.

E2 zu META-006/014 und SEM-005/010/014: Gemeinsame FORMAT-Relationen und
ErrorType-Veröffentlichung gemäss Kapitel 05/06/16. Implizite Komponentengrenzen,
gebundene Layoutpräfixe und Attributüberschreibungen werden gemeinsam geprüft.

E2 zu META-003/006/014 und SEM-005/010/014: Vollständigkeit effektiver numerischer
und Koordinaten-Deklarationen sowie Fehler expliziter Zielbindungen gemäss
Kapitel 05/06/16; Funktions- und GENERIC-Ausnahmen bleiben getrennt.

E1 zu SYN-002/004/013 und META-015: Vollständige reservierte Worttabellen,
kontextabhängige Wörter, Fragment-/Rename-Parität und versionsgebundene Boolean-
Herkunft gemäss Kapitel 04–06/16. Buildnachweis für Tokenvokabular in Kapitel 03.

E5 zu META-006/010/014/017 und SEM-010/014/017: Strukturierte Quell-Metaattribute,
Dokumentation, Funktionserläuterungen und deklarationsgebundener CRS-Vergleich
gemäss Kapitel 04–06/16. Externe Overlays bleiben gesonderte F-Pflicht.

E5-Nachweis zu SEM-010/014/017, META-006/010/014 und TEST-011/012:
`StructuredTranslationContractTest` und portable Übersetzungsgegenfälle prüfen
den gebundenen Strukturvergleich statt eines Volltext-/Namensfallbacks. Dies
belegt die dort einzeln aufgeführten Regeln, keine pauschale G2-Abnahme.

E1-Nachweis zu META-003/006/014/015, SEM-005/008/010/014 und TEST-011/012:
`PredefinedOidIdentityContractTest` prüft versionsgebundene OID-Identitäten,
Herkunft/Basen/Modifikatoren, Navigationsziele und Import-/Wiederverwendungsfälle.
Dies schliesst die vollständige vordefinierte Modellabnahme nicht automatisch ab.

E2 Collection-Elementbindung (META-006/014, SEM-005/010/014, TEST-011/012):
2.4 verwendet für einfache benannte Elemente die vorhandene Domain-/Struktur-
bindung. 2.3 bleibt strukturgebunden; ANYSTRUCTURE und RESTRICTION bleiben
versionsunabhängig strukturspezifisch. Nachweise und offene Ursachen stehen in
NamedCollectionElementContractTest und der versionierten G2-Regelmatrix.

E5 geometrische Kontextkorrespondenzen (META-007/010/014, SEM-005/008/010/014/017,
TEST-011/012): Kontextualisierte Segmente und Inspektionsstrukturen werden nur
nach gebundenem Pfad-/Geometrievergleich zugeordnet und anschliessend regulär
verglichen. GeometryTranslationContextContractTest prüft öffentliche Links,
Fehlerziele, exakte Toleranzen, Flächen, eigene Segmente und isolierte Vertexkontexte.

E1 vordefinierte Namensbindung (META-006/014/015, SEM-005/008/010/014,
TEST-011/012): gemeinsame Binder für vorhandene vordefinierte Inhalte,
UNQUALIFIED-Importprofile und echte Mehrdeutigkeit. PredefinedNameBindingContractTest
prüft Zielidentität, Quellbereiche, Revisionen und Wiederverwendung. Vollständige
vordefinierte Modellinhalte und die allgemeine Namenskategorienprüfung bleiben
getrennte G2-Regeln.

E2 skalierte Typgrenzen (SYN-004/013/015, META-006/014, SEM-005/010/014,
TEST-011/012): ScaledBoundaryContractTest prüft Mantissen einschliesslich Null,
Vorzeichen, Präzision, öffentliche Typwerte, exakte UTF-16-Diagnosebereiche,
Fragmente, importierte Grenzen und Fresh/Reuse-Editfolgen. Exponentenrelation und
allgemeines Konstantenprofil bleiben separate offene Normregeln.

E4 direkte Constraintnamen (META-006/011/014, SEM-005/008/010/014, TEST-011/012):
ConstraintNameContractTest prüft eindeutige Namen über direkte Constraintfamilien
und same-topic CONSTRAINTS OF, zweite Namensbereiche und öffentliche Queries.
Vererbte Namensräume bleiben im Fallkatalog ausdrücklich offen.

L0B-3-Kompatibilität zu META-006/010/014/017: Die Verträge in Kapitel 04/05/16
erhalten fortgesetzte Kommentarstrings mit Original-UTF-16-Bereichen und alle
Vorkommen von `fn.param` an Funktionen. Bestehende Einzelwertkonflikte bleiben
Fehler. Diese Konkretisierung fügt keine neue Anforderungs-ID hinzu.

L2-2 konkretisiert WP-23, META-003/006/011/014, SEM-005/008/010/014/017/019
und TEST-011/012 gemäss Kapitel 05/06: öffentliche gebundene Viewformationen,
effektive Reihenfolge, vererbter AREA-/UNION-Ausschluss für ALL OF sowie
versionsgerechte Rollenübernahme und nachfolgende Teilnehmernavigation.
`ViewFoundationContractTest` und der gemeinsame portable Vertrag ergänzen
die bestehenden View-/Inspektions-/Übersetzungsprüfungen.

L2-3 konkretisiert WP-24/25 und die kumulative Phase2, META-003/006/007/010/011/014,
SEM-005/006/008/010/011/014/017/019 sowie TEST-002/003/009/011/012. Kapitel05/06
bleiben Vertragsquelle: tatsächliche übersetzte Importziele, Korrespondenzen und
Reihenfolge, getrennte gleichnamige Generic-Bindungen, Quellenabstraktion vor
Inspektionskonkretisierung und gemeinsame geerbte Namensauflösung.
`Phase2ClosureContractTest` ergänzt die bestehenden Translation-/Generic-/Metadaten-/
Linienform-/Grafikverträge; das Phasenurteil verlangt frische volle T1/T2/T3-Nachweise.

L3-1 (AC3-01/02, META-012, SEM-016, INC-007/016, TEST-006/007/014):
reale unveränderliche SemanticOverlays mit Zielbindung, Präzedenz, Konflikt-/
Mehrdeutigkeitsfehlern, Herkunft und öffentlicher Schemawirkung. Inhaltsbasierte
Fingerprints, isolierte Closure-/Importzustände, Fresh/Reuse und verspätete
Analyseannahme. Kapitel05/06/08/09/10/16 bleiben die Verträge;
SemanticOverlayContractTest und OverlayWorkspaceContractTest sowie die gemeinsame
portable Strecke prüfen Wirkung statt Fingerprint-Platzhaltern.

L3-2 konkretisiert RES-001–RES-012 sowie die bestehenden Workspace-/CLI- und
Portabilitätsverträge durch die APIs und Abnahmen aus Kapitel 08 §11, 07, 10,
16 und 18. Die operative Abnahme erfolgt kumulativ für AC3-01–AC3-06; die neuen
Closure-/Pin-/Lock-/Originalbindungsassertions sind keine pauschale Freigabe der
weiterhin `CANDIDATE_ONLY` markierten Normmatrix oder der G2-Phase4.

## P3-1 – additive CLI-Verträge

CLI-001–007, COMPAT-001–003 und LOG-005 verweisen für Kommandoschema,
Konfigurationspräzedenz, tatsächliches ili2c-Prüfprofil und Eltern-/Kindläufe auf
Kapitel 18 sowie das API-Register in Kapitel 16. FMT-001–004 bewahren Werte
innerhalb mehrzeiliger Literale/Metadaten bei expliziter Zeilenendenwahl mit
einem Hinweis; gewöhnliche Kommentarzeilenenden werden normalisiert.
`OutputPlanner`, `CliOutputWriter` und die abgeschlossenen Dateiartefakte regeln
Hostausgaben, keine zusätzliche Compilersemantik. Abnahme: CLI-/Formatter-
Vertragstests, echte Referenz-/Ersatzprozesse und Plattformprüfung gemäss
Kapitel 03/13/14. Der operative Nachweisstand steht in `docs/p3/STATUS.md`.
