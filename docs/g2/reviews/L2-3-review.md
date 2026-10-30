# Unabhängiger Review: L2-3 (kumulatives Phase-2-Gate)

Lieferabschnitt / Phase / letztes Phasengate?: **L2-3 / Phase 2 / ja, letztes Phasengate**
Implementierungsagent: Codex
Prüfagent: OpenCode; tatsächliche Provider-/Modellkonfiguration: **OpenCode v2.0.28, Provider `deepseek`, Modell `deepseek/deepseek-flash` (Anzeigename DeepSeek V4.1 Flash 1017), DeepSeek direkt; kein OpenRouter**
BASE_SHA (L2-3): `ca8ff6958926c441b62994da00089d7c5fe02470`
PHASE_BASE_SHA (kumulativ): `81ad01919c45165ad20ecb86986ff5f0475cc44c`
CANDIDATE_SHA (durch git überprüft): `d358e3c5e26b4db646124648f9b876ffa0d8b90b`
HEAD vor/nach der Prüfung: identisch `d358e3c5e26b4db646124648f9b876ffa0d8b90b`; `git status --porcelain` vor und nach der Prüfung leer. Der unveränderte Bericht liegt ausserhalb des Produktkandidaten unter `/Users/stefan/sources/interlis-java/build/review/L2-3/review.md`.

Prüfumgebung / Toolchain: macOS 15.8.1 arm64; Java 25.0.3+9-LTS (Temurin), Gradle 9.8.1, Node.js v24.22.0, GraalVM 25.0.3+9.1, Binaryen 133; Playwright 1.64.1; echter Safari 27.0 (20625.1.29.18.28). Eigener sauberer Worktree `/private/tmp/interlis-java-L2-3-review-d358e3c`. `npm ci` war im vorbereiteten Worktree ausgeführt (Log `npm-ci.log`, 0 Vulnerabilities); die Browsercaches waren vorhanden. Gelesen wurden `AGENTS.md`, Plan/Reviewprompt, die Lieferberichte L2-1/L2-2/L2-3, die betroffenen Spezifikationsänderungen sowie der relevante vollständige Produktions-/Testdiff ab `ca8ff695...` und ab `81ad0191...`.

## Kriterienprüfung

