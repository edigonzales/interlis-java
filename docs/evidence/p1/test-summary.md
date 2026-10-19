# P1 verification summary

Date: 2026-10-18. These are new Gradle/P1 results, separate from the historical
Maven spike under `docs/evidence/wasm-spike`.

## JVM

Command: `./gradlew check`

| Test class | Tests | Failures |
|---|---:|---:|
| `guru.interlis.source.SourceTextTest` | 4 | 0 |
| `guru.interlis.diagnostics.DiagnosticPresentationTest` | 2 | 0 |
| `guru.interlis.diagnostics.DiagnosticCatalogTest` | 3 | 0 |
| `guru.interlis.model.DecimalLiteralTest` | 3 | 0 |
| `guru.interlis.compiler.SyntaxContractTest` | 4 | 0 |
| `guru.interlis.compiler.FragmentParserTest` | 4 | 0 |
| `guru.interlis.compiler.LanguageProfileContractTest` | 3 | 0 |
| `guru.interlis.compiler.P2HeaderOptionsTest` | 1 | 0 |
| `guru.interlis.compiler.FormatterContractTest` | 3 | 0 |
| `guru.interlis.compiler.CompilerEngineTest` | 5 | 0 |
| `guru.interlis.compiler.ArchitectureBoundaryTest` | 1 | 0 |
| `guru.interlis.compiler.SnapshotOwnershipTest` | 1 | 0 |
| `guru.interlis.compiler.CanonicalSourceOrderTest` | 1 | 0 |
| `guru.interlis.compiler.CompiledSchemaSelectionTest` | 1 | 0 |
| `guru.interlis.compiler.PublicSdkConstructionTest` | 1 | 0 |
| `guru.interlis.compiler.ApiInvariantTest` | 2 | 0 |
| `guru.interlis.compiler.ModelSelectorContractTest` | 2 | 0 |
| `guru.interlis.compiler.OutcomeContractTest` | 1 | 0 |
| `guru.interlis.compiler.FingerprintContractTest` | 1 | 0 |
| `guru.interlis.compiler.CollectionTypeContractTest` | 1 | 0 |
| `guru.interlis.compiler.ModifierContractTest` | 1 | 0 |
| `guru.interlis.compiler.AssociationRulesContractTest` | 2 | 0 |
| `guru.interlis.compiler.ConformanceRulesContractTest` | 6 | 0 |
| `guru.interlis.compiler.FunctionContractTest` | 1 | 0 |
| `guru.interlis.compiler.CoordinateReferenceSystemContractTest` | 1 | 0 |
| `guru.interlis.compiler.AssociationAttributeContractTest` | 1 | 0 |
| `guru.interlis.compiler.GeometryTypeContractTest` | 1 | 0 |
| `guru.interlis.compiler.ViewContractTest` | 2 | 0 |
| `guru.interlis.compiler.ExternalConstraintContractTest` | 1 | 0 |
| `guru.interlis.compiler.UniqueConstraintContractTest` | 1 | 0 |
| `guru.interlis.compiler.ViewBaseExtensionContractTest` | 1 | 0 |
| `guru.interlis.compiler.TranslationMetadataContractTest` | 1 | 0 |
| `guru.interlis.compiler.TranslationMappingContractTest` | 3 | 0 |
| `guru.interlis.compiler.ViewFormationSemanticsContractTest` | 3 | 0 |
| `guru.interlis.compiler.ExpressionConstraintContractTest` | 2 | 0 |
| `guru.interlis.compiler.DomainEnumerationContractTest` | 1 | 0 |
| `guru.interlis.compiler.DuplicateNameContractTest` | 3 | 0 |
| `guru.interlis.compiler.GeometryConstraintContractTest` | 2 | 0 |
| `guru.interlis.compiler.EnumerationConstantContractTest` | 1 | 0 |
| `guru.interlis.compiler.EqualityFamilyContractTest` | 4 | 0 |
| `guru.interlis.compiler.TemporalTypeContractTest` | 1 | 0 |
| `guru.interlis.compiler.TypeFamilyContractTest` | 1 | 0 |
| `guru.interlis.compiler.FormattedTypeContractTest` | 1 | 0 |
| `guru.interlis.compiler.UnitTypeContractTest` | 1 | 0 |
| `guru.interlis.compiler.OidRuleContractTest` | 2 | 0 |
| `guru.interlis.compiler.AttributeModifierContractTest` | 1 | 0 |
| `guru.interlis.compiler.EngineContractTest` | 4 | 0 |
| `guru.interlis.compiler.ImportBindingContractTest` | 1 | 0 |
| `guru.interlis.compiler.ImportDiagnosticContractTest` | 1 | 0 |
| `guru.interlis.compiler.QueryOwnershipTest` | 2 | 0 |
| `guru.interlis.compiler.RunEventContractTest` | 2 | 0 |
| `guru.interlis.compiler.ReferenceFixtureParityTest` | 2 | 0 |
| `guru.interlis.compiler.ReferenceRuleTest` | 3 | 0 |
| `guru.interlis.compiler.VersionScopeTest` | 2 | 0 |
| `guru.interlis.compiler.internal.DiagnosticOrderTest` | 1 | 0 |
| `guru.interlis.compiler.internal.antlr.VersionScannerTest` | 1 | 0 |
| `guru.interlis.repository.FileSourceLoaderTest` | 3 | 0 |
| `guru.interlis.repository.HttpSourceLoaderTest` | 3 | 0 |
| `guru.interlis.repository.RepositoryIndexReaderTest` | 4 | 0 |
| `guru.interlis.repository.ContentStoreTest` | 3 | 0 |
| `guru.interlis.repository.RepositoryClientTest` | 2 | 0 |
| `guru.interlis.repository.RepositoryRefreshServiceTest` | 2 | 0 |
| `guru.interlis.workspace.WorkspaceTest` | 5 | 0 |
| `guru.interlis.workspace.WorkspaceIsolationTest` | 1 | 0 |
| `guru.interlis.projection.DiagramProjectionTest` | 4 | 0 |
| `guru.interlis.projection.CrossSnapshotReferenceTest` | 1 | 0 |
| `guru.interlis.wasm.bridge.BridgeDispatcherTest` | 3 | 0 |
| `guru.interlis.lsp.LspServerTest` | 4 | 0 |
| `guru.interlis.lsp.LspFeatureTest` | 8 | 0 |
| `guru.interlis.lsp.LspProcessTest` | 1 | 0 |
| `guru.interlis.lsp.LspProtocolCancellationTest` | 1 | 0 |
| `guru.interlis.lsp.LspErrorContractTest` | 1 | 0 |
| `guru.interlis.cli.CliApplicationTest` | 8 | 0 |
| **Total** | **167** | **0** |

