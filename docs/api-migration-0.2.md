# Migration der Vorab-API 0.1 → 0.2

Toolversion: `0.2.0-SNAPSHOT`; API-Version: `0.2`. Diagnose-, Wire- und
Diagrammformatversionen bleiben unverändert. Diese Version ist keine P3-Freigabe.

| Bisher | Neu | Anpassung |
|---|---|---|
| `FunctionParameter.typeName(): String` | `typeId(): TypeId` | Typ über den Graphen oder snapshotgebundene Typabfrage lesen. |
| `FunctionSymbol.returnType(): String` | `returnType(): TypeId` | Keine eigene Typnamenauswertung im Verbraucher. |
| Funktionssyntax mit Typnamenstrings | `TypeSyntax` für Parameter und Ergebnis | Originalbereiche und strukturierte Typvarianten benutzen. |
| `FunctionCallExpressionSyntax.functionName(): String` | `NameSyntax` | Anzeige über `text()`, Navigation über Segmente. |
| `SyntaxSnapshot(..., ParseMode, ...)` | `SyntaxSnapshot(..., ParseOptions, ...)` | Vollständige tatsächliche Parseoptionen übergeben; `mode()` bleibt verfügbar. |
| `SourceInventory(..., AnalysisBudget)` | `SourceInventory(..., Map<SourceId, SyntaxSnapshot>)` | Quellen vorab parsen; Quelle, Revision und Text müssen exakt übereinstimmen. |
| Nur aufgelöste `PathStep`-Varianten | Zusätzlich `PathStep.UnresolvedStep` | Exhaustive Switches ergänzen; fehlerhafte Pfade haben kein vorgetäuschtes `THIS`-Ziel. |
| Kein eigener Funktionsobjekttyp | `ObjectArgumentType` / `ObjectArgumentTypeSyntax` mit `objects` | `false` für Einzelobjekte, `true` für Mengen; Switches um `OBJECT_ARGUMENT` und `OBJECT_SET` ergänzen. Basis und geordnete Einschränkungen bleiben typisiert. |
| `ALL` als generische Konstante | `ObjectSetExpression` / `ObjectSetExpressionSyntax` | Gebundene Typreferenz bzw. optionale explizite Auswahl auswerten; kein Stringvergleich mit `ALL`. |
| Pfadsyntax als Namensliste | `PathExpressionSyntax.Segment` | `name()`, `associationAccess()`, `associationQualifier(): Optional<NameSyntax>`, `index(): Optional<PathIndex>` und vollständigen Schrittbereich verwenden; für Rename den Namensbereich. |
| Nur direkte semantische Rollenpfade | Zusätzlich `PathStep.AssociationAccessStep` | Beide Rollen, Assoziation, Kardinalität und Zwischentyp auswerten; `associationObject` unterscheidet `\rolle`. |
| Assoziationskardinalität verworfen | `AssociationDeclarationSyntax.cardinality()` | Optionaler exakter Wert; ohne Angabe bei einer Wurzel genau eine Beziehungsinstanz je Teilnehmerkombination, sonst geerbt. |
| `RoleSymbol.target()` / `targetOptional()` | `targets(): List<RoleTarget>` | Alle Alternativen und Restriktionen auswerten; `targetRoots()` liefert zulässige Hierarchiewurzeln. Keine Wahl des ersten Ziels. |
| `RoleDeclarationSyntax.target()` | `targets(): List<RoleDeclarationSyntax.Target>` | Alle Basis-/Restriktionsnamen samt Bereichen erhalten. |
| Assoziation ohne gebundene Basis | `AssociationSymbol.baseAssociation()` und `cardinality()` | Konstruktor um `Optional<SymbolId>` und effektive `Cardinality` ergänzen; eigene Rollen von `effectiveMembers` unterscheiden. |

Alle Werte bleiben nullfrei und unveränderlich. `TypeId` wird nur im zugehörigen
Graphen aufgelöst; öffentliche Queries verwenden `TypeRef(snapshotId, typeId)`.
Die bestehenden Fabriken `CompilerEngines.create()` und `Workspaces.create()`
sowie `SyntaxSnapshot`, `AnalysisSnapshot` und `CompiledSchema` bleiben erhalten.
Das SDK-Beispiel verwendet weiterhin diese Einstiege.

Fachliche Änderungen: Falsche Funktionsargumentanzahl und unverträgliche
Argumenttypen werden abgewiesen; der deklarierte Ergebnistyp bestimmt den
Aufrufausdruck. Die CONTRACTED-Pflicht gilt für 2.3, nicht für 2.4.
Unvollständige Views und noch nicht modellierte Deklarationsarten werden bis
zu ihrer vollständigen Umsetzung als Implementierungslücken ausgewiesen.
Frühere Annahme solcher Modelle war kein Nachweis eines nutzbaren Schemas.

