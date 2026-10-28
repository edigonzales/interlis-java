# `interlis-java`: G2-Abschluss mit schlanken Tests und ohne Python

**Verbindlicher Umsetzungsvorschlag für Codex; unabhängige Prüfung mit OpenCode / DeepSeek Flash 1017**  
**Fassung:** 2.0, 28. Oktober 2026  
**Untersuchter Repository-Stand:** `edigonzales/interlis-java`, `main`, Commit `29f079d558ac8acca411f9f2975e85d537672b10`  
**Status:** Neuer Ausführungsauftrag zur Übernahme ins Repository; die beschriebenen Änderungen wurden noch **nicht** implementiert oder getestet.

> **Leitsatz:** Nicht mehr die Verwaltung von Nachweisen optimieren, sondern die Zahl unabhängig bewiesener, korrekt implementierter INTERLIS-Sprachregeln erhöhen. Bestehende Beweise nicht wegwerfen. Die aktive Python-Abhängigkeit vollständig beseitigen, ohne das bewährte Testverhalten neu zu erfinden.

## 1. Auftrag, Grenzen und Rangfolge

### 1.1 Zwei gleichrangige Muss-Ziele

1. Den Compiler für **INTERLIS 2.3 und 2.4** fachlich korrekt und vollständig bis **G2 / WP-20–27** abschliessen. Eine angenommene Quelle ist nur dann korrekt kompiliert, wenn das resultierende öffentliche Schema semantisch stimmt; ein grüner Parser oder Exitcode allein reicht nicht.
2. **Sämtlichen getrackten aktiven Python-Code aus `interlis-java` entfernen** und Build, Tests, Konformität, Referenzprüfungen, Benchmarks, SDK-Prüfungen, Evidenzprüfung und die notwendigen Reviewabläufe ohne Python betreiben. Dies ist ein hartes frühes Phasengate und ein dauerhaftes Qualitätskriterium.

### 1.2 Ausdrückliche Umsteuerung des bisherigen Ablaufs

Diese Datei ersetzt nach expliziter Übernahme durch den Nutzer **nur die Ausführungsreihenfolge, die Testtaktung und die Revieworganisation** der bisherigen starren Schritte A–I in `docs/evidence/stabilization/g2-completion-plan.md`. Die vollständigen fachlichen Anforderungen und ihre Normbezüge bleiben bestehen. Insbesondere dürfen keine Normbedingungen, Python-gestützten Negativprüfungen oder bereits festgelegten Abnahmeverträge durch Umbenennen oder Löschen verschwinden.

Die bestehenden Dokumente `AGENTS.md`, `docs/spec/04-*` bis `09-*`, `13-*`, `14-*`, `16-*` und `17-*` bleiben fachlich massgeblich. Bei echten fachlichen Widersprüchen gilt: Originalnorm und freigegebener Anforderungsvertrag vor Implementierung; erforderliche Nutzerentscheidung ausdrücklich festhalten. Historische Prüfberichte werden **nicht rückwirkend umgeschrieben**. Die neuen Steuerungsdateien erhalten einen einzigen aktiven Einstieg unter `docs/g2/`.

**Nicht beauftragt:** P3-Produktfreigabe und vollständige Legacy-CLI-Kompatibilität, P4 (Transfer/Validierung), P5 (DB), eine generelle Neuentwicklung der Architektur, zusätzliche Laufzeitframeworks oder eine neue allumfassende Testplattform. Vorhandene LSP-, CLI-, SDK- und Browserverträge bleiben jedoch Regressionspflicht für G2-relevante Änderungen.

## 2. Verifizierte Ausgangslage und Erkenntnisse aus den anderen Compilern

### 2.1 `interlis-java` am untersuchten Commit

Laut versionierten Status-/Dashboardberichten, **nicht durch hier ausgeführte neue Tests**:

- 700 JVM-Tests und 3'257 portable Testfälle je ausgewiesener Laufzeit zuletzt grün.
- Externe Conformance-Suite: **593 ausgewählte Fälle**, Projektbewertung **574 PASS / 19 XFAIL / 0 FAIL / 0 ERROR / 0 XPASS**. Davon sind **18 normative, noch nicht neu freigegebene Referenzabweichungen** und **ein separat geführter INTERLIS-1-Profilfall**. Die rohe Runnerbilanz und die Projektbewertung müssen getrennt sichtbar bleiben.
- **369** inventarisierte Compilerregel-Bedingungen mit **1'476 geplanten Fällen**; erst **19** Bedingungen mit atomaren Assertion-Verknüpfungen. `CANDIDATE_ONLY` ist **kein** Wirksamkeitsbeweis. **80** weitere G2-Anforderungen sind ohne konkrete Abnahme.
- A1 und A2-R1/R2 haben unabhängige Teilabnahmen. A2-R3/R4 und A3 sind nicht als Gesamtumfang abgenommen; neueste Zwischenreviews nennen konkrete Nachweis- und Compilerlücken.
- Der aktuelle Git-Baum enthält **36 `.py`-Dateien** (33 unter `tools/reference/`, zwei unter `tools/performance/`, eine unter `tools/sdk/`, zusammen ca. 310 KB). Mindestens `conformanceWrapperTest`, `g2EvidenceTest`, `conformanceCheck`, `geoCorpus`, `jvmBenchmark`, `sdkProcessCheck` rufen direkt Python aus Gradle auf.
- Auffällige Produktionsschwerpunkte: `GraphBuilder.java` (~361 KB), `SyntaxTreeBuilder.java` (~122 KB). Kein grosser Refactor ohne reproduzierten Bedarf.

Diese Zahlen sind Momentaufnahmen. **Codex muss zu Beginn `git rev-parse HEAD`, `git status`, vorhandene Tasks und den neuesten Stand erneut prüfen**. Bei Abweichungen gilt das tatsächlich ausgecheckte Repository; kein historischer Wert wird als aktueller Testlauf ausgegeben.

### 2.2 Vergleich als konkrete Architekturentscheidung

