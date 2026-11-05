# Entscheidungen aus B0

## D1 — POM-Discovery beibehalten

Die unveränderte gepinnte Engine kompiliert zwei POM-Projekte mit normalen
`src/main/java/module-info.java` tatsächlich auf dem Modulpfad. Originalargv
unter extern `B0/pom/target/**/supplement/javac.args`, Produktprobe `pom.log`.
Beide Deskriptoren werden beschrieben, ein benannter API-Consumer läuft und
ein Import von probe.internal.Hidden scheitert ausdrücklich am fehlenden Export.
Der native Gegenversuch `modular_to_maven` baut ebenfalls echte Module und
generiert Mavenmetadaten, leitet hier aber andere GAVs aus den Modulnamen ab.
POM bleibt deshalb die einzige GAV-/Scope-/Pinquelle; kein zweiter Produktweg.
Die private Gegenprobe ist kein migriertes Produkt und kein Endzustandsnachweis.

## D2 — enger gehashter Testschritt

`B0/pom/build/Probe.java` registriert über AssemblyDescriptor.andThen einen
echten Engine-Schritt nach der Produktproduktion. Tests, Testtools und Hookquelle
sind gehashte Stepinputs. Nur Testquellen werden mit javac kompiliert; das
unveränderte Produkt-JAR bleibt auf dem Modulpfad. `--patch-module` ergänzt
das Testverzeichnis, endliche testseitige reads/opens ermöglichen JUnit.
Keine Produktionsneukompilierung, kein Enginepatch, keine öffentliche Hilfs-API.

Die endgültige Probe beweist package-private Zugriff, Named-Module-Identität
von Produkt und Test, zwei Parameterinvocations, Methodenselektion,
Assertionfehler (JUnitExit1) und echte leere Tag-Auswahl (JUnitExit2).
JUnit schreibt originale XMLs; `whitebox-gate.json` bindet sie an Prozessargv,
Exits und Engineevents. Die frühe Recorderfassung kopierte bei fehlgeschlagenen
Schritten das erhaltene vorherige output statt des fehlgeschlagenen run~.
Diese alten `hook-failure-raw`/`hook-empty-raw` sind **kein** Failurereportbeleg.
Die korrigierte finale Aufnahme verwendet bei Fehlern ausdrücklich run~;
die vier finalen kleinen Kontrollen wurden dafür neu ausgeführt.

Produktintegration, Kollisionsschutz, sämtliche Regressionen und unabhängige
ungepatchte Produktgrenzen bleiben B5-Gates; die Probe nimmt sie nicht vorweg.

## D3 — Fremdmodulnamen an tatsächlichen Dateien

`B0/foreign-modules.json` inventarisiert SHA, Pakete und jar-Ausgaben:
ANTLR org.antlr.antlr4.runtime, LSP4J org.eclipse.lsp4j und
org.eclipse.lsp4j.jsonrpc sind automatische Fremdmodule. Gson com.google.gson
und ErrorProne com.google.errorprone.annotations besitzen MR9-Deskriptoren.
Die Jenesis-Downloadcachenamen mit Tilden sind keine Modulpfad-Dateinamen:
LSP4J kann daraus keinen gültigen automatischen Namen ableiten. Die Probe
verwendet byteidentische kanonische artifactId-version.jar-Dateien und prüft
MR-JARs mit `jar --describe-module --release 9`. Produktclosures müssen diese
Namen ausdrücklich erhalten; keine Fremd-JAR-Umschreibung oder Versionserhöhung.

LSP/Gson-Reflection, Services, tatsächlicher Backendclasspath und Runtimebilder
sind noch ungeprüfte Verbraucherrisiken, kein aus dem Inventar abgeleitetes PASS.

## B5.1 — explicit model/compiler and the real test adapter

| Own module | requires / reason / transitive | exports | opens / services / JDK |
|---|---|---|---|
| `guru.interlis.model` | none; own immutable contracts | model, model.query, source, diagnostics, execution; model.internal **only to guru.interlis.compiler**, whose maintained implementation imports shared type/format rules | none; java.base implicit |
| `guru.interlis.compiler` | transitive model (public request/result APIs); ANTLR runtime (internal parsing only), nontransitive | compiler, compiler.query, syntax, format | none; no ServiceLoader contract exists in these two modules |

The native engine compiles actual production with descriptors/module path. The narrow `JpmsTestRun` replaces the native observed test executor, retains its strict pinned JUnit resolver and binds maintained test sources/resources plus deterministic selection/input plan into an actual engine step. Test-only javac patches the producing named product; only compiled test classes/resources patch the test JVM. No own product JAR is on that JVM classpath. Explicit finite JUnit/test-only reads and test-package opens exist only in this step. The engine still prepares its ordinary test binary to detect/resolve the test framework; it is not the authoritative JPMS run, is never used as a product, and does not replace a production compilation. No engine source was changed.

Native JUnit XML stays unchanged. Separate engine receipts capture actual args, exits, test plan and patched input hashes. Source/product resource/FQCN collisions are rejected. Original case identities remain intact, including parameter invocations. `module-check` inspects real descriptors/closures/split packages, executes an unpatched named SDK consumer and the same JARs on the classpath, rejects internal javac access and checks reflection remains closed.

## B5.2 — middle DAG batch

| Module | requires / public-signature rationale | exports | opens / services / JDK |
|---|---|---|---|
| guru.interlis.workspace | transitive compiler and model: Workspace exposes analyses, requests and revision/model types | workspace, workspace.resolution | none; java.base |
| guru.interlis.repository | transitive workspace and model: resolution/source/catalog API; nontransitive java.net.http and java.xml for concrete host IO | repository | none; test-only jdk.httpserver reads, no product edge |
| guru.interlis.projection | transitive compiler and model: snapshot-bound DiagramInput/options/result API | projection | none |
| guru.interlis.documentation | transitive model and projection: checked-schema projector and GraphML input; compiler remains test-only direct dependency | documentation | none; java.xml is test-only for independent document parsing |
| guru.interlis.wasm.bridge | ordinary workspace/compiler/projection/model: portable public wire/session API does not expose their public types | bridge | none |

