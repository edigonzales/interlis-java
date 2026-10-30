# Unabhängiger Review: L2-1

Lieferabschnitt / Phase / letztes Phasengate?:
**L2-1 / Phase 2 / NEIN.** Dies ist nicht das letzte Gate einer Phase. Geprüft
wurden **AC2-01 und AC2-02 einzeln**. Es wird kein Phase-2-PASS und kein
kumulativer AC2-01–AC2-07-PASS behauptet; L2-2/L2-3 wurden nicht begonnen.
Der Abschnitt ist gemäss Handoff ausdrücklich nicht die letzte Lieferung einer
Phase.

Implementierungsagent: Codex

Prüfagent: OpenCode; tatsächliche Provider-/Modellkonfiguration:
- Startkonfiguration gemäss Auftrag: **OpenCode v2.0.28**, `--model
  deepseek/deepseek-flash`, **DeepSeek DIREKT**, UI **DeepSeek V4.1 Flash
  1017**, **kein OpenRouter**.
- Tatsächlich beobachteter Prozess:
  `/Users/stefan/.opencode/bin/opencode run --standalone --model
  deepseek/deepseek-flash --agent build ...`. `opencode --version` meldet
  `2.0.28`. In der Prozessumgebung ist kein OpenRouter-Endpunkt gesetzt.
  Eine Modellselbstauskunft allein ist kein Identitätsnachweis; die Angabe
  stützt sich auf die tatsächliche Prozesskonfiguration und die vorgegebene
  Startkonfiguration. Es wurden keine Zugangsdaten gelesen oder ausgegeben.

BASE_SHA: `81ad01919c45165ad20ecb86986ff5f0475cc44c`
CANDIDATE_SHA (durch git überprüft): `4b7c31e4b4b28c7a340f21be1988f152c865c348`

- `git rev-parse HEAD` im eigenen Review-Worktree:
  `4b7c31e4b4b28c7a340f21be1988f152c865c348`.
- `git status --porcelain` **vor und nach** der Prüfung: **leer**.
- Elterncommit des Kandidaten: `81ad019…` = BASE_SHA; BASE ist Vorfahre des
  Kandidaten.
- `git diff --shortstat BASE..CANDIDATE`: **19 Dateien, +1179/−531**.
- `git diff --check BASE..CANDIDATE`: **Exit 0** (keine Whitespace-Fehler).
- Der vollständige relevante Diff wurde gelesen: Produktionscode
  `FunctionBindings.java`, `GraphBuilder.java`, `AttributePathType.java`
  (nur Javadoc), die vier geänderten/neuen Compilertestklassen, der portable
  Vertrag, die Spezifikations-/Status-/Inventardateien.
- Der Reviewer hat **keine** Produkt-, Test-, Build- oder Abnahmequelle
  geändert. Eigene Proben und Logs liegen ausschliesslich unter dem
  ignorierten `build/` des Review-Worktrees.

Prüfumgebung / Toolchain:
- Eigener sauberer Worktree:
  `/private/tmp/interlis-java-L2-1-review-4b7c31e`, detached auf
  CANDIDATE_SHA.
- macOS **15.8.1**, arm64; Temurin **25.0.3+9**
  (`/Users/stefan/.sdkman/candidates/java/25.0.3-tem`); Gradle-Wrapper
  **9.8.1**; Node **v24.22.0**
  (`/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node`).
- Pflichtlauf im OS-gesperrten Python-Umfeld über
  `tools/verification/without-python.cjs`: privater PATH,
  `sandbox-exec`, geerbte `process-exec`-Sperre, `--no-daemon`.
  Nachweise in `build/reports/python-free/environment.json`:
  `python/python3/pip/pip3/pypy/uv` → **ENOENT**; absolute
  `/usr/bin/python3`, `/usr/bin/pip3` → **EPERM**.
- Kein Python/pip/uv, kein OpenRouter, kein Push/Merge/Release durch den
  Reviewer.

## Kriterienprüfung

