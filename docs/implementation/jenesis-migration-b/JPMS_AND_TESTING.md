# JPMS, Modulpfad und White-Box-Tests

## 1. Ziel: echte Module, keine blossen Deskriptordateien

Alle zehn eigenen JVM-Projekte werden mit expliziten Deskriptoren gebaut. Der reale Produkt-javac-Aufruf verwendet passende Moduleingaben und erzwingt Lesbarkeit/Exporte. `jar --describe-module`, tatsächliche javac-Args, ein benannter SDK-Consumer und negative Zugriffsproben weisen das nach. Nur `module-info.class` nachträglich in eine classpathkompilierte JAR zu legen, erfüllt den Auftrag nicht.

JARs behalten ihre bisherige Rolle als konsumierbare Java-Artefakte. Modulname, GAV, Dateiname und Version sind unterschiedliche Identitäten und werden ausdrücklich zugeordnet. Normale Classpathverbraucher lesen weiterhin dieselben Produktbytes; es wird nicht zusätzlich eine „legacy“-JAR pro Modul produziert.

Keine eigenen Produktions-Split-Packages. Der bisherige Packagebesitz ist zu prüfen, einschliesslich generierter ANTLR-Klassen und Ressourcen. Package-intern über zwei eigene Module verteilte Typen werden nicht mit `--add-exports` kaschiert. Tatsächliche Zyklus-/Split-Probleme zuerst nachweisen; keine grossflächige Umstrukturierung auf Verdacht.

## 2. Modulgraph und Deskriptorpolitik

Ausgangspunkt der direkten eigenen Dependencies:

| Modul | Direkte fachliche Kandidaten, in B0 anhand Imports/API abzugleichen |
|---|---|
| model | keine eigenen |
| compiler | model, ANTLR-Runtime |
| workspace | compiler, model |
| repository | workspace, model; Java-HTTP/XML nach wirklicher Verwendung |
| projection | compiler, model |
| documentation | model, projection; compiler nicht allein wegen Tests ins Hauptmodul |
| wasm-bridge | workspace, compiler, projection, model |
| compiler-cli | compiler, workspace, repository, projection, model |
| lsp | workspace, repository, compiler, projection, documentation, model; LSP4J/JSON-RPC/Gson nach realem Graph |
| verification | compiler, model, workspace, repository, wasm-bridge, Gson; CLI-Testdependency bleibt testseitig |

Für jeden Deskriptor eine kleine Tabelle in DECISIONS führen: requires, Grund, transitive ja/nein, öffentliche Signatur, exports, opens, uses/provides und erforderliche JDK-Module.

- `requires transitive` nur, wenn Typen des anderen Moduls tatsächlich zur veröffentlichten API gehören oder ein verbindlicher Consumervertrag dies verlangt. Nicht jede transitive Mavenabhängigkeit ist ein transitive requires.
- `requires static` nur für tatsächlich optionale/compile-only Lesbarkeit. Es ist kein Mittel, eine notwendige Runtimeabhängigkeit zu verstecken.
- Öffentliche API-Packages exportieren, intern/ANTLR-Implementierung nicht pauschal exportieren.
- Keine `open module`-Deklaration. Reflectionzugriff nur für konkrete Packages an konkret erforderliche Laufzeitmodule, etwa Gson bei tatsächlich reflektierten eigenen Typen.
- Kein Produktions-`--add-exports ...=ALL-UNNAMED`, `--add-opens ...=ALL-UNNAMED`, `--add-reads ...=ALL-UNNAMED` oder `ALL-MODULE-PATH,ALL-DEFAULT` als Ersatz für einen korrekten Descriptor.
- Services via `uses`/`provides` deklarieren, wo der Code sie verwendet. Classpath-kompatible `META-INF/services` bleiben vorhanden bzw. werden aus dem einen Vertrag erzeugt. Beide Wege testen.
- `java.base` implizit. `java.net.http`, `java.xml`, `java.logging`, `jdk.compiler` etc. anhand realer Nutzung, jdeps und Laufzeitproben bestimmen; Testserver `jdk.httpserver` nicht ungeprüft in die Produktruntime aufnehmen.

