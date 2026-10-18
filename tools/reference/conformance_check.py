#!/usr/bin/env python3
"""Pinned INTERLIS compiler conformance run against the public CLI.

The suite and runner are external test evidence, not production dependencies.
Only INTERLIS 2.3/2.4 cases are selected; INTERLIS 1.0 is excluded explicitly.
"""
from __future__ import annotations

import hashlib
import json
import os
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
OUT = ROOT / "docs" / "evidence" / "conformance"
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


def main() -> int:
    suite = prepare()
    cli = ROOT / "interlis-cli" / "build" / "install" / "interlis-cli" / "bin" / "interlis-cli"
    if not cli.exists():
        raise SystemExit(f"CLI distribution not found: {cli}; run :interlis-cli:installDist")
    output = ROOT / "build" / "conformance"
    output.mkdir(parents=True, exist_ok=True)
    baseline = OUT / "baseline.json"
    baseline.write_text(json.dumps({
        "schemaVersion": "1.0",
        "compiler": "interlis-java",
        "knownFailures": []
    }, indent=2) + "\n")
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
            "internalErrorPatterns": ["internal failure:", "Exception in thread"]
        }],
        "baseline": str(baseline)
    }
    config_path = output / "run-config.json"
    config_path.write_text(json.dumps(config, indent=2) + "\n")
    log = output / "conformance.log"
    command = ["java", str(suite / "InterlisConformance.java"), "run", "--config", str(config_path)]
    with log.open("w", encoding="utf-8") as handle:
        completed = subprocess.run(command, cwd=ROOT, stdout=handle, stderr=subprocess.STDOUT,
                                   timeout=1800, check=False)
    summary_path = output / "summary.json"
    if not summary_path.exists():
        raise SystemExit(f"conformance runner produced no summary; see {log}")
    summary = json.loads(summary_path.read_text())
    all_cases = json.loads((suite / "suite" / "test-cases.json").read_text())["testCases"]
    selected = [case for case in all_cases if case["languageVersion"] in ("2.3", "2.4")]
    excluded = [case for case in all_cases if case["languageVersion"] not in ("2.3", "2.4")]
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
