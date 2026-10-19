# Implementierungsplan und überprüfbare Arbeitspakete

## 1. Ausführungsprinzip

Die Arbeit folgt vertikalen fachlichen Abschnitten. Jeder Abschnitt enthält Modell/Syntax, Semantik, Diagnose, öffentliche Abfrage, relevante JVM-/WASM-Prüfung und Dokumentation. Ein klassisches Vorgehen „erst vollständiger Batchcompiler, dann irgendwann IDE und Browser“ erfüllt den Auftrag nicht.

Alle Pakete liefern Code, passende Tests, aktualisierte Anforderungszuordnung und einen dokumentierten Fortschrittsstand. Die Tabellen benennen die Mindestlieferung. Ein Agent darf intern kleinere Schritte wählen, aber nicht die Abnahme durch mehrere unvollständige Platzhalter ersetzen.

## 2. P0 Technische Grundlage

### WP-00 Repository und Gradle

**Eingang:** dieses Paket, leeres oder explizit zugewiesenes Repository. **Verträge:** BUILD-001 bis BUILD-004, AGENT-001/008/010, ARCH-001/009.

**Lieferung:** Gradle-Wrapper mit verifiziertem Pin, Groovy-DSL, Java 25-Toolchain, tatsächliche Startmodule model/compiler/cli/teavm, einfacher Testlauf und dokumentierte Laufumgebung. Spezifikationen unter `docs/spec/`, Referenzevidenz unter `docs/evidence/`. Kein aktives Maven-/Ant-Zweitbuild. Root-Lifecycle bleibt klein.

**Abnahme:** Frischer Checkout kompiliert eine minimale echte API-Anwendung, `check` führt mindestens einen aussagekräftigen Vertragstest aus, Versionen sind im Report vorhanden, Modulgraph ist azyklisch. Kein künstlicher `success`-Test als Ersatz für die nächsten Pakete. **Weiter:** WP-01 und WP-02 können nach stabilem Build unabhängig vorbereitet werden.

### WP-01 Referenzen und Fallkorpus

**Eingang:** WP-00. **Verträge:** TEST-/COMPAT-Anforderungen aus Kapitel 13, GOAL-012.

**Lieferung:** Gepinnte Referenzquellen/Compilerartefakte mit SHA-256, externe Compiler-Suite im bewusst ausgewählten 2.3/2.4-Scope, eigener kleiner Fixturekatalog, Manifest mit Herkunft/Version/Erwartung/Normbezug. Legacywerkzeuge laufen in isolierten Testadaptern. Die dokumentierten 610 Gesamtfälle der früheren Suite werden nicht als Zahl der unterstützten 2.3/2.4-Fälle übernommen.

**Abnahme:** Je ein positiver und negativer Fall läuft gegen ein Referenzwerkzeug; jeder Fall ist eindeutig zugeordnet. Bericht unterscheidet PASS/XFAIL/FAIL/XPASS/ERROR bzw. die konkret verwendeten äquivalenten Status. Keine ungeprüfte Massenübernahme alter XFAILs als eigene Erfolge.

### WP-02 Syntaxspike nach Gradle überführen

**Eingang:** WP-00 und beigefügte Syntaxevidenz. **Verträge:** BUILD-005/006/010, SYN-/WASM-Grundanforderungen.

**Lieferung:** Generator/Runtime 4.13.2, bestehende Grammatik mit Pin/Provenienz, Lexer-/Parserfehler, EOF-Prüfung, UTF-16-Positionskonversion. TeaVM-Gradle-Ausgaben für WasmGC und JS. Der bereits belegte Maven-Spike wird reproduziert und dann in den neuen Build integriert.

**Abnahme:** Dieselben kleinen Positiv-/Negativfälle laufen auf JVM und TeaVM; Runtimeclasspath enthält keinen ANTLR-Generator. Resultate sind maschinenlesbar verglichen. Der bekannte Warnhinweis zu `viewDef` wird reproduziert, fachlich bewertet und mit einem Test behandelt; er darf nicht allein durch globale Warnungsunterdrückung verschwinden.

### WP-03 Laufkontext und Diagnostikgrundlage

**Eingang:** WP-00. **Verträge:** DIAG-/LOG-Grundlagen, SRC-Positionen.

