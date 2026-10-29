# Unabhängiger Review: L0B-1

Lieferabschnitt / Phase / letztes Phasengate?: **L0B-1 / Phase 0b / nein** (Phasengate ist L0B-3)
Implementierungsagent: Codex
Prüfagent: OpenCode; tatsächliche Provider-/Modellkonfiguration: **direkter Provider `deepseek`, Modell `deepseek/deepseek-flash`** (Modellkatalogname „DeepSeek V4.1 Flash 1017“), OpenCode **v2.0.28**. Kein anderes Modell/Provider in dieser Prüfung.
BASE_SHA: `c671ce3aa943afcc53579b4cd488773afbd0206e`
CANDIDATE_SHA (durch git überprüft): `74f504405e726bf9922d19a228f424e3ff76421c`
Prüfumgebung / Toolchain: macOS 15.8.1 aarch64; Temurin **25.0.3+9** (`JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem`); Gradle-Wrapper **9.8.1**; eigener Worktree `/private/tmp/interlis-java-L0B-1-review-74f5044`. `HEAD` und `git status --porcelain` vor und nach der Prüfung geprüft; Arbeitsbaum sauber. Produktquellen, Tests, Kriterien und Pins des Kandidaten wurden nicht verändert. Temporäre Prüfkopien liegen ausschliesslich unter `build/`; kein Commit/Push, kein L0B-2.

## Kriterienprüfung

| Kriterium-ID | Erfüllt / verletzt / nicht prüfbar | Eigener Test-/Code-/CI-Beleg | Finding-ID |
|---|---|---|---|
| **AC0B-01** | **Erfüllt** | `gradle/conformance.gradle` startet den externen `InterlisConformance.java` direkt per Gradle-`Exec`; kein zweiter Runner. Eigener frischer Lauf: 593/610 Fälle, 17 INTERLIS-1.0-Fälle ausgeschlossen; roh 574 PASS / 18 FAIL / 1 XFAIL / 0 XPASS / 0 ERROR, Runner-Exit **1**; Projekt 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS, Task-Exit **0**. Archiv-SHA-256 `6a80b7e8…37fe`; extrahierter Runner bytegleich mit dem Archiv (`0f83ff36…e5ff`). Frischeberichte nach `run-start.txt` geprüft. | — |
| **AC0B-02** | **Erfüllt** | 18 Normausnahmen in `normative-assessment.json`, alle 18 `PASS`; 22 Original- und 23 Kontroll-Hashes geprüft; Diagnosen mit Code/Severity/Phase/MessageKey/Argumenten/Revision/UTF-16-Range und Multiplizität verglichen. Roh- und Projektbilanz getrennt. Der separate INTERLIS-1-Profilfall `ili23.Attribute10Test.attributeComplete` bleibt als eigener XFAIL sichtbar und wird nicht als Normausnahme bewertet. | — |
| **AC0B-03** | **Erfüllt** | Eigener Alt-/Neu-Vergleich: alter Pythonpfad am BASE_SHA selbst ausgeführt (20 Wrappertests OK, 593 Fälle) und gegen den neuen Lauf am CANDIDATE_SHA verglichen: 593/593 gleiche IDs, **0 Feldabweichungen** über Sprache, Inputs, Erwartung, Outcome, Klassifikation, Exit, Timeout, Kommando, stdout/stderr und Quellmetadaten; Summary, Normbewertung und Exit 1 identisch. Die 22 neuen JUnit-Tests (21 `NormativeConformanceContractTest` + 1 `OriginalViewFixtureContractTest`) liefen im frischen Gesamtlauf; darunter die Canaries für fehlende/duplizierte/fremde IDs, geänderten Original-/Kontroll-/Suitehash, XPASS/ERROR, fehlende/falsche Kontrolle, internen Fehler, Cancellation, INCOMPLETE_SUPPORT, kaputtes JSON und veraltete Rohberichte. Die beiden Python-Dateien und ihre aktiven Aufrufe sind entfernt. | — |

## Eigene Gegenprobe (neu, nicht aus dem Kandidatentest kopiert)

