# Build- und Prüffunktionsmatrix

Diese Matrix ist zusammen mit `SPECIFICATION.md` verbindlich. Sie beschreibt den erwarteten **Funktionsumfang**, nicht erfundene bereits vorhandene Jenesis-Tasks. Codex ergänzt in `PARITY_REPORT.md` die tatsächlich verwendeten API-Aufrufe, Selektoren, Pfade und Rohbelege.

## 1. Bestehende Funktion → Auftrag A

| Bisherige Funktion / wichtige Quelle | Jenesis-JVM-Spur in A | Abnahme / Grenze |
|---|---|---|
| `settings.gradle`, elf frühere bzw. jetzt zwölf Gradle-Subprojekte | Root-POM mit genau zehn JVM-Kindern | Zählung betrifft konkrete JVM-Projekte, nicht internen Enginegraphen/Tests/Parent-POM. Gradle behält seine gesamte Projektliste. |
| `gradle/java-conventions.gradle` | POM-Release + passende Prozesskonfiguration | Effektive javac-Argumente und Testproperties vergleichen. |
| `gradle/libs.versions.toml` | DependencyManagement und exakte Jenesis-Pins | Nicht blind Versionstexte übertragen: tatsächliche Scopeauflösung inklusive BOM/Parents vergleichen. |
| `generateGrammarSource` | offizielle ANTLR-Sourcegenerierung | Tool/Runtime 4.13.2, vollständiges Lexer-/Parservokabular, `-visitor -no-listener`, keine Generator-JARs in Runtime. |
| `generateTestGrammarSource` | nur übernehmen, wenn tatsächlich Testgrammatiken vorhanden/benötigt | Keine leere Task als Ersatzprodukt; die alte TeaVM-Taskabhängigkeit bleibt in Gradle unberührt. |
| `processResources` der JVM-Module | normale Ressourcen plus gezielte Zusatzdateien | Schemas, Serviceregistrierungen, Unicode-Lizenz, Testresources bytegenau. |
| `compileJava`, `compileTestJava` | Jenesis-Compiler | Alle vorhandenen Sources, Classpath, keine eigenen JPMS-Deskriptoren. |
| `jar`, `sourcesJar` | Jenesis-JAR-/Sources-Ausgaben | Klassen-/Ressourcen-/Sourcesinventar, Version und Runtimeclosure. |
| normale `test`-Tasks | Engine-JUnit-Lauf mit `-compiler-contract` | Alle echten normalen Testidentitäten; Verifikationsklassen mit gemischten normalen und getaggten Methoden nicht pauschal ausschliessen. |
| `interlis-verification:test` inklusive Rootfixtures | normale Tests + explizite externe Inputbindung | Insbesondere R6-Fixtures: Seed-ZIP, A3-Modelle, Node-Fingerprinthelfer. Reale Cachegegenprobe. |
| `contractAssertionsTest` je ausgewähltem Produktmodul | expliziter Produzentenlauf aus aktuellen Bindungen | Frischer Aufruf, exakte Methodenauswahl/Parameterinvocations, eigener Runbeleg. |
| `prepareNormativeInputs` | vorbereitender Contractadapter | Bestehende PDF-Pins und Cachepolitik, nur explizites Contractprofil. Keine neue PDF-Extraktion oder Pythonlaufzeit. |
| `interlis-verification:contractTest` / `compilerContractCheck` | `java build/InterlisJvmBuild.java contracts` | Nur nach echten Jenesis-Produzenten, strikte Run-/Artefakt-/Quellbindung. |
| `compileSdkExample` / `sdkExampleTest` | javac/java gegen neue JARs | Compiler/Main-JARs und Runtime der neuen Spur, kein Source- oder Gradleclasspath. |
| CLI `installDist` und Unicode-Launcher | gleiche vorhandene Form unter neuem Target | Templates/Dateinamen/Argument-/Exitverträge erhalten. Kein jpackage. |
| LSP `installDist` | gleiche vorhandene Form unter neuem Target | Echter Protokollstart ohne stdout-Verunreinigung; keine neue Runtimebundling-Anforderung. |
| `sdkPackage` | gleiche SDK-ZIP-Form | Dateien, Beispiele, Versionmanifest, Ausführungsrechte, separate entpackte Nutzung. |
| `sdkProcessCheck` / `sdkArchiveCheck` | bestehende Java-Prüfer mit explizitem Layout | Reale Aufrufe gegen neue Artefakte, nicht nur Fixturevalidator. |
| `compilerCliCheck` | bestehender Hostprüfer mit explizitem Layout | Erforderliche aktuelle Fälle aus Quelle ermitteln; keine statische alte Zahl als einzige Wahrheit. |
| `pythonFreeCheck` | bleibt sinnvoll ausführbar | Eigene neue Buildquellen mitprüfen; Upstreamvendor-/Hilfsquellbereich bewusst inventarisieren, nicht Scanner pauschal abschalten. |
| `repositoryReferenceCheck`, `referenceCheck` und Varianten | Klassen bleiben Jenesis-kompiliert und auflösbar | Teure Referenzprozesse nur bei konkretem betroffenen Vertrag; unveränderte Fremdcompiler nicht als Pflicht unnötig neu laden. |
| `geoCorpus`, `fullCorpus` | Hostprogramme bleiben auflösbar | Kleine Input-/Hash-/Classpathproben; keine Pflicht zur gesamten Live-/Korpusabnahme. |
| `fullCorpusJvm`, `JvmBenchmark`, `EditorPressure`, `ObservationPressure` | Klassen und Child-Prozessauflösung prüfen | Keine Pflicht zur erneuten langen Korpus-/Performanceabnahme bei reinem Buildwechsel, solange die notwendige JVM-Parität anderweitig belegt ist. Bei konkreter Abweichung gezielt eskalieren. |
| `conformanceAssessment` im normalen CLI-Testquellensatz | Testklassen korrekt erzeugen, separater Java-Einstieg bleibt adressierbar | Kein neues Source-Set anlegen. Externe Gesamtsuite bleibt bisheriger Gradle-Profilteil in A; eine nötige kleine Konformitätsprobe darf den neuen Classpath nutzen. |
| `semanticComparison` mit JVM/JS/Wasm | JVM-Klasse und Comparator-Unitfälle bleiben | Crossruntime-Profil nicht unter Jenesis übernehmen; kein JS-/Wasm-Nachweis aus Unitfixtures ableiten. |
| `platformCheck`, `testdata/verification/jvm-profile.json` | bisheriges Gradle-Releaseprofil bleibt | Neue Parität führt eigenes logisches Inventar. Alte Mindestzahlen/Reportpfade nicht pauschal auf Jenesis umschreiben. |
| `sdkReproducibilityCheck` | getrennte Jenesis-JVM-Reproduzierbarkeitsprüfung | Zwei neue Erzeugungen, keine Wiederverwendung generierter Artefakte, eigener harter Differenzfehler. |
| `releaseCheckP3` / `releaseEvidenceCheck` und OS-Guard | nicht migrieren | Aktueller Gesamtvertrag bleibt; kein lokaler JVM-PASS ersetzt Plattform-/Browser-/Wasmbelege. |
| `diagramUiBuild`, `diagramUiPackage`, `editorBundle` | Standardpfad erhalten, nicht migrieren | Ein separat ausgewiesener Referenzlauf für Editorverträglichkeit darf diese Aufgaben ausführen; kein Teil des normalen Jenesis-JVM-Builds. |
| Extension `stage:core` und F5/`dev:prepare` | bestehender Verbraucher bleibt | Test in isolierter Extensionkopie; Default ruft weiter Gradle auf. Keine neuen Benutzer-/Releasekonfigurationen. |
| Extensionstart `resolveLaunchPlan` + kleiner Extension-Host-Smoke | explizites neues `editor-check`-Profil | Schattenbundle mit Jenesis-JARs, echter unveränderter Launcher/Inventarvalidator, Diagnose/Navigation/Restart und Hash-Gegenproben. |
| Extension-JRE, `runtime-link.json`, `stage:runtime`, VSIX | bestehende Zuständigkeit und Pins erhalten | Keine neue Runtimeerzeugung oder jpackage-Pipeline im Core. Vorhandene Runtime darf für Probe verwendet werden, ohne die Runtimeoptimierung neu abzunehmen. |
| Compiler-APE/Cosmopolitan | ausdrücklich Folgeauftrag | A liefert nur das normale Compiler-JAR-/Runtimeinventar; keine native Toolchain oder neuen Tasks. JPMS ist kein vorgeschaltetes Pflichtgate. |
| `checkPortable`, `checkBrowser`, TeaVM/Web Image | nicht migrieren | Keine Pflichtläufe und keine Unterwanderung ihrer Voraussetzungen. |
| vorhandener GitHub-P3-Workflow | nicht ersetzen | Neue manuelle JVM-Kommandos dokumentieren; keine unechte neue CI-Freigabe behaupten. |

