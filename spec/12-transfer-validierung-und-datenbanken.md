# 12 – Überbau: Transfer, Validierung und Datenbankkompatibilität

## 1. Geltung und Entwicklungsgrenze

Der Compiler ist der erste Liefergegenstand eines langfristig vollständigen Java-INTERLIS-Stacks. Dieses Dokument hält die späteren Verbraucher so konkret fest, dass der Compiler ihre Anforderungen von Beginn an berücksichtigt. Es ist keine Anweisung, bereits in P0–P3 einen Validator oder Datenbankimporter zu implementieren.

Die Verantwortung wird entlang fachlicher Produkte geschnitten:

```text
CompiledSchema + explizite Verbraucheroptionen
  → TransferPlan
  → ValidationPlan
  → SchemaPlan
```

Ein `CompiledSchema` bleibt ein unveränderliches, fachlich geprüftes Modell. Projektionen erzeugen weitere unveränderliche Pläne. Laufzeitdaten, Caches, Objektidentitäten, Datenbankverbindungen und Zwischenergebnisse einer Datenvalidierung gehören in laufgebundene Kontexte.

Die späteren Module heissen vorläufig `interlis-transfer`, `interlis-validation` und `interlis-db`. Sie werden erst angelegt, wenn reale implementierte Funktionalität vorliegt. Der Compiler hängt niemals von diesen Modulen ab. Die öffentlichen Plan- und Laufzeitverträge dieses Dokuments sind für P4/P5 vorgesehen; P1/P2 benötigen passende Metamodell-Vertragstests und mindestens eine kleine exemplarische Projektion, keine leeren Gerüste aller künftigen Klassen.

## 2. Anforderungen und Abnahme

