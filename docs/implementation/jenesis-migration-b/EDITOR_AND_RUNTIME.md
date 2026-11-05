# Editorverbraucher, modulare Launcher und Runtimepakete

## 1. Zwei Repositories, eine explizite Grenze

Core `edigonzales/interlis-java` produziert das Server-/Rendererbundle. `edigonzales/interlis-vscode` ist dessen tatsächlicher Verbraucher. Es ist nicht ausreichend, nur einen Core-Kompatibilitätstest einzubauen und den normalen Extensionbuild weiterhin auf Gradle zu lassen.

Im gelesenen Extensionstand ruft `scripts/stage-core.cjs` selbst `gradlew editorBundle` auf und liest `build/editor-bundle`. `src/host/launch.ts` startet den Server mit `-cp`. Beide Stellen müssen zum jeweiligen B-Meilenstein umgestellt werden. Die TypeScript-Editorfunktionen, LSP-Protokollversion, fachlichen Schemas und Runtimepins werden nicht neu erfunden.

## 2. Bundleproduktion im Core

`EditorBundleRun.run(BuildRequest, BuildOutcome)` bzw. ein gleichwertiger enger Adapter:

1. verifizierte aktuelle LSP-Runtimeclosure über `ModuleArtifacts` beziehen;
2. vorhandenen `frontend/diagram-ui`-Build mit seinen bestehenden npm-Pins starten bzw. gültige identische Outputs wiederverwenden;
3. bestehendes npm-Paketieren des neutralen Renderers aufrufen; ein historischer Scriptname mit `gradle` darf bei tatsächlicher Zweckänderung einmal sachgerecht umbenannt werden;
4. reinen Bundleassembler mit expliziten Inputpfaden/-hashes und Producerbeleg aufrufen;
5. vollständige Ausgabe inventarisieren und atomar bereitstellen.

`tools/editor/bundle.cjs` soll nicht mehr beim Import der Datei sofort globale Outputs löschen. Refaktoriere zu kleinen echten Funktionen:

```javascript
readInputs(inputManifest, expectedDigest)
verifyInputs(inputs)
assembleBundle(inputs, stagingDirectory)
writeManifest(bundleDirectory, metadata)
verifyBundle(bundleDirectory, expectedManifestDigest)
```

CLI und Modulexport benutzen dieselbe Implementierung. Keine zweite dauerhafte Bundlelogik für A-Kompatibilität und B-Produktion. Vorhandene geeignete A-Testhelfer entsprechend zusammenführen oder entfernen, historische Belege behalten.

Die Bundlepayload bleibt vertraut: `server/lib`, neutraler Renderer, `.tgz`, Protokolltypen und Schemas, Rendererprovenienz. Eine neue Rootausgabe unter `target/jenesis-jvm/editor-bundle` ist zulässig und zentral deklariert. Schema-/Typquellen werden gegen denselben relevanten Coreinput gebunden; kein Renderer/Schema von einem anderen Commit. Absolute Hostpfade und private Informationen nicht in die publizierbare Payload schreiben.

## 3. Staging in der Extension

Zwei getrennte Vorgänge, kein impliziter Fallback:

```javascript
buildCoreBundle(coreDirectory, options)
readAndVerifyBundle(bundleDirectory, expectedReference)
importCoreBundle(bundle, outputDirectory, options)
stageCore(coreDirectory, options)
```

`stageCore` ist die Conveniencekomposition: Core mit Java-25-Projekteinstieg **einmal** im `editor-bundle`-Profil bauen, dessen strukturierte Ergebnisdatei lesen, anschliessend reiner Import. `importCoreBundle` kann im CI ein bereits erzeugtes Bundle konsumieren, ohne Gradle/Jenesis erneut zu starten. Fehlendes Bundle bedeutet Fehler, nicht heimlichen Buildversuch.

Bestehendes `--core-dir` darf als kompatibler Entwicklereinstieg bleiben, ruft aber danach Jenesis statt Gradle auf. Ein neues `--bundle-dir` muss den erwarteten Producer-/Manifestbezug explizit erhalten. Änderungen an package.json, F5-Prepare-Scripts, Tasks und Workflows müssen diesen tatsächlichen Weg verwenden; keine parallele Produktionsspur.

Staging vor/nach Kopie verifizieren: kompletter exakter Dateisatz, sichere relative Pfade, Hashes, ggf. relevante Executablebits und intakte Renderer-/Schemaschnittstelle. Ziel nur innerhalb des ausdrücklich gewählten Scratch-/Extensionoutputroots löschen/ersetzen. Keine persönliche VS-Code-Installation überschreiben.

`development=true` darf einen aktuell getrackten Dirty-Core mit eindeutigen relevanten Inputhashes zulassen. Release-/normales Lockstaging verlangt saubere passende Provenienz. Der Dirty-Modus wird nicht still zum Default und ersetzt keinen finalen Kandidatenbezug.

