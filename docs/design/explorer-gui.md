# Gestaltungsgrundlagen für den INTERLIS Object Explorer

## 1 Ziel

Der Desktop-Explorer macht INTERLIS-Daten anhand ihrer fachlichen Modellstruktur
zugänglich. Die Regeln gelten für die künftige Oberfläche.

## 2 Navigation

Die Hauptansicht verbindet die Modellnavigation mit dem Objektinspektor.
Unter einem Topic stehen die Klassen und die Assoziationstypen mit
eigenständigen Vorkommen im geöffneten Transfer. Beide führen zur Objektliste.
Bei Assoziationen ohne TID oder OID zeigt die Zeile die Transferposition sowie
Rollennamen und Zielkennungen in effektiver Modellreihenfolge.
Physische Datenbanktabellen und Spalten bestimmen diese Navigation nicht.
Beim Öffnen eines Transfers zeigt der Modellbaum die Topics der importierten
Baskets und ihre Modelle. Leere Baskets zählen mit. Die Klassen und
Assoziationen eines sichtbaren Topics folgen der Modellreihenfolge. **Show all models** neben
**Model** zeigt zusätzlich die übrigen Modelle und Topics. Beim Umschalten
bleiben sichtbare aufgeklappte Knoten und die sichtbare Auswahl erhalten.

## 3 Zusätzliche Ansichten

Karte, Tabelle und Abfragen sind bei Bedarf zugängliche Ansichten. Der
Abfrage-Tab bietet IQL für modellbasierte Abfragen und DuckDB-SQL für
direkte SQL-Abfragen. Editor, Moduswahl, Ausführen, Abbrechen und
Ergebnistabelle genügen. Für IQL kommen Vorschläge aus dem geprüften
Modell, für SQL aus dem Katalog des geöffneten Datensatzes.

## 4 Visuelle Gestaltung

Weissraum, Ausrichtung und Einrückung machen Zusammenhänge sichtbar. Die
Oberfläche hat wenige dauerhaft sichtbare Bedienelemente. Trennlinien dienen
der Orientierung. Dekorative Farben, Symbole und mittig gesetzte Trennpunkte
entfallen. Die bestehende Compiler-GUI dient höchstens als technische
Referenz.

## 5 Objektinspektor

### 5.1 Effektive Attributreihenfolge

Der Inspektor verwendet `ObjectDescriptor.slots()` des jeweiligen Typs. Die
`TransferSlot`-Einträge liegen dort bereits in effektiver Reihenfolge vor.
`TransferSlot.member()` liefert die effektive Position und die Herkunft.
Die Reihenfolge aus JSON, SQL oder XTF und eine Sortierung nach Name oder
Typ haben darauf keinen Einfluss.

### 5.2 Vererbung

Geerbte Attribute erscheinen an ihrer effektiven Position in der konkreten
Klasse. Ein mit `EXTENDED` eingeschränktes Attribut erscheint einmal mit seinem
effektiv geltenden Typ. Seine Herkunft kann bei Bedarf eingesehen werden.

### 5.3 Einzelobjektprüfung

**Validate object** steht am Titel des ausgewählten Objekts und im
Kontextmenü seiner Zeile. Ein Ergebnisdialog zeigt die lokalen Befunde und
nennt den geprüften Umfang. Die Objektbezeichnung übernimmt die Rollen und
Zielkennungen aus der Liste.

## 6 Komplexe Werte und Strukturen

### 6.1 Strukturen an ihrer Attributposition

Beim Aufklappen erscheinen die enthaltenen Werte direkt unter dem
Strukturattribut. Nachfolgende Attribute behalten ihre Modellposition.

### 6.2 Typografie und Ausrichtung

Attributnamen und Werte haben auf allen Ebenen dieselbe Schriftgrösse.
Objekttitel und Abschnittsüberschriften dürfen sich typografisch abheben.
Einheitliche Ausrichtung, einfache Einrückungen und angemessener Weissraum
verdeutlichen die Hierarchie. Verschachtelte umrandete Panels entfallen.

### 6.3 LIST und BAG

Bei `LIST OF` bleibt die fachliche Reihenfolge der Einträge sichtbar.
`BAG OF` zeigt die Einträge ohne Rang oder Ordnungsnummer. Duplikate bleiben
in beiden Fällen sichtbar.

### 6.4 Beliebig verschachtelte Strukturen

Dasselbe Aufklappprinzip gilt auf jeder Tiefe für einzelne `STRUCTURE`-Werte
ebenso wie für Einträge in `LIST OF` und `BAG OF`. Jeder Eintrag kann wiederum
einzelne oder mehrwertige Strukturen sowie einfache Werte, Referenzen und
Geometrien enthalten. Die Attribute erscheinen in der effektiven Reihenfolge
des konkreten Strukturtyps des jeweiligen Werts. Massgeblich sind dessen
`ObjectDescriptor` und `TransferSlot`-Einträge.

Strukturwerte bleiben Teil ihres übergeordneten Objekts. Eine Darstellung als
eigenständiges INTERLIS-Objekt oder als Beziehung setzt einen fachlichen
Grund voraus. Wie sehr tiefe Verschachtelungen bedient werden, bleibt offen.

