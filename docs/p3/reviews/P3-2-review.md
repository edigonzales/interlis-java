# Unabhängiger P3-2-Kandidatenreview (Runde 7) – LSP und gemeinsame Sprachabfragen

**Status: PASS für P3-2** (AC-P3-05 bis AC-P3-08 sowie kumulativ P3-1 im hier geprüften JVM-/Portable-Umfang).
**Kein G3-Abschluss.** P3-3, P3-4, P3-5 und die finale Plattformmatrix bleiben offen.
Kein bedingtes PASS, keine Reparatur am Kandidaten, keine Änderung versionierter Dateien, kein Commit.

- **Kandidat:** `5eac6b64fb352982376f054a765597688d5f975b` (`fix(workspace): return cancelled outcomes for pre-engine compile limits`).
- **Vergleichsbasis:** `fbcbc768cf4b87d29f565973c6f19a3903b57187` (P3-1-Dokumentabschluss; als Vorfahr bestätigt).
- **P3-1-Produkt:** `9108b0387d9f1b82f506d8dc64e301e8522137dd` (als Vorfahr bestätigt).
- **Separater Checkout:** `/private/tmp/interlis-java-P3-2-r7` (real: `/private/tmp/interlis-java-P3-2-r7`).
- **Bericht:** `/Users/stefan/sources/interlis-java/build/review/P3-2-r7/review.md` (ausserhalb des Kandidatencheckouts).
- **Sauberkeit vorher:** `git rev-parse HEAD` = Kandidaten-SHA; `git status --porcelain=v1` leer; `git diff --check` leer.
- **Sauberkeit nachher:** HEAD unverändert; `git status --porcelain=v1` leer; `git diff --check` leer; keine Commits, kein Push. Nur ignorierte `build/`- und `.gradle/`-Verzeichnisse wurden beschrieben.
- **Eigene Quellen/Logs:** ausschliesslich unter dem ignorierten `build/review/P3-2-r7/` des Kandidaten; eine dauerhafte Kopie der neuen Proben und Rohlogs liegt unter `/Users/stefan/sources/interlis-java/build/review/P3-2-r7/{probe,raw}/`.
- **OS-Python-Sperre:** eingehalten; kein Python/pip/uv, auch nicht ad hoc. `build/reports/python-free/environment.json` belegt für diesen Lauf `gitHead=5eac6b64…`, `dirty=""` und für `python`, `python3`, `pip`, `pip3`, `pypy`, `uv` `ENOENT` bzw. `/usr/bin/python3` `EPERM`.
- **Provider:** direkte DeepSeek-API über OpenCode, Modell `deepseek/deepseek-flash` (`launch.json`), nicht OpenRouter.
- **Äusserer Transport:** Der Runner schreibt `process-result.json`/`outer-environment.json` erst nach Prozessende. Dieser Bericht nimmt keinen positiven Transport-/Manifestendstatus vorweg; die fachliche Bewertung stützt sich ausschliesslich auf die unten genannten tatsächlich ausgeführten Kommandos.
- **Unverändert gelesene Vorberichte:** `P3-2-466790c-FAIL.md` (SHA-256 `a4ec9ca4…`), `P3-2-1c19acb-FAIL.md` (`b8608148…`), `P3-2-f960cea-FAIL.md` (`81170750…`), `P3-2-3018d4b-FAIL.md` (`bae331d3…`), `P3-2-643fe12-FAIL.md` (`e0029d21…`), `P3-2-e31ad29-FAIL.md` (`084027cd…`). Alle genannten Prüfsummen wurden im Checkout nachgerechnet und stimmen.
- **Umfang:** AC-P3-05/06/07/08 vollständig, erneute Prüfung aller bisherigen Befunde F1–F6, R4-F7, R5-F6d, R5-F7 und R6-F8, neue eigene positive/negative Gegenproben, gemeinsame Budgetpfade und lokale kumulative P3-1-Regression. P3-3 bis P3-5 und G3 bleiben ausdrücklich offen.

## 1. Kurzurteil

Der Kandidat ist in den geprüften P3-2-Verträgen grün. Meine **neue eigene Probe** `P3R7CompileOutcomeProbe` liefert **24/24 PASS**, die neue Ergänzungsprobe `P3R7BridgeAnalyzeProbe` **4/4 PASS**. Die unveränderten Vorproben liefern 48/48 (R6), 31/31 (R5), 48/48 (R1), 76/76 (R3), 21/21 (R3-Grenzfälle), 119/119 (R4), 24/24 (R4 negativ), 14/14 (keyless), 5/5 (Syntaxbudget), 9/9 (FailureId), 4/4 und 1/1 (alte Budgetproben).

**R6-F8 ist geschlossen.** `Workspace.compile` gibt für frühe Capture-Abbrüche jetzt in beiden öffentlichen Overloads `CompilationOutcome.Cancelled` mit dem ursprünglichen Grund zurück, ohne Schema-/Reuseannahme. Die gleiche neue Probe ist auf dem Vorgänger `e31ad29` in genau den sechs F8-Prüfungen rot (Exception statt Ergebnis; Bridge `status: cancelled`), auf dem Kandidaten vollständig grün. Damit ist die Probe nachweislich sensitiv und der Regress unabhängig bestätigt und behoben. `checkPortable` besteht mit **3548 JS und 3548 WasmGC**.

