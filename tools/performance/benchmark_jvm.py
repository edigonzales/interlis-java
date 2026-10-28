#!/usr/bin/env python3
"""Run the pinned geo corpus through the JVM CLI and write reproducible measurements."""
from __future__ import annotations

import json
import os
import pathlib
import platform
import resource
import statistics
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "docs" / "evidence" / "p3" / "geo-corpus.json"
OUT = ROOT / "build" / "reports" / "performance" / "performance-jvm.json"
CACHE = pathlib.Path(os.environ.get("CONFORMANCE_CACHE", str(pathlib.Path.home() / ".cache" / "interlis-java" / "geo-corpus")))
CLI = ROOT / "interlis-cli" / "build" / "install" / "interlis-cli" / "bin" / "interlis-cli"
ITERATIONS = 3


def run(command: list[str]) -> tuple[int, float, bytes, bytes, int]:
    started = time.perf_counter_ns()
    completed = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, check=False)
    elapsed = (time.perf_counter_ns() - started) / 1_000_000.0
    if completed.returncode != 0:
        raise SystemExit(f"command failed ({completed.returncode}): {' '.join(command)}\n"
                         + completed.stderr.decode(errors="replace"))
    memory = resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss
    return completed.returncode, elapsed, completed.stdout, completed.stderr, memory


def main() -> int:
    if not CLI.exists():
        raise SystemExit(f"CLI distribution not found: {CLI}")
    manifest = json.loads(MANIFEST.read_text())
    models = []
    for entry in manifest["models"]:
        path = CACHE / entry["file"].replace("/", "__")
        if not path.exists():
            raise SystemExit(f"corpus file missing: {path}")
        _, _, check_out, check_err, _ = run([str(CLI), "check", str(path)])
        _, _, format_out, format_err, _ = run([str(CLI), "format", str(path)])
        check_samples = []
        format_samples = []
        stable = True
        for _ in range(ITERATIONS):
            _, elapsed, out, err, memory = run([str(CLI), "check", str(path)])
            check_samples.append(elapsed)
            stable = stable and out == check_out and err == check_err
            _, elapsed, out, err, memory = run([str(CLI), "format", str(path)])
            format_samples.append(elapsed)
            stable = stable and out == format_out and err == format_err
        models.append({
            "model": entry["model"],
            "file": entry["file"],
            "language": entry["language"],
            "bytes": entry["bytes"],
            "sha256": entry["sha256"],
            "checkMs": check_samples,
            "checkMedianMs": statistics.median(check_samples),
            "formatMs": format_samples,
            "formatMedianMs": statistics.median(format_samples),
            "outputStableAcrossRuns": stable,
            "maxRssKb": memory,
        })
    result = {
        "schemaVersion": "1.0",
        "runtime": "jvm",
        "tool": str(CLI.relative_to(ROOT)),
        "iterationsPerModel": ITERATIONS,
        "warmupPerModel": 1,
        "corpus": str(MANIFEST.relative_to(ROOT)),
        "corpusIndexSha256": manifest["index"]["sha256"],
        "machine": {
            "platform": platform.platform(),
            "machine": platform.machine(),
            "processor": platform.processor(),
            "cpus": os.cpu_count(),
            "java": subprocess.run(["java", "-version"], stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, check=False).stdout.decode(
                                       errors="replace").splitlines()[:1],
        },
        "models": models,
        "notes": "Wall-clock subprocess measurements; warmup excluded. No production claim.",
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({"models": len(models), "out": str(OUT.relative_to(ROOT))}, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
