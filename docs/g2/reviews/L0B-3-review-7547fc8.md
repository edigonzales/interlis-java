# Unabhängiger Review: L0B-3

Lieferabschnitt / Phase / letztes Phasengate?: **L0B-3 / Phase 0b / ja** – dieser Review ist zugleich das kumulative Phase-0b-Gate.
Implementierungsagent: Codex
Prüfagent: OpenCode; tatsächliche Provider-/Modellkonfiguration: **OpenCode v2.0.28**, Provider **`deepseek`**, Modell **`deepseek-flash`** (UI: „DeepSeek V4.1 Flash 1017“), **direkt, kein OpenRouter**. Die Bindung stammt aus der Startkonfiguration und der Harness-Modellbindung, nicht aus einer blossen Selbstauskunft des Modells.
BASE_SHA: `abde84ef666184e128827894d63e176709e7c3db`
CANDIDATE_SHA (durch git überprüft): `7547fc88fd096753a89fed93bc16a2489542e12f`
Prüfumgebung / Toolchain: macOS 15.8.1 (24H32) arm64; Temurin 25.0.3+9; GraalVM 25.0.3+9.1; Gradle-Wrapper 9.8.1; Node 24.22.0; Binaryen 133; Playwright 1.64.1; Safari 27.0 (SafariDriver 20625.1.29.18.28). Eigener, frischer Worktree `/private/tmp/interlis-java-L0B-3-review-7547fc8`; `HEAD` vor und nach der Prüfung unverändert `7547fc88…`, `git status --porcelain` leer. Keine Produktquelle, kein Test, keine Builddatei und kein Kriterium wurde geändert. Reviewhilfen und Rohlogs liegen ausschliesslich unter dem ignorierten `build/`.

Hinweis zur Ausführung: Der erste Gesamtlauf scheiterte in diesem frischen Worktree an einer fehlenden Playwright-Installation (`Cannot find module 'playwright'` in `browserContractTest`, `webImageBrowserTest`, `browserBenchmark`; 5m 57s, 79 Tasks, Exit 1). Mit dem vorhandenen Lock wurde `npm ci` ausgeführt (2 Pakete, keine Produktänderung). Der danach gestartete zweite Gesamtlauf wurde nach `checkBrowser`, `webImageProbe` und `wasmBenchmark` während `jvmBenchmark` unterbrochen. Der erste fehlgeschlagene Lauf wird **nicht** als Erfolg gewertet; der zweite Lauf ist **nicht abgeschlossen** und wird ebenfalls nicht als vollständiges Phasengate gewertet. Die unten als „offen“ bezeichneten Gates sind genau deshalb offen.

## Kriterienprüfung

