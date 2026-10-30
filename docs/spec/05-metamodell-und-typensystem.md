# 05 – Metamodell und Typensystem

## 1. Ziel, Modul und Abgrenzung

Das Modul `interlis-model` enthält das eigene fachliche Modell sowie die kleinen, gemeinsam benötigten Quelltext- und Diagnostiktypen. Es hängt nicht von ANTLR, Compiler, Workspace, Repositories, LSP, Datenbanken oder einer Geometrie-Engine ab. `guru.interlis.model` und `guru.interlis.model.query` bilden die öffentliche Modell-API; interne Aufbauhilfen müssen als solche abgegrenzt bleiben.

**Das Metamodell ist der zentrale gemeinsame fachliche Vertrag des neuen Stacks.** Eine Klassenliste für Diagramme allein erfüllt diesen Vertrag nicht. Ebenso wenig genügt ein Baum, der die Grammatikproduktionen ohne Namensauflösung abbildet. Das Modell muss vom Compiler geprüfte Beziehungen, effektive Eigenschaften und typisierte Ausdrücke ausdrücken können. Dasselbe Datenmodell muss während der Analyse unvollständige oder fehlerhafte Zustände ausdrücklich repräsentieren können.

Die bisherigen Verbraucher sind wichtige Vergleichsquellen. [iox-ili/Validator.java](https://github.com/claeis/iox-ili/blob/2807992c45bb10d18f44ea4a882fc19f6dd1e4cb/src/main/java/ch/interlis/iox_j/validator/Validator.java) und [ili2c/Viewable.java](https://github.com/claeis/ili2c/blob/cbccff35fc98fdfa370e42e3bb39bb4edd70a658/ili2c-core/src/main/java/ch/interlis/ili2c/metamodel/Viewable.java) zeigen, dass unter anderem aufgelöste Typen, Vererbung und effektive Attribut-/Rollenreihenfolgen fachlich gebraucht werden. Diese Informationen dürfen nicht erst in jedem Verbraucher neu interpretiert werden.

IlisMeta07 und IlisMeta16 werden als standardisierte fachliche Referenz berücksichtigt; sie sind in P3 keine Exportziele. Sie ersetzen nicht die zusätzlichen Zustände für Fehler, Herkunft, Referenzsegmente und Analysedeckung. Primärquelle: [INTERLIS-Metamodell](https://www.interlis.ch/modelle/metamodell).

## 2. Normativer Katalog

| ID | Normative Anforderung | Phase | Abnahme |
|---|---|---|---|
| META-001 | Veröffentlichte Modellgraphen MÜSSEN unveränderlich sein, einschliesslich enthaltener Collections. | P1 | Keine Änderung über zurückgegebene Listen, Maps, Builder oder Consumer. |
| META-002 | Fehlerhafte Analyse und erfolgreich geprüftes Schema MÜSSEN im API unterscheidbar sein. | P1 | `CompiledSchema` nur über das Gate in Kapitel 06; Teilanalyse ohne Erfolgssiegel. |
| META-003 | Beziehungen MÜSSEN Identitäten verwenden, die nicht allein aus Namen bestehen. | P1 | Doppelte Namen, Rename und Revisionen ergeben keine falsche Vereinigung. |
| META-004 | Jede öffentliche Abfrage mit einem Handle MUSS dessen Snapshotzugehörigkeit prüfen. | P1 | Verwendung eines alten `SymbolRef` löst einen definierten API-Fehler aus. |
| META-005 | Auflösungsfehler, noch nicht analysierte Teile und rechtmässige spätere Bindung MÜSSEN unterschiedliche Zustände besitzen. | P1/P2 | Fehlender Domänenname, `ErrorType` und legale Generic-Domäne bleiben unterscheidbar. |
| META-006 | Alle Sprachkonstrukte im Featurekatalog MÜSSEN bis P2 semantisch repräsentiert und geprüft sein. | P2 | Katalogzeile besitzt Konstruktor-/Analyse-/Negativ-/Export- oder Querytest. |
| META-007 | Kanonische Symbolidentität und übersetzte beziehungsweise transferspezifische Namen MÜSSEN getrennt sein. | P2 | Übersetzungsfixture verändert keine Referenzidentität. |
| META-008 | Deklarationsreihenfolge und effektive Attribut-/Rollenreihenfolge MÜSSEN explizit modelliert sein. | P1/P2 | Goldenvergleich mit Legacyfällen für Vererbung, Überschreibung und eingebettete Rollen. |
| META-009 | Numerische Werte MÜSSEN exakt und mit der benötigten INTERLIS-Genauigkeitsinformation gespeichert werden. | P1 | Zahlen grösser 2^53, Skalen, Exponenten und Grenzen ohne Doubleverlust. |
| META-010 | `CompiledSchema` MUSS genau die gewählten Wurzelmodelle samt geprüfter Abhängigkeitsmenge beschreiben. | P1 | Fehler in unabhängiger Quelle blockiert nicht; Fehler in Importclosure blockiert. |
| META-011 | Quellbezug und fachliche Herkunft MÜSSEN auch für geerbte, synthetische und vordefinierte Elemente nachvollziehbar sein. | P1 | Geerbtes Attribut führt auf Originaldeklaration zurück. |
| META-012 | Konfigurationsoverlays und Laufzeitcaches DÜRFEN das gemeinsame Modell nicht mutieren. | P1/P4 | Zwei unterschiedlich konfigurierte Verbraucher können dasselbe Schema parallel verwenden. |
| META-013 | Modellgültigkeit DARF NICHT von den Fähigkeiten eines einzelnen späteren Consumers abhängen. | P1/P2 | Gültiger View wird nicht wegen fehlendem SQL-Exporter abgelehnt oder still verworfen. |
| META-014 | Ein unbekanntes Modellfeature DARF NICHT als untypisierte Map, `Object`, `Any` oder leere Liste verschwinden. | P1/P2 | Exhaustive Variantenprüfung und sichtbare Coverage-Lücke. |
| META-015 | Builtins MÜSSEN versionsspezifisch, unveränderlich und ohne versteckten globalen Laufzustand verfügbar sein. | P1/P2 | Parallelkompilierung beider Versionen beeinflusst keine andere Sitzung. |
| META-016 | Typbeziehungen MÜSSEN über typisierte Ergebnisobjekte erklärt werden können. | P1/P2 | Unzulässige Zuweisung liefert Grund und betroffene Teiltypen. |
| META-017 | Semantische Fingerprints MÜSSEN fachliche Reihenfolge, Optionen und Abhängigkeiten berücksichtigen. | P2 | Fachliche Änderung verändert Fingerprint; Run-ID und Uhrzeit tun dies nicht. |

## 3. Identitäten, Referenzen und Abfragen

### 3.1 Werttypen und Gültigkeitsbereich

```java
public record SnapshotId(String value) {}
public record SymbolId(String value) {}
public record TypeId(String value) {}
public record ExpressionId(String value) {}
public record ScopeId(String value) {}
public record ReferenceId(String value) {}

public record SymbolRef(SnapshotId snapshot, SymbolId symbol) {}
public record TypeRef(SnapshotId snapshot, TypeId type) {}
public record ExpressionRef(SnapshotId snapshot, ExpressionId expression) {}
public record ScopeRef(SnapshotId snapshot, ScopeId scope) {}
```

Strings sind nichtleer, opak und nicht als FQN zu parsen. Die genaue Kodierung ist eine interne Entscheidung, aber die öffentlichen Semantiken sind verbindlich. Ein `SymbolId` kann bei nachweislich derselben Deklaration innerhalb einer Sitzung über Revisionen stabil bleiben. Ein `SymbolRef` bleibt trotzdem an seinen Snapshot gebunden. Cross-Session-Gleichheit ist nicht zugesichert.

Graphinterne Kanten verwenden die kleineren IDs. An einer öffentlichen Abfragegrenze werden Handles mit Snapshot verwendet. Ein Aufrufer, der eine stabile ID bewusst im neuen Snapshot nachschlagen will, muss dies explizit über `rebindSymbol(SymbolId)` tun. Diese Methode prüft die Existenz; sie behauptet keine Gleichheit der alten und neuen fachlichen Eigenschaften.

`SnapshotMismatchException extends IllegalArgumentException` bezeichnet API-Fehlgebrauch. Ein alter Handle darf nicht zufällig das neue Symbol gleichen Namens zurückgeben. Fehlende Namen im Benutzermodell sind dagegen fachliche Diagnostik und keine Java-Ausnahme.

### 3.2 Gültiges generisches `QueryResult`

Die folgenden Java-Signaturen sind normativ. Die verschachtelten Records sind unmittelbare Implementierungen desselben generischen Interface; es werden keine rohen Typen und keine nicht zulässigen generischen `permits`-Sonderfälle verwendet.

```java
public sealed interface QueryResult<T>
        permits QueryResult.Known, QueryResult.Unresolved,
                QueryResult.Invalid, QueryResult.NotAnalyzed {

    record Known<T>(T value) implements QueryResult<T> {}

    record Unresolved<T>(
        UnresolvedReason reason,
        List<SymbolRef> candidates
    ) implements QueryResult<T> {}

    record Invalid<T>(
        List<DiagnosticId> diagnosticIds
    ) implements QueryResult<T> {}

    record NotAnalyzed<T>(
        NotAnalyzedReason reason
    ) implements QueryResult<T> {}
}
```

Die Implementierung ergänzt Nullprüfungen und defensive Kopien. `Known.value` ist niemals null; `Invalid.diagnosticIds` ist nichtleer und referenziert Diagnosen desselben Snapshots. `Unresolved` bedeutet, dass die Abfrage versucht wurde, aber keine eindeutige fachliche Bindung vorliegt. `NotAnalyzed` bedeutet fehlende Analyseabdeckung, etwa wegen Abbruch, nicht unterstützter P1-Fähigkeit oder einer noch nicht angeforderten Teilanalyse. Eine rechtmässige aufgeschobene Domäne ist `Known<GenericType>`, nicht `Unresolved`.

`UnresolvedReason` und `NotAnalyzedReason` sind geschlossene Reason-Enums oder typisierte Varianten mit stabiler Kennung. Meldungstexte sind keine Reason-Identitäten. Ob ein Name fehlt oder mehrdeutig ist, muss für Completion und Diagnose unterscheidbar sein. Kandidaten besitzen Quell- beziehungsweise Symbolprovenienz; es erfolgt keine willkürliche Auswahl des ersten Kandidaten.

### 3.3 `SemanticQueries`

`SemanticQueries` ist eine read-only Sicht auf genau einen `SemanticGraph`. Die folgenden Methoden bilden die erste verbindliche Oberfläche; zusätzliche Komfortmethoden müssen auf denselben Indizes beruhen.

| Signatur | Bedeutung |
|---|---|
| `SnapshotId snapshotId()` | Identität des gebundenen Graphen. |
| `QueryResult<Symbol> symbol(SymbolRef ref)` | Vollständiger aktueller Symboldeskriptor oder definierter Zustand. |
| `QueryResult<TypeDescriptor> type(TypeRef ref)` | Typ mit etwaigen legalen Bindungsbedingungen. |
| `QueryResult<TypedExpression> expression(ExpressionRef ref)` | Typisierter Ausdruck samt Ursprung. |
| `QueryResult<TypeDescriptor> declaredType(SymbolRef ref)` | Expliziter oder abgeleiteter deklarierter Typ; keine stille Aliasauflösung. |
| `QueryResult<EffectiveType> effectiveType(SymbolRef ref)` | Aufgelöste Typwirkung mit Alias-/Restriktionspfad. |
| `QueryResult<List<EffectiveMember>> effectiveMembers(SymbolRef viewable)` | Fachliche, deterministische Reihenfolge von Attributen und Rollen. |
| `QueryResult<SymbolRef> resolveReference(ReferenceId reference)` | An diese Querysicht gebundene Referenzauflösung. |
| `QueryResult<List<SymbolRef>> visibleSymbols(ScopeRef scope, NamePrefix prefix)` | Sichtbarkeit nach Sprachregeln; kein globales FQN-Matching. |
| `List<SymbolRef> findQualifiedName(QualifiedName name)` | Alle Kandidaten; ein Duplikat ist kein Erfolg mit dem ersten Treffer. |
| `Optional<SymbolRef> rebindSymbol(SymbolId id)` | Explizite ID-Suche im aktuellen Snapshot. |
| `List<NameReference> referencesTo(SymbolRef target)` | Originale Referenzvorkommen, einschliesslich Namenssegmenten. |

Eine nackte `ReferenceId` ist in `resolveReference` ausdrücklich lokal zur bereits gebundenen Querysicht. Wird sie ausserhalb dieser Sicht transportiert, muss sie zusammen mit deren `SnapshotId` übertragen werden; Browser-/LSP-DTOs enthalten diesen Kontext. Diese Regel gilt für alle kompakten ID-Listen in Resultaten.

## 4. Semantischer Graph und Symbolhierarchie

### 4.1 `SemanticGraph`

`public final class SemanticGraph` besitzt unveränderliche Indizes für Symbole, Typen, Ausdrücke, Scopes, Referenzen und Herkunft. Öffentliche Getter geben read-only Sichten zurück. Die Struktur wird in einem laufbezogenen Builder aufgebaut und beim Freeze vollständig validiert. Der Builder darf nach dem Freeze keine Arrays oder Collections mehr besitzen, mit denen die veröffentlichte Sicht veränderbar bleibt.

Graphinvarianten:

- Jede eigene ID ist in ihrer Kategorie eindeutig.
- Jede vorhandene ID-Kante zeigt auf ein vorhandenes Element desselben Graphen.
- Noch nicht aufgelöste Kanten werden durch Auflösungszustände beschrieben, nicht durch erfundene IDs.
- Containment besitzt genau einen fachlichen Eigentümer, ausser expliziten Wurzel-/Builtin-Elementen; Containmentzyklen sind verboten.
- Semantisch zulässige Referenzzyklen sind darstellbar. Unzulässige Vererbungs-/Typaliaszyklen werden diagnostiziert und nicht durch Rekursion bis zum Stackoverflow verarbeitet.
- Jede SourceRange verweist auf eine im Snapshot bekannte Revision oder auf eine ausdrücklich synthetische Herkunft ohne vorgetäuschten Originalbereich.

`SemanticGraphBuilder` kann im Modellmodul als ausdrücklich interne Aufbauhilfe liegen, damit das Compiler-Modul Graphen ohne umgekehrte Abhängigkeit erzeugen kann. Diese Aufbauhilfe ist keine alternative fachliche Compiler-API. Der Graph darf partielle Daten enthalten; nur das Gate in Kapitel 06 darf ihnen die Eigenschaft eines `CompiledSchema` verleihen.

### 4.2 Gemeinsamer Symbolkopf

`SymbolHeader` enthält `SymbolId id`, `SymbolKind kind`, `DeclaredName name`, `Optional<SymbolId> owner`, `int declarationOrder`, `Origin origin`, `Documentation documentation`, `MetaAttributeSet metaAttributes` und typisierte Modifier. Die Implementierung verhindert unzulässige Kombinationen bereits soweit lokal entscheidbar. Beispielsweise darf ein Symbol nicht gleichzeitig zwei unabhängige Owner haben.

`DeclaredName` unterscheidet einen wirklichen Quellnamen von einem synthetischen Anzeigenamen. Eine unbenannte Assoziation bekommt eine stabile interne Identität, aber keinen erfundenen Benutzerquellnamen. `QualifiedName` ist eine geordnete Segmentliste für Anzeige und Namensindex. Sie ist keine Identität.

`Origin` ist eine geschlossene Hierarchie: `DeclaredOrigin(SourceRange)`, `DerivedOrigin(SymbolId declaredAt, DerivationKind)`, `PredefinedOrigin(LanguageVersion, String key)` und `SyntheticOrigin(SyntheticReason, Optional<SourceRange>)`. Bei einer geerbten Eigenschaft bleiben Deklarationsort und effektiver Besitzer getrennt.

### 4.3 Klasseninventar

Die folgenden Typen sind echte fachliche Varianten. Eine leere generische `Element`-Basisklasse mit später beliebigen Properties erfüllt den Auftrag nicht.

| Typ | Wesentliche Felder zusätzlich zum Kopf | Zentrale Invarianten/Abfragen |
|---|---|---|
| `ModelSymbol` | Sprachversion, Modellversionsliteral, Sprachkennung, Issuer/AT, Modellart, Imports, Translationbezug, geordnete Mitglieder | Sprach- und Modellversion getrennt; mehrere Modelle je Quelle möglich. |
| `TopicSymbol` | Basistopic, Dependencies, OID-/BasketOID-Regeln, deferred generics, Mitglieder | Vererbungs-/Dependencyregeln versionsbezogen. |
| `ClassSymbol` | Basisklasse oder Basisstruktur, Attribute, Constraints, OID-Regel | Gebundene Identität; Struktur-/Klassenunterschied bleibt explizit. |
| `StructureSymbol` | Basistyp, Attribute, Constraints | Keine erfundene Objektidentität; Einbettung separat. |
| `AssociationSymbol` | optionale Originalbezeichnung, Rollen, Attribute, Constraints, OID-/Kardinalitätsregel, Derived-From | N-stelligkeit und Assoziationen mit Attributen bleiben ausdrückbar. |
| `ViewSymbol` | Viewformation, Basen/Aliase, Selektionsausdrücke, abgeleitete Attribute | Formation als geschlossener Typ, keine beliebigen SQL-Fragmente. |
| `AttributeSymbol` | declared TypeId/Typzustand, Ableitungsausdrücke, Modifier, Überschreibungsbezug | Mandatory, transient, extended und effektive Reihenfolge getrennt. |
| `RoleSymbol` | Ziele/Restriktionen, Aggregationsart, Kardinalität, Ordnung, external/hiding, Ableitung | Rollen sind keine normalen Referenzattribute. |
| `DomainSymbol` | Typbeschreibung, Basistyp, Generic-Markierung, Domänenconstraints | Alias und einschränkende Domänenerweiterung unterscheiden. |
| `UnitSymbol` | Dimension, Basisbezug, Definition/Umrechnung, Symbolname | Exakte oder symbolische Umrechnung; keine obligatorische Double-Normalisierung. |
| `EnumElementSymbol` | Elternpfad, Kinder, Deklarations-/Ordnungsposition, Erweiterungsbezug | Baum und flache Blattliste getrennte Sichten. |
| `FunctionSymbol` | Parameter, Rückgabetyp, Erklärung, externer Funktionsschlüssel | Compiler prüft Signatur; er lädt keine Implementierungsklasse. |
| `ParameterSymbol` | Parameterart, Typ, Scope, optionaler Override | Funktions-, Laufzeit- und Signaturparameter getrennt. |
| `ConstraintSymbol` | `ConstraintDefinition`, Owner, optionaler Name, Herkunft | Kein untypisierter Ausdruckstext als einzige Repräsentation. |
| `ContextSymbol` | geordnete Generic-zu-Konkrettyp-Zuordnungen | Auswahlbedingungen und erlaubte Alternativen erhalten. |
| `MetadataBasketSymbol` / `MetaObjectSymbol` | Referenztopic/-klasse, geerbter Basket, Objektdeklarationen | Modellinformationen nicht mit transferseitigen Datenbaskets verwechseln. |
| `LineFormSymbol` | Name und referenzierte Segmentstruktur | Benutzerdefinierte Kurvenformen bleiben semantisch referenziert. |
| `GraphicSymbol` / `DrawingRuleSymbol` | Basis, Selektionen, bedingte Signaturparameterzuordnungen | Fachliche GRAPHIC-Sprache; unabhängig von der späteren UML-Visualisierung. |

`Symbol` ist sealed. `ViewableSymbol` ist die gemeinsame sealed Unterhierarchie für Klasse, Struktur, Assoziation und View. Records werden verwendet, wenn ihr struktureller Wertcharakter passend ist; graphübergreifende Beziehungen bleiben IDs, damit `equals()` keine zyklischen Objektgraphen durchläuft.

Die laufende Vorab-API 0.2 konkretisiert Laufzeitparameter als
`ParameterSymbol(header, typeId, mandatory, overrides)` in `interlis-model` /
`guru.interlis.model`. Der Header enthält Modell- oder Klassen-/Struktur-Scope, Quellursprung und
Deklarationsreihenfolge. `overrides` enthält optional die geerbte Parameteridentität. Funktionsargumente bleiben `FunctionParameter`;
Signatur-/METAOBJECT-Parameter verwenden denselben Parametervertrag und den unten beschriebenen MetaObjectType.
`SymbolValueExpression` enthält die gebundene `targetSymbolId` eines
Laufzeitparameters, einer Klassen- oder Attributpfadkonstante. Die Zielart ist
am Symbol ablesbar; fehlende Bindungen erzeugen Fehlerausdrücke statt Scheinziele.
`ClassValueType.restrictions()` liefert geordnete `SymbolId` statt Namenstrings.
`structureValue` unterscheidet STRUCTURE (auch Klassen) von CLASS (nur Klassen).
`AttributePathType` bewahrt geordnete Typrestriktionen und optional einen
`Owner`: `ClassAttribute(ExpressionId path)`, `Argument(SymbolId function,
int index)` oder `Viewable(SymbolId target)` für Konstanten. Der Index ist
nullbasiert in der unveränderlichen Funktionsparameterliste. OF-Ausdrücke
bleiben auch im ausgewählten `CompiledSchema` erreichbar. Alle Werte sind
nullfrei, unveränderlich und an denselben Graphsnapshot gebunden. Die Konstruktoren
kopieren Listen; fehlerhafte Bindungen verhindern das Schema. Es werden keine
Laufzeitwerte erzeugt. `ParameterClassFunctionContractTest` prüft öffentliche
Inhalte, Namespaces, Imports, Versionen, Übersetzungen und negative Typ-/Scopefälle.
Bei Aufrufen liefert `ATTRIBUTE OF @argument` den Bindungskontext einer
unqualifizierten Attributpfadkonstante aus dem tatsächlichen Argument,
wenn dieses eine konkrete Wurzel liefert. Bei einem nicht bestimmbaren
Owner (unbeschränkter Klassenwert oder UNDEFINED) bleibt der ebenfalls
normativ zulässige lexikalische Konstantenkontext erhalten; es wird keine
Klasse für das tatsächliche Argument erfunden.
Verkettete Abhängigkeiten werden vor der abhängigen Konstante gebunden;
die veröffentlichte Argumentliste bleibt dennoch in Quellreihenfolge.
Dies gilt auch für eine zulässige Abhängigkeit auf ein späteres Argument.
Explizit angegebene Klassenziele bleiben unverändert und werden exakt gegen
den erforderlichen Owner geprüft; ein bloss verwandter Klassenname genügt
nicht (2.3 §2.8.11/PDF46–47, 2.4 §3.8.11/PDF59–60: keine Subklasse).
Ein fehlgeschlagenes abhängiges Argument liefert keinen Ersatzkontext aus
dem aufrufenden Owner. Allgemeine ATTRIBUTE-Parameter ohne OF behalten
den gewöhnlichen lexikalischen Kontext. Diese Aufrufbindung ist kein
Vollständigkeitsnachweis für sämtliche Attributpfadoperationen.
Ein tatsächlich übergebener ATTRIBUTE-Wert mit `OF` auf genau denselben
gebundenen klassenwertigen Ausdruck erfüllt den abhängigen Ownervertrag;
eine gleiche Typrestriktion allein ersetzt diese Wertidentität nicht.
Bei einem abhängigen ATTRIBUTE-Rückgabetyp erhält ein erfolgreicher Aufruf
mit bindbarem Owner einen eigenen gebundenen Typ: konstante Klassenziele bleiben
`Viewable`, klassenwertige tatsächliche Ausdrücke bleiben als
`ClassAttribute(ExpressionId)` im selben Snapshot erreichbar. Die formale
Rückgabetypdeklaration und ihre `Argument`-Abhängigkeit bleiben unverändert.
Externe Funktionen werden dabei nicht ausgeführt.
Liefert ein zulässiges tatsächliches Argument keinen bindbaren Owner
(UNDEFINED oder eine Objektmenge mit unbeschränkter ANYCLASS-Wurzel), bleibt
der formale `Argument`-Bezug im Ergebnistyp erhalten. `FunctionCallExpression`
bewahrt das tatsächliche Funktionsziel und die geordneten tatsächlichen
Argumente im selben Snapshot. Dies ist eine legale wertabhängige Bindung,
kein Quellfehler, kein fehlender Implementierungszweig und kein erfundener
konkreter Owner. `ExpressionTypeState.RESOLVED` bezeichnet hier den geprüften
statischen Vertrag, nicht einen ausgeführten oder bestimmten Laufzeitwert.

In Vorab-API 0.2 repräsentiert `FunctionParameter(String name, TypeId typeId, boolean mandatory)`
einen Parameter; `FunctionSymbol.returnType()` liefert ebenfalls `TypeId`.
Beide Typreferenzen gehören zum selben semantischen Graphen wie die Funktion.
Die Parameterliste ist defensiv kopiert, nullfrei und in Deklarationsreihenfolge.
Fehlerhafte oder noch nicht implementierte Typen werden ausdrücklich als solche
modelliert und dürfen keinen gültigen Compileabschluss erzeugen.
Aufrufe prüfen Argumentanzahl und Zuweisungsverträglichkeit über dieselben
Typregeln wie `TypeRelations.canAssign`; der Ausdruck übernimmt den deklarierten
Rückgabetyp. Die Signatur beschreibt keine Ausführung externer Funktionen.

Versionsregel: INTERLIS 2.3 verlangt `CONTRACTED` für Modelle mit Funktionen
([Referenz 2.3, Abschnitt 2.14](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf)).
In INTERLIS 2.4 hat der Modellmodifikator keine Bedeutung mehr
([Referenz 2.4](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf)).
Sein Fehlen ist dort kein Fehler, seine Anwesenheit kein Warnungsgrund.
Die syntaktische Information bleibt erhalten.

Fehlerhafte Objektpfade dürfen in einer Analyse `PathStep.UnresolvedStep(name,
range)` enthalten. Dieser nullfreie unveränderliche Schritt besitzt kein
Referenzziel und darf nicht als `THIS` interpretiert werden. Ein solcher
Bindungsfehler verhindert ein gültiges Schema für die betroffene Closure.

### 4.4 Referenzsegmente

`NameReference` enthält `ReferenceId`, ursprünglichen Scope, Verwendungsart, eine geordnete Liste `ReferenceSegment`, den Gesamtbereich und `BindingResolution`. Jedes Segment besitzt den originalen Textbereich und seinen eigenen optionalen fachlichen Zielbezug. Ein vollständiger Pfad `Model.Topic.Class` kann deshalb separat auf Modell, Topic und Klasse navigieren.

`BindingResolution` unterscheidet `BoundTarget(SymbolId)`, `UnresolvedTarget(reason,candidates)`, `InvalidTarget(diagnosticIds)` und `NotAnalyzedTarget(reason)`. Felder werden nicht mit null markiert. Synthetische Bezüge, beispielsweise abgeleitete Vererbungskanten, sind keine vorgetäuschten Textreferenzen. Diese Gestaltung greift die quellsegmentbezogene Referenzidee in [GeoWerkstatt/Reference.cs](https://github.com/geowerkstatt/GeoW.Interlis.Tools/blob/02f89943b08030b898c56da6eb9b9cb7bdc361f7/src/Geowerkstatt.Interlis.Compiler/AST/Reference.cs) auf.

## 5. Typensystem und exakte Werte

### 5.1 Geschlossene Typvarianten

`TypeDescriptor` ist sealed und besitzt eine `TypeId`, Typart und Herkunft. Die folgende Menge ist als fachlicher Mindestumfang verbindlich; Hilfsvarianten dürfen ergänzt werden, wenn sie einen nachgewiesenen Sprachunterschied ausdrücken.

| Typfamilie | Erforderliche Varianten/Informationen |
|---|---|
| Text | `TextType` mit TEXT/MTEXT/NAME/URI-Semantik, optionaler Längenbegrenzung und Erweiterungsbedingungen. |
| Bool und Ausrichtung | `BooleanType`, `AlignmentType` mit horizontaler/vertikaler Art und definierten Werten. |
| Numerik | `NumericType` mit exakten Grenzen, Genauigkeit, Einheit, Zirkularität, Richtung und Referenzsystembezug. |
| Aufzählungen | `EnumerationType`, `EnumTreeValueType`; Baum, ORDERED/CIRCULAR, FINAL-Teile und Erweiterung. |
| Formatierte Werte | `FormattedType`; Basisstruktur/-domäne, Formatteile, Teilattributbezüge und Grenzen. |
| Zeit | Vordefinierte `DomainSymbol`-/`FormattedType`-Werte mit gebundenen Basisstrukturen und Attributen; `TemporalKind` bezeichnet ihre Zeitart. Die 2.4-Schlüsselwörter verwenden dieselben Domänen. |
| Geometrie | `CoordinateType`, `MultiCoordinateType`, `LineType`, `MultiLineType`, `SurfaceType`, `MultiSurfaceType`, `AreaType`, `MultiAreaType`. |
| Identifikation | `OidType` mit ANY/numerisch/Text/UUID-Semantik und eigener Herkunft. |
| Blackbox | `BlackboxType` mit XML/BINARY-Art. |
| Strukturen/Collections | `StructureValueType`, `CollectionType` mit BAG/LIST, Kardinalität und Restriktionen. |
| Referenzen | `ReferenceType` mit Zielmenge, EXTERNAL-Regel und Restriktionen. |
| Klassen-/Pfadwerte | `ClassValueType`, `AttributePathType`; Werte sind fachliche Klassen/Attributpfade, keine normalen Objektverweise. |
| Funktionsargumente | `ObjectArgumentType` für einzelne Objekte und Objektmengen, `EnumValueArgumentType`, `EnumTreeValueArgumentType`. |

`ObjectArgumentType(TypeId, Origin, Optional<SymbolId> base, List<SymbolId> restrictions,
boolean objects)` verwendet eine gemeinsame Zielbeschreibung für `OBJECT OF` und
`OBJECTS OF`. `objects=false` bezeichnet ein einzelnes Objekt (`OBJECT_ARGUMENT`),
`true` eine Objektmenge (`OBJECT_SET`). Die leere Basis bezeichnet `ANYCLASS`;
die leere Einschränkungsliste bedeutet keine zusätzliche Einschränkung.
Andernfalls enthält sie zulässige Spezialisierungen in Quellreihenfolge.
Alle Werte sind nullfrei und unveränderlich; Symbol- und Typreferenzen gehören
zum selben Snapshot. Die Typbeziehung prüft gebundene Identitäten und deren
Vererbung. Ein einzelnes Objekt kann als Menge mit höchstens einem Element
übergeben werden; eine statisch mehrwertige Menge erfüllt `OBJECT OF` nicht.
Skalare erfüllen keine der beiden Varianten. Einschränkungen ausserhalb der
Basis ergeben eine Typdiagnose; sie sind kein nachträglicher Laufzeitfilter.
Compiler, `objectCount` und `TypeRelations.canAssign` verwenden dieselbe Regel.
Positive Fälle schliessen Unterklassen, Funktionsresultate und Mengen ein;
negative Fälle fremde Klassen, fehlende Ziele, Skalare, Mengen an Einzelargumenten
und zu weit gefasste Zieltypen. Einzelobjekte sind auf Identität vergleichbar
(`==`, `!=`, `<>`), nicht auf Ordnung. Objektmengen sind keine skalaren
Vergleichswerte. Öffentliche Abfragen und Ausdrucksprüfung müssen übereinstimmen.

`ObjectSetExpression(id, typeId, typeState, range)` repräsentiert `ALL` mit einer
gebundenen Objektmengentypreferenz. Ohne explizite Auswahl ist die Basis die
Kontextklasse; sonst die angegebene Basis samt Einschränkungen. Die immutable,
nullfreie Variante besitzt keine Ausdruckskinder. Fehlerhafte Auswahl erhält
`ERROR` und einen `ErrorType`, niemals ein untypisiertes erfolgreiches `ALL`.

| Alias/Generics | `AliasType`, `GenericType`, `DeferredTypeBinding`; deklarierte und effektive Sicht bleiben unterscheidbar. |
| Analysefehler | `ErrorType` mit Diagnoseursache, `PendingType` mit nicht analysiertem Zustand; niemals produktive fachliche Datentypen. |

`MANDATORY` wird als Null-/Definiertheitsbedingung des Werts beziehungsweise Attributes modelliert. BAG/LIST-Kardinalität und Elementdefiniertheit werden separat behandelt. Ein Defaultwert für Collectiongrenzen oder Rollenmultiplizität muss aus dem jeweiligen Sprachprofil kommen und darf nicht als allgemeines Java-Default geraten werden.

Domainreferenzen werden anhand des gebundenen Ziels ausgewertet, unabhängig
von der Sortierung der Quelldateien. `DomainReferenceType` erhält die Domain-ID;
Ausdrücke und öffentliche Typabfragen verwenden deren aufgelösten Descriptor.
Dies gilt auch für importierte Funktionssignaturen, geerbte Attribute,
Struktur-/Indexpfade, Enum-Basen und FORMAT-Komponenten. Eine noch nicht
aufgebaute Symbolinstanz ist kein Grund für einen generischen Ersatztyp oder
eine Suche nach gleichnamigen Deklarationen ausserhalb der Sichtbarkeit.
Typabhängigkeitszyklen erzeugen Diagnosen und verhindern das gültige Schema;
Domainbedingungen bleiben separat erhalten und werden ebenfalls geprüft.
`ImportedDomainTypeContractTest` prüft positive und negative Mehrdateifälle in
beiden Sortierungen, Typidentitäten, Navigation und frische gegenüber
wiederverwendeter Analyse.

Geometrietypen speichern Achsen, Dimension, Vertexdomäne, Rotation, Referenzsystem, erlaubte Linienformen, Richtung, Überlappungsparameter und gegebenenfalls Linienattribute. Es werden keine JTS-Geometrieobjekte erzeugt. Eine gültige Typbeschreibung ist unabhängig davon, ob ein späterer konkreter Geometriealgorithmus ihre Daten bereits verarbeiten kann.

`UnitSymbol` erhält die operative Kurzbezeichnung im Header und die dokumentarische
Bezeichnung separat als `designation`. `base` bezeichnet die deklarierte
EXTENDS-Basis, `derivedFrom` das Ziel einer Umrechnung. `components` enthält
immutable `Component(operator, unit)`-Werte in Quellreihenfolge; der erste
Operator ist MULTIPLY, die übrigen MULTIPLY oder DIVIDE. IDs gehören zum
selben Snapshot. Bei Basis-/abgeleiteten Einheiten ist die Komponentenliste leer.
Das Feld `definition` bleibt für Anzeige und symbolische Umrechnungsnotation;
die Bindung und Prüfung zusammengesetzter Einheiten darf daraus keine zweite
Semantik ableiten. Dimensionen und vollständige exakte Umrechnungen bleiben
Teil des vollständigen P2-Vertrags.

Abstrakte Zusammensetzungen verlangen ausschliesslich abstrakte Komponenten,
konkrete ausschliesslich konkrete. EXTENDS verlangt eine abstrakte Basis;
eine zusammengesetzte Erweiterung erhält Anzahl und Reihenfolge der Operatoren
und spezialisiert die jeweiligen Komponenten. Importierte Kurzzeichen werden
über sichtbare Deklarationsidentitäten gebunden, unabhängig von Dateisortierung.
Ein nicht sichtbarer Name darf nicht über gleichnamige globale Einheiten
ersetzt werden. Fehlende Ziele und Abhängigkeitszyklen verhindern ein gültiges
Schema; die Traversierung prüft Abbruch und Arbeitsbudget. Gebundene
Komponentennamen und Kurzzeichen behalten ihre Quellbereiche für Navigation
und Rename. `ComposedUnitContractTest` prüft positive/negative Fälle beider
Versionen und den öffentlichen Zugriff auf diese Werte.

### 5.2 Exakte Dezimalzahlen und Kardinalitäten

```java
public record DecimalLiteral(
    String lexeme,
    BigDecimal exactValue,
    DecimalPrecision precision
) {}

public record DecimalPrecision(
    int fractionalDigits,
    int exponent,
    PrecisionOrigin origin
) {}

public record Cardinality(BigInteger lower, CardinalityUpper upper) {}

public sealed interface CardinalityUpper
        permits CardinalityUpper.Finite, CardinalityUpper.Unbounded {
    record Finite(BigInteger value) implements CardinalityUpper {}
    record Unbounded() implements CardinalityUpper {}
}
```

Der Literalparser prüft, dass Lexem, exakter Wert und Präzisionsdaten zusammenpassen. Das Lexem bleibt für Diagnostik und Modelltreue erhalten. Eine intern normalisierte Vergleichsform darf ergänzend existieren. Unabhängige Dokumentationstexte dürfen nicht als numerische Identität dienen.

`DecimalPrecision` ist hier die Speicherform der lexikalischen Angaben. Die fachliche INTERLIS-Genauigkeit wird von `NumericTypeRules` aus Lexem, Exponent und Domänenkontext berechnet und durch Legacy-/Normfixtures abgesichert. Der Coding-Agent darf `BigDecimal.scale()` nicht ohne diesen Schritt mit der INTERLIS-Genauigkeit gleichsetzen. `PI` und `LNBASE` werden als symbolische Konstanten modelliert; der Compiler muss sie nicht stillschweigend in einen gerundeten Double umwandeln.

Kardinalitätsgrenzen sind nichtnegativ. Ein endliches Maximum muss mindestens dem Minimum entsprechen. Unbegrenzt ist eine eigene Variante und weder `-1` noch `Long.MAX_VALUE`. Ob eine bestimmte Kardinalität in einem Sprachkontext erlaubt ist, prüft die Semantik. Numerische JSON-Projektionen verwenden bei möglichem Präzisionsverlust Strings beziehungsweise explizite exakte Zahlobjekte.

### 5.3 Typrelationen

`TypeRelations` ist ein zustandsfreier Dienst über einen unveränderlichen Graphen. Er führt keine Datenvalidierung aus. Er benötigt folgende Operationen:

```java
QueryResult<EffectiveType> resolveAliases(TypeRef type);
QueryResult<TypeRelation> canAssign(TypeRef source, TypeRef target,
                                  AssignmentContext context);
QueryResult<TypeRelation> canExtend(TypeRef derived, TypeRef base,
                                  ExtensionContext context, LanguageVersion language);
QueryResult<TypeRelation> areComparable(TypeRef left, TypeRef right,
                                      ComparisonOperator operator);
QueryResult<UnitRelation> compareUnits(SymbolRef left, SymbolRef right);
```

`TypeRelation` enthält `RelationDecision { ALLOWED, DISALLOWED }`, einen stabilen Grund und die relevanten Herkunfts-/Typverweise. Noch nicht bekannte oder fehlerhafte Typen ergeben `NotAnalyzed` beziehungsweise `Invalid`; sie sind nicht automatisch „zueinander passend“. Kontextparameter unterscheiden etwa normale Zuweisung, Attributüberschreibung, Domänenerweiterung und Funktionsargument. Eine universelle `isSubtype()`-Booleanmethode würde diese fachlichen Unterschiede unzulässig zusammenziehen.

Aliaszyklen, Enum-Erweiterungen, eingeschränkte Zielklassen, Einheiten und numerische Grenzen werden mit besuchten Knoten beziehungsweise komponentenweiser Auswertung geprüft. Diagnoseerzeugung und reine Relationsberechnung sind getrennt, damit dieselbe Relation nicht bei jeder Query neue Meldungen anhängt.

## 6. Ausdrücke, Constraints und weitere Fachsprache

### 6.1 Typisierte Ausdrücke

`TypedExpression` ist eine geschlossene Hierarchie mit ID, Quellursprung, Ergebnis-Typzustand und geordneten Kindern. Mindestens erforderlich sind Literal-, Namens-/Pfad-, Unary-, Binary-, Funktionsaufruf-, DEFINED-, Parameter-, Klassenkonstanten-, Attributpfadkonstanten-, ALL-/Objektmengen- und Inspection-Varianten. `UNDEFINED` ist eine sprachliche Konstante mit eigener Definiertheitssemantik. Sie ist kein `ErrorType` und kein Java-null.

Ein `ObjectPathExpression` besteht aus typisierten `PathStep`-Varianten: Attribut, Rolle, Assoziationspfad, THIS/PARENT, struktur-/listenbezogene Auswahl, Aggregates und die weiteren normativ zulässigen Kontextschritte. Jeder Schritt speichert Eingabe-/Ausgabetyp, Kardinalitätswirkung und aufgelöstes Ziel beziehungsweise Fehlerzustand. Ein vollständiger Pfad wird nicht nur als String gespeichert.

`THIS` übernimmt den Typ seines Kontextes: in einer Wertebereichsbedingung den
Wertebereichstyp, in einem Objektpfad den Objekt- beziehungsweise Strukturtyp.
Objektverweise sind keine Boolean-Werte. Nach einem skalaren Pfadwert ist kein
weiterer Attributzugriff zulässig. Assoziationszugänge (`\`), Indizes und
Inspektionsschritte dürfen nicht durch Weglassen des Operators als gewöhnliche
Namensschritte interpretiert werden.

`PathStep.AssociationAccessStep(name, association, sourceRole, targetRole,
associationObject, cardinality, resultType, range)` hält einen Zugang von einem
Klassenobjekt über eine Assoziation fest. Alle IDs und Typen gehören zum
umgebenden Snapshot. `sourceRole` verbindet das Ausgangsobjekt, `targetRole`
bezeichnet die angesprochene Gegenrolle. `associationObject=true` (`\rolle`)
selektiert die Assoziationsinstanzen, sonst deren Zielobjekte. Kardinalität und
Zwischentyp bleiben auch dann erhalten, wenn ein nachfolgendes Attribut den
Endtyp des Pfades bestimmt. Eine direkte `RoleStep` auf einer Assoziationsinstanz
selektiert dagegen je Assoziationsinstanz einen Beteiligten. Ausdrücke, Signaturen und
Zwischentypen müssen vollständig in der ausgewählten `CompiledSchema`-Closure
verfügbar bleiben. Graphkonstruktion prüft referenzierte Ziele und Typen;
fehlende IDs sind ein Vertragsfehler. Die Bindung muss Mehrdeutigkeit, Topic-
Sichtbarkeit, EXTERNAL und geerbte Zugänge beachten. Unvollständig unterstützte
Rollenkomposition darf kein gültiges Schema erzeugen.

`PathStep.AssociationSourceStep(name, association, sourceRole, cardinality,
resultType, range)` hält den Ausgangsrollenzugang auf Assoziationsinstanzen fest,
auch bei `rolle[Assoziation]`. Er besitzt keine künstliche Gegenrolle. Der
Qualifikator bindet die Assoziation, der Rollenname die Ausgangsrolle; beide
besitzen eigene `NameReference`-Bereiche. Alle Werte sind nullfrei und immutable,
IDs snapshotlokal, Bereiche an Quelle und Revision gebunden. Die Variante ist
Teil der geschlossenen `PathStep`-Familie (Vorab-API 0.2).

Die Zugangskardinalität gilt je Ausgangsobjekt. Bei einer n-stelligen Beziehung
(n > 2) bindet eine einzelne Teilnehmeridentität nicht die Kombination aller
anderen Rollen; deren Rollenkardinalität darf deshalb keinen Singletonzugang
vortäuschen. Der statische Zugang verwendet konservativ `0..*`. Der Ergebnistyp
bewahrt zusätzlich eine bereits im vorherigen Schritt entstandene Objektmenge.
Weitere Rollen-, Assoziations- und Referenzattributschritte führen diese Menge
fort. Eine Folgebeziehung muss für sämtliche Zielalternativen passen. Skalare
Attributwerte mehrerer Objekte dürfen nicht still zu einem einzelnen Wert
werden. Allgemeine Strukturmengen-/Inspektionspfade bleiben eine eigene
verpflichtende P2-Abnahme. Positive und negative Vertragstests prüfen n-stellige
und qualifizierte Zugänge in 2.3/2.4, Typen, genaue Navigations-/Renamebereiche,
Mehrdeutigkeit, EXTERNAL, HIDING, Zielmengen und Mengenfortsetzungen.

`PathIndex(kind, position)` (model) enthält FIRST/LAST ohne Position oder
POSITION mit exaktem `BigInteger`. Werte sind immutable und nullfrei; nullbasierte
Indizes sind sprachlich ungültig. `PathStep.IndexStep(index, resultType, range)`
folgt auf den `AttributeStep`, dessen Namensbereich und Referenzziel separat
bleiben. Sein Quell-/Revisionsbereich umfasst Attribut und Klammern;
`resultType` gehört zum selben Snapshot und bleibt in der Schema-Closure.
LIST-Auswahl liefert den Elementtyp; Koordinatenauswahl den exakten Achsentyp.
Indizes beginnen bei 1 und dürfen eine endliche LIST-Obergrenze beziehungsweise
die Achsenzahl nicht überschreiten. FIRST/LAST gelten nur für LIST. Eine
Position oberhalb der Mindestkardinalität bleibt erlaubt und kann zur Laufzeit
undefiniert sein. BAG, skalare Werte und unselektierte Mengen eröffnen keinen
Indexzugang. Ungültige Auswahl erzeugt einen typisierten Fehler samt Diagnose.
Positive/negative Verträge prüfen Grenzwerte, sehr grosse Positionen, verschachtelte
Fortsetzungen, Zwischen-/Endtypen, Navigation und Rename in beiden Versionen.

UNIQUE- und EXISTENCE-Pfade verwenden dieselbe Bindung wie andere
`PathExpression`-Werte. `UniquenessConstraint.keys()` ist eine geordnete Liste
von `ExpressionId`; `where()` hält den optionalen booleschen Filter.
`LocalUniqueness(prefix, fields)` enthält eine Präfix-`ExpressionId` und geordnete
Feld-`ExpressionId`s. Der Präfix führt ausschliesslich über Strukturattribute;
die Felder binden im ausgewählten Strukturelement. Globale Schlüssel dürfen
keine mehrwertigen Zugänge oder Strukturwerte als Endwert besitzen. LOCAL
liefert keine leeren Ersatzschlüssel. `basket()` und `name()` bleiben erhalten.

`ExistenceConstraint.source()` und `ExistenceTarget.path()` sind ebenfalls
`ExpressionId`s; die Alternative erhält ihre gebundene `viewable()`-Identität.
Jeder Zielpfad bindet in seinem eigenen Objektkontext, bei unverändertem
lexikalischem Topic des Constraints. Abhängigkeiten werden über Topic-Identitäten
geprüft, nicht anhand der Schreibweise eines Namens. Keine ungültige Alternative
darf still weggelassen werden und dennoch ein gültiges Schema ergeben.

EXISTENCE prüft Vergleichbarkeit von Attributwerten, nicht die Zuweisbarkeit
des vollständigen Quellbereichs. Zahlenintervalle und Textlängen allein sind
keine Modellablehnungsgründe. Einheiten-/Richtungs-/Referenzsystemmetadaten,
Koordinatenform und Strukturidentitäten bleiben relevant. Die allgemeinen
Zuweisungsregeln behalten ihre Bereichsprüfung. Die begründete Auslegung und
Referenzunterschiede stehen im [Normnachweis](../evidence/stabilization/existence-types-and-indices.md).
Höchstens einwertige Struktur-Collections sind als Einzelstruktur vergleichbar.
Ganze mehrwertige Collection-Endwerte behalten Collectionart, Kardinalität und
Elementtyp. Der Vergleich verwendet dieselbe Collectionart und kompatible
Elementwerte, nicht die vollständige Zuweisbarkeit der Kardinalitätsintervalle.
Er ist keine elementweise Pfadfortsetzung und flacht keine Mengen ab.
LIST-Auswahl liefert einen einzelnen Wert. Die begründete Auslegung und
Referenzabweichungen stehen im [Folgenachweis](../evidence/stabilization/enum-format-oid-collections.md).

Alle IDs gehören zum umgebenden Snapshot; Quellbereiche, Endtypen, Fehlerzustände
und Schritte sind über `SemanticGraph.expression(id)` zugänglich. Das Schema
bewahrt alle referenzierten Ausdrücke und Typen; Graphkonstruktion prüft fehlende
Ausdrucks-/Zielreferenzen. Listen sind nullfrei, immutable und in Quellreihenfolge.
Abbruch verwendet den bestehenden Laufkontext. Unbekannte Pfadformen verhindern
ein gültiges Schema mit einer expliziten Implementierungslücke. Positive und
negative Vertragstests prüfen innere/externe Constraints, WHERE, BASKET, LOCAL,
verschachtelte Pfade, Zielalternativen, Scope, Typen, Kardinalitäten und Rename.

`EnumerationType.values()` ist der effektive Baum in Präordnung mit stabiler
Geschwisterreihenfolge und ursprünglichen Bereichen geerbter Werte.
`baseType(): Optional<TypeId>` bezeichnet den unmittelbaren effektiven
Basistyp; `finalSubtrees(): Set<String>` enthält geschlossene Geschwisterlisten
einschliesslich geerbter Abschlüsse. `finalPart()` fragt nur die Wurzel ab.
Vererbung von Domänen und EXTENDED-Attributen verwendet dieselbe Baumregel;
Zuweisung und öffentliche Typqueries verwenden dieselbe Identitätskette.

Die einfachen vordefinierten Domänen `INTERLIS.INTERLIS_1_DATE`,
`INTERLIS.HALIGNMENT` und `INTERLIS.VALIGNMENT` sind `DomainSymbol`-Werte
mit `FINAL` und `PredefinedOrigin` je Sprachversion. Ihre Verwendung erhält
einen `DomainReferenceType`; das Ziel enthält `TEXT*8` beziehungsweise einen
horizontalen/vertikalen `AlignmentType`. Wiederholte Verwendungen teilen die
Domänenidentität im Graph; Quelldeklarationsbereiche werden nicht erfunden.
Typqueries lösen diese Domänen wie gewöhnliche Aliasse auf. Ein unbekannter
vordefinierter Name besitzt einen `ErrorType` mit Diagnoseursache und erhält
keinen produktiven Generic-Ersatz. FINAL verbietet die Erweiterung der Domäne,
nicht ihre Verwendung als Attributtyp. Dies vervollständigt noch nicht den
gesamten vordefinierten Modellinhalt aus Anhang A.

`FormattedType` enthält eine optionale Basisdomäne, Basisstruktur, vordefinierte
Zeitart, effektive geordnete `Part`-Werte und deklarierte lexikalische Grenzen.
Teile erhalten Attribut-/Formatdomänen-IDs, optionale exakte Stellenzahl,
Literal und Herkunft. `formatName` ist ausschliesslich Anzeigetext.
XMLDate, XMLTime und XMLDateTime besitzen dieselben gebundenen Teile wie
benutzerdefinierte Formate. Ihre GregorianDate-/UTC-Strukturen, GregorianYear,
numerischen Komponenten, Einheiten und Referenzsysteme bleiben im Graph
öffentlich erreichbar. `Origin.PredefinedOrigin` nennt die Sprachversion;
es wird keine Quelldeklaration vorgetäuscht. `predefined()` klassifiziert die
Zeitart und bestimmt keine zweite Wertprüfung. Breiten und Genauigkeit folgen
dem jeweiligen Anhang A: 2.3 hat bei XMLDate keine expliziten Breiten; 2.4
verwendet Year/4, Month/2 und Day/2. XMLTime/DateTime verwenden in 2.3 Hours/2,
in 2.4 zusätzlich Minutes/2 und Seconds/2. Beide verwenden die deklarierte
Sekundenpräzision 0.000..59.999. Zusätzliche Kalenderprüfungen gehören nicht
zum Compilervertrag. DATE/TIMEOFDAY/DATETIME werden in 2.4 als
`DomainReferenceType` auf diese Domänen gebunden. Der parallele `TemporalType`
entfällt in Vorab-API 0.2.
Geerbte Teile stehen vor Ergänzungen; Grenzen der gesamten Basiskette bleiben
wirksam. `OidType.valueType(): Optional<TypeId>` erhält den numerischen oder
textlichen Träger; ANY hat keinen konkreten Träger. Vordefinierte OID-Domänen
besitzen explizite synthetische Herkunft, keine vorgetäuschte Quelldeklaration.
Alle IDs gehören zum umgebenden Snapshot; Listen und Mengen sind immutable
und nullfrei. Der Graph prüft Referenzen und bewahrt benötigte Typen in der
ausgewählten Closure. Konstruktion und Validierung benutzen den bestehenden
Laufkontext; ungelöste Referenzen ergeben Diagnosen, fehlende Implementierung
verhindert ein gültiges Schema. Vertragstests prüfen effektive Inhalte,
Herkunft, Queries, Navigation/Rename, Vererbungsfehler und portable Parität.
Diese Verträge ersetzen keine vollständige Typfamilienabnahme; deren aktuelle
Grenzen stehen im Folgenachweis.

FORMAT-Konstanten und Grenzen werden aus den gebundenen Bestandteilen geprüft:
Vorkommastellen berücksichtigen die explizite Auffüllbreite, Nachkommastellen
die lexikalische Mantissenpräzision des numerischen Bereichs. Exponent und
Mantissenpräzision bleiben getrennt; es gibt keine Rundung über `double`.
Verschachtelte Formate prüfen ihre eigenen und geerbten Grenzen, bevor der
äussere Wert verglichen wird. Vergleiche bleiben lexikografisch über die
numerischen Komponenten; ein geerbter Präfix behält seine Bereichsgrenzen.
Abstrakte numerische Formatkomponenten bleiben als unbeschränkte numerische
Attributtypen im Graph erhalten. Die fehlende Präzision ist legale Abstraktion,
keine Implementierungslücke. Eine darauf beruhende Domain oder ein verwendendes
Attribut muss ABSTRACT sein, auch bei verschachtelten Formaten. Explizite
FORMAT-Grenzen schränken die exakten Komponentenwerte ein; sie erfinden keine
numerische Präzision. Bei INHERITANCE über eine spezialisierte Struktur
verweisen die effektiven Teile auf deren wirksame Attribute. Reihenfolge,
Auffüllbreite und deklarierte Herkunft bleiben erhalten. Die konkretisierte
Präzision und alle geerbten Grenzen gelten gemeinsam für Grenzen und Konstanten.

`SymbolHeader.oidRules(): List<OidRule>` enthält gebundene effektive Regeln,
Basket vor Objekt. `OidRule(basket, domain, origin)` gehört zu
`guru.interlis.model`; alle Ziel-IDs gehören zum Snapshot. Eine leere Liste
enthält keine explizite oder übernommene Regel. Eine einzelne Objektregel mit
leerem `domain` bezeichnet explizites NO OID. Fehlgeschlagene Bindung darf
nicht als NO OID erscheinen. Vererbte Regeln und Topic-Defaults behalten ihre
ursprüngliche Herkunft; Klassen übernehmen ausschliesslich Objektregeln.
Strukturen erhalten keine OID-Regeln. Nur in 2.4 bedeutet eine fehlende
Klassen-/Topic-Festlegung implizites NO OID; dessen Vererbung folgt dann
denselben Konkretisierungsregeln wie explizites NO OID. INTERLIS.NOOID steht
nur in 2.4 zur Verfügung. In 2.3 darf eine noch fehlende Festlegung ergänzt,
ein ausdrücklich geerbtes NO OID jedoch nicht in ANYOID geändert werden.
In 2.4 ist die Klassen-Konkretisierung NO OID → ANYOID → konkrete OID möglich;
ein direkter Sprung von NO OID zur konkreten OID ist unzulässig. Eine einmal
zugewiesene Topic-OID bleibt in beiden Versionen unverändert. Die eingebaute
NOOID-Domäne kann nicht durch beliebige eigene OID-Domänen erweitert werden.
Domänenziele müssen OID-Typen sein. In 2.3 verlangen explizite OID-AS-Angaben
CONTRACTED; 2.4 hat diese Pflicht aufgehoben. Die Regeln für unveränderliche
konkrete OID-Zuordnung und legale Konkretisierung gelten auch bei Vererbung.
Syntax, Diagnose, Navigation/Rename und Schema-Closure benutzen dieselben
gebundenen Ziele. Fehler sperren das Schema, der Teilgraph bleibt inspizierbar.
`FormatPrecisionOidBindingContractTest` prüft positive/negative Regeln,
Getrenntheit von Basket/Objekt, Herkunft, Scope, Typen und öffentliche Abfragen.
`OidFollowupAbstractFormatContractTest` ergänzt Versionsunterschiede,
Topic-Zuordnungen, abstrakte Komponenten und effektive FORMAT-Attributziele.
`NoOidReferenceContractTest` prüft zusätzlich die NO-OID-Regel für
Referenzattribute und Rollenzielmengen. Die vollständige P2-Abnahme und alle
FORMAT-Relationen bleiben weiterhin offen.

`ReferenceType(id, origin, target, restrictions, external)` gehört zu
`interlis-model` / `guru.interlis.model`. `target: Optional<SymbolId>` ist bei
ANYCLASS leer; `restrictions: List<SymbolId>` enthält die disjunktiven
zugelassenen Unterhierarchien in Quellreihenfolge. `targetRoots()` liefert die
Restriktionen, sonst das Primärziel; leer bezeichnet uneingeschränktes ANYCLASS.
Alle Werte sind nullfrei, unveränderlich und snapshotgebunden. Klasse und
Assoziation sind zulässige Zielarten, Struktur nicht. Jede Restriktion muss
das Primärziel spezialisieren. Die gemeinsam genutzte Typverträglichkeit
prüft sämtliche möglichen Ziele; Erweiterungen dürfen nur einschränken und
EXTERNAL entfernen, nicht hinzufügen. Topicübergreifende Referenzziele verlangen
EXTERNAL, auch in Restriktionen und Collection-Elementen; geerbte Ziele im
Topic-Vererbungsweg bleiben davon ausgenommen. Namensreferenzen erhalten die gebundenen
Primär- und Einschränkungsziele für Navigation/Rename. Pfade verwenden diese
Menge und ihren gemeinsamen Kontext statt des ersten Listenelements.

In 2.4 dürfen Referenzattribute und Rollen keine Klassen mit effektivem
explizitem oder implizitem NO OID zum Ziel haben (Referenz 3.5.3); die Regel
gilt unabhängig von EXTERNAL. In 2.3 wird dieses zusätzliche Verbot nicht
angewandt (2.5.3). Massgeblich sind die Sprache der referenzierenden Verwendung
und die gebundenen effektiven Objekt-OID-Regeln des Ziels. Eine Basket-OID
oder die OID der referenzierenden Klasse/Assoziation ersetzt keine Ziel-OID.
Restriktionen ersetzen die zugelassene Basismenge; alle OR-Alternativen und
Restriktionswurzeln werden geprüft, einschliesslich Vererbung und lokalem
EXTENDED-Kontext. Eine Assoziation als Referenzziel benötigt in beiden
Versionen eine eigene Identität (Referenz 2.7.1/3.7.1), aus OID AS oder der
OID-Eigenschaft; ihre Rollenidentifikation allein genügt nicht.
Uneingeschränktes ANYCLASS bezeichnet eine offene polymorphe Zielmenge und
ist keine konkrete Klasse mit implizitem NO OID. Der Compiler erfindet dafür
weder eine OID noch zählt er zufällig vorhandene Klassen als geschlossene
Menge auf. Die Zulässigkeit konkreter Transferreferenzen ist Aufgabe der
späteren Datenvalidierung; explizite Restriktionen werden bereits statisch
geprüft. Fehler verhindern ein gültiges Schema der betroffenen Closure.

`TopicSymbol.dependencies()` bewahrt direkt deklarierte, gebundene Topic-IDs
in Quellreihenfolge. Die Liste ist nullfrei, immutable und snapshotgebunden;
geerbte Abhängigkeiten werden über `baseTopic()` ermittelt, nicht in diese Liste
kopiert. DEPENDS-ON-Namen haben eigene gebundene Referenzsegmente, einschliesslich
Modellqualifikation, für Navigation und Rename. Unauflösbare oder artfalsche
Ziele bleiben Diagnosen; Namenssuffixe sind kein Ersatz für Identitätsbindung.

Für Referenzattribute und Rollen gelten in 2.3 und 2.4 dieselben
Abhängigkeitsregeln: Ziele im eigenen Topic oder dessen Basis-Topic sind lokal.
Andere zugelassene Zielwurzeln benötigen EXTERNAL und eine explizite, direkt
oder aus dem Basis-Topic geerbte Abhängigkeit auf ihren Topic. Eine Kette von
DEPENDS-ON-Kanten ersetzt keine direkte Deklaration. Eine reine MANDATORY-Überschreibung übernimmt den gebundenen Typ der
Basis bereits vor der Ausdrucksbindung; ein generischer Platzhalter darf weder
Typ- noch Zielprüfung umgehen. Die gebundene `TypeId` ist dieselbe wie beim
geerbten Attribut, auch über mehrere Überschreibungen. Pfade, Funktionsargumente,
UNIQUE/EXISTENCE und öffentliche Typabfragen benutzen diese eine Bindung.
Fehlende geerbte Attribute und Zyklen sind fachliche Fehler. Ein expliziter Typ
wie `NUMERIC` darf nicht als ausgelassener Typ interpretiert werden.
`ClassSymbol.baseType()` erhält auch eine Basisstruktur gemäss den verwendeten
PDF-Referenzen 2.3 §2.5.3 und 2.4 §3.5.3; ein zuvor akzeptierter Basistyp darf
nicht aus dem Schema verschwinden. Der Widerspruch zur korrigierten Onlinefassung
2.4 ist im [Fachnachweis](../evidence/stabilization/mandatory-override-types.md)
als offene Normprofilklärung festgehalten; keine pauschale Vollständigkeitszusage.
Die Prüfung umfasst alle
Restriktionen/OR-Alternativen, effektive geerbte Mitglieder und Referenzen in
verschachtelten Strukturwerten am verwendenden Topic. Identitäten entscheiden;
gleiche Klassen- oder Topic-Namen sind kein Sichtbarkeitsbeleg. Abhängigkeitszyklen
einschliesslich geerbter Selbstabhängigkeiten sind Fehler und sperren das Schema.
Die Traversierung prüft Arbeitsbudget/Abbruch und benötigt keinen rekursiven
Java-Aufrufstapel. `TopicDependencyContractTest` prüft positive und negative
Fälle beider Versionen sowie die gebundenen Ziele im öffentlichen Schema.

`RoleSymbol.targets()` enthält nichtleere, geordnete `RoleTarget(base,
restrictions)`-Alternativen. Alle IDs gehören zum selben Snapshot; Basis und
Restriktionen sind Klassen oder Assoziationen. `ANYCLASS` hat eine explizite
vordefinierte Identität. Eine leere Restriktionsliste erlaubt die ganze
Basishierarchie; sonst ist die Liste eine Disjunktion spezialisierter Hierarchien.
`allowedRoots()` beziehungsweise `RoleSymbol.targetRoots()` liefern diese
Wurzeln in Deklarationsreihenfolge, ohne Unterklassen aufzuzählen. Alle Werte
sind nullfrei und unveränderlich. OR-Basen dürfen nicht voneinander abgeleitet
sein. Ein Pfad oder Funktionsargument muss für sämtliche möglichen Ziele gelten;
ein Verbraucher darf nicht stellvertretend das erste Ziel wählen.

`AssociationSymbol.baseAssociation()` bewahrt die optionale gebundene Basis.
`cardinality()` enthält die effektive Linkmultiplizität; fehlende Angaben werden
aus der Basis übernommen, bei einer Wurzel gilt genau eins.
`roles()` und `attributes()` bleiben eigene Deklarationen. Die vorhandene
`effectiveMembers`-Abfrage liefert geerbte und überschriebene Mitglieder samt
Herkunft und stabiler Position. Eine Erweiterung darf keine Rollen hinzufügen,
Zielmengen oder Kardinalitäten erweitern, FINAL überschreiben oder EXTERNAL
ändern. Fehlende Kardinalität erbt die effektive Grenze. Bei Klassenzugängen
bleibt ohne HIDING der allgemeine Zugang erhalten; HIDING an der Ausgangsrolle
unterdrückt ihn für die spezialisierte Teilnehmerhierarchie. Widersprechende
Geschwistererweiterungen sind Fehler. Eine Qualifikation darf weder HIDING noch
Topic-Sichtbarkeit umgehen; fehlende Unterstützung muss das Schema sperren.

Der Objektkontext eines externen Constraints und dessen lexikalischer Topic
sind getrennt zu erhalten. Lokale `EXTENDED`-Klassen und -Assoziationen werden
über gebundene Vererbungsidentitäten im Topic-Vererbungsweg angewandt. Eine
unabhängige gleichnamige Klasse darf den Kontext nicht ersetzen. Positive und
negative Vertragstests prüfen 2.3/2.4, Zielreferenzen, effektive Reihenfolge,
Typabfragen, HIDING und Diagrammkanten für sämtliche Zielalternativen.

Wertebereichsbedingungen sind in 2.4 benannte, geordnete Bedingungen ihres
Wertebereichs; 2.3 lässt diese Syntax nicht zu. Ihre Ausdrücke bleiben als
`DomainConstraint` mit gebundenen Typen im Graph erhalten. Jeder Ausdruck muss
Boolean ergeben; Namen sind innerhalb der Wertebereichsdefinition eindeutig.
Ein Verbraucher findet die zugehörigen `ConstraintSymbol`-Werte über ihre
Owner-ID. Die Weitergabe und Prüfung geerbter Bedingungen bleibt Teil des
vollständigen P2-Vertrags.

Der Compiler prüft Operatorzulässigkeit, Operandentypen und Funktionssignaturen. Er wertet keine benutzerdefinierte Java-Funktion aus und liest keine Transferobjekte. Konstante Auswertung ist optional und nur zulässig, wenn sie exakt, deterministisch und fachlich gleichwertig ist; sie ersetzt nicht den ursprünglichen Ausdruck für Herkunft und Diagnostik.

### 6.2 Vollständiger fachlicher Featurekatalog

Jede Zeile erhält in der Implementierung mindestens einen positiven, einen relevanten negativen und einen Metamodell-/Querytest. Die Zulässigkeit wird je Sprachversion mit der normativen Versionstabelle aus04 verbunden. Eine aus der Ausgangsgrammatik erkennbare Form ist noch kein Nachweis, dass sie in beiden Versionen zulässig ist.

| Featuregruppe | Verbindlicher fachlicher Umfang bis P2 |
|---|---|
| Modellkopf | Modellart, Sprachkennung, Issuer, Versionsliteral, CONTRACTED, Translation, erlaubte Headeroptionen und Endnamen. |
| Import/Scope | QUALIFIED/UNQUALIFIED-Sichtbarkeit, Mehrdeutigkeit, gesetzliche Vorwärtsreferenzregeln und Modell-/Topicgrenzen. |
| Topics | Vererbung, abstract/final, VIEW TOPIC, Dependencies, Objekt-/Basket-OIDs und deferred generics. |
| Klassen/Strukturen | Vererbung, abstract/extended/final, OID/NO OID, geerbte und lokale Eigenschaften. |
| Attribute | Mandatory, Ableitung, transient, Erweiterung, subdivision/continuous und restriktive Typen. |
| Assoziationen | Binär/n-stellig, Rollen, Aggregation/Komposition, External/Ordered/Hiding, Rollenrestriktionen, Kardinalitäten, eigene Attribute und abgeleitete Beziehungen. |
| Domänen | Alias, Erweiterung, abstract/final/generic, obligatorische Werte und Domänenconstraints. |
| Basistypen | Sämtliche Familien aus Abschnitt5 samt ihren Versionseinschränkungen und Erweiterungsregeln. |
| Aufzählung | Verschachtelung, Dotted-Names, Reihenfolge, Zirkularität, FINAL-Teilbäume, Erweiterungen, ALL OF und OTHERS. |
| Numerik/Einheiten | Genauigkeit, Grenzen, Kreissinn, Zirkularität, Basis-/abgeleitete-/zusammengesetzte Einheiten und symbolische Definitionen. |
| Referenzsysteme | Metaobjekt- und Domänenbezüge, Achsenbezüge, Koordinatenrotation und REFSYS-Angaben. |
| Linien/Flächen | VERTEX, STRAIGHTS/ARCS/eigene Formen, Überlappungsparameter, Richtung und Linienattribute; Multi-Varianten nach Version. |
| Formate/Zeit | FORMAT mit Basisstruktur/-domäne, Formatelemente und Grenzen; zeitbezogene Formen nach Version. |
| Generics/Context | Zulässige Generic-Domänen, konkrete Alternativen, Context-Zuordnung, aufgeschobene Konkretisierung und dessen Gültigkeitsbedingungen. |
| Metaobjekte | SIGN-/REFSYSTEM-Baskets, Vererbung, deklarierte Metaobjekte und referenzierte Klassen. |
| Parameter | Laufzeitparameter, Signaturparameter, METAOBJECT-Parameter und Funktionsargumente. |
| Mandatory | Boolesche Konsistenzbedingung, benannt oder unbenannt gemäss Version. |
| Plausibility | Prozentgrenze, Richtung und boolescher Ausdruck, mit getrenntem späterem Datenumfang. |
| Existence | Quellpfad, erlaubte Zielklassen/-views, alternative Zielpfade und Typverträglichkeit. |
| Uniqueness | Globale, lokale und Basket-bezogene Form, Vorbedingung/WHERE, zusammengesetzte Pfade. |
| Set Constraints | Menge und Kontext, LOCAL/BASKET soweit erlaubt, WHERE/Vorbedingung und eigentlicher Ausdruck. |
| Externe Constraints | CONSTRAINTS OF und Zuordnung zur erlaubten Zieldeklaration. |
| Ausdrücke | Normative Präzedenz, Relationen, Logik, Arithmetik, DEFINED, UNDEFINED, Konstanten und Mengenargumente. |
| Objektpfade | Direkte und inverse Pfade, Spezialschritte, Collectionselektion, Rollen-/Attributwechsel und Kontextregeln. |
| Funktionen | Deklaration, Argumentarten, Aufrufauflösung, Anzahl/Typen, Rückgabewert und externe Signaturidentität. |
| Views | Projection, Join einschliesslich optionaler Basis, Union, Aggregation, Inspection/Area Inspection, Aliasbasen, Base Extension, Selection und Viewattribute. |
| Graphics | GRAPHIC-Vererbung/Basis, Drawing Rules, Signaturparameterzuordnungen, bedingte Zuweisungen und Enumranges. |
| Übersetzungen | Referenzmodell/-version, strukturelle Zuordnung, Typ-/Constraint-/Rollenentsprechung und voneinander getrennte Namen. |
| Dokumentation/Metadaten | Quellkommentare, Metaattribute, Erläuterungen und nachvollziehbare Herkunft. |
| Vordefiniertes Modell | Normativ zugehörige Builtins je Sprachprofil; keine zufällige Abhängigkeit vom zuletzt verwendeten Modell. |

`ConstraintDefinition` verwendet hierfür konkrete Records `MandatoryConstraint`, `PlausibilityConstraint`, `ExistenceConstraint`, `UniquenessConstraint`, `SetConstraint` und `DomainConstraint`. Gemeinsame Merkmale dürfen in Hilfstypen liegen. Die spätere Validierungsreichweite wird ausdrücklich gespeichert, beispielsweise objektlokal, Basket oder Transfer; die endgültige Ausführungsplanung legt [12](12-transfer-validierung-und-datenbanken.md) fest.

## 7. Effektive Mitglieder und Übersetzungen

`EffectiveMember` enthält das deklarierende Symbol, den effektiven Besitzer, den Überschreibungs-/Vererbungsweg, die fachliche Position und die Herkunftsart. Die Reihenfolge wird durch `EffectiveMemberResolver.resolve(SymbolRef)` aus den Sprach- und Transferregeln ermittelt. Eine alphabetische Sortierung oder eine zufällige Mapiteration ist unzulässig.

Der `inheritancePath` führt vom Besitzer der tatsächlich ausgewählten
Memberdeklaration über jede Zwischenstufe zum effektiven Besitzer, jeweils
einmal. Ein lokales Mitglied hat nur seinen eigenen Besitzer im Pfad; ein
überschriebenes Mitglied beginnt beim Besitzer der überschreibenden
Deklaration. Gegenüber dem abgefragten Besitzer ist ein geerbtes Override
`INHERITED`, während `overridden` dessen Überschreibungsbezug erhält. Ein dort
lokal deklariertes Override ist `OVERRIDDEN`. `AttributeSymbol.overrides`
bewahrt daneben die tatsächliche Basiseigenschaft. Diese Verträge gelten
gleich für Klassen, Strukturen und Assoziationen sowie für beide Sprachen.

Eine Überschreibung darf die durch die fachlichen Regeln definierte Position nicht unbemerkt verändern. Eingebettete Assoziationsrollen werden als solche markiert; sie werden nicht zu gewöhnlichen Attributen umetikettiert. Für eine Diagrammoption „nur eigene Attribute“ wird nach Herkunft gefiltert. Für Transfer-/DB-Pläne wird die vollständige effektive Information verwendet.

`TranslationLink` verbindet Original- und Übersetzungssymbole mit überprüfter Korrespondenz. Ein Transfername wird erst unter einem ausgewählten Transfer-/Sprachprofil bestimmt. Die Entscheidung ist nicht in `SymbolId` kodiert. Konflikte und fehlende Entsprechungen bleiben fachliche Diagnostik; eine Übersetzung ist keine globale Umbenennung aller Symbole im Graphen.

`ModelTranslation.sourceTarget(): Optional<SymbolId>` bindet das unmittelbar
angegebene Originalmodell in der gewählten Version. Eine leere Bindung bleibt
eine diagnostizierte fehlerhafte Analyse. `correspondences(): List<TranslationLink>`
enthält zunächst die eigenen Deklarationen und anschliessend die tatsächlich
benutzten Importkorrespondenzen in deterministischer Deklarationsreihenfolge.
`TranslationLink(original, translated)` erhält beide Identitäten; ein unverändert
importiertes Symbol kann auf sich selbst verweisen. Alle IDs gehören zum selben
Graph, die Liste ist immutable und nullfrei. Im gültigen Schema sind Struktur
und fachliche Inhalte geprüft; bei strukturell gescheiterter Bindung bleibt die
Liste leer. Vorhandene unbenutzte Sprachvarianten bestimmen keine Importwahl.
Transitive Zuordnungen werden konfliktgeprüft zusammengeführt; Modellpaare
begrenzen die Rekursion. Zyklen ohne Originalmodell sind Fehler.
Die Korrespondenzen enthalten auch deklarierte Enum-Knoten.
`EnumElementSymbol.ordinal()` bezeichnet deren Position im effektiven Baum
(`EnumerationValue.order()`), nicht die globale Deklarationsreihenfolge.
Gebundene Quellreferenzen werden pro Scope, Bereich, Verwendung und Ziel
einmal erfasst; ein früher und ein später Bindungszugriff erzeugen keine
doppelten Queryvorkommen.

## 8. `CompiledSchema` als geprüfte Sicht

```java
public interface CompiledSchema {
    SnapshotId snapshotId();
    SchemaFingerprint fingerprint();
    SemanticGraph semanticGraph();
    SemanticQueries queries();
    List<SymbolRef> rootModels();
    List<SymbolRef> models();
    Set<LanguageVersion> languageVersions();
    Map<SourceId, SourceRevision> sourceRevisions();
    SchemaProvenance provenance();
}
```

Die Produktionsimplementierung ist `guru.interlis.compiler.internal.DefaultCompiledSchema`, final und unveränderlich. Sie wird ausschliesslich durch `CompilationGate` aus06 erzeugt. Das Interface im Modellmodul vermeidet eine Abhängigkeit von `interlis-model` zurück zum Compiler. Ein Architekturtest verbietet weitere Produktionsimplementierungen innerhalb dieses Repositories.

Dies ist ein API-Vertrag, keine Java-Sicherheitsgrenze gegen Drittcode: Ein externer Aufrufer kann technisch ein Interface selbst implementieren. Die Gültigkeitsgarantie gilt für von `CompilerEngine` ausgegebene Instanzen. Ein Consumer, der fremde Implementierungen akzeptiert, muss seine Eingabegrenze ausdrücklich definieren; er darf deren Herkunft nicht allein aus dem Interfacenamen ableiten.

Die geprüfte Closure enthält keine fachlichen Error-/Pending-Typen und keine unaufgelösten verpflichtenden Bindungen. Rechtmässige Generic-/Deferred-Zustände und externe Funktionssignaturen bleiben zulässig. `SchemaProvenance` enthält Compiler-/Sprachprofil-/Grammatikstand, angeforderte Wurzeln, relevante Quellfingerprints, Resolverbindungen und semantisch wirksame Optionen/Overlays. Uhrzeit und Run-ID sind keine fachlichen Fingerprintbestandteile.

`semanticGraph()` und `queries()` des Schemas sind auf diese geprüfte Closure beschränkt. Sie dürfen unabhängige fehlerhafte Modelle aus dem umfassenderen `AnalysisSnapshot` nicht als eigene geprüfte Inhalte ausgeben. Die Implementierung teilt unveränderliche Symbole, Typen und Ausdrücke und verwendet eine unveränderliche Auswahl-/Indexsicht; ein zweiter tief kopierter Vollgraph ist nicht erforderlich. Die Auswahl enthält alle fachlich benötigten Referenzziele und Owner. Ein Handle desselben Snapshots, der ausserhalb dieser Auswahl liegt, ergibt `NotAnalyzed(OUTSIDE_SELECTION)`; ein Handle eines anderen Snapshots bleibt ein `SnapshotMismatchException`-Fall. Dadurch bleiben die Garantien der vollständigen Analyse und der geprüften Schemaauswahl eindeutig.

`models()` enthält die Rootmodelle und deren benötigte Modellabhängigkeiten in deterministischer Reihenfolge. `sourceRevisions()` nennt die dafür verwendeten Quellen; die Texte können weitere unselektierte Nachbarmodelle enthalten, ohne dass diese Bestandteil der Schemasicht werden. Ein Syntaxfehler, der innerhalb einer gemeinsamen Quelldatei nicht zuverlässig von der Root abgegrenzt werden kann, bleibt nach dem konservativen Gatevertrag06 blockierend.

Das Schema besitzt keine JDBC-Verbindungen, Transferobjekte, veränderlichen CRS-Caches, Runtimeparameterwerte oder Rendererinstanzen. Verbraucher erzeugen eigene Kontexte und Pläne. Mehrere Validatoren oder Datenbankplaner dürfen dasselbe Schema gleichzeitig lesen.

## 9. Konkrete Abnahmetests

1. **Alias und Genauigkeit:** Ein numerischer Domänentyp mit `9007199254740993.0010` wird über mindestens zwei Aliase verwendet. Deklarierter Typ, effektiver Typ, exakter Wert, Lexem und Präzision bleiben abfragbar. JSON-Projektion und Wasm liefern keinen gerundeten Wert.
2. **Vererbung und Rollen:** Eine Basisklasse, eine Erweiterung, eine überschriebene Eigenschaft und eine Assoziation ergeben eine fest erwartete effektive Reihenfolge. Eigene/geerbte Mitglieder sind unterscheidbar. Eine unzulässige Erweiterung liefert eine konkrete Typrelation und Diagnose.
3. **Teilmodell:** Ein fehlender Domänenname ergibt eine unaufgelöste Bindung mit Ursache; eine unabhängige bekannte Domäne bleibt abfragbar. Ein legal aufgeschobener Generic-Typ ist bekannt und fachlich verschieden vom Fehlerzustand.
4. **Namensidentität:** Zwei gleichnamige Deklarationen bleiben zwei Symbole mit unterschiedlichen IDs. `findQualifiedName` liefert beide, während die eindeutige Bindung eine Mehrdeutigkeit meldet.
5. **Snapshotgrenze:** Ein Handle aus Revision N wird auf N+1 zurückgewiesen. Explizites `rebindSymbol` kann ein erhaltenes Symbol neu adressieren, dessen Eigenschaften anschliessend aus N+1 gelesen werden.
6. **Übersetzung:** Original und Übersetzung besitzen überprüfte Korrespondenz; ein ausgewählter Transfername ändert nicht die kanonische Identität. Eine strukturell nicht passende Übersetzung blockiert die betroffene Closure.
7. **Unveränderlichkeit:** Collections und intern verwendete Builder werden nach Publikation verändert beziehungsweise Zugriffe versucht. Kein veröffentlichter Graph darf sich ändern; verbotene Operationen scheitern sichtbar.
8. **Grafische Sprache:** Ein INTERLIS-GRAPHIC-Element und ein UML-Diagrammexport sind unabhängig repräsentiert. Das Modellfeature verschwindet nicht, nur weil der UML-Renderer keine Signaturregeln zeichnet.
9. **Closure:** Eine gültige Root mit gültigen Imports kann ein Schema ergeben, während ein unabhängiges offenes Modell Fehler hat. Derselbe Fehler in einer importierten Quelle blockiert den Erfolg.
10. **Zyklen:** Alias-/Vererbungszyklen liefern endliche Diagnostik mit Referenzpfad und keinen Stackoverflow. Fachlich erlaubte Referenzzyklen bleiben abbildbar.

Erst die Kombination aus sprachlicher Konformität, Metamodell-Goldens und realen Consumerprojektionen beweist die Qualität dieser Modellschicht. Ein erfolgreicher JSON-Export mit Klassen- und Attributnamen genügt nicht.

### 9.1 Gebundene Views in Vorab-API 0.2

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
keine zweite Attributvariante. `AGGREGATES` ist zugänglich, gehört aber nicht
zur Liste der transferierten View-Mitglieder. `PathStep.ViewBaseStep` erhält
lokale Basisidentität und fachliches Ziel.

Listen sind unveränderlich und nullfrei. IDs beziehen sich auf denselben
Snapshot. Quellbereiche bleiben revisionsgebundene UTF-16-Halbintervalle.
Ungültige und noch unvollständig gebundene Views bleiben in der Analyse
inspizierbar; nur das gemeinsame Gate entscheidet über `CompiledSchema`.
Die Einführungsnachweise stehen im [Lieferstatus](../evidence/stabilization/conformance-deliveries.md);
diese Vertragsbeschreibung behauptet keine vollständige P2-Abnahme.

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

### 9.2 Gebundene Inspektionsfaktoren

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


### Gebundene generische Koordinatenkontexte, Vorab-API 0.2

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

### Gebundene Linienformen, Vorab-API 0.2

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


### K4: Metadaten, Achsen und Geometrieinspektion, Vorab-API 0.2

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
`FunctionParameter`. SIGN/METAOBJECT-Signaturparameter bleiben bis K5 ausdrücklich
gesperrt; weder Grafikdaten noch externe Metadatenwerte werden ausgeführt/eingelesen.

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
L1-1 bindet `INTERLIS.LineGeometry`, `INTERLIS.SurfaceEdge` und
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
2.4 §3.15). Eigene Kurvensegmente verwenden dieselben kontextbezogenen
Struktur-, Typ- und Ausdruckswerte wie die Standardsegmente. Die frühere
pauschale Sperre INSPECTION_CUSTOM_SEGMENT entfällt bei vollständiger Bindung.
Die gesonderte Prüfung der Quellabstraktion selbst deklarierter LineCoord-Member
ist als offene G2-Regel dokumentiert und wird nicht durch die Kontextkopie ersetzt.

Alle Werte sind unveränderlich, nullfrei und snapshotgebunden; Sammlungen werden
kopiert. Aufbauzustand gehört dem Compilerlauf, Budget und Abbruch werden über
seinen bestehenden RunContext geprüft. Vordefinierte UNIQUE-Ausdrücke verwenden
leere Bereiche der Quelle `predefined:/INTERLIS-<language>`; sie sind keine
navigierbaren Quelltextstellen. Vertragstests: `MetadataBindingContractTest`,
`GeometryInspectionContractTest`, `DiagramProjectionTest`; positive und negative
Versionen, Identitäten, Übersetzungen, Imports, Dateireihenfolge und
Fresh-vs-Reuse müssen fachlich übereinstimmen. Eine vollständige P3-Abnahme
folgt daraus nicht.

### K5: Gebundene Grafikdefinitionen, Vorab-API 0.2

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

### K6: Umrechnungsfaktoren und skalare Einheitenvererbung

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

`NumericUnitConversionContractTest` prüft die sechs K6-Regressionen, öffentliche
Werte, Fehlerbereiche, importierte Bindungen, Vererbung, Typqueries,
Übersetzungen sowie frische und wiederverwendete Analyse in 2.3/2.4.


### G2 C: Effektive Geometrie und versionsgerechte Toleranz

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
öffentlichen Inhalte und positiven/negativen Fälle; aktuelle Ausführungsevidenz
steht im Implementierungsstatus.


### G2 D: Verantwortliche Signaturklasse

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
Übersetzungen, Imports, Dateireihenfolge und Fresh-vs-Reuse. Ausführungsevidenz
und verbleibende G2-Grenzen stehen im Implementierungsstatus.

### E1: Modellkopfoptionen

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

E1 ergänzt `ModelOptions.language(): Optional<TextValue>` mit der dokumentarischen
Modellsprache und Originalherkunft. `ModelSyntax.language()` ist daraus abgeleitet;
der redundante String-Konstruktorparameter entfällt in API 0.2. Modell- und
Übersetzungssprache bleiben getrennt. Eigene Sprachbezeichnungen bleiben zulässig.

### E2: Einheitenrelationen und Domäneneinschränkungen

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
Referenzsystemregeln bleiben in der G2-Matrix gesondert abzuprüfen.

MANDATORY wird im Domain-Syntaxmodifier und im effektiven DomainSymbol erhalten;
`DomainSymbol.mandatory()` liest den effektiven Flag. MANDATORY allein übernimmt
bei einer Erweiterung den gebundenen Basistyp; ohne Basis ist es ein Quellfehler.
Attribute und Parameter übernehmen den Pflichtflag verwendeter Domänen.
Aliastypen werden nicht mutiert. CLASS-Domänen behalten ihre gebundenen
Restriktionen und ihre typisierten CONSTRAINTS. resolveAliases/canAssign/
canExtend/areComparable bewahren Invalid und Pending-Ursachen; Aliaszyklen
werden über besuchte IDs begrenzt, nicht über eine willkürliche Länge von 64.

E2-Präzision ist versionsabhängig: 2.3 §2.8.5 S.42 erlaubt Verfeinerung,
sofern gerundete Grenzen im Basisbereich bleiben (7.9949 ja, 7.9999 nein bei
Basismaximum 7.99). 2.4 §3.8.5 S.53 verlangt unveränderte Präzision.
`TypeRelations.canExtend(derived, base, context, language)` erhält daher einen
expliziten nullfreien LanguageVersion-Parameter; weder Quellnamen noch ein
zufällig gefundenes Modell bestimmen das Profil. Interne Verbraucher und
Vorab-API-0.2-Tests werden direkt angepasst, ohne alte Überladung.
Die 2.3-Rundung verwendet exakte Dezimalwerte und HALF_UP; Tie-Details bleiben
in der Normprüfung separat sichtbar. Allgemeine Zuweisung ist unverändert.

### E2: Normative TEXT-Länge

Textliteraltypen verwenden in 2.3 §2.8.1 und in 2.4 §3.8.1: Anzahl Zeichen mit kanonischer
Kombinationsklasse 0 nach kanonischer Zerlegung (NFD). Dies ist weder UTF-16-
Länge noch die Anzahl Unicode-Codepoints im ursprünglichen Text. Es wird nur
die Länge bestimmt; LiteralExpression.textValue(), Lexem und SourceRange bleiben
unverändert. Deklarierte maximale Länge muss positiv sein; Länge null eines
Literals bleibt möglich. CR/LF/TAB erfordern MTEXT, auch bei Unicode-Escapes.

Der portable Compiler verwendet gepinnte Unicode-17.0.0-Gewichtungsintervalle
und Hanguls kanonische Zerlegung, ohne Laufzeit-Normalizer und ohne neue
Abhängigkeit. Generierung und Normalisierungskontrolle liegen unter
`tools/reference/generate_text_weights.py` und
`tools/reference/TextLengthNormalizationProbe.java`; die Eingaben und Pins
stehen im Nachweis. Klassen von kombinierenden Zeichen werden nicht aus der
allgemeinen Zeichenkategorie Mn/Mc abgeleitet. NFKD-Kompatibilitätszerlegungen
werden nicht angewendet. Die Längenregel gilt in beiden Sprachversionen;
Unicode-Repertoire und Modell-CHARSET sind davon getrennte Verträge.

### E3: Gebundene Strukturwerte und Restriktionen

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

### E2: Gemeinsame Erweiterung effektiver Typen

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

### E4: Enumwerte, Operatoren und Standardsignaturen

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
ihre eigene Identität. convertUnit und die areAreas-Varianten bleiben bis zu
ihrer vollständigen Kontextbindung ausdrücklich gesperrt; diese Teillieferung
behauptet keine vollständige Builtin- oder E4-Abnahme.

Formatvergleiche verlangen gebundene verwandte Komponenten oder eine
Formatbasisbeziehung; gleicher TypeKind genügt nicht (z.B. XMLDate/XMLTime).
Die weitergehende gemeinsame FORMAT-Zuweisungs-/Erweiterungsprüfung bleibt
als eigene E2-Abnahme bestehen. Der Dateiendevertrag wird ausschliesslich
im Syntaxdienst geprüft, mit ILI-SYN-0004 für Folgetokens.

### E4: Gebundene Funktionsargumentpfade und Zahlenkonstanten

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

### E1/E4: Definiertheit von Funktionssignaturen

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

### E2: Gemeinsame FORMAT-Relationen

META-006/014 und SEM-005/010/014: Zuweisung und Erweiterung formatierter Typen
verwenden die gebundenen Strukturen, Komponenten und Grenzen. Eine Erweiterung
erhält das geerbte Layout als Präfix, spezialisiert seine Komponenten und darf
Grenzen einschränken. Erweiterte Attribute behalten ihre Basisidentität für
diese Prüfung. Die öffentliche Typabfrage verwendet dieselbe Regel wie der
Compiler. Die bestehende Auswertung gebundener FORMAT-Werte wird intern nach
interlis-model verschoben und gemeinsam verwendet; es entsteht kein zweiter
Formatter oder öffentlicher Einstieg.

### E2: Vollständigkeit numerischer und Koordinaten-Deklarationen

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

E1 zu META-015: Vordefinierte Boolean-Typen für Literale, logische/vergleichende
Ausdrücke und Standardfunktionssignaturen werden nach Sprachversion besessen.
Eine gemischte Closure darf keine 2.3-Herkunft für ein 2.4-Ergebnis erhalten.
Unbenutzte parallele Primitive werden nicht vorsorglich materialisiert.

### E5: Gebundene Quell-Metadaten

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

Vordefinierte OID-Domänen besitzen innerhalb eines Sprachprofils eine kanonische
Identität, `PredefinedOrigin` und keine künstliche Benutzermodell-Eigentümerschaft.
L1-1 ergänzt den tatsächlichen vordefinierten Owner `INTERLIS` je Sprachversion:
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

### E2: Benannte Collection-Elementtypen nach Sprachversion

META-006/014: Ein einfach benannter BAG-/LIST-Elementtyp wird in 2.4 als
DomainReferenceSyntax gebunden. Das Ziel ist eine Domäne oder eine Struktur;
CollectionType erhält den tatsächlichen Elementtyp samt gebundenem Ziel.
DomainReferenceType und TypeRelations.resolveAliases erhalten die referenzierte
Domäne; Domain-Vererbung bleibt davon getrennt. In 2.3 sind solche Elemente
auf Strukturen beschränkt. ANYSTRUCTURE und explizite RESTRICTION behalten ihre
Strukturbindung in beiden Versionen. Eine Klasse ist kein Strukturelement.
Grundlage: 2.3 §2.6.1 S.32, 2.4 §3.6.1 S.40. Keine neue öffentliche Typvariante.

### E5: Übersetzung geometrischer Inspektionskontexte

META-007/010/014: ModelTranslation.correspondences enthält auch überprüfte
Kontextkopien eigener und vordefinierter Segmente, ihre Mitglieder/Constraints
sowie die erzeugten Inspektionsstrukturen. Kanonische Segmentdeklarationen
bleiben erhalten. Die Zuordnung verwendet die tatsächliche Kontextualisierungs-
map und explizite Rollen der erzeugten Wrapper; Anzeigenamen und synthetische
ID-Strings sind keine fachlichen Identitätsregeln. Unterschiedliche Vertextypen
behalten getrennte Kopien. Sämtliche veröffentlichten Links sind unveränderlich
und gehören zum selben Snapshot. Explizite Geometrietoleranzen werden anhand
ihres exakten Werts verglichen, nicht anhand der Dezimalschreibweise.

### E1: Einheitliche Bindung vordefinierter Namen

META-006/014/015 und SEM-008: Qualifizierte INTERLIS-Namen benötigen keinen
Import; einfache Namen dieser Elemente benötigen IMPORTS UNQUALIFIED INTERLIS
(2.3 §2.5.1 S.28, 2.4 §3.5.1 S.34). Primitive Typschlüsselwörter bleiben davon
unabhängige Syntax. Vorhandene Fabriken veröffentlichen dieselben kanonischen
Ziele bei qualifiziertem und unqualifiziertem Zugriff. Quellreferenzen erhalten
ihre ursprünglichen Namen, UTF-16-Bereiche und Revisionen. Keine neue API.

### E4: Namen von Konsistenzbedingungen

META-006/011/014: Benannte Konsistenzbedingungen in 2.4 besitzen innerhalb
ihrer deklarierenden Klasse beziehungsweise ihres Wertebereichs eindeutige
Namen (3.12 S.77). Die Constraintfamilien teilen diesen Namensraum; unbenannte
Bedingungen benötigen keinen künstlich öffentlichen Namen. Der vorhandene
ConstraintSymbol bewahrt den tatsächlichen Namen und seinen Quellbereich,
auch für CONSTRAINTS OF. Struktur-, Assoziations- und Viewkörper verwenden
dieselbe direkte Namensprüfung wie Klassen.

### L1-2: numerische Unterteilungen und exakte Faktorbedingungen

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
freigegebene Profil aus Kapitel 06: nur nachweisbare Faktorwidersprüche werden
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
