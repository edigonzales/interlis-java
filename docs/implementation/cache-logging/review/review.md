# Independent review — source-specific repository cache logging

## Scope and candidate binding

- Review worktree: `/Users/stefan/sources/interlis-java/build/review/cache-logging/r1/worktree`
- `pwd` → the path above.
- `git rev-parse HEAD` → `27a3bfa42e55d63b165ec5d78ec1df896290dbe4`
- `git status --porcelain` → empty (clean detached worktree).
- Base: `59141fa88a642d2c8201970f978fd7befe288f26`; candidate is the single commit
  `27a3bfa Show source-specific repository cache access in verbose CLI output`.
- Read: repository `AGENTS.md`, `docs/spec/AGENTS.md`, the execution plan, the
  implementation report, the complete `base..candidate` product/test/docs diff,
  the relevant Java sources and tests, and the raw evidence.
- The implementation report was treated as a claim; the checks below use the
  committed code, the built distribution, and the raw artifacts.

## Evidence audit

### Manifest and hash binding

`node build/reviewer/verify-manifest.cjs "$PWD" "$PWD/build/reviewer/distribution"`
exited 0:

| Group | Checked | Matching | Mismatched/missing |
|---|---:|---:|---:|
| `files_sha256` | 622 | 622 | 0 |
| `test_sources_sha256` | 14 | 14 | 0 |
| `runtime_jars_sha256` (mapped original install dir → `build/reviewer/distribution/lib`) | 7 | 7 | 0 |

The seven distribution JAR hashes also equal the `jars` map in
`evidence/processes.json` (7/7). The clean candidate worktree therefore binds the
recorded test and process content to these exact files.

### JUnit evidence

All 14 original XML files under `evidence/junit/` were read directly:

| Suite | Tests | Fail | Error | Skip |
|---|---:|---:|---:|---:|
| `guru.interlis.repository.RepositoryCacheRegressionTest` | 7 | 0 | 0 | 0 |
| `guru.interlis.repository.RepositoryCacheContractTest` | 9 | 0 | 0 | 0 |
| `guru.interlis.repository.RepositoryCacheObservationTest` | 6 | 0 | 0 | 0 |
| `guru.interlis.repository.HttpSourceLoaderTest` | 5 | 0 | 0 | 0 |
| `guru.interlis.cli.CliApplicationTest` | 9 | 0 | 0 | 0 |
| `guru.interlis.cli.ColorOutputContractTest` | 7 | 0 | 0 | 0 |
| `guru.interlis.cli.DefaultRepositoryCacheTest` | 5 | 0 | 0 | 0 |
| `guru.interlis.cli.RepositoryCacheRendererTest` | 2 | 0 | 0 | 0 |
| `guru.interlis.cli.VerboseDiagnosticsContractTest` | 3 | 0 | 0 | 0 |
| `guru.interlis.cli.VerboseRepositoryCacheTest` | 4 | 0 | 0 | 0 |
| `guru.interlis.diagnostics.DiagnosticCatalogTest` | 3 | 0 | 0 | 0 |
| `guru.interlis.diagnostics.DiagnosticMessageFormatterTest` | 5 | 0 | 0 | 0 |
| `guru.interlis.diagnostics.DiagnosticPresentationTest` | 2 | 0 | 0 | 0 |
| `guru.interlis.model.VersionManifestTest` | 2 | 0 | 0 | 0 |
| **Total** | **69** | **0** | **0** | **0** |

The final `RepositoryCacheObservationTest` XML has 6 cases and timestamp
`2026-11-01T17:55:52.217Z`, later than the other suites, consistent with the
report’s statement that its final version supersedes the earlier one. It
contains the new offline-replay case exercising the old `Access` and
`RepositoryClosureService` constructors. The seven
`RepositoryCacheRegressionTest` cases are unchanged by the candidate and appear
green in their XML: `weakEtagDoesNotHideAChangeDuringFinalVerification`,
`lastModifiedDoesNotHideAChangeDuringFinalVerification`,
`weakEtagCannotHideOnlineLockOrPinMismatchButOfflineLockRetainsOldBytes`,
`lastModifiedCannotHideOnlineLockOrPinMismatchButOfflineLockRetainsOldBytes`,
`optionalOfflineSiteMissIsNotARequiredSourceFailure`,
`malformedOptionalSiteWarnsWithoutBecomingAnIndexFailure`,
`a304MustConfirmTheExactStrongEtagBeforeReusingBytes`.

