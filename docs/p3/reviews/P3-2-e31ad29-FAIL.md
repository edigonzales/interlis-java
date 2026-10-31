# Unabhängiger P3-2-Kandidatenreview (Runde 6) – LSP und gemeinsame Sprachabfragen

**Status: FAIL für P3-2. Kein P3-2-PASS, kein G3-Abschluss.**

- Kandidat: `e31ad29251eba60f1c7a36ecdb993fd1356eff41` (`docs(p3): refresh relocated syntax profile evidence`; Produktfix im Vorgänger `a7f24a7`).
- Vergleichsbasis: `fbcbc768cf4b87d29f565973c6f19a3903b57187` (P3-1-Dokumentabschluss).
- P3-1-Produkt: `9108b0387d9f1b82f506d8dc64e301e8522137dd`.
- Separater Checkout: `/tmp/interlis-java-P3-2-r6-a7f24a7` (real: `/private/tmp/interlis-java-P3-2-r6-a7f24a7`).
- Bericht: `/Users/stefan/sources/interlis-java/build/review/P3-2-r6/review.md` (ausserhalb des Checkouts).
- Sauberkeit vorher: `git rev-parse HEAD` lieferte exakt den Kandidaten; `git status --porcelain=v1` war leer; `git diff --check` war leer.
- Sauberkeit nachher: `git rev-parse HEAD` unverändert; `git status --porcelain=v1` leer; `git diff --check` leer. Keine Änderungen an versionierten Dateien, keine Commits, kein Push.
- Eigene Quellen, Proben und Rohlogs ausschliesslich unter dem ignorierten `build/review/P3-2-r6/` des Checkouts.
- OS-Python-Sperre eingehalten: kein Python/pip/uv, auch nicht ad hoc. Verwendet wurden Java 25.0.3 (Temurin), Node 24.22.0, der Gradle-Wrapper 9.8.1 und Shell.
- Provider: direkte DeepSeek-API über OpenCode, nicht OpenRouter.
- Unverändert gelesene Vorberichte: `docs/p3/reviews/P3-2-466790c-FAIL.md`, `P3-2-1c19acb-FAIL.md`, `P3-2-f960cea-FAIL.md`, `P3-2-3018d4b-FAIL.md`, `P3-2-643fe12-FAIL.md`.
- Umfang: AC-P3-05 bis AC-P3-08 vollständig, erneute Prüfung aller bisherigen Befunde F1–F6, R4-F7, R5-F6d und R5-F7, neue eigene positive/negative Gegenproben, gemeinsame Budgetpfade sowie die lokale kumulative P3-1-Regression. P3-3 bis P3-5 und G3 bleiben ausdrücklich offen.

## 1. Kurzurteil

Die LSP-Kernpfade am Kandidaten sind in echten stdio-Prozessen und in den öffentlichen Serviceverträgen grün. Meine eigene neue Hauptprobe liefert 48/48, die neue keylose Probe 14/14, die neue Syntaxbudgetprobe 5/5. Die unveränderten Vorproben liefern 119/119 (R4), 24/24 (R4 negativ), 76/76 (R3), 31/31 (R5 Alias-/Resyncprobe) und 21/21 (R3 Grenzprobe). F1–F6, R4-F7 (Tokenbudget), R5-F6d (Primärlöschung über den überlebenden Alias) und R5-F7 (Resynchronisation über eine andere URI) sind damit in ihren Reproduktionen geschlossen.

Dennoch ist P3-2 nicht abnahmefähig. **Neuer, reproduzierbarer MUSS-Regress F8 im gemeinsamen Budget-/Compile-Pfad:** `Workspace.capture` bricht seit dem Kandidaten bei einem konsistenten Syntaxsnapshot mit `budgetExceeded` mit `OperationCancelledException(RESOURCE_LIMIT)` ab (`DefaultWorkspace.java:287`). Dadurch erreicht `Workspace.compile` den Engine-Pfad nicht mehr, der daraus vertragsgemäss `CompilationOutcome.Cancelled(RESOURCE_LIMIT)` erzeugen würde. Die öffentliche Workspace-Compile-API wirft stattdessen; die Bridge antwortet mit einem Protokollstatus `cancelled` statt mit `status: ok` und `result.outcome: cancelled`. Der bestehende P3-1-Portable-Vertrag `stabilization-contract.cjs` erwartet genau dieses Ergebnis und schlägt am Kandidaten bei `stabilization-137` fehl. Die aktuelle JS- und WasmGC-Portable-Suite ist damit rot. Am Vorgänger `643fe12` bestehen dieselben Proben; der Regress wird durch den neuen Capture-Abbruch ausgelöst.

Damit ist **AC-P3-05 bis AC-P3-08 für die direkt geprüften LSP-Verträge PASS**, aber **kumulativ P3-1 FAIL** und **P3-2 insgesamt FAIL**. Ein Teil-PASS der LSP-Verträge und die geschlossenen F1–F7 dürfen nicht als P3-2- oder G3-Abschluss gelesen werden.

