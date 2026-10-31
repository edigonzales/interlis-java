# Unabhängiger Review CLI1

- **Phase:** CLI1 (Neuer Name und nur noch ein CLI-Vertrag)
- **Phasenbasis-SHA:** `c3d736ac82365524835adef8df40789f3fa3ae60`
- **Auftragsbasis-SHA:** `c3d736ac82365524835adef8df40789f3fa3ae60`
- **Geprüfter Kandidaten-SHA:** `beca84286ba964de7c5ccbf941f4c48c2c3c0055`
- **Tatsächlicher Review-HEAD und Zustand:** `beca84286ba964de7c5ccbf941f4c48c2c3c0055`, Worktree sauber (`git status --short` leer), Baum `dca871721449553d74605e283a7dad62a0dc2c96`.
- **OpenCode / Provider / Modell / API-Host:** OpenCode `2.0.28`; Provider `deepseek`; Modell `deepseek-flash` (offizielle Zuordnung DeepSeek-V4.1-Flash-1017, kanonische ID `deepseek/deepseek-v4.1-flash`); Host `api.deepseek.com`. Belege: `build/review/compiler-cli/preflight/provider.json`, Sitzungsmodell laut Systemkonfiguration.
- **Umgebung:** macOS arm64, Temurin 25.0.3+9, Gradle-Wrapper 9.8.1, Node 24.22.0. Python war über den geerbten `without-python`-Guard nicht erreichbar (PATH-ENOENT bzw. absolute EPERM-Proben); es wurden keine Python-/pip-Aufrufe ausgeführt.

## Prüfstand und wichtige Vorbemerkung

Der übergebene Kandidaten-Worktree ist auf Modulebene POSIX-schreibgeschützt (`dr-xr-xr-x`). Gradle 9.8.1 verweigert die Konfiguration mit
`Configuring project ':interlis-wasm-webimage' without an existing directory ... can't be written to`,
weil `File.canWrite()` für die Projektverzeichnisse falsch ist. Deshalb wurde das vollständige Gate in einer isolierten Scratch-Kopie unter dem Review-Buildpfad ausgeführt:

```sh
git clone --shared /Users/stefan/sources/interlis-java \
  build/review/compiler-cli/CLI1-r1/scratch-candidate
git -C build/review/compiler-cli/CLI1-r1/scratch-candidate \
  checkout --detach beca84286ba964de7c5ccbf941f4c48c2c3c0055
```

Geprüft: `HEAD` = Kandidat, Baum-Hash beider Bäume = `dca871721449553d74605e283a7dad62a0dc2c96`, Scratch-Arbeitsbaum sauber. Die Produktquellen des Kandidaten wurden nicht verändert. Das Gate lief mit `--rerun-tasks` im Scratch-Baum; die echten Reports liegen unter `scratch-candidate/**/build/`.

## Kriterienprüfung

