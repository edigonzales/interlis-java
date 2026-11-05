# Buildbesitz, Profile und Bereinigung

## 1. Richtung der Abhängigkeiten

```text
InterlisJvmBuild (kleiner Projekteinstieg)
  ├─ Jenesis → gemeinsame JVM-JARs + Tests + JVM-Distributionen
  ├─ Node → bestehender Renderer/Editorbundle
  ├─ eingefrorener ConsumerSnapshot
  │     └─ Gradle → nur TeaVM-/Web-Image-Adapter und deren Ausgaben
  └─ Verification/Releasevalidatoren → konkrete verknüpfte Rohbelege

interlis-vscode:
  Core einmal explizit bauen ODER fertiges Bundle importieren
  → Bundle prüfen → eigene Runtime-/VSIX-Werkzeuge → modularer LSP-Start
```

Keine Rückkante von Gradle zu Jenesis, JVM-Build oder sdk-check. Ein Runtime-/Browser-Vergleich darf bereits erzeugte JVM-Referenzdaten konsumieren; er darf nicht über eine Root-Task wieder den ganzen JVM-Build aktivieren. Gradle ist kein versteckter Gesamtbuild mehr.

## 2. Vollständiges Dispositionsinventar in B0

Aus settings.gradle, Root-build.gradle, gradle/*.gradle und allen Subprojektbuilds die aktiven Tasks/Dependencies, SourceSets, Ressourcen, Konfigurationen und CI-Aufrufe erfassen. Auch package.json-Scripts, Shellskripte und README-Kommandos durchsuchen. Für jeden Eintrag festhalten: Funktion, neuer Besitzer, neues Kommando, Input-/Reportvertrag, erforderlicher Test, Löschzeitpunkt.

Keinen Eintrag allein wegen `evidence` oder `g2` im Namen löschen. Historisch abgeschlossene Testlogik ist bereits bei der Verificationmigration behandelt worden; B erfindet keine zweite pauschale Evidenzstilllegung. Umfangreiche Profile bleiben explizit auswählbar, nicht im Defaultbuild.

## 3. Verbindliche Migration der bekannten Einstiegspunkte

Die folgende Liste ist Startinventar; zusätzliche lokale Tasks werden nach demselben Schema eingeordnet.

| Bisher | Ziel | Anforderung |
|---|---|---|
| JVM-subprojects `classes`, `jar`, `sourcesJar`, `test`, `check` | `build` / `artifacts` / Filter | Jenesis, normale Testmenge und Quellen/Lizenzen erhalten |
| CLI/LSP `installDist` | `ExistingJvmDistribution` bzw. sachgerechter Name nach Bereinigung | Inventargesteuerte Lieferung, später benannte Modulstarts |
| `compilerContractCheck`, contractAssertion-Produzenten | `contracts` | Frische selektierte Tests, Consumer danach |
| `pythonFreeCheck` | `host --profile python-free` | bestehendes Javawerkzeug, keine neue Pythonpflicht |
| `compilerCliCheck` | `host --profile cli` | reale neue Distribution |
| `sdkProcessCheck` / `sdkArchiveCheck` / `sdkPackage` | `sdk-check` und interner Paketproduzent | kein Kreis Paket→Test→Paket; Inputs vor Produktion festlegen |
| `platformCheck` | `host --profile platform` | tatsächliche Reports/Profile lesen, keine Gradlepfadannahme |
| `prepareConformanceSuite` | schmale Suitevorbereitung im Hostprofil | gepinntes Archiv, sichere Extraktion, Originalrunner |
| `runConformanceSuite`, `conformanceCheck`, `conformanceEvidence` | `host --profile conformance` bzw. `conformance-evidence` | Exit/XPASS/Normausnahmen unverändert |
| `conformanceWrapperTest` | normale zuständige JUnit-Tests | keine Vollsuite als Voraussetzung jedes Unitlaufs |
| `referenceCheck`, `originalViewReferenceCheck` | `host --profile reference` / `reference-originals` | gepinnter Referenzcompiler; gezielte Auswahl |
| `viewReferenceCheck`, `unitReferenceCheck`, `repositoryReferenceCheck` | feste entsprechende Hostprofile | JVM-/Node-Voraussetzungen ausdrücklich |
| `geoCorpus`, `fullCorpus` | feste Preparation-Profile | vorhandene Pins und Archive, kein Netzbezug bei normalen Tests |
| `fullCorpusJvm`, `jvmBenchmark` | explizite JVM-Performanceprofile | nie automatisch im Default |
| `semanticComparison` | explizites Composite-Hostprofil | JVM/JS/Wasm-Resultate desselben Inputs |
| `editorPressure`, `observationPressure` | explizite Hostprofile | bestehende Budgets und Berichtbindung |
| `diagramUiBuild`, `diagramUiPackage` | vorhandene Nodeproduzenten aus Coreentry | Gradle ist dafür kein zwingender JVM-Vorbau |
| `editorBundle` | `editor-bundle` | echte Jenesis-LSP-JARs, kein alter Gradlepfad |
| `checkPortable`, `checkBrowser` | feste Consumerprofile | normale JVM- und betroffene Backendgates, keine implizite Veröffentlichung |
| `wasmBenchmark`, `browserBenchmark`, `fullCorpusWasm/Browsers/Safari/EditorWasm`, `fullCorpusArtifacts` | explizite Wasm-/Performanceprofile | existierende Testprogramme/Budgets erhalten |
| `performanceCheckerTest`, `releaseEvidenceCheckerTest` | gezielte vorhandene Node-Tests | direkt ausführbar ohne teure Producer |
| `performanceCheck` | explizites Compositeprofil | unveränderte vollständige P3-Leistungspflichten |
| `sdkReproducibilityCheck` | bestehender JVM-Reproadapter + Consumervergleich | tatsächliche Bytevergleichsfehler ablehnen |
| `releaseEvidenceCheck`, `releaseCheckP3`, `releaseCheck -PreleaseProfile=P3` | `release-check --profile P3` | Gesamtplan unverändert in Schutzwirkung |
| `checkNodeRuntime` | gemeinsamer kleiner Preflight in Node-/Wasmprofilen | Pin übernehmen, nicht Node 22 pauschal annehmen |

Der Profileparameter ist eine exakte Enum-/Mapauswahl. Keine frei ausführbare mainClass, keine dynamisch aus JSON geladene Shell. Beispiele für bestehende Hostmains: `SdkProcesses`, `SdkArchiveCheck`, `CompilerCliProcesses`, `PlatformProcesses`, `FullCorpusRun`, `ConformanceEvidence`, `ReferenceProcesses`, `RepositoryReferenceCheck`. Keine Umbenennung ohne konkreten Nutzen.

## 4. Gradle-Endzustand

Der Wrapper bleibt im Root, weil er weiterhin gebraucht wird. `settings.gradle` umfasst nur `interlis-wasm-teavm` und `interlis-wasm-webimage`. Keine versteckten includes der zehn JVM-Projekte. Im Root bleiben nur Wasm-Lifecycle-/Preflight-/Koordinationsfunktionen, die zu diesen zwei Backendprojekten gehören.

Eigene Backendadapter, TeaVMs JS-Interopdependency, erforderliche Backend-JUnit-Tests, gepinnter TeaVM-Core, Binaryen-/Graal-Homeauswahl und Codegenoptionen bleiben. Für gewöhnliche modulare JVM-JARs in diesen Classpathbackends die Gradle-Modulpfadinferenz ausdrücklich kontrollieren; keine versehentliche Backend-JPMS-Migration.

Zu entfernen/ersetzen:

- `implementation project(':interlis-...')` auf gemeinsame JVM-Produkte;
- `dependsOn ':interlis-compiler:generateTestGrammarSource'` nach Nachweis, welche tatsächliche Eingabe gebraucht wird;
- SDK-/CLI-/Verification-Root-Backedges;
- Java-Gradle-Buildskripte der zehn gemeinsamen JVM-Projekte;
- `gradle/host-verification.gradle`, `gradle/verification-contracts.gradle` und JVM-Teile von `gradle/conformance.gradle`, sobald ihre Funktionen migriert sind;
- unbenutzte Versionkatalogeinträge und Java-Konventionen. Backendbenötigte Teile werden eng erhalten, nicht pauschal gelöscht.

Eine alte Gradle-Task darf nicht unbegrenzt als stiller Wrapper um Jenesis weiterleben. Erforderliche kurze Übergangsaliase dürfen bis B4 existieren; danach genau ein dokumentierter Aufruf. Ein bewusst fehlschlagender Migrationshinweis ist erlaubt, wenn er keine aktive alternative Buildspur erzeugt; nicht für jede entfernte Subtask ein neues Aliasframework entwickeln.

## 5. Norm-/Conformancebehandlung

Der bisherige externe `InterlisConformance.java`-Runner bleibt Besitzer seiner fachlichen Ausführung und Rohreports. Der neue Hostadapter übernimmt nur Inputpin, Download/Extraktion, passende CLI, Filter, Prozess-/Timeoutbehandlung und Auswertung vorhandener erlaubter Normausnahmen.

Nicht `ignoreExitValue` in ein allgemeines Erfolgszeichen verwandeln: erwartete Runnercodes und anschliessende normative Prüfung bleiben getrennt. Interner Defekt/Crash/XPASS ist kein erlaubtes fachliches Reject. Kleine Filterauswahl für Migrationsgates ist möglich, vollständige P3-Suite bleibt im Releaseprofil.

## 6. Phasenordnung statt Taskgewirr

Feste Reihenfolge für eine volle explizite Consumerabnahme:

1. JVM-JARs und benötigte normale Tests produzieren bzw. gültige unveränderte Belege prüfen.
2. Gewähltes Compilercontractprofil und CLI-/SDK-Produkte erzeugen.
3. Runtimeclosure und nötige Frontend-/Referenzartefakte einfrieren.
4. Backendcodegen aus genau dieser Übergabe.
5. Backend-/Browser-/Vergleichsprüfungen ausführen.
6. Nur im ausdrücklich angeforderten Releaseprofil die vollständigen Performance-/Plattform-/Evidence-Gates bewerten.

Teure Compilierungen nicht gleichzeitig mit engen Timeout-/Performancefällen laufen lassen. Ein Orderingconstraint darf keine zusätzlichen Profile auswählen. Grosses Releaseprofil wird nicht als bequemer Default für jeden kleinen Test benutzt.

## 7. Cleanup-Gegenproben

Ein frischer Checkout ohne eigene Gradleoutputs und ohne globale eigene Mavenartefakte muss den JVM-Build, SDK und Editorbundle erzeugen können. Ein Wasm-Build mit fehlender Übergabe muss früh verständlich abbrechen statt gemeinsame Quellen selbst zu kompilieren. Ein vorhandener alter Gradle-output oder falscher SNAPSHOT darf diesen Fehler nicht verdecken.

`clean --scope jvm` entfernt nur aktuelle JVMoutputs. `consumer` entfernt ausgewählte generierte Übergaben/Bundleoutputs, nicht Originalbelege. `wasm` entfernt nur Backendoutputs. Ein Clean-Test erhält Sentineldateien in `build/jenesis`, `build/interlis`, den Spezverzeichnissen und einer unabhängigen Worktree-/Belegablage. Externe Vendor-/Dependencycaches werden nicht pauschal gelöscht.

Suche abschliessend nach aktiven `gradlew`, `installDist`, `/build/test-results`, `sourceSets`, `generateTestGrammarSource`, `mavenLocal`, `build/editor-bundle`, JVM-subproject-Einträgen und früheren Profileinstiegen. Historische Dokumente/Originalbelege sind begründete Resttreffer; aktive Consumer nicht.
