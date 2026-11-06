# Build und Toolchains

Jenesis 0.18.1 erzeugt die elf JVM-Module mit Java 25 ohne Preview. Die POMs
definieren Group, Artifact, Version, Scopes und Pins. Der Upstreampin gehört
zu den [Jenesis-Quellen](../../build/JENESIS-UPSTREAM.json). Der gepinnte
Gradle-Wrapper baut ausschliesslich TeaVM und GraalVM Web Image.

## JVM und Auslieferung

| Aufruf nach `java build/InterlisJvmBuild.java` | Ergebnis |
|---|---|
| `build` | JVM-Produkte und reguläre Tests |
| `build --module interlis-compiler --tests '.*VersionScannerTest'` | Eine explizite Klassen- oder Methodenfilterung im angegebenen Modul |
| `artifacts` | JVM-Produkte ohne Tests |
| `contracts` | Vertragstests gemäss aktuellem Katalog, mit frischen Produzenten und originalen JUnit-Berichten |
| `module-check` | Prüfung der Modulgrenzen und des Laufzeitinventars |
| `sdk-package` | Modulare CLI-/LSP-Launcher und SDK-ZIP |
| `sdk-check` | Dieselbe Auslieferung plus ausgeführte CLI-/LSP-/SDK-Prozesse |
| `runtime-check --application compiler` | Nachweis der Laufzeitclosure |
| `editor-bundle` | Geprüfte Übergabe für Editorclients |
| `editor-check --extension-dir /absolute/extension --reference-bundle /absolute/bundle` | Prüfung der expliziten Editorübergabe |

Eigene JVM-Produkte haben explizite JPMS-Deskriptoren. Die normalen
CLI-/LSP-Launcher starten mit dem jeweiligen Modul und einer geprüften
Laufzeitclosure. Bibliotheksverbraucher verwenden die tatsächlich
exportierten Packages. Generatorprodukte gehören zum Buildoutput, insbesondere
die erzeugten ANTLR-Klassen. Grammatikquellen, Lizenzen und handgepflegte
Ressourcen bleiben im jeweiligen Modul.

Buildausgaben liegen unter `target/jenesis-jvm`. `.jenesis` enthält den
lokalen Abhängigkeitscache. Verifikationsläufe halten ihren Eingabestand und
Ausgabepfad in einer gehashten Layoutdatei und ihren Prozessbelegen fest.
`clean --scope jvm|consumer|wasm` löscht ausschliesslich die ausgewählte
Outputsicht. Root-`build/`, Dokumentation und externe Belege sind Quellen.

## Portable Backends

Verwende Node.js 24.22.0 und setze `INTERLIS_NODE` auf das absolute
Executable. Installiere die gepinnten Abhängigkeiten des Browserharness mit
`npm ci` in `interlis-wasm-teavm`. GraalVM Web Image benötigt zusätzlich
`GRAALVM_HOME` mit SVM-Wasm und `BINARYEN_HOME` mit `bin/wasm-opt`.
Die [Freigabematrix](release.md) definiert die erforderlichen Laufzeiten.

```sh
export INTERLIS_NODE=/absolute/node-24.22.0/bin/node
java build/InterlisJvmBuild.java wasm-export
JVM_INPUTS=$(jq -r .manifest target/jenesis-jvm/reports/wasm-export.json)
JVM_INPUTS_SHA256=$(jq -r .manifestSha256 target/jenesis-jvm/reports/wasm-export.json)
java build/InterlisJvmBuild.java wasm --profile teavm-smoke \
  --inputs "$JVM_INPUTS" --inputs-sha256 "$JVM_INPUTS_SHA256"
```

Das Receipt enthält den absoluten Manifestpfad und den Producerhash. Gradle
verifiziert beides und prüft die im unveränderlichen ConsumerSnapshot
erfassten Artefakte, Closure, Module und Ressourcen. Backend-Codegenerierung
und Laufproben bleiben an denselben Snapshot gebunden. Direkte
Wrapperaufrufe benötigen ebenfalls `-PjvmInputs`, `-PjvmInputsSha256` und das
geprüfte `-PnodeExecutable`. Der normale JVM-Build braucht diese Toolchains
nicht.

Die Backendprofile sind `wasm-prepare` für eine kleine WasmGC-Ladekette,
`teavm-smoke` für JS und WasmGC, `webimage-smoke`, `teavm-contracts`,
`webimage-contracts`, `browser-prepare` und `browser-contracts`.
Eine erfolgreiche Smoke-Quittung bindet die beiden positiven und negativen
Vertragsfälle an den Snapshot und ist auf diesen Prüfbereich beschränkt.

Native APE und der CosmoGVM-Uhrpfad sind zurückgestellt. Der JVM- und
Portable-Build verwenden die hier beschriebenen Laufzeiten und Übergaben.
