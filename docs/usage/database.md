# Datenaustausch mit ili2db-Schemata

Das JVM-Kommando `interlis-db` erstellt ili2db-kompatible Schemata in
GeoPackage oder PostgreSQL/PostGIS. Es importiert XTF-FULL-Transfers in einen
neuen benannten Datensatz und exportiert benannte Datensätze. Das
Referenzprofil für die Schemaerzeugung verwendet `ili2gpkg` und `ili2pg`
**5.5.2**. Der Transferpfad unterstützt im selben Profil auch bestehende
5.5.1-Schemata. Er kompiliert das gespeicherte Modell und verwendet dessen
gespeicherte SQL-Zuordnungen.
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
INTERLIS 2.3 und 2.4 mit Text, Boolean, 32-Bit-Ganzzahlen, Aufzählungen als
XTF-Code und höchstens einem optionalen 2D-Punkt in EPSG:2056 je Klasse.
In 2.4 kann eine Klasse optionale
`REFERENCE TO`-Attribute auf eine Klasse desselben Baskets haben. Baskets
und Objekte verwenden Transferkennungen. Grenzen des Profils sind unter
anderem Vererbung, Strukturen, Assoziationen, stabile OIDs, Kurven,
GeoPackage-RTree beim Schreiben und Updates. Der gespeicherte
Modelltext ist auf eine Million Zeichen begrenzt.

Der Adapter unterscheidet SQL `NULL` und leeren Text. Die Referenzwerkzeuge
5.5.1 können leeren Text beim Import zu SQL `NULL` normalisieren und im
XTF-Export auslassen. Beschädigte und nicht
unterstützte Abbildungen werden diagnostiziert. Modellconstraints
werden vom separaten Validator geprüft.

Der CLI-Vertrag erhält den zusätzlichen Befehl `schemaimport`. Die
[Interoperabilitätsprüfung](../development/testing.md#ili2db-interoperabilität)
verwendet die Originalwerkzeuge in beiden Richtungen.
