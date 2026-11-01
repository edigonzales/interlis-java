# Independent Review: HTTP Network Policy (NET-01 through NET-05)

**Verdict: PASS**

- Candidate: `53eb35743ee3e51afa809f74daea575c5a32ccf5`
- Tree: `8b3d6f4361ee3f36c35c954c4ecdcffe976d4a96`
- Base: `958f186a92361c3b827a5f0de13a8afee3fe0294`
- Worktree: `/Users/stefan/sources/interlis-java/build/review/http-network-policy/r1/worktree`
- Reviewer: OpenCode, `deepseek/deepseek-flash`, provider `deepseek`, `https://api.deepseek.com`
- Date: 2026-11-01
- No must-fix finding; both required own probe classes executed successfully.

## 1. Scope and constraints observed

The review was read-only for tracked files and historical evidence. I did not use
Gradle, Python, subagents, credentials/config sources, broad test/corpus/benchmark/
browser/release runs, or a model switch. All probes used local servers, isolated
temporary homes/caches, and scratch under `build/reviewer/probes`. The worktree
remained clean (`git status --porcelain` empty) and the exact candidate/tree above
was checked before and after the review.

The explicitly authorized `FetchPolicy` break is treated as intentional, not as a
compatibility finding. Historical reviewer sources such as
`docs/implementation/cache-logging/review/probes/LoaderProbe.java` intentionally
retain the old constructor and are not active SDK examples.

## 2. Candidate and evidence integrity

- `git rev-parse HEAD` = candidate SHA; `git rev-parse HEAD^{tree}` = requested
  tree SHA; worktree clean.
- Manifest audit (`node build/reviewer/probes/HashAudit.cjs`, exit 0):
  `942` product entries and `42` evidence entries, `0` mismatched, `0` missing;
  all `7` supplied distribution JAR hashes match `manifest.json` (`0` bad).
- Supplied launcher
  `build/reviewer/distribution/bin/interlis-compiler` has SHA-256
  `f07a68e741009bea4761403225815635bafe49deb6162ae4e835f2a1f41f50fc`, byte-identical
  to the launcher recorded by the implementation run. The supplied JARs also
  match `processes.json`.
- `git diff --check` both against the base and in the worktree exits 0.

### JUnit evidence

`node build/reviewer/probes/EvidenceAudit.cjs` (exit 0) parsed the retained XML
directly and confirmed:

- `22` suites, `126` tests, `0` skipped, `0` failures, `0` errors.
- Repository module: `15` suites / `86` tests.
- CLI module: `7` suites / `40` tests.
- Values match `junit-summary.json`.
- The retained `HttpNetworkPolicyTest` XML contains the nine named network-policy
  tests, including `responseHeadersResetInactivityTimer`,
  `redirectsAndConditionalRetryReceiveNewTotalBudgets`,
  `newTlsConnectionUsesConnectTimeout`,
  `httpsConnectSucceedsThroughExplicitProxy`, and
  `explicitProxyAndDirectInstancesAreConcurrentAndSystemSelectorIsCaptured`.

### CLI and SDK evidence

`EvidenceAudit.cjs` also confirmed:

- `processes.json`: `17` real CLI cases, `passed: true`, no signals, every exit
  matching the case expectation; request-shape checks passed (direct cases only
  origin requests, explicit/system/config proxy cases only absolute proxy
  requests, explain/usage/offline cases zero requests).
- `sdk-processes.json`: `2` processes (javac and java), both exit `0`.
- `processes-final.log` and `processes-r2.log` both contain
  `HTTP_NETWORK_PROCESSES_PASS 17`.
- `toolchain.json` records Java 25.0.3 Temurin, Node v22.13.0; my probes used the
  same Java and Node versions.

### Retained failed intermediate runs

`executions.json` contains 11 retained logs. The failures are present and are not
used as passing evidence:

- `network-tests.log` exit 1: compile error, unreported `InterruptedException` in
  the new test helper.
