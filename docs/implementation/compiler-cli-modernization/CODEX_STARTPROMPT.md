# Codex-Startprompt – Eigenständiges INTERLIS Compiler CLI

Du bist **Agent A: Implementierung und Orchestrierung** im bestehenden Repository `edigonzales/interlis-java`. Dieser Prompt ist der ausdrückliche Auftrag, die **drei Phasen CLI1, CLI2 und CLI3 vollständig und nacheinander** umzusetzen. Du startest die unabhängigen Reviews selbst und arbeitest nach jedem belegten PASS autonom weiter. Es genügt nicht, Implementierung oder Reviewprompts vorzubereiten.

## 1. Verbindlicher Auftrag

Lies und befolge:

- `AGENTS.md` und gegebenenfalls zusätzliche Regeln der betroffenen Pfade;
- `docs/implementation/compiler-cli-modernization/EXECUTION_PLAN.md`;
- `docs/implementation/compiler-cli-modernization/OPENCODE_REVIEW_PROMPT.md`;
- aktuelle Status-/API-/Diagnose-/Buildverträge, insbesondere die betroffenen Teile von `docs/spec/03`, `06`, `09`, `13`, `14`, `16`, `18`; vor ihrer Bearbeitung `docs/spec/AGENTS.md`.

Die Nutzerentscheidungen stehen fest:

**Modul:** `interlis-cli` → `interlis-compiler-cli`.

**Programm:** `interlis-cli` → `interlis-compiler`.

**Kein ili2c-Bedienmodus, kein automatischer Legacy-Fallback, keine alten Launcher, Aliase, Wrapper oder Kompatibilitätsartefakte.** Es wurde noch nichts veröffentlicht; Rückwärtskompatibilität zu `interlis-cli` ist nicht erforderlich. Das Bibliotheksmodul `interlis-compiler` und das Repository `interlis-java` bleiben benannt wie bisher.

**Unverändert erhalten:** INTERLIS 2.3/2.4-Sprachverhalten, freigegebene Normprofile, gemeinsame externe Java-Conformance-Suite, referenzbasierte fachliche Tests, Compiler-/Workspace-/LSP-/Browserverträge, sichere Fehler-/Abbruchbehandlung, Pythonfreiheit. Der echte ili2c bleibt ein externer **Testreferenzcompiler**, nicht Teil der ausgelieferten Anwendung.

Nach der Bereinigung müssen normale CLI-Diagnosen zum konkreten Prüfauftrag gehören und verständliche deutsche/englische Texte statt technischer Nachrichtenschlüssel liefern. Das mitgelieferte Office-Original wird nicht verändert, sondern als Regressionsfall verwendet.

## 2. Arbeitsbeginn

1. Prüfe `pwd`, `git status --short`, `git rev-parse HEAD`, aktuellen Zweig und Arbeitsregeln. Der untersuchte Planstand ist `a996ef0a5e915eb76c3e96a98b8f445f7005e851`, **kein Reset-Ziel**. Erhalte und trenne fremde Änderungen. Verwende den vorhandenen Compiler-Wartungszweig oder einen passenden neuen lokalen Arbeitszweig; keine zurückgestellten P4-Arbeiten hineinmischen.
2. Erfasse die verfügbare JDK-/Gradle-/Node-/Browserumgebung und vorhandenen Tasks. Lies die konkreten Build-/Releasepreflights, bevor du Gates startest. Pins nicht ungefragt upgraden.
3. Lies den letzten gültigen G2-/P3-Status als Baseline. Nicht alle historischen Berichte neu inventarisieren. Schreibe eine kurze `docs/implementation/compiler-cli-modernization/STATUS.md` mit aktuellem Auftrag, Phase, Basis-SHA und nächster Aktion.
4. Prüfe die mitgelieferte Originaldatei `testdata/cli-modernization/original/SO_AGI_DataCatalog_Base_20260529.ili` gegen SHA-256 `fbb1bccc0e84795284f9fb1a3c0be9e6db516c3c4b8ae43ad2f856cf70948431`. Datei bytegenau erhalten; positive Kontrolle getrennt erstellen. Kein hartcodierter `/Users/stefan/...`-Pfad.
5. Führe das **OpenCode-Preflight aus Abschnitt 5 dieses Prompts jetzt** durch. Fehlender Zugriff auf einen unabhängigen Reviewer darf nicht erst am Ende als Überraschung auftreten.
6. Beginne CLI1. Keine separate lange Analyse-/Inventurphase und keine neue generische Prüfplattform.

