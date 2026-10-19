# P3-4 performance evidence

## Corpus

Pinned from `https://geo.so.ch/models/ilimodels.xml`:

- index SHA-256 `ff9561b4e248da29c0bde4f65e77c1e22e00e45a4f7c8538756debd11451d93c`
- thirteen standalone models: twelve INTERLIS 2.3 and one 2.4
- exact model hashes and sizes in `geo-corpus.json`

The corpus contains 13 real models (about 36 KB); it is still a seed, not a production-scale corpus. It is not presented as a production
benchmark; the larger repository is the source for a later expansion.

## Measurements

| Runtime | File | Scope |
|---|---|---|
| JVM CLI | `performance-jvm.json` | compile and format, 1 warmup + 3 runs, stability and max RSS |
| TeaVM WasmGC | `performance-wasm.json` | compile, 1 warmup + 3 runs |
| Playwright Chromium | `performance-browser.json` | compile, 1 warmup + 3 runs |
| Playwright Firefox | `performance-browser.json` | compile, 1 warmup + 3 runs |
| Playwright WebKit | `performance-browser.json` | one small model; others skipped on WasmGC stack limit |

Commands:

```sh
./gradlew geoCorpus
./gradlew jvmBenchmark
./gradlew wasmBenchmark
./gradlew browserBenchmark
./gradlew performanceCheck
```

## Queue and abort

- `BoundedObservationQueue` capacity 1024; a 100,000-event load test verifies bounded retention and drop statistics (`BoundedObservationQueueTest`).
- `CancelledAnalysisTest` verifies a pre-cancelled analysis terminates without a schema.

## Open measurement gaps

- No large production corpus yet; the current selection is a real but small
  baseline.
- The browser 2.4 model and two WebKit models exceed the current browser WasmGC
  stack limit and are recorded as `skipped`; they are measured on JVM/WasmGC.
