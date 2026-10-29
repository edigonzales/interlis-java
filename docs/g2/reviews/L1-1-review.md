# Unabhängiger Review: L1-1

Lieferabschnitt / Phase / letztes Phasengate?: **L1-1 / Predefined-Syntax-Import / nein.** Das vollständige kumulative Phase-1-/Safari-/WebImage-Gate ist laut Handoff L1-3.
Implementierungsagent: Codex
Prüfagent: OpenCode v2.0.28, gestartet mit `--model deepseek/deepseek-flash`; tatsächliche Startkonfiguration: **Provider DeepSeek DIREKT, UI DeepSeek V4.1 Flash 1017, kein OpenRouter.** Eine Modellselbstauskunft ist kein Identitätsnachweis; die Konfiguration stammt aus dem tatsächlichen Start.
BASE_SHA: `b0032e9627fc76128aade70c9cd98985cb0b2b98`
CANDIDATE_SHA (durch git überprüft): `6cbf8de422cd8c0c8c9ec1ea900d729053ea660f`
Prüfumgebung / Toolchain: eigener sauberer Worktree `/private/tmp/interlis-java-L1-1-review-6cbf8de`; macOS arm64; Temurin `25.0.3+9` (`JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem`); Gradle Wrapper `9.8.1`; Node `24.22.0` (`/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node`); Playwright für L1-1 nicht benötigt und nicht verwendet; ili2c `5.6.9-bf16026e83f2377153b2e818a4f3838f0a423722` aus `/Users/stefan/.cache/interlis-java/ili2c.jar`, SHA-256 `37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e`. Kein Python, kein pip, kein uv. `git rev-parse HEAD` = `6cbf8de422cd8c0c8c9ec1ea900d729053ea660f`; `git status --porcelain` vor und nach der Prüfung leer; `git diff --check BASE..CANDIDATE` Exit 0; `git diff --stat` 23 Dateien, `+1465/−638`. Produktquellen, Tests, Builddateien und Kriterien blieben unverändert. Der Bericht liegt ausserhalb des Produktcommits unter `/Users/stefan/sources/interlis-java/build/review/L1-1/review-r3.md`; alle relativen Rohpfade unten liegen im Prüf-Worktree.

## Kriterienprüfung

| Kriterium-ID | Erfüllt / verletzt / nicht prüfbar | Eigener Test-/Code-/CI-Beleg | Finding-ID |
|---|---|---|---|
| **AC1-01** Vordefinierte Modelle, Domains, Klassen/Strukturen/Units und versionsabhängige Namen öffentlich korrekt gebunden; insbesondere `LineGeometry`/`SurfaceEdge`/`SurfaceBoundary` | **erfüllt** | Frischer Compilerlauf **630** Tests, 0 Failures/Errors/Skips; darunter die 9 neuen `LanguageFoundationContractTest`-Methoden. Eigene, neu erfundene Probe `build/review/L1-1/probe-r3/ReviewProbeR3.java`: **Exit 0, `REVIEW_PROBE_R3_PASS checks=409`**. Probe A prüft kanonische Owner in einem sprachgemischten Snapshot in beiden Richtungen (auch für die Sprache ausserhalb der Closure), Closure-Sprachmenge, Fresh/Reuse nach Root-Wechsel und nach Quellen-Edit. Probe B prüft die Geometrie-/Kardinalitäts-/StartSegment-Inhalte, kanonische versus kontextuelle Herkunft für Struktur/Attribut/Constraint und konkrete Vertex-Typen in 2.3 und 2.4. Probe C prüft die materialisierte `members()`-Reihenfolge breit (35 Member in 2.3, 37 in 2.4) gegen die aus `testdata/g2/predefined-catalogue.json` seiten-/offset-abgeleitete Anhang-A-Reihenfolge (76 bzw. 83 Top-Level-Deklarationen) sowie strikt steigende `declarationOrder`. Sechs gepinnte Originalgeometriequellen: alle sechs SHA-256 unverändert, eigener CLI 6/6 Exit 0/`ok`, ili2c 6/6 Exit 0. Kein offenes Finding; F1/F2 sind unabhängig als behoben bestätigt. | — |
| **AC1-02** Lexer/Parser/Name-/Importregeln, Sichtbarkeit/Forward-References/Recovery für 2.3/2.4 mit positiven/negativen und öffentlichen Query-Fällen; Fehler/Implementierungslücke/interner Fehler unterscheidbar | **erfüllt** | Frischer Compilerlauf **630/0/0/0**; die bestehenden Lexical-/Recovery-/Name-/Import-/Scope-/Outcome-Suiten sind unverändert und frisch grün. Die 9 neuen Methoden decken qualifizierte und ausdrücklich unqualifizierte Namen, fehlenden Import, falsche Gross-/Kleinschreibung, unbekannte Geometrienamen, Original-UTF-16-Bereiche/-Revisionen, Fresh/Reuse nach Import-Edit und das freigegebene TYPE-MODEL-Profil ab. Eigene Probe B prüft zusätzlich den expliziten `IMPORTS INTERLIS`-Zielbezug, das `INTERLIS`-Präfixsegment und die exakten Segment-Slices am öffentlichen `NameReference`; eigene CLI-Kontrollen unterscheiden SOURCE_ERRORS/`ILI-SEM-0010` mit `TYPE_MODEL_CONTENT` von Erfolg. Keine Grammatikänderung im Diff. | — |