Du darfst lokale Implementierung, Tests, erforderliche Worktrees, lokale Produkt-/Dokumentationscommits und direkte DeepSeek-Reviews im vereinbarten Umfang selbständig ausführen. Kein automatischer Push, Merge, Release, Zugriff auf Produktionsdaten oder Änderung globaler Zugangsdaten. Vorhandene autorisierte CI-Wege dürfen genutzt werden; eine fehlende Berechtigung ist ausdrücklich zu melden, nicht zu umgehen.

## 3. Phasen und verbindliche Prüfpunkte

### CLI1: Umbenennen und Legacy entfernen

**Kriterien:** CLI1-01 bis CLI1-09 im Plan.

- Verschiebe das Modul einschliesslich `main`, `test`, `evidenceTest`, `verification`, `verificationTest`. Passe `settings.gradle`, alle Gradle-Projektreferenzen, Runner, SDK-, Release-, CI- und aktuellen Dokumentationspfade an.
- Setze Programmnamen und Distributionsbasis explizit. Soll: `interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler`, im SDK weiterhin `cli/bin/interlis-compiler`. Windows entsprechend `.bat`. CLI-JAR heisst `interlis-compiler-cli-<version>.jar`; Compilerbibliotheks-JAR nicht umbenennen.
- `Main` verwendet nur die Standardanwendung. Entferne `Ili2cMain`, `Ili2cInvocationPlanner`, `LegacyCliProfile`, `LegacyExitCodePolicy`, `LegacyDiagnosticRenderer` nach Prüfung der tatsächlichen Aufrufer. Bereinige deren Parameter, Konstruktoren und Zweige in gemeinsamen Helfern.
- Kein nackter Dateiaufruf mit heimlicher Profilerkennung mehr. Fehlender/ungültiger Unterbefehl: klarer Bedienfehler, Exit 2, ohne Modell-/Netzwerkzugriff. Hilfe/Version und vorhandene Standardkommandos nach Plan erhalten.
- Entferne `ili2cStartScripts`, `ili2cJar`, `compat/ili2c.jar` und reine Legacy-Abnahmen. Keine Attrappen/No-op-Tasks unter alten Namen stehenlassen.
- Erstelle eine kurze Testdelta-Tabelle mit `REMOVE_LEGACY_ONLY`, `MIGRATE_STANDARD`, `KEEP_REFERENCE`, `RELOCATE_ONLY`. Nicht sämtliche Tests namens `Legacy...` löschen. Allgemeine Unicode-/Pfad-/IO-/Prozessfehlerprüfungen auf den neuen Aufruf übertragen.
- Sprachreferenz, normative Prüfungen und externe Suite bleiben. Nur deren **Aufruf des eigenen Compilers** ändert sich. Verhindere, dass der echte ili2c oder Testhilfen in der Produktdistribution landen.
- Führe das Phase-CLI1-Gate aus. Kurzer Bericht unter `implementation/CLI1.md`, dann sauberer lokaler Kandidatencommit und unabhängiger Review.

### CLI2: Auftragsbezogene, verständliche Diagnosen

**Erst nach unabhängigem CLI1-PASS. Kriterien:** CLI2-01 bis CLI2-11.

