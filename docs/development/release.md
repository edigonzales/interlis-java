# Compiler-Freigabeprüfung

## Vertragsversionen

Vergleiche im Freigabereview die öffentlichen Verträge mit der letzten
veröffentlichten Version. Prüfe und dokumentiere die Versionsentscheidung
für jede betroffene Fläche separat.

- Öffentliche Java-SDK-API
- Diagnosekatalog
- Bridge-Protokoll
- Browser-Buildmanifest
- Unterstützte INTERLIS-Sprachprofile
- Quelltextformatierung
- Diagrammschema

Erhöhe bei einem inkompatiblen Bruch die betroffene Vertragsversion im
selben PR und aktualisiere Verbraucher, Vertragstests und Dokumentation.
Begründe bei kompatiblen Änderungen die Versionsentscheidung. Die Tests
prüfen die Übereinstimmung der veröffentlichten Versionsangaben. Das Review
bewertet die Kompatibilität anhand der Verträge und ihrer Verbraucher.

## Plattformen und Ablauf

Der [Workflow](../../.github/workflows/compiler-platforms.yml) verwendet Java
25.0.3. Unter Linux führt er `./gradlew check packageTest compilerZipChecksum
editorBundle` aus. Danach folgen die swisstopo- und Codeberg-Konformitätssuites.
Windows führt gezielte CLI-, Repository- und Validator-Tests und dieselben
Paketprüfungen aus. Beide Jobs sichern die nativen Gradle-JUnit-Berichte.

Der Portable-Job prüft die erzeugten TeaVM-JS- und WasmGC-Artefakte mit Node
und Chromium. Dazu gehören Client, Worker, Diagrammprojektion und der
Validator-WasmGC-Probe. Für eine Browserveröffentlichung werden die
zugesagten Browser mit den tatsächlichen Browserartefakten geprüft.

## Veröffentlichung

Compiler-Artefakte werden ausschliesslich als Snapshots über die CI auf
`main` veröffentlicht. Dafür werden keine GitHub-Releases oder Versionstags
erstellt.

Nach einem Push auf `main` und erfolgreichen Pflichtjobs veröffentlicht der
Workflow das geprüfte Compiler-ZIP unter
`guru.interlis:interlis-compiler-bindist` im
[Snapshot-Repository](https://jars.interlis.guru/snapshots). Das Paket enthält
POSIX- und Windows-Launcher und setzt Java 25 voraus. Gradle Maven Publish übernimmt
das ZIP und seine Prüfsumme aus demselben Workflow und prüft nach dem Upload
die heruntergeladenen Bytes. Die Publikationsrechte gehören zum Environment
`compiler-snapshots`.
