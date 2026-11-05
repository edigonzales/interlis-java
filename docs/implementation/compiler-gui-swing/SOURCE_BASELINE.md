# Quellenbaseline und verifizierte Anschlussstellen

## 1. Geprüfter Stand

Repository: `edigonzales/interlis-java`.

Am 5. November 2026 gelesener `main`-Commit:

```text
f43a7a2baf6203a2fe82493088d91613d2329f55
```

Commitnachricht: `add new spec`; parent `c02c9112f93cd69c113faf52d6267f601433e0fd`.

Die Spezifikation wurde anhand der unten genannten Repositorydateien und offizieller Dokumentation erstellt. Es wurde keine GUI implementiert, kein Produktbuild ausgeführt und kein externer Review gestartet. Ein lokaler JavaFX-PoC, uncommittete Änderungen oder neuere Branches des Nutzers sind durch diese Baseline nicht inventarisiert. G0 muss diese reale Ausgangslage prüfen.

## 2. Tatsächlich gelesene Repositorydateien

Alle folgenden Quellverweise beziehen sich auf den obigen Commit. Einstieg:

`https://github.com/edigonzales/interlis-java/tree/f43a7a2baf6203a2fe82493088d91613d2329f55`

### R1 — Arbeitsregeln

`AGENTS.md`

Festgelegt sind Java25 ohne Preview, Jenesis0.18.1 als alleiniger JVM-Builder, POMs als Metadaten und Gradle nur für die beiden Wasmbackends. Es gelten unveränderliche Snapshots, strukturierte Diagnosen, keine Hostzugriffe im Compilerkern und risikobasierte Tests. Die Regeln warnen ausdrücklich vor doppelten vollständigen Korpus-/Benchmarkläufen durch Codex und OpenCode. APE bleibt pausiert.

### R2 — Reale CLI-Hostorchestrierung

`interlis-compiler-cli/src/main/java/guru/interlis/cli/DefaultCompilerCli.java`, insbesondere `semantic(...)` und `texts(...)`.

Der Code lädt Quellen, erstellt einen Workspace, bereitet Repositoryclosure über `HostRepositories` vor, wendet ein Prepared-Update an und kompiliert über `Workspace.compile(...)`. Ablehnung, Abbruch und stale Updates werden getrennt behandelt. Quellen stehen im Workspace zur Verfügung; deren Erhalt vor dem Schliessen ist ein geeigneter GUIanschlusspunkt. Der Code ist Referenz für vorhandenes Verhalten, nicht zur vollständigen Kopie vorgesehen.

### R3 — Workspace und Capture

`interlis-workspace/src/main/java/guru/interlis/workspace/Workspace.java`

Nachgewiesene Methoden: `source`, `state`, `currentRevision`, `configure`, `capture`, `analyze`, `compile`, `close`. Kontext-/Cancellationüberladungen existieren. Mutierende Operationen einer Instanz sind nicht nebenläufig gedacht.

`interlis-workspace/src/main/java/guru/interlis/workspace/WorkspaceCapture.java`

Nachgewiesene öffentliche Zugriffe: `workspaceRevision()`, `request()`, `reuse()`, `resolution()`, `fingerprint()`. `resolution()` liefert eine vorbereitete Closure und ist ausdrücklich kein semantisches Gültigkeitszertifikat.

### R4 — Gemeinsame Hostadapter

`interlis-repository/src/main/java/guru/interlis/repository/HostRepositories.java`

`createObserved(...)` baut lauflokalen Zugriff mit Loader, ContentStore, Cachebeobachtung und expliziter Proxykonfiguration. `Access.loader()` und `Access.closureService()` sind vorhanden. Die GUI soll diese Infrastruktur nutzen und keinen eigenen HTTPclient-/Cachevertrag daneben setzen.

### R5 — Indexlesen und Browsermetadaten

`interlis-repository/src/main/java/guru/interlis/repository/RepositoryIndexReader.java`

`read(IndexPayload, RepositoryBase)` liest den bestehenden XMLindexvertrag. Erfasst werden Name, SchemaLanguage, File, Version sowie optional precursorVersion, md5 und browseOnly. XMLsicherheit und Prüfung auf ausbrechende SourceURIs sind bereits Teil des Readers. Ein zusätzliches GUI-XMLmodell ist deshalb nicht erforderlich.

### R6 — Genaue Auswahlen und Pins

`interlis-repository/src/main/java/guru/interlis/repository/RepositoryResolveOptions.java`

Enthält RepositoryConfig, ModelPins, SourcePins und optionalen ResolutionLock. Pins sind exakte immutable Hostpolicy.

`interlis-repository/src/main/java/guru/interlis/repository/ModelPin.java`

Ein ModelPin enthält Modellname, Originalversion, SourceId und ContentDigest. `selector()` erzeugt `ModelSelector.ByName` mit genauer Version und Quellenhinweis. Eine im Browser gewählte Name-/Version-/Quellenidentität darf nicht als bereits digestgesperrter Inhalt bezeichnet werden.

### R7 — Suchentscheidungen

`interlis-repository/src/main/java/guru/interlis/repository/RepositorySearchStep.java`

Nachgewiesen sind geordnete Such-/Auswahlschritte einschliesslich LOOKUP, SELECTED und Problemarten sowie `safeUri(...)`. Die GUI kann reale Schritte anzeigen; sie darf daraus nicht umfassendere, gar nicht erfasste Auswahlbegründungen erfinden.

### R8 — Strukturierte Diagnosen

`interlis-model/src/main/java/guru/interlis/diagnostics/Diagnostic.java`

