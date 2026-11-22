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

JUnit führt bis zu zwei Fälle gleichzeitig aus. Für jeden PostgreSQL-Test
startet und entfernt die Suite mit Testcontainers einen PostgreSQL-16-Container
mit PostGIS 3.4 (`sogis/postgis:16-3.4`). Dazu muss Docker laufen.
Jeder Fall entpackt sein Referenzwerkzeug in ein eigenes temporäres
Arbeitsverzeichnis. JUnit löscht dieses Verzeichnis samt GeoPackage-Dateien,
Prozesslogs und temporären Dateien des Referenzwerkzeugs auch bei Testfehlern.

Die `*ArrayText*`-Fälle prüfen Reihenfolge, Duplikate, Sonderzeichen,
gespeicherte SQL-Spaltennamen und den bekannten Referenzfehler bei
`NULL`-Elementen. Die `*ArrayBoolean*`-Fälle prüfen die SQL- und
JSON-Typen. Fehlerhafte Array-Werte lassen die XTF-Zieldatei intakt.
Die `*ArrayInteger*`-Fälle prüfen typisierte Ganzzahlen und den Rollback,
wenn ein Wert die 32-Bit-Kodierung übersteigt. Die `*ArrayEnumeration*`-Fälle
prüfen benannte Aufzählungen und qualifizierte XTF-Codes. Die
`*ArrayBag*`-Fälle prüfen Duplikate, fehlende Werte und die gruppierte
XTF-2.4-Ausgabe von ili2db.

Die `*ExpandSingle*`-Fälle prüfen den Austausch optionaler flacher
Strukturen mit ili2db-`--expandStruct`, das Umbenennen einer SQL-Spalte
und den Erhalt der XTF-Datei bei beschädigtem Strukturtyp. Die
`*ExpandList*`-Fälle prüfen fünf Positionen, den Wechsel zur
Strukturtabelle bei Obergrenze sechs und Rollback bei einer zu langen Liste.
Die `*ExpandBag*`-Fälle prüfen doppelte und leere Einträge sowie die
gruppierte XTF-2.4-Referenzausgabe. Die `*ExpandNested*`-Fälle prüfen
beide Typmarkierungen und erkennen innere Werte ohne äusseren Strukturtyp.
Die `*ExpandSchema*`-Fälle vergleichen den Katalog mit der Referenz. Sie
prüfen kollidierende Spaltennamen, die gemeinsame ID-Vergabe und
`--add-models` mit der gespeicherten Option. Die `*ExpandSmart2*`-Fälle
prüfen zwei konkrete Eigentümertabellen, stabile IDs und freigewordene
Spalten nach `update` sowie die Datensatzzeilen nach `replace` und `delete`.
Die `*JsonStructure*`-Fälle prüfen `--coalesceJson` für Einzelstrukturen,
typisierte Werte, SQL `NULL` und den Erhalt der XTF-Datei bei fehlerhaftem JSON.
Die `*JsonCollections*`-Fälle prüfen flache Arrays in beiden
Werkzeugrichtungen. Für innere JSON-Objekte vergleichen sie die
gespeicherten Werte und prüfen den Export mit `interlis-db` sowie den
bekannten Exportfehler von ili2db 5.5.2. Die `*JsonSchema*`-Fälle
vergleichen den Katalog, kombinieren JSON und expandierte Spalten in
derselben Tabelle und prüfen `update`, `--add-models` sowie die
gemeinsame ID-Vergabe.

<a id="ili2db-json-export-reproduzieren"></a>

