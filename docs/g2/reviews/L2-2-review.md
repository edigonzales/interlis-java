# Unabhängiger Review: L2-2

Lieferabschnitt / Phase / letztes Phasengate?: **L2-2, Phase 2, kein Phasengate** (L2-3 ist das Phasengate).
Implementierungsagent: Codex
Prüfagent: OpenCode; tatsächliche Konfiguration: **OpenCode CLI `v2.0.28`**, Startprozess
`opencode run --standalone --model deepseek/deepseek-flash --agent build --format json --auto`.
Provider-Konfiguration ohne OpenRouter-Eintrag (`disabled_providers: []`); DeepSeek direkt
gemäss Startbefehl. Die Modellselbstauskunft wurde nicht als Identitätsbeleg verwendet.
BASE_SHA: `dda2a5aa9c780b2248f3b692af222a562a1cda7b`
CANDIDATE_SHA (durch git überprüft): `30ef129ad648ec5d8522ed36226fec31f420d84a`
Prüfumgebung / Toolchain: eigener sauberer Worktree
`/private/tmp/interlis-java-L2-2-review-30ef129`; `git rev-parse HEAD` = CANDIDATE_SHA,
`git status --porcelain` leer, BASE ist direkter Vorfahr (1 Commit Abstand). macOS 15.8.1
arm64, Java 25.0.3 (Temurin 25.0.3+9-LTS), Gradle 9.8.1 (Wrapper, SHA-256 gepinnt),
Node v24.22.0. `npm ci` war im Worktree bereits erfolgt.

## Kriterienprüfung

| Kriterium-ID | Erfüllt / verletzt / nicht prüfbar | Eigener Test-/Code-/CI-Beleg | Finding-ID |
|---|---|---|---|
| **AC2-03** – Projection/Selection/Join/Union/Aggregation, abgeleitete Sichten, Inspektionen und `ALL OF` entsprechen der Norm und den tatsächlichen Bindungsregeln. | **erfüllt** | Eingefrorene Normtexte selbst geprüft: `NORM:2.3:2.15:p69:009` nennt nur Attribute; `NORM:2.4:3.15:p91:005` nennt Attribute **und Rollen** und verbietet `ALL OF` bei Vereinigungen und AREA-Inspektionen. Beide `quoteSha256` stimmen mit dem tatsächlichen Zitat überein. Produktdiff: `ViewBuilder` verbietet `ALL OF` auch bei geerbter AREA-/UNION-Formation und kopiert in 2.4 effektive deklarierte Rollen; `GraphBuilder` setzt die Navigation an tatsächlichen Rollenzielen fort. `ViewFoundationContractTest` 9/9, `ViewBindingContractTest` 12/12, `ViewInheritanceTranslationContractTest` 10/10, alle 0 Failures/Errors/Skips. Eigene Gegenprobe: zweistufig geerbte AREA- und UNION-`ALL OF` werden in 2.3 und 2.4 mit `SOURCE_ERRORS`/`ILI-SEM-0010`/`VIEW_ALL_OF` abgelehnt; geerbte SURFACE-Inspektion bleibt gültig; direkte AREA-Sperre bleibt; 2.3 veröffentlicht Rollen nicht und lehnt den abgeleiteten Zugriff ab. | – |
| **AC2-04** – Viewresultat enthält korrekte effektive Mitglieder, Typen, Zielidentitäten und Reihenfolgen; erfolgreiche Syntaxanalyse allein genügt nicht. | **erfüllt** | `ViewFoundationContractTest` prüft öffentlich: effektive Mitglieder/Positionen/Vererbungspfade/Override-Typidentität, Projection/Selection/Join/Union/Aggregation, nullable Joins, geordnete Unionableitungen, nicht transferiertes `AGGREGATES`, verschachtelte Strukturinspektion mit unmittelbarem `PARENT`, Rollenherkunft/-ziele, anschliessende Navigation, Importrevisionen B→C→B in beiden Dateireihenfolgen, Fresh/Reuse sowie UTF-16-Definitionsquery (CRLF/Emoji, Revision 9). XML: 9 Tests, 0 Failures/Errors/Skips. Eigene Gegenprobe prüft zusätzlich die effektive Reihenfolge `[a, b, label, note]` einer erweiterten Assoziation, `DerivedOrigin(originalRole, ALIAS)`, `ObjectArgumentType.objects()==false` bei Rollenkardinalität `{*}`, tatsächliche Basis-/Rollziele, `RoleStep` auf die Originalrolle und die Navigation `src->a->flag` bzw. `src->b->name` bis zum kontextualisierten `Local.A.flag`/`B.name`. | – |

