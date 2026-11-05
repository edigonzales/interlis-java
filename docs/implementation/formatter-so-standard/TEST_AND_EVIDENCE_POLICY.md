# Testökonomie und belastbare Nachweise

## 1. Grundsatz

Wähle den kleinsten Testumfang, der die tatsächlich geänderten Verträge und Verbraucher aussagekräftig prüft. Neuer Commit, neue Phase, neue Reviewrunde oder anderer Dokumentationsstand allein rechtfertigt keinen Vollauf. Ein Vergleichs-/Verifierdefekt kann dagegen mehrere frühere Resultate sachlich entwerten; dann die betroffenen Fälle gezielt neu prüfen.

Erstmalige Gesamtabnahme: vollständiger ausgewählter Formatterkorpus, alle Constraint-Familien, passende CLI-/LSP-Prozesse und echte JPMS-APIprobe. Nicht automatisch sämtliche übrigen Compiler-, Resolver-, Diagramm-, Backend-, GUI-, Browser-, Performance- und Releaseprüfungen.

## 2. Retestmatrix

| Änderung nach Review | Minimal erforderlicher Retest |
|---|---|
| Rechtschreibung, Links, Abschlussbericht | Dokument-/Link-/Schema-/Scopecheck; kein Formatterlauf ohne produktiven Einfluss |
| Reviewstarter/Urteilsparser | Eigene Prozess-/Fehler-/Kandidatenbindungstests; neue unabhängige Sitzung, keine pauschalen Produktsuiten |
| Ein Constraint-Familienlayout | Goldens/Fehlerfälle dieser Familie, gemeinsame Grenzfälle, passende Korpusdateien und Idempotenz |
| Gemeinsames ExpressionLayout oder Breitenentscheidung | Alle betroffenen Constraint-Goldens und kleiner kompletter Formatterkorpus |
| Kommentar-/Metadatenschutz, Emissionsbesitz oder EOL | Gesamter kleine Formatter-Sicherheitskorpus; gezielte CLI-/LSP-Verbraucher bei geänderten Outputs |
| FormatVerifier oder Testoracle | Mutationstests plus alle Belege neu validieren, deren Aussage von der fehlerhaften Prüfung abhing; wenn alte Originalbytes zugänglich sind, Revalidierung ohne unnötigen Produktneubau möglich |
| CLI-Optionsparser/Writer | CLI-Option-/Dateisicherheitsfälle und ein realer Prozess; Kerngoldens nicht pauschal wiederholen |
| LSP-Adapter/Optionsdecodierung | LSP-Request-/stale-/readonly-/Cancellationfälle, ein echter Prozess; kein voller VSIX-Regressionstest ohne Extensionänderung |
| FormatOptions/API oder Moduldescriptor | Betroffene Consumer neu kompilieren, echte Modulprobe, betroffene Hostintegration; keine Hintertür über Classpath |
| Corpusmanifest/Fingerprint | Hash-/Mutation-/Incrementaltests und betroffene Fixtures; kein Löschen alter Fehlerbelege |
| Portable erreichbare Implementierung | Frische betroffene Backendartefakte mit kleinen Runtimefällen; nicht aus JVM-PASS ableiten |

Pro Korrektur kurz protokollieren: Finding, geänderte Inputs, betroffene Verträge/Verbraucher, neue Tests und wiederverwendete Belege samt Identitätsgrund. Nicht jede Testrunde als kompletter neuer Abschluss ausgeben.

## 3. Inkrementelle Testinputs

Formattertests hängen auch von `.ili`-Fixtures, Goldens, Manifesten und gegebenenfalls Stilkonstanten ab. Neue Inputpfade in bestehende `build.jenesis/test-inputs.properties` oder die tatsächlich aktive Inputbindung aufnehmen. Beweise mit einer kleinen Mutation, dass ein geändertes Fixture nicht durch einen alten grünen Report verdeckt wird. Kein pauschales `--rerun-all` als dauerhafte Reparatur.

Ein Testbericht gehört zum tatsächlichen Quell-/Fixture-/Options-/Toolchainstand. Bei Wiederverwendung Gleichheit relevanter Inputs und Artefakte nachweisen. Ein Dokumentationscommit kann dieselben Produktbytes besitzen; er macht alte Ausführungen nicht rückwirkend zu Läufen auf seinem Commit. SHAs/Dirtyangaben alter Reports nicht umschreiben.

## 4. Nachweisstruktur

