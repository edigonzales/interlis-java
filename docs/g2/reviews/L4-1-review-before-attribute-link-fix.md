# Unabhängiger Review: L4-1 (R2)

Lieferabschnitt / Phase / letztes Phasengate?: **L4-1 / Phase 4 / nein** (das Phasengate ist L4-2, AC4-04–07).  
Implementierungsagent: Codex.  
Prüfagent: OpenCode CLI **v2.0.28**; tatsächliche Provider-/Modellkonfiguration: **provider `deepseek`, model `deepseek-flash`** (DeepSeek direkt, kein OpenRouter). Beleg: Startkommando des äusseren Launchers `... --model deepseek/deepseek-flash --agent build ...` in `build/reports/python-free/environment.json`; Modellselbstauskunft ist kein kryptografischer Identitätsnachweis.  
BASE_SHA: `4a84068624cffde4549186c53cf23b9aaad7b179`  
CANDIDATE_SHA (durch `git rev-parse HEAD` verifiziert): `b804c47f6a51110039556931b3cb4cee2a256be0`  
Worktree: `/private/tmp/interlis-java-L4-1-review-b804c47-nopy` (sauber; `git status --porcelain` leer; eigene Proben ausschliesslich unter ignoriertem `build/review/`).  
Bericht: `/Users/stefan/sources/interlis-java/build/review/L4-1-r3/review.md` (ausserhalb des Kandidaten).

## Prüfumgebung / Toolchain

| Werkzeug | Tatsächlich verwendet |
|---|---|
| Java | Temurin **25.0.3+9-LTS** (`JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem`) |
| Node | **v24.22.0** (`/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node`) |
| Gradle | Wrapper **9.8.1**, direkter Aufruf `./gradlew --no-daemon` (nicht der Python-freie Wrapper) |
| GraalVM | **25.0.3** (`/Users/stefan/.sdkman/candidates/java/25.0.3-graal`) |
| Binaryen | `binaryen-version_133` |
| Browser | Chromium **156.0.8078.4**, Firefox **157.0**, WebKit **27.2**, echtes Safari **27.0 (20625.1.29.18.28)** |
| Python | **nicht verwendet**; PATH enthält nur `/var/folders/.../interlis-without-python-9HNKgw/bin`; `python/python3/pip/pip3/pypy/uv` = ENOENT, absolute Interpreter = EPERM (Manifest des äusseren Launchers) |

## Kriterienprüfung

