# Spezifikation — Compiler-APE-Machbarkeit

## 1. Produktfrage und Erfolg

Der Auftrag untersucht die **bestehende INTERLIS-Compiler-CLI** als native, ohne Java-Installation nutzbare APE-Auslieferung. Er baut auf Auftrag A auf. Das angestrebte Endartefakt ist `interlis-compiler.com`, erzeugt aus x86-64- und ARM64-Code mit Cosmopolitan-Unterstützung. Die zusätzlichen Java-Bibliotheken bleiben als JARs verfügbar; der LSP bleibt JVM-basiert.

Das positive Machbarkeitsziel ist eine gemeinsame Datei für **Linux x64, Windows x64 und macOS arm64**, mit real geprüftem Compilerverhalten und sicheren Repositoryzugriffen. Linux arm64 und macOS x64 sind zusätzliche, nicht automatisch verpflichtende Ziele. Windows arm64, BSD, mobile Plattformen und Webbrowser sind nicht zugesagt. Ein optionaler Lauf auf weiteren Zielen erweitert nur den nachgewiesenen Umfang.

`GO` bedeutet: eine technisch belastbare Grundlage für einen späteren Auslieferungsauftrag, **nicht** einen Release oder einen neuen Produktionssupportvertrag. `NO_GO` darf eine erfolgreich abgeschlossene Untersuchung sein. Die genauen Urteilsbedingungen stehen in Abschnitt 11 und der Testpolicy.

## 2. Nichtziele und Erhaltungsverträge

Die bestehenden produktiven Java-APIs, Sichtbarkeiten, Packages, Versionspins und INTERLIS-Semantik bleiben unverändert. Kein JPMS-Refactoring und kein Umstellen des normalen Compilers auf CosmoGVM. Die Java25-Toolchain aus Auftrag A bleibt für Jenesis zuständig; der native Builder ist eine getrennte Werkzeuginstallation.

Keine LSP-/Extension-/VSIX-Änderungen, kein nativer LSP, keine neue jlink-/jpackage-Distribution. Keine TeaVM-/Web-Image-Migration oder neue Ausführung dieser Profile. Keine neuen Sprachmerkmale, keine Transferdatenvalidierung und keine Optimierung der Constraint-/Kommentarformatierung. Der vorhandene Befehl `format` muss sich gleich verhalten.

Keine Änderung externer historischer Evidenz, keine Neuetikettierung alter Abnahmen. Der bestehende vollständige P3-Releasecheck bleibt ungeschwächt und wird nicht mit lokalen APE-Machbarkeitsbelegen als erfüllt ausgegeben.

Ein kleinster nachgewiesener Anwendungs-Kompatibilitätsfix ist nur erlaubt, wenn er keine öffentliche/fachliche Semantik ändert, nicht über Metadaten oder unterstützte Konfiguration lösbar ist und eine echte Regression plus JVM-Gegenprobe besitzt. Müssen wichtige Produktfunktionen entfernt, Compilerinternas umgebaut oder eigene JDK-/GraalVM-Substitutionen gepflegt werden, ist das ein zu berichtender Scope-/Machbarkeitsblocker. Keine vorsorglichen Fixes.

## 3. Bestehende Integrationspunkte aus Auftrag A

Diese Angaben sind gelesener Istzustand am Recherchecommit, keine neu erfundenen vorhandenen APIs:

| Klasse | Vorhandener Einstieg | Nutzung im APE-Auftrag |
|---|---|---|
| `build.InterlisJvmBuild` | `main(String[])`, `parse(String[])`, `BuildRequest` | Ergänzt einen ausdrücklichen `ape`-Namensraum; bestehende Befehle behalten ihr Verhalten. |
| `build.interlis.JenesisBridge` | `run(BuildRequest)`, `readExistingOutcome(Path)`, `runtimeClasspath(String, BuildOutcome)` | Gepinnte Engine nutzen; kein eigener Resolver. |
| `build.interlis.ModuleArtifacts` | `runtime(Path, String, BuildOutcome)`, `Artifact` | Compilerclosure inklusive Koordinaten, Reihenfolge und tatsächlichen Hashes exportieren. |
| `build.interlis.JvmBuildInputs` | `requireProject`, `snapshot`, `fingerprint`, `sha256` | Bestehende Quelle-/Engineprüfungen wiederverwenden. |
| `build.interlis.ExistingJvmDistribution` | `run(Path)`, `generate(Path)` | Nicht pauschal aufrufen: diese erzeugen auch LSP/SDK. Nur vorhandene Compiler-Launcherlogik gezielt wiederverwenden. |
| `guru.interlis.cli.Main` | `main(String[])` | Native Mainklasse ist zunächst dieselbe wie auf der JVM. |

`JenesisBridge.run` besitzt bereits einen internen Erzeugungsmodus `BuildRequest("artifacts", null, null, root)` mit `tests(false)` und Staging. Das ist am Ausgangsstand **kein** vom öffentlichen Parser unterstützter Befehl `java ... artifacts`. Nicht eine erfundene CLI dokumentieren. Der neue Adapter darf diesen bestehenden Java-Einstieg nutzen. Er kann dabei alle zehn Projekte entdecken und erforderlichenfalls Artefakte erzeugen; das rechtfertigt keine erneute komplette Testausführung und keine Übernahme fremder Module in den nativen Classpath.

`readExistingOutcome` prüft vorhandene Ergebnisorte, ist alleine aber **kein Nachweis aktueller Quell-/Artefaktparität**. Wiederverwendung benötigt zusätzlich aktuelle Inputbindung und unveränderte Inventar-/JAR-Hashes. Bei Unsicherheit den schmalen Erzeugungslauf wiederholen statt Quellenfreiheit zu behaupten.

