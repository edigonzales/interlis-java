# INTERLIS Java Spezifikation und Anforderungskatalog

**Version 1.0 · 18. Oktober 2026 · Arbeitsname `interlis-java` · Java 25 · Gradle/Groovy**

Dieses Paket spezifiziert einen neuen Java-INTERLIS-Stack für die Fähigkeiten von ili2c, iox-ili, ilivalidator und ili2db. Die unmittelbare Implementierung beginnt beim Compiler. Die Dokumente enthalten das Gesamtziel, verbindliche Anforderungen, Klassen- und Methodenverträge, Fehler-/Zustandsmodelle, konkrete Agentenaufträge und überprüfbare Abnahmen.

Der Compiler wird als gemeinsame Bibliothek für Batch, IDE/LSP, Visualisierung und Browserausführung entwickelt. Sein Metamodell und seine geprüfte Schema-Sicht bilden später die Grundlage für Transfer, Validierung und kompatible Datenbankabbildung. Die Datenbankverträge werden nicht zugunsten einer intern bequemeren Abbildung aufgegeben.

## 1. Schnell beginnen

1. Die `AGENTS.md` aus dem Paketwurzelverzeichnis als `AGENTS.md` ins Repository-Root übernehmen.
2. Dieses Verzeichnis `spec/` einschliesslich seiner eigenen [AGENTS.md](AGENTS.md) nach `docs/spec/` übernehmen.
3. Das beigefügte `evidence/` nach `docs/evidence/` übernehmen. Relative Evidenzverweise bleiben dadurch gültig.
4. Für die Erstimplementierung dem Coding-Agenten [STARTPROMPT.md](STARTPROMPT.md) als ausdrücklichen Arbeitsauftrag geben.
5. Mit P0 und P1 aus [Implementierungsplan](14-implementierungsplan-und-arbeitspakete.md) arbeiten. Der Agent aktualisiert Status und Nachweise im Repository.

Die Spezifikation allein benötigt kein Build-Tool. Sie ist eine Dokumentlieferung; ein neues GitHub-Repository oder der hier beschriebene Compiler wurde damit noch nicht erstellt. Der beigefügte historische Syntaxspike ist tatsächlich ausgeführte Evidenz und wird im neuen Projekt auf Gradle überführt.

### 1.1 Zwei Arbeitsregeldateien

Die Root-`AGENTS.md` gilt für die Arbeit im gesamten Repository und enthält Einstieg, Architekturgrenzen, Build-/Testregeln und den Umgang mit dem aktuellen Auftrag. `docs/spec/AGENTS.md` ergänzt Regeln für Änderungen an der Spezifikation: stabile Anforderungs-IDs, eindeutige Vertragsquellen, gemeinsame API-Änderungen und ehrliche Nachweise. Die Rootdatei verlangt ausdrücklich, diese Zusatzregeln vor Änderungen an der Spezifikation zu lesen. Regeln für Java-Code sind deshalb bereits im Root verankert.

Beide Dateien verweisen auf die Fachkapitel, statt deren Signaturkataloge zu kopieren. Die 21 Hauptdokumente werden durch diese zwei Arbeitsregeldateien ergänzt; das Paket enthält damit 23 Markdown-Dateien zu Spezifikation und Agentensteuerung. Das Lesen einer Datei aktiviert keinen Implementierungsauftrag. Bei Review, Dokumentpflege oder einem gezielten Fix gilt der tatsächlich erteilte Umfang.

## 2. Verbindliche Grundentscheidungen