- Reproduziere den Unterschied zwischen vollständigem Analysebestand und ausgewähltem Compileauftrag mit eigenen minimalen Quellen. Nicht auf die bereits beobachtete Einzeldatei beschränken.
- Implementiere die gemeinsame Root-/Closure-Diagnoseauswahl gemäss Plan, möglichst als kleine interne `RequestDiagnosticSelector`-Funktion und explizite immutable `AnalysisSnapshot.requestDiagnostics()`-Sicht. Bestehende gleichwertige API verwenden, falls inzwischen vorhanden. Gate und CLI dürfen keine getrennten Selektionsregeln besitzen.
- Erhalte die volle `AnalysisSnapshot.diagnostics()`-Liste und die IDE-/Workspace-Sicht. Nimm Imports, Translation-/Overlayabhängigkeiten, fehlende Roots/Imports, Warnungen und relevante globale Diagnosen ohne Range ernst. Nicht bloss nach Rootdateiname oder ERROR filtern.
- Implementiere kleine reine DE-/EN-Textformatierung für den bestehenden Diagnosekatalog und die explizite Option `--diagnostic-language de|en`, Default EN. Kein globaler Localezustand, kein neues i18n-Framework, keine erneute Sprachprüfung im Renderer.
- Der Office-Fall erhält eine verständliche Namenskonfliktmeldung und die tatsächliche RelatedLocation der kollidierenden Deklaration. Keine feste Zeilennummer, keine Modelländerung, keine neue Normentscheidung.
- JSON erhält bei Bedarf additive Texte/RelatedLocations; kanonische Codes, Keys, Argumente, IDs, Ranges und fachliche Outcomes nicht durch Prosa ersetzen. Aktuelle Normprüfungen nicht grosszügiger machen, damit neue Ausgaben grün werden.
- stdout/stderr, Format-/Diagrammausgaben, UTF-8, Flush-/IO-Fehler und Abbrüche erhalten. Vollständige aktuelle Katalogabdeckung testen, nicht nur eine einzelne Meldung.
- Führe das Phase-CLI2-Gate und betroffene Portable-/LSP-/Bridgeverträge aus. Kurzer Bericht, neuer Kandidatencommit, unabhängiger Review.

### CLI3: Vollständige Produktabnahme

**Erst nach unabhängigem CLI2-PASS. Kriterien:** CLI3-01 bis CLI3-08 **und alle CLI1-/CLI2-Kriterien kumulativ**.

- Teste das tatsächlich neu gebaute und entpackte SDK ausserhalb des Checkouts. Keine alte Installation, kein Testclasspath, keine Aliasdateien.
- Prüfe angepasste Artefakt-/Releasechecker mit absichtlich fehlendem neuen Launcher, unerlaubtem Legacyartefakt, altem/fremdem oder unvollständigem Report in isolierten Testkopien. Keine blosse Anpassung von Dateizahlen.
- Führe das aktualisierte vollständige `releaseCheckP3` und alle nicht darin enthaltenen fälligen Referenz-/Archiv-/Semantik-/Reproduzierbarkeitsnachweise aus. Browser einschliesslich echtem Safari, Web Image, reale Modellclosures, Performancebudgets und SDK-/LSP-Verträge bleiben Pflicht.
- Sichere echte macOS-/Linux-/Windows-Nachweise für denselben finalen Produktcommit. Verwende bestehende berechtigte CI-Wege; fehlende Plattformausführung ist BLOCKED und kein erfolgreicher Skip.
- Abschlussdokumentation konsistent; historische Abnahmen nicht umschreiben. Der letzte unabhängige Review ist zugleich die einzige kumulative Gesamtabnahme dieses Auftrags.

## 4. Verbindlicher Commit-/Reviewzyklus

Für jede Phase:

1. Fachliche Fehlbehauptungen zuerst mit tatsächlichem RED reproduzieren, danach minimal korrigieren und GREEN prüfen. Bei reiner Umbenennung zählen klare Artefakt-/Aufrufvertragsprüfungen; keinen erfundenen historischen RED behaupten.
2. Während der Arbeit gezielt testen; vor Übergabe das vollständige fällige Phasengate. Notiere echte Kommandos, Exits, Testanzahlen, Failures/Errors/Skips und Artefakte.
3. Code, Tests und kurzen Implementierungsbericht committen. `BASE_SHA` bleibt die Basis der gesamten Phase, auch nach mehreren FAIL-/Rework-Runden. Produkt-SHA erst **nach** Commit auslesen, nicht selbstreferenziell in denselben Commit schreiben.
4. Erzeuge `request.json` unter ignorierten Buildausgaben. Es enthält `phase`, `baseSha`, `assignmentBaseSha`, `candidateSha`, absoluten Reviewworktree-/Ausgabeordner, Kriterien-IDs, Implementierungsbericht, Plan-/Reviewpromptpfad und fällige Gates. Keine PASS-Vorgaben oder Secrets.
5. Starte OpenCode nach Abschnitt 5 in einem neuen eigenen Worktree, frischem Kontext und begrenzter Schreibberechtigung für Build-/Reviewausgaben.
6. Warte auf echtes Prozessende. Prüfe Prozess-/Sperrstatus, tatsächlichen HEAD, Produktunverändertheit, Bericht und `verdict.json`. Kein Regex auf «PASS» in einem Zwischenlog als Freigabe.
7. Bei FAIL: begründete Findings in derselben Phase korrigieren, neu committen, frischen Review starten. Bei BLOCKED: autonom lösbare Voraussetzungen herstellen und sauber erneut versuchen; echte verbleibende Blocker konkret berichten.
8. Bei gültigem PASS: unveränderten Reviewbericht ablegen, SHA/Pfad/Hash im kurzen Status vermerken, **automatisch die nächste Phase beginnen**. Kein Benutzer-Checkpoint nach jeder erfolgreichen Phase.

