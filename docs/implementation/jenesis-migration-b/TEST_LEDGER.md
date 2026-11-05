# Tatsächliche Prüfungen

Externe Belegwurzel:
`/Users/stefan/sources/interlis-java-jenesis-b-evidence/`.
Keine Rohreports werden nachträglich auf neue SHAs umgeschrieben.

## B0 — JB-01–03/31/32/37

- Enginepin: setup.py überprüft194 Originalfiles, `B0/pin-check.json`.
- `node B0/run.cjs`: zwei echte Enginebuilds, POM und native Modularlayout,
  jeweils Exit0. Java25 explizit; argv/cwd/Exit in *.process.json.
- `node B0/hook-final.cjs`: echter gehashter Assemblyhook; normal3Invocations,
  Methodenselektion1, absichtlicher Fehler4Invocations/1Failure/JUnitExit1,
  leere Tag-Auswahl0Invocations/JUnitExit2. Negative Engineprozesse Exit1.
  Originale unter hook-final-*-raw, inklusive argv/stdout/stderr/Exits/events.
- `node B0/hook-gates.cjs`: benannter Main und negativer Internalimport
  für beide Produktlayouts; positiv Exit0, javac negativ Exit1 mit
  `does not export`. Descriptorinventare separat.
- `node B0/reference.cjs`: frischer tatsächlicher Modelbuild24Tests,
  Exit0, null Fehler/Skips. Nur externe Cacheartefakte vorab kopiert,
  keine eigenen Maven-/Gradleprodukte. Receipt und Rohberichte kopiert nach
  `B0/model-reference-raw`; keine Compiler-/Korpus-/Benchmarkvollrunde.
- `python3 B0/a-audit.py`: alle referenzierten originalen A4-Reporthashes,
  Exits und XML-Fehler/Skips geprüft;1297/1304 historische Fälle, kein neuer Lauf.
- Reiner OpenCode2.0.28-No-tools-Preflight: regulär Exit0/PREFLIGHT_OK.
  Kein fachlicher Review und noch keine finale Alias-/Providerfreigabe.

Frühe Recorderprobe und Korrektur sind in DECISIONS D2 disponiert;
nur finale run~-Rohberichte belegen die erwarteten Failure-/Emptyausgänge.
Probehelfer benutzen lokal Python zur Inventarisierung; der Produktbuild
erhält dadurch keine Pythonabhängigkeit. Keine Native-/APEausführung.

## B1 — JB-05/06/08, erste B1-Anteile von JB-04/07/13

- Java25 `ArtifactContracts`:29 Buildgrenzenprüfungen, Exit0
  (`B1/adapter-final.*`). Fehlende/mehrdeutige Inventare, Artefakte/Sources,
  falsche eigene Version, manipulierte JAR mit aktualisierter lokaler Hashzeile,
  lückenhafte/doppelte Runtime und ausbrechende Symlinks werden abgelehnt.
  Clean erhält Engine-/Adapter-/Specsentinels; Lockreentry scheitert unmittelbar.
- Echter selektierter Modelbuild: DecimalLiteralTest3JUnitfälle, Exit0
  (`B1/selected-final.*`, normal-originals mit Receipt/XML).
  Fehlende Auswahl und unbekanntes Hostprofil jeweils Exit1;
  `artifacts --module interlis-model` Exit0 ohne Testbehauptung.
- `host --profile python-free`: realer Abschluss über finalen Adapterstand,
  Exit0 (`B1/host-final.log`, host-originals). Explizites Layout und echter
  Sourceguardbericht unter target. Compositeaufruf ohne Lockreentry/Deadlock.
- `sdk-check`: Exit0. Neun SDK-Prozesse, bestehende CLI-Prozessfälle und echter
  Archivconsumer. Originale argv/Exits/Reports/Lieferbytes:
  `B1/delivery-originals`. Kein normaler JVM-Volltest aus Artefaktproduktion.
- `contracts`: Exit0. Frische272 Produzenteninvocations und vier Consumer;
  Originalreports/Projektion/Normen/Runbindung: `B1/contracts-originals`.
  Normale Verificationfälle des initialen Builds stehen in normal-originals.
- `B1/cache-probe/run.cjs`: isolierte Kopie mit den Adapterinputs, vier echte
  selektierte Compilerläufe Exit0. Testschritt executed → skipped → executed →
  executed bei unverändertem Stand, wirksamer Fixtureänderung (Kommentar) und
  echter Prozesskonfigurationsänderung. Logs/events/XML/java.args archiviert.