| ID | Normative Anforderung | Phase | Abnahme |
|---|---|---|---|
| STACK-001 | Der Compiler MUSS alle für Transfer, Validierung und DB-Abbildung relevanten semantischen Informationen typisiert und eindeutig bereitstellen. | P2 | Semantische Goldens gegen ausgewählte Altmodelle einschliesslich Reihenfolge, Übersetzung, Views und Rollen. |
| STACK-002 | Ein gültiges Sprachkonstrukt DARF wegen einer fehlenden Verbraucherfähigkeit nicht durch den Compiler abgelehnt werden. | P1 | Gültiges Modell ergibt Schema; absichtlich begrenzter Projektor liefert `Unsupported`. |
| STACK-003 | Pläne MÜSSEN unveränderlich, ihren Eingaben zugeordnet und ohne verborgene Änderung des CompiledSchema erzeugt werden. | P1/P4 | Parallel erzeugte Projektionen beeinflussen sich nicht; veränderte Optionen ergeben passende Fingerprints. |
| STACK-004 | Sprachversion, Transferprofil und DB-Kompatibilitätsprofil MÜSSEN getrennte Identitäten besitzen. | P1 | API und Cachetests erlauben keine Ableitung ausschliesslich aus `ILI23`/`ILI24`. |
| STACK-005 | Kanonische Symbolidentität, dargestellter Name und tatsächlich verwendeter Transfername MÜSSEN getrennt bleiben. | P2 | Übersetzungsmodell mit abweichenden Namen wird korrekt projiziert. |
| STACK-006 | Effektive Attribute und Rollen MÜSSEN ihre fachlich definierte Reihenfolge, Überschreibungen und Herkunft behalten. | P2 | Golden für Vererbung, überschriebenes Attribut und eingebettete Assoziationsrolle. |
| STACK-007 | Verbraucher MÜSSEN ihre Fähigkeiten explizit ausweisen und nicht unterstützte Merkmale vor unbeabsichtigtem Datenverlust erkennen. | P4 | Planung meldet alle relevanten fehlenden Fähigkeiten; kein stilles Weglassen. |
| STACK-008 | Ein Transferreader MUSS mindestens auf Objektebene streamen und DARF nicht den ganzen Transfer zwingend materialisieren. | P4 | Grosser Transfer bei festem Arbeitsspeicherbudget; dokumentierte Obergrenze für ein einzelnes Objekt. |
| STACK-009 | IOM-/iox-Kompatibilität MUSS getrennt als Datenmodell-, Ereignis- und gegebenenfalls Java-API-Vertrag behandelt werden. | P4 | Adaptervertrag mit verschachtelten Strukturen, Referenzen, Reihenfolgen und undefinierten Werten. |
| STACK-010 | Datenvalidierung MUSS lokale und transferweite Regeln unterscheiden; ein lokaler Modus darf keine vollständige Validität behaupten. | P4 | Bericht mit geprüften und ausgelassenen Regelklassen. |
| STACK-011 | Ausdrucksauswertung MUSS die getypte Compilersemantik und exakte INTERLIS-Zahlendarstellung verwenden. | P4 | Ausdrücke werden nicht erneut aus Rohtext interpretiert; exakte Grenzfalltests. |
| STACK-012 | Geometrieprüfungen MÜSSEN wiederholte Arbeit vermeiden, fachlich relevante Rundungs-/Bogenregeln aber erhalten. | P4 | Geometrieadapter- und Cachetests mit Bögen, Präzision und geänderten Optionen. |
| STACK-013 | Validierung MUSS ein explizites Ressourcenbudget unterstützen; für die vorgesehene grosse Betriebsvariante ist ein Gesamtbudget von höchstens 64 GB planbar. | P4 | Lasttest berücksichtigt Heap, Native Memory, Caches und Puffer; Überschreitung wird kontrolliert behandelt. |
| STACK-014 | Laufzeitabhängige GENERIC-/DEFERRED-Konkretisierung MUSS als gültiger Modellzustand erhalten und über passenden Kontext geprüft werden. | P2/P4 | Gültige abstrakte Domäne kompiliert; fehlende oder ungültige Transferkonkretisierung wird separat diagnostiziert. |
| STACK-015 | Konfigurations- und CRS-/Übersetzungs-Overlays MÜSSEN explizit, unveränderlich und Teil des Plan-Fingerprints sein. | P2/P4 | Verschiedene Overlays teilen keine inkorrekten Pläne oder Caches. |
| DB-001 | Bestehende ili2db-Schemata und ihre Metadaten MÜSSEN unter ausdrücklich benannten Referenzprofilen weiterverwendbar sein. | P5 | Bidirektionale Interoperabilitätsmatrix mit gepinnten Altversionen. |
| DB-002 | Bereits gespeicherte Namens- und Abbildungszuordnungen MÜSSEN bei Wiederverwendung massgeblich sein. | P5 | Bestehende gekürzte oder kollidierte Namen bleiben erhalten. |
| DB-003 | SQL-Namen, technische Tabellen/Spalten, Constraints, Metadaten und Abbildungsstrategien MÜSSEN als getrennt prüfbare Verträge behandelt werden. | P5 | Strukturelle Schema-Goldens und Metadatenvergleiche. |
| DB-004 | Planung und Ausführung MÜSSEN getrennt sein; Schemaänderungen dürfen nicht als Nebenwirkung einer Modellanalyse auftreten. | P5 | `SchemaPlanner` benötigt keine schreibende Verbindung und erzeugt reviewbaren Plan. |
| DB-005 | Datenbankdialekte MÜSSEN ihre tatsächlichen Unterschiede kapseln. Ein erster PostgreSQL-/PostGIS-Umfang darf andere geplante Profile nicht fälschlich als unterstützt melden. | P5 | Capability- und Dialekttests. |
| DB-006 | Ein Import-/Export-Roundtrip ausschliesslich mit dem neuen Werkzeug DARF nicht als Nachweis der ili2db-Kompatibilität gelten. | P5 | Alt→Neu und Neu→Alt mit realen Operationen. |
| DB-007 | Fehlende oder widersprüchliche vorhandene Metadaten MÜSSEN diagnostiziert werden; stilles Neuerfinden ihrer Bedeutung ist unzulässig. | P5 | Absichtlich beschädigte Mappingmetadaten werden erkannt. |
| DB-008 | Transaktions-, Wiederaufnahme- und Abbruchverhalten MÜSSEN je Operation und Dialekt explizit sein. | P5 | Abbruch vor/nach Batch und injizierter SQL-Fehler ohne ungemeldeten Teilzustand. |
| DB-009 | JDBC-Batching und Datenbankzugriffe SOLLEN Roundtrips reduzieren; geänderte Fehlerlokalisierung oder Objektzuordnung müssen dabei kontrolliert bleiben. | P5 | Gemessene Statement-/Roundtripzahlen und Fehlerzuordnung im Batch. |
| DB-010 | Neue fachliche Modelloptimierungen DÜRFEN historische Abbildungsprofile nicht stillschweigend verändern. | P5 | Dieselben Eingaben unter altem Profil ergeben dieselbe vertragliche Abbildung. |

