"""Run an isolated, independent OpenCode 2.x phase review on macOS."""
import argparse
import base64
import datetime
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import socket
import subprocess
import sys
import time
import urllib.request

from gate import checked_file, digest, read, require, validate

PHASES = {"G0": (1, 2, 3, 4, 5, 6, 46, 47), "G1": range(1, 14), "G2": (*range(1, 7), *range(14, 21)),
          "G3": (*range(1, 7), *range(21, 29)), "G4": (*range(1, 7), *range(29, 39)),
          "G5": (*range(1, 7), *range(39, 45)), "G6": range(1, 53)}
SPEC = Path("docs/implementation/compiler-gui-swing")
MAPPING = "https://api-docs.deepseek.com/quick_start/pricing/"
ENDPOINT = "https://api.deepseek.com"


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n")


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()


def descendants(root):
    return sorted(path for path in root.rglob("*") if path.is_file())


def rights(candidate, evidence, directory, probes):
    def rule(action, resource, effect="allow"):
        return dict(action=action, resource=resource, effect=effect)

    inputs = directory / "inputs"
    # Internal reads are location-relative. The external-directory rules
    # remain the boundary for any location outside the isolated candidate.
    permissions = [rule("*", "*", "deny"), rule("read", "*"), rule("glob", "*"), rule("grep", "*"),
                   rule("read", "*.env*", "deny"), rule("read", ".git", "deny")]
    for base in (candidate, evidence, inputs):
        for resource in (str(base), str(base / "*")):
            for action in ("read", "glob", "grep", "external_directory"):
                permissions.append(rule(action, resource))
    for name in ("output", "scratch"):
        base = directory / name
        permissions.extend([rule("external_directory", str(base)), rule("external_directory", str(base / "*")),
                            rule("read", str(base / "*")), rule("edit", str(base / "*"))])
    permissions.extend(rule("shell", command) for command in probes)
    return permissions