**Hypothese und fachliches Risiko:** Die Java-Normbewertung könnte bei einem echten der 18 Fälle nur Mockdaten prüfen und eine Veränderung der exakten UTF-16-Diagnose im versionierten Manifest durchlassen.

**Neu erstellter Testinput / exakter Pfad:** Temporäre Kandidatenkopie `build/review/L0B-1/cache-probe` (aus `git archive 74f5044…`), frische Suiteberichte aus dem eigenen Lauf hineinkopiert. Im kopierten `docs/evidence/conformance/normative-expectations.json` wurde für `ili24.Refhb24Test.roadsExgm2ien` der erste `allowedDiagnostics[0].range.startOffset` von **10847** auf **10848** geändert. Der Kandidaten-Worktree blieb unberührt.

**Erwartetes Verhalten:** `conformanceAssessment` muss mit Exit 1 und `overallResult=FAIL` scheitern; genau dieser eine Assessmentfall darf nicht mehr `PASS` sein. Nach Rücknahme der Änderung muss wieder 18/18 `PASS` gelten.

**Kommando / Exitcode / beobachteter Output:**

```sh
cd build/review/L0B-1/cache-probe
JAVA_HOME=… ./gradlew :interlis-cli:conformanceAssessment \
  -x runConformanceSuite -x prepareConformanceSuite
# EXIT 1
# selected=593 result=FAIL rawExit=1
# {"PASS":574,"XFAIL":18,"FAIL":1,"XPASS":0,"ERROR":0,"TOTAL":593}
# ili24.Refhb24Test.roadsExgm2ien:
#   IllegalArgumentException: semantic diagnostics differ from the pinned expectation
```

Nach Wiederherstellung des Manifests: Exit **0**, `PASS`, 18 Assessments. Logs: `build/review/L0B-1/normative-probe-negative.log`, `…/normative-probe-restored.log`.

**Ergebnis und Schlussfolgerung:** Die Änderung einer einzelnen UTF-16-Range im echten Manifest wird auf dem realen 18er-Fall erkannt. Die strenge Normbewertung ist nicht nur eine Testfassade.

**Zusätzliche eigene Gegenprobe (Hash/Control):** In derselben temporären Kopie wurde die negative Kontrolldatei `testdata/conformance/normative/ili23.LineType23Test.overlapTooSmallFail.zero.ili` um ein Byte verändert. Erwartungsgemäss Exit **1**, `control source pin mismatch`, genau der betroffene Fall `FAIL`; nach Wiederherstellung Exit **0**, `PASS`. Logs: `build/review/L0B-1/normative-control-hash-negative.log`, `…/normative-control-hash-restored.log`.

## Pflichtprüfungen

**Ausgeführte Kommandos / Exits / Testzahlen**

```sh
JAVA_HOME=… ./gradlew check conformanceCheck --rerun-tasks
# Exit 0, 44 Tasks ausgeführt, 1m41s
# 728 JVM-Tests in 149 Testsuiten: 0 Failures, 0 Errors, 0 Skips
# davon 59 G2-Evidenztests (Python-G2-Pfad, ausserhalb L0B-1) OK
# 22 neue Conformance-Tests: 21 NormativeConformanceContractTest + 1 OriginalViewFixtureContractTest
# 593/610 Fälle; roh 574/18/1/0/0; Projekt 574/19/0/0/0; Task-Exit 0
```

Rohlog: `build/review/L0B-1/verification-r3-review.log`.

```sh
cd build/review/L0B-1/base-old   # git archive BASE_SHA c671…
JAVA_HOME=… ./gradlew conformanceCheck
# Exit 0; 20 Python-Wrappertests OK; 593 Fälle; roh 574/18/1/0/0; Projekt 574/19/0/0/0; Exit 1
```

```sh
python3 build/review/L0B-1/parity-own.py
# Exit 0
# 593/593 gleiche IDs; 0 case diffs; summary.json gleich; normative-assessment.json gleich;
# auch der kombinierte Projektbericht ist nach Root-/diag-ID-Normalisierung gleich;
# Exit 1 in beiden; Auswahl 610/593/17, Versionen 2.3/2.4, ausgeschlossen 1.0
```