The Gradle test reports are generated under `<module>/build/test-results/test/`.

## Public SDK example

Command: `./gradlew :interlis-compiler:sdkExampleTest`

Result: **SDK PASS**, `models=1`. The example under
`examples/sdk/SdkExample.java` is compiled against the built
`interlis-compiler`/`interlis-model` jars and executed with the runtime
classpath; `check` depends on this task.
The selected P1 fixtures additionally run against the pinned ili2c 5.6.9
reference; see `../reference/reference-results.json`.

The LSP process test uses real stdio framing, opens a document, invokes
`workspace/executeCommand` with `interlis.diagram`, checks the returned
`snapshotId` and rejects a stale snapshot, runs `interlis.compile`, then
performs shutdown/exit. The in-process LSP test covers diagnostics, hover,
definition, symbols, completion, sequential text changes and compile-request
cancellation: a canceled `interlis.compile` future is observed by the compiler
cancellation token, and the session still answers a diagram request afterwards.
LSP-011 is additionally covered end-to-end at the JSON-RPC layer:
`LspProtocolCancellationTest` sends `workspace/executeCommand` `interlis.compile`
with a deliberately slow workspace, sends the standard `$/cancelRequest`, and
asserts a `-32800`/`RequestCancelled` response, an observed compiler cancellation
token, a successful diagram request afterwards, and a valid shutdown response.

`LspErrorContractTest` separates protocol, language and internal failures over a
real JSON-RPC endpoint: an unknown method produces `-32601`, an invalid INTERLIS
model produces a successful `rejected` compile result, and an injected workspace
failure produces `-32603`.

