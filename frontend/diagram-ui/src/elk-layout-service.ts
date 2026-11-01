import ELKApi,{type ELK as ElkPort} from 'elkjs/lib/elk-api.js';
import {layoutDiagram,type LayoutDiagram,type DiagramSettings} from './layout';
import type {RenderSnapshot} from './render-types';
export interface LayoutServicePort {
 layout(projection:RenderSnapshot['diagram'],settings:DiagramSettings,signal?:AbortSignal):Promise<LayoutDiagram>;
 cancel():void;
 close():void;
}
/** One native ELK worker and one pending layout per canvas; replacement cancels obsolete computation. */
export class ElkLayoutService implements LayoutServicePort {
 private elk?:ElkPort;
 private reject?: (error:Error)=>void;
 private cleanup?:()=>void;
 private closed=false;
 constructor(private readonly url:URL|string) { }
 layout(projection:RenderSnapshot['diagram'],settings:DiagramSettings,signal?:AbortSignal):Promise<LayoutDiagram> {
  if(this.closed)return Promise.reject(new Error('Diagram is closed'));
  this.cancel();
  return new Promise((resolve,reject)=>{
   if(signal?.aborted){reject(new DOMException('Layout cancelled','AbortError'));return;}
   const elk=new ELKApi({workerUrl:String(this.url)});this.elk=elk;this.reject=reject;
   const abort=()=>{if(this.elk===elk)this.cancel();};
   signal?.addEventListener('abort',abort,{once:true});
   const finish=()=>{clearTimeout(deadline);signal?.removeEventListener('abort',abort);elk.terminateWorker();if(this.elk===elk){this.elk=undefined;this.reject=undefined;this.cleanup=undefined;}};
   const deadline=setTimeout(()=>{finish();reject(new Error('Diagram layout exceeded 20 seconds'));},20000);
   this.cleanup=finish;
   layoutDiagram(projection,settings,elk).then(layout=>{if(this.elk!==elk)return;finish();resolve(layout);},error=>{if(this.elk!==elk)return;finish();reject(error);});
  });
 }
 cancel():void {const reject=this.reject;this.cleanup?.();this.cleanup=undefined;this.elk=undefined;this.reject=undefined;reject?.(new DOMException('Layout superseded','AbortError'));}
 close():void {this.closed=true;this.cancel();}
}
