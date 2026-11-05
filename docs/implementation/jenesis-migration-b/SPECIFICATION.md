# Spezifikation — Auftrag B

## 1. Zweck, Priorität und Abgrenzung

Auftrag A hat einen zweiten, POM-basierten Jenesis-JVM-Build eingeführt. Auftrag B beseitigt den dauerhaften Parallelbetrieb, setzt die neue Artefaktversorgung tatsächlich in den Verbrauchern ein und modularisiert anschliessend die eigenen Java-Produkte. Die Architekturänderungen bleiben vom Compilerverhalten getrennt.

**B-I**: neuer Buildbesitz, Wasm-Artefaktschnittstelle, Extensionintegration, CI-/Task-Überführung und Entfernung der alten JVM-Gradle-Spur. **B-II**: explizite Moduldeskriptoren, echte modulare Produktion und Starts, sichere Tests und endgültige Abnahme. Ein erfolgreicher B-I-Zwischenstand darf bei einem echten B-II-Blocker erhalten bleiben; er darf nicht als Gesamt-PASS ausgegeben werden.

Dieser Auftrag erlaubt keine Weiterarbeit an Compiler-APE oder CosmoGVM. Ein künftiger APE-Auftrag ist kein Abnahmekriterium von B. `interlis-wasm-webimage` ist trotz `native-image --tool:svm-wasm` ein bestehender Wasm-Backendverbraucher und nicht der pausierte Cosmopolitan-Auftrag.

## 2. Module und Besitzer

| Projekt | Neuer Buildbesitzer | Ziel-Modulname | Lieferung |
|---|---|---|---|
| interlis-model | Jenesis | guru.interlis.model | Bibliothek |
| interlis-compiler | Jenesis | guru.interlis.compiler | Bibliothek |
| interlis-workspace | Jenesis | guru.interlis.workspace | Bibliothek |
| interlis-repository | Jenesis | guru.interlis.repository | Bibliothek |
| interlis-projection | Jenesis | guru.interlis.projection | Bibliothek |
| interlis-documentation | Jenesis | guru.interlis.documentation | Bibliothek |
| interlis-wasm-bridge | Jenesis | guru.interlis.wasm.bridge | normale Java-Bridge, kein Codegenerator |
| interlis-compiler-cli | Jenesis | guru.interlis.compiler.cli | JVM-CLI, Main guru.interlis.cli.Main |
| interlis-lsp | Jenesis | guru.interlis.lsp | JVM-LSP, Main guru.interlis.lsp.Main |
| interlis-verification | Jenesis | guru.interlis.verification | intern, nicht publizieren |
| interlis-wasm-teavm | Gradle | kein neuer JPMS-Zwang für Backendadapter | JS/WasmGC und bestehende Browserartefakte |
| interlis-wasm-webimage | Gradle | kein neuer JPMS-Zwang für Backendadapter | bestehendes Web-Image-Ergebnis |
| frontend/diagram-ui | bestehende Node-/npm-Werkzeuge | entfällt | Renderer, Typen und Schemaartefakte |
| interlis-vscode | bestehende Node-/npm-Werkzeuge | LSP wird als Modul gestartet | VSIX mit vorhandener JVM-Spur |

Modulnamen sind Zielvorgaben und in B0 auf Namenskonflikte/Verbraucher abzugleichen. Keine automatische Namensableitung nur aus JAR-Dateinamen. Maven-Koordinaten bleiben `guru.interlis:<bisheriger-artifactId>:<Projektversion>`; die neue Modulidentität ist nicht automatisch eine neue GAV.

**Gradle besitzt nur die beiden Backendprojekte.** Eigene Java-Entry-Points dieser Projekte verbleiben dort. Damit bedeutet „ein JVM-Produzent“ nicht, dass Gradle überhaupt keine JavaCompile-Task mehr besitzen darf; verboten ist die zweite Kompilierung gemeinsamer JVM-Produktquellen.

## 3. Unveränderte Verträge