| Kriterium | Verdikt | Kern |
|---|---|---|
| AC-P3-05 – Scheduling, Synchronisation, Quellen, Watcher | **PASS** | F1–F6d, R4-F7 und R5-F7 in eigenen Proben geschlossen; Watcher/Primärlöschung/Resync/Tokenbudget im LSP kontrolliert. F8 liegt im gemeinsamen Workspace-/Bridge-Compilepfad, nicht in den hier geprüften LSP-Notifications. |
| AC-P3-06 – Mehrdatei-Rename | **PASS** | Mehrdatei, offene/geschlossene Versionen, Disk-/Schreibschutz, Kollision, Revisionen, Originalsegmente und überlebende Aliasadresse grün. |
| AC-P3-07 – Completion, Tokens, Folding, Formatierung, Diagrammanker | **PASS** | Scope/Vererbung/Qualifizierer/Sprachversion/Ersetzungsbereich/Incomplete, gebundene Tokens, Folding, gemeinsame Formatierung, PLAINTEXT-Hover und Snapshotanker grün. |
| AC-P3-08 – Lifecycle, stdio, Fehlerarten | **PASS** | Lifecycle, preinit/postshutdown, Exit 0/1, keine Exceptiondaten, originale Compiler-FailureId und LSP-Ressourcenfehler grün. |
| Gemeinsamer Workspace-/Bridge-Budgetvertrag (Kap. 07 §2.5, Kap. 10) | **FAIL** | **F8:** `Workspace.compile` wirft statt `Cancelled(RESOURCE_LIMIT)`; Bridge liefert `status: cancelled`; Portable JS/WasmGC `stabilization-137` rot. |
| Kumulativ P3-1 (lokal) | **FAIL** | `check`, SDK 9 und normative Conformance 577/16/0 grün; `checkPortable` und damit JS/WasmGC nicht grün. Roher Conformance-Lauf bleibt getrennt FAIL. |
| Externe Plattformmatrix | offen | Keine unabhängige Windows-/Linux-Ausführung am aktuellen SHA; kein Erfolg vorweggenommen. |
| P3-3/P3-4/P3-5/G3 | offen | Nicht Gegenstand dieses Teilreviews. |

## 2. Umgebung, Sauberkeit und tatsächlich ausgeführte Prüfungen

| Bestandteil | Tatsächlich verwendet |
|---|---|
| Betriebssystem | macOS 15.8.1, Darwin 24.6.0, arm64; temporäres Dateisystem case-insensitiv (APFS) |
| JDK | Temurin `25.0.3+9-LTS`, `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem` |
| Gradle | Repository-Wrapper 9.8.1 |
| Node | `v24.22.0` unter `/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node` |
| Python | nicht verwendet; kein Python/pip/uv |
| Browser | kein Chromium/Firefox/WebKit/Safari-/WebImage-Lauf in diesem Review |
| Windows/Linux | keine lokale Ausführung; die neue Matrix am Kandidaten wurde nicht als Erfolg gewertet |

### 2.1 Tatsächliche Kommandos und Resultate

```text
git rev-parse HEAD
-> e31ad29251eba60f1c7a36ecdb993fd1356eff41

git status --porcelain=v1
-> (leer, vor und nach allen Proben)
git diff --check
-> (leer)

JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
  ./gradlew check :interlis-lsp:installDist \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
  --console=plain
-> Exit 0, BUILD SUCCESSFUL in 5s, 48 actionable tasks: 5 executed, 43 up-to-date
-> 1148 Testfälle, 0 Failures, 0 Errors, 0 Skips
-> 968 Modultests: Compiler 744, LSP 53, CLI 72, Workspace 23,
   Model 16, Projection 12, Repository 41, WASM-Bridge 7
-> 145 Evidenzfälle, 15 G2-Assertionfälle, 20 Verification-Fälle
-> PYTHON_FREE_SOURCE_PASS 1347 inputs
-> SDK PASS snap-ca6702cc5434ba6f340711adc82de4ad557aa4248a6b5f14d3da13cfee0078e7 models=1

JAVA_HOME=... ./gradlew :interlis-lsp:test \
  --tests 'guru.interlis.lsp.P3ProjectRenameTest.primaryDeletionReportedThroughTheSurvivingAliasPreservesTheCoreIdentity' \
  --tests 'guru.interlis.lsp.P3ProjectRenameTest.aValidReopenThroughAnotherAliasRecoversRejectedOpenAndChangeGenerations' \
  --tests 'guru.interlis.lsp.P3ProjectRenameTest.aSmallTokenBudgetProducesResourceCancellationAndTheSameSessionRecovers' \
  --tests 'guru.interlis.lsp.P3ProjectRenameTest.aSurvivingHardlinkBecomesTheClosedEditAddressWhenItsPrimaryDisappears' \
  --rerun-tasks --console=plain
-> Exit 0, 4 Tests, 0 Failures, 16 Tasks ausgeführt

JAVA_HOME=... ./gradlew :interlis-workspace:test \
  --tests 'guru.interlis.workspace.P3CaptureBudgetTest' --rerun-tasks --console=plain
-> Exit 0, 1 Test, 0 Failures, 9 Tasks ausgeführt

JAVA_HOME=... ./gradlew checkPortable sdkProcessCheck conformanceCheck sdkPackage \
  -PnodeExecutable=... --console=plain
-> Exit 1, BUILD FAILED in 35m 5s, 60 actionable tasks: 16 executed, 44 up-to-date
-> SDK_PROCESS_PASS 9 commands
-> conformanceCheck: selected=593 result=PASS rawExit=1
   {"PASS":577,"XFAIL":16,"FAIL":0,"XPASS":0,"ERROR":0,"TOTAL":593}
-> :interlis-wasm-teavm:jsContractTest FAILED
-> sdkPackage war UP-TO-DATE (kein frischer Paketlauf)

JAVA_HOME=... ./gradlew :interlis-wasm-teavm:wasmContractTest \
  -PnodeExecutable=... --console=plain
-> Exit 1, BUILD FAILED; wasm-contract-result.json status=failed
```

Der `check`-Lauf ist ein Aggregat der vorhandenen XML-Resultate; einzelne Tasks waren `UP-TO-DATE`. Die Zähler behaupten nicht, jeder Modultask sei in genau diesem Prozess frisch gelaufen. Die vier gezielten neuen LSP-Tests und der Workspace-Budgettest wurden dagegen mit `--rerun-tasks` frisch ausgeführt.

## 3. Eigene neue ausführbare Gegenproben

Alle Quellen und Rohlogs liegen unter dem ignorierten `build/review/P3-2-r6/` des Kandidaten. Die Proben wiederholen keine bestehende JUnit-Methode; sie sprechen den installierten LSP-Prozess über echtes stdio an oder verwenden ausschliesslich öffentliche Service-/Bridge-Verträge.

