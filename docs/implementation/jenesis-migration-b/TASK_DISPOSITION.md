# B0 — Disposition der aktiven Einstiegspunkte

Die B0-Ausgangsdisposition ist nach B4 auf implementierte Besitzer aktualisiert.
Tatsächlich ausgeführte Abnahmen und nicht fällige breite Läufe stehen im TEST_LEDGER. Baselinequellen und sämtliche196 Task-/Kantenstellen sind extern
in `B0/source-inventory.json` mit Zeilen erfasst. Die Tabelle umfasst jeden
wörtlich registrierten Task und die beiden dynamischen Hostregistrierungen;
Java-/Application-Pluginlifecycles werden anschliessend separat disponiert.
Die zehn JVM-Gradleskripte und drei alten JVM-Rootskripte wurden in B4 entfernt.
Backendtasks bleiben ausschliesslich in den beiden Adapterprojekten.

| Quelle | Alter Einstieg | Geplanter Besitzer/Aufruf | Input-/Reportvertrag | Gate | Entfernung |
|---|---|---|---|---|---|
| build.gradle | checkNodeRuntime | Node-/Wasmprofil-Preflight | gradle.properties Nodepin; tatsächliche Nodeversion | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | checkPortable | host --profile portable | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | checkBrowser | host --profile browser | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | releaseCheckP3 | release-check --profile P3 | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | wasmBenchmark | host --profile wasm-benchmark | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | browserBenchmark | host --profile browser-benchmark | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | fullCorpusWasm | host --profile full-corpus-wasm | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | fullCorpusBrowsers | host --profile full-corpus-browsers | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | fullCorpusSafari | host --profile full-corpus-safari | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | fullCorpusEditorWasm | host --profile full-corpus-editor-wasm | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | fullCorpusArtifacts | host --profile full-corpus-artifacts | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | performanceCheckerTest | host --profile performanceer-test | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | performanceCheck | host --profile performance | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | sdkPackage | sdk-package | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | releaseCheck | release-check --profile P3 | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | sdkReproducibilityCheck | host --profile reproducibility | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | releaseEvidenceCheckerTest | host --profile release-evidence-checker-test | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | releaseEvidenceCheck | release-check --profile P3 (Validator) | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | diagramUiBuild | editor-bundle (Node: frontend/diagram-ui/prepare.cjs) | npm-Lock, Rendererquellen; Rendererprovenienz | B3 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | diagramUiPackage | editor-bundle (Node: frontend/diagram-ui/package-renderer.cjs) | npm-Lock, Rendererquellen; Rendererprovenienz | B3 | B4 nach erfolgreichem Ersatzgate |
| build.gradle | editorBundle | editor-bundle | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B3 | B4 nach erfolgreichem Ersatzgate |
| gradle/conformance.gradle | prepareConformanceSuite | host --profile conformance (Vorbereitung) | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/conformance.gradle | runConformanceSuite | host --profile conformance (Runner) | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/conformance.gradle | conformanceWrapperTest | build --module interlis-compiler-cli | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/conformance.gradle | conformanceCheck | host --profile conformance | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/host-verification.gradle | pythonFreeCheck | host --profile python-free | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/host-verification.gradle | semanticComparison | host --profile semantic-comparison | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/host-verification.gradle | geoCorpus | host --profile geo-corpus | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/host-verification.gradle | fullCorpus | host --profile full-corpus | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/host-verification.gradle | fullCorpusJvm | host --profile full-corpus-jvm | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/host-verification.gradle | observationPressure | host --profile observation-pressure | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/host-verification.gradle | editorPressure | host --profile editor-pressure | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/host-verification.gradle | jvmBenchmark | host --profile jvm-benchmark | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/host-verification.gradle | sdkProcessCheck | sdk-check | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/host-verification.gradle | sdkArchiveCheck | sdk-check | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/host-verification.gradle | platformCheck | host --profile platform | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/host-verification.gradle | conformanceEvidence | host --profile conformance-evidence | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/host-verification.gradle | compilerCliCheck | host --profile cli | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/host-verification.gradle | repositoryReferenceCheck | host --profile repository-reference | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/host-verification.gradle | referenceCheck | host --profile reference | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/host-verification.gradle | originalViewReferenceCheck | host --profile reference-originals | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/host-verification.gradle | viewReferenceCheck | host --profile reference-views | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/host-verification.gradle | unitReferenceCheck | host --profile reference-units | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/verification-contracts.gradle | prepareNormativeInputs | contracts (Vorbereitung) | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/verification-contracts.gradle | contractAssertionsTest | contracts (Produzent) | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/verification-contracts.gradle | contractTest | contracts (Consumer) | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| gradle/verification-contracts.gradle | compilerContractCheck | contracts | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-compiler/build.gradle | compileSdkExample | sdk-check | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-compiler/build.gradle | sdkExampleTest | sdk-check | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-compiler-cli/build.gradle | conformanceAssessment | host --profile conformance (Assessment) | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-verification/build.gradle | pythonFreeCheck | host --profile python-free | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-verification/build.gradle | semanticComparison | host --profile semantic-comparison | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-verification/build.gradle | geoCorpus | host --profile geo-corpus | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-verification/build.gradle | fullCorpus | host --profile full-corpus | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-verification/build.gradle | fullCorpusJvm | host --profile full-corpus-jvm | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-verification/build.gradle | observationPressure | host --profile observation-pressure | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-verification/build.gradle | editorPressure | host --profile editor-pressure | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-verification/build.gradle | jvmBenchmark | host --profile jvm-benchmark | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-verification/build.gradle | sdkProcessCheck | sdk-check | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-verification/build.gradle | sdkArchiveCheck | sdk-check | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-verification/build.gradle | platformCheck | host --profile platform | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-verification/build.gradle | conformanceEvidence | host --profile conformance-evidence | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-verification/build.gradle | compilerCliCheck | host --profile cli | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-verification/build.gradle | repositoryReferenceCheck | host --profile repository-reference | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-verification/build.gradle | referenceCheck | host --profile reference | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-verification/build.gradle | originalViewReferenceCheck | host --profile reference-originals | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-verification/build.gradle | viewReferenceCheck | host --profile reference-views | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-verification/build.gradle | unitReferenceCheck | host --profile reference-units | Normalisierte JVM-Closure, explizites VerificationLayout; originale Reports/Exits | B1/B4 | B4 nach erfolgreichem Ersatzgate |
| interlis-wasm-teavm/build.gradle | wasmContractTest | Gradle:wasmContractTest | Snapshot/Toolpins/eigene Adapter; Backendreceipt | B2 | behalten, Backedges entfernen |
| interlis-wasm-teavm/build.gradle | prepareBrowserHarness | Gradle:prepareBrowserHarness | Snapshot/Toolpins/eigene Adapter; Backendreceipt | B2 | behalten, Backedges entfernen |
| interlis-wasm-teavm/build.gradle | jsContractTest | Gradle:jsContractTest | Snapshot/Toolpins/eigene Adapter; Backendreceipt | B2 | behalten, Backedges entfernen |
| interlis-wasm-teavm/build.gradle | diagramAdapterTest | Gradle:diagramAdapterTest | Snapshot/Toolpins/eigene Adapter; Backendreceipt | B2 | behalten, Backedges entfernen |
| interlis-wasm-teavm/build.gradle | browserContractTest | Gradle:browserContractTest | Snapshot/Toolpins/eigene Adapter; Backendreceipt | B2 | behalten, Backedges entfernen |
| interlis-wasm-teavm/build.gradle | safariContractTest | Gradle:safariContractTest | Snapshot/Toolpins/eigene Adapter; Backendreceipt | B2 | behalten, Backedges entfernen |
| interlis-wasm-teavm/build.gradle | buildBrowserTools | Gradle:buildBrowserTools | Snapshot/Toolpins/eigene Adapter; Backendreceipt | B2 | behalten, Backedges entfernen |
| interlis-wasm-teavm/build.gradle | browserClientTest | Gradle:browserClientTest | Snapshot/Toolpins/eigene Adapter; Backendreceipt | B2 | behalten, Backedges entfernen |
| interlis-wasm-teavm/build.gradle | uiProjectionTest | Gradle:uiProjectionTest | Snapshot/Toolpins/eigene Adapter; Backendreceipt | B2 | behalten, Backedges entfernen |
| interlis-wasm-teavm/build.gradle | repositoryDiagnosticCheck | Gradle:repositoryDiagnosticCheck | Snapshot/Toolpins/eigene Adapter; Backendreceipt | B2 | behalten, Backedges entfernen |
| interlis-wasm-webimage/build.gradle | compileWebImageProbe | Gradle:compileWebImageProbe | Snapshot/Toolpins/eigene Adapter; Backendreceipt | B2 | behalten, Backedges entfernen |
| interlis-wasm-webimage/build.gradle | webImageBuild | Gradle:webImageBuild | Snapshot/Toolpins/eigene Adapter; Backendreceipt | B2 | behalten, Backedges entfernen |
| interlis-wasm-webimage/build.gradle | webImagePrepareBrowser | Gradle:webImagePrepareBrowser | Snapshot/Toolpins/eigene Adapter; Backendreceipt | B2 | behalten, Backedges entfernen |
| interlis-wasm-webimage/build.gradle | webImageBrowserTest | Gradle:webImageBrowserTest | Snapshot/Toolpins/eigene Adapter; Backendreceipt | B2 | behalten, Backedges entfernen |
| interlis-wasm-webimage/build.gradle | webImageProbe | Gradle:webImageProbe | Snapshot/Toolpins/eigene Adapter; Backendreceipt | B2 | behalten, Backedges entfernen |

