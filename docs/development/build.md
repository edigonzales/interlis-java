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

| Aufruf nach `java build/InterlisJvmBuild.java` | Ergebnis |
|---|---|
| `artifacts` | JVM-Produkte ohne Tests |
| `conformance` | Gepinnte externe Codeberg-Suite für INTERLIS 2.3 und 2.4 |
| `compiler-bindist` | Compiler-ZIP, Prüfung des entpackten Launchers und SHA256SUMS |
| `module-check` | Prüfung der Modulgrenzen und des Laufzeitinventars |
| `sdk-package` | Modulare CLI-/LSP-Launcher und SDK-ZIP |
| `sdk-check` | Dieselbe Auslieferung plus ausgeführte CLI-/LSP-/SDK-Prozesse |
| `runtime-check --application compiler` | Nachweis der Laufzeitclosure |
| `gui-run` | Desktop-GUI starten |
| `gui-runtime` | Modulares Java-Laufzeitimage für die Desktop-GUI erzeugen |
| `gui-package` | Native Desktop-App für macOS auf Apple Silicon paketieren |
| `gui-check --profile <profil>` | Gezielte GUI-Probe mit `shell`, `compile`, `diagnostics`, `dependencies`, `repositories`, `lifecycle` oder `packaged` |
| `editor-bundle` | Geprüfte Übergabe für Editorclients |
| `editor-check --extension-dir /absolute/extension --reference-bundle /absolute/bundle` | Prüfung der expliziten Editorübergabe |

Eigene JVM-Produkte haben explizite JPMS-Deskriptoren. Die normalen
CLI-/LSP-Launcher starten mit dem jeweiligen Modul und einer geprüften
Laufzeitclosure. Bibliotheksverbraucher verwenden die tatsächlich
exportierten Packages. Generatorprodukte gehören zum Buildoutput, insbesondere
die erzeugten ANTLR-Klassen. Grammatikquellen, Lizenzen und handgepflegte
Ressourcen bleiben im jeweiligen Modul.

Buildausgaben liegen unter `target/jenesis-jvm`. `.jenesis` enthält den
lokalen Abhängigkeitscache. JUnit-Berichte liegen im Engine-Output unter `executed/output/reports/tests`.
`clean --scope jvm` entfernt Engine-Ausgaben, Berichte, Buildtools,
Distributionen, Conformance-Ausgaben sowie Modul- und Laufzeitprüfungen.
`clean --scope consumer` entfernt Snapshots, Übergaben und Editor-Bundles
sowie ihre Laufverzeichnisse. `clean --scope wasm` entfernt die
Buildverzeichnisse der beiden portablen Backends.

Die derzeitigen Clean-Sichten erfassen GUI-Pakete unter `target/jenesis-jvm/gui`,
CLI-Revisionsressourcen unter `target/jenesis-jvm/build-revisions` und
Renderer-Ausgaben unter `frontend/diagram-ui/{dist,build}` nicht. Diese
Ausgaben gehören zur Bestandsaufnahme für die gezielte Clean-Umstellung.
Root-`build/`, Dokumentation und externe Belege sind Quellen.

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
java build/InterlisJvmBuild.java wasm-export
JVM_INPUTS=$(jq -er .manifest target/jenesis-jvm/reports/wasm-export.json)
JVM_INPUTS_SHA256=$(jq -er .manifestSha256 target/jenesis-jvm/reports/wasm-export.json)
java build/InterlisJvmBuild.java wasm --profile teavm-smoke \
  --inputs "$JVM_INPUTS" --inputs-sha256 "$JVM_INPUTS_SHA256"
```

Die Exportdatei enthält den absoluten Manifestpfad und dessen SHA-256-Prüfsumme. Gradle
verifiziert beides und prüft die im unveränderlichen ConsumerSnapshot
erfassten Artefakte, Closure, Module und Ressourcen. Backend-Codegenerierung
und Laufproben bleiben an denselben Snapshot gebunden. Direkte
Wrapperaufrufe benötigen ebenfalls `-PjvmInputs`, `-PjvmInputsSha256` und das
geprüfte `-PnodeExecutable`. Der normale JVM-Build braucht diese Toolchains
nicht.

- `teavm-smoke` baut JS und WasmGC und führt direkte Node-Tests für beide
  Laufzeiten, den Client und den Diagrammadapter aus.
- `browser-smoke` lädt das vorbereitete Browserpaket in Chromium mit dem
  tatsächlichen Worker. `browser-check` führt dieselben Prüfungen zusätzlich
  in Firefox und Playwright-WebKit aus.
- `webimage-smoke` prüft das experimentelle Web-Image-Backend explizit.
- `wasm-prepare` und `browser-prepare` erzeugen die jeweiligen Artefakte.

Installiere für Browserproben den entsprechenden gepinnten Playwright-Browser,
beispielsweise mit `npm exec -- playwright install chromium` in
`interlis-wasm-teavm`. Ein echter Safari-Lauf erfolgt separat mit
`:interlis-wasm-teavm:safariSmoke` am Gradle-Wrapper und derselben expliziten
Snapshotübergabe.

Native APE und der CosmoGVM-Uhrpfad sind zurückgestellt. Der JVM- und
Portable-Build verwenden die hier beschriebenen Laufzeiten und Übergaben.
