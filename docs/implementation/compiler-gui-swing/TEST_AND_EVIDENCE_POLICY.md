# Zielgerichtete Tests und belastbare Nachweise

## 1. Prinzip

Die Frage lautet immer: **Welches konkrete Regressionsrisiko oder Abnahmekriterium beweist dieser Lauf?** Nicht: „Welche Tests könnten wir vorsichtshalber alle starten?“

Eigene neue Logik fokussiert testen; echte Integrationsgrenzen mit kleinen Szenarien belegen. Unveränderte Compilersemantik benötigt keine neue vollständige Konformitätskampagne. Kein pauschaler Default `contracts`, vollständiger Korpus, Benchmark, Browser, Wasm, Web Image oder release-check. Ein solcher Lauf ist nur bei konkret betroffener Grenze zulässig und wird kurz begründet.

Codex und OpenCode führen breite Prüfungen nicht doppelt aus. Der Reviewer liest Originalbelege und führt eigene kleine Gegenproben aus. Ein unabhängiges Review ist keine zweite komplette CIpipeline.

## 2. Testebenen

**Pure Unit Tests:** Sessionzustand, Diagnosenprojektion, Rangeabbildung, Graphprojektion, Katalogfilter, Settingswerte. Kein Display, Netzwerk oder echter Nutzer-Cache.

**SDK-/Hostintegration:** echte Compiler-/Workspace-/Repositoryinstanzen mit kleinen `.ili`- und Indexfixtures. HTTP auf Loopback mit Requestzähler; kein Live-repository als Pflichtfixture. Fehler und Abbrüche kontrollierbar erzeugen.

**Headful Swing Tests:** wirklich angezeigter JFrame mit FlatLaf, echte Swing Events/Robotaktionen und semantische Assertions. Alle UIzugriffe der Teststeuerung über EDT. Native Filechooser kann im grösseren Flow über einen injizierten Dialogadapter kontrolliert werden; mindestens ein tatsächlich sichtbarer Choosersmoke prüft die reale Verkabelung. Direkter Controlleraufruf allein ist kein GUItest.

**Packaged Process Test:** der native App-Launcher startet das gelieferte Produkt mit eingebetteter Runtime. Ein testseitiger Controller/Robot darf Fixtures bedienen; kein Sonderprodukt, das statt der GUI nur einen Testbericht schreibt. Unterstützungsflags müssen normale Service-/UIpfade verwenden und dürfen keinen Test-PASS aus dem Produktcode liefern.

Headful Tests separat markieren und explizit auswählen. Headless CI darf sie nicht versehentlich starten; fehlt für ein fälliges Headful-Gate ein Desktop, ist der Nachweis BLOCKED, nicht „passed with skip“. macOS-Automation-/Screenshotrechte in G0 prüfen. Ein Linux-Xvfb-Lauf kann Swingverhalten belegen, ersetzt aber keinen macOS-App-Start.

## 3. Testgruppen

Namen in dieser Tabelle sind neue fachlich benannte Testvorschläge. Vorhandene passende Tests bevorzugen. Keine `G1Test`, `P3GuiReviewTest` oder Fixtures in kryptischen Phasenordnern.

