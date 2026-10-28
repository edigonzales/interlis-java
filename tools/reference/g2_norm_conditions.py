#!/usr/bin/env python3
"""Build paragraph-cluster conditions from the G2 A2-R4 section inventory.

Each compiler-rule section is split into clusters of at most three exact
paragraphs. A condition carries the owning step, the exact quote/hashes and four
*planned* case kinds (positive, negative, model, query). Planned cases are not
evidence; the artifact explicitly remains PLANNED_NOT_ACCEPTED until individual
cases are implemented and reviewed.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STEPS = {"B1", "B2", "B3", "C1", "C2", "C3", "C4", "D1", "D2", "D3",
         "E1", "E2", "E3", "F1", "F2", "F3", "G1", "G2", "G3", "H1", "H2", "H3"}
CASE_KINDS = ("POSITIVE", "NEGATIVE", "MODEL", "QUERY")
LEXICAL_RULES = {
    "Name": ["NORM:NAME.alphabet", "NORM:NAME.length", "NORM:NAME.case"],
    "Zeichenketten": ["NORM:STRING.singleLine", "NORM:STRING.quoteEscape", "NORM:STRING.unicodeEscape",
                      "NORM:STRING.surrogatePair", "NORM:STRING.backslashEscape"],
    "Kommentar": ["NORM:COMMENT.lineStart", "NORM:COMMENT.lineEnd", "NORM:COMMENT.blockStart",
                  "NORM:COMMENT.nested", "NORM:SOURCE.commentUnicode"],
    "Zahlen": ["NORM:NUMBER.leadingZeros", "NORM:NUMBER.signedInteger", "NORM:NUMBER.scaledFloat"],
    "Eigenschaft": ["NORM:PROPERTY.context"],
    "Erläuterung": ["NORM:EXPLANATION.delimiter"],
}


def digest(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def fragment_reason(quote: str) -> str | None:
    stripped = quote.strip()
    if re.fullmatch(r"\d+(?:\.\d+)*\s+.+", stripped) and len(stripped) < 160:
        return "section heading without normative sentence"
    if stripped.startswith("Abbildung "):
        return "figure caption"
    if re.match(r"^[A-Za-z][A-Za-z0-9]*\s*=", stripped):
        return "grammar fragment"
    if stripped.startswith("END ") or all(line.strip().startswith("END ") or not line.strip()
                                           for line in stripped.splitlines()):
        return "code example tail"
    return None


def lexical_rules(title: str) -> list[str]:
    return next((ids for keyword, ids in LEXICAL_RULES.items() if keyword in title), [])


def build(inventory: dict, crosswalk: dict, matrix: dict | None = None, candidate_index: dict | None = None) -> dict:
    rows = {f"{doc['document']}:{row['section']}": row
            for doc in crosswalk["documents"] for row in doc["sections"]}
    crosswalk_docs = {doc["document"]: doc for doc in crosswalk["documents"]}
    atomic = {row["id"]: row for row in (matrix or {}).get("atomicRules", [])}
    candidates = {row["id"]: row["candidates"] for row in (candidate_index or {}).get("conditions", [])}
    documents = []
    for document in inventory["documents"]:
        version = document["document"]
        entries = document["entries"]
        by_section: dict[str, list[dict]] = {}
        for entry in entries:
            if entry["section"] == "UNSECTIONED":
                continue
            by_section.setdefault(entry["section"], []).append(entry)
        conditions = []
        section_order = [row["section"] for row in crosswalk_docs[version]["sections"]]
        for section in section_order:
            row = rows[f"{version}:{section}"]
            paragraphs = sorted(by_section.get(section, []), key=lambda entry: (entry["page"], entry["start"]))
            kind = row["disposition"]
            if kind == "COMPILER_RULE":
                clusters = [paragraphs[index:index + 3] for index in range(0, len(paragraphs), 3)] or [[]]
            else:
                clusters = [paragraphs]
            for index, cluster in enumerate(clusters, 1):
                quote = "\n\n".join(entry["quote"] for entry in cluster)
                condition_id = f"NORM:{version}:{section}:C{index:02d}"
                mapped = lexical_rules(row["title"]) if kind == "COMPILER_RULE" else []
                cases = []
                if kind == "COMPILER_RULE":
                    for case_kind in CASE_KINDS:
                        cases.append({
                            "id": f"{condition_id}:{case_kind[0]}",
                            "kind": case_kind,
                            "status": "PLANNED",
                            "description": {
                                "POSITIVE": f"Compile a valid model exercising {row['title']} ({version}).",
                                "NEGATIVE": f"Reject an invalid {row['title']} source with a structured source/semantic diagnostic ({version}).",
                                "MODEL": f"Assert public symbol identity/target/source range for {row['title']} ({version}).",
                                "QUERY": f"Assert the public query/navigation result for {row['title']} ({version}).",
                            }[case_kind],
                        })
                linked = []
                for rule_id in mapped:
                    rule = atomic.get(rule_id)
                    if rule is None:
                        continue
                    linked.append({
                        "id": rule_id,
                        "caseCoverage": rule.get("caseCoverage"),
                        "independentAcceptance": rule.get("independentAcceptance"),
                        "observedMethods": [item.get("candidateTest") for item in rule.get("observedAssertions", [])],
                        "remainingCause": rule.get("remainingCause"),
                    })
                conditions.append({
                    "id": condition_id,
                    "document": version,
                    "section": section,
                    "sectionTitle": row["title"],
                    "kind": kind,
                    "ownerStep": row["ownerStep"],
                    "phase": row["phase"],
                    "paragraphIds": [entry["id"] for entry in cluster],
                    "paragraphCount": len(cluster),
                    "quote": quote,
                    "quoteSha256": digest(quote),
                    "matrixRules": mapped,
                    "requirements": [],
                    "implementation": "NOT_ASSESSED",
                    "evidence": ("PARTIAL_LINKED" if linked else "PLANNED") if kind == "COMPILER_RULE" else "NOT_APPLICABLE",
                    "linkedAtomicRules": linked,
                    "candidateTests": candidates.get(condition_id, []),
                    "candidateStatus": "CANDIDATE_ONLY" if candidates.get(condition_id) else "NONE",
                    "cases": cases,
                    "atomization": "PARAGRAPH_CLUSTER",
                    "acceptance": ("PARTIAL_LINKED_NOT_ACCEPTED" if linked else "PLANNED_NOT_ACCEPTED") if kind == "COMPILER_RULE" else (
                        "LATER_PHASE" if kind == "LATER_EXECUTION" else (
                            "OUTSIDE_COMPILER" if kind == "OUTSIDE_COMPILER" else "NOT_APPLICABLE")),
                    "review": "NOT_REVIEWED",
                    "remainingCause": ("Planned cases only; implementation/evidence and independent review remain open."
                                       if kind == "COMPILER_RULE" else row["reason"]),
                })
        for condition in conditions:
            if condition["kind"] == "COMPILER_RULE" and condition["candidateStatus"] == "NONE":
                reason = fragment_reason(condition["quote"])
                if reason:
                    condition.update(kind="EXPLANATORY", ownerStep=None, phase=None, cases=[],
                                     evidence="NOT_APPLICABLE", acceptance="NOT_APPLICABLE",
                                     atomization="SECTION_LEVEL", fragmentReason=reason,
                                     remainingCause="Explanatory fragment inside a compiler-rule section; no compiler condition.")
            if condition["ownerStep"] == "B1":
                condition.update(kind="PREDEFINED_CONTENT", cases=[], evidence="PREDEFINED_INVENTORY",
                                 acceptance="CATALOGUE_PARTIAL_NOT_ACCEPTED", atomization="SOURCE_LISTING",
                                 predefinedInventory="g2-A2-predefined-inventory.md",
                                 remainingCause="Predefined-model source listing tracked by the A2 predefined catalogue and its tests; no generic four-case condition.")
        documents.append({"document": version, "conditions": conditions,
                          "sections": len({condition["section"] for condition in conditions})})
    return {"schemaVersion": "1.0", "status": "PLANNED_NOT_ACCEPTED",
            "purpose": "A2-R4 paragraph-cluster condition worklist; planned cases are not evidence",
            "atomization": "PARAGRAPH_CLUSTER", "documents": documents}


def validate(data: dict, inventory: dict, crosswalk: dict) -> dict:
    inventory_docs = {doc["document"]: doc for doc in inventory["documents"]}
    crosswalk_docs = {doc["document"]: doc for doc in crosswalk["documents"]}
    compiler_paragraphs = {doc["document"]: {entry["id"] for entry in doc["entries"]
                                             if any(row["section"] == entry["section"] and row["disposition"] == "COMPILER_RULE"
                                                    for row in crosswalk_docs[doc["document"]]["sections"])}
                           for doc in inventory["documents"]}
    for document in data.get("documents", []):
        version = document["document"]
        known_sections = {row["section"] for row in crosswalk_docs[version]["sections"]}
        seen = set()
        covered = set()
        entries_by_id = {entry["id"]: entry for entry in inventory_docs[version]["entries"]}
        for condition in document["conditions"]:
            if condition["section"] not in known_sections:
                raise ValueError("Unknown condition section: " + condition["id"])
            row = next(row for row in crosswalk_docs[version]["sections"]
                       if row["section"] == condition["section"])
            if condition["kind"] != row["disposition"] and not (
                    condition["kind"] == "EXPLANATORY" and condition.get("fragmentReason")) and not (
                    condition["kind"] == "PREDEFINED_CONTENT" and row["ownerStep"] == "B1"):
                raise ValueError("Condition kind differs from crosswalk: " + condition["id"])
            if condition["atomization"] != "PARAGRAPH_CLUSTER" and not (
                    condition["kind"] == "EXPLANATORY" and condition.get("fragmentReason")) and not (
                    condition["kind"] == "PREDEFINED_CONTENT" and condition["atomization"] == "SOURCE_LISTING"):
                raise ValueError("Condition claims atomization: " + condition["id"])
            if condition["acceptance"] in {"PASSED", "VERIFIED", "ACCEPTED"}:
                raise ValueError("Condition claims acceptance: " + condition["id"])
            if condition["evidence"] == "PARTIAL_LINKED":
                if not condition.get("linkedAtomicRules"):
                    raise ValueError("Partial link without atomic evidence: " + condition["id"])
                if condition["acceptance"] != "PARTIAL_LINKED_NOT_ACCEPTED":
                    raise ValueError("Partial link has wrong acceptance: " + condition["id"])
            elif condition["evidence"] == "PLANNED" and condition["acceptance"] != "PLANNED_NOT_ACCEPTED":
                raise ValueError("Planned condition has wrong acceptance: " + condition["id"])
            if condition["kind"] == "COMPILER_RULE":
                if condition["ownerStep"] not in STEPS or condition["phase"] != "G2":
                    raise ValueError("Invalid condition owner: " + condition["id"])
                if [case["kind"] for case in condition["cases"]] != list(CASE_KINDS):
                    raise ValueError("Missing planned case kinds: " + condition["id"])
                if any(case["status"] != "PLANNED" for case in condition["cases"]):
                    raise ValueError("Case claims execution: " + condition["id"])
                if not condition["paragraphIds"]:
                    raise ValueError("Empty compiler condition: " + condition["id"])
            elif condition["kind"] == "PREDEFINED_CONTENT":
                if condition["ownerStep"] != "B1" or condition["phase"] != "G2" or condition["cases"]:
                    raise ValueError("Invalid predefined-content condition: " + condition["id"])
                if condition["acceptance"] != "CATALOGUE_PARTIAL_NOT_ACCEPTED":
                    raise ValueError("Predefined content claims acceptance: " + condition["id"])
            ids = condition["paragraphIds"]
            if len(ids) != len(set(ids)):
                raise ValueError("Duplicate paragraph in condition: " + condition["id"])
            joined = "\n\n".join(entries_by_id[p]["quote"] for p in ids)
            for paragraph in ids:
                if paragraph not in entries_by_id:
                    raise ValueError("Unknown paragraph: " + condition["id"])
                if entries_by_id[paragraph]["section"] != condition["section"]:
                    raise ValueError("Paragraph belongs to another section: " + condition["id"])
                if paragraph in covered:
                    raise ValueError("Paragraph in more than one condition: " + paragraph)
                covered.add(paragraph)
            if condition["quote"] != joined or condition["quoteSha256"] != digest(joined):
                raise ValueError("Changed condition quote: " + condition["id"])
            if condition["id"] in seen:
                raise ValueError("Duplicate condition id: " + condition["id"])
            seen.add(condition["id"])
        missing = compiler_paragraphs[version] - covered
        if missing:
            raise ValueError(f"Uncovered compiler paragraphs in {version}: {len(missing)}")
        missing_sections = known_sections - {condition["section"] for condition in document["conditions"]}
        if missing_sections:
            raise ValueError(f"Uncovered sections in {version}: {len(missing_sections)}")
    return {document["document"]: {"conditions": len(document["conditions"]),
                                   "compilerConditions": sum(1 for c in document["conditions"] if c["kind"] == "COMPILER_RULE"),
                                   "plannedCases": sum(len(c["cases"]) for c in document["conditions"])}
            for document in data["documents"]}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--crosswalk", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    if not out.is_relative_to(ROOT / "build"):
        parser.error("Output must be under build/")
    inventory = json.loads(args.inventory.read_text())
    crosswalk = json.loads(args.crosswalk.read_text())
    matrix = json.loads((ROOT / "docs/evidence/stabilization/g2-rule-matrix.json").read_text())
    candidate_index = json.loads((ROOT / "docs/evidence/stabilization/g2-test-candidate-index.json").read_text())
    data = build(inventory, crosswalk, matrix, candidate_index)
    data["validation"] = validate(data, inventory, crosswalk)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(data["validation"]))


if __name__ == "__main__":
    main()
