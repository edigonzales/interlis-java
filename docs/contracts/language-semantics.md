# Sprachsemantik und gebundene Modellinformationen

Die gebundene Sicht bewahrt Typen, geordnete Beziehungen und Herkunft für alle Verbraucher. Sprachversionsunterschiede und bewusste Referenzabweichungen sind in den [Normentscheidungen](norm-decisions.md) belegt.

<a id="gebundene-views"></a>
## Gebundene Views

`ViewSymbol` trennt `baseView` (Vererbung) von `formation` und den geordneten
`bases`. `Formation` ist geschlossen: `Projection`, `Join`, `Union`,
`Aggregation` und `Inspection`. Basen sind `ViewSymbol.Base`-Symbole mit
`SymbolKind.VIEW_BASE`, eigener Identität, Ziel und `orNull`-Merkmal.
Ein expliziter Alias hat einen eigenen editierbaren Namensbereich. Ohne Alias
verweisen Namensvorkommen auf die zugrunde liegende Deklaration.
`baseExtensions` hält die Zuordnungen zur ursprünglichen Basis im Kontext der
jeweiligen View; eine Erweiterung verändert nicht das Basismodell.

`selections` enthält gebundene `ExpressionId`-Werte. `members` und `constraints`
behalten die fachliche Reihenfolge. `AttributeSymbol.derivations()` liefert die
lokalen Ableitungsausdrücke in Quellreihenfolge. Die unmittelbare
`overrides()`-Kette erhält geerbte Alternativen; ihre fachliche Reihenfolge ist
Basis vor Erweiterung, wobei die letzte definierte Alternative gilt. Die gewählte
Schema-Sicht enthält diese Ausdrücke samt ihrer Typen. `EffectiveMember` behält
für Views den Weg vom deklarierenden Besitzer bis zur aktuellen Sicht und
unterscheidet lokale Überschreibung von blosser Vererbung. `ALL OF` erzeugt Attribute mit Herkunftsverweis,
keine zweite Attributvariante. In 2.3 übernimmt `ALL OF` die Attribute; in 2.4
zusätzlich die deklarierten Rollen in ihrer effektiven Reihenfolge (2.3 §2.15,
PDF69; 2.4 §3.15, PDF91). Kopierte Rollen bleiben gewöhnliche abgeleitete
Attribute mit `DerivedOrigin(originalRole, ALIAS)` und gebundenem `RoleStep`.
Ihr Objekttyp bezeichnet den einzelnen Teilnehmer der Basiszeile, auch bei
mehrwertiger Beziehungskardinalität; seine tatsächlichen Ziele bleiben für
weitere Pfadschritte verfügbar. Rollenmerkmale werden nicht zu Attributmerkmalen.
`AGGREGATES` ist zugänglich, gehört aber nicht
zur Liste der transferierten View-Mitglieder. `PathStep.ViewBaseStep` erhält
lokale Basisidentität und fachliches Ziel.

Listen sind unveränderlich und nullfrei. IDs beziehen sich auf denselben
Snapshot. Quellbereiche bleiben revisionsgebundene UTF-16-Halbintervalle.
Ungültige und noch unvollständig gebundene Views bleiben in der Analyse
inspizierbar; nur das gemeinsame Gate entscheidet über `CompiledSchema`.

`Inspection` enthält zusätzlich zur Basis den gebundenen Pfad, die Identität
seines Strukturelements und die Identität des Elternobjekts/-elements. Die
Basis bezeichnet weiterhin die deklarierte Ausgangsklasse; ein
`ViewBaseStep.target` kann bei Inspektionen das abgeleitete Element bezeichnen.
`InspectionStep` hält Kontextart (`PARENT`, `THISAREA`, `THATAREA`), View und Ziel
explizit fest. Fehlende Geometrieinhalte werden durch `INCOMPLETE_SUPPORT`
ausgewiesen; navigierbare Teilstrukturen sind keine Schemafreigabe.

`AssociationSymbol.derivedFrom` ist optional und verweist auf eine lokale
`ViewSymbol.Base`-Identität. `RoleSymbol.derivation` verweist optional auf den
gebundenen Ausdruck, der den Teilnehmer bestimmt. Beide sind nullfrei,
snapshotlokal und unveränderlich. Eine inkompatible Rollenzuweisung erzeugt
eine Typdiagnose. Diagrammprojektionen verwenden für Formationsbasen und
abgeleitete Assoziationen `VIEW_DERIVATION`, für View-Vererbung `INHERITANCE`.

<a id="gebundene-inspektionsfaktoren"></a>
## Gebundene Inspektionsfaktoren

`InspectionExpression` (`guru.interlis.model`) hält einen benannten oder
inline definierten Inspektionsfaktor. `view` ist optional, `source` bezeichnet
die Ausgangsklasse/-struktur, `element` die inspizierte Struktur, `path` den
gebundenen Zerlegungspfad und `restriction` optional den gebundenen OF-Pfad.
`area` unterscheidet Gebietsinspektion. Bei einer benannten Sicht bleibt deren
Identität einschliesslich Selektionen und Ableitungen zugänglich. Der Typ ist
eine BAG-Menge der inspizierten Strukturelemente, keine skalare Referenz.

`children()` enthält zuerst den Zerlegungspfad und gegebenenfalls danach die
Einschränkung. Typen, Symbole und Ausdrücke gehören zum selben Snapshot;
alle Komponenten sind unveränderlich und nullfrei. Die Schemaauswahl erhält
auch diese Ausdruckskinder und ihre Typen. Ungültige Ziele erzeugen
`ErrorExpression`; eine unzulässige Einschränkung erhält ERROR-Zustand und
eine Sprachdiagnose. Quellbereiche bleiben halb-offene UTF-16-Bereiche.

Die Einschränkung MUSS ein einzelnes Objekt der Ausgangsklasse oder einer
Unterklasse bezeichnen. Ein Skalar oder eine Objektmenge ist unzulässig.
Die Prüfung verwendet gemeinsame Typrelationen. Inline- und benannte
Inspektionen verwenden denselben Zerlegungspfadbinder wie die View-Formation.
Topic-Abhängigkeiten werden anhand gebundener Ziele geprüft, nachdem die
Topics vollständig aufgebaut sind. Ein ungültiger PARENT-/Gebietskontext ist
ein Sprachfehler; eine unvollständige Geometriestruktur bleibt eine davon
unterscheidbare Implementierungslücke. Diese Geometriesperre bleibt erhalten.

Abnahme: `InspectionFactorContractTest` prüft beide Sprachversionen,
Restriktionen, Element-/Elternidentitäten, geerbte Inspektionen, Navigation,
Imports, Dateireihenfolge und frische gegen wiederverwendete Analyse.
Weitere Normkombinationen und vollständige Geometriestrukturen bleiben offen.

<a id="gebundene-generische-koordinatenkontexte"></a>
## Gebundene generische Koordinatenkontexte

`ContextSymbol` (`interlis-model`, `guru.interlis.model`) ist eine eigene
Symbolart CONTEXT. Seine unveränderlichen `Mapping`-Werte enthalten die
GENERIC-Domainidentität, konkrete Alternativen in Deklarationsreihenfolge und
den Quellbereich der generischen Referenz. Es werden keine Laufzeitwerte gewählt.
`TopicSymbol.deferredGenerics` erhält direkte Deklarationen; Vererbung bleibt
über `baseTopic` zugänglich. `TopicSymbol.genericBindings` liefert wirksame
Verwendungen einschliesslich geerbter Mitglieder und erreichbarer Strukturen.
Die verschachtelte `GenericBinding` enthält Domain, geordnete Alternativen,
wirksame CONTEXT-Identitäten und `BindingState`: CONCRETE für genau eine
Alternative, DEFERRED für eine legale Auswahl beim Transfer, ABSTRACT für noch
nicht konkretisierte Verwendungen in einem abstrakten Topic. Fehler sind
Diagnosen und werden nicht als legale Aufschiebung veröffentlicht. Ein gültiges
Schema enthält weder eine ungeklärte konkrete Verwendung noch widersprüchliche
Kontexte. Sämtliche IDs gehören zum selben Snapshot; die Schema-Closure erhält
sie. Die globale Typdefinition der Domain bleibt erhalten, weil dasselbe
GENERIC je Modell/Topic unterschiedliche wirksame Konkretisierungen haben kann.

Kontexte wirken nur im eigenen Modell und entlang transitiver Imports. Eine
neue Festlegung muss bestehende Alternativen spezialisieren; unvereinbare
Importkontexte sind Fehler. Konkrete Ziele müssen Koordinatentypen mit passenden
Dimensionen und eingeschränkten Grenzen sein. Die Auswahl benötigt keine
Geometrieengine. `GenericContextContractTest` prüft öffentliche Bindungen,
Quellqueries, Versionen, Imports, Vererbung, Übersetzungsziele und Fresh-vs-Reuse
nach geänderten importierten Kontexten. Fachgrundlage: 2.4 §3.5.2 und §3.8.8.

<a id="gebundene-linienformen"></a>
## Gebundene Linienformen

`LineFormSymbol` ist eine eigene Symbolart LINE_FORM mit optionaler
`segmentStructure: SymbolId`. Ein fehlendes Ziel ist nur in fehlerhafter Analyse
zulässig; ein gültiges Schema besitzt ein gebundenes Strukturziel mit
LineSegment-Basiskante. Eigene Linienformen benötigen in 2.3 CONTRACTED; in 2.4
entfällt diese Bedingung. Übersetzungen vergleichen die korrespondierenden
Strukturidentitäten. Referenz: 2.3 §2.8.12.3, 2.4 §3.8.12.3.

`GeometryType.lineForms` ersetzt den bisherigen Text durch eine unveränderliche
Liste gebundener Identitäten in Deklarationsreihenfolge. Für den fachlichen
Übersetzungsvergleich zählt die Menge, nicht ihre Reihenfolge.
`lineAttributes` enthält eine optionale Strukturidentität, `directed` den
Richtungssinn, `withoutOverlaps` die effektive Bedingung und `overlap` eine optionale
`GeometryType.Tolerance`. `Explicit(DecimalLiteral)` bewahrt den exakten Wert
einschliesslich lexikalischer Präzision; `TrackWidth(SymbolId coordinateDomain)`
bewahrt die bestimmende Koordinatendomäne der impliziten Spurbreitentoleranz
`q * sqrt(2) / 2`. Es wird kein gerundeter Dezimalwert gespeichert. Alle Werte sind
unveränderlich und nullfrei; alle Ziele gehören zum selben Graphen. Eine leere
Toleranz bedeutet nicht null, sondern keine etablierte Toleranz (etwa bei einer
noch abstrakten Koordinatenbasis). Vererbung erhält die bestimmende Basisdomäne.
Quellnavigation verwendet die beim Binden erfassten Referenzen.

Die vordefinierten Segmenttypen und Einheiten besitzen `PredefinedOrigin` und
innerhalb einer Sprachversion gemeinsame Identitäten unabhängig von Import-
und Dateireihenfolge. SegmentEndPoint, ArcPoint und Radius sowie ABSTRACT/FINAL
und effektive Mitglieder sind öffentlich zugänglich. Sie bleiben bei der
Schemaauswahl erhalten, ohne Deklarationen unabhängiger Quellmodelle aufzunehmen.
Die kontextabhängigen Stützpunkttypen und die StartSegment-Bedingung
werden im folgenden Abschnitt konkretisiert. Benutzerdefinierte Segmenttypen werden wie die vordefinierten Strukturen
kontextbezogen übernommen: Vererbung, effektive Mitglieder, Overrides, Typen,
Ableitungen und sämtliche Constraintfamilien bleiben erhalten. Abhängige
verschachtelte Strukturmengen und Domänen werden entsprechend gebunden. Die
kanonischen Deklarationen und unabhängige fremde Ziele bleiben unverändert;
jedes Inspektions-/Geometriepaar besitzt einen eigenen Kontext. Abgeleitete
Symbole erhalten CONTEXTUALIZATION-Herkunft und keinen künstlichen Quellnamenbereich. `LineFormBindingContractTest` prüft den hier beschriebenen
Teilumfang positiv, negativ, über Queries und mit Wiederverwendung.

<a id="metadaten-achsen-und-geometrieinspektion"></a>
## Metadaten, Achsen und Geometrieinspektion

Kanonische Besitzer aller folgenden Modellwerte sind `interlis-model` /
`guru.interlis.model`. `MetadataBasketSymbol(header, basketKind, topic,
baseBasket, objects)` und `MetaObjectSymbol(header, objectClass)` ergänzen
METADATA_BASKET und METAOBJECT. `objects` enthält nur lokale Identitäten in
Deklarationsreihenfolge; geerbte Objekte behalten ihre ursprüngliche Identität.
`topic` und `objectClass` sind nur bei fehlgeschlagener Bindung leer; solche
Analysen erzeugen kein gültiges Schema. `baseBasket` ist optional. Basketbasen
müssen hinsichtlich Art, FINAL und Topic-Vererbung verträglich sein. Objektklassen
binden im referenzierten Topic einschliesslich dessen Vererbung. Unauflösbare
oder mehrdeutige Objektbezüge sind Sprachfehler. Übersetzungen vergleichen
gebundene Ziele und die unveränderten externen Metaobjektnamen.

