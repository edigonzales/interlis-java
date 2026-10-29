# Unabhängiger Review: L0A

Lieferabschnitt / Phase / letztes Phasengate?:
**L0A / Pilot 0a / nein.** Gemäss Handoff ist L0A ausdrücklich der enge Pilotumfang
`AC0A-01`–`AC0A-04`; es ist nicht die letzte Lieferung einer regulären Phase. Das
kumulative Phasengate sowie Safari-/Browser-/Web-Image-Pflichten sind daher nicht
Gegenstand dieses Reviews.

Implementierungsagent: Codex
Prüfagent: OpenCode; tatsächliche Provider-/Modellkonfiguration:
**direkter DeepSeek-Provider, `deepseek/deepseek-flash` (Anzeige: „DeepSeek V4.1
Flash 1017“), OpenCode v2.0.28.** Der frühere OpenRouter-Kreditfehler wurde nicht
verwendet und ist kein Befund über diesen Lauf.

BASE_SHA:
`865c99bab6ccea4ca3900d897381fa3a2a41fe39`

CANDIDATE_SHA (durch git überprüft):
`a36bca9e6a71bcd1a15c8f5577d97a74b11b9fc1`

Prüfumgebung / Toolchain:
- Sauberer, separater Worktree `/private/tmp/interlis-java-L0A-review-a36bca9`.
- `git rev-parse HEAD` → `a36bca9e6a71bcd1a15c8f5577d97a74b11b9fc1`;
  `git status --short` → **leer** (0 Zeilen), auch nach allen Prüfungen.
- `git diff --stat BASE..CANDIDATE` → 17 Dateien, 646 Insertions, 92 Deletions.
- `git diff --check BASE..CANDIDATE` → Exit 0.
- JAVA_HOME: `/Users/stefan/.sdkman/candidates/java/25.0.3-tem`
  (Temurin 25.0.3+9); Gradle-Wrapper 9.8.1.
- Node: `/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node`
  (`v24.22.0`).
- BASE wurde für die RED-Reproduktion zusätzlich als temporärer Archive-Checkout
  unter `/tmp/interlis-java-L0A-review-base` entpackt. Dort wurde ausschliesslich
  die bereits committete Testklasse kopiert, um den Vorher-Fehler zu prüfen; der
  Kandidaten-Worktree blieb unverändert.
- Eigene Gegenprobe-Quellen und Rohlogs liegen ausschliesslich unter
  `build/review/` des Review-Worktrees bzw. `build/reports/g2/L0A-review/`.
  Produktquellen, Tests, Builddateien und Abnahmekriterien des Kandidaten wurden
  nicht verändert.

## Kriterienprüfung

