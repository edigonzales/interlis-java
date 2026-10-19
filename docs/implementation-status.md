# Implementierungsstatus interlis-java

Stand: **18. Oktober 2026**. Auftrag: Erstimplementierung **P0 und P1** gemäss
`docs/spec/STARTPROMPT.md`, Arbeitspakete WP-00 bis WP-03 und WP-10 bis WP-17.
P2 bis P5 bleiben Zielanforderungen und sind **nicht** als erfüllt markiert.

Statuslegende: **PLANNED** = geplant, **IMPLEMENTED** = Logik vorhanden,
**VERIFIED** = bezeichnete Abnahme tatsächlich ausgeführt und bestanden,
**PARTIAL** = Teilumfang implementiert/verifiziert, **OPEN** = offen oder blockiert.

## 1. Kurzfazit

Die P0-Basis und der vertikale P1-Abschnitt sind als **Gradle-Multiprojekt**
implementiert. Ein kleiner, gepinnter ili2c-5.6.9-Referenzlauf validiert die
positiven und negativen P1-Fixtures. Die historische 610-Fall-Suite ist als
separater `conformanceCheck` für INTERLIS 2.3/2.4 integriert: 593 von 610 Fällen
(17×1.0 ausgeschlossen), aktuell 567 PASS/26 FAIL/0 ERROR. Der Compiler
analysiert mehrere Modelldateien mit Import,
Domänen, Klassen/Strukturen, Vererbung, Attributen, Assoziationsrollen,
Kardinalitäten und typisierten Mandatory-Constraints. Das Gate stellt nur bei
vollständiger Closure und vollständiger Coverage ein `CompiledSchema` aus.
Nicht implementierte Sprachkonstrukte werden als `NOT_IMPLEMENTED` sichtbar und
blockieren den Compile.

Verifiziert sind: **134 JVM-Tests**, TeaVM-WasmGC und TeaVM-JavaScript unter
Node, Chromium/Firefox/WebKit über Playwright, ein tatsächlich ausgeführter
GraalVM-Web-Image-Build samt Node- und Browservertrag (Chromium/Firefox/WebKit),
CLI-Distribution und LSP-Adaptertest.

Die P1-Abnahme ist abgeschlossen. **Akzeptierte Restlücke:** 258
Javadoc-Warnungen (API-003), vom Nutzer am 2026-10-18 ausdrücklich als
dokumentierte Restlücke akzeptiert. **Nicht bestanden** ist die 610er-Suite im
gewählten 2.3/2.4-Umfang (567 PASS/26 FAIL/0 ERROR); das ist der P2-Backlog.
Ebenfalls offen: vollständige 2.3/2.4-Semantik (P2) und Performancebudgets. Die
Plattform-Gates JVM, Node, Chromium, Firefox, Playwright-WebKit und echtes Safari
sind ausgeführt und bestanden.

## 2. Toolchain und Pins

| Bestandteil | Verwendet | Nachweis |
|---|---|---|
| Java | Eclipse Temurin 25.0.2+10, `--release 25`, keine Preview | `java -version`, `./gradlew --version` |
| Gradle | 9.8.1, Groovy-DSL, Wrapper mit SHA-256 `dce76f55…b01f` | `gradle/wrapper/gradle-wrapper.properties` |
| ANTLR | Generator und Runtime 4.13.2 | `gradle/libs.versions.toml`, Runtime-Dependencytest |
| Grammatik | geostandards-ch, Commit `f8f97238c2540a49915f8bb97ee99bbf434b74da`, unverändert | `interlis-compiler/src/main/antlr/README.md` |
| JUnit | Jupiter 5.11.4 (BOM) | `gradle/libs.versions.toml` |
| TeaVM | 0.16.0 | `interlis-wasm-teavm/build.gradle` |
| Node | 22.23.1, WasmGC-Flag `--experimental-wasm-exnref` | Ergebnisdateien |
| LSP4J | 0.23.1 | `interlis-lsp/build.gradle` |
| GraalVM | Oracle GraalVM 25.4.4.1.1+1.1 (native-image 25.0.4.1.1) mit `--tool:svm-wasm` | Web-Image-Ergebnis und Vergleich |
| Binaryen | 133 (`wasm-as`, `wasm-opt`) | Web-Image-Build |
| Playwright | 1.64.1; Chromium 1243, Firefox 1543, WebKit 2359 | `browser-results.json` |
| ili2c-Referenz | 5.6.9, ZIP-SHA-256 `0d6fc5dc…c8ee` | `docs/evidence/reference/reference-results.json` |
| Safari | Safari 27.0.1, `safaridriver` | `safari-result.json` (`passed`, echter Browservertrag) |

