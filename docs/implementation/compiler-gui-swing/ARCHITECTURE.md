# Architektur und Implementierungsverträge

## 1. Modulentscheidung

Ein neues beziehungsweise passend umgebautes Modul `interlis-compiler-gui`, JPMS-Name `guru.interlis.compiler.gui`, Paketbasis `guru.interlis.gui`.

Vorgesehener Descriptor, nach dem tatsächlichen Importbedarf minimal halten:

```java
module guru.interlis.compiler.gui {
    requires java.desktop;
    requires com.formdev.flatlaf;
    requires guru.interlis.model;
    requires guru.interlis.compiler;
    requires guru.interlis.workspace;
    requires guru.interlis.repository;
}
```

Keine GUI-API für andere Produkte exportieren. Tests erhalten nur die im bestehenden Build üblichen gezielten Zugriffe. Kein pauschales `open module`, keine produktiven `--add-opens`-/Classpath-Ausweichkonstruktionen.

**Bewusste Entscheidung gegenüber dem früheren PoC-Vorschlag:** Die endgültige GUI hängt nicht von `guru.interlis.compiler.cli` ab. Sie benötigt strukturierte Quellen-, Closure- und Diagnosedaten; deshalb verwendet sie direkt Workspace-/Repository-/Compiler-APIs. Der CLI-Adapter ist eine Verhaltensreferenz und ein Paritätsoracle, nicht der GUI-Backendtransport. Dadurch wird auch die CLI-Abhängigkeit auf Diagrammprojektion nicht unnötig in die GUI-Laufzeit gezogen.

Der vorhandene Hostauflauf wird über dünne Adapter komponiert. Es wird keine zweite Modellnamens-, Versions-, Import-, Cache-, Proxy-, Syntax- oder Warnungssemantik geschrieben. Ein wenig hostseitige Ablaufkoordination ist zulässig; eine Kopie des `DefaultCompilerCli` samt Optionsparser ist es nicht. Falls eine notwendige strukturierte Information öffentlich fehlt, ist eine kleine begründete Erweiterung im zuständigen Modul besser als Reflection, Textparsing oder ein GUI-Sonderresolver. Betroffene Verträge und gezielte Verbrauchertests dann mitanpassen.

Keine Abhängigkeit von LSP, Wasm, Node, Webfrontend, Formatterprodukt oder Dokumentationsgenerator. Keine neue DI-, Eventbus-, Reactive-, Docking- oder GUI-Builder-Runtime. Konstruktorinjektion genügt.

## 2. Schichten

```text
ui/*                Swing-Komponenten, Renderer, Action-/Selection-Bindung
controller/*        Sitzungsaktionen, Hintergrundaufträge, Aktualitätsregeln
application/*       hostseitige Prüf-/Browserservices und immutable Ergebnisse
presentation/*      reine Projektionen: Diagnosen, Baum, Inspector, Tabellen
infrastructure/*    Settings, begrenzte Dateihilfen, Desktopadapter
                       |
                       v
         vorhandene model / compiler / workspace / repository APIs
```

Das sind Pakete innerhalb eines GUI-Moduls, keine fünf neuen Maven-/JPMS-Module. Pure Projektionen bleiben ohne Swing testbar. Views verwenden weder `Files` noch HTTP noch Compileraufrufe. Hintergrundservices kennen keine `JTable` und keinen `JTree`.

## 3. Datenmodell

Die folgenden Namen sind **neu vorgeschlagene GUI-Typen**, nicht die Behauptung, sie existierten bereits. Vorhandene äquivalente Typen wiederverwenden. Keine komplizierte generische Frameworkhierarchie einführen.

### 3.1 Sitzung und Auftrag

`SessionInputs` enthält geordnete explizite lokale Quellen und geordnete explizite Rootauswahlen. `RootChoice` unterscheidet lokale Modellwurzel und genaue Repositorywurzel. Die Repositorywurzel enthält Name, Originalversion, SourceId und Anzeigenprovenienz. Auswahlidentität und Anzeigenlabel sind getrennt.

`SessionOptions` enthält geordnete aktive Repositorykonfiguration, Cache-/Netzwerkpolicy, WarningPolicy und DiagnosticLanguage. Reine Anzeigepräferenzen wie Tab oder Sortierung gehören nicht in den fachlichen Requestfingerprint.

