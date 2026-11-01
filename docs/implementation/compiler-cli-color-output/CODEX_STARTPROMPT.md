# Codex – Startprompt für die autonome CLI-Farbausgabe

Du bist **Agent A (Implementierung, Tests, Review-Orchestrierung)** für den Folgeauftrag **`interlis-compiler` – farbige Konsolendiagnosen** im Repository `edigonzales/interlis-java`. Dies ist ein **ausdrücklicher Auftrag zur Umsetzung aller drei Phasen COLOR1, COLOR2 und COLOR3**. Arbeite ohne manuelle Phasenstopps; eine Phase wird erst nach einer **tatsächlich durchgeführten, unabhängigen OpenCode-/DeepSeek-Prüfung mit `PASS`** abgeschlossen.

**Verbindliche Dokumente, in dieser Reihenfolge lesen:**

1. Repository-`AGENTS.md`, für Spezifikationsänderungen zusätzlich `docs/spec/AGENTS.md`.
2. `docs/implementation/compiler-cli-color-output/EXECUTION_PLAN.md` (**massgebliche fachliche Spezifikation**, IDs C1-01..C3-10).
3. `docs/implementation/compiler-cli-color-output/OPENCODE_REVIEW_PROMPT.md` (vollständiger unabhängiger Prüfauftrag).
4. Vorhandener Abschluss `docs/implementation/compiler-cli-modernization/STATUS.md` und betroffene CLI-, Diagnose-, Test- und Builddateien. G2/P3 historische Abnahmen bleiben erhalten.

## 1. Vorbereitungen / Scope

- `pwd`, `git status --short`, `git rev-parse HEAD`, `git remote -v`, aktuellen Branch prüfen. Die Ausarbeitung basiert auf dem erreichbaren `edigonzales/interlis-java` (`main`-Gitbaum `b8051493a8b93e0be4b12735785803d068df6f80`), **nicht** darauf zurücksetzen. Der im ursprünglichen Auftrag genannte Link `edigonzales/interlis-guru` war beim Review nicht erreichbar; wenn dein lokaler Remote anders lautet, die realen Pfade/Commits anhand des Checkouts verifizieren und transparent dokumentieren.
- Ausschliesslich Folgeauftrag zur farbigen **Textdiagnoseausgabe**. Kein Zurückbringen von `ili2c`-CLI-Kompatibilität, keine Umbenennung, kein Parser-/Sprachrefactoring, keine P4-/Validator-/DB-Aufgabe.
- Bestehende `DiagnosticMessageFormatter`, `ConsoleDiagnosticRenderer`, `JsonDiagnosticWriter`, `DiagnosticLanguage`, `AnalysisSnapshot.requestDiagnostics()`, `CliEnvironment` und `CommandSpec` untersuchen und wiederverwenden. Keine zweite Diagnosequelle oder globales Terminal-Singleton anlegen.
- JDK 25, Gradle-Wrapper, Node/Browser, aktuelle CLI-Distribution und verfügbare Testgates prüfen. Kein Python, auch nicht für Reviewhilfen. Prüfe im Voraus den bestehenden Python-Sperrpfad `tools/verification/without-python.cjs`.
- Lege `docs/implementation/compiler-cli-color-output/STATUS.md` mit BASIS-SHA, aktuellem Stand und fälligem Gate an; Bericht kurz halten. Neue Berichte unter `implementation/COLOR<N>.md`, unabhängige Reports unter `reviews/`.
- **OpenCode/DeepSeek-Preflight jetzt durchführen**, bevor größere Änderungen beginnen: vorhandenen OpenCode-2.x-CLI-Pfad/Version/`run --help` verifizieren; bestehenden direkten DeepSeek-API-Zugang, effektiven Modellschlüssel `deepseek/deepseek-flash` und nachweisbare Zuordnung zu V4.1 Flash 1017 prüfen. Keine API-Schlüssel ausgeben, keine neuen Login-/Providerumgebungen erfinden. Kein Proxy/OpenRouter/Zen/Go-Fallback. Kurze, harmlose Bereitschaftsprobe unter der tatsächlich verwendeten Guard-/Worktree-Orchestrierung.

## 2. Phasenarbeiten

### COLOR1: Farbvertrag und echte Terminalfähigkeit (C1-01..C1-08)

