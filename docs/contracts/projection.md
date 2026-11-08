# Projektion, Diagramme und Dokumentation

Die reine Projektion erhält fachliche Identität und Herkunft. Layout und Interaktion gehören zu den Verbrauchern. Für Formate gelten die mitgelieferten Schemas und die öffentlichen Java-Typen.

<a id="eingabe-optionen-und-ergebnis"></a>
## Eingabe, Optionen und Ergebnis

<a id="öffentliche-api"></a>
### Öffentliche API

Der öffentliche Einstieg lautet `DiagramProjectors.create() -> DiagramProjector`. Er erzeugt den zustandslosen Standardprojektor mit den in diesem Kapitel festgelegten typisierten Formatierern und ohne I/O. Implementierungshelfer bleiben intern; ein Aufrufer muss kein eigenes Dependency-Injection-System und keine Rendererbibliothek bereitstellen. Optionen werden je Aufruf übergeben und nicht global mutiert.

`DiagramProjectionOutcome` besitzt die Varianten `Produced(DiagramDocument document, List<ProjectionNotice> notices)` und `Rejected(List<ProjectionNotice> reasons)`. Abbruch verwendet den gemeinsamen `OperationCancelledException`-Vertrag. Eine fachlich partielle, aber darstellbare Eingabe führt zu `Produced` mit passendem Status. Eine unpassende Snapshotreferenz, unmögliche Auswahl oder nicht darstellbare zwingende Projektionsanforderung führt zu `Rejected`.

`ProjectionNotice` ist kein INTERLIS-Sprachfehler. Es enthält `ProjectionNoticeCode`, Schweregrad, Nachrichtenschlüssel/-argumente, optionalen SymbolRef und optionalen SourceRange. Beispiele für Codes sind `UNSUPPORTED_PRESENTATION`, `UNRESOLVED_TARGET`, `PROJECTION_LIMIT_REACHED` und `STALE_SELECTION`. Compilerdiagnostik bleibt im Eingangssnapshot erhalten; ein Projektionshinweis darf dessen Gültigkeit weder aufheben noch herstellen.

Wird ein solcher Zustand über den gemeinsamen Verbraucher-Diagnosekanal ausgegeben, verwendet der Adapter das Mapping auf `ILI-PROJ-0001` beziehungsweise die in [Diagnosevertrag](diagnostics.md) vorgesehenen Projektionsdiagnosen und führt den feineren ProjectionNoticeCode als typisierten Grund mit. Das interne Hinweisobjekt und der gemeinsame Diagnosecode sind damit zwei dokumentierte Darstellungen desselben Verbraucherbefunds, keine konkurrierenden Sprachdiagnosen.

<a id="optionen"></a>
### Optionen

`DiagramOptions` ist ein unveränderlicher Wert mit mindestens:

| Feld | Typ/Werte | Verhalten |
|---|---|---|
| `selection` | `DiagramSelection` | SymbolRefs von Modellen, Topics oder ausgewählten Elementen des Eingabesnapshots. |
| `scope` | `SELECTED`, `WITH_DEPENDENCIES`, `NEIGHBOURHOOD` | Expliziter Umfang; bei Nachbarschaft zusätzlich geprüfte maximale Tiefe. |
| `visibleKinds` | Menge von `DiagramNodeKind` | Sichtbare Fachkategorien; kanonische Sortierung beim Fingerprint. |
| `inheritedMembers` | `HIDE`, `SHOW_WITH_ORIGIN` | Geerbte Elemente aus effektivem Metamodell; keine erneute Vererbungsberechnung. |
| `showTypes` | boolean | Typdarstellung in Mitgliedern. |
| `showRoleCardinalities` | boolean | Kardinalitäten anzeigen; semantische Rollenzuordnung bleibt erhalten. |
| `showConstraints` | boolean | Constraints als typisierte Namen/Arten in eigener Gruppe. |
| `showImports` | boolean | Importbeziehungen und nötige externe Rahmen. |
| `labelPolicy` | `CANONICAL`, `TRANSLATED` | Labelauswahl ohne Identitätsänderung. |
| `translationModel` | `Optional<SymbolRef>` | Bei TRANSLATED explizites Übersetzungsmodell desselben Snapshots mit gebundenen ModelTranslation-Korrespondenzen. |
| `neighbourhoodDepth` | `OptionalInt`, 0..32 | Bei NEIGHBOURHOOD explizite maximale Entfernung im gebundenen Referenzgraphen. |
| `associationPresentation` | `ENTITY`, `COMPACT_BINARY` | Standard ENTITY; verlustfrei geeignete binäre Assoziationen können Kanten sein. |
| `budget` | `ProjectionBudget(maxNodes,maxEdges,maxMembersPerNode)` | Ausdrückliche Begrenzung mit LIMITED-Meldung. |

Eine Auswahl einzelner Elemente fügt deren Modell-/Topicrahmen hinzu, ohne
unbeteiligte Geschwister zu expandieren. Ausgewählte Modelle/Topics nehmen
hingegen ihren deklarierten Teilbaum auf. NEIGHBOURHOOD folgt beidseitig
gebundenen TYPE_USE-/BASE_TYPE-/ROLE_TARGET-/Importreferenzen und Viewbasen;
reine Constraintausdrücke und Deklarationsendnamen sind keine Nachbarn.
Containermitgliedschaft verbraucht keinen Beziehungsschritt. Die Tiefe0
expandiert keine Beziehungen; nötige externe Randstubs bleiben ausdrücklich
als solche sichtbar. Budgets und Abbruch gelten auch für die Nachbarsuche.