**Wichtig:** „Nicht migrieren“ bedeutet nicht löschen oder unbrauchbar machen. Die ausführbaren JVM-Hilfsklassen werden mitgebaut; nur ihre teuren beziehungsweise nicht-JVM-Orchestrierungen bleiben ausserhalb des neuen Auftragsprofils.

## 2. Datei-/Modulbesitz

### Produktcode

Alle Main-/Testpackages und die bisherigen API-Grenzen bleiben bestehen. Reine Compilerregressionen werden nicht zurück in `interlis-verification` verschoben. Es entstehen keine zusätzlichen Source-Sets. Kein neues generisches `interlis-build-framework` als Produktmodul.

### Buildcode

`build/jenesis/` gehört unverändert dem gepinnten Upstream. `build/InterlisJvmBuild.java` und `build/interlis/` gehören der kleinen neuen Projekteinbindung. Sie werden nicht in Produktartefakte aufgenommen und haben eigene überprüfte Inputs.

### Verifikation

`interlis-verification` darf kleine Jenesis-/Gradle-Inventarleser und einen layoutneutralen Paritätsprüfer aufnehmen. Diese Erweiterungen haben normale Unit-Tests im selben Modul. Ein echter Produkt-CLI-Test darf nicht bloss durch einen Test des Reportparsers ersetzt werden.