### 3.1 Neue LSP-Hauptprobe `probe/r6-main.mjs`

**48 Prüfungen, 0 Fehlschläge, Exit 0; Rohlog `build/review/P3-2-r6/r6-main.log`.**

Enthalten sind:

- **F6d Deleted:** Projekt mit `Base.ili`, `Root.ili` und Hardlink `HardBase.ili`; Primärdatei löschen, Watcher meldet `Deleted` über den überlebenden Alias. `compile` bleibt `succeeded`/`modelCount=2`, die Quellenzahl bleibt zwei, Rename enthält genau zwei Dokumente und als geschlossene Adresse den überlebenden Alias. Genau eine `Root`-Deklaration.
- **F6d Changed:** gleicher Aufbau; Primärdatei löschen, Inhalt über den überlebenden Alias ändern, Watcher meldet `Changed` über den Alias. `compile` bleibt `succeeded`/`modelCount=2`; Rename enthält den Alias; keine zweite Quelle.
- **R5-F7 Resync:** abgelehntes `didOpen` und abgelehnter `didChange` (2,2-MB-Ersatz), jeweils Alias-first und Canonical-first. Vor dem gültigen Wiederöffnen liefert die Sprachabfrage `ContentModified` (-32801); nach dem gültigen Open über die andere URI ist `compile` `succeeded`/`modelCount=2`, die Sprachabfrage wieder verfügbar, und ein unabhängiger gültiger offener Client (`Base.ili`) bleibt autoritativ.
- **R4-F7 Tokenbudget:** `initializationOptions.analysisBudget.maxTokens=1`; `didOpen` wirft keine Notification-Exception, stderr enthält weder `AnalysisBudgetExceededException` noch `Notification threw`; `compile` und Sprachabfrage liefern -32800/`RESOURCE_LIMIT` ohne Exceptiondaten. Nach `didChangeConfiguration` mit erhöhtem Budget antwortet dieselbe Sitzung wieder und kompiliert.
- **F5 Quelllimit:** 2,2-MB-`didChange` ergibt `ContentModified` (-32801) ohne Exceptiondaten, der Betriebskanal nennt `SOURCE_LIMIT_EXCEEDED`, kein Stacktrace; ein vollständiger Ersatz resynchronisiert und kompiliert.
- **AC-P3-07:** Completion liefert lokale und äussere Namen ohne fremde Mitglieder; Semantic Tokens sind nichtleer und fünfwertig; Folding liefert Deklarationsbereiche; Formatierung respektiert `tabSize=3`; Hover ist `plaintext`; Diagramm liefert ein parsebares Dokument mit `snapshotId`.
- **AC-P3-08:** Pre-Init `ServerNotInitialized` (-32002), unbekannte Methode `MethodNotFound` (-32601), Post-Shutdown `InvalidRequest` (-32600), jeweils `data=null`; `exit` nach Shutdown Status 0, `exit` ohne Shutdown Status 1, kein Stacktrace.

### 3.2 Neue keylose Aliasprobe `probe/KeylessAliasProbe.java`

**14 Prüfungen, 0 Fehlschläge, Exit 0; Rohlog `build/review/P3-2-r6/keyless-alias.log`.**

Der testweise Standardprovider `probe/NoKeyProvider.java` liefert wie die OpenJDK-25-Windows-Implementierung `BasicFileAttributes.fileKey()==null`; gestartet mit `-Djava.nio.file.spi.DefaultFileSystemProvider=NoKeyProvider`. Für `Deleted` und `Changed` über den überlebenden Hardlink nach Primärlöschung:

```text
compile=succeeded modelCount=2
sources=2
established Core SourceId (Base.ili) present
rename documentChanges=2, closed URI=HardBase.ili
Base.ili deleted, HardBase.ili exists
```

Damit ist die belegte keylose Aliasregistrierung bei Primärabwesenheit unabhängig positiv geprüft. Das ist ein Testprovider, kein echter Windows-Lauf.

### 3.3 Neue Syntaxbudgetprobe `probe/SyntaxBudgetProbe.java`

**5 Prüfungen, 0 Fehlschläge, Exit 0; Rohlog `build/review/P3-2-r6/syntax-budget.log`.**

Der öffentliche `SyntaxServices.create().parse` liefert für `maxTokens=1` und `maxSourceUtf16Length=10` jeweils `OperationCancelledException(RESOURCE_LIMIT)` und nie die interne `AnalysisBudgetExceededException`; ein ungebundener Parse bleibt vollständig. Das bestätigt den beabsichtigten Teil des gemeinsamen Syntaxfixes.

### 3.4 Neue Bridge-/Workspace-Budgetprobe (F8) `probe/BridgeBudgetProbe.java` und `probe/WorkspaceCompileBudgetProbe.java`

**Bridge: 4 Prüfungen, 2 Fehlschläge, Exit 1; Rohlog `build/review/P3-2-r6/bridge-budget.log`.**
**Workspace: 1 Prüfung, 1 Fehlschlag, Exit 1; Rohlog `build/review/P3-2-r6/workspace-compile-budget.log`.**

Numerisches Budget mit 4097 Ziffern über die öffentliche Bridge:

```text
updateSource -> status ok, workspaceRevision=1
compile       -> {"status":"cancelled","cancellation":{"reason":"RESOURCE_LIMIT"}}
```

Erwartet ist nach dem bestehenden P3-1-Portable-Vertrag und `NumericLiteralBudgetContractTest`:

```text
status ok, result.outcome=cancelled, result.cancellation.reason=RESOURCE_LIMIT
```

Direkt über `Workspace.compile`:

```text
THROWN=guru.interlis.execution.OperationCancelledException: operation cancelled: RESOURCE_LIMIT
```

Erwartet: `Cancelled[analysis=Optional.empty, reason=RESOURCE_LIMIT]`.

