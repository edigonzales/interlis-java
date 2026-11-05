# Produktspezifikation — INTERLIS Compiler GUI

## 1. Ziel und verbindliche Grenze

Die Anwendung hilft beim Prüfen von INTERLIS-Modellen sowie beim Verstehen ihrer Fehler, Imports und Repositoryherkunft. Sie soll deutlich hilfreicher als eine reine Konsole sein, aber keine zweite IDE werden.

Die drei Hauptfunktionen sind gleichermassen Bestandteil dieses Auftrags:

- **Intelligente Fehlerliste:** strukturierte Diagnosen, Ursachenbezüge, Hinweise und Navigation zu revisionsgebundenem Quelltext.
- **Dependency Explorer:** tatsächliche direkte und transitive Modellabhängigkeiten als Baum und Listen, einschliesslich fehlender oder mehrdeutiger Auflösung.
- **Repository Browser:** konfigurierte Repositories durchsuchen, Modellversionen und Metadaten ansehen und eine konkrete Modellidentität ausdrücklich in die Prüfsitzung übernehmen.

Die Bedienoberfläche verwendet englische Bezeichnungen. Erläuternde Entwicklerdokumentation bleibt Deutsch. Diagnoseprosa ist über die vorhandene DE-/EN-Unterstützung wählbar; Default EN. Keine neue Übersetzungsarchitektur für sämtliche UI-Texte in diesem Auftrag.

**Nicht Bestandteil:** JavaFX, JFXPanel, WebView, Sprotty, ELK, Browserembedding, Graph- oder UML-Zeichenfläche, Diagrammexport, Format Preview, Formatierung, Quelltextbearbeitung, automatische Korrekturen, KI-Funktionen, semantischer Modelldiff, vollständiger Model Inspector, Watch Mode, Projektdateiformat, Workspacepersistenz, Repositorypublikation, Registry, Hintergrundcrawler, Benutzerverwaltung, Pluginplattform, GraalVM Native Image und APE.

Das bestehende Diagramm-/Formatterprodukt ausserhalb der GUI bleibt erhalten. „Ohne Diagramme“ bedeutet nicht, bereits vorhandenen Renderer- oder Projektionscode aus dem Gesamtprojekt zu löschen.

## 2. Grundstruktur: eine Sitzung, drei Arbeitsbereiche

### 2.1 Gemeinsamer Rahmen

Ein `JFrame`, kein Dockingframework und keine Sammlung unabhängiger Toolfenster. Standardgrösse etwa 1280 × 860 logische Pixel, sinnvoll bedienbar ab 1000 × 700. Grössen sind Layoutziele, keine plattformspezifischen Pixeltest-Goldens.

Oben stehen Menü und eine ruhige Aktionsleiste:

```text
INTERLIS Compiler
File    View    Settings    Help

[Add files…] [Compile] [Cancel]            [Offline] [Settings…]
Inputs: 3 files · 1 explicit root · 2 enabled repositories

[ Check ] [ Dependencies ] [ Repositories ]

                  aktiver Arbeitsbereich

Last run: Succeeded · 0 errors · 2 warnings · 0.8 s    [Show log]
```

`Compile` prüft immer die **Sitzung**, nicht die zufällig ausgewählte Browserzeile. `Add files…` führt nach dem Hinzufügen zum Bereich Check. `Cancel` gehört zum laufenden Compilerauftrag. Repositoryladevorgänge haben einen eigenen Abbruch im Repositorybereich.

Die gemeinsame Statuszeile zeigt den letzten Compilerlauf mit Zeitpunkt, Ergebnis und Zählwerten. Nach Änderungen an Eingaben, Roots, Repositoryreihenfolge oder laufrelevanten Optionen erscheint zusätzlich **Inputs changed — compile again**. Ein alter erfolgreicher Lauf wird nicht als Prüfung der neuen Eingaben dargestellt.

Die drei Tabs bleiben während eines Compilerlaufs benutzbar. Eingabeänderungen und Compileroptionen sind während des Laufs gesperrt; Cancel, Navigation, Diagnosefilter und reine Repositoryinspektion bleiben verfügbar. Repositoryeinstellungen dürfen den eingefrorenen laufenden Auftrag niemals verändern. Eine Übernahme aus dem Repositorybrowser ist während des Compilerlaufs deaktiviert.

