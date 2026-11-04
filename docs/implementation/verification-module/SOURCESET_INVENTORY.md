# Source-Set-Inventar (V0)

Ausgang: `e389c8eecf50f7672d418df59f37341e47a44038`, main, sauber. Referenz ff30c7e ist Vorfahr; aktuelle Cache-/CLI-/TeaVM-Verträge bleiben erhalten.

## interlis-compiler-cli/src/evidenceTest/java/guru/interlis/cli/evidence/AssertionEvidence.java

Ziel: interlis-verification/src/test; SHA-256 `2b4f940af68b537dfcb80507c8099085b378b44eaf4b82178c9e4b39fa1562a9`.

Imports: `com.google.gson.*`, `java.io.*`, `java.nio.file.*`, `java.time.Instant`, `java.util.*`, `javax.xml.parsers.DocumentBuilderFactory`, `org.w3c.dom.*`, `static guru.interlis.cli.evidence.EvidenceFiles.*`.

## interlis-compiler-cli/src/evidenceTest/java/guru/interlis/cli/evidence/AssertionEvidenceTest.java

Ziel: interlis-verification/src/test; SHA-256 `22cb733ef6190435b1dea5d6f6146e2944805386810a9c3a0006aace281921de`.

Imports: `com.google.gson.*`, `java.nio.file.*`, `java.nio.file.attribute.FileTime`, `java.time.Instant`, `java.util.*`, `org.junit.jupiter.api.*`, `org.junit.jupiter.api.io.TempDir`, `org.junit.jupiter.params.ParameterizedTest`, `org.junit.jupiter.params.provider.*`, `static org.junit.jupiter.api.Assertions.*`, `static guru.interlis.cli.evidence.EvidenceFiles.*`.

- `recordedAssertionsBindToFreshActualCompilerMethodsAndHashedXml()`: `@Test`
- `exactXmlIdentityAndHashesPass()`: `@Test`
- `wrongClassMethodSuiteCountersDetailsAndTimestampFail(String xml)`: `@ParameterizedTest @MethodSource("badXml")`
- `missingStaleOrChangedXmlNeverPasses()`: `@Test`
- `brokenSourceOrRecordedSignatureFails(String key)`: `@ParameterizedTest @ValueSource(strings={"assertionFragments", "methodSha256", "sourceSha256", "source", "jvmSuite", "jvmXmlSha256", "extent", "jvmXml"})`
- `obsoleteOrUnhashedSuiteFails()`: `@Test`
- `codePointOffsetsAndArtifactHashesAreExact()`: `@Test`
## interlis-compiler-cli/src/evidenceTest/java/guru/interlis/cli/evidence/CatalogueEvidence.java

Ziel: interlis-verification/src/test; SHA-256 `1812916bf0eca47c1b07e82ff57dc6b156687980226e18485906ff1e65e5ea9e`.

Imports: `com.google.gson.*`, `java.io.*`, `java.nio.file.*`, `java.util.*`, `java.util.regex.*`, `static guru.interlis.cli.evidence.EvidenceFiles.*`.

## interlis-compiler-cli/src/evidenceTest/java/guru/interlis/cli/evidence/CatalogueEvidenceTest.java

Ziel: interlis-verification/src/test; SHA-256 `3fb2709a1f9cf2b6116f1ffc52c76d5093fa6005491f954bc93156a044fd7eb6`.

Imports: `com.google.gson.*`, `java.util.List`, `org.junit.jupiter.api.Test`, `org.junit.jupiter.params.ParameterizedTest`, `org.junit.jupiter.params.provider.ValueSource`, `static org.junit.jupiter.api.Assertions.*`, `static guru.interlis.cli.evidence.EvidenceFiles.*`.

