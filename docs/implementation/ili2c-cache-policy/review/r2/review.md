# Independent follow-up review: N-1 cache specification wording

- **Candidate:** `c92669b9a6b6e08ea6881fbaae8a22a9ebda05b3`
- **Tree:** `f41750968283b5c760ee05c04e7bb33c67b6b0d6`
- **Base / prior PASS candidate:** `c9fdd30a4c2f142f6dd5e16576333424b6e10349`
- **Prior tree:** `1dd5a1f09e652397cdc5423915f6be837c21703b`
- **Review worktree:** `/Users/stefan/sources/interlis-java/build/review/ili2c-cache-policy/r2/worktree`
- **Review directory:** `/Users/stefan/sources/interlis-java/build/review/ili2c-cache-policy/r2/output`
- **Overall verdict:** **PASS**
- **N-1:** **Resolved**. No open finding relevant to this follow-up.

## Scope and method

This is the requested documentation-only follow-up to the independent `r1`
review. The prior review passed CACHE-01..06 with zero must-fix findings and one
low documentation observation, N-1. I read `request.json`, the prior
`review.md`/`verdict.json`, the prior raw counterprobe logs, the actual candidate
diff and the two changed specification sections. I did **not** repeat Gradle,
product probes, corpus, benchmarks, browser or release checks.

The candidate is one commit directly on top of the prior reviewed candidate:

```text
c92669b (HEAD) Review snapshot: resolve obsolete cache specification wording
c9fdd30        Review snapshot: ili2c MD5 and age cache policy
```

`git show --stat` reports exactly the two documented files (11 insertions,
9 deletions). The worktree was clean before and after the review.

## Change containment: only the two spec files

```text
git diff --name-only c9fdd30a4c2f142f6dd5e16576333424b6e10349 c92669b9a6b6e08ea6881fbaae8a22a9ebda05b3
docs/spec/08-modellaufloesung-und-repositories.md
docs/spec/17-abdeckungsmatrix-und-review.md
```

Excluding those two paths, the diff is empty:

```text
git diff --name-status c9fdd30... c92669b... -- . \
  ':(exclude)docs/spec/08-modellaufloesung-und-repositories.md' \
  ':(exclude)docs/spec/17-abdeckungsmatrix-und-review.md'
(no output)
```

Production, test, helper and evidence trees are byte-identical to the prior
reviewed candidate. Top-level tree hashes compared at both commits:

| Path | Result |
|---|---|
| `interlis-compiler` | SAME `0e68e7410120eb902869d8a18bfa1ee8e8c7ef39` |
| `interlis-compiler-cli` | SAME `88f011ed63a3ae88b694a5153e191b32eac356dc` |
| `interlis-lsp` | SAME `01db854ec529245c3c2419eede3eb9bbfc71d655` |
| `interlis-model` | SAME `effb58b55c37a2c80df215559aee22de0a6bc4b1` |
| `interlis-projection` | SAME `de579d3fe757d53318d2c8dbc0f1c357b1f64e71` |
| `interlis-repository` | SAME `8b904b0b7a74ef3d3aef58e6fd77fcc7a71b47aa` |
| `interlis-wasm-bridge` | SAME `fc7c41f1e5b23f796b2f170c9847ad95f7672d96` |
| `interlis-wasm-teavm` | SAME `539549f989d1912596c32c18fc3d352d592972da` |
| `interlis-wasm-webimage` | SAME `f83aea1fe2f448ef99f44bdb1e2dde72c92d2802` |
| `interlis-workspace` | SAME `ac5f01f3860c60a035bc79709ba85b50091dfd26` |
| `tools` | SAME `61e98e204944992aaeafcd2d04ea27c1c19db8d7` |
| `testdata` | SAME `87ea15159c7bd3999c06d72821dfd4e2d88b00e7` |
| `examples` | SAME `c7d55e8399bcd1bb4ca352fd8abda0bf3f3c94f2` |
| `gradle` | SAME `bc27942ea6d5f8a02447444ca807a8b4142603b9` |
| `docs/implementation` | SAME `0b9128e1ebaecf83579d05ae9d0eff2839d4cd80` |
| `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, `README.md`, `AGENTS.md`, `LICENSE`, `.gitignore`, `.github` | SAME |

Only `docs/spec` differs, and within it the name-only diff above shows exactly
the two reviewed files. Therefore every production/test/helper input behind the
prior PASS is unchanged.

## N-1 resolution audit

The prior N-1 observation had three parts. All three are corrected:

1. **Chapter 08 RES-013 requirement/acceptance row, `08:191`.**
   The requirement now names the authoritative §17 policy ("Cachefristen und
   veröffentlichte Modell-MD5 vor einer Beschaffung") and the acceptance column
   names "Erstdownload, 24h-/7Tage-Fristen, MD5 vor Download, Abschaltung,
   Offline ohne Netz, Lockreplay und korrupte/parallel geschriebene Einträge".
   The ID, priority (`P2`) and owner are unchanged; no new requirement was added.
2. **Older closure paragraph, `08:304-309`.**
   It no longer demands an end-of-run online re-read. It now says reuse follows
   cache age and optional index MD5 per §17 and that a second online completion
   check is removed. Contradictory bytes encountered during normal retrieval
   still remain `CHANGED_SOURCE`/pin/lock errors, matching §17.
3. **Observation text, `08:547-550`.**
   It no longer claims strong ETags or completion accesses. It now states that
   additional online completion checks and ETags are gone per §17 while optional
   sites, pins and locks remain. This matches §17.

**Matrix 17, `17:385`.** The RES-013 row now points to "Kapitel08 §17",
names 24h/7-day TTLs and MD5-before-download, states "keine ETags oder zweite
Onlineprüfung", and names `Ili2cCachePolicyTest`, `RepositoryCacheContractTest`
and `DefaultRepositoryCacheTest`. Those tests exist in the unchanged tree
(10, 9 and 5 `@Test` methods respectively) and cover the named policy points,
including inclusive boundaries/no age renewal, MD5 before request, offline/no-lock
and lock replay, disabled cache, and corrupt/parallel entries.

**Actual row location.** I checked the original finding against the actual text:
the RES-013 acceptance row is `docs/spec/08-modellaufloesung-und-repositories.md:191`.
Chapter 09 contains cache-observation prose but no RES-013 acceptance row. The
correction was therefore applied at the correct location.

**Requirement IDs and scope.** The set of requirement IDs is unchanged in both
documents (13 requirement rows in chapter 08; 290 unique matrix IDs in chapter
17, identical before and after). Only wording changed; no behavior, scope or
requirement was added or removed.

**Historical sections.** Sections 08 §13 and §16 still contain older
ETag/304/completion/validator prose, but both begin with an explicit note that
those specific details are superseded by the §17 cache follow-up. This is
intentionally retained historical evidence, not a live contradictory summary.
The change does not delete historical evidence or reserved compatibility APIs.

## Reused prior evidence

- Prior `r1` verdict: **PASS**, CACHE-01..06, 0 must-fix findings.
- Prior worktree was clean at `c9fdd30` / tree `1dd5a1f`.
- Raw `build/reviewer/probes/java-probe.log`: 13 `PROBE_PASS` cases plus
  `CACHE_PROBE_PASS 13`.
- Raw `build/reviewer/probes/launcher-probe.log`: `LAUNCHER_PROBE_PASS 16`.
- The relevant source, helper and test trees are byte-identical (table above),
  so those audited results remain valid for this snapshot. No counterprobe was
  rerun for this documentation-only follow-up, as instructed.

## Document checks actually executed

All commands were run in the r2 worktree unless noted.

| Command | Exit | Result |
|---|---|---|
| `git rev-parse HEAD HEAD^{tree}` | 0 | `c92669b...` / `f417509...` |
| `git log -1 --format=%H%n%P` / `git show --stat --summary HEAD` | 0 | parent `c9fdd30...`; only the two spec files changed |
| `git status --porcelain` | 0 | empty (clean worktree) |
| `git diff --name-only c9fdd30... c92669b...` | 0 | exactly the two documented files |
| `git diff --name-status c9fdd30... c92669b... -- . ':(exclude)<both files>'` | 0 | no non-document changes |
| subtree `git rev-parse <sha>:<path>` comparison | 0 | all production/test/helper trees SAME |
| `grep -n "nochmals verglichen\|starke 304-Bestätigung\|Erstdownload/304"` on both files | 1 | no matches (prior N-1 reproducer now clean) |
| requirement-ID set `diff` for chapter 08 | 0 | no ID changes |
| requirement-ID set `diff` for chapter 17 | 0 | no ID changes |
| `git diff --check c9fdd30... c92669b...` | 0 | no whitespace errors |
| check named test files exist | 0 | all three present |
| `grep -n "^## 17\." docs/spec/08-...` | 0 | authoritative section 17 exists |

## Findings

**None open.** N-1 is resolved. The only remaining old ETag/304/completion text
is inside explicitly superseded historical sections, which the follow-up prompt
requires preserving.

## Not executed (deliberately out of scope)

Gradle test suites, own product counterprobes, full corpus, benchmarks, browser
or platform matrix, release checks, Python, delegation and credentials were not
used. These are unnecessary for a documentation-only commit whose product,
test and helper trees are provably identical to the prior PASS candidate.

## Conclusion

The follow-up changes only the two documented specification files, corrects the
RES-013 requirement/acceptance, closure and observation wording to the
authoritative chapter 08 §17 contract, and aligns matrix 17 with that section and
the current tests. Product/test/helper content is unchanged from the prior
reviewed candidate, so the prior CACHE-01..06 PASS and 13 Java + 16 launcher
counterprobe results remain applicable.

**Verdict: PASS**