### 2.2 Check

```text
+--------------------------+----------------------------------------------+
| Inputs                   | Problems                   12 of 18 visible   |
| [+ Add] [- Remove]        | [Severity…] [Search…] [Group related]        |
|                          | Severity | Code | Message | File | Position  |
| local-model.ili           | ...                                          |
| base.ili                  +----------------------------------------------+
|                          | Details                                      |
| Root models              | Full message · cause · related locations     |
| All input models /       | documented help/hints where available        |
| explicit selection       |                                              |
|                          | Source preview (read-only)                    |
| Repository roots         | path / source revision / selected range       |
| Model · version · origin |  line-numbered original source with highlight |
+--------------------------+----------------------------------------------+
```

Links stehen explizit hinzugefügte lokale Dateien und explizite Repository-Roots getrennt. Downloads werden nicht unbemerkt zu „lokal hinzugefügten Dateien“. Eine Datei kann mehrere Modelle enthalten; Dateiname und Modellname sind nicht austauschbar.

**Rootregel:** Ohne explizite Roots werden alle Modelle der ausdrücklich hinzugefügten lokalen Dateien über die vorhandene `BySource`-Semantik geprüft. Sobald mindestens ein Modell explizit als Root gewählt wird, sind nur diese Roots und ihre Closure Ziel der Prüfung; übrige lokale Dateien bleiben verfügbare Eingaben. Der aktive Modus steht sichtbar über der Rootliste. Entfernen aller Roots stellt den Standardmodus wieder her. Ein Repository-only-Lauf ist möglich, sobald mindestens ein konkreter Repository-Root vorhanden ist.

Ungültige Dateiköpfe dürfen durch eine fehlende Modellzeile nicht verschwinden: im Standardmodus bleiben sie Eingaben und liefern echte Diagnosen. Keine impliziten Verzeichnisscans. Drag-and-drop nimmt ausdrücklich ausgewählte `.ili`-Dateien an; Verzeichnisse werden mit einer verständlichen Meldung abgewiesen.

Die lokale Dateiliste zeigt Anzeigename, vollständigen Pfad als Tooltip und lesbaren Ladezustand. Doppelte Quellen werden anhand der vorhandenen Hostnormalisierung behandelt, nicht durch plattformunabhängiges Kleinschreiben aller Pfade. Doppelte Modellnamen in unterschiedlichen Quellen werden nicht entfernt.

### 2.3 Dependencies

```text
+--------------------------------+----------------------------------------+
| [Find model…] [Unresolved only] | Selected model / import                 |
|                                | Name · model version · INTERLIS version |
| Root Model A                   | Actual source · repository · revision   |
|  + Base                        | Resolution status                       |
|  |  + Units                    |                                        |
|  + Shared                      | [Direct imports] [Used by] [Resolution] |
|  + MissingModel [unresolved]   | ...                                    |
|                                | [Show source] [Show related problems]   |
+--------------------------------+----------------------------------------+
```

Der Baum zeigt **den letzten erfassten Lauf**, nicht den Inhalt des Repositoryindexes. Er ist eine Darstellung eines gerichteten Graphen. Ein Modell darf unter mehreren Importpfaden erscheinen. Zyklen werden als Referenz auf einen bereits auf dem aktuellen Pfad vorhandenen Knoten beendet; gemeinsame Unterabhängigkeiten dürfen nicht durch ein globales „visited“ verschwinden.

Rootreihenfolge und Importreihenfolge folgen den bestehenden fachlichen Verträgen. „Used by“ zeigt direkte eingehende Kanten; die Oberfläche nennt dies ausdrücklich. „Direct imports“ zeigt direkte ausgehende Kanten. Transitive Abhängigkeiten sind über den Baum erreichbar, nicht durch eine unbeschriftete Vermischung von direkt und transitiv.

Zustände mindestens: resolved, missing, ambiguous, blocked, builtin, not available. Unvollständige Ergebnisse sind erlaubt und sichtbar als solche gekennzeichnet. Eine erfolgreich beschaffte Quelle kann dennoch Syntax- oder Typfehler haben. „Resolved“ bedeutet nicht „Compiled successfully“.

