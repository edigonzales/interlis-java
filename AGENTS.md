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
- Readers and writers are codecs, not data validators. Transfer adapters enforce
  wire syntax, structural decodability, protocol rules, security and resource
  limits. They may use model information to interpret the wire format. They
  must accept decodable data even when it violates model constraints. Domain
  ranges, mandatory attributes, referential existence and geometry topology
  belong to a separate validation component.
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

## Testphilosophie

- Halte Tests direkt und verständlich. Testfälle sind Java-/JUnit- oder
  JavaScript-/Node-Code; INTERLIS- und Transferfixtures liegen als `.ili` bzw.
  `.xtf` vor. Kurze lokale Quelltexte und Parameterisierung im Test sind erlaubt.
- Keine separaten JSON-/YAML-/XML-/Properties-Testregistrierungen, Norm-Test-
  Mappings, generierten Testpläne oder künstlichen Coverage-Inventare. Keine
  gleichwertige Ersatzregistry in Java. Einzige fachliche Ausnahme ist die
  externe Codeberg-Conformance-Suite mit ihren eng begründeten Abweichungen.
- JSON als Produktprotokoll, fachliches Golden, Abhängigkeits-Lock oder notwendiges
  Buildmanifest ist kein Testkatalog. Produkt- und Buildintegrität bleiben erhalten.
- Sprachsemantik und öffentliche Schemaergebnisse werden umfassend auf der JVM
  geprüft. JS/WasmGC prüfen die echten generierten Laufzeiten mit wenigen
  aussagekräftigen Integrationsfällen und gezielten Backendregressionen. Keine
  pauschale Duplizierung der gesamten Sprachtestsuite über alle Laufzeiten.
- Wähle die kleinste ausreichende Testauswahl für eine Änderung. Breite
  Modultests gehören an sinnvolle Integrationspunkte; vollständige Browser-,
  Benchmark- oder Referenzmatrizen sind kein Standard nach jedem Edit oder Review.
- Keine doppelte Testausführung allein für einen zweiten Nachweis. Unveränderte
  gültige Läufe dürfen verwendet werden; Produkt, Tests, Fixtures, relevante
  Buildoptionen und Toolchain müssen dazu passen. Nie einen nicht ausgeführten,
  abgebrochenen oder übersprungenen Pflichtlauf als Erfolg ausgeben.
- Tests prüfen fachliche Ergebnisse, Fehlerzustände und Isolation, nicht nur
  erfolgreiche Exits oder Mindestanzahlen. Gezielte Originalnorm-/Issue-Verweise
  im Test sind sinnvoll; eine maschinelle Vollständigkeitsbehauptung ist es nicht.
- JUnit-/Node-Discovery und native Reports haben Vorrang vor eigenen Frameworks,
  Profilgraphen, Reportprojektionen und Belegsammlern. Prozess-, Timeout-,
  Leerauswahl- und Fehlerweitergabe müssen zuverlässig bleiben.
- Entferne ersetzte Testinfrastruktur mit ihren Aufrufern, Tags und Dokumentations-
  vorgaben. Keine Legacy-Aliase, Dummyreports oder parallelen Ersatzpfade.
- Erhalte konkrete Regressionen, insbesondere Quellbereiche, Typen, Referenzen,
  Sprachversionsgrenzen, Abbruch, Ressourcenlimits und frische versus
  wiederverwendete Analysen. Keine Schwächung von Assertions für einen grünen Build.
- Unabhängige Reviews prüfen Originalcode, Ziele und eigene gezielte Gegenproben.
  Sie müssen nicht identische breite Läufe wiederholen. Ein Bericht dokumentiert
  den geprüften Commit, Befehle, Ergebnisse und offene Grenzen ohne eigene
  neue maschinenlesbare Review-/Gatearchitektur.

Die [Testanleitung](docs/development/testing.md) und die
[Freigabeprüfung](docs/development/release.md) beschreiben die direkten Aufrufe
und die Prüfungen für die Auslieferung.
