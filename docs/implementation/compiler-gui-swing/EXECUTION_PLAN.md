# Ausführungsplan — sieben sequenzielle Phasen

## 1. Arbeitsmodus und Gate

Zielbranch: `codex/compiler-gui-swing`. Aus dem beim Start bewusst geprüften `main`-Stand arbeiten, vorzugsweise in einem eigenen Worktree. Die Referenz aus SOURCE_BASELINE ist ein Analysepin, kein Befehl, neuere Arbeit auf diesen Stand zurückzusetzen.

Vor Implementierung `git status`, aktive Branches/Worktrees, aktuellen main-/origin/main-Stand und geltende AGENTS-Dateien prüfen. Fremde Änderungen weder verwerfen noch automatisch stashen. Existiert der Zielbranch bereits, Herkunft und Auftrag prüfen und nur eine passende vorhandene Implementierung fortsetzen; kein `switch -C`/`reset --hard`. Eine nicht passende Namenskollision durch eindeutig benannten neuen Branch lösen und dokumentieren.

Beispiel nach überprüfter Basis und freiem Zielpfad:

```sh
git worktree add -b codex/compiler-gui-swing ../interlis-java-compiler-gui-swing main
```

Die konkrete Basis-SHA wird vor dem ersten Produktcommit erfasst. Spezifikationsdateien im Zielworktree ablegen. Kein Commit auf main, kein automatischer Push, Merge, Release oder PR.

**Jede Phase:** Implementieren → kleinste ausreichende Tests → Kandidatencommit → frischer unabhängiger OpenCode-Review → Findings durch Codex beheben → betroffene Retests → neuer Kandidat und neue Reviewrunde → gültiges PASS → nächste Phase. Ein Urteil einer anderen Phase oder eines anderen Commits genügt nicht.

Die letzte bestandene Phase liefert den `phaseBaseSha` der nächsten. Reviews sehen zusätzlich den kumulativen Diff gegen `baseSha`. Sie prüfen neue Anforderungen und betroffene bereits bestandene Verträge. Vollständiger Produkttestlauf pro Phasenwechsel ist ausdrücklich nicht vorgesehen.

## 2. Phasenübersicht

| Phase | Produktzuwachs | Neu fällige Kriterien |
|---|---|---|
| G0 — Foundation | Branch, Build, Swingrahmen, Reviewstarter, jlink-Spike | GUI-01..06, GUI-46..47 |
| G1 — Compilation | echte Sitzung und typed Backend | GUI-07..13 |
| G2 — Diagnostics | benutzbarer Check-Arbeitsbereich | GUI-14..20 |
| G3 — Dependencies | laufgebundener Dependency Explorer | GUI-21..28 |
| G4 — Repositories | vollständiger Browser und exakte Rootübernahme | GUI-29..38 |
| G5 — Integration | Settings, Bedienbarkeit, Robustheit und Sicherheit | GUI-39..44 |
| G6 — Delivery | native App und gesamte Zielerreichung | GUI-45, GUI-48..52; Abschlussprüfung GUI-01..52 |

GUI-01..06 bleiben in jedem Review als globale Regeln relevant; ihre unveränderten Nachweise dürfen wiederverwendet werden. Die genaue Liste der pro Runde fälligen Kriterien steht maschinenlesbar im Reviewrequest. Nicht fällige Zukunftskriterien in frühen Phasen nicht als FAIL oder als vorweggenommenes PASS deklarieren.

## G0 — Foundation

### Arbeiten

Repository-/PoC-Bestand inventarisieren. Existiert bereits ein GUI-Modul, die sinnvollen produktneutralen Teile übernehmen und den Swing-Produktpfad eindeutig machen. JavaFX nur aus der neuen GUIclosure entfernen; keine fremden Branches oder gesamten Diagrammprodukte bereinigen.

JDK25-/Jenesispfad, Compiler-Module, vorhandene Dependency-Pins und Headful-Testmöglichkeiten prüfen. FlatLaf fix pinnen, GUI-Modul registrieren, expliziten Modulstart und minimales reales Fenster mit drei Tabs implementieren. Noch nicht implementierte Aktionen deaktivieren statt Erfolgsstubs zu verwenden.

Früh ein jlink-Image mit Swing, FlatLaf und den benötigten Compiler-/Workspace-/Repositoryabhängigkeiten bauen. Native-Library-/Multi-Release-/automatische-Modulrisiken jetzt klären. Kein GUI-Funktionsausbau, solange der gewählte Produktpfad nicht startet.