## Eigene Gegenprobe

**Hypothese und fachliches Risiko:** Der neue kanonische Owner-/`members()`-Pfad könnte (a) eine im Snapshot materialisierte Sprache ausserhalb der angeforderten Closure erneut ohne vollständigen Owner lassen (Rest von F1), (b) bei einem Root-/Closure-Wechsel oder einem Quellen-Edit veraltete Mitglieder bzw. eine falsche Closure-Sprachmenge über Reuse veröffentlichen, (c) die materialisierte `members()`-Reihenfolge ausserhalb der wenigen Produktassertionen verletzen (Rest von F2), oder (d) kanonische und kontextbezogene Geometrie wieder vermischen, insbesondere den Benutzer-Vertex in die kanonische `SurfaceEdge.Geometry` tragen und Präfix-/Importziele falsch binden.

**Neu erstellter Testinput / exakter Pfad:** `build/review/L1-1/probe-r3/ReviewProbeR3.java` (unabhängig aus der öffentlichen Compiler-API geschrieben, keine Kopie einer Produktmethode; 409 Checks). Quellen werden im Speicher erzeugt; zusätzlich eigene CLI-Eingaben unter `build/review/L1-1/geometry-r3/` und `build/review/L1-1/type-model-r3/`.

**Erwartetes Verhalten:**
- In einem `AnalysisSnapshot` mit den Quellen `M23` (2.3, `INTERLIS.LineGeometry`) und `M24` (2.4, `INTERLIS.SurfaceEdge`) existieren zwei sprachgetrennte kanonische `INTERLIS`-Modelle. Auch wenn nur `M23` als Root gewählt wird, bleibt der materialisierte 2.4-Owner vollständig (und spiegelbildlich); die Closure-Sprachmenge bleibt genau die Root-Sprachmenge; `rootModels()` enthält nur den Benutzerroot; Reuse stimmt mit frischer Analyse überein. Nach einem Quellen-Edit (2.4 verwendet nur noch `LineGeometry`) bleibt kein veraltetes `SurfaceEdge`-Materialisat zurück.
- `LineGeometry.Segments` = LIST {0..*}, Element `LineSegment`; `SurfaceEdge.Geometry` = gerichtete POLYLINE ohne Vertex/LineForms; `LineAttrs` (ANYSTRUCTURE, optional) nur 2.3; `SurfaceBoundary.Lines` = LIST {0..*}, Element `SurfaceEdge`; genau eine kanonische `isOfClass(Segments[FIRST], StartSegment)`-Bedingung mit leerem `predefined:/INTERLIS-<sprache>`-Bereich. Die kontextbezogene Inspektionskopie hat eine eigene Identität, `DerivedOrigin(..., CONTEXTUALIZATION)` für Struktur/Attribut/Constraint, einen Benutzerquellbereich für den Constraint-Ausdruck und einen konkreten Vertex-Typ; die kanonische `LineSegment.SegmentEndPoint` bleibt unverändert; `findQualifiedName("INTERLIS.LineGeometry")` bleibt eindeutig.
- Ein breit materialisierender Input (Linienformen, Einheiten, Zeit-/OID-/Metadaten-Deklarationen, Geometrien) erzeugt `ModelSymbol.members()` exakt in der aus dem gepinnten Katalog abgeleiteten Anhang-A-Reihenfolge, mit strikt steigender `declarationOrder`; `STRAIGHTS`/`ARCS` stehen vor `ANYUNIT`.