Enthält unter anderem ID, Code, Severity, Phase, MessageKey/Argumente, optionale PrimaryRange, RelatedLocations, CauseId, DependsOn, SymbolId und FailureId. Keine notwendige Interpretation von Konsolentexten.

`interlis-model/src/main/java/guru/interlis/diagnostics/DiagnosticPresentation.java`

`project(...)` liefert sichtbare und unterdrückte Diagnosen einschliesslich Unterdrückungsgründen. Vorhandene Ursachenbezüge werden verwendet. Dies ist eine geeignete Grundlage für eine verständliche, verlustfreie GUIprojektion.

### R9 — Fachlicher Resolververtrag

`docs/spec/08-modellaufloesung-und-repositories.md`, insbesondere Identitäten, Compiler-DTOs, PreparedClosure, Auswahlregeln und JVM-Hostgrenzen.

Dokumentiert sind unter anderem `ModelSelector`, `ImportBinding`/`ImportResolution`, originale Versionsstrings, quellenrevisionsgebundene Eingaben und die Trennung zwischen Beschaffung und Sprachbindung. Implementierung und aktuellen Vertrag zusammen prüfen; ein Dokumentname allein ist kein Beweis, dass jede dort erwähnte optionale API im aktuellen Checkout bereits verfügbar ist.

### R10 — Bisheriger Reviewstil

`docs/implementation/formatter-so-standard/OPENCODE_REVIEW_PROMPT.md`

Die brauchbaren Mechanismen sind frische OpenCode-Sitzung, direkte DeepSeek-API, tatsächliche Modellzuordnung, Kandidaten-/Treebindung, isolierter Review, Rohbelege, eigene Gegenproben und formale Urteilsprüfung. **Nicht übernommen wird der auf diesen anderen Auftrag begrenzte einmalige Gesamtreview. Für die Swing-GUI ist ein Review nach jeder Phase verpflichtend.**

## 3. Offizielle externe Primärquellen

### E1 — Swing/EDT

Oracle Java25, `javax.swing` package summary:

`https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/javax/swing/package-summary.html`

Swingkomponenten und angeschlossene Models grundsätzlich auf dem EDT bedienen; längere Berechnungen/IO nicht auf diesem Thread. Der Architekturentwurf setzt dies mit getrennten Services und EDTgebundenem Controller um.

### E2 — FlatLaf

FormDev:

`https://www.formdev.com/flatlaf/`

FlatLaf ist ein Swing-Look-and-Feel; offizieller Modulname `com.formdev.flatlaf`, Light/Dark und HiDPIunterstützung sind dokumentiert. Eine konkrete Dependencyversion wird vor Implementierung anhand eines verfügbaren Artefakts gepinnt, nicht frei geraten.

Native Ressourcen:

`https://www.formdev.com/flatlaf/native-libraries/`

FlatLaf liefert native Bibliotheken mit und dokumentiert ihre Einbettung/Auslieferung. Das erfordert im modularen Runtime-/Apppfad eine reale Prüfung und darf nicht als grundsätzlich nativfrei angenommen werden.

### E3 — Runtime und Packaging

Oracle Java25:

`https://docs.oracle.com/en/java/javase/25/docs/specs/man/jlink.html`

`https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html`

jlink erzeugt eine modulare Runtime; jpackage erstellt plattformspezifische Appimages/Pakete und kann eine Runtime mitliefern. Native Pakete werden auf der Zielplattform gebaut. Die Spezifikation beansprucht deshalb zunächst nur einen tatsächlich geprüften macOS-ARM64-Apppfad.

### E4 — OpenCode 2.x

`https://opencode.ai/v2/docs/cli/commands/`

`https://opencode.ai/v2/docs/permissions/`

`https://opencode.ai/v2/docs/models/`

Dokumentiert sind nichtinteraktives `run`, explizite Modellwahl, Dateianhänge, JSONL-Ausgabe, bedingt unterstütztes standalone und automatische Zustimmung ohne Aufhebung expliziter Sperren. V2-Berechtigungen verwenden `permissions`, `shell` und `subagent`; die wirksame lokale Konfiguration muss zur installierten Version passen. Vorhandene Modell-/Providernamen ermitteln statt erfinden.

### E5 — DeepSeek-Modellzuordnung

`https://api-docs.deepseek.com/quick_start/pricing/`

Gelesen am 5. November 2026: API-Modell `deepseek-flash` → `DeepSeek-V4.1-Flash-1017`, direkter Endpunkt `https://api.deepseek.com`. Vor jedem tatsächlich gestarteten Review die für den Auftrag relevante Zuordnung verifizieren; ein zukünftiger Aliaswechsel darf nicht unbemerkt ein anderes Modell unter dem verlangten Namen liefern.

## 4. Noch zu verifizieren, nicht als Tatsache ausgeben

Existenz und Zustand des lokalen GUI-PoCs; tatsächlich installierte OpenCode-2.x-Binary und Providerkonfiguration; gewählte FlatLaf-Artefaktversion; tatsächliche Linkfähigkeit aller GUIabhängigkeiten; aktuelle Jenesis-Registrierungs-/Modularisierungspunkte; macOS-Headful-Berechtigungen; genaue ByName-Quellenbindung im aktuellen Hostpfad; finale Appgrösse und weitere Betriebssysteme.

Die `gui-run`, `gui-runtime`, `gui-check`, `gui-package`-Befehle sowie die vorgeschlagenen GUIklassen sind zu implementierende Produkte dieser Spezifikation, keine bereits verifizierten vorhandenen Befehle. Keine Zukunftsversion, Signierung oder Abnahme aus dem Dokumenttext ableiten.
