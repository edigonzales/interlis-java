# Unabhängiger Review: L4-1

Lieferabschnitt / Phase / letztes Phasengate?: **L4-1 / Phase 4 / nein** (das Phasengate ist L4-2, AC4-04–07).  
Implementierungsagent: Codex.  
Prüfagent: OpenCode CLI 2.0.28; Harness-Konfiguration **provider `deepseek`, model `deepseek-flash`**, direkte DeepSeek-API (kein OpenRouter); Modellselbstauskunft ist kein kryptografischer Identitätsnachweis, die Konfiguration wurde aus der laufenden Sitzung übernommen.  
BASE_SHA: `4a84068624cffde4549186c53cf23b9aaad7b179`  
CANDIDATE_SHA (durch `git rev-parse HEAD` verifiziert): `5d54c9dcf4fa6f8d5226c233d6d117287281e636`  
Worktree: `/tmp/interlis-java-L4-1-review-5d54c9d` (sauber, `git status --porcelain` leer; eigene Proben ausschliesslich unter ignoriertem `build/review/`).  
Bericht: `/Users/stefan/sources/interlis-java/build/review/L4-1/review.md` (ausserhalb des Kandidaten).

## Prüfumgebung / Toolchain

| Werkzeug | Tatsächlich verwendet |
|---|---|
| Java | Temurin **25.0.3+9-LTS** (`JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem`) |
| Node | **v24.22.0** (`/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node`) |
| Gradle | Wrapper **9.8.1** |
| GraalVM | **25.0.3** (`/Users/stefan/.sdkman/candidates/java/25.0.3-graal`) |
| Binaryen | `binaryen-version_133` |
| Browser | Chromium **156.0.8078.4**, Firefox **157.0**, WebKit **27.2**, echtes Safari **27.0 (20625.1.29.18.28)** |
| Python | **nicht verwendet**; Lauf über `tools/verification/without-python.cjs` |

## Kriterienprüfung

| Kriterium-ID | Urteil | Eigener Beleg | Finding |
|---|---|---|---|
| **AC4-01** Vollständigkeit, Grammatikzweige, Builtins, 80 Anforderungen | **verletzt** | 1063 JUnit-Fälle in 199 frischen XMLs (886 regulär: 735 Compiler, 41 Repository, 37 CLI, 22 Workspace, 7 Bridge, 16 Model, 16 LSP, 12 Projection; 144 Evidenz, 15 Assertions, 18 Host), 0 Failures/Errors/Skips. `g2EvidenceTest` bindet 369 Bedingungen, 80 Anforderungen, 321 Produktionen mit 1225 Occurrences, 21 Builtins, 130 Predefined, 21 Sondertypen an echte XML-Suiten/Klassen/Methoden; 6 Negativproben. **Eigener Einzelabgleich der Links** zeigt jedoch eine falsche Zuordnung: `NORM:2.3:3.2.1:C01` (Transfer-Ableitbarkeit, P4) ist als `ASSERTIONS_LINKED` geführt, obwohl sein einziger Link `RemainingLanguageContractTest.acceptedAndRejectedWhitespaceAreExplicitInBothLanguageProfiles` keinen Transfer-/Ableitungs-/Datenmodellbegriff enthält und der eigene `scope`/`resolution` P4-Laufzeitsemantik behauptet. Die 2.3-§3.2.1-Quote ist Transfer, die 2.4-§3.2.1-Quote ist Zeichenvorrat/Whitespace; hier wurde offenbar die 2.4-Behandlung auf die 2.3-ID kopiert. Der Integritätstest kann das nicht erkennen, weil er nur Methoden-/XML-Existenz prüft. | **F1** |
| **AC4-02** JVM-/Portable-/Referenz-Semantikvergleich | **erfüllt** | `semanticComparison`: Exit 0, 4 tatsächliche Fälle (2.3/2.4, je gültig/ungültig), **8 Mutationsprüfungen**; JVM, TeaVM-JS und WasmGC liefern dieselbe öffentliche Member-/Zielprojektion; gepinntes ili2c-5.6.9-Metamodell (`JAR 37d94573…`) liefert für `F.Child`, `F.T.Link`, `F.U.Link` dieselben geordneten Member und voll qualifizierten Rollenziele. `a3-portable.json`: 8 Projektionen (JS/WasmGC × 4 Quellen), Exit 0. Grenzen sind ausdrücklich ausgewiesen (nur 3 Viewables, nur Outcome/geordnete Member/qualifizierte Rollenziele; Origins/Diagnostik/Ausdruck/Generics/Translation nicht referenzgleich). Eigene neue A3-Probe auf einem **fremden Modell** (nicht `testdata/g2/a3/*`) bestätigt Reihenfolge, Vererbungspfad, gleichnamige Ziele in zwei Topics, JS/WasmGC-Gleichheit, Ablehnung von `5..0` und Erkennung eines falschen gleichnamigen Ziels. | – |
| **AC4-03** Referenzabweichungen/Profilentscheidungen | **erfüllt** | 12 historische Fälle in `L4-1-decisions.json` (11 Ablehnungen, `overlapTooSmallFail` akzeptiert) mit unveränderten Original-/Kontrollpins (0 Abweichungen gegen `normative-expectations.json`); 6 frühere OID-/CONTRACTED-Fälle, CIRCULAR, SET, TYPE-MODEL, Numerik, areAreas2/3 und N2/N3 gelten fort. Frischer Conformance-Lauf: roh **577 PASS / 15 FAIL / 1 XFAIL**, Projekt **577 PASS / 16 XFAIL / 0 FAIL/ERROR/XPASS**, **21/21** strenge Assessments PASS. Suite-Commit `88576ffc…`, Archiv-SHA-256 `6a80b7e8…` und Baseline unverändert. Eigene CLI-Probe bestätigt die STRICT-Entscheidung an den bytegepinnten Originalen sowie die URI-Referenzentscheidung. | – |

