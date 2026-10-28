# WasmGC: Prozessabschluss unter Node

Untersuchung vom 20. Oktober 2026; WASM-004/005/013/018 und TEST-001/007.
Die maschinenlesbare Evidenz steht in
[wasmgc-process-exit-2026-10-20.json](wasmgc-process-exit-2026-10-20.json).
Dies ist eine Laufzeituntersuchung, keine Performanceabnahme.

## Befund und Entscheidung

Node 22.23.1 beendet die TeaVM-WasmGC-Probe trotz fertig bearbeiteter Anfragen
nicht innerhalb des Testlimits. Der native Stack zeigt den Hauptthread in
`node::NodePlatform::DrainTasks` / `uv_cond_wait`; ein Hintergrundthread arbeitet
in `BackgroundCompileJob`, `WasmTypingPhase`, `GraphReducer` und `WasmTyper`.
Damit ist der Stillstand im optimierenden V8-Wasm-Compiler lokalisiert.
Ein bestimmter Upstreamfehler oder eine mathematisch endlose Schleife ist damit
nicht bewiesen.

Bereits `TeaVM.wasmGC.load` ohne Bridgeanfrage reproduziert das Problem.
Reines `WebAssembly.compile` beendet sich dagegen regulär. Die semantischen
Compilerfälle und ein offenes Workspacehandle sind somit nicht notwendig,
um den Stillstand auszulösen. Die Ressourcenaufnahme unmittelbar nach `load`
enthält noch `FSReqPromise`/`CloseReq`; der native Stack ist der entscheidende
Nachweis für das Warten auf V8-Arbeit.

| Profil | Nur kompilieren | Laden | Capabilities | Semantik |
|---|---|---|---|---|
| Node 22.23.1, normale Optimierung | PASS | TIMEOUT | TIMEOUT | TIMEOUT nach fachlichem Abschluss |
| Node 22.23.1, `--liftoff-only` | PASS | PASS | PASS | PASS |
| Node 24.22.0, normale Optimierung | PASS | PASS | PASS | PASS |
| Node 24.22.0, `--liftoff-only` | PASS | PASS | PASS | PASS |

Die isolierenden Versuche verwenden ein 15-Sekunden-Limit pro Prozess.
Der reguläre Gradle-Vertrag hat das Problem unter Node 22 auch mit seinem
60-Sekunden-Limit nachgewiesen. Ein wegen Timeout beendeter Prozess bleibt
`TIMEOUT`, unabhängig von zuvor geschriebenen positiven Fachresultaten.

Das geprüfte Hostprofil verwendet deshalb **Node 24.22.0**. Der Pin steht in
`gradle.properties`; `checkNodeRuntime` prüft ihn vor Vertrags- und
Benchmarkläufen. `-PnodeExecutable` gilt auch für die Root-Benchmarktasks.
TeaVM 0.16.0, der WasmGC-Produktionspfad und die normale Optimierung bleiben
erhalten. Weder `process.exit(0)` noch `--liftoff-only` dienen als Lösung.
Browser und Web Image werden weiterhin eigenständig geprüft.

Das Node-Archiv stammt aus der
[offiziellen Distribution](https://nodejs.org/dist/v24.22.0/).
SHA-256 für `node-v24.22.0-darwin-arm64.tar.gz`:
`6a9724d9b50a5f22b4816cded441b5a8d9ddae546191a5e5b7efa5a5e2ee26d5`,
gegen die dortige `SHASUMS256.txt` geprüft.
Die Unterstützung anderer Betriebssysteme wird daraus nicht abgeleitet.

## Reproduktion

Nach `:interlis-wasm-teavm:buildWasmGC`:

```sh
python3 tools/performance/probe_wasm_exit.py \
  --node /absoluter/pfad/zu/node \
  --output build/reports/stabilization/exit-probe --semantics
```

Die Probe verändert keine Produktionsartefakte und beendet nur eigene
Kindprozesse nach Timeout. Auf macOS wird der hängende Semantikprozess vor
seiner Beendigung mit `sample` untersucht. Reports enthalten Kommandos,
Wasm-Hash, Node-/V8-Ausgaben, Abschlussereignisse und tatsächliche Exitcodes.
Der kleine Loader steht in `interlis-wasm-teavm/wasm-exit-probe.cjs`; das Wasm
selbst ist noch kein auf eine einzelne V8-Funktion reduziertes Reproduktionsmodul.

Offen bleiben die vereinbarten Editor-/Batch-/Abbruch-/Retentionbudgets,
die Plattformmatrix und die übrigen P2/P3-Pflichten. Ein reguläres Prozessende
schliesst diese Gates nicht.