- Repository-Arbeitsname `interlis-java`, Maven-/Gradle-Group `guru.interlis`, Java-Pakete `guru.interlis.*`.
- Java 25 ohne Preview; Gradle-Wrapper und Groovy-DSL als einziger aktiver Build.
- Nur INTERLIS 2.3 und 2.4; vollständige Sprachsemantik ist P2-Ziel.
- ANTLR 4 als internes Parserwerkzeug; keine ANTLR-Typen in der öffentlichen API.
- `SyntaxSnapshot`, `AnalysisSnapshot` und `CompiledSchema` als getrennte Nutzungssichten.
- Unveränderliche veröffentlichte Daten, explizite Revisionen, überprüfbare Wiederverwendung.
- Diagnosen als Daten; Legacydarstellung, LSP, JSON und Betriebsereignisse daraus klar abgeleitet.
- WASM und IDE ab P1; TeaVM praktisch weiterführen, Web Image tatsächlich untersuchen.
- Transfer-/Validation-/SchemaPlan halten das Gesamtziel präsent, werden aber erst in ihrer Phase implementiert.
- Bestehende DB-Mappings/Metadaten und gegenseitige Alt-/Neu-Verwendung sind verbindliche P5-Abnahmen.

Ant/Ivy ist technisch möglich, wird für dieses Projekt aber nicht gewählt. Der bestehende Wunsch nach möglichst einfacher Bedienung wird durch wenige Gradle-Konventionen, tatsächlich benötigte Module und explizite Tasks umgesetzt. Es gibt kein zusätzliches Buildframework.

## 3. Inhaltsverzeichnis

| Datei | Inhalt |
|---|---|
| [00-auftrag-und-agentenregeln.md](00-auftrag-und-agentenregeln.md) | Ausführbarer Auftrag, normative Sprache, Arbeitszyklus, Status- und Änderungsregeln |
| [01-zielbild-und-anforderungen.md](01-zielbild-und-anforderungen.md) | Gesamtprodukt, Benutzerabläufe, funktionale/nichtfunktionale Anforderungen, Phasen |
| [02-architektur-und-modulgrenzen.md](02-architektur-und-modulgrenzen.md) | Modulgraph, Datenebenen, Zustandsbesitz, Kern-/Host-/Verbrauchergrenzen |
| [03-build-und-repository.md](03-build-und-repository.md) | Gradle/Groovy, Wrapper, Toolchains, ANTLR-Isolation, CI und Distributionen |
| [04-quelltext-syntax-und-parser.md](04-quelltext-syntax-und-parser.md) | Quellen, Unicode/UTF-16, lossless Syntax, Grammatik/Versionen, Recovery |
| [05-metamodell-und-typensystem.md](05-metamodell-und-typensystem.md) | TransferDescription-Nachfolge, Symbole, Typen, Ausdrücke, fachliche Vollständigkeit |
| [06-kompilierung-und-semantik.md](06-kompilierung-und-semantik.md) | Engine-API, Phasen, fachliche Prüfdienste, Coverage, Gültigkeitsgate und Resultate |
| [07-workspace-inkrementalitaet-und-lsp.md](07-workspace-inkrementalitaet-und-lsp.md) | Revisionen, Updates, Abhängigkeiten, Wiederverwendung, Hintergrundanalyse und Sprachdienste |
| [08-modellaufloesung-und-repositories.md](08-modellaufloesung-und-repositories.md) | Import-/Modellauswahl, SourceSet, offline Resolver, Hostbeschaffung und Provenienz |
| [09-diagnostik-logging-und-telemetrie.md](09-diagnostik-logging-und-telemetrie.md) | Diagnosecodes, Ursachen, Legacy/JSON, Laufkontext, strukturierte Logs und Telemetrie |
| [10-webassembly-und-portabilitaet.md](10-webassembly-und-portabilitaet.md) | belegter Spike, TeaVM/Web Image, Sessionprotokoll, Workerabbruch und Browsermatrix |
| [11-visualisierung-und-projektionen.md](11-visualisierung-und-projektionen.md) | Diagramm-DTOs, Quellnavigation, stabile IDs, Mermaid/PlantUML/Sprotty/GLSP |
| [12-transfer-validierung-und-datenbanken.md](12-transfer-validierung-und-datenbanken.md) | spätere Plans/APIs für Transfer/Validierung/DB und strenge Kompatibilitätsverträge |
| [13-tests-kompatibilitaet-und-qualitaet.md](13-tests-kompatibilitaet-und-qualitaet.md) | Referenzsuite, semantische Goldens, Editierfälle, Plattformvergleich und Abnahmeprofile |
| [14-implementierungsplan-und-arbeitspakete.md](14-implementierungsplan-und-arbeitspakete.md) | konkrete Arbeitspakete P0–P5, Voraussetzungen, Ergebnisse und Gates |
| [15-entscheidungen-risiken-und-quellen.md](15-entscheidungen-risiken-und-quellen.md) | ADRs, Risiken, überprüfte Quellen, Commitpins und Evidenzgrenzen |
| [16-api-register-und-vertragstests.md](16-api-register-und-vertragstests.md) | kanonische Typen/Methoden/Packages und gemeinsame Vertragstests |
| [17-abdeckungsmatrix-und-review.md](17-abdeckungsmatrix-und-review.md) | Abdeckung der Nutzeranforderungen, vollständiger Anforderungsindex und Dokumentprüfung |
| [18-cli-sdk-und-kompatibilitaetsadapter.md](18-cli-sdk-und-kompatibilitaetsadapter.md) | CLI-/SDK-Einstiege, Konfiguration, Exporter, Exitcodes und optionale alte Java-API |
| [STARTPROMPT.md](STARTPROMPT.md) | direkt verwendbarer Startauftrag für den Coding-Agenten |

