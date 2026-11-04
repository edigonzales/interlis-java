# VS-Code-Kompatibilität — begrenzter Verbrauchercheck für Auftrag A

**Verbindlicher Teil der finalen Spezifikation, 4. November 2026.** Dieses Dokument ergänzt SPECIFICATION Abschnitt 8 und JA-33 bis JA-36. Es beauftragt keine dauerhafte Extension-, Frontend- oder Runtime-Migration.

## 1. Warum diese Prüfung dazugehört

Die vorhandene Extension `edigonzales/interlis-vscode` hat zwei unterschiedliche Verbindungen zum Core:

1. **Buildverbindung:** `scripts/stage-core.cjs` startet `gradlew editorBundle --no-daemon`, liest `build/editor-bundle` und erzeugt den Extension-Artefaktlock. Der F5-/Developmentpfad verwendet die bestehende Vorbereitungskette.
2. **Laufzeitverbindung:** `src/host/launch.ts::resolveLaunchPlan(...)` prüft JAR-Inventar, Hashes und Java 25 und startet `guru.interlis.lsp.Main` mit `-cp <server/lib/*>`.

Die erste Verbindung muss trotz paralleler Jenesis-Spur weiter funktionieren. Die zweite muss die Jenesis-JARs korrekt akzeptieren, obwohl die normale Buildverbindung noch Gradle verwendet. Ein grüner Gradle-Staginglauf beweist deshalb nicht automatisch Jenesis-Kompatibilität.

Im Core erzeugt `tools/editor/bundle.cjs` heute dieses logische Paket:

```text
editor-bundle/
  server/lib/*.jar
  diagram-ui/
  diagram-ui.tgz
  types/
  schemas/
  renderer-provenance.json
  manifest.json
```

Die Serverbibliotheken kommen bisher aus `interlis-lsp/build/install/interlis-lsp/lib`. Die Nichtserverdateien stammen aus den bestehenden Frontend-, Protokoll- und Schemaquellen. A muss diesen Vertrag nicht neu erfinden.

## 2. Fester Scope und Schreibgrenzen

### Enthalten

- Ein kleiner echter Nachweis des bisherigen Staging-/F5-Vorbereitungsvertrags in einer isolierten Testkopie.
- Ein separates Schattenbundle mit den tatsächlichen Jenesis-LSP-JARs und nachgewiesenen unveränderten Nichtserverressourcen.
- Prüfung mit dem echten, unveränderten Extension-Inventar-/Launchcode.
- Ein kleiner echter Extension-Host-Smoke gegen diese Server-JARs auf dem verfügbaren Entwicklungs-Host.
- Enge Integritätsgegenproben und eindeutige Rohbelege.

### Nicht enthalten

Kein Austausch des normalen `stage:core`-Buildaufrufs, kein dauerhaftes `--builder`-Backend im Extensionprojekt, keine neue F5-Konfiguration, kein Umbau des TypeScript-/Rendererbuilds, kein Wechsel der VSIX-Identität oder ihrer Versionierungsregeln. Keine neue jlink-/jpackage-/JRE- oder APE-Variante. Keine Voll-GUI-/UML-/Remote-/Multiplattformabnahme.

`interlis-vscode` bleibt **read-only Quell-/Testverbraucher**, kein zweites zu committendes Projekt. Fremde lokale Änderungen erhalten. Bei Bedarf eine separate Kopie/einen Worktree des ermittelten passenden Extensionstands erzeugen. Keine Änderungen an der persönlichen VS-Code-Installation, Nutzerprofilen oder installierten Extensions.

Neu erzeugte Testlocks sind nur in einem eindeutig begrenzten Schatten-/Scratchverzeichnis zulässig. Vorher/nachher die ursprünglichen versionierten Locks, Runtimepins und Extensionquellen hashen. Alte akzeptierte Core-/Extensionpaare bleiben historische Abnahmen; sie werden nicht auf den Jenesiskandidaten umetikettiert.

## 3. Referenzstand und Voraussetzungen

