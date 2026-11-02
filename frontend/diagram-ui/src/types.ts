/** Protocol 1.0 data. Revision and exact numerical payloads remain decimal strings. */
export type Revision = string;
export type JsonValue = null | boolean | number | string | JsonValue[] | {[key: string]: JsonValue};
export type JsonObject = {[key: string]: JsonValue};
export interface SourceRangeDto { readonly source: string; readonly revision: Revision; readonly startOffset: number; readonly endOffset: number; }
export interface SymbolRefDto { readonly snapshot: string; readonly symbol: string; }
export interface SourceAnchorDto { readonly snapshotId: string; readonly nameRange: SourceRangeDto; readonly declarationRange: SourceRangeDto | null; }
export interface DiagramOptionsDto {
  readonly schemaVersion:'1.0';
  readonly scope?:'SELECTED'|'WITH_DEPENDENCIES'|'NEIGHBOURHOOD';
  readonly visibleKinds?:readonly ('CLASS'|'STRUCTURE'|'ASSOCIATION'|'VIEW'|'DOMAIN'|'EXTERNAL_REFERENCE'|'GRAPHIC'|'DRAWING_RULE'|'LINE_FORM'|'METADATA_BASKET'|'METAOBJECT')[];
  readonly inheritedMembers?:'HIDE'|'SHOW_WITH_ORIGIN';
  readonly showTypes?:boolean;readonly showRoleCardinalities?:boolean;readonly showConstraints?:boolean;readonly showImports?:boolean;
  readonly translationModel?:SymbolRefDto;readonly neighbourhoodDepth?:number;
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
