# Unabhängiger Review: L1-3 (Phase-1-Gate)

Lieferabschnitt / Phase / letztes Phasengate?:
**L1-3 / Phase 1 / JA – dies ist das letzte Gate der Phase 1.** Geprüft wurden daher nicht nur AC1-05–08, sondern alle Kriterien AC1-01–08 kumulativ am selben Kandidaten-SHA. Phase 2 wurde nicht begonnen.

Implementierungsagent: Codex

Prüfagent: OpenCode; tatsächliche Provider-/Modellkonfiguration:
Startkonfiguration gemäss Auftrag: **OpenCode v2.0.28**, `--model deepseek/deepseek-flash`, **DeepSeek DIREKT**, UI **DeepSeek V4.1 Flash 1017**, **kein OpenRouter**. Eine Modellselbstauskunft ist kein Identitätsnachweis; die Prüfung stützt sich auf diese vorgegebene Startkonfiguration und die tatsächlich ausgeführten Kommandos/Artefakte.

BASE_SHA: `50568aa1cb947a088321ce3e17ccf0ea3177b4be`
CANDIDATE_SHA (durch git überprüft): `62c0870a58db677a796143aa8f89578f2e3d7f43`

- `git rev-parse HEAD` im eigenen Review-Worktree: `62c0870a58db677a796143aa8f89578f2e3d7f43`.
- `git status --porcelain` vor und nach der Prüfung: **leer**.
- `git diff --shortstat BASE..CANDIDATE`: **17 Dateien, +1059/−499**.
- `git diff --shortstat b0032e9…(Phase-1-Ausgangsbasis)..CANDIDATE`: **46 Dateien, +3985/−718**.
- L1-3-Produktionsdiff: nur `GraphBuilder.java` (+47/−…), `StructureBindings.java`, `EffectiveMember.java` (Javadoc/Vertrag); keine neue öffentliche Signatur.
- `git diff --check BASE..CANDIDATE`: Exit 0 (sauber). Der kumulative Diff ab Phase-1-Basis meldet abschliessende Leerzeichen ausschliesslich in der bereits in L1-2 hinzugefügten, byte-/hash-gepinnten Conformance-Kontrollquelle `testdata/conformance/normative/ili23.Attribute23Test.attributeComplete.ili` (siehe Beobachtung O2).

Prüfumgebung / Toolchain:
- Eigener sauberer Worktree: `/private/tmp/interlis-java-L1-3-review-62c0870` (detached auf CANDIDATE_SHA).
- macOS arm64; Temurin **25.0.3+9** (`JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem`); Gradle-Wrapper **9.8.1**.
- Node **v24.22.0** (`/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node`); Playwright **1.64.1**; Chromium **156.0.8078.4**, Firefox **157.0**, Playwright-WebKit **27.2**; echtes Safari **27.0 (20625.1.29.18.28)**.
- GraalVM **25.0.3** und Binaryen **133** für Web Image.
- Python-gesperrtes OS-Umfeld über `tools/verification/without-python.cjs`: privater PATH, `sandbox-exec`, geerbte `process-exec`-Sperre, `--no-daemon`; Nachweise `ENOENT` für `python/python3/pip/pip3/pypy/uv` in PATH und `EPERM` für absolute Interpreter. Kein Python/pip/uv durch den Reviewer verwendet.
- Alle Läufe/Artefakte beziehen sich auf `gitHead=62c0870…`, `dirty=""` aus `build/reports/python-free/environment.json`.

## Kriterienprüfung