Maschinenlesbare Pins und Artefaktgrössen: `docs/evidence/p1/toolchain.json`.

## 3. Repository- und Modulzustand

Die Spezifikation liegt jetzt vertragsgemäss unter `docs/spec/`, die historische
Evidenz unter `docs/evidence/`. Die historische Maven-Spike-Evidenz wurde nicht
umgeschrieben oder als neuer Gradle-Lauf ausgegeben.

| Modul | Zweck | Status |
|---|---|---|
| `interlis-model` | Quellen, Diagnostik, Ausführung, semantische Werte, Graph, Query-Verträge | VERIFIED |
| `interlis-compiler` | ANTLR-Adapter, eigene Syntax, Index, Bindung, Typen, Gate, Engine | VERIFIED |
| `interlis-workspace` | Revisionen, atomare Updates, Resolver, Capture, Reuse, bedingte Annahme | VERIFIED |
| `interlis-repository` | JVM-Datei-/HTTP-Loader, Limits, typisierte Fehler | VERIFIED (Datei + HTTP: Missing/Offline/Cancel/InvalidPath/Redirect/Redirect-Limit/403/Timeout/Encoding/Größe) |
| `interlis-projection` | Diagramm-DTOs, Projektor, Mermaid/PlantUML, JSON | VERIFIED |
| `interlis-cli` | check/compile/analyze/diagram/version/capabilities, Exitcodes | VERIFIED |
| `interlis-lsp` | LSP4J-Adapter: Diagnostik, Hover, Definition, Symbols, Completion, `interlis.diagram`, `interlis.compile`, stdio-Lifecycle | VERIFIED (In-Process + stdio) |
| `interlis-wasm-bridge` | Protokoll 1.0, JSON, Sessions, Snapshots, Queries, Diagramme | VERIFIED |
| `interlis-wasm-teavm` | WasmGC/JS, Worker-Browserharness, Node-/Browserverträge | VERIFIED (inkl. Safari) |
| `interlis-wasm-webimage` | GraalVM-Web-Image-Bootstrap, Node- und Browservergleich | VERIFIED (Node, Chromium, Firefox, WebKit; Safari-WebImage nicht separat) |

Buildkonventionen: `gradle/java-conventions.gradle`, zentrale Versionen in
`gradle/libs.versions.toml`. ANTLR-Generator bleibt aus dem Runtime-Classpath;
die generierten Quellen liegen ausschliesslich unter `build/generated-src`.

## 4. Arbeitspakete und Anforderungen

### 4.1 P0