Versioniert nur schlanke Status-/Entscheidungsdateien. Rohoutputs in einem neuen, explizit gewählten Evidenceverzeichnis, ausserhalb von clean-gefährdeten Outputs für finale Belege. Kein bestehender Run überschreiben.

```text
<evidence-root>/formatter/<candidate-or-run-id>/
  environment.json
  commands.jsonl
  junit-originals/
  corpus-inputs.json
  corpus-report.json
  corpus-output/
  diffs/
  cli/
  lsp/
  api-module/
  evidence-index.json
```

Originalstreams, JUnitreports und tatsächliche Exitcodes/Signals/Timeouts unverändert erhalten. Evidenceindex bindet Pfade, Hashes, Inputfingerprints, Testauswahl, Optionen und Umfang. Keine Secrets, personenbezogenen fremden Files oder globalen Authdateien kopieren.

Zulässige Status trennen: PASSED, FAILED, BLOCKED, NOT_RUN, NOT_REQUIRED, REUSED. `SKIPPED`/abgebrochen/keine Testentdeckung ist niemals PASSED. Ein fehlender nicht erforderlicher Release-/Windows-/Wasm-Lauf ist nicht automatisch ein Blocker des lokalen Formatterauftrags; eine erforderliche betroffene Verbraucherprüfung bleibt dagegen offen.

## 5. Prozess- und Abbruchregeln

Vorhandenen sicheren Recorder wiederverwenden, nicht ein zweites allgemeines Framework bauen. Kommandos als Argumentlisten, begrenzte Laufzeiten und Originalargv ohne Secrets. Laufzeitlimits am tatsächlichen Testzweck orientieren; kein 60s-Outerwrapper um einen absichtlich längeren Kindtest. Bei Abbruch eigene Kinder gezielt beenden, nicht globale Java-/Nodeprozesse töten. Ein äusserer Timeout darf nicht durch später gefundenen Text „PASS“ überschrieben werden.

Fuzz-/Stressproben begrenzt und reproduzierbar. Keine wiederholten identischen Läufe ohne Hypothese, bis zufällig grün erscheint. Nach einem Fehler Ursache klären und nur die Gegenfrage erneut ausführen.

## 6. Corpus-/Semantikorakel

Testwerkzeug und Produktverifier dürfen gemeinsame SourceText-/Parser-APIs verwenden, aber nicht dasselbe einzige Vergleichsergebnis gegenseitig bestätigen. Goldens, unabhängige Testfingerprints, bewusste Mutationen und semantische Stichproben bieten unterschiedliche Kontrollen.

Ein Tokenvergleich darf Metaattribute nicht pauschal wegfiltern. Ein Kommentarvergleich darf nur nachgewiesene Randeinrückung/EOL normalisieren. Ein semantischer Vergleich darf neue Fehler, geänderte Warnungen oder andere Werte nicht als „nur neue Position“ löschen. Normalisierungsregeln benennen und gegen adversariale Abweichungen testen.

## 7. Bestehende Evidenz-/Contractverträge

Vor Tests/Methodenverschiebungen nach aktiven Referenzen suchen. Ein referenzierter Test kann zusätzliche Contractselektoren haben. Aktualisiere sie gezielt und erhalte die Testfrage; keine Zahl künstlich konstant halten, keine historische G2-Kontrolllogik neu einführen.

Falls aktuelle Contractprüfungen von geänderten aktiven Spezifikationshashes abhängen, die Quelle nach den bestehenden Regeln neu binden und genau diesen kleinen Consumer-/Produzentenvertrag ausführen. Alte B-Reports und frühere Formattergoldens bleiben historische Ergebnisse mit ihrem damaligen Kontext.

## 8. Unabhängigkeit ohne doppelte Volltests

Codex führt die nötigen Produktläufe aus. OpenCode liest die ursprünglichen Belege und führt eigene kleine Gegenproben aus. Ein unabhängig geschriebenes Metawert-/Kommentar-/Constraintgegenbeispiel ist wertvoller als derselbe grosse Lauf zum zweiten Mal. Der Reviewer darf bei konkretem Zweifel breiter testen und muss sagen, welche offene Frage das beantwortet.

Nach einem Reviewfix erneut ein frischer Gesamt-Review des neuen Kandidaten, aber keine automatische Wiederholung aller Originaltests. Gesamt-PASS braucht vollständige weiterhin gültige Evidenz, nicht zwangsläufig einen einzigen monolithischen Testlauf.
