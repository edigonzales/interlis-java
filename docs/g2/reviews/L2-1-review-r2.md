# Unabhängiger Review: L2-1 (R2)

Lieferabschnitt / Phase / letztes Phasengate?:
**L2-1 / Phase 2 / NEIN.** Dies ist nicht das letzte Gate einer Phase. Geprüft
wurden **AC2-01 und AC2-02 einzeln**. Es wird kein Phase-2-PASS und kein
kumulativer AC2-01–AC2-07-PASS behauptet; L2-2/L2-3 wurden nicht begonnen.
Der Abschnitt ist gemäss Handoff ausdrücklich nicht die letzte Lieferung einer
Phase. Dies ist ein gegenüber R1 vollständig neuer Lauf auf einem neuen SHA;
der historische R1-Bericht wurde nicht als Prüfnachweis übernommen.

Implementierungsagent: Codex

Prüfagent: OpenCode; tatsächliche Provider-/Modellkonfiguration:
- Startkonfiguration gemäss Auftrag: **OpenCode v2.0.28**, `--model
  deepseek/deepseek-flash`, **DeepSeek DIREKT**, UI **DeepSeek V4.1 Flash
  1017**, **kein OpenRouter**.
- Tatsächlich beobachteter Prozess:
  `/Users/stefan/.opencode/bin/opencode run --standalone --model
  deepseek/deepseek-flash --agent build --format json --auto ...`.
  `opencode --version` meldet `v2.0.28`. In der Prozessumgebung ist kein
  OpenRouter-Endpunkt gesetzt (nur `AI_AGENT`, `OPENCODE`,
  `OPENCODE_SESSION_ID`, `OPENCODE_TERMINAL`). Eine Modellselbstauskunft
  allein ist kein Identitätsnachweis; die Angabe stützt sich auf die
  tatsächliche Prozesskonfiguration und die vorgegebene Startkonfiguration.
  Es wurden keine Zugangsdaten gelesen oder ausgegeben.

BASE_SHA: `81ad01919c45165ad20ecb86986ff5f0475cc44c`
CANDIDATE_SHA (durch git überprüft): `535d46714b59769058bbd46a737c35ac38e3dd0f`

- `git rev-parse HEAD` im eigenen Review-Worktree:
  `535d46714b59769058bbd46a737c35ac38e3dd0f` (detached).
- `git status --porcelain` **vor und nach** der Prüfung: **leer** (0 Zeilen).
- Elterncommit des Kandidaten: `4b7c31e4b4b28c7a340f21be1988f152c865c348`
  (= R1-Kandidat); `81ad019…` ist Vorfahre des Kandidaten
  (`git merge-base --is-ancestor`: ja).
- `git diff --shortstat BASE..CANDIDATE`: **20 Dateien, +1740/−531**.
- `git diff --shortstat R1-Kandidat..CANDIDATE`: **12 Dateien, +581/−20**
  (F1-Nacharbeit: Dokumentation, zwei Testmethoden, zwei portable Fälle,
  mechanische Inventarpflege und der unveränderte R1-Bericht).
- `git diff --check BASE..CANDIDATE`: **Exit 0** (keine Whitespace-Fehler).
- Der historische R1-Bericht `docs/g2/reviews/L2-1-review-r1.md` ist im
  Kandidaten unverändert; seine SHA-256 ist
  `e13320f8bf1c5b631ed91ec35414ca870f4efbd5920e2f7d201c18737d72fbd3`
  (im Review-Worktree und im Haupt-Checkout identisch). Er ist Historie und
  kein Steuerprompt; R1-Prüfungen wurden nicht als R2-Nachweis übernommen.
- Der vollständige relevante Diff wurde gelesen: Produktionscode
  `FunctionBindings.java`, `GraphBuilder.java`, `AttributePathType.java`
  (nur Javadoc), die vier geänderten/neuen Compilertestklassen, der portable
  Vertrag, die Spezifikations-/Status-/Plan-/Inventardateien. Am
  R1→R2-Diff enthält der Produktionscode **ausschliesslich Javadoc**; die
  F1-Behebung ist die vom R1-Reviewer ausdrücklich erlaubte präzisierte
  Dokumentation plus Test, keine neue Laufzeitsemantik.
- Der Reviewer hat **keine** Produkt-, Test-, Build- oder Abnahmequelle
  geändert. Eigene Proben und Logs liegen ausschliesslich unter dem
  ignorierten `build/` des Review-Worktrees.

