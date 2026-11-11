# Transfer als Java-Bibliothek

`interlis-transfer` projiziert einen unveränderlichen `CompiledSchema` in einen
`TransferPlan`. `interlis-xtf23` und `interlis-xtf24` bieten objektweise Reader und
Writer für die jeweiligen FULL-Profile. Die Laufzeitclosure eines XML-Adapters
enthält nur das Modell, den Transferkern und den Adapter sowie JDK-Module.
Compiler, LSP und Browser nehmen den XML-Adapter über diese Ergänzung nicht auf.

## XTF 2.4 verwenden

Das Maven-Artefakt heisst `guru.interlis:interlis-xtf24`, das JPMS-Modul
`guru.interlis.xtf24`. Es exportiert `guru.interlis.xtf24` und macht den
Transferkern transitiv lesbar. Der Compiler wird nur zum Erstellen des Schemas
benötigt. Die [SDK-Anleitung](sdk.md) beschreibt die Kompilierung.

```java
var result = TransferPlanner.plan(schema, TransferPlanner.Names.DECLARED);
if (!(result instanceof TransferPlanningResult.Ready ready)) {
    throw new IllegalArgumentException("Schema cannot be transferred");
}
var plan = ready.plan();
try (var reader = new Xtf24Reader(plan, input);
     var writer = new Xtf24Writer(plan, output)) {
    for (var event = reader.readNext(cancellation); event.isPresent();
         event = reader.readNext(cancellation)) {
        writer.write(event.orElseThrow(), cancellation);
    }
}
```

Die Typen stammen aus `guru.interlis.transfer` und `guru.interlis.xtf24`.
`input`, `output` und der Abbruchtoken werden vom Host bereitgestellt. Beide
Streams bleiben Eigentum des Aufrufers. Die Konstruktoren führen keine
Stream-I/O aus. Reader und Writer werden jeweils von einem Aufrufer verwendet.
Der Reader prüft das XML-Dokumentende vor der Ausgabe von `EndTransfer`.
Das anschliessende leere Ergebnis bestätigt den Protokollabschluss. Erst ein erfolgreiches `EndTransfer` beendet
und flusht die Ausgabe. Ein vorzeitiges Writer-Close meldet einen unvollständigen
Transfer. Nach einem Fehler innerhalb einer Operation bleibt nur Close möglich.
Eine bereits vor dem Aufruf angeforderte Annullierung lässt die Position bestehen.
Annullierung während einer Operation wird zwischen Parser-/Writeroperationen
beobachtet. Ein blockierender Host-Stream braucht hostseitige Timeouts.

## Unterstütztes Profil

XTF 2.4 FULL verwendet die normativen INTERLIS- und Geometrienamespaces,
Modell-XMLNS und Originalnamen übersetzter Modelle. Der Reader akzeptiert beliebige
Präfixe. Die Headerdeklarationen behalten ihre Reihenfolge und Herkunft.
Deklarierte Modelle decken ihre gerichtete Importclosure ab. Pro Basket werden
generische Domains mit expliziten vollqualifizierten `generic=concrete`-Paaren
gebunden. Ein Basket beeinflusst die Bindungen des folgenden Baskets nicht.

Der Adapter transportiert typisierte Skalare mit exakten Zahlenlexemen,
Strukturen, LIST/BAG mit Reihenfolge und Duplikaten, Vererbung, Referenzen und
Association-Payloads. Native 2D-/3D-Koordinaten, Linien, Flächen, Areas und
Mehrfachgeometrien sowie Standardkreisbögen sind unterstützt. INCOMPLETE erlaubt
die vorgesehenen Mehrfachdarstellungen einfacher Linien und Flächen.
BLACKBOX BINARY und XML werden begrenzt verarbeitet. XML-Fragmente behalten den
Namespacekontext, einschliesslich Bindungen für QName-Textwerte.

Die Codecs prüfen XML-/Protokollsyntax, Dekodierbarkeit, Reihenfolge und
Ressourcenlimits. Modellbereiche, Pflichtwerte, Kardinalitäten, Zielobjektexistenz,
Strukturzuweisbarkeit und Geometrietopologie gehören zur separaten Datenvalidierung.
Dekodierbare fachlich ungültige Werte bleiben erhalten. Die handgeschriebenen
Beispiele unter `testdata/reference/iox/ili24` enthalten dafür einen bewusst
ungültigen Transfer. Ausgewählte externe Referenzprüfungen stehen in der
[IOX-Referenz](../reference/iox-legacy.md).

## Grenzen und Fehler

INITIAL/UPDATE und Löschereignisse, eigene LINE FORM, nichtleere unbekannte
Erweiterungen und Metadaten, 1D-Geometrie sowie vom Transferplaner ausgeschlossene
Views, Graphics und abgeleitete/synthetische Transferobjekte werden nicht angeboten.
Eine automatische Konvertierung zwischen XTF 2.3 und 2.4 ist nicht enthalten.
Mehrere Aussenringe werden beim Lesen und Schreiben abgelehnt. Kurvenpartitionen,
mehrteilige Linien in einem Flächenrand und LINEATTR-Payloads benötigen beim
Schreiben eine explizite externe Transformation. Ein synthetisches Embedded-Feld
repräsentiert genau einen Link. Wiederholte gleichnamige Embedded-Links am
selben Objekt werden mit `INVALID_WIRE` abgelehnt.
Negative native 2.4-Radien werden gelesen und erhalten. Der Writer meldet dafür
`UNSUPPORTED`, weil der neutrale Bogenwert sie nicht von der signierten
Richtungsdarstellung aus XTF 2.3 unterscheiden kann. Nullradien bleiben erhalten.

Bei Mehrfachgeometrien folgt der Adapter der wiederholbaren EBNF der Norm und
akzeptiert leere Mehrfachwerte. Die abweichende Maximalmultiplizität im publizierten
Geometrie-XSD begrenzt eine allgemeine XSD-Interoperabilitätsaussage. Die
Referenztests prüfen tatsächlich mehrere Komponenten mit IOX.

`Xtf24Exception` unterscheidet XML-, Sicherheits-, Wire-, Identitäts-, Limit-,
I/O-, Konfigurations- und Unsupported-Fehler und liefert verfügbare Quellpositionen.
Die gemeinsamen Sequenzfehler verwenden `TransferProtocolException`.
`Xtf24Limits` begrenzt Tiefe, Skalare, technische Namen, objektweise Ereignisse
und aggregierte Zeichen, Bytes eines Parserfortschritts sowie transferweite
Identitäten. Zusätzlich gelten 64 Attribute und 64 Namespace-Deklarationen pro
Element und 4096 Modelle im Header. DTD und externe Entities sind gesperrt.

Reader und Writer puffern jeweils ein Objekt und behalten Identitätsmengen bis
Abschluss, Fehler oder Close. Der Speicher wächst somit mit der Objektgrösse
und der Anzahl eindeutiger IDs. Auch bei einer direkten Reader-Writer-Pipeline
existieren die Identitätsindizes beider Instanzen. Die Limits sind Ressourcenbudgets
und liefern keine feste Heap-Kapazitätsgarantie.

Der Java-SDK-Vertrag ist Version 0.4. `TransferValue.XmlFragment` besitzt neben
`xml` die unveränderliche Map `namespaceBindings`. Der Einargumentkonstruktor
bleibt verfügbar. Bestehende Record-Patterns müssen die neue Komponente aufnehmen.
