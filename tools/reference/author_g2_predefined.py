#!/usr/bin/env python3
"""Explicit authoring of A2 predefined locators into build/; no baseline edits.

This records statements, not their normative approval or implementation. pypdf
is needed only for this authoring command, never for the compiler or unit tests.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

from g2_inventory import ROOT
from g2_predefined import signature_uses


def author(root: Path) -> dict:
    import pypdf
    from pypdf import PdfReader
    if pypdf.__version__ != "6.10.0":
        raise ValueError("A2 page locators require the recorded pypdf 6.10.0 extractor")
    internal = "interlis-compiler/src/main/java/guru/interlis/compiler/internal/"
    documents = json.loads((root / "docs/evidence/stabilization/g2-rule-matrix.json").read_text())["documents"]
    pages = {}
    for version in ["2.3", "2.4"]:
        path = root / ("build/norms/ili" + version.replace(".", "") + ".pdf")
        if hashlib.sha256(path.read_bytes()).hexdigest() != documents[version]["sha256"]:
            raise ValueError("Changed norm: " + version)
        pages[version] = [p.extract_text() for p in PdfReader(path).pages]
    def sha(text):
        return hashlib.sha256(text.encode()).hexdigest()
    def anchor(file, start, end):
        source = internal + file + ".java"
        text = (root / source).read_text()
        a = text.index(start)
        b = text.index(end, a) + len(end)
        return dict(source=source, start=a, end=b, sha256=sha(text[a:b]))
    anchors = {key: anchor(*args) for key, args in {
        "unit": ("GraphBuilder", "    Optional<SymbolId> builtinUnit(", "    /** Validate bound dependencies"),
        "simple": ("GraphBuilder", "    private Optional<SymbolId> builtinSimpleDomain(", "    private Optional<SymbolId> builtinOidDomain("),
        "oid": ("GraphBuilder", "    private Optional<SymbolId> builtinOidDomain(", "    private Optional<SymbolId> bindFormatName("),
        "metadata": ("MetadataBindings", "    Optional<SymbolId> predefined(", "    private SymbolId member("),
        "temporal": ("PredefinedTemporal", "    Optional<SymbolId> find(", "    private SymbolId id("),
        "lines": ("LineForms", "    Optional<SymbolId> predefined(", "    private SymbolId id("),
        "inspection": ("ViewBuilder", "    SymbolId geometryElement(", "    private SymbolId syntheticStructure("),
        "functions": ("BuiltinFunctions", "    private static final List<Signature> SIGNATURES", "    static Optional<Signature> find("),
        "functionModel": ("GraphBuilder", "    FunctionSymbol builtinDeclaration(", "    private boolean builtinArgumentMatches("),
        "primitive": ("GraphBuilder", "    private TypeId buildType(", "            case ClassValueTypeSyntax"),
        "boolean": ("GraphBuilder", "    private TypeId builtinBoolean(", "    FunctionSymbol builtinDeclaration("),
    }.items()}
    def excerpt(version, page, start, end, section="A"):
        text = pages[version][page - 1]
        quote = text[start:end]
        return dict(document=version, section=section, page=page, extractedPageSha256=sha(text),
                    quote=quote, quoteSha256=sha(quote), excerptStart=start, excerptEnd=end,
                    offsetUnit="Unicode code points in pypdf 6.10.0 page text; not compiler source ranges")
    def locate(version, pattern, owner=None):
        for page in range(94, 98) if version == "2.3" else range(121, 125):
            text = pages[version][page - 1]
            start, end = 0, len(text)
            if owner:
                header = re.search(r"\b(?:CLASS|STRUCTURE)\s+" + owner + r"\b", text)
                if not header:
                    # These are the only member-bearing appendix declarations
                    # split across pages; all end delimiters stay explicit.
                    if not (owner == "ArcSegment" and version == "2.4" and page == 124):
                        continue
                else:
                    start = header.end()
                finish = text.find("END " + owner, start)
                end = finish if finish >= 0 else len(text)
            match = re.search(pattern, text[start:end], re.S)
            if match:
                return excerpt(version, page, start + match.start(), start + match.end())
        raise ValueError((version, pattern, owner))
    rows = []
    def add(path, kind, pattern, source, owner=None, versions=("2.3", "2.4"), aliases=None,
            detail=None, step="B1", note=None, norm=None):
        norms = norm or {v: locate(v, pattern, owner) for v in versions}
        payload = detail or "; ".join(v + ": " + " ".join(n["quote"].split()) for v, n in norms.items())
        acceptance = dict(
            positive="Bind/query " + path + " as " + kind + " in each applicable version; verify " + payload,
            negative="A missing " + path + ", wrong bound kind/type/target/order or misspelled reference must not silently succeed.",
            modelOrQuery="For " + path + " verify owning language, canonical identity, origin, declared/effective order and exact payload: " + payload)
        if kind == "FUNCTION":
            acceptance = dict(positive="Bind " + path + " with ordered formal/result types from " + payload,
                              negative="Wrong arity, incompatible arguments, invalid dependent path or unavailable version of " + path + " produce source errors.",
                              modelOrQuery="FunctionSymbol " + path + " retains ordered formal names/resolved types, result type and origin; calls keep its bound target.")
        rows.append(dict(id="PREDEFINED:" + path, path=path, kind=kind, step=step, norm=norms,
                         sourceAnchors=[anchors[s] for s in source.split(",")], sourceBindingNames=aliases or [path],
                         implementationObservation=note or "Factory/source candidate; ordinary public binding and exact versioned payload not proved by this inventory.",
                         acceptance=acceptance, requirements=["META-003", "META-006", "META-011", "META-014", "META-015", "SEM-005", "SEM-010", "TEST-011", "TEST-012"],
                         independentAcceptance="MISSING_EVIDENCE", remainingCause="Inventory only. Exact model/query assertions and execution belong to " + step + "."))
    add("INTERLIS", "MODEL", r'(?:CONTRACTED\s+)?TYPE MODEL INTERLIS.*?VERSION\s+"[^"]+"\s*=', "simple,oid",
        note="Ordinary INTERLIS ModelSymbol factory/lookup not established in the predefined binders; B1 must examine version/origin/catalogue visibility.")
    for name in ["STRAIGHTS", "ARCS"]:
        add("INTERLIS." + name, "LINE_FORM", r"\b" + name + r";", "lines", aliases=[name],
            detail=name + " binds " + ("StraightSegment" if name == "STRAIGHTS" else "ArcSegment") + " and retains the geometry-context vertex lineage.")
    aliases = dict(METER="m", KILOGRAM="kg", SECOND="s", AMPERE="A", DEGREE_KELVIN="K", MOLE="mol", RADIAN="rad", STERADIAN="sr", CANDELA="cd", Minute="min", Hour="h", Day="d", Month="M", Year="Y")
    units = "ANYUNIT DIMENSIONLESS LENGTH MASS TIME ELECTRIC_CURRENT TEMPERATURE AMOUNT_OF_MATTER ANGLE SOLID_ANGLE LUMINOUS_INTENSITY MONEY".split() + list(aliases)
    for name in units:
        symbol = aliases.get(name, name)
        add("INTERLIS." + name, "UNIT", r"\b" + name + r"\s*(?:\[[^\]]+\])?\s*(?:\([^)]*\))?\s*(?:EXTENDS[^;]+|=[^;]+)?;",
            "temporal" if name in ["Minute", "Hour", "Day", "Month", "Year"] else "unit", aliases=["INTERLIS." + symbol],
            note="Declared designation " + name + ", operative name " + symbol + ". Both spelling bindings require B1 proof."
                 + (" Current KILOGRAM designation spelling is KILOGRAMM; not corrected here." if name == "KILOGRAM" else ""))
    for name in "URI NAME INTERLIS_1_DATE BOOLEAN HALIGNMENT VALIGNMENT NOOID ANYOID I32OID STANDARDOID UUIDOID LineCoord GregorianYear XMLTime XMLDate XMLDateTime".split():
        source = "primitive" if name in ["URI", "NAME"] else "boolean" if name == "BOOLEAN" else "simple" if name in ["INTERLIS_1_DATE", "HALIGNMENT", "VALIGNMENT"] else "oid" if name.endswith("OID") else "lines" if name == "LineCoord" else "temporal"
        add("INTERLIS." + name, "DOMAIN", r"(?:DOMAIN\s+)?\b" + name + r"(?:\s*\([^)]*\))?\s*(?:EXTENDS\s+\w+\s*)?=[^;]+;", source,
            versions=("2.4",) if name == "NOOID" else ("2.3", "2.4"),
            note="Keyword descriptor exists; ordinary qualified DomainSymbol binding not established. Distinguish primitive availability from declared domain contents." if name in ["URI", "NAME", "BOOLEAN"] else None)
    for name, values in dict(BOOLEAN=["false", "true"], HALIGNMENT=["Left", "Center", "Right"], VALIGNMENT=["Top", "Cap", "Half", "Base", "Bottom"]).items():
        for index, value in enumerate(values):
            add("INTERLIS." + name + "." + value, "ENUM_VALUE", r"\b" + value + r"\b", "boolean", detail="Ordered value " + value + " at index " + str(index) + " in " + name + ".")
    classes = "METAOBJECT METAOBJECT_TRANSLATION REFSYSTEM COORDSYSTEM SCALSYSTEM SIGN CALENDAR TIMEOFDAYSYS".split()
    structures = "AXIS TimeOfDay UTC GregorianDate GregorianDateTime LineSegment StartSegment StraightSegment ArcSegment SurfaceEdge SurfaceBoundary LineGeometry".split()
    for name in classes + structures:
        kind = "CLASS" if name in classes else "STRUCTURE"
        source = "metadata" if name in "METAOBJECT METAOBJECT_TRANSLATION REFSYSTEM COORDSYSTEM SCALSYSTEM AXIS SIGN".split() else "temporal" if name in "CALENDAR TIMEOFDAYSYS TimeOfDay UTC GregorianDate GregorianDateTime".split() else "inspection" if name in "SurfaceEdge SurfaceBoundary LineGeometry".split() else "lines"
        add("INTERLIS." + ("TIMESYSTEMS." if name in ["CALENDAR", "TIMEOFDAYSYS"] else "") + name, kind, kind + r"\s+" + name + r"\b[^=]*=", source,
            note="Only context-specific inspection construction identified. Ordinary named predefined binding must be checked separately in B1." if source == "inspection" else None)
    add("INTERLIS.TIMESYSTEMS", "TOPIC", r"TOPIC TIMESYSTEMS\s*=", "temporal", note="Topic/classes are created by timeSystem on demand; direct topic lookup not established.")
    add("INTERLIS.BaseTimeSystems", "METADATA_BASKET", r"REFSYSTEM BASKET BaseTimeSystems\s*~\s*TIMESYSTEMS", "temporal", detail="REFSYSTEM basket binds TIMESYSTEMS; ordered CALENDAR/GregorianCalendar and TIMEOFDAYSYS/UTC groups.")
    for cls, obj in [("CALENDAR", "GregorianCalendar"), ("TIMEOFDAYSYS", "UTC")]:
        add("INTERLIS.BaseTimeSystems." + obj, "METAOBJECT", r"OBJECTS OF " + cls + r":\s*" + obj, "temporal", detail="Metaobject " + obj + " belongs to BaseTimeSystems and binds class TIMESYSTEMS." + cls + ".")
    members = [("METAOBJECT", "Name", "ATTRIBUTE", "metadata"), ("METAOBJECT_TRANSLATION", "Name NameInBaseLanguage", "ATTRIBUTE", "metadata"), ("AXIS", "Unit", "PARAMETER", "metadata"), ("COORDSYSTEM", "Axis", "ATTRIBUTE", "metadata"), ("SCALSYSTEM", "Unit", "PARAMETER", "metadata"), ("SIGN", "Sign", "PARAMETER", "metadata"), ("CALENDAR", "Unit", "PARAMETER", "temporal"), ("TIMEOFDAYSYS", "Unit", "PARAMETER", "temporal"), ("TimeOfDay", "Hours Minutes Seconds", "ATTRIBUTE", "temporal"), ("UTC", "Hours", "ATTRIBUTE", "temporal"), ("GregorianDate", "Year Month Day", "ATTRIBUTE", "temporal"), ("GregorianDateTime", "Hours Minutes Seconds", "ATTRIBUTE", "temporal"), ("LineSegment", "SegmentEndPoint", "ATTRIBUTE", "lines"), ("ArcSegment", "ArcPoint Radius", "ATTRIBUTE", "lines"), ("SurfaceEdge", "Geometry LineAttrs", "ATTRIBUTE", "inspection"), ("SurfaceBoundary", "Lines", "ATTRIBUTE", "inspection"), ("LineGeometry", "Segments", "ATTRIBUTE", "inspection")]
    for owner, names, kind, source in members:
        for name in names.split():
            add("INTERLIS." + ("TIMESYSTEMS." if owner in ["CALENDAR", "TIMEOFDAYSYS"] else "") + owner + "." + name, kind,
                r"(?:CONTINUOUS\s+)?(?:SUBDIVISION\s+)?" + name + r"(?:\s*\([^)]*\))?\s*:[^;]+;", source, owner=owner,
                versions=("2.3",) if name == "LineAttrs" else ("2.3", "2.4"))
    for owner, name in [("METAOBJECT", "Name"), ("METAOBJECT_TRANSLATION", "Name"), ("METAOBJECT_TRANSLATION", "NameInBaseLanguage")]:
        add("INTERLIS." + owner + ".UNIQUE:" + name, "CONSTRAINT", r"UNIQUE " + name + r";", "metadata", owner=owner)
    add("INTERLIS.LineGeometry.MANDATORY:StartSegment", "CONSTRAINT", r"MANDATORY CONSTRAINT isOfClass\s*\(Segments\[FIRST\],StartSegment\);", "inspection", owner="LineGeometry")
    # Explicit norm-side list, independent of which names the implementation
    # currently happens to contain. Removing a factory must not remove its rule.
    functions = "len lenM trim trimM objectCount elementCount myClass isSubClass isOfClass isEnumSubVal inEnumRange convertUnit areAreas length multilength surface multisurface areAreas2 areAreas3 areAreasWithoutGaps areAreasWithoutGaps2".split()
    for index, name in enumerate(functions):
        norm = None
        if name.startswith("areAreasWithoutGaps"):
            text = pages["2.4"][86]
            start = text.index("FUNCTION " + name + " ")
            end = text.index("BOOLEAN;", start) + len("BOOLEAN;")
            norm = {"2.4": excerpt("2.4", 87, start, end, "3.14")}
        add("INTERLIS." + name, "FUNCTION", r"FUNCTION " + name + r"\s*\(.*?\):\s*(?:STRUCTURE|NUMERIC|BOOLEAN|TEXT|MTEXT);", "functions,functionModel",
            versions=("2.3", "2.4") if index < 13 else ("2.4",), step="E2", norm=norm)
        if name in ["areAreas2", "areAreas3"]:
            rows[-1]["pendingUserDecision"] = "BUILTIN_AREAS2_AREAS3_NORM_PROFILE"
        if name == "areAreas2":
            text = pages["2.4"][86]
            start = text.index("FUNCTION areAreas2 ")
            end = text.index("BOOLEAN;", start) + len("BOOLEAN;")
            rows[-1]["alternativeNormEvidence"] = [excerpt("2.4", 87, start, end, "3.14")]
            rows[-1]["acceptance"] = dict(
                positive="Record the actual OBJECTS signature and both 2.4 declarations (chapter OBJECTS, appendix OBJECT). After explicit user decision, verify the chosen argument profile with its bound signature.",
                negative="Reject wrong arity and malformed path operands. The single-object versus set argument expectation is conditional on the user decision, not asserted by this inventory.",
                modelOrQuery="Publish the actual ordered function parameters/result and keep both norm sources and their unresolved difference visible; do not label either unapproved profile as accepted.")
    tables = {}
    for version, ranges in [("2.3", [(25, "ABSTRACT ACCORDING", "Tabelle 1:")]),
                            ("2.4", [(29, "ABSTRACT ACCORDING", None), (30, "PERIPHERY PI", None), (31, "TRANSFER TRANSIENT", "Tabelle 1:")])]:
        refs = []
        words = []
        for page, start_marker, end_marker in ranges:
            text = pages[version][page - 1]
            start = text.index(start_marker)
            end = text.index(end_marker, start) if end_marker else len(text)
            refs.append(excerpt(version, page, start, end, "2.2.7" if version == "2.3" else "3.2.7"))
            words.extend(re.findall(r"\b[A-Z][A-Z0-9_]*\b", text[start:end]))
        tables[version] = dict(words=words, excerpts=refs)
    profile_source = "interlis-compiler/src/main/java/guru/interlis/compiler/internal/antlr/LanguageProfile.java"
    profile_text = (root / profile_source).read_text()
    other = []
    for key, start_marker, end_marker, detail in [
        ("identifier", "    public static boolean isIdentifier(", "    static void classify(", "ASCII identifier, whole token/EOF and 255-character limit; reserved words depend on version."),
        ("classification", "    private static int classifiedType(", "    static boolean supports(", "Preserve EOF/hidden tokens and nonletter tokens; reserved and contextual identifiers keep their selected profile and length."),
        ("supports", "    static boolean supports(", "    static Optional<Feature> featureForToken(", "Every current Feature is available only in ILI24; actual source behavior, not normative approval."),
        ("tokenFeatures", "    static Optional<Feature> featureForToken(", "            default -> Optional.empty();", "Record the token-to-feature mappings and AST-side profile checks; an unmapped token is not by itself a version gate.")]:
        start = profile_text.index(start_marker)
        end = profile_text.index(end_marker, start) + len(end_marker)
        other.append(dict(id="CLASSIFICATION:" + key, step="B3", source=profile_source, start=start, end=end,
                          sha256=sha(profile_text[start:end]), behavior=detail,
                          acceptance=dict(positive=detail, negative="A different profile, spelling, boundary or unsupported construct must not silently bypass " + key + ".",
                                          modelOrQuery="Public tokens/diagnostics and declaration/range preservation agree in STRICT, EDITOR, fragments and rename."),
                          independentAcceptance="MISSING_EVIDENCE"))
    special = []
    special_rules = {"ANYCLASS": ["SYN:restrictedClassOrAssRef", "SYN:argumentType"],
                     "ANYSTRUCTURE": ["SYN:restrictedStructureRef", "SYN:argumentType"],
                     "UNDEFINED": ["SYN:factor", "EXPRESSION.undefinedArguments", "EXPRESSION.undefinedValues"],
                     "PI": ["SYN:numericConst", "CONST.symbolicNumbers"],
                     "LNBASE": ["SYN:numericConst", "CONST.symbolicNumbers"]}
    for name, step, reference, behavior in [
        ("ANYCLASS", "B1", anchor("GraphBuilder", "    private SymbolId anyClass(", "    private Optional<SymbolId> objectBase("), "Synthetic per-model class sentinel; object/reference unrestricted bases also use an empty target. No ordinary global INTERLIS.ANYCLASS declaration is established."),
        ("ANYSTRUCTURE", "B1", anchors["functionModel"], "Unrestricted structure type uses StructureValueType with empty target; distinguish from an unknown structure name."),
        ("UNDEFINED", "E2", anchor("GraphBuilder", "    private ExpressionResult undefinedExpression(", "    /** A missing parsed condition"), "A resolved UndefinedExpression has an explicitly undefined type, never ErrorType/PendingType; contextual mandatory/optional rules remain E2."),
        ("PI", "E2", anchor("GraphBuilder", "    private ExpressionResult constantExpression(", "    private ExpressionResult undefinedExpression("), "Symbolic numeric constant, optionally unit-qualified; no rounded exact decimal replacement."),
        ("LNBASE", "E2", anchor("GraphBuilder", "    private ExpressionResult constantExpression(", "    private ExpressionResult undefinedExpression("), "Symbolic numeric constant, optionally unit-qualified; no rounded exact decimal replacement.")]:
        special.append(dict(id="SPECIAL:" + name, step=step, sourceAnchors=[reference], actualRepresentation=behavior,
                            languages=["2.3", "2.4"], languageScopeMeaning="The special syntax/type representation exists in both source profiles; individual contexts remain subject to the linked rules.",
                            ruleReferences=special_rules[name],
                            acceptance=dict(positive="Use " + name + " in its allowed syntax and inspect the exact target/type representation.",
                                            negative="An invalid use or misspelling of " + name + " must remain distinct from the legal special value/type.",
                                            modelOrQuery=behavior), independentAcceptance="MISSING_EVIDENCE"))
    builtin_text = (root / (internal + "BuiltinFunctions.java")).read_text()
    values = re.search(r"enum Value\s*\{([^}]+)\}", builtin_text, re.S)[1]
    uses = signature_uses(builtin_text)
    grammar = dict(TEXT="textType", MTEXT="textType", NUMERIC="numericType", BOOLEAN="booleanType",
                   CLASS_VALUE="classType", ANYSTRUCTURE="restrictedStructureRef", OBJECT="argumentType",
                   OBJECTS="argumentType", BAG="attrTypeDef", ENUM="argumentType", ENUM_TREE="argumentType",
                   POLYLINE="lineType", MULTIPOLYLINE="lineType", SURFACE="lineType", MULTISURFACE="lineType", ATTRIBUTE="attributeType")
    for name in re.findall(r"\b[A-Z_]+\b", values):
        actual = uses.get(name, [])
        special.append(dict(id="BUILTIN_VALUE:" + name, step="E2", sourceAnchors=[anchors["functionModel"], anchors["functions"]],
                            languages=["2.3", "2.4"], languageScopeMeaning="The factory descriptor variant exists in both language engines; actual signature availability is recorded separately, not inferred from this factory scope.",
                            actualSignatureUses=actual, actualSignatureVersions=[v for v in ["2.3", "2.4"] if any(v in use["languages"] for use in actual)],
                            usageState="USED_IN_SIGNATURES" if actual else "NO_CURRENT_SIGNATURE_USES",
                            ruleReferences=["SYN:" + grammar[name]] + list(dict.fromkeys("BUILTIN:" + use["function"].split(".")[1] for use in actual)),
                            actualRepresentation="BuiltinFunctions.Value." + name + "; result/argument descriptor creation and special ATTRIBUTE dependent-path branch in GraphBuilder.",
                            acceptance=dict(positive="Inspect explicit signatures using " + name + " and their resolved descriptor.",
                                            negative="A wrong-family argument to a " + name + " formal must produce a source error; no generic replacement type.",
                                            modelOrQuery="Preserve value kind, target restrictions, units/precision where applicable, ordered dependencies and language origin for " + name + "."),
                            independentAcceptance="MISSING_EVIDENCE"))
        if not actual:
            if name != "OBJECT":
                raise ValueError("New unused signature variant requires explicit author review: " + name)
            special[-1].update(pendingUserDecision="BUILTIN_AREAS2_AREAS3_NORM_PROFILE",
                               assertionsConditionalOnUserDecision=True,
                               ruleReferences=["SYN:argumentType", "BUILTIN:areAreas2"],
                               actualRepresentation="Value.OBJECT has zero current SIGNATURES uses in either version. The 2.4 appendix areAreas2 declaration uses OBJECT; chapter 3.14 and current factory use OBJECTS.",
                               acceptance=dict(positive="Verify the current zero-use state. Only if the user chooses an OBJECT formal for areAreas2, inspect the resulting bound single-object signature.",
                                               negative="Do not invent a current OBJECT-formal call test. After user decision, test the applicable single-object/set distinction or retain/remove this unused factory variant explicitly.",
                                               modelOrQuery="Keep zero actual signature uses and no active signature version visible. Any changed public formal type requires the recorded user decision and new model/query evidence."))
    return dict(schemaVersion="1.0", scope="A2-R2 inventory, not compiler or normative acceptance.", documents=documents,
                declarations=rows, specialValuesAndTypes=special, lexicalNormTables=tables, otherClassificationRules=other, coverageBoundary={
                    "includes": ["Explicit appendix-A declarations/members/constraints/enum values in both versions", "Chapter-only additional standard functions", "Actual lexical tables/features/contextual classifications"],
                    "notProven": ["Ordinary lookup of all predefined declarations", "Versioned implementation and public graph correctness", "Clause-specific case/assertion execution", "Full A2-R3/R4 paragraph and contract completeness"]})


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    if not args.out.resolve().is_relative_to(ROOT / "build"):
        parser.error("Authoring outputs must be under build/; review and adopt explicitly")
    data = author(ROOT)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"declarations": len(data["declarations"]), "acceptance": "MISSING_EVIDENCE"}))
