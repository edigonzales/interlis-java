# Tests

Wähle Prüfungen anhand der betroffenen [Anforderungen](../contracts/requirements.md),
Verbraucher und Risiken. Ein JVM-Test belegt weder einen Browserlauf noch einen
CLI-Prozess. Ein erfolgreicher Parserlauf belegt keine vollständige semantische
Abdeckung. Wiederverwendbare Belege brauchen identische relevante Eingaben,
ausgeführte Prozesse, Rohberichte und bekannte Skips.

## Direkte Prüfungen

Führe JVM-Tests mit einem normalen Benutzerkonto aus. Die Dateirechtetests
prüfen tatsächliche Schreibverbote des Betriebssystems.

- `./gradlew test` oder `./gradlew check` führt die regulären JVM-Tests aus.
- `./gradlew :interlis-compiler:test --tests '*VersionScannerTest'` wählt eine
  JUnit-Klasse. `--tests '*VersionScannerTest.detectsUnsupportedVersion'`
  wählt eine Methode.
- `./gradlew packageTest` prüft die entpackten CLI- und SDK-Pakete, die
  SDK-Beispiele und den installierten LSP-Prozess. Der LSP-Check braucht Node.
- `./gradlew codebergConformance` prüft den gepinnten Codeberg-Stand für
  INTERLIS 2.3 und 2.4. Der Build prüft die SHA-256 des Archivs vom GitHub-Spiegel.
  `-PcodebergFilter='^ili23\.Class23Test\.classComplete$'` wählt einen Fall.
  Die [Erwartungen](../../testdata/conformance/normative/expectations.json) und
  [Baseline](../../testdata/conformance/baseline.json) enthalten die fachlichen
  Abweichungen. Die Berichte liegen unter `build/conformance/codeberg/`.
- `./gradlew swisstopoConformance -PswisstopoSuiteDir=../suite-interlis` prüft
  die Originaltests gegen die modulare Validator-Installation. Gradle gibt den Commit
  des Suite-Checkouts aus. Die Ergebnisse liegen unter
  `build/conformance/swisstopo/` und `build/test-results/swisstopoAdapterTest/`.
- `./gradlew guiImageTest` prüft das GUI-Laufzeitimage. `guiShellTest` und
  `:interlis-compiler-gui:guiProbe -PguiProbe=compile` benötigen einen Desktop.
  Weitere Profile sind `diagnostics`, `dependencies`, `repositories` und
  `lifecycle`. `guiAppTest` prüft das macOS-ARM64-Paket mit echten Fenstern.