| ID | erfüllt / verletzt / nicht prüfbar | eigener Beleg | Finding |
|---|---|---|---|
| CLI1-01 | erfüllt | `settings.gradle` enthält `interlis-compiler-cli` und keinen `interlis-cli`-Alias; `git ls-tree HEAD` zeigt kein altes Modul; alle fünf Source-Sets (`main`, `test`, `verification`, `verificationTest`, `evidenceTest`) vorhanden; Bibliotheksmodul `interlis-compiler` unverändert; Gate kompiliert alle Module. | – |
| CLI1-02 | **verletzt** | Installation und `distZip` enthalten `bin/interlis-compiler` als `0755`; das SDK-ZIP enthält `cli/bin/interlis-compiler` jedoch als **`0644`**. `zipinfo -l` und direkter Start belegen den Defekt; `SdkArchiveCheck` ruft `setExecutable(true)` auf und maskiert ihn. | F1 |
| CLI1-03 | erfüllt | `Main` enthält nur den Standardweg; die fünf Legacyklassen und `ToolVersionRenderer` sind gelöscht; `DefaultCompilerCli` hat keinen Legacykonstruktor/-zustand; `git grep` findet keine aktiven Legacyklassen; `compilerCliCheck` und Identitätstests bestehen. | – |
| CLI1-04 | erfüllt | Leerer Aufruf, nackte Datei, unbekannter Befehl, `-o0`, `--no-auto`, `--modeldir`, `--bogus` liefern Exit 2 mit `interlis-compiler check <File.ili>`; `--help`/`-h`/`--version` liefern Exit 0; `ExitCodePolicy` bildet 0/1/2/3/4 korrekt ab; Identitäts-/Lifecycle-/AtomicOutput-Tests bestehen. | – |
| CLI1-05 | erfüllt | `compilerCliCheck` meldet `COMPILER_CLI_PROCESSES_PASS 19`; eigene Archivprozesse (über den entpackten Launcher via `sh`, siehe F1) bestehen für `check`, `compile`, `analyze`, `diagram`, `format`, `--json`, `--` und Unicode/CWD; Installation und `distZip` starten direkt. | – |
| CLI1-06 | erfüllt | Delta-Tabelle in `implementation/CLI1.md` klassifiziert die entfernten Legacyoptionen/-prozesse, die migrierten Standardfälle, die erhaltenen Referenzprüfungen und die reinen Pfadverschiebungen; die neuen `CompilerCliIdentityContractTest`/`CompilerCliProcesses` decken Pfad-, Unicode-, Stream-, Import-, Syntax-/Semantik-, IO- und Abbruchfälle ab; 19 reale Prozesse und 94 Node-Checkerfälle bestehen. | – |
| CLI1-07 | erfüllt | `gradle/conformance.gradle` verwendet den neuen Launcher; `ReferenceProcesses`, `NormativeConformance`, `SemanticComparison` sind unverändert (R100); Outcomes 577 PASS/16 XFAIL/0 FAIL/0 XPASS/0 ERROR/21 Normkontrollen; Suite-/Quellenpins nicht geändert. | – |
| CLI1-08 | **verletzt** | Release-/SDK-/Plattformpfade und die Hauptdokumente sind nachgeführt; Pythonfreiheit belegt (`PYTHON_FREE_SOURCE_PASS 1433 inputs`); historische Berichte unverändert. Aber aktive Dokumente nennen weiterhin entfernte Legacy-APIs als aktuellen Bestand: `docs/api-migration-0.2.md:476` (`LegacyCliProfile` als „Neue Host-API“) und `docs/spec/02-architektur-und-modulgrenzen.md:106` (`LegacyDiagnosticRenderer` als Rollenbeispiel). | F2 |
| CLI1-09 | **verletzt** | Das vollständige Gate besteht (Exit 0, 64/64 Tasks), aber die eigene Pflicht-Gegenprobe „frisches SDK-Archiv direkt starten“ besteht nicht: direkter Aufruf des entpackten `cli/bin/interlis-compiler` endet mit Exit 126 (`permission denied`). Der `distZip`-Launcher ist dagegen `0755` und startet. | F1 |

## Eigene Gegenproben

### G1 – Frisches SDK-Archiv, fremdes Verzeichnis mit Leerzeichen/Unicode

**Hypothese:** Das ausgelieferte SDK-ZIP stellt einen direkt startbaren `interlis-compiler` bereit; ein alter Dateiaufruf und eine ungültige Option werden vor Host-IO mit Exit 2 abgelehnt; kein Netz-/Resolverzugriff; kein versteckter Alias.

**Input/Setup:**
- Frisches ZIP `build/releases/interlis-java-sdk-0.2.0-SNAPSHOT.zip` (SHA-256 `1844b69855a44ea468b7f33f91af6a7b43c96723529db3524ce8a7b6848e5bd9`) in `counterprobe/ascii space/sdk` entpackt.
- Arbeitsverzeichnis `counterprobe/Arbeits Ordner 😀` (Leerzeichen, Umlaut, Emoji).
- Netz-Sentinel: lokaler HTTP-Proxy auf `127.0.0.1:<Port>` mit Positivkontrolle `HTTP GET /control`; danach `JAVA_OPTS` und `http(s)_proxy` auf den Sentinel gesetzt.