| Kriterium-ID | Erfüllt / verletzt / nicht prüfbar | Eigener Test-/Code-/CI-Beleg | Finding-ID |
|---|---|---|---|
| **AC2-01** – Objekt-/Attribut-/Rollenpfade inklusive Indizes, Mengen und Spezialkontexten liefern korrekte gebundene Ausdruckstypen und Referenzen. | **Erfüllt für den geprüften Kernumfang; offener MAJOR-Randbefund F1, Beobachtungen O1/O2.** | Frischer Compilerlauf: **668** Compiler-Testfälle, 0 Failures/Errors/Skips; darunter `DependentFunctionArgumentsContractTest` **10/10**, `ConstraintPathBindingContractTest` **13/13** (Indizes, Mengen, Rollen, SET/WHERE/UNIQUE/EXISTENCE), `AreaFunctionContractTest` **7/7**. Eigene Probe A–E (2.3/2.4): per-Call-Ergebnistypen, Klassenzielidentitäten, `ClassAttribute` im selben Snapshot, gebundene `>>label`-Ziele und Definitionsqueries; **98 Checks PASS**. Portable JS/WasmGC je **3423** PASS. Fresh/Reuse mit drei Importrevisionen und beiden Dateireihenfolgen ist in der neuen Testmethode `importedDependentContextsFollowRevisionsAndMatchFreshAnalysisInEitherFileOrder` enthalten. F1 betrifft den `UNDEFINED`-Actual und ist vor einem PASS zu klären. | **F1** (O1/O2) |
| **AC2-02** – Alle relevanten 21 Builtins, Signaturen/`UNDEFINED`-Semantik und Constraintfamilien (inkl. SET/WHERE, UNIQUE, EXISTENCE, externe Constraints und Namensvererbung) öffentlich korrekt und mit Gegenfällen geprüft; externe Funktionen nicht ausgeführt. | **Nicht abschliessbar – BLOCKED (NORM_DECISION).** Die technisch prüfbaren Teile sind belegt; die Signaturgrundlage und zwei Constraint-Profile hängen an **drei unbeantworteten Nutzerentscheidungen**. | `BuiltinSignatureFoundationContractTest` **2/2**: echte 13 (2.3) bzw. 21 (2.4) Aufrufe, gebundene Funktionsziele, tatsächliche `PredefinedOrigin`-Signaturen, falsche Argumentanzahl für jeden Namen. `UndefinedFunctionContractTest` bleibt grün (MANDATORY/UNDEFINED). `ConstraintPathBindingContractTest` **13/13**, `AreaFunctionContractTest` **7/7**, `ExistenceValueFamiliesContractTest` grün. Externe Funktionen werden nicht ausgeführt (reine Bindung; kein Evaluator im Diff). Offene Normfragen N1–N3. | **N1, N2, N3** |

### Technische Einzelprüfung der fünf Produktänderungen

| # | Behauptung | Ergebnis | Beleg |
|---|---|---|---|
| 1 | `FunctionBindings.arguments` bindet bekannte Argumente iterativ in OF-Abhängigkeitsreihenfolge, erhält die Quellreihenfolge der veröffentlichten Kinder, ohne globalen aktuellen Aufrufzustand; unqualifizierte Konstanten werden im tatsächlichen Owner gebunden, Klammern/Ketten/spätere Argumente eingeschlossen; unbekannte Funktionen nutzen die normale Fehlerbindung. | **Erfüllt** für die belegten Fälle; Randfall O1. | Code: lokale `values`/`active`/`chain`, `List.copyOf` in Indexreihenfolge; `checkWork()` in jedem Schritt; `dependency()` folgt `Argument.index()`. Tests: `aDependencyOnALaterActualArgument…`, `chainedAttributeArguments…`, `(>>label)`, 100-Vorwärts-Kette. Eigene Probe A/B. Unbekannte Funktion manuell geprüft: `F.missing(>>label)` → `SOURCE_ERRORS`, `ILI-SEM-0001` (Exit 1), kein internes Failure. |
| 2 | Ein gescheitertes Owner-Argument propagiert die ursprünglichen Diagnoseursachen in abhängige `ErrorExpression`, kein lexikalischer Scheinersatz. | **Erfüllt.** | `aFailedOwnerArgumentDoesNotPublishALexicallyBoundReplacementConstant` (Owner-Fehler zuerst) und eigene Probe C (späteres Owner-Argument fehlerhaft): beide Kinder `ErrorExpression`, gleiche `causes`, genau ein `ILI-SEM-0001`. Am BASE-SHA zeigt Probe C den alten lexikalischen Ersatz (Kind 0 war kein `ErrorExpression`). |
| 3 | `ATTRIBUTE OF` einer klassenwertigen Pfadangabe kann genau denselben tatsächlichen klassenwertigen Ausdruck als Owner erfüllen; struktureller Vergleich gebundener Identitäten; explizite Klassenziele bleiben exakt, keine Subklasse. | **Erfüllt** für die belegten Fälle. | `aClassValuedAttributeSuppliesItsDeclaredDependentAttributeArgument`, `explicitAttributeOwnersRemainExact…` (Base/Child, `>>C->surface`), `sameBoundExpression`/`sameExpression`. Eigene Probe A (zwei verschiedene Klassenziele, keine Kreuzkontamination) und B (`cls` als tatsächlicher Owner). |
| 4 | Abhängige `ATTRIBUTE`-Rückgabetypen werden pro erfolgreichem Aufruf gebunden; formaler Funktionstyp und Argumentbezug bleiben unverändert; konstante Ziele `Viewable`, klassenwertige tatsächliche Ausdrücke `ClassAttribute(ExpressionId)` im selben Snapshot; keine externe Funktionsausführung. | **Überwiegend erfüllt; MAJOR-Randbefund F1 für `UNDEFINED`.** | `dependentReturnTypesAreBoundToEachInvocation…` (2.3/2.4, `>Data` und `cls`, Deklaration bleibt `Argument(pick,0)`, Ergebnis neuer Typ). Probe A bestätigt zwei unterschiedliche per-Call-Typen. **F1:** `F.pick(UNDEFINED)` ist `RESOLVED`, `typeId == pick.returnType()`, Owner `Argument[pick,0]`; damit kein eigener gebundener Typ, entgegen der neuen Dokumentation „jeder erfolgreiche Aufruf“. |
| 5 | Vollständig aufgelöste inkompatible ENUM-/FORMAT-/Geometrie-/Klassenwerttypen bei EXISTENCE sind `SOURCE_ERRORS`, nicht `INCOMPLETE_SUPPORT`; Generic/Deferred/Pending werden nicht maskiert. | **Erfüllt.** | Norm: 2.3 §2.12/PDF59 und 2.4 §3.12/PDF77 (Bedingungsattribut muss mit dem anderen Attribut kompatibel sein). `incompatibleResolvedExistenceEnumerationsAreSourceErrors` (ENUM). Eigene Probe D (FORMAT) und E (Geometrie POLYLINE/SURFACE) in 2.3/2.4: nur `SOURCE_ERRORS`, `ILI-SEM-0005`, kein `INCOMPLETE_SUPPORT`, keine Failure-ID. Zusätzlich manuell: Klassenwert `CLASS RESTRICTION(Data1)` vs. `Data2` → `SOURCE_ERRORS`/`ILI-SEM-0005`. Code: `familyPending` nur noch `GENERIC`, `DEFERRED`, `PENDING`. |
| 6 | Zehn `DependentFunctionArgumentsContractTest`-Methoden, zwei `BuiltinSignatureFoundationContractTest`-Methoden, 18 neue portable Quellenfälle; öffentliche Ziele/Typen/Definitionsranges (UTF-16/Revision), Ketten/100-Vorwärtsargumente, Fehlerpropagation, Importrevisionen/FreshReuse/Dateireihenfolge. | **Erfüllt.** | JUnit-XML: `DependentFunctionArgumentsContractTest tests="10" skipped="0" failures="0" errors="0"`, `BuiltinSignatureFoundationContractTest tests="2" skipped="0" failures="0" errors="0"`. Compiler-`@Test`-Methoden **662** (BASE 650), JUnit-Testfälle **668** (drei `@ParameterizedTest` expandieren zusätzlich). Portable `stabilizationCases=3423` je Backend (18 neue Fälle; Ausgangsstand 3405 gemäss Handoff/Phase-1-Nachweis). |

