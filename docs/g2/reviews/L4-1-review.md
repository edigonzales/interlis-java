# Unabhängiger Review: L4-1 (R4)

Lieferabschnitt / Phase / letztes Phasengate?: **L4-1 / Phase 4 / nein** (das Phasengate ist L4-2 mit AC4-04–07).
Implementierungsagent: Codex.
Prüfagent: OpenCode CLI **v2.0.28**; tatsächliche Provider-/Modellkonfiguration: **provider `deepseek`, model `deepseek-flash`** (DeepSeek direkt, kein OpenRouter). Beleg: `launch.json`/Startkommando des äusseren Launchers `--model deepseek/deepseek-flash --agent build` sowie das Laufmanifest `build/reports/python-free/environment.json`. Die Modellselbstauskunft ist kein kryptografischer Identitätsnachweis.
BASE_SHA: `4a84068624cffde4549186c53cf23b9aaad7b179`
CANDIDATE_SHA (durch `git rev-parse HEAD` verifiziert): `e3a57c072bf93ff2351d6aa6fc551f4e562c4437`
Worktree: `/private/tmp/interlis-java-L4-1-review-e3a57c0-nopy` (sauber; `git status --porcelain` leer; eigene Proben ausschliesslich unter ignoriertem `build/review-r4/`).
Bericht: `/Users/stefan/sources/interlis-java/build/review/L4-1-r4/review.md` (ausserhalb des Kandidaten). Rohbelege: `build/review-r4/raw/` und Kopien unter `/Users/stefan/sources/interlis-java/build/review/L4-1-r4/raw/`.

## Prüfumgebung / Toolchain

| Werkzeug | Tatsächlich verwendet |
|---|---|
| Java | Temurin **25.0.3+9-LTS** (`JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem`) |
| Node | **v24.22.0** (`/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node`) |
| Gradle | Wrapper **9.8.1**, direkter Aufruf `./gradlew --no-daemon` (nicht der Python-freie Wrapper) |
| GraalVM | **25.0.3** (`/Users/stefan/.sdkman/candidates/java/25.0.3-graal`) |
| Binaryen | `binaryen-version_133` |
| Browser | Chromium **156.0.8078.4**, Firefox **157.0**, WebKit **27.2**, echtes Safari **27.0 (20625.1.29.18.28)** |
| Python | **nicht verwendet**; PATH nur `/var/folders/.../interlis-without-python-*/bin`; `python/python3/pip/pip3/pypy/uv` = ENOENT, absolute Interpreter = EPERM (Manifest des äusseren Launchers) |

## Kriterienprüfung