**Exakte Kommandos (Auszug) und tatsächliche Ergebnisse:**
- `cli/bin/interlis-compiler --help` → **Exit 126**, `permission denied`; ZIP-Eintrag ist `-rw-r--r--`.
- `sh cli/bin/interlis-compiler --help` → Exit 0, neue Identität `interlis-compiler 0.2.0-SNAPSHOT`.
- `sh cli/bin/interlis-compiler version --json` → Exit 0, `{"tool":"interlis-compiler",...}`.
- `sh cli/bin/interlis-compiler check Good.ili` → Exit 0, stdout leer.
- `sh cli/bin/interlis-compiler compile Good.ili` → Exit 0, stdout leer.
- `sh cli/bin/interlis-compiler analyze Good.ili` → Exit 0.
- `sh cli/bin/interlis-compiler diagram Good.ili` → Exit 0, Mermaid-Ausgabe auf stdout.
- `sh cli/bin/interlis-compiler format --check Good.ili` → Exit 0.
- `sh cli/bin/interlis-compiler check Bad.ili` → Exit 1, Diagnose auf stderr, stdout leer.
- `sh cli/bin/interlis-compiler check Syntax.ili` → Exit 1, Syntaxdiagnose auf stderr.
- `sh cli/bin/interlis-compiler check --json Bad.ili` → Exit 1, parsebares JSON auf stdout, keine Banner.
- `sh cli/bin/interlis-compiler check -- "-Minus 😀.ili"` → Exit 0 (Optionsende/Unicode/Leerzeichen).
- Office-Original unverändert (`fbb1bccc...`): Exit 1 mit `ILI-SEM-0010`; der Rohkey ist erwartungsgemäss noch CLI2-Arbeit, keine CLI1-Abweichung.
- `https://invalid.example/model.ili` (alter Dateiaufruf) → Exit 2, `unknown command`, vor IO.
- `check --modeldir x Good.ili`, `check -o0`, `check --no-auto`, `--bogus` → jeweils Exit 2 mit `unsupported option`/`unknown command`, vor IO.
- **Sentinel-Log nach den drei IO-Proben: 0 Bytes** (Positivkontrolle zuvor: `HTTP GET /control`). Kein Hinweis auf Netz-/Resolverzugriff im Bedienfehlerpfad.
- Alias-Prüfung: `cli/bin/interlis-cli`, `.bat`, `ili2c`, `ili2c.bat`, `cli/compat/ili2c.jar` **nicht vorhanden**; `grep` nach alten Launcherpfaden im entpackten SDK ohne Treffer.
- Gegenvergleich `distZip`: Entpackter `interlis-compiler-0.2.0-SNAPSHOT/bin/interlis-compiler` ist `0755` und startet direkt (Exit 0). Der Defekt ist auf `sdkPackage` begrenzt.

**Ergebnis:** Die funktionalen Standardaufrufe bestehen; die verlangte direkte Startbarkeit aus dem frischen SDK-Archiv besteht **nicht**. Der `SdkArchiveCheck` extrahiert mit `Files.copy` und ruft danach `launcher.toFile().setExecutable(true,false)` auf; damit wird genau dieser Lieferdefekt vor der Prüfung beseitigt.

**Zusatzbeobachtung (nicht als Produktfinding gewertet):** Wird das SDK zusätzlich unter einem Pfad mit Emoji entpackt, kann die JVM die CLI-JAR nicht laden (`ClassNotFoundException`); ein triviales `Hello.jar` reproduziert dies unabhängig vom Produkt. Der reguläre Test `CompilerCliProcesses` nutzt dagegen ein Unicode-Arbeitsverzeichnis mit ASCII-Installationspfad und besteht. Die Pflichtprobe nach Plan ist mit Leerzeichen/Unicode im Arbeitsverzeichnis erfüllt.

### G2 – Netz-/Resolverfreiheit der Bedienfehler

Siehe G1: Sentinel mit Positivkontrolle, danach alter Dateiaufruf, `--modeldir` und `--bogus`; Exit 2, Sentinel-Log 0 Bytes. Zusätzlich zeigt der Code, dass `execute` den Befehl/die Optionen vor `load(...)`/`ConfigurationLoader`/Repository auflöst.