| Kriterium-ID | Erfüllt / verletzt / nicht prüfbar | Eigener Test-/Code-/CI-Beleg | Finding-ID |
|---|---|---|---|
| **AC2-01** | **Erfüllt** | Eigene neue Gegenprobe: importierte geerbte Assoziation, Sichtbasis bleibt `Base.T.Link`, 2.4-Rollen `a`/`b` werden auf die aktuellen lokalen Teilnehmer `Local.A`/`Local.B` kontextualisiert; 22 eigene Prüfungen, 0 Fehler. Zusätzlich `DependentFunctionArgumentsContractTest` 12/12 (u. a. UNDEFINED-/ANYCLASS-Symbolbesitzer und abhängige Rückgabetypen), `ConstraintPathBindingContractTest` 13/13 (u. a. Indizes, LIST-Auswahl, Koordinatenachsen, Objektmengen), `QualifiedAssociationPathContractTest` 5/5, `AssociationAccessContractTest` 7/7, `NamedCollectionElementContractTest` 4/4, `RoleTargetInheritanceContractTest` 6/6, alle 0 Failures/Errors/Skips. Codeprüfung: `FunctionBindings.arguments` bindet tatsächliche Argumente in Quellreihenfolge, memoisiert/iterativ und ohne zweiten Binder; Indizes, Pfade und Rollen bleiben typisiert. | — |
| **AC2-02** | **Erfüllt** | `BuiltinSignatureFoundationContractTest` 2/2: echte Signaturen von 13 Builtins in 2.3 und 21 in 2.4, geordnete Argumente, keine MANDATORY-Parameter/Rückgaben, falsche Argumentanzahl als `ILI-SEM-0005` ohne internen Fehler. `SetConditionContextContractTest` 9/9: SET/WHERE-Kontext, individuelle Objektwerte nur im WHERE, 2.4-`OBJECTS`-Voraussetzung, 2.3-konstante SET-Bedingung, externe Constraints, keine Fehlerkaskaden. `ConstraintPathBindingContractTest` 13/13 (`UNIQUE`, `EXISTENCE`, externe Constraints), `ExistenceValueFamiliesContractTest` 8/8, `UniqueConstraintContractTest` 1/1, `ExternalConstraintContractTest` 1/1, `ConstraintNameContractTest` 5/5; keine externen Funktionen oder Grafik-/Metadatenwerte ausgeführt. | — |
| **AC2-03** | **Erfüllt** | `ViewFoundationContractTest` 9/9: Projection/Selection/Join/Union/Aggregation, drei View-Vererbungsebenen, AREA-/UNION-`ALL OF`-Verbot auch über abgeleitete Sicht, zulässige SURFACE-Inspektion, 2.4-Rollenkopie in Quellreihenfolge, einzelner Linkteilnehmer bleibt einzelnes Objekt, Rollenorigin/-pfad und Teilnehmernavigation. Ergänzend `ViewContractTest` 2/2, `ViewFormationSemanticsContractTest` 3/3, `ViewDependencyContractTest` 1/1, `ViewBaseExtensionContractTest` 1/1, `ViewInheritanceTranslationContractTest` 10/10. Eigene Gegenprobe deckt importierte geerbte Basis und lokales Shadowing ab. | — |
| **AC2-04** | **Erfüllt** | `ViewFoundationContractTest` prüft effektive Mitglieder, Positionen, `EffectiveMember.inheritancePath`, Herkunft, Typidentität, Override-Kette, Join-Nullability, Union-Reihenfolge und nicht transferierte `AGGREGATES`; 9/9 Methoden. `OriginalViewReuseTest`/`ViewInheritanceTranslationContractTest` prüfen Fresh/Reuse und unveränderte veröffentlichte Altgraphen. Der eigene Probe prüft die tatsächliche öffentliche Typbasis der kopierten Rollen, nicht nur den Erfolg der Syntaxanalyse. | — |
| **AC2-05** | **Erfüllt** | `Phase2ClosureContractTest.translatedAssociationAllOfRolesKeepOrderedCorrespondencesAcrossImportsAndRevisions` 1/1, dazu fünf weitere L2-3-Methoden; `ImportedTranslationContractTest` 12/12, `StructuredTranslationContractTest` 11/11, `GeometryTranslationContextContractTest` 7/7, `ViewInheritanceTranslationContractTest` 10/10, `TranslationMetadataContractTest` 1/1. Geprüft: vier getrennte Original-/Translations-/Verbrauchermodelle, konkurrierende gleichnamige Workspace-Symbole, Import-/Dateireihenfolge, geordnete Rollen-ALL-OF-Korrespondenzen, Originalziele, falsches übersetztes Rollenziel, SourceErrors, Erholung und Fresh/Reuse. Eigene Gegenprobe: vertauschte Achsenreferenzen in der Translation werden abgelehnt und als Translation-Problem diagnostiziert. | — |
| **AC2-06** | **Erfüllt** | `Phase2ClosureContractTest` 6/6: LineCoord-Quellabstraktion auch in verschachtelten Strukturen, gültige abstrakte/externe Parameter, tatsächliche Koordinatentypen im Inspektionsresultat, transitive übersetzte Metadaten-/Linienform-/Generic-Closures, Achsen/Objekte/Strukturen/Alternativen/Quellidentitäten, falsche gleich geformte Vertexdomain, getrennte gleichnamige Generic-Domains mit `DEFERRED`, geordneten Alternativen und Fehler-/Erholungsfolge. Dazu `MetadataBindingContractTest` 9/9, `LineFormBindingContractTest` 7/7, `GenericContextContractTest` 11/11, `GraphicBindingContractTest` 10/10, `GraphicResponsibilityContractTest` 12/12, `GeometryInspectionContractTest` 6/6. Die areAreas2/3-Profile (N1), N2 und N3 sind über die bestehenden Tests erneut ausgeführt. Eigene Gegenprobe: nicht abstraktes LineCoord-Feld in einer verschachtelten Struktur wird am Kandidaten mit `NUMERIC_ABSTRACT`/`ILI-SEM-0010` abgelehnt; am L2-3-BASE wird es noch akzeptiert. | — |
| **AC2-07** | **Erfüllt** | Frischer vollständiger Pflichtlauf am Kandidaten `d358e3c`: **Exit 0, 4m53s, 71/71 Tasks ausgeführt** (`--rerun-tasks`, kein `-x test`), inklusive `checkPortable`, `checkBrowser` (echtes Safari), Web-Image-Node-Probe und Web-Image-Browsermatrix, `g2EvidenceTest`, `conformanceCheck`, `sdkProcessCheck`, `sdkPackage`, `:interlis-lsp:installDist`, `pythonFreeCheck`. JUnit-XML: **970 Fälle**, davon **803 reguläre JVM** (694 Compiler), 137 Evidenz, 15 Assertion, 15 Host; **0 Failures/Errors/Skips**. Portable JS und WasmGC je **3487 PASS**; Web Image Node **3487**; Web Image Chromium/Firefox/WebKit je **3487**; TeaVM Chromium/Firefox/WebKit und echtes Safari je **3487**. T2: 593/610, roh **577 PASS/15 FAIL/1 XFAIL**, Projekt **577 PASS/16 XFAIL/0 FAIL/ERROR/XPASS**, **21/21** Assessments. SDK **9** Kommandos, SDK-Paket und LSP-Distribution erstellt, Pythonfreiheit **1213** Eingaben mit ENOENT/EPERM. Keine fällige Phase-2-Prüfung ausgelassen. | — |