| Kriterium-ID | Urteil | Eigener Beleg | Finding |
|---|---|---|---|
| **AC4-01** Vollständigkeit, Grammatikzweige, Builtins, 80 Anforderungen | **erfüllt** | 1063 JUnit-Fälle in 199 frischen XMLs (886 regulär: **735 Compiler**, 41 Repository, 37 CLI, 22 Workspace, 7 Bridge, 16 Model, 16 LSP, 12 Projection; 144 Evidenz, 15 Assertions, 18 Host), 0 Failures/Errors/Skips, Header 199/199 konsistent. `g2EvidenceTest` bindet 369 Bedingungen, 80 Anforderungen, 321 Produktionen mit 1225 Occurrences, 21 Builtins, 130 Predefined, 21 Sondertypen an echte XML-Suiten/Klassen/Methoden; **264** Bindings, alle real, alle Quotenhashs stimmen. **F5 ist korrekt behoben:** `NORM:2.4:3.6.1:C03` hat sieben tatsächlich ausgeführte Methoden; der Testkörper von `MandatoryOverrideTypeContractTest.referenceContinuationUsesInheritedTypeBeforeConstraintBinding` enthält `MANDATORY` ohne Typ und prüft den geerbten `ReferenceType`; `DomainRestriction…mandatoryDomains` prüft `DomainRef` und `Attribute.mandatory`; `NamedCollectionElement…namedScalars` prüft `BAG`/`LIST`, `Cardinality` und `DomainReferenceType`; `CollectionType…listOfText` prüft den direkten `LIST OF TEXT`-Typ; `StructureValue…anyStructureAndOrderedRestrictions` prüft skalare/kollektive `RestrictedStructureRef`/`ANYSTRUCTURE`; `NoOidReference…referenceRestrictionsRemainBoundNavigable` prüft `REFERENCE` und Pfad; `StructureValue…unrelatedMissingAndWrongKindRestrictions` prüft negative `SOURCE_ERRORS`. Alle sieben Methoden wurden im frischen XML je einmal ausgeführt (`build/review-r4/raw/junit-summary.json`). Die 285/84-Bilanz, alle 369 IDs/Zitate/Hashpins und die 264 Bindings sind unverändert. Eigener semantischer Linkabgleich: 0 strukturelle Fehler; 49 Zero-Overlap-Kandidaten triagiert und die risikotragenden Körper/Helper gelesen (u. a. `NORM:2.4:3.7.1:C04` mit `Cardinality` im Helper `RoleTargetInheritanceContractTest.targets`, `NORM:2.3:2.8:C03` mit Enum-/Numeric-Konstanten, `NORM:2.4:3.14:C09` mit tatsächlichen `areAreasWithoutGaps`-Aufrufen); 10 keyword-NO-HIT-Fälle als oberflächliche Suchtreffer triagiert, deren Regeln durch die verlinkten Körper oder benachbarte direkte Assertions abgedeckt sind. 38 Single-Link-Fälle anhand von Quote/Link/Klasse triagiert; die alleinigen Modifierlinks betreffen tatsächlich `2.3 §2.2.5`/`2.4 §3.2.5` und die Propertylisten. 84 `OUTSIDE_G2_COMPILER`-Cluster sind EBNF-Metasyntax, Kapitelüberschriften, reine Transfer-/P4-Absätze, Laufzeit-/Geometrievalidierung, Beispiele oder nicht-normative Anhänge. Keine unerkannte semantische Linklücke gefunden. | **O1** (nicht blockierende redaktionelle Beobachtung) |
| **AC4-02** JVM-/Portable-/Referenz-Semantikvergleich | **erfüllt** | `semanticComparison`: Exit 0, vier echte gepinnte Quellen (2.3/2.4, je gültig/ungültig), **8 Mutationsprüfungen** (fehlender Member, falsches gleichnamiges Ziel, vertauschte Reihenfolge, fälschlich gültig, je Sprache), acht portable Projektionen (JS/WasmGC), gepinntes ili2c-Metamodell (JAR SHA-256 `37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e`). Grenzen ausdrücklich im Report: nur **3 Viewables** (`F.Child`, `F.T.Link`, `F.U.Link`), **4 Quellen**, gemeinsame Felder `outcome`, geordnete geerbte Member, voll qualifizierte Rollenziele; Diagnostikidentitäten, Origins, Ausdrucks-/Generics-/Translationsgraph sind **nicht** referenzgleich und werden separat durch direkte Assertions geprüft (`SemanticMemberProtocolTest`, `ViewInheritanceTranslationContractTest`, `RemainingLanguageContractTest`, alle im frischen XML grün). Eigene neue A3-Gegenprobe auf fremdem Modell `Z` (nicht `testdata/g2/a3`): `Z.Leaf` liefert auf **beiden** Backends `gamma,beta,alpha,delta,epsilon`; `Z.P.Rel` → `Z.P.Same`/`Z.P.Other`, `Z.Q.Rel` → `Z.Q.Same`/`Z.Q.Other`; doppelter Typname `D` auf beiden Backends abgelehnt. Kein Fixture-Echo. | – |
| **AC4-03** Referenzabweichungen/Profilentscheidungen | **erfüllt** | Zwölf historische Fälle in `L4-1-decisions.json` (11 Ablehnungen; `overlapTooSmallFail` mit 0.0001 akzeptiert, 0 abgelehnt) mit tatsächlichem Top-Level-`userDecision` vom 30.10.2026 und unveränderten Original-/Kontrollpins. `L4-1-model-class-decision.json` bestätigt die 2.3-Kompatibilität für modellweite Rollenziele; `L4-1-profile-questions.json` bestätigt **STRICT_OUTER_TYPE_NAMES** und **URI_REFERENCES_INCLUDING_RELATIVE**. Frischer Conformance-Lauf: 593/610 (17 INTERLIS-1 ausgeschlossen); roh **577 PASS / 15 FAIL / 1 XFAIL / 0 ERROR / 0 XPASS**; Projekt **577 PASS / 16 XFAIL / 0 FAIL/ERROR/XPASS**; **21/21** Assessments PASS. Eigene CLI-/Bridge-Proben: äusserer Schattenname abgelehnt (`SCOPE_OUTER_TYPE_NAME`), gültiges explizites `EXTENDED` akzeptiert; relative URI akzeptiert, Leerzeichen-/Nicht-ASCII-/1024-Zeichen-URI abgelehnt (`MODEL_ISSUER`); 3-Rollen-Aggregation abgelehnt (`AGGREGATION_ARITY`), 2-Rollen-Aggregation akzeptiert; ORDERED-Abschwächung abgelehnt (`ROLE_ORDER_EXTENSION`), ORDERED-Erhalt akzeptiert. Unabhängige Byteprüfung: Metadata-Original SHA-256 `1dd1…200d`, Kontrolle `2cbdb2…c795`, exakt zwei Renames und bytegleich; Gesuchsteller-Original `8f36…bfe`, Kontrolle `ffd5c0…c06a`, exakt zwei Renames und bytegleich. 13-Modelle-Corpus: 11 erfolgreiche Compiles, 2 exakt geprüfte Ablehnungen, 13 erfolgreiche Formatierungen. | – |