## Eigene neue Gegenproben (tatsächlich erfunden und ausgeführt)

Alle Proben liegen im ignorierten Worktree `build/review/probes/`; Rohbelege zusätzlich unter `/Users/stefan/sources/interlis-java/build/review/L4-1/raw/probes/`.

### P1 – A3-Projektion auf einem fremden Modell (AC4-02)

- **Input:** neues Modell `Probe` (2.4) mit `STRUCTURE Base/Derived`, zwei Topics `P`/`Q` mit je `CLASS Same`, `CLASS Other` und `ASSOCIATION R` (Rollen `left`/`right`), sowie eine ungültige Variante mit `gamma: 5..0`. Keine der `testdata/g2/a3/*`-Dateien.
- **Hypothese:** Die öffentliche `semanticProperties`-Projektion liefert auf TeaVM-JS **und** WasmGC dieselbe geerbte Memberreihenfolge und voll qualifizierte Ziele; eine falsche gleichnamige Zielzuordnung (`Probe.P.Same` statt `Probe.Q.Same`) wird erkannt; `5..0` wird auf beiden Backends abgelehnt.
- **Kommando:** `node build/review/probes/a3-probe.cjs`
- **Beobachtung:** `A3_PROBE_PASS fresh model; JS and WasmGC agree; invalid range rejected in both; wrong equal-name target detected`, Exit 0. `Probe.Derived` = `alpha,beta,gamma` mit Positionen 0,1,2 und Pfaden `Probe.Base→Probe.Derived` bzw. `Probe.Derived`; `Probe.P.R` = `left→Probe.P.Same`, `right→Probe.P.Other`; `Probe.Q.R` = `left→Probe.Q.Same`, `right→Probe.Q.Other`.
- **Schluss:** AC4-02 wird nicht nur durch Fixture-Echo, sondern durch eine eigene öffentliche Projektion auf beiden Portables gestützt.

### P2 – Identitätskontinuität über echte Edits (AC4-01)

- **Input:** 2.4-Modell mit Geschwister-Topics `A`/`B`, je `STRUCTURE Shared`; Edit 1 = Kommentar + fremde Domain, Edit 2 = Verschieben von `B.Shared` nach neuem `TOPIC C`, Edit 3 = Duplikat `Shared` in `A`.
- **Hypothese:** Eindeutige Deklarationen behalten ihre Identität über Kommentar/Einfügung; Verschieben in einen anderen Besitzer und Duplikate im selben Scope erzeugen neue Identitäten; unbeteiligte Geschwister bleiben stabil.
- **Kommando:** `node build/review/probes/identity-probe.cjs`
- **Beobachtung:** `IDENTITY_PROBE_PASS unique sibling continuity; comment/insertion stable; move and duplicate allocate new identities`, Exit 0. Alle IDs in `identity-probe.json`.
- **Schluss:** Die neue `DeclarationIndexer`-Logik erfüllt die behauptete Kontinuitätsgrenze auf dem öffentlichen Portable-Bridge-Pfad.