| Kriterium-ID | Erfüllt / verletzt / nicht prüfbar | Eigener Test-/Code-/CI-Beleg | Finding-ID |
|---|---|---|---|
| **AC0A-01** Vorher-Nachweis: offener äusserer Kommentar nach abgeschlossenem innerem Kommentar wird in 2.3 und 2.4 reproduziert; mindestens ein neuer Negativtest zeigt vor dem Fix tatsächlich RED. | **erfüllt** | Committete Testklasse in temporärem BASE-Checkout ausgeführt: `:interlis-compiler:test --tests '*NestedBlockCommentContractTest' --rerun-tasks` → Exit 1, **6 Tests, 4 Failures**, u. a. `openOuterCommentAfterClosedInnerCommentNeverProducesCompiledSchema [1] 2.3/[2] 2.4` mit „expected Rejected but was **Succeeded**“. Eigene Lexer-Gegenprobe auf BASE: **64 Failures**, darunter `expected Rejected, got Succeeded` für 2-, 3- und 4-stufig ungeschlossene äussere Kommentare sowie für `/*/**/ `. Auf dem Kandidaten: siehe AC0A-02 und Gegenprobe. | — |
| **AC0A-02** Einfache und verschachtelte gültige Kommentare bleiben verlustfrei; offener äusserer/innerer Kommentar wird in 2.3/2.4 korrekt mit Syntaxdiagnostik behandelt; STRICT/EDITOR, Quellrekonstruktion/UTF-16-Bereiche und Compile-Gate korrekt. | **erfüllt** | Neue `NestedBlockCommentContractTest`: 6/6 Invocations grün (XML unten); bestehender `LexicalRecoveryContractTest`: 2/2 unverändert grün. Eigene Java-Gegenprobe: **328 Checks, 0 Failures** auf dem Kandidaten (Validfälle inkl. 3-/4-facher Verschachtelung, `/*/`-Nahtstellen, Emoji/LF/CRLF, exakte Trivia-Ranges/Rekonstruktion, folgende Klasse, Schema-Symbol; Ungültigfälle in beiden Modi/Versionen mit `ILI-SYN-0002`/`ERROR`/`PARSING` am Original-Öffner, keine COMMENT-Trivia, `Rejected` + `SOURCE_ERRORS`, kein `Failed`/`Cancelled`). Zusätzliche CLI-Fälle: ungeschlossener 3-stufiger Kommentar in 2.3 und 2.4 → Exit 1, `SOURCE_ERRORS`, `ILI-SYN-0002`, StartOffset 61; gültiger 4-stufiger Kommentar → Exit 0. | — |
| **AC0A-03** Relevante JUnit-/Conformance-/Portabilitätsprüfungen dokumentiert und ausgeführt; kein Originalpin, XFAIL oder Golden gelockert. | **erfüllt** | Pflichtkommando aus dem Handoff vollständig mit `--rerun-tasks` ausgeführt: **Exit 0**, `BUILD SUCCESSFUL in 2m 5s`, 49 actionable Tasks ausgeführt. JVM: **706 Tests / 0 Failures / 0 Errors / 0 Skips** (147 Suites; Compiler 621, neue Klasse 6). T2: 593/610 ausgewählt, roh **574 PASS / 18 FAIL / 1 XFAIL / 0 ERROR / 0 XPASS** (Runner-Exit 1), Projekt **574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS** (Exit 0). TeaVM JS **3267** und WasmGC **3267** Fälle, beide `status: passed`, Node v24.22.0. `g2EvidenceTest`: 59 Tests OK; `conformanceWrapperTest`: 20 Tests OK; SDK-Beispiel PASS. Pins/XFAILs siehe unten unverändert. | — |
| **AC0A-04** Sauberer Kandidatencommit, kurzer Bericht, nachprüfbarer Handoff; Reviewer führt eigenen neuen Lexer-Gegenfall aus; aktiver Einstieg unter `docs/g2/`; historische Reviews bleiben historisch. | **erfüllt** | `git status --short` leer; HEAD exakt der Handoff-SHA; `docs/g2/implementation/L0A.md` und `docs/g2/STATUS.md` (`AWAITING_REVIEW`) vorhanden; aktive Verweise in `AGENTS.md`, `README.md`, `docs/spec/README.md`, `docs/spec/13`, `docs/spec/14`; Historie in `docs/evidence/stabilization/g2-completion-plan.md` ausdrücklich als historisch markiert. Eigener, nicht aus Codex’ Tests übernommener Lexer-Gegenfall wurde ausgeführt (3-/4-stufig ungeschlossen und trailing ungeschlossen, plus gültige 4-stufige Variante). | — |

## Eigene Gegenprobe

**Hypothese und fachliches Risiko:**
Der alte Lexer `'/*' (BlockComment | .)*? '*/'` konnte im gewöhnlichen
Zeichen-Zweig `/*` und `*/` konsumieren. Dadurch konnte ein ungeschlossener
äusserer Kommentar am `*/` eines inneren Kommentars enden. Wenn danach wieder
gültiger Quelltext folgt, wird ein fachlich ungültiges Modell akzeptiert. Die
Behauptung von Codex ist, dass die zwei Lookahead-Guards dies in beiden
Sprachversionen und beiden Parse-Modi verhindern, ohne gültige verschachtelte
Kommentare zu verlieren. Die Gegenprobe versucht, sowohl eine verbleibende
Akzeptanzlücke als auch eine Über-Ablehnung gültiger Kommentare zu finden.

