#!/usr/bin/env python3
"""Read-only source inventory for the G2 rule audit.

This is a discovery/checking aid, not a coverage or acceptance generator. Scan
output belongs under build/. In particular, a grammar alternative or a quoted
specification requirement is not evidence that the compiler implements it.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

import g2_predefined

ROOT = Path(__file__).resolve().parents[2]
GRAMMARS = ["interlis-compiler/src/main/antlr/InterlisParser.g4",
            "interlis-compiler/src/main/antlr/InterlisLexer.g4"]
BUILTINS = "interlis-compiler/src/main/java/guru/interlis/compiler/internal/BuiltinFunctions.java"
MATRIX = "docs/evidence/stabilization/g2-rule-matrix.json"

# Ownership of semantic follow-up; B3 owns the structural syntax audit for all
# alternatives, including nested alternatives in another family's production.
SEMANTIC_RULES = {
    "B2": "modeldef topicRef classRef structureRef classOrStructureRef domainRef unitRef viewRef graphicRef viewableRef associationRef",
    "B3": "interlis2def definitions",
    "C1": "unitDef derivedUnit composedUnit numeric numericType",
    "C2": "basketOidRule objectOidRule oidDomainRef oIDType decConst numericConst",
    "C3": "attrTypeDef attrType type baseType textType textConst enumTreeValueType enumeration enumElement enumerationConst alignmentType booleanType refSys formattedType formatDef baseAttrRef formattedConst dateTimeType coordinateType rotationDef blackboxType classType attributeType lineType lineForm lineFormType controlPoints intersectionDef cardinality restrictedClassOrAssRef classOrAssociationRef restrictedStructureRef restrictedClassOrStructureRef",
    "C4": "domainDef",
    "D1": "topicDef classDef structureDef classOrStructureDef",
    "D2": "referenceAttr associationDef roleDef",
    "D3": "attributeDef",
    "E1": "objectOrAttributePath attributePath pathEl associationPath attributeRef classConst attributePathConst",
    "E2": "constant expression term term0 term1 term2 predicate relation factor functionCall argument functionDef argumentDef argumentType runTimeParameterDef",
    "E3": "constraintDef mandatoryConstraint plausibilityConstraint existenceConstraint uniquenessConstraint globalUniqueness uniqueEl localUniqueness setConstraint constraintsDef",
    "F1": "viewDef formationDef projection join union aggregation inspection renamedViewableRef baseExtensionDef selection viewAttributes viewAttribute",
    "F3": "genericRef contextDef contextMapping lineFormTypeDef metaDataBasketDef metaObjectGroup metaDataBasketRef metaObjectRef parameterDef graphicDef drawingRule condSignParamAssignment signParamAssignment enumAssignment enumRange",
}
RULE_STEPS = {name: step for step, names in SEMANTIC_RULES.items() for name in names.split()}


def legacy_step(identifier: str) -> str:
    """Assign historical IDs to the agreed delivery sequence, without accepting them."""
    exact = {"XMLDate.layout": "C3", "XMLTime.layout": "C3", "CLASS.modelScopeAbstraction": "B2",
             "DOMAIN.final": "D1", "DOMAIN.mandatoryTypeInheritance": "C4", "DOMAIN.classValueConstraints": "C3",
             "TYPE.identicalImportedOid": "C2", "BINDING.oidTypeReferences": "C2", "TYPE.requiredDomainQueries": "C4",
             "INHERITANCE.predefinedAttributes": "D3", "NUMERIC.literalBudgets": "B3",
             "NUMERIC.scaledBoundaryMantissa": "C2", "SYNTAX.scaledBoundaryRangeAndBudget": "B3",
             "CONST.numericUnits": "C1", "CONST.symbolicNumbers": "C2", "CONST.contextualEnumFormat": "C3",
             "INSPECTION.lineCoordMemberAbstraction": "C3",
             "SEM:ModelRules.checkEndName": "B3", "SEM:NumericTypeRules.checkRestriction": "C4"}
    plain = identifier.split("(")[0]
    if plain in exact:
        return exact[plain]
    if identifier.startswith("SYN:"):
        return RULE_STEPS[identifier[4:]]
    if identifier.startswith("RES-"):
        return ["H2", "H1", "H1", "H1", "H1", "H3", "H3", "H3", "H2", "H2", "H2", "H3"][int(identifier[4:]) - 1]
    if identifier.startswith("SEM:"):
        groups = {"ModelRules": "F2", "TopicRules": "D2", "ModifierRules": "D1", "InheritanceChecker": "D1",
                  "AttributeOverrideChecker": "D3", "NumericTypeRules": "C3", "UnitRules": "C1",
                  "EnumerationRules": "C3", "ReferenceTypeRules": "D2", "CollectionRules": "C3",
                  "GeometryTypeRules": "C3", "FormattedTypeRules": "C3", "GenericBindingRules": "F3",
                  "AssociationRules": "D2", "OidRules": "C2", "ConstraintChecker": "E3",
                  "ViewChecker": "F1", "TranslationChecker": "F2", "GraphicChecker": "F3"}
        return groups[identifier[4:].split(".")[0]]
    groups = {"TRANSLATION": "F2", "GEOMETRY": "C3", "GRAPHIC": "F3", "INSPECTION": "F1", "MODEL": "B2",
              "PROFILE": "B3", "PREDEFINED": "B1", "BINDING": "B2", "RECOVERY": "B3", "MODIFIER": "D1",
              "TYPE": "C3", "UNIT": "C1", "QUERY": "C4", "TEXT": "C3", "STRUCTURE": "C3", "COLLECTION": "C3",
              "INHERITANCE": "D1", "EXTENSION": "C4", "FUNCTION": "E2", "EXPRESSION": "E2", "BUILTIN": "E2",
              "SYNTAX": "B3", "LEXICAL": "B3", "BUILD": "B3", "SOURCE_META": "F3", "CONSTRAINT": "E3",
              "DECLARATION": "C4", "NUMERIC": "C1"}
    return groups[re.split(r"[.:]", identifier)[0]]


def digest(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def mask_comments(text: str) -> str:
    """Keep offsets and newlines; do not confuse ANTLR literals with comments."""
    result = list(text)
    index = 0
    while index < len(text):
        if text[index] in "'[":
            end = "'" if text[index] == "'" else "]"
            index += 1
            closed = False
            while index < len(text):
                if text[index] == "\\":
                    index += 2
                elif text[index] == end:
                    index += 1
                    closed = True
                    break
                else:
                    index += 1
            if not closed:
                raise ValueError("Unclosed grammar literal or character set")
        elif text.startswith("//", index) or text.startswith("/*", index):
            end = text.find("\n", index) if text.startswith("//", index) else text.find("*/", index + 2)
            if end < 0:
                if text.startswith("/*", index):
                    raise ValueError("Unclosed grammar comment")
                end = len(text)
            elif text.startswith("/*", index):
                end += 2
            for position in range(index, end):
                if text[position] != "\n":
                    result[position] = " "
            index = end
        else:
            index += 1
    return "".join(result)


TOKEN = re.compile(r"'(?:\\.|[^'\\])*'|\[(?:\\.|[^\]\\])*\]|\w+|->|\S")
HEADER = re.compile(r"^\s*(?:fragment\s+)?(\w+)\s*:\s*", re.M)


def grammar_inventory(text: str, source: str, lexer: bool) -> list[dict]:
    masked = mask_comments(text)
    rules = []
    consumed = 0
    for match in HEADER.finditer(masked):
        if match.start() < consumed:
            continue
        name = match[1]
        tokens = []
        for token in TOKEN.finditer(masked, match.end()):
            if token[0] == ";":
                consumed = token.end()
                break
            tokens.append(token)
        else:
            raise ValueError("Unterminated grammar rule: " + name)
        if not lexer and name not in RULE_STEPS:
            raise ValueError("Unassigned semantic grammar production: " + name)
        # Lexer commands are not EBNF. Their literal source remains in the rule.
        end = next((i for i, token in enumerate(tokens) if token[0] == "->"), len(tokens))
        tokens = tokens[:end]
        occurrences = []

        def occurrence(kind: str, path: str, first: int, last: int, **extra) -> None:
            start = tokens[first].start() if first < len(tokens) else tokens[-1].end() if tokens else match.end()
            finish = tokens[last - 1].end() if last > first else start
            fragment = text[start:finish]
            occurrences.append({"id": f"GRAMMAR:{'lexer' if lexer else 'parser'}:{name}:{path}",
                                "kind": kind, "start": start, "end": finish,
                                "line": text.count("\n", 0, start) + 1,
                                "text": fragment, "sha256": digest(fragment), **extra})

        def block(first: int, last: int, path: str) -> None:
            depth = 0
            splits = [first]
            groups = []
            opening = None
            for i in range(first, last):
                token = tokens[i][0]
                if token == "(":
                    if depth == 0:
                        opening = i
                    depth += 1
                elif token == ")":
                    depth -= 1
                    if depth < 0:
                        raise ValueError("Unbalanced grammar group: " + name)
                    if depth == 0:
                        groups.append((opening, i))
                elif token == "|" and depth == 0:
                    splits.append(i + 1)
            if depth:
                raise ValueError("Unclosed grammar group: " + name)
            for i, start in enumerate(splits):
                finish = splits[i + 1] - 1 if i + 1 < len(splits) else last
                occurrence("ALTERNATIVE", f"{path}/alt{i + 1}", start, finish)
            for i, (start, finish) in enumerate(groups):
                block(start + 1, finish, f"{path}/group{i + 1}")
            # Quantifiers apply to a group, token, literal or character set.
            depth = 0
            for i in range(first, last):
                token = tokens[i][0]
                if token == "(":
                    depth += 1
                elif token == ")":
                    depth -= 1
                if token in ["?", "*", "+"] and depth == 0:
                    if i > first and tokens[i - 1][0] in ["?", "*", "+"]:
                        continue  # nongreedy suffix of the preceding quantifier
                    atom = next((a for a, b in groups if b == i - 1), i - 1)
                    if atom > first and tokens[atom - 1][0] == "~":
                        atom -= 1
                    if atom < first:
                        raise ValueError("Quantifier without an atom: " + name)
                    occurrence({"?": "OPTIONAL", "*": "ZERO_OR_MORE", "+": "ONE_OR_MORE"}[token],
                               f"{path}/quantifier{sum(1 for t in tokens[first:i] if t[0] in ['?', '*', '+']) + 1}",
                               atom, i + 2 if i + 1 < last and tokens[i + 1][0] == "?" else i + 1,
                               nongreedy=i + 1 < last and tokens[i + 1][0] == "?")

        block(0, len(tokens), "root")
        rules.append({"id": f"{'LEX' if lexer else 'SYN'}:{name}", "production": name,
                      "source": source, "line": text.count("\n", 0, match.start(1)) + 1,
                      "sha256": digest(text[match.start(1):consumed]), "syntaxStep": "B3",
                      "semanticStep": "B3" if lexer else RULE_STEPS[name],
                      "occurrences": occurrences})
    if not rules:
        raise ValueError("Empty grammar inventory: " + source)
    return rules


def spec_requirements(text: str, source: str) -> list[dict]:
    """Keep the original IDs and acceptance text; no inference from the index."""
    result = []
    for line, value in enumerate(text.splitlines(), 1):
        cells = [part.strip() for part in value.strip().strip("|").split("|")]
        if (len(cells) == 4 and re.fullmatch(r"[A-Z]+-\d{3}", cells[0])
                and not cells[0].startswith("ADR-")):
            result.append(dict(id=cells[0], requirement=cells[1], phase=cells[2],
                               acceptance=cells[3], source=source, line=line,
                               sourceLineSha256=digest(value)))
    return result


def toc_sections(pages: list[str]) -> list[dict]:
    """Extract locators, never normative rules, from the pinned printed TOCs.

    All entries deliberately require a separate body/paragraph audit. A TOC
    heading does not say whether a condition is normative or in compiler scope.
    """
    sections = []
    for text in pages[1:6]:
        for line in text.splitlines():
            match = re.match(r"^\s*([1-4](?:\.\d+)*)\s+(.+?)\.{2,}[ .]*(\d+)\s*$", line)
            if match:
                sections.append(dict(section=match[1], title=match[2].strip(), page=int(match[3])))
    toc = " ".join(" ".join(pages[1:6]).split())
    for match in re.finditer(r"Anhang ([A-Z])\s+(.+?)(?:\.\s*){2,}(\d+)", toc):
        sections.append(dict(section="APPENDIX:" + match[1], title=match[2].strip(), page=int(match[3])))
    if not sections or len({s["section"] for s in sections}) != len(sections):
        raise ValueError("Missing or duplicate TOC locators")
    if any(not 1 <= s["page"] <= len(pages) for s in sections):
        raise ValueError("Norm TOC page outside the document")
    return sections


def check_norm_excerpts(pages: list[str], version: str, clauses: list[dict]) -> list[str]:
    """Verify exact manual excerpts; this does not approve their interpretation."""
    checked = []
    for clause in clauses:
        reference = clause["norm"].get(version)
        if reference is None:
            continue
        page = reference["page"]
        if reference["document"] != version or not 1 <= page <= len(pages):
            raise ValueError("Wrong norm excerpt document/page: " + clause["id"])
        text = pages[page - 1]
        start, end = reference["excerptStart"], reference["excerptEnd"]
        if (not 0 <= start < end <= len(text) or digest(text) != reference["extractedPageSha256"]
                or text[start:end] != reference["quote"] or digest(reference["quote"]) != reference["quoteSha256"]):
            raise ValueError("Changed norm excerpt: " + clause["id"])
        checked.append(clause["id"])
    return checked


def norm_inventory(path: Path, version: str, expected: dict, clauses: list[dict]) -> dict:
    # PDF extraction is an authoring operation, not a compiler/build dependency.
    # Use the explicitly recorded pypdf version; stdlib grammar tests need none.
    import pypdf
    actual_sha = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual_sha != expected["sha256"]:
        raise ValueError("Changed norm PDF: " + version)
    pages = [page.extract_text() for page in pypdf.PdfReader(path).pages]
    if any(not page.strip() for page in pages):
        raise ValueError("Unextractable norm page: " + version)
    sections = toc_sections(pages)
    return dict(document=version, sha256=actual_sha, url=expected["url"],
                extractor="pypdf " + pypdf.__version__, pageCount=len(pages),
                extractedPageHashes=[digest(text) for text in pages], sections=sections,
                checkedExcerptIds=check_norm_excerpts(pages, version, clauses),
                acceptance="MISSING_EVIDENCE",
                remainingCause="TOC locators only. Body paragraphs, appendices and each compiler-relevant condition still need individual rule/step/assertion mapping; none is excluded or accepted by this scan.")


def matrix_mappings(root: Path, matrix: dict, requirements: list[dict]) -> dict:
    """Bind the actual assignments/assertions, including explicit case-link gaps.

    Structural checks cannot decide the correctness of a normative assertion.
    Persisting every field makes such changes visible to the separate review.
    """
    legacy = []
    for row in matrix["rules"]:
        expected = legacy_step(row["id"])
        if row.get("step") != expected:
            raise ValueError("Matrix step differs from fixed assignment: " + row["id"])
        legacy.append({"id": row["id"], "step": row["step"], "historicalState": row["state"],
                       "independentAcceptance": row["independentAcceptance"],
                       "mappingSha256": digest(json.dumps(row, ensure_ascii=False, sort_keys=True,
                                                          separators=(",", ":")))})
    atoms = copy.deepcopy(matrix.get("atomicRules", []))
    valid_requirements = {row["id"] for row in requirements}
    valid_steps = {"B1", "B2", "B3", "C1", "C2", "C3", "C4", "D1", "D2", "D3",
                   "E1", "E2", "E3", "F1", "F2", "F3", "G1", "G2", "G3", "H1", "H2", "H3"}
    ids = [row["id"] for row in atoms]
    if len(ids) != len(set(ids)):
        raise ValueError("Duplicate atomic rule")
    links = []
    sources = {}
    assertion_links = []
    for row in atoms:
        if (row["step"] not in valid_steps or not row["requirements"]
                or not set(row["requirements"]) <= valid_requirements
                or set(row["acceptance"]) != {"positive", "negative", "modelOrQuery"}
                or not all(isinstance(value, str) and value.strip() for value in row["acceptance"].values())
                or not row["norm"] or not set(row["norm"]) <= {"2.3", "2.4"}):
            raise ValueError("Incomplete atomic rule mapping: " + row["id"])
        if not row["candidateTests"]:
            links.append({"ruleId": row["id"], "status": "MISSING_CASE_LINK"})
        for link in row["candidateTests"]:
            classname, _, method = link.partition(".")
            paths = sorted(root.glob("interlis-*/src/test/java/**/" + classname + ".java"))
            if len(paths) != 1:
                raise ValueError("Missing or ambiguous candidate test: " + link)
            path = paths[0]
            text = path.read_text()
            methods = re.findall(r"^\s*@Test\s+(?:public\s+)?void\s+(\w+)\s*\(", text, re.M)
            if method and method not in methods:
                raise ValueError("Missing candidate @Test method: " + link)
            source = str(path.relative_to(root))
            sources[source] = digest(text)
            links.append({"ruleId": row["id"], "link": link, "source": source,
                          "status": "METHOD_CANDIDATE" if method else "CLASS_ONLY_NOT_AN_ASSERTION"})
        for evidence in row.get("observedAssertions", []):
            # Versioned metadata and test source can be checked on a fresh
            # checkout. Raw build outputs are checked separately at review/gate
            # time; their absence here must not make ordinary `check` nonportable.
            path = (root / evidence["source"]).resolve()
            if not path.is_relative_to((root / "interlis-compiler/src/test/java").resolve()):
                raise ValueError("Assertion source outside compiler tests: " + row["id"])
            text = path.read_text()
            start, end = evidence["methodStart"], evidence["methodEnd"]
            method_text = text[start:end]
            classname, separator, method = evidence["candidateTest"].partition(".")
            package = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
            if (not separator or path.name != classname + ".java" or not 0 <= start < end <= len(text)
                    or digest(text) != evidence["sourceSha256"] or digest(method_text) != evidence["methodSha256"]
                    or not re.search(r"@Test\s+(?:public\s+)?void\s+" + re.escape(method) + r"\s*\(", method_text)
                    or package is None or evidence["jvmSuite"] != package[1] + "." + classname
                    or not evidence["assertionFragments"]
                    or any(fragment not in method_text for fragment in evidence["assertionFragments"])):
                raise ValueError("Changed test assertion evidence: " + row["id"])
            report = (root / "docs/evidence/stabilization" / evidence["runEvidence"]).resolve()
            if not report.is_relative_to((root / "docs/evidence/stabilization").resolve()):
                raise ValueError("Assertion run evidence outside evidence directory: " + row["id"])
            run = json.loads(report.read_text())["freshSelectedRun"]
            matching = [item for item in run["reports"] if item["file"] == evidence["jvmXml"]
                        and item["sha256"] == evidence["jvmXmlSha256"]
                        and item["suite"] == evidence["jvmSuite"]
                        and method + "()" in item["tests"]
                        and item["failures"] == item["errors"] == item["skipped"] == "0"]
            xml_path = Path(evidence["jvmXml"])
            if (run["processExitCode"] != 0 or len(matching) != 1 or xml_path.is_absolute()
                    or ".." in xml_path.parts or not xml_path.parts or xml_path.parts[0] != "build"
                    or evidence["extent"] != "PARTIAL" or not evidence["remainingGap"]):
                raise ValueError("Invalid partial assertion run evidence: " + row["id"])
            sources[evidence["source"]] = evidence["sourceSha256"]
            assertion_links.append({"ruleId": row["id"], "candidateTest": evidence["candidateTest"],
                                    "runEvidence": evidence["runEvidence"], "jvmXml": evidence["jvmXml"],
                                    "status": "PARTIAL_SOURCE_AND_RECORDED_RUN_METADATA",
                                    "rawOutputCheck": "REQUIRED_SEPARATELY_FOR_REVIEW"})
    return dict(legacyRules=legacy, atomicRuleMappings=atoms, candidateTestLinks=links,
                candidateTestSourceHashes=dict(sorted(sources.items())), observedAssertionLinks=assertion_links)


def check_raw_assertion_evidence(root: Path, matrix: dict) -> list[dict]:
    """Require the archived testcase, not just its versioned metadata.

    This is opt-in for review/gates; ordinary source checks remain usable on a
    clean checkout without generated build reports. A missing, changed, skipped
    or failing raw testcase is never a successful review evidence check.
    """
    result = []
    for row in matrix["atomicRules"]:
        for evidence in row.get("observedAssertions", []):
            relative = Path(evidence["jvmXml"])
            if relative.is_absolute() or ".." in relative.parts or not relative.parts or relative.parts[0] != "build":
                raise ValueError("Unsafe raw testcase path: " + row["id"])
            path = (root / relative).resolve()
            if not path.is_relative_to((root / "build").resolve()) or not path.is_file():
                raise ValueError("Missing raw testcase report: " + row["id"])
            if hashlib.sha256(path.read_bytes()).hexdigest() != evidence["jvmXmlSha256"]:
                raise ValueError("Changed raw testcase report: " + row["id"])
            xml = ET.parse(path).getroot()
            method = evidence["candidateTest"].partition(".")[2] + "()"
            matches = [case for case in xml.findall("testcase") if case.get("name") == method]
            if (xml.get("name") != evidence["jvmSuite"] or len(matches) != 1
                    or matches[0].get("classname") != evidence["jvmSuite"]
                    or any(matches[0].find(kind) is not None for kind in ["failure", "error", "skipped"])
                    or any(int(xml.get(kind, "0")) for kind in ["failures", "errors", "skipped"])):
                raise ValueError("Unsuccessful or missing raw testcase: " + row["id"])
            result.append({"ruleId": row["id"], "candidateTest": evidence["candidateTest"],
                           "xmlSha256": evidence["jvmXmlSha256"], "status": "PARTIAL_TESTCASE_PASSED"})
    if not result:
        raise ValueError("No partial assertion evidence to check")
    return result


def scan(root: Path) -> dict:
    pins = {}
    grammars = []
    for source in GRAMMARS:
        text = (root / source).read_text()
        pins[source] = digest(text)
        grammars.extend(grammar_inventory(text, source, "Lexer" in source))
    requirements = []
    for path in sorted((root / "docs/spec").glob("[0-9][0-9]-*.md")):
        if path.name.startswith("17-"):
            continue  # index is not a second contractual source
        source = str(path.relative_to(root))
        text = path.read_text()
        pins[source] = digest(text)
        requirements.extend(spec_requirements(text, source))
    ids = [item["id"] for item in requirements]
    if len(ids) != len(set(ids)):
        raise ValueError("Duplicate normative specification requirement")
    text = (root / BUILTINS).read_text()
    pins[BUILTINS] = digest(text)
    names = re.findall(r'(?:signature|new Signature)\("([A-Za-z]\w*)"', text)
    if len(names) != len(set(names)) or not names:
        raise ValueError("Duplicate or missing builtin signature")
    functions = [{"id": "BUILTIN:" + name, "source": BUILTINS,
                 "line": text.count("\n", 0, text.index('"' + name + '"')) + 1,
                 "step": "E2"} for name in names]
    mappings = matrix_mappings(root, json.loads((root / MATRIX).read_text()), requirements)
    return {"schemaVersion": "1.0", "purpose": "Source discovery, not semantic coverage or independent acceptance",
            "acceptance": "MISSING_EVIDENCE", "pins": pins, "grammars": grammars,
            "specRequirements": requirements, "standardFunctions": functions, **mappings,
            "otherPredefinedContents": g2_predefined.inventory(root),
            "remainingA2": ["Every normative manual paragraph, including compiler-relevant transfer/model implications and appendices",
                            "Atomize prose and specification contracts beyond numbered requirements",
                            "Concrete model/query assertions and positive/negative/editor case assignment for each individual rule",
                            "Version-specific norm anchors and user decision packets; no inferred approvals",
                            "Independent completeness review"]}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--norm23", type=Path)
    parser.add_argument("--norm24", type=Path)
    parser.add_argument("--require-run-evidence", action="store_true")
    args = parser.parse_args()
    out = args.out.resolve()
    if not out.is_relative_to(ROOT / "build"):
        parser.error("Discovery outputs must be under build/; no automatic matrix/baseline updates")
    data = scan(ROOT)
    if args.norm23 or args.norm24:
        if not args.norm23 or not args.norm24:
            parser.error("Both pinned norms must be inventoried together")
        matrix = json.loads((ROOT / MATRIX).read_text())
        clauses = matrix.get("atomicRules", []) + g2_predefined.norm_references(ROOT)
        data["normDocuments"] = [norm_inventory(path, version, matrix["documents"][version], clauses)
                                 for path, version in [(args.norm23, "2.3"), (args.norm24, "2.4")]]
    if args.require_run_evidence:
        data["rawAssertionEvidence"] = check_raw_assertion_evidence(
            ROOT, json.loads((ROOT / MATRIX).read_text()))
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"acceptance": data["acceptance"], "grammarProductions": len(data["grammars"]),
                      "grammarOccurrences": sum(len(row["occurrences"]) for row in data["grammars"]),
                      "specRequirements": len(data["specRequirements"]), "standardFunctionSignatures": len(data["standardFunctions"])}))


if __name__ == "__main__":
    main()
