const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process');
const out=__dirname,wt="/Users/stefan/sources/interlis-java/build/review/ili2c-cache-policy/r1/worktree";
const args=['run','--standalone','--auto','--agent','build','--model','deepseek/deepseek-flash','--format','json','--title','Independent ili2c MD5 TTL cache c9fdd30',
'--file',path.join(out,'request.json'),'--file',path.join(out,'REVIEW_PROMPT.md'),
'Independently review the MD5/TTL cache migration using request.json and REVIEW_PROMPT.md. Audit the diff and original evidence, execute your own focused counterprobes, write review.md and verdict.json, then finish with factual PASS or FAIL.'];
const log=fs.openSync(path.join(out,'opencode.jsonl'),'w'),err=fs.openSync(path.join(out,'stderr.log'),'w');const startedAt=new Date().toISOString();
fs.writeFileSync(path.join(out,'launch.json'),JSON.stringify({startedAt,worktree:wt,command:['/Users/stefan/.opencode/bin/opencode',...args]},null,2));
const child=cp.spawn(process.execPath,[path.join(out,'guard.cjs'),'/Users/stefan/.opencode/bin/opencode',...args],{cwd:wt,env:{...process.env,JAVA_HOME:'/Users/stefan/.sdkman/candidates/java/25.0.3-tem',OPENCODE_DISABLE_AUTOUPDATE:'1'},stdio:['ignore',log,err]});
child.on('exit',(exitCode,signal)=>{fs.closeSync(log);fs.closeSync(err);const guard=path.join(wt,'build/reports/python-free/environment.json');if(fs.existsSync(guard))fs.copyFileSync(guard,path.join(out,'guard.json'));fs.writeFileSync(path.join(out,'process-result.json'),JSON.stringify({startedAt,finishedAt:new Date().toISOString(),exitCode,signal},null,2));console.log(JSON.stringify({exitCode,signal}));process.exitCode=exitCode===0?0:1;});