| Kriterium-ID | Erfüllt / verletzt / nicht prüfbar | Eigener Test-/Code-/CI-Beleg | Finding-ID |
|---|---|---|---|
| **AC1-01** – Vordefinierte Modelle, Domains, Klassen/Strukturen/Units und versionsabhängige Namen öffentlich korrekt gebunden; insbesondere `SurfaceEdge`/`SurfaceBoundary`/`LineGeometry` | **Erfüllt** | Frischer JVM-Lauf: 656 Compiler-Tests, 0 Failures/Errors/Skips; darunter `LanguageFoundationContractTest` 9/9, `PredefinedBindingContractTest` 7/7, `PredefinedNameBindingContractTest` 8/8, `PredefinedOidIdentityContractTest` 6/6. Eigene Probe `predefinedOwner` (2.3/2.4, je 10 Checks): kanonischer `INTERLIS`-Modelowner, Versionsdatum 2005-06-16 bzw. 2014-07-09, Mitgliedschaft, `SurfaceEdge`-Member (`Geometry`, in 2.3 zusätzlich `LineAttrs`), `LineGeometry.Segments`-Element → kanonisches `LineSegment`, Constraint ohne Benutzerquellbereich. Portable/Web-Image-Fälle je 3405 PASS. Nutzerentscheidung A (TYPE MODEL: Strukturen und abstrakte Klassen zulässig, TOPIC/konkrete Klasse/Parameter/Metadatenbehälter verboten) durch `ModelKindContractTest` 5/5 und `LanguageFoundationContractTest` bestätigt. | — |
| **AC1-02** – Lexer/Parser/Name-/Importregeln, Sichtbarkeit/Forward-References/Recovery für 2.3/2.4; Fehler/Implementierungslücke/interner Fehler unterscheidbar | **Erfüllt** | Frischer Compilerlauf 656/0/0/0; u. a. `LexicalRecoveryContractTest` 2/2, `ImportBindingContractTest` 1/1, `ImportDiagnosticContractTest` 1/1, `QualifiedAssociationPathContractTest` 5/5, `DuplicateNameContractTest` 3/3, `ModelHeaderContractTest` 9/9, `CanonicalSourceOrderTest` 1/1. L1-3 ändert keine Grammatik; der relevante neue Quellordnungsfall (finaler importierter Basistyp in beiden kanonischen Reihenfolgen) wird durch `InheritanceFoundationContractTest` und die eigene Probe abgedeckt. Diagnosen tragen `SOURCE_ERRORS`, Originalquelle/Revision/UTF-16-Bereich, kein `failureId`; `INCOMPLETE_SUPPORT`/`INTERNAL_FAILURE` werden nicht verwechselt. | — |
| **AC1-03** – Numerische Unterteilungen, Skalierung, exakte Einheitenrelationen, OID und gemeinsame Typkompatibilität | **Erfüllt** | Frisch: `NumericSubdivisionContractTest` 13/13, `ScaledBoundaryContractTest` 7/7, `NumericConstantBindingContractTest` 4/4, `UnitRelationContractTest` 6/6, `ComposedUnitContractTest` 10/10. Eigene Gegenprobe `numericFactorSeven` (2.3/2.4, je 12 Checks): `coarse=7[INTERLIS.s]`, `fine=1[INTERLIS.s]`, `P:0..23[coarse]`, `V:0..6 CIRCULAR[fine]` wird akzeptiert, effektiver Vorgänger ist P, `V` ist Unterteilung/kontinuierlich/CIRCULAR; `V:0..5` wird mit `SUBDIVISION_FACTOR`/SEM-0010 abgelehnt. T2 schützt die freigegebene CIRCULAR-Ausnahme weiterhin mit Original-/Kontrollhash, exakt zwei SEM-0010 und Positivkontrolle; 19/19 Assessments PASS. | — |
| **AC1-04** – Text, Enum, FORMAT/Zeit, Coordinates/Geometrie, Collections/References und Typ-Queries mit tatsächlichen Werten, Zielidentitäten und Grenzfällen in beiden Versionen | **Erfüllt** | Frischer Gesamtlauf 656 Compiler-Tests ohne Fehler/Skips, darunter `TypeFamilyContractTest`, `TextLiteralContractTest` 5/5, `FormattedTypeContractTest`, `TemporalTypeContractTest`, `GeometryTypeContractTest`, `CollectionTypeContractTest`, `ReferenceRuleTest` 3/3, `CoordinateReferenceSystemContractTest`, `FormatPrecisionOidBindingContractTest` 10/10. Die öffentliche Semantik wird nicht nur über Exitcodes, sondern über portable `symbolAt`/`definition`/Schema-Assertions geprüft (3405 Fälle je TeaVM-JS/WasmGC, Browser und Web Image). Eigene Probe prüft zusätzlich `LineGeometry`/`SurfaceEdge`-Typziele und den effektiven numerischen Vorgänger. | — |
| **AC1-05** – Modell-/Topic-/Klassen-/Strukturvererbung, ABSTRACT/FINAL/EXTENDED, Override und vererbte Eigenschaften fachlich korrekt und öffentlich abfragbar | **Erfüllt** | `InheritanceFoundationContractTest` ist mit **12/12** Tests in der frischen XML (`tests="12" failures="0" errors="0" skipped="0"`) und deckt Besitzerpfade, lokale/geerbte Overrides, finale importierte Klassen/Strukturen in beiden Quellordnungen, finale Attribute, verwaistes EXTENDED, finale Topics, Assoziationsattribute, abstrakte Assoziationsmember, Topic-/Klassen-/Strukturzyklen und Topicklassen-Bindungen ab. Produktionsseitig: `GraphBuilder.pathTo` liefert jetzt Deklarationsbesitzer → Zwischenstufen → effektiver Besitzer je einmal; `isFinalDeclaration` nutzt den gebundenen Deklarationsindex vor dem Symbolaufbau; `validateTopicInheritanceCycles` prüft nach vollständiger Topicbindung mit demselben begrenzten `baseChain`/SEM-0006-Vertrag; `ATTRIBUTE_FINAL` und `EXTENDED_BASE` werden an der lokalen Deklaration gemeldet; unveränderte geerbte Deklarationen werden nicht erneut lokal diagnostiziert. Eigene Probe: vierstufige Klassen-/Assoziationshierarchie mit echten Pfaden, sofortigem `overrides`-Ziel und unveränderten Positionen; finaler importierter Topic und finales importiertes Attribut werden abgelehnt, unverändert geerbte FINAL-Attribute dagegen akzeptiert; Selbst- und Dreifach-Topiczyklen werden als `SOURCE_ERRORS`/ILI-SEM-0006 ohne Schema und ohne interne Ausnahme abgelehnt. | — |
| **AC1-06** – Association/Roles, Restriktionen, HIDING/EXTERNAL/OID/DEPENDS ON, effektive Ziele und Identitäten | **Erfüllt** | Frisch: `RoleTargetInheritanceContractTest` 6/6, `AssociationAccessContractTest` 7/7, `AssociationAttributeContractTest` 1/1, `AssociationRulesContractTest` 2/2, `TopicDependencyContractTest` 10/10, `NoOidReferenceContractTest` 11/11, `FormatPrecisionOidBindingContractTest` 10/10. Eigene Probe: vierstufige Assoziationshierarchie mit tatsächlichem Rollenziel `A2` und Pfad `[Deep, Leaf]`; finales Assoziationsattribut wird mit `ATTRIBUTE_FINAL` abgelehnt (Kontrolle ohne FINAL erfolgreich); verwaistes `EXTENDED` an Assoziationsattribut wird mit `EXTENDED_BASE` abgelehnt; abstraktes Assoziationsattribut wird in einer konkreten dreistufigen Assoziation konkretisiert, sonst `ABSTRACT_MEMBER`. Assoziationsattribute nutzen dieselbe gemeinsame Override-/MANDATORY-/Typprüfung wie Klassen/Strukturen; keine zweite Semantik im Adapter. | — |
| **AC1-07** – Effektive Mitglieder, Herkunft und Transferreihenfolge bleiben bei Import-/Dateireihenfolge und Fresh/Reuse identisch | **Erfüllt** | `InheritanceFoundationContractTest.importedEditsKeepPublicOrderPathsAndQueriesEqualToFreshAnalysis` (beide Sprachen, beide Quellordnungen, vier Revisionen) sowie eigene Probe `importedRevisionReuse` (2.3/2.4, je 48 Checks): drei Revisionen mit Deklarationsumordnung/-umbenennung, Reuse gegen frische Analyse, gleiche gebundene Bedeutung/Reihenfolge/Pfade/Positionen, `definition` auf die aktuelle importierte Quelle/Revision; stabile IDs werden nur innerhalb desselben Snapshots verglichen. `publishedValuesStayFrozen` (je 5 Checks) bestätigt, dass veröffentlichte `EffectiveMember`-Listen und Pfade durch Reuse nicht mutiert werden; `inheritancePath`/`origin`/`overridden` sind unveränderliche öffentliche Werte. View-Verträge wurden nicht geändert (`ViewBuilder` nicht im L1-3-Diff); `ViewBaseExtensionContractTest` und `ViewInheritanceTranslationContractTest` 10/10 bleiben grün. | — |
| **AC1-08** – Vollständige T1-/T2-/T3-Phasenregression und erneute Bewertung AC1-01–07 am neuen SHA; unabhängige Phase-1-Entscheidung | **Erfüllt** | Der vorgeschriebene Gesamtlauf wurde **vollständig frisch** ausgeführt (siehe Pflichtprüfungen): Exit 0, 71/71 Tasks, `BUILD SUCCESSFUL in 4m 20s`. 765 reguläre JVM-Tests, 136 Evidenz-, 15 Assertion-, 15 Hosttests, überall 0 Failures/Errors/Skips; T2 roh 579 PASS/13 FAIL/1 XFAIL, Projekt 579 PASS/14 XFAIL, 19/19 Assessments; TeaVM-JS/WasmGC, Chromium/Firefox/WebKit, echtes Safari und Web Image je 3405 PASS; SDK 9 Prozesskommandos, SDK-Paket, LSP-Distribution/-Prozesstests; Pythonfreiheit 1197 Eingaben. Eigene Gegenprobe 436 Checks PASS am Kandidaten und 14 tatsächliche rote Gegenbefunde am BASE_SHA. Alle AC1-01–07 wurden am selben SHA erneut bewertet. | — |

