"""Validate the independently produced verdict and its process/evidence bindings."""
import hashlib
import json
from pathlib import Path

from jsonschema import Draft202012Validator


def digest(path):
    with path.open("rb") as file:
        sha = hashlib.sha256()
        for block in iter(lambda: file.read(65536), b""):
            sha.update(block)
        return sha.hexdigest()


def read(path):
    return json.loads(path.read_text())


def require(condition, message):
    if not condition:
        raise ValueError(message)


def checked_file(path, root):
    path = Path(path)
    require(path.is_absolute() and path.is_file() and path == path.resolve()
            and path.is_relative_to(root), "Invalid evidence path: " + str(path))
    return path


def validate(request, verdict, process, manifest, probes, schema, live_commit, live_tree):
    require(process["exitCode"] == 0 and not process["timeout"] and not process["fatalError"]
            and process["sessionId"] and process["completed"]
            and process["model"] == {"id": "deepseek-flash", "providerID": "deepseek"}
            and process["endpoint"] == "https://api.deepseek.com", "Review process did not complete with the required provider/model")
    Draft202012Validator(schema).validate(verdict)
    require(verdict["verdict"] == "PASS", "Independent verdict: " + verdict["verdict"])
    for key in ("assignment", "phaseId", "baseSha", "phaseBaseSha", "candidateSha", "candidateTree"):
        require(verdict[key] == request[key], "Verdict binding mismatch: " + key)
    require((live_commit, live_tree) == (request["candidateSha"], request["candidateTree"]),
            "The active candidate changed")
    criteria = [item["id"] for item in verdict["criteria"]]
    require(sorted(criteria) == sorted(request["requiredCriteria"]), "Criterion set mismatch")
    require(not verdict["limitations"], "PASS must record scope limits in the review report, not in limitations")
    for path, expected in manifest.items():
        source = Path(path)
        require(source.is_file() and source.resolve() == source and digest(source) == expected,
                "Candidate or original evidence changed: " + path)

    output = Path(request["outputDirectory"])
    scratch = Path(request["scratchDirectory"])
    require(checked_file(output / "review.md", output).stat().st_size > 0, "Missing review report")
    require(checked_file(output / "verdict.json", output).stat().st_size > 0, "Missing verdict file")
    for criterion in verdict["criteria"]:
        for evidence in criterion["evidence"]:
            require(evidence in manifest or evidence in probes, "Unknown criterion evidence: " + evidence)
    for check in verdict["independentChecks"]:
        path = checked_file(check["commandLog"], scratch)
        require(probes.get(str(path)) == digest(path), "Counterprobe has no recorded OpenCode shell execution")
        observed = read(path)
        require(observed["exitCode"] == check["exitCode"], "Counterprobe exit mismatch")
