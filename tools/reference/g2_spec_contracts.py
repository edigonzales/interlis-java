#!/usr/bin/env python3
"""A2-R4 worklist for numbered specification requirements and explicit
unnumbered normative lines. The inventory records ownership and gaps; it does
not claim implementation, test coverage or acceptance.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CHAPTERS = ("04", "05", "06", "07", "08", "09", "10", "13", "16")
KEYWORD = re.compile(r"\b(MUSS|DARF NICHT|SOLL|MUSS NICHT|SOLL NICHT|MUST NOT|MUST|SHALL)\b")
STEPS = {"B1", "B2", "B3", "C1", "C2", "C3", "C4", "D1", "D2", "D3",
         "E1", "E2", "E3", "F1", "F2", "F3", "G1", "G2", "G3", "H1", "H2", "H3"}
PREFIX_STEP = {
    "SYN": "B3", "META": "B2", "SEM": "B2", "RES": "H1", "SRC": "H1", "INC": "H1",
    "DIAG": "F3", "LOG": "F3", "API": "B2", "FMT": "B3",
}
G2_PREFIXES = set(PREFIX_STEP)
OUTSIDE_REASON = {
    "AGENT": "Agent process rule outside G2 compiler acceptance.",
    "BUILD": "Build/toolchain rule outside G2 language acceptance.",
    "ARCH": "Cross-cutting architecture rule outside a single G2 step.",
    "GOAL": "Product goal outside G2 compiler acceptance.",
    "NFR": "Cross-cutting quality/performance rule outside G2 language acceptance.",
    "TEST": "Test-process rule outside G2 language acceptance.",
    "STACK": "Technology/stack rule outside G2 language acceptance.",
    "CLI": "P3 CLI compatibility, outside G2.",
    "COMPAT": "P3 compatibility profile, outside G2.",
    "LSP": "P3 LSP integration, outside G2.",
    "VIZ": "P3 visualization, outside G2.",
    "WASM": "P3 portable/platform acceptance, outside G2.",
    "SDK": "P3 SDK/release, outside G2.",
    "PERF": "P3 performance, outside G2.",
}
CHAPTER_STEP = {"04": "B3", "05": "C3", "06": "B2", "07": "H1", "08": "H1",
                "09": "F3", "10": None, "13": None, "16": "B2"}


def digest(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def is_g2_phase(phase: str) -> bool:
    return any(token in phase for token in ("P0", "P1", "P2", "P0/P1", "P0–P2", "P1–P3"))


def numbered(requirements: list[dict], matrix: dict) -> list[dict]:
    refs: dict[str, set[str]] = {}
    for rule in matrix["rules"]:
        for requirement in rule["requirements"]:
            refs.setdefault(requirement, set()).add(rule["id"])
    rows = []
    for requirement in requirements:
        identifier = requirement["id"]
        prefix = identifier.split("-")[0]
        linked = sorted(refs.get(identifier, set()))
        steps = sorted({next(rule["step"] for rule in matrix["rules"] if rule["id"] == rule_id)
                        for rule_id in linked})
        owner = steps[0] if len(steps) == 1 else ("MULTI" if steps else PREFIX_STEP.get(prefix))
        if linked:
            status = "MATRIX_LINKED"
        elif prefix in G2_PREFIXES and owner in STEPS:
            status = "G2_STEP_PLANNED"
        elif prefix in G2_PREFIXES:
            status = "G2_OWNER_MISSING"
        else:
            status = "OUTSIDE_G2"
        rows.append({
            "id": identifier,
            "kind": "NUMBERED_REQUIREMENT",
            "requirement": requirement["requirement"],
            "phase": requirement["phase"],
            "acceptance": requirement["acceptance"],
            "source": requirement["source"],
            "line": requirement["line"],
            "sourceLineSha256": requirement["sourceLineSha256"],
            "matrixRules": linked,
            "ownerSteps": steps,
            "ownerStep": owner if owner in STEPS else None,
            "status": status,
            "g2Relevance": "G2" if prefix in G2_PREFIXES else "NON_G2",
            "evidence": "MATRIX_RULE_ONLY" if linked else ("PLANNED" if prefix in G2_PREFIXES else "NOT_APPLICABLE"),
            "remainingCause": ("Linked to matrix rule(s); concrete test/acceptance still open."
                               if linked else
                               ("No matrix rule links this G2 requirement; planned owner step still needs concrete acceptance."
                                if prefix in G2_PREFIXES else
                                OUTSIDE_REASON.get(prefix, "Outside the G2 compiler scope."))),
        })
    return rows


def unnumbered() -> list[dict]:
    rows = []
    for chapter in CHAPTERS:
        path = next((ROOT / "docs/spec").glob(chapter + "-*.md"))
        lines = path.read_text().splitlines()
        fence = False
        for number, line in enumerate(lines, 1):
            stripped = line.strip()
            if stripped.startswith("```"):
                fence = not fence
                continue
            if fence or not stripped or stripped.startswith(("#", "|", ">", "-")):
                continue
            if not KEYWORD.search(line):
                continue
            owner = CHAPTER_STEP[chapter]
            rows.append({
                "id": f"SPEC:{chapter}:L{number}",
                "kind": "UNNUMBERED_NORMATIVE_LINE",
                "text": stripped,
                "source": str(path.relative_to(ROOT)),
                "line": number,
                "sourceLineSha256": digest(line),
                "ownerStep": owner,
                "status": "G2_STEP_PLANNED" if owner in STEPS else "LATER_PHASE",
                "acceptance": "PLANNED_NOT_ACCEPTED",
                "evidence": "NONE",
                "remainingCause": "Line-level detection only; surrounding prose and paragraph atomization still need review.",
            })
    return rows


def build(source: dict, matrix: dict) -> dict:
    numbered_rows = numbered(source["specRequirements"], matrix)
    unnumbered_rows = unnumbered()
    return {
        "schemaVersion": "1.0",
        "status": "PLANNED_NOT_ACCEPTED",
        "purpose": "A2-R4 numbered requirement and explicit unnumbered-line worklist; no coverage or acceptance claim",
        "numbered": numbered_rows,
        "unnumbered": unnumbered_rows,
    }


def validate(data: dict, source: dict) -> dict:
    expected = {row["id"] for row in source["specRequirements"]}
    actual = {row["id"] for row in data["numbered"]}
    if expected != actual or len(data["numbered"]) != len(expected):
        raise ValueError("Numbered requirement coverage differs from source inventory")
    for row in data["numbered"]:
        if row["status"] not in {"MATRIX_LINKED", "G2_STEP_PLANNED", "G2_OWNER_MISSING", "OUTSIDE_G2"}:
            raise ValueError("Unknown numbered status: " + row["id"])
        if row["status"] == "G2_STEP_PLANNED" and row["ownerStep"] is None:
            raise ValueError("G2 requirement lacks owner step: " + row["id"])
        if row["status"] == "MATRIX_LINKED" and row["ownerStep"] is None and not row["ownerSteps"]:
            raise ValueError("Matrix-linked requirement lacks owner steps: " + row["id"])
        if row["status"] == "G2_OWNER_MISSING" and row["ownerStep"] is not None:
            raise ValueError("Owner-missing row has an owner: " + row["id"])
        if row["status"] == "OUTSIDE_G2" and row["g2Relevance"] != "NON_G2":
            raise ValueError("Outside row is G2-relevant: " + row["id"])
        if row["evidence"] == "MATRIX_RULE_ONLY" and not row["matrixRules"]:
            raise ValueError("Matrix evidence without rule: " + row["id"])
    for row in data["unnumbered"]:
        if not row["sourceLineSha256"] or not row["source"].startswith("docs/spec/"):
            raise ValueError("Unnumbered line lacks source/hash: " + row["id"])
        if row["ownerStep"] is not None and row["ownerStep"] not in STEPS:
            raise ValueError("Invalid unnumbered owner: " + row["id"])
    return {"numbered": len(data["numbered"]), "unnumbered": len(data["unnumbered"]),
            "matrixLinked": sum(1 for row in data["numbered"] if row["status"] == "MATRIX_LINKED"),
            "ownerMissing": sum(1 for row in data["numbered"] if row["status"] == "G2_OWNER_MISSING"),
            "outsideG2": sum(1 for row in data["numbered"] if row["status"] == "OUTSIDE_G2"),
            "acceptance": "PLANNED_NOT_ACCEPTED"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    if not out.is_relative_to(ROOT / "build"):
        parser.error("Output must be under build/")
    source = json.loads((ROOT / "docs/evidence/stabilization/g2-source-inventory.json").read_text())
    matrix = json.loads((ROOT / "docs/evidence/stabilization/g2-rule-matrix.json").read_text())
    data = build(source, matrix)
    data["validation"] = validate(data, source)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(data["validation"]))


if __name__ == "__main__":
    main()