## Eigene Gegenprobe

**Hypothese und fachliches Risiko:**
Die L1-3-Korrekturen könnten nur die vorhandenen Tests befrieden, aber öffentliche Werte weiterhin falsch liefern: (a) Besitzerpfade könnten Zwischenstufen auslassen, Besitzer verdoppeln oder die deklarierende statt der effektiven Kette zeigen; (b) FINAL könnte bei importierten Basen von der Quellenordnung abhängen oder unveränderte geerbte Deklarationen fälschlich ablehnen; (c) Assoziationsattribute könnten FINAL/EXTENDED/abstrakte Member anders behandeln als Klassen-/Strukturattribute; (d) Topiczyklen könnten weiterhin ein Schema oder eine interne Ausnahme erzeugen; (e) Fresh/Reuse könnte veröffentlichte Werte mutieren oder eine falsche importierte Definition liefern; (f) die kumulativen numerischen/predefined Verträge könnten durch die L1-3-Änderungen regressieren.

**Neu erstellter Testinput / exakter Pfad:**
`build/review/L1-3/probe/InheritanceCumulativeProbe.java` (nur im ignorierten `build/` des Review-Worktrees; kein Produkt-/Test-/Buildfile geändert). Die Quelle wurde unabhängig gegen die öffentliche Compiler-API geschrieben; keine Produktmethode wurde umbenannt oder kopiert. Geprüft werden u. a. eine vierstufige Klassen- und Assoziationshierarchie, eine dreistufige abstrakte Assoziation, importierte FINAL-Topics/-Attribute in beiden Quellordnungen, Selbst-/Dreifach-Topiczyklen, unveränderte Reuse-Werte, drei importierte Revisionen mit `definition`-Ziel, ein 7:1-Faktor und der versionsgebundene Predefined-Owner.