Die native Eingabe wird durch `ModuleArtifacts.runtime(root, "interlis-compiler-cli", outcome)` bestimmt. Nicht durch `find *.jar`, nicht durch einen handgepflegten Klassenpfad und nicht durch ein Fat-JAR mit verschatteten Dependencies. Die reale Closure darf grösser sein als nur compiler/model; erforderliche Workspace-/Repository-/Projection-Dependencies bleiben erhalten.

## 4. Architektur und Datenfluss

```text
Produktquellen + gepinnte Jenesis-Konfiguration
       |
       v
Jenesis-Auftrag A: echte erzeugte Runtime-Artefakte
       |
       v
Unveränderlicher compiler-inputs.json + kopierte Runtime-JARs
       |                         |
       |                         +--> JVM-Referenz derselben JARs
       v
CosmoGVM + cosmocc, beide getrennt und hashgepinnt
       |
       +--> native x86-64-Zwischenbild
       +--> native ARM64-Zwischenbild
       |
       v
fixupobj auf Arbeitskopien + apelink
       |
       v
Unveränderlicher Kandidat: interlis-compiler.com + Manifest/Notices
       |
       +--> gezielte CLI-/Netzwerkparität
       +--> native Plattform-/Start-/Hermetikproben
       v
Machbarkeitsbericht + unabhängiger Gesamt-Review
```

Der native Builder erzeugt Maschinencode aus bereits kompiliertem Java. Er darf dafür den Jenesis-Compilerbuild nicht durch `javac`, Madura oder Elide neu ersetzen. Das kleine Toolchain-Probeprogramm in F1 ist eine ausdrücklich separate Technikprobe, kein alternativer Produktbuild.

### 4.1 Vorgeschlagene Quellablage

```text
build/interlis/ape/
  CompilerApe.java
  CompilerRuntimeInputs.java
  ApeToolchain.java
  ApeBuild.java
  ApeAssembler.java
  ApeEvidence.java
  [ApeProcesses.java nur falls vorhandene Prozesshelfer nicht passen]
  toolchain.lock.json
  native-image/
    [nur tatsächlich benötigte Konfigurationsdateien]
  probes/
    ToolchainProbe.java
    [kleine buildbezogene Testquellen]

tools/ape/
  run-probes.cjs
  fixture-server.cjs
  compare-results.cjs
  *.test.cjs

testdata/ape/
  cases.json
  [kleine eigene ILI-/Repository-/Netzwerkfixtures oder Verweise auf vorhandene]

docs/implementation/compiler-ape-feasibility/
  [diese Auftragsdateien und die operativen Ergebnisdokumente]
```

Das sind **Designvorgaben**, keine Behauptung schon vorhandener Klassen. Kleine verwandte Helfer dürfen als verschachtelte Typen zusammengelegt werden. Keine SPI, Pluginplattform, neue Produktmodule oder allgemeine Native-Buildbibliothek. Ein negativer früher Abbruch erfordert keine Implementierung unbrauchbarer Restklassen oder leere Erfolgsstubs.

Versionierte APE-Buildquellen liegen unter dem bereits geschützten `build/interlis/`, nicht in einem neuen ungeschützten Root-`build/*`-Ordner. Generierte Produkte liegen ausschliesslich unter `target/compiler-ape/`. Die vorhandenen Skip-/Discoveryregeln müssen Buildquellen weiter aus den zehn Produktmodulen ausschliessen.

### 4.2 Ausgabeablage und Unveränderlichkeit

```text
target/compiler-ape/
  project.lock
  inputs/<input-digest>/
    compiler-inputs.json
    runtime/*.jar
    reference/                  # nur Compiler-JVM-Referenz, falls Launcher nötig
  toolchains/<archive-sha256>/   # alternativ expliziter isolierter Artefaktcache
  builds/<gemeinsamer-build-key>/
    x86_64/
    aarch64/
    fused/
    logs/
  candidates/<artifact-sha256>/
    interlis-compiler.com
    bundle-manifest.json
    notices/
  reports/<run-id>/
```

Finale Kandidaten und originale Belege nicht überschreiben. `fixupobj` arbeitet auf separaten Kopien der Zwischenbilder. Ein erneuter Build erhält einen neuen Run; Cachewiederverwendung wird als solche dokumentiert. Ein existierender Digestordner wird entweder vollständig verifiziert wiederverwendet oder explizit abgelehnt, nicht halb aktualisiert.

Review- und langfristige Rohbelege werden vor Clean ausserhalb des Repositorys aufbewahrt. Normale `clean`-Aufrufe müssen versionierte Jenesis-/APE-Quellen erhalten.

## 5. Bedienvertrag und Integration in Jenesis

Der folgende projektspezifische Befehlsraum ist **neu zu implementieren**, keine Jenesis-Upstream-Option:

```sh
java build/InterlisJvmBuild.java ape inspect
java build/InterlisJvmBuild.java ape prepare
java build/InterlisJvmBuild.java ape toolchain --lock <ABSOLUTE_LOCKDATEI>
java build/InterlisJvmBuild.java ape probe --lock <ABSOLUTE_LOCKDATEI>
java build/InterlisJvmBuild.java ape build --inputs <ABSOLUTES_INPUTMANIFEST> --arch x86_64 --lock <ABSOLUTE_LOCKDATEI>
java build/InterlisJvmBuild.java ape build --inputs <ABSOLUTES_INPUTMANIFEST> --arch aarch64 --lock <ABSOLUTE_LOCKDATEI>
java build/InterlisJvmBuild.java ape fuse --x64 <ABSOLUTES_SLICE_MANIFEST> --arm64 <ABSOLUTES_SLICE_MANIFEST> --lock <ABSOLUTE_LOCKDATEI>
java build/InterlisJvmBuild.java ape report --evidence <ABSOLUTER_BELEGORDNER>
```

