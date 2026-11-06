"""Small regression checks for the validator output oracle."""

from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest

import run


class ValidatorOutputTest(unittest.TestCase):
    def test_unexpected_stderr_fails_even_when_exit_and_diagnostics_match(self):
        case = next(case for case in run.read_json(run.CASES_FILE) if case["id"] == "validate-23")
        versions = run.read_json(run.LOCK_FILE)["versions"]
        info = [
            *(f"{tool}-{versions[tool]}" for tool in ("ilivalidator", "ili2c", "iox-ili")),
            "first validation pass...",
            "second validation pass...",
            *case["requiredInfo"],
            "...validation done",
        ]
        stderr = "\n".join(f"Info: {line}" for line in info)
        valid = {"exitCode": 0, "mismatches": []}
        run.validator_check(case, "", stderr + "\n\n", versions, valid)
        self.assertEqual(valid["mismatches"], [])

        injected = ["java.lang.IllegalStateException: injected", "\tat example.Probe.run(Probe.java:1)"]
        invalid = {"exitCode": 0, "mismatches": []}
        run.validator_check(case, "", "\n".join([stderr, *injected]), versions, invalid)
        self.assertEqual(invalid["unexpectedLogLines"], injected)
        self.assertEqual(invalid["diagnostics"], [])
        self.assertEqual(invalid["missingInfo"], [])
        self.assertTrue(invalid["mismatches"])


class BenchmarkLimitsTest(unittest.TestCase):
    def test_sampled_scratch_limit_terminates_a_running_child(self):
        with TemporaryDirectory() as directory:
            work = Path(directory)
            scratch = work / "temp"
            scratch.mkdir()
            limits = {"timeoutSeconds": 5, "tempLimitBytes": 16}
            script = "from pathlib import Path; import time; Path('spill').write_bytes(b'x' * 128); time.sleep(10)"
            result = run.run_probe([sys.executable, "-c", script], work, scratch, limits)
            self.assertEqual(result["stopReason"], "temp_limit")
            self.assertIn("terminate", result["stopSignals"])
            self.assertNotEqual(result["exitCode"], 0)
            self.assertEqual(result["sampledPeakTempBytes"], 128)
            self.assertEqual(result["tempAtExitBytes"], 128)

    def test_pool_size_uses_recognized_close_file_size(self):
        prefix = "Info: close(): LinkPool KEY: "
        suffix = " </tmp/a b> (BTree.java:396)"
        match = run.POOL_CLOSE.fullmatch(prefix + "size 3, filesize 4096" + suffix)
        self.assertEqual(match.groups(), ("LinkPool KEY", "4096"))
        self.assertIsNone(run.POOL_CLOSE.fullmatch(prefix + "size 3" + suffix))


if __name__ == "__main__":
    unittest.main()