**A/B-Gegenprobe am Vorgänger `643fe12`:** dieselben Quellen, gegen die dortige Distribution kompiliert und ausgeführt, liefern für die Bridge `status ok`/`outcome cancelled` und für `Workspace.compile` `Cancelled[... RESOURCE_LIMIT]`. Rohlogs: `bridge-budget-parent643fe12.log`, `workspace-compile-budget-parent643fe12.log`. Damit ist F8 als Regress des aktuellen Kandidaten und nicht als Eigenheit der Probe belegt.

### 3.5 Unveränderte Wiederholung der bisherigen Reviewerquellen

Die Quellen wurden unverändert aus den vorherigen Reviewablagen übernommen und gegen die Distribution dieses Kandidaten ausgeführt:

| Probe | Beobachtung | Wertung |
|---|---|---|
| `probe/alias-edge-r5-unmodified.mjs` (Review 5) | 31/31 PASS, Exit 0; inklusive F6d Deleted/Changed, R5-F7 beide Richtungen, externer Alias, bytegleiche getrennte Dateien | PASS; die früheren R5-REDs sind geschlossen |
| `probe/probe-lsp-r1-unmodified.mjs` (Review 1) | 48/48 PASS, Exit 0 | PASS |
| `probe/independent-r3-unmodified.mjs` (Review 3) | 76/76 PASS, Exit 0, 5 Beobachtungen | PASS |
| `probe/sync-edge-r3-unmodified.mjs` (Review 3) | 21/21 PASS, Exit 0 | PASS |
| `probe/independent-r4-unmodified.mjs` (Review 4) | 119/119 PASS, Exit 0 | PASS |
| `probe/negative-r4-unmodified.mjs` (Review 4) | 24/24 PASS, Exit 0; `maxTokens` jetzt kontrolliert | PASS |
| `probe/ReviewFailureR4.java` (Review 4) | 9/9 PASS; Compile- und Analyse-FailureId, `data=null` | PASS |
| `probe/finding-f6-r5-unmodified.mjs` (Review 5) | 7/8 PASS, Exit 1; letzte Assertion siehe §6.1 | Probe-Assertion fehlerhaft, Produktpfad grün |

## 4. Reproduzierbarer MUSS-Befund F8 – `Workspace.compile` verliert den `Cancelled`-Ergebnisvertrag bei Syntaxbudgets

### 4.1 Anforderung

Kapitel 07 §2.5 (Zeile 215) verlangt: „Ein ausgeschöpftes semantisches `AnalysisBudget` führt bei `analyze` zu `OperationCancelledException(RESOURCE_LIMIT)` und bei `compile` zu `Cancelled(RESOURCE_LIMIT)` mit höchstens einer ausdrücklich unvollständigen Teilsicht.“ Die Engine implementiert das in `DefaultCompilerEngine.compile` korrekt (Zeilen 134/137: `OperationCancelledException` und `AnalysisBudgetExceededException` werden zu `CompilationOutcome.Cancelled`). Der P3-1-Portable-Vertrag `interlis-wasm-teavm/stabilization-contract.cjs` erwartet für den numerischen Budgetfall `stabilization-137` ebenfalls `status: ok` und `outcome: cancelled` (Zeilen 6–9 und 2029). `NumericLiteralBudgetContractTest` prüft denselben Ergebnisvertrag auf Engineebene.

### 4.2 Reproduktion

1. `build/review/P3-2-r6/probe/BridgeBudgetProbe.java`: `openSession`, `updateSource` mit 4097-stelligem numerischem Literal, `compile` über `guru.interlis.bridge.BridgeDispatcher`.
2. `build/review/P3-2-r6/probe/WorkspaceCompileBudgetProbe.java`: dasselbe Modell über die öffentliche `Workspace`-API.
3. `./gradlew :interlis-wasm-teavm:wasmContractTest` bzw. der `jsContractTest` im kumulativen Lauf.

Beobachtung am Kandidaten:

```text
Bridge:   {"status":"cancelled","cancellation":{"reason":"RESOURCE_LIMIT"}}
Workspace: guru.interlis.execution.OperationCancelledException: operation cancelled: RESOURCE_LIMIT
Portable: js-contract-result.json  status=failed, requestId=stabilization-137
          wasm-contract-result.json status=failed, requestId=stabilization-137
```

Beobachtung am Vorgänger `643fe12` mit denselben Proben:

```text
Bridge:   {"status":"ok","result":{"outcome":"cancelled","cancellation":{"reason":"RESOURCE_LIMIT"}}}
Workspace: Cancelled[analysis=Optional.empty, reason=RESOURCE_LIMIT]
Portable: vorhandene js-contract-result.json/wasm-contract-result.json: status=passed, 3548 Fälle
```

### 4.3 Ursache und Dateibezug

- `interlis-workspace/src/main/java/guru/interlis/workspace/DefaultWorkspace.java:287` – neu hinzugefügt: `if(syntax.coverage().budgetExceeded()) throw new OperationCancelledException(CancellationReason.RESOURCE_LIMIT);` im gemeinsamen `capture`.
- `DefaultWorkspace.compile` (Zeilen 398–406) ruft `capture(...)` vor `engine.compile(...)` auf. Die Exception verlässt daher die öffentliche Compile-Methode, bevor `DefaultCompilerEngine.compile` den kontrollierten `CompilationOutcome.Cancelled`-Pfad erreicht.
- `interlis-wasm-bridge/src/main/java/guru/interlis/bridge/CompilerSessionFacade.java:159` ruft `workspace.compile` auf; `BridgeDispatcher` fängt die `OperationCancelledException` ab und erzeugt den Protokollstatus `cancelled`.
- `P3CaptureBudgetTest` (Zeile 25) prüft nur, dass `workspace.capture` wirft. Es gibt keinen Test, der den öffentlichen `Workspace.compile`-Ergebnisvertrag für `budgetExceeded` prüft; deshalb blieb der Regress im JVM-`check` grün.
- Die neue Kapitel-04-Zeile 170–172 erlaubt einem Capture, `RESOURCE_LIMIT` vor Resolver/Reuse zu melden. Sie löst aber den Widerspruch zu Kapitel 07 §2.5 und zum bestehenden Portable-Vertrag nicht auf. Kapitel 10 (Zeile 288) nennt für die Bridge `status: cancelled`; der vorhandene P3-1-Vertrag erwartet dagegen `status: ok`/`outcome: cancelled`. Der Kandidat hat diese Vertragsfrage weder entschieden noch synchronisiert.

