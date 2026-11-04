# Spezifikation — Auftrag A: Jenesis-JVM-Buildparität (final)

**Finalisiert am 4. November 2026.** Diese Fassung ersetzt die frühere Auftrag-A-Fassung vollständig. Massgeblich ist weiterhin `docs/implementation/jenesis-jvm-a/`; keine zusätzlichen älteren Addenda parallel anwenden.

## 1. Zweck und Begriffe

Auftrag A ändert den **Buildweg**, nicht die Produktarchitektur. Derselbe Java-Quellstand soll zusätzlich zum bisherigen Gradle-Build durch Jenesis kompiliert, getestet und in den vorhandenen JVM-Artefaktformen bereitgestellt werden. Die Referenz ist der tatsächlich untersuchte und getestete lokale Ausgangsstand, nicht bloss eine frühere Erfolgsmeldung.

„POM-basiert“ bedeutet: POMs sind deklarative Eingaben von Jenesis. Maven ist weder Voraussetzung noch Ausführer. „Parität“ bedeutet vollständige Zuordnung von Eingaben, Dependencies, Klassen/Ressourcen, Tests und beobachtbarem Verhalten. Zwei ZIP-Dateien müssen nicht allein deshalb denselben Hash besitzen, weil sie denselben Produktstand verpacken; die Grenzen des Vergleichs stehen in Abschnitt 9.

„Keine JPMS-Migration“ bedeutet: kein neuer eigener Moduldeskriptor, keine Testpackageänderung, keine geänderte Produktsichtbarkeit. Bereits vorhandene Launcher, die gewöhnliche JARs als **automatische Module** starten, bleiben in Funktion. Das ist nicht die spätere explizite Modularisierung.

„Ohne Wasm“ bedeutet: keine Migration oder Ausführung der TeaVM-/Web-Image-Codegenerierung als Pflicht dieses Auftrags. Reine Java-Bridgeklassen und JVM-Tests bleiben ausdrücklich enthalten. Ein bestehender gemeinsamer Testfixturepfad mit `wasm` im Namen ist deshalb nicht automatisch aus dem Testumfang zu entfernen.

## 2. Umfang und Nichtziele

### 2.1 Genau zehn JVM-Projekte

| Projekt | Rolle | Wesentliche direkte Projektabhängigkeiten im gelesenen Stand |
|---|---|---|
| `interlis-model` | Modell, Quellen, Diagnosen, Ausführungstypen | keine |
| `interlis-compiler` | Parser/Compiler/Queries/Formatter | model |
| `interlis-workspace` | Workspace und inkrementelle Arbeit | compiler, model |
| `interlis-repository` | Modellauflösung und Hostrepository | workspace, model |
| `interlis-projection` | Diagrammprojektion | compiler, model |
| `interlis-documentation` | Dokumentationsmodell/-ausgabe | model, projection; compiler für Tests |
| `interlis-wasm-bridge` | normale Java-Protokollbridge | workspace, compiler, projection, model |
| `interlis-compiler-cli` | vorhandene Compileranwendung | compiler, workspace, repository, projection, model |
| `interlis-lsp` | vorhandener LSP-Server | workspace, repository, compiler, projection, documentation, model |
| `interlis-verification` | interne Prüfwerkzeuge | compiler, model, workspace, repository, wasm-bridge; CLI für Tests |

Die Tabelle ist gegen die effektiven Gradle-Konfigurationen in A0 abzugleichen. Keine direkten Abhängigkeiten allein anhand transitiver Zufallstreffer entfernen. `interlis-verification` ist kein veröffentlichtes SDK-Modul. Seine internen JARs dürfen für Prüfprozesse entstehen, aber nicht in CLI/LSP/SDK oder öffentliche Maven-Staginginventare gelangen.

### 2.2 In Auftrag A enthalten

- Deklarative POMs, feste Engine-/Tool-/Dependencybindungen, normale Main-/Testsourcepfade.
- ANTLR-Generierung, Ressourcen/Lizenzen, JVM-Kompilierung, normale JARs und Sources-JARs.
- Vollständige normale JVM-Testmenge der zehn Module auf dem Classpath.
- Explizite aktuelle Compiler-Contracts und deren frisch ausgewählte Produzententests.
- Kleine reale SDK-, CLI- und LSP-Prüfungen gegen die neuen Artefakte.
- Bestehende JVM-Distributionsform funktional nachbilden, ohne neues Produktformat.
- Eng begrenzte Buildlayout-/Reportadapter im internen Verifikationsbereich.
- Nachweis getrennten, sauberen und inkrementell korrekten Betriebs beider Buildspuren.
- Schutz des vorhandenen VS-Code-Core-Stagingpfads plus begrenzter echter Kompatibilitätsnachweis mit Jenesis-erzeugten LSP-JARs; keine dauerhafte Migration des Extension-Builds.
- Normales gehashtes Compiler-Runtimeinventar als vorhandene JVM-Lieferinformation, auch für einen später separat beauftragten APE-Versuch verwendbar.

### 2.3 Ausdrücklich nicht enthalten

Keine Jenesis-Übernahme von `interlis-wasm-teavm` oder `interlis-wasm-webimage`; keine Umstellung ihrer `project(...)`-Dependencies auf Maven-Koordinaten. Keine Frontend-, Extension-, VSIX- oder dauerhafte Editorbundlemigration. Der ausdrücklich beauftragte isolierte Kompatibilitätsnachweis nach Abschnitt 8 und `EDITOR_COMPATIBILITY.md` ist davon ausgenommen. Kein Umbau des vollständigen P3-Releaseaggregators, keine neue Browser-/Benchmark-/Plattformvollabnahme. Keine Entfernung des Gradle-Wrappers oder der Java-Gradle-Dateien. Kein Publishing, `export` nach `~/.m2`, Release, Push oder Merge. Keine Produkt-/JDK-/ANTLR-/JUnit-/Gson-/LSP4J-Upgrades, keine Native-Image-/Cosmopolitan-/CosmoGVM-/APE-Integration und kein TeaVM-Patch. Kein neuer nativer LSP, keine Einführung neuer Runtime-Auswahlmöglichkeiten.

Für die Koexistenz notwendige **kleine** Anpassungen an Gradle-Clean, aktueller Dokumentation, Quellenbindungen und Default-kompatiblen Verifikationspfaden sind erlaubt. Sie dürfen keine Wasm-Tasksemantik oder bisherige Releasepflicht lockern. Das Aufteilen eines bestehenden Bundle-Assemblers in eine gemeinsame reine Funktion ist nur zulässig, wenn es die schmale Kompatibilitätsprobe vereinfacht und alle bisherigen Defaults unverändert bleiben; kein neues öffentliches Plugin-/Backendmodell.

### 2.4 Verbindliche Auslieferungsgrenze

Die neue Priorität lautet: **A → separat beauftragter Compiler-APE-Machbarkeitsversuch → gegebenenfalls APE-Releaseintegration.** Eine vollständige explizite JPMS-Migration, die Wasm-Artefaktentkopplung und eine spätere Umstellung der normalen Extension auf Jenesis sind eigenständige Entscheidungen. Nichts davon wird durch A automatisch gestartet; ein früher erwähnter grosser „Auftrag B“ ist keine Abnahmevoraussetzung.