## 3. Gemeinsame Planverträge

### 3.1 Identitäten und Unveränderlichkeit

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

### 3.2 Planungsergebnis

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

## 4. TransferPlan und Datentransfer

### 4.1 Eingaben und Planner

`TransferOptions` enthält das explizite `TransferProfileId`, Namens-/Übersetzungswahl, relevante Formatoptionen und Ressourcenlimits. Die konkrete Liste kompatibler Optionen wird gegen die bisherigen Werkzeuge aufgenommen. Eine unbestimmte Option `legacy=true` genügt nicht.

`TransferMetadata` enthält benötigte Originalmodellinformationen und provenance-bezogene Daten, die nicht zwingend dauerhaft in jedem semantischen Objekt gespeichert werden müssen. Die Quellenhaltung wird mit [Repository-Auflösung](08-modellaufloesung-und-repositories.md) abgestimmt. Ein späterer Writer darf kein Modell aus dem Netz nachladen, nur um fehlende ursprüngliche Modelltexte zu rekonstruieren.

`TransferPlanner` besitzt:

```java
PlanningOutcome<TransferPlan> plan(
    CompiledSchema schema,
    TransferOptions options,
    TransferMetadata metadata,
    CancellationToken cancellation);
```

**Vorbedingungen:** Das Schema wurde durch das Compilation-Gate erzeugt; Optionen und Metadaten entsprechen ihren Schemaversionen; Quell- und Symbolbezüge passen zur Schemaherkunft.

**Nachbedingungen:** `Ready` enthält eine kompakte, selbstkonsistente Beschreibung aller für den gewählten Transfer nötigen Topics, Klassen, Strukturen, Attribute, Rollen und Namen. Die Reihenfolge ist explizit. Symbolbindung und Typinformationen werden aus dem geprüften Schema übernommen und nicht aus Text erneut geraten.

**Fehler:** Fehlende Verbraucherfähigkeit ergibt `Unsupported`. Unzulässige Kombinationen von Transferoptionen ergeben `Rejected`. Unbekannte interne Graphreferenzen ergeben `Failed`. Eine rechtmässig zur Laufzeit zu konkretisierende Domäne ist für sich kein Planungsfehler.

### 4.2 Planstrukturen

| Typ | Wesentliche Informationen |
|---|---|
| `TransferPlan` | Provenienz, Profil, Modelle/Topics, Tagindex, Objektdeskriptoren, benötigte Laufzeitbindungen |
| `TopicDescriptor` | Kanonische Topicidentität, Transfername, zulässige Objekttypen, Basketregeln |
| `ObjectDescriptor` | Kanonische Klasse/Struktur/Assoziation, Transfername/Tag, Basistyp, effektive Slots, Objektidentitätsregeln |
| `AttributeSlot` | Feste Position, kanonisches Attribut, Transfername, Typdeskriptor, Kardinalität, Herkunft/Überschreibung |
| `RoleSlot` | Rolle, Zieltyp, Kardinalität, Einbettungs-/Assoziationsbezug, Transferdarstellung |
| `RuntimeTypeDescriptor` | Aufgelöste statische Typinformation plus gegebenenfalls rechtmässige Laufzeitbindung |
| `BasketBindingRequirements` | Erwartete generische beziehungsweise aufgeschobene Konkretisierungen |

`TransferPlan.findObjectByTag(String tag)` liefert ein explizites Treffer-/Nichttrefferergebnis. `TransferPlan.object(SymbolRef ref)` prüft sowohl Snapshotzugehörigkeit als auch die enthaltene Symbol-ID. Eine nackte `SymbolId` genügt an dieser öffentlichen Grenze nicht. Ein Übersetzungsname darf nicht als kanonischer Schlüssel intern die ursprüngliche Identität ersetzen.

