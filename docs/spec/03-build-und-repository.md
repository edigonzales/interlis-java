# Build und Repository

Der beauftragte G2-Abschluss prüft Quellen, Normbelege und frisch ausgeführte
JUnit-Assertionbindungen über `g2EvidenceTest` als Teil von `check`. Die kleine
Java-Testhilfe verwendet die gepinnten Original-PDFs und unverändert erhaltene
`pypdf`-Textkoordinaten; sie schreibt keine Baselines oder Erwartungen.
Revieweingaben sind saubere Checkouts des vollständigen Kandidaten-SHA.
Rohberichte liegen unter `build/reports/g2/` und den JUnit-Ausgabeverzeichnissen.
Das spätere G2-Gate bleibt vom P3-`releaseCheck` getrennt.

## 1. Entscheidung: Gradle mit Groovy-DSL

Das Projekt verwendet **Gradle als einziges orchestrierendes Build-Tool**. Alle Buildskripte sind Groovy (`.gradle`), Java-Quellen verwenden Java 25 ohne Preview. Der Gradle-Wrapper gehört ins Repository. Maven wird nicht als zusätzliches Buildsystem eingeführt. Das historische Maven-POM des Syntaxspikes ist Referenzevidenz; P0 überführt dessen Ablauf in Gradle.

