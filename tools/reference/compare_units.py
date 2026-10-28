#!/usr/bin/env python3
"""Compare K6's shared cases and pinned originals through real compiler processes.

Requires the built CLI distribution. Writes observations under build/, never
updates suite pins, expectations or normative exceptions.
"""
import argparse
import concurrent.futures
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--node', type=Path, required=True)
    parser.add_argument('--ili2c', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    output = root / 'build/reports/stabilization/k6-units'
    output.mkdir(parents=True, exist_ok=True)
    shared = (root / 'interlis-wasm-teavm/stabilization-contract.cjs').read_text()
    block = shared[shared.index('  let numericUnitCases = 0;'):shared.index('  return numericUnitCases +')]
    script = ('(async()=>{const cases=[];'
              'const verify=async(text,expected)=>cases.push({sources:[{sourceId:"memory:/case.ili",text}],expected});'
              'const verifySources=async(sources,expected)=>cases.push({sources,expected});'
              + block + ';console.log(JSON.stringify(cases));})();')
    cases = json.loads(subprocess.check_output([str(args.node), '-e', script], text=True))
    for path in sorted((root / 'testdata/conformance/k6-units').glob('*.ili')):
        cases.append(dict(id=path.stem, original=True, expected='rejected',
                          sources=[dict(sourceId=path.name, text=path.read_text(), path=str(path))]))
    java = str(args.java_home / 'bin/java')
    cli = str(root / 'interlis-cli/build/install/interlis-cli/bin/interlis-cli')
    env = dict(os.environ, JAVA_HOME=str(args.java_home))
    sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()

    def run(item):
        number, case = item
        folder = output / f'{number:03}'
        folder.mkdir(exist_ok=True)
        inputs = []
        for source in case['sources']:
            path = Path(source['path']) if 'path' in source else folder / source['sourceId'].split('/')[-1]
            if 'path' not in source:
                path.write_text(source['text'])
            inputs.append(path)
        results = {}
        for label, command in [('current', [cli, 'check', '--json', *map(str, inputs)]),
                               ('ili2c', [java, '-jar', str(args.ili2c), '-o0', *map(str, inputs)])]:
            try:
                process = subprocess.run(command, text=True, capture_output=True, env=env, timeout=45)
                process_error = process.returncode not in (0, 1) or bool(re.search(
                    r'java\.[\w.]+(?:Exception|Error)|^\s+at ch\.interlis\.|Cannot invoke .* because .* is null',
                    process.stderr, re.MULTILINE))
                results[label] = dict(command=command, exitCode=process.returncode, timedOut=False,
                                      processError=process_error, stdout=process.stdout, stderr=process.stderr)
            except subprocess.TimeoutExpired:
                results[label] = dict(command=command, exitCode=None, timedOut=True,
                                      processError=True, stdout='', stderr='process timed out')
        own = results['current']
        try:
            payload = json.loads(own['stdout'])
            if case['expected'] == 'succeeded':
                matches = own['exitCode'] == 0 and payload == {'status': 'ok', 'diagnostics': []}
            else:
                matches = (own['exitCode'] == 1 and payload['diagnostics']
                           and payload['status'].startswith('rejected: RejectionSummary[reasons=[SOURCE_ERRORS],')
                           and 'unsupportedFeatureIds=[]' in payload['status'])
        except (ValueError, KeyError, TypeError):
            matches = False
        return dict(id=case.get('id', f'K6-unit-{number}'), original=case.get('original', False),
                    expected=case['expected'], ownExpected=bool(matches and not own['processError'] and not own['stderr']),
                    sources=[dict(path=str(path), source=path.read_text(), sha256=sha(path)) for path in inputs], results=results)

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        rows = list(pool.map(run, enumerate(cases)))
    expected = sum(row['ownExpected'] for row in rows)
    agreements = sum(not any(p['processError'] for p in row['results'].values())
                     and row['results']['current']['exitCode'] == row['results']['ili2c']['exitCode'] for row in rows)
    report = dict(recordedAt=datetime.now(timezone.utc).isoformat(), baseCommit=subprocess.check_output(
        ['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(), referenceVersion='5.6.9',
        referenceJarSha256=sha(args.ili2c), ownArtifacts={str(p.relative_to(root)): sha(p)
        for p in sorted((Path(cli).parent.parent / 'lib').glob('*.jar'))},
        javaVersion=subprocess.run([java, '-version'], capture_output=True, text=True).stderr,
        total=len(rows), ownExpected=expected, referenceAgreement=agreements,
        processErrors=sum(p['processError'] for row in rows for p in row['results'].values()), cases=rows)
    (output.parent / 'k6-units-reference-comparison.json').write_text(json.dumps(report, indent=2) + '\n')
    print(f'{expected}/{len(rows)} own expectations; {agreements}/{len(rows)} reference agreements; '
          f'{report["processErrors"]} process errors')
    return 0 if expected == len(rows) and not report['processErrors'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
