# Pinned ili2c reference evidence (small P1 profile)

Date: 2026-10-18. This is a deliberately small, pinned reference profile for the
P1 fixture set. It is **not** the historical 610-case suite and does not claim
full INTERLIS 2.3/2.4 conformance.

| Item | Value |
|---|---|
| Tool | ili2c 5.6.9 (`5.6.9-bf16026e83f2377153b2e818a4f3838f0a423722`) |
| Source | `https://downloads.interlis.ch/ili2c/ili2c-5.6.9.zip` |
| SHA-256 | `0d6fc5dc739a490ef6b027072d58281bb0731c932f03d95662f83d20dc07c8ee` |
| Java | Temurin 25.0.2+10 |
| Runner | `tools/reference/reference_check.py` |

Reproduce:

```sh
python3 tools/reference/reference_check.py
```

The runner verifies the zip hash, extracts `ili2c.jar` into a cache, and runs
the explicit cases. It writes `reference-results.json` with commands, exit
codes, fixture SHA-256 values and output excerpts. The report uses only
**PASS/FAIL** for this profile; no XFAIL/XPASS/ERROR entries are used.

## Cases

| Case | Fixtures | Expected | Actual | Status |
|---|---|---|---|---|
| `base-survey-24` | `base24.ili`, `survey24.ili` | exit 0 | 0 | PASS |
| `legacy-23` | `legacy23.ili` | exit 0 | 0 | PASS |
| `broken-24` | `broken24.ili` | exit 1 | 1 | PASS |
| `trailing-24` | `trailing24.ili` | exit 1 | 1 | PASS |
| `missing-import` | `survey24.ili` with `--no-auto` | exit 1 | 1 | PASS |

The positive fixture implements the reference rules encountered during this
run: an attribute override requires `(EXTENDED)`, a cross-topic role requires
`(EXTERNAL)`, and the topic requires `DEPENDS ON Base.Common;`. The new
compiler enforces the override and external-role rules and supports topic
dependencies so that the reference-valid fixture is also accepted by P1.

`ReferenceFixtureParityTest` runs the same positive/negative profile through the
new engine. The reference output remains evidence about the reference tool, not
a claim that the new compiler implements all of ili2c.
