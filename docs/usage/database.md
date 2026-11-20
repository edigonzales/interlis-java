# Datenaustausch mit ili2db-Schemata

Das JVM-Kommando `interlis-db` erstellt ili2db-kompatible Schemata in
GeoPackage oder PostgreSQL/PostGIS. Es importiert XTF-FULL-Transfers in einen
neuen benannten Datensatz und exportiert benannte Datensätze. Das
Referenzprofil für die Schemaerzeugung verwendet `ili2gpkg` und `ili2pg`
**5.5.2**. Für den bisherigen Umfang mit Punkten unterstützt der Transfer
auch bestehende 5.5.1-Schemata. Er kompiliert das gespeicherte Modell und
verwendet dessen gespeicherte SQL-Zuordnungen.
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

Das Kommando liest ein eigenständiges Modell aus einer lokalen UTF-8-Datei.
Vor dem ersten Schreibzugriff prüft es das Modell und bestimmt die
Tabellen, Spalten und Metadaten gemäss der Standard-Namensvergabe von 5.5.2.
PostgreSQL benötigt eine vorhandene PostGIS-Erweiterung und einen
kleingeschriebenen Schemanamen, den ili2pg unquotiert verwenden kann.
Das neue Schema entsteht in einer Transaktion. GeoPackage entsteht als temporäre Datei
neben dem Ziel und wird nach erfolgreichem Abschluss per Hardlink
veröffentlicht. Ein vorhandener Schemaname oder Zielpfad führt zu einem
Fehler. Für GeoPackage muss das Ziel-Dateisystem Hardlinks unterstützen.

## Aufruf

```sh
./gradlew :interlis-db:installDist
interlis-db/build/install/interlis-db/bin/interlis-db export \
  --gpkg data.gpkg --model Example --dataset original original.xtf
interlis-db/build/install/interlis-db/bin/interlis-db import \
  --pg-url jdbc:postgresql://localhost:5432/geodata --schema example --user editor \
  --model Example --dataset addition addition.xtf
```

Das PostgreSQL-Passwort wird bei Bedarf aus `INTERLIS_DB_PASSWORD` gelesen.
Ein Import legt den angegebenen Datensatz an. Einen bereits vorhandenen
Datensatznamen meldet er als Fehler. Ein erfolgreicher Export ersetzt die
ausdrücklich gewählte Zieldatei atomar. Ein abgebrochener Export belässt eine
bisherige Zieldatei. Ein Import schreibt Objekte, Baskets und den Datensatz
in einer Transaktion und bestätigt das XTF-Quellenende vor dem Commit.
Nach dem Rollback lässt sich der Import mit demselben Datensatznamen
wiederholen.
PostgreSQL-Sequenznummern können nach einem Rollback Lücken aufweisen.

## Referenzprofil

Für die Erzeugung mit dem Originalwerkzeug verwende
`--noSmartMapping --createBasketCol --createTidCol --sqlEnableNull
--defaultSrsAuth EPSG --defaultSrsCode 2056` an. Verwende beim Referenzimport
`--importTid --importBid` und beim Referenzexport `--exportTid`. Das Kommando
benötigt die gespeicherten BID/TID und bewahrt sie beim Werkzeugwechsel.
Ein Datensatz enthält mindestens einen Basket. Baskets ohne Objekte sind
zulässig.

Unterstützt sind flache Klassen eines eigenständigen Modells in
INTERLIS 2.3 und 2.4 mit Text, Boolean, 32-Bit-Ganzzahlen und Aufzählungen als
XTF-Code. Je Klasse ist eine optionale Geometriespalte in EPSG:2056 zulässig.
Sie speichert einen 2D-Punkt, eine zweidimensionale `POLYLINE` oder eine
zweidimensionale Fläche (`SURFACE` oder `AREA`) mit einem Aussenring und
optionalen Innenringen. Linien und Flächenränder unterstützen
`WITH (STRAIGHTS)`, `WITH (ARCS)` und
`WITH (STRAIGHTS, ARCS)`. Wie bei ili2db 5.5.2 liegen Linien in
`COMPOUNDCURVE`-Spalten und Flächen in `CURVEPOLYGON`-Spalten.
Die Spaltenmetadaten weisen den Modelltyp `SURFACE` oder `AREA` aus.
Jeder Ring wird als `COMPOUNDCURVE` geschrieben. Aufeinanderfolgende
Geraden bilden eine `LINESTRING`-Komponente, aufeinanderfolgende Bögen
eine `CIRCULARSTRING`-Komponente.

In XTF 2.4 FULL speichern die folgenden Spaltentypen alle Teile einer
Geometrie in Lieferreihenfolge und derselben Objektzeile. `typeKind`
weist den INTERLIS-Modelltyp aus.

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
Geometriekatalog und das passende Binärformat. GeoPackage-Flächen,
gebogene Linien und Geometriesammlungen verwenden den Standardheader ohne
optionale XY-Ausdehnung.
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
Punkte aus der gespeicherten Geometrie. Der XTF-2.4-Codec verwendet die
normgerechte Reihenfolge mit Endpunkt vor Zwischenpunkt. ili2gpkg 5.5.2
schreibt beim Export die umgekehrte Reihenfolge. Der Werkzeugwechsel für
diese Referenzausgabe erfolgt in der gemeinsamen Datenbank.
[INTERLIS 2.4, Anhang C](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=133)