| WP | Anforderungen | Status | Umfang und Evidenz |
|---|---|---|---|
| WP-00 Repository/Gradle | BUILD-001..004, 009; AGENT-001/008/010; ARCH-001/009; NFR-009/010 | VERIFIED | Wrapper, Groovy-DSL, JDK 25, Module, zyklusfreier Graph; `./gradlew check` grün. |
| WP-01 Referenzen/Fallkorpus | TEST-/COMPAT-Grundlagen; GOAL-012 | **VERIFIED (P1-Profil; 610er-Suite separat)** | Eigene Fixtures unter `testdata/models`; ili2c 5.6.9 mit Quell-URL und SHA-256 gepinnt. Fünf explizite Fälle (2.4-Import/Vererbung, 2.3, defekt, trailing, fehlender Import) laufen gegen das Referenzwerkzeug. Zusätzlich ist die gepinnte 610er-Suite als separater `conformanceCheck` für 2.3/2.4 integriert: 593 von 610 Fällen (17×1.0 ausgeschlossen), aktuell 567 PASS/26 FAIL/0 ERROR. Der FAIL-Stand ist P2-Kompatibilitätsbacklog, kein P1-Releasegate; Clustering in `docs/evidence/conformance/backlog.md`. |
| WP-02 Syntaxspike nach Gradle | BUILD-005/006/010; SYN-012; WASM-001/002 | VERIFIED | Grammatik unverändert, ANTLR 4.13.2, bekannte Warnung 154 zu `viewDef` reproduziert; JVM, WasmGC-Node und JS-Node laufen. |
| WP-03 Diagnostik/Laufkontext | DIAG-001..005/007..009; LOG-001..004/007/010 | VERIFIED (P1-Kern) | Strukturierte Diagnosen, Katalog inklusive Doppelcode-/Argument-/Phasenprüfung, Collector, kanonische Ordnung, Cause-Suppression, RunContext/Lifecycle mit genau einem Abschlussversuch, Observer-Isolierung, Abbruch/Budget und LSP-stdout-Reinheit sind getestet. Ein produktiver Telemetrie-/Dateisink bleibt P3. |

### 4.2 P1

| WP | Anforderungen | Status | Umfang und Evidenz |
|---|---|---|---|
| WP-10 Quellen/Syntax/Positionen | SRC-001..007; SYN-001..009/011/013/014 | VERIFIED (P1-Teil) | UTF-16-Halbintervalle, CRLF/LF/CR (auch einzelnes CR und Leerzeilen), Emoji, LineMap, eigene verlustfreie Syntax, Tokens/Trivia, EOF, Trailing-Content, EDITOR-Recovery, Versionserkennung. Fragmentanalyse ist P2 und wirft explizit `UnsupportedOperationException`. |
| WP-11 Metamodell/Deklarationen | META-001..005/008/009/011/014; API-001/003 | VERIFIED (P1; API-003-Restlücke akzeptiert) | Immutable Graph, Modell/Topic/Klasse/Struktur/Domäne/Assoziation/Attribut/Rolle/Constraint, exakte Dezimalwerte inklusive `9007199254740993.0010`, Origin, stabile IDs. `ArchitectureBoundaryTest` prüft, dass öffentliche Modell-/Compiler-APIs keine ANTLR-, LSP-, HTTP-, JDBC-, UI- oder internen Typen leaken; `SnapshotOwnershipTest` prüft Copy-on-Construction, `CanonicalSourceOrderTest` die Unabhängigkeit von der Source-Map-Einfügereihenfolge, `PublicSdkConstructionTest` den minimalen String-SDK-Fall. Javadoc-Paketverträge sind für alle öffentlichen Pakete ergänzt; der geprüfte Kernvertrag steht in `docs/evidence/p1/api-review.md`. Die verbleibenden 258 Javadoc-Warnungen sind als dokumentierte Restlücke vom Nutzer akzeptiert (Entscheidung 2026-10-18) und blockieren die P1-Abnahme nicht. |
| WP-12 Importe/Bindung | RES-001..008/012; SEM-001/008 | VERIFIED (P1-Teil) | ByName/BySource-Selektoren, In-Memory-Closure, explizite Bindings, fehlender Import mit `ILI-RES`-Diagnose, Mehrdeutigkeit, negative Abhängigkeit und späteres Erscheinen einer Quelle. Kein implizites HTTP in der Engine. |
| WP-13 Typprüfung/Gate | SEM-002..005/007/009/011..015/018/019; META-010; ARCH-004 | VERIFIED (P1-Teil) | Outcome-Trennung (`Succeeded`/`Rejected`/`Cancelled`), deterministische Compilation-Fingerprints, Typauflösung, Domänen-Aliase, Vererbung/Override, effektive Reihenfolge, typisierte Constraints, Fehlerfortpflanzung, Gate, Closure-Auswahl, kein Schema bei Fehler/NOT_IMPLEMENTED. `CompiledSchemaSelectionTest` bestätigt, dass ein unabhängig defektes Modell außerhalb der gewählten Closure den Erfolg nicht verhindert. |
| WP-14 Workspace/IDE | INC-001..013/015..022; LSP-001..011/014/016 | VERIFIED (P1) | Workspace, atomare Updates, Revisionen, Capture/accept, Fresh-vs-Reuse, Later-Import, Stale-Commit und LanguageQueries implementiert; LSP4J-Adapter mit Diagnostik/Hover/Definition/Symbolen/Completion. Echter stdio-Prozess-/Shutdown-/Exit-Test. `interlis.diagram` nutzt denselben Analysis-Snapshot wie die Navigation und weist veraltete Snapshots ab. `interlis.compile` ist asynchron; `$/cancelRequest` liefert `RequestCancelled`, der Compiler beobachtet den Abbruch, die Sitzung bleibt funktionsfähig. LSP-016 trennt Protokoll- (-32601), Sprach- (`rejected`) und interne Fehler (-32603). `WorkspaceIsolationTest` belegt unabhängige Revisionen/Analysen paralleler Workspaces. Rename/References/QuickFix bleiben P3. |
| WP-15 Diagramm | VIZ-001..005/009/012/013/016; ARCH-008 | VERIFIED (P1-Teil) | Rendererunabhängiges `DiagramDocument` 1.0, Container/Nodes/Edges, Vererbung, Assoziationen, effektive Mitglieder, SourceAnchors, Mermaid/PlantUML/JSON, Budget `LIMITED`, deterministische Ausgabe; der Budgetfall ist als `LIMITED`-Dokument mit Notices getestet. `CrossSnapshotReferenceTest` weist gemischte Snapshots zurück. |
| WP-16 TeaVM/Web Image | WASM-001..013/016/017; GOAL-002/007; SEM-019 | VERIFIED (P1) | WasmGC und JS laufen unter Node; Session-Lifecycle (Close → `SESSION_NOT_FOUND`) ist getestet; echte Chromium/Firefox/WebKit- und Safari-Läufe inkl. Modelländerung, Reanalyse, exakter Dezimalzahl und Worker-Neustart bestehen. Web Image wurde mit GraalVM 25.4.4.1.1+1.1 real gebaut und unter Node sowie in Chromium, Firefox und Playwright-WebKit ausgeführt; der Größenvergleich zu 25.0.3 steht in `webimage-comparison.json`. |
| WP-17 CLI/P1-Abnahme | Kapitel 18, GOAL-007/008, API-002/004/005 | VERIFIED (P1) | `installDist`, `version`, `capabilities`, `check`/`compile`, `analyze`, `diagram`, JSON-Diagnostik, Exitcodes. Der öffentliche SDK-Fall wird als `examples/sdk/SdkExample.java` gegen die gebauten JARs kompiliert und ausgeführt. Browser- und Safari-Abnahme sind grün. |

