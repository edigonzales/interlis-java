#!/usr/bin/env python3
"""Prepare the 18 existing normative reference exceptions for user revalidation.

The packet never changes an expectation. It binds each case to both pinned norm
versions and the R4 paragraph inventory, keeps the current own/reference
expectations separate, and leaves the decision explicitly PENDING_USER.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SECTION = re.compile(r"\b(\d+(?:\.\d+)*)\b")


def digest(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def other_version(version: str) -> str:
    return "2.4" if version == "2.3" else "2.3"


def versioned_section(section: str, source_version: str, target_version: str) -> str:
    if section.startswith("APPENDIX:"):
        return section
    parts = section.split(".")
    if source_version == "2.3" and target_version == "2.4" and parts[0] == "2":
        parts[0] = "3"
    elif source_version == "2.4" and target_version == "2.3" and parts[0] == "3":
        parts[0] = "2"
    return ".".join(parts)


def sections_from_text(text: str) -> list[str]:
    return list(dict.fromkeys(SECTION.findall(text)))


HEADER = re.compile(r"(INTE\s*RLIS|E-Government-Standards|Verein eCH|Seite \d+ von|eCH-0031|www\.interlis\.ch)", re.I)


def display_quote(quote: str) -> str:
    lines = [line for line in quote.splitlines()
             if not HEADER.search(line) and not re.fullmatch(r"\s*Seite\s+\d+(\s+von\s+\d+)?\s*", line)
             and not re.fullmatch(r"\s*\d+\s*", line)]
    cleaned = "\n".join(lines).strip()
    return cleaned or quote.strip()


def paragraph_excerpts(inventory_doc: dict, section: str, reason: str, limit: int = 6) -> list[dict]:
    entries = [entry for entry in inventory_doc["entries"] if entry["section"] == section]
    entries.sort(key=lambda entry: (entry["page"], entry["start"]))
    keywords = [word.casefold() for word in re.findall(r"[A-Za-zÄÖÜäöü]{4,}", reason)]
    keywords += ["oid", "basket", "contracted", "hiding", "no oid", "tolerance", "präzision",
                 "laufzeit", "runtime", "rolle", "role", "association", "toleranz"]
    selected = []
    for entry in entries:
        display = display_quote(entry["quote"])
        if len(display) < 20:
            continue
        low = display.casefold()
        if len(selected) < 2 or any(keyword in low for keyword in keywords):
            selected.append((entry, display))
        if len(selected) >= limit:
            break
    return [{"id": entry["id"], "page": entry["page"], "quote": entry["quote"],
             "quoteSha256": entry["quoteSha256"], "displayQuote": display,
             "selectedBy": "first readable paragraphs plus reason keywords"} for entry, display in selected]


def build(expectations: dict, inventory: dict, crosswalk: dict) -> dict:
    inv = {doc["document"]: doc for doc in inventory["documents"]}
    cw = {doc["document"]: doc for doc in crosswalk["documents"]}
    cases = []
    for case in expectations["cases"]:
        version = case["languageVersion"]
        known_sections = {row["section"] for row in cw[version]["sections"]}
        sections = [section for section in sections_from_text(case["norm"]["sections"])
                    if section in known_sections or versioned_section(section, version, other_version(version)) in
                    {row["section"] for row in cw[other_version(version)]["sections"]}]
        norm_evidence = []
        for section in sections:
            for document in (version, other_version(version)):
                target = section if document == version else versioned_section(section, version, document)
                row = next((row for row in cw[document]["sections"] if row["section"] == target), None)
                if row is None:
                    raise ValueError(f"Missing crosswalk section {document}:{target} for {case['testId']}")
                norm_evidence.append({
                    "document": document,
                    "section": target,
                    "title": row["title"],
                    "page": row["page"],
                    "disposition": row["disposition"],
                    "ownerStep": row["ownerStep"],
                    "paragraphs": paragraph_excerpts(inv[document], target, case["norm"]["reason"]),
                })
        cases.append({
            "testId": case["testId"],
            "owner": case.get("owner"),
            "languageVersion": version,
            "step": next((item["step"] for item in json.loads((ROOT / "docs/evidence/stabilization/g2-rule-matrix.json").read_text())["normativeDecisionQueue"]["cases"]
                          if item["testId"] == case["testId"]), None),
            "referenceExpectation": case["referenceExpectation"],
            "ownExpectation": case["ownExpectation"],
            "normSections": case["norm"]["sections"],
            "normReason": case["norm"]["reason"],
            "normEvidence": norm_evidence,
            "sources": case.get("sources", []),
            "positiveControls": case.get("positiveControls", []),
            "allowedDiagnostics": case.get("allowedDiagnostics", []),
            "recommendation": "KEEP_EXISTING_PENDING_USER",
            "recommendationBasis": "Existing project decision and the recorded norm reason remain unchanged until the user revalidates both versions.",
            "userDecision": "PENDING_USER_REVALIDATION",
            "decisionOptions": ["KEEP_EXISTING", "CHANGE_TO_REFERENCE", "OTHER_WITH_NORM_REASON"],
            "review": "MISSING_EVIDENCE",
        })
    return {
        "schemaVersion": "1.0",
        "status": "PENDING_USER_REVALIDATION",
        "purpose": "Prepare the 18 existing normative reference exceptions for explicit user revalidation; no expectation is changed.",
        "source": "docs/evidence/conformance/normative-expectations.json",
        "sourceSha256": hashlib.sha256((ROOT / "docs/evidence/conformance/normative-expectations.json").read_bytes()).hexdigest(),
        "normalization": "No change of previous authorizations, own expectations, reference outcomes or XFAIL status.",
        "cases": cases,
    }


def validate(packet: dict, expectations: dict, inventory: dict, crosswalk: dict) -> dict:
    expected = {case["testId"]: case for case in expectations["cases"]}
    if len(packet.get("cases", [])) != len(expected):
        raise ValueError("Decision packet does not cover all cases")
    for case in packet["cases"]:
        original = expected.get(case["testId"])
        if original is None:
            raise ValueError("Unknown case: " + case["testId"])
        if case["ownExpectation"] != original["ownExpectation"] or case["referenceExpectation"] != original["referenceExpectation"]:
            raise ValueError("Decision packet changed an expectation: " + case["testId"])
        if case["userDecision"] != "PENDING_USER_REVALIDATION":
            raise ValueError("Decision packet claims a user decision: " + case["testId"])
        if not case["normEvidence"]:
            raise ValueError("Missing norm evidence: " + case["testId"])
        documents = {item["document"] for item in case["normEvidence"]}
        if documents != {"2.3", "2.4"}:
            raise ValueError("Decision packet lacks both norm versions: " + case["testId"])
        for evidence in case["normEvidence"]:
            row = next((row for row in crosswalk["documents"] if row["document"] == evidence["document"]), None)
            if row is None or not any(section["section"] == evidence["section"] for section in row["sections"]):
                raise ValueError("Unknown norm evidence section: " + case["testId"])
            for paragraph in evidence["paragraphs"]:
                if digest(paragraph["quote"]) != paragraph["quoteSha256"]:
                    raise ValueError("Changed norm excerpt: " + case["testId"])
    return {"cases": len(packet["cases"]), "status": packet["status"], "expectationsChanged": False}


def markdown(packet: dict) -> str:
    lines = ["# Normative Referenzausnahmen – Nutzerentscheidung", "",
             "Status: **PENDING_USER_REVALIDATION**. Keine Erwartung wurde geändert.", "",
             "| Testfall | Schritt | Version | Eigene | Referenz | Normstellen | Empfehlung |",
             "|---|---|---|---|---|---|---|"]
    for case in packet["cases"]:
        lines.append(f"| `{case['testId']}` | {case['step'] or '–'} | {case['languageVersion']} | "
                     f"{case['ownExpectation']} | {case['referenceExpectation']['outcome']} | "
                     f"{case['normSections']} | {case['recommendation']} |")
    lines += ["", "## Begründungen", ""]
    for case in packet["cases"]:
        lines.append(f"### `{case['testId']}`")
        lines.append("")
        lines.append(case["normReason"])
        lines.append("")
        for evidence in case["normEvidence"][:2]:
            for paragraph in evidence["paragraphs"][:1]:
                lines.append(f"> {paragraph['displayQuote'][:500]}")
                lines.append("")
        lines.append("Entscheidung: **PENDING_USER_REVALIDATION**.")
        lines.append("")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--markdown", type=Path, required=True)
    args = parser.parse_args()
    for path in (args.out, args.markdown):
        if not path.resolve().is_relative_to(ROOT / "build"):
            parser.error("Outputs must be under build/")
    expectations = json.loads((ROOT / "docs/evidence/conformance/normative-expectations.json").read_text())
    inventory = json.loads((ROOT / "docs/evidence/stabilization/g2-norm-inventory.json").read_text())
    crosswalk = json.loads((ROOT / "docs/evidence/stabilization/g2-norm-crosswalk.json").read_text())
    packet = build(expectations, inventory, crosswalk)
    packet["validation"] = validate(packet, expectations, inventory, crosswalk)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.markdown.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(packet, ensure_ascii=False, indent=2) + "\n")
    args.markdown.write_text(markdown(packet))
    print(json.dumps(packet["validation"]))


if __name__ == "__main__":
    main()
