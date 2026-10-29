# Unabhängiger Review: L1-2

Lieferabschnitt / Phase / letztes Phasengate?:
L1-2 / Phase 1 / **NEIN**. L1-3 ist das letzte Phase-1-Gate. Hier sind nur AC1-03 und AC1-04 zu prüfen. Browser/Safari/WebImage und das kumulative AC1-01–08-Gate sind L1-3 fällig.

Implementierungsagent: Codex

Prüfagent: OpenCode; tatsächliche Provider-/Modellkonfiguration:
Startkonfiguration gemäss Auftrag: **OpenCode v2.0.28**, `--model deepseek/deepseek-flash`, **DeepSeek DIREKT**, UI **DeepSeek V4.1 Flash 1017**, **kein OpenRouter**. Eine Modellselbstauskunft ist kein Identitätsnachweis; die Prüfung stützt sich auf diese vorgegebene Startkonfiguration und die tatsächlich ausgeführten Kommandos.

BASE_SHA: `f566e8de6f060ce4b0fef4215cbdc3100d06b1c4`
CANDIDATE_SHA (durch git überprüft): `39f48ca239c44b27a81e5562a58c717c90253534`
- `git rev-parse HEAD` im eigenen Worktree: `39f48ca239c44b27a81e5562a58c717c90253534`
- `git status --porcelain` vor und nach der Prüfung: leer.
- `git diff --stat BASE..CANDIDATE`: **30 Dateien, 1997 Insertions, 526 Deletions**.

Prüfumgebung / Toolchain:
- Eigener Worktree: `/private/tmp/interlis-java-L1-2-review-39f48ca`
- macOS arm64 (Host `Monterrico.local`)
- JAVA_HOME: `/Users/stefan/.sdkman/candidates/java/25.0.3-tem` (Temurin 25.0.3+9)
- Node: `/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node` (v24.22.0)
- Gradle 9.8.1 über den Repository-Wrapper
- Python-gesperrtes OS-Umfeld: `tools/verification/without-python.cjs` mit privatem PATH, `sandbox-exec`, geerbter OS-`process-exec`-Sperre und `--no-daemon`
- Kein Python/pip/uv durch den Reviewer verwendet.

## Kriterienprüfung

| Kriterium-ID | Erfüllt / verletzt / nicht prüfbar | Eigener Test-/Code-/CI-Beleg | Finding-ID |
|---|---|---|---|
| **AC1-03** – Numerische Unterteilungen, Skalierung, exakte Einheitenrelationen, OID und gemeinsame Typkompatibilität erfüllen die jeweilige Norm; zu jedem strittigen Profil liegt ein entscheidungsfähiger Norm-/Gegenfall vor | **Erfüllt** | Frischer Pflichtlauf: `checkPortable g2EvidenceTest conformanceCheck sdkProcessCheck pythonFreeCheck --continue --rerun-tasks` Exit 0. `NumericSubdivisionContractTest` 13/13, `ScaledBoundaryContractTest` 7/7 im vollen `test`-Task; eigene Java-/CLI-Gegenproben (62 + 16 + 6 + 6 Fälle); T2-Projektbilanz 579 PASS / 14 XFAIL / 0 FAIL mit 19/19 Assessments PASS; neue CIRCULAR-Ausnahme mit Original-/Kontrollhash und exakt zwei SEM-0010. | — |
| **AC1-04** – Text, Enum, FORMAT/Zeit, Coordinates/Geometrie, Collections/References und Typ-Queries erhalten tatsächliche Werte, Zielidentitäten und Grenzfälle in beiden Versionen | **Erfüllt** | Volles `check` mit 753 regulären JVM-Tests (davon 644 Compiler), 136 Evidenz-, 15 Assertion- und 15 Hosttests, **0 Failures/Errors/Skips**; bestehende Familienprüfungen unverändert und vollständig ausgeführt; neue öffentliche Schema-/Domainquery-Assertions in `NumericSubdivisionContractTest` und `ScaledBoundaryContractTest`; Node-JS/WasmGC je 3365 Fälle. | — |

### Traceability der im Lieferbericht genannten Anforderungs-IDs