`inspect` verändert keine Toolchains, lädt nichts herunter und startet keinen Native-Build. `prepare` beschafft fehlende normale Jenesis-Dependencies über die bestehende gepinnte Spur, erzeugt/kopiert Compilerinputs und kann die vorhandene JVM-Referenz ausführen. `toolchain` ist die ausdrücklich gewählte Beschaffung nach geprüften Pins; optional nimmt sie bereits vorhandene vollständig geprüfte Archive entgegen. `probe`, `build` und `fuse` verlangen eine verifizierte Toolchain und ersetzen sie nicht still.

Lokale Ausführungsprüfungen verwenden einen externen Testtreiber, z.B.:

```sh
node tools/ape/run-probes.cjs --binary <ABSOLUTES_APE> \
  --manifest <BUNDLE_MANIFEST> --profile cli --output <NEUER_REPORTORDNER>
```

Zulässige Profile: `cli`, `network`, `portable`. `portable` muss auf dem tatsächlichen Zielhost laufen; ein übergebenes Ziel ist höchstens eine Erwartung, die gegen reale Hostdaten geprüft wird. Der Testtreiber darf Node oder Java benötigen; die **auszuliefernde Compileranwendung** darf davon nicht abhängig sein. In einer JVM-freien Zielumgebung wird nicht der Java-Buildeinstieg zum Starten des Compilers vorausgesetzt.

Unbekannte Optionen, doppelte Optionen, relative unsichere Eingabeorte, fehlende erforderliche Dateien und leere Selektionen scheitern klar. Keine Shellzeichenketten für Prozessparameter, keine unsichtbaren Downloads aus `build` nach einem fehlenden Pin. Keine automatische Änderung von `JAVA_HOME`, PATH, Systemtruststore oder `binfmt_misc` ausserhalb der eigenen Kindprozessumgebung.

Bei `InterlisJvmBuild.main` den neuen Namensraum vor der bisherigen Argumentinterpretation explizit behandeln oder den Parser schmal erweitern. Die bestehenden Profile/Fehlerfälle behalten ihren Vertrag. Der bestehende JVM-Lock und der APE-Lock dürfen nicht rekursiv erworben werden. Definiere eine konsistente Reihenfolge; halte den JVM-Lock während Inventarerzeugung und Snapshotkopie, nicht unnötig über lange Native-Builds. Andere Buildprozesse dürfen die gerade einzufrierenden Eingaben nicht austauschen.

## 6. Klassen- und Methodenverträge

### 6.1 `CompilerApe`

**Aufgabe:** dünne projektspezifische Orchestrierung und Zustandsprüfung; keine Compilerlogik.

- `static void run(Path root, String[] args)`: `JvmBuildInputs.requireProject(root)` prüfen, Request parsen, erforderliche Locks geordnet erwerben, nur den ausdrücklich gewählten Schritt starten; Fehler mit nichtnull Exitstatus an den Aufrufer geben. `INSPECT` bleibt eine reine Leseoperation ohne Anlage von Toolchain-/Buildoutputs.
- `static Request parse(Path root, String[] args)`: `Operation`-Enum (`INSPECT`, `PREPARE`, `TOOLCHAIN`, `PROBE`, `BUILD`, `FUSE`, `REPORT`), optionale `Architecture`, verifizierbare Pfade; keine offenen Shellfragmente.
- `static void requireInputsFor(Request request)`: Vorbedingungen pro Operation; eine fehlende Phase wird nicht durch einen alten fremden Output ersetzt.
- `static Path newRunDirectory(Path outputRoot, String purpose)`: kollisionsfreier, abgegrenzter Ausgabeordner; Run-ID ist Betriebsmetadatum, nicht fachlicher Cachekey.

Ein kleiner Java-Adapter darf `ProcessBuilder` verwenden. Er muss nicht ein neues allgemeines Jenesis-Plugin werden. Die Engine selbst bleibt unverändert.

### 6.2 `CompilerRuntimeInputs`

**Aufgabe:** eine überprüfbare, unveränderliche Compilerclosure für beide Native-Architekturen und die JVM-Referenz.

Vorgeschlagene Records:

```java
record RuntimeJar(String coordinates, String relativePath, String sha256, long bytes) {}
record CompilerInputs(String schemaVersion, String sourceCommit, String sourceTree,
                      boolean dirty, String productInputDigest,
                      String mainClass, List<RuntimeJar> orderedRuntime,
                      Map<String, String> buildInputs) {}
```

- `static CompilerInputs prepare(Path root, Path destination)`: bestehende Jenesis-Erzeugung mit `tests(false)` verwenden oder gleichwertig belegten aktuellen Ausgang wiederverwenden; ausschliesslich `ModuleArtifacts.runtime(...compiler-cli...)` exportieren.
- `static void verifyClosure(Path root, List<Artifact> artifacts)`: vollständige Liste, eindeutige Namen/Koordinaten, tatsächliche Dateihashes, sichere reale Pfade, keine verbotenen Tool-/LSP-/Testmodule. Tatsächlich nötige Produktdependencies nicht nur wegen ihres Namens ausschliessen.
- `static void copyAndVerify(List<Artifact> artifacts, Path runtimeDirectory)`: vor und nach der Kopie hashen; Originale unverändert lassen; Reihenfolge erhalten; keine überzähligen Dateien.
- `static CompilerInputs readVerified(Path manifest)`: Schema, Mainklasse, vollständige Datei-/Hashliste und reale Pfadgrenzen prüfen; keine Auswahl des „neuesten“ beliebigen Stagingordners.
- `static List<Path> orderedClasspath(CompilerInputs inputs, Path inputRoot)`: nur Manifestreihenfolge, keine Wildcards und keine Umordnung durch HashMapiteration.
- `static String buildInputDigest(...)`: canonical deterministic Hash über tatsächliche Produkt-JARs, native Konfiguration, Toolchain-ID, Zielarchitektur und wirksame Buildoptionen; Commitdatum/Reviewtext sind kein Ersatz für Inhaltsbindung.