### 4.4 Wirkung

- Der P3-1-Portable-Nachweis (JS und WasmGC, je 3548 Fälle) ist am Kandidaten rot. `stabilization-137` ist der erste numerische Budgetfall; weitere Fälle werden durch den Abbruch nicht mehr erreicht.
- Die öffentliche `Workspace.compile`-API verletzt den in Kapitel 07 §2.5 zugesagten Ergebnisvertrag: Ein Budgetfall ist kein geworfener API-Fehler, sondern `CompilationOutcome.Cancelled(RESOURCE_LIMIT)`.
- Bridge-Clients erhalten statt eines abgeschlossenen Compile-Ergebnisses eine Protokoll-Cancellation. Das ist eine beobachtbare Verhaltensänderung gegenüber dem P3-1-Produkt, ohne dass der bestehende Portable-Vertrag oder die Dokumentation konsistent nachgeführt wurde.
- Der Fehler ist nicht auf einen Randfall beschränkt: Er trifft jedes `budgetExceeded`-Syntaxsnapshot, insbesondere die bereits vorhandenen numerischen Grenzfälle, und damit die kumulative P3-1-Abnahme.

### 4.5 Gegenempfehlung

1. Den Capture-Abbruch nicht ungefiltert aus `Workspace.compile` entweichen lassen: In `DefaultWorkspace.compile` (beide Overloads) `OperationCancelledException` fangen und als `CompilationOutcome.Cancelled(Optional.empty(), reason)` zurückgeben; `analyze`/`capture` dürfen weiterhin werfen. Alternativ den `budgetExceeded`-Abbruch nur im Analyse-/LSP-Pfad aktivieren, nicht im gemeinsamen Capture für `compile`.
2. Einen öffentlichen Vertragstest ergänzen: numerisches Budget und Tokenbudget über `Workspace.compile` und über die Bridge; erwartet `CompilationOutcome.Cancelled(RESOURCE_LIMIT)` bzw. `status: ok` + `outcome: cancelled`.
3. Kapitel 04, 07 und 10 sowie `stabilization-contract.cjs` konsistent halten. Eine Änderung des Portable-Vertrags auf `status: cancelled` wäre eine Abnahmeveränderung und bräuchte einen ausdrücklichen fachlichen Beleg und eine neue P3-1-Bewertung; sie darf nicht stillschweigend durch einen roten Test ersetzt werden.
4. `P3CaptureBudgetTest` um den Compile-Ergebnisvertrag ergänzen; der bestehende Capture-Test bleibt als engerer Test erhalten.

**Rohpfade:** `build/review/P3-2-r6/bridge-budget.log`, `build/review/P3-2-r6/workspace-compile-budget.log`, `build/review/P3-2-r6/bridge-budget-parent643fe12.log`, `build/review/P3-2-r6/workspace-compile-budget-parent643fe12.log`, `interlis-wasm-teavm/build/js-contract-result.json`, `interlis-wasm-teavm/build/wasm-contract-result.json`.

## 5. Kriterienmatrix AC-P3-05 bis AC-P3-08

### AC-P3-05 – Dokumentnotifications, neueste Generation, Abbruch, bedingte Annahme, Quellen/Watcher

| Anforderung | Beobachtung | Verdikt |
|---|---|---|
| INC-018/022 – veraltete Ergebnisse, Capture/paralleles Update, bedingte Annahme | `P3AnalysisSchedulingTest`; unveränderte R4-Probe 119/119 (30 schnelle Vollersetzungen, finale Generation); R3-Grenzprobe 21/21 (40 Vollersetzungen); eigene Hauptprobe 48/48 | PASS |
| INC-019 – Konfigurations-/Resolverwechsel als Workspace-Änderung | Eigene Tokenbudget-/Konfigurationserholung; R4 S7 `allowedLanguages=2.3/2.4`; `P3PresentationConfigurationTest`; ungültige Konfiguration bleibt wirkungslos | PASS |
| LSP-003 – Watcher überschreiben offenen Clienttext nicht | Eigene F6d-Proben mit ungespeichertem `Root`-Text; unveränderte R5-Probe; keylose Probe; Case-/Hardlink-Watcher | PASS |
| LSP-004 – atomare mehrteilige `didChange` | Eigene übergrosse Änderung: `ContentModified`, kein Payload-Echo, Vollersatz resynchronisiert; unveränderte R3-/R4-Proben; `P3DocumentSynchronizationTest` | PASS |
| LSP-006 – leere Diagnostik ersetzt frühere | Unveränderte R1-/R3-/R4-Proben; `P3ProjectRenameTest`; No-op-Version exakt | PASS |
| LSP-011 – Abbruch in Engine/Worker, Sitzung danach nutzbar | Unveränderte R1-/R3-/R4-Proben; `LspProtocolCancellationTest`; eigene Tokenbudget-Sitzung bleibt nutzbar | PASS |
| LSP-015 – Repositoryänderung aktualisiert abhängige offene Analyse | Unveränderte R3-Probe A32/A33; R4 S0 realer Alias; R5-Alisprobe | PASS |
| Quellidentität, geschlossene Projektquellen, Hardlink-/Symlink-/Case-Alias | F6a–F6c in R1/R3/R4-Proben; F6d in eigener Deleted-/Changed-Probe und keyloser Probe; R5-REDs jetzt grün | PASS |
| Konfigurierbare Budgets / Ressourcengrenzen im LSP | Eigene `maxTokens`-Probe: -32800/RESOURCE_LIMIT, kein Stacktrace, Erholung; R4 negativ 24/24; Quell-/Projektlimit kontrolliert; F8 betrifft den gemeinsamen Workspace-/Bridge-Compilepfad, nicht die LSP-Antwort | PASS (LSP) |
| Bedingte Cacheannahme / neueste Generation | `P3AnalysisSchedulingTest`; 30-/40-Edit-Proben; keine veraltete Publikation beobachtet | PASS |

