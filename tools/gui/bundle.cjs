// Host assembly only: the renderer remains the shared, unchanged ESM bundle.
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../..');
process.env.PATH=path.dirname(process.execPath)+path.delimiter+process.env.PATH;
require(path.join(root,'frontend/diagram-ui/prepare.cjs'));
const out=path.join(root,'interlis-compiler-gui/src/main/resources/guru/interlis/compiler/gui/web');
fs.rmSync(out,{recursive:true,force:true});
fs.mkdirSync(out,{recursive:true});
fs.cpSync(path.join(root,'frontend/diagram-ui/dist'),path.join(out,'dist'),{recursive:true});
fs.copyFileSync(path.join(root,'frontend/diagram-ui/test/fixtures/interactive.ili'),path.join(out,'Interactive.ili'));
for(const name of ['GuiExample.ili','Broken.ili'])fs.copyFileSync(path.join(__dirname,'examples',name),path.join(out,name));
for(const name of ['index.html','host.js'])fs.copyFileSync(path.join(__dirname,name),path.join(out,name));