**Lieferung:** unveränderliche Diagnosewerte, erster Katalog, getrennte RunEvents, JSON-/Legacyrenderergrundlage, CancellationToken und typisierte Laufresultate. Die ersten Parserfehler verwenden echte Codes und Bereiche.

**Abnahme:** Gleiches Eingabeproblem erzeugt identische fachliche Daten auf JVM/WASM; Renderer tauscht nicht die Ursache aus; Beobachtungsausfall verdeckt keinen Compilerfehler. Gültig, abgelehnt, abgebrochen und intern fehlgeschlagen sind unterscheidbar.

## 3. P1 Durchgängiger semantischer Abschnitt

### Verbindlicher P1-Fallumfang

P1 implementiert einen begrenzten, aber durchgängigen Umfang: zwei referenzvalidierte Modelldateien mit Import, mindestens ein Topic, numerische/textuelle Domäne, Klasse, lokale Attribute, eine geerbte und überschriebene Eigenschaft soweit im Fixture normativ zulässig, Kardinalität, eine einfache Assoziation/Rolle und ein typisierter Constraint. Die Kernfälle existieren für 2.3 und 2.4; zusätzlich wird mindestens ein bewusst nur 2.4-zulässiger Fall geprüft. P1 darf für nicht implementierte Konstrukte explizite Coverage-Lücken melden.

Die exakten `.ili`-Texte sind in WP-01 anhand des Referenzcompilers zu validieren. Diese Beschreibung ist keine Erlaubnis, unzulässige Beispielsyntax oder Semantik zu erfinden. Für jede Kernfunktion gibt es mindestens einen gezielt negativen Fall.

### WP-10 Quellen, Syntax und Positionen

**Eingang:** WP-02/03. **Verträge:** Kapitel 04, SRC-/SYN-IDs.

**Klassenfokus:** SourceText, SourceRange, LineMap, SyntaxSnapshot, SyntaxService, ANTLR-Adapter, eigener SyntaxLowerer, RecoveryStrategy. **Lieferung:** lossless eigene Syntax, keine ANTLR-Typen in öffentlicher API, zuverlässige Versionserkennung und sichere Fehlerbereiche.

**Abnahme:** CRLF, Emojis, Kommentare und fehlende Tokens; Text und Bereiche stimmen exakt. Zusätzlicher Text nach Modellende wird erkannt. Ein beschädigtes Attribut vernichtet nicht die folgenden unabhängigen Deklarationen. Versionsspezifische Keywords werden korrekt behandelt.

### WP-11 Metamodell und Deklarationen

**Eingang:** WP-10. **Verträge:** Kapitel 05/06, META-/SEM-Grundlagen.

**Klassenfokus:** SemanticGraph, ModelSymbol, TopicSymbol, ClassSymbol, AttributeSymbol, DomainSymbol, Scope, Reference, TypeDescriptor, QueryResult, DeclarationIndexer. **Lieferung:** immutable Graph mit geordneten Deklarationen, Herkunft und eindeutigen IDs; keine Laufzeitcaches im Modell.

**Abnahme:** Alle P1-Deklarationen sind abfragbar; Duplikate haben eigene Bereiche und klare Diagnose. Modellnamenindex erzwingt keine unzulässigen Vorwärtsreferenzen. Keine unimplementierte Featurefamilie wird still in einen generischen „valid“-Knoten umgewandelt.

### WP-12 Imports und Bindung

**Eingang:** WP-11; Resolver-SPI kann parallel vorbereitet werden. **Verträge:** Kapitel 08, SEM-Bindung, RES-001 bis RES-008.

**Klassenfokus:** ModelResolutionService, SourceInventory, SourceSet, ImportBinding, ReferenceBinder, ScopeBuilder, ReferenceIndex. **Lieferung:** mehrere Modelle/Datei, explizite konkrete Importziele, aufgelöste Namenssegmente und stabile Herkunft. Erste Quellebeschaffung lokal/In-Memory, kein implizites HTTP in der Engine.

**Abnahme:** Positiver Import; fehlender Import; falsche Modellversion; Mehrdeutigkeit; geänderte/entfernte Abhängigkeit; Quelle erscheint später ohne Edit des Importierenden; stale ModelLocation wird abgewiesen.

### WP-13 Typprüfung und gültiges Schema