Ausgangsrollenzugänge verwenden zusätzlich `PathStep.AssociationSourceStep` mit
Assoziation, Ausgangsrolle, Kardinalität, Ergebnistyp und Quellbereich. Es gibt
keine erfundene Gegenrolle. Exhaustive Switches um diese Variante ergänzen;
`rolle[Assoziation]` besitzt getrennte Referenzen auf Rolle und Assoziation.
Kardinalitäten von Zugangsschritten gelten je Ausgangsobjekt; `resultType`
bewahrt auch die Mengenwertigkeit früherer Schritte. N-stellige Zugänge dürfen
nicht anhand einer einzigen Rollenkardinalität als Singleton behandelt werden.

UNIQUE-/EXISTENCE-Pfade werden nun über `ExpressionId` referenziert:
`UniquenessConstraint.keys()`, `LocalUniqueness.prefix()/fields()`,
`ExistenceConstraint.source()` und `ExistenceTarget.path()`. Verbraucher lesen
über `SemanticGraph.expression(id)` den `PathExpression` mit `path()`,
`typeId()`, Quellbereich und Fehlerzustand. `LocalUniqueness` hat keinen separaten
Namen mehr; der Constraintname steht am umgebenden `UniquenessConstraint`.
Syntaxverbraucher verwenden `ExpressionSyntax`-Pfade, `localPrefix()` und
`basket()` statt `NameSyntax`-Schlüsseln und `localUniqueness()`-Boolean.
Alle Pfade und Zielreferenzen sind jetzt Syntaxkinder; Scope und Reihenfolge
bleiben explizit. Diese Änderung vervollständigt die laufende Vorab-API 0.2;
es gibt keine veraltete Parallelfassade.

Indexauswahl erweitert `PathExpressionSyntax.Segment` um `Optional<PathIndex>`
(vor dem Bereich); ohne Auswahl `Optional.empty()` übergeben. `PathIndex`
unterscheidet FIRST/LAST und eine exakte numerische Position. Im Modell folgt
`PathStep.IndexStep` auf den gebundenen `AttributeStep`; `resultType` ist die
selektierte Struktur oder Koordinatenachse im selben Snapshot. Exhaustive
Switches ergänzen. Rename verwendet weiterhin den Bereich des Attributnamens,
nie die Klammern oder den Index. Es entsteht keine zusätzliche Fassade.

Enum-Konstruktoren erhalten `finalSubtrees` statt eines blossen
Wurzel-Flags; `EnumerationType` zusätzlich `Optional<TypeId> baseType`.
`values()` enthält den effektiven Baum einschliesslich geerbter Werte.
`EnumerationValueSyntax` erhält `qualified`; Syntaxwerte bleiben deklariert.
`finalPart()` am Modell bleibt eine abgeleitete Abfrage der Wurzel.

`FormattedTypeSyntax` verwendet optionale Basisnamen und strukturierte
`Part`-Werte statt einer Formatnamensauswertung. `FormattedType` erhält
`predefined`, `baseDomain`, `structure` und effektive `parts` zusätzlich zu
Anzeigetext und lexikalischen Grenzen. Verbraucher benutzen die gebundenen
IDs; Formatnamen sind keine zweite fachliche Semantik. Geerbte Grenzen sind
über die Basiskette zu berücksichtigen.

`OidTypeSyntax.valueType()` enthält optional die Trägersyntax,
`OidType.valueType()` deren `TypeId`. Bei ANY ist der Wert leer.
Vordefinierte Domänen wie `INTERLIS.UUIDOID` sind synthetische Symbole im
Snapshot; sie besitzen keinen umbenennbaren Quellnamen. Ein unqualifiziertes
`UUIDOID` verlangt eine sichtbare Deklaration. Die genannten Änderungen
vervollständigen die noch unveröffentlichte API 0.2; keine Parallelfassade.

`oidRule(): Optional<String>` entfällt in `TopicSyntax`,
`ClassDeclarationSyntax`, `AssociationDeclarationSyntax` und `SymbolHeader`.
Syntaxverbraucher verwenden `oidRules(): List<OidRuleSyntax>` mit optionalem
Domänennamen und Regelbereich; Modellverbraucher `List<OidRule>` mit gebundener
Domänen-ID und Herkunft. `basket()` unterscheidet Basket- von Objektregeln.
Eine leere Domäne bedeutet explizites NO OID; eine leere Liste bedeutet, dass
keine Regel vorhanden ist. Der Modellheader enthält auch übernommene Regeln.
Bei Konstruktion ohne Regeln `List.of()` übergeben. Headertexte werden nicht
mehr durch Verbraucher ausgewertet.

