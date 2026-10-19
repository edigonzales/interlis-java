# P3-4 performance harness

Commands:

```sh
./gradlew geoCorpus
./gradlew jvmBenchmark
./gradlew wasmBenchmark
./gradlew browserBenchmark
./gradlew performanceCheck   # all of the above
```

The corpus is pinned by `docs/evidence/p3/geo-corpus.json`. Model bytes are
cached outside the repository under `~/.cache/interlis-java/geo-corpus`;
the manifest records URL, version, byte size and SHA-256.

Measurements are evidence, not production guarantees. JVM measurements use
subprocess wall time. WasmGC measurements use the TeaVM API in Node. Browser
measurements use Playwright pages with one warmup run and three measured runs.

The browser harness skips models that exceed the current browser WasmGC stack
limit and records those entries as `skipped`; JVM/WasmGC measurements still
cover them.