**Neu erstellter Testinput / exakte Pfade:**
Eigene Quellen (nicht aus Codex’ Testklasse übernommen), erzeugt im
Review-Worktree:

- `build/review/counterprobe/fixtures/unclosed-3-level-2.3.ili` bzw. `-2.4.ili`:
  ```ili
  INTERLIS 2.3; MODEL F AT "https://example.org" VERSION "1" = /* outer /* middle /* inner */ END F.
  ```
  (2.4 analog; äusserer Öffner bleibt offen, nur der innerste wird geschlossen,
  danach folgt gültiger Modellabschluss.)
- `build/review/counterprobe/fixtures/unclosed-empty-inner-2.3.ili`:
  ```ili
  INTERLIS 2.3; MODEL F AT "https://example.org" VERSION "1" = /*/**/ END F.
  ```
  (`/*/**/` ist ein leerer innerer Kommentar; der äussere bleibt offen.)
- `build/review/counterprobe/fixtures/trailing-unclosed-2.3.ili` bzw. `-2.4.ili`:
  ```ili
  INTERLIS 2.3; MODEL F AT "https://example.org" VERSION "1" = END F. /* outer /* middle /* inner */
  ```
  (ungeschlossener Kommentar nach einem vollständigen Modell.)
- `build/review/counterprobe/fixtures/balanced-4-level-2.3.ili` bzw. `-2.4.ili`:
  ```ili
  INTERLIS 2.3; MODEL F AT "https://example.org" VERSION "1" = /* a /* b /* c /* d */ c2 */ b2 */ a2 */ TOPIC T = CLASS C = END C; END T; END F.
  ```
- `build/review/counterprobe/L0AReviewCounterProbe.java`: eigenständiges
  Java-Programm über die öffentlichen APIs `SyntaxServices`, `CompilerEngines`,
  `SourceText`/`CompilationRequest`. Es prüft zusätzlich 2-, 3- und 4-stufig
  ungeschlossene Kommentare, `* /`-Nahtstellen, gültige leere/verschachtelte
  Kommentare, Emoji, CRLF, exakte COMMENT-Trivia, Rekonstruktion, `ILI-SYN-0002`-
  Phase/Severity/Range, `SOURCE_ERRORS` und die Unterscheidung zu
  `Failed`/`Cancelled`.

**Erwartetes Verhalten:**
Ungeschlossene äussere Kommentare müssen in 2.3 und 2.4 in STRICT und EDITOR
abgelehnt werden, mit Syntaxdiagnostik am ursprünglichen Öffner und ohne
`CompiledSchema`; gültige verschachtelte Kommentare müssen verlustfrei als
COMMENT-Trivia erhalten bleiben und kompilieren.

**Kommando / Exitcode / beobachteter tatsächlicher Output:**

Kandidat:
```sh
CP=$(ls interlis-cli/build/install/interlis-cli/lib/*.jar | tr '\n' ':')
javac -cp "$CP" -d build/review/counterprobe/classes \
  build/review/counterprobe/L0AReviewCounterProbe.java
java -cp "build/review/counterprobe/classes:$CP" L0AReviewCounterProbe
# Exit 0
# COUNTERPROBE checks=328 failures=0
```

BASE (temporärer Checkout, gleiche Probe):
```sh
CP=$(ls /tmp/interlis-java-L0A-review-base/interlis-cli/build/install/interlis-cli/lib/*.jar | tr '\n' ':')
javac -cp "$CP" -d build/review/counterprobe/base-classes \
  build/review/counterprobe/L0AReviewCounterProbe.java
java -cp "build/review/counterprobe/base-classes:$CP" L0AReviewCounterProbe
# Exit 1
# COUNTERPROBE checks=312 failures=64
# u. a.:
# FAIL 2.3/unclosed-3-level/compile: expected Rejected, got Succeeded
# FAIL 2.4/unclosed-3-level/compile: expected Rejected, got Succeeded
# FAIL 2.3/unclosed-empty-inner/compile: expected Rejected, got Succeeded
# FAIL 2.4/unclosed-empty-inner/compile: expected Rejected, got Succeeded
```