`DecimalLiteral.precision().fractionalDigits()` bewahrt bei Exponentenschreibweise
jetzt die Nachkommastellen der Mantisse: `1.2e3` hat eine Mantissenstelle und
Exponent 3. Der frühere Wert 0 war ein Metadatenfehler. Exakter Zahlenwert und
Originallexem bleiben erhalten.

Die OID-/FORMAT-Folgekorrektur ändert keine Signatur. Bei geerbten FORMAT-Teilen
zeigt `attribute()` jetzt auf das wirksame Attribut der spezialisierten Struktur;
`origin()` bleibt der ursprüngliche Deklarationsort. Verbraucher dürfen diese
beiden Bezüge nicht gleichsetzen. Unbeschränkte numerische Komponenten sind in
abstrakten Formaten legal; Präzision ist erst nach Konkretisierung verfügbar.
`INTERLIS.NOOID` und die implizite NO-OID-Vererbung gelten ausschliesslich in 2.4.
Die frühere versionsübergreifende Annahme war falsch.

## Referenzattribute und NO OID (21. Oktober 2026)

`ReferenceType` erhält `Optional<SymbolId> target` und
`List<SymbolId> restrictions` statt eines einzelnen obligatorischen Zieles.
Konstruktoren übergeben `Optional.of(target), List.of()` für ein normales
Primärziel; `Optional.empty()` bezeichnet ANYCLASS. `targetRoots()` liefert
die zugelassenen Wurzeln, ohne Unterklassen zu enumerieren. Verbraucher dürfen
bei mehreren Restriktionen nicht nur das erste Ziel verwenden.
`ReferenceTypeSyntax` erhält entsprechend eine Liste von Einschränkungsnamen;
`children()` enthält auch deren Quellbereiche. Keine parallele Alt-Fassade.

2.4-Modelle, deren Referenzattribute oder Rollen auf eine Klasse ohne stabile
Objektidentität zeigen, werden jetzt abgelehnt. Gültige Modelle müssen die
beabsichtigte Identität explizit oder über Topic-/Klassenvererbung festlegen.
Die 2.3-Regel bleibt davon getrennt. Historische Referenzberichte über frühere
Testdateien werden durch angepasste aktuelle Fixtures nicht nachträglich gültig.


Einheiten erhalten in der laufenden Vorab-API 0.2 eine gebundene Zusammensetzung.
`UnitSymbol` und `UnitDeclarationSyntax` ergänzen `designation`, `derivedFrom`
und `components`; Konstruktoraufrufe müssen diese Werte explizit übergeben.
`name`/Headername bezeichnet jetzt das Kurzzeichen, `designation` die
Dokumentationsbezeichnung. Komponenten enthalten Operator und Ziel-ID bzw.
Syntaxnamen; erste Komponente MULTIPLY, danach MULTIPLY oder DIVIDE.
`base` bleibt von der Umrechnungsreferenz `derivedFrom` getrennt. Verbraucher
lesen Komponenten aus dieser Liste statt aus `definition`; der Text bleibt
für Anzeige und noch symbolische Umrechnungen erhalten. Keine Alt-/Neu-Fassade.

## Gebundene Views und Attributableitungen

Die bisher textuellen `ViewSymbol.formation()`/`body()` werden durch eine
optionale geschlossene Formation und gebundene Basen, Basiserweiterungen,
Selektionen, Mitglieder und Constraints ersetzt. `baseView()` bezeichnet
Vererbung; `bases()` bezeichnet lokale `ViewSymbol.Base`-Symbole. Neue
`SymbolKind.VIEW_BASE`-Werte sind bei exhaustiver Verarbeitung zu behandeln.

`AttributeSymbol` und `AttributeDeclarationSyntax` erhalten als letzte
Konstruktorkomponente `derivations` (unveränderliche geordnete Liste von
ExpressionId beziehungsweise ExpressionSyntax). Ohne Zuweisung ist die Liste
leer. Originaltexte bleiben über den Quellsnapshot verfügbar. Neue
`PathStep.ViewBaseStep`-Werte enthalten Basis- und Zielidentität.

`AssociationSymbol`/`AssociationDeclarationSyntax` haben neu die abschliessende
optionale Komponente `derivedFrom`, `RoleSymbol`/`RoleDeclarationSyntax` die
Komponente `derivation`. Ohne entsprechende Klausel ist sie leer. Beim Modell
ist `derivedFrom` eine lokale Basisidentität, `derivation` eine Ausdrucksidentität;
bei der Syntax sind es die strukturierten Quellkonstrukte.
`ViewSymbol.Inspection` enthält `element` und `parent`; `PathStep.InspectionStep`
kennzeichnet den gebundenen Spezialkontext. Partielle Geometriestrukturen sind
nur über die Analyse zugänglich, solange ihre Vollständigkeitsdiagnose besteht.