- Reale Inventarprobe: alle zehn JAR-/POM-/Sources-Ergebnisse, Runtimeclosures
  und normalisierte Maven-Stagingrolle erreichbar, Exit0 (`inventory-final.log`).
  Temporäre Probequelle extern archiviert und aus dem Checkout entfernt.

Zusätzlicher Testhelfergegenfall und Staginggetter ändern keine gelieferten
Compiler-/LSP-/Verificationklassen. SDK-/Contractoriginale bleiben frühere
tatsächliche Läufe; keine zweite semantische Vollrunde wegen eines Testhelfers.
Altspurentfernung/weitere Hostprofile bleiben B4, Module/Starts B5/B6.
Noch keine Wasm-/Extensionintegration von B.

## B2 — JB-09–13, B2-Anteile von JB-06/10/31/32/37

- `wasm-export` erzeugt vollständige JAR-/POM-/Sources-Inventare und endliche
  Runtimeclosures. Tatsächlicher CLI-version-Prozess erzeugt eingefrorene
  Metadaten. Original: `B2/snapshot-original`, Referenz in snapshot-reference.json.
  Inhalt und variable Herkunft sind getrennt; unabhängige Referenz liegt neben
  dem immutable Snapshot. Kein Test-PASS aus Erzeugung.
-13 echte Node-Testinvocations am realen Java-Snapshotreader, null Fehler/Skips:
  `B2/snapshot-tests.xml/log`. Erfolgreicher Originalimport, falsche Referenz,
  Schema/Empty, Missing/Extra, JAR+lokale Hashzeile, Sources, POM-GAV, Parent,
  Traversal, absoluter Pfad und Symlinkausbruch. Nur Buildtoolklassen separat
  für Fixtures kompiliert; keine der zehn Produktquellen per Test-javac gebaut.
-33 Buildgrenzen-/Clean-/Lockprüfungen Exit0 (`final-output-contracts.log`):
  zusätzliche Targetroot- und Active-consumer-Gegenproben. Kein Quellenlöschen.
- Gradle-Graph und tatsächliche Builds kompilieren nur Backendadapter.
  `teavm-main-final` und `webimage-main-first` über den Projekteinstieg Exit0:
  echte TeaVM0.16.0-JS-/WasmGC-Erzeugung und Oracle-GraalVM25.0.3-Web-Image,
  Binaryen133, Node24.22.0. Je Runtime gültige Unicodeeingabe und gezielt
  ungültiger Typ über neun reale Protokollrequests. Kein Browser-/Vollkorpus-PASS.
  Originale Run-/Classpath-/Codegenreceipts und Runtimebytes in runs-originals,
  runtime-originals und artifact-origins.json; frühere schmale Erstproben bleiben
  separat erhalten. Tool-seitige Disassembly/Emscripten-Skips sind keine Laufzeit-
  Pflichtfälle und werden nicht als Tests gezählt.
- `warm-cache/comparison.json`: zwei tatsächliche Jenesis-Produzenten mit
  identischem GAV, veränderten Model-JARbytes (privates Scratchfeld), zwei
  Auflösungen desselben warmen isolierten Gradlehomes. Ausschliesslich lokale
  Snapshot-Mavenrepos, offline, keine Maven-/Produkt-Caches vorausgesetzt;
  sämtliche sechs Runtime-GAVs/Hashes stimmen jeweils mit ihrem Producer überein.
- Fehlende Übergabe sowie fehlendes JAR scheitern auch mit alten eigenen
  Gradle-JARs und isolierter globaler Mavenattrappe. Die frühe Missing-JAR-Probe
  verwendete versehentlich den Hash eines neueren Snapshots und scheiterte schon
  an dieser Referenz; sie belegt keinen JAR-Fall. `missing-jar-fixed` verwendet
  die unabhängige Originalreferenz und weist den fehlenden Inventarinhalt nach.
- `external-conflict`: erzwungenes echtes ANTLR4.13.1 wird vor JavaCompile vom
  Runtimeguard abgelehnt. Der Guard läuft auch bei UP-TO-DATE-Produzenten.
  `resolved-guards-positive` und `output-guard-positive` jeweils Exit0;
  `output-symlink` Exit1 vor Kompilierung, externer Sentinel unverändert.