`ObjectDescriptor.effectiveSlots()` liefert die fachlich richtige, unveränderliche Reihenfolge. Die Position eines überschriebenen Attributes darf nicht allein durch das Auftauchen in einer neuen lokalen Liste verändert werden. Eine Assoziation mit eigenen Attributen oder höherer Stelligkeit muss als eigenständiger semantischer Sachverhalt erhalten bleiben.

### 4.3 Transferereignisse und Objektmodell

Die initiale Streaming-API orientiert sich an einem Ereignisstrom mit `StartTransfer`, `StartBasket`, `ObjectEvent`, `EndBasket` und `EndTransfer`. Die genaue historische Ereignisreihenfolge und Zusatzinformationen werden als iox-Kompatibilitätsvertrag aufgenommen. Ein `TransferReader` besitzt konzeptionell:

```java
Optional<TransferEvent> readNext(CancellationToken cancellation);
void close();
```

I/O-Ausnahmen werden im konkreten I/O-Modul definiert und in ein strukturiertes Laufzeitergebnis überführt. Der neutrale Compilerkern ist davon unabhängig. Der Reader darf das nach aussen gegebene Ereignis nicht beim nächsten `readNext` heimlich wiederverwenden und verändern. Für hohe Leistung kann später eine ausdrücklich ausgeliehene Cursor-API hinzukommen; sie muss eine andere, dokumentierte Besitzsemantik besitzen.

Ein `TransferObject` hält Identität, Typdeskriptorbezug und Attribut-/Rollenwerte eines Objekts. Ein einzelnes Objekt darf verschachtelte Strukturen besitzen. Das ist keine Aufforderung, einen ganzen Basket im Speicher zu halten. Ein übergrosses Einzelobjekt unterliegt einem eigenen Budget und einer eindeutigen Fehlerbehandlung.

Das Datenmodell unterscheidet mindestens:

- nicht vorhandenen Slot beziehungsweise Wert;
- ausdrücklich undefinierten Wert, soweit das Format dies unterscheidet;
- Text, Booleschen Wert, exakte Zahl und Aufzählungswert;
- Referenz mit Identitäts- und gegebenenfalls Basketbezug;
- verschachtelte Struktur;
- geordnete beziehungsweise ungeordnete Mehrfachwerte gemäss Modell;
- Geometrie mit ausreichender Information über Koordinaten, Bögen und Genauigkeit.

Eine Java-`null`-Sammlung ist kein Ersatz für diese Unterscheidungen. Ein allgemeines `Map<String,Object>` darf an einer Adaptergrenze hilfreich sein, aber nicht der einzige typisierte Vertrag des neuen Stacks werden.

`BasketRuntimeContext` verbindet Basketidentität, Topic, Laufzeitdomänen und Transferprofil. Die Prüfung von GENERIC-/DEFERRED-Bindungen erzeugt einen unveränderlichen `ResolvedBasketContext`; sie verändert das globale Schema nicht. Unterschiedliche Baskets dürfen unterschiedliche rechtmässige Konkretisierungen besitzen.

### 4.4 IOM-/iox-Adapter

Ein zukünftiger `IomAdapter` erhält getrennte Methoden:

```java
TransferObject fromIom(IomObjectView input, TransferPlan plan,
                       BasketRuntimeContext basket);
IomObjectView toIom(TransferObject input, TransferPlan plan);
```

Die konkreten Legacy-IOM-Klassen werden nur in einem Adaptermodul referenziert. `IomObjectView` ist hier ein abstrakter Grenzvertrag; die Implementierung kann einen alten `IomObject` oder eine eigene Sicht kapseln. Die API darf nicht behaupten, dass identische Methodennamen bereits identische Semantik bedeuten.

Abnahmefälle umfassen Referenzen, OID-/Basketbezug, verschachtelte Strukturen, Reihenfolgen, Bögen, undefinierte Werte und übersetzte Namen. Daten, die nicht verlustfrei darstellbar sind, erzeugen eine explizite Diagnose. Eine Bogenlinearisierung ist eine deklarierte Transformation mit Optionen und messbaren Folgen, keine allgemeine beiläufige Konvertierung.

## 5. ValidationPlan und Validator

### 5.1 Semantik und Laufzeit trennen

