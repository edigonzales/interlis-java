# Independent review: ili2c MD5/TTL cache migration

- **Candidate:** `c9fdd30a4c2f142f6dd5e16576333424b6e10349`
- **Tree:** `1dd5a1f09e652397cdc5423915f6be837c21703b`
- **Base:** `6f4017c7ab90cb678c79f547b759c3661578726a`
- **Review worktree:** `/Users/stefan/sources/interlis-java/build/review/ili2c-cache-policy/r1/worktree`
- **Review directory:** `/Users/stefan/sources/interlis-java/build/review/ili2c-cache-policy/r1/output`
- **Reviewer environment:** Temurin Java 25.0.3, Node v24.22.0, macOS 15.8.1/arm64
- **Overall verdict:** **PASS** (no open must-fix issue; independent counterprobes executed)

## Scope and method

I read the root and `docs/spec/AGENTS.md` rules, the implementation report
(`docs/implementation/ili2c-cache-policy/REPORT.md`), the new authoritative
chapter 08 §17, the changed APIs/tests and the full candidate diff. I audited
the original evidence and manifest. I then wrote and executed **new**
counterprobes against the supplied runtime distribution only:

- 13 Java probes in package `probe`, compiled with `javac` against
  `build/reviewer/distribution/lib/*` and run with `java`.
- 16 CLI probes with Node against
  `build/reviewer/distribution/bin/interlis-compiler` and a private local HTTP
  server, isolated cache/home directories.

Scratch files live only under `build/reviewer/`. No product, test or historical
evidence file was edited. No Gradle, Python, corpus, benchmark, browser,
release, credential or delegation was used. The worktree remained clean.

The user decision in chapter 08 §17 supersedes the earlier ETag/304 and
online completion-recheck contracts. I did not treat TTL staleness, the absence
of a completion recheck, ETag removal, or the documented early-SDK
`RepositoryCacheMetadata` signature migration as regressions.

## Per-criterion result

### CACHE-01 — PASS

**Contract:** local SHA-256 integrity plus optional index MD5 before a model
download; metadata 24 h / model 7 d; inclusive boundary; per-URL last full
successful download; no renewal on hit; future/unknown age never fresh;
zero/generic always fetch.

**Code audit:**
- `RepositoryCacheRequest` is an immutable null-free record; `metadata()` = 1 day,
  `model(md5)` = 7 days, `unconditional()` = 0; negative ages rejected
  (`RepositoryCacheRequest.java:1-23`).
- `HttpSourceLoader.fresh()` rejects zero, `EPOCH`, and future timestamps and
  uses `Duration.between(...).compareTo(maxAge) <= 0`, so the exact boundary is
  inclusive (`HttpSourceLoader.java:168-173`).
- On a fresh entry the loader verifies the cached SHA-256 through
  `store.lookup(cached.digest())`, compares the optional MD5 case-insensitively,
  publishes `FRESH_HIT` only on a real reuse, and never calls `metadata.put`
  (`HttpSourceLoader.java:92-105`). A missing/corrupt/MD5-mismatching entry
  publishes `CACHE_MISS` and performs a full download.
- `downloadedAt` is written only after a complete successful 200 whose content
  is confirmed in the content store (`HttpSourceLoader.java:147-156`).
- Repository metadata consumers use `metadata()`; model loads in the verified
  closure use `model(metadata.md5())` (`RepositorySearch.java:140,149`,
  `RepositoryClient.java:64,103`, `LockedRepositoryCatalog.java:17`,
  `RepositoryClosureService.java:293`).

**Independent probes:** `PROBE_PASS 01` (exact 24 h inclusive, no renewal,
file-level future timestamp, `EPOCH`), `PROBE_PASS 02` (MD5 match/mismatch and
`CACHE_MISS` before `DOWNLOAD`), `PROBE_PASS 03` (generic/zero always fetch),
`PROBE_PASS 09` (injected clock owns freshness), `PROBE_PASS 13` (corrupt local
content is refreshed). CLI `warm`, `future-timestamp`, `changed-md5` passed.

### CACHE-02 — PASS

