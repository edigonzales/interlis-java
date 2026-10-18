#!/usr/bin/env python3
"""Pinned ili2c reference check for the small P1 fixture profile.

This is an evidence tool, not part of the Gradle production build. It downloads
the pinned ili2c distribution, verifies its SHA-256, and runs explicitly listed
cases. Status values are PASS/FAIL; no timing data is interpreted as a model
result.
"""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "docs" / "evidence" / "reference"
URL = "https://downloads.interlis.ch/ili2c/ili2c-5.6.9.zip"
SHA256 = "0d6fc5dc739a490ef6b027072d58281bb0731c932f03d95662f83d20dc07c8ee"
VERSION = "5.6.9"
CACHE = Path(os.environ.get("ILI2C_CACHE", str(Path.home() / ".cache" / "interlis-java")))

CASES = [
    {
        "id": "base-survey-24",
        "phase": "P1",
        "files": ["testdata/models/base24.ili", "testdata/models/survey24.ili"],
        "extraArgs": [],
        "expectedExit": 0,
        "expectation": "Der gepinnte ili2c akzeptiert die importierte 2.4-Modellmenge mit Vererbung, Override und EXTERNAL-Rolle.",
    },
    {
        "id": "legacy-23",
        "phase": "P1",
        "files": ["testdata/models/legacy23.ili"],
        "extraArgs": [],
        "expectedExit": 0,
        "expectation": "Der gepinnte ili2c akzeptiert das ausgewählte 2.3-Modell.",
    },
    {
        "id": "broken-24",
        "phase": "P1",
        "files": ["testdata/models/broken24.ili"],
        "extraArgs": [],
        "expectedExit": 1,
        "expectation": "Der gepinnte ili2c lehnt die fehlende Attributtyp-Deklaration ab.",
    },
    {
        "id": "trailing-24",
        "phase": "P1",
        "files": ["testdata/models/trailing24.ili"],
        "extraArgs": [],
        "expectedExit": 1,
        "expectation": "Der gepinnte ili2c lehnt nachlaufenden Text nach dem Modell ab.",
    },
    {
        "id": "missing-import",
        "phase": "P1",
        "files": ["testdata/models/survey24.ili"],
        "extraArgs": ["--no-auto"],
        "expectedExit": 1,
        "expectation": "Der gepinnte ili2c meldet den fehlenden Import ohne automatische Suche.",
    },
]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def prepare() -> Path:
    CACHE.mkdir(parents=True, exist_ok=True)
    archive = CACHE / f"ili2c-{VERSION}.zip"
    if not archive.exists():
        subprocess.run(["curl", "-fsSL", "--max-time", "180", "-o", str(archive), URL],
                       check=True)
    actual = sha256(archive)
    if actual != SHA256:
        raise SystemExit(f"reference hash mismatch: {actual} != {SHA256}")
    jar = CACHE / "ili2c.jar"
    if not jar.exists():
        with zipfile.ZipFile(archive) as zf:
            with zf.open("ili2c.jar") as source, jar.open("wb") as target:
                shutil.copyfileobj(source, target)
    return jar


def main() -> int:
    jar = prepare()
    results = []
    for case in CASES:
        command = ["java", "-jar", str(jar), *case["extraArgs"], *case["files"]]
        completed = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
        passed = completed.returncode == case["expectedExit"]
        def sanitize(text: str) -> str:
            return (text.replace(str(ROOT), "<repo>").replace(str(CACHE), "<cache>"))[-4000:]
        results.append({
            **case,
            "command": "java -jar <cache>/ili2c.jar " + " ".join(
                [*case["extraArgs"], *case["files"]]),
            "actualExit": completed.returncode,
            "status": "PASS" if passed else "FAIL",
            "stdout": sanitize(completed.stdout),
            "stderr": sanitize(completed.stderr),
            "fixtureSha256": {file: sha256(ROOT / file) for file in case["files"]},
        })
    report = {
        "tool": "ili2c",
        "version": VERSION,
        "source": URL,
        "sourceSha256": SHA256,
        "java": subprocess.run(["java", "-version"], capture_output=True, text=True,
                               check=False).stderr.splitlines()[:3],
        "date": "2026-10-18",
        "scope": "Small pinned 2.3/2.4 P1 fixture profile, not the historical 610-case suite.",
        "results": results,
    }
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    (EVIDENCE / "reference-results.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    for result in results:
        print(f"{result['id']}: {result['status']} (exit {result['actualExit']}, "
              f"expected {result['expectedExit']})")
    return 0 if all(r["status"] == "PASS" for r in results) else 1


if __name__ == "__main__":
    sys.exit(main())