`CompileRequest` enthält RunId, monotonen UI-Generationwert, unveränderliche SessionInputs/SessionOptions und ein beim Start festgehaltenes Arbeitsverzeichnis. Dieses Arbeitsverzeichnis darf später nicht vom Installationsverzeichnis oder einer veränderten Prozess-CWD abhängen.

`SourceSnapshot` enthält SourceId, SourceRevision, unveränderten SourceText, verfügbaren Originalbyte-Digest und klare Herkunft. Nicht selbst eine Compilerrevision erfinden. Werte nach Erstellung unveränderlich.

`CompileResult` enthält mindestens:

- Requestidentität, Status und Zeitangaben;
- alle strukturierten fachlichen Diagnosen und getrennte technische Probleme;
- immutable SourceId→SourceSnapshot-Zuordnung des tatsächlichen Laufs;
- verfügbare vorbereitete Bindungen/Dependencydaten;
- tatsächlich erfasste Repositoryentscheidungen und Provenienz;
- expliziten Vollständigkeitsstatus pro Teilergebnis, nicht bloss ein optionales leeres Array.

GUI-Ergebniszustände bilden die existierenden fachlichen Outcomevarianten ab. Niemals „keine Diagnosen“ als Erfolgsbeweis verwenden. Ein `analyze`-Erfolg ist kein Ersatz für die echte Compilation.

### 3.2 Abhängigkeiten

`DependencySnapshot` repräsentiert unveränderliche Knoten und Kanten plus Roots und Auflösungsvollständigkeit. Schlüssel eines konkreten Modellvorkommens umfasst die vorhandene Modellidentität: Modellname, genaue Quelle/Revision und Deklarationsbezug, nicht nur den Namen. Version nach bestehendem Vertrag zusätzlich erhalten; Builtins haben einen eigenen Identitätstyp.

`DependencyEdge` enthält importingModel, ImportSite, tatsächliche `ImportResolution` beziehungsweise eine eindeutig als unbekannt markierte Resolution, und verfügbare Diagnosebezüge. Missing-/ambiguous-Knoten besitzen stabile GUI-IDs pro Importstelle, keine erfundene aufgelöste SourceId.

`DependencyOccurrence` ist nur ein **sichtbarer Baumpfad** auf einen Graphknoten. Die Graphidentität darf nicht mit der Swing-TreeNode-Identität verwechselt werden. CycleReference und wiederholtes Auftreten sind verschiedene Darstellungsfälle.

### 3.3 Repositorykatalog

`RepositoryEntryId` bindet Repositoryidentität, Indexrevision/-fingerprint und Eintragsvorkommen. Gleichnamige Zeilen dürfen nicht kollabieren. `RepositoryCatalogSnapshot` enthält pro Repository LoadState, Indexeinträge, sichere Problemtexte, Ladezeit und Provenienz.

Browserkatalog und Compilerergebnis sind getrennte Werte. Browserrefresh ersetzt niemals Resolverprovenienz eines bestehenden Compilerlaufs. Auswahl-ID enthält die Kataloggeneration, damit ein verspäteter Previewdownload nicht auf eine neu ausgewählte Zeile angewendet wird.

Die bereits vorhandenen `RepositoryModelMetadata`-Informationen sind der Mindestumfang. Der Browser muss keine bisher verworfenen Metadaten wie Titel oder Kurzbeschreibung durch einen eigenen XMLparser nachladen. Eine spätere Ergänzung bleibt möglich, ist hier nicht nötig.

## 4. Klassen und Methoden

Klassen können sinnvoll zusammengefasst werden; Verantwortungsgrenzen und Testbarkeit sind bindend. Keine leeren Interfaces nur für eine einzige triviale Implementierung. Interfaces an echten Test-/IO-Grenzen sind erwünscht.

