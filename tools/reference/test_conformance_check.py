"""Failure-path contracts for explicit normative exceptions (no external tools needed)."""
import copy
import hashlib
import json
import re
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import conformance_check as c


class OriginalViewPortableFixtureTest(unittest.TestCase):
    def test_portable_originals_match_pinned_source_bytes_and_all_model_roots(self):
        root = Path(__file__).resolve().parents[2]
        fixtures = root / 'testdata/conformance/b1-b3-acceptance'
        pins = {}
        for line in (fixtures / 'sources.properties').read_text(encoding='utf-8').splitlines():
            if not line or line.startswith('#'):
                continue
            test_id, value = line.split('=', 1)
            self.assertNotIn(test_id, pins, 'duplicate original fixture pin')
            pins[test_id] = value.split('|', 2)
        shared = (root / 'interlis-wasm-teavm/stabilization-contract.cjs').read_text(encoding='utf-8')
        begin = '// BEGIN PINNED B1-B3 ORIGINALS JSON'
        end = '// END PINNED B1-B3 ORIGINALS JSON'
        self.assertEqual(1, shared.count(begin))
        self.assertEqual(1, shared.count(end))
        block = shared.split(begin, 1)[1].split(end, 1)[0].strip()
        prefix = 'const originalViewFixtures = '
        self.assertTrue(block.startswith(prefix))
        self.assertTrue(block.endswith(';'))
        embedded = json.loads(block[len(prefix):-1])
        self.assertEqual(19, len(pins))
        self.assertEqual(19, len(embedded))
        self.assertEqual(set(pins), {entry['testId'] for entry in embedded})
        for entry in embedded:
            with self.subTest(test_id=entry['testId']):
                filename, pin, _ = pins[entry['testId']]
                original = (fixtures / filename).read_bytes()
                self.assertEqual(original, entry['text'].encode('utf-8'))
                self.assertEqual(pin, hashlib.sha256(original).hexdigest())
                self.assertEqual(pin, entry['sha256'])
                roots = re.findall(r'^\s*(?:CONTRACTED\s+)?MODEL\s+([A-Za-z_]\w*)\b',
                                   original.decode('utf-8'), re.MULTILINE)
                self.assertTrue(roots)
                self.assertEqual(roots, entry['roots'])


