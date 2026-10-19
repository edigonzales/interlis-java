# P3-1 CLI option inventory

Status: implemented for the P3-1 slice; no model generators.

## Commands

| Command | Purpose |
|---|---|
| `version` | tool version and language profile |
| `capabilities` | machine-readable feature profile |
| `check` | compile/validate model files |
| `compile` | alias for `check` |
| `analyze` | analysis diagnostics |
| `diagram` | checked-schema diagram output |
| `format` | format INTERLIS source |

## Options

| Option | Commands | Behaviour |
|---|---|---|
| `--json` | `check`, `compile`, `analyze` | machine-readable diagnostics |
| `--root NAME` | `check`, `compile`, `analyze`, `diagram` | explicit model root |
| `--format mermaid\|plantuml\|json` | `diagram` only | diagram output format |
| `--check` | `format` | report changed files, no write |
| `--write` | `format` | atomic in-place write |
| `--indent N` | `format` | indentation width 0..16 |
| `--line-ending lf\|crlf` | `format` | output line ending |

## Rejected options

- Unknown options are rejected as usage errors.
- `--export` and other generator/IlisMeta options are rejected; P3-1 has no
  model-generator API.
- `--format` outside `diagram` is rejected.
- `--json` for `diagram` is rejected; use `--format json`.
- `--check` together with `--write` is rejected.

## Exit codes

| Code | Meaning |
|---:|---|
| 0 | success / formatting check clean |
| 1 | source rejected, or `--check` found changes |
| 2 | usage error, unknown/unsupported option, missing file |
| 3 | internal failure |
| 4 | cancelled |

The exact legacy ili2c numeric profile is outside P3-1; this inventory is the
explicit current CLI contract, not a claim of full ili2c option compatibility.