## 4. Wie die Detailtiefe zu lesen ist

Die Fachkapitel nennen öffentliche Signaturen und interne Verantwortlichkeiten mit Vorbedingungen, Nachbedingungen, Fehlern, Abbruch, Reihenfolge, Besitz und Tests. Ein Getterinventar allein wäre kein ausreichender Methodenentwurf; deshalb stehen besonders die semantischen Einzelprüfungen und ihre negativen Fälle im Mittelpunkt.

Für übergreifende Typen ist Kapitel 16 das Namens-/Besitzregister. Ein Coding-Agent liest vor einem Arbeitspaket die gemeinsamen Grundlagen sowie die betroffenen Fachkapitel. Er soll die lange Spezifikation nicht durch einen verkürzten neuen Scope ersetzen. Private Hilfsklassen und der genaue interne Zuschnitt dürfen im Rahmen der Verträge entstehen.

Ein P1-Feature, das syntaktisch erkannt, aber semantisch nicht implementiert ist, bleibt ausdrücklich unvollständig. Eine gültige Klasse in einem defekten Editorpuffer kann trotzdem navigierbar sein. Ein geprüftes `CompiledSchema` entsteht dagegen nur bei erfülltem Gate für die angeforderte Closure.

## 5. Was bereits nachgewiesen ist

Die beigefügte Probe hat denselben kleinen Java-/ANTLR-Code auf JVM, TeaVM-WasmGC in Node und TeaVM-JavaScript in Node erfolgreich ausgeführt. Sie prüft kleine 2.3/2.4-Syntaxfälle, Fehlererholung, EOF, Records/Sealed Types/Pattern Matching, exakte grosse Zahlen und Unicodepositionen. Die vollständige unveränderte geostandards-Grammatik war eingebunden.

**Sie beweist noch keinen vollständigen Compiler, keinen Browserlauf und keinen Web-Image-Erfolg.** Versionen, Rohresultate, Grössen und nicht geprüfte Aspekte stehen in Kapitel 10 und den Evidenzdateien. Das neue Gradle-/Semantik-/Browserprogramm hat eigenständige Abnahmen.

## 6. Empfohlener erster Meilenstein

Zwei Modelldateien mit Import, Domänen, Klassenvererbung, Attributen/Rollen und typisiertem Constraint. Derselbe fachliche Stand ist über SDK/CLI und Browser verwendbar; ein unvollständiger Editorzwischenstand bleibt analysierbar. Navigation und Diagramm kommen aus demselben Modell. JVM-/portable Resultate werden mit echten Referenz- und Negativfällen verglichen.

Dieser Abschnitt entscheidet über die Tragfähigkeit der Architektur. Danach folgen die vollständige 2.3/2.4-Semantik, die freigegebenen CLI-/Exportprofile und später Transfer, Validierung und DB. Die Spezifikation enthält deren Verträge bereits, damit der Compiler keine später benötigten Informationen verliert.