**Contract:** explicit typed cache-request propagation through routing, HTTP,
verified loader, search, client and lock catalogue; source compatibility of
functional loaders; immutable/null-free API; correct post-download MD5, SHA-256
pins and lock behavior; cancellation; atomic workspace revision gate.

**Code audit:**
- Additive `SourceLoader.load(locator, policy, request, token)` default delegates
  to the old abstract method, so existing functional loaders remain source
  compatible (`SourceLoader.java:18-26`).
- Routing, HTTP, `RepositorySearch`, `RepositoryClient`, `LockedRepositoryCatalog`
  and the closure `VerifiedLoader` all accept and forward the typed request
  (`RoutingSourceLoader.java:19-27`, `HttpSourceLoader.java:71-77`,
  `RepositorySearch.java:140,149`, `RepositoryClient.java:64,103`,
  `LockedRepositoryCatalog.java:17`, `RepositoryClosureService.java:436-438`).
- Post-download MD5 is enforced in the closure (`RepositoryClosureService.java:293-300`);
  SHA-256 pins/locks are enforced in `VerifiedLoader` (`:442-486`).
- Cancellation is checked before cache reuse and downloads
  (`HttpSourceLoader.java:79,99,115,137`; verified loader `:440,473`).
- The workspace revision gate remains at every publication point
  (`RepositoryClosureService.java:91,134,159,327`).
- `HostRepositories.createObserved(..., Clock)` centralises clock injection
  (`HostRepositories.java:48-58`).

**Independent probes:** `PROBE_PASS 11` (request factories/null-free validation),
`PROBE_PASS 12` (typed cancellation, zero requests). CLI `lock-cold`,
`lock-offline`, `lock-warm-online`, `lock-changed-model`, `lock-changed-index`
and `post-md5-mismatch` passed: a changed model or index is fetched once and
then rejected by the MD5/pin/lock check; offline and fresh online lock replays
perform zero HTTP requests.

### CACHE-03 — PASS

**Contract:** atomic/strict metadata-v2; offline-only v1 fallback; no fallback
from corrupt v2; no-store/404 cannot resurrect v1; old digest contents are not
deleted; concurrency and failed/partial writes; correct clock ownership.

**Code audit:**
- Metadata is v2 by default; v1 is selected only when offline and no v2 entry
  exists (`DirectoryMetadataStore.java:29-32`). A present v2 entry is parsed
  strictly; failure is a miss, never a v1 fallback (`:45-56`).
- v2 allows only `version/source/response-source/sha256/downloaded-at`; v1 is
  read as unknown age (`Instant.EPOCH`) (`:45-54`).
- Writes go to a temporary file and are published with `ATOMIC_MOVE` +
  `REPLACE_EXISTING` (`:59-74`). A failed/partial write is reported once per
  host access.
- `remove` deletes both the v2 and the legacy v1 association (`:76-78`).
  Content files are never deleted; only metadata is removed.
- 404 removes the association and throws `NOT_FOUND`; `no-store` removes it
  after download (`HttpSourceLoader.java:138-154`).
- The clock is owned by the structured host access and passed only to the HTTP
  loader; no global clock is used (`HostRepositories.java:48-58`).

**Independent probes:** `PROBE_PASS 04` (404 removes v2+v1, keeps the digest
file, offline then misses), `PROBE_PASS 06` (8 concurrent readers, no partial
`.content-*`/`.metadata-*` files, offline reuse), `PROBE_PASS 07` (v1 offline
only, online republish, corrupt v2 does not fall back), `PROBE_PASS 08`
(no-store removes v1 and cannot resurrect it, content retained),
`PROBE_PASS 10` (write failure warns once and loads still succeed).
CLI `v1-offline` and `corrupt-v2-offline` passed.

### CACHE-04 — PASS

**Contract:** no ETag/Last-Modified persistence or conditional requests; no 304
retry; no second online verification (including negative sources); identity,
content coding, no-store, limits, redirect, proxy and timeouts intact; online
failures never silently use stale cache.

**Code audit:**
- Main-code grep finds no `If-None-Match`, `If-Modified-Since`, ETag storage or
  `Last-Modified` persistence. The only `304` use is an unsolicited-response
  guard that falls through to `IO_FAILURE` without retry
  (`HttpSourceLoader.java:124,144-145`).