## Inspektionsfaktoren innerhalb von API 0.2

Die geschlossenen Hierarchien erhalten `InspectionExpressionSyntax` und
`InspectionExpression`; exhaustive Switches müssen diese Varianten behandeln.
Die Syntax trennt benannte Sicht/Inline-Formation von einer optionalen
OF-Einschränkung. Das Modell liefert den BAG-Typ, Ausgangs- und Elementidentität,
Zerlegungspfad, optionale Sicht und Einschränkung. Über `children()` bleiben die
gebundenen Ausdrücke erreichbar. Ungültige Einschränkungen sind Sprachfehler;
Die inzwischen gebundenen Geometrieinspektionen und ihre verbleibenden
Normprüfungen sind im Abschnitt G2 C unten beschrieben.

### View-Vererbung (Stabilisierungsfortsetzung)

Keine zusätzliche Signaturänderung. `AttributeSymbol.derivations()` enthält
lokale Alternativen; Verbraucher lesen über `overrides()` zuerst die Basis,
dann die Erweiterungen. Es gilt die letzte definierte Alternative. Geerbte
`ALL OF`-Attribute besitzen eine gebundene Typidentität, auch ohne eigene
Syntaxdeklaration. Effektive View-Mitglieder erhalten den vollständigen
Vererbungspfad und die Herkunft `OVERRIDDEN` bei lokaler Überschreibung.


## K2: Parameter und Klassenwerte innerhalb von 0.2

`ParameterSymbol`, `SymbolValueExpression` und `AttributePathType` erweitern die
jeweiligen geschlossenen Modellhierarchien. Exhaustive Verbraucher behandeln
sie sowie `SymbolKind.PARAMETER` und `TypeKind.ATTRIBUTE_PATH`.
`ClassValueType.restrictions` wechselt von Namenstrings zu `SymbolId`, die
Syntaxvariante zu `NameSyntax`. Für Attributpfadtypen wird kein `GenericType`
mehr als Platzhalter erzeugt: `AttributePathType.Owner` trennt OF-Ausdruck,
@Argument (Funktionsidentität mit nullbasiertem Parameterindex) und den
Konstantenbesitzer. OF-Ausdrücke bleiben im ausgewählten Schema verfügbar.
`SymbolValueExpression.targetSymbolId` ersetzt ungebundene Konstantentexte für
PARAMETER, Klassen- und Attributpfadkonstanten. Syntaxvarianten siehe Kapitel 04.
LSP und Queries erkennen Parameter; Rename prüft deren eigenen Namensraum.
Alle IDs gehören zum selben Snapshot; es gibt keine parallele Übergangsfassade.


## K3: CONTEXT und Topic-Bindungen innerhalb von 0.2

`ContextDeclarationSyntax`/`ContextSymbol` und `SyntaxKind.CONTEXT`/
`SymbolKind.CONTEXT` erweitern die geschlossenen Hierarchien. TopicSyntax erhält
als letzte Komponente `List<NameSyntax> deferredGenerics`; TopicSymbol erhält
`List<SymbolId> deferredGenerics` und `List<GenericBinding> genericBindings`.
Diese Listen sind nullfrei und unveränderlich. Direkte Deklarationen bleiben
von wirksamen Bindungen getrennt. Für kontextabhängige Koordinatenalternativen
lesen Verbraucher die BindingState-/Domain-/Alternativen-/Kontext-Identitäten
am Topic. Die Domaindefinition wird nicht global auf eine Alternative ersetzt.
Alle Konstruktoraufrufe und Schema-/Query-Verbraucher verwenden den neuen
Vertrag ohne parallele Fassade. Vollständige Verträge: Spec 04/05/16.

## K4: Linienformen und Geometriekomponenten

- `LineFormDeclarationSyntax` und `LineFormSymbol` ersetzen die bisher gesperrte
  Deklaration. Exhaustive Symbol-/Syntax-Switches berücksichtigen LINE_FORM.
- `LineTypeSyntax.lineForm(): Optional<String>` wird durch
  `lineForms(): List<NameSyntax>` ersetzt. `lineAttributes()` liefert
  `Optional<NameSyntax>`; `directed()` und `withoutOverlaps()` erhalten die Marker.
