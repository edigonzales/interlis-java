#!/usr/bin/env python3
"""Run the pinned IOX and ilivalidator reference cases in isolated JVMs."""

import argparse
from contextlib import suppress
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import re
import shutil
import signal
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
BENCHMARK_INPUT_BYTES = 16 * 1024**2
STOP_GRACE_SECONDS = 0.25
POOL_CLOSE = re.compile(
    r"Info: close\(\): ([^:]+): (?:size \d+, )?filesize (\d+)"
    r"(?:, updates \d+,)? <.+> \((?:BTree|BTreeImpl|ObjPoolImpl2)\.java:\d+\)"
)
FATAL_LOG = re.compile(r"OutOfMemoryError|No space left on device|A fatal error has been detected",
                       re.IGNORECASE)
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


def run_probe(argv, work, temp, limits=None):
    env = ENV | {key: str(temp) for key in ("TMPDIR", "TMP", "TEMP")}
    cwd = work
    timeout = CASE_TIMEOUT_SECONDS
    resource_platform = platform.system() if limits else None
    use_wait4 = resource_platform in ("Darwin", "Linux") and hasattr(os, "wait4")
    if limits:
        env.pop("ILI_LEGACYAREAREAS", None)
        cwd = temp
        timeout = limits["timeoutSeconds"]
    started = time.monotonic()
    peak_temp = 0
    peak_scratch = 0
    stop_reason = None
    stop_started = None
    monitor_error = None
    signals = []
    usage = None
    with (work / "stdout.log").open("wb") as stdout, (work / "stderr.log").open("wb") as stderr:
        with subprocess.Popen(argv, cwd=cwd, env=env, stdout=stdout, stderr=stderr) as child:
            try:
                while True:
                    if use_wait4:
                        pid, status, child_usage = os.wait4(child.pid, os.WNOHANG)
                        if pid:
                            child.returncode = os.waitstatus_to_exitcode(status)
                            usage = child_usage
                    else:
                        child.poll()
                    if not limits and child.returncode is not None:
                        break
                    current_temp = temp_bytes(temp)
                    peak_temp = max(peak_temp, current_temp)
                    if limits:
                        log_bytes = os.fstat(stdout.fileno()).st_size + os.fstat(stderr.fileno()).st_size
                        for name in ("gc.log", "heap.json"):
                            with suppress(FileNotFoundError):
                                log_bytes += (work / name).stat().st_size
                        peak_scratch = max(peak_scratch, current_temp + log_bytes)
                        if peak_scratch > limits["tempLimitBytes"]:
                            stop_reason = stop_reason or "temp_limit"
                    now = time.monotonic()
                    if now - started >= timeout and (limits or child.returncode is None):
                        stop_reason = stop_reason or "timeout"
                    if child.returncode is not None:
                        break
                    if stop_reason:
                        if not limits:
                            child.kill()
                            break
                        # Popen.send_signal can reap the child before wait4 collects its usage.
                        if stop_started is None:
                            stop_started = now
                            with suppress(ProcessLookupError):
                                if use_wait4:
                                    os.kill(child.pid, signal.SIGTERM)
                                else:
                                    child.terminate()
                                signals.append("terminate")
                        elif signals == ["terminate"] and now - stop_started >= STOP_GRACE_SECONDS:
                            with suppress(ProcessLookupError):
                                if use_wait4:
                                    os.kill(child.pid, signal.SIGKILL)
                                else:
                                    child.kill()
                                signals.append("kill")
                    time.sleep(TEMP_SAMPLE_MS / 1000)
            except KeyboardInterrupt:
                if not limits:
                    raise
                stop_reason = "interrupted"
            except OSError as failure:
                if not limits:
                    raise
                stop_reason = stop_reason or "monitor_error"
                monitor_error = str(failure)
            finally:
                if child.returncode is None:
                    child.kill()
                    if limits:
                        signals.append("kill")
                exit_code = child.wait()
    elapsed = time.monotonic() - started
    temp_at_exit = temp_bytes(temp)
    result = {
        "exitCode": exit_code, "timedOut": stop_reason == "timeout", "wallMillis": round(elapsed * 1000, 2),
        "sampledPeakTempBytes": max(peak_temp, temp_at_exit), "tempAtExitBytes": temp_at_exit,
        "tempSampleIntervalMillis": TEMP_SAMPLE_MS,
    }
    if limits:
        result.update(stopReason=stop_reason, stopSignals=signals, monitorError=monitor_error,
                      sampledPeakScratchBytes=peak_scratch,
                      processCpuMillis=None, peakRssBytes=None, processUsageSource=None,
                      processReadBytes=None, processWrittenBytes=None)
        if usage:
            result.update(processCpuMillis=round((usage.ru_utime + usage.ru_stime) * 1000, 2),
                          peakRssBytes=usage.ru_maxrss * (1024 if resource_platform == "Linux" else 1) or None,
                          processUsageSource=f"{resource_platform} wait4")
    return result


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


