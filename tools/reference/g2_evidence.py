#!/usr/bin/env python3
"""Freeze and verify G2 review inputs; run the existing Gradle acceptance profile.

No baseline, expectation or source file is updated. Reports and archives belong
under build/. A successful report requires fresh outputs and unchanged inputs.
"""
from __future__ import annotations

import argparse
from collections import Counter
import datetime as dt
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import tarfile
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PROFILE = ROOT / "testdata/g2/a1-evidence-profile.json"
RUNTIME_ARTIFACTS = [
    "interlis-wasm-teavm/build/generated/teavm/js/interlis-compiler.js",
    "interlis-wasm-teavm/build/generated/teavm/wasm-gc/interlis-compiler.wasm",
    "interlis-wasm-teavm/build/generated/teavm/wasm-gc/interlis-compiler.wasm-runtime.js",
    "interlis-wasm-webimage/build/webimage/interlis-compiler.js",
    "interlis-wasm-webimage/build/webimage/interlis-compiler.js.wasm"]
REPORTS = {
    "reference": "build/conformance/report.json",
    "project": "build/reports/conformance/conformance-summary.json",
    "teaVmJavaScript": "interlis-wasm-teavm/build/js-contract-result.json",
    "teaVmWasmGC": "interlis-wasm-teavm/build/wasm-contract-result.json",
    "teaVmBrowsers": "interlis-wasm-teavm/build/browser-results.json",
    "safari": "interlis-wasm-teavm/build/safari-result.json",
    "webImageNode": "interlis-wasm-webimage/build/webimage/result.json",
    "webImageBrowsers": "interlis-wasm-webimage/build/webimage-browser/browser-results.json",
    "sdkProcesses": "build/reports/sdk/sdk-process-comparison.json",
}


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path: Path, data: object) -> None:
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(["git", "-C", str(root), *args]).decode()


def source_files(root: Path) -> list[dict]:
    names = sorted(set(filter(None, git(root, "ls-files", "--cached", "--others",
                                        "--exclude-standard", "-z").split("\0"))))
    result = []
    for name in names:
        path = root / name
        if path.is_symlink():
            raise ValueError(f"Review source must not follow a symlink: {name}")
        if path.is_file():
            result.append({"path": name, "sha256": sha(path), "bytes": path.stat().st_size,
                           "executable": bool(path.stat().st_mode & 0o111)})
    return result