TRANSLATED verwendet ausschliesslich die vorhandenen TranslationLink-Paare
des ausgewählten Übersetzungsmodells, einschliesslich gebundener mehrstufiger
Korrespondenzen. Nicht zugeordnete externe Namen bleiben kanonisch. Nur
Anzeigelabels und benannte Typlabels wechseln; canonicalName, IDs, SymbolRefs,
Kardinalitäten und SourceAnchors bleiben beim ursprünglichen Diagramm.
Fehlende Parameter, falsche oder veraltete Übersetzungsmodelle werden typisiert
abgelehnt; es gibt keine implizite Auswahl nach Sprache oder Deklarationsreihenfolge.
Der bisherige DiagramOptions-Konstruktor ohne diese beiden Parameter bleibt
als Überladung erhalten. Die neue kanonische Recordform enthält beide Felder.

Defaults sind in der einzigen öffentlichen Fabrik `DiagramOptions.defaults(DiagramSelection selection)` festzulegen und als Golden zu prüfen. Sie verlangt die konkrete nichtleere Auswahl, statt diese aus globalem Zustand oder einer zufälligen Modellreihenfolge zu erfinden. CLI, LSP und Browser dürfen Defaults nicht unabhängig unterschiedlich nachbauen. Ein Renderer kann eine zusätzliche Stilkonfiguration besitzen, die keine fachliche Auswahl verändert.

`selection` enthält mindestens ein Element. Alle SymbolRefs müssen zum Eingabesnapshot gehören. Für eine bequeme „gesamtes Wurzelmodell“-Funktion darf der Host die Wurzeln des CompiledSchema in eine Auswahl überführen. Der Host darf nicht auf zufällige Iterationsreihenfolge zurückgreifen, um ohne Auswahl „das erste Modell“ zu visualisieren.

<a id="diagramdocument-version-10"></a>
## `DiagramDocument` Version 1.0

<a id="wurzel-und-datenmodell"></a>
### Wurzel und Datenmodell

`DiagramDocument` ist ein unveränderlicher, durch JSON-Schema beschriebener DTO-Graph. Pflichtfelder:

- `schemaVersion`: `1.0`, unabhängig vom Compiler- oder Bridgeprotokoll;
- `snapshotId`: Herkunft der semantischen Daten;
- `sourceRevisions`: geordnete Zuordnung SourceId zu SourceRevision;
- `inputKind`: `CHECKED_SCHEMA` oder `ANALYSIS`;
- `completeness`: `COMPLETE`, `PARTIAL` oder `LIMITED`, bezogen auf die angeforderte Projektion;
- `selection` und `optionsFingerprint`;
- `containers`, `nodes`, `edges` und `notices`.

`COMPLETE` bedeutet, dass der angeforderte Diagrammumfang vollständig projiziert wurde. Es ist kein Ersatz für ein erfolgreiches Compiler-Gate. Ein aus `ANALYSIS` gewonnenes Dokument bleibt als solches gekennzeichnet, auch wenn alle ausgewählten Diagrammelemente vollständig darstellbar sind.

| DTO | Erforderliche Felder |
|---|---|
| `DiagramContainer` | ID, Kind MODEL/TOPIC, optionaler Parent, Label, kanonischer qualifizierter Name, optionaler SymbolRef, Herkunft/SourceAnchor. |
| `DiagramNode` | ID, Kind, Container-ID, Label, kanonischer Name, Status, SymbolRef oder expliziter Stubbezug, unveränderliche `Set<Modifier> modifiers` aus dem gemeinsamen Symbolheader, Compartments, SourceAnchor. |
| `DiagramCompartment` | ID, Kind ATTRIBUTES/ROLES/CONSTRAINTS/ENUM_VALUES/DETAILS, geordnete Mitglieder. |
| `DiagramMember` | ID, Kind, Label, optionale Typ-/Kardinalitätsdarstellung, Ursprungssymbol, deklariert-in-Symbol, geerbt/überschrieben, SourceAnchor. |
| `DiagramEdge` | ID, Beziehungskind, Source-/Target-Endpoint, semantischer Ursprungsref, Endinformationen, Label, SourceAnchor. |
| `DiagramEndpoint` | Vorhandene Container-/Knoten-ID, optional zugehörige Mitglieds-ID. |
| `DiagramEdgeEnd` | Optionaler Rollenname, exakte Kardinalität, Aggregationsart und explizites fachliches Ende. |
| `SourceAnchor` | SnapshotId, SourceRange des Namens, optionaler umfassender Deklarationsbereich. |

`DiagramNodeKind` umfasst mindestens CLASS, STRUCTURE, ASSOCIATION, VIEW, DOMAIN und EXTERNAL_REFERENCE. Aufzählungen können als Domäne mit ENUM_VALUES-Compartment dargestellt werden; verschachtelte Enumerationseinträge behalten ihren Pfad und ihre Reihenfolge. Ein späterer zusätzlicher Knotentyp benötigt eine versionierte Capability und kompatible Fallbackdarstellung.

`DiagramNodeStatus` unterscheidet RESOLVED, PARTIAL und UNRESOLVED. „Extern“ als INTERLIS-Eigenschaft und „importiert aus einer anderen Quelle“ sind getrennte Fach-/Herkunftsmerkmale; sie dürfen nicht aus einem einzigen missverständlichen Boolean abgeleitet werden.

