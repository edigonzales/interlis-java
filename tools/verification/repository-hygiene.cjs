'use strict';

const fs = require('node:fs');
const path = require('node:path');
const {execFileSync} = require('node:child_process');

const retiredDocs = new Set(['spec','g2','p3','evidence','archive','archives',
  'api-migration-0.2.md','implementation-status.md']);
const phaseTestName = /^(?:G2[A-Z]?|P[0-3]|L[0-4][AB]?|[ABEK][1-6]|Phase[1-5])[A-Z][A-Za-z0-9]*Test\.java$/;
const inlineLink = /!?\[[^\]\n]*\]\(\s*(<[^>\n]+>|(?:[^()\s]|\([^()\n]*\))+)/g;
const linkDefinition = /^\s{0,3}\[[^\]]+\]:\s*(<[^>\n]+>|\S+)/;

function tracked(root) {
  return execFileSync('git',['ls-files','--cached','--others','--exclude-standard','-z'],
      {cwd:root,encoding:'utf8',maxBuffer:16*1024*1024}).split('\0').filter(Boolean);
}

function markdownLines(text) {
  const lines = [];
  let fence;
  for(const [index,line] of text.split(/\r?\n/).entries()) {
    const mark = line.match(/^\s{0,3}(`{3,}|~{3,})/);
    if(mark) {
      if(!fence) fence = mark[1];
      else if(mark[1][0]===fence[0]&&mark[1].length>=fence.length) fence=undefined;
    } else if(!fence) lines.push([index+1,line]);
  }
  return lines;
}

function anchors(lines) {
  const result = new Set();
  const counts = new Map();
  for(const [,line] of lines) {
    for(const match of line.matchAll(/<(?:a|span)\b[^>]*\b(?:id|name)=["']([^"']+)["']/g)) result.add(match[1]);
    const heading = line.match(/^\s{0,3}#{1,6}\s+(.+?)\s*#*$/);
    if(!heading)continue;
    const base = heading[1].replace(/<[^>]*>/g,'').toLowerCase().replace(/[^\p{L}\p{N}_ -]/gu,'').replace(/ /g,'-');
    const count = counts.get(base) || 0;
    result.add(count ? base+'-'+count : base);
    counts.set(base,count+1);
  }
  return result;
}

function verify(root, files = tracked(root)) {
  root=fs.realpathSync(root);
  const errors = [];
  const documents = new Map();
  const anchorIndex = new Map();
  const existsInside = file => {
    try {
      const actual=fs.realpathSync(file);
      return actual===root||actual.startsWith(root+path.sep);
    } catch(error) {
      if(error.code==='ENOENT'||error.code==='ENOTDIR')return false;
      throw error;
    }
  };
  const requireInput = (owner,relative) => {
    if(path.isAbsolute(relative)||!existsInside(path.resolve(root,relative))) errors.push(owner+': missing or foreign input '+relative);
  };
  for(const file of new Set(files)) {
    const [directory,component] = file.split('/');
    if(directory==='docs'&&retiredDocs.has(component)) errors.push(file+': retired documentation path');
    if(file.includes('/src/test/java/')&&phaseTestName.test(path.basename(file))) errors.push(file+': delivery phase in test class name');
    if(!existsInside(path.join(root,file))) {
      if(file.endsWith('.md')) errors.push(file+': missing document');
      continue;
    }
    if(file.endsWith('.md')) documents.set(file,markdownLines(fs.readFileSync(path.join(root,file),'utf8')));
    if(path.basename(file)==='test-inputs.properties') {
      const text=fs.readFileSync(path.join(root,file),'utf8');
      for(const row of text.matchAll(/^input\.\d+=(.+)$/gm))requireInput(file,row[1].trim());
    }
  }

  let links=0;
  for(const [file,lines] of documents) for(const [lineNumber,line] of lines) {
    const definition=line.match(linkDefinition);
    const masked=line.replace(/(?<!`)(`+)(?!`).*?\1/g,m=>' '.repeat(m.length));
    const targets=definition?[definition[1]]:[...line.matchAll(inlineLink)]
        .filter(m=>masked[m.index]!== ' ').map(m=>m[1]);
    for(const raw of targets) {
      const target=raw.replace(/^<|>$/g,'');
      if(/^(?:[a-z][a-z\d+.-]*:|\/\/)/i.test(target))continue;
      links++;
      const location=file+':'+lineNumber;
      const [url,fragment] = target.split('#',2);
      let resolved,id;
      try {
        resolved=path.resolve(root,path.dirname(file),decodeURIComponent(url.split('?')[0]||path.basename(file)));
        id=decodeURIComponent(fragment||'');
      } catch {
        errors.push(location+': invalid encoded link '+target);
        continue;
      }
      if(!existsInside(resolved)) {
        errors.push(location+': missing or foreign link '+target);
        continue;
      }
      const local=path.relative(root,resolved).split(path.sep).join('/');
      if(id&&documents.has(local)) {
        if(!anchorIndex.has(local))anchorIndex.set(local,anchors(documents.get(local)));
        if(!anchorIndex.get(local).has(id))errors.push(location+': missing anchor '+target);
      }
    }
  }
  return {documents:documents.size,links,errors};
}

module.exports = {verify};
if(require.main===module) {
  const result=verify(path.resolve(__dirname,'../..'));
  if(result.errors.length) {
    console.error(result.errors.join('\n'));
    process.exitCode=1;
  } else console.log('REPOSITORY_HYGIENE_PASS',result.documents,'documents and',result.links,'local links');
}
