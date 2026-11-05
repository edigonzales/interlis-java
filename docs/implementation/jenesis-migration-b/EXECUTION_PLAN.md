# Ausführungsplan — B0 bis B7, anschliessend R

## Grundregeln

Jede Phase beginnt mit kurzer Input-/Scopeprüfung, implementiert einen kleinen vollständigen Abschnitt, führt dessen notwendige Tests aus und verlinkt die echten Belege. Die nächste **abhängige** Implementierungsphase beginnt erst nach bestandenem Mindestgate. Ein technischer Preflight des späteren Reviewzugangs ist erlaubt; es gibt **keinen fachlichen OpenCode-Review nach jeder Phase**.

B-I = B0–B4. B-II = B5–B7. Beide gehören zum Gesamtauftrag. Ein echter Blocker wird festgehalten; unabhängige lesende/inventarisierende Vorbereitung darf fortgesetzt werden, aber kein Gate wird allein dadurch grün. Keine Rückfragen für normale Detailentscheidungen; keine erfundenen Fähigkeiten, neuen Vendorpins oder stillen Scopeausweitungen.

## B0 — Baseline, Taskinventar und kleine Modulprobe

### Implementierungsvorbereitung

- Beide Repositoryzustände und vorhandene A-/Verificationbelege erfassen. Neuer isolierter B-Coreworktree, pausierten APE-Branch unangetastet lassen.
- Zehn JVM-Projekte, Dependencies/Scopes, generierte Klassen, Ressourcen, Packagebesitz, normale und Contracttestidentitäten sowie alle aktiven Gradle-/Node-/CI-Einstiege erfassen.
- Jenesis-Pin und lokale CLI-Hilfe prüfen. Kleine private Probe mit zwei expliziten Produktmodulen, API-/internal-Package und JUnitfällen bauen.
- POM+JPMS bzw. nötiges Modullayout testen; Mavenkoordinaten/Pins beibehalten. Kein Engineupgrade.
- White-Box-Patching über den engen Jenesis-Hook nachweisen: package-private Zugriff, echter Named-Module-Status, Parameterfälle, leere Auswahl, absichtlicher Fehler und echte Reports. Nicht erfolgreiche Tests aus Sollmethodenliste synthetisieren.
- Fremdmodule tatsächlich inventarisieren und endliche Risikoliste für LSP4J/Gson/ANTLR erstellen.
- Vorhandenen OpenCode-/DeepSeek-Zugang technisch prüfen, keine Produktreviewrunde.

### Mindestgate

Die Probe verwendet die tatsächliche Engine und Standard-JDKs. Erfolg und erwartete Negativfälle, Inputs, Args und Reports sind vorhanden. Layout-/Teststrategie ist schriftlich entschieden und operativ machbar. Ist sie es nicht, B-II nicht vorschnell umsetzen. B-I kann nur als ausdrücklich abgegrenzter unabhängiger Teil weiter vorbereitet werden.

**Keine A-Volltests neu ausführen, nur weil ein neuer Auftrag beginnt.** Eine kleine echte Modell-/Compilerprüfung genügt als Umgebungsreferenz, sofern vorhandene A-Belege verwendbar sind. Fehlende erforderliche Baseline nach Bedarf selektiv erheben.

## B1 — Normalisierte Engineergebnisse und führender JVM-Einstieg

### Arbeiten

- JenesisBridge-Ergebnisnormalisierung, ModuleArtifacts und gezielte Inputfingerprints.
- `artifacts` als explizites Erzeugungsprofil und feste Hostprofile; nicht das normale `build` auf Testskip ändern.
- VerificationLayout/CompilerContractRun auf inventargesteuerte Pfade vorbereiten und neue Outputs verwenden.
- Bestehende JVM-Distribution/-SDKprüfung auf genau diese Ergebnisse binden.
- Profil-/Lock-/Cleanregeln und normale Default-Unabhängigkeit von Gradle/Node/Wasm nachweisen.

### Mindestgate

Gezielte Buildadaptertests plus echter kleiner JVMbuild, ein eng selektierter normaler Test und Contractproducer/Consumer. Fehlende Engineausgabe, leere Auswahl, falsches Artefakt und Outputsymlink werden zurückgewiesen. Echte ausgewählte Verification-Hostprüfung gegen neue Dateien. Ein Fixture-/Konfigurationswechsel invalidiert den richtigen tatsächlichen Testschritt; Wiederholung ohne Änderung darf wiederverwenden.

Ein kleiner Lock-/Reentry-Test muss beweisen, dass zusammengesetzte Profile nicht am eigenen Lock hängen. Keine vollumfängliche Korpus-/Browserabnahme.

## B2 — ArtifactSnapshot und Backendentkopplung

### Arbeiten

- ConsumerSnapshot mit verifiziertem Mavenbaum, Closure und Producerbindung.
- Beide Backendbuilds auf diese Übergabe umstellen; keine `project(...)`-Kante auf JVM-Produkte.
- Tatsächlich unbenötigte `generateTestGrammarSource`-Orderingreste entfernen; keine Dummy-Task.
- SDK-/Renderer-Root-Backedges durch explizite Dateninputs ersetzen.
- Backendclasspath, Toolpins und Codegenoutputs im Receipt zusammenführen.