`modifiers` wird im JSON additiv als nach Enumname sortierte Stringliste
übertragen. Insbesondere ABSTRACT wird aus dem Compilergraphen übernommen,
nicht aus Labels oder Modelltext rekonstruiert. Der frühere Java-Konstruktor
ohne Modifikatoren bleibt als explizite Fabrik für eine leere Menge verfügbar;
der Standardprojektor übergibt immer die tatsächlichen Headerwerte.
Ältere DiagramDocument-1.0-Nachrichten ohne dieses additive Feld bleiben
strukturell lesbar, belegen aber keine verfügbare Modifikatordarstellung.

Kardinalitäten werden als `minimum`-Dezimalstring und `maximum` als getaggte Variante `bounded(value)` oder `unbounded` serialisiert. Dadurch gibt es weder `-1` als verstecktes Unendlich noch Rundungsverlust bei grossen Werten. Anzeigeformen wie `0..*` erzeugt ein gemeinsamer Formatter. Fachlich genaue Dezimalwerte folgen dem Wire-Vertrag aus [Bridge-Vertrag](bridge-protocol.md).

<a id="invarianten"></a>
### Invarianten

1. Jede Element-ID ist im Dokument eindeutig. Knoten-, Rahmen-, Mitglieds- und Kanten-IDs verwenden getrennte Präfixe.
2. Jeder Parent existiert; der Containerbaum ist azyklisch.
3. Jeder Knoten gehört zu genau einem vorhandenen Container. Stubs können in einem expliziten externen Rahmen liegen.
4. Jede Kante hat existierende Endpunkte; eine optionale Mitglieds-ID gehört zum angegebenen Endknoten.
5. Alle realen SourceAnchors besitzen die richtige Quellrevision. Synthetische Stubs bekommen keine erfundene editierbare Position.
6. Reihenfolgen von Mitgliedern folgen den semantischen Abfragen; Renderer sortieren Attribute nicht eigenmächtig alphabetisch um.
7. CanonicalName, DisplayLabel und SymbolId sind getrennte Felder.
8. Ein Budgetabbruch verändert Vollständigkeit und Hinweise; er verschwindet nicht hinter einer normalen erfolgreichen leeren Liste.

Der `DiagramValidator.validate(document)` prüft diese DTO-Invarianten in Vertragstests und bei eingehenden externen Adaptern. Im Produktionspfad darf die eigene korrekt konstruierende Projektion eine günstigere Konsistenzprüfung verwenden, sofern die Invarianten durch Konstruktion abgesichert sind.

<a id="stabile-identitäten"></a>
### Stabile Identitäten

Knotenidentitäten werden aus der erhaltenen semantischen SymbolId und der Projektionsrolle abgeleitet. Eine Kantenidentität verwendet den semantischen Ursprung, Beziehungskind und fachlich eindeutige Endrolle. Ein laufender Listenindex ist keine stabile Assoziationsidentität.

Für geerbte Mitgliedsanzeigen ist die Identität eine Kombination aus anzeigendem Eigentümer, tatsächlichem Ursprungssymbol und Darstellungsrolle. Dadurch kann dasselbe geerbte Attribut in mehreren Klassen angezeigt werden, ohne IDs zu duplizieren.

Unaufgelöste Stubs erhalten einen Schlüssel aus belegtem Ausgangsscope, Referenzsegment und erwarteter Kategorie. Werden sie später aufgelöst, entsteht der echte Knoten mit dessen SymbolId. Die UI darf eine explizite belegte Übergangszuordnung nutzen; sie darf den Stub nicht aufgrund gleicher Beschriftung beliebig mit einem anderen Symbol verschmelzen.

Cross-Session-Stabilität wird nicht versprochen. Export-Goldens normalisieren opake IDs anhand eindeutig belegter Fachanker. Sie dürfen dadurch keine doppelte Deklaration oder mehrere unterschiedliche Beziehungen zusammenfallen lassen.

<a id="fachliche-projektionsregeln"></a>
## Fachliche Projektionsregeln

<a id="klassen-typen-und-vererbung"></a>
### Klassen, Typen und Vererbung

`DiagramProjector` verwendet die effektiven Attribut-/Rollenabfragen des Metamodells. Ein geerbtes, lokal überschriebenes Attribut wird entsprechend der dortigen effektiven Reihenfolge behandelt. Die Projektion darf dieselbe Vererbung nicht durch ein eigenes rekursives Sammeln erneut interpretieren.

Eine Vererbungskante läuft fachlich von Subtyp zu Basistyp. Das gilt für Klassen und entsprechend unterstützte Domänen-/Topicbeziehungen. Darstellungspfeile werden im Renderer aus diesem festen Verhältnis abgeleitet. Zyklische oder unaufgelöste Vererbungen bleiben Diagnostik beziehungsweise partielle Kanten; sie werden nicht als geprüfte Hierarchie ausgegeben.

Domänen zeigen ihren deklarierten Typ und bei Bedarf dessen aufgelöste Bedeutung. Aliasname und effektiver Typ werden getrennt dargestellt, damit die Abstraktion des Modells sichtbar bleibt. Views können ihre Ableitungsbeziehungen anzeigen, ohne dass die Diagrammschicht View-Semantik selbst berechnet.

<a id="assoziationen-und-rollen"></a>
### Assoziationen und Rollen

Die Standardprojektion stellt jede Assoziation als eigenes Element dar. Ihre Rollen erscheinen als gerichtete Beziehungen zur jeweiligen Zielklasse und behalten Rollenname, Kardinalität, Aggregationsart und Quellbezug. Eine Assoziation mit Attributen erhält eigene Attribute; drei oder mehr Rollen bleiben vollständig sichtbar.