def run_case(case, output, classpath, versions, limits=None, java="java"):
    work = output / case["id"]
    temp = work / "temp"
    home = (temp if limits else work) / "home"
    temp.mkdir(parents=True)
    home.mkdir()
    inputs = work / "inputs"
    inputs.mkdir()
    model = inputs / Path(case["model"]).name
    xtf = inputs / Path(case["xtf"]).name
    shutil.copyfile(CORPUS / case["model"], model)
    shutil.copyfile(CORPUS / case["xtf"], xtf)
    heap_log = work / "heap.json"
    options = [*JVM_OPTIONS, f"-Duser.home={home}", f"-Djava.io.tmpdir={temp}"]
    if limits:
        options += ["-Xlog:gc,gc+heap=debug,gc+init,gc+heap+exit:file=../gc.log:uptimemillis,level,tags:filecount=0"]
    mode = "benchmark" if limits else case["mode"]
    argv = [java, *options, "-cp", classpath, "LegacyProbe", str(heap_log), mode]
    if case["mode"] == "read":
        argv += [case["version"], str(model), str(xtf)]
    elif case["mode"] == "validate":
        if limits:
            argv.append("--trace")
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
    try:
        result.update(run_probe(argv, work, temp, limits))
    finally:
        if limits:
            try:
                shutil.rmtree(temp)
            except OSError as failure:
                result["mismatches"].append(f"Cannot clean benchmark scratch: {failure}")
            result["scratchRemoved"] = not temp.exists()
    if heap_log.exists():
        try:
            heap = read_json(heap_log)
        except (OSError, ValueError):
            if not limits:
                raise
            heap = None
        result["heap"] = heap
        if not isinstance(heap, dict) or heap.get("heapLimitBytes") != HEAP_LIMIT_BYTES:
            result["mismatches"].append(f"Expected JVM heap limit of {HEAP_LIMIT_BYTES} bytes (-Xmx2g)")
        elif limits and "gcCollectionMillis" not in heap:
            result["mismatches"].append("Missing GC collection measurement status")
    else:
        if limits:
            result["heap"] = None
        result["mismatches"].append("Missing JVM heap sample")
    if limits and result["stopReason"]:
        result["mismatches"].append(f"Benchmark stopped: {result['stopReason']}")
    elif result["timedOut"]:
        result["mismatches"].append(f"Exceeded {CASE_TIMEOUT_SECONDS} s process timeout")
    errors = "replace" if limits else "strict"
    stdout = (work / "stdout.log").read_text(encoding="utf-8", errors=errors)
    stderr = (work / "stderr.log").read_text(encoding="utf-8", errors=errors)
    logs = ["stdout.log", "stderr.log"]
    if limits:
        if (work / "gc.log").is_file():
            logs.append("gc.log")
        elif not result["stopReason"]:
            result["mismatches"].append("Missing JVM GC log")
        benchmark_result(case, model, xtf, stdout, stderr, result)
    result["logSha256"] = {name: sha256(work / name) for name in logs}
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


def benchmark_result(case, model, xtf, stdout, stderr, result):
    lines = stdout.splitlines() + stderr.splitlines()
    pools = [match.groups() for line in lines if (match := POOL_CLOSE.fullmatch(line))]
    result["poolCloseSizes"] = [{"pool": pool, "logicalBytes": int(size)} for pool, size in pools] or None
    result["inputBytes"] = {case["model"]: model.stat().st_size, case["xtf"]: xtf.stat().st_size}
    result["workload"] = case.get("workload")
    result["verifiedInfo"] = [marker for marker in
                              ["first validation pass...", "second validation pass...", *case["requiredInfo"]]
                              if f"Info: {marker}" in lines]
    fatal = FATAL_LOG.search(stdout + "\n" + stderr)
    if fatal:
        result["mismatches"].append(f"Fatal JVM or storage failure: {fatal.group()}")


def benchmark_limits(parser, args, selected):
    if not args.archive or not args.archive.is_file():
        parser.error("BLOCKED_INPUT: --benchmark requires a local release ZIP via --archive")
    output = args.output or Path(tempfile.gettempdir())
    if output.resolve().is_relative_to(HARNESS.parents[1]):
        parser.error("benchmark output must be outside the repository")
    if not args.case or any(case["mode"] != "validate" for case in selected):
        parser.error("--benchmark requires explicit validator --case selections")
    if not math.isfinite(args.timeout_seconds) or not 0 < args.timeout_seconds <= 300:
        parser.error("--timeout-seconds must be greater than 0 and at most 300")
    if not 0 < args.temp_limit_bytes <= 1024**3:
        parser.error("--temp-limit-bytes must be between 1 and 1073741824")
    if not len(selected) <= args.max_cases <= 32:
        parser.error("--max-cases must cover the selected cases and be at most 32")
    input_bytes = sum((CORPUS / case[key]).stat().st_size for case in selected for key in ("model", "xtf"))
    if input_bytes > BENCHMARK_INPUT_BYTES or args.archive.stat().st_size > BENCHMARK_INPUT_BYTES:
        parser.error("benchmark inputs or release ZIP exceed the 16 MiB preflight bound")
    return {"timeoutSeconds": args.timeout_seconds, "tempLimitBytes": args.temp_limit_bytes,
            "maxCases": args.max_cases, "maxInputBytes": BENCHMARK_INPUT_BYTES}