| Anforderungs-IDs | Beobachtung / Beleg |
|---|---|
| SYN-004/010/013/015 | CONTINUOUS ohne SUBDIVISION wird als `ILI-SYN-0002`/PARSING am folgenden Namen gemeldet; EDITOR, STRICT und ATTRIBUTE_DECLARATION-Fragment mit UTF-16-Bereich und Revision geprüft (`NumericSubdivisionContractTest.continuousWithoutSubdivisionIsAParsingErrorWithExactNameRangeAndFragmentRecovery`, eigene CLI-Proben). Grammar/Recovery-Inventar-IDs unverändert. |
| SRC-002/005 | Diagnosen tragen SourceId/Revision und exakte UTF-16-Bereiche; eigener Java-Probe prüft `memory:/public-probe.ili`, Revision 11, nichtleere PrimaryRange; unveränderte geerbte Fehler werden am Original deklariert (`unchangedInvalidInheritedRelationsReportOnlyTheirOriginalDeclaration`). |
| META-006/008/009/011/014/016 | `NumericType`/`NumericRange`/`DecimalLiteral`/`UnitSymbol`/`EffectiveMember`/`FormattedType` öffentlich geprüft: Flags, Präzision, Lexeme, exakte Werte, Einheitenziele, effektive Reihenfolge, FORMAT-Ziele; keine neue öffentliche Nutzer-API. |
| SEM-003/005/010/011/014/017/019 | `ILI-SEM-0010` mit Severity ERROR, Phase SEMANTIC_CHECKING, Argumenten `element`/`rule` und stabilen Regelkennungen; negative Fälle für Vorgänger, Typ, Ganzzahligkeit, Nichtnegativität, CIRCULAR und Faktor; `SOURCE_ERRORS` von `RESOURCE_LIMIT`, ungelösten Typen und internem Fehler getrennt. |
| TEST-002/003/009/011/012 | Volles `check`, `g2EvidenceTest`, `conformanceCheck`, `sdkProcessCheck`, `pythonFreeCheck`; XML-Rohzahlen unten; portable Node-Verträge je 3365 Fälle; T2-Roh-/Projektbilanz getrennt. |

## Eigene Gegenprobe

Die folgenden Gegenproben wurden vom Reviewer neu erstellt und ausgeführt. Sie liegen ausschliesslich unter dem ignorierten `build/review/L1-2/probe/` des Review-Worktrees, nicht im Produktkandidaten. Keine vorhandene Testmethode wurde umbenannt oder wiederverwendet.

### G1 – Öffentliche Schema-/Query-Werte und Fehlerpfade (Java, neu)

**Hypothese und fachliches Risiko:** Der Compiler könnte intern richtig rechnen, aber ein falsches öffentliches `CompiledSchema` veröffentlichen (Flags, Vorgänger, Präzision, Lexem, Einheitenziel, FORMAT-Teile), oder Faktor-/Dimensionsfehler als falschen Ablehnungsgrund bzw. als internen Fehler melden.

**Neu erstellter Testinput:** `build/review/L1-2/probe/PublicProbe.java` (eigenständiges Java-Programm gegen die öffentliche Compiler-API; nutzt `interlis-cli/build/install/interlis-cli/lib/*`, also den im Pflichtlauf gebauten Kandidaten).

**Erwartetes Verhalten:**
- Clock-Struktur: `Hours` nicht Unterteilung, `Minutes`/`Seconds` Unterteilung + kontinuierlich + CIRCULAR; `Seconds`-Maximum `59.999`, Präzision 3 Nachkommastellen, Einheit `INTERLIS.s`; effektive Reihenfolge exakt `Hours, Minutes, Seconds`; FORMAT verweist auf genau diese Struktur und Attribute.
- Skalierte Periode: `0.000e1..0.599e2` = 0..59.9, Quantum 0.1, Periode 60; mit `h=60[s]` gültig.
- Faktorkette `a=6[s]; b=2[a]; c=3[b]` = 36 s: Periode 36 gültig, 35 mit genau einer `SUBDIVISION_FACTOR`-Diagnose abgelehnt.
- Gemeinsamer unbekannter Faktor `(u*INTERLIS.m)` vs `(u*INTERLIS.s)`: trotz Kürzung von `u` bekannter Dimensionswiderspruch, Ablehnung.
- Zwei TIME-Einheiten ohne Umrechnungspfad: Unterteilung gültig; öffentliches `compareUnits` bleibt `DISALLOWED`; Symbol-IDs bleiben verschieden (keine ID-Zusammenlegung).
- `CONTINUOUS` ohne `SUBDIVISION`: genau eine PARSING-Diagnose `ILI-SYN-0002`, `SOURCE_ERRORS`, kein `failureId`/kein interner Fehler.

