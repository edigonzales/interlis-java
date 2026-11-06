# Anforderungsregister

Die IDs bezeichnen stabile Anforderungen. Geltend beschreibt den Produktvertrag. Einen ausgeführten Nachweis liefern die gebundenen Tests und ihre Rohberichte. Geplant bezeichnet Anforderungen an künftige Verbraucher. Die Compilerpflichten für ihre Eingaben gelten bereits. Zurückgestellt bezeichnet den optionalen mutierbaren Java-Adapter.

Der [Compilerkatalog](../../testdata/verification/compiler-contracts.json) bindet die ausführbaren Compilerfälle an konkrete Suite, Methode und Invocation. Für weitere Anforderungen nennt die Tabelle die erforderliche Abnahme.


## AGENT

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="agent-001"></a>`AGENT-001` | Der Agent MUSS den aktuellen Repositoryzustand und vorhandene Arbeitsregeln lesen und fremde Änderungen erhalten. | Arbeitsregel | [Vertrag](../../AGENTS.md) | Arbeitsbericht nennt Ausgangszustand und abgegrenzte Änderungen. |
| <a id="agent-002"></a>`AGENT-002` | Jedes Arbeitspaket MUSS Anforderungen, Implementierung, aussagekräftige Prüfungen und Dokumentationsänderungen zusammen liefern. | Arbeitsregel | [Vertrag](../../AGENTS.md) | Vier Teile sind im Ergebnis nachvollziehbar; keine reine Dateiliste. |
| <a id="agent-003"></a>`AGENT-003` | Ein Stub, ein leerer Visitor, eine feste Erfolgsantwort oder ein ignoriertes Konstrukt DARF NICHT als implementierte Sprachunterstützung gelten. | Arbeitsregel | [Vertrag](../../AGENTS.md) | Negativtest und Capability-/Coverage-Bericht erkennen den fehlenden Teil. |
| <a id="agent-004"></a>`AGENT-004` | Der Agent MUSS nicht implementierte fachliche Konstrukte explizit melden und ihre Auswirkungen auf das Kompilierungsergebnis berücksichtigen. | Arbeitsregel | [Vertrag](../../AGENTS.md) | Unabgedecktes Konstrukt erzeugt kein gültiges CompiledSchema. |
| <a id="agent-005"></a>`AGENT-005` | Der Agent MUSS einen fehlgeschlagenen Test am Vertrag analysieren; Erwartungen dürfen nur mit fachlichem Beleg geändert werden. | Arbeitsregel | [Vertrag](../../AGENTS.md) | Änderung von Goldens/Baselines enthält Referenz und Begründung. |
| <a id="agent-006"></a>`AGENT-006` | Die neue Implementierung MUSS die Semantik selbst ausführen; ein heimlicher Aufruf von ili2c/ilic als Produktionscompiler ist unzulässig. | Arbeitsregel | [Vertrag](../../AGENTS.md) | Referenzwerkzeuge laufen ausschliesslich in Testadaptern oder explizitem Legacy-Adapter. |
| <a id="agent-007"></a>`AGENT-007` | Ergebnisse MÜSSEN erreichte Implementierung, ausgeführte Prüfungen, nicht ausgeführte Prüfungen und offene Risiken unterscheiden. | Arbeitsregel | [Vertrag](../../AGENTS.md) | Abschlussbericht verwendet keine pauschale Aussage „alles grün“ ohne Umfang. |
| <a id="agent-008"></a>`AGENT-008` | Dokumentiere dauerhafte Entscheidungen und den wiederaufnehmbaren Arbeitsstand im Repository oder in der vorgesehenen aktiven Ablage. | Arbeitsregel | [Vertrag](../../AGENTS.md) | Neuer Agent findet Anforderungen, Status, Testkommandos und nächsten Arbeitsschritt. |
| <a id="agent-009"></a>`AGENT-009` | Gemeinsame API-Änderungen aktualisieren Javadoc, Verträge, Adapter und Vertragstests zusammen. | Arbeitsregel | [Vertrag](../../AGENTS.md) | Aktualisierte Adapter, Javadoc und Vertragsprüfungen. |
| <a id="agent-010"></a>`AGENT-010` | Der Agent MUSS den kleinsten vollständigen nächsten vertikalen Abschnitt implementieren; ungenutzte Frameworks und leere Zukunftsmodule werden nicht angelegt. | Arbeitsregel | [Vertrag](../../AGENTS.md) | Jede Produktionsabhängigkeit und jedes Modul besitzt einen aktuellen Verbraucher oder eine konkrete Abnahme. |

## GOAL

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="goal-001"></a>`GOAL-001` | Der Kern MUSS INTERLIS 2.3 und 2.4 mit expliziter Versionsauswahl analysieren; andere Modellsprachen werden eindeutig abgelehnt. | Geltend | [Vertrag](../../README.md) | Versionsmatrix einschliesslich versionsabhängiger Schlüsselwörter; P1 Umfang markiert, P2 vollständig. |
| <a id="goal-002"></a>`GOAL-002` | Batch, IDE und Browser MÜSSEN dieselbe semantische Implementierung verwenden. | Geltend | [Vertrag](../../README.md) | Dieselben Eingaben liefern dieselbe normalisierte Analyse und dieselben Diagnosecodes. |
| <a id="goal-003"></a>`GOAL-003` | Ein öffentliches typisiertes Metamodell MUSS die Nachfolge der TransferDescription für alle geplanten Verbraucher ermöglichen. | Geltend | [Vertrag](../../README.md) | Verbrauchervertragstests für Typen, Referenzen, Reihenfolgen und Projektionen. |
| <a id="goal-004"></a>`GOAL-004` | Unvollständige oder fehlerhafte Quellen MÜSSEN sinnvoll analysierbare Teilinformationen ergeben. | Geltend | [Vertrag](../../README.md) | Editiersequenzen erhalten Nachbarsymbole und präzise fehlende Elemente. |
| <a id="goal-005"></a>`GOAL-005` | Der Workspace MUSS Quellen aktualisieren/entfernen und unveränderte Arbeit wiederverwenden können. | Geltend | [Vertrag](../../README.md) | Revisionstests, Cache-Nachweise und Gleichheit zur frischen Analyse. |
| <a id="goal-006"></a>`GOAL-006` | Modellvisualisierungen MÜSSEN aus semantischen Projektionen mit Quellverweisen entstehen. | Geltend | [Vertrag](../../README.md) | Diagramm-Goldens und Navigation; Renderer erfindet keine Bindungsregeln. |
| <a id="goal-007"></a>`GOAL-007` | Der Compiler MUSS als JVM-Bibliothek und CLI nutzbar sein und einen geprüften Browserpfad erhalten. | Geltend | [Vertrag](../../README.md) | JVM-/WASM-/Browserabnahmen im selben Arbeitspaket. |
| <a id="goal-008"></a>`GOAL-008` | Strukturierte Diagnostik und Laufereignisse MÜSSEN ab dem ersten öffentlichen Lauf verfügbar sein. | Geltend | [Vertrag](../../README.md) | Diagnose- und Eventvertragstests, Legacy-/JSON-Darstellungen. |
| <a id="goal-009"></a>`GOAL-009` | Die späteren Transfer-/Validator-/DB-Module MÜSSEN dieselbe fachliche Modellinterpretation verwenden. | Geplant | [Vertrag](../design/transfer-validation-database.md) | Gemeinsame Modellfingerprints und Plan-Goldens. |
| <a id="goal-010"></a>`GOAL-010` | Der DB-Ersatz MUSS bestehende Abbildungsinformationen respektieren und gegenseitige Weiterverwendung ermöglichen. | Geplant | [Vertrag](../design/transfer-validation-database.md) | Alte/neue Werkzeuge lesen und verändern Datenbanken in beiden Richtungen. |
| <a id="goal-011"></a>`GOAL-011` | CLI-Namen, Optionen, Ausgaben und Exitcodes MÜSSEN zum freigegebenen Werkzeugprofil passen; das Compiler-CLI verwendet ausschliesslich den neuen Standardvertrag. | Geltend | [Vertrag](../../README.md) | Standardprozessmatrix für das Compiler-CLI; gepinnte Referenzläufe für weitere ausdrücklich freigegebene Werkzeuge. |
| <a id="goal-012"></a>`GOAL-012` | Die externe Compilersuite MUSS übernommen und mit semantischen, Editor- und Verbrauchertests ergänzt werden. | Geltend | [Vertrag](../../README.md) | Testreports unterscheiden Akzeptanz, Semantik und bekannte Abweichungen. |

## NFR

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="nfr-001"></a>`NFR-001` | Quelltextanalyse MUSS bei gleichen fachlichen Inputs deterministisch sein. | Geltend | [Vertrag](../architecture.md) | Reihenfolge, Diagnostik und normalisierte Projektion bleiben über Wiederholung/Plattform gleich. |
| <a id="nfr-002"></a>`NFR-002` | Unveränderliche veröffentlichte Snapshots MÜSSEN gefahrlos parallel lesbar sein. | Geltend | [Vertrag](../architecture.md) | Parallele Abfragen verändern weder Resultate noch Cachezustand des Snapshots. |
| <a id="nfr-003"></a>`NFR-003` | Der portable Kern DARF keine impliziten Netzwerk-, Dateisystem-, DB- oder UI-Zugriffe benötigen. | Geltend | [Vertrag](../architecture.md) | Abhängigkeitsprüfung und In-Memory-/WASM-Lauf. |
| <a id="nfr-004"></a>`NFR-004` | Laufzeit und Speicher MÜSSEN auf einem versionierten Korpus gemessen und gegen Budgets geprüft werden. | Geltend | [Vertrag](../architecture.md) | Messverfahren mit Rohresultaten; Ziele und Messungen getrennt. |
| <a id="nfr-005"></a>`NFR-005` | Abbruch und Ressourcenlimits MÜSSEN definierte Resultate liefern; ein Abbruch darf kein gültiges Teilergebnis ausstellen. | Geltend | [Vertrag](../architecture.md) | Phasenabbruchtests und Worker-Verhalten. |
| <a id="nfr-006"></a>`NFR-006` | Unicode, CRLF, Kommentare und Quellpositionen MÜSSEN verlustfrei und versionsbezogen behandelt werden. | Geltend | [Vertrag](../architecture.md) | UTF-16-/Codepoint-/Zeilenmatrix mit Emojis und Umlauten. |
| <a id="nfr-007"></a>`NFR-007` | Diagnostik und Projektionen MÜSSEN ohne localeabhängige Zahlen-/Sortierunterschiede erzeugt werden. | Geltend | [Vertrag](../architecture.md) | Tests mit verschiedenen JVM-Locale-/Timezone-Einstellungen. |
| <a id="nfr-008"></a>`NFR-008` | Öffentliche Resultate MÜSSEN Nullfreiheit, Reihenfolge und Lebensdauer dokumentieren. | Geltend | [Vertrag](../architecture.md) | API-Vertragstests und Register. |
| <a id="nfr-009"></a>`NFR-009` | Der Build MUSS mit gepinnten Werkzeugen reproduzierbar ausführbar sein. Jenesis baut die JVM-Produkte. Der Gradle-Wrapper baut die beiden portablen Backends. | Geltend | [Vertrag](../development/build.md) | Frischer Checkout, gepinnte Toolchain und dokumentierte Kommandos. |
| <a id="nfr-010"></a>`NFR-010` | Der Kern MUSS unabhängig von einer laufenden Serverinfrastruktur verwendbar sein. | Geltend | [Vertrag](../architecture.md) | Lokale Bibliotheks-/CLI-Ausführung ohne Spring, Datenbank oder Container. |
| <a id="nfr-011"></a>`NFR-011` | Produktionsgeometrievalidierung MUSS später begrenzte Caches, Streaming und explizite Ressourcenbudgets anbieten. | Geplant | [Vertrag](../design/transfer-validation-database.md) | Grössenkorpus und definierte Reaktion auf Budgeterschöpfung; 64 GB nicht überschritten im festgelegten Profil. |
| <a id="nfr-012"></a>`NFR-012` | Abhängigkeiten und technische Neuerungen MÜSSEN einen tatsächlichen aktuellen Nutzen haben. | Geltend | [Vertrag](../architecture.md) | Kurzer Zweck je Dependency; kein Framework nur für künftige Eventualitäten. |