| Kriterium-ID | Urteil | Eigener Beleg | Finding |
|---|---|---|---|
| **AC4-01** Vollständigkeit, Grammatikzweige, Builtins, 80 Anforderungen | **verletzt** | 1063 JUnit-Fälle in 199 frischen XMLs (886 regulär: 735 Compiler, 41 Repository, 37 CLI, 22 Workspace, 7 Bridge, 16 Model, 16 LSP, 12 Projection; 144 Evidenz, 15 Assertions, 18 Host), 0 Failures/Errors/Skips, Header 199/199 konsistent. `g2EvidenceTest` bindet 369 Bedingungen, 80 Anforderungen, 321 Produktionen mit 1225 Occurrences, 21 Builtins, 130 Predefined, 21 Sondertypen an echte XML-Suiten/Klassen/Methoden; 261/261 Bindings unabhängig gegen Quell-/Methodenhash und frische XML geprüft. **F1/F2 sind korrekt behoben** (2.3 §3.2.1 Transfer = OUTSIDE; areAreas-Links = BuiltinSignatureFoundation; 2.4 C05 = Toleranz, C06 = Topologie). **Aber:** `NORM:2.4:3.6.1:C03` ist als `ASSERTIONS_LINKED` geführt, obwohl der einzige Link `ModifierContractTest.propertyListsOnlyAcceptModifiersPermittedForTheDeclarationContext` keinen einzigen `AttrTypeDef`-Zweig (`MANDATORY`, `BAG/LIST OF`, `Type`, `DomainRef`, `ReferenceAttr`, `RestrictedStructureRef`) ausführt. Die Bedingung ist damit nicht semantisch verknüpft; die 285/84-Bilanz ist für diese Zeile ungenau. | **F5** |
| **AC4-02** JVM-/Portable-/Referenz-Semantikvergleich | **erfüllt** | `semanticComparison`: Exit 0, 4 echte Quellen (2.3/2.4, je gültig/ungültig), **8 Mutationsprüfungen** (fehlender Member, falsches gleichnamiges Ziel, vertauschte Reihenfolge, fälschlich gültig, je Sprache), 8 portable Projektionen (JS/WasmGC), gepinntes ili2c-Metamodell (JAR `37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e`). Grenzen ausdrücklich: nur 3 Viewables, 4 Quellen, Outcome/geordnete Member/qualifizierte Rollenziele; Diagnostikidentitäten, Origins, Ausdrucks-/Generics-/Translationsgraph **nicht** referenzgleich, separat durch direkte Assertions belegt (`SemanticMemberProtocolTest`, `ViewInheritanceTranslationContractTest`, `RemainingLanguageContractTest` u.a.). Eigene fremde A3-Probe auf JS **und** WasmGC: 3-stufige Vererbung `zeta,alpha,middle,omega` in tatsächlicher Quellreihenfolge, korrekte `inheritancePath`-Kette, gleichnamige Ziele `Z.P.Same`/`Z.Q.Same` und `Z.P.Other`/`Z.Q.Other`, doppelter Typname auf beiden Backends abgelehnt. | – |
| **AC4-03** Referenzabweichungen/Profilentscheidungen | **erfüllt** | 12 historische Fälle in `L4-1-decisions.json` (11 Ablehnungen, `overlapTooSmallFail` akzeptiert) mit tatsächlich bestätigtem Top-Level-`userDecision` (30.10.2026), unveränderten Original-/Kontrollpins; alle 12 Test-IDs sind in den 21 aktiven Assessments vorhanden. `L4-1-profile-questions.json` enthält den ausdrücklichen STRICT-Entscheid und die URI-Referenzentscheidung; `L4-1-model-class-decision.json` die 2.3-Kompatibilität. Frischer Conformance-Lauf: roh **577 PASS / 15 FAIL / 1 XFAIL / 0 ERROR / 0 XPASS**, Projekt **577 PASS / 16 XFAIL / 0 FAIL/ERROR/XPASS**, **21/21** Assessments PASS. Eigene CLI-Proben: STRICT äussere Namen (invalid abgelehnt, gültiges `EXTENDED` akzeptiert), relative/fehlerhafte/überlange URI, 3-Rollen-Aggregation, ORDERED-Abschwächung/Erhalt. | – |

## Eigene neue Gegenproben (tatsächlich erfunden und ausgeführt)

Alle Proben liegen im ignorierten Worktree `build/review/L4-1-r3/`; Rohbelege dort.

### P1 – Semantischer Link-Audit (AC4-01, F1/F2/F5)

- **Input:** `testdata/g2/completion-links.json` + gepinnte Normabsätze + tatsächliche Testkörper; keine Produktänderung.
- **Hypothese:** F1/F2/Geometrie-Zuordnungen sind korrekt; jede der 285 als `ASSERTIONS_LINKED` geführten Bedingungen hat mindestens einen gebundenen, öffentlich beobachteten Test; die 84 `OUTSIDE`-Cluster sind ursprünglich `NOT_COMPILER` und tragen keinen Scheinlink.
- **Kommando:** `node build/review/L4-1-r3/probes/link-audit.cjs`
- **Beobachtung:** **Exit 0**, `failures=0`; Counts 369/285/84, 80 Anforderungen, 321 Produktionen/1225 Occurrences, 21 Builtins, 130 Predefined, 21 Sondertypen, 261 Bindings; 369/369 Quotenhashs stimmen; F1 (2.3 §3.2.1 OUTSIDE, 2.4 §3.2.1 Whitespace), F2 (areAreas-Testkörper enthält `areAreas`, `areAreas2/3`, `areAreasWithoutGaps`), Geometrie (2.3 C04/2.4 C05 Toleranz, 2.4 C06 Topologie) PASS. Roh: `build/review/L4-1-r3/raw/link-audit.log`.
- **Ergänzung:** `binding-check.cjs` prüft alle 261 Bindings gegen die frischen XMLs und die Code-Point-Quell-/Methodenhashs: **261/261 OK**, Exit 0 (`raw/binding-check.log`).

