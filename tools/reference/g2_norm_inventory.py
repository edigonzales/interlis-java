#!/usr/bin/env python3
"""Read-only paragraph inventory for the G2 A2-R4 norm audit.

The inventory is a worklist, not a coverage or acceptance claim. It extracts
the exact page slices of both pinned PDFs, assigns stable IDs and hashes, and
keeps every paragraph explicitly UNCLASSIFIED until a later mapping entry names
its owner step, matrix rules, requirements and concrete acceptance.

PDF extraction is an authoring operation (pypdf); the structural helpers are
pure and unit-tested without pypdf. Outputs must stay under build/.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
MATRIX = "docs/evidence/stabilization/g2-rule-matrix.json"
SECTION_LINE = re.compile(r"^\s*(\d+(?:\.\d+){0,4})\s+(\S.*?)\s*$")
BLANK = re.compile(r"\n[ \t]*\n")


def digest(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def _normal_title(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", text.casefold())


def section_starts(pages: list[str], sections: list[dict]) -> dict[str, list[tuple[int, int, str]]]:
    """Return every body heading occurrence (page index, char offset, title).

    A TOC locator gives the first page. Body headings are found by their exact
    section number at a line start; the TOC title only disambiguates repeated
    numbers. Missing headings stay locator-only rather than being invented.
    """
    known = {item["section"] for item in sections}
    found: dict[str, list[tuple[int, int, str]]] = {}
    for page_index, text in enumerate(pages):
        offset = 0
        for line in text.splitlines(keepends=True):
            match = SECTION_LINE.match(line)
            if match:
                number, title = match.group(1), match.group(2).strip()
                if number in known:
                    found.setdefault(number, []).append((page_index, offset, title))
            offset += len(line)
    return found


def body_starts(pages: list[str], sections: list[dict]) -> dict[str, tuple[int, int, str, bool]]:
    """Resolve each TOC section to its body start, falling back to the locator."""
    raw = section_starts(pages, sections)
    result: dict[str, tuple[int, int, str, bool]] = {}
    for item in sections:
        number = item["section"]
        toc_page = max(0, item["page"] - 1)
        candidates = [entry for entry in raw.get(number, []) if entry[0] >= max(0, toc_page - 1)]
        wanted = _normal_title(item["title"])
        exact = next((entry for entry in candidates if _normal_title(entry[2]) == wanted), None)
        if exact is None:
            exact = next((entry for entry in candidates if wanted and wanted in _normal_title(entry[2])), None)
        if exact is not None:
            result[number] = (exact[0], exact[1], exact[2], True)
        else:
            result[number] = (toc_page, 0, item["title"], False)
    return result


def split_blocks(text: str, cuts=()) -> list[tuple[int, int, str]]:
    """Split a page into exact non-empty slices at blank lines and headings.

    The ranges are half-open slices of ``text``; whitespace-only separators are
    not blocks. This keeps every quote verifiable without normalising the PDF
    extraction.
    """
    points = {0, len(text)}
    for match in BLANK.finditer(text):
        points.add(match.start())
        points.add(match.end())
    points.update(offset for offset in cuts if 0 < offset < len(text))
    ordered = sorted(points)
    blocks: list[tuple[int, int, str]] = []
    for start, end in zip(ordered, ordered[1:]):
        block = text[start:end]
        if block.strip():
            blocks.append((start, end, block))
    return blocks


def build_inventory(pages: list[str], sections: list[dict], document: str) -> dict:
    """Build the paragraph worklist for one pinned document."""
    if not pages:
        raise ValueError("no pages")
    starts = body_starts(pages, sections)
    ordered = sorted((page, offset, section, title, exact)
                     for section, (page, offset, title, exact) in starts.items())
    entries: list[dict] = []
    section_at_page: dict[int, str | None] = {}
    for page_index, text in enumerate(pages):
        page_starts = [(offset, section, title, exact) for page, offset, section, title, exact in ordered
                       if page == page_index]
        cuts = [offset for offset, section, title, exact in page_starts if exact]
        blocks = split_blocks(text, cuts)
        if not blocks and text.strip():
            blocks = [(0, len(text), text)]
        for block_index, (start, end, block) in enumerate(blocks):
            # The section active at the block start; headings were split out.
            active = None
            for page, offset, section, title, exact in ordered:
                if (page, offset) <= (page_index, start):
                    active = (section, title)
                else:
                    break
            section, title = active if active else ("UNSECTIONED", None)
            entry_id = f"NORM:{document}:{section}:p{page_index + 1}:{block_index + 1:03d}"
            entries.append({
                "id": entry_id,
                "document": document,
                "section": section,
                "sectionTitle": title,
                "page": page_index + 1,
                "start": start,
                "end": end,
                "quote": block,
                "quoteSha256": digest(block),
                "pageSha256": digest(text),
                "classification": "UNCLASSIFIED",
                "ownerStep": None,
                "matrixRules": [],
                "requirements": [],
                "acceptance": "MISSING_EVIDENCE",
                "remainingCause": "Paragraph not yet atomized into a normative condition with owner step and acceptance.",
            })
        section_at_page[page_index + 1] = None
    for page in range(1, len(pages) + 1):
        active = [entry["section"] for entry in entries if entry["page"] == page]
        section_at_page[page] = active[0] if active else None
    ids = [entry["id"] for entry in entries]
    if len(ids) != len(set(ids)):
        raise ValueError("Duplicate paragraph id")
    return {
        "schemaVersion": "1.0",
        "purpose": "A2-R4 norm paragraph worklist; extraction and hash binding only, not coverage or acceptance",
        "acceptance": "MISSING_EVIDENCE",
        "document": document,
        "pageCount": len(pages),
        "sectionCount": len(sections),
        "entries": entries,
        "sections": sections,
        "sectionStarts": {section: {"page": page + 1, "offset": offset, "title": title, "exactHeading": exact}
                          for section, (page, offset, title, exact) in starts.items()},
        "pageSections": {str(page): section for page, section in section_at_page.items()},
        "remainingCause": "Every entry still needs a normative classification, owner step, matrix rule and positive/negative/model/query acceptance; section assignment needs review.",
    }


def validate_inventory(inventory: dict, pages: list[str]) -> dict:
    """Check hashes, slices, unique IDs and page coverage without judging content."""
    entries = inventory.get("entries", [])
    if not entries:
        raise ValueError("empty inventory")
    seen = set()
    per_page: dict[int, list[tuple[int, int]]] = {}
    for entry in entries:
        entry_id = entry["id"]
        if entry_id in seen:
            raise ValueError("Duplicate id: " + entry_id)
        seen.add(entry_id)
        page = entry["page"]
        if not 1 <= page <= len(pages):
            raise ValueError("Page outside document: " + entry_id)
        text = pages[page - 1]
        start, end = entry["start"], entry["end"]
        if not 0 <= start < end <= len(text) or text[start:end] != entry["quote"]:
            raise ValueError("Changed paragraph slice: " + entry_id)
        if digest(entry["quote"]) != entry["quoteSha256"] or digest(text) != entry["pageSha256"]:
            raise ValueError("Changed paragraph hash: " + entry_id)
        if entry["classification"] == "UNCLASSIFIED" and (
                entry["ownerStep"] is not None or entry["matrixRules"]
                or entry["acceptance"] != "MISSING_EVIDENCE"):
            raise ValueError("Unclassified entry carries acceptance: " + entry_id)
        per_page.setdefault(page, []).append((start, end))
    for page in range(1, len(pages) + 1):
        ranges = per_page.get(page, [])
        text = pages[page - 1]
        covered = [False] * len(text)
        for start, end in ranges:
            for index in range(start, end):
                covered[index] = True
        for index, char in enumerate(text):
            if not char.isspace() and not covered[index]:
                raise ValueError(f"Uncovered page text: page {page} offset {index}")
    return {"entries": len(entries), "pages": len(per_page), "unclassified": sum(
        1 for entry in entries if entry["classification"] == "UNCLASSIFIED")}


def load_pages(path: Path, expected_sha256: str) -> list[str]:
    """Read one pinned PDF; pypdf is imported only for this authoring step."""
    import pypdf
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != expected_sha256:
        raise ValueError("Changed norm PDF: " + str(path))
    pages = [page.extract_text() for page in pypdf.PdfReader(path).pages]
    if any(not page.strip() for page in pages):
        raise ValueError("Unextractable norm page: " + str(path))
    return pages


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--norm23", type=Path)
    parser.add_argument("--norm24", type=Path)
    args = parser.parse_args()
    out = args.out.resolve()
    if not out.is_relative_to(ROOT / "build"):
        parser.error("Inventory outputs must be under build/")
    if not args.norm23 or not args.norm24:
        parser.error("Both pinned norms are required")
    matrix = json.loads((ROOT / MATRIX).read_text())
    documents = []
    for path, version in [(args.norm23, "2.3"), (args.norm24, "2.4")]:
        pin = matrix["documents"][version]
        pages = load_pages(path, pin["sha256"])
        sections = json.loads((ROOT / "docs/evidence/stabilization/g2-source-inventory.json").read_text())[
            "normDocuments"]
        section_rows = next(row["sections"] for row in sections if row["document"] == version)
        inventory = build_inventory(pages, section_rows, version)
        inventory["pdfSha256"] = pin["sha256"]
        inventory["pdfUrl"] = pin["url"]
        inventory["validation"] = validate_inventory(inventory, pages)
        documents.append(inventory)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"documents": documents, "acceptance": "MISSING_EVIDENCE"}, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({document["document"]: {"pages": document["pageCount"], "sections": document["sectionCount"],
                                             "entries": len(document["entries"]),
                                             "unclassified": document["validation"]["unclassified"]}
                      for document in documents}))


if __name__ == "__main__":
    main()