- `GeometryType.lineForms(): List<SymbolId>` und
  `lineAttributes(): Optional<SymbolId>` ersetzen Namensstrings. Die Liste erhält
  die Quellreihenfolge; Übersetzung vergleicht gebundene Mengen. Richtung,
  OVERLAPS-Marker und exakte optionale Toleranz sind eigene Komponenten.
- Vordefinierte Einheiten besitzen jetzt gemeinsame Identitäten je Sprachversion
  und `PredefinedOrigin` statt einer modellgebundenen synthetischen Herkunft.
  IDs bleiben snapshotgebunden; Verbraucher dürfen ihre Zeichenfolge nicht parsen.
- Completion und Diagramme ergänzen LINE_FORM. Die Diagrammreferenz `segment`
  zeigt die Strukturbindung; bestehendes JSON-Schema und TypeScript-Adapter
  transportieren die zusätzliche Kind-Zeichenfolge unverändert.


## K4: Metadaten und Inspektionen innerhalb API 0.2

- Exhaustive Switches ergänzen METADATA_BASKET und METAOBJECT. Baskets liefern
  Topic, optionale Basis und lokale Objekte; Metaobjekte ihre Klasse. Fehlende
  Zieloptionen sind ausschliesslich in fehlerhaften Analysen erlaubt.
- `NumericRange.referenceSystem()` liefert einen gebundenen Record mit Ziel,
  Domainmarker und optionalem einsbasiertem Achsenindex. `NumericTypeSyntax`
  bewahrt dieselben Bestandteile als Namen. `CoordinateTypeSyntax.axes()`
  enthält vollständige numerische Syntax; `CoordinateType.rotation()` und
  das Syntaxgegenstück bewahren die Achsenrotation.
- `ParameterSymbol` ergänzt `overrides`; `ParameterDeclarationSyntax` ergänzt
  `modifiers`. Der Owner unterscheidet Laufzeit- von Klassen-/Strukturparametern.
- Inspektionsstrukturen behalten den konkreten Koordinatentyp. CONTEXTUALIZATION
  verweist über DerivedOrigin auf die kanonische Segmentvorlage. Die lokale
  Basiskante und die StartSegment-Bedingung sind öffentliche Modellinhalte.
- SurfaceEdge enthält LineAttrs nur in INTERLIS 2.3; 2.4 hat Linienattribute
  abgeschafft. LINE ATTRIBUTES wird dort gezielt abgelehnt.
- Diagramme ergänzen Metadatenknoten und Referenzen sowie deklarierte Parameter.
  JSON-Schema 1.0 und TypeScript-Adapter transportieren die offenen Kind-Strings.

Die Vorab-API bleibt unveröffentlicht 0.2. Die folgenden K5-/G2-Ergänzungen
beschreiben SIGN/METAOBJECT-Parameter, GRAPHIC und eigene Inspektionssegmente;
keine Alt-/Neu-Fassade und keine zweite Binderimplementierung.

K5 ergänzt `GraphicSymbol`, `DrawingRuleSymbol` und `MetaObjectType` sowie die
zugehörige strukturierte Grafiksyntax. Exhaustive Switches um GRAPHIC,
DRAWING_RULE und METAOBJECT ergänzen. Grafik-/Regelvererbung und Basis sind
getrennte IDs; `effectiveRules` erhält die fachliche Reihenfolge. Bedingungen
und gewöhnliche Zuweisungen referenzieren vorhandene Ausdrücke; Metaobjektwerte
referenzieren Symbole. ACCORDING enthält Enum-Pfade im Typ des Selektors.
Signaturparameter bleiben `ParameterSymbol`; METAOBJECT ist keine
Transferreferenz. Verbraucher verwenden gebundene IDs statt Quelltext-Regex.
Die unveröffentlichten API-Version 0.2 und Wire-/Diagrammformat 1.0 bleiben
bestehen; es entsteht keine parallele Fassade.

K6 ergänzt an `UnitSymbol` und `UnitDeclarationSyntax` vor `definition` die
Argumente `conversionFactors` und `conversionFunction`. Ohne Umrechnung
`List.of(), Optional.empty()` übergeben. Faktoren sind
`UnitSymbol.ConversionFactor(operator, kind, decimal, range)` mit
`ConstantKind.DECIMAL/PI/LNBASE`. Nur DECIMAL hat einen exakten `DecimalLiteral`;
alle Faktoren behalten den Konstantenbereich. Der erste Faktor multipliziert.
`derivedFrom` mit leerer Faktorenliste und ohne Funktion bedeutet Faktor eins.
FUNCTION-Erklärungen sind separat gespeichert und werden nicht ausgeführt.
Verbraucher lesen diese Werte statt Definitionstexte numerisch auszuwerten.
Die unveröffentlichte API 0.2 bleibt bestehen; es gibt keine parallelen
Konstruktorfassaden. `canExtend` berücksichtigt
aber nun die skalare Einheitenvererbung.