## 5. P1-Sprachumfang

**Implementiert und typgeprüft:** Modellkopf (INTERLIS 2.3/2.4, AT/VERSION),
IMPORTS (qualified/unqualified), TOPIC inklusive `DEPENDS ON`, CLASS, STRUCTURE,
DOMAIN (Text, Numerik, Boolean, Enumeration, Collection, Koordinate/MULTICOORD,
Referenz, Alias), Attribute inklusive `(EXTENDED)`-Override,
Assoziationen und Rollen inklusive `(EXTERNAL)` für Cross-Topic-Ziele,
Kardinalität, Vererbung/Überschreibung, effektive Reihenfolge,
`MANDATORY CONSTRAINT` mit boolescher Typisierung, Pfade auf Attribute,
Literale, `DEFINED`, arithmetische/logische Operatoren.

**Explizit `NOT_IMPLEMENTED` und ablehnend:** VIEW, GRAPHIC, FUNCTION, UNIT,
CONTEXT, METAOBJECT/SIGN/REFSYSTEM, TRANSLATION, DEFERRED GENERICS, FORMAT,
OID, BLACKBOX, temporale Typen, ENUMTREEVAL, nicht-Mandatory-Constraints
(EXISTENCE, UNIQUENESS, SET, PLAUSIBILITY), externe CONSTRAINTS OF,
Full-Alignment, Attributpfad-Sonderformen, Funktionsaufrufe, `ALL`,
Parameter, Inspection. Diese Konstrukte erzeugen `ILI-IMPL-0001` und
`INCOMPLETE_SUPPORT`; sie werden weder still übersprungen noch als gültiges
Schema veröffentlicht.

