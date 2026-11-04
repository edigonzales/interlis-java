# interlis-java

Moderner Java-INTERLIS-Stack für INTERLIS 2.3/2.4. Der aktuelle Stand umfasst
den Compilerumfang mit Compilerkern, Workspace, IDE-Abfragen, LSP-Adapter,
Diagrammprojektion, CLI, TeaVM-WasmGC/JavaScript und einen GraalVM-Web-Image-
Vergleich.

- Spezifikation: [docs/spec/README.md](docs/spec/README.md)
- CLI-Farbausgabe: [Plan](docs/implementation/compiler-cli-color-output/EXECUTION_PLAN.md), [Status](docs/implementation/compiler-cli-color-output/STATUS.md)
- Compiler-CLI-Folgeauftrag: [Plan](docs/implementation/compiler-cli-modernization/EXECUTION_PLAN.md), [Status](docs/implementation/compiler-cli-modernization/STATUS.md)
- Historischer G2-Auftrag und Lieferfolge: [Plan](docs/g2/EXECUTION_PLAN.md), [Status](docs/g2/STATUS.md)
- Fortschritt und offene Gates: [docs/implementation-status.md](docs/implementation-status.md)
- Ausgeführte P1-Evidenz: [docs/evidence/p1/README.md](docs/evidence/p1/README.md)
- Gepinnte ili2c-Referenzprofilprüfung: [docs/evidence/reference/README.md](docs/evidence/reference/README.md)

## Voraussetzungen

- JDK 25 (Temurin 25.0.3+9 ist verifiziert), keine Preview-Features
- Node.js 24.22.0 für portable Verträge
- Für Browserverträge: lokal installierte Playwright-Browser
- Für das vollständige Produktprofil: Oracle GraalVM 25.0.3 und Binaryen 133 (Web Image)

Der Gradle-Wrapper ist gepinnt (9.8.1). Maven/Ant sind keine Build-Einstiege.

## Bauen und prüfen

```sh
./gradlew check                # JVM-Tests + SDK-Beispiel gegen gebaute JARs
./gradlew checkPortable        # JVM + TeaVM WasmGC/JS unter Node
./gradlew conformanceCheck     # gepinnte externe Suite und getrennte Projektbewertung
./gradlew :interlis-compiler-cli:installDist
./gradlew :interlis-lsp:installDist
./gradlew referenceCheck       # gepinntes ili2c-Referenzprofil
./gradlew pythonFreeCheck      # Quellen-/Buildpfade ohne Python-Abhängigkeit
```

Die parallele Jenesis-JVM-Spur für zehn Module verwendet dieselben Produktquellen,
Java25 und streng gepinnte Dependencies. Gradle bleibt Gesamtbuild, Wasm- und
Editor-Stagingpfad. Maven wird durch die POMs nicht zum Buildausführer.

```sh
java build/InterlisJvmBuild.java build
java build/InterlisJvmBuild.java build --module interlis-compiler --tests '.*VersionScannerTest'
java build/InterlisJvmBuild.java contracts
java build/InterlisJvmBuild.java sdk-check
java build/InterlisJvmBuild.java parity --baseline /absolute/path/to/parity-input.json
```

`build` führt normale Tests ohne Compiler-Contractmethoden aus; eingeschränkte
Filter sind keine Gesamtabnahme. `contracts` erzeugt frische ausgewählte
Produzenten vor den Contractconsumern. Ausgaben liegen unter `target/jenesis-jvm`,
Dependencycaches unter `.jenesis`; keine Ergänzung aus Gradleausgaben oder `~/.m2`.
`sdk-check` erwartet ein noch freies `target/jenesis-jvm/delivery`; frühere
Lieferungen bei Bedarf zuvor separat archivieren. Parität liest gebundene echte
Gradle-/Jenesis-Inventare, ohne selbst eine neue Testsuite zu starten.

[Kommandos, Grenzen und ausgeführte Belege](docs/implementation/jenesis-jvm-a/STATUS.md)
und [Editor-Verbraucherprobe](docs/implementation/jenesis-jvm-a/EDITOR_COMPATIBILITY_REPORT.md).
`editor-check --extension-dir /absolute/extension --reference-bundle /absolute/bundle`
ist ein expliziter Scratch-Verbraucherlauf mit separat vorbereiteten
Developmentressourcen, kein neuer normaler Extensionbuild. Der rohe
`java build/jenesis/Make.java` ist nur der Engineeinstieg; er ersetzt die
Projekt-Input-/Layout-/Contract-/Paritätsgates nicht. Compiler-APE bleibt ein
separater Folgeauftrag; JPMS ist dafür keine Voraussetzung.

Portable Browserverträge (Chromium/Firefox/WebKit):