Prüfumgebung / Toolchain:
- Eigener sauberer Worktree:
  `/private/tmp/interlis-java-L2-1-review-535d467`, detached auf
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
| **AC2-01** – Objekt-/Attribut-/Rollenpfade inklusive Indizes, Mengen und Spezialkontexten liefern korrekte gebundene Ausdruckstypen und Referenzen. | **Erfüllt für den geprüften Umfang; F1 aus R1 ist geschlossen.** Kein technischer FAIL. | Frischer Compilerlauf: **670** Compiler-XML-Testfälle (664 `@Test`-Methoden, Parameterized-Expansion), 0 Failures/Errors/Skips; darunter `DependentFunctionArgumentsContractTest` **12/12**, `ConstraintPathBindingContractTest` **13/13** (Indizes, Mengen, Rollen, SET/WHERE/UNIQUE/EXISTENCE, Singleton-/Collection-Identitäten), `AreaFunctionContractTest` **7/7**. Eigene neue Probe (A–F, 2.3/2.4): **72 Checks PASS**, am BASE **6 rote Abschnitte**. Portable JS/WasmGC je **3427** PASS. Fresh/Reuse mit drei Importrevisionen und beiden Dateireihenfolgen in `importedDependentContextsFollowRevisionsAndMatchFreshAnalysisInEitherFileOrder`. F1: Dokumentation präzisiert und durch `undefinedAndUnrestrictedActualOwnersRetainLegalSymbolicReturnDependencies` öffentlich geprüft; eigene Probe bestätigt den formalen `Argument`-Bezug und die tatsächlichen Kinder. | **keine** (F1/O1/O2 aufgelöst; O4 Beobachtung) |
| **AC2-02** – Alle relevanten 21 Builtins, Signaturen/`UNDEFINED`-Semantik und Constraintfamilien (inkl. SET/WHERE, UNIQUE, EXISTENCE, externe Constraints und Namensvererbung) öffentlich korrekt und mit Gegenfällen geprüft; externe Funktionen nicht ausgeführt. | **Nicht abschliessbar – BLOCKED (NORM_DECISION).** Die technisch prüfbaren Teile sind belegt; Signaturgrundlage und zwei Constraint-Profile hängen an **drei weiterhin unbeantworteten Nutzerentscheidungen**. | `BuiltinSignatureFoundationContractTest` **2/2**: echte 13 (2.3) bzw. 21 (2.4) Aufrufe, gebundene Funktionsziele, tatsächliche `PredefinedOrigin`-Signaturen, falsche Argumentanzahl für jeden Namen. `UndefinedFunctionContractTest` 10/10, `ConstraintPathBindingContractTest` 13/13, `AreaFunctionContractTest` 7/7, `ExistenceValueFamiliesContractTest` 8/8, `ExternalConstraintContractTest` 1/1, `SetConstraintScopeContractTest` 1/1, `UniqueConstraintContractTest` 1/1. Externe Funktionen werden nicht ausgeführt (reine Bindung; kein Evaluator im Diff). Normprofile N1–N3 selbst nachgeprüft: unverändert akzeptiert. | **N1, N2, N3** |

### Technische Einzelprüfung der Produktänderungen