Die zehn JVM-Projekte (vollständige Liste in SPECIFICATION §2) verlieren in B4
ihre Java-/Application-Gradleskripte: classes/jar/sourcesJar → Jenesis artifacts,
test/check → Jenesis build, installDist → inventargesteuerte JVM-Distribution.
Kompilierte ANTLR-Implementierung bleibt main-only; Testgrammar-Orderingrest
wird in B2 entfernt. Kotlin/Groovy oder neue Frameworkquellen sind nicht vorhanden.
Gemeinsame Java-Konventionen behalten nur nachweislich erforderliche Backendteile.
settings.gradle enthält nach B4 genau die zwei Backendprojekte.

Extension: scripts/stage-core.cjs (stage:core/dev:prepare/F5/CI) → in B3 einmal
Java25 editor-bundle und anschliessender reiner verifizierter Import. Inputs sind
explizite Producer-/Manifestreferenz und vollständiges Bundleinventar, keine
Gradle-output-Suche. src/host/launch.ts behält B3 noch den Classpathstart;
B6 migriert Lock und tatsächlichen Launchplan gemeinsam auf --module-path/--module.
Die Runtime-/VSIXproduzenten bleiben Nodebesitz. Prüfung: Integritätsgegenfälle,
echter Corebundleimport und isolierter Extension-Host, später gebündelte JVM.