## Eigene neue Gegenproben (tatsächlich erfunden und ausgeführt)

Alle Proben liegen im ignorierten Worktree `build/review-r4/`; Rohbelege dort und unter `/Users/stefan/sources/interlis-java/build/review/L4-1-r4/raw/`.

### P1 – Semantischer Link-Audit (AC4-01, F1/F2/F5)

- **Input:** `testdata/g2/completion-links.json` (SHA-256 `ed8a3acb985dfac1297a4fee1294c8ea84f483954615706d7c222bb4691a7f25`) + gepinnte Normabsätze + tatsächliche Testkörper/Helper.
- **Hypothese:** Jede der 285 als `ASSERTIONS_LINKED` geführten Bedingungen hat mindestens einen gebundenen, öffentlich beobachteten Test; die 84 `OUTSIDE`-Cluster tragen keinen Scheinlink; die C03-Siebenmethodenzeile deckt alle `AttrTypeDef`-Zweige tatsächlich ab.
- **Kommando:** `node build/review-r4/probes/link-audit.cjs .` → **Exit 0**, `AUDIT_STRUCTURE_PASS`, `failures=0`; Counts `369/285/84/80/321/1225/21/130/21`, **264** Bindings; alle Quotenhashs gegen `g2-norm-conditions.json`; C03-Union `MANDATORY,DOMAIN_REF,BAG,LIST,OF,CARDINALITY,RESTRICTION,ANYSTRUCTURE,REFERENCE`; Roh: `raw/link-audit.json`, `raw/link-audit.log`.
- **Ergänzung:** `all-condition-audit.cjs` (nur 10 Keyword-NO-HITs, alle als oberflächlich bestätigt), `scope-audit.cjs`, `single-link.txt` (38 Fälle), `grammar-group-audit2.cjs` (30 Gruppen/321 Produktionen), `requirements.txt` (80 Anforderungen, alle mit Links oder `processEvidence`), `outside-conditions.txt` (84 Cluster). `c03-bodies.txt`/`c03-bodies2.txt` enthalten die tatsächlichen sieben Methodenkörper.
- **F1/F2-Nachprüfung:** `NORM:2.3:3.2.1:C01` = `OUTSIDE_G2_COMPILER` ohne Link; `NORM:2.3:2.2.1:C01` und `NORM:2.4:3.2.1:C01` = Whitespace-Links; beide areAreas-Absätze (`2.3 §2.12 C03`, `2.4 §3.12 C04`) haben `BuiltinSignatureFoundationContractTest…` als ersten Link, dessen Körper `areAreas`, `areAreas2/3` und `areAreasWithoutGaps` enthält; `2.3 §2.8.13.1 C04` und `2.4 §3.8.13.1 C05` sind die statischen Toleranzabsätze, `2.4 C06` ist `OUTSIDE` (reine Topologie).

