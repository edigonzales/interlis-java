# Primärquellen und überprüfte Ausgangslage

Stand dieser Spezifikation: **5. November 2026**. Quellen wurden zur Erstellung gelesen; hier wurden keine neuen Cosmo-Native-Builds, Runtimepatches oder unabhängigen Code-Reviews ausgeführt.

## S1 — Repositoryreferenz

- Repository: https://github.com/edigonzales/interlis-java
- Branch: `codex/compiler-ape-feasibility`
- Verifizierter Head: `d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87`
- Snapshot: https://github.com/edigonzales/interlis-java/tree/d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87
- Arbeitsregeln: https://github.com/edigonzales/interlis-java/blob/d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87/AGENTS.md

Referenz ist ein Abgleichpunkt, keine Anweisung zum Zurücksetzen. Die vorhandenen Arbeitsregeln verlangen ausdrücklich wirkungsbezogene Tests und keine Doppelung grosser Korpus-/Benchmarkläufe durch Codex und OpenCode.

## S2 — bisherige Untersuchung und tatsächlicher Umfang

- https://github.com/edigonzales/interlis-java/blob/d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87/docs/implementation/compiler-ape-feasibility/FEASIBILITY_REPORT.md
- https://github.com/edigonzales/interlis-java/blob/d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87/docs/implementation/compiler-ape-feasibility/TOOLCHAIN_REPORT.md
- https://github.com/edigonzales/interlis-java/blob/d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87/tools/ape/README.md

Der Bericht beschreibt den getrennten Selector-/Uhrbefund, echte lokale Compilererfolge, Mac-Loaderbedarf und fehlende native Linux-/Windowsbelege. Diese sind dokumentierte Projektresultate. Die externen grossen Raw-/Binarybelege muss Codex lokal finden und prüfen; ein Bericht ist kein Ersatz für deren behauptete Ausführung.

## S3 — Toolchain-/Produkt-/Native-Integration

- https://github.com/edigonzales/interlis-java/blob/d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87/build/interlis/ape/toolchain.lock.json
- https://github.com/edigonzales/interlis-java/blob/d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87/build/interlis/ape/CompilerApe.java
- https://github.com/edigonzales/interlis-java/blob/d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87/tools/ape/toolchain.cjs
- https://github.com/edigonzales/interlis-java/blob/d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87/tools/ape/compiler-native.cjs

Fakten: strenge Originalinventarbindung, sieben geordnete Produkt-JARs, ausdrückliche APE-Operationen, aktuelle Poll-Konfiguration, gemeinsame Sliceinputs und echte fixup/apelink-Fusion. Die neuen Kompatibilitätsoperationen/-parameter dieser Spezifikation existieren dort noch nicht.

## S4 — vorhandene Laufharnesses

- https://github.com/edigonzales/interlis-java/blob/d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87/tools/ape/network-probes.cjs
- https://github.com/edigonzales/interlis-java/blob/d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87/tools/ape/fixture-server.cjs
- https://github.com/edigonzales/interlis-java/blob/d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87/tools/ape/record.cjs

Der vorhandene Netzwerkharness enthält die gezielte JVM/native-Casefamilie, isolierten Testtrust und den Mac-Hauptlauf. Nicht als automatisch plattformunabhängigen fertigen Harness darstellen. Die Nachweisadapter behalten Originalstreams und echte Prozesszustände.

## S5 — isolierte Reproducer

- https://github.com/edigonzales/interlis-java/blob/d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87/tools/ape/probes/ClockProbe.java
- https://github.com/edigonzales/interlis-java/blob/d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87/tools/ape/probes/HttpProbe.java
- https://github.com/edigonzales/interlis-java/blob/d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87/tools/ape/http-probe-build.cjs

ClockProbe trennt `System.currentTimeMillis()` von `Instant` ohne Netzwerk. HttpProbe verwendet JDK `HttpClient` mit URI, keine INTERLIS-Klassen. Der zusätzliche kombinierte Runtimeprüfer dieses Folgeauftrags ist ein Entwurf.

## S6 — fehlerhafte Cosmo-Substitution und richtige UTC-Implementierung

- https://github.com/elide-dev/graal/blob/4375bae55a446d540f1739644b3db823a8825c40/substratevm/src/com.oracle.svm.core.posix/src/com/oracle/svm/core/posix/cosmo/CosmoSubstitutions.java
- https://github.com/elide-dev/graal/blob/4375bae55a446d540f1739644b3db823a8825c40/substratevm/src/com.oracle.svm.core.posix/src/com/oracle/svm/core/posix/cosmo/CosmoPlatformTimeUtils.java
- https://github.com/elide-dev/graal/blob/4375bae55a446d540f1739644b3db823a8825c40/substratevm/src/com.oracle.svm.core.posix/src/com/oracle/svm/core/posix/cosmo/CosmoUtils.java