| Klasse | Wesentliche Methoden und Verantwortung |
|---|---|
| `GuiMain` | `main(String[])`: Ressourcen/Settings vorbereiten, FlatLaf einrichten, EDT-Start; Fehler vor Fensterstart verständlich behandeln. |
| `GuiApplication` | `start()`, `close()`: Servicecomposition, Executorbesitz, kontrolliertes Herunterfahren; kein globaler Singleton. |
| `MainFrame` | `bind(SessionController)`, `showView(ViewId)`, `render(SessionViewState)`: Menü, Toolbar, Tabs und Status, keine Semantik. |
| `SessionController` | `addFiles(List<Path>)`, `removeInput(InputId)`, `setRoots(...)`, `updateOptions(...)`, `compile()`, `cancel()`, `close()`: einziger schreibender Besitzer des sichtbaren Sitzungszustands auf dem EDT. |
| `CompileService` | `compile(CompileRequest, CancellationToken, Consumer<RunUpdate>)`: synchroner fachlicher Hintergrundauftrag, immutable Resultat, try/finally für alle Ressourcen. |
| `CompilerHostAdapter` | `loadInputs(...)`, `createWorkspace(...)`, `resolveRepositories(...)`, `captureInputs(...)`, `compileWorkspace(...)`: dünne Orchestrierung bestehender APIs; darf in CompileService integriert sein. |
| `DependencySnapshotBuilder` | `build(capture, outcome, sources, provenance)`: reine Abbildung echter Bindungen; keine Resolver-/Parserimplementierung. |
| `DependencyTreeProjection` | `children(OccurrencePath)`, `directImports(ModelKey)`, `usedBy(ModelKey)`, `find(...)`: graphgebundene Views, pfadlokaler Zyklenschutz. |
| `DiagnosticPresenter` | `present(result, filter, language)`, `details(DiagnosticId)`: vorhandene Formatierung/DiagnosticPresentation verwenden; Originaldaten behalten. |
| `SourcePreviewModel` | `from(SourceSnapshot, Optional<SourceRange>)`: Text, Zeilenmodell und Highlight, keine IO und kein Nachladen. |
| `NavigationController` | `showDiagnostic(...)`, `showDependency(...)`, `showSource(...)`: typisierte Auswahlbezüge, keine globale String-Eventbus-Navigation. |
| `RepositoryBrowserController` | `load(repositoryIds)`, `cancelLoad()`, `filter(...)`, `select(entryId)`, `previewSource(entryId)`, `addExactRoot(entryId)`: eigene Generationen, kein Mutieren eines laufenden Compilerrequests. |
| `RepositoryCatalogService` | `load(config, token)`, `preview(entry, policy, token)`: gemeinsamer SourceLoader + RepositoryIndexReader; pro Repository explizites Ergebnis. |
| `RepositoryEntryPresenter` | `rows(catalog, filter)`, `details(entryId)`, `compare(leftId, rightId)`: reine Metadatenprojektion. |
| `GuiSettingsStore` | `load()`, `save(settings)`: schemaVersion, atomarer Austausch, sichere Defaults und recoverable Fehler. |
| `CheckPanel` | Inputs-/Rootbereiche, Problemtabelle und Details über SplitPanes. |
| `ProblemsTableModel` | `setRows(...)`, `diagnosticIdAtModelRow(int)`: typisierte Spalten, keine Datenquelle anhand sichtbarer Rowindizes erraten. |
| `DiagnosticDetailsPanel` | `show(details)`: Cause/Related-Linkaktionen und SourcePreviewPanel, sicherer Text. |
| `SourcePreviewPanel` | `show(model)`: nicht editierbare Textkomponente, Zeilennummern, Rangehighlight und Copy. |
| `DependenciesPanel` | Baum plus Inspector, Direct imports/Used by/Resolution; expand-on-demand. |
| `RepositoriesPanel` | geordnete Repositoryliste, Katalogtabelle, Filter und Inspector/Metadatenvergleich. |
| `SettingsDialog` | Theme, Diagnosesprache, gemeinsame Netzwerk-/Cache-/Warnungsoptionen; Validierung ohne Netz. |
| `RunLogModel` | begrenzter, sanitierter Ringpuffer; unabhängiger Toggle, keine heimliche Komplettpersistenz. |

`TableRowSorter` und `convertRowIndexToModel` bewusst einsetzen. Eine Diagnose oder ein Repositoryeintrag wird bei Auswahl immer über seine stabile ID identifiziert. Bei Modelländerung Auswahl anhand ID restaurieren oder sichtbar löschen, niemals denselben Zeilenindex als „gleiches Objekt“ interpretieren.

Swingkomponenten erhalten stabile `setName(...)`-Namen für Tests und Accessibility. Menüpunkte und Buttons teilen `Action`-Instanzen. Plattformgerechte Modifier über `Toolkit.getMenuShortcutKeyMaskEx()`, keine hartkodierte Ctrl-Annäherung auf macOS. Keine Reflection auf private UI-Felder im normalen Produkt.

## 5. Compilerablauf

