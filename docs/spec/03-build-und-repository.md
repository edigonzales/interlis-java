# Build und Repository

## 1. Buildbesitz seit Auftrag B

Der ausdrückliche Nutzerauftrag vom05.11.2026 ersetzt die parallele A-Spur:
Jenesis0.18.1 (Quellpin in `build/JENESIS-UPSTREAM.json`) ist alleiniger
JVM-Builder. Gradle9.8.1 mit Groovy-DSL und Wrapper bleibt ausschliesslich
für `interlis-wasm-teavm` und `interlis-wasm-webimage`. POMs definieren
Koordinaten, Scopes und Pins; Maven/Ant werden nicht ausgeführt.
Die Änderung betrifft BUILD-001/007/010 und die Ausführung bestehender Gates;
fachliche Pflichtmengen werden nicht gesenkt.

Operativer Fortschritt und tatsächliche Nachweise stehen in
[Jenesis B](../implementation/jenesis-migration-b/STATUS.md).
Historische A-/G2-Belege behalten Builder, Pfade und ihren damaligen Stand.
Ein eingeschränkter Erfolg ist keine allgemeine Produktfreigabe.

## 2. Toolchains und Grenzen

Java25 ohne Preview und `--release 25` bleibt verbindlich. Die bestehenden
Pins für ANTLR4.13.2, JUnit5.11.4, TeaVM0.16.0, LSP4J0.23.1, Gson2.14.0,
Node24.22.0 sowie die expliziten GraalVM-/Binaryenprofile bleiben erhalten.
Die tatsächlichen Versionen und Prozess-Exits gehören zum Beleg.
Der Default-JVMbuild benötigt weder Node noch Gradle, Extensioncheckout,
Browser, Web Image oder native APE-Toolchain. APE/Uhrpatch bleiben pausiert.
Externe Installationen werden ausdrücklich bereitgestellt, nicht still nachgeladen.

Alle eigenen Produkte erhalten in B-II explizite JPMS-Deskriptoren. Exporte
folgen öffentlichen API-Packages; interne ANTLR-/Implementierungspackages
bleiben gekapselt. Testpatches und Testopens gehören ausschliesslich in den
engen Testhook. Reguläre CLI-/LSPstarts verwenden benannte Module.
Gleiche modulare JARs bleiben für Classpath-/Wasmverbraucher verwendbar.

## 3. Quellen, Outputs und Übergaben

| Pfad | Besitzer/Zweck |
|---|---|
| `pom.xml`, `<modul>/pom.xml`, `jenesis.properties` | Zehn JVM-Module, Version, Dependency-/Generator-/Testkonfiguration |
| `build/InterlisJvmBuild.java`, `build/interlis/` | Kleiner Projekteinstieg und explizite Adapter |
| `build/jenesis/`, `build/JENESIS-UPSTREAM.json` | Unveränderte gepinnte Enginequellen |
| `target/jenesis-jvm/engine` | Tatsächliche Engineergebnisse |
| `target/jenesis-jvm/reports`, `evidence`, `host` | Inventare, rohe JUnit-/Prozessreports, explizite Laufbindung |
| `target/jenesis-jvm/consumers/<digest>` | Immutable Mavenclosure mit gehashten JARs/POMs/Sources |
| `target/jenesis-jvm/editor-bundles/<hash>` | LSP-JARs, Renderer, Typen, Schemas und Lizenz |
| `settings.gradle`, `build.gradle`, `gradle/*` | Ausschliesslich zwei Backendadapter, deren Pins/Preflight |
| `<modul>/src/main`, `src/test`, `testdata/` | Unveränderte Quellstruktur und fachliche Tests |
| `.jenesis` | Dependencycache, kein eigener globaler Maven-SNAPSHOT |

Ein Consumer prüft Manifestidentität, Producerbezug, exakten Dateisatz,
Closure/POMs und Hashes vor der Auflösung und nach der Erzeugung. Kein
`mavenLocal`, keine alten Gradle-JARs, keine zweite Produktionskompilierung.
Verifikation verwendet ein explizites gehashtes Layout mit Laufwurzel;
fehlende Rollen werden nicht durch alte Gradlepfade ergänzt.
JVM-, Consumer- und Backendlocks haben getrennte endliche Zuständigkeiten.

## 4. Architektur und Tests