**Diffprüfung:** `git diff --stat BASE..CANDIDATE` = 14 Dateien, +450/−70. Produktcode nur
`GraphBuilder.java` (+11) und `ViewBuilder.java` (+24); neu `ViewFoundationContractTest.java`
(199 Zeilen); portabler Vertrag `stabilization-contract.cjs` (+24/−1). Die übrigen Änderungen
sind Dokumentation/Spec sowie mechanisch nachgeführte Quellenlocator-/Hash-/Testzahlen.
Keine Build-, Gradle-, Wrapper- oder Conformance-Suite-/Manifest-/XFAIL-/Baseline-Datei
geändert; die Evidenz-Matrix, das Source-Inventory und der Test-Candidate-Index wurden
ausschliesslich mechanisch (Hashes, Offsets, `testMethods`) nachgeführt. `git diff
--diff-filter=D` ist leer, bestehende Tests wurden weder gelöscht noch verändert.

## Eigene Gegenprobe

**Hypothese und fachliches Risiko:** Die Behauptung könnte an drei Stellen falsch sein:
(a) das AREA-/UNION-`ALL OF`-Verbot greift nur eine Ableitungsebene tief; (b) 2.4 verliert
bei geerbten Assoziationsrollen Reihenfolge, Herkunft oder Zielidentität oder macht aus einem
Rollenwert fälschlich eine Objektmenge; (c) nach einem kopierten Rollenwert setzt der Pfad
nicht am tatsächlichen (ggf. kontextualisierten) Ziel fort, während 2.3 die Rolle unzulässig
veröffentlichen könnte.

**Neu erstellter Testinput / exakter Pfad:** vollständig neu geschriebene Java-Gegenprobe
unter dem ignorierten Kandidaten-Build:
`build/review/L2-2/counterprobe/src/guru/interlis/compiler/ReviewerCounterProbe.java`
(SHA-256 `d4de147217c96bc6e350221a7616659030f40aa2c286bddf030181329bce9925`), kompiliert
gegen den Test-Runtime-Classpath des Compiler-Moduls. Sie ist kein umbenannter vorhandener
Test; die Quellen und Assertions wurden für diese Prüfung neu formuliert. Enthalten sind:
zweistufig geerbte AREA-/UNION-/SURFACE-Fälle in 2.3/2.4; eine `ASSOCIATION Narrow EXTENDS Link`
mit geerbten Rollen `a`, `b` und geerbtem Attribut `label` plus lokalem `note`; eine
`TOPIC Local EXTENDS Base` mit `CLASS A (EXTENDED)` und einer lokalen Assoziation, deren Rollen
explizit auf `Base.A`/`Base.B` zeigen, damit die Kontextualisierung tatsächlich durchlaufen wird.

**Erwartetes Verhalten:** zweistufig geerbte AREA-/UNION-`ALL OF` werden abgelehnt, geerbte
SURFACE-Inspektionen bleiben gültig; 2.3 kopiert nur Attribute und lehnt den Rollenzugriff ab;
2.4 kopiert die effektiven Rollen in Reihenfolge mit Originalrollen-Herkunft und einem
einzelnen Teilnehmerziel, und die anschliessende Navigation erreicht `Local.A.flag` bzw.
`B.name`.

**Kommando / Exitcode / beobachteter tatsächlicher Output:**

```sh
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
  ./gradlew -q -I build/review/L2-2/counterprobe/init.gradle \
  :interlis-compiler:reviewerPrintTestClasspath
/Users/stefan/.sdkman/candidates/java/25.0.3-tem/bin/javac \
  -cp "<test-runtime-classpath>" -d build/review/L2-2/counterprobe/classes \
  build/review/L2-2/counterprobe/src/guru/interlis/compiler/ReviewerCounterProbe.java
/Users/stefan/.sdkman/candidates/java/25.0.3-tem/bin/java \
  -cp "build/review/L2-2/counterprobe/classes:<test-runtime-classpath>" \
  guru.interlis.compiler.ReviewerCounterProbe
```