Die letzten Änderungen verstärken nur die vor-/nachgelagerten Output- und
Resolverguards; Generatoroptionen, Backend-Javaquellen, Toolpins und tatsächlich
getestete JAR-/Runtimebytes bleiben unverändert. Dafür nur eigene gezielte
Guardproben, keine zweite volle Compiler-/Browser-/Performancekampagne.
Die komplette JVM-Gradlespurentfernung bleibt B4; B2 behauptet sie nicht.

## B3 — real Editor consumer (2026-11-05)

JB-12/JB-13: `editor-bundle` uses normalized LSP closure, the pinned Node24 renderer producer and pure assembler. `dev:prepare` in the isolated Extension worktree actually invoked it, imported the exact structured producer result and compiled TypeScript/esbuild successfully (exit0). The first preparation failed before building because of a circular CommonJS export; the export order was corrected, then the second preparation succeeded. Both original logs/exits remain. The standalone importer gate passed eight actual-bundle checks, including wrong Core SHA, missing/changed/extra JAR, changed renderer, symlink output sentinel and dirty release rejection. Import ran with process-start APIs replaced by throwing guards.

The isolated downloaded VS Code1.140.0 host ran against `build/dev-extension`, with isolated user/shared/extensions directories: activation/commands, real definition and dirty-buffer restart, shutdown, actual process exit0. B3 intentionally remains the prior classpath LSP launch; this is not JPMS proof. Runtime pin unchanged. Evidence: external `B3/{prepare-first.*,prepare-second.*,import-gate.*,extension-host.*,bundle-originals,run-originals,result-original.json}`. No personal extension installed.

## B4 — only JVM builder; explicit host and release plans (2026-11-05)

JB-01–JB-15/JB-29/JB-37 (B-I scope): settings now includes exactly the two backend adapters; all ten JVM Gradle scripts and the three JVM host/contract/conformance scripts are removed. JVM versions/scopes/pins come from POMs; unused catalog entries and the duplicate Gradle productVersion were removed. Active host/release, Core/Extension CI, process tools, README, spec03 and its decision/index references were migrated. Historical evidence was not rewritten. Current process-binding overlays explicitly replace deleted Gradle sources with their POM/profile owner.

Actual targeted gates (external `B4/`):

- `fresh.cjs`: all ten artifacts plus normal model tests (exit0), no Gradle/Node/npm/Maven/Ant/native executable available; deny programs were never called. No own Gradle scripts/outputs existed. `fresh-normal-originals` and `fresh-reports-originals` retain actual reports. Before the lean-consumer proof, that scratch's generated engine and own JVM src directories were removed. Because this deliberately removed artifact originals, a second **artifact-only** fresh generation (`fresh-final.cjs`, exit0) retains actual full engine/Maven outputs and source inputs in `fresh-final-core`. No JVM test suite was repeated for that retention. The subsequent verification-only version-helper fix was separately rebuilt and verified through actual SDK consumers. Nine product modules were unchanged.
- `lean-backend`: actual fresh TeaVM JS/WasmGC codegen and both two-case Node smokes, exit0, using explicit B2 snapshot d0f921…/manifest d9c66d…; own JVM src directories and own engineergebnisse were absent. Raw argv, codegen/runtime receipts and reports in `lean-backend-originals`; current two-project Gradle graph. B2 snapshot was deliberately reused as immutable input; this is not a claim about later modular bytes.
- `ArtifactContracts`:33 boundary/clean/lock/sentinel checks, exit0, original compiler output and logs retained. Sources, specs, independent outputs and external sentinel preserved.
- `VerificationLayoutTest` + `PlatformProcessesTest`:5 actual normal tests, exit0; explicit new paths, missing roles, XML integrity and duplicate directory failure paths. Earlier layout-only3 tests also passed; no full JVM suite run in B4.
- `host --profile sdk`: real fresh CLI/SDK/archive consumers exit0 after POM version migration. A local variable-name collision caused one compile failure, then was corrected; both original logs retained. Current helper reads authoritative root POM, no Gradle version fallback.
- `host --profile conformance`: selected actual `ili23.UnicodeDoc23Test.simpleOk`, original pinned external runner, normative CLI test helper, exit0. The first attempted nonexistent filter was rejected by the runner as no selected cases; it was a mistaken test input, not a success. No full conformance suite claimed.
-120 Node layout/release/performance checker tests, zero failures/skips;17 distribution inventory tests after authoritative POM fixture migration (the obsolete invalid-version fixture was corrected after it failed to mutate the new source). The actual `host --profile release-evidence-checker-test` also ran successfully under its explicit hashed layout. A final18-case layout/distribution subset passed after isolating clean-generation layout inputs.
- Final Python-free Java host: exit0. Extension importer hardening:9 actual-bundle checks, exit0, including preserved nonempty-unowned-output sentinel. This did not change server/runtime or renderer bytes; B3 host evidence reused.
- `release-check --profile P3` without current platform evidence: actual exit1 at preflight, before product/performance/backend work. This is expected rejection, not a P3 acceptance.

