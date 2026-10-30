# Unabhängiger Review: L2-1

Lieferabschnitt / Phase / letztes Phasengate?: **L2-1 / Phase 2 / NEIN**
Implementierungsagent: Codex
Prüfagent: OpenCode; tatsächliche Provider-/Modellkonfiguration: OpenCode-CLI **v2.0.28**
(`/Users/stefan/.opencode/bin/opencode --version`), Startprozess
`opencode run --standalone --model deepseek/deepseek-flash --agent build --format json --auto …`.
Der tatsächlich übergebene Providerpräfix ist `deepseek`; im Startkommando und
in der aktiven `opencode.jsonc` ist kein OpenRouter-Provider und keine
OpenRouter-URL vorhanden (der Auftrag verlangt DeepSeek direkt). Die Modellselbstauskunft „DeepSeek V4.1 Flash1017“ wird
ausdrücklich **nicht** als Identitätsbeleg verwendet. Die getrennt laufende
Desktop-App meldet 2.1.4; die Prüfsitzung selbst ist die CLI 2.0.28.
BASE_SHA: `81ad01919c45165ad20ecb86986ff5f0475cc44c`
CANDIDATE_SHA (durch git überprüft): `900706a177a047e6f7d0ffd34d9e4cb2853a2ad7`
Prüfumgebung / Toolchain: macOS arm64; Java `25.0.3` (Temurin,
`JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem`); Gradle-Wrapper
`9.8.1`; Node `v24.22.0`; npm `ci` bereits frisch; eigener sauberer Worktree
`/private/tmp/interlis-java-L2-1-review-900706a`. `git status --porcelain` war
vor und nach allen Prüfungen leer; `git diff --check` sauber. Kein Python,
kein pip/uv, kein OpenRouter, kein Push/Commit/Release.

## Kriterienprüfung

| Kriterium-ID | Erfüllt / verletzt / nicht prüfbar | Eigener Test-/Code-/CI-Beleg | Finding-ID |
|---|---|---|---|
| **AC2-01** – Objekt-/Attribut-/Rollenpfade inklusive Indizes, Mengen und Spezialkontexten liefern korrekte gebundene Ausdruckstypen und Referenzen. | **Erfüllt.** Kein technisches Muss-Finding. | Frischer Compilerlauf `:interlis-compiler:test`: **679** JUnit-Fälle, 0 Failures/Errors/Skips, darunter `DependentFunctionArgumentsContractTest` **12/12**, `SetConditionContextContractTest` **9/9**, `ConstraintPathBindingContractTest` **13/13**, `AreaFunctionContractTest` **7/7**. Eigene neue Probe (A/B/D/F/H/I, 2.3/2.4): 66/66 Checks; am BASE 22 rote Checks. Fresh/Reuse, drei Importrevisionen und beide Dateireihenfolgen sind in `importedDependentContextsFollowRevisionsAndMatchFreshAnalysisInEitherFileOrder` öffentlich geprüft; portable JS/WasmGC je **3459** Fälle PASS. | keine |
| **AC2-02** – Alle relevanten 21 Builtins, ihre Signaturen/`UNDEFINED`-Semantik und die Constraintfamilien (inkl. SET/WHERE, UNIQUE, EXISTENCE, externe Constraints und Namensvererbung) sind öffentlich korrekt und mit Gegenfällen geprüft; externe Funktionen werden nicht ausgeführt. | **Erfüllt.** Kein technisches Muss-Finding. | `BuiltinSignatureFoundationContractTest` **2/2** (echte 13/21 Aufrufe, gebundene Ziele, reale Signaturen, falsche Argumentzahl für jeden Namen); `UndefinedFunctionContractTest` 10/10; `ExistenceValueFamiliesContractTest` 8/8; `ExternalConstraintContractTest` 1/1; `SetConstraintScopeContractTest` 1/1; `UniqueConstraintContractTest` 1/1; `ConstraintPathBindingContractTest` 13/13. Eigene Probe C/F/G/H/I prüft die 2.4-`OBJECTS`-Grenze, externe Constraints, N1/N2-Profile und die Grenze Klassenkonstante/Attributwert. Kein Evaluator oder externer Funktionsaufruf im Diff. T2: **21/21** strenge Assessments PASS. | keine |