Der LSP bleibt JVM-Anwendung. Die vorhandene Extension liefert Java bereits mit und darf ihre vorhandene jlink-Optimierung weiterverwenden. „Kein jlink in A“ bedeutet **keine neue Runtime-Pipeline im Core**, nicht das Abschalten der vorhandenen Extension-Pipeline. Weder ein neues `module-info.java` noch ein `jpackage`-App-Image ist für diesen Auftrag nötig.

Der spätere Compiler-APE-Pfad soll normale JARs konsumieren können. In A genügt deshalb das ohnehin notwendige Artefakt-/Runtimeinventar; keine vorsorgliche Native-Schicht, kein leerer `ape`-Task und kein Export des internen Verifikationsmoduls.

## 3. Zielstruktur, Einstieg und Buildbesitz

Vorgesehene Struktur:

```text
pom.xml                              # Aggregator/Parent, nur zehn JVM-Kinder
jenesis.properties                   # maven-Layout, feste Defaults
jenesis-contracts.properties         # nur erforderliche explizite Overrides
build.jenesis/                       # gemeinsame Toolkonfiguration
build/
  jenesis/                          # unveränderte versionierte Upstream-Engine
  InterlisJvmBuild.java              # dünner Projekteinstieg, package build
  interlis/                         # kleine projektbezogene Adapter, nicht Produkt
<jvm-modul>/pom.xml
<jvm-modul>/build.jenesis/            # nur wo modulbezogene Unterschiede bestehen
<jvm-modul>/src/test/build.jenesis/   # nur testspezifische Konfiguration
interlis-wasm-teavm/.jenesis.skip
interlis-wasm-webimage/.jenesis.skip
docs/.jenesis.skip                   # historische POMs sind keine Projekte
# Weitere klar abgegrenzte Marker nur, wenn die Discovery sie benötigt.
target/jenesis-jvm/                  # ausschliesslich generierte neue Ausgaben
  engine/                           # tatsächlicher Engine-Targetbaum
  delivery/                         # bestehende CLI/LSP/SDK-Form
  reports/                          # Indexe und Paritätsberichte
  evidence/                         # unveränderliche runbezogene Rohbelegkopien
  editor-compat/                    # isolierte Verbrauchertestdaten; keine Releasefreigabe
.jenesis/                           # normale Enginecache-/Mechanikdaten, ignoriert
```

Der neue dokumentierte Projekteinstieg ist:

```sh
java build/InterlisJvmBuild.java build
java build/InterlisJvmBuild.java contracts
java build/InterlisJvmBuild.java sdk-check
java build/InterlisJvmBuild.java parity --baseline <absoluter-baseline-belegpfad>
java build/InterlisJvmBuild.java editor-check --extension-dir <absolutes-extension-checkout> --reference-bundle <absolutes-geprueftes-bundle>
```

Diese **projektspezifischen** Kommandos sind Implementierungsvorgaben, keine Behauptung eingebauter Jenesis-Selektoren. `build` ist der Default. Eine enge Auswahl `--module <bekannter-modulname>` und `--tests <explizite-auswahl>` ist für Entwickler/Follow-up-Fixes vorgesehen; sie muss als eingeschränkter Lauf ausgewiesen werden. Unbekannte Module, Profile oder Filter sind Fehler, keine leeren erfolgreichen Läufe.

`editor-check` ist ein ausdrücklich gewählter Verbrauchercheck, kein Bestandteil jedes normalen `build`. Er prüft bereits erzeugte Jenesisartefakte sowie separat bereitgestellte Referenzressourcen; fehlende Eingaben sind Fehler, kein Anlass, darin heimlich Gradle zu starten. Die dafür benötigten vorhandenen Node-/Extension-Testwerkzeuge bleiben ihrer bisherigen Zuständigkeit zugeordnet.

Der Einstieg delegiert Kompilierung, Dependencyauflösung, JARs und normale Tests an die **unveränderte** Jenesis-Engine. Er ergänzt nur Root-/Inputübergabe, explizite Verträge, bestehende Verpackung und Paritätsprüfung. Kein eigener allgemeiner Task-Scheduler, Resolver, Compiler oder alternativer JUnit-Runner. Kein Shellstring mit ungequoteten Pfaden.

Der Standardaufruf `java build/jenesis/Make.java` bleibt als Engineeinstieg verfügbar. Er ist nicht ohne Weiteres mit dem vollständigen Projektergebnis gleichzusetzen, falls projektspezifische Inputs/Vorprüfungen nur der dünne Einstieg bereitstellt. Dokumentiere diese Grenze; ein solcher Rohaufruf darf keine fertige Projektabnahme vortäuschen.

### 3.1 Koexistenz ohne Clean-Falle

Gradles bisherige Ausgabeorte bleiben unverändert. Vor dem Einchecken der Engine wird der **Root-Clean** so eingegrenzt, dass `build/jenesis/**`, `build/InterlisJvmBuild.java`, `build/interlis/**` und ihre versionierten Begleitdateien erhalten bleiben. Andere selbst erzeugte Root-Buildausgaben dürfen bereinigt werden. Modul-Clean bleibt bei seinen normalen Outputs.

Setze die Löschmenge mit der unterstützten Gradle-API ausdrücklich neu; nicht nur ein zweites `delete` hinzufügen, während das ursprüngliche Löschen von `build/` bestehen bleibt. Keine Datenrettung aus Git nach dem Löschen, kein „clean nicht verwenden“ als einzige Sicherung. Keine Symlinks aus dem Ausgabeordner heraus verfolgen. Nachweis in einer wegwerfbaren Kopie mit Hashinventar aller versionierten Buildquellen und Sentineldateien in Ausgabeordnern.

Passe `.gitignore` so an, dass die Engine und eigene Buildquellen tatsächlich versioniert werden, aber generierte `target/`, `.jenesis/` und normale Modul-Buildausgaben ignoriert bleiben. Prüfe die endgültigen Regeln mit `git check-ignore`; ein ignoriertes Elternverzeichnis kann eine Ausnahme unwirksam machen.

Referenz und Kandidat für Paritätsvergleiche laufen in getrennten Worktrees. Nach Abschluss muss auch im selben Checkout gelten: Jenesis überschreibt keine Gradle-Ausgaben, Gradle-Clean entfernt keine Jenesis-Quellen, und keine Spur liest versehentlich die Produkte der anderen.

## 4. Engine, POMs und feste Abhängigkeiten

### 4.1 Enginepin und Funktionsnachweis

Startkandidat: Jenesis **0.18.1**, Commit `177014053cce311924f6724d4e4a7fe5689ed9b6`. Beziehe die offiziellen Quellen mit nachvollziehbarer Herkunft und Prüfsumme; erfinde keine Checksums. Versionsdatei, Lizenz und Herkunft werden mitgeführt. Keine Änderungen unter `build/jenesis/` gegenüber dem verifizierten Release.

A0 prüft an einem kleinen isolierten POM-Projekt: Main/Test auf Classpath, package-private White-Box-Test, BOM-/Runtime-/Testscope, ANTLR 4.13.2, Tag- und Methodenauswahl, tatsächliche Reports, fehlgeschlagene und leere Auswahl, externe Inputinvalidierung und benötigte Einstiegsschnittstellen. Lese die zum **gepinnten Stand** gehörenden `skill/migrate`-/Registry-Anweisungen ergänzend; sie dürfen den Auftragsumfang nicht erweitern.

