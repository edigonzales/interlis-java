/** Protocol 1.0 data. Revision and exact numerical payloads remain decimal strings. */
export type Revision = string;
export type JsonValue = null | boolean | number | string | JsonValue[] | {[key: string]: JsonValue};
export type JsonObject = {[key: string]: JsonValue};
export interface SourceRangeDto { readonly source: string; readonly revision: Revision; readonly startOffset: number; readonly endOffset: number; }
export interface SymbolRefDto { readonly snapshot: string; readonly symbol: string; }
export interface SourceAnchorDto { readonly snapshotId: string; readonly nameRange: SourceRangeDto; readonly declarationRange: SourceRangeDto | null; }
export interface SessionLimits {
  readonly maxSourceUtf16Length: number; readonly maxTotalSourceUtf16Length: string;
  readonly maxSources: number; readonly maxRetainedSnapshots: number;
  readonly maxMessageUtf16Length: number; readonly maxConcurrentSessions: number;
}
export interface AnalysisBudgetDto {
  readonly maxSourceUtf16Length?: number; readonly maxTotalSourceUtf16Length?: string;
  readonly maxTokens?: string; readonly maxSyntaxNodes?: string; readonly maxRecoveryEvents?: number;
  readonly maxParseRuleDepth?: number; readonly maxSemanticWorkUnits?: string; readonly maxDiagnostics?: number;
  readonly maxNumericLiteralDigits?: number; readonly maxAbsoluteDecimalExponent?: number;
}
export interface ModelSelectionDto { readonly modelName: string; readonly modelVersion?: string; readonly sourceHint?: string; }
export interface ResolutionPolicyDto {
  readonly preferWorkspaceSource?: boolean; readonly modelSelections?: readonly ModelSelectionDto[];
  readonly sourcePriorities?: readonly {readonly sourceId: string; readonly priority: number}[];
}
export interface CacheLimitsDto {
  readonly maxSyntaxEntries?: number; readonly maxAnalysisEntries?: number;
  readonly maxQueryEntries?: number; readonly maxRetainedSourceUtf16Units?: string;
}
export type OverlayTargetDto = string | {readonly name: string; readonly modelVersion?: string; readonly sourceHint?: string};
export type OverlayDto = {readonly id: string; readonly priority: number; readonly target: OverlayTargetDto; readonly range?: SourceRangeDto} & (
  {readonly kind: 'translation'; readonly original: OverlayTargetDto} |
  {readonly kind: 'crs'; readonly crs: string} |
  {readonly kind: 'meta-attribute'; readonly attributes: Readonly<Record<string, readonly string[]>>});