Für die Reproduktion des JSON-Exportfehlers genügen die vier Fälle unten.
Das jeweilige Referenzwerkzeug erstellt das Schema mit `--coalesceJson`,
importiert den Datensatz mit einer leeren inneren Struktur und versucht,
ihn mit `--exportTid` zu exportieren. Der Test bestätigt den Fehlertext
und prüft den erfolgreichen Export aus derselben Datenbank mit
`interlis-db`. [Fehlerbeschreibung und Beispielwert](../usage/database.md#ili2db-json-exportfehler)

```sh
./gradlew :interlis-db:dbInteropTest --tests '*DbInteropTest.*JsonCollections*'
```

`*DbInteropTest.*CatalogueReference*` prüft beide Werkzeugrichtungen
für Katalogreferenzen mit `--coalesceCatalogueRef` auf GeoPackage und
PostGIS. Die Fixtures verwenden die CHBase-V1-Strukturform in beiden
Sprachversionen. Die Fälle prüfen die Ziel-ID im separaten Katalogdatensatz,
leere Strukturen, Rollback bei fehlendem Ziel und fehlerhafte Metadaten.
`*DbInteropTest.*CatalogueSmart2*` prüft zusätzlich alternative Zielspalten,
mehrdeutige Ziel-TIDs und den atomaren Export bei doppelt belegten Spalten.
`*DbInteropTest.*CatalogueSchema*` vergleicht den SQL-Katalog der
`newClass`- und Smart2-Schemata mit ili2db. Die Mischläufe prüfen
anschliessend die gemeinsame ID-Vergabe und den Schutz referenzierter
Katalogdatensätze beim Löschen.

`*DbInteropTest.*ChbaseMultilingual*` prüft beide Werkzeugrichtungen für
`--expandMultilingual`, den gespeicherten Spaltenstamm, die Sprachspalten,
mehrzeilige Inhalte sowie Rollback bei nicht abbildbaren Sprachwerten.
`*DbInteropTest.*ChbaseLocalised*` prüft `--expandLocalised`, Sprache,
mehrzeilige Inhalte, Leertext, SQL-`NULL` und den atomaren Export bei
fehlender Sprachspalte.
`*DbInteropTest.*ChbaseTextSchema*` vergleicht beide Textabbildungen
im gemeinsamen Schema mit ili2db und prüft die ID-Vergabe mit einem
weiteren Referenzimport.
`*DbInteropTest.*ChbaseSmart2*` prüft die geerbten Textspalten und die
ausgelagerte Multipunkt-Geometrie gemeinsam. Die Fälle vergleichen die
Schemata, prüfen beide Werkzeugrichtungen und kontrollieren beim
Aktualisieren und Löschen IDs, Hilfszeilen und Raumindizes.
`*DbInteropTest.*ChbasePoints*` prüft `--coalesceMultiPoint` in
beiden Werkzeugrichtungen, einschliesslich doppelter Punkte, SRID,
SQL-`NULL` und der leeren Strukturtabellen.
`*DbInteropTest.*ChbaseLines*` prüft `--coalesceMultiLine` mit geraden
und gebogenen Segmenten sowie gerichteten Linien.
`*DbInteropTest.*ChbaseSurfaces*` prüft `--coalesceMultiSurface` mit
mehreren Flächen, Innenring, Kreisbogen, abweichendem SRID und atomarem
Export bei einer beschädigten Geometrieabbildung.
`*DbInteropTest.*ChbaseGeometrySchema*` vergleicht die erzeugten
Schemata für die drei Geometrieabbildungen mit ili2db. Die Mischläufe
prüfen danach Geometriewerte, SRID, die gemeinsamen IDs und einen
weiteren Referenzimport.

`*DbInteropTest.*Smart*` prüft Smart1 und Smart2 mit XTF 2.3 und 2.4.
Die Fälle vergleichen beide Werkzeugrichtungen, physische Eigentümer
und Zielspalten, Geometriezuordnungen und Datensatzmutationen. Die
Schemaerzeugung wird gegen den Katalog von ili2db geprüft. Beim
Smart2-Vergleich wird die Reihenfolge innerhalb eines Satzes
alternativer Fremdschlüsselspalten normalisiert, da ili2db sie aus
einer Menge ermittelt.

`*DbInteropTest.*SharedSource*` prüft importierte Typen aus derselben
gespeicherten Modelldatei, die Fachwerte und CRS sowie beide
Werkzeugrichtungen mit dem Referenzschema.
`*DbInteropTest.*StoredImports*` prüft transitive und gemeinsam genutzte
Quellen in mehreren gespeicherten Dateien. Fehlende und mehrdeutige
Quellen müssen die vorherige Exportdatei erhalten.
`*DbInteropTest.*LocalImports*` vergleicht die erzeugten Modellzeilen
und das SQL-Schema mit ili2db. Danach laufen beide Werkzeugrichtungen
mit den gespeicherten Quellen.
`*DbInteropTest.*ImportedStruct*` prüft eine gemeinsam verwendete
importierte Struktur als Einzelwert, Liste und Bag. Es vergleicht
die Abbildung, die Eigentümer und Positionen sowie die Qualifikation
der Werte im XTF.
`*DbInteropTest.*ImportedTopic*` prüft den Umfang der Abbildung bei
`DEPENDS ON`, den Werkzeugwechsel mit einem separaten Zieldatensatz
und den Rollback bei mehrdeutigen Ziel-TIDs.
`*DbInteropTest.*JointSchema*` prüft zwei eigenständige Modelle mit
gleichnamigen Topics und Klassen, ihre gespeicherten SQL-Zuordnungen,
die gemeinsame ID-Vergabe und beide Werkzeugrichtungen.
`*DbInteropTest.*MixedExport*` prüft Header und Baskets gemischter
Datensätze, eine unvollständige Auswahl und getrennte Datensätze beider
XTF-Versionen im selben Referenzschema.
`*DbInteropTest.*MixedImport*` prüft gemischte Lieferungen in Referenz-
und eigenen Schemata, die gemeinsame ID-Vergabe beim nächsten Referenzimport
und den Rollback bei einem fremden späteren Basket.
`*DbInteropTest.*MixedMutation*` prüft selektive Updates mit stabilen IDs,
die vollständige Auswahl für Ersatz und Löschung sowie den Rollback bei
eingehenden Referenzen aus einem weiteren Modell.
`*DbInteropTest.*AppendModels*` ergänzt ein befülltes Referenzschema
mit einer Namenskollision und einer gemeinsam genutzten Struktur. Es
vergleicht den Katalog nach einem Abbruch während der DDL und prüft danach
den Werkzeugwechsel mit den bestehenden und neuen SQL-Zuordnungen.

`*DbInteropTest.*Translation*` prüft Quellenbindung, Originaltabellen,
skalare und verschachtelte Werte, Schemaerstellung, Modellergänzung und
die explizite Ausgabesprache. Die Fälle vergleichen XTF 2.3 mit übersetzten
Baskets und XTF 2.4 mit den Namen des Originals.
`*DbInteropTest.*Topic*` umfasst zusätzlich geerbte Klassen,
basketübergreifende Abhängigkeiten und Klassenfragmente, abstrakte
Basistopics mit verengten Textattributen sowie lokale Rollen und
Referenzen in abgeleiteten Baskets. `*DbInteropTest.*TopicMutations*`
prüft bei übersetzten und abgeleiteten Baskets die stabilen IDs beim
Update, Ersatz und Löschung sowie Rollback bei eingehenden Referenzen.

Die Fälle `*DbInteropTest.*Mutation*` prüfen `replace` und `update`
in beiden Werkzeugrichtungen. Sie prüfen die internen IDs und den Bestand
in SQL, exportiertes XTF, besitzende Datensätze, Referenzen und Rollback.
Die Auswahl umfasst beide XTF-Versionen und beide Backends.

Die Fälle `*DbInteropTest.*Delete*` prüfen das Löschen benannter
Datensätze und die Wiederherstellung aller Zeilen beim Rollback.
`*DbInteropTest.*Spatial*` prüft die Raumindizes aus beiden Schemaerzeugern,
den Werkzeugwechsel und die räumlichen Treffer nach Schreiboperationen.
Die GeoPackage-Fälle prüfen zusätzlich die RTree-Trigger und den Indexinhalt.

`*DbInteropTest.*Xyz*` prüft XYZ-Geometrien und die XY-Indexhüllen.
`*DbInteropTest.*GeometryLayout*` prüft mehrere Spalten und Hilfstabellen,
`*DbInteropTest.*GeometryHierarchy*` deren Verwendung in Klassenhierarchien.
Die Tests vergleichen Kataloge beider Schemaerzeuger und wechseln für Import,
Export und Mutationen zwischen den Werkzeugen. Bei Klassenhierarchien
mit Geometrie-Hilfstabellen prüfen sie den Referenzimport und reproduzieren
den [Fehler des Referenzexports](../usage/database.md#referenzprofil)
für dieses Layout.

`*DbInteropTest.*Crs*` prüft Schema- und Modellstandards, Koordinatendomänen
und Stützpunktdomänen. `*DbInteropTest.*MixedSrid*` prüft mehrere EPSG-Codes
pro Objekt, die Kataloge beider Schemaerzeuger und räumliche Treffer nach
`update`, `replace` und `delete`. Es prüft auch fehlerhafte Spaltenmetadaten
und den Rollback eines erst beim zweiten Objekt fehlgeschlagenen Ersatzes.
`*DbInteropTest.*LocalSrid*` prüft abweichende lokale GeoPackage-SRS-IDs
und die Ablehnung nicht eindeutiger Katalogzuordnungen.

`*DbInteropTest.*FixedMultiSrs*` prüft EPSG-suffigierte Spalten in beiden
XTF-Versionen. Die XTF-2.4-Fälle mit `Deferred` im Namen prüfen die Domänenwahl
pro Basket, die XTF-2.3-Fälle mit `AlternateCrs` das alternative CRS-Modell.
`*DbInteropTest.*BasketCrsMutation*` prüft in beiden Transferformaten
unveränderte IDs beim Update, CRS-Wechsel beim Ersatz, Rollback und Raumindizes.

Die Tests zu aufgeschobenen `VERTEX`-Domänen prüfen den Werkzeugwechsel
mit `--disableValidation` auf der ili2db-Seite. Der Validator von 5.5.2
bricht bei generischen Stützpunktdomänen mit offenen Achsgrenzen ab.

Für `EXTERNAL`-Referenzattribute prüft die Suite frühere und spätere Ziele
im selben Datensatz, den konkreten Zieltyp, die Datensatzisolierung und
den Rollback bei fehlendem Ziel oder falscher BID. Sie liest auch einen
XTF-2.3-Referenzexport ohne Ziel-BID erneut ein.
Ein separat importierter Katalog prüft zusätzlich die Suche im selben Schema,
den Wiederimport des Quellexports und den Fehler bei mehrdeutigem Ziel.
Die UUID-Mischläufe vergleichen die physische Identitätsspalte,
die `oidDomain` und die normalisierte UUID in SQL und XTF.
Bei eingebetteten 1:n- und 1:1-Rollen ohne eigene Attribute prüft sie die
Rollenspalten am Eigentümer, ausgelassene Rollen und Rollback bei einem
Ziel ausserhalb des deklarierten Hierarchiezweigs.
Bei eigenständigen n:m-Links prüft sie die beiden SQL-Schlüssel und
die anonymen XTF-Links. Eine basketfremde lokale Rolle löst einen Rollback aus.
Bei attributierten eingebetteten 1:n-Links vergleicht sie die verschachtelte
Nutzlast, die direkte Referenz mit SQL `NULL` und das Fehlen der Rolle.
Eine zusätzliche Linkzeile zum selben Eigentümer lässt den Export scheitern.
Ein eigenes Modell mit `DEPENDS ON` prüft die Baskets beider Topics,
die Zuordnung zur deklarierten Zielklasse und die konkrete Ziel-TID.
Bei eingebetteten 1:n-Rollen zwischen Topics prüft es die Rollenspalte
am lokalen Eigentümer und die eingebettete Nutzlast aus einer Linktabelle.
Bei eigenständigen n:m-Links und 1:n-Links mit fremder n-Seite prüft es
den Basket des Links, beide Ziel-TIDs und die eigenständigen XTF-Objekte.

```sh
./gradlew :interlis-db:dbInteropTest
```

Die `*StrokePolyline*`-Fälle prüfen den Austausch gerader XY-Polylinien
mit ili2db-`--strokeArcs`, gespeicherte SQL-Namen und das Verhalten bei
widersprüchlichen Spaltenmetadaten. Die `*StrokeArcs*`-Fälle vergleichen
die Stützpunktfolge eines gewöhnlichen Kreisbogens mit der Referenz und
prüfen grosse Bögen, Vollkreise und den Rollback am Koordinatenlimit. Die
`*StrokeSurface*`-Fälle prüfen `POLYGON`, Innenringe und den Erhalt der
XTF-Datei bei beschädigtem WKB oder falschem Katalogtyp. Die
`*StrokeArea*`-Fälle vergleichen die Koordinaten gemeinsamer gebogener
Kanten und prüfen in XTF 2.3 einen mehrteiligen Rand. Die
`*StrokeMultiPolyline*`-Fälle prüfen Reihenfolge und Richtung von
`MULTIPOLYLINE`, `MultiLine` und `MultiDirectedLine`. Die
`*StrokeMultiSurface*`-Fälle prüfen `MULTIPOLYGON`, Teilflächen und
Innenringe bei `MULTISURFACE`, `MULTIAREA` und CHBase-`MultiSurface`
sowie leere SQL-Sammlungen. Die `*StrokeSchema*`-Fälle vergleichen
den Katalog mit einem ili2db-Schema, prüfen die gemeinsame ID-Vergabe
und ergänzen ein Modell mit der gespeicherten Option. Die
`*StrokeSmart2*`-Fälle prüfen eine geerbte Geometrie in einer indizierten
Hilfstabelle. Sie vergleichen SQL-IDs und räumliche Abfragen vor und
nach `update` und `delete`.

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
Bei verschachtelten Strukturen prüft sie Rückverweise auf den direkten
Elternteil, die Reihenfolge bei vertauschten SQL-IDs und den Rollback
nach geschriebenen Enkelzeilen.
Bei Strukturreferenzen prüft sie das bereits gespeicherte SQL-Ziel,
Vorwärtsauflösung, fehlende Werte, Rollback bei fehlendem Ziel und
eine Diagnose für ein basketfremdes Ziel beim Export.
In Quellhierarchien prüft die Suite Referenzspalten auf der Wurzel, einer
Zwischenstufe und dem Blatt. Die Vorwärtsauflösung muss die jeweilige
Deklarationstabelle aktualisieren. Ein fehlendes Ziel rollt auch bereits
geschriebene Erweiterungszeilen zurück.
Sie löst einen Rollback nach geschriebenen Strukturzeilen aus und prüft den
Erhalt der Zieldatei bei einer beschädigten Rückverweiszuordnung.
Bei eingebetteten 1:n-Assoziationen prüft sie die Rollenmetadaten, eine
SQL-Namenskollision, Vorwärtsreferenzen, SQL `NULL` und den Rollback bei
einem unauflösbaren Ziel. Bei n:m-Assoziationen prüft sie die physische
Beziehungstabelle, anonyme Links, beide Rollenziele, Vorwärtsreferenzen,
skalare Attribute je Zielpaar und den Rollback bei einem fehlenden Ziel.
Bei eigenständigen Links mit Hierarchie-Enden prüft sie polymorphe
Vorwärtsreferenzen an beiden Rollen, die TIDs und die skalaren Werte.
Ein Endpunkt aus einem Geschwisterzweig rollt den Datensatz zurück. Beim
Export bleibt die bisherige Datei bei typ- oder basketfremden Zielen erhalten.
Bei eigenständigen Links mit drei Rollen prüft die Suite die Spalten und
die XTF-Reihenfolge aller Rollen sowie die skalaren Werte. Ein Ziel ausserhalb
des deklarierten Zweigs der dritten Rolle führt zu Rollback oder Exportabbruch.
Für binäre n:m-Links mit einer oder zwei `ORDERED`-Rollen prüft die Suite
die Sequenzspalten und die Rollenziele in beiden Werkzeugrichtungen. Sie
speichert Positionen zusätzlich direkt im SQL-Schema und prüft Import und
Export mit `interlis-db` auf exakte Positionswerte, SQL `NULL`, Rollback bei
einem Überlauf und Erhalt der Ausgabedatei bei einer ungültigen SQL-Position.
Bei eingebetteten 1:n-Rollen ohne eigene Attribute prüft sie die
Sequenzspalte auf der n-Seite, die Position auf der Gegenrolle im XTF,
ein vorwärts aufgelöstes Ziel und die Diagnose einer Position ohne Referenz.
Bei 1:n-Rollen mit eigenen Attributen prüft sie die Positionsspalte am
Rückverweis der Linktabelle und die Kombination aus Position, Referenz
und Attributwerten. Die Fixtures enthalten direkte, eingebettete und
ausgelassene Rollen.
Für 1:n mit Attributen prüft sie eingebettete Attributwerte, direkte Referenzen,
fehlende Rollen und den Erhalt der Zieldatei bei mehreren Linkzeilen zum Objekt.
Polymorphe Ziele werden mit einer Zwischenklasse und einem konkreten
Nachfahren aus derselben Hierarchie geprüft.
Bei einer Blattklasse als Eigentümer prüft die Suite die Linknutzlast am
Blatt und einen beschädigten Rückverweis auf ein Geschwisterobjekt.
Attribuierte 1:1-Assoziationen werden mit beiden Deklarationsrichtungen
und mit direkter, eingebetteter und fehlender Rolle geprüft.
Bei 1:1-Assoziationen kehren die beiden Sprachfixtures die Deklarationsreihenfolge
um. Die Tests prüfen die jeweilige Rollenspalte, deren SQL-Nullbarkeit und die
gespeicherte Ziel-ID.
Für eingebettete 1:n- und 1:1-Assoziationen mit Hierarchie-Enden prüft die Suite
die Rollenspalte auf der abstrakten Basis und auf einem Zwischenfragment,
Referenzen am konkreten XTF-Objekt, Selbstbezüge und fehlende Werte. Ziele im
falschen Zweig oder Basket werden diagnostiziert.
Die Suite prüft ausserdem eine eigenständige Aggregation und eine eingebettete
Komposition mit Hierarchie-Enden anhand ihrer SQL-Zuordnungen und XTF-Rollen.
Bei Klassenvererbung prüft die Suite konkrete und abstrakte Basisklassen,
Geschwistertypen, mehrstufige Pfade, den Diskriminator und die gemeinsame ID
der Teilzeilen. Beschädigte Daten und Vererbungsmetadaten müssen den
Export abbrechen und eine bestehende Zieldatei erhalten.
Bei einer erweiterten eigenständigen Assoziation prüft die Suite
die gemeinsame `T_Id`, den konkreten Linktyp ohne TID, beide physischen
Rollenzuordnungen und die Attributwerte der beiden Deklarationstabellen.
Fehlt eine Erweiterungszeile, bleibt die bisherige XTF-Datei erhalten.
Geometrien in Klassenhierarchien werden anhand ihrer Deklarationstabellen,
der räumlichen Katalogeinträge, der Koordinaten und der Werte im XTF-Objekt
geprüft. Die Suite deckt mehrere Geometrien auf einem Tabellenpfad, fehlende
Geometrien und eine beschädigte Geometrie in einer Teilzeile ab.
Bei geerbten Strukturattributen prüft die Suite die Deklarationstabelle,
Listenpositionen und verschachtelte Einzelwerte. Ein Rückverweis auf die
ID eines Basisobjekts ohne die erforderliche Erweiterungszeile wird
diagnostiziert.
Bei Strukturvererbung prüft die Suite Einzelwerte, `LIST OF` und `BAG OF`,
die konkreten XTF-Typen, die gemeinsame `T_Id` und den Basket der Teilzeilen
sowie `T_Seq` und die Rückverweise auf der Strukturwurzel. Ein Typ ausserhalb
des deklarierten Zweigs bricht den Export ab und erhält die bisherige Datei.
Bei polymorphen Referenzen prüft die Suite die Metadaten für den
deklarierten Zieltyp und die XTF-TID von konkreten Nachfolgern. Die
Fixtures prüfen Vorwärtsreferenzen, SQL `NULL` sowie ungültige Typzweige
und basketfremde Referenzen. Ein GeoPackage-Fall prüft den Export eines
Objekts mit 32 polymorphen Referenzfeldern.
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