**Kommando / Exitcode / beobachteter tatsächlicher Output:**
```text
/Users/stefan/.sdkman/candidates/java/25.0.3-tem/bin/java \
  -cp 'interlis-cli/build/install/interlis-cli/lib/*' \
  build/review/L1-2/probe/PublicProbe.java
Exit 0; Log: build/review/L1-2/probe/public-probe.log
summary checks=62 failures=0
```

**Ergebnis und Schlussfolgerung:** Alle 62 Prüfungen erfolgreich. Die öffentlichen Werte, die Diagnoseklassifikation, die Dimensionsaussage und die Trennung von Umrechnungserlaubnis und Dimensionsbeweis sind am realen Kandidaten belegt. Kein Finding.

### G2 – Skalierte Maxima, gemischte Grenzen, Ganzzahligkeit und effektive Reihenfolge (Node/CLI, neu)

**Hypothese und fachliches Risiko:** Die Periodenformel `max-min+quantum(max)` könnte bei skalierten Maxima, gemischten skalierten/unskalierten Grenzen oder skalierter Ganzzahligkeit falsch rechnen; ein falscher Vorgänger (z. B. eine nicht-Attribut-Rolle) könnte akzeptiert werden; ein geerbter geänderter Vorgänger könnte ungeprüft bleiben.

**Neu erstellter Testinput:** `build/review/L1-2/probe/run-cli-probes2.cjs`, Modelle unter `build/review/L1-2/probe/models/`, jeweils 2.3 und 2.4.

**Erwartetes Verhalten / tatsächliche Beobachtung (16/16):**
- `0.000e1..0.599e2` (0..59.9, Quantum 0.1) mit `h=60[s]`: **Exit 0**.
- `0.000e1..0.598e2` (0..59.8, Quantum 0.1): **Exit 1**, `ILI-SEM-0010`.
- `0.00e1..59.99` (0..59.99) mit `h=60[s]`: **Exit 0**; `0.00e1..59.98`: **Exit 1**.
- Skalierter ganzzahliger Vorgänger `0.00e2..0.23e2` (0..23): **Exit 0**; `0.000e2..0.230e2` (0..23.0, Nachkommapräzision): **Exit 1**.
- Geerbter Vorgänger, neue Unterteilung im Kind: **Exit 0**.
- Geerbte Unterteilung mit überschriebenem, nicht mehr ganzzahligem Vorgänger: **Exit 1**.

**Kommando / Exitcode / beobachteter tatsächlicher Output:**
```text
/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
  build/review/L1-2/probe/run-cli-probes2.cjs
Exit 0; Log: build/review/L1-2/probe/cli-probes2.log
summary { cases: 16, fails: 0 }
```

**Ergebnis und Schlussfolgerung:** Die Periodenformel, die Ganzzahligkeitsprüfung, die effektive Reihenfolge und die geerbte Vorgängerprüfung verhalten sich in beiden Sprachversionen wie dokumentiert. Kein Finding.

**Zwischenschritt, transparent:** Ein erster, breiterer Probeentwurf (`run-cli-probes.cjs`, 21 Fälle) enthielt sechs absichtlich falsch konstruierte Modelle: `0.00e1..0.5999e2` verletzt die bestehende Regel gleicher Nachkommapräzision, und `0e1..23e1` verletzt die bestehende Mantissenform skalierter Grenzen. Beide wurden vom Kandidaten korrekt mit `ILI-SEM-0010` bzw. `ILI-SYN-0002` abgelehnt. Diese sechs Fälle waren **keine** Kandidatenfehler; sie belegen die fortgeltende Präzisions-/Mantissenprüfung. Die korrigierten, gültigen Fälle sind G2.

### G3 – Exponenten-/Arbeitsbudget ohne internen Arithmetikfehler (Java, neu)

**Hypothese und fachliches Risiko:** Ein erhöhtes lexikalisches Exponentenbudget könnte vor der Dezimalausrichtung eine riesige `BigDecimal`-Operation auslösen und als `Failed`/`ILI-INF-0001` statt als kontrollierter `RESOURCE_LIMIT` oder als Quellenfehler enden.

