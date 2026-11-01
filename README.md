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


Mit `--verbose` zeigen Repositoryzugriffe zusätzlich Informationsmeldungen pro
Quelle: Download, bestätigte Cacheverwendung (HTTP 304) oder Offlinecache.
Modellsuche, Auswahl und Cachezugriffe erscheinen unmittelbar in ihrer
tatsächlichen Reihenfolge auf stderr. Vor der zweiten Quellenprüfung erscheint
eine Abschlussankündigung. Alle Kopfzeilen beginnen mit `INFO`, `WARNING`,
`ERROR` oder `HINT`, gefolgt von `[Code]`, optionaler Quellposition und Text.
Die Präfixe bleiben auch bei deutscher Meldungssprache Englisch; Texte und
Farben folgen der gemeinsamen Sprach-/Farbpolicy. Diagnose-JSON und
Compilerentscheidungen bleiben davon unabhängig.

Repositorydownloads werden standardmässig unter `~/.interlis-cache` gespeichert.
Online prüft jeder Beschaffungslauf die Aktualität. Nur ein in der 304-Antwort
identisch bestätigter starker ETag erlaubt die Wiederverwendung vorhandener
Bytes. HTTP-Abrufe fordern mit `Accept-Encoding: identity` eine unkomprimierte
Darstellung an; `Vary: Accept-Encoding` erlaubt damit sichere Cachetreffer.
Andere Variantenfelder und `no-store` werden nicht wiederverwendet.
Bei schwachen ETags, Last-Modified allein oder fehlenden Validatoren
wird erneut vollständig geladen, um exakte Pins und Locks zu prüfen. `--cache DIR` ändert den Pfad,
`--no-cache` deaktiviert persistente Zugriffe. `--offline` verwendet den letzten
vollständig vorhandenen lokalen Repositorystand auch ohne Lockfile. Für exakt
festgelegte Abhängigkeiten `--write-lock projekt.lock` und später
`--read-lock projekt.lock --offline` verwenden. Netzfehler aktivieren keinen
stillen Offlinefallback. Cache und Lockfile ersetzen keine aktuellen lokalen
Projekttexte. [Verhalten und Beispiele](docs/spec/18-cli-sdk-und-kompatibilitaetsadapter.md#standardcache-hostumgebung-und-beispiele).

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
