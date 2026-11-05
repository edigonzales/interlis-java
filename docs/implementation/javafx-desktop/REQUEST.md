# Spezifikation: INTERLIS Compiler – JavaFX Desktop Prototype

**Status:** Proof of Concept
**Repository:** https://github.com/edigonzales/interlis-java
**Zielplattform:** macOS ARM64
**Technologien:** Java 25, JavaFX, Jenesis, JPMS

## 1. Ziel

Implementierung einer kleinen, eigenständigen JavaFX-Desktopanwendung, um die technische Machbarkeit einer zukünftigen grafischen Oberfläche für den INTERLIS-Compiler nachzuweisen.

Der Prototyp muss einen echten INTERLIS-Compileraufruf ausführen, die Ergebnisse anzeigen und ein interaktives Klassendiagramm darstellen.

Die Anwendung wird über Jenesis mit `jlink` und `jpackage` als eigenständig ausführbare macOS-App ausgeliefert.

Es handelt sich ausdrücklich nicht um die Implementierung der endgültigen Benutzeroberfläche.

## 2. Architektur

Neues Modul:

`interlis-compiler-gui`

JPMS-Modulname:

`guru.interlis.compiler.gui`

Abhängigkeiten:

- `javafx.controls`
- `javafx.web`
- `guru.interlis.compiler.cli` beziehungsweise die bestehenden Compiler-SDK-APIs
- `guru.interlis.projection`
- weitere bereits vorhandene Compiler-Module nur nach tatsächlichem Bedarf

Das Modul verwendet die bestehenden Compiler- und Diagramm-APIs. Keine Duplizierung von Compilerlogik.

Keine Abhängigkeit zu LSP, VS-Code-Extension oder Wasm-Adaptern.

Keine neuen Frameworks ausser JavaFX. Insbesondere kein Swing, kein Spring Boot und kein zusätzlicher Dependency-Injection-Container.

## 3. Minimale Benutzeroberfläche

Ein JavaFX-Hauptfenster mit folgenden Bereichen:

| Bereich | JavaFX-Komponente | Funktion |
|---|---|---|
| Model files | ListView | Geladene `.ili`-Dateien |
| Dependencies | TreeView | Tatsächlich aufgelöste Modellabhängigkeiten, soweit über die bestehende API verfügbar |
| Problems | TableView | Compilerdiagnosen mit Schweregrad, Position und Meldung |
| Diagram | WebView | Bestehender interaktiver Sprotty-Renderer |
| Toolbar | Buttons | Open, Compile, Example |
| Status | Label | Compilerstatus und Fehler |

Layout über `BorderPane` und `SplitPane`.

Keine aufwendige Gestaltung. Ein einfaches, modernes JavaFX-Theme genügt.

### Verhalten

- Über **Open** lassen sich eine oder mehrere `.ili`-Dateien auswählen.
- **Example** lädt mitgelieferte Testmodelle.
- **Compile** führt eine tatsächliche Kompilierung aus.
- Die Diagnosen werden angezeigt.
- Bei verfügbarer Diagrammprojektion erscheint das Klassendiagramm.
- Fehler werden sichtbar und nachvollziehbar dargestellt.
- Die Oberfläche darf während der Kompilierung nicht blockieren.

Keine Modellbearbeitung und kein vollständiger Repositorybrowser.

## 4. Diagrammrenderer

Verwendung des bestehenden Renderers aus:

`frontend/diagram-ui`

Dessen Implementierung wird nicht dupliziert oder neu geschrieben.

Einbettung in JavaFX `WebView` über ein lokal paketiertes HTML-/JavaScript-Bundle.

Die Diagrammdaten stammen aus der vorhandenen Java-Projektion (`DiagramDocument` beziehungsweise dem bestehenden JSON-Vertrag).

### Zwingend zu prüfen

- Laden des vollständigen JavaScript-Bundles
- Kompatibilität mit den verwendeten JavaScript-Modulen
- ELK.js und dessen Web Worker
- libavoid-js inklusive WebAssembly, sofern der entsprechende Routingmodus verwendet wird
- Darstellung eines echten INTERLIS-Klassendiagramms
- Zoom und Pan
- Auswahl von Diagrammelementen
- SVG-Export und PNG-Export
- Java-JavaScript-Kommunikation

Die benötigten JavaScript-, CSS-, Worker- und gegebenenfalls Wasm-Dateien müssen im ausgelieferten App-Bundle enthalten sein.

Es dürfen zur Laufzeit keine Ressourcen aus einem CDN benötigt werden.

Falls das Laden über `file://` aufgrund von Browser-Sicherheitsbeschränkungen scheitert, darf ein kleiner eingebetteter HTTP-Server auf `127.0.0.1` geprüft werden.

**Wichtig:** Wenn wesentliche Funktionen des bestehenden Renderers in JavaFX WebView nicht funktionieren, wird der Prototyp nicht durch einen neuen Renderer ersetzt. Das Problem wird dokumentiert und die Technologiewahl neu beurteilt.