Du darfst Reviewerberichte nicht umschreiben. Reine Ablage eines unveränderten Berichts braucht keinen künstlichen zweiten Code-Review. Jede fachliche Produkt-/Test-/Build-/Kriterienänderung nach PASS erzeugt einen neuen prüfpflichtigen Kandidaten.

## 5. Autarkes OpenCode-/DeepSeek-Review

### Preflight

- Ermittle den tatsächlich installierten **OpenCode-2-CLI-Pfad**, Version und `run --help`. Im Projekt wurde OpenCode 2.0.28 verwendet; die aktuelle lokale Version ist zu prüfen, nicht hart zu behaupten.
- Nutze bestehende GUI-/CLI-Credentials. Prüfe `auth list`, Modellliste und effektiven Provider **im verwendeten Projektprofil**. Ein fehlendes Environment-Secret ist kein Beweis fehlender Anmeldung. Keine Credentialdatei ausgeben/exportieren.
- Verbindlicher Provider/Modellschlüssel: `deepseek/deepseek-flash`, direkter Host `api.deepseek.com`, Zielmodell DeepSeek-V4.1-Flash-1017. Prüfe tatsächliche Zuordnung. Kein OpenRouter, Go/Zen, Proxy oder anderer Modellfallback.
- Prüfe die für autonomes Lesen, Builds, Testprozesse und Reviewausgaben erforderlichen projektbezogenen Berechtigungen. Kein pauschales Abschalten sämtlicher Sicherheitsgrenzen. Kein eigener Produktimplementierungsauftrag an den Reviewer.
- Führe eine kleine harmlose Bereitschaftsprüfung in der realen Ausführungskonfiguration aus. Erst dann viel Implementierungsarbeit beginnen. Eine Modell-Selbstauskunft ersetzt keine Provider-/Konfigurationsevidenz.

### Ausführung

Erzeuge einen neuen detached Worktree auf `CANDIDATE_SHA` und ein frisches Reviewverzeichnis. Nutze den privaten Standaloneweg der **tatsächlich vorhandenen** V2-Version, wenn unterstützt. Der typische, vorher gegen `--help` geprüfte Aufruf lautet:

```sh
opencode run --standalone \
  --model deepseek/deepseek-flash \
  --format json \
  --file docs/implementation/compiler-cli-modernization/EXECUTION_PLAN.md \
  --file docs/implementation/compiler-cli-modernization/OPENCODE_REVIEW_PROMPT.md \
  --file /absoluter/pfad/zum/review/request.json \
  "Führe die unabhängige Prüfung der beauftragten Phase durch."
```

Der vollständige Worktree-/Python-Guardablauf steht in Plan §9. Auf der bestehenden macOS-Umgebung den Agenten samt privatem Server und Kindprozessen mit `tools/verification/without-python.cjs` starten. Keine ungesperrte bestehende GUI-Session wiederverwenden und anschliessend Pythonfreiheit behaupten. Wenn deine Version einen expliziten Server braucht, verwalte einen eigenen unter derselben Sperre, prüfe Bereitschaft und warte auf die vollständige Session. Ändere keinen fremden laufenden Server.