| Kriterium-ID | Erfüllt / verletzt / nicht prüfbar | Eigener Test-/Code-/CI-Beleg | Finding-ID |
|---|---|---|---|
| **AC0B-01** | **erfüllt** | `gradle/conformance.gradle` startet den externen `InterlisConformance.java` direkt (`runConformanceSuite` → `Exec`), kein zweiter Runner. `gradle/conformance-suite.properties`: Commit `88576ffc…d2c3`, Archiv-SHA-256 `6a80b7e8…37fe`; der Cache-Tarball wurde mit genau diesem SHA geprüft. `build/conformance/run-config.json` enthält `filter=^(ili23|ili24)\.`, `xpassPolicy=fail`; `summary.json`: 610 Fälle, 593 selektiert, 17 INTERLIS-1.0 ausgeschlossen, roh `PASS=580, XFAIL=1, FAIL=12, XPASS=0, ERROR=0`, Runner-Exit 1; `build/reports/conformance/conformance-summary.json` Projekt `PASS=580, XFAIL=13, FAIL=0, XPASS=0, ERROR=0`. Eigene Konsistenzprüfung `verify-conformance.cjs`: `CONFORMANCE_REVIEW_PASS`. | — |
| **AC0B-02** | **erfüllt** | 18 Assessments in `build/reports/conformance/conformance-summary.json`, alle `result=PASS`; exakt sechs mit `LEGACY_OID_23_WITHOUT_CONTRACTED`/`APPROVED_USER`, zwölf ohne. Für die sechs wurden Original-, Positiv- und Negativprozess tatsächlich ausgeführt (Exit 0/0/1) und die strukturierte Diagnose inkl. Code, Severity, Phase, MessageKey, Argumenten, Revision, UTF-16-Range und Multiplizität verglichen. Eigener Node-Hashcheck: alle 18 `sources` gegen die gepinnten Suite-Fixtures, alle Positiv-/Negativkontrollen gegen die Repo-Dateien, alle sechs Negativ-Ranges auf `OID AS NotOid`; `bad=0`. Der INTERLIS-1-Profilfall `ili23.Attribute10Test.attributeComplete` bleibt als eigener roher XFAIL ausserhalb der 18 Assessments sichtbar. Roh- und Projektbilanz sind getrennt. | — |
| **AC0B-03** | **erfüllt** | Historischer Vergleich mit `git show` und Node: die Basisdatei `docs/evidence/conformance/normative-expectations.json` hat 18× `ownExpectation=rejected`; der Kandidat hat exakt sechs `rejected→accepted` (die sechs genannten IDs), die übrigen zwölf Einträge sind in `ownExpectation`, `referenceExpectation`, `sources`, `positiveControls`, `negativeControls`, `allowedDiagnostics`, `languageVersion` und `norm` unverändert. Der L0B-1-Bericht am Basisstand belegt roh 574 PASS/18 FAIL/1 XFAIL; der Kandidat hat roh 580 PASS/12 FAIL/1 XFAIL. Die Differenz ist genau die sechs freigegebenen Fälle; die 12 rohen FAIL-IDs sind exakt die zwölf verbleibenden Assessments. `NormativeConformanceContractTest` (Canaries für fehlende/duplizierte ID, Hash, XPASS/ERROR, falsche/fehlende Kontrolle, internen Fehler, veraltete Berichte) lief im Gesamtlauf; `conformanceWrapperTest` war Teil des Tasks. Keine Python-Aufrufe. | — |
| **AC0B-04** | **erfüllt** | `g2EvidenceTest` ist Teil von `check` und wurde ausgeführt. JUnit-XML-Aggregation (`aggregate-junit.cjs`): `test` 730 Tests, `evidenceTest` 136, `g2AssertionTest` 15, `verificationTest` 14; jeweils **0 Failures, 0 Errors, 0 Skips**. `AssertionEvidenceTest` prüft Suite/Klasse/Methode, falsche Klasse bei gleicher Methode, Zähler, Details, Skips, Zeitstempel, Quell-/XML-Hashes und frische Bytes; `CatalogueEvidence`/`RuntimeReports` prüfen die Norm-/Entscheidungs- und Roh-/Projektbindungen. | — |
| **AC0B-05** | **erfüllt** | Die Migrationsmatrix `docs/g2/python-migration.md` ordnet alle 36 Ausgangsdateien einer Entscheidung zu; L0B-3 entfernt die letzten neun. Aktive Norm-/PDF-/Versions-/Matrix-/Katalogverträge bleiben: `g2EvidenceTest` (136 Tests) grün. Der Diff von `g2-source-inventory.json` und `testdata/g2/predefined-catalogue.json` enthält nur Quellhash-/Anker-/Zeilenverschiebungen, keine neuen Normzitate oder gelockerten Erwartungen. Unverändert geprüft: `TextLengths.java` SHA-256 `47e90b…d207`; die entpackten Unicode-17-Dateien ergeben `2e1efc…70c` bzw. `5019ff…7db`; kein PDFBox-Neuextraktor; die pypdf-6.10-JSONs bleiben unverändert. | — |
| **AC0B-06** | **nicht prüfbar / offen** | Belegt: SDK 9 Prozesse (`SDK_PROCESS_PASS 9 commands`), Referenzprofil 5/5 (`REFERENCE_PASS 5 cases`), Original-Views 19/19, Corpus 13 (`CORPUS_PASS 13 models`), Views 134 eigene Erwartungen/102 Referenzübereinstimmungen/6 sichtbare Referenzprozessfehler, Units 123/123 eigene/121 Übereinstimmungen/0 Prozessfehler, Wasm-Benchmark 4 Modelle, LSP-Installation und -Tests. **Offen:** Der zweite Lauf begann `jvmBenchmark` (nur `CORPUS_PASS 13 models`), beendete ihn aber nicht (`JVM_BENCHMARK_PASS` fehlt); `browserBenchmark` wurde nicht erreicht; `performanceCheck` ist nicht abgeschlossen. Der erste Lauf hatte `JVM_BENCHMARK_PASS 13 models`, scheiterte aber insgesamt an fehlendem Playwright und ist kein Erfolgsnachweis. | F2 |
| **AC0B-07** | **verletzt** | `git ls-files '*.py'` ist leer (Basis: 9 Dateien); `pythonFreeCheck` scannte im zweiten Lauf 1180 getrackte/nicht ignorierte Eingaben; die Harness-Proben belegen `python`/`python3`/`pip`/`pip3`/`pypy`/`uv` mit PATH-`ENOENT` und `/usr/bin/python3`/`/usr/bin/pip3` mit `EPERM`. **Aber:** Die eigene Canary `tools/review-counter-canary.sh` mit `exec /usr/bin/env python3 "$@"` ergab `PYTHON_FREE_SOURCE_PASS 1181 inputs` und **Exit 0**; erwartet war Exit 1. `exec python3` und eine `.py`-Datei werden dagegen korrekt abgelehnt. Der Scanner erkennt damit eine verbreitete, unquotierte `env`-Form nicht. | **F1** |
| **AC0B-08** | **nicht prüfbar / offen** | Der erforderliche Gesamtlauf wurde nicht abgeschlossen: kein `BUILD SUCCESSFUL`, kein abschliessendes `EXIT=0`; `build/reports/python-free/environment.json` steht auf `status=running` (gitHead korrekt, dirty leer, Proben ENOENT/EPERM). Vollständig belegt sind dagegen die funktionalen Plattformläufe: TeaVM-JS 3279, WasmGC 3279, Web Image Node 3279, Web Image Chromium/Firefox/WebKit je 3279, TeaVM Chromium/Firefox/WebKit je 3279, reales Safari 3279; `checkPortable` und `checkBrowser` wurden abgeschlossen. `jvmBenchmark`, `browserBenchmark`, `performanceCheck` und der abschliessende Gesamtlauf fehlen. | F2 |