The broad normal JVM/JPMS closure is due in B5; final modular backend/Editor bytes are due in B7. Full347-root performance, all browsers/Safari and external Linux/Windows CI are NOT_REQUIRED_FOR_B at B4: no changed semantics/budgets and no P3 release requested. Their full release obligations remain in explicit closed profiles and validators. Missing mandatory B codegen/consumer tests would not be covered by this disposition.

## B5.1 — first modular batch

JB-16–JB-23/JB-37: actual model/compiler product javac has module inputs and explicit descriptor output.25 model +789 compiler normal invocations, zero failures/skips. Original A-native24/788 identities are a complete multiset subset; exactly one real ModuleIdentityTest was added per module. Raw report bytes, engine origin and selected inputs in `B5/{model-normal-originals,compiler-normal-originals}`; logical comparison in `batch1-logical-case-coverage.json`. Existing production `.class` bytes/FQCNs and non-META-INF resources are unchanged against B4's retained unmodular JARs; only descriptors added (`batch1-product-comparison.json`), actual javac supplements retained.

`module-check` exit0: actual graph/name/split-package check, successful unpatched named SDK compiler result, same product JARs successful on classpath, compiler internal import explicitly rejected because its package is not exported, reflective constructor accessibility denied. Raw commands/process exits/consumer outputs and byte inventories retained in `batch1-boundary-originals`.

Actual adapter gate: named product and patched tests asserted, selected existing parameterized method (without caller-supplied signature) executed **two** real invocations, exit0. Private identical-source worktree mutation: deliberate wrong module assertion gave actual JUnit1 + engine1 and failure XML; nonexistent class selection gave engine1; copying actual SourceId production source into test sources was rejected by the FQCN-shadow guard after native test preparation. All three expected negatives and full raw failing-step output captured in `adapter-*-originals`, driver exit0. No deliberate failing fixture was added to maintained tests. No broad suite repeated for these controls.

B5.1 minimum gate passed. B5.2 is now due; upstream25/789 normal case evidence may be reused if relevant module bytes/contracts stay unchanged.

## B5.2 — middle modular batch

Actual normal invocations: workspace24, repository113, projection23, documentation9, bridge25; zero failures/skips, original reports/exits/args retained under `B5/*-normal-originals`. All original A-native23/112/22/8/24 identities are preserved exactly, with one new module identity test per module. Existing product class bytes/FQCNs and non-META-INF resources (including both authoritative schemas) remain byte-identical to B4 unmodular products. `batch2-case-and-product-comparison.json` records exact bindings; actual modular product-javac supplements are retained.

The independent named SDK/API consumer resolves and directly references all seven current modular APIs, compiles a real model and rejects unexported compiler internals; same JARs work on classpath, reflection stays closed. Seven-module descriptor/graph/split-package gate exit0, raw subprocess originals in `batch2-boundary-originals`. `artifacts` only populated the complete normalized inventory for this graph gate, not an extra test claim. The upstream25/789 module normal suites were not repeated: their product classes/descriptors and asserted behavior stayed unchanged.

B5.2 minimum gate passed. B5.3 (CLI/LSP/verification, native report bindings and process tests) is now due.

## B5.3 — complete own graph and fresh contracts

All ten own products are explicit modules. CLI142, LSP77 and verification90 normal invocations passed, no failures/skips. Original A-native141/76/86 identities all remain. Extras: one ModuleIdentityTest per module, one new native-container integrity test, and two JvmBuildParityTest cases already present in the B baseline after the A evidence run. Full normal modular phase coverage is therefore **1317 actual invocations** (A1304 +two existing baseline-source cases +ten module assertions +one native report case). This is a logical identity multiset comparison, not inference from totals. See `batch3-logical-case-coverage.json` and original per-module report/receipt directories. No upstream full suite was repeated solely for phase/receipt changes.