Für Referenzprozesse vorhandene Compiler-Launchertemplates/Argumentregeln wiederverwenden. Nötigenfalls eine kleine Compiler-only-Stagingmethode aus `ExistingJvmDistribution` extrahieren; dessen Standardverhalten für SDK/LSP nicht verändern. Der bestehende Windows-ENV-Argumentübergang gilt nur für den JVM-Launcher. Ein nativer Aufruf soll reguläre native `argv` erhalten und nicht künstlich die JVM-Bridge aktivieren.

### 6.3 `ApeToolchain`

**Aufgabe:** gegengeprüfte Drittanbieterwerkzeuge, keine eigene Toolchainentwicklung.

```java
record ArchivePin(String id, String version, String uri, String sha256) {}
record ToolIdentity(String relativePath, String sha256, String versionOutput) {}
record VerifiedToolchain(Path root, String lockDigest,
                         Map<String, ToolIdentity> tools) {}
```

- `static Lock readLock(Path path)`: Pflichtfelder, SHA-256-Form, festgelegte Referenz, erwartete Builderplattform und vollständige Werkzeugliste prüfen. Kein `latest`.
- `static Path acquire(ArchivePin pin, Path cache)`: expliziter Download mit Timeouts/Grössengrenze in temporäre Datei, Hashprüfung vor Entpacken/Ausführen, atomare Übernahme; Netzwerk-/Hashfehler getrennt melden.
- `static VerifiedToolchain installVerified(Lock lock, Path cache)`: Archivinventar prüfen; Zip-/Tar-Pfadtraversal und externe Symlinks verhindern, notwendige **interne** Toolchain-Symlinks nicht pauschal kaputtmachen; keine Überschreibung fremder Ziele.
- `static CapabilityReport inspect(VerifiedToolchain tools)`: echte `native-image --version` und unterstützte Optionen, kosmospezifische Libs/CAP-Caches, `fixupobj`, `apelink` und Loaderdateien prüfen. Versionsausgabe alleine beweist keine funktionierende Cosmo-Unterstützung.
- `static ProbeResult runJavaProbe(...)`: F1-Programm mit derselben relevanten Native-Konfiguration bauen und tatsächlich ausführen; Testfehler nicht als Compilerfehler darstellen.

Die initialen Referenz-URLs/-Hashes stehen im Toolchaindokument. Sie müssen real verifiziert werden. Unterstützt eine neuere gepinnte Drittanbieterversion das Ziel nachweislich besser, darf nach dokumentierter Auswahl **die isolierte Native-Toolchain** geändert werden. Kein automatischer Versionsroulette, kein Update der JVM-Produkttoolchain und kein Hashaustausch nur zur Umgehung eines Fehlers.

### 6.4 `ApeBuild`

**Aufgabe:** Native-Image-Aufruf je Architektur und Bindung aller wirksamen Eingaben.

- `static BuildPlan plan(CompilerInputs inputs, VerifiedToolchain tools, Architecture arch, NativeOptions options)`: vollständige Argumentliste mit `guru.interlis.cli.Main`; kompatibles CPU-Ziel, begründete Ressourcen-/Reflectionregeln, keine JVM-Fallbackoption. Nur nachgewiesene Optionen der gepinnten Toolchain.
- `static Path writeArguments(BuildPlan plan, Path file)`: korrektes Native-Image-/JDK-Argumentdateiformat; nicht Shellquoting imitieren. Whitespace-/Unicode-Pfade testen. Rohargumente ohne Credentials aufbewahren.
- `static NativeSlice build(BuildPlan plan, Path freshOutput)`: gepinntes Tool explizit starten, stdout/stderr separat behalten, Exitcode/Timeout/Signal erfassen, Produktdatei und Buildreport tatsächlich verifizieren; keine leere Successdatei.
- `static void verifyNoFallback(NativeSlice slice)`: Buildreport/Optionen und Ausführungsbeobachtung zusammen prüfen; ein Shellstarter, der Java startet, genügt nicht.
- `static void verifyNoInputDrift(...)`: relevante Inputs vor/nach Erzeugung vergleichen. Koordinaten oder Commitname alleine genügen nicht.

Architekturergebnisse sind Zwischenprodukte, keine schon bestätigten Universaldateien. Ein gewöhnlicher hostnativer GraalVM-Build darf optional als **Diagnosekontrolle** dienen, wenn er eine konkrete Frage isoliert; er ist nicht stiller Ersatz für CosmoGVM.

Keine breite Build-time-Initialisierung ganzer eigener Packages oder sämtlicher JDK-Klassen auf Verdacht. Ressourcenregeln werden aus realen Zugriffswegen und Gegenproben abgeleitet. Ein Tracing-Agent hilft, beweist aber allein keine Vollständigkeit. Jede eigene Metadatengruppe erhält Zweck und minimalen Triggerfall.

### 6.5 `ApeAssembler`

**Aufgabe:** tatsächlicher Zwei-Architektur-Zusammenbau und unveränderliche Kandidatenbildung.

