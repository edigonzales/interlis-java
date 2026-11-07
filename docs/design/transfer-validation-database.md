# Entwurf für Transfer, Validierung und Datenbanken

Der formatunabhängige Transferkern steht in `interlis-transfer` für INTERLIS 2.3 und 2.4 zur Verfügung. `interlis-xtf23` liest und schreibt XTF-2.3-FULL-Transfers objektweise. Weitere Formatadapter, Datenvalidierung und Datenbankabbildung sind geplant. Die API-Skizzen beschreiben den vorgesehenen Ausbau.

Die Verantwortung wird entlang fachlicher Produkte geschnitten:

```text
CompiledSchema + explizite Verbraucheroptionen
  → TransferPlan
  → ValidationPlan
  → SchemaPlan
```

Ein `CompiledSchema` bleibt ein unveränderliches, fachlich geprüftes Modell. Projektionen erzeugen weitere unveränderliche Pläne. Laufzeitdaten, Caches, Objektidentitäten, Datenbankverbindungen und Zwischenergebnisse einer Datenvalidierung gehören in laufgebundene Kontexte.

`interlis-transfer` verwendet die öffentliche, snapshotgebundene Modellsicht. Als weitere Verbraucher sind `interlis-validation` und `interlis-db` vorgesehen. Der Compiler stellt die Modellinformationen bereit und belegt sie mit Metamodell- und Projektionstests.

<a id="gemeinsame-planverträge"></a>
## Gemeinsame Planverträge

<a id="identitäten-und-unveränderlichkeit"></a>
### Identitäten und Unveränderlichkeit

`ConsumerId(String value)` bezeichnet beispielsweise den Transferwriter oder den DB-Planer. `FeatureId(String value)` bezeichnet ein fachliches Merkmal, das für einen Verbraucher relevant ist. IDs werden in einem versionierten Katalog gepflegt; eine nicht bekannte Feature-ID darf nicht als implizit unterstützt gelten.

`ConsumerCapabilities` besitzt:

```java
ConsumerId consumer();
String implementationVersion();
Set<FeatureId> supportedFeatures();
CapabilityCheck check(RequiredFeatures requirements);
```

`CapabilityCheck` ist unveränderlich und enthält geordnete fehlende Merkmale mit Ursprungselementen. Eine Menge wird bei Serialisierung kanonisch geordnet; HashMap-/HashSet-Iterationsreihenfolgen sind kein Austauschvertrag.

`RequiredFeatures` wird aus dem tatsächlich angeforderten Modell- und Operationsumfang berechnet. Ein Transferexporter muss nicht jede mögliche DB-Funktion unterstützen. Umgekehrt darf ein blosses Vorkommen eines unbekannten Merkmals nicht ignoriert werden, wenn es den konkreten Transfer verändern kann.

`PlanFingerprint(String value)` identifiziert die kanonischen Plan-Eingaben: semantischer Schemainhalt, Optionsschema und Optionswerte, Verbraucher- und Profilversionen, externe Funktionssignaturen soweit relevant, Overlays und bestehende DB-Metadaten. Reine Quellpositionsänderungen müssen einen positionsunabhängigen Transferplan nicht zwangsläufig ändern; die Implementierung muss aber Diagnoseprovenienz und semantische Gleichheit getrennt behandeln.

Die zugehörige `PlanProvenance` enthält den Ursprungssnapshot, relevante Quellstände, Profil-IDs und Fingerprints. Ein Plan darf keine Referenz auf einen später veränderbaren Workspace behalten. `SymbolId` ist innerhalb eines Plans an dessen ursprünglichen Snapshot gebunden. Ein persistierter Plan verwendet zusätzlich die definierte kanonische fachliche Identität und sein Schemainhalt-Fingerprint; snapshotlokale IDs allein sind kein dauerhaftes Austauschformat.

<a id="planungsergebnis"></a>
### Planungsergebnis

Die zukünftige gemeinsame Ergebnisschnittstelle ist:

```java
public sealed interface PlanningOutcome<T> {
    record Ready<T>(T plan, List<Diagnostic> diagnostics)
            implements PlanningOutcome<T> {}
    record Unsupported<T>(CapabilityCheck capabilities,
                          List<Diagnostic> diagnostics)
            implements PlanningOutcome<T> {}
    record Rejected<T>(List<Diagnostic> diagnostics)
            implements PlanningOutcome<T> {}
    record Cancelled<T>(CancellationReason reason)
            implements PlanningOutcome<T> {}
    record Failed<T>(FailureId failureId, Diagnostic diagnostic)
            implements PlanningOutcome<T> {}
}
```

Die Darstellung illustriert den öffentlichen Vertrag; produktiver Code muss Collections defensiv kopieren, Pflichtwerte prüfen und geeignete katalogisierte Gründe verwenden. `Rejected` beschreibt beispielsweise widersprüchliche Mappingoptionen oder vorhandene Metadaten. Es bedeutet nicht, dass das zuvor erzeugte `CompiledSchema` nachträglich sprachlich ungültig geworden ist.

Pläne werden vollständig geprüft veröffentlicht. Ein abgebrochener Aufbau veröffentlicht keinen halb initialisierten Plan. Eine teilweise abfragbare Planung kann später als gesonderter Entwurfstyp eingeführt werden; sie darf nicht dieselbe Erfolgsvariante verwenden.