- `network-tests-r2.log` exit 1: `responseHeadersResetInactivityTimer` timed out
  because the test server did not flush headers; fixed and evidenced separately
  by successful `header-probe.log`.
- `processes.log` exit 1: distribution run inherited Java 21 and failed class
  version 69; `processes-r2.log` and `processes-final.log` are the Java 25 reruns.
- `cli-final-tests.log` exit 1: Javadoc `@param query` error; corrected, then
  `cli-final-tests-r2.log` succeeded.

This matches the explanations in `REPORT.md`. The final successful runs are
`targeted-tests.log`, `header-probe.log`, `distribution-build.log`,
`processes-r2.log`, `cli-final-tests-r2.log`, and `processes-final.log`.

### Reuse of repository tests after the Javadoc-only correction

The repository test XML is timestamped before the final CLI run; the final
`cli-final-tests-r2.log` recompiled `:interlis-repository:compileJava` after the
`RepositoryClient` Javadoc correction and then ran the CLI tests successfully.
I reconstructed the pre-correction Javadoc block from the candidate diff, compiled
the current and reconstructed `RepositoryClient.java` with `-g:none` against the
supplied JARs, and both produced the identical class hash
`3ac5353be64d276f73ab0bab481cea2c2a485955148b56bc2784cdf77f23a85b`
(`build/reviewer/probes/reuse.log`, exit 0). The correction is comment-only; the
repository-test reuse is acceptable. The actual Gradle run includes debug line
tables, which do not change executable semantics.

## 3. Requirement assessment

### NET-01 — FetchPolicy, defaults, validation, migration: PASS

`interlis-repository/src/main/java/guru/interlis/repository/FetchPolicy.java:16-37`
now has `offline`, `maxBytes`, `connectTimeout`, `readTimeout`, and
`Optional<Duration> requestTimeout`. Defaults are online, 16 MiB, 15 s connect,
40 s inactivity, no total request limit. The compact constructor rejects nulls,
nonpositive values, and durations not representable as `long` nanoseconds.
`defaults()` matches the approved 15/40/off decision.

Active consumers use the new five-component constructor:
`RepositoryNetworkOptions.java:45-52`, `ConfigurationLoader.java:44-46`, tests,
and `examples/sdk/RepositoryNetworkExample.java`. The removed old constructor and
`timeout()` are the authorized API break. `javap` against the supplied JAR
confirmed the new public constructor/accessors and the additive host overloads.

### NET-02 — Timing, budgets, cancellation, no partial cache: PASS

`HttpSourceLoader.java:188-227` measures elapsed time with `System.nanoTime()`,
initializes inactivity at send, resets it in the response-header callback and on
nonempty body bytes, and takes the minimum of read inactivity and the optional
total exchange limit. `receive()` resets `started` and `lastActivity` for every
redirect and every outer cache retry. Cancellation is polled at most every 50 ms;
the future and active body subscription are cancelled in the `finally` block.
`HttpTimeoutException` is mapped to `SourceLoadException.Reason.TIMEOUT`; an
external interruption is preserved separately; other `IOException` becomes
`IO_FAILURE`.

`BoundedBody` (`HttpSourceLoader.java:230-278`) bounds bytes before delegating,
cancels on overflow, and does not publish content on a failure. Cache publication
occurs only after a complete body has been decoded (`:132-143`). The retained
cache tests additionally cover corrupt bodies, 304 retries, no-store responses,
parallel writers, absence of `.tmp` leftovers, and offline reads; my probe also
confirmed zero cache files after a timed-out response.

Evidence: `HttpNetworkPolicyTest.java:29-127` (`defaultsAndValidation`,
header/body inactivity, progressing body vs total, header reset, redirect/retry
fresh budgets, timed-out content not cached, TLS connect timeout) and
`HttpSourceLoaderTest.java:29-157` (UTF-8, redirects, typed failures, timeout,
cancellation, oversize streaming). My `TimeoutProbe` independently re-executed
the timeout/redirect/budget/cache behaviors against the supplied distribution.