### P3 – CLI-Profilentscheidungen (AC4-03)

- **Input:** relative URI `../models?x=1#v`, ungültige URI `a b`, 1024 Zeichen, sowie die bytegepinnten Corpus-Originale `SO_AGI_Metadata_20230304` und `SO_AWJF_Gesuchsteller_20210202` und die separat umbenannten Kontrollen.
- **Hypothese:** Relative URI-Referenzen bleiben gültig; Syntaxfehler/Überlänge werden mit `MODEL_ISSUER` abgelehnt; die STRICT-Regel lehnt die Originale mit genau einer `SCOPE_OUTER_TYPE_NAME`-Diagnose ab, die Kontrollen kompilieren.
- **Kommando:** `sh build/review/probes/cli-probe.sh`
- **Beobachtung:** `CLI_PROBE_PASS`, Exit 0; `relative exit=0`, `space exit=1`, `long exit=1`, `metadata-original exit=1`, `metadata-control exit=0`. Die Metadata-Originaldiagnose stimmt exakt mit `corpus-expectations.json`: `ILI-SEM-0010 / ERROR / SEMANTIC_CHECKING / diagnostic.semantics.inheritance`, Range `2442..2544`, Argumente `FileFormat` + `local type names must not shadow an enclosing type name/SCOPE_OUTER_TYPE_NAME`, Multiplizität 1. Der Gesuchsteller-Fall: Range `734..1238`, gleiche Argumente, Multiplizität 1.
- **Schluss:** Die Nutzerentscheidungen sind im öffentlichen Prozessverhalten umgesetzt; der Benchmark wurde nicht abgeschwächt.

### P4 – Semantische Relevanz der Completion-Links (AC4-01, Finding)

- **Input:** die fünf `ASSERTIONS_LINKED`-Bedingungen `NORM:2.3:3.2.1:C01`, `NORM:2.3:2.8.13.1:C04`, `NORM:2.4:3.8.13.1:C06`, `NORM:2.3:2.12:C03`, `NORM:2.4:3.12:C04`; Vergleich der normativen Schlüsselbegriffe mit dem tatsächlichen Methodenkörper des einzigen jeweiligen Links.
- **Hypothese:** Ein Link, der die zitierte Normaussage trägt, muss im Testkörper mindestens einen der zitierten Begriffe (Transfer/Ableitung/Datenmodell, Fläche/Rand/Figur, areAreas) ausführen.
- **Kommando:** `node build/review/probes/link-audit.cjs`
- **Beobachtung:** `LINK_AUDIT_IRRELEVANT 5/5`, Exit 2 (der Exit 2 ist die Befundsignalisierung, kein Produktfehler). Insbesondere:
  - `NORM:2.3:3.2.1:C01`, `scope` = „Transfer-Ableitbarkeit aus dem Datenmodell ist P4-Laufzeitsemantik.“, `resolution` = „Implementierungsdefinierter Whitespace-Vorrat explizit geprüft …“ → Link `RemainingLanguageContractTest.acceptedAndRejectedWhitespaceAreExplicitInBothLanguageProfiles`; Testkörper enthält nur Whitespace-Fälle, keinen Transfer-/Ableitungsbegriff.
  - `NORM:2.3:2.8.13.1:C04` (Figur 21, Randlinien/Enklaven/Zusammenhang) und `NORM:2.4:3.8.13.1:C06` („Linienzüge, die nicht zu einem Rand gehören, sind nicht erlaubt“) → nur `GeometryRulesContractTest.surfaceToleranceIsExplicitIn23AndSymbolicIn24`, das Toleranz, nicht Topologie/Figuren prüft (der `resolution` nennt den gemischten Compileranteil „Toleranz“; die Zuordnung ist als gemischter Absatz verteidigbar, aber der Link ist nicht der zitierte Teil).
  - `NORM:2.3:2.12:C03` / `NORM:2.4:3.12:C04` (areAreas-Beispiel) → nur `ExpressionConstraintContractTest.builtinFunctionAndImplicationCompile`, das `INTERLIS.len`/Implikation, aber kein `areAreas` ausführt; die areAreas-Bindung ist an anderer Stelle (`BuiltinSignatureFoundationContractTest.allAvailableBuiltins…`) tatsächlich geprüft, der Link ist aber falsch.