Logs: `build/review/L0B-1/base-old-conformance.log`, `.../parity-own.log`. Der Codex-`parity.cjs` wurde nicht als alleiniger Nachweis verwendet; die eigene Basisausführung ist der primäre Alt-/Neu-Beleg. Zusätzlich stimmte `parity-review.py` gegen die Codex-Legacy-JSONs ohne Abweichung (Exit 0). `git diff --name-status BASE..CANDIDATE` enthält keine Compiler-/Modell-/Runtimequellen; die Parität vergleicht daher denselben Compilerstand mit geändertem Conformance-/Test-/Buildpfad.

**Cachekorrekturen (eigene reale Proben, ohne `--rerun-tasks`)**

- **Externe Originalfixtures:** Baseline `:interlis-cli:test --tests '*OriginalViewFixtureContractTest'` → Exit 0. Danach im temporären Checkout eine Pinzeile in `sources.properties` verändert → Task wurde tatsächlich ausgeführt (nicht `UP-TO-DATE`) und endete Exit 1 mit `AssertionFailedError` in `OriginalViewFixtureContractTest.java:31`; nach Wiederherstellung Exit 0. Logs: `cache-fixture-baseline.log`, `cache-fixture-pin-negative.log`, `cache-fixture-restored.log`.
- **Portabler JS-Fixtureblock:** Eine eingebettete `sha256` im `stabilization-contract.cjs` verändert → Task wurde ausgeführt, Exit 1, derselbe Assertionfehler; nach Wiederherstellung Exit 0. Logs: `cache-js-negative.log`, `cache-js-restored.log`.
- **Relativer `CONFORMANCE_CACHE`:** Im temporären Checkout `CONFORMANCE_CACHE=build/review-relative-cache-r3`; dort ein absichtlich falsches Archiv gelegt → `prepareConformanceSuite` Exit 1, `Suite archive hash mismatch`; im Gradle-Daemonverzeichnis entstand **kein** Pfad `review-relative-cache-r3`. Korrekt gepinntes Archiv an derselben relativen Repositoryadresse → Exit 0; extrahierter Runner SHA-256 `0f83ff36…e5ff`. Ein gefilterter realer Runnerlauf schrieb `suite`, `outputDirectory`, `cacheDirectory` und `baseline` als absolute Pfade, davon `cacheDirectory` unter dem Repository. Logs: `cache-relative-negative.log`, `cache-relative-positive.log`, `cache-relative-run.log`.

**Rohberichte / Bindung an CANDIDATE_SHA**

- `build/conformance/summary.json`, `report.json`, `report.junit.xml` (593 Tests, 18 Failures, 0 Errors, 1 Skip), `report.html`, `runner-exit.txt` (1), `normative-assessment.json` (18 Assessments, alle PASS), `run-config.json`, `conformance.log`.
- Projektbericht: `build/reports/conformance/conformance-summary.json` (`overallResult=PASS`, `referenceResult=FAIL`, `selection` 610/593/17).
- Test-XML: `interlis-cli/build/test-results/test/TEST-guru.interlis.cli.conformance.NormativeConformanceContractTest.xml` (21) und `…OriginalViewFixtureContractTest.xml` (1).
- Alle genannten Läufe wurden in diesem Worktree auf `74f504405e726bf9922d19a228f424e3ff76421c` erzeugt; `git rev-parse HEAD` und `git status --porcelain` nach der Prüfung unverändert.

**Nicht ausgeführte fällige Prüfungen mit Grund**

- Keine L0B-1-Pflichtprüfung ausgelassen.
- `checkPortable`, echte Safari-/Browser-/Web-Image-Matrix und ein vollständiger Python-freier Gesamtlauf sind **nicht** L0B-1-Pflichten; sie sind spätestens mit **L0B-3** fällig. `check` benötigt weiterhin Python für die 59 G2-Evidenztests; 34 getrackte Python-Dateien bleiben. Daraus wird hier keine Pythonfreiheit abgeleitet.
- Keine kumulative Phasenabnahme: L0B-1 ist nicht die letzte Lieferung der Phase 0b.