Eine spätere kompakte Darstellung einer binären Assoziation ist nur erlaubt, wenn beide Enden und alle anzuzeigenden Informationen verlustfrei auf eine Kante abgebildet werden. Assoziationsidentität, Rollenidentitäten und SourceAnchors müssen trotzdem erhalten bleiben. Bei Attributen, zusätzlichen Rollen oder nicht darstellbaren semantischen Eigenschaften muss die Darstellung auf ENTITY zurückfallen und den Grund als Projektionshinweis liefern.

Diamanten für Aggregation/Komposition werden aus den semantischen Endinformationen abgeleitet. Der Agent darf ihre Seite nicht aus der Reihenfolge zweier Klassennamen oder einer zufälligen Source-/Target-Konvention raten. Ein gezieltes Fixture prüft beide Richtungen und explizite Rollenkardinalitäten.

<a id="weitere-beziehungen"></a>
### Weitere Beziehungen

Das Beziehungsschema unterscheidet mindestens INHERITANCE, ASSOCIATION_ROLE, ASSOCIATION, REFERENCE, STRUCTURE_COMPOSITION, DOMAIN_USE, VIEW_DERIVATION, MODEL_IMPORT und TRANSLATION. Kategorien können zunächst deaktiviert sein, müssen dann aber über die Projektionsfähigkeiten kenntlich werden.

Eine `REFERENCE TO`-Verwendung und eine Strukturkomposition sind unterschiedliche Beziehungen. Modellimports verbinden Rahmen, nicht zufällige Klassen im importierten Modell. Fachlich verschiedene Beziehungen zwischen denselben zwei Knoten bleiben getrennte Kanten mit eigener Identität.

<a id="filter-und-partielle-daten"></a>
### Filter und partielle Daten

Filter werden in einer definierten Reihenfolge angewandt: Auswahl/Scope, Fachkategorien, Beziehungsumfang, Mitgliederanzeige, Budget. Benötigte Endpunkte werden als sichtbare Knoten oder ausdrückliche Stubs behalten. Werden Kanten ausgelassen, wird ihr Umfang im LIMITED-Hinweis angegeben. Ein bloss ausgeblendetes Ziel und ein fachlich unaufgelöstes Ziel erhalten unterschiedliche Statuswerte.

Ein fehlerhafter Attributtyp darf als unaufgelöstes Mitglied erscheinen. Die Projektion darf ihn weder in einen Standardtyp umdeuten noch das ganze gültige Nachbarmodell entfernen. Aktuelle Compilerdiagnosen bleiben an Knoten oder Mitgliedern über ihre IDs verknüpfbar.

<a id="klassen--und-methodenverträge"></a>
## Klassen- und Methodenverträge

| Klasse/Methode | Vor-/Nachbedingungen und Fehlerverhalten |
|---|---|
| `DefaultDiagramProjector.project(input,options,token)` | Prüft Snapshotreferenzen und Auswahl; erzeugt lokal einen neuen DTO-Graphen; veröffentlicht erst nach Konsistenzprüfung; keine I/O und keine Veränderung des Eingangsgraphs. |
| `DiagramSelectionResolver.resolve(input,selection,scope)` | Liefert deterministisch ausgewählte SymbolRefs und Herkunft; nicht verfügbare Auswahl typisiert ablehnen. |
| `NodeProjector.project(symbol,context)` | Ein semantisches Symbol in passenden Knotentyp überführen; ununterstützte Kategorie melden, nicht generisch als Klasse darstellen. |
| `AssociationProjector.project(association,context)` | Rollen und eigene Attribute vollständig behandeln; Ursprung pro Kante erhalten; Diagrammoptionen dürfen Sprachsemantik nicht verändern. |
| `MemberProjector.effectiveMembers(viewable,options)` | Effektive Reihenfolge aus SemanticQueries übernehmen und Anzeigeursprung hinzufügen. |
| `TypeLabelFormatter.format(typeRef,policy)` | Deterministisch, rekursionsbegrenzt und aliasbewusst; ErrorType, legal aufgeschobener Typ und UNDEFINED unterscheidbar. |
| `CardinalityFormatter.format(cardinality)` | Exakte untere/obere Grenze; keine Float-Konversion; Unendlich nur aus expliziter Variante. |
| `SourceAnchorFactory.forSymbol(input,symbol)` | Nur belegte Quellbereiche des aktuellen Snapshots; bei synthetischen Elementen Optional.empty. |
| `DiagramJsonCodec.encode(document)` | Schema 1.0, deterministische Reihenfolge, exakte Zahlen, korrektes Escaping; keine Reflection-Pflicht. |
| `DiagramValidator.validate(document)` | Alle Invarianten aus 3.2; klarer struktureller Fehlerbericht mit Element-ID. |
| `MermaidRenderer.render(document,style)` | Erzeugt ausschliesslich Text; keine Shell-/Netzaufrufe; Capabilities prüfen und Labels escapen. |
| `PlantUmlRenderer.render(document,style)` | Gleicher Fachumfang wie Projektion, deterministischer Text; keine automatische Übermittlung an einen öffentlichen Renderdienst. |

Abbruch wird mindestens pro grösserer Symbolgruppe und bei aufwendiger Rekursion geprüft. Ein abgebrochener Projektionslauf liefert keinen halbfertigen DiagramDocument als reguläres Ergebnis. Ein explizites Budgetlimit darf dagegen einen konsistenten reduzierten Graphen mit LIMITED erzeugen, wenn sämtliche erhaltenen Kanten weiterhin gültige Endpunkte haben.