- `verifyUnchanged`/`verifyUnavailable` and the `VerificationStarted` emission
  were removed from `RepositoryClosureService` (candidate diff).
- `Accept-Encoding: identity` is fixed (`HttpSourceLoader.java:117`), content
  coding is validated on 200 (`:146,186-192`), `no-store` is still excluded
  (`:180-183`), byte limits are enforced in the body subscriber and decoder
  (`:245-293,194-203`), redirects are limited to five and scheme-checked
  (`:114-136`), the proxy is captured per loader (`:206-207`) and timeouts are
  enforced (`:217-235`).
- Online failures propagate as `SourceLoadException`; only the explicit offline
  path reads cached bytes (`HttpSourceLoader.java:82-91`). The existing
  `Ili2cCachePolicyTest.expiredNetworkFailureNeverFallsBackButExplicitOfflineWorks`
  covers this and is in the passing 10-test suite.

**Independent probes:** `PROBE_PASS 03` and `PROBE_PASS 05` (all requests carry
`identity`; no conditional header; unsolicited 304 is `IO_FAILURE` with exactly
one request). CLI probes assert every recorded request has no
`If-None-Match`/`If-Modified-Since`, no `HTTP 304` message is emitted, and warm
runs perform zero HTTP requests. `PROBE_PASS 04`/`08` show 404/no-store
invalidate rather than silently reuse; the expired-network-failure case is
covered by the passing `Ili2cCachePolicyTest`.

### CACHE-05 — PASS

**Contract:** truthful chronological INFO `FRESH_HIT`; hit metrics; reserved
legacy codes not emitted; JSON/results/warnings/exits preserved; CLI four
commands.

**Code audit:**
- `FRESH_HIT` is added to `RepositoryCacheEvent` (`RepositoryCacheEvent.java:5`)
  and has DE/EN text in `DiagnosticMessageFormatter.java:96`.
- `DefaultCompilerCli` counts `FRESH_HIT` (and the existing offline/not-modified
  reasons) as a hit for `RunEvent.CacheLookup`
  (`DefaultCompilerCli.java:303`); the observer is called in occurrence order.
- `NOT_MODIFIED`, `VerificationStarted` and `ILI-RES-0013` remain reserved;
  main code emits none of them. The existing renderer/catalogue entries are
  harmless compatibility surfaces.
- No new diagnostic codes; catalogue stays v4; JSON, warnings, artifacts and
  exits are untouched by the cache change.

**Independent probes:** CLI `warm` produced exactly
`0011, 0010, 0012, 0010` (lookup, fresh index, selected, fresh model) with two
"fresh cache" lines, no `ILI-RES-0013`, no `HTTP 304`, and zero HTTP requests.
The same chronology is exercised for `check`, `compile`, `analyze` and
`diagram` in the original 22-case process evidence. My `changed-md5` case
produced a truthful `CACHE_MISS` before `DOWNLOAD`.

### CACHE-06 — PASS

**Contract:** audit original test XML/logs, intermediate failures and corrected
test oracles; 139 unique final passing cases using reused passing suites plus a
closure rerun (not a single green full run); SDK 9, CLI 22 and public 4; bind
actual runtime JARs and sources with the manifest.

**Evidence audit:**
- `targeted/` contains **26 suites / 139 tests / 0 failures / 0 errors /
  0 skips** (11 model in 3 suites, 90 repository in 13, 38 CLI in 10).
- `targeted-before/` contains the same 26 suites / 139 tests with exactly one
  failure: `RepositoryClosureContractTest` expected 6 but observed 3
  (the removed completion re-read). `closure-rerun/` is the corrected 15-test
  suite, 0 failures; its XML is identical to the closure XML used in
  `targeted/`.
- `red/` preserves the expected failure (4 requests vs 2; old completion
  recheck). `consumers-before/` preserves the old 3-vs-2 request oracle failure
  in `corruptBytesGiveMissThenDownloadAndNeverAFalseHit`.
- `processes-before.json` preserves the first process-oracle failure at
  `changed-model-md5` (extra truthful `CACHE_MISS` line); the corrected oracle
  is in the current helper, and `processes.json` has **22 passing cases**.