- **Schluss:** Die Integritätsprüfung ist grün, aber die behauptete 286/83-Aufteilung ist nicht durchgehend semantisch tragfähig. Das ist ein offenes Muss-Finding gegen AC4-01, kein Produktcodefehler.

## Pflichtprüfungen (frisch auf exakt `5d54c9d`)

Tatsächliches Kommando (identisch mit dem Auftrag, über den Python-freien Wrapper; Gradle ergänzt `--no-daemon`):

```sh
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
tools/verification/without-python.cjs ./gradlew \
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
| Gesamtlauf | **Exit 0**, `BUILD SUCCESSFUL in 6m 9s`, **80 Tasks ausgeführt**; keine fällige Prüfung übersprungen. Nur optionale Tasks `emscriptenStubWasmGC`, `emscriptenWasmGC`, `disasmWasmGC` SKIPPED. Rohlog: `build/review/L4-1/raw/gate.log`. |
| JUnit | **1063** Tests in **199** XML-Dateien: 886 regulär (735 Compiler, 41 Repository, 37 CLI, 22 Workspace, 7 Bridge, 16 Model, 16 LSP, 12 Projection), 144 Evidenz, 15 Assertions, 18 Host; **0 Failures / 0 Errors / 0 Skips**. Headerprüfung: 199/199 `testsuite@name` = `testcase@classname`, `tests` = Anzahl `testcase`, Zeitstempel `2026-10-30T14:39:22Z`–`14:41:06Z`. Zusammenfassung: `build/review/L4-1/raw/junit-summary.json`. |
| `checkPortable` | TeaVM-JS/Node **3548 PASS**, WasmGC/Node **3548 PASS** (`js-contract-result.json`, `wasm-contract-result.json`). |
| `checkBrowser` | Chromium **3548**, Firefox **3548**, WebKit **3548**, echtes Safari **3548** (`browser-results.json`, `safari-result.json`). |
| WebImage | WebImage/Node **3548**; Chromium/Firefox/WebKit je **3548** (`webimage/result.json`, `webimage-browser/browser-results.json`). |
| `semanticComparison` | **Exit 0**, `SEMANTIC_COMPARISON_PASS 4 … 8 mutation checks`; `a3-portable.json` 8 Projektionen, Exit 0; `referenceJarSha256 37d94573…`; Grenzen ausgewiesen (`semantic-comparison.json`). |
| `g2EvidenceTest` | **144** Tests, 0 Fehler; `CompletionLinksTest` 7 Tests (1 + 6 Negativproben) PASS. |
| `conformanceCheck` | 593/610 (17 INTERLIS-1 ausgeschlossen); roh **577 PASS / 15 FAIL / 1 XFAIL / 0 ERROR / 0 XPASS**; Projekt **577 PASS / 16 XFAIL / 0 FAIL / 0 ERROR / 0 XPASS**; **21/21** Assessments PASS. Rohbericht: `conformance-summary.json`, `build/conformance/report.json`. |
| `referenceCheck` | 5/5 PASS (`build/reports/reference/reference-results.json`). |
| `originalViewReferenceCheck` | 19/19 PASS, 0 Prozessfehler (`original-view-reference-comparison.json`). |
| `viewReferenceCheck` | 134 eigene Erwartungen, 102 Referenzübereinstimmungen, **6 sichtbare Referenzprozessfehler** (`views-reference-comparison.json`); keine Gleichheitsbehauptung. |
| `unitReferenceCheck` | 123 eigene Erwartungen, 121 Übereinstimmungen, **0** Prozessfehler (`units-reference-comparison.json`). |
| `pythonFreeCheck` | **1286** Inputs PASS; getrackte `.py` = **0**; `gitHead = 5d54c9d…`, `dirty = ""`; Proben `python/python3/pip/pip3/pypy/uv` = **ENOENT**, absolute Pfade = **EPERM** (`environment.json`, `source-check.json`). |
| SDK/LSP | `SDK_PROCESS_PASS 9 commands`, `sdkPackage`, `:interlis-lsp:installDist` erfolgreich. |
| `performanceCheck` | JVM **13** Modelle (11 erfolgreich, 2 exakt abgelehnt); WasmGC **4**; Chromium/Firefox je 13 (12 gemessen, 1 Stackausnahme `IliValidGwr_V1_0`); WebKit 13 (1 gemessen, 12 Stackausnahmen). Die P3-Messausnahmen sind sichtbar und ersetzen keine G2-Browserfälle (`performance-browser.json`). |

## Norm-/Suite-/Pin-/Baseline-Unverändertheit

- **Keine** Änderung unter `docs/evidence/conformance/` oder `testdata/conformance/` (Diff-Dateiliste leer).
- Suite-Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3` und Archiv-SHA-256 `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe` unverändert; das gecachte Archiv wurde mit exakt diesem Hash geprüft.
- `baseline.json` (1 INTERLIS-1-XFAIL), die **21** aktiven Assessments und das historische **19-Fälle-Bytearchiv** unverändert; keine neue XFAIL-Ausnahme, kein XPASS.
- `predefined-catalogue.json` und `g2-source-inventory.json` ändern nur Quellanker-/Hash-/Offsetwerte, keine fachlichen Werte; `g2-test-candidate-index.json` bleibt `CANDIDATE_ONLY` (TestMethodenzahl 704→729, keine Promotion).
- `git diff --check` meldet nur Trailing Whitespace in den neuen `.ili`-Kontrollkopien (`association-model-class23.ili`, `metadata-outer-name-control23.ili`); das ist bei byte-nahen Originalkopien erklärbar, aber hygienisch zu bereinigen.

