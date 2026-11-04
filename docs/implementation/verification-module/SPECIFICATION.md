# Spezifikation: internes Modul `interlis-verification`

**Version:** 1.0 · **Status:** Spezifikation, nicht implementiert oder ausgeführt  
**Repository:** `edigonzales/interlis-java`  
**Untersuchter Referenzstand:** `ff30c7edffbf0ee57ba95e44b630c08f4d4a8934` (`main`)  
**Auftrag:** Source-Sets und Prüfverantwortlichkeiten bereinigen. **Keine Jenesis-Migration.**

## 1. Ziel und verbindliche Grenzen

Die ausführbaren Host-Prüfprogramme werden aus `interlis-compiler-cli/src/verification` in ein internes Gradle-Teilprojekt `interlis-verification` überführt. Die Tests dieser Programme werden gewöhnliche Tests dieses Moduls. Die bisherigen `evidenceTest`-Quellen werden nicht blind verschoben: Jede Prüfung wird nach aktuellem Nutzen, Verbraucher und Aussagekraft eingeordnet, sinnvoll benannt, gegebenenfalls aufgeteilt oder begründet aus dem aktiven Build entfernt.

Das Ergebnis muss verständlicher sein als der Ausgangsstand. Eine neue Ebene von abstrakten Testframeworks, dynamischen Pluginregistries, allgemeinen Evidenz-Engines oder zusätzlichen Source-Sets ist ausdrücklich nicht das Ziel.

### 1.1 Ergebnis

- `interlis-compiler-cli` enthält nur noch die CLI und ihre eigenen gewöhnlichen Tests.
- `interlis-verification` verwendet ausschliesslich die üblichen Java-Source-Sets `main` und `test`.
- Prüfprogramme, ihre Unit-Tests, aktuelle Vertragsprüfungen und historische Abnahmen sind klar unterschieden.
- Direkte Compilerregressionen, die zufällig im bisherigen `verificationTest` liegen, kehren in das Compiler-Testmodul zurück.
- Laufende Integritäts-, Herkunfts-, Negativ- und fachliche Prüfungen bleiben erhalten oder erhalten einen mindestens gleichwertigen, konkret nachgewiesenen Ersatz.
- Historische Originalnachweise bleiben unverändert erhalten. Historische Arbeitsstände werden nicht als aktuelle Produktanforderung festgeschrieben.
- Jede Implementierungsphase wird gezielt von Codex geprüft. **Der unabhängige OpenCode-Review findet erst nach allen Implementierungsphasen statt.**

### 1.2 Nicht im Scope

Kein Jenesis, kein Maven-Build, kein JPMS-Umbau, keine Versionsupgrades, kein Wechsel von Gradle/Groovy, keine neue Produkt-CLI, keine Änderungen an INTERLIS-Semantik, CLI-Ausgaben oder LSP-Protokoll. Keine Wiederaufnahme des entfernten TeaVM-Patches. Kein Umbau von TeaVM/GraalVM Web Image, kein automatischer Voll-Release und kein Push.

Vorhandene Java-/ANTLR-/Node-/Browser-/GraalVM-Pins bleiben bestehen. Produktive Java-Klassen werden nur geändert, wenn eine eindeutig notwendige, nicht semantische Integrationskorrektur nachgewiesen ist; eine solche Abweichung ist ausdrücklich zu dokumentieren und gezielt zu testen. Insbesondere werden keine intern sichtbaren Compilerklassen für die Verifikation öffentlich gemacht.

## 2. Ausgangslage und konkrete Problemstellen

Die Source-Sets werden aktuell in `gradle/host-verification.gradle` und `gradle/evidence.gradle` am CLI-Projekt ergänzt. Die Hostprogramme erben Teile des CLI-Test-Classpaths. `check` hängt sowohl an den Unit-Tests der Prüfprogramme als auch über `g2EvidenceTest` an einem grossen G2-Nachweispaket. Dieses Paket bereitet Norm-PDFs vor, führt ausgewählte Compilerassertions erneut aus und benötigt Testberichte mehrerer Produktmodule.

Der untersuchte Stand enthält insbesondere diese Kopplungen:

| Bereich | Konkrete Kopplung | Konsequenz |
|---|---|---|
| Hostprogramme | `guru.interlis.cli.verification.*` und `sourceSets.verification.runtimeClasspath` | Neues Paket, neue Taskbesitzer und ausdrückliche Dependencies nötig. |
| CLI-Distribution | Relative `dependsOn 'installDist'` in am CLI registrierten Tasks | Nach dem Umzug müssen diese Referenzen `:interlis-compiler-cli:installDist` heissen. |
| Plattformbericht | `PlatformProcesses` kennt alte XML-Verzeichnisse und Mindestzahlen | Migration der Produzenten **und** aller Berichtskonsumenten. |
| Release-Validator | `tools/release/evidence.cjs` prüft ebenfalls Plattformzahlen/Nachweise | Kein blosses Anpassen eines Java-Testzählers. |
| Source-Fingerprints | Java `ProfileFingerprint` und Node `tools/performance/profile.cjs` | Neue Quellen und Pfade müssen konsistent berücksichtigt werden. |
| Subprozesse | Hilfsprogramme wie `FullCorpusCold` werden per Klassenname gestartet | Auch Stringreferenzen und Child-Classpaths migrieren. |
| Evidenzprüfungen | Exakte Quellhashes, Codepoint-Offsets, historische Statuswerte | Historische Belege nicht massenhaft auf neue Quellen umschreiben. |
| Compilerregression | `UnicodeWeightsTest` liegt im Compiler-internen Package, aber im CLI-Source-Set | In das normale Compiler-Test-Source-Set verschieben. |
| Gemischter Test | `CorpusOuterNameContractTest` nutzt Compiler, CLI und `HostChecks` | Nicht als rein interner Compilertest behandeln; ausdrückliche Testdependency auf CLI. |

Die im früheren Gespräch erwähnten Verzeichniszählungen sind **kein** verbindliches Dateiinventar. Phase V0 erfasst die tatsächlich vorhandenen `.java`-Dateien, Testmethoden, Parameterfälle und Laufzeitabhängigkeiten des lokalen Ausgangsstands.