## ARCH

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="arch-001"></a>`ARCH-001` | Der Modulgraph MUSS azyklisch sein und die erlaubten Abhängigkeiten einhalten. | Geltend | [Vertrag](../architecture.md) | Build-/Architekturtest liest Produktionsgraph und Importgrenzen. |
| <a id="arch-002"></a>`ARCH-002` | Öffentliche API DARF keine ANTLR-, LSP-, JDBC- oder UI-Typen in den portablen Kern tragen. | Geltend | [Vertrag](../architecture.md) | Signaturprüfung plus portable Kompilierung. |
| <a id="arch-003"></a>`ARCH-003` | Alle veröffentlichten fachlichen Snapshots MÜSSEN unveränderlich sein. | Geltend | [Vertrag](../architecture.md) | Mutations-/Lebensdauertests einschliesslich nach Workspace.close. |
| <a id="arch-004"></a>`ARCH-004` | CompiledSchema unserer Engine MUSS ausschliesslich über CompilationGate entstehen. | Geltend | [Vertrag](../architecture.md) | Architektur- und Fehlerpfadtests. |
| <a id="arch-005"></a>`ARCH-005` | Compiler und Projektionen DÜRFEN keine globale Prozesskonfiguration verändern. | Geltend | [Vertrag](../architecture.md) | Zwei isolierte Sitzungen mit abweichenden Profilen beeinflussen sich nicht. |
| <a id="arch-006"></a>`ARCH-006` | Fachliche Reihenfolgen MÜSSEN explizit modelliert werden. | Geltend | [Vertrag](../architecture.md) | Geerbte/überschriebene Attribute und Rollen bleiben JVM/WASM gleich. |
| <a id="arch-007"></a>`ARCH-007` | Resolverzustand und Overlays MÜSSEN in den Eingabestand und Cachevertrag eingehen. | Geltend | [Vertrag](../architecture.md) | Änderung ohne Quelltextänderung erzwingt erforderliche Neuanalyse. |
| <a id="arch-008"></a>`ARCH-008` | Diagramm-/Transfer-/Validator-/DB-Consumer MÜSSEN ihre Unterstützung explizit ausweisen. | Geltend | [Vertrag](../architecture.md) | UnsupportedFeature erzeugt Ergebnis mit betroffenen Elementen. |
| <a id="arch-009"></a>`ARCH-009` | Das Projekt DARF keine DI-/Server-/ORM-Laufzeit für reine Modellanalyse verlangen. | Geltend | [Vertrag](../architecture.md) | Minimale Java-main-/WASM-Anwendung analysiert aus Strings. |
| <a id="arch-010"></a>`ARCH-010` | Wiederverwendung MUSS ein optionaler Optimierungsparameter bleiben. | Geltend | [Vertrag](../architecture.md) | Reuse.none und Cachepfad liefern fachlich gleiche Resultate. |