## Findings

### F1 – MAJOR / Muss (AC4-01): falscher Completion-Link für `NORM:2.3:3.2.1:C01`

- **Ort:** `testdata/g2/completion-links.json`, Bedingung `NORM:2.3:3.2.1:C01`; Beleg `build/review/probes/link-audit.json`.
- **Reproduktion:** `node build/review/probes/link-audit.cjs` → `LINK_AUDIT_IRRELEVANT 5/5`, Exit 2.
- **Erwarteter Vertrag:** Die 2.3-§3.2.1-Quote lautet „Jeder INTERLIS-Transfer kann aus dem dazugehörigen INTERLIS-Datenmodell durch die Anwendung von Regeln abgeleitet werden (modell-basierter Datentransfer).“ Das ist P4-Transfer, keine Compilerbedingung; die parallele 2.3-§3.2.2- und die 2.4-§4.2.1/4.2.2-Transferbedingungen stehen korrekt als `OUTSIDE_G2_COMPILER`.
- **Beobachteter Fehler:** Disposition `ASSERTIONS_LINKED`, einziger Link `RemainingLanguageContractTest.acceptedAndRejectedWhitespaceAreExplicitInBothLanguageProfiles`; der Methodenkörper enthält nur `INTERLIS<space>2.x`-Whitespace-Fälle, keinen Transfer-/Ableitungs-/Datenmodellbegriff. Die `scope`-Zeile sagt selbst „P4-Laufzeitsemantik“, die `resolution` ist die der **2.4-§3.2.1**-Zeichenvorratsbedingung („Implementierungsdefinierter Whitespace-Vorrat …“). Offenbar wurde die 2.4-Behandlung auf die 2.3-ID desselben Abschnittsmusters kopiert.
- **Wirkung:** Die behauptete Aufteilung „286 ASSERTIONS_LINKED / 83 OUTSIDE_G2_COMPILER“ und die Aussage in `docs/g2/implementation/L4-1.md` („Compilerteile und reine Daten-/Transferausführung sind jeweils benannt“) sind um diesen Fall ungenau. Der `CompletionLinksTest` erkennt das nicht, weil er nur Link-/Methoden-/XML-Existenz prüft.
- **Nötige Nacharbeit:** `NORM:2.3:3.2.1:C01` auf `OUTSIDE_G2_COMPILER` mit korrekter Transfer-/P4-Begründung setzen (analog `NORM:2.3:3.2.2:C01`, `NORM:2.4:4.2.1:C01`, `NORM:2.4:4.2.2:C01`); keine Assertion erfinden und keinen P4-Test als Compilerbeleg ausgeben. Kein Produktcode betroffen.