Compiler-/Modell-/Workspace-/Repository-/Formatter-/LSP-Semantik, Diagnosen und deren Reihenfolge, CLI-Optionen und Exitcodes, Quellbereiche/Revisionen, Editorprotokoll und fachliche Schemaversionen bleiben unverändert. Kein API-Renaming nur für die Buildumstellung. `Format Document` bekommt in diesem Auftrag keinen neuen Solothurn-Stil.

ANTLR, JDK, Jenesis, JUnit, LSP4J, Gson, TeaVM, Web Image und npm-Abhängigkeiten zunächst unverändert pinnen. Ein unerwartetes Werkzeughindernis wird an der gepinnten Version untersucht. Kein stilles Upgrade, Enginepatch, Fremd-JAR-Rewrite oder Wechsel des Buildframeworks. Ist eine echte Engineänderung erforderlich, blocker dokumentieren statt ungeprüft auf `main` umzusteigen.

Sources, Ressourcen, Lizenzdateien, Servicekonfiguration und JAR-Manifestinformation müssen erhalten bleiben. `module-info.class` ist eine gewollte zusätzliche Datei; daraus folgt keine Erlaubnis, andere Klassendateien, Ressourcen oder Testfälle wegzulassen.

Die vorhandene White-Box-Testabdeckung wird weder gelöscht noch durch grössere Sichtbarkeit oder pauschale Reflection ersetzt. Näheres in JPMS_AND_TESTING. Historische Testcounts sind Indikatoren, keine zu reproduzierenden magischen Konstanten. Baselineidentitäten plus erwartete neue Tests sind massgeblich.

## 4. Ziel-Entry-Point und Konfiguration

`java build/InterlisJvmBuild.java` bleibt der dokumentierte Projekteinstieg, damit kein unnötiger zweiter Java-Launcher entsteht. Der Klassenname ist historisch; sein begrenzter Dispatch darf ausdrücklich benannte Consumerprofile koordinieren. Er wird kein frei konfigurierbarer allgemeiner DAG-Scheduler.

Zielprofile (Implementierungsvorgaben; nicht Behauptungen aktueller Verfügbarkeit):

```text
build [--module NAME] [--tests CLASS_REGEX[#METHOD]]
artifacts [--module NAME]
contracts
host --profile EXAKTER_NAME
sdk-check
editor-bundle
editor-check --extension-dir ABSOLUTER_PFAD
wasm-export
wasm --profile EXAKTER_NAME --inputs ABSOLUTES_MANIFEST
jpms-check [--module NAME]
runtime-check --application compiler|lsp
release-check --profile P3
clean --scope jvm|consumer|wasm
```

`build` bleibt Default und führt normale JVM-Tests aus, nicht Browser, Wasm, Runtimeerzeugung oder Release. `artifacts` erzeugt ausschliesslich Artefakte und behauptet keinen Test-PASS. Eine intern benötigte Engineartefaktproduktion darf nicht erst normale Tests erzwingen, um deren Runner zu bauen.

Unbekannte Profile, Module und Filter oder leere erwartete Testauswahlen sind Fehler. Nicht unterstützte Mehrfachfilter nicht still akzeptieren. Argumente als Listen, nie ungequotete Shellketten. Helfer erzeugen eine klare strukturierte Ergebnisdatei; Verbraucher raten nicht anhand von stdout oder `find newest`.

Engine-API und Pin bleiben unter `build/jenesis/` unangetastet. `build/InterlisJvmBuild.java`, `build/interlis/` und `build/JENESIS-UPSTREAM.json` sind **Quellen**, kein wegwerfbares Root-Buildverzeichnis. Root-Gradle-Ausgaben und neue gemeinsame Outputs sollen unter klaren `target/`-Teilbäumen liegen. Bestehende `target/jenesis-jvm`-Verträge nicht ohne Nutzen umbenennen.

### 4.1 Sperren und Reentranz

Bestehende JVM-Locks bleiben. Ein Consumerlauf erhält zuerst einen eingefrorenen Snapshot und startet erst dann Gradle/Node. Kein Kindprozess darf auf einen vom Elternprozess gehaltenen gleichnamigen Lock warten. Gradle ruft nie Jenesis auf; Extensionbuild startet den Core höchstens einmal vor dem anschliessenden reinen Import.