CLI-End-to-End auf exakt den oben genannten Quellen:
```sh
interlis-cli/build/install/interlis-cli/bin/interlis-cli check --json \
  build/review/counterprobe/fixtures/unclosed-3-level-2.3.ili
# Kandidat: Exit 1
# {"status":"rejected: RejectionSummary[reasons=[SOURCE_ERRORS],...]",
#  "diagnostics":[{"code":"ILI-SYN-0002","severity":"ERROR","phase":"PARSING",
#  "range":{"startOffset":61,"endOffset":62},...}]}
# BASE: Exit 0, {"status":"ok","diagnostics":[]}

interlis-cli/build/install/interlis-cli/bin/interlis-cli check --json \
  build/review/counterprobe/fixtures/trailing-unclosed-2.4.ili
# Kandidat: Exit 1, SOURCE_ERRORS, ILI-SYN-0004/PARSING (StartOffset 68)
# BASE: Exit 0, {"status":"ok","diagnostics":[]}

interlis-cli/build/install/interlis-cli/bin/interlis-cli check --json \
  build/review/counterprobe/fixtures/balanced-4-level-2.3.ili
# Kandidat: Exit 0, {"status":"ok","diagnostics":[]}
```

Reproduktionslog der Testklasse auf BASE:
```sh
cp interlis-compiler/src/test/java/guru/interlis/compiler/NestedBlockCommentContractTest.java \
  /tmp/interlis-java-L0A-review-base/interlis-compiler/src/test/java/guru/interlis/compiler/
cd /tmp/interlis-java-L0A-review-base
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
  ./gradlew :interlis-compiler:test --tests '*NestedBlockCommentContractTest' --rerun-tasks
# Exit 1
# 6 tests completed, 4 failed
# [1] 2.3 / [2] 2.4: openOuter...: expected Rejected but was Succeeded
# [1] 2.3 / [2] 2.4: unfinishedComments...: expected false but was true
```

**Ergebnis und Schlussfolgerung:**
Der Kandidat besteht die eigene Gegenprobe mit **328/328 Checks** und
**0 Failures**. Auf BASE sind **64** Gegenproben-Checks rot; die zentrale
3-stufige Variante, `/*/**/` und die trailing-Variante werden von BASE als
`ok`/`Succeeded` akzeptiert, vom Kandidaten dagegen mit `SOURCE_ERRORS` und
`ILI-SYN-0002` bzw. `ILI-SYN-0004` abgelehnt. Gültige tief verschachtelte
Kommentare werden weiterhin akzeptiert. Damit ist die konkrete fachliche
Behauptung unabhängig bestätigt und der Vorher-Fehler reproduziert.

## Pflichtprüfungen

**Ausgeführte Kommandos, tatsächlicher Exitcode, Tests/Fehler/Skips:**

1. Exakt das Handoff-Kommando, vollständig und frisch:
   ```sh
   JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
   ./gradlew check conformanceCheck \
     :interlis-wasm-teavm:jsContractTest :interlis-wasm-teavm:wasmContractTest \
     -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
     --rerun-tasks
   ```
   Exit **0**; `BUILD SUCCESSFUL in 2m 5s`; 49 actionable Tasks ausgeführt.
   Rohlog: `build/reports/g2/L0A-review/verification.log`.

2. JVM-Aggregation aus `**/build/test-results/test/TEST-*.xml` (Attribute
   `tests`/`failures`/`errors`/`skipped`):
   **147 Suites, 706 Tests, 0 Failures, 0 Errors, 0 Skips**.
   Compiler: **621 Tests**; `NestedBlockCommentContractTest`: **6 Tests, 0/0/0**
   (`interlis-compiler/build/test-results/test/TEST-guru.interlis.compiler.NestedBlockCommentContractTest.xml`,
   Suite-Timestamp `2026-10-29T11:44:11.963Z`).
   `LexicalRecoveryContractTest`: 2 Tests, 0 Failures (unveränderte Bestandsdatei).