Der Inspector zeigt Modellname, rohe Modellversion, Sprachversion, logische Quellidentität, lokale/Repository-/Builtin-Herkunft und verfügbare Provenienz. Die Ansicht **Resolution** zeigt die tatsächlich erfassten Auswahl-/Suchschritte dieses Laufs. Keine neu erfundene Erklärung und keine erneute Netzabfrage beim Anklicken eines Baumknotens.

„Show source“ öffnet den mit diesem Lauf erfassten Quelltext in einer wiederverwendeten, nichtmodalen Inspectoransicht. „Show related problems“ wechselt zum Check-Tab mit passendem Filter und sichtbarer Möglichkeit, diesen zurückzusetzen. Verknüpfung anhand echter Quellen-/Modellbezüge, nicht anhand eines Teilstrings des Modellnamens in der Meldung.

### 2.4 Repositories

```text
+---------------------+----------------------------------+----------------------+
| Repositories        | [Search model…] [Language…]      | Selected entry       |
| All enabled         | [Include browse-only] [Reload]   | Name                 |
| 1. local fixtures   |                                  | Version / language   |
| 2. models.example   | Model | Version | Repo | Status  | Source / precursor   |
|                     | ...                              | browseOnly / MD5     |
| [+] [-] [↑] [↓]     |                                  | [Preview source]     |
| [Enable/Disable]    | 125 entries · 2/3 loaded         | [Add exact root]     |
|                     | per-repository load problems      | [Compare metadata]   |
+---------------------+----------------------------------+----------------------+
```

Repositoryverwaltung gehört in diesen Bereich; Netzwerk-/Cacheoptionen in Settings. Kein zweiter widersprüchlicher Repositoryeditor im Settingsdialog.

Unterstützt werden explizit konfigurierte lokale Repositoryverzeichnisse und HTTP(S)-Basen mit `ilimodels.xml`, entsprechend den bestehenden Hostregeln. Keine Suchmaschine über das ganze Internet. Der Browser lädt nur die ausgewählten konfigurierten Indexe; kein selbständiger rekursiver Site-Crawl. Die Kompilierung darf weiterhin den bestehenden Resolververtrag zur Repositoriesuche verwenden. Zusätzliche dabei verwendete Herkunft bleibt in Dependencies sichtbar, auch wenn sie nicht in der Browserliste steht.

Kein verstecktes Defaultrepository. Beim ersten Start ist die Liste leer; die Oberfläche erklärt, wie eines hinzugefügt wird. Aktivierung und Prioritätsreihenfolge sind sichtbar und werden in die nächste Kompilierung übernommen. Eine Browserauswahl allein ändert keine Priorität.

Eine Tabellenzeile repräsentiert einen konkreten Indexeintrag. Gleichnamige Modelle aus zwei Repositories bleiben zwei Einträge. Versionen sind opake Originalstrings, keine Semver- oder Datumssortierung mit fachlicher Bedeutung. Textuelle Sortierung ist zulässig, aber es gibt kein daraus abgeleitetes „latest“-Badge. `precursorVersion` wird angezeigt; der Browser konstruiert keine eigene Nachfolgerauswahl. Genaue Indexduplikate bleiben erkennbar und werden nicht still zu einem einzigen fachlichen Modell erklärt.

`browseOnly`-Einträge sind über den aktivierbaren Filter sichtbar, standardmässig eingeschlossen und gekennzeichnet. Sie dürfen inspiziert werden; **Add exact root** ist für sie in diesem Umfang deaktiviert. Ein Tooltip erklärt, dass die automatische Modellsuche sie ausschliesst. Die GUI umgeht diese Eigenschaft nicht durch heimliches Herunterladen als lokale Rootdatei.

**Compare metadata** vergleicht genau zwei ausgewählte Einträge nebeneinander: Name, Version, Sprachversion, Repository, Quelle, precursorVersion, browseOnly und vorhandene Prüfsumme. Unterschiede werden markiert. Das ist ausdrücklich kein Textdiff und kein semantischer Diff. Nicht vorhandene Metadaten erscheinen als „Not provided“, niemals als erfundener Wert.