**Conformance-Fallmenge; rohe und projektspezifische Bilanz**

- Suite: 610 Fälle; ausgewählt 593 (`^(ili23|ili24)\.`), ausgeschlossen 17 mit Sprachversion 1.0.
- Roh: 574 PASS, 18 FAIL, 1 XFAIL, 0 XPASS, 0 ERROR; Runner-Exit 1; JUnit-XML 593/18/0/1.
- Projekt: 574 PASS, 19 XFAIL, 0 FAIL, 0 XPASS, 0 ERROR; Task-Exit 0; `referenceResult=FAIL` bleibt separat.
- Die 18 rohen FAIL-IDs sind exakt die 18 Manifest-IDs; kein zusätzlicher FAIL, keine fehlende Ausnahme.
- 18 Assessments PASS; 22 Original- und 23 Kontroll-Hashes; der separate INTERLIS-1-Profilfall `ili23.Attribute10Test.attributeComplete` ist der 19. XFAIL und nicht Teil der 18.

**Norm-/Suite-/XFAIL-/Baseline-/Quellpin-Unverändertheit**

- `git diff BASE..CANDIDATE` zeigt keine Änderung an `docs/evidence/conformance/baseline.json` oder `normative-expectations.json`; nur die README-Erläuterung wurde ergänzt.
- `gradle/conformance-suite.properties`: Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`, Archiv-SHA `6a80b7e8…37fe`, Codeberg-URL unverändert gegenüber dem alten Pythonpin; Defaultfilter `^(ili23|ili24)\.` unverändert.
- Archivhash selbst nachgerechnet; der extrahierte Runner ist bytegleich mit dem Archiv (`cmp`), SHA-256 `0f83ff36…e5ff`.
- Keine XFAIL-, Baseline-, Norm- oder Suiteänderung; keine Nutzer-Normentscheidung wurde freigegeben oder verändert.

**Python-Migrations-/Phaseninvarianten soweit relevant**

- Basis: 36 getrackte `.py`-Dateien; L0B-1 entfernt `tools/reference/conformance_check.py` und `test_conformance_check.py`; 34 bleiben für L0B-2/L0B-3. Matrix klassifiziert alle 36 genau einmal; aktive Conformance-Aufrufe enthalten kein Python mehr.
- Keine Pythonfreiheitsbehauptung, keine Browser-/Portable-Abnahme, keine L0B-2-Arbeit.

## Findings

Keine offenen Muss-Findings.

- Die früheren Urteile zu `c5a50bb` / Revision 2 wurden nicht übernommen; dieser Bericht gilt ausschliesslich für `74f504405e726bf9922d19a228f424e3ff76421c`.
- Die 18 offenen Nutzer-Normentscheidungen bleiben offen; dieser Review ändert sie nicht.

## Urteil

**PASS**

Begründung: Alle drei L0B-1-Kriterien sind am aktuellen SHA mit eigenen Läufen belegt: direkter unveränderter externer Runner, frische Rohberichte, 18/18 strenge Normausnahmen, getrennte Bilanzen und separate INTERLIS-1-Ausnahme, unveränderte Pins sowie echte Alt-/Neu-Parität über 593 Fälle. Beide Cachekorrekturen wurden mit tatsächlich ausgeführten Negativ-/Positivproben bestätigt; zusätzlich wurde eine neue, eigene Manipulation einer realen UTF-16-Range und eine Kontrollhash-Manipulation ausgeführt und korrekt abgewiesen. Die fälligen L0B-1-Pflichtprüfungen sind vollständig; nicht fällige Phasen-/Browser-/Pythonfreiheitsgates bleiben L0B-3 vorbehalten.

Nächster konkreter Schritt: L0B-2 erst nach diesem PASS beginnen; L0B-3 bleibt das vollständige 0b-Phasengate. Kein L0B-2 wurde in dieser Sitzung begonnen.
