#!/usr/bin/env python3
"""Generate NFD/CCC=0 count weights; not a Unicode normalizer.

Input versions and hashes are explicit. Canonical reordering does not affect
counts, so no normalization engine is required in the portable compiler.
"""
import argparse,functools,hashlib,json
from pathlib import Path

PINS={'UnicodeData':'2e1efc1dcb59c575eedf5ccae60f95229f706ee6d031835247d843c11d96470c',
      'NormalizationTest':'5019ffd530751a741900c849c0e010332f142a3612234639bd200b82138a87db'}

def generate(data_path,test_path,target,check):
    raw={};ccc={}
    for label,p in [('UnicodeData',data_path),('NormalizationTest',test_path)]:
        digest=hashlib.sha256(p.read_bytes()).hexdigest()
        if digest!=PINS[label]:raise ValueError(f'{label}: changed input pin {digest}')
    for line in data_path.read_text().splitlines():
        f=line.split(';');cp=int(f[0],16);ccc[cp]=int(f[3])
        if f[5] and not f[5].startswith('<'):raw[cp]=[int(c,16) for c in f[5].split()]
    @functools.lru_cache(None)
    def weight(cp):
        if 0xAC00<=cp<=0xD7A3:return 2 if (cp-0xAC00)%28==0 else 3
        if cp in raw:return sum(map(weight,raw[cp]))
        return 0 if ccc.get(cp,0) else 1
    ranges=[]
    for cp in sorted(ccc):
        w=weight(cp)
        if w==1 or 0xAC00<=cp<=0xD7A3:continue
        if ranges and ranges[-1][1]+1==cp and ranges[-1][2]==w:ranges[-1][1]=cp
        else:ranges.append([cp,cp,w])
    checked=0
    for line in test_path.read_text().splitlines():
        line=line.split('#',1)[0].strip()
        if not line or line.startswith('@'):continue
        columns=[[int(c,16) for c in col.split()] for col in line.split(';')[:5]]
        # NormalizationTest's explicit NFD is independent of our decomposition walk.
        for source,nfd in [(0,2),(1,2),(2,2),(3,4),(4,4)]:
            assert sum(map(weight,columns[source]))==sum(ccc.get(cp,0)==0 for cp in columns[nfd]),line
            checked+=1
    rows='\n'.join('        '+', '.join(f'0x{a:X}, 0x{b:X}, {w}' for a,b,w in ranges[i:i+4])+',' for i in range(0,len(ranges),4))
    java='''package guru.interlis.compiler.internal;

/** INTERLIS 2.4 text length: CCC=0 code points after canonical decomposition.
 * Generated from pinned Unicode 17.0.0; see tools/reference/generate_text_weights.py.
 * Copyright Unicode, Inc.; Unicode License v3 is included under docs/evidence/
 * stabilization/registries/UNICODE-LICENSE.txt. Ordering decomposed marks does not
 * change the count. Source strings and UTF-16 positions are never normalized.
 */
final class TextLengths {
    private TextLengths() { }
    static int canonicalLength(String text) {
        java.util.Objects.requireNonNull(text, "text");
        int result=0;
        for(int i=0;i<text.length();) {
            int cp=text.codePointAt(i);i+=Character.charCount(cp);
            if(cp>=0xAC00 && cp<=0xD7A3) { result+=(cp-0xAC00)%28==0?2:3;continue; }
            int low=0,high=WEIGHTS.length/3-1,weight=1;
            while(low<=high) {
                int mid=(low+high)>>>1,offset=mid*3;
                if(cp<WEIGHTS[offset]) high=mid-1;
                else if(cp>WEIGHTS[offset+1]) low=mid+1;
                else {weight=WEIGHTS[offset+2];break;}
            }
            result+=weight;
        }
        return result;
    }
    private static final int[] WEIGHTS={
'''+rows+'''
    };
}
'''
    if check:assert target.read_text()==java,'Generated weights are stale'
    else:target.write_text(java)
    print(json.dumps(dict(unicode='17.0.0',pins=PINS,intervals=len(ranges),normalizationChecks=checked,generatedSha256=hashlib.sha256(java.encode()).hexdigest())))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('unicode_data',type=Path);p.add_argument('normalization_tests',type=Path);p.add_argument('--check',action='store_true');p.add_argument('--target',type=Path,default=Path('interlis-compiler/src/main/java/guru/interlis/compiler/internal/TextLengths.java'));a=p.parse_args();generate(a.unicode_data,a.normalization_tests,a.target,a.check)