Beispiel **als Entwurf**, kein fertiger nachgewiesener Deskriptor:

```java
module guru.interlis.compiler {
    requires transitive guru.interlis.model;
    requires org.antlr.antlr4.runtime;
    exports guru.interlis.compiler;
    exports guru.interlis.compiler.query;
    exports guru.interlis.syntax;
    exports guru.interlis.format;
}
```

Die endgültige Exportliste wird gegen Kapitel 16/APIregister und wirkliche Consumer geprüft. Aktuell nicht existierende Packages nicht blind eintragen; vorhandene weitere öffentliche Vertragsbereiche nicht übersehen.

## 3. Fremdbibliotheken

Alle gepinnten Runtime-JARs mit `jar --describe-module`, Manifest und Packageinventar untersuchen. Report pro Dependency: Koordinate, SHA, echter/automatischer Descriptor, tatsächlicher Name, exports/opens/services, Split-Packages und Runtimebeobachtungen. Namen von ANTLR, LSP4J, JSON-RPC und Gson werden aus den **tatsächlichen** Dateien gelesen.

Ein automatisches **Fremdmodul** auf dem Module-Path ist für B zulässig, wenn es stabil identifiziert, gehasht und im endlichen Inventar ausgewiesen ist. Eigene Module dürfen nach B5 nicht automatisch bleiben. Fehlender stabiler Name oder Split-Package bei Fremdlibs ist ein konkret zu lösender Modulgraphbefund, keine Erlaubnis für ein Upgrade oder globales Öffnen.

B verlangt nicht, sämtliche externen JARs für eine vollständige jlink-Anwendungsclosure umzuschreiben. Keine automatische `modules.properties`-Transformation ohne belegten Bedarf und ausdrückliche Disposition. Der bevorzugte Runtimeweg linkt notwendige JDK-Module und hält unveränderte Anwendungs-JARs daneben; siehe EDITOR_AND_RUNTIME.

## 4. Verbindliche Testentscheidung

### 4.1 Zwei verschiedene Fragen

1. **Fachliche/White-Box-Regressionen:** Verhalten und interne Invarianten erhalten, einschliesslich package-private Zugriffe.
2. **Modulgrenzen:** Ein externer benannter Consumer darf nur das deklarierte API lesen; produktive Reflection und Services funktionieren ohne Testfreigaben.

Eine einzige Teststrategie beantwortet nicht automatisch beides. Tests mit zusätzlichen Modulfreigaben beweisen keine unveränderte Produktionskapselung; reine Black-Box-Tests ersetzen nicht ohne Weiteres vorhandene interne Assertions.

### 4.2 Bestehende Tests nicht massenhaft umschreiben

Die vielen gemeinsamen Test-/Produktpackages bleiben grundsätzlich erhalten. Keine pauschale Umbenennung aller Testpackages, keine künstlichen public-Bridgeklassen im Produkt, keine breit angelegte Reflectionmigration nur wegen des Builders. Produktinternas und Testintent bleiben unverändert.

**Standardstrategie für diese vorhandenen White-Box-Tests:** testseitiges JDK-`--patch-module` mit wenigen expliziten JUnit-/Testzugriffsfreigaben. Das ist ein temporärer Testprozess, keine Änderung der gelieferten JAR und kein Vendorpatch. Jenesis bietet dies nach den gelesenen Quellen nicht als eingebauten Standard an; die schmale Integration muss in B0 belegt werden.

Benannte **Black-Box-Testconsumer** verwenden getrennte Testpackages und ungepatchte Produkt-JARs. Neue Boundarytests möglichst so ausführen. Tests, die bereits ohne Internas über öffentliche APIs arbeiten, brauchen nicht zwangsläufig umgezogen zu werden; der zusätzliche kleine Consumer prüft den Modulvertrag unabhängig.

### 4.3 `JpmsTestRun` — enger Testadapter, kein zweiter Produktbuild

