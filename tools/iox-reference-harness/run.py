#!/usr/bin/env python3
"""Run the pinned IOX and ilivalidator reference cases in isolated JVMs."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tempfile
import time
from zipfile import ZipFile

HARNESS = Path(__file__).resolve().parent
CORPUS = HARNESS.parents[1] / "testdata/reference/iox"
JAVA_SOURCE = HARNESS / "LegacyProbe.java"
LOCK_FILE = HARNESS / "legacy-lock.json"
CASES_FILE = CORPUS / "cases.json"
TEMP_SAMPLE_MS = 5
CASE_TIMEOUT_SECONDS = 30
HEAP_LIMIT_BYTES = 2 * 1024**3
JVM_OPTIONS = [
    "-Xmx2g",
    "-Duser.language=en", "-Duser.country=US",
    "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
    "-Djavax.xml.accessExternalDTD=", "-Djavax.xml.accessExternalSchema=",
]
ENV = {key: value for key, value in os.environ.items() if key not in {
    "CLASSPATH", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "JDK_JAVAC_OPTIONS", "_JAVA_OPTIONS",
}}


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as data:
        for chunk in iter(lambda: data.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def setup_process(argv, output, report, env):
    result = subprocess.run(argv, cwd=output, env=env, capture_output=True, timeout=60,
                            encoding="utf-8", errors="replace")
    report["setup"].append({
        "argv": argv, "exitCode": result.returncode, "stdout": result.stdout, "stderr": result.stderr,
    })
    if result.returncode:
        raise ValueError(f"{argv[0]} exited with {result.returncode}: {result.stderr.strip()}")
    return (result.stdout + result.stderr).strip()


def legacy_jars(lock, archive_arg, output, report, env):
    archive = output / "ilivalidator.zip"
    if archive_arg:
        shutil.copyfile(archive_arg, archive)
    else:
        setup_process([
            "curl", "--fail", "--silent", "--show-error", "--location", "--proto", "=https",
            "--proto-redir", "=https", "--max-time", "55",
            "--output", str(archive), lock["archive"]["url"],
        ], output, report, env)
    actual = sha256(archive)
    report["archive"] = {"sha256": actual, "url": lock["archive"]["url"], "localInput": archive_arg}
    if actual != lock["archive"]["sha256"]:
        raise ValueError(f"Legacy archive SHA-256 mismatch: {actual}")

    distribution = output / "legacy"
    with ZipFile(archive) as bundle:
        jars = lock["jarSha256"]
        if sorted(name for name in bundle.namelist() if name.endswith(".jar")) != sorted(jars):
            raise ValueError("Legacy JAR inventory does not match lock")
        for name, digest in jars.items():
            target = distribution / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(bundle.read(name))
            if sha256(target) != digest:
                raise ValueError(f"Legacy JAR SHA-256 mismatch: {name}")
    report["verifiedJarSha256"] = jars
    return distribution / lock["archive"]["entrypoint"]


def temp_bytes(directory):
    total = 0
    for path in directory.rglob("*"):
        try:
            if path.is_file():
                total += path.stat().st_size
        except FileNotFoundError:
            pass  # A short-lived temporary file vanished between listing and stat.
    return total


def run_probe(argv, work, temp):
    env = ENV | {key: str(temp) for key in ("TMPDIR", "TMP", "TEMP")}
    started = time.monotonic()
    peak_temp = 0
    timed_out = False
    with (work / "stdout.log").open("wb") as stdout, (work / "stderr.log").open("wb") as stderr:
        with subprocess.Popen(argv, cwd=work, env=env, stdout=stdout, stderr=stderr) as child:
            try:
                while child.poll() is None:
                    peak_temp = max(peak_temp, temp_bytes(temp))
                    if time.monotonic() - started >= CASE_TIMEOUT_SECONDS:
                        timed_out = True
                        child.kill()
                        break
                    time.sleep(TEMP_SAMPLE_MS / 1000)
            finally:
                if child.poll() is None:
                    child.kill()
                exit_code = child.wait()
    elapsed = time.monotonic() - started
    temp_at_exit = temp_bytes(temp)
    return {
        "exitCode": exit_code, "timedOut": timed_out, "wallMillis": round(elapsed * 1000, 2),
        "sampledPeakTempBytes": max(peak_temp, temp_at_exit), "tempAtExitBytes": temp_at_exit,
        "tempSampleIntervalMillis": TEMP_SAMPLE_MS,
    }


def reader_check(case, stdout, stderr, result):
    expected = (CORPUS / case["events"]).read_text(encoding="utf-8")
    expected_error = case.get("readerError")
    expected_exit = 0 if expected_error is None else 2
    actual_error = stderr.strip() or None
    if expected_error is not None and result["exitCode"] == 2:
        try:
            actual_error = json.loads(stderr)
        except json.JSONDecodeError:
            result["mismatches"].append("Expected a structured reader error on stderr")
    result["readerError"] = actual_error
    result["eventLines"] = stdout.count("\n")
    if stdout != expected:
        result["mismatches"].append(f"IOX events differ from {case['events']}")
    if result["exitCode"] != expected_exit or actual_error != expected_error:
        result["mismatches"].append(f"Expected exit {expected_exit} and reader error {expected_error}")


def validator_check(case, stdout, stderr, versions, result):
    lines = stdout.splitlines() + stderr.splitlines()
    info = [line.removeprefix("Info: ") for line in lines if line.startswith("Info: ")]
    diagnostics = sorted(line for line in lines if line.startswith(("Error: ", "Warning: ")))
    unexpected = [line for line in lines
                  if line.strip() and not line.startswith(("Info: ", "Error: ", "Warning: "))]
    positive = case["expectation"] == "positive"
    expected_exit = 0 if positive else 1
    terminal = [line for line in info if line in ("...validation done", "...validation failed")]
    expected_terminal = ["...validation done" if positive else "...validation failed"]
    required = ["first validation pass...", "second validation pass..."] + case["requiredInfo"]
    required += [f"{tool}-{versions[tool]}" for tool in ("ilivalidator", "ili2c", "iox-ili")]
    result["validatorTerminal"] = terminal
    result["diagnostics"] = diagnostics
    result["unexpectedLogLines"] = unexpected
    result["missingInfo"] = [line for line in required if line not in info]
    if result["exitCode"] != expected_exit or terminal != expected_terminal:
        result["mismatches"].append(f"Expected exit {expected_exit} and completion {expected_terminal}")
    if diagnostics != sorted(case["diagnostics"]):
        result["mismatches"].append("Validator diagnostics differ from the case expectation")
    if unexpected:
        result["mismatches"].append("Unexpected validator output outside Info, Error, and Warning lines")
    if result["missingInfo"]:
        result["mismatches"].append("Missing validator phase, coverage, or version messages")


def run_case(case, output, classpath, versions):
    work = output / case["id"]
    temp = work / "temp"
    home = work / "home"
    temp.mkdir(parents=True)
    home.mkdir()
    inputs = work / "inputs"
    inputs.mkdir()
    model = inputs / Path(case["model"]).name
    xtf = inputs / Path(case["xtf"]).name
    shutil.copyfile(CORPUS / case["model"], model)
    shutil.copyfile(CORPUS / case["xtf"], xtf)
    heap_log = work / "heap.json"
    argv = ["java", *JVM_OPTIONS, f"-Duser.home={home}", f"-Djava.io.tmpdir={temp}",
            "-cp", classpath, "LegacyProbe", str(heap_log), case["mode"]]
    if case["mode"] == "read":
        argv += [case["version"], str(model), str(xtf)]
    elif case["mode"] == "validate":
        argv += ["--plugins", str(work / "plugins"), "--modeldir", str(model.parent),
                 "--models", model.stem, str(xtf)]
        (work / "plugins").mkdir()
    else:
        raise ValueError(f"Unknown case mode: {case['mode']}")

    result = {"id": case["id"], "expectation": case["expectation"], "argv": argv, "mismatches": []}
    if case["expectation"] == "known-defect":
        result.update(reason=case["reason"], source=case["source"])
    result["inputSha256"] = {case["model"]: sha256(model), case["xtf"]: sha256(xtf)}
    if "events" in case:
        result["inputSha256"][case["events"]] = sha256(CORPUS / case["events"])
    result.update(run_probe(argv, work, temp))
    if heap_log.exists():
        heap = read_json(heap_log)
        result["heap"] = heap
        if heap.get("heapLimitBytes") != HEAP_LIMIT_BYTES:
            result["mismatches"].append(f"Expected JVM heap limit of {HEAP_LIMIT_BYTES} bytes (-Xmx2g)")
    else:
        result["mismatches"].append("Missing JVM heap sample")
    if result["timedOut"]:
        result["mismatches"].append(f"Exceeded {CASE_TIMEOUT_SECONDS} s process timeout")
    stdout = (work / "stdout.log").read_text(encoding="utf-8")
    stderr = (work / "stderr.log").read_text(encoding="utf-8")
    result["logSha256"] = {name: sha256(work / name) for name in ("stdout.log", "stderr.log")}
    if case["mode"] == "read":
        reader_check(case, stdout, stderr, result)
    else:
        validator_check(case, stdout, stderr, versions, result)
    if result["mismatches"]:
        result["status"] = "FAIL"
    elif case["expectation"] == "known-defect":
        result["status"] = "XFAIL"
    else:
        result["status"] = "PASS"
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, help="verify and use this local release ZIP without downloading")
    parser.add_argument("--output", type=Path, help="new directory for reports (default: a fresh temp directory)")
    parser.add_argument("--case", action="append", help="run only this case ID (repeatable)")
    args = parser.parse_args()

    cases = read_json(CASES_FILE)
    ids = {case["id"] for case in cases}
    if not ids or len(ids) != len(cases):
        parser.error("case inventory must be nonempty with unique IDs")
    if args.case and set(args.case) - ids:
        parser.error(f"unknown case IDs: {', '.join(sorted(set(args.case) - ids))}")
    selected = [case for case in cases if not args.case or case["id"] in args.case]
    archive_arg = str(args.archive.resolve()) if args.archive else None
    try:
        if args.output:
            output = args.output.resolve()
            output.mkdir()
        else:
            output = Path(tempfile.mkdtemp(prefix="iox-reference-")).resolve()
    except FileExistsError:
        parser.error(f"--output must name a new directory: {args.output}")
    except OSError as failure:
        parser.error(f"cannot create output directory: {failure}")
    print(f"Evidence: {output}", flush=True)
    lock = read_json(LOCK_FILE)
    report = {
        "schema": 1, "python": sys.version.splitlines()[0], "platform": platform.platform(),
        "invocation": [sys.executable, str(Path(__file__).resolve()), *sys.argv[1:]],
        "sourceSha256": {path.name: sha256(path) for path in (Path(__file__), JAVA_SOURCE, LOCK_FILE, CASES_FILE)},
        "selectedCases": [case["id"] for case in selected], "setup": [], "cases": [],
        "versions": lock["versions"], "logSha256Scope": "raw-output-of-this-run", "status": "FAIL",
    }
    try:
        setup_temp = output / "setup-temp"
        setup_temp.mkdir()
        setup_env = ENV | {key: str(setup_temp) for key in ("TMPDIR", "TMP", "TEMP")}
        java_version = setup_process(["java", "-version"], output, report, setup_env)
        javac_version = setup_process(["javac", "-version"], output, report, setup_env)
        if not re.search(r'version "25(?=[."+-])', java_version) or not re.match(r'javac 25(?:[.+-]|$)', javac_version):
            raise ValueError("Java 25 and javac 25 are required on PATH")
        entrypoint = legacy_jars(lock, archive_arg, output, report, setup_env)
        classes = output / "classes"
        classes.mkdir()
        setup_process(["javac", "--release", "25", "-encoding", "UTF-8", "-cp", str(entrypoint),
                       "-d", str(classes), str(JAVA_SOURCE)], output, report, setup_env)
        classpath = os.pathsep.join(map(str, (classes, entrypoint)))
        for case in selected:
            result = run_case(case, output, classpath, lock["versions"])
            report["cases"].append(result)
            print(f"{result['status']:5} {result['id']} (exit {result['exitCode']})")
        if all(result["status"] != "FAIL" for result in report["cases"]):
            report["status"] = "PASS"
    except (OSError, ValueError, subprocess.TimeoutExpired) as failure:
        report["error"] = str(failure)
        print(f"FAIL {failure}", file=sys.stderr)
    finally:
        executed = {case["id"] for case in report["cases"]}
        report["notRun"] = [case["id"] for case in cases if case["id"] not in executed]
        (output / "report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