## Eigene Gegenproben

### Gegenprobe 1 – Metadatenfortsetzung, UTF-16 und `fn.param`

**Hypothese / Risiko:** Die neue mehrzeilige `!!@`-Fortsetzung könnte nur LF und einfache Werte beherrschen, Marker aus dem Originalbereich verlieren, Surrogatpaare falsch zählen oder `fn.param` an Nicht-Funktionen fälschlich erlauben.

**Neu erstellter Input:** `build/review/L0B-3/probes/MetadataProbe.java` (Reviewerquelle, nicht im Kandidaten). Modell mit `fn.description`, zwei `fn.param`, einem über zwei `!!@`-Zeilen fortgesetzten `fn.return` mit escaped quotes und 🦊, für 2.3/2.4 und LF/CRLF; ferner Duplikate an `DOMAIN`/`CLASS`, offener String und `\q`-Escape.

**Erwartet:** Erfolg für die Funktionsfälle; `entries().size()==4`, `values().size()==3`, `values("fn.param")` in Quellreihenfolge, `values()` erster Index, exakter dekodierter Text, exakter UTF-16-Range inkl. `!!@` und 🦊, Revision/Source-ID erhalten, unveränderliche Rückgaben; alle Negativfälle `SOURCE_ERRORS` ohne `ILI-INTERNAL-0001`.

**Kommando / Exit:** `javac … MetadataProbe.java` → Exit 0; `java … MetadataProbe` → **`METADATA_PROBE_PASS checks=76`, Exit 0**.

**Ergebnis:** Hypothese widerlegt; die Metadatenverträge halten in beiden Sprachen und Zeilenenden. Kein Finding.

### Gegenprobe 2 – Begrenzte 2.3-OID-Ausnahme

**Hypothese / Risiko:** Die Entfernung der CONTRACTED-Vorbedingung könnte zu breit wirken und OID-Domänenbindung, Override oder andere CONTRACTED-Regeln mitfreigeben.