## Eigene Gegenprobe

**Hypothese und fachliches Risiko:**
Die L2-1-Implementierung könnte nur die neuen Tests befrieden, aber öffentlich
weiterhin (a) einen globalen aktuellen Aufrufzustand oder eine falsche
Ergebniswiederverwendung zeigen, (b) bei einem fehlgeschlagenen späteren
Owner-Argument einen lexikalisch gebundenen Ersatz veröffentlichen, (c) alle
abhängigen Rückgabetypen an die Deklaration binden statt pro Aufruf, oder
(d) aufgelöste FORMAT-/Geometrie-Inkompatibilitäten bei EXISTENCE weiterhin
als `INCOMPLETE_SUPPORT` ausgeben. Die Probe versucht genau diese Behauptungen
zu widerlegen, nicht vorhandene Tests umzubenennen.

**Neu erstellter Testinput / exakter Pfad oder vollständige Quelle:**
`build/review/L2-1/probe/CounterProbe.java` (nur im ignorierten `build/` des
Review-Worktrees; keine Produkt-/Test-/Builddatei geändert). Die Quelle nutzt
ausschliesslich die öffentliche Compiler-API und wurde unabhängig formuliert.
Fünf Abschnitte je 2.3/2.4:

- **A – per-Call-Ergebnisbindung:** zwei Aufrufe `F.pick(a)` und `F.pick(b)`
  mit verschiedenen klassenwertigen Pfaden in einer Bedingung; erwartet zwei
  verschiedene Ergebnistypen, je `ClassAttribute` auf den **eigenen** Actual,
  keine Kreuzkontamination, beide `check`-Aufrufe aufgelöst.
- **B – tatsächlicher Owner statt Lexik:** `F.check(cls,>>label)` mit
  `cls: CLASS RESTRICTION(Data)` und konkurrierendem lexikalischem
  `Host.label : BOOLEAN`; erwartet Ziel `Data.label`, nicht `Host.label`.
- **C – späteres fehlerhaftes Owner-Argument:** `F.check(>>label,>Missing)`;
  erwartet genau ein `ILI-SEM-0001`, beide Argumente `ErrorExpression` mit
  denselben ursprünglichen `causes`.
- **D – FORMAT-EXISTENCE:** zwei verschiedene `FORMAT BASED ON`-Typen;
  erwartet `SOURCE_ERRORS` + `ILI-SEM-0005`, kein `INCOMPLETE_SUPPORT`.
- **E – Geometrie-EXISTENCE:** `POLYLINE` gegen `SURFACE` mit demselben
  Koordinatenbereich; erwartet `SOURCE_ERRORS` + `ILI-SEM-0005`, kein
  `INCOMPLETE_SUPPORT`.

