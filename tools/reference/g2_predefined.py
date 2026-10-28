"""Source-level discovery for A2-R2, never compiler semantics or acceptance.

The reviewed catalogue is explicit authoring data. Norm excerpts are checked by
g2_inventory against pinned PDFs; source anchors and all catalogue fields are
part of the reproducible scan. A matching anchor is not proof of implementation.
"""
from __future__ import annotations

import copy
import hashlib
import json
from pathlib import Path
import re

CATALOGUE = "testdata/g2/predefined-catalogue.json"
PROFILE = "interlis-compiler/src/main/java/guru/interlis/compiler/internal/antlr/LanguageProfile.java"
FUNCTIONS = "interlis-compiler/src/main/java/guru/interlis/compiler/internal/BuiltinFunctions.java"
FEATURE_CASES = {
    "TEMPORAL_TYPES": ("DATE/TIMEOFDAY/DATETIME attribute types", "C3"),
    "MODEL_TRANSFER_OPTIONS": ("NOINCREMENTALTRANSFER header plus CHARSET/XMLNS declarations", "B2"),
    "MULTICOORD": ("MULTICOORD domain with bounded axes", "C3"),
    "MULTIGEOMETRY": ("MULTIPOLYLINE/MULTISURFACE/MULTIAREA with bound vertex and forms", "C3"),
    "COORDINATE_CRS": ("coordinate REFSYS string and axis system bindings", "C3"),
    "GENERIC_DOMAIN": ("a GENERIC coordinate domain", "F3"),
    "DEFERRED_GENERICS": ("a topic DEFERRED GENERICS declaration", "F3"),
    "CONTEXT_DEFINITION": ("a CONTEXT mapping to concrete coordinate alternatives", "F3"),
    "DOMAIN_CONSTRAINT": ("a constraint on a domain", "E3"),
    "NAMED_OR_BASKET_CONSTRAINT": ("named constraints in every supported family and BASKET uniqueness", "E3"),
    "INLINE_ENUM_COLLECTION": ("a collection with inline enumeration element type", "C3"),
    "EMPTY_FUNCTION_PARAMETERS": ("a function declaration with zero formal parameters", "E2"),
    "EMPTY_FUNCTION_ARGUMENTS": ("a call to a bound zero-parameter function", "E2"),
}


