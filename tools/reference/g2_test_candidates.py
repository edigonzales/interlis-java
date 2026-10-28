#!/usr/bin/env python3
"""Build a read-only candidate index from R4 conditions/requirements to JVM tests.

Candidates are not evidence. Each entry records only a test class/method and a
keyword score so that the later condition execution can inspect the actual
assertions instead of guessing.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TESTS = ROOT / "interlis-compiler/src/test/java"
STOP = {"eine", "einer", "eines", "sind", "wird", "werden", "muss", "darf", "soll", "nicht",
        "auch", "oder", "und", "der", "die", "das", "des", "dem", "den", "für", "mit", "von",
        "bei", "als", "ist", "ein", "eine", "einer", "eines", "einem", "einen", "auf", "aus",
        "this", "that", "with", "from", "must", "shall"}
WORD = re.compile(r"[A-Za-zÄÖÜäöü]{4,}")
EXPAND = {
    "modelle": ["model"], "modell": ["model"], "themen": ["topic"], "thema": ["topic"],
    "klassen": ["class"], "klasse": ["class"], "strukturen": ["structure"], "struktur": ["structure"],
    "attribute": ["attribute"], "attribut": ["attribute"], "beziehungen": ["association", "role"],
    "beziehung": ["association", "role"], "wertebereiche": ["type", "domain"], "wertebereich": ["type", "domain"],
    "zeichenketten": ["text", "string"], "zeichenkette": ["text", "string"], "aufzaehlungen": ["enum"],
    "aufzählungen": ["enum"], "zahlen": ["numeric", "number"], "einheiten": ["unit"], "einheit": ["unit"],
    "metaobjekte": ["metadata"], "metadaten": ["metadata"], "funktionen": ["function"], "funktion": ["function"],
    "sichten": ["view"], "sicht": ["view"], "darstellung": ["graphic"], "darstellungen": ["graphic"],
    "konsistenzbedingungen": ["constraint"], "bedingungen": ["constraint"], "ausdrücke": ["expression"],
    "ausdruecke": ["expression"], "vererbung": ["inheritance", "extends"], "namensräume": ["namespace", "import"],
    "namensraeume": ["namespace", "import"], "koordinaten": ["coord"], "linienzüge": ["line", "polyline"],
    "linienzuege": ["line", "polyline"], "flächen": ["surface"], "flaechen": ["surface"], "rollen": ["role"],
    "referenzattribute": ["reference"], "übersetzungen": ["translation"], "uebersetzungen": ["translation"],
    "parameter": ["parameter"], "laufzeitparameter": ["parameter", "runtime"], "grafik": ["graphic"],
    "signaturen": ["signature"], "einzelflächen": ["surface"], "gebietseinteilungen": ["surface"],
    "zusammengesetzte": ["composed"], "abgeleitete": ["derived"], "basiseinheiten": ["unit"],
}


def digest(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def tokens(text: str) -> list[str]:
    return [word.casefold() for word in WORD.findall(text) if word.casefold() not in STOP]


def methods(text: str) -> list[tuple[int, int, str]]:
    found = []
    for match in re.finditer(r"@Test\s+(?:public\s+)?void\s+(\w+)\s*\(", text):
        start = match.start()
        index = text.find("{", match.end())
        if index < 0:
            continue
        depth = 0
        end = index
        while end < len(text):
            char = text[end]
            if char == '"':
                if text.startswith('"""', end):
                    end += 3
                    while end < len(text) and not text.startswith('"""', end):
                        end += 1
                    end += 3
                    continue
                end += 1
                while end < len(text):
                    if text[end] == "\\":
                        end += 2
                    elif text[end] == '"':
                        end += 1
                        break
                    else:
                        end += 1
                continue
            if char == "'":
                end += 1
                while end < len(text):
                    if text[end] == "\\":
                        end += 2
                    elif text[end] == "'":
                        end += 1
                        break
                    else:
                        end += 1
                continue
            if text.startswith("//", end):
                end = text.find("\n", end)
                end = len(text) if end < 0 else end
                continue
            if text.startswith("/*", end):
                end = text.find("*/", end + 2)
                end = len(text) if end < 0 else end + 2
                continue
            if char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    found.append((start, end + 1, match.group(1)))
                    break
            end += 1
    return found


