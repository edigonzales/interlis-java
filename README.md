# interlis-java

Moderner Java-INTERLIS-Stack für INTERLIS 2.3/2.4. Der aktuelle Stand umfasst
den P0-/P1-Vertikalschnitt: Compilerkern, Workspace, IDE-Abfragen, LSP-Adapter,
Diagrammprojektion, CLI, TeaVM-WasmGC/JavaScript und einen GraalVM-Web-Image-
Vergleich.

- Spezifikation: [docs/spec/README.md](docs/spec/README.md)
- Aktiver G2-Auftrag und Lieferfolge: [Plan](docs/g2/EXECUTION_PLAN.md), [Status](docs/g2/STATUS.md)
- Fortschritt und offene Gates: [docs/implementation-status.md](docs/implementation-status.md)
- Ausgeführte P1-Evidenz: [docs/evidence/p1/README.md](docs/evidence/p1/README.md)
- Gepinnte ili2c-Referenzprofilprüfung: [docs/evidence/reference/README.md](docs/evidence/reference/README.md)

## Voraussetzungen

- JDK 25 (Temurin 25.0.3+9 ist verifiziert), keine Preview-Features
- Node.js 24.22.0 für portable Verträge
- Für Browserverträge: lokal installierte Playwright-Browser
- Optional für Web Image: Oracle GraalVM 25.4.4, `wasm-as` aus Binaryen >= 119

Der Gradle-Wrapper ist gepinnt (9.8.1). Maven/Ant sind keine Build-Einstiege.

## Bauen und prüfen

```sh
./gradlew check                # JVM-Tests + SDK-Beispiel gegen gebaute JARs
./gradlew checkPortable        # JVM + TeaVM WasmGC/JS unter Node
./gradlew conformanceCheck     # gepinnte externe Suite und getrennte Projektbewertung
./gradlew :interlis-cli:installDist
./gradlew :interlis-lsp:installDist
./gradlew referenceCheck       # gepinntes ili2c-Referenzprofil
./gradlew pythonFreeCheck      # Quellen-/Buildpfade ohne Python-Abhängigkeit
```

Portable Browserverträge (Chromium/Firefox/WebKit):

```sh
cd interlis-wasm-teavm && npm install --no-audit --no-fund && cd ../..
./gradlew :interlis-wasm-teavm:browserContractTest
```

Der echte Safari-Vertrag ist ein separates Gate. Er setzt aktivierte
Safari-Remote-Automation voraus und schließt eine normale Safari-Sitzung vor dem
Start, damit der WebDriver eine Automationsinstanz starten kann:

```sh
./gradlew :interlis-wasm-teavm:safariContractTest
```

Web-Image-Vergleich (Toolchain separat installiert):

```sh
./gradlew :interlis-wasm-webimage:webImageProbe \
  -PgraalVmHome=$HOME/.sdkman/candidates/java/25.4.4+1-graal \
  -PbinaryenHome=/path/to/binaryen
./gradlew :interlis-wasm-webimage:webImageBrowserTest \
  -PgraalVmHome=$HOME/.sdkman/candidates/java/25.4.4+1-graal \
  -PbinaryenHome=/path/to/binaryen
```

Der Web-Image-Browsertest nutzt dieselben lokal installierten Playwright-Browser
wie der TeaVM-Browsertest; ohne `npm install` im TeaVM-Modul schlägt er sichtbar
fehl, statt still zu überspringen.

## CLI

```sh
interlis-cli/build/install/interlis-cli/bin/interlis-cli version
interlis-cli/build/install/interlis-cli/bin/interlis-cli check \
  testdata/models/base24.ili testdata/models/survey24.ili
interlis-cli/build/install/interlis-cli/bin/interlis-cli diagram \
  --root Survey --format mermaid \
  testdata/models/base24.ili testdata/models/survey24.ili
```

`check`/`compile` liefern Exitcode 0 bei gültiger Closure, 1 bei fachlicher
Ablehnung, 2 bei Bedienfehlern, 3 bei internem Fehler, 4 bei Abbruch.
`--json` liefert strukturierte Diagnostik.

## Browserdemo

```sh
./gradlew :interlis-wasm-teavm:prepareBrowserHarness
jwebserver -p 8080 -d interlis-wasm-teavm/build/web
# http://127.0.0.1:8080/
```

Die Demo lädt das TeaVM-WasmGC-Modul in einem Worker, analysiert den Textarea-
Inhalt, kompiliert und projiziert ein Diagramm. `window.interlisBrowserTest`
stellt den Vertrag für die Automatisierung bereit.

## Umfang und Ehrlichkeit

P1 ist ein belegter vertikaler Teilumfang, kein vollständiger INTERLIS-Compiler.
Nicht implementierte Konstrukte werden mit `ILI-IMPL-0001` und
`INCOMPLETE_SUPPORT` abgelehnt und erzeugen kein `CompiledSchema`. Die
vollständige P2-Semantik und eine breitere historische Legacy-Suite bleiben
offene Anforderungen. Die verbleibenden Javadoc-Warnungen (API-003) sind als
dokumentierte P1-Restlücke akzeptiert; Details stehen im Statusdokument. Es wurden keine
Releases veröffentlicht und keine Produktionsmigrationen ausgeführt.