Exitcode **0**. Auszug aus `build/review/L2-2/counterprobe/counterprobe.log`
(SHA-256 `d4ca7a65dbe4ed4bcba67d9121f82f434ca40cf18867636c0feba20ab6002390`):

```text
PASS 2.3 two-level inherited AREA ALL OF carries ILI-SEM-0010/VIEW_ALL_OF
PASS 2.3 two-level inherited SURFACE ALL OF succeeds
PASS 2.3 two-level inherited UNION ALL OF carries ILI-SEM-0010/VIEW_ALL_OF
PASS 2.3 ALL OF publishes attributes only, inherited then local: [label, note]
PASS 2.3 navigation via unpublished role is rejected
PASS 2.4 ALL OF publishes effective association members in order: [a, b, label, note]
PASS 2.4 role copy a uses ALIAS origin
PASS 2.4 role copy a is one participant, not an object set
PASS 2.4 role copy a targets A but was A
PASS 2.4 role copy a path points at the original role
PASS 2.4 role copy b is one participant, not an object set
PASS 2.4 navigation flagValue has type BooleanType
PASS 2.4 navigation flagValue binds flag on A
PASS 2.4 navigation nameValue has type TextType
PASS 2.4 navigation nameValue binds name on B
PASS 2.4 copied role a is contextually specialized to Local.A
REVIEWER_L2_2_CHECKS=76 FAILURES=0
```

**Ergebnis und Schlussfolgerung:** Die konkreten Behauptungen hielten der Gegenprobe stand;
keine Widerlegung. Zwei frühere Entwürfe der Gegenprobe verwendeten absichtlich konstruierte,
aber fachlich unzulässige Modelle (neue Rolle an einer erweiterten Assoziation; Projektion
einer geerbten Assoziation im abgeleiteten Topic) und wurden verworfen, nicht als
Produktfinding gewertet; der oben dokumentierte finale Lauf ist der massgebliche.

## Pflichtprüfungen

**Exakt ausgeführtes Kommando (vollständig frisch, `--rerun-tasks`, keine alten Reports):**

```sh
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
tools/verification/without-python.cjs ./gradlew checkPortable g2EvidenceTest \
conformanceCheck sdkProcessCheck pythonFreeCheck --continue --rerun-tasks \
-PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node
```

- **Exitcode 0**, `BUILD SUCCESSFUL in 2m 34s`, **58 actionable tasks: 58 executed**
  (`build/review/L2-2/reviewer-logs/pflichtlauf.log`, SHA-256
  `9aef836ad86afbc726391caeb1c8ab96a13dfaae713b40e9fcbeca7355e645ee`). Keine
  `UP-TO-DATE`-/`FROM-CACHE`-Wiederverwendung; `--rerun-tasks` wirksam.
- **JUnit-XML-Frische:** Laufstart `2026-10-30T08:16:07.888Z`; alle **176** XML-Dateien
  liegen zwischen `2026-10-30T08:16:17.820Z` und `2026-10-30T08:16:47.111Z`. Keine
  XML-Datei ist älter als der Lauf.
- **Bindung an den Kandidaten:** Der gesamte Lauf erfolgte im sauberen Worktree mit
  `HEAD` = CANDIDATE_SHA; `build/reports/python-free/environment.json` trägt
  `gitHead: 30ef129ad648ec5d8522ed36226fec31f420d84a` und `dirty: ""`. Die übrigen
  Rohreports tragen keinen eigenen SHA, sind aber über den eindeutigen Worktree/HEAD,
  die unter `--rerun-tasks` neu erzeugten Zeitstempel und die im Lauf erzeugten
  CLI-/Portable-Artefakte gebunden; es wurden keine Fremd- oder Altberichte übernommen.
