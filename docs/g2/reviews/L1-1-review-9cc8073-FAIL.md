# Unabhängiger Review: L1-1

Lieferabschnitt / Phase / letztes Phasengate?: **L1-1 / Predefined-Syntax-Import / nein.** Das vollständige kumulative Phase-1-/Safari-/WebImage-Gate ist laut Handoff L1-3.
Implementierungsagent: Codex
Prüfagent: OpenCode; tatsächliche Provider-/Modellkonfiguration: **OpenCode v2.0.28, Provider DeepSeek direkt, Modell `deepseek/deepseek-flash` (UI: DeepSeek V4.1 Flash 1017); kein OpenRouter.** Die Selbstauskunft des Modells ist kein Identitätsnachweis; die Konfiguration stammt aus dem tatsächlichen Start.
BASE_SHA: `b0032e9627fc76128aade70c9cd98985cb0b2b98`
CANDIDATE_SHA (durch git überprüft): `9cc8073409781e4fa3c48f4334f7de6c2d9220fd`
Prüfumgebung / Toolchain: sauberer separater Worktree `/private/tmp/interlis-java-L1-1-review-9cc8073`; alle im Bericht genannten relativen `build/...`-Rohpfade liegen in diesem Prüf-Worktree (der Bericht selbst liegt bewusst unter `/Users/stefan/sources/interlis-java/build/review/L1-1/review.md` ausserhalb des Produktcommits); macOS arm64; Temurin `25.0.3+9` (`JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem`); Gradle Wrapper `9.8.1`; Node `24.22.0` (`/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node`); Playwright `1.64.1`; ili2c `5.6.9-bf16026e83f2377153b2e818a4f3838f0a423722` aus `/Users/stefan/.cache/interlis-java/ili2c.jar`. `git status --porcelain` vor und nach der Prüfung leer; `git diff --check BASE..CANDIDATE` Exit 0. Es wurden keine Produktquellen, Tests, Builddateien oder Kriterien verändert.

## Kriterienprüfung

| Kriterium-ID | Erfüllt / verletzt / nicht prüfbar | Eigener Test-/Code-/CI-Beleg | Finding-ID |
|---|---|---|---|
| **AC1-01** Vordefinierte Modelle, Domains, Klassen/Strukturen/Units und versionsabhängige Namen öffentlich korrekt gebunden; insbesondere `LineGeometry`/`SurfaceEdge`/`SurfaceBoundary` | **verletzt** | Einsprachige Fälle sind grün: 7 neue Methoden in `LanguageFoundationContractTest` (628 Compiler-Tests, 0 Fehler/Skips), eigene `ReviewProbe` mit 49 Assertions (Materialisierung, kanonische/kontextuelle Identität, Constraint-Bindung, TYPE-MODEL-Profil) Exit 0; die sechs Originalquellen sind mit eigenem Compiler und ili2c je 6/6 akzeptiert. **Aber:** In einem öffentlichen `AnalysisSnapshot` mit einem Modell der anderen Sprache ausserhalb der angeforderten Closure bleibt der kanonische Owner unvollständig (F1). | **F1** |
| **AC1-02** Lexer/Parser/Name-/Importregeln, Sichtbarkeit/Forward-References/Recovery für 2.3/2.4 mit positiven/negativen und öffentlichen Query-Fällen; Fehler/Implementierungslücke/interner Fehler unterscheidbar | **erfüllt** | Frisches `:interlis-compiler:test` 628/0/0/0; darin 7 neue Methoden, u. a. `absentImportWrongCaseAndUnknownGeometryNameRemainSourceErrors` (SOURCE_ERRORS + `ILI-SEM-0001`), `predefinedGeometryUsesTheActualQualifiedOrUnqualifiedSourceRange` (UTF-16-/Revisionsprüfung), `importEditsMatchFreshAnalysisAndDoNotMutatePublishedDeclarations` (Fresh/Reuse), `ordinaryAndContextualGeometryKeepDistinctIdentitiesAndVertexTypes`. Bestehende Lexical-/Recovery-/Name-/Import-/Scope-/Outcome-Suiten unverändert und grün; keine Grammatikänderung im Diff. Der sprachübergreifende Analysegraph ist F1 und wird dort (Owner-Vertrag AC1-01) geführt. | — |