Der öffentlich gelesene Snapshot zeigt die falsche monotone Clockauswahl in `currentTimeMillis`, während die System-UTC-Abfrage Realtime nutzt. Er erklärt den Befund, beweist aber keine Quell-/ABIgleichheit zum heruntergeladenen `25.2.4-dev`-Builder.

## S7 — methodengenaue Deaktivierung

- https://github.com/elide-dev/graal/blob/4375bae55a446d540f1739644b3db823a8825c40/substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/substitute/AnnotationSubstitutionProcessor.java

`handleMethodInAliasClass` prüft konfigurierte deaktivierte Substitutionen anhand des annotierten Klassen-/Methodennamens. Hierauf beruht der bevorzugte Entwurf. Ob das exakte Vendorbinary diese Option und Einbindung in passender Form unterstützt, ist ausdrücklicher C0/C1-Prüfauftrag.

## S8 — nur Cosmo und interne API

- https://github.com/elide-dev/graal/blob/4375bae55a446d540f1739644b3db823a8825c40/substratevm/src/com.oracle.svm.core.posix/src/com/oracle/svm/core/posix/cosmo/CosmoLibCSupplier.java
- https://docs.oracle.com/en/graalvm/jdk/25/sdk/com/oracle/svm/core/annotate/package-summary.html

Der Guard prüft die gewählte Ziel-LibC. Die Substitutionsannotationsschnittstelle ist keine unterstützte allgemeine API; der Patch muss deshalb exakt gepinnt und bei Vendorwechsel neu qualifiziert bzw. entfernt werden.

## S9 — Java-Zeitverträge

- https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/System.html

`currentTimeMillis` ist kalenderbezogene Millisekundenzeit. `nanoTime` dient Differenzen mit beliebigem Ursprung. Der Test darf Wallzeit nicht als garantiert monoton behandeln und `nanoTime` nicht mit einer Unix-Epoche gleichsetzen.

## S10 — optionale Classfile-Transformation

- https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/classfile/package-summary.html

JDK 25 bietet strukturierte Classfile-Lese-/Transformations-APIs. Dies ist der vorgesehene schmale technische Ansatz für die nur bedingt erlaubte Route B, nicht ein Auftrag für eine neue allgemeine Bytecodebibliothek.

## S11 — ursprüngliche Vendorreferenz

- https://github.com/elide-dev/madura/blob/88cd910650f2e0db4968c0ad6c38089a8ef6b78d/scripts/setup-graalvm.sh

Madura ist die Herkunft der gepinnten CosmoGVM-Distribution. Sein funktionierender lokaler javac-Anwendungsfall ist kein automatischer HTTPS-Nachweis für den INTERLIS-Compiler.

## S12 — DeepSeek-Modellzuordnung

- https://api-docs.deepseek.com/quick_start/pricing/
- https://api-docs.deepseek.com/quick_start/agent_integrations/

Am Spezifikationstag nennt die direkte API `deepseek-flash` als Zugang zu DeepSeek-V4.1-Flash-1017. Für jeden tatsächlichen unabhängigen Lauf die aktuelle Zuordnung und reale Provider-ID prüfen; keine garantierte unveränderliche Aliassemantik behaupten.

## S13 — OpenCode 2.x CLI und Rechte

- https://dev.opencode.ai/v2/docs/cli/commands/
- https://opencode.ai/v2/docs/permissions

Die offizielle Dokumentation beschreibt nichtinteraktive Runs, Modellwahl, JSON-Ausgabe und die eigene V2-Policyform. Massgeblich bleiben installierte CLI-Hilfe und tatsächlicher Projekt-/Authkontext. Shellzugriffe sind nicht allein durch eine Edit-Sperre sicher eingegrenzt.

## S14 — OpenCode Modell-/Providerkontext

- https://opencode.ai/v2/docs/models/
- https://opencode.ai/v2/docs/providers

Lokale Kennungen, aktivierte Modelle und Zugang sind projektspezifisch. Ein Alias oder ein anderer lokaler Projektkontext allein beweist keine bestimmte tatsächlich verwendete Modellrevision.

## Grenzen der Spezifikation

Die bevorzugte Substitute wurde für dieses Paket nicht kompiliert; die alternative Builder-JAR-Transformation wurde nicht ausgeführt. Pins des noch zu untersuchenden Classfiles, konkreter Patch-JARs und neuer Compilerbilder sind absichtlich nicht erfunden. Ziel dieser Dokumente ist ein implementierbarer und überprüfbarer Auftrag, keine vorweggenommene Erfolgsmeldung.