## Vordefinierte Zeittypen

`TemporalType` und `TypeKind.TEMPORAL` entfallen. DATE/TIMEOFDAY/DATETIME
liefern in 2.4 einen `DomainReferenceType` auf INTERLIS.XMLDate/XMLTime/XMLDateTime.
Den effektiven `FormattedType` über die bestehende Typabfrage lesen;
`predefined(): Optional<TemporalKind>` bezeichnet seine Zeitart. Auch direkte
XMLDate-Verwendungen sind Domänenreferenzen. Die Formate enthalten jetzt
gebundene Basisstrukturen, Attribute, Breiten, Einheiten und Referenzsysteme.
Zeitwerte folgen denselben FORMAT-Regeln wie benutzerdefinierte Formate.
Es gibt keine getrennte pauschale Datumsprüfung mehr. Insbesondere sind in 2.3
fehlende Monats-/Tagespolster zulässig; 2.4 hat explizite Breiten.

## Gebundene Übersetzungskorrespondenzen

`ModelTranslation` erhält zusätzlich `sourceTarget` und `correspondences`.
Konstruktoraufrufer übergeben eine optionale gebundene Originalmodell-ID und eine
immutable Liste von `TranslationLink(original, translated)`; ungebundene partielle
Analysen haben keine erfundenen Ziele. Verbraucher lesen die Links am bestehenden
`ModelSymbol` über Graph oder `SemanticQueries.symbol()`. Die Namen und IDs beider
Deklarationen bleiben erhalten. Unveränderte Imports können Identitätslinks
enthalten; eine Übersetzung ersetzt keine IDs im übrigen Graph.


## G2 C: Geometrietoleranz und Kontextstrukturen

`GeometryType.overlap()` wechselt innerhalb der unveröffentlichten API 0.2 von
`Optional<DecimalLiteral>` zu `Optional<GeometryType.Tolerance>`. Beim Lesen
unterscheiden Verbraucher `GeometryType.Explicit.value()` und
`GeometryType.TrackWidth.coordinateDomain()`. Konstruktorverbraucher verpacken
explizite Werte in `new GeometryType.Explicit(value)`. Bei TrackWidth bestimmen
die gebundenen Koordinatenpräzisionen `q * sqrt(2) / 2`; diese Grösse wird nicht
als gerundete Dezimalzahl ausgegeben. Vererbung erhält die ursprüngliche
bestimmende Domäne. Leere Toleranz ist keine numerische Null.

Geometriedeskriptoren enthalten vor Ausdrucksbindung die effektiven geerbten
Eigenschaften. `canExtend` prüft dieselben Regeln wie der Compiler und ist von
`canAssign` getrennt. Inspektionen eigener Segmente liefern die vorhandenen
`StructureSymbol`-/Attribut-/Constraint-/Ausdruckswerte mit
`DerivedOrigin(..., CONTEXTUALIZATION)`; kanonische Vorlagen und fremde Ziele
bleiben unverändert. Die Bindung von `INTERLIS.LineCoord` bezeichnet die echte
vordefinierte Domäne. Es entstehen weder eine zweite öffentliche Fassade noch
neue Einstiegspunkte.


## G2 D: Signatur-Verantwortung

Keine neue Signatur oder Konstruktoränderung. `DrawingRuleSymbol.signatureClass`
bleibt die deklarierte OF-Klasse. Die gebundenen Metaobjekte, Parameter-Eigentümer,
Overrides, Bedingungen und ACCORDING-Fälle bleiben öffentlich erhalten.
Die Prüfung erlaubt passende Basissignaturen mit zusätzlichen Parametern einer
Unterklasse und bestimmt die Pflichtparameter im jeweiligen Zuweisungskontext.
Explizites METAOBJECT OF wird weiterhin separat geprüft; bei fehlendem OF
bleibt die Prüfung gegen die verantwortliche Klasse des Zeichenregelkontexts
erhalten. Der vordefinierte Sign-Parameter erlaubt dabei passende Basissignaturen.
Geerbte Werte werden bei Bedarf gegen engere Parametertypen geprüft, ohne die
Basiswerte zu verändern. Spezifikation 05/06/16 beschreibt die statischen Grenzen;
Bedingungen und Zeichenregeln werden nicht ausgeführt.