- `pinnedSourceAndAtomicMappingsMatchCurrentSources()`: `@Test`
- `allGrammarOccurrencesRemainOwnedAndUnaccepted()`: `@Test`
- `allSpecRequirementsAndExplicitLinesStayBound()`: `@Test`
- `allBuiltinSignaturesAndPendingDecisionsStayBound()`: `@Test`
- `predefinedDeclarationsSpecialTypesAndVersionProfilesStayBound()`: `@Test`
- `everyRecordedCandidateStillNamesTheExactTestMethod()`: `@Test`
- `a3JvmAndPortablePairBindingsRemainPresent()`: `@Test`
- `all19DecisionCasesKeepExpectationsQuotesAndExplicitUserStatus()`: `@Test`
- `setProfileAdditionsCannotChangeHistoricalCasesOrInventApproval()`: `@Test`
- `foreignOrAcceptedGrammarOccurrenceFails(String key)`: `@ParameterizedTest @ValueSource(strings={"id", "acceptance", "ownerStep", "missing"})`
- `unknownDuplicateOrChangedSpecContractFails(String key)`: `@ParameterizedTest @ValueSource(strings={"duplicate", "missing", "sourceLineSha256", "status", "requirement"})`
- `changedOrMissingBuiltinSignatureFails(String key)`: `@ParameterizedTest @ValueSource(strings={"result", "arguments", "since", "rule", "ownerStep", "acceptance", "missing"})`
- `lostWeakenedOrChangedPredefinedMappingFails(String key)`: `@ParameterizedTest @ValueSource(strings={"missing", "duplicate", "step", "anchor", "independentAcceptance", "weaker"})`
- `wrongSpecialTypeScopeOrObjectUseFails(String key)`: `@ParameterizedTest @ValueSource(strings={"languages", "ruleReferences", "languageScopeMeaning", "actualSignatureUses", "actualSignatureVersions", "usageState", "pendingUserDecision", "assertionsConditionalOnUserDecision"})`
- `keywordContextAndFeatureChangesFail(String key)`: `@ParameterizedTest @ValueSource(strings={"word", "context", "feature", "duplicate"})`
- `multilineBuiltinSyntaxKeepsVersionRuleAndArgumentPositions()`: `@Test`
- `inventedChangedOrAcceptedTestCandidateFails(String key)`: `@ParameterizedTest @ValueSource(strings={"method", "methodSha256", "suite", "status"})`
- `brokenA3PairFails(String key)`: `@ParameterizedTest @ValueSource(strings={"methodSha256", "markers", "missing"})`
- `manipulatedDecisionPacketFails(String key)`: `@ParameterizedTest @ValueSource(strings={"ownExpectation", "userDecision", "quote", "quoteSha256", "missing"})`
## interlis-compiler-cli/src/evidenceTest/java/guru/interlis/cli/evidence/CompletionLinksTest.java

Ziel: interlis-verification/src/test; SHA-256 `d4a9aa79addb7a3295c3befadfa6ba223b91bcfca309d537054ad8c9421ed795`.

Imports: `com.google.gson.*`, `java.nio.file.*`, `java.time.Instant`, `java.util.*`, `org.junit.jupiter.api.Test`, `org.junit.jupiter.params.ParameterizedTest`, `org.junit.jupiter.params.provider.ValueSource`, `static guru.interlis.cli.evidence.EvidenceFiles.*`, `static org.junit.jupiter.api.Assertions.*`.

- `allExistingIdsHaveConcreteCurrentSourceAndExecutedPublicAssertions()`: `@Test`
- `inventedOrLostLinksCannotAppearComplete(String kind)`: `@ParameterizedTest @ValueSource(strings={"condition","branch","builtin","predefined","method","sourceHash"})`
## interlis-compiler-cli/src/evidenceTest/java/guru/interlis/cli/evidence/EvidenceFiles.java

Ziel: interlis-verification/src/test; SHA-256 `33bfd8b13b691ead459592b4d086801d0e13c88ca26377add6e07404432ec3e1`.

Imports: `com.google.gson.*`, `java.io.*`, `java.nio.charset.StandardCharsets`, `java.nio.file.*`, `java.security.*`, `java.util.*`, `java.util.regex.Pattern`, `java.util.stream.StreamSupport`.

## interlis-compiler-cli/src/evidenceTest/java/guru/interlis/cli/evidence/NormEvidence.java

Ziel: interlis-verification/src/test; SHA-256 `444614f5c2b60aeb12ba5d3a2c25147f0b65a04f2c32b97d7bbf7564f3c0bf85`.

Imports: `com.google.gson.*`, `java.io.*`, `java.nio.charset.StandardCharsets`, `java.nio.file.*`, `java.util.*`, `java.util.zip.GZIPInputStream`, `static guru.interlis.cli.evidence.EvidenceFiles.*`.

## interlis-compiler-cli/src/evidenceTest/java/guru/interlis/cli/evidence/NormEvidenceTest.java

Ziel: interlis-verification/src/test; SHA-256 `00ac1167581184fffed9f642056024974b9c643b6cc70104722dd844ace78d2c`.

