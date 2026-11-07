#!/usr/bin/env python3
"""Verify the suite-interlis catalog against its external Git checkout."""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys

from run import HARNESS, sha256


CATALOG_FILE = HARNESS.parents[1] / "testdata/reference/suite-interlis/cases.json"
UPSTREAM_URL = "https://github.com/geostandards-ch/suite-interlis"
UPSTREAM_COMMIT = "f30711184d2374feacb81c5742382d35597164ca"
XTF_COUNT, MODEL_COUNT, TEST_CLASS_COUNT = 223, 9, 17
XTF_PATH = r"data/[A-Za-z0-9.]+\.xtf"
MODEL_PATH = r"data/models/[A-Za-z0-9_.-]+\.ili"
JAVA_PATH = r"routine/src/test/java/ch/interlis/testsuite/[A-Za-z]+Test\.java"
DOC_PATH = r"doc/[A-Za-z]+\.md"
CASE_ID = r"[A-Za-z]+\.T[0-9]+[a-z]"
PHASES = {"P4.2", "P4.4", "G1"}
RULE_GROUPS = {
    "xml", "character-encoding", "header", "transfer-structure", "basket", "object-identity",
    "object-encoding", "attribute-value", "attribute-cardinality", "attribute-order", "object-order",
    "structure-attribute", "reference-attribute", "association-encoding", "association-target",
    "association-cardinality", "local-constraint", "global-constraint", "coordinate", "polyline",
    "area-surface", "area-constraint",
}
ROUTINE_PROFILES = {"ilivalidator": "1.11.14", "ig/check": "2018.0.695"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def record(value, required, optional, label):
    require(isinstance(value, dict), f"Expected an object for {label}")
    missing = set(required) - value.keys()
    extra = value.keys() - set(required) - set(optional)
    require(not missing and not extra,
            f"Invalid fields in {label} (missing {sorted(missing)}, unexpected {sorted(extra)})")


def unique_object(pairs):
    obj = {}
    for key, value in pairs:
        require(key not in obj, f"Duplicate JSON field: {key}")
        obj[key] = value
    return obj


def text(value):
    return isinstance(value, str) and bool(value.strip())


def matches(pattern, value):
    return isinstance(value, str) and re.fullmatch(pattern, value) is not None


def text_list(value):
    return isinstance(value, list) and bool(value) and all(map(text, value)) and len(set(value)) == len(value)


def acceptance(value):
    return type(value) is bool or value == "unknown"


def source_reference(source, pattern, field, case_id):
    record(source, ("path", "line", field), (), f"{case_id} source")
    require(matches(pattern, source["path"]) and type(source["line"]) is int and source["line"] > 0,
            f"Invalid {field} source location for {case_id}")


def validate_case(case, model_sets):
    record(case, (
        "id", "xtf", "sha256", "modelSet", "ruleGroup", "phases", "norm",
        "expectedAcceptance", "oracleStatus", "java", "documentation",
    ), ("notes", "vendorSkips", "encoding"), "case")
    case_id = case["id"]
    require(matches(CASE_ID, case_id), f"Invalid case ID: {case_id!r}")
    require(matches(XTF_PATH, case["xtf"]) and matches(r"[0-9a-f]{64}", case["sha256"]),
            f"Invalid XTF path or SHA-256 for {case_id}")
    require(text(case["modelSet"]) and case["modelSet"] in model_sets,
            f"Unknown model set for {case_id}")
    require(text(case["ruleGroup"]) and case["ruleGroup"] in RULE_GROUPS,
            f"Unknown rule group for {case_id}")
    require(text_list(case["phases"]) and set(case["phases"]) <= PHASES,
            f"Invalid implementation phases for {case_id}")
    if "encoding" in case:
        require(case["encoding"] in ("utf-8", "invalid-utf-8"), f"Invalid encoding for {case_id}")

    norm = case["norm"]
    record(norm, ("sections",), ("source", "openDecision"), f"{case_id} norm")
    require(text_list(norm["sections"]), f"Missing or invalid norm sections for {case_id}")
    require(all(text(value) for key, value in norm.items() if key != "sections"),
            f"Invalid norm source or decision for {case_id}")

    java = case["java"]
    source_reference(java, JAVA_PATH, "method", case_id)
    require(matches(r"[A-Za-z][A-Za-z0-9_]*", java["method"]), f"Invalid Java method for {case_id}")
    doc = case["documentation"]
    source_reference(doc, DOC_PATH, "expectedAcceptance", case_id)
    expected, documented = case["expectedAcceptance"], doc["expectedAcceptance"]
    require(acceptance(expected) and acceptance(documented), f"Invalid expectation for {case_id}")
    oracle = case["oracleStatus"]
    require(oracle in ("AGREES", "CONFLICT", "DOCUMENTATION_GAP"), f"Unknown oracle status for {case_id}")
    if "notes" in case:
        require(text_list(case["notes"]), f"Invalid source notes for {case_id}")
    if oracle == "DOCUMENTATION_GAP":
        require(documented == "unknown" and case.get("notes"), f"Unexplained documentation gap for {case_id}")
    else:
        require(type(expected) is bool and type(documented) is bool
                and (expected == documented) == (oracle == "AGREES"),
                f"Inconsistent oracle expectations for {case_id}")

    if "vendorSkips" in case:
        skips = case["vendorSkips"]
        require(isinstance(skips, list) and skips, f"Invalid vendor skips for {case_id}")
        seen = set()
        for skip in skips:
            record(skip, ("profile", "reason", "active"), (), f"{case_id} vendor skip")
            require(text(skip["profile"]) and skip["profile"] in ROUTINE_PROFILES
                    and text(skip["reason"]) and type(skip["active"]) is bool,
                    f"Invalid vendor skip for {case_id}")
            require(skip["profile"] not in seen, f"Duplicate vendor skip for {case_id}")
            seen.add(skip["profile"])


def load_catalog(path=CATALOG_FILE):
    """Read and validate all metadata before selecting or checking inputs."""
    catalog = json.loads(Path(path).read_text(encoding="utf-8"), object_pairs_hook=unique_object)
    record(catalog, ("schema", "upstream", "models", "modelSets", "cases"), (), "catalog")
    require(type(catalog["schema"]) is int and catalog["schema"] == 1, "Unsupported catalog schema")
    upstream = catalog["upstream"]
    record(upstream, ("url", "commit", "transferVersion", "routineProfiles"), (), "upstream")
    require(upstream["url"] == UPSTREAM_URL and upstream["commit"] == UPSTREAM_COMMIT,
            "Wrong upstream URL or commit pin")
    require(upstream["transferVersion"] == "2.3" and upstream["routineProfiles"] == ROUTINE_PROFILES,
            "Invalid upstream transfer version or routine profiles")

    models = catalog["models"]
    require(isinstance(models, dict) and len(models) == MODEL_COUNT, "Expected 9 pinned models")
    for model, digest in models.items():
        require(matches(MODEL_PATH, model) and matches(r"[0-9a-f]{64}", digest),
                f"Invalid model path or SHA-256: {model!r}")
    model_sets = catalog["modelSets"]
    require(isinstance(model_sets, dict) and model_sets, "Missing model sets")
    listed_models = set()
    for name, paths in model_sets.items():
        require(text(name) and text_list(paths) and set(paths) <= models.keys(), f"Invalid model set: {name!r}")
        listed_models.update(paths)
    require(listed_models == models.keys(), "Model sets do not cover the pinned models")

    cases = catalog["cases"]
    require(isinstance(cases, list) and len(cases) == XTF_COUNT, "Expected 223 catalog cases")
    ids, xtfs, methods, used_model_sets = set(), set(), set(), set()
    for case in cases:
        validate_case(case, model_sets)
        java = case["java"]
        method = (java["path"], java["method"])
        require(case["id"] not in ids, f"Duplicate case ID: {case['id']}")
        require(case["xtf"] not in xtfs, f"Duplicate XTF path: {case['xtf']}")
        require(method not in methods, f"Duplicate Java source method: {method}")
        ids.add(case["id"])
        xtfs.add(case["xtf"])
        methods.add(method)
        used_model_sets.add(case["modelSet"])
    require(len({path for path, _ in methods}) == TEST_CLASS_COUNT, "Expected 17 Java test classes")
    require(used_model_sets == model_sets.keys(), "Catalog has unused model sets")
    return catalog


def git(checkout, *args):
    env = {key: value for key, value in os.environ.items() if not key.startswith("GIT_")}
    try:
        result = subprocess.run([
            "git", "--no-replace-objects", "--no-optional-locks", "-c", "core.fsmonitor=false",
            "-C", str(checkout), *args,
        ], env=env, capture_output=True, timeout=15)
    except (OSError, subprocess.TimeoutExpired) as error:
        raise ValueError(f"Git checkout check failed: {error}") from error
    if result.returncode:
        stderr = result.stderr.decode("utf-8", errors="replace").strip()
        raise ValueError(f"Git {args[0]} failed for checkout {checkout}: {stderr}")
    return result.stdout


def verify_checkout(checkout, commit):
    """Require the pinned HEAD at a Git checkout root. Return its canonical path."""
    root = Path(checkout).resolve()
    require(root.is_dir(), f"Missing checkout directory: {root}")
    require(not root.is_relative_to(HARNESS.parents[1]), f"Expected checkout outside the product repository: {root}")
    actual_root = git(root, "rev-parse", "--show-toplevel").decode("utf-8").rstrip("\n")
    require(Path(actual_root).resolve() == root, f"Expected Git checkout root: {root}")
    head = git(root, "rev-parse", "--verify", "HEAD").decode("ascii").strip()
    require(head == commit, f"Wrong checkout pin: expected {commit}, found {head}")
    return root


def original_file(checkout, path):
    require(isinstance(path, str) and not any(character in path for character in ("\\", ":", "\0")),
            f"Invalid original path: {path!r}")
    parts = path.split("/")
    require(all(part not in ("", ".", "..") for part in parts), f"Invalid original path: {path!r}")
    location = Path(checkout)
    for part in parts:
        location /= part
        require(not location.is_symlink(), f"Symlink in original path: {location}")
    require(location.is_file(), f"Missing original file: {location}")
    return location


def verify_input(checkout, path, digest):
    """Verify one original file and return the path to its verified bytes."""
    location = original_file(checkout, path)
    actual = sha256(location)
    require(actual == digest, f"SHA-256 mismatch for {path}: expected {digest}, found {actual}")
    return location


def source_text(checkout, commit, path):
    actual = original_file(checkout, path).read_bytes()
    require(actual == git(checkout, "show", f"{commit}:{path}"), f"Changed original source: {path}")
    return actual.decode("utf-8")


def java_cases(source, path):
    # The pinned JUnit sources use one straight-line method and Javadoc block per case.
    methods = list(re.finditer(
        r"/\*\*((?:(?!\*/).)*)\*/\s*(@Test)\s+public void (\w+)\(\)\s*\{([^}]*)\}", source, re.S))
    require(len(methods) == len(re.findall(r"(?m)^[ \t]*@Test\b", source))
            and not re.search(r"(?m)^[ \t]*@Ignore\b", source), f"Unaccounted JUnit test or ignore: {path}")
    for method in methods:
        javadoc, _, name, body = method.groups()
        ids = re.findall(r"(?m)^[ \t]*\*[ \t]*@ID[ \t]+(\S+)", javadoc)
        xtfs = re.findall(r'TestUtil\.runJob\(vendor,\s*"\.\./(data/[^\"]+\.xtf)"\)', body)
        assertions = re.findall(r"(?m)^[ \t]*assert(True|False)\(ret\);", body)
        require(len(ids) == len(xtfs) == len(assertions) == 1, f"Unaccounted Java oracle: {path}#{name}")
        skips = {(profile, not comment) for comment, profile in re.findall(
            r'(?m)^[ \t]*(//[ \t]*)?assumeFalse\s*\(System\.getProperty\("vendor"\)\.equals\("([^\"]+)"\)\);',
            body)}
        yield ids[0], (path, name, source.count("\n", 0, method.start(2)) + 1,
                       xtfs[0], assertions[0] == "True", skips)


def doc_expectation(lines, source, case_id):
    index = source["line"] - 1
    require(index < len(lines) and lines[index].startswith("|**Expected result**|"),
            f"Wrong documentation expectation row for {case_id}")
    group = next((line for line in reversed(lines[:index]) if line.startswith("|ID|")), "")
    require(group.strip() == f"|ID|{case_id[:-1]}", f"Wrong documentation rule for {case_id}")
    mentions = re.findall(r"(?<!\w)" + re.escape(case_id) + r"\.xtf\s*:\s*([^<|]+)", lines[index], re.I)
    polarities = set()
    for mention in mentions:
        if mention.lower().startswith("no error message"):
            polarities.add(True)
        elif mention.lower().startswith("error message"):
            polarities.add(False)
        else:
            polarities.add("unknown")
    return polarities.pop() if len(polarities) == 1 else "unknown"


def check_inventory(catalog, checkout):
    """Verify all tracked inputs, Java assertions and cited documentation rows."""
    commit = catalog["upstream"]["commit"]
    checkout = verify_checkout(checkout, commit)
    tree = git(checkout, "ls-tree", "-r", "--name-only", "-z", commit).decode("utf-8").split("\0")
    tracked = set(tree)
    cases = catalog["cases"]
    java_files = {case["java"]["path"] for case in cases}
    for suffix, expected in ((".xtf", {case["xtf"] for case in cases}),
                             (".ili", set(catalog["models"])),
                             (".java", java_files)):
        actual = {path for path in tracked if path.endswith(suffix)
                  and (suffix != ".java" or path.startswith("routine/src/test/"))}
        require(actual == expected, f"Tracked {suffix} inventory does not match catalog")

    for path, digest in catalog["models"].items():
        verify_input(checkout, path, digest)
    for case in cases:
        verify_input(checkout, case["xtf"], case["sha256"])
    changed = git(checkout, "status", "--porcelain=v1", "--untracked-files=no").decode("utf-8").splitlines()
    if changed:
        raise ValueError(f"Changed original checkout: {changed[0]}")

    java_oracles = {}
    for path in sorted(java_files):
        for case_id, oracle in java_cases(source_text(checkout, commit, path), path):
            require(case_id not in java_oracles, f"Duplicate Java case ID: {case_id}")
            java_oracles[case_id] = oracle
    require(java_oracles.keys() == {case["id"] for case in cases}, "Java case IDs do not match catalog")
    docs = {path: source_text(checkout, commit, path).splitlines()
            for path in sorted({case["documentation"]["path"] for case in cases})}
    for case in cases:
        case_id, java, doc = case["id"], case["java"], case["documentation"]
        skips = {(skip["profile"], skip["active"]) for skip in case.get("vendorSkips", [])}
        expected = (java["path"], java["method"], java["line"], case["xtf"], case["expectedAcceptance"], skips)
        require(java_oracles[case_id] == expected, f"Java source oracle does not match catalog for {case_id}")
        require(doc_expectation(docs[doc["path"]], doc, case_id) == doc["expectedAcceptance"],
                f"Documentation expectation does not match catalog for {case_id}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkout", required=True, type=Path, help="external suite-interlis Git checkout")
    args = parser.parse_args()
    try:
        catalog = load_catalog()
        check_inventory(catalog, args.checkout)
    except (ValueError, OSError) as error:
        print(f"Suite inventory: {error}", file=sys.stderr)
        return 1
    print(f"Verified {XTF_COUNT} XTF cases, {MODEL_COUNT} models and {TEST_CLASS_COUNT} JUnit classes "
          f"at {UPSTREAM_COMMIT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