The task logs (`evidence/targeted.log`, `final-cli.log`,
`legacy-constructors.log`, `final-build-portable.log`) all end with
`BUILD SUCCESSFUL`; the executed task names match the described runs.

### Distribution-process evidence

`evidence/processes.json` was read in full. It contains 29 actual cases,
`passed: true`, Node `v24.22.0`, Java home `25.0.3-tem`, and 7 artifact hashes.
The recorded exits are 0 for 27 cases, 1 for `optional-site` and 1 for
`second-304` (both expected by the harness). I checked the recorded stderr and
HTTP rows for the high-signal cases:

- `cold`: 4 INFO lines, HTTP `200,200,304,304`, two downloads and two confirmed
  304 hits, including the completion accesses.
- `hot-*` for `check/compile/analyze/diagram`: 4 INFO lines each, all 304.
- `compact-*`: 0 INFO; `json-*` and `json-verbose-*`: 0 INFO, equal JSON.
- `de-*`: `Information [ILI-RES-0010]`.
- `offline-false`/`offline-true`: 2 INFO lines each, 0 HTTP.
- `optional-site`: index hit, `ilisite.xml` offline miss, `ILI-RES-0001`, no
  `ILI-RES-0003`.
- `unconfirmed-304`: neutral miss plus a real 200 download.
- `second-304`: two neutral misses, no hit, exit 1.
- `no-cache`/`no-store`: four downloads, no cache-hit wording.
- `color-always`/`color-never`: ANSI present/absent as expected.
- `version`: `diagnosticCatalogVersion` = `interlis-diagnostics/v3`.

### Portable evidence

`evidence/portable.json` binds a source fingerprint and three built artifacts.
I recomputed the fingerprint in both the review worktree and the main checkout:
both produce `4c99e154cbd2415e0c0f3f032401582c81dabed3070f90d3b09245fa9e737362`,
exactly the evidence value. The main checkout is at the same clean candidate
`27a3bfa…`, and the three artifact hashes match 3/3. `portable-final.log` ends
with `repository-diagnostic PASS: JS and WasmGC diagnostics and causes agree`.
`repository-diagnostic-contract.cjs` asserts the existing protocol/capabilities
(`protocolVersion 1.0`, languages `2.3/2.4`) and the absence of
`ILI-RES-0010` in compiler diagnostics; `BridgeCodec.capabilities()` contains no
new catalog-version wire field. The JVM distribution reports catalog v3.

## Independent counterprobes

Environment: Node `v24.22.0`; `javac`/`java` Temurin `25.0.3+9-LTS`; supplied
distribution under `build/reviewer/distribution`; all scratch output under
`build/reviewer`.

### Real launcher + asynchronous local HTTP server

`node build/reviewer/probes/counterprobes.cjs "$PWD"` exited 0 with
`COUNTERPROBES_PASS 22`. Independent assertions (not copied from the
implementation harness) covered:

1. Cold run: 4 INFO lines, first two `source downloaded`, last two
   `content used from cache (HTTP 304)`, HTTP `200,200,304,304`.
2. Warm run: 4 INFO lines, all four requests 304.
3. Non-verbose: 0 INFO. `--json --verbose`: 0 INFO, stderr equal to
   non-verbose, parsed JSON equal, no `ILI-RES-0010` in JSON.
4. German: `Information [ILI-RES-0010]` and
   `Inhalt aus Cache verwendet (HTTP 304)`.
5. `--color always`: cyan `INFO` and gray code spans; `never`: no ANSI.
6. Unconfirmed 304: neutral `cache could not be used` followed by a real 200
   `source downloaded`; no hit and no retry wording.
7. Always-304 second attempt: exactly two neutral misses for the model, no hit,
   exit 1.
8. Offline with and without `--read-lock`: 2 hits, 0 HTTP, “content used from
   cache offline”.
9. Optional site absent: index offline hit, `ilisite.xml` offline miss,
   `ILI-RES-0001`, no `ILI-RES-0003`. Fresh offline cache (required index
   absent): `ILI-RES-0003` with `ilimodels.xml` offline miss.
10. Corrupt cached bytes: neutral miss, fresh download, then a confirmed 304 on
    the completion access.
11. `--no-cache` and `no-store`: four downloads, no cache-hit wording.
12. Verbose does not change the diagram Mermaid export (stdout equal); compile
    stdout equal; verbose adds 4 INFO and non-verbose 0.