## 5. Compilerintegration

Ein bestehendes, gültiges INTERLIS-Modell muss erfolgreich kompiliert werden können.

Ein fehlerhaftes Modell muss tatsächliche Compilerdiagnosen liefern.

Verwendung der bestehenden In-Process-Compiler-API, beispielsweise des bereits vorhandenen `DefaultCompilerCli` oder einer geeigneten Compiler-/Workspace-API.

Keine Ausführung von `Main.main()` mit `System.exit()` und kein externer Compilerprozess.

Die Kompilierung läuft auf einem Hintergrundthread. Updates der JavaFX-Komponenten erfolgen auf dem JavaFX Application Thread.

Die Diagrammprojektion verwendet die bestehenden Datenverträge und darf keine eigene INTERLIS-Interpretation enthalten.

## 6. Jenesis und Packaging

Die Integration erfolgt ausschliesslich über die bestehende Jenesis-Buildarchitektur.

Kein Gradle- oder Maven-Build für das neue JVM-Modul.

### Anforderungen

- JavaFX-Abhängigkeiten mit festen Versionen
- Plattformkompatible JavaFX-Artefakte und JMODs
- Vollständiger JPMS-Modulpfad
- `jlink` erzeugt eine lauffähige Runtime mit JavaFX
- `jpackage` erzeugt `INTERLIS Compiler.app`
- Das JavaScript-Diagrammbundle wird mitgeliefert
- Anwendung startet ohne lokal installiertes Java
- Anwendung funktioniert offline

**Erste Zielplattform:** macOS ARM64.

Optional anschliessend macOS x64.

Windows und Linux sind nicht Bestandteil dieses Prototyps, sollen aber durch die Architektur nicht ausgeschlossen werden.

Signierung und Notarisierung sind für den lokalen Machbarkeitsnachweis noch nicht erforderlich.

## 7. Umsetzung in Phasen

### Phase 1 – JavaFX und Diagramm

- Neues JPMS-GUI-Modul anlegen.
- JavaFX-Hauptfenster mit WebView erstellen.
- Bestehenden Diagrammrenderer einbetten.
- Reales Beispiel-DiagramDocument darstellen.
- Interaktions- und Exportfunktionen prüfen.

**Gate:** Der vorhandene Diagrammrenderer funktioniert innerhalb von JavaFX WebView oder die Inkompatibilitäten sind eindeutig dokumentiert.

### Phase 2 – Jenesis-Packaging

- Buildintegration für das GUI-Modul ergänzen.
- JavaFX-JMODs integrieren.
- Runtime über `jlink` erstellen.
- macOS-App über `jpackage` erzeugen.
- App unabhängig vom Entwicklungsprojekt und ohne installiertes Java starten.

**Gate:** Die paketierte Anwendung startet und zeigt den funktionierenden Diagrammrenderer.

### Phase 3 – Compilerintegration

- Modelldateien auswählen.
- Vorhandene Compiler-API einbinden.
- Gültige und fehlerhafte Modelle kompilieren.
- Diagnosen darstellen.
- Diagramm aus dem tatsächlichen Compilerergebnis erzeugen.
- Alles nochmals in der paketierten App prüfen.

**Gate:** Vollständiger Ablauf von der `.ili`-Datei bis zum interaktiven Diagramm funktioniert.

Jede Phase muss erfolgreich abgeschlossen sein, bevor die nächste beginnt.

## 8. Abnahmekriterien

Der Prototyp gilt als erfolgreich, wenn:

- [ ] Eine eigenständige `INTERLIS Compiler.app` für macOS ARM64 existiert.
- [ ] Die Anwendung ohne installiertes Java startet.
- [ ] Ein echtes INTERLIS-Modell kompiliert werden kann.
- [ ] Fachliche Compilerfehler angezeigt werden.
- [ ] Ein interaktives Sprotty-Diagramm angezeigt wird.
- [ ] Zoom, Pan und Selektion funktionieren.
- [ ] SVG- und PNG-Export funktionieren.
- [ ] Die Anwendung offline funktioniert.
- [ ] Keine Änderungen am bestehenden Diagrammrenderer erforderlich sind, die andere Konsumenten beeinträchtigen.
- [ ] Bestehende Compiler-, CLI- und LSP-Builds unverändert funktionieren.

Die Resultate inklusive Plattform, Versionsständen, Packaging-Grösse und festgestellten Einschränkungen werden in einem kurzen Markdown-Bericht festgehalten.

## 9. Ausdrücklich ausserhalb des Umfangs

Keine vollständige IDE, kein Quelltexteditor, kein Syntaxhighlighting, keine Modellbearbeitung, keine Persistenz von Workspaces, keine Benutzerkonfiguration, keine umfassende Repositoryverwaltung und keine Release-Pipeline.

Das Ziel ist eine belastbare Technologieentscheidung, nicht ein bereits vollständiges Produkt.