## Eigene Gegenprobe

**Hypothese und fachliches Risiko:** Die L2-3-Behauptungen könnten zu breit oder zu eng sein: (a) die neue gemeinsame lexikalische Topic-Mitgliedersuche könnte eine importierte geerbte Assoziation zwar lokal, aber nicht über einen Import auflösen oder die aktuellen Topic-Teilnehmer nicht kontextualisieren; (b) die Entfernung der pauschalen LineCoord-Ausnahme könnte entweder weiterhin nicht abstrakte Quellfelder akzeptieren oder legitime abstrakte Strukturen/Parameter überverwerfen. Zusätzlich wurde die Übersetzungsprüfung mit vertauschten Achsenidentitäten konfrontiert.

**Neu erstellter Testinput / exakter Pfad:** `/private/tmp/interlis-java-L2-3-review-d358e3c/build/review-l2-3/probe/Phase2ReviewProbe.java` (ausschliesslich im ignorierten `build/`-Baum; keine Produktquelle geändert). Die Probe verwendet die öffentliche Compiler-API und `CompilerTestSupport` und prüft unter anderem:

```text
INTERLIS 2.4; CONTRACTED MODEL Base ... TOPIC T = OID AS INTERLIS.UUIDOID;
  CLASS A = flag:BOOLEAN; END A; CLASS B = name:TEXT; END B;
  ASSOCIATION Link = a -- {1} A; b -- {*} B; label:TEXT; END Link; END T; END Base.

CONTRACTED MODEL F ... IMPORTS Base;
  TOPIC Local EXTENDS Base.T =
    CLASS A(EXTENDED) = local:TEXT; END A;
    CLASS B(EXTENDED) = other:BOOLEAN; END B;
    VIEW V PROJECTION OF src~Link; = ALL OF src; END V;
  END Local; END F.
```

sowie eine abstrakte verschachtelte LineCoord-Struktur (`Inner(ABSTRACT)=p(ABSTRACT):INTERLIS.LineCoord; S(ABSTRACT) EXTENDS INTERLIS.LineSegment=inner:Inner;`) und dieselbe Quelle mit nicht abstraktem `Inner`/`p`. Die Translationsprobe verwendet zwei 2.4-Modelle mit `REFSYSTEM BASKET`/`Library.World[1]`/`[2]` und vertauscht in der Übersetzung die Achsenreferenzen.

**Erwartetes Verhalten:** Importierte geerbte Assoziation wird in beiden Sprachen aufgelöst; die Sichtbasis behält `Base.T.Link`, die Rollenkopien in 2.4 sind einzelne Teilnehmer und zeigen auf `Local.A`/`Local.B`; abstrakte verschachtelte LineCoord-Strukturen und kanonische LineCoord-Parameter bleiben gültig; ein nicht abstraktes Feld wird mit `NUMERIC_ABSTRACT`/`ILI-SEM-0010` abgelehnt; vertauschte Achsen werden als Translation-Referenzfehler abgelehnt.

**Kommando / Exitcode / beobachteter tatsächlicher Output:** Kompiliert mit Temurin 25 und dem Kandidaten-Testclasspath; ausgeführt mit `java -cp ... guru.interlis.compiler.Phase2ReviewProbe`:

```text
REVIEW_PROBE checks=22 failures=0
CANDIDATE_EXIT:0
```

Dieselbe Probe gegen den sauberen L2-3-BASE `ca8ff6958926c441b62994da00089d7c5fe02470` (separater temporärer Worktree, nur `:interlis-compiler:testClasses`, keine konkurrierende Distribution während der Conformance) ergab:

```text
REVIEW_PROBE checks=10 failures=4
FAIL: imported inherited association must compile (2.3): ... ILI-SEM-0001 ... Link ... src
FAIL: imported inherited association must compile (2.4): ... ILI-SEM-0001 ... Link ... src
FAIL: non-abstract attribute through nested non-abstract LineCoord rejected (2.3): Succeeded
FAIL: non-abstract attribute through nested non-abstract LineCoord rejected (2.4): Succeeded
BASE_EXIT:1
```

**Ergebnis und Schlussfolgerung:** Am Kandidaten sind alle 22 Probeannahmen erfüllt; am BASE reproduziert dieselbe Probe vier echte Vorherfehler (unaufgelöste geerbte Assoziation und akzeptierte nicht abstrakte LineCoord-Felder). Die L2-3-Korrekturen sind damit nicht nur durch die neuen Produkttests, sondern durch eine unabhängige, fachlich andere Gegenprobe bestätigt. Es gibt kein Gegenfinding.

## Pflichtprüfungen

**Ausgeführte Kommandos, Exitcodes, Umfang:** Der verlangte vollständige Lauf wurde mit dem OS-Sperrwrapper und exakt dem vorgegebenen Task-Satz ausgeführt (der Wrapper ergänzt beobachtbar `--no-daemon`):

```text
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
tools/verification/without-python.cjs ./gradlew checkPortable checkBrowser \
:interlis-wasm-webimage:webImageProbe g2EvidenceTest conformanceCheck \
sdkProcessCheck sdkPackage :interlis-lsp:installDist pythonFreeCheck \
--continue --rerun-tasks \
-PnodeExecutable=.../node -PgraalVmHome=.../25.0.3-graal \
-PbinaryenHome=.../binaryen-version_133
```

Ergebnis: **Exit 0, 4m53s, 71 actionable tasks: 71 executed** (`build/review-l2-3/gate.log`); BUILD SUCCESSFUL. Es wurde kein `-x test`, kein `--rerun-tasks`-Ersatz und kein alter Report verwendet.

**JUnit-XML (frisch):** Alle 177 XML-Dateien wurden nach dem Start des Laufs geschrieben. Aggregat:

| Suite | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| `interlis-compiler/test` | 694 | 0 | 0 | 0 |
| `interlis-cli/test` | 33 | 0 | 0 | 0 |
| `interlis-model/test` | 16 | 0 | 0 | 0 |
| `interlis-lsp/test` | 16 | 0 | 0 | 0 |
| `interlis-projection/test` | 12 | 0 | 0 | 0 |
| `interlis-repository/test` | 19 | 0 | 0 | 0 |
| `interlis-wasm-bridge/test` | 3 | 0 | 0 | 0 |
| `interlis-workspace/test` | 10 | 0 | 0 | 0 |
| **reguläre JVM gesamt** | **803** | **0** | **0** | **0** |
| `interlis-cli/evidenceTest` | 137 | 0 | 0 | 0 |
| `interlis-compiler/g2AssertionTest` | 15 | 0 | 0 | 0 |
| `interlis-cli/verificationTest` | 15 | 0 | 0 | 0 |
| **Gesamt** | **970** | **0** | **0** | **0** |

Neu am Kandidaten: `Phase2ClosureContractTest` 6/6, `ViewFoundationContractTest` 9/9, `DependentFunctionArgumentsContractTest` 12/12, `BuiltinSignatureFoundationContractTest` 2/2, `SetConditionContextContractTest` 9/9, `GeometryInspectionContractTest` 6/6; jeweils `failures="0" errors="0" skipped="0"` und frische XML-Zeitstempel. Die 694 XML-Fälle des Compilers entsprechen **688 `@Test`-Methoden** (sechs zusätzliche Fälle durch parametrisierte Methoden); die Dokumentation nennt korrekt 688 Methoden.