3. `g2EvidenceTest`: im `check`-Lauf ausgeführt; **59 Tests, OK**.
   Rohlog im `verification.log`; der letzte Lauf erfolgte nach der
   Matrixzähler-Korrektur.

4. `conformanceWrapperTest`: **20 Tests, OK** (im `check`-Lauf).

5. SDK-/JVM-Beispiel: `:interlis-compiler:sdkExampleTest` PASS,
   `SDK PASS ... models=1` (im `verification.log`).

6. TeaVM/Node:
   - `interlis-wasm-teavm/build/js-contract-result.json`:
     `status: passed`, `runtime: teavm-js`, `stabilizationCases: 3267`,
     `node: v24.22.0`.
   - `interlis-wasm-teavm/build/wasm-contract-result.json`:
     `status: passed`, `runtime: teavm-wasm-gc`, `stabilizationCases: 3267`,
     `node: v24.22.0`.
   - Die zehn neuen Kommentar-Varianten (5 Fälle × 2 Versionen) sind in
     `interlis-wasm-teavm/stabilization-contract.cjs` als `nestedCommentCases`
     enthalten; die Zählung wurde um genau diese 10 erhöht.
   - Nicht prüfende Tasks `disasmWasmGC`, `emscriptenStubWasmGC`,
     `emscriptenWasmGC` sind konfigurationsbedingt **SKIPPED**; es wurde kein
     JUnit- oder portabler Vertragstest übersprungen (JUnit-Skips = 0,
     JS-/Wasm-Status `passed`).

**Rohberichte / CI-Artefakte und Bindung an CANDIDATE_SHA:**
Alle Artefakte wurden im Review-Worktree mit nachgewiesenem
`HEAD = a36bca9e6a71bcd1a15c8f5577d97a74b11b9fc1` und leerem
`git status --short` erzeugt. Pfade:
`build/reports/g2/L0A-review/verification.log`,
`build/conformance/{summary.json,report.json,report.junit.xml,conformance.log,normative-assessment.json,run-config.json}`,
`build/reports/conformance/{conformance-summary.json,conformance-log.txt}`,
`interlis-*/build/test-results/test/TEST-*.xml`,
`interlis-wasm-teavm/build/{js-contract-result.json,wasm-contract-result.json}`,
`build/review/counterprobe/{candidate.log,base.log,base-red.log,cli-counterprobe.log,cli-empty-inner.log,cli-trailing.log,cli-trailing-valid.log}`.
Die Test-XMLs tragen frische Suite-Timestamps vom Lauf (`2026-10-29T11:44Z`),
die Conformance-/Portable-Artefakte stammen aus demselben Lauf
(`2026-10-29T12:44–12:46 +0100`). `run-config.json` zeigt den Kandidaten-CLI-Pfad
`/private/tmp/interlis-java-L0A-review-a36bca9/interlis-cli/...`.

**Nicht ausgeführte fällige Prüfungen mit Grund:**
Keine für L0A fällige Prüfung wurde ausgelassen. `checkBrowser`/Safari,
Web Image und die vollständige kumulative Phasenmatrix sind laut
`EXECUTION_PLAN.md` §2.1 und Reviewprompt §5 ausdrücklich **kein L0A-Pilotgate**
und werden spätestens mit `L0B-3` fällig. `checkPortable` als solches war nicht
separat nötig, weil dessen Bestandteile (`check` + JS- + Wasm-ContractTest) im
Pflichtkommando vollständig enthalten waren.

**Conformance-Fallmenge; rohe und projektspezifische Bilanz:**
- Auswahl: **593 von 610** Fällen (Filter `^(ili23|ili24)\.`),
  **17 ausgeschlossen** (INTERLIS-1.0-Profil), Suite-Commit
  `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`, Archiv-SHA-256
  `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe`.
- **Rohe Suitebewertung:** Runner-Exit **1**;
  **574 PASS / 18 FAIL / 1 XFAIL / 0 ERROR / 0 XPASS**
  (`build/conformance/summary.json`, `report.json`, `report.junit.xml`,
  `conformance.log`).
