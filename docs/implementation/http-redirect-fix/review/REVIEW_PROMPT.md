# Independent review: malformed HTTP redirect fix

This is a narrow follow-up to OBS-02 in the completed HTTP-network-policy review.
Review only REDIR-01..REDIR-05. The base already includes the independently
accepted timeout/proxy work; do not repeat that entire review. No API or CLI
production change is intended. git diff BASE -- the production HttpSourceLoader
should show only three narrow URI/Request-construction exception boundaries.

Read the request, plan, short report and changed code/tests. Independently audit
original evidence: pre-fix new loader test 5 cases with 4 failures due to raw
IllegalArgumentException, post-fix repository 26 cases/four suites, one CLI test
with eight combinations, eleven actual new-distribution processes. Interpret
command exits honestly: green.log's repo component passed, overall group failed
on a new CLI test assumption; cli-green-r2.log is the final successful CLI/build
run. Stale-distribution process failures are retained, not counted as PASS.
All Gradle executions use explicit Java25, including launcher/daemon.

Assess that invalid Location syntax and Request-builder rejection become
SourceLoadException(IO_FAILURE) with constant safe messages; initial URI parsing
uses the same narrow boundary. Do not require swallowing arbitrary runtime
exceptions from cache stores, observers or unrelated internal logic. Existing
relative redirect, unsupported scheme, redirect limit, cancellation, timeout and
cache behavior must remain intact. Check secret-safe public diagnostics and
no successful/partial cache publication for failed redirect exchanges.

Verify hash bindings and real runtime JARs. Compile/run one own NEW focused
Java or Node counterprobe using distribution/lib/* or the supplied launcher;
use a different malformed Location and a valid relative-redirect control.
Inspect the original CLI raw outputs/exits (1, not 3) and structured IO_FAILURE.
No Gradle, Python, delegation, credentials, broad suites/corpus/benchmarks/browser
or release gates. Product/tests/docs and historical evidence remain read-only.
Scratch belongs under build/reviewer. No need to run all original tests again.

Write review.md and verdict.json to reviewDirectory with candidate SHA,
REDIR-01..REDIR-05 results, findings/severity, own actual commands/exits and
PASS/FAIL. PASS requires no open must-fix findings and the own counterprobe.
Finish the OpenCode process normally.
