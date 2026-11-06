# Codex-Auftrag: vollständiger INTERLIS-Formatter im Solothurner Stil

Du bist der implementierende Primäragent im lokalen Repository `edigonzales/interlis-java`. Setze den vollständigen Auftrag dieses Verzeichnisses um. Liefere nicht nur einen weiteren Plan.

## Ziel

Entwickle den bisherigen zeilenorientierten Formatter zu einem quelltreuen, strukturierten Formatter für INTERLIS 2.3 und 2.4 weiter. Orientierung sind die gepflegten kantonalen Modelle aus `sogis/sogis-interlis-repository`. Besonders wichtig sind identisch konservativ behandelte `/* ... */`-/`/** ... */`-Blöcke, gewöhnliche `!!`-Kommentare, genaue `!!@`-Metawerte, richtige Leerzeilen und ein vollständiges, syntaxbasiertes Constraint-Layout.

Ein Auftrag enthält F1 (allgemeine Formatierung) und F2 (Constraints). F0 bereitet die verbindlichen Fixtures vor; F3 integriert und nimmt ab. Jede Phase muss ihr notwendiges Gate bestehen, bevor die nächste abhängige Phase beginnt. Kein fachlicher Zweitagentenreview nach einzelnen Phasen. Erst nach allen Phasen, gezielten Abschlussprüfungen und lokalem Kandidatencommit folgt der unabhängige Gesamt-Review.

## Verbindlich lesen

Lies die geltenden `AGENTS.md`-Dateien sowie alle Dokumente dieses Pakets. Aktuelle Verträge stehen in `docs/development/build.md`, `docs/development/testing.md`, `docs/contracts/sources-and-syntax.md`, `docs/contracts/diagnostics.md`, `docs/contracts/formatting.md`, `docs/contracts/requirements.md`, `docs/usage/cli.md` und `docs/usage/sdk.md`. Lies weitere Verträge nur soweit betroffen. Dokumentationspfade am aktuellen Checkout verifizieren.

Aktuelle ausdrückliche Entscheidungen dieses Folgeauftrags ersetzen alte Vorgaben zur bloss zeilenerhaltenden Formatierung und deren alten Sollstil. Sicherheits-, Sprach-, Build-, Integritäts- und Evidenzpflichten werden nicht aufgehoben. Aktualisiere widersprechende aktive Formatterverträge; historische Rohbelege nicht umschreiben.

## Ausgangslage und Parallelität

Auftrag B ist auf `origin/main`. Der bei der Spezifikation gelesene Referenzcommit ist `c02c9112f93cd69c113faf52d6267f601433e0fd`. Dies ist kein Reset-/Checkout-Zwang. Vor Änderungen tatsächlichen Branch, BASE_SHA, staged/unstaged/untracked Dateien sowie wirksame Toolchain feststellen.

Bevorzuge einen eigenen Branch `codex/formatter-so-standard` und eigenen Worktree. Keine fremden Änderungen staschen, verwerfen, committen oder formatieren. Kein ungeprüftes `git add -A`, kein `git reset --hard`, kein pauschales `git clean`. Root-`build/` enthält Jenesisquellen und darf nicht gelöscht werden.

Das GUI kann parallel entstehen. Keine GUI-Klassen, Swing-/JavaFX-/FlatLaf-Dependencies, Diagrammviewer oder GUI-Packaging implementieren. Keine APE-/Native-/Cosmo-Aufgaben starten. Keine Buildsystemmigration, kein Jenesis-/JDK-/ANTLR-/Node-Upgrade. Die bestehende modulare Architektur erhalten.

## Technische Leitplanken