**AC-P3-05 im geprüften LSP-Umfang: PASS.** F1–F6d, R4-F7 und R5-F7 sind geschlossen. F8 ist ein separater gemeinsamer Compile-/Bridge-Vertrag und wird in der kumulativen Zeile geführt.

### AC-P3-06 – Workspace-weites Rename (LSP-012)

| Prüfung | Beobachtung | Verdikt |
|---|---|---|
| Mehrdatei-Rename offen/geschlossen | Eigene F6d-Renameproben: zwei Dokumente, offene exakte Version, geschlossene `null`; unveränderte R5-/R4-Proben; `P3ProjectRenameTest` | PASS |
| `documentChanges`/Clientversionen | Versioned-Edit-Ausgabe in allen eigenen und unveränderten Proben; ohne Capability `changes` | PASS |
| Diskhash/Pfadziel/Schreibrecht | Unveränderte R4-S10/R5-Proben; `P3ProjectRenameTest`; unangekündigte Diskänderung `ContentModified`, Schreibschutz kontrollierter Fehler, keine Datei geschrieben | PASS |
| Kollision/Originalsegmente/Revisionen | `P3CompletionScopeContractTest` (äussere/geerbte Kollisionen); `P3ProjectRenameTest`; fehlende Revision `CONTENT_MODIFIED`; Originalsegmentprüfung im Plan | PASS |
| Fehlende/unvollständige Quelle | `LspFeatureTest`; `P3CompletionScopeContractTest`; kein vollständiger Plan | PASS |
| Server schreibt keine Rename-Dateien | Eigene und unveränderte Proben vergleichen Dateiinhalte; keine Schreiboperation beobachtet | PASS |

**AC-P3-06: PASS.**

### AC-P3-07 – gemeinsame Completion/Tokens/Folding/Formatierung/Diagrammanker

| Prüfung | Beobachtung | Verdikt |
|---|---|---|
| Scope, Import, Vererbung | Eigene Hauptprobe: `localName` und `OuterName`, kein `foreignName`; unveränderte R1-/R3-/R4-Proben; `P3CompletionScopeContractTest` | PASS |
| Qualifizierer/Member | Unveränderte R4-S11: `Base.` liefert nur `Common`; `membersOf`/`visibleSymbols` | PASS |
| Sprachversion/Keywords | `P3CompletionScopeContractTest`: `MULTICOORD` nur 2.4; Completion nutzt Sprachversion | PASS |
| Ersetzungsbereich/InsertText/Incomplete | `P3CompletionProtocolTest` (100-Trunkierung, `isIncomplete`, ganzer Name); unveränderte R4-Probe; eigene Completionprobe | PASS |
| Gebundene Tokens/Zeilen | Eigene Probe: nichtleere fünfwertige Payload; unveränderte R4-Probe: `inherited` als `property`, Kommentar als `comment`, zeilenbegrenzte UTF-16-Spannen | PASS |
| Folding | Eigene Probe: Deklarationsbereiche; unveränderte R4-Probe: mehrzeiliger Kommentar | PASS |
| Formatierung | Eigene Probe: `tabSize=3` wirksam; ungültige Quelle Fehler statt scheinbarer Erfolg; `P3PresentationConfigurationTest` | PASS |
| Hover/Diagrammanker | Eigene Probe: Hover `plaintext`, Diagramm mit `snapshotId`; unveränderte R4-Probe: veralteter Snapshot `InvalidParams` | PASS |

**AC-P3-07: PASS.**

### AC-P3-08 – Lifecycle, stdio, Fehlerarten

| Anforderung | Beobachtung | Verdikt |
|---|---|---|
| LSP-001 – dünner Adapter, keine zweite Binder-/Typsemantik | Inspektion: LSP nutzt Workspace/`LanguageQueries`; keine ANTLR-/Binder-Implementierung im LSP-Modul | PASS |
| LSP-002 – `initialize`/`initialized`/`shutdown`/`exit`/Dokumentlebenszyklus | Eigene Probe: Pre-Init, Post-Shutdown, Exit 0/1; unveränderte R1-/R3-/R4-Proben | PASS |
| LSP-008 – stdio, stdout nur Protokoll | Alle eigenen Proben parsen ausschliesslich Frames; stderr im Normalfall ohne Stacktrace; Notification-Exception bei `maxTokens` nicht mehr beobachtet | PASS |
| LSP-009 – keine globalen Compile-Locks/Listener | Inspektion der Scheduler-Executors; zahlreiche unabhängige Serverprozesse in den Proben | PASS |
| LSP-016 – Transport-/Compiler-/interne Fehler unterscheidbar | Eigene Probe: -32800/RESOURCE_LIMIT, -32801/ContentModified, -32002/-32600/-32601; `ReviewFailureR4` 9/9 mit originaler FailureId und `data=null` | PASS |
| F8-Abgrenzung | F8 ist ein öffentlicher Workspace-/Bridge-Ergebnisvertrag, keine LSP-Transport-/Compilerfehlerverwechslung | separater MUSS-Befund |

**AC-P3-08: PASS** für die LSP-Verträge. F8 bleibt ein eigener gemeinsamer Compile-/Bridge-Befund.

### Kumulative P3-1-Regression

