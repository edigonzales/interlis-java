export type DiagnosticSeverity = "error" | "warning" | "information" | "hint";
export interface Position { line: number; character: number; byteOffset: number; }
export interface SourceRange { uri: string; start: Position; end: Position; }
export type DiagnosticPhase =
  | "unknown"
  | "lexical"
  | "syntax"
  | "editor-recovery"
  | "model-discovery"
  | "resolution"
  | "semantic"
  | "translation"
  | "repository"
  | "formatting"
  | "request"
  | "internal";
export type DiagnosticTag =
  | "primary"
  | "cascaded"
  | "recovery"
  | "deprecated"
  | "unnecessary";
export interface Diagnostic {
  severity: DiagnosticSeverity;
  code: string;
  message: string;
  range: SourceRange | null;
  relatedInformation: Array<{ range: SourceRange | null; message: string }>;
  notes: string[];
  treatedAsError: boolean;
  source?: "compiler" | "live" | "lint";
  phase?: DiagnosticPhase;
  tags?: Array<DiagnosticTag>;
  helpId?: string;
  fingerprint?: string;
}
export interface LogEvent {
  level: "trace" | "debug" | "information" | "warning" | "error";
  category: string;
  message: string;
  context: Record<string, string>;
}
export interface CompilationRequest {
  roots: string[];
  options?: {
    autoSearch?: boolean;
    warningsAsErrors?: boolean;
    modelDirectories?: string[];
  };
  externalMetaAttributes?: Array<{ element: string; name: string; value: string }>;
}
export interface CompilationResult {
  schemaVersion: 1;
  abiVersion: 1;
  compilerVersion: string;
  kind: "compilation";
  success: boolean;
  cancelled: boolean;
  errorCount: number;
  warningCount: number;
  missingModels: string[];
  models: Array<{
    name: string;
    iliVersion: string;
    uri: string;
    metaAttributes: Array<{ name: string; value: string }>;
  }>;
  diagnostics: Diagnostic[];
  logs: LogEvent[];
  /** CLI-compatible human-readable compiler transcript, including the final status line. */
  transcript?: string[];
}
export interface SyntaxToken { kind: string; text: string; channel: number; range: SourceRange; }
export interface SyntaxNode { id: number; parent: number | null; kind: string; range: SourceRange; }
export interface SyntaxContext { kind: string; range: SourceRange; }
export interface SyntaxImportReference {
  model: string; unqualified: boolean; range: SourceRange;
}
export interface SyntaxSnapshot {
  schemaVersion: 1;
  abiVersion: 1;
  compilerVersion: string;
  kind: "syntax";
  success: boolean;
  uri: string;
  documentVersion: number;
  iliVersion: "1.0" | "2.3" | "2.4" | "unknown";
  tokens: SyntaxToken[];
  nodes: SyntaxNode[];
  contexts: SyntaxContext[];
  imports: string[];
  importReferences?: SyntaxImportReference[];
  diagnostics: Diagnostic[];
}
export type EditorSymbolKind =
  | "model" | "topic" | "class" | "structure" | "association"
  | "view" | "graphic" | "domain" | "unit" | "attribute";