<a id="renderer-layout-und-navigation"></a>
## Renderer, Layout und Navigation

<a id="layoutvertrag"></a>
### Layoutvertrag

Der Projector erzeugt keine Pixelkoordinaten. `LayoutState` gehört zum Host und ist nach Diagramm-/Sitzungsidentität, Viewkonfiguration und Layoutengineversion getrennt. Der Host kann Positionen unveränderter Knoten behalten und neue Knoten ergänzen. Nach einer reinen Labeländerung soll er nicht ungefragt das ganze Diagramm neu anordnen.

Der Host überträgt vollständige DiagramDocuments. Eine Delta-Schnittstelle wird erst bei gemessenem Bedarf eingeführt. Bereits damit kann die UI anhand stabiler IDs ihren Layoutzustand wiederverwenden. Ein unnötig früher Delta-Mechanismus würde zusätzliche Versions-, Lösch- und Reihenfolgeverträge erzeugen.

Layoutantworten erhalten die angeforderte SnapshotId. Eine spät eintreffende alte Layoutantwort darf eine neuere Diagrammversion nicht überschreiben. Stiloptionen, Zoom und manuell verschobene Knoten ändern weder SourceRevision noch WorkspaceRevision.

<a id="navigation"></a>
### Navigation

`DiagramNavigationService.resolve(document,elementId,currentSnapshot)` ist ein Hostdienst. Bei gleicher SnapshotId liefert er den belegten SourceAnchor. Bei anderem Snapshot wird eine explizite, eindeutige semantische Neuzuordnung versucht oder `STALE_SELECTION` zurückgegeben. Absolute Offsets alter Quellen werden nie einfach auf den aktuellen Text angewandt.

Eine Elementauswahl kann in beide Richtungen synchronisiert werden: Diagrammklick zu Quelle und Textcursor zu SymbolRef/Diagramm-ID. Gleich benannte fremde Symbole dürfen nicht über reinen Stringvergleich hervorgehoben werden. SourceAnchors ohne editierbare Quelle bieten Information, aber keinen scheinbar möglichen Textsprung.

<a id="text--und-bildexport"></a>
### Text- und Bildexport

Mermaid-/PlantUML-Renderer generieren aus den DTOs kontrollierte Sprachelemente. Bezeichner und Dokumentation werden als Daten escaped; Modelltext darf keine zusätzlichen Direktiven einschleusen. Relative Quelllinks können optional durch einen Host-LinkResolver erzeugt werden. Ohne einen solchen Resolver gibt es keine erfundenen Links.

Die Erzeugung von SVG/PDF/PNG liegt in einem Exportadapter, der gepinnte Rendererwerkzeuge startet beziehungsweise eine vorhandene Webdarstellung exportiert. Netzbasierte Renderingdienste sind keine automatische Defaultabhängigkeit. Der reine Compiler-/Projectorpfad bleibt auch ohne diese Programme verwendbar.

<a id="gemeinsame-kernfixtures"></a>
### Gemeinsame Kernfixtures

1. **Import und Vererbung:** Zwei Dateien, importierte Domäne und Basisklasse; identische Knoten, Vererbung und Typanzeige in JVM- und Browserprojektion.
2. **Fehlerhafter Zwischenstand:** Fehlender Attributtyp neben gültiger Klasse; partielle Darstellung, genaue Quelle und keine erfolgreiche Schemakennzeichnung.
3. **Assoziationen:** Binär, n-är, mit Attributen, Aggregation/Komposition und mehreren Beziehungen zwischen denselben Klassen; keine Informationsverluste.
4. **Geerbte Elemente:** Überschreibung behält effektive Position; Ursprung und lokaler Eigentümer bleiben navigierbar.
5. **Übersetzung:** Labelwechsel ändert keine Symbol-/Diagrammidentität; kanonischer Name bleibt abfragbar.
6. **Stabilität:** Kommentaränderung, fremde Einfügung und kleine Labeländerung erhalten IDs und vorhandenes Layout soweit fachlich belegbar.
7. **Budget:** Erzwungene Begrenzung erzeugt konsistenten Graphen, LIMITED und nachvollziehbare Anzahl ausgelassener Elemente.
8. **Escaping:** Umlaute, Emoji, Sonderzeichen und rendererähnliche Texte liefern gültige, unverfälschte Exporte.

Die Tests vergleichen zuerst DiagramDocument-Strukturen. Reine Pixelvergleiche können fachliche Fehler verdecken und sind kein Ersatz. Für die ausgewählten interaktiven und statischen Renderer kommen wenige gezielte visuelle Abnahmen hinzu: abgeschnittene Labels, falsche Kardinalitätsenden, verlorene Assoziationsattribute und Navigation nach einer Änderung.

<a id="linienformen-in-der-projektion"></a>
### Linienformen in der Projektion

`DiagramNodeKind.LINE_FORM` stellt eine deklarierte Linienform dar. Die
Standardauswahl enthält diese Kategorie. Eine REFERENCE-Kante mit Label
`segment` zeigt auf die gebundene Segmentstruktur; deren Vererbung verwendet
weiterhin INHERITANCE. Identitäten und Quellanker stammen aus dem gemeinsamen
Graphen. Das Diagrammformat 1.0 und sein Schema bleiben unverändert: Die dort
bereits offenen Kind-Strings werden vom TypeScript-Adapter durchgereicht.
`DiagramProjectionTest.projectsLineFormTargetAndSegmentInheritance` prüft
Knoten, beide Beziehungen und den Diagrammvalidator.