### Ausdrückliche Profilentscheidungen (N1–N3)

| Entscheidung | Umsetzung am Kandidaten | Eigener Beleg |
|---|---|---|
| **N1** – areAreas2/areAreas3 in 2.4 beide mit `OBJECTS OF ANYCLASS`; Singleton-Zuweisung und TEXT-Pfade bleiben; in 2.3 beide unzulässig. | **Erfüllt.** Beide Signaturen unverändert als `OBJECTS`; `areAreas2(THIS,…)`/`areAreas3(THIS,…)` in `MANDATORY` 2.4 akzeptiert; `isSetCondition` lehnt `THIS` nur im SET-Hauptausdruck ab; in 2.3 sind beide Namen unbekannt. | Eigene Probe F; portable Fälle `areAreas2-direct/escaped/dynamic` und `areAreas3-*`; `SetConditionContextContractTest.approvedAreaNames…`; `BuiltinSignatureFoundationContractTest` 21 Signaturen. |
| **N2** – OBJECTS-Parameterfunktionen in 2.4-`MANDATORY`-/Plausibilitätsbedingungen wie ili2c erhalten. | **Erfüllt.** Keine neue Einschränkung ausserhalb von SET; `MANDATORY CONSTRAINT INTERLIS.objectCount(ALL)>=0` in 2.3/2.4 erfolgreich; ebenso `F.check(THIS)`/`F.check(ALL)` ausserhalb des SET-Hauptausdrucks. | Eigene Probe F; `SetConditionContextContractTest.confirmedObjectSetFunctionsStillAcceptSingleObjectsOutsideSetConditions`; portable Fälle. |
| **N3** – Kein einzelnes Objektkontextobjekt im SET-Hauptausdruck; direkte Attribut-/Rollen-/THIS-Werte auch verschachtelt ablehnen; WHERE bleibt individuell; ALL, Klassen-/Attributpfadkonstanten, Laufzeitparameter und passende Aufrufe bleiben. | **Erfüllt, ohne Legacy-Ausnahme.** Jeder gewöhnliche Pfad im revisionsgebundenen Hauptausdruck erhält `ILI-SEM-0008`/`TYPE_CHECKING` mit Segment, Grund und Namensbereich; WHERE, `ALL`, `>Klasse`, `>>Attribut`, Laufzeitparameter und geparste TEXT-Pfade behalten ihre Binder. In 2.4 verlangt der Hauptausdruck zusätzlich einen tatsächlichen Aufruf mit `OBJECTS`-Formal; 2.3 erlaubt weiterhin konstante SET-Bedingungen. | Eigene Probe A/B/C/F/G/H/I; `SetConditionContextContractTest` 9/9; `ConstraintPathBindingContractTest`; portable `setConditionContextCases`; zwei neue strenge Original-/Kontroll-Assessments. |
| **`NORM:2.4:3.12:p78:004`** – mindestens ein tatsächlicher Funktionsaufruf mit `OBJECTS OF`-Formal im Hauptausdruck; Aufruf nur in WHERE zählt nicht. | **Erfüllt.** `hasObjectSetFunction` scannt den gebundenen Hauptausdruck budget-/abbruchgeprüft und typisiert den Fehler als `ILI-SEM-0005`; bereits fehlerhafte/nicht boolesche Ausdrücke erhalten keine Folgemeldung. | Eigene Probe G (2.4: nur-WHERE → 1×SEM-0005; 2.3 akzeptiert); `SetConditionContextContractTest.only24RequiresAnObjectSetFunctionInTheConditionItself`; externe Variante in eigener Probe C. |

### Technische Einzelprüfung der Produktänderungen

