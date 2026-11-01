import fs from 'node:fs';
import crypto from 'node:crypto';
import path from 'node:path';
const root=process.cwd();
const manifest=JSON.parse(fs.readFileSync('docs/implementation/repository-cache/fixes/evidence/product-manifest.json','utf8'));
function sha(p){return crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');}
let bad=[],missing=[],ok=0;
for(const [p,h] of Object.entries(manifest.files_sha256)){ if(!fs.existsSync(p)){missing.push(p);continue;} const a=sha(p); if(a!==h)bad.push({p,expected:h,actual:a}); else ok++; }
let tbad=[],tmissing=[],tok=0;
for(const [p,h] of Object.entries(manifest.test_sources_sha256)){ if(!fs.existsSync(p)){tmissing.push(p);continue;} const a=sha(p); if(a!==h)tbad.push({p,expected:h,actual:a}); else tok++; }
const dist='build/reviewer/distribution/lib';
let jbad=[],jmissing=[],jok=0;
for(const [n,h] of Object.entries(manifest.final_runtime_jars_sha256)){ const p=path.join(dist,n); if(!fs.existsSync(p)){jmissing.push(p);continue;} const a=sha(p); if(a!==h)jbad.push({p,expected:h,actual:a}); else jok++; }
const extra=fs.readdirSync(dist).filter(n=>!(n in manifest.final_runtime_jars_sha256));
console.log(JSON.stringify({files:{ok,bad,missing},tests:{ok:tok,bad:tbad,missing:tmissing},jars:{ok:jok,bad:jbad,missing:jmissing,extra}},null,2));