`NumericRange.referenceSystem()` liefert `Optional<NumericRange.ReferenceSystem>`
statt Text. Der Record enthält `SymbolId target`, `boolean domain` und
`OptionalInt axis`; Indizes sind einsbasiert. Ein Domainziel muss numerisch sein
oder eine vorhandene Koordinatenachse auswählen. Ein Bezug ohne Achse auf
eine Koordinatendomain mit ROTATION bezeichnet eine Richtung (Nullrichtung
und Drehsinn); eine explizite Einheit muss dann eine Winkeleinheit sein. Metaobjektziele müssen zur
Referenzsystemfamilie gehören; ein Achsenbezug verlangt ein Koordinatensystem.
`CoordinateType.rotation()` enthält optional `Rotation(zeroAxis, quarterTurnAxis)`
mit zwei verschiedenen vorhandenen Achsen. Achseneinheiten, Grenzen und
Referenzsysteme bleiben in Quellreihenfolge erhalten. Die gemeinsame
Einheitenbeziehung prüft den Unit-Parameter; ANYUNIT verlangt keine explizite
Einheit, ein spezifischer Parameter verlangt eine verträgliche Einheit.
Normgrundlage: 2.3 §§2.8.5/2.8.8/2.10, 2.4 §§3.8.5/3.8.8/3.10.

`INTERLIS.METAOBJECT_TRANSLATION` ist in 2.3 und 2.4 eine konkrete,
eigenständige vordefinierte `ClassSymbol`-Instanz ohne Basisklasse. Ihre beiden
lokalen Attribute `Name` und `NameInBaseLanguage` sind in dieser Reihenfolge
MANDATORY NAME mit der festen Grenze 255. Zwei getrennte `ConstraintSymbol`-
Werte enthalten je eine globale `UniquenessConstraint` mit genau einem gebundenen
Attributpfad; sie bilden keinen zusammengesetzten Schlüssel. Attribute und
Constraints behalten Owner, Deklarationsreihenfolge und `PredefinedOrigin` je
Version. Ihre Definitionen erfinden keine Quellbereiche; die Ausdruckspfade
verwenden den vorhandenen leeren vordefinierten Anker. Vererbung verwendet die
gewöhnlichen Basis-/Effektivmitglieder und Typ-/Sprachqueries.
Überschreibungen vordefinierter Attribute unterliegen denselben Pflicht-, Typ-,
Struktur-/Collection- und Kardinalitätsregeln wie Quelldeklarationen.
Grundlage: Anhang A, 2.3 S. 95 und 2.4 S. 122.

Gebundene NAME-/URI-Typen besitzen die festen Grenzen 255/1023; eine fehlende
Längenoption der Schlüsselwortsyntax bedeutet keinen unbegrenzten Typ.
TEXT und MTEXT behalten ihre optionale deklarierte Längengrenze. Die Syntax
bleibt unverändert quellengetreu; effektive Typen und öffentliche Typqueries
verwenden die fachlichen Grenzen.