Für Composite-Profile wenige feste Schritte in topologischer Reihenfolge im selben Prozess bzw. ohne reentrante CLI-Aufrufe. Aktive Runverzeichnisse nicht durch Clean entfernen. Kein Lock für den ganzen normalen JVM-Baum über einen langen Browser-/Wasm-Lauf, wenn ein immutable Snapshot genügt. Gemeinsame Ausgaben werden atomar ersetzt oder content-addressed neu angelegt.

## 5. Bestehende Adapter gezielt weiterentwickeln

### 5.1 `InterlisJvmBuild`

`parse(String[])` erweitert die exakte Whitelist, unterscheidet build-, consumer- und releasebezogene Optionen und erzeugt einen unveränderlichen Request. `main(String[])` macht Root-/Pinprüfung, dispatcht und hält nur die benötigten Locks. Profilargumente werden nicht beliebig an Shell/Gradle durchgereicht.

Bestehende Profile werden entweder weiterverwendet oder nach nachvollziehbarer Migration auf einen eindeutigen Namen reduziert. A-spezifisches `parity --baseline` gehört nach Abschluss nicht mehr in den normalen Releasepfad. Baselinevergleichscode darf als gezieltes Entwicklerwerkzeug bleiben, wenn ein konkreter gepflegter Gebrauch besteht; sonst aktive Hilfswege entfernen, historische Reports erhalten.

### 5.2 `JenesisBridge`

Heute hängen `runtimeClasspath`, `snapshotTests` und Verbraucher an Ergebnisnamen wie `build/maven/build-module-…`. B normalisiert das **einmal innerhalb dieser Klasse**, nicht in jedem Verbraucher erneut.

Vorgeschlagene Verträge (Namen im vorhandenen Stil, Rückgabetypen entsprechend verfeinern):

```java
BuildOutcome run(BuildRequest request, Map<String,String> overrides);
BuildOutcome readExistingOutcome(Path root);
ModuleResult moduleResult(BuildOutcome outcome, String projectId);
List<Path> runtimeClasspath(String projectId, BuildOutcome outcome);
List<Path> runtimeModulePath(String projectId, BuildOutcome outcome);
List<Path> testClasspath(String projectId, BuildOutcome outcome);
Properties snapshotFreshTests(Path root, String projectId,
                              BuildOutcome outcome, Path destination);
```

`ModuleResult` enthält Projekt-ID, expliziten Modulnamen (bis B5 zunächst optional), tatsächlichen Engine-Resultlocator, eigene JAR, Sources, POM, geordnete Runtimeclosure und zugehörige Testreports. Der Locator wird aus realen Engineinventaren ermittelt; nicht aus zufälligen Dateinamen/Listenpositionen. Doppelte oder fehlende Ergebnisse scheitern. Ein Verzeichnis auf dem Classpath wird nicht als Nachweis einer modularen JAR ausgegeben.

Ein internes `EngineResultIndex` als kleine verschachtelte Klasse ist ausreichend; kein eigenes generisches Repository-/Resolverframework. Modulmodus ist eine konkrete getestete Eigenschaft, nicht aus einem Profilnamen abgeleitet. Bestehende `runtimeClasspath`-Signaturen nach Möglichkeit erhalten, damit künftige externe Verbraucher, einschliesslich pausierter APE-Arbeit, nicht unnötig brechen.

### 5.3 `ModuleArtifacts`

`runtime(Path,String,BuildOutcome)` liest die normalisierten Ergebnisse, prüft eigene Produzentenidentität, vollständige Closure, Dateihashes und verbotene Runtimeleaks. Die bisher hardcodierte Projektversion wird aus einer gemeinsamen autoritativen Version bezogen, nicht bloss abgeschafft. `Artifact` erhält wo nötig Modulname und Descriptorstatus, behält aber GAV und SHA getrennt.