| Gruppe | Vorgeschlagene Tests | Kernfrage |
|---|---|---|
| T01 Build/Module | `GuiModulePathTest`, `GuiRuntimeImageSmoke` | Echte minimale Closure, kein JavaFX und kein Classpathfallback? |
| T02 Reviewgate | `ReviewGateValidationTest` | Falsche SHA/IDs, fehlende Kriterien, FAIL, Abbruch, fehlende Gegenprobe sicher abgewiesen? |
| T03 Session | `SessionControllerTest`, `RootSelectionTest` | Was ist Eingabe, Root, aktiver Lauf, veraltet? |
| T04 Compiler | `CompilerHostAdapterTest` | Reale Erfolg-/Fehler-/Warnungs-/Repositoryergebnisse ohne zweite Semantik? |
| T05 Quellen | `RunSourceSnapshotTest` | Nachträglicher Dateiedit kann einen alten Snapshot nicht verändern? |
| T06 Abbruch | `CompileCancellationTest`, `LateResultGuardTest` | Keine veraltete Übernahme, tatsächliche kooperative Beendigung? |
| T07 Diagnosen | `DiagnosticPresenterTest`, `DiagnosticSelectionTest` | Filter-/Ursachen-/Sortierregeln mit richtigen IDs? |
| T08 Sourcepreview | `SourcePreviewMappingTest` | UTF-16, CRLF, Unicode, EOF und fehlende Source korrekt? |
| T09 Dependencies | `DependencyProjectionTest`, `DependencyNavigationTest` | Chain/Diamond/Cycle/Partial/Identität sowie Used by richtig? |
| T10 Catalog | `RepositoryCatalogServiceTest`, `RepositoryEntryIdentityTest` | Alle richtigen Zeilen und isolierte Fehler, keine Zweitparser? |
| T11 Rootübernahme | `ExactRepositoryRootTest` | Name+Version+Quelle statt nur Name; browseOnly wird nicht umgangen? |
| T12 Network | `RepositoryOfflineTest`, `RepositoryCancellationTest` | Offline null Requests; Preview und Compile gleiche Policy? |
| T13 Browser-Races | `RepositoryGenerationTest` | Langsame alte Antwort überschreibt keine neue Auswahl? |
| T14 Settings | `GuiSettingsStoreTest` | Atomarer Austausch, Reihenfolge, Defekte, kein realer Nutzerpfad? |
| T15 Safety | `SafeTextRenderingTest`, `GuiShutdownTest` | Kein HTML/Secretleak, keine zurückbleibenden Worker? |
| T16 Swing | `GuiInteractionTest` mit expliziten Profilen | Echte Benutzeraktionen führen zu den spezifizierten Ergebnissen? |
| T17 Package | `PackagedGuiSmoke` | Native App nutzt embedded Java und läuft ausserhalb des Checkouts? |
| T18 Verbraucher | vorhandene fokussierte CLI-/SDK-/LSP-Tests | Veränderte gemeinsame Verträge bleiben kompatibel? |

Ein Test, der mehrere Zeilen dieser Matrix sinnvoll abdeckt, muss nicht künstlich aufgespalten werden. Keine hundert triviale Gettertests.

## 4. Deterministische Fixtures

Unter fachlichen Pfaden, beispielsweise:

```text
testdata/gui/compilation/valid-and-invalid/
testdata/gui/compilation/multiple-models/
testdata/gui/dependencies/diamond/
testdata/gui/dependencies/cycle/
testdata/gui/repositories/version-selection/
testdata/gui/repositories/browse-only/
testdata/gui/diagnostics/unicode-ranges/
```

Bestehende kleinere Fixtures wiederverwenden, falls ihre Aussage identisch ist. Für neue Fixtures dokumentieren, welches Verhalten geprüft wird. Keine grossen realen Repositories duplizieren.

Pflichtkonstellation für exact-root: Repository A und B bieten denselben Modellnamen, A hat höhere Priorität, in B wird ein konkreter Kandidat gewählt. Bei einer ansonsten gültigen konfliktfreien Sitzung muss Compile erfolgreich exakt den gewählten Kandidaten aus B verwenden, niemals still A auswählen. Eine pauschale Ablehnung der Browserauswahl erfüllt dieses Kriterium nicht. Nur bei einem separat erzeugten echten Konflikt (z. B. zwei widersprüchlichen expliziten Roots oder Metadaten-/Quellmismatch) ist eine nachvollziehbare Ablehnung das erwartete Ergebnis. Zusätzlich zwei Versionen innerhalb eines Repositories. Metadata-/Quellversionmismatch muss als realer Fehler sichtbar bleiben.

Offlineprüfung: Loopbackserver mit Zähler, Cache bewusst warm/kalt, tatsächlichen Zählerstand nach vollständigem Ablauf prüfen. Ausschliesslich `offline == true` im DTO zu prüfen reicht nicht. Kein Internetzugriff als Ersatz für das lokale Netzwerkfixture.

Raceprüfung: Latches/barriers/fake-clock an echten Serviceseams statt bloss lange Sleeps. Reihenfolge gezielt erzwingen: A startet, A wird abgebrochen/überholt, B wird aktuell, A antwortet zuletzt. Das Ergebnis muss B bleiben. Browser und Compiler haben getrennte Generationen.

## 5. GUI-Szenarien