No direct ServiceLoader use exists in this batch; JAXP uses the platform's existing factory/provider contract. No production opens or test-server dependency was introduced. All five test modules include a real named-product/patch identity assertion.

## B5.3 — CLI/LSP/internal host closure

| Module | requires / transitive rationale | exports / qualified opens | JDK / services |
|---|---|---|---|
| guru.interlis.compiler.cli | transitive compiler/model/repository (typed CLI environment, diagnostics and repository observation renderers); ordinary workspace/projection (implementation) | exports cli; no opens | java.base; no direct ServiceLoader |
| guru.interlis.lsp | transitive compiler/model/workspace/projection/LSP4J/Gson (public server/editor DTO signatures); ordinary repository/documentation/JSON-RPC | exports lsp; opens **lsp only to com.google.gson** for DTO records and its authoritative package-private DiagramWireAdapter | jdk.unsupported is required: actual modular formatting failed when Gson could not allocate LSP4J Either3; enabling the existing allocator preserves behavior. No JSON-RPC opening needed. |
| guru.interlis.verification | ordinary compiler/model/workspace/repository/bridge/Gson, internal host-only API | no public exports; opens **verification only to com.google.gson** for existing process records | java.xml, java.management, java.compiler, jdk.compiler; jdk.httpserver only for its real controlled CLI process fixture, never delivered with CLI/LSP |

The pinned engine classifies a foreign JAR as modular only from a declared name/descriptor or alias sidecar. LSP4J's two pinned JARs declare neither; raw tilde cache names are not valid automatic names. Actual native production javac initially rejected their requires. Two finite **POM `jenesis.alias` comments** use the engine's native resolver to give byte-identical copies the B0-verified automatic names org.eclipse.lsp4j / org.eclipse.lsp4j.jsonrpc. No module-info or manifest was injected into those JARs, no modules.properties transformation, no vendor change/upgrade; all five foreign JAR hashes match B0. Their consuming LSP JAR carries the native alias metadata.

Test-only jdk.httpserver reads also cover existing CLI/LSP controlled server tests. LSP process tests now fork the real named LSP root. The internal verification child-launch helper preserves a named parent; only its explicit test-main case carries a test patch, ordinary FullCorpusCold children carry none. Product launchers are due in B6.

Native JUnit XML containers are not logical test suites. The P3-jvm-distribution-v3-jpms report/schema2 keeps original raw XML hashes, raw file count and a separate class-per-directory logical identity index derived from actual testcase/native IDs. The minimum1411 tests /352 logical suites is unchanged; documentation and all ten ModuleIdentityTest classes are additionally required. Old paths, lost logical classes, duplicate identities, changed raw hashes and malformed native IDs fail. Native XML is never rewritten. Existing projected contract reports remain separately bound to their original native reports.

`contracts` prepares tools through `artifacts`, not another ordinary verification test run; ordinary coverage belongs to build and its explicitly bound phase evidence. Fresh selected producer/consumer execution remains mandatory and is actually verified. Contract receipts now bind the actual patched classes and copied module/tool inputs as artifacts as well.

## B6 — canonical launch identities

The CLI module identity is aligned with the assignment target and uniform project-name mapping: **guru.interlis.compiler.cli**, main guru.interlis.cli.Main. B5 had first used the shorter guru.interlis.cli identity. Only the descriptor/name assertion changed; all142 CLI normal cases were rerun successfully for the final name. Other module normal suites remain valid. Regular launchers now use one explicit module root and lib-directory module path, preserving the existing Windows argument bridge and upstream license. No ALL-MODULE-PATH/ALL-DEFAULT, classpath fallback or test patch occurs in delivery launchers. A template guard also rejects an accidental bare Windows `set` command.


## Korrekturen nach unabhängiger Runde 1

Der frische OpenCode2/direct-DeepSeek-Review des ersten Kandidatenpaars fand F-01 bis F-06; Originalurteil und Quellengegenproben bleiben extern unverändert. F-01: gemeinsame portable Rollenproduktion, strikte Leser unverändert; Windows-native Schreibweise wird vor Speicherung normalisiert und in Java/Node gezielt geprüft. F-02: SdkProcesses speichert die tatsächlichen CLI-JSON-Bytes, dieselbe Quelle wie sdk-package; reale Paketierungs-/SDK-Prozesspfade ergeben bytegleiche Archive. F-03: der Reviewindex leitet den aktuellen sauberen Snapshot aus dem gebundenen Bundle ab und führt ursprüngliche Backend-Ausführungssnapshots separat; Byteidentität belegt Wiederverwendung statt historischer Headerumschreibung. F-04: alter V0–V5-Reviewstarter entfernt; GUI-Guardleser verwenden den zentralen Core-Layoutvertrag. F-05: gemeinsame Prüfung expliziter, korrekt benannter, gekapselter eigener Deskriptoren bei Produktion/Import und zwingende zehn Quelldeskriptoren im Modulgate. F-06: eigene invocationgebundene JVM-Lease wird während immutable Backend-/Node-Schritten suspendiert; ursprüngliche Inputs bleiben unveränderliche Nachbedingung, danach werden aktuelle Engineergebnisse unter wiedererworbener Lease normalisiert. Kein globaler Lauf/Listener, kein Testhook im Produkt und keine neue Buildengine.