**Add exact root** übernimmt Modellname, rohe Version und genaue SourceId als explizite Rootauswahl. Das sichtbare Label enthält Version und Herkunft. Es übernimmt nicht bloss den Namen und lässt anschliessend einen anderen Kandidaten kompilieren. Übernahme startet keinen Lauf und verändert keine Dateien. Die nächste Kompilierung validiert die Identität durch die vorhandenen Resolver-/Compilerverträge. Das ist noch keine unveränderliche Inhaltsbindung: ein Digest-Pin oder Lock ist dafür zusätzlich notwendig; eine solche Sperre wird nicht behauptet.

Bei Namens-/Versionskonflikten zwischen bereits gewählten Roots keine stille Ersetzung: verständliche Konfliktanzeige und explizites Entfernen/Ersetzen. Gleicher Browser-Root wird nicht doppelt hinzugefügt.

Indexe werden asynchron geladen. Ein Fehler eines Repositories entfernt nicht die erfolgreich geladenen anderen Indexe. Leerer Index, nicht geladen, offline nicht verfügbar und fehlerhaft sind unterschiedliche Zustände. Suche und Filter arbeiten auf dem geladenen Katalog und lösen kein Netz pro Tastendruck aus. „Reload“ verwendet die bestehende Cachepolicy; ein erzwungener Cache-Bypass wird ohne passende gemeinsame API nicht vorgetäuscht.

Eine Quellenpreview ist ein expliziter, abbrechbarer Ladevorgang über denselben Hostloader. Sie erhält eine eigene Snapshotidentität und darf niemals den Quelltext eines früheren Compilerlaufs ersetzen. Keine Datei wird durch Preview oder Add root geschrieben.

## 3. Intelligente Diagnosen, ohne KI

Die Tabelle verwendet echte `Diagnostic`-Daten. Spalten: Severity, Code, Message, Source, Position; Phase im Detail. Sortierung und Filter sind reine Sichtoperationen. Die Gesamtsummen und das Compilerergebnis ändern sich dadurch nicht.

Detailliert angezeigt werden die vollständige lokalisierte Meldung, vorhandene Hinweise/Kataloghilfe, primäre und verwandte Fundstellen, Cause-/Depends-on-Bezüge und gegebenenfalls FailureId. Vorhandene `DiagnosticPresentation`- und Message-Formatter-Funktionen wiederverwenden. Wo die vorhandene API keinen Hilfetext anbietet, wird keiner ausgedacht.

„Group related“ reduziert auf Wunsch Folgediagnosen über die vorhandenen Ursachenbezüge. Dabei bleiben Anzahl und Zugang zu allen ausgeblendeten Diagnosen erhalten. Keine Schlussfolgerung „gleicher Text = gleiche Ursache“. IDs und tatsächliche Beziehungen bleiben stabil.

Die Vorschau zeigt ausschliesslich den **Originaltext der passenden Quellrevision**. Markierungen verwenden die bestehenden UTF-16-Offsets und halboffenen Bereiche. Zeilenenden dürfen nicht vor dem Mapping normalisiert werden. LF, CRLF, Umlaute, Surrogatpaare und leere Bereiche sind explizit zu behandeln. Ohne passende Quelle/Fundstelle erscheint ein Hinweis; keine fiktive Zeile 1 und kein Highlight in neu von Platte gelesenem Text.

Die Oberfläche bietet Copy message und Copy details. Reporttexte werden über die bestehende sichere Diagnoseformatierung erzeugt. Kein automatisches Öffnen von URLs aus Modelltexten oder Meldungen; Swing-HTMLdarstellung untrusted Strings verhindern oder korrekt escapen.

## 4. Zustand, Fehler und Sicherheit

Compilerläufe erhalten voneinander getrennte IDs und eingefrorene Eingaben. Die Anwendung unterscheidet Erfolg, fachliche Ablehnung, unvollständige Auflösung, technischen Fehler und Abbruch. Warnungen sind nach der gemeinsamen Warnungspolicy zulässig oder ablehnend; kein eigenes GUI-Gate.

Während des Laufs zeigt eine unbestimmte Fortschrittsanzeige die reale Aktivität. Keine erfundenen Prozentwerte. Logs sind sekundär, über „Show log“ erreichbar und begrenzt; sie ersetzen keine strukturierten Daten.