- **Projektbewertung:** **574 PASS / 19 XFAIL / 0 FAIL / 0 ERROR / 0 XPASS**,
  Gesamt-PASS (`build/conformance/normative-assessment.json`; kombinierte
  Wrapper-Sicht `build/reports/conformance/conformance-summary.json` mit
  `selection.excludedVersions=['1.0']`).
- Die 18 rohen FAIL-IDs sind exakt die 18 Einträge aus
  `docs/evidence/conformance/normative-expectations.json`; jede
  projektspezifische Bewertung ist `PASS`. Der separate INTERLIS-1-Profilfall
  bleibt als 1 XFAIL sichtbar. Es gab keinen XPASS. Roh- und Projektbilanz
  wurden getrennt geprüft.

**Norm-/Suite-/XFAIL-/Baseline-/Quellpin-Unverändertheit:**
- `docs/evidence/conformance/*`, `docs/evidence/conformance/baseline.json`,
  `docs/evidence/conformance/normative-expectations.json` und
  `tools/reference/conformance_check.py` sind im Diff **nicht enthalten**;
  Suite-Commit und Archiv-SHA sind unverändert.
- Die 18 normativen Ausnahmen und der INTERLIS-1-Fall bleiben unverändert;
  es wurde keine neue Freigabe erteilt und kein XFAIL/XPASS umklassiert.
- Die einzigen Inventaränderungen sind mechanisch an die geänderte Quelle
  gebunden: Lexer-Pin, Zeilen/Offsets, zwei Hashreferenzen und der
  Occurrence-Zähler (1218 → 1225). Ich habe die Hashes unabhängig geprüft:
  Grammatik-Inventar-Hash, Quellinventar-Hash, Lexer-Pin sowie die Pins der
  geänderten Spec-Dateien 13/14 stimmen mit den Dateien überein; das
  Grammatik-Inventar ist mit der Matrix konsistent (321 Produktionen, 1225
  Occurrences, 125 Parser-/196 Lexer-Produktionen).
- `tools/reference/test_g2_grammar_inventory.py` erhöht den erwarteten Zähler
  auf 1225 und prüft zusätzlich die Gleichheit mit der Matrix. Das ist eine
  durch die Grammatikänderung erzwungene, gegen die Matrix abgesicherte
  Erwartungsanpassung, keine Lockerung eines Goldens; alle fachlichen
  Erwartungen bleiben `PLANNED_NOT_ACCEPTED`.
- Zusätzlich unabhängig geprüft: alle drei geänderten JSON-Dateien parsen
  (`python3` `json.load`), und
  `node --check interlis-wasm-teavm/stabilization-contract.cjs` → Exit 0.

**Python-Migrations-/Phaseninvarianten soweit relevant:**
Nicht fällig für L0A; die Python-Entfernung ist Phase 0b (`L0B-1`–`L0B-3`).
Die geänderte Datei `tools/reference/test_g2_grammar_inventory.py` ist eine
Evidenztest-Anpassung, keine Python-Migration. `python3` wird von `check`/
`conformanceWrapperTest`/`g2EvidenceTest`/`conformanceCheck` weiterhin genutzt;
das ist für den Pilotstand erwartet und kein L0A-Verstoss.

**Kumulative Phasenregression, wenn fällig:**
Nicht fällig (L0A ist kein Phasenabschluss). Die für L0A relevanten
T1-/T2-/T3-Verträge wurden dennoch vollständig ausgeführt.

## Fundstellen

- **Produktionsfix (einzige Semantikänderung):**
  `interlis-compiler/src/main/antlr/InterlisLexer.g4:240-245`. Der gewöhnliche
  Zeichen-Zweig der rekursiven `BlockComment`-Regel konsumiert durch die zwei
  Lookahead-Guards weder `/*` noch `*/`; der rekursive Zweig bleibt
  `BlockComment`. Keine Änderung an Parser, GraphBuilder, SyntaxTreeBuilder,
  Gate oder öffentlichen Signaturen (Diff geprüft).