| Quelle | Was besonders gut funktioniert | Was wir übernehmen / nicht übernehmen |
|---|---|---|
| [`claeis/ili2c`](https://github.com/claeis/ili2c) | JUnit-Tests mit vielen kleinen `.ili`-Originalfällen; teils direkte Metamodellassertions (`TransferDescription`); gültige und ungültige Gegenfälle | **Übernehmen:** kleine fachlich klare Modelle und Assertions auf tatsächliche Typen/Elemente. **Nicht übernehmen:** nur `assertNull`/`assertNotNull`, globale Loggingmuster und grosse Boilerplate-Mengen. |
| [`edigonzales/ilic-fork`](https://github.com/edigonzales/ilic-fork) | Trennt externe gemeinsame Conformance-Suite (610 Fälle) von lokalen C++-/CLI-Regressionen, Snapshot-/Inkrementalitäts-, ABI- und WASM-Tests; CTest-Canary prüft echte Failures | **Übernehmen:** genau diese Trennung, kleine Testharnesses, Negative-Canaries, CI-Schichten. **Nicht übernehmen:** C++-spezifische Sanitizer als Java-Pflicht und einen zweiten Runner. |
| [`edigonzales/interlis-java`](https://github.com/edigonzales/interlis-java) | Starke öffentliche Java-Semantik (`CompiledSchema`, Queries, Diagnosen), JVM-/TeaVM-/Browser- und Fresh/Reuse-Prüfung, unabhängige Reviews | **Behalten:** semantische Tiefe, Gating, Browserparität, strenge Ausnahmen. **Vereinfachen:** übergrosses Evidenzinventar, Python-Orchestrierung, doppelte Prüfpfade, starrer A–I-Prozess. |

**Zentraler Befund:** `ilic-fork` und `interlis-java` verwenden bereits dieselbe extern gepinnte [`interlis-compiler-testsuite`](https://codeberg.org/edigonzales/interlis-compiler-testsuite) mit dem in unserem Python-Skript festgelegten Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`. Sie besitzt den ausführbaren **Java-Runner `InterlisConformance.java`**. `interlis-java` ruft ihn heute **aus Python** auf. Die Migration darf daher **keinen eigenen Conformance-Runner in Java duplizieren**. Der vorhandene Runner ist die einzige zuständige Instanz für Testfallauswahl, `PASS/XFAIL/FAIL/XPASS/ERROR` und Rohbericht.

**Wichtige Ausnahme:** Unser `conformance_check.py` implementiert zusätzliche, strengere positive/negative Quellen- und Diagnoseprüfungen für 18 normative Abweichungen. **Diese projektspezifische Semantik muss erhalten bleiben**; sie darf nicht einfach durch eine pauschale zusätzliche XFAIL-Liste ersetzt werden.

## 3. Die künftige Testarchitektur: vier Ebenen, eine klare Wahrheit

### Ebene T1 – Fachliche JVM-Vertragstests (schnell, pro Änderung)

JUnit 5 in den vorhandenen Modulen. Für eine konkrete Sprachregel mindestens:

- **Positiv:** ein wirklich gültiges `.ili`-Modell und korrektes öffentliches `CompiledSchema`.
- **Negativ:** ein minimal ungültiges Gegenmodell, **kein** gültiges Schema; erwartete fachliche Rejection und möglichst genaue Diagnose samt Bereich.
- **Öffentliche Semantik:** Symbolidentität, gebundenes Ziel, Typrelation, Herkunft, effektive Member-/Transferreihenfolge, Constraint-/View-Inhalte oder Quellreferenz – nur die für diese Regel relevanten Teile.
- **Grenze:** Grenzwert, Versionsunterschied oder Namens-/Importsituation, falls für die Regel relevant.

Tests dürfen die Fachregel nicht einfach durch Spiegelung interner Implementierungsdetails bestätigen. Kleinere externe `.ili`-Fixtures wie bei `ili2c` sind bevorzugt, wo sie leichter zu lesen sind; Inline-Quellen sind für kompakte Varianten geeignet. Bestehende testseitige `CompilerTestSupport`-Methoden wiederverwenden.

### Ebene T2 – Eine externe Compiler-Conformance-Suite (gemeinsam, unverfälscht)

- Exakt **ein** gepinnter Java-Runner aus `interlis-compiler-testsuite`; keine zweite eigene Auswahl-/Statusmaschine.
- Für G2 bewusst **2.3/2.4-Profil** mit der historisch dokumentierten Auswahl von **593** Fällen verwenden; die Suite insgesamt enthält 610 Fälle. Die Sonderbehandlung des als INTERLIS-1-Profilfall bekannten ausgewählten Falls muss transparent bleiben – ein Name mit `ili23` macht den Quellinhalt nicht automatisch zum INTERLIS-2-Modell.
- **Raw-Runnerergebnis** und **projektspezifische normative Zusatzbewertung** getrennt ausgeben. Bei den 18 Fällen das Original, Kontrollquellen, Quell-Hashes, Diagnoseart/-argumente/-bereiche und Exitcodes weiterhin prüfen. Ein Referenzprozessabsturz ist `ERROR`, kein sachlicher Compilervergleich.
- Baseline, Suite-Pin, Testselektion und XFAIL-Ausnahmen **niemals implizit aktualisieren**. Der zusätzliche Java-Postprozessor darf nur die explizit angeführten 18 geprüften Normausnahmen separat beurteilen, keine neue allgemeine Statusmaschine bauen. XPASS soll zur Prüfung führen, nicht still verschwinden.

### Ebene T3 – Portabilität und Integrationsverträge (risikobasiert, vollständige Phasengates)

JVM/TeaVM-JS/TeaVM-WasmGC, Node, echte Browser (inklusive Safari), Web Image, public Bridge, CLI/SDK, LSP und Diagramm. **Nicht jede lokale Compilerregel benötigt einen vollen Browsereinstieg.** Ein gezielter Bridge-/WASM-Regressionslauf ist bei betroffenem Adapter sofort fällig; die gesamte Browser- und Web-Image-Matrix ist spätestens am **verbindlichen Phasengate** frisch auszuführen. Wer eine Pflichtprüfung nicht ausführen kann, meldet `NOT_EXECUTED` und damit ein offenes Gate – keinen PASS.

Semantikparität bedeutet Vergleich der tatsächlich exportierten Daten. Ein Completion-Ergebnis, in dem ein Member vorkommt, beweist z. B. nicht dessen **effektive Reihenfolge**. Fehlende Brückenfelder bleiben als Nachweislücke sichtbar. Nur für konkrete echte Produktbedürfnisse öffentliche Bridge-APIs vergrössern.

### Ebene T4 – Echte gepinnte Modell-Closures (End-to-end vor G2)

Gepinnte reale INTERLIS-2.3/2.4-Modelle einschliesslich transitiver Importe aus dem vereinbarten Modellindex (u. a. `geo.so.ch`). Teste direkte Compiler-/CLI-Ausführung, Offline-/Pin-/Lock-Verhalten und wesentliche öffentliche Schemaelemente. Nur „Exit 0“ reicht nicht. Stabile Quellen/Artefakthashes, Modellversionen und die Closure gehören zum Nachweis.

### Ergänzende Qualitätsprüfung – Fehlgrün-Canary

Die eigene Test-/Evidenztechnik benötigt wenige **negative Kontrolltests**: Ein absichtlich fehlender Conformance-Fall, ein duplizierter Fall, ein alter Bericht, eine unpassende JUnit-Klasse mit gleichem Methodennamen oder ein übersprungener Test muss zuverlässig **scheitern**. Das übernimmt die Grundidee des `ilic-fork`-Canarys, ohne wieder eine umfangreiche generische Evidenzplattform zu bauen.

### Ausführungstaktung

| Zeitpunkt | Pflicht | Ausdrücklich nicht erforderlich |
|---|---|---|
| Während des Fixes | Betroffene T1-Tests; zuerst reproduzierendes Rot, danach Grün | Gesamte Browsermatrix bei jedem Edit |
| Vor dem Feature-Review | Betroffene JVM-Modultests; gezielte T2-Fälle für die geänderte Regel, **volle** T2-Suite bei breit wirkender Parser-/Binder-/Typänderung; relevante T3-Adaptertests | Sämtliche historischen Inventargeneratoren oder die komplette Browsermatrix erneut starten |
| Vor dem Review eines Werkzeugmigrationstickets | Identische Alt-/Neu-Eingaben und Fehlpfade; neue Java-Tests; echte Prozessläufe und Rohdaten | Reimplementierung von Tests, die nachweislich historisch und nicht mehr aktiv sind |
| Vor Phasenabnahme | `check`, T2, `checkPortable`, `checkBrowser` inkl. echtem Safari, Web Image, relevante SDK-/LSP-Adapterverträge; Scope/T4 gemäss Phase | Eine grüne Datei oder ein alter Run als Ersatz für die Phase |
| Vor G2-Gesamtabnahme | Alle T1–T4-Gates, G2-Regelmatrix, Normfreigaben, Python-free-Gate und exakter Review-SHA | Release-/P4-/P5-Zusagen |

**Ziel:** Tests sind so streng wie nötig und so lokal wie möglich. Der Agent darf einen aktuellen, nicht betroffenen grünen Plattformnachweis zur Risikoabschätzung verwenden, **nicht** um eine fällige neue Phasenabnahme zu behaupten.

## 4. Ein Arbeitszyklus pro kleiner fachlicher Änderung

1. **Ticket wählen:** Eine bekannte Fehlerquelle oder eine zusammenhängende Sprachregel; Normstellen/Anforderungs-IDs, vorhandene Implementierungsorte und die erwartete öffentliche Wirkung bestimmen. Keine neue Vollinventur.
2. **Fehlschlag beweisen:** Original-`.ili` beibehalten, eine minimal reproduzierbare positive/negative/Boundary-Fixture schreiben; den Fehler vor dem Fix mit echtem Compiler nachweisen (Red). Konstruiere bei Bedarf mehrere Versionen/Importreihenfolgen.
3. **Minimal beheben:** Richtige vorhandene Stelle (`ANTLR`, Syntaxlowering, Bindung, gemeinsame Typrelation, Expression/Constraint, Resolver); keine pauschale Diagnoseunterdrückung, keine spätere Backendlogik im Kern, kein globaler mutable State.
4. **Öffentlich beweisen:** T1 inklusive semantischer Werte, Quellranges und Gate; gezielte T2-Fälle mit unverändertem Pin/Profil (volle T2-Suite bei breit wirkender Änderung und an jedem Phasengate); T3 gezielt, falls betroffen. Negative und positive Kontrollfälle dauerhaft ablegen.
5. **Commit vor Review:** `git diff --check`, tatsächlich gelaufene Tests mit Exitcodes dokumentieren und eine **versionierte Review-Anfrage ohne selbstreferenziellen `head_sha`** zusammen mit Implementierung und Tests **lokal committen**. **Erst danach** `git rev-parse HEAD` bestimmen und SHA im ignorierten `build/review/<TICKET>/handoff.yaml` (oder als separate Übergabenachricht) an den Reviewer geben. Den eigenen Commit-SHA niemals nachträglich in denselben Commit hineinschreiben: Das wäre zirkulär. Kein Push oder Release ohne Auftrag.
6. **Unabhängiger Review:** OpenCode/DeepSeek Flash 1017 auf isoliertem Worktree am konkreten SHA. Reviewer liest Norm/Original/Code, führt Tests selbst aus und **entwickelt mindestens einen neuen relevanten Gegenfall**, der nicht aus Codex-Tests übernommen wurde. Urteil und konkrete Findings.
7. **Bei Nacharbeit:** Nur Findings beheben, neue SHA committen und den geänderten Umfang **erneut unabhängig prüfen**. Kein neues Reviewframework und keine neue grosse Planungsrunde.
8. **Bei Annahme:** Ticket mit Testbeweisen und Review-SHA als `ACCEPTED` markieren; unabhängig offene Fachfragen als eigene Tickets erhalten; nächster fachlicher Schritt.

**Zustände strikt trennen:** `PLANNED`, `IMPLEMENTED`, `TESTED`, `REVIEWED_ACCEPTED`, `BLOCKED_NORM`, `BLOCKED_INFRA`. Eine Testkandidatenliste oder ein grüner Gradle-Task macht keine Regel automatisch zu `REVIEWED_ACCEPTED`.

### Reviewurteile

- `ABGENOMMEN`: konkrete getestete Behauptungen am exakten SHA, unabhängige Ausführung und eigene Gegenprobe, keine blockierenden Befunde.
- `NACHARBEIT`: reproduzierbarer Gegenfall oder konkrete fehlende Vertragsprüfung, mit präziser Abhilfe.
- `WARTET_AUF_NORMFREIGABE`: eine echte offene INTERLIS-Auslegung; weder Testerwartung noch XFAIL wird ohne Nutzerentscheidung geändert.
- `NICHT_PRUEFBAR`: Toolchain, Quellen, Snapshot oder auszuführende Tests fehlen. Kein fachliches PASS.

## 5. Phase 0a – Schlanker Start und beweisbarer Pilotfix

**Ziel:** Eine reale Sprachregel korrigieren und den neuen Implementierung/Review-Takt ausprobieren, bevor die Python-Migration beginnt.

**Lieferungen:**

1. Git-/Toolchainstand und Baseline prüfen. Nur bestehende Statusquellen lesen: `docs/implementation-status.md`, `docs/evidence/stabilization/phase-a-dashboard.md`, neuestes A2-R3-Review, normative Entscheidungsliste. Geschlossene A1/A2-R1/R2-Abnahmen nicht erneut erfinden.
2. `docs/g2/README.md` als Einstieg und `docs/g2/WORK_QUEUE.md` mit wenigen konkreten Tickets anlegen; alte aktive A–I-Fortsetzungsanweisungen auf den hier übernommenen Plan verweisen lassen. Historische Berichte nicht rückdatieren.
3. Bereits korrigierten XML-Klassenidentitätsfehler (`testcase@classname`/`testsuite@name`) als offenen Reviewauftrag notieren; ein technisches Mini-Review nur vorziehen, falls er die neue Beweiskette blockiert.
4. **Erster Bug:** `G2-LEX-COMMENT-OUTER`. Mit `testdata/g2/a2-unterminated-nested-comment.ili` reproduzieren: verschachtelter Kommentar, innerer abgeschlossen, äusserer offen. `STRICT` muss ablehnen; `EDITOR` darf Recovery liefern, aber kein falsches gültiges Schema; 2.3/2.4; gültige verschachtelte Kommentare unverändert korrekt mit Source-/UTF-16-Bereichen. Der aktuelle `LexicalRecoveryContractTest` behandelt laut untersuchtem Stand nur `/* outer` als unterminierten Negativfall.
5. Gezielte Tests, frisch ausgeführte Regression, Implementierungscommit, unabhängiger Reviewer und neue Gegenprobe. Ergebnis dokumentieren.

**Gate P0a:** Bug ist wirklich behoben und am festgelegten SHA unabhängig akzeptiert; neue Queue/Planhierarchie eindeutig; ursprüngliche Referenz-/Normpins unverändert; keine scheinbare vollständige A2/A3-Abnahme behauptet.

## 6. Phase 0b – Sämtliches Python entfernen (früh und verpflichtend)

**Startbedingung:** P0a angenommen. **Endbedingung:** Keine getrackte `.py`-Datei, keine notwendige Python-Installation, keine aktiven Python-/pip-Kommandos und **alle erforderlichen Test-/Build-Einstiege funktionieren nachweislich ohne Python**. Einzelne nachfolgende Tickets dürfen technisch unabhängig laufen, G2-Phase 1 beginnt jedoch erst nach vollständiger P0b-Abnahme.

### 6.1 Klassifizieren statt 1:1 portieren

Erstelle **einmalig** eine kurze, vollständige 36-Dateien-Migrationsliste aus `git ls-files '*.py'`. Pro Datei genau eine Entscheidung, jeweils mit Nachweis:

- `REUSE_EXTERNAL`: Die Funktion ist bereits im gepinnten **externen Java-Runner** verfügbar. Python-Orchestrierung entfernen, Java-Runner direkt aus Gradle starten.
- `PORT_MINIMAL`: Ein **projektspezifisch notwendiger** Vertrag fehlt extern; als kleinste Java-/JUnit-Implementierung erhalten.
- `REPLACE_WITH_TEST`: Ein bisheriges Script wird durch gezielte vorhandene oder neue JUnit-/Gradle-Vertragstests ersetzt; Funktion und Negativpfad bleiben erhalten.
- `FREEZE_ARCHIVE`: Ein nur historisch benutzter Inventar-/PDF-Generator erzeugt bereits gepinnte, geprüfte Artefakte. Diese bleiben unveränderlich archiviert; ein dauerhaft notwendiger Prüfer bleibt in Java. Entfall der Generatorfunktion nur, wenn der unabhängige Review begründet, weshalb **keine aktuelle Reproduktionspflicht** verloren geht.
- `DELETE_REDUNDANT`: Doppelte, unbenutzte oder durch andere Tests bereits vollständig gedeckte Logik; Löschung nur mit Verwendungsanalyse und Reviewerentscheid.

**Kein pauschales „alle Skripte nach Java übersetzen“.** Es muss am Ende **null Python** geben, aber nicht 36 neue Java-Helferklassen.

### 6.2 Migrationspakete in der vorgesehenen Reihenfolge

| Ticket | Existierende Python-Schwerpunkte | Java-/Gradle-Lösung und harte Abnahme |
|---|---|---|
| `G2-JAVA-TOOLS-01` | `conformance_check.py`, `test_conformance_check.py`, `reference_check.py`, alte Vergleiche zu Units/Views | **Gepinnten externen `InterlisConformance.java` direkt aus Gradle starten.** Download/Pins und Kommandokonstruktion durch kleine Gradle-/Java-Integration; projektspezifische 18 Normausnahmen in eigenem minimalem Java-/JUnit-Prüfer mit Original-/Kontrollquelle/Diagnoseprüfung. `conformanceCheck` und `conformanceWrapperTest` bleiben echte Gates. Referenzprozess-Negativpfade. Keine zweite Fallklassifikations-Engine. |
| `G2-JAVA-TOOLS-02` | `g2_evidence.py`, `g2_inventory.py`, `g2_a3_pairs.py`, `g2_test_candidates.py`, zugehörige Tests | Nur **laufend benötigte** Quell-/Hash-/JUnit-XML-/Assertion-Nachweise als kleine Java-Tests/Verifier erhalten. XML muss richtige `testsuite@name` **und** `testcase@classname` **und** `testcase@name` prüfen. Selbsttest: falsche Klasse bei gleicher Methode, fehlender/alter/übersprungener Bericht und unpassender SHA werden abgelehnt. `g2EvidenceTest` bleibt ausführbar; keine neue generische Snapshot-/Patchplattform. |
| `G2-JAVA-TOOLS-03` | `g2_norm_*`, `g2_grammar_inventory.py`, `g2_builtin_inventory.py`, `g2_predefined.py`, `author_g2_predefined.py`, `g2_spec_contracts.py` usw. | Bereits vorhandene Norm-/Grammatik-/Builtin-/Requirements-/PDF-Inventare als **historische gepinnte Eingaben** erhalten. Die für weitere fachliche G2-Prüfung nötigen aktiven Invarianten zu Java/JUnit überführen; entbehrliche Erzeugungsketten löschen. Wenn Text aus gepinnten Norm-PDFs weiterhin dynamisch benötigt wird, Extraktionsprofil ausdrücklich validieren (PDFBox ist **nicht** codepoint-/pypdf-offsetkompatibel per Annahme). Kein stilles Repinning. |
| `G2-JAVA-TOOLS-04` | `geo_corpus.py`, `generate_text_weights.py`, `benchmark_jvm.py`, `probe_wasm_exit.py`, `tools/sdk/check_distribution.py` und verbliebene Skripte | Für **aktive** Modell-Closure-/Performance-/SDK-/Prozessprüfungen kleine Java-/JUnit-/Gradle-Entrypoints; vorhandenes Node für bestehende Wasm-/Browser-Tests weiterverwenden. Bei einem Generator nur die versionierte Ausgabe plus überprüfte Herkunft einfrieren, sofern keine künftige Regeneration nötig ist. Bestehende Gradle-Tasknamen und Fehlerpfade behalten. |
| `G2-JAVA-TOOLS-05` | Repo-weite Bereinigung, CI, README, Gradle, Guard | Alle `.py`-Dateien löschen; verwaiste Python-Aufrufe und erforderliche `pip`-Schritte entfernen. Neuer **`pythonFreeCheck`** prüft getrackte `.py`-Dateien und aktive Tool-/Taskaufrufe. Negative Canary erkennt absichtlich eingefügte Python-Datei/-Abhängigkeit. **Zusätzlich** realer Build-/Testlauf in einer Umgebung ohne Python-Interpreter (nicht bloss ein Grep-PASS). |

Jedes Paket ist ein **separater testbarer Diff mit Commit und unabhängigem OpenCode-Review**. Nach G2-JAVA-TOOLS-01 läuft Conformance direkt über den bestehenden Java-Runner. Solange andere Python-Werkzeuge noch nicht migriert sind, wird der Teilstatus korrekt als `PARTIAL` geführt; insbesondere ist das globale P0b-Gate noch offen.

### 6.3 Minimaler technischer Java-Zuschnitt (Vorschläge, nicht neue öffentliche APIs)

Erst tatsächliche vorhandene Java-/Test- und Gradle-Strukturen untersuchen. Für gemeinsam benötigte Werkzeuglogik **maximal ein kleines testseitiges Tool-Paket bzw. Modul**, nur wenn Wiederverwendung eine echte Notwendigkeit ist. Sonst in bestehenden Test-SourceSets/Test-Fixtures und JavaExec-/Gradle-Aufgaben bleiben.

| Verantwortung | Vorgeschlagener Typ / Methodenvertrag | Was ausdrücklich nicht implementiert wird |
|---|---|---|
| Externe Suite | `PinnedConformanceTask`/Gradle-Aufgabe: `verifyPin(Path archive, String sha256)`, `runExistingJavaRunner(Path runner, Path cli, Path selection, Path out)`, `verifyCaseSet(Set<String> expected, Report raw)`; frische Reports und Exitstatus prüfen | Kein eigener Testfall-Runner; keine eigene Implementierung von PASS/XFAIL/FAIL/ERROR |
| Normative Ausnahmen | `NormativeExceptionVerifier.assess(Expectation e, RawCase c, Path cli)`: Quelle/Hash, Version, Original-/Kontrollprozesse, strukturierte Diagnosen (Code, Severity, Phase, Argumente, Range, Multiplizität), Ergebnis/Fehlergrund. `verifyAll(...)` kontrolliert exakte Fall-ID-Menge | Keine automatische „akzeptierte Abweichung“ aufgrund eines grünen Status; keine Änderung der 18 Erwartungen |
| Nachweisintegrität | `JUnitXmlEvidenceVerifier.verify(Path xml, String suite, String className, String methodName, Instant runStart)`: eindeutige Suite, Klasse, Methode, frische Datei, kein Fehler/Skip; `ArtifactPinVerifier.verify(Path, sha256)` | Kein unnötiger Snapshot-Container mit eigener domänenspezifischer Sprache |
| Prozessprüfungen | `CompilerProcessProbe.run(Path executable, List<Path> models, ... )`: Exit, stdout/stderr, Timeout, eindeutige `ACCEPTED`/`REJECTED`/`ERROR`/`TIMEOUT`-Klassifikation | Kein maskiertes `ERROR` als gültige Ablehnung |
| Pythonfreiheit | `pythonFreeCheck`: prüft **getrackte** `.py` und aktive Build-/CI-/Skriptaufrufe; echte Python-freie CI-Jobausführung prüft zusätzlich Runtime-Abhängigkeiten | Kein Verbot, historische Berichte zu lesen, in denen alte Python-Befehle dokumentiert sind |

Typnamen sind Vorschläge; keine Klasse erstellen, bevor ein konkreter Test eine aktuelle Aufgabe dafür nachweist. Methoden vor Implementation an reale Formatverträge und vorhandene Datenklassen anpassen. Die Java-Werkzeuge dürfen niemals als versteckter Wrapper Python starten.

### 6.4 Unverzichtbare Paritäts- und Manipulationsnachweise

- **Suite:** exakt dieselben ausgewählten Fall-IDs, gleicher Pin, gleiche Rohberichte/Status und Exitpolicy für identische Eingaben. Zusätzlich: fehlender, doppelter, unerwarteter oder XPASS-Fall muss sichtbar fehlschlagen.
- **Normausnahmen:** gleiche 18 IDs, identische Original- und Kontrollquellen/Hashes, unveränderte semantische Diagnosesignaturen und die getrennte rohe/Projektbilanz. Der separate INTERLIS-1-Profilfall bleibt separat.
- **Evidenz:** alte positive und negative Python-Selbsttests gegen den neuen Java-Prüfer als *Verhaltensvertrag* übernehmen. Bei einer Migration dürfen Alt-/Neu-Reports nicht allein deshalb als gleich gelten, weil ein Wrapper nur `PASS` ausgibt.
- **Ausführbarkeit:**  `./gradlew check`, `conformanceCheck`, `conformanceWrapperTest`, `g2EvidenceTest`, `sdkProcessCheck`, aktive Referenz-/Corpus-/Performance-Tasks erfolgreich ohne Python ausführen. Toolchainbedingte andere Blocker gesondert ausweisen.
- **Reproduzierbarkeit:** Originaldaten nicht entfernen; der Runner muss im frischen Checkout nach Zugriff auf einen gepinnten Download oder einen vorbefüllten geprüften Cache laufen. Tests melden externen Netz-/Referenzausfall ehrlich.

**Gate P0b:** `git ls-files '*.py'` ohne Treffer; `pythonFreeCheck` inkl. Canary grün; sämtliche aktiven Ausführungswege Python-frei, im unabhängig geprüften Java-/Gradle-Build und separatem Umgebungslauf nachgewiesen. **Ohne P0b kein Start der grossen Fachphase 1 und keine G2-Freigabe.**

## 7. Phase 1 – Sprachgrundlage (bisher B–D)

Nach P0b fachliche Tickets in der Reihenfolge der tatsächlichen Risiken:

1. **B1:** vordefinierte Modelle, Symbole, Typen, Units, OID und versionierte Builtins; konkret `SurfaceEdge`, `SurfaceBoundary`, `LineGeometry` korrekt öffentlich binden.
2. **B2/B3:** Namensräume, Imports, Sichtbarkeit, Vorwärtsreferenzen, case sensitivity, Syntax-Alternativen, Recovery und klare Trennung `SOURCE_ERRORS` / `INCOMPLETE_SUPPORT` / `INTERNAL_FAILURE`. Offene `TYPE_MODEL_STRUCTURE_BOUNDARY` zur Entscheidung vorbereiten statt still ändern.
3. **C1–C4:** numerische Unterteilungen, exakte Einheiten/Skalierung, Text/Enum/OID, FORMAT, Datum/Zeit, Geometrie, Collections und gemeinsame Typrelationen. `NUMERIC_SCALING_PAIR_AND_CONSTANT_PROFILE` separat offen halten.
4. **D1–D3:** Vererbung, Assoziationen, Rollen, Restriktionen, effektive Mitglieder, Herkunft und **Transferreihenfolge**, unabhängig von Datei-/Importreihenfolge und Fresh/Reuse.

**Komponenten:** vorhandene `InterlisLexer.g4`, `InterlisParser.g4`, `AntlrSyntaxService`, `SyntaxTreeBuilder`, `LanguageProfile`, `NameResolver`, `ImportBinder`, `GraphBuilder`, `StructureBindings`, `LineForms`, `TypeCompatibility`, `SemanticGraph`. Erst in tatsächliche Klassen lesen, dann kleine Änderung in der fachlich richtigen Stelle.

**Gate Phase 1:** Jede betroffene G2-Regel ist mit eigenen fachlichen Positiv-/Negativ-/Queryassertions verifiziert und unabhängig geprüft oder als echte Normentscheidung *nicht* abgenommen; vollständige T1/T2/T3-Gates und neues Phasenreview. Keine still akzeptierte Implementierungslücke, kein falsches `CompiledSchema`.

## 8. Phase 2 – Ausdrücke, Constraints, Views und Übersetzungen (bisher E–F)

1. Objekt-/Attribut-/Rollenpfade mit Mengen/Indizes und Spezialkontexten; **21** Standardfunktionen, typrichtiges Argument-/Ergebnisverhalten und `UNDEFINED`. Externe Funktionen **nicht** ausführen.
2. Alle Constraintfamilien einschliesslich SET/WHERE, Existence, Unique, Mandatory/Plausibility, External, vererbte Namen; öffentliche gebundene Struktur und korrekte Typrelation.
3. Views: Projektion, Selektion, Join, Union, Aggregation, Inspektion, Ableitung, Vererbung, `ALL OF`; korrekte Schemaelemente und Referenzziele, nicht nur erfolgreiches Parsen.
4. Übersetzte Import-Closures, Korrespondenzen, effektive **Reihenfolgen**, `CONTEXT/GENERIC/DEFERRED`, Metadaten, Grafik/Signatur und LineForms. `areAreas2`/`areAreas3` nur mit geklärter Normstelle ändern.

**Komponenten:** `FunctionBindings`, `BuiltinFunctions`, `GraphBuilder`, `ViewBuilder`, `GraphicBuilder`, `MetadataBindings`, `DefaultSemanticQueries` sowie vorhandene öffentliche Metamodelltypen.

**Gate Phase 2:** T1/T2/T3 vollständig; semantische Abnahme der E/F-Regeln; eigene Negativproben für falsche gleichnamige Ziele und verlorene/umgeordnete Mitglieder; frischer unabhängiger Phasenreview.

## 9. Phase 3 – Echte Overlays und reproduzierbare Modellauflösung (bisher G–H)

1. `SemanticOverlays` von Fingerprint-Platzhaltern zu echten, unveränderlichen Translation-/CRS-/Metaattribut-Nutzdaten; Zielidentität, Konfliktprüfung, Herkunft und öffentliche Wirkung.
2. Inhaltliche Fingerprints; reine Overlaywechsel invalidieren die richtige Closure und erhalten unabhängige Symbole. Fresh/Reuse fachlich identisch.
3. Repositoryindizes, Auswahl, lokale/HTTP-Hostbeschaffung, Versionen, transitive Importe, Offline, Pin/Lock, Mehrdeutigkeit, Abbruch, stale Input. Kein HTTP im Compilerkern.

**Komponenten:** `SemanticOverlays`, `CompilationFingerprinter`, `DefaultWorkspace`, `DefaultModelResolutionService`, `ResolverCatalog`, `SourceProvider`, `CompilerServices`.

**Gate Phase 3:** Effektive Daten und Resultate bei gleichen Inputs/Reihenfolgen identisch; differente Overlays bewirken die erwarteten Differenzen; Offline-/Lock-/Abbruchfehler reproduzierbar; vollständige T1/T2/T3, neue Gegenproben und unabhängiger Phasenreview.

## 10. Phase 4 – Vollständige G2-Abnahme (bisher I)

1. Die vorhandenen **369 Compilerbedingungen**, 80 offenen G2-Anforderungen, relevante Builtins/Predefined-Inhalte und Grammatikzweige **nicht nur inventarisieren**, sondern nach tatsächlichen fachlichen Assertions/Normbelegen, betroffenen öffentlichen Modellen und Versionen prüfen. `CANDIDATE_ONLY` bleibt offen. Es ist **nicht** mechanisch für jede Grammatik-Occurrence ein unnötiges Testquartett zu erzeugen; fachliche Bedingung statt Zahl optimieren.
2. Vollständigen **A3-Semantikvergleich**: JVM/portable und wo möglich Referenztools. Verlorene Member, gleichnamige falsche Ziele, vertauschte Reihenfolgen und illegale Schemaannahme müssen als echte Gegenproben erkannt werden. Nur gemeinsame tatsächlich verfügbare Felder vergleichen; Abdeckungslücken explizit benennen.
3. **18 normative Referenzausnahmen** einzeln mit korrekten Normstellen, minimalen Gegenfällen und tatsächlichen Compilerprozessen zur Nutzerentscheidung vorlegen; `TYPE_MODEL_STRUCTURE_BOUNDARY`, `NUMERIC_SCALING_PAIR_AND_CONSTANT_PROFILE`, `areAreas2/areAreas3` und weitere Profile ebenfalls. Besonders bei sechs OID-/CONTRACTED-Fällen wurden in bisherigen Berichten unpassende Auszüge benutzt – nicht ungeprüft übernehmen.
4. T4 mit echten gepinnten 2.3/2.4-Closures samt transitiven Importen, Versionen, Offline-/Lock-Verhalten, öffentlichen Typen und Reihenfolge durchführen.
5. T1–T4, `pythonFreeCheck`, CI-Reproduzierbarkeit, vollständigen G2-Regel-/Abnahmestatus und einen **neuen unabhängigen Gesamt-Review für exakt den letzten Commit** durchführen.

**G2 ist nur abgenommen, wenn** keine unerkannte Implementierungslücke und kein ungeklärter `FAIL/ERROR/XPASS` existieren, alle notwendigen Normentscheidungen dokumentiert/freigegeben sind, die vollständigen G2-Regeln und Plattformen nachgewiesen wurden und OpenCode den exakten Schlussstand akzeptiert. Benutzerfreigegebene **rohe Referenzabweichungen** bleiben sichtbar, auch wenn das Projektprofil sie gesondert bewertet. Python ist vollständig entfernt. P3 ist damit **nicht** automatisch abgenommen.

## 11. Prioritäten und fokussierte erste Tickets

Nach P0a folgt zwingend P0b. Anschliessend: (1) **falsch angenommene ungültige Modelle**/falsches Schema, (2) **gültige zurückgewiesene Modelle**, (3) **falscher öffentlicher Graph trotz grünem Compile**, (4) Varianten von Import/Version/Recovery/Reuse, (5) fehlende Assertions ohne bekannten Defekt, (6) verbleibende Nachweistechnik.

**Vorgeschlagene erste Fachkarten:**

- `G2-LEX-COMMENT-OUTER`: Offener äusserer Blockkommentar trotz geschlossenem innerem; 2.3/2.4; STRICT/EDITOR; Range/Gate. Start in Phase 0a.
- `G2-B1-PREDEFINED-LINE`: Binding von `SurfaceEdge`/`SurfaceBoundary`/`LineGeometry`; sechs bekannte Abweichungen, öffentliche Zielidentität und beide Sprachversionen.
- `G2-A3-PUBLIC-GRAPH`: verlorener geerbter Member, falsches gleichnamiges Ziel, semantische Reihenfolge bei Übersetzung auf tatsächlich exportierten JVM-/portablen Feldern.
- `G2-REVIEW-R3-XML-IDENTITY`: sehr kleiner Nachweisreview zum bereits korrigierten `testsuite@name`/`testcase@classname`-Problem, ohne neue Evidence-Architektur.

**Optionaler Drei-Compiler-Gegenvergleich:** Bei strittigen Regel-/Normfragen denselben gepinnten `.ili`-Fall mit `ili2c`, `ilic-fork` und `interlis-java` ausführen. Ergebnis sauber unterscheiden: `ACCEPTED`, `REJECTED`, `ERROR`, `TIMEOUT`, `UNAVAILABLE`. Vergleich ist eine zusätzliche Diagnosequelle, **keine Normabstimmung nach Mehrheit**; `ilic-fork` wird nicht zum benötigten Produkt-/Build-Abhängigkeit jedes Tickets.

## 12. Kleine, überprüfbare Abnahmeartefakte

Pro Ticket eine **mit dem Code committete** kurze `docs/g2/review/<TICKET>-request.md` (Scope, `base_sha`, Tests, Belegpfade; **noch kein** eigener `head_sha`) sowie nach dem Commit ein **nicht mitzucommittendes** `build/review/<TICKET>/handoff.yaml` bzw. separate Übergabenachricht mit dem tatsächlichen `head_sha`. Rohreports liegen in `build/reports/` oder einem dauerhaften CI-Artefakt und können vom Reviewer neu erzeugt werden. Der Code-Commit selbst darf keine von seiner eigenen SHA abhängige Datei enthalten. Die **einzige** operative Queue bleibt `docs/g2/WORK_QUEUE.md`; historische Nachweisdokumente behalten den Status *historisch*.

```yaml
id: G2-LEX-COMMENT-OUTER
kind: COMPILER_RULE  # oder JAVA_TOOLING
base_sha: <voller-SHA>
head_sha: <voller-SHA>
requirements: [<echte-IDs>]
norm_refs: [<genaue-INTERLIS-2.3-und-2.4-Stellen>]
fixtures: [<Pfad-zu-original.ili>, <Pfad-zu-gegenprobe.ili>]
public_contract: [<was-genau-nachweisbar-sein-muss>]
executed_tests:
  - command: <exakter-Aufruf>
    exit: <Exitcode>
    report: <Pfad-oder-CI-Artefakt>
    sha256: <Report-SHA>
not_executed: [<Pflichtprüfung-mit-Grund>]
norm_decisions: [<offene-Entscheidungen>]
review:
  actual_model_id: <wirklich-eingestellter-provider/model-Wert>
  reviewed_sha: <voller-SHA>
  verdict: ABGENOMMEN|NACHARBEIT|WARTET_AUF_NORMFREIGABE|NICHT_PRUEFBAR
```

Das obige YAML ist **die Übergabemetadatei nach dem Commit**, nicht eine Datei, die mit `head_sha` in denselben Commit geschrieben wird. Der versionierte Request enthält nur vor dem Commit stabile Felder. Nach der Abnahme darf ein gesonderter Status-Commit den **geprüften Implementierungs-SHA** referenzieren; ein solcher Status-Commit gilt nicht selbst als unabhängig überprüfte Codeänderung. Das nächste Phasengate prüft deshalb den dann aktuellen Gesamt-SHA neu.

Bei Java-Werkzeugmigration zusätzlich: `old_python_files`, `classification`, `replacement`, `old_run`, `new_run`, `invariant_checks`, `failure_canary`, `deleted_paths`, `python_free_evidence`. Kein Platzhalter gilt als erfüllter Nachweis.

**Review-Verifikation:** Commit zuerst, separater frischer Worktree mit exakt diesem SHA danach. Der zweite Agent prüft ihn, schreibt den Review ausserhalb des Featurebranches und verändert die zu prüfenden Produktivquellen nicht. Eine Änderung nach Review erzeugt einen **neuen SHA und eine neue Prüfung**. Wenn OpenCode nicht automatisch aus Codex heraus aufgerufen werden kann, wird ein eigenständig ausführbarer Reviewauftrag ausgegeben; Codex darf ihn nicht fingieren.

## 13. Reproduktionskommandos und Dateiablage

Die **drei gelieferten Dateien** aus dieser Unterhaltung werden **im Repository-Root** wie folgt eingeordnet:

| Gelieferte Datei | Ziel innerhalb `interlis-java` |
|---|---|
| `INTERLIS_G2_UMSETZUNGSPLAN.md` | `docs/g2/EXECUTION_PLAN.md` |
| `CODEX_STARTPROMPT_INTERLIS_G2.md` | `docs/g2/CODEX_STARTPROMPT.md` |
| `OPENCODE_REVIEW_PROMPT_INTERLIS_G2.md` | `docs/g2/OPENCODE_REVIEW_PROMPT.md` |

Nach Einordnung beginnt Codex mit `docs/g2/CODEX_STARTPROMPT.md`. Alle Pfade in diesen Dokumenten beziehen sich auf den **Repository-Root**, nicht auf den temporären lokalen Dateiablageort. Das `docs/g2/`-Verzeichnis kann Codex oder der Nutzer anlegen. Das Lesen eines Prompts startet noch keine Implementierung; hierzu ist ein expliziter Nutzerauftrag nötig.

**Bereits vorhandene Tasknamen** (vor Migration teils Python-gebunden; nicht ungeprüft als erfolgreich behaupten):

```bash
./gradlew :interlis-compiler:test
./gradlew check
./gradlew conformanceCheck
./gradlew checkPortable
./gradlew checkBrowser
./gradlew g2EvidenceTest
./gradlew conformanceWrapperTest
./gradlew sdkProcessCheck
```

**Nach P0b zusätzlich:** `./gradlew pythonFreeCheck` und frischer Test-/Buildlauf **ohne Python**. `pythonFreeCheck` ist ein neu zu implementierender Task, kein heute bereits vorhandener Task.

### Primärquellen

- [`interlis-java` Status](https://github.com/edigonzales/interlis-java/blob/main/docs/implementation-status.md), [G2-A–I-Abschlussplan](https://github.com/edigonzales/interlis-java/blob/main/docs/evidence/stabilization/g2-completion-plan.md), [Phase-A-Dashboard](https://github.com/edigonzales/interlis-java/blob/main/docs/evidence/stabilization/phase-a-dashboard.md).
- [A2-R3-Review](https://github.com/edigonzales/interlis-java/blob/main/docs/evidence/stabilization/g2-reviews/A2-R3-review-2026-10-28.md), [Normentscheidungspaket](https://github.com/edigonzales/interlis-java/blob/main/docs/evidence/stabilization/g2-normative-decision-packet-2026-10-28.md).
- [`ili2c`-Regressionen](https://github.com/claeis/ili2c/tree/master/test), [`ilic-fork`-Conformance-Konzept](https://github.com/edigonzales/ilic-fork/blob/main/docs/conformance.md), [`ilic-fork`-Regressionsrunner](https://github.com/edigonzales/ilic-fork/blob/main/cmake/run_ilic_regression.cmake).
- Externe [`interlis-compiler-testsuite`](https://codeberg.org/edigonzales/interlis-compiler-testsuite), Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`; unser archivierter Download-Pin bleibt massgeblich.

---

**Erfolg bedeutet:** mehr unabhängig verifizierte richtige INTERLIS-Semantik, weniger Test- und Dokumentationsballast, vollständige gemeinsame Conformance ohne versteckte Ausnahmen und ein nachweisbar **Python-freier** aktiver Entwicklungs-/Test-/Reviewweg. **Nicht**: mehr generierte Markdown- oder JSON-Dateien.