<a id="metadaten-und-parameter"></a>
### Metadaten und Parameter

METADATA_BASKET und METAOBJECT ergänzen die Standard-Knotenarten. Gebundene
REFERENCE-Kanten zeigen `topic`, `class` und `basket`; Basketvererbung nutzt
INHERITANCE. Ein Topic-Ziel wird bei Bedarf als Referenzknoten dargestellt.
Deklarierte Klassen-/Strukturparameter erscheinen in PARAMETERS als PARAMETER
mit Typ, Override-Marker und Quellanker. Diagrammformat und Schema bleiben 1.0;
die vorhandenen offenen Kind-Strings werden vom TypeScript-Adapter transportiert.
Die Projektionsprüfung validiert die neue Struktur und die Referenzziele.

<a id="grafikdefinitionen-in-der-projektion"></a>
### Grafikdefinitionen in der Projektion

GRAPHIC und DRAWING_RULE ergänzen `DiagramNodeKind` und die Standardauswahl.
Die Projektion erhält lokale Zeichenregelknoten und gebundene REFERENCE-Kanten
`basis`, `signature` und `graphic`; Grafik-/Regelvererbung nutzt INHERITANCE.
Die Diagramme führen keine Zeichenregeln aus. Identitäten, Reihenfolgen und
Quellanker stammen aus dem gemeinsamen Graphen. Das Schema 1.0 und der
TypeScript-Adapter unterstützen die vorhandenen offenen Kind-Strings.
`DiagramProjectionTest.projectsGraphicBasisRuleSignatureAndInheritance` prüft
die Knoten, Beziehungen und den Diagrammvalidator.

<a id="eigener-gemeinsamer-interaktiver-diagrammconsumer"></a>
### Interaktiver Diagrammconsumer der VS-Code-Extension

Der interaktive Renderer gehört zur VS-Code-Extension. Er verwendet die
DiagramDocument-1.0-Typen und das Schema aus dem Core-Bundle. Die Wire-Typen
liegen in `interlis-projection/src/main/typescript/diagram-document.ts`.
Der Compilerharness zeigt fachliche Diagrammantworten als JSON. Das
Core-Bundle enthält Serverlaufzeit, Typen, Schemas und Lizenzen.

DiagramCanvas hält eingefrorene Semantik getrennt vom mutierbaren Sprotty-
Viewmodell. Native Move-/Routing-/Selectioncommands verändern Positionen und
innere Stützpunkte, niemals fachliche Endpunktsymbole oder Quelltext. Neuere
Generationen, Server-/Quellenwechsel und dispose brechen alte Worker ab.
Unveränderte Elementidentitäten behalten ihre Geometrie; neue Geschwister
werden neben belegten Positionen platziert, Container wachsen entsprechend.
Navigation wird als opaque Element-ID an den Host delegiert. Komposition und
Aggregation stammen unverändert vom jeweils publizierten Diagrammende.

Die Extension unterstützt orthogonale, polygonale und kubische Kanten.
Der Compilerharness prüft die portable Projektion. Renderer- und native
VS-Code-Tests prüfen Layout, Interaktion und die tatsächlich gespeicherten
Exporte.

Aufzählungsdomänen behalten ihre effektiven Pfade in ENUM_VALUES als
`DiagramMember.MemberKind.ENUM_VALUE`, mit Deklarationsreihenfolge, echtem
EnumElementSymbol (sofern deklariert), ursprünglichem Quellanker und
Inherited-Markierung. HIDE und gemeinsames Memberbudget gelten auch dafür;
LIMITED bleibt explizit. Identitäten/Anker bleiben bei übersetzten Labels
kanonisch. Das vorhandene Schema1.0 erlaubt offene Memberkind-Zeichenketten.
DiagramProjectionTest prüft verschachtelte/effektive Werte, Quellen, HIDE und
Budget. Die Extension steuert Aufzählungsdomänen und Inline-Aufzählungen
deklarierter Attribute unabhängig.

COMPACT_BINARY wird im reinen Java-Projektor aus bereits gebundenen Symbolen
gebildet. Genau zwei eindeutige Rollen ohne eigene Attribute/Constraints,
Vererbungs-/Ableitungsbezüge, geordnete, versteckende oder abgeleitete Rollen,
Zielrestriktionen oder besondere Linkkardinalität dürfen eine ASSOCIATION-Kante
ersetzen. Das Rollenmerkmal `EXTERNAL` steht der Reduktion bei eindeutig
aufgelöstem Ziel nicht im Weg. Es bleibt im semantischen Modell erhalten.
Beide Diagrammdarstellungen zeigen das Ziel ohne separate Markierung des
Rollenmerkmals. Andere eingehende
Bezüge auf den Assoziationsknoten verhindern die Reduktion. Alle übrigen
Assoziationen bleiben vollständige Entitäten; dies ist kein unimplementierter
Optionsfallback und macht eine vollständige Projektion nicht PARTIAL.

Die neue Kante übernimmt beide Zielendpunkte und Endmetadaten in
Rollendeklarationsreihenfolge, mit eigener Assoziations-ID, SymbolRef und
ursprünglichem Assoziationsanker. Selbstbeziehungen behalten denselben Knoten
an beiden Enden, parallele Beziehungen verschiedene Kantenidentitäten. Bestehende
Schemas1.0 erlauben den additiven Kindstring ASSOCIATION. Der neutrale Adapter
ordnet auch kompakte Kanten ihrem Assoziationssymbol zu; bestehende Textconsumer
bewahren beide Endlabels und Diamanten. Die portable Capability kündigt beide
implementierten Darstellungen an. CompactAssociationProjectionTest,
DiagramProtocolTest und der tatsächliche portable Adaptervertrag prüfen diese
Daten gemeinsam mit n-ären/attributtragenden Entitäten.

