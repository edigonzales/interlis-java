#!/usr/bin/env python3
"""Pin a small real INTERLIS corpus from the official geo.so.ch repository."""
from __future__ import annotations

import hashlib
import json
import os
import pathlib
import sys
import urllib.request
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "evidence" / "p3" / "geo-corpus.json"
BASE = "https://geo.so.ch/models/"
INDEX_URL = BASE + "ilimodels.xml"
INDEX_SHA256 = "ff9561b4e248da29c0bde4f65e77c1e22e00e45a4f7c8538756debd11451d93c"
CACHE = pathlib.Path(os.environ.get("CONFORMANCE_CACHE", str(pathlib.Path.home() / ".cache" / "interlis-java" / "geo-corpus")))
SELECTED = [
    ("AGI/SO_FunctionsExt.ili", "2.3"),
    ("AGI/SO_AGI_GWR_20241118.ili", "2.3"),
    ("AGI/SO_AGI_STAC_20230426.ili", "2.3"),
    ("AGI/contrib/IliValidGwr_V1_0.ili", "2.4"),
]


def sha256(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fetch(url: str, target: pathlib.Path) -> None:
    if target.exists():
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url, timeout=60) as response:
        target.write_bytes(response.read())


def main() -> int:
    index = CACHE / "ilimodels.xml"
    fetch(INDEX_URL, index)
    actual = sha256(index)
    if actual != INDEX_SHA256:
        raise SystemExit(f"index hash mismatch: {actual} != {INDEX_SHA256}")
    entries = {}
    root = ET.parse(index).getroot()
    for element in root.iter():
        local = element.tag.rsplit("}", 1)[-1]
        if not local.endswith("ModelMetadata"):
            continue
        data = {}
        for child in element:
            name = child.tag.rsplit("}", 1)[-1]
            if name in ("Name", "File", "Version", "SchemaLanguage"):
                data[name] = child.text
        if data.get("File"):
            entries[data["File"]] = data
    models = []
    for file, version in SELECTED:
        metadata = entries.get(file)
        if metadata is None:
            raise SystemExit(f"model not present in pinned index: {file}")
        target = CACHE / file.replace("/", "__")
        fetch(BASE + file, target)
        models.append({
            "file": file,
            "model": metadata["Name"],
            "version": metadata["Version"],
            "language": version,
            "url": BASE + file,
            "sha256": sha256(target),
            "bytes": target.stat().st_size,
        })
    manifest = {
        "schemaVersion": "1.0",
        "repository": BASE,
        "index": {"url": INDEX_URL, "sha256": INDEX_SHA256},
        "models": models,
        "selection": "four standalone, compilable 2.3/2.4 models",
        "note": "Model bytes are cached outside the repository; the manifest pins them.",
    }
    OUT.write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps({"models": len(models), "indexSha256": actual}, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