| Kriterium | Verdikt | Kern |
|---|---|---|
| AC-P3-05 – Scheduling, Synchronisation, Quellen, Watcher | **PASS** | F1–F6, R4-F7, R5-F6d, R5-F7 in eigenen und unveränderten Proben geschlossen; Queues/Generation/Cacheannahme, atomare Edits, Resync, Quelllimits und physische Identität grün. |
| AC-P3-06 – Mehrdatei-Rename (LSP-012) | **PASS** | Offene/geschlossene Quellen, Revisionen, Disk-/Schreibschutz, Kollisionen, Originalsegmente und Clientversionen grün; keine Dateischreibungen. |
| AC-P3-07 – Completion, Tokens, Folding, Formatierung, Diagrammanker | **PASS** | Gemeinsame Sichtbarkeit/Qualifizierer/Vererbung/Sprachversion/Ersetzungsbereich/incomplete, gebundene zeilenrichtige Tokens, Folding, gemeinsame Formatierung, PLAINTEXT-Hover und Snapshotanker grün. |
| AC-P3-08 – Lifecycle, stdio, Fehlerarten | **PASS** | Lifecycle, preinit/postshutdown, Exit 0/1, stdio-Reinheit, keine Exceptiondaten und originale Compiler-FailureId grün; keine Binder-/Typsemantik im Adapter. |
| Gemeinsamer Workspace-/Bridge-Budgetvertrag | **PASS** | R6-F8 behoben: beide `Workspace.compile`-Overloads liefern `Cancelled`; Bridge `compile` bleibt `status: ok` + `outcome: cancelled`; `analyze` bleibt `status: cancelled`. |
| Kumulativ P3-1 (lokal) | **PASS** im geprüften JVM-/Portable-Umfang | `check` 1148 Fälle, `checkPortable` JS/WasmGC je 3548, SDK 9, frisches SDK-Paket, normative Conformance 577/16/0. Roher Conformance-Lauf bleibt getrennt FAIL. |
| Externe Windows-/Linux-Matrix am Kandidaten | **offen** | Nicht unabhängig ausgeführt/ausgewertet; kein Erfolg vorweggenommen. |
| P3-3/P3-4/P3-5/G3 | **offen** | Nicht Gegenstand dieses Teilreviews. |

## 2. Umgebung, Sauberkeit und tatsächlich ausgeführte Prüfungen

| Bestandteil | Tatsächlich verwendet |
|---|---|
| Betriebssystem | macOS 15.8.1, Darwin 24.6.0, arm64 |
| JDK | Temurin `25.0.3+9-LTS`, `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem` |
| Gradle | Repository-Wrapper 9.8.1 |
| Node | `v24.22.0` unter `/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node` |
| Python | nicht verwendet; kein Python/pip/uv |
| Browser | kein Chromium-/Firefox-/WebKit-/Safari-/WebImage-Lauf in diesem Review |
| Windows/Linux | keine lokale Ausführung; externe Matrix am Kandidaten nicht bewertet |

### 2.1 Tatsächliche Kommandos und Resultate

```text
git rev-parse HEAD                         -> 5eac6b64fb352982376f054a765597688d5f975b
git status --porcelain=v1                  -> leer (vor und nach allen Proben)
git diff --check                           -> leer
git merge-base --is-ancestor fbcbc76 HEAD  -> 0 (Vorfahr)
git merge-base --is-ancestor 9108b038 HEAD -> 0 (P3-1-Produkt ist Vorfahr)

JAVA_HOME=... ./gradlew check :interlis-lsp:installDist \
  -PnodeExecutable=<node> --console=plain  -> Exit 0, BUILD SUCCESSFUL in 5 s
  Aggregat: 48 Tasks (5 executed, 43 up-to-date), 1148 Testfälle, 0 Failures/Errors/Skips
  Evidenz 145, G2-Assertions 15, Verification 20; PYTHON_FREE_SOURCE_PASS 1348 inputs;
  SDK PASS snap-ca6702cc… models=1

JAVA_HOME=... ./gradlew check --rerun-tasks ... --console=plain
  -> Exit 0, BUILD SUCCESSFUL in 20 s, 45 actionable tasks: 45 executed
  -> 968 Modultests (Compiler 744, LSP 53, Workspace 23, CLI 72, Model 16,
     Projection 12, Repository 41, WASM-Bridge 7), 0 Failures/Errors/Skips
  -> zusätzlich Evidenz 145, G2-Assertions 15, Verification 20
  -> insgesamt 1148 Testfälle, 0 Failures/Errors/Skips
  -> PYTHON_FREE_SOURCE_PASS 1348 inputs; SDK PASS

JAVA_HOME=... ./gradlew checkPortable ... --console=plain
  -> Exit 0, BUILD SUCCESSFUL in 18 s, 51 Tasks (8 executed, 43 up-to-date)
  -> :interlis-wasm-teavm:jsContractTest ausgeführt:  status=passed, stabilizationCases=3548
  -> :interlis-wasm-teavm:wasmContractTest ausgeführt: status=passed, stabilizationCases=3548
  -> outcome=succeeded, runtime=teavm-js bzw. teavm-wasm-gc, Node v24.22.0

JAVA_HOME=... ./gradlew :interlis-lsp:installDist --rerun-tasks --console=plain
  -> Exit 0, BUILD SUCCESSFUL in 3 s, 17 actionable tasks: 17 executed

JAVA_HOME=... ./gradlew :interlis-lsp:test :interlis-workspace:test :interlis-compiler:test \
  sdkProcessCheck conformanceCheck sdkPackage --rerun-tasks ... --console=plain
  -> Exit 0, BUILD SUCCESSFUL in 1 m 44 s, 36 actionable tasks: 36 executed
  -> Compiler 744 / LSP 53 / Workspace 23, je 0 Failures/Errors/Skips
  -> SDK_PROCESS_PASS 9 commands
  -> conformanceAssessment: selected=593 result=PASS rawExit=1
     {"PASS":577,"XFAIL":16,"FAIL":0,"XPASS":0,"ERROR":0,"TOTAL":593}
  -> sdkPackage frisch ausgeführt
```

Der rohe Referenzlauf bleibt getrennt **FAIL**: `build/reports/conformance/conformance-summary.json`
nennt `referenceResult=FAIL` mit `PASS=577, XFAIL=1, FAIL=15, ERROR=0` bei `TOTAL=593`.
Die normative Projektauswertung mit den freigegebenen 16 Ausnahmen ist `PASS` (577/16/0).
Es findet keine Umetikettierung des Rohlaufs statt.