Ant/Ivy wäre technisch tauglich: Ant führt explizite Tasks aus, Ivy löst Abhängigkeiten auf. Für dieses Projekt ist Gradle die einfachere Gesamtentscheidung, weil Projektabhängigkeiten, Parsergenerierung, Tests und JVM-/WASM-Auslieferung bereits in einem Projektmodell organisiert werden können. Ein zweiter Ant-/Ivy-Build wäre zu pflegen und würde keinen aktuellen Verbraucher vereinfachen. Diese Bewertung bezieht sich auf diesen Modulverbund, nicht auf die generelle Eignung von Ant. Quellen: [Gradle Multiprojektbuilds](https://docs.gradle.org/current/userguide/multi_project_builds.html), [Ant-Tutorial](https://ant.apache.org/manual/tutorial-tasks-filesets-properties.html), [Ivy Quick Start](https://ant.apache.org/ivy/history/latest-milestone/tutorial/start.html).

Maven Central und Maven-kompatible Veröffentlichungsmetadaten sind Repository-/Artefaktformate. Ihre Verwendung bedeutet nicht, dass Maven als Build-Tool benötigt wird.

## 2. Toolchain und feste Versionen

| Bestandteil | Entscheidung | Verifikationspflicht |
|---|---|---|
| Java-Sprache/Zielbytecode | Java 25, `--release 25`, keine Preview | JVM- und portable Prüfungen mit den konkreten verwendeten JDK-Patches |
| Gradle | feste stabile 9.x-Version mit Java 25-Unterstützung; P0 pinnt den getesteten Patch | mindestens 9.1.0 laut Kompatibilitätsmatrix; kein `latest`/Versionsbereich im Wrapper |
| Gradle-Ausführung | JDK 25 | `./gradlew --version` im Report |
| ANTLR Generator/Runtime | beide 4.13.2 als Startpin | Versionen bleiben identisch; Generator nur im Buildclasspath |
| TeaVM | 0.16.0 als Startpin | dokumentierte DSL plus tatsächlicher Gradle-/Browserlauf |
| Tests | JUnit Jupiter auf JUnit Platform | konkreter kompatibler BOM-/Engine-Pin bei P0, keine dynamischen Versionen |
| Web Image | separat installierte geeignete Oracle-GraalVM-Version | eigener P1-Versuch, nicht still als Standard-JDK vorausgesetzt |
| Node/Browser | Node 24.22.0 für portable Hostprüfungen, Browserpins je Testprofil | echte Versionsausgaben im Report; `checkNodeRuntime` prüft den Node-Pin |

`-PnodeExecutable=/absoluter/pfad/zu/node` bestimmt die Node-Ausführung für
Vertragstests und Benchmarks; ohne Angabe wird `node` verwendet und dessen
Version geprüft. Der Pin steht in `gradle.properties`. Node 22.23.1 bleibt wegen
eines reproduzierten Stillstands im optimierenden V8-Wasm-Compiler ausserhalb
dieses Hostprofils. Diagnoseversuche mit `--liftoff-only` ersetzen den Pin nicht.
Der Produktionspfad im Browser bleibt TeaVM-WasmGC mit normaler Optimierung.

Die offizielle Gradle-Matrix nennt Java 25-Unterstützung ab 9.1.0. Der am 18.10.2026 abgerufene aktuelle Dokumentationsstand bezeichnet sich als 9.8.1. **P0 muss einen tatsächlich verfügbaren und mit TeaVM getesteten 9.x-Patch fest eintragen**, vorzugsweise den dann aktuellen stabilen Patch. Dieser noch auszuführende Integrationsschritt ist keine offene Wahl des Build-Tools. Quelle: [Gradle-Kompatibilität](https://docs.gradle.org/current/userguide/compatibility.html).

Ein Wrapperpin besteht aus URL, Distributiontyp `bin`, Versionsnummer und offizieller SHA-256. Die Wrapperdateien werden mit Gradle erzeugt und nicht aus erfundenen Binärdaten erstellt. JDK-Installation kann lokal vorhanden oder in CI durch einen expliziten Setup-Schritt bereitgestellt sein. Der Build installiert nicht unbemerkt mehrere JDKs. Ein automatischer Toolchain-Download wird erst hinzugefügt, wenn sein Nutzen und seine Konfiguration konkret sind. Quelle: [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

## 3. Kleinste sinnvolle Dateistruktur

| Pfad | Zweck |
|---|---|
| `settings.gradle` | Projektname, tatsächliche Teilprojekte, zentrale Repositories |
| `build.gradle` | Root-Lifecycle und wenige Aggregationstasks |
| `gradle.properties` | Projektversion und wenige nachvollziehbare Buildparameter |
| `gradle/libs.versions.toml` | feste Bibliotheks-/Pluginversionen |
| `gradle/java-conventions.gradle` | kurze gemeinsame Java-/Test-/Archivkonventionen, explizit je Modul angewendet |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/…` | reproduzierbarer Einstieg |
| `<modul>/build.gradle` | sichtbare Plugin- und Projektabhängigkeiten des Moduls |
| `<modul>/src/main/java` | Produktionscode |
| `<modul>/src/test/java` | Einheit-/Vertragstests |
| `interlis-compiler/src/main/antlr` | gepflegte `.g4`-Quellen mit Provenienz |
| `testdata/` | kleine eigene Fixtures und Manifeste externer Korpora |
| `docs/spec/` | dieses Spezifikationspaket |
| `docs/implementation-status.md` | Fortschritt und reproduzierbare nächste Schritte |

Die gemeinsame Scriptdatei bleibt klein, deklarativ und ohne eigene Java/Groovy-Klassen. Module wenden sie ausdrücklich an. Für P0/P1 sind kein `buildSrc`, kein zusätzliches Convention-Plugin-Projekt und keine verschachtelte Composite-Build-Hierarchie erforderlich. Wird später eine echte komplexe wiederverwendbare Task benötigt, wird genau diese Buildlogik isoliert; es wird kein allgemeines eigenes Buildframework entwickelt.

## 4. Minimale Groovy-Konfiguration

Folgende Ausschnitte sind verbindliche Strukturbeispiele, keine bereits getesteten fertigen Repositorydateien. P0 führt sie im tatsächlichen Wrapperstand aus und schreibt die geprüfte Form ein.

`settings.gradle`:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}

rootProject.name = 'interlis-java'
include('interlis-model', 'interlis-compiler',
        'interlis-cli', 'interlis-wasm-teavm')
```

Weitere Module werden mit ihrem ersten ausführbaren Abschnitt aufgenommen. Abhängigkeiten auf noch nicht angelegte Projekte werden nicht auskommentiert als dauernde Vorlage behalten.

`gradle/java-conventions.gradle` setzt mindestens:

```groovy
group = 'guru.interlis'
version = providers.gradleProperty('projectVersion').get()

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
    withSourcesJar()
}

tasks.withType(JavaCompile).configureEach {
    options.release = 25
    options.encoding = 'UTF-8'
}

tasks.withType(Test).configureEach {
    useJUnitPlatform()
    systemProperty 'file.encoding', 'UTF-8'
}

tasks.withType(AbstractArchiveTask).configureEach {
    preserveFileTimestamps = false
    reproducibleFileOrder = true
}
```

Das Modul wendet zuvor sein `java-library`- oder `application`-Plugin an und bindet dann das Script ein. Testabhängigkeiten werden einmal eindeutig festgelegt und tatsächlich verfügbar gemacht. Javadoc-Artefakte und Publishing folgen, wenn eine veröffentlichte SDK-Abnahme ansteht.

Ein Bibliotheksmodul zeigt seine Abhängigkeiten beispielsweise so:

```groovy
plugins { id 'java-library' }
apply from: rootProject.file('gradle/java-conventions.gradle')

dependencies {
    api project(':interlis-model')
    implementation libs.antlr.runtime
    testImplementation platform(libs.junit.bom)
    testImplementation libs.junit.jupiter
    testRuntimeOnly libs.junit.platform.launcher
}
```

`api` ist nur richtig, wenn die öffentlichen Signaturen Typen des anderen Moduls enthalten. Interne Bibliotheken verwenden `implementation`. Die gezeigten Catalog-Aliase werden im tatsächlichen TOML passend deklariert; sie sind keine vorhandenen eingebauten Gradle-Aliase.

## 5. ANTLR-Generierung und Runtimegrenze

Das Gradle-ANTLR-Plugin erzeugt Parser über `generateGrammarSource`. Es kann Generatorbibliotheken standardmässig in die API-/Laufzeitkonfiguration vererben. Ausserdem muss die gewünschte ANTLR-Version ausdrücklich gesetzt werden. Quelle: [ANTLR-Plugin](https://docs.gradle.org/current/userguide/antlr_plugin.html).

Deshalb ist die Buildabnahme konkreter als „ANTLR-Plugin angewendet“:

1. Generator und Runtime sind explizit auf 4.13.2 gepinnt.
2. Grammatik liegt unter `src/main/antlr`; generierte Java-Dateien liegen ausschliesslich unter `build/generated-src/…`.
3. Generierter Packagepfad ist `guru.interlis.compiler.internal.antlr` oder ein gleichwertiger interner Unterpfad.
4. Runtimeartefakte enthalten nur die benötigte ANTLR-Runtime, nicht den Generator, StringTemplate oder dessen Tool-Abhängigkeiten.
5. Die automatische Konfigurationsvererbung wird gezielt gelöst oder eine isolierte Generator-Konfiguration verwendet. P0 prüft die tatsächliche Gradle-Version; ein pauschales Löschen aller `extendsFrom`-Beziehungen ist unzulässig.
6. Ein Runtime-Dependencytest und ein Wasm-Reachabilitytest kontrollieren die Grenze.
7. Die Generierung läuft nach `clean` und lässt sich aus einem frischen Checkout reproduzieren.

Wenn das integrierte Plugin mit wenig deklarativer Konfiguration sauber isolierbar ist, wird es verwendet. Wenn nicht, darf eine einzige isolierte `JavaExec`-Task mit deklarierten Inputs, Outputs, Toolclasspath und JavaLauncher eingesetzt werden. Keine dritte parallele Generatorpipeline und kein per Shell zusammengesetzter Classpath. Lexer-/Parserabhängigkeit und `tokenVocab`-Auflösung müssen im Generierungstest enthalten sein.

## 6. Lifecycle und Kommandos

| Kommando/Task | Vertrag |
|---|---|
| `./gradlew check` | Alle vorhandenen Module kompilieren; schnelle fachliche, Vertrags- und Architekturtests ausführen. Keine Browserinstallation, keine fremden Live-Repositories. |
| `./gradlew :interlis-cli:installDist` | JVM-Distribution mit Startskripten und benötigten Bibliotheken erstellen. |
| `./gradlew :interlis-compiler:conformanceTest` | Gepinnte 2.3/2.4-Suite und ihren Ergebniskatalog ausführen; Vorbedingungen/Fetched-Korpus explizit. |
| `./gradlew :interlis-compiler:semanticGoldenTest` | Semantische Referenzprojektionen und Legacyvergleich prüfen. |
| `./gradlew :interlis-workspace:incrementalTest` | Editiersequenzen, Invalidierung, Lebensdauer und Fresh-vs-Reuse-Gleichheit prüfen. |
| `./gradlew :interlis-wasm-teavm:buildWasmGC` | TeaVM-WasmGC samt Runtime erzeugen; reale Plugin-Task aus Kapitel 10. |
| `./gradlew :interlis-wasm-teavm:wasmContractTest` | Fachliche Vertragstests im Node-Profil ausführen; keine Browserprüfung behaupten. |
| `./gradlew :interlis-wasm-webimage:webImageProbe` | Vergleichsversuch mit deklarierter externer Toolchain ausführen und Ergebnis erfassen. |
| `./gradlew checkPortable` | Vorhandene JVM- und Node-Zielprofile einschliesslich wasmContractTest aggregieren. |
| `./gradlew checkBrowser` | Tatsächliche verpflichtende Chrome-/Firefox-/Safari-Matrix aus Kapitel 10 prüfen. |
| `./gradlew releaseCheck` | Alle für die gewählte Freigabe verpflichtenden Prüfungen koordinieren; fehlende Pflichtumgebung ist kein grüner Skip. |

Ab P1 hängt releaseCheck für die P1-Freigabe von check, checkPortable, checkBrowser und dem dokumentierten Web-Image-Untersuchungsnachweis ab. P0 verwendet nur seine bereits vorhandenen Tasks und darf daraus keine P1-Freigabe ableiten. Die gewählte Freigabephase wird als expliziter Taskparameter oder eigenes Releaseprofil angegeben; eine unbekannte Phase ist ein Konfigurationsfehler.

Root-Tasks aggregieren nur vorhandene Module. `check` muss nach Einführung weiterer Module weiter sinnvoll bleiben. Ein optionales lokales Profil darf Toolchainfehlen sichtbar als `NOT_RUN` berichten; im freigabepflichtigen CI-Profil ist dasselbe Fehlen ein Gate-Fehler. Es gibt keine success-Task, die nur eine Datei mit Erfolgstext schreibt.

Die vorhandenen TeaVM- und Web-Image-Contracttasks akzeptieren
`-PnodeExecutable=/absoluter/pfad/zu/node`. Reports nennen die tatsächlich
gestartete Runtime. Die Browserprüfung verwendet die zur gepinnten
Playwright-Installation gehörenden Browser; hart codierte Pfade zu anderen
Cacheversionen sind kein reproduzierbares Profil. Echtes Safari wird separat
mit dem macOS-Treiber geprüft.

Ein Erfolg erfordert sowohl bestandene Assertions als auch einen regulären
erfolgreichen Prozessabschluss. Ein vor dem Timeout geschriebener PASS-Report,
ignorierte Exitcodes oder absichtliches Beenden des Prozesses genügen nicht.
Rohberichte werden unter Build-Ausgaben erzeugt; überprüfte Evidenz wird
ausdrücklich mit Stand, Werkzeugversionen und Artefakthashes nach
`docs/evidence/` übernommen. Normale Prüfläufe verändern weder Pins noch
Erwartungen. Für P3 gehören Konformitäts-, Performance-, Distributions- und
Plattformnachweise zu den Pflichtprüfungen; fehlende Nachweise halten G3 offen.

Korpusbeschaffung, falls erforderlich, ist eine getrennte explizite Task mit festem Commit und Prüfsumme. Tests laufen gegen lokal bereitgestellte Bytes. Unit-Tests verwenden keine unstabilen Online-Modellserver. Integrationstests mit HTTP benötigen einen kontrollierten Testserver und festgelegte Antworten.

## 7. CI und lokale Entwicklung

Die erste CI besitzt eine kleine JVM-Spur für jeden Änderungsvorschlag und eine klar ausgewiesene portable Spur. Ein vollständiger Freigabelauf ergänzt grosse Korpora, Browser, Plattformen und spätere DB-Profile. Ein Zeitplanlauf kann grössere Vergleichsmatrizen ausführen; er ersetzt die verpflichtenden Gates nicht.

JVM-Unterstützung ist mindestens Linux x64, Windows x64 und macOS arm64. P0 beginnt auf einer verfügbaren Plattform; P3 stellt die zugesagte Distribution auf allen drei Plattformen tatsächlich zu. Native OS-Images und Sprachbindings sind später eigene Artefakte mit eigener Matrix. Der Browsercompiler muss keine JDBC-Treiber oder OS-Images enthalten.

Die Architekturregeln können zunächst mit kleinen gezielten Tests geprüft werden. Eine neue grosse Qualitätsplattform ist nicht erforderlich. Formatierung wird durch ein konsistentes Javaformat und einen geprüften Formatcheck festgelegt. Neue Plugins werden nach Zweck und Laufzeitkosten ausgewählt, nicht zur Maximierung der Pluginzahl.

## 8. Anforderungen

| ID | Normative Anforderung | Phase | Abnahme |
|---|---|---|---|
| BUILD-001 | Gradle-Wrapper und Groovy-DSL MÜSSEN der einzige aktive Build-Einstieg sein. | P0 | Frischer Checkout baut ohne Maven/Ant-Installation. |
| BUILD-002 | JDK 25 und feste verifizierte Versionen MÜSSEN dokumentiert sein. | P0 | Versionen-/Wrapper-/Dependencyreport. |
| BUILD-003 | Teilprojekte MÜSSEN dem azyklischen fachlichen Modulgraph entsprechen. | P0 | Dependencytest, keine impliziten Zwischen-JAR-Kopien. |
| BUILD-004 | Buildlogik MUSS klein und sichtbar bleiben; Projektzugriffe in `afterEvaluate` sind zu vermeiden. | P0 | Konfiguration ist deklarativ, Taskabhängigkeiten explizit. |
| BUILD-005 | ANTLR-Generator und Runtime MÜSSEN getrennt und gleichversioniert sein. | P0 | Runtimeclasspath und generierter Parser geprüft. |
| BUILD-006 | Generierte Artefakte MÜSSEN reproduzierbar aus Quellen entstehen. | P0 | Zwei saubere Läufe ohne Zeitstempel-/Pfadabhängigkeit in fachlichen Outputs. |
| BUILD-007 | `check` MUSS eine schnelle, netzwerkunabhängige Entwicklerabnahme sein, sobald Abhängigkeiten/Korpus bereitstehen. | P0 | Wiederholung offline ohne Live-Repositoryzugriff. |
| BUILD-008 | Pflichtprüfungen DÜRFEN bei fehlenden Tools nicht als erfolgreich übersprungen gelten. | P1 | Releaseprofil scheitert mit konkreter Vorbedingung. |
| BUILD-009 | Publishing DARF keine Voraussetzung für lokales Bauen/Testen sein. | P0 | Lokale Distribution funktioniert ohne Zugangsdaten. |
| BUILD-010 | Das historische Maven-Spike-POM DARF nicht zum zweiten aktiven Build des Produkts werden. | P0 | Gradle reproduziert die Probe; POM bleibt nur in Evidenzarchiv. |

## 9. Auslieferung

Die erste nutzbare CLI-Auslieferung ist eine `application`-Distribution mit `lib/` und Startskripten. Ein Fat-JAR wird erst ergänzt, wenn ein freigegebener Aufruf wie `java -jar ili2c.jar` es verlangt. Normale Bibliotheks-JARs bleiben unverschattet. Die konkrete Legacy-Mainclass und Dateibenennung werden über das CLI-Kompatibilitätsprofil getestet.

WASM-Auslieferungen enthalten einen versionierten JS-Lader, das Wasm-Artefakt, eine Protokollversion und einen Ressourcen-/Buildmanifest. Das Builddatum ist Betriebsmetadatum, kein semantischer Modellfingerprint. Veröffentlichungen verwenden feste Versionsnummern und nachvollziehbare Releaseprofile. Eine Maven-kompatible Publikation ist mit Gradle möglich und verpflichtet Anwender nicht zur Verwendung von Maven.


`conformanceWrapperTest` prüft mit JUnit im CLI-Testquellensatz die Fehlerpfade
der versionierten Normerwartungen und die gepinnten portablen Originalfixtures.
`check` und `conformanceCheck` hängen davon ab. `conformanceCheck` startet den
unveränderten externen `InterlisConformance.java` direkt aus Gradle mit Java 25;
Archivprüfung, Konfiguration und direkte Prozessausführung stehen in
`gradle/conformance.gradle`, die Pins in `gradle/conformance-suite.properties`.
Der rohe Bericht und Runner-Exit bleiben unter `build/conformance/` erhalten.
Eine separate Java-Testhilfe bewertet ausschliesslich die expliziten
Normabweichungen mit Original-/Kontrollhashes und strukturierten Diagnosen und
schreibt den Projektbericht unter `build/reports/conformance/`. Die Suite bleibt
alleiniger Runner; Erwartungen, Baseline und Pins werden nie automatisch aktualisiert.

Die funktionalen portablen Probeläufe werden nach den im selben Taskgraph
angeforderten Compiler-/Native-Image-Builds und dem Referenzprozesslauf
angeordnet und untereinander serialisiert. mustRunAfter fügt keine zusätzlich
angeforderte Laufzeit hinzu; bestehende Timeouts/Budgets bleiben unverändert.
Das verhindert konkurrierende CPU-/Speicherlast der Verifikationsprozesse,
ist aber selbst kein Performance- oder Plattform-Abnahmenachweis.

E1-Parsernachweis: Eine ausgeführte ANTLR-Regeneration darf keine Tokennummern
aus früheren Ausgaben wiederverwenden. Der Generierungstask räumt sein eigenes
generiertes Ausgabeverzeichnis vor der Neuerzeugung; Lexer/Parser und deren
Tokenvokabular müssen zum gleichen Grammatikstand gehören. Quellverzeichnisse
werden nicht verändert. Inkrementelle und frische Builds müssen übereinstimmen.

### L0B-3: Java-Hostprüfungen und Pythonfreiheit

`geoCorpus`, `jvmBenchmark`, `sdkProcessCheck`, `referenceCheck` und die
Original-/View-/Unit-Referenzvergleiche verwenden das separate CLI-SourceSet
`verification`; seine Gson-/JUnit-Abhängigkeiten gehören nicht zur Distribution.
`verificationTest` prüft Fehlerpfade und das eingefrorene Unicode-NFD-Orakel.
`pythonFreeCheck` ist Teil von `check` und prüft getrackte sowie nicht ignorierte
Eingaben mit Negativcanarys. Historische Texte bleiben lesbar. Das abschliessende
0b-Gate läuft zusätzlich mit gesperrtem Interpreterzugriff einschliesslich
absoluter Aufrufe, nicht nur mit einem bereinigten PATH. Kein älterer Gradle-
Daemon darf für diesen Nachweis verwendet werden. Plattform-/Prozessreports
bleiben frisch und an dieselben geprüften Eingaben gebunden.

### P3: vollständiger Performancevertrag

`fullCorpus` prüft den eingefrorenen offiziellen Index und sämtliche
Originaldateien mit expliziten Encodings und Byte-/Texthashes.
`fullCorpusJvm`, `fullCorpusWasm`, `fullCorpusBrowsers` und `fullCorpusSafari`
erzeugen aktuelle vollständige öffentliche Protokollcaptures.
`fullCorpusEditorWasm`, `editorPressure`, `observationPressure` und
`fullCorpusArtifacts` messen lange reale Sitzungen, Abbruch, begrenzte
Beobachtung und ausgelieferte Roh-/Kompressionsgrössen. `performanceCheck`
führt das vollständige feste Profil aus Kapitel13 aus; das frühere13er-Profil
bleibt über seine bisherigen Einzeltasks als historische Evidenz verfügbar.
Ein fehlender Safari-/Runtime-/Hardware-/Ergebnisnachweis scheitert, ohne Skip.
