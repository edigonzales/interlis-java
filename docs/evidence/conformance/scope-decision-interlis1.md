# Scope decision: INTERLIS 1

Date: 2026-10-19

## Decision

INTERLIS 1 support is **out of scope** for interlis-java. The compiler and its
acceptance suite target INTERLIS 2.3 and 2.4 only.

## Consequence

The pinned historical suite still contains one fixture that starts with
`TRANSFER INTERLIS1;` and uses `TABLE` syntax:

- `ili23.Attribute10Test.attributeComplete`

It is retained as evidence and is **not** an acceptance criterion for the
2.3/2.4 compiler. `conformanceCheck` therefore reports:

- **592 PASS / 1 XFAIL / 0 FAIL / 0 ERROR / 0 false accepts**
- in-scope result: **592 of 592 PASS**
- the XFAIL is registered in `baseline.json` with `actualOutcome: rejected`

No suite case is removed; the out-of-scope status is documented here and in
`backlog.json`/`backlog.md`. An unexpected acceptance becomes XPASS and fails
the run, so the exception cannot silently become a pass.
