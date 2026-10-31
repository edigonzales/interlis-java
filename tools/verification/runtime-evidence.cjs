'use strict';
const fs=require('node:fs'),path=require('node:path');const {root,hash,fingerprint,commitInfo}=require('../performance/profile.cjs');
/** Evidence belongs to the actual loaded artifacts and an unchanged candidate. */
function begin(files){
 const artifacts={};for(const file of files){const absolute=path.resolve(root,file);if(!absolute.startsWith(root+path.sep)||!fs.statSync(absolute).isFile())throw Error('missing/unsafe runtime artifact');artifacts[file]=hash(fs.readFileSync(absolute));}
 return {...commitInfo(),sourceFingerprint:fingerprint(),artifacts,node:process.version};
}
function finish(evidence){
 const current=commitInfo();if(evidence.gitCommit!==current.gitCommit||evidence.dirty!==current.dirty||evidence.sourceFingerprint!==fingerprint())throw Error('candidate changed during runtime verification');
 for(const [file,sha256]of Object.entries(evidence.artifacts))if(hash(fs.readFileSync(path.resolve(root,file)))!==sha256)throw Error('runtime artifact changed during verification');
 return evidence;
}
module.exports={begin,finish};