- `static void verifyCompatibleSlices(NativeSlice x64, NativeSlice arm64)`: gleiche Compilerinputs, Produktversion, Trustpolicy und kompatible Werkzeug-/Metadatenkonfiguration; nur erlaubte architekturspezifische Differenzen.
- `static PreparedSlice applyFixupCopy(NativeSlice input, Path work)`: Originalhash erfassen; Kopie mit gepinntem `fixupobj` bearbeiten; Original muss danach unverändert sein; Zwischenoutput binden.
- `static ApeCandidate fuse(..., Path work)`: tatsächliches `apelink` mit beiden gepinnten Loaderinputs und ARM-Mac-Bootstrapreferenz aufrufen, vollständige argv und Exitcode behalten. Keine angenommene Universalität aus Dateisuffix oder magic bytes.
- `static void freezeCandidate(ApeCandidate candidate, Path target)`: finale Binarybytes, Modus, Grösse, Manifest und Noticeinventar einmalig schreiben. Spätere Signatur-/Patch-/Packschritte erzeugen einen neuen Kandidaten.
- `static BundleResult packageAndVerify(...)`: kleine Testauslieferung aus Kandidat und notwendigen Beigaben erstellen, separat entpacken und Hash-/Modus-/Inventargleichheit prüfen. Kein Releasepublish.

Mac-Loader können technisch Dateien in einen privaten Cache extrahieren. Solches Verhalten ist zu messen und offen auszuweisen. Das rechtfertigt weder externe Downloads beim ersten Start noch einen ungeprüften privaten Loader aus der Entwicklerumgebung.

### 6.6 `ApeEvidence`

**Aufgabe:** faktische Beleginventare und Entscheidungsvorbereitung, kein Generator von Erfolg.

- `static void validateRunManifest(...)`: Eingabedigest, finale Binaryhashes, Fixture-/Harnesshashes, wirkliche Hostplattform, Startmodus und Exitstatus prüfen.
- `static Comparison compareCli(ReferenceRun jvm, CandidateRun nativeRun, ComparisonPolicy policy)`: strukturierte Ergebnisse verlustfrei vergleichen; Arrays/Diagnosefolge/Literale/Dezimalgenauigkeit erhalten. Exakte zulässige Unterschiede separat auflisten.
- `static void requireCases(Profile profile, List<CaseResult> cases)`: deklarierte Pflichtfälle exakt vorhanden, keine doppelten IDs oder verdeckten Skips; erwartete negative Produktexits sind keine fehlgeschlagenen Harnesses.
- `static PlatformSummary collectPlatforms(List<Path> reports, String artifactHash)`: nur echte Runs desselben finalen Kandidaten; Plattformidentität aus Hostbelegen, nicht aus Wunschparametern.
- `static Decision evaluate(Facts facts)`: Regeln aus Abschnitt 11; fehlende Daten erzeugen kein GO. Dokumentierte technische Einschränkungen dürfen nicht nachträglich gelöscht werden.

`report` erzeugt aus Belegen einen Bericht, **führt aber nicht automatisch noch einmal alle Tests aus**. Ein vollständig maschinell erzeugter Report ersetzt die begründete Machbarkeitsempfehlung und den unabhängigen Review nicht.

### 6.7 Prozess- und Fixturehelfer

Vorhandene geeignete Prozesshelfer wiederverwenden. Falls notwendig, `ApeProcesses.run(List<String> argv, Path cwd, Map<String,String> env, Duration timeout, Path logs)` ergänzen. Keine Shellinterpolation, keine vollständigen sensitiven Umgebungsdumps, kein Deadlock durch ungepufferte volle Pipes. Timeout beendet ausschliesslich den eigenen Prozessbaum und wird nicht als erwarteter Produktfehler umgedeutet.

Der Node-Testtreiber hat exportierbare kleine Funktionen `loadCandidate`, `runCase`, `compareCase`, `writeRunReport` und `main`. `fixture-server.cjs` bietet `startRepositoryServer`, `startTlsRepositoryServer`, `startConnectProxy`, `stopFixture`. Bestehende lokale Testserver bevorzugen; keine zweite umfangreiche Serverplattform.

TLS-Fixtures benutzen einen nachprüfbar gepinnten Testschlüssel und Testzertifikate ohne Produktionsgeheimnisse. Die Test-CA darf nicht in den finalen Kandidaten oder in globale Truststores gelangen. Ressourcen werden mit `finally`/Shutdownhooks beendet.

## 7. Native-Konfiguration und produktbezogene Risiken

### 7.1 Ressourcen

Inventarisiere die tatsächlichen Compiler-/Modell-/Repository-/Projection-Ressourcen: Grammatikergebnisse, Unicode-/Formatdaten, Diagnose-/Sprachressourcen, Versionsdateien, Servicebeschreibungen und nötige Schemas. Nicht alle Dateien des Repos pauschal einbetten. Testarchive, Buildberichte, Quellcode, lokale Keys, absolute Benutzerpfade und LSP-Dateien gehören nicht hinein.

Erkenne Reflection-/ServiceLoader-/Proxyzugriffe aus dem wirklichen Runtimegraphen. Keine Gson-/LSP4J-Konfiguration auf Verdacht nur weil der LSP sie verwendet. Serviceprovider und Charset-/Locale-/TLS-Funktionen werden durch konkrete Cases geprüft.

### 7.2 Laufzeitinitialisierung und Betriebssystem

Hostwerte dürfen nicht vom Linux-Buildhost für Windows/macOS festgeschrieben werden: insbesondere Pfadseparatoren, Charset, CWD, HOME, Proxy, Terminalerkennung, Dateisperren, Cacheorte und Tempverzeichnisse. Eine korrekte `os.name`-Zeichenkette allein beweist kein korrektes Dateisystemverhalten. Umgekehrt darf der Harness seine Plattform nicht aus einer möglicherweise vorinitialisierten Anwendungseigenschaft ableiten.