**Neu erstellter Input:** `build/review/L0B-3/probes/OidProbe.java`: `OID AS`/`BASKET OID AS` ohne und mit CONTRACTED in 2.3/2.4, Vererbung; `PARAMETER`/`FUNCTION` ohne CONTRACTED in 2.3; falsche OID-Domäne; inkompatibler Override.

**Erwartet:** OID in 2.3 ohne CONTRACTED akzeptiert, Domänen korrekt gebunden; `PARAMETER_CONTRACTED`, `FUNCTION_CONTRACTED`, `OID_DOMAIN`, `OID_OVERRIDE` weiterhin abgelehnt.

**Kommando / Exit:** `javac … OidProbe.java` → Exit 0; `java … OidProbe` → **`OID_PROBE_PASS checks=36`, Exit 0**.

**Ergebnis:** Hypothese widerlegt; die Ausnahme ist auf die OID-Bedingung begrenzt. Kein Finding.

### Gegenprobe 3 – Corpus-Seed und falscher Cache

**Hypothese / Risiko:** Der Seed könnte einen falschen/vorhandenen Cache still überschreiben oder falsche Bytes akzeptieren.

**Input:** `build/review/L0B-3/probes/results.log`; positiver relativer Cache `build/review/L0B-3/corpus-good`; falscher `ilimodels.xml`; danach gültiger Seed mit manipuliertem `AGI__SO_FunctionsExt.ili`.

**Erwartet:** Positiv 14 Dateien/13 Modelle und Exit 0; falscher Index und falsches Modell je Exit 1 mit „Changed or missing pinned input“.

**Kommando / Exit:** `GEO_CORPUS_CACHE=… java … GeoCorpus "$PWD"` → positiv `CORPUS_PASS 13 models`, Exit 0; bad index **Exit 1**; bad model **Exit 1**.

**Ergebnis:** Hypothese widerlegt; Seed-Recovery und Cache-Integrität funktionieren. Kein Finding.

### Gegenprobe 4 – Python-freier Scanner (Canary)

**Hypothese / Risiko:** `pythonFreeCheck` könnte eine unquoted Shell-Invocation übersehen und damit eine aktive Python-Abhängigkeit als grün melden.

**Input:** `tools/review-counter-canary.sh`:
```sh
#!/bin/sh
exec /usr/bin/env python3 "$@"
```

**Erwartet:** Exit 1, `Active Python/pip command` (analog zum dokumentierten Canary).

**Kommando / Exit:** `java -cp … guru.interlis.cli.verification.PythonFreeCheck "$PWD"` → **`PYTHON_FREE_SOURCE_PASS 1181 inputs`, Exit 0**. Kontrollen: `exec python3` → Exit 1; `.py` → Exit 1; nach Entfernen → `PYTHON_FREE_SOURCE_PASS 1180 inputs`, Exit 0.

**Ergebnis:** **Finding F1 bestätigt.** Der Scanner ist für diese Form blind. Die Datei wurde entfernt; `git status` ist leer.

### Gegenprobe 5 – Konformitätskonsistenz

**Input:** `build/review/L0B-3/probes/verify-conformance.cjs` über den erzeugten Roh-/Projektberichten.

**Erwartet:** Suite-Pin, 610/593/17, roh 580/12/1, Projekt 580/13, 18 Assessments PASS, sechs Kompatibilitätsfälle mit Original-/Positiv-/Negativprozess, zwölf rohe FAIL-IDs = zwölf Nicht-Kompatibilitäts-Assessments.

**Kommando / Exit:** `node …/verify-conformance.cjs` → **`CONFORMANCE_REVIEW_PASS`, Exit 0**.

**Ergebnis:** AC0B-01/02/03-Daten sind konsistent. Kein Finding.

## Pflichtprüfungen

