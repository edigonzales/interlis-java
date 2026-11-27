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

Der Compiler-Regressionstest verwendet [IlisMeta16, Version 2022-10-10](../../testdata/conformance/metamodel/IlisMeta16.ili)
aus dem [INTERLIS-Modellrepository](https://models.interlis.ch/core/IlisMeta16.ili)
bytegetreu (SHA-256 `a6aecc507b8000f3cead3ae593200491153540ae4c74e033a4af4af8e3935dda`).
Derselbe Modelltext liegt mit LF-Zeilenenden im
[ili2c-Quellstand b93884b](https://github.com/claeis/ili2c/blob/b93884b2a12ce294e563738c7e901f800e3719b5/standard/IlisMeta16.ili).
Die [LGPL 2.1](../../testdata/conformance/metamodel/LICENSE.lgpl) liegt bei der Fixture.
Der Test prüft die Bindung von `BASKET OID AS` an die Modelldomäne und
die Rollen der gleichnamigen Topic-Assoziation.

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

## Aufbau der Datenbanktests

Die Testquellen liegen unter `interlis-db/src/`.

| Testquellen | Gradle-Aufgabe | Inhalt |
| --- | --- | --- |
| `test` | `:interlis-db:check` | Modultests für CLI-Optionen, SQL-Abbildung und interne Verträge ohne Datenbankserver |
| `dbTest` | `:interlis-db:dbTest` | Eigene CLI und JDBC gegen echte Datenbanken, mit gezielten SQL-Prüfungen, Fehlerfällen und Rollback |
| `dbInteropTest` | `:interlis-db:dbInteropTest` | Katalogvergleich und Austausch mit ili2gpkg und ili2pg als separaten Java-Prozessen |
| `dbTestSupport` | Gemeinsame Testquellen | JDBC-, CLI- und XTF-Helfer sowie gemeinsam verwendete `.ili`- und `.xtf`-Fixtures |

Beide DB-Suiten rufen die eigene CLI über `Main.run` im JUnit-Prozess auf.
Die Mischläufe prüfen beispielsweise den Import mit ili2db und den Export
mit `interlis-db` in derselben Datenbank. DB-Tests legen GeoPackage-Dateien in
`@TempDir` an. Für PostgreSQL 16 mit PostGIS 3.4 startet Testcontainers
einen Container pro Klasse. Die Fälle erhalten getrennte Schemata.
Tests, die den datenbankweiten Katalog verändern, verwenden einen separaten
Container. JUnit führt je Suite bis zu zwei Fälle parallel aus.

Der [Linux-Push-Workflow](../../.github/workflows/database-tests.yml) führt
`dbTest` bei jedem Push aus. Bei Mapping-Änderungen laufen die betroffenen
Mischläufe gezielt. Die vollständige Referenzsuite ist für DB-Freigaben
und für einen Wechsel der ili2db-Referenzversion vorgesehen.

```sh
./gradlew :interlis-db:check
./gradlew :interlis-db:dbTest --tests '*BaseMappingStorageTest.storesBaseMappingsAndRejectsDamagedAttribute'
./gradlew :interlis-db:dbInteropTest --tests '*BaseMappingInteropTest.geoPackage*'
```

`--tests '*InputSelectionInteropTest.geoPackageImport*'` und
`--tests '*InputSelectionInteropTest.postgisImport*'` vergleichen die
BID-/Topic-Filter, leere Auswahlen und leere FULL-Dateien mit ili2db.
`InputSelectionStorageTest` prüft den Import ohne Basket-Spalte, externe
Zielauflösung und Rollback bei fehlerhaftem XML in einem ausgelassenen Basket.
`--tests '*InputSelectionInteropTest.*Update*'` prüft den selektiven
Basket-Ersatz und stabile IDs. Der eigene DB-Test prüft den Rollback bei
einer Referenz aus einem ausgelassenen Basket.
`--tests '*MultiFileImportInteropTest'` prüft den Werkzeugwechsel mit mehreren
XTF-Dateien, dateiübergreifende `EXTERNAL`-Referenzen und die Importstatistik.
`MultiFileImportStorageTest` prüft unter anderem Rollback bei Fehlern in
einer späteren Datei.
`--tests '*MultiFileMutationInteropTest.*Update*'` prüft den Werkzeugwechsel
nach einem gefilterten Mehrdatei-Update. `MultiFileMutationStorageTest` prüft
den Rollback bei fehlerhaftem XML in einer späteren Datei.
`--tests '*MultiFileMutationInteropTest.*Replace*'` prüft den Werkzeugwechsel
nach einem Mehrdatei-Ersatz. Die eigenen DB-Fälle prüfen doppelte Kennungen
und den Erhalt des bisherigen Bestands bei Fehlern.
`--tests '*MultiFileMutationInteropTest.*RepeatedBasket*'` prüft dieselbe
BID über mehrere Update-Dateien. Die eigenen DB-Fälle prüfen den Rollback
bei Referenzen auf verworfene Zwischenobjekte.
`--tests '*InputSelectionInteropTest.*UnpartitionedImport*'` vergleicht die
Datensatz- und Basketmetadaten sowie die Fachwerte beim Werkzeugwechsel
in Schemata ohne `T_basket`.

Der GeoPackage-Selektor läuft ohne Docker. JUnit-XML liegt pro Aufgabe unter
`interlis-db/build/test-results/` und der HTML-Bericht unter
`interlis-db/build/reports/tests/`.

## Eigene Datenbanktests

`./gradlew :interlis-db:dbTest` führt alle eigenen DB-Tests aus. Die
Suite benötigt keine ili2db-Distributionen.

`ArrayStorageTest` prüft XTF 2.3 und 2.4. Die Fälle vergleichen Text und
Leertext, SQL `NULL`, leere Arrays, Boolean- und Integer-Werte direkt in der
Datenbank. Für Exportprüfungen setzen sie die Arraywerte per JDBC. Sie prüfen
auch den Erhalt einer bestehenden XTF-Datei bei falschen SQL-Typen,
beschädigten Elementen oder einem `NULL`-Element in einem direkten XTF-2.4-Array.
Der Integerfall prüft den Rollback von Objektzeilen, Basket und Datensatz,
wenn ein späteres Element die 32-Bit-Kodierung übersteigt.
Ein paralleler Import mit denselben IDs in einem zweiten PostgreSQL-Schema
prüft, dass der Rollback die dort gespeicherten Zeilen intakt lässt. Die Fälle prüfen auch umbenannte
Array-Spalten samt gespeicherten Zuordnungen und den Erhalt der XTF-Datei
bei mehreren Elementen für eine optionale Einzelstruktur.

`ArrayReferenceStorageTest` prüft Rollback bei fehlenden Array-Zielen,
den Erhalt der Exportdatei bei basketfremden Referenzen und den Schutz
referenzierter Objekte bei Update, Replace und Delete. Die Gegenprobe
umfasst auch gespeicherte Referenzen aus einem Modell ausserhalb der
CLI-Auswahl.

`ArraySmart2StorageTest` prüft stabile Objekt-, Basket- und Datensatz-IDs
beim Kürzen und Leeren von Listen, die ID-Vergabe bei Replace und den
Erhalt des unabhängigen Datensatzes bei Delete.

`SqlConstraintTest` prüft für beide XTF-Versionen globale und zusammengesetzte
Schlüssel, SQL `NULL`, Ganzzahl- und Festkommabereiche sowie die gerundete
Dezimalgrenze oberhalb von `2^53`. Die Fälle prüfen Rollback und die
Constraint-Wirkung für neu hinzugefügte Modelle direkt in der Datenbank.
Die strikten Fälle prüfen Vorwärtsreferenzen und stabile IDs bei flachen
Rollen und Rollen in Klassenhierarchien. Fehlende Pflichtfelder prüfen den
Rollback über verschachtelte Strukturzeilen, Klassenfragmente und
Geometriehilfstabellen. Räumliche Treffer belegen die Aktualisierung der Indizes.

`ForeignKeyProfileTest` prüft für beide XTF-Versionen die genauen
Fremdschlüsselziele, Vorwärtsreferenzen, Strukturzeilen und den Rollback
beim Import und beim Commit. Ein zweiter Fall vergleicht Indexspalten im
Profil ohne Fremdschlüssel. Beide Fälle prüfen den Zuwachs bei `--add-models`.

`DatasetAuditProfileTest` prüft die Datasetzuordnung und die Auditspalten
nach Import, Update und Replace. Es kontrolliert die Objekt-IDs, die
Zeitstempel und den Benutzer auf Fach-, Struktur- und Geometriehilfszeilen.
Ein zweiter Datensatz prüft die Isolation der Änderungen.

`HierarchyProfileTest` prüft Hierarchieschlüssel, Geometriehilfszeilen und
Beziehungstabellen in `newClass` und Smart2. Ein eingehender Verweis aus einem
zweiten Datensatz blockiert Replace und Delete. Nach dem Entfernen des
Verweises prüft der Test den erfolgreichen Replace und Delete sowie den
Erhalt des unabhängigen Datensatzes. Die `T_Type`-Fälle prüfen die Werte
auf Fach-, Struktur- und Linkzeilen und die SQL-Checks nach `--add-models`.

`GeometryStorageTest` prüft explizite Radien, offene Flächenringe und leere
XTF-2.4-Geometriesammlungen nach bereits geschriebenen Objekten. Beim
Rollback prüft er Zeilen und ID-Vergabe. Beschädigte gespeicherte Geometrien
führen zur Attributdiagnose und lassen die bestehende Exportdatei intakt.

`BaseMappingStorageTest` prüft den Abbruch der Schemaerzeugung, den Erhalt
belegter Ziele und den Rollback nach bereits geschriebenen Objekten.
SQL-Gegenproben prüfen beschädigte Strukturzeilen, Sequenzen, Rollenziele,
Vererbungsfragmente und Zuordnungen. Der GeoPackage-Fall für XTF 2.4 prüft
die Projektion mit 32 polymorphen Referenzen.

`ExternalReferenceStorageTest` prüft externe Ziele über Basket-, Topic-
und Datensatzgrenzen. Es vergleicht TIDs und BID-Zuordnungen in SQL und XTF
und prüft fehlende, mehrdeutige und typfremde Ziele. Abgewiesene Imports
erhalten die gespeicherten Zeilen. Fehler beim Export erhalten die Datei.
`ExternalTextReferenceStorageTest` prüft den unveränderten `REF`-Text
ohne Zielbestand, Updates, Rollback für fehlende lokale Linkziele und
den Erhalt der bisherigen XTF-Datei bei mehreren eingebetteten Links.
`AlternativeRoleStorageTest` prüft die Zielspalte einer `OR`-Rolle,
einen ungeeigneten Typ und den Export bei gleichzeitig belegten Spalten.

`OrderedRoleStorageTest` prüft die Positionsspalten eigenständiger,
eingebetteter und attributierter Rollen direkt per JDBC. Es prüft den
64-Bit-Grenzwert, fehlende Positionen, den Rollback bei Überlauf und den
Erhalt der Ausgabedatei bei verwaisten oder ungültigen Positionswerten.

`ObjectIdStorageTest` prüft UUID-Identitäten in XTF 2.3 und 2.4, den
Rollback bei ungültigen UUIDs und mehrdeutigen Zielen sowie den Erhalt der
Exportdatei bei fehlenden OID-Metadaten. Für XTF 2.4 prüft er zusätzlich
die Gross- und Kleinschreibung von Text-OIDs und die Diagnose bei Leerwerten.

`DatasetMutationTest` prüft `update`, `replace` und `delete` mit stabilen
und gespeicherten Identitäten. Das Update ohne `--create-tid-col` prüft
UUIDOID und STANDARDOID sowie die Abweisung eines Topics mit einer Klasse
ohne Objekt-OID. Die Fälle vergleichen Fach-, Struktur- und
Linkzeilen, unabhängige Datensätze und referenzierte Ziele. Sie prüfen auch
Schreibkonkurrenz und Rollback bei fehlerhaftem XTF oder blockierenden Referenzen.

`SpatialIndexStorageTest` prüft die Treffer und den Indexinhalt nach
Punkt- und Sammlungsupdates, Replace, Delete und Rollback. Die Fälle prüfen
die Hülle von Kreisbögen und die Diagnose eines fehlenden GeoPackage-Triggers.

`ScalarStorageTest` prüft 64-Bit-Grenzen, Dezimalpräzision, Kalender- und
Zeitwerte, FORMAT, UUID-Fachattribute sowie BLOB- und XML-Werte. Der
Zeitzonentest startet getrennte JVM-Prozesse. SQL-Gegenproben prüfen
den Rollback und den Erhalt der Exportdatei, insbesondere bei Überlauf,
ungültigem Datum und übergrossen Binär- oder XML-Werten.

`EnumerationStorageTest` prüft Code 0, SQL `NULL`, geänderte Anzeigetexte,
umbenannte Lookup-Tabellen und unbekannte oder mehrdeutige Lookup-IDs.
Er prüft den Rollback bei fehlerhaften Spalten- und Arraywerten sowie bei
einem SQL-Konflikt nach der Schemaerweiterung. Die Fälle prüfen den Erhalt
der Exportdatei bei beschädigten Lookup-Zeilen und Arraywerten.

`StructureMappingStorageTest` prüft gespeicherte Positionen, eine
umbenannte EXPAND-Spalte, Rollback bei zu langen Listen und den Erhalt
der Exportdatei bei beschädigten Typmarkierungen oder JSON-Werten.
Weitere Fälle prüfen die JSON-Typen und leere gespeicherte Sammlungen,
die stabilen IDs beim Update und das Freigeben belegter Spalten.

`StructurePointStorageTest` prüft Punktwerte in Einzel- und Listenstrukturen
und die räumlichen Treffer nach einem Update und nach einem Rollback.

`CrsDefaultsStorageTest` prüft die Schema-Standards EPSG:21781 und 4326,
fehlende oder widersprüchliche CRS-Metadaten und statische generische
Domain-Zuweisungen. Der GeoPackage-Fall prüft konkurrierende lokale SRS-IDs.
Der Fall für Änderungen am PostGIS-Katalog `spatial_ref_sys` verwendet pro
Sprachversion einen eigenen Container.

`MultiSrsStorageTest` prüft räumliche Treffer und gespeicherte SRIDs nach
Update, Replace und Delete sowie den Rollback nach einem Fehler beim zweiten
Objekt. Er prüft auch widersprüchliche Zuordnungen und die Auflösung einer
umbenannten EPSG-Spalte.

`BasketCrsStorageTest` prüft stabile IDs beim Update, Rollback, den
Wechsel der CRS-Wahl beim Replace und Indexbereinigung beim Delete. Die
Fälle prüfen auch inkompatible Alternativmodelle, fehlendes `--multi-srs`,
mehrdeutige EPSG-Zuordnungen und den Erhalt der Exportdatei bei
beschädigten Topics oder Domänenzuweisungen.

`SmartMutationStorageTest` prüft stabile Objekt- und Basket-IDs in Smart1
und Smart2, den Rollback bei Klassenwechseln und eingehenden Referenzen sowie
Replace und Delete nach Auflösung der Referenz. Er prüft auch das Entfernen
optionaler Geometrien und die Bereinigung der GeoPackage-Hilfszeilen.

`SmartClassStorageTest` prüft Leertext und SQL `NULL`, einen beschädigten
Smart1-Diskriminator und eine umbenannte Smart2-Spalte. Die Referenzfälle
prüfen den Rollback bei falschem Zieltyp und den Erhalt der Exportdatei
bei mehreren belegten Zielspalten.

`SmartPayloadStorageTest` prüft Eigentümer und Reihenfolge in
Strukturtabellen sowie SRID und Zuordnung der Geometriehilfszeilen.
Mehrfach belegte Eigentümer und fehlende GeoPackage-Hilfszeilen werden
per JDBC hergestellt und müssen die bestehende Exportdatei erhalten.

`ChbaseTextStorageTest` prüft die gespeicherten Sprachspalten, einen
umbenannten Spaltenstamm, Leertext und SQL-`NULL` sowie Rollback bei
Sprachfehlern und atomaren Export bei einer fehlenden Spalte.

`ChbaseGeometryStorageTest` prüft SQL-`NULL`, leere Strukturtabellen,
Geometrieteile und atomaren Export bei beschädigten Geometrien. Die
Smart2-Fälle prüfen stabile IDs, Hilfszeilen und räumliche Treffer nach
Update und Delete.

`CatalogueReferenceStorageTest` prüft fehlende und mehrdeutige Katalogziele
mit Rollback, widersprüchliche Fremdschlüsselmetadaten, doppelt belegte
Smart2-Zielspalten sowie den Schutz referenzierter Katalogdatensätze.

`ModelSourcesStorageTest` prüft fehlende lokale Modellimporte, fehlende und
mehrdeutige gespeicherte Modellquellen sowie den Erhalt der Exportdatei.
Eine manipulierte Topiczuordnung prüft, dass ein Datensatz mit einem
Basket ausserhalb der Auswahl erhalten bleibt.

`MultiModelStorageTest` prüft Modellauswahl, getrennte Sprachversionen im
selben Schema, Rollback bei einem späteren fremden Basket oder einem
mehrdeutigen Ziel sowie Datensatzänderungen mit eingehenden Referenzen.

`ModelExtensionStorageTest` prüft widersprüchliche Modellquellen und
Einstellungen sowie Erweiterungen, die bestehende Tabellen ändern würden.
Die Gegenproben prüfen die konkrete Zuordnung in der Diagnose und erhalten
Katalog, Metadaten, Fachzeilen und IDs. Beim Abbruch während einer
additiven DDL und der anschliessenden Wiederholung prüft der Test die
Transaktion. Ein per SQL aufgebautes Smart2-Layout mit unsuffigierter
Zielspalte prüft den Zielwechsel beim Update und den anschliessenden Export.

## ili2db-Interoperabilität

Die separate Gradle-Suite verwendet die offiziellen Distributionen von ili2gpkg
und ili2pg in Version 5.5.2. Der Task `:interlis-db:downloadIli2dbReferences`
lädt beide ZIPs von `downloads.interlis.ch` nach
`interlis-db/build/ili2db-reference/`. `dbInteropTest` ruft den Download-Task
automatisch auf. Mit vorhandenen ZIPs und Gradle-Abhängigkeiten im lokalen
Cache kann Gradle mit `--offline` laufen. Docker lädt fehlende Container-Images
bei Bedarf separat.

Die PostgreSQL-Tests benötigen Docker und verwenden das Image
`sogis/postgis:16-3.4`.
Jeder Fall entpackt sein Referenzwerkzeug in ein eigenes temporäres
Arbeitsverzeichnis. JUnit löscht dieses Verzeichnis samt GeoPackage-Dateien,
Prozesslogs und temporären Dateien des Referenzwerkzeugs auch bei Testfehlern.

`CoreTransferInteropTest` verwendet einen PostGIS-Container für die Klasse
und getrennte Schemata pro Fall. Die Option
`--tests '*CoreTransferInteropTest.*External*'` prüft externe Referenzen in
beiden Werkzeugrichtungen und Schematypen, auch bei getrennt gelieferten
Topics. Ein Folgeimport mit ili2db prüft die ID-Vergabe im eigenen Schema.
Mit `--tests '*CoreTransferInteropTest.*Ordered*'` prüft dieselbe Klasse
Katalog und Rollen in beiden Richtungen. Die Referenzfälle halten fest,
welche Positionswerte ili2db 5.5.2 beim Import und Export auslässt.
`--tests '*CoreTransferInteropTest.*Oid*'` prüft UUID-Identitäten und externe
Ziele in beiden Richtungen und Schematypen. Ein Folgeimport mit ili2db prüft
die ID-Vergabe im eigenen Schema.

`--tests '*AlternativeRoleInteropTest'` prüft je Backend und XTF-Version
beide Zielzweige einer lokalen `OR`-Rolle in newClass, Smart1 und Smart2.
Die Auswahl `*EmbeddedTargets*` prüft den eingebetteten 1:n-Fall in
newClass mit Katalogvergleich und tatsächlichen Fremdschlüsseln.
`*EmbeddedOwners*` prüft die Spalte und den Fremdschlüssel in beiden
alternativen Eigentümertabellen. `AlternativeRoleStorageTest` prüft den
Zielwechsel, stabile IDs und voneinander unabhängige Rollenupdates.
`--tests '*ExternalTextReferenceInteropTest'` bündelt externe Attribute,
eingebettete Rollen und beide Formen von Linktabellen im
`--sql-ext-ref-cols`-Profil. Die Mischläufe prüfen den `REF`-Wert ohne
geladenes Ziel und den Export ohne Ziel-`BID` in XTF 2.3.

`--tests '*IdentityColumnInteropTest'` vergleicht in NewClass die Profile
mit beiden und ohne beide Identitätsspalten. XTF 2.4 ergänzt Smart1 und Smart2
mit beiden Spalten. `IdentityColumnStorageTest` prüft die vollständige
Schaltermatrix, direkte SQL-Inserts, gespeicherte und erzeugte TIDs, Referenzen,
konkrete OIDs und den Rollback nach einer Schemaerweiterung.

`--tests '*IlisMeta16SchemaInteropTest'` prüft die drei Vererbungsprofile
der Referenzwerkzeuge mit `--createBasketCol`, die gespeicherten Strategien
und die Basket- und OID-Spalten der Assoziation.
`IlisMeta16SchemaStorageTest` prüft die erforderliche Option
`--create-basket-col` für `BASKET OID AS` und die Diagnose für die
Strukturkomposition `UnaryExpr.SubExpression`. Beide Fehler lassen das
Datenbankziel unverändert.

`--tests '*TextOidInteropTest'` prüft Modell- und Topicdomänen,
geerbte Objekt-OIDs, Assoziations-OIDs und ein skalares `OID TEXT`-Attribut.
`--tests '*NumericIdentityInteropTest.*ObjectOid*'` prüft numerische
Objekt-OIDs mit UUID-Baskets und modellierten Objektkennungen ohne
`--create-tid-col`. Die Mischläufe prüfen den Katalog, den XTF-Wert,
das Update und das datensatzübergreifende Referenzziel.
`--tests '*NumericIdentityInteropTest.*BasketOid*'` prüft denselben
Werkzeugwechsel mit numerischen Basket-OIDs und UUID-Objekt-OIDs ohne
Import- oder Exportoptionen zur Übernahme von Transferkennungen.
Der Katalogvergleich und der native XTF-Transfer über Referenzschemata
decken alle drei Vererbungsprofile ab. `ObjectIdStorageTest` prüft diese
Profile in eigenen Schemata.

`--tests '*CustomRowIdInteropTest'` prüft in beiden Werkzeugrichtungen
Referenzschemata mit `--t_id_Name Row_Custom`. Der Austausch enthält eine
Struktur, eine Rolle und indizierte Punkte in Haupt- und Hilfstabelle.
`CustomRowIdStorageTest` prüft Update, Replace und Delete, die GeoPackage-
ID-Vergabe oberhalb eines manuell gesetzten Werts und die Ablehnung
inkonsistenter Primärschlüssel.

Die SQL-Profilfälle liegen in `SchemaProfileInteropTest`. Für diese Auswahl
verwende `./gradlew :interlis-db:dbInteropTest --tests '*SchemaProfileInteropTest'`.

Die `*ProfileNullability*`-Fälle tauschen Pflichtskalare und optionale
Werte in Referenzschemata ohne `--sqlEnableNull` aus. Sie prüfen den
Rollback bei einer verletzten SQL-Pflichtspalte.
Die `*ProfileRequiredRoles*`- und `*ProfileRequiredGeometries*`-Fälle
vergleichen strikte Referenz- und eigene Schemata und prüfen den
Werkzeugwechsel in beiden Richtungen. `*ProfileStrictLinks*` prüft beide
Rollenspalten und Vorwärtsreferenzen eigenständiger n:m-Links im strikten
Schema. `SqlConstraintTest` prüft die stabilen IDs und den Rollback beim
fehlenden oder falschen Ziel. `*ProfileStrictSmart1*` prüft ein
gemeinsames Pflichtfeld und die nullable Spalten zweier Geschwistertypen in
einer Smart1-Tabelle. `*ProfileStrictSmart2*` prüft kopierte
Pflichtfelder und eine zwingend belegte Rollenspalte auf einer konkreten
Tabelle. `SqlConstraintTest` prüft stabile IDs beim Update und den Rollback
bei einem fehlenden Pflichtfeld. `*ProfileMandatoryChecks*` vergleicht die
aktiven PostgreSQL-Prüfungen und nullable GeoPackage-Spalten. Der Mischlauf
bindet Vorwärtsreferenzen mit Strukturattribut, eingebetteter Rolle und in
XTF 2.4 direktem Klassenattribut. `SqlConstraintTest` prüft die Rollbacks
bei fehlenden Zielen und bei der Verletzung eines SQL-Checks.
`*ProfileNestedStructures*` prüft Einzelwerte, Listen und Bags in
Strukturen. `*ProfileRequiredHierarchy*` prüft Pflichtfelder und zwei
Rollen auf dasselbe Ziel in einer `newClass`-Hierarchie.
`*ProfileRequiredGeometryFragments*` prüft Pflichtgeometrien und
optionale Werte in aufgeteilten Geometrietabellen.
Die `*ProfileForeignKeys*`-Fälle vergleichen die beiden Einzelprofile für
Fremdschlüssel und referenzierende Indizes, auch nach `--add-models`.
Der gemeinsame Austausch für beide Optionen läuft in
`*ProfileMetaInformation*`.
Die `*ProfileHierarchyKeys*`-Fälle vergleichen Fremdschlüssel in
`newClass`- und Smart2-Schemata sowie den Austausch von Geometriehilfszeilen,
Beziehungstabellen und polymorphen Verweisen.
Die `*ProfileDiscriminator*`-Fälle prüfen `T_Type` in flachen Klassen,
Hierarchien, Struktur- und Linktabellen nach dem Austausch in beiden Richtungen.
Die `*ProfileStandardColumns*`-Fälle vergleichen das Einzelprofil für
`T_datasetname` samt Indexzuwachs bei `--add-models`. Der gemischte Austausch
verwendet das kombinierte Profil mit Auditspalten. Die Fälle vergleichen die
Datasetzuordnung, den Benutzer und die gespeicherten Zeitstempel nach beiden
Imports.
Die `*ProfileUniqueBasket*`-Fälle reproduzieren den Fehler bei der
ili2db-Schemaerzeugung für `UNIQUE (BASKET)` in INTERLIS 2.4 und tauschen
Daten in einem mit `interlis-db` erzeugten Schema aus.
Die `*ProfileMetaInformation*`-Fälle vergleichen `T_ILI2DB_META_ATTRS`
einschliesslich einer inversen Rolle. Die Mischläufe kombinieren Smart2,
Fremdschlüssel, Indizes, zusammengesetzte Schlüssel, SQL-Bereichsprüfungen
und Aufzählungsmetadaten. Sie prüfen die gespeicherten Constraints und den
Austausch ganzzahliger und dezimaler Grenzwerte in beiden Richtungen.

`--tests '*ScalarInteropTest'` bündelt die skalaren Werte in je einem
Schema pro Backend und XTF-Version. Die Fälle vergleichen Katalog und
Fachwerte in beiden Werkzeugrichtungen und Schematypen. Sie prüfen auch
die Dezimalrundung beim ili2db-Import, den gespeicherten SQL-Namen einer
umbenannten FORMAT-Spalte und den Unicode-Exportfehler von ili2gpkg
in XTF 2.3. Modellnamenswerte und Alignment-Codes werden im Standard- und
im Textprofil geprüft. Die Alignment-Fälle vergleichen auch die benannte
Domäne und die Anzeigetextspalten.
Bei numerischen OID-Attributen prüfen die Fälle die unveränderte
Schreibweise, SQL `NULL` und `typeKind=OID`.
Die Dezimalarrays verwenden ein eigenes `--coalesceArray`-Profil
und prüfen die Reihenfolge, Duplikate, SQL `NULL` und die numerische Kodierung.

`--tests '*EnumerationInteropTest'` prüft den Katalog und beide
Werkzeugrichtungen für SQL-ITF-Codes, Anzeigetexte, gemeinsame und
getrennte Lookup-Tabellen sowie gespeicherte Lookup-IDs. Der ID-Fall
kombiniert geerbte und erweiterte Definitionen, abweichende gespeicherte
IDs und einen ili2db-Folgeimport. Inline-Aufzählungen mit `EXTENDED`
prüfen geteilte und kopierte Spalten, die Lookup-Zeilen je Deklaration
sowie Lookup-IDs mit Fremdschlüssel und Anzeigetext. Weitere Fälle prüfen
den Werkzeugwechsel nach `--add-models` sowie Arrays mit ITF-Codes oder Lookup-IDs.
Die Alignment-Fälle prüfen gespeicherte Codes und Lookup-IDs, Anzeigetexte
und die Reihenfolge der vordefinierten Lookup-Werte.

`--tests '*ArrayScalarInteropTest'` bündelt skalare LIST- und BAG-Arrays,
optionale Einzelstrukturen sowie EXPAND und JSON in einem Modell pro
Sprachversion. Katalog und Datenaustausch werden in beiden Schematypen
geprüft. Ein ili2db-Import nach `--add-models` prüft die Array-Einstellung
und die gemeinsame ID-Vergabe. Eine Gegenprobe erhält den bekannten
ili2db-Exportfehler bei umhüllten `NULL`-Elementen und prüft den erfolgreichen
Export mit `interlis-db`.

`--tests '*ArraySmart2InteropTest'` vergleicht die geerbten Array-Spalten
in den konkreten Tabellen und prüft beide Werkzeugrichtungen in beiden
Schematypen. Der ili2db-Export nach einem nativen Update prüft eine gekürzte
und eine geleerte Liste.

`--tests '*ArrayReferenceInteropTest'` vergleicht die Kataloge und prüft
Vorwärtsreferenzen sowie Reihenfolge und doppelte Ziele im XTF und in den
SQL-Arrays beider Schematypen. Ein ili2db-Folgeimport prüft die gemeinsame
ID-Vergabe.

`--tests '*StructureMappingInteropTest'` prüft EXPAND für Einzelwerte,
LIST, BAG, Verschachtelung und die Rückkehr zur Strukturtabelle bei
Obergrenze sechs in einem Modell. Der Smart2-Lauf prüft die beiden
Eigentümertabellen und den gemischten Export nach Update. Das JSON-Schema
kombiniert JSON und expandierte Spalten in derselben Tabelle und
unterscheidet SQL `NULL` vom leeren Objekt. Die Schemafälle prüfen
`--add-models` und einen weiteren ili2db-Import zur ID-Vergabe.

`--tests '*StructurePointInteropTest'` vergleicht die Kataloge für einen Punkt
in der regulären Strukturtabelle. Die Mischläufe prüfen SRID, Koordinaten,
Rückverweise und Listenpositionen in beiden Werkzeugrichtungen.

<a id="ili2db-json-export-reproduzieren"></a>

Für die Reproduktion des JSON-Exportfehlers genügen die vier Fälle unten.
Das jeweilige Referenzwerkzeug erstellt das Schema mit `--coalesceJson`,
importiert den Datensatz mit einer leeren inneren Struktur und versucht,
ihn mit `--exportTid` zu exportieren. Der Test bestätigt den Fehlertext
und prüft den erfolgreichen Export aus derselben Datenbank mit
`interlis-db`. [Fehlerbeschreibung und Beispielwert](../usage/database.md#ili2db-json-exportfehler)

```sh
./gradlew :interlis-db:dbInteropTest --tests '*StructureMappingInteropTest.*JsonCollections*'
```

`--tests '*CatalogueReferenceInteropTest'` prüft die CHBase-V1-Katalog-
referenzen mit `--coalesceCatalogueRef` und einem separaten Katalogdatensatz.
Die Gruppen `*NewClass*` und `*Smart2*` vergleichen den Katalog, die
SQL-Zielspalten und beide Werkzeugrichtungen in beiden Schematypen.
Ein ili2db-Folgeimport prüft die gemeinsame ID-Vergabe. Die Gruppe
`*Direct24*` prüft ein direktes `REFERENCE TO` im Referenzschema.

`--tests '*ChbaseTextInteropTest'` prüft `--expandMultilingual` und
`--expandLocalised` im selben Schema und in einer XTF-Lieferung mit zwei
Baskets. Katalogvergleich, beide Werkzeugrichtungen in beiden Schematypen
und ein ili2db-Folgeimport decken die SQL-Abbildung und die ID-Vergabe ab.
`--tests '*ChbaseGeometryInteropTest'` bündelt Multipunkte, Multilinien und
Multiflächen in einer Lieferung mit drei Baskets. Katalogvergleich und beide
Werkzeugrichtungen in beiden Schematypen prüfen doppelte Punkte, gerichtete
Linien, Kreisbögen, Innenringe und die SRIDs. Ein ili2db-Folgeimport prüft
die gemeinsame ID-Vergabe. Die separat auswählbaren Smart2-Fälle verbinden
geerbte Sprachwerte mit einer Multipunkt-Hilfstabelle und prüfen den
ili2db-Export nach einem nativen Update. Die Auswahl `*Xyz*` in
`ChbaseGeometryInteropTest` prüft alle drei kompakten XYZ-Geometrien
in Hilfstabellen sowie Z-Werte an Punkten und Bogenendpunkten.
`ChbaseGeometryStorageTest` prüft die Z-Grenzen, das Leeren einer
Geometrie, stabile IDs und den Rollback bei einer späten fehlenden Ordinate.

`--tests '*SmartClassInteropTest'` prüft skalare Werte, polymorphe
Referenzen und den Katalog beider Schemaerzeuger. Die Smart1-Skalarfälle
verwenden die Standardwahl von ili2db. Der Smart2-Folgeimport prüft die
gemeinsame ID-Vergabe. Beim Smart2-Katalogvergleich wird die Reihenfolge
innerhalb eines Satzes alternativer Fremdschlüsselspalten normalisiert.

`--tests '*SmartPayloadInteropTest'` verbindet Struktur- und Geometriemodell
in einem Schema pro Smart-Profil und einer Lieferung mit zwei Baskets.
Die Fälle vergleichen die Kataloge und prüfen Struktureigentümer und
Geometriezuordnungen in beiden Werkzeugrichtungen. Die XTF-Prüfung ordnet
die Baskets über Modell und BID zu.

`--tests '*SmartMutationInteropTest'` prüft in beiden Layouts einen
ili2db-Import, ein natives Update und den anschliessenden ili2db-Export. SQL
und XTF belegen stabile IDs, den geerbten Wert und die Änderung.

`--tests '*ModelSourcesInteropTest'` bündelt gemeinsam und transitiv
gespeicherte Modellquellen sowie importierte Strukturen in einer Lieferung
mit drei Baskets. Der Katalogvergleich prüft die Modellzeilen und die
Abbildung. Nach dem Entfernen der lokalen Modelldateien laufen beide
Werkzeugrichtungen in beiden Schematypen.

`--tests '*MultiModelInteropTest.*Mixed*'` prüft gleichnamige Klassen,
gemischte Datensätze und die SQL-Zuordnungen in beiden Schematypen. Der
ili2db-Export folgt auf ein selektives natives Update. Ein Folgeimport
überprüft die gemeinsame ID-Vergabe. Die Auswahl `*ImportedTopic*` prüft
`DEPENDS ON` mit einem separaten Zieldatensatz und zwei Referenzformen.

`--tests '*ModelExtensionInteropTest'` vergleicht additive Erweiterungen
eines befüllten Schemas mit dem gestuften ili2db-Schemaimport. Die
Mischläufe prüfen unabhängige Modelle, gemeinsame Domänen, externe
Rollen in neuen Tabellen, Smart2-Untertypen mit eigener Tabelle und
einen Smart1-Untertyp mit passendem `T_Type`. Der strikte Fall prüft
`NOT NULL` in der hinzugefügten Tabelle. Beide Werkzeuge lesen die
bestehenden Daten und tauschen Daten im erweiterten Schema aus.
Ein ili2db-Folgeimport prüft die gemeinsame ID-Vergabe.

Der Transfer von Smart1-Fragmenten, gemeinsam genutzten Strukturlisten
und polymorphen Smart2-Referenzen verwendet vollständig aufgebaute
Schemata. `--tests '*SmartReferenceInteropTest'` prüft eingebettete Rollen,
Referenzattribute und anonyme n:m-Linkzeilen.
Im Smart1-Fragmentfall wird der Basket des Basismodells mit `interlis-db`
importiert, da ili2db 5.5.2 bei diesem Import `T_Type` leer lässt. Die
Fragmente werden in beiden Werkzeugrichtungen ausgetauscht.

`--tests '*ExportSelectionInteropTest.*Baskets*'` vergleicht die
Basket-Auswahl mit ili2db `--baskets` und importiert die XTF-Dateien mit
dem jeweils anderen Werkzeug.
`--tests '*ExportSelectionInteropTest.*Topics*'` vergleicht die exakte
Topic-Auswahl über mehrere Datensätze und den leeren Transfer mit
ili2db `--topics`.
`--tests '*ExportSelectionInteropTest.*Models*'` prüft automatisch benannte
Importe und den Modellexport über Datensatzgrenzen hinweg aus derselben
Referenzdatenbank. Die Auswahl `*MissingBid*` vergleicht den BID-Ersatzwert
nach einem Referenzimport ohne `--importBid` und die Auflösung externer
Verweise auf enthaltene und ausgelassene Zielbaskets.
`--tests '*ExportSelectionInteropTest.*Datasets*'` prüft den gemeinsamen
Export mehrerer Datensätze samt datensatzübergreifender Referenz und
gegenseitigem Wiederimport.

`--tests '*TranslationInteropTest'` bündelt skalare und verschachtelte
Baskets im Katalogvergleich und Werkzeugwechsel. Ein weiterer Fall
ergänzt ein befülltes ili2db-Schema und prüft die erhaltenen IDs, den
Austausch neuer Lieferungen und die explizite Ausgabesprache. XTF 2.3
prüft den Sprachwechsel in beide Richtungen. XTF 2.4 exportiert mit den
Namen des Originals. `TranslationStorageTest` prüft beschädigte oder
fehlende Quellen und die abgewiesene Ausgabesprache mit bytegenau
erhaltener Zieldatei. Nach einem nativen Update liest ili2db die
geänderte Übersetzung. Stabile IDs, falsche Modellauswahl, Replace und
Delete prüft `TranslationStorageTest` direkt.

`--tests '*TopicInheritanceInteropTest'` bündelt geerbte Klassen,
Klassenfragmente, Attributverfeinerung und Referenzen in fünf Baskets.
Der Mischlauf vergleicht den Katalog und prüft den Werkzeugwechsel in
beiden Schematypen. `TopicInheritanceStorageTest` prüft unzulässige
Geschwistertypen, gespeicherte abstrakte Baskets und den Rollback bei
einer Referenz in den falschen Basket. Ein ili2db-Export nach nativem
Update prüft die aktualisierten Werte der Fragmente. Der eigene DB-Test
prüft stabile IDs, eingehende Referenzen und den Erhalt unabhängiger
Datensätze bei Mutationen im NewClass-Layout. Der Basismodell-Export
vergleicht `--export-model` mit ili2db `--exportModels`. Beide Werkzeuge
importieren die Ausgabe des jeweils anderen in ein reines Basisschema.
Der Mischlauf prüft Koordinaten und SRID eines geerbten Punkts sowie
vorhandene und ausgelassene Struktur-Einzelwerte und die Listenreihenfolge.
Eine geerbte 1:n-Rolle und ein anonymer n:m-Link werden über ihre Zielverweise
im XTF und die Fremdschlüssel im Ziel kontrolliert. Der eigene Test prüft
die unveränderte Zieldatei bei doppeltem `T_Seq`, einem unerreichbaren Basistopic und einem
bei der Projektion entfallenden Rollenziel.

`--tests '*TopicEnumInteropTest'` vergleicht die Abbildung einer
Enum-Verfeinerung im abgeleiteten Topic in beiden Werkzeugrichtungen.
`TopicEnumStorageTest` prüft die unveränderte SQL-Identität beim Update
des Kindmodellcodes in der Basisspalte.
Der anschliessende Basismodell-Export reduziert unterteilte Enum-Blätter.
Die Mischläufe importieren die Ausgabe des jeweils anderen Werkzeugs ins
Basisschema. Der eigene Test prüft die unveränderte Zieldatei bei einem Code
ohne Basiswert.

`--tests '*DatasetMutationInteropTest'` prüft in beiden Schematypen den
Werkzeugwechsel über `update`, `replace` und `delete`. SQL und XTF belegen
die stabilen IDs beim Update und den Ersatz der Daten beim Replace. Ein
weiterer ili2db-Import prüft die gemeinsame ID-Vergabe. Eine Gegenprobe
prüft die Behandlung der `ORDERED`-Position beim gemischten Update. In
einem Schema ohne `--createTidCol` vergleichen beide Werkzeugrichtungen
ausserdem die stabilen Basket- und Objekt-OIDs und die erhaltenen SQL-IDs.
Der Mischlauf vergleicht auch die Tabellen zu `--createImportTabs` und
prüft die Statistik beider Werkzeuge einschliesslich leerer Baskets.
`DatasetMutationTest` prüft ihren Rollback bei einem späten Fehler.
`--tests '*SpatialIndexInteropTest'` vergleicht die RTree- und GiST-Definitionen
beider Schemaerzeuger. Die Fälle tauschen gerade und gebogene Geometrien
gemeinsam aus und prüfen die räumlichen Treffer nach einem Update durch
ili2db. Die XTF-2.4-Fälle prüfen zusätzlich die Reihenfolge der Geometrieteile.

`--tests '*XyzGeometryInteropTest'` vergleicht die Kataloge und die
Z-Werte von Punkten, Bögen, Linien und Flächen in beiden Werkzeugrichtungen.
Die XTF-2.4-Fälle prüfen auch die Reihenfolge der XYZ-Sammlungen.
`XyzGeometryStorageTest` prüft XY-Indexhüllen, den Z-Platzhalter für
Bogenstützpunkte in PostGIS und den Rollback bei fehlenden Höhen oder
gemischten Dimensionen.

`--tests '*GeometryLayoutInteropTest'` vergleicht die Kataloge für gemeinsame
Geometriespalten und für Hilfstabellen. Klassen mit mehreren Geometrien
und die Klassenhierarchie teilen sich je Layout dasselbe Schemapaar.
Der Mischlauf prüft auch Updates. Bei Hilfstabellen bleiben der
Referenzimport mit nativem Export und der dokumentierte
[Fehler des Referenzexports](../usage/database.md#referenzprofil) abgedeckt.
`GeometryLayoutStorageTest` prüft räumliche Indizes, stabile IDs,
SQL-`NULL`, beschädigte Basketzuordnungen und Rollback per JDBC.

`--tests '*CrsDefaultsInteropTest'` prüft Schema- und Modellstandards und
bündelt Punkte und Geometrien mit `VERTEX`-Domänen. Eigene Fälle prüfen
den statischen generischen Kontext in XTF 2.4 und die lokale GeoPackage-ID
gegen den EPSG-Code im Geometrie-Header.

`--tests '*MultiSrsInteropTest'` prüft gemischte SRIDs und feste EPSG-Suffixe
mit Geometriespalten in der Fachtabelle und in Hilfstabellen. Die Fälle
vergleichen die Kataloge und prüfen beide Werkzeugrichtungen in beiden
Schematypen. Im eigenen Schema liest ili2db auch den Zustand nach einem Update.

`--tests '*BasketCrsInteropTest'` prüft alternative Topics in XTF 2.3 und
aufgeschobene Domänen in XTF 2.4. Die Punktfälle vergleichen die Kataloge
und prüfen beide Werkzeugrichtungen, den Wechsel nach Replace und in XTF 2.4
einen leeren Basket und den Folgeimport. Die XTF-2.4-Fälle mit `DeferredMulti`
und `DeferredCurves` prüfen 3D-Sammlungen beziehungsweise Kurven und Flächen.

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

`--tests '*StrokeGeometryInteropTest'` bündelt gerade und segmentierte
XY-Polylinien, Punkte, `SURFACE` mit Innenring und benachbarte `AREA`-Objekte
in einer Lieferung pro Sprachversion. Der Mischlauf prüft den Katalog,
die Bogenstützpunkte und Koordinatentoleranzen, den gemeinsamen gebogenen
Rand sowie einen mehrteiligen Rand in XTF 2.3. Ein ili2db-Folgeimport prüft
die gemeinsame ID-Vergabe. Die Schemaerweiterung übernimmt `--strokeArcs`
für eine weitere Flächenspalte. `StrokeGeometryStorageTest` prüft
umbenannte Spalten, grosse Bögen, Vollkreise, kollineare Stützpunkte,
Sehnentoleranz und Rollback am Koordinatenlimit. Dazu kommen die
Ablehnung von XYZ und der Erhalt der XTF-Datei bei fehlerhaften
Spaltenmetadaten, Katalogtypen oder WKB-Werten.

`--tests '*StrokeMultiGeometryInteropTest'` prüft den Katalog und den
Werkzeugwechsel für CHBase-`MultiLine`, `MultiDirectedLine` und
`MultiSurface` mit zwei Baskets derselben Lieferung. XTF 2.4 ergänzt
einen Basket mit `MULTIPOLYLINE`, `MULTISURFACE` und `MULTIAREA`.
Die Prüfungen umfassen Reihenfolge, Richtung, Bogenstützpunkte,
Innenringe sowie EPSG:2056 und EPSG:21781.
`StrokeMultiGeometryStorageTest` prüft den Export leerer
`MULTIPOLYGON`-Werte neben befüllten Sammlungen.

`--tests '*StrokeSmart2InteropTest'` prüft geerbte, segmentierte
Geometrien in einer indizierten Hilfstabelle. Der ili2db-Export nach
einem nativen `update` prüft Punkt, Linie und Text.
`StrokeSmart2StorageTest` prüft stabile Objekt- und Basket-IDs,
räumliche Treffer und die Bereinigung beim Löschen. PostgreSQL
weist den GiST-Zugriff mit `EXPLAIN` nach, GeoPackage prüft den RTree.

Die Auswahl `--tests '*BaseMappingInteropTest.geoPackage*'` läuft ohne Docker.
Der Basislauf vergleicht vor dem ersten Datenimport den SQL-Katalog und die
Metadaten der erzeugten Schemata. Die GeoPackage-Erweiterungseinträge für
Polylinien und Flächen werden separat geprüft. Die Tests vergleichen Punkte,
gerade und gebogene Segmente, Aussen- und Innenringe, die gemeinsame Kante
benachbarter `AREA`-Objekte, SRS und `NULL` für XTF 2.3 und 2.4 in beiden
Werkzeugrichtungen. In 2.3 prüfen sie auch die Zusammenführung eines
geteilten Randes. In 2.4 prüfen sie die geordneten Teile von
`MULTICOORD`, `MULTIPOLYLINE`, `MULTISURFACE` und `MULTIAREA`. Nach einem
weiteren Referenzimport im eigenen Schema prüft JDBC die gemeinsame ID-Vergabe,
die Basket-Zuordnung und einen Marker mit seiner Verknüpfung. `GeometryStorageTest`
prüft Rollback und Exportfehler für Geometrien sowie den Export leerer
SQL-Sammlungen.

Der Referenz-Basislauf enthält zusätzliche Anwendungsspalten in einer
Fach-, einer Struktur- und einer Linktabelle. Der Test
`BaseMappingStorageTest.applicationColumnsKeepValuesAndDatabaseDefaults`
prüft Anwendungswerte auf erhaltenen Zeilen, SQL-Defaults auf neuen
Zeilen und den Rollback bei einer zusätzlichen Pflichtspalte.

Der Basislauf prüft die XTF-Ergebnisse. `BaseMappingStorageTest` prüft
die physischen Zeilen, ihre Binärkodierung und Zuordnungen per JDBC.
Eine beschädigte Zuordnung für `Place.Select` erhält die Exportdatei.
Ein direkter PostgreSQL-Insert prüft den Default der gemeinsamen Sequenz.
Beide Tests prüfen optionale
Einzelstrukturen, `LIST OF` und `BAG OF` mit demselben
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
die Sequenzspalten und die Rollenziele in beiden Werkzeugrichtungen.
`OrderedRoleStorageTest` setzt Positionen direkt im SQL-Schema und prüft
exakte Werte, SQL `NULL`, Rollback und Erhalt der Ausgabedatei.
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