Die CLI hat einen bestehenden Windows-JVM-Argumentadapter; er wird im nativen Standardpfad nicht künstlich aktiviert. Native Unicodeargumente und Working-Directory-Verhalten müssen auf Windows tatsächlich geprüft werden.

### 7.3 TLS und Trust

GraalVM dokumentiert für Native Image die Übernahme eines Buildzeit-Truststores. Deshalb ist die produktive Trustquelle expliziter Buildinput mit Hash, Herkunft und Aktualisierungsfolgen. Keine individuellen Firmen-/Test-CAs des Entwicklerrechners automatisch einbauen.

Ein finaler Standardkandidat wird mit einer sauberen dokumentierten Trustbasis gebaut. Für kontrolliertes positives Loopback-HTTPS darf ein separater Testtruststore **zur Laufzeit** verwendet werden, soweit die konkrete Toolchain das nachweislich unterstützt. Falls eine spezielle Testbinary nötig ist, ist sie separat gehasht und kein Ersatz für den Standardkandidaten.

Der Standardkandidat muss einen echten öffentlichen HTTPS-Repositoryzugriff erfolgreich schaffen und untrusted bzw. falsche Hostnamen ablehnen. Öffentliches Netz liefert einen Smoke, keine reproduzierbare Erwartungsbasis für das gesamte Corpus. URLs, Antworten und Bytes werden für den Smoke erfasst. Netz-/Proxyblockade der Umgebung führt zu einem offenen Nachweis, nicht zu „TLS funktioniert nicht“.

## 8. Abnahmeumfang und Nichtregression

Die feste kleine Testmatrix in `TEST_MATRIX.md` ist verbindlich. Es gibt keine pauschale erneute vollständige JVM-, Browser-, Wasm- oder Vollkorpusabnahme. Nach nativen Metadaten-/Toolchainänderungen ist die kleine Native-Matrix relevant, nicht automatisch die gesamte Produktsuite.

Gleichwohl müssen die bisherigen Buildeinstiege ihre Argumente, normalen Tests und Ausgabepfade behalten. Ein gezielter Parser-/Orchestrierungsgegencheck und eine kleine JVM-Referenzprobe nach Änderungen an `InterlisJvmBuild` sind Pflicht. Jenesis-Quellen-/Inputschutz, Gradle-Clean-Guard und Editorwege dürfen nicht geschwächt werden.

Für reine additive APE-Dateien und unveränderte Produktartefakte sind neue GUI-Läufe und LSP-Tests nicht begründbar. Wird entgegen dem Scope doch eine gemeinsame Runtime-/Produktfunktion geändert, muss die Auswirkung benannt und ein enger Verbrauchercheck ergänzt oder der Eingriff zurückgenommen werden.

## 9. Artefakt- und Nachweisschemata

Die Implementierung darf additive Felder ergänzen, aber die Identitätsgrenzen nicht weglassen. Beispiele sind Formen mit Platzhaltern, keine erzeugten Nachweise.

```json
{
  "schemaVersion": "1.0",
  "kind": "interlis-compiler-ape-inputs",
  "source": {"commit": "<SHA>", "tree": "<TREE>", "dirty": true},
  "productInputDigest": "<SHA256>",
  "mainClass": "guru.interlis.cli.Main",
  "builder": {"jenesis": "<ACTUAL_PIN>", "jdk": "<ACTUAL_JVM_VERSION>"},
  "orderedRuntime": [
    {"coordinates": "<COORDINATES>", "path": "runtime/<FILE>.jar", "sha256": "<SHA256>", "bytes": 1}
  ],
  "buildInputs": {"<QUELLPFAD>": "<SHA256>"}
}
```

Der separate Native-Buildplan bindet zusätzlich `toolchainLockSha256`, `nativeConfigurationDigest`, die gemeinsame Inputidentität und je Architektur einen eigenen Slice-Buildkey. Die beiden Architekturpläne verwenden dasselbe Compilerinputmanifest; die Fusion nimmt zwei explizite Slice-Manifeste entgegen, niemals automatisch die zuletzt gefundenen Dateien.

Eine Runtimeprüfung ergänzt mindestens `runId`, `candidateSha256`, `inputDigest`, `profile`, tatsächliche `host`-Daten, `launchMode`, `emulation`, exakte `command`-Argumentliste, Fixture-/Harnesshashes, individuelle Case-Ergebnisse, Prozesszeiten, Exit-/Signal-/Timeoutangaben und Originalprotokollhashes. Im Binary eingebettete Provenienz darf keine nachträglich unbekannte Git-SHA vortäuschen; baue mit tatsächlich festgelegten Inputs und halte Git-/Reviewbindung extern.

Neue Durchläufe behalten ihre eigene Herkunft. Ein späterer Commit darf ältere Belege referenzieren, wenn ein dokumentierter Inhaltsvergleich die Unverändertheit ihrer relevanten Inputs zeigt. Kein Umschreiben von `dirty`, Commit oder Laufdatum.

## 10. Kosten- und Wartungsgrenzen

Keine permanente CosmoGVM-/JDK-/libc-Patchpflege. Keine vollständige Modellkorpusbenchmarkkampagne, kein PGO-Aufbau und kein allgemeines Multi-OS-Releaseframework. Ein kleiner eigener Builderadapter, explizite native Metadaten, Testfixtures und verständliche Dokumentation sind angemessen.

Bei Native-Fehlern zunächst minimalen Reproducer und Zuordnung erstellen: Produktkonfiguration, Bibliothek, Toolchain, Loader, Netzwerk oder nicht verfügbare Umgebung. Nach einem deterministisch wiederholten Fehler keine identischen unbegründeten Neuversuche. Jede weitere Änderung braucht eine neue überprüfbare Hypothese. Optional eine gezielte reguläre GraalVM-Kontrolle oder dokumentierte alternative **ungepatchte** Vendorversion verwenden; kein endloses Versionsprobieren.