## 4. Zwei kontrollierte Umstellungen

**B3:** Normaler Extensionproduzent wechselt zu Jenesis; der vorübergehende Serverstart darf noch dem unveränderten Classpathvertrag entsprechen. Das isoliert die Buildherkunft von JPMS-Problemen.

**B6:** Echte modulare Server-JARs und Modulstart. Der nun erforderliche Lock-/Launchvertrag wird gemeinsam in Core und Extension aktualisiert. Danach kein permanenter „versuche Modulpfad, bei Fehler Classpath“-Fallback. Ein Modulauflösungsfehler muss sichtbar bleiben.

## 5. Modularer Launchvertrag

Für den Core-CLI-Launcher:

```text
<java25> --module-path <lib-directory> \
  --module guru.interlis.compiler.cli/guru.interlis.cli.Main <user-args>
```

Für den LSP im Extension-Host:

```text
<bundled-or-explicit-java25> --module-path <server/lib> \
  --module guru.interlis.lsp/guru.interlis.lsp.Main
```

Das sind Zielstartformen, keine bereits getesteten Kommandos. Jeweils die tatsächliche endliche gehashte Closure verwenden. Keine Test-JARs in `lib`; kein JAR-Wildcard-Classpath zusätzlich zum gleichen Module-Path; keine `ALL-MODULE-PATH`-Krücke. JVMoptionen, Pfade mit Leerzeichen und Plattformseparatoren als Argumentliste, nicht als ungequoteten String bilden.

`launch.ts` behält `resolveLaunchPlan(...)` als zentralen Produzenten. Der Plan enthält unveränderliche `java`, `args`, Corecommit, Bundledigest, Runtimedigest/-quelle und den tatsächlichen Modulbezug. `ArtifactLock` erweitert man um einen streng geprüften Startvertrag, z.B.:

```json
{
  "schemaVersion": 2,
  "coreCommit": "<ACTUAL_CORE_SHA>",
  "protocolVersion": "lsp-3.17-baseline",
  "mainClass": "guru.interlis.lsp.Main",
  "launch": {
    "kind": "module",
    "moduleName": "guru.interlis.lsp",
    "mainClass": "guru.interlis.lsp.Main"
  },
  "files": [{"path": "lib/<ACTUAL_JAR>.jar", "sha256": "<SHA256>"}]
}
```

Kein unbegrenztes `javaArgs`-Feld aus untrusted Bundlemetadaten ausführen. Nur der bekannte Main-/Modulname wird unterstützt. Alte Schemata nicht auf Dauer still akzeptieren; die neue passende Extension und ihr eigenes Bundle werden gemeinsam ausgeliefert. Die LSP-Protokollversion bleibt unabhängig vom Packaging-Lockschema gleich.

`resolveLaunchPlan` prüft weiterhin vollständiges Serverinventar, Runtimeintegrität und Java25. Ein verpacktes VSIX verliert nicht seine Runtimeprüfung, nur weil ein Entwickler Java explizit überschreibt. Modulname/Descriptor muss zum inventarisierten Haupt-JAR passen. Runtimeoverride ist eine ausdrücklich kontrollierte Entwicklerfunktion, kein allgemeiner Classpathfallback.

## 6. Windows und Prozessverhalten

Die bestehende Unicode-Argumentbehandlung der Compiler-CLI aus A wird gelesen und funktional erhalten. Nicht einfach einen Standardlauncher generieren und die PowerShell-/Quotingdetails entfernen. Englische/Unicode-Dateinamen, Leerzeichen, anderer CWD, stdin, JSON, stdout/stderr und Exitcodes mit den vorhandenen gezielten Fällen prüfen.

Der LSP sendet keine Startup-/Moduldiagnose auf stdout. `initialize`, `initialized`, document-open, eine echte Diagnose, mindestens eine Navigation oder Diagrammanfrage, `shutdown`/`exit` und Restart werden mit dem neuen Startweg geprüft. Zusätzliche Debuglogs nur auf stderr bzw. in explizite Debugdateien.

Eine reine Parserprüfung der neuen Launchargumente ersetzt keinen gestarteten Prozess. Ein Node-Unit-Test ersetzt keinen echten Extension-Host. Vorhandene Extension-Smokeharness verwenden, keine zweite GUI-Automationsplattform entwickeln.

## 7. Jlink und jpackage: begrenzte Prüfung, kein zweites Auslieferungsprojekt

Die bestehende Extensionpipeline mit voller JRE bzw. jlink-Optimierung bleibt ihr Besitzer. B prüft sie mit den neuen Modulstarts und aktualisiert lediglich erforderliche Modul-/Serviceinputs. Kein `jpackage`-App-Image in die VSIX einbetten, keine neue native LSP-Spur.

Für die separat startbare Compiler-CLI wird nach erfolgreichem B6 ein kleiner expliziter `runtime-check --application compiler` ausgeführt:

- JDK-Module aus tatsächlicher Closure/jdeps und dynamischen Laufzeitverträgen bestimmen.
- Mit unverändertem JDK25 ein privates Runtimeimage bauen und die unveränderten modularen JARs daneben ausliefern.
- Den CLI-Launcher mit **dieser** Runtime und Module-Path starten, während ein fremdes JAVA_HOME/PATH keine andere JVM bereitstellt.
- Version, ein gültiges/ungültiges Modell, Formatierung und ein kontrollierter HTTP-/HTTPS-Repositoryfall gegen genau dieses Paket prüfen. Die Uhrpatcharbeit spielt hier keine Rolle; dies ist reguläres HotSpot, nicht CosmoGVM.
- Lizenz-/Herkunftsdateien, Runtimeversion und konkrete JDK-Modulliste festhalten. Fehlende Kryptografie-/Serviceprovider nicht durch TLSabschwächung kompensieren.

**Keine Forderung, automatische Fremdmodule in jlink hineinzulinken.** Ein JDK-only-Image mit unveränderten Anwendungs-JARs auf dem Modulpfad ist ein zulässiges positives Ergebnis. Vollständige Appmodule-Verlinkung ist nur möglich, wenn deren Closure linkbar ist; diese Eigenschaft offen berichten statt Fremd-JARs lokal umzuschreiben.

Zusätzlich eine kleine `jpackage --type app-image`-Capability-Probe auf dem verfügbaren Host durchführen, soweit mit dem unveränderten JDK und derselben Closure möglich. Ein geeigneter vorab erzeugter Runtimeimagepfad ist erlaubt. Erforderliche Jenesis-packaging-Konfiguration erst anhand des Pins prüfen; ansonsten den Standard-JDK-Aufruf als begrenzte Capabilityprobe dokumentieren, nicht als zweite Produktionspackagingpipeline installieren.

Die `jpackage`-Prüfung ist eine **Eignungsentscheidung**, keine Pflicht zu einem neuen Standarddistributionsformat. Ein konkret dokumentierter Nicht-Einsatz wegen automatischer Dependencies/Launcheranforderung kann ein korrektes Ergebnis sein; die vorhandene modulare JVM-CLI muss trotzdem funktionieren. Keine `.dmg/.msi/.deb`-Produktion, Signierung, Notarisierung oder Installation ins Hostsystem. Runtimegrössen und Crossplatformfähigkeit nur aus tatsächlich geprüften Paketen ableiten.

JDK25-spezifisch: Servicebinding des erzeugten Images bewusst bestimmen; nicht von früheren jpackage-Defaults ausgehen. Voll-JRE und reduziertes Image nicht als gleicher Digest/gleiche Abnahme behandeln.

## 8. Gezielte Tests

B3: stage aus echtem Core, reiner Bundleimport ohne Corebuilder, falscher Commit, manipuliertes/fehlendes/zusätzliches JAR, Renderer-/Schemaabweichung, sicherer Zielpfad, tatsächlicher Extension-Host mit Jenesis-JARs. Nach reiner Dokumentkorrektur nicht erneut ausführen.

B6: korrekter Modulstart, falscher Modulname, fehlendes `requires`/Dependency, unerwartete breite Launchflags und beschädigte Runtime müssen erkennbar scheitern. Echte LSP-Sequenz und CLI-Unicode-/Exitfälle. Verpacktes VSIX in isoliertem Testverzeichnis mit gebündelter Runtime testen, nicht nur globales Entwickler-Java.

B7: neue JVM-/Consumerbytes integrieren; auf vorhandenen echten Zielhosts die passenden Launcherfälle prüfen. Nicht verfügbare Windows-/Linuxrunner explizit ausweisen. Neue Workflowdefinition ist keine erfolgreiche Workflowausführung. Die vollständigen Releaseplattformgates bleiben offen, bis reale Nachweise vorliegen.

## 9. Commit-/Reviewbindung beider Repositories

Coreproduktcode samt Spez-/Statusänderungen zuerst geprüft committen. Daraus erzeugte Bundlemetadaten können diesen Core-SHA korrekt nennen. Extensioncode/Lock dann geprüft committen. Externes `candidate-pair.json` bindet beide SHAs/Trees und die Bundle-/Runtimeidentität. Der Core muss nicht in einem weiteren Metadatencommit wiederum den Extension-SHA aufnehmen.

Nach einem Review-Corefix entsteht ein neuer Corekandidat. Neue Bundle-/Lockbindung aktualisieren, relevante Byteunterschiede bestimmen und Extension bei Bedarf neu committen. Unveränderte ursprüngliche Laufbelege nur mit belegtem Inputbezug wiederverwenden; niemals ihren alten Committext ändern. Ein Review des alten Kandidatenpaares bestätigt nicht automatisch das neue Paar.