- `sdk-processes.json` has **9 commands** with `status: passed`;
  `public.json` has **4 cases**, all exit 0, source SHA-256 matches the existing
  input, 6 downloads on each initial run and 6 `FRESH_HIT` lines on each warm
  run with no 304/0013.
- `manifest.json` **401/401 input hashes** match the current worktree. The
  seven runtime JARs in `build/reviewer/distribution/lib/` match the manifest
  artifact hashes and the `processes.json` JAR map. The original `distZip` entry
  is not present in the review worktree, so that one archive hash could not be
  re-hashed; the runtime JARs actually used by the probes were verified.
- Test oracle changes are semantically justified by §17: expected completion
  calls 6→3, request counts 3→2, `NOT_MODIFIED`→`FRESH_HIT`, no `VERIFY`
  events, and weak-validator changed-source cases now expect the new policy.
  No golden, XFAIL or language/semantic diagnostic was changed.

## Findings

### N-1 (low, non-blocking) — stale supersession text in older spec sections

- **Files/lines:** `docs/spec/08-modellaufloesung-und-repositories.md:304-306`
  still says online index/model sources are re-read at completion and yield
  `CHANGED_SOURCE`; `docs/spec/08-modellaufloesung-und-repositories.md:191`
  still lists "Erstdownload/304/Änderung" in the RES-013 acceptance row;
  `docs/spec/17-abdeckungsmatrix-und-review.md:385` still summarises RES-013
  with "starke 304-Bestätigung".
- **Why it is non-blocking:** chapter 08 §17 is explicitly the authoritative
  follow-up contract, and §13/§16 already carry the one-line supersession note.
  The implementation matches §17; no code path performs the old completion
  recheck or uses ETags. This is editorial drift, not a migration regression.
- **Reproducer:**
  `grep -n "nochmals verglichen\|starke 304-Bestätigung\|Erstdownload/304" docs/spec/08-modellaufloesung-und-repositories.md docs/spec/17-abdeckungsmatrix-und-review.md`
- **Suggested follow-up:** add the same supersession note to §12 and update the
  RES-013 summary/acceptance wording to chapter 08 §17. This does not block
  the cache migration.

## Commands actually executed (own probes and audits)

| Command | Exit | Result |
|---|---|---|
| `git rev-parse HEAD HEAD^{tree}` | 0 | `c9fdd30…` / `1dd5a1f…` |
| `git status --porcelain` | 0 | empty (clean tracked tree) |
| `./build/reviewer/distribution/bin/interlis-compiler version` | 0 | 0.2.0-SNAPSHOT, diagnostics v4 |
| `"$JAVA_HOME/bin/javac" -cp 'build/reviewer/distribution/lib/*' -d build/reviewer/probes/classes build/reviewer/probes/CacheProbe.java` | 0 | compiled |
| `"$JAVA_HOME/bin/java" -cp 'build/reviewer/distribution/lib/*:build/reviewer/probes/classes' probe.CacheProbe …` | 0 | `CACHE_PROBE_PASS 13` |
| `node build/reviewer/probes/launcher-probe.cjs` | 0 | `LAUNCHER_PROBE_PASS 16` |
| manifest/input and artifact hash audit (Node) | 0 | 401/401 inputs; 7/7 supplied runtime JARs |
| JUnit XML aggregation (Node) | 0 | targeted 26/139/0; before 139/1; closure rerun 15/0 |

**Not executed (deliberately out of scope / reused evidence):** Gradle test
suites, full corpus, benchmarks, browser/platform matrix, release checks. The
constraints forbid them and the prompt says the original suites need not be
repeated. I reused the original XML/log evidence after checking its counts,
oracles, intermediate failures and manifest binding.

## Conclusion

All six cache criteria pass. The candidate implements the §17 TTL/MD5 policy,
keeps the typed request API and loader compatibility, preserves integrity,
pins, locks, cancellation, workspace gating and CLI output contracts, and its
evidence is internally consistent and bound to the actual sources/runtime JARs.
The only observation is low-severity editorial drift in older spec paragraphs,
which does not affect behavior or the verdict.

**Verdict: PASS**