export interface WorkspaceConfigurationDto {
  readonly semanticProfile?: 'interlis-2.3-2.4/v1'; readonly allowedLanguages?: readonly ('2.3' | '2.4')[];
  readonly warningPolicy?: 'ALLOW_WARNINGS' | 'REJECT_WARNINGS'; readonly analysisBudget?: AnalysisBudgetDto;
  readonly overlays?: readonly OverlayDto[]; readonly resolutionPolicy?: ResolutionPolicyDto;
  readonly resolverCatalog?: Readonly<Record<string, string>>; readonly cacheLimits?: CacheLimitsDto;
}
export interface SessionOptions {
  readonly configuration?: WorkspaceConfigurationDto; readonly limits?: Partial<SessionLimits>;
  readonly sources?: readonly {readonly sourceId: string; readonly text: string}[];
}
export interface SessionHandle { readonly id: string; readonly epoch: number; }
export interface SessionState {
  readonly workspaceRevision: Revision; readonly sourceRevisions: Readonly<Record<string, Revision>>;
  readonly limits: SessionLimits; readonly configurationFingerprint: string;
}
export interface SourceUpdateDto {
  readonly sourceId: string;
  readonly expectation: {readonly kind: 'absent'} | {readonly kind: 'at'; readonly revision: Revision};
  readonly change: {readonly kind: 'replace'; readonly text: string} |
    {readonly kind: 'edits'; readonly edits: readonly {readonly startOffset: number; readonly endOffset: number; readonly text: string}[]};
}
export interface BatchSourceUpdateDto { readonly expectedWorkspaceRevision: Revision; readonly updates: readonly SourceUpdateDto[]; }
export interface RemoveSourceDto { readonly sourceId: string; readonly expectedRevision: Revision; }
export interface SetConfigurationDto { readonly expectedWorkspaceRevision: Revision; readonly configuration: WorkspaceConfigurationDto; }
export interface UpdateReceiptDto {
  readonly sourceId: string; readonly previousSourceRevision: Revision | null; readonly sourceRevision: Revision | null;
  readonly previousWorkspaceRevision: Revision; readonly workspaceRevision: Revision;
  readonly changed: boolean; readonly reasons: readonly string[];
}
export interface BatchUpdateReceiptDto {
  readonly previousWorkspaceRevision: Revision; readonly workspaceRevision: Revision; readonly updates: readonly UpdateReceiptDto[];
}
export interface ConfigurationReceiptDto {
  readonly previousWorkspaceRevision: Revision; readonly workspaceRevision: Revision; readonly changed: boolean; readonly configurationFingerprint: string;
}
export type RootSelectorDto = ({readonly kind: 'byName'} & ModelSelectionDto) | {readonly kind: 'bySource'; readonly source: string};
export interface AnalysisRequestDto { readonly expectedWorkspaceRevision: Revision; readonly roots: readonly RootSelectorDto[]; }
export type CompileRequestDto = AnalysisRequestDto;
export interface DiagnosticDto {
  readonly id: string; readonly code: string; readonly severity: string; readonly phase: string; readonly messageKey: string;
  readonly range?: SourceRangeDto; readonly arguments: readonly {readonly name: string; readonly kind: string; readonly value: string}[];
  readonly causeId?: string; readonly symbolId?: string;
}
export interface AnalysisResultDto {
  readonly snapshotId: string; readonly workspaceRevision: Revision; readonly analysisState: 'complete' | 'partial';
  readonly sources: readonly {readonly sourceId: string; readonly revision: Revision}[];
  readonly diagnostics: readonly DiagnosticDto[];
  readonly coverage: {readonly requestedRootsChecked: boolean; readonly incomplete: readonly string[]};
  readonly missingModels: readonly {readonly modelName: string; readonly modelVersion: string | null; readonly reason: string}[];
}
export type CompilationResultDto =
  {readonly outcome: 'succeeded'; readonly snapshotId: string; readonly schemaId: string; readonly diagnostics: readonly DiagnosticDto[]} |
  {readonly outcome: 'rejected'; readonly snapshotId: string; readonly diagnostics: readonly DiagnosticDto[]; readonly rejection: {
    readonly reasons: readonly string[]; readonly diagnosticIds: readonly string[]; readonly requestedRoots: readonly string[]; readonly unsupportedFeatureIds: readonly string[]}} |
  {readonly outcome: 'cancelled'; readonly cancellation: {readonly reason: string}} |
  {readonly outcome: 'failed'; readonly failureId: string; readonly diagnostic: DiagnosticDto};
export interface PositionQueryDto {
  readonly kind: 'symbolAt' | 'definition' | 'semanticProperties' | 'hover' | 'completion';
  readonly sourceId: string; readonly revision: Revision; readonly startOffset: number; readonly endOffset: number;
  readonly prefix?: string; readonly limit?: number;
}
export interface QueryRequestDto { readonly snapshotId: string; readonly query: PositionQueryDto; }
export type OriginDto =
  {readonly kind:'overlay'; readonly overlayId:string; readonly priority:number; readonly range?:SourceRangeDto} |
  {readonly kind:'declared'; readonly range:SourceRangeDto} |
  {readonly kind:'derived'; readonly declaredAt:string; readonly derivation:string} |
  {readonly kind:'predefined'; readonly key:string} |
  {readonly kind:'synthetic'; readonly reason:string};