### P2 – Fremdes A3-Modell auf TeaVM-JS und WasmGC (AC4-02)

- **Input:** neues 2.4-Modell `Z` mit `STRUCTURE Base/Mid/Leaf` (3-stufig, Felder `zeta,alpha,middle,omega`), zwei Topics `P`/`Q` mit gleichnamigen Klassen `Same`/`Other` und je `ASSOCIATION Rel`; absichtlich ungültige Variante mit doppeltem Typnamen `D`. Keine `testdata/g2/a3/*`-Datei.
- **Hypothese:** Die öffentliche `semanticProperties`-Projektion liefert auf beiden Backends dieselbe tatsächliche Memberreihenfolge, Herkunftskette und voll qualifizierte Rollen-Ziele; die ungültige Variante wird auf beiden Backends abgelehnt.
- **Kommando:** `node build/review/L4-1-r3/probes/a3-r2-probe.cjs`
- **Beobachtung:** **Exit 0**, `A3_R2_PROBE_PASS`; `Z.Leaf` = `zeta,alpha,middle,omega` (Positionen 0..3), Pfade `[Z.Base,Z.Mid,Z.Leaf]`, `[Z.Base,Z.Mid,Z.Leaf]`, `[Z.Mid,Z.Leaf]`, `[Z.Leaf]`; `Z.P.Rel` = `left→Z.P.Same`, `right→Z.P.Other`; `Z.Q.Rel` = `left→Z.Q.Same`, `right→Z.Q.Other`; `D` auf JS und WasmGC `rejected`. Roh: `raw/a3-probe.json`, `raw/a3-probe.log`.
- **Schluss:** AC4-02 ist nicht Fixture-Echo; die reale Projektion beider Backends wurde auf einem fremden Modell bestätigt.

### P3 – Öffentliche CLI-Proben (AC4-01/AC4-03)

- **Input:** acht neue `.ili`-Dateien unter `build/review/L4-1-r3/probes/models/`: äusserer Name ungültig/gültig mit `EXTENDED`, relative URI, URI mit Leerzeichen, 1024-Zeichen-URI, 3-Rollen-Aggregation, ORDERED-Abschwächung, ORDERED-Erhalt.
- **Hypothese:** Die behaupteten Produktkorrekturen sind im öffentlichen CLI-Prozess sichtbar; kein Test wird nur umbenannt.
- **Kommando:** `node build/review/L4-1-r3/probes/cli-r2-probe.cjs`
- **Beobachtung:** **Exit 0**, 8/8 PASS: `outer-invalid` Exit1 `SCOPE_OUTER_TYPE_NAME`; `outer-valid-extended` Exit0; `issuer-relative` Exit0; `issuer-space`/`issuer-overlong` Exit1 `MODEL_ISSUER`; `agg-three` Exit1 `AGGREGATION_ARITY`; `ordered-weakened` Exit1 `ROLE_ORDER_EXTENSION`; `ordered-kept` Exit0. Roh: `raw/cli-probe.json`, `raw/cli-probe.log`.

### P4 – F5-Gegenprobe (AC4-01, Finding)

- **Input:** `NORM:2.4:3.6.1:C03`, dessen einziger Link und der tatsächliche Methodenkörper.
- **Hypothese:** Der Link trägt die zitierte `AttrTypeDef`-Grammatik nur, wenn der Testkörper mindestens einen der Zweige `MANDATORY`, `BAG/LIST OF`, `Type`, `DomainRef`, `ReferenceAttr`, `RestrictedStructureRef` ausführt.
- **Kommando:** `node build/review/L4-1-r3/probes/f5-c03.cjs`
- **Beobachtung:** **Exit 2** (Befundsignal, kein Produktfehler): `C03_MISMATCH`; `AttrTypeDef tokens present: []`; der Testkörper deklariert **kein Attribut**, sondern nur `TOPIC T (ABSTRACT) = CLASS C (ABSTRACT) = END C` bzw. `CLASS C (TRANSIENT)`. Roh: `raw/f5-c03.log`.
- **Schluss:** Die Zeile ist semantisch nicht verknüpft; AC4-01 ist insoweit nicht vollständig belegt.

