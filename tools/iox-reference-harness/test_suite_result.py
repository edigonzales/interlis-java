"""Regression checks for classifying pinned validator logs."""

import unittest

from run import LOCK_FILE, read_json
from suite_inventory import load_catalog
from suite_result import classify


TEXT_LENGTH_DIAGNOSTIC = (
    "Error: line 12: TestSuite.Bodenbedeckung.GebaeudeArt: tid ga4: "
    "Attribute Art3 is length restricted to 3"
)
XML_END_TAG_MESSAGE = 'Message: The end-tag for element type "TRANSFER" must end with a \'>\' delimiter.'
XML_REJECTION = [
    "Info: validate data...",
    "Info: first validation pass...",
    "Error: javax.xml.stream.XMLStreamException: ParseError at [row,col]:[31,11]",
    XML_END_TAG_MESSAGE,
    "Error:   ParseError at [row,col]:[31,11]",
    XML_END_TAG_MESSAGE,
    "Info: ...validation failed",
]


class ValidatorResultClassificationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cases = {case["id"]: case for case in load_catalog()["cases"]}
        cls.versions = read_json(LOCK_FILE)["versions"]
        cls.version_lines = [
            f"Info: {tool}-{cls.versions[tool]}"
            for tool in ("ilivalidator", "ili2c", "iox-ili")
        ]

    def assess(self, case_id, exit_code, lines, stop_reason=None):
        process = {
            "exitCode": exit_code,
            "stopReason": stop_reason,
            "timedOut": stop_reason == "timeout",
        }
        stderr = "\n".join(self.version_lines + list(lines)) + "\n"
        return classify(self.cases[case_id], process, "", stderr, self.versions)

    def assert_outcome(self, result, state, status):
        self.assertEqual(result["observed"]["state"], state, result)
        self.assertEqual(result["assessment"]["status"], status, result)

    def validation_log(self, terminal, *diagnostics):
        return [
            "Info: validate data...",
            "Info: first validation pass...",
            *diagnostics,
            "Info: second validation pass...",
            f"Info: ...validation {terminal}",
        ]

    def test_completed_validation_accepts_positive_case(self):
        result = self.assess("RAt.T02a", 0, self.validation_log("done"))
        self.assert_outcome(result, "ACCEPTED", "PASS")

    def test_observed_acceptance_does_not_resolve_an_oracle_conflict(self):
        case = self.cases["RAt.T02a"] | {"oracleStatus": "CONFLICT"}
        stderr = "\n".join(self.version_lines + self.validation_log("done")) + "\n"
        result = classify(case, {"exitCode": 0}, "", stderr, self.versions)
        self.assert_outcome(result, "ACCEPTED", "CONFLICT")

    def test_expected_rejection_requires_matching_diagnostic(self):
        lines = self.validation_log("failed", TEXT_LENGTH_DIAGNOSTIC)
        result = self.assess("RAt.T02b", 1, lines)
        self.assert_outcome(result, "REJECTED", "PASS")

    def test_unrelated_rejection_fails_suite_expectation(self):
        lines = self.validation_log(
            "failed",
            "Error: line 12: TestSuite.Bodenbedeckung.GebaeudeArt: tid ga4: "
            "Attribute Art3 is missing",
        )
        result = self.assess("RAt.T02b", 1, lines)
        self.assert_outcome(result, "REJECTED", "FAIL")

    def test_uncurated_negative_case_has_no_verified_assessment(self):
        lines = self.validation_log(
            "failed",
            "Error: line 12: TestSuite.Bodenbedeckung.GebaeudeArt: tid ga4: "
            "Attribute Beschreibung is length restricted to 9",
        )
        result = self.assess("RAt.T03a", 1, lines)
        self.assert_outcome(result, "REJECTED", "UNSUPPORTED")

    def test_matching_diagnostic_cannot_mask_jvm_crash(self):
        lines = self.validation_log(
            "failed",
            TEXT_LENGTH_DIAGNOSTIC,
            'Exception in thread "main" java.lang.NullPointerException',
        )
        result = self.assess("RAt.T02b", 1, lines)
        self.assert_outcome(result, "TOOL_FAILURE", "TOOL_FAILURE")
        self.assertEqual(result["observed"]["failureKind"], "RUNTIME_EXCEPTION")

    def test_controlled_xml_rejection_stops_after_first_pass(self):
        result = self.assess("RXW.T01b", 1, XML_REJECTION)
        self.assert_outcome(result, "REJECTED", "PASS")

    def test_xml_rejection_with_stack_trace_is_a_tool_failure(self):
        lines = [*XML_REJECTION, "\tat org.interlis2.validator.Validator.runValidation(Validator.java:100)"]
        result = self.assess("RXW.T01b", 1, lines)
        self.assert_outcome(result, "TOOL_FAILURE", "TOOL_FAILURE")

    def test_timeout_cannot_pass_even_with_success_markers(self):
        result = self.assess("RAt.T02a", -9, self.validation_log("done"), "timeout")
        self.assert_outcome(result, "TOOL_FAILURE", "TOOL_FAILURE")
        self.assertEqual(result["observed"]["failureKind"], "TIMEOUT")

    def test_acceptance_requires_both_passes_and_completion(self):
        for missing in (
            "Info: first validation pass...",
            "Info: second validation pass...",
            "Info: ...validation done",
        ):
            with self.subTest(missing=missing):
                lines = self.validation_log("done")
                lines.remove(missing)
                result = self.assess("RAt.T02a", 0, lines)
                self.assert_outcome(result, "TOOL_FAILURE", "TOOL_FAILURE")

    def test_missing_local_model_blocks_validation(self):
        for case_id, missing_models in (
            ("RAt.T02b", "TestSuite2"),
            ("RHE.T05a", "TestSuite2, AModel"),
        ):
            with self.subTest(case_id=case_id):
                result = self.assess(case_id, 1, [f"Error: {missing_models}: model(s) not found"])
                self.assert_outcome(result, "BLOCKED_INPUT", "BLOCKED_INPUT")

    def test_deliberate_unknown_header_model_is_not_a_missing_dependency(self):
        result = self.assess("RHE.T05a", 1, ["Error: AModel: model(s) not found"])
        self.assert_outcome(result, "TOOL_FAILURE", "TOOL_FAILURE")
        self.assertEqual(result["observed"]["failureKind"], "MODEL_DISCOVERY")


if __name__ == "__main__":
    unittest.main()