def source_digest(files: list[dict]) -> str:
    return hashlib.sha256(json.dumps(files, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def snapshot(root: Path, out: Path) -> dict:
    files = source_files(root)
    out.mkdir(parents=True, exist_ok=False)
    with tarfile.open(out / "source.tar.gz", "w:gz") as archive:
        for item in files:
            archive.add(root / item["path"], arcname=item["path"], recursive=False)
    if source_files(root) != files:
        raise ValueError("Source changed while creating the review snapshot")
    manifest = {"schemaVersion": "1.0", "createdAt": dt.datetime.now(dt.timezone.utc).isoformat(),
                "baseCommit": git(root, "rev-parse", "HEAD").strip(),
                "gitStatus": git(root, "status", "--porcelain=v1"),
                "sourceDigest": source_digest(files), "files": files,
                "archiveSha256": sha(out / "source.tar.gz")}
    norms = root / "docs/evidence/stabilization/g2-rule-matrix.json"
    manifest["norms"] = {}
    if norms.is_file():
        for version, document in json.loads(norms.read_text())["documents"].items():
            pdf = root / "build/norms" / ("ili" + version.replace(".", "") + ".pdf")
            if not pdf.is_file() or sha(pdf) != document["sha256"]:
                raise ValueError(f"Missing or changed pinned norm: {version}")
            target = out / pdf.name
            target.write_bytes(pdf.read_bytes())
            manifest["norms"][version] = {"file": target.name, "sha256": sha(target),
                                            "url": document["url"]}
    write(out / "manifest.json", manifest)
    verify(out)
    return manifest


def verify(directory: Path, root: Path | None = None) -> dict:
    manifest = json.loads((directory / "manifest.json").read_text())
    if source_digest(manifest["files"]) != manifest["sourceDigest"]:
        raise ValueError("Changed source manifest")
    if sha(directory / "source.tar.gz") != manifest["archiveSha256"]:
        raise ValueError("Changed source archive")
    expected = {item["path"]: item for item in manifest["files"]}
    with tarfile.open(directory / "source.tar.gz", "r:gz") as archive:
        seen = set()
        for member in archive:
            name = PurePosixPath(member.name)
            if name.is_absolute() or ".." in name.parts or not member.isfile() or member.name in seen:
                raise ValueError(f"Unsafe or duplicate archive member: {member.name}")
            seen.add(member.name)
            data = archive.extractfile(member).read()
            item = expected.get(member.name)
            if item is None or hashlib.sha256(data).hexdigest() != item["sha256"] or len(data) != item["bytes"]:
                raise ValueError(f"Changed archive source: {member.name}")
            if bool(member.mode & 0o111) != item["executable"]:
                raise ValueError(f"Changed source executable mode: {member.name}")
        if seen != expected.keys():
            raise ValueError("Incomplete source archive")
    for norm in manifest.get("norms", {}).values():
        if sha(directory / norm["file"]) != norm["sha256"]:
            raise ValueError("Changed norm input")
    if root is not None and source_files(root) != manifest["files"]:
        raise ValueError("Working sources differ from the frozen review input")
    return manifest


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def jvm_summary(root: Path, expected: dict, started_ns: int) -> dict:
    files = sorted(root.glob("interlis-*/build/test-results/test/TEST-*.xml"))
    require(bool(files), "Missing JVM test reports")
    require({str(path.relative_to(root)) for path in files} == set(expected), "Unexpected or missing JVM suites")
    counts = dict(tests=0, errors=0, failures=0, skipped=0)
    for path in files:
        require(path.stat().st_mtime_ns >= started_ns, "Stale JVM test report: " + str(path))
        report = ET.parse(path).getroot()
        cases = report.findall("testcase")
        require(len(cases) == int(report.get("tests", 0)) == expected[str(path.relative_to(root))],
                "Incomplete JVM testcase details: " + str(path))
        for kind in ["failure", "error", "skipped"]:
            require(not any(case.find(kind) is not None for case in cases), "Unsuccessful JVM testcase")
        for key in counts:
            counts[key] += int(report.get(key, 0))
    if not counts["tests"] or any(counts[k] for k in ["errors", "failures", "skipped"]):
        raise ValueError(f"Incomplete or failed JVM acceptance: {counts}")
    return counts


def report_result(key: str, data: object, profile: dict | None = None) -> None:
    profile = profile or json.loads(PROFILE.read_text())
    try:
        _report_result(key, data, profile)
    except (KeyError, TypeError, AttributeError) as error:
        raise ValueError(f"Incomplete report: {key}") from error


def _report_result(key: str, data: object, profile: dict) -> None:
    if key == "reference":
        require(data["schemaVersion"] == "1.0" and len(data["compilers"]) == 1, "Incomplete reference profile")
        compiler = data["compilers"][0]
        tests = compiler["tests"]
        require(compiler["id"] == "interlis-java" and len(tests) == profile["conformance"]["selectedCases"],
                "Incomplete reference cases")
        ids = sorted(test["testId"] for test in tests)
        require(len(set(ids)) == len(ids) and hashlib.sha256(json.dumps(ids, separators=(",", ":")).encode()).hexdigest()
                == profile["conformance"]["caseIdsSha256"], "Changed reference case selection")
        require(all(t["languageVersion"] in profile["conformance"]["languages"] and not t["timedOut"]
                    and t["exitCode"] in [0, 1] and t["command"] and t["actualOutcome"] in ["accepted", "rejected"] for t in tests),
                "Invalid reference process outcome")
        summary = dict.fromkeys(["PASS", "XFAIL", "FAIL", "XPASS", "ERROR"], 0)
        summary.update(Counter(test["classification"] for test in tests))
        summary["TOTAL"] = len(tests)
        require(summary == data["summary"] == compiler["summary"], "Reference summary disagrees with details")
        require(not summary["ERROR"] and not summary["XPASS"], "Unresolved reference process result")
        return
    if key == "project":
        summary = data["summary"]
        require(data["overallResult"] == "PASS" and not any(summary[n] for n in ["FAIL", "ERROR", "XPASS"]),
                "Unresolved project conformance outcomes")
        require(summary["TOTAL"] == profile["conformance"]["selectedCases"]
                and sum(summary[n] for n in ["PASS", "XFAIL", "FAIL", "XPASS", "ERROR"]) == summary["TOTAL"],
                "Incomplete project conformance cases")
        selection = data["selection"]
        require(selection["languageVersions"] == profile["conformance"]["languages"]
                and selection["suiteCaseCount"] == profile["conformance"]["suiteCases"]
                and selection["selectedCaseCount"] == summary["TOTAL"]
                and selection["excludedCaseCount"] == profile["conformance"]["excludedCases"]
                and data["suite"]["commit"] == profile["conformance"]["suiteCommit"], "Changed conformance selection")
        assessments = data["normativeAssessments"]
        require(all(a["result"] == "PASS" and a["referenceClassification"] == "FAIL"
                    and a["ownExpectation"] in ["accepted", "rejected"] and a["actualOutcome"] == a["ownExpectation"]
                    and a["original"]["command"] and a["positiveControl"]["command"]
                    and a["original"]["exitCode"] == (0 if a["ownExpectation"] == "accepted" else 1)
                    and a["positiveControl"]["exitCode"] == 0 for a in assessments), "Incomplete normative assessment")
    elif key in ["teaVmBrowsers", "webImageBrowsers"]:
        if (len(data) != 3 or {row["browser"] for row in data} != {"chromium", "firefox", "webkit"}
                or any(row["status"] != "passed" or row["result"]["status"] != "ok" for row in data)):
            raise ValueError(f"Incomplete browser result: {key}")
        for row in data:
            require(row["browserVersion"] and row["playwright"] and row["node"] == profile["nodeVersion"], "Missing browser tool version")
            portable_contract(row["result"], profile, key == "teaVmBrowsers")
    elif key == "safari":
        if data["status"] != "passed" or data["browser"] != "Safari" or data["result"]["status"] != "ok":
            raise ValueError("Safari did not pass")
        require(data["driverVersion"] and data["node"] == profile["nodeVersion"], "Missing Safari tool version")
        portable_contract(data["result"], profile, True)
    else:
        require(data["status"] in ["ok", "passed"], f"Failed runtime: {key}")
        if key == "sdkProcesses":
            sdk_contract(data)
        else:
            expected_runtime = {"teaVmJavaScript": "teavm-js", "teaVmWasmGC": "teavm-wasm-gc",
                                "webImageNode": "graalvm-web-image"}[key]
            require(data["runtime"] == expected_runtime and data["node"] == profile["nodeVersion"], "Missing runtime version")
            portable_contract(data, profile, False)


def portable_contract(data: dict, profile: dict, browser: bool) -> None:
    require(type(data["stabilizationCases"]) is int and data["stabilizationCases"] == profile["portableCases"],
            "Incomplete portable cases")
    require(data.get("capabilities", data.get("tool")) == profile["projectVersion"], "Changed compiler version")
    if browser:
        require(data["analyzeRevision"] == "1" and data["compiledOutcome"] == "succeeded"
                and data["rejectedOutcome"] == "rejected" and data["workerRestartOutcome"] == "succeeded"
                and data["exactDecimal"] is True and data["diagnostics"] == 0
                and data["diagramNodes"] == 2 and data["diagramEdges"] == 0 and data["diagramAssociations"] == 0,
                "Incomplete browser contracts")
    else:
        require(data["outcome"] == "succeeded", "Incomplete portable outcome")


def sdk_contract(data: dict) -> None:
    expected = [(["version"], 0), (["version", "--json"], 0), (["capabilities"], 0),
                (["check", "{ROOT}/testdata/models/base24.ili", "{ROOT}/testdata/models/survey24.ili"], 0),
                (["check", "{ROOT}/testdata/models/broken24.ili"], 1),
                (["format", "--check", "{TEMP_MODEL}"], 1),
                (["format", "--indent", "4", "--line-ending", "lf", "{TEMP_MODEL}"], 0),
                (["format", "--write", "--indent", "4", "--line-ending", "lf", "{TEMP_MODEL}"], 0),
                (["check", "--no-such-option", "{ROOT}/testdata/models/base24.ili"], 2)]
    commands = data["commands"]
    require(data["schemaVersion"] == "1.0" and len(commands) == len(expected), "Incomplete SDK commands")
    require(data["cli"] == "interlis-cli/build/install/interlis-cli/bin/interlis-cli", "Changed SDK entrypoint")
    suffix = "/testdata/models/base24.ili"
    root_argument = commands[3]["arguments"][1]
    require(root_argument.endswith(suffix), "Missing SDK source root")
    reported_root = root_argument[:-len(suffix)]
    require(Path(reported_root).is_absolute(), "SDK source root must be absolute")
    temporary = None
    for row, (arguments, exit_code) in zip(commands, expected):
        actual = []
        for argument in row["arguments"]:
            if argument.startswith(reported_root + "/"):
                actual.append("{ROOT}/" + argument[len(reported_root) + 1:])
            elif Path(argument).is_absolute() and Path(argument).name == "model.ili":
                require(temporary is None or temporary == argument, "Different SDK temporary model")
                temporary = argument
                actual.append("{TEMP_MODEL}")
            else:
                actual.append(argument)
        require(actual == arguments and row["exitCode"] == exit_code, "Changed SDK command/exit profile")
        require(len(row["stdoutSha256"]) == 64 and row["stdoutLines"] >= 0 and row["stderrLines"] >= 0,
                "Incomplete SDK stream evidence")


def conformance_pair(raw: dict, project: dict) -> None:
    require(project["referenceSummary"] == raw["summary"] and project["referenceResult"] == raw["overallResult"],
            "Raw/project reference summaries disagree")
    failed = {t["testId"] for t in raw["compilers"][0]["tests"] if t["classification"] == "FAIL"}
    assessments = project["normativeAssessments"]
    require(len(assessments) == len(failed) and {a["testId"] for a in assessments} == failed,
            "Missing or duplicate normative assessments")
    summary = dict(raw["summary"])
    summary["FAIL"] -= len(assessments)
    summary["XFAIL"] += len(assessments)
    require(summary == project["summary"], "Unexplained raw/project classification change")


def run(args: argparse.Namespace) -> int:
    frozen = verify(args.snapshot, ROOT)
    profile_name = str(args.profile.resolve().relative_to(ROOT))
    expected_profile = next((item for item in frozen["files"] if item["path"] == profile_name), None)
    require(expected_profile is not None and expected_profile["sha256"] == sha(args.profile),
            "Acceptance profile must be part of the frozen source input")
    profile = json.loads(args.profile.read_text())
    require(sum(profile["jvmSuites"].values()) == profile["jvmTests"], "Inconsistent JVM profile")
    require(profile["jvmTests"] > 0 and profile["portableCases"] > 0
            and profile["conformance"]["selectedCases"] > 0
            and profile["conformance"]["selectedCases"] + profile["conformance"]["excludedCases"]
            == profile["conformance"]["suiteCases"], "Empty or inconsistent acceptance profile")
    args.out.mkdir(parents=True, exist_ok=False)
    command = [str(ROOT / "gradlew"), "check", ":interlis-cli:installDist", "checkPortable", "checkBrowser",
               ":interlis-wasm-webimage:webImageProbe", "conformanceCheck", "sdkProcessCheck",
               "-PnodeExecutable=" + str(args.node), "-PgraalVmHome=" + str(args.graal),
               "-PbinaryenHome=" + str(args.binaryen), "--continue", "--rerun-tasks", "--console=plain"]
    env = dict(os.environ, JAVA_HOME=str(args.java_home))
    # Reports from an older run cannot certify this run, even after a process failure.
    for path in REPORTS.values():
        (ROOT / path).unlink(missing_ok=True)
    for path in ROOT.glob("interlis-*/build/test-results/test/TEST-*.xml"):
        path.unlink()
    for path in [*(ROOT / name for name in RUNTIME_ARTIFACTS),
                 *(ROOT / "interlis-cli/build/install/interlis-cli/lib").glob("*.jar")]:
        path.unlink(missing_ok=True)
    started_ns = time.time_ns()
    started = dt.datetime.now(dt.timezone.utc).isoformat()
    with (args.out / "gradle.log").open("w") as log:
        result = subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
    report = {"schemaVersion": "1.0", "status": "FAILED", "startedAt": started,
              "startedNs": started_ns,
              "finishedAt": dt.datetime.now(dt.timezone.utc).isoformat(), "command": command,
              "exitCode": result.returncode, "inputSourceDigest": frozen["sourceDigest"],
              "inputManifestSha256": sha(args.snapshot / "manifest.json"),
              "profile": profile_name, "profileSha256": sha(args.profile), "reports": {}, "jvmReports": {}}
    try:
        verify(args.snapshot, ROOT)
        if result.returncode != 0:
            raise ValueError(f"Gradle process exited with {result.returncode}")
        report["jvm"] = jvm_summary(ROOT, profile["jvmSuites"], started_ns)
        for name in profile["jvmSuites"]:
            original = ROOT / name
            target = args.out / "jvm" / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(original.read_bytes())
            report["jvmReports"][name] = {"file": str(target.relative_to(args.out)), "sha256": sha(target),
                                          "originalMtimeNs": original.stat().st_mtime_ns}
        results = {}
        for key, name in REPORTS.items():
            path = ROOT / name
            data = json.loads(path.read_text())
            report_result(key, data, profile)
            results[key] = data
            target = args.out / (key + ".json")
            target.write_bytes(path.read_bytes())
            report["reports"][key] = {"file": target.name, "sha256": sha(target)}
        conformance_pair(results["reference"], results["project"])
        jars = sorted((ROOT / "interlis-cli/build/install/interlis-cli/lib").glob("*.jar"))
        if not jars:
            raise ValueError("Missing SDK artifacts")
        report["artifacts"] = {name: sha(ROOT / name) for name in RUNTIME_ARTIFACTS}
        report["artifacts"].update({str(path.relative_to(ROOT)): sha(path) for path in jars})
        report["toolchains"] = {}
        for key, invocation in {
            "java": [str(args.java_home / "bin/java"), "-version"],
            "gradle": [str(ROOT / "gradlew"), "--version"],
            "node": [str(args.node), "--version"],
            "graalVm": [str(args.graal / "bin/native-image"), "--version"],
            "binaryen": [str(args.binaryen / "bin/wasm-opt"), "--version"],
        }.items():
            version = subprocess.run(invocation, cwd=ROOT, env=env, text=True, capture_output=True, timeout=60)
            if version.returncode != 0:
                raise ValueError(f"Cannot record toolchain version: {key}")
            report["toolchains"][key] = {"command": invocation, "exitCode": version.returncode,
                                         "stdout": version.stdout, "stderr": version.stderr}
        report["pins"] = {name: sha(ROOT / name) for name in [
            "docs/evidence/conformance/baseline.json", "docs/evidence/conformance/normative-expectations.json",
            "gradle/wrapper/gradle-wrapper.properties", "gradle/libs.versions.toml", "gradle.properties"]}
        report["status"] = "PASSED"
    except (ValueError, OSError, KeyError, TypeError, ET.ParseError) as error:
        report["failure"] = str(error)
    report["logSha256"] = sha(args.out / "gradle.log")
    write(args.out / "manifest.json", report)
    print(json.dumps({"status": report["status"], "exitCode": result.returncode,
                      "report": str(args.out / "manifest.json"), "failure": report.get("failure")}))
    return 0 if report["status"] == "PASSED" else 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    make = sub.add_parser("snapshot")
    make.add_argument("--out", type=Path, required=True)
    check = sub.add_parser("verify")
    check.add_argument("--snapshot", type=Path, required=True)
    check.add_argument("--sources", action="store_true")
    execute = sub.add_parser("run")
    execute.add_argument("--snapshot", type=Path, required=True)
    execute.add_argument("--out", type=Path, required=True)
    execute.add_argument("--profile", type=Path, default=PROFILE)
    for name in ["java-home", "node", "graal", "binaryen"]:
        execute.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    if args.action == "snapshot":
        print(json.dumps({"sourceDigest": snapshot(ROOT, args.out)["sourceDigest"]}))
    elif args.action == "verify":
        print(json.dumps({"sourceDigest": verify(args.snapshot, ROOT if args.sources else None)["sourceDigest"]}))
    else:
        return run(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