Imports: `com.google.gson.*`, `java.util.*`, `org.junit.jupiter.api.Test`, `org.junit.jupiter.params.ParameterizedTest`, `org.junit.jupiter.params.provider.ValueSource`, `static org.junit.jupiter.api.Assertions.*`, `static guru.interlis.cli.evidence.EvidenceFiles.*`.

- `bothOriginalPdfsAllPagesParagraphsConditionsAndExactExcerptsStayBound()`: `@Test`
- `movedWeakenedOrWrongVersionExcerptFails(String key)`: `@ParameterizedTest @ValueSource(strings={"document", "page", "excerptStart", "quote", "quoteSha256", "extractedPageSha256"})`
- `changedParagraphMissingCoverageAndFalseAcceptanceFail(String key)`: `@ParameterizedTest @ValueSource(strings={"quote", "quoteSha256", "pageSha256", "document", "start", "acceptance", "atomization", "missing", "duplicate", "unclassified"})`
- `invalidCrosswalkFails(String key)`: `@ParameterizedTest @ValueSource(strings={"missing", "duplicate", "ownerStep", "atomization", "acceptance"})`
- `incompleteOrAcceptedConditionFails(String key)`: `@ParameterizedTest @ValueSource(strings={"missing", "ownerStep", "case", "duplicateParagraph", "quoteSha256", "acceptance", "kind", "atomization"})`
- `wrongPdfHashVersionAndExtractorAreRejected()`: `@Test`
## interlis-compiler-cli/src/evidenceTest/java/guru/interlis/cli/evidence/RuntimeReportPathsTest.java

Ziel: interlis-verification/src/test; SHA-256 `545f386609b48dabeba06ecef5a550b1478408640f6e0676e54c2ad02dc60e05`.

Imports: `com.google.gson.*`, `org.junit.jupiter.api.Test`, `java.util.List`, `static org.junit.jupiter.api.Assertions.*`.

- `nativeWindowsAndUnixPathsKeepExactlyTheSameCommandContract()`: `@Test`
## interlis-compiler-cli/src/evidenceTest/java/guru/interlis/cli/evidence/RuntimeReports.java

Ziel: interlis-verification/src/test; SHA-256 `8e7e5c98327d94c756c2d0288e3442b11091b85fe905816f5813635e273224d9`.

Imports: `com.google.gson.*`, `java.nio.file.Path`, `java.util.*`, `static guru.interlis.cli.evidence.EvidenceFiles.*`.

## interlis-compiler-cli/src/evidenceTest/java/guru/interlis/cli/evidence/RuntimeReportsTest.java

Ziel: interlis-verification/src/test; SHA-256 `6c9e3acdd5ceaaa621912722a8e1c0ce6c5701ebfdf110c8a585d5229af21d8b`.

Imports: `com.google.gson.*`, `org.junit.jupiter.api.Test`, `org.junit.jupiter.params.ParameterizedTest`, `org.junit.jupiter.params.provider.ValueSource`, `static org.junit.jupiter.api.Assertions.*`, `static guru.interlis.cli.evidence.EvidenceFiles.*`.

- `eightEmptyGreenReportVariantsFail(String key)`: `@ParameterizedTest @ValueSource(strings={"reference","project","teaVmJavaScript","teaVmWasmGC","webImageNode","safari","webImageBrowsers","sdkProcesses"})`
- `completePortableAndBrowserFormatPassesWithExplicitHistoricalProfile()`: `@Test`
- `missingOrWrongBrowserPayloadFails(String key)`: `@ParameterizedTest @ValueSource(strings={"exactDecimal","diagnostics","diagramNodes","diagramEdges","diagramAssociations","analyzeRevision","compiledOutcome","rejectedOutcome","workerRestartOutcome","stabilizationCases"})`
- `partialOrDuplicateBrowserMatrixFails(String value)`: `@ParameterizedTest @ValueSource(strings={"[]","[{\"browser\":\"chromium\"}]","[{\"browser\":\"chromium\"},{\"browser\":\"chromium\"},{\"browser\":\"chromium\"}]"})`
- `completeBrowserAndSafariReportsPassAndFailedPayloadsDoNot()`: `@Test`
- `sdkCommandAndStreamProfileCannotBeChanged()`: `@Test`
- `detailedReferenceSummaryAndProjectReclassificationMustAgree()`: `@Test`
## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/CompilerCliProcesses.java

