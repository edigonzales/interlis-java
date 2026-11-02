/** Own presentation-only layout input. No compiler AST, language service or runtime. */
export interface Position {line:number;character:number;byteOffset:number;}
export interface SourceRange {uri:string;start:Position;end:Position;}
export interface DiagramMember {name:string;type:string;cardinality?:string;declaringType?:string;inherited:boolean;inlineEnumValues?:string[];id?:string;}
export interface DiagramNode {id:string;containerId:string;label:string;kind:string;abstract:boolean;range:SourceRange|null;stereotypes?:string[];members:DiagramMember[];enumValues:string[];operations?:string[];layoutSize?:{width:number;height:number};headerHeight?:number;}
export interface DiagramEdge {id:string;sourceId:string;targetId:string;kind:string;label:string;cardinality?:string;sourceCardinality?:string;targetCardinality?:string;}
export interface RenderSnapshot {success:boolean;diagram:{nodes:DiagramNode[];edges:DiagramEdge[]};}