- Ergänze `--color=auto|always|never` und `--color MODE` in **nur** den vorgesehenen Diagnosebefehlen; impliziter Default `auto`. `CommandSpec`, `DefaultCommandLineParser`, `DefaultCompilerCli` gezielt erweitern; doppelte/falsche Werte vor Host-IO ablehnen. Bestehende `--diagnostic-language`, `--json` und `--` dürfen nicht brechen.
- Implementiere reine `ColorPolicy` und `AnsiStyler` mit injizierbarer stderr-Terminalfähigkeit. Priorität aus Plan §2.2 beachten, besonders `NO_COLOR` (nichtleer), `TERM=dumb`, explizite Optionen und Maschinenoutputs. Nur SGR, Reset nach jedem Stilbereich.
- `CliEnvironment` muss für vorhandene programmatische Aufrufer konstruktor-kompatibel bleiben. Bei der effektiven Umgebungsübergabe darf die neue Capability **nicht verloren gehen**.
- In COLOR1 die Stilpolitik **minimal an die echte Textdiagnoseausgabe anschliessen**, sodass die `always`-/`never`-Prozesstests tatsächlich beweisbar sind. Ausführliche Quelltextframes folgen erst in COLOR2.
- Teste reine Policy-/Parserfälle und echte Prozesse, insbesondere **`2>errors.log` bei weiter interaktivem stdout** in `auto`; `System.console().isTerminal()` alleine ist kein zuverlässiger Nachweis für stderr. Unbekannte stderr-Terminalfähigkeit ergibt `auto` ohne Farbe. Keine Shell- oder native-access-Experimente mit untrusted Modelltext; neue Terminal-Abhängigkeit nur mit begründetem Bedarf.
- Bestehende CLI-Prozesstests und `pythonFreeCheck` ausführen; Quelländerungen mit RED/GREEN wo möglich nachweisen. Berichte, sauberer Kandidatencommit, dann autonomer **COLOR1-Review**.

### COLOR2: Farbige Textdiagnose + sicherer Quellkontext (C2-01..C2-11)

**Nur nach unabhängigem COLOR1-PASS.**

- Erweitere den bestehenden `ConsoleDiagnosticRenderer` um ein explizites, reines Renderoptionsprofil. Bestehende Overloads bleiben kompatibel und standardmässig farbfrei.
- Severity, Position, Code und Markierung in der verbindlichen zurückhaltenden Palette hervorheben, **ohne** DE/EN-Nachrichten, RelatedLocations, Hints oder kanonische Diagnosedaten zu verändern.
- Implementiere einen kleinen, bounded `DiagnosticSnippetRenderer` auf den **bereits übergebenen Quellen**. Bei falscher Revision oder fehlender Quelle keinen Ausschnitt erfinden; Range auf URI + UTF-16-Offsets belassen. Reine Darstellung, keine Dateisystem-/Repository-Leseoperation.
- Quelltext ist **untrusted**: ANSI/OSC/C0/C1/Bidi-Sequenzen vor dem Styling neutralisieren. Markerausrichtung bei Tabs, CRLF, Unicode-Surrogaten/Emoji, kombinierenden Zeichen und breiten Zeichen testen. Höchstens drei Kontextzeilen und 160 sichtbare Spalten, auch bei extremen Ranges/Zeilen. Bei nicht sicherer Visualbreite keine falsche Exaktheit behaupten.
- `--json` und `format`-/`diagram`-stdout, Dateien/Locks, SDK/LSP/Bridge dürfen **keine** ANSI-Sequenzen bekommen. Mit `--json --color=always` muss identische kanonische JSON-Ausgabe entstehen. Prüfe `stripAllowedSgr(alwaysText) == neverText`, ansonsten FAIL.
- Prüfe das **unveränderte** Office-Original als realen Regressionsfall inklusive tatsächlicher RelatedLocation. Keine Modellumbenennung, keine hartcodierte 279/272-Regel.
- Betroffene JUnit-/Prozess-/UTF-8-/Pythonfreiheitsgates ausführen; echter neuer Test mit manipulierten Steuerzeichen. Sauber committen und autonomen **COLOR2-Review** starten.

### COLOR3: Releaseintegration und kumulativer Abschluss (C3-01..C3-10)

**Nur nach unabhängigem COLOR2-PASS.**

- Prüfe aus einem frisch gebauten und entpackten Produkt die wirklichen `interlis-compiler`-Launcher ausserhalb des Arbeitscheckouts; keine IDE-/Testclasspath-Abkürzung.
- Integriere reale Farb-/JSON-/Redirecttests in die **bestehenden** CLI-Prozess- und Releasechecker. Verwende kein zweites allgemeines Testframework und keine grünen Mockberichte für echte TTY-Fälle.
- `README.md`, Hilfe und betroffene aktuelle CLI-Dokumentation aktualisieren. Alte Reviews und Normentscheidungen nicht redaktionell als neu „grün“ umdeuten.
- Führe den aktuellen vollständigen `./gradlew releaseCheckP3` (bzw. nach verifiziertem Taskinventar den gleichwertigen aktuellen Gate-Einstieg) am finalen SHA aus. Alle dazugehörigen Sprach-, Browser-/Safari-/Web-Image-, SDK-/LSP-, Conformance-, Pythonfreiheits-, Reproduzierbarkeits- und Plattformanforderungen bleiben Pflicht.
- Prüfe echte macOS-, Linux- und Windows-Prozesse beziehungsweise autorisierte **exakt SHA-verknüpfte** CI-Läufe. Fehlt ein erforderliches System/echte Safari-Automation, ist das `BLOCKED`, nicht `PASS`. Keine globale Änderung der Hostkonfiguration ohne Auftrag.
- Unabhängiger **COLOR3-Review** prüft C1+C2+C3 **kumulativ am finalen Produkt-SHA**. Erst dessen echtes PASS beendet den Auftrag.