**Rohreports / Bindung an CANDIDATE_SHA:** `build/reports/python-free/environment.json` enthält `gitHead: d358e3c5e26b4db646124648f9b876ffa0d8b90b`, `dirty: ""` und `status: passed`. JUnit-XML, `build/conformance/*`, `build/reports/sdk/*`, `build/releases/interlis-java-sdk-0.2.0-SNAPSHOT.zip`, `interlis-wasm-teavm/build/*result*.json` und `interlis-wasm-webimage/build/*` stammen alle aus diesem Lauf. Der SDK-Zip enthält 22 Archiveinträge (CLI, Model, Compiler, Version-Manifest, SDK-Beispiel); SHA-256 `668fbbedd4fbd6aada783ae546df10df4e065a8f8d714eb02ef577981d3ed051`.

**Portable / Browser / Web Image:**

- TeaVM-JS: `stabilizationCases: 3487`, `status: passed`, `runtime: teavm-js`, Node v24.22.0.
- TeaVM-WasmGC: `stabilizationCases: 3487`, `status: passed`, `runtime: teavm-wasm-gc`, Node v24.22.0.
- Web Image Node: `stabilizationCases: 3487`, `status: ok`, `runtime: graalvm-web-image`, Node v24.22.0.
- TeaVM-Browsermatrix: Chromium **156.0.8078.4**, Firefox **157.0**, WebKit **27.2**, Playwright **1.64.1**, jeweils `status: passed` und 3487 Fälle; zusätzlich **echtes Safari** `status: passed`, `driverVersion: Included with Safari 27.0 (20625.1.29.18.28)`, 3487 Fälle, inklusive `analyzeRevision=1`, Ablehnung, Compile, Diagrammadapter, exaktem Dezimalwert und Worker-Neustart.
- Web-Image-Browsermatrix: Chromium/Firefox/WebKit jeweils 3487 Fälle, `runtime: graalvm-web-image`, `passed`.
- Web-Image-Build mit GraalVM-Web-Image/Binaryen 133 erfolgreich; der Node-Probe- und der Browserpfad sind getrennt belegt.

**T2-Conformance (roh und Projekt getrennt):** Auswahl **593/610** aus dem gepinnten Suite-Lauf (`ili23: 553`, `ili24: 40`). Rohreport: `tests="593" failures="15" errors="0" skipped="1"`, `runner-exit.txt` = **1**, Zusammenfassung **577 PASS / 15 FAIL / 1 XFAIL / 0 XPASS / 0 ERROR**. Projektbewertung: `overallResult: PASS`, **577 PASS / 16 XFAIL / 0 FAIL / 0 ERROR / 0 XPASS**, **21/21** strenge Assessments PASS. Alle 15 rohen FAIL-IDs sind in den 21 Assessments enthalten; sechs positive Kontroll-Assessments kommen zusätzlich aus Nicht-FAIL-Fällen. Die Trennung roh/projektiv bleibt sichtbar.