**Erwartetes Verhalten:**
- `inheritancePath` genau vom ausgewählten Deklarationsbesitzer über jede Zwischenstufe zum effektiven Besitzer, je einmal; lokale Overrides `OVERRIDDEN`, geerbte Overrides `INHERITED` mit `overridden=true`; `AttributeSymbol.overrides` zeigt auf die unmittelbare Basiseigenschaft.
- Finale importierte Topics/Attribute und final überschriebene Attribute werden mit `SOURCE_ERRORS`/SEM-0010 abgelehnt; unverändert geerbte FINAL-Attribute bleiben gültig; verwaistes `EXTENDED` wird abgelehnt.
- Assoziationen verwenden dieselben MANDATORY-/Typ-/FINAL-/EXTENDED-Regeln; konkrete Assoziationen konkretisieren abstrakte Attribute.
- Topiczyklen enden in `SOURCE_ERRORS` mit ILI-SEM-0006, ohne Schema und ohne interne Ausnahme.
- Fresh/Reuse liefert gleiche gebundene Bedeutung und aktuelle importierte Definitionsziele; veröffentlichte alte Werte bleiben unverändert.
- 7:1-Unterteilung wird akzeptiert, 6:1-Periode abgelehnt; Predefined-Owner/Member bleiben versionsrichtig.

