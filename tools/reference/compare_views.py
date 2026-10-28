#!/usr/bin/env python3
"""Run the shared view cases as real CLI processes against the pinned ili2c.

Writes raw observations only. It neither changes suite pins nor turns differences
into normative successes. Requires an already built CLI distribution.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
from datetime import datetime, timezone


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', required=True, type=Path)
    parser.add_argument('--node', required=True, type=Path)
    parser.add_argument('--ili2c', required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    output = root / 'build/reports/stabilization/view-cases'
    output.mkdir(parents=True, exist_ok=True)
    shared = (root / 'interlis-wasm-teavm/stabilization-contract.cjs').read_text()
    shared = shared[shared.index('  let viewCases = 0;'):shared.index('  return viewCases +')]
    script = ('(async()=>{const cases=[]; const verify=async(text,expected)=>cases.push({text,expected});'
              + shared + ';console.log(JSON.stringify(cases));})();')
    cases = json.loads(subprocess.check_output([str(args.node), '-e', script], text=True))
    env = dict(os.environ, JAVA_HOME=str(args.java_home))
    java = str(args.java_home / 'bin/java')
    cli = str(root / 'interlis-cli/build/install/interlis-cli/bin/interlis-cli')

    def run(item):
        number, case = item
        path = output / f'{number:03}.ili'
        path.write_text(case['text'])
        results = {}
        for label, command in [('current', [cli, 'check', str(path)]),
                               ('ili2c', [java, '-jar', str(args.ili2c), '-o0', str(path)])]:
            process = subprocess.run(command, capture_output=True, text=True, env=env, timeout=30)
            process_error = process.returncode not in (0, 1) or bool(re.search(
                r"java\.[\w.]+(?:Exception|Error)|^\s+at ch\.interlis\.|class ch\.interlis\.[^\n]+ cannot be cast to class|Cannot invoke \"[^\"]+\" because [^\n]+ is null", process.stderr, re.MULTILINE))
            results[label] = dict(command=command, exitCode=process.returncode,
                                  processError=process_error,
                                  stdout=process.stdout, stderr=process.stderr)
        return dict(id=number, expected=case['expected'], source=case['text'],
                    sourceSha256=hashlib.sha256(case['text'].encode()).hexdigest(), **results)

    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(run, enumerate(cases)))
    expected = sum(not c['current']['processError'] and c['current']['exitCode'] == (0 if c['expected'] == 'succeeded' else 1) for c in results)
    agree = sum(not c['current']['processError'] and not c['ili2c']['processError'] and c['current']['exitCode'] == c['ili2c']['exitCode'] for c in results)
    report = dict(recordedAt=datetime.now(timezone.utc).isoformat(), total=len(results),
                  currentExpected=expected, referenceAgreement=agree,
                  currentProcessErrors=sum(c['current']['processError'] for c in results),
                  referenceProcessErrors=sum(c['ili2c']['processError'] for c in results),
                  baseCommit=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
                  ili2cSha256=hashlib.sha256(args.ili2c.read_bytes()).hexdigest(),
                  javaVersion=subprocess.run([java, '-version'], text=True, capture_output=True).stderr,
                  cases=results)
    (output.parent / 'view-reference-comparison.json').write_text(json.dumps(report, indent=2) + '\n')
    print(f'{expected}/{len(results)} expected current results; {agree}/{len(results)} reference agreements')
    return 0 if expected == len(results) else 1


if __name__ == '__main__':
    raise SystemExit(main())