**Erwartetes Verhalten:**
Wie oben; zusätzlich: veröffentlichte Kinder bleiben in Quellreihenfolge; die
Ergebnistypen und `ClassAttribute.path()`-Ausdrücke liegen im selben Snapshot;
keine interne Ausnahme und keine Failure-ID.

**Kommando / Exitcode / beobachteter tatsächlicher Output:**
```text
/Users/stefan/.sdkman/candidates/java/25.0.3-tem/bin/java \
  -cp 'interlis-cli/build/install/interlis-cli/lib/*' \
  build/review/L2-1/probe/CounterProbe.java
```
Am Kandidaten: **Exit 0**; Log `build/review/L2-1/probe/probe.log`:
```text
REVIEW_PROBE_PASS checks=98
```
Am **BASE_SHA `81ad019…`** wurde dieselbe Probe in einem separaten temporären
Worktree (`/tmp/interlis-java-L2-1-base`) gegen dort frisch gebautes
`:interlis-cli:installDist` ausgeführt: **Exit 1**, Log
`build/review/L2-1/probe/probe-base.log`, **18 Fehlschläge**, unter anderem:
```text
A 2.3/2.4 per-call result binding: expected success ... FUNCTION_ATTRIBUTE
B 2.3/2.4 class-valued path owner: expected success ... FUNCTION_ATTRIBUTE
C 2.3/2.4 dependent actual is an error expression
D 2.3/2.4 only SOURCE_ERRORS, got [INCOMPLETE_SUPPORT]
E 2.3/2.4 only SOURCE_ERRORS, got [INCOMPLETE_SUPPORT] (2.4: [SOURCE_ERRORS, INCOMPLETE_SUPPORT])
```
Der temporäre BASE-Worktree wurde danach wieder entfernt; der
Review-Worktree blieb sauber.

**Ergebnis und Schlussfolgerung:**
Die Probe widerlegt die Risikobehauptungen am Kandidaten nicht: **98 Checks
PASS**. Sie reproduziert am BASE-SHA **18 tatsächliche Vorherfehler** in genau
den korrigierten Bereichen (lexikalische Ownerbindung, fehlender
Fehlerersatz, pauschales `INCOMPLETE_SUPPORT`). Damit ist sie eine echte,
eigenständige Gegenprobe und keine Wiederholung der Produktmethoden. Kein
Finding aus dieser Probe.

**Zusätzliche ausgeführt Randproben (nicht Teil der 98 Checks):**
- **Normprofile A/B/C (nur Beobachtung, keine Entscheidung):**
  `areAreas2(ALL,…)`, `areAreas2(THIS,…)` und `areAreas3(ALL,…)` werden in
  2.4 akzeptiert; `MANDATORY CONSTRAINT INTERLIS.objectCount(ALL)>=0;` wird in
  2.3 und 2.4 akzeptiert; `SET CONSTRAINT n>0;` und
  `SET CONSTRAINT DEFINED(THIS);` werden in 2.3 und 2.4 akzeptiert.
  Quelldateien: `build/review/L2-1/profile/*.ili`; Kommandos:
  `interlis-cli/build/install/interlis-cli/bin/interlis-cli check --json <datei>`.
- **Zyklen:** Selbst- und Mutual-Cycle in abhängigen Formalparametern werden
  mit `SOURCE_ERRORS` und `FUNCTION_ARGUMENT_CYCLE` abgelehnt (kein
  `INTERNAL_FAILURE`), Quelldateien `build/review/L2-1/edges/self-cycle.ili`,
  `mutual-cycle.ili`.
- **Unbekannte Funktion:** `F.missing(>>label)` → `SOURCE_ERRORS`,
  `ILI-SEM-0001`, normale Fehlerbindung.
- **Klassenwert-EXISTENCE:** `CLASS RESTRICTION(Data1)` gegen
  `CLASS RESTRICTION(Data2)` → `SOURCE_ERRORS`, `ILI-SEM-0005`.
- **Typbeobachtung:** `build/review/L2-1/probe/TypeObservation.java`;
  Ausgabe `build/review/L2-1/probe/observation.log`:
  ```text
  2.3/2.4 unrestricted-owner attr=AttributePathType owner=Viewable[Host]
  2.3/2.4 undefined-owner attr=AttributePathType owner=Viewable[Host]
  2.3/2.4 undefined-return pick=AttributePathType owner=Argument[pick,0] state=RESOLVED sameAsDeclaration=true
  ```
  Daraus folgen O1, O2 und F1.

## Pflichtprüfungen

**Frischer Pflichtlauf (genau der vorgegebene Umfang, ohne Python):**
```text
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
tools/verification/without-python.cjs \
./gradlew checkPortable g2EvidenceTest conformanceCheck sdkProcessCheck \
  pythonFreeCheck --continue --rerun-tasks \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node
```
**Exit 0**, `BUILD SUCCESSFUL in 2m 18s`, **58 actionable tasks: 58 executed**
(Log `build/review/L2-1/mandatory-gate.log`). Der Lauf ist über
`build/reports/python-free/environment.json` an
`gitHead=4b7c31e4b4b28c7a340f21be1988f152c865c348`, `dirty=""`, Start
`2026-10-30T02:34:23.715Z`, Ende `2026-10-30T02:36:43.036Z` gebunden.
Automatisch `SKIPPED` waren nur die experimentellen Emscripten-/Disasm-Tasks;
kein Test wurde deaktiviert, kein `-x test` verwendet, kein `@Disabled`.