1. `SessionController.compile()` prüft auf dem EDT, ob ein Lauf aktiv ist, und friert Inputs/Options/Generation ein. Ungültige oder leere Sitzung führt zu einem Bedienhinweis, nicht zu einem Compilererfolg.
2. Ein dedizierter Worker lädt lokale Quellen über `FileSourceLoader` beziehungsweise vorhandene Hostzugriffe. Normalisierung, Dekodierung und Limits aus diesen APIs übernehmen.
3. Frischen Workspace für diesen Lauf erstellen und Texte mit dessen Revisionsvertrag einspielen. V1 benötigt weder Langzeitanalyse noch einen zwischen Läufen mutierten Workspace.
4. Roots nach SPECIFICATION konstruieren. Repositoryroot-Auswahl mindestens `ModelSelector.ByName(name, Optional.of(version), Optional.of(source))`; tatsächlich unterstützte Bindung in G1/G4 beweisen. `ModelPin` verlangt zusätzlich einen Originalbyte-Digest und wird nicht aus Name/Version/MD5 frei erfunden.
5. Pro Lauf einen `HostRepositories.Access` mit eingefrorenem Cache/Proxy und Beobachtern erzeugen. `closureService().resolve(...)` und vorhandene `RepositoryResolveOptions` verwenden. Prepared-Update unter Revisionsprüfung anwenden. Rejected, Cancelled und Stale nicht als leere erfolgreiche Auflösung behandeln.
6. `Workspace.capture(...)` beziehungsweise gleichwertige vorhandene Resultatdaten am unveränderten Stand abgreifen. Aus `capture.resolution()` und den SourceSet-Bindungen den Dependency-Snapshot ableiten. Anschliessend über `Workspace.compile(...)` real kompilieren. Falls das Ergebnis bereits dieselben Input-/Closure-Daten zugänglich macht, keine unnötige doppelte Capturearbeit. Es darf kein zweiter Compilerlauf bloss für die GUIansicht entstehen.
7. Bei Repositoryablehnung verfügbare tatsächliche lokale/partielle Quellen und Suchschritte sichern. Falls noch keine belastbare Bindung existiert, „not available“ anzeigen; keine ausgewählte Repositoryzeile als aufgelösten Import ausgeben. Fachlicher Ergebniszustand bleibt abgelehnt/unvollständig.
8. Diagnosen, Quellen und Provenienz vor `Workspace.close()` in immutable Resultatwerte überführen. Kompilierungsfehler verhindern die Anzeige vorhandener richtiger Dependency-/Quellinformationen nicht.
9. Der Worker veröffentlicht genau ein terminales Resultat. Übernahme auf dem EDT nur bei passendem Request/Generationwert und nicht verworfenem Lauf. Renderer sehen ausschliesslich fertige Snapshots.

Die Auswahl-/HTTP-/Warningpolicy bleibt in den bestehenden Bibliotheken. Vergleiche in Tests mindestens einen lokalen und einen Repositorylauf mit dem vorhandenen CLI-/SDKverhalten. Eine vollständige Extraktion aller CLIhostklassen ist nicht Teil dieses Auftrags.

## 6. Nebenläufigkeit und Ressourcen

Ein Single-Thread-Executor für Compilation. Ein kleiner separater IO-Executor für Repository-/Previewaufträge, maximal zwei Worker. Keine unbegrenzte Warteschlange bei schnellem Tab-/Selectionwechsel; überholte Previewjobs canceln/ersetzen. Lokale Filterung entweder günstig auf dem EDT oder als generationgebundene reine Projektion ausserhalb, nie mit IO.

Sitzungszustand wird nur auf dem EDT mutiert. Services erhalten immutable Parameter. Workspaceinstanzen sind lauflokal und werden nicht gleichzeitig aus mehreren Threads verändert. Ergebnisse werden atomar ausgetauscht, nicht zeilenweise mit mutierenden Fachobjekten nachgereicht.

Cancellation hat zwei Teile: vorhandenen `CancellationToken` signalisieren und gegebenenfalls Future/IO-Abbruch unterstützen. `Future.cancel()` allein ersetzt keine Kooperation. Bei Cancel sofort den Lauf visuell als cancelling markieren und neue Resultatübernahme sperren; „Cancelled“ erst nach tatsächlicher Beendigung/entsprechendem terminalen Resultat. Die Anzeige bleibt reagibel. Kein `Thread.stop` und kein `System.exit` aus Servicecode.

