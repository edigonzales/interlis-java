#!/usr/bin/env python3
"""Exercise the installed CLI as a process and pin its observable contract."""
from __future__ import annotations

import hashlib
import json
import pathlib
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
CLI = ROOT / "interlis-cli" / "build" / "install" / "interlis-cli" / "bin" / "interlis-cli"
EVIDENCE = ROOT / "build" / "reports" / "sdk"
TESTDATA = ROOT / "testdata" / "models"


def run(arguments: list[str]) -> dict:
    completed = subprocess.run([str(CLI)] + arguments, cwd=ROOT,
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                               check=False, text=True)
    return {
        "arguments": arguments,
        "exitCode": completed.returncode,
        "stdout": completed.stdout,
        "stderr": completed.stderr,
        "stdoutSha256": hashlib.sha256(completed.stdout.encode()).hexdigest(),
    }


def main() -> int:
    if not CLI.exists():
        raise SystemExit(f"installed CLI missing: {CLI}")
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    results = []
    version = run(["version"])
    assert version["exitCode"] == 0, version
    for field in ("api:", "diagnostics:", "protocol:", "format:", "diagram:"):
        assert field in version["stdout"], (field, version["stdout"])
    results.append(version)

    version_json = run(["version", "--json"])
    assert version_json["exitCode"] == 0, version_json
    parsed_version = json.loads(version_json["stdout"])
    assert parsed_version["apiVersion"] == "0.2"
    assert parsed_version["protocol"] == "interlis.compiler/1.0"
    results.append(version_json)
    (EVIDENCE / "version-manifest.json").write_text(
        json.dumps(parsed_version, indent=2) + "\n")

    capabilities = run(["capabilities"])
    parsed_capabilities = json.loads(capabilities["stdout"])
    assert capabilities["exitCode"] == 0
    assert parsed_capabilities["protocol"] == "interlis.compiler/1.0"
    assert parsed_capabilities["apiVersion"] == "0.2"
    results.append(capabilities)

    positive = run(["check", str(TESTDATA / "base24.ili"), str(TESTDATA / "survey24.ili")])
    assert positive["exitCode"] == 0, positive
    results.append(positive)

    negative = run(["check", str(TESTDATA / "broken24.ili")])
    assert negative["exitCode"] == 1, negative
    assert "ILI-" in negative["stderr"], negative
    results.append(negative)

    with tempfile.TemporaryDirectory() as directory:
        target = pathlib.Path(directory) / "model.ili"
        target.write_text("INTERLIS 2.4;\nMODEL M AT \"x\" VERSION \"1\" =\n"
                          "TOPIC T =\nCLASS C =\nvalue : TEXT*10;\nEND C;\nEND T;\nEND M.\n")
        checked = run(["format", "--check", str(target)])
        assert checked["exitCode"] == 1, checked
        results.append(checked)
        formatted = run(["format", "--indent", "4", "--line-ending", "lf", str(target)])
        assert formatted["exitCode"] == 0, formatted
        results.append(formatted)
        written = run(["format", "--write", "--indent", "4", "--line-ending", "lf",
                       str(target)])
        assert written["exitCode"] == 0, written
        assert "    TOPIC T =" in target.read_text()
        results.append(written)

    unknown = run(["check", "--no-such-option", str(TESTDATA / "base24.ili")])
    assert unknown["exitCode"] == 2, unknown
    results.append(unknown)

    output = {
        "schemaVersion": "1.0",
        "status": "passed",
        "cli": str(CLI.relative_to(ROOT)),
        "commands": [{key: value for key, value in result.items()
                      if key not in ("stdout", "stderr")}
                     | {"stdoutLines": len(result["stdout"].splitlines()),
                        "stderrLines": len(result["stderr"].splitlines())}
                     for result in results],
        "note": "Process-level comparison; stdout/stderr are represented by hashes and line counts.",
    }
    (EVIDENCE / "sdk-process-comparison.json").write_text(json.dumps(output, indent=2) + "\n")
    print("SDK_PROCESS_PASS " + str(len(results)) + " commands")
    return 0


if __name__ == "__main__":
    sys.exit(main())