def sha(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def signature_uses(text: str) -> dict:
    """Record actual typed signature occurrences, including unused enum values."""
    result = {}
    simple = r'signature\("(\w+)"\s*,\s*Value\.(\w+)((?:\s*,\s*Value\.\w+)*)\)'
    explicit = r'new Signature\("(\w+)"\s*,\s*Value\.(\w+)\s*,\s*List\.of\((.*?)\)\s*,\s*LanguageVersion\.(ILI23|ILI24)\s*,\s*Rule\.\w+\)'
    matches = [(m.start(), m[1], m[2], re.findall(r"Value\.(\w+)", m[3]), "ILI23")
               for m in re.finditer(simple, text)]
    matches += [(m.start(), m[1], m[2], re.findall(r"Value\.(\w+)", m[3]), m[4])
                for m in re.finditer(explicit, text, re.S)]
    declared_names = re.findall(r'(?:signature|new Signature)\("(\w+)"', text)
    if sorted(name for _, name, _, _, _ in matches) != sorted(declared_names):
        raise ValueError("Unrecorded builtin signature syntax")
    for _, name, returned, arguments, since in sorted(matches):
        languages = ["2.3", "2.4"] if since == "ILI23" else ["2.4"]
        for value, position in [(returned, "result")] + [(v, "argument:" + str(i)) for i, v in enumerate(arguments)]:
            result.setdefault(value, []).append(dict(function="INTERLIS." + name, position=position, languages=languages))
    return result


def anchored(root: Path, reference: dict) -> dict:
    text = (root / reference["source"]).read_text()
    start = reference["start"]
    end = reference["end"]
    if not 0 <= start < end <= len(text) or sha(text[start:end]) != reference["sha256"]:
        raise ValueError("Changed predefined source anchor: " + reference["source"])
    return dict(reference, line=text.count("\n", 0, start) + 1)


def language_profile(text: str) -> dict:
    tables = []
    matches = list(re.finditer(r'RESERVED_(23|24)\s*=\s*words\("""\s*(.*?)\s*"""\)', text, re.S))
    if [m[1] for m in matches] != ["23", "24"]:
        raise ValueError("Changed language profile table structure")
    for match in matches:
        words = match[2].split()
        if len(words) != len(set(words)):
            raise ValueError("Duplicate reserved word")
        for word in words:
            if not re.fullmatch("[A-Z][A-Z0-9_]*", word):
                raise ValueError("Invalid reserved word: " + word)
            tables.append({"id": "RESERVED:" + match[1] + ":" + word, "word": word,
                           "language": "2." + match[1][1], "step": "B3",
                           "source": PROFILE, "tableStart": match.start(), "tableEnd": match.end(),
                           "tableSha256": sha(match[0]),
                           "acceptance": {"positive": "Reject " + word + " as an identifier in the selected profile.",
                                          "negative": "Do not reject a mixed-case identifier such as X_" + word + ".",
                                          "modelOrQuery": "Original token text/range, fragment parsing and rename agree for " + word + "."},
                           "independentAcceptance": "MISSING_EVIDENCE"})
    # A feature's token map and every AST-side use are recorded separately below.
    feature_match = re.search(r"enum Feature\s*\{(.*?)private final String label;", text, re.S)
    if feature_match is None:
        raise ValueError("Missing language features")
    features = []
    for match in re.finditer(r'([A-Z_]+)\("([^"]+)"\)', feature_match[1]):
        scenario, semantic_step = FEATURE_CASES[match[1]]
        features.append({"id": "PROFILE:" + match[1], "label": match[2], "step": "B3",
                         "semanticStep": semantic_step, "actualSupports": {"2.3": False, "2.4": True},
                         "acceptance": {"positive": "Exercise " + scenario + " through the 2.4 branch and inspect its structured syntax/model.",
                                        "negative": "Exercise the same construct in 2.3 and verify the explicit profile diagnostic; no internal error or valid recovered schema.",
                                        "modelOrQuery": "Keep the original ranges, selected language and typed payload of " + scenario + ". STRICT and EDITOR agree on validity."},
                         "source": PROFILE, "independentAcceptance": "MISSING_EVIDENCE"})
    if not features:
        raise ValueError("Empty language features")
    # Include every contextual case separately, including each grouped token.
    contextual = re.search(r"boolean contextual=(.*?)if\(contextual\)type=", text, re.S)
    if contextual is None:
        raise ValueError("Missing contextual token classification")
    contexts = [{"id": "CONTEXTUAL:XML", "token": "XML", "languages": ["2.3", "2.4"],
                 "condition": "previous token BLACKBOX", "step": "B3"}]
    for match in re.finditer(r"case (.*?) -> (.*?);", contextual[1], re.S):
        for token in re.findall(r"InterlisLexer\.(\w+)", match[1]):
            contexts.append({"id": "CONTEXTUAL:" + token, "token": token, "languages": ["2.3"],
                             "condition": match[2].strip(), "step": "B3"})
    if len(contexts) != len({c["id"] for c in contexts}):
        raise ValueError("Duplicate contextual token")
    for context in contexts:
        context.update(source=PROFILE, classificationSha256=sha(contextual[0]),
                       acceptance={"positive": "Preserve grammar meaning of " + context["token"] + " when " + context["condition"] + ".",
                                   "negative": "Outside that context, " + context["token"] + " follows the version's ordinary identifier/reserved-word rule. Reject illegal constructs through the language profile.",
                                   "modelOrQuery": "Verify original token text/range and following declaration after lookahead/recovery; no hidden acceptance by contextual reclassification."},
                       independentAcceptance="MISSING_EVIDENCE")
    return dict(reservedWords=tables, features=features, contextualTokens=contexts)


def inventory(root: Path, catalogue: dict | None = None) -> dict:
    catalogue = copy.deepcopy(catalogue) if catalogue is not None else json.loads((root / CATALOGUE).read_text())
    rows = catalogue["declarations"]
    if len(rows) != len({r["id"] for r in rows}):
        raise ValueError("Duplicate predefined declaration")
    pinned = {CATALOGUE: sha((root / CATALOGUE).read_text())}
    for row in rows:
        if (row["step"] not in ["B1", "B2", "B3", "E2"] or not row["norm"]
                or not set(row["norm"]) <= {"2.3", "2.4"}
                or set(row["acceptance"]) != {"positive", "negative", "modelOrQuery"}
                or not all(row["acceptance"].values()) or not row["sourceAnchors"]
                or row["independentAcceptance"] != "MISSING_EVIDENCE"):
            raise ValueError("Incomplete predefined mapping: " + row["id"])
        row["sourceAnchors"] = [anchored(root, r) for r in row["sourceAnchors"]]
        for ref in row["sourceAnchors"]:
            pinned[ref["source"]] = sha((root / ref["source"]).read_text())
    special = catalogue["specialValuesAndTypes"]
    if len(special) != len({row["id"] for row in special}):
        raise ValueError("Duplicate special value/type")
    for row in special:
        if (row["step"] not in ["B1", "E2"] or not row["sourceAnchors"]
                or set(row["acceptance"]) != {"positive", "negative", "modelOrQuery"}
                or not all(row["acceptance"].values()) or row["independentAcceptance"] != "MISSING_EVIDENCE"
                or row.get("languages") != ["2.3", "2.4"] or not row.get("ruleReferences")
                or not row.get("languageScopeMeaning")):
            raise ValueError("Incomplete special value/type: " + row["id"])
        row["sourceAnchors"] = [anchored(root, ref) for ref in row["sourceAnchors"]]
        for ref in row["sourceAnchors"]:
            pinned[ref["source"]] = sha((root / ref["source"]).read_text())
    rules = {r["id"] for r in json.loads((root / "docs/evidence/stabilization/g2-rule-matrix.json").read_text())["rules"]}
    uses = signature_uses((root / FUNCTIONS).read_text())
    for row in special:
        if not set(row["ruleReferences"]) <= rules:
            raise ValueError("Unknown special rule linkage: " + row["id"])
        if row["id"].startswith("BUILTIN_VALUE:"):
            value = row["id"].split(":")[1]
            expected = uses.get(value, [])
            if row["actualSignatureUses"] != expected:
                raise ValueError("Wrong signature uses/version: " + row["id"])
            if (row["actualSignatureVersions"] != [v for v in ["2.3", "2.4"] if any(v in use["languages"] for use in expected)]
                    or row["usageState"] != ("USED_IN_SIGNATURES" if expected else "NO_CURRENT_SIGNATURE_USES")):
                raise ValueError("Wrong signature-use state: " + row["id"])
            if not expected and (row.get("pendingUserDecision") != "BUILTIN_AREAS2_AREAS3_NORM_PROFILE"
                                 or row.get("assertionsConditionalOnUserDecision") is not True):
                raise ValueError("Unused descriptor is not a normative decision: " + row["id"])
    other = [anchored(root, row) for row in catalogue["otherClassificationRules"]]
    profile = (root / PROFILE).read_text()
    pinned[PROFILE] = sha(profile)
    lexical = language_profile(profile)
    for word in lexical["reservedWords"]:
        table = catalogue["lexicalNormTables"][word["language"]]
        word["normTable"] = word["language"] + ":" + ("2.2.7" if word["language"] == "2.3" else "3.2.7")
        word["foundInPinnedNormTable"] = word["word"] in table["words"]
    lexical["normTables"] = catalogue["lexicalNormTables"]
    lexical["tableDifferences"] = {}
    for version, table in catalogue["lexicalNormTables"].items():
        actual = {row["word"] for row in lexical["reservedWords"] if row["language"] == version}
        lexical["tableDifferences"][version] = dict(missingInSource=sorted(set(table["words"]) - actual),
                                                   additionalInSource=sorted(actual - set(table["words"])))
    references = []
    for path in sorted((root / "interlis-compiler/src/main/java").rglob("*.java")):
        if path.name == "LanguageProfile.java":
            continue
        text = path.read_text()
        for match in re.finditer(r"LanguageProfile\.Feature\.(\w+)|LanguageProfile\.(featureForToken|supports|classify|isIdentifier)\(", text):
            ref = {"source": str(path.relative_to(root)), "line": text.count("\n", 0, match.start()) + 1,
                   "text": match[0], "feature": match[1], "operation": match[2], "step": "B3"}
            references.append(ref)
            pinned[ref["source"]] = sha(text)
    referenced_features = {r["feature"] for r in references if r["feature"]}
    token_features = set(re.findall(r"Optional.of\(Feature\.(\w+)\)", profile))
    known_features = {r["id"].split(":")[1] for r in lexical["features"]}
    if referenced_features | token_features != known_features:
        raise ValueError("Unused or unrecorded language feature")
    # Origins introduced by new producers remain visible even before a manual
    # catalogue row exists. A context-copy origin is not automatically a symbol.
    producers = []
    for module in ["interlis-compiler", "interlis-model"]:
        for path in sorted((root / module / "src/main/java").rglob("*.java")):
            text = path.read_text()
            for match in re.finditer(r"new\s+(?:guru\.interlis\.model\.)?(?:Origin\.)?PredefinedOrigin\s*\(", text):
                source = str(path.relative_to(root))
                producers.append(dict(source=source, line=text.count("\n", 0, match.start()) + 1,
                                      coveredBySourceCatalogue=source in pinned,
                                      review="REVIEW_SOURCE_FACTORY_OR_CONTEXT_COPY"))
                pinned[source] = sha(text)
    return dict(catalogue=CATALOGUE, sourceHashes=dict(sorted(pinned.items())), declarations=rows,
                specialValuesAndTypes=special, predefinedOriginProducers=producers,
                lexicalProfile=lexical, profileCallSites=references,
                otherClassificationRules=other,
                coverageBoundary=catalogue["coverageBoundary"],
                independentAcceptance="MISSING_EVIDENCE")


def norm_references(root: Path) -> list[dict]:
    """Flatten only locator references for the existing exact PDF checker."""
    catalogue = json.loads((root / CATALOGUE).read_text())
    result = [dict(id=row["id"], norm=row["norm"]) for row in catalogue["declarations"]]
    for row in catalogue["declarations"]:
        for index, reference in enumerate(row.get("alternativeNormEvidence", [])):
            result.append(dict(id=row["id"] + ":alternative:" + str(index), norm={reference["document"]: reference}))
    for version, table in catalogue["lexicalNormTables"].items():
        for index, excerpt in enumerate(table["excerpts"]):
            result.append(dict(id="RESERVED_TABLE:" + version + ":" + str(index), norm={version: excerpt}))
    return result
