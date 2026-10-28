#!/usr/bin/env python3
"""Isolate WasmGC shutdown; a timed-out child is a failure even after positive assertions."""
import argparse
import datetime
import hashlib
import json
import platform
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--node", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--timeout", type=float, default=15)
    parser.add_argument("--semantics", action="store_true",
                        help="Also run the current shared semantic cases (requires a current build)")
    args = parser.parse_args()
    if args.timeout <= 0:
        parser.error("--timeout must be positive")
    args.output.mkdir(parents=True, exist_ok=True)
    wasm = ROOT / "interlis-wasm-teavm/build/generated/teavm/wasm-gc/interlis-compiler.wasm"
    script = ROOT / "interlis-wasm-teavm/wasm-exit-probe.cjs"
    modes = ["compile", "load", "capabilities"] + (["semantics"] if args.semantics else [])
    results = []
    for profile, flags in [("standard", []), ("liftoff-only-diagnostic", ["--liftoff-only"])]:
        for mode in modes:
            command = [args.node, "--experimental-wasm-exnref", *flags, str(script), mode]
            log = args.output / f"{profile}-{mode}.log"
            started = time.monotonic()
            timed_out = False
            with log.open("w") as stream:
                child = subprocess.Popen(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT)
                try:
                    child.wait(timeout=args.timeout)
                except subprocess.TimeoutExpired:
                    timed_out = True
                    if platform.system() == "Darwin" and mode == "semantics":
                        sample = args.output / f"{profile}-{mode}.sample.txt"
                        subprocess.run(["/usr/bin/sample", str(child.pid), "2", "-file", str(sample)],
                                       stdout=stream, stderr=subprocess.STDOUT, timeout=10, check=False)
                    child.terminate()
                    try:
                        child.wait(timeout=3)
                    except subprocess.TimeoutExpired:
                        child.kill()
                        child.wait(timeout=3)
            text = log.read_text()
            result = {"profile": profile, "mode": mode, "command": command,
                      "status": "TIMEOUT" if timed_out else "PASS" if child.returncode == 0 else "FAIL",
                      "exitCode": child.returncode, "elapsedSeconds": time.monotonic() - started,
                      "deadlineSeconds": args.timeout, "operationCompleted": '"operation-complete"' in text,
                      "beforeExitObserved": '"beforeExit"' in text, "exitObserved": '"exit"' in text,
                      "log": str(log), "logSha256": hashlib.sha256(log.read_bytes()).hexdigest()}
            results.append(result)
            print(f"{profile}/{mode}: {result['status']}", flush=True)
    report = {"recordedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "scope": "Diagnostic only; liftoff-only is not a production or performance acceptance",
              "node": subprocess.check_output([args.node, "--version"], text=True).strip(),
              "host": platform.platform(), "wasmSha256": hashlib.sha256(wasm.read_bytes()).hexdigest(),
              "results": results}
    (args.output / "result.json").write_text(json.dumps(report, indent=2) + "\n")
    return 1 if any(r["status"] != "PASS" for r in results) else 0


if __name__ == "__main__":
    raise SystemExit(main())