Falls Dokumentation eine im Release fehlende Funktion beschreibt: zuerst eine kleine offizielle, versionskompatible Konfiguration/Adapterlösung prüfen. Kein stiller Enginefork oder Wechsel auf unreleased main. Ist eine zwingende Fähigkeit nicht mit überschaubarem Aufwand erreichbar, den konkreten Nachweis als BLOCKED festhalten, statt eine Fassade zu bauen, die Gradle aufruft.

### 4.2 POM-Regeln

Root-POM mit `packaging=pom`, zehn expliziten Kindern, `guru.interlis` als Group, Release 25 und zentralem DependencyManagement. Kinder besitzen normale Main-/Testpfade und ausdrückliche Abhängigkeiten. Kein zusätzlicher Build über Maven-Surefire-/ANTLR-/Exec-Pluginblöcke: solche Blöcke wären keine verlässliche Jenesis-Konfiguration.

Der Versionswert aus `gradle.properties` bleibt für Auftrag A der Produktreferenzwert. POM-/Jenesiswerte müssen vor dem Build damit übereinstimmen. Eine temporäre deklarative Doppelung ist nur mit automatisch geprüftem Driftfehler zulässig. Keine automatische Änderung der Gradle-Produktversion beim Jenesislauf.

Im gelesenen Stand: ANTLR 4.13.2, JUnit-BOM 5.11.4, Gson 2.14.0, LSP4J 0.23.1, Java 25; der konkrete JDK-Patch und alle transitiven Artefakte kommen aus der tatsächlichen A0-Baseline. Nicht durch eine neuere Resolverwahl aktualisieren. Parent-/BOM-POMs und heruntergeladene Tools gehören ebenfalls zum Pin-/Herkunftsinventar.

Unterscheide Maven-Scopes von Gradles `api`/`implementation`: diese Konzepte sind nicht vollständig identisch. Für die Kompilierung benötigte Libraries nicht nur deshalb in `runtime` deklarieren, um eine API-Eigenschaft zu imitieren. Vergleiche die **effektiven** Compile-, Test- und Runtime-Closures pro Modul und prüfe die generierten Verbraucher-POMs getrennt. Insbesondere darf der ANTLR-Generator nicht in die Produktruntime gelangen; Tests dürfen Produktauflösung nicht versehentlich verändern.

Konfliktauflösung muss dem bisherigen tatsächlich verwendeten Satz entsprechen. Eine andere transitive Version wird nicht durch „Maven ist anders“ legitimiert. Setze gezielte Management-/Exclusion-Regeln und belege sie. Kein pauschales Ausschliessen aller Transitives, kein `systemPath`, kein Verweis auf Gradlecache-JARs.

**Präzise Paritätsgrenze bei Scopes:** Die effektive Produktruntime bleibt in Koordinaten, Versionen und Fremdartefaktbytes gleich. POMs können Gradles getrennte API-/Implementation-Weitergabe nicht in jedem Detail darstellen. Zusätzliche Compile-Sichtbarkeit einer bereits identischen Produktruntime-Bibliothek darf deshalb als konkrete deklarative Abbildungsdifferenz dokumentiert werden, sofern weder neue Quellabhängigkeiten entstehen noch Produkt-API, Runtime, Toolisolation oder bestehende Verbraucher verletzt werden. Kein pauschales „alle Compile-Differenzen erlaubt“. Nicht mittels `optional` oder Exclusion eine tatsächlich benötigte transitive Runtimeabhängigkeit aus dem Verbraucher-POM entfernen. Jenesis-spezifische Console-Runnerbibliotheken werden als Testtooling separat bilanziert, nicht als verlorene oder zusätzliche Produktdependency.

### 4.3 Pinning und lokales Staging

Nach erprobter Konfiguration vollständige Jenesis-Pins aufnehmen und dann streng prüfen (`jenesis.dependency.pin=strict`, sofern im gepinnten Stand bestätigt). Der Pinvorgang ist eine kontrollierte Änderung, kein gewöhnlicher Buildschritt. Er darf nicht unbemerkt neueste Testtools oder Generatoren einführen. Auch Console-Launcher-/Buildtool-Dependencies explizit festlegen.

`interlis-verification` wird mit der dokumentierten `stage=false`-Regel beziehungsweise der entsprechenden bestätigten Konfiguration aus öffentlichen Stageinventaren ausgeschlossen. Tests und Buildadapter ebenfalls. Lokales Staging zum Lesen/Prüfen der generierten POMs ist erlaubt, Export/Publishing nicht. Die Wasm-Builds konsumieren dieses Staging in Auftrag A noch nicht.

## 5. Kompilierung, Generatoren und Ressourcen

Bewahre `--release 25`, UTF-8, effektive Debug-/Lint-/Parameteroptionen und das Fehlen von Preview. Feste Optionen aus `gradle/java-conventions.gradle` in die passende Jenesis-Konfiguration übersetzen. Properties-Escaping und First-match-Konfigurationssuche berücksichtigen. Eine modulspezifische Datei darf allgemeine Optionen nicht unbemerkt verdrängen.

ANTLR wird ausschliesslich durch die offizielle Jenesis-Integration oder eine kleine unterstützte Generatoranbindung mit dem identischen offiziellen Tool erzeugt. Generator und Runtime beide 4.13.2, Tool separat gepinnt. Eingabe bleibt `interlis-compiler/src/main/antlr`; Package `guru.interlis.compiler.internal.antlr`, Visitor an, Listener aus.

Eine illustrative, in A0 am Release zu bestätigende Konfiguration im Compiler-POM-Modul:

```properties
package=guru.interlis.compiler.internal.antlr
arguments=-visitor -no-listener
@grammars/antlr=src/main/antlr
```

Die Bindingbasis ist nicht nach Gefühl relativ zur Propertiesdatei zu wählen. Anhand der tatsächlichen Pluginregel und generierten Pfade prüfen. Lexer und Parser müssen dieselbe frische Tokenvokabularbasis sehen. Nach Lexer-/Parseränderung, nach Löschung einer Grammatik und nach sauberem Neubau dürfen keine veralteten Tokens oder Klassen weiterwirken. Generierte Dateien bleiben unter Jenesis-Ausgaben und nicht im Sourcebaum.

Kopiere normale Ressourcen exakt; schliesse keine zusätzlichen Builddateien in JARs ein. Der vorhandene Unicode-Lizenztext muss im Compiler unter `META-INF/licenses/` erhalten bleiben. ServiceLoader-Dateien, Schemas und dokumentierte Ressourcennamen prüfen. Kein pauschaler Ressourcenroot `.`. Sources-JARs müssen ihre bisherigen relevanten Quellen abdecken; generierte Quellen werden nach tatsächlicher Baseline bewertet, nicht geraten. Es wird keine neue Javadoc-Veröffentlichungspflicht eingeführt.

## 6. Testausführung und externe Inputs

### 6.1 Normale Tests