### P2 – Fremdes A3-Modell auf TeaVM-JS und WasmGC (AC4-02)

- **Input:** neues 2.4-Modell `Z` mit `STRUCTURE Base/Mid/Leaf` (3-stufig; Felder `gamma,beta,alpha,delta,epsilon`), zwei Topics `P`/`Q` mit gleichnamigen Klassen `Same`/`Other` und je `ASSOCIATION Rel`; absichtlich ungültige Variante mit doppeltem Typnamen `D`. Keine Datei aus `testdata/g2/a3/`.
- **Hypothese:** Die öffentliche `semanticProperties`-Projektion liefert auf beiden Backends dieselbe tatsächliche Memberreihenfolge und voll qualifizierte Rollen-Ziele; die ungültige Variante wird auf beiden Backends abgelehnt.
- **Kommando:** `node build/review-r4/probes/l4-probe.cjs .` → **Exit 0**, `L4_PROBE_PASS 28 checks, 0 failed` (14 Checks je Backend). `Z.Leaf` = `gamma,beta,alpha,delta,epsilon`; `Z.P.Rel` = `left→Z.P.Same`, `right→Z.P.Other`; `Z.Q.Rel` = `left→Z.Q.Same`, `right→Z.Q.Other`; `D` auf JS und WasmGC `rejected`. Roh: `raw/l4-probe.json`, `raw/l4-probe.log`.

### P3 – Öffentliche Profil-/Outcome-Proben (AC4-01/AC4-03)

- **Input:** neue Quellen im selben `l4-probe.cjs`: äusserer Schattenname, gültiges `EXTENDED`, relative URI, URI mit Leerzeichen/Nicht-ASCII/1024 Zeichen, 3-/2-Rollen-Aggregation, ORDERED-Abschwächung/-Erhalt.
- **Beobachtung:** 14/14 je Backend PASS; Diagnose-Argumente enthalten `SCOPE_OUTER_TYPE_NAME`, `MODEL_ISSUER`, `AGGREGATION_ARITY`, `ROLE_ORDER_EXTENSION`; gültige Fälle `succeeded`. Roh: `raw/l4-probe.log`, `raw/l4-probe.json`.

### P4 – Domain-Grenzprobe (AC4-01, zusätzliche Gegenprobe)

- **Input:** frische 2.3-Quellen jenseits der Testdateien: `D (EXTENDED) EXTENDS Base`, `EXTENDS FINAL`, `MANDATORY` ohne Basis, `NUMERIC` ohne `ABSTRACT`.
- **Beobachtung:** `node build/review-r4/probes/domain-probe.cjs .` → alle vier `rejected`, mit `EXTENDED`-Kontext, `DOMAIN_FINAL`, `INHERITED_TYPE_CONTEXT`, `NUMERIC_ABSTRACT`. Der Compiler behandelt die Domänenregeln also tatsächlich, auch dort, wo der Completion-Link nur repräsentative Assertions nennt. Roh: `raw/domain-probe.log`.

## Pflichtprüfungen (frisch auf exakt `e3a57c0`)

Tatsächliches Kommando, **direkt** und nicht über den Wrapper:

```sh
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem ./gradlew --no-daemon \
  checkPortable checkBrowser :interlis-wasm-webimage:webImageProbe semanticComparison \
  g2EvidenceTest conformanceCheck sdkProcessCheck sdkPackage :interlis-lsp:installDist \
  pythonFreeCheck referenceCheck originalViewReferenceCheck viewReferenceCheck unitReferenceCheck \
  performanceCheck --continue --rerun-tasks \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
  -PgraalVmHome=/Users/stefan/.sdkman/candidates/java/25.0.3-graal \
  -PbinaryenHome=/Users/stefan/.cache/interlis-java/runtime/binaryen-version_133
```

