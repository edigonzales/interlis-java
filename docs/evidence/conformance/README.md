# Pinned compiler conformance suite

This directory records the external INTERLIS compiler conformance run.

| Item | Value |
|---|---|
| Suite | `interlis-compiler-testsuite` |
| Source | `https://codeberg.org/edigonzales/interlis-compiler-testsuite` |
| Commit | `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3` |
| Archive SHA-256 | `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe` |
| Selection | INTERLIS 2.3 and 2.4 only (`^(ili23|ili24)\.`) |
| Excluded | INTERLIS 1.0, with the suite's own language field |
| Runner | pinned `InterlisConformance.java` from the suite archive |
| Compiler | `interlis-cli check` |

Reproduce:

```sh
./gradlew :interlis-cli:installDist
./gradlew conformanceCheck
```

The task is deliberately separate from `check` and `releaseCheck`: the
external suite is large, network-dependent on first use, and its baseline is a
compatibility observation, not a P1 release gate. The full runner report stays
in `build/conformance`; only the compact summary and a log tail are committed
here.

The complete failure list is in `backlog.json`; the human-readable clustering is
in `backlog.md`. `ROLE_CARDINALITY_DEFAULT` is now modeled together with the
association/role validation bundle. Activating the default alone had produced
false accepts; the bundle now rejects missing names, duplicate role names,
additional roles in extended associations, multiple aggregation roles, invalid
composition cardinalities and role namespace conflicts.

`baseline.json` intentionally has no XFAIL entries. Every deviation must be
justified before it is added; a historical ili2c XFAIL is not a baseline for
this compiler.

## Current result (2026-10-18)

- Selected: **593** of 610 suite cases (INTERLIS 2.3/2.4).
- Excluded: **17** INTERLIS 1.0 cases, by language version.
- Result: **592 PASS, 1 FAIL, 0 ERROR, 0 XFAIL, 0 XPASS**.
- Translation: **36/36 Single-Feature-Accept-Fälle PASS**, **0** False Accepts in Translation-Reject-Fällen (`translation-summary.json`).
- Overall: **FAIL**. This is the expected P2 compatibility backlog, not a P1
  release gate. The task intentionally fails while the suite is red; the
  compact result is in `conformance-summary.json`.

The former `ERROR` `ili24.Attribute24Test.text_List` (`LIST {0..*} OF
TEXT*20`) was a real lowering bug: an optional `structureRef()` list was
checked with `!= null` instead of `isEmpty()`. It is fixed and covered by
`CollectionTypeContractTest`; the case is now PASS.