Methoden für eigene JAR, POM und Sources ausdrücklich ergänzen, statt in Exportern erneut Enginepfade zu parsen. `assertProductClosure(...)` verwirft Verification, JUnit, Generatoren, Archivtool, Testpatches und Buildmodule in Produktclosures. Keine Testdependency darf durch eine Konfliktauflösung die Produktruntimeversion verändern.

### 5.4 `JvmBuildInputs`

`fingerprint(Path,String)` erfasst neben bestehenden Quellen/Fixtures alle wirksamen Deskriptoren, Pin-/Versionsmetadaten, Testzugriffspläne und Schema-/Serviceinputs. Keine globale Ordnerhashung, die jede Dokumentationsänderung jeden Benchmark invalidiert. Gemeinsame Änderungen werden über tatsächliche Abhängigkeiten weitergereicht. Private absolute Pfade, zufällige Runnamen und alte Reports sind keine Produktinputs.

`requireProject(...)`, sichere Pfadprüfung und Quellenpinprüfung bleiben. Konfigurationsänderungen müssen den echten Jenesis-Schritt invalidieren, nicht nur den anschliessenden Bericht. Gradle-Referenzfingerprints für historische Nachweise separat lesen; neue Tests laufen nicht heimlich auf alten Outputs.

### 5.5 `VerificationLayout` und `CompilerContractRun`

Die Default-Gradlepfade werden entfernt, sobald B-I umgestellt ist. `forInvocation(Path)` benötigt eine ausdrücklich identifizierte aktuelle Layoutdatei oder erzeugt sie aus einem verifizierten aktuellen Buildresultat; keine automatische Rückkehr zu `*/build/test-results`.

`read(Path,Path)` prüft Schema, Producer, Root-/Pfadgrenzen, Run-/Snapshotbindung und vollständige Pflichtfelder des gewählten Profils. `junitDirectory`, `compilerDistribution`, `lspDistribution`, `sdkArchive`, `normativePdf` und `rootReport` bleiben die zentralen Zugangspunkte. Separate Wasm-/Browserausgaben werden ausdrücklich referenziert, nicht in JVM-Verzeichnisse kopiert, um alte Pfade vorzutäuschen.

`CompilerContractRun.run(Path)` behält Produzentenauswahl → tatsächlichen frischen Testlauf → Reportaufnahme → Consumerprüfung. Eine Umbenennung von Testklassen führt zu einer dokumentierten Bindingmigration in `current-bindings.json`, nie zur nachträglichen Änderung historischer G2-Dateien. In B ist eine Testpackage-Umbenennung nicht das Standardmittel.

### 5.6 Begrenzte neue Komponenten

- `HostVerificationProfiles`: feste Whitelist bestehender Profile, deren Voraussetzungen und Runmethoden. Kein dynamisches Classname-Argument vom Benutzer.
- `ConsumerSnapshot`: erstellt/liest/verifiziert die immutable Übergabe für Wasm und Editor; Methoden in ARTIFACT_HANDOFF.
- `WasmBuildRun`: kontrollierter Gradleaufruf mit genau einem Manifest, Backendprofil und Belegpfad. Kein JVM-Build im Gradlekind.
- `EditorBundleRun`: ruft vorhandene Nodeproduzenten und einen reinen Bundleassembler mit expliziter LSP-Closure auf.
- `ModulePathContract`: JAR-Deskriptor-, Graph- und Boundaryprüfungen aus JPMS_AND_TESTING.
- `JpmsTestRun`: nur soweit für White-Box-Tests erforderlich, ein kleiner Jenesis-Hook über JDK-Tools/JUnit, ohne Produktneukompilierung.

Diese Klassen dürfen kleiner zusammengefasst werden, wenn ihre Verträge klar bleiben. Keine Attrappen, abstrakten Pluginregistries oder leeren Zukunftsmodule anlegen. Ziel ist weniger parallele Mechanik, nicht eine zweite Buildengine.

## 6. Buildlayout und deklarative Wahrheit

Ziel ist ein **tatsächlich JPMS-kompilierter** Produktgraph. `module-info.java` beschreibt Lesbarkeit, Export, Öffnung und Services. GAV/Version/Pins und Tool-/Testabhängigkeiten behalten eine einzige gepflegte Zuordnung.