Ziel: interlis-verification/src/main; SHA-256 `05f96e6d84762df17852ef6311ab5c2fbc60a60d278de023fa3034307e292b51`.

Imports: `java.nio.file.*`, `java.time.Duration`, `java.util.*`, `static guru.interlis.cli.verification.HostChecks.*`.

## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/ConformanceEvidence.java

Ziel: interlis-verification/src/main; SHA-256 `0d03b872bf1569492794383ec4acf62f9247c8e125ec72af857c69ddaf7f0ff7`.

Imports: `com.google.gson.*`, `java.nio.file.*`, `static guru.interlis.cli.verification.HostChecks.*`.

## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/CorpusExpectations.java

Ziel: interlis-verification/src/main; SHA-256 `e2eb4aee8bf9780b279b89eca1d316b9587eae11cf058105cda18545f98f180e`.

Imports: `com.google.gson.*`, `java.nio.file.Path`, `java.util.*`, `static guru.interlis.cli.verification.HostChecks.*`.

## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/EditorPressure.java

Ziel: interlis-verification/src/main; SHA-256 `12b5f2b0fc5512d8d3e80fd2d2ee961b0ca3d190018f1a24f27d1f1a64786037`.

Imports: `com.google.gson.*`, `guru.interlis.bridge.BridgeDispatcher`, `guru.interlis.compiler.*`, `guru.interlis.execution.*`, `guru.interlis.source.*`, `java.lang.management.ManagementFactory`, `java.nio.file.*`, `java.util.*`, `java.util.concurrent.atomic.AtomicInteger`, `static guru.interlis.cli.verification.HostChecks.*`.

## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/FullCorpus.java

Ziel: interlis-verification/src/main; SHA-256 `26290d0160dae82401c1a1b4e21d40f34bba1898a004ba77ce71b09334137caf`.

Imports: `com.google.gson.*`, `java.io.*`, `java.nio.*`, `java.nio.charset.*`, `java.nio.file.*`, `java.util.*`, `java.util.zip.*`, `javax.xml.parsers.DocumentBuilderFactory`, `org.w3c.dom.Element`, `static guru.interlis.cli.verification.HostChecks.*`.

## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/FullCorpusCold.java

Ziel: interlis-verification/src/main; SHA-256 `8d6f18e534d19095cfccdaf63cca72c6082eba7e81ee0b16ef30ec06b89fed76`.

Imports: `guru.interlis.bridge.BridgeDispatcher`, `java.nio.file.Path`, `static guru.interlis.cli.verification.HostChecks.*`.

## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/FullCorpusRun.java

Ziel: interlis-verification/src/main; SHA-256 `6f3a30b2b06af743e0399cd2d949a6e7be8cae24722cbf1f975ed72bf9a6b1f0`.

Imports: `com.google.gson.*`, `guru.interlis.bridge.BridgeDispatcher`, `java.nio.file.*`, `java.util.*`, `static guru.interlis.cli.verification.HostChecks.*`.

## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/GeoCorpus.java

Ziel: interlis-verification/src/main; SHA-256 `92e7ebec47cde6ff1153d79fcd40dbc06fc76ef10ee1ff835190d4a4d95bd90c`.

Imports: `java.net.URI`, `java.nio.file.*`, `java.util.*`, `java.util.zip.ZipFile`, `javax.xml.parsers.DocumentBuilderFactory`, `org.w3c.dom.*`, `com.google.gson.*`, `static guru.interlis.cli.verification.HostChecks.*`.

## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/HostChecks.java

Ziel: interlis-verification/src/main; SHA-256 `856f616b77846fae871b01b91288c9cd26b0d4d55e43a558862de11b1bc7c2c2`.

Imports: `com.google.gson.*`, `java.io.*`, `java.net.URI`, `java.nio.file.*`, `java.security.*`, `java.time.Duration`, `java.util.*`, `java.util.concurrent.TimeUnit`.

## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/JvmBenchmark.java

Ziel: interlis-verification/src/main; SHA-256 `322e74f6bbbec42122c9faf5392d48cf549b59bca09fb9b34731d8d8fd781b10`.

Imports: `java.nio.file.*`, `java.time.Duration`, `java.util.*`, `java.util.regex.Pattern`, `static guru.interlis.cli.verification.HostChecks.*`.

## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/ObservationPressure.java

Ziel: interlis-verification/src/main; SHA-256 `ef04e22d2b60fd2b44501dd69289761119b8ae6f4206f8eddf6775b0d9bde02f`.