check/release-Ordering wird als feste topologische Profilfolge übernommen;
mustRunAfter darf keine zusätzlichen Profile auswählen. Keine stillen Gradle-
Wrapperaliases nach B4. Historische Dokument- und Belegtreffer aus der externen
Einstiegssuche werden bei der finalen Restesuche einzeln von aktiven Verbrauchern
unterschieden. Vollständige P3-/Korpus-/Performanceprofile bleiben ausdrücklich
aufrufbar und fail closed; B0 erklärt keines davon für ausgeführt oder entbehrlich.


## Endgültiger aktiver Besitz nach B7

Alle B0–B7-Pakete sind implementiert und gezielt abgenommen (aktuelle Tabelle in STATUS, Rohbelege in TEST_LEDGER). Frühere Zukunftsformulierungen oben beschreiben die ursprüngliche Disposition. Aktuell: zehn eigene explizite JPMS-Produkte aus Jenesis, ausschliesslich zwei Gradle-Backendadapter, unveränderte fremde Pins; echte modulare Extension-/CLI-Starts, kein Classpathfallback. Der historische Schattenbundleadapter wurde entfernt; editor-check verwendet nur den echten Extensionimport und vorhandenen isolierten Hostharness. Historische Paritätsleser bleiben reine Nachweisadapter, kein zweiter Builder. Runtimeimages sind explizite Capabilityprofile; die Extension bleibt Besitzerin ihrer gepinnten JRE/jlink-Auswahl. Die volle P3-Veröffentlichungsabnahme ist nicht beauftragt; ihre Schutzwirkung und Pflichtprofile bleiben erhalten. Lokale Kandidaten-/Reviewbindung erfolgt extern nach den Commits.