**Kommando / Exitcode / beobachteter tatsächlicher Output:**
```text
javac --release 25 -cp <compiler/model/antlr4-runtime> -d build/review/L1-1/probe-r3/classes \
  build/review/L1-1/probe-r3/ReviewProbeR3.java          # Exit 0
java -cp <probe/classes:compiler:model:antlr4-runtime> ReviewProbeR3   # Exit 0
```
Output (`build/review/L1-1/probe-r3/probe-r3.log`):
```text
SECTION_MIXED_REUSE_PASS
SECTION_GEOMETRY_PASS 2.3
SECTION_ORDER_PASS 2.3 members=35
SECTION_GEOMETRY_PASS 2.4
SECTION_ORDER_PASS 2.4 members=37
REVIEW_PROBE_R3_PASS checks=409
```

**Ergebnis und Schlussfolgerung:** Die Probe konnte keine der vier Risikobehauptungen widerlegen. Besonders relevant: Der F1-Fall ist in beiden Richtungen auch bei einem Root-Wechsel und nach einem Quellen-Edit vollständig; die breite Reihenfolgeprüfung bestätigt F2 über die Produktassertion hinaus für 35/37 materialisierte Deklarationen. Kein neues Finding.

**Zusätzliche eigene Referenzkontrollen (keine Codex-Evidenz):**
- Sechs Originalquellen aus `docs/evidence/stabilization/g2-A2-predefined-binding-observations-2026-10-27.json` extrahiert (`build/review/L1-1/geometry-r3/manifest.json`); alle sechs SHA-256 stimmen mit den Pins überein. Eigener Compiler: 6/6 Exit 0/`ok` (`cli-r3.log`); ili2c 5.6.9: 6/6 Exit 0, `...compiler run done` (`ili2c-r3.log`).
- TYPE MODEL, frische ili2c-5.6.9-Kontrollen (`type-model-r3/ili2c-r3.log`): Struktur Exit 0, abstrakte Klasse Exit 0, konkrete Klasse Exit 1, Topic Exit 1, Metadatenbehälter Exit 1; zusätzlich Laufzeitparameter Exit 0. Der eigene Compiler (`type-model-r3/own-cli-r3.log`): Struktur und abstrakte Klasse Exit 0/`ok`; konkrete Klasse, Topic, Laufzeitparameter und Metadatenbehälter Exit 1/`SOURCE_ERRORS` mit `ILI-SEM-0010` (Metadatenbehälter zusätzlich `ILI-SYN-0002`); das Regelargument enthält `TYPE_MODEL_CONTENT` (z. B. `TYPE MODEL may only declare type definitions/TYPE_MODEL_CONTENT`). Damit ist die ausdrücklich beauftragte Begrenzung umgesetzt: Die Nutzerentscheidung „Wie ili2c: Strukturen und abstrakte Klassen zulassen (empfohlen)“ lässt nur Strukturen und abstrakte Klassen zusätzlich zu; TOPIC, konkrete Klassen, Laufzeitparameter und Metadatenbehälter bleiben verboten. Der ili2c-Parameterbefund ist eine bewusst strengere Projektgrenze, keine Verletzung der Nutzerentscheidung und kein Finding.

## Pflichtprüfungen

