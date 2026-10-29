# Unabhängiger Review: L0B-3

Lieferabschnitt / Phase / letztes Phasengate?: **L0B-3 / Phase 0b / ja** – dieser Review ist zugleich das kumulative Phase-0b-Gate.
Implementierungsagent: Codex
Prüfagent: OpenCode; tatsächliche Provider-/Modellkonfiguration: **OpenCode v2.0.28**, Provider **`deepseek`**, Modell **`deepseek-flash`** (UI: „DeepSeek V4.1 Flash 1017“), **direkt, kein OpenRouter**. Die Angabe stammt aus der Startkonfiguration des Auftraggebers; eine unabhängige Providerattestierung ausserhalb dieser Konfiguration gibt es nicht.
BASE_SHA: `abde84ef666184e128827894d63e176709e7c3db`
CANDIDATE_SHA (durch git überprüft): `d67b0a669ac41261078fe02dafe1544e2c1ef3bb`
Prüfumgebung / Toolchain: macOS 15.8.1 (24H32) arm64; Temurin **25.0.3+9**; GraalVM **25.0.3+9.1**; Gradle-Wrapper **9.8.1**; Node **24.22.0**; Binaryen **133**; Playwright **1.64.1**; Safari **27.0** (SafariDriver `Included with Safari 27.0 (20625.1.29.18.28)`). Eigener frischer Worktree `/private/tmp/interlis-java-L0B-3-review-d67b0a6`; `HEAD` vor und nach der Prüfung `d67b0a6…`, `git status --porcelain` vor und nach der Prüfung leer. Playwright war im Worktree bereits über den vorhandenen Lock installiert; ein erneutes `npm ci` war nicht nötig. Keine Produktquelle, kein Test, keine Builddatei und kein Kriterium wurde geändert. Reviewhilfen, Canaries und Rohlogs lagen ausschliesslich unter dem ignorierten `build/`; alle temporären Canaries wurden entfernt.

**Transparenz zur Prozessvorgabe:** Bei der Vorbereitung eines Probeedits wurde einmal versehentlich ein **leerer** `python3 - <<'EOF'`-Heredoc auf dem Host gestartet (keine Eingabe, kein Output, Exit 0). Er war nicht Teil der Gates, erzeugte keine Evidenz und wurde nicht als Prüfmittel verwendet. Alle Produktprüfungen, Gates und belastbaren Proben liefen im Python-gesperrten Harness beziehungsweise ausschliesslich mit Java, Node und Shell; die Pythonfreiheit des Produkts ist davon nicht betroffen. Die Vorgabe „kein Python“ wurde durch diesen einen No-op-Aufruf formal verletzt; er wird hier offengelegt.

## Kriterienprüfung