## 3. Zielstruktur

```text
interlis-verification/
  build.gradle
  src/main/java/guru/interlis/verification/
    HostChecks.java
    VerificationLayout.java
    ProfileFingerprint.java
    CompilerCliProcesses.java
    SdkProcesses.java
    SdkArchiveCheck.java
    PlatformProcesses.java
    ConformanceEvidence.java
    ... bestehende weitere Host-Prüfprogramme ...
  src/test/java/guru/interlis/verification/
    ... Tests der Hostprogramme ...
    ... kleine modulübergreifende Integrationsverträge ...
  src/test/java/guru/interlis/verification/contracts/
    ... nur tatsächlich weiter benötigte Vertragsprüfungen und Testhelfer ...
  src/test/resources/                 # nur kleine neue synthetische Fixtures

interlis-compiler/
  src/test/java/guru/interlis/compiler/
    LegacyOidCompatibilityTest.java
    LegacySourceMetadataTest.java
  src/test/java/guru/interlis/compiler/internal/
    UnicodeWeightsTest.java

gradle/
  host-verification.gradle            # Root-Delegation und projektübergreifende Reihenfolge
  verification-contracts.gradle       # nur falls für ausgewählte Testproduzenten nötig

testdata/verification/                # nur notwendige aktuelle Bindungs-/Profilmigrationen
```

`src/verification`, `src/verificationTest` und `src/evidenceTest` entfallen vollständig. Sie werden auch nicht unter einem neuen Namen als Source-Sets wieder angelegt. Mehrere `Test`-Tasks dürfen dieselben kompilierten Klassen aus `src/test` mit JUnit-Tags auswählen.

### 3.1 Modulabhängigkeiten

Das neue Modul verwendet das Gradle-Plugin `java`, nicht `application`. Die bisherigen einzelnen `main(String[])`-Prüfprogramme bleiben einzelne Einstiegspunkte; kein zusätzliches `interlis-verify`-Produkt wird gebaut.

`gradle/java-conventions.gradle` wird wie bei den anderen Java-Modulen angewandt. Der Build benennt Abhängigkeiten ausdrücklich:

- `implementation` für die im Maincode tatsächlich verwendeten Produktbibliotheken und Gson; insbesondere Compiler, Modell, Workspace, Projektion und Bridge prüfen, Repository nur bei tatsächlicher Verwendung.
- `testImplementation` für JUnit Jupiter/BOM und zusätzlich `project(':interlis-compiler-cli')`, weil beispielsweise `CorpusOuterNameContractTest` die öffentliche CLI-API benutzt.
- `testRuntimeOnly` für den JUnit-Platform-Launcher entsprechend dem vorhandenen Versionskatalog.
- Keine Abhängigkeit auf den früheren `sourceSets.test`-Output der CLI und kein `extendsFrom` auf fremde Testkonfigurationen.
- Keine neue JUnit-Abhängigkeit im Maincode, nur um dort Assertions aus Tests aufzurufen. Hostprogramme verwenden die bereits vorhandene explizite Fehlerbehandlung.

Die Abhängigkeitsrichtung ist ausschliesslich **Verifikation → Produkt**. Compiler, Modell, CLI, LSP, Wasm-Bridge und Wasm-Backends dürfen weder zur Kompilierung noch zur Laufzeit vom neuen Modul abhängen. Auch deren Tests dürfen keine umgekehrte Abhängigkeit benötigen. Ein fehlender gemeinsamer kleiner Testhelfer ist kein Anlass für einen Dependency-Zyklus.

Ein intern erzeugtes Verifikations-JAR ist zulässig. Es wird nicht publiziert, nicht in SDK/CLI/LSP/Editor-Bundles eingepackt und nicht in TeaVM/Web-Image-Classpaths aufgenommen. Das gilt auch für das gegebenenfalls durch gemeinsame Konventionen erzeugte Sources-JAR.

### 3.2 Sichtbarkeit und Packages

Die im Referenzstand benannten Mainklassen aus `guru.interlis.cli.verification` wechseln grundsätzlich gemeinsam nach `guru.interlis.verification`. Diese flache Struktur bewahrt package-private Helfer und vermeidet eine unnötige Freigabe von Methoden. Fachliche Unterpackages sind für diesen Auftrag nicht erforderlich.

Testhelfer der ehemaligen Evidenztests bleiben zunächst Testcode unter `guru.interlis.verification.contracts`. Nur ein tatsächlich von ausführbaren Mainprogrammen benötigter Validator gehört in `src/main`. Kein reflexartiges Befördern sämtlicher Testhelfer in Produktionscode.

## 4. Mainklassen: verbindliche Migrationszuordnung

Die folgende Liste ist am lokalen Stand zu vervollständigen. Keine zusätzliche vorhandene Klasse darf durch fehlende Erwähnung verschwinden.