### G3 – Alter Aufruf und ungültige Option als echte Prozesse

Siehe G1: Exit 2, keine Compilerphase, kein stdout, konkreter Hinweis auf `interlis-compiler check <Datei.ili>`.

## Ausgeführte Pflichtprüfungen

Exaktes Gate (im identischen Scratch-Baum, `--rerun-tasks`):

```sh
./gradlew --no-daemon check conformanceCheck pythonFreeCheck \
  sdkProcessCheck sdkArchiveCheck compilerCliCheck releaseEvidenceCheckerTest \
  :interlis-compiler-cli:installDist :interlis-compiler-cli:distZip \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
  --rerun-tasks --console=plain
```

- **Exit 0**, `BUILD SUCCESSFUL in 2m 41s`, **64/64 Tasks ausgeführt**. Rohlog: `gate.log`.
- **JUnit:** 224 XML-Suiten, **1196 Tests**, 0 Failures, 0 Errors, 0 Skips. Verteilung: compiler 771, compiler-cli 250, lsp 53, model 16, projection 19, repository 41, wasm-bridge 23, workspace 23. Ausgewertet aus `scratch-candidate/**/build/test-results/**/*.xml`.
- **Release-Evidenz-Checker:** 94 Node-Tests, **94 pass / 0 fail / 0 skipped** (`gate.log`, `releaseEvidenceCheckerTest`).
- **Conformance:** 593 Fälle, **577 PASS / 16 XFAIL / 0 FAIL / 0 ERROR / 0 XPASS**, 21 unveränderte Normkontrollen; externer Referenzlauf separat 577/1/15/0/0 (`build/reports/conformance/conformance-summary.json`, `build/conformance/normative-assessment.json`).
- **SDK:** `SDK_PROCESS_PASS 9 commands`; `SDK_ARCHIVE_CONSUMER_PASS 1844b698...`; Archive SHA-256 `1844b69855a44ea468b7f33f91af6a7b43c96723529db3524ce8a7b6848e5bd9`; Version `interlis-compiler 0.2.0-SNAPSHOT`, API `0.2`.
- **Compiler-CLI:** `COMPILER_CLI_PROCESSES_PASS 19`; Report `build/reports/p3/compiler-cli/processes.json`, `gitCommit` = Kandidat, `sourceFingerprint` = `354ac941...`, `status = passed`.
- **Python:** `PYTHON_FREE_SOURCE_PASS 1433 inputs`; `build/reports/python-free/source-check.json` = `passed`. Keine Python-/pip-Aufrufe durch den Reviewer.
- **Artefakte:** `installDist` enthält `bin/interlis-compiler`/`.bat` und sieben Lib-JARs (sechs `interlis-*` plus `antlr4-runtime`); CLI-JAR `interlis-compiler-cli-0.2.0-SNAPSHOT.jar` und Compiler-JAR `interlis-compiler-0.2.0-SNAPSHOT.jar` sind getrennt; CLI-JAR enthält keine Legacy-/Verifikationsklassen. `distZip` enthält `bin/interlis-compiler` als `0755`.
- **Kandidatenunverändertheit:** `HEAD` und Baum unverändert; `git status --short` im Kandidaten leer. Alle Proben/Reports nur unter `build/review/...`.
- **Plattform/CI:** Der CLI1-Mindestgate verlangt keine Linux-/Windows-Nachweise; diese bleiben CLI3. Kein CI-Lauf wurde für CLI1 als Nachweis behauptet.

**Nicht ausgeführte fällige Prüfungen:** keine für CLI1. Portable-/Browser-/Safari-/WebImage-/Performance-/Plattform-/Reproduzierbarkeitspflichten sind laut Plan erst CLI2/CLI3 fällig und wurden nicht als ausgeführt behauptet.

## Findings

### F1 – SDK-ZIP liefert den Launcher nicht ausführbar; Archivprüfung maskiert das (Muss, blockierend)