G2 E1 ergänzt ohne Signaturänderung die tatsächlich gebundenen einfachen
Domänen `INTERLIS.INTERLIS_1_DATE`, `INTERLIS.HALIGNMENT` und
`INTERLIS.VALIGNMENT`. Verbraucher lösen ihren `DomainReferenceType` über
`TypeRelations.resolveAliases` auf. Die Ziele sind FINAL und besitzen
`PredefinedOrigin` ohne Quelldeklaration. Unbekannte Namen liefern reguläre
Quellfehler; falsche Funktions-/ALL-OF-Präfixe binden keinen lokalen Suffix.

G2 E1 vervollständigt METAOBJECT_TRANSLATION über die bestehenden Klassen-,
Attribut-, Constraint- und Pfadtypen; öffentliche Signaturen ändern sich nicht.
NAME-/URI-Typen veröffentlichen ihre festen Grenzen 255/1023. Die leeren
Funktionslisten folgen dem 2.3/2.4-Syntaxprofil einschliesslich Fragmenten.

E1-Modellkopf: ModelSyntax erhält als letzten Konstruktorparameter ModelOptions;
ModelSymbol erhält ModelOptions und den gebundenen String transferNamespace.
Bei selbst konstruierten Beispielen ohne explizite Optionen ModelOptions.defaults()
verwenden. Syntaxoptionen enthalten originale Herkunft; den wirksamen Namespace
über ModelSymbol.transferNamespace() lesen. Kein Charset-Optional bedeutet den
normativen Anhang-D-Zeichenvorrat, nicht UTF-8. Es gibt keine Alt-Konstruktorfassade.

E1 ergänzt `ModelOptions.language(): Optional<TextValue>` mit der dokumentarischen
Modellsprache und Originalherkunft. `ModelSyntax.language()` ist daraus abgeleitet;
der redundante String-Konstruktorparameter entfällt in API 0.2. Modell- und
Übersetzungssprache bleiben getrennt. Eigene Sprachbezeichnungen bleiben zulässig.

E2 korrigiert den bisherigen compareUnits-Platzhalter auf den schon spezifizierten
Rückgabetyp QueryResult<UnitRelation>; Aufrufer behandeln Known/Unresolved/
Invalid/NotAnalyzed wie bei anderen Typabfragen. UnresolvedReason ergänzt
NOT_A_UNIT. DomainSymbol.mandatory() ist der effektive geerbte Pflichtflag,
auch sichtbar über header.modifiers. Keine parallele Fassade. NumericTypeSyntax
erhält ungültige Quellgrenzen zur Diagnostik; NumericRange bleibt validiert.

E2-Präzision ist versionsabhängig: 2.3 §2.8.5 S.42 erlaubt Verfeinerung,
sofern gerundete Grenzen im Basisbereich bleiben (7.9949 ja, 7.9999 nein bei
Basismaximum 7.99). 2.4 §3.8.5 S.53 verlangt unveränderte Präzision.
`TypeRelations.canExtend(derived, base, context, language)` erhält daher einen
expliziten nullfreien LanguageVersion-Parameter; weder Quellnamen noch ein
zufällig gefundenes Modell bestimmen das Profil. Interne Verbraucher und
Vorab-API-0.2-Tests werden direkt angepasst, ohne alte Überladung.
Die 2.3-Rundung verwendet exakte Dezimalwerte und HALF_UP; Tie-Details bleiben
in der Normprüfung separat sichtbar. Allgemeine Zuweisung ist unverändert.

E2-TEXT ergänzt ohne öffentliche Signaturänderung die Literal-Längenregel aus
Kapitel 05/06 (META-003/006/014, SEM-005/010/014, TEST-011/012). Bindung und
öffentliche Typabfragen verwenden denselben Literaltyp; Quelltext, Identitäten
und UTF-16-Bereiche werden nicht normalisiert. Geprüft durch TextLengthsTest
und TextLiteralContractTest sowie die gepinnten Normalisierungseingaben.

E3: StructureValueType erhält Optional<SymbolId> target und List<SymbolId>
restrictions als vier Konstruktorparameter. Für ein konkretes Ziel bisheriger
Konstruktoren Optional.of(target), List.of() verwenden. ANYSTRUCTURE hat leeres
target; mehrere Restriktionen sind keine einzelnen Ersatzwurzeln. Navigation
muss einen gemeinsamen zulässigen Strukturkontext bestimmen. Für Funktions-
argumente AssignmentContext.FUNCTION_ARGUMENT verwenden. Alte Konstruktoren
oder parallele Fassaden bleiben nicht bestehen. Diagramm- und Compilerverbraucher
sind unmittelbar angepasst. SyntaxSnapshot/AnalysisSnapshot/CompiledSchema bleiben.