- Ein reiner Formatterkern im bestehenden Compiler-Modul; kein Dateisystem, Netzwerk, Compilerprozess oder Repositoryauflösung im Formatieraufruf.
- Öffentliche Signaturen von `ModelFormatter`, `ModelFormatters` und `FormatOutcome` erhalten. `FormatOptions` kontrolliert um `maxLineWidth` erweitern; alter Dreierkonstruktor und Accessoren bleiben vorhanden. Alte Record-Pattern-Verbraucher tatsächlich suchen, nicht volle Binär-/Quellkompatibilität behaupten.
- Intern darf `DefaultModelFormatter` ersetzt werden. Kein Legacyengine-Schalter, keine duplizierte Ausdrucksgrammatik, kein pauschales Regex-Formatieren.
- Syntax, Originaltokens und Trivia gemeinsam verwenden. Kein Umordnen von Deklarationen, Imports, Metadaten, Argumenten oder Constraints. Keine Klammerentfernung, logische Vereinfachung oder Änderung von Zahlen-/Stringlexemen.
- Alle fünf Constraint-Familien samt Varianten abdecken. `CONSTRAINTS OF` als Strukturblock mitbehandeln. Der Quellcode schreibt Plausibilitätsbedingungen als `CONSTRAINT ... >= ...% ...`, nicht mit einem erfundenen `PLAUSIBILITY`-Schlüsselwort.
- Ein Reparse und strikte Quelltreueprüfung vor `Formatted` sind verbindlich. Idempotenz wird in Tests geprüft, nicht durch beliebiges wiederholtes Formatieren bis zur Stabilität produziert.
- CLI-Dateischreiben bleibt revisions-/inhaltsgeprüft und atomar je Datei. LSP liefert keine Edits für schreibgeschützte oder inzwischen unpassende Dokumente.
- Entferne den redundanten LSP-Format-Code-Action-Edit. Standardformatierung mit Requestoptionen bleibt und wird gezielt getestet. Keine letzte-Request-Optionen global cachen.
- Erhalte Testschutz, aber aktualisiere absichtlich veraltete Stil-Goldens mit begründeten Diffs. Niemals Metadaten-/Semantikveränderungen durch neue Goldens legitimieren.

## Ablauf

F0 → F1a → F1b → F1c → F2a → F2b → F3 → Kandidatencommit → R.

Die Details und Testgates stehen in EXECUTION_PLAN.md. F1c ist der fertig getestete F1-Zwischenstand, F2b der fertig getestete Constraint-Zwischenstand. Beide sind Zwischencommits, keine Nutzerfreigabe-/Wartepunkte. Arbeite selbständig bis zum Ende. Technischen OpenCode-Preflight schon in F0 durchführen.

Nur notwendige Tests: zuerst betroffene Klassen, dann schmale reale Verbraucherprüfungen. Ein neuer Commit, eine neue Phase oder Reviewrunde rechtfertigt keinen Vollauf. Nach Änderungen an gemeinsamen Layout-/Schutzregeln kann dagegen das komplette kleine Formatterkorpus erforderlich sein. Breite gesamte Compiler-, Wasm-, Browser-, Performance- und Releasekampagnen nicht schematisch starten. Tatsächlich betroffene portable Verbraucher nach HOST_INTEGRATION prüfen.

## Unabhängige Prüfung

Nach F3 geprüften Stand lokal committen. Eine frische OpenCode-2.x-Sitzung mit DeepSeek Flash 1017 über die direkte DeepSeek-API startet in einer isolierten Arbeitskopie des exakten Kandidaten. Keine andere Modellversion oder Providerfallbacks; technische Zuordnung und echte Antwortmetadaten prüfen. Kein Codex-Subagent als Ersatz.

Reviewer erhält den ganzen Auftrag, kumulativen Diff, BASE_SHA/CANDIDATE_SHA/TREE_SHA, Korpuspin, Goldens, Rohlogs und Belegindex. Er prüft Code UND Zielerreichung und führt eigene kleine Gegenproben aus. Er repariert keine Produktdateien. Details und Urteilsschema in OPENCODE_REVIEW_PROMPT.md.

Bei berechtigten Findings: selbst korrigieren, den kleinsten ausreichenden Retest begründen/ausführen, neuen Kandidaten committen und frisch den gesamten Auftrag reviewen lassen. Unveränderte breite Belege nicht automatisch wiederholen. Altes Review-PASS gilt nicht für neue Produktänderungen. Bei echten Blockern ehrlichen Teilstand liefern, kein PASS simulieren.

## Abschluss

Nenne konkrete Änderungen, Kriterien FF-01 bis FF-40, Phasengates, alte/neue APIbeispiele, Korpusresultate und Diffs, tatsächliche Tests/Skips, gezielte Wiederverwendung, finalen Commit, Modell-/Providerbindung, unabhängiges Urteil und Belegpfade. Keine vollständige Releasefreigabe behaupten.

Kein Push, Merge, Tag, Release, Modellrepository-Commit oder Kontakt an Dritte. Beginne mit F0 und führe den Auftrag aus.
