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
25.0.3. Unter Linux führt `java build/jenesis/Make.java` die regulären
JVM-Tests aus. `java build/interlis/ConformanceRun.java` prüft die gepinnte
Codeberg-Suite für INTERLIS 2.3 und 2.4.
Linux und Windows verwenden `java build/interlis/ExistingJvmDistribution.java check`
für die installierten SDK-, CLI- und LSP-Prozesse. Das Kommando
`java build/interlis/ExistingJvmDistribution.java compiler` erstellt und prüft
das entpackte Compiler-ZIP.
Windows führt zusätzlich gezielte CLI- und Repositorytests für Pfade, Dateien
und HTTP aus.

Ein eigener Linux-Job ruft den Gradle-Wrapper direkt auf. Gradle bezieht die
Laufzeitclosure von Jenesis ohne JVM-Testausführung und prüft das generierte
TeaVM-JavaScript und WasmGC mit Node. Der Job startet Chromium für die
Browser-/Worker-Integration. Er prüft auch die Client-, Worker- und Diagrammtests.
Für eine Browserveröffentlichung werden die zugesagten Browser mit den
tatsächlichen Browserartefakten geprüft.

Ein Push auf `main` kann nach erfolgreichen Pflichtjobs ein Compiler-ZIP
unter `guru.interlis:interlis-compiler-bindist` im
[Snapshot-Repository](https://jars.interlis.guru/snapshots) publizieren. Das
Paket enthält POSIX- und Windows-Launcher und setzt Java 25 voraus. Der
Publisher übernimmt das geprüfte ZIP und seine Prüfsumme aus demselben
Workflow und prüft nach dem Upload die heruntergeladenen Bytes. Die
Publikationsrechte gehören zum Environment `compiler-snapshots`.
