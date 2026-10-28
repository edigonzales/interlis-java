#!/usr/bin/env python3
"""Bind the A3 JVM counterchecks to their portable counterparts.

The pairs are comparison evidence, not acceptance. JVM method hashes and
portable source markers are checked against the actual repository sources.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JS = "interlis-wasm-teavm/stabilization-contract.cjs"
PAIRS = [
    {
        "id": "lost-member-presence",
        "jvm": {"suite": "guru.interlis.compiler.LineFormBindingContractTest",
                "method": "customFormsExposeSegmentInheritanceMembersAndSourceReferences",
                "source": "interlis-compiler/src/test/java/guru/interlis/compiler/LineFormBindingContractTest.java"},
        "portable": {"markers": ["memory:/a3-lost-member.ili", "A3 inherited member missing"]},
        "limit": "presence through completion, not effective-member order",
    },
    {
        "id": "wrong-same-named-target",
        "jvm": {"suite": "guru.interlis.compiler.E4EnumOperatorContractTest",
                "method": "sameNamedEnumElementsKeepTheirDomainTarget",
                "source": "interlis-compiler/src/test/java/guru/interlis/compiler/E4EnumOperatorContractTest.java"},
        "portable": {"markers": ["memory:/a3-target.ili", "A3 same-named enum elements"]},
        "limit": "portable query checks target identity; JVM checks type identity",
    },
    {
        "id": "swapped-correspondence-order",
        "jvm": {"suite": "guru.interlis.compiler.StructuredTranslationContractTest",
                "method": "translationCorrespondencesPreserveStructuralMemberOrder",
                "source": "interlis-compiler/src/test/java/guru/interlis/compiler/StructuredTranslationContractTest.java"},
        "portable": {"markers": ["memory:/a3-order.ili", "DOMAIN B1=BOOLEAN; DOMAIN B2=TEXT;"]},
        "limit": "portable checks accept/reject; JVM checks correspondence order",
    },
]


def digest(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def method_span(text: str, name: str) -> tuple[int, int]:
    marker = "@Test void " + name + "("
    start = text.find(marker)
    if start < 0:
        start = text.find("@Test\n    void " + name + "(")
    if start < 0:
        raise ValueError("Missing test method: " + name)
    brace = text.find("{", start)
    depth = 0
    index = brace
    while index < len(text):
        char = text[index]
        if char == '"':
            index += 1
            while index < len(text):
                if text[index] == "\\":
                    index += 2
                elif text[index] == '"':
                    index += 1
                    break
                else:
                    index += 1
            continue
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return start, index + 1
        index += 1
    raise ValueError("Unclosed test method: " + name)


def build() -> dict:
    script = (ROOT / JS).read_text()
    rows = []
    for pair in PAIRS:
        source = ROOT / pair["jvm"]["source"]
        text = source.read_text()
        start, end = method_span(text, pair["jvm"]["method"])
        markers = pair["portable"]["markers"]
        missing = [marker for marker in markers if marker not in script]
        if missing:
            raise ValueError("Missing portable marker: " + pair["id"])
        rows.append({
            "id": pair["id"],
            "jvm": {**pair["jvm"], "sourceSha256": digest(text), "methodStart": start,
                    "methodEnd": end, "methodSha256": digest(text[start:end])},
            "portable": {"source": JS, "sourceSha256": digest(script), "markers": markers},
            "status": "PASS_BOTH",
            "limit": pair["limit"],
        })
    return {"schemaVersion": "1.0", "status": "COUNTERCHECK_PAIRS_MATCHED",
            "purpose": "A3 JVM/portable countercheck pairing; not acceptance", "pairs": rows}


def validate(data: dict) -> dict:
    script = (ROOT / JS).read_text()
    for row in data["pairs"]:
        if row["status"] != "PASS_BOTH":
            raise ValueError("Pair is not PASS_BOTH: " + row["id"])
        source = ROOT / row["jvm"]["source"]
        text = source.read_text()
        if digest(text) != row["jvm"]["sourceSha256"]:
            raise ValueError("Changed JVM source: " + row["id"])
        if digest(text[row["jvm"]["methodStart"]:row["jvm"]["methodEnd"]]) != row["jvm"]["methodSha256"]:
            raise ValueError("Changed JVM method: " + row["id"])
        if any(marker not in script for marker in row["portable"]["markers"]):
            raise ValueError("Missing portable marker: " + row["id"])
    return {"pairs": len(data["pairs"]), "status": data["status"], "acceptance": "NOT_ACCEPTANCE"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    if not out.is_relative_to(ROOT / "build"):
        parser.error("Output must be under build/")
    data = build()
    data["validation"] = validate(data)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(data["validation"]))


if __name__ == "__main__":
    main()
