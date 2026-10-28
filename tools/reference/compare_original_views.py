#!/usr/bin/env python3
"""Compare the 19 pinned B1–B3 originals through real compiler processes.

Requires an already built CLI distribution. All fixture pins are checked before
starting any process. Observations go under build/; expectations stay unchanged.
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
import time


REFERENCE_SHA256 = '37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e'
UPSTREAM_COMMIT = '4ea207f388ef7d91b03d968cc1636f4de84e36ea'


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--ili2c', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    fixture_root = root / 'testdata/conformance/b1-b3-acceptance'
    pins = fixture_root / 'sources.properties'
    cases = []
    for line in pins.read_text().splitlines():
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        test_id, value = line.split('=', 1)
        filename, expected_hash, upstream_path = value.split('|')
        source = fixture_root / filename
        if source.parent != fixture_root or sha256(source) != expected_hash:
            raise SystemExit(f'Source pin mismatch: {test_id}: {source}')
        text = source.read_text()
        language = re.search(r'\bINTERLIS\s+(2\.[34])\s*;', text)
        models = re.findall(r'(?m)^\s*(?:CONTRACTED\s+)?MODEL\s+(\w+)', text)
        if not language or not models:
            raise SystemExit(f'Missing fixture language/models: {test_id}')
        cases.append(dict(testId=test_id, source=source, sha256=expected_hash,
                          upstreamPath=upstream_path, languageVersion=language[1], rootModels=models))
    if len(cases) != 19 or len({case['testId'] for case in cases}) != 19:
        raise SystemExit('Expected exactly 19 unique pinned original cases')
    if sha256(args.ili2c) != REFERENCE_SHA256:
        raise SystemExit('The reference JAR does not match the pinned ili2c 5.6.9 distribution')

    java = str(args.java_home / 'bin/java')
    cli = root / 'interlis-cli/build/install/interlis-cli/bin/interlis-cli'
    env = dict(os.environ, JAVA_HOME=str(args.java_home))
    artifacts = {str(path.relative_to(root)): sha256(path)
                 for path in sorted((cli.parent.parent / 'lib').glob('*.jar'))}
    if not artifacts:
        raise SystemExit('Build the CLI distribution before running this comparison')

    def run(command):
        started = time.monotonic()
        try:
            process = subprocess.run(command, capture_output=True, text=True, env=env, cwd=root, timeout=45)
            process_error = process.returncode not in (0, 1) or bool(re.search(
                r'java\.[\w.]+(?:Exception|Error)|^\s+at ch\.interlis\.|Cannot invoke .* because .* is null',
                process.stderr, re.MULTILINE))
            return dict(command=command, exitCode=process.returncode, timedOut=False,
                        processError=process_error, stdout=process.stdout, stderr=process.stderr,
                        durationMs=round((time.monotonic() - started) * 1000))
        except subprocess.TimeoutExpired as failure:
            stdout = failure.stdout.decode(errors='replace') if isinstance(failure.stdout, bytes) else failure.stdout
            stderr = failure.stderr.decode(errors='replace') if isinstance(failure.stderr, bytes) else failure.stderr
            return dict(command=command, exitCode=None, timedOut=True, processError=True,
                        stdout=stdout or '', stderr=stderr or '',
                        durationMs=round((time.monotonic() - started) * 1000))
        except OSError as failure:
            return dict(command=command, exitCode=None, timedOut=False, processError=True,
                        stdout='', stderr=str(failure), durationMs=round((time.monotonic() - started) * 1000))

    versions = dict(current=run([str(cli), 'version', '--json']),
                    ili2c=run([java, '-jar', str(args.ili2c), '--version']),
                    java=run([java, '-version']))

    def compare(case):
        results = dict(current=run([str(cli), 'check', '--json', str(case['source'])]),
                       ili2c=run([java, '-jar', str(args.ili2c), '-o0', str(case['source'])]))
        current = results['current']
        try:
            payload = json.loads(current['stdout'])
            current_expected = (current['exitCode'] == 0 and not current['processError']
                                and not current['stderr'] and payload == {'status': 'ok', 'diagnostics': []})
        except (ValueError, TypeError):
            current_expected = False
        reference_expected = results['ili2c']['exitCode'] == 0 and not results['ili2c']['processError']
        return dict(testId=case['testId'], expected='accept', languageVersion=case['languageVersion'],
                    rootModels=case['rootModels'], inputPins=[dict(path=str(case['source'].relative_to(root)),
                    sha256=case['sha256'], upstreamPath=case['upstreamPath'])],
                    currentExpected=current_expected, referenceExpected=reference_expected, results=results)

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        rows = list(pool.map(compare, cases))
    sources_unchanged = all(sha256(case['source']) == case['sha256'] for case in cases)
    artifacts_unchanged = all(sha256(root / path) == expected for path, expected in artifacts.items())
    version_checks = (all(row['exitCode'] == 0 and not row['processError'] for row in versions.values())
                      and 'Version 5.6.9-' in versions['ili2c']['stdout'] + versions['ili2c']['stderr'])
    report = dict(recordedAt=datetime.now(timezone.utc).isoformat(),
                  baseCommit=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
                  upstreamRepository='https://github.com/claeis/ili2c', upstreamCommit=UPSTREAM_COMMIT,
                  sourceManifest=dict(path=str(pins.relative_to(root)), sha256=sha256(pins)),
                  sourcesUnchanged=sources_unchanged, artifactsUnchanged=artifacts_unchanged,
                  referenceVersion='5.6.9', referenceJarSha256=REFERENCE_SHA256,
                  ownArtifacts=artifacts, toolVersions=versions, versionsVerified=version_checks,
                  total=len(rows), currentExpected=sum(row['currentExpected'] for row in rows),
                  referenceExpected=sum(row['referenceExpected'] for row in rows),
                  processErrors=sum(result['processError'] for row in rows for result in row['results'].values()),
                  cases=rows)
    output = root / 'build/reports/stabilization/original-view-reference-comparison.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + '\n')
    print(f'{report["currentExpected"]}/19 current expectations; '
          f'{report["referenceExpected"]}/19 reference expectations; {report["processErrors"]} process errors')
    return 0 if (report['currentExpected'] == 19 and report['referenceExpected'] == 19
                 and not report['processErrors'] and sources_unchanged and artifacts_unchanged and version_checks) else 1


if __name__ == '__main__':
    raise SystemExit(main())