<a id="transferplan-und-datentransfer"></a>
## TransferPlan und Datentransfer

<a id="eingaben-und-planner"></a>
### Eingaben und Planner

`TransferPlanner.plan(CompiledSchema)` verwendet die Namen der kanonischen Originaldeklarationen. Die Überladung mit `TransferPlanner.Names.DECLARED` wählt die deklarierten Namen und behält die kanonische Identität. Der Planner übernimmt Typen, Reihenfolge und Herkunft aus dem geprüften Schema. `TransferPlanningResult.Ready` enthält den vollständigen Plan im unterstützten Umfang. `Unsupported` nennt betroffene Pfade und Merkmale. Legale generische Bindungen werden für die spätere Basketkonkretisierung erhalten.

Explizite Transferprofile und zusätzliche Quellmetadaten gehören zum späteren Ausbau an den Adaptergrenzen. Die Quellenhaltung wird mit der [Repository-Auflösung](../contracts/repositories.md) abgestimmt.

<a id="planstrukturen"></a>
### Planstrukturen

Die folgende Tabelle beschreibt den vorgesehenen Ausbau der Deskriptoren.

| Typ | Wesentliche Informationen |
|---|---|
| `TransferPlan` | Provenienz, Profil, Modelle/Topics, Tagindex, Objektdeskriptoren, benötigte Laufzeitbindungen |
| `TopicDescriptor` | Kanonische Topicidentität, Transfername, zulässige Objekttypen, Basketregeln |
| `ObjectDescriptor` | Kanonische Klasse/Struktur/Assoziation, Transfername/Tag, Basistyp, effektive Slots, Objektidentitätsregeln |
| `AttributeSlot` | Feste Position, kanonisches Attribut, Transfername, Typdeskriptor, Kardinalität, Herkunft/Überschreibung |
| `RoleSlot` | Rolle, Zieltyp, Kardinalität, Einbettungs-/Assoziationsbezug, Transferdarstellung |
| `RuntimeTypeDescriptor` | Aufgelöste statische Typinformation plus gegebenenfalls rechtmässige Laufzeitbindung |
| `BasketBindingRequirements` | Erwartete generische beziehungsweise aufgeschobene Konkretisierungen |

Die Zuordnung formatspezifischer Tags gehört zum späteren Adapter. `TransferPlan.object(SymbolRef ref)` prüft die Snapshotzugehörigkeit und liefert den Deskriptor für die enthaltene Symbol-ID, falls er im Plan liegt. Der Plan hält kanonische Identität und gewählte Transferbezeichnung getrennt.

`ObjectDescriptor.effectiveSlots()` liefert die fachlich richtige, unveränderliche Reihenfolge. Die Position eines überschriebenen Attributes darf nicht allein durch das Auftauchen in einer neuen lokalen Liste verändert werden. Eine Assoziation mit eigenen Attributen oder höherer Stelligkeit muss als eigenständiger semantischer Sachverhalt erhalten bleiben.

<a id="transferereignisse-und-objektmodell"></a>
### Transferereignisse und Objektmodell

`TransferReader.readNext(CancellationToken)` und `TransferWriter.write(...)` prüfen die Folge `StartTransfer`, Baskets mit `StartBasket`, `ObjectEvent` und `EndBasket`, sowie `EndTransfer`. `Optional.empty()` bestätigt das Quellenende nach `EndTransfer`. Ereignisse sind unveränderlich und bleiben nach dem nächsten Aufruf lesbar. Assoziationsereignisse erhalten die Rollen und die eigenen Attribute der Beziehung. Die Einbettung im konkreten Format bestimmt der Adapter.

Ein `TransferObject` hält Identität, Typbezug und Attribut-/Rollenwerte eines einzelnen Objekts. Adapter legen Grössen- und Tiefengrenzen für einzelne Objekte fest. Der Kern hält die Modelldeklarationen, den aktiven Basketkontext und getrennte Mengen für BID und FULL-TID. Der Speicher für diese Kennungen wächst mit ihrer Anzahl und wird bei Transferende, Fehler oder Close freigegeben.