## Pinned ili2c reference profile

Command: `python3 tools/reference/reference_check.py`

Result: **5/5 PASS** against ili2c 5.6.9 (ZIP SHA-256 verified). The small
profile covers the accepted 2.4 import/inheritance set, a 2.3 model, a broken
model, trailing content and a missing import. See
`../reference/reference-results.json` and `../reference/README.md`.

## External conformance suite

Command: `./gradlew conformanceCheck`

The pinned 610-case suite is integrated for INTERLIS 2.3/2.4 only: **593
selected, 17 INTERLIS 1.0 excluded**. Current result: **592 PASS, 1 XFAIL, 0
FAIL, 0 ERROR, 0 XPASS** (`conformance-summary.json`). The single XFAIL is
`Attribute10Test.attributeComplete` and needs `TRANSFER INTERLIS1;`/`TABLE`
syntax. **INTERLIS 1 is out of scope** (decision 2026-10-19,
`../conformance/scope-decision-interlis1.md`); in-scope are **592 of 592**
cases PASS. The XFAIL is explicitly registered in
`baseline.json`; an unexpected pass would become XPASS and fail the task. The former `text_List` internal
error is fixed and covered by `CollectionTypeContractTest`. The failure
clustering and first-family decision are in `../conformance/backlog.md`.

## Portable Node profiles

Command: `./gradlew checkPortable`

- TeaVM WasmGC under Node 22.23.1 with `--experimental-wasm-exnref`: PASS
  (`wasm-contract-result.json`), including exact decimal
  `9007199254740993.0010` and a diagram projection.
- TeaVM JavaScript under Node 22.23.1: PASS (`js-contract-result.json`).
- `checkPortable` completed successfully after running both manifests and all JVM `check` tasks.

## Browser matrix

Command: `./gradlew :interlis-wasm-teavm:browserContractTest`

| Browser | Result |
|---|---|
| Chromium (Playwright revision 1243) | PASS |
| Firefox (Playwright revision 1543) | PASS |
| WebKit (Playwright revision 2359) | PASS |

Each run: capabilities, session open, source update, analysis, rejected
intermediate state, successful compile, diagram, exact-decimal check, worker
termination/restart and recompilation. See `browser-results.json`.

Real Safari: `./gradlew :interlis-wasm-teavm:safariContractTest` now passes with
Safari 27.0.1. The WebDriver contract starts Safari in automation mode, loads
the harness, and checks capabilities, source update, analysis, rejection,
compile, diagram, exact decimal value and worker restart. Result:
`safari-result.json` with status `passed`. G1 is closed. Playwright WebKit
remains a separate browser result and is not presented as Safari evidence.
The task also verifies that the full `checkBrowser` matrix is green.

## GraalVM Web Image

Command:
`./gradlew :interlis-wasm-webimage:webImageProbe -PgraalVmHome=$HOME/.sdkman/candidates/java/25.4.4+1-graal -PbinaryenHome=/tmp/binaryen`

Result: PASS under Node 22.23.1 with Oracle GraalVM 25.4.4.1.1+1.1 (`webimage-result.json`). The same protocol bridge analyzed and compiled a model. The 25.4.4 artifact is larger than the 25.0.3 baseline; see `webimage-comparison.json`.

Browser command:
`./gradlew :interlis-wasm-webimage:webImageBrowserTest -PgraalVmHome=… -PbinaryenHome=…`

Result: **PASS** in Chromium, Firefox and Playwright-WebKit
(`webimage-browser-results.json`). The TeaVM Safari contract passes separately;
a Web Image Safari run was not performed.

## CLI distribution

Commands:
`./gradlew :interlis-cli:installDist`
`interlis-cli/build/install/interlis-cli/bin/interlis-cli version`
`interlis-cli/build/install/interlis-cli/bin/interlis-cli check testdata/models/base24.ili testdata/models/survey24.ili`
`interlis-cli/build/install/interlis-cli/bin/interlis-cli diagram --root Survey --format mermaid testdata/models/base24.ili testdata/models/survey24.ili`

Result: version output present; positive check exit 0; Mermaid class diagram
produced. Negative fixture check exit 1 in `CliApplicationTest`.