Der fachliche DAG bleibt unverändert. Öffentliche APIs benutzen eigene
Quell-/Modelltypen, niemals ANTLR. SDKprodukte bleiben unverschattet.
Normale Tests erhalten Parameter-/dynamische Fälle, echte Assertionausführung
und unveränderte Rohreports. Compilercontracts werden frisch selektiert,
danach prüfen gebundene Consumer die Produzenten. Leere Auswahl, Fehler,
unerlaubte Skips, Abbruch und fremde Laufbindung sind Gatefehler.

## 5. ANTLR und Ressourcen

Die gepinnte Engine generiert Lexer/Parser aus `interlis-compiler/src/main/antlr`
in ihre eigenen Outputs. Generator und Runtime bleiben4.13.2. Generierte
Klassen liegen intern unter `guru.interlis.compiler.internal.antlr` und
werden nicht öffentlich exportiert. Tokennummern, `tokenVocab`, UTF-16-Positionen
und EOF werden aus demselben Grammatikstand geprüft. Keine Testgrammatik
wird durch eine Dummy-Task ersetzt. Der normale Produktclasspath enthält
ausschliesslich die Runtime, niemals Generator/StringTemplate/Testwerkzeuge.
Ressourcen und Lizenzen bleiben vollständig in den erzeugten JARs/Sources.
Frische und wiederverwendete Erzeugung müssen fachlich übereinstimmen.

## 6. Lifecycle und Kommandos

Alle JVMkommandos verwenden `java build/InterlisJvmBuild.java` (Java25).

| Profil | Vertrag |
|---|---|
| `build` | Zehn Module, normale Tests; kein Gradle/Node/Browser/Live-Repository nötig. |
| `build --module interlis-compiler --tests Klassenregex#Methode` | Gezielte echte Auswahl; leer ist Fehler, kein Gesamtbeleg. |
| `artifacts` | Nur Produkte/Sources/POMs; ausdrücklich kein Testnachweis. |
| `contracts` | Frische ausgewählte Assertions, anschliessend gebundene Consumer. |
| `sdk-package` / `sdk-check` | Inventargesteuerte CLI-/LSP-/SDK-Produktion bzw. reale Prozess-/Archivprüfung. |
| `host --profile conformance` | Gepinnter externer Runner und getrennte normative Bewertung. |
| `host --profile reference` | Gepinnte ili2c-Gegenprobe. |
| `host --profile python-free` | Quellen-/Buildgrenzenprüfung mit Negativcanarys. |
| `wasm-export` | Immutable Mavenclosure und verifizierter ConsumerSnapshot. |
| `wasm --profile teavm-smoke --inputs ABSOLUT --inputs-sha256 HASH` | Frische JS-/WasmGC-Erzeugung und schmale echte Laufzeitfälle. |
| `wasm --profile webimage-smoke --inputs ABSOLUT --inputs-sha256 HASH` | Web Image mit expliziter externer Toolchain und echter Laufzeitprobe. |
| `host --profile portable` / `host --profile browser` | Explizite portable Vertrags- bzw. reale Browserabnahme. |
| `host --profile performance` | Vollständige bestehende P3-Leistungspflichten. |
| `release-check --profile P3` | Vollständiger fachlicher Freigabeplan; fehlende Pflichtbelege sind Fehler. |
| `editor-bundle` | Node-Renderer und echte Jenesis-LSP-Closure, reiner Bundleassembler. |
| `clean --scope jvm\|consumer\|wasm` | Endliche Outputbesitzer; niemals Root-build-Quellen oder Caches löschen. |

Direkte Backend-Wrapperaufrufe benötigen `-PjvmInputs=/absolut/consumer-manifest.json`
und `-PjvmInputsSha256=PRODUCER_HASH`. Gradle enthält nur zwei Adapterprojekte.

Die Releasepflichten umfassen normale JVMtests, Contracts, portable und Browserprüfungen sowie den dokumentierten Web-Image-Nachweis. Ein eingeschränktes Profil erteilt keine vollständige Freigabe. Die gewählte Freigabephase wird als expliziter Taskparameter oder eigenes Releaseprofil angegeben; eine unbekannte Phase ist ein Konfigurationsfehler.