| Bestehende Klasse | Zielklasse | Zu bewahrender Vertrag |
|---|---|---|
| `HostChecks` | gleicher einfacher Name, neues Package | `Result`, Prozessstart, Timeouts, UTF-8, Pins, CLI-Pfade und Artefaktprüfung |
| `ProfileFingerprint` | gleicher Name | `commit(Path)`, `dirty(Path)`, `current(Path)` und Java/Node-Quellbindung |
| `CompilerCliProcesses` | gleicher Name | `main`, `REQUIRED`, `verify`, `verifyColorCases`, `stripSgr`, tatsächliche CLI-Fälle |
| `SdkProcesses` | gleicher Name | `main`, `check(Path)`, `expect(Path,List<String>,int)`, `version(JsonObject)` |
| `SdkArchiveCheck` | gleicher Name | SDK konsumieren/entpacken/prüfen; keine Verifikationsartefakte ausliefern |
| `PlatformProcesses` | gleicher Name | aktuelle JVM-/SDK-/CLI-Berichte an einen Kandidaten binden |
| `ConformanceEvidence` | gleicher Name | aktuelle externe Rohberichte und Projektbewertung verknüpfen |
| `CorpusExpectations` | gleicher Name | `load(...)`, `check(...)`, exakte Diagnose-/Negativfallverträge |
| `GeoCorpus` | gleicher Name | gepinnte kleine Korpusgrundlage, unveränderte Eingaben |
| `FullCorpus` | gleicher Name | gepinnte Korpusbereitstellung ohne Baseline-Umschreiben |
| `FullCorpusRun` | gleicher Name | JVM-Korpuslauf, korrekte Child-Aufrufe, unveränderte Auswahl/Budgets |
| `FullCorpusCold` | gleicher Name | Hilfsprozess; FQCN-Stringreferenzen explizit migrieren |
| `JvmBenchmark` | gleicher Name | Benchmark und `stable(...)`-Vergleich ohne veränderte Toleranzen |
| `EditorPressure` | gleicher Name | bestehender Langzeittest, nicht Bestandteil gewöhnlicher Unit-Tests |
| `ObservationPressure` | gleicher Name | Beobachtungs-/Queue-Prüfung mit unveränderten Grenzen |
| `ReferenceProcesses` | gleicher Name | Profile `profile`, `originals`, `views`, `units` und Artefaktpins |
| `RepositoryReferenceCheck` | gleicher Name | bestehende Repository-Referenzprüfung |
| `SemanticComparison` | gleicher Name | `main`, `call`, `project`, fachliche Vergleichsgrenzen unverändert |
| `PythonFreeCheck` | gleicher Name | `main`, `validate(...)`, vorhandene Pythonfreiheitsanforderung |
| weitere lokal vorhandene Klasse | gleicher Name, begründetes Ziel | im Inventar einzeln nachführen |

Keine grosse kosmetische Umbenennung dieser Programme. Insbesondere wird `ConformanceEvidence` nicht allein deshalb entfernt, weil das Wort „Evidence“ vorkommt. Es ist von der speziellen historischen G2-Testinfrastruktur zu unterscheiden.

### 4.1 `HostChecks`

Bestehende Methoden bleiben zunächst in Signatur und Verhalten erhalten:

```java
record Result(List<String> command, int exitCode,
              String stdout, String stderr, double durationMs) {}
static void require(boolean ok, String message);
static String hash(byte[] bytes) throws Exception;
static String hash(Path path) throws Exception;
static JsonObject read(Path path) throws IOException;
static void write(Path path, Object value) throws IOException;
static Path cache(Path root, String variable, String fallback);
static Path cli(Path root);
static Path java();
static void pin(Path path, String expected) throws Exception;
static void download(URI uri, Path target, String expected) throws Exception;
static Result run(Path root, List<String> command, Duration timeout) throws Exception;
static Result run(Path root, List<String> command, Duration timeout,
                  Map<String, String> environment) throws Exception;
static Result cli(Path root, List<String> arguments) throws Exception;
static Map<String, String> artifacts(Path root) throws Exception;
static void checkPayload(Result result, boolean rejected);
```

Die JSON-Helfer `s`, `rows`, `strings` bleiben bei tatsächlichen Aufrufern bestehen. `strictUtf8` bleibt privat. Prozessumgebung, Working Directory, vollständige stdout/stderr-Erfassung, Child-Prozessbeendigung und temporäres Cleanup werden nicht nebenbei verändert. Keine Umsetzung mittels Shellstring-Konkatenation.

`cli(Path)` darf an den kleinen Layouthelfer delegieren. Ein Wechsel des Hostpakets darf nicht den Pfad der **Produkt-CLI** ändern. Windows `.bat`, Leerzeichen und Unicode in Pfaden bleiben unterstützt.

### 4.2 Neuer kleiner Layouthelfer `VerificationLayout`

Zweck ist ausschliesslich, die durch diese Migration betroffenen Produkt-/Berichtspfade explizit und testbar zu halten. Kein generisches Pfad- oder Buildframework.

```java
final class VerificationLayout {
    VerificationLayout(Path repositoryRoot);
    Path repositoryRoot();
    Path compilerDistribution();
    Path compilerLauncher(boolean windows);
    Path junitDirectory(String module, String taskName);
    Path verificationUnitReports();
    Path verificationContractReports();
    Path rootReport(String relativeName);
}
```

Verträge:

- Root absolut und normalisiert; keine Abhängigkeit von `user.dir`, wenn ein Root übergeben wurde.
- `compilerDistribution()` bleibt `interlis-compiler-cli/build/install/interlis-compiler`.
- Unitberichte liegen unter `interlis-verification/build/test-results/test`.
- Vertragsberichte liegen unter `interlis-verification/build/test-results/contractTest`.
- Modul-/Tasksegmente sind jeweils einzelne zulässige Segmente, keine absoluten Pfade oder Traversierungen.
- `rootReport` erlaubt nur relative Pfade unter `build/reports`; keine Symlink-Schreibausbrüche zulassen.
- Alte Source-Set-Berichtspfade werden nicht als Fallback gesucht. Ein fehlender neuer Bericht bleibt ein Fehler.

Falls der lokale Code bereits einen passenden kleinen Layouthelfer besitzt, diesen erweitern statt duplizieren. Langzeitprüfungen müssen nicht flächendeckend auf das neue Objektmodell umgeschrieben werden; nur betroffene Verzeichniskenntnis zentralisieren.

### 4.3 `PlatformProcesses`: Prüfung extrahieren, nicht verwässern

`main(String[])` bleibt der Orchestrierungseinstieg. Extrahiere den bisher eingebetteten JUnit-Teil so weit, dass er mit kleinen Verzeichnisfixtures testbar ist. Vorgesehene interne Methoden/Typen:

```java
record JvmReportSummary(long tests, int suites,
                        Map<String, String> xmlHashes) {}
static JvmReportSummary inspectJUnitReports(Path root,
                                            List<Path> requiredDirectories)
        throws Exception;
static void verifyRequiredProfile(JvmReportSummary summary,
                                  /* kleiner expliziter Profilvertrag */ ...);
```