Autonomen Reviewstarter nach OPENCODE_REVIEW_PROMPT wiederverwenden/anpassen oder klein neu implementieren. Lokalen OpenCode-2.x-Aufruf, direkte Providerzuordnung und tatsächliche 1017-Modellzuordnung prüfen. Headful Desktop-/Robot-/Screenshotberechtigungen prüfen, keine OS-Sicherheitsabfragen umgehen. Isolation und menschenfreie Test-/Reportrechte konfigurieren. G0-Review selbst dient als erster echter Funktionsnachweis; ein gesonderter teurer LLM-Testchat ist nicht nötig.

### Kleinste ausreichende Tests

GUI-Modulbau, Modul-/Closureprüfung, echter Start des Fensters über Module-Path und einmal aus dem Runtimeimage. Screenshot plus Komponenten-/Prozessbeleg. Normaler headless Testpfad darf ohne Display nicht scheitern. Reviewgate mit kleinen lokalen negativen Fixtures testen: falsche SHA, fehlendes Kriterium, FAIL/BLOCKED, abgebrochener Prozess und veränderte Quellen werden abgewiesen. Diese Fixtures testen nur den Gatevalidator und sind keine echten Reviewurteile.

Nur einen gezielten bestehenden CLI-/SDK-Modulsmoke ausführen, wenn Buildregistrierung/Abhängigkeitsbehandlung verändert wurde. Keine Sprachkorpus-/Wasm-/Browsertests.

### Gate

GUI startet mit FlatLaf aus der tatsächlich gelinkten Runtime; Reviewstarter führt den G0-Review real aus. Reviewer prüft unabhängig Closure/No-JavaFX und einen Gate-Negativfall. G1 beginnt erst nach PASS.

## G1 — Compilation

### Arbeiten

Immutable SessionInputs/SessionOptions/CompileRequest/CompileResult, Controllerzustände und dünne SDK-/Hostadapter implementieren. Lokale Dateien, sichere Identität, Rootmodus, Mehrmodell-Dateien, Hintergrundlauf, Cancel und revisionsgebundene SourceSnapshots.

Repositorykonfiguration bereits im Backend unterstützen, obwohl die Verwaltungsoberfläche erst G4 vollständig wird. Vorhandenen Hostresolver verwenden; Testfixtures können diese Konfiguration direkt injizieren. Ergebnis-/Warnungskategorien übernehmen. Kein Diagramm-/Formatteraufruf und keine CLI-Textauswertung.

### Kleinste ausreichende Tests

Lokales gültiges Modell, Syntaxfehler, semantischer Fehler, zwei Modelle in einer Datei, explicit-root-Closure mit weiterer nur verfügbarer Datei, fehlender Import, Warnungspolicy und Cancel/late-result. Ein deterministischer lokaler Repositoryfall testet denselben Hostpfad wie später die GUI. Originalsource nach Lauf extern ändern und beweisen, dass das Ergebnis unverändert bleibt.

Headful `compile`-Smoke: Datei hinzufügen → Compile → echter Ergebnisstatus; UI bleibt während kontrolliert blockiertem Hintergrundjob bedienbar. Ein CLI-/SDK-Paritätsfall genügt, sofern keine weiteren gemeinsamen Verträge verändert wurden.

### Gate

Ein echter Compilerlauf funktioniert im Fenster, einschliesslich nachvollziehbarer Ablehnung und Cancel. Reviewer verwendet eine eigene kleine Mehrmodell-/Rootgegenprobe und untersucht die Request-/Quellenbindung. Keine G2-Arbeit vor PASS.

## G2 — Diagnostics

### Arbeiten

Problemtabelle, Filter/Suche/Sortierung, Ursachen-/Folgediagnosen, DE/EN-Diagnoseprosa, Related locations, Copy und read-only Sourcepreview implementieren. Detail-/Navigationprojektionen rein halten. Keine zweite Meldungsbibliothek. Zeilen-/Spalten-/UTF-16-Mapping korrekt lösen.

### Kleinste ausreichende Tests

DiagnosticPresenter mit Rootcause/Folgediagnose, fehlender Range und Filterzählung. SourcePreviewModel mit CRLF, Umlaut, Surrogatpaar, EOF-/Nullbereich und stale/fehlender Source. View-/Modelrow-Mapping nach Sortierung mit mindestens zwei verschiedenen Quellen.

