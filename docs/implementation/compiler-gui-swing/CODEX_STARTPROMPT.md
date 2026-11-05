# Arbeitsauftrag für Codex

Implementiere im Repository `edigonzales/interlis-java` die in diesem Paket spezifizierte Swing-/FlatLaf-Compileroberfläche. Es ist ein Implementierungsauftrag für genau dieses Paket, nicht für alle im Repository vorhandenen Zukunftsspezifikationen.

## Verbindlicher Umfang

Drei zusammenhängende Arbeitsbereiche: **Check** mit intelligenter strukturierter Fehlerliste und schreibgeschützter Sourcepreview; **Dependencies** mit tatsächlichen Imports, Herkunft und Resolutiondetails als Baum/Listen; **Repositories** mit Suche, Versionen, Metadatenvergleich, Preview und exakter Rootübernahme.

Kein JavaFX, JFXPanel, WebView, Diagrammrenderer, UML-/Graphcanvas, Diagrammexport oder Format Preview. Auch kein Editor, Watch Mode, KI, semantischer Diff, Projektdateiformat, Native Image oder APE. Bestehende Diagramm-/Formatterprodukte anderer Konsumenten nicht entfernen.

## Lesen und Ausgangslage

Lies zuerst AGENTS.md und die für deine Änderungen geltenden Regeln. Lies anschliessend die Dateien in diesem Verzeichnis:

1. SPECIFICATION.md
2. ARCHITECTURE.md
3. EXECUTION_PLAN.md
4. TEST_AND_EVIDENCE_POLICY.md
5. OPENCODE_REVIEW_PROMPT.md und review-verdict.schema.json
6. SOURCE_BASELINE.md

Prüfe die aktuelle reale API und Buildstruktur, statt Quellennamen oder Befehle aus der Spezifikation ungeprüft als vorhanden anzunehmen. Neue Klassen und gui-*-Profile sind explizite Implementierungsvorschläge. SOURCE_BASELINE enthält den gelesenen Stand, nicht zwingend deinen aktuellen Checkout.

## Eigener Branch

Implementiere ausschliesslich in `codex/compiler-gui-swing`, vorzugsweise einem separaten Worktree von der geprüften main-Basis. Erfasse Basis-SHA. Fremde Dirty-Changes, andere Worktrees und PoC-Arbeiten erhalten. Kein Reset/Force-Recreate, kein automatischer Stash. Bei bereits existierendem passenden Branch kontrolliert fortsetzen, bei unpassender Kollision neuen eindeutigen Namen dokumentieren. Kein Push/Merge/Release/PR ohne zusätzlichen Auftrag.

## Arbeitsweise

Arbeite G0, G1, G2, G3, G4, G5, G6 strikt nacheinander. Implementiere pro Phase einen vollständigen vertikalen Abschnitt einschliesslich Fehlerpfad. Kein Fake-Compiler, keine fixen Erfolgsantworten, kein Sammeln grosser Folgephasenänderungen vor dem Gate.

Nach jeder Phase:

- notwendige gezielte Tests erfolgreich ausführen und Rohbelege erfassen;
- lokalen Kandidatencommit und unveränderliche Belegbindung herstellen;
- selbständig eine frische unabhängige OpenCode-2.x-Sitzung mit DeepSeek Flash 1017 über die direkte DeepSeek-API starten;
- Code und Zielerreichung sowie eigene Gegenproben prüfen lassen;
- regulären Prozessabschluss und strukturiertes Urteil validieren;
- Findings selbst beheben, betroffene Tests wiederholen und neuen Kandidaten erneut unabhängig reviewen.

Erst ein gültiges PASS erlaubt die nächste Phase. Kein manueller Startauftrag an den Nutzer, keine Nachfrage vor jeder Reviewrunde und kein Codex-Selbstreview als Ersatz. Einen vorhandenen funktionierenden Reviewstarter bevorzugen. OpenCode-2.x-Rechte prüfen; keine ungeprüfte V1-Konfiguration oder geratenes Modellalias.

Ist eine Pflichtvoraussetzung tatsächlich nicht verfügbar, Phase BLOCKED dokumentieren. Technische Routineprobleme eigenständig lösen, aber keine Sicherheitsgrenzen umgehen, fehlende Belege verschweigen oder nächste Phasen trotzdem beginnen.

## Architekturdisziplin

Nur ein GUI-Modul und überschaubare Pakete. Bestehende Compiler-/Workspace-/Repository-/Diagnoseverträge direkt verwenden. Kein CLI-Textparsing, keine Abhängigkeit von CLI oder LSP für den GUIbackendpfad, keine zweite Versions-/Import-/Cachepolicy. Rootauswahl bewahrt konkrete Version und Quelle. Resultate und Sourcepreview sind lauf-/revisionsgebunden.

Keine IO-/Compilerarbeit auf dem EDT. Workerlebenszyklus, Cancellation, late-result-Guards und eindeutiger Besitzer des Sitzungszustands sind Produktanforderungen, keine optionale Optimierung. Tabellen-/Baumauswahl anhand stabiler IDs; untrusted Texte sicher als Plain Text anzeigen.

Java25, JPMS und bestehender Jenesis-Projekteinstieg bleiben massgeblich. Kein Maven-/neuer Gradle-JVMbuild. GUIruntime früh mit jlink nachweisen, macOS-ARM64-App am Schluss mit jpackage. CLI-/LSP-Distributionen nicht mit GUIabhängigkeiten belasten.

## Tests und Abschluss

Nur Tests ausführen, die die konkrete Änderung sinnvoll absichern. Keine kompletten Korpus-/Benchmark-/Wasm-/Browserläufe ohne benanntes neues Risiko. Reviewer prüft vorhandene Originalbelege und ergänzt kleine eigene Gegenproben statt alle Suites nochmals zu starten. Reale Headful-Tests und finaler App-Start sind trotzdem verpflichtend; ViewModeltests sind kein Ersatz.

Pflege STATUS.md und die erforderlichen zentralen Statusverweise. Klassen/Tests/Fixtures fachlich benennen, nicht nach Phasennummern. Aufräumen nur im Auftragsscope; keine fremden historischen Spezifikationen löschen.

Abschluss erst bei GUI-01..52 erfüllt und gültigem G6-PASS. Berichte Branch, Basis-/Produkt-SHA, implementierte Funktionen, ausgeführte und wiederverwendete Tests, reale Plattform, App-Pfad, tatsächliche Build-/Startbefehle, Modell-/Providerbindung und Reviewpfade. Ungeprüfte Plattformen und fehlende Signierung/Notarisierung klar benennen. Keine erfundenen Erfolgsnachweise.
