// Executed inside the real VS Code extension host, using the unchanged consumer.
const assert=require('node:assert/strict');const vscode=require('vscode');const fs=require('node:fs');
exports.run=async()=>{
 const extension=vscode.extensions.getExtension('edigonzales.interlis-editor');assert.ok(extension);await extension.activate();assert.equal(extension.isActive,true);
 const uri=vscode.Uri.file(process.env.INTERLIS_HOST_FIXTURE);const doc=await vscode.workspace.openTextDocument(uri);await vscode.window.showTextDocument(doc);
 const wait=async(fn,message)=>{const until=Date.now()+20000;while(Date.now()<until){const value=await fn();if(value)return value;await new Promise(resolve=>setTimeout(resolve,100));}throw Error(message);};
 const definition=()=>vscode.commands.executeCommand('vscode.executeDefinitionProvider',uri,new vscode.Position(7,17));
 const defs=await wait(async()=>{const values=await definition();return values?.length?values:null;},'No definition from Jenesis server');assert.equal((defs[0].range||defs[0].targetRange).start.line,4);
 const original=doc.getText();const replace=async text=>{const edit=new vscode.WorkspaceEdit();edit.replace(uri,new vscode.Range(doc.positionAt(0),doc.positionAt(doc.getText().length)),text);assert.equal(await vscode.workspace.applyEdit(edit),true);};
 await replace(original.replace('height : Height;','height : UnknownDomain;'));
 const diagnostics=await wait(()=>{const values=vscode.languages.getDiagnostics(uri).filter(d=>d.severity===vscode.DiagnosticSeverity.Error);return values.length?values:null;},'Missing invalid-model diagnostics');
 assert.equal(doc.isDirty,true);const dirty=doc.getText();await vscode.commands.executeCommand('interlisGuru.stopServer');
 await wait(()=>vscode.languages.getDiagnostics(uri).length===0,'Diagnostics not cleared after stop');
 await vscode.commands.executeCommand('interlisGuru.restartServer');
 const renewed=await wait(()=>{const values=vscode.languages.getDiagnostics(uri).filter(d=>d.severity===vscode.DiagnosticSeverity.Error);return values.length?values:null;},'Missing renewed diagnostics after restart');assert.equal(doc.getText(),dirty);assert.equal(doc.isDirty,true);
 await replace(original);await wait(()=>vscode.languages.getDiagnostics(uri).length===0,'Valid model diagnostics not cleared');
 await wait(async()=>{const values=await definition();return values?.length;},'Definition missing after restart');
 await vscode.commands.executeCommand('interlisGuru.stopServer');
 const record={status:'passed',host:vscode.version,extensionVersion:extension.packageJSON.version,extensionPath:extension.extensionPath,activation:true,definitionLine:4,diagnostics:diagnostics.map(d=>({code:d.code,message:d.message,range:d.range})),renewed:renewed.map(d=>({code:d.code,message:d.message})),dirtyBufferPreserved:true,stopRestart:true};
 fs.writeFileSync(process.env.INTERLIS_EDITOR_HOST_RECEIPT,JSON.stringify(record,null,2));console.log('JENESIS_EXTENSION_HOST_PASS '+JSON.stringify(record));
};
