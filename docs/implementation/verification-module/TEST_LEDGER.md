# Gezielte Prüfungen

Rohbelegroot `/Users/stefan/sources/interlis-java-verification-evidence/`.
Alle Läufe vor dem Kandidatencommit behalten Herkunft BASE e389c8e, dirty=true
(ab Inventardokumentation); keine Umetikettierung auf einen späteren SHA.
JDK für Gradle: Temurin 25.0.3-tem, Wrapper 9.8.1; Host macOS arm64.
Vollkorpus, Benchmarks, Browser/Web Image, externe Plattformen und Vollrelease
sind bei dieser Source-/Task-/Validatororganisation nicht pauschal fällig.

V0: `JAVA_HOME=…/25.0.3-tem ./gradlew :interlis-compiler-cli:verificationTest
--tests '*HostChecksTest' --tests '*CompilerCliProcessesTest' --tests '*PythonFreeCheckTest'`:
Exit 0, BUILD SUCCESSFUL (10s), Rohlog `v0/baseline.log`.
Bestätigt Prozess-/UTF-8-/Timeout-/Farb-/Scanvalidatoren vor dem Umzug.
Inventar-/Dispositionsabgleich via `inventory.cjs`, `decisions.cjs`; alle
39 Java-Dateien und ihre annotierten Testmethoden erfasst.

V1: compileJava/compileTestJava + verification:test + compiler:test sélection
LegacyOidCompatibilityTest, LegacySourceMetadataTest, internal.UnicodeWeightsTest:
Exit 0 (`v1-tests-fixed.log`, 18 tool cases/7 suites + 9 product cases/3 suites).
Premier essai a révélé la vraie dependency RepositoryReferenceCheck → repository;
ajoutée explicitement, aucun changement de visibilité. Premier log conservé.
`./gradlew sdkProcessCheck sdkArchiveCheck semanticComparison platformCheck --dry-run`:
Exit 0 (`v1-graphs.log`); prouve les graphes uniquement, pas leur exécution.
Main-discovery et vrai child JVM dans VerificationLayoutTest; chemins Unicode,
espaces, Windows et symlink testés. XML originaux copiés sous `v1/`.

V2: `./gradlew :interlis-verification:test compilerContractCheck` nach konkreten
Integrationskorrekturen erfolgreich (`v2-current.log`, Exit 0). Sechs vorhandene
Testquellenpins und zehn Prozess-/Build-/Doku-Bindungen waren durch frühere
API-/CLI-Änderungen bzw. diesen Umzug veraltet. Nur aktuelle Overlaydatei ergänzt;
keine historische Datei geändert. Negative Coveragetests prüfen zuerst die gültige
Kontrolle, damit ein anderer Pinfehler keine Mutation scheinbar bestätigt.
`./gradlew :interlis-verification:test :interlis-compiler:test --tests
'*BuiltinSignatureFoundationContractTest' --tests '*PredefinedBindingContractTest'
--tests '*ObjectFunctionContractTest' --tests '*E1ProfileSafetyContractTest'
--tests '*LexicalProfileContractTest' --tests '*LanguageProfileContractTest'`:
Exit 0 (`v2-replacements.log`), 76 Unitfälle/14 Suites und 32 Produktfälle/6 Suites.
`contractTest --tests '*NoSuchContract'`: erwarteter Exit 1, konkreter Fehler
'No compiler-contract tests discovered' (`v2-empty-selection.log`). Danach
`./gradlew compilerContractCheck`: Exit 0 (`v2-final.log`), 4 Contracts/2 Suites
und 272 ausgewählte Produktinvocations. Unit-/Contractmethoden disjunkt.
Aktuelle Normzitate konsumieren gepinnte PDF-/Extraktionsbytes ausschliesslich
im expliziten Contractprofil; Unit-Gegenproben verwenden synthetische Seiten.