METAOBJECT mit Name und UNIQUE-Bedingung, REFSYSTEM, COORDSYSTEM mit Axis,
SCALSYSTEM und AXIS mit Unit-Parameter besitzen gemeinsame vordefinierte
Identitäten je Sprachversion. Unit-Überschreibungen behalten Typ und
Override-Ziel; bei Referenzsystemen/Achsen sind keine beliebigen Parameter erlaubt.
Der Owner unterscheidet Laufzeit- und Klassenparameter. Funktionsargumente bleiben
`FunctionParameter`. SIGN-/METAOBJECT-Signaturparameter folgen den
[gebundenen Grafikregeln](#gebundene-grafikdefinitionen). Der Compiler liest
oder verarbeitet dabei keine Grafik- oder externen Metadatenwerte.

Geometrieinspektionen verwenden die bestehenden `StructureSymbol`, Typ- und
Ausdruckswerte. Die lokale Segmentfamilie übernimmt den tatsächlichen
Stützpunkttyp. `Origin.DerivedOrigin(canonicalSegment, CONTEXTUALIZATION)`
bewahrt die Identität der vordefinierten Vorlage; `baseType` verbindet die
kontextbezogene Hierarchie, nicht eine zweite deklarierte Vererbung.
LineGeometry enthält Segments und eine typisierte MandatoryConstraint mit
`isOfClass(Segments[FIRST], StartSegment)`. Die Bedingung ist im Schema über
ExpressionId erreichbar. SurfaceEdge besitzt gerichtete Geometry; nur in 2.3
kommt LineAttrs mit gebundener Struktur bzw. ANYSTRUCTURE hinzu. 2.4 hat
Linienattribute abgeschafft (Vorwort, §3.15, Anhang A): LINE ATTRIBUTES wird als
versionswidrig abgelehnt, nicht als Implementierungslücke. SurfaceBoundary enthält Lines.
Die vordefinierte Bindung erfasst `INTERLIS.LineGeometry`, `INTERLIS.SurfaceEdge` und
`INTERLIS.SurfaceBoundary` auch als gewöhnliche Strukturtypen. Die Deklarationen
aus Anhang A bewahren LIST {0..*}, gerichtete aber noch unbestimmte POLYLINE,
optionale Felder, nur in 2.3 ANYSTRUCTURE-LineAttrs und die echte gebundene
StartSegment-Bedingung mit leerem vordefiniertem Ausdrucksbereich.
Inspektionskopien erhalten `DerivedOrigin(canonical, CONTEXTUALIZATION)` statt
derselben `PredefinedOrigin`; ihre Mitglieder und Bedingungen verweisen auf
die entsprechenden kanonischen Deklarationen. Konkrete Stützpunkttypen bleiben
auf die jeweilige Inspektion beschränkt. Die öffentliche Namensabfrage liefert
die kanonische Deklaration eindeutig.
Listen und Optionalität entsprechen den vordefinierten Deklarationen.
AREA-Inspektionen erlauben THISAREA/THATAREA, aber kein ALL OF (2.3 §2.15,
2.4 §3.15); dieses Verbot gilt auch für von ihnen abgeleitete Sichten ohne
eigene Formationssyntax. Eigene Kurvensegmente verwenden dieselben kontextbezogenen
Struktur-, Typ- und Ausdruckswerte wie die Standardsegmente. Die frühere
pauschale Sperre INSPECTION_CUSTOM_SEGMENT entfällt bei vollständiger Bindung.
Selbst deklarierte Attribute mit dem abstrakten `INTERLIS.LineCoord` müssen
auch in eigenen Linienstrukturen ausdrücklich ABSTRACT sein (2.3 §2.6.1,
PDF31; 2.4 §3.6.1, PDF38). Eine abstrakte Besitzerklasse allein genügt nicht.
Die spätere Kontextkopie ersetzt diese Prüfung der Quelle nicht; kanonische
vordefinierte Mitglieder bleiben unverändert. Legale externe Parametersignaturen
und GENERIC-Koordinaten behalten ihre eigenen Regeln.

Alle Werte sind unveränderlich, nullfrei und snapshotgebunden; Sammlungen werden
kopiert. Aufbauzustand gehört dem Compilerlauf, Budget und Abbruch werden über
seinen bestehenden RunContext geprüft. Vordefinierte UNIQUE-Ausdrücke verwenden
leere Bereiche der Quelle `predefined:/INTERLIS-<language>`; sie sind keine
navigierbaren Quelltextstellen. Vertragstests: `MetadataBindingContractTest`,
`GeometryInspectionContractTest`, `DiagramProjectionTest`; positive und negative
Versionen, Identitäten, Übersetzungen, Imports, Dateireihenfolge und
Fresh-vs-Reuse müssen fachlich übereinstimmen.

<a id="gebundene-grafikdefinitionen"></a>
## Gebundene Grafikdefinitionen

`GraphicSymbol` und `DrawingRuleSymbol` gehören `interlis-model` /
`guru.interlis.model`. `GraphicSymbol` enthält optionale `baseGraphic` und
`basis`, geordnete `selections`, lokale `rules` und `effectiveRules`.
Selektionen stehen Basis vor Erweiterung; überschriebene Regeln behalten ihre
Position, neue Regeln werden angehängt. In fehlerhaften Analysen können Ziele
fehlen; ein gültiges Schema benötigt die fachlich erforderlichen Bindungen.
`DrawingRuleSymbol` enthält `signatureClass`, `overrides` und geordnete
`Clause(range, condition, assignments)`. Zuweisungen binden den Parameter über
`SymbolId`; Werte unterscheiden `ExpressionValue`, `MetaObjectValue` und
`AccordingValue`. Der letztere enthält den Ausdrucksselektor und geordnete
`EnumAssignment`-Werte; deren Pfade gehören zum aufgelösten Enumtyp des Selektors.
Ein Intervall benötigt geordnete Geschwisterknoten. Basiszuweisungen bleiben am
Basisregelsymbol erhalten, damit ihre Reihenfolge nicht durch Kopieren verloren
geht. Die API führt keine Zuweisungen aus.

Signaturparameter verwenden `ParameterSymbol` mit gebundenem Typ und Override.
`MetaObjectType(id, origin, target)` unterscheidet Signatur-Metaobjekte von
Transferreferenzen. Ein explizites OF liefert die Zielklasse; bei leerem Ziel
wird der Klassenbezug im Zeichenregelkontext anhand der verantwortlichen
Signaturklasse bestimmt. Die unten beschriebene Verantwortungsregel erlaubt
für den vordefinierten Sign-Parameter die Wiederverwendung passender
Basissignaturen; andere METAOBJECT-Parameter behalten ihre Kontexttypprüfung. Daher entsteht aus einem METAOBJECT-Parameter
keine NO-OID-Prüfung für Transferbeziehungen. Solche Beziehungen und
Referenzattribute unterliegen weiterhin den versionsgerechten OID-Regeln.

Alle Werte und Listen sind nullfrei und unveränderlich. Symbol-/Typ-/Ausdrucks-IDs
gehören demselben Snapshot, Bereiche ihrer Quellrevision. Abbruch folgt dem
Compilerlauf; ungültige Bindungen oder Typen erzeugen strukturierte Diagnosen
und verhindern das Schema. Quellqueries verwenden dieselben Referenzen.
`GraphicBindingContractTest` prüft Identitäten, Bedingungen, Typen, Reihenfolge,
Vererbung, Übersetzungen, Imports und Wiederverwendung. Geometriezuweisungen
lösen die Stützpunktdomains über denselben Typvertrag wie öffentliche Queries
auf; ein konkreter Koordinatentyp kann einen passenden abstrakten Parameter
spezialisieren. Fehlende Typinformation wird nicht durch einen Ersatztyp ersetzt.

<a id="umrechnungsfaktoren-und-skalare-einheitenvererbung"></a>
## Umrechnungsfaktoren und skalare Einheitenvererbung

`UnitSymbol` (interlis-model / guru.interlis.model) ergänzt
`conversionFactors: List<ConversionFactor>` und
`conversionFunction: Optional<String>` vor `definition`. Der verschachtelte
`ConversionFactor(operator, kind, decimal, range)` verwendet den bestehenden
`Operator` und `ConstantKind { DECIMAL, PI, LNBASE }`. Nur DECIMAL besitzt einen
`DecimalLiteral`; Lexem, exakter Wert und Präzision bleiben erhalten. Der
Bereich bezeichnet die Konstante in ihrer Quellrevision. Die Werte und Listen
sind nullfrei und unveränderlich. Nicht aufgelöste Ziel-IDs können in einer
fehlerhaften Analyse fehlen, ohne die bereits erfassten Faktoren zu verlieren.
Ein gültiges Schema verlangt die vollständige Bindung. Numeric-Faktoren und
FUNCTION-Erklärung schliessen sich gegenseitig aus. Eine leere Faktorenliste
bei vorhandener `derivedFrom` ohne FUNCTION bedeutet Identitätsumrechnung.

Null wird je Konstante exakt geprüft. Multiplikation mit null und Division
durch null verhindern das Schema mit verschiedenen Regelkennungen und dem
Konstantenbereich. Die Repräsentation berechnet kein Gesamtprodukt und keinen
Quotienten; der unten beschriebene Unterteilungsvergleich darf bekannte
Faktoren gezielt exakt vergleichen. PI/LNBASE werden nicht gerundet und
FUNCTION wird nicht ausgeführt.
Übersetzungsvergleiche verwenden Operatoren, Konstantenarten, exakte Zahlen
und gebundene Einheitenziele. Zahlenformatierung und Quellbereiche sind keine
numerische Umrechnungsidentität.

Bei skalaren numerischen Domain- und Attributerweiterungen wird eine
syntaktisch ausgelassene Einheit vor der Ausdrucksbindung aus der effektiven
Basis übernommen. Das verändert nur den neu definierten Typ und mutiert
keine referenzierte Domain. Eine konkrete Basiseinheit bleibt dieselbe
gebundene Identität, auch wenn eine andere Einheit umrechenbar wäre.
Eine abstrakte Basiseinheit erlaubt passende Spezialisierung über die gebundenen
Einheitenbeziehungen; ein konkreter einheitenloser Basisbereich bleibt
einheitenlos. Compiler und `TypeRelations.canExtend` verwenden hierfür
dieselbe interne Relation und erklären Ablehnungen über `TypeRelation`.
Die allgemeine Zuweisungsprüfung erhält dadurch keine automatische Umrechnung
oder pauschale neue Regel für Literale. Koordinatenachsen und vollständige
Dimensions-/Präzisionsregeln bleiben gesonderte Verpflichtungen.

`NumericUnitConversionContractTest` prüft sechs Regressionen für Einheiten und Umrechnungen, öffentliche
Werte, Fehlerbereiche, importierte Bindungen, Vererbung, Typqueries,
Übersetzungen sowie frische und wiederverwendete Analyse in 2.3/2.4.

<a id="effektive-geometrie-und-versionsgerechte-toleranz"></a>
## Effektive Geometrie und versionsgerechte Toleranz

Vor Ausdrucksbindung erhalten Domain- und Attributerweiterungen ihre effektiven
Linienformen, Vertexdomäne und etablierte Toleranz. Eine explizite neue
POLYLINE-Deklaration bleibt ohne DIRECTED ungerichtet; MANDATORY allein übernimmt
den Basistyp. Gemeinsame Erweiterungsregeln prüfen Familie, Formenreduktion,
Vertexrestriktion, Richtung, unveränderliche Toleranz und in 2.3 die
Linienattributstruktur. SURFACE kann zu AREA präzisiert werden. Öffentliche
`TypeRelations.canExtend` verwendet dieselbe Regel; Wertzuweisung bleibt davon
getrennt. Zuweisungsvergleiche mit Spurbreitentoleranzen verwenden exakte
quadrierte Grössen ohne Quadratwurzelapproximation.

Eine konkrete Geometrie benötigt Formen und konkrete Stützpunkte. In 2.3
benötigen SURFACE/AREA eine explizite oder geerbte positive Toleranz. In 2.4 ist
die Spurbreite zulässig; GENERIC kann eine legale aufgeschobene Konkretisierung
tragen. Die explizite 2.4-Toleranz und alle Stützpunktachsen haben dieselbe
Stellenzahl. Eine feste Untergrenze 0.001 ist keine Sprachregel.
Grundlage: 2.3 §2.8.12.2–3 und §2.8.13.4; 2.4 §3.8.12.2–3 und §3.8.13.4.
Die zusätzliche 2.3-Referenzerwartung zur Toleranzpräzision bleibt eine offene
Normprüfung; daraus folgt keine automatische Ausnahme im Konformitätslauf.
`GeometryRulesContractTest` und `GeometryInspectionContractTest` prüfen die
öffentlichen Inhalte und positiven/negativen Fälle.

<a id="verantwortliche-signaturklasse"></a>
## Verantwortliche Signaturklasse

`DrawingRuleSymbol.signatureClass` bewahrt die deklarierte, gebundene OF-Klasse.
Die verantwortliche Klasse wird je gebundener Sign-Alternative intern geprüft;
ein einzelnes zusätzliches Klassenfeld an der gesamten Regel wäre bei mehreren
Alternativen irreführend. Die bestehenden öffentlichen Werte enthalten alle
Metaobjekt-, Klassen-, Parameter- und Override-Identitäten sowie die geordneten
Bedingungen und ACCORDING-Fälle. Es gibt keine neue öffentliche Fassade.

Ausgangspunkt ist die Klasse des ausgewählten Sign-Metaobjekts. Sie muss mit
der deklarierten Signaturklasse durch Vererbung vereinbar sein. Verwendete
Parameter können die Verantwortung entlang dieser Vererbungskette präzisieren,
sofern die OF-Klasse gleich dieser Parameterklasse oder deren Erweiterung ist.
Eine lediglich deklarierte, noch nicht verwendete Unterklasse führt ihre
zusätzlichen Pflichtparameter nicht automatisch ein. Die tatsächliche
Metaobjektklasse kann ihrerseits zusätzliche Pflichtparameter verlangen.
Explizite METAOBJECT-OF-Beschränkungen bleiben unabhängig davon verbindlich,
auch bei geerbten Werten und einer erst in der deklarierten Unterklasse
eingeführten engeren Zielbeschränkung.
Grundlage: 2.3 §2.16, S. 71; 2.4 §3.16, S. 92–93.

Geerbte Klauseln werden vor lokalen Klauseln betrachtet. Für die statische
Verantwortungs- und Typprüfung werden unbedingte Zuweisungen mit den jeweiligen
bedingten Klauseln verbunden; partielle bedingte Erweiterungen behalten die
früheren bedingten Sign-Auswahlen. Jeder Kontext erhält die Quellreihenfolge und
die jeweils spätere Zuweisung desselben gebundenen Parameters. Die
Pflichtparameterprüfung prüft statische Zuweisungsvorkommen, keine Laufzeit-
Definiertheit unter beliebigen Daten und Bedingungen. Es werden weder Bedingungen
noch Aufzählungsselektoren ausgeführt oder Boolean-Kombinationen gelöst.

Nach der vollständigen Parameter-/Override-Bindung werden geerbte Werte
zusätzlich gegen engere Parametertypen der verantwortlichen Klasse geprüft.
Ein bereits ersetzter Wert bleibt an seiner Basisregel öffentlich erhalten,
verursacht aber keinen falschen Typfehler im überschreibenden Kontext.
`GraphicResponsibilityContractTest` prüft die Originalquellen, positive/negative
Fälle, öffentliche Bindungen, Typverfeinerungen, Bedingungen/ACCORDING,
Übersetzungen, Imports, Dateireihenfolge und Fresh-vs-Reuse.

<a id="modellkopfoptionen"></a>
## Modellkopfoptionen

`ModelOptions` (interlis-model/guru.interlis.model) enthält optionale Herkunft
von NOINCREMENTALTRANSFER sowie optionale `TextValue(value, origin)` für CHARSET
und XMLNS. Die Werte sind unveränderlich/nullfrei; `ModelSyntax.options()`
erhält exakt diese expliziten Quellangaben und Originalrevisionen.
`ModelSymbol.options()` veröffentlicht sie nach Bindung. Fehlendes CHARSET
bedeutet den normativen Anhang-D-Zeichenvorrat; `usesDefaultCharacterRepertoire()`
macht diesen Default ausdrücklich abfragbar. Es ist kein Encoding-Default.
`incrementalTransferAllowed()` ist ohne NOINCREMENTALTRANSFER wahr.
`ModelSymbol.transferNamespace()` enthält den wirksamen Transfernamensraum:
2.3 fest http://www.interlis.ch/INTERLIS2.3; 2.4 explizites XMLNS oder
http://www.interlis.ch/xtf/2.4/<kanonisches Originalmodell>. Übersetzungen
verwenden dasselbe Transfervokabular wie ihr Original; widersprechendes XMLNS
ist ein Fehler. Register-/Namensraumprüfung löst keine Hostzugriffe aus.
CHARSET-Namen und Aliasse werden anhand des gepinnten IANA-Registers geprüft;
verwandte Aliasse bleiben im Quellwert erhalten und sind semantisch gleich.
Namespaces sind nichtleere, unreservierte URI-Referenzen; veraltete relative
URI-Referenzen werden nicht allein wegen dieser Empfehlung abgelehnt.
Die Optionssyntax ist nur in 2.4 zulässig. CONTRACTED hat dort auch für den
Übersetzungsvergleich keine semantische Wirkung; die 2.3-Regel bleibt erhalten.

`ModelOptions.language()` bewahrt der dokumentarischen
Modellsprache und Originalherkunft. `ModelSyntax.language()` ist daraus abgeleitet;
der redundante String-Konstruktorparameter entfällt in API 0.2. Modell- und
Übersetzungssprache bleiben getrennt. Eigene Sprachbezeichnungen bleiben zulässig.

<a id="einheitenrelationen-und-domäneneinschränkungen"></a>
## Einheitenrelationen und Domäneneinschränkungen

META-003/006/009/011/014/016, SEM-005/010/014, TEST-011/012.
`TypeRelations.compareUnits` liefert den vorgesehenen `QueryResult<UnitRelation>`:
beide Snapshotreferenzen werden geprüft, unbekannte IDs und fremde Symbolarten
bleiben Unresolved (`NOT_A_UNIT` bei falscher Art). Gebundene Ableitungen und
zusammengesetzte Produkte/Quotienten werden auf gemeinsame konkrete Ziele
bezogen. Eine abstrakte gemeinsame EXTENDS-Basis bedeutet keine numerische
Umrechnung. Zyklen, fehlende Komponenten und ungültige Faktoren liefern keine
scheinbar bekannte Verträglichkeit. Externe Umrechnungsfunktionen werden nur
strukturell berücksichtigt, weder ausgeführt noch numerisch approximiert.
Compiler und Query verwenden dieselbe reine Relation, auch für numerische
Referenzsysteme. Umrechenbarkeit erlaubt keinen Einheitenwechsel bei Erweiterung.

Numerische Erweiterungen von Domänen und Attributen schränken Grenzen ein und
bewahren in 2.4 die Nachkommastellen. Öffentliche canExtend-Queries verwenden diese
Regel ebenfalls. Zuweisung bleibt davon getrennt. Syntax erhält auch verkehrte
Grenzen unverändert; NUMERIC_BOUNDS/NUMERIC_PRECISION erzeugen reguläre Quellfehler
mit ErrorType statt Exceptions oder Ersatzbereichen. Die strengere Modell-
NumericRange-Invariante bleibt erhalten. Weitere Skalierungs-/Abstraktions- und
Referenzsystemregeln bleiben gesondert zu prüfen.

MANDATORY wird im Domain-Syntaxmodifier und im effektiven DomainSymbol erhalten;
`DomainSymbol.mandatory()` liest den effektiven Flag. MANDATORY allein übernimmt
bei einer Erweiterung den gebundenen Basistyp; ohne Basis ist es ein Quellfehler.
Attribute und Parameter übernehmen den Pflichtflag verwendeter Domänen.
Aliastypen werden nicht mutiert. CLASS-Domänen behalten ihre gebundenen
Restriktionen und ihre typisierten CONSTRAINTS. resolveAliases/canAssign/
canExtend/areComparable bewahren Invalid und Pending-Ursachen; Aliaszyklen
werden über besuchte IDs begrenzt, nicht über eine willkürliche Länge von 64.

Numerische Präzision ist versionsabhängig: 2.3 §2.8.5 S.42 erlaubt Verfeinerung,
sofern gerundete Grenzen im Basisbereich bleiben (7.9949 ja, 7.9999 nein bei
Basismaximum 7.99). 2.4 §3.8.5 S.53 verlangt unveränderte Präzision.
`TypeRelations.canExtend(derived, base, context, language)` erhält daher einen
expliziten nullfreien LanguageVersion-Parameter; weder Quellnamen noch ein
zufällig gefundenes Modell bestimmen das Profil.
Die 2.3-Rundung verwendet exakte Dezimalwerte und HALF_UP; Tie-Details bleiben
in der Normprüfung separat sichtbar. Allgemeine Zuweisung ist unverändert.

<a id="normative-text-länge"></a>
## Normative TEXT-Länge

Textliteraltypen verwenden in 2.3 §2.8.1 und in 2.4 §3.8.1: Anzahl Zeichen mit kanonischer
Kombinationsklasse 0 nach kanonischer Zerlegung (NFD). Dies ist weder UTF-16-
Länge noch die Anzahl Unicode-Codepoints im ursprünglichen Text. Es wird nur
die Länge bestimmt; LiteralExpression.textValue(), Lexem und SourceRange bleiben
unverändert. Deklarierte maximale Länge muss positiv sein; Länge null eines
Literals bleibt möglich. CR/LF/TAB erfordern MTEXT, auch bei Unicode-Escapes.

Der portable Compiler verwendet gepinnte Unicode-17.0.0-Gewichtungsintervalle
und Hanguls kanonische Zerlegung, ohne Laufzeit-Normalizer und ohne neue
Abhängigkeit. `UnicodeWeightsTest` prüft die Tabellenwerte und das vollständige
explizite NFD-Orakel anhand der [gepinnten Unicode-Originale](../../testdata/reference/unicode/17.0.0/README.md).
Den Test startet `java build/InterlisJvmBuild.java build --module interlis-compiler --tests '.*UnicodeWeightsTest'`.
Klassen von kombinierenden Zeichen werden nicht aus der
allgemeinen Zeichenkategorie Mn/Mc abgeleitet. NFKD-Kompatibilitätszerlegungen
werden nicht angewendet. Die Längenregel gilt in beiden Sprachversionen;
Unicode-Repertoire und Modell-CHARSET sind davon getrennte Verträge.

<a id="gebundene-strukturwerte-und-restriktionen"></a>
## Gebundene Strukturwerte und Restriktionen

`StructureValueType(id, origin, Optional<SymbolId> target, List<SymbolId> restrictions)`
ersetzt den einzelnen obligatorischen Zielwert in API 0.2. Leeres target bedeutet
ANYSTRUCTURE; ungelöste Namen erzeugen ErrorType. Restriktionen sind unveränderlich,
nullfrei und in Quellreihenfolge gebunden. targetRoots() liefert die eingeschränkten
Ziele beziehungsweise das Primärziel. StructureValueTypeSyntax erhält die Original-
Namen und UTF-16-Bereiche; es gibt keine künstliche universelle Strukturdeklaration.

Ein konkreter lauflokaler StructureBindings-Helfer verwendet den Aufbauzustand des
GraphBuilder. Compiler und TypeRelations.canAssign verwenden dieselbe Relation:
Strukturelemente passen zu ihren erlaubten Wurzeln; im FUNCTION_ARGUMENT-Kontext
passen auch Objekte, deren Klasse die geforderte Struktur erweitert. ANYSTRUCTURE
akzeptiert dort Strukturelemente und Einzelobjekte, keine Objektmengen oder Zahlen.
Normale eingebettete Wertzuweisung behält ihre eigene Objekt-/Strukturgrenze.
BAG kann bei Einschränkung zu LIST werden; die umgekehrte Richtung ist unzulässig.
Kardinalität und Elementrestriktionen werden gemeinsam geprüft.

Pflichtige beliebige Strukturen beziehungsweise Typen ohne verfügbare konkrete
Struktur verlangen ein abstraktes Attribut. Konkrete Unterstrukturen anderer Themen
werden nur über explizite passende Wurzeln berücksichtigt. Konkrete Klassen und
Strukturen dürfen keine effektiven abstrakten Attribute behalten. Normbezug:
2.3 §2.5.3/2.6.4/2.14; 2.4 §3.5.3/3.6.4/3.14. Nicht gemeinsame Mitglieder von
Alternativen sind über einen gemeinsamen Pfad nicht statisch ansprechbar.

Inspektionen, Kontextualisierung, Übersetzungen, generische Verwendungen, Diagramm-
labels und öffentliche Queries erhalten Grundziel und Restriktionen. Vorhandene
Konstruktorverbraucher und Tests werden direkt angepasst. Relevante Vererbungs-
und Aliasketten verwenden besuchte IDs statt stiller Abschneidung bei 16/32/64;
Abbruch und Arbeitsbudget bleiben aktiv. Keine neue Pass-/Plugin-Infrastruktur.

<a id="gemeinsame-erweiterung-effektiver-typen"></a>
## Gemeinsame Erweiterung effektiver Typen

META-003/006/014, SEM-005/010/014: Domänen und Attributüberschreibungen sowie
`canExtend` verwenden dieselben Regeln. Collectionelemente werden rekursiv auf
Erweiterbarkeit geprüft, einschliesslich Einheiten und versionsabhängiger
Präzision; eine blosse Wertzuweisung reicht dafür nicht. Textlängen dürfen
nicht wachsen; TEXT/MTEXT dürfen bei Erweiterung nicht gewechselt werden. NAME/URI dürfen bei einer
Attributüberschreibung unverändert wiederholt werden; ihre spezielle Bedeutung
wird nicht durch beliebige Texttypen ersetzt.

Koordinaten dürfen Dimensionen ergänzen, keine entfernen. Die etablierten
Achsen verwenden die numerische Erweiterungsregel. Einheiten, Referenz- und
Richtungsangaben sowie Rotation werden vor der Ausdrucksbindung aus der Basis
übernommen, wenn die eigene Inline-Definition sie auslässt. Angegebene
Referenzidentitäten und Richtungen dürfen nicht geändert werden. Zuweisung
von Koordinaten bleibt davon getrennt und verlangt dieselbe Dimensionenzahl.
Nur eigene Inline-Typen werden vervollständigt; gemeinsam referenzierte Aliasse
werden nicht mutiert. Grundlage: 2.3 §2.8.1/2.8.5/2.8.8; 2.4 §3.8.1/3.8.5/3.8.8.

<a id="enumwerte-operatoren-und-standardsignaturen"></a>
## Enumwerte, Operatoren und Standardsignaturen

META-003/006/008/010/014, SEM-005/010/014/017: EnumArgumentType(id, origin,
treeValues) ist die unveränderliche polymorphe ENUMVAL-/ENUMTREEVAL-Familie
einer Funktionssignatur. Sie erfindet keine konkrete Enumdefinition. Noch
kontextfreie Enum-Konstanten behalten ihre symbolische Schreibweise und eine
polymorphe Familie; konkrete Zuweisungen und Argumente binden Typ und Ziel.
EnumerationType erhält treeValues als achten Konstruktorparameter. ALL OF
erhält Werte einschliesslich Knoten und die referenzierte Enumidentität in
baseType. Gewöhnliche Definitionen verwenden false. ALL OF verlangt eine
Enumdefinition; es ist keine zweite Enumdefinition. Blätter sind Baumwerten
zuweisbar, die umgekehrte allgemeine Zuweisung ist unzulässig.

Compiler und TypeRelations.areComparable verwenden dieselbe Operatorregel.
TEXT/MTEXT und ganze Koordinaten sind nur auf Gleich-/Ungleichheit vergleichbar;
Linien nur auf Definiertheit. Objektmengen sind keine einzelnen Objektidentitäten.
BOOLEAN und Ausrichtungen behalten ihre geordnete Bedeutung. Enum-Ordnung
stammt aus der linearen ORDERED-Definition; CIRCULAR bildet keine lineare
Vergleichsordnung und ALL OF allein erzeugt keine Ordnung. Norm: 2.3 §2.8.2–4/2.13/2.14; 2.4 §3.8.2–4/3.13/3.14.

Verwendete vollständige Standardfunktionen besitzen FunctionSymbol-Signaturen
mit geordneten Parametern, Profil, Herkunft und gebundenen Aufrufreferenzen.
Dies umfasst isEnumSubVal/inEnumRange sowie die 2.4-Funktionen length,
multilength, surface und multisurface. AREA ist als einzelner Flächenwert für
surface zulässig, MULTIAREA für multisurface. Es wird nichts ausgeführt.
UNQUALIFIED INTERLIS eröffnet unqualifizierte Aufrufe; lokale Funktionen behalten
ihre eigene Identität. `convertUnit` und die `areAreas`-Varianten verwenden
den [gebundenen Funktionskontext](#funktionskontext-und-konstanteneinheiten).

Formatvergleiche verlangen gebundene verwandte Komponenten oder eine
Formatbasisbeziehung; gleicher TypeKind genügt nicht (z.B. XMLDate/XMLTime).
Die [FORMAT-Zuweisungs- und Erweiterungsprüfung](#gemeinsame-format-relationen) folgt den gemeinsamen Typrelationen. Der Dateiendevertrag wird ausschliesslich
im Syntaxdienst geprüft, mit ILI-SYN-0004 für Folgetokens.

<a id="gebundene-funktionsargumentpfade-und-zahlenkonstanten"></a>
## Gebundene Funktionsargumentpfade und Zahlenkonstanten

META-006/011/014: FunctionCallExpression.argumentPaths ist eine unveränderliche,
nach Argumentindex geordnete Map<Integer, ArgumentPath>. children bleibt die
Quellreihenfolge der tatsächlichen Argumente. ArgumentPath enthält geordnete
ArgumentPathStep-Werte, rootArgument, expectedType und eine optionale Bindung an
einen PathExpression desselben Snapshots. Die Wurzel ist ein vorheriges Argument.
Eine leere Bindung bezeichnet einen legal erst wertabhängig bekannten Objekttyp;
sie ersetzt keine fehlgeschlagene statische Bindung. Schritte erhalten Namen,
Originalbereiche, Assoziationsqualifikation und Index.

Numerische Konstanten veröffentlichen ihre gebundene Einheit im NumericRange
ihres Typs. PI/LNBASE bleiben ConstantExpression-Werte ohne gerundeten, als exakt
ausgegebenen Ersatzwert. Ihr Typ erhält DeclaredOrigin des Originalbereichs.

Die Closure-Auswahl erhält ArgumentPath-Ausdrücke und deren erforderliche
Typen. Kontextualisierung und Übersetzungsprüfung traversieren sie ebenso wie
die ursprünglichen Argumente. Die Quellargumentliste bleibt davon getrennt.
TypeRelations.canAssign benötigt für noch abhängige ATTRIBUTE-Eigentümer
den tatsächlichen Aufrufkontext; ohne ihn bleibt eine sonst passende Relation
NotAnalyzed(PENDING), ein bereits unpassender Werttyp DISALLOWED.

<a id="definiertheit-von-funktionssignaturen"></a>
## Definiertheit von Funktionssignaturen

META-006/014 und SEM-005/010: FunctionParameter erhält mandatory als dritten
Parameter, FunctionSymbol returnMandatory als vierten. Die Syntaxwerte
FunctionParameterSyntax und FunctionDeclarationSyntax erhalten entsprechende
Booleanwerte. Sie bewahren direktes MANDATORY; gebundene Signaturen
berücksichtigen zusätzlich die benannte Domäne und ihre Basen. Namen der
formalen Parameter bleiben dokumentarisch. Übersetzungen vergleichen die
Definiertheitsregeln zusammen mit den gebundenen Typen.

UNDEFINED gehört ohne MANDATORY zum Wertebereich (2.3 §2.8; 2.4 §3.8).
Zuweisungsabfragen zu reinen Typen erkennen den undefinierten Wert; erforderliche
Deklarationen besitzen ihre eigene mandatory-Eigenschaft. Ein explizit
undefinierter aktueller Parameter erfüllt keine MANDATORY-Signatur. Das ist
keine Ausführung der Funktion und keine allgemeine Datenvalidierung.

<a id="gemeinsame-format-relationen"></a>
## Gemeinsame FORMAT-Relationen

META-006/014 und SEM-005/010/014: Zuweisung und Erweiterung formatierter Typen
verwenden die gebundenen Strukturen, Komponenten und Grenzen. Eine Erweiterung
erhält das geerbte Layout als Präfix, spezialisiert seine Komponenten und darf
Grenzen einschränken. Erweiterte Attribute behalten ihre Basisidentität für
diese Prüfung. Die öffentliche Typabfrage verwendet dieselbe Regel wie der
Compiler. Die bestehende Auswertung gebundener FORMAT-Werte wird intern nach
interlis-model verschoben und gemeinsam verwendet; es entsteht kein zweiter
Formatter oder öffentlicher Einstieg.

<a id="vollständigkeit-numerischer-und-koordinaten-deklarationen"></a>
## Vollständigkeit numerischer und Koordinaten-Deklarationen

META-003/006/014 und SEM-005/010/014: NUMERIC ohne beide Grenzen ist in einer
Domänen- oder Attributdeklaration abstrakt. Ein Attribut muss dafür selbst als
ABSTRACT deklariert sein; ein abstrakter Eigentümer allein konkretisiert den Typ
nicht. Koordinaten mit unvollständigen Achsen erfordern ABSTRACT oder in 2.4 eine
zulässige GENERIC-Deklaration samt den gesonderten Kontextregeln. Abstrakte
Einheiten sind bei konkreten numerischen Grenzen unzulässig. Die Prüfung nutzt
die effektiven geerbten Typen und verändert keine gemeinsam benutzten Aliasse.
Unbeschränktes NUMERIC in Funktionssignaturen bleibt ein zulässiger formaler Typ.
Fehlende Einheiten-/Referenzziele ergeben ErrorType mit tatsächlichen Diagnosen.

Von ausdrücklich deklariertem NUMERIC bleiben die aus Ableitungsausdrücken
erschlossenen Typen von View-Attributen getrennt. Der vordefinierte LineCoord-
Platzhalter eigener Kurvensegmente wird erst im Linien-/Inspektionskontext auf
den tatsächlichen Vertextyp bezogen; er wird nicht durch diese Vollständigkeits-
prüfung als fehlerhafter konkreter Koordinatenbereich umgedeutet. Die zulässigen
Verwendungskontexte des Platzhalters bleiben eine eigene Prüfung.

META-015. Vordefinierte Boolean-Typen für Literale, logische/vergleichende
Ausdrücke und Standardfunktionssignaturen werden nach Sprachversion besessen.
Eine gemischte Closure darf keine 2.3-Herkunft für ein 2.4-Ergebnis erhalten.
Unbenutzte parallele Primitive werden nicht vorsorglich materialisiert.

<a id="gebundene-quell-metadaten"></a>
## Gebundene Quell-Metadaten

META-006/010/014/017: MetaAttributeSet.values() enthält Map<String, Value> statt
einer String-Map. Value(text, origin) bewahrt den Wert und seine Herkunft.
Documentation(text, ranges) enthält den aufbereiteten Text sowie die geordneten
ursprünglichen Kommentarbereiche. FunctionSymbol erhält Documentation explanation
als fünften Parameter. Alle Werte sind nullfrei, defensiv kopiert und immutable.
Identische doppelte Metaangaben sind erlaubt; widersprüchliche Angaben zu
einwertigen Schlüsseln sind weiterhin Quellfehler. Das explizite kompatible
Funktionsprofil erlaubt `fn.param` mehrfach an Funktionsdeklarationen. Alle
Vorkommen bleiben als `MetaAttributeSet.Entry(key,value)` in `entries()` in
Quellreihenfolge erhalten; `values(String key)` liefert eine unveränderliche
Liste aller Vorkommen dieses Schlüssels, bei Fehlen die leere Liste.
`values()` bleibt der kompatible Index auf das jeweils erste Vorkommen und
dessen Herkunft. Der bestehende Konstruktor `MetaAttributeSet(Map)` bleibt
verfügbar; der ergänzte Konstruktor `(Map,List<Entry>)` verlangt denselben
Erstvorkommensindex. Beide Sammlungen sind defensiv kopiert, nullfrei und
unveränderlich; spätere Mutationen einer Eingabesammlung ändern keine
veröffentlichten Werte. Vererbte/projizierte Werte bewahren auch die vollständige
Vorkommensliste und ihre ursprüngliche Herkunft. Andere Schlüssel und
`fn.param` ausserhalb von Funktionen behalten die bisherigen Konfliktregeln.

`PlausibilityConstraint` bewahrt tatsächlichen Prozentsatz und Operator;
`SetConstraint.where()` ist der gebundene individuelle Filter und `condition()`
die anschliessende Mengenbedingung. `SetConstraintScope.GLOBAL` bezeichnet den
konzeptionell globalen Geltungsbereich, auch über einen Transfer hinaus. BASKET
begrenzt ihn in 2.4 ausdrücklich auf einen Behälter. Es gibt keine erfundene
LOCAL-SET-Variante. Spätere Validierung berichtet ihre verfügbare Prüfmenge,
ohne die fachliche Bedeutung von GLOBAL zu verkleinern.

Für SET-Bedingungen in 2.3 und 2.4 gilt nach dem Nutzerentscheid vom 30.10.2026:
Der Hauptausdruck besitzt kein einzelnes Kontextobjekt. Gewöhnliche Attribut-,
Rollen- und THIS-Werte sind darin auch als verschachtelte Funktionsargumente
unzulässig; der WHERE-Filter bleibt objektbezogen. Der gemeinsame Pfadbinder
liefert für solche Zugriffe `ILI-SEM-0008` mit Namensbereich, Revision, Segment
und Grund sowie einen ErrorExpression/ErrorType. Übergeordnete Ausdrücke
erhalten ERROR ohne zusätzliche Typfehler aus bereits fehlerhaften Operanden.
ALL, explizite Klassen-/Attributpfadkonstanten, Laufzeitparameter und zulässige
Funktionsaufrufe bleiben verfügbar. Geparste konstante TEXT-Pfade der
Flächenfunktionen binden weiterhin separat im tatsächlichen Mengenwurzeltyp;
sie stellen keinen direkten individuellen Objektwert dar. Externe
CONSTRAINTS-OF-Blöcke verwenden dieselbe Regel.
In 2.4 muss der Hauptausdruck zusätzlich mindestens einen tatsächlichen
Funktionsaufruf mit OBJECTS-Formalparameter enthalten (§3.12/PDF78,
NORM:2.4:3.12:p78:004). Ein entsprechender Aufruf allein in WHERE genügt nicht.
Ein ansonsten fehlerfreier Hauptausdruck ohne solchen Aufruf erhält
ILI-SEM-0005 und einen typisierten Fehler statt eines gültigen Schemas.
In 2.3 bleibt beispielsweise eine rein konstante SET-Bedingung zulässig.
Bereits fehlerhafte Ausdrücke erzeugen keine zusätzliche Meldung aus dieser
abhängigen Prüfung; Übersetzungsvergleiche melden keine Folgeunterschiede
aufgrund solcher Fehlerausdrücke.

Ebenfalls ausdrücklich bestätigt sind die bestehenden 2.4-Profile:
areAreas2 und areAreas3 besitzen beide OBJECTS OF ANYCLASS als erstes Argument;
die allgemeine Singleton-Zuweisung bleibt erhalten, während THIS im
SET-Hauptausdruck aufgrund der Kontextregel unzulässig ist. Beide Namen
bleiben in 2.3 unzulässig. Funktionen mit OBJECTS-Parametern bleiben in
MANDATORY-/Plausibilitätsbedingungen auch in 2.4 wie in ili2c zulässig
(bewusste Abweichung von §3.12/PDF77). Die TEXT-Pfadprüfung bleibt erhalten.

Vordefinierte OID-Domänen besitzen innerhalb eines Sprachprofils eine kanonische
Identität, `PredefinedOrigin` und keine künstliche Benutzermodell-Eigentümerschaft.
Vordefinierte Symbole besitzen den Owner `INTERLIS` je Sprachversion:
ein `ModelSymbol` mit `ModelKind.TYPE`, Sprache `en`, Issuer
`http://www.interlis.ch/`, Version `2005-06-16` und CONTRACTED in 2.3 bzw.
`2014-07-09` ohne CONTRACTED in 2.4. Seine `members()` enthalten die im
Snapshot materialisierten vordefinierten Deklarationen in Anhang-A-Reihenfolge;
die bisherige bedarfsabhängige Materialisierung wird dadurch nicht zur
Vollinventur unbenutzter Deklarationen. Deren Owner ist dieses vordefinierte
Modell; explizite `IMPORTS INTERLIS` besitzen sein kanonisches Ziel.
Qualifizierte Quellreferenzen binden auch ihr INTERLIS-Präfix an dieses Modell.
Es erhält keinen erfundenen Quellbereich und ist kein angefordertes Benutzer-
Rootmodell. Gleiche Namen aus 2.3 und 2.4 behalten verschiedene Identitäten.
Der Ownervertrag gilt für jede im Analyse-Snapshot materialisierte Sprache,
auch wenn deren Benutzermodell ausserhalb der angeforderten Closure liegt.
Die Sprachmenge der gewählten Closure wird dadurch nicht erweitert.
2.3 definiert ANYOID/I32OID/STANDARDOID/UUIDOID ohne ABSTRACT/EXTENDS (2.8.9,
Anhang A); 2.4 definiert NOOID, ANYOID(ABSTRACT) EXTENDS NOOID und die drei
konkreten Domänen EXTENDS ANYOID (3.8.9, Anhang A). Trägerwerte und Herkunft
bleiben typisiert öffentlich abfragbar. Die gesonderte OID-AS-Regel für abstrakte
Klassen/Themen wird nicht durch einen Domänenmodifikator ersetzt.

<a id="benannte-collection-elementtypen-nach-sprachversion"></a>
## Benannte Collection-Elementtypen nach Sprachversion

META-006/014: Ein einfach benannter BAG-/LIST-Elementtyp wird in 2.4 als
DomainReferenceSyntax gebunden. Das Ziel ist eine Domäne oder eine Struktur;
CollectionType erhält den tatsächlichen Elementtyp samt gebundenem Ziel.
DomainReferenceType und TypeRelations.resolveAliases erhalten die referenzierte
Domäne; Domain-Vererbung bleibt davon getrennt. In 2.3 sind solche Elemente
auf Strukturen beschränkt. ANYSTRUCTURE und explizite RESTRICTION behalten ihre
Strukturbindung in beiden Versionen. Eine Klasse ist kein Strukturelement.
Grundlage: 2.3 §2.6.1 S.32, 2.4 §3.6.1 S.40. Keine neue öffentliche Typvariante.

<a id="übersetzung-geometrischer-inspektionskontexte"></a>
## Übersetzung geometrischer Inspektionskontexte

META-007/010/014: ModelTranslation.correspondences enthält auch überprüfte
Kontextkopien eigener und vordefinierter Segmente, ihre Mitglieder/Constraints
sowie die erzeugten Inspektionsstrukturen. Kanonische Segmentdeklarationen
bleiben erhalten. Die Zuordnung verwendet die tatsächliche Kontextualisierungs-
map und explizite Rollen der erzeugten Wrapper; Anzeigenamen und synthetische
ID-Strings sind keine fachlichen Identitätsregeln. Unterschiedliche Vertextypen
behalten getrennte Kopien. Sämtliche veröffentlichten Links sind unveränderlich
und gehören zum selben Snapshot. Explizite Geometrietoleranzen werden anhand
ihres exakten Werts verglichen, nicht anhand der Dezimalschreibweise.

<a id="einheitliche-bindung-vordefinierter-namen"></a>
## Einheitliche Bindung vordefinierter Namen

META-006/014/015 und SEM-008: Qualifizierte INTERLIS-Namen benötigen keinen
Import; einfache Namen dieser Elemente benötigen IMPORTS UNQUALIFIED INTERLIS
(2.3 §2.5.1 S.28, 2.4 §3.5.1 S.34). Primitive Typschlüsselwörter bleiben davon
unabhängige Syntax. Vorhandene Fabriken veröffentlichen dieselben kanonischen
Ziele bei qualifiziertem und unqualifiziertem Zugriff. Quellreferenzen erhalten
ihre ursprünglichen Namen, UTF-16-Bereiche und Revisionen. Keine neue API.

<a id="namen-von-konsistenzbedingungen"></a>
## Namen von Konsistenzbedingungen

META-006/011/014: Benannte Konsistenzbedingungen in 2.4 besitzen innerhalb
ihrer deklarierenden Klasse beziehungsweise ihres Wertebereichs eindeutige
Namen (3.12 S.77). Die Constraintfamilien teilen diesen Namensraum; unbenannte
Bedingungen benötigen keinen künstlich öffentlichen Namen. Der vorhandene
ConstraintSymbol bewahrt den tatsächlichen Namen und seinen Quellbereich,
auch für CONSTRAINTS OF. Struktur-, Assoziations- und Viewkörper verwenden
dieselbe direkte Namensprüfung wie Klassen.

<a id="numerische-unterteilungen-und-exakte-faktorbedingungen"></a>
## numerische Unterteilungen und exakte Faktorbedingungen

META-006/008/009/011/014/016 und SEM-003/005/010/011/014/017/019:
`AttributeSymbol.subdivision()` und `continuous()` behalten die tatsächlichen
Quellmarker. SUBDIVISION verlangt einen skalaren numerischen Typ und ein
unmittelbar vorausgehendes numerisches Attribut in der effektiven
Attributreihenfolge. Rollen sind keine Vorgängerattribute. Der Vorgänger muss
ganzzahlige Genauigkeit besitzen; ganzzahlige Endpunktwerte allein machen
beispielsweise `0.0..23.0` nicht zu einem ganzzahligen Typ. Die Unterteilung
hat einen nichtnegativen Bereich. Abstrakte, noch unbestimmte numerische
Grenzen erhalten keine erfundenen Werte und werden bei Konkretisierung geprüft.
Aliase beziehungsweise benannte Domainbezüge werden mit der bestehenden
Typauflösung aufgelöst; Collections werden nicht zu skalaren Typen umgedeutet.

CONTINUOUS verlangt SUBDIVISION und einen CIRCULAR-Typ. Der Parser meldet
fehlendes SUBDIVISION als `ILI-SYN-0002` in Phase PARSING am folgenden
Attributnamen. Der wiederhergestellte Syntaxbaum behält beide tatsächlichen
Marker; Editor-/Strict-Modus und Deklarationsfragmente verwenden dieselbe
Prüfung mit Original-UTF-16-Bereichen und Revisionen. Vollständiger
Fragmentverbrauch ist von Fehlerfreiheit getrennt. Die Recovery-Grammatik
bleibt erhalten; ihr breiterer Erkennungsraum ist keine Sprachfreigabe.

Die Kreisperiode ist Maximalwert minus Minimalwert plus der letzten
signifikanten Einheit des Maximalwertes: `0..59` und `0.000..59.999` ergeben
jeweils 60. Dies folgt aus 2.3 §2.8.5/PDF41 und §2.8.7/PDF44 bzw.
2.4 §3.8.5/PDF52 und §3.8.7/PDF55; der reine Abstand 59 wäre falsch.
Bei bestimmbaren Faktoren muss diese Periode dem Faktor von Vorgänger- zu
Unterteilungseinheit entsprechen. Abgeleitete und zusammengesetzte Einheiten,
Multiplikation und Division verwenden exakte rationale Zahlen; keine
Double-Werte und keine gerundete Dezimaldivision. Gemeinsame Abhängigkeiten
und symbolische PI-/LNBASE-Terme werden vor der Berechnung gekürzt.
EXTENDS einer abstrakten Einheit begründet keinen Umrechnungsfaktor.
Bei einer noch abstrakten Vorgängereinheit prüft die gemeinsame numerische
Einheitenrelation die zulässige Spezialisierung. Eine passende Einheit bleibt
bis zur Konkretisierung legal aufgeschoben; eine andere Dimension bleibt
ein Fehler. So darf beispielsweise TIME später zu Stunden konkretisiert
werden, während bereits eine Minutenunterteilung definiert ist.

Eine FUNCTION-Umrechnung wird nie ausgeführt. Ein gemeinsamer solcher
Einheitenstamm kann trotzdem einen bekannten relativen Faktor tragen, etwa
`h=60[u]` gegenüber `u`. Fehlt ein bestimmbarer Faktor, etwa bei einheitenlosen
Attributen oder einer verbleibenden FUNCTION-Umrechnung, gilt das ausdrücklich
freigegebene Profil aus [Compilervertrag](compiler-and-model.md): nur nachweisbare Faktorwidersprüche werden
abgelehnt. Bekannte inkompatible Einheiten bleiben Fehler. PI/LNBASE bleiben
symbolisch: Ein verbleibender einzelner transzendenter Faktor kann keiner
endlichen rationalen Periode entsprechen; eine allgemeine algebraische
Unabhängigkeit beider Konstanten wird nicht vorausgesetzt. Eine fehlende
Einheitenbindung ist weiterhin ein Bindungsfehler und kein erlaubter
unbestimmter Faktor.

Die Prüfung verwendet die endgültigen effektiven Mitglieder und Typen und
prüft geerbte Unterteilungen im Kontext der Erweiterung erneut. Diagnosen
`ILI-SEM-0010` unterscheiden fehlenden Vorgänger, nichtnumerische Typen, nichtganzzahligen Vorgänger, negative Grenzen, fehlendes CIRCULAR und
Faktorwidersprüche. Bei einem nur in der Erweiterung ungültigen geerbten
Element liegt die Diagnose am Benutzerkontext. Bereits fehlerhafte Typen
erzeugen keine zusätzliche Ersatzdiagnose. Dieselbe unverändert geerbte
Unterteilungsbeziehung wird einmal an ihrer ursprünglichen Deklaration
diagnostiziert; ein anderer effektiver Vorgänger begründet einen neuen
Prüfkontext. Quellfehler verhindern das Schema;
Abbruch oder ausgeschöpftes semantisches Arbeitsbudget bleiben kontrollierte
Ressourcenabbrüche. Iterative Einheitenprüfung und begrenzte Ganzzahlarbeit
gelten auch für tiefe beziehungsweise sehr grosse Faktorabhängigkeiten.
Vor der Periodenbildung wird zudem die erforderliche Dezimalausrichtung
belastet, damit ein erhöhtes lexikalisches Exponentenbudget weder eine
unbegrenzte Ganzzahlallokation noch einen internen Arithmetikfehler auslöst.

Die normativ ausdrücklich optionale Referenzsystem-Konsistenzprüfung für
Unterteilungen wird nicht zu einer zusätzlichen Compilerpflicht erklärt
(2.3 §2.6.1/PDF31, 2.4 §3.6.1/PDF38–39). Die vorhandenen öffentlichen Typ-,
Einheiten-, Herkunfts-, Reihenfolge- und Formatwerte bleiben nullfrei,
unveränderlich und snapshotgebunden. Es entsteht keine neue öffentliche API.

Fehlende deklarierte Umrechenbarkeit ist kein Beweis eines Dimensionsfehlers.
Zwei konkrete Einheiten mit demselben abstrakten TIME-Vorfahren ohne
Umrechnungsdefinition sowie vollständig unbestimmte Einheiten behalten einen
unbekannten Faktor und sind im freigegebenen Unterteilungsprofil zulässig.
Eine exakte Dimensionszerlegung kann dagegen bekannte widersprechende
Grundgrössen, auch in Produkten und Quotienten, nachweisen. Unbestimmte
Grundgrössen bleiben symbolisch und dürfen nur bei identischer Bindung gekürzt
werden. Gleiche vordefinierte abstrakte Grössen beider Sprachversionen haben
dieselbe Dimension; ihre Symbolidentitäten bleiben verschieden. Diese
Dimensionsaussage erteilt keine Umrechnungserlaubnis: Die bestehende öffentliche
Unit-Relation bleibt ohne deklarierten Umrechnungspfad DISALLOWED.

Overlaywerte verwenden Origin.OverlayOrigin(String overlayId,int priority,
Optional<SourceRange> range) und ModelTranslation.origin:Optional<Origin>.
Die bisherige fünfparametrige ModelTranslation-Konstruktion bleibt erhalten.
Die Konfigurationsherkunft kann ausserhalb des Modell-SourceSet liegen und
ist keine erfundene Modelltextstelle. Symbol-/Typidentitäten und deklarierte
Namen bleiben erhalten; öffentliche Metadaten enthalten wirksame Overlaywerte
vor allen unveränderten Quellvorkommen ([Compilervertrag](compiler-and-model.md)). Geprüfte externe
Translationen erhalten dieselben Korrespondenzen und Originalziele wie
Quelltranslationen; das Schema bleibt unveränderlich und gategebunden.

<a id="gemeinsame-geometriebindung"></a>
## Gemeinsame Geometriebindung

Der effektive Geometrietyp wird vor Ausdrucksbindung hergestellt. Compilerprüfung und
öffentliche Erweiterungsabfragen verwenden dieselbe Regel; Zuweisungen behalten
ihre eigene Verträglichkeit. Die versionierte Toleranzdarstellung und die
Kontextualisierung eigener Segmente stehen in [Modellvertrag](compiler-and-model.md). Ein Sprachfehler bei
Präzision, Vollständigkeit oder Erweiterung ergibt eine strukturierte
Quelldiagnose; eine alte Implementierungssperre darf keine tatsächlich
implementierten eigenen Segmente mehr sperren. Offene Referenz-/Normentscheidungen
bleiben in den [Normentscheidungen](norm-decisions.md) sichtbar und dürfen nicht automatisch durch
Erwartungsänderungen erledigt werden.

<a id="signatur-verantwortung-und-geerbte-parameterwerte"></a>
## Signatur-Verantwortung und geerbte Parameterwerte

Die Prüfung verwendet die gemeinsame Bindung von Metaobjekten, Parametern und Typen für
Zeichenregeln. Der vorhandene GraphicBuilder prüft nach der vollständigen
Parameter-/Override-Bindung die Verantwortungsregeln aus [Modellvertrag](compiler-and-model.md).
Quellfehler nennen fehlende Parameter, unpassende Metaobjektklassen oder
unverträgliche Werte. Geerbte Werte und ihre Quellen werden nicht mutiert.
Die Prüfung ist statisch; sie führt weder Zeichenregeln noch Bedingungen aus.

Modellkopfbindung verwendet ModelOptions aus [Modellvertrag](compiler-and-model.md). Der gepinnte
IANA-Registerstand prüft CHARSET ohne Abhängigkeit von JVM-Hostcodecs oder Netz.
Ungültige Namen/Namensräume liefern MODEL_CHARSET/MODEL_XMLNS. Übersetzungen
prüfen gleichen Inkrementaltransferumfang und semantisch gleiche Zeichenvorräte;
CHARSET-Aliasse werden über das Register verglichen. TRANSLATION_XMLNS markiert
widersprechende explizite Transfernamensräume. Die effektive Namespace-Bindung
folgt den tatsächlich gebundenen Originalzielen, auch transitiv und unabhängig
von Quellenreihenfolge. Der gebundene Strukturvergleich interpretiert diese
Optionen nicht nochmals aus Text. Der 2.4-CONTRACTED-Marker wird nicht als semantischer
Übersetzungsunterschied gewertet.

Datums- und Zeitprofile (SYN-004/013/015, SEM-005/010/014, META-003/014): DATE,
TIMEOFDAY und DATETIME sind 2.4-Syntax; die XML-Domänen bleiben in beiden
Versionen verfügbar. TYPE MODEL enthält die vorgesehenen Deklarationsfamilien;
SYMBOLOGY benötigt nur in 2.3 CONTRACTED. Dokumentarische Familienempfehlungen
für REFSYSTEM/SYMBOLOGY werden nicht als zusätzliche Quellfehler erzwungen.
Wiederholte Properties liefern ILI-SYN-0007 am zweiten Token; Warnungsablehnung
verwendet das gemeinsame Gate. EXTENDED und explizites EXTENDS am selben
Klassen-/Strukturkopf sind unzulässig. Fehlende Attributtypen nach Parser-Recovery
behalten die ursprüngliche Syntaxursache als ErrorType; sie erzeugen keine
Implementierungssperre. SyntaxProfileSafetyTest und ModelKindContractTest
prüfen diese Regeln.

<a id="compiler-einheitenrelationen-und-domäneneinschränkungen"></a>
## Einheitenrelationen und Domäneneinschränkungen

META-003/006/009/011/014/016, SEM-005/010/014, TEST-011/012.
`TypeRelations.compareUnits` liefert den vorgesehenen `QueryResult<UnitRelation>`:
beide Snapshotreferenzen werden geprüft, unbekannte IDs und fremde Symbolarten
bleiben Unresolved (`NOT_A_UNIT` bei falscher Art). Gebundene Ableitungen und
zusammengesetzte Produkte/Quotienten werden auf gemeinsame konkrete Ziele
bezogen. Eine abstrakte gemeinsame EXTENDS-Basis bedeutet keine numerische
Umrechnung. Zyklen, fehlende Komponenten und ungültige Faktoren liefern keine
scheinbar bekannte Verträglichkeit. Externe Umrechnungsfunktionen werden nur
strukturell berücksichtigt, weder ausgeführt noch numerisch approximiert.
Compiler und Query verwenden dieselbe reine Relation, auch für numerische
Referenzsysteme. Umrechenbarkeit erlaubt keinen Einheitenwechsel bei Erweiterung.

Numerische Erweiterungen von Domänen und Attributen schränken Grenzen ein und
bewahren in 2.4 die Nachkommastellen. Öffentliche canExtend-Queries verwenden diese
Regel ebenfalls. Zuweisung bleibt davon getrennt. Syntax erhält auch verkehrte
Grenzen unverändert; NUMERIC_BOUNDS/NUMERIC_PRECISION erzeugen reguläre Quellfehler
mit ErrorType statt Exceptions oder Ersatzbereichen. Die strengere Modell-
NumericRange-Invariante bleibt erhalten. Weitere Skalierungs-/Abstraktions- und
Referenzsystemregeln bleiben gesondert zu prüfen.

MANDATORY wird im Domain-Syntaxmodifier und im effektiven DomainSymbol erhalten;
`DomainSymbol.mandatory()` liest den effektiven Flag. MANDATORY allein übernimmt
bei einer Erweiterung den gebundenen Basistyp; ohne Basis ist es ein Quellfehler.
Attribute und Parameter übernehmen den Pflichtflag verwendeter Domänen.
Aliastypen werden nicht mutiert. CLASS-Domänen behalten ihre gebundenen
Restriktionen und ihre typisierten CONSTRAINTS. resolveAliases/canAssign/
canExtend/areComparable bewahren Invalid und Pending-Ursachen; Aliaszyklen
werden über besuchte IDs begrenzt, nicht über eine willkürliche Länge von 64.

Numerische Präzision ist versionsabhängig: 2.3 §2.8.5 S.42 erlaubt Verfeinerung,
sofern gerundete Grenzen im Basisbereich bleiben (7.9949 ja, 7.9999 nein bei
Basismaximum 7.99). 2.4 §3.8.5 S.53 verlangt unveränderte Präzision.
`TypeRelations.canExtend(derived, base, context, language)` erhält daher einen
expliziten nullfreien LanguageVersion-Parameter; weder Quellnamen noch ein
zufällig gefundenes Modell bestimmen das Profil.
Die 2.3-Rundung verwendet exakte Dezimalwerte und HALF_UP; Tie-Details bleiben
in der Normprüfung separat sichtbar. Allgemeine Zuweisung ist unverändert.

<a id="compiler-normative-text-länge"></a>
## Normative TEXT-Länge

Textliteraltypen in 2.4 verwenden §3.8.1: Anzahl Zeichen mit kanonischer
Kombinationsklasse 0 nach kanonischer Zerlegung (NFD). Dies ist weder UTF-16-
Länge noch die Anzahl Unicode-Codepoints im ursprünglichen Text. Es wird nur
die Länge bestimmt; LiteralExpression.textValue(), Lexem und SourceRange bleiben
unverändert. Deklarierte maximale Länge muss positiv sein; Länge null eines
Literals bleibt möglich. CR/LF/TAB erfordern MTEXT, auch bei Unicode-Escapes.

Der portable Compiler verwendet gepinnte Unicode-17.0.0-Gewichtungsintervalle
und Hanguls kanonische Zerlegung, ohne Laufzeit-Normalizer und ohne neue
Abhängigkeit. `UnicodeWeightsTest` prüft die Tabellenwerte und das vollständige
explizite NFD-Orakel anhand der [gepinnten Unicode-Originale](../../testdata/reference/unicode/17.0.0/README.md).
Den Test startet `java build/InterlisJvmBuild.java build --module interlis-compiler --tests '.*UnicodeWeightsTest'`.
Klassen von kombinierenden Zeichen werden nicht aus der
allgemeinen Zeichenkategorie Mn/Mc abgeleitet. NFKD-Kompatibilitätszerlegungen
werden nicht angewendet. Bisheriges Codepoint-Längenverhalten in 2.3 bleibt
unverändert; dies erklärt keine vollständige 2.3-Unicode-Abnahme.

<a id="compiler-gebundene-strukturwerte-und-restriktionen"></a>
## Gebundene Strukturwerte und Restriktionen

`StructureValueType(id, origin, Optional<SymbolId> target, List<SymbolId> restrictions)`
ersetzt den einzelnen obligatorischen Zielwert in API 0.2. Leeres target bedeutet
ANYSTRUCTURE; ungelöste Namen erzeugen ErrorType. Restriktionen sind unveränderlich,
nullfrei und in Quellreihenfolge gebunden. targetRoots() liefert die eingeschränkten
Ziele beziehungsweise das Primärziel. StructureValueTypeSyntax erhält die Original-
Namen und UTF-16-Bereiche; es gibt keine künstliche universelle Strukturdeklaration.

Ein konkreter lauflokaler StructureBindings-Helfer verwendet den Aufbauzustand des
GraphBuilder. Compiler und TypeRelations.canAssign verwenden dieselbe Relation:
Strukturelemente passen zu ihren erlaubten Wurzeln; im FUNCTION_ARGUMENT-Kontext
passen auch Objekte, deren Klasse die geforderte Struktur erweitert. ANYSTRUCTURE
akzeptiert dort Strukturelemente und Einzelobjekte, keine Objektmengen oder Zahlen.
Normale eingebettete Wertzuweisung behält ihre eigene Objekt-/Strukturgrenze.
BAG kann bei Einschränkung zu LIST werden; die umgekehrte Richtung ist unzulässig.
Kardinalität und Elementrestriktionen werden gemeinsam geprüft.

Pflichtige beliebige Strukturen beziehungsweise Typen ohne verfügbare konkrete
Struktur verlangen ein abstraktes Attribut. Konkrete Unterstrukturen anderer Themen
werden nur über explizite passende Wurzeln berücksichtigt. Konkrete Klassen und
Strukturen dürfen keine effektiven abstrakten Attribute behalten. Normbezug:
2.3 §2.5.3/2.6.4/2.14; 2.4 §3.5.3/3.6.4/3.14. Nicht gemeinsame Mitglieder von
Alternativen sind über einen gemeinsamen Pfad nicht statisch ansprechbar.

Inspektionen, Kontextualisierung, Übersetzungen, generische Verwendungen, Diagramm-
labels und öffentliche Queries erhalten Grundziel und Restriktionen. Vorhandene
Konstruktorverbraucher und Tests werden direkt angepasst. Relevante Vererbungs-
und Aliasketten verwenden besuchte IDs statt stiller Abschneidung bei 16/32/64;
Abbruch und Arbeitsbudget bleiben aktiv. Keine neue Pass-/Plugin-Infrastruktur.

Die Erweiterungsprüfung für Domänen, Attributüberschreibungen und öffentliche
Queries verwendet denselben Typvergleich. Collections prüfen Elemente rekursiv
als Erweiterung, nicht als Wertzuweisung. Eigene numerische und Koordinaten-
Inline-Typen erhalten vererbte Einheiten/Referenzen/Richtungen/Rotation vor
der Ausdrucksbindung. Aliasziele bleiben unverändert. TEXT-Längen verwenden
dieselbe NFD-Regel in 2.3 und 2.4 ([Modellvertrag](compiler-and-model.md)). SEM-005/010/014, TEST-011/012.

SEM-005/010/014/017 und TEST-011/012. Vergleichsoperatoren werden nach
Operandfamilie gemeinsam mit den öffentlichen Queries geprüft. Enum-/FORMAT-
Konstanten werden bei konkretem Typkontext gebunden; Enumreferenzen erhalten
die tatsächlichen Ziele und Originalbereiche. Auch Inline-Enums veröffentlichen
ihre Elementidentitäten. Geerbte Grafikwerte werden geprüft, ohne gemeinsam
verwendete Ursprungsausdrücke umzuschreiben. Enumfunktionen prüfen gleiche
Enumidentitäten und zulässige Knoten. Geometriefunktionen prüfen das Profil und
die tatsächliche Familie. Offene kontextabhängige Builtins behalten ihr Gate.

<a id="funktionskontext-und-konstanteneinheiten"></a>
## Funktionskontext und Konstanteneinheiten

SEM-005/010/014/020: Numerische Konstanteneinheiten werden im Aufrufkontext
gebunden; unbekannte oder abstrakte Einheiten sind Quellfehler. Parsebare
Stringpfade verwenden den gemeinsamen Pfadbinder. Ein statisch bekannter
Wurzeltyp erfordert eine vollständige Bindung; wertabhängige Wurzeln behalten
strukturierte Pfade mit erforderlichem Ergebnistyp. Übersetzungsvergleiche von
Aufrufen prüfen gebundene Funktionsziele und Pfadreferenzen. Funktionsausführung
ist davon nicht umfasst.

Die abhängigen ATTRIBUTE-Parameter werden mit den tatsächlichen Argumenten
geprüft (einschliesslich benutzerdefinierter Funktionen). Konstanten werden
im tatsächlichen OF-Kontext gebunden, auch bei verketteten oder späteren
Argumentabhängigkeiten; die veröffentlichte Reihenfolge bleibt die
Quellreihenfolge. Abhängige ATTRIBUTE-Ergebnistypen werden pro erfolgreichem
Aufruf mit bindbarem Owner gebunden; formale Deklarationen bleiben unverändert.
Bei UNDEFINED oder unbeschränkten Objektwurzeln bleibt der legale symbolische
Argumentbezug zusammen mit Funktionsziel und tatsächlichen Aufrufkindern
erhalten ([Modellvertrag](compiler-and-model.md), SEM-006); RESOLVED verspricht keinen Laufzeitwert.
Ohne konkreten
Aufrufkontext liefert die öffentliche Zuweisungsabfrage für passende Werttypen
mit noch abhängiger Eigentümeridentität NotAnalyzed(PENDING); unpassende
Werttypen bleiben DISALLOWED. areAreas und die 2.4-WithoutGaps-Variante prüfen
SurfaceBag/SurfaceAttr gemeinsam; UNDEFINED bei SurfaceBag bindet SurfaceAttr
an die Objektklasse. Stringvarianten verlangen konstante, vollständig geparste
Pfade und behalten zusätzliche ArgumentPath-Bindungen im veröffentlichten Schema.
Strukturmengen dürfen in diesem Pfadkontext durchlaufen werden.

convertUnit verwendet die gebundenen Umrechnungsbeziehungen ([Modellvertrag](compiler-and-model.md));
Zielattribute, deklarierte Funktionsparameter und Vergleichsoperanden liefern
die Zieleinheit. Einheitenlose Zahlenkonstanten übernehmen den numerischen
Verwendungskontext. Ein unbeschränkter NUMERIC-Parameter legt keine neue
einheitenlose Zieleinheit fest. Werte und Umrechnungsfaktoren werden nicht
ausgeführt. Fremde konkrete Einheiten ohne gemeinsame Umrechnungswurzel
sind unzulässig, auch wenn beide dieselbe abstrakte Dimension erweitern.

Normentscheidung zum 2.4-Standardmodell: §3.14 deklariert areAreas2 mit
OBJECTS und zeigt ausdrücklich ALL; Anhang A enthält OBJECT bei areAreas2
und zusätzlich areAreas3 mit OBJECTS. Das Projekt folgt für areAreas2 dem
Funktionskapitel und erhält areAreas3 als dortigen Mengenalias.
areAreasWithoutGaps/areAreasWithoutGaps2 sind erst in 2.4 verfügbar.
Diese Entscheidung ändert keine eingefrorene Referenzerwartung.
Der Nutzer hat dieses bestehende Mengensignaturprofil am 30.10.2026
ausdrücklich bestätigt. Ebenfalls bestätigt: OBJECTS-Parameterfunktionen
bleiben in 2.4-Einzelobjektbedingungen zulässig wie in ili2c. Der neu bestätigte
SET-Kontext unterscheidet dagegen den individuellen WHERE-Filter vom
Hauptausdruck ohne individuelles Objekt; [Modellvertrag](compiler-and-model.md) beschreibt Fehlerausdrücke,
Diagnostik, Konstanten und separat gebundene TEXT-Pfade.

Definiertheit. Allgemeine UNDEFINED-Zuweisung und Funktionsargumente verwenden
dieselbe Typregel. Eine Textkonstante kanonischer Länge null gilt gemäss
2.3 §2.8.1 / 2.4 §3.8.1 ebenfalls als undefiniert. Ungelöste oder fehlerhafte
Zieltypen werden dadurch nicht passend. MANDATORY-Signaturparameter lehnen
explizit undefinierte Werte ab; die Signaturpflicht bleibt öffentlich erhalten.
Compilezeit-Pfadparameter von areAreas2/3/WithoutGaps2 verlangen weiterhin
gültige konstante Pfade, abgesehen vom ausdrücklich erlaubten SurfaceBag-
UNDEFINED. Es entsteht kein Auswerter für Laufzeitargumente.

FORMAT. Compiler und öffentliche canAssign/canExtend-Abfragen verwenden
gemeinsame Layout-/Komponenten-/Bereichsregeln. Präfixerweiterungen berücksichtigen
die gebundenen überschriebenen Attribute; ein gleicher einfacher Attributname
allein genügt nicht. Fehlende explizite Grenzen werden aus den Komponenten und
verschachtelten/inherited FORMAT-Grenzen ermittelt. Ungültige FORMAT-Bindungen
veröffentlichen ErrorType mit tatsächlichen Diagnose-IDs, keine scheinbar gültige
Relation. Der Compiler besitzt keine pauschale FORMAT-Ausnahme für Overrides.

Numerische Vollständigkeit (2.3 §2.8/2.8.5/2.8.8; 2.4 §3.8/3.8.5/3.8.8):
Prüfungen unterscheiden formale Funktionssignaturen von Domänen und Attributen.
Die Prüfung erfolgt nach früher Typauflösung und Übernahme geerbter Eigenschaften.
Numerische Referenzsystemangaben auf generische Koordinaten dürfen ohne
ROTATION im generischen Bereich offen bleiben (INTERLIS 2.4 §3.8.8, S.57–58).
Ihre Verwendung zählt auch indirekt über numerische Typen und Koordinatenachsen
als generische Verwendung. Wirksame konkrete CONTEXT-Alternativen werden mit
demselben Referenzsystemvertrag auf Achse, ROTATION und Einheit geprüft; die
veröffentlichte Referenz behält dabei ihre generische Zielidentität.

Die Erlaubnis generischer Koordinaten in 2.4 ersetzt weder deren Dimensionen-
noch CONTEXT-/DEFERRED-Prüfung. Fehlerhafte Zielbindungen bleiben von legalen
abstrakten Typen unterscheidbar; kein gültiges Schema entsteht aus Quellfehlern.

SYN-002/004/013 und META-015. Lexikalische Klassifikation und Prüfung
kontextabhängiger Wörter gelten auch bei Fragmenten und Rename. Ein in 2.3
nicht reserviertes neues Wort kann eine benannte Deklaration bezeichnen;
fehlende Bindungen bleiben gewöhnliche Quellfehler. Das Ausdrücken einer
2.4-Konstruktion in 2.3 wird dadurch nicht erlaubt. Boolean-Herkünfte bleiben
in gemischten Closures nach Besitzer/Sprachversion getrennt.

META-006/010/014/017 und SEM-010/014/017. Quell-Metaattribute werden aus der
strukturierten Syntax an die deklarierte Identität gebunden. Eine erneute globale
Textsuche bildet keine Metadaten- oder Übersetzungssemantik. CRS-Metaangaben
werden beim Übersetzungsvergleich an den korrespondierenden Koordinaten-
Deklarationen verglichen. Eine Zeichenfolge in gewöhnlichen Kommentaren,
Strings oder Funktionserläuterungen ist keine CRS-Angabe. Beliebige lokalisierte
Beschriftungs-Metaattribute müssen in Übersetzungen nicht textgleich sein.
Hover übernimmt gebundene Dokumentation und Funktionserläuterungen.
Externe Overlayeingaben und deren Auflösung bleiben Lieferung F.

Der Übersetzungsvergleich verwendet die zugeordneten Deklarationen der gebundenen
Closure. Er prüft Eigenschaften aller repräsentierten Typvarianten, die Struktur
der Enumeltern und geschlossenen Teilbäume, FORMAT-Komponentenidentitäten,
Collectionart/-kardinalität, Modifikatoren und vollständige Constraintdefinitionen.
Pfade und Funktionsaufrufe vergleichen tatsächliche Zielidentitäten. Ein globaler
Namensersatz, erneutes Lexen des Modelltexts oder tiefenbegrenzte Typformstrings
sind keine fachliche Vergleichsgrundlage. Domain-Constraints erhalten eigene
Korrespondenzen und benannte Constraints behalten ihren tatsächlichen Namensbereich.
Konstanten vergleichen interpretierte Werte; bei numerischen Typgrenzen bleibt
die deklarierte Genauigkeit zusätzlich relevant. Eine Funktionserläuterung darf
anders lauten, ihre strukturelle Präsenz bleibt erhalten (2.3 §2.5.1/2.14;
2.4 §3.5.1/3.14). Fehlende Implementierung oder ein fehlender Zieltyp ist keine
positive Übersetzungsgleichheit.

OID-Bindung verwendet dieselbe vordefinierte Identität in importierten
Funktionssignaturen und lokalen Attributen, getrennt nach 2.3/2.4. Jeder
Namensgebrauch wird unmittelbar aufgezeichnet, auch Funktionsparameter/-ergebnis
und verschachtelte Typverweise. Ein nachträglicher Attributscan ersetzt dies
nicht. Allgemeine OID-Zuweisungs-/Erweiterungsregeln und benannte primitive
Collectiontypen sind davon getrennt nachzuweisen.

<a id="bindung-benannter-bag-list-elemente"></a>
## Bindung benannter BAG-/LIST-Elemente

SEM-005/010/014: 2.4 löst einen einfachen benannten Collection-Elementtyp über
die gemeinsame Domain-/Strukturbindung auf. Imports, Navigation und Diagnose
verwenden dieselbe Quellreferenz wie ein skalares benanntes Attribut. 2.3 darf
dadurch keine Collection über einer skalaren Domäne akzeptieren. Explizite
Strukturrestriktionen dürfen keine Domänenziele erhalten. Abnahme: BAG und LIST
mit OID-/TEXT-/Numeric-Domänen, Domain-Vererbung, Strukturen, falschen Zielarten,
Imports in beiden Dateireihenfolgen und gültig–ungültig–gültig mit Fresh/Reuse.

<a id="geprüfte-kontextkorrespondenzen"></a>
## Geprüfte Kontextkorrespondenzen

SEM-005/008/010/014/017: Vor einer zusätzlichen Inspektionskorrespondenz werden
Quellpfad, OF-Restriktion, Besitzer und gebundener Geometrietyp verglichen. Nur
passende Kontexte ergänzen die bestehende Map; Konflikte oder zusammenfallende
Kopien sind unzulässig. Neu zugeordnete Deklarationen durchlaufen dieselben
Typ-/Referenz-/Ausdrucksprüfungen. Die Veröffentlichung erfolgt nach diesen
Prüfungen. Constraints vergleichen ihre Ausdrücke vor den daraus abgeleiteten
Referenzlisten, damit gültige Kontextziele bereits zugeordnet sind.
Abnahme: positive/negative Toleranzen, Inline-/Named-/Constraint-Inspektionen,
Flächen, eigene Segmente, zwei Vertextypen, öffentliche Links, Quellenreihenfolge
und Fresh/Reuse. Keine Geometrieberechnung oder zusätzliche öffentliche API.

<a id="gemeinsame-auswahl-von-quell--und-vordefinierten-namen"></a>
## Gemeinsame Auswahl von Quell- und vordefinierten Namen

SEM-005/008/010/014: Domänen, Strukturen, Objektbasen, Einheiten, FORMAT, OID AS
und Funktionen verwenden für vorhandene vordefinierte Ziele dieselbe Auswahl.
Lokale passende Deklarationen behalten ihre Identität. Eine mehrdeutige
Quellauflösung ist ein Bindungsfehler und darf nicht durch einen Builtin-Aufruf
ersetzt werden. Der indexbasierte Resolver erhält keine künstlichen Quell-
deklarationen; die vordefinierten Fabriken bleiben beim Compilerlauf.
Abnahme: beide Versionen, explizite und unqualifizierte Namen, fehlender Import,
primitive Schlüsselwörter, lokale Ziele, konkurrierende Userimports, öffentliche
Quellqueries, umgekehrte Dateireihenfolge und Importpolicy-Edits frisch/wiederverwendet.
Die Priorität eines einzelnen Userimports gegenüber einem gleichnamigen
INTERLIS-Import wird in dieser Teillieferung erhalten und separat normativ
geprüft; sie ist kein abgeschlossener Nachweis allgemeiner Namenskategorien.

<a id="mantissenprüfung-gemeinsamer-typgrenzen"></a>
## Mantissenprüfung gemeinsamer Typgrenzen

SEM-005/010/014 und SYN-004: Die Syntaxgrenzen von numerischen Domänen, Attributen,
Koordinatenachsen, numerischen OIDs und numerischen FORMAT-Komponenten werden
gemeinsam geprüft. Eine unzulässige Mantisse ist ein regulärer Quellsyntaxfehler,
kein Error durch fehlende Implementierung. Exakter Wert, Präzision und Lexem
gültiger Grenzen bleiben im veröffentlichten NumericRange erhalten. Konstanten
und Einheitenfaktoren werden nicht erneut als Typgrenze interpretiert.

<a id="direkte-constraintnamensräume"></a>
## Direkte Constraintnamensräume

SEM-005/008/010/014: Direkte 2.4-Constraints und CONSTRAINTS OF im selben Thema
werden nach ihrem gebundenen Ziel gemeinsam auf doppelte Namen geprüft.
Duplikate erhalten die Regel CONSTRAINT_NAME am tatsächlichen zweiten
Namensbereich. Unterschiedliche Ziele und Themenkontexte bleiben getrennt;
die Domainregel DOMAIN_CONSTRAINT_NAME bleibt erhalten. 2.3 erhält dadurch
keine zusätzliche Namenspflicht. Vererbte Constraintnamensräume und externe
Bedingungen in abgeleiteten Themen sind eine gesonderte offene Abnahme.
ConstraintNameContractTest prüft direkte Körper, unterschiedliche Familien,
externe Blöcke, öffentliche Quellqueries, Imports und Fresh/Reuse-Edits.

<a id="freigegebene-oid-kompatibilität"></a>
## freigegebene OID-Kompatibilität

Nutzerentscheidung vom 29.10.2026: `OID AS` und `BASKET OID AS` werden in
INTERLIS 2.3 auch ohne `CONTRACTED` angenommen. Dies ist eine ausdrückliche
Kompatibilitätsabweichung zur älteren Normvoraussetzung (2.3 §2.5.1–2.5.2),
entsprechend ili2c und verbreiteten Bestandsmodellen. Sie ändert weder die
OID-Domänenbindung noch Vererbung/Override, ANYOID-, NO-OID- oder
Sprachprofilregeln. Andere CONTRACTED-Voraussetzungen bleiben unverändert.
Die sechs betroffenen Originalfälle werden reguläre PASS-Fälle und behalten
zusätzliche gepinnte Positiv-/Negativkontrollen; die übrigen zwölf
Referenzausnahmen und offenen Normentscheidungen sind davon nicht freigegeben.

<a id="freigegebenes-type-model-kompatibilitätsprofil"></a>
## freigegebenes TYPE-MODEL-Kompatibilitätsprofil

Nutzerentscheidung vom 29.10.2026: `TYPE MODEL` darf in INTERLIS 2.3 und 2.4
neben Units, Domains, Funktionen und Linienformen auch Strukturen und abstrakte
Klassen deklarieren. Dies entspricht dem frisch geprüften ili2c-5.6.9-Verhalten
und ist das ausdrücklich gewählte Kompatibilitätsprofil zur engeren Aufzählung
in 2.3 §2.5.1 bzw. 2.4 §3.5.1. TOPIC, konkrete Klassen, Laufzeitparameter und
Metadatenbehälter bleiben dort unzulässig. Andere Modell- und Vererbungsregeln
werden nicht aufgehoben. Originalquellen und Suitepins bleiben unverändert.

<a id="freigegebene-numerische-kompatibilitätsprofile"></a>
## freigegebene numerische Kompatibilitätsprofile

Nutzerentscheidungen vom 29.10.2026, jeweils für 2.3 und 2.4:

- Skalierte Bereichsgrenzen behalten das bestehende kompatible Profil.
  Gleiche oder absteigende Exponenten sowie gemischte skalierte/unskalierte
  Grenzen bleiben zulässig, etwa `0.1e1..0.2e1`, `-0.2e2..0.3e1` und
  `0.1e1..20.0`. Dies weicht bewusst von der engeren Exponenten-/Paarvorgabe
  in 2.3 §2.8.5/PDF41–42 und 2.4 §3.8.5/PDF52 ab. Werteordnung,
  Genauigkeit und die bereits geltenden Mantissenprüfungen bleiben erhalten.
- Allgemeine Zahlenkonstanten und Einheitenfaktoren behalten die breitere
  Exponentialschreibweise, auch `1.0e1` und `0.01e1`. Der Compiler bewahrt
  ihren exakten Wert, ihr Lexem und ihre Präzision. Die engere Prüfung
  numerischer Typgrenzen wird nicht auf diese Kontexte übertragen. Die
  frischen ili2c-5.6.9-Gegenfälle lehnen diese beiden Schreibweisen ab;
  die Nutzerentscheidung bestätigt ausdrücklich unser bestehendes Profil.
- Kontinuierliche Unterteilungen ohne bestimmbaren Faktor bleiben zulässig,
  insbesondere ohne Einheiten oder mit lediglich beschriebener FUNCTION-
  Umrechnung. Nur nachweisbare Faktorwidersprüche werden abgelehnt.
  Numerischer Vorgänger, Ganzzahligkeit, nichtnegative Grenzen und CIRCULAR
  werden dennoch geprüft; bekannte numerische Faktoren werden exakt
  verglichen und FUNCTION-Erklärungen nicht ausgeführt. Der Unterschied
  zwischen unbestimmtem Faktor und fehlender/fehlerhafter Bindung bleibt
  erhalten. [Modellvertrag](compiler-and-model.md) regelt die öffentlichen Werte und Fehlerpfade.

Dies ändert keine Suite-Fall-ID, keine Originalquelle, keinen Suite-/Archivpin
und keine der 18 gesonderten Conformance-Erwartungen. Es erlaubt keine
pauschale numerische Normabweichung ausserhalb dieser ausdrücklich gewählten
Profile.

Die fortgeltende CIRCULAR-Prüfung deckt im Suite-Positivfall
`ili23.Attribute23Test.attributeComplete` zwei fehlende CIRCULAR-Marker auf.
Dieser konkrete Fall ergänzt die bisherigen 18 unveränderten Bewertungen als
19. strenge Referenzausnahme: unverändertes Original und Hash, exakt zwei
SEMANTIC_CHECKING-Diagnosen sowie eine erfolgreiche Kontrollquelle, die nur
die beiden Marker ergänzt. Das erfasst die Konsequenz der im bestätigten
Profil ausdrücklich erhaltenen CIRCULAR-Regel; eine gesonderte ausdrückliche
Nutzerfreigabe dieses einzelnen Fixture-Namens wird nicht behauptet. Die Suite-Klassifikation, Projektbewertung und Pins stehen im [Conformance-Profil](../../testdata/conformance/normative/expectations.json).

Im freigegebenen Unterteilungsprofil ist eine fehlende deklarierte
Umrechnungsroute von einem nachgewiesenen Dimensionswiderspruch zu unterscheiden.
Einheiten mit gleicher abstrakter Grundgrösse ohne numerische Definition und
vollständig unbestimmte Einheiten bleiben zulässig. Nur eine beweisbar
widersprechende Dimensionszerlegung oder ein bestimmbarer falscher Faktor
begründet SUBDIVISION_FACTOR. Die allgemeinen Zuweisungs-, Erweiterungs- und
Umrechnungsrelationen werden dadurch nicht erweitert ([Modellvertrag](compiler-and-model.md)).

Einfache Namen
verwenden im Topic dieselbe geerbte Mitgliedersuche wie lokale qualifizierte
Typbindungen. Lokale Deklarationen gehen geerbten vor; Topicnamen selbst
werden im umgebenden Modell aufgelöst, weil Topics keine Topics enthalten.
Geerbte Assoziationsbasen bleiben ursprüngliche Deklarationen, ihre Teilnehmer
werden im aktuellen Topic kontextualisiert. Die Quelle selbst deklarierter
LineCoord-Attribute benötigt ABSTRACT; eine spätere Inspektionskonkretisierung
ist keine Ausnahme vom Quellvertrag ([Modellvertrag](compiler-and-model.md)). Import-/Translations-Closures
behalten tatsächliche Korrespondenzen und geordnete Generic-/Metadaten-/Linienformziele.
Öffentliche Gegenfälle: `CrossModelSemanticClosureContractTest`; keine Runtimeausführung.

<a id="fachliche-semanticoverlays"></a>
## Fachliche SemanticOverlays

SEM-016, META-012, INC-007/016: SemanticOverlays enthält echte unveränderliche
TranslationOverlay-, CrsOverlay- und MetaAttributeOverlay-Daten. OverlaySource
nennt eine nichtleere externe Kennung, ganzzahlige Priorität und optional einen
revisionsgebundenen Bereich der Konfigurationsquelle. Target enthält einen
kanonischen QualifiedName und optional exakte Modellversion/SourceId. Es gibt
keine Namensalias- oder Latest-Substitution. Fehlende/mehrdeutige Ziele,
ungeeignete Zielarten und widersprüchliche gleichrangige Werte werden als
ILI-SEM-0011/BINDING diagnostiziert und verhindern das gemeinsame Gate.

Höhere Priorität überschreibt niedrigere; die Quelle bildet den Grundstand.
Gleichrangige identische Werte sind im wirksamen Wert/Index idempotent; ihre
getrennten Vorkommen und Herkunftsangaben bleiben in der öffentlichen Liste erhalten. Die öffentliche Metadatenliste
enthält zuerst wirksame externe Werte nach absteigender Priorität und dann
die unveränderten Quellvorkommen; ihr First-Occurrence-Index liefert den
wirksamen Wert. Herkunft bleibt Origin.OverlayOrigin(id,priority,range),
auch für verdrängte Vorkommen. Eingabelisten und Werte sind kopiert/nullfrei.

TranslationOverlay verknüpft zwei vorhandene Modelle. Das Original und seine
Imports werden Teil der Prüfclosure, ohne seinen Namensraum zu importieren.
Eine widersprechende ausdrückliche TRANSLATION OF-Deklaration ist ein Fehler.
Der vorhandene strukturelle Übersetzungsprüfer erzeugt dieselben geprüften
Korrespondenzen; ModelTranslation.origin hält die externe Herkunft. CRS setzt
die deklarationsgebundene CRS-Metainformation nur auf Koordinatentypen;
die gleichen Zielregeln gelten für den Schlüssel CRS in einem Metaoverlay.
Es findet keine Koordinatentransformation und keine externe CRS-Auflösung statt.

Fingerprints sind SHA-256 über eine versionierte längenpräfixierte UTF-16-
Inhaltsdarstellung sämtlicher Werte, Ziele, Qualifikatoren, Herkunft und
Overlayreihenfolge. Fachliche Bindungen gehören ausschliesslich dem Lauf.
Overlaywechsel ändern Analyse-/Schema-/Workspacefingerprints; Wiederverwendung
ist weiter optional und darf weder alte Ergebnisse mutieren noch Aliase/Imports
in nachfolgende Läufe übertragen. Budget und Abbruch gelten auch für die Bindung.
Ungültige Konfigurationen bleiben im Analyseergebnis sichtbar; globale
Overlayfehler sind im Compile-Gate relevant, auch ohne Modell-Quellbereich.

Der Compiler trennt CompilationFingerprint (SHA-256/compilation-v2, tatsächliche
Eingaben, kanonische Importstellenreihenfolge, unabhängig von AnalysisReuse)
von SnapshotId. Letztere enthält zusätzlich Parsemodus und die konkrete
Wiederverwendungsprovenienz, weil stabile Symbol-IDs innerhalb verschiedener
Graphen verschieden zugewiesen sein können. SchemaFingerprint (schema-v2)
verwendet die fachliche Eingabeidentität statt vorheriger Snapshot-/Symbol-IDs.
Cache-Eviction oder ein zusätzlicher Warm-Lauf ändern dadurch kein fachliches
Schemafingerprint; snapshotgebundene Referenzen bleiben strikt getrennt.

Fehlende oder mehrdeutige externe Originalmodelle bleiben ungeklärte
Resolutionseingaben (ILI-RES-0005/0006; MISSING_INPUT/AMBIGUOUS_INPUT), während
fehlende normale Deklarationsziele ungültige Konfiguration sind. Alle drei
globalen Diagnosearten bleiben im gemeinsamen Gate ohne erfundene Modellstelle
relevant; spätere Lieferung eines Originals erlaubt erneute normale Bindung.

`CompilationRequest.fingerprint()` verwendet dieselbe feldweise kanonische
Compileridentität wie die Engine (compilation-v3). Roots, Importstellen/-resultate/
-probleme, TranslationBindings und sämtliche ResolutionDiagnostics werden mit
Längen, Listenanzahlen und Optionalmarkern serialisiert. Record-/Listen-
`toString()` ist kein Identitätsformat; verschachtelte SourceIds und Versionswerte
dürfen keine Feldgrenzen verschieben. Die Identität bleibt unabhängig von Reuse
und Parsemodus; Schema-/Snapshotrollen bleiben getrennt.
