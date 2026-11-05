# CI, Conformance und Release-Evidence

## 1. Kein Verlust von Qualitätsverträgen beim Builderwechsel

Im gelesenen Core läuft `.github/workflows/p3-platforms.yml` auf Linux x64 und Windows x64. Es startet derzeit `platformCheck` über den Gradlewrapper und lädt `build/reports/...`, `build/releases` und `*/build/test-results` hoch. Diese Pfade/Aufrufe werden auf die tatsächlichen Jenesis-Ergebnisse umgestellt. Ein neues YAML allein ist kein ausgeführter Plattformnachweis.

Aktuelle branchspezifische Trigger des alten P3-Auftrags sind gegen den gewollten B-Endzustand zu bereinigen. Sinnvoll: schnelle JVM-Prüfung für normale Änderungen, ausdrücklich gewählte Backend-/Releasejobs. Keine vollständige P3-/Performancekampagne bei jedem Dokumentationscommit; keine automatischen Publishing-/Native-/APEjobs. Vorhandene Action-Commitpins und minimale `contents: read`-Rechte beibehalten, keine pauschalen Upgrades.

## 2. Drei CI-Arten

### 2.1 JVM-Job

Java25 bereitstellen, Corequellen aus exakt einem Commit, Jenesispin prüfen. Normaler JVMbuild plus passende Contract-/SDK-/Modulstartprüfungen. Kein Gradle notwendig. Linux und Windows verwenden dieselbe Java-Projekteinstiegssemantik, aber plattformrichtige Argument- und Exitbehandlung.

Die konkreten Rohreports werden aus den Engineergebnisinventaren in ein Runpaket exportiert; keine Shellglobs, die zufällig alte Reports mehrerer Läufe einsammeln. Fehlgeschlagene Logs immer aufbewahren. `if-no-files-found: error` bleibt für erwartete Pflichtbelege. Uploads eines gescheiterten Laufs bekommen dadurch keine Erfolgsbewertung.

### 2.2 Backend-/Editor-Verbraucherjob

Ein geprüfter JVM-Producer erzeugt einen immutable ConsumerSnapshot. Der Folgejob erhält genau diesen Snapshot mit erwarteter Manifestidentität und Quellen-/Pinbezug. Keine eigene erneute JVMkompilierung als implizite „Sicherheitsmassnahme“ und keine Auswahl des zuletzt vorhandenen Artifacts.

Der Backendjob startet nur die zwei dafür vorgesehenen Gradleprojekte und deren eigene Quellen. Der Editorjob konsumiert das Bundle im reinen Importmodus; F5/Entwicklung dürfen den vorherigen Build explizit anbieten. Compiler-JARs, Renderer, Typen/Schemas und Runtime erhalten konkrete kohärente Herkunft.

Jobgrenzen verändern die Vertrauensbasis: Erwartete Artefakt-ID/Run-ID/SHA/Manifesthash aus dem ausgewählten Producer beziehen, nicht aus einem beliebigen eingehenden Archiv. Sichere Extraktion, exakter Dateisatz, gehashte POM-/JARclosure und sauberer frischer Output gelten auch remote.

### 2.3 Vollständiger Releasecheck

`release-check --profile P3` ist ausdrücklich anzufordern. Es prüft den vollständigen bestehenden fachlichen Plan: normale JVMtests, Compilercontracts, externe Conformance/normative Auswertung, CLI/SDK, beide Backends, erforderliche echte Browser/Safari, Performanceprofile, Plattformnachweise, JVMreproduzierbarkeit und vollständige Auslieferungsintegrität.

Keine dieser Pflichten wird entfernt, nur weil das neue Buildsystem sie nicht automatisch kennt. Es darf gezielt gültige identische Nachweise konsumieren, aber keine fehlenden/fremden/abgebrochenen/mit unerlaubten Skips versehenen Reports freigeben. Eine nicht beauftragte vollständige P3-Ausführung kann für B ausgelassen bleiben; deren Releasezustand ist dann offen, nicht grün.

## 3. Konkrete Adapterverträge

Bestehende `tools/release/*.cjs`, `tools/performance/profile.cjs` und Verifikationstools möglichst weiterverwenden. Neue Pfade werden über einen kleinen gemeinsamen Layout-/Manifestleser zugeführt, nicht in vielen Dateien als neue feste Stringmuster verteilt.

Vorgeschlagene Java-Komponente `HostVerificationProfiles`:

```java
ProfilePlan plan(String exactProfile, BuildContext context);
Set<RequiredInput> requiredInputs(ProfilePlan plan);
VerificationResult run(ProfilePlan plan, VerifiedInputs inputs, Path output);
void verifyCompletion(ProfilePlan plan, VerificationResult actualResult);
```

`ProfilePlan` ist eine geschlossene Liste vorhandener Klassen/Nodeprogramme und ihrer vorbereiteten Inputs. `run` führt in fester Reihenfolge aus, propagiert den wirklichen Exit, schreibt Originallogs und beendet nur eigene Prozesse. `verifyCompletion` prüft erwartete Fälle/Reports, nicht bloss einen PASS-Text. Keine eigene fachliche Conformanceklassifikation in den Orchestrator kopieren.

Vorgeschlagene `WasmBuildRun`:

```java
WasmInvocation plan(Path root, BackendProfile profile, Snapshot inputs);
ProcessReceipt execute(WasmInvocation invocation, Path output);
BackendResult readVerifiedResult(WasmInvocation invocation, ProcessReceipt receipt);
```