class NormativeExpectationsTest(unittest.TestCase):
    def setUp(self):
        filter_patch = patch.object(c, 'FILTER', r'^(ili23|ili24)\.')
        filter_patch.start()
        self.addCleanup(filter_patch.stop)
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        patcher = patch.object(c, 'ROOT', self.root)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.original = self.root / 'original.ili'
        self.positive = self.root / 'positive.ili'
        self.original.write_text('invalid source')
        self.positive.write_text('valid source')
        self.cli = self.root / 'compiler'
        self.payload = {'status': 'rejected: RejectionSummary[reasons=[SOURCE_ERRORS], unsupportedFeatureIds=[]]',
                        'diagnostics': [{'id': 'run-local', 'code': 'ILI-SEM-0005', 'severity': 'ERROR',
                                         'phase': 'TYPE_CHECKING', 'messageKey': 'diagnostic.semantics.type',
                                         'arguments': [{'name': 'actual', 'kind': 'TYPE_NAME', 'value': 'TEXT*20'}],
                                         'range': {'source': self.original.as_uri(), 'revision': '1',
                                                   'startOffset': 1, 'endOffset': 2}}]}
        self.entry = {'testId': 'ili23.test', 'languageVersion': '2.3', 'referenceExpectation': {'outcome': 'accept'},
                      'ownExpectation': 'rejected', 'norm': {'url': 'https://www.interlis.ch', 'sections': '2.6'},
                      'sources': [{'path': 'original.ili', 'sha256': c.sha256(self.original)}],
                      'positiveControls': [{'path': 'positive.ili', 'sha256': c.sha256(self.positive)}],
                      'allowedDiagnostics': c.diagnostic_signatures(self.payload, [self.original], ['original.ili'])}
        self.case = {'testId': 'ili23.test', 'languageVersion': '2.3', 'expectation': {'outcome': 'accept'},
                     'classification': 'FAIL', 'actualOutcome': 'rejected', 'exitCode': 1, 'timedOut': False,
                     'inputs': ['original.ili'], 'command': [str(self.cli), 'check', str(self.original)]}
        self.counts = {'PASS': 0, 'FAIL': 1, 'XFAIL': 0, 'XPASS': 0, 'ERROR': 0, 'TOTAL': 1}
        self.report = {'overallResult': 'FAIL', 'summary': self.counts,
                       'compilers': [{'summary': self.counts, 'tests': [self.case]}]}
        self.summary = {'overallResult': 'FAIL', 'summary': self.counts}
        self.manifest = {'schemaVersion': 1, 'suiteCommit': c.COMMIT, 'suiteSha256': c.SHA256, 'cases': [self.entry]}

    def process(self, payload=None, code=1, stderr=''):
        return subprocess.CompletedProcess([], code, json.dumps(payload or self.payload), stderr)

    def assess(self, results=None):
        if results is None:
            results = [self.process(), self.process({'status': 'ok', 'diagnostics': []}, 0)]
        with patch.object(c.subprocess, 'run', side_effect=results):
            return c.assess_report(self.report, self.summary, 1, {'ili23.test'}, self.manifest, self.cli)

    def test_only_reviewed_negative_and_positive_checks_earn_exception(self):
        result = self.assess()
        self.assertEqual('PASS', result['overallResult'])
        self.assertEqual(1, result['summary']['XFAIL'])
        self.assertEqual(1, result['referenceSummary']['FAIL'])
        self.assertEqual('FAIL', self.case['classification'])  # raw evidence remains unchanged

    def test_unexpected_argument_range_or_multiplicity_blocks_exception(self):
        for change in ('arguments', 'range', 'duplicate', 'missing'):
            with self.subTest(change=change):
                payload = copy.deepcopy(self.payload)
                if change == 'arguments': payload['diagnostics'][0]['arguments'][0]['value'] = 'BOOLEAN'
                elif change == 'range': payload['diagnostics'][0]['range']['startOffset'] = 0
                elif change == 'duplicate': payload['diagnostics'] *= 2
                else: payload['diagnostics'] = []
                self.assertEqual('FAIL', self.assess([self.process(payload)])['overallResult'])

    def test_warning_syntax_error_and_implementation_guard_cannot_be_waived(self):
        for key, value in [('severity', 'WARNING'), ('phase', 'PARSING'), ('code', 'ILI-IMPL-0001')]:
            with self.subTest(value=value):
                payload = copy.deepcopy(self.payload)
                payload['diagnostics'][0][key] = value
                self.assertEqual('FAIL', self.assess([self.process(payload)])['overallResult'])

    def test_cancellation_internal_failure_or_incomplete_support_cannot_be_waived(self):
        for status in ['cancelled: stopped', 'failed: defect',
                       'rejected: RejectionSummary[reasons=[SOURCE_ERRORS, INCOMPLETE_SUPPORT], unsupportedFeatureIds=[VIEW]]']:
            payload = self.payload | {'status': status}
            self.assertEqual('FAIL', self.assess([self.process(payload)])['overallResult'])

    def test_process_error_stderr_malformed_json_and_timeout_fail(self):
        for result in [self.process(code=2), self.process(stderr='Exception'),
                       subprocess.CompletedProcess([], 1, 'not JSON', ''),
                       subprocess.TimeoutExpired('compiler', 60)]:
            with self.subTest(result=result):
                self.assertEqual('FAIL', self.assess([result])['overallResult'])

    def test_failed_positive_control_prevents_exception(self):
        self.assertEqual('FAIL', self.assess([self.process(), self.process()])['overallResult'])

    def test_positive_control_warnings_prevent_exception(self):
        self.assertEqual('FAIL', self.assess([self.process(), self.process(self.payload | {'status': 'ok'}, 0)])['overallResult'])

    def test_original_and_positive_pins_are_enforced(self):
        for path in [self.original, self.positive]:
            with self.subTest(path=path):
                before = path.read_bytes()
                path.write_text('changed')
                self.assertEqual('FAIL', self.assess([])['overallResult'])
                path.write_bytes(before)

    def test_language_and_reference_expectation_are_enforced(self):
        for key, value in [('languageVersion', '2.4'), ('expectation', {'outcome': 'reject'})]:
            before = self.case[key]
            self.case[key] = value
            self.assertEqual('FAIL', self.assess([])['overallResult'])
            self.case[key] = before

    def test_missing_duplicate_or_unknown_result_fails(self):
        for cases in [[], [self.case, self.case], [self.case | {'testId': 'other'}]]:
            self.report['compilers'][0]['tests'] = cases
            with self.assertRaises(ValueError): self.assess([])

    def test_nonstandard_runner_exit_cannot_be_hidden(self):
        with self.assertRaises(ValueError):
            c.assess_report(self.report, self.summary, 2, {'ili23.test'}, self.manifest, self.cli)

    def test_stale_summary_fails(self):
        self.summary = {'overallResult': 'PASS', 'summary': self.counts | {'FAIL': 0, 'PASS': 1}}
        with self.assertRaises(ValueError): self.assess([])

    def test_changed_suite_or_duplicate_exception_fails(self):
        self.manifest['suiteSha256'] = 'changed'
        with self.assertRaises(ValueError): self.assess([])
        self.manifest['suiteSha256'] = c.SHA256
        self.manifest['cases'] *= 2
        with self.assertRaises(ValueError): self.assess([])

    def test_unknown_exception_is_not_silently_ignored(self):
        self.manifest['cases'].append(self.entry | {'testId': 'missing'})
        with self.assertRaises(ValueError): self.assess()

    def test_reference_xpass_and_error_stay_failures(self):
        self.manifest['cases'] = []
        for classification in ['XPASS', 'ERROR']:
            self.case['classification'] = classification
            counts = self.counts | {'FAIL': 0, classification: 1}
            self.report['summary'] = self.report['compilers'][0]['summary'] = self.summary['summary'] = counts
            self.assertEqual('FAIL', self.assess([])['overallResult'])

    def accepted_original_checks(self):
        self.negative = self.root / 'zero.ili'
        self.negative.write_text('zero tolerance')
        negative = copy.deepcopy(self.payload)
        negative['diagnostics'][0]['range']['source'] = self.negative.as_uri()
        self.entry.update(ownExpectation='accepted', referenceExpectation={'outcome': 'reject'},
                          negativeControls=[{'path': 'zero.ili', 'sha256': c.sha256(self.negative)}],
                          allowedDiagnostics=c.diagnostic_signatures(negative, [self.negative], ['zero.ili']))
        self.case.update(expectation={'outcome': 'reject'}, actualOutcome='accepted', exitCode=0)
        ok = self.process({'status': 'ok', 'diagnostics': []}, 0)
        return [ok, ok, self.process(negative)]

    def test_accepted_original_requires_clean_acceptance_and_exact_negative_control(self):
        result = self.assess(self.accepted_original_checks())
        self.assertEqual('PASS', result['overallResult'])
        self.assertEqual(1, result['summary']['XFAIL'])
        self.assertEqual(1, result['referenceSummary']['FAIL'])
        self.assertEqual('FAIL', self.case['classification'])
        self.assertEqual('accepted', result['normativeAssessments'][0]['actualOutcome'])

    def test_accepted_original_without_pinned_negative_control_cannot_be_waived(self):
        self.accepted_original_checks()
        for missing in [None, []]:
            with self.subTest(missing=missing):
                self.entry['negativeControls'] = missing
                self.assertEqual('FAIL', self.assess([])['overallResult'])
        del self.entry['negativeControls']
        self.assertEqual('FAIL', self.assess([])['overallResult'])
        self.entry['negativeControls'] = [{'path': 'zero.ili', 'sha256': 'wrong'}]
        self.assertEqual('FAIL', self.assess([])['overallResult'])

    def test_accepted_original_checks_cannot_mask_warnings_errors_or_wrong_control(self):
        checks = self.accepted_original_checks()
        negative = json.loads(checks[2].stdout)
        wrong = copy.deepcopy(negative)
        wrong['diagnostics'][0]['arguments'][0]['value'] = 'BOOLEAN'
        for results in [
            [self.process(code=1)],
            [self.process({'status': 'ok', 'diagnostics': []}, 0, stderr='Exception')],
            [self.process(self.payload | {'status': 'ok'}, 0)],
            [checks[0], checks[1], checks[0]],
            [checks[0], checks[1], self.process(wrong)],
            [checks[0], checks[1], self.process(negative | {'status': 'failed: defect'})],
        ]:
            with self.subTest(results=results):
                self.assertEqual('FAIL', self.assess(results)['overallResult'])

    def test_accepted_original_cannot_hide_changed_reference_outcome_or_unknown_expectation(self):
        self.accepted_original_checks()
        self.case['classification'] = 'XPASS'
        counts = self.counts | {'FAIL': 0, 'XPASS': 1}
        self.report['summary'] = self.report['compilers'][0]['summary'] = self.summary['summary'] = counts
        self.assertEqual('FAIL', self.assess([])['overallResult'])
        self.case['classification'] = 'FAIL'
        self.report['summary'] = self.report['compilers'][0]['summary'] = self.summary['summary'] = self.counts
        self.entry['ownExpectation'] = 'anything'
        self.assertEqual('FAIL', self.assess([])['overallResult'])


if __name__ == '__main__':
    unittest.main()
