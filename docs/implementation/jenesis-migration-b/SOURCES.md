# Quellen, Ausgangsstand und Grenzen

Gelesen am **5. November 2026**. Verwendet wurden die verbundenen GitHub-Lesefunktionen für die Repositories und offizielle Dokumentation für die Werkzeuge. Produktbuilds und Tests wurden bei der Erstellung dieser Spezifikation nicht ausgeführt. Ein lokaler Git-Klon war in der Analyseumgebung wegen fehlender DNS-Auflösung nicht möglich; die konkrete Quellanalyse stützt sich auf die erfolgreich gelesenen GitHub-Dateien, nicht auf einen behaupteten lokalen Checkout.

## Repositoryquellen

### S01 — Core-Referenz

[Core auf 2a2b0272761caa2b65d559c07909970f4a1290ff](https://github.com/edigonzales/interlis-java/tree/2a2b0272761caa2b65d559c07909970f4a1290ff)

Erfolgreich gelesen: Root-Build, Baum, Buildadapter, Verificationlayout und A-Status. Der A-Status dokumentiert einen lokal abgeschlossenen Auftrag, nennt aber noch keinen abschliessenden externen Review-PASS. Das ist eine Grenze des eingecheckten Nachweises, kein Nachweis eines Produktfehlers.

- [A/STATUS.md](https://github.com/edigonzales/interlis-java/blob/2a2b0272761caa2b65d559c07909970f4a1290ff/docs/implementation/jenesis-jvm-a/STATUS.md)
- [A/CAPABILITY_REPORT.md](https://github.com/edigonzales/interlis-java/blob/2a2b0272761caa2b65d559c07909970f4a1290ff/docs/implementation/jenesis-jvm-a/CAPABILITY_REPORT.md)
- [InterlisJvmBuild.java](https://github.com/edigonzales/interlis-java/blob/2a2b0272761caa2b65d559c07909970f4a1290ff/build/InterlisJvmBuild.java)
- [JenesisBridge.java](https://github.com/edigonzales/interlis-java/blob/2a2b0272761caa2b65d559c07909970f4a1290ff/build/interlis/JenesisBridge.java)
- [ModuleArtifacts.java](https://github.com/edigonzales/interlis-java/blob/2a2b0272761caa2b65d559c07909970f4a1290ff/build/interlis/ModuleArtifacts.java)
- [VerificationLayout.java](https://github.com/edigonzales/interlis-java/blob/2a2b0272761caa2b65d559c07909970f4a1290ff/interlis-verification/src/main/java/guru/interlis/verification/VerificationLayout.java)

### S02 — bisheriger Gesamtbuild und Wasm-Kopplung

- [P3-Workflow](https://github.com/edigonzales/interlis-java/blob/2a2b0272761caa2b65d559c07909970f4a1290ff/.github/workflows/p3-platforms.yml)
- [build.gradle](https://github.com/edigonzales/interlis-java/blob/2a2b0272761caa2b65d559c07909970f4a1290ff/build.gradle)
- [interlis-verification/build.gradle](https://github.com/edigonzales/interlis-java/blob/2a2b0272761caa2b65d559c07909970f4a1290ff/interlis-verification/build.gradle)
- [gradle/conformance.gradle](https://github.com/edigonzales/interlis-java/blob/2a2b0272761caa2b65d559c07909970f4a1290ff/gradle/conformance.gradle)
- [TeaVM-Build](https://github.com/edigonzales/interlis-java/blob/2a2b0272761caa2b65d559c07909970f4a1290ff/interlis-wasm-teavm/build.gradle)
- [Web-Image-Build](https://github.com/edigonzales/interlis-java/blob/2a2b0272761caa2b65d559c07909970f4a1290ff/interlis-wasm-webimage/build.gradle)
- [Editorbundle](https://github.com/edigonzales/interlis-java/blob/2a2b0272761caa2b65d559c07909970f4a1290ff/tools/editor/bundle.cjs)

Belegt sind Gradle-Projektabhängigkeiten der beiden Backends, eine TeaVM-Abhängigkeit von `generateTestGrammarSource`, Root-Backedges zu SDK/Frontend und hardcodierte Gradle-Lieferpfade. Daraus folgt nicht, dass die Testgrammargenerierung fachlich gebraucht wird; B0 muss die tatsächliche Eingabe ermitteln.

### S03 — tatsächlicher Extensionverbraucher

[Extension auf a5b774a4d593ca5424212c8a733fe816106639f7](https://github.com/edigonzales/interlis-vscode/tree/a5b774a4d593ca5424212c8a733fe816106639f7)

- [scripts/stage-core.cjs](https://github.com/edigonzales/interlis-vscode/blob/a5b774a4d593ca5424212c8a733fe816106639f7/scripts/stage-core.cjs): baut aktuell `gradlew editorBundle`, liest `build/editor-bundle` und schreibt den Server-Lock.
- [src/host/launch.ts](https://github.com/edigonzales/interlis-vscode/blob/a5b774a4d593ca5424212c8a733fe816106639f7/src/host/launch.ts): prüft JAR-Inventar, Runtime und Java 25, startet derzeit `-cp ... guru.interlis.lsp.Main`.

B betrifft dieses separate Repository tatsächlich. Keine andere ältere INTERLIS-Extension anhand ihres ähnlichen Namens ändern.

### S04 — gepinnte Engine

[Jenesis auf 177014053cce311924f6724d4e4a7fe5689ed9b6](https://github.com/jenesis/jenesis/tree/177014053cce311924f6724d4e4a7fe5689ed9b6)

Die A-Capability-Proben belegen POM-/Classpath-Build, ANTLR, Reports und Project-API. **Sie belegen noch nicht** die hier vorgeschlagene kombinierte modulare Produkt- und White-Box-Teststrategie. Diese wird in B0 mit dem konkreten Pin geprüft. Aktuelle Webseiten können neuere Funktionen beschreiben.

## Offizielle Werkzeugdokumentation

### S05 — Jenesis

- [Referenz](https://jenesis.build/tool/reference/)
- [Begründung der Testmodulstrategie](https://jenesis.build/why/tool/)
- [Getting started](https://jenesis.build/tool/getting-started/)

Die aktuelle Dokumentation beschreibt eigenständige Testmodule, kein eingebautes `--patch-module`-White-Box-Modell. Deshalb ist der enge Testadapter ein ausdrücklich zu prüfender projektspezifischer Hook, keine behauptete Standardfunktion. Die direkten Kapitelaufrufe für `migrating`, `packaging` und `building-and-running` waren beim Abruf teilweise nicht verfügbar; daraus wurden keine zusätzlichen unbestätigten Detailbehauptungen abgeleitet.

### S06 — Java 25

- [javac, insbesondere Patching Modules](https://docs.oracle.com/en/java/javase/25/docs/specs/man/javac.html)
- [java, Modulpfad und Startoptionen](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html)
- [jlink](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jlink.html)
- [jpackage](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html)
- [Packaging Overview](https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html)

JDK-`--patch-module` ist ein unterstützter Mechanismus, auch für Tests. Er ist nicht mit einem GraalVM-/Vendorpatch gleichzusetzen. Ab JDK 25 bindet jpackage Services nicht mehr automatisch wie zuvor; erforderliche Services müssen bewusst berücksichtigt und getestet werden.

### S07 — unabhängiger Reviewer

- [OpenCode Changelog](https://opencode.ai/changelog)
- [OpenCode 2 CLI Commands](https://opencode.ai/v2/docs/cli/commands)
- [DeepSeek Modelle](https://api-docs.deepseek.com/quick_start/pricing/)
- [DeepSeek Changelog](https://api-docs.deepseek.com/updates/)

Die gelesenen DeepSeek-Quellen ordnen `deepseek-flash` der Variante DeepSeek-V4.1-Flash-1017 zu. Vor dem realen Review erneut verifizieren; ein Alias ist kein unveränderlicher technischer Revisionspin. Die installierte OpenCode-Hilfe ist für die tatsächlichen CLI-Flags massgeblich.

## Herkunft eigener Vorgaben

Die Klassennamen für neue Adapter, Schemas, Profile, Phasen und Testgates in diesem Paket sind **Implementierungsvorgaben**. Sie sind weder Behauptungen über bereits vorhandenen Code noch abgeschlossene Fähigkeitsnachweise. Wo eine bestehende Klasse genannt wird, muss Codex deren aktuellen Stand lesen und möglichst gezielt erweitern statt parallel neu implementieren.