- **Committete Vertragstests:**
  `interlis-compiler/src/test/java/guru/interlis/compiler/NestedBlockCommentContractTest.java:25-125`
  (drei parameterisierte Methoden, 2.3/2.4, LF/CRLF, Emoji, STRICT/EDITOR,
  Trivia-/Token-Ranges, Rekonstruktion, Compile-Gate). Bestehender
  `LexicalRecoveryContractTest.java` ist unverändert.
- **Portable Gegenfälle:**
  `interlis-wasm-teavm/stabilization-contract.cjs:34-48` (fünf Kommentarfälle ×
  zwei Versionen) und die Zähleraddition in der Rückgabe (Zeile 1738).
- **Evidenz-/Inventaranpassung:**
  `tools/reference/test_g2_grammar_inventory.py:25-28` (1218 → 1225, zusätzliche
  Matrix-Gleichheitsassertion); `docs/evidence/stabilization/g2-*-inventory.json`
  und `g2-rule-matrix.json` mechanisch aktualisiert.
- **Aktiver Einstieg/Status:**
  `docs/g2/STATUS.md`, `docs/g2/implementation/L0A.md`; historische Einordnung
  in `docs/evidence/stabilization/g2-completion-plan.md`.

## Findings

Keine offenen BLOCKER- oder MAJOR-Findings. Die vier Kriterien `AC0A-01` bis
`AC0A-04` sind am Kandidaten-SHA belegt.

Beobachtungen ohne Abnahmewirkung:
- Der Pflichtlauf meldet neben `disasmWasmGC` auch
  `emscriptenStubWasmGC`/`emscriptenWasmGC` als SKIPPED. Das sind
  Nicht-Test-Buildtasks; die geforderten JUnit- und portablen Vertragstests
  liefen vollständig (0 Skips, JS/Wasm `passed`).
- Der Implementierungsbericht nennt für den RED-Lauf der ursprünglichen
  Einzelmethode 2 Failures; die von mir auf BASE ausgeführte **ganze neue
  Testklasse** hat erwartungsgemäss 4 Failures (zwei negative Methoden × zwei
  Versionen). Das ist kein Widerspruch, sondern der breitere Klassenlauf.

## Urteil

**PASS**

Begründung:
Alle vier L0A-Kriterien sind am unveränderten Kandidaten
`a36bca9e6a71bcd1a15c8f5577d97a74b11b9fc1` erfüllt. Der Vorher-Fehler ist mit
der committeten Testklasse auf BASE tatsächlich RED (6 Tests, 4 Failures,
`Succeeded` statt `Rejected`) und mit einer eigenen, nicht übernommenen
Lexer-Gegenprobe auf BASE (64 Failures; 2-/3-/4-stufige, `/*/**/`- und trailing
ungeschlossene Kommentare werden akzeptiert) reproduziert. Auf dem Kandidaten
sind die eigenen 328 Checks grün; ungültige Kommentare werden in 2.3/2.4,
STRICT/EDITOR mit `ILI-SYN-0002`/`SOURCE_ERRORS` abgelehnt, gültige
verschachtelte Kommentare bleiben verlustfrei und kompilieren. Der vollständige
Pflichtlauf ist mit Exit 0 belegt (706 JVM-Tests ohne Fehler/Skips, 593
T2-Fälle mit getrennter Roh-/Projektbilanz, je 3267 TeaVM-JS/WasmGC-Fälle,
59 Evidenz- und 20 Wrapper-Tests, SDK PASS). Pins, XFAILs, Baseline und
normative Ausnahmen sind unverändert; die Inventaränderungen sind mechanisch
und hashkonsistent.

Nächster konkreter Schritt:
Gemäss `EXECUTION_PLAN.md` darf nach diesem `L0A: PASS` mit `L0B-1`
(Conformance/normative Sonderfälle nach Java, Python-Abbau) begonnen werden.
Dieser Review beginnt selbst keine weitere Lieferung und ändert den Kandidaten
nicht.
