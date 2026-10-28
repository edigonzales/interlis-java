#!/usr/bin/env python3
"""Section-level crosswalk for the G2 A2-R4 norm audit.

The crosswalk assigns every TOC section a disposition (compiler step, later
phase, explanatory or outside the compiler) and an explicit reason. It does NOT
atomize paragraphs into conditions; entries are marked SECTION_LEVEL and remain
worklist items until individual positive/negative/model/query acceptance exists.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STEPS = {"B1", "B2", "B3", "C1", "C2", "C3", "C4", "D1", "D2", "D3",
         "E1", "E2", "E3", "F1", "F2", "F3", "G1", "G2", "G3", "H1", "H2", "H3"}


def canonical_section(document: str, section: str) -> str:
    """Drop the version-specific language chapter offset (2.x vs 3.x)."""
    if section.startswith("APPENDIX:"):
        return section
    parts = section.split(".")
    if document == "2.3" and parts and parts[0] in {"2", "3"}:
        return ".".join(parts[1:]) if len(parts) > 1 else ""
    if document == "2.4" and parts and parts[0] in {"3", "4"}:
        return ".".join(parts[1:]) if len(parts) > 1 else ""
    return section


def disposition(document: str, section: str, title: str) -> dict:
    """Return a proposed section disposition with an explicit reason."""
    key = canonical_section(document, section)
    low = title.casefold()
    if (document == "2.3" and section == "2") or (document == "2.4" and section == "3"):
        return {"disposition": "EXPLANATORY", "ownerStep": None, "phase": None,
                "reason": "Chapter heading for the language description; rules are in its subsections."}
    if (document == "2.3" and section == "3") or (document == "2.4" and section == "4"):
        return {"disposition": "LATER_EXECUTION", "ownerStep": None, "phase": "P4",
                "reason": "Chapter heading for sequential transfer; belongs to the later transfer layer."}
    if section.startswith("APPENDIX:"):
        letter = section.split(":", 1)[1]
        if letter == "A":
            return {"disposition": "COMPILER_RULE", "ownerStep": "B1", "phase": "G2",
                    "reason": "Normative internal INTERLIS model; predefined contents and public identities."}
        if letter == "B":
            if document == "2.3":
                return {"disposition": "COMPILER_RULE", "ownerStep": "B3", "phase": "G2",
                        "reason": "Normative character table for lexical acceptance."}
            return {"disposition": "LATER_EXECUTION", "ownerStep": None, "phase": "P4",
                    "reason": "Normative XML schema for the INTERLIS model; transfer layer, not compiler closure."}
        if letter == "C":
            if document == "2.3":
                return {"disposition": "EXPLANATORY", "ownerStep": None, "phase": None,
                        "reason": "Informative Roads example."}
            return {"disposition": "LATER_EXECUTION", "ownerStep": None, "phase": "P4",
                    "reason": "Normative XML schema for geometry types; transfer layer."}
        if letter == "D":
            return {"disposition": "COMPILER_RULE", "ownerStep": "B3", "phase": "G2",
                    "reason": "Normative character table."}
        if letter == "E":
            return {"disposition": "EXPLANATORY", "ownerStep": None, "phase": None,
                    "reason": "Informative Roads example."}
        predefined = {"F", "G", "H", "I", "J"} if document == "2.3" else {"F", "G", "H", "I", "J", "K", "L"}
        if letter in predefined:
            return {"disposition": "COMPILER_RULE", "ownerStep": "B1", "phase": "G2",
                    "reason": "Standard-extension predefined contents; versioned catalogue ownership."}
        if letter in {"K", "L", "M", "N", "O", "P", "Q", "R"}:
            return {"disposition": "EXPLANATORY", "ownerStep": None, "phase": None,
                    "reason": "Glossary, index, bibliography or document apparatus."}
    if document == "2.3" and section.startswith("1"):
        if key == "7":
            return {"disposition": "COMPILER_RULE", "ownerStep": "B2", "phase": "G2",
                    "reason": "Contract declarations affect compiled model headers."}
        return {"disposition": "EXPLANATORY", "ownerStep": None, "phase": None,
                "reason": "Introductory principles; no additional compiler rule."}
    if document == "2.4" and section.startswith(("1", "2")):
        if key == "8":
            return {"disposition": "COMPILER_RULE", "ownerStep": "C2", "phase": "G2",
                    "reason": "Rounding of numeric values and coordinates affects type semantics."}
        if key == "7":
            return {"disposition": "OUTSIDE_COMPILER", "ownerStep": None, "phase": "G3",
                    "reason": "Tool capability/conformity statements; no compiler language rule."}
        return {"disposition": "EXPLANATORY", "ownerStep": None, "phase": None,
                "reason": "Introductory principles; no additional compiler rule."}
    if document == "2.3" and section.startswith("3"):
        return _transfer(document, key, low)
    if document == "2.4" and section.startswith("4"):
        return _transfer(document, key, low)
    # Language chapters: canonical key is the chapter without the version offset.
    if key == "1" or key.startswith("2"):
        return {"disposition": "COMPILER_RULE", "ownerStep": "B3", "phase": "G2",
                "reason": "Lexical/syntax rule."}
    if key == "3":
        return {"disposition": "COMPILER_RULE", "ownerStep": "B3", "phase": "G2",
                "reason": "Main language rule and reserved words."}
    if key == "4":
        return {"disposition": "COMPILER_RULE", "ownerStep": "D1", "phase": "G2",
                "reason": "Inheritance rule."}
    if key == "5" or key.startswith("5."):
        return {"disposition": "COMPILER_RULE", "ownerStep": "B2", "phase": "G2",
                "reason": "Model, topic, class, namespace and visibility rule."}
    if key == "6" or key.startswith("6."):
        return {"disposition": "COMPILER_RULE", "ownerStep": "D3", "phase": "G2",
                "reason": "Attribute declaration and effective member rule."}
    if key == "7" or key.startswith("7."):
        return {"disposition": "COMPILER_RULE", "ownerStep": "D2", "phase": "G2",
                "reason": "Association and role rule."}
    if key == "8" or key.startswith("8."):
        return {"disposition": "COMPILER_RULE", "ownerStep": "C3", "phase": "G2",
                "reason": "Value range, constant and type rule."}
    if key == "9" or key.startswith("9."):
        return {"disposition": "COMPILER_RULE", "ownerStep": "C1", "phase": "G2",
                "reason": "Unit and numeric subdivision rule."}
    if key == "10" or key.startswith("10.") or key == "11" or key.startswith("11."):
        return {"disposition": "COMPILER_RULE", "ownerStep": "F3", "phase": "G2",
                "reason": "Metaobject, parameter, CRS or runtime parameter rule."}
    if key == "12":
        return {"disposition": "COMPILER_RULE", "ownerStep": "E3", "phase": "G2",
                "reason": "Constraint family rule."}
    if key == "13":
        return {"disposition": "COMPILER_RULE", "ownerStep": "E1", "phase": "G2",
                "reason": "Expression and path rule."}
    if key == "14":
        return {"disposition": "COMPILER_RULE", "ownerStep": "E2", "phase": "G2",
                "reason": "Function and builtin rule."}
    if key == "15":
        return {"disposition": "COMPILER_RULE", "ownerStep": "F1", "phase": "G2",
                "reason": "View formation rule."}
    if key == "16":
        return {"disposition": "COMPILER_RULE", "ownerStep": "F3", "phase": "G2",
                "reason": "Graphic/signature rule."}
    return {"disposition": "AUDIT_REQUIRED", "ownerStep": None, "phase": None,
            "reason": "Section subject not yet classified; requires manual audit."}


def _transfer(document: str, key: str, low: str) -> dict:
    if key in {"2.1", "2.2"} or "ableitbarkeit" in low or "erweiterten modellen" in low:
        return {"disposition": "COMPILER_RULE", "ownerStep": "B2", "phase": "G2",
                "reason": "Transfer prerequisite derived from the compiled model."}
    if "normative referenzen" in low:
        return {"disposition": "OUTSIDE_COMPILER", "ownerStep": None, "phase": "P4",
                "reason": "Transfer reference list; no compiler rule."}
    return {"disposition": "LATER_EXECUTION", "ownerStep": None, "phase": "P4",
            "reason": "Sequential/XML transfer rule; belongs to the later transfer layer, not compiler closure."}


def build(sections: list[dict], document: str) -> list[dict]:
    rows = []
    for item in sections:
        proposed = disposition(document, item["section"], item["title"])
        rows.append({
            "id": f"{document}:{item['section']}",
            "document": document,
            "section": item["section"],
            "title": item["title"],
            "page": item["page"],
            **proposed,
            "matrixRules": [],
            "atomization": "SECTION_LEVEL",
            "acceptance": "MISSING_EVIDENCE",
            "review": "NOT_REVIEWED",
        })
    return rows


def validate(rows: list[dict], sections: list[dict], document: str) -> dict:
    if len(rows) != len(sections):
        raise ValueError("Crosswalk does not cover every section: " + document)
    expected = {f"{document}:{item['section']}" for item in sections}
    actual = {row["id"] for row in rows}
    if expected != actual:
        raise ValueError("Crosswalk section ids differ from the TOC: " + document)
    for row in rows:
        if row["disposition"] == "COMPILER_RULE":
            if row["ownerStep"] not in STEPS or row["phase"] != "G2":
                raise ValueError("Invalid compiler step: " + row["id"])
        elif row["disposition"] in {"LATER_EXECUTION", "OUTSIDE_COMPILER"}:
            if row["ownerStep"] is not None or not row["phase"]:
                raise ValueError("Invalid later/outside disposition: " + row["id"])
        elif row["disposition"] == "EXPLANATORY":
            if row["ownerStep"] is not None or row["phase"] is not None:
                raise ValueError("Invalid explanatory disposition: " + row["id"])
        elif row["disposition"] != "AUDIT_REQUIRED":
            raise ValueError("Unknown disposition: " + row["id"])
        if not row.get("reason"):
            raise ValueError("Missing reason: " + row["id"])
        if row["atomization"] != "SECTION_LEVEL" or row["acceptance"] != "MISSING_EVIDENCE":
            raise ValueError("Section row claims atomization/acceptance: " + row["id"])
    return {"sections": len(rows), "auditRequired": sum(1 for row in rows if row["disposition"] == "AUDIT_REQUIRED"),
            "compilerRules": sum(1 for row in rows if row["disposition"] == "COMPILER_RULE")}


def apply_crosswalk(inventory: dict, rows: list[dict]) -> dict:
    by_section = {row["section"]: row for row in rows}
    for entry in inventory["entries"]:
        row = by_section.get(entry["section"])
        if row is None:
            entry["classification"] = "EXPLANATORY"
            entry["ownerStep"] = None
            entry["phase"] = None
            entry["matrixRules"] = []
            entry["acceptance"] = "NOT_APPLICABLE"
            entry["remainingCause"] = "Cover, title page, table of contents or other document apparatus; no compiler rule."
            entry["sectionDisposition"] = "EXPLANATORY"
            entry["atomization"] = "SECTION_LEVEL"
            continue
        entry["classification"] = row["disposition"]
        entry["ownerStep"] = row["ownerStep"]
        entry["phase"] = row["phase"]
        entry["matrixRules"] = list(row["matrixRules"])
        entry["acceptance"] = {
            "COMPILER_RULE": "SECTION_LEVEL_PENDING",
            "LATER_EXECUTION": "LATER_PHASE",
            "OUTSIDE_COMPILER": "OUTSIDE_COMPILER",
            "EXPLANATORY": "NOT_APPLICABLE",
            "AUDIT_REQUIRED": "MISSING_EVIDENCE",
        }[row["disposition"]]
        entry["remainingCause"] = row["reason"] + " Paragraph atomization and concrete acceptance remain open."
        entry["sectionDisposition"] = row["disposition"]
        entry["atomization"] = "SECTION_LEVEL"
    return inventory


def validate_classified(inventory: dict) -> dict:
    """Validate a section-level inventory without claiming atomization."""
    allowed = {"COMPILER_RULE", "LATER_EXECUTION", "OUTSIDE_COMPILER", "EXPLANATORY"}
    acceptance = {"COMPILER_RULE": "SECTION_LEVEL_PENDING", "LATER_EXECUTION": "LATER_PHASE",
                  "OUTSIDE_COMPILER": "OUTSIDE_COMPILER", "EXPLANATORY": "NOT_APPLICABLE"}
    counts: dict[str, int] = {}
    for document in inventory.get("documents", []):
        if document.get("validation", {}).get("atomization") != "PENDING":
            raise ValueError("Inventory does not declare atomization PENDING: " + document.get("document", "?"))
        for entry in document.get("entries", []):
            classification = entry.get("classification")
            if classification not in allowed:
                raise ValueError("Unclassified or unknown entry: " + entry.get("id", "?"))
            if entry.get("acceptance") != acceptance[classification]:
                raise ValueError("Acceptance does not match section-level disposition: " + entry["id"])
            if entry.get("atomization") != "SECTION_LEVEL":
                raise ValueError("Entry claims atomization: " + entry["id"])
            if classification == "COMPILER_RULE":
                if entry.get("ownerStep") not in STEPS or entry.get("phase") != "G2":
                    raise ValueError("Invalid compiler owner: " + entry["id"])
            elif classification in {"LATER_EXECUTION", "OUTSIDE_COMPILER"}:
                if entry.get("ownerStep") is not None or not entry.get("phase"):
                    raise ValueError("Invalid later/outside owner: " + entry["id"])
            elif entry.get("ownerStep") is not None or entry.get("phase") is not None:
                raise ValueError("Explanatory entry carries an owner: " + entry["id"])
            counts[classification] = counts.get(classification, 0) + 1
    if not counts:
        raise ValueError("Empty inventory")
    return {"entries": sum(counts.values()), "atomicRules": 0, "counts": counts,
            "acceptance": "SECTION_LEVEL_MAPPING_NOT_ACCEPTED", "atomization": "PENDING"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out-crosswalk", type=Path, required=True)
    parser.add_argument("--out-inventory", type=Path)
    parser.add_argument("--inventory", type=Path)
    args = parser.parse_args()
    for path in [args.out_crosswalk, args.out_inventory]:
        if path is not None and not path.resolve().is_relative_to(ROOT / "build"):
            parser.error("Outputs must be under build/")
    source = json.loads((ROOT / "docs/evidence/stabilization/g2-source-inventory.json").read_text())
    documents = []
    for norm in source["normDocuments"]:
        rows = build(norm["sections"], norm["document"])
        documents.append({"document": norm["document"], "sections": rows,
                          "validation": validate(rows, norm["sections"], norm["document"])})
    crosswalk = {"schemaVersion": "1.0", "status": "PROPOSED_WORKLIST_NOT_ACCEPTED",
                 "purpose": "Section-level owner assignment for A2-R4; no paragraph atomization or acceptance",
                 "documents": documents}
    args.out_crosswalk.parent.mkdir(parents=True, exist_ok=True)
    args.out_crosswalk.write_text(json.dumps(crosswalk, ensure_ascii=False, indent=2) + "\n")
    summary = {doc["document"]: doc["validation"] for doc in documents}
    if args.inventory and args.out_inventory:
        inventory = json.loads(args.inventory.read_text())
        by_doc = {doc["document"]: doc["sections"] for doc in documents}
        for doc in inventory["documents"]:
            apply_crosswalk(doc, by_doc[doc["document"]])
            doc["validation"] = {**doc["validation"], "sectionLevel": True,
                                 "atomization": "PENDING",
                                 "unclassified": sum(1 for e in doc["entries"] if e["classification"] == "UNCLASSIFIED")}
        args.out_inventory.parent.mkdir(parents=True, exist_ok=True)
        args.out_inventory.write_text(json.dumps(inventory, ensure_ascii=False, indent=2) + "\n")
        summary["inventory"] = str(args.out_inventory)
    print(json.dumps(summary))


if __name__ == "__main__":
    main()