E2 ohne zusätzliche Signaturänderung: canExtend prüft Collectionelemente als
Erweiterung und erlaubt zusätzliche Koordinatendimensionen. Numerische und
Koordinatenwerte veröffentlichen ausgelassene geerbte Eigenschaften bereits
vor Ausdrücken; Aliasziele bleiben isoliert. Die NFD-Längenregel gilt ebenfalls
in 2.3; frühere Codepoint-Erwartungen an solche Literaltypen sind zu korrigieren.

E4: EnumerationType erhält als achten Parameter boolean treeValues; für normale
Definitionen false verwenden. ALL OF ist öffentlich ein eigener effektiver
EnumerationType mit treeValues=true und referenzierter Definition in baseType.
EnumArgumentType ergänzt die geschlossene TypeDescriptor-/TypeKind-Familie.
BuiltinFunctionExpressionSyntax.functionName() ist jetzt NameSyntax; .text()
und Segmentbereiche verwenden. Vollständige Standardaufrufe tragen tatsächliche
FunctionSymbol-Ziele. Kontextgebundene Enum-/FORMAT-Konstanten können den
deklarierten Aliastyp erhalten; für die effektive Familie resolveAliases nutzen.
Keine Alt-Konstruktoren oder parallelen Fassaden werden beibehalten.

E4-Funktionskontexte: FunctionCallExpression erhält als achten Parameter
Map<Integer, FunctionCallExpression.ArgumentPath> argumentPaths; für bisherige
Aufrufe Map.of() verwenden. children bleibt die ursprüngliche Argumentliste.
Zusätzliche Stringpfade werden über ihren Argumentindex und die vorherige
Wurzel referenziert; eine Bindung ist nur für statisch bekannte Wurzeln vorhanden.
LiteralExpressionSyntax und ConstantExpressionSyntax erhalten als letzten
Konstruktorparameter Optional<NameSyntax> unit. Ohne Einheit Optional.empty().
PI/LNBASE sind symbolisch; Verbraucher dürfen keine gerundeten Dezimalgrenzen
mehr als deren exakten Wert interpretieren. Keine Alt-Konstruktoren bleiben.

E1/E4-Definiertheit: FunctionParameter erhält mandatory, FunctionSymbol
returnMandatory als letzten Konstruktorparameter. Gleichnamige Syntaxrecords
erhalten diese Angaben ebenfalls am Ende. Bisherige nicht pflichtige
Signaturen verwenden false; Quell-MANDATORY und geerbte Domänenpflicht müssen
beim Erzeugen semantischer Signaturen berücksichtigt werden. Keine Alt-Fassade.

E5: MetaAttributeSet.values() liefert Map<String,MetaAttributeSet.Value>. Der
Text steht in Value.text(), Herkunft in Value.origin(). Documentation erhält
List<SourceRange> ranges als zweiten Parameter. FunctionSymbol erhält
Documentation explanation als fünften, FunctionDeclarationSyntax
Optional<SourceMetadata.Text> explanation als letzten Parameter. SyntaxSnapshot
erhält Map<SourceRange,SourceMetadata> metadata als letzten Konstruktorparameter.
Für leere Angaben empty-Fabriken/Optional.empty()/Map.of() verwenden.
Verbraucher und Beispiele werden unmittelbar angepasst, ohne Alt-Fassaden.

`ConstraintDeclarationSyntax` erhält als letzte Komponenten
`Optional<Plausibility> plausibility` und `Optional<ExpressionSyntax> where`.
Unbetroffene Constraintfamilien übergeben jeweils `Optional.empty()`. Der kleine
verschachtelte Plausibility-Record enthält Prozentzahl, Vergleichsoperator und
ursprünglichen Zahlenbereich. Die SET-Vorbedingung wird getrennt von ihrer
Mengenbedingung verwendet. `SetConstraintScope.GLOBAL` ersetzt die bisherigen
LOCAL/TRANSFER-Werte; BASKET bleibt bestehen.
Verbraucher dürfen GLOBAL nicht auf den aktuellen Transfer einschränken.

Vordefinierte OID-Domänen sind keine synthetischen Kinder des Benutzermodells
mehr. Verbraucher verwenden `Origin.PredefinedOrigin` und die kanonische
versionsgebundene Symbolidentität; Quellnavigation erhält sie auch bei Typen
in Funktionssignaturen. 2.3 und 2.4 haben unterschiedliche deklarierte Basen
und ABSTRACT-Modifikatoren gemäss ihrem jeweiligen vordefinierten Modell.

Geprüfte ModelTranslation.correspondences können nun zusätzlich kontextualisierte
Segment-/Inspektionsstrukturen samt Mitgliedern und Constraints enthalten.
Verbraucher verwenden die veröffentlichten Links; sie rekonstruieren Beziehungen
nicht aus synthetischen Namen oder IDs. Öffentliche Signaturen bleiben erhalten.