### Mindestgate

Alle schnellen Snapshot-/Cache-/Mutationstests aus ARTIFACT_HANDOFF. Ein echter frischer TeaVM-JavaScript- und WasmGC-Build mit schmalen vorhandenen Laufzeitfällen (gültig, ungültig, Unicode/Formatter bzw. gemeinsame Bridgeanfrage). Ein echter Web-Image-Build mit kleiner vorhandener Nodeprobe. Keine vollständige Performance-/Browsermatrix.

Wenn ein Backendwerkzeug tatsächlich fehlt, dessen Integration **nicht** als bestanden deklarieren. Bereits geprüfter Export-/Verifierteil bleibt verwertbar, notwendiger abhängiger Phasenabschluss aber offen. Konfiguration/Classpathprobe ist kein Ersatz für realen Codegen-PASS.

## B3 — Normaler Editorbuild konsumiert Jenesis

### Arbeiten

- Core-Nodeproduktion und reiner Bundleassembler von Gradlepfaden trennen.
- Extension `stageCore` in Build- und Importphase aufteilen; tatsächliche npm/F5/CI-Einstiege migrieren.
- Bestehende Lock-/Runtime-/Rendererprüfung vollständig erhalten.
- Noch vorhandener Classpath-LSP-Start ist in dieser Phase zulässiger Zwischenstand; Umstellung folgt B6.

### Mindestgate

Echtes Jenesis-Editorbundle, reiner Import ohne gestarteten Corebuild, aktive Stage-/Prepare-Strecke und kleiner echter Extension-Hostlauf. Gegenproben: falscher Corebezug, fehlendes/geändertes/zusätzliches JAR, defekter Renderer, unsicherer Output. Kein persönliches VSIX-Installieren, keine Änderung am bestehenden Entwicklerprofil.

Gezielte Node-/TypeScript-/Extensiontests statt aller UIs/Browser. Renderer nicht neu bauen, wenn dessen Inputs nachweislich identisch sind; vollständige Bundleintegrität dennoch prüfen.

## B4 — JVM-Gradle-Spur entfernen; B-I abnehmen

### Arbeiten

- settings.gradle auf zwei Backendprojekte reduzieren.
- Zehn JVM-Gradle-Buildskripte und migrierte Root-JVM-/Host-/Contract-/Conformance-Tasks entfernen.
- Nicht mehr gebrauchte Konventionen, Versionen, Profile, A-Parallelpfade und aktive Anleitungen bereinigen.
- CI-JVMjobs und lokale Einstiegskommandos auf Jenesis umstellen; Backend-/Releaseprofile explizit trennen.
- Verifikations-/Releasevalidatoren auf tatsächliche neue Reportpfade beziehen; Schutzwirkung nicht ändern.

### Mindestgate

Frischer JVMbuild ohne verfügbare eigene Gradleoutputs und ohne Gradle im benötigten JVMpfad. Moduleliste genau zehn; normaler Build startet keine Wasm-/Node-/APE-Tools. Frischer Backendconsumer mit nur seinen Quellen und Snapshot läuft. Alte eigene Maven-/Gradleoutputs können einen fehlenden Snapshot nicht ersetzen. Clean-Sentinels und aktive Restesuche bestehen.

SDK-/Editor-B-I-Belege aus B1/B3 wiederverwenden, wenn Inputs identisch bleiben; keine Wiederholung allein wegen Dateilöschung einer unbenutzten Gradlekonfiguration. B-I darf als klarer lokaler Zwischencommit festgehalten werden. Noch kein Gesamt-PASS und noch kein unabhängiger Gesamt-Review.

## B5 — Eigene Produktion und Tests modularisieren

### Arbeiten

Nach JPMS_AND_TESTING in drei DAG-Batches:

1. model, compiler;
2. workspace, repository, projection, documentation, wasm-bridge;
3. compiler-cli, lsp, verification.

- Jeweils echte `module-info.java`, wirksame JPMS-Kompilierung, benötigte Ressourcen-/Service- und Testzugriffspläne.
- POM-/GAV-/Pinmetadaten konsistent; keine stillen Versionswechsel oder falschen generated POMs.
- Test-only Patchadapter in den echten Enginegraph aufnehmen; Baselineassertions erhalten.
- Aktuelle Contract-/Reportbindungen anpassen, falls technische Testcontainer sich ändern.
- Classpath-Vertrag derselben modularen JARs prüfen.

### Mindestgate pro Batch

Graph-/Descriptor-/Split-Packageprüfung, echte normale Tests der geänderten Module, ein benannter API-Consumer und gezielte negative Kapselungsprobe. Keine broad opens/exports. Testerfolg auf tatsächlichem benanntem Produktmodul, bekannte Fehltests und Auswahlfehler werden erkannt. Alle ursprünglichen normalen Invocations plus erwartete neue müssen einmal über den letzten relevanten Produktstand abgedeckt sein; unveränderte vorgelagerte Module nicht in jedem Batch erneut vollständig laufen lassen.