V3: `:interlis-verification:test --tests '*PlatformProcessesTest' --tests
'*ProfileFingerprintTest'` Exit 0 (`v3-java-first.log`); gesamte Unitgruppe
79 Fälle/16 Suites Exit 0 (`v3-java-final.log`), danach Layout-Gegenprobe
wegen engerer Symlinkvorfahrenprüfung Exit 0 (`v3-layout.log`).
Parität am tatsächlichen Inputbestand und gemeinsamem Git-Fixture mit 501
Prüfquellen, Frontend und aktuellen Bindungsinputs; Änderung neuer Quelle
ändert beide Fingerprints identisch. Java berücksichtigt jetzt die bisher
fehlenden Frontendquellen und wie Node den expliziten Deleted-Marker.
`node --test tools/release/evidence.test.cjs tools/release/distribution-files.test.cjs`:
94 Fälle Exit 0, keine Skips (`v3-node-final.log`); positive sowie alte/fehlende/
doppelte Berichtspfade, falscher Kandidat, manipulierte XML-Archivbytes,
Mindestzahlen und Verifikations-JAR-Leak negativ geprüft.
`./gradlew check compilerContractCheck sdkArchiveCheck platformCheck --dry-run`:
Exit 0 (`v3-graphs.log`). `check` selektiert keine Norm-/Contract-/G2-Tasks;
platformCheck selektiert compilerContractCheck. Keine Release-Preflightumgebung
vorgetäuscht, kein echter Plattformvollnachweis behauptet. CI-Upload `*/build/test-results/`
und `*/build/reports/tests/` erfasst die neuen Pfade bereits. Editorbundle kopiert
nur LSP-Libraries; keine umgekehrte Dependency oder TeaVM-Classpathänderung.

Profilrechnung (keine neue Vollplattformausführung): alte Untergrenze
1232/229 minus evidence 145/6, verification 25/9, g2Assertions 15/11
plus Compilerregressionen 9/3, heutige Unitgruppe 79/16, Contracts 4/2,
ausgewählte Assertions 272/128 = 1411 Fälle/352 Suites. Aktuelle Tests/Suites
kommen aus realen gezielten Reports; alte Gruppen aus methodengenauem Inventar
und den 15 eindeutigen Originalassertions in elf Suites. Alle Konsumenten
verwenden testdata/verification/jvm-profile.json; kein Herabsetzen der Grenzen.

V4: `./gradlew :interlis-verification:clean :interlis-verification:test
sdkProcessCheck sdkArchiveCheck :interlis-lsp:installDist compilerContractCheck`:
Exit 0 (`v4-integration.log`, 12s). Verifikationsklassen frisch kompiliert;
79 Unitfälle, 4 Contracts, 272 ausgewählte Assertions ohne Fehler/Skips.
SDK_PROCESS_PASS 9, SDK_ARCHIVE_CONSUMER_PASS mit Archiv-SHA
`d628c81a7a21eaf1dc4b3f234c99ec1c8172693cb9cdd950a1d2114ae47e0a83`.
Produktdistributionen/SDK waren bei identischem Produktinhalt up-to-date;
Prozessprüfer und entpackter separater Consumer wurden tatsächlich neu gestartet.
LSP-InstallDist erfolgreich up-to-date, kein unberechtigter Protokollvolltest.

CLI-/LSP-JAR-Inhalte und SDK-Dateinamen separat geprüft (`v4-isolation.json`);
keine Verifikationsklasse/JAR. Gson im LSP ist eine bestehende echte LSP4J-
Dependency; die erste externe Prüfprobe hatte es zu breit verboten und wurde
auf das tatsächliche Isolationsziel beschränkt. Kein Produktfix war nötig.
Projektbezogene compile/runtime/test-Konfigurationen einschliesslich Wasm
mit externem Gradle-Initskript auf Rückdependency geprüft: Exit 0
(`v4-dependencies-fixed.log`). Erster Root-Konfigurationszugriff scheiterte
an Gradle9-Ownerlocks; Probe auf eigene Projekttasks korrigiert.
Kein Browser-/Wasm-/Vollkorpus-/Benchmark-/Mehrplattform-/P3-Releaseprofil
wurde hier ausgeführt oder aus Fixtures als bestanden abgeleitet.