**Norm-/Suite-/XFAIL-/Baseline-/Quellpin-Unverändertheit:** Suite-Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`, Archiv-SHA-256 `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe` selbst nachgerechnet. `gradle/conformance-suite.properties`, `docs/evidence/conformance/baseline.json`, der Runner und die Originalquellen sind gegenüber dem Phase-Base unverändert. Das 19-Fälle-Archiv hat SHA-256 `67244a8138bbe2c17e0feaecd7c08885be981904cb88d33cfaad980123a2e6c1`; seine 19 Fälle sind objektgleich mit den ersten 19 der aktuellen 21 Fälle. Nur die zwei ausdrücklich vom Nutzerprofil N3 gedeckten SET-Fälle wurden in L2-1 ergänzt; L2-3 änderte keine Normerwartung, kein Golden, keine Originalsuite, keinen XFAIL und keine Baseline. `g2-rule-matrix.json` bleibt bei `CANDIDATE_ONLY`; 688 Compiler-Testmethoden, 15 `PredefinedOrigin`-Produzenten und 19 Profilcalls sind bestätigt. Die zwei fachlich geänderten `GeometryInspectionContractTest`-Methoden und ihre portablen Gegenstücke enthalten nun explizit abstrakte `S`-/`Data`-/Attribut-/`SegmentEndPoint`-Deklarationen; der Diff ändert nur diese Quellstrings, nicht die Assertions zu Typen, Constraints, Override, kanonischer Identität und den zwei getrennten Vertexkontexten. Die alten nicht abstrakten Feldformen bleiben als negative Quellen in den neuen L2-3-Tests repräsentiert.

**Python-Migrations-/Phaseninvarianten:** `git ls-files '*.py'` = **0**. `source-check.json`: **1213** geprüfte Eingaben, `status: passed`. `environment.json`: private PATH liefert für `python`, `python3`, `pip`, `pip3`, `pypy`, `uv` jeweils **ENOENT**; absolute `/usr/bin/python3` und `/usr/bin/pip3` liefern **EPERM**; Exit 0. Damit ist die Pythonfreiheit des gesamten Pflichtlaufs belegt, nicht nur per Grep. Die Differenz 1213 gegenüber den 1212 des Implementierungsberichts ist der nachträglich hinzugekommene Lieferbericht und wird als solche ausgewiesen.

**Kumulative Phasenregression:** Der Lauf enthält `check`/`checkPortable`, die vollständige Portable-Matrix, die Browsermatrix mit echtem Safari, Web Image, Evidenz-/Assertion-/Hosttests, T2 und Pythonfreiheit am selben SHA. Sämtliche AC2-01–AC2-06 wurden erneut bewertet; der frühere L2-1-/L2-2-PASS wurde nicht als Ersatz verwendet.

**Nicht ausgeführte fällige Prüfungen mit Grund:** Keine. `releaseCheck`, T4-/Corpus-/Benchmark-/P3-/P4-/P5-Pflichten sind für dieses Phase-2-Gate nicht erforderlich und wurden nicht erfunden. Die experimentellen Emscripten-/`disasm`-Tasks wurden von Gradle als `SKIPPED` geführt; sie sind laut Auftrag keine Pflichtprüfungen. Kein Gate-Test wurde übersprungen. Nach dem Gate wurde nur der separate BASE-Vergleichsprobe-Build in einem temporären Worktree ausgeführt; er konkurrierte nicht mit der Conformance.

## Findings

**Keine offenen Muss-Findings, keine Blocker, keine Major-Findings.**

Nicht blockierende Beobachtungen:

1. Die kopierten 2.4-ALL-OF-Rollen sind konservativ nicht `mandatory`; die Normstelle `NORM:2.4:3.15:p91:005` verlangt die Übernahme von Attributen und Rollen, nicht eine zusätzliche MANDATORY-Markierung. Das ist mit der bereits akzeptierten L2-2-Beobachtung konsistent und kein neues Muss-Finding.
2. `pythonFreeCheck` meldet 1213 statt 1212 Eingaben, weil der nachträglich gespeicherte Lieferbericht selbst ein scanbares Input ist; der Status bleibt `passed`, und der Lauf enthält keine Python-Ausführung.
3. Die XML-Ausführung des Compilers zählt 694 Fälle bei 688 `@Test`-Methoden; die sechs zusätzlichen Fälle sind parametrisierte Methoden. Das ist kein verlorener Test und kein Skip.

## Urteil

**PASS**

Begründung: AC2-01 bis AC2-07 sind am exakten Kandidaten `d358e3c5e26b4db646124648f9b876ffa0d8b90b` jeweils mit eigenem tatsächlichem Beleg erfüllt. Der vollständige frische Pflichtlauf am Kandidaten bestand mit Exit 0, 71/71 Tasks, 970 JUnit-Fällen ohne Failures/Errors/Skips, 3487 portablen Fällen je Backend, vollständiger Browser-/Safari-Matrix, Web Image, SDK, LSP, T2 mit getrennter Roh-/Projektbilanz und 21/21 Assessments sowie belegter Pythonfreiheit. Die unabhängige Gegenprobe bestand am Kandidaten mit 22/22 Prüfungen und reproduzierte am L2-3-BASE vier echte Vorherfehler; damit sind die beiden risikotragenden L2-3-Korrekturen unabhängig bestätigt. Es gibt keine offenen Muss-Findings. Der Review gibt ausdrücklich **kein** globales G2-PASS, keine P3-/P4-/P5-Freigabe und keine Freigabe der weiterhin `PENDING_USER_REVALIDATION` befindlichen globalen D2-Paketentscheidung; Phase 3 wurde nicht begonnen.

Nächster konkreter Schritt: Status/Evidenz für L2-3 als unabhängig geprüft dokumentieren und danach gemäss Auftrag die Phase-3-Entscheidung des Nutzers abwarten; keine Implementierungsarbeiten aus diesem Review ableiten.