**Neu erstellter Testinput:** `build/review/L1-2/probe/ExponentProbe.java` mit erhöhtem `maxAbsoluteDecimalExponent = Integer.MAX_VALUE` und kleinem semantischem Budget, beide Sprachversionen.

**Erwartetes Verhalten:** Kein `Failed`, kein interner Fehler. **Tatsächlich:**
- Grosse positive Exponenten → **`Cancelled` / `RESOURCE_LIMIT`** in 2.3 und 2.4.
- Negative Riesenezponenten → **`Rejected` / `SOURCE_ERRORS`** (Parser/Mantissen-/Literalprüfung) in 2.3 und 2.4.
- Gemischte Riesenezponenten → **`Rejected` / `SOURCE_ERRORS`**, kein interner Fehler.

**Kommando / Exitcode / beobachteter tatsächlicher Output:**
```text
/Users/stefan/.sdkman/candidates/java/25.0.3-tem/bin/java \
  -cp 'interlis-cli/build/install/interlis-cli/lib/*' \
  build/review/L1-2/probe/ExponentProbe.java
Exit 0; Log: build/review/L1-2/probe/exponent-probe.log
failedOutcomes=0
```

**Ergebnis und Schlussfolgerung:** Der behauptete kontrollierte Umgang mit erhöhtem Exponentenbudget ist bestätigt; es gibt keinen internen Arithmetikabsturz. Kein Finding.

### G4 – TYPE MODEL und Kontextprüfung (Java, neu)

**Hypothese und fachliches Risiko:** Die neue Unterteilungsprüfung könnte nur normale MODEL-Klassen erfassen und TYPE-MODEL-Strukturen ungeprüft lassen.

**Neu erstellter Testinput:** `build/review/L1-2/probe/TypeModelProbe.java` mit `CONTRACTED TYPE MODEL`, gültiger Unterteilung, TEXT-Vorgänger und fehlendem CIRCULAR, beide Versionen.

**Erwartetes Verhalten / tatsächliche Beobachtung:** Gültige TYPE-MODEL-Struktur **Succeeded**; TEXT-Vorgänger und fehlendes CIRCULAR jeweils **Rejected** mit `SOURCE_ERRORS` und `SUBDIVISION_PREDECESSOR_NUMERIC` bzw. `SUBDIVISION_CIRCULAR`. **Exit 0.**

**Ergebnis und Schlussfolgerung:** TYPE-MODEL-Strukturen werden geprüft. Kein Finding.

### G5 – Vererbte Dimensionen (Node/CLI, neu)

**Hypothese und fachliches Risiko:** Zwei Einheiten mit gleicher abstrakter Basis (TIME) dürfen ohne Umrechnungsfaktor als unklar gelten; zwei Einheiten mit TIME- bzw. LENGTH-Abstammung müssen dagegen als bekannt verschieden abgelehnt werden.

**Neu erstellter Testinput:** `build/review/L1-2/probe/run-cli-probes3.cjs`, 2.3/2.4.

**Tatsächliche Beobachtung:** `a EXTENDS TIME`, `b EXTENDS TIME` → **Exit 0**; `a EXTENDS TIME`, `b EXTENDS LENGTH` → **Exit 1** (`ILI-SEM-0010`). **Exit 0 / 6 Fälle, 0 Fehler.**

## Pflichtprüfungen

### Ausgeführter Pflichtlauf

Exakt im Python-gesperrten OS-Umfeld, frisch mit `--rerun-tasks`, im eigenen Worktree:

```text
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
tools/verification/without-python.cjs \
./gradlew checkPortable g2EvidenceTest conformanceCheck sdkProcessCheck pythonFreeCheck \
  --continue --rerun-tasks \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node
```

- **Exitcode: 0**
- **BUILD SUCCESSFUL in 2m 31s**
- **58 actionable tasks: 58 executed**
- `without-python.cjs` ergänzt `--no-daemon`.
- Rohlog: `build/review/L1-2/mandatory-run.log`
- Umgebungsnachweis: `build/reports/python-free/environment.json`
  - `gitHead = 39f48ca239c44b27a81e5562a58c717c90253534`
  - `dirty = ""`
  - `status = passed`, `exitCode = 0`
  - PATH-Proben: `python`, `python3`, `pip`, `pip3`, `pypy`, `uv` → **ENOENT**
  - absolute Interpreter: `/usr/bin/python3`, `/usr/bin/pip3` → **EPERM**