- **JUnit-Zahlen (echte Klassen/Methoden aus XML, nicht nur Summary-JSON):**
  - `test`: **797 Tests**, 0 Failures, 0 Errors, 0 Skips; davon
    `interlis-compiler` **688 Tests** in 130 Testklassen.
  - `evidenceTest`: **137 Tests**, 0/0/0 (`AssertionEvidenceTest` 25,
    `CatalogueEvidenceTest` 56, `NormEvidenceTest` 31, `RuntimeReportsTest` 25).
  - `g2AssertionTest`: **15 Tests**, 0/0/0.
  - `verificationTest`: **15 Tests**, 0/0/0.
  - `ViewFoundationContractTest`: `testsuite name="guru.interlis.compiler.ViewFoundationContractTest"`
    **9 Tests**, 0 Failures/Errors/Skips; die neun `testcase@name` wurden einzeln geprüft
    (u. a. `inheritedAreaInspectionsCannotBypassTheAllOfProhibition`,
    `allOfIn24CopiesAssociationRolesInOrderAndPreservesSingleParticipantTargets`,
    `copiedRoleValuesRemainNavigableAcrossDerivedViews`,
    `importedAllOfRolesFollowSourceRevisionsWithoutMutatingPublishedViews`).
- **Portable/JS/WasmGC:** `interlis-wasm-teavm/build/js-contract-result.json` und
  `wasm-contract-result.json` jeweils `status: passed`, `runtime: teavm-js` bzw.
  `teavm-wasm-gc`, `stabilizationCases: 3471`, Node `v24.22.0`. Die zwölf neuen
  Quellen-/Queryfälle sind im portablen Vertrag enthalten (6 je Version); der Zähler
  3471 entspricht 3459 aus L2-1 + 12. Kein Browsernachweis behauptet.
- **T2-Conformance (roh und Projekt getrennt):** Auswahl **593/610** (`ili23`/`ili24`,
  Filter `^(ili23|ili24)\.`); roh **577 PASS / 15 FAIL / 1 XFAIL / 0 XPASS / 0 ERROR**;
  Projektbewertung **577 PASS / 16 XFAIL / 0 FAIL / 0 ERROR / 0 XPASS**; **21/21**
  strenge Assessments `PASS`. JUnit-Rohreport: `tests="593" failures="15" errors="0"
  skipped="1"` (XFAIL als Skip ausgewiesen). `overallResult` der Projektbewertung
  `PASS` bei Roh-`FAIL`/`runner-exit.txt` = 1; die Trennung bleibt sichtbar.
  Suite-Pin `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`, SHA-256
  `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe`.
- **SDK:** `build/reports/sdk/sdk-process-comparison.json` `status: passed`, **9**
  Prozesskommandos (`version`, `version --json`, `capabilities`, zwei `check`, drei
  `format`, ungültige Option) mit erwarteten Exitcodes.
- **Pythonfreiheit:** `build/reports/python-free/source-check.json` `passed`, **1210**
  Eingaben; `environment.json` belegt PATH-Proben `ENOENT` für `python`, `python3`,
  `pip`, `pip3`, `pypy`, `uv` und `EPERM` für absolute `/usr/bin/python3`,
  `/usr/bin/pip3`, bei `exitCode: 0`. Im gesamten Pflichtlauf wurde kein Python/pip/uv
  zur Evidenzerzeugung verwendet.
- **Norm-/Suite-/XFAIL-/Baseline-/Quellpin-Unverändertheit:** `g2-norm-inventory.json`
  ist nicht im Diff; die beiden zitierten Normstellen und ihre `quoteSha256` wurden
  unabhängig nachgerechnet. Unter `docs/evidence/conformance`, `testdata/conformance`
  und den Conformance-Suite-/Runner-/Manifestpfaden ist **keine** Datei geändert. Das historische
  19-Fälle-Bytearchiv und die 21 Assessments bleiben gepinnt; `CANDIDATE_ONLY` bleibt
  `CANDIDATE_ONLY` (`testMethods` 682, `conditions` 621, `requirements` 80,
  `withCandidate` 669). `g2-rule-matrix.json`-Hashes für Source-Inventory und
  Test-Candidate-Index stimmen mit den tatsächlichen Dateien überein; die **15**
  `PredefinedOrigin`-Produzenten und **19** Profilcalls (`profileCallSites`) sind
  gegenüber BASE unverändert (nur Quellzeilen-Offsets). Alle **197** Source-Anchors des
  Predefined-Katalogs wurden gegen die tatsächlichen Quellen nachgerechnet: 0
  Abweichungen. **682** Compiler-`@Test`-Methoden (XML-Ausführung 688 Fälle, u. a.
  drei parametrisierte Methoden).
