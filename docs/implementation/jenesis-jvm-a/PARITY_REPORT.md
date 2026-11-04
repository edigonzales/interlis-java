# JVM-Parität — Auftrag A

A4 auf macOS arm64, Temurin25.0.3, Jenesis0.18.1 am unveränderten Pin
`177014053cce311924f6724d4e4a7fe5689ed9b6`. Gradle bleibt Referenz/Gesamtbuild.
Rohbelege: `/Users/stefan/sources/interlis-java-jenesis-evidence/`.

## Tatsächlich verglichener Umfang

`a4-parity-final-adapters.log`, Exit0; vollständiges logisches Inventar unter
`target/jenesis-jvm/reports/parity-input.json`/`parity.json`, externe Kopie in `a4/`.
Der interne `JvmBuildParity` liest die ursprünglichen Gradle-XMLs aus A0 und
archivierten nativen Jenesis-Reports aus A1–A3 mit ihren Hashbindungen. Die
54 realen Gradle-Deskriptoren in `a3/gradle-identities.jsonl` lösen Anzeigeformate
mit ausschliesslichem Parameterdisplay auf. Keine Testgruppe wird ausgeblendet.

Alle **1297 ursprünglichen Invocations** bestehen mit gleicher Klassen-, Methoden-
und Parameteridentität:1218 P,79 V_alt. Jenesis V enthält sieben separat
benannte neue Adaptertests; die zwei zusätzlichen `JvmBuildParityTest`-Fälle
wurden anschliessend separat ausgeführt. Keine Fehler/Skips in diesen Belegen.
Die anfänglichen Statussummen1397/1318 waren Additionsfehler; die unveränderten
Rohberichte und Modulzahlen ergeben1297/1218. Diese Dokumentkorrektur verändert
keine Testmenge. Die272 expliziten frischen Produzenten und4 Consumer aus A3
bleiben getrennte Läufe, keine zusätzlichen einzigartigen Normalfälle.

Die Wiederverwendung der Normalfälle ist begründet: Produkt-Java-/Testquellen,
Toolchain und externe Bytes blieben unverändert. A4 ändert Packaging, interne
Layout-/Paritäts-/Quellprüfer und deren Nachweise. Deren betroffene Fälle,
Source-Bindings und reale CLI/SDK/Editor-Verbraucher wurden gezielt neu geprüft.
Keine pauschale Wiederholung der ursprünglichen1297 Tests.

## Dependencies, Klassen, Ressourcen, Sources

Alle vier effektiven Scopes aller zehn Module sind inventarisiert. Fremde
Runtime-GAVs und SHA256 bleiben gleich. Eigene Gradle-Compile-Classdirs werden
mit den zugehörigen neu erzeugten JARs verglichen; dieser Unterschied ist
explizit typisiert. Eigenes Mainoutput ist kein zusätzlicher Dependencyknoten.
Die ausschliesslich konkrete ANTLR-Runtime-Compileweitergabe wird nur bei
identischer bereits vorhandener Runtime akzeptiert. JUnit-Engine/Launcher-
Compileexposition und Console/Reporting/ApiGuardian bleiben Testtooling.
Keine generelle Freigabe zusätzlicher Dependencies.

Alle Klassen und Ressourcen der neun Produkte sind **bytegleich** zur separaten
Gradle-Referenz. Damit sind auch ihre öffentlichen APIs erhalten. Verification
ändert ausschliesslich die konkret benannten Layout-/Input-/Paritätsadapter;
keine pauschale Ausnahme für dessen JAR. Hinzu kommen interne Prüfklassen.

Die native Jenesis-Main-JAR-Metadatenvariation ist präzise: Created-By=Jenesis,
Implementation-Title/-Version, Sbom-Format=CycloneDX und modulbezogene
Sbom-Location; dazu genau das jeweilige Maven-POM, pom.properties und CycloneDX-
Dokument. Keine generelle META-INF-Ausnahme. Sourcearchive erhalten nur die
konkreten Created-By/Implementation-Attribute. Im Compiler-Sources-JAR liegen
vier unveränderte generierte Javaquellen unter ihrem Packagepfad; Gradle legt sie
im Root ab. Vier `.interp`/`.tokens`-Generatorhilfsdateien werden nicht geliefert.
Alle gepflegten Java-/Grammatik-/Lizenz-/Dokumentquellen bleiben erhalten. Die
bereits im Main enthaltene Unicode-Lizenz ist zusätzlich im Sources-JAR.

