# Build und Toolchains

Der Gradle-Wrapper baut alle JVM-Module und die portablen Backends.
Setze `JAVA_HOME` auf ein Java-25-JDK. Unter Windows verwende `gradlew.bat`.

```sh
./gradlew build
./gradlew check
./gradlew :interlis-compiler:test --tests '*SyntaxContractTest'
```

Gradle generiert die ANTLR-Klassen unter `interlis-compiler/build/`. ANTLR
4.13.2 läuft mit separater Generator- und Laufzeitkonfiguration. Das
Compiler-Source-JAR enthält die Grammatikquellen und ihre Lizenz. Die Produkte
starten auf dem Modulpfad. Gradlex patcht Whitebox-Tests in das jeweilige
Produktmodul. Testberichte liegen unter `build/test-results/` und
`build/reports/tests/` des jeweiligen Projekts. `./gradlew clean` entfernt
die Buildausgaben.

## JVM und Auslieferung

| Gradle-Aufgabe | Ausgabe oder Funktion |
|---|---|
| `:interlis-compiler-cli:installDist` | `interlis-compiler-cli/build/install/interlis-compiler/` |
| `compilerZip compilerZipChecksum` | Compiler-ZIP und Prüfsumme unter `build/distributions/compiler/` |
| `compilerAppZip validatorAppZip` | Anwendungen mit Java-Laufzeit unter `build/distributions/apps/` |
| `sdkZip` | `build/distributions/sdk/sdk.zip` mit CLI und SDK-Beispielen |
| `:interlis-lsp:installDist` | Modularer Launcher unter `interlis-lsp/build/install/interlis-lsp/` |
| `:interlis-lsp:distZip` | Plattformunabhängiges LSP-ZIP mit Modulpfad, Wire-Dateien und Lizenzen |
| `packageTest` | Prozessprüfungen für Compiler, SDK, Validator und LSP. Benötigt Node für LSP. |
| `:interlis-compiler-gui:run` | Modularer Desktop-Start |
| `:interlis-compiler-gui:guiRuntime` | Verlinkte Laufzeit unter `interlis-compiler-gui/build/gui/runtime/` |
| `:interlis-compiler-gui:guiApp` | macOS-ARM64-App unter `interlis-compiler-gui/build/gui/macos-arm64/` |

Die [Testanleitung](testing.md) beschreibt die separaten Konformitäts- und
Desktopprüfungen.

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
  :interlis-wasm-teavm:browserClientTest
```

TeaVM und Web Image verwenden die Laufzeit ihrer direkten Gradle-Projektabhängigkeiten.
Der Browserharness übernimmt die Versionsangaben der modularen Compiler-CLI.

- `:interlis-wasm-teavm:buildWasmGC` erzeugt die WasmGC-Dateien.
- `:interlis-wasm-teavm:prepareBrowserHarness` baut das Browserpaket.
- `:interlis-wasm-teavm:browserSmoke` prüft dieses Paket mit Chromium.
  `:interlis-wasm-teavm:browserCheck` prüft zusätzlich Firefox und Playwright-WebKit.
- `:interlis-wasm-teavm:safariSmoke` startet die Probe mit installiertem Safari.
- `:interlis-wasm-webimage:webImageSmoke` prüft das experimentelle Web-Image-Backend.

Installiere für Browserproben den entsprechenden gepinnten Playwright-Browser,
beispielsweise mit `npm exec -- playwright install chromium` in
`interlis-wasm-teavm`. Native APE und der CosmoGVM-Uhrpfad sind zurückgestellt.