| Kriterium-ID | Erfüllt / verletzt / nicht prüfbar | Eigener Test-/Code-/CI-Beleg | Finding-ID |
|---|---|---|---|
| **AC0B-01** | **erfüllt** | `gradle/conformance.gradle` startet den externen `InterlisConformance.java` direkt per `Exec`; kein zweiter allgemeiner Runner. Suite-Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`, Archiv-SHA-256 `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe`; der Cache-Tarball ergab mit `shasum -a 256` exakt diesen SHA. Frischer Lauf: 610 Fälle, 593 selektiert, 17 INTERLIS-1.0 ausgeschlossen; `run-config.json` mit `filter=^(ili23\|ili24)\.` und `xpassPolicy=fail`; roh **580 PASS / 12 FAIL / 1 XFAIL / 0 XPASS / 0 ERROR**, Runner-Exit **1**; Projekt **580 PASS / 13 XFAIL / 0 FAIL/XPASS/ERROR**, Assessment-Exit **0**. Eigener Node-Verifier `verify-conformance.cjs`: `CONFORMANCE_R2_PASS`. | — |
| **AC0B-02** | **erfüllt** | Alle **18** Assessments in `build/reports/conformance/conformance-summary.json` sind `result=PASS`; exakt **sechs** tragen `compatibilityDecision.id=LEGACY_OID_23_WITHOUT_CONTRACTED` und `userDecision=APPROVED_USER`, die übrigen zwölf nicht. Für die sechs wurden Original (Exit 0), Positivkontrolle (Exit 0) und Negativkontrolle (Exit 1) tatsächlich ausgeführt; die Negativdiagnose ist je `ILI-SEM-0010`, `ERROR`, `SEMANTIC_CHECKING`, `diagnostic.semantics.inheritance`, Argument enthält `OID_DOMAIN`, Quelle `oid23-wrong-domain.ili`, Revision `1`, UTF-16-Range `106..164`. Eigener Verifier hat alle Original-/Positiv-/Negativ-Command-Dateien gehasht und mit den Manifest-Pins, inklusive `e14f6c54…a05`, verglichen. Der separate INTERLIS-1-Profilfall bleibt als eigener roher XFAIL (`ili23.Attribute10Test.attributeComplete`) ausserhalb der 18 Assessments. Roh- und Projektbilanz sind getrennt. | — |
| **AC0B-03** | **erfüllt** | `git show`-Vergleich der Basisdatei: 18 Fälle, davon 17 `rejected` und 1 `accepted`; im Kandidaten wechseln **exakt die sechs genannten Fälle** von `rejected` zu `accepted`. Bei den übrigen zwölf sind `ownExpectation`, `referenceExpectation`, `sources`, `positiveControls`, `negativeControls`, `allowedDiagnostics`, `languageVersion` und `norm` unverändert. Rohbilanz Basis (L0B-1-Bericht) 574/18/1, Kandidat 580/12/1; die Differenz ist genau die sechs Fälle. `NormativeConformanceContractTest` lief mit **23/23** Tests, darunter fehlende/doppelte IDs, geänderte Hashes, XPASS/ERROR, falsche/fehlende Kontrolle, veraltete Reports, unapproved/raw-PASS-Freigabe und Negativkontrolle. Suite-/Archivpin und `baseline.json` unverändert; kein XPASS/ERROR. | — |
| **AC0B-04** | **erfüllt** | `g2EvidenceTest` ist Teil von `check` und wurde im Gesamtlauf ausgeführt. XML-Aggregation: Modul-`test` **730** Tests, `evidenceTest` **136**, `g2AssertionTest` **15**, `verificationTest` **15**; jeweils **0 Failures / 0 Errors / 0 Skips**. `AssertionEvidenceTest` (25 Tests) prüft Suite, Klasse, Methode, falsche Klasse bei gleicher Methode, Zähler, Details, Skips, Frische, Details-Tags und XML-/Quellhashes; `CatalogueEvidence`/`RuntimeReports` prüfen Norm-/Entscheidungs- und Roh-/Projektbindungen. Die Bewertung nutzt frische XML-Bytes aus demselben Lauf. | — |
| **AC0B-05** | **erfüllt** | Migrationsmatrix `docs/g2/python-migration.md` ordnet alle 36 Ausgangsdateien einer Entscheidung zu; L0B-3 entfernt die letzten neun. Aktive Norm-/PDF-/Versions-/Matrix- und Katalogverträge bleiben: `g2EvidenceTest` 136 Tests grün. `TextLengths.java` SHA-256 `47e90b02842b0305ff9ef63fc20a8eb9478d2c9f44385408392a9381eaecd207`; entpackte Unicode-17-Dateien `2e1efc1d…470c` und `5019ffd5…87db`; `UnicodeWeightsTest` prüft **100170** explizite NFD-Vergleiche und einen manipulierten GZip. PDFs `8ab1c6b5…bc52`/`a8fa2679…5069`; pypdf-6.10-GZip-JSONs unverändert; kein PDFBox-Neuextraktor. `g2-source-inventory.json`/`predefined-catalogue.json` enthalten nur mechanische Anker-/Zeilen-/Hashverschiebungen; die `atomicRules`-Normzitate blieben unverändert (eigener strukturierter Diff). | — |
| **AC0B-06** | **erfüllt** | SDK **9** echte Distributionskommandos (Exit 0/0/0/0/1/1/0/0/2, inkl. Version, Capabilities, Check positiv/negativ, Format check/write, Usage); Referenzprofil **5/5 PASS**; Original-Views **19/19** beidseitig; Views **134** eigene Erwartungen, **102** Referenzübereinstimmungen, **6** separat sichtbare Referenzprozessfehler; Units **123/123** eigene, **121** Übereinstimmungen, **0** Prozessfehler; Corpus **13** Modelle; JVM-Benchmark **13** Modelle mit je 3 Check-/Formatmessungen; Wasm-Benchmark **4** Modelle; Browserbenchmark Chromium **12**, Firefox **12**, WebKit **1** gemessen, historische Skips **1/1/12** sichtbar. Eigene Corpus-Probe mit relativem `GEO_CORPUS_CACHE`: frisch **14** Seed-Dateien/13 Modelle Exit 0; manipuliertes Modell und manipulierter Index je Exit **1**; Löschen + Seed-Restore wieder Exit 0. Keine still deaktivierte Aufgabe. | — |
| **AC0B-07** | **erfüllt** | `git ls-files '*.py'` ist leer (Basis 9 Dateien). `pythonFreeCheck` scannte **1181** getrackte/nicht ignorierte Eingaben und war grün. Eigene Canary `tools/review-r2-canary.sh` mit `xargs -0 -n1 /usr/bin/env -S python3 …`: direkter Scanner **Exit 1** (`Active Python/pip command: …`), danach wieder **Exit 0/1181**. Eigene Gradle-Canary `:interlis-cli:pythonFreeCheck --rerun-tasks --no-daemon`: **Exit 1 / BUILD FAILED**, nach Entfernen **Exit 0 / BUILD SUCCESSFUL / 1181**. `.py`-Canary Exit 1; legitime `/usr/bin/env node`-Shell Exit 0. OS-Laufzeit: PATH-`python3` **127/ENOENT**, absolutes `/usr/bin/python3` **71/EPERM**, `/usr/bin/env python3` **126/EPERM**, Node-Kontrolle Exit 0; dieselbe Canary unter `sandbox-exec` Exit 1. Keine aktiven Python-/pip-Aufrufe. | O2 (Restlücke, nicht blockierend) |
| **AC0B-08** | **erfüllt** | Exakter Vollauf aus dem sauberen Worktree: **Exit 0**, `BUILD SUCCESSFUL in 5m 33s`, **79 Tasks ausgeführt**, `--no-daemon`, privater PATH und vererbte macOS-`process-exec`-Sperre. `environment.json`: `gitHead=d67b0a6…`, `dirty=""`, `status=passed`, `exitCode=0`; Proben `python/python3/pip/pip3/pypy/uv` = ENOENT, `/usr/bin/python3` und `/usr/bin/pip3` = EPERM. Funktionale Plattformläufe jeweils **3279** Fälle: TeaVM-JS, WasmGC, Web Image Node, Web Image Chromium/Firefox/WebKit, TeaVM Chromium/Firefox/WebKit, reales Safari; keine funktionalen Skips. T2, SDK, Referenz, Corpus, JVM/Wasm/Browser-Benchmark, LSP-Installation und `pythonFreeCheck` waren im selben Taskgraph. | — |

**Kumulative Phase 0b (AC0B-01 bis AC0B-08):** Alle acht Kriterien wurden anhand eigener Läufe, Rohreports, Code und eigener Gegenproben erneut geprüft, nicht nur über alte Reviewberichte. Der Full-Gate-Lauf und die Nachproben binden an exakt `d67b0a669ac41261078fe02dafe1544e2c1ef3bb`; das kumulative Phase-0b-Gate ist damit erfüllt.

## Eigene Gegenproben

### Gegenprobe 1 – Metadatenfortsetzung, öffentliche Vorkommen, UTF-16 und Fresh/Reuse

**Hypothese und Risiko:** Die neue Fortsetzung könnte nur LF ohne Einrückung beherrschen, `fn.param` an einer Funktion nur scheinbar wiederholen, `values()`/`entries()` mutierbar machen oder bei Fresh/Reuse alte Werte weiterreichen.

**Input:** `build/review/L0B-3/r2/probes/MetadataR2Probe.java` (Reviewerquelle, nicht im Kandidaten). Modell mit `fn.description`, drei `fn.param` (inkl. identischer Dublette), einem über zwei `!!@`-Zeilen fortgesetzten `fn.return` mit escaped quotes, Einrückung und 🦊, für 2.3/2.4 und LF/CRLF, in `STRICT` und `EDITOR`; ferner Konflikte an `fn.description`, `fn.param` an `DOMAIN`/`PARAMETER`, offener String und Revision-2-Reuse.

**Erwartet:** Fünf Occurrences in Quellreihenfolge; `values("fn.param")` in Quellreihenfolge; `values()` erster Index; exakter dekodierter Text und exakter Original-UTF-16-Slice; unveränderliche Rückgaben; Konfliktfälle `SOURCE_ERRORS` ohne `ILI-INTERNAL-0001`; Fresh/Reuse gleich und veröffentlichte Metadaten unverändert.

**Kommando / Exit:** `javac … MetadataR2Probe.java` → Exit 0; `java … MetadataR2Probe` → **`METADATA_R2_PROBE_PASS checks=146`, Exit 0**.

**Ergebnis:** Hypothese widerlegt; der Vertrag hält in beiden Sprachen, Zeilenenden, Modi und Reuse. Kein Finding.

### Gegenprobe 2 – Grenze der 2.3-OID-Kompatibilität

**Hypothese und Risiko:** Die Entfernung der CONTRACTED-Vorbedingung könnte zu breit wirken und Domänenbindung, Override oder andere CONTRACTED-Regeln mitfreigeben.

**Input:** `build/review/L0B-3/r2/probes/OidR2Probe.java`: `OID AS`/`BASKET OID AS` ohne und mit CONTRACTED in 2.3/2.4, Vererbung; falsche OID-Domäne; inkompatibler Override; `PARAMETER`/`FUNCTION` ohne CONTRACTED in 2.3 und 2.4.

**Erwartet:** 2.3-OID ohne CONTRACTED akzeptiert, Domänen korrekt gebunden; `PARAMETER_CONTRACTED`, `FUNCTION_CONTRACTED`, `OID_DOMAIN`, `OID_OVERRIDE` weiterhin abgelehnt; 2.4 unverändert.

**Kommando / Exit:** `javac … OidR2Probe.java` → Exit 0; `java … OidR2Probe` → **`OID_R2_PROBE_PASS checks=52`, Exit 0**.

**Ergebnis:** Hypothese widerlegt; die Ausnahme ist auf die 2.3-OID-Bedingung begrenzt. Kein Finding.

### Gegenprobe 3 – Corpus-Seed, relativer Cache und falsche Bytes

**Hypothese und Risiko:** Der Seed könnte falsche Bytes akzeptieren, einen vorhandenen falschen Cache still überschreiben oder einen relativen Cachepfad anders auflösen als die Node-Werkzeuge.

**Input:** Frischer temporärer Root mit `GEO_CORPUS_CACHE=cache` (relativ); `GeoCorpus` mit dem Seed; danach manipuliertes `AGI__SO_FunctionsExt.ili`, Löschen und erneuter Seed-Restore, danach manipuliertes `ilimodels.xml`.

**Erwartet:** Positiv 14 Dateien/13 Modelle Exit 0; manipulierte Datei und Index Exit 1 mit „Changed or missing pinned input“; Restore Exit 0.

**Kommando / Exit:** `java … guru.interlis.cli.verification.GeoCorpus "$PWD"` → **Exit 0 / `CORPUS_PASS 13 models`**; manipuliertes Modell **Exit 1**; Restore **Exit 0**; manipulierter Index **Exit 1**. Der Cachepfad lag unter dem temporären Root, nicht im Repository.

**Ergebnis:** Hypothese widerlegt; Seed-/Cacheintegrität und relative Auflösung funktionieren. Kein Finding.

### Gegenprobe 4 – Python-freier Scanner und echte OS-Sperre

**Hypothese und Risiko:** `pythonFreeCheck` könnte die neue, aus `xargs`/`env -S`/Pipe zusammengesetzte Form übersehen; der Laufzeitharness könnte nur PATH bereinigen, aber absolute Interpreter nicht sperren.

**Input:** `tools/review-r2-canary.sh`:
```sh
#!/bin/sh
printf '%s\0' model.ili | xargs -0 -n1 /usr/bin/env -S python3 -c 'print(1)'
```
Zusätzlich eine `.py`-Canary, eine legitime `/usr/bin/env node`-Shell und die aus dem Full-Gate-`environment.json` entnommene Sandbox-Profilkopie.

**Erwartet:** Scanner Exit 1 und Benennung der Datei; nach Entfernen Exit 0/1181; `.py` Exit 1; Node-Shell Exit 0. OS: PATH ENOENT, absolute Pfade und `env` EPERM, Node-Kontrolle erlaubt.

**Kommando / Exit:** Direkter Scanner **Exit 1** (`Active Python/pip command: tools/review-r2-canary.sh`), nach Entfernen **Exit 0 / 1181**; `.py` **Exit 1**; Node-Shell **Exit 0**; Gradle-Canary **Exit 1 / BUILD FAILED**, danach **Exit 0 / BUILD SUCCESSFUL / 1181**. OS: PATH `python3` **127/ENOENT**, `/usr/bin/python3` **71/EPERM**, `/usr/bin/env python3` **126/EPERM**, Node-Kontrolle **0**; dieselbe Shell-Canary unter `sandbox-exec` **Exit 1** mit „Operation not permitted“.

**Ergebnis:** F1 ist für die verlangten Formen behoben und der Laufzeitnachweis ist unabhängig. **Restbeobachtung O2:** Die YAML-Listenschreibweise `- run: /usr/bin/env -S python3 …` wurde in einer explorativen Probe nicht erkannt (Exit 0). Keine aktive Datei verwendet diese Form; die OS-Sperre verhindert die Ausführung. Kein blockierendes Finding, aber eine Empfehlung für die nächste Scannerpflege.

### Gegenprobe 5 – Konformitätskonsistenz und Pinbindung

**Hypothese und Risiko:** Die sechs Kompatibilitätsfälle könnten nur über die Zusammenfassung „passend“ gemacht worden sein, ohne echte Original-/Positiv-/Negativprozesse, Pins oder Diagnosen.

**Input:** `build/review/L0B-3/r2/probes/verify-conformance.cjs` über `build/conformance/report.json`, `build/reports/conformance/conformance-summary.json` und `docs/evidence/conformance/normative-expectations.json`.

**Erwartet:** 593 rohe Fälle; roh 580/12/1; Projekt 580/13; 18 Assessments; exakt sechs Kompatibilitätsfälle; zwölf rohe FAIL-IDs = zwölf verbleibende Assessments; alle Command-Dateien hashen zu den Manifest-Pins; Negativdiagnose und Range exakt; INTERLIS-1-XFAIL separat; Suite-Pins unverändert.

**Kommando / Exit:** `node …/verify-conformance.cjs` → **`CONFORMANCE_R2_PASS checks=18 assessments`, Exit 0**.

**Ergebnis:** Hypothese widerlegt; die Bilanzen, Pins und Diagnosen sind konsistent. Kein Finding.

### Gegenprobe 6 – JUnit-Zählung und unabhängige Plattformzahlen

**Input:** Eigene XML-Aggregation `aggregate-junit.cjs` über alle Modul-XMLs sowie die Live-JSONs aus Portable/Browser/WebImage/Performance.

**Erwartet/beobachtet:** Modul-`test` 730 (621+16+10+19+12+33+16+3), `evidenceTest` 136, `g2AssertionTest` 15, `verificationTest` 15; alle 0/0/0. Portable JS/WasmGC je 3279; TeaVM Chromium/Firefox/WebKit je 3279; WebImage Node und Browser je 3279; Safari 3279; JVM 13 Modelle, Wasm 4, Browser 12/12/1 gemessen mit 1/1/12 sichtbaren historischen Skips.

**Ergebnis:** Die Zahlen des Implementierungsberichts sind für diesen SHA reproduziert. Kein Finding.

## Pflichtprüfungen

**Vollkommando (exakt, aus dem sauberen Worktree):**
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
Der Harness ergänzte `--no-daemon`. Ergebnis: **Exit 0**, `BUILD SUCCESSFUL in 5m 33s`, **79 actionable tasks: 79 executed**, Start `18:39:27Z`, Ende `18:45:02Z`. Rohlog: `build/review/L0B-3/r2/raw-full-gate.log`.

**Tatsächliche Exits und Mengen:**
- `check`/`g2EvidenceTest`: im Graphen ausgeführt; Modul-`test` 730, Evidenz 136, Assertions 15, Host/Verifikation 15; **0 Failures / 0 Errors / 0 Skips**.
- T2: 610 Fälle, 593 selektiert, 17 INTERLIS-1.0 ausgeschlossen; roh **580 PASS / 12 FAIL / 1 XFAIL / 0 XPASS / 0 ERROR**, Runner-Exit 1; Projekt **580 PASS / 13 XFAIL / 0 FAIL/XPASS/ERROR**, Task-Exit 0; 18/18 Assessments PASS; sechs Kompatibilitätsfälle, zwölf verbleibende Ausnahmen; INTERLIS-1-XFAIL separat.
- `checkPortable`: TeaVM-JS **3279**, WasmGC **3279**.
- `checkBrowser`: TeaVM Chromium/Firefox/WebKit je **3279**, reales Safari **3279**, Web Image Chromium/Firefox/WebKit je **3279**; keine funktionalen Skips.
- `webImageProbe`: Node Web Image **3279**.
- `conformanceCheck`: Assessment-Exit 0.
- `sdkProcessCheck`: **9** Kommandos; `sdkPackage` ZIP gebaut; `:interlis-lsp:installDist` und LSP-Tests (16) erfolgreich.
- `geoCorpus`: **13** Modelle; `referenceCheck`: **5/5**; `originalViewReferenceCheck`: **19/19**; `viewReferenceCheck`: **134/102/6**; `unitReferenceCheck`: **123/121/0**.
- `performanceCheck`: JVM **13** Modelle, Warmup 1 + 3 Messungen, RSS in Bytes pro Kindprozess; Wasm **4** Modelle; Browser Chromium **12**, Firefox **12**, WebKit **1** gemessen; Skips 1/1/12 sichtbar; JVM-Benchmark Exit 0.
- `pythonFreeCheck`: **1181** Eingaben, Exit 0.
- `environment.json`: `gitHead=d67b0a669ac41261078fe02dafe1544e2c1ef3bb`, `dirty=""`, `status=passed`, `exitCode=0`, Proben ENOENT/EPERM.

**Rohberichte / Bindung an CANDIDATE_SHA:**
- `/private/tmp/interlis-java-L0B-3-review-d67b0a6/build/review/L0B-3/r2/raw-full-gate.log`
- `/private/tmp/interlis-java-L0B-3-review-d67b0a6/build/reports/python-free/environment.json` und `source-check.json`
- `/private/tmp/interlis-java-L0B-3-review-d67b0a6/build/conformance/{run-config.json,summary.json,report.json,runner-exit.txt,conformance.log}`
- `/private/tmp/interlis-java-L0B-3-review-d67b0a6/build/reports/conformance/conformance-summary.json`
- `/private/tmp/interlis-java-L0B-3-review-d67b0a6/build/reports/{sdk,reference,stabilization,performance}/…`
- Modul-XML unter `interlis-*/build/test-results/{test,evidenceTest,g2AssertionTest,verificationTest}`; LSP-Report unter `interlis-lsp/build/test-results/test`.
- `interlis-wasm-teavm/build/{js-contract-result.json,wasm-contract-result.json,browser-results.json,safari-result.json}`
- `interlis-wasm-webimage/build/webimage/result.json` und `build/webimage-browser/browser-results.json`
- Eigene Prüfungen: `build/review/L0B-3/r2/probes/` (Probequellen, `*.log`, `junit-counts.log`, `verify-conformance.cjs`).

**Nicht ausgeführte fällige Prüfungen:** Keine. `releaseCheck`/P3-Release, P4 und P5 sind nicht Bestandteil von L0B-3 und wurden nicht als Ersatz für Pflichtgates verwendet. Die alten Python-Skripte wurden nicht erneut ausgeführt; der geforderte Python-freie Weg und die Java-/Node-Nachfolger wurden geprüft.

**Norm-/Suite-/XFAIL-/Baseline-/Quellpin-Unverändertheit:** Suite-Commit `88576ffc…d2c3` und Archiv-SHA `6a80b7e8…37fe` unverändert und erneut aus dem Cache verifiziert; `baseline.json` unverändert mit nur dem separaten INTERLIS-1-XFAIL. Die sechs `ownExpectation`-Änderungen sind die einzigen Änderungen an den 18 Erwartungen; die übrigen zwölf sind in allen fachlichen Feldern unverändert. Original-/Positiv-/Negativquellen und ihre SHA-256 sind gepinnt; die sechs Negativkontrollen zeigen auf die neue gepinnte `oid23-wrong-domain.ili`. Corpusmanifest und alle 13 Quellpins unverändert; `corpus-seed.zip` (SHA-256 `4506dfa7…264`) enthält exakt die 14 Originaldateien mit den Manifest-Hashes und Indexmetadaten; 13 Modelle/14 Dateien. Unicode-, PDF- und TextLengths-Pins unverändert; keine XFAIL-/Baseline-/Pin-Lockerung.

**Python-Migrations-/Phaseninvarianten:** 36 getrackte `.py` an der Ausgangsbasis, 9 in L0B-3 entfernt, aktuell **0**. Die Migrationsmatrix klassifiziert alle 36 Dateien; die letzten neun sind `FREEZE_ARCHIVE`, `REPLACE_WITH_TEST`, `PORT_MINIMAL` oder `DELETE_REDUNDANT` mit konkretem Ersatz. Aktive Gradle-/CI-/Shell-/Node-/Reviewpfade enthalten keinen Python-/pip-Aufruf; historische Dokumente bleiben lesbar. Der Laufzeitharness belegt die Abwesenheit für PATH und absolute Pfade; die Scanner-Canary belegt die Quellerkennung.

**Kumulative Phasenregression:** AC0B-01 bis AC0B-05 wurden anhand eigener Rohberichte, XML-Zählungen, `git show`-Vergleiche und Pinprüfungen erneut bewertet; AC0B-06 bis AC0B-08 wurden im selben frischen Gesamtlauf und mit eigenen Gegenproben geprüft. Das Phase-0b-Gate ist am Kandidaten-SHA erfüllt.

## Findings

**Keine MAJOR-/BLOCKER-Findings.**

**O1 / Beobachtung (nicht abnahmeblockierend) – zwei historische Texte im Entscheidungspaket sind nach der Sechser-Freigabe widersprüchlich.**
Ort: `docs/evidence/stabilization/g2-normative-decision-packet-2026-10-28.json` (`purpose`/`normalization` sagen weiterhin „no expectation is changed“) und `docs/evidence/stabilization/g2-rule-matrix.json` (`normativeDecisionPacket.remainingCause` sagt weiterhin „no expectation was changed“, während `expectationsChanged=true` und sechs Fälle freigegeben sind). Die fachlichen Case-Daten, `normative-expectations.json`, `CatalogueEvidence.decisions` und alle 18 Assessments sind korrekt und konsistent. Empfehlung: diese beiden historischen Prosa-Felder bei der nächsten Pflege auf den Sechser-Stand nachziehen; kein Kriterienverstoss.

**O2 / Beobachtung (nicht abnahmeblockierend) – Scanner-Restlücke bei YAML-Listenschreibweise.**
`pythonFreeCheck` erkennt `run: …`, `exec`, `env`, absolute Pfade, `xargs`, Pipes und `$()`; die explorative Form `- run: /usr/bin/env -S python3 …` wurde nicht erkannt (Exit 0). Keine aktive Datei verwendet diese Form; der Laufzeitharness verweigert die Ausführung unabhängig davon (EPERM). Empfehlung für eine spätere Scannerpflege, kein Finding gegen AC0B-07.

**O3 / Beobachtung (nicht abnahmeblockierend) – Live-Browsercontracts protokollieren 3279, assertieren es aber nicht selbst.**
Die tatsächlichen Läufe zeigen für alle funktionalen Browser-/Safari-/WebImage-Pfade 3279; die Contract-Skripte loggen den Wert, während `RuntimeReports` ihn nur anhand des eingefrorenen Profils prüft. Eine spätere stille Fallreduktion würde daher nicht zwingend am Contract scheitern. Empfehlung: `stabilizationCases===3279` in den Live-Contract-Skripten assertieren; kein Finding für diesen Lauf.

## Urteil

**PASS**

Begründung: Der Kandidat `d67b0a669ac41261078fe02dafe1544e2c1ef3bb` erfüllt AC0B-01 bis AC0B-08 und das kumulative Phase-0b-Gate. Der frühere F1-Scannerfehler ist für die verlangten Formen behoben und mit einer eigenen, tatsächlich ausgeführten Gradle-Canary (Exit 1) sowie direkten Scannerproben nachgewiesen; F2 ist durch den vollständigen Gesamtlauf mit `BUILD SUCCESSFUL`, Exit 0, 79 Tasks, T2 580/12/1 roh und 580/13 Projekt, allen 18 Assessments und allen funktionalen Plattformläufen mit 3279 Fällen behoben. Es gibt keine offenen MAJOR-/BLOCKER-Findings. Die Beobachtungen O1–O3 betreffen historische Prosa, eine nicht aktive Scanner-Randform und eine fehlende Live-Assertion; sie ändern die Abnahme dieses Laufs nicht.

Nächster konkreter Schritt: L0B-3/Phase 0b ist freigegeben; erst danach darf L1-1 begonnen werden. Die Beobachtungen O1–O3 können in einer späteren Pflege bereinigt werden, ohne den geprüften Produktcommit rückwirkend zu ändern.