Imports: `guru.interlis.compiler.*`, `guru.interlis.execution.*`, `guru.interlis.model.*`, `guru.interlis.source.*`, `java.nio.file.*`, `java.util.*`, `static guru.interlis.cli.verification.HostChecks.*`.

## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/PlatformProcesses.java

Ziel: interlis-verification/src/main; SHA-256 `ce70ffac68fc2269da12c1bac75ac13065bb66e6eb12ff7395cbb0a90c112983`.

Imports: `com.google.gson.*`, `java.nio.file.*`, `java.util.*`, `javax.xml.parsers.DocumentBuilderFactory`, `static guru.interlis.cli.verification.HostChecks.*`.

## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/ProfileFingerprint.java

Ziel: interlis-verification/src/main; SHA-256 `b8e1f3e61bb05d28e5e5e46f31383c8fba4897a2c6beef767cb84f768f41ce56`.

Imports: `java.nio.charset.StandardCharsets`, `java.nio.file.*`, `java.time.Duration`, `java.util.*`, `static guru.interlis.cli.verification.HostChecks.*`.

## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/PythonFreeCheck.java

Ziel: interlis-verification/src/main; SHA-256 `24952fc378255a8614286f499d5920f121eaa38adb69b539fe531496ba2f50f2`.

Imports: `java.nio.file.*`, `java.time.Duration`, `java.util.*`, `java.util.regex.Pattern`, `static guru.interlis.cli.verification.HostChecks.*`.

## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/ReferenceProcesses.java

Ziel: interlis-verification/src/main; SHA-256 `52248b18b1b9560f59d2a0c4993fd5c51bc2de27640f30235611aa699d74019a`.

Imports: `com.google.gson.*`, `java.net.URI`, `java.nio.file.*`, `java.time.Duration`, `java.util.*`, `java.util.regex.Pattern`, `java.util.zip.ZipFile`, `static guru.interlis.cli.verification.HostChecks.*`.

## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/RepositoryReferenceCheck.java

Ziel: interlis-verification/src/main; SHA-256 `5b0f0de8d99b83173ce24d51282a8f4293c44377d93dae34709c541c713520be`.

Imports: `guru.interlis.compiler.*`, `guru.interlis.execution.CancellationToken`, `guru.interlis.repository.*`, `guru.interlis.source.*`, `guru.interlis.workspace.*`, `java.nio.charset.StandardCharsets`, `java.nio.file.*`, `java.time.Duration`, `java.util.*`, `javax.tools.ToolProvider`, `static guru.interlis.cli.verification.HostChecks.*`.

## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/SdkArchiveCheck.java

Ziel: interlis-verification/src/main; SHA-256 `e9b79c08d0ec1d64dc6d74e8bab88be24614228afa240c9295202c5a10ba35b4`.

Imports: `com.google.gson.*`, `java.io.File`, `java.nio.file.*`, `java.time.Duration`, `java.util.*`, `java.util.zip.ZipFile`, `static guru.interlis.cli.verification.HostChecks.*`.

## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/SdkProcesses.java

Ziel: interlis-verification/src/main; SHA-256 `3597d9a3dd0fe80209b407a80ea7d28360484d9bd9559eea6dee1a40f68715cc`.

Imports: `com.google.gson.*`, `java.nio.file.*`, `java.util.*`, `static guru.interlis.cli.verification.HostChecks.*`.

## interlis-compiler-cli/src/verification/java/guru/interlis/cli/verification/SemanticComparison.java

Ziel: interlis-verification/src/main; SHA-256 `7c9a22665c9bbda1ad4e0510b082ddc55c88c47074f643bfd5ae0c4a89142f1e`.

Imports: `com.google.gson.*`, `guru.interlis.bridge.BridgeDispatcher`, `java.net.URLClassLoader`, `java.nio.file.*`, `java.time.Duration`, `java.util.*`, `static guru.interlis.cli.verification.HostChecks.*`.

## interlis-compiler-cli/src/verificationTest/java/guru/interlis/cli/verification/CompilerCliProcessesTest.java

Ziel: interlis-verification/src/test/java/guru/interlis/verification; SHA-256 `e70a1bc228f4d448ba23e3ae554ae2926f15c16cb92ceb7231fc758d6f8c6070`.

Imports: `java.util.List`, `java.util.LinkedHashMap`, `java.util.Map`, `org.junit.jupiter.api.Test`, `static org.junit.jupiter.api.Assertions.*`.