### 6.5 Geometrien innerhalb von Strukturen

Geometrieattribute erscheinen im Objekt und in jeder Struktur an ihrer
effektiven Attributposition. Der tatsächliche INTERLIS-Geometrietyp bleibt
sichtbar, etwa `COORD`, `POLYLINE`, `SURFACE` oder `AREA`.

Sobald Kartenunterstützung vorhanden ist, kann jede Geometrie einzeln für die
Karte ausgewählt werden. Das gilt auch für mehrere Geometrieattribute im
selben Objekt oder Strukturwert. Bei einer Sammlung bleibt die Auswahl dem
konkreten Eintrag und dessen Attribut zugeordnet. Das gilt auch für
`BAG OF`, dessen Einträge keine fachliche Reihenfolge haben.

Ein gemeinsamer, zurückhaltender Zugang zur Kartenansicht genügt. Eingebettete
Karten und automatische Miniaturkarten entfallen. Die Karte zeigt eine
zusätzliche Sicht auf den Geometriewert, der Teil seines fachlichen
Strukturwerts oder Objekts bleibt. Darstellung und räumliche Abfragen
können dieselbe native DuckDB-Geometrie verwenden. Kreisbögen werden dort
mit einer modellabhängigen Toleranz segmentiert. Die gespeicherten
INTERLIS-Werte und der XTF-Export behalten ihre vollständige Geometriesemantik.

### 6.6 Einheitliches Darstellungsprinzip

**Ein Attribut steht an seiner Modellposition. Sein Wert wird entsprechend
dem tatsächlichen INTERLIS-Typ dargestellt. Komplexe Werte können am selben
Ort aufgeklappt oder gezielt untersucht werden.**

Dieses Prinzip gilt auf jeder Tiefe für einfache, strukturierte und
geometrische Werte. Eigene GUI-Komponenten oder Architekturabstraktionen pro
INTERLIS-Konstrukt setzen einen nachgewiesenen Implementierungsbedarf voraus.

## 7 Assoziationen und Navigation

`REFERENCE TO` steht an der effektiven Attributposition und führt zum
referenzierten Objekt. Eigenständige Assoziationen stehen mit ihren
fachlichen Rollennamen im Abschnitt **Beziehungen** nach den Attributen.
Auch bei eingebetteter XTF-Darstellung geben Modell- und Transferinformationen
den Ausschlag. Dieselbe Referenz erscheint einmal.

Die Navigation unterstützt eingehende Beziehungen und die Rückkehr zum
Ausgangsobjekt. Eigene Attribute einer Assoziation sind im Kontext der
Beziehung zugänglich. Komplexe oder mehrstellige Assoziationsinstanzen
können selbst navigierbar sein. Fachlich definierte Rollenreihenfolgen bleiben
erhalten.

Pfeil und Zielkennung auflösbarer Referenzen bilden gemeinsam einen
unterstrichenen Link. Das gilt für die Objektliste und den Inspektor. Der
verbleibende Teil einer Objektzeile dient der Auswahl der Ausgangsinstanz.
**Go to target** im Kontextmenü bietet denselben Sprung per Tastatur.

## 8 Offene Gestaltungsfragen

- Wie werden eingehende und ausgehende Beziehungen ohne gemeinsame
  Modellreihenfolge angeordnet?
- Wie werden mehrstellige Assoziationen mit eigenen Attributen dargestellt?
- Ab welcher Sammlungsgrösse ist eine Tabelle besser lesbar? Wie wird die
  Bedienung bei sehr tiefen Strukturen gestaltet?
- Wie bleibt die Navigation bei vielen Objekten und Beziehungen übersichtlich?
- Welche zusätzlichen Modellinformationen erscheinen standardmässig?

## 9 Textuelles Referenzbeispiel

Die konkrete Klasse `Schulhaus` erbt `Bezeichnung`. Ihre effektive
Attributreihenfolge lautet `Bezeichnung`, `Standort`, `Erschliessung`,
`Baujahr`. `Erschliessung` ist ein Strukturwert mit einem `Haupteingang`
vom Typ `Eingang` und einer `LIST OF Zugang`. Der Abschnitt
**Beziehungen** zeigt die Assoziation `Eigentum` mit ihrem Attribut `Seit`.

```text
Schulhaus

Bezeichnung     Schulhaus Aare
Standort        COORD (2 600 100, 1 200 100)
Erschliessung   Erschliessungsdaten (aufgeklappt)
  Haupteingang  Eingang (aufgeklappt)
    Name        Haupteingang
    Lage        COORD (2 600 110, 1 200 105)
  Zugaenge      LIST OF Zugang (2 Einträge, aufgeklappt)
    1
      Name      Zufahrt Nord
      Verlauf   POLYLINE
    2
      Name      Fussweg Süd
      Verlauf   POLYLINE
Baujahr         1978

Beziehungen
Eigentum
  Eigentuemer   Gemeinde Aare
  Seit          1982
```

Die Einrückung zeigt die Zugehörigkeit bei gleicher Schriftgrösse. Die
beiden `Verlauf`-Werte sind über ihren jeweiligen Listeneintrag einzeln
auswählbar, ebenso `Lage` am Haupteingang und `Standort` am Schulhaus.
