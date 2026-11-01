# Independent HTTP network policy review

Review the exact candidate and NET-01 through NET-05, following request.json.
The approved user plan is in the implementation report and EXECUTION_PLAN.
Defaults 15s connect, 40s read inactivity, no total exchange deadline; JVM default
proxy unless explicitly overridden. FetchPolicy API cleanup is intentional and
all active consumers must migrate. Read timeout begins at send, resets on final
response headers and nonempty body bytes. Optional total covers each complete
exchange. Redirects and the existing conditional retry get fresh budgets.

Read implementation, API contracts and original raw evidence independently.
Verify manifest hashes, 126 JUnit cases / 22 suites with zero skips/failures,
17 real CLI processes and two SDK processes. Actual failed intermediate runs
are retained and explained; they are not passing evidence. The repository tests
precede a comment-only RepositoryClient Javadoc correction; assess this reuse.
The final CLI tests and process run bind the final changed product. Confirm JAR
hashes of the supplied distribution. Do not rerun broad suites or use Gradle.

Add your own narrow counterprobes using Java/javac against distribution/lib/*
and/or Node plus the supplied actual launcher. At least one independent timeout
probe and one independent proxy/precedence/isolation probe must execute. HTTPS
CONNECT and TLS connect timeout already have original local JUnit evidence;
inspect those assertions and add focused probes wherever uncertainty remains.
Use local servers, isolated temporary homes/caches and scratch under build/reviewer.
Do not edit tracked files or historical evidence. No delegation, Python, model
switches, credentials/config access, corpus, benchmarks, browsers or release gates.

Assess atomic cache behavior, typed failures/cancellation, no global production
mutations, old host entry points, CLI validation and precedence, explain-config,
all three timeout fingerprint components and public SDK construction. Do not
invent compatibility requirements opposed to the explicitly approved API break.

Write review.md and verdict.json to reviewDirectory, with candidate SHA,
NET-01..NET-05 statuses, actual own commands/exits, findings with severity and
file/line references, and clear overall PASS/FAIL. PASS requires no open must-fix
findings and completed own probes. Finish the OpenCode process normally.