**Frischer Pflichtlauf (Node/Java, ohne Python):**
```text
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
tools/verification/without-python.cjs \
./gradlew check checkPortable g2EvidenceTest conformanceCheck sdkProcessCheck pythonFreeCheck \
  --continue --rerun-tasks \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node
```
- **Exit 0**, `BUILD SUCCESSFUL in 2m 37s`, **58 actionable tasks: 58 executed**. Der Harness ergänzt `--no-daemon`, privates PATH und die vererbte OS-process-exec-Sperre. `check` und `g2EvidenceTest` waren explizit im Graph und wurden frisch ausgeführt; `checkPortable` zog die echten Node-JS-/WasmGC-Verträge. Rohlog: `build/review/L1-1/mandatory-run-r3.log`.
- **XML-Zähler (frisch, je `tests/failures/errors/skipped`):** Compiler **630/0/0/0**, CLI 33/0/0/0, LSP 16/0/0/0, Model 16/0/0/0, Projection 12/0/0/0, Repository 19/0/0/0, Wasm-Bridge 3/0/0/0, Workspace 10/0/0/0 → **739 reguläre JVM-Tests, 0 Fehler/Skips**. Evidenz **136/0/0/0**, Assertion **15/0/0/0**, Verification/Host **15/0/0/0**. Gesamt **905 Tests, 0 Failures, 0 Errors, 0 Skips**. XML-Frische: 170 Dateien, `2026-10-29T19:55:01Z` bis `19:55:27Z`; Roh-XMLs in den jeweiligen Modul-`build/test-results/`. `LanguageFoundationContractTest` selbst: **9/0/0/0**.
- **Portable T3:** `interlis-wasm-teavm/build/js-contract-result.json` und `wasm-contract-result.json`: je `stabilizationCases=3299`, `status=passed`, `node=v24.22.0` (TeaVM-JS bzw. WasmGC); die 20 neuen L1-1-Fälle sind enthalten (Basis 3279). Keine Browser-/Safari-Aussage abgeleitet.
- **SDK:** `SDK_PROCESS_PASS 9 commands`; `sdkExampleTest` im selben Lauf.
- **Conformance (frisch):** `selected=593`; **Rohbilanz 580 PASS / 12 FAIL / 1 XFAIL** (`referenceSummary`, `referenceResult=FAIL`, `rawExit=1`); **Projektbilanz 580 PASS / 13 XFAIL / 0 FAIL / 0 XPASS / 0 ERROR**, `overallResult=PASS`; **18/18 Assessments PASS** (`assessmentCaseIds` = 18). Rohberichte: `build/reports/conformance/conformance-summary.json`, `conformance-log.txt` (Frischzeit 20:57).
- **Pythonfreiheit:** `build/reports/python-free/environment.json`: `gitHead=6cbf8de422cd8c0c8c9ec1ea900d729053ea660f`, `dirty=""`, `status=passed`, `exitCode=0`; Proben `python/python3/pip/pip3/pypy/uv` = `ENOENT`, absolute `/usr/bin/python3` und `/usr/bin/pip3` = `EPERM`; `PYTHON_FREE_SOURCE_PASS 1187 inputs`; `git ls-files '*.py'` = 0. Kein Python in den geänderten Produktpfaden.

**Rohberichte / Bindung an CANDIDATE_SHA:** Alle oben genannten Reports wurden im Lauf für exakt `6cbf8de...` erzeugt; `environment.json` bestätigt diesen Head und `dirty=""`. Die frischen XML-Zeitstempel liegen innerhalb des Laufzeitfensters (`19:54:51Z` bis `19:57:29Z`).

**Nicht ausgeführte fällige Prüfungen mit Grund:** Keine L1-1-fällige Prüfung wurde ausgelassen. Nicht ausgeführt und für L1-1 **nicht fällig**: `checkBrowser`/echtes Safari/Web Image, `performanceCheck`/JVM-/Wasm-/Browser-Benchmarks und die vollständige kumulative Phase-1-Abnahme. Diese sind laut Handoff L1-3 bzw. P3/P4/P5; die betroffenen gemeinsamen T3-Verträge wurden über Node/TeaVM ausgeführt. Die Reviewunabhängigkeit dieses Berichts ist keine vorweggenommene Phase-1-Freigabe.

