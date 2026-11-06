"""Small regression checks for the validator output oracle."""

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


if __name__ == "__main__":
    unittest.main()