**Kommando / Exitcode / beobachteter tatsächlicher Output:**
```text
/Users/stefan/.sdkman/candidates/java/25.0.3-tem/bin/java \
  -cp 'interlis-cli/build/install/interlis-cli/lib/*' \
  build/review/L1-3/probe/InheritanceCumulativeProbe.java
```
Am Kandidaten: **Exit 0**, Log `build/review/L1-3/probe/probe.log`:
```text
... 22 SECTION_PASS-Zeilen (11 Abschnitte × 2 Sprachversionen) ...
REVIEW_PROBE_PASS checks=436
```
Am BASE_SHA `50568aa1…` wurde dieselbe Probe in einem separaten temporären Worktree gegen dort frisch gebaute `:interlis-cli:installDist` ausgeführt: **Exit 1**, `REVIEW_PROBE_FAIL failures=14`, darunter:
```text
class4 path [sym-7, sym-5, sym-3, sym-9]
assoc4 path [sym-14, sym-11, sym-7, sym-17]
assocFinal 2.3 unexpectedly succeeded
assocFinal 2.4 unexpectedly succeeded
importedFinalTopic 2.3 true unexpectedly succeeded
importedFinalTopic 2.4 true unexpectedly succeeded
selfCycle 2.3 unexpectedly succeeded
selfCycle 2.4 unexpectedly succeeded
```
(`importedRevisionReuse` bestand auch am BASE_SHA; es ist eine Regression-/Konsistenzprüfung, kein neuer Fix. Die übrigen 7 Abschnitte pro Sprache zeigten den Vorherfehler.)

**Ergebnis und Schlussfolgerung:**
Die Probe widerlegt die Risikobehauptungen am Kandidaten nicht: 436 Checks PASS. Gleichzeitig zeigt derselbe Test am BASE_SHA 14 tatsächliche Fehlschläge in genau den korrigierten Bereichen (Pfade, Assoziations-FINAL, importierter FINAL-Topic, Topic-Selbstzyklus). Damit ist die Gegenprobe nicht nur eine Wiederholung der Produktmethoden, sondern reproduziert den Vorherfehler unabhängig. Kein Finding.

## Pflichtprüfungen

**Frischer Pflichtlauf (genau der vorgegebene Umfang, ohne Python):**
```text
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
tools/verification/without-python.cjs \
./gradlew checkPortable checkBrowser :interlis-wasm-webimage:webImageProbe \
  g2EvidenceTest conformanceCheck sdkProcessCheck sdkPackage \
  :interlis-lsp:installDist pythonFreeCheck --continue --rerun-tasks \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
  -PgraalVmHome=/Users/stefan/.sdkman/candidates/java/25.0.3-graal \
  -PbinaryenHome=/Users/stefan/.cache/interlis-java/runtime/binaryen-version_133
```
**Exit 0**, `BUILD SUCCESSFUL in 4m 20s`, **71 actionable tasks: 71 executed**. Laufzeitfenster laut `environment.json`: `startedAt 2026-10-30T00:05:21Z`, `finishedAt 2026-10-30T00:09:42Z`, `gitHead 62c0870…`, `dirty ""`.

**JUnit-XML (frisch, selbst geparst; nicht nur Zusammenfassungs-JSON):**

