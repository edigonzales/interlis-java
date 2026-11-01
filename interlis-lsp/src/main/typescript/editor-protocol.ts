/** Host protocol version 1. UTF-16 positions and opaque identities are resolved only by Java. */
import type {DiagramDocumentDto,DiagramOptionsDto} from '../../../../frontend/diagram-ui/src/types';
export type {DiagramDocumentDto,DiagramOptionsDto} from '../../../../frontend/diagram-ui/src/types';
export interface VersionedDocument {uri:string;version:number;}
export interface DiagramRequest {protocolVersion:1;requestId:string;textDocument:VersionedDocument;symbolIds?:string[];options?:DiagramOptionsDto;}
export interface DiagramResponse {protocolVersion:1;serverInstanceId:string;requestId:string;diagramId:string;rootUri:string;documentVersion:number;workspaceRevision:string;snapshotId:string;documentVersions:Record<string,number>;selectedSymbolIds:string[];document:DiagramDocumentDto;}
export interface AnalysisReady {protocolVersion:1;serverInstanceId:string;workspaceRevision:string;snapshotId:string;documentVersions:Record<string,number>;}
export interface DiagramNavigationParams {protocolVersion:1;serverInstanceId:string;diagramId:string;snapshotId:string;textDocument:VersionedDocument;elementId:string;}
export interface DiagramNavigation {serverInstanceId:string;snapshotId:string;uri:string;range:{start:{line:number;character:number};end:{line:number;character:number}};readonly:boolean;}