## 3. Eigene neue ausführbare positive/negative Gegenproben

### 3.1 Neue Hauptprobe `P3R7CompileOutcomeProbe` (24 Prüfungen, 24/24 PASS, Exit 0)

Quelle: `build/review/P3-2-r7/probe/P3R7CompileOutcomeProbe.java` (SHA-256
`dcf2c8965a65b1fbf6730d051c143c1257f3dce61951e9cc0db4f8e146377936`);
Rohlog: `build/review/P3-2-r7/r7-compile-outcome.log`.

Die Probe spricht **nur öffentliche Verträge** an (`Workspace`, `CompilerOptions`,
`AnalysisBudget`, `RunContextFactory`, `BridgeDispatcher`) und wiederholt keine
Projekt-JUnit-Methode. Sie enthält positiven Nachweis und negative Kontrollen:

- **Tokenbudget über beide Compile-Overloads:** `Workspace.compile(request, token)` und
  `Workspace.compile(request, context, token)` liefern je
  `Cancelled[analysis=Optional.empty, reason=RESOURCE_LIMIT]`; keine Exception;
  `acceptedAnalyses=0`, `cachedSyntaxEntries=0`, Quelle/Revision unverändert.
- **Getrennte Analysepfade bleiben werfend:** `Workspace.analyze` und
  `Workspace.capture` liefern weiterhin `OperationCancelledException(RESOURCE_LIMIT)`.
- **Erholung:** Nach `configure` mit dem Standardbudget kompiliert dieselbe Sitzung
  `Succeeded`; genau eine Analyse wird angenommen.
- **Quelllängenbudget:** Ein nachträglich verkleinertes `maxSourceUtf16Length` führt
  ebenfalls zu `Cancelled(RESOURCE_LIMIT)` ohne Annahme und ohne Revisionsänderung.
- **Externer Abbruch:** Ein vorab abgebrochenes Token ergibt
  `Cancelled(SUPERSEDED)` statt einer Exception; keine Annahme.
- **Negative Sprachkontrolle:** Eine gewöhnliche Quellfehler-Eingabe bleibt
  `Rejected` mit `RejectionSummary[reasons=[SOURCE_ERRORS]]` und wird nicht als
  Cancelled umetikettiert.
- **API-Fehler bleiben getrennt:** `null`-Token und `null`-Request ergeben
  `NullPointerException`; eine veraltete Workspace-Revision ergibt
  `UpdateRejectedException` – nicht Cancelled.
- **Bridge (öffentliches Protokoll):** 4097-stelliges numerisches Literal ergibt
  `{"status":"ok","result":{"outcome":"cancelled","cancellation":{"reason":"RESOURCE_LIMIT"}}}`;
  nach Korrektur der Quelle kompiliert dieselbe Sitzung `succeeded`; eine
  gewöhnliche fehlerhafte Quelle bleibt `status: ok` + `outcome: rejected`.

Auszug der tatsächlichen Ausgabe:

```text
PASS compile(convenience) -> Cancelled(RESOURCE_LIMIT) without partial analysis :: Cancelled[analysis=Optional.empty, reason=RESOURCE_LIMIT]
PASS no analysis accepted after the cancelled compile :: WorkspaceMetrics[parses=1, ..., acceptedAnalyses=0, cachedSyntaxEntries=0, ..., cancellations=1]
PASS compile(context) -> Cancelled(RESOURCE_LIMIT) without partial analysis :: Cancelled[analysis=Optional.empty, reason=RESOURCE_LIMIT]
PASS analyze keeps throwing OperationCancelledException(RESOURCE_LIMIT) :: operation cancelled: RESOURCE_LIMIT
PASS capture keeps throwing OperationCancelledException(RESOURCE_LIMIT) :: operation cancelled: RESOURCE_LIMIT
PASS same session compiles successfully after restoring the default budget :: Succeeded[...]
PASS early source-length capture abort also becomes Cancelled(RESOURCE_LIMIT) :: Cancelled[analysis=Optional.empty, reason=RESOURCE_LIMIT]
PASS external cancellation is a Cancelled outcome with the original reason :: Cancelled[analysis=Optional.empty, reason=SUPERSEDED]
PASS ordinary source errors stay Rejected with a rejection summary :: Rejected[..., rejection=RejectionSummary[reasons=[SOURCE_ERRORS], ...]]
PASS null cancellation token is an early API error, not a Cancelled outcome :: java.lang.NullPointerException: token
PASS null compile request is an early API error, not a Cancelled outcome :: java.lang.NullPointerException: request
PASS stale workspace revision keeps its separate API error :: guru.interlis.workspace.UpdateRejectedException: workspace revision changed
BRIDGE_COMPILE={"status":"ok","result":{"outcome":"cancelled","cancellation":{"reason":"RESOURCE_LIMIT"}}}
PASS bridge session recovers after the budget cancellation :: {"outcome":"succeeded",...}
PASS bridge keeps language errors as rejected outcomes :: {"outcome":"rejected",...}
RESULT checks=24 failures=0
```

### 3.2 A/B-Negativkontrolle derselben Probe am Vorgänger `e31ad29`

Dieselbe unveränderte Probe, kompiliert gegen die Distribution des Vorgängercheckouts
`/private/tmp/interlis-java-P3-2-r6-a7f24a7` (`e31ad292…`), liefert
**24 Prüfungen, 6 Fehlschläge, Exit 1** (`r7-compile-outcome-parent-e31ad29.log`):