B0 prüft mit dem Pin zwei kleine Fälle: vorhandene POM-Discovery plus explizites Produktdescriptor und Native-Modullayout mit Mavenkoordinaten. Wähle den kleinsten unterstützten Weg. POM-Layout darf im Endzustand bleiben, **wenn** die tatsächlichen javac-/java-Aufrufe JPMS verwenden und keine doppelte unabhängige Dependencywahrheit entsteht. Andernfalls auf das getestete modulare Jenesis-Layout umstellen und POMs ausschliesslich als korrekte erzeugte Consumer-Metadaten führen.

Keine ungeprüften `@jenesis`-Tags erfinden. Die Entscheidung wird vor Produktmigration in DECISIONS.md mit minimalem Probeprojekt und echten Args/Reports belegt. Keine dauerhafte Doppelspur „pom product“ und „modular product“ derselben Klassen. Produktpackagegrenzen und GAVs bleiben identisch. Ein Resolverlayoutwechsel ist nicht automatisch ein Abhängigkeitsupgrade.

## 7. Abnahmeumfang und echte Umgebungslücken

Jede Phase hat in EXECUTION_PLAN ein Mindestgate. Fehlende notwendige Codegen-/Verbraucherläufe dürfen nicht durch eine Syntaxprüfung oder ein erfundenes PASS ersetzt werden. Fehlt etwa die erforderliche Web-Image-Toolchain, bleibt dessen Integrationsgate offen; bereits unabhängige nicht destruktive Vorarbeit ist erlaubt, der abhängige Vollabschluss aber nicht.

Der Auftrag ist **keine Veröffentlichung des vollständigen P3-Produkts**. Die vollständige historische Performance-/Browser-/Plattformmatrix bleibt im neuen Releaseprofil verpflichtend, muss aber für eine Build-/Modularisierungskorrektur nicht beliebig oft neu laufen. B benötigt echte schmale Backend- und Hauptverbraucherprüfungen. Plattformabhängige Releaseversprechen werden nur für tatsächlich geprüfte Kandidaten bestätigt. Fehlen entfernte CI-Runs, lautet deren Status NOT_RUN_ENV; Konfiguration und fail-closed Gegenproben können trotzdem unabhängig beurteilt werden, sind aber kein Plattform-PASS.

Gegenproben und Originalläufe sind getrennt. Eine Fixture, die ein fehlendes Artefakt korrekt zurückweist, belegt den Validator; sie belegt nicht die erfolgreiche echte Compilerintegration. Notwendige Laufzeitannahmen und Umfang der Schlussfolgerung werden pro Kriterium dokumentiert.

## 8. Abnahmekriterien

Jedes Kriterium erhält in CRITERIA_EVIDENCE mindestens Zustand, Codefundstelle, tatsächliche Belegreferenz und offene Grenze. Kein automatisch erzeugtes PASS allein aus dem Vorhandensein einer Datei.