### Extension-Verbraucher

`interlis-vscode` ist read-only Referenz/Testverbraucher, kein weiterer Implementierungsumfang. Ein enger Node-Probenadapter im Core darf unveränderten echten Extensioncode verwenden. Kein dauerhafter Ersatz für `stage-core.cjs`, kein neues Packagingbackend, keine Änderung freigegebener Locks oder Runtimepins. `EDITOR_COMPATIBILITY.md` ist verbindlich.

### Daten und Berichte

Historische Originale bleiben unter ihren bisherigen Pfaden. Neue aktuelle Layout- und Runbeschreibungen entstehen unter `target/jenesis-jvm/`. Neue konfigurierende Inputlisten sind versionierte Quellen; Laufwerte und vollständige Reports sind keine Quellen. Aktuelle Bindungsoverlays dürfen nachvollziehbare Build-/Pfadänderungen enthalten, nicht neue fachliche Erwartungen.

## 3. Logische Testmengen

Definiere in A0 vier getrennte JVM-Testinventare:

1. **P:** normale Produkttests einschliesslich klassischer White-Box-/Compilerregressionen.
2. **V:** normale Verifikations-/Validator-Tests, ohne getaggte Contractmethoden.
3. **C:** explizite `compiler-contract`-Consumer.
4. **S:** die für C ausgewählten realen Produktassertions und ihre Parameterinvocations.

Für dieselbe aktuelle Quellbasis muss gelten:

```text
Jenesis(P) = Gradle(P)
Jenesis(V_alt) = Gradle(V_alt)
Jenesis(C) = Gradle(C)
Jenesis(S) = aktuelle vollständige Bindingauswahl
```

Neue Adaptertests sind `V_neu` und werden separat bilanziert. `P` und `S` können sich absichtlich überschneiden, weil S eine frische besondere Ausführung für einen Contract belegt. Addiere sie nicht zu einer angeblichen Menge einzigartiger Produktfälle.

Die Zahl 1411/352 des bisherigen Plattformprofils ist kein universeller Sollwert für einen normalen Jenesis-JUnit-Lauf. Suiteaufteilung und Zusatzläufe können sich unterscheiden. Parität wird auf **Fallidentitäten, Auswahl und Ergebnissen** festgestellt, nicht durch das Herabsetzen einer alten Zahl oder das Erhöhen einer anderen Zahl mit neuen Hilfstests.

Ein einzelner deklarierter Parameter-Test ist nicht gleichbedeutend mit seinen ausgeführten Invocations. Unvollständige Parameterausführung muss sichtbar werden. Dynamische/Nested-Tests sind, soweit vorhanden, mit eindeutiger Container-/Invocationidentität zu berücksichtigen. Codex darf bei einer Anzeigeformatdifferenz nicht eine ganze Testgruppe aus dem Vergleich nehmen.

Die Editor-Kompatibilitätsfälle werden separat als Verbraucherinventar **E** protokolliert; sie erhöhen nicht die JVM-Testzahlen. E enthält den echten bisherigen Vorbereitungsweg, Jenesis-Serverinventar/Launchplan, Extension-Host-Smoke und Integritätsgegenproben. Ein synthetischer Extensionlock allein ist kein echter Verbrauchernachweis.