**Eingang:** WP-12. **Verträge:** Kapitel 05/06 und zukünftige Planverträge in Kapitel 12.

**Klassenfokus:** TypeResolver, InheritanceChecker, EffectiveMemberResolver, ExpressionTypeChecker, ConstraintChecker, CompilationGate, DefaultCompiledSchema. **Lieferung:** P1-Typen, fachlich korrekte Reihenfolgen, typisierte Constraints, Fehlerfortpflanzung mit Ursache, validierte Closure.

**Abnahme:** Typverträglichkeit, Vererbungszyklus, unbekannter Name, ungültiger Operand, korrekte Kardinalität/Reihenfolge; nach Fehler kein CompiledSchema. Legales 2.4-Late-Binding wird von ErrorType konzeptionell getrennt; soweit noch nicht implementiert bleibt es explizit nicht abgedeckt. Ein unabhängiger Workspacefehler blockiert die gültige Rootclosure nicht.

### WP-14 Workspace und erste Sprachdienste

**Eingang:** WP-10 bis WP-13. **Verträge:** Kapitel 07, INC-/LSP-IDs.

**Klassenfokus:** DefaultWorkspace, SourceStore, DependencyIndex, AnalysisCache, PublicationController, LanguageQueries, LspDocumentManager. **Lieferung:** SourceUpdate/applyBatch, Capture, Reuse, Abbruch, Dokumentversionsabbildung; Navigation/Hover/DocumentSymbols und erste Completion auf demselben AnalysisSnapshot.

**Abnahme:** Vollständige Editiersequenz einschliesslich ungültigem Zwischenstand; frische Analyse gleich inkrementellem Resultat; Kommentaränderung und semantische Änderung korrekt invalidiert; verspäteter Hintergrundlauf wird nicht veröffentlicht. LSP-Test transportiert echte Nachrichten, keine zweite Parserlogik im Client.

### WP-15 Diagrammprojektion

**Eingang:** WP-13/14. **Verträge:** Kapitel 11, VIZ-/ARCH-008.

**Klassenfokus:** DiagramProjector, DiagramDocument, DiagramNode, DiagramEdge, DiagramSourceAnchor, MermaidRenderer oder bestehender Adapter. **Lieferung:** ein sinnvoller Ausschnitt mit Klasse, Vererbung, Assoziation, Attributen und Quellnavigation. Der Projektor akzeptiert gültige und partielle Analysen mit erkennbaren Zuständen.

**Abnahme:** Graph-Golden unabhängig vom Layout; geerbte Elemente korrekt gekennzeichnet; ungelöste Beziehung erkennbar; kleine Textänderung erhält wiedererkennbare IDs soweit eindeutig. Ausgabeescaping ist geprüft. Ein ausgewählter vorhandener Renderer zeigt denselben Graph.

### WP-16 Semantik auf TeaVM und Web Image

**Eingang:** WP-13, später auch WP-14/15 in derselben P1-Abnahme. **Verträge:** Kapitel 10, alle für P1 markierten WASM-Anforderungen.

**Lieferung:** gemeinsame Bridge und Tests, TeaVM-WasmGC/JS, echte Browserprüfung; tatsächlich ausgeführter Web-Image-Vergleich mit dokumentierter Toolchain, Export-/Importanbindung und gemessenen Resultaten. Ein scheiternder Web-Image-Versuch wird bis zur konkreten Ursache untersucht. Die Entscheidung über den primären Auslieferungsweg erhält Messwerte und Kompatibilitätsdaten.

**Abnahme:** P1-Semantik auf JVM/TeaVM gleich; Chrome, Firefox und echtes Safari müssen die in Kapitel 10 festgelegte Produktionspfad-Matrix bestehen. Fehlende Browserumgebung hält G1 offen und gilt weder als Erfolg noch als Sprachfehler. Automatisiertes WebKit allein ist kein Safari-Nachweis. Browser kann ein Modell ändern und erneut analysieren; vollständiger Protokoll-/Unicode-/Präzisionsvertrag; Abbruchstrategie real geprüft. Web Image erhält PASS oder reproduzierbaren technischen Blocker mit Architekturfolgen. Ein ungeprüftes „sollte gehen“ erfüllt die Untersuchung nicht.

### WP-17 CLI und gemeinsame P1-Abnahme