| Nr. | Behauptung | Ergebnis | Eigener Beleg |
|---|---|---|---|
| 1 | `FunctionBindings.arguments` bindet tatsächliche ATTRIBUTE-Konstanten im tatsächlichen `OF`-Kontext, iterativ/memoisiert, Klammern/Ketten/spätere Abhängigkeiten; Kinder bleiben in Quellreihenfolge; gescheiterter Owner propagiert Ursachen. | **Erfüllt.** | `DependentFunctionArgumentsContractTest` 12/12; eigene Probe D (restringierter Klassenwert `cls` bindet `>>label` an `Data.label`, nicht `Host.label`); 100-Ketten-Test; `aFailedOwnerArgument…`; Codeinspektion `FunctionBindings.arguments`/`blockedFunctionArgument`. |
| 2 | Klassenwertiger OF-Pfad und tatsächlicher Owner werden über gebundene Ausdrucksidentitäten verglichen; abhängige Rückgabetypen pro Aufruf gebunden; formale Deklaration unverändert; `Viewable`/`ClassAttribute`; UNDEFINED/ANYCLASS symbolisch; `RESOLVED` nur statisch. | **Erfüllt.** | `dependentReturnTypesAreBoundToEachInvocation…`, `undefinedAndUnrestrictedActualOwnersRetainLegalSymbolicReturnDependencies`, `aClassValuedAttributeSuppliesItsDeclaredDependentAttributeArgument`; R2-Beobachtung O5 bleibt als bewusste Grenze dokumentiert; Codeinspektion `FunctionBindings.returnType`/`attributeMatches`/`sameBoundExpression`. |
| 3 | Inkompatible aufgelöste ENUM-/FORMAT-/Geometrie-/Klassenwerttypen in EXISTENCE sind `SOURCE_ERRORS`; Generic/Deferred/Pending bleiben `INCOMPLETE_SUPPORT`. | **Erfüllt.** | `ConstraintPathBindingContractTest.incompatibleResolvedExistenceEnumerationsAreSourceErrors`; `existenceDistinguishesAlignmentBlackboxTemporalAndEnumerationValues`; Codeinspektion `familyPending` enthält nur noch `GENERIC, DEFERRED, PENDING`. |
| 4 | Gemeinsamer SET-Wertpfadeinstieg anhand gebundenem lokalem/externem Constraintowner und revisionsgebundenem Hauptausdruckbereich; kein globaler SET-Zustand/Adaptercheck; Budget/Abbruch aktiv. | **Erfüllt.** | `GraphBuilder.isSetCondition` (Owner-Syntax + Range), `pathExpression`-Einstieg, `checkWork` in den Iterationen; `SetConditionContextContractTest` inkl. externem Block, Rollen, TEXT-Pfaden, Revision/FreshReuse; eigene Probe A/C/H/I. |
| 5 | 2.4-Funktionsvoraussetzung per budgetgeprüftem iterativem Scan echter Funktionsziele/Formaltypen; fehlender Aufruf → SEM-0005 + typisierter Fehler; keine Folgediagnose bei bereits fehlerhaften/nicht booleschen Bedingungen. | **Erfüllt.** | `hasObjectSetFunction` (BFS, `checkWork`, `ObjectArgumentType.objects()`); `SetConditionContextContractTest.only24Requires…`; eigene Probe A (keine SEM-0005 aus Fehlerpfaden), C, G. |
| 6 | Vergleich/Logik/Arithmetik/NOT erzeugen keine zusätzlichen Typfehler aus fehlerhaften Operanden; Übersetzungsvergleiche fehlerhafter Constraintausdrücke erzeugen keine abhängigen `TRANSLATION_CONSTRAINT`-Fehler; gültige Fälle bleiben verglichen. | **Erfüllt.** | Codeinspektion `binaryExpression`/`unaryExpression` (`!error && …`), `constraintHasErrors` nur bei `ERROR`; `SetConditionContextContractTest.alreadyErroneousSetExpressionsDoNotProduceDependentTranslationMismatches`; eigene Probe A (genau 2 SEM-0008, keine Folgefehler). |

## Eigene Gegenprobe

**Hypothese und fachliches Risiko:**
Die neue SET-Kontextregel könnte (a) direkte/nested Objektwerte nur oberflächlich
oder mit falscher Multiplizität/Kaskade ablehnen, (b) WHERE fälschlich
mitbestrafen, (c) die 2.4-`OBJECTS`-Voraussetzung durch einen Aufruf nur in
WHERE oder durch einen falschen Formaltyp erfüllen, (d) die bestätigten
N1/N2-Profile verletzen, (e) bei restringierten klassenwertigen Actuals den
Attributwert lexikalisch statt am tatsächlichen Owner binden, oder (f) bei
zyklischen abhängigen Formalparametern hängen/abstürzen. Die Probe ist neu
formuliert und nicht aus R1/R2 übernommen.