| # | Behauptung | Ergebnis | Beleg |
|---|---|---|---|
| 1 | `FunctionBindings.arguments` bindet bekannte tatsächliche Argumente iterativ in OF-Abhängigkeitsreihenfolge, erhält die Quellreihenfolge der veröffentlichten Kinder, ohne globalen aktuellen Aufrufzustand; unqualifizierte Konstanten werden im tatsächlichen Owner gebunden, Klammern/Ketten/spätere Argumente eingeschlossen; unbekannte Funktionen nutzen die normale Fehlerbindung. | **Erfüllt.** | Code: lokale `values`/`active`/`chain`, `List.copyOf(arguments)` in Indexreihenfolge; `checkWork()` in jedem Schritt; `dependency()` folgt `Argument.index()`; keine Instanzfelder. Tests: `aDependencyOnALaterActualArgument…`, `chainedAttributeArguments…`, `(>>label)`, 100-Vorwärts-Kette. Eigene Probe: per-Call-Isolation und funktionsproduzierter Owner. Unbekannte Funktion manuell: `F.missing(>>label)` → `SOURCE_ERRORS`, `ILI-SEM-0001`, kein internes Failure. |
| 2 | Ein gescheitertes Owner-Argument propagiert die ursprünglichen Diagnoseursachen in abhängige `ErrorExpression`, kein lexikalischer Scheinersatz. | **Erfüllt.** | `aFailedOwnerArgumentDoesNotPublishALexicallyBoundReplacementConstant`; eigene Probe mit späterem fehlerhaftem Owner (`F.check(>>label,>Missing)`): genau ein `ILI-SEM-0001`, beide Kinder `ErrorExpression`, gleiche `causes`, Call `ERROR`. Am BASE zeigt dieselbe Probe den alten lexikalischen Ersatz. |
| 3 | `ATTRIBUTE OF` einer klassenwertigen Pfadangabe kann genau denselben tatsächlichen klassenwertigen Ausdruck als Owner erfüllen; struktureller Vergleich gebundener Identitäten; explizite Klassenziele bleiben exakt, keine Subklasse. | **Erfüllt.** | `aClassValuedAttributeSuppliesItsDeclaredDependentAttributeArgument`, `explicitAttributeOwnersRemainExact…` (Base/Child, `>>C->surface`), `sameBoundExpression`/`sameExpression`, `ClassAttribute.path()`. Eigene Probe bestätigt `ClassAttribute(path)` auf exakt den eigenen Actual und keine Kreuzkontamination. Zusätzlich eigener Negativfall `>Base,>>Child->childOnly` → `SOURCE_ERRORS`/Regel `FUNCTION_ATTRIBUTE`; positiver `>Child,>>Child->label` → ok. |
| 4 | Abhängige ATTRIBUTE-Rückgabetypen werden pro erfolgreichem Aufruf gebunden; formaler Funktionstyp und Argumentbezug bleiben unverändert; konstante Ziele `Viewable`, klassenwertige tatsächliche Ausdrücke `ClassAttribute(ExpressionId)` im selben Snapshot; keine externe Funktionsausführung. | **Erfüllt; F1-Dokumentationslücke geschlossen.** | `dependentReturnTypesAreBoundToEachInvocation…` (2.3/2.4, `>Data` und `cls`, Deklaration bleibt `Argument(pick,0)`, Ergebnis neuer Typ). Eigene Probe: zwei Aufrufe mit `clsA`/`clsB` erhalten **zwei verschiedene** Typen; funktionsproduzierter Owner `F.pick(F.which(#true))` ergibt `ClassAttribute` auf genau den `which`-Call im selben Snapshot; `UNDEFINED` und `OBJECTS OF ANYCLASS` behalten den formalen `Argument`-Bezug. |
| 5 | Vollständig aufgelöste inkompatible ENUM-/FORMAT-/Geometrie-/Klassenwerttypen bei EXISTENCE sind `SOURCE_ERRORS`, nicht `INCOMPLETE_SUPPORT`; Generic/Deferred/Pending werden nicht maskiert. | **Erfüllt.** | Norm: 2.3 §2.12/PDF59 und 2.4 §3.12/PDF77 (Bedingungsattribut muss kompatibel sein). `incompatibleResolvedExistenceEnumerationsAreSourceErrors` (ENUM). Eigene Probe: Klassenwert `CLASS RESTRICTION(A)` gegen `CLASS RESTRICTION(B)` in 2.3/2.4 → nur `SOURCE_ERRORS`, `ILI-SEM-0005`, kein `INCOMPLETE_SUPPORT`, keine Failure-ID. R1-Proben zu FORMAT/Geometrie wurden nicht als Nachweis übernommen; der Code-Pfad `familyPending` enthält nur noch `GENERIC`, `DEFERRED`, `PENDING`. |
| 6 | Vierzehn neue `DependentFunctionArgumentsContractTest`/`BuiltinSignatureFoundationContractTest`-Methoden, 22 neue portable Quellenfälle; öffentliche Ziele/Typen/Definitionsranges (UTF-16/Revision), Ketten/100-Vorwärtsargumente, Fehlerpropagation, Importrevisionen/FreshReuse/Dateireihenfolge. | **Erfüllt.** | JUnit-XML: `DependentFunctionArgumentsContractTest tests="12" skipped="0" failures="0" errors="0"`, `BuiltinSignatureFoundationContractTest tests="2" skipped="0" failures="0" errors="0"`; Compiler-`@Test`-Methoden **664** (BASE 650); portable `stabilizationCases=3427` je Backend (R1-Kandidat 3423, BASE 3405; +22). Keyword-Index bleibt `CANDIDATE_ONLY`. |

## Eigene Gegenprobe

**Hypothese und fachliches Risiko:**
Die L2-1-Implementierung könnte auf den neuen Test zugeschnitten sein, aber
öffentlich weiterhin (a) pro Aufruf denselben deklarierten Ergebnis-Typ
verwenden, (b) einen funktionsproduzierten klassenwertigen Ausdruck nicht als
gebundenen Owner akzeptieren, (c) bei `UNDEFINED` oder einer unbeschränkten
`CLASS`-Pfadangabe einen konkreten Owner erfinden bzw. den formalen Bezug
verlieren, oder (d) bei einem gescheiterten späteren Owner-Argument einen
lexikalischen Ersatz veröffentlichen. Die Probe ist neu formuliert und prüft
bewusst Randfälle, die in den Produktmethoden nicht vorkommen (insbesondere
`F.pick(F.which(#true))` mit funktionsproduziertem Klassenwert und zwei
gleich restringierte, aber verschiedene Klassenpfade).