**JUnit-XML (frisch, selbst aus den XMLs summiert; nicht nur
Zusammenfassungs-JSON):**

| Suite | Dateien | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|---:|
| `interlis-compiler:test` | 128 | **668** | 0 | 0 | 0 |
| `interlis-cli:test` | 3 | 33 | 0 | 0 | 0 |
| `interlis-lsp:test` (inkl. `LspProcessTest`) | 5 | 16 | 0 | 0 | 0 |
| `interlis-model:test` | 6 | 16 | 0 | 0 | 0 |
| `interlis-projection:test` | 3 | 12 | 0 | 0 | 0 |
| `interlis-repository:test` | 6 | 19 | 0 | 0 | 0 |
| `interlis-wasm-bridge:test` | 1 | 3 | 0 | 0 | 0 |
| `interlis-workspace:test` | 2 | 10 | 0 | 0 | 0 |
| **reguläre JVM-Tests gesamt** | | **777** | **0** | **0** | **0** |
| `interlis-cli:evidenceTest` | 4 | **136** | 0 | 0 | 0 |
| `interlis-compiler:g2AssertionTest` | 11 | **15** | 0 | 0 | 0 |
| `interlis-cli:verificationTest` (Host) | 5 | **15** | 0 | 0 | 0 |

Die XML-Zeitstempel liegen zwischen `2026-10-30T02:34:33.234Z` und
`2026-10-30T02:34:57.837Z`, also nach dem Laufstart. Die neuen Klassen sind
vollständig enthalten: `DependentFunctionArgumentsContractTest` 10/10,
`BuiltinSignatureFoundationContractTest` 2/2, `AreaFunctionContractTest` 7/7,
`ConstraintPathBindingContractTest` 13/13. Compiler-`@Test`-Methoden: **662**
(BASE 650); drei `@ParameterizedTest`-Methoden ergeben die 668 XML-Fälle.
`@Disabled`/`@Ignore` in den Testquellen: **0**.

**Portable/Node (kein Browserersatz):**
- `interlis-wasm-teavm/build/js-contract-result.json`:
  `status=passed`, `stabilizationCases=3423`, `runtime=teavm-js`,
  Node v24.22.0.
- `interlis-wasm-teavm/build/wasm-contract-result.json`:
  `status=passed`, `stabilizationCases=3423`, `runtime=teavm-wasm-gc`,
  `nodes=2`, Node v24.22.0.
- Beide Dateien wurden im Pflichtlauf um `03:36` lokal neu geschrieben.
- `checkPortable` enthält `check`, die berührten SDK-/LSP-/CLI-Tests und beide
  Node-Backends (Task-Log `:interlis-compiler:test`, `:interlis-cli:test`,
  `:interlis-lsp:test`, `:interlis-wasm-teavm:jsContractTest`,
  `:interlis-wasm-teavm:wasmContractTest`, `:checkPortable`).