The pre-existing malformed-redirect-URI gap noted as OBS-02 below is not a
regression from this candidate and does not affect the timeout/cancellation
acceptance evidence. It is not a must-fix for NET-01–05.

### NET-03 — Proxy choice, HTTP/HTTPS CONNECT, isolation: PASS

`RepositoryProxyConfiguration.java:10-41` is a closed immutable host type with
`SystemDefault`, `Direct`, and validated `HttpProxy(host, port)`. `selector()`
captures the current JVM selector for `SystemDefault`, uses `NO_PROXY` for
`Direct`, and an explicit HTTP proxy selector for `HttpProxy`; a null JVM
selector becomes direct. `HttpSourceLoader.java:190-191` passes the selector to
`HttpClient` with redirect following disabled. `HostRepositories.java:24-49`
routes the proxy choice into the HTTP loader while leaving file loading local.

The retained JUnit evidence at `HttpNetworkPolicyTest.java:129-167` covers
concurrent direct/explicit/system loaders, capture of the system selector at
construction, unchanged system properties and default selector, offline not
contacting the proxy, HTTPS CONNECT, and TLS connect timeout. My own Java probe
additionally ran a direct loader, an explicit proxy, and an old-entry system
loader concurrently; it observed `origin`/`proxy`/`proxy` respectively with no
system-property or default-selector mutation. My Node launcher probe also
verified `--proxy localhost` (absolute proxy requests), JVM-default proxy, and
`--no-proxy` overriding a deliberately broken JVM proxy. No production code
calls `ProxySelector.setDefault` or mutates proxy properties.

### NET-04 — CLI/properties, precedence, validation, explain-config: PASS

`CommandSpec.java:41-51` adds `--connect-timeout`, `--read-timeout`,
`--request-timeout`, `--proxy`, `--proxyPort`, and `--no-proxy`.
`RepositoryNetworkOptions.java:12-76` validates timeouts and proxy hosts/ports,
applies defaults, rejects same-level proxy conflicts, derives the effective
`--proxy-mode`, and supplies the effective `FetchPolicy`. `ConfigurationLoader`
applies CLI over file over profile defaults, validates effective values before
source/network access, and emits deterministic provenance for `--explain-config`
including redacted URI values (`ConfigurationLoader.java:30-122`).
`DefaultCompilerCli.java:247-262` loads/explains before source loading;
`:289-312` passes the effective policy and proxy to the repository host;
`:648-651` documents the new options.

Retained tests: `RepositoryNetworkConfigurationTest.java:15-82` (defaults,
CLI/file precedence, proxy conflicts, invalid/secret-bearing hosts, invalid
timeouts before source loading, local compilation unaffected) and the CLI
application/configuration suites. My Node launcher probe independently confirmed
defaults, CLI-over-file precedence, config proxy use, `--no-proxy` override,
invalid-timeout/conflict/orphan-port exits with zero network requests, and
`--explain-config` origins/effective proxy mode.

### NET-05 — Cache/offline/pins/locks/diagnostics regressions: PASS

The retained 15 repository suites include `ContentStoreTest`,
`FileSourceLoaderTest`, `RepositoryCacheContractTest`,
`RepositoryCacheObservationTest`, `RepositoryCacheRegressionTest`,
`PinnedRealClosureTest`, `RepositoryClosureContractTest`,
`RepositoryIndexReaderTest`, `RepositoryParityIntegrationTest`,
`RepositoryRefreshServiceTest`, `RepositorySearchContractTest`, and
`ResolutionLockContractTest`, plus the network tests. Together they cover cache
hits/misses, corrupt metadata/body, no-store behavior, optional sites, pins,
resolution locks, refresh/search behavior, offline closure, and diagnostics.
No broad corpus/benchmark/browser/release run was required or performed.