Ein echter Befund war die doppelte ANTLR-Sourcebindung in Gradle: der Plugin-
Taskprovider war bereits gebunden; die zusätzliche srcDir-Zeile führte beim
Sources-JAR zu Duplikaten. Die redundante Zeile wurde entfernt. Der source-saubere
BASE-Referenzworktree verwendete für die Artefaktbaseline einen dokumentierten
Init-Workaround gleicher Wirkung (`a4-reference-sources.gradle`); der erste
Fehllauf bleibt `a4-gradle-artifacts.log`. Im Kandidaten sind `sourcesJar` und
`sdkExampleTest` real grün (`a4-gradle-source-fix-correct-task.log`). Keine
Referenzquellen wurden verändert. Jenesis bindet Javaquellen über native Bind-
Extensionfilter; nicht gepflegte Finder-`.DS_Store` werden wie bei Gradle nicht
in Sources-JARs aufgenommen, ohne Benutzerdateien zu löschen.

Öffentliches natives Maven-Staging enthält neun Produktmodule, keine
Verification-/Test-/Buildtool-JARs oder entsprechenden Sourcearchive. Interne
Verification-Reports/SBOMs bleiben als Nachweise verfügbar. POM-Versionen und
Runtimeweitergabe werden aus realen nativen Inventaren geprüft; kein Mavenlauf.

## Reale kleine Verbraucher

`a4-sdk-build-fixed.log`, Exit0: bestehende CLI/LSP-bin/lib-Form aus vollständigen
aktuellen Runtimeinventaren;55 `CompilerCliProcesses`,9 `SdkProcesses`, separater
entpackter SDK-Consumer mit javac/java/Launcher. Keine Gradleprodukte im SDK.
ZIP-Schreiber ist das getrennte, streng gepinnte Build-only-Projekt unter
`build/interlis/archive-tool`: Commons Compress1.28.0 erhält echte Unix0755-
Launcherrechte; codec1.19.0/io2.20.0/lang3 3.18.0 sind dessen isolierte Closure.
Keine dieser Libraries gelangt in Produkte. Launchertexttemplates stammen aus
BASEs erzeugten Gradle-Skripten; ausschliesslich CLASSPATH wird parametrisiert.
POSIX-Automatic-Module-Path und Windows-UTF16-Brücke bleiben erhalten.

`a4-lsp-process.log` und `a4/lsp-process.json`: echter Java-Classpath-Prozess,
initialize/initialized/shutdown/exit, Content-Length-Framing, Exit0. Kein
Starttext auf stdout. Elf teure JVM-Hosteinstiege einschliesslich separatem CLI-
Konformitätseinstieg sind aus echten Main-/Test-JARs auflösbar; keine erneuten
Gesamtkorpus-/Benchmark-/Crossruntimeprozesse. `a4/consumer-main-resolution.log`
und `other-consumer-mains.log` nennen die tatsächlichen CodeSource-JARs.

`a4/parity-counterprobes.json`: reale Prüferprozesse, Kontrolle0; zusätzliche
Console-Runtime, verlorene gepflegte Source (mit korrekt aktualisiertem
Archivhash) und ungebundener Rawreport jeweils Exit1. A3s acht tieferen Raw-/Run-
Bindungsgegenproben bleiben unverändert gültig. `JvmBuildParityTest` weist
insbesondere verlorene/ersetzte Parameterinvocations zurück.

`a4-python-free-pinned.log`: eigener Buildcode wird mitgescannt. Die unveränderte
Engine enthält auch fremde Toolfähigkeiten; sie wird nur nach vollständiger
Dateimengen-/SHA-Prüfung ihres exakten Upstreampins separat inventarisiert.
Der Scanner eigener Quellen bleibt strikt; seine beiden bisherigen Negativtests
sind erneut grün. Kein Interpreter ist Teil der neuen Buildspur.

Der echte Extension-Nachweis E-01–07 steht in
[EDITOR_COMPATIBILITY_REPORT.md](EDITOR_COMPATIBILITY_REPORT.md).
A5:81 vorgesehene Artefakte sind in zwei unabhängigen Erzeugungen bytegleich;
SDK-Mutation führt hart zu Exit1. Details und begrenzte Verificationnachkompilierung
stehen im Testledger. Alle20 aktuellen JARs stimmen mit diesen Erzeugungen überein.
Eigene Dependencies werden zusätzlich gegen tatsächliche Produzentenbytes geprüft;
ein trotz richtigem Hash fremdes eigenes JAR wird abgelehnt. 282 normale Produkt-
Testklassen stimmen ebenfalls bytegenau mit der Gradle-Referenz überein.
Die finale Parität ist als unveränderliche Artefaktkopie samt archiviertem Plan
unter `a4/final/` erneut geprüft. Unabhängiger Review folgt nach Kandidatencommit.