- **Betroffene Kriterien:** CLI1-02, CLI1-09 (Auslieferung/Startbarkeit des SDK; Pflicht-Gegenprobe).
- **Reproduktion:**
  ```sh
  unzip -q build/releases/interlis-java-sdk-0.2.0-SNAPSHOT.zip -d /tmp/sdk-check
  ./cli/bin/interlis-compiler --help   # Exit 126: permission denied
  zipinfo -l .../interlis-java-sdk-0.2.0-SNAPSHOT.zip | grep cli/bin
  # -rw-r--r-- cli/bin/interlis-compiler
  ```
  Der `distZip`-Launcher ist dagegen `-rwxr-xr-x` und startet direkt.
- **Ursache:** Der Root-Task `sdkPackage` kopiert `install/interlis-compiler` ohne explizite Dateimodi in ein `Zip` (`build.gradle:196-213`); der Application-`distZip` setzt die Ausführbarkeit, der SDK-`Zip` nicht.
- **Maskierung:** `SdkArchiveCheck.java:45` ruft nach `Files.copy` `launcher.toFile().setExecutable(true,false)` auf. Damit prüft der Gate-Lauf nicht die tatsächlich ausgelieferte Startbarkeit. `tools/release/distribution-files.cjs:14-18` prüft nur `isFile()`, nicht den Modus.
- **Erwartet:** `cli/bin/interlis-compiler` im SDK-ZIP ist `0755`; ein direkter Start aus dem entpackten Archiv funktioniert ohne `chmod`.
- **Erforderliche Korrektur:** `sdkPackage` so konfigurieren, dass die Launcher (und nur diese) mit `0755` gespeichert werden (z. B. `filesMatching('bin/interlis-compiler*') { mode = 0755 }` oder aus dem `distZip`/einer korrekt konfigurierten Distribution kopieren). `SdkArchiveCheck` muss den im ZIP gespeicherten Unix-Modus prüfen bzw. den Launcher ohne `setExecutable` direkt starten; `distribution-files.cjs`/Negativtests sollten einen falschen Modus ablehnen.

### F2 – Aktive Dokumente nennen entfernte Legacy-APIs weiter als aktuellen Bestand (Muss, niedrige Schwere)

- **Betroffenes Kriterium:** CLI1-08 (aktive Dokumentation neuer Produktumfang).
- **Beleg:**
  - `docs/api-migration-0.2.md:476`: „Neue Host-APIs: ... und das explizite `LegacyCliProfile`.“ Die Klasse ist mit dem Kandidaten gelöscht.
  - `docs/spec/02-architektur-und-modulgrenzen.md:106`: `LegacyDiagnosticRenderer` wird in der aktiven Rollentabelle weiter als aktuelle Rolle geführt.
- **Erwartet:** Aktive Migration-/Spezifikationsstellen beschreiben `LegacyCliProfile`/`LegacyDiagnosticRenderer` als entfernt (oder führen nur noch aktuelle Rollen); historische Reports bleiben unverändert.
- **Erforderliche Korrektur:** Die beiden aktiven Stellen präzisieren; keine historischen Berichte umschreiben. `docs/spec/16`/`09` enthalten bereits korrekte „entfällt/entfallen“-Formulierungen und können als Muster dienen.

## Urteil

**FAIL**

- **Begründung:** Die funktionale Legacy-Bereinigung, der neue Modul-/Programmname und das vollständige Gate sind weitgehend korrekt. Die verpflichtende Gegenprobe „frisches SDK-Archiv direkt starten“ scheitert jedoch reproduzierbar mit Exit 126, weil `sdkPackage` den Launcher als `0644` ausliefert und `SdkArchiveCheck` dies mit `setExecutable(true)` maskiert. Zusätzlich sind zwei aktive Dokumentstellen nicht auf den neuen Produktumfang nachgeführt.
- **Offene Muss-Findings:** F1, F2.
- **Fehlende Pflichtnachweise:** keine (das CLI1-Gate wurde vollständig ausgeführt); die Startbarkeit des SDK-Launchers ist kein fehlender, sondern ein reproduzierbar verletzter Nachweis.
- **Nächster Schritt:** F1 und F2 im Produkt beheben, neuen Kandidaten-Commit erzeugen, CLI1 erneut prüfen. Keine Sprach-/Normänderung, keine Änderung der externen Pins.