| ID | Phase | Vertrag | Erfüllung |
|---|---|---|---|
| JB-01 | B0/B7 | Scope und APE-Pause | Kein APE-/Cosmo-Build, Patch, Merge, Publishing oder fachfremder Produktumbau; vorhandene Arbeit bleibt erhalten. |
| JB-02 | B0 | Belastbare Baseline | Zwei Repositorystände, Toolpins, Testidentitäten, Tasks und tatsächliche A-Nachweise sind erfasst; keine neue behauptete Erfolgshistorie. |
| JB-03 | B0/B5 | Engine-Fähigkeit | Mit dem tatsächlichen unveränderten Pin sind JPMS-Produktion, Reportbindung und gewählte White-Box-Testintegration praktisch erprobt. |
| JB-04 | B1/B4 | Ein JVM-Produzent | Alle zehn JVM-Projekte werden ausschliesslich durch Jenesis produziert; kein Gradle/Maven/fremdes javac für Produktquellen. |
| JB-05 | B1 | Stabile Artefakt-API | Normalisierte Ergebnisobjekte ersetzen POM-spezifische Pfadannahmen in Verbrauchern; fehlende/mehrdeutige Outputs scheitern. |
| JB-06 | B1/B7 | Inkrementalität und Clean | Relevante Quellen, Konfigurationen und Fixtures invalidieren richtige Schritte; Clean löscht nur zugewiesene Outputs. |
| JB-07 | B1/B4 | Verifikation vollständig | Hostprofile, Compiler-Contracts, Norm-/Conformancevorbereitung und erforderliche Selektoren bleiben erreichbar. |
| JB-08 | B1/B7 | Authentische Testberichte | Parameterfälle, Skips, Fehler, Artefakt-/Input- und Runbindung bleiben erhalten; kein Erfolg aus Sollzahlen. |
| JB-09 | B2 | Geschlossener Wasm-Snapshot | Jenesis-JARs und POMs werden als vollständige unveränderliche Manifest-/Maven-Übergabe exportiert. |
| JB-10 | B2/B4 | Gradle-Grenze | Nur die zwei Backendprojekte und ihre eigenen Adapter werden durch Gradle gebaut; keine gemeinsame JVM-Quellkompilierung. |
| JB-11 | B2 | Consumerintegrität | Falsche, fehlende, zusätzliche, stale oder neu gehashte Fremdartefakte werden vor Codegenerierung abgelehnt. |
| JB-12 | B2/B7 | Backendintegration | TeaVM JS/WasmGC und Web Image verwenden nachweislich die neuen JARs; Moduldeskriptoren werden nicht still entfernt. |
| JB-13 | B2/B4 | Keine Buildzyklen | Gradle ruft nicht Jenesis zurück; kein Reentry desselben Buildlocks; Phasenordnung ersetzt Root-Task-Backedges. |
| JB-14 | B3 | Editorbundle-Produktion | Normaler Bundleproduzent verwendet Jenesis-LSP-Artefakte und den gepinnten vorhandenen Node-Renderer. |
| JB-15 | B3 | Extension-Staging | Echter stage:core/F5-/CI-Verbraucher baut oder importiert das neue Bundle, ohne Gradle-Fallback. |
| JB-16 | B3/B6 | Extension-Sicherheit | Vollständige JAR-/Renderer-/Runtimeinventare, sichere Pfade und vor/nach-Integritätsprüfung bleiben wirksam. |
| JB-17 | B4/B7 | Altlastenbereinigung | Aktive Java-Gradle-Tasks, Parallelkonfigurationen und irreführende Anleitungen sind entfernt; Geschichte bleibt unverändert. |
| JB-18 | B5 | Eigene JPMS-Module | Zehn eigene JVM-Projekte haben echte Deskriptoren und werden auf dem Modulpfad kompiliert, nicht nur dekorativ paketiert. |
| JB-19 | B5 | Modulgraph | Keine Produktions-Split-Packages/Zyklen; requires/transitive/static, uses/provides und Exporte entsprechen tatsächlichen Verträgen. |
| JB-20 | B5 | Keine Sichtbarkeitstricks | Keine public-Internas, pauschalen open modules oder universellen Produktionszugriffsschalter als Testworkaround. |
| JB-21 | B5 | White-Box-Erhalt | Vorhandene Assertions und Invocations bleiben erhalten; Testpatching ist eng, kollisionsfrei und nur testseitig. |
| JB-22 | B5/B6 | Echte Modultests | Benannte Consumer und Nicht-Export-/Opens-Negativproben prüfen die echte ungepatchte Produktgrenze. |
| JB-23 | B5 | Dependency-/Metadatenparität | GAVs, Versionen, Scopes, Ressourcen, Services und Sources bleiben korrekt; Fremdmodule werden tatsächlich inventarisiert. |
| JB-24 | B5/B7 | Classpath-Kompatibilität | Dieselben modularen JARs bleiben für vereinbarte JVM-/SDK-/Wasm-Classpathverbraucher nutzbar; kein Doppelprodukt. |
| JB-25 | B6 | CLI-Modulstart | Regulärer CLI-Launcher startet das explizite Modul; Unicode, stdin/stdout, JSON und Exitcodes bleiben korrekt. |
| JB-26 | B6 | LSP-Modulstart | LSP startet als benanntes Modul, inklusive Extension; JSON-RPC bleibt frei von fremdem stdout. |
| JB-27 | B6/B7 | Runtimeprüfung | Vorhandene Extension-JRE/jlink-Spur bleibt; kleine echte Runtime-/App-Image-Prüfung und ihre Grenzen sind dokumentiert. |
| JB-28 | B7 | CI-Aufteilung | JVM-, Consumer- und Releasejobs verwenden die neue Zuständigkeit, genaue Inputbindung und unveränderte erforderliche Gates. |
| JB-29 | B4/B7 | Release-Evidence | Fehlende, fremde, veraltete oder unvollständige Nachweise werden weiterhin abgelehnt; keine P3-Abschwächung. |
| JB-30 | B7 | Reproduzierbarkeit | Vorgesehene JVM-Lieferartefakte sind in zwei sauberen Erzeugungen verglichen; TeaVM-Ausgaben bleiben aus Bytegleichheit ausgenommen. |
| JB-31 | B0–B7 | Phasengates | Alle abhängigen Phasen starten erst nach ihren erfolgreichen notwendigen Gates; echte Blocker bleiben sichtbar. |
| JB-32 | B0–R | Gezielte Retests | Fixauswahl folgt Input-/Verbraucherrisiko; Dokument-/Commitwechsel löst keinen pauschalen Volltest aus. |
| JB-33 | B7 | Kandidatencommits | Geprüfte Core- und Extension-SHAs/Trees und kumulative Diffs sind lokal vorhanden; keine fremden Änderungen im Commit. |
| JB-34 | R | Unabhängiger Agent | Frischer OpenCode-2.x-Review mit direktem DeepSeek Flash 1017, belegter Modellzuordnung und regulärem Abschluss. |
| JB-35 | R | Gesamt-Review | Reviewer beurteilt JB-01–JB-40, beide Repositories, Originalbelege und eigene Gegenproben; repariert nicht selbst. |
| JB-36 | R | Korrekturschleife | Berechtigte Findings werden korrigiert, gezielt getestet, neu committet und frisch vollständig reviewt. |
| JB-37 | B0–R | Rechte und Secrets | Keine Tokens in Logs, fremde Änderungen oder globale Installationen; Review- und Testarbeit ist isoliert. |
| JB-38 | B7/R | Ergebnisgrenzen | Lokale Implementierungsabnahme, tatsächliche Backend-/Plattformabnahme und P3-Freigabe werden getrennt ausgewiesen. |
| JB-39 | B7 | Wartbarer Endzustand | Ein dokumentierter Default, kein permanenter alter JVM-Builder; keine unbenutzten Strategien/Adapter oder historische Evidenzumschreibung. |
| JB-40 | B7/R | Nachvollziehbarer Abschluss | B-I/B-II-Status, Belege, Reviewergebnis, offene Releasepflichten und Arbeitskopiezustände sind vollständig dokumentiert. |

## 9. Abgelöste Regeln aus Auftrag A

Gezielt ersetzt werden: Gradle als Gesamt-/Referenzbuild im Normalbetrieb; dauerhafte zehn Java-Gradle-Projekte; rein POM-/Classpath-basierte Produktion; unverändertes Extension-Staging; der Ausschluss einer Wasm-Artefaktentkopplung. Nicht ersetzt werden: strikte Pins, Quellen-/Artefaktbindung, Testvollständigkeit, normale Compiler-/Formattersemantik, historische Rohbelege und das Verbot ungefragter Veröffentlichungen.

Aktuelle Dokumente müssen das erklären. Alte Auftrags-/Fehlerberichte behalten ihre damalige Aussage und erhalten höchstens einen klaren Verweis auf den Nachfolgeauftrag. Kein historisches A-PASS auf neue modulare Bytes umetikettieren. Keine Wiederaufnahme des TeaVM-Determinismuspatches oder des pausierten CosmoGVM-Patches.