```text
THROWN token budget, convenience overload :: OperationCancelledException: operation cancelled: RESOURCE_LIMIT
FAIL compile(convenience) -> Cancelled(RESOURCE_LIMIT) ...
THROWN token budget, explicit RunContext overload :: OperationCancelledException: operation cancelled: RESOURCE_LIMIT
FAIL compile(context) -> Cancelled(RESOURCE_LIMIT) ...
THROWN source length budget :: OperationCancelledException: operation cancelled: RESOURCE_LIMIT
FAIL early source-length capture abort also becomes Cancelled(RESOURCE_LIMIT) ...
THROWN pre-cancelled token :: OperationCancelledException: operation cancelled: SUPERSEDED
FAIL external cancellation is a Cancelled outcome with the original reason ...
BRIDGE_COMPILE={"status":"cancelled","cancellation":{"reason":"RESOURCE_LIMIT"}}
FAIL numeric budget compile keeps protocol status ok ...
FAIL numeric budget compile is outcome=cancelled RESOURCE_LIMIT ...
RESULT checks=24 failures=6
```

Damit ist die Probe sensitiv für genau den F8-Regress; die übrigen 18 Prüfungen
(Sprachfehler, API-Fehler, Recovery) sind auf beiden Ständen gleich. Der Kandidat
besteht 24/24, der Vorgänger nicht.

### 3.3 Neue Ergänzungsprobe `P3R7BridgeAnalyzeProbe` (4 Prüfungen, 4/4 PASS, Exit 0)

Quelle: `build/review/P3-2-r7/probe/P3R7BridgeAnalyzeProbe.java` (SHA-256
`4f14dd2259761c18e1515bed53d65c294656610683290f43790665c61f786b05`);
Rohlog: `build/review/P3-2-r7/r7-bridge-analyze.log`.

Sie sichert die zweite Hälfte des gemeinsamen Budgetpfads ab: `analyze` über die
öffentliche Bridge behält die sitzungsweite Abbruchhülle
`{"status":"cancelled","cancellation":{"reason":"RESOURCE_LIMIT"}}` und täuscht
kein abgeschlossenes Compile-Ergebnis vor. Zusammen mit §3.1 ist damit belegt:

| Operation | Budget-/Abbruchverhalten | Deckung |
|---|---|---|
| `Workspace.compile` (beide Overloads) | Ergebnis `Cancelled(reason)`, keine Exception | neue Probe 24/24; A/B rot auf `e31ad29` |
| `Workspace.analyze` / `capture` | weiterhin `OperationCancelledException(reason)` | neue Probe; `P3CaptureBudgetTest` |
| Bridge `compile` | `status: ok` + `outcome: cancelled` | neue Probe; Portable 3548 JS/WasmGC |
| Bridge `analyze` | `status: cancelled` + `reason` | neue Ergänzungsprobe 4/4 |
| LSP `interlis.compile` (abgetrennter Capturepfad) | RequestCancelled `-32800`/`RESOURCE_LIMIT` | unveränderte R6-Probe 48/48 |

Damit sind die berührten gemeinsamen Budgetpfade in beiden Richtungen geprüft,
ohne einen Adapter als zweite Semantik zu verwenden.

## 4. R6-F8 – Ursache, Korrektur und unabhängige Bestätigung

### 4.1 Vertrag

Kapitel 07 §2.5 verlangt: „Ein ausgeschöpftes semantisches `AnalysisBudget` führt
bei `analyze` zu `OperationCancelledException(RESOURCE_LIMIT)` und bei `compile`
zu `Cancelled(RESOURCE_LIMIT)` mit höchstens einer ausdrücklich unvollständigen
Teilsicht.“ Der bestehende P3-1-Portable-Vertrag und
`NumericLiteralBudgetContractTest` verlangen für den Budgetfall ein
abgeschlossenes Compile-Ergebnis (`status: ok`, `outcome: cancelled`), keine
Protokoll-Cancellation. Kapitel 04 (Zeilen 167–172) erlaubt dem Capture, frühe
Grenzen als `RESOURCE_LIMIT` zu melden; Kapitel 07 §2.6 verlangt, dass
`Cancelled` keinen vollständigen Analyse-/Abhängigkeitscommit auslöst.

### 4.2 Beobachtung am Vorgänger `e31ad29`

`DefaultWorkspace.capture` warf bei `syntax.coverage().budgetExceeded()` eine
`OperationCancelledException`, die ungefiltert aus `Workspace.compile` entwich,
bevor die Engine ihren `Cancelled`-Pfad erreichte. Die Bridge antwortete deshalb
mit `status: cancelled`; der Portablefall `stabilization-137` war rot. Meine
A/B-Probe reproduziert das am Vorgänger mit 6 Fehlschlägen (§3.2).

### 4.3 Korrektur am Kandidaten

`interlis-workspace/src/main/java/guru/interlis/workspace/DefaultWorkspace.java`
fängt in **beiden** öffentlichen `compile`-Overloads `OperationCancelledException`
und liefert `new CompilationOutcome.Cancelled(Optional.empty(), cancelled.reason())`.
`acceptOutcome` wird dabei nicht erreicht; es entsteht weder ein Schema noch eine
Reuse-/Cacheannahme. Nullprüfungen für `request`, `context` und `token` erfolgen
vor dem `try`-Block. `analyze`/`capture` bleiben unverändert werfend.
`P3CaptureBudgetTest` prüft jetzt zusätzlich beide Compile-Ergebnisse explizit,
die unveränderte Quelle/Revision und `acceptedAnalyses==0`.

Meine Probe bestätigt zusätzlich:

- kein `schema`/`snapshotId` im Bridge-Ergebnis des Abbruchs;
- `cachedSyntaxEntries=0` und `acceptedAnalyses=0` nach wiederholten Abbrüchen;
- Erholung in derselben Sitzung nach Budgetkorrektur;
- `Cancelled(SUPERSEDED)` bei externem Token-Abbruch;
- Quelllängen- und Tokenbudget verhalten sich gleich;
- gewöhnliche Sprachfehler bleiben `Rejected`;
- `null`-Argumente und Revisionskonflikte bleiben getrennte API-Fehler.