**Vollkommando (zweiter Lauf, exakt):**
```sh
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
tools/verification/without-python.cjs ./gradlew \
check g2EvidenceTest checkPortable checkBrowser \
:interlis-wasm-webimage:webImageProbe conformanceCheck sdkProcessCheck sdkPackage \
:interlis-lsp:installDist geoCorpus performanceCheck referenceCheck \
originalViewReferenceCheck viewReferenceCheck unitReferenceCheck pythonFreeCheck \
--continue --rerun-tasks \
-PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
-PgraalVmHome=/Users/stefan/.sdkman/candidates/java/25.0.3-graal \
-PbinaryenHome=/Users/stefan/.cache/interlis-java/runtime/binaryen-version_133
```

**Tatsächliche Exits:**
- Erster Lauf: `raw-full-gate.log`, **Exit 1**, `BUILD FAILED in 5m 57s`, 79 Tasks; Fehler nur `webImageBrowserTest`, `browserContractTest`, `browserBenchmark` wegen fehlendem `playwright`. Kein Erfolg.
- `npm ci` in `interlis-wasm-teavm`: „added 2 packages“, keine Produktänderung.
- Zweiter Lauf: `raw-full-gate-2.log`; **kein abschliessendes Exit** (unterbrochen). `environment.json`: `status=running`, `gitHead=7547fc88…`, `dirty=""`. Kein `BUILD SUCCESSFUL`, kein `EXIT=0`.

**Abgeschlossene Tasks des zweiten Laufs (aus dem Log):** `check`, `g2EvidenceTest`, `pythonFreeCheck` (1180), `checkPortable` (JS 3279, WasmGC 3279), `checkBrowser` (TeaVM Chromium/Firefox/WebKit je 3279, Safari 3279, Web Image Chromium/Firefox/WebKit je 3279), `webImageProbe` (3279), `conformanceCheck` (580/13), `sdkProcessCheck` (9), `sdkPackage`, `geoCorpus` (13), `referenceCheck` (5), `originalViewReferenceCheck` (19), `viewReferenceCheck` (134/102/6), `unitReferenceCheck` (123/121/0), `wasmBenchmark` (4), `:interlis-lsp:installDist`/`:interlis-lsp:test`.

**Nicht abgeschlossen:** `jvmBenchmark` (nur `CORPUS_PASS 13 models`, kein `JVM_BENCHMARK_PASS`), `browserBenchmark`, `performanceCheck`, abschliessender Gesamtlauf/`EXIT`. Der erste Lauf hatte `JVM_BENCHMARK_PASS 13 models`, ist aber wegen der Playwright-Fehler kein gültiger Abschluss.

**Tests / Failures / Errors / Skips (JUnit-XML, zweiter Lauf):**
- `test`: 730 / 0 / 0 / 0
- `evidenceTest`: 136 / 0 / 0 / 0
- `g2AssertionTest`: 15 / 0 / 0 / 0
- `verificationTest`: 14 / 0 / 0 / 0

**Conformance:** 610 Fälle, 593 selektiert, 17 INTERLIS-1.0 ausgeschlossen. Roh **580 PASS / 12 FAIL / 1 XFAIL / 0 XPASS / 0 ERROR**, Runner-Exit 1. Projekt **580 PASS / 13 XFAIL / 0 FAIL/XPASS/ERROR**, Task-Exit 0. 18 Assessments alle `PASS`; sechs Kompatibilitätsfälle, zwölf verbleibende Normausnahmen.

**Plattformzahlen:** TeaVM-JS 3279; WasmGC 3279; Web Image Node 3279; Web Image Chromium/Firefox/WebKit je 3279; TeaVM Chromium/Firefox/WebKit je 3279; Safari 3279. Keine funktionalen Skips in diesen Läufen. Die historischen Browserbenchmark-Skips (Chromium 1, Firefox 1, WebKit 12) sind im Benchmark-Skript sichtbar; der Benchmark selbst wurde im zweiten Lauf nicht erreicht.

