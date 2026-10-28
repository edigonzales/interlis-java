#!/usr/bin/env python3
"""Create the A2-R4 builtin-function worklist from the actual Java signatures.

Each of the 21 declared standard functions receives its signature, owner step,
E2 requirement union and four planned case kinds (positive, negative, context,
undefined). Planned cases are not execution evidence.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUILTINS = "interlis-compiler/src/main/java/guru/interlis/compiler/internal/BuiltinFunctions.java"
NAME = re.compile(r'(?:signature|new Signature)\("([A-Za-z]\w*)"')
VALUE = re.compile(r"Value\.([A-Z_]+)")
SINCE = re.compile(r"LanguageVersion\.(ILI23|ILI24)")
RULE = re.compile(r"Rule\.([A-Z_]+)")
CASE_KINDS = ("POSITIVE", "NEGATIVE", "CONTEXT", "UNDEFINED")


def digest(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def parse_signatures(text: str) -> list[dict]:
    signatures = []
    for match in re.finditer(r"(?:signature|new Signature)\(", text):
        start = match.start()
        depth = 0
        end = start
        while end < len(text):
            if text[end] == "(":
                depth += 1
            elif text[end] == ")":
                depth -= 1
                if depth == 0:
                    break
            end += 1
        statement = text[start:end + 1]
        name_match = NAME.search(statement)
        values = VALUE.findall(statement)
        if name_match is None or not values:
            continue  # helper/record declaration, not a declared function
        since = SINCE.search(statement)
        rule = RULE.search(statement)
        signatures.append({
            "name": name_match.group(1),
            "result": values[0],
            "arguments": values[1:],
            "since": since.group(1) if since else "ILI23",
            "rule": rule.group(1) if rule else "SIMPLE",
            "line": text.count("\n", 0, start) + 1,
        })
    if len(signatures) != len({signature["name"] for signature in signatures}):
        raise ValueError("Duplicate builtin signature")
    return signatures


def build(text: str, source_inventory: dict, matrix: dict) -> dict:
    signatures = parse_signatures(text)
    expected = [item["id"].removeprefix("BUILTIN:") for item in source_inventory["standardFunctions"]]
    if [signature["name"] for signature in signatures] != expected:
        raise ValueError("Java signatures differ from the source inventory")
    e2_requirements = sorted({requirement for rule in matrix["rules"] if rule["step"] == "E2"
                              for requirement in rule["requirements"]})
    rows = []
    for signature in signatures:
        name = signature["name"]
        decision = "BUILTIN_AREAS2_AREAS3_NORM_PROFILE" if name in {"areAreas2", "areAreas3"} else None
        rows.append({
            "id": "BUILTIN:" + name,
            "name": name,
            "result": signature["result"],
            "arguments": signature["arguments"],
            "since": signature["since"],
            "rule": signature["rule"],
            "source": BUILTINS,
            "line": signature["line"],
            "matrixRule": "BUILTIN:" + name,
            "matrixRuleSource": "BUILTIN_WORKLIST",
            "ownerStep": "E2",
            "requirements": e2_requirements,
            "acceptance": "USER_DECISION_PENDING" if decision else "PLANNED_NOT_ACCEPTED",
            "pendingUserDecision": decision,
            "implementation": f"{BUILTINS}:{signature['line']}",
            "evidence": "SIGNATURE_ONLY",
            "cases": [
                {"id": f"BUILTIN:{name}:{kind[0]}", "kind": kind, "status": "PLANNED",
                 "description": {
                     "POSITIVE": f"Call {name} with {', '.join(signature['arguments']) or 'no argument'} and assert {signature['result']}.",
                     "NEGATIVE": f"Call {name} with a wrong argument count/type and assert a structured source/semantic diagnostic.",
                     "CONTEXT": f"Assert the version/context rule for {name} in 2.3/2.4.",
                     "UNDEFINED": f"Assert the UNDEFINED/result behavior of {name} where the norm permits it.",
                 }[kind],
                 "preserved": True}
                for kind in CASE_KINDS
            ],
            "remainingCause": ("Signature/profile is a pending user decision; no normative change is made."
                               if decision else
                               "Signature is declared, but argument/result/context/UNDEFINED acceptance is still planned."),
        })
    return {
        "schemaVersion": "1.0",
        "status": "PLANNED_NOT_ACCEPTED",
        "purpose": "A2-R4 builtin-function worklist; signatures are implementation facts, not acceptance",
        "source": BUILTINS,
        "requirements": e2_requirements,
        "functions": rows,
    }


def validate(data: dict, source_inventory: dict) -> dict:
    expected = [item["id"].removeprefix("BUILTIN:") for item in source_inventory["standardFunctions"]]
    names = [row["name"] for row in data["functions"]]
    if names != expected or len(names) != len(set(names)):
        raise ValueError("Builtin worklist differs from source inventory")
    for row in data["functions"]:
        if row["ownerStep"] != "E2" or row["matrixRule"] != row["id"]:
            raise ValueError("Invalid builtin owner: " + row["id"])
        if [case["kind"] for case in row["cases"]] != list(CASE_KINDS):
            raise ValueError("Missing builtin case kinds: " + row["id"])
        if any(case["status"] != "PLANNED" for case in row["cases"]):
            raise ValueError("Builtin case claims execution: " + row["id"])
        if row["acceptance"] == "USER_DECISION_PENDING" and not row["pendingUserDecision"]:
            raise ValueError("User decision without id: " + row["id"])
        if row["acceptance"] not in {"PLANNED_NOT_ACCEPTED", "USER_DECISION_PENDING"}:
            raise ValueError("Builtin claims acceptance: " + row["id"])
    return {"functions": len(names), "plannedCases": sum(len(row["cases"]) for row in data["functions"]),
            "userDecisions": sum(1 for row in data["functions"] if row["acceptance"] == "USER_DECISION_PENDING"),
            "acceptance": "PLANNED_NOT_ACCEPTED"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    if not out.is_relative_to(ROOT / "build"):
        parser.error("Output must be under build/")
    text = (ROOT / BUILTINS).read_text()
    source = json.loads((ROOT / "docs/evidence/stabilization/g2-source-inventory.json").read_text())
    matrix = json.loads((ROOT / "docs/evidence/stabilization/g2-rule-matrix.json").read_text())
    data = build(text, source, matrix)
    data["validation"] = validate(data, source)
    data["sourceSha256"] = digest(text)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(data["validation"]))


if __name__ == "__main__":
    main()
