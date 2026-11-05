# Optional compiler APE investigation

Use Java25 explicitly. The repository's normal JVM profiles do not run these tools.
From the repository root:

```sh
java build/InterlisJvmBuild.java ape inspect
java build/InterlisJvmBuild.java ape prepare
java build/InterlisJvmBuild.java ape verify --inputs /absolute/compiler-inputs.json
java build/InterlisJvmBuild.java ape toolchain --lock /absolute/toolchain.lock.json
java build/InterlisJvmBuild.java ape probe --lock /absolute/toolchain.lock.json
java build/InterlisJvmBuild.java ape build --inputs /absolute/compiler-inputs.json --arch x86_64 --lock /absolute/toolchain.lock.json
java build/InterlisJvmBuild.java ape build --inputs /absolute/compiler-inputs.json --arch aarch64 --lock /absolute/toolchain.lock.json
java build/InterlisJvmBuild.java ape fuse --x64 /absolute/x64/slice.json --arm64 /absolute/arm64/slice.json --lock /absolute/toolchain.lock.json
java build/InterlisJvmBuild.java ape report --evidence /absolute/evidence-directory
```

`toolchain` explicitly downloads pinned archives and installs under
`target/compiler-ape/toolchains`. A matching Docker image, Node and network
access for acquisition are prerequisites. Native builds use the pinned Linux
x64 image; running it through emulation is build assistance only. Installation
validates the complete pinned extracted inventory, including internal symlinks.
No global tool installation or JDK replacement occurs.

`prepare` invokes the existing Jenesis artifact producer and creates a
content-addressed ordered compiler closure. `verify`, build and fusion reject
stale source inputs. Each native attempt has a fresh directory, original raw
streams and process receipt. Fusion validates producer seals and operates on
copies. Current source changes do not retroactively invalidate an unchanged
historical experiment as evidence, but they prevent blindly reusing its inputs
for a new build. Regenerate inputs explicitly and compare actual relevant bytes.

The bundle declares both runtime loaders. On macOS arm64 invoke
`ape-loader-macos-arm64 interlis-compiler.com <CLI arguments>`. This avoids the
shell bootstrap's first-run C compiler dependency. The loader is compiled at
packaging time from unmodified pinned vendor C source using the recorded host
Clang/SDK. Linux's supplied loader is unmodified vendor ELF. No Windows runtime
or native Linux proof is implied by including a loader.

Small explicit drivers: `f0-gate.cjs`, `core-smoke.cjs`, `cli-probes.cjs`,
`network-probes.cjs`, `slice-checks.cjs` and `bundle.cjs`. Read their argument
lists before use. Outputs must be fresh absolute paths. `http-probe-build.cjs`
and `http-probe-run.cjs` isolate the Java HTTP boundary without product classes;
an optional provider argument tests image-time selector selection. Diagnostic
receipts preserve actual child failures, even when the diagnostic driver itself
completes normally. They are not product PASS gates.

`report` expects `report-request.json` in its evidence directory. Schema1.0:
`candidateManifest` is an absolute bundle manifest path and `entries` is a
nonempty list of absolute `path`, exact `sha256` and `kind`. A `receipt` also
specifies `expectedExitCode`; a `cases` report specifies exact `requiredCases`;
`facts` only verifies the selected file's identity. Reports produce an inventory
on stdout, never an automatic feasibility/platform verdict. Original raw
streams and a reasoned decision remain necessary.

Public TLS keys under `testdata/ape/tls` are disposable fixtures, never private
credentials. The standard delivery contains no fixture truststore. See the
assignment's FEASIBILITY_REPORT.md and TEST_LEDGER.md for actual coverage and
unexecuted cases. No release or production suitability is asserted.

This investigation ends in NO_GO for the pinned vendor: its
System.currentTimeMillis substitution uses CLOCK_MONOTONIC, breaking valid TLS.
ClockProbe compares it directly to Instant.now. Build that small diagnostic with
`node tools/ape/http-probe-build.cjs /absolute/toolchain.lock.json sun.nio.ch.PollSelectorProvider ClockProbe`.
The current compiler recipe retains the demonstrated image-time Poll-provider
selection; no completed corrected compiler bundle or TLS success is asserted.
The delivered historical F2 candidate uses the original recipe.

When wrapping a long command for evidence, explicitly exceed its own bounded
timeout, e.g. `node tools/ape/record.cjs /absolute/new-output native --timeout-ms 2100000 java ...`.
The default is60s. Named `ape-` containers from nested recorders register with
the outer supervisor and are force-removed on timeout. The explicit
`record-container-check.cjs /absolute/new-output` regression exercises that
Docker lifecycle without a native build.
