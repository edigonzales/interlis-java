# 05 – Metamodell und Typensystem

## 1. Ziel, Modul und Abgrenzung

Das Modul `interlis-model` enthält das eigene fachliche Modell sowie die kleinen, gemeinsam benötigten Quelltext- und Diagnostiktypen. Es hängt nicht von ANTLR, Compiler, Workspace, Repositories, LSP, Datenbanken oder einer Geometrie-Engine ab. `guru.interlis.model` und `guru.interlis.model.query` bilden die öffentliche Modell-API; interne Aufbauhilfen müssen als solche abgegrenzt bleiben.

**Das Metamodell ist der zentrale gemeinsame fachliche Vertrag des neuen Stacks.** Eine Klassenliste für Diagramme allein erfüllt diesen Vertrag nicht. Ebenso wenig genügt ein Baum, der die Grammatikproduktionen ohne Namensauflösung abbildet. Das Modell muss vom Compiler geprüfte Beziehungen, effektive Eigenschaften und typisierte Ausdrücke ausdrücken können. Dasselbe Datenmodell muss während der Analyse unvollständige oder fehlerhafte Zustände ausdrücklich repräsentieren können.

Die bisherigen Verbraucher sind wichtige Vergleichsquellen. [iox-ili/Validator.java](https://github.com/claeis/iox-ili/blob/2807992c45bb10d18f44ea4a882fc19f6dd1e4cb/src/main/java/ch/interlis/iox_j/validator/Validator.java) und [ili2c/Viewable.java](https://github.com/claeis/ili2c/blob/cbccff35fc98fdfa370e42e3bb39bb4edd70a658/ili2c-core/src/main/java/ch/interlis/ili2c/metamodel/Viewable.java) zeigen, dass unter anderem aufgelöste Typen, Vererbung und effektive Attribut-/Rollenreihenfolgen fachlich gebraucht werden. Diese Informationen dürfen nicht erst in jedem Verbraucher neu interpretiert werden.

IlisMeta07 und IlisMeta16 werden als standardisierte fachliche Referenz und mögliche Exportziele berücksichtigt. Sie ersetzen nicht die zusätzlichen Zustände für Fehler, Herkunft, Referenzsegmente und Analysedeckung. Primärquelle: [INTERLIS-Metamodell](https://www.interlis.ch/modelle/metamodell).

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
| `ClassSymbol` | Basistyp, Attribute, Constraints, OID-Regel | Identität und Struktur-/Klassenunterschied explizit. |
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
| Zeit | `TemporalType` mit konkreter normativer Datums-/Zeitart und Versionszulässigkeit. |
| Geometrie | `CoordinateType`, `MultiCoordinateType`, `LineType`, `MultiLineType`, `SurfaceType`, `MultiSurfaceType`, `AreaType`, `MultiAreaType`. |
| Identifikation | `OidType` mit ANY/numerisch/Text/UUID-Semantik und eigener Herkunft. |
| Blackbox | `BlackboxType` mit XML/BINARY-Art. |
| Strukturen/Collections | `StructureValueType`, `CollectionType` mit BAG/LIST, Kardinalität und Restriktionen. |
| Referenzen | `ReferenceType` mit Zielmenge, EXTERNAL-Regel und Restriktionen. |
| Klassen-/Pfadwerte | `ClassValueType`, `AttributePathType`; Werte sind fachliche Klassen/Attributpfade, keine normalen Objektverweise. |
| Funktionsargumente | `ObjectArgumentType`, `ObjectsArgumentType`, `EnumValueArgumentType`, `EnumTreeValueArgumentType`. |
| Alias/Generics | `AliasType`, `GenericType`, `DeferredTypeBinding`; deklarierte und effektive Sicht bleiben unterscheidbar. |
| Analysefehler | `ErrorType` mit Diagnoseursache, `PendingType` mit nicht analysiertem Zustand; niemals produktive fachliche Datentypen. |

`MANDATORY` wird als Null-/Definiertheitsbedingung des Werts beziehungsweise Attributes modelliert. BAG/LIST-Kardinalität und Elementdefiniertheit werden separat behandelt. Ein Defaultwert für Collectiongrenzen oder Rollenmultiplizität muss aus dem jeweiligen Sprachprofil kommen und darf nicht als allgemeines Java-Default geraten werden.

Geometrietypen speichern Achsen, Dimension, Vertexdomäne, Rotation, Referenzsystem, erlaubte Linienformen, Richtung, Überlappungsparameter und gegebenenfalls Linienattribute. Es werden keine JTS-Geometrieobjekte erzeugt. Eine gültige Typbeschreibung ist unabhängig davon, ob ein späterer konkreter Geometriealgorithmus ihre Daten bereits verarbeiten kann.

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
                                  ExtensionContext context);
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

Eine Überschreibung darf die durch die fachlichen Regeln definierte Position nicht unbemerkt verändern. Eingebettete Assoziationsrollen werden als solche markiert; sie werden nicht zu gewöhnlichen Attributen umetikettiert. Für eine Diagrammoption „nur eigene Attribute“ wird nach Herkunft gefiltert. Für Transfer-/DB-Pläne wird die vollständige effektive Information verwendet.

`TranslationLink` verbindet Original- und Übersetzungssymbole mit überprüfter Korrespondenz. Ein Transfername wird erst unter einem ausgewählten Transfer-/Sprachprofil bestimmt. Die Entscheidung ist nicht in `SymbolId` kodiert. Konflikte und fehlende Entsprechungen bleiben fachliche Diagnostik; eine Übersetzung ist keine globale Umbenennung aller Symbole im Graphen.

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
