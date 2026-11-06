"""Classify the pinned ilivalidator process separately from the suite oracle."""

from collections import Counter
import re

from run import FATAL_LOG


VERSION_TOOLS = ("ilivalidator", "ili2c", "iox-ili")
VERSION_PREFIXES = tuple(f"Info: {tool}-" for tool in VERSION_TOOLS)
FIRST_PASS = "Info: first validation pass..."
SECOND_PASS = "Info: second validation pass..."
DONE = "Info: ...validation done"
FAILED = "Info: ...validation failed"
LIFECYCLE = {FIRST_PASS, SECOND_PASS, DONE, FAILED}
MODEL_NOT_FOUND = re.compile(r"Error: ([\w]+(?:, [\w]+)*): model\(s\) not found")
RUNTIME_FAILURE = re.compile(
    r"Exception in thread|(?:Caused by|Suppressed):|"
    r"\b(?:java|javax|jdk|sun)\.[\w.$]*(?:Exception|Error)\b|"
    r"\bat [\w.$]+\([^\r\n]*\.java:\d+\)"
)

# The diagnostics are tied to these unchanged originals and the existing 1.15.0 lock.
REJECTIONS = {
    "RTO.T03a": (
        "Error: TestSuite.GebaeudeRegister: tid TestSuite.Bodenbedeckung: BID TestSuite.Bodenbedeckung "
        "of TestSuite.GebaeudeRegister already exists in TestSuite.Bodenbedeckung",
    ),
    "RAt.T02b": (
        "Error: line 12: TestSuite.Bodenbedeckung.GebaeudeArt: tid ga4: Attribute Art3 is length restricted to 3",
    ),
    "RKB.T01c": (
        "Error: line 12: TestSuite.Kardinalitaet.A: tid a1: "
        "RolleB should associate 1 to 1 target objects (instead of 2)",
    ),
    "RKo.T01b": (
        "Error: line 15: TestSuite.Bodenbedeckung.Geschichte: tid ges2: Unique constraint "
        "TestSuite.Bodenbedeckung.Geschichte.Constraint1 is violated! Values A already exist in Object: ges1",
    ),
    "RKo.T06c": (
        "Error: line 15: TestSuite.Bodenbedeckung.Gebaeude: tid geb1: Mandatory Constraint "
        "TestSuite.Bodenbedeckung.Gebaeude.Constraint1 is not true.",
    ),
    "RPO.T02b": (
        "Error: line 12: TestSuite.Bodenbedeckung.BoLinien: tid bl8: "
        "Attribute Form has an invalid self-intersection at (2480110.0, 1060130.0)",
    ),
    "RSU.T01a": (
        "Error: TestSuite.Bodenbedeckung.BoFlaechen: tid f7: Overlay "
        "coord1 (2480120.000, 1060110.000), coord2 (2480120.000, 1060125.000), tids f7, f8",
        "Error: TestSuite.Bodenbedeckung.BoFlaechen: tid f7: Overlay "
        "coord1 (2480120.000, 1060125.000), coord2 (2480120.000, 1060140.000), tids f7, f8",
        "Error: failed to validate AREA TestSuite.Bodenbedeckung.BoFlaechen.Form",
    ),
}
XML_CASE = "RXW.T01b"
XML_EXCEPTION = "Error: javax.xml.stream.XMLStreamException: ParseError at [row,col]:[31,11]"
XML_ERROR = "Error:   ParseError at [row,col]:[31,11]"
XML_MESSAGE = """Message: The end-tag for element type "TRANSFER" must end with a '>' delimiter."""
XML_REJECTION = [XML_EXCEPTION, XML_MESSAGE, XML_ERROR, XML_MESSAGE]