Alle vorhandenen normalen JVM-Testmethoden und Parameterinvocations der zehn Module laufen mit dem gepinnten JUnit-Stack auf dem Classpath. Die bestehenden Packages bleiben unverändert. `compiler-contract` ist aus dem normalen Lauf ausgeschlossen, nicht die ganze Klasse: einige Contractklassen enthalten auch normale Negativtests.

Als dokumentierter Jenesis-Ausgangspunkt ist `jenesis.test.tag=-compiler-contract` zu prüfen. Methodenselektion muss modulspezifisch sein; ein globaler Filter darf nicht unbeteiligte Module in einen falschen „keine Tests“-Fehler treiben oder eine erwartete Gruppe leer überspringen. Eine enge Entwicklerauswahl ist kein vollständiger Paritätsnachweis.

Jenesis-Tests laufen nicht zwingend im Modulverzeichnis und können gegen gepackte Test-JARs laufen. `repo.root`, `testdata.dir`, Encoding, Testressourcen und ggf. weitere bestehende Systemproperties werden an die **tatsächliche Test-JVM** übergeben. Ein `-Drepo.root` nur am Elternprozess ist kein Nachweis. Für bestehende Tests, die bewusst Dateipfade aus Testressourcen benötigen, darf die bestätigte Klassenverzeichnis-Ausführung verwendet werden, statt Testsemantik umzuschreiben.

### 6.2 Externe Inputinvalidierung

Erhalte insbesondere die gerade ergänzten Fixturebindungen: aktuelle Sourcebindings, Completionlinks, G2-/Normentscheidungsdateien, Seed-/Vollkorpusarchive, A3-Modelle, Node-Fingerprinthelfer und Unicode-Daten. Die genaue Matrix ist modulbezogen aus dem aktuellen Gradle-Build und den tatsächlichen Lesestellen zu übernehmen.

Nur `repo.root` zu setzen macht diese Dateien noch nicht zu Jenesis-Cacheinputs. `JvmBuildInputs` liefert deshalb einen deterministischen, modulspezifischen Inhaltsfingerprint der zusätzlich gelesenen Dateien. Dieser muss über bestätigte Engineinputs oder eine aufgelöste Testkonfiguration nachweislich den tatsächlichen Testschritt invalidieren. Keine Referenz auf einen wechselnden Dateipfad bei unverändertem Inhaltscachekey. Fehlende Dateien, Pfadtraversal und Symlinkausbrüche sind Fehler.

Normale Unit-Cachekeys enthalten keine zufällige Run-ID, aktuelle Uhrzeit oder unnötige HEAD-Abhängigkeit. Eine Kommentar-/Fixtureänderung muss betroffene Prüfer neu ausführen, aber nicht zwingend alle Produktmodule. Eine reale Drei-Schritt-Probe belegt: erster Lauf ausgeführt → unveränderter Lauf wiederverwendet → ausschliesslich relevante Fixturebytes geändert, betroffener Test wirklich neu ausgeführt.

Keine neue automatische statische Testauswahl als Abnahmewahrheit einführen. Eng selektierte Tests sind eine begründete Aufrufentscheidung; die Risiken indirekter Zugriffe und Reflection bleiben zu berücksichtigen.

Auch **Änderungen der eigenen Adapter-/Hooklogik** müssen ihren betroffenen gecachten Schritt invalidieren. Falls eine Engine-API nur deklarierte Eingaben und serialisierten Schrittzustand in den Schlüssel aufnimmt, gehört ein überprüfter Implementierungsdigest bzw. ein entsprechender echter Quellinput in diesen Zustand. Ein unveränderter serialisierter Zustand bei geänderter Java-Schrittlogik darf keinen alten Erfolg legitimieren. Eine begrenzte Scratchprobe soll eine passende Logikänderung bei sonst gleichen Eingaben erkennen; kein globales Cacheabschalten als dauerhafte Lösung.

### 6.3 Explizite aktuelle Compiler-Contracts

`contracts` übernimmt logisch den bestehenden `compilerContractCheck`, nicht die gesamte P3-Freigabe:

1. Aktuelle Bindungen aus Original und aktuellem Overlay laden und validieren.
2. Benötigte Norminputs mit bestehenden Pins vorbereiten; keine Neuinterpretation/Neuerzeugung der Normextraktion.
3. Pro Produzentenmodul exakt die verlangten Methoden und Parameterfälle ausführen.
4. Echte Rohreports sichern und an Auswahl, Quell-/Input-/Artefaktstand, Run-ID und Toolchain binden.
5. Erst danach die `compiler-contract`-Tests des Verifikationsmoduls ausführen.

Pro Contractaufruf frische Run-ID; Produzenten und Consumer dürfen keine alten erfolgreichen Ausgaben übernehmen. Kompilierungen dürfen bei identischen Inputs gecacht bleiben. Grosse normale Testmengen müssen nicht erneut laufen, nur weil ausgewählte Assertions frisch gebraucht werden.

Es gibt keinen erfolgreichen Contract ohne reale Produzentenläufe. Ein ausgewählter Methodenname ohne Parameterinvocations, ein leerer Consumer, fehlende XMLs oder ein Report eines anderen Runs müssen scheitern. Die Abdeckungs-/Normentscheidungsassertions bleiben inhaltlich erhalten; ausschliesslich Layout-/Reportformatanbindung darf angepasst werden.

### 6.4 Native Reports und Vergleichsformat

Aktiviere echte Jenesis-/JUnit-Reports ausdrücklich. Gehe nicht davon aus, dass deren XML-Dateinamen und Parameternamen Gradle entsprechen. Sichere native Reports unverändert.

Ein kleiner Adapter darf daraus ein neutrales Testinventar und bei Bedarf das bereits konsumierte **klassengruppierte Vergleichsformat** bilden. Jede abgeleitete Zeile ist auf Originalreport, Hash und eindeutige Testidentität zurückzuführen. Zusammenführen oder Gruppieren darf keine Fehler, Skips, Duplikate, Parameterfälle oder ungewöhnliche Namen verwerfen.

Wenn native Reports eine benötigte Methoden-/Invocationidentität nicht liefern, darf eine kleine testseitige JUnit-Platform-Listeneranbindung diese **während des echten Laufs** erfassen. Kein Ableiten ausgeführter Fälle nur aus erwarteten Quellen. Keine Implementierung eines eigenen Testframeworks und kein Ersetzen der normalen Jenesis-JUnit-Ausführung.

Nicht alle Reportzeiten sind ZIP-reproduzierbar. Für Frischeprüfungen präzise Runzuordnung verwenden; Zeitauflösung des Runners korrekt berücksichtigen, nicht `Instant.EPOCH` als allgemeine Abkürzung wählen.

## 7. Klassen- und Methodenverträge

Die folgenden Typen sind **Designvorgaben für eigene kleine Adapter**. Die Signaturen sind keine behauptete Jenesis-API. Offizielle API-Aufrufe werden in A0 gegen das gepinnte Release kompiliert und nachgewiesen. Kleine Typen dürfen sinnvoll in einer Datei als Records/nested classes liegen; nicht für jede Zeile ein neues Framework-Modul schaffen.

### 7.1 `build.InterlisJvmBuild`

Datei `build/InterlisJvmBuild.java`; ausschliesslich Buildkoordination.