## Eigene Gegenprobe

**Hypothese und fachliches Risiko:** Die neue kanonische Owner-/`DerivedOrigin`-Logik könnte (a) unbenutzte vordefinierte Deklarationen vollinventarisieren, (b) kanonische und kontextuelle `LineGeometry` über dieselbe öffentliche Namensabfrage mehrdeutig machen, (c) die StartSegment-Bedingung an den falschen Typ binden, oder (d) in einem Workspace mit beiden Sprachversionen einen unvollständigen Owner erzeugen. Jede dieser Behauptungen wird mit selbst erfundenen Quellen gegen die öffentliche API geprüft; vorhandene Tests werden nicht kopiert.

**Neu erstellte Probe 1 (`ReviewProbe.java`, unabhängig, 49 Assertions):**
- 2.4-Quelle mit `IMPORTS INTERLIS`, `STRUCTURE Holder = id: INTERLIS.UUIDOID`, einer POLYLINE-Inspection und `VIEW ... INSPECTION OF ...`.
- Erwartet und beobachtet: `findQualifiedName("INTERLIS")` = 1; Modell `TYPE`, nicht CONTRACTED, Version `2014-07-09`, Issuer `http://www.interlis.ch/`, Sprache `en`, leerer Name-Range; `UUIDOID` und `LineGeometry` materialisiert, **`SurfaceEdge`/`SurfaceBoundary` nicht** (keine Vollinventur); jeder Member-Owner ist das INTERLIS-Modell; `findQualifiedName("INTERLIS.LineGeometry")` = 1 trotz kontextueller Kopie; die kontextuelle Kopie hat `DerivedOrigin(canonical, CONTEXTUALIZATION)` und einen eigenen StartSegment-Typ; die kanonische Bedingung ist `isOfClass(Segments[FIRST], StartSegment)` mit `predefined:/INTERLIS-ILI24`-Bereich, die kontextuelle Bedingung behält ihren Quellbereich; 2.3-Modell ist CONTRACTED/`2005-06-16` und materialisiert bei gewöhnlicher `LineGeometry` kein `SurfaceEdge`; TYPE MODEL akzeptiert Struktur und abstrakte Klasse, lehnt konkrete Klasse/Topic/Parameter in 2.3 und 2.4 ab.
- Kommando (Kurzform): `javac --release 25 -cp <compiler+model+antlr-4.13.2> .../ReviewProbe.java && java -cp ... ReviewProbe`
- **Exit 0, Output `REVIEW_PROBE_PASS checks=49`.**

**Neu erstellte Probe 2 (`MixedLanguageProbe.java`, Gegenprobe zu F1):**
- Zwei Quellen in einem `CompilationRequest`: 2.4-Root `Root24` und 2.3-Modell `Other23`, beide mit `line: INTERLIS.LineGeometry`; selektiert wird nur `Root24` (die 2.3-Quelle liegt ausserhalb der Closure, wird aber im öffentlichen `AnalysisSnapshot` indexiert).
- Erwartet: der im Snapshot materialisierte 2.3-Owner ist vollständig (Mitglieder enthalten die materialisierte 2.3-`LineGeometry`; deren Owner ist `predefined:ILI23:model:INTERLIS`).
- Beobachtet: `INTERLIS`-Owner = 2; `predefined:ILI23:model:INTERLIS` hat **`members=0`**; `predefined:ILI23:LineGeometry` hat **`owner=Optional.empty`**; `INTERLIS.LineGeometry` = 2, davon ein Symbol ohne Owner. **Exit 1, `MIXED_LANGUAGE_PROBE_FAIL`.** Die spiegelbildliche Probe (`MixedReverse.java`: 2.3-Root, 2.4 ausserhalb der Closure) zeigt dasselbe für `predefined:ILI24:model:INTERLIS` (`members=0`) und `predefined:ILI24:LineGeometry` (`owner=empty`). Werden beide Modelle als Roots selektiert (`MixedRoots.java`), sind beide Owner vollständig (`members=5` je Sprache) — der Fehler ist damit eindeutig auf die Closure-Sprachmenge in `PredefinedModels.finish` zurückführbar.
- Ergebnis: **F1 bestätigt.**

