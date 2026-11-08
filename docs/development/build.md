# Build und Toolchains

Jenesis 0.18.1 erzeugt die zwölf JVM-Module mit Java 25 ohne Preview. Die POMs
definieren Group, Artifact, Version, Scopes und Pins. Die [Jenesis-Quellen](../../build/jenesis) liegen im Repository.
Der gepinnte
Gradle-Wrapper baut ausschliesslich TeaVM und GraalVM Web Image.

## JVM und Auslieferung

```sh
java build/jenesis/Make.java
java build/jenesis/Make.java +interlis-compiler
java '-Djenesis.test.filter=.*VersionScannerTest#detectsUnsupportedVersion' build/jenesis/Make.java +interlis-compiler
```

Der erste Aufruf baut und prüft alle JVM-Module. `+interlis-compiler`
wählt die Compiler-Closure und die Tests des Compilers. Jenesis verwendet
seinen Daemon und seine kompilierten Buildklassen automatisch wieder.
Die JPMS-Tests verwenden die von Jenesis kompilierten Testklassen und
starten sie als Patch des jeweiligen Produktmoduls. Die Produktdeskriptoren
bleiben dabei unverändert.

| Aufruf | Ergebnis |
|---|---|
| `java -Djenesis.test.skip=true build/jenesis/Make.java stage` | JVM-Produkte und Staging ohne Testausführung |
| `java build/interlis/ConformanceRun.java` | Gepinnte Codeberg-Suite für INTERLIS 2.3 und 2.4 |
| `java build/interlis/ExistingJvmDistribution.java compiler` | Compiler-ZIP, Prüfung des entpackten Launchers und SHA256SUMS |
| `java build/interlis/ExistingJvmDistribution.java sdk` | Modulare CLI-/LSP-Launcher und SDK-ZIP |
| `java build/interlis/ExistingJvmDistribution.java check` | SDK-Auslieferung und installierte CLI-/LSP-/SDK-Prozesse prüfen |
| `java build/interlis/ModulePathContract.java` | Modulgrenzen und Laufzeitinventar prüfen |
| `java build/interlis/CompilerRuntimeCheck.java` | Laufzeitclosure des Compilers prüfen |
| `java build/interlis/GuiRuntime.java run\| runtime\| package` | Desktop starten, Java-Laufzeitimage bauen oder macOS-App auf Apple Silicon paketieren |
| `java build/interlis/GuiRuntime.java check PROFILE` | GUI-Probe `shell`, `compile`, `diagnostics`, `dependencies`, `repositories`, `lifecycle` oder `packaged` |
| `java build/interlis/EditorBundleRun.java` | Geprüfte Übergabe für Editorclients |
| `java build/interlis/EditorCompatibilityRun.java EXTENSION BUNDLE` | Prüfung der expliziten Editorübergabe mit absoluten Verzeichnissen |

Eigene JVM-Produkte haben explizite JPMS-Deskriptoren. Die normalen
CLI-/LSP-Launcher starten mit dem jeweiligen Modul und einer geprüften
Laufzeitclosure. Bibliotheksverbraucher verwenden die tatsächlich
exportierten Packages. Generatorprodukte gehören zum Buildoutput, insbesondere
die erzeugten ANTLR-Klassen. Grammatikquellen, Lizenzen und handgepflegte
Ressourcen bleiben im jeweiligen Modul.

Buildausgaben liegen unter `target/jenesis-jvm`. `.jenesis` enthält den
lokalen Abhängigkeitscache. JUnit-Berichte liegen im Engine-Output unter `executed/output/reports/tests`.
`java build/interlis/BuildOutputs.java jvm` entfernt die JVM-Ausgaben
einschliesslich GUI-Paketen und Revisionsressourcen.
`java build/interlis/BuildOutputs.java editor` entfernt Editor-Bundles und
die zugehörigen Renderer-Ausgaben. `./gradlew clean` entfernt ausschliesslich
die beiden Backend-Buildverzeichnisse. Die Jenesis-Quellen und
Abhängigkeitscaches bleiben erhalten.

## Portable Backends

Verwende Node.js 24.22.0 und setze `INTERLIS_NODE` auf das absolute
Executable. Installiere die gepinnten Abhängigkeiten des Browserpakets mit
`npm ci` in `interlis-wasm-teavm`. GraalVM Web Image benötigt zusätzlich
`GRAALVM_HOME` mit SVM-Wasm und `BINARYEN_HOME` mit `bin/wasm-opt`.
Die [Freigabeprüfung](release.md#plattformen-und-ablauf) beschreibt die CI-Jobs
und die Prüfungen vor einer Browserveröffentlichung.

```sh
export INTERLIS_NODE=/absolute/node-24.22.0/bin/node
export PATH="$(dirname "$INTERLIS_NODE"):$PATH"
npm ci --prefix interlis-wasm-teavm
./gradlew "-PnodeExecutable=$INTERLIS_NODE" \
  :interlis-wasm-teavm:teavmJsTest :interlis-wasm-teavm:teavmWasmTest \
  :interlis-wasm-teavm:browserClientTest :interlis-wasm-teavm:diagramAdapterTest
```

Gradle startet den offiziellen Jenesis-Einstieg bei Bedarf und übernimmt
die in dessen Modul-Inventar ausgewiesenen Runtime-JARs aus dem aktuellen
Checkout. Die Backend-Tasks verfolgen diese JARs als Classpath-Eingaben.
Für die Browserversion liest Gradle zusätzlich das Versionsmanifest aus
der aktuellen CLI. Der JVM-Build benötigt weder Node noch GraalVM Web Image.

- `:interlis-wasm-teavm:buildWasmGC` erzeugt die WasmGC-Dateien.
- `:interlis-wasm-teavm:prepareBrowserHarness` baut das Browserpaket.
- `:interlis-wasm-teavm:browserSmoke` prüft dieses Paket mit Chromium.
  `:interlis-wasm-teavm:browserCheck` prüft zusätzlich Firefox und Playwright-WebKit.
- `:interlis-wasm-teavm:safariSmoke` startet die Probe mit installiertem Safari.
- `:interlis-wasm-webimage:webImageSmoke` prüft das experimentelle Web-Image-Backend.

Installiere für Browserproben den entsprechenden gepinnten Playwright-Browser,
beispielsweise mit `npm exec -- playwright install chromium` in
`interlis-wasm-teavm`. Native APE und der CosmoGVM-Uhrpfad sind zurückgestellt.