- `wrongExitPollutedStdoutAndCrashFailTheStandardProcessProfile()`: `@Test`
- `usageErrorsMustActuallyFailAndKeepStdoutEmpty()`: `@Test`
- `strictColorCheckerRejectsAnsiLeaksMissingResetsAndFalseExits()`: `@Test`
## interlis-compiler-cli/src/verificationTest/java/guru/interlis/cli/verification/CorpusOuterNameContractTest.java

Ziel: interlis-verification/src/test/java/guru/interlis/verification; SHA-256 `47a6837201c8f8a95bf5773d1c2263bc4d9423aa3d8c49a287440a5aad30201d`.

Imports: `com.google.gson.*`, `guru.interlis.cli.*`, `guru.interlis.compiler.*`, `guru.interlis.diagnostics.*`, `guru.interlis.execution.CancellationToken`, `guru.interlis.model.*`, `guru.interlis.source.*`, `java.nio.charset.StandardCharsets`, `java.io.*`, `java.nio.file.*`, `java.util.*`, `java.util.zip.ZipFile`, `org.junit.jupiter.api.Test`, `static org.junit.jupiter.api.Assertions.*`, `static guru.interlis.cli.verification.HostChecks.*`.

- `pinnedOriginalHasOneExactSourceErrorAndRenamedControlHasActualInheritedMembers(@org.junit.jupiter.api.io.TempDir Path folder)`: `@Test`
- `wrongSuccessExitDiagnosticSourceRangeAndMultiplicityCannotPassTheNegativeBenchmark()`: `@Test`
## interlis-compiler-cli/src/verificationTest/java/guru/interlis/cli/verification/FullCorpusInputContractTest.java

Ziel: interlis-verification/src/test/java/guru/interlis/verification; SHA-256 `6ff292cd0ae8f27d096e8a484cb4def7f95876c280fb43630201ace2e4473940`.

Imports: `com.google.gson.*`, `java.nio.file.*`, `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.io.TempDir`, `static org.junit.jupiter.api.Assertions.*`, `static guru.interlis.cli.verification.HostChecks.*`.

- `completeIndexAndExplicitLegacyDecodingReproducePinnedTexts()`: `@Test`
- `omittedRootCannotTurnIntoAPartialSuccessfulCorpus()`: `@Test`
- `legacyDecoderMismatchAndUnapprovedEncodingsFailClosed()`: `@Test`
- `changedOriginalArchiveIsNeverRepairedOrDownloaded()`: `@Test`
## interlis-compiler-cli/src/verificationTest/java/guru/interlis/cli/verification/HostChecksTest.java

Ziel: interlis-verification/src/test/java/guru/interlis/verification; SHA-256 `9ccf513f22fcbe0f5e462fda4fb5a3e099b0e9290621ac55f93717edd464a817`.

Imports: `com.google.gson.*`, `java.nio.file.*`, `java.time.Duration`, `java.util.*`, `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.io.TempDir`, `static org.junit.jupiter.api.Assertions.*`, `static guru.interlis.cli.verification.HostChecks.*`.

- `wrongSdkVersionOrCliPayloadNeverPasses()`: `@Test`
- `brokenArtifactAndBenchmarkMeasurementFail()`: `@Test`
- `corpusMetadataMustExistBeUnambiguousAndKeepItsVersion()`: `@Test`
- `realSubprocessTimeoutAndNonzeroExitRemainDistinct()`: `@Test`
## interlis-compiler-cli/src/verificationTest/java/guru/interlis/cli/verification/LegacyOidCompatibilityTest.java

Ziel: interlis-compiler/src/test/java/guru/interlis/compiler; SHA-256 `d0de83aa0240fddab33e667dddbb3283ded0923f3cd684bab527c5b3c609e0bc`.

Imports: `guru.interlis.compiler.*`, `guru.interlis.execution.CancellationToken`, `guru.interlis.model.*`, `guru.interlis.source.*`, `java.util.*`, `org.junit.jupiter.api.Test`, `static org.junit.jupiter.api.Assertions.*`.

- `legacyOidAndBasketRulesRemainBoundWithoutContracted()`: `@Test`
- `wrongOidDomainAndIncompatibleOverrideRemainSourceErrors()`: `@Test`
- `unrelatedContractedRulesAreNotWaived()`: `@Test`
## interlis-compiler-cli/src/verificationTest/java/guru/interlis/cli/verification/LegacySourceMetadataTest.java