```sh
(cd interlis-wasm-teavm && npm ci)
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
  -PgraalVmHome=$HOME/.sdkman/candidates/java/25.0.3-graal \
  -PbinaryenHome=/path/to/binaryen
./gradlew :interlis-wasm-webimage:webImageBrowserTest \
  -PgraalVmHome=$HOME/.sdkman/candidates/java/25.0.3-graal \
  -PbinaryenHome=/path/to/binaryen
```

Der Web-Image-Browsertest nutzt dieselben lokal installierten Playwright-Browser
wie der TeaVM-Browsertest; ohne `npm ci` im TeaVM-Modul schlägt er sichtbar
fehl, statt still zu überspringen.

## CLI

```sh
interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler version
interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler check \
  testdata/models/base24.ili testdata/models/survey24.ili
interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler diagram \
  --root Survey --format mermaid \
  testdata/models/base24.ili testdata/models/survey24.ili
```

`check`/`compile` liefern Exitcode 0 bei gültiger Closure, 1 bei fachlicher
Ablehnung, 2 bei Bedienfehlern, 3 bei internem Fehler, 4 bei Abbruch.
`--diagnostic-language de|en` wählt Diagnoseprosa (Default `en`, unabhängig vom Host).
`--json` liefert ein JSON-Ergebnis auf stdout, gewöhnliche Diagnosen gehen auf stderr.
`--color=auto|always|never` (auch `--color MODE`) gilt bei allen fünf Diagnosebefehlen,
Default `auto`. AUTO färbt nur sicher interaktives stderr; `2>errors.log` bleibt
farbfrei, auch bei interaktivem stdout. Nichtleeres `NO_COLOR` oder `TERM=dumb`
deaktiviert AUTO. Explizites ALWAYS übersteuert diese Defaults, NEVER unterdrückt
Farbe. Unter Windows bleibt AUTO konservativ farbfrei. JSON, format-/diagram-
stdout und Dateien erhalten keine zusätzlichen ANSI-Sequenzen.

Textdiagnosen zeigen standardmässig nur Fundstelle, Schweregrad, Code und Meldung.
`--verbose` ergänzt Quellausschnitte, verwandte Fundstellen und Hinweise bei
`check`, `compile`, `analyze`, `diagram` und `format`. JSON bleibt vollständig.
Ausführliche Diagnosen zeigen bis zu drei revisionstreue Quellzeilen mit maximal160
sichtbaren Spalten. Unsichere Unicodebreiten erhalten einen UTF-16-Hinweis statt
eines ungenauen Carets; Steuerzeichen werden druckbar escaped. Beispiel:
`interlis-compiler check --verbose --color=always --diagnostic-language de Modell.ili`.
Mit `--root` prüfen `check`/`compile` nur die gewählten Modelle samt benötigten
Abhängigkeiten; zusätzliche Dateien bleiben verfügbare Eingaben. `analyze` zeigt
weiterhin den vollständigen Analysestand. Lokale Importdateien ausdrücklich
angeben oder ein indexiertes Repository verwenden; kein Verzeichnisscan.

```sh
interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler check \
  --diagnostic-language de --root Survey \
  testdata/models/base24.ili testdata/models/survey24.ili
./gradlew sdkPackage
# Nach dem Entpacken: cli/bin/interlis-compiler (Windows: interlis-compiler.bat).
```

`check`, `compile`, `analyze` und `diagram` unterstützen denselben expliziten
Repository-Optionssatz: `--repository DIR_OR_URI` (mehrfach), `--model-pin`,
`--source-pin`, `--cache`, `--no-cache`, `--offline`, `--read-lock`, `--write-lock`, `--config`,
`--explain-config` und `--warnings allow|reject`. Repositoryoptionen ausser Cacheeinstellungen benötigen
`--repository`; jedes Repository benötigt `ilimodels.xml`. Es gibt keine
Defaultrepositories. `format` benötigt und akzeptiert keine Repositories.
Zum Beispiel (Modellname und Repository entsprechend ersetzen):

```sh
interlis-compiler diagram --repository https://models.interlis.ch/ \
  --root MeinModell --verbose --format mermaid
```

`--root` funktioniert auch ohne lokale Quelldatei. `--config FILE` lädt explizite
UTF-8-Properties, etwa `repository=https://models.interlis.ch/`; CLI-Werte haben
Vorrang. `--verbose` ist ausschliesslich eine CLI-Präsentationsoption.
Ein Lock beschreibt die aufgelösten Quellen: `check`/`compile` schreiben ihn nach
erfolgreicher Kompilierung, `diagram` nach erfolgreicher Projektion und `analyze`
nach abgeschlossener Analyse, auch wenn diese fachliche Diagnosen enthält.

