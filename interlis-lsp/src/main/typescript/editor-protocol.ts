/** Host protocol version 1. UTF-16 positions and opaque identities are resolved only by Java. */
import type {DiagramDocumentDto,DiagramOptionsDto} from '../../../../interlis-projection/src/main/typescript/diagram-document';
export type {DiagramDocumentDto,DiagramOptionsDto} from '../../../../interlis-projection/src/main/typescript/diagram-document';
export interface VersionedDocument {uri:string;version:number;}
export interface DiagramRequest {protocolVersion:1;requestId:string;textDocument:VersionedDocument;symbolIds?:string[];options?:DiagramOptionsDto;}
export interface DiagramResponse {protocolVersion:1;serverInstanceId:string;requestId:string;diagramId:string;rootUri:string;documentVersion:number;workspaceRevision:string;snapshotId:string;documentVersions:Record<string,number>;selectedSymbolIds:string[];document:DiagramDocumentDto;}
export interface AnalysisReady {protocolVersion:1;serverInstanceId:string;workspaceRevision:string;snapshotId:string;documentVersions:Record<string,number>;}
export interface DiagramNavigationParams {protocolVersion:1;serverInstanceId:string;diagramId:string;snapshotId:string;textDocument:VersionedDocument;elementId:string;}
export interface DiagramNavigation {serverInstanceId:string;snapshotId:string;uri:string;range:{start:{line:number;character:number};end:{line:number;character:number}};readonly:boolean;}
export interface DiagramSelectionParams {protocolVersion:1;serverInstanceId:string;diagramId:string;snapshotId:string;textDocument:VersionedDocument;sourceDocument:VersionedDocument;position:{line:number;character:number};}
export interface DiagramSelectionResult {protocolVersion:1;serverInstanceId:string;diagramId:string;snapshotId:string;rootUri:string;documentVersion:number;sourceUri:string;sourceVersion:number;elementIds:string[];symbolIds:string[];}
export type ExportFormat='MARKDOWN'|'HTML'|'DOCX'|'MERMAID'|'PLANTUML'|'GRAPHML';
export interface DocumentationOptions {language?:'DE'|'FR'|'IT'|'EN';inheritance?:'SEPARATE'|'INLINE'|'INLINE_ABSTRACT_ONLY';headers?:Record<string,string>;}
export interface ExportArtifactParams {protocolVersion:1;textDocument:VersionedDocument;format:ExportFormat;modelName?:string;modelVersion?:string;options?:DocumentationOptions;}
export interface ExportArtifact {mediaType:string;encoding:'utf8'|'base64';content:string;fileNameSuggestion:string;sourceSnapshot:{serverInstanceId:string;snapshotId:string;rootUri:string;documentVersion:number;workspaceRevision:string;sourceRevisions:Record<string,string>};limitations:string[];}
export interface EditorAssistParams {protocolVersion:1;textDocument:VersionedDocument;position:{line:number;character:number};options:{tabSize:number;insertSpaces:boolean};}
export interface EditorAssistResult {protocolVersion:1;serverInstanceId:string;snapshotId:string;uri:string;documentVersion:number;edits:{range:{start:{line:number;character:number};end:{line:number;character:number}};newText:string}[];caret:{line:number;character:number};}
