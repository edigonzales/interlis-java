# Independent review: ili2c MD5/TTL cache migration

Review the current candidate against baseSha. Read root AGENTS, the implementation
report, the new chapter08 section17 (authoritative user decision), APIs and all
changed code/tests. Earlier ETag/304 and completion-recheck requirements have
explicitly been superseded by the user. Do NOT flag the intended TTL staleness,
absence of completion checks, ETag removal, or documented early-SDK metadata
signature change as regressions. Do not demand unrequested features or broad tests.

CACHE-01: local SHA256 integrity plus optional index MD5 before model download;
metadata24h/model7d, inclusive boundary, per-URL last full successful download,
no renewal on hit, future/unknown age never fresh, zero/generic always fetch.
CACHE-02: explicit typed CacheRequest propagation through Routing, HTTP, verified
loader, RepositorySearch, RepositoryClient, lock catalogue; source compatibility
of functional loaders and immutable/null-free API. Correct post-download MD5,
SHA256 pins and lock behavior, cancellation, workspace atomic revision gate.
CACHE-03: metadata-v2 atomic/strict, offline-only v1 fallback, no fallback from
corrupt v2, no-store/404 cannot resurrect v1 entries, no deletion of old digest
contents, concurrency and failed/partial writes, correct clock ownership.
CACHE-04: no ETag/Last-Modified persistence or requests, no304 retry, no second
online verification including negative sources; identity/coding/no-store/limits/
redirect/proxy/timeouts intact; online failures never silently use stale cache.
CACHE-05: truthful chronological INFO FRESH_HIT, hit metrics, reserved legacy
codes not emitted, JSON/results/warnings/exits preserved, CLI four commands.
CACHE-06: audit original test XML/logs, intermediate failures and corrected test
oracles, 139 unique final passing cases using reused passing suites plus closure
rerun (NOT a single green full run), SDK9, CLI22 and public4. Bind actual runtime
JARs and sources using the manifest. No need to repeat original suites.

Mandatory: run your OWN NEW focused counterprobes with javac/java against supplied
runtime jars or Node and supplied launcher. Pick boundary/MD5/migration/lock edge
cases beyond the original helper. Scratch only build/reviewer. You may package
probe Java in guru.interlis.repository to inspect package-level hosts if needed.
No Gradle, Python, delegation, credentials, full corpus/benchmarks/browser/release.
Do not edit product/tests/docs or historical evidence. Keep worktree clean.

Write review.md and verdict.json to reviewDirectory. Include candidate SHA,
per-criterion PASS/FAIL, actionable findings with severity/file/line/reproducer,
actual own commands/exits and evidence audit. Overall PASS requires no open
must-fix issue AND executed independent counterprobes. Finish normally.
