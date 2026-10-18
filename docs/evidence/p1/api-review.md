# P1 API review baseline (core contracts)

Date: 2026-10-18. This is a **core API review**, not the exhaustive
method-by-method API-003 sign-off. The measured Javadoc warning baseline is in
`javadoc-summary.json`; this document records the reviewed contracts.

## Package ownership

| Package | Responsibility | Host dependency |
|---|---|---|
| `guru.interlis.source` | source identity, revisions, UTF-16 ranges, line maps | none |
| `guru.interlis.model` | immutable semantic graph, stable IDs, compiled-schema view | none |
| `guru.interlis.compiler` | request, analysis, gate, compilation outcomes | no ANTLR/LSP/HTTP/JDBC/UI in public API |
| `guru.interlis.workspace` | revisioned in-memory updates and reuse | no file/network access |
| `guru.interlis.projection` | pure renderer-independent diagram projection | none |
| `guru.interlis.diagnostics` | immutable diagnostics, catalog and presentation | none |
| `guru.interlis.execution` | run identity, cancellation, observation | no global current run |
| `guru.interlis.syntax` | recoverable own syntax tree | no public ANTLR types |
| `guru.interlis.model.query` | snapshot-bound read-only queries | none |
| `guru.interlis.workspace.resolution` | explicit resolver SPI and prepared closures | no implicit fetch |
| `guru.interlis.repository` | explicit file/HTTP loaders | isolated host IO |
| `guru.interlis.cli` | typed in-process CLI adapter | no `System.exit` outside entry |
| `guru.interlis.lsp` | LSP4J adapter with protocol-only stdout | no second language core |
| `guru.interlis.bridge` | transport-neutral JSON protocol | no global sessions |

`ArchitectureBoundaryTest` scans public model/compiler classes and rejects
`org.antlr.*`, `org.eclipse.lsp4j.*`, `java.net.http.*`, `java.sql.*`,
`javax.swing.*`, `java.awt.*`, internal packages and CLI/LSP types in public
signatures.

## Contract decisions reviewed

- **Nullness:** optional values use `Optional`; public records do not use null as
  a sentinel. Constructor arguments are checked with `Objects.requireNonNull`.
- **Immutability:** model/snapshot/projection values are immutable records.
  `SourceSet`, `CompilationRequest`, `Diagnostic`, `DiagramDocument` and related
  containers copy caller lists/maps on construction. `SnapshotOwnershipTest`
  mutates caller collections after construction and verifies that compilation is
  unchanged.
- **Revisions and ranges:** source ranges are UTF-16 half-open ranges bound to a
  `SourceId` and `SourceRevision`. Cross-snapshot symbol/type references are
  rejected at the query/projection boundary; `CrossSnapshotReferenceTest`
  verifies the diagram rejection.
- **Ordering:** effective member order follows the INTERLIS inheritance order;
  diagnostics are canonically sorted by source/range/code/arguments; diagram
  output is deterministic. Diagnostic order is tested independently of emission
  order. Source-map insertion order is tested to leave snapshot identity and
  model selection unchanged (`CanonicalSourceOrderTest`).
- **Cancellation:** every long-running compiler/workspace entry takes a
  `CancellationToken`; cancellation is a distinct outcome, not a language
  rejection. LSP `$/cancelRequest` is forwarded through the compile command.
- **Outcomes:** `CompilationOutcome` is closed over `Succeeded`, `Rejected`,
  `Cancelled` and `Failed`; only `Succeeded` carries a `CompiledSchema`.
- **SDK:** `PublicSdkConstructionTest` builds a request from strings and public
  types only; `PublicSdkConstructionTest` is compiled with the normal test
  classpath, not a test-only facade.

## Accepted residual

On 2026-10-18 the user explicitly accepted the remaining Javadoc warning
baseline (258 warnings) as a documented P1 residual. It is not a release
blocker for the P1 scope; P2 documentation work may improve it later.

## Known gaps

- 258 Javadoc warnings remain. Parameter tags and comments were added for source
  and core model/compiler/workspace/projection records, getters, compact
  constructors and public enum constants; most remaining warnings are missing
  comments on public methods and sealed-interface members. Package-level
  contracts were added for `source`, `model`, `compiler`, `workspace` and
  `projection`.
- The API register's broader contract-test names are not all present under their
  exact historical names; equivalent coverage is distributed across the test
  classes listed in `test-summary.md`.
- No binary/source compatibility baseline exists yet; the project is still
  `0.1.0-SNAPSHOT`.