| Prüfung | Beobachtung | Verdikt |
|---|---|---|
| `check` | Exit 0; 1148 Fälle, 0 Failures/Errors/Skips; 744 Compiler-, 53 LSP-, 145 Evidenz-, 15 G2-, 20 Verification-Fälle | PASS (lokal) |
| Gezielte neue LSP-Tests | 4/4 frisch PASS (`--rerun-tasks`) | PASS |
| `P3CaptureBudgetTest` | 1/1 frisch PASS; prüft Capture-Abbruch, nicht den Compile-Ergebnisvertrag | PASS (eng) |
| `sdkProcessCheck` | 9 Kommandos PASS | PASS |
| `sdkPackage` | im kumulativen Lauf `UP-TO-DATE`; kein frischer Paketlauf | nicht frisch belegt |
| `conformanceCheck` | normativ 577 PASS / 16 XFAIL / 0 FAIL; roher Lauf bleibt 577/1/15 FAIL | PASS (normativ), Rohlauf FAIL |
| `checkPortable` | **FAIL** bei `:interlis-wasm-teavm:jsContractTest`; `wasmContractTest` ebenfalls FAIL | **FAIL** |
| P3-1-CLI-/Formatter-Verträge | durch den P3-2-Diff nicht verändert; `check` grün | PASS im JVM-Umfang |

**Kumulativ P3-1 lokal: FAIL** wegen F8 und der roten Portable-Suite. Die historischen 3548er-Portable-Nachweise werden nicht als aktuelle Prüfung übernommen; der rohe Conformance-Lauf bleibt getrennt FAIL.

## 6. Weitere Beobachtungen, Quellenbindung und Pins

### 6.1 Bewertung der letzten Assertion in `finding-f6.mjs`

Die unveränderte Originalprobe verlangt `symbols.length === 1 && symbols[0].name === 'Root'`, also **ein einziges Symbol insgesamt**. Die tatsächliche öffentliche Ausgabe ist die flache Liste `["Root","T","C","value"]`; sie enthält genau eine `Root`-Deklaration. Das LSP erlaubt für `textDocument/documentSymbol` sowohl flache als auch hierarchische Ausgaben; die flache Liste ist vertragskonform. Die Assertion verwechselt „genau ein Wurzelmodell“ mit „genau ein Symbol insgesamt“. Ich übernehme sie nicht und ändere die Produktsemantik nicht, um sie grün zu machen. Die Originalprobe bleibt mit 7/8 und Exit 1 erhalten; die separate eigene Probe prüft die sachlich richtige Erwartung. Es gibt keinen Produktbefund daraus.

### 6.2 Roher Conformance-Lauf

Der rohe Referenzlauf bleibt `FAIL` (577 PASS, 1 XFAIL, 15 FAIL); die normative Auswertung mit den freigegebenen Ausnahmen ist `PASS` (577/16/0). Die Rohausgabe wird nicht als Produkterfolg umetikettiert.

### 6.3 Keyless-Provider und Plattformgrenzen

Die keylose Aliasprobe verwendet einen testweisen Standardprovider mit `fileKey()==null`. Sie ist kein Windows-Lauf; der reale Windows-Nachweis bleibt der externen Matrix vorbehalten. Die keylose Probe bestätigt die belegte Aliasregistrierung bei Primärabwesenheit, nicht eine allgemeine Windows-Dateisystemidentität.

### 6.4 Weitere Grenzfälle

- **Unregistrierter externer Hardlink nach Primärlöschung:** Die unveränderte R5-Probe `unregistered surviving hardlink retains the Core-ID after primary deletion` ist am Kandidaten grün. Das ist eine Erweiterung der Aliasregistrierung, kein eigener MUSS-Befund.
- **Verschachtelte Verzeichnis-Symlinks:** Die unveränderte R3-Probe E3 liefert weiterhin `MISSING_INPUT`. Der normative Text verlangt ausdrücklich nur den symlinkten Workspace-Root; daraus wird kein neues MUSS abgeleitet.
- **Case-Alias:** Die unveränderte R3-/R5-Case-Probe ist auf macOS APFS grün; Windows wurde nicht unabhängig ausgeführt.
- **Review 5 Transport:** Der Bericht wurde geschrieben, aber der äussere Transport endete mit Exit 1/Manifest failed. Daraus wird kein positiver Abnahmenachweis abgeleitet. Seine konkreten Reproduktionen wurden durch eigene REDs bestätigt und sind jetzt grün.

### 6.5 Quellbindung, Pins und Statuswahrheit

- `docs/p3/STATUS.md` bezeichnet P3-2 korrekt als offen und G3 als offen; dieser Bericht ändert den Status nicht.
- `docs/p3/implementation/P3-2.md` nennt für den aktuellen Stand 53 LSP-Fälle und die Korrekturpfade; die tatsächlichen Zähler dieses Laufs (53 LSP, 744 Compiler, 145 Evidenz) stimmen damit überein. Die dortige Aussage „Workspace-Capture bricht ... ab“ beschreibt genau die Ursache von F8; der Portable-Compile-Ergebnisvertrag wird dort nicht als geändert ausgewiesen.
- `docs/g2/` und `docs/evidence/p3/` sind zwischen `fbcbc76` und dem Kandidaten unverändert; die G2-Norm-/Kompatibilitätsprofile und Originalerwartungen bleiben erhalten.
- Die aktiven G2-Quellpins, Methoden-/Range-/Zeilenlocator in `docs/evidence/stabilization/` und `testdata/g2/` wurden mechanisch nachgeführt; die Evidenz-Suite ist grün. Keine Norm-/Corpus-/Suiteerwartung wurde abgeschwächt.
- Die Spezifikationsänderungen in Kapitel 04/07 sind Vertragsergänzungen zu Dateiidentität, Resync und frühen Budgets. Kapitel 04 legalisiert den Capture-Abbruch; Kapitel 07 §2.5 und der Portable-Vertrag bleiben dazu widersprüchlich. Das ist Teil von F8, keine stillschweigende Scope-Abschwächung.
- Der Elterncheckout mit unabhängiger uncommitteter Browserarbeit wurde nicht als Kandidatenbestandteil behandelt und nicht als P3-2-Abnahme verwendet.