```java
public static void main(String[] args) throws Exception;
static BuildRequest parse(String[] args);
static int execute(BuildRequest request) throws Exception;
static BuildOutcome buildJvm(BuildContext context) throws Exception;
static ContractOutcome verifyContracts(BuildContext context) throws Exception;
static void verifyExistingDistribution(BuildContext context, BuildOutcome built) throws Exception;
static void verifyEditorConsumer(BuildContext context, EditorCheckRequest request) throws Exception;
```

`parse`: nur bekannte Profile/Module; für `editor-check` genau die expliziten Consumer-/Referenzbundleparameter plus dokumentierte optionale Testumgebung akzeptieren; Pfade als einzelne Argumente und vor der Verwendung normalisieren/prüfen; ungültige Auswahl Exit != 0. `execute`: Scope-/Version-/Clean-Sicherheitscheck, unveränderte Engine aufrufen, Fehler propagieren, notwendige Adaptersequenz. Kein Gradle-/Maven-Aufruf. `buildJvm`: nur zehn JVM-Projekte, gewöhnliche Tests und vollständiges Artefaktinventar. `verifyContracts`: Produzenten vor Consumer, frische Identität, keine Rekursion durch einen erneuten vollständigen Build. `verifyExistingDistribution`: nur vorhandene Form und passende reale Prozesse. `verifyEditorConsumer`: ruft den eng begrenzten Node-Verbrauchercheck aus `EDITOR_COMPATIBILITY.md` auf, prüft dessen tatsächlichen Exit/Umfang/Artefaktbindung und propagiert Fehler. Kein npm-/VS-Code-Aufruf aus `buildJvm` und keine zweite Extension-Launchimplementierung in Java.

### 7.2 `build.interlis.JenesisBridge`

Eine einzige Stelle kapselt versionsabhängige Engineaufrufe. Bevorzugt die dokumentierten `Make`-/Ergebnisinventarschnittstellen; keine Suche nach „dem neuesten JAR“ im Targetbaum.

```java
BuildOutcome run(BuildContext context, BuildSelection selection) throws Exception;
Map<String, ModuleArtifacts> readInventory(BuildOutcome outcome) throws Exception;
List<Path> runtimeClasspath(String module, BuildOutcome outcome) throws Exception;
List<Path> testClasspath(String module, BuildOutcome outcome) throws Exception;
void requireExactModuleSet(Set<String> actual);
void requireNoForeignBuildInputs(BuildOutcome outcome) throws Exception;
```

`ModuleArtifacts`: Koordinaten, Main-JAR, Sources-JAR falls vorgesehen, Testausgabe, native Reports und die tatsächlichen scopegetrennten Dependencylisten mit Hashes. Interne Verifikationsartefakte bleiben verfügbar, auch wenn nicht öffentlich staged. Fehlende/doppelte Koordinaten sind Fehler. Pfade unter Gradle-Ausgabeorten, fremde eigene SNAPSHOTs oder Quellen aus Wasm-Modulen sind abzulehnen. Keine private Reflection in der Engine, keine Kopie eines Resolvers.

### 7.3 `build.interlis.JvmBuildInputs`

```java
ProjectBaseline inspectProject(Path root) throws Exception;
void requireVersionAgreement(ProjectBaseline baseline) throws Exception;
InputSnapshot snapshot(Path root, String module, TestProfile profile) throws Exception;
String fingerprint(InputSnapshot snapshot);
Map<String, String> testJvmSettings(BuildContext context, InputSnapshot snapshot);
void requireInputsUnchanged(InputSnapshot before, InputSnapshot after);
```

Snapshots enthalten logisch relative Pfade, SHA-256 und eine versionierte Auswahlpolicy; sortiert und ohne aktuelle Uhrzeit. Auch gelesene POM-/Properties-/Hookquellen und Toolpins berücksichtigen. `testJvmSettings` liefert Root-/Testdata-/Layout-/Inputwerte; deren tatsächliche Übergabe wird geprüft. Geheimnisse sind weder Snapshotdaten noch Systempropertywerte. Producer-/Consumer-Bindungen, Modulversionen und Dateiinhalte werden getrennt von umgebungsabhängigen absoluten Pfaden aufgezeichnet.

### 7.4 `build.interlis.ExistingJvmDistribution`

```java
DistributionOutcome assemble(BuildContext context, Map<String, ModuleArtifacts> modules) throws Exception;
void writeLaunchers(DistributionLayout output, LauncherTemplates templates) throws Exception;
void verifyRuntimeClosure(DistributionOutcome distribution) throws Exception;
void writeSdkArchive(DistributionOutcome distribution, Path archive) throws Exception;
```

Nur vorhandene CLI/LSP-`bin`/`lib`-Struktur und bestehendes SDK-ZIP. Unveränderte Dependency-JARs, keine Shading-/Fat-JAR-Lösung. Dateinamen der eigenen JARs erhalten die bisherigen automatischen Modulnamen. Launcher dürfen als **versionierte Texttemplates aus der Baseline** übernommen und nachvollziehbar parametrisiert werden; sie werden nicht bei jedem Jenesislauf aus Gradle generiert. Rechte, LF/CRLF-Regeln, Unicode-/Argumentweitergabe, Arbeitsverzeichnis und Exitcodes beibehalten.

`writeSdkArchive`: vorhandenes Archivlayout, versionierte Beispiel-/Manifestdateien, deterministische Reihenfolge und erforderliche Unix-Startrechte erhalten. Standard-ZIP-Ausgabe ohne diese Rechte ist kein gleichwertiger Ersatz. Nutze eine vorhandene geeignete Engine-/Bibliotheksfunktion; falls eine kleine Build-only-ZIP-Library erforderlich ist, ausdrücklich begründen/pinnen und strikt von Produkten trennen. Kein selbst erfundener ZIP-Patcher.

### 7.5 `guru.interlis.verification.JvmBuildParity`

Interner Prüfer unter `interlis-verification/src/main/java`; verwendet die vorhandene Gson-Abhängigkeit und JDK-Funktionen, keine neue Produktdependency.

```java
public static void main(String[] args) throws Exception;
ParityResult compare(BuildInventory baseline, BuildInventory candidate) throws Exception;
void compareDependencies(ModuleInventory before, ModuleInventory after);
void compareArtifacts(ModuleInventory before, ModuleInventory after) throws Exception;
void compareExecutedTests(TestInventory before, TestInventory after);
void requireScopeAndProvenance(BuildInventory inventory);
```

Nicht gleichnamige Dateipfade, sondern logische Modul-/Scope-/Testidentitäten vergleichen. Erwartete Testmenge kommt aus einer echten Gradle-Baseline, nicht aus Codex' handgeschriebener Sollzahl. Eigene neue Adaptertests werden getrennt ausgewiesen und dürfen verlorene Produkttests zahlenmässig nicht verdecken. Jede nicht erlaubte Differenz führt zu einem strukturierten FAIL mit Ursache. Die eng begrenzte POM-/Gradle-Compile-Abbildungsregel aus Abschnitt 4.2 wird ausdrücklich ausgewiesen und nicht als allgemeine Ignoreliste implementiert.

### 7.6 `guru.interlis.verification.TestReportInventory`