**T2 – Conformance (gepinnt, frisch, Roh und Projekt getrennt):**
- Suite: Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`, SHA-256
  `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe`.
- Filter `^(ili23|ili24)\.`; Fallmenge **610**, ausgewählt **593**,
  ausgeschlossen **17** (nur Version `1.0`, INTERLIS-1-Profil).
- Rohbilanz (`referenceSummary`): **579 PASS / 13 FAIL / 1 XFAIL / 0 XPASS /
  0 ERROR / 593**; `referenceResult=FAIL`, `exitCode=1` (bei rohen FAILs
  erwartet).
- Projektbilanz (`summary`): **579 PASS / 14 XFAIL / 0 FAIL / 0 XPASS /
  0 ERROR / 593**; `overallResult=PASS`.
- **19/19** `normativeAssessments` mit `result=PASS`; `assessmentCaseIds`
  vollständig. Die 19 Assessment-IDs sind gegenüber BASE unverändert.
- `build/reports/conformance/conformance-summary.json` und
  `conformance-log.txt` wurden im Lauf um `03:36` neu geschrieben.

**SDK/CLI/LSP/Pythonfreiheit:**
- `sdkProcessCheck`: `SDK_PROCESS_PASS 9 commands`;
  `build/reports/sdk/sdk-process-comparison.json` mit 9 Kommandos,
  Exitcodes 0/0/0/0/1/1/0/0/2 (erwartete Negativfälle) und SHA-256 der
  Ausgaben; Task erfolgreich.
- `interlis-cli:installDist` wurde **vor** dem Conformance-Lauf (03:34)
  gebaut; es gab keinen gleichzeitigen Distributionsneubau während der
  Conformance.
- Pythonfrei: `git ls-files '*.py'` = **0**;
  `PYTHON_FREE_SOURCE_PASS 1201 inputs`;
  `build/reports/python-free/source-check.json` = `status=passed`.
  Im Pflichtlauf keine Python-/pip-/uv-Ausführung; die OS-Sperre beweist
  ENOENT/EPERM.

**Norm-/Suite-/XFAIL-/Baseline-/Quellpin-Unverändertheit:**
- `docs/evidence/stabilization/g2-norm-inventory.json` ist **nicht** im Diff;
  keine Normzitate/PDF-Seiten/Grammatikpins geändert.
- `docs/evidence/conformance`, Suite-Pin, Baseline, Manifest und die
  Original-/Kontrollquellen sind **nicht** im Diff. Keine XFAIL-/Manifest-
  Änderung, kein CANDIDATE_ONLY zu einer Abnahme umetikettiert.
- `g2-rule-matrix.json`: nur Inventar-Hashes und `testMethods` 650→662;
  `conditions=621`, `requirements=80`, `withCandidate=669` unverändert.
- `g2-source-inventory.json`: nur Quelloffsets sowie die daraus folgenden
  Datei-/Locator-Hashes (u. a. `docs/spec/05/06/16`, `GraphBuilder.java`,
  `predefined-catalogue.json`); keine Normzitate/Statusbedeutungen.
  Die **15** `new Origin.PredefinedOrigin`-Produzenten und **19**
  Profilcalls (`profileCallSites`) sind gegenüber BASE unverändert.
- `g2-test-candidate-index.json`: `testMethods` 662 und Offset-Verschiebungen
  in `AreaFunctionContractTest`; Status bleibt `CANDIDATE_ONLY`.
- `testdata/g2/predefined-catalogue.json`: nur `sourceAnchors`-Offsets/Hashes;
  Dokument-SHA-256 `8ab1c6b5…` (2.3) und `a8fa2679…` (2.4) sowie die Zitate
  unverändert.
- `g2-source-inventory.json`-SHA `8d304e06…` und
  `g2-test-candidate-index.json`-SHA `5356bb1a…` stimmen mit den in
  `g2-rule-matrix.json` eingetragenen Hashes überein.

**Nicht ausgeführte fällige Prüfungen mit Grund:**
- `checkBrowser` mit echter Browsermatrix/echtem Safari und Web Image ist
  **spätestens L2-3-Phasengate** fällig und für L2-1 nicht behauptet. Es wurde
  kein Browser-/Safari-/Web-Image-Erfolg als Nachweis verwendet.
- `releaseCheck`, P3-/P4-/P5-, T4-/Corpus-/Benchmark-Pflichten sind für diesen
  Lieferabschnitt nicht vorgesehen und wurden nicht als Pflicht erfunden.
- Kein kumulatives Phase-2-Gate: L2-1 ist nicht die letzte Lieferung der
  Phase; L2-2/L2-3 sind nicht begonnen.

**Python-Migrations-/Phaseninvarianten soweit relevant:**
- 0 getrackte `.py`; 1201 geprüfte Eingaben; PATH-/EPERM-Nachweise wie oben.
- Keine neue Python-Abhängigkeit, kein Python in den aktiven Prüfwegen.
- Fortgeltende Nutzerentscheidungen aus Phase 0b/1 (Metadatenwiederholungen/
  mehrzeilige Strings, 2.3 OID AS/BASKET OID AS ohne CONTRACTED,
  TYPE MODEL, Exponenten-/CONTINUOUS-/CIRCULAR-Profile,
  `ili23.Attribute23Test.attributeComplete`) sind nicht Gegenstand dieses
  Diffs und wurden nicht verändert; die 19 Assessments bleiben unverändert.

**Kumulative Phasenregression, wenn fällig:**
Nicht fällig. Der Lauf deckt dennoch die vollständige JVM-/Evidenz-/Assertion-/
Host-Matrix und beide Node-Backends ab. Die Phase-2-Abnahme bleibt offen.

## Findings

**N1 / BLOCKER (NORM_DECISION) – `areAreas2`/`areAreas3`-Signatur in 2.4.**
Ort: `BuiltinFunctions.SIGNATURES`, `BuiltinSignatureFoundationContractTest`,
portable `dependentFunctionCases`; aktuelle Profile `areAreas2` und
`areAreas3` je `(OBJECTS, TEXT, TEXT)`.
Normbeleg: Funktionskapitel 2.4 §3.14/PDF87
(`FUNCTION areAreas2 (Objects: OBJECTS OF ANYCLASS; …)`, Beispiel
`SET CONSTRAINT areAreas2 (ALL,"Geometry->Surfaces","Surface");`) gegen
Anhang A/PDF122 (`FUNCTION areAreas2 (Object: OBJECT OF ANYCLASS; …)` und
`areAreas3 (Objects: OBJECTS OF ANYCLASS; …)`).
Eigene Minimalfälle (2.4, `check --json`, Exit 0): `areAreas2(ALL,…)`,
`areAreas2(THIS,…)`, `areAreas3(ALL,…)` werden akzeptiert. ili2c kennt
`areAreas3` nicht und akzeptiert `areAreas2` mit ALL/THIS (Handoff/Evidenz).
Der Kandidat ändert das bestehende Profil nicht und **zementiert** es in den
neuen Tests. Ohne Nutzerentscheidung ist AC2-02 (und AC2-06/L2-3) nicht
abschliessbar. Voraussetzung: Entscheidung, ob die zwei Mengensignaturen
bleiben oder Anhang A exakt umgesetzt wird; danach Tests/Profile anpassen.

**N2 / BLOCKER (NORM_DECISION) – OBJECTS-Parameterfunktionen in
MANDATORY-/Plausibilitätsbedingungen (2.4).**
Ort: `GraphBuilder`-Constraintbindung / `BuiltinFunctions`; keine
Profiländerung im Kandidaten.
Normbeleg: 2.4 §3.12/PDF77, `NORM:2.4:3.12:p77:006`: „Solche Bedingungen
dürfen keine Funktion verwenden, die auf einem Argument (typischerweise auf
dem ersten) eine Menge von Objekten (OBJECTS OF) erwartet.“
Eigener Minimalfall (2.4, `check --json`, Exit 0):
`MANDATORY CONSTRAINT INTERLIS.objectCount(ALL) >= 0;` wird akzeptiert.
2.3 enthält dieses ausdrückliche Verbot nicht; dort ist die Akzeptanz
ebenfalls beobachtet. Ohne Nutzerentscheidung ist AC2-02 nicht abschliessbar.
Voraussetzung: Entscheidung, ob das Kompatibilitätsprofil erhalten bleibt oder
die 2.4-Regel durchgesetzt wird; danach Constraintbindung/Tests anpassen.

**N3 / BLOCKER (NORM_DECISION) – direkter Objektkontext im
SET-Hauptausdruck (2.3 und 2.4).**
Ort: `GraphBuilder.buildConstraint` / `constraintPath`; keine Profiländerung.
Normbeleg: 2.3 §2.12/PDF60: „Da sich solche Konsistenzbedingungen nicht auf
das einzelne Objekt beziehen, können nie die Werte von Attributen angesprochen
werden.“ 2.4 §3.12/PDF79: SET-Bedingungen müssen mindestens eine Funktion mit
`OBJECTS OF`-Argument verwenden; „… können nie die Werte von Attributen
angesprochen werden.“ Der WHERE-Filter bleibt objektbezogen.
Eigene Minimalfälle (2.3 und 2.4, `check --json`, Exit 0):
`SET CONSTRAINT n > 0;` und `SET CONSTRAINT DEFINED(THIS);` werden akzeptiert.
Ohne Nutzerentscheidung ist AC2-02 nicht abschliessbar. Voraussetzung:
Entscheidung, ob direkte Objektwerte im Hauptausdruck abgelehnt und WHERE
erhalten wird; danach Bindung/Tests anpassen.

**F1 / MAJOR (technischer Randbefund, nicht durch N1–N3 gedeckt) –
`F.pick(UNDEFINED)` bindet den abhängigen Rückgabetyp nicht pro Aufruf.**
Ort: `FunctionBindings.returnType` (Rückfall `return function.returnType()`),
`GraphBuilder.functionCallExpression`.
Reproduktion (2.3 und 2.4):
```text
FUNCTION pick(objects:CLASS):ATTRIBUTE OF @objects RESTRICTION(TEXT);
TOPIC T=CLASS Host=label:TEXT;
  MANDATORY CONSTRAINT DEFINED(F.pick(UNDEFINED)); END Host; END T;