### JUnit-XML-Rohzahlen (frisch, direkt aus `build/test-results`, nicht aus alten Summaries)

| Test-Task | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `:interlis-compiler:test` | 644 | 0 | 0 | 0 |
| `:interlis-cli:test` | 33 | 0 | 0 | 0 |
| `:interlis-lsp:test` | 16 | 0 | 0 | 0 |
| `:interlis-model:test` | 16 | 0 | 0 | 0 |
| `:interlis-projection:test` | 12 | 0 | 0 | 0 |
| `:interlis-repository:test` | 19 | 0 | 0 | 0 |
| `:interlis-wasm-bridge:test` | 3 | 0 | 0 | 0 |
| `:interlis-workspace:test` | 10 | 0 | 0 | 0 |
| **Summe `test`** | **753** | **0** | **0** | **0** |
| `:interlis-compiler:g2AssertionTest` | 15 | 0 | 0 | 0 |
| `:interlis-cli:evidenceTest` | 136 | 0 | 0 | 0 |
| `:interlis-cli:verificationTest` | 15 | 0 | 0 | 0 |
| **Gesamt** | **919** | **0** | **0** | **0** |

Auszug der für L1-2 zentralen Klassen:
- `NumericSubdivisionContractTest`: **13 Tests**, 0 Failures/Errors/Skips.
- `ScaledBoundaryContractTest`: **7 Tests** im vollen `test`-Task, 0 Failures/Errors/Skips; zusätzlich **1** gebundene Methode im `g2AssertionTest`.
- `NormativeConformanceContractTest`: **23 Tests**, 0 Failures/Errors/Skips.
- `CatalogueEvidenceTest`: **55 Tests**, 0 Failures/Errors/Skips.
- `OriginalViewFixtureContractTest`: **1 Test**, 0 Failures/Errors/Skips.

### Portabler Node-/WasmGC-Vertrag

- `interlis-wasm-teavm/build/js-contract-result.json`: `status = passed`, `runtime = teavm-js`, `stabilizationCases = 3365`, Node `v24.22.0`, Zeitstempel `2026-10-29T22:20:58Z`.
- `interlis-wasm-teavm/build/wasm-contract-result.json`: `status = passed`, `runtime = teavm-wasm-gc`, `nodes = 2`, `stabilizationCases = 3365`, Node `v24.22.0`, Zeitstempel `2026-10-29T22:21:02Z`.
- Der neue L1-2-Block in `interlis-wasm-teavm/stabilization-contract.cjs` enthält 28 Basis-Cases + 4 skalierte Grenzprofile + 1 grossen Kürzungsfall je Version, also 33 je Version / 66 insgesamt; beide Laufzeiten führen sie aus.
- **Kein Browser-/Safari-/WebImage-PASS daraus abgeleitet.** Diese Prüfungen sind L1-3 fällig.

### SDK und Pythonfreiheit

- `sdkProcessCheck`: **SDK_PROCESS_PASS 9 commands**; SDK-Beispiel `snap-eac2020f6e6225a81ddc165ca6108efa94c1b77dace2e57e98981a11cf8e4097 models=1`.
- `pythonFreeCheck`: **PYTHON_FREE_SOURCE_PASS 1194 inputs**.
- Keine getrackten `.py`-Dateien oder Python-Aufrufe im geprüften Pfad; der Pflichtlauf lief ausschliesslich unter Node/Java/Gradle.

### Conformance: Fallmenge, Roh- und Projektbilanz