```java
TestInventory readGradle(Path rawDirectory, ExecutionReceipt receipt) throws Exception;
TestInventory readJenesis(Path rawDirectory, ExecutionReceipt receipt) throws Exception;
void requireCompleteSelection(TestInventory actual, TestSelection expected);
void requireSuccessful(TestInventory actual);
CanonicalReportSet projectPerSuite(TestInventory actual, Path target) throws Exception;
void verifyRawBindings(CanonicalReportSet derived) throws Exception;
```

XML-Parsing ohne DTD/externe Entities. Leere Pflichtreports, Duplikate, widersprüchliche Summen, fehlende Methoden-/Parameteridentität, Fehler/Abbrüche/Skips werden erkannt. `projectPerSuite` ist nur eine nachvollziehbare Formatprojektion tatsächlich ausgeführter Fälle, kein Reportgenerator aus Erwartungen. Der Native-Report bleibt erhalten; Mappingpolicy und Hashes sind Bestandteil des abgeleiteten Nachweises.

### 7.7 Kleine Datentypen

Mindestens folgende logischen Werte werden benötigt; Records dürfen nested sein:

- `BuildRequest`: Profil, explizite Modulauswahl, Filter, absoluter Root, gegebenenfalls Baselinebelegpfad und ausschliesslich bei `editor-check` ein `EditorCheckRequest`.
- `BuildContext`: Root, eigener Targetroot, gepinnte Tools, Sourcezustand, Auswahl und **nur für frische Contractläufe** Run-ID.
- `BuildInventory`: Builder, Module, Toolchain, relevante Source-/Inputfingerprints, Artefakte, Tests, Umfang.
- `TestIdentity`: Modul, Klasse, Methodensignatur, stabile Invocationkennung und ursprünglicher Anzeigename.
- `ExecutionReceipt`: Originalprozess, Argumentliste, Arbeitsverzeichnis, Start/Ende, Exit/Signal, tatsächliche Reports, Status EXECUTED/REUSED/NOT_RUN.
- `ParityResult`: PASS/FAIL/BLOCKED, Kriterien, Differenzen, Originalbelegreferenzen.

`EditorCheckRequest` enthält ausschliesslich expliziten Extension-Quellpfad/Commit, überprüftes Referenzbundle, neues Serverinventar, isolierten Ausgabepfad und vorhandene Runtime-/Editorinformationen. Die Details stehen in `EDITOR_COMPATIBILITY.md`.

Immutable Daten bevorzugen. Kein globaler „aktueller Build“ und kein Prozesszustand, der nebenläufige Läufe vermischt.

### 7.8 Bestehende Verifikationsklassen an zwei Layouts anbinden

Erweitere die bestehende `VerificationLayout` gezielt, statt überall `build/` durch `target/` zu ersetzen:

```java
static VerificationLayout forInvocation(Path root);
static VerificationLayout read(Path root, Path explicitLayoutFile) throws Exception;
Path compilerDistribution();
Path compilerLauncher(boolean windows);
Path lspDistribution();
Path sdkArchive(String version);
Path junitDirectory(String module, String profile);
Path contractAssertionManifest(String module);
Path normativePdf(String languageVersion);
Path rootReport(String relativeName) throws IOException;
```

Ohne ausdrückliche Layoutdatei bleiben **alle Gradle-Defaults** erhalten. Für Jenesis wird eine buildbezogene, gehashte Layoutdatei aus dem tatsächlichen Ergebnisinventar erzeugt und den Host-/Test-JVMs übergeben. Kein Fallback von einem fehlenden Jenesispfad auf Gradle. Pfadgrenzen, Symlinkschutz, Modul-/Profilnamenvalidierung und bestehende Negativtests bleiben.

Falls Testhelfer im Unterpackage auf den Typ zugreifen müssen, darf diese **interne Verifikations-API** gezielt öffentlich werden; niemals dafür Produktinternas öffnen. `HostChecks.cli(...)` und `artifacts(...)` nutzen das Layout. Andere direkte Lese-/Schreibstellen werden inventarisiert und angepasst, soweit sie für Auftrag A aktiv gebraucht werden.

`CompilerCoverageContractTest.validateExecutedAssertions(...)` erhält Produzentenpfad und Manifest über das Layout. `validateRunIdentity(...)`, `JUnitReportAssertions.fresh(...)` und Sourcebindings bleiben streng. `NormativeQuoteAssertions` bezieht den vorbereiteten PDF-Pfad aus demselben Layout. Der Standard-Gradlepfad muss weiterhin ohne Zusatzoption funktionieren.

`ProfileFingerprint` und sein Node-Pendant werden um relevante neue POM-/Jenesis-/Buildadaptereingaben konsistent ergänzt. Gelieferte neue Enginequellen können über einen vollständig überprüften Treehash eingebunden werden; eine blosse frei behauptete Versionsdatei genügt nicht. Generierte Targets/Caches werden nicht zu Sourceeingaben. Bei einer Erweiterung der Auswahllogik Parität beider Implementierungen gezielt nachweisen.

Es wird kein allgemeines pluginfähiges Reportframework gebaut. `PlatformProcesses` und der komplette Node-P3-Validator bleiben im bestehenden Gradle-Releaseprofil; ihre komplette Übernahme nach Jenesis gehört nicht zu A. Notwendige Layoutparameter für kleine JVM-Integration sind davon zu unterscheiden.

### 7.9 Contractkoordination als kleiner Adapter

Ein `build.interlis.CompilerContractRun` oder ein gleichwertiger kleiner Teil des Einstiegs kapselt:

```java
ContractSelection loadCurrentSelection(Path root) throws Exception;
ProducerResults runSelectedAssertions(BuildContext context, ContractSelection selection) throws Exception;
void writeBoundProducerManifests(BuildContext context, ProducerResults results) throws Exception;
ContractOutcome runConsumers(BuildContext context, ProducerResults results) throws Exception;
```

Keine zweite frei abweichende Auswahlsemantik neben Original/Overlay einführen. Entweder eine kleine gemeinsame rein lesende Auswahlhilfe wiederverwenden oder identische Entscheidungen durch einen gezielten Paritätsvertrag prüfen. Norminputs dürfen aus verifiziertem Cache kommen. Frische Produzenten dürfen nicht durch kopierte Gradle-XMLs ersetzt werden.

Konkurrierende normale und Contractläufe dürfen weder Reports überschreiben noch denselben schreibenden Enginetargetzustand unkontrolliert teilen. Unveränderliche Runbelegkopien unmittelbar nach dem jeweiligen Produzenten sichern; selektierte Engineaufrufe bei gemeinsamem Target nötigenfalls serialisieren. Alte/nicht ausgewählte Reports werden nicht in das neue Inventar hineinglobbt.

## 8. Bestehende CLI-/LSP- und Editorverträge

### 8.1 JVM-Distributionen

Die Produktlauncher und die neue Prüfspur werden an den bisherigen Fällen gemessen, insbesondere Exitcodes, stdout/stderr-Trennung, Unicodepfade/Argumente, Shellsonderzeichen, relativer Arbeitsort, Farbausgabe und SDK-Beispiel gegen gebaute JARs. Der bisherige CLI-Start über automatische Module bleibt bestehen, bis seine Änderung gesondert beauftragt und geprüft ist; keine Änderung des bisher aus JAR-Dateinamen abgeleiteten Modulnamens.