`plan` erzeugt genaue Gradlewrapper-Args, benötigte Toolhomes und Manifestbindung ohne Netzwerk-/Toolinstallation. `execute` begrenzt Zeit/Ressourcen und hält keine JVMlock-Reentranz. `readVerifiedResult` verbindet tatsächlich verwendeten Classpath, Backendpin und echte Ausgabehashes; ein Null-/Stalereport wird abgelehnt.

Für Node-Verbraucher ein gemeinsamer kleiner, soweit vorhanden zu erweiternder Layoutreader:

```javascript
readVerificationInputs(file, expectedHash)
resolveReport(inputs, role)
verifyProducerIdentity(inputs, expected)
collectArtifactInventory(inputs)
```

Keine zusätzliche universelle Registry. Rollen sind bekannte Verträge wie `jvm-tests`, `compiler-contracts`, `cli`, `sdk`, `teavm-js`, `teavm-wasm`, `webimage`, `platform-linux-x64`. Konsumenten dürfen fehlende Rolle nicht durch den alten Gradlepfad ersetzen.

## 4. Reports und Mindestmengen

Bestehende Mindestzahlen für Testfälle/Suites nicht ungeprüft weitertragen und nicht pauschal senken. Die Modularisierung kann Container-/Suiteform ändern. Baselinefall → neuer tatsächlicher Fall wird logisch zugeordnet, einschliesslich Parameter- und dynamischen Invocations. Eine Abnahme darf nicht von der technischen Zahl XML-Dateien abhängen, wenn die JUnit-Engine sie anders gruppiert; die echte fachliche Mindestmenge bleibt jedoch erhalten.

Bei aktuell sourcegebundenen Assertions muss der neue Report dieselbe echte Assertionausführung belegen. Eine Liste von zu startenden Methodennamen ist kein Runbeleg. Gegenproben: Assertion weggelassen, Parameterfall fehlt, Report eines anderen Moduls, Filter ohne Treffer, stale Run-ID und nachträglich veränderte Rohdatei.

Historische Reports behalten Originalpfade und ihren damaligen Builder. Aktuelle Validatoren können ein **explizit deklariertes historisches Inputschema** lesen, wenn es für eine reale überprüfbare Wiederverwendung nötig ist; sie dürfen es nicht als laufenden Gradlefallback benutzen. Keine endlose Doppelwartung zweier aktiver Reportproduzenten.

## 5. Reproduzierbarkeit und Integrität getrennt

Die JVM-Erzeugung wird in zwei frischen Outputwurzeln/Arbeitskopien mit identischen relevanten Inputs geprüft. Zufällige Ausgabepfade, Zeit-/Locale-/Umaskeffekte dürfen keine Produktabweichung verdecken. JAR/Sources/POM/SDK werden entsprechend ihrem bestehenden Liefervertrag verglichen. Der vorhandene A-Reproadapter darf dafür weiterentwickelt werden; nicht Jenesis-Diagnosemodus allein als hartes Gate missverstehen.

Die beiden Erzeugungen müssen nicht beide sämtliche Tests ausführen. Ein bereits getesteter konkreter Auslieferungskandidat bleibt von der separaten Reproduzierbarkeitsgegenprobe unterscheidbar.

JS/Wasm-Ausgaben sind weiterhin nicht zur Bytegleichheit unabhängiger Builds verpflichtet. Sie bleiben jedoch vollständig im Auslieferungsinventar und an ihre **eigenen** echten Laufzeitnachweise gebunden. Keine generelle Ausnahme für `.js`, `.json` oder ganze Browserverzeichnisse. Der frühere TeaVM-Determinismuspatch bleibt entfernt.

Bundle-/Versions-/Runmanifeste mit bewusst variablen Herkunftsfeldern nicht für Vergleiche in der echten Lieferung verändern. Vergleichsprojektion nur separat, eng dokumentiert; tatsächliche Artefakthashes bleiben unverändert. Normale Paketintegrität gilt auch für Runtime-/App-Images, deren Byte-Reproduzierbarkeit nicht automatisch unterstellt wird.

## 6. Notwendige Negativtests vor B-Abschluss

- Erwarteter Plattform-/Backendbericht fehlt oder ist zu alt/fremd.
- Producer war gescheitert/abgebrochen, Report enthält dennoch PASS-Zeile.
- JUnit enthält Fehler, unerlaubten Skip, verlorene Auswahl oder falschen Parameterumfang.
- Ausgeliefertes JAR/JS/Wasm wurde nach einem passenden Test geändert.
- Lokale Datei und ihr enger Hash wurden zusammen geändert, die übergeordnete Producer-/Runbindung nicht.
- SDK-/Editor-/Browserinventar enthält zusätzliche oder fehlende Pflichtdateien.
- Linuxbericht wird als Windows/Safari ausgegeben bzw. Classpathreport als Modultest.
- Gesamtrelease wird mit einem echten JVM-Erfolg aber fehlenden Backend-/Browserbelegen angefragt.

Solche Validatorproben sind schnell und können ohne die ganze P3-Performancekampagne laufen. Sie ersetzen keine ausdrücklich fälligen echten B-Consumerläufe.

## 7. Keine Veröffentlichung

B ändert Workflowcode und prüft ihn lokal bzw. liest verfügbare passende CI-Nachweise. Ohne ausdrückliche zusätzliche Autorisierung: kein Push, kein workflow_dispatch, keine neue kostenpflichtige Runnerbeschaffung, kein Tag und kein Release. Lokale vollständige Ausführung vorhandener Tools innerhalb des Auftrags ist erlaubt. Nicht ausgeführte CI-/Plattformjobs werden ehrlich benannt.
