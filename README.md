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

Jenesis 0.18.1 ist der einzige JVM-Builder. Die zehn POMs definieren GAVs,
Scopes und Pins; Maven/Ant werden nicht ausgeführt. Der gepinnte Gradle-Wrapper
9.8.1 baut ausschliesslich die zwei Wasm-Adapter.

## Bauen und prüfen

```sh
java build/InterlisJvmBuild.java build
java build/InterlisJvmBuild.java build --module interlis-compiler --tests '.*VersionScannerTest'
java build/InterlisJvmBuild.java artifacts
java build/InterlisJvmBuild.java contracts
java build/InterlisJvmBuild.java sdk-check
java build/InterlisJvmBuild.java host --profile conformance
java build/InterlisJvmBuild.java host --profile reference
java build/InterlisJvmBuild.java host --profile python-free
```

`build` führt normale Tests aus; `artifacts` erzeugt ausdrücklich nur Produkte.
`contracts` führt frische ausgewählte Produzenten vor den Contractconsumern aus.
Ausgaben liegen unter `target/jenesis-jvm`, Dependencycaches unter `.jenesis`.
Die gewöhnliche JVMspur benötigt weder Gradle noch Node, Browser oder GraalVM.
`sdk-check` erstellt CLI/LSP/SDK und prüft reale Prozesse; frühere generierte
Deliveryverzeichnisse werden in `delivery-history` aufbewahrt.

Consumer benötigen eine explizite Übergabe. `wasm-export` schreibt den absoluten
Manifestpfad und dessen Producerhash nach `target/jenesis-jvm/reports/wasm-export.json`.
Beide Werte müssen gemeinsam aus diesem Ergebnis übernommen werden:

```sh
export INTERLIS_NODE=/absolute/node-24.22.0/bin/node
java build/InterlisJvmBuild.java wasm-export
java build/InterlisJvmBuild.java wasm --profile teavm-smoke --inputs /absolute/consumer-manifest.json --inputs-sha256 PRODUCER_HASH
java build/InterlisJvmBuild.java wasm --profile webimage-smoke --inputs /absolute/consumer-manifest.json --inputs-sha256 PRODUCER_HASH
java build/InterlisJvmBuild.java editor-bundle
java build/InterlisJvmBuild.java host --profile portable
java build/InterlisJvmBuild.java host --profile browser
java build/InterlisJvmBuild.java release-check --profile P3
```

Web Image benötigt zusätzlich `GRAALVM_HOME` und `BINARYEN_HOME`. Die breiten
Hostprofile wählen ihre Vorbereitung ausdrücklich; sie gehören nicht zum
Defaultbuild. `release-check` bleibt bei fehlenden aktuellen Plattform-,
Conformance-, Browser-, Performance- oder Interpreterguardbelegen geschlossen.
Eine lokale B-Abnahme ist keine vollständige P3-Freigabe.

`clean --scope jvm|consumer|wasm` entfernt ausschliesslich die gewählten Outputs.
`build/jenesis`, `build/interlis`, Spezifikation und externe Belege sind Quellen.
Der rohe `java build/jenesis/Make.java` ersetzt die Projekteinstiegsgates nicht.
Compiler-APE und CosmoGVM-Uhrpatch bleiben pausiert.

Aktueller Migrationsstand und Rohbeleggrenzen:
[Jenesis B](docs/implementation/jenesis-migration-b/STATUS.md).
Historischer A-Paritätsnachweis:
[Jenesis A](docs/implementation/jenesis-jvm-a/STATUS.md).

## CLI

```sh
target/jenesis-jvm/delivery/compiler/bin/interlis-compiler version
target/jenesis-jvm/delivery/compiler/bin/interlis-compiler check \
  testdata/models/base24.ili testdata/models/survey24.ili
target/jenesis-jvm/delivery/compiler/bin/interlis-compiler diagram \
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
target/jenesis-jvm/delivery/compiler/bin/interlis-compiler check \
  --diagnostic-language de --root Survey \
  testdata/models/base24.ili testdata/models/survey24.ili
java build/InterlisJvmBuild.java sdk-package
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
./gradlew -PjvmInputs="$JVM_INPUTS" -PjvmInputsSha256="$JVM_INPUTS_SHA256" :interlis-wasm-teavm:prepareBrowserHarness
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

Reguläre CLI-/LSP-Pakete starten über `--module-path lib --module guru.interlis.compiler.cli/guru.interlis.cli.Main` bzw. `guru.interlis.lsp/guru.interlis.lsp.Main`. Der explizite lokale Runtimecheck lautet `java build/InterlisJvmBuild.java runtime-check --application compiler`; er ist kein Bestandteil des Defaultbuilds.
