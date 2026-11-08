# Compiler-Freigabeprüfung

## Vertragsversionen

Vergleiche im Freigabereview die öffentlichen Verträge mit der letzten
veröffentlichten Version. Prüfe und dokumentiere die Versionsentscheidung
für jede betroffene Fläche separat.

- Öffentliche Java-SDK-API
- Diagnosekatalog
- Bridge-Protokoll
- Unterstützte INTERLIS-Sprachprofile
- Quelltextformatierung
- Diagrammschema

Erhöhe bei einem inkompatiblen Bruch die betroffene Vertragsversion im
selben PR und aktualisiere Verbraucher, Vertragstests und Dokumentation.
Begründe bei kompatiblen Änderungen die Versionsentscheidung. Die Tests
prüfen die Übereinstimmung der veröffentlichten Versionsangaben. Das Review
bewertet die Kompatibilität anhand der Verträge und ihrer Verbraucher.

## Plattformen und Ablauf

Der [Workflow](../../.github/workflows/compiler-platforms.yml) prüft Linux x64
und Windows x64 mit Java 25.0.3. Er führt die JVM-Tests und die installierten
SDK-, CLI- und LSP-Proben aus. Linux führt zusätzlich die vollständige
gepinnte Codeberg-Suite für INTERLIS 2.3 und 2.4 aus. Für eine
Browserveröffentlichung werden die zugesagten Browser mit den tatsächlichen
Browserartefakten geprüft.

Ein Push auf `main` kann nach erfolgreichen Pflichtjobs ein Compiler-ZIP
unter `guru.interlis:interlis-compiler-bindist` im
[Snapshot-Repository](https://jars.interlis.guru/snapshots) publizieren. Das
Paket enthält POSIX- und Windows-Launcher und setzt Java 25 voraus. Der
Publisher übernimmt das geprüfte ZIP und seine Prüfsumme aus demselben
Workflow und prüft nach dem Upload die heruntergeladenen Bytes. Die
Publikationsrechte gehören zum Environment `compiler-snapshots`.