**Neu erstellter Testinput / exakter Pfad oder vollständige Quelle:**
`build/review/L2-1-r2/probe/R2OwnerBindingCounterProbe.java` (nur im
ignorierten `build/` des Review-Worktrees; keine Produkt-/Test-/Builddatei
geändert). Die Quelle nutzt ausschliesslich die öffentliche Compiler-API
(`CompilerEngines`, `CompilationRequest`, `SourceSet`, `SemanticGraph`,
`CompiledSchema`) und läuft je 2.3/2.4 durch sechs Abschnitte:

- **A – per-Call-Isolation:** `F.pick(clsA)` und `F.pick(clsB)` mit zwei
  verschiedenen `CLASS RESTRICTION(Data)`-Pfaden in einer Bedingung; erwartet
  zwei **verschiedene** Ergebnistypen, je `ClassAttribute` auf den **eigenen**
  Actual, beide Owner-Ausdrücke im selben Snapshot.
- **B – funktionsproduzierter Owner:** `F.pick(F.which(#true))` mit
  `which(flag:BOOLEAN):CLASS RESTRICTION(Data)`; erwartet `ClassAttribute`
  auf genau den `which`-Aufruf.
- **C – `UNDEFINED`:** `F.pick(UNDEFINED)`; erwartet `RESOLVED`,
  `typeId == pick.returnType()`, Owner `Argument(pick,0)`, Actual-Kind im
  Snapshot, **kein** `Viewable`/`ClassAttribute`.
- **D – unbeschränkter Klassenwert:** `F.pick(cls)` mit `cls:CLASS`; erwartet
  keinen erfundenen `Viewable`-Owner; beobachtet wird der gebundene
  `ClassAttribute(cls)` (siehe Beobachtung O5).
- **E – Fehlerpropagation:** `F.check(>>label,>Missing)` mit späterem
  Owner-Argument; erwartet genau ein `ILI-SEM-0001`, beide Kinder
  `ErrorExpression` mit denselben Ursachen.
- **F – lexikalischer Fallback:** `F.check(cls,>>label)` mit unbeschränktem
  `cls`; erwartet `Host.label` und `Viewable(Host)`.

**Erwartetes Verhalten:**
Wie oben; zusätzlich bleiben veröffentlichte Kinder in Quellreihenfolge, die
Ergebnistypen und `ClassAttribute.path()`-Ausdrücke liegen im selben Snapshot,
und es entsteht keine interne Ausnahme/Failure-ID.

**Kommando / Exitcode / beobachteter tatsächlicher Output:**
```text
/Users/stefan/.sdkman/candidates/java/25.0.3-tem/bin/javac \
  -cp 'interlis-cli/build/install/interlis-cli/lib/*' \
  -d build/review/L2-1-r2/probe/classes \
  build/review/L2-1-r2/probe/R2OwnerBindingCounterProbe.java
/Users/stefan/.sdkman/candidates/java/25.0.3-tem/bin/java \
  -cp 'build/review/L2-1-r2/probe/classes:interlis-cli/build/install/interlis-cli/lib/*' \
  R2OwnerBindingCounterProbe
```
Am Kandidaten: **Exit 0**; Log `build/review/L2-1-r2/probe/probe.log`:
```text
OBSERVE 2.3 unrestricted-class owner=Optional[ClassAttribute[path=ExpressionId[value=expr-3]]] declarationType=false
OBSERVE 2.4 unrestricted-class owner=Optional[ClassAttribute[path=ExpressionId[value=expr-3]]] declarationType=false
R2_PROBE_PASS checks=72 failures=0
```
Dieselbe Probe in einem separaten temporären Worktree auf **BASE_SHA
`81ad019…`** gegen dort frisch gebautes `:interlis-cli:installDist`:
**Exit 1**, Log `build/review/L2-1-r2/probe/probe-base.log`, **44 Checks /
6 rote Abschnitte**:
```text
FAIL 2.3 per-call isolation: unexpected rejection ... FUNCTION_ATTRIBUTE
FAIL 2.3 function-owner: owner is Optional[Argument[function=sym-4, index=0]]
FAIL 2.3 failed-owner: dependent child is not ErrorExpression
FAIL 2.4 per-call isolation: unexpected rejection ... FUNCTION_ATTRIBUTE
FAIL 2.4 function-owner: owner is Optional[Argument[function=sym-4, index=0]]
FAIL 2.4 failed-owner: dependent child is not ErrorExpression
```
Der temporäre BASE-Worktree wurde danach wieder entfernt; der Review-Worktree
blieb sauber.

**Ergebnis und Schlussfolgerung:**
Die Probe widerlegt die Risikobehauptungen am Kandidaten nicht: **72 Checks
PASS**. Sie reproduziert am BASE-SHA **6 tatsächliche Fehlklassen** in genau
den korrigierten Bereichen (fehlende per-Call-Bindung, funktionsproduzierter
Owner nicht gebunden, lexikalischer Fehlerersatz). Damit ist sie eine echte,
eigenständige Gegenprobe und keine Wiederholung oder Umbenennung einer
Produktmethode. Kein Finding aus dieser Probe.