`Coord`/`MULTICOORD` wird als Typ im P1-Umfang akzeptiert; für INTERLIS 2.3 wird
`MULTICOORD` als versionswidrig abgelehnt. Eine vollständige versionsabhängige
Keyword-Matrix ist P2 (SYN-004).

## 6. Öffentliche API und bewusste Ergänzungen

Die kanonischen Namen aus Kapitel 16 werden verwendet. Zusätzlich bzw. bewusst
projektintern:

- `ILI-IMPL-0001`: neuer, katalogisierter Diagnosecode für nicht implementierte
  Pflichtfähigkeiten (Kapitel 09 nennt keinen eigenen Code; die Ergänzung ist
  im Katalog und in Tests sichtbar).
- `LanguageQueries.create()` als statische Fabrik des Compiler-Query-Pakets.
- `EngineDebug` und Debug-Protokolloperationen wurden nach der Fehlersuche
  **entfernt**; es gibt keinen Produktionsschalter für Erfolg/Fehler.
- `interlis-wasm-bridge` enthält das gemeinsame, versionierte Protokoll 1.0;
  TeaVM- und Web-Image-Adapter enthalten nur Host-Interop.
- `DefaultCompiledSchema` liegt wie spezifiziert in
  `guru.interlis.compiler.internal` und wird ausschliesslich vom Gate erzeugt.

ANTLR-, LSP4J-, Graal- oder TeaVM-Typen erscheinen nicht in den öffentlichen
Kernverträgen. Der portable Kern nutzt eine eigene SHA-256-Implementierung
(`guru.interlis.source.Sha256`), weil TeaVM `java.security.MessageDigest` und
`java.util.HexFormat` nicht bereitstellt. Die Fingerprints bleiben
plattformgleich.

## 7. Tests und tatsächliche Ergebnisse

Ausführliche Evidenz: `docs/evidence/p1/test-summary.md` und die dort
referenzierten JSON-Dateien.

| Kommando | Ergebnis |
|---|---|
| `./gradlew check` | **BUILD SUCCESSFUL**, 134 Tests, 0 Fehler |
| `./gradlew :interlis-compiler:sdkExampleTest` | **SDK PASS**, Beispiel gegen gebaute JARs kompiliert und ausgeführt |
| `python3 tools/reference/reference_check.py` | **5/5 PASS** gegen ili2c 5.6.9; kleiner gepinnter P1-Fallkorpus |
| `./gradlew conformanceCheck` | **FAIL (erwartet)**: 593 2.3/2.4-Fälle, 567 PASS/26 FAIL/0 ERROR; separater P2-Backlog |
| `./gradlew javadoc` | **BUILD SUCCESSFUL**; 258 Warnungen, als API-003-Restlücke akzeptiert |
| `./gradlew checkPortable` | **SUCCESS**, JVM plus WasmGC-/JS-Node-Verträge |
| `./gradlew :interlis-wasm-teavm:browserContractTest` | **SUCCESS** für Chromium, Firefox, WebKit |
| `./gradlew :interlis-wasm-webimage:webImageBrowserTest -P…` | **SUCCESS** für Chromium, Firefox, Playwright-WebKit |
| `./gradlew :interlis-wasm-teavm:safariContractTest` | **SUCCESS**: echter Safari-WebDriver-Vertrag |
| `./gradlew checkBrowser -PgraalVmHome=… -PbinaryenHome=…` | **SUCCESS**: TeaVM-, Safari- und Web-Image-Browser |
| `./gradlew releaseCheck -PgraalVmHome=… -PbinaryenHome=…` | **SUCCESS**: Check, Portable, Browser und Web-Image-Untersuchung |
| `./gradlew :interlis-wasm-webimage:webImageProbe -PgraalVmHome=… -PbinaryenHome=…` | **SUCCESS** mit GraalVM 25.4.4.1.1; Ergebnis `webimage-result.json` |
| `./gradlew :interlis-cli:installDist` + `bin/interlis-cli check …` | **Exit 0** für gültige Fixtures; negativer Fall Exit 1 in `CliApplicationTest` |
| `./gradlew :interlis-lsp:installDist` | **SUCCESS**; In-Process-LSP-Vertragstest grün |