Code/Ressourcen/FQCNs und Produkt-SDK-API bis auf gezielt erklärtes Descriptor-/Metadatenplus vergleichen. Kein Bytegleichheitsanspruch zwischen alter nichtmodularer und neuer modularer JAR.

## B6 — Modulare Starts, Extension und Runtimeprüfung

### Arbeiten

- Reguläre CLI-/LSP-Launcher auf `--module-path ... --module ...` umstellen.
- Extension-Lock/Launchvertrag gemeinsam auf modulare Semantik aktualisieren; Classpath-Fehlerfallback entfernen.
- Vorhandene Windows-Unicode-/Argumentbehandlung beibehalten.
- Echte SDK-/CLI-/LSP-Prozesse und isolierte Extension mit gebündelter JVM prüfen.
- Bestehende Extension-jlink-Auswahl mit neuem Servermodul prüfen; kleine Compiler-JDK-Runtime plus app-image-Eignungsprobe ausführen.

### Mindestgate

Modulstart mit passendem Graph, gezielte fehlende Dependency-/falscher Name-Gegenproben, Unicode/JSON/Exitcodes, LSP initialize→diagnostics/navigation→shutdown/restart. Keine JUnit-/Testopens in den Launchplänen. Echter Runtimeimage-Aufruf ohne erreichbares Entwickler-Java. Kontrolliertes HTTP/HTTPS mit normalen HotSpot-JDKs. Defekte bundled Runtime wird weiterhin abgelehnt.

Jpackage darf dokumentiert nicht als neue Primärdistribution gewählt werden; das hebt den erfolgreichen regulären CLI-/LSP-/Runtimevertrag nicht auf. Plattformtests nur für tatsächlich betroffene Startpfade und verfügbare richtige Hosts; fehlende Releaseplattformbelege bleiben separat offen.

## B7 — Gesamtintegration, Releasevertrag, Bereinigung und Kandidaten

### Arbeiten

- Modulare JARs tatsächlich in beide Backendgeneratoren übernehmen. Keine zweite nichtmodulare JAR erzeugen.
- Engste erneute Backend-/Editorproben für die neuen Bytes; Dependency-/Report-/CI-Gesamtplan verifizieren.
- P3-Evidencevalidator, normative Vorbereitung und Performanceprofil weiterhin fail closed. Keine vollständige Releasefreigabe behaupten, wenn die dafür erforderlichen externen Nachweise nicht vorliegen.
- JVM-JARs/Sources/POMs/SDK in zwei unabhängigen sauberen Erzeugungen bytevergleichen, ohne sämtliche Tests zweimal auszuführen. Variable Herkunftsberichte und Runtimeimages nur gemäss ihrem ausdrücklichen Vertrag behandeln.
- Alle aktiven Altlasten, Testpatchleaks, obsolete A-Adapter und widersprüchlichen Anleitungen bereinigen. Herkunft der unveränderten historischen Belege bewahren.
- Aktuelle Zustände/Dispositions- und Kriterienmatrix abschliessen. Finaler Code-/Stage-/Secret-/Whitespacecheck.
- Corecommit, danach zugehöriges Extensionbundle/Lock und Extensioncommit; externes Kandidatenpaar erfassen.

### Mindestgate

JB-01–JB-33 und JB-37–JB-40 haben passende Belege für ihren vertraglichen lokalen Implementierungsumfang. Die gesamte normale JVM-Abdeckung ist auf dem finalen relevanten modularen Stand logisch vollständig, ohne künstliches Duplizieren. Mindestintegration beider Backends und echter Editorverbraucher vorhanden. Keine Pflichtlücke als PASS.

Nicht fällige vollständige P3-/Langzeit-/alle-Browserläufe als NOT_REQUIRED_FOR_B führen, sofern der geänderte Vertrag nicht gerade diesen Lauf benötigt; fehlende fällige Codegen-/Consumerprüfung dagegen BLOCKED. Unterschied ausdrücklich begründen.

## R — einmaliger Gesamt-Review, bei Findings wiederholen

Nach allen erfolgreich abgeschlossenen Phasen und den Kandidatencommits OPENCODE_REVIEW_PROMPT vollständig ausführen. Reviewer liest beide Repositories, gesamten kumulativen Diff, Anforderungen und rohe Belege. Er führt eigene gezielte Gegenproben aus, keine schematische Vollsuiteverdopplung.

Bei Findings: Codex korrigiert, erstellt Impact-/Retestliste, führt die betroffenen Tests aus, committet neue Kandidaten und startet **frischen Gesamt-Review**. Der Reviewer darf frühere unveränderte Rohbelege prüfen und weiterverwenden; Urteil immer an die aktuellen beiden SHAs/Trees binden.

Technischer Reviewfehler, falsches Modell, Timeout, unvollständige Kriterienbewertung oder veränderte Kandidatenquellen sind kein PASS. Bei echten Blockern Abschluss transparent als unvollständig/blockiert, nicht mit einer hypothetischen späteren Ausführung versprechen.