| Profil | Benutzerpfad | Nachweis |
|---|---|---|
| `shell` | App öffnen, drei Tabs, Resize, Close | Fenster-/Komponentenbaum, Screenshot, Prozessende |
| `compile` | Dateien hinzufügen, Roots prüfen, Compile/Cancel | echter Runstatus und reagibler Tabwechsel |
| `diagnostics` | Invalides Modell, Filter, Sortierung, Auswahl, Related location, Copy | DiagnosticId und passende originale SourceRange |
| `dependencies` | Root expandieren, Diamond/Zyklus, Used by, Problemsnavigation | echte Graphidentitäten plus sichtbarer Inspector |
| `repositories` | Laden, Suchen, zwei Versionen vergleichen, Preview, exact root übernehmen, Compile | ausgewählter Kandidat entspricht tatsächlicher Herkunft |
| `lifecycle` | Theme, Tastatur, kleine Grösse, defekte Settings, delayed IO, Close | richtige Zustände und keine hängenden Prozesse |
| `packaged` | kombinierter kurzer Pfad über alle drei Tabs im ausgelieferten Appimage | tatsächlicher Native-Launcher, eingebettete Runtime und Resultate |

Screenshots belegen sichtbare Qualität und Fehlerzustände, nicht allein fachliche Richtigkeit. Mindestens Check mit gewählter Diagnose, Dependencies mit Herkunft und Repositoryvergleich dokumentieren. Keine pixelgenauen goldenen Screenshots über Plattform-/Fontversionen hinweg. Stattdessen schmale semantische Assertions zu sichtbaren Labels, gewählten IDs, Fokus und Aktionen.

## 6. Befehle und Auswahl

Der bestehende selektive JVM-Einstieg ist:

```sh
java build/InterlisJvmBuild.java build \
  --module interlis-compiler-gui \
  --tests '.*(DiagnosticPresenterTest|SourcePreviewMappingTest)'
```

Das neue Modul muss zuvor registriert sein. Regex und Klassen gegen den implementierten Testbaum prüfen. Keine nicht existierenden Tests als ausgeführt aufführen. Headful Profile sind die in G0 neu einzubauenden `gui-check`-Einstiege, nicht bereits auf der Baseline vorhandene Features.

Retestregel: Produktänderung an Presenter → Presenter-/Selektionstest und bei betroffener Verkabelung ein Headful-Diagnosefall. Produktänderung an Repositoryrootbindung → exact-root plus nächster betroffener SDK-/CLItest. Reiner Dokument-/Statuscommit → Dokumentkonsistenz, keine Kompilierung. Ein neu entdeckter grenzübergreifender Fehler rechtfertigt gezielte Erweiterung der Auswahl.

## 7. Rohbelege und Wiederverwendung

Pro Testinvokation mindestens speichern:

```text
candidateSha / relevantProductTreeHash
platform / arch / toolchain
argv (ohne Secrets), workingDirectory, start/end
input fixture hashes, artifact hashes
exitCode / signal / timeout
executed test IDs, counts, failures, skips
stdout/stderr beziehungsweise JUnit-/GUI-Prozessreport
```

Jeder Eintrag im Testledger beantwortet `question`, `criteria`, `whyThisScope`, `result` und `evidencePaths`. Wiederverwendete Ergebnisse nennen ursprüngliche SHA sowie die überprüfte Gleichheit der relevanten Produktdateien, Eingaben, Toolchain und Umgebung. SHAallein nicht ignorieren; begründete inhaltsidentische Wiederverwendung ist erlaubt.

Ein abgebrochener Prozess, ein leerer Report, ein übersprungener Pflichtfall und ein blosses `exit 0` ohne nachgewiesene Testausführung sind kein PASS. Viewer-only-Unit-Tests ersetzen keine Headful-Abnahme. Nicht ausgeführte zusätzliche Betriebssysteme als nicht geprüft deklarieren, nicht als fehlgeschlagen oder bestanden erfinden.

## 8. Reviewer-Gegenproben

Pro Phase mindestens eine konkrete unabhängige Gegenprobe zu einer nichttrivialen Annahme, in G2–G4 vorzugsweise zwei kleine unterschiedliche Fälle. Der Reviewer wählt Inputs und prüft selbst. Er darf vorhandene Testharnesses nutzen, soll nicht nur den Codexbefehl wiederholen.

G0: Closure oder Reviewgate; G1: Rootmodus/Abbruch; G2: Range/Sortieridentität; G3: Diamond/Zyklus; G4: source/version binding/offline; G5: Shutdown/unsafe text; G6: paketierter Gesamtpfad oder Runtimeherkunft. Gegenproben in isoliertem Scratchbereich, niemals Produktfixtures oder Goldens passend machen.