`checkPortable` ist mit 3548 JS und 3548 WasmGC grün; der frühere Portablefall 137
ist damit wieder ein abgeschlossenes `status: ok`/`outcome: cancelled`.

### 4.4 Abgrenzung

- Der LSP-`interlis.compile`-Befehl verwendet bewusst den abgetrennten
  Capture-/Engine-Pfad (`AnalysisScheduler.compile`), nicht `Workspace.compile`.
  Er liefert deshalb weiterhin `-32800`/`RESOURCE_LIMIT`; das ist durch die
  unveränderte R6-Probe 48/48 und `P3PresentationConfigurationTest` gedeckt und
  kein Widerspruch zum öffentlichen Workspace-Compile-Vertrag.
- Bridge `analyze` behält die sitzungsweite `status: cancelled`-Hülle; Bridge
  `compile` liefert ein abgeschlossenes Ergebnis. Beide Pfade wurden getrennt
  geprüft (§3.1/§3.3).

## 5. Unveränderte Wiederholung der bisherigen Reviewerproben

Die Quellen wurden bytegleich aus der R6-Ablage übernommen
(`probe-unmodified-sha256.txt`), im Kandidatencheckout gegen die frisch gebaute
Distribution ausgeführt und nicht editiert.

| Probe | Beobachtung | Wertung |
|---|---|---|
| `r6-main.mjs` | RESULT checks=48 failures=0, Exit 0 | PASS |
| `alias-edge-r5-unmodified.mjs` | RESULT checks=31 failures=0, Exit 0 | PASS |
| `probe-lsp-r1-unmodified.mjs` | 48 PASS, Exit 0 | PASS |
| `independent-r3-unmodified.mjs` | 76 PASS + 5 Beobachtungen, Exit 0 | PASS |
| `sync-edge-r3-unmodified.mjs` | 21 PASS, Exit 0 | PASS |
| `independent-r4-unmodified.mjs` | 119 PASS, Exit 0 | PASS |
| `negative-r4-unmodified.mjs` | 24 PASS, Exit 0 | PASS |
| `KeylessAliasProbe.java` (+ `NoKeyProvider`) | 14 PASS, Exit 0 | PASS |
| `SyntaxBudgetProbe.java` | 5 PASS, Exit 0 | PASS |
| `ReviewFailureR4.java` | 9 PASS, Exit 0 | PASS |
| `BridgeBudgetProbe.java` | 4 PASS, Exit 0 | PASS |
| `WorkspaceCompileBudgetProbe.java` | 1 PASS, Exit 0 | PASS |
| `finding-f6-r5-unmodified.mjs` | 7/8, Exit 1 | bekannte fehlerhafte Probe-Assertion, kein Produktbefund (§8.1) |

Die R6-Hauptprobe enthält F6d `Deleted`/`Changed` über den überlebenden Alias,
R5-F7-Resynchronisation in beiden URI-Richtungen für abgelehntes Open und Change,
R4-F7-Tokenbudget, F5-Quelllimit, AC-P3-07 und AC-P3-08. Die R3/R4-Proben decken
Symlinkroot, Hardlink-/Case-Alias, geschlossene Quellen, Importinvalidierung,
Rename-Revisionen, Schreibschutz und Cancellation ab.

## 6. Kriterienmatrix AC-P3-05 bis AC-P3-08

### 6.1 AC-P3-05 – Dokumentnotifications, neueste Generation, Abbruch, bedingte Annahme, Quellen/Watcher

| Anforderung | Beobachtung | Verdikt |
|---|---|---|
| INC-018/022 – veraltete Ergebnisse, Capture/paralleles Update, bedingte Annahme | `P3AnalysisSchedulingTest` (blockierte Engine, Supersedierung, Shutdown); unveränderte R4-Probe 119/119 (30 Vollersetzungen), R3-Grenzprobe 21/21 (40 Vollersetzungen); neue Compile-Probe: `acceptedAnalyses=0` nach Abbrüchen | PASS |
| INC-019 – Konfigurations-/Resolverwechsel als Workspace-Änderung | `P3PresentationConfigurationTest` (atomare Konfiguration, ungültige Werte lassen alte Konfiguration stehen); R4 `allowedLanguages` 2.3/2.4; eigene Budget-/Konfigurationserholung | PASS |
| LSP-003 – Watcher überschreiben offenen Clienttext nicht | R6-Hauptprobe (F6d mit ungespeichertem `Root`-Text), R5-Alisprobe 31/31, keylose Probe 14/14, `P3ProjectRenameTest` | PASS |
| LSP-004 – atomare mehrteilige `didChange` | `P3DocumentSynchronizationTest` (ungültiger Batch atomar, Vollersatz, CRLF/UTF-16, Spaltenbegrenzung); R6-F5: oversize `ContentModified` ohne Payload-Echo, Vollersatz resynchronisiert | PASS |
| LSP-006 – leere Diagnostik ersetzt frühere; Clientversionen auch bei No-op | `P3DocumentSynchronizationTest` (`diagnosticReplacementCarriesTheExactClientVersionIncludingNoOpChanges`); R1/R3/R4-Proben | PASS |
| LSP-011 – Abbruch in Engine/Worker, Sitzung danach nutzbar | `LspProtocolCancellationTest`, `P3AnalysisSchedulingTest`; R6-Tokenbudget: `-32800`, danach dieselbe Sitzung wieder nutzbar; externe Cancellation als `Cancelled` | PASS |
| LSP-015 – Repositoryänderung aktualisiert abhängige offene Analyse | R3 A32/A33, R4 S0 (realer Alias), R5-Alisprobe; `P3ProjectRenameTest.aClosedDependencyRefresh…` | PASS |
| Physische Identität, Alias, Primärlöschung, Resync | F6a–F6d in R1/R3/R4/R5/R6-Proben; keylose Probe Deleted/Changed je 14/14; R6-Resync beide URI-Richtungen und beide Ablehnungsarten; primäre Löschung behält Core-ID und geschlossene Aliasadresse | PASS |
| Quelllimits/Budgets im LSP | Syntaxbudgetprobe 5/5; R6-Tokenbudget `-32800/RESOURCE_LIMIT` ohne Notificationstacktrace; Quelllimit `-32801`/`SOURCE_LIMIT_EXCEEDED`; R4 negativ 24/24 | PASS |
| Begrenzte Queues, Arbeit ausserhalb Owner, neueste Generation | Inspektion `AnalysisScheduler` (Owner 256, Analyse 2, Compile 16, Query 128; abgetrennte Executors), `InterlisWorkspaceService` (I/O 2/32); `P3AnalysisSchedulingTest`; parallele Serverprozesse in den Proben | PASS |