def load_tests() -> list[dict]:
    tests = []
    for path in sorted(TESTS.rglob("*.java")):
        text = path.read_text()
        package = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
        if package is None:
            continue
        for start, end, name in methods(text):
            body = text[start:end]
            tests.append({"suite": package.group(1) + "." + path.stem,
                          "source": str(path.relative_to(ROOT)), "method": name,
                          "methodStart": start, "methodEnd": end,
                          "methodSha256": digest(body), "body": body.casefold()})
    return tests


def candidates(text: str, tests: list[dict], limit: int = 3) -> list[dict]:
    words = tokens(text)
    words = list(dict.fromkeys(words + [expanded for word in words for expanded in EXPAND.get(word, [])]))
    if not words:
        return []
    scored = []
    for test in tests:
        score = sum(min(test["body"].count(word), 20) for word in words)
        if score:
            scored.append((score, test))
    scored.sort(key=lambda item: (-item[0], item[1]["suite"], item[1]["method"]))
    return [{"suite": test["suite"], "source": test["source"], "method": test["method"],
             "methodStart": test["methodStart"], "methodEnd": test["methodEnd"],
             "methodSha256": test["methodSha256"], "score": score, "status": "CANDIDATE_ONLY"}
            for score, test in scored[:limit]]


def build() -> dict:
    tests = load_tests()
    conditions = json.loads((ROOT / "docs/evidence/stabilization/g2-norm-conditions.json").read_text())
    contracts = json.loads((ROOT / "docs/evidence/stabilization/g2-spec-contract-inventory.json").read_text())
    condition_rows = []
    for document in conditions["documents"]:
        for condition in document["conditions"]:
            if condition["kind"] != "COMPILER_RULE":
                continue
            condition_rows.append({"id": condition["id"], "section": condition["section"],
                                   "title": condition["sectionTitle"], "ownerStep": condition["ownerStep"],
                                   "candidates": candidates(condition["sectionTitle"] + " " + " ".join(condition["matrixRules"]) + " " + condition.get("quote", "")[:1200], tests)})
    requirements = []
    for row in contracts["numbered"]:
        if row["status"] != "G2_STEP_PLANNED":
            continue
        requirements.append({"id": row["id"], "ownerStep": row["ownerStep"],
                             "candidates": candidates(row["requirement"], tests)})
    return {"schemaVersion": "1.0", "status": "CANDIDATE_ONLY",
            "purpose": "Keyword candidates only; no assertion, execution or acceptance claim",
            "testMethods": len(tests), "conditions": condition_rows, "requirements": requirements}


def validate(data: dict) -> dict:
    if not data["conditions"] or not data["requirements"]:
        raise ValueError("Empty candidate index")
    known = {(test["source"], test["method"]): test["methodSha256"] for test in load_tests()}
    for section in ("conditions", "requirements"):
        for row in data[section]:
            for candidate in row["candidates"]:
                if candidate["status"] != "CANDIDATE_ONLY":
                    raise ValueError("Candidate claims acceptance: " + row["id"])
                key = (candidate["source"], candidate["method"])
                if key not in known:
                    raise ValueError("Unknown candidate method: " + row["id"])
                if known[key] != candidate["methodSha256"]:
                    raise ValueError("Changed candidate method: " + row["id"])
    return {"testMethods": data["testMethods"], "conditions": len(data["conditions"]),
            "requirements": len(data["requirements"]),
            "withCandidate": sum(1 for row in data["conditions"] + data["requirements"] if row["candidates"]),
            "acceptance": "CANDIDATE_ONLY"}


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
