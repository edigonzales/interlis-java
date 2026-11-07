# Arbeitsregeln für interlis-java

- Bearbeite den erteilten Auftrag. Prüfe vor Änderungen Branch, Index und
  Arbeitsbaum, erhalte fremde Änderungen und lies die betroffenen
  [Verträge](docs/README.md) und [Modulgrenzen](docs/architecture.md).
  Veröffentlichungen und Arbeiten an Produktionssystemen brauchen einen
  konkreten Auftrag.
- Nutze [Jenesis 0.18.1 mit Java 25](docs/development/build.md) für alle
  [registrierten JVM-Module](build/interlis/JvmBuildInputs.java). POMs definieren
  Koordinaten und Abhängigkeiten. Der Gradle-Wrapper
  baut die beiden Wasm-Backends mit explizitem geprüftem ConsumerSnapshot.
  `build/jenesis/`, `build/interlis/`, `build/InterlisJvmBuild.java` und der
  Upstreampin sind Quellen. Verwende die gezielten Clean-Sichten.
- Unterstütze INTERLIS 2.3 und 2.4. ANTLR bleibt intern. Compiler und Modell
  arbeiten mit expliziten Daten und Diensten. Dateisystem, HTTP, JDBC, LSP und
  UI gehören an ihre Hostgrenzen.
- Erhalte unveränderliche, revisionstreue Syntax-, Analyse- und Schemasichten.
  Das Compiler-Gate entscheidet über die Gültigkeit der angeforderten Closure.
  Diagnostik und Laufbeobachtung verwenden ihre zentralen Datenverträge.
  Abbruch, Quellfehler, fehlende Eingaben, Warnungsablehnung und interne Fehler
  bleiben unterscheidbar.
- Behandle Wiederverwendung als Optimierung. Frische und wiederverwendende
  Analysen müssen fachlich übereinstimmen. Veraltete Editor- und
  Repositoryresultate werden vor Übernahme oder Veröffentlichung verworfen.
- Erhalte Reihenfolge, Herkunft, Typen, Übersetzungsbeziehungen, effektive
  Attribute und Rollen für spätere Verbraucher. Prüfe neue Semantik gegen
  die geltende Anforderung und Normentscheidung. Alte Werkzeuge liefern
  Referenzverhalten für die ausdrücklich benannten Kompatibilitätsprofile.
- Nutze die vorhandenen Typen und fachlichen Regeln auch in CLI, LSP,
  Projektionen und Browser. Produktionscode und API-Namen sind Englisch.
  Javadoc beschreibt relevante Nullfreiheit, Mutabilität, Reihenfolge,
  Revisionen, Einheiten und Fehlerzustände. Aktualisiere Verbraucher, Tests
  und Vertragsdokumente gemeinsam, wenn sich öffentliche Verträge ändern.
- Halte bei Änderungen öffentlicher Verträge im PR fest, welche veröffentlichte
  Vertragsversion betroffen ist. Erhöhe sie bei einem inkompatiblen Bruch im
  selben PR. Begründe bei kompatiblen Änderungen die Versionsentscheidung.
  Die Prüfliste steht in der [Freigabeprüfung](docs/development/release.md#vertragsversionen).
- Wähle die kleinsten [aussagekräftigen Prüfungen](docs/development/testing.md).
  Führe breite Korpus-, Browser-, Plattform- und Benchmarkläufe aus, wenn
  eine betroffene Abnahme sie fordert. Unabhängige Reviews prüfen Originalbelege
  und eigene gezielte Gegenproben. Belastbare Nachweise dürfen wiederverwendet
  werden, wenn relevante Eingaben und Produktstand übereinstimmen.
- Erfasse Befehle, Werkzeugversionen, Prüfbereiche, Exits, Skips und offene
  Nachweise. Ein abgebrochener oder übersprungener Lauf zählt nicht als Erfolg.
  Ändere Goldens, XFAILs und erwartete Diagnosen nur mit fachlicher Begründung.
  Ein vollständiges Freigabegate richtet sich nach
  [development/release.md](docs/development/release.md).