- Die [Buildanleitung](build.md#portable-backends) zeigt die Node-Smokes für die
  erzeugten JS-/WasmGC-Artefakte. Der Portable-CI-Job führt `browserSmoke`
  und `validationBrowserSmoke` mit Chromium aus. Die Validatorprobe
  kompiliert ihr Modell und prüft neutrale Transferwerte im WasmGC-Artefakt.

## Testinhalte

Quelltexttests decken exakte Revisionen, UTF-16, astrale Zeichen, CR/LF,
Kommentare, Recovery und vollständigen Dateiverbrauch ab. Compilerprüfungen
unterscheiden gültige Closure, partielle Analyse, fehlende Eingaben,
abgelehnte Warnungen, Abbruch und interne Fehler. Semantische Goldens halten
Typen, Referenzen, effektive Reihenfolge, Übersetzungen, Rollen und Projektionen
fest. Nur Akzeptanz oder Exitcode genügen dafür nicht.

Workspace- und Editorprüfungen vergleichen frische und wiederverwendete
Analyse, späte Abhängigkeiten, Unicode-Deltas, Abbruch, stale Resultate,
Cachegrenzen und dokumentübergreifende Edits. Projektionsprüfungen kontrollieren
Identitäten, Filter, Budgetgrenzen und getrennte Layout-/Navigationseffekte.
Diagnosetests prüfen stabile Codes, strukturierte Argumente, Quellbereiche und
unterschiedliche Renderer. Laufbeobachtung muss deterministisch und isoliert
bleiben, auch bei einer fehlerhaften Senke.

Repositoryprüfungen verwenden lokale HTTP-Gegenstellen und feste Uhren.
Wichtige Gegenproben betreffen selektierte Version, negativen Treffer,
MD5-/SHA-Integrität, Fristgrenze, Offlinecache, Proxy, Redirect und Fehler.
CLI- und LSP-Verträge prüfen tatsächliche Streams, Status, Prozessabschluss,
Ausgabeatomizität, Revisionen und veröffentlichte Diagnosen.

Differentialtests unterscheiden normativ bestätigte Abweichungen,
Implementierungsunterschiede und Defekte der Referenzwerkzeuge. Die
[Sprachentscheidungen und ihre Normquellen](../contracts/norm-decisions.md)
geben den Massstab.
Robustheitsläufe brauchen begrenzte Eingaben, Zeit- und Ressourcenbudgets,
reproduzierbare Seeds sowie explizite Grenzen für Exits und Skips.

## Prüfergebnisse

JUnit-XML und Prozesslogs liegen in den Buildausgaben. Halte für eine Prüfung
Toolchain, Plattform, relevante Eingaben, Commit, Befehle, Exits und Skips fest.
Die [Freigabeprüfung](release.md) beschreibt die Auslieferungsprüfungen.

## ili2db-Interoperabilität

Die separate Gradle-Suite verwendet die offiziellen Distributionen von ili2gpkg
und ili2pg in Version 5.5.2. Der Task `:interlis-db:downloadIli2dbReferences`
lädt beide ZIPs von `downloads.interlis.ch` nach
`interlis-db/build/ili2db-reference/`. `dbInteropTest` ruft den Download-Task
automatisch auf. Mit vorhandenen ZIPs und Gradle-Abhängigkeiten im lokalen
Cache kann Gradle mit `--offline` laufen. Docker lädt fehlende Container-Images
bei Bedarf separat.

Für jeden PostgreSQL-Test startet und entfernt die Suite mit Testcontainers
einen PostgreSQL-16-Container mit PostGIS 3.4 (`sogis/postgis:16-3.4`).
Dazu muss Docker laufen. GeoPackage-Tests verwenden temporäre Dateien.

```sh
./gradlew :interlis-db:dbInteropTest
```

Die Auswahl `--tests '*DbInteropTest.geoPackage*'` läuft ohne Docker.
Die Tests vergleichen vor dem ersten Datenimport den SQL-Katalog und die
Metadaten der erzeugten Schemata. Die GeoPackage-Erweiterungseinträge für
Polylinien und Flächen werden separat geprüft. Die Tests vergleichen Punkte,
gerade und gebogene Segmente, Aussen- und Innenringe, die gemeinsame Kante
benachbarter `AREA`-Objekte, SRS und `NULL` für XTF 2.3 und 2.4 in beiden
Werkzeugrichtungen. In 2.3 prüfen sie auch die Zusammenführung eines
geteilten Randes. In 2.4 prüfen sie die geordneten Teile von
`MULTICOORD`, `MULTIPOLYLINE`, `MULTISURFACE` und `MULTIAREA` sowie den
Export leerer SQL-Sammlungen. Die Suite prüft den Import-Rollback bei
einem expliziten Bogenradius, offenem Ring oder einer leeren XTF-Sammlung,
den Erhalt der bisherigen XTF-Datei bei beschädigter Geometrie und einen
weiteren Referenzimport nach dem neuen Import. In beiden Sprachversionen
prüft sie auch optionale Einzelstrukturen, `LIST OF` und `BAG OF` mit demselben
Strukturtyp, Rückverweisspalten, `T_Seq`, Reihenfolge und Duplikate.
Sie löst einen Rollback nach geschriebenen Strukturzeilen aus und prüft den
Erhalt der Zieldatei bei einer beschädigten Rückverweiszuordnung.
Bei eingebetteten 1:n-Assoziationen prüft sie die Rollenmetadaten, eine
SQL-Namenskollision, Vorwärtsreferenzen, SQL `NULL` und den Rollback bei
einem unauflösbaren Ziel. Bei n:m-Assoziationen prüft sie die physische
Beziehungstabelle, anonyme Links, beide Rollenziele, Vorwärtsreferenzen,
skalare Attribute je Zielpaar und den Rollback bei einem fehlenden Ziel.
Für 1:n mit Attributen prüft sie eingebettete Attributwerte, direkte Referenzen,
fehlende Rollen und den Erhalt der Zieldatei bei mehreren Linkzeilen zum Objekt.
Bei 1:1-Assoziationen kehren die beiden Sprachfixtures die Deklarationsreihenfolge
um. Die Tests prüfen die jeweilige Rollenspalte, deren SQL-Nullbarkeit und die
gespeicherte Ziel-ID.
Das Originalwerkzeug lädt das Modell für das neue Schema aus der Datenbank.

Die JUnit-Rohberichte stehen unter `interlis-db/build/test-results/dbInteropTest/`.

## swisstopo-Validator-Konformität

Die Klasse `GebaeudeRegister` darf den Namen ihres Topics tragen.
Der Compiler akzeptiert diese Modellierung in 2.3 und 2.4 gemäss
[Entscheidung 09](../contracts/norm-decisions.md#decision-09).
Gradle kompiliert die unveränderten Originaltests zusammen mit dem
Validatoradapter. Die Suite prüft INTERLIS 2.3 und XTF 2.3 über den Launcher aus
dem entpackten Validator-ZIP. Bei `RHE.T05a/b` und `RSU.T17a/d` übergibt
der Adapter die Suite-Modelle explizit, damit der XTF-Reader den abweichenden
Modellnamen oder die Version im Header diagnostiziert. Für einen Einzelfall
verwende beispielsweise `-PswisstopoFilter='.*ConstraintTest\.RKo_T09a'`.

Die [Abweichungsliste](../../testdata/conformance/swisstopo-deviations.tsv)
dokumentiert die erwarteten Befunde. Technische Fehler, unerwartete Failures,
ein inzwischen bestandener Baselinefall und eine leere Auswahl lassen die
Prüfung fehlschlagen.

Am Upstream-Stand `f307111` bestehen 222 von 223 Originaltests.
`RKo.T09a` prüft UNIQUE über Rollenpfade eingebetteter Beziehungen.
`RSU.T05a/b` prüfen Aussenrand und berührende Enklave in einem Randzug
(INTERLIS 2.3 §2.8.13.1, Figur 22).
`RPO.T02a/b` prüfen unzulässige Überlagerungen und Schnitte im
Segmentinneren. Bei `RPO.T02c` ist die Kreuzung zulässig, weil beide
Durchgänge dort einen Segmentendpunkt haben. Die
[Normauslegung](../contracts/norm-decisions.md#gemeinsame-segmentendpunkte-bei-linienzügen)
dokumentiert diese Kompatibilitätsregel.

`RSU.T18a–e` prüfen `INTERLIS_ext.areAreas2` pro Objekt und `RSU.T19a–e`
prüfen `INTERLIS_ext.areAreas3` über eine Objektmenge. Beide Reihen
verwenden den TEXT-Pfad `Geom->Geom1->Geom2`. `T18d` und `T19e` sind
die positiven Originalfälle. ilivalidator 1.15.1 bestätigt die zehn Erwartungen.

Bei `RSU.T16a` akzeptieren
der native Validator und ilivalidator 1.15.1 den gelieferten Kurvenrand,
für den das Original einen Fehler erwartet.
Die [Abweichungen im Detail](swisstopo-suite-deviations.md)
beschreiben die Originalerwartungen und die beobachteten Befunde.

`RAt.T09e/g` prüfen die numerische Bereichsprüfung nach Rundung auf
die Modellpräzision. `RAt.T11a/b` prüfen, dass der Validator ungültige BID und TID bei einem
wirksamen `UUIDOID`-Wertebereich auch in XTF 2.3 FULL ohne `OIDSPACES` meldet.