### 7.1 Plattformmatrix

| Ziel | Status | Umfang |
|---|---|---|
| JVM 25 | PASS | Syntax, Semantik, Gate, Workspace, Projektion, CLI, LSP-In-Process + stdio |
| TeaVM WasmGC / Node 22 | PASS | Protokoll, Import, Constraint, Diagramm, exakte Dezimalzahl |
| TeaVM JavaScript / Node 22 | PASS | Protokoll, Compile |
| Chromium | PASS | Modell öffnen/ändern, analysieren, ablehnen, kompilieren, Diagramm, Worker-Neustart |
| Firefox | PASS | derselbe Vertrag |
| WebKit (Playwright) | PASS | derselbe Vertrag; **kein** Safari-Nachweis |
| Safari (echt) | PASS | WebDriver-Session, Harness, Compile, Diagramm, exakte Dezimalzahl, Worker-Neustart |
| GraalVM Web Image / Node | PASS | Compiler-Bridge, Compile |
| GraalVM Web Image / Chromium, Firefox, Playwright-WebKit | PASS | Compiler-Bridge, Compile |
| GraalVM Web Image / Safari | **NOT_RUN** | nicht separat gelaufen; TeaVM-Safari ist PASS |

## 8. Offene Gates, Blocker und Grenzen

1. **G1 – echtes Safari.** **Geschlossen.** Safari Remote Automation ist aktiviert;
   der echte WebDriver-Vertrag läuft mit Harness, Compile, Diagramm, exakter
   Dezimalzahl und Worker-Neustart. `checkBrowser` und `releaseCheck` sind grün.
2. **Referenz-/Konformitätsumfang.** Das kleine Fünf-Fall-Profil ist P1-grün.
   Die gepinnte 610er-Suite ist für 2.3/2.4 integriert (593 Fälle; 17×1.0
   ausgeschlossen), aber mit 567 PASS/26 FAIL/0 ERROR noch nicht bestanden.
   Dieser Stand ist der P2-Kompatibilitätsbacklog; „vollständig konform“ wird
   nicht behauptet.
3. **Web Image und Safari.** Der Web-Image-Pfad wurde unter Node und in
   Chromium, Firefox und Playwright-WebKit ausgeführt. TeaVM wurde zusätzlich in
   echtem Safari geprüft; der Web-Image-Safari-Pfad ist nicht separat gelaufen.
4. **P2-Semantik.** Vollständige Typfamilien, Views, Funktionen, Einheiten,
   Formate, Übersetzungen, Graphics, Metaobjekte, Generics/Deferred,
   vollständige Constraints und die versionierte Keyword-Matrix fehlen. Sie
   sind `NOT_IMPLEMENTED` und blockieren betroffene Compiles.
5. **Fragmentanalyse** (`parseFragment`) ist P2 und wirft explizit
   `UnsupportedOperationException`.
6. **Performancebudgets** wurden nicht gemessen; es gibt keine Aussage zu
   Laufzeit/Speicher auf realen Korpora.
7. **LSP- und Workspace-Vertiefung:** Die P1-Anforderungen LSP-001..011/014/016
   sind verifiziert. Rename-/References-/QuickFix-Fähigkeit (P3), Watched-File-
   Aktualisierung und feingranulare semantische Cache-Invalidierung stehen aus.
8. **API-003 (akzeptierte Restlücke):** `./gradlew javadoc` baut, meldet aber
   258 Kommentar-/`@param`-Warnungen (133 Methoden-/Member-Kommentare, 71
   `@param`, 39 `@return`, 15 Default-Konstruktoren). Der Nutzer hat diese
   Restlücke am 2026-10-18 als dokumentiert akzeptiert; die Baseline steht in
   `docs/evidence/p1/javadoc-summary.json`.