### Cross-cutting checks

- **Atomic cache:** `DirectoryContentStore.java:40-52` writes a temporary file and
  atomically moves it when supported (with an explicit fallback for unsupported
  atomic moves); metadata is published only after a verified content lookup
  (`HttpSourceLoader.java:136-141`). `DirectoryMetadataStore.java:54-71` also
  writes via a temporary file and atomic move. Retained parallel-writer and
  `.tmp` tests pass; my timeout probe observed zero published files.
- **Typed failures/cancellation:** `SourceLoadException.Reason` retains
  `NOT_FOUND`, `ACCESS_DENIED`, `IO_FAILURE`, `TOO_LARGE`, `INVALID_ENCODING`,
  `OFFLINE`, `REDIRECT_LIMIT`, and `TIMEOUT`. Timeout maps to `TIMEOUT`;
  cancellation remains `OperationCancelledException`; retained tests cover both.
  OBS-02 records one pre-existing malformed-URI edge outside the changed surface.
- **No global production mutations:** production sources contain no
  `ProxySelector.setDefault`, proxy-property writes, or global cache/run state.
  My Java isolation probe and the retained JUnit test both observed unchanged
  system properties and default selector.
- **Old host entry points:** `HttpSourceLoader()` and 3-argument
  `HostRepositories.create/createObserved` remain and use `SystemDefault`;
  retained tests exercise them, and the SDK example compiles against the new
  overloads.
- **All three timeout fingerprint components:** `RepositoryClient.java:128-133`
  emits catalog v4 with connect, read, and optional request components. The
  retained `RepositoryClientTest.java:30-46` asserts four distinct revisions; my
  `TimeoutProbe` independently obtained four distinct revisions.
- **Public SDK construction:** `examples/sdk/RepositoryNetworkExample.java`
  compiled with `javac` and ran with `java` against the supplied
  `distribution/lib/*`; exit 0, output `connect=PT15S read=PT40S request=off`.

## 4. Own counterprobes (actual commands and exits)

All scratch artifacts are under
`/Users/stefan/sources/interlis-java/build/review/http-network-policy/r1/worktree/build/reviewer/probes`.