**Zusätzlich ausgeführte Norm-/Randproben (nur Beobachtung, keine
Nutzerentscheidung; Dateien unter `build/review/L2-1-r2/profile/`):**
- **N1:** 2.4 `areAreas2(ALL,…)`, `areAreas2(THIS,…)`, `areAreas3(ALL,…)` →
  jeweils `check --json` **Exit 0 / status ok**; das bestehende Profil ist
  unverändert.
- **N2:** 2.3 und 2.4 `MANDATORY CONSTRAINT INTERLIS.objectCount(ALL)>=0;` →
  **Exit 0 / status ok**; das bestehende Profil ist unverändert.
- **N3:** 2.3 und 2.4 `SET CONSTRAINT n>0;` und
  `SET CONSTRAINT DEFINED(THIS);` → **Exit 0 / status ok**; das bestehende
  Profil ist unverändert.
- **F1:** `F.pick(UNDEFINED)` in 2.3/2.4 → **Exit 0 / status ok**.
- **EXISTENCE-Klassenwert:** `CLASS RESTRICTION(A)` gegen
  `CLASS RESTRICTION(B)` in 2.3/2.4 → **Exit 1**, nur `SOURCE_ERRORS`,
  `ILI-SEM-0005`, kein `INCOMPLETE_SUPPORT`.
- **Explizite Owner:** `>Base,>>Child->childOnly` → **Exit 1**,
  `SOURCE_ERRORS` (Regeltext `FUNCTION_ATTRIBUTE`);
  `>Child,>>Child->label` → **Exit 0 / status ok**.
- **Unbekannte Funktion:** `F.missing(>>label)` → **Exit 1**,
  `SOURCE_ERRORS`, `ILI-SEM-0001`, keine Failure-ID (normale Fehlerbindung).

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
**Exit 0**, `BUILD SUCCESSFUL in 2m 15s`, **58 actionable tasks: 58 executed**
(Log `build/review/L2-1-r2/mandatory-gate.log`). Der Lauf ist über
`build/reports/python-free/environment.json` an
`gitHead=535d46714b59769058bbd46a737c35ac38e3dd0f`, `dirty=""`, Start
`2026-10-30T03:14:09.390Z`, Ende `2026-10-30T03:16:25.604Z` gebunden.
Automatisch `SKIPPED` waren nur die experimentellen Emscripten-/Disasm-Tasks;
kein Test wurde deaktiviert, kein `-x test` verwendet, kein `@Disabled`
(0 Treffer in den Testquellen).

**JUnit-XML (frisch, selbst aus den XMLs summiert; nicht nur
Zusammenfassungs-JSON):**

| Suite | Dateien | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|---:|
| `interlis-compiler:test` | 128 | **670** | 0 | 0 | 0 |
| `interlis-cli:test` | 3 | 33 | 0 | 0 | 0 |
| `interlis-lsp:test` (inkl. `LspProcessTest`) | 5 | 16 | 0 | 0 | 0 |
| `interlis-model:test` | 6 | 16 | 0 | 0 | 0 |
| `interlis-projection:test` | 3 | 12 | 0 | 0 | 0 |
| `interlis-repository:test` | 6 | 19 | 0 | 0 | 0 |
| `interlis-wasm-bridge:test` | 1 | 3 | 0 | 0 | 0 |
| `interlis-workspace:test` | 2 | 10 | 0 | 0 | 0 |
| **reguläre JVM-Tests gesamt** | | **779** | **0** | **0** | **0** |
| `interlis-cli:evidenceTest` | 4 | **136** | 0 | 0 | 0 |
| `interlis-compiler:g2AssertionTest` | 11 | **15** | 0 | 0 | 0 |
| `interlis-cli:verificationTest` (Host) | 5 | **15** | 0 | 0 | 0 |

Die XML-Zeitstempel liegen zwischen `2026-10-30T03:14:18.869Z` und
`2026-10-30T03:14:44.465Z`, also nach dem Laufstart. Die neuen Klassen sind
vollständig enthalten: `DependentFunctionArgumentsContractTest` 12/12,
`BuiltinSignatureFoundationContractTest` 2/2, `AreaFunctionContractTest` 7/7,
`ConstraintPathBindingContractTest` 13/13. Compiler-`@Test`-Methoden: **664**
(BASE 650). `@Disabled`/`@Ignore` in den Testquellen: **0**.

**Portable/Node (kein Browserersatz):**
- `interlis-wasm-teavm/build/js-contract-result.json`:
  `status=passed`, `stabilizationCases=3427`, `runtime=teavm-js`,
  Node v24.22.0.
- `interlis-wasm-teavm/build/wasm-contract-result.json`:
  `status=passed`, `stabilizationCases=3427`, `runtime=teavm-wasm-gc`,
  `nodes=2`, Node v24.22.0.