Ein echter Headful-Diagnoseablauf: invalides Modell kompilieren → sortieren/filtern → richtige Zeile wählen → richtige Fundstelle und Related location öffnen → kopieren. Eine gültige Kompilierung muss die vorherige Fehlerliste sauber ablösen.

### Gate

Check ist fachlich nützlich, nicht nur ein formatiertes Logfenster. Reviewer erstellt eine unabhängige Range-/Sortier-Gegenprobe und kontrolliert eine echte Headful-Evidenz. PASS erforderlich.

## G3 — Dependencies

### Arbeiten

DependencySnapshot aus echten captures/bindings bauen. Lazy Baummodell, pfadlokale Zyklen, Mehrfachvorkommen, Roots, Missing/Ambiguous/Blocked/Builtin, Direct imports, Used by und Inspector. Auflösungsschritte in einer einfachen Liste; keine Graphzeichenfläche. Navigation zu Source und Problems.

### Kleinste ausreichende Tests

Chain, Diamond, Cycle, mehrere Roots und gleiche Modellnamen mit verschiedenen Quellen. Modellauflösungszustand und fachliches Compilerergebnis unabhängig prüfen. Partial-/Rejected-Ergebnis darf nicht als erfolgreicher leerer Graph erscheinen. Ein kleiner erzeugter Baum prüft Lazy-Expansion statt exponentiellem Vorabaufbau.

Headful `dependencies`: echten Mehrdateienlauf prüfen → Knoten expandieren → Quelle/Version und Used by ansehen → zu verwandter Diagnose navigieren. Keine Screenshots als Ersatz für echte Kantenassertionen.

### Gate

Explorer erklärt einen tatsächlich ausgeführten Lauf. Reviewer kontrolliert insbesondere gemeinsamen Subgraph plus Zyklus und prüft, dass kein Index-/Namensregex als Semantikquelle verwendet wird. PASS erforderlich.

## G4 — Repositories

### Arbeiten

Repositoryliste mit Aktivierung/Priorität, asynchronen Indexloader, lokale Suche und Filter, Eintragsinspector, Metadatenvergleich, explizite Sourcepreview und Add exact root implementieren. Identity-/Version-/browseOnly-Regeln durchhalten. Cache/Offline/Proxy aus gemeinsamen Adaptern verwenden.

Die exact-root-Kette muss end-to-end beweisen, dass die ausgewählte SourceId/Version im Compilerergebnis wieder auftaucht. Reicht eine vorhandene öffentliche Schnittstelle nicht, minimale fachlich passende Erweiterung mit SDK-/CLI-Vertragstest; keine Umgehung oder GUI-eigene Auswahl.

### Kleinste ausreichende Tests

Lokale Repositoryfixtures mit zwei Versionen, zwei Repositories gleichen Modellnamens, browseOnly und Mehrmodell-Datei. Lokaler HTTP-Testserver für Erfolg, ein defektes Repository neben einem erfolgreichen, Timeout/Cancel und offline null Requests. Gemeinsame XML-/URI-Sicherheit einmal als Delegations-/Regressionstest bei betroffener Adaptergrenze, nicht alle Parserangriffe neu implementieren.

Metadata comparison und gefilterte/sortierte Rootübernahme mit stabiler EntryId. Controlled refresh/preview race. Headful `repositories`: Index laden → suchen → zwei Versionen vergleichen → eine genaue Quelle übernehmen → kompilieren → dieselbe Quelle in Dependencies belegen. Dazu ein browseOnly-Fall mit deaktivierter Aufnahme.

### Gate

Alle Browserfunktionen funktionieren ohne stillen Einfluss auf einen vorhandenen Lauf. Reviewer prüft einen eigenen konkurrierenden Same-name-Kandidaten und beobachtet offline tatsächliche Netzwerkaufrufe statt nur ein Flag. PASS erforderlich.

## G5 — Integration

### Arbeiten

Settingspersistenz, gemeinsame Optionen/Defaults, Light/Dark, Tastatur/Fokus, Resize, Empty states und Last-run-/stale-Banner vervollständigen. Cache-/Proxy-/Timeoutoptionen konsistent anschliessen. Sichere Plain-Text-Renderer, begrenzte Logs/Previews/Bäume und sauberes Shutdownverhalten.