Bei unvertretbarem Memory-/Zeitbedarf des Builders einen sinnvollen expliziten Ressourcenrahmen und die tatsächliche Messung dokumentieren. Eine kleine lokale Hardware ist kein Beweis technischer Unmöglichkeit. Keine Zeit-/RAMlimits nur erhöhen, bis zufällig ein grüner Lauf entsteht; keine reservierten/teuren Ressourcen ohne Auftrag beschaffen.

## 11. Entscheidungen und negative Abschlusszweige

| Ergebnis | Erforderliche Aussage |
|---|---|
| `GO` | Kombiniertes APE, tatsächliche kleine Produkt-/Netzwerkprüfungen und alle drei Pflichtplattformen; kein JVM-/Builderbedarf, keine unbenannten Laufzeitvoraussetzungen; Direktstart als geprüfter Kindprozess und normaler Terminalstart ohne externe Zusatzinstallation sind belegt. |
| `GO_WITH_LIMITATIONS` | Alle Pflichtplattformen und Kernfunktionen wurden real geprüft, aber die nutzbare Auslieferung braucht klar ausgewiesene zulässige Beigaben/Startbedingungen, etwa einen mitgelieferten Loader oder einen ausdrücklich notwendigen Shellstart. Kein „echte Ein-Datei-Auslieferung“-/uneingeschränkter Direktstart-Claim. Keine notwendige JVM, deaktivierte TLS-Prüfung oder ausgefallene Pflichtfunktion tolerieren. |
| `NO_GO` | Reproduzierbarer technischer Blocker unter den freigegebenen Grenzen, z.B. erforderlicher eigener Toolchainfork, falsche Compilerresultate trotz begrenzt untersuchter Konfiguration oder nicht sinnvoll auslieferbarer nativer Runtimepfad. |
| `INCONCLUSIVE` | Fehlende Pflichtplattform, nicht verfügbare Builderumgebung, nicht verifizierbares Archiv oder notwendiger nicht ausführbarer Test; beobachtete Teilerfolge bleiben benannt. |

Ein vom Benutzer nicht geforderter neuer Hersteller-Supportvertrag oder fehlende notarized Releasepakete blockiert den Prototyp nicht automatisch; dies sind spätere Auslieferungsthemen. Dagegen darf ein geschlossener CLI-Defekt nicht als „nur Releasefrage“ versteckt werden.

Kann ein früheres Produktgate nicht bestanden werden, werden abhängige Phasen als `NOT_RUN_BLOCKED` markiert. Die **Analysephase** darf nach belegter Einordnung in den terminalen Bericht F5 übergehen, ohne so zu tun, als sei das Produktgate bestanden. Alle erreichbaren unabhängigen Validator-/Regressionsproben bleiben sinnvoll auszuführen. Review folgt auf den committeten tatsächlichen Stand und bewertet diese Entscheidung.

Ein Review-PASS bestätigt die Korrektheit und Vollständigkeit des tatsächlich beauftragten Untersuchungswegs; er überschreibt kein `NO_GO`/`INCONCLUSIVE`. Eine positiv behauptete Umsetzung ohne erforderliche Runs ist `FAIL`, nicht ein akzeptabler negativer Abschluss.

## 12. Abnahmekriterien

Die Kriterien gelten für den tatsächlichen Untersuchungsweg. Bei frühem negativem Abschluss sind unerreichbare Produktnachweise explizit `NOT_RUN_BLOCKED` mit kausalem Blocker zu versehen, nicht PASS oder pauschal N/A. Pflichtkriterien zur Ehrlichkeit, Scope-, Git- und Reviewhygiene entfallen dadurch nicht.