export interface EffectiveMemberDto {
  readonly symbolId:string; readonly qualifiedName:string; readonly name:string; readonly symbolKind:string;
  readonly position:number; readonly origin:string; readonly inheritancePath:readonly string[]; readonly targets:readonly string[];
}
export type QueryResultDto = {readonly snapshotId:string} & (
  {readonly kind:'known'; readonly symbolId:string} |
  {readonly kind:'unresolved'; readonly reason?:string; readonly candidates?:readonly string[]} |
  {readonly kind:'invalid'; readonly diagnosticIds:readonly string[]} |
  {readonly kind:'notAnalyzed'; readonly reason:string} |
  {readonly kind:'definition'; readonly symbolId:string; readonly nameRange:SourceRangeDto; readonly declarationRange:SourceRangeDto} |
  {readonly kind:'hover'; readonly signature:string; readonly documentation:string; readonly range:SourceRangeDto} |
  {readonly kind:'completion'; readonly candidates:readonly {readonly label:string; readonly insertText:string; readonly kind:string; readonly detail:string}[]; readonly replacementRange:SourceRangeDto; readonly incomplete:boolean} |
  {readonly kind:'semanticProperties'; readonly symbolId:string; readonly qualifiedName:string; readonly symbolKind:string;
   readonly metadata:readonly {readonly key:string; readonly text:string; readonly origin:OriginDto}[]; readonly effectiveMembers:readonly EffectiveMemberDto[];
   readonly translation?:{readonly sourceModel:string; readonly sourceTarget?:string; readonly origin?:OriginDto; readonly correspondences:readonly {readonly original:string; readonly translated:string}[]}}
);
export interface DiagramOptionsDto {
  readonly schemaVersion:'1.0';
  readonly scope?:'SELECTED'|'WITH_DEPENDENCIES'|'NEIGHBOURHOOD';
  readonly visibleKinds?:readonly ('CLASS'|'STRUCTURE'|'ASSOCIATION'|'VIEW'|'DOMAIN'|'EXTERNAL_REFERENCE'|'GRAPHIC'|'DRAWING_RULE'|'LINE_FORM'|'METADATA_BASKET'|'METAOBJECT')[];
  readonly inheritedMembers?:'HIDE'|'SHOW_WITH_ORIGIN';
  readonly showTypes?:boolean;readonly showRoleCardinalities?:boolean;readonly showConstraints?:boolean;readonly showImports?:boolean;
  readonly labelPolicy?:'CANONICAL'|'TRANSLATED';readonly associationPresentation?:'ENTITY'|'COMPACT_BINARY';
  readonly budget?:{readonly maxNodes?:number;readonly maxEdges?:number;readonly maxMembersPerNode?:number};
}
export type DiagramRequestDto = ({readonly snapshotId:string; readonly schemaId?:never} | {readonly schemaId:string; readonly snapshotId?:never}) & {readonly selection?:readonly SymbolRefDto[];readonly options?:DiagramOptionsDto};
export interface DiagramMemberDto {
  readonly id: string; readonly kind: string; readonly label: string; readonly typeLabel: string | null; readonly cardinality: string | null;
  readonly declaredSymbol: string; readonly effectiveOwner: string; readonly inherited: boolean; readonly overridden: boolean; readonly anchor: SourceAnchorDto | null;
}
export interface DiagramCompartmentDto { readonly id: string; readonly kind: string; readonly members: readonly DiagramMemberDto[]; }
export interface DiagramNodeDto {
  readonly id: string; readonly kind: string; readonly containerId: string; readonly label: string; readonly canonicalName: string;
  readonly status: string; readonly symbol: SymbolRefDto | null; readonly compartments: readonly DiagramCompartmentDto[]; readonly anchor: SourceAnchorDto | null;
  /** Absent only in older 1.0 producers; current producer always supplies the declaration flags. */
  readonly modifiers?: readonly string[];
}
export interface DiagramContainerDto {
  readonly id: string; readonly kind: string; readonly parentId: string | null; readonly label: string; readonly canonicalName: string;
  readonly symbol: SymbolRefDto | null; readonly anchor: SourceAnchorDto | null;
}
export interface DiagramEndpointDto { readonly elementId: string; readonly memberId: string | null; }
export interface DiagramEdgeEndDto {
  readonly roleName: string | null; readonly cardinality: {readonly minimum: string; readonly maximum: {readonly kind: 'bounded'; readonly value: string} | {readonly kind: 'unbounded'}} | null;
  readonly aggregation: string;
}
export interface DiagramEdgeDto {
  readonly id: string; readonly kind: string; readonly source: DiagramEndpointDto; readonly target: DiagramEndpointDto;
  readonly origin: SymbolRefDto | null; readonly sourceEnd: DiagramEdgeEndDto; readonly targetEnd: DiagramEdgeEndDto;
  readonly label: string; readonly anchor: SourceAnchorDto | null;
}
export interface ProjectionNoticeDto {
  readonly code: string; readonly severity: string; readonly messageKey: string; readonly arguments: readonly string[];
  readonly symbol: SymbolRefDto | null; readonly range: SourceRangeDto | null;
}
export interface DiagramDocumentDto {
  readonly schemaVersion: '1.0'; readonly snapshotId: string; readonly sourceRevisions: Readonly<Record<string, Revision>>;
  readonly inputKind: 'ANALYSIS' | 'CHECKED_SCHEMA'; readonly completeness: 'COMPLETE' | 'PARTIAL' | 'LIMITED';
  readonly selection: readonly SymbolRefDto[]; readonly optionsFingerprint: string;
  readonly containers: readonly DiagramContainerDto[]; readonly nodes: readonly DiagramNodeDto[];
  readonly edges: readonly DiagramEdgeDto[]; readonly notices: readonly ProjectionNoticeDto[];
}