Ziel: interlis-compiler/src/test/java/guru/interlis/compiler; SHA-256 `79f7cf9857c543ca3e98afea8d98562c58a05d65514517c692c086e8ada1bc68`.

Imports: `guru.interlis.compiler.*`, `guru.interlis.execution.CancellationToken`, `guru.interlis.source.*`, `guru.interlis.model.*`, `guru.interlis.syntax.*`, `java.util.*`, `org.junit.jupiter.api.Test`, `static org.junit.jupiter.api.Assertions.*`.

- `repeatedFunctionParametersAndMultilineCommentsCompileInBothProfiles()`: `@Test`
- `everyOccurrenceAndItsOriginalUtf16RangeIsPublicInStrictAndEditorModes()`: `@Test`
- `reusedEditsRetainBothValuesAndDoNotMutatePublishedMetadata()`: `@Test`
- `scalarConflictsAndUnterminatedOrMalformedContinuationsRemainSourceErrors()`: `@Test`
## interlis-compiler-cli/src/verificationTest/java/guru/interlis/cli/verification/PythonFreeCheckTest.java

Ziel: interlis-verification/src/test/java/guru/interlis/verification; SHA-256 `c08c0348ea1956316c3e1b172766becd5ff1666039b90494941c05a0c15cb70f`.

Imports: `org.junit.jupiter.api.Test`, `static org.junit.jupiter.api.Assertions.*`.

- `sourceManifestAndGradleShellNodeOrJavaCallsFail()`: `@Test`
- `absoluteAndEnvWrappedShellCommandsFail()`: `@Test`
## interlis-compiler-cli/src/verificationTest/java/guru/interlis/cli/verification/SemanticComparisonTest.java

Ziel: interlis-verification/src/test/java/guru/interlis/verification; SHA-256 `eebd6a10855b7f520dfee01dd0fb40cbab41914bfd4592bc73cf8a26a96d5143`.

Imports: `java.nio.file.Path`, `java.util.List`, `org.junit.jupiter.api.Test`, `static org.junit.jupiter.api.Assertions.*`.

- `actualGraphProjectionDetectsLossWrongEqualNamedTargetOrderAndFalseValidity()`: `@Test`
## interlis-compiler-cli/src/verificationTest/java/guru/interlis/compiler/internal/UnicodeWeightsTest.java

Ziel: interlis-compiler/src/test/java/guru/interlis/compiler/internal; SHA-256 `5af02a6c7d797c682952995e1546100b02a11c7b8ce27f940579428061d72244`.

Imports: `java.io.*`, `java.nio.charset.StandardCharsets`, `java.nio.file.*`, `java.security.MessageDigest`, `java.util.*`, `java.util.zip.GZIPInputStream`, `org.junit.jupiter.api.Test`, `static org.junit.jupiter.api.Assertions.*`.

- `frozenTableAndBothOriginalUnicodeSourcesRemainPinned()`: `@Test`
- `everyExplicitNfdNormalizationColumnAgreesWithTheExistingTable()`: `@Test`

Mainprogramme: Package gemeinsam nach guru.interlis.verification; FullCorpusCold-Childstring ebenfalls. HostChecks/Tooltests bleiben package-private gekoppelt. UnicodeWeightsTest braucht compiler/internal.TextLengths, keine öffentliche Freigabe. OID/Metadata nutzen nur Compiler/Modell. CorpusOuterNameContractTest benutzt CLI und HostChecks und bleibt im Verifikationsmodul mit expliziter CLI-Testdependency.

Berichte: verification/test und verification/contractTest; produktseitig contractAssertionsTest. JavaExec-Besitzer verification; Rootdelegation bleibt. Distribution bleibt compiler-cli/build/install/interlis-compiler. Plattformverbraucher: PlatformProcesses, tools/release/evidence.cjs/test-fixtures.cjs, p3-platforms.yml. Fingerprints: ProfileFingerprint und tools/performance/profile.cjs (bestätigte Frontend-Differenz).

Vorläufiges Delta: reine Umzüge ändern die Fallzahl nicht; historische Catalogue-/Norm-Zweige und doppelte ausgewählte Assertionausführung ändern das Profil. Exakte Zählung wird aus V2/V3-Reports und manifestierten Parameterinvocations abgeschlossen. Keine Übernahme einer Verzeichniszahl als Testzahl.