Failures and targeted corrections are retained: CLI test compilation needed its existing controlled HTTP server's test-only read; native LSP product javac needed the two native POM aliases; LSP test compilation needed its controlled HTTP read. The first executable LSP77 run had one formatting failure because jdk.unsupported was unresolved. Its descriptor now names the actual Gson allocation requirement; all77 LSP cases then passed, including modular child processes. Internal verification product compilation needed jdk.httpserver for the real CLI process fixture; its90 normal cases, including real child timeout/nonzero distinction and native report counter/identity negatives, then passed. No semantic expectation, foreign pin or vendor byte was changed. Original failing-step supplements/XML and actual exits are retained.

Ten-module boundary gate passed: standard Configuration/descriptor/split checks, named API consumer directly linking CLI/LSP and all seven prior public modules, real compiler result, identical product JARs on classpath, internal import rejection/reflection closure. `batch3-boundary-originals` contains actual argv/exits/artifact hashes. CLI/LSP existing class files and all non-META-INF resources are unchanged against B4. Only four internal verification class files changed for report/child-launch behavior; explicit inventory in `batch3-product-comparison.json`. All five foreign module JAR hashes match B0, including both aliased LSP4J copies (`foreign-module-bytes-unchanged.json`).

Fresh **modular** contracts: producer compiler240, model8, workspace12, repository2, bridge2, lsp4, cli4; consumer4. All actual engine steps/runner exits0, exact selected identities and parameter invocations validated by the existing projection/consumer assertions. `modular-contracts-originals` retains native XML, source/patch/artifact bindings, engine events, projection metadata and manifests. The prebuild ran `artifacts`, no repeated normal test suite. The106 targeted Node release/evidence/artifact-comparison tests passed after native-container metadata migration, retaining minimum thresholds and adding lost/duplicate logical-suite/raw-count negatives.

B5 minimum gates for all three batches passed. Regular distribution/Extension launch and runtime delivery are due in B6, modular backend bytes in B7. No ordinary classpath proof is relabelled as a modular test proof.


## B6 — modular launch and private runtimes

Raw evidence: external B6 directory. `sdk-modular-first` exit0 covers SDK9/CLI55/archive with actual module launch; `cli-canonical-normal-originals` contains the final compiler.cli descriptor's 142 native normal invocations. `prepare-modular-first` exit0 exercised actual prepareDev and schema3 module bundle. `compiler-runtime-eighth` exit0 and `compiler-runtime-originals` preserve actual per-process argv/env/exit/outputs: runtime version/modules, version JSON, Unicode filename valid/invalid/format, missing root/dependency failures, actual image javac SDK consumer, real HTTP/HTTPS index/model reads, scoped test truststore and jpackage app-image launch with foreign JAVA_HOME. The technical app-image version 1.0 meets macOS requirements without changing Maven SNAPSHOT version. No production format selection or installer.

Failed runtime-adapter iterations remain original: initial javac errors; jdeps reduced output incorrectly assumed JDK-only; duplicate javac launcher already supplied by jdk.compiler; insufficient PATH omitted POSIX utilities; an invalid Unicode model identifier fixture (Unicode filename retained after correction); jpackage rejects SNAPSHOT then leading-zero app versions. Each was diagnosed before retry, no failed run is PASS. The eventual real positive run supersedes them.

Extension `extension-jlink-fifth` and `extension-full-jre` exit0 retain unchanged vendor pins. jdeps root analysis plus actual descriptor requires feed finite JDK roots, with java.logging/java.sql and existing dynamic providers explained. Foreign/own application JARs remain external. Earlier jlink attempts exposed reduced own-module dependencies and Node cpSync not materializing legal links; a minimal actual Node probe and original errors are retained. Explicit validated link materialization now works for full and reduced runtime. Both actual VSIX are packaged and extracted (`vsix-pair` exit0); sizes64,753,619/41,670,186 bytes, reduction35.6481%, existing >=10% rule selects jlink. `packaged-tests` exit0:13 real checks including HTTP protocol and corrupted runtime. `packaged-https` exit0:actual TLS LSP/navigation/export/shutdown; `packaged-extension-host` exit0:real isolated VSCode1.140, activation/definition/dirty-buffer restart/stop. `launch-negative` exit0:8 launch tests including wrong module/arbitrary flags. `current-editor-check` exit0 proves the retained convenience profile uses actual extension import/host, no obsolete shadow assembly. Initial build without staged generated protocol types failed and is retained; pure current bundle import followed by build passed. No personal installation, Windows/Linux launch or CI run claimed.