- Beide Dateien wurden im Pflichtlauf frisch neu geschrieben.
- `checkPortable` enthält `check`, die berührten SDK-/LSP-/CLI-Tests und beide
  Node-Backends (Task-Log `:interlis-compiler:test`, `:interlis-cli:test`,
  `:interlis-lsp:test`, `:interlis-wasm-teavm:jsContractTest`,
  `:interlis-wasm-teavm:wasmContractTest`, `:checkPortable`).

**T2 – Conformance (gepinnt, frisch, Roh und Projekt getrennt):**
- Suite: Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`, SHA-256
  `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe`
  (unverändert).
- Filter `^(ili23|ili24)\.`; Fallmenge **610**, ausgewählt **593**,
  ausgeschlossen **17** (nur Version `1.0`, INTERLIS-1-Profil).
- Rohbilanz (`referenceSummary`): **579 PASS / 13 FAIL / 1 XFAIL / 0 XPASS /
  0 ERROR / 593**; `referenceResult=FAIL`, `exitCode=1` (bei rohen FAILs
  erwartet).
- Projektbilanz (`summary`): **579 PASS / 14 XFAIL / 0 FAIL / 0 XPASS /
  0 ERROR / 593**; `overallResult=PASS`.
- **19/19** `normativeAssessments` mit `result=PASS`; `assessmentCaseIds`
  vollständig, gegenüber BASE unverändert (die Datei
  `docs/evidence/conformance/*` ist nicht im Diff).
- `build/reports/conformance/conformance-summary.json` und
  `conformance-log.txt` wurden im Lauf frisch neu geschrieben.

**SDK/CLI/LSP/Pythonfreiheit:**
- `sdkProcessCheck`: `SDK_PROCESS_PASS 9 commands`;
  `build/reports/sdk/sdk-process-comparison.json` mit 9 Kommandos,
  Exitcodes 0/0/0/0/1/1/0/0/2 (erwartete Negativfälle) und SHA-256 der
  Ausgaben; Status `passed`.
- `interlis-cli:installDist` wurde **vor** dem Conformance-Lauf gebaut; es gab
  keinen gleichzeitigen Distributionsneubau während der Conformance.
- Pythonfrei: `git ls-files '*.py'` = **0**;
  `PYTHON_FREE_SOURCE_PASS 1202 inputs`;
  `build/reports/python-free/source-check.json` = `status=passed`.
  Im Pflichtlauf keine Python-/pip-/uv-Ausführung; die OS-Sperre beweist
  ENOENT/EPERM.

**Norm-/Suite-/XFAIL-/Baseline-/Quellpin-Unverändertheit:**
- `docs/evidence/stabilization/g2-norm-inventory.json` ist **nicht** im Diff;
  keine Normzitate/PDF-Seiten/Grammatikpins geändert.
- `docs/evidence/conformance`, Suite-Pin, Baseline, Manifest und die
  Original-/Kontrollquellen sind **nicht** im Diff. Keine XFAIL-/Manifest-
  Änderung, kein CANDIDATE_ONLY zu einer Abnahme umetikettiert.
- `testdata/g2/predefined-catalogue.json`: nur `sourceAnchors`-Offsets/Hashes
  (22 Nicht-Offset-Zeilen, alle GraphBuilder-Hash), keine Zitat-/Dokument-
  Hashes; Dokument-Hashes für 2.3/2.4 unverändert.
- `g2-rule-matrix.json`: nur Inventar-Hashes und `testMethods` 662→664;
  `conditions=621`, `requirements=80`, `withCandidate=669` unverändert.
  Die SHA-256 von `g2-source-inventory.json`
  (`9ce56a1f…`) und `g2-test-candidate-index.json` (`b13a6d59…`) stimmen mit
  den in `g2-rule-matrix.json` eingetragenen Hashes überein.
- `g2-source-inventory.json`: 200 Nicht-Offset-Zeilen, ausschliesslich
  Spec-/GraphBuilder-/Catalogue-Hashes und Zeilennummern; **0** Änderungen an
  `acceptance`/`status`/`requirement`/`norm`/`quote`/`page`/`section`.
- `g2-test-candidate-index.json`: `testMethods=664`, Status
  `CANDIDATE_ONLY`; die beiden neuen Klassen bleiben (wie R1-O4) ausserhalb
  des Keyword-Index. Keine Abnahmewirkung.
- `FunctionBindings.java` enthält **15** `new Origin.PredefinedOrigin`-
  Produzenten wie BASE; `otherPredefinedContents.profileCallSites` bleibt
  **19** Einträge; keine Profil-/Signaturänderung.

**Nicht ausgeführte fällige Prüfungen mit Grund:**
- `checkBrowser` mit echter Browsermatrix/echtem Safari und Web Image ist
  **spätestens L2-3-Phasengate** fällig und für L2-1 nicht behauptet. Es wurde
  kein Browser-/Safari-/Web-Image-Erfolg als Nachweis verwendet.
- `releaseCheck`, P3-/P4-/P5-, T4-/Corpus-/Benchmark-Pflichten sind für diesen
  Lieferabschnitt nicht vorgesehen und wurden nicht als Pflicht erfunden.
- Kein kumulatives Phase-2-Gate: L2-1 ist nicht die letzte Lieferung der
  Phase; L2-2/L2-3 sind nicht begonnen.

**Python-Migrations-/Phaseninvarianten soweit relevant:**
- 0 getrackte `.py`; 1202 geprüfte Eingaben; PATH-/EPERM-Nachweise wie oben.
- Keine neue Python-Abhängigkeit, kein Python in den aktiven Prüfwegen.
- Fortgeltende Nutzerentscheidungen aus Phase 0b/1 (Metadatenwiederholungen/
  mehrzeilige Strings, 2.3 OID AS/BASKET OID AS ohne CONTRACTED,
  TYPE MODEL, Exponenten-/CONTINUOUS-/CIRCULAR-Profile,
  `ili23.Attribute23Test.attributeComplete`) sind nicht Gegenstand dieses
  Diffs und wurden nicht verändert; die 19 Assessments bleiben unverändert.
- Global D2 bleibt PENDING_USER_REVALIDATION; kein G2-FINAL.

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
werden.“ 2.4 §3.12/PDF78–79 (`NORM:2.4:3.12:p78:004`): SET-Bedingungen müssen
mindestens eine Funktion mit `OBJECTS OF`-Argument verwenden; „… können nie
die Werte von Attributen angesprochen werden.“ Der WHERE-Filter bleibt
objektbezogen.
Eigene Minimalfälle (2.3 und 2.4, `check --json`, Exit 0):
`SET CONSTRAINT n > 0;` und `SET CONSTRAINT DEFINED(THIS);` werden akzeptiert.
Ohne Nutzerentscheidung ist AC2-02 nicht abschliessbar. Voraussetzung:
Entscheidung, ob direkte Objektwerte im Hauptausdruck abgelehnt und WHERE
erhalten wird; danach Bindung/Tests anpassen.

**F1 / R1-MAJOR – GESCHLOSSEN (kein aktives Finding).**
Der R1-Befund (`F.pick(UNDEFINED)` bindet den abhängigen Rückgabetyp nicht pro
Aufruf, während der Dokumenttext „jeder erfolgreiche Aufruf“ zu breit war) ist
durch die vom R1-Reviewer ausdrücklich erlaubte **präzisierte Dokumentation
plus Test** geschlossen: Kapitel 05/06/16 und das interne Javadoc
unterscheiden nun bindbare Owner (eigener Typ, `Viewable`/`ClassAttribute`)
von `UNDEFINED`/unbeschränkten ANYCLASS-Wurzeln (formaler `Argument`-Bezug,
`RESOLVED` als statischer Vertrag ohne Laufzeitwert). Die neue Methode
`undefinedAndUnrestrictedActualOwnersRetainLegalSymbolicReturnDependencies`
prüft beide Sprachen und beide Fälle über öffentliche Graphwerte; die eigene
Probe bestätigt zusätzlich `typeId == pick.returnType()` und die tatsächlichen
Kinder. Kein konkreter Owner wird erfunden, kein legaler Fall abgelehnt, keine
neue öffentliche Typvariante eingeführt, keine externe Funktion ausgeführt.

**O1/O2 / Beobachtungen aus R1 – mit dem Kandidaten adressiert.**
Der nicht statisch bestimmbare Owner (`cls:CLASS` bzw. `UNDEFINED`) behält den
normativ ebenfalls zulässigen lexikalischen Konstantenkontext; Kapitel 05
präzisiert dies, und `anIndeterminateOwnerKeepsTheNormativeLexicalConstantContextWithoutInventingAClass`
prüft beide Sprachen und beide Actuals öffentlich. Eigene Probe F bestätigt
`Host.label` und `Viewable(Host)`. Kein neues Profil, kein Blocker.

**O3 / Beobachtung ohne Abnahmefinding – Laufzeiten/Zahlen.**
Der Implementierungsbericht nennt `2m14s`/`1202`/`779`/`670`; der frische
Lauf ergibt `2m15s`/`1202`/`779`/`670`. Die Laufzeitdifferenz ist rein
redaktionell; alle fachlich relevanten Zahlen stimmen. Kein Kriterienverstoss.

**O4 / Beobachtung ohne Abnahmefinding – Kandidatenindex.**
`DependentFunctionArgumentsContractTest` und
`BuiltinSignatureFoundationContractTest` erscheinen weiterhin nicht im
Keyword-Index `g2-test-candidate-index.json` und nicht in
`candidateTestSourceHashes` der Quellinventur. `testMethods=664` ist korrekt,
der Index ist ausdrücklich `CANDIDATE_ONLY` („Keyword candidates only; no
assertion, execution or acceptance claim“). Keine Abnahmewirkung; bei der
nächsten mechanischen Inventarpflege prüfen, ob eine Aufnahme gewünscht ist.

**O5 / Beobachtung ohne Abnahmefinding – unbeschränkter Klassenwert.**
`F.pick(cls)` mit `cls:CLASS` wird am Kandidaten als
`ClassAttribute(path=cls)` gebunden (neuer Typ), nicht symbolisch wie
`UNDEFINED`/`OBJECTS OF ANYCLASS`; am BASE blieb der deklarierte
`Argument`-Bezug. Das ist mit dem präzisierten Vertrag vereinbar, weil der
klassenwertige **Ausdruck** `cls` selbst eine bindbare Owner-Identität
besitzt; die Parenthese in Kapitel 05 nennt für den symbolischen Fall nur
`UNDEFINED` und unbeschränkte ANYCLASS-Objektmengen. Kein Normverstoss, aber
eine Grenze, die bei künftigen Dokumentänderungen bewusst bleiben sollte.

**O6 / Beobachtung ohne Abnahmefinding – Diagnosecode bei explizitem
Falschowner.**
`>Base,>>Child->childOnly` wird korrekt mit `SOURCE_ERRORS` abgelehnt; der
Diagnosecode ist `ILI-SEM-0010` (Katalog: „Vererbung oder Überschreibung
verletzt eine geprüfte Regel“, `messageKey=diagnostic.semantics.inheritance`),
während der Regeltext `FUNCTION_ATTRIBUTE` enthält. Das ist das bestehende
`emitRule`-Schema und keine Änderung dieses Kandidaten; die Ablehnung selbst
ist fachlich richtig. Kein Abnahmefinding.

## Urteil

**BLOCKED**

**Bei BLOCKED: NORM_DECISION.**
Konkrete Voraussetzung: Die drei bereits gestellten Nutzerentscheidungen
**N1 (areAreas2/areAreas3)**, **N2 (OBJECTS-Funktionen in
MANDATORY/Plausibility 2.4)** und **N3 (SET-Hauptausdruck)** müssen
beantwortet sein. Erst danach kann AC2-02 – und damit L2-1 – abschliessend
bewertet werden. F1 aus R1 ist mit diesem Kandidaten geschlossen; es gibt
kein neues technisches Muss-Finding und keinen technischen FAIL.

Begründung:
- **AC2-01** ist für den geprüften Umfang technisch belegt: 670 Compiler-
  XML-Fälle ohne Fehler/Skips, eigene neue Probe A–F (72 Checks PASS, 6 rote
  Abschnitte am BASE), portable JS/WasmGC je 3427 PASS, Fresh/Reuse- und
  Definitionsquery-Fälle. Der R1-MAJOR F1 ist durch präzisierte Dokumentation
  plus öffentlichen Test geschlossen; die Beobachtungen O1/O2 sind adressiert.
- **AC2-02** ist **nicht abschliessbar**: Die Signaturgrundlage
  `areAreas2`/`areAreas3` (N1) und zwei ausdrücklich normwidrig erscheinende,
  aber als Nutzerfrage offene Constraint-Profile (N2/N3) sind unbeantwortet.
  Der Kandidat ändert diese Profile nicht; für N1 zementiert der neue
  Signaturtest das aktuelle Verhalten, für N2/N3 bleiben die bestehenden
  Tests/Profile unverändert. Eine Empfehlung oder Zeitablauf wird nicht als
  Zustimmung behandelt.
- Der technische Pflichtlauf ist **frisch, vollständig und Exit 0**: 779
  JVM-Tests, 136 Evidenz-, 15 Assertion-, 15 Hosttests, 0/0/0; T2 roh
  579/13/1 und Projekt 579/14/0 bei 593/610, 19/19 Assessments PASS; SDK 9
  Kommandos; Pythonfreiheit 1202 Eingaben mit ENOENT/EPERM; beide
  Node-Backends 3427 PASS.
- **Kein Phase-2-PASS**, kein Start von L2-2/L2-3. Browser/Safari/Web Image
  sind für L2-1 nicht behauptet und bleiben spätestens L2-3-Phasengate.

Nächster konkreter Schritt: Nutzer beantwortet N1–N3. Danach Implementierer:
Profil/Tests gemäss Entscheidung anpassen und einen neuen Kandidatencommit
erzeugen. Der neue SHA ist **vollständig neu** zu prüfen (inkl. frischem
Pflichtlauf und eigener Gegenprobe); frühere PASS-Berichte ersetzen keine
Abnahme des neuen SHA.