| Suite | Dateien | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|---:|
| `interlis-compiler:test` | 126 | **656** | 0 | 0 | 0 |
| `interlis-cli:test` | 3 | 33 | 0 | 0 | 0 |
| `interlis-lsp:test` (inkl. `LspProcessTest`) | 5 | 16 | 0 | 0 | 0 |
| `interlis-model:test` | 6 | 16 | 0 | 0 | 0 |
| `interlis-projection:test` | 3 | 12 | 0 | 0 | 0 |
| `interlis-repository:test` | 6 | 19 | 0 | 0 | 0 |
| `interlis-wasm-bridge:test` | 1 | 3 | 0 | 0 | 0 |
| `interlis-workspace:test` | 2 | 10 | 0 | 0 | 0 |
| **reguläre JVM-Tests gesamt** | | **765** | **0** | **0** | **0** |
| `interlis-cli:evidenceTest` | 4 | 136 | 0 | 0 | 0 |
| `interlis-compiler:g2AssertionTest` | 11 | 15 | 0 | 0 | 0 |
| `interlis-cli:verificationTest` | 5 | 15 | 0 | 0 | 0 |

Die XMLs tragen Zeitstempel nach `startedAt`; `@Disabled`/`Assumptions`/`skipped` wurden nicht gefunden. Die 12 L1-3-Methoden sind in `TEST-guru.interlis.compiler.InheritanceFoundationContractTest.xml` vollständig enthalten (`tests="12" skipped="0" failures="0" errors="0"`).

**Portable/Node (kein Browserersatz):**
- `interlis-wasm-teavm/build/js-contract-result.json`: `status=passed`, `stabilizationCases=3405`, `runtime=teavm-js`, Node v24.22.0.
- `interlis-wasm-teavm/build/wasm-contract-result.json`: `status=passed`, `stabilizationCases=3405`, `runtime=teavm-wasm-gc`, Node v24.22.0.
- Web Image Node-Probe `interlis-wasm-webimage/build/webimage/result.json`: `status=ok`, `stabilizationCases=3405`, `runtime=graalvm-web-image`, `outcome=succeeded`.

**Echte Browser / Safari / Web Image (getrennt ausgewertet):**
- `interlis-wasm-teavm/build/browser-results.json`: Chromium 156.0.8078.4, Firefox 157.0, Playwright-WebKit 27.2, Playwright 1.64.1 – je **3405** PASS, inkl. `exactDecimal=true`, Worker-Neustart, akzeptiertem/abgelehntem Compile und Diagrammadapter (`diagramNodes=2`).
- `interlis-wasm-teavm/build/safari-result.json`: **echtes Safari 27.0 (20625.1.29.18.28)** über `safaridriver`, `status=passed`, `stabilizationCases=3405`, `exactDecimal=true`, `workerRestartOutcome=succeeded`, leerer `driverStderr`. Keine Gleichsetzung mit WebKit.
- `interlis-wasm-webimage/build/webimage-browser/browser-results.json`: Chromium/Firefox/WebKit im Web-Image-Build je **3405** PASS, `runtime=graalvm-web-image`.
- Unabhängige Diagrammadapter-Prüfung: `DIAGRAM_ADAPTER_PASS n-ary=2`.

