# Arbeitsregeln für interlis-java

Diese Datei gehört als `AGENTS.md` in das Repository-Root. Pfadangaben beziehen sich auf dieses Root. Sie gilt für Arbeiten im gesamten Repository; für Änderungen unter `docs/spec/` ist zusätzlich die dortige `AGENTS.md` zu lesen. Anweisungen der Ausführungsumgebung und der aktuelle Nutzerauftrag haben Vorrang vor Repositorydokumenten.

## 1. Auftrag und Produktziel

`interlis-java` entwickelt einen modernen Java-INTERLIS-Stack mit den Fähigkeiten von ili2c, iox-ili, ilivalidator und ili2db. Der Compiler ist die gemeinsame Grundlage für Batch, IDE/LSP, Visualisierung und portable Ausführung. Vertrautes Werkzeugverhalten und bestehende ili2db-Abbildungen bleiben ausdrückliche Kompatibilitätsziele.

Bearbeite den tatsächlich erteilten Auftrag. Die Anwesenheit der Spezifikation oder das Lesen von `STARTPROMPT.md` aktiviert nicht selbständig einen Implementierungsauftrag. Bei einer beauftragten Erstimplementierung nach diesem Startprompt umfasst der erste Abschnitt P0 und P1. Bei einer Dokumentänderung, Analyse oder einem gezielten Fix bleibt dieser konkrete Umfang massgeblich. P2 bis P5 bleiben Zielanforderungen; sie werden dadurch weder vorzeitig implementiert noch gestrichen.

## 2. Einstieg und massgebliche Dokumente

Für den beauftragten G2-Abschluss gilt organisatorisch `docs/g2/EXECUTION_PLAN.md`;
der operative Stand steht ausschliesslich in `docs/g2/STATUS.md`. Die fachlichen
Spezifikationen und historischen Nachweise bleiben erhalten.

1. Prüfe den Repositoryzustand und vorhandene Änderungen. Lies die für die betroffenen Pfade geltenden Arbeitsregeln sowie `docs/implementation-status.md`, falls vorhanden. Erhalte fremde Änderungen.
2. Lies vor der ersten Implementierung `docs/spec/README.md`, `00-auftrag-und-agentenregeln.md`, `01-zielbild-und-anforderungen.md`, `02-architektur-und-modulgrenzen.md`, `03-build-und-repository.md` und `16-api-register-und-vertragstests.md` unter `docs/spec/`.
3. Wähle das beauftragte Arbeitspaket aus `14-implementierungsplan-und-arbeitspakete.md`. Lies seine Fachkapitel und die relevanten Abnahmen aus `13-tests-kompatibilitaet-und-qualitaet.md`. Verwende `17-abdeckungsmatrix-und-review.md` zur Zuordnung der Anforderungs-IDs. Bereits gelesene unveränderte Kapitel müssen nicht für jeden kleinen Schritt erneut vollständig geladen werden.

Die Spezifikation ist die fachliche Vertragsquelle. Kapitel 16 regelt gemeinsame API-Namen und Besitzer; die Fachkapitel regeln Details und Methodenbedingungen. Kapitel 01 definiert den Scope, Kapitel 03 den Build, Kapitel 14 die Lieferabschnitte. Eine Inkonsistenz wird anhand der betroffenen Verträge geklärt und konsistent korrigiert. Das Verhalten einer bestehenden Implementierung ist ein Referenznachweis, keine automatische Erlaubnis zum Ändern der Spezifikation.

## 3. Architektur- und Qualitätsgrenzen

- Verwende Java 25 ohne Preview, Gradle mit Groovy-DSL und den Repository-Wrapper. Paketbasis und Group sind `guru.interlis`. Lege nur tatsächlich benötigte Module an; führe keinen parallelen Maven-/Ant-Build und kein zusätzliches Buildframework ein. Das historische POM unter `docs/evidence/` bleibt Referenzevidenz.
- Unterstütze ausschliesslich INTERLIS 2.3 und 2.4. ANTLR bleibt intern; öffentliche APIs verwenden eigene Quell-, Syntax- und Modelltypen.
- Halte Compiler und Modell unabhängig von Dateisystem, HTTP, JDBC, LSP-Transport und UI. Hostzugriffe und Laufzeitintegration gehören in explizite Adapter.
- Erhalte die Verträge von `SyntaxSnapshot`, `AnalysisSnapshot` und `CompiledSchema`. Veröffentlichte Werte bleiben unveränderlich. Quellrevisionen, UTF-16-Bereiche, Snapshotreferenzen und fachliche Reihenfolgen werden nicht durch bequemere untypisierte Werte ersetzt.
- Ein gültiges `CompiledSchema` entsteht nur über das Gate für die angeforderte Closure. Fehlende Implementierung, Quellfehler, ungelöste Eingaben, Warnungsablehnung, Abbruch und interne Defekte bleiben unterscheidbar. Legale aufgeschobene Typbindung ist kein Fehlerzustand.
- Wiederverwendung ist eine Optimierung. Der gleiche Eingabestand muss bei frischer und wiederverwendender Analyse fachlich übereinstimmen. Übernimm veraltete Hintergrundresultate weder als aktuelle Anzeige noch in aktuelle Wiederverwendungsdaten.
- Behandle strukturierte Diagnostik und Laufbeobachtung als Teil jedes fachlichen Abschnitts. Verwende die zentralen Diagnose- und `RunContext`-Verträge aus Kapitel 09; keine globalen aktuellen Läufe, globalen Listener oder zweite Gateentscheidung in Adaptern.
- Halte spätere Verbraucherinformationen vollständig, insbesondere Typen, Übersetzungen, effektive Attribute/Rollen und Transferreihenfolgen. Bestehende DB-Mappings und Metadaten dürfen nicht still durch neue Zuordnungen ersetzt werden.