## Findings-Folgeprüfung am ersten Kandidaten decd97c

Unabhängiger OpenCode2.0.28/direct DeepSeek Flash1017-Review: regulärer Exit0,
Kandidat/Tree und alle72 Originalhashes unverändert; tatsächliches Sessionmodell
DeepSeek/deepseek-flash bestätigt. Urteil FAIL mit R1–R4; Originale extern unter
`reviews/decd97c07257ee197cf8c833db4081faf339b08c/round-01/`.
R1: Nodevalidator/Fixtures auf tatsächliche55 CLI-Fälle und Diagnosekatalogv4
abgeglichen. Fixtures nutzen jetzt dieselbe explizite Node-Profilliste; ein
unabhängiger Quellvertragstest vergleicht sie mit Java REQUIRED/VERSION.
Vor Fix ist der neue Test RED (Exit1, r1-red.log), danach95 Nodefälle/0 Skips
PASS (r1-node-final.log). Produktversionen wurden nicht geändert.
R2: aktive P3-Dokumente korrigiert und mit aktuellem JVM-Profil verknüpft.
R3: alle verificationTest-Zielpfade im Inventar berichtigt.
R4: ungenutzte direkte Projektion-Dependency entfernt. Compile-/Testtasks
bleiben bei identischen Klassen und aufgelöstem Classpath up-to-date
(r1-java-process.log); die ursprünglichen V4-79er-Belege bleiben mit ihrer
Herkunft gültig. SDK-/CLI-Hostprogramme wurden wirklich ausgeführt:55 CLI-
Fälle,9 SDK-Kommandos und entpackter separater Consumer, alle Exit0.

Zusätzlich selbst gefunden: Root-ordering für fullCorpusJvm war noch am alten
CLI-Projekt registriert. Besitzer korrigiert; nur der aktuelle Build-Pin im
Overlay nachgeführt. Tatsächlicher Dry-run zeigt den Hosttask nach allen vier
vorgeschriebenen Root-Tasks (`r1-taskgraph-order.log`, Exit0); kein Korpuslauf.
Direkte Gradle-Introspektionsversuche waren wegen Gradle9-Lifecycle/Ownerlocks
ungeeignet, ein hängender Versuch wurde bewusst mit SIGINT abgebrochen und ist
kein PASS. Der erfolgreiche Nachweis verwendet den tatsächlichen Taskgraph.
Sieben Coverage-Unitfälle nach dem aktuellen Build-Pin bestehen
(`r1-coverage-cli-final.log`).

`node tools/release/producer-evidence.cjs`: tatsächliche55 CLI-Fälle und
vollständiger SDK-Versionsbericht v4 gegen die synthetische übrige Releasehülle
PASS; fehlender aktueller CLI-Fall und veralteter Diagnosekatalog werden
zurückgewiesen (`r1-interop-passed.json`, Exit0). Inputdateien sind gehasht;
Herkunft bleibt decd97c/dirty=true, keine spätere SHA-Umetikettierung.
Ein erster Lauf erkannte korrekt einen nach Quellergänzung veralteten CLI-
Fingerprint. Nach frischem CLI-Lauf wurde die synthetische Versionshülle auf
den vollständigen tatsächlichen SDK-Versionssatz abgestimmt; keine Assertion
gelockert. Rohfehler und finale erfolgreiche Probe bleiben separat erhalten.
Keine Produktsemantik, Erwartungen, Originalbelege, Toolpins oder Testgrenzen
wurden geändert. Unbetroffene breite Prüfungen wurden nicht wiederholt.