| Prüfung | Ergebnis |
|---|---|
| Gesamtlauf | **Exit 0**, `BUILD SUCCESSFUL in 6m 19s`, **80 actionable tasks: 80 executed**; drei optionale Tasks `SKIPPED` (`emscriptenStubWasmGC`, `emscriptenWasmGC`, `disasmWasmGC`), keine fällige Pflichtprüfung übersprungen. Roh: `build/review-r4/mandatory-run.log` (SHA-256 `d49691ea39281ea1824f5c0d4425dd83ec1b76d13bb90aa98d36c809e7d7d498`). |
| JUnit | **1063** Tests in **199** XMLs; 886 regulär (735 Compiler, 41 Repository, 37 CLI, 22 Workspace, 7 Bridge, 16 Model, 16 LSP, 12 Projection), 144 Evidenz, 15 Assertions, 18 Host; **0 Failures / 0 Errors / 0 Skips**. Header: 199/199 `testsuite@name` = `testcase@classname`, `tests` = Anzahl `testcase`, Zeitstempel `2026-10-30T16:10:17Z`–`16:12:03Z`. C03: alle sieben Methoden je einmal ausgeführt. Zusammenfassung: `raw/junit-summary.json` (SHA-256 `2bfb04fda8a8a15df9af7cf225b2bd1709f32f333ba07921bb5ee18259c09bdb`). |
| `checkPortable` | TeaVM-JS/Node **3548 PASS**, WasmGC/Node **3548 PASS** (`interlis-wasm-teavm/build/js-contract-result.json`, `wasm-contract-result.json`). |
| `checkBrowser` | Chromium **3548**, Firefox **3548**, WebKit **3548**, echtes Safari **3548** (`browser-results.json`, `safari-result.json`). |
| WebImage | WebImage/Node **3548**; Chromium/Firefox/WebKit je **3548** (`interlis-wasm-webimage/build/webimage/result.json`, `webimage-browser/browser-results.json`). |
| `semanticComparison` | **Exit 0**, 4 Fälle, 8 Mutationen, Referenz-JAR `37d94573…`; `a3-portable.json` 8 Projektionen; Grenzen im Report (`build/reports/g2/semantic-comparison.json`, SHA-256 `1286cdc326a016ae2bfcfca65e54a84cf8a1ed18ce0a7ff77845abeafab63363`). |
| `g2EvidenceTest` | **144** Tests, 0 Fehler; `CompletionLinksTest` 7 Tests (1 + 6 Negativproben) PASS (`interlis-cli/build/test-results/evidenceTest/TEST-guru.interlis.cli.evidence.CompletionLinksTest.xml`). |
| `conformanceCheck` | 593/610 (17 INTERLIS-1 ausgeschlossen); roh **577 PASS / 15 FAIL / 1 XFAIL / 0 ERROR / 0 XPASS**; Projekt **577 / 16 XFAIL / 0 FAIL / 0 ERROR / 0 XPASS**; **21/21** Assessments PASS (`build/reports/conformance/conformance-summary.json`, SHA-256 `782dd38265e9324d5b5c06de974605128fd9101a23f1fc30d39e49de430793ca`). |
| `referenceCheck` | 5/5 PASS (`build/reports/stabilization/*reference*`). |
| `originalViewReferenceCheck` | **19/19** PASS, 0 Prozessfehler. |
| `viewReferenceCheck` | 134 eigene Erwartungen, **102** Übereinstimmungen, **6** sichtbare Referenzprozessfehler; keine Gleichheitsbehauptung (`views-reference-comparison.json`). |
| `unitReferenceCheck` | 123 eigene Erwartungen, **121** Übereinstimmungen, **0** Prozessfehler. |
| `pythonFreeCheck` | **1288** Inputs PASS; `git ls-files '*.py'` = 0. Äusseres Manifest `build/reports/python-free/environment.json`: `gitHead` exakt `e3a57c0…`, `dirty` leer, PATH nur ohne-python-Bin, `python/python3/pip/pip3/pypy/uv` ENOENT, absolute Interpreter EPERM, Profil-SHA `655915d1…`; `status: running` wird erst nach CLI-Ende vom äusseren Launcher finalisiert (Kopie nach `outer-environment.json`). |
| SDK/LSP | `SDK_PROCESS_PASS 9 commands`; SDK-ZIP `build/releases/interlis-java-sdk-0.2.0-SNAPSHOT.zip` (17'030'394 Bytes); `:interlis-lsp:installDist` erfolgreich. |
| `performanceCheck` | JVM **13** Modelle (11 erfolgreich, 2 exakt abgelehnt: `SO_AGI_Metadata_20230304`, `SO_AWJF_Gesuchsteller_20210202`); WasmGC **4**; Chromium/Firefox je 13 (12 gemessen, 1 sichtbare Stackausnahme `IliValidGwr_V1_0`), WebKit 13 (1 gemessen, 12 sichtbare Stackausnahmen). Erwartungs-Hash `testdata/g2/corpus-expectations.json` = `d1a4d7d6…`. P3-Messgrenzen sichtbar, keine G2-Browserpflichtskips (dort alle 3548). |
| T4 | 41 Repository-Tests, darunter `PinnedRealClosureTest` mit echter 2.3-`RoadsExdm2ien`- und 2.4-`CHBase_Part2_LOCALISATION_V2`-Closure, transitiven Imports, Pins/Locks, Offline-/Stale-Verträgen und öffentlichen Schema-Assertions (`interlis-repository/build/test-results/test/`). |