Der neutrale Reconciler verwendet IDs ausschliesslich innerhalb desselben
Snapshots als Identität. Bei einem neuen Snapshot entsteht ein exakter
Presentationkey aus dem vom Core gelieferten Quell-URI, Kind und canonicalName;
Offsets und ähnliche Labels werden nicht abgeglichen. Mehrdeutige oder
quelllose Keys verlieren ihren Zustand. Kanten verlangen zusätzlich eindeutige
Endpunktkeys, Quellbezug und identische Endmetadaten; nicht eindeutig zuordenbare
Memberendpunkte werden verworfen. Ein umbenanntes/ersetztes Element erbt deshalb
keine Geometrie allein aufgrund einer wiederverwendeten Symbol-ID. Die Keys
gehören nur zum Viewzustand und autorisieren niemals Navigation oder Sprach-
operationen. Server-/Rootwechsel verwirft die Sitzung samt Commandhistory.
Der portable Vertrag prüft tatsächliche Vorher-/Nachher-Projektionen der Engine
mit einer ausgetauschten Deklaration und einer unveränderten Nachbarklasse.

Jede neue Projektionsantwort bildet eine Barriere für die native Sprotty-
Modellhistorie. Eindeutig zugeordnete Geometrie und der Viewport werden übernommen,
aber Undo darf keinen alten Quell-/Projektionsgraphen wiederherstellen.
Selektion/Drag/Route im aktuellen Graphen verwenden weiterhin echte Commands.
Die DOMaktualisierung bleibt ein VNodepatch; eine Selektionsänderung fordert keine
neue Analyse oder Projektion an. Core-IDs enthalten im aktuellen Indexer auch
Revision/Fingerprint und wechseln tatsächlich bei einer Quelldeklarationsänderung;
sie sind deshalb ausdrücklich kein sitzungsübergreifender Presentationkey.

<a id="lokale-sichtfilter-und-begrenzter-viewzustand"></a>
### lokale Sichtfilter und begrenzter Viewzustand

`DiagramFilterController` und `visibleDocument` besitzen ausschliesslich die
Darstellung bereits projizierter Kategorien, Kanten und Kompartimente. Sie ändern
keine fachliche Auswahl und lösen keine Compileranalyse aus. Importtiefen folgen
nur expliziten `MODEL_IMPORT`-Kanten vom Core-ausgewählten Modell; Labels werden
nicht als Namen aufgelöst. Externe Stubs müssen über verbleibende sichtbare
Verbindungen von einem normalen Knoten erreichbar sein; leere Rahmen entfallen.
Ausblenden löscht keine Geometrie aus dem separaten Viewzustand.

`SavedViewState.version=1` enthält begrenzte Geometrie, Pin-/Manuellmarkierungen,
Filter, Layoutsettings und Viewport. Persistente Schlüssel sind SHA-256 der
exakten eindeutigen Presentationkeys; Quelltext, Quell-URIs mit Credentials,
Kommentar-/Labeltexte und Snapshot-Semantik werden nicht gespeichert. Beim Laden
werden die Hashes nur gegen eindeutige aktuelle Corekeys abgeglichen. Umbenannte,
entfernte, mehrdeutige oder quelllose Elemente erhalten keine fremde Geometrie.
Unbekannte Felder, nicht finite/überdimensionierte Zahlen, ungültige Settings,
mehr als1000 Knoten-/Kantenrecords, mehr als256 Punkte pro Route oder mehr als1MB
serialisierter Zustand werden abgewiesen. Die Hostablage besitzt zusätzlich eine
begrenzte Anzahl Workspace-/Root-/Panelschlüssel. Viewstate ist keine Befugnis
für Navigation oder Spracheingriffe; diese bleiben versionsgebundene Javaqueries.

Inline-Enumerationen deklarierter Attribute verwenden ebenfalls `ENUM_VALUES`.
Der Core liefert geordnete Pfade mit Attributpräfix, Original-Enum-Symbol/Anker
und effektivem Besitzer. Geerbte Darstellungen haben eigene IDs und denselben
ursprünglichen Anker. Domainreferenzen behalten ihre Domainknoten; der Renderer
berechnet keine Enumsemantik aus Typtext. Das gemeinsame Memberbudget und
`InheritedMembers.HIDE` gelten auch hier.

`DiagramCanvas` integriert diese Filter in denselben echten Sprotty-Graphen.
Sein Beobachter vereinigt sichtbare Geometrie mit ausgeblendeten Elementen;
Snapshotwechsel gleichen auch Pin-/Manuell-/Hiddenmengen über die exakten
Presentationkeys ab. Eigene begrenzte Viewhistory enthält nur Geometrie und
überschreitet keine externe Projektionsantwort. `settle` erhält manuelle/pinnte
Knoten und manuelle Routen; `resetLayout` verwirft diese explizit, aber keine Filter.
Das interaktive Layout verwendet den ELK-Worker. Für das Inkscape-Ziel kann
Libavoid orthogonale und polygonale Routen berechnen. Splines bleiben
kubische Bézierpfade. Die fachlichen Endpunkte bleiben unverändert.