Schliessen: neue Jobs ablehnen, Tokens setzen, IO und Executor kontrolliert beenden. Nicht auf dem EDT auf einen unbegrenzten Shutdown warten. Der reale Prozess muss nach dem Schliessen enden; ein versteckter HTTP-/Workerthread ist ein Fehler. Die vorhandenen Netzwerkzeitlimits respektieren; keine globale Änderung von ProxySelector oder Systemeinstellungen.

Quellvorschauen auf die bestehenden Ladelimits begrenzen. Für grosse Texte darf ein gekennzeichnetes Fenster um die Fundstelle angezeigt werden, solange absolute Offsets/Zeilennummern richtig bleiben. Such-/Diagnosedaten vollständig erhalten; sichtbare Limits zählen und erklären. Bäume lazy expandieren, keine vollständige exponentielle Baumkopie. Maximale Darstellungstiefe und Knotenzahl mit sichtbarem „More/limit reached“, nicht stilles Abschneiden. V1-Ziel: 10 000 Katalogeinträge und 1 000 Dependencyknoten sollen in kontrollierten Tests ohne EDT-Hänger navigierbar sein; das ist kein Anspruch auf umfangreiche Benchmarks.

## 7. Browserservice und Sicherheit

`HostRepositories.createObserved(...)` liefert den vorhandenen Loader. Indexpayload aus den geladenen Originaldaten bilden und `RepositoryIndexReader.read(...)` verwenden. Keine zweite JAXB-/DOMinterpretation im GUIpaket. Dessen Validierung relativer Quellen und XML-Sicherheitsregeln bleiben wirksam.

Eine Browserenumeration darf nicht durch die automatische Resolverkandidatensuche ersetzt werden, falls diese bereits browseOnly/Versionen wegfiltert. Für Inspektion den vollständigen Index lesen, für Kompilierung weiterhin den Resolver verwenden. Dieser Unterschied ist Absicht.

Die GUI akzeptiert als gespeicherte Repositorybasen nur lokale Verzeichnisse oder HTTP(S)-Basen ohne userinfo, Query und Fragment. Damit wird in diesem authlosen Umfang keine möglicherweise geheime URL als Benutzerpräferenz persistiert. Genaue Quell-URIs aus validierten Indexdaten bleiben intern erhalten und werden nur sicher redigiert angezeigt.

Keine pauschale Vorabprüfung aller Modellquellen eines Indexes. Quelltext nur auf Preview oder Kompilierung laden. Die Browserliste zeigt Metadatenstatus, nicht ungeprüft „source valid“. Sourcepreview und Compilerlauf müssen dieselbe Offline-/Cache-/Proxyentscheidung respektieren, aber jeweils eigene unveränderliche Aufträge haben.

Untrusted Modell-/Repository-/Diagnosetexte in Swing grundsätzlich als Plain Text behandeln. JLabels/JTable-Renderer aktivieren teilweise HTML bei entsprechendem Prefix: HTML sicher ausschalten oder escapen. Public URIs über vorhandene Sanitizer zeigen; genaue Identitäten intern nicht durch redigierte URIs ersetzen. Dateipfade, Quellinhalte und Secrets gehören nicht ungefragt in Fehlerreports. Keine externe Resource wird automatisch geöffnet.

## 8. Einstellungen

V1 verwendet eine kleine versionierte Propertiesdatei unter einem dokumentierten Benutzerverzeichnis, z. B. `~/.interlis/gui/settings.properties`. Testinstanzen erhalten ein isoliertes Settingsverzeichnis per injizierter Konfiguration. Kein hart verdrahteter Schreibzugriff aufs reale Home in Tests.

Repositoryreihenfolge als explizite nummerierte Liste, nicht anhand zufälliger Mapiteration. Datenformat bei Bedarf mit bestehenden Bibliotheken implementieren; keine zusätzliche JSONabhängigkeit nur für Settings.

Save: validieren, temporäre Datei im gleichen Verzeichnis schreiben, atomar ersetzen soweit unterstützt. Bei Fehler letzte gültige Datei erhalten und warnen. Bei beschädigter Datei sichere Defaults und eine gesicherte Originalkopie; nicht automatisch alle alten Werte vernichten. Bounds gegen aktuelle Bildschirmflächen begrenzen, damit ein Fenster nach Monitorwechsel erreichbar bleibt.