def start_review(args, root, directory):
    candidate = directory / "candidate"
    inputs = directory / "inputs"
    scratch = directory / "scratch"
    output = directory / "output"
    for path in (inputs, scratch, output):
        path.mkdir()
    require(git(root, "branch", "--show-current") == "codex/compiler-gui-swing", "Wrong product branch")
    require(not git(root, "status", "--porcelain"), "Commit the complete candidate before review")
    commit = git(root, "rev-parse", "HEAD")
    tree = git(root, "rev-parse", "HEAD^{tree}")
    base = git(root, "rev-parse", args.base + "^{commit}")
    previous = git(root, "rev-parse", args.phase_base + "^{commit}")
    git(root, "merge-base", "--is-ancestor", base, previous)
    git(root, "merge-base", "--is-ancestor", previous, commit)
    if args.phase == "G0":
        require(previous == base and not args.previous_pass, "G0 phase base must be the assignment base")
    else:
        require(args.previous_pass, "An accepted previous phase is required")
        prior = read(args.previous_pass)
        require(prior == {"passed": True, "phase": "G" + str(int(args.phase[1:]) - 1),
                          "baseSha": base, "candidateSha": previous,
                          "candidateTree": git(root, "rev-parse", previous + "^{tree}")},
                "Previous phase PASS does not match the phase base")
    git(root, "worktree", "add", "--detach", str(candidate), commit)
    require(not git(candidate, "status", "--porcelain"), "Review candidate is dirty")

    evidence = args.ledger.resolve().parent
    require(not evidence.is_relative_to(root) and evidence != evidence.parent, "Use an external evidence directory")
    ledger = read(args.ledger)
    require(ledger["candidateSha"] == commit and ledger["candidateTree"] == tree, "Test ledger candidate mismatch")
    require(ledger["tests"] and all(test["result"] == "PASS" and test["evidencePaths"] for test in ledger["tests"]),
            "Incomplete targeted tests")
    originals = {args.ledger.resolve()}
    for test in ledger["tests"]:
        originals.update(checked_file(path, evidence) for path in test["evidencePaths"])
    tracked = [candidate / name for name in git(candidate, "ls-files", "-z").rstrip("\x00").split("\x00")]
    require(all(path.is_file() and path.resolve() == path for path in tracked), "Non-regular candidate sources")
    for name, start in (("phase.diff", previous), ("cumulative.diff", base)):
        (inputs / name).write_text(git(candidate, "diff", "--no-ext-diff", start, commit) + "\n")

    # A fresh OpenCode store, using the already-authorized DeepSeek credentials via a read-only reference.
    xdg = directory / "opencode-state"
    for folder in ("data/opencode", "config/opencode", "cache", "state", "tmp"):
        (xdg / folder).mkdir(parents=True, exist_ok=True)
    auth_directory = Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local/share")) / "opencode"
    for name in ("account.json", "auth.json"):
        auth = auth_directory / name
        if auth.is_file():
            (xdg / "data/opencode" / name).symlink_to(auth.resolve())

    # Commands are selected by the reviewer from exact argv vectors. Shell syntax is never evaluated.
    probe_commands = {}
    for label, command in ledger["reviewProbes"].items():
        require(re.fullmatch(r"[a-z][a-z0-9-]*", label) and isinstance(command, list) and command
                and Path(command[0]).is_absolute(), "Invalid counterprobe declaration")
        probe_commands["review-probe " + label] = command
    require(probe_commands, "No independent test commands configured")
    write(inputs / "probe-commands.json", probe_commands)
    shell = inputs / "review-shell"
    shell.write_text((root / SPEC / "review/review-shell").read_text())
    shell.chmod(0o500)
    config = {"shell": str(shell), "providers": {"deepseek": {"settings": {"baseURL": ENDPOINT}}},
              "permissions": rights(candidate, evidence, directory, probe_commands)}
    config_file = xdg / "config/opencode/opencode.json"
    config["agents"] = {"phase-review": {"description": "Independent phase review",
                                         "mode": "primary", "permissions": config["permissions"]}}
    write(config_file, config)
    env = {key: os.environ[key] for key in ("HOME", "USER", "LOGNAME", "DEEPSEEK_API_KEY") if key in os.environ}
    env.update(PATH="/usr/bin:/bin:/usr/sbin:/sbin", SHELL=str(shell), TMPDIR=str(xdg / "tmp"),
               XDG_DATA_HOME=str(xdg / "data"), XDG_CONFIG_HOME=str(xdg / "config"),
               XDG_CACHE_HOME=str(xdg / "cache"), XDG_STATE_HOME=str(xdg / "state"),
               OPENCODE_DISABLE_AUTOUPDATE="1", OPENCODE_SERVER_PASSWORD=secrets.token_urlsafe(32))

    # Even a subprocess cannot write to tracked files, original evidence or the user's home.
    profile = inputs / "sandbox.sb"
    paths = " ".join("(subpath " + json.dumps(str(path)) + ")" for path in (scratch, output, xdg))
    profile.write_text("(version 1) (allow default) (deny file-write*)\n"
                       "(allow file-write* " + paths + ' (literal "/dev/null"))\n')
    guard = ["/usr/bin/sandbox-exec", "-f", str(profile)]
    sandbox_check = subprocess.run([*guard, "/usr/bin/touch", str(inputs / "forbidden-write")],
                                   env=env, cwd=candidate, capture_output=True, text=True, timeout=10)
    require(sandbox_check.returncode != 0 and "Operation not permitted" in sandbox_check.stderr
            and not (inputs / "forbidden-write").exists(), "Review sandbox failed to protect inputs")
    (inputs / "sandbox-probe.txt").write_text(sandbox_check.stderr)
    binary = Path(shutil.which("opencode") or "/missing-opencode").resolve()
    version = subprocess.check_output([binary, "--version"], text=True).strip()
    require(re.fullmatch(r"opencode v2\.\d+\.\d+.*", version), "OpenCode 2.x required")
    help_text = subprocess.check_output([binary, "run", "--help"], text=True)
    require(all(flag in help_text for flag in ("--server", "--auto", "--model", "--format", "--file")),
            "OpenCode run does not support the required flags")
    (inputs / "opencode-run-help.txt").write_text(help_text)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(MAPPING, timeout=20) as response:
        mapping = response.read()
    require(b"deepseek-flash" in mapping and b"DeepSeek-V4.1-Flash-1017" in mapping, "Official model mapping changed")
    (inputs / "deepseek-model-mapping.html").write_bytes(mapping)

    request = dict(assignment="compiler-gui-swing", phaseId=args.phase, baseSha=base, phaseBaseSha=previous,
                   candidateSha=commit, candidateTree=tree, requiredCriteria=[f"GUI-{number:02d}" for number in PHASES[args.phase]],
                   candidateDirectory=str(candidate), outputDirectory=str(output), scratchDirectory=str(scratch),
                   testLedger=str(args.ledger.resolve()), phaseDiff=str(inputs / "phase.diff"),
                   cumulativeDiff=str(inputs / "cumulative.diff"), originalEvidence=sorted(map(str, originals)),
                   schema=str(candidate / SPEC / "review-verdict.schema.json"), previousRounds=args.previous_round,
                   provider="deepseek", model="deepseek-flash", modelBuildFromOfficialMapping="DeepSeek-V4.1-Flash-1017",
                   endpoint=ENDPOINT, mappingSource=MAPPING, mappingCheckedAt=now(), probeCommands=list(probe_commands),
                   evidenceIndex=str(inputs / "evidence-index.json"))
    write(inputs / "request.json", request)
    instructions = (candidate / SPEC / "OPENCODE_REVIEW_PROMPT.md").read_text().split("## 6.")[1].split("## 7.")[0]
    (inputs / "REVIEW_REQUEST.md").write_text(instructions + "\nUse absolute evidence paths from evidence-index.json. "
        "Run at least one reviewer-chosen counterprobe using one exact command in probeCommands. "
        "The shell records its own stdout, stderr, argv and exit in scratch/<label>.json. "
        "Use that path in commandLog. Record any out-of-scope limitations in review.md. "
        "In verdict.json, criteria MUST contain exactly these IDs, including all global criteria: "
        + ", ".join(request["requiredCriteria"]) + ". For unchanged global criteria, audit the previously accepted "
        "gate and the current diff and include a separate criterion object with evidence for each. "
        "For PASS leave verdict limitations empty. Keep review.md and verdict.json in outputDirectory.\n")
    manifest = {str(path): digest(path) for path in (*tracked, *originals, *descendants(inputs), config_file)}
    write(inputs / "evidence-index.json", manifest)
    manifest[str(inputs / "evidence-index.json")] = digest(inputs / "evidence-index.json")

    with socket.socket() as local:
        local.bind(("127.0.0.1", 0))
        port = local.getsockname()[1]
    address = f"http://127.0.0.1:{port}"
    server_args = [*guard, str(binary), "serve", "--hostname", "127.0.0.1", "--port", str(port)]
    with (directory / "server.log").open("wb") as server_log:
        server = subprocess.Popen(server_args, env=env, cwd=candidate, stdout=server_log, stderr=server_log, start_new_session=True)
        try:
            run_session(args, directory, candidate, inputs, request, manifest, opener, address, env, guard, binary, version, server)
        finally:
            if server.poll() is None:
                os.killpg(server.pid, signal.SIGTERM)
                try:
                    server.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(server.pid, signal.SIGKILL)
                    server.wait()


