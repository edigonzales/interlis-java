# Phasenplan — F1 und F2 gemeinsam, mit getrennten Gates

## Ablaufregel

F0 → F1a → F1b → F1c → F2a → F2b → F3 → Kandidatencommit → R.

Die nächste abhängige Phase beginnt erst nach dem bestandenen Gate. Kein unabhängiger Agentenreview zwischen Implementierungsphasen. Technischer Reviewpreflight früh erlaubt. F1c/F2b sind sinnvoll getrennte Zwischencommits; keine Benutzerfreigabe nötig. Bei echter Blockade unabhängige Arbeit erlauben, aber kein übersprungenes Gate als bestanden melden.

## F0 — Baseline, Stilfälle, Verbraucher und Preflight

Erfasse aktuellen BASE_SHA, Worktreezustand, aktive Regeln, Jenesis/JDK-/Modulpins und Compilerformatversion. Verifiziere die vorhandenen Kommandos aus README statt alte Gradletasks zu kopieren. Inventar von Formatteraufrufen, Tests, API-Record-Patterns, Host-/Bridgeverbrauchern, Formatversionsquelle, Fixture-/Contractabhängigkeiten.

Beschaffe den ausgewählten gepinnten SO-Korpus ausdrücklich. Hashes, Lizenzen/Herkunft, Syntaxbaseline und alte Outputs erfassen. Kleine vollständige Fixtures/Goldens für alle Regeln entwerfen. Die fünf Constraintfamilien auf tatsächliche Syntaxfelder/ranges abbilden; UNIQUE WHERE und SET-Varianten explizit prüfen.

Vorhandenen OpenCode-2.x-Reviewstarter, Modellbindung und sichere Arbeitsweise technisch prüfen. Kein fachlicher Review starten. Fehlender Zugang ist ein späterer Reviewblocker, keine Erlaubnis zum Modellwechsel; unabhängige lokale Implementierung darf fortgesetzt werden.

**Gate:** Baseline- und Callsiteinventar; unveränderte Originalhashes; bestehende gezielte Formattertests als Referenz; Parserprobe aller positiven Goldeninputs; dokumentierte negative Ausgangsfälle; Konkretion des Sollstils. Noch keine absichtlich fehlschlagende neue Stiltestsuite als vorhandenen Produktfehler ausgeben.

**Kriterien:** FF-01..03,06,38 vorbereiten.

## F1a — Quellmodell, Schutz und Layoutfundament

Additive FormatOptions-Breite mit Dreierkonstruktor und Validierung. Quellindex, Trivia-/Metadatenanker, kleiner LayoutDoc/Printer und EOL-/Ownershipmodell implementieren. Noch keine zweite Engine einführen. Bestehende Implementierung schrittweise intern ersetzen; API bleibt.

Verifier mit echtem Reparse, Token-/Metawert-/Bindungsschutz und internem Fehlerpfad. Tests dürfen einen bewusst manipulierten Zwischenplan/Output einschleusen, um Verifierablehnung nachzuweisen; keine solche Schnittstelle öffentlich exportieren.

**Gate:** APIkompatibilität, Breitenlimits, LF/CRLF/CR/mixed, Erklärung/Metawertschutz, Token-/Ankermutationen, Cancellation, mehrere parallele Aufrufe. Reale modulare Kompilierung und bestehende relevante Tests. Keine Backend-/GUIkampagne.

**Kriterien:** FF-04..06,12..15,24..27.

## F1b — Kommentar- und Deklarationslayout

Standalone Block-/Doc-Kommentare verschieben, Inline-/nested-/trailing-Fälle behandeln, `!!` und `!!@` unterscheiden. Vollständige Block-/Abschnittseinrückung samt CONSTRAINTS OF, Leerzeilen vor Bündeln und Tokenabstände umsetzen. Domain-/Enumeration-/Funktionskopf-/Kardinalitätsfälle nicht durch globale Zeichenregeln behandeln.

Geschützte Ausnahmen mit engstem Bereich, Grund und Notice; keine pauschale Rückkehr zu „alle mehrzeiligen Kommentare unverändert“. Bestehende Parser-/Metadatazuordnung verwenden.

**Gate:** T01..T14,T23,T25,T26 plus F1-relevante ausgewählte Referenzdifs. Lexerfolge/Metaanker/Idempotenz eigenständig prüfen. Einrückungsänderung darf ASCII-Tabellen und Erklärungswerte nicht verändern. Vorhandene F1-Verträge vollständig grün.

**Kriterien:** FF-07..14,24..27.

## F1c — Fertiger allgemeiner Formatter und erster Zwischencommit

F1 auf dem kompletten ausgewählten Korpus prüfen; verbleibende Constraintlayouts in dieser Zwischenphase ausdrücklich als noch F2-offen klassifizieren, nicht als fertiges Gesamtziel. CLI-/LSP-Optionsintegration so weit anbinden, dass F1 über beide Hosts testbar ist. Additive CLIbreite und LSPdecodierung, Entfernung des redundanten Format-Action-Edits, bestehende Schreib-/readonly-/Cancellationverträge.

Normale Kernformatierfälle über API, echten modularen CLI-Launcher und LSP-Prozess bei identischen Optionen vergleichen. Keine Vorgabe, jetzt sämtliche sprachlichen Compilerfälle neu auszuführen.

**Gate:** F1-Sicherheits-/Stilfälle, echter kleiner CLI- und LSP-Formatierlauf, drei-Argument-APIconsumer, kein veralteter Default-Code-Action-Pfad. F1-Zwischencommit mit Herkunft der vorab ausgeführten Tests.