Die ausführlichen Verträge bleiben in den Fachkapiteln. Diese Merkpunkte sind keine alternative verkürzte Spezifikation.

## 4. Arbeitsweise

Benenne pro fachlichem Abschnitt die betroffenen Anforderungs-IDs, die erwartete Beobachtung und die passende Abnahme. Implementiere innerhalb des erteilten Umfangs einen vollständigen Abschnitt mit Fehlerpfad, öffentlichem Zugriff und relevanten Adaptern. Ein Stub, ein leerer Visitor, ein übersprungenes Konstrukt oder eine feste Erfolgsantwort erfüllt keine Anforderung.

Produktionscode und API-Namen sind Englisch. Erläutere in Javadoc besonders Nullfreiheit, Mutabilität, Reihenfolge, Revisionen, Einheiten, Abbruch und Fehler. Verwende klare Typen, kleine explizite Fabriken und Konstruktorinjektion. Füge Abhängigkeiten oder Frameworks nur für einen konkreten aktuellen Bedarf hinzu.

Bei Änderungen gemeinsamer APIs aktualisiere die betroffenen Verbraucher, Vertragstests und Spezifikationsstellen gemeinsam. Beachte dafür `docs/spec/AGENTS.md`. Kopiere keine zweite fachliche Namens-, Typ- oder Validierungssemantik in CLI, LSP, Diagrammrenderer oder Browserclient.

Löse Routineentscheidungen selbständig mit der einfachsten vertragskonformen Umsetzung. Bei einer echten ungeklärten Scope- oder Kompatibilitätsentscheidung beschreibe Auswirkungen und Empfehlung und erledige unabhängige Arbeit weiter. Veröffentlichungen, Releases, Nachrichten an Dritte und Produktionsmigrationen müssen vom konkreten Auftrag gedeckt sein; diese Datei erteilt dafür keinen zusätzlichen Auftrag.

## 5. Build und zielgerichtete Verifikation

Die ausführbaren Kommandos und Phasengates stehen in Kapitel 03 und 14. Prüfe zunächst, was im aktuellen Stand bereits vorhanden ist. Nutze bei bestehendem Wrapper `./gradlew` beziehungsweise unter Windows `gradlew.bat`.

- Ein Compilervertrag kann beispielsweise mit `./gradlew :interlis-compiler:test` geprüft werden, sobald dieses Modul existiert.
- `checkPortable` prüft den vorgesehenen JVM-/Node-Vertrag; `checkBrowser` die reale Browsermatrix. Verwende diese Tasks, sobald sie implementiert sind, für die davon betroffenen Änderungen und fälligen Gates.
- `releaseCheck` gilt für den ausdrücklich beschriebenen Entwicklungsstand. Fehlende oder übersprungene Pflichtprüfungen halten das jeweilige Gate offen.

Erfinde keine vorhandenen Tasks, Versionspins oder erfolgreichen Läufe. Bei reinen redaktionellen Änderungen genügt die passende Dokumentprüfung. Änderungen an Semantik, APIs, ausführbaren Beispielen oder Buildlogik erhalten die dafür notwendigen Prüfungen. Wiederhole darüber hinaus nur Tests, die eine konkrete offene Frage klären.

Ändere Goldens, XFAILs oder erwartete Diagnosen nur mit fachlichem Beleg. Erfasse tatsächliche Kommandos, Versionen, Umfang und Resultate. Ein Node-Erfolg belegt keinen Browserlauf; ein Parsererfolg keine vollständige Semantik; eine erfolgreiche Kompilierung noch keinen erfolgreichen CLI-Export. Die konkreten TeaVM-/Web-Image-Pflichten stehen in Kapitel 10.

## 6. Fortschritt und Abschluss

Halte beauftragten Implementierungsfortschritt unter `docs/implementation-status.md` sowie die Nachweise gemäss Kapitel 13 fest. Chatverlauf und diese Datei ersetzen keinen nachvollziehbaren Projektstatus. Verändere bei einem reinen Review keinen Implementierungsstatus ohne neue Evidenz.

Berichte zum Abschluss: bearbeiteter Umfang und IDs, wesentliche Änderungen, ausgeführte Prüfungen, nicht ausgeführte fällige Prüfungen, konkrete Blocker und nächster fachlicher Schritt. Trenne geplant, implementiert und tatsächlich verifiziert. Ein anderer Agent muss die beauftragte Arbeit anhand des Repositorys fortsetzen können.