## BUILD

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="build-001"></a>`BUILD-001` | Jenesis MUSS der alleinige aktive JVM-Builder sein; Gradle/Groovy/Wrapper MUSS ausschliesslich die zwei Wasm-Backends bauen. | Geltend | [Vertrag](../development/build.md) | Frischer Checkout baut ohne Maven/Ant-Installation. |
| <a id="build-002"></a>`BUILD-002` | JDK 25 und feste verifizierte Versionen MÜSSEN dokumentiert sein. | Geltend | [Vertrag](../development/build.md) | Versionen-/Wrapper-/Dependencyreport. |
| <a id="build-003"></a>`BUILD-003` | Teilprojekte MÜSSEN dem azyklischen fachlichen Modulgraph entsprechen. | Geltend | [Vertrag](../development/build.md) | Dependencytest, keine impliziten Zwischen-JAR-Kopien. |
| <a id="build-004"></a>`BUILD-004` | Buildlogik MUSS klein und sichtbar bleiben; Projektzugriffe in `afterEvaluate` sind zu vermeiden. | Geltend | [Vertrag](../development/build.md) | Konfiguration ist deklarativ, Taskabhängigkeiten explizit. |
| <a id="build-005"></a>`BUILD-005` | ANTLR-Generator und Runtime MÜSSEN getrennt und gleichversioniert sein. | Geltend | [Vertrag](../development/build.md) | Runtimeclasspath und generierter Parser geprüft. |
| <a id="build-006"></a>`BUILD-006` | Generierte Artefakte MÜSSEN aus gepinnten Quellen entstehen; fachliche Ergebnisse bleiben deterministisch. JVM-/SDK- und nicht von TeaVM-Programmen abhängige Lieferdateien MÜSSEN binär reproduzierbar bleiben. TeaVM-Programme und ausschliesslich ihre Hash-/Grössenfelder im Manifest sind davon ausgenommen. | Geltend | [Vertrag](../development/release.md#pflichtnachweise) | Zwei unabhängige Builds mit vollständigen Inventaren, funktionalen JS-/Wasm-Nachweisen und präzisem Bytevergleich; Integrität der konkreten Lieferung bleibt geprüft. |
| <a id="build-007"></a>`BUILD-007` | Der normale JVMbuild MUSS eine schnelle, netzwerkunabhängige Entwicklerabnahme sein, sobald Abhängigkeiten/Korpus bereitstehen. | Geltend | [Vertrag](../development/build.md) | Wiederholung offline ohne Live-Repositoryzugriff. |
| <a id="build-008"></a>`BUILD-008` | Pflichtprüfungen DÜRFEN bei fehlenden Tools nicht als erfolgreich übersprungen gelten. | Geltend | [Vertrag](../development/build.md) | Releaseprofil scheitert mit konkreter Vorbedingung. |
| <a id="build-009"></a>`BUILD-009` | Publishing DARF keine Voraussetzung für lokales Bauen/Testen sein. | Geltend | [Vertrag](../development/build.md) | Lokale Distribution funktioniert ohne Zugangsdaten. |
| <a id="build-010"></a>`BUILD-010` | Die POMs definieren JVM-Koordinaten, Abhängigkeiten und Scopes. Der JVM-Build wird ausschliesslich über Jenesis ausgeführt. | Geltend | [Vertrag](../development/build.md) | Jenesis reproduziert die Produkte; aktive POMs sind Metadaten, das historische Spike-POM bleibt Evidenz. |

## SRC

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="src-001"></a>`SRC-001` | Jede Quelle MUSS eine opake `SourceId`, eine Revision und den unveränderten Java-String besitzen. | Geltend | [Vertrag](sources-and-syntax.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Zwei Revisionen derselben Quelle bleiben getrennt adressierbar. |
| <a id="src-002"></a>`SRC-002` | Alle internen Quellbereiche MÜSSEN halb-offene UTF-16-Offsets verwenden. | Geltend | [Vertrag](sources-and-syntax.md) | Tests mit Umlauten, Supplementary Characters, CRLF und EOF. |
| <a id="src-003"></a>`SRC-003` | Zeilen- und Spaltenumrechnung MUSS deterministisch und ohne Plattform-Newline-Annahmen sein. | Geltend | [Vertrag](sources-and-syntax.md) | Roundtrip der gültigen Positionen und dokumentierte Behandlung von Zeilenumbrüchen. |
| <a id="src-004"></a>`SRC-004` | Ein Offset oder Bereich ausserhalb der bezeichneten Quellrevision MUSS als API-Fehlgebrauch zurückgewiesen werden. | Geltend | [Vertrag](sources-and-syntax.md) | Negative Offsets, falsche Quelle, falsche Revision und Ende hinter Textlänge. |
| <a id="src-005"></a>`SRC-005` | Inhalt, Kommentare und Zwischenräume MÜSSEN aus einem Syntaxsnapshot exakt rekonstruierbar bleiben. | Geltend | [Vertrag](sources-and-syntax.md) | Identischer UTF-16-String nach Rekonstruktion für jedes Syntaxfixture. |
| <a id="src-006"></a>`SRC-006` | Kanonisierung einer URI MUSS vor Eintritt in den Kern erfolgen; Gleichheit von `SourceId` ist Stringgleichheit. | Geltend | [Vertrag](sources-and-syntax.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Keine plattformabhängige Auflösung und keine Netz-/Dateizugriffe bei Konstruktion. |
| <a id="src-007"></a>`SRC-007` | Content-Fingerprints MÜSSEN Algorithmus und Eingabekodierung festlegen und dürfen keine Prozessidentitäten enthalten. | Geltend | [Vertrag](sources-and-syntax.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Gleiche Inhalte erhalten auf allen Zielplattformen denselben Fingerprint. |

## SYN

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="syn-001"></a>`SYN-001` | Der produktive Einstieg MUSS die gesamte Eingabe bis EOF prüfen. | Geltend | [Vertrag](sources-and-syntax.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Gültiges Modell plus nachlaufender Text wird abgelehnt. |
| <a id="syn-002"></a>`SYN-002` | Lexerfehler und Parserfehler MÜSSEN gesammelt werden; Standardlistener auf stderr sind zu entfernen. | Geltend | [Vertrag](sources-and-syntax.md) | Ein unzulässiges Zeichen ist sichtbar, auch wenn der Parser den Rest akzeptiert. |
| <a id="syn-003"></a>`SYN-003` | Ausschliesslich INTERLIS 2.3 und 2.4 sind zulässige Sprachversionen. | Geltend | [Vertrag](sources-and-syntax.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · 1.0, 2.2 und unbekannte Versionsangaben erhalten eine erklärende Diagnose. |
| <a id="syn-004"></a>`SYN-004` | Versionsabhängige Schlüsselwörter und Konstrukte MÜSSEN über einen expliziten Sprachprofilvertrag geprüft werden. | Geltend | [Vertrag](sources-and-syntax.md) | Positive und negative versionsbezogene Tests; keine blosse Prüfung des Headers. |
| <a id="syn-005"></a>`SYN-005` | `STRICT` und `EDITOR` MÜSSEN dieselbe Sprachgültigkeit verwenden. | Geltend | [Vertrag](sources-and-syntax.md) | Recovery allein erzeugt in keinem Modus ein gültiges Compile-Ergebnis. |
| <a id="syn-006"></a>`SYN-006` | Editor-Recovery MUSS noch erkennbare umgebende Deklarationen und ihre Bereiche erhalten. | Geltend | [Vertrag](sources-and-syntax.md) | `height :` zerstört nicht die umgebende Klasse oder die folgende Klasse. |
| <a id="syn-007"></a>`SYN-007` | Fehlende, übersprungene und synthetische Syntax MUSS ausdrücklich von ursprünglicher Syntax unterscheidbar sein. | Geltend | [Vertrag](sources-and-syntax.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Fehlstellen mit Nullbreite; Recoverysegment mit originalem Textbereich. |
| <a id="syn-008"></a>`SYN-008` | Kein öffentlicher Typ DARF von ANTLR-Bäumen, Tokens oder RecognitionExceptions abhängen. | Geltend | [Vertrag](sources-and-syntax.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Architekturtest und öffentliche API-Prüfung. |
| <a id="syn-009"></a>`SYN-009` | Parsen MUSS bei Fehlern und bei Budgetüberschreitung terminieren. | Geltend | [Vertrag](sources-and-syntax.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Wiederholte fehlerhafte Eingaben und tiefe Verschachtelung liefern definiertes Ergebnis. |
| <a id="syn-010"></a>`SYN-010` | Fragmentanalyse MUSS expliziten Fragmenttyp und einen unveränderten Quellbereich besitzen. | Geltend | [Vertrag](sources-and-syntax.md) | Ausdruck, Objektpfad und Attributfragment mit korrektem Offset; Restinhalt wird erkannt. |
| <a id="syn-011"></a>`SYN-011` | Verlustfreie Syntax und fachliche Modellprojektion MÜSSEN getrennte Repräsentationen bleiben. | Geltend | [Vertrag](sources-and-syntax.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Syntax enthält Fehlerknoten; Metamodell enthält keinen ANTLR-Kontext. |
| <a id="syn-012"></a>`SYN-012` | Grammatikstand, Generatorversion und Runtimeversion MÜSSEN fest gepinnt sein. | Geltend | [Vertrag](sources-and-syntax.md) | Reproduzierbarer Gradlebuild, dokumentierte Herkunft und Lizenz. |
| <a id="syn-013"></a>`SYN-013` | Die AST-Abbildung MUSS jeden erkannten Produktionszweig behandeln oder sichtbar als noch nicht unterstützt kennzeichnen. | Geltend | [Vertrag](sources-and-syntax.md) | Coverage-Test; kein `default -> null` und kein stilles Überspringen. |
| <a id="syn-014"></a>`SYN-014` | Identitäten für Syntaxknoten DÜRFEN keinen statischen Zähler und keine FQN als alleinige Identität verwenden. | Geltend | [Vertrag](sources-and-syntax.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Sitzungsparallelität, Duplikate und Einfügen einer vorangehenden Deklaration. |
| <a id="syn-015"></a>`SYN-015` | Grammatikänderungen MÜSSEN Gültigkeit, Operatorbindung und Recovery gemeinsam regressionsprüfen. | Geltend | [Vertrag](sources-and-syntax.md) | Gültige, ungültige und Editorfixtures für jede betroffene Regel. |

## META

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="meta-001"></a>`META-001` | Veröffentlichte Modellgraphen MÜSSEN unveränderlich sein, einschliesslich enthaltener Collections. | Geltend | [Vertrag](compiler-and-model.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Keine Änderung über zurückgegebene Listen, Maps, Builder oder Consumer. |
| <a id="meta-002"></a>`META-002` | Fehlerhafte Analyse und erfolgreich geprüftes Schema MÜSSEN im API unterscheidbar sein. | Geltend | [Vertrag](compiler-and-model.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · `CompiledSchema` nur über das Gate in Kapitel 06; Teilanalyse ohne Erfolgssiegel. |
| <a id="meta-003"></a>`META-003` | Beziehungen MÜSSEN Identitäten verwenden, die nicht allein aus Namen bestehen. | Geltend | [Vertrag](compiler-and-model.md) | Doppelte Namen, Rename und Revisionen ergeben keine falsche Vereinigung. |
| <a id="meta-004"></a>`META-004` | Jede öffentliche Abfrage mit einem Handle MUSS dessen Snapshotzugehörigkeit prüfen. | Geltend | [Vertrag](compiler-and-model.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Verwendung eines alten `SymbolRef` löst einen definierten API-Fehler aus. |
| <a id="meta-005"></a>`META-005` | Auflösungsfehler, noch nicht analysierte Teile und rechtmässige spätere Bindung MÜSSEN unterschiedliche Zustände besitzen. | Geltend | [Vertrag](compiler-and-model.md) | Fehlender Domänenname, `ErrorType` und legale Generic-Domäne bleiben unterscheidbar. |
| <a id="meta-006"></a>`META-006` | Alle Sprachkonstrukte im Featurekatalog MÜSSEN bis P2 semantisch repräsentiert und geprüft sein. | Geltend | [Vertrag](compiler-and-model.md) | Katalogzeile besitzt Konstruktor-/Analyse-/Negativ-/Export- oder Querytest. |
| <a id="meta-007"></a>`META-007` | Kanonische Symbolidentität und übersetzte beziehungsweise transferspezifische Namen MÜSSEN getrennt sein. | Geltend | [Vertrag](compiler-and-model.md) | Übersetzungsfixture verändert keine Referenzidentität. |
| <a id="meta-008"></a>`META-008` | Deklarationsreihenfolge und effektive Attribut-/Rollenreihenfolge MÜSSEN explizit modelliert sein. | Geltend | [Vertrag](compiler-and-model.md) | Goldenvergleich mit Legacyfällen für Vererbung, Überschreibung und eingebettete Rollen. |
| <a id="meta-009"></a>`META-009` | Numerische Werte MÜSSEN exakt und mit der benötigten INTERLIS-Genauigkeitsinformation gespeichert werden. | Geltend | [Vertrag](compiler-and-model.md) | Zahlen grösser 2^53, Skalen, Exponenten und Grenzen ohne Doubleverlust. |
| <a id="meta-010"></a>`META-010` | `CompiledSchema` MUSS genau die gewählten Wurzelmodelle samt geprüfter Abhängigkeitsmenge beschreiben. | Geltend | [Vertrag](compiler-and-model.md) | Fehler in unabhängiger Quelle blockiert nicht; Fehler in Importclosure blockiert. |
| <a id="meta-011"></a>`META-011` | Quellbezug und fachliche Herkunft MÜSSEN auch für geerbte, synthetische und vordefinierte Elemente nachvollziehbar sein. | Geltend | [Vertrag](compiler-and-model.md) | Geerbtes Attribut führt auf Originaldeklaration zurück. |
| <a id="meta-012"></a>`META-012` | Konfigurationsoverlays und Laufzeitcaches DÜRFEN das gemeinsame Modell nicht mutieren. | Geltend | [Vertrag](compiler-and-model.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Zwei unterschiedlich konfigurierte Verbraucher können dasselbe Schema parallel verwenden. |
| <a id="meta-013"></a>`META-013` | Modellgültigkeit DARF NICHT von den Fähigkeiten eines einzelnen späteren Consumers abhängen. | Geltend | [Vertrag](compiler-and-model.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Gültiger View wird nicht wegen fehlendem SQL-Exporter abgelehnt oder still verworfen. |
| <a id="meta-014"></a>`META-014` | Ein unbekanntes Modellfeature DARF NICHT als untypisierte Map, `Object`, `Any` oder leere Liste verschwinden. | Geltend | [Vertrag](compiler-and-model.md) | Exhaustive Variantenprüfung und sichtbare Coverage-Lücke. |
| <a id="meta-015"></a>`META-015` | Builtins MÜSSEN versionsspezifisch, unveränderlich und ohne versteckten globalen Laufzustand verfügbar sein. | Geltend | [Vertrag](compiler-and-model.md) | Parallelkompilierung beider Versionen beeinflusst keine andere Sitzung. |
| <a id="meta-016"></a>`META-016` | Typbeziehungen MÜSSEN über typisierte Ergebnisobjekte erklärt werden können. | Geltend | [Vertrag](compiler-and-model.md) | Unzulässige Zuweisung liefert Grund und betroffene Teiltypen. |
| <a id="meta-017"></a>`META-017` | Semantische Fingerprints MÜSSEN fachliche Reihenfolge, Optionen und Abhängigkeiten berücksichtigen. | Geltend | [Vertrag](compiler-and-model.md) | Fachliche Änderung verändert Fingerprint; Run-ID und Uhrzeit tun dies nicht. |

## SEM

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="sem-001"></a>`SEM-001` | Der Compiler MUSS nur explizite Sources/Bindings/Optionen verwenden und darf keine versteckten Quellen nachladen. | Geltend | [Vertrag](compiler-and-model.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Kein Dateisystem-/HTTP-Zugriff aus Compiler- oder Modellmodul. |
| <a id="sem-002"></a>`SEM-002` | Analyse, Ablehnung, Abbruch und interner Fehler MÜSSEN typisierte Ergebnisse besitzen. | Geltend | [Vertrag](compiler-and-model.md) | Je ein real auslösender Testfall pro Ergebniskategorie. |
| <a id="sem-003"></a>`SEM-003` | Das Erfolgsgate MUSS vollständige Coverage und alle kanonischen Fehler der angeforderten Closure prüfen. | Geltend | [Vertrag](compiler-and-model.md#gültigkeitsgate-und-veröffentlichung) | Unterdrückte oder nicht dargestellte Fehler erzeugen keinen Erfolg. |
| <a id="sem-004"></a>`SEM-004` | Ein Fehler in unabhängigen Workspacequellen DARF eine gültige Rootclosure nicht blockieren. | Geltend | [Vertrag](compiler-and-model.md#gültigkeitsgate-und-veröffentlichung) | [Katalog](../../testdata/verification/compiler-contracts.json) · Zwei unabhängige Modelle, eines fehlerhaft; gezielte Kompilierung des anderen gelingt. |
| <a id="sem-005"></a>`SEM-005` | Unaufgelöste Pflichtbindungen, Error-/Pending-Typen und unbekannte Implementierungszweige MÜSSEN den betroffenen Compile blockieren. | Geltend | [Vertrag](compiler-and-model.md) | Kein Erfolg durch `null`, Defaulttyp oder ausgelassene Deklaration. |
| <a id="sem-006"></a>`SEM-006` | Rechtmässige Generic-/Deferred-Bindungen und externe Funktionssignaturen MÜSSEN von Fehlern getrennt bleiben. | Geltend | [Vertrag](compiler-and-model.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Gültiger Fall kompiliert ohne Runtimeimplementierung beziehungsweise Datensatzkonkretisierung. |
| <a id="sem-007"></a>`SEM-007` | Jede Phase MUSS deterministische Eingaben, Ausgaben, Zustandsbesitz und Abbruchpunkte haben. | Geltend | [Vertrag](compiler-and-model.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Wiederholung und Plattformvergleich liefern gleiche fachliche Resultate. |
| <a id="sem-008"></a>`SEM-008` | Vorabindexierung DARF sprachlich verbotene Vorwärtsreferenzen oder Sichtbarkeiten nicht erlauben. | Geltend | [Vertrag](compiler-and-model.md) | Ziel vorhanden im globalen Index, aber im fraglichen Scope unzulässig: Diagnose. |
| <a id="sem-009"></a>`SEM-009` | Zyklen MÜSSEN fachlich klassifiziert und endlich bearbeitet werden. | Geltend | [Vertrag](compiler-and-model.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Alias-/Vererbungszyklus und erlaubter Referenzzyklus besitzen getrennte Ergebnisse. |
| <a id="sem-010"></a>`SEM-010` | Typen, Constraints, Views, Übersetzungen und effektive Eigenschaften MÜSSEN nach Katalog05 vollständig geprüft werden. | Geltend | [Vertrag](compiler-and-model.md) | Vollständige Abdeckungsmatrix mit positiven und negativen Fällen. |
| <a id="sem-011"></a>`SEM-011` | Wiederverwendung MUSS semantisch optional sein; ohne Cache muss dasselbe fachliche Resultat entstehen. | Geltend | [Vertrag](compiler-and-model.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Fresh-versus-reuse nach Editfolgen. |
| <a id="sem-012"></a>`SEM-012` | Veraltete, fremde oder nicht passende Cacheeingaben MÜSSEN ignoriert oder als API-Fehlgebrauch zurückgewiesen werden. | Geltend | [Vertrag](compiler-and-model.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Geänderte Sprachoption oder Importrevision verursacht keinen falschen Cachetreffer. |
| <a id="sem-013"></a>`SEM-013` | Kein global veränderlicher Logger, Modellkatalog, Proxywert, Nameindex oder Fehlerstatus ist zulässig. | Geltend | [Vertrag](compiler-and-model.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Mehrere Engines/Sitzungen ohne gegenseitige Beeinflussung. |
| <a id="sem-014"></a>`SEM-014` | Ressourcenlimits und Abbruch MÜSSEN als nicht erfolgreiche Ausgänge sichtbar sein. | Geltend | [Vertrag](compiler-and-model.md) | Limit reduziert Ergebnisabdeckung; Gate bleibt geschlossen. |
| <a id="sem-015"></a>`SEM-015` | Teilergebnisse MÜSSEN ihren tatsächlichen Analysezustand ausweisen. | Geltend | [Vertrag](compiler-and-model.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Completion kann bekannte Symbole nutzen; Typabfrage unbekannter Teile liefert `NotAnalyzed`. |
| <a id="sem-016"></a>`SEM-016` | Externe Configoverlays MÜSSEN als immutable Eingaben und Fingerprintbestandteile behandelt werden. | Geltend | [Vertrag](compiler-and-model.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Zwei Overlaykonfigurationen verwechseln keine Resultate oder Laufzeitcaches. |
| <a id="sem-017"></a>`SEM-017` | Norm- und Legacyabweichungen MÜSSEN explizit begründet und getestet sein; automatische Baselineanpassung ist verboten. | Geltend | [Vertrag](compiler-and-model.md) | Jede akzeptierte Abweichung besitzt Referenz, Fall und bewusste Entscheidung. |
| <a id="sem-018"></a>`SEM-018` | Jeder produktive Aufruf von `DefaultCompiledSchema` MUSS durch `CompilationGate` erfolgen. | Geltend | [Vertrag](compiler-and-model.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Architekturtest und gezielter Gate-Negativtest. |
| <a id="sem-019"></a>`SEM-019` | Die Frontendpipeline MUSS denselben fachlichen Code auf JVM und Wasm verwenden. | Geltend | [Vertrag](compiler-and-model.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Identische semantische Fixtureausgabe; kein nur für Wasm vereinfachtes Modell. |
| <a id="sem-020"></a>`SEM-020` | CLI/LSP/Projektionen DÜRFEN keine zweite unabhängige Namens- oder Typsemantik implementieren. | Geltend | [Vertrag](compiler-and-model.md) | Adapterdelegation und Vergleich an identischen Modellfällen. |

## INC

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="inc-001"></a>`INC-001` | Der Workspace MUSS versionierte In-Memory-Quellen ohne Dateisystem und Netzwerk verwalten können. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Dieselbe Sitzung auf JVM und Wasm mit virtuellen URIs. |
| <a id="inc-002"></a>`INC-002` | Änderungen MÜSSEN atomar sein und die erwartete Ausgangsrevision prüfen. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Konflikt- und ungültige Edit-Tests zeigen unveränderten Ausgangszustand. |
| <a id="inc-003"></a>`INC-003` | Quellrevision, Workspace-Revision und LSP-Dokumentversion MÜSSEN getrennte Werte sein. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · No-op-Änderung und Schliessen/Wiederöffnen eines Dokuments. |
| <a id="inc-004"></a>`INC-004` | Veröffentlichte Snapshots MÜSSEN unveränderlich sein und ihren Quellstand vollständig identifizieren. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Alter Snapshot bleibt nach weiteren Änderungen identisch abfragbar. |
| <a id="inc-005"></a>`INC-005` | EDITOR-Analyse MUSS an unvollständigen Deklarationen brauchbare Nachbarelemente erhalten. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Editfolge mit fehlendem Attributtyp; Nachbarklasse navigierbar. |
| <a id="inc-006"></a>`INC-006` | Unveränderte Dateien SOLLEN ab P1 nicht erneut geparst werden; fehlerhafte Dateien dürfen separat erneut geparst werden. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Zähler für Parse-Aufrufe belegen Wiederverwendung ohne Timing-Annahme. |
| <a id="inc-007"></a>`INC-007` | Cache-Schlüssel MÜSSEN alle fachlich wirksamen Eingaben berücksichtigen. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Änderungen von Sprachversion, Optionen, Imports und Overlays invalidieren. |
| <a id="inc-008"></a>`INC-008` | Abhängigkeiten auf fehlende oder mehrdeutige Namen und Modelle MÜSSEN erfasst werden. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Später hinzugefügtes Modell oder zweite Kandidatendeklaration aktualisiert Analyse. |
| <a id="inc-009"></a>`INC-009` | Eine inkrementelle Analyse MUSS fachlich einer frischen Analyse desselben Zustands entsprechen. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Normalisierter Vergleich nach jeder Editfolge. |
| <a id="inc-010"></a>`INC-010` | Symbolidentitäten MÜSSEN für eindeutig unveränderte Deklarationen innerhalb derselben Sitzung wiederverwendet werden. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Kommentar-/Nachbaränderung verändert die betroffenen SymbolIds nicht. |
| <a id="inc-011"></a>`INC-011` | Identitätszuordnung DARF bei Mehrdeutigkeit keine falsche Kontinuität erfinden. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Duplikate, Verschieben und Löschen/Neuanlegen erzeugen nötigenfalls neue IDs. |
| <a id="inc-012"></a>`INC-012` | Alte SymbolRefs und SourceRanges MÜSSEN gegen den jeweiligen Snapshot beziehungsweise die Revision geprüft werden. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Cross-Snapshot- und Cross-Revision-Abfragen schlagen typisiert fehl. |
| <a id="inc-013"></a>`INC-013` | Abbruch MUSS die Quelle erhalten, unveröffentlichte Teilergebnisse verwerfen und einen gültigen Folgelauf erlauben. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Abbruch an jeder Phasengrenze, danach frische/incrementelle Ergebnisgleichheit. |
| <a id="inc-014"></a>`INC-014` | Cache-Speicher MUSS begrenzt werden; gehaltene veröffentlichte Snapshots bleiben gültig. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Kleine Limits erzwingen Eviction ohne Ergebnisverlust. |
| <a id="inc-015"></a>`INC-015` | Ein Analysefehler in einem unabhängigen Workspace-Modell DARF eine gültige angeforderte Wurzelhülle nicht blockieren. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Zwei unabhängige Wurzeln, eine davon ungültig. |
| <a id="inc-016"></a>`INC-016` | Fachliche Modell-Overlays und Betriebscaches MÜSSEN getrennt bleiben. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Overlay-Fingerprint verändert Ergebnis; Cache-Eviction verändert es nicht. |
| <a id="inc-017"></a>`INC-017` | Feingranulare semantische Wiederverwendung SOLL erst nach Messungen und korrekter Abhängigkeitsinstrumentierung eingeführt werden. | Geltend | [Vertrag](workspace-and-lsp.md#bedingte-übernahme-einer-abgetrennten-analyse) | Benchmark und Gleichheitsnachweis vor Aktivierung einer Optimierung. |
| <a id="inc-018"></a>`INC-018` | Veraltete Ergebnisse DÜRFEN aktuelle Diagnostik, Quellverweise und Schreiboperationen nicht ersetzen. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Ergebnis von Revision n trifft nach Revision n+1 ein. |
| <a id="inc-019"></a>`INC-019` | Ein Konfigurations- oder Resolverwechsel MUSS als Workspace-Änderung behandelt werden. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Gleicher Quelltext, anderer Importstand führt zu neuer Analyse. |
| <a id="inc-020"></a>`INC-020` | Sitzungen MÜSSEN ohne globale Compiler-Locks, globale Listener und globale mutierbare Namensregister isoliert sein. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Zwei Sitzungen mit gleichnamigen, unterschiedlichen Modellen. |
| <a id="inc-021"></a>`INC-021` | Gemeinsam zu übernehmende Quellen MÜSSEN über einen atomaren bedingten Batch installierbar sein. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Ein Konflikt in einer von drei Quellen verhindert jede Teilübernahme. |
| <a id="inc-022"></a>`INC-022` | Eine ausserhalb der Workspace-Ownerqueue berechnete Analyse MUSS bedingt als Wiederverwendungsstand übernommen werden können; veraltete Ergebnisse dürfen weder Caches noch aktuelle Veröffentlichung ersetzen. | Geltend | [Vertrag](workspace-and-lsp.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Capture, paralleles Quellupdate, verspätete Analyse und anschliessender Reuse-Lauf. |

## LSP

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="lsp-001"></a>`LSP-001` | Der JVM-LSP MUSS ein dünner LSP4J-Adapter um Workspace und LanguageQueries sein. | Geltend | [Vertrag](workspace-and-lsp.md) | Keine zweite Binder-/Typprüfungsimplementierung im LSP-Modul. |
| <a id="lsp-002"></a>`LSP-002` | `initialize`, `initialized`, `shutdown`, `exit` und Dokumentlebenszyklus MÜSSEN protokollkonform funktionieren. | Geltend | [Vertrag](workspace-and-lsp.md) | Prozess-/Protokolltest einschliesslich sauberem Shutdown. |
| <a id="lsp-003"></a>`LSP-003` | Geöffnete Dokumente MÜSSEN aus Clientinhalt analysiert werden; Dateiwatcher dürfen diesen nicht überschreiben. | Geltend | [Vertrag](workspace-and-lsp.md) | Ungespeicherte Änderung und gleichzeitige Dateimeldung. |
| <a id="lsp-004"></a>`LSP-004` | `didChange`-Änderungen MÜSSEN sequenziell interpretiert und als atomarer SourceUpdate übernommen werden. | Geltend | [Vertrag](workspace-and-lsp.md) | Mehrere abhängige Änderungen innerhalb einer Notification. |
| <a id="lsp-005"></a>`LSP-005` | Diagnostik MUSS aus Diagnostic-Daten entstehen; Logparsing ist verboten. | Geltend | [Vertrag](workspace-and-lsp.md) | Gleiche Codes/Ranges in CLI-JSON, LSP und Browser. |
| <a id="lsp-006"></a>`LSP-006` | Leere aktuelle Diagnostik MUSS frühere Diagnostik ersetzen. | Geltend | [Vertrag](workspace-and-lsp.md) | Fehler korrigieren; Client empfängt leere Liste. |
| <a id="lsp-007"></a>`LSP-007` | Navigation, Hover, Dokumentgliederung und elementare Completion MÜSSEN für die P1-Strecke funktionieren. | Geltend | [Vertrag](workspace-and-lsp.md) | Import, Vererbung und fehlerhafter Nachbar im Editorfixture. |
| <a id="lsp-008"></a>`LSP-008` | Das Startkommando MUSS stdio als standardisierten Transport anbieten; stdout enthält nur Protokoll. | Geltend | [Vertrag](workspace-and-lsp.md) | Logausgaben und Stacktraces verunreinigen den Kanal nicht. |
| <a id="lsp-009"></a>`LSP-009` | Globale Compile-Locks und Manipulation bestehender EhiLogger-Listener DÜRFEN nicht Teil der neuen Integration sein. | Geltend | [Vertrag](workspace-and-lsp.md) | Architekturprüfung und parallele unabhängige Serverinstanzen. |
| <a id="lsp-010"></a>`LSP-010` | Capabilities DÜRFEN nur für tatsächlich implementierte und geprüfte Funktionen angekündigt werden. | Geltend | [Vertrag](workspace-and-lsp.md) | Capability-Snapshot je Phase. |
| <a id="lsp-011"></a>`LSP-011` | Request-Abbruch MUSS in den Compiler weitergeleitet oder durch dokumentierten Worker-Abbruch umgesetzt werden. | Geltend | [Vertrag](workspace-and-lsp.md) | Abbruchantwort und anschliessend funktionsfähige Sitzung. |
| <a id="lsp-012"></a>`LSP-012` | Workspace-weite Referenzsuche und Rename MÜSSEN Vollständigkeit und Revisionskonsistenz prüfen. | Geltend | [Vertrag](workspace-and-lsp.md) | Mehrdatei-Rename, Kollision, fehlende Quelle, Stale-Ergebnis. |
| <a id="lsp-013"></a>`LSP-013` | Semantic Tokens, Folding und Formatierung SOLLEN dieselbe Syntax-/Semantikbasis nutzen. | Geltend | [Vertrag](workspace-and-lsp.md) | Keine Regex-Nachimplementierung semantischer Namensauflösung. |
| <a id="lsp-014"></a>`LSP-014` | Eine Diagrammabfrage MUSS denselben Snapshot wie ihre Navigation verwenden. | Geltend | [Vertrag](workspace-and-lsp.md) | Modelländerung während Layout; alte Quellverweise abgelehnt. |
| <a id="lsp-015"></a>`LSP-015` | Repositoryänderungen MÜSSEN die bereits geöffnete abhängige Dokumentanalyse aktualisieren. | Geltend | [Vertrag](workspace-and-lsp.md) | Importdatei extern aktualisiert, keine Clienttextänderung. |
| <a id="lsp-016"></a>`LSP-016` | Transport-, Compiler- und internes Fehlerverhalten MÜSSEN auseinandergehalten werden. | Geltend | [Vertrag](workspace-and-lsp.md) | Ungültiges JSON-RPC, ungültiges INTERLIS und interner Fehler erhalten verschiedene Antworten. |

## RES

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="res-001"></a>`RES-001` | Engine und SourceProvider-Snapshot DÜRFEN keine implizite IO ausführen. | Geltend | [Vertrag](repositories.md) | In-Memory- und Browserlauf; fehlende Quelle wird als PendingSourceRequest sichtbar. |
| <a id="res-002"></a>`RES-002` | Sprache, Modellversion, Quellenrevision und Repositoryrevision MÜSSEN getrennt sein. | Geltend | [Vertrag](repositories.md) | Modelle mit gleichem Namen/verschiedenen Versionsstrings werden korrekt gewählt oder als mehrdeutig gemeldet. |
| <a id="res-003"></a>`RES-003` | Mehrere Modelle je Datei MÜSSEN korrekt auswählbar sein. | Geltend | [Vertrag](repositories.md) | ByName/BySource und Importbindung testen unterschiedliche Closure. |
| <a id="res-004"></a>`RES-004` | Auflösungsentscheidungen MÜSSEN deterministisch und erklärbar sein. | Geltend | [Vertrag](repositories.md) | Gleicher Katalog trotz anderer Mapiteration ergibt gleichen Trace und Resultat. |
| <a id="res-005"></a>`RES-005` | Die Engine MUSS Resolverbindungen gegen die tatsächliche Quelle prüfen. | Geltend | [Vertrag](repositories.md) | Manipulierter Offset oder falsche Revision wird verworfen. |
| <a id="res-006"></a>`RES-006` | Neue oder entfernte Modelle MÜSSEN positive und negative Lookupabhängigkeiten invalidieren. | Geltend | [Vertrag](repositories.md) | Fehlender Import erscheint später ohne Edit des importierenden Textes. |
| <a id="res-007"></a>`RES-007` | Workspaceänderungen während eines Downloads MÜSSEN erhalten bleiben. | Geltend | [Vertrag](repositories.md) | Veralteter Batch wird atomar abgewiesen; Editoroverride bleibt aktuell. |
| <a id="res-008"></a>`RES-008` | Offlinebetrieb MUSS mit eingefrorener Closure möglich sein. | Geltend | [Vertrag](repositories.md) | Alle erforderlichen Modelle liegen vor; kein Netztestzugriff. |
| <a id="res-009"></a>`RES-009` | Datei-/HTTP-Adapter MÜSSEN Limits, Abbruch und konkrete Fehlerursachen besitzen. | Geltend | [Vertrag](repositories.md) | Timeout, 404, 403, defekte Kodierung, Redirectzyklus und grosse Antwort separat geprüft. |
| <a id="res-010"></a>`RES-010` | Byte- und Textfingerprints MÜSSEN sauber getrennt und korrekt berechnet werden. | Geltend | [Vertrag](repositories.md) | UTF-8/UTF-16/CRLF-/BOM-Provenienztests. |
| <a id="res-011"></a>`RES-011` | Repositoryindex-Leser MÜSSEN echte Indexfixtures und Referenzprofile unterstützen. | Geltend | [Vertrag](repositories.md) | Gepinnte eigene/offizielle Indexdateien; keine ad-hoc HTML-Suche als Compilerresolver. |
| <a id="res-012"></a>`RES-012` | Zyklen und grosse Closures MÜSSEN terminieren oder am definierten Budget enden. | Geltend | [Vertrag](repositories.md) | Kreisimport, Doppelimport und Modellzahlgrenze mit deterministischem Resultat. |
| <a id="res-013"></a>`RES-013` | Der JVM-CLI-Host MUSS standardmässig einen persistenten Repositorycache verwenden, online gemäss §17 Cachefristen und veröffentlichte Modell-MD5 vor einer Beschaffung prüfen und explizit offline auch ohne Lockfile aus dem lokalen Repositorystand auflösen können. | Geltend | [Vertrag](repositories.md#cache-nach-ili2c) | Erstdownload, 24h-/7Tage-Fristen, MD5 vor Download, Abschaltung, Offline ohne Netz, Lockreplay und korrupte/parallel geschriebene Einträge. |

## DIAG

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="diag-001"></a>`DIAG-001` | Der Compiler MUSS Lexer-, Parser-, Bindungs-, Typ- und Semantikfehler als unveränderliche `Diagnostic`-Werte liefern. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Ein Syntax- und ein Semantikfehler sind ohne Logauswertung über die Java-API und WASM auslesbar. |
| <a id="diag-002"></a>`DIAG-002` | Jede Diagnose MUSS einen stabil dokumentierten Code und typisierte Argumente besitzen. Ein gerenderter Text DARF NICHT ihre Identität sein. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Textänderung beeinflusst Code, Ursachenbezug und Tests auf Argumente nicht. |
| <a id="diag-003"></a>`DIAG-003` | Quellbezogene Diagnosen MÜSSEN `SourceId`, `SourceRevision` und halb offene UTF-16-Bereiche verwenden. Globale Diagnosen MÜSSEN ohne erfundene Quellposition darstellbar sein. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Tests mit CRLF, Umlauten, Emoji und globalem Auflösungsfehler. |
| <a id="diag-004"></a>`DIAG-004` | Die vollständige Diagnoseliste MUSS vom gefilterten Anzeigeergebnis getrennt bleiben. Das Compilation-Gate MUSS relevante Fehler, Warnungspolitik und Analyseabdeckung prüfen und eine typisierte Ablehnungszusammenfassung erzeugen. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Unterdrückte Folgefehler erzeugen keinen Erfolg; Warnungsablehnung wird nicht als Sprachfehler dargestellt. |
| <a id="diag-005"></a>`DIAG-005` | Ursachen und abhängige Diagnosen MÜSSEN explizit referenziert werden. Unterdrückung allein anhand von Meldungstexten ist unzulässig. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Fehlender Import verdeckt passende Folgefehler, ein unabhängiger Literalfehler bleibt sichtbar. |
| <a id="diag-006"></a>`DIAG-006` | Diagnosecodes, Argumentnamen und deren Bedeutung MÜSSEN versioniert und auf unbeabsichtigte inkompatible Änderungen geprüft werden. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Katalogvertrag und JSON-Vertragstests. |
| <a id="diag-007"></a>`DIAG-007` | Sprachlich ungültige Benutzereingaben DÜRFEN KEINE ungefangenen internen Exceptions sein. Interne Defekte MÜSSEN als solche erkennbar bleiben. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Ungültige Modelle ergeben `Rejected`; injizierter interner Defekt ergibt `Failed` mit `FailureId`. |
| <a id="diag-008"></a>`DIAG-008` | Gleichwertige Läufe mit denselben semantischen Eingaben MÜSSEN dieselbe kanonische Diagnosereihenfolge liefern. Laufzeit- und Run-IDs dürfen diese Reihenfolge nicht beeinflussen. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Wiederholung und Laufzeitvergleich mit definierter ID-Normalisierung. |
| <a id="diag-009"></a>`DIAG-009` | Diagnoseerzeugung MUSS begrenzt sein. Ein überschrittenes Ressourcenlimit DARF NICHT als erfolgreiche oder vollständig geprüfte Analyse erscheinen. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Adversarische Eingabe überschreitet konfiguriertes Limit kontrolliert und zeitnah. |
| <a id="diag-010"></a>`DIAG-010` | Quick-Fixes MÜSSEN versionierte Quellstände adressieren und separat vom Diagnosekern erzeugt werden. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Änderung an einem inzwischen modifizierten Dokument wird abgewiesen oder neu berechnet. |

## LOG

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="log-001"></a>`LOG-001` | Der portable Kern MUSS ohne konkrete Logging- und Telemetrieimplementierung funktionieren. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Abhängigkeitsprüfung und JVM-/WASM-Build. |
| <a id="log-002"></a>`LOG-002` | Jeder angenommene öffentliche Lauf MUSS genau einen Abschlusszustand besitzen: erfolgreich, abgelehnt, abgebrochen oder intern fehlgeschlagen. Der Ablehnungsgrund MUSS Quelle, fehlende Voraussetzung und unvollständige Implementierung unterscheiden. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Ereignisautomat einschliesslich Exception-, Coverage- und Abbruchpfaden. |
| <a id="log-003"></a>`LOG-003` | Eine intern kontrollierte Ausführung MUSS genau einen Abschluss-Emissionsversuch durchführen. Eine Exactly-once-Zustellung über Prozessgrenzen darf daraus nicht behauptet werden. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Testobserver zählt Abschlussversuche, einschliesslich Observerfehlern. |
| <a id="log-004"></a>`LOG-004` | Ein Fehler einer optionalen Beobachtungssenke DARF das fachliche Ergebnis nicht überschreiben und DARF keine rekursive Logging-Schleife auslösen. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Werfender Observer verändert das Compile-Ergebnis nicht. |
| <a id="log-005"></a>`LOG-005` | CLI-Ausgabekanäle, Meldungstexte, Reihenfolge und Exitcodes MÜSSEN anhand des freigegebenen Werkzeugprofils geprüft werden. Das Compiler-CLI hat ausschliesslich den Standardvertrag; externe Sprachreferenzen bleiben unabhängig. | Geltend | [Vertrag](../usage/cli.md#diagnosen-und-farbe) | [Katalog](../../testdata/verification/compiler-contracts.json) · Gepinnte CLI-Fixtures mit getrennt erfasstem stdout, stderr und Exitcode. |
| <a id="log-006"></a>`LOG-006` | Der LSP-Adapter MUSS Diagnosen direkt projizieren. Bei stdio-Transport MUSS stdout ausschliesslich Protokollnachrichten enthalten. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · LSP-Test mit parallelen Diagnosen und Betriebsereignissen. |
| <a id="log-007"></a>`LOG-007` | Strukturierte Ereignisse MÜSSEN ein dokumentiertes Schema mit Ereignisname, Laufbezug und geordneter Sequenz besitzen. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Schema- und Reihenfolgetests. |
| <a id="log-008"></a>`LOG-008` | Der Host MUSS die Vertraulichkeit von Credentials und optional von Quellinhalten wahren. Bekannte Geheimnisfelder dürfen nicht in Ereignisse übernommen werden. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Resolver- und JDBC-Tests mit markierten Testgeheimnissen. |
| <a id="log-009"></a>`LOG-009` | Häufige Ereignisse MÜSSEN ohne unbeschränkte Puffer und ohne unkontrolliert hohe Metrik-Kardinalität erfasst werden können. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Lasttest mit begrenzter Queue und dokumentierter Verwerfungsstatistik. |
| <a id="log-010"></a>`LOG-010` | CLI, JVM-Dienste und WASM MÜSSEN eigene Ausgabeadapter verwenden; globale Logger-Listener und Prozess-Systemproperties zur Steuerung einzelner Läufe sind unzulässig. | Geltend | [Vertrag](diagnostics.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Zwei unabhängige Compilerinstanzen beeinflussen einander nicht. |

## WASM

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="wasm-001"></a>`WASM-001` | Der portable Kern MUSS denselben fachlichen Java-Quellcode auf JVM und TeaVM verwenden. | Geltend | [Vertrag](bridge-protocol.md) | Gemeinsame Module, keine zweite INTERLIS-Semantik in JavaScript. |
| <a id="wasm-002"></a>`WASM-002` | Der vorhandene Syntaxversuch MUSS unter Gradle reproduziert werden. | Geltend | [Vertrag](bridge-protocol.md) | Frische Build-/Laufmanifestdatei mit Befehlen und Exitcodes. |
| <a id="wasm-003"></a>`WASM-003` | TeaVM-/Web-Image-Annotationen und Laufzeittypen DÜRFEN nicht in Modell-, Syntax- oder Semantik-APIs gelangen. | Geltend | [Vertrag](bridge-protocol.md) | Abhängigkeits-/Importprüfung. |
| <a id="wasm-004"></a>`WASM-004` | Sprache und verwendete JDK-APIs MÜSSEN getrennt auf Portabilität geprüft werden. | Geltend | [Vertrag](bridge-protocol.md) | Feature-/Bibliotheksmatrix mit ausführbaren Prüfungen. |
| <a id="wasm-005"></a>`WASM-005` | P1 MUSS eine echte semantische Strecke mit Imports, Vererbung, Domäne, Constraint und Fehlerzustand portieren. | Geltend | [Vertrag](bridge-protocol.md) | Gleichheit normalisierter JVM-/Wasm-Ausgaben. |
| <a id="wasm-006"></a>`WASM-006` | Der ausgewählte TeaVM-Produktionspfad MUSS für G1 die P1-Vertragssuite in echtem Chrome, Firefox und Safari erfolgreich ausführen; Node allein genügt nicht. | Geltend | [Vertrag](bridge-protocol.md) | Gepinnte Browser-/Versionskombinationen jeweils PASS; fehlende Umgebung hält G1 offen. |
| <a id="wasm-007"></a>`WASM-007` | Ein realer GraalVM-Web-Image-Vergleich MUSS vor der endgültigen Adapterentscheidung versucht und dokumentiert werden. | Geltend | [Vertrag](bridge-protocol.md) | Build-/Laufresultat oder reproduzierbarer konkret belegter Blocker. |
| <a id="wasm-008"></a>`WASM-008` | Das Browserprotokoll MUSS versioniert sein und grosse Zahlen, Revisionen und IDs verlustfrei übertragen. | Geltend | [Vertrag](bridge-protocol.md) | Grenzwerte jenseits 2^53, Dezimalskalen, Unicode und JSON-Roundtrip. |
| <a id="wasm-009"></a>`WASM-009` | Browser-Sitzungen MÜSSEN Änderungen, Analysen, Abfragen, Snapshotfreigabe und Schliessen unterstützen. | Geltend | [Vertrag](bridge-protocol.md) | Durchgängige Mehrdatei-Editfolge im Worker. |
| <a id="wasm-010"></a>`WASM-010` | Lange Analysen DÜRFEN den UI-Hauptthread nicht belegen. | Geltend | [Vertrag](bridge-protocol.md) | Standardclient führt Compiler in eigenem Worker aus. |
| <a id="wasm-011"></a>`WASM-011` | Abbruch und veraltete Antworten MÜSSEN mit dem tatsächlichen Ausführungsmodell vereinbar sein. | Geltend | [Vertrag](bridge-protocol.md) | Laufender Worker kann beendet und aus Hostzustand neu aufgebaut werden. |
| <a id="wasm-012"></a>`WASM-012` | Wasm und zugehörige Runtime-/Loaderdateien MÜSSEN zusammen versioniert ausgeliefert werden. | Geltend | [Vertrag](bridge-protocol.md) | Frische Installation benötigt keine zufällig vorhandene alte Runtime. |
| <a id="wasm-013"></a>`WASM-013` | Wasm-/JS-/JVM-Vergleiche MÜSSEN denselben Eingabestand und dieselben Optionen verwenden. | Geltend | [Vertrag](bridge-protocol.md) | Input-Fingerprint und Fixture-Pins im Testmanifest. |
| <a id="wasm-014"></a>`WASM-014` | Kein Ziel DARF bei fehlender Verbraucherfähigkeit gültige Sprachelemente stillschweigend weglassen. | Geltend | [Vertrag](bridge-protocol.md) | Explizite Capabilities und Unsupported-Projektionsresultate. |
| <a id="wasm-015"></a>`WASM-015` | Langzeitsitzungen MÜSSEN begrenzte Snapshot-/Cachehaltung und explizite Freigabe besitzen. | Geltend | [Vertrag](bridge-protocol.md) | Wiederholte Edits mit begrenztem Speicherwachstum. |
| <a id="wasm-016"></a>`WASM-016` | Native Image, Web Image und GraalWasm MÜSSEN in Code und Dokumentation korrekt unterschieden werden. | Geltend | [Vertrag](bridge-protocol.md) | Benannte Tasks/Artefakte bezeichnen ihr reales Ziel. |
| <a id="wasm-017"></a>`WASM-017` | Browser-Repositoryauflösung MUSS im Host liegen; der Kern erhält Quellen und Bindungen. | Geltend | [Vertrag](bridge-protocol.md) | Compilerfixture funktioniert ohne Netzberechtigung. |
| <a id="wasm-018"></a>`WASM-018` | Performanceaussagen MÜSSEN als Messungen, Ziele oder offene Hypothesen gekennzeichnet sein. | Geltend | [Vertrag](bridge-protocol.md) | Rohdaten, Hardware-/Runtimeversion und Messmethode für jede veröffentlichte Zahl. |

## VIZ

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="viz-001"></a>`VIZ-001` | Eine einzige rendererunabhängige Projektion MUSS die fachliche Diagrammstruktur erzeugen. | Geltend | [Vertrag](projection.md) | Zwei Renderer zeigen dieselben Knoten, Rollen und Beziehungen. |
| <a id="viz-002"></a>`VIZ-002` | Modell-, Topic-, Klassen-, Domänen- und Vererbungsdarstellung MUSS bereits die P1-Strecke begleiten. | Geltend | [Vertrag](projection.md) | Durchgängiges Editor-/Wasm-Fixture mit Navigation. |
| <a id="viz-003"></a>`VIZ-003` | Partielle Analysen MÜSSEN ihren Zustand und fehlende Bezüge erkennbar machen. | Geltend | [Vertrag](projection.md) | Fehlender Import erzeugt markierten Stub und keinen erfundenen Zieltyp. |
| <a id="viz-004"></a>`VIZ-004` | Diagramme MÜSSEN SnapshotId und Quellrevisionen erhalten. | Geltend | [Vertrag](projection.md) | Alte Diagrammanker werden nach einer Quelländerung geprüft. |
| <a id="viz-005"></a>`VIZ-005` | Knoten-/Kantenidentitäten MÜSSEN bei eindeutig unveränderter Semantik innerhalb einer Sitzung stabil bleiben. | Geltend | [Vertrag](projection.md) | Kommentaränderung und Einfügen einer fremden Assoziation ändern bestehende IDs nicht. |
| <a id="viz-006"></a>`VIZ-006` | Fachliche Namen DÜRFEN nicht als einzige Identität dienen. | Geltend | [Vertrag](projection.md) | Gleichnamige Fehlerdeklarationen und Umbenennung. |
| <a id="viz-007"></a>`VIZ-007` | Assoziationen mit Attributen oder mehr als zwei Rollen MÜSSEN vollständig als eigene Elemente darstellbar sein. | Geltend | [Vertrag](projection.md) | N-äre und attributierte Assoziationsfixtures. |
| <a id="viz-008"></a>`VIZ-008` | Lokale, geerbte und überschriebene Elemente MÜSSEN unterscheidbar sein. | Geltend | [Vertrag](projection.md) | Effektive Reihenfolge stimmt mit Metamodellqueries überein. |
| <a id="viz-009"></a>`VIZ-009` | Filter MÜSSEN alle Renderer aus denselben Optionen ableiten. | Geltend | [Vertrag](projection.md) | Rendererübergreifende Projektionstests für dieselben Filter. |
| <a id="viz-010"></a>`VIZ-010` | Layoutpositionen DÜRFEN den Compilergraphen nicht verändern. | Geltend | [Vertrag](projection.md) | Verschieben/Filtern verändert keine Semantik oder Quellrevision. |
| <a id="viz-011"></a>`VIZ-011` | Fehlende Rendererfähigkeiten MÜSSEN explizit ausgewiesen werden. | Geltend | [Vertrag](projection.md) | Keine stille Auslassung n-ärer Beziehungen oder falsche Gültigkeitsaussage. |
| <a id="viz-012"></a>`VIZ-012` | Ausgabeformate MÜSSEN Bezeichner, Dokumentation und Labels korrekt escapen. | Geltend | [Vertrag](projection.md) | Sonderzeichen, Zeilenwechsel und rendererähnlicher Modelltext. |
| <a id="viz-013"></a>`VIZ-013` | Statische Exporte MÜSSEN deterministisch sein. | Geltend | [Vertrag](projection.md) | Gleiche Projektion ergibt byteidentischen Mermaid-/PlantUML-Text. |
| <a id="viz-014"></a>`VIZ-014` | Die interaktive Browseransicht SOLL vorhandene Sprotty-/elkjs-Arbeit wiederverwenden. | Geltend | [Vertrag](projection.md) | Kleiner geprüfter Adapter statt neuer fachlicher Rendererlogik. |
| <a id="viz-015"></a>`VIZ-015` | GLSP-basierte grafische Modellbearbeitung DARF nicht Voraussetzung einer lesenden Visualisierung werden. | Geltend | [Vertrag](projection.md) | P1 ohne GLSP-Server betreibbar; späterer Adapter separat. |
| <a id="viz-016"></a>`VIZ-016` | Eine Diagrammbegrenzung MUSS ihren reduzierten Umfang ausdrücklich melden. | Geltend | [Vertrag](projection.md) | Kleines Knoten-/Kantenbudget erzeugt LIMITED, keine scheinbar vollständige Ausgabe. |
| <a id="viz-017"></a>`VIZ-017` | Kanonische Namen und übersetzte Anzeigenamen MÜSSEN getrennt bleiben. | Geltend | [Vertrag](projection.md) | Sprache wechseln verändert Labels, nicht fachliche Identität. |
| <a id="viz-018"></a>`VIZ-018` | Export und interaktive Navigation MÜSSEN aus demselben Quellstand entstehen. | Geltend | [Vertrag](projection.md) | Änderung während Layout/Export führt zu Stale-Prüfung. |

## STACK

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="stack-001"></a>`STACK-001` | Der Compiler MUSS alle für Transfer, Validierung und DB-Abbildung relevanten semantischen Informationen typisiert und eindeutig bereitstellen. | Geltend | [Vertrag](../design/transfer-validation-database.md) | Semantische Goldens gegen ausgewählte Altmodelle einschliesslich Reihenfolge, Übersetzung, Views und Rollen. |
| <a id="stack-002"></a>`STACK-002` | Ein gültiges Sprachkonstrukt DARF wegen einer fehlenden Verbraucherfähigkeit nicht durch den Compiler abgelehnt werden. | Geltend | [Vertrag](../design/transfer-validation-database.md) | Gültiges Modell ergibt Schema; absichtlich begrenzter Projektor liefert `Unsupported`. |
| <a id="stack-003"></a>`STACK-003` | Pläne MÜSSEN unveränderlich, ihren Eingaben zugeordnet und ohne verborgene Änderung des CompiledSchema erzeugt werden. | Geltend | [Vertrag](../design/transfer-validation-database.md) | Parallel erzeugte Projektionen beeinflussen sich nicht; veränderte Optionen ergeben passende Fingerprints. |
| <a id="stack-004"></a>`STACK-004` | Sprachversion, Transferprofil und DB-Kompatibilitätsprofil MÜSSEN getrennte Identitäten besitzen. | Geltend | [Vertrag](../design/transfer-validation-database.md) | API und Cachetests erlauben keine Ableitung ausschliesslich aus `ILI23`/`ILI24`. |
| <a id="stack-005"></a>`STACK-005` | Kanonische Symbolidentität, dargestellter Name und tatsächlich verwendeter Transfername MÜSSEN getrennt bleiben. | Geltend | [Vertrag](../design/transfer-validation-database.md) | Übersetzungsmodell mit abweichenden Namen wird korrekt projiziert. |
| <a id="stack-006"></a>`STACK-006` | Effektive Attribute und Rollen MÜSSEN ihre fachlich definierte Reihenfolge, Überschreibungen und Herkunft behalten. | Geltend | [Vertrag](../design/transfer-validation-database.md) | Golden für Vererbung, überschriebenes Attribut und eingebettete Assoziationsrolle. |
| <a id="stack-007"></a>`STACK-007` | Verbraucher MÜSSEN ihre Fähigkeiten explizit ausweisen und nicht unterstützte Merkmale vor unbeabsichtigtem Datenverlust erkennen. | Geplant | [Vertrag](../design/transfer-validation-database.md) | Planung meldet alle relevanten fehlenden Fähigkeiten; kein stilles Weglassen. |
| <a id="stack-008"></a>`STACK-008` | Ein Transferreader MUSS mindestens auf Objektebene streamen und DARF nicht den ganzen Transfer zwingend materialisieren. | Geplant | [Vertrag](../design/transfer-validation-database.md) | Grosser Transfer bei festem Arbeitsspeicherbudget; dokumentierte Obergrenze für ein einzelnes Objekt. |
| <a id="stack-009"></a>`STACK-009` | IOM-/iox-Kompatibilität MUSS getrennt als Datenmodell-, Ereignis- und gegebenenfalls Java-API-Vertrag behandelt werden. | Geplant | [Vertrag](../design/transfer-validation-database.md) | Adaptervertrag mit verschachtelten Strukturen, Referenzen, Reihenfolgen und undefinierten Werten. |
| <a id="stack-010"></a>`STACK-010` | Datenvalidierung MUSS lokale und transferweite Regeln unterscheiden; ein lokaler Modus darf keine vollständige Validität behaupten. | Geplant | [Vertrag](../design/transfer-validation-database.md) | Bericht mit geprüften und ausgelassenen Regelklassen. |
| <a id="stack-011"></a>`STACK-011` | Ausdrucksauswertung MUSS die getypte Compilersemantik und exakte INTERLIS-Zahlendarstellung verwenden. | Geplant | [Vertrag](../design/transfer-validation-database.md) | Ausdrücke werden nicht erneut aus Rohtext interpretiert; exakte Grenzfalltests. |
| <a id="stack-012"></a>`STACK-012` | Geometrieprüfungen MÜSSEN wiederholte Arbeit vermeiden, fachlich relevante Rundungs-/Bogenregeln aber erhalten. | Geplant | [Vertrag](../design/transfer-validation-database.md) | Geometrieadapter- und Cachetests mit Bögen, Präzision und geänderten Optionen. |
| <a id="stack-013"></a>`STACK-013` | Validierung MUSS ein explizites Ressourcenbudget unterstützen; für die vorgesehene grosse Betriebsvariante ist ein Gesamtbudget von höchstens 64 GB planbar. | Geplant | [Vertrag](../design/transfer-validation-database.md#geometrie-und-speicherbudget) | Lasttest berücksichtigt Heap, Native Memory, Caches und Puffer; Überschreitung wird kontrolliert behandelt. |
| <a id="stack-014"></a>`STACK-014` | Laufzeitabhängige GENERIC-/DEFERRED-Konkretisierung MUSS als gültiger Modellzustand erhalten und über passenden Kontext geprüft werden. | Geltend | [Vertrag](../design/transfer-validation-database.md#basketabhängige-domänenkonkretisierung) | Gültige abstrakte Domäne kompiliert; fehlende oder ungültige Transferkonkretisierung wird separat diagnostiziert. |
| <a id="stack-015"></a>`STACK-015` | Konfigurations- und CRS-/Übersetzungs-Overlays MÜSSEN explizit, unveränderlich und Teil des Plan-Fingerprints sein. | Geltend | [Vertrag](../design/transfer-validation-database.md) | Verschiedene Overlays teilen keine inkorrekten Pläne oder Caches. |

## DB

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="db-001"></a>`DB-001` | Bestehende ili2db-Schemata und ihre Metadaten MÜSSEN unter ausdrücklich benannten Referenzprofilen weiterverwendbar sein. | Geplant | [Vertrag](../design/transfer-validation-database.md) | Bidirektionale Interoperabilitätsmatrix mit gepinnten Altversionen. |
| <a id="db-002"></a>`DB-002` | Bereits gespeicherte Namens- und Abbildungszuordnungen MÜSSEN bei Wiederverwendung massgeblich sein. | Geplant | [Vertrag](../design/transfer-validation-database.md#namemapping-und-dialektgrenze) | Bestehende gekürzte oder kollidierte Namen bleiben erhalten. |
| <a id="db-003"></a>`DB-003` | SQL-Namen, technische Tabellen/Spalten, Constraints, Metadaten und Abbildungsstrategien MÜSSEN als getrennt prüfbare Verträge behandelt werden. | Geplant | [Vertrag](../design/transfer-validation-database.md) | Strukturelle Schema-Goldens und Metadatenvergleiche. |
| <a id="db-004"></a>`DB-004` | Planung und Ausführung MÜSSEN getrennt sein; Schemaänderungen dürfen nicht als Nebenwirkung einer Modellanalyse auftreten. | Geplant | [Vertrag](../design/transfer-validation-database.md#planung) | `SchemaPlanner` benötigt keine schreibende Verbindung und erzeugt reviewbaren Plan. |
| <a id="db-005"></a>`DB-005` | Datenbankdialekte MÜSSEN ihre tatsächlichen Unterschiede kapseln. Ein erster PostgreSQL-/PostGIS-Umfang darf andere geplante Profile nicht fälschlich als unterstützt melden. | Geplant | [Vertrag](../design/transfer-validation-database.md) | Capability- und Dialekttests. |
| <a id="db-006"></a>`DB-006` | Ein Import-/Export-Roundtrip ausschliesslich mit dem neuen Werkzeug DARF nicht als Nachweis der ili2db-Kompatibilität gelten. | Geplant | [Vertrag](../design/transfer-validation-database.md) | Alt→Neu und Neu→Alt mit realen Operationen. |
| <a id="db-007"></a>`DB-007` | Fehlende oder widersprüchliche vorhandene Metadaten MÜSSEN diagnostiziert werden; stilles Neuerfinden ihrer Bedeutung ist unzulässig. | Geplant | [Vertrag](../design/transfer-validation-database.md) | Absichtlich beschädigte Mappingmetadaten werden erkannt. |
| <a id="db-008"></a>`DB-008` | Transaktions-, Wiederaufnahme- und Abbruchverhalten MÜSSEN je Operation und Dialekt explizit sein. | Geplant | [Vertrag](../design/transfer-validation-database.md#ausführung-und-leistung) | Abbruch vor/nach Batch und injizierter SQL-Fehler ohne ungemeldeten Teilzustand. |
| <a id="db-009"></a>`DB-009` | JDBC-Batching und Datenbankzugriffe SOLLEN Roundtrips reduzieren; geänderte Fehlerlokalisierung oder Objektzuordnung müssen dabei kontrolliert bleiben. | Geplant | [Vertrag](../design/transfer-validation-database.md) | Gemessene Statement-/Roundtripzahlen und Fehlerzuordnung im Batch. |
| <a id="db-010"></a>`DB-010` | Neue fachliche Modelloptimierungen DÜRFEN historische Abbildungsprofile nicht stillschweigend verändern. | Geplant | [Vertrag](../design/transfer-validation-database.md) | Dieselben Eingaben unter altem Profil ergeben dieselbe vertragliche Abbildung. |

## TEST

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="test-001"></a>`TEST-001` | Jede implementierte normative Anforderung MUSS auf einen Test, eine reproduzierbare Inspektion oder eine begründete andere Abnahme abgebildet werden. | Geltend | [Vertrag](../development/testing.md) | Aktualisierte Anforderungs-/Abdeckungsmatrix. |
| <a id="test-002"></a>`TEST-002` | Referenztools, Grammatik, Testkorpora und externe Suites MÜSSEN über unveränderliche Stände und Integritätsnachweise festgelegt werden. | Geltend | [Vertrag](../development/testing.md) | Manifest und überprüfte Downloads; kein stilles `latest`. |
| <a id="test-003"></a>`TEST-003` | Sprachtests MÜSSEN 2.3 und 2.4 ausdrücklich unterscheiden; ausgeschlossene 1.0-/2.2-Fälle dürfen weder als bestanden noch als Regression der zugesagten Sprachen zählen. | Geltend | [Vertrag](../development/testing.md) | Bericht mit ausgewähltem und ausgeschlossenem Umfang. |
| <a id="test-004"></a>`TEST-004` | Akzeptanz-/Ablehnungstests MÜSSEN durch semantische Vergleiche ergänzt werden. | Geltend | [Vertrag](../development/testing.md) | Typ-, Bindungs-, Reihenfolge- und Constraint-Goldens. |
| <a id="test-005"></a>`TEST-005` | Das endgültige Compiler-Gate MUSS vollständige Prüfung der angeforderten Root-Closure verlangen. Teilweise erkannte Syntax darf kein Erfolgsschema erzeugen. | Geltend | [Vertrag](../development/testing.md) | Negativtests für Recovery und unvollständige Abdeckung. |
| <a id="test-006"></a>`TEST-006` | Der inkrementelle Stand MUSS nach jeder relevanten Editierfolge dem frischen Lauf auf denselben Quellen entsprechen. | Geltend | [Vertrag](../development/testing.md) | Deterministische Editiersequenzen und normalisierter Ergebnisvergleich. |
| <a id="test-007"></a>`TEST-007` | Die portable Strecke MUSS fachlich identische Fixtures auf JVM und aktivem WASM-Ziel ausführen. | Geltend | [Vertrag](../development/testing.md) | Maschinenlesbarer Paritätsbericht. |
| <a id="test-008"></a>`TEST-008` | Browserunterstützung DARF nur für tatsächlich getestete Browser-/Versionenkombinationen behauptet werden. | Geltend | [Vertrag](../development/release.md#pflichtnachweise) | Ausführbare Browsermatrix mit Laufzeitvoraussetzungen. |
| <a id="test-009"></a>`TEST-009` | Fuzz- und Negativtests MÜSSEN begrenzte Laufzeit, Speicher und Ausgabe besitzen; Hänger und interne Defekte werden als Fehler klassifiziert. | Geltend | [Vertrag](../development/testing.md) | Reproduzierbare Seeds und kontrollierte Ressourcenlimits. |
| <a id="test-010"></a>`TEST-010` | Benchmarkresultate MÜSSEN Hardware, Laufzeit, Eingaben, Optionen, Warmup und Messmethode nennen. | Geltend | [Vertrag](../development/testing.md) | Versionierter Messbericht, ohne aus kleinen Beispielen abgeleitete Produktionszusagen. |
| <a id="test-011"></a>`TEST-011` | Bekannte Abweichungen MÜSSEN einzeln begründet, einem Verantwortlichen beziehungsweise Issue und einem Umfang zugeordnet sein. | Geltend | [Vertrag](../development/testing.md) | Überprüfter Baselineeintrag; kein automatisches Umwandeln aller FAILs in XFAIL. |
| <a id="test-012"></a>`TEST-012` | Normative Erwartungen MÜSSEN eine Referenz oder ausdrückliche offene Entscheidungsnotiz besitzen. Unsichere Normregeln dürfen nicht als erfundene Goldens festgeschrieben werden. | Geltend | [Vertrag](../development/testing.md) | Fixture-Metadaten mit Abschnitt/Quelle oder offenem Reviewstatus. |
| <a id="test-013"></a>`TEST-013` | Reale Modellkorpora MÜSSEN lokal reproduzierbar und unabhängig von einem beweglichen Online-Repository testbar sein. | Geltend | [Vertrag](../development/testing.md) | Gefrorener Quellenbestand mit vollständig aufgelöster Abhängigkeit. |
| <a id="test-014"></a>`TEST-014` | Abbruch, Fehler, Observerausfall und veraltete Publikation MÜSSEN die gleichen Qualitätsgates wie der Erfolgsfall erhalten. | Geltend | [Vertrag](../development/testing.md) | Zustands- und Integrationstests. |
| <a id="test-015"></a>`TEST-015` | Architekturtests MÜSSEN verbotene Abhängigkeiten und nach aussen geleakte ANTLR-/Host-Typen erkennen. | Geltend | [Vertrag](../development/testing.md) | Abhängigkeits- und öffentliche API-Prüfung. |

## COMPAT

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="compat-001"></a>`COMPAT-001` | Kompatibilität MUSS getrennt für Sprache, CLI, Diagnostik, Metamodell, Transfer, Java-API und DB ausgewiesen werden. | Geltend | [Vertrag](norm-decisions.md) | Benannte Profile und Matrix; kein pauschaler Kompatibilitätsclaim. |
| <a id="compat-002"></a>`COMPAT-002` | CLI-Exitcodes und Ausgabekanäle MÜSSEN für das freigegebene Werkzeugprofil geprüft werden; der Compiler-CLI-Standardvertrag ersetzt ausschliesslich dessen frühere Bedienparität. | Geltend | [Vertrag](../development/testing.md) | Getrennte stdout-/stderr-/Exitcode-Fixtures. |
| <a id="compat-003"></a>`COMPAT-003` | Ein semantischer Unterschied zum Altcompiler MUSS gegen Norm und Verbraucherwirkung bewertet werden. | Geltend | [Vertrag](../development/testing.md) | Dokumentierte Entscheidung: Regression, bewusste Normkorrektur oder offener Fall. |
| <a id="compat-004"></a>`COMPAT-004` | Ein Legacy-Java-Adapter ist derzeit nicht Bestandteil von P3/P4; bei Wiederaufnahme MUSS er echte verwendete Objektgraph- und gegebenenfalls API-/ABI-Verträge prüfen. | Geltend | [Vertrag](../development/testing.md) | Eigener Auftrag mit gepinnten Verbrauchern; blosse Gettergleichheit genügt nicht. |
| <a id="compat-005"></a>`COMPAT-005` | Datenbankkompatibilität MUSS bidirektionale Operationen gegen das bisherige Werkzeug umfassen. | Geplant | [Vertrag](../design/transfer-validation-database.md) | Alt→Neu und Neu→Alt pro unterstütztem DB-Profil. |
| <a id="compat-006"></a>`COMPAT-006` | Ein Profilwechsel MUSS ausdrücklich getestet und dokumentiert werden; bestehende Datenbanken dürfen nicht still einem neuen Profil zugeordnet werden. | Geplant | [Vertrag](../design/transfer-validation-database.md) | Mehrdeutigkeits- und Migrationsfälle mit alten Metadaten. |
| <a id="compat-007"></a>`COMPAT-007` | Nicht unterstützte Verbrauchermerkmale MÜSSEN als fehlende Fähigkeit sichtbar bleiben und dürfen nicht still aus Goldens entfernt werden. | Geltend | [Vertrag](../development/testing.md) | Gültiges Schema mit absichtlich begrenzter Projektion. |

## ADR

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="adr-001"></a>`ADR-001` | Monorepo `interlis-java`, Namespace `guru.interlis` | Entscheidung | [Vertrag](../architecture.md) | expliziter Produkt-/Namensentscheidung |
| <a id="adr-002"></a>`ADR-002` | Java 25 ohne Preview | Entscheidung | [Vertrag](../architecture.md) | nachgewiesener Zielplattformblockade mit Variantenvergleich |
| <a id="adr-003"></a>`ADR-003` | Jenesis baut JVM-Produkte. Der Gradle-Wrapper baut die beiden Wasm-Backends. | Entscheidung | [Vertrag](../development/build.md) | konkretem unlösbarem Integrationsproblem; kein spontaner Mavenwechsel |
| <a id="adr-004"></a>`ADR-004` | ANTLR 4 als interne Syntaxbasis | Entscheidung | [Vertrag](../architecture.md) | gemessenem Recovery-/Performanceproblem mit überprüftem Alternativparser |
| <a id="adr-005"></a>`ADR-005` | SyntaxSnapshot, AnalysisSnapshot und CompiledSchema | Entscheidung | [Vertrag](../architecture.md) | Änderung muss alle drei Nutzungskategorien erhalten |
| <a id="adr-006"></a>`ADR-006` | Immutable veröffentlichte Graphen, Builder intern | Entscheidung | [Vertrag](../architecture.md) | belegtem Problem mit gleichwertigem Zustandsvertrag |
| <a id="adr-007"></a>`ADR-007` | CompiledSchema-Interface im model, interne finale Engineimplementierung | Entscheidung | [Vertrag](../architecture.md) | neuem öffentlichen Vertragsbedarf |
| <a id="adr-008"></a>`ADR-008` | Hostbeschaffung ausserhalb des Compilers | Entscheidung | [Vertrag](../architecture.md) | keine Ausnahme ohne explizite neue Hostgrenze |
| <a id="adr-009"></a>`ADR-009` | Diagnosen als Daten, Betriebsereignisse separat | Entscheidung | [Vertrag](../architecture.md) | Schemaevolution mit Migration |
| <a id="adr-010"></a>`ADR-010` | TeaVM unverändert gepinnt weiterführen, Web Image real vergleichen | Entscheidung | [Vertrag](../architecture.md) | Ergebnis der P1-Vergleichsmatrix; spätere Toolchainänderung als eigener Auftrag |
| <a id="adr-011"></a>`ADR-011` | ganze geänderte Datei zuerst neu parsen, Semantikcache graduell verfeinern | Entscheidung | [Vertrag](../architecture.md) | gemessenen Engpässen oder Editoranforderung |
| <a id="adr-012"></a>`ADR-012` | neutrale Diagrammprojektion und vorhandene Renderer | Entscheidung | [Vertrag](../architecture.md) | konkretem Rendering-/Interaktionsbedarf |
| <a id="adr-013"></a>`ADR-013` | Transfer-/Validation-/SchemaPlan | Entscheidung | [Vertrag](../design/transfer-validation-database.md) | fachlicher neuer Verbraucheranforderung |
| <a id="adr-014"></a>`ADR-014` | DB-Profile und gespeicherte Zuordnungen massgeblich | Entscheidung | [Vertrag](../design/transfer-validation-database.md) | expliziter Migration mit entsprechender Freigabe |
| <a id="adr-015"></a>`ADR-015` | alte Java-Binärkompatibilität **zurückgestellt** | Entscheidung | [Vertrag](../design/transfer-validation-database.md) | konkretem Bestandsanwendung-Auftrag mit unveränderbarem API-Bedarf |
| <a id="adr-016"></a>`ADR-016` | Die Compilerwerkzeuge unterstützen Quellformatierung über API und CLI. Modellgeneratoren und IlisMeta07/16 sind ausserhalb des Produktumfangs. | Entscheidung | [Vertrag](../architecture.md) | neuem Generator-/Exportauftrag mit Format-/Capabilitymatrix |
| <a id="adr-017"></a>`ADR-017` | Die Diagrammintegration nutzt den TypeScript-Consumer. Native C++-, C-ABI- und iox-Verbraucher bleiben eigene künftige Vorhaben. | Entscheidung | [Vertrag](../architecture.md) | gepinntem C++/C-ABI-Verbraucherauftrag |
| <a id="adr-018"></a>`ADR-018` | Performance-Korpus `https://geo.so.ch/models` | Entscheidung | [Vertrag](../architecture.md) | nicht reproduzierbarem oder lizenzrechtlich ungeeignetem Korpus |

## API

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="api-001"></a>`API-001` | Gemeinsame Typen MÜSSEN einen einzigen kanonischen Besitzer und Namen haben. | Geltend | [Vertrag](../usage/sdk.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Register, Modulgraph und reale Imports stimmen überein. |
| <a id="api-002"></a>`API-002` | Öffentliche Einstiege MÜSSEN tatsächlich konstruierbar sein. | Geltend | [Vertrag](../usage/sdk.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · PublicSdkConstructionTest gegen Artefakte. |
| <a id="api-003"></a>`API-003` | Klassen-/Methodendokumentation MUSS Vorbedingungen, Nachbedingungen, Reihenfolge, Fehler und Lebensdauer erklären. | Geltend | [Vertrag](../usage/sdk.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Vertragstest-/Javadocreview. |
| <a id="api-004"></a>`API-004` | Unterschiedliche Ablehnungs-/Abbruchursachen MÜSSEN bis zu den Adaptern unterscheidbar bleiben. | Geltend | [Vertrag](../usage/sdk.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Outcome-/CLI-/Bridge-Matrix. |
| <a id="api-005"></a>`API-005` | Snapshots und geprüfte Sichten MÜSSEN ihre Auswahl und Herkunft korrekt begrenzen. | Geltend | [Vertrag](../usage/sdk.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Selection-/Ownershiptests. |
| <a id="api-006"></a>`API-006` | Änderungen an gemeinsamen APIs MÜSSEN Verbraucher, Verträge und Tests gemeinsam aktualisieren. | Geltend | [Vertrag](../usage/sdk.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Kein Drift nach API-Änderung. |

## CLI

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="cli-001"></a>`CLI-001` | CLI und SDK MÜSSEN dieselbe CompilerEngine verwenden. | Geltend | [Vertrag](../usage/cli.md) | Fachlich identische normalisierte Resultate. |
| <a id="cli-002"></a>`CLI-002` | Nur der Prozesseinstieg DARF System.exit verwenden; die Anwendung MUSS als Bibliothek testbar sein. | Geltend | [Vertrag](../usage/cli.md) | In-Process-Tests fangen Resultate und Streams ohne JVM-Ende auf. |
| <a id="cli-003"></a>`CLI-003` | Die Standardkommandos und Optionen MÜSSEN explizit geparst werden; alte/unbekannte Aufrufe werden vor Host-IO abgelehnt. | Geltend | [Vertrag](../usage/cli.md) | Standardprozessmatrix; Exit 2 ohne Compilerstart. |
| <a id="cli-004"></a>`CLI-004` | Exitcodes, Streams und Meldungen MÜSSEN zum freigegebenen Profil passen. | Geltend | [Vertrag](../usage/cli.md) | Prozessvergleich auf zugesagten Plattformen. |
| <a id="cli-005"></a>`CLI-005` | Konfigurationspräzedenz MUSS eindeutig und erklärbar sein. | Geltend | [Vertrag](../usage/cli.md) | Konfliktfälle und Herkunftsreport. |
| <a id="cli-006"></a>`CLI-006` | Fachartefakte, Diagnostik und Betriebslogs MÜSSEN eindeutig getrennt bzw. gerahmt sein. | Geltend | [Vertrag](../usage/cli.md) | Formatierte Quellausgaben und Diagnostik bleiben trennbar; NDJSON ohne Fremdtext. |
| <a id="cli-007"></a>`CLI-007` | Ein fehlgeschlagener Lauf DARF kein unvollständiges Artefakt als Erfolg ausweisen. | Geltend | [Vertrag](../usage/cli.md) | IO-Fehler/Abbruch mit vorhandener Zieldatei. |

## SDK

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="sdk-001"></a>`SDK-001` | Der SDK-Grundfall MUSS rein aus Strings und expliziten Optionen ausführbar sein. | Geltend | [Vertrag](../usage/sdk.md) | Kompilierender Beispiel-/Vertragstest ohne Dateisystem. |
| <a id="sdk-002"></a>`SDK-002` | Öffentliche SDK-Beispiele MÜSSEN mit den ausgelieferten Artefakten getestet werden. | Geltend | [Vertrag](../usage/sdk.md) | Beispielkompilierung in CI. |
| <a id="sdk-003"></a>`SDK-003` | API-, Diagnose- und Protokollversionen MÜSSEN getrennt nachvollziehbar sein. | Geltend | [Vertrag](../usage/sdk.md) | Versionsmanifest/Migrationsfall. |
| <a id="sdk-004"></a>`SDK-004` | Modellgeneratoren sind nicht Bestandteil von P3; die Anforderung ist durch die Formatierungsanforderungen FMT-001..004 ersetzt. | Geltend | [Vertrag](../usage/sdk.md) | Keine Generator-API; Entscheidung und Ersatz sind dokumentiert. |

## FMT

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="fmt-001"></a>`FMT-001` | Die Formatierung MUSS als deterministische, idempotente öffentliche API ohne IO verfügbar sein. | Geltend | [Vertrag](formatting.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · API-Vertrag, Wiederholungslauf und Unveränderlichkeit der fachlichen Tokenfolge. |
| <a id="fmt-002"></a>`FMT-002` | Die Formatierung MUSS Kommentare, Zeichenketten, Unicode und die fachliche Bedeutung erhalten und fehlerhafte Quellen ohne Teilprodukt ablehnen. | Geltend | [Vertrag](formatting.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Positive/negative Formatfixtures, Re-Parse und semantischer Vergleich. |
| <a id="fmt-003"></a>`FMT-003` | Die CLI MUSS `format` mit stdout, `--write`, `--check` und dokumentierten Exitcodes anbieten. | Geltend | [Vertrag](formatting.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · Prozessfixtures für Ausgabe, In-Place-Schreiben, Check-Modus und Breitenoption. |
| <a id="fmt-004"></a>`FMT-004` | In-Place-Formatierung MUSS atomar sein und bei Fehlern die vorhandene Datei unverändert lassen. | Geltend | [Vertrag](formatting.md) | [Katalog](../../testdata/verification/compiler-contracts.json) · IO-Fehler-/Abbruchtest mit vorhandener Zieldatei. |

## LEGACY

| ID | Anforderung | Status | Fachvertrag | Testbindung |
|---|---|---|---|---|
| <a id="legacy-001"></a>`LEGACY-001` | Ein Adapter für den alten mutierbaren ili2c-Java-Objektgraphen bleibt zurückgestellt. | Zurückgestellt | [Vertrag](../design/transfer-validation-database.md) | Eigener Auftrag mit echten Verbrauchertests erforderlich. |
| <a id="legacy-002"></a>`LEGACY-002` | Ein alter mutabler Objektgraph DARF das neue Schema nicht verändern. | Zurückgestellt | [Vertrag](../design/transfer-validation-database.md) | Gilt erst bei Wiederaufnahme des Adapterauftrags. |