Bei einem neuen Lauf bleiben alte Resultate bis zur Ablösung deutlich als vorheriger Stand markiert. Abbruch macht alte Ergebnisse nicht aktuell. Späte Antworten eines abgebrochenen/überholten Runs dürfen keinen neueren Stand überschreiben. Gleiches gilt für Repositoryrefresh und Sourcepreview.

Netzwerk-/Dateizugriffe, Kopfindexierung und Kompilierung laufen nicht auf dem Swing-EDT. Die Settingsoberfläche unterstützt offline, persistenter Cache an/aus und Pfad, System/Direct/Explicit proxy sowie die vorhandenen Timeoutoptionen. Defaults aus gemeinsamen APIs übernehmen; keine zweite Konstantsammlung. Keine Proxykennwörter, neuen Authverfahren oder globalen Systemproperty-Umschaltungen.

Die GUI speichert nur Benutzerpräferenzen: Fensterposition/Splitter, Theme light/dark, Diagnosesprache, letzte Dateiauswahldirectory und Repository-/Netzwerk-/Cacheeinstellungen. Keine Quellen, Diagnosen, vollständigen Workspaces oder Secrets. Beschädigte Settings führen zu sichtbarer Warnung und sicheren Defaults, nicht zu Startabbruch oder zum Überschreiben der defekten Datei ohne Sicherung.

## 5. Abnahmekriterien

Jedes Kriterium benötigt nachvollziehbaren Code-/Test-/GUI- oder Packagingnachweis. Phasenzuordnung steht im EXECUTION_PLAN. Die IDs bleiben stabil, die Testklassen erhalten fachliche Namen, keine Phasenpräfixe.