### F2 – MINOR (Evidenzgenauigkeit): areAreas-Links zeigen auf den falschen Test

- `NORM:2.3:2.12:C03` und `NORM:2.4:3.12:C04` (areAreas-Beispiel) verweisen nur auf `ExpressionConstraintContractTest.builtinFunctionAndImplicationCompile`, das `INTERLIS.len`/Implikation testet, nicht `areAreas`. Die areAreas-Bindung ist tatsächlich durch `BuiltinSignatureFoundationContractTest.allAvailableBuiltinsKeepTheirRealSignaturesAndBoundCallsInCompiledSchema` geprüft (Testkörper enthält `INTERLIS.areAreas`, `areAreas2/3`, `areAreasWithoutGaps`). Link korrigieren; die Bedingung bleibt compilerrelevant.

### F3 – MINOR (Berichtsgenauigkeit): Zahlen im Implementierungsbericht

- `docs/g2/implementation/L4-1.md` nennt im Abschnitt „Vollständiger eigener Pflichtlauf“ **1061 JUnit / 16 Host** und **72/72 Tasks**; der frische Kandidatenlauf hat **1063 JUnit / 18 Host** und **80 Tasks**. Der spätere Abschnitt „corpus-profile-tests3.log“ nennt bereits 18 Host. Kein funktionaler Fehler, aber die Endzahlen sollten auf den tatsächlichen Lauf aktualisiert werden.

### F4 – Beobachtung (kein Muss-Finding): `overlapTooSmallFail`-Einzelfeld

- `L4-1-decisions.json` führt beim Fall `overlapTooSmallFail` weiterhin `norm.reviewStatus = "Independent normative review remains OPEN"`, während die Nutzerentscheidung top-level als bestätigt erfasst ist. Das ist als historische Offenheit der unabhängigen Normprüfung lesbar; die Nutzerentscheidung und der unveränderte XFAIL sind korrekt. Keine Normentscheidung durch den Reviewer.

## Offene Blocker / benötigte konkrete Nacharbeit

- **Kein BLOCKER** (keine fehlende Nutzerentscheidung, keine Infrastrukturlücke). F1 ist eine eng begrenzte Evidenzkorrektur; F2/F3 sind Genauigkeitskorrekturen.
- Nach Korrektur von F1/F2 in `testdata/g2/completion-links.json` (und ggf. Berichtszahlen F3) ist ein **neuer Kandidaten-SHA** erforderlich; `:interlis-cli:g2EvidenceTest`, `CompletionLinksTest` und der Einzelabgleich sind erneut auszuführen. Produktcode, Tests, Suite-/Archivpins, Normen und Erwartungen müssen unverändert bleiben.

## Urteil

**FAIL** – AC4-01 ist wegen F1 (falsche Completion-Link-Zuordnung und dadurch ungenaue 286/83-Bilanz) nicht vollständig belegt. AC4-02 und AC4-03 sind auf dem exakten SHA durch eigene Proben und den frischen Pflichtlauf **erfüllt**. Alle ausführbaren Tore sind grün (1063 JUnit, 3548 je Portable/Browser/Safari/WebImage, Conformance 577/16/0 Projekt, 21/21 Assessments, Pythonfreiheit 1286 Inputs), aber ein Muss-Kriterium der Vollständigkeits-Evidenz bleibt offen. L4-2 beginnt daher **nicht**; nach der engen Evidenzkorrektur und neuem Kandidaten ist L4-1 erneut zu prüfen, danach folgt automatisch L4-2.

**Nächster konkreter Schritt:** F1/F2 in `testdata/g2/completion-links.json` korrigieren, F3 im Implementierungsbericht nachziehen, neuen Kandidaten committen und den vollständigen L4-1-Review (mindestens `g2EvidenceTest` + Link-Audit + voller Pflichtlauf) erneut ausführen. Kein Push/Release.

**Rohbelege:** `build/review/L4-1/raw/gate.log`, `junit-summary.json`, `conformance-summary.json`, `semantic-comparison.json`, `a3-jvm.json`, `a3-portable.json`, `environment.json`, `performance-*.json`, `views-reference-comparison.json`, `units-reference-comparison.json`, `original-view-reference-comparison.json`, `probes/`.