def run_session(args, directory, candidate, inputs, request, manifest, opener, address, env, guard, binary, version, server):
    authorization = base64.b64encode(("opencode:" + env["OPENCODE_SERVER_PASSWORD"]).encode()).decode()

    def api(path):
        req = urllib.request.Request(address + path, headers={"Authorization": "Basic " + authorization})
        with opener.open(req, timeout=10) as response:
            return json.load(response)

    for _ in range(100):
        require(server.poll() is None, "Private OpenCode server exited")
        try:
            models = api("/api/model")["data"]
            break
        except OSError:
            time.sleep(.1)
    else:
        raise ValueError("Private OpenCode server did not become ready")
    sources = api("/api/config")
    config_path = Path(env["XDG_CONFIG_HOME"]) / "opencode/opencode.json"
    declared = read(config_path)
    policies = [source for source in sources if source.get("info", {}).get("permissions")]
    require(len(policies) == 1 and policies[0]["path"] == str(config_path)
            and policies[0]["info"]["permissions"] == declared["permissions"]
            and policies[0]["info"]["shell"] == declared["shell"]
            and policies[0]["info"]["agents"] == declared["agents"], "Unexpected effective review permissions")
    write(directory / "permissions-binding.json", {"configSource": str(config_path), "sha256": digest(config_path)})
    model, = [m for m in models if m["id"] == "deepseek-flash" and m["providerID"] == "deepseek"]
    require(model["enabled"] and model["status"] == "active" and model["modelID"] == "deepseek-flash"
            and model["settings"]["baseURL"] == ENDPOINT
            and model["package"] == "@opencode/ai/providers/deepseek", "Wrong effective DeepSeek transport")
    write(directory / "model.json", dict(id=model["id"], providerID=model["providerID"], modelID=model["modelID"],
                                        package=model["package"], baseURL=model["settings"]["baseURL"]))
    invocation = [*guard, str(binary), "run", "--server", address, "--auto", "--agent", "phase-review",
                  "--model", "deepseek/deepseek-flash", "--format", "json", "--file", str(inputs / "request.json"),
                  "--file", str(inputs / "REVIEW_REQUEST.md"),
                  f"Independently review {request['phaseId']}. Read {inputs / 'request.json'} and "
                  f"{inputs / 'REVIEW_REQUEST.md'} first. Allowed shell commands are exactly: "
                  + ", ".join(request["probeCommands"]) + ". Required verdict IDs, each exactly once: "
                  + ", ".join(request["requiredCriteria"]) + ". Write review.md and verdict.json as instructed."]
    start = now()
    write(directory / "launch.json", dict(argv=invocation, cwd=str(candidate), startedAt=start, openCodeVersion=version,
                                         model=request["model"], endpoint=ENDPOINT))
    with (directory / "opencode.jsonl").open("wb") as out, (directory / "stderr.log").open("wb") as err:
        child = subprocess.Popen(invocation, cwd=candidate, env=env, stdout=out, stderr=err, start_new_session=True)
        timed_out = False
        try:
            child.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            timed_out = True
            os.killpg(child.pid, signal.SIGKILL)
            child.wait()
    process = dict(exitCode=child.returncode, timeout=timed_out, startedAt=start, endedAt=now())
    write(directory / "process-result.json", process)
    events = [json.loads(line) for line in (directory / "opencode.jsonl").read_text().splitlines()]
    sessions = {event["sessionID"] for event in events if event.get("sessionID")}
    require(len(sessions) == 1, "Expected exactly one independent session")
    session = sessions.pop()
    export = api(f"/api/experimental/session/{session}/export")
    if "data" in export:
        export = export["data"]
    model = {key: export["info"]["model"][key] for key in ("id", "providerID")}
    responses = [m for m in export["messages"] if m.get("type") == "assistant"]
    require(responses and all(m.get("model", {}).get("providerID") == "deepseek"
                              and m.get("model", {}).get("id") == "deepseek-flash" for m in responses),
            "Assistant response model could not be bound to the selected provider")
    write(directory / "session-binding.json", dict(sessionId=session, model=model,
          responses=[{key: response.get(key) for key in ("id", "model", "finish", "rawFinish", "time")}
                     for response in responses]))
    process.update(sessionId=session, model=model, endpoint=ENDPOINT,
                   fatalError=any(event.get("type") == "error" for event in events),
                   completed=responses[-1].get("finish") in ("stop", "end_turn", "completed"))
    write(directory / "process-result.json", process)
    errors = [event["error"] for event in events if event.get("type") == "error"]
    error_codes = [(error.get("type"), error.get("status")) for error in errors if isinstance(error, dict)]
    require(not process["fatalError"] and process["exitCode"] == 0 and process["completed"] and not timed_out,
            f"OpenCode ended before completing the review (exit={child.returncode}, errors={error_codes})")
    probe_logs = {}
    for event in events:
        part = event.get("part", {})
        state = part.get("state", {})
        if event.get("type") == "tool_use" and part.get("tool") == "shell" and state.get("status") == "completed":
            command = state.get("input", {}).get("command")
            match = re.search(r"(?m)^COUNTERPROBE_SHA256=([0-9a-f]{64})$", state.get("output", ""))
            if command in request["probeCommands"] and match:
                probe_logs[str(directory / "scratch" / (command.split()[-1] + ".json"))] = match.group(1)
    require(not git(candidate, "status", "--porcelain"), "Detached candidate was modified")
    root = Path.cwd()
    require(not git(root, "status", "--porcelain"), "Product candidate was modified")
    validate(request, read(directory / "output/verdict.json"), process, manifest, probe_logs,
             read(candidate / SPEC / "review-verdict.schema.json"), git(root, "rev-parse", "HEAD"),
             git(root, "rev-parse", "HEAD^{tree}"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phase", choices=PHASES, required=True)
    parser.add_argument("--base", required=True)
    parser.add_argument("--phase-base", required=True)
    parser.add_argument("--ledger", type=Path, required=True)
    parser.add_argument("--round-directory", type=Path, required=True)
    parser.add_argument("--previous-pass", type=Path)
    parser.add_argument("--previous-round", action="append", default=[])
    parser.add_argument("--timeout", type=int, default=1800)
    args = parser.parse_args()
    root = Path.cwd().resolve()
    directory = args.round_directory.resolve()
    require(not directory.is_relative_to(root), "Review artifacts must be outside the product worktree")
    directory.mkdir(mode=0o700, parents=True)
    try:
        start_review(args, root, directory)
    except Exception as error:
        write(directory / "gate-result.json", {"passed": False, "detail": str(error)})
        raise
    request = read(directory / "inputs/request.json")
    write(directory / "gate-result.json", {"passed": True, "phase": args.phase,
          "baseSha": request["baseSha"], "candidateSha": request["candidateSha"], "candidateTree": request["candidateTree"]})
    print("Valid independent PASS in", directory)


if __name__ == "__main__":
    main()