`exportSvg(FULL|VISIBLE)` serialisiert die aktuelle gefilterte Sprotty-Zeichnung
mit berechneten Styles und lokalen Referenzen. FULL verwendet gemalte Bounds
inklusive Labels, VISIBLE den tatsächlichen Viewport. Keine neue Projektion oder
ELKberechnung. Handles/Hitflächen/aktive Ressourcen sind ausgeschlossen. Partielle
Sichten bleiben sichtbar markiert. Inkscape-Verbindungen verweisen auf tatsächliche
Shapes; Splines erhalten nur statische Verweise, keine falschen Liveconnectoren.
GAPS dekoriert geometrische Kreuzungen mit lokalen Masken, ohne Kanten zu entfernen;
die Berechnung ist auf200000 Segmentvergleiche und64 Lücken pro Kante begrenzt.
`exportPng` rastert genau dieses SVG lokal; maximal8192 Pixel pro Achse/32MP,
Skalierung0.5..3. Ein währenddessen veränderter View bricht das Ergebnis ab.
Beide Exporte begrenzen ihre Nutzlast auf16MB; der Host besitzt Save/Cancel,
Zieldatei und abschliessendes Schreiben, nicht der Renderer.

`mountDiagramControls` ist die lokale Bedienoberfläche der VS-Code-Extension. Native Zeiger-/Tastatureingaben, ResizeObserver, begrenzter Pan und
Viewportzoom verwenden CSSpixel; die Rasterauflösung ist explizit. Toolbarfokus
und Quelleditorfokus dürfen von Hintergrundpatches nicht übernommen werden.
Ein Themewechsel ändert nur die Darstellung. Exporte verwenden die bei
Exportbeginn berechneten Farben.

Lokale Sichtaktionen dürfen einen durch den Host invalidierten Graphen nicht
wieder als aktuell ausgeben. Der Canvas behält dafür Quellfrische getrennt von
lokaler Layoutaktivität. Filter-/Settingsänderungen während einer ausstehenden
Quellantwort werden für die nächste aktuelle Antwort vorgemerkt; eine während
ELK geänderte Option verwirft deren überholtes Layout. STALE/LOADING erlaubt
keinen Export. Auch der Host prüft Frische zusätzlich zur Generation für
Navigation, Source-Reveal, semantischen Fokus, Persistenz und Exportdialog.

<a id="reine-dokumentationsprojektion"></a>
### Reine Dokumentationsprojektion

Das JVM-Verbrauchermodul `interlis-documentation` hängt vom neutralen Modell
und der bestehenden Diagrammprojektion ab; der Compiler hängt nicht davon ab.
`DocumentationProjector.project(CompiledSchema, DocumentationOptions,
CancellationToken)` akzeptiert ausschliesslich gategeprüfte Schemas. Optionen
besitzen Root-IDs, Labelsprache DE/FR/IT/EN, SEPARATE/INLINE/INLINE_ABSTRACT_ONLY,
validierte Headeroverrides und höchstens20000 Einträge. Erreichen des Budgets
führt zu Ablehnung, nicht zu einem scheinbar vollständigen Katalog.

`DocumentationDocument` kopiert Abschnitte, Member, Herkunft, Typen,
Kardinalitäten, Constraints, Dokumentation und Referenzen. Strukturen werden
referenziert, nicht rekursiv inline expandiert. Identifikatoren/Kommentare
werden niemals übersetzt. `MarkdownDocumentationRenderer`,
`HtmlDocumentationRenderer` und `DocxDocumentationRenderer` rendern nur dieses
Dokument, ohne Compiler-/Datei-/Netz-IO. Inhalte werden zielgerecht escaped;
HTML ist inert, DOCX besitzt echte OOXML-Tabellen/Styles/Beziehungen ohne
Fremdfonts oder externe Ressourcen. `GraphMlRenderer` liest ausschliesslich
`DiagramDocument`; n-äre Assoziationen behalten Knoten und Rollen. Geometrie
wird nicht erfunden. Mermaid-/PlantUML-Text benutzt die bestehenden Renderer;
deren Textnotation ersetzt keinen interaktiven Sprottyview.

Die Dokumentprojektion begrenzt zusätzlich den summierten Text auf4 Millionen
UTF-16-Codeeinheiten, bevor Vererbung Inhalte vervielfachen kann. Renderer prüfen
das Präsentationsbudget nach jeder Tabellenzeile; der LSP begrenzt die tatsächlich
UTF-8-kodierte Antwort weiterhin auf8MB. Quellrevisionen und Snapshot werden in
allen Katalogformaten ausgegeben. Vordefinierte Namen stammen aus dem kanonischen
`PredefinedOrigin.key`, einschliesslich `INTERLIS.XMLDate` für den gebundenen
Datumstyp. Der GraphML-Einstieg exponiert `DiagramDocument`; daher ist das
Projektionsmodul eine öffentliche API-Abhängigkeit der Dokumentation.

## Künftige grafische Bearbeitung

Grafische Quellbearbeitung ist ein eigener Entwurf. Eine solche Operation
benötigt einen Command mit erwarteter Snapshot- und Quellrevision, einen
semantisch geprüften Änderungsplan und die Textedit- und Konfliktregeln des
[Workspace](workspace-and-lsp.md#rename). Anschliessend folgt neue Analyse
und Projektion. Ein mutierter Diagrammgraph wird nicht als INTERLIS-Quelle
persistiert. Die Projektion bleibt unabhängig von einem GLSP-Servermodell.