def benchmark_jdk(output, report, env):
    settings = setup_process(["java", "-XshowSettings:properties", "-version"], output, report, env)
    match = re.search(r"^\s*java\.home = (.+)$", settings, re.MULTILINE)
    if not match:
        raise ValueError("Cannot identify benchmark Java installation")
    jdk = Path(match[1]).resolve()
    java = "java.exe" if os.name == "nt" else "java"
    javac = "javac.exe" if os.name == "nt" else "javac"
    vm = "bin/server/jvm.dll" if os.name == "nt" else (
        "lib/server/libjvm.dylib" if platform.system() == "Darwin" else "lib/server/libjvm.so")
    report["jdkSha256"] = {name: sha256(jdk / name) for name in
                           ("release", f"bin/{java}", f"bin/{javac}", "lib/modules", vm)}
    return str(jdk / "bin" / java), str(jdk / "bin" / javac)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, help="verify and use this local release ZIP without downloading")
    parser.add_argument("--output", type=Path, help="new directory for reports (default: a fresh temp directory)")
    parser.add_argument("--case", action="append", help="run only this case ID (repeatable)")
    parser.add_argument("--benchmark", action="store_true", help="measure explicitly selected validator cases")
    parser.add_argument("--timeout-seconds", type=float, help="benchmark JVM timeout (default: 30)")
    parser.add_argument("--temp-limit-bytes", type=int, help="benchmark writable scratch limit (default: 64 MiB)")
    parser.add_argument("--max-cases", type=int, help="maximum cases per benchmark invocation (default: 8)")
    args = parser.parse_args()
    if not args.benchmark and any(value is not None for value in
                                  (args.timeout_seconds, args.temp_limit_bytes, args.max_cases)):
        parser.error("benchmark budget options require --benchmark")

    cases = read_json(CASES_FILE)
    ids = {case["id"] for case in cases}
    if not ids or len(ids) != len(cases):
        parser.error("case inventory must be nonempty with unique IDs")
    if args.case and set(args.case) - ids:
        parser.error(f"unknown case IDs: {', '.join(sorted(set(args.case) - ids))}")
    selected = [case for case in cases if not args.case or case["id"] in args.case]
    limits = None
    if args.benchmark:
        if args.timeout_seconds is None:
            args.timeout_seconds = 30
        if args.temp_limit_bytes is None:
            args.temp_limit_bytes = 64 * 1024**2
        if args.max_cases is None:
            args.max_cases = 8
        limits = benchmark_limits(parser, args, selected)
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
    if limits:
        report.update(mode="benchmark", budgets=limits, architecture=platform.machine(), measurementScope={
            "wallMillis": "JVM launch through exit observation, including model compilation and shutdown",
            "heap": "JVM used heap sampled from probe startup through shutdown; GC time is a JVM estimate",
            "sampledPeakTempBytes": "named logical files in isolated JVM cwd, home and tmpdir",
            "sampledPeakScratchBytes": "temp bytes plus output logs and heap.json; sampled, not a filesystem quota",
            "poolCloseSizes": "logical file sizes from recognized CLI trace records at pool close",
        })
    try:
        setup_temp = output / "setup-temp"
        setup_temp.mkdir()
        setup_env = ENV | {key: str(setup_temp) for key in ("TMPDIR", "TMP", "TEMP")}
        java, javac = benchmark_jdk(output, report, setup_env) if limits else ("java", "javac")
        java_version = setup_process([java, "-version"], output, report, setup_env)
        javac_version = setup_process([javac, "-version"], output, report, setup_env)
        if not re.search(r'version "25(?=[."+-])', java_version) or not re.match(r'javac 25(?:[.+-]|$)', javac_version):
            raise ValueError("Java 25 and javac 25 are required on PATH")
        entrypoint = legacy_jars(lock, archive_arg, output, report, setup_env)
        classes = output / "classes"
        classes.mkdir()
        setup_process([javac, "--release", "25", "-encoding", "UTF-8", "-cp", str(entrypoint),
                       "-d", str(classes), str(JAVA_SOURCE)], output, report, setup_env)
        classpath = os.pathsep.join(map(str, (classes, entrypoint)))
        for case in selected:
            result = run_case(case, output, classpath, lock["versions"], limits, java)
            report["cases"].append(result)
            print(f"{result['status']:5} {result['id']} (exit {result['exitCode']})")
            if result.get("stopReason") == "interrupted":
                break
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