Bevorzugt bestehende öffentliche Jenesis-Hooks/Toolsteps verwenden. Falls native Testschritte ihre benötigten Args/Inputs nicht korrekt abbilden, ist eine begrenzte Projektintegration erlaubt, die **ausschliesslich Tests** mit JDK-javac kompiliert und den vorhandenen gepinnten JUnit Platform Launcher startet. Produktklassen werden niemals neu kompiliert, kopiert als Quellen eingebunden oder im Test-JAR ersetzt.

Der Adapter läuft als echter inputgehashter Jenesis-Schritt. Er darf nicht ausserhalb des Buildgraphs einen zweiten globalen Cache oder Runner implementieren. Die JUnit-Engine bleibt Besitzer von Discovery und Ausführung; der Adapter darf keine PASS-Reports aus ausgewählten Methodennamen erzeugen.

Vorgeschlagene Methoden:

```java
TestPlan plan(ModuleResult product, TestSelection selection, TestInputs inputs);
void verifyNoProductShadowing(TestPlan plan);
List<String> compileArguments(TestPlan plan);
List<String> runtimeArguments(TestPlan plan);
TestRun execute(TestPlan plan, Path freshOutput);
VerifiedTestRun verifyReports(TestPlan plan, TestRun actualRun);
```

`TestPlan` erfasst expliziten Produktmodulnamen, Produkt-/Dependency-JARs, tatsächliche Testquellen/-ressourcen, JUnit-/Testonly-Dependencies, endliche zusätzliche reads/exports/opens, Auswahl und Inputs. Kein generisches „open everything“.

**Kompilierung:** Produktmodule auf dem Module-Path, Testquellen patchen genau ihr zugeordnetes Produktmodul. JUnit/Testonly-JARs können auf dem Classpath liegen; dann ist ein ausdrückliches testseitiges `--add-reads <tested-module>=ALL-UNNAMED` zulässig. Keine Produkt-JAR gleichzeitig auf Classpath und Module-Path. Weitere eigene Testdependencies bleiben benannte Module und bekommen nur testseitig erforderliche Lesbarkeit/Exports.

**Ausführung:** Das unveränderte Produkt-JAR bleibt auf dem Module-Path; nur kompiliertes Test-JAR/-Verzeichnis wird per `--patch-module` ergänzt. JUnit-Reflection erhält konkrete Testpackages. Klassen werden durch tatsächliche JUnit-Selektion gefunden; ein Classpathscan, der gepatchte Modulklassen nicht sieht, darf nicht still null Tests liefern. Ein Runner auf dem Classpath kann explizite ausgewählte Klassen nutzen, sofern die Probe zeigt, dass diese in dem erwarteten benannten Modul geladen werden.

**Discovery-/Referenzgate:** Der Testlauf muss einen package-private Zugriff, parametrisierte Fälle, eine selektierte Methode, eine absichtliche Assertionverletzung, eine leere Auswahl und einen Fremdmodul-Nichtzugriff zeigen. Auch im Testszenario muss `SomeProductClass.class.getModule().isNamed()` wahr sein. Ein falscher zusätzlicher Main-Classpath darf diesen Test nicht grün machen.

**Kollisionsschutz:** Testpatch darf keine Produkt-FQCN, `module-info.class`, produktive Service-/Schemaressource oder Main-Class ersetzen. Baselinebedingt notwendige reine Testressourcen-Overlays sind einzeln zu begründen und dürfen keinen ungeprüften Produktinput verdecken. Generate-ANTLR bleibt main-only, keine Parserkopie in Testoutputs.

**Lifecycle:** Report-/Fixtureinputs invalidieren tatsächliche Tests. Klassen-/Ressourcenpatching bleibt ausserhalb von Produktstaging. False/Null-Reports, fehlende Parameterinvocations, nicht erlaubte Skips und Runnerfehler scheitern.

Wenn dieser enge Hook mit dem Pin nicht zuverlässig umsetzbar ist, B-II als BLOCKED berichten; **keine** stille Rückkehr zu ausschliesslichen Classpathtests und kein Enginefork. Unabhängige B-I-Ergebnisse bleiben erhalten.

### 4.4 Testreportformat