Die Platzhalterzeile ist ein Entwurfsvertrag, kein behaupteter kompilierbarer Patch: Verwende vorhandene geeignete Profiltypen oder einen kleinen `record`, nicht eine allgemeine Regelsprache. `inspectJUnitReports` prüft reguläre Dateien, XML-Identität, positive Fallzahlen, keine Fehler/Skips, sichere XML-Parserkonfiguration und Hashes. `main` bindet weiter SDK- und Compiler-CLI-Prozessergebnisse, Kandidaten-SHA, Dirty-Zustand und Quellfingerprint.

Mindestzahlen wie `1232` Tests/`229` Suites dürfen nicht blind beibehalten und nicht bequem auf null abgesenkt werden. Für die Migration gibt es eine **nachvollziehbare Delta-Rechnung**: verschoben, umbenannt, aufgeteilt, dedupliziert, historisch aus dem aktiven Profil entfernt, neu hinzugefügt. Entsprechende Java-/Node-Validatoren und Fixtures erhalten denselben aktuellen Profilvertrag. Die Vollständigkeit darf nicht ausschliesslich aus dem Bericht selbst abgeleitet werden.

Die alte `evidenceTest`-Suite wird nicht mehr vorausgesetzt. Die neue Unit-Suite wird genau einmal erfasst; die neue Vertragssuite wird in einem Profil erfasst, das `compilerContractCheck` tatsächlich ausgeführt hat. Eine kleine lokale Unit-Prüfung wird nicht zum vollständigen Plattformnachweis erklärt.

### 4.4 `ProfileFingerprint`

Behalte die vorhandenen Methoden und die aktuelle Sicherheitsbedeutung. Prüfe insbesondere:

- `interlis-verification/src`, dessen `build.gradle`, neue aktuelle Bindungsdateien und geänderte Gradle-Skripte sind enthalten, soweit sie das geprüfte Profil bestimmen.
- Keine Buildausgaben, Reviewlogs oder Testberichte werden als eigene Eingaben gehasht.
- Java und Node verwenden für dieselben gewollten Eingaben dieselbe Sortierung, Kodierung und Hashbildung.
- Der Referenzstand zeigt eine mögliche Unterschiedlichkeit der Auswahl rund um `frontend/diagram-ui/`. Inventar und Parität gezielt prüfen; nicht einfach annehmen, dass zwei ähnliche Implementierungen gleich sind.
- Keine Ausweitung zu einem neuen Fingerprintsystem. Einen bestätigten Widerspruch minimal und dokumentiert korrigieren; alte Nachweise danach nicht als identische neue Fingerprints ausgeben.

Führe einen gezielten Paritätstest auf einem gemeinsamen Fixture beziehungsweise einem geeigneten sauberen Worktree aus. Ein `git`-Fehler oder fehlende Eingabe darf nicht zu einem leeren erfolgreichen Fingerprint werden.

## 5. Zuordnung bisheriger `verificationTest`-Tests

| Testklasse | Ziel | Behandlung |
|---|---|---|
| `CompilerCliProcessesTest` | `interlis-verification/src/test`, neues Hostpackage | Fehlererkennung und Farbfallvalidator erhalten. |
| `HostChecksTest` | neues Verifikationsmodul | Prozess-/UTF-8-/Timeout-/Pfadtests erhalten. |
| `PythonFreeCheckTest` | neues Verifikationsmodul | Prüfung vorhandener Scanregeln erhalten. |
| `SemanticComparisonTest` | neues Verifikationsmodul | reine Vergleichslogiktests nicht mit vollständigem Wasm-Lauf verwechseln. |
| `FullCorpusInputContractTest` | neues Verifikationsmodul | Eingabepins/Inventarprüfungen ohne pauschalen Vollkorpuslauf. |
| `CorpusOuterNameContractTest` | neues Verifikationsmodul | kleiner echter Compiler-/CLI-Integrationsvertrag plus Negativprüfer; CLI nur als Testdependency. |
| `LegacyOidCompatibilityTest` | `interlis-compiler/src/test/java/guru/interlis/compiler` | reine Produktregression; `compile`, positive/negative OID-Fälle erhalten. |
| `LegacySourceMetadataTest` | Compiler-Testmodul, sofern lokale Prüfung reine Compilerabhängigkeit bestätigt | keine Abhängigkeit zurück auf Verifikation erzeugen; bei tatsächlicher CLI-Kopplung entsprechenden Teil separat erhalten. |
| `UnicodeWeightsTest` | `interlis-compiler/src/test/java/guru/interlis/compiler/internal` | alle Oracle-/Manipulationsfälle erhalten; Package-private-Zugriff bleibt innerhalb des Moduls. |

Die Productregressionen werden nicht als historische „Legacy“-Tests gelöscht. Im Namen bezeichnet „Legacy“ hier fachliche Kompatibilität, nicht entbehrliche Projektgeschichte.

`UnicodeWeightsTest` benötigt weiter `repo.root` und die gepinnten Unicode-Eingaben. Seine bekannte Prüfung von 100170 Normalisierungsvergleichen bleibt erhalten. Die bestehende Prüfung des `TextLengths.java`-Hashs wird in diesem Auftrag nicht ohne gesonderten Ersatz entfernt; sie gehört nicht zur pauschalen Bereinigung eingefrorener G2-Arbeitsstände.

Tests, die nach dem Umzug zusätzlich einen gemeinsamen Helper bräuchten, werden nicht mit einer Rückabhängigkeit `compiler:test -> verification:main` repariert. Kleine reine testlokale Helfer sind dort vorzuziehen.

## 6. Entscheidung zu `evidenceTest`

**Es gibt künftig weder dieses Source-Set noch einen gleichnamigen neuen Sammeltest.** Daraus folgt nicht, dass alle seine Assertions entfallen. Massgeblich ist die methodengenaue Disposition in `EVIDENCE_DISPOSITION.md`, die Codex in V0 mit dem lokalen Code vervollständigt.

Drei Kategorien:

1. **Dauerhafte Prüflogik:** beispielsweise kaputte JUnit-XMLs, fehlende Rohberichte, falsche Versionen, unvollständige Plattformmatrix, Pfadmanipulation, falsche SDK-Exitcodes. Als gewöhnliche Unit-/Contracttests erhalten oder in einen bereits aktiven äquivalenten Prüfer integrieren.
2. **Aktuelle fachliche Vertrags-/Abdeckungsbindung:** beispielsweise echte Testmethoden zu gültigen Compileranforderungen, aktuelle normative Entscheidungsfreigaben, reale positive/negative Kontrollen. Separat ausführbar erhalten; bei Bedarf mit nachvollziehbarer aktueller Bindung statt historischer G2-Zustandsfelder.
3. **Historische Arbeitsstandssicherung:** eingefrorene Zahlen, alte Planungszustände, alte Reviewer-/G2-Abnahmebestände oder der Nachweis, dass ein damaliger Quelltext noch exakt gleich formatiert ist. Diese sind nicht automatisch Anforderungen an jeden neuen Build. Die Originalbelege bleiben unverändert; reine historische Validatoren dürfen nach dokumentierter Entkopplung aus dem aktiven Code entfallen.

**Unbekannt ist keine Löschfreigabe.** Unklarer Nutzen wird zunächst isoliert in `contractTest` erhalten, mit präziser offener Zuordnung; ein endgültiges erfolgreiches Paket verlangt eine begründete Entscheidung, keine pauschale Liste „später prüfen“.

### 6.1 Testgruppen und Aufgaben

| Aufgabe | Auswahl | Zweck | Automatisch in `check`? |
|---|---|---|---|
| `:interlis-verification:test` | ungetaggte normale Tests, `excludeTags 'compiler-contract'` | schnelle Tests der Werkzeuge/Validatoren, kleine lokale Integration | Ja |
| `:interlis-verification:contractTest` | `includeTags 'compiler-contract'` | aktuelle Bindungs-/Abdeckungs-/Normverträge mit ihren konkret nötigen Inputs | Nein |
| `:compilerContractCheck` | Root-Aggregation von `contractTest` und seinen notwendigen Produzenten | bewusste aktuelle Vertragsabnahme | Nein, aber in den entsprechenden Release-/Plattformprofilen |

Keine JUnit-`@Disabled`, `Assumptions` oder Erfolgssprünge bei fehlenden Pflichtinputs. Tests werden über Taskauswahl getrennt, nicht durch Laufzeit-Skips. Die leere Tagselektion muss mit einem aussagekräftigen Fehler auffallen.

Ein kurzer Beispielausschnitt für den Zielbuild, am vorhandenen Gradle-Stand zu verifizieren:

```groovy
tasks.named('test', Test) {
    useJUnitPlatform { excludeTags 'compiler-contract' }
    systemProperty 'repo.root', rootProject.projectDir.absolutePath
}

tasks.register('contractTest', Test) {
    testClassesDirs = sourceSets.test.output.classesDirs
    classpath = sourceSets.test.runtimeClasspath
    useJUnitPlatform { includeTags 'compiler-contract' }
    systemProperty 'repo.root', rootProject.projectDir.absolutePath
    // Explizite Eingaben und nur die benötigten Produzenten ergänzen.
    // Separate XML-/HTML-Verzeichnisse; keine Überlappung mit `test`.
}
```

`contractTest` wird **nicht** zusätzlich an `:interlis-verification:check` gehängt. Der Root-`check` wird damit von historischen PDF-/G2-Arbeitsstandskontrollen befreit, nicht von Produktregressionen oder Validator-Unit-Tests.

### 6.2 Aktuelle Compilerassertions ausführen

Die bisherige Aufgabe `:interlis-compiler:g2AssertionTest` wird nur soweit ersetzt, wie die nach dem Audit weiterhin benötigte aktuelle Bindung dies verlangt.

Bevorzugter Zielname: `contractAssertionsTest` in jedem tatsächlich benötigten Produktmodul. Diese Tasks verwenden dessen **bestehendes** `sourceSets.test`, nur die explizit gebundenen Methoden und eigene Berichtspfade. Keine neuen Source-Sets. Es gibt keine pauschale Abhängigkeit auf alle Tests aller Module.

Vorgaben:

- Die Auswahl ist die deduplizierte Vereinigung der weiterhin notwendigen aktuellen Bindungen; aus zwei alten Nachweisketten wird nicht automatisch eine doppelte Ausführung.
- Leere, unbekannte, doppelte oder ungültige Bindungen werden abgelehnt.
- Explizite JUnit-XML-Identitäten und erforderliche parametrisierte Aufrufe bleiben überprüfbar; nicht mehr aus einer universellen Annahme „ParameterizedTest = zwei Fälle“ ableiten.
- Ein aktueller Lauf erhält eine Run-ID/Startmarkierung und Hashliste seines wirklichen Outputs. Ein alter XML-Ordner genügt nicht.
- Wenn Frische zwingend ist, sind genau diese Produzenten nicht aus Testcache/Up-to-date-Ergebnissen als frischer Lauf zu übernehmen. Keine globale Abschaltung sämtlicher Buildcaches.
- `contractTest` liest nur seine konkret erzeugten Berichte. Keine Schleife, in der ein Test erneut den Root-`check` startet.
- Alte `g2-*`-Outputnamen entfallen bei entfallener aktiver Verwendung. Historische Dokumente mit diesen Namen bleiben historische Dokumente.

Eine kleine Groovy-Map/JsonSlurper-Auswahl im vorhandenen Stil reicht. Keine Compiler-Testdiscovery-Engine, keine Runtime-Generierung neuer JUnit-Tests und keine rekursive Gradle-Orchestrierung aus einem Test heraus.

### 6.3 Aktuelle Bindungsdaten versus historische Daten

Nicht jedes Dokument unter `docs/evidence` ist automatisch nur historisch: Einige Dateien werden noch als aktive Fixture oder Erwartung konsumiert. Phase V0 bestimmt die wirklichen Verbraucher.

Historische JSON/PDF-/Textbelege, Berichte, gepinnte Korpora und frühere Hashes werden nicht global umgeschrieben. Falls ein aktuell benötigter Test durch einen Umzug einen neuen Locator oder eine neue Quellbindung benötigt:

- die genaue Alt-/Neu-Zuordnung und den Grund festhalten;
- nur den tatsächlich aktuellen Bindungsvertrag ändern oder eine kleine ausdrückliche aktuelle Bindungsdatei unter `testdata/verification` anlegen;
- den Originalbeleg samt ursprünglichem Hash unverändert lassen;
- keine zweite vollständige Kopie der historischen G2-Dokumentation als „aktuell“ anlegen;
- keine automatische `sourceSha256`-/Offset-/Fallzahl-Neugenerierung über sämtliche Dokumente;
- keine beliebigen Pfadaliase oder Fallbacksuche nach ähnlich benannten Klassen einführen.

Aus einem geänderten Locator folgt keine neue fachliche Freigabe. Änderungen an fachlichen Expectations, an normativen Ausnahmen oder an Nutzerentscheidungen sind nicht beauftragt.

## 7. Methodenverträge für verbleibende Vertragsprüfungen

Alle hier vorgesehenen Helfer sind klein und paketprivat. Wenn ein vorhandener aktiver Java-/Node-Validator die jeweilige Anforderung bereits vollständig erfüllt, wird dessen Testabdeckung erweitert statt ein zweiter fachlicher Validator gebaut. In diesem Fall enthält die Dispositionsmatrix den exakten Ersatz mit Negativfallnachweis.

### 7.1 `ContractFiles` – aus `EvidenceFiles` nur die benötigten Teile

Ziel im Testpackage `guru.interlis.verification.contracts`. Keine globale `static ROOT`, die schon beim Klassenladen bei fehlender Systemproperty fehlschlägt. Root explizit über Konstruktor, oder ein kleiner zentraler Testsetup-Aufruf mit verständlicher Fehlermeldung.

```java
final class ContractFiles {
    ContractFiles(Path root);
    Path inside(String relative) throws IOException;
    String text(String relative) throws IOException;
    JsonObject json(String relative) throws IOException;
    void requireHash(String relative, String sha256) throws IOException;
    static String sha256(byte[] bytes);
    static String codePointSlice(String text, int start, int end);
    // rows/strings/ids nur behalten, soweit reale Aufrufer existieren.
}
```

`inside` lehnt absolute Pfade, `..` und Symlinkausbrüche ab. `codePointSlice` behält die historische Unicode-Codepoint-Semantik, **nicht** Java-UTF-16-Indexierung. Ein allgemeiner `interlis-cli -> interlis-compiler-cli`-Alias gehört nicht in den neuen aktuellen Resolver; noch benötigte historische Locatorübersetzungen müssen einzeln begründet und begrenzt sein.

`STEPS`, G2-Dateinamenpräfixe, historische `acceptance`-Statusregeln und spezielle JSON-Hashserialisierung werden nur mit einem verbleibenden konkreten Verbraucher übernommen.

### 7.2 `JUnitReportAssertions` – aus `AssertionEvidence`

```java
record TestInvocation(String suite, String displayName) {}
static Set<TestInvocation> readVerifiedXml(Path xml, String expectedSha256,
                                           Instant notBefore) throws Exception;
static void requireFreshInvocations(Set<TestInvocation> expected,
                                    Path reportsDirectory,
                                    Instant started,
                                    Map<String, String> expectedHashes)
        throws Exception;
```

Bewahren: sichere XML-Verarbeitung, passende Suite-/Klassen-/Fallidentitäten, Hashbindung, vollständige nichtleere Reports, Fehler-/Skip-Ablehnung, Frische und Ablehnung zusätzlicher fremder oder fehlender Dateien. Zeitstempelauflösung und Dateisystempräzision realistisch testen; kein neues fragiles Nanosekundenversprechen.

Der bisherige `recorded(JsonObject)` mischt alte Teilabnahmestände mit aktuellen Methoden. Diese Mischung auflösen: historischer Bericht bleibt historisch; aktuelle Methodenbindung wird aus dem aktuellen Vertragsmanifest geprüft. Keine dauerhafte Forderung, dass die heutige vollständige Abnahme noch `extent=PARTIAL` und `remainingGap` tragen müsse.

### 7.3 `CompilerCoverageContractTest` – aus `CompletionLinksTest`

Tag `compiler-contract`. Kernmethoden aufteilen:

```java
static void validateCoverage(JsonObject contract);
static void validateSourceBindings(JsonObject contract, ContractFiles files)
        throws Exception;
static void validateExecutedAssertions(JsonObject contract,
                                        /* verifizierte aktuelle Reports */ ...)
        throws Exception;
```

Die aktuelle Zuordnung von relevanten Anforderungs-IDs, Grammatikzweigen, Builtins und öffentlichen Beobachtungen zu **wirklichen** Tests bleibt erhalten. Ein Link beweist nicht allein die Richtigkeit einer Semantik. Fehlende, fremde und doppelte IDs bleiben Fehler. Quellen und tatsächliche Ausführung sind getrennt prüfbar.

Die Negativtests für entfernte Bedingungen, verlorene Zweige, fehlende Builtins, erfundene Methoden und falsche Quellenhashes bleiben in der normalen Unit-Gruppe, soweit sie mit kleinen Fixtures arbeiten. Nur der Test gegen den echten Repository-/Runzustand trägt den Contract-Tag. Klassenweite Tags dürfen diese Negativtests nicht versehentlich aus `test` entfernen.

### 7.4 `RuntimeReportContractTest` / SDK-Pfadprüfung

Aus `RuntimeReports` und seinen Tests bleiben nur aktive Formate bzw. durch aktuelle Validatoren nicht ersetzte sinnvolle Gegenproben. Bewahre gegebenenfalls folgende Methodenfamilien unter einem präziseren Namen `RuntimeReportAssertions`:

`validateReport(key,value,profile)`, `validatePortable(...)`, `validateReference(...)`, `validateProject(...)`, `validatePair(...)`, `validateSdk(...)`.

Keine weitere neue Allzweck-Reportbibliothek. `reference/project/pair` dürfen nicht still mit P3-`tools/release/evidence.cjs` gleichgesetzt werden: konkrete Unterschiede der akzeptierten Formate und Schutzfälle nachweisen.