Der Projekteinstieg wählt ausschliesslich vorhandene Module und feste Profile. Ein optionales lokales Profil darf Toolchainfehlen sichtbar als `NOT_RUN` berichten; im freigabepflichtigen CI-Profil ist dasselbe Fehlen ein Gate-Fehler. Es gibt keine success-Task, die nur eine Datei mit Erfolgstext schreibt.

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

Das aktuelle P3-Profil wird ausdrücklich mit `release-check --profile P3` gewählt. `release-check` ohne Profil und
unbekannte Profile scheitern bereits bei der Konfiguration. Vor dem Gesamtlauf
werden erfolgreiche Windows-/Linux-Actions desselben sauberen Produktcommits
mit `tools/release/platform-evidence.cjs` bezogen und gegen die von GitHub
gelieferten Archivprüfsummen geprüft. Der Gesamtlauf erfolgt auf der
macOS-arm64-Referenzmaschine unter `tools/verification/without-python.cjs`;
der aktive, vererbte OS-Ausführungsschutz ist eine Vorbedingung. Eine blosse
frühere Erfolgsmeldung dieses Schutzes reicht nicht.

`tools/release/evidence.cjs` bindet die tatsächlich geladenen Laufzeitartefakte,
Quellfingerprints, Fallzahlen, Plattformprozesse, Corpusmessungen und zwei
saubere reproduzierbare Builds an diesen Commit. Er prüft auch das ausgelieferte
Browser-Buildmanifest und die getrennten Versionsverträge. Geänderte Quellen,
fremde JARs, fehlende Browser und alte oder übersprungene Belege ergeben keinen
Erfolg. Der interne Taskerfolg ersetzt weder den erfolgreichen Abschluss des
äusseren Schutzprozesses noch den unabhängigen kumulativen G3-Review.

Die Vollständigkeit wird gegen die tatsächliche Lieferdateimenge geprüft,
nicht nur gegen einen Selbstzähler im Bericht. Das Browsermanifest enthält
exakt sämtliche tatsächlich gelieferten Webressourcen ausser sich selbst,
jeweils mit passendem Hash und passender Bytezahl. Eine gekürzte Liste mit
entsprechend reduziertem Zähler, ein partielles Manifest oder explizite
Skip-/Fehler-/Abbruchangaben dürfen auch neben positiven Gesamtsummen keinen
Erfolg ergeben.

TeaVM wird unverändert in der gepinnten Upstream-Version 0.16.0 verwendet.
Für dessen JavaScript-/Wasm-Ausgaben wird keine binäre Reproduzierbarkeit
zugesichert. Die Freigabe basiert auf den funktionalen und relevanten
nichtfunktionalen Prüfungen des konkreten Auslieferungsartefakts.
Dessen Integritäts- und Herkunftsnachweise bleiben verbindlich.

Beide Generatoren prüfen den gepinnten offiziellen Core im tatsächlichen
Compilerclasspath gegen `gradle/teavm-core.json`. Das Browsermanifest bindet
diese Identität an die generierten Wasm-Bytes. JVM-/SDK-Dateien, kopierte
TeaVM-Runtime und unveränderte Browserwerkzeuge bleiben bytegleichheitspflichtig.
Einmal bauen, genau diese Dateien testen, unverändert archivieren und ausliefern.
Ein unabhängiger Build ersetzt keinen Laufzeitnachweis der ausgelieferten Datei.

Korpusbeschaffung, falls erforderlich, ist eine getrennte explizite Task mit festem Commit und Prüfsumme. Tests laufen gegen lokal bereitgestellte Bytes. Unit-Tests verwenden keine unstabilen Online-Modellserver. Integrationstests mit HTTP benötigen einen kontrollierten Testserver und festgelegte Antworten.

## 7. CI und lokale Entwicklung

Die erste CI besitzt eine kleine JVM-Spur für jeden Änderungsvorschlag und eine klar ausgewiesene portable Spur. Ein vollständiger Freigabelauf ergänzt grosse Korpora, Browser, Plattformen und spätere DB-Profile. Ein Zeitplanlauf kann grössere Vergleichsmatrizen ausführen; er ersetzt die verpflichtenden Gates nicht.