## 7. Nicht ausgeführt / Grenzen

- Kein `checkBrowser`, kein `releaseCheck`, kein `performanceCheck`, kein Chromium-/Firefox-/WebKit-/Safari-/WebImage-Lauf, kein `npm ci`. Diese Pfade sind für den JVM-LSP nicht erforderlich und bleiben P3-3/P3-5/G3.
- Keine lokale Linux-x64-/Windows-x64-Ausführung und keine unabhängige Auswertung der neuen GitHub-Matrix am Kandidaten. Ein Erfolg wird nicht vorweggenommen; die finale Plattformmatrix bleibt P3-5.
- `check` ist ein Aggregat der XML-Resultate; einzelne Tasks waren `UP-TO-DATE`. Die Zähler behaupten nicht, jeder Modultask sei in genau diesem Prozess frisch gelaufen.
- `sdkPackage` war im kumulativen Lauf `UP-TO-DATE`; es liegt kein frischer Paketlauf vor.
- Der keylose Fall wurde mit einem testweisen Provider auf macOS ausgeführt, nicht auf einem echten Windows-Host.
- Keine Heap-/Retention-Messung für abgelehnte Riesentexte; beobachtet ist nur das Verhalten und der Codezustand.
- Die unveränderte `finding-f6.mjs`-Probe ist mit 7/8 nicht grün; die einzelne rote Assertion ist ein Probe-Fehler, kein Produktbefund.
- Keine Reparatur am Kandidaten, keine Änderung versionierter Dateien, kein Commit/Push; eigene Dateien ausschliesslich unter `build/review/P3-2-r6/`.

## 8. Gesamturteil

**P3-2: FAIL.**

- AC-P3-05: **PASS** für die direkt geprüften LSP-Verträge; F1–F6d, R4-F7 und R5-F7 geschlossen.
- AC-P3-06: **PASS**.
- AC-P3-07: **PASS**.
- AC-P3-08: **PASS** für Lifecycle, stdio und LSP-Fehlerarten; F4/F5/F7 geschlossen.
- Gemeinsamer Workspace-/Bridge-Budgetvertrag: **FAIL (F8)** – `Workspace.compile` wirft statt `Cancelled(RESOURCE_LIMIT)`; Bridge liefert `status: cancelled`; Portable JS/WasmGC `stabilization-137` rot.
- Kumulativ P3-1: **FAIL** (lokal) wegen F8 und der roten Portable-Suite; `check`, SDK 9 und normative Conformance sind grün, der rohe Conformance-Lauf bleibt FAIL.
- Externe Plattformmatrix: offen; P3-3/P3-4/P3-5/G3: offen.

F8 ist ein reproduzierbarer MUSS-Regress, kein blosses Testnamen- oder Timingproblem. Er ist am Vorgänger `643fe12` mit denselben Proben nicht vorhanden und wird durch den neuen Capture-Abbruch in `DefaultWorkspace.java:287` ausgelöst. Bis der öffentliche Compile-Ergebnisvertrag wiederhergestellt und die Portable-Suite erneut grün ausgeführt ist, ist P3-2 nicht abgenommen. Ein Teil-PASS von AC-P3-05 bis AC-P3-08 und die geschlossenen F1–F7 dürfen nicht als P3-2- oder G3-Abschluss gelesen werden. Der Kandidat wurde nicht repariert, keine Erwartung abgeschwächt und kein bedingtes PASS ausgesprochen.

## 9. Rohpfade

- `build/review/P3-2-r6/check-install.log`
- `build/review/P3-2-r6/cumulative.log`
- `build/review/P3-2-r6/conformance.log` (versuchter separater Lauf; der belastbare normative Nachweis steht im kumulativen Lauf)
- `build/review/P3-2-r6/lsp-new-tests.log`
- `build/review/P3-2-r6/workspace-budget-test.log`
- `build/review/P3-2-r6/r6-main.log`
- `build/review/P3-2-r6/keyless-alias.log`
- `build/review/P3-2-r6/syntax-budget.log`
- `build/review/P3-2-r6/bridge-budget.log`
- `build/review/P3-2-r6/workspace-compile-budget.log`
- `build/review/P3-2-r6/bridge-budget-parent643fe12.log`
- `build/review/P3-2-r6/workspace-compile-budget-parent643fe12.log`
- `build/review/P3-2-r6/alias-edge-r5.log`
- `build/review/P3-2-r6/finding-f6-unmodified.log`
- `build/review/P3-2-r6/probe-lsp-r1.log`
- `build/review/P3-2-r6/independent-r3.log`
- `build/review/P3-2-r6/sync-edge-r3.log`
- `build/review/P3-2-r6/independent-r4.log`
- `build/review/P3-2-r6/negative-r4.log`
- `build/review/P3-2-r6/failureid.log`
- Quellen: `build/review/P3-2-r6/probe/r6-main.mjs`, `KeylessAliasProbe.java`, `NoKeyProvider.java`, `SyntaxBudgetProbe.java`, `BridgeBudgetProbe.java`, `WorkspaceCompileBudgetProbe.java`, `alias-edge-r5-unmodified.mjs`, `finding-f6-r5-unmodified.mjs`, `probe-lsp-r1-unmodified.mjs`, `independent-r3-unmodified.mjs`, `sync-edge-r3-unmodified.mjs`, `independent-r4-unmodified.mjs`, `negative-r4-unmodified.mjs`, `ReviewFailureR4.java`
- Portable-Resultate: `interlis-wasm-teavm/build/js-contract-result.json`, `interlis-wasm-teavm/build/wasm-contract-result.json`