| Command | Exit | Result |
|---|---|---|
| `node build/reviewer/probes/HashAudit.cjs` | 0 | `manifest_product=942 manifest_evidence=42 bad=0 missing=0`; `distribution_jars=7 bad=0` |
| `node build/reviewer/probes/EvidenceAudit.cjs` | 0 | `junit suites=22 tests=126 skipped=0 failures=0 errors=0`; `cli_processes=17 passed=true`; `sdk_processes=2 exits=0,0`; `execution_logs=11`; `EVIDENCE_AUDIT_PASS` |
| `javac -cp 'build/reviewer/distribution/lib/*' -d build/reviewer/probes/classes build/reviewer/probes/TimeoutProbe.java` | 0 | compiled against supplied JARs |
| `java -cp 'build/reviewer/probes/classes:build/reviewer/distribution/lib/*' TimeoutProbe` | 0 | all listed timeout/proxy/cache/fingerprint probes passed; `TIMEOUT_PROBE_PASS` |
| `javac -cp 'build/reviewer/distribution/lib/*' -d build/reviewer/probes/classes build/reviewer/probes/BadRedirectCheck.java` | 0 | compiled |
| `java -cp 'build/reviewer/probes/classes:build/reviewer/distribution/lib/*' BadRedirectCheck` | 0 | `OBSERVED java.lang.IllegalArgumentException: Expected closing bracket for IPv6 address at index 8: http://[` |
| `node build/reviewer/probes/ProxyProbe.cjs` | 0 | `PROXY_PROBE_PASS 12` |
| `javac -cp 'build/reviewer/distribution/lib/*' -d build/reviewer/sdk examples/sdk/RepositoryNetworkExample.java` | 0 | public SDK example compiled |
| `java -cp 'build/reviewer/sdk:build/reviewer/distribution/lib/*' RepositoryNetworkExample` | 0 | `connect=PT15S read=PT40S request=off` |
| `javac -g:none -cp 'build/reviewer/distribution/lib/*' -d build/reviewer/probes/reuse/current-classes build/reviewer/probes/reuse/current/guru/interlis/repository/RepositoryClient.java` | 0 | current source compiled |
| `javac -g:none -cp 'build/reviewer/distribution/lib/*' -d build/reviewer/probes/reuse/pre-classes build/reviewer/probes/reuse/pre/guru/interlis/repository/RepositoryClient.java` | 0 | reconstructed pre-correction source compiled |
| `shasum -a 256 build/reviewer/probes/reuse/current-classes/guru/interlis/repository/RepositoryClient.class build/reviewer/probes/reuse/pre-classes/guru/interlis/repository/RepositoryClient.class` | 0 | identical class hash `3ac5353be64d276f73ab0bab481cea2c2a485955148b56bc2784cdf77f23a85b` |
| `javap -classpath 'build/reviewer/distribution/lib/*' guru.interlis.repository.FetchPolicy guru.interlis.repository.RepositoryProxyConfiguration guru.interlis.repository.HttpSourceLoader guru.interlis.repository.HostRepositories` | 0 | confirmed new five-component constructor/accessors and additive host overloads |
| `git rev-parse HEAD` | 0 | `53eb35743ee3e51afa809f74daea575c5a32ccf5` |
| `git rev-parse HEAD^{tree}` | 0 | `8b3d6f4361ee3f36c35c954c4ecdcffe976d4a96` |
| `git status --porcelain` | 0 | empty; tracked worktree clean |
| `git diff --check` | 0 | no whitespace errors |
| `shasum -a 256 build/reviewer/distribution/lib/*.jar` | 0 | all seven hashes match manifest/process evidence |

### Timeout probe output

`java ... TimeoutProbe` produced:

```text
PASS defaults-and-validation
PASS catalog-fingerprint-all-three-timeouts
PASS tls-connect-timeout elapsedMs=402
PASS read-timeout-before-headers elapsedMs=262 requests=1
PASS read-timeout-after-partial-body elapsedMs=285 requests=2
PASS progressing-body-resets-read-budget elapsedMs=1247 requests=3
PASS redirect-fresh-total-budget elapsedMs=521 requests=2
PASS total-budget-without-read-inactivity elapsedMs=456
PASS timed-out-content-not-cached files=0
PASS proxy-isolation proxyRequests=2 originRequests=1
TIMEOUT_PROBE_PASS
```

The probe uses local raw sockets, not the implementation test helpers. It checks
the 15/40/off API defaults and validation, four distinct catalog revisions, TLS
connect timeout, header inactivity, partial-body inactivity, progressing-body
reset, fresh redirect budget, total-limit enforcement with no read inactivity,
no cache publication after timeout, and concurrent proxy isolation.

### Proxy/precedence probe output

`node build/reviewer/probes/ProxyProbe.cjs` produced `PROXY_PROBE_PASS 12` with
these launcher exits (`build/reviewer/probes/proxy-probe-results.json`):

| Case | Exit | Observed |
|---|---:|---|
| `explain-defaults` | 0 | defaults 15/40/off, `proxy-mode=system` |
| `explicit-proxy-hostname` | 0 | all requests absolute through `--proxy localhost` |
| `system-proxy` | 0 | all requests through `-Dhttp.proxy*` |
| `direct-overrides-system` | 0 | all requests direct despite broken JVM proxy |
| `config-precedence` | 0 | file connect timeout, CLI read/request/proxy overrides |
| `properties-proxy` | 0 | all requests through properties proxy |
| `file-proxy-overridden-direct` | 0 | all requests direct after CLI `--no-proxy` |
| `invalid-timeout-no-network` | 2 | usage error, zero requests |
| `proxy-conflict` | 2 | usage error, zero requests |
| `orphan-port` | 2 | usage error, zero requests |
| `total-timeout-progressing` | 1 | TIMEOUT with reading progressing, after ~1.6 s; read timeout was 3 s |
| `read-inactivity-progressing` | 0 | success after ~5.3 s; 1 s inactivity budget reset by 300 ms chunks |