**Conformance-Fallmenge; rohe und projektspezifische Bilanz:** 593/610 ausgewählt; roh **580 PASS / 12 FAIL / 1 XFAIL**; Projekt **580 PASS / 13 XFAIL / 0 FAIL / 0 XPASS / 0 ERROR**; 18/18 Zusatzprüfungen PASS. Roh- und Projektbewertung bleiben getrennt.

**Norm-/Suite-/XFAIL-/Baseline-/Quellpin-Unverändertheit:**
- `gradle/conformance-suite.properties` unverändert: Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`, SHA-256 `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe`. `gradle/conformance.gradle`, `docs/evidence/conformance/`, `interlis-cli/src/evidenceTest/` und `testdata/g2/a1-evidence-profile.json` sind im Diff `BASE..CANDIDATE` nicht enthalten; keine Grammatik-, XFAIL-, Baseline- oder Suiteänderung (`git diff --name-only` enthält weder `.g4`/ANTLR noch Conformance-/Suite-/XFAIL-Dateien).
- `testdata/g2/predefined-catalogue.json` ändert ausschliesslich Quellanker/Hashes (8 zusätzliche reine Strukturzeilen durch den neuen `PredefinedModels`-Anker); Normzitate, Seiten, Pfade, IDs und Erwartungen bleiben unverändert. `docs/evidence/stabilization/g2-source-inventory.json` ändert nur Quell-/Zeilen-/Methodenlocator und Hashanker sowie neue Fabrikanker; `g2-rule-matrix.json` nur Hashes/Locator und die reale Testmethodenzahl `615 → 624` (CANDIDATE_ONLY). Keine stillen Erwartungsänderungen.
- Die 18 Suite-Zusatzerwartungen und die sechs früher freigegebenen OID-Fälle bleiben unverändert. `ModelKindContractTest` ändert ausschliesslich die ausdrücklich freigegebene abstrakte-Klasse-Positiverwartung und ersetzt die alte Negativerwartung durch eine konkrete Klasse; `PredefinedOidIdentityContractTest` verschärft die alte `owner-empty`-Assertion auf den tatsächlichen vordefinierten Owner. Die beiden unveränderten früheren Reviewberichte `docs/g2/reviews/L1-1-review-9cc8073-FAIL.md` und `L1-1-review-35f09fb-FAIL.md` sind byte-identisch mit den externen Originalen (SHA-256 `76b7ae22…` bzw. `51e27337…`).
- PDF-/Normpins: `build/norms/ili23.pdf` SHA-256 `8ab1c6b5815aa31f424389d7ffd7af95fe0480d95e24ed926366e8db75afbc52` und `build/norms/ili24.pdf` SHA-256 `a8fa2679615ed4bd6939055f3adfd799e0e101afdf7c4cdea9ba85d177e75069` stimmen mit `testdata/g2/predefined-catalogue.json` überein. Referenzstellen: TYPE MODEL 2.3 §2.5.1 / PDF S. 26 und 2.4 §3.5.1 / PDF S. 32; Geometrie-Anhang A 2.3 S. 94/96–97 und 2.4 S. 121/124. Die Anhang-A-Reihenfolge wurde aus den gepinnten Seiten-/Offsetdaten des Katalogs unabhängig neu berechnet (76 Top-Level-Deklarationen in 2.3, 83 in 2.4); keine PDF-Neuextraktion mit Python.

**Python-Migrations-/Phaseninvarianten soweit relevant:** 0 getrackte `.py`; `pythonFreeCheck` unter der echten OS-Sperre erfolgreich; keine Hinweise auf eine verborgene Python-Abhängigkeit in den geänderten Pfaden. Keine Phase-0b-Invariante verletzt.

**Kumulative Phasenregression, wenn fällig:** Nicht fällig. L1-1 ist nicht die letzte Lieferung der Phase; die vollständige Phase-1-/Browser-/WebImage-Abnahme ist L1-3.

**Diffumfang:** 23 Dateien, `+1465/−638`, darunter `GraphBuilder`, `LineForms`, neues `PredefinedModels`, `ViewBuilder`, 9 neue `LanguageFoundationContractTest`-Methoden, die freigegebene `ModelKindContractTest`-Anpassung, die verschärfte OID-Owner-Assertion, Evidenzanker und die synchronisierten Kapitel 05/06/15/16/17. Keine neue öffentliche Signatur, keine Grammatikdatei, keine Python-Datei.

## Findings

**F1 / behoben (kein offenes Finding).** Der auf `9cc8073` reproduzierte unvollständige Owner einer materialisierten Sprache ausserhalb der Closure ist am aktuellen SHA nicht reproduzierbar. Meine Probe A prüft beide Richtungen mit unterschiedlichen vordefinierten Deklarationen: In einem Snapshot mit 2.3- und 2.4-Quellen bleibt der Owner der jeweils nicht als Root gewählten Sprache vollständig; alle materialisierten Top-Level-Symbole haben den Owner ihrer Sprache und stehen in dessen `members()`; `languageVersions()` bleibt die Closure-Sprachmenge; `rootModels()` enthält nur den Benutzerroot. Zusätzlich stimmen Reuse nach Root-Wechsel und nach einem Quellen-Edit mit der frischen Analyse überein, und nach dem Edit bleibt kein veraltetes `SurfaceEdge`-Materialisat zurück.

**F2 / behoben (kein offenes Finding).** `PredefinedModels.ORDER` enthält jetzt `STRAIGHTS`, `ARCS` vor `ANYUNIT`. Unabhängig vom Codex-Audit habe ich die vollständige materialisierte Reihenfolge gegen die aus dem gepinnten Katalog abgeleitete Anhang-A-Folge geprüft: Der eigene breite Input materialisiert 35 Member in 2.3 bzw. 37 in 2.4; die Folge ist exakt die gefilterte Anhang-A-Folge, `STRAIGHTS < ARCS < ANYUNIT`, und alle `declarationOrder`-Werte sind strikt steigend. Die Produktregression `predefinedLineFormsPrecedeUnitsAndTypesInAppendixOrder` läuft zusätzlich frisch mit.

**Keine neuen MAJOR-/BLOCKER-Findings.** Die früheren F1-/F2-Korrekturen ändern keine Norm-, Suite-, XFAIL- oder Baseline-Erwartung.

## Urteil

**AC1-01: PASS.**
**AC1-02: PASS.**
**Gesamturteil L1-1: PASS.**

**Begründung:** Der Kandidat `6cbf8de422cd8c0c8c9ec1ea900d729053ea660f` ist sauber und wurde vollständig am exakten SHA geprüft. Alle fälligen Pflichtprüfungen sind frisch und grün: Exit 0, 58 Tasks, 739 reguläre JVM-, 136 Evidenz-, 15 Assertion- und 15 Host-Tests ohne Fehler/Skips; 3299 Node-JS-/WasmGC-Fälle je Profil; T2 roh 580/12/1, Projekt 580/13/0 mit 18/18 Assessments; SDK 9; Python-frei mit ENOENT/EPERM. Die sechs Originalgeometrien sind mit eigenem Compiler und ili2c je 6/6 Exit 0 belegt; das TYPE-MODEL-Profil ist in beiden Sprachen gegen die ausdrückliche Nutzerentscheidung und die Begrenzung geprüft. Die neue unabhängige Gegenprobe (409 Checks) bestätigt F1 und F2 als behoben und fand kein neues Finding. L1-1 ist nicht das kumulative Phasengate; dieser PASS ist keine Vorabfreigabe für L1-2/L1-3 oder Phase 1 insgesamt.

**Nächster konkreter Schritt:** Nach diesem L1-1-PASS darf Codex gemäss Nutzerauftrag mit **L1-2** beginnen. Kein Commit/Push/Release durch diesen Reviewer; kein L1-2 wurde hier begonnen.