**T2 – Conformance (gepinnt, frisch, Roh und Projekt getrennt):**
- Suite: Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`, SHA-256 `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe`; Filter `^(ili23|ili24)\.`; Fallmenge **610**, ausgewählt **593**, ausgeschlossen **17** (nur Version `1.0`, INTERLIS-1-Profil).
- Externer Runner: `runner-exit.txt=1` (erwartet bei rohen FAILs), Rohbilanz `summary.json`: **579 PASS / 13 FAIL / 1 XFAIL / 0 XPASS / 0 ERROR / 593**, `overallResult=FAIL`.
- Projektspezifische Bewertung `normative-assessment.json`: **579 PASS / 14 XFAIL / 0 FAIL / 0 XPASS / 0 ERROR / 593**, `overallResult=PASS`, **19/19 Assessments PASS**, `assessmentCaseIds` vollständig.
- `build/reports/conformance/conformance-summary.json` bindet dieselbe Suite, denselben Filter, dieselbe Auswahl, denselben Exit und dieselbe Projektbilanz. Die 19 Assessments sind gegenüber `50568aa1…` unverändert; die CIRCULAR-Ausnahme `ili23.Attribute23Test.attributeComplete` enthält Original-/Kontrollhash, `authorizationBasis`, exakt zwei SEM-0010 mit Severity/Phase/Argumenten/UTF-16-Bereichen und eine erfolgreiche Positivkontrolle.

**SDK/CLI/LSP:**
- `sdkProcessCheck`: `SDK_PROCESS_PASS 9 commands` (Version, Capabilities, gültiger/ungültiger Check, Format, u. a.); `build/reports/sdk/sdk-process-comparison.json` mit Exitcodes und SHA-256 der Ausgaben.
- `sdkPackage`: `build/releases/interlis-java-sdk-0.2.0-SNAPSHOT.zip` (ca. 16.9 MB) und `build/reports/sdk/version-manifest.json` erzeugt.
- `:interlis-lsp:installDist` gebaut; LSP-Tests 16/16 inkl. `LspProcessTest` und Protokoll-/Cancellation-Tests.

**Python-/Phaseninvarianten:**
- `git ls-files '*.py'`: **0**; `pythonFreeCheck`/`PYTHON_FREE_SOURCE_PASS 1197 inputs`; `source-check.json status=passed`.
- `environment.json`: privater PATH, geerbte OS-`process-exec`-Sperre, `python/python3/pip/pip3/pypy/uv` → `ENOENT`, absolute `/usr/bin/python3`, `/usr/bin/pip3` → `EPERM`; `--no-daemon` im tatsächlichen Befehl.
- Der Reviewer verwendete kein Python/pip/uv; nur Java, den gepinnten Node, git, shasum und POSIX-Werkzeuge.

**Norm-/Suite-/XFAIL-/Baseline-/Quellpin-Unverändertheit:**
- `git diff BASE..CANDIDATE -- docs/evidence/conformance gradle/conformance-suite.properties`: **leer**; Suite-Commit/-SHA, Filter und Baseline unverändert. `baseline.json` enthält weiterhin genau einen bekannten INTERLIS-1-Fall (`Attribute10Test.attributeComplete`).
- `normative-expectations.json`: 19 Fälle, unverändert gegenüber BASE; alle Original-/Kontrollquellen-Hashes werden im Assessment erneut geprüft.
- Inventarpflege in L1-3 ist mechanisch: `g2-source-inventory.json` und `predefined-catalogue.json` ändern ausschliesslich Quelloffsets/`line`-Werte sowie die daraus folgenden Datei-/Locator-Hashes; keine Normzitate, Statusbedeutungen oder Anforderungstexte. `testMethods` 638→650 entspricht den 650 Compiler-`@Test`-Methoden (die `validation`-Kandidatenzahl 615 bleibt unverändert). `g2EvidenceTest` (136 Tests) bestätigt die Anker-/Hash-Bindungen; die in `g2-rule-matrix.json` eingetragenen SHA-256 von Inventar und Kandidatenindex stimmen mit den Dateien überein.
- Keine CANDIDATE_ONLY-Zeile wurde als vollständige G2-Abnahme umetikettiert.

**Kumulative Phasenregression, wenn fällig:**
- Phase-1-Produktion ab `b0032e9…` umfasst 46 Dateien (+3985/−718); L1-3 selbst ändert nur die oben genannten Vererbungsstellen. Die L1-1-/L1-2-Familien (`LanguageFoundationContractTest`, `NumericSubdivisionContractTest`, `ScaledBoundaryContractTest`, Predefined-/Unit-/Typ-/Formatted-/Geometry-/Collection-/Reference-Suiten) liefen frisch und vollständig. Die früheren L1-1-/L1-2-PASS-Berichte wurden nur als Historie gelesen, nicht als Ersatz für diesen Lauf.
- Alle vier ausdrücklichen Nutzerentscheidungen bleiben wirksam: A (TYPE MODEL), B (skalierte Grenzen), C (breitere Konstanten-/Einheitenprofile), D (CONTINUOUS SUBDIVISION ohne bestimmbaren Faktor zulassen, nachweisbare Faktorfehler ablehnen) sowie E/Phase 0b (2.3 OID AS/BASKET OID AS ohne CONTRACTED, Metadatenprofil). Die zugehörigen Tests/Profile sind unverändert und frisch grün.

**Nicht ausgeführte fällige Prüfungen:**
Keine fällige Phase-1-Prüfung wurde ausgelassen. `releaseCheck`, P3-/P4-/P5-, T4-/Corpus-/Benchmark-Pflichten sind für dieses Gate nicht vorgesehen und wurden nicht als Pflicht erfunden. `:interlis-wasm-teavm:disasmWasmGC` und die experimentellen Emscripten-Tasks sind keine Prüfpfade dieses Gates (SKIPPED im offiziellen Lauf).

## Findings

**Keine offenen Muss-Findings.**

**Beobachtungen ohne Finding:**
- **O1 (erwartete Rohbilanz):** Die rohe T2-Bilanz enthält 13 FAIL + 1 XFAIL; sie wird erst durch die 19 separat geprüften Projekt-Assessments zu 14 XFAIL/0 FAIL. Genau diese Trennung ist verlangt; kein Fehlgrün.
- **O2 (gepinnte Kontrollquelle):** `git diff --check b0032e9..CANDIDATE` meldet abschliessende Leerzeichen in `testdata/conformance/normative/ili23.Attribute23Test.attributeComplete.ili`. Die Datei ist eine byte-/hash-gepinnte Kontrollquelle (SHA-256 `a88d5c08…`), die in L1-2 bewusst unverändert übernommen wurde; eine Bereinigung würde den Pin brechen. Der L1-3-Commit selbst ist `git diff --check`-sauber. Kein Handlungsbedarf.
- **O3 (Parallelität):** `org.gradle.parallel=true` führt im offiziellen Gesamtlauf zu verschachtelter Task-Ausgabe. Die für `runConformanceSuite` verwendete CLI-Distribution war jedoch vor dem Conformance-Start fertig (Installations-Mtime `01:05:59` = `run-start.txt`; `installDist` ist harte Abhängigkeit), und es wurde kein externer/konkurrierender Neubau gestartet. Der Lauf enthält keinen `NoClassDefFoundError`; die Assessment-Wiederholungen mit dem CLI bestanden. Kein Finding.
- **O4 (Berichtsformulierung):** Die Formulierung „Kandidatenmethodenzahl 638→650“ im Implementierungsbericht meint die Gesamtzahl der Compiler-`@Test`-Methoden; die separate Kandidatenzahl in `validation.testMethods` bleibt bei 615. Die Inventarpflege ist konsistent, die Formulierung nur unscharf. Kein Kriterienverstoss.

## Urteil

**PASS**

Begründung:
- Alle acht Kriterien **AC1-01 bis AC1-08** sind am exakten Kandidaten-SHA `62c0870a58db677a796143aa8f89578f2e3d7f43` einzeln belegt.
- Der vollständige fällige Phase-1-Pflichtlauf ist frisch, ohne Python, mit echtem Safari und Web Image erfolgreich: Exit 0, 71/71 Tasks, 765 JVM-Tests + 136 Evidenz- + 15 Assertion- + 15 Hosttests ohne Failures/Errors/Skips; portable Node, Chromium/Firefox/WebKit/Safari und Web Image je 3405 PASS; T2-Projekt 579 PASS/14 XFAIL mit 19/19 Assessments; SDK/LSP/Python-Freiheit belegt.
- Die eigene, unabhängig geschriebene Gegenprobe besteht am Kandidaten mit 436 Checks und reproduziert am BASE_SHA mit 14 Fehlschlägen die korrigierten Vorherfehler (Pfade, Assoziations-FINAL, importierter FINAL-Topic, Topic-Selbstzyklus). Keine umbenannte Produktmethode.
- Kein offenes Muss-Finding, keine unberechtigte Norm-/Baseline-/XFAIL-/Suite-Änderung, keine Pflichtprüfung ausgelassen.
- Die früheren L1-1-/L1-2-PASS-Berichte wurden nur als Historie behandelt; die kumulative Bewertung erfolgte vollständig neu auf dem aktuellen SHA.

Nächster konkreter Schritt: **Phase 1 ist damit unabhängig abgenommen.** Phase 2 (L2-1) darf gemäss `docs/g2/EXECUTION_PLAN.md` erst nach diesem PASS beginnen und ist nicht Teil dieses Auftrags. Der globale D2-Status bleibt `PENDING_USER_REVALIDATION`; dieses PASS ist keine globale G2-/P3-/P4-/P5-Freigabe.