**Rohberichte / Bindung an CANDIDATE_SHA:**
- `/private/tmp/interlis-java-L0B-3-review-7547fc8/build/review/L0B-3/raw-full-gate.log` (erster, fehlgeschlagener Lauf)
- `/private/tmp/interlis-java-L0B-3-review-7547fc8/build/review/L0B-3/raw-full-gate-2.log` (zweiter, unterbrochener Lauf)
- `/private/tmp/interlis-java-L0B-3-review-7547fc8/build/review/L0B-3/probes/results.log`
- `/private/tmp/interlis-java-L0B-3-review-7547fc8/build/reports/python-free/environment.json` (`gitHead=7547fc88…`, `dirty=""`, Proben ENOENT/EPERM, `status=running`)
- `/private/tmp/interlis-java-L0B-3-review-7547fc8/build/conformance/{run-config.json,summary.json,report.json,runner-exit.txt}`
- `/private/tmp/interlis-java-L0B-3-review-7547fc8/build/reports/conformance/conformance-summary.json`
- `/private/tmp/interlis-java-L0B-3-review-7547fc8/build/reports/{sdk,reference,performance,stabilization,python-free}/…`
- `/private/tmp/interlis-java-L0B-3-review-7547fc8/interlis-wasm-teavm/build/{browser-results.json,safari-result.json,js-contract-result.json,wasm-contract-result.json}`
- `/private/tmp/interlis-java-L0B-3-review-7547fc8/interlis-wasm-webimage/build/webimage/result.json` und `build/webimage-browser/browser-results.json`

Die Reports liegen im Worktree am exakten Kandidaten-SHA; `environment.json` bindet den Lauf an `gitHead` und bestätigt eine leere Dirty-Liste. Der zweite Lauf ist wegen der Unterbrechung nicht als vollständiges Gate verwendbar.

**Norm-/Suite-/XFAIL-/Baseline-/Quellpin-Unverändertheit:** Suite-Commit `88576ffc…d2c3` und Archiv-SHA `6a80b7e8…37fe` unverändert; `baseline.json` unverändert (nur der separate INTERLIS-1-XFAIL); die sechs `ownExpectation`-Änderungen sind die einzigen Änderungen an den 18 Normerwartungen; die übrigen zwölf sind unverändert. Original-/Positiv-/Negativquellen und ihre SHA-256 sind gepinnt; die sechs Negativkontrollen sind die neue gepinnte `oid23-wrong-domain.ili` mit `OID_DOMAIN`. Corpusmanifest und alle 13 Quellpins unverändert; `corpus-seed.zip` enthält exakt die 14 Originaldateien mit den Manifest-Hashes; Unicode-Gz und `TextLengths.java` bytegleich. Keine XFAIL-/Baseline-/Pin-Lockerung.

**Python-Migrations-/Phaseninvarianten:** 36 getrackte `.py` an der Basis, 9 in L0B-3 entfernt, aktuell **0**. Der aktive Gradle-/Java-/Node-Weg enthält keine Python-/pip-Aufrufe; historische Dokumente bleiben lesbar. Die Scanner-Canary ist jedoch für die `exec /usr/bin/env python3`-Form blind (F1). Der Harness belegt die Laufzeitabwesenheit (PATH ENOENT, absolute EPERM) und blockiert eine tatsächlich ausgeführte Python-Invocation; er ersetzt aber nicht die fehlende Quell-Canary.

**Kumulative Phasenregression:** AC0B-01 bis AC0B-05 wurden anhand eigener Läufe/Daten erneut geprüft (nicht nur über alte Reviewberichte). AC0B-06 ist nur teilweise belegt, AC0B-07 ist durch F1 verletzt, AC0B-08 ist nicht abgeschlossen. Damit ist das kumulative Phase-0b-Gate nicht PASS-fähig.

## Findings