## 9a. P2-Zwischenstand (WP-23/WP-24 abgeschlossen, 2026-10-18)

- Conformance: **566 PASS / 27 FAIL / 0 ERROR** von 593 ausgewählten 2.3/2.4-Fällen.
- JVM: **134 Tests, 0 Fehler**; `checkPortable`, `checkBrowser` und `releaseCheck` grün.
- Umgesetzt: Typfamilien (Temporal, OID/Blackbox/Alignment, FORMAT, CLASS/STRUCTURE-Werte), UNIT/unitRef, Domain-Enumerationen, Domain-Extends, Rollen-/Assoziationsregeln, Attribut-Overrides, Namens-/Endnamen-Regeln, Geometry-Typen und -Regeln, View-Deklarationen mit Alias-/Attributprüfungen, externe Constraint-Blöcke, UNIQUE-/SET-/Plausibility-Constraints, Enum-/Klassenkonstanten.
- **WP-24 Translation:** Guard entfernt; Quellmodell/Version, Modellkopf, Imports, Token-/Namenskorrespondenz, Constraints, Modellreferenzen, Einheiten und Koordinaten werden validiert. **36/36 Single-Feature-Translation-Accept-Fälle PASS**, **0** Translation-Reject-False-Accepts; Nachweis in `docs/evidence/conformance/translation-summary.json`.
- **WP-23 View-Formation:** **19/19 View-Accept-Fälle PASS**, **0/20 View-Reject-False-Accepts**; ABSTRACT-/BASIS-/Alias-/Topic-View-Regeln und View-Base-Extensions sind validiert. Nachweis in `docs/evidence/conformance/view-summary.json`.
- **WP-22 Objektpfade/Funktionen/Constraints:** alle 25 Constraint-/Ausdrucksfälle, alle 3 Funktionsfälle und die 2.4-Constraint-/Domain-Fälle PASS; **0** neue False Accepts. Nachweis in `docs/evidence/conformance/wp22-summary.json`.
- 25 weitere Translation-Accept-Fälle scheitern noch an anderen P2-Features (u. a. vollständige Line- und Metaobject-Semantik), nicht an Translation.
- Das Goal „P2 vollständig“ bleibt offen; der 490er-Stand ist der P2-Kompatibilitätsbacklog.

## 9. Nächster Arbeitsschritt (P2)

Beginne mit den verbleibenden Typ-/Ausdrucksfamilien und der vollständigen Line-/Metaobject-Semantik. WP-23 und WP-24 sind abgeschlossen. Historischer Hinweis: WP-20 (vollständige Typfamilien, numerische Genauigkeit,
Einheiten, Geometrietypen) und der versionierten Feature-/Keyword-Matrix aus
Kapitel 04. Vor jedem neuen Feature: positiven, negativen, Metamodell-/Query-
und Portabilitätsfall gemeinsam ergänzen; `AnalysisCoverage`-Feature entfernen,
erst wenn der Fall im Gate und in JVM/Node/Browser grün ist. Die kleine
ili2c-Referenzprofilprüfung ist erledigt; eine breitere Legacy-Suite bleibt eine
optionale Vertiefung. Die Plattformmatrix ist mit echtem Safari grün.

## 10. Fortsetzung ohne Chatkontext

1. `AGENTS.md` und `docs/spec/README.md` lesen.
2. Diesen Status und `docs/evidence/p1/test-summary.md` lesen.
3. `./gradlew check` und `./gradlew checkPortable` ausführen.
4. Für portable Änderungen zusätzlich `:interlis-wasm-teavm:browserContractTest`
   und die Web-Image-Probe mit den dokumentierten Properties ausführen.
5. Offene Punkte aus Abschnitt 8 zuerst bearbeiten; keine P2-Fähigkeit als
   erfüllt markieren, solange Coverage-/Negativ-/Plattformnachweise fehlen.