LSP-Smoke: echter neuer Prozess, gültiges `Content-Length`-Framing, initialize → initialized → shutdown → exit, erwartete Antworten, Timeout-/Exitbehandlung, keine Startmeldung auf stdout. Keine vollständige neue LSP-Funktionsabnahme. Der getrennte Editor-Kompatibilitätscheck ergänzt diesen Prozesssmoke, ersetzt aber weder eine Gesamt-GUI-Abnahme noch eine neue Releasefreigabe.

Der neue SDK-/CLI-Consumer läuft ausschliesslich gegen Jenesis-erzeugte JARs und die passende vollständige Runtimeclosure. Keine Gradle-Klassen auf dem Testprozessclasspath. Ein unveränderter Scripttext allein beweist keine funktionierende Distribution.

### 8.2 Extension-Vertrag erhalten, nicht mitmigrieren

Der bestehende Verbraucher `edigonzales/interlis-vscode` ruft in `scripts/stage-core.cjs` unmittelbar `gradlew editorBundle --no-daemon` auf und liest `build/editor-bundle`. `tools/editor/bundle.cjs` im Core nimmt heute Server-JARs aus `interlis-lsp/build/install/interlis-lsp/lib`. Diese Standardwege und ihre Bedeutung bleiben erhalten.

Der Extension-Start ist davon getrennt: `resolveLaunchPlan(...)` aus `src/host/launch.ts` prüft das vollständige Serverinventar samt SHA-256 und startet `guru.interlis.lsp.Main` mit `java -cp <server/lib/*>`. **Diesen LSP-Classpath-Start nicht mit dem automatischen Module-Path-Launcher der Compiler-CLI verwechseln.** Beide bleiben in ihrem jeweiligen Verbraucher gültig.

`EDITOR_COMPATIBILITY.md` definiert zwei erforderliche, getrennte Nachweise:

- Der bisherige Gradle-/stage:core-/F5-Vorbereitungsweg bleibt benutzbar; ein echter kleiner Vorbereitungs-/Staginglauf belegt mehr als nur ein Dry-run.
- Ein isoliertes Schattenbundle mit ausschliesslich neuen Jenesis-Server-JARs wird vom bestehenden unveränderten Extension-Code geprüft und für einen kleinen echten Extension-Host-Lauf verwendet. Ein Test mit weiterhin Gradle-erzeugten Server-JARs erfüllt diese zweite Aussage nicht.

Normale Referenz-Frontend-/Rendererartefakte dürfen für diesen Verbrauchercheck aus einem separat ausgewiesenen bisherigen Build desselben relevanten Quellstands stammen. Nur die neuen Server-JARs werden aus Jenesis genommen. Herkunft und Integrität beider Mengen müssen getrennt erfasst werden; es ist bewusst **kein vollständiger Jenesis-Frontendbuild**.

Die Extension ist Testverbraucher, nicht zweites Schreibprojekt. Ihr ausgecheckter Benutzerstand, Release-Locks, Runtimepins, .vscode-Konfiguration und persönliche Installation bleiben unangetastet. Nur eine isolierte Testkopie darf ein neu aus tatsächlichen Kandidatenbytes erzeugtes Testlock erhalten; der alte freigegebene Core-/Extension-Paarstatus wird nicht auf den Kandidaten umgebunden. Fehlende reale Verbraucherprüfung ist BLOCKED für JA-35, kein fingierter Kompatibilitäts-PASS.

### 8.3 Compiler als späteres APE-Ziel

Das neue `BuildInventory` muss die normale Compiler-CLI-Hauptklasse, ihre eigene JAR, die vollständige geordnete Runtimeclosure, Ressourcen-/Lizenzinventare, Versionen und Dateihashes enthalten. Das ist schon für JVM-Parität erforderlich und genügt als Übergabepunkt für einen späteren nativen Versuch. Keine Extension-Runtime, LSP4J-, Verification-, JUnit- oder ANTLR-Generatorartefakte in eine angebliche Compilerclosure aufnehmen; nur tatsächliche Runtimeabhängigkeiten der Compiler-CLI.

A erzeugt weder APE noch Native-Image-Konfigurationen und beurteilt nicht die CosmoGVM-Tauglichkeit. Die vorhandene JVM-CLI bleibt funktionsfähige Referenz und Alternative. Der spätere APE-Versuch darf unabhängig von JPMS und vom LSP beauftragt werden.

## 9. Vergleich, Wiederholbarkeit und Berichtgrenzen

### 9.1 Gradle gegen Jenesis

Vergleiche pro Modul:

- Koordinaten/Version und effektive Main-Compile-/Runtime-/Testclosures mit Bytes und Herkunft;
- Klasseninventar und generierte Klassen, normale Ressourcen, Lizenzen und Sources-JAR-Inhalt;
- öffentliche API und relevante package-private Testzugriffe;
- tatsächlich ausgeführte Testidentitäten, Outcomes und Parameterfälle;
- SDK-/CLI-/LSP-Beobachtungen und Distributionseinträge.

ZIP-Containerreihenfolge/-Zeitstempel sowie konkret bekannte Builder-Metadaten dürfen als solche erklärt werden. **Keine pauschale Ausblendung von Manifest, META-INF oder Debugdaten.** Manifestwerte können beobachtbares Verhalten beeinflussen. Abweichende Klassenbytes erfordern Ursache und gezielte Prüfung; „gleiche API“ allein beweist nicht gleiche Funktion. Keine Binärnormalisierung der ausgelieferten JARs.

### 9.2 Zwei Jenesis-Builds

Die bisherige JVM-Reproduzierbarkeitsanforderung bleibt: unter demselben definierten JDK-/Plattformprofil zwei unabhängige saubere Jenesis-Erzeugungen der lieferbaren JVM-Artefakte vergleichen. Für die zweite Erzeugung keine Wiederverwendung generierter Klassen/JARs/Buildcacheoutputs, normale offizielle Dependencycaches sind erlaubt. Die Testreports selbst dürfen Runzeiten unterscheiden; deren Bytes sind keine Produktartefakte.

Führe nicht deshalb eine zweite Volltestsuite aus, wenn nur die reproduzierbare JAR-/Distributionsherstellung zu belegen ist. Ausgewiesener reiner Erzeugungslauf plus Artefaktvergleich und vorhandene Tests derselben gelieferten Bytes genügt. Die Sicherheitsbindung darf dabei nicht vorgetäuscht werden.

Die Jenesis-Diagnoseoption `project.reproduce` ist nach aktueller Dokumentation **kein automatisch fehlschlagendes Gate bei Differenzen**. Ein dokumentierter Exit 0 allein reicht deshalb nicht; der Projektvergleich muss eine unerlaubte Produktdifferenz selbst mit Exit != 0 ablehnen. Diese konkrete Eigenschaft am gepinnten Release bestätigen.

### 9.3 Quellen-/Runbindung und historische Belege

Ein Bericht behält den tatsächlichen SHA/Tree/Dirtyzustand seiner Ausführung. Vorcommit-Ergebnisse werden nicht auf den späteren Commit umetikettiert. Wiederverwendung wird durch identische relevante Inputs/Artefakte begründet und separat dokumentiert.