| ID | Kriterium | Nachweis |
|---|---|---|
| AP-01 | Scope eingehalten | Nur Compiler-APE-Machbarkeit; keine JPMS-, LSP-, Extension-, Wasm- oder Formatter-Stiländerung. |
| AP-02 | Jenesis als Artefaktquelle | Alle Compiler-Eingaben aus tatsächlich validierten Jenesis-Ergebnissen; kein Gradle-/Maven-/Cacheprodukt als Ersatz. |
| AP-03 | Revisions- und Inputbindung | Quellstand, Dirty-Zustand und relevante Inhaltsfingerprints sind ehrlich erfasst; Änderungen während Erzeugung werden erkannt. |
| AP-04 | Compiler-Runtimeclosure | Reihenfolge, Koordinaten, Namen und Hashes sind vollständig; keine Test-, LSP-, Verification- oder Buildtool-Leaks. |
| AP-05 | Toolchainpin | Archiv- und Werkzeugidentität, Quelle, Version und SHA-256 verifiziert; abweichende Bytes führen zum Fehler. |
| AP-06 | Kein eigener Toolchain-Fork | Unveränderte Drittanbieter-Toolchain; keine stillen Patches, breiten JDK-Substitutionssammlungen oder ungeprüften Versionstausche. |
| AP-07 | Frühe Fähigkeitserprobung | Realer Java-Probeaufbau prüft Java25, Cosmo-Backend und notwendige Basiseigenschaften vor grosser Integration. |
| AP-08 | Echte APE-Erzeugung | Beide Architekturinputs und der nachgewiesene Zusammenbau sind gebunden; gewöhnliches Native Image ist nicht als APE deklariert. |
| AP-09 | Ressourcen und Reflection | Nur begründete Reachability-/Ressourcenregeln; keine unbekannten Missing-Registration-Fehler oder weggefilterten Produktpfade. |
| AP-10 | Keine eingefrorene Buildumgebung | HOME, CWD, Proxy, Cache, Zeit/Locale und Trustmaterial sind kontrolliert; keine privaten Builderdaten oder notwendigen Builderpfade in der Laufzeit. |
| AP-11 | Fachliche CLI-Parität | Kleine reale Prüfmatrix gegen JVM derselben Eingaben; Fehlercodes, Diagnosen und strukturierte Ergebnisse werden nicht schönnormalisiert. |
| AP-12 | Formatterparität | Stdout/check/write/Optionen, Fehlerfall und Schutz der Originaldateien funktionieren unverändert; kein neuer Stil. |
| AP-13 | Hostverhalten | Unicode-/Leerzeichenpfade, Argumentgrenzen, Streams, Exitcodes und Dateiausgaben werden real geprüft. |
| AP-14 | TLS bleibt sicher | Gültiges HTTPS funktioniert; untrusted/mismatched Zertifikate werden abgelehnt; kein Trust-all und keine Builder-CA-Verschleppung. |
| AP-15 | Repositoryfunktionen | Importe, Cache/Offline, Proxy und Timeouts funktionieren in den relevanten kleinen kontrollierten Szenarien. |
| AP-16 | Ohne JVM und Buildbaum | Das Laufzeitartefakt arbeitet ohne nachladbare JVM, JARs oder versteckte JDK-/Quellpfade; erlaubte Runtimebeigaben sind vollständig ausgewiesen. |
| AP-17 | Plattformnachweis | Dasselbe finale Universal-APE wird auf den drei Pflichtplattformen geprüft; fehlende Runs sind keine PASS-Plattformen. |
| AP-18 | Loader und Prozessstart | Direktstart und Shellstart sind getrennt; Loader/Entpacken/Emulation/Compilerbedarf werden nicht verborgen. |
| AP-19 | Nachweisvalidatoren | Fehlende, fremde, stale, manipulierte, geskipte oder unvollständige Ergebnisse werden fail-closed erkannt. |
| AP-20 | Bestehende Buildwege erhalten | Normale JVM-/Gradle-/Editorwege erhalten keine Native-Pflicht oder zusätzliche Toolchaininstallation. |
| AP-21 | Sichere Pfade und Clean | Versionierte build/-Quellen und fremde Arbeit bleiben erhalten; Downloads, Ausgaben und Mutationstests sind isoliert. |
| AP-22 | Explizite Ausführung | Native Toolchainbeschaffung und Builds nur durch explizite Aufrufe; keine globale Änderung und kein unautorisierter Push/Release. |
| AP-23 | Gezielte Teststrategie | Phasengates und Fixprüfungen folgen Auswirkungen; gültige Belege bleiben erhalten, aber neue Binärbytes werden nicht mit alten Runs freigegeben. |
| AP-24 | Messungen ehrlich | Grösse/Start/kleine Kompilierung als Beobachtung; keine behaupteten Beschleunigungen oder erfundenen Leistungsbudgets. |
| AP-25 | Herkunft und Hinweise | Runtimebestandteile, Drittanbieterquellen und Lizenz-/Notice-Dateien sind inventarisiert; keine pauschale rechtliche Freigabe. |
| AP-26 | Getrennte Urteile | GO/GO_WITH_LIMITATIONS/NO_GO/INCONCLUSIVE und Review-PASS/FAIL/BLOCKED sind getrennt und aus Belegen ableitbar. |
| AP-27 | Bedienbarer Prototyp | Dokumentierte explizite Befehle, Vorbedingungen und Fehlerpfade; keine erfundenen CLI-Flags oder leeren Erfolgsstubs. |
| AP-28 | Saubere Abbruchgrenzen | Reproduzierbare Blocker werden minimiert und eingeordnet; fehlende Infrastruktur wird nicht als technische Unmöglichkeit verkauft. |
| AP-29 | Unabhängiger Gesamt-Review | Frische OpenCode-2.x-/DeepSeek-Flash-1017-Sitzung prüft exakten Kandidaten, Code, Ziele und eigene Gegenproben erst am Ende. |
| AP-30 | Korrektur-/Review-Schleife | Berechtigte Findings behebt Codex; gezielte Retests, neuer Commit, frischer kumulativer Review. |
| AP-31 | Git- und Beleghygiene | Nur Auftragsänderungen committen; Originalnachweise unverändert; keine stillen Änderungen nach finalem Review. |
| AP-32 | Exakte Lieferung | Ein geprüfter Archiv-/Bundleinhalt bindet unveränderte finale Binarybytes und notwendige Beigaben; Austausch-/Mischfälle scheitern. |

## 13. Ergebnisdokumente

Codex erzeugt bzw. führt fort:

- `BASELINE.md`: wirklicher Ausgangsstand, A-Nachweislage, Tool-/Hostinventar und ausgeschlossene Bereiche.
- `TOOLCHAIN_REPORT.md`: tatsächlich verwendete Vendorpins, Optionen, Probeausgänge, Loader-/Architekturfakten, Warnungen und ungelöste Punkte.
- `STATUS.md`: Phasenstatus und offene Fragen, keine vorweggenommenen Erfolge.
- `TEST_LEDGER.md`: Tests und Wiederverwendung mit präziser Herkunft und Gründen.
- `FEASIBILITY_REPORT.md`: Entscheidung, Plattform-/Startmatrix, Ressourcen/Grössen, Runtimebeigaben, Wartungsrisiken und nächste getrennte Entscheidung.

Originalprozesslogs, Binary-/Input-/Archivmanifeste, Probeberichte und Reviewerurteile liegen gehasht im externen Belegroot. Kein nachgelagerter Dokumentationscommit nach dem finalen Review ohne transparenten erneuten Review des geänderten Kandidaten.