**Neu erstellter Testinput / exakter Pfad oder vollständige Quelle:**
`build/review/L2-1-r3/probe/R3SetContextCounterProbe.java` (nur unter dem
ignorierten `build/` des Review-Worktrees; keine Produkt-, Test-, Build- oder
Abnahmedatei geändert). Die Quelle nutzt ausschliesslich die öffentliche
Compiler-API (`CompilerEngines`, `CompilationRequest`, `SourceSet`,
`SemanticGraph`, `CompilationOutcome`). Sie läuft je 2.3/2.4 durch neun
Abschnitte:

- **A – Multiplizität/Kaskade:** 2.3/2.4 `SET CONSTRAINT objectCount(ALL)>=0 AND (n>0 OR flag)`; erwartet **genau zwei** `ILI-SEM-0008` an `n` und `flag` (UTF-16, Revision 7), keine `SEM-0005`, Top-Condition `ERROR`.
- **B – Attributtyp-Konstante vs. Wert:** `F.check(ALL,>>label)` zulässig und an `C.label` gebunden; `F.check(ALL,label)` mit genau einem `SEM-0008` an `label`.
- **C – Externer Constraint:** 2.3 `SET CONSTRAINT #true` zulässig; 2.4 abgelehnt mit genau einem `SEM-0005`; Hauptausdruck mit `objectCount(ALL)` in beiden Versionen zulässig.
- **D – Abhängiger Owner:** `cls:CLASS RESTRICTION(Data)` liefert für `F.check(cls,>>label)` `Data.label`, nicht `Host.label`, Owner `Viewable(Data)`, TEXT-Restriktion, keine Diagnose.
- **E – Zyklusrobustheit:** zyklische `ATTRIBUTE OF @…`-Formalparameter terminieren in 5 ms, werden als `SOURCE_ERRORS` ohne Failure-ID abgelehnt; beobachtet wird der bestehende `FUNCTION_ARGUMENT_CYCLE`-Vertrag (`ILI-SEM-0010`, bereits in `ParameterClassFunctionContractTest` geprüft).
- **F – N1/N2/N3-Grenze:** 2.4 `areAreas2(THIS…)`/`areAreas3(THIS…)` in `MANDATORY` zulässig, in `SET` mit genau einem `SEM-0008` an `THIS` abgelehnt; 2.3 `areAreas2` unbekannt; `objectCount(ALL)` in `MANDATORY` zulässig.
- **G – 2.4-Voraussetzung:** `SET CONSTRAINT WHERE F.flag(ALL):#true` in 2.3 zulässig, in 2.4 genau ein `SEM-0005`.
- **H – TEXT-Pfadgrenze:** dynamischer TEXT-Attributpfad `name` im SET-Hauptausdruck wird mit `SEM-0008@name` abgelehnt; derselbe Wert ist in WHERE zulässig.
- **I – Klassenkonstante vs. Attributwert:** `F.isClass(>Data)` im SET zulässig (mit zusätzlichem `objectCount(ALL)`), `F.isClass(cls)` mit genau einem `SEM-0008` an `cls` abgelehnt; `F.check(THIS)` in `MANDATORY` zulässig.

**Erwartetes Verhalten:**
Wie oben; insbesondere keine globale SET-Zustandsabhängigkeit, keine
Zusatzdiagnose aus fehlerhaften Operanden, keine lexikalische Ersatzbindung bei
restringiertem tatsächlichem Owner, kein Hänger/Internal Failure.

