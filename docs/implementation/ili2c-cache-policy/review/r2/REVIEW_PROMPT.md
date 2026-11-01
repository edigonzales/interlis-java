# Independent follow-up review: resolve N-1 documentation drift

Review the new snapshot against the prior independently reviewed candidate.
Read request.json, prior review.md/verdict.json and your own original raw counterprobe
results. The prior full migration review passed CACHE-01..06 with zero must-fix
findings, 13 new Java and16 new launcher counterprobes. Only a low documentation
observation N-1 remained. The user requested correction and a repeated review.

Confirm git diff --name-only baseSha candidateSha has ONLY the two documented
spec files. Verify all production/test/helper trees are IDENTICAL to the prior
reviewed candidate; reuse the prior audited evidence and counterprobe results.
Do not repeat Gradle, own product probes, corpus, benchmarks, browsers or release
checks for this documentation-only follow-up. No Python, delegation, credentials.

N-1 correction: chapter08 RES-013 primary requirement/acceptance row now names
24h/7days and index MD5; the older closure paragraph no longer demands online
completion rechecks; observation text no longer claims strong ETags/completion
accesses. Matrix17 RES-013 now matches chapter08 section17 and current tests.
The original review accidentally named chapter09 for the acceptance row, but
that actual row is chapter08:191, not chapter09. Review original findings against
actual text. Earlier historical ETag sections have explicit supersession notes;
do not demand deleting historical evidence or reserved compatibility APIs.

Check existing requirement IDs/scope and remaining live summaries for contradictory
wording; do not add requirements or change behavior. Product/tracked files remain
read-only. Scratch is build/reviewer only. Write review.md and verdict.json in
reviewDirectory, naming both snapshot SHAs, whether N-1 is resolved, proof that
production/test/helper content is unchanged, reused counterprobe evidence,
actual document-check commands/exits, remaining findings and overall PASS/FAIL.
PASS requires no open finding relevant to this follow-up. Finish normally.