def classify(case, process, stdout, stderr, versions):
    """Return the observed process state and the comparison with the Java assertion."""
    case_id = case["id"]
    lines, diagnostics, unexpected, diagnostic_positions = [], [], [], []
    for stream in (stdout, stderr):
        previous = None
        for line in stream.splitlines():
            xml_message = case_id == XML_CASE and line == XML_MESSAGE and previous in (XML_EXCEPTION, XML_ERROR)
            if line.startswith(("Error: ", "Warning: ")) or xml_message:
                diagnostics.append(line)
                diagnostic_positions.append(len(lines))
            elif line.strip() and not line.startswith("Info: "):
                unexpected.append(line)
            lines.append(line)
            previous = line
    markers = [line for line in lines if line in LIFECYCLE]
    observed_versions = [line for line in lines if line.startswith(VERSION_PREFIXES)]
    observed = {
        "diagnostics": diagnostics,
        "unexpectedLogLines": unexpected,
        "validationMarkers": [line.removeprefix("Info: ") for line in markers],
        "versionMessages": [line.removeprefix("Info: ") for line in observed_versions],
    }

    def result(state, status, reason):
        return {
            "observed": {"state": state, **observed},
            "assessment": {"status": status, "reason": reason},
        }

    def tool_failure(kind, reason):
        observed["failureKind"] = kind
        return result("TOOL_FAILURE", "TOOL_FAILURE", reason)

    exit_code = process["exitCode"]
    if process.get("timedOut") or process.get("stopReason"):
        stop_reason = process.get("stopReason") or "timeout"
        return tool_failure(stop_reason.upper(), f"Validator stopped: {stop_reason}")
    if exit_code not in (0, 1):
        return tool_failure("UNEXPECTED_EXIT", f"Unexpected validator exit {exit_code}")
    expected_versions = [f"Info: {tool}-{versions[tool]}" for tool in VERSION_TOOLS]
    if Counter(observed_versions) != Counter(expected_versions):
        return tool_failure("VERSION_MISMATCH", "Validator version messages differ from the locked versions")

    xml_rejected = case_id == XML_CASE and diagnostics == XML_REJECTION
    if any(FATAL_LOG.search(line) for line in lines):
        return tool_failure("FATAL_ERROR", "Validator reported a fatal JVM or storage error")
    if any(RUNTIME_FAILURE.search(line) and not (xml_rejected and line == XML_EXCEPTION) for line in lines):
        return tool_failure("RUNTIME_EXCEPTION", "Validator reported a runtime exception")
    if unexpected:
        return tool_failure("UNEXPECTED_OUTPUT", "Validator emitted unrecognised output")

    missing_models = {name for line in diagnostics if (match := MODEL_NOT_FOUND.fullmatch(line))
                      for name in match[1].split(", ")}
    wrong_header_model = case_id == "RHE.T05a" and "AModel" in missing_models
    if wrong_header_model:
        missing_models.discard("AModel")  # The incorrect header declaration is the test input.
    if missing_models:
        if exit_code != 1:
            return tool_failure("INCOMPLETE_VALIDATION", "Validator reported missing models on its acceptance path")
        return result("BLOCKED_INPUT", "BLOCKED_INPUT",
                      "Unresolved local models: " + ", ".join(sorted(missing_models)))

    expected = case["expectedAcceptance"]
    conflict = case.get("oracleStatus") == "CONFLICT"
    terminal = DONE if exit_code == 0 else FAILED
    expected_markers = [FIRST_PASS, terminal] if exit_code == 1 and xml_rejected else [
        FIRST_PASS, SECOND_PASS, terminal,
    ]
    if markers != expected_markers:
        if wrong_header_model:
            return tool_failure("MODEL_DISCOVERY", "Declared header model AModel could not be resolved before validation")
        if "Error: no models given to look for" in diagnostics:
            return tool_failure("MODEL_DISCOVERY", "Model discovery ended before a validation result")
        return tool_failure("INCOMPLETE_VALIDATION", "Validator phase and terminal messages do not match its exit")
    first_pass = lines.index(FIRST_PASS)
    completion = lines.index(terminal)
    if not all(first_pass < position < completion for position in diagnostic_positions):
        return tool_failure("INCOMPLETE_VALIDATION", "Validator diagnostics occurred outside the validation passes")

    if exit_code == 0:
        if diagnostics:
            return tool_failure("UNEXPECTED_OUTPUT", "Validator reported diagnostics on its acceptance path")
        if conflict:
            return result("ACCEPTED", "CONFLICT", "JUnit and suite documentation disagree")
        if expected is True:
            return result("ACCEPTED", "PASS", "Validator accepted the original as expected by JUnit")
        if expected is False:
            return result("ACCEPTED", "FAIL", "Validator accepted an original JUnit expects to reject")
        return result("ACCEPTED", "UNSUPPORTED", "No known JUnit expectation")

    if not any(line.startswith("Error: ") for line in diagnostics):
        return tool_failure("INCOMPLETE_VALIDATION", "Validator did not report a rejection diagnostic")
    if conflict:
        return result("REJECTED", "CONFLICT", "JUnit and suite documentation disagree")
    if expected is True:
        return result("REJECTED", "FAIL", "Validator rejected an original JUnit expects to accept")
    if expected is not False:
        return result("REJECTED", "UNSUPPORTED", "No known JUnit expectation")
    if case_id not in REJECTIONS and case_id != XML_CASE:
        return result("REJECTED", "UNSUPPORTED", "No verified diagnostic oracle for this original")
    if xml_rejected or (case_id in REJECTIONS and Counter(diagnostics) == Counter(REJECTIONS[case_id])):
        return result("REJECTED", "PASS", "Validator reported the expected case-specific rejection")
    return result("REJECTED", "FAIL", "Validator diagnostics differ from the case-specific rejection")
