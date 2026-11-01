# Independent Review: Malformed HTTP Redirect Fix (REDIR-01 through REDIR-05)

**Verdict: PASS**

- Candidate: `7fa83ef1bfdec7f8dacc2925c5c27dd4cfa891f3`
- Tree: `4a2a597b38dc4d35ac3818f1a0bca6295fe16d46`
- Base: `53eb35743ee3e51afa809f74daea575c5a32ccf5`
- Worktree: `/Users/stefan/sources/interlis-java/build/review/http-redirect-fix/r1/worktree`
- Reviewer: OpenCode, `deepseek/deepseek-flash`, provider `deepseek`, `https://api.deepseek.com`
- Date: 2026-11-01
- No must-fix findings; the required own redirect counterprobe passed.

## 1. Scope and constraints observed

This was a narrow follow-up to OBS-02. I reviewed only REDIR-01 through REDIR-05,
the production `HttpSourceLoader` diff, the two changed test files, the process
helper, and the two spec updates. I did not repeat the prior network-policy
review, did not rerun its broad suites, and did not use Gradle, Python,
subagents, credentials, or corpus/benchmark/browser/release runs.

Tracked production, test, documentation and historical evidence files were read
only. The worktree remained clean before and after the review. The only new files
are under `build/reviewer`, and the two review products are in the requested
output directory.

## 2. Candidate and diff integrity

- `git rev-parse HEAD` = candidate SHA; `git rev-parse HEAD^{tree}` = requested
  tree SHA; `git status --short` was empty.
- The production change is exactly one file:
  `interlis-repository/src/main/java/guru/interlis/repository/HttpSourceLoader.java`.
  No CLI, API, formatter, compiler, workspace, or build production file changed.
- The production diff contains exactly three narrow
  `IllegalArgumentException` boundaries:
  1. initial `URI.create(locator.location())`;
  2. `HttpRequest.newBuilder(current).GET()`;
  3. redirect `current.resolve(location)`.
- Each boundary maps to `SourceLoadException(IO_FAILURE)` with a fixed message:
  `invalid HTTP source address` for the first two, and
  `invalid HTTP redirect address` for the redirect-location case. The original
  `IllegalArgumentException` remains the internal cause.
- The loader does not catch arbitrary `RuntimeException`; cache stores, observers
  and unrelated internal logic are not swallowed.
- The cache write occurs only after a successful `200` response and decode. A
  failed redirect throws before `store.put` or metadata publication, so there is
  no successful or partial cache publication for the failed exchange.

## 3. Original evidence audit

### RED evidence

`evidence/red/TEST-guru.interlis.repository.HttpRedirectFailureTest.xml` and
`evidence/red.log` show 5 new loader tests, 4 failures, 0 errors, 0 skips. The
failures are raw `java.lang.IllegalArgumentException` (with underlying
`URISyntaxException` where applicable), matching the original OBS-02. The source
and test hashes in `evidence/red-source.json` bind to the base production file
`9de52549...` and the candidate test file `f973d199...`; both hashes were
recomputed and matched.

### GREEN evidence

- Repository: 26 tests in 4 suites, 0 failures, 0 errors, 0 skips
  (`HttpRedirectFailureTest` 5, `HttpSourceLoaderTest` 5,
  `HttpNetworkPolicyTest` 9, `RepositoryCacheRegressionTest` 7).
- CLI: one test method with 8 combinations (2 malformed targets x 4 commands),
  0 failures, 0 errors, 0 skips.
- `evidence/junit-summary.json` totals 5 suites, 27 tests, 0 failures/errors/skips.
- `evidence/green.log` overall exit 1 is honest: the repository component passed;
  the overall group failed on the first CLI test oracle assumption. The retained
  intermediate XML confirms that earlier assumption was wrong, and
  `evidence/cli-green-r2.log` is the final successful CLI/build run with exit 0.
- The spec updates in chapters 08 and 13 match the observed contract and the
  accepted implementation, including the prohibition on a broad
  `RuntimeException` catch.

### Process evidence

- `evidence/processes.json` contains 11 cases: 8 invalid target/command
  combinations with exit 1, unsupported scheme with exit 1, redirect limit with
  exit 1, and a successful valid relative redirect with exit 0.
- All invalid cases have `signal: null`, no raw `IllegalArgumentException`, no
  `internal failure`, and no `marked-secret` leak. JSON commands expose
  structured `reason=IO_FAILURE`; the diagram case is text-mode and exposes
  `ILI-RES-0003`. The in-process CLI test independently asserts the structured
  `reason=IO_FAILURE` diagnostic for all four commands.