## B7 — final modular integration

External `B7/modular-export` exit0 exports the actual modular artifacts; `modular-snapshot-reference.json` records digest6d9100f2576e3d736ab3cc1208db7d70d97523f6771fbbeda6096c2a6d66e563 and manifestSHA d065ffbb783eba6efec7d55b57490ee5e8a35367307c4f33a5187065201e2cb8. Actual TeaVM JS/WasmGC and Web Image code generation plus the valid/rejected runtime cases all exit0 (`backends-driver`, `teavm-originals`, `webimage-originals`). No descriptor stripping, separate nonmodular product or browser claim.

`final-contracts` exit0: fresh272 selected native JPMS producer invocations and4 consumers, original native XML/projections/manifests/engine events in `final-contracts-originals`. Only current process source bindings were reconciled (`binding-reconciliation.json`); historical G2 links/assertion expectations unchanged. `final-normal-reuse.json` explicitly compares all original native phase product/runtime JAR hashes and all recorded src hashes with final relevant products, including the B6 canonical CLI142 run:1317 normal invocations remain covered without a duplicate normal suite.

`repro-driver` exit0: two independent clean committed private input snapshots, each fresh engine outputs, `sdk-package` without tests. All32 staged JVM binary/source JARs/POMs, SDKzip and four launchers byte-identical; source inputs/original generation trees/logs retained. A subsequent standalone module-check after contracts exposed missing unrelated module inventories in the selected last outcome. The gate now explicitly requests all artifacts instead of consuming an implicit last subset. `final-boundary-second` exit0: final ten-module graph, named API consumer, same JARs on classpath, forbidden internal import and reflection denial. `repro-final-product-binding.json` proves this check-only helper change left all independently generated Maven product bytes unchanged. No second product compiler or full-suite retest.

`native-reports-second` exit0 exercises the actual native-report adapter against unchanged original1317 normal reports plus fresh276 contract cases:1593 actual testcases,398 logical suites,139 original XML files. Existing minimum1411/352 and exact required logical reports pass. Removing a real native report is rejected; original restored. Private probe sources/argv/exits are retained and not shipped. First harness used the wrong consumer subdirectory and initial subset-dependent module-check failed; original errors remain, neither is PASS. `source-guard` exit0 executes the real final PythonFreeScan host and its negative canaries. `final-source-audit.json` records the active two-backend Gradle graph and finite launcher flags. `current-import-gate` exit0 adds unknown-root/extra-JVM-flags/own-automatic-module counterprobes to real bundle import (12 checks). Existing B4/B5 release/evidence validator counterprobes and unchanged platform thresholds are reused. New clean candidate bundle/lock binding and final package receipts are attached externally with the candidate pair.

No full corpus, benchmark, browser matrix or P3 release rerun is due solely to this build/module ownership migration; compiler semantics/benchmark contracts are unchanged. No Windows/Linux native run or CI dispatch is claimed. Actual required codegen/consumer/host integrations above are distinct from these deferred release obligations.


## Unabhängige Runde 1 und gezielte Korrekturen

Originalrunde `reviews/pair-92cb361-63e67ff/round-01`: frische Sitzung ses_e533f7743ffed1SVT0aYMjfaWD, OpenCode2.0.28/direct DeepSeek Flash1017, alle40 Kriterien und14 eigene Gegenproben. Urteil FAIL; Quellen unverändert, Prozessende exit1 ohne Signal/Timeout. Dies ist ausdrücklich keine gültige PASS-Abnahme. Die Findings F-01–06 werden im nächsten vollständigen Review erneut bewertet.

Rohbelege der Korrekturen: `B7/corrections-round01`. F-01: tatsächlicher Buildhelper schreibt Windows-native relative Pfade portabel; Java-Verifikationslayout und Node-Leser akzeptieren denselben erzeugten Propertiesinput. Neue VerificationLayoutTest-Probe plus bestehende Fälle bestehen. Der erste Modul-Normallauf hatte90 erfolgreiche Originalinvocations und einen neuen Fixturefehler (Layoutdatei ausserhalb des vorgesehenen target-Roots); Fehleroriginale erhalten. Nach gezielter Fixturekorrektur bestehen alle vier Layoutfälle. Die unveränderten90 Originalmethoden plus die neue erfolgreiche Probe ergeben logische Verifikationsabdeckung91 und normale Gesamtabdeckung1318. Kein vollständiger JVM-/Korpuslauf wiederholt.