export interface EditorDeclaration {
  id: string;
  name: string;
  qualifiedName: string;
  kind: EditorSymbolKind;
  containerId: string | null;
  range: SourceRange;
  selectionRange: SourceRange;
  endRange: SourceRange | null;
}
export interface EditorReference {
  text: string;
  kind: "extends" | "type" | "collection" | "reference" | "unit";
  sourceId: string | null;
  range: SourceRange;
}
export interface EditorSnapshot {
  schemaVersion: 1;
  abiVersion: 1;
  compilerVersion: string;
  kind: "editor";
  success: boolean;
  /** Current runtime results always include this; optional for legacy fixtures. */
  recovered?: boolean;
  /** Current runtime results always include this; optional for legacy fixtures. */
  complete?: boolean;
  uri: string;
  documentVersion: number;
  iliVersion: "1.0" | "2.3" | "2.4" | "unknown";
  declarations: EditorDeclaration[];
  references: EditorReference[];
  imports: SyntaxImportReference[];
  contexts: SyntaxContext[];
  diagnostics: Diagnostic[];
}
export interface CompilerCapabilities {
  nativeEditorSnapshot: boolean;
  incrementalSession?: boolean;
  incrementalStats?: boolean;
  incrementalTrace?: boolean;
  incrementalCacheSnapshot?: boolean;
  strictEditorSeparation?: boolean;
}
export interface IncrementalStats {
  sourceAdds: number;
  sourceRemoves: number;
  sourceNoOps: number;
  versionOnlyUpdates: number;
  contentChanges: number;
  sourceReintroductions: number;
  rejectedUpdates: number;
  parseRequests: number;
  editorSnapshotRequests: number;
  strictParserBuilds: number;
  tolerantParserBuilds: number;
  strictParserHits: number;
  tolerantParserHits: number;
  parserEntries: number;
  parserRetainedBytes: number;
  parserBuilds: number;
  parserHits: number;
  parserEvictions: number;
  parserBytes: number;
  syntaxMaterializations: number;
  editorMaterializations: number;
  rootAnalysisHits: number;
  rootAnalysisMisses: number;
  rootAnalysisBuilds: number;
  rootAnalysisEvictions: number;
  rootEntries: number;
  rootRetainedBytes: number;
  invalidatedRootEntries: number;
  reusedClosureSources: number;
  reparsedClosureSources: number;
  compilationInvocations: number;
  cancelledPlans: number;
  compileRequests: number;
  compileExecutions: number;
}
export interface SemanticSymbol {
  id: string; name: string; qualifiedName: string; kind: string;
  containerId: string; range: SourceRange | null; abstract: boolean;
  selectionRange: SourceRange | null;
  endRange: SourceRange | null;
}
export interface SemanticReference {
  sourceId: string; targetId: string; kind: string; range: SourceRange | null;
}
export interface SemanticDependency {
  sourceUri: string; targetUri: string; model: string; range?: SourceRange | null;
}
export interface DiagramMember {
  name: string; type: string; cardinality?: string; declaringType?: string;
  inherited: boolean; inlineEnumValues?: string[];
}
export interface DiagramNode {
  id: string; containerId: string; label: string; kind: string; abstract: boolean;
  range: SourceRange | null; stereotypes?: string[];
  members: DiagramMember[]; enumValues: string[]; operations?: string[];
}
export interface DiagramEdge {
  id: string; sourceId: string; targetId: string; kind: string;
  label: string; cardinality: string;
  sourceCardinality?: string; targetCardinality?: string;
}
export interface DocumentationSection {
  id: string; title: string; kind: string; text: string; level: number;
}
export interface DocumentationRow {
  name: string; cardinality: string; type: string; description?: string; range?: string;
}
export interface DocumentationRole {
  name: string; cardinality: string; type: string; description?: string;
}
export interface DocumentationUnique {
  scope: "global" | "local"; perBasket: boolean; prefix?: string;
  elements: string[]; where?: string; origin: "direct" | "inherited";
  inheritedFrom?: string;
}
export interface DocumentationViewable {
  name: string; kind: "class" | "structure" | "view" | "association";
  isAbstract: boolean; documentation?: string; rows: DocumentationRow[];
  roles?: DocumentationRole[]; uniqueness?: DocumentationUnique[];
}
export interface DocumentationEnumerationEntry {
  value: string; documentation?: string; displayName?: string;
}
export interface DocumentationEnumeration {
  name: string; documentation?: string; entries: DocumentationEnumerationEntry[];
}
export interface DocumentationTopic {
  name: string; documentation?: string;
  viewables: DocumentationViewable[];
  enumerations: DocumentationEnumeration[];
}
export interface DocumentationModel {
  name: string; uri: string; title?: string; shortDescription?: string;
  topics: DocumentationTopic[];
  viewables: DocumentationViewable[];
  enumerations: DocumentationEnumeration[];
}
export interface SemanticSnapshot {
  schemaVersion: 1;
  abiVersion: 1;
  compilerVersion: string;
  kind: "semantic";
  success: boolean;
  cancelled: boolean;
  roots: string[];
  documentVersions: Record<string, number>;
  missingModels?: string[];
  symbols: SemanticSymbol[];
  references: SemanticReference[];
  dependencies: SemanticDependency[];
  diagram: { nodes: DiagramNode[]; edges: DiagramEdge[] };
  documentation: {
    title: string;
    sections: DocumentationSection[];
    models?: DocumentationModel[];
  };
  diagnostics: Diagnostic[];
  logs: LogEvent[];
}
export interface CompilationAnalysisResult {
  schemaVersion: 1;
  abiVersion: 1;
  compilerVersion: string;
  kind: "compilation-analysis";
  compilation: CompilationResult;
  semantic: SemanticSnapshot;
  syntax: SyntaxSnapshot[];
}
export interface FormatResult {
  schemaVersion: 1;
  abiVersion: 1;
  compilerVersion: string;
  kind: "formatting";
  success: boolean;
  applicable: boolean;
  changed: boolean;
  text: string;
  diagnostics: Diagnostic[];
}
export interface ResolvedWorkspace {
  models: Array<{ uri: string; source: string }>;
}
export interface EmscriptenIlicModule {
  HEAPU8: Uint8Array;
  HEAPU32: Uint32Array;
  _ilic_abi_version(): number;
  _ilic_version(): number;
  _ilic_alloc(length: number): number;
  _ilic_free(pointer: number): void;
  _ilic_session_create(): number;
  _ilic_session_destroy(session: number): void;
  _ilic_session_put_source(session: number, uri: number, uriLength: number,
    source: number, sourceLength: number, version: bigint): number;
  _ilic_session_remove_source(session: number, uri: number, uriLength: number): number;
  _ilic_incremental_stats?(session: number): number;
  _ilic_incremental_trace?(session: number): number;
  _ilic_incremental_cache_snapshot?(session: number): number;
  _ilic_reset_incremental_stats?(session: number): number;
  _ilic_clear_incremental_caches?(session: number): number;
  _ilic_compile(session: number, request: number, requestLength: number): number;
  _ilic_parse(session: number, request: number, requestLength: number): number;
  _ilic_editor_snapshot?(session: number, request: number, requestLength: number): number;
  _ilic_analyze(session: number, request: number, requestLength: number): number;
  _ilic_compile_and_analyze(session: number, request: number, requestLength: number): number;
  _ilic_format(session: number, request: number, requestLength: number): number;
  _ilic_result_json(result: number, resultLength: number): number;
  _ilic_result_destroy(result: number): void;
}
export class CompilerSession {
  constructor(module: EmscriptenIlicModule, options?: { allowLegacyEditorProjection?: boolean });
  putSource(uri: string, source: string | Uint8Array, version?: number): void;
  putWorkspace(workspace: ResolvedWorkspace): void;
  removeSource(uri: string): boolean;
  incrementalStats?(): IncrementalStats;
  incrementalTrace?(): Record<string, unknown>;
  incrementalCacheSnapshot?(): Record<string, unknown>;
  resetIncrementalStats?(): void;
  clearIncrementalCaches?(): void;
  compile(request: CompilationRequest): CompilationResult;
  parse(uri: string): SyntaxSnapshot;
  editorSnapshot(uri: string): EditorSnapshot;
  analyze(request: CompilationRequest): SemanticSnapshot;
  compileAndAnalyze(request: CompilationRequest): CompilationAnalysisResult;
  format(uri: string, options?: { indentSize?: number; requireValidSyntax?: boolean }): FormatResult;
  dispose(): void;
}
export class Compiler {
  constructor(module: EmscriptenIlicModule, options?: { allowLegacyEditorProjection?: boolean });
  readonly module: EmscriptenIlicModule;
  readonly abiVersion: number;
  readonly compilerVersion: string;
  readonly capabilities: CompilerCapabilities;
  createSession(): CompilerSession;
}
export function createCompiler(options?: {
  moduleFactory?: (options?: Record<string, unknown>) => Promise<EmscriptenIlicModule> | EmscriptenIlicModule;
  moduleOptions?: Record<string, unknown>;
  compatibility?: { allowLegacyEditorProjection?: boolean };
}): Promise<Compiler>;
