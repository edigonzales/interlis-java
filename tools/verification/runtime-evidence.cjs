'use strict';
const fs=require('node:fs'),path=require('node:path');const {createHash}=require('node:crypto');
const root=fs.realpathSync(path.resolve(__dirname,'../..'));
const hash=value=>createHash('sha256').update(value).digest('hex');
/** Check that the loaded artifacts stay unchanged during a runtime test. */
function begin(files){
 const artifacts={};for(const file of files){const absolute=path.resolve(root,file);if(!absolute.startsWith(root+path.sep)||!fs.statSync(absolute).isFile())throw Error('missing/unsafe runtime artifact');artifacts[file]=hash(fs.readFileSync(absolute));}
 return {artifacts,node:process.version};
}
function finish(evidence){
 for(const [file,sha256]of Object.entries(evidence.artifacts))if(hash(fs.readFileSync(path.resolve(root,file)))!==sha256)throw Error('runtime artifact changed during verification');
 return evidence;
}
module.exports={begin,finish,hash};