**Kriterien:** F1-Anteil FF-01..15,24..34,37..38. F2-Pflichtfamilien bleiben sichtbar offen.

## F2a — Ausdruckslayout sowie MANDATORY und Plausibilität

ExpressionLayout für alle vorhandenen akzeptierten Ausdrucksarten, originale Operator-/Klammerstruktur, argumentweise Calls und Kommentare. Flat-/Breakmechanismus ohne exponentielle Suche. MANDATORY und Plausibilitäts-CONSTRAINT vollständig; Prozentlexeme exakt. Langtextatome bewusst überlaufen lassen.

**Gate:** T15,T16,T21..T28; Breitenknappheitsfälle, verschachtelte Calls, Pfade, mixed operators und Kommentare. F1-Kommentar-/Wertschutz gegen neue gemeinsame Layoutänderungen erneut im notwendigen Umfang prüfen. Keine automatische gesamte Compilersuite.

**Kriterien:** FF-15..17,21..27.

## F2b — EXISTENCE, UNIQUE, SET und zweiter Zwischencommit

Alle verbleibenden Familien und Varianten nach CONSTRAINT_LAYOUT. UNIQUE WHERE-/LOCAL-Doppelpunkte und beide SET-Kopfreihenfolgen explizit. Keine Umformung in vermeintlich einfachere gleichwertige Klauseln. Familiencoverage mit den tatsächlichen Grammatikalternativen abgleichen.

**Gate:** T17..T22 plus vollständige kleine Constraintgoldens, zugehörige Metadatenfälle und Idempotenz. Gepinnte Waldplan-/Naturgefahrenreferenzen mit tatsächlichen Constraints vollständig. F2-Zwischencommit. Noch keine vollständige Release-/Plattformbehauptung.

**Kriterien:** FF-16..25,36 vorbereiten.

## F3 — Gesamtabnahme, Verbraucher, Bereinigung und Kandidat

Aktuellen gesamten ausgewählten Formatterkorpus mit neuen Produktbytes ausführen bzw. nachweislich unveränderte gültige Phasenbelege zusammenführen. Finale Diffs klassifizieren, alle unerklärten Inhalts-/Bindungsänderungen beseitigen. Semantische Stichproben mit identischer lokaler Fixtureclosure. API-/CLI-/LSP-Konsistenz, tatsächliche JPMS-Ausführung und gegebenenfalls kleine betroffene portable Verbraucher nach HOST_INTEGRATION.

Alle alten Formattermethoden/Tests/Notices/Docs/Selektoren auf aktive Restverweise untersuchen. Keinen parallelen Legacyweg behalten. Aktive Spezifikation und Formatversionsquelle aktualisieren; alte Rohbelege nicht verändern. Fixtureinputinvalidierung durch reale Mutation nachweisen. Gesamtziel FF-01..FF-38 anhand Belegindex bewerten.

**Gate:** Gewünschte Stiloutputs und Sicherheitsinvarianten vollständig; alle Pflichtfamilien; echte Host-/Modulintegration; alle geänderten Dateien gehören zum Scope; `git diff --check`; keine Secrets/grossen Rohartefakte/fremden Änderungen staged. Kandidaten lokal committen, CANDIDATE_SHA/TREE_SHA festhalten.

Falls nach dem Commit eine Prüfung einen sauberen Commit braucht, diese ehrlich neu ausführen. Vorher erzeugte Belege nicht mit der neuen SHA umetikettieren. Kein zusätzlicher Commit nur um das externe Reviewurteil in denselben zu prüfenden Commit einzubauen.

## R — Ein unabhängiger Gesamt-Review nach allen Phasen

OpenCode2.x/DeepSeek Flash1017 frisch, autonom, schreibgeschützte versionierte Kandidatenquellen, beschreibbare Scratch-/Buildoutputs. Vollständigen kumulativen Diff ab BASE_SHA und alle Anforderungen prüfen, nicht nur den letzten Fixcommit. Reviewer liest echte Rohbelege und erstellt eigene kleine Gegenproben.

Bei FAIL → Codex korrigiert → gezielte Retests → neuer Kandidatencommit → neuer frischer Gesamt-Review. Bei BLOCKED keine andere Modellversion und kein selbstgeschriebenes PASS. Umfang/Blocker offen dokumentieren. Notwendige Nachweise dürfen aus mehreren identischen relevanten Produktständen zusammengesetzt werden; keine pauschalen doppelten Korpus-/Benchmarkläufe.

**Abschlussgate:** FF-01..FF-40 erfüllt und unabhängiges gültiges PASS. Workflow allein ist kein erfolgreicher Review. Finales Urteil bleibt extern mit Kandidat, Modell und Belegen gebunden. Kein Push/Merge/Release.

## Auswahl häufiger Testkommandos

Diese Beispiele am tatsächlichen Checkout überprüfen; keine erfundenen existierenden Testnamen verwenden:

```sh
java build/InterlisJvmBuild.java build --module interlis-compiler --tests '.*Formatter.*Test'
java build/InterlisJvmBuild.java build --module interlis-compiler-cli --tests '.*Format.*Test'
java build/InterlisJvmBuild.java build --module interlis-lsp --tests '.*Format.*Test'
```

Liegt ein notwendiger Test in einer anders benannten bestehenden Klasse, Auswahl entsprechend erweitern oder gezielt ausführen. Prüfe die tatsächliche Zahl entdeckter und ausgeführter Fälle. Eigene notwendige Korpusoperation vorzugsweise in vorhandene Verification-Aufrufmuster einfügen; einen neuen Namen erst nach Implementierung als ausführbar dokumentieren. Kein Buildshortcut über alte Gradle-JVM-Tasks.