Historische `docs/evidence`-/G2-Belege bleiben unverändert. Aktuelle Overlays dürfen ausschliesslich nachvollziehbare neue Pfad-/Buildbindungen aufnehmen; Produktfälle, Erwartungen und alte Normzitate nicht verändern. Ein lokaler Jenesis-PASS ist keine P3-Freigabe und kein Nachweis einer Wasm-/Browsermigration.

## 10. Dokumentation und Kompatibilität

Die aktuelle README beschreibt beide Einstiege und deren **verschiedenen** Umfang. `AGENTS.md` und aktive Buildverträge erhalten eine eng gefasste Auftrag-A-Ausnahme, keine Behauptung einer bereits abgeschlossenen Totalmigration. Versioniertes `build/` ist nun teilweise Quelle: das muss für Clean, Ignore, Fingerprint und Scanner explizit dokumentiert sein.

Erhalte alle bestehenden Gradle-Rootkommandos und ihre inhaltlichen Pflichten. Ein in der Rootdatei erforderlicher Clean-Fix darf die gewachsenen `mustRunAfter`-/`dependsOn`-Kanten nicht verändern. Produktartefakte dürfen weder Verifikations-JARs noch Engine-/Adapter-/Generator-/Testklassen enthalten. Gson im LSP bleibt eine erlaubte echte Produktdependency; nicht jede im Tooling vorkommende Library ist pauschal verboten.

## 11. Abnahmekriterien

| ID | Verbindliche Beobachtung |
|---|---|
| JA-01 | Genau zehn JVM-Projekte erfasst; keine aktive Wasm-/Historical-POM-/Frontend-Discovery. |
| JA-02 | Engine unverändert und fest gepinnt; erforderliche Fähigkeiten im Release tatsächlich erprobt. |
| JA-03 | Produkt-/JDK-/Dependencyversionen erhalten; keine unbemerkten Tools mit beweglicher Version. |
| JA-04 | POMs deklarativ, Scopes/Closures nachgewiesen; kein Maven-/Gradle-Aufruf in der neuen JVM-Spur. |
| JA-05 | Keine JPMS-/Testpackage-/Sichtbarkeits-/Compilersemantikmigration. |
| JA-06 | Gradle bleibt funktionsfähig; bestehende Wasm- und Gesamtbuildaufgaben werden nicht umgestellt. |
| JA-07 | Clean-/Ignore-Sicherheit für versionierte Buildquellen und getrennte Outputwurzeln nachgewiesen. |
| JA-08 | ANTLR identisch gepinnt/isoliert; sauberer und inkrementeller Generatorlauf ohne Altreste. |
| JA-09 | Effektive Compileroptionen, Ressourcen, Lizenzen und Sources-JARs entsprechen dem vereinbarten Bestand. |
| JA-10 | Alle normalen JVM-Testidentitäten der Baseline unter Jenesis erfolgreich; Zusatztests maskieren keinen Verlust. |
| JA-11 | Normale Tests schliessen nur Contractmethoden aus; White-Box-Zugriffe unverändert funktionsfähig. |
| JA-12 | Root-/Testdata-/Environmentkonfiguration erreicht tatsächlich die Testprozesse. |
| JA-13 | Externe Fixture-/Quellinputs invalidieren betroffene Tests; unveränderter Lauf kann korrekt wiederverwendet werden. |
| JA-14 | Contractauswahl stimmt mit aktuellen Bindungen überein; echte frische Produzenten vor Consumer. |
| JA-15 | Native Reports bleiben erhalten; Formatprojektionen erhalten Identität, Parameterfälle, Fehler und Herkunft. |
| JA-16 | Fehlende/leere/fremde/manipulierte/stale Reports werden abgelehnt; kein Gradle-Fallback. |
| JA-17 | Layoutadapter hat unveränderte Gradle-Defaults und strikt isolierte Jenesispfade. |
| JA-18 | CLI-/LSP-/SDK-Form und Prozessverträge gegen tatsächlich Jenesis-erzeugte Artefakte bestätigt. |
| JA-19 | Verifikation, Buildengine, ANTLR-Tool und Testframework gelangen nicht in Produktlieferungen. |
| JA-20 | Abhängigkeiten, JAR-/Ressourcen-/API-Inventare und generierte Verbraucher-POMs nachvollziehbar verglichen. |
| JA-21 | Unabhängige Jenesis-Erzeugungen reproduzieren die vorgesehenen JVM-Lieferartefakte; Differenzen schlagen fehl. |
| JA-22 | Source-/Tool-/Input-/Artefaktbindung vollständig, historische Nachweise unverändert, kein SHA-Rebranding. |
| JA-23 | Kleine echte SDK-/CLI-/LSP-Integration und passende Negativ-/Prozessfälle ausgeführt. |
| JA-24 | Alte Gradle-Kommandos und kritische Taskreihenfolgen nicht beschädigt; passende Regression geprüft. |
| JA-25 | Klasse-/Methoden-/API-Adapter klein und getestet; keine neue allgemeine Build-/Test-/Reportplattform. |
| JA-26 | Phasen A0–A5 mit notwendigen erfolgreichen Gates, ohne Review nach Einzelphasen, abgeschlossen. |
| JA-27 | Dokumentation, Testledger, Scope-/Capability-/Paritätsberichte ehrlich und konsistent. |
| JA-28 | Reviewfixes nach Impactmatrix getestet; unveränderte belastbare Belege mit Herkunft wiederverwendet. |
| JA-29 | Geprüfter Kandidat lokal committet; fremde Änderungen erhalten; kein Push/Release. |
| JA-30 | Autarker unabhängiger Gesamt-Review tatsächlich mit OpenCode 2.x / direktem Flash 1017 auf exaktem Kandidaten. |
| JA-31 | Berechtigte Findings korrigiert, gezielt geprüft, neu committet und frisch unabhängig überprüft. |
| JA-32 | Gültiges finales PASS mit eigenen Reviewer-Gegenproben; danach keine stillen Änderungen. |
| JA-33 | Bestehender Gradle-Editorbundle-/stage:core-/F5-Vorbereitungsvertrag bleibt benutzbar; echter schmaler Referenzverbraucherlauf und keine dauerhaften Extensionänderungen. |
| JA-34 | Isoliertes Schattenbundle verwendet exakt Jenesis-LSP-Runtime-JARs und nachgewiesene unveränderte Nichtserverressourcen; neue Testlocks stammen aus echten Bytes und überschreiben keine Release-Locks. |
| JA-35 | Bestehender Extension-Verbraucher und echter kleiner Extension-Host-Smoke akzeptieren diese JARs; fehlende/veränderte/zusätzliche JARs werden zurückgewiesen; Herkunft/Runtime/Scope sind belegt. |
| JA-36 | Compiler-APE ist nur getrenntes Folgeziel; keine Native-/JPMS-Pflicht eingeschmuggelt, keine LSP-APE-Migration und keine Entfernung oder Doppelimplementierung der vorhandenen Extension-JRE-/jlink-Pipeline. |

Ein fachlich notwendiger nicht ausführbarer Nachweis bleibt BLOCKED. Ausserhalb des ausdrücklichen Umfangs liegende Profile bleiben NOT_IN_SCOPE und werden weder als PASS noch als automatischer Blocker ausgegeben.
