# Datenaustausch mit ili2db-Schemata

Das JVM-Kommando `interlis-db` erstellt ili2db-kompatible Schemata in
GeoPackage oder PostgreSQL/PostGIS. Es importiert und exportiert XTF-FULL-Transfers
für ausgewählte Modelle oder benannte Datensätze. Das
Referenzprofil für die Schemaerzeugung verwendet `ili2gpkg` und `ili2pg`
**5.5.2**. Für den bisherigen Umfang mit Punkten unterstützt der Transfer
auch bestehende 5.5.1-Schemata. Er kompiliert das gespeicherte Modell und
verwendet dessen gespeicherte SQL-Zuordnungen.
Ein ausgewähltes Modell kann Text-, Aufzählungs- und Koordinatendomänen
weiterer Modelle verwenden. Das Kommando lädt die benötigten gespeicherten
Dateien über die Importangaben in `T_ILI2DB_MODEL` und prüft die
tatsächlichen Importe beim Kompilieren. Fehlende oder mehrdeutige
Quellen werden mit Modellnamen gemeldet.
Gespeicherte `TRANSLATION OF`-Beziehungen werden mit dem angegebenen
Original und seiner Modellversion kompiliert. Die physische Abbildung
der Übersetzungsfamilie verwendet die gespeicherten SQL-Namen des Originals.
Bei XTF 2.3 führt die Datenbank die Sprache jedes Baskets in
`T_ILI2DB_BASKET.topic`. Die Enum-Spalten verwenden die Codes des Originals.
Bei XTF 2.4 verwendet der Transfer auch bei Auswahl eines übersetzten Modells
die Originalsprache.
Strukturwerte, Referenzen, Rollen und Punktgeometrien verwenden dieselbe
SQL-Abbildung in jeder Übersetzung. Referenzen und Rückverweise bleiben
dem tatsächlich gespeicherten Basket zugeordnet.
Für den Datenaustausch sind die Tabellen und XTF-Baskets des Fachmodells
massgeblich.
Verwendete Strukturen aus importierten Modellen erhalten ihre Tabellen samt
Rückverweisen. Optionale Einzelwerte, `LIST OF` und `BAG OF` verwenden
denselben Strukturtyp und behalten dessen qualifizierten XTF-Namen.
Ein `DEPENDS ON` übernimmt die Abbildung des importierten Zieltopics.
`EXTERNAL`-Attribute und Rollen können dessen Objekte auch in separaten
Datensätzen referenzieren.
Ein mit `EXTENDS` abgeleitetes Topic übernimmt die Klassen seines Basistopics.
Geerbte und lokal deklarierte Objekte belegen gemeinsam den konkreten
Basket. Die geerbten Objekte behalten den XTF-Namen und die SQL-Tabelle
ihrer Deklaration.
Das DB-Profil verwendet pro Basket ein primäres Topic. Zusätzliche
`TOPICS`-Angaben im XTF 2.3 werden beim Import abgewiesen.
Ein abstraktes Basistopic liefert Tabellen für konkrete abgeleitete Baskets.
Bei `CLASS (EXTENDED)` verwendet ein verengtes `TEXT`-Attribut die
SQL-Spalte der Basisdeklaration. Der Export verwendet im XTF 2.4 ihren
Namensraum.
In `newClass`-Klassenhierarchien innerhalb desselben Topics verwenden
verengte Ganzzahlbereiche mit gleichem Speichertyp (`INTEGER` oder `BIGINT`)
und verengte Festkommaattribute mit gleicher Skala ebenfalls die Spalte
und den `ATTRNAME`-Eintrag der Basisdeklaration. Auf PostGIS bestimmt die
Basisdeklaration auch die SQL-Präzision der Festkommaspalte.
`--create-num-checks` verwendet für diese Spalten den Basisbereich.
Ohne `--createNumChecks` kann ili2pg 5.5.2 für zehnstellige Ganzzahlgrenzen
eine `integer`-Spalte anlegen, auch wenn der Modellbereich darüber hinausgeht.
Der Austausch für solche Bereiche setzt auf PostGIS eine `bigint`-Spalte
voraus.
Inline-Aufzählungen mit `EXTENDED` verwenden im `newClass`-Standardprofil
ebenfalls die Spalte und den `ATTRNAME`-Eintrag der Basisdeklaration.
Der konkrete Typ liefert den als Text gespeicherten XTF-Code, auch für neu
hinzugekommene Werte wie `active.ready`.
In Smart1 gilt dieselbe Regel für gemeinsam gespeicherte Attribute.
Beim Kopieren aus einer abstrakten Klasse kann die konkrete Tabelle
eine Spalte für die erweiterte Deklaration erhalten. Ihr `ATTRNAME`-Eintrag
bezeichnet dann diese Deklaration.
In Smart2 verwendet jede konkrete Tabelle die Aufzählungsdeklaration,
die für ihre Klasse gilt. Geerbte Spalten behalten den Namen der
Deklaration in `ATTRNAME`.
Lokale Referenzen dürfen auf Objekte geerbter Klassen im selben Basket
zeigen. Der Basket kann eingebettete Rollen des Basistopics übernehmen.
Eine in einem abgeleiteten Topic deklarierte eingebettete 1:n-Rolle mit
lokal deklarierter n-Klasse darf auf eine geerbte Klasse zeigen.
[Referenzhandbuch](https://github.com/claeis/ili2db/blob/4607cc3a1171ed8a885ed83076c197a12559b41f/docs/ili2db.rst)

## Schemaerzeugung

```sh
./gradlew :interlis-db:installDist
interlis-db/build/install/interlis-db/bin/interlis-db schemaimport \
  --gpkg data.gpkg --model Example Example.ili
interlis-db/build/install/interlis-db/bin/interlis-db schemaimport \
  --pg-url jdbc:postgresql://localhost:5432/geodata --schema example --user editor \
  --model Example Example.ili
```

Das Kommando liest das ausgewählte Modell aus lokalen UTF-8-Dateien.
Zusätzliche Importquellen werden mit je einem `--model-file IMPORT.ili`
angegeben. Es speichert die benötigten Dateien samt ihren Modell- und
Importlisten im neuen Schema.
Mit mehreren `--model NAME` legt `schemaimport` eigenständige Modelle
gemeinsam an. Ihre Tabellen teilen die technischen Metadaten und die
ID-Vergabe. Namenskollisionen werden anhand des deklarierenden Modells
aufgelöst.
Vor dem ersten Schreibzugriff prüft es das Modell und bestimmt die
Tabellen, Spalten und Metadaten gemäss der Standard-Namensvergabe von 5.5.2.
PostgreSQL benötigt eine vorhandene PostGIS-Erweiterung und einen
kleingeschriebenen Schemanamen, den ili2pg unquotiert verwenden kann.

Ab `interlis-db 0.3.0-SNAPSHOT` entstehen neue Schemata standardmässig
ohne `T_basket` und ohne Spalte für gewöhnliche Transferkennungen.
`--create-basket-col` aktiviert die Zuordnung zu Baskets und Datensätzen.
`--create-tid-col` speichert zusätzlich die ursprünglichen Transfer-TIDs.
Die Optionen lassen sich unabhängig verwenden. Konkrete Objekt-OIDs
erhalten automatisch eine typgerechte Spalte `T_Ili_Tid`. `T_Id` bleibt
der interne SQL-Primärschlüssel. Ein abstraktes `ANYOID` legt die
Speicherung erst über die konkreten Unterklassen fest.

`TOPIC … EXTENDS …`, `BASKET OID AS` und aufgeschobene generische
Domänen erfordern `--create-basket-col`. Die Optionen
`--create-dataset-col` und `--create-unique` für `UNIQUE (BASKET)`
benötigen ebenfalls die Basket-Spalte. `--add-models` übernimmt
beide Einstellungen aus dem bestehenden Schema.

Skripte, die ein Schema für den bisherigen datensatzbezogenen Transfer
erstellen, ergänzen beim Schemaimport `--create-basket-col --create-tid-col`.
Bestehende Schemata behalten ihr gespeichertes Layout.

Das neue Schema entsteht in einer Transaktion. GeoPackage entsteht als temporäre Datei
neben dem Ziel und wird nach erfolgreichem Abschluss per Hardlink
veröffentlicht. Ein vorhandener Schemaname oder Zielpfad führt zu einem
Fehler. Für GeoPackage muss das Ziel-Dateisystem Hardlinks unterstützen.

Mit `schemaimport --stroke-arcs` legt `interlis-db` ein neues
`newClass`- oder Smart2-Schema mit der linearen XY-Speicherung von ili2db
`--strokeArcs` an. Die Option gilt für Linien und Flächen, einschliesslich
ihrer Sammlungen. Punkte und Punktsammlungen behalten ihre SQL-Typen.
`schemaimport --add-models` übernimmt die Einstellung des bestehenden
`newClass`-Schemas. Ausgelagerte Geometriespalten können mit einem RTree-
oder GiST-Index verwendet werden.

Mit `schemaimport --create-geom-index` erhält jede Geometriespalte
einen Raumindex. GeoPackage registriert `gpkg_rtree_index` mit der
virtuellen Tabelle und ihren Standardtriggern. PostGIS legt einen GiST-Index an.
Die entsprechende Option in ili2db 5.5.2 ist `--createGeomIdx`.

`schemaimport --sql-ext-ref-cols` entspricht ili2db `--sqlExtRefCols`.
Im `newClass`-Profil speichert ein externes Referenzattribut den XTF-`REF`
in einer Textspalte mit deklarierter Länge 255. Das funktioniert ohne
geladenen Zielbestand. `ATTRNAME.target` bezeichnet das Modellziel,
und lokale Referenzen behalten ihre numerische Abbildung.
Eine eingebettete externe 1:n-Rolle ohne eigene Attribute verwendet
dieselbe Textkodierung in der Eigentümertabelle. `update` kann die Rolle
ändern oder leeren und behält die Objekt-ID.
In eigenständigen Linktabellen mit einem externen und einem lokalen Ende
speichert die Zeile den externen `REF`-Text und die numerische ID des
lokalen Ziels. `--create-fk` legt den Fremdschlüssel für das lokale Ziel an.
Bei eingebetteten Assoziationen mit eigenen Attributen liegen der
`REF`-Text, die Eigentümer-ID und die Attribute in der Beziehungstabelle.
Der Export schreibt eine direkte Referenz für eine Zeile ohne Attributwerte
und eine eingebettete Referenz, wenn Attributwerte vorliegen.
Ein mitgeliefertes Ziel-`BID` aus XTF 2.3 wird gemäss Referenzprofil
beim Export weggelassen. `--add-models` übernimmt die Einstellung.

`schemaimport --create-fk-idx` entspricht ili2db `--createFkIdx`.
Es erzeugt Indizes für Basket-, Dataset-, Rollen- und Strukturverweise.
`--add-models` übernimmt die Einstellung des bestehenden Schemas.
`schemaimport --create-fk` entspricht ili2db `--createFk` und legt
aufgeschobene SQL-Fremdschlüssel an. Die Referenzprüfung der Datenbank
erfolgt beim Commit der Transaktion.
Das gilt auch für gemeinsam verwendete `T_Id` in Basistabellen und
Geometriehilfstabellen, Beziehungstabellen und alternative Smart2-Ziele.

`schemaimport --create-type-discriminator` fügt `T_Type` auf den
Wurzeltabellen auch bei nur einem konkreten Typ hinzu.
`--create-type-constraint` sichert vorhandene `T_Type`-Spalten mit einem
SQL-Check über die zulässigen Typen ab. Beide Optionen entsprechen
ili2db `--createTypeDiscriminator` und `--createTypeConstraint`.

`schemaimport --create-dataset-col` fügt gemäss ili2db `--createDatasetCol`
die indizierte Spalte `T_datasetname` hinzu. Der Transfer befüllt sie auf
Fachzeilen, Strukturzeilen und Geometriehilfszeilen aus dem gewählten
Datensatz. Beim Export bestimmt die Verknüpfung über `T_Basket` die Auswahl.

`schemaimport --create-std-cols` entspricht ili2db `--createStdCols`.
Der Transfer pflegt `T_LastChange`, `T_CreateDate` und `T_User` auf den
Fachtabellen und Geometriehilfstabellen. GeoPackage speichert die beiden
Zeitwerte als Unix-Millisekunden, PostgreSQL als lokale SQL-Zeitstempel.
Ein `update` erhält das Erstellungsdatum bestehender Zeilen.

`schemaimport --create-unique` übernimmt unbedingte `UNIQUE`-Regeln mit
direkten, in derselben Tabelle gespeicherten Schlüsselspalten. Bei
`UNIQUE (BASKET)` in INTERLIS 2.4 enthält der SQL-Schlüssel zusätzlich
`T_Basket`. ili2db 5.5.2 bricht bei der Schemaerzeugung für die geprüfte
Basket-Regel mit `no table attached` ab. Beide Werkzeuge können mit dem
von `interlis-db` erzeugten Schema Daten austauschen.

`schemaimport --create-num-checks` erzeugt Bereichsprüfungen für direkte
Ganzzahl- und Festkommaspalten. Wie bei ili2db `--createNumChecks` bleiben
Ganzzahlgrenzen exakt. Grenzen von Festkommazahlen werden für den SQL-Check
mit Binary64-Genauigkeit übernommen.

`schemaimport --create-text-checks` entspricht ili2db `--createTextChecks`.
PostGIS prüft, dass belegte Textwerte nach dem Trimmen mindestens ein
Zeichen enthalten. Normalisierte Textspalten übernehmen die
Zeichenprüfungen der Referenz. GeoPackage speichert die Einstellung.
`--sql-cols-as-text`-Spalten für einfache Attribute erhalten diese
Prüfungen nicht.

`schemaimport --create-date-time-checks` entspricht ili2db
`--createDateTimeChecks`. PostGIS prüft die inklusiven Grenzen der als
`DATE`, `TIME` oder `TIMESTAMP` gespeicherten Domänen. GeoPackage speichert
die Einstellung. Temporale Formate in SQL-Textspalten bleiben frei von
diesen SQL-Prüfungen.

`schemaimport --create-mandatory-checks` speichert die Einstellung für
ili2db `--createMandatoryChecks`. Im Profil mit `SqlNull=enable` erzeugt
die Referenz dazu keine zusätzlichen Pflichtprüfungen. Die aktive
strikte Variante von ili2pg 5.5.2 belegt auch optionale Referenzen mit
`IS NOT NULL` und liegt ausserhalb dieses Schemaerzeugungsprofils.

`schemaimport --create-meta-info` schreibt die Modellmetadaten in
`T_ILI2DB_META_ATTRS`. Dazu gehören deklarierte Meta-Attribute,
Kardinalitäten, Rollenarten und die SQL-Namen der Klassen eines Topics.
Metadaten von Rollen ohne eigene SQL-Spalte werden ebenfalls gespeichert.
`--add-models` ergänzt fehlende Einträge und erhält gespeicherte Werte.

Mit `schemaimport --coalesce-catalogue-ref` erzeugt das Kommando die
kompakte SQL-Abbildung für CHBase-V1-Katalogreferenzen, entsprechend
`--coalesceCatalogueRef` in ili2db. Die Option gilt beim Anlegen eines
`newClass`- oder Smart2-Schemas.

Die Optionen `--expand-multilingual` und `--expand-localised`
aktivieren die entsprechenden CHBase-Textabbildungen bei der
Schemaerzeugung. Beide Optionen können kombiniert werden.
Mit `--coalesce-multi-point`, `--coalesce-multi-line` und
`--coalesce-multi-surface` lassen sich die drei kompakten
Geometrieabbildungen für zweidimensionale Strukturen einzeln aktivieren.
Die Geometriespalte übernimmt den CRS und die Grenzen der Koordinatendomäne
der inneren Struktur.

Mit `schemaimport --expand-struct` legt das Kommando ein `newClass`- oder
Smart2-Schema mit den Fachspalten für `ili2db.mapping=EXPAND` an. In
Smart2 erhält jede konkrete Eigentümertabelle die zugehörigen Spalten
für das geerbte Attribut. `update` leert die frei gewordenen Spalten
einer gekürzten Liste. `--add-models` übernimmt die Einstellung des
bestehenden `newClass`-Schemas.

Mit `schemaimport --coalesce-json` legt das Kommando in einem neuen
`newClass`-Schema die JSON-Spalten für `ili2db.mapping=JSON` an. Die
Option lässt sich mit `--expand-struct` kombinieren. `--add-models`
übernimmt beide gespeicherten Einstellungen.

Mit `schemaimport --coalesce-array` erzeugt das Kommando in `newClass` und Smart2
die Spalten für `ili2db.mapping=ARRAY`. GeoPackage speichert die Arrays
als JSON-Text, PostGIS als typisierte SQL-Arrays. Die Option ist mit
`--expand-struct` und `--coalesce-json` kombinierbar. `--add-models`
übernimmt im bestehenden `newClass`-Schema die Array-Einstellung.
In Smart2 liegen geerbte Arrays in den konkreten Eigentümertabellen.
Beim Kürzen oder Entfernen einer Liste ersetzt `update` ihren SQL-Wert
und behält die Identität des Objekts bei.

`schemaimport --create-enum-col-as-itf-code` erzeugt numerische
SQL-Spalten für skalare Aufzählungen, entsprechend ili2db
`--createEnumColAsItfCode`. Boolean bleibt eine eigene SQL-Abbildung.
Mit `--create-enum-txt-col` kommen `_txt`-Spalten für skalare
Aufzählungen und Boolean hinzu. `--beautify-enum-disp-name` ersetzt im
Standardlabel Unterstriche durch Leerzeichen. Explizite Anzeigenamen aus
dem Modell haben Vorrang. Der Export liest den Fachwert aus der Hauptspalte.
Bei Inline-Aufzählungen mit `EXTENDED` liefert der konkrete Typ das
Label, auch wenn die Klassen eine gemeinsame `_txt`-Spalte verwenden.
Mit `--create-single-enum-tab` werden die Aufzählungswerte und
ihre Metadaten in `T_ILI2DB_ENUM` abgelegt, entsprechend ili2db
`--createSingleEnumTab`. Die Tabelle führt jede Definition mit ihrer
eigenen Zuordnung von XTF- und ITF-Codes. Bei Inline-Aufzählungen mit
`EXTENDED` erscheinen die Definitionen, für die das Schema eine
Fachspalte anlegt. Eine gemeinsam genutzte Basisspalte kann daher XTF-Codes
enthalten, die in `T_ILI2DB_ENUM` fehlen.
Mit `--create-enum-tabs` erhält jede Definition eine eigene
Lookup-Tabelle, entsprechend ili2db `--createEnumTabs`. `CLASSNAME`
enthält ihren SQL-Namen. Fachspalten verwenden weiterhin die separat
gewählte Kodierung. Bei Inline-Aufzählungen mit `EXTENDED` führen
Basis und Erweiterung je eine Lookup-Tabelle mit ihren effektiven Werten,
auch wenn sie eine Fachspalte teilen.
`--create-enum-tabs-with-id` erzeugt numerische Fachspalten und
Lookup-Tabellen mit `T_Id`, entsprechend ili2db
`--createEnumTabsWithId`. PostgreSQL verwendet die Schema-Sequenz.
Ein GeoPackage vergibt die Lookup-IDs innerhalb der jeweiligen Tabelle.
Erweiterte Aufzählungsdomänen teilen die Tabelle ihrer Basisdefinition.
`thisClass` unterscheidet die Zeilen für den jeweiligen Typ, auch
wenn beide denselben XTF-Code enthalten. `seq` folgt der expliziten
Ordnungsdeklaration des Typs.
Inline-Aufzählungen mit `EXTENDED` verwenden dieselbe Lookup-Regel,
wenn Smart2 oder Smart1 eine Fachspalte für die erweiterte Deklaration
anlegt. Mit `--create-fk` verweist die Fachspalte auf die Lookup-Tabelle.
Eine gemeinsam genutzte Basisspalte in `newClass` oder Smart1 wird für
Lookup-IDs abgewiesen. In diesem Layout speichert ili2db 5.5.2 nur
Lookup-Zeilen der Basis und scheitert beim Import neuer Codes der
Erweiterung. Die ITF-Kodierung ist für diese Inline-Erweiterungen
ebenfalls ausgeschlossen.
`--add-models` übernimmt die gespeicherten Aufzählungsoptionen.
Lookup-Zeilen für neue Definitionen werden in derselben Transaktion
ergänzt. Vorhandene IDs, Anzeigenamen und Inaktiv-Markierungen bleiben
erhalten.

## Bestehendes Schema ergänzen

```sh
interlis-db schemaimport --add-models --gpkg data.gpkg \
  --model Survey --model-file Shared.ili Survey.ili
```

`--add-models` ergänzt ein vorhandenes ili2db-Schema in einer
Transaktion. Das Kommando übernimmt dessen Einstellungen, die
gespeicherten SQL-Zuordnungen und die gemeinsame ID-Vergabe.
Zusätzliche lokale Abhängigkeiten werden wie bei der Schemaerzeugung
mit `--model-file` angegeben. Identische gespeicherte Quellen und
bereits abgebildete Abhängigkeiten werden wiederverwendet. Eine
Übersetzung zu einem vorhandenen Original wird als Modellquelle ergänzt
und verwendet dessen Fachtabellen. Eine
gemeinsam genutzte Strukturtabelle erhält die zusätzlichen
Rückverweisspalten für neue Eigentümer. Ein bereits abgebildetes
ausgewähltes Modell, eine abweichende gespeicherte Quelle oder eine
widersprüchliche Schemaoption führt zum Abbruch.

Im nullable Smart2-Profil kann `--add-models` unabhängige Modelle und
Modelle mit gemeinsamen skalaren Domänen ergänzen. Die gespeicherte
Quellversion einer gemeinsamen Domäne muss mit der über `--model-file`
angegebenen Quelle übereinstimmen. Die konkreten Tabellen erhalten die
geerbten Spalten gemäss dem gespeicherten Profil.
Gemeinsam genutzte Strukturtabellen erhalten auch in Smart2 die nullable
Rückverweisspalten für die neuen Eigentümer. Bei einer nachträglichen
Smart2-Erweiterung konnte ili2gpkg 5.5.2 in XTF 2.3 und 2.4 die Einträge in
`ATTRNAME` schreiben, ohne diese SQL-Spalten anzulegen. Der folgende Import
scheiterte an den fehlenden Spalten. Das von `interlis-db` ergänzte Schema
verwendet die Spaltenabbildung eines gemeinsamen Schemaimports beider Modelle.

Ein neues Modell kann in Smart2 ein bestehendes Topic erweitern und einen
konkreten Untertyp mit vollständiger eigener Tabelle ergänzen. Die gespeicherten
SQL-Namen und Vererbungsstrategien der Basistypen gelten weiterhin. Benötigt
eine bereits abgebildete Referenz für den neuen Typ weitere Zielspalten,
bricht `--add-models` mit dem Namen der betroffenen Referenz ab.

Im nullable Smart1-Profil ergänzt `--add-models` unabhängige Modelle und
skalare Untertypen in einer bestehenden Tabelle mit `T_Type` ohne Typcheck.
Die neuen Fachspalten sind nullable und übernehmen die aktiven SQL-Checks
für Zahlen, Texte und Zeitwerte des Schemas. Die Liste der Typwerte umfasst auch
Untertypen aus früheren Erweiterungen. Beim erneuten `--schemaimport` mit
ili2gpkg 5.5.2 fehlten in der Probe die physischen Spalten des Untertyps.
Die Katalogreferenz ist hier der gemeinsame Schemaimport beider Modelle.
Speicherte die Tabelle vorher genau einen konkreten Typ, ergänzt
`--add-models` die Spalte `T_Type`, übernimmt den vorhandenen Typ in alle
bisherigen Zeilen und setzt `NOT NULL`. Die Spalte erhält keinen Default.

In Smart1 und Smart2 kann ein neues Modell externe eingebettete Rollen auf
Klassen in bereits abgebildeten Topics ergänzen. Die Rollenspalten stehen
in der neuen Eigentümertabelle. Bei einem abstrakten Ziel bestimmt die
gespeicherte Vererbungsabbildung die konkreten Zieltabellen. Fehlende
Klassenzuordnungen und inkonsistente Zieltabellen führen zum Abbruch.

## Aufruf

```sh
interlis-db import --gpkg data.gpkg --model Example addition.xtf
interlis-db export --gpkg data.gpkg --model Example result.xtf
```

Bei einem Schema ohne `T_basket` liest der Import die Kennungen für die
Referenzauflösung aus dem XTF und fügt die Objekte zu den Fachtabellen
hinzu. Der Export fasst die Zeilen der ausgewählten Modelle je konkretem
Topic in einem erzeugten Basket zusammen. Direkt per SQL eingefügte
Fachzeilen brauchen in diesem Schema keine Basket-ID. Fehlt einem Objekt
ohne fachliche OID die gespeicherte TID, erzeugt der Export eine
Transferkennung aus seinem SQL-Schlüssel und verwendet sie auch in
Referenzen. Das gilt auch für Zeilen aus einem früheren Import ohne
`--create-tid-col`.

Schemata mit `T_basket` verwenden `--dataset` für den Transfer. Beim Export
kannst du alternativ einzelne Baskets mit je einem `--basket BID` auswählen.
Jede BID muss im Schema eindeutig sein und zu einem gewählten Modell gehören.
Der Export folgt der Reihenfolge der internen Basket-IDs. Baskets ohne
Datensatzzuordnung können ebenfalls ausgewählt werden. Externe Verweise auf
ausgelassene Ziele bleiben nur bei eindeutiger Auflösung exportierbar.
Mit je einem `--topic Modell.Topic` wählst du alle Baskets des angegebenen
Topics über Datensatzgrenzen hinweg. Massgeblich ist der gespeicherte
qualifizierte Name. Ein Basistopic wählt keine abgeleiteten Topics mit aus.
Ein bekanntes Topic ohne Baskets ergibt einen Modellheader und einen
leeren Datenabschnitt. Ein Datensatzimport erfordert mindestens einen Basket.
Pro Export gilt eine Auswahlart. Die BIDs und TIDs im ausgegebenen Transfer
müssen eindeutig sein. Kollisionen führen zum Abbruch.

```sh
./gradlew :interlis-db:installDist
interlis-db/build/install/interlis-db/bin/interlis-db export \
  --gpkg data.gpkg --model Example --dataset original original.xtf
interlis-db/build/install/interlis-db/bin/interlis-db import \
  --pg-url jdbc:postgresql://localhost:5432/geodata --schema example --user editor \
  --model Example --dataset addition addition.xtf
```

Das PostgreSQL-Passwort wird bei Bedarf aus `INTERLIS_DB_PASSWORD` gelesen.
Beim Export können mehrere Modelle derselben INTERLIS-Sprachversion über
je ein `--model` ausgewählt werden. Mit `--dataset` muss die Modellauswahl
sämtliche Baskets des Datensatzes abdecken. Bei einem Datensatz- oder
Basket-Export nennt der Header die Modelle der exportierten Baskets.
Bei `--topic` nennt er die Modelle der gewählten Topics. Ein Schema kann
getrennte Datensätze in beiden Sprachversionen enthalten.
Mit `export --model ORIGINAL --export-model UEBERSETZUNG` wird ein
XTF-2.3-Datensatz in der gewählten Modellsprache ausgegeben. `--model`
bezeichnet die gespeicherten Baskets und `--export-model` deren Ausgabeform.
Für XTF 2.4 ist als Ausgabeform das Originalmodell zulässig.
Beim Import können ebenfalls mehrere Modelle derselben Sprachversion
ausgewählt werden. Header und Baskets müssen zur Auswahl passen. Ein
unzulässiger späterer Basket rollt den ganzen Import zurück.
Ein Import legt den angegebenen Datensatz an. Einen bereits vorhandenen
Datensatznamen meldet er als Fehler. Ein erfolgreicher Export ersetzt die
ausdrücklich gewählte Zieldatei atomar. Ein abgebrochener Export belässt eine
bisherige Zieldatei. Ein Import schreibt Objekte, Baskets und den Datensatz
in einer Transaktion und bestätigt das XTF-Quellenende vor dem Commit.
Nach dem Rollback lässt sich der Import mit demselben Datensatznamen
wiederholen.
PostgreSQL-Sequenznummern können nach einem Rollback Lücken aufweisen.

## Datensätze ersetzen

`replace` verwendet dieselben Optionen wie `import`. Es behält die interne ID
des benannten Datensatzes und ersetzt alle seine Baskets und Objekte. Die neuen
Zeilen erhalten neue interne IDs, auch bei gleicher BID oder TID. Ein fehlender
Datensatz wird angelegt. Die Änderung wird erst nach dem bestätigten Ende des
XTF-FULL-Transfers veröffentlicht. Die Löschung erfasst auch Klassenfragmente, Strukturen, Linkzeilen und
Geometrien der bisherigen Baskets. PostGIS sperrt die betroffenen Tabellen für konkurrierende Schreibzugriffe.
GeoPackage verwendet seine bestehende Schreibtransaktion.
Bei gemischten Datensätzen muss die Auswahl per `--model` die bisherigen
Baskets vollständig abdecken.
Bei abgeleiteten und bei übersetzten XTF-2.3-Baskets bestimmt das
gespeicherte Topic die Modellauswahl für Ersatz und Löschung.
Zeigt eine bestehen bleibende Referenz auf eine der alten internen Objekt-IDs,
wird der Ersatz zurückgerollt. Ausgehende `EXTERNAL`-Referenzen der neuen
Lieferung verwenden die bestehende Suche im selben Schema.

## Baskets aktualisieren

`update` verwendet dieselben Optionen wie der datensatzbezogene Import
und setzt ein Schema mit `--create-tid-col` voraus. Ein fehlender Datensatz
wird angelegt. Jeder gelieferte Basket enthält seinen vollständigen neuen
Bestand. Vorhandene Baskets und überlebende Objekte behalten ihre internen IDs.
Ausgelassene Objekte im gelieferten Basket werden gelöscht. Ausgelassene
Baskets bleiben erhalten. Bei fremder oder mehrdeutiger BID sowie bei einem
abweichenden Topic bricht die Operation ab. Eine OID kann bei der
Aktualisierung innerhalb ihres Baskets wiederverwendet werden.
Die Auswahl per `--model` kann sich bei einem Update auf die gelieferten
Modelle beschränken.

Das Update-Profil unterstützt Klassen mit `UUIDOID`, `STANDARDOID`
oder gespeicherten Transfer-IDs aus `--importTid --importBid`.
BID und TID müssen für den Abgleich im Bestand vorliegen.
Bei fehlender oder mehrdeutiger Zuordnung bricht das Update ab.
In einer Klassenhierarchie teilen die aktualisierten Fragmente weiterhin
dieselbe `T_Id`. Die konkrete Klasse eines bestehenden Objekts muss
übereinstimmen. Geometriewerte können ersetzt oder gelöscht werden.
Strukturzeilen des gelieferten Baskets werden aus den neuen optionalen Werten,
Listen und Bags aufgebaut. Die Eigentümer behalten ihre internen IDs.
Lokale Referenzattribute werden gegen den gelieferten neuen Basketbestand
aufgelöst. Vorwärtsreferenzen können dadurch auf neu gelieferte Objekte zeigen.
Eingebettete Rollen und anonyme Linkzeilen geben den neuen Basketinhalt wieder,
einschliesslich eigener Attribute und `ORDERED`-Positionen. Eigenständige
UUID-Links behalten ihre interne ID. Die bereits beschriebene Behandlung
von Positionswerten durch ili2db 5.5.2 gilt auch beim Werkzeugwechsel mit
`update`.
`EXTERNAL`-Referenzen können auf später gelieferte Objekte sowie auf Ziele
in anderen Baskets oder Datensätzen zeigen. Externe Zielfremdschlüssel
werden gegen den Endbestand gebunden. Bestehen bleibende Referenzen auf
zu löschende Objekte und mehrdeutige Ziele führen zum Rollback.

## Datensätze löschen

`interlis-db delete --gpkg data.gpkg --model Example --dataset original`
entfernt die Fachzeilen, Baskets und Dataset-Zeile in derselben Transaktion.
Für PostGIS gelten die üblichen Verbindungsoptionen. Der Datensatz muss
vorhanden sein und seine Baskets müssen zu den ausgewählten Modellen gehören.
Eine Referenz aus einem bestehen bleibenden Basket auf ein zu löschendes
Objekt führt zum Rollback. Die gemeinsame ID-Vergabe läuft nach der Löschung
weiter.

## Bestehende Raumindizes

GeoPackage-Dateien mit RTree-Indizes für XY- und XYZ-Geometrien können mit
`import`, `replace`, `update` und `delete` verwendet werden. Die registrierte virtuelle
Tabelle und ihre Standardtrigger müssen zur gemappten Geometriespalte und
zur internen ID passen. Die Trigger pflegen den Index in derselben Transaktion
wie die Fachzeilen. Indizierte GeoPackage-Geometrien erhalten eine XY-Hülle
im Header. Bei Kreisbögen umfasst diese Hülle auch die Extrempunkte des
durchlaufenen Bogens. In XTF 2.4 umfasst sie bei `MULTICOORD`,
`MULTIPOLYLINE`, `MULTISURFACE` und `MULTIAREA` alle Teile.
Bei PostGIS pflegt PostgreSQL die vorhandenen GiST-Indizes.

## Referenzprofil

Für das Profil mit gespeicherten Basket- und Transferkennungen verwende
bei `interlis-db` die Optionen `--create-basket-col --create-tid-col`.
Für die Erzeugung mit dem Originalwerkzeug verwende
`--noSmartMapping --createBasketCol --createTidCol --sqlEnableNull
--defaultSrsAuth EPSG --defaultSrsCode 2056` an. Verwende beim Referenzimport
`--importTid --importBid` und beim Referenzexport `--exportTid`. Dieses
Profil bewahrt die gespeicherten BID und TID beim Werkzeugwechsel.
Ein Datensatz enthält mindestens einen Basket. Baskets ohne Objekte sind
zulässig.

Der Transfer liest auch Referenzschemata ohne `--sqlEnableNull`. Bei direkt
gespeicherten Pflichtwerten setzt die Datenbank die vorhandenen `NOT NULL`-
Bedingungen durch. `schemaimport --add-models` übernimmt das
Nullbarkeitsprofil des bestehenden `newClass`-Schemas. Für neue Tabellen
gelten dieselben Pflichtspaltenregeln wie bei der Neuanlage. Neue
Besitzerspalten in gemeinsam verwendeten Strukturtabellen bleiben nullable.
Das gilt auch für einen als `MANDATORY` deklarierten Einzelwert. Der
DB-Transfer kann eine Elternzeile ohne Strukturwert importieren und exportieren.
Die Vollständigkeit prüft der separat aufgerufene [Validator](validator.md).

`schemaimport --sql-not-null` erzeugt ein striktes `newClass`-Schema für
Klassen und ihre Strukturen. Direkt gespeicherte skalare Pflichtattribute
erhalten in einer Klassenhierarchie `NOT NULL` auf der Tabelle ihrer
Deklaration. Die Fragmente eines Objekts verwenden dieselbe `T_Id`.
Pflichtgeometrien in flachen Klassen und lokale, ungeordnete 1:1- und
1:n-Rollen mit Kardinalität `{1}` und einer konkreten
Zieltabelle erhalten `NOT NULL`. Geometrien verwenden ein festes CRS.
Bei der Aufteilung der Geometriespalten einer flachen Klasse gilt die
Nullbarkeit auch auf der jeweiligen Hilfstabelle. Lokale Referenzattribute
mit einer konkreten, vererbungsfreien Zielklasse bleiben auch bei `MANDATORY REFERENCE TO`
nullable. XTF 2.3 überträgt diese Referenzattribute innerhalb einer Struktur.
XTF 2.4 unterstützt zusätzlich direkte Klassenattribute. Ohne die Option
gilt `SqlNull=enable`. Die Kombination mit `--create-mandatory-checks` ist
auf das nullable Profil beschränkt.
Geometrieabbildungen mit `--multi-srs` verwenden das Profil mit `SqlNull=enable`.

Der FULL-Transfer mit GeoPackage und PostGIS unterstützt bestehende
ili2db-Schemata mit `--sqlColsAsText`. Einfache Attribute werden als SQL-Text
gespeichert. Der Transfer erhält unter anderem Zahlenschreibweisen,
Zeitbruchteile, Enum-Codes und die Schreibweise von OID-Attributen.
BLACKBOX verwendet Base64 beziehungsweise XML-Text. Bei reinem
BLACKBOX-XML-Text speichert die Referenz-Textspalte keine Namespace-Bindungen
des umgebenden XTF-Elements. Das Profil unterstützt Enum-Codes ohne
zusätzliche Enum-Schemaoptionen und ohne `--expandStruct`.
`schemaimport --sql-cols-as-text` erzeugt dieses Profil. `--add-models`
übernimmt die Textabbildung aus dem bestehenden Schema.
`import --raw-scalar-text` speichert den XML-Textinhalt direkt abgelegter
skalarer Attribute in diesem Profil auch bei ungültiger Wertschreibweise.
Referenzen, Geometrien, OIDs und BLACKBOX behalten ihre Transferkodierung.
Nach der Korrektur der gespeicherten Werte kann der reguläre Export erfolgen.

In bestehenden `newClass`-Schemata aus ili2db 5.5.2 mit
`--coalesceCatalogueRef` verwendet der FULL-Transfer die Fremdschlüsselspalte
des Fachattributs für CHBase-V1-Katalogreferenzen. Dies gilt für Einzelwerte
aus `CatalogueReference` und `MandatoryCatalogueReference` mit dem einzigen
Feld `Reference`. Das XTF behält die Struktur. Ein fehlender Verweis wird als
SQL-`NULL` gespeichert und das Feld beim Export weggelassen. Das Ziel kann
in einem separaten Datensatz desselben Schemas liegen.
In Smart2-Schemata kann das Katalogziel in mehreren konkreten Tabellen liegen.
Der Import befüllt die Fremdschlüsselspalte der passenden Tabelle. Mehrere
passende Zielzeilen oder gleichzeitig befüllte Zielspalten werden abgewiesen.

Für `--expandMultilingual` in bestehenden `newClass`-Schemata verwendet
der FULL-Transfer die gespeicherte Basisspalte und ihre Sprachspalten
`_de`, `_fr`, `_rm`, `_it` und `_en`. Die Basisspalte enthält den
Text ohne Sprachangabe. Der Export baut daraus die CHBase-V1- oder
CHBase-V2-Struktur `MultilingualText` beziehungsweise `MultilingualMText`.
Mehrere Einträge für dieselbe Sprache oder Sprachen ohne zugehörige
Spalte führen beim Import zu einer Diagnose.

Bei `--expandLocalised` liegt der Text des Einzelwerts in der
Basisspalte, die optionale Sprache in `<stamm>_lang`. Das gilt für
`LocalisedText` und `LocalisedMText` beider CHBase-Versionen.

Bei `--coalesceMultiPoint` verwendet der FULL-Transfer in bestehenden
`newClass`-Schemata eine `MULTIPOINT`-Spalte für
`ili2db.mapping=MultiPoint`. Das Einzelattribut enthält eine Struktur
mit einem BAG aus zweidimensionalen Koordinatenstrukturen. Die
Komponenten und ihre Duplikate bleiben im XTF erhalten.
`--coalesceMultiLine` bildet `MultiLine` und `MultiDirectedLine`
aus CHBase V1 sowie gleich aufgebaute, mit
`ili2db.mapping=MultiLine` markierte Strukturen in einer
`MULTICURVE`-Spalte ab. Der Transfer erhält Kreisbogen und Linienrichtung.
`--coalesceMultiSurface` verwendet für die CHBase-V1-Struktur
`MultiSurface` und für entsprechend markierte Strukturen eine
`MULTISURFACE`-Spalte. Die zweidimensionale Abbildung erhält
Aussenringe, Innenringe und Kreisbogen.

Das kombinierte Smart2-Profil ist mit flachen, über `ili2db.mapping`
markierten Strukturtypen geprüft. Ein geerbtes mehrsprachiges Attribut
liegt in der Tabelle seines konkreten Typs. Die kompakte Geometrie kann
in der Hilfstabelle liegen. `update` behält Objekt- und Basket-ID und
setzt weggefallene Sprachen auf `NULL`. `delete` entfernt auch die
Hilfszeilen und die zugehörigen Einträge im GeoPackage-RTree.

Für bestehende Smart1-Schemata unterstützt `interlis-db` den FULL-Import
und -Export von Klassenhierarchien mit skalaren Attributen. Das
Referenzschema lässt sich mit `--noSmartMapping --smart1Inheritance`
und den übrigen Optionen des Referenzprofils erzeugen. Tabellen und
konkrete Typen werden über die gespeicherten Zuordnungen und `T_Type`
aufgelöst.
Bei numerischen `EXTENDED`-Attributen mit gleichem Speichertyp und gleicher
Festkommaskala bestimmt die Basisdeklaration Spalte, `ATTRNAME`, Präzision
und optionale SQL-Bereichschecks in der gemeinsamen Tabelle.
Übernimmt eine konkrete Tabelle das Attribut einer abstrakten Klasse ohne
eigene Tabelle, verwendet sie die für ihren Typ wirksame Deklaration.
Weitere Untertypen teilen diese Spalte.
Referenzattribute und eingebettete 1:n-Rollen verwenden die gespeicherte
physische Zieltabelle. Bei einem enger deklarierten Ziel wird auch der
konkrete Typ der Zielzeile geprüft.
Für Smart2-Schemata unterstützt der Transfer skalare Klassenattribute
in den vollständigen Tabellen der konkreten Klassen. Das Referenzschema
verwendet dazu `--noSmartMapping --smart2Inheritance`.
Bei numerischen `EXTENDED`-Attributen mit gleichem Speichertyp und gleicher
Festkommaskala erhält die konkrete Tabelle die Spalte und den `ATTRNAME` der
wirksamen Deklaration. Präzision und optionale SQL-Bereichschecks verwenden
deren verengten Bereich.
Bei polymorphen Referenzattributen und ungeordneten Rollen verwendet
Smart2 alternative Fremdschlüsselspalten. Der Import befüllt die
passende Zielspalte. Mehrere gleichzeitig belegte Zielspalten für
eine Referenz führen zu einer Diagnose.
Optionale Strukturen und `LIST OF` können in beiden Smart-Profilen
von einer Basisklasse geerbt werden. Die gespeicherte Rückverweisspalte
gehört zur tatsächlichen Eigentümertabelle. Jede Strukturzeile hat
genau einen Eigentümer und behält ihre Listenposition.
Bei geerbten Punkten bestimmen die gespeicherten Zuordnungen die
Geometriespalte und ihren CRS. GeoPackage kann die geerbte oder die
lokal deklarierte Geometrie in eine Hilfstabelle auslagern. Die Hilfszeile
gehört zum konkreten Typ und teilt dessen Objekt- und Basket-ID.
`update`, `replace` und `delete` verwenden in beiden Smart-Profilen
die tatsächlich belegten Tabellen. Updates behalten die internen IDs
überlebender Objekte. Eingehende Referenzen aus verbleibenden Datensätzen
schützen die referenzierten Zeilen.
Mit `schemaimport --inheritance smart1` lässt sich ein neues Smart1-Schema
aus dem Modell erzeugen. Die Abbildung verwendet die deklarierte
Hierarchie und die Assoziationsrollen. `schemaimport --inheritance smart2`
erzeugt eine vollständige Tabelle pro konkretem Typ. Ohne die Option
verwendet `schemaimport` die `newClass`-Abbildung.

Unterstützt sind Klassen eines eigenständigen Modells in
INTERLIS 2.3 und 2.4 mit Text, Boolean, 32- und 64-Bit-Ganzzahlen und Aufzählungen als
XTF-Code. PostGIS speichert mehrere optionale Geometrieattribute in der
Klassentabelle und erhält mit `--create-geom-index` für jede Geometriespalte
einen GiST-Index. GeoPackage speichert die erste Geometrie in der
Klassentabelle und jede weitere in einer Hilfstabelle. PostGIS verwendet
dieselbe Ablage mit `schemaimport --one-geom-per-table` (ili2pg `--oneGeomPerTable`).
Jede Hilfszeile teilt `T_Id` und `T_Basket` mit ihrem Eigentümer und
ist auch für `NULL` vorhanden.

Skalare Dezimalwerte mit festen Nachkommastellen verwenden PostGIS
`numeric(p,s)` und GeoPackage `DOUBLE`. PostgreSQL erhält beim Import
mit `interlis-db` den exakten Dezimalwert innerhalb der SQL-Präzision.
GeoPackage verwendet die Genauigkeit von Binary64. Der Referenzimport
von ili2db 5.5.2 kann auf beiden Backends grosse Dezimalwerte runden.
`INTERLIS.XMLDate` in XTF 2.3 und `DATE` in XTF 2.4 belegen eine
SQL-`DATE`-Spalte. Der Adapter verwendet das Kalenderdatum ohne Zeitzone.
`INTERLIS.XMLTime` beziehungsweise `TIMEOFDAY` verwendet eine
SQL-`TIME`-Spalte mit Millisekunden. Der Transfer schreibt lokale Uhrzeiten
unabhängig von der JVM-Zeitzone.
`INTERLIS.XMLDateTime` beziehungsweise `DATETIME` überträgt ein
lokales Datum mit Uhrzeit. GeoPackage verwendet `DATETIME` in der
ili2gpkg-Schreibweise mit `Z`. PostGIS verwendet einen
`timestamp without time zone`. Das Kommando übernimmt die lokalen
Felder einschliesslich Millisekunden.
`FORMAT`, `NAME` und `URI` verwenden die ili2db-Textspalten und
Typmetadaten. Dazu gehören eingeschränkte temporale Formatdomänen.
Fachattribute vom Typ `INTERLIS.UUIDOID` liegen als `TEXT(36)` in
GeoPackage und als `uuid` in PostGIS. Das Kommando normalisiert die
Schreibweise auf Kleinbuchstaben. XTF 2.3 verwendet das `OID`-Attribut,
XTF 2.4 den UUID-Wert als Text.
`BLACKBOX BINARY` belegt `BLOB` in GeoPackage und `bytea` in PostGIS.
Der Adapter unterscheidet leere Binärwerte und SQL `NULL` und begrenzt
die gelesene Bytezahl anhand des Base64-Limits der XTF-Codecs.
`BLACKBOX XML` belegt `TEXT` in GeoPackage und `xml` in PostGIS. Der
Adapter speichert das XML-Fragment und erhält Namespaces innerhalb
seiner Elemente. Der XTF-2.4-Export von ili2db 5.5.2 gibt dasselbe
Fragment als maskierten Text aus. In XTF 2.3 kann ili2gpkg 5.5.2
beim Export von XML-Payloads mit Nicht-ASCII-Zeichen mit einem
UTF-8-Fehler abbrechen.

`schemaimport --gpkg-multi-geom-per-table` verwendet das ili2gpkg-Profil
`--gpkgMultiGeomPerTable` mit mehreren Geometriespalten in einer Tabelle.
`gpkg_contents` übernimmt die SRS-ID und die Grenzen der ersten
physischen Geometriespalte. Jede Spalte erhält einen Katalogeintrag mit
ihrer eigenen SRS-ID und mit `--create-geom-index` eigene RTree-Trigger.
Dieses optionale Profil weicht von der GeoPackage-Regel mit einer
Geometriespalte pro Feature-Tabelle ab.

Für Geometrien mit festem CRS erzeugt `schemaimport --multi-srs` die
EPSG-suffigierten Spalten und Metadaten von ili2db `--multiSrs`. Ein
Geometrieattribut mit EPSG:2056 erhält beispielsweise die Spalte
`lage_2056`. Bei bestehenden Schemata liest der Transfer die tatsächlichen
Namen aus den ili2db-Zuordnungen.

XTF 2.4 unterstützt statisch über `CONTEXT` gebundene generische
Punktdomänen. Bei einer einzigen erlaubten konkreten Domäne akzeptiert der
Import auch den von ili2db 5.5.2 erzeugten Basket ohne `ili:domains`.
Der Export schreibt die eindeutige Zuordnung explizit.

In bestehenden ili2db-`--multiSrs`-Schemata wählt der XTF-2.4-Transfer
die Punktspalte anhand der konkreten Domäne. Der Import speichert die
Zuordnung in `T_ILI2DB_BASKET.domains` und befüllt die gewählte
EPSG-Variante. Beim Export führen Werte in einer inaktiven Variante zum Abbruch.
`schemaimport --multi-srs` plant für eine aufgeschobene Punktdomäne eine
Spalte pro konkretem EPSG-Code. Bei `POLYLINE`, `SURFACE` und `AREA`
gilt die Basketbindung der generischen `VERTEX`-Domäne. Dieselbe
Auswahl gilt für `MULTICOORD`, `MULTIPOLYLINE`, `MULTISURFACE` und
`MULTIAREA` mit XY- oder XYZ-Koordinaten. Die konkreten Domänen liefern die
Spalten- und Kataloggrenzen.

In bestehenden XTF-2.3-Schemata mit ili2db `--altSrsModel` exportiert
`interlis-db` benannte Datensätze aus dem Original- und dem Alternativmodell.
Die Modelle stehen gemeinsam in einer gespeicherten Modelldatei und haben
gleich strukturierte flache Topics und Klassen. Das gespeicherte Topic
bestimmt die EPSG-Spalte sowie die Namen im XTF. Für ein neues Schema
erzeugt `schemaimport --multi-srs --alt-srs-model Original=Alternative`
die gemeinsamen Tabellen. `--model` bezeichnet das Originalmodell. Der
Import speichert das ursprüngliche Topic jedes Baskets und verwendet dessen
EPSG-Spalte.

Bei `update` behalten bestehende Baskets ihre Domänenbindung und ihr
Topic. `replace` kann Baskets mit einer neuen CRS-Auswahl liefern.
`delete` entfernt die zugehörigen Hilfszeilen und Raumindexeinträge
in derselben Transaktion.

Für neue Schemata setzt `schemaimport --default-epsg CODE` den
Standard. Ohne Option gilt EPSG:2056. GeoPackage verwendet die vorhandenen
EPSG-Definitionen aus dem Referenzprofil, darunter LV03 (21781) und
WGS84 (4326). PostGIS prüft den Code in `spatial_ref_sys`.
Für Koordinatenattribute gilt zuerst `REFSYS` (INTERLIS 2.4), dann
`!!@ CRS` an der Typdeklaration oder am Modell. Bei einem Verweis auf
eine Koordinatendomäne ist deren Deklaration massgeblich. Ein gespeichertes
`SETTINGS.modelSrsCode` ersetzt die CRS-Metainfo des betreffenden Modells.
Ohne CRS-Angabe aus dem Modell gilt `SETTINGS.defaultSrsCode`. Der räumliche Katalog muss
zur Auflösung passen. Linien und Flächen verwenden das CRS ihrer
`VERTEX`-Domäne. Verschiedene Geometrieattribute eines Objekts dürfen
unterschiedliche CRS haben. Der Schlüssel einer Geometrie-Hilfstabelle
in `T_ILI2DB_TRAFO` enthält den EPSG-Code des betreffenden Attributs.
Ein vorhandener `srid`-Eintrag in `T_ILI2DB_COLUMN_PROP` muss ebenfalls
diesen EPSG-Code enthalten. Widersprüchliche Zuordnungen führen zum Abbruch.
GeoPackage schreibt die lokale `srs_id` des Katalogs in den Geometrie-Header.
Bei bestehenden Schemas von ili2gpkg 5.5.2 liest `interlis-db` auch Header
mit dem EPSG-Code, sofern dieser Code im Katalog eindeutig der lokalen ID
zugeordnet und die Nummer als lokaler Schlüssel unbelegt ist. PostGIS
verwendet im Referenzprofil die EPSG-Nummer als SRID.
XYZ-Geometrien speichern die dritte
Ordinate im SQL-Wert und die exakten Modellgrenzen als `c3Min` und `c3Max`
in den Spaltenmetadaten. PostGIS verwendet den Z-Typzusatz, GeoPackage das
Z-Bit der ili2gpkg-Kodierung. Die Raumindizes arbeiten mit der XY-Hülle.
Die Geometriespalte speichert XY- und XYZ-Punkte, `POLYLINE` sowie
`SURFACE` oder `AREA` mit einem Aussenring und optionalen Innenringen. Linien und Flächenränder unterstützen
`WITH (STRAIGHTS)`, `WITH (ARCS)` und
`WITH (STRAIGHTS, ARCS)`. Wie bei ili2db 5.5.2 liegen Linien in
`COMPOUNDCURVE`-Spalten und Flächen in `CURVEPOLYGON`-Spalten.
Die Spaltenmetadaten weisen den Modelltyp `SURFACE` oder `AREA` aus.
Jeder Ring wird als `COMPOUNDCURVE` geschrieben. Aufeinanderfolgende
Geraden bilden eine `LINESTRING`-Komponente, aufeinanderfolgende Bögen
eine `CIRCULARSTRING`-Komponente. Bei einem XYZ-Bogen tragen Anfang und
Ende Z. Die XY-Zwischenposition wird wie in ili2db mit einem
`NaN`-Platzhalter für Z gespeichert.

Bestehende ili2db-Schemata mit `--strokeArcs` speichern XY-Polylinien in
`LINESTRING`-Spalten und XY-`SURFACE` sowie XY-`AREA` mit Aussen- und
Innenringen in `POLYGON`-Spalten. `interlis-db` liest die Option aus den
Schemaeinstellungen und prüft den SQL-Typ im Katalog sowie in den
Spaltenmetadaten. Gerade Polylinien bleiben in ihrer ursprünglichen
Reihenfolge erhalten. Beim Import bleiben Anfang, Stützpunkt und Ende
eines Kreisbogens erhalten. Die Sehnenabweichung richtet sich nach den
Nachkommastellen der ersten Achse der Koordinatendomäne. Bei ganzzahligen
Achsen beträgt die Toleranz eine halbe Koordinateneinheit. Grosse Bögen
folgen der durch den Stützpunkt bestimmten Seite. Bei einem Vollkreis
bilden Anfang und Stützpunkt die Enden des Durchmessers. Zwei benachbarte
`AREA`-Objekte mit entgegengesetzt gelieferten gemeinsamen Bögen erhalten
dieselben Stützpunkte in umgekehrter Reihenfolge. Der Export
liefert die gespeicherten Geraden. Ein Import rollt zurück, wenn die
Segmentierung das Koordinatenlimit überschreitet. In XTF 2.4 verwendet
`MULTIPOLYLINE` dafür `MULTILINESTRING`. Dieselbe Speicherung gilt für
die zusammengefassten CHBase-Strukturen `MultiLine` und `MultiDirectedLine`
in beiden XTF-Versionen. `MULTISURFACE` und `MULTIAREA` in XTF 2.4
sowie die zusammengefasste CHBase-Struktur `MultiSurface` in beiden
Versionen verwenden `MULTIPOLYGON`. Leere SQL-Sammlungen werden beim
Export als fehlendes Attribut übertragen.

In XTF 2.4 FULL unterstützen die folgenden Spaltentypen XY und XYZ.
Alle Teile einer Geometrie liegen in Lieferreihenfolge in demselben
Spaltenwert. `typeKind` weist den INTERLIS-Modelltyp aus.

| INTERLIS | SQL | WKB-Teile |
|---|---|---|
| `MULTICOORD` | `MULTIPOINT` | `POINT` |
| `MULTIPOLYLINE` | `MULTICURVE` | `COMPOUNDCURVE`, beim Lesen auch `LINESTRING` |
| `MULTISURFACE`, `MULTIAREA` | `MULTISURFACE` | `CURVEPOLYGON`, beim Lesen auch `POLYGON` |

Das Limit von 100’000 gespeicherten XY-Paaren gilt je Geometriewert über
alle Teile und Ringe hinweg. Bogen-Zwischenpunkte und wiederholte
Anschlusspunkte zwischen Komponenten zählen mit. Neue GeoPackages
registrieren je nach Spaltentyp `gpkg_geom_COMPOUNDCURVE`,
`gpkg_geom_CURVEPOLYGON`, `gpkg_geom_MULTICURVE` oder
`gpkg_geom_MULTISURFACE`. Beim Lesen genügen der übereinstimmende
Geometriekatalog und das passende Binärformat. Ohne Raumindex verwenden
GeoPackage-Flächen, gebogene Linien und Geometriesammlungen den Header
ohne XY-Ausdehnung.
[GeoPackage-Geometrieerweiterung](https://www.geopackage.org/spec131/#extension_geometry_types)

Beim Import von XTF 2.3 fügt der Adapter die `POLYLINE`-Teile einer
`BOUNDARY` in Lieferreihenfolge zusammen, wenn die Anschlusspunkte
numerisch übereinstimmen.
Das Datenbankprofil verlangt geschlossene Ringe. Eine Lücke oder ein
offener Ring wird am Attribut diagnostiziert. Der Export schreibt in
XTF 2.3 und 2.4 je einen Linienzug pro Ring.
Bei `AREA` im FULL-Transfer wird jedes Gebietsobjekt einzeln gespeichert.
Gebietsübergreifende Prüfungen wie Überlappungen und die Übereinstimmung
gemeinsamer Grenzsegmente führt der Validator aus.

Ein fehlendes XTF-Attribut ergibt SQL `NULL`. Eine ausdrücklich leere
XTF-Sammlung (`<multicoord/>`, `<multipolyline/>` oder `<multisurface/>`)
wird beim Import am Attribut diagnostiziert und führt zum Rollback.
Beim Export erscheint eine als SQL-Geometrie mit `EMPTY` gespeicherte
Sammlung als fehlendes Attribut. Ein leeres Element in einer gespeicherten
Sammlung führt zu einer Diagnose.

Der XTF-Import erwartet bei einem Bogen Start-, Zwischen- und Endpunkt.
Ein expliziter Radius (`R` in XTF 2.3 oder `r` in XTF 2.4) führt zu
einer Diagnose am Attribut und zum Rollback. Beim Export stammen die drei
Punkte aus der gespeicherten Geometrie. Der XTF-2.4-Writer schreibt den
Endpunkt vor dem Zwischenpunkt. Der Leser akzeptiert auch die umgekehrte
Reihenfolge aus Exporten von ili2db 5.5.2.
[INTERLIS 2.4, Anhang C](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=133)

In bestehenden Schemata mit ili2db-`--expandStruct` kann ein optionaler
flacher Strukturwert mit Text, Boolean, 32-Bit-Ganzzahlen und Aufzählungen
direkt in der Eigentümertabelle liegen. `TRAFO` weist die Expansion aus,
`ATTRNAME` benennt je Feld die physische Spalte. Die Typmarkierung enthält
den gespeicherten SQL-Namen der Struktur. Eine vorhandene, leere Struktur
hat eine Typmarkierung und ausschliesslich `NULL`-Felder.
Expandierte `LIST OF`- und `BAG OF`-Attribute verwenden bis zu fünf
nummerierte Spaltengruppen. Doppelte BAG-Einträge bleiben erhalten. Bei einer Obergrenze ab sechs verwendet ili2db für
Strukturwerte die reguläre Strukturtabelle. Ein optionaler, ebenfalls
expandierter innerer Strukturwert erhält eine eigene Typmarkierung und
eigene Feldspalten in derselben Tabelle.

In bestehenden Schemata mit ili2db-`--coalesceArray` kann eine
`LIST OF`- oder `BAG OF`-Sammlung mit genau einem Feld je Strukturelement in einer
Spalte liegen. Unterstützt sind Text, Boolean, 32-Bit-Ganzzahlen,
Dezimalwerte mit festen Nachkommastellen und Aufzählungen als XTF-Code.
Duplikate bleiben erhalten. GeoPackage speichert ein JSON-Array in `TEXT`,
PostGIS verwendet `varchar[]`, `boolean[]`, `integer[]` oder `numeric(p,s)[]`.
GeoPackage speichert Dezimalwerte als JSON-Zahlen. `interlis-db` liest
und schreibt diese Zahlen ohne Umweg über Binary64. SQL `NULL` und
leere Arrays ergeben ein fehlendes
Attribut. Ein einzelnes `NULL`-Element ergibt bei `interlis-db` eine
leere Struktur. ili2db 5.5.2 importiert dieselbe Form, bricht aber beim
eigenen Export mit `illegal value null for Entry` ab, wenn das Textfeld
`Entry` heisst. Ein optionaler Struktureinzelwert mit `ili2db.mapping=ARRAY`
verwendet dieselbe Kodierung mit höchstens einem Element. In XTF 2.4
können `LIST OF` und `BAG OF` die unterstützten Skalare direkt übertragen.
Ein gespeichertes `NULL`-Element ist in dieser direkten XTF-Form nicht
darstellbar und wird am Attribut diagnostiziert.

Referenzlisten können auf eine konkrete Klasse desselben Topics und
Baskets mit einer eigenen SQL-Tabelle zeigen. XTF 2.3 verwendet dafür eine
Struktur mit einem Referenzfeld, XTF 2.4 unterstützt auch die direkte
Referenzliste. Der Import löst Vorwärtsreferenzen am Basketende auf und
erhält Reihenfolge und Duplikate. Im GeoPackage stehen die internen
`T_Id`-Werte als Dezimalstrings im JSON-Array, in PostGIS als `bigint[]`.
`update`, `replace` und `delete` schützen Ziele, auf die weiterhin
gespeicherte Arrays zeigen.
[ili2db-Abbildung](https://github.com/claeis/ili2db/blob/4607cc3a1171ed8a885ed83076c197a12559b41f/src/ch/ehi/ili2db/fromili/FromIliRecordConverter.java#L766-L813)

In bestehenden Schemata mit `--createEnumColAsItfCode` liegen skalare
Aufzählungen als numerische Codes vor. Die Blattwerte werden in der
effektiven Baumreihenfolge ab null nummeriert. Der XTF-Transfer verwendet
weiterhin die qualifizierten XTF-Codes. Unbekannte Codes werden am
betroffenen Attribut diagnostiziert.
Bei `--createEnumTabsWithId` speichert die Fachspalte die `T_Id`
des Lookup-Eintrags. Der Transfer verwendet die in der Datenbank
gespeicherte Zuordnung für die jeweilige Aufzählungsdefinition.
Mit `--coalesceArray` verwenden auch Aufzählungsarrays diese numerischen
Kodierungen. GeoPackage speichert JSON-Zahlen, PostGIS verwendet `bigint[]`.
XTF 2.3 überträgt die Einfeldstruktur, XTF 2.4 auch die direkte Liste.
Für Aufzählungen in expandierten Strukturen gilt die XTF-Textkodierung.
Numerische Kodierung und zusätzliche Anzeigespalten für diese Felder
werden als nicht unterstützte Kombination diagnostiziert.

In bestehenden Schemata mit `--coalesceJson` speichert ili2db eine
optionale flache Struktur als JSON-Objekt. GeoPackage verwendet `TEXT`,
PostGIS `JSONB`. Der qualifizierte INTERLIS-Typ steht in `@type`. SQL
`NULL` steht für den fehlenden Strukturwert. Eine vorhandene leere
Struktur wird als Objekt mit `@type` übertragen. `LIST OF` und `BAG OF`
mit einer Obergrenze über eins liegen als JSON-Arrays vor, auch bei einem
einzigen Eintrag. Eine zusätzliche optionale Struktur liegt als inneres
JSON-Objekt vor.

<a id="ili2db-json-exportfehler"></a>

**Exportfehler bei verschachteltem JSON in ili2db 5.5.2.** ili2gpkg und
ili2pg importieren mit `--coalesceJson` auch Strukturwerte mit einem
inneren Objekt. Beim anschliessenden Export desselben Datensatzes meldet
ili2db `unexpected json token END_OBJECT; '}' expected`. Betroffen sind
GeoPackage und PostGIS mit XTF 2.3 und 2.4 FULL. Bereits eine leere
innere Struktur genügt. So sieht der gespeicherte Wert im XTF-2.4-Test aus.

```json
{
  "@type": "JsonCollections24.Data.Wrapper",
  "Detail": { "@type": "JsonCollections24.Data.Entry" }
}
```

Beim rekursiven Lesen des inneren Objekts behält
`Iox2jsonUtility.readOneObject` das alte Token des äusseren Objekts.
`interlis-db` verwendet dieselbe JSON-Abbildung und kann den betroffenen
Datensatz aus der bestehenden Datenbank exportieren. Flache JSON-Objekte
und Arrays flacher Strukturen wurden mit beiden Werkzeugen in beiden
Richtungen geprüft.
[ili2db-Quellcode](https://github.com/claeis/ili2db/blob/4607cc3a1171ed8a885ed83076c197a12559b41f/src/ch/ehi/ili2db/json/Iox2jsonUtility.java#L309-L339) ·
[Reproduktion](../development/testing.md#ili2db-json-export-reproduzieren)

In XTF 2.3 und 2.4 FULL können Klassen und Strukturen optionale und
obligatorische Strukturwerte sowie `LIST OF`- und `BAG OF`-Attribute mit einem Strukturtyp aus dem
Modell oder dem eigenen Topic enthalten. Verschachtelungen sind azyklisch.
Strukturelemente verwenden die unterstützten skalaren Attributtypen.
Im strikten `newClass`-Schema gilt `NOT NULL` für direkt gespeicherte
skalare Pflichtfelder auch in verschachtelten Strukturtabellen. Die
Rückverweise bleiben auf jeder Ebene nullable.
Bei `LIST OF` und `BAG OF` mit `RESTRICTION` verwendet das
`newClass`-Profil den Rückverweis der deklarierten Strukturhierarchie.
`T_Type` bezeichnet den konkreten Strukturtyp der gespeicherten Zeile.
Topic-lokale Strukturen können optionale `REFERENCE TO`-Attribute auf eine
Klasse desselben Topics und Baskets enthalten. Vorwärtsreferenzen werden
am Basketende aufgelöst.
Bei `REFERENCE TO … RESTRICTION` bestimmt die deklarierte Basisklasse
die Fremdschlüsselabbildung. Smart2 behält die Zielspalten aller
physischen Tabellen dieses Basistyps. Die fachliche Einschränkung prüft
der Validator.
Jeder Strukturtyp hat eine Tabelle.
Die gespeicherte `ATTRNAME`-Zuordnung benennt je Elternattribut eine
Rückverweisspalte in dieser Tabelle. Die Strukturzeilen erhalten eigene
SQL-IDs und den Basket des Wurzelobjekts. Der Rückverweis speichert die
SQL-ID des direkten Elternobjekts oder der direkten Elternstruktur.
Die `T_Ili_Tid` einer Strukturzeile bleibt beim Import `NULL`. Ein fehlender
Wert belegt keine Zeile.
`T_Seq` beginnt beim Import für jedes Elternattribut bei null und bestimmt
bei `LIST OF` und `BAG OF` die Ausgabereihenfolge.
Duplikate bleiben erhalten. Verwaiste oder mehrdeutig zugeordnete Strukturzeilen
sowie fehlende oder doppelte Positionen werden beim Export diagnostiziert.
[ili2db-Strukturabbildung](https://github.com/claeis/ili2db/blob/4607cc3a1171ed8a885ed83076c197a12559b41f/docs/ili2db.rst#strukturattribute)

Strukturvererbung mit `--noSmartMapping` speichert die skalaren Attribute
auf den Tabellen der jeweiligen Deklarationen. Alle Zeilen im Pfad teilen
`T_Id` und `T_Basket`. Die Wurzeltabelle enthält `T_Type`, `T_Seq` und die
Rückverweisspalten, auch für Attribute, die einen Untertyp deklarieren.
Der Export schreibt den konkreten Strukturtyp. Liegt dieser ausserhalb
des deklarierten Zweigs, nennt die Diagnose das Elternattribut und den
gespeicherten Typ.

XTF 2.3 und 2.4 FULL unterstützen auch binäre 1:n- und 1:1-Assoziationen
ohne eigene Attribute zwischen Klassen desselben Topics und Baskets.
Beide Rollen verwenden `--` und sind lokal und ungeordnet. Bei 1:n hat die
Rolle zur 1-Seite `{0..1}` oder `{1}`. Die Tabelle der n-Seite speichert die
Ziel-ID. Bei 1:1 hat jede Rolle `{0..1}` oder `{1}`. Die Tabelle der zweiten
deklarierten Rolle speichert die Ziel-ID der ersten Rolle. Die Rollenspalte
erlaubt unter `--sqlEnableNull` SQL `NULL`.
In strikten `newClass`-Schemata bestimmt die Kardinalität der gespeicherten
Rolle die Nullbarkeit. Der Import unterstützt Pflichtrollen einfacher lokaler
1:1- und 1:n-Assoziationen mit einer Zieltabelle, auch bei Klassen in
`newClass`-Hierarchien. Bei Vorwärtsreferenzen reserviert er pro Ziel-TID
im Basket eine SQL-ID und belegt die Rollenspalte beim Einfügen. Trifft
das Ziel ein, muss sein konkreter Typ zu allen wartenden Rollen passen.
Ein vorhandener Fremdschlüssel ist wie bei ili2db `--createFk` aufgeschoben.

Rollen dürfen auf beiden Seiten Klassen in Vererbungshierarchien deklarieren,
einschliesslich abstrakter Basisklassen. Die Rollenspalte liegt in der
Tabelle der deklarierten Eigentümerklasse. Sie speichert die gemeinsame
`T_Id` der Zielzeilen. Die Zielmetadaten benennen die Tabelle des deklarierten
Zieltyps. Im XTF erscheint die Rolle am konkreten Eigentümerobjekt und
verwendet die TID aus der Wurzeltabelle des Ziels. Eine `RESTRICTION`
am Rollenziel ändert weder den Fremdschlüssel einer eingebetteten Rolle
noch den einer eigenständigen Linktabelle.
Auch Selbstbezüge innerhalb derselben Hierarchie sind möglich.
Bei binären lokalen n:m-Assoziationen mit ungeordneten Rollen kann ein Ende
zwei getrennte Zielzweige mit `OR` deklarieren. Die Linktabelle erhält
die alternativen Fremdschlüsselspalten der physischen Zieltabellen.
Pro Linkzeile wird genau eine Spalte dieser Rolle verwendet.

`CLASSNAME` und `INHERITANCE` führen die Assoziation, `TRAFO` kennzeichnet
sie als `embedded` und `ATTRNAME` benennt Spalte und Ziel. Referenzen auf
später gelieferte Zielobjekte werden am Basketende aufgelöst. Ein fehlendes
oder für diesen Basket unauflösbares Referenzziel führt zum Rollback.
Eine leere Assoziationshülle mit Referenz wird wie die direkte Referenz
importiert. SQL `NULL` ergibt beim Export eine ausgelassene Rolle.
[ili2db-Beziehungsabbildung](https://github.com/claeis/ili2db/blob/4607cc3a1171ed8a885ed83076c197a12559b41f/docs/ili2db.rst#beziehungen)

Lokale binäre Aggregationen (`-<>`) und Kompositionen (`-<#>`) ohne eigene
Attribute verwenden dieselbe Abbildung. Die Kardinalitäten bestimmen, ob die
Rolle auf der Eigentümertabelle oder in einer Beziehungstabelle gespeichert
wird. Die Rollen dürfen auch Klassen in Vererbungshierarchien deklarieren.

Bei n:m-Assoziationen speichert eine Beziehungstabelle beide Rollenspalten
in Deklarationsreihenfolge und danach die eigenen skalaren Attribute. Ihre
Zeilen erhalten eine gemeinsame SQL-ID und den Basket. `TRAFO` enthält
`newClass`, `tableKind` enthält `ASSOCIATION` und `ATTRNAME` sowie `foreignKey`
beschreiben beide Ziele. XTF überträgt die Links ohne TID. Beide Ziele werden
am Basketende aufgelöst.

Bei diesen eigenständigen n:m-Assoziationen darf jedes Rollenende eine
Klasse in einer Hierarchie deklarieren. Die Metadaten bezeichnen die
physische Tabelle des deklarierten Typs. Die Rollen speichern die gemeinsame
`T_Id` der konkreten Objekte. Ihre XTF-Referenzen verwenden jeweils die TID
der Wurzeltabelle. Beide Ziele müssen zum deklarierten Typzweig und zum
Basket des Links gehören. Eigene skalare Attribute bleiben in der
Linktabelle.

Eine eigenständige n:m-Assoziation kann von einer abstrakten
Basisassoziation mit denselben beiden `--`-Rollen erben. Das Profil unterstützt
eine direkte konkrete Erweiterung mit skalaren Attributen. Die
Wurzeltabelle speichert beide Rollenschlüssel, die deklarierten Attribute
der Basis und `T_Type`. Die Erweiterungstabelle speichert ihre eigenen
Attribute unter derselben `T_Id` und im selben Basket. Im XTF erscheint
der konkrete Linktyp ohne TID.

Eigenständige Assoziationen können auch drei lokale, ungeordnete `--`-Rollen
mit einer Obergrenze grösser als eins haben. Ihre Linktabelle speichert
die drei Rollen in Deklarationsreihenfolge und danach die eigenen skalaren
Attribute. Im XTF erscheint ein anonymer Link mit allen drei Referenzen.
Für jedes Rollenende gilt dieselbe Typ- und Basketprüfung wie bei n:m.

Beim XTF-2.3-Import mit ili2gpkg 5.5.2 wurden identische Zielpaare zweier
anonymer Assoziationen derselben Beziehungstabelle zugeordnet. Die
Mischfixtures verwenden für diese Assoziationen unterschiedliche Zieltypen.

Bei eigenständigen binären n:m-Assoziationen mit lokalen `--`-Rollen sind
eine oder beide Rollen mit `ORDERED` zulässig. Jede geordnete Rolle erhält
in der Beziehungstabelle eine Spalte `<Rollenspalte>_T_Seq`. XTF 2.3
überträgt die Position als `ORDER_POS`, XTF 2.4 als `ili:order_pos`.
Der Adapter erhält explizite positive 64-Bit-Positionen und übersetzt eine
fehlende Position in SQL `NULL`. ili2db 5.5.2 nutzt dasselbe SQL-Schema,
ignoriert Positionswerte aber beim Import und lässt sie beim Export weg.
[Referenzschema](https://github.com/claeis/ili2db/blob/4607cc3a1171ed8a885ed83076c197a12559b41f/src/ch/ehi/ili2db/fromili/FromIliRecordConverter.java#L496-L506)

Bei eingebetteten 1:n-Assoziationen ohne eigene Attribute darf die
lokale n-Rolle `ORDERED` sein. Im XTF trägt die übertragene Gegenrolle
die Position. Die physische Tabelle der n-Seite speichert sie in
`<Rollenspalte>_T_Seq` neben dem Fremdschlüssel. Fehlt die Rolle, sind
Fremdschlüssel und Position SQL `NULL`.

1:n- und 1:1-Assoziationen mit eigenen skalaren Attributen verwenden ebenfalls
eine Beziehungstabelle. Im XTF erscheint die Rolle am Objekt der n-Seite
bzw. der zweiten deklarierten Rolle. Eine
Linkzeile mit Attributwerten ergibt eine eingebettete Assoziation. Sind alle
eigenen Spalten `NULL`, erscheint eine direkte Referenz. Ohne Linkzeile
entfällt die Rolle. Mehrere Linkzeilen zum selben Objekt der n-Seite werden
beim Export an der Rolle diagnostiziert.
Die 1-Seite darf eine Klasse in einer Vererbungshierarchie deklarieren.
Der Export liest ihre TID aus der Wurzeltabelle. Die deklarierte Klasse
auf der speichernden Seite darf auch eine konkrete Blattklasse sein. In diesem Fall
bezieht sich der Rückverweis auf deren Tabellenfragment. Bei einem
deklarierten Eigentümer mit Untertypen fragt der Referenzexport zusätzliche
Rollenspalten ab, die das Referenzschema nicht enthält.
[Referenzabfrage](https://github.com/claeis/ili2db/blob/4607cc3a1171ed8a885ed83076c197a12559b41f/src/ch/ehi/ili2db/toxtf/TransferToXtf.java#L1631-L1664)

Bei einer eingebetteten 1:n-Assoziation mit eigenen Attributen und einer
lokalen `ORDERED`-Rolle an der n-Seite speichert die Beziehungstabelle die
Position in `<Eigentümer-Rollenspalte>_T_Seq`. Im XTF trägt die Gegenrolle
die Position sowohl bei einer direkten Referenz als auch bei einer
Referenz mit eingebetteten Attributwerten.

XTF 2.3 und 2.4 FULL unterstützen Klassenvererbung mit `--noSmartMapping`
innerhalb eines Topics und von einem Basistopic zu einem abgeleiteten Topic.
Abstrakte Basisklassen können ihre Attribute an
mehrere konkrete Nachfolger vererben. Pro Klasse entsteht eine Tabelle mit deren deklarierten
Feldern. Ein Objekt belegt je eine Zeile in der Wurzeltabelle und in jeder
Tabelle seines Vererbungspfads. Diese Zeilen teilen `T_Id` und `T_Basket`.
Nur die Wurzel speichert die TID und den SQL-Tabellennamen der konkreten
Klasse als `T_Type`. Beim Export entsteht daraus ein XTF-Objekt mit allen
Feldern seines Pfads. Fehlende, unerwartete oder basketfremde Teilzeilen
und unbekannte oder abstrakte Typen werden diagnostiziert.
[ili2db-Vererbungsabbildung](https://github.com/claeis/ili2db/blob/4607cc3a1171ed8a885ed83076c197a12559b41f/docs/ili2db.rst#vererbung)

Geometrien behalten die Zuordnung zur deklarierenden Klasse. Ein Objekt
kann geerbte und eigene XY- sowie XYZ-Geometrien enthalten. Bei separater
Ablage gehört jede Hilfszeile zur Tabellenzeile der deklarierenden Klasse.
Fehlende Hilfszeilen oder abweichende Baskets verhindern den Export und
eine betroffene Datensatzlöschung. GeoPackage registriert die physischen
Geometrietabellen als `features` und übrige Tabellen als `attributes`.

ili2db 5.5.2 bindet beim Export aus einer Klassenhierarchie die
Geometrie-Hilfstabelle einer Unterklasse doppelt ein. Der Fehler tritt
auch in einem vom Referenzwerkzeug erzeugten und befüllten Schema auf,
bei ili2gpkg im Standardlayout und bei ili2pg mit `--oneGeomPerTable`.
Für diese Ablage funktionieren der Referenzimport und der Export mit
`interlis-db`. Für den Wechsel in beiden Richtungen eignen sich das
PostGIS-Standardlayout und das optionale GeoPackage-Profil mit mehreren
Geometriespalten in einer Tabelle.

Optionale Struktureinzelwerte, `LIST OF` und `BAG OF` können in einer
Klassenhierarchie deklariert und geerbt werden. Die Rückverweisspalte
und ihre Metadaten beziehen sich auf die Tabelle der ursprünglichen
Deklaration. Für ein neues Attribut einer Unterklasse ist dies deren eigene
Tabelle. Die referenzierte Zeile muss zum gespeicherten Typ und Basket des
Elternobjekts gehören.

Optionale lokale `REFERENCE TO`-Attribute in topic-lokalen Strukturen und
in XTF 2.4 auch in Klassen können auf Klassenhierarchien desselben
Topics und Baskets zielen. Die Referenzspalte liegt in der Quelltabelle
und speichert die gemeinsame `T_Id` des Zielobjekts. `ATTRNAME.Target` und
die Fremdschlüsselmetadaten nennen die physische Tabelle des deklarierten
Zieltyps. Die XTF-TID stammt aus der Wurzeltabelle. Der tatsächliche
Zieltyp muss dem deklarierten Typ entsprechen oder von ihm abgeleitet
sein. Vorwärtsreferenzen werden am Basketende aufgelöst.
[ili2db-Referenzabbildung](https://github.com/claeis/ili2db/blob/4607cc3a1171ed8a885ed83076c197a12559b41f/src/ch/ehi/ili2db/fromili/FromIliRecordConverter.java#L1040-L1068)

Bei einer Vererbungshierarchie auf der Quellseite liegt die Referenzspalte
in der Tabelle der Deklaration. Das gilt für Strukturattribute in beiden
Sprachversionen und für Klassenattribute in 2.4. Eine Vorwärtsreferenz
aktualisiert die Zeile dieser Tabelle. Im XTF erscheint die Referenz am
konkreten Strukturwert oder Klassenobjekt.

Optionale `REFERENCE TO (EXTERNAL)`-Attribute unterstützen Ziele in anderen
Baskets desselben Topics und Datensatzes. In 2.3 liegt das Attribut in einer
Struktur, in 2.4 auch direkt in einer Klasse. Die Spalte speichert weiterhin
die numerische `T_Id` des Zielobjekts. Beim Import werden noch offene Ziele
nach dem bestätigten Transferende aufgelöst. In XTF 2.3 gibt der Export für
ein Ziel in einem anderen Basket dessen BID aus. Der Import akzeptiert die
von ili2db 5.5.2 ausgegebene Form mit `REF` allein und prüft eine mitgelieferte
BID gegen den tatsächlichen Zielbasket. In XTF 2.4 genügt `ili:ref`.

Fehlt ein `EXTERNAL`-Attributziel im aktuellen Transfer, sucht der Import
im selben Schema nach einem eindeutigen Treffer im deklarierten Typzweig.
Eine mitgelieferte Ziel-BID schränkt die Suche ein. Der Export liefert
die Baskets des Quelldatensatzes und prüft die Eindeutigkeit externer
Referenzen auf separat gespeicherte Ziele. Der Zieldatensatz muss für
den Wiederimport vorliegen.

Dieselbe Auflösung gilt für eingebettete 1:n- und 1:1-Assoziationen ohne
eigene Attribute im selben Topic. Die gespeicherte Rolle ist `EXTERNAL`.
Die Rollenspalte liegt wie bei lokalen Zielen in der Eigentümertabelle.
Das Ziel darf in einem anderen Datensatz desselben Schemas liegen.
Bei einem eigenständigen n:m-Link darf eine der beiden Rollen `EXTERNAL`
sein, auch auf ein Ziel in einem anderen Datensatz desselben Schemas.
Die andere Rolle und die Linkzeile bleiben im selben Basket verankert.
Bei einer eingebetteten 1:n-Assoziation mit eigenen skalaren Attributen
speichert die Linktabelle die `EXTERNAL`-Zielrolle. Die Eigentümerrolle
und der Basket der Linkzeile gehören weiterhin zum Eigentümerobjekt.
Das `EXTERNAL`-Ziel darf aus einem anderen Datensatz desselben Schemas kommen.

Ein `EXTERNAL`-Referenzattribut darf auf eine Klasse eines Topics zielen,
das im gleichen Modell direkt mit `DEPENDS ON` deklariert ist. Der physische
Fremdschlüssel und die Auflösung am Transferende bleiben gleich.
Das gilt auch für die `EXTERNAL`-Rolle einer eingebetteten 1:n-Assoziation
mit lokaler n-Seite. Eigene skalare Attribute bleiben in der Linktabelle.
Eigenständige n:m-Links können ein Ziel im abhängigen Topic ansprechen.
Liegt bei einer 1:n-Assoziation die n-Seite im fremden Topic, erscheint sie
im XTF als eigenständiger Link und die Linkzeile bleibt im deklarierenden Topic.

Flache Klassen können `OID AS INTERLIS.UUIDOID` deklarieren. Für das
Referenzprofil speichert `T_Ili_Tid` den UUID-Wert in PostGIS als `uuid`
und in GeoPackage als `TEXT(36)`. Für die Schemaerzeugung auf PostGIS
muss `uuid_generate_v4()` aus `uuid-ossp` im Suchpfad verfügbar sein.
Die Spaltenmetadaten führen die
`oidDomain`. Import und Export verwenden die kanonische UUID-Schreibweise
mit Kleinbuchstaben. XTF 2.3 FULL verwendet die UUID als TID ohne
OIDSPACE-Präfix. Die UUID-Regel kann aus dem Topic oder der Basisklasse
stammen. Die Wurzeltabelle speichert die OID und die Fragmente teilen
dieselbe interne `T_Id`. Ein Topic mit `BASKET OID AS INTERLIS.UUIDOID`
speichert die gelieferte BID in `T_ILI2DB_BASKET.T_Ili_Tid`.
Lokale und `EXTERNAL`-Referenzen auf UUID-Objekte verwenden dieselbe
normalisierte UUID bei der Auflösung und behalten die numerischen Fremdschlüssel.
Eigenständige lokale n:m-Assoziationen können eine UUID-OID besitzen.
Die Beziehungstabelle führt dafür zusätzlich `T_Ili_Tid` und `oidDomain`.
Flache Klassen können auch `OID AS INTERLIS.STANDARDOID` deklarieren.
Beide Backends speichern die OID als Text in `T_Ili_Tid`. Referenzen
behalten ihre numerischen Fremdschlüssel. Transfer und Suche verwenden
die OID in der gespeicherten Schreibweise.

Modellierte `OID TEXT`-Domänen können Basket- und Objektkennungen
festlegen. Das gilt auch für geerbte Klassen-OIDs und eigene Kennungen
lokaler n:m-Assoziationen. Beide Backends speichern
die Kennungen in `T_Ili_Tid` als Text und führen die deklarierte
Objekt-OID-Domäne in den Spaltenmetadaten. Skalare Attribute vom Typ
`OID TEXT` werden als Textspalten mit `typeKind=OID` abgebildet.
Import, Export und Referenzauflösung erhalten die Gross- und
Kleinschreibung. Klassen mit explizitem `NO OID` behalten ihre
Transferkennung in `T_Ili_Tid`.

Zum weiteren Ausbau gehören weitere Assoziationsformen mit Hierarchie-Enden
sowie weitere `EXTENDED`-Abbildungen. Weitere Grenzen sind andere OID-Regeln, eigene Linienformen,
inkrementelle XTF-Transfers. Der gespeicherte Modelltext
ist auf eine Million Zeichen begrenzt.

Der Adapter unterscheidet SQL `NULL` und leeren Text. Die Referenzwerkzeuge
5.5.2 können leeren Text beim Import zu SQL `NULL` normalisieren und im
XTF-Export auslassen. Beschädigte und nicht
unterstützte Abbildungen werden diagnostiziert. Modellconstraints
werden vom separaten Validator geprüft.

Die [Interoperabilitätsprüfung](../development/testing.md#ili2db-interoperabilität)
verwendet die Originalwerkzeuge in beiden Richtungen.