Frischer externer Java-Runner, gepinnter Suite-Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`, Archiv-SHA-256 `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe`:

- Suite-Fälle: **610**; ausgewählt: **593** (2.3/2.4); ausgeschlossen: **17** (Version 1.0).
- **Roh:** 579 PASS / **13 FAIL** / 1 XFAIL / 0 ERROR / 0 XPASS.
- **Projekt:** 579 PASS / **14 XFAIL** / **0 FAIL** / 0 ERROR / 0 XPASS.
- **Referenz:** 579 PASS / 13 FAIL / 1 XFAIL.
- **Assessments:** **19/19 PASS**.
- Roh-Exit des externen Runners: **1** (erwartet wegen der Roh-FAILs); die Projektbewertung bleibt davon getrennt und lautet PASS.
- Rohberichte: `build/conformance/report.json`, `build/conformance/summary.json`, `build/conformance/normative-assessment.json`, `build/conformance/report.junit.xml`, `build/conformance/runner-exit.txt`.
- Der neue Fall `ili23.Attribute23Test.attributeComplete` ist roh **FAIL** (Original wird abgelehnt, Referenz akzeptiert) und projektseitig **XFAIL**.

Neue 19. CIRCULAR-Referenzausnahme, am Kandidaten tatsächlich ausgeführt:
- Original: `test/data/ili23/translation/attributeComplete.ili`, SHA-256 `f42135b02ec698de9bc62059c01d6585bf57e2822605913f46ce30d4145c5443`; Exit **1**, `SOURCE_ERRORS`, `unsupportedFeatureIds=[]`, **genau zwei** `ILI-SEM-0010`/ERROR/SEMANTIC_CHECKING mit `diagnostic.semantics.inheritance`:
  - `attrA6`, Revision `1`, UTF-16 `[368,405)`;
  - `attrB6`, Revision `1`, UTF-16 `[1246,1283)`.
- Positive Kontrolle: `testdata/conformance/normative/ili23.Attribute23Test.attributeComplete.ili`, SHA-256 `a88d5c0874f7ac8656d3d5593a5c296a27bd0c55a2111c6e35e5b8b583c4af39`; Exit **0**, Diagnostics leer.
- Die Kontrollquelle unterscheidet sich vom Original **nur** in den zwei `CIRCULAR`-Ergänzungen bei `attrA6` und `attrB6` (Zeilen 16 und 56); alle übrigen Bytes einschliesslich der Original-Leerzeichen bleiben erhalten.

### Norm-/Suite-/XFAIL-/Baseline-/Quellpin-Unverändertheit

- `gradle/conformance-suite.properties`: SHA-256 `d3c71ada76680b782c9cf4ffbd10eb5b03b4ed8fd57884b62d24eac2efbfa597` in BASE und CANDIDATE identisch; Commit/Archiv/URL unverändert.
- `docs/evidence/conformance/baseline.json`: SHA-256 `33893a959bfeeb5d0ac21d9d1eeb0987b859e5cde0f86f283821429ac6b78a59` identisch; nur der bestehende INTERLIS-1-Known-Failure `ili23.Attribute10Test.attributeComplete` enthalten.
- `testdata/conformance/b1-b3-acceptance/sources.properties`: SHA-256 `564a6dbd7ba9264eb35e378dec5bdc84a204f222dd8f16713c1bfcdcc1e82ab9` identisch.
- `normative-expectations.json`: die **18 bisherigen Case-Objekte sind byte-/strukturgleich**; hinzugefügt wurde genau ein 19. Objekt. Die Zahlen 18→19 in `NormativeConformance` und `CatalogueEvidenceTest` sind reine Vollständigkeitszahlen; keine bestehende Erwartung wurde abgeschwächt.
- `g2-normative-decision-packet`: die **18 bisherigen Fälle unverändert**, genau ein neuer 19. Fall; Status bleibt `PENDING_USER_REVALIDATION`, `pendingDecisions 12→13`, `global D2` nicht abgeschlossen.
- `g2-rule-matrix`: bestehende 18 Queue-Objekte unverändert; neuer 19. Eintrag mit `MISSING_EVIDENCE` / `PENDING_USER_REVALIDATION`; nur Quellhash-/Offsetpflege, keine `CANDIDATE_ONLY`→`PASS`-Umbewertung.
- `g2-source-inventory.json`: nur Quell-/Locator-/Hashpflege (u. a. GraphBuilder-/SyntaxTreeBuilder-Hashes, Spec-Hashes, Zeilen/Offsets); keine fachlichen Normzitate, keine Grammatik-IDs, keine Statusaufwertung. `testMethods 624→638` entspricht 13 neuen `NumericSubdivisionContractTest`-Methoden + 1 neuer `ScaledBoundaryContractTest`-Methode.
- `testdata/g2/predefined-catalogue.json`: ausschliesslich Offsetverschiebungen aus der GraphBuilder-Änderung; keine Status-/Assertionsänderung.
- Normzitate der neuen Ausnahme: `NORM:2.3:2.8.5:p41:007` und `NORM:2.4:3.8.5:p52:007` sind **bytegleich** mit den bereits vorhandenen Einträgen in `g2-norm-inventory.json` (gleiche Quote-Texte und SHA-256); es wurde kein neuer Normtext extrahiert.
- `git diff --check BASE..CANDIDATE`: Exit 2, ausschliesslich Trailing Whitespace in der neuen gepinnten Kontrollquelle. Das ist beabsichtigt: Die Kontrollquelle übernimmt die Original-Leerzeichen, damit sie sich nur durch die zwei CIRCULAR-Ergänzungen unterscheidet. Kein Produkt-/Test-/Build-File betroffen.

### Herleitung der 19. Referenzausnahme (offen geprüft)

- Norm 2.3 §2.8.5/PDF41 und 2.4 §3.8.5/PDF52: Ist ein Attribut als kontinuierliche Unterteilung des Vorgängerattributs definiert, **muss** der Typ als zirkulär definiert sein.
- Die bestätigte Nutzerentscheidung 3 vom 29.10.2026 lautet ausdrücklich: „Bestehende Fälle zulassen, nachweisbare Faktorfehler ablehnen (empfohlen)“ — mit der Bedingung „Numerischer Vorgänger, Ganzzahligkeit, nichtnegative Grenzen und CIRCULAR werden trotzdem geprüft; bekannte numerische Faktoren werden exakt verglichen.“
- Das unveränderte Suite-Original enthält bei `attrA6` und `attrB6` kontinuierliche Unterteilungen ohne `CIRCULAR`. Der Kandidat lehnt es daher normativ korrekt mit genau zwei `ILI-SEM-0010` ab; die Suite akzeptiert es. Die positive Kontrolle ergänzt ausschliesslich die zwei `CIRCULAR`-Marker und besteht.
- `authorizationBasis` im Manifest dokumentiert genau diese Frage/Antwort und behauptet **keine** separate explizite Nutzerfreigabe des Fixture-Namens. Der globale D2-Packet bleibt `PENDING_USER_REVALIDATION`.
- **Reviewerbewertung:** Die Herleitung ist fachlich tragfähig und macht **keine neue Normentscheidung** erforderlich. Die CIRCULAR-Regel ist unmissverständlich und vom Nutzer bestätigt; die 19. strenge Ausnahme ist die transparente, gepinnte Konsequenz. Die individuelle Fixture-Revalidierung bleibt als globale D2-Frage offen und wird hier nicht als erteilt behauptet. Dieser Punkt ist eine dokumentierte Beobachtung, kein Muss-Finding für L1-2.

### Nicht ausgeführte fällige Prüfungen mit Grund

- **Browser-/Safari-/WebImage-Matrix:** für L1-2 **nicht fällig**; sie ist ausdrücklich L1-3 (letzter Phase-1-Lieferabschnitt) vorbehalten. Kein PASS wird daraus abgeleitet.
- **Kumulatives AC1-01–08-/Phasengate:** für L1-2 **nicht fällig**; L1-3.
- **P3/P4/P5/T4:** nicht Gegenstand dieses Auftrags; keine solchen Pflichten erfunden.
- **`releaseCheck`:** für L1-2 nicht fällig und nicht behauptet.
- Alle für L1-2 fälligen Pflichtprüfungen (`checkPortable` inkl. `check`, `g2EvidenceTest`, `conformanceCheck`, `sdkProcessCheck`, `pythonFreeCheck`) wurden tatsächlich ausgeführt.

### Python-Migrations-/Phaseninvarianten soweit relevant

- Kein Python/pip/uv im geprüften Pfad; `pythonFreeCheck` erfolgreich; OS-Proben ENOENT/EPERM.
- Keine getrackten `.py`-Dateien; keine versteckte Interpreterabhängigkeit.
- Phase 1 ist **nicht** abgeschlossen; L1-3 bleibt offen.

### Kumulative Phasenregression

Nicht fällig, da L1-2 nicht die letzte Lieferung der Phase ist. Die vollständige AC1-01–08-Regression und die Safari-/WebImage-Matrix sind im L1-3-Review am dann gültigen Kandidaten-SHA nachzuholen.

## Findings

**Keine BLOCKER- oder MAJOR-Findings.**

Beobachtungen (nicht blockierend):

- **OBS-1 (19. Ausnahme):** Die individuelle Fixture-Freigabe wurde nicht separat vom Nutzer erteilt; das Manifest dokumentiert dies ausdrücklich und stützt die Ausnahme auf die bestätigte CIRCULAR-Regel. Der Reviewer hat die Norm, die Quellenpins und die exakten Diagnosen unabhängig geprüft und akzeptiert die Herleitung. Der globale D2-Packet bleibt `PENDING_USER_REVALIDATION`; keine vorweggenommene globale Normfreigabe.
- **OBS-2 (Diagnosegranularität bei geändertem Vorgänger):** Bei einer geerbten, nur durch einen überschriebenen Vorgänger ungültigen Unterteilung liegt die Diagnose am Benutzerkontext der erweiternden Struktur (`Child`, ganze Deklaration), nicht am einzelnen Attribut. Das entspricht dem dokumentierten Vertrag „Diagnose am Benutzerkontext“; die unveränderte geerbte Beziehung wird exakt am Original deklariert. Eine feinere Attributlokalisierung wäre eine Qualitätsverbesserung, kein Vertragsbruch.
- **OBS-3 (Roh-Exit 1):** Der externe Suite-Runner endet roh mit Exit 1 (13 bekannte Roh-FAILs); die Projektbewertung ist getrennt und PASS. Das ist die dokumentierte Roh-/Projekt-Trennung, kein verstecktes Fehlgrün.
- **OBS-4 (Whitespace):** `git diff --check` meldet ausschliesslich die beabsichtigten Original-Leerzeichen der neuen Kontrollquelle; die Kontrollquelle unterscheidet sich nur durch die zwei CIRCULAR-Ergänzungen. Kein Produkt-/Test-/Build-File betroffen.
- **OBS-5 (Assertion-Bindung):** Die neue `ScaledBoundaryContractTest`-Methode läuft im vollen `:interlis-compiler:test` (7 Tests), ist aber nicht in den `g2AssertionTest`-Selektionslinks aufgenommen; dort läuft weiterhin die bestehende gebundene Methode. Das ist keine Testdeaktivierung: Der volle `check` führt die neue Methode aus, und die 15 Assertion-Tests bleiben grün. Eine Aufnahme in die G2-Assertion-Evidenz kann bei der nächsten mechanischen Inventarpflege erfolgen.

## Urteil

**PASS**

Begründung:
- AC1-03 und AC1-04 sind am Kandidaten-SHA `39f48ca239c44b27a81e5562a58c717c90253534` tatsächlich erfüllt.
- Der frische Pflichtlauf im Python-gesperrten OS-Umfeld endet mit **Exit 0**; **753** reguläre JVM-Tests, **136** Evidenz-, **15** Assertion- und **15** Hosttests, überall **0 Failures/Errors/Skips**; Node-JS/WasmGC je **3365** Fälle; **9** SDK-Prozesskommandos; **1194** Python-Free-Eingaben.
- Die T2-Bilanz ist sauber getrennt: roh **579 PASS / 13 FAIL / 1 XFAIL**, Projekt **579 PASS / 14 XFAIL / 0 FAIL/ERROR/XPASS**, **19/19 Assessments PASS**, 593/610 Fälle.
- Die 18 bisherigen Conformance-Objekte, Suite-/Archiv-/Baseline-/Quellpins und Normzitatpins sind unverändert; die neue 19. CIRCULAR-Ausnahme ist mit Original-/Kontrollhash, exakten Diagnosen und bestätigter Regel belegt.
- Die eigenen, neu erfundenen Gegenproben (62 öffentliche Schema-Checks, 16 CLI-Fälle, 6 Exponentenfälle, 6 TYPE-MODEL-Fälle, 6 Vererbungs-/Dimensionsfälle) sind ausgeführt und bestanden; sie sind keine umbenannten vorhandenen Tests.
- Keine offenen Muss-Findings. Die Beobachtungen OBS-1 bis OBS-5 sind dokumentiert und nicht blockierend.

Nächster konkreter Schritt:
L1-3 (AC1-05–08, vollständige kumulative AC1-01–08-Regression, echte Safari-/Browser-/Web-Image-Matrix) am nächsten Kandidaten-SHA. Vor einem L1-3-PASS keine Phase-1-Freigabe; der globale D2-Packet bleibt bis zur ausdrücklichen Nutzer-Revalidierung offen.