## Pflichtprüfungen (frisch auf exakt `b804c47`)

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
| Gesamtlauf | **Exit 0**, `BUILD SUCCESSFUL in 6m 30s`, **80 actionable tasks: 80 executed**; keine fällige Pflichtprüfung übersprungen. Roh: `build/review/L4-1-r3/raw/gate.log`. |
| JUnit | **1063** Tests in **199** XMLs; 886 regulär (735 Compiler, 41 Repository, 37 CLI, 22 Workspace, 7 Bridge, 16 Model, 16 LSP, 12 Projection), 144 Evidenz, 15 Assertions, 18 Host; **0 Failures / 0 Errors / 0 Skips**. Header: 199/199 `testsuite@name` = `testcase@classname`, `tests` = Anzahl `testcase`, Zeitstempel `2026-10-30T15:11:02Z`–`15:12:56Z`. Zusammenfassung: `raw/junit-summary.json`. |
| `checkPortable` | TeaVM-JS/Node **3548 PASS**, WasmGC/Node **3548 PASS** (`interlis-wasm-teavm/build/js-contract-result.json`, `wasm-contract-result.json`). |
| `checkBrowser` | Chromium **3548**, Firefox **3548**, WebKit **3548**, echtes Safari **3548** (`browser-results.json`, `safari-result.json`). |
| WebImage | WebImage/Node **3548**; Chromium/Firefox/WebKit je **3548** (`interlis-wasm-webimage/build/webimage/result.json`, `webimage-browser/browser-results.json`). |
| `semanticComparison` | **Exit 0**, 4 Fälle, 8 Mutationen, Referenz-JAR `37d94573…`; `a3-portable.json` 8 Projektionen; Grenzen im Report (`semantic-comparison.json`). |
| `g2EvidenceTest` | **144** Tests, 0 Fehler; `CompletionLinksTest` 7 Tests (1 + 6 Negativproben) PASS. |
| `conformanceCheck` | 593/610 (17 INTERLIS-1 ausgeschlossen); roh **577 PASS / 15 FAIL / 1 XFAIL / 0 ERROR / 0 XPASS**; Projekt **577 / 16 XFAIL / 0 FAIL / 0 ERROR / 0 XPASS**; **21/21** Assessments PASS (`conformance-summary.json`). |
| `referenceCheck` | 5/5 PASS (`reference-results.json`). |
| `originalViewReferenceCheck` | **19/19** PASS, 0 Prozessfehler. |
| `viewReferenceCheck` | 134 eigene Erwartungen, **102** Übereinstimmungen, **6** sichtbare Referenzprozessfehler; keine Gleichheitsbehauptung. |
| `unitReferenceCheck` | 123 eigene Erwartungen, **121** Übereinstimmungen, **0** Prozessfehler. |
| `pythonFreeCheck` | **1287** Inputs PASS; getrackte `.py` = 0. Äusseres Manifest: `gitHead` exakt `b804c47…`, `dirty` leer, PATH nur ohne-python-Bin, `python/python3/pip/pip3/pypy/uv` ENOENT, absolute Interpreter EPERM, Profil-SHA `655915d1…`; `status: running` wird erst nach CLI-Ende vom äusseren Launcher finalisiert (`raw/outer-environment-snapshot.json`, SHA-256 `d207e064…`). |
| SDK/LSP | `SDK_PROCESS_PASS 9 commands`; SDK-ZIP `build/releases/interlis-java-sdk-0.2.0-SNAPSHOT.zip`; `:interlis-lsp:installDist` erfolgreich. |
| `performanceCheck` | JVM **13** Modelle (11 erfolgreich, 2 exakt abgelehnt); WasmGC **4**; Chromium/Firefox je 13 (12 gemessen, 1 Stackausnahme `IliValidGwr_V1_0`); WebKit 13 (1 gemessen, 12 Stackausnahmen). P3-Messgrenzen sichtbar, keine G2-Browserpflichtskips (dort alle 3548). |

## Norm-/Suite-/Pin-/Baseline-Unverändertheit