F-02: tatsächliche sdk-package- und sdk-check-Läufe exit0 einschliesslich SDK9/CLI55/Archivconsumer; beide SDKzipSHA a10b89b27f6de1650d4dc3b0b27a0c663f113b8cea7281a5314f16242bbd2958. Das ist auch der originale SHA beider sauberen B7-Erzeugungen: der bisherige reine Paketierungspfad bleibt bytegleich, der Prüfpfad stimmt nun damit überein. Originale zwei saubere Erzeugungen werden anhand der finalen tatsächlichen Lieferartefakthashes weiterverwendet; keine künstliche zweite Normalprüfung.

F-04: V0–V5-Transport aus package.json/scripts entfernt, historische Reviewberichte erhalten; auch bisher übersehener GUI-Resilience-Guardpfad nutzt nun den zentralen Layoutleser. Syntaxprüfung und tatsächlicher kleiner OS-Interpreterguard liefern den aktuellen target-Pfad; keine vollständige GUI-Resiliencesuite für den blossen Belegpfad wiederholt. Unveränderter echter gebündelter Extensionhost aus B6 bleibt über identische Produkt-/Runtimebytes wiederverwendbar.

F-05:14 Snapshotgegenproben erfolgreich, zusätzlich tatsächlich erzeugtes automatisches Own-JAR durch den gemeinsamen Deskriptorguard abgewiesen; fehlender Quelldeskriptor vor Produceraufruf abgewiesen. F-06: tatsächlicher Host-Node-Checker im expliziten privaten Verbraucherbarrieretest; gleichzeitig startender realer all-module artifacts-Build exit0; danach tatsächliche Nodevalidatoren, Leasewiedererwerb, Ergebnisnormalisierung und Hostabschluss exit0. Keine Produktions-Testhooks und kein vollständiger portable-Normallauf für diese isolierte Lockfrage. Die erste Buildhelperkompilierung nach der Korrektur hatte einen typisierten Artifact-Zugriffsfehler; Original erhalten, danach Buildtools/SDK/Host erfolgreich kompiliert.

Die Reviewerempfehlung echter Windows-/Linux-CI-Läufe bleibt als Releaseplattformvorbehalt offen: auf diesem macOS-Host nicht ausführbar, kein Push/Dispatch autorisiert. Die konkrete Windows-Pfadverletzung ist durch portable Produzenten, strikte Java-/Node-Inputs und native Pfadfixtures korrigiert; es wird keine Windows-/Linux-CI-Ausführung behauptet. Neuer Kandidat benötigt einen frischen unabhängigen Gesamt-Review.


Weitere gezielte Abschlussbelege derselben Korrekturrunde: `normal-coverage.json` prüft das genaue Multiset der90 ursprünglichen erfolgreichen Verifikationsfälle gegen den ersten Korrekturlauf und vereinigt es mit dem korrigierten Vierfällelauf zu91; übrige neun Modulquellen und Produkt-/Runtime-JARhashes stimmen mit ihren ursprünglichen Normalbelegen überein. Frische272+4 Verträge unter `contract-b62b5629-6f85-476b-be25-1650ee1e0267` exit0, unveränderte Originale in `contracts-originals`. Neues vollständiges Modul-/API-/Classpath-/Kapselungsgate exit0. Zusätzlich wurden wegen des SDK-Produzentenfixes auch zwei neue getrennte saubere JVM-/SDK-Erzeugungen ohne Tests durchgeführt: `corrections-round01/repro-result.json`,32 bytegleiche Artefakte. Sie stimmen auch mit der korrigierten tatsächlich geprüften SDK-Auslieferung überein. Die alten Reproberichte bleiben Originale; kein zusätzlicher JVM-Normallauf. Aktueller PythonFree-Sourceguard und echter kleiner OS-Interpreterguard exit0. Vollständige Windows/Linux-CI und die GUI-Resiliencesuite bleiben aus den oben genannten Gründen unbehauptet.