**Kommando / Exitcode / beobachteter tatsächlicher Output:**
```text
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem
$JAVA_HOME/bin/javac -cp 'interlis-cli/build/install/interlis-cli/lib/*' \
  -d build/review/L2-1-r3/probe/classes \
  build/review/L2-1-r3/probe/R3SetContextCounterProbe.java
$JAVA_HOME/bin/java \
  -cp 'build/review/L2-1-r3/probe/classes:interlis-cli/build/install/interlis-cli/lib/*' \
  R3SetContextCounterProbe
```
Am Kandidaten: **Exit 0**, Log
`build/review/L2-1-r3/probe/probe.log`:
```text
OBSERVE 2.3 E cycle diagnostics=[ILI-SEM-0010:diagnostic.semantics.inheritance, …]
OBSERVE 2.4 E cycle diagnostics=[ILI-SEM-0010:diagnostic.semantics.inheritance, …]
OBSERVE 2.4 H diagnostics=[ILI-SEM-0008@name]
R3_PROBE checks=66 failures=0
```
Dieselbe Probe gegen einen temporären separaten BASE-Worktree
(`81ad019…`, frisch `:interlis-cli:installDist`): **Exit 1**,
`build/review/L2-1-r3/probe/probe-base.log`, **54 auswertbare Checks /
22 rote Abschnitte**, u. a. A (keine Ablehnung, 0 Diagnosen), B (kein
SEM-0008), C (2.4-Konstante akzeptiert), D (falscher Owner/FUNCTION_ATTRIBUTE),
F (SET-THIS nicht abgelehnt), G (nur-WHERE akzeptiert), I (Klassenwert nicht
abgelehnt). Der temporäre Worktree wurde mit `git worktree remove --force`
entfernt; der Kandidat blieb unverändert.

**Ergebnis und Schlussfolgerung:**
Die Probe widerlegt die Risikobehauptungen am Kandidaten nicht: **66/66 Checks
PASS**. Sie ist am BASE tatsächlich rot (22 Abschnitte) und damit eine echte,
eigenständige Gegenprobe. Kein Finding aus dieser Probe.

## Pflichtprüfungen

**Ausgeführte Kommandos, tatsächlich ermittelter Exitcode, Anzahl Tests/Fehler/Skips:**

```text
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
tools/verification/without-python.cjs ./gradlew checkPortable g2EvidenceTest \
conformanceCheck sdkProcessCheck pythonFreeCheck --continue --rerun-tasks \
-PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node
```
**Exit 0**, `BUILD SUCCESSFUL in 2m 26s`, **58 actionable tasks: 58 executed**
(`--rerun-tasks`; kein `-x test`, kein stiller Testskip). Rohlog:
`build/reports/g2/L2-1/review-r3-mandatory.log`.

| Prüfbereich | Tests/Fälle | Failures | Errors | Skips | Beleg |
|---|---:|---:|---:|---:|---|
| Reguläre JVM-Tests gesamt | **788** | 0 | 0 | 0 | frische JUnit-XML unter `*/build/test-results/test` |
| davon Compiler | **679** | 0 | 0 | 0 | 129 XML-Dateien; 673 `@Test`-Methoden + 3 `@ParameterizedTest`-Expansionen |
| CLI / LSP / Model / Projection / Repository / Wasm-Bridge / Workspace | 33 / 16 / 16 / 12 / 19 / 3 / 10 | 0 | 0 | 0 | frische JUnit-XML |
| Evidenztests (`evidenceTest`) | **137** | 0 | 0 | 0 | 25+56+31+25 |
| Assertionstests (`g2AssertionTest`) | **15** | 0 | 0 | 0 | frische JUnit-XML |
| Host-/Verifikationstests (`verificationTest`) | **15** | 0 | 0 | 0 | frische JUnit-XML |
| SDK-Prozess | **9 Kommandos**; `SDK PASS … models=1` | – | – | – | `sdkProcessCheck` |
| Pythonfreiheit | **1207 Inputs** | – | – | – | `PYTHON_FREE_SOURCE_PASS 1207 inputs` |

Alle 175 frischen JUnit-XML-Dateien sind neuer als der Startmarker
`review-r3-env.log`; `tests/failures/errors/skipped` wurden aus den XML-Attributen
summiert, nicht aus einem Zusammenfassungs-JSON übernommen. Automatisch
`SKIPPED` waren nur die Nicht-Test-Tasks `emscriptenStubWasmGC`,
`emscriptenWasmGC` und `disasmWasmGC`; **kein Test** wurde deaktiviert. In den
Testquellen sind keine neuen `@Disabled`/`@Ignore`/`assumeTrue`-Skips.