**AC-P3-05: PASS.**

### 6.2 AC-P3-06 – Workspace-weites Rename (LSP-012)

| Prüfung | Beobachtung | Verdikt |
|---|---|---|
| Mehrdatei-Rename offen/geschlossen | R6-F6d: zwei Dokumente, offene exakte Version, geschlossene `null`; R5/R4-Proben; `P3ProjectRenameTest` | PASS |
| `documentChanges`/Clientversionen | Versioned Edits in allen Proben; ohne Capability `changes`; `P3ProjectRenameTest` | PASS |
| Diskhash/Pfadziel/Schreibrecht | `P3ProjectRenameTest`: unangekündigte Diskänderung `ContentModified`, Schreibschutz kontrollierter Fehler, keine Datei geschrieben; R4 S10/R5 | PASS |
| Kollision/Originalsegmente/Revisionen | `P3CompletionScopeContractTest` (äussere/geerbte Kollisionen); `P3ProjectRenameTest`; fehlende Revision `CONTENT_MODIFIED`; Originalsegmentprüfung im Plan | PASS |
| Fehlende/unvollständige Quelle | `LspFeatureTest`, `P3CompletionScopeContractTest`, `P3ProjectRenameTest`; kein vollständiger Plan | PASS |
| Server schreibt keine Rename-Dateien | Proben vergleichen Dateiinhalte vor/nach; keine Schreiboperation beobachtet | PASS |

**AC-P3-06: PASS.**

### 6.3 AC-P3-07 – gemeinsame Completion/Tokens/Folding/Formatierung/Diagrammanker

| Prüfung | Beobachtung | Verdikt |
|---|---|---|
| Scope, Import, Vererbung | R6-Hauptprobe: `localName`/`OuterName`, kein fremdes Mitglied; `P3CompletionScopeContractTest`; R1/R3/R4 | PASS |
| Qualifizierer/Member | R4 S11: `Base.` liefert nur `Common`; `visibleSymbols`/`membersOf` in Kapitel 05/16 | PASS |
| Sprachversion/Keywords | `P3CompletionScopeContractTest`: `MULTICOORD` nur 2.4; Version in Completion | PASS |
| Ersetzungsbereich/InsertText/Incomplete | `P3CompletionProtocolTest` (100-Trunkierung, `isIncomplete`, ganzer Name); R4-Probe; R6 | PASS |
| Gebundene Tokens/Zeilen | R6: nichtleere fünfwertige Payload; R4: `inherited` als `property`, Kommentar als `comment`, zeilenbegrenzte UTF-16-Spannen; `P3PresentationConfigurationTest` | PASS |
| Folding | R6: Deklarationsbereiche; R4: mehrzeiliger Kommentar; `P3PresentationConfigurationTest` | PASS |
| Formatierung | R6: `tabSize=3` wirksam; ungültige Quelle Fehler statt scheinbarer Erfolg; `P3PresentationConfigurationTest` | PASS |
| Hover/Diagrammanker | R6: Hover `plaintext`, Diagramm mit `snapshotId`; R4: veralteter Snapshot `InvalidParams`; `LspErrorContractTest` | PASS |

**AC-P3-07: PASS.**

### 6.4 AC-P3-08 – Lifecycle, stdio, Fehlerarten

| Anforderung | Beobachtung | Verdikt |
|---|---|---|
| LSP-001 – dünner Adapter, keine zweite Binder-/Typsemantik | Inspektion: keine ANTLR-/Binder-/GraphBuilder-Nutzung in `interlis-lsp/src/main`; Modulabhängigkeiten nur Workspace/Compiler/Model/Projection/Repository/LSP4J/Gson | PASS |
| LSP-002 – `initialize`/`initialized`/`shutdown`/`exit` | `LspProcessTest` (echter stdio, preinit/postshutdown, Exit auch bei offener Eingabe); R1/R6: Exit 0 nach Shutdown, 1 ohne Shutdown | PASS |
| LSP-008 – stdio, stdout nur Protokoll | Alle Proben parsen ausschliesslich Frames; stderr ohne Stacktrace; `LspStdoutPurity`-Pfad in `LspProcessTest`; R6-Tokenbudget ohne Notificationstacktrace | PASS |
| LSP-009 – keine globalen Compile-Locks/Listener | Scheduler-Inspektion (keine globalen Locks, keine Logger-Listener); zahlreiche unabhängige Serverprozesse | PASS |
| LSP-016 – Transport-/Compiler-/interne Fehler unterscheidbar | R6: `-32800/RESOURCE_LIMIT`, `-32801/ContentModified`, `-32002`, `-32600`, `-32601`; `ReviewFailureR4` 9/9 mit originaler FailureId und `data=null`; `LspErrorContractTest` | PASS |
| Keine Adapterfänge interner Compilerklassen | `grep` über LSP/Bridge/CLI/Workspace: kein `AnalysisBudgetExceededException` in Adaptern | PASS |

**AC-P3-08: PASS.**

## 7. Kumulativ P3-1 (lokal)