**Originalquellen und Referenz:** Die sechs in `g2-A2-predefined-binding-observations-2026-10-27.json` gepinnten Quelltexte wurden aus `geometry-original-pairs.json` extrahiert; alle sechs SHA-256 stimmen mit den Pins überein (`build/review/L1-1/geometry/`). Eigener CLI-Compiler: 6/6 Exit 0, `{"status":"ok","diagnostics":[]}`. ili2c 5.6.9 (`java -jar`): 6/6 Exit 0, `...compiler run done`.

**TYPE-MODEL-Referenzkontrolle:** Frische ili2c-5.6.9-Läufe mit `java -jar` (`build/review/L1-1/ili2c/`): 2.3/2.4 `STRUCTURE` Exit 0, `CLASS (ABSTRACT)` Exit 0, `CLASS` (konkret) Exit 1, `TOPIC` Exit 1; in 2.3 mit `CONTRACTED TYPE`, in 2.4 mit `TYPE`. Das deckt die Nutzerentscheidung „Wie ili2c: Strukturen und abstrakte Klassen zulassen (empfohlen)“ und die Begrenzung (Topic/konkrete Klasse/Parameter/Metadatenbehälter bleiben verboten) ab. Der frühere `-cp ...Main`-Aufruf liefert bei ili2c auch im Fehlerfall Prozessexit 0; die hier berichteten Werte verwenden den vom Referenzwerkzeug vorgesehenen `-jar`-Start.

## Pflichtprüfungen

**Frischer Pflichtlauf (Node/Java, ohne Python):**

```text
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
tools/verification/without-python.cjs \
./gradlew checkPortable g2EvidenceTest conformanceCheck sdkProcessCheck pythonFreeCheck \
  --continue --rerun-tasks \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node
```