**Rohberichte / CI-Artefakte und deren Bindung an CANDIDATE_SHA:**
`build/reports/python-free/environment.json` nennt `gitHead=900706a…`, `dirty=""`,
`status=passed`, Start/Ende des Laufs. `build/conformance/run-config.json` und
`build/conformance/summary.json` wurden in diesem Lauf um 08:28–08:29 UTC
erzeugt; `interlis-wasm-teavm/build/js-contract-result.json` und
`wasm-contract-result.json` um 08:29 UTC. Der Kandidaten-Worktree war vor und
nach dem Lauf sauber; `HEAD` blieb `900706a…`.

**Nicht ausgeführte fällige Prüfungen mit Grund:**
Keine. `checkBrowser` mit echtem Safari und das Web Image sind **nicht**
fällig, weil L2-1 gemäss Handoff **nicht** die letzte Lieferung einer Phase ist
und der Plan diese Matrix spätestens beim kumulativen L2-3-Gate verlangt; sie
werden hier nicht als ausgeführt behauptet. P3/P4/P5/T4 sind nicht Gegenstand
dieses Auftrags.

**Conformance-Fallmenge; rohe und projektspezifische Bilanz:**
- Ausgewählt: **593 / 610** Fälle (17 ausgeschlossen), Filter `^(ili23|ili24)\.`, externer gepinnter Runner `InterlisConformance.java`.
- **Roh:** `577 PASS / 15 FAIL / 1 XFAIL / 0 XPASS / 0 ERROR / TOTAL 593`; `overallResult=FAIL`, `exitCode=1`; `runner-exit.txt=1`. Das ist der erwartete Rohstand, kein stiller PASS.
- **Projektbewertung:** `577 PASS / 16 XFAIL / 0 FAIL / 0 XPASS / 0 ERROR / TOTAL 593`; **21/21** strenge Assessments `result=PASS`.
- Die 15 Roh-FAILs sind die 13 bisherigen plus die zwei ausdrücklich auf N3 gestützten Fälle `ili23.Constraint23Test.setCstrt` und `ili23.InterlisConversion23Test.iliconversion_ConversionOfSETCONSTRAINT`; der separate XFAIL ist `ili23.Attribute10Test.attributeComplete`.
- Eigene Nachläufe der beiden neuen Originale: `setCstrt` Exit 1 mit exakt zwei `ILI-SEM-0008` (`attrA1` 186–192, `attrB1` 442–448); Kontrolle Exit 0. `iliconversion_ConversionOfSETCONSTRAINT` Exit 1 mit exakt einem `ILI-SEM-0008` (`attr2` 240–245); Kontrolle Exit 0. Die UTF-16-Bereiche wurden unabhängig gegen den Originaltext geschnitten.
- **Norm-/Suite-/XFAIL-/Baseline-/Quellpin-Unverändertheit:** Suite-Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`, Archiv-SHA `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe` und `gradle/conformance-suite.properties` unverändert. Das neue Manifest hat **21** Fälle; die **19** alten Objekte sind
strukturell (deep-equal) unverändert, die zwei Ergänzungen sind exakt die genannten IDs mit
`authorizationBasis.kind=APPROVED_SET_OBJECT_CONTEXT_PROFILE` und dem wörtlichen N3-Satz. Das historische 19-Fälle-Manifest ist unter `docs/evidence/conformance/normative-expectations-before-set-context.json` bytegenau archiviert; SHA-256 `67244a8138bbe2c17e0feaecd7c08885be981904cb88d33cfaad980123a2e6c1` stimmt mit dem BASE-Stand überein. Das globale D2-Paket bleibt `PENDING_USER_REVALIDATION`/19 Fälle; der Evidenz-Canary prüft alte Objekte, fehlende/fremde Ergänzungen und erfundene Autorisierung (Test `setProfileAdditionsCannotChangeHistoricalCasesOrInventApproval` PASS). R1-/R2-Berichte sind unverändert (`e13320f8…` bzw. `509c1c03…`).
- Die beiden neuen Assessment-Quellenpins stimmen: Originale `5a288863…`/`42d33199…`, Kontrollen `407f1d04…`/`492aff80…`.

**Python-Migrations-/Phaseninvarianten soweit relevant:**
`git ls-files '*.py'` ist leer. Der Pflichtlauf wurde unter
`tools/verification/without-python.cjs` ausgeführt: privater PATH, `--no-daemon`,
`python`, `python3`, `pip`, `pip3`, `pypy`, `uv` jeweils `ENOENT`; absolute
`/usr/bin/python3` und `/usr/bin/pip3` jeweils `EPERM`. Kein Python/pip/uv
wurde für Hilfsprüfungen verwendet.

**Kumulative Phasenregression, wenn fällig:**
Nicht fällig (L2-1 ist nicht das Phasengate). Die volle T1-/T2-/T3-Regression
und das kumulative L2-1→L2-2→L2-3-Gate bleiben dem L2-3-Review vorbehalten.

## Findings

**Kein BLOCKER, kein MAJOR, kein offenes Muss-Finding.**

Beobachtungen ohne Abnahmewirkung:

- **O-R3-1 – Zyklusdiagnose.** Zyklische abhängige Formalparameter terminieren
  und werden als `SOURCE_ERRORS` ohne Failure-ID abgelehnt; der Diagnosecode ist
  der bestehende `FUNCTION_ARGUMENT_CYCLE`-Vertrag (`ILI-SEM-0010`,
  `diagnostic.semantics.inheritance`), der bereits in
  `ParameterClassFunctionContractTest` geprüft ist. Kein neuer Defekt, keine
  falsche Akzeptanz; die Meldung ist für einen pathologischen Deklarationsfall
  nur sprachlich unspezifisch.
- **O-R3-2 – Roh-FAIL-Sichtbarkeit.** Die zwei neuen Roh-FAILs sind bewusst und
  N3-bedingt; sie werden nicht als Raw-PASS versteckt, sondern über exakt zwei
  autorisierte Assessment-Objekte projektseitig als XFAIL bewertet.
- **O-R3-3 – Laufzeit-/Zahlenabweichungen.** Der eigene Lauf braucht 2m26s
  statt der im Implementierungsbericht genannten 2m20s/2m17s; die fachlichen
  Zahlen (788/679/137/15/15/9/1207/3459, 577/15/1 roh, 577/16/0 Projekt,
  21/21) stimmen mit den selbst ermittelten Werten überein. Rein redaktionell.
- **O-R3-4 – Automatische Task-Skips.** `emscriptenStubWasmGC`,
  `emscriptenWasmGC`, `disasmWasmGC` wurden von Gradle automatisch
  übersprungen; es sind keine Testfälle. Kein Pflichttest wurde übersprungen.
- **O-R3-5 – Kein Browser-/Safari-/Web-Image-Nachweis.** Für L2-1 nicht fällig;
  die Node-Läufe belegen ausdrücklich keinen Browser- oder Safari-Lauf. Diese
  Aussagegrenze bleibt für das spätere L2-3-Gate bestehen.

## Urteil

**PASS**

**Begründung und nächster konkreter Schritt:**
AC2-01 und AC2-02 sind für den geprüften L2-1-Umfang am exakten Kandidaten
`900706a177a047e6f7d0ffd34d9e4cb2853a2ad7` erfüllt. Der Pflichtlauf wurde
vollständig frisch mit `--rerun-tasks` und ohne Python-Interpreter ausgeführt
(Exit 0, 58/58 Tasks; 788 JVM-Tests, 137 Evidenz-, 15 Assertion-, 15 Hosttests,
0 Failures/Errors/Skips; SDK 9; Pythonfrei 1207; JS/WasmGC je 3459). T2 ist
mit der erwarteten Rohbilanz `577/15/1` und der Projektbilanz `577/16/0` sowie
`21/21` strengen Assessments belegt; die 19 alten Objekte, Suite-/Archivpins und
R1/R2-Berichte sind unverändert, die zwei Ergänzungen sind exakt N3-basiert und
als regelweite, nicht fallweise Freigabe gekennzeichnet. Die eigene neue
Gegenprobe ist am Kandidaten 66/66 grün und am BASE 22-fach rot. Es gibt kein
offenes Muss-Finding. Nächster Schritt gemäss Plan: L2-2 (Views/Inspektionen)
darf nach diesem PASS beginnen; L2-1 selbst ist **kein** Phasen-PASS, und
Safari/Browsermatrix/Web Image bleiben spätestens für L2-3 fällig. Kein Push,
kein Commit, kein Release, kein Start von L2-2 durch diesen Review.