In XTF 2.3 und 2.4 FULL kann eine Klasse einen optionalen Struktureinzelwert
und `LIST OF`-Attribute mit einem konkreten, flachen Strukturtyp aus dem
Modell oder dem eigenen Topic enthalten. Strukturelemente verwenden die
unterstützten skalaren Attributtypen. Jeder Strukturtyp hat eine Tabelle.
Die gespeicherte `ATTRNAME`-Zuordnung benennt je Elternattribut eine
Rückverweisspalte in dieser Tabelle. Die Strukturzeilen erhalten eigene
SQL-IDs und den Basket des Elternobjekts. Ihre `T_Ili_Tid` bleibt beim
Import `NULL`. Ein fehlender Wert belegt keine Zeile. `T_Seq` beginnt beim
Import bei null und bestimmt beim Export die Listenreihenfolge. Gleiche
Listenelemente bleiben erhalten. Verwaiste oder mehrdeutig zugeordnete
Strukturzeilen und fehlende oder doppelte Listenpositionen werden beim
Export diagnostiziert.
[ili2db-Strukturabbildung](https://github.com/claeis/ili2db/blob/4607cc3a1171ed8a885ed83076c197a12559b41f/docs/ili2db.rst#strukturattribute)

XTF 2.3 und 2.4 FULL unterstützen auch binäre 1:n- und 1:1-Assoziationen
ohne eigene Attribute zwischen konkreten Klassen desselben Topics und Baskets.
Beide Rollen verwenden `--` und sind lokal und ungeordnet. Bei 1:n hat die
Rolle zur 1-Seite `{0..1}` oder `{1}`. Die Tabelle der n-Seite speichert die
Ziel-ID. Bei 1:1 hat jede Rolle `{0..1}` oder `{1}`. Die Tabelle der zweiten
deklarierten Rolle speichert die Ziel-ID der ersten Rolle. Die Rollenspalte
erlaubt unter `--sqlEnableNull` SQL `NULL`.
`CLASSNAME` und `INHERITANCE` führen die Assoziation, `TRAFO` kennzeichnet
sie als `embedded` und `ATTRNAME` benennt Spalte und Ziel. Referenzen auf
später gelieferte Zielobjekte werden am Basketende aufgelöst. Ein fehlendes
oder für diesen Basket unauflösbares Referenzziel führt zum Rollback.
Eine leere Assoziationshülle mit Referenz wird wie die direkte Referenz
importiert. SQL `NULL` ergibt beim Export eine ausgelassene Rolle.
[ili2db-Beziehungsabbildung](https://github.com/claeis/ili2db/blob/4607cc3a1171ed8a885ed83076c197a12559b41f/docs/ili2db.rst#beziehungen)

Bei n:m-Assoziationen speichert eine Beziehungstabelle beide Rollenspalten
in Deklarationsreihenfolge und danach die eigenen skalaren Attribute. Ihre
Zeilen erhalten eine gemeinsame SQL-ID und den Basket. `TRAFO` enthält
`newClass`, `tableKind` enthält `ASSOCIATION` und `ATTRNAME` sowie `foreignKey`
beschreiben beide Ziele. XTF überträgt die Links ohne TID. Beide Ziele werden
am Basketende aufgelöst.

Beim XTF-2.3-Import mit ili2gpkg 5.5.2 wurden identische Zielpaare zweier
anonymer Assoziationen derselben Beziehungstabelle zugeordnet. Die
Mischfixtures verwenden für diese Assoziationen unterschiedliche Zieltypen.

1:n-Assoziationen mit eigenen skalaren Attributen verwenden ebenfalls eine
Beziehungstabelle. Im XTF bleibt die Rolle am Objekt der n-Seite. Eine
Linkzeile mit Attributwerten ergibt eine eingebettete Assoziation. Sind alle
eigenen Spalten `NULL`, erscheint eine direkte Referenz. Ohne Linkzeile
entfällt die Rolle. Mehrere Linkzeilen zum selben Objekt der n-Seite werden
beim Export an der Rolle diagnostiziert.

In 2.4 kann eine Klasse optionale
`REFERENCE TO`-Attribute auf eine Klasse desselben Baskets haben. Baskets
und Objekte verwenden Transferkennungen. Grenzen des Profils sind unter
anderem Vererbung, verschachtelte Strukturen, stabile OIDs, eigene
Linienformen, GeoPackage-RTree beim Schreiben und Updates. Der gespeicherte
Modelltext ist auf eine Million Zeichen begrenzt.

Der Adapter unterscheidet SQL `NULL` und leeren Text. Die Referenzwerkzeuge
5.5.2 können leeren Text beim Import zu SQL `NULL` normalisieren und im
XTF-Export auslassen. Beschädigte und nicht
unterstützte Abbildungen werden diagnostiziert. Modellconstraints
werden vom separaten Validator geprüft.

Der CLI-Vertrag erhält den zusätzlichen Befehl `schemaimport`. Die
[Interoperabilitätsprüfung](../development/testing.md#ili2db-interoperabilität)
verwendet die Originalwerkzeuge in beiden Richtungen.