**F1 / MAJOR – `pythonFreeCheck` erkennt `exec /usr/bin/env python3` nicht.**
Ort: `interlis-cli/src/verification/java/guru/interlis/cli/verification/PythonFreeCheck.java:11` (`COMMAND`-Regex) und `:21` (Anwendung); Regressionstest `interlis-cli/src/verificationTest/java/guru/interlis/cli/verification/PythonFreeCheckTest.java:7-17` deckt nur gequotete Aufrufe und `#!/usr/bin/env python3` ab.
Reproduktion: `tools/review-counter-canary.sh` mit `#!/bin/sh` und `exec /usr/bin/env python3 "$@"`; `java -cp <verification-classes>:<gson> guru.interlis.cli.verification.PythonFreeCheck "$PWD"`.
Erwartet: Exit 1, `Active Python/pip command: tools/review-counter-canary.sh`.
Beobachtet: `PYTHON_FREE_SOURCE_PASS 1181 inputs`, Exit 0. Kontrollen `exec python3` und `.py` liefern Exit 1; nach Entfernen der Canary wieder 1180 Inputs/Exit 0.
Wirkung: Eine unquoted `env`-/Pfad-Invocation in einem `.sh`-, `.gradle`- oder Node-Pfad kann die Quell-Canary umgehen. Der Laufzeitharness würde eine tatsächlich ausgeführte Python-Invocation blockieren, aber die ausdrückliche Canary-Zusage „echte zusätzliche Quellcanarys scheitern“ ist für diese verbreitete Form falsch. AC0B-07 ist damit verletzt; ohne Nacharbeit ist kein Phase-0b-PASS möglich.
Nötige Nacharbeit: `COMMAND` um unquotierte Interpreter nach `env`, `command`, `exec`, `xargs`, absoluten Pfaden und Substitutionen erweitern; JUnit-Canary für `exec /usr/bin/env python3` und `env python3` ergänzen; `pythonFreeCheck` und den vollständigen Lauf auf einem neuen Kandidaten-SHA wiederholen; danach vollständiger neuer Review.

**F2 / OFFENES GATE (kein Produktfinding) – Gesamtlauf und Performance-Teil nicht abgeschlossen.**
Der zweite Lauf wurde während `jvmBenchmark` unterbrochen; `JVM_BENCHMARK_PASS`, `browserBenchmark`, `performanceCheck` und ein abschliessendes `EXIT=0`/`BUILD SUCCESSFUL` fehlen. Der erste Lauf ist wegen der Playwright-Infrastrukturfehler kein Erfolg. Damit ist AC0B-06 nicht vollständig und AC0B-08 nicht prüfbar. Vor einem PASS müssen diese Gates auf demselben (neuen) SHA vollständig und ohne Unterbrechung abgeschlossen werden.

**Beobachtungen (nicht abnahmeblockierend, aber zu bereinigen):**
- Die Browser-Contract-Skripte prüfen `stabilizationCases` nicht explizit auf 3279; sie protokollieren den Wert nur. Die tatsächlichen Läufe zeigen 3279, eine spätere stille Fallreduktion würde aber nicht scheitern. Empfehlung: `stabilizationCases===3279` in den Contract-Skripten assertieren.
- `g2-rule-matrix.json` enthält im historischen `conformanceGate` weiterhin 574/19 und in `platformGates` 699/3249; das ist ein datierter historischer Zeiger, keine aktive Prüfung. Die aktuellen Zahlen stehen in `STATUS.md`/Implementierungsbericht/`conformance-summary.json`. Kein Widerspruch zu den aktiven Verträgen, aber bei der nächsten Pflege nachführen.

## Urteil

**FAIL**

Begründung: F1 ist ein reproduzierbarer MAJOR-Defekt des Python-frei-Gates: `pythonFreeCheck` meldet für eine getrackte, nicht ignorierte Shell-Canary mit `exec /usr/bin/env python3` **Exit 0** statt Exit 1. Damit ist AC0B-07 verletzt und das kumulative Phase-0b-Gate (AC0B-08) nicht PASS-fähig. Unabhängig davon ist der Gesamtlauf nicht abgeschlossen (F2): `jvmBenchmark`/`browserBenchmark`/`performanceCheck` und das abschliessende Gate fehlen; der erste Lauf ist wegen fehlendem Playwright kein Erfolg. Die funktionalen Plattformläufe (10×3279 inkl. Safari/Web Image) und die Konformitätsbilanz (roh 580/12/1, Projekt 580/13, 18 Assessments PASS) sind dagegen vollständig belegt.

Nächster konkreter Schritt: Scanner um die `env`-/Pfad-/`xargs`-Formen erweitern, die neue Canary als JUnit-Test aufnehmen, `pythonFreeCheck` und anschliessend den vollständigen Gesamtlauf (inkl. `jvmBenchmark`, `browserBenchmark`, `performanceCheck`) auf einem **neuen** Kandidaten-SHA ausführen und den gesamten Review erneut durchführen. Kein L1-1 vor einem PASS.