| ID | Muss-Kriterium |
|---|---|
| GUI-01 | Ausschliesslich eigener Produktbranch; fremde Änderungen und PoC bleiben geschützt. |
| GUI-02 | GUI und ihre Laufzeit enthalten kein JavaFX, Browserembedding, Diagrammfrontend oder zusätzliche JVM-Buildkette. |
| GUI-03 | Explizites GUI-JPMS-Modul mit FlatLaf und ausschliesslich erforderlicher Runtimeclosure. |
| GUI-04 | Fenster mit Check, Dependencies, Repositories sowie gemeinsamem Sitzungsstatus existiert real. |
| GUI-05 | Alle Swingzustände EDT-konform; keine fachliche Arbeit/IO in Renderern oder EDT. |
| GUI-06 | Reeller, neuer OpenCode-2.x-/DeepSeek-Flash-1017-Review ist pro Phase technisch ausführbar und gebunden. |
| GUI-07 | Mehrere lokale Dateien, Drag-and-drop, Entfernen und sichere Quellenidentität funktionieren. |
| GUI-08 | All-inputs- und explicit-roots-Modus sowie Mehrmodell-Dateien werden korrekt unterschieden. |
| GUI-09 | Echte In-Process-Kompilierung über vorhandene APIs; weder CLI-Textparsing noch Subprozess/System.exit. |
| GUI-10 | Compilerresultat, WarningPolicy und Fehlerkategorien werden unverfälscht übernommen. |
| GUI-11 | Resultat enthält unveränderliche laufgebundene Quellen, Diagnosen und verfügbare Auflösungsdaten. |
| GUI-12 | Cancel, Einzellaufregel und Unterdrückung verspäteter Ergebnisse sind nachgewiesen. |
| GUI-13 | Eingabe-/Optionsänderungen invalidieren den Aktualitätsstatus, nicht heimlich das Ergebnisobjekt. |
| GUI-14 | Strukturierte Diagnosetabelle mit Severity, Code, Message, Source und Position. |
| GUI-15 | Filter/Suche/Sortierung erhalten Gesamtzahlen und fachliches Laufresultat. |
| GUI-16 | Ursachen/Folgediagnosen sind anhand echter IDs navigierbar; keine still verlorenen Meldungen. |
| GUI-17 | Lokalisierte Details, Related locations und vorhandene Hinweise werden korrekt gezeigt. |
| GUI-18 | Sourcepreview ist schreibgeschützt, snapshotgebunden und bei fehlender Quelle ehrlich. |
| GUI-19 | UTF-16-/CRLF-/Unicode-/Nullbereich-Markierungen zeigen die richtige Stelle. |
| GUI-20 | Copy message/details und Zeilenwahl nach Sortierung referenzieren die richtige Diagnose. |
| GUI-21 | Dependencydaten stammen aus dem realen Lauf, nicht aus Regex oder Repositorymetadaten allein. |
| GUI-22 | Direkte/transitive Imports und mehrere Roots werden korrekt und deterministisch dargestellt. |
| GUI-23 | Diamond-Graphen, gemeinsame Unterabhängigkeiten und Zyklen bleiben vollständig und endlich. |
| GUI-24 | Missing, ambiguous, blocked, builtin und partielle Ergebnisse sind unterscheidbar. |
| GUI-25 | Inspector zeigt tatsächliche Version, Quelle, Herkunft und gesonderten Semantikstatus. |
| GUI-26 | Direct imports und Used by liefern korrekte aus-/eingehende Kanten. |
| GUI-27 | Resolution-Ansicht verwendet nur erfasste Entscheidungen; kein erfundenes latest oder Re-Resolve. |
| GUI-28 | Navigation Dependencies ↔ Problems/Source verwendet echte Identitäten. |
| GUI-29 | Repository hinzufügen/entfernen/aktivieren/sortieren; kein heimlicher Default. |
| GUI-30 | Browser lädt vorhandenen Indexvertrag über gemeinsamen Loader/Cache/Proxy, ohne Zweitparser. |
| GUI-31 | Repositoryfehler sind isoliert; leer/unavailable/partial/offline werden nicht vermischt. |
| GUI-32 | Katalogsuche ist lokal; gleiche Namen verschiedener Versionen/Quellen bleiben sichtbar. |
| GUI-33 | Originalversionen, precursorVersion und browseOnly werden ohne neue Auswahlsemantik gezeigt. |
| GUI-34 | Metadatenvergleich zweier konkreter Einträge funktioniert ohne semantischen Diff. |
| GUI-35 | Quellenpreview ist explizit, abbrechbar, schreibgeschützt und vom Compiler-Snapshot getrennt. |
| GUI-36 | Add exact root bewahrt Name+Version+Quelle; gleichnamiger anderer Kandidat darf nicht einspringen. |
| GUI-37 | browseOnly führt nicht zu verstecktem Bypass oder stiller Aufnahme in die Compilation. |
| GUI-38 | Offline verhindert tatsächliche Netzaufrufe; vorhandener/nicht vorhandener Cache wird korrekt gezeigt. |
| GUI-39 | Laufrelevante Settings/Defaults und neue Reihenfolgen sind konsistent und eingefroren pro Lauf. |
| GUI-40 | Settings atomar gespeichert; Defekt/Schreibfehler sichtbar; keine Secrets oder Quelltexte persistiert. |
| GUI-41 | Tabwechsel, Keyboardaktionen, Fokus, Resize sowie Light/Dark sind praktisch bedienbar. |
| GUI-42 | Ein leerer/fehlerhafter/abgebrochener Ablauf erzeugt keine irreführende Erfolgsmeldung. |
| GUI-43 | Schliessen beendet Worker und IO; begrenzte Vorschauen/Logs/Bäume verhindern unkontrollierte Last. |
| GUI-44 | Untrusted Texte/URIs werden sicher angezeigt; keine HTMLinjektion oder automatische URL-Aktion. |
| GUI-45 | Build-/Run-/GUI-Test-/Package-Einstiege nutzen die bestehende Jenesis-Architektur. |
| GUI-46 | Normale JVM-Tests bleiben headless-fähig und benötigen weder Node noch GUI-Display. |
| GUI-47 | Tatsächliches jlink-Image startet die GUI; Modul-, Ressourcen- und Native-Library-Closure geprüft. |
| GUI-48 | macOS-ARM64-App startet ausserhalb des Checkouts und ohne Nutzung eines externen Java. |
| GUI-49 | Reale GUI-Szenarien prüfen alle drei Hauptfunktionen, nicht nur ViewModels/Screenshots. |
| GUI-50 | Betroffene CLI-/SDK-/LSP- und Buildverträge sind mit gezielten Regressionen geschützt. |
| GUI-51 | Dokumentation benennt reale Befehle, Toolchain, Plattform und verbleibende Auslieferungsgrenzen. |
| GUI-52 | Alle sieben Phasen haben gültige unabhängige PASS-Urteile; finale Belege gehören zum finalen Produktstand. |