Als recherchierter Extensionstand dient `a5b774a4d593ca5424212c8a733fe816106639f7`. Dieser ist wie der Core-Referenz-SHA **kein Reset-Befehl**. A0 prüft das tatsächlich vorhandene zusammengehörige Core-/Extensionpaar und dokumentiert Abweichungen.

Vor der Probe feststellen:

- tatsächlicher Extension-Commit/Tree und relevante lokale Änderungen;
- Core-Produktzustand, Jenesis-Artefaktinventar, LSP-Hauptklasse und Runtimeclosure;
- `scripts/stage-core.cjs`, `scripts/prepare-dev.cjs`, `scripts/build.cjs`, Launch-/Runtimecode und bestehende Tests;
- geeigneter vorhandener oder nach bestehendem Testverfahren bezogener isolierter Editorhost;
- gepinnte npm-Abhängigkeiten und geeignete getrennt dokumentierte Node-Ausführung;
- eine passende bekannte Java-25-Runtime für den Host.

Ein Teil der bisherigen Verfahren fordert ein sauberes Core-Checkout. Für einen Vorcommitversuch nur den ausdrücklich vorhandenen Development-Modus verwenden und dessen Dirtyzustand offen erfassen, oder einen erlaubten Zwischencommit des geprüften Teilstands verwenden. Keine Manifestfelder ändern, um einen sauberen Releasebuild vorzutäuschen.

Fehlender geeigneter Editorhost ist eine früh zu klärende Voraussetzung. Ein reiner Terminal-LSP-Test ersetzt den verlangten Extension-Host-Smoke nicht. Keine globale Installation oder Systemänderung als unbemerkte Voraussetzung einführen.

## 4. Herkunft der beiden Artefaktmengen

### 4.1 Referenzressourcen

Die vorhandenen Frontend-/Renderer-/Protokollressourcen dürfen aus einem separat ausgeführten Gradle-`editorBundle`- beziehungsweise vorhandenen Extension-Staginglauf kommen. Dessen Kommando, Quellstand, Eingaben und Bundlehashes werden protokolliert. Das ist **Referenzvorbereitung ausserhalb des Jenesisproduzenten**, keine versteckte Gradle-Delegation in `buildJvm`.

Die relevanten Nichtserverquellen müssen zur geprüften Kandidatenbasis passen. Ein alter Renderer wird nicht allein deshalb verwendet, weil irgendwo ein `.tgz` liegt. Bei unveränderten relevanten Inputs darf ein gültiger bereits vorhandener Referenzbeleg wiederverwendet werden. Den Core-Commit eines alten Bundles nicht still umschreiben.

### 4.2 Neue Serverartefakte

Die Servermenge kommt vollständig aus der tatsächlichen Jenesis-Runtimeclosure von `interlis-lsp`. Dazu gehören eigene Module sowie die identischen gepinnten externen Runtimebibliotheken. Keine Gradle-eigenen JARs beimischen, kein neuer Fat-JAR und keine Test-, Build-, Generator- oder Verification-JAR.

Im Schattenbundle wird `server/lib` **vollständig neu erstellt**. Nicht nur das Haupt-JAR über eine alte Gradle-Lieferung kopieren. Verwaiste Bibliotheken dürfen dadurch nicht übrig bleiben. Dateiname, relative Ablage, SHA-256, Koordinaten und Herkunft werden inventarisiert. Nur eine Datei mit passendem Namen zu finden genügt nicht.

Die regulären Pfade `build/editor-bundle` und `interlis-lsp/build/install/...` dürfen für diesen Versuch nicht mit Jenesisdateien überschrieben werden. Sonst könnte ein späterer F5-/Gradlelauf unbemerkt die falsche Spur verwenden.

### 4.3 Manifest und Testlock

Ein neues vollständiges Schattenmanifest wird aus den tatsächlichen Schattenbytes erstellt. Behalte die vom vorhandenen Consumer erwartete Struktur: Manifest-/Editorprotokollversion, Diagrammversion, Corebezug und vollständige Dateiliste mit SHA-256. Das Manifest selbst wird nicht als unauflösbare Selbsthashreferenz aufgenommen.