## Norm-/Suite-/Pin-/Baseline-Unverändertheit

- **Keine** Änderung unter `docs/evidence/conformance/` oder `testdata/conformance/` gegenüber `4a84068` (`git diff --stat` leer).
- Suite-Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3` und Archiv-SHA-256 `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe` unverändert; `baseline.json` (1 INTERLIS-1-XFAIL), die **21** aktiven Assessments und das historische **19-Fälle-Bytearchiv** unverändert; keine neue XFAIL-Ausnahme, kein XPASS.
- `testdata/g2/predefined-catalogue.json` ist nach Entfernen der `sourceAnchors` **semantisch identisch** zum Basisstand (eigener Vergleich). `g2-test-candidate-index.json`: Statusverteilung unverändert **1255× CANDIDATE_ONLY** (keine Promotion), nur Testmethodenzahl 704→729 und zwei Methodenoffsets. `g2-rule-matrix.json`: nur die Hashes/704→729 der Evidenzindizes geändert, keine Normbedingung/Quote.
- `testdata/g2/completion-links.json`: 369/285/84/80/321/1225/21/130/21 und 264 Bindings; alle 369 Quotenhashs gegen die unveränderte `g2-norm-conditions.json`; C03-Siebenmethodenzeile und alle übrigen Bindings hash-/XML-gebunden.
- Korpuskontrollen: Metadata-Original `1dd1c4fbfcabfb9966478e51b062178bf22a36206210126c1ae263ea3807200d` → Kontrolle `2cbdb2fe90e1c167226a6c07d40754f4cfe1ba0944bd25103bf9d317f4a7c795`, exakt zwei Renames, bytegleich; Gesuchsteller-Original `8f363a179dc03397776f98874143533a76a37602a0ca6724b5ecd78e478efbfe` → Kontrolle `ffd5c0edf590dfc172c146644277f1cb4376db83342bc56abe8efc5aecabc06a`, exakt zwei Renames, bytegleich.
- Gegenüber `b804c47` sind nur `docs/g2/STATUS.md`, `docs/g2/implementation/L4-1.md`, der unveränderte R3-Bericht und `testdata/g2/completion-links.json` geändert. Compiler/API/Testmethoden/Build/Suite/Normen/Erwartungen unverändert; `L4-1-review-before-link-fix.md` hat exakt SHA-256 `40c9ced549c1d626f6efcefe39bfa407e768e649293418774c9f242a922f37a5`, `L4-1-review-before-attribute-link-fix.md` exakt `76726599d96c1db740ea89e4856bc535e8bbcab81048ba84c2af25685d36c03a`.

## Findings

### F5 – geschlossen (bestätigt)

`NORM:2.4:3.6.1:C03` bindet nun sieben tatsächlich ausgeführte öffentliche Methoden. Die Körper wurden gelesen (nicht nur Namen), die Hashes stimmen, die Methoden liefen im frischen XML je einmal, und die Vereinigung deckt `MANDATORY` ohne Typ, `DomainRef`/`mandatory`, `BAG`/`LIST`/`Cardinality`, direkten `LIST OF TEXT`, skalare/kollektive `RestrictedStructureRef`/`ANYSTRUCTURE`, `REFERENCE`-Pfad und negative `SOURCE_ERRORS` ab. Der unpassende Propertylistenlink ist dort entfernt; die alleinigen Modifierlinks bei `2.3 §2.2.5`/`2.4 §3.2.5` bleiben fachlich passend.

### O1 – MINOR / redaktionell (nicht blockierend): Versionslabel in drei 2.3-Scopes

- **Ort:** `testdata/g2/completion-links.json`, `NORM:2.3:2.8:C01`, `C02`, `C03`.
- **Beobachtung:** `scope` und `resolution` lauten „2.4-Domainconstraints sind Compilerpflicht, keine spätere Ausführung.“, obwohl die Zeilen 2.3-Zeilen sind und die Quotes `2.3 §2.8` (Wertebereiche/Konstanten) zitieren. Die tatsächlich verlinkten Methoden (`DomainRestriction…`, `NumericAbstraction…`, `ExpressionConstraint…`, `TypeFamily…`, `ImportedDomainType…`, `NumericConstantBinding…`) sind reale, frische Compiler-Tests und decken die zentralen zitierten Regeln (MANDATORY/ABSTRACT/EXTENDS, Basistypfamilien, Enum-/Numeric-Konstanten) ab; das FINAL/EXTENDED-Verhalten ist zusätzlich durch `PredefinedBindingContractTest` bzw. die eigene Domain-Probe belegt. Die Zeile ist daher nicht unverknüpft.
- **Wirkung:** redaktionelle Evidenzungenauigkeit, keine Verletzung von AC4-01 (die Zuordnung zu ausgeführten Assertions ist vorhanden). Empfehlung für eine spätere Evidenzrevision: Versionslabel auf 2.3 korrigieren. Kein Produktcode betroffen.

## Urteil

**PASS** – AC4-01, AC4-02 und AC4-03 sind am exakten Kandidaten `e3a57c072bf93ff2351d6aa6fc551f4e562c4437` durch eigene Proben, tatsächliche Testkörper und den frischen 80-Task-Pflichtlauf belegt. F5 ist geschlossen; F1–F4 sind ebenfalls geschlossen. Die ausführbaren Tore sind grün: 1063 JUnit (0 Failures/Errors/Skips), 3548 je TeaVM-JS/WasmGC/Chromium/Firefox/WebKit/echtem Safari/WebImage, Conformance roh 577/15/1 und Projekt 577/16/0 mit 21/21 Assessments, SDK9/SDK-ZIP/LSP, Pythonfreiheit 1288 Inputs mit ENOENT/EPERM, T4-Closures und alle Benchmarkpfade. O1 ist eine nicht blockierende redaktionelle Beobachtung; es gibt kein offenes Muss-Finding.

**Nächster konkreter Schritt:** L4-2 (AC4-04–AC4-07) am dann aktuellen Produkt-SHA mit vollständigem kumulativem T1–T4-/Safari-/Web-Image-/SDK-/CLI-/LSP-/Pythonfrei-Gate. Kein Push, kein Merge, kein Release.

**Transporttrennung:** Der oben genannte **Exit 0** ist der Exit des Gradle-Pflichtlaufs, nicht der Exit des OpenCode-CLI-Transports. Der CLI-Transportexit wird ausschliesslich vom äusseren Launcher unverfälscht protokolliert; aus dem grünen Gradle-Lauf wird kein erfolgreicher CLI-Transport abgeleitet. Das äussere Manifest zeigt vor CLI-Ende `status: running` mit exaktem Head `e3a57c0…`, leerem Dirty-Stand, PATH-ENOENT und absoluten EPERM-Proben; die finale Status-/Kopierprovenienz schreibt der Launcher nach `outer-environment.json`.
