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

L0B-3 runs corpus verification and JVM measurement through Java host tools.
The original index and all thirteen source byte sequences are preserved in
`docs/evidence/p3/corpus-seed.zip` (provenance in `corpus-seed.md`); no pin changes.
Set `GEO_CORPUS_CACHE` to choose the shared JVM/Node cache; relative paths use the
repository root. `CONFORMANCE_CACHE` remains a compatible fallback.
JVM RSS is now recorded explicitly in **bytes per child** using `/usr/bin/time`
on macOS/Linux; the old Python/Darwin `maxRssKb` label was not a reliable unit.
A failed compiler, changed input/output, missing measurement or process timeout
fails the task. Existing compiler incompatibilities therefore keep the gate open.