| Prüfung | Beobachtung | Verdikt |
|---|---|---|
| `check` frisch | 45/45 Tasks ausgeführt; 1148 Testfälle, 0 Failures/Errors/Skips; 968 Modultests + 145 Evidenz + 15 G2 + 20 Verification; `PYTHON_FREE_SOURCE_PASS 1348`; SDK PASS | PASS |
| `checkPortable` | JS 3548 und WasmGC 3548, beide `status=passed`, `outcome=succeeded` | PASS |
| `sdkProcessCheck` | 9 Kommandos PASS | PASS |
| `sdkPackage` | im frischen Lauf ausgeführt; SDK-ZIP erzeugt | PASS |
| `conformanceCheck` | normativ 577 PASS / 16 XFAIL / 0 FAIL / 0 XPASS / 0 ERROR; Rohlauf getrennt FAIL 577/1/15 | PASS (normativ), Rohlauf FAIL |
| P3-1-CLI-/Formatter-Verträge | `interlis-cli/src/main` im Kandidaten-Diff unverändert; CLI 72 Tests grün | PASS im JVM-Umfang |
| Browser/Safari/WebImage/Windows/Linux | in diesem Review nicht ausgeführt | offen (P3-3/P3-5/G3) |

Die historischen 3548er-Browser-/Safari-/WebImage-Nachweise werden nicht als
aktuelle Prüfung übernommen. Der rohe Conformance-Lauf bleibt ausdrücklich FAIL.

## 8. Sämtliche bisherigen Befunde – einzeln

| Befund | Status | Nachweis |
|---|---|---|
| F1 symlinkter Projektroot leer | **geschlossen** | R3 A6; R4 S0/F1/F2 (symlinkter Root, logische SourceIds, kein Stacktrace) |
| F2 Alias wird zweite Quelle | **geschlossen** | R3 A7/A34–A36; R4 S1/S3; R5 31/31; R6 48/48 |
| F3 Ressourcenlimit als interner Fehler | **geschlossen** | Syntaxbudgetprobe 5/5; R4 B1/B2/C1; R6-Token-/Quelllimit; neue Probe (analyze/capture werfen typisiert) |
| F4 FailureId verloren | **geschlossen** | `ReviewFailureR4` 9/9; originale FailureId, `data=null` |
| F5 übergrosser Clienttext still verworfen | **geschlossen** | R4 A47–A50; R6-F5 (`ContentModified`, Betriebsmeldung `SOURCE_LIMIT_EXCEEDED`, Vollersatz resynchronisiert) |
| F6 physische Identität bei Discovery/unbekannten `didOpen`-Aliassen | **geschlossen** | R3 E1/E2; R4 S1/S3; keylose Probe 14/14; R6 48/48 |
| R5-F6d Primärlöschung über überlebenden Hardlink-Alias | **geschlossen** | R6 Deleted/Changed: `succeeded`/`modelCount=2`, Quellenzahl 2, Renameadresse = Alias; keylose Probe 14/14 |
| R4-F7 Tokenbudget | **geschlossen** | R6-Tokenbudget: `-32800/RESOURCE_LIMIT`, kein Stacktrace, Erholung; `P3PresentationConfigurationTest`; neue Compile-Probe |
| R5-F7 Resynchronisation nach URIwechsel | **geschlossen** | R6: abgelehntes Open/Change, Alias-first und Canonical-first, gültiges Wiederöffnen hebt Sperre auf, anderer offener Client bleibt autoritativ |
| R6-F8 `Workspace.compile` wirft statt `Cancelled` | **geschlossen** | Neue Probe 24/24; A/B 6 Fehlschläge auf `e31ad29`; alte `BridgeBudgetProbe`/`WorkspaceCompileBudgetProbe` jetzt PASS; Portable 3548/3548 |
| Fehlerhafte letzte Assertion in `finding-f6.mjs` | **kein Produktbefund** | 7/8; flache Ausgabe `Root,T,C,value` enthält genau eine Root-Deklaration; Original bleibt unverändert, Semantik nicht angepasst |
| Review5 Transportexit1/Manifest failed | **Prozesshinweis, kein Abnahmenachweis** | Bericht/Proben unverändert; Reproduktionen wurden durch eigene REDs bestätigt und sind jetzt grün |
| Roher Conformance-Lauf FAIL | **offen, getrennt** | Roh 577/1/15 FAIL; normative Auswertung 577/16/0 PASS; keine Umetikettierung |
| E3 verschachtelte Verzeichnis-Symlinks | **Beobachtung, kein MUSS** | R3 E3 `MISSING_INPUT`; Kapitel 07 verlangt ausdrücklich nur den symlinkten Workspace-Root |

## 9. Zusätzliche Beobachtungen und Restrisiken

1. **Kapitel 10 – Formulierung `status: cancelled`:** Zeile 288 nennt für ein
   „während der semantischen Analyse ausgeschöpftes `AnalysisBudget`“ die
   sitzungsweite Hülle `status: cancelled`. Die Implementierung erfüllt das für
   `analyze` (eigene Ergänzungsprobe) und für `compile` den spezifischeren
   Ergebnisvertrag aus Kapitel 07 §2.5 / P3-1 (`status: ok` + `outcome: cancelled`).
   Das ist mit der operationsbezogenen Lesart konsistent; eine künftige
   redaktionelle Klarstellung in Kapitel 10 (explizit `analyze` vs. `compile`)
   bleibt empfehlenswert, ist aber kein reproduzierbarer MUSS-Befund und keine
   Abschwächung.
2. **Metrik `cancellations`:** Bei einem Capture-Abbruch zählt der neue
   Compile-Catch einmal. Für den separaten `ResolutionCancelled`-Pfad zählt das
   Capture ebenfalls; eine doppelte Zählung bei genau diesem Pfad ist theoretisch
   möglich, wurde in diesem Review nicht beobachtet und ist nicht Teil des
   Abnahmekriteriums. Keine Aussage ohne Reproduktion.
