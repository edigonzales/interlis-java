# P2 work-package audit (WP-20..27)

Status: **in progress**. This audit is the working record for the P2 completion
goal; it is not a declaration that P2 is complete.

| WP | Scope | Current evidence | Remaining |
|---|---|---|---|
| WP-20 | type families, numeric precision, units, geometry types | 2.3/2.4 conformance PASS; type-family contract tests | formal requirement-by-requirement audit |
| WP-21 | inheritance, structures, associations, roles, effective transfer order | conformance PASS for roles/inheritance; contract tests | formal audit of every P2 inheritance rule |
| WP-22 | object paths, functions, constraints | `wp22-summary.json` | none known; keep under audit |
| WP-23 | views and derivations | `view-summary.json` (19/19 accept, 0/20 false accepts) | none known; keep under audit |
| WP-24 | translations and metaattributes | `translation-summary.json` (36/36 accept, 0 false accepts), XML/metaobject summary | none known; keep under audit |
| WP-25 | GENERIC/DEFERRED/CONTEXT | `generic-summary.json` (16/16) | none known; keep under audit |
| WP-26 | repository index, HTTP, offline/lock profiles | repository file/HTTP loaders, offline/limit/cancel tests, standard `ilimodels.xml` index reader with safe relative resolution | `RepositoryClient`, refresh/catalog and lock-profile contracts |
| WP-27 | norm/feature/test-suite balance | pinned conformance suite and this audit | complete requirement-to-implementation matrix without silent gaps |

## Version profile matrix

The explicit profile is implemented in
`interlis-compiler/src/main/java/guru/interlis/compiler/internal/antlr/LanguageProfile.java`.
Violations are reported as `ILI-SYN-0006` with the concrete construct.

| Construct | 2.3 | 2.4 | Enforcement |
|---|---|---|---|
| `MULTICOORD` | rejected | accepted | token-profile check |
| `GENERIC` domain | rejected | accepted | token-profile check |
| `DEFERRED GENERICS` | rejected | accepted | token-profile check |
| `CONTEXT` definition | rejected | accepted | token-profile check |
| inline enumeration collection | rejected | accepted | syntax-builder profile check |

Views and functions remain version-neutral as exercised by the 2.3 suite.

## Evidence to keep current

- `docs/evidence/conformance/conformance-summary.json`
- `docs/evidence/conformance/*-summary.json`
- `docs/evidence/p1/test-summary.md`
- `docs/implementation-status.md`