Keine neuen Goodies einführen. Kein Projektformat, Watcher, Autoformat oder Editor. Für die drei Hauptbereiche UI-Abstände, Schrift, Tabellenbreiten und Fokusführung konsistent gestalten; keine Layouts mit absoluten Pixelkoordinaten.

### Kleinste ausreichende Tests

Settings roundtrip in isoliertem Benutzerverzeichnis; defekte/teilweise/unbekannte Werte und Schreibfehler. Repositoryreihenfolge bleibt erhalten. Zwei gezielte Controller-Races, Closing während verzögertem IO, HTMLähnlicher Modellname/Diagnosetext und redigierte URI. Ein begrenzter synthetischer Skalierungstest ohne umfangreiche Benchmarkkampagne.

Headful `lifecycle`: Keyboardpfad durch Tabs, Light/Dark, kleinere Fenstergrösse, stale Inputs, Cancel, Close. Nach Close tatsächliches Prozessende prüfen. Kein pauschaler Wiederholungslauf aller G1–G4-Tests, nur berührte Teilbereiche.

### Gate

Anwendung ist zusammenhängend bedienbar und zuverlässig. Reviewer nimmt eine eigene Shutdown-/late-callback-Gegenprobe und prüft Plain-Text-/Settingsgrenzen. PASS erforderlich.

## G6 — Delivery und Gesamtziel

### Arbeiten

Jenesis-JPackageintegration und macOS-ARM64-App abschliessen. Passende native Ressourcen, Lizenzhinweise, Pin-/Modulinventar und echte Packagingbefehle dokumentieren. App ausserhalb des Checkouts starten und eingebettete Runtime nachweisen. CLI-/LSP-Distributionsgrenzen kontrollieren.

Endnutzerkurzanleitung mit lokalen Dateien, Repository-only-Root, Problemsnavigation, Dependencies, Offline und bekannten nicht unterstützten Funktionen schreiben. Kein Signierungs-/Notarisierungsbeleg erfinden.

### Kleinste ausreichende Tests

Aktuelles finales Appimage bauen. Ein zusammenhängender **packaged** Headful-Smoke führt alle drei Hauptfunktionen mit deterministischen Fixtures aus. Kein Wiederholen aller GUIprofile ohne konkrete Änderung. Jlink-Modulinventar/Java-home/ressourcenunabhängiger Start und vollständiges Prozessende aufzeichnen.

Gezielte Regression für tatsächlich geänderte gemeinsame Build-/Repository-/Workspace-APIs: vorhandener CLIgültig-/ungültig-Prozess und gegebenenfalls ein LSP-/SDKsmoke. Wenn dort keine Produktänderung seit gültigem Nachweis vorliegt, Belege wiederverwenden und keine komplette Suite starten.

### Gate

Reviewer prüft GUI-01..52 und die kumulative Produktänderung, validiert frühere Rohbelege sowie den endgültigen Paketnachweis und führt mindestens eine eigene gezielte End-to-End-/Packaginggegenprobe aus. Alle Pflichtkriterien brauchen PASS. Nicht geprüfte zusätzliche Plattformen sind eine offen dokumentierte Scopegrenze, nicht ein ausgedachtes PASS.

## 3. Status und Artefakte

Unter `docs/implementation/compiler-gui-swing/STATUS.md` einen kompakten Ausführungsstand mit Phase, Kandidaten-SHA, implementierten IDs, Tests und Reviewverweisen pflegen. Anfangs keine Phase als bestanden markieren. Zusätzlich vorhandene zentrale Statusdokumentation gezielt aktualisieren, soweit AGENTS dies verlangt.

Rohbelege und Urteile liegen ausserhalb des geprüften Quellbaums, damit ein PASS-Commit nicht seinen eigenen Hash verändern muss. Ein späterer reiner Status-/Dokumentationscommit verweist auf die tatsächlich geprüfte Produkt-SHA und unveränderten Produktinhalt; er darf nicht als ein selbst geprüftes neues Produkt ausgegeben werden. Keine bedeutungslosen Testwiederholungen nur wegen Statusänderungen.

Nach FAIL repariert Codex; der Reviewer repariert nicht. Nach BLOCKED technische Ursachen begrenzt autonom behandeln. Bleibt der Blocker, Phase offen lassen und nicht in die nächste Phase gehen. Niemals einen fehlenden externen Review durch eine Selbsteinschätzung ersetzen.