Der Kern vergleicht die Zeichenwerte innerhalb dieser Namensräume unabhängig von der Herkunft `SOURCE` oder `SYNTHETIC`. Die OID-Regeln für INITIAL und UPDATE gehören zum jeweiligen Transferprofil. Der XTF-2.3-Adapter muss für FULL zusätzlich BID und TID gemeinsam prüfen, auch wenn die gleichlautende Kennung zuerst als TID erscheint. In XTF 2.4 gilt die transferweite Eindeutigkeit der FULL-TIDs und die allgemeine Objektstromregel für Behälter- und Objektidentifikationen. Die Akzeptanz gleicher Zeichenwerte in den getrennten Namensräumen des Kerns trifft keine Aussage über die Gültigkeit einer konkreten XTF-Codierung. [Gepinnte Normbelege und P4.0-Befunde](../reference/iox-legacy.md#normbelege)

`Xtf23Reader` und `Xtf23Writer` verwenden vom Aufrufer verwaltete Streams. OIDSPACE-Deklarationen bleiben in Quellreihenfolge am Adapter verfügbar. Der FULL-Adapter liest und schreibt BLACKBOX XML und BINARY, OID-Attributwerte sowie CLASS-, STRUCTURE- und ATTRIBUTE-Pfadwerte. Modellgebundene Linienattribute bleiben an jeder einzelnen Randlinie von SURFACE und AREA erhalten. Nichtleere ALIAS-Tabellen, inkrementelle Transfers, CLIPPED-Geometrien und benutzerdefinierte Linienformen meldet der Adapter als `UNSUPPORTED`. Er prüft die XML- und Wire-Struktur. Datenconstraints und Geometrietopologie gehören zur späteren Validierung.

Die LINEATTR-Abnahme prüft den Reader-/Writer-Roundtrip gemäss INTERLIS 2.3 §§3.3.11.12–13. In ilivalidator 1.15.0 meldet der Standardreader von IOX 1.24.4 bei dieser Kodierung `expected segment type`. Mit `ILI_XTF23_READER=ch.interlis.iom_j.xtf.Xtf23Reader0` gelangt die Datei bis zur Polygonprüfung, die `Lineattributes not supported` meldet. Beide Validatorläufe liefern Exit 1.

Der XTF-2.3-Header führt mindestens ein Modell. Ein leerer Datenbereich ist zulässig. Ein vorhandener BAG- oder LIST-Wrapper enthält mindestens eine Struktur. XTF 2.3 lässt undefinierte Attribute aus und erhält leeren TEXT als eigenen Wert.

Das Datenmodell unterscheidet mindestens:

- nicht vorhandenen Slot beziehungsweise Wert;
- ausdrücklich undefinierten Wert, soweit das Format dies unterscheidet;
- Text, Booleschen Wert, exakte Zahl und Aufzählungswert;
- Referenz mit Identitäts- und gegebenenfalls Basketbezug;
- verschachtelte Struktur;
- geordnete beziehungsweise ungeordnete Mehrfachwerte gemäss Modell;
- Geometrie mit ausreichender Information über Koordinaten, Bögen und Genauigkeit.

Eine Java-`null`-Sammlung ist kein Ersatz für diese Unterscheidungen. Ein allgemeines `Map<String,Object>` darf an einer Adaptergrenze hilfreich sein, aber nicht der einzige typisierte Vertrag des neuen Stacks werden.

`StartBasket` erhält Basketidentität, Topics, Zustandsmetadaten und Laufzeitdomänen. Im neutralen Kern gilt bei `CONCRETE` die einzige vom Modell festgelegte Alternative. Eine mitgelieferte Zuordnung muss damit übereinstimmen. `DEFERRED` erfordert eine explizite Auswahl aus den veröffentlichten Alternativen. `ABSTRACT` beschreibt eine unaufgelöste Bindung. Ein Basket braucht ein konkretes Topic. Für alle wirksamen Verwendungen derselben generischen Domäne im Basket gilt dieselbe Konkretisierung, einschliesslich geerbter Verwendungen.

Der XTF-2.4-Adapter muss nach §4.3.6 auch schemafeste generische Verwendungen in `domains` explizit zuordnen. Die Auswertung inkrementeller Änderungen gehört zum späteren Verbraucher.

<a id="iom-iox-adapter"></a>
### IOM-/iox-Adapter

Ein zukünftiger `IomAdapter` erhält getrennte Methoden:

```java
TransferObject fromIom(IomObjectView input, TransferPlan plan,
                       BasketRuntimeContext basket);
IomObjectView toIom(TransferObject input, TransferPlan plan);
```

Die konkreten Legacy-IOM-Klassen werden nur in einem Adaptermodul referenziert. `IomObjectView` ist hier ein abstrakter Grenzvertrag; die Implementierung kann einen alten `IomObject` oder eine eigene Sicht kapseln. Die API darf nicht behaupten, dass identische Methodennamen bereits identische Semantik bedeuten.

Abnahmefälle umfassen Referenzen, OID-/Basketbezug, verschachtelte Strukturen, Reihenfolgen, Bögen, undefinierte Werte und übersetzte Namen. Daten, die nicht verlustfrei darstellbar sind, erzeugen eine explizite Diagnose. Eine Bogenlinearisierung ist eine deklarierte Transformation mit Optionen und messbaren Folgen, keine allgemeine beiläufige Konvertierung.

<a id="validationplan-und-validator"></a>
## ValidationPlan und Validator

<a id="semantik-und-laufzeit-trennen"></a>
### Semantik und Laufzeit trennen

Der bisherige [`iox-ili Validator`](https://github.com/claeis/iox-ili/blob/master/src/main/java/ch/interlis/iox_j/validator/Validator.java) verwendet zahlreiche Metamodellkonzepte; `ilivalidator` bildet das Werkzeug um diese Fähigkeiten. Der neue Stack soll die getypte semantische Grundlage des Compilers direkt weiterverwenden.

`ValidationPlanner.plan(CompiledSchema schema, ValidationOptions options, FunctionCatalog functions, GeometryCapabilities geometry, CancellationToken token)` liefert `PlanningOutcome<ValidationPlan>`.

`ValidationPlan` enthält lokale Objektregeln, notwendige Referenzprüfungen, Basketregeln, globale beziehungsweise transferweite Regeln und explizite Anforderungen an Indizes und externe Funktionen. Keine Regel wird bei der Planung allein aus ihrem ursprünglichen Text erneut geparst. Eine getypte Ausdrucksprojektion erhält Typen, Pfadschritte, Einheiten, exakte Literale, Unbekannt-/Undefiniertverhalten und Herkunft.

`FunctionCatalog` beschreibt verfügbare Laufzeitimplementierungen über deklarierte Signaturen und versionierte Implementierungsidentitäten. Die Existenz einer fachlich gültigen externen Funktionsdeklaration bedeutet nicht, dass jeder Host diese Funktion ausführen kann. Ein Validator muss vor Ausführung melden können, welche benötigte Implementierung fehlt.

<a id="regelklassen-und-coverage"></a>
### Regelklassen und Coverage

| Regelklasse | Typische Aufgabe | Ressourcenstrategie |
|---|---|---|
| Lokal | Einzelwert, Kardinalität, lokal auswertbarer Constraint | Objektweise, zeitnah freigeben |
| Referenziell | Existenz und Typ von Referenzzielen | Identitätsindex und gegebenenfalls spätere Auflösung |
| Basketbezogen | Abhängigkeiten und Kontext eines Baskets | Begrenzter Basketzustand oder expliziter Spill |
| Transferweit | Eindeutigkeit, Mengen- und globale Regeln | Indizes, Sortierung, Aggregation oder zweite Phase |
| Geometrisch | Form-, Topologie-, Flächen- und Beziehungsprüfungen | Geeignete Geometrieoperationen und räumliche Indizes |

Diese Tabelle legt keine ungeprüften Einzelregeln der INTERLIS-Norm fest. Der Agent ordnet jede tatsächlich implementierte Normregel anhand ihrer benötigten Information zu und hinterlegt eine Referenz sowie Tests.

`ValidationMode` erhält mindestens `FULL` und `LOCAL_ONLY`. `ValidationCoverage` zählt geprüfte Regeln und führt ausgelassene Regelklassen samt Grund auf. Ein fehlerfreier `LOCAL_ONLY`-Lauf ergibt `PASSED_CHECKED_SCOPE`, nicht `VALID` für den gesamten Transfer.

<a id="laufzeitklassen-und-methoden"></a>
### Laufzeitklassen und Methoden

`ValidationSession` ist ein explizit laufgebundener, veränderlicher Zustand mit:

```java
void beginTransfer(TransferHeader header);
void beginBasket(BasketHeader header);
void accept(TransferObject object, DataLocation location,
            CancellationToken cancellation);
void endBasket(CancellationToken cancellation);
ValidationReport finish(CancellationToken cancellation);
void close();
```

Vorbedingungen und Nachbedingungen werden über einen Zustandsautomaten durchgesetzt. `accept` vor `beginBasket`, ein zweites `finish` oder ein Objekt nach Transferende sind Protokollfehler des aufrufenden Adapters. Sie dürfen nicht als beliebige fachliche Datenfehler verschleiert werden.

`accept` führt lokale Prüfungen aus und registriert nur die für spätere Regeln erforderlichen Informationen. `endBasket` führt die jetzt abschliessbaren Regeln aus. `finish` bearbeitet verbleibende globale Regeln und erzeugt einen unveränderlichen Bericht mit Coverage, Zählern, begrenzten Diagnosebeispielen und gegebenenfalls einer separaten vollständigen Diagnosesenke.

Eine sehr grosse Anzahl Datenfehler darf nicht zwangsläufig als vollständige Liste im Heap gehalten werden. Der Bericht unterscheidet Gesamtanzahl, gespeicherte Beispiele und ausgeschriebene Diagnosen. Ein Abbruch darf nicht als vollständig bestandene Prüfung erscheinen.

`DataLocation` ist ein eigener Typ für Transferquelle, XML-/Dateiposition soweit verfügbar, Basketidentität, Objektidentität und Attribut-/Strukturpfad. Er darf nicht in einen `SourceRange` für ILI-Modelltext gepresst werden. Eine Datenregel kann sowohl `DataLocation` als auch den `SourceRange` des verursachenden Modellconstraints besitzen.

`ExpressionEvaluator.evaluate(ExpressionProgram program, EvaluationContext context)` liefert einen getaggten `EvaluationResult`. Dieser unterscheidet Wert, fachlich undefiniert, aufgrund fehlender Voraussetzung nicht auswertbar und internen Ausführungsfehler. Java-Exceptions sind kein Ersatz für normale Undefiniertsemantik.

`EvaluationContext` enthält aktuelles Objekt, Basketkontext, Zugriff auf freigegebene Indizes, Funktionsimplementierungen und Budget. Er hält keine versteckten globalen Registries. Der Ausdrucksplan kann referenzielle Reinheit und benötigte Kontexte ausweisen, damit wiederholte Auswertungen gezielt wiederverwendet werden.

<a id="geometrie-und-speicherbudget"></a>
### Geometrie und Speicherbudget

Die Schnittstelle `GeometryOperations` kapselt die konkret gewählte Bibliothek. Methoden werden aus echten benötigten Operationen abgeleitet, zum Beispiel `validate`, `relate`, `measure` und eine indexfähige Hüllgeometrie. Der Java-Compiler muss diese Bibliothek nicht laden, um eine Geometriedomäne zu typisieren.

`GeometryPolicy` enthält Präzisionsmodell, Toleranzen, Umgang mit Bögen und relevante Prüfoptionen. `GeometryCacheKey` umfasst Objekt-/Attributidentität beziehungsweise unveränderlichen Inhaltsfingerprint, diese Policy und die Operation. Ein Cache allein nach Objekt-ID ist unzulässig, wenn Geometrie oder Prüfoptionen veränderbar sind.

Teure vorbereitete Geometrien oder räumliche Indizes dürfen wiederverwendet werden, wenn die jeweilige Bibliothek dies für unveränderte Eingaben erlaubt. Konvertierung, Normalisierung und Validierung werden nicht für jede identische Teilprüfung blind wiederholt. Jede Optimierung muss Ergebnisgleichheit einschliesslich Fehlerfällen nachweisen.

Das Standardbetriebsprofil strebt repräsentative produktive Datenprüfungen, einschliesslich grösserer Datensätze, mit **`-Xmx2g` (2 × 2^30 = 2'147'483'648 Byte Java-Heap)** an. Das Ziel ist anhand repräsentativer Lasten zu überprüfen und gilt ohne universelle Leistungs- oder Kapazitätsgarantie. Fachlich extrem komplexe Eingaben dürfen nach Begründung höhere Budgets benötigen.

Ein künftiges `ResourceBudget` führt den Java-Heap, den Gesamtprozessspeicher (RSS), native Allokationen, Cachegrenzen und den temporären Spitzenplatz als getrennte Grössen. Für einzelne Objekte und Geometrien gelten eigene Grenzen. I/O und Laufzeit werden separat gemessen. Der Host plant eine gemessene Sicherheitsmarge ein und stoppt kontrolliert vor einer absehbaren Überschreitung.

Transferweite Indizes benötigen eine `IndexStore`-Abstraktion mit kleinen In-Memory- und später bedarfsgerecht ausgelagerten Implementierungen. Die Spezifikation schreibt keinen verteilten Cluster vor. Eine lokale, nachvollziehbare Spill-Strategie wird erst anhand realer Datenmengen gewählt und muss Datentypen, Ordnung und Identitäten verlustfrei erhalten.

<a id="datenbankabbildung-und-bestehende-ili2db-datenbanken"></a>
## Datenbankabbildung und bestehende ili2db-Datenbanken

<a id="referenzverhalten-explizit-beschreiben"></a>
### Referenzverhalten explizit beschreiben

Die bisherige [`NameMapping`](https://github.com/claeis/ili2db/blob/master/src/ch/ehi/ili2db/mapping/NameMapping.java) verarbeitet unter anderem Namenskürzungen, Kollisionen, Übersetzung und bereits gespeicherte Mappings. [`Viewable2TableMapper`](https://github.com/claeis/ili2db/blob/master/src/ch/ehi/ili2db/mapping/Viewable2TableMapper.java) enthält Abbildungsstrategien für Klassen und Assoziationen. [`DbNames`](https://github.com/claeis/ili2db/blob/master/src/ch/ehi/ili2db/base/DbNames.java) dokumentiert zahlreiche technische Namen. Diese Quellen sind zu untersuchende Referenzen, keine Einladung, ungeprüft nur einige Konstanten zu kopieren.

`DbCompatibilityProfile` enthält:

- benannte Referenzversion und ausführbares Referenzartefakt;
- unterstützten Backend-/Dialektstand;
- aktivierte Abbildungsoptionen einschliesslich Defaults;
- Algorithmusversionen für Namen, Vererbung, Strukturen und Assoziationen;
- benötigte Metadatentabellen, Spalten und zulässige historische Varianten;
- Verhalten bei vorhandenen Schemen und nicht eindeutiger Profilermittlung;
- Transfer-/Modellbezüge, soweit sie die Abbildung beeinflussen.

Ein Profil kann mehrere nachweislich gleiche Versionen abdecken. Gleichheit muss durch Tests begründet werden. Ein einzelner unbenannter Modus „ili2db-kompatibel“ ist kein ausreichender Vertrag.

<a id="zu-erfassende-kompatibilitätsoberfläche"></a>
### Zu erfassende Kompatibilitätsoberfläche

| Bereich | Vertragliche Informationen |
|---|---|
| Fachtabellen | Namen, Qualifizierung, Zusammenlegung/Aufteilung von Klassen, Views soweit unterstützt |
| Fachspalten | Namen, Reihenfolge soweit vertraglich relevant, Typ, Genauigkeit, Nullable, Defaults |
| Identitäten | Technische IDs, OIDs, Typdiskriminatoren, Basket-/Datasetbezüge |
| Beziehungen | Fremdschlüssel, Join-/Assoziationstabellen, eingebettete Rollen, Struktureigentümer und Sequenz |
| Geometrie | Datenbanktyp, Dimension, SRID-/CRS-Zuordnung, Indizes und Mappingoptionen |
| Vererbung | Gewählte Strategien einschliesslich der bestehenden smart-Abbildungen |
| Metadaten | Modelltexte, Einstellungen, Namenszuordnungen, Abbildungs-/Vererbungsdaten, Spalten-/Tabelleneigenschaften und Übersetzungsdaten |
| Betrieb | Import-, Update-, Export-, Lösch- und Transaktionsverhalten je unterstütztem Werkzeugumfang |

Zu untersuchen sind insbesondere die `T_ILI2DB_*`-Tabellen, etwa `MODEL`, `SETTINGS`, `CLASSNAME`, `ATTRNAME`, `TRAFO`, `INHERITANCE`, `BASKET`, `DATASET`, `COLUMN_PROP`, `TABLE_PROP`, `META_ATTRS` und `NLS`, soweit das gewählte Profil sie verwendet. Auch technische Namen wie `T_Id`, `T_Ili_Tid`, `T_Type`, Basket-, Parent- und Sequenzspalten sind profilabhängig aufzunehmen. Diese Aufzählung ist keine Zusage, dass jede genannte Tabelle in jedem Backend und jeder alten Version vorhanden ist.

Historische Unterschiede in Spaltennamen und Metadatenformaten müssen als explizite Reader-Varianten modelliert werden. Der neue Code darf sie nicht aufgrund einer vermeintlich einheitlichen aktuellen Schreibweise überschreiben.

<a id="lesende-erfassung-bestehender-schemen"></a>
### Lesende Erfassung bestehender Schemen

`SchemaIntrospector.read(SchemaReadRequest request, DbReadSession session, CancellationToken token)` liefert ein unveränderliches `ExistingSchemaMetadata`. Es führt keine DDL- oder DML-Änderungen durch.

`ExistingSchemaMetadata` enthält fachliche und technische Objekte, gespeicherte ili2db-Zuordnungen, relevante DB-Eigenschaften und einen Herkunfts-/Lesestand. Vorhandene Metadaten sind nicht automatisch vollständig oder konsistent. `MetadataConsistencyChecker.check(ExistingSchemaMetadata metadata, DbCompatibilityProfile profile)` liefert konkrete Befunde und eindeutig benannte fehlende Informationen.

Ein `CompatibilityProfileDetector.detect(ExistingSchemaMetadata metadata, ProfileCatalog profiles)` liefert `Matched`, `Ambiguous` oder `Unknown`. Er darf im mehrdeutigen Fall kein beliebiges Profil als gesichert auswählen. Eine ausdrücklich vom Benutzer gewählte Profilannahme bleibt in der Provenienz sichtbar und wird soweit möglich gegen die tatsächlichen Metadaten geprüft.

<a id="planung"></a>
### Planung

`SchemaOverlays` ist der unveränderliche verbraucherspezifische Kontext für die DB-Abbildung. Er enthält ausschliesslich Informationen, die die Abbildung eines bereits gültigen Schemas beeinflussen. [`SemanticOverlays`](../contracts/language-semantics.md#fachliche-semanticoverlays) verändert dagegen die fachliche Analyse und muss vor der Erzeugung des `CompiledSchema` angewandt werden. Der DB-Planer darf mit `SchemaOverlays` keine neue Sprachbindung, veränderte Vererbungsregel oder unvalidierte Typänderung einschleusen. Beide Aggregate besitzen getrennte Typen und Fingerprints. CRS-/Übersetzungs-/Metadatenoptionen werden anhand ihrer tatsächlichen Wirkung einer dieser Grenzen zugeordnet; allein ihr Name entscheidet dies nicht.

`SchemaPlanner` besitzt:

```java
PlanningOutcome<SchemaPlan> plan(
    CompiledSchema schema,
    MappingOptions options,
    ExistingSchemaMetadata existing,
    SchemaOverlays overlays,
    DbCompatibilityProfile profile,
    CancellationToken cancellation);
```

**Vorbedingungen:** Vollständige, geordnete Eingaben; explizite Abbildungsoptionen; vorhandene Metadaten und Profilzuordnung sind konsistent oder besitzen ausdrücklich bearbeitbare Befunde. Ein leeres neues Schema wird durch einen eigenen validen `ExistingSchemaMetadata.empty(...)`-Wert dargestellt, nicht durch `null`.

**Nachbedingungen:** Der Plan enthält Objekt-/Tabellenabbildung, Attribut-/Spaltenabbildung, Assoziations-/Strukturregeln, technische Metadatenoperationen und ein geordnetes Ausführungsmodell. Er ist ohne schreibende DB-Verbindung prüfbar. Keine planungsinterne Optimierung darf historische bestehende Namen ersetzen.

`SchemaPlan` besitzt `mapping()`, `operations()`, `metadataChanges()`, `requiredCapabilities()`, `preconditions()` und `provenance()`. `preconditions` beschreibt überprüfbare Annahmen an den vorhandenen Datenbankzustand. Die Ausführung muss diese Annahmen unmittelbar vor Änderungen prüfen, damit ein inzwischen verändertes Schema nicht blind nach einem veralteten Plan bearbeitet wird.

<a id="namemapping-und-dialektgrenze"></a>
### NameMapping und Dialektgrenze

`NameMappingResolver.resolve(NameRequest request, ExistingNameMappings existing, NameAllocationContext context)` liefert `ResolvedSqlName` mit Herkunft `EXISTING` oder `ALLOCATED` und einer nachvollziehbaren Entscheidung. Ein vorhandenes Mapping wird zuerst geprüft und, sofern gültig, übernommen.

`NameAllocationContext` enthält Profil, Dialekt, reservierte Namen und bereits geplante Namen. Die Zuordnungsreihenfolge ist kanonisch. Ein automatischer Fallback, der nach einer Kollision zufällige Suffixe ergänzt, ist unzulässig, wenn das Profil ein anderes historisches Verfahren verlangt.

`DbDialect` kapselt Identifikatorlängen, Zeichen-/Bytezählung, Gross-/Kleinschreibung, Quoting, reservierte Wörter, SQL-/Geometrietypen, Sequenzen beziehungsweise ID-Erzeugung, DDL-Transaktionen und Batchfähigkeiten. Die Abbildungsstrategie bleibt im Planer. Ein Dialekt darf nicht heimlich neu entscheiden, welche Klassen gemeinsam in einer Tabelle liegen.

`SqlRenderer.render(SchemaOperation operation, DbDialect dialect)` erzeugt ausführbare SQL-Fragmente und gebundene Parameter dort, wo der Dialekt dies erlaubt. Identifikatoren werden über geprüfte Identifier-Typen gerendert. Benutzerwerte werden nicht über Stringverkettung in SQL eingefügt.

<a id="ausführung-und-leistung"></a>
### Ausführung und Leistung

`SchemaExecutor.apply(SchemaPlan plan, DbWriteSession session, ExecutionPolicy policy, CancellationToken token)` liefert `SchemaExecutionReport`. Er prüft Vorbedingungen, erfasst ausgeführte Schritte und unterscheidet vollständig übernommen, kontrolliert zurückgerollt und explizit teilweise angewendet. Das tatsächliche DDL-Transaktionsverhalten des Dialekts ist massgeblich.

`DataImportSession` arbeitet objekt-/batchweise mit einem unveränderlichen Transfer-/Mappingplan. Sie hält laufgebundene ID-Zuordnungen, Prepared Statements und Batchzustände. Nicht jede Attributkonvertierung darf einen einzelnen DB-Roundtrip verursachen. `BatchPolicy` begrenzt Objektanzahl und Speicherverbrauch; fachliche Fehler müssen noch einem Objekt oder einer eingrenzbaren Batchmenge zuordenbar bleiben.

`DataExportSession` liest die zum Profil passende gespeicherte Zuordnung und verwendet sie für Transfernamen und Objektbeziehungen. Ein Export darf nicht ausschliesslich aus Spaltennamen eine vermeintliche INTERLIS-Struktur rekonstruieren, wenn autoritative Mappingmetadaten vorhanden sind.

Ein erster PostgreSQL-/PostGIS-Adapter ist wegen des vorgesehenen Einsatzes naheliegend. Die gemeinsame API darf spätere Backends ermöglichen, muss deren Fähigkeiten aber ehrlich deklarieren. Es werden keine vermeintlich datenbankneutralen SQL-Details erfunden, die in der Praxis nur auf einem Backend funktionieren.

<a id="optionale-java-kompatibilität"></a>
## Optionale Java-Kompatibilität

Die explizite Benutzeranforderung erlaubt einen neuen inneren Aufbau. Deshalb ist eine neue Java-API unter `guru.interlis.*` der Standard. Falls vorhandene Bibliotheken vor ihrer Ablösung weiterverwendet werden, kann ein `compat-ili2c`-Adapter geprüft werden.

`LegacyTransferDescriptionAdapter.materialize(CompiledSchema schema, LegacyApiProfile profile)` müsste einen vollständigen konsistenten alten Objektgraphen erzeugen. Das Profil referenziert die passende Metamodellreferenz sowie den zugesagten API-/gegebenenfalls ABI-Umfang und die unterstützten Verbraucher. Dazu gehören korrekte Besitzer-/Containerbeziehungen, Vererbung, Typen, Referenzen, Reihenfolgen und alle vom konkreten Verbraucher benutzten Hilfsfunktionen. Ein paar gleich benannte Getter sind kein Drop-in-Nachweis. Die konkrete Adaptergrenze wird zusätzlich in [CLI, SDK und Kompatibilitätsadapter](../usage/cli.md) beschrieben.

Das Adaptermodul hängt auf die gepinnte alte Bibliothek. Der neue Kern darf diese Abhängigkeit nicht transitiv erhalten. Ein Klassenpfadkonflikt durch mehrere inkompatible Ausgaben derselben alten Packages muss durch Artefakttrennung oder einen expliziten Prozess-/Classloadervertrag vermieden werden. Eine tatsächliche binärkompatible Ersatzbibliothek wäre ein eigener Liefergegenstand mit API-/ABI-Tests.

Bestehende Verbraucher können zunächst als Referenzprobe dienen, zum Beispiel um einen projizierten Modellgraphen gegen den alten Validator zu prüfen. Daraus folgt noch keine Zusage, alle bisherigen Implementierungsdetails dauerhaft nachzubauen.

<a id="durchgängige-szenarien-und-abnahme"></a>
## Durchgängige Szenarien und Abnahme

<a id="übersetztes-modell-mit-vererbung"></a>
### Übersetztes Modell mit Vererbung

Ein Modell besitzt eine Basisklasse, eine abgeleitete Klasse mit Überschreibung und ein Übersetzungsmodell. Der Compiler erstellt kanonische Identitäten und die Übersetzungsbeziehung. Der Transferplan verwendet die gewählten Transfernamen, behält aber die ursprünglichen Symbolbezüge und Slotpositionen. Der Datenbankplan folgt bestehenden gespeicherten Namenszuordnungen.

**Prüfungen:** semantisches Golden; Transfer mit altem Reader lesbar; keine doppelte Spalte für ein korrekt überschriebenes Attribut; vorhandene Tabellen-/Spaltennamen bleiben identisch. Ein Fehler in einer Übersetzungsregel wird am Modell geprüft, eine fehlende Fähigkeit eines Exporters separat.

<a id="basketabhängige-domänenkonkretisierung"></a>
### Basketabhängige Domänenkonkretisierung

Das Schema enthält eine rechtmässige spätere Domänenbindung. Es kompiliert erfolgreich. Zwei Baskets liefern verschiedene erlaubte Konkretisierungen. Der Validator erzeugt zwei passende `ResolvedBasketContext`-Werte. Geometrie-/Typcaches beziehen den jeweiligen Kontext ein.

**Prüfungen:** keine Schemaänderung zwischen Baskets; falsche Konkretisierung erscheint als transfer-/kontextbezogener Fehler; ein Cachetreffer des ersten Baskets darf die zweite Prüfung nicht verfälschen.

<a id="vorhandene-datenbank-mit-namenskollision"></a>
### Vorhandene Datenbank mit Namenskollision

Das alte Werkzeug hat zwei lange Namen auf gespeicherte unterschiedliche SQL-Namen abgebildet. Der neue Planer liest diese Zuordnung. Auch wenn sein zukünftiger Standardalgorithmus andere Namen erzeugen würde, verwendet das aktive Legacy-Profil die gespeicherten Namen.

**Prüfungen:** Alt erzeugt → Neu importiert/exportiert/aktualisiert; Neu erzeugt unter demselben Profil → Alt führt die entsprechenden Operationen aus. Manipulierte oder widersprüchliche Zuordnung ergibt einen konkreten Befund und keine stillschweigende Neuerzeugung.

<a id="pflichtprüfungen-auf-klassenebene"></a>
### Pflichtprüfungen auf Klassenebene

| Test | Abzusichernder Vertrag |
|---|---|
| `TransferPlannerContractTest` | Vollständige Deskriptoren, feste Reihenfolge, Übersetzungsnamen, Snapshotbindung |
| `TransferEventProtocolTest` | Ereignisordnung, Besitzsemantik, Close/Abbruch, kein komplettes File im Speicher |
| `IomAdapterRoundTripTest` | Werte, Referenzen, Strukturen und Geometrie verlustfrei innerhalb der unterstützten Fähigkeiten |
| `ConsumerCapabilityTest` | Kein stilles Weglassen; `Unsupported` verändert Compilergültigkeit nicht |
| `ValidationCoverageTest` | Lokaler Modus behauptet keine globale Validität |
| `ExpressionEvaluationParityTest` | Getypte Ausdrücke, exakte Zahlen und definierte Undefiniertfälle |
| `GeometryPolicyCacheTest` | Cache berücksichtigt Inhalt, Präzision, Bögen und Laufzeitkontext |
| `ValidationResourceBudgetTest` | Begrenzte Indizes/Diagnosen, kontrollierter Spill oder Abbruch |
| `ExistingSchemaMetadataTest` | Historische Spaltenvarianten, Inkonsistenzen, Profilmehrdeutigkeit |
| `NameMappingResolverTest` | Bestehende Zuordnung zuerst; deterministische historische Kollisionen |
| `SchemaPlannerGoldenTest` | Abbildungsstrategien und technische Metadaten unter gepinnten Profilen |
| `SchemaExecutorFailureTest` | Vorbedingungswechsel, Abbruch, Rollback und expliziter Teilzustand |
| `LegacyDatabaseInteroperabilityTest` | Beide Richtungen mit altem Werkzeug, nicht nur eigener Roundtrip |

<a id="anforderungen-an-die-heutige-compilersicht"></a>
## Anforderungen an die heutige Compilersicht

Die heutigen Compilerverträge müssen die Metamodellinformationen und Grenzen dieses Dokuments berücksichtigen. Die Tests zeigen, dass mindestens eine kompakte Projektion aus dem geprüften Schema erzeugt werden kann und ein begrenzter Verbraucher fehlende Fähigkeiten korrekt meldet. Vollständiges XTF-I/O, ausführbare Geometrievalidierung und JDBC-Schemaerzeugung gehören zu den geplanten Verbrauchern.

Die Verbraucher werden als eigene Implementierungen geliefert. Die Compilerarbeit bewahrt ihre Eingabeverträge und fachlichen Modellinformationen.

Die [Kompatibilitätsprüfungen](../development/testing.md) unterscheiden Referenzverhalten und tatsächlich nachgewiesene Unterstützung. Die historische Quelle wird anhand des [Quellenregisters](../contracts/norm-decisions.md) gepinnt; Aussagen über erreichte Kompatibilität müssen auf tatsächlich ausgeführten Tests beruhen.