```
Beobachtung (`TypeObservation.java`, Exit 0): Der Aufruf ist
`state=RESOLVED`; `pickCall.typeId()` ist **gleich** `pick.returnType()`;
der Typ ist `AttributePathType owner=Argument[pick,0]`. Es entsteht **kein**
eigener gebundener Ergebnistyp (`Viewable` oder `ClassAttribute`), obwohl die
neue Dokumentation in `docs/spec/05`/`06` sagt, jeder erfolgreiche Aufruf
besitze einen eigenen gebundenen Typ. Erwartet: entweder ein
`PENDING`/undefinierter Ergebnistyp oder eine strukturierte Ablehnung, oder
eine präzisierte Dokumentation plus Test. Dieser Fall ist nicht durch die drei
Nutzerfragen abgedeckt. Nacharbeit vor einem PASS erforderlich; er allein
begründet noch kein normatives Urteil.

**O1 / Beobachtung ohne Abnahmefinding – unqualifizierte Konstante bei
unbeschränktem Klassenpfad.**
`FUNCTION check(objects:CLASS; attr:ATTRIBUTE OF @objects RESTRICTION(TEXT));`
mit `cls : CLASS;` und `F.check(cls,>>label)` kompiliert; `>>label` wird als
`Viewable[Host]` (lexikalisch) gebunden, nicht an den tatsächlichen
klassenwertigen Pfad `cls`. Die Norm erlaubt den Klassennamen auch „aus dem
Kontext“; deshalb ist dies nicht sicher ein Fehler. Es ist aber eine Grenze
der Aussage „im tatsächlichen Owner gebunden“ und nicht durch neue Tests
abgedeckt. Empfehlung: im nächsten Kandidaten präzisieren oder durch einen
Test/Entscheid absichern. Kein Blocker für die technische L2-1-Prüfung.

**O2 / Beobachtung ohne Abnahmefinding – `UNDEFINED`-Owner.**
`F.check(UNDEFINED,>>label)` kompiliert; `>>label` wird ebenfalls
`Viewable[Host]`. Dies ist der gleiche Randfall wie O1 (kein statisch
bestimmbarer Owner). Kein Nachweis eines Normverstosses, aber eine
Dokumentations-/Testlücke.

**O3 / Beobachtung ohne Abnahmefinding – Pythonfrei-Zahl.**
Der Implementierungsbericht nennt **1200** Eingaben; der frische Lauf meldet
**1201** (`PYTHON_FREE_SOURCE_PASS 1201 inputs`, `source-check.json
status=passed`). Die Abweichung ist rein redaktionell; die Pythonfreiheit ist
für den Kandidaten belegt. Kein Kriterienverstoss.

**O4 / Beobachtung ohne Abnahmefinding – Kandidatenindex.**
Die beiden neuen Testklassen `DependentFunctionArgumentsContractTest` und
`BuiltinSignatureFoundationContractTest` erscheinen nicht in
`g2-test-candidate-index.json` (Keyword-Kandidaten) und nicht in
`candidateTestSourceHashes` der Quellinventur. `testMethods=662` ist korrekt,
der Index ist ausdrücklich `CANDIDATE_ONLY` („Keyword candidates only; no
assertion, execution or acceptance claim“). Keine Abnahmewirkung; bei der
nächsten mechanischen Inventarpflege prüfen, ob eine Aufnahme gewünscht ist.

## Urteil

**BLOCKED**

**Bei BLOCKED: NORM_DECISION.**
Konkrete Voraussetzung: Die drei bereits gestellten Nutzerentscheidungen
**N1 (areAreas2/areAreas3)**, **N2 (OBJECTS-Funktionen in
MANDATORY/Plausibility 2.4)** und **N3 (SET-Hauptausdruck)** müssen
beantwortet sein. Erst danach kann AC2-02 – und damit L2-1 – abschliessend
bewertet werden. Zusätzlich ist **F1** (abhängiger Rückgabetyp bei
`UNDEFINED`-Actual) im nächsten Kandidaten zu beheben oder die neue
Dokumentation ist präzise einzuschränken und mit einem Test zu belegen.

Begründung:
- **AC2-01** ist für den geprüften Kernumfang technisch belegt: 668 Compiler-
  Testfälle ohne Fehler/Skips, eigene Probe A–E (98 Checks PASS), portable
  JS/WasmGC je 3423 PASS, Fresh/Reuse- und Definitionsquery-Fälle. **F1**
  (`UNDEFINED`-Actual) ist ein offener MAJOR-Randbefund und vor einem PASS zu
  klären; die Beobachtungen O1/O2 sind nicht blockierend.
- **AC2-02** ist **nicht abschliessbar**: Die Signaturgrundlage
  `areAreas2`/`areAreas3` (N1) und zwei ausdrücklich normwidrig erscheinende,
  aber als Nutzerfrage offene Constraint-Profile (N2/N3) sind unbeantwortet.
  Der Kandidat ändert diese Profile nicht; für N1 zementiert der neue
  Signaturtest das aktuelle Verhalten, für N2/N3 bleiben die bestehenden
  Tests/Profile unverändert. Eine Empfehlung oder Zeitablauf wird nicht als
  Zustimmung behandelt.
- Der technische Pflichtlauf ist **frisch, vollständig und Exit 0**: 777
  JVM-Tests, 136 Evidenz-, 15 Assertion-, 15 Hosttests, 0/0/0; T2 roh
  579/13/1 und Projekt 579/14/0 bei 593/610, 19/19 Assessments PASS; SDK 9
  Kommandos; Pythonfreiheit 1201 Eingaben mit ENOENT/EPERM; beide
  Node-Backends 3423 PASS. Es gibt keinen technischen FAIL im Sinne eines
  fehlschlagenden Pflichttests.
- **Kein Phase-2-PASS**, kein Start von L2-2/L2-3. Browser/Safari/Web Image
  sind für L2-1 nicht behauptet und bleiben spätestens L2-3-Phasengate.

Nächster konkreter Schritt: Nutzer beantwortet N1–N3. Danach Implementierer:
Profil/Tests gemäss Entscheidung anpassen, F1 klären, neuen Kandidatencommit
erzeugen. Der neue SHA ist **vollständig neu** zu prüfen (inkl. frischem
Pflichtlauf und eigener Gegenprobe); frühere PASS-Berichte ersetzen keine
Abnahme des neuen SHA.
