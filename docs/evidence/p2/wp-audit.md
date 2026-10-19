# P2 compiler work-package audit (WP-20..27)

Status: **COMPLETE for the compiler P2 scope** (2026-10-19).

## Scope boundary

This audit covers the compiler work packages WP-20..27 only. P3 integration,
P4 transfer/validation and P5 databases are explicitly out of scope. Product
requirements owned by later phases (full LSP rename/references/quickfix, renderer
adapters, release/profile work) are not claimed here.

INTERLIS 1 is out of scope; the single affected conformance fixture is the
documented XFAIL in `../conformance/baseline.json`.

## Work packages

| WP | Scope | Status | Evidence |
|---|---|---|---|
| WP-20 | complete type families, numeric precision, units, geometry type definitions | **VERIFIED** | `TypeFamilyContractTest`, `TemporalTypeContractTest`, `OidRuleContractTest`, `UnitTypeContractTest`, `GeometryTypeContractTest`, `CoordinateReferenceSystemContractTest`, `FormattedTypeContractTest`, `ClassValueTypeContractTest`; 2.3/2.4 conformance PASS |
| WP-21 | inheritance, structures, associations, roles, effective transfer order | **VERIFIED** | `ConformanceRulesContractTest`, `AssociationRulesContractTest`, `AssociationAttributeContractTest`, `AttributeModifierContractTest`, `DuplicateNameContractTest`; role/inheritance conformance PASS |
| WP-22 | object paths, functions, constraints, type compatibility | **VERIFIED** | `ExpressionConstraintContractTest`, `FunctionContractTest`, `EqualityFamilyContractTest`, `ExternalConstraintContractTest`, `UniqueConstraintContractTest`, `SetConstraintScopeContractTest`, `GeometryConstraintContractTest`; `wp22-summary.json` |
| WP-23 | views and derivations | **VERIFIED** | `ViewContractTest`, `ViewBaseExtensionContractTest`, `ViewDependencyContractTest`, `ViewFormationSemanticsContractTest`; `view-summary.json` (19/19 accept, 0/20 false accepts) |
| WP-24 | translations, metaattributes, metaobjects | **VERIFIED** | `TranslationMappingContractTest`, `TranslationMetadataContractTest`; `translation-summary.json` (36/36 accept, 0 false accepts), `xml-meta-role-summary.json` |
| WP-25 | GENERIC/DEFERRED/CONTEXT | **VERIFIED** | `generic-summary.json` (16/16), `P2HeaderOptionsTest`; no false accepts |
| WP-26 | repository index, HTTP, offline, immutable catalog/lock policy | **VERIFIED** | `RepositoryIndexReaderTest`, `RepositoryClientTest`, `ContentStoreTest`, `RepositoryRefreshServiceTest`; `repository-summary.json` |
| WP-27 | complete norm/feature/test-suite balance | **VERIFIED** | this audit, `LanguageProfileContractTest`, `FragmentParserTest`, pinned 593-case suite: 592 PASS / 1 documented XFAIL / 0 FAIL / 0 ERROR, 0 false accepts |

## Compiler P2 requirement traceability

| Requirement | Status | Evidence |
|---|---|---|
| GOAL-001 version matrix | VERIFIED | `LanguageProfileContractTest`; explicit 2.3/2.4 profile |
| GOAL-003 consumer contracts | VERIFIED | compiler contract tests plus conformance families |
| GOAL-012 separate acceptance/semantics/known differences | VERIFIED | `conformance-summary.json`, `baseline.json`, XFAIL policy |
| ARCH-006 JVM/WASM semantic parity | VERIFIED | `ReferenceFixtureParityTest`, `checkPortable` |
| SYN-004 version-dependent keywords | VERIFIED | `LanguageProfileContractTest` |
| SYN-010 fragment analysis | VERIFIED | `FragmentParserTest` |
| SYN-013 explicit unsupported syntax | VERIFIED | `SyntaxContractTest`, syntax coverage |
| META-005 error/missing/generic types distinct | VERIFIED | `TypeFamilyContractTest`, generic conformance |
| META-006 metamodel catalog tests | VERIFIED | declaration/metamodel contract tests |
| META-007 translation preserves identity | VERIFIED | `TranslationMappingContractTest` |
| META-008 legacy inheritance/override/role goldens | VERIFIED | pinned conformance translation/inheritance/rôle cases |
| META-013 views without SQL exporter | VERIFIED | view conformance, `ViewContractTest` |
| META-014 exhaustive variant/coverage check | VERIFIED | conformance suite plus contract matrix |
| META-016 invalid assignment reason | VERIFIED | `TypeFamilyContractTest`, `ConformanceRulesContractTest` |
| META-017 fingerprint semantic changes | VERIFIED | `FingerprintContractTest` |
| SEM-006 valid compile without runtime implementation | VERIFIED | accepted conformance cases, compiled-schema tests |
| SEM-008/009 scope/cycle separation | VERIFIED | `ConformanceRulesContractTest`, import/closure tests |
| SEM-010 complete positive/negative matrix | VERIFIED | this audit, 592/592 in-scope conformance |
| SEM-017 documented deviations | VERIFIED | `scope-decision-interlis1.md`, XFAIL entry |
| RES-009 loader limits/cancellation/typed errors | VERIFIED | `FileSourceLoaderTest`, `HttpSourceLoaderTest` |
| RES-011 index fixtures/reference profiles | VERIFIED | `RepositoryIndexReaderTest` with 09/20 profile fixtures |
| TEST-004 type/binding/order/constraint goldens | VERIFIED | contract tests and conformance families |
| TEST-009 controlled limits | VERIFIED | budget/cancellation tests |
| TEST-013 frozen resolved closure | VERIFIED | `CompiledSchemaSelectionTest`, workspace capture tests |
| COMPAT-003 documented deviation decisions | VERIFIED | INTERLIS1 scope decision, XFAIL baseline |

## Residual notes

- The pinned suite intentionally contains the INTERLIS 1 transfer case; it is
  XFAIL, not PASS, and is excluded from the compiler scope.
- P2 does not claim P3 replacement/export functionality, P4 transfer/validation
  or P5 database mappings.