**Eingang:** alle vorherigen P1-Pakete. **Verträge:** Kapitel 18, Kapitel 13, GOAL-002/007/008.

**Lieferung:** CLI für den P1-Umfang, lesbare und strukturierte Meldungen, Version-/Capabilities-Auskunft, Distribution und ein reproduzierbarer kompletter Beispieldurchlauf. First-run-Anleitung enthält konkrete Kommandos.

**Abnahme:** Ein neuer Nutzer kann Build, positive/negative Analyse, Browserdemo, Diagramm und Reports reproduzieren. Statusbericht bezeichnet den Teilumfang ausdrücklich. Referenzverhalten und Abweichungen sind dokumentiert. Keine zukünftigen Anforderungen wurden gestrichen, um den Meilenstein zu erfüllen.

## 4. P2 Vollständiger Compiler

P2 wird in fachlichen Featurefamilien umgesetzt. Jede Familie erweitert Parser, Modell, Semantik, Diagnostik, Queries und die relevanten JVM-/WASM-/Editorfälle gemeinsam. Sinnvolle Pakete sind:

| Paket | Fachlicher Inhalt | Besondere Abnahme |
|---|---|---|
| WP-20 | vollständige Typfamilien, numerische Genauigkeit, Einheiten, Geometrietypdefinitionen | Typgoldens und Grenzfälle ohne Geometrieengine im Compiler |
| WP-21 | Vererbung, Strukturen, Assoziationen, Rollen, effektive Transferordnung | Legacysemantik und reale Verbraucherprojektionen |
| WP-22 | Objektpfade, Funktionen, Constraints und Typkompatibilität | positive/negative Regeln, Ursachenunterdrückung, exakte Ausdrucksstruktur |
| WP-23 | Views und Ableitungen | resultierende Schemata und Referenzidentitäten |
| WP-24 | Übersetzungen, Metaattribute, externe Overlays | kanonische Identität vs. Transfername, Änderungen invalidieren Pläne |
| WP-25 | vollständige 2.4-Besonderheiten inklusive GENERIC/DEFERRED | legale Aufschiebung, unzulässige Verwendung und konkrete Bindungsbedingungen |
| WP-26 | Repositoryindex/HTTP/Offline-/Lockprofile | reproduzierbare Closure und IO-Fehlerverträge |
| WP-27 | vollständige Norm-/Feature-/Testsuitenbilanz | jede Regel zu Implementierung und Fällen zugeordnet, keine stille Coverage-Lücke |

Die Tabellen sind Arbeitsfamilien, nicht die vollständige Liste der INTERLIS-Normregeln. Die Detailmatrix in Kapitel 06 und die Normreferenzen steuern die Vollständigkeitsprüfung. Keine Familie darf allein durch vorhandene Klassennamen als erledigt gelten.

## 5. P3 Compilerersatz und Integration

WP-30 erfasst und implementiert das konkrete ili2c-CLI-Profil einschliesslich Optionen, Streams und Exitcodes. **Es gibt in P3 keine Modellgeneratoren und keine IlisMeta07/16-Exporter.** Stattdessen gehört eine deterministische Quellformatierung als öffentliche API und als CLI-Befehl `format` zum Umfang. Generatoroptionen ausserhalb des freigegebenen Profils werden ausdrücklich abgelehnt. WP-31 schliesst vollständige LSP-Fähigkeiten und den TypeScript-Diagrammadapter gegen gepinnte Browser-Schemas an. C++-/C-ABI-/iox-Adapter sind nicht P3; sie gehören zu P4 oder einem eigenen Adapterauftrag. WP-32 misst grosse reale Korpora auf Basis von `https://geo.so.ch/models`, führt die zugesagte JVM-/Browsermatrix aus und optimiert konkret festgestellte Engpässe. WP-33 bereitet SDK-/CLI-Releases mit Versions-/Profilangaben vor. Ein Legacy-Java-API-Adapter ist derzeit nicht Bestandteil von P3.

Freigabekriterium ist der freigegebene Verbraucherumfang. Ein ausgereifter neuer Compiler kann früh als neue Bibliothek veröffentlicht werden; die Bezeichnung als Drop-in für bestehende Produktionsabläufe verlangt die jeweilige Kompatibilitätsabnahme.

## 6. P4 Transfer und Validierung