**Keine Python-Aufrufe durch dich oder den Reviewer**, auch nicht für Dateianalyse, JSON, ZIP oder kleine Hilfsprogramme. Nutze vorhandene Java-/Gradle-/Node-/Shell-Werkzeuge. Die bereits autorisierten Negativproben des vorhandenen Sperrrunners bleiben die einzige entsprechende Ausnahme. Baue keine neue Agentenplattform; vorhandenen Startweg verwenden oder höchstens eine kleine lokale Shell-/Node-Orchestrierung für Wait/Exit/Validierung.

Keine `--continue`-/alte `--session`-Wiederverwendung aus der Implementierung. Keine laufenden Agenten als schon fertige Prüfung melden. Logs getrennt sichern, Secrets redigieren, Exit des tatsächlichen Review-/Guardprozesses auswerten. Bei Transportfehler maximal zwei saubere Wiederholungen desselben unveränderten Kandidaten versuchen; danach BLOCKED. Fachliche FAILs lösen dagegen begründete Korrekturen aus, keine blosse Wiederholung bis ein Modell PASS sagt.

### Abnahmeprüfung durch Codex

Du akzeptierst ausschliesslich einen vollständig beendeten unabhängigen Review mit konsistentem `review.md` und `verdict.json` nach Planschema. Prüfe:

- Phase und vollständige SHAs stimmen; Arbeitsbaum unverändert;
- Provider/Modell/API-Host und tatsächliche OpenCode-Version belegt;
- genau eines von PASS/FAIL/BLOCKED, kein «PASS ausser …»;
- alle Kriterien geprüft, Pflichtläufe tatsächlich ausgeführt oder zulässige CI-Evidenz für genau diesen SHA unabhängig ausgewertet;
- neue eigene Gegenprobe wirklich durchgeführt;
- keine offenen Muss-Findings/fehlenden Checks bei PASS;
- erfolgreiche Beendigung und abschliessendes Sperrmanifest; kein hängender Hintergrundprozess.

Ein technisch gestarteter Agent ist keine unabhängige Abnahme. Fehlen Zugang, passende Toolversion, Rechte oder Prüfplattformen nach angemessenen Versuchen, berichte den konkreten BLOCKED-Zustand. Nicht kommentarlos auf manuelle GUI-Prüfung ausweichen und nicht selbst die Rolle des Reviewers übernehmen.

## 6. Nicht verhandelbare Qualitätsregeln

Keine stillen XFAIL-/Norm-/Suitepinänderungen, keine deaktivierten Pflichtfälle und keine veralteten Reports als neuer Erfolg. Keine Kundenmodelle korrigieren, um Compilerfehler zu kaschieren. Die Office-Originalquelle ist ein Testinput, keine zu editierende Produktkonfiguration.

Die volle Analysesicht bleibt erhalten. Ein einzelner Dateiaufruf mit weniger Fehlermeldungen beweist keine allgemeine Closurekorrektheit. Warnings, Importfehler und globale Auftragsfehler dürfen nicht verschwinden. Verständliche Prosa ersetzt keine strukturierten Ursachen.

Die Registryabdeckung der Diagnostik wird getestet; Normbegründungen und konkrete Korrekturhinweise nur aus belegter Ursache. Kein neues allgemeines Compiler-/Evidence-/CLI-/i18n-Framework. Keine Änderung der öffentlichen Compilersemantik unter dem Etikett «CLI-Aufräumen».

Die vollständige Schlussabnahme bleibt verpflichtend. Dass CLI-Bedienparität ausdrücklich entfällt, berechtigt nicht zum Entfernen von Corpus-/Performance-/Browser-/SDK-/Sprachprüfungen. Reduziere Verwaltungsarbeit, nicht fachliche Qualität.

## 7. Abschlussausgabe

Nach CLI3-PASS liefere einen kurzen Bericht mit neuem Artefaktpfad, Beispielaufrufen, einem tatsächlichen verbesserten Diagnosebeispiel, den unveränderten Sprachverträgen, den drei Produkt-/Review-SHAs und den tatsächlich ausgeführten Gates/Plattformen. Nenne auch relevante bewusst nicht beauftragte Arbeit. Keine Behauptung einer Veröffentlichung oder eines Releases.

**Beginne jetzt mit CLI1 und setze den gesamten Auftrag bis zur kumulativen CLI3-Abnahme autonom fort. Stoppe nur bei tatsächlicher Gesamterledigung oder einem konkret nachgewiesenen, nicht selbständig auflösbaren Blocker.**