- **Exit 0**, `BUILD SUCCESSFUL in 2m 37s`, **58 actionable tasks executed**. Der Harness ergänzt `--no-daemon`; `checkPortable` zieht `check` und damit `g2EvidenceTest`; `g2EvidenceTest` war zusätzlich explizit im Graph. Alle in diesem Lauf erzeugten XML-Reports sind frisch (`--rerun-tasks`); keine alte JSON-Zusammenfassung wurde als Erfolg gewertet.
- `build/reports/python-free/environment.json`: `gitHead=9cc8073409781e4fa3c48f4334f7de6c2d9220fd`, `dirty=""`, `status=passed`, `exitCode=0`; Proben `python/python3/pip/pip3/pypy/uv` = `ENOENT`, absolute `/usr/bin/python3` und `/usr/bin/pip3` = `EPERM`. `PYTHON_FREE_SOURCE_PASS 1185 inputs`; `git ls-files '*.py'` leer.
- **JVM-Tests (frische XMLs, je `tests/failures/errors/skipped`):** Compiler **628/0/0/0**, CLI 33/0/0/0, LSP 16/0/0/0, Model 16/0/0/0, Projection 12/0/0/0, Repository 19/0/0/0, Wasm-Bridge 3/0/0/0, Workspace 10/0/0/0 → **737 reguläre JVM-Tests, 0 Fehler/Skips**. Evidenz **136/0/0/0**, Assertion **15/0/0/0**, Verification/Host **15/0/0/0**. `LanguageFoundationContractTest` selbst: **7/0/0/0**.
- **Portable T3:** `interlis-wasm-teavm/build/js-contract-result.json` und `wasm-contract-result.json`: je `stabilizationCases=3299`, `status=passed`, `node=v24.22.0` (TeaVM-JS bzw. WasmGC). Die 20 neuen L1-1-Fälle sind enthalten (vorher 3279).
- **SDK:** `SDK_PROCESS_PASS 9 commands`; `sdkExampleTest` und `installDist` im selben `check`-Graph.
- **Conformance (frisch):** `selected=593`; **Rohbilanz 580 PASS / 12 FAIL / 1 XFAIL** (`referenceSummary`, `rawExit=1`); **Projektbilanz 580 PASS / 13 XFAIL / 0 FAIL / 0 XPASS / 0 ERROR**, `overallResult=PASS`; **18/18 Assessments PASS**. `build/reports/conformance/conformance-summary.json`, `build/reports/conformance/conformance-log.txt`.
- **Pins unverändert:** `gradle/conformance-suite.properties` weiterhin Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`, SHA-256 `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe`; `gradle/conformance.gradle`, `docs/evidence/conformance/`, `interlis-cli/src/evidenceTest/`, `testdata/g2/a1-evidence-profile.json` sind im Diff `BASE..CANDIDATE` nicht enthalten. `testdata/g2/predefined-catalogue.json` wurde nur um Quellanker/Hashes ergänzt bzw. verschoben; keine Erwartung geändert. Keine XFAIL-/Baseline-/Grammatikänderung. Die 18 Zusatzerwartungen und die sechs OID-/CONTRACTED-Fälle bleiben unverändert; `ModelKindContractTest` ändert ausschliesslich die freigegebene abstrakte-Klasse-Positiverwartung und ersetzt die alte Negativerwartung durch eine konkrete Klasse. `PredefinedOidIdentityContractTest` verschärft die alte `owner-empty`-Assertion auf den tatsächlichen vordefinierten Owner.
- **Nicht ausgeführt (nicht fällig für L1-1):** `checkBrowser`/echtes Safari/Web Image, `performanceCheck`/`jvmBenchmark`/`wasmBenchmark`/`browserBenchmark`, vollständige Phase-1-Kumulativprüfung. Diese sind laut Handoff L1-3 bzw. P3/P4/P5 und keine L1-1-Pflicht; die betroffenen gemeinsamen T3-Verträge wurden über Node/TeaVM ausgeführt.
- **Diffumfang:** 21 Dateien (`+1153/−638`), darunter `GraphBuilder`, `LineForms`, neues `PredefinedModels`, `ViewBuilder`, 7 neue Testmethoden, Evidenzanker und die synchronisierten Kapitel 05/06/15/16/17. Keine neue öffentliche Signatur, keine Grammatikdatei, keine Python-Datei.

## Findings

**F1 / MAJOR – Unvollständiger kanonischer INTERLIS-Owner für eine Sprache ausserhalb der Closure im öffentlichen `AnalysisSnapshot`.**

- **Ort:** `interlis-compiler/src/main/java/guru/interlis/compiler/internal/GraphBuilder.java` (Aufbau der `languages`-Menge aus `closure.models()` vor `PredefinedModels.finish(this, languages)`) und `interlis-compiler/src/main/java/guru/interlis/compiler/internal/PredefinedModels.java` (`finish` reheadert/materialisiert nur die übergebenen Sprachen; `ensure` erzeugt für jede Referenz bereits ein Modell mit `members=[]`). Die neue Spezifikation `docs/spec/05-metamodell-und-typensystem.md` sagt: „Seine `members()` enthalten die im Snapshot materialisierten vordefinierten Deklarationen … Deren Owner ist dieses vordefinierte Modell.“ Das gilt im Fehlerfall nicht.
- **Reproduktion (öffentliche API, keine Testkopie):** `CompilationRequest` mit zwei Quellen:
  - `Root24`: `INTERLIS 2.4; CONTRACTED MODEL Root24 ... STRUCTURE H = line: INTERLIS.LineGeometry; END H; END Root24.`
  - `Other23`: `INTERLIS 2.3; CONTRACTED MODEL Other23 ... STRUCTURE H = line: INTERLIS.LineGeometry; END H; END Other23.`
  - Root-Selektor nur `ByName("Root24")`; `SourceSet` mit beiden Quellen; `AnalysisSnapshot` über `CompilerEngines.create().compile(...).analysis()` auswerten.
  - Probe: `build/review/L1-1/probe/MixedLanguageProbe.java` (Gegenprobe: `MixedReverse.java` mit umgekehrten Versionen).
- **Erwartet:** `predefined:ILI23:model:INTERLIS` ist der kanonische Owner; seine `members()` enthalten die materialisierte `predefined:ILI23:LineGeometry`, und deren `owner()` ist dieses Modell (analog für 2.4). Mindestens muss der Snapshot keine halb materialisierte Sprache mit leerem Owner exposieren.
- **Beobachtet:** `findQualifiedName(QualifiedName.of("INTERLIS"))` = 2; `predefined:ILI23:model:INTERLIS` hat `members=0`; `predefined:ILI23:LineGeometry` hat `owner=Optional.empty`; `findQualifiedName(QualifiedName.of("INTERLIS","LineGeometry"))` = 2, davon ein Symbol ohne Owner. `MixedLanguageProbe` Exit **1**. Die spiegelbildliche 2.3-Root-/2.4-Nicht-Closure-Probe zeigt dasselbe für ILI24. Werden beide Modelle als Roots selektiert, sind beide Owner vollständig (`members=5`), was den Fixpfad bestätigt.
- **Nötige Nacharbeit:** `PredefinedModels.finish` für alle Sprachen ausführen, für die im Graph materialisierte vordefinierte Symbole existieren (nicht nur `closure.models()`), oder die Materialisierung für Nicht-Closure-Sprachen konsistent unterlassen. Regressionstest mit einem sprachgemischten `SourceSet` und öffentlicher `AnalysisSnapshot`-Query für beide Owner; danach neuen Kandidaten-SHA und vollständigen Review. Keine Normänderung, keine XFAIL-/Suite-Änderung nötig.

## Urteil

**FAIL**

**Begründung:** Die Pflichtprüfungen für L1-1 sind am exakten SHA `9cc8073409781e4fa3c48f4334f7de6c2d9220fd` frisch und grün ausgeführt (737 JVM-, 136 Evidenz-, 15 Assertion-, 15 Host-Tests; 3299 Node-JS-/WasmGC-Fälle je Profil; T2 roh 580/12/1, Projekt 580/13, 18/18 Assessments; SDK 9; Python-frei mit ENOENT/EPERM). Die einsprachigen AC1-01-/AC1-02-Fälle und die sechs Originalgeometrien sind belegt. **AC1-01 ist jedoch durch F1 verletzt:** Für eine im Snapshot materialisierte Sprache ausserhalb der angeforderten Closure ist der kanonische INTERLIS-Owner leer und die materialisierte `LineGeometry` hat keinen Owner; das widerspricht der neuen Owner-/`members()`-Spezifikation und der öffentlichen Namensabfrage. Das ist ein reproduzierbarer Defekt des neuen `PredefinedModels`-Pfads, kein Infrastruktur- oder Normblocker.

**Nächster konkreter Schritt:** F1 im selben Abschnitt beheben (Sprachmenge für `finish` aus den tatsächlich materialisierten vordefinierten Symbolen bzw. allen Indexsprachen bilden), einen sprachgemischten Regressionstest ergänzen, `checkPortable g2EvidenceTest conformanceCheck sdkProcessCheck pythonFreeCheck` erneut frisch ausführen und den vollständigen L1-1-Review auf dem neuen SHA wiederholen. Kein L1-2 vor einem neuen PASS.