- **Keine** Änderung unter `docs/evidence/conformance/` oder `testdata/conformance/` (Diff-Dateiliste leer).
- Suite-Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3` und Archiv-SHA-256 `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe` unverändert; das gecachte Archiv wurde mit exakt diesem Hash geprüft.
- `baseline.json` (1 INTERLIS-1-XFAIL), die **21** aktiven Assessments und das historische **19-Fälle-Bytearchiv** unverändert; keine neue XFAIL-Ausnahme, kein XPASS.
- `predefined-catalogue.json` und `g2-source-inventory.json` ändern nur Quellanker-/Hash-/Offsetwerte; nach Entfernen von `sourceAnchors` ist die Predefined-Katalogstruktur **semantisch identisch** zum Basisstand; `g2-test-candidate-index.json` bleibt `CANDIDATE_ONLY` (Testmethodenzahl 704→729, keine Promotion).
- `testdata/g2/corpus-expectations.json`: Original-/Kontrollhashes stimmen; das ZIP-Original ist mit den zwei minimalen Renames **bytegenau** die Kontrollkopie; Diagnose-Ranges zeigen auf die tatsächlichen Deklarationen; `performance-browser.json` bindet den Erwartungs-Hash `d1a4d7d6…`; 11 Compiles + 2 exakte Ablehnungen + 13 Formatierungen.
- Gegenüber `5d54c9d` sind nur `STATUS.md`, `L4-1.md`, der unveränderte Erstbericht und `completion-links.json` geändert. Compiler/API/Tests/Build/Suite/Normen/Erwartungen unverändert; `L4-1-review-before-link-fix.md` hat exakt SHA-256 `40c9ced549c1d626f6efcefe39bfa407e768e649293418774c9f242a922f37a5`.
- F4 (`reviewStatus = OPEN` beim `overlapTooSmallFail`-Einzelfeld) ist innerhalb der byte-/feldtreuen historischen Originalfallkopie; der Top-Level-`userDecision` bestätigt den Nutzerakt. Kein offener Nutzerakt.

## Findings

### F5 – MINOR (Evidenzgenauigkeit, aber Muss für die AC4-01-Zeile): falscher Completion-Link für `NORM:2.4:3.6.1:C03`

- **Ort:** `testdata/g2/completion-links.json`, Bedingung `NORM:2.4:3.6.1:C03`; Beleg `build/review/L4-1-r3/raw/f5-c03.log`.
- **Reproduktion:** `node build/review/L4-1-r3/probes/f5-c03.cjs` → `C03_MISMATCH`, Exit 2 (Befundsignal).
- **Erwarteter Vertrag:** Die Quote ist die Grammatik `AttrTypeDef = ( 'MANDATORY' [ AttrType ] | AttrType | ( ( 'BAG' | 'LIST' ) [ Cardinality ] 'OF' AttrType ) )` und `AttrType = ( Type | DomainRef | ReferenceAttr | RestrictedStructureRef )`.
- **Beobachteter Fehler:** Einziger Link `ModifierContractTest.propertyListsOnlyAcceptModifiersPermittedForTheDeclarationContext`; der Methodenkörper kompiliert nur `TOPIC T (ABSTRACT) = CLASS C (ABSTRACT) = END C` bzw. `CLASS C (TRANSIENT) = END C` und enthält **keine Attributdeklaration** und keinen der genannten `AttrTypeDef`-Zweige. Die Zeile ist als `ASSERTIONS_LINKED` gezählt, obwohl sie den zitierten Normsatz nicht ausführt.
- **Wirkung:** Die behauptete Bilanz „285 Compilercluster mit Assertions“ ist für diese Zeile ungenau; die Integritätsprüfung kann das nicht erkennen, weil sie nur Methode/Xml/Hash bindet. Die `AttrTypeDef`-Sprache selbst ist nicht ungetestet (Grammatikgruppe 3.6 und z.B. `StructureValueContractTest`/`AttributeModifierContractTest`), aber der bedingungsspezifische Nachweis fehlt.
- **Nötige Nacharbeit:** Link auf einen Test setzen, der `MANDATORY`/`BAG`/`LIST OF`/`REFERENCE TO`/`RESTRICTION` tatsächlich parst (z.B. `StructureValueContractTest.anyStructureAndOrderedRestrictionsAreBoundForScalarAndCollectionAttributes` und/oder `AttributeModifierContractTest.abstractAttributeModifierRemainsVisible`/`NoOidReferenceContractTest.externalAndAnyClassAreIndependentOfStableIdentity`), Scope/Resolution entsprechend präzisieren. Evidenz-only; neuer Kandidat und erneuter Review erforderlich. Kein Produktcode betroffen.

### Bestätigt geschlossen

- **F1** (2.3 §3.2.1 Transfer/Whitespace): `OUTSIDE_G2_COMPILER`, keine Whitespace-Compilerassertion; 2.4 §3.2.1 Whitespace-Link bleibt. Eigene Quote-/Link-Prüfung PASS.
- **F2** (areAreas): beide Absätze führen `BuiltinSignatureFoundationContractTest…` als ersten Link; Testkörper enthält `areAreas`, `areAreas2/3`, `areAreasWithoutGaps`; Constraintsyntax separat. PASS.
- **F3** (1061/16/72 vs. 1063/18/80): Der frühere 72-Task-Lauf ist ausdrücklich historisch; der unabhängige Kandidatenlauf wird mit 1063/18/80/6m09s berichtet. Keine rückwirkende Zahlenumdeutung.
- **F4** (OPEN im historischen Einzelfeld): top-level Nutzerentscheid bestätigt; kein fehlender Nutzerakt.

## Offene Blocker / benötigte konkrete Nacharbeit

- **Kein BLOCKER** (keine fehlende Nutzerentscheidung, keine Infrastrukturlücke). F5 ist eine eng begrenzte Evidenzkorrektur.
- Nach Korrektur von F5 in `testdata/g2/completion-links.json` ist ein **neuer Kandidaten-SHA** erforderlich; `g2EvidenceTest`, der semantische Link-Audit und der vollständige Pflichtlauf sind erneut auszuführen. Produktcode, Tests, Suite-/Archivpins, Normen und Erwartungen müssen unverändert bleiben.

## Urteil

**FAIL** – AC4-01 ist wegen F5 (falscher Completion-Link für `NORM:2.4:3.6.1:C03`) nicht vollständig semantisch belegt. AC4-02 und AC4-03 sind auf dem exakten SHA durch eigene Proben und den frischen Pflichtlauf **erfüllt**. Alle ausführbaren Tore sind grün (1063 JUnit, 3548 je Portable/Browser/Safari/WebImage, Conformance 577/16/0 Projekt, 21/21 Assessments, Pythonfreiheit 1287 Inputs, SDK9/LSP), aber ein Muss-Kriterium der Vollständigkeits-Evidenz bleibt offen. L4-2 beginnt daher **nicht**; nach der engen Evidenzkorrektur und neuem Kandidaten ist L4-1 erneut zu prüfen, danach folgt automatisch L4-2.

**Nächster konkreter Schritt:** F5-Link in `testdata/g2/completion-links.json` auf einen tatsächlichen `AttrTypeDef`-Test umstellen, neuen Kandidaten committen und den vollständigen L4-1-Review (mindestens `g2EvidenceTest` + Link-Audit + voller Pflichtlauf) erneut ausführen. Kein Push/Release.

**Rohbelege:** `build/review/L4-1-r3/raw/gate.log`, `junit-summary.json`, `binding-check.log`, `link-audit.log`, `a3-probe.json`, `a3-probe.log`, `cli-probe.json`, `cli-probe.log`, `f5-c03.log`, `outer-environment-snapshot.json`; ferner `build/reports/g2/semantic-comparison.json`, `a3-jvm.json`, `a3-portable.json`, `build/reports/conformance/conformance-summary.json`, `build/reports/performance/performance-*.json`, `build/reports/stabilization/*-reference-comparison.json`, `interlis-wasm-teavm/build/{js,wasm}-contract-result.json`, `browser-results.json`, `safari-result.json`, `interlis-wasm-webimage/build/webimage*/`.

**Transporttrennung:** Der oben genannte **Exit 0** ist der Exit des Gradle-Pflichtlaufs, nicht der Exit des OpenCode-CLI-Transports. Der CLI-Transportexit wird ausschliesslich vom äusseren Launcher unverfälscht protokolliert; aus dem grünen Gradle-Lauf wird kein erfolgreicher CLI-Transport abgeleitet.