Keine automatischen Compile-/Netzoperationen beim Start. Der aktive Repositorytab kann nach ausdrücklichem Aufruf laden; gespeicherte Konfiguration ist kein Auftrag, alle Quellen im Hintergrund herunterzuladen.

## 9. Jenesis, JPMS und Distribution

JDK 25 ohne Preview und vorhandener Jenesis-Builder. POMs bleiben Metadaten, kein Mavenlauf. `build/jenesis/` nicht als generiertes Verzeichnis behandeln. Keine pauschalen Änderungen an Wasm-/Frontendbuilds.

G0 muss insbesondere `pom.xml`, Modulregistrierung in `build/interlis/JvmBuildInputs.java`, Inventar-/Pinning-/Modulprüfungen und `build/InterlisJvmBuild.java` prüfen. Nur tatsächlich notwendige Stellen erweitern. Keine globalen Listen auf `ALL-MODULE-PATH` vereinfachen. CLI-/LSP-Distributionen behalten ihre eigene Closure und ziehen die GUI nicht transitiv mit.

Vorgeschlagene **neu zu implementierende** Projekteinstiege:

```sh
java build/InterlisJvmBuild.java build --module interlis-compiler-gui
java build/InterlisJvmBuild.java gui-run
java build/InterlisJvmBuild.java gui-runtime
java build/InterlisJvmBuild.java gui-check --profile diagnostics
java build/InterlisJvmBuild.java gui-package
```

Diese Befehle sind auf der Quellenbaseline noch nicht nachgewiesen. G0 implementiert und dokumentiert die tatsächlich verfügbaren Varianten. `gui-check` bekommt wenige explizite Profile aus dem Testplan, keine beliebig komplexe generische Testsprache. Headful Tests gehören nicht in den normalen Defaultbuild. Neue Profile benutzen dieselben Jenesisartefakte; kein paralleles `javac`-Produktbuildskript.

Die FlatLaf-Version wird in G0 anhand eines verfügbaren offiziellen Artefakts fest gepinnt; keine `latest`-/Versionsbereichsabhängigkeit. Moduldescriptor auch bei Multi-Release-JARs wirklich prüfen. FlatLaf besitzt Native Libraries; deren Ressourcen/Nativeladepfad im jlink-/App-Kontext real prüfen, nicht mit „Swing ist pure Java“ wegargumentieren. Keine ungeprüfte Entfernung dieser Ressourcen zur Grössenoptimierung.

Bereits G0 erzeugt ein echtes jlink-Image aus der **GUI-Compiler-Runtimeclosure**. JPMS-startbar bedeutet nicht automatisch jlink-fähig. Enthält eine transitive Abhängigkeit nur einen automatischen Modulnamen, den bestehenden kontrollierten Jenesis-Modularisierungspfad prüfen; keinen stillen Classpathfallback einbauen. Jeder Eingriff benötigt Pin, Herkunft, passenden Descriptor und gezielten Runtimebeleg.

G6 erstellt mit `jpackage --type app-image` eine macOS-ARM64-App namens `INTERLIS Compiler.app`. Ergebnis z. B. unter `target/jenesis-jvm/delivery/gui/macos-arm64/`, ohne bestehende Deliveryverzeichnisse anderer Produkte pauschal zu löschen. Signierung/Notarisierung und öffentliche Releasepipeline sind nicht im Scope; der Bericht benennt die daraus verbleibende Gatekeepergrenze. Nicht mit „läuft auf sauberem Download-Mac“ werben, solange dies nicht getestet ist.

Native Packaging auf der Zielplattform. Windows/Linux sollen durch plattformneutrale Pfad-/Tastatur-/UIlogik möglich bleiben, werden ohne realen Nachweis nicht als ausgelieferte Plattformen behauptet. Kein Universal-macOS-Binary vortäuschen. Kein Native-Image-Auftrag.

Abnahme unabhängig vom Checkout: App an einen Pfad mit Leerzeichen kopieren, aus anderem Arbeitsverzeichnis starten, externes `JAVA_HOME`/Classpath entfernen und tatsächliches `java.home` aus dem App-Prozess prüfen. Die eingebettete Runtime muss verwendet werden. Ein Umgebungsvariablentest allein ist kein Beweis, wenn weiterhin ein externes Java gestartet wird. Sauberes VM-/Benutzerprofil ohne installiertes Java ist zusätzlicher geeigneter Nachweis, falls verfügbar; kein Java des Nutzers deinstallieren.