Das Testlock unter der isolierten Extensionkopie erhält dieselben vom Consumer verlangten Felder und **neue tatsächliche JAR-Hashes**. `files` enthält die vollständigen relativen `lib/*.jar`-Einträge. Nicht die alten Freigabehashes übernehmen und bei Abweichung die Prüfung abschalten.

Ergänzende Builder-/Input-/Dirty-/Runtime-/Rendererherkunft wird in einem separaten Prüfbeleg geführt; dafür ist kein neues öffentliches Bundle- oder Lockschema nötig. `coreCommit` allein beweist nicht, welche Buildspur und welche uncommitteten Inputs verwendet wurden.

Aus einem normalen Gradlepaket in Scratch kopierte `release/distribution.json`- oder Runtimeinventare können veraltet sein, sobald Server-/Rendererbytes wechseln. Einen **neuen klar als Test markierten Kandidaten** nach dem bestehenden Verfahren herstellen oder den vorhandenen nicht paketierten Developmenttestmodus nutzen. Keine Hashguards entfernen, um eine inkonsistente Kopie starten zu können.

## 5. Kleine Implementierung, keine zweite Packagingplattform

Ein passender Probenadapter, beispielsweise `tools/verification/editor-compatibility.cjs`, genügt. Bevorzuge vorhandene Funktionen/Proben; keine doppelte Implementierung von `resolveLaunchPlan`, Runtimeprüfung oder LSP-Semantik. Notwendige npm-/esbuild-Abhängigkeiten kommen aus dem unveränderten Extension-Lock in der Testkopie, nicht aus einer neuen Core-Frontendpipeline.

Folgende Funktionen sind **Vorgaben für den eigenen Probenadapter**, keine Behauptung schon vorhandener APIs:

```js
async function inspectConsumer(extensionRoot) { /* ConsumerSnapshot */ }
async function assembleShadowBundle({referenceBundle, serverInventory, outputRoot, coreState}) { /* ShadowBundle */ }
async function prepareConsumerCopy({consumer, shadowBundle, scratchRoot, runtime}) { /* ConsumerFixture */ }
async function verifyLaunchAndIntegrity(fixture) { /* ProbeResult[] */ }
async function runExtensionHostSmoke(fixture, editorExecutable) { /* HostSmokeResult */ }
async function verifyIsolation(before, after) { /* throws on unrelated changes */ }
```

`inspectConsumer`: tatsächlichen Quellstand, erforderliche Dateien, Locks, Launch-/Runtime-/Testschnittstellen inventarisieren. Keine Modell- oder Quellpfade erraten, keine fremden Quellen verändern.

`assembleShadowBundle`: leeres isoliertes Ausgabeziel, vollständige Jenesis-Serverclosure, geprüfte Nichtserverdateien, Rechte und neues Manifest. Relative Pfade validieren; Traversal, Duplikate und aus dem erlaubten Baum führende Symlinks ablehnen. Keine Löschoperation über einem Benutzerverzeichnis. Keine alte Serverdatei als Fallback.

`prepareConsumerCopy`: unveränderte Extensionquellen samt Lockfile in eine isolierte Kopie übernehmen; Testartefakte und Testlock dort erzeugen. Renderer-/Protokollquellen müssen passen. Den normalen `stage:core`-Aufruf **nicht nach der Schatteninjektion erneut starten**, weil er sonst wieder Gradle-JARs einsetzt. Nach jedem Vorbereitungsschritt final geladene Serverbytes prüfen.

`verifyLaunchAndIntegrity`: den echten `resolveLaunchPlan(...)` aus dem Extensionstand beziehungsweise vorhandene dazugehörige Tests ausführen. Falls TypeScript für den Probenzugriff kompiliert werden muss, unveränderte Quelldateien mit dem bestehenden gepinnten Buildtool in Scratch kompilieren, nicht den Validator in der Probe nachbauen. Gültige Kontrolle zuerst; Fehler bei Manipulation müssen vor dem Serverstart auftreten.