- **Regressionsprofile N1/N2/N3 und Nachbarregeln:** vollständige Suite statt
  Einzelauswahl; u. a. `SetConditionContextContractTest` 9, `AreaFunctionContractTest` 7,
  `FunctionContextContractTest` 3, `RoleTargetInheritanceContractTest` 6,
  `MetadataBindingContractTest` 9, `ScaledBoundaryContractTest` 7,
  `NumericConstantBindingContractTest` 4, `ConformanceRulesContractTest` 6 – alle
  0 Failures/Errors/Skips. Die zwei SET-Assessments `ili23.Constraint23Test.setCstrt`
  und `ili23.InterlisConversion23Test.iliconversion_ConversionOfSETCONSTRAINT` sind in
  den 21 `PASS`-Assessments enthalten.
- **Nicht ausgeführte fällige Prüfungen:** keine für L2-2. `checkBrowser`, echte
  Safari-Matrix und Web Image sind ausdrücklich erst für das L2-3-Phasengate fällig;
  diese Lieferung behauptet keinen Browserlauf. Kein `P3`/`P4`/`P5`/`T4`-Gate
  erfunden. Da L2-2 kein Phasengate ist, ist keine kumulative Phasenabnahme fällig;
  Global D2 bleibt getrennt `PENDING_USER_REVALIDATION`.

## Findings

Keine offenen Muss-Findings, keine Blocker, keine Major-Findings.

**Nicht blockierende Beobachtungen (ohne Abzug, ausserhalb des L2-2-Mussumfangs):**

1. Eine Sicht in einem abgeleiteten Topic konnte in meiner Probe eine **geerbte
   Assoziation** nicht direkt als Projektionsbasis auflösen (`ILI-SEM-0001`,
   `Link` unresolved). Das ist durch den L2-2-Diff nicht berührt (View-Basisauflösung
   unverändert) und gehört nicht zu AC2-03/04; für die Kontextualisierungsprobe wurde
   deshalb eine lokale Assoziation mit explizit auf `Base.A`/`Base.B` qualifizierten
   Rollenzielen verwendet. Keine Nacharbeit in L2-2.
2. Kopierte 2.4-Rollen sind konservativ nicht `mandatory`, auch bei nicht-nullable Basis.
   Die eingefrorene Normstelle definiert für kopierte Rollen kein Attribut-`MANDATORY`;
   die Übergabe verlangt ausdrücklich, dass optionale Joins keine Pflichtwerte
   garantieren. Kein Normverstoss in diesem Umfang.

## Urteil

**PASS**

Begründung: Beide Muss-Kriterien **AC2-03** und **AC2-04** sind am exakten
CANDIDATE_SHA `30ef129ad648ec5d8522ed36226fec31f420d84a` durch eigene Codeprüfung,
frische Pflichtausführung und eine neue, tatsächlich ausgeführte fachliche Gegenprobe
belegt. Alle fälligen Pflichtprüfungen wurden mit `--rerun-tasks` vollständig
ausgeführt; JUnit-XML, Portable-, SDK-, Conformance- und Pythonfreiheits-Rohberichte
wurden selbst geprüft. Keine offenen Muss-Findings. Kein Phasengate; **kein globales
G2-PASS**, Global D2 bleibt `PENDING_USER_REVALIDATION`. Nächster konkreter Schritt:
L2-3 (Translation/Generics/Metadaten) mit kumulativer AC2-Prüfung, Browsermatrix,
echtem Safari, Web Image und CLI/SDK/LSP.

Der vollständige Bericht wurde unverändert unter
`/Users/stefan/sources/interlis-java/build/review/L2-2/review.md` abgelegt. Der
Kandidatenworktree blieb unverändert (`git status --porcelain` leer); es gab keinen
Push, Commit oder Release. Die Gegenprobe liegt ausschliesslich im ignorierten
`build/`-Baum.