Die Compilerbibliothek bleibt `interlis-compiler`; Modul und CLI-JAR heissen
`interlis-compiler-cli`. Alte Programme, Optionsprofile und Aliase entfallen.

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

Die bestätigten Sprach-/Normprofile stehen in [COMPILER_NORM_DECISIONS](docs/g2/COMPILER_NORM_DECISIONS.md).
Historische P3-/G3-Abnahmen bleiben auf ihren bezeichneten Produktcommits gültig;
der aktuelle Compiler-CLI-Folgeauftrag wird separat im verlinkten Status geführt.
Weitere Stackmodule und Lieferabschnitte folgen dem Spezifikationsplan.
Es wurden keine Releases veröffentlicht und keine Produktionsmigrationen ausgeführt.


Mit `--verbose` erscheinen Modellsuche, Auswahl und Cachezugriffe unmittelbar
in ihrer tatsächlichen Reihenfolge auf stderr. Die Meldungen unterscheiden Download,
lokale Verwendung innerhalb der Cachefrist und Offlinecache. Alle Kopfzeilen
beginnen mit `INFO`, `WARNING`, `ERROR` oder `HINT`, gefolgt von `[Code]`, optionaler
Quellposition und Text. Präfixe bleiben Englisch, Texte folgen DE/EN und Farben
der gemeinsamen Policy. Diagnose-JSON und Compilerentscheidungen bleiben unabhängig.

Repositorydownloads liegen standardmässig unter `~/.interlis-cache`.
Repositorymetadaten werden bis 24 Stunden, Modelldateien bis sieben Tage nach
dem letzten vollständigen Download lokal verwendet. Vor Modellwiederverwendung
wird die lokale MD5 mit der optionalen MD5 aus `ilimodels.xml` verglichen.
Fehlende, beschädigte, abgelaufene oder MD5-abweichende Inhalte werden neu geladen;
ohne MD5 entscheidet das Cachealter. Treffer verlängern die Fristen nicht.
ETags und eine zweite Online-Abschlussrunde entfallen. SHA-256 schützt weiterhin
Cacheintegrität und Pins/Locks. `Accept-Encoding: identity` bleibt fest;
`Vary` sperrt diesen Anwendungscache nicht, `no-store` wird weiterhin respektiert.
`--cache DIR` ändert den Pfad, `--no-cache` deaktiviert persistente Zugriffe.
`--offline` verwendet alte verifizierte Inhalte auch ohne Lockfile, ohne Netz.
Alte v1-Zuordnungen bleiben offline lesbar; online erfolgt die Umstellung auf
v2 beim nächsten Download. Für exakte Abhängigkeiten `--write-lock projekt.lock`
und später `--read-lock projekt.lock --offline` verwenden. Netzfehler aktivieren
keinen stillen Offlinefallback. Lokale Projekttexte werden durch den Cache oder
Lock nicht eingefroren. [Vertrag](docs/spec/08-modellaufloesung-und-repositories.md#17-cache-nach-ili2c-md5-und-fristen-massgeblicher-folgevertrag).

### HTTP-Timeouts und Proxy

Online-Repositories verwenden standardmässig 15 Sekunden Connecttimeout und
40 Sekunden Leseinaktivität. Ein regelmässig fortschreitender Download darf länger
dauern. Ein Gesamtlimit pro HTTP-Austausch ist optional; Redirects und Cache-Retries
beginnen neue Budgets.

```sh
interlis-compiler check --root Basis --repository https://models.example/ --connect-timeout 15 --read-timeout 40 --request-timeout 120
interlis-compiler check --root Basis --repository https://models.example/ --proxy proxy.example --proxyPort 3128
interlis-compiler check --root Basis --repository https://models.example/ --no-proxy
interlis-compiler check --explain-config
```

Dieselben Einstellungen sind in einer expliziten Properties-Datei verfügbar:
connect-timeout, read-timeout, request-timeout (positive ganze Sekunden bzw.
off für das Gesamtlimit), proxy, proxyPort, no-proxy (true/false).
CLI überschreibt Datei, dann Defaults. Ohne Proxywahl gilt die JVM-Auswahl;
ein expliziter Proxy verwendet standardmässig Port80. --no-proxy erzwingt einen
direkten Zugriff. Proxyhost und aktives no-proxy derselben Ebene sind unzulässig.
Offline kontaktiert weder Ziel noch Proxy. Eigene Proxy-Authentisierung ist nicht
implementiert. Java-SDK: FetchPolicy benötigt jetzt offline, maxBytes,
connectTimeout, readTimeout und Optional<Duration> requestTimeout; der bisherige
Konstruktor und timeout() entfallen. Details und Nachweise:
[HTTP-Netzwerkpolicy](docs/implementation/http-network-policy/EXECUTION_PLAN.md).