`runExtensionHostSmoke`: vorhandenen Extension-Host-Runner nutzen, Kommandos/Umgebung als echte Argumentlisten erfassen, begrenztes Timeout und Kindprozessbereinigung. Das bestehende Projekt bietet `npm run test:extension-host`; im recherchierten Stand unterstützen dessen Runner `INTERLIS_TEST_EXTENSION_ROOT`, `INTERLIS_TEST_CODE` und `INTERLIS_JAVA`. Diese konkreten Inputs in A0 gegen den tatsächlich benutzten Stand bestätigen, keine erfundenen Filterflags einsetzen. Vorhandene ausreichend kleine Tests wiederverwenden. Fehlende genau benötigte Beobachtungen dürfen durch eine kleine Scratch-Testdatei ergänzt werden, ohne Extension-Produktcode umzuschreiben.

`verifyIsolation`: Original-Extensionquellen, Release-Locks, Runtimepins, reguläre Core-Ausgaben und Benutzerprofile wurden durch Schatteninjektion/Mutation nicht verändert. Erlaubte neue Referenz-Gradleausgaben werden getrennt behandelt; nicht jede absichtliche Baselineausgabe als Fremdänderung werten.

Die Bridge `InterlisJvmBuild.verifyEditorConsumer(...)` aus SPECIFICATION ruft lediglich diese Probe auf und prüft Ergebnis/Provenienz. Keine eigene Java-Extensionlaufzeit implementieren. Nur die beim aktuellen diff benötigten kleinen Tests schreiben; keine neue allgemeine VS-Code-Testplattform.

## 6. Erforderliche Beobachtungen

| ID | Beobachtung | Grenze |
|---|---|---|
| E-01 | Bisheriger `stage:core`-/Development-Vorbereitungspfad funktioniert mit den Core-Buildänderungen. | Separater Gradle-Referenzlauf, kein Beleg für Jenesisserver. Ein sinnvoller Pfadlauf plus Prüfung des gemeinsamen Aufrufwegs genügt; F5 und Staging nicht zweimal breit duplizieren. |
| E-02 | Echter Extension-Inventarvalidator akzeptiert genau die vollständige Jenesis-LSP-Closure. | Keine Ersetzung der Hashprüfung durch eigenen schwächeren Prüfer. |
| E-03 | Echter isolierter Extension-Host aktiviert die Extension und startet den neuen Server. | Ein direkter `java`-Prozess alleine genügt nicht. |
| E-04 | Kleine lokale gültige/ungültige Modellfixture erzeugt erwartete Diagnose; eine echte Definition/Navigation liefert die erwartete Fundstelle. | Keine Live-Repositories; Diagnosen nicht aus einem Mock erzeugen. |
| E-05 | Stop/Restart beendet den alten Server und neuer Start liefert erneut die erwartete Diagnose. | Keine Zombieprozesse, keine alte Antwort als neuer Erfolg. |
| E-06 | Fehlende, veränderte und zusätzliche Server-JAR wird jeweils vom bestehenden Consumer abgelehnt. | Nur Schattenkopie verändern, vorher gültige Kontrolle. |
| E-07 | Quellen-/Bundle-/Runtime-/Lockhashes und Commit-/Dirtyzustände sind korrekt erfasst; originale Locks unverändert. | Kein neuer freigegebener VSIX-/Plattformstatus. |

Der ohnehin benötigte LSP-Prozesssmoke aus SPECIFICATION prüft weiterhin Framing, stdout und Shutdown. Die Editorfälle dürfen dessen Ergebnisse nutzen, wo die Aussage wirklich dieselbe ist, ersetzen aber keine fehlende echte Extensionaktivierung.

## 7. Runtime, jlink und Plattformgrenze

Bevorzugt eine bereits qualifizierte vorhandene Java-25-Runtime desselben Extensionstands und Hosts verwenden. Keine Änderung von `runtime-link.json`, Modulzusätzen, vendor truststore oder Runtimepins; keine neue Grössenoptimierungsrunde und kein jpackage im Core.