SDK-Pfadprüfungen bleiben hostunabhängig: Windows-Laufwerkpfad, Unix-Pfad, UNC-Pfad sowie Ablehnung von relativen und laufwerksrelativen Pfaden. Das Einlesen eines Windows-Berichts auf macOS darf nicht `Path.isAbsolute()` des Hostsystems zur einzigen Wahrheit machen.

Reine Validator-Unit-Tests bleiben unter `test`; sie zertifizieren keine tatsächlich gestartete Browser- oder Plattformmatrix.

### 7.5 Normative Entscheidungen und Normreferenzen

Aktuelle Regeln zur genehmigten Änderung von Expectations und zur vollständigen Zuordnung von positiven/negativen Kontrollen bleiben erhalten, vorzugsweise als `NormativeDecisionsContractTest` mit kleinen Unit-Gegenproben. Historische Zustände wie `USER_DECISION_PENDING` sind dagegen nicht automatisch aktuelle Anforderungen.

`NormEvidence.pages` ist **kein** PDF-Parser, sondern bindet aufbewahrte Textextraktionen an ein Original-PDF. Das bleibt in der Dokumentation klar. Keine neue Python-/OCR-/PDF-Extraktionspipeline einführen.

Ein verbleibender aktueller Normnachweis darf seine Hash-/Zitatbindung nicht verlieren. Aber die Vollkontrolle sämtlicher damaliger Paragraphen-, Crosswalk- und Planungszahlen muss nicht bei jeder Unit-Test-Ausführung erfolgen. Ist sie ausschliesslich historische Bestandskontrolle, wird ihr aktiver Code mit dokumentierter Begründung entfernt. Originaldateien, Zitate und Herkunft verbleiben unverändert.

Normdownloads erfolgen nur durch einen ausdrücklich aufgerufenen notwendigen Vorbereitungstask, nicht während Gradle-Konfiguration und nicht aus einem Unit-Test. Ein frischer normaler `check` benötigt dadurch keine Norm-PDF-Downloads. Fehlende Inputs eines tatsächlich angeforderten aktuellen Contracttests bleiben Fehler, kein erfolgreicher Skip.

## 8. Gradle-Orchestrierung und zyklusfreier Taskgraph

### 8.1 Verteilung der Buildlogik

`interlis-verification/build.gradle` besitzt Dependencies, Testkonfiguration und JavaExec-Hosttasks. `gradle/host-verification.gradle` enthält nur noch schlanke Root-Delegation und nötige projektübergreifende Verknüpfung; keine fremden Source-Sets erzeugen. Eine zusätzliche kleine `gradle/verification-contracts.gradle` ist nur für tatsächlich benötigte Testproduzenten in mehreren Produktmodulen gerechtfertigt. `gradle/evidence.gradle` entfällt, sobald seine verbleibenden Aufgaben ausdrücklich übernommen sind.

Vorhandene stabile Root-Kommandos bleiben erreichbar: `sdkProcessCheck`, `sdkArchiveCheck`, `compilerCliCheck`, `platformCheck`, `conformanceEvidence`, `referenceCheck`, `repositoryReferenceCheck`, `semanticComparison`, `geoCorpus`, `fullCorpus`, `fullCorpusJvm`, `jvmBenchmark`, `editorPressure`, `observationPressure`, `pythonFreeCheck` sowie die bestehenden Referenz-Unterprofile.

Sie delegieren an `:interlis-verification:<task>`. Veraltete **CLI-lokale** Taskpfade werden in aktuellen Skripten/Anleitungen geändert. Keine dauerhafte zweite Ausführung unter alten und neuen Tasknamen. Historische Kommandos in unveränderten Abnahmebelegen werden nicht umgeschrieben.

### 8.2 Wesentliche Kanten

```text
:check
  -> Produktmodul :check / :test
  -> :interlis-verification:test
  -> :pythonFreeCheck (weiterhin nur dessen vorhandener Zweck)

:sdkProcessCheck
  -> :interlis-verification:classes
  -> :interlis-compiler-cli:installDist
  -> tatsächlicher SDK-Prozesslauf

:sdkPackage
  -> :interlis-compiler-cli:installDist
  -> :sdkProcessCheck

:sdkArchiveCheck
  -> :sdkPackage
  -> :interlis-verification:sdkArchiveCheck

:compilerContractCheck
  -> nur notwendige <produkt>:contractAssertionsTest
  -> gegebenenfalls explizite erforderliche gepinnte Norminputs
  -> :interlis-verification:contractTest

:platformCheck
  -> :check
  -> :compilerContractCheck (soweit aktuelles vollständiges Profil)
  -> :compilerCliCheck
  -> :sdkArchiveCheck
  -> :interlis-lsp:installDist
  -> :interlis-verification:platformCheck
```

Die Darstellung beschreibt Abhängigkeiten, nicht zwingend zusätzliche doppelte Wrapper-Aufgaben. Wähle einen eindeutigen Besitzer je Ausführung. Kein `verification:check -> platformCheck -> root:check -> verification:check`-Zyklus. Kein `contractTest -> root:check`. Kein `sdkPackage -> sdkArchiveCheck -> sdkPackage`.

`mustRunAfter` bleibt eine Reihenfolgebedingung und wird nicht aus Bequemlichkeit in eine neue `dependsOn`-Vollprüfung umgewandelt. Nach Paket-/Taskumzug werden alle relativen Projektpfade vollständig qualifiziert.

### 8.3 Browser, Release und SDK

- `prepareBrowserHarness` muss weiterhin den tatsächlich erzeugten SDK-Versionsbericht erreichen; der Root-Aufruf kann stabil bleiben.
- `semanticComparison` behält seine ausdrücklichen TeaVM-/Node-Abhängigkeiten. Ein reiner Unit-Test seiner Vergleichslogik startet diese Backends nicht.
- `releaseCheckP3`, `releaseEvidenceCheck`, `platformCheck`, `.github/workflows/p3-platforms.yml`, `tools/release/evidence.cjs`, `test-fixtures.cjs`, `platform-evidence.cjs`, `tools/editor/bundle.cjs` und alle weiteren tatsächlichen Verbraucher werden geprüft.
- SDK-/CLI-/LSP-Verzeichnisstrukturen und Produktartefakte bleiben unverändert. Nur Prüfberichtspfade/Prüfmodulartefakte ändern sich.
- Die Umstellung von Berichtspfaden darf alte erfolgreiche Berichte nicht unbemerkt weiterverwenden. Negativtest: nur alter XML-Pfad vorhanden, neuer fehlt → Fehler.
- `g2EvidenceTest` wird in aktiven Profilen durch die konkret weiterhin nötige `compilerContractCheck`-Kante ersetzt und danach entfernt. Kein Dummy-Erfolgstask.