3. **G2-Evidenzindex:** Die Änderungen in `docs/evidence/stabilization/` und
   `testdata/g2/` sind auf Quellspannen, Zeilennummern, Hashes und die Zahl neuer
   Testmethoden (731→738) beschränkt. `docs/g2/`, `docs/evidence/p3/` und die
   normativen G2-Profile sind unverändert; keine Erwartung wurde abgeschwächt.
4. **Portable-Vertrag:** Die einzige Änderung an
   `stabilization-contract.cjs` stammt aus dem älteren Kandidaten `466790c`
   (Completion-Abfrageposition `0` → Position in `own:TEXT`); die Erwartung
   „`inherited` ist sichtbar“ bleibt unverändert. Im aktuellen Kandidaten wurde
   die 3548er-Suite nicht verändert.
5. **Elterncheckout:** Die unabhängige uncommittete Browser-/Bridgeclientarbeit
   des Elterncheckouts ist nicht Bestandteil dieses Kandidaten und wurde nicht als
   P3-2-Abnahme verwendet. Der Kandidatencheckout ist sauber.

## 10. Nicht ausgeführt / Grenzen

- Kein `checkBrowser`, kein `releaseCheck`, kein `performanceCheck`, kein
  Chromium-/Firefox-/WebKit-/Safari-/WebImage-Lauf, kein `npm ci`. Diese Pfade
  gehören zu P3-3/P3-5/G3.
- Keine lokale Linux-x64-/Windows-x64-Ausführung und keine unabhängige Auswertung
  der neuen GitHub-Matrix am Kandidaten. Ein Erfolg wird nicht vorweggenommen; die
  finale Plattformmatrix bleibt P3-5.
- Die keylose Probe verwendet einen testweisen Standardprovider mit
  `fileKey()==null`; sie ist kein echter Windows-Lauf.
- Keine Heap-/Retention-Messung für abgelehnte Riesentexte; beobachtet ist das
  Verhalten und der Codezustand.
- Die unveränderte `finding-f6.mjs`-Probe bleibt mit 7/8 rot; die einzelne rote
  Assertion ist ein Probe-Fehler, kein Produktbefund.
- Der äussere Transport-/Manifestendstatus wird durch den Runner nach Prozessende
  geschrieben und hier nicht vorweggenommen.

## 11. Gesamturteil

**P3-2: PASS.**

- AC-P3-05: **PASS** – F1–F6, R4-F7, R5-F6d und R5-F7 sind in eigenen und
  unveränderten Proben geschlossen; Scheduling, atomare Edits, Quelllimits,
  physische Identität und Resync sind grün.
- AC-P3-06: **PASS**.
- AC-P3-07: **PASS**.
- AC-P3-08: **PASS**.
- R6-F8: **geschlossen** – beide öffentlichen `Workspace.compile`-Overloads liefern
  `Cancelled` mit ursprünglichem Grund, ohne Schema-/Reuseannahme; die neue Probe
  ist auf dem Vorgänger rot und auf dem Kandidaten grün; Portable JS/WasmGC 3548.
- Kumulativ P3-1: **PASS** im geprüften JVM-/Portable-Umfang (`check` 1148,
  Portable 3548/3548, SDK 9, SDK-Paket, normative Conformance 577/16/0); der rohe
  Conformance-Lauf bleibt getrennt FAIL.
- **G3 bleibt offen; P3-3, P3-4 und P3-5 bleiben offen.** Die externe
  Windows-/Linux-Matrix am Kandidaten sowie Browser-/Safari-/WebImage-Nachweise
  sind nicht Gegenstand dieses P3-2-Reviews und werden nicht als Erfolg
  vorweggenommen.

Es gibt keinen offenen reproduzierbaren MUSS-Befund gegen AC-P3-05 bis AC-P3-08
oder den gemeinsamen Budgetvertrag. Der Kandidat wurde nicht repariert, keine
Erwartung abgeschwächt und kein G3-Abschluss ausgesprochen.

## 12. Rohpfade

Neue Proben und Rohlogs im Kandidatencheckout (ignoriert):

- `build/review/P3-2-r7/probe/P3R7CompileOutcomeProbe.java`
- `build/review/P3-2-r7/probe/P3R7BridgeAnalyzeProbe.java`
- `build/review/P3-2-r7/r7-compile-outcome.log` (24/24)
- `build/review/P3-2-r7/r7-compile-outcome-after-installDist.log` (24/24, frische Distribution)
- `build/review/P3-2-r7/r7-compile-outcome-parent-e31ad29.log` (24 Prüfungen, 6 Fehlschläge)
- `build/review/P3-2-r7/r7-bridge-analyze.log` (4/4)
- `build/review/P3-2-r7/check-install.log`
- `build/review/P3-2-r7/check-rerun.log`
- `build/review/P3-2-r7/checkPortable.log`
- `build/review/P3-2-r7/cumulative-rerun.log`
- `build/review/P3-2-r7/installDist-rerun.log`
- `build/review/P3-2-r7/probe-unmodified-sha256.txt`
- unveränderte Vorproben: `build/review/P3-2-r7/probe-unmodified/`

Dauerhafte Kopie ausserhalb des Kandidaten:

- `/Users/stefan/sources/interlis-java/build/review/P3-2-r7/probe/`
- `/Users/stefan/sources/interlis-java/build/review/P3-2-r7/raw/`

Projektartefakte:

- `build/reports/conformance/conformance-summary.json` (normativ PASS, Referenz FAIL)
- `interlis-wasm-teavm/build/js-contract-result.json` (3548, passed)
- `interlis-wasm-teavm/build/wasm-contract-result.json` (3548, passed)
- `build/releases/interlis-java-sdk-0.2.0-SNAPSHOT.zip`
- `build/reports/python-free/environment.json`