JVM-Unterstützung ist mindestens Linux x64, Windows x64 und macOS arm64. P0 beginnt auf einer verfügbaren Plattform; P3 stellt die zugesagte Distribution auf allen drei Plattformen tatsächlich zu. Native OS-Images und Sprachbindings sind später eigene Artefakte mit eigener Matrix. Der Browsercompiler muss keine JDBC-Treiber oder OS-Images enthalten.

Die Architekturregeln können zunächst mit kleinen gezielten Tests geprüft werden. Eine neue grosse Qualitätsplattform ist nicht erforderlich. Formatierung wird durch ein konsistentes Javaformat und einen geprüften Formatcheck festgelegt. Neue Plugins werden nach Zweck und Laufzeitkosten ausgewählt, nicht zur Maximierung der Pluginzahl.

## 8. Anforderungen

| ID | Normative Anforderung | Phase | Abnahme |
|---|---|---|---|
| BUILD-001 | Jenesis MUSS der alleinige aktive JVM-Builder sein; Gradle/Groovy/Wrapper MUSS ausschliesslich die zwei Wasm-Backends bauen. | P0 | Frischer Checkout baut ohne Maven/Ant-Installation. |
| BUILD-002 | JDK 25 und feste verifizierte Versionen MÜSSEN dokumentiert sein. | P0 | Versionen-/Wrapper-/Dependencyreport. |
| BUILD-003 | Teilprojekte MÜSSEN dem azyklischen fachlichen Modulgraph entsprechen. | P0 | Dependencytest, keine impliziten Zwischen-JAR-Kopien. |
| BUILD-004 | Buildlogik MUSS klein und sichtbar bleiben; Projektzugriffe in `afterEvaluate` sind zu vermeiden. | P0 | Konfiguration ist deklarativ, Taskabhängigkeiten explizit. |
| BUILD-005 | ANTLR-Generator und Runtime MÜSSEN getrennt und gleichversioniert sein. | P0 | Runtimeclasspath und generierter Parser geprüft. |
| BUILD-006 | Generierte Artefakte MÜSSEN aus gepinnten Quellen entstehen; fachliche Ergebnisse bleiben deterministisch. JVM-/SDK- und nicht von TeaVM-Programmen abhängige Lieferdateien MÜSSEN binär reproduzierbar bleiben. TeaVM-Programme und ausschliesslich ihre Hash-/Grössenfelder im Manifest sind davon ausgenommen. | P0 | Zwei unabhängige Builds mit vollständigen Inventaren, funktionalen JS-/Wasm-Nachweisen und präzisem Bytevergleich; Integrität der konkreten Lieferung bleibt geprüft. |
| BUILD-007 | Der normale JVMbuild MUSS eine schnelle, netzwerkunabhängige Entwicklerabnahme sein, sobald Abhängigkeiten/Korpus bereitstehen. | P0 | Wiederholung offline ohne Live-Repositoryzugriff. |
| BUILD-008 | Pflichtprüfungen DÜRFEN bei fehlenden Tools nicht als erfolgreich übersprungen gelten. | P1 | Releaseprofil scheitert mit konkreter Vorbedingung. |
| BUILD-009 | Publishing DARF keine Voraussetzung für lokales Bauen/Testen sein. | P0 | Lokale Distribution funktioniert ohne Zugangsdaten. |
| BUILD-010 | Das historische Maven-Spike-POM DARF nicht zum zweiten aktiven Build des Produkts werden. | P0 | Jenesis reproduziert die Produkte; aktive POMs sind Metadaten, das historische Spike-POM bleibt Evidenz. |

## 9. Auslieferung

Die CLI-Auslieferung ist eine Jenesis-erzeugte Distribution mit `lib/` und Startskripten. Ein CLI-Fat-JAR und ili2c-Kompatibilitätsartefakte gehören nicht zur Lieferung. Normale Bibliotheks-JARs bleiben unverschattet. Die Hauptklasse ist guru.interlis.cli.Main; der Launchername ist explizit interlis-compiler. Das CLI-JAR bleibt eindeutig interlis-compiler-cli-<version>.jar.

WASM-Auslieferungen enthalten einen versionierten JS-Lader, das Wasm-Artefakt, eine Protokollversion und einen Ressourcen-/Buildmanifest. Das Builddatum ist Betriebsmetadatum, kein semantischer Modellfingerprint. Veröffentlichungen verwenden feste Versionsnummern und nachvollziehbare Releaseprofile. Maven-kompatible Publikationsmetadaten sind möglich und verpflichtet Anwender nicht zur Verwendung von Maven.