Ein ausdrücklicher absoluter Developer-Java-Override ist im vorhandenen Developmentvertrag erlaubt. Dann wird der Nachweis korrekt als **Extensionentwicklung mit dieser Java-Runtime** bezeichnet, nicht als Prüfung einer gebündelten Release-JRE. Bei paketierten Kopien darf der bestehende Consumer weiterhin die mitgelieferte Runtime validieren, auch wenn ein Override gewählt ist; diesen Guard nicht umgehen.

Ein Host genügt für den hier beauftragten Kompatibilitätsversuch. Andere Betriebssysteme, UI-Suites oder Runtimevarianten sind nicht automatisch erneut zu prüfen, solange deren Mechanismen und Produktinputs unverändert bleiben. Eine tatsächliche Änderung solcher Mechanismen würde die passende zusätzliche Prüfung erforderlich machen, gehört aber nicht zur geplanten Umsetzung von A.

## 8. Bericht, Wiederverwendung und Review

Codex erzeugt `EDITOR_COMPATIBILITY_REPORT.md` mit:

- tatsächlichem Core-/Extensionzustand und Scope beider Nachweisrichtungen;
- überprüften Quellen-/Tool-/Runtimeversionen;
- vollständigen Server- und Nichtserverinventaren mit Herkunft/Hashes;
- Herkunft des neuen Schatten-Testlocks und unveränderten Original-Lockhashes;
- tatsächlich ausgeführten Kommandos, Exits, Auswahl, Logs und E-01 bis E-07;
- expliziter Abgrenzung von Development-/gebündelter Runtime, API-Extension-Host und GUI-/Plattformfreigabe;
- Wiederverwendung unveränderter Belege und konkret offenen notwendigen Voraussetzungen.

Nach reinen Dokument- oder Reviewer-Metadatenfixes nicht den Editor erneut starten. Nach Änderungen am Schatten-Lockgenerator die Hash-/Inventarproben plus den betroffenen kleinen Launchconsumer wiederholen. Bei Änderungen der LSP-JAR-Closure oder des realen Startvertrags den betroffenen Extension-Host-Smoke erneut ausführen. Keine vollständige GUI-Matrix aus Formalismus.

Der unabhängige Reviewer prüft die echten Originalbelege und ergänzt eine eigene schmale Consumer-/Integritätsgegenprobe. Er muss den kompletten Host-/GUI-Lauf bei unveränderten relevanten Bytes nicht wiederholen. Fehlender obligatorischer E-Nachweis bleibt BLOCKED, ein nicht fälliger APE-/JPMS-Lauf bleibt NOT_IN_SCOPE.

## 9. Quellen des abgegrenzten Vertrags

Gelesener Extensionstand: `a5b774a4d593ca5424212c8a733fe816106639f7`. Pfade wurden am 4. November 2026 über den GitHub-Connector geprüft; neuere lokale Quellen muss A0 berücksichtigen.

- [Staging](https://github.com/edigonzales/interlis-vscode/blob/a5b774a4d593ca5424212c8a733fe816106639f7/scripts/stage-core.cjs)
- [Inventar-/Launchcode](https://github.com/edigonzales/interlis-vscode/blob/a5b774a4d593ca5424212c8a733fe816106639f7/src/host/launch.ts)
- [Vorhandener Extension-Host-Runner](https://github.com/edigonzales/interlis-vscode/blob/a5b774a4d593ca5424212c8a733fe816106639f7/scripts/extension-host.cjs)
- [Runtime-Linkkonfiguration](https://github.com/edigonzales/interlis-vscode/blob/a5b774a4d593ca5424212c8a733fe816106639f7/release/runtime-link.json)
- [Core-Bundleassembler](https://github.com/edigonzales/interlis-java/blob/84fe4552099f791a40f726a7628f5878a1a665d4/tools/editor/bundle.cjs)

Die neuen Adapter und Nachweise sind Implementierungsvorgaben. Für dieses Spezifikationspaket wurden keine Extensionbuilds oder Verbrauchertests ausgeführt.