Der bisherige [`iox-ili Validator`](https://github.com/claeis/iox-ili/blob/master/src/main/java/ch/interlis/iox_j/validator/Validator.java) verwendet zahlreiche Metamodellkonzepte; `ilivalidator` bildet das Werkzeug um diese Fähigkeiten. Der neue Stack soll die getypte semantische Grundlage des Compilers direkt weiterverwenden.

`ValidationPlanner.plan(CompiledSchema schema, ValidationOptions options, FunctionCatalog functions, GeometryCapabilities geometry, CancellationToken token)` liefert `PlanningOutcome<ValidationPlan>`.

`ValidationPlan` enthält lokale Objektregeln, notwendige Referenzprüfungen, Basketregeln, globale beziehungsweise transferweite Regeln und explizite Anforderungen an Indizes und externe Funktionen. Keine Regel wird bei der Planung allein aus ihrem ursprünglichen Text erneut geparst. Eine getypte Ausdrucksprojektion erhält Typen, Pfadschritte, Einheiten, exakte Literale, Unbekannt-/Undefiniertverhalten und Herkunft.

`FunctionCatalog` beschreibt verfügbare Laufzeitimplementierungen über deklarierte Signaturen und versionierte Implementierungsidentitäten. Die Existenz einer fachlich gültigen externen Funktionsdeklaration bedeutet nicht, dass jeder Host diese Funktion ausführen kann. Ein Validator muss vor Ausführung melden können, welche benötigte Implementierung fehlt.

### 5.2 Regelklassen und Coverage

| Regelklasse | Typische Aufgabe | Ressourcenstrategie |
|---|---|---|
| Lokal | Einzelwert, Kardinalität, lokal auswertbarer Constraint | Objektweise, zeitnah freigeben |
| Referenziell | Existenz und Typ von Referenzzielen | Identitätsindex und gegebenenfalls spätere Auflösung |
| Basketbezogen | Abhängigkeiten und Kontext eines Baskets | Begrenzter Basketzustand oder expliziter Spill |
| Transferweit | Eindeutigkeit, Mengen- und globale Regeln | Indizes, Sortierung, Aggregation oder zweite Phase |
| Geometrisch | Form-, Topologie-, Flächen- und Beziehungsprüfungen | Geeignete Geometrieoperationen und räumliche Indizes |

Diese Tabelle legt keine ungeprüften Einzelregeln der INTERLIS-Norm fest. Der Agent ordnet jede tatsächlich implementierte Normregel anhand ihrer benötigten Information zu und hinterlegt eine Referenz sowie Tests.

`ValidationMode` erhält mindestens `FULL` und `LOCAL_ONLY`. `ValidationCoverage` zählt geprüfte Regeln und führt ausgelassene Regelklassen samt Grund auf. Ein fehlerfreier `LOCAL_ONLY`-Lauf ergibt `PASSED_CHECKED_SCOPE`, nicht `VALID` für den gesamten Transfer.

### 5.3 Laufzeitklassen und Methoden

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

### 5.4 Geometrie und Speicherbudget

Die Schnittstelle `GeometryOperations` kapselt die konkret gewählte Bibliothek. Methoden werden aus echten benötigten Operationen abgeleitet, zum Beispiel `validate`, `relate`, `measure` und eine indexfähige Hüllgeometrie. Der Java-Compiler muss diese Bibliothek nicht laden, um eine Geometriedomäne zu typisieren.

`GeometryPolicy` enthält Präzisionsmodell, Toleranzen, Umgang mit Bögen und relevante Prüfoptionen. `GeometryCacheKey` umfasst Objekt-/Attributidentität beziehungsweise unveränderlichen Inhaltsfingerprint, diese Policy und die Operation. Ein Cache allein nach Objekt-ID ist unzulässig, wenn Geometrie oder Prüfoptionen veränderbar sind.

Teure vorbereitete Geometrien oder räumliche Indizes dürfen wiederverwendet werden, wenn die jeweilige Bibliothek dies für unveränderte Eingaben erlaubt. Konvertierung, Normalisierung und Validierung werden nicht für jede identische Teilprüfung blind wiederholt. Jede Optimierung muss Ergebnisgleichheit einschliesslich Fehlerfällen nachweisen.

`ResourceBudget` enthält mindestens Gesamtziel, maximalen Heapanteil, Cachegrenzen, temporären Speicherplatz und Grenzen einzelner Objekte beziehungsweise Geometrien. Ein Betriebsprofil mit höchstens **64 GB Gesamt-RAM** ist ein explizites Ziel für grosse Validierungen, keine bereits erreichte Leistungszusage. Die API verwendet Bytewerte; bis zu einer ausdrücklich anderen Festlegung gilt konservativ `64_000_000_000` Byte. GB und GiB dürfen nicht unbemerkt gleichgesetzt werden. Der Heap darf nicht einfach auf das gesamte Budget gesetzt werden, wenn native Speicheranteile, Threads, Direct Buffers oder weitere Prozesse ebenfalls Platz benötigen. Der konkrete Host reserviert eine gemessene Sicherheitsmarge und stoppt kontrolliert vor einer absehbaren Überschreitung; `OutOfMemoryError` ist keine reguläre Budgetstrategie.

Transferweite Indizes benötigen eine `IndexStore`-Abstraktion mit kleinen In-Memory- und später bedarfsgerecht ausgelagerten Implementierungen. Die Spezifikation schreibt keinen verteilten Cluster vor. Eine lokale, nachvollziehbare Spill-Strategie wird erst anhand realer Datenmengen gewählt und muss Datentypen, Ordnung und Identitäten verlustfrei erhalten.

## 6. Datenbankabbildung und bestehende ili2db-Datenbanken

### 6.1 Referenzverhalten explizit beschreiben

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

### 6.2 Zu erfassende Kompatibilitätsoberfläche

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

### 6.3 Lesende Erfassung bestehender Schemen

`SchemaIntrospector.read(SchemaReadRequest request, DbReadSession session, CancellationToken token)` liefert ein unveränderliches `ExistingSchemaMetadata`. Es führt keine DDL- oder DML-Änderungen durch.

`ExistingSchemaMetadata` enthält fachliche und technische Objekte, gespeicherte ili2db-Zuordnungen, relevante DB-Eigenschaften und einen Herkunfts-/Lesestand. Vorhandene Metadaten sind nicht automatisch vollständig oder konsistent. `MetadataConsistencyChecker.check(ExistingSchemaMetadata metadata, DbCompatibilityProfile profile)` liefert konkrete Befunde und eindeutig benannte fehlende Informationen.

Ein `CompatibilityProfileDetector.detect(ExistingSchemaMetadata metadata, ProfileCatalog profiles)` liefert `Matched`, `Ambiguous` oder `Unknown`. Er darf im mehrdeutigen Fall kein beliebiges Profil als gesichert auswählen. Eine ausdrücklich vom Benutzer gewählte Profilannahme bleibt in der Provenienz sichtbar und wird soweit möglich gegen die tatsächlichen Metadaten geprüft.

### 6.4 Planung

`SchemaOverlays` ist der unveränderliche verbraucherspezifische Kontext für die DB-Abbildung. Er enthält ausschliesslich Informationen, die die Abbildung eines bereits gültigen Schemas beeinflussen. `SemanticOverlays` aus Kapitel 06 verändert dagegen die fachliche Analyse und muss vor der Erzeugung des `CompiledSchema` angewandt werden. Der DB-Planer darf mit `SchemaOverlays` keine neue Sprachbindung, veränderte Vererbungsregel oder unvalidierte Typänderung einschleusen. Beide Aggregate besitzen getrennte Typen und Fingerprints. CRS-/Übersetzungs-/Metadatenoptionen werden anhand ihrer tatsächlichen Wirkung einer dieser Grenzen zugeordnet; allein ihr Name entscheidet dies nicht.

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

### 6.5 NameMapping und Dialektgrenze

`NameMappingResolver.resolve(NameRequest request, ExistingNameMappings existing, NameAllocationContext context)` liefert `ResolvedSqlName` mit Herkunft `EXISTING` oder `ALLOCATED` und einer nachvollziehbaren Entscheidung. Ein vorhandenes Mapping wird zuerst geprüft und, sofern gültig, übernommen.

`NameAllocationContext` enthält Profil, Dialekt, reservierte Namen und bereits geplante Namen. Die Zuordnungsreihenfolge ist kanonisch. Ein automatischer Fallback, der nach einer Kollision zufällige Suffixe ergänzt, ist unzulässig, wenn das Profil ein anderes historisches Verfahren verlangt.

`DbDialect` kapselt Identifikatorlängen, Zeichen-/Bytezählung, Gross-/Kleinschreibung, Quoting, reservierte Wörter, SQL-/Geometrietypen, Sequenzen beziehungsweise ID-Erzeugung, DDL-Transaktionen und Batchfähigkeiten. Die Abbildungsstrategie bleibt im Planer. Ein Dialekt darf nicht heimlich neu entscheiden, welche Klassen gemeinsam in einer Tabelle liegen.

`SqlRenderer.render(SchemaOperation operation, DbDialect dialect)` erzeugt ausführbare SQL-Fragmente und gebundene Parameter dort, wo der Dialekt dies erlaubt. Identifikatoren werden über geprüfte Identifier-Typen gerendert. Benutzerwerte werden nicht über Stringverkettung in SQL eingefügt.

### 6.6 Ausführung und Leistung

`SchemaExecutor.apply(SchemaPlan plan, DbWriteSession session, ExecutionPolicy policy, CancellationToken token)` liefert `SchemaExecutionReport`. Er prüft Vorbedingungen, erfasst ausgeführte Schritte und unterscheidet vollständig übernommen, kontrolliert zurückgerollt und explizit teilweise angewendet. Das tatsächliche DDL-Transaktionsverhalten des Dialekts ist massgeblich.

`DataImportSession` arbeitet objekt-/batchweise mit einem unveränderlichen Transfer-/Mappingplan. Sie hält laufgebundene ID-Zuordnungen, Prepared Statements und Batchzustände. Nicht jede Attributkonvertierung darf einen einzelnen DB-Roundtrip verursachen. `BatchPolicy` begrenzt Objektanzahl und Speicherverbrauch; fachliche Fehler müssen noch einem Objekt oder einer eingrenzbaren Batchmenge zuordenbar bleiben.

`DataExportSession` liest die zum Profil passende gespeicherte Zuordnung und verwendet sie für Transfernamen und Objektbeziehungen. Ein Export darf nicht ausschliesslich aus Spaltennamen eine vermeintliche INTERLIS-Struktur rekonstruieren, wenn autoritative Mappingmetadaten vorhanden sind.

Ein erster PostgreSQL-/PostGIS-Adapter ist wegen des vorgesehenen Einsatzes naheliegend. Die gemeinsame API darf spätere Backends ermöglichen, muss deren Fähigkeiten aber ehrlich deklarieren. Es werden keine vermeintlich datenbankneutralen SQL-Details erfunden, die in der Praxis nur auf einem Backend funktionieren.

## 7. Optionale Java-Kompatibilität

Die explizite Benutzeranforderung erlaubt einen neuen inneren Aufbau. Deshalb ist eine neue Java-API unter `guru.interlis.*` der Standard. Falls vorhandene Bibliotheken vor ihrer Ablösung weiterverwendet werden, kann ein `compat-ili2c`-Adapter geprüft werden.

`LegacyTransferDescriptionAdapter.materialize(CompiledSchema schema, LegacyApiProfile profile)` müsste einen vollständigen konsistenten alten Objektgraphen erzeugen. Das Profil referenziert die passende Metamodellreferenz sowie den zugesagten API-/gegebenenfalls ABI-Umfang und die unterstützten Verbraucher. Dazu gehören korrekte Besitzer-/Containerbeziehungen, Vererbung, Typen, Referenzen, Reihenfolgen und alle vom konkreten Verbraucher benutzten Hilfsfunktionen. Ein paar gleich benannte Getter sind kein Drop-in-Nachweis. Die konkrete Adaptergrenze wird zusätzlich in [CLI, SDK und Kompatibilitätsadapter](18-cli-sdk-und-kompatibilitaetsadapter.md) beschrieben.

Das Adaptermodul hängt auf die gepinnte alte Bibliothek. Der neue Kern darf diese Abhängigkeit nicht transitiv erhalten. Ein Klassenpfadkonflikt durch mehrere inkompatible Ausgaben derselben alten Packages muss durch Artefakttrennung oder einen expliziten Prozess-/Classloadervertrag vermieden werden. Eine tatsächliche binärkompatible Ersatzbibliothek wäre ein eigener Liefergegenstand mit API-/ABI-Tests.

Bestehende Verbraucher können zunächst als Referenzprobe dienen, zum Beispiel um einen projizierten Modellgraphen gegen den alten Validator zu prüfen. Daraus folgt noch keine Zusage, alle bisherigen Implementierungsdetails dauerhaft nachzubauen.

## 8. Durchgängige Szenarien und Abnahme

### 8.1 Übersetztes Modell mit Vererbung

Ein Modell besitzt eine Basisklasse, eine abgeleitete Klasse mit Überschreibung und ein Übersetzungsmodell. Der Compiler erstellt kanonische Identitäten und die Übersetzungsbeziehung. Der Transferplan verwendet die gewählten Transfernamen, behält aber die ursprünglichen Symbolbezüge und Slotpositionen. Der Datenbankplan folgt bestehenden gespeicherten Namenszuordnungen.

**Prüfungen:** semantisches Golden; Transfer mit altem Reader lesbar; keine doppelte Spalte für ein korrekt überschriebenes Attribut; vorhandene Tabellen-/Spaltennamen bleiben identisch. Ein Fehler in einer Übersetzungsregel wird am Modell geprüft, eine fehlende Fähigkeit eines Exporters separat.

### 8.2 Basketabhängige Domänenkonkretisierung

Das Schema enthält eine rechtmässige spätere Domänenbindung. Es kompiliert erfolgreich. Zwei Baskets liefern verschiedene erlaubte Konkretisierungen. Der Validator erzeugt zwei passende `ResolvedBasketContext`-Werte. Geometrie-/Typcaches beziehen den jeweiligen Kontext ein.

**Prüfungen:** keine Schemaänderung zwischen Baskets; falsche Konkretisierung erscheint als transfer-/kontextbezogener Fehler; ein Cachetreffer des ersten Baskets darf die zweite Prüfung nicht verfälschen.

### 8.3 Vorhandene Datenbank mit Namenskollision

Das alte Werkzeug hat zwei lange Namen auf gespeicherte unterschiedliche SQL-Namen abgebildet. Der neue Planer liest diese Zuordnung. Auch wenn sein zukünftiger Standardalgorithmus andere Namen erzeugen würde, verwendet das aktive Legacy-Profil die gespeicherten Namen.

**Prüfungen:** Alt erzeugt → Neu importiert/exportiert/aktualisiert; Neu erzeugt unter demselben Profil → Alt führt die entsprechenden Operationen aus. Manipulierte oder widersprüchliche Zuordnung ergibt einen konkreten Befund und keine stillschweigende Neuerzeugung.

### 8.4 Pflichtprüfungen auf Klassenebene

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

## 9. Abgrenzung der ersten Compilerlieferung

P1/P2 MÜSSEN die Metamodellinformationen und Grenzen dieses Dokuments berücksichtigen. Sie MÜSSEN bereits zeigen, dass mindestens eine kompakte Projektion aus dem geprüften Schema erzeugt werden kann und ein begrenzter Verbraucher fehlende Fähigkeiten korrekt meldet. Vollständiges XTF-I/O, ausführbare Geometrievalidierung und JDBC-Schemaerzeugung sind P4/P5-Arbeit.

Ein Agent darf die Compilerarbeit nicht wegen der Grösse des künftigen Datenbankteils auf ein schwaches Textmodell reduzieren. Ebenso darf er P1 nicht mit Dutzenden unbenutzten DB-Interfaces blockieren. Entscheidend sind heute die semantische Vollständigkeit der begonnenen Sprachmerkmale, unveränderliche Ergebnisse und überprüfbare Projektionen.

Die Weiterentwicklung wird nach [Implementierungsplan](14-implementierungsplan-und-arbeitspakete.md) freigegeben und durch die [Kompatibilitätsmatrix](13-tests-kompatibilitaet-und-qualitaet.md) abgesichert. Die historische Quelle wird anhand des [Quellenregisters](15-entscheidungen-risiken-und-quellen.md) gepinnt; Aussagen über erreichte Kompatibilität müssen auf tatsächlich ausgeführten Tests beruhen.
