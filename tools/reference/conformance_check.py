#!/usr/bin/env python3
"""Pinned INTERLIS compiler conformance run against the public CLI.

The suite and runner are external test evidence, not production dependencies.
Only INTERLIS 2.3/2.4 cases are selected; INTERLIS 1.0 is excluded explicitly.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tarfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMMIT = "88576ffc4cd491dd72d2375eb897ea9f3f55d2c3"
URL = f"https://codeberg.org/edigonzales/interlis-compiler-testsuite/archive/{COMMIT}.tar.gz"
SHA256 = "6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe"
CACHE = Path(os.environ.get("CONFORMANCE_CACHE", str(Path.home() / ".cache" / "interlis-java" / "conformance")))
OUT = ROOT / "build" / "reports" / "conformance"
FILTER = os.environ.get("CONFORMANCE_FILTER", r"^(ili23|ili24)\.")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def prepare() -> Path:
    CACHE.mkdir(parents=True, exist_ok=True)
    archive = CACHE / f"suite-{COMMIT}.tar.gz"
    if not archive.exists():
        with urllib.request.urlopen(URL, timeout=180) as response, archive.open("wb") as target:
            shutil.copyfileobj(response, target)
    actual = sha256(archive)
    if actual != SHA256:
        raise SystemExit(f"suite hash mismatch: {actual} != {SHA256}")
    suite = CACHE / f"suite-{COMMIT}"
    if not (suite / "InterlisConformance.java").exists():
        with tarfile.open(archive, "r:gz") as tar:
            tar.extractall(CACHE)
        extracted = CACHE / f"interlis-compiler-testsuite-{COMMIT}"
        if not extracted.exists():
            extracted = CACHE / "interlis-compiler-testsuite"
        if extracted.exists():
            if suite.exists():
                shutil.rmtree(suite)
            extracted.rename(suite)
    if not (suite / "InterlisConformance.java").exists():
        raise SystemExit(f"runner not found after extraction: {suite}")
    return suite


def diagnostic_signatures(payload: dict, paths: list[Path], inputs: list[str]) -> list[dict]:
    """Keep semantic arguments, multiplicity and UTF-16 ranges; discard only run-local IDs."""
    if len(paths) != len(inputs):
        raise ValueError("diagnostic source count mismatch")
    sources = {path.absolute().as_uri(): name for path, name in zip(paths, inputs)}
    result = []
    for diagnostic in payload["diagnostics"]:
        if (diagnostic["severity"] != "ERROR" or diagnostic["phase"] not in ("SEMANTIC_CHECKING", "TYPE_CHECKING")
                or not diagnostic["code"].startswith("ILI-SEM-")):
            raise ValueError("exception contains a non-semantic error or unexpected warning")
        source_range = dict(diagnostic["range"])
        source_range["source"] = sources[source_range["source"]]
        result.append({key: diagnostic[key] for key in ("code", "severity", "phase", "messageKey", "arguments")}
                      | {"range": source_range})
    return sorted(result, key=lambda item: json.dumps(item, sort_keys=True))


def check_json_process(result: subprocess.CompletedProcess, rejected: bool) -> dict:
    if result.returncode != (1 if rejected else 0):
        raise ValueError(f"unexpected process exit {result.returncode}")
    if result.stderr:
        raise ValueError(f"unexpected process stderr: {result.stderr}")
    payload = json.loads(result.stdout)
    if rejected:
        if (not payload["status"].startswith("rejected: RejectionSummary[reasons=[SOURCE_ERRORS],")
                or "unsupportedFeatureIds=[]" not in payload["status"] or not payload["diagnostics"]):
            raise ValueError("expected source rejection without unsupported features")
    elif payload != {"status": "ok", "diagnostics": []}:
        raise ValueError("positive control must succeed without diagnostics")
    return payload


def evaluate_normative_case(entry: dict, case: dict, cli: Path) -> dict:
    """An explicit, pinned exception is earned by both negative and positive CLI checks."""
    evidence = {"testId": case["testId"], "referenceExpectation": case["expectation"],
                "referenceClassification": case["classification"], "actualOutcome": case["actualOutcome"],
                "ownExpectation": entry["ownExpectation"], "norm": entry["norm"], "result": "FAIL"}
    try:
        rejected = entry["ownExpectation"] == "rejected"
        expected_reference = {"outcome": "accept" if rejected else "reject"}
        if (entry["ownExpectation"] not in ("accepted", "rejected")
                or entry["referenceExpectation"] != expected_reference
                or case["expectation"] != expected_reference
                or case["languageVersion"] != entry["languageVersion"] or not entry["norm"]
                or case["timedOut"] or case["exitCode"] != (1 if rejected else 0)
                or case["actualOutcome"] != entry["ownExpectation"]
                or case["classification"] != "FAIL"):
            raise ValueError("reference result, language or explicit expectation mismatch")
        inputs = [source["path"] for source in entry["sources"]]
        if inputs != case["inputs"] or case["command"][:2] != [str(cli), "check"]:
            raise ValueError("source list or runner command mismatch")
        paths = [Path(path) for path in case["command"][2:]]
        if len(paths) != len(inputs):
            raise ValueError("source count mismatch")
        for path, source in zip(paths, entry["sources"]):
            if not path.as_posix().endswith("/" + source["path"]) or sha256(path) != source["sha256"]:
                raise ValueError("original source pin mismatch")
        controls = [ROOT / source["path"] for source in entry["positiveControls"]]
        if not controls:
            raise ValueError("positive control missing")
        for path, source in zip(controls, entry["positiveControls"]):
            if not path.resolve().is_relative_to(ROOT.resolve()) or sha256(path) != source["sha256"]:
                raise ValueError("positive control pin mismatch")
        checks = [("original", paths, inputs, rejected),
                  ("positiveControl", controls, [s["path"] for s in entry["positiveControls"]], False)]
        if not rejected:
            negatives = entry["negativeControls"]
            negative_paths = [ROOT / source["path"] for source in negatives]
            if not negatives:
                raise ValueError("negative control missing for accepted original")
            for path, source in zip(negative_paths, negatives):
                if not path.resolve().is_relative_to(ROOT.resolve()) or sha256(path) != source["sha256"]:
                    raise ValueError("negative control pin mismatch")
            checks.append(("negativeControl", negative_paths, [s["path"] for s in negatives], True))
        for name, files, names, reject_check in checks:
            command = [str(cli), "check", "--json", *map(str, files)]
            result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, timeout=60, check=False)
            evidence[name] = {"command": command, "exitCode": result.returncode,
                              "stdout": result.stdout, "stderr": result.stderr}
            payload = check_json_process(result, reject_check)
            if reject_check and diagnostic_signatures(payload, files, names) != sorted(
                    entry["allowedDiagnostics"], key=lambda item: json.dumps(item, sort_keys=True)):
                raise ValueError("semantic diagnostics differ from the reviewed expectation")
        evidence["result"] = "PASS"
    except (KeyError, TypeError, ValueError, OSError, subprocess.TimeoutExpired) as error:
        evidence["error"] = str(error)
    return evidence


def assess_report(report: dict, summary: dict, runner_exit: int, selected_ids: set[str],
                  manifest: dict, cli: Path) -> dict:
    """Preserve reference results; never let an exception mask incomplete/process-failed runs."""
    if (manifest["schemaVersion"] != 1 or manifest["suiteCommit"] != COMMIT
            or manifest["suiteSha256"] != SHA256 or len(report["compilers"]) != 1):
        raise ValueError("suite/manifest identity mismatch")
    cases = report["compilers"][0]["tests"]
    ids = [case["testId"] for case in cases]
    if len(ids) != len(set(ids)) or set(ids) != selected_ids:
        raise ValueError("incomplete or duplicate reference results")
    counts = {key: 0 for key in ("PASS", "XFAIL", "FAIL", "XPASS", "ERROR")}
    for case in cases:
        counts[case["classification"]] += 1
    counts["TOTAL"] = len(cases)
    raw_pass = not any(counts[key] for key in ("FAIL", "ERROR", "XPASS"))
    if (counts != report["summary"] or counts != summary["summary"]
            or counts != report["compilers"][0]["summary"]
            or report["overallResult"] != ("PASS" if raw_pass else "FAIL")
            or summary["overallResult"] != report["overallResult"]
            or runner_exit != (0 if raw_pass else 1)):
        raise ValueError("runner exit, result and fresh reports disagree")
    entries = {entry["testId"]: entry for entry in manifest["cases"]}
    if len(entries) != len(manifest["cases"]):
        raise ValueError("duplicate normative expectation")
    assessments = []
    for case in cases:
        if case["testId"] in entries:
            assessment = evaluate_normative_case(entries[case["testId"]], case, cli)
            assessments.append(assessment)
            if assessment["result"] == "PASS":
                counts["FAIL"] -= 1
                counts["XFAIL"] += 1
    # A filtered run may omit known exceptions, but a full run must contain every entry.
    if FILTER == r"^(ili23|ili24)\." and not set(entries).issubset(selected_ids):
        raise ValueError("normative expectation missing from selected suite")
    passed = not any(counts[key] for key in ("FAIL", "ERROR", "XPASS")) and all(
        entry["result"] == "PASS" for entry in assessments)
    return {"overallResult": "PASS" if passed else "FAIL", "summary": counts,
            "referenceSummary": summary["summary"], "referenceResult": report["overallResult"],
            "normativeAssessments": assessments, "referenceReport": "build/conformance/report.json"}


def main() -> int:
    suite = prepare()
    cli = ROOT / "interlis-cli" / "build" / "install" / "interlis-cli" / "bin" / "interlis-cli"
    if not cli.exists():
        raise SystemExit(f"CLI distribution not found: {cli}; run :interlis-cli:installDist")
    output = ROOT / "build" / "conformance"
    output.mkdir(parents=True, exist_ok=True)
    baseline = ROOT / "docs" / "evidence" / "conformance" / "baseline.json"
    if not baseline.is_file():
        raise SystemExit(f"baseline not found: {baseline}")
    config = {
        "suite": str(suite / "suite" / "test-cases.json"),
        "outputDirectory": str(output),
        "cacheDirectory": str(CACHE / "fixtures"),
        "timeoutSeconds": 60,
        "parallelism": 4,
        "xpassPolicy": "fail",
        "filter": FILTER,
        "compilers": [{
            "id": "interlis-java",
            "source": str(cli),
            "command": ["{compiler}", "check", "{files}"],
            "acceptedExitCodes": [0],
            "rejectedExitCodes": [1],
            "internalErrorPatterns": ["internal failure:", "Exception in thread"],
            "baseline": str(baseline)
        }]
    }
    config_path = output / "run-config.json"
    config_path.write_text(json.dumps(config, indent=2) + "\n")
    log = output / "conformance.log"
    command = ["java", str(suite / "InterlisConformance.java"), "run", "--config", str(config_path)]
    (output / "summary.json").unlink(missing_ok=True)
    (output / "report.json").unlink(missing_ok=True)
    (OUT / "conformance-summary.json").unlink(missing_ok=True)
    with log.open("w", encoding="utf-8") as handle:
        completed = subprocess.run(command, cwd=ROOT, stdout=handle, stderr=subprocess.STDOUT,
                                   timeout=1800, check=False)
    summary_path = output / "summary.json"
    if not summary_path.exists():
        raise SystemExit(f"conformance runner produced no summary; see {log}")
    summary = json.loads(summary_path.read_text())
    all_cases = json.loads((suite / "suite" / "test-cases.json").read_text())["testCases"]
    selected = [case for case in all_cases if re.search(FILTER, case["id"])]
    excluded = [case for case in all_cases if case["languageVersion"] not in ("2.3", "2.4")]
    manifest = json.loads((ROOT / "docs/evidence/conformance/normative-expectations.json").read_text())
    report = json.loads((output / "report.json").read_text())
    assessment = assess_report(report, summary, completed.returncode, {case["id"] for case in selected}, manifest, cli)
    (output / "normative-assessment.json").write_text(json.dumps(assessment, indent=2) + "\n")
    summary.update(assessment)
    summary["suite"] = {"url": URL, "commit": COMMIT, "sha256": SHA256}
    summary["filter"] = FILTER
    summary["selection"] = {
        "languageVersions": ["2.3", "2.4"],
        "suiteCaseCount": len(all_cases),
        "selectedCaseCount": len(selected),
        "excludedCaseCount": len(excluded),
        "excludedVersions": sorted({case["languageVersion"] for case in excluded})
    }
    summary["cli"] = str(cli.relative_to(ROOT))
    summary["exitCode"] = completed.returncode
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "conformance-summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    log_text = log.read_text(errors="replace")
    log_text = (log_text.replace(str(ROOT), "<repo>")
                .replace(str(CACHE), "<cache>")
                .replace(str(Path.home()), "<home>"))
    (OUT / "conformance-log.txt").write_text(log_text[-12000:] + "\n")
    counts = summary.get("summary", {})
    print(f"selected={len(selected)} totalSuite={len(all_cases)} "
          f"excluded={len(excluded)} result={summary.get('overallResult', '?')} "
          f"exit={completed.returncode}")
    print(" ".join(f"{key}={counts.get(key, 0)}" for key in
                   ("PASS", "XFAIL", "FAIL", "XPASS", "ERROR")))
    return 0 if summary.get("overallResult") == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