WP-40 erstellt TransferPlan und modellgesteuerte Streamdeskriptoren. WP-41 implementiert XTF-Lesen/-Schreiben und IOM-/iox-Adapter im vereinbarten Umfang. WP-42 erstellt ValidationPlan und objektbezogene Ausführung. WP-43 ergänzt globale Regeln, Referenzauflösung, Geometrievalidierung und begrenzte Caches. WP-44 verifiziert ilivalidator-CLI-/Log-/Ergebnisprofile sowie grosse Korpora.

Compiler-Caches werden dabei nicht zu Validator-Laufzeitcaches umfunktioniert. Ein Objekt-/Single-Pass-Modus weist nicht ausgeführte globale Prüfungen aus und darf kein vollständiges „gültig“ melden. Die Geometrie- und Speicherziele werden anhand realer Fälle gemessen; ein RAM-Budget allein bestimmt keinen korrekten Streamingalgorithmus.

## 7. P5 Datenbanken

WP-50 inventarisiert reale ili2db-Mappings und Metadatentabellen. WP-51 implementiert reine SchemaPlan-Erzeugung mit ExistingSchemaMetadata und Profilen. WP-52 implementiert den ersten PostgreSQL/PostGIS-Dialekt und Ausführung. WP-53 prüft bidirektionale Weiterverwendung und Migrationen. WP-54 ergänzt weitere festgelegte Backends und Profile. Der PostgreSQL-Fokus priorisiert Arbeit, hebt die übergeordneten DB-Verträge aber nicht auf.

Erst die realen alten/neuen Werkzeuge auf derselben Datenbank beantworten die Drop-in-Frage. Keine „bereinigte“ Schemaerzeugung ersetzt diese Abnahme. DB-Schreiboperationen brauchen zudem konkrete Transaktions-/Rollback- und Fehlerverträge gemäss Kapitel 12.

## 8. Gates und Status

| Gate | Muss vorliegen | Unzulässiger Ersatz |
|---|---|---|
| G0 | reproduzierbarer Gradle-/Parser-/TeaVM-Build, Pins und erste Diagnostik | kopiertes historisches Maven-Erfolgsprotokoll |
| G1 | semantischer P1-Umfang, IDE-Zwischenstand, Chrome/Firefox/echtes Safari gemäss Kapitel 10, Diagramm, realer Web-Image-Versuch mit Ergebnis/ggf. begründetem Blocker | nur Parser, nur Node oder nur ClassSkeleton |
| G2 | vollständige 2.3/2.4-Feature-/Normmatrix und semantische Vergleichstests | Akzeptanzsuite allein |
| G3 | freigegebene CLI-/SDK-/Integrations- und Plattformprofile | allgemeine Behauptung „kompatibel“ |
| G4 | Transfer-/Validatorfälle und ehrlicher Vollständigkeitsstatus | Object-only-Modus als Vollvalidierung |
| G5 | DB-Verträge und alte/neue Kreuzverwendung | neuer Stack liest nur eigene Datenbank |

## 9. Noch auszuführende Entscheidungen mit klarer Fälligkeit

| Entscheidung | Fälligkeit | Verfahren |
|---|---|---|
| genauer Gradle 9.x-/JUnit-Patch | WP-00 | verfügbare stabile Version pinnen, reale Pluginintegration prüfen |
| korrigierte Parser-Recovery/Versionsstrategie | WP-02/WP-10 | Syntax-/Editor-/Normtests; kleine Grammatikänderungen mit Beleg |
| primärer WASM-Auslieferungsweg | WP-16 | Vergleichsmatrix, Browserresultate, Grösse/Speicher/Latenz |
| genaue Legacy-CLI-Referenzdistribution | WP-01/WP-30 | Artefaktversion, Hash und Fallprofil erfassen |
| Performancebudgets je Korpus | erste Messung WP-16, Freigabe WP-32 | Sollwerte getrennt von gemessenen Zahlen festlegen |
| zusätzliche DB-Backends und Profile | vor WP-50/54 | vorhandene Verwendungen inventarisieren, Abnahme je Profil |

Diese Punkte sind technische/fachliche Nachweise mit festem Arbeitsschritt. Sie sind kein Anlass, den Beginn von P0/P1 auf eine weitere allgemeine Architekturentscheidung zu verschieben.
