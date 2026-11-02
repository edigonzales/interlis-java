import {displayText} from './geometry';
import type {DiagramNodeDto,DiagramContainerDto,DiagramEdgeEndDto} from './types';
export interface PresentationLine {id:string;text:string;y:number;kind:string;}
/** Text is data throughout; wrapping never interprets an INTERLIS name. */
export function wrap(text:string,width=68):string[] {
 const lines:string[]=[];for(const line of displayText(text).split(/\r?\n/)){const characters=Array.from(line);if(!characters.length)lines.push('');while(characters.length)lines.push(characters.splice(0,width).join(''));}return lines;
}
export function presentation(node:DiagramNodeDto|DiagramContainerDto,settings?:{attributeMode:string;showLocalEnumerationValues:boolean}):{lines:PresentationLine[];width:number;height:number;titleLines:string[]} {
 const lines:PresentationLine[]=[],titleLines=wrap(node.label);let y=titleLines.length*18+12+('modifiers' in node&&node.modifiers?.includes('ABSTRACT')?20:0)+('compartments' in node&&node.kind!=='CLASS'?20:0);
 if('compartments' in node)for(const compartment of node.compartments){
  if((settings?.attributeMode==='NONE'&&['ATTRIBUTES','ROLES'].includes(compartment.kind))||(compartment.kind==='ENUM_VALUES'&&settings?.showLocalEnumerationValues===false))continue;
  if(!compartment.members.length)continue;
  y+=12;
  for(const member of compartment.members){if(member.inherited&&settings?.attributeMode==='OWN')continue;const text=member.label+(member.overridden?' (EXTENDED)':'')+(member.typeLabel?' : '+member.typeLabel:'')+(member.cardinality?' '+member.cardinality:'')+(member.inherited?' [inherited]':'');
   for(const textLine of wrap(text)){lines.push({id:member.id,text:textLine,y,kind:compartment.kind});y+=18;}
  }
 }
 const all=[...titleLines,...lines.map(l=>l.text)],canvas=typeof document==='undefined'?undefined:document.createElement('canvas').getContext('2d');if(canvas)canvas.font='12px monospace';
 const width=Math.max(220,Math.ceil(Math.max(...all.map(t=>canvas?canvas.measureText(t).width:Array.from(t).length*7.3)))+28);
 return {lines,titleLines,width,height:Math.max(64,y+14)};
}
export function endLabel(end:DiagramEdgeEndDto):string {
 const c=end.cardinality,max=c?.maximum.kind==='unbounded'?'*':c?.maximum.value;
 return [end.roleName,c?(c.minimum===max?max:c.minimum+'..'+max):null].filter(Boolean).join(' ');
}