This probe uses the supplied actual launcher and isolated `HOME`/`user.home`,
origin and proxy servers on loopback, and the local model/index fixtures.

## 5. Findings

### Must-fix findings

None.

### OBS-01 — INFO, non-blocking: retained total-timeout process does not isolate read from total

`docs/implementation/http-network-policy/evidence/processes.json:535-570`
(`request-total-timeout`) and `tools/verification/http-network-processes.cjs:50`
use a server that delays headers for 1600 ms with both `--read-timeout 1` and
`--request-timeout 1`. The resulting TIMEOUT therefore does not by itself prove
the optional total limit rather than read inactivity. This is an evidence-clarity
issue only: the total limit is directly asserted by
`HttpNetworkPolicyTest.java:64-76`
(`progressingBodyExceedsInactivityBudgetButTotalLimitStillWins`) and by my
`TimeoutProbe` (`total-budget-without-read-inactivity`, 456 ms with read timeout
500 ms) and `ProxyProbe` (`total-timeout-progressing`, 3 s read vs 1 s total).
No product change is required for NET-01–05.

### OBS-02 — LOW, non-blocking, pre-existing: malformed redirect `Location` escapes as an untyped exception

`interlis-repository/src/main/java/guru/interlis/repository/HttpSourceLoader.java:98`
calls `current.resolve(location)` outside any `IllegalArgumentException` mapping;
the catch block at `:147-154` handles HTTP timeout, interruption, and `IOException`
but not malformed URI input. My local `BadRedirectCheck` probe with
`Location: http://[` observed a raw
`java.lang.IllegalArgumentException: Expected closing bracket for IPv6 address at index 8: http://[`.
In the CLI this would reach the generic `RuntimeException` handler at
`interlis-compiler-cli/src/main/java/guru/interlis/cli/DefaultCompilerCli.java:155-159`
and be reported as an internal failure rather than a typed source-load
`IO_FAILURE`. The same pattern pre-dates this candidate at the initial
`URI.create(locator.location())`; the candidate neither introduced nor worsened
it, and no NET-01–05 acceptance case depends on it. I do not treat it as a
must-fix for this review; it is a reasonable follow-up to map malformed redirect
locations to `SourceLoadException.Reason.IO_FAILURE`.

## 6. Not run / limitations

- No Gradle, broad suite, corpus, benchmark, browser, release, or deployment run,
  per the review constraints. The original JUnit and process evidence was audited
  instead; the reviewer ran only the narrow probes above.
- HTTPS CONNECT was not re-implemented in the reviewer probe because the retained
  local JUnit assertion at `HttpNetworkPolicyTest.java:155-167` directly checks
  `CONNECT localhost:443` and the proxied TLS body, and inspection found no
  uncertainty. TLS connect timeout was independently re-probed.
- The supplied provider/configuration evidence was inspected only as context; no
  credentials or configuration sources were accessed.
- OBS-01 and OBS-02 are the only findings; neither is a must-fix, and all
  NET-01–05 acceptance evidence is present.

## 7. Final determination

**PASS** for NET-01, NET-02, NET-03, NET-04, and NET-05. The candidate builds on
the approved API break, migrates active consumers, implements the 15/40/off
policy and per-instance proxy behavior, validates CLI/configuration input before
source/network access, preserves cache/offline/pin/lock/diagnostic behavior, and
is backed by complete retained evidence plus successful independent timeout and
proxy/precedence/isolation counterprobes.
