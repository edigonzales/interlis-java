#!/usr/bin/env python3
"""Run selected original suite-interlis cases against the pinned legacy validator."""

import argparse
import json
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tempfile

import run
from suite_inventory import CATALOG_FILE, load_catalog, verify_checkout, verify_input
from suite_result import classify


MAX_CASES = 12
LIMITS = {"timeoutSeconds": run.CASE_TIMEOUT_SECONDS, "tempLimitBytes": 64 * 1024**2}


def external_output(path, checkout):
    output = (path or Path(tempfile.gettempdir())).resolve()
    for root in (run.HARNESS.parents[1], checkout.resolve()):
        if output.is_relative_to(root):
            raise ValueError(f"Output must be outside {root}")
    if path is None:
        return Path(tempfile.mkdtemp(prefix="suite-interlis-", dir=output))
    output.mkdir(mode=0o700)
    return output


def finish_case(result, status, reason):
    result["status"] = status
    result["assessment"] = {"status": status, "reason": reason}
    return result


def run_case(case, catalog, checkout, output, java, entrypoint, versions):
    case_id = case["id"]
    models = catalog["modelSets"][case["modelSet"]]
    inputs = {case["xtf"]: case["sha256"], **{path: catalog["models"][path] for path in models}}
    observed = {"state": "NOT_RUN", "argv": None, "cwd": None, "process": None, "logSha256": {}}
    result = {
        "id": case_id,
        "expectation": {key: case[key] for key in ("expectedAcceptance", "oracleStatus", "java", "documentation")},
        "norm": case["norm"], "ruleGroup": case["ruleGroup"], "phases": case["phases"],
        "expectedInputSha256": inputs, "inputSha256": {}, "stagedInputs": {}, "observed": observed,
    }
    for key in ("notes", "vendorSkips"):
        if key in case:
            result["expectation"][key] = case[key]

    work = output / case_id
    temp = work / "temp"
    home = temp / "home"
    plugins = temp / "plugins"
    home.mkdir(parents=True)
    plugins.mkdir()
    try:
        for path, digest in inputs.items():
            source = verify_input(checkout, path, digest)
            target = temp / path
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            verify_input(temp, path, digest)
            result["inputSha256"][path] = digest
            result["stagedInputs"][path] = str(target.relative_to(output))
    except (ValueError, OSError) as failure:
        return finish_case(result, "BLOCKED_INPUT", str(failure))

    options = [*run.JVM_OPTIONS, f"-Duser.home={home}", f"-Djava.io.tmpdir={temp}"]
    # A relative --modeldir value keeps path-list separators out of the resolver input.
    argv = [java, *options, "-jar", str(entrypoint), "--plugins", plugins.name,
            "--modeldir", "data/models", case["xtf"]]
    observed.update(argv=argv, cwd=str(temp))
    launch_failure = None
    try:
        observed["process"] = run.run_probe(argv, work, temp, LIMITS)
    except OSError as failure:
        launch_failure = failure
    for name in ("stdout.log", "stderr.log"):
        log = work / name
        if log.is_file():
            observed["logSha256"][str(log.relative_to(output))] = run.sha256(log)
    if launch_failure is not None:
        observed["state"] = "TOOL_FAILURE"
        observed["failureKind"] = "LAUNCH"
        return finish_case(result, "TOOL_FAILURE", str(launch_failure))

    stdout = (work / "stdout.log").read_text(encoding="utf-8", errors="replace")
    stderr = (work / "stderr.log").read_text(encoding="utf-8", errors="replace")
    outcome = classify(case, observed["process"], stdout, stderr, versions)
    observed.update(outcome["observed"])
    result["assessment"] = outcome["assessment"]
    result["status"] = outcome["assessment"]["status"]
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkout", required=True, type=Path, help="external suite-interlis Git checkout")
    parser.add_argument("--archive", required=True, type=Path, help="local locked ilivalidator release ZIP")
    parser.add_argument("--case", required=True, action="append", help="original case ID (repeatable)")
    parser.add_argument("--output", type=Path, help="new external directory (default: fresh temporary directory)")
    args = parser.parse_args()
    if len(args.case) > MAX_CASES or len(set(args.case)) != len(args.case):
        parser.error(f"select at most {MAX_CASES} unique case IDs")
    try:
        catalog = load_catalog()
    except (ValueError, OSError) as failure:
        parser.error(str(failure))
    cases = {case["id"]: case for case in catalog["cases"]}
    unknown = set(args.case) - cases.keys()
    if unknown:
        parser.error(f"unknown case IDs: {', '.join(sorted(unknown))}")
    try:
        output = external_output(args.output, args.checkout)
    except FileExistsError:
        parser.error(f"--output must name a new directory: {args.output}")
    except (ValueError, OSError) as failure:
        parser.error(f"cannot create output directory: {failure}")

    print(f"Evidence: {output}", flush=True)
    report = {
        "schema": 1, "status": "FAIL", "upstream": catalog["upstream"],
        "checkout": str(args.checkout.resolve()), "python": platform.python_version(), "platform": platform.platform(),
        "invocation": [sys.executable, str(Path(__file__).resolve()), *sys.argv[1:]],
        "selectedCases": args.case, "budgets": {**LIMITS, "maxCases": MAX_CASES},
        "setup": [], "cases": [], "logSha256Scope": "raw-output-of-this-run",
    }
    stage = "checkout"
    try:
        checkout = verify_checkout(args.checkout, catalog["upstream"]["commit"])
        report["verifiedCheckoutCommit"] = catalog["upstream"]["commit"]
        stage = "archive"
        lock = run.read_json(run.LOCK_FILE)
        report["versions"] = lock["versions"]
        report["sourceSha256"] = {path.name: run.sha256(path) for path in (
            CATALOG_FILE, run.LOCK_FILE, Path(__file__), run.HARNESS / "run.py",
            run.HARNESS / "suite_inventory.py", run.HARNESS / "suite_result.py",
        )}
        setup_temp = output / "setup-temp"
        setup_temp.mkdir()
        env = run.ENV | {key: str(setup_temp) for key in ("TMPDIR", "TMP", "TEMP")}
        entrypoint = run.legacy_jars(lock, str(args.archive.resolve()), output, report, env)
        stage = "java"
        java_path = shutil.which("java")
        if java_path is None:
            raise ValueError("Java 25 is required on PATH")
        java = str(Path(java_path).resolve())
        version = run.setup_process([java, "-version"], output, report, env)
        if not re.search(r'version "25(?=[."+-])', version):
            raise ValueError("Java 25 is required on PATH")
        stage = "case"
        for case_id in args.case:
            result = run_case(cases[case_id], catalog, checkout, output, java, entrypoint, lock["versions"])
            report["cases"].append(result)
            process = result["observed"]["process"] or {}
            print(f"{result['status']} {case_id} (exit {process.get('exitCode', 'not started')})", flush=True)
            if process.get("stopReason") == "interrupted":
                break
        if len(report["cases"]) == len(args.case) and all(case["status"] == "PASS" for case in report["cases"]):
            report["status"] = "PASS"
    except (ValueError, OSError, subprocess.TimeoutExpired) as failure:
        report["status"] = "BLOCKED_INPUT" if stage in ("checkout", "archive") else "TOOL_FAILURE"
        report["error"] = {"stage": stage, "message": str(failure)}
        print(f"{report['status']} {stage}: {failure}", file=sys.stderr)
    finally:
        completed = {case["id"] for case in report["cases"]}
        report["notRun"] = {"status": "NOT_RUN",
                            "unselectedCases": sorted(cases.keys() - set(args.case)),
                            "selectedCases": [case_id for case_id in args.case if case_id not in completed]}
        (output / "report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