## 9. Nachweise, Dateiinventare und Testsicherheit

Die Migration selbst liefert vier kleine versionierbare Arbeitsnachweise im Auftragsordner:

1. `SOURCESET_INVENTORY.md`: jede Quelldatei, Testklasse, Methoden-/Parametergruppen, Dependencies, Verbraucher und Ziel.
2. `EVIDENCE_DECISIONS.md`: jede betroffene Evidenz-Testmethode, Einordnung, Entscheidung, Ersatz und Nachweis.
3. `TEST_LEDGER.md`: tatsächlich gelaufene gezielte Prüfungen sowie begründete Wiederverwendung/Nichtausführung.
4. `STATUS.md`: Phasenzustand und Kandidaten-SHA; unabhängigem Urteil nicht vorgreifen.

Keine künstliche Erfolgsgeschichte aus Füllzahlen. Relevante Rohlogs und Reports bleiben ausserhalb versionierter Quellen unter einem eindeutigen Arbeits-/Reviewpfad. Tests und Nachweise vor einem Commit werden nicht nachträglich auf einen neuen SHA umetikettiert.

Jede Entfernung einer Testmethode besitzt genau eine begründete Kategorie: echte Doppelung mit konkret erhaltenem Ersatz, historischer Kontrollzweck ohne aktiven Verbraucher oder durch andere aktuelle Prüfung vollständig abgedeckte Anforderung. „Alt“, „G2“, „sieht kompliziert aus“, „Migration sonst schwierig“ und „grüner Build“ reichen nicht.

## 10. Abnahmekriterien

| ID | Verbindliche Abnahme |
|---|---|
| VM-01 | Neues internes Gradle-Modul mit nur `main`/`test`; keine Jenesis-/JPMS-/Versionsmigration. |
| VM-02 | Alle drei alten CLI-Source-Sets samt Konfiguration, Taskpfaden und aktiven Verweisen entfernt. |
| VM-03 | Alle Hostprogramme und Child-FQCNs korrekt migriert; Root-Kommandos weiterhin sinnvoll erreichbar. |
| VM-04 | Keine umgekehrte Produktdependency; keine Verifikationsklassen/JARs in SDK, CLI, LSP, Editor, Wasm. |
| VM-05 | Direkte Compilerregressionen korrekt zurückgeführt, insbesondere Unicode und OID; keine Sichtbarkeitsaufweichung. |
| VM-06 | `evidenceTest` methodengenau entschieden; weder blindes Kopieren noch pauschales Löschen. |
| VM-07 | Historische Originalbelege unverändert; aktuelle Bindungsmigrationen einzeln nachvollziehbar. |
| VM-08 | Aktuelle fachliche-/Coverage-/Freigabeverträge und nötige Negativfälle erhalten oder belegt ersetzt. |
| VM-09 | Normale Tests und `compiler-contract`-Tests disjunkt; keine erfolgreichen Skips oder leere Selektion. |
| VM-10 | `check` startet keine historischen PDF-/G2-Vollprüfungen; explizite Profile behalten notwendige Gates. |
| VM-11 | Taskgraph zyklusfrei; relative `installDist`-/Root-/Ordering-Kanten richtig migriert. |
| VM-12 | Berichtspfade, Profile, Java-/Node-Prüfer und CI konsistent; Testzahlen mit Delta-Nachweis statt Absenkung. |
| VM-13 | Frische, Vollständigkeit, Hash-/Kandidatenbindung und Manipulationsablehnung bleiben wirksam. |
| VM-14 | Java-/Node-Fingerprints berücksichtigen neue Prüfquellen konsistent, ohne Nachweis-Selbstreferenz. |
| VM-15 | Gezielte reale CLI-/SDK-Integration erfolgreich; keine Behauptung nicht ausgeführter Plattform-/Browserläufe. |
| VM-16 | Aktive Doku/AGENTS-Verweise bereinigt; unbenutzte Hilfsklassen/Fixtures/Builddateien entfernt. |
| VM-17 | Jede Implementierungsphase hat ein erfolgreiches eigenes Testgate; keine unabhängigen Reviews zwischen Phasen. |
| VM-18 | Finaler exakter Commit durch OpenCode 2.x + DeepSeek Flash 1017 unabhängig geprüft; Findings-/Recommit-/Review-Schleife erfolgreich. |

## 11. Quellenbasis

Die Pfade im Text beziehen sich auf den oben genannten Referenzcommit. Konkrete Kernquellen:

- `gradle/host-verification.gradle`, `gradle/evidence.gradle`, `build.gradle`.
- Alle Dateien in den drei betreffenden CLI-Source-Sets; besonders `HostChecks`, `PlatformProcesses`, `ProfileFingerprint`, `UnicodeWeightsTest`, `CorpusOuterNameContractTest`.
- `AssertionEvidence(Test)`, `CatalogueEvidence(Test)`, `CompletionLinksTest`, `EvidenceFiles`, `NormEvidence(Test)`, `RuntimeReports(Test)`, `RuntimeReportPathsTest`.
- [Gradle: Java-Tests und JUnit-Tagfilter](https://docs.gradle.org/current/userguide/java_testing.html).
- [Gradle: Java-Plugin und Source-Sets](https://docs.gradle.org/current/userguide/java_plugin.html).

Die Spezifikation formuliert Zielverträge. Neue Methodennamen und Klassen sind Entwurfsvorgaben, keine Behauptung, dass entsprechender Code bereits existiert oder getestet wurde. Lokale Abweichungen vom Referenzcommit werden in V0 geprüft, nicht durch Zurücksetzen beseitigt.