## 3. Unverhandelbarer autonomer Reviewzyklus

Für **jede** Phase:

1. Lege vorab **BASE_SHA** und feste Kriterienliste fest. Schreibe Tests, Code und kurzen Implementierungsbericht. Führe alle fälligen Pflichtgates frisch aus, mit tatsächlich gemeldeten Exits/Skips/Resultaten.
2. `git diff --check`; committe Änderungen; hole anschliessend `CANDIDATE_SHA=$(git rev-parse HEAD)` in voller Länge. Der SHA darf **nicht** vor seinem Commit als Selbstreferenz in einen Produktbericht eingebaut werden.
3. Erzeuge einen **neuen** clean/detached Worktree exakt auf `CANDIDATE_SHA`, ein ignoriertes Reviewverzeichnis unter `build/review/compiler-cli-color/...` und eine `request.json` mit Phase, BASE/ASSIGNMENT/CANDIDATE SHA, Kriterien-IDs, Pfaden, fälligen Gates und Providerprofil. Keine Secrets und keine vorweggenommenen Resultate.
4. Starte OpenCode über die **bestehende eigene** 2.x-CLI mit *neuem* Sessionkontext und **direkter DeepSeek API** unter der bestehenden Python-Sperre. Verifiziere die Syntax auf der installierten Version. Typischer Aufruf:

   ```sh
   opencode run --standalone --model deepseek/deepseek-flash --format json \
     --file docs/implementation/compiler-cli-color-output/EXECUTION_PLAN.md \
     --file docs/implementation/compiler-cli-color-output/OPENCODE_REVIEW_PROMPT.md \
     --file /ABSOLUTER/PFAD/request.json \
     'Führe den unabhängigen COLOR-Review für den bezeichneten Kandidaten aus.'
   ```

   Der Start erfolgt aus dem **Reviewer-Worktree**. Benutze vorhandene Credentials; weder Schlüssel in ENV-Snapshots/Logs ausgeben noch ein anderes Modell einsetzen. Kein Wiederverwenden der Codex-Session oder einer bestehenden ungesperrten GUI-Session.
5. **Warte auf echtes Prozessende**, prüfe Prozess-/Transportstatus, äusseres Python-Sperrmanifest, effektives Modell-/Providerprofil, `review.md`, `verdict.json`, exakten HEAD, sauberen Worktree und fällige eigene Reviewer-Tests. Kein `grep PASS` im Zwischenlog.
6. Akzeptiere nur `PASS` mit sämtlichen Kriterien erfüllt und konkreten Nachweisen. Bei `FAIL`: Findings derselben Phase korrigieren, neuer Commit, frischer unabhängiger Review. Bei `BLOCKED`: technische Voraussetzungen selbständig beheben, sonst transparent stoppen; **nicht** selber PASS erteilen oder fällige Gates entfernen. Reine Transportstörung begründet höchstens zwei erneute saubere Versuche auf demselben unveränderten SHA, nicht ein Modellroulette.
7. Nach gültigem PASS **automatisch zur nächsten Phase**, ohne den Benutzer nach jeder Phase um Zustimmung zu bitten. Die Berichte bleiben unverändert, referenzieren den wirklichen Produkt-SHA. Nach einem neuen produktiven/testseitigen Commit ist der frühere Review nicht mehr für den geänderten Stand gültig.

## 4. Endausgabe von Agent A

Erst nach **COLOR3-Gesamt-PASS**:

- Basis- und finaler SHA, pro Phase der tatsächlich unabhängig geprüfte SHA und ein unveränderter Reportpfad;
- die drei unabhängigen `PASS`-Urteile samt Provider-/Modell-/Guardbeleg;
- tatsächliche Pflichtbefehle/CI-Links, Exits, Testzahlen und Skips;
- demonstrierter Terminaloutput (Auto/Always/Never), JSON-Bytevertrag, `NO_COLOR`, `2>file`, Office-Fall;
- zurückgebliebene ausdrücklich nicht blockierende Beobachtungen und konkrete nächste Schritte;
- kein Push, Merge oder Release ohne gesonderten Nutzerauftrag.

**Verbot:** Eine vorhandene G2-/P3-/CLI1–CLI3-Abnahme als Nachweis dieses neuen Farbfeatures ausgeben. Ein Reviewer-Prompt allein ist keine ausgeführte unabhängige Prüfung. Eine nicht durchgeführte Safari-/Plattform-/Terminalprüfung ist kein `PASS`.