Die normalen CLI-Conformancetests prüft mit JUnit im CLI-Testquellensatz die Fehlerpfade
der versionierten Normerwartungen und die gepinnten portablen Originalfixtures.
`host --profile conformance` startet den unveränderten externen
`InterlisConformance.java` direkt mit Java25;
Archivprüfung, Konfiguration und direkte Prozessausführung stehen in
`build/interlis/HostVerificationProfiles.java`, die Pins in `testdata/conformance/suite.properties`.
Der rohe Bericht und Runner-Exit bleiben unter dem expliziten Hostlauf unter `target/jenesis-jvm/host/` erhalten.
Eine separate Java-Testhilfe bewertet ausschliesslich die expliziten
Normabweichungen mit Original-/Kontrollhashes und strukturierten Diagnosen und
schreibt den Projektbericht unter dessen `reports/conformance/`. Die Suite bleibt
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

Die Hostprofile `geo-corpus`, `jvm-benchmark`, `sdk`, `reference` und die
Original-/View-/Unit-Referenzvergleiche gehören zum internen Modul
`interlis-verification` mit ausschliesslich `src/main` und `src/test`.
Seine Gson-/JUnit-Abhängigkeiten gehören nicht zur Distribution. Tool- und
Validator-Unit-Tests laufen unter `build --module interlis-verification`; aktuelle
Repository-/Runbindungen unter `contracts` mit dem Tag `compiler-contract`.
Direkte OID-/Metadaten-/Unicode-NFD-Regressionsfälle gehören zum Compiler.
`host --profile python-free` prüft getrackte sowie nicht ignorierte
Eingaben mit Negativcanarys. Historische Texte bleiben lesbar. Das abschliessende
0b-Gate läuft zusätzlich mit gesperrtem Interpreterzugriff einschliesslich
absoluter Aufrufe, nicht nur mit einem bereinigten PATH. Kein älterer Gradle-
Daemon darf für diesen Nachweis verwendet werden. Plattform-/Prozessreports
bleiben frisch und an dieselben geprüften Eingaben gebunden.

### P3: vollständiger Performancevertrag

`host --profile full-corpus` prüft den eingefrorenen offiziellen Index und sämtliche
Originaldateien mit expliziten Encodings und Byte-/Texthashes.
die Hostprofile `full-corpus-jvm`, `full-corpus-wasm`, `full-corpus-browsers` und `full-corpus-safari`
erzeugen aktuelle vollständige öffentliche Protokollcaptures.
die Hostprofile `full-corpus-editor-wasm`, `editor-pressure`, `observation-pressure` und
`full-corpus-artifacts` messen lange reale Sitzungen, Abbruch, begrenzte
Beobachtung und ausgelieferte Roh-/Kompressionsgrössen. `host --profile performance`
führt das vollständige feste Profil aus Kapitel13 aus; das frühere13er-Profil
bleibt über seine expliziten Einzelprofile als historische Evidenz verfügbar.
Ein fehlender Safari-/Runtime-/Hardware-/Ergebnisnachweis scheitert, ohne Skip.

Der explizite Runtimecheck verwendet `java build/InterlisJvmBuild.java runtime-check --application compiler`. Er erzeugt ein privates JDK-only-Image mit unveränderten modularen CLI-JARs, prüft SDK/HTTP/HTTPS sowie eine lokale app-image-Eignungsprobe. App-Image-Proben verwenden die technische macOS-kompatible Version 1.0; sie ändern keine GAV und sind keine Standarddistribution oder Installerfreigabe. Die Extension besitzt weiterhin ihre eigene gepinnte JRE-/jlink-Auswahl.

Host-/Contract-/SDK-Layouts schreiben relative Rollen immer mit `/`, auch auf Windows; die Java- und Node-Leser lehnen native Backslashes in Rollen weiterhin ab. `JvmBuildLock` ist eine aufrufgebundene Producerlease. Explizite immutable Backend-/Node-Verbraucher suspendieren sie, prüfen anschliessend die ursprünglichen Quellinputs und normalisieren den aktuellen Artefaktstand nach Wiedererwerb. SDK-Prozessprüfung und reine Paketierung verwenden dieselben tatsächlichen CLI-Versionsbytes.
