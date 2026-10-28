#!/usr/bin/env python3
"""Bind every grammar production occurrence to its owner step and matrix rule.

The source inventory is discovery only. This worklist adds the explicit owner
step, matrix rule, requirements and a planned acceptance state for each of the
125 parser and 196 lexer productions and their 1218 nested occurrences. It never
claims that a grammar alternative is implemented or accepted.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STEPS = {"B1", "B2", "B3", "C1", "C2", "C3", "C4", "D1", "D2", "D3",
         "E1", "E2", "E3", "F1", "F2", "F3", "G1", "G2", "G3", "H1", "H2", "H3"}


def build(source: dict, matrix: dict) -> dict:
    rules = {rule["id"]: rule for rule in matrix["rules"]}
    productions = []
    occurrences = []
    for grammar in source["grammars"]:
        lexer = grammar["source"].endswith("InterlisLexer.g4")
        rule_id = ("LEX:" if lexer else "SYN:") + grammar["production"]
        rule = rules.get(rule_id)
        if rule is None and lexer:
            # Lexer terminals are not yet individual matrix rows; the grammar
            # worklist owns them under the lexical step until the matrix is
            # expanded. This is explicit ownership, not acceptance.
            rule = {"id": rule_id, "step": grammar["semanticStep"] or grammar["syntaxStep"],
                    "requirements": ["SYN-002", "SYN-004", "SYN-005", "SYN-007", "SYN-011", "SYN-013",
                                     "TEST-011", "TEST-012"],
                    "state": "OPEN"}
        if rule is None:
            raise ValueError("Grammar production without matrix rule: " + rule_id)
        if rule["step"] not in STEPS:
            raise ValueError("Grammar production has invalid owner step: " + rule_id)
        productions.append({
            "id": rule_id,
            "production": grammar["production"],
            "class": "lexer" if lexer else "parser",
            "source": grammar["source"],
            "line": grammar["line"],
            "matrixRule": rule_id,
            "matrixRuleSource": "MATRIX" if rule_id in rules else "GRAMMAR_WORKLIST",
            "ownerStep": rule["step"],
            "requirements": rule["requirements"],
            "state": rule["state"],
            "occurrenceCount": len(grammar["occurrences"]),
            "acceptance": "PLANNED_NOT_ACCEPTED",
        })
        for occurrence in grammar["occurrences"]:
            occurrences.append({
                "id": occurrence["id"],
                "production": grammar["production"],
                "class": "lexer" if lexer else "parser",
                "source": grammar["source"],
                "line": occurrence["line"],
                "kind": occurrence["kind"],
                "text": occurrence["text"],
                "sha256": occurrence["sha256"],
                "matrixRule": rule_id,
                "matrixRuleSource": "MATRIX" if rule_id in rules else "GRAMMAR_WORKLIST",
                "ownerStep": rule["step"],
                "requirements": rule["requirements"],
                "state": rule["state"],
                "acceptance": "PLANNED_NOT_ACCEPTED",
                "remainingCause": "Grammar occurrence has an owner step and matrix rule; concrete parser/lexer acceptance and independent review remain open.",
            })
    return {
        "schemaVersion": "1.0",
        "status": "PLANNED_NOT_ACCEPTED",
        "purpose": "A2-R4 grammar occurrence worklist; ownership is not implementation or acceptance evidence",
        "source": "g2-source-inventory.json",
        "productions": productions,
        "occurrences": occurrences,
    }


def validate(data: dict, source: dict) -> dict:
    if len(data.get("productions", [])) != len(source["grammars"]):
        raise ValueError("Production count differs from source inventory")
    source_ids = [occurrence["id"] for grammar in source["grammars"] for occurrence in grammar["occurrences"]]
    ids = [occurrence["id"] for occurrence in data["occurrences"]]
    if len(ids) != len(set(ids)) or len(ids) != len(source_ids) or set(ids) != set(source_ids):
        raise ValueError("Occurrence ids differ from source inventory")
    for occurrence in data["occurrences"]:
        if occurrence["ownerStep"] not in STEPS:
            raise ValueError("Invalid occurrence owner: " + occurrence["id"])
        if occurrence["acceptance"] != "PLANNED_NOT_ACCEPTED":
            raise ValueError("Occurrence claims acceptance: " + occurrence["id"])
        if not occurrence["matrixRule"] or not occurrence["requirements"]:
            raise ValueError("Occurrence lacks matrix rule/requirements: " + occurrence["id"])
    for production in data["productions"]:
        if production["ownerStep"] not in STEPS or production["acceptance"] != "PLANNED_NOT_ACCEPTED":
            raise ValueError("Invalid production: " + production["id"])
    return {"productions": len(data["productions"]), "occurrences": len(data["occurrences"]),
            "parserProductions": sum(1 for p in data["productions"] if p["class"] == "parser"),
            "lexerProductions": sum(1 for p in data["productions"] if p["class"] == "lexer"),
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