A separate real-launcher probe used a synthetic repository URL with userinfo;
the direct loader probe used query and fragment. Exit 0; every
published/message source was `http://127.0.0.1:PORT/...` with no userinfo,
query or fragment.

### Direct distribution-JAR probes

`javac -cp 'build/reviewer/distribution/lib/*'` compiled two reviewer-owned
consumers; both exited 0:

- `SanitizeProbe`: `SANITIZE_PROBE_PASS checks=26`. It verified public
  observation sanitization (userinfo/query/fragment removed, invalid source
  neutralized, global source omitted), exact EN/DE text and color spans,
  neutral `CACHE_MISS` (no “retry”, “missing”, “stored”, “erneut”, “fehlt”,
  “gespeichert”), `DOWNLOAD` not claiming persistence, empty renderer result for
  `WRITE_UNAVAILABLE`, terminal/bidi escaping with printable Unicode preserved,
  and independent repeated observations.
- `LoaderProbe`: `LOADER_PROBE_PASS checks=14 requests=7`. Against a real JDK
  HTTP server it verified one `DOWNLOAD`, one `NOT_MODIFIED`, and one
  `OFFLINE_HIT` per actual access with sanitized sources; zero HTTP on offline;
  legacy three-argument `HostRepositories.create` callbacks are exactly
  `DOWNLOAD, NOT_MODIFIED`; throwing detailed and legacy observers do not change
  loading.

## CL criteria

| Criterion | Verdict | Basis |
|---|---|---|
| CL-01 | PASS | Source-specific observations for HTTP and both offline paths; old enum/Access/Closure constructors and the new detailed observer are additively wired; one event per actual access; observer failures isolated. Code audit (`HttpSourceLoader`, `RepositoryClosureService`, `HostRepositories`, `CacheObservations`), JUnit, `LoaderProbe`, 22 launcher cases. |
| CL-02 | PASS | Published observations remove userinfo, query and fragment; no headers; renderer escapes terminal/bidi controls while preserving printable Unicode. `SanitizeProbe`, credential launcher probe, `LoaderProbe`, JUnit. |
| CL-03 | PASS | Common `INFO`/`Information [ILI-RES-0010]`, central formatter, DE/EN, color policy and `ConsoleDiagnosticRenderer`; each actual access including the completion check is visible. Launcher probes, renderer probe, JUnit. |
| CL-04 | PASS | Output only with `--verbose` and without diagnostic `--json`; `WRITE_UNAVAILABLE` is not duplicated as INFO; diagnostics, warning policy, gate, JSON and exports unchanged. Compact/JSON/export equality probes, `--warnings reject` warm run, JUnit. |
| CL-05 | PASS | Correct wording for confirmed strong 304, offline, download, no-store/no-cache, corrupt body, unconfirmed and second 304; `CACHE_MISS` is neutral and promises neither missing bytes nor a retry. Counterprobes, JUnit, process evidence. |
| CL-06 | PASS | Seven newest cache-correction tests unchanged and green; optional sites remain optional and required index failures distinct; targeted unit/HTTP/distribution raw evidence present. Regression XML, process JSON, independent optional/required cases. |
| CL-07 | PASS | Catalog v3 and API/spec/status synchronized; JVM reports v3; narrow portable probe checks existing protocol/capabilities and absence of host INFO in compiler diagnostics, with source/artifact hashes bound. Code, docs, `version`, portable evidence. |
| CL-08 | PASS (within reviewer visibility) | This is an actual independent review in a clean separate checkout, using DeepSeek V4.1 Flash 1017 directly, with raw-evidence audit and own counterprobes; no open must-findings. Process/session exit binding remains the orchestrator’s external check. |

## Findings

No blocking or actionable product findings.

Non-blocking editorial observation: `docs/spec/09-diagnostik-logging-und-telemetrie.md:454`
still describes what catalog `v2` added (`ILI-RES-0008/0009`); the current
catalog `v3` is stated later at line 492 and matches the code, CLI and API
register. This is a historical description, not a contradictory current
contract.

## Limitations of this review

No Gradle, Python, corpus, benchmark, browser, platform or release gate was run.
The narrow test scope deliberately reused the committed, hash-bound raw JUnit
and process evidence for unchanged suites; the independent probes above are the
additional checks. CL-08’s process/session binding is audited externally after
this report.

## Verdict

**PASS** for candidate `27a3bfa42e55d63b165ec5d78ec1df896290dbe4`, with all
CL-01–CL-08 satisfied in the reviewed product and evidence and no open
must-findings.