- `evidence/intermediate/stale-distribution-process.json` retains the earlier
  exit-3 stale-runtime failure and is marked `passed:false`; it is not counted as
  a new-distribution PASS.
- The 7 JAR hashes in `evidence/processes.json` match the supplied
  `build/reviewer/distribution/lib/*.jar` byte-for-byte. The repository JAR also
  contains both fixed message strings, and the candidate source hash matches the
  manifest entry `d4a7fe1a...`.

### Hash bindings

- Manifest product audit: 944 entries, 944 matches, 0 missing, 0 mismatched.
- Manifest evidence audit: 23 entries, all hashes matched.
- All 7 distribution JAR hashes matched the manifest and `processes.json`.
- All six review-scope file hashes matched the manifest.
- Toolchain evidence records Gradle 9.8.1 with launcher and daemon on Java 25.0.3,
  matching the required explicit Java 25 setup; Node 22.13.0 was used only as a
  local process helper by the implementation.

## 4. Own new counterprobe

I compiled and ran a new focused Java probe against the supplied distribution
JARs, using a different malformed Location value than any original case:

- Malformed Location: `http://[::1/%ZZ?token=probe-secret-7f3a`
- Expected and observed: `SourceLoadException` with `reason=IO_FAILURE`, message
  exactly `invalid HTTP redirect address`, no secret in the message, and internal
  cause `IllegalArgumentException`.
- Relative-redirect control: Location `index.xml`, served from the local HTTP
  server; observed `valid-probe` content loaded successfully.

Command and result:

```sh
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
PATH=/Users/stefan/.sdkman/candidates/java/25.0.3-tem/bin:$PATH \
javac -cp 'build/reviewer/distribution/lib/*' \
  -d build/reviewer/probe build/reviewer/probe/RedirectCounterProbe.java

JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
PATH=/Users/stefan/.sdkman/candidates/java/25.0.3-tem/bin:$PATH \
java -cp 'build/reviewer/probe:build/reviewer/distribution/lib/*' RedirectCounterProbe
```

Exit 0. Output:

```text
MALFORMED_REDIRECT typed IO_FAILURE safe-message cause=IllegalArgumentException
RELATIVE_REDIRECT control loaded=valid-probe
REDIRECT_COUNTERPROBE_PASS
```

## 5. Criterion results

| Criterion | Result | Basis |
| --- | --- | --- |
| REDIR-01 | PASS | Invalid Location syntax and request-builder rejection become `SourceLoadException(IO_FAILURE)`; initial parsing uses the same narrow boundary; test and own probe confirm the typed result and internal cause. |
| REDIR-02 | PASS | Messages are fixed and value-free; public CLI output contains no secret or raw exception; failed redirect exchange publishes no content or success observation. |
| REDIR-03 | PASS | Relative redirects, unsupported scheme, redirect limit, cancellation, timeout and cache behavior remain covered by the retained 26 repository cases; own relative-redirect control passed. |
| REDIR-04 | PASS | CLI test covers 8 combinations with exit 1 and structured `reason=IO_FAILURE`; 11 real distribution processes show exit 1 for failures, exit 0 for the valid relative redirect, and no exit 3 on the final distribution. |
| REDIR-05 | PASS | This independent OpenCode/deepseek-flash review was performed on the fixed snapshot with the raw evidence audit, hash checks, and a new local counterprobe; no must-fix finding remains. |

## 6. Findings

**Must-fix findings: none.**

**Non-blocking observations:**

- The diagram distribution case prints localized text and shows `ILI-RES-0003`;
  the structured `reason=IO_FAILURE` is asserted by the in-process CLI test rather
  than by that text renderer. This is existing CLI presentation behavior, not a
  redirect-fix defect.
- The implementation process evidence records commands from its own install
  directory, but the recorded JAR hashes exactly bind those runs to the supplied
  review distribution. The stale-runtime exit-3 attempts are explicitly retained
  as failures and are not used as PASS evidence.
- `docs/implementation-status.md` correctly leaves the review open at candidate
  creation; no review result is pre-claimed.

## 7. Not run

- No Gradle or JUnit suite was rerun; the retained XML/log evidence was audited
  and reused, as requested.
- The prior network-policy review and its broad probes were not repeated.
- No corpus, benchmark, browser, platform, performance, or release gate was run.