## 4. Abhängigkeitsscope und Toolisolation

Pro Modul werden mindestens diese getrennten Mengen verglichen:

```text
mainCompile
mainRuntime
testCompile
testRuntime
antlrTool (nur compiler)
buildTools (nur Projekteinbindung/Engine)
```

Jede Zeile enthält GAV, Klassifier/Typ, SHA-256, Resolverherkunft und den Pfad des tatsächlich verwendeten Artefakts. Eigene Module werden zusätzlich mit Source-/Buildbindung identifiziert. Eine gleiche Dateibezeichnung beweist keine gleichen Bytes. Generatoren, JUnit, Console-Launcher, etwaige ZIP-/Reporttooling-Libraries bleiben aus `mainRuntime` der Produkte heraus.

LSP4J/Gson sind normale LSP-Runtimebestandteile. ANTLR-Runtime ist eine echte Compilerdependency. Ein pauschales Verbot jeder in Tooling ebenfalls benutzten Library wäre falsch.

Für Compile-Sichtbarkeit gilt die eng begrenzte POM-/Gradle-Abbildungsregel aus SPECIFICATION Abschnitt 4.2. Runtimegleichheit bleibt strikt; zusätzlicher Engine-Testlauncher gehört in die Toolingbilanz. Jede zulässige Scopeabbildung wird einzeln begründet, nicht durch ein globales Ausblenden von Dependencies.

Parent-/BOM- und Toolpins bleiben auch bei einem eingeschränkten Testlauf stabil. Ein nachträglicher `pin`-Lauf ist eine Konfigurationsänderung mit entsprechendem Dependency-/Verbraucher-Testumfang, nicht nur ein Dokumentationsupdate.

## 5. Paritätsberichte

`PARITY_REPORT.md` muss für jede Zeile der Matrix und jedes JA-Kriterium eine der folgenden Aussagen besitzen:

- **PROVEN:** tatsächlicher relevanter Nachweis; Pfad/Hash/Runreferenz.
- **REUSED:** ursprünglicher tatsächlicher Nachweis plus überprüfte unveränderte relevante Inputs/Artefakte.
- **NOT_IN_SCOPE:** ausdrückliche Auftragsgrenze, keine implizite Erledigung.
- **BLOCKED:** fälliger Nachweis fehlt; konkrete Ursache.

Dazu eine Tabelle aller erlaubten Artefaktformatdifferenzen: betroffener Pfad/Feld, tatsächliche Differenz, technische Ursache, Auswirkung und Nachweis. Keine generische Ignoreliste für `META-INF/**`, alle Manifeste oder alle Klassenmetadata.

## 6. Koexistenzproben

Folgende Reihenfolge muss in getrennten wegwerfbaren Prüfkopien korrekt funktionieren:

```text
Gradle-JVM-Baseline → Jenesis-JVM-Build → Jenesis-Testlauf
Jenesis-Engine vorhanden → Gradle clean → Engine-/Buildquellen unverändert
Jenesis-Ausgaben bereinigt → Jenesis-Neubau ohne Gradle-Outputs
Unveränderter Jenesis-Test → gültige Wiederverwendung
Nur relevante Fixture verändert → betroffener Jenesis-Test neu
Nur irrelevante Doku verändert → keine Pflicht zur ganzen Produktsuite
Bisheriger stage:core/dev:prepare-Pfad → funktionierendes unverändertes Referenzbundle
Jenesis-LSP-JARs → getrenntes Schattenbundle → echter Extension-Verbraucher
Schatten-JAR fehlt/verändert/zusätzlich → bestehender Extensionvalidator lehnt ab
```

Für die Prüfung „ohne Gradle-Outputs“ dürfen in einer dafür angelegten Kopie gezielt `*/build/classes`, `*/build/libs`, `build/install`-Äquivalente fehlen. Niemals ungezielt im Benutzerworktree löschen. Dependencycaches sind keine Produktoutputs und müssen nicht global gelöscht werden.

Die Editorprobe verändert weder das bestehende `build/editor-bundle` noch den regulären LSP-Installationsbaum mit Jenesis-Dateien. Andernfalls könnten Gradle/F5 scheinbar korrekt arbeiten, tatsächlich aber aus der falschen Buildspur lesen.

Die Root-Clean-Sicherung schützt nicht vor einem manuell eingegebenen `rm -rf build`; die Dokumentation muss die neue Quellenrolle klar nennen. Sie darf diesen normalen Gradle-Befehl aber nicht selbst unsicher lassen.