Native JUnit XML/Open-Test-Reporting unverändert aufbewahren. Falls der aktuelle Reportadapter zusätzliche Identität benötigt, nur einen getrennten Index erzeugen. Logische Identität mindestens Testquelle, Klasse, Methode, Parameter-/dynamischer Fall und Tagselektion. JUnit-Containerstruktur darf sich durch Modultests ändern; Zuordnung muss nachvollziehbar statt über reine Gesamtzahlen erfolgen.

Bestehende aktuelle Assertion-/Completionlinks aktualisieren, falls überhaupt eine tatsächliche Testidentitätsänderung nötig wird. Historische Norm-/G2-Belege bleiben unverändert. `CompilerContractRun` muss die tatsächlichen neuen modularen Producerreports verwenden.

## 5. `ModulePathContract`

Neues kleines internes Prüftool, vorzugsweise im Verificationmodul; Hilfen im Buildadapter nur für Deskriptor-/Inventorylesung:

```java
ModuleInventory inspect(Collection<Path> jars);
void verifyExpectedNames(ModuleInventory actual, ModulePlan expected);
void verifyNoSplitPackages(ModuleInventory actual);
void verifyReadability(ModuleInventory actual);
ProbeResult runNamedConsumer(LaunchInputs inputs, ConsumerFixture fixture);
ProbeResult assertForbiddenAccess(LaunchInputs inputs, ConsumerFixture fixture);
```

Deskriptoren mit Standard-JDK-APIs lesen, z.B. `ModuleFinder`, `ModuleDescriptor`, `Configuration`. Fehlende oder doppelte Modulnamen und getrennte Dateien mit gleichem Paket ablehnen. `jdeps --check`/`--print-module-deps` unterstützen die Analyse, ersetzen keine Reflection-/Service-Laufzeitprobe.

Benannter SDK-Consumer benötigt reale Compiler-/Modell-API, erzeugt wenigstens ein Ergebnis und behauptet die erwarteten Modulnamen der verwendeten Typen. Negativconsumer versucht einen bekannten nicht exportierten Compiler-internal-Typ zu importieren und muss gezielt an der Modulgrenze scheitern, nicht an einem Tippfehler oder fehlenden externen JAR.

Reflexionsprobe: benötigte Gson-/LSP-Serialisierung funktioniert; ein nicht freigegebenes anderes Package wird nicht pauschal geöffnet. Serviceprobe für vorhandene Provider muss auf Classpath und Module-Path dasselbe fachliche Ergebnis liefern.

Keine Probeausgaben in normalen LSP-stdout einschleusen. Modulzugehörigkeit über separate Testconsumer oder explizite JVM-Diagnoseläufe mit separaten Logfiles beobachten.

## 6. Umstellungsbatches

B5.1 model → compiler (ANTLR und Formatterressourcen eingeschlossen).

B5.2 workspace → repository und projection → documentation → wasm-bridge, nach tatsächlichem DAG.

B5.3 compiler-cli → lsp → verification, einschliesslich Testonly-Edges und Hostmains.

Nach jedem Batch: Descriptor-/Graphprüfung, betroffene normale Tests, kleiner benannter Consumer und mindestens eine geeignete negative Boundaryprobe. Bereits unveränderte Batches nicht jedes Mal vollständig neu testen. Nach einem gemeinsamen Export-/Dependencyfehler betroffene Nachfahren gezielt aufnehmen.

## 7. Endzustand und künftige APE-Verwendung

Produktion benutzt keine Testpatches. Die Moduldeskriptoren sind in normalen JARs enthalten, die SDK-/Classpath-/Wasm-Verbraucher weiterhin erhalten. Ein späterer APE-Build darf diese JARs auf seinem Buildclasspath verwenden; B behauptet dafür keine native Abnahme und führt keinen Cosmo-Build aus.

In der normalen Produktlieferung: keine JUnit-Abhängigkeit, keine Testklasse, kein `--patch-module`, keine Test-Opens, keine Verificationbibliothek. JAR-/Launcher-/Bundle-Scans prüfen das ausdrücklich.
