# API-Register und gemeinsame Vertragstests

## 1. Zweck und Autorität

Dieses Register ordnet die gemeinsamen Typen, Fabriken und Dienste eindeutig einem Modul und Fachvertrag zu. Die vollständigen Signaturen und Methodenbedingungen stehen im verlinkten Kapitel. Eine Tabellenbeschreibung hier ist kein Auftrag, daneben eine zweite anders benannte Implementierung einzuführen. Nicht öffentliche Hilfsmethoden dürfen im jeweiligen Arbeitspaket angepasst werden, sofern die beobachtbaren Verträge und Abnahmen erhalten bleiben.

L1-1 verwendet die bestehenden `ModelSymbol`, `StructureSymbol`, `Origin` und
Querytypen für kanonische INTERLIS-Modellowner und gewöhnliche vordefinierte
Geometriestrukturen. Es entsteht keine neue öffentliche Signatur. Owner,
Sprachversion, bedarfsabhängig materialisierte Mitglieder, Import-/Präfixziele
und kanonische gegenüber kontextbezogener Herkunft sind in Kapitel 05 geregelt.

L1-2 verwendet die bestehenden `AttributeSymbol`, `NumericType`, `NumericRange`,
`DecimalLiteral`, `UnitSymbol`, `EffectiveMember`, `FormattedType` und
`TypeRelations`-Verträge. Es entsteht keine neue öffentliche Nutzer-API.
Unterteilungsmarker, effektive Vorgänger, exakte Faktorbedingungen und deren
Diagnosen stehen in Kapitel 05, die ausdrücklich freigegebenen Profile in
Kapitel 06/15. `NumericSubdivisionContractTest` und die erweiterten
`ScaledBoundaryContractTest`-Domainqueries prüfen diese Inhalte über das
öffentliche Schema beziehungsweise den abgelehnten Analyse-Snapshot.

Der modulübergreifend erreichbare interne Einstieg
`guru.interlis.model.internal.TypeCompatibility.definitelyDifferentUnitDimensions`
verwendet die interne Dimensionszerlegung für einen ausschliesslichen
Widerspruchsbeweis, ohne Umrechnungen zu erlauben. Seine Auflösung und
Arbeitsbudgetprüfung stammen aus dem aufrufenden Analysegraphen; kein
globaler Zustand und keine zusätzlichen veröffentlichten Modellwerte.

Java-Blöcke in der Spezifikation zeigen Signaturen und Varianten; Bodies und Imports sind teilweise absichtlich ausgelassen. Das Repository MUSS zusätzlich tatsächlich kompilierende API-Beispiele enthalten. Die Spezifikation behauptet keine erfolgreiche Kompilierung der nur deklarativ abgebildeten Klassenblöcke.

L1-3 präzisiert den bestehenden `EffectiveMember.inheritancePath`- und
Herkunftsvertrag aus Kapitel 05 für Klassen, Strukturen und Assoziationen.
`SemanticQueries.effectiveMembers`, die öffentlichen Symbolbezüge und
`LanguageQueries.definition` verwenden dieselben gebundenen Deklarationen;
es entstehen keine neuen Signaturen. Reihenfolge, Pfad und tatsächliches
Override-Ziel werden auch nach Importrevisionen und Fresh/Reuse geprüft.

Vorab-API 0.2 konkretisiert die Funktionssignaturen mit `TypeId` in
`FunctionParameter`/`FunctionSymbol` (Kapitel 05), Funktionssyntax mit `TypeSyntax`
und `NameSyntax` sowie `SyntaxSnapshot.options()` (Kapitel 04).
`ObjectArgumentType` (model, Kapitel 05) und `ObjectArgumentTypeSyntax`
(compiler/syntax, Kapitel 04) verwenden denselben Basis-/Restriktionsvertrag
für einzelne Objekte und Mengen. `objects` unterscheidet `OBJECT_ARGUMENT`
von `OBJECT_SET`; `TYPE_OBJECT_ARGUMENT` bleibt die gemeinsame Syntaxfamilie.
`ObjectSetExpression` / `ObjectSetExpressionSyntax` erhalten die gebundene
beziehungsweise syntaktische `ALL`-Auswahl. `PathExpressionSyntax.Segment`
erhält den Assoziationsoperator, den optionalen `NameSyntax`-Qualifikator und
`index(): Optional<PathIndex>` getrennt vom Namen. `PathIndex` und
`PathStep.IndexStep` (model, Kapitel 05) erhalten Auswahl und Zwischentyp;
exhaustive Verbraucher müssen den zusätzlichen Schritt berücksichtigen;
`PathStep.AssociationAccessStep` hält Assoziation, beide Rollen, Kardinalität
und Zwischentyp im selben Snapshot. `PathStep.AssociationSourceStep` (model,
Kapitel 05) ergänzt den Ausgangsrollenzugang mit Assoziation und Ausgangsrolle,
Kardinalität je Ausgangsobjekt und Ergebnistyp einschliesslich geerbter
Mengenwertigkeit. Exhaustive Verbraucher berücksichtigen diese Variante.
`AssociationDeclarationSyntax.cardinality`
erhält die Multiplizität der Assoziationsinstanzen. Nullfreiheit, Unveränderlichkeit,
Reihenfolge, Fehler und positive/negative Abnahmen regeln Kapitel 04/05.
`RoleDeclarationSyntax.Target` (compiler/syntax) und `RoleTarget` (model)
erhalten jede Basis mit ihren Restriktionen; `RoleSymbol.targets()` ersetzt den
Einzelzielvertrag. `AssociationSymbol.baseAssociation()` ergänzt die gebundene
Vererbung, `cardinality()` die effektive Linkmultiplizität; effektive Rollen verwenden die bestehende `EffectiveMember`-Abfrage.
Vertragstests prüfen auch Typabfragen und das ausgewählte gültige Schema.

`ConstraintDeclarationSyntax` und `ExistenceConstraintSyntax` (compiler/syntax,
Kapitel 04) bewahren strukturierte Schlüssel-/Zielpfade sowie LOCAL-Präfix und
BASKET. `UniquenessConstraint`, seine `LocalUniqueness` und `ExistenceConstraint`
(model, Kapitel 05) verwenden `ExpressionId` für diese Pfade. Es gibt keine
parallele String- oder `ObjectPath`-Fassade: Der zugehörige `PathExpression`
enthält Typ, Schritte und Quellbezug. Graph-/Schema-Closure und Vertragstests
schliessen diese Ausdrücke ein; Fehler und Lücken erzeugen kein gültiges Schema.

`PathStep.UnresolvedStep` (model, Kapitel 05) ergänzt die geschlossene
Pfadschrittmenge für fehlerhafte Analysen. Der Schritt bewahrt Name und
Quellbereich, besitzt kein erfundenes Ziel und verhindert ein gültiges Schema
für die betroffene Closure. Exhaustive Verbraucher müssen diese Variante
ausdrücklich behandeln.
`SourceInventory` erhält bereits vorhandene Syntaxsnapshots statt eines Budgets
für verstecktes Parsing (Kapitel 08). Es gibt keine parallelen Alt-/Neu-Fassaden.
Die [Migration](../api-migration-0.2.md) nennt die betroffenen Aufrufer.

Die laufende Vorab-API 0.2 erhält zusätzlich strukturierte
`EnumerationTypeSyntax`, `FormattedTypeSyntax` und `OidTypeSyntax` im
Compilerpaket `guru.interlis.syntax` (Kapitel 04). Die Modelltypen
`EnumerationType`, `FormattedType` und `OidType` gehören weiterhin
`interlis-model` / `guru.interlis.model` (Kapitel 05): effektive Enum-Basis und
FINAL-Teilbäume, gebundene Formatbestandteile sowie OID-Trägertypen.
Syntax verwendet Quellnamen, das Modell snapshotgebundene IDs; alle Werte
bleiben immutable und nullfrei. `ExistenceValueFamiliesContractTest` prüft
Inhalte, positive und negative Regeln, öffentliche Queries und Quellbindung.
Es gibt keine parallelen Alt-/Neu-Konstruktoren. FORMAT-Konkretisierung benötigt
keinen zusätzlichen öffentlichen Zustand: `Part.attribute()` bezeichnet das
wirksame Attribut der spezialisierten Struktur; `Part.origin()` behält den
Deklarationsort. Die noch fehlende numerische Präzision bleibt im referenzierten
`NumericType` sichtbar. `OidFollowupAbstractFormatContractTest` prüft diese
Unterscheidung und die sprachversionsabhängigen OID-Folgeregeln.

`OidRuleSyntax` (compiler, `guru.interlis.syntax`, Kapitel 04) und `OidRule`
(model, `guru.interlis.model`, Kapitel 05) ersetzen rohe OID-Headerstrings.
Die Syntaxdeklarationen besitzen `oidRules()` in Quellreihenfolge,
`SymbolHeader.oidRules()` gebundene effektive Regeln mit Herkunft. Optionalität,
NO OID, Snapshotbezug und Fehlerregeln stehen in den Fachkapiteln. Auch diese
Änderung gehört zur laufenden, unveröffentlichten Vorab-API 0.2; Aufrufer
übergeben Listen statt `Optional<String>`.

`ReferenceType` (model, `guru.interlis.model`, Kapitel 05) verwendet ein
optionales Primärziel und geordnete gebundene Restriktionen; leeres Primärziel
bedeutet ANYCLASS. `ReferenceTypeSyntax` (compiler, `guru.interlis.syntax`,
Kapitel 04) erhält dazu die Einschränkungsnamen als Syntaxkinder. Dies ersetzt
den bisherigen Einzelziel-Konstruktor innerhalb der unveröffentlichten 0.2-API.
`NoOidReferenceContractTest` prüft Zielmengen, Typabfragen, Quellbindungen und
versionsabhängige NO-OID-Diagnostik; die gemeinsame portable Suite prüft die
positiven und negativen Fälle über dieselbe Compilerengine.

`AttributeSymbol.declaredType()` enthält bei einer reinen MANDATORY-
Überschreibung die gebundene `TypeId` des geerbten Attributes bereits für die
Ausdrucksprüfung. `overrides()` bleibt die unmittelbare Attributidentität;
Herkunft und Quellbereiche der neuen Deklaration bleiben erhalten. Fehlende
Basen/Zyklen erzeugen Diagnosen statt eines gültigen generischen Ersatztyps.
`ClassSymbol.baseType()` kann gemäss PDF-Referenzprofil eine Klasse oder Struktur
bezeichnen; die Graphvalidierung erhält und prüft diese Identität.
`MandatoryOverrideTypeContractTest` prüft beide Sprachversionen, Pfade, Funktionen,
Constraints, Quellnavigation und die Sortierung importierter Quelldateien.
Keine Signaturänderung und keine zusätzliche Fassade.

`DomainReferenceType.targetDomain()` und `EffectiveType.aliasPath()` bewahren
die Domainidentität auch bei früh benötigten importierten Typen. Die
Ausdrucksprüfung verwendet dieselbe Bindung wie das endgültige Schema;
Dateisortierung und Wiederverwendung dürfen die fachliche Entscheidung nicht
ändern. `ImportedDomainTypeContractTest` prüft beide Sprachversionen,
Skalar-/Funktions-/Struktur-/Indexpfade, Enum-/FORMAT-Basen, Typqueries,
Navigation und Typzyklen. Der Abschnitt ändert keine öffentliche Signatur.

`TopicSymbol.dependencies()` bleibt eine Liste direkt deklarierter Topic-IDs;
`baseTopic()` erhält die Vererbung separat (Kapitel 05). Die gebundenen
DEPENDS-ON-Namensreferenzen verwenden den vorhandenen `TYPE_USE`-Vertrag.
`TopicDependencyContractTest` prüft Scope, Vererbung, Restriktionen und Zugriff
auf diese Werte. Dafür entsteht keine neue öffentliche Signatur oder API-Version.

`UnitSymbol` (model, `guru.interlis.model`) und `UnitDeclarationSyntax`
(compiler, `guru.interlis.syntax`) erhalten `designation`, `derivedFrom` und
`components` zusätzlich zu den bestehenden Grunddaten. Header-/Syntaxname
bezeichnen das Kurzzeichen. `UnitSymbol.Component(Operator, SymbolId)` und
`UnitDeclarationSyntax.Component(Operator, NameSyntax)` sind geordnete,
nullfreie immutable Werte; `UnitSymbol.Operator` unterscheidet MULTIPLY und
DIVIDE. Vollständiger Vertrag in Kapitel 04/05. Konstruktoren der laufenden,
unveröffentlichten Vorab-API 0.2 werden direkt angepasst; keine Parallelfassade.
`ComposedUnitContractTest` prüft Bindung, Fehler, öffentliche IDs, Numerik und
Quellnavigation; die portable Suite prüft denselben Compilerkern.

## 2. Module, Packages und grundlegende Typen

| Modul | Package | Kanonische Typen | Definition |
|---|---|---|---|
| model | `guru.interlis.source` | `SourceId`, `SourceRevision`, `SourceText`, `SourceRange`, `SourcePosition`, `LineMap`, `TextEdit` | [04](04-quelltext-syntax-und-parser.md), Editvertrag [07](07-workspace-inkrementalitaet-und-lsp.md) |
| model | `guru.interlis.model` und fachliche Unterpackages | `SnapshotId`, `SymbolId`, `TypeId`, `ExpressionId`, `ScopeId`, `ReferenceId`, jeweilige `…Ref`, `SemanticGraph`, `CompiledSchema`, `SchemaFingerprint`, `SchemaProvenance` | [05](05-metamodell-und-typensystem.md) |
| model | `guru.interlis.model.query` | `QueryResult<T>`, `SemanticQueries`, `TypeRelations`, typisierte Gründe und Ergebnisse | [05](05-metamodell-und-typensystem.md) |
| model | `guru.interlis.diagnostics` | `Diagnostic`, `DiagnosticId`, `DiagnosticCode`, `DiagnosticArgument`, `RelatedLocation`, `Severity`, `Phase`, Katalogverträge | [09](09-diagnostik-logging-und-telemetrie.md) |
| model | `guru.interlis.execution` | `CancellationToken`, `CancellationReason`, `OperationCancelledException`, `RunObserver`, `RunEvent`, `RunId`, `RunContext`, `RunContextFactory`, `RunLifecycle`, `FailureId`, `ClockSource` | [09](09-diagnostik-logging-und-telemetrie.md), Ergebnisverwendung [06](06-kompilierung-und-semantik.md) |
| compiler | `guru.interlis.syntax` | `SyntaxServices`, `SyntaxService`, `ParseOptions`, `ParseMode`, `SyntaxSnapshot` und eigene Syntaxknoten | [04](04-quelltext-syntax-und-parser.md) |
| compiler | `guru.interlis.format` | `ModelFormatters`, `ModelFormatter`, `FormatOptions`, `FormatOutcome`, `FormattingNotice` | [18](18-cli-sdk-und-kompatibilitaetsadapter.md) |
| compiler | `guru.interlis.compiler` | `CompilerEngines`, `CompilerServices`, `CompilerEngine`, `CompilationRequest`, `CompilerOptions`, `AnalysisBudget`, `AnalysisSnapshot`, `AnalysisCoverage`, `AnalysisReuse`, `CompilationOutcome`, `RejectionSummary`, `RejectionReason` | [06](06-kompilierung-und-semantik.md) |
| compiler | `guru.interlis.compiler` | `ModelSelector`, `SourceSet`, `ImportSite`, `ImportBinding`, `ImportResolution`, `ModelLocation`, `ResolutionProblem`, `ResolutionFingerprint` | [06](06-kompilierung-und-semantik.md), [08](08-modellaufloesung-und-repositories.md) |
| compiler | `guru.interlis.compiler.query` | `LanguageQueries`, Completion-/Hover-/Definition-/Rename-Verträge | [07](07-workspace-inkrementalitaet-und-lsp.md) |
| workspace | `guru.interlis.workspace` | `Workspaces`, `Workspace`, `WorkspaceRevision`, `WorkspaceConfiguration`, `WorkspaceState`, `SourceUpdate`, `SourceExpectation`, `SourceChange`, `WorkspaceCapture`, `AnalysisAcceptance`, Receipts/Requests/Metrics | [07](07-workspace-inkrementalitaet-und-lsp.md) |
| workspace | `guru.interlis.workspace.resolution` | `SourceProvider`, `SourceInventory`, `ModelResolutionService`, `ResolutionRequest`, `ResolutionOutcome`, `PreparedClosure`, Kandidaten/Closure/Trace | [08](08-modellaufloesung-und-repositories.md) |
| repository | `guru.interlis.repository` | `RepositoryClient`, `SourceLoader`, `LoadedSource`, `FetchPolicy`, `ContentStore`, Index-/Refreshdienste | [08](08-modellaufloesung-und-repositories.md) |
| projection | `guru.interlis.projection` und Unterpackages | `DiagramProjectors`, `DiagramProjector`, `DiagramOptions`, `DiagramDocument`, Diagramm-/Notice-Typen | [11](11-visualisierung-und-projektionen.md) |
| Hostadapter | werkzeugspezifisch | `PublicationController`, LSP-Adapter, Beobachtungssenken | [07](07-workspace-inkrementalitaet-und-lsp.md), [09](09-diagnostik-logging-und-telemetrie.md) |
| CLI | `guru.interlis.cli` | `CliApplication`, `CliExecutionResult`, `CliTermination`, `CliOutcomeMapper`, `CommandLineParser`, `ExitCodePolicy`, `OutputPlanner` | [18](18-cli-sdk-und-kompatibilitaetsadapter.md) |

`LanguageVersion` ist der gemeinsame Modellwert `{ ILI23, ILI24 }`. Er wird nicht pro Parser, Host oder Plan neu deklariert. `SemanticOverlays` ist der kanonische Name der unveränderlichen fachlichen Overlayeingabe aus CompilerOptions; allgemeine Formulierungen „Modell-Overlays“ führen keine zweite Klasse `ModelOverlaySet` ein.

Die Foundation enthält keine `AnalysisSnapshot`-Referenzen. Eine Abbruchausnahme transportiert den Grund, ein optionaler Teilsnapshot steht im Compiler-Outcome. Damit bleibt der Modulgraph auch bei Fehlerbehandlung azyklisch.

## 3. Konstruierbare öffentliche Einstiege

| Einstieg | Signaturen | Vertrag |
|---|---|---|
| Syntax | `SyntaxServices.create()` | erzeugt nutzbare SyntaxService ohne Workspace, IO oder globale Listener |
| Compiler | `CompilerEngines.create()`; `CompilerEngines.create(CompilerServices)` | Defaults bzw. expliziter Observer, Uhr und Kontextfactory; fachliche Optionen bleiben pro Request |
| Dienste | `CompilerServices.defaults()`; `CompilerServices(observer, clock, runContexts)` | unveränderliche Dienstzusammenstellung ohne Pflicht zu einem Loggingbackend |
| Laufkontext | `RunContextFactory.defaults()`; `createRoot()`; `createRoot(attributes)`; `createChild(parent)` | frische unveränderliche Root-/Kindmetadaten; die Factory startet keinen Lauf |
| Workspace | `Workspaces.create(CompilerEngine, WorkspaceConfiguration)`; `WorkspaceConfiguration.defaults()` | leerer Workspace, Revision 0, keine Modellbeschaffung beim Erzeugen |
| Diagramm | `DiagramProjectors.create()`; `DiagramOptions.defaults(selection)` | verwendet expliziten Snapshot beziehungsweise Schema und eine konkrete Auswahl; kein aktueller globaler Workspace |
| Formatierung | `ModelFormatter.format(source, options, token)` | deterministische Quellformatierung ohne IO; kein Modellgenerator |
| CLI | `CliApplication.run(arguments,environment,token)` | Bibliotheksaufruf ohne System.exit |
| Browser | `CompilerClient` gemäss Kapitel 10 | versioniertes Promise-/Sessionprotokoll und explizite Abbruchoptionen |

Shared CompilerServices müssen für die zugesagte parallele Engineverwendung geeignet sein. Workspaceaufrufe auf derselben Instanz sind dagegen zunächst seriell. Diese beiden Zusagen dürfen nicht verwechselt werden.

## 4. Compilervertrag in kompakter Form

Die Engine besitzt die zwei vollständigen Methoden `analyze(request, reuse, context, token)` und `compile(request, reuse, context, token)`. `context` ist ein frischer `RunContext`. Vier Convenience-Overloads sind `analyze(request, token)`, `analyze(request, reuse, token)`, `compile(request, token)` und `compile(request, reuse, token)`. Ohne explizite Wiederverwendung gilt `AnalysisReuse.none()`; ohne expliziten Kontext erzeugt die injizierte Factory einen neuen Rootkontext. `analyze` dient fehlertoleranten Anfragen; `compile` erzwingt die strikte Gültigkeit der gewünschten Closure.

Der vollständige Engineaufruf besitzt den Lifecycle des Compilerlaufs und erzeugt genau dessen Start und Abschluss. Convenience-Methoden und Workspace starten keinen zusätzlichen Compilerlauf. Eine CLI-/Browseroperation, die Beschaffung, Kompilierung und Export umfasst, besitzt einen eigenen Elternlauf und übergibt einen frischen Kindkontext an die Engine. `RunLifecycle` bleibt neutral in der Foundation; er referenziert keine Compilerresultate. Es gibt weder einen globalen aktuellen Kontext noch eine `ThreadLocal`- oder zusätzliche `RunScope`-API.

| Situation | analyze | compile | Aussage |
|---|---|---|---|
| gültiger vollständiger Umfang | AnalysisSnapshot | Succeeded(schema,analysis) | angeforderte Closure geprüft |
| Sprachfehler | Snapshot mit Diagnosen | Rejected(analysis,rejectionSummary) | konkrete Fehler im relevanten Quellumfang |
| benötigtes Feature nicht implementiert | Snapshot mit NOT_IMPLEMENTED-Coverage | Rejected(analysis,rejectionSummary) | Implementierungsumfang unzureichend; keine Behauptung sprachlicher Ungültigkeit |
| fehlende notwendige Quelle | partielle Analyse mit Ursache | Rejected(analysis,rejectionSummary) | benötigte Eingabe fehlt; Ursache kann fachlich oder betrieblich sein |
| Warnung bei REJECT_WARNINGS | Warnung bleibt Warnung | Rejected(analysis,rejectionSummary) | explizite Warnungspolitik, keine umetikettierte Sprachfehlerseverity |
| Abbruch/Deadline/Ressourcenlimit | OperationCancelledException(reason) | Cancelled(optionalAnalysis,reason) | unvollständig; keine Gültigkeitsaussage |
| interner Defekt | CompilerInternalException(FailureId) | Failed(FailureId,safeDiagnostic) | Implementierungs-/Laufzeitfehler |
| ungültige Java-Argumente | gezielte Exception vor Laufannahme | gezielte Exception vor Laufannahme | API-Vertragsverletzung |

`RejectionSummary` stammt ausschliesslich aus dem Gate. Es enthält Gründe, relevante Diagnose-IDs, angeforderte Wurzeln und betroffene nicht unterstützte Feature-IDs. RejectionReason unterscheidet SOURCE_ERRORS, WARNINGS_REJECTED, MISSING_INPUT, AMBIGUOUS_INPUT, ENVIRONMENT_FAILURE und INCOMPLETE_SUPPORT. Adapterspezifische Status werden aus dieser Zusammenfassung abgeleitet; Adapter führen keine eigene Gateprüfung über alle Snapshotdiagnosen aus. Ein CLI-Exporter kann nach Compilererfolg noch scheitern. `SUCCESS` für die CLI entsteht erst nach der vereinbarten vollständigen Ausgabe und deren erfolgreichem Flush.

Die kanonische Quellenordnung ist `SourceId.value()` nach Java `String.compareTo`, also lexikografisch über UTF-16-Codeeinheiten, ohne Locale-Kollation und unabhängig von der Einfügereihenfolge der Eingabemap. Die Diagnostik verwendet diese Ordnung mit den weiteren Kriterien aus Kapitel 09; globale Diagnosen stehen zuerst. Fachliche Deklarations- und Transferreihenfolgen werden dadurch nicht umsortiert.

### 4.1 Workspace und ausserhalb ausgeführte Analyse

`capture(request, token)` liefert einen unveränderlichen `WorkspaceCapture` mit Request, Wiederverwendung, Fingerprint und `PreparedClosure resolution`. Die Convenience-Variante ohne Token verwendet einen niemals abgebrochenen Token. Der Host kann über `capture.resolution()` fehlende Quellen und Auflösungsspuren prüfen, ohne selbst Imports erneut zu parsen.

Eine Hintergrundanalyse folgt `capture` → vollständiger Engineaufruf → `acceptAnalysis(capture, analysis)` → bedingte Veröffentlichung. `acceptAnalysis` prüft Herkunft, Request, Quellenrevisionen und Fingerprints. Nur ein passender aktueller Stand übernimmt Wiederverwendungsdaten und Abhängigkeiten atomar; diese Übernahme erhöht die WorkspaceRevision nicht. Ein inzwischen veralteter Stand liefert `STALE_NO_COMMIT`. Ein vollständig beendeter Snapshot mit fachlichen Fehlern kann wiederverwendbar sein; ein abgebrochener Teilsnapshot wird nicht als vollständiger Cachestand übernommen. Vollständige Workspaceaufrufe erhalten `(request, context, token)` und reichen den Kontext an die Engine weiter.

## 5. Modellabfragen und Gültigkeitsbereiche

`SemanticQueries` verwendet `SymbolRef`, `TypeRef` und weitere snapshotgebundene Referenzen. Ein Ref aus einem anderen Snapshot ist keine still korrigierbare FQN-Anfrage. Eine gewünschte Suche nach einem Namen ist eine eigene Query mit möglicher Mehrdeutigkeit.

`QueryResult<T>` hat genau die Basisvarianten `Known<T>`, `Unresolved<T>`, `Invalid<T>` und `NotAnalyzed<T>`. Legale Generic-/Deferred-Werte gehören zu den fachlichen Typen und werden nicht durch `Unresolved` ersetzt. `Known` kann einen wohldefinierten legal aufgeschobenen Typ beschreiben.

`CompiledSchema.semanticGraph()` und `queries()` sind auf seine geprüfte Auswahl beschränkt. Derselbe zugrunde liegende AnalysisSnapshot kann weitere unabhängige Fehler enthalten, die diese Sicht nicht veröffentlicht. `schema.models()` und `sourceRevisions()` enthalten nur die relevante Closure samt tatsächlich benötigter Quellen. Bei Dateien mit mehreren Modellen bleibt die Quellrevision die Revision der gesamten Datei; die Schemaauswahl ist dennoch modellbezogen.

## 6. Interne Klassen mit festgelegter Verantwortung

| Bereich | Kanonische Verantwortlichkeiten |
|---|---|
| Pipeline | `DefaultCompilerEngine`, `CompilationOrchestrator`, `CompilationRun`, `ReusePlanner`, `SourceSetVerifier`, `CompilationFingerprinter` |
| Syntax/Index | `SyntaxPhase`, `DeclarationIndexer`, `RootSelector`, `ImportBinder`, `ScopeBuilder`, `ReferenceBinder` |
| Typen/Regeln | `TypeResolver`, `InheritanceChecker`, `EffectiveMemberResolver`, `ExpressionTypeChecker`, `ObjectPathResolver`, `FunctionCallChecker`, `ConstraintChecker`, `ViewChecker`, `TranslationChecker`, `GraphicChecker` |
| Ergebnis | `GraphFreezer`, `CompilationGate`, `DefaultCompiledSchema`, `IdentityReconciler` |
| Workspace | `DefaultWorkspace`, `DependencyIndex`, `AnalysisCache` und Source-/Invalidierungsverwaltung gemäss Kapitel 07 |
| Resolver | `ModelHeaderIndexer`, `CandidateSelector`, `ClosureBuilder`, `ImportBindingAssembler`, `ResolutionFingerprinter` |
| Diagnose und Lauf | `DiagnosticCollector`, `DiagnosticPresentation`, profilierte Renderer, `RunContextFactory`, `RunLifecycle` und Observeradapter gemäss Kapitel 09 |

Die Tabelle benennt fachliche Grenzen. Sie verlangt weder eine öffentliche Klasse pro Prüfmethode noch einen separaten Vollscan für jeden Dienst. Zusammenlegen ist zulässig, wenn die Einzelverträge und ihre Tests sichtbar bleiben. Ein allgemeiner Visitor mit beliebigen `Map<String,Object>`-Werten erfüllt den typisierten Modellvertrag nicht.

## 7. Spätere Verbraucher-APIs

Die in Kapitel 12 definierten `TransferPlanner`, `ValidationPlanner` und `SchemaPlanner` erhalten ausschliesslich gültige Schemas und explizite Profile/Overlays beziehungsweise bereits geprüfte Transferpläne. IO-Ausführung liegt in getrennten Sitzungen/Executoren. Die detailreichen Zukunftsverträge sind eine Architekturreserve mit Abnahme, kein Auftrag zur Anlage leerer P4/P5-Module während P0.

`LegacyCompilerProfile` komponiert ein `LegacyCliProfile` und weitere Sprach-/Metamodellreferenzen. `LegacyApiProfile` bezeichnet zusätzlich den konkreten alten Java-API-/ABI-/Verbraucherumfang. Diese Profile werden nicht als austauschbare Strings ohne eigene Felder behandelt.

Eine gemeinsame Java-WASM-Bridge kann nach tatsächlicher Wiederverwendung in `interlis-wasm-bridge` liegen. Dieses Modul hängt von workspace/compiler/projection/model ab. TeaVM- und Graal-Interoptypen bleiben in den Zieladaptern. Die Bridge gehört nicht in projection, weil das eine falsche Abhängigkeit zu workspace erzeugen würde.

## 8. Gemeinsame Vertragstests

Diese Namen bezeichnen zu implementierende Tests. Sie sind keine bereits ausgeführten Java-Tests dieses Spezifikationspakets.

| Testname | Prüft | Zugehörige Kapitel |
|---|---|---|
| `PublicSdkConstructionTest` | Fabriken und minimaler String-Compilefall sind tatsächlich verwendbar |04/06/07/18 |
| `CoreDependencyBoundaryTest` | keine ANTLR-Typen in öffentlichen API, keine Host-/UI-/DB-Zyklen |02/03 |
| `SourceRangeContractTest` | UTF-16, Revision, CRLF, Halbintervalle und Texteditgrenzen |04/07 |
| `SnapshotOwnershipTest` | externe Listenmutation, Workspaceupdate/close und Cacheeviction verändern alten Snapshot nicht |05/07 |
| `CrossSnapshotReferenceTest` | alte/vermischte Ref-Werte liefern den definierten Fehler |05/07 |
| `CompiledSchemaSelectionTest` | Schema enthält nur geprüfte Closure, unabhängige fehlerhafte Modelle bleiben draussen |05/06 |
| `CoverageGateTest` | fehlende Implementierung, abgebrochene Phase oder unterdrückte Fehler erzeugen keinen Erfolg |06/09 |
| `CompilationOutcomeContractTest` | alle Ausgänge inklusive Budget/Warnungspolitik/Fehler sind korrekt klassifiziert |06/09/18 |
| `SourceSetBindingVerificationTest` | falsche Importrevision, Modellversion oder Deklarationsposition wird erkannt |06/08 |
| `NegativeDependencyInvalidationTest` | später hinzugefügtes Modell/zweiter Kandidat verändert Bindung |07/08 |
| `FreshVsIncrementalContractTest` | jeder Editierzwischenstand entspricht frischer semantischer Analyse |07/13 |
| `LspSequentialChangeTest` | mehrere LSP-Edits werden auf Zwischenständen materialisiert |07 |
| `StalePublicationTest` | verspätetes Ergebnis überschreibt aktuelle Diagnostik nicht |07/10 |
| `DetachedAnalysisAcceptanceTest` | aktueller Capture übernimmt Cache und Abhängigkeiten atomar; veralteter Capture verändert beides nicht |07/10 |
| `DiagnosticCauseImportTest` | importierte Resolverdiagnosen behalten konsistente Ursachen im aktuellen Bericht |08/09 |
| `ObserverFailureIsolationTest` | werfende Senke verfälscht weder fachlichen Status noch Ergebnis |09 |
| `RunContextPropagationTest` | Convenience, Workspace und parallele Eltern-/Kindläufe erzeugen genau ihre eigenen Start-/Abschlussereignisse |06/07/09 |
| `CanonicalSourceOrderTest` | verschiedene Einfügereihenfolgen desselben SourceSet ändern das fachliche Ergebnis und die kanonische Diagnostik nicht |06/08/09 |
| `PortableNumericContractTest` | grosse Ganzzahlen, Dezimalpräzision und Literalhintergrund bleiben erhalten |05/10 |
| `WasmHandleLifecycleTest` | freigegebene/alte Sessionhandles und Workerneustart werden korrekt behandelt |10 |
| `DiagramProjectionContractTest` | Assoziationen, geerbte Elemente, ungelöste Ziele und SourceAnchors |11 |
| `CliCompletionAfterExportTest` | Export-/Flushfehler verhindern falschen CLI-Erfolg |18 |
| `ModelExportContractTest` | jedes zugesagte Generatorformat erfüllt seine Formatgoldens und meldet nicht unterstützte Features vor der Ausgabe |18 |
| `LegacyCrossUseDatabaseTest` | Alt/Neu auf derselben Datenbank mit Metadaten und echten Operationen |12/13, P5 |

## 9. Registeranforderungen

| ID | Normative Anforderung | Phase | Abnahme |
|---|---|---|---|
| API-001 | Gemeinsame Typen MÜSSEN einen einzigen kanonischen Besitzer und Namen haben. | P0/P1 | Register, Modulgraph und reale Imports stimmen überein. |
| API-002 | Öffentliche Einstiege MÜSSEN tatsächlich konstruierbar sein. | P1 | PublicSdkConstructionTest gegen Artefakte. |
| API-003 | Klassen-/Methodendokumentation MUSS Vorbedingungen, Nachbedingungen, Reihenfolge, Fehler und Lebensdauer erklären. | P1 | Vertragstest-/Javadocreview. |
| API-004 | Unterschiedliche Ablehnungs-/Abbruchursachen MÜSSEN bis zu den Adaptern unterscheidbar bleiben. | P1 | Outcome-/CLI-/Bridge-Matrix. |
| API-005 | Snapshots und geprüfte Sichten MÜSSEN ihre Auswahl und Herkunft korrekt begrenzen. | P1 | Selection-/Ownershiptests. |
| API-006 | Änderungen an gemeinsamen APIs MÜSSEN Verbraucher, Register und Tests gemeinsam aktualisieren. | P1 | Kein Drift nach API-Änderung. |

## 10. Vollständigkeit des Methodenentwurfs

Der Entwurf legt alle wesentlichen öffentlichen Einstiege und die fachlich bedeutenden internen Prüfdienste fest. Er erfindet keine tausenden trivialen Getter als Ersatz für fachliche Regeln. Zusätzliche private Datenträger oder Helpermethoden entstehen im Code nach Bedarf. Fehlt beim Implementieren ein fachlicher Vertrag, wird der betreffende Abschnitt mit Regel, Methodenzuständigkeit und Fall ergänzt; die globale Zielmatrix bleibt erhalten.

### View-Vertragserweiterung innerhalb der unveröffentlichten API 0.2

Besitzer ist `interlis-model`, Package `guru.interlis.model`: `ViewSymbol` mit
seinen geschlossenen Formationsvarianten und `Base`/`BaseExtension`,
`SymbolKind.VIEW_BASE`, `PathStep.ViewBaseStep` sowie
`AttributeSymbol.derivations()`. Kapitel 05 beschreibt Ordnung, Herkunft,
Snapshotbindung und Schema-Abdeckung. `interlis-compiler` besitzt die
strukturierten Syntaxgegenstücke gemäss Kapitel 04 und den internen
`ViewBuilder`; dieser teilt ausschliesslich den Zustand seines Compilerlaufs.

`ViewBindingContractTest` prüft geordnete Schema-Inhalte, Ausdruckserreichbarkeit,
Vererbung, Selektionen, Join-/Unionregeln und Aggregation. Laufzeit- und
Konformitätsabnahmen werden getrennt im Implementierungsstatus festgehalten.
Es existiert keine parallele stringbasierte View-Fassade.

Ergänzt werden `PathStep.InspectionStep`/`InspectionKind`,
`ViewSymbol.Inspection.element`/`parent`, `AssociationSymbol.derivedFrom` und
`RoleSymbol.derivation`. Die zugehörigen Syntaxwerte liegen in
`AssociationDeclarationSyntax.derivedFrom` und `RoleDeclarationSyntax.derivation`.
Die Bindung von Referenzen und die erreichbaren Modellinhalte werden in
`ViewBindingContractTest`, die Kanten in `DiagramProjectionTest` geprüft.

`interlis-compiler` besitzt `guru.interlis.syntax.InspectionExpressionSyntax`
und `SyntaxKind.EXPRESSION_INSPECTION`; `interlis-model` besitzt
`guru.interlis.model.InspectionExpression` als weitere geschlossene Variante
von `TypedExpression`. Konstruktorinvarianten, Typ, Kinderreihenfolge,
Snapshotbindung und Fehlerzustände stehen in Kapitel 04/05.
`InspectionFactorContractTest` und die gemeinsamen portablen Fälle sichern
den Vertrag. Die Vorab-API bleibt 0.2; es gibt keine zweite Fassade.

View-Vererbungsverträge werden zusätzlich durch
`ViewInheritanceTranslationContractTest` geprüft: unmittelbare `overrides()`-Kette,
lokale Ableitungslisten in Basis-vor-Erweiterung-Reihenfolge, vollständiger
effektiver Vererbungspfad, MANDATORY nach ALL OF, importierte Identitäten und
AGGREGATES-Korrespondenz in Übersetzungen. `ViewFoundationContractTest` ergänzt
gebundene Projektions-/Selektions-/Join-/Union-/Aggregations- und
Inspektionsresultate, das vererbte AREA-Verbot und versionsgerechtes `ALL OF`
samt Rollenherkunft und navigierbaren Teilnehmerzielen in 2.4. Keine neue
öffentliche Signatur.


### Parameter- und Klassenwerte, Vorab-API 0.2

Kapitel 04 besitzt `ParameterDeclarationSyntax`, `ParameterExpressionSyntax`,
`AttributePathTypeSyntax`, `AttributeConstantExpressionSyntax` und die gebunden
vorbereitete `ClassValueTypeSyntax` (compiler/syntax). Kapitel 05 besitzt
`ParameterSymbol`, `SymbolValueExpression`, `ClassValueType` und
`AttributePathType` mit seinen geschlossenen `Owner`-Varianten (model).
`PARAMETER` ergänzt SymbolKind/CompletionKind; der LSP bildet es auf Variable ab.
Semantik, TypeRelations, Quellqueries und Schemaauswahl verwenden dieselben
Identitäten. `ParameterClassFunctionContractTest` und portable Verträge prüfen
den in Kapitel 05 beschriebenen Teilumfang. Keine parallelen Alt-/Neu-Fassaden.
`DependentFunctionArgumentsContractTest` ergänzt die abhängige Aufrufbindung:
Konstanten verwenden den tatsächlichen OF-Kontext, klassenwertige Ausdrücke
behalten ihre gebundene Identität, und abhängige ATTRIBUTE-Rückgabetypen
werden je Aufruf mit bindbarem Owner gebunden, ohne die formale Deklaration
zu verändern. Legale UNDEFINED-/ANYCLASS-Wurzeln behalten den symbolischen
Argumentbezug und ihre tatsächlichen Aufrufkinder (Kapitel 05).
`AttributePathType.ClassAttribute.path()` kann hierfür neben dem deklarierten
OF-Pfad einen klassenwertigen tatsächlichen Ausdruck desselben Snapshots
referenzieren. Kapitel 05 besitzt den fachlichen Vertrag; keine neue Signatur.
`SetConditionContextContractTest` prüft den bestätigten SET-Hauptkontext ohne
individuelle Attribut-/Rollen-/THIS-Werte, den objektbezogenen WHERE-Filter,
externe Constraints, Revisionen/FreshReuse und die ausdrücklich bestätigten
areAreas2/3- sowie OBJECTS-Funktionsprofile. Die gemeinsame Pfadbindung liefert
`ILI-SEM-0008` und typisierte Fehler; keine zusätzliche öffentliche API.


### K3: Generische Koordinatenkontexte

`ContextDeclarationSyntax` und `TopicSyntax.deferredGenerics` gehören
compiler/syntax (Kapitel 04); `ContextSymbol`, `TopicSymbol.GenericBinding` und
`TopicSymbol.BindingState` gehören model (Kapitel 05). `SymbolKind.CONTEXT`
ergänzt die geschlossene Symbolmenge. Quellqueries verwenden die gebundenen
Referenzen; Verbraucher lesen die wirksame Bindung am Topic, ohne Domain-Typen
kontextabhängig zu überschreiben. Verträge und Grenzen stehen in Kapitel 05;
`GenericContextContractTest` prüft auch die Inhalte des ausgewählten Schemas.

### K4: Linienformen

Kapitel 04 besitzt `LineFormDeclarationSyntax` und die strukturierten
Linienform-/Linienattributreferenzen von `LineTypeSyntax` (compiler/syntax).
Kapitel 05 besitzt `LineFormSymbol` und die erweiterten gebundenen
`GeometryType`-Komponenten (model). LINE_FORM ergänzt SymbolKind,
CompletionKind und DiagramNodeKind; Kapitel 11 besitzt den Diagrammvertrag.
`LineFormBindingContractTest` und `DiagramProjectionTest` prüfen die öffentlichen
Identitäten, Reihenfolgen, Referenzen und Projektionen. Die Vorab-API bleibt 0.2;
String- und gebundene Parallelfassaden werden nicht angeboten.


### K4: Metadaten, Achsen und Inspektionen

Kapitel 04 besitzt `MetadataBasketDeclarationSyntax` und dessen
`ObjectDeclaration`, die erweiterten `NumericTypeSyntax`/`CoordinateTypeSyntax`
sowie Parametermodifikatoren (compiler/syntax). Kapitel 05 besitzt
`MetadataBasketSymbol`, `MetaObjectSymbol`, `NumericRange.ReferenceSystem`,
`CoordinateType.Rotation`, `ParameterSymbol.overrides` und
`DerivationKind.CONTEXTUALIZATION` (model). Herkunft, optionale Fehlerbindungen,
Reihenfolge und Lebensdauer stehen dort. Kapitel 11 besitzt die zusätzlichen
Diagrammarten METADATA_BASKET/METAOBJECT und PARAMETERS/PARAMETER.
`MetadataBindingContractTest` und `GeometryInspectionContractTest` prüfen
öffentliche Inhalte und Fehlerpfade; Projektion und portable Verträge ergänzen
die Verbraucherabnahme. API 0.2 erhält keine parallele String-Fassade.

### K5: Grafiken und Signaturparameter

Kapitel 04 besitzt `GraphicDeclarationSyntax` einschliesslich seiner
Regel-/Zuweisungswerte und `MetaObjectTypeSyntax` (compiler/syntax). Kapitel 05
besitzt `GraphicSymbol`, `DrawingRuleSymbol` mit geschlossenen Wertvarianten und
`MetaObjectType` (model). GRAPHIC/DRAWING_RULE ergänzen SymbolKind, SyntaxKind,
CompletionKind und DiagramNodeKind; METAOBJECT ergänzt TypeKind. Der LSP bildet
Grafiken auf Module und Zeichenregeln auf Methoden ab. Quellqueries verwenden
die gebundenen Parameter-/Basis-/Signaturreferenzen. Verträge und Fehlerzustände
stehen in Kapitel 04/05, Projektion in Kapitel 11. `GraphicBindingContractTest`,
`DiagramProjectionTest` und portable Verträge prüfen den Umfang. API 0.2 erhält
keine zusätzliche String- oder Alt-Fassade.

### K6: Exakte Einheitenkonversionen

Kapitel 05 besitzt `UnitSymbol.ConversionFactor` und `UnitSymbol.ConstantKind`
sowie die neuen `conversionFactors`-/`conversionFunction`-Komponenten (model).
Kapitel 04 besitzt die ergänzten Komponenten von `UnitDeclarationSyntax`
(compiler/syntax), verwendet denselben Faktorwert und behält Namensreferenzen
als `NameSyntax`. Konstruktoren werden innerhalb der unveröffentlichten API
0.2 direkt angepasst; keine parallele Alt-Fassade. `TypeRelations.canExtend`
behält seine Signatur und prüft skalare Einheiten nach dem Vertrag aus Kapitel
05. `NumericUnitConversionContractTest` und portable Fälle sichern Inhalte,
Nullfreiheit, Unveränderlichkeit, Quellbezug und Fehlerpfade.

### Vordefinierte Datums-/Zeitdomänen (G2 A)

Die vordefinierten Inhalte verwenden `DomainSymbol`, `DomainReferenceType`,
`FormattedType`, `StructureSymbol` und `AttributeSymbol` aus `interlis-model` /
`guru.interlis.model`. `TemporalKind` bleibt die Klassifikation; `TemporalType`
und `TypeKind.TEMPORAL` entfallen innerhalb der unveröffentlichten 0.2.
Struktur-, Attribut-, Einheiten- und Referenzsystem-IDs gehören zum jeweiligen
Graph. Vordefinierte Herkunft besitzt keinen Quelldeklarationsbereich.
`PredefinedTemporalContractTest` prüft Inhalte, Versionsprofile, verschachtelte
Formate und Fresh-/Reuse-Parität; `TemporalTypeContractTest` prüft die gemeinsame
Bindung der 2.4-Schlüsselwörter. Grenzen und Fehler verwenden den gemeinsamen
FORMAT-Prüfer aus Kapitel 05; sämtliche veröffentlichten Werte sind immutable
und nullfrei.

### Einfache vordefinierte Domänen und Namensbindung (G2 E1)

`DomainSymbol`, `DomainReferenceType`, `TextType`, `AlignmentType` und
`Origin.PredefinedOrigin` behalten ihre Besitzer und Signaturen.
`PredefinedBindingContractTest` prüft die drei einfachen Domänen nach Kapitel 05,
FINAL, snapshotgebundene Typ-/Sprachqueries, Originalbereiche, unbekannte Ziele,
exakte qualifizierte Namen, Imports und Wiederverwendung. Neue öffentliche
Fassaden oder Konstruktorvarianten entstehen nicht. Werte bleiben unveränderlich,
nullfrei und graphgebunden; fehlende Namen ergeben erklärbare Quellfehler.

### E1: Funktionslisten und METAOBJECT_TRANSLATION

Die bestehenden Fabriken, Snapshot-Verträge sowie `FunctionDeclarationSyntax`,
`FunctionSymbol`, `FunctionCallExpression`, `ClassSymbol`, `AttributeSymbol`,
`ConstraintSymbol`, `UniquenessConstraint` und `PathExpression` behalten ihre
Signaturen und Besitzer. `E1FunctionMetadataContractTest` prüft Versionsprofile,
Originalbereiche, Recovery, Ausdrucksfragmente, typisierte Aufrufe, beide
Pflichtattribute und unabhängigen UNIQUE-Schlüssel, Reihenfolgen, vordefinierte
Herkunft, Vererbung, Übersetzungen, Imports, Queries und Wiederverwendung.
Es gibt keine neue öffentliche Fassade. Werte bleiben nullfrei, unveränderlich
und an ihren Graph gebunden; Funktions- und Datenbedingungen werden nicht
ausgeführt. Typqueries sehen die festen NAME-/URI-Grenzen aus Kapitel 05.

### Gebundene Übersetzungen (G2 B)

`TranslationLink` gehört zu `interlis-model` / `guru.interlis.model` und enthält
die snapshotgebundenen Original-/Übersetzungs-IDs. `ModelTranslation` ergänzt
`sourceTarget(): Optional<SymbolId>` und `correspondences(): List<TranslationLink>`
gemäss Kapitel 05. Die unveröffentlichte API 0.2 erhält direkt die fünfteilige
Konstruktion, keine parallele Fassade. `SemanticQueries.symbol()` macht die
Beziehungen über das gebundene `ModelSymbol` zugänglich; Namen und kanonische
Entsprechung bleiben getrennt. `ImportedTranslationContractTest` prüft die
gepinnten Originalfunde, öffentliche Bindungen, transitive und unveränderte
Imports, Namenskollisionen, Fehlerursachen und Fresh-/Reuse-Parität.


### G2 C: Geometrietoleranz und eigene Inspektionssegmente

Besitzer von `GeometryType.Tolerance`, `GeometryType.Explicit(DecimalLiteral)`
und `GeometryType.TrackWidth(SymbolId coordinateDomain)` ist **interlis-model /
guru.interlis.model**. `GeometryType.overlap()` liefert
`Optional<GeometryType.Tolerance>`. Kapitel 05 beschreibt Nullfreiheit,
Unveränderlichkeit, Snapshotbindung, genaue explizite Werte, die symbolische
Spurbreite und das Verhalten bei Vererbung/aufgeschobener Konkretisierung.
`TypeRelations.canExtend` und der Compiler verwenden dieselbe geometrische
Erweiterungsregel. Die bestehenden Struktur-, Typ-, Herkunfts- und
Ausdrucksverträge stellen auch eigene kontextualisierte Segmente dar; es gibt
keine zusätzliche öffentliche Segmentfassade. Vertragstests:
`GeometryRulesContractTest`, `GeometryInspectionContractTest`,
`LineFormBindingContractTest`; gemeinsame portable Fälle in
`stabilization-contract.cjs`. Eine ausgeführte Annahmeprobe belegt allein noch
keine vollständige graphische JVM-/TeaVM-Parität.


### G2 D: Bestehende Grafikverträge

`DrawingRuleSymbol.signatureClass` (interlis-model / guru.interlis.model) bleibt
die deklarierte OF-Klasse. MetaObjectValue, ParameterSymbol.owner/overrides,
Clause und EnumAssignment bewahren die gebundenen Identitäten und Reihenfolgen.
Die verantwortliche Klasse wird im jeweiligen Zuweisungskontext geprüft; es wird
kein einzelnes Verantwortlichkeitsfeld für die gesamte Regel eingeführt.
Kapitel 05 regelt Verantwortung, Pflichtparameter und die statischen Grenzen;
Kapitel 06 den Prüfzeitpunkt. `GraphicResponsibilityContractTest` und die
portablen Signaturfälle prüfen diesen Vertrag ohne Änderung der Konstruktoren
oder Einstiegspunkte.

### E1: ModelOptions

`ModelOptions` und der verschachtelte `TextValue` gehören zu interlis-model /
guru.interlis.model. ModelSyntax und ModelSymbol ergänzen `options()`;
ModelSymbol ergänzt `transferNamespace()`. Konstruktorverbraucher werden in der
unveröffentlichten Vorab-API 0.2 direkt angepasst. Optionals und Werte sind
nullfrei/immutable; Reihenfolge und Originalrevisionen folgen Kapitel 04/05.
ModelHeaderContractTest prüft Werte, Herkunft, Defaults, IANA-Aliasse,
Versionsfehler, gebundene Übersetzungsnamensräume, Konflikte und Wiederverwendung.
Die bestehenden Fabriken und Snapshot-/Schema-Verträge bleiben unverändert.

E1 ergänzt `ModelOptions.language(): Optional<TextValue>` mit der dokumentarischen
Modellsprache und Originalherkunft. `ModelSyntax.language()` ist daraus abgeleitet;
der redundante String-Konstruktorparameter entfällt in API 0.2. Modell- und
Übersetzungssprache bleiben getrennt. Eigene Sprachbezeichnungen bleiben zulässig.

E2 konkretisiert META-003/006/009/014 und SEM-005/010/014 gemäss Kapitel 05/06:
DomainSymbol.mandatory(), gebundene CLASS-Domänen, unverfälschte fehlerhafte
NumericTypeSyntax und QueryResult<UnitRelation> mit NOT_A_UNIT. Verbraucher
verwenden die gemeinsame Typrelation. UnitRelationContractTest und
DomainRestrictionContractTest prüfen Modell, Queries, Fehlursachen und Edits.
Die weiterführende Normabnahme bleibt in g2-rule-matrix.json ausdrücklich offen.

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

E3 (META-003/006/008/010/014, SEM-005/008/010/014/017, SYN-004/013/015,
TEST-011/012) ergänzt StructureValueTypeSyntax in guru.interlis.syntax und
TYPE_STRUCTURE_VALUE. StructureValueType.target() ist optional; restrictions()
und targetRoots() erhalten gebundene Identitäten. Kapitel 04/05/06 definiert
Nullfreiheit, Reihenfolge, Quell-/Snapshotbezug, Funktionskontext und Fehler.
StructureValueContractTest, die bisherigen View-/Inspektionsverträge und
DomainRestrictionContractTest prüfen Verbraucher und lange Ketten.

E2-Vertragspräzisierung: `TypeRelations.canExtend` prüft Collections rekursiv
mit der angegebenen LanguageVersion, Koordinatenerweiterung darf Dimensionen
ergänzen; `canAssign` bleibt hinsichtlich der Dimensionenzahl unverändert.
Vererbte Einheiten, Referenzen, Richtung und Rotation sind bereits im effektiven
öffentlichen Typ erhalten. TEXT-Literaltypen verwenden in beiden Versionen
die normative NFD-Länge. SharedExtensionContractTest / TextLiteralContractTest
prüfen Compiler, Queries, Imports und frische/wiederverwendete Analyse.

E4 in API 0.2: EnumArgumentType gehört zu interlis-model/guru.interlis.model,
EnumArgumentTypeSyntax zu interlis-compiler/guru.interlis.syntax. Beide sind
nullfrei und unveränderlich und unterscheiden ENUMVAL/ENUMTREEVAL über
treeValues. EnumerationType erhält treeValues als achten Konstruktorparameter
(siehe Kapitel 05). BuiltinFunctionExpressionSyntax.functionName() liefert
NameSyntax statt String; seine Kinder enthalten Name und Argumente in
Quellreihenfolge. Vollständige Standardsignaturen erscheinen als FunctionSymbol,
Aufrufe tragen targetFunction und gebundene NameReferences. Die Bindung gehört
zum Compilerlauf; veröffentlichte Werte bleiben snapshotgebunden.
E4EnumOperatorContractTest prüft Signaturen, Identitäten, Operatoren, Quellbereiche,
Imports, Übersetzungsgegenfälle und frische/wiederverwendete Analyse.

E4-Fortsetzung in API 0.2: FunctionCallExpression (interlis-model) erhält
Map<Integer, ArgumentPath> argumentPaths als achten Konstruktorparameter; normale
Aufrufe verwenden Map.of(). Seine verschachtelten ArgumentPath/ArgumentPathStep
Records besitzen die Daten und Bedingungen aus Kapitel 05. Referenzen gehören
zum gleichen Snapshot; keine zusätzliche Sitzungslebensdauer. LiteralExpressionSyntax
und ConstantExpressionSyntax (interlis-compiler) erhalten Optional<NameSyntax>
unit als letzten Parameter; ohne explizite Einheit Optional.empty(). Fehler
bleiben Compilerdiagnosen. FragmentSyntaxContractTest prüft SourceId, Revision,
UTF-16, Escape-Grenzen, Lexerfehler und Fragmentende.

E1/E4-Definiertheit in API 0.2: FunctionParameter(name,typeId,mandatory),
FunctionSymbol(header,parameters,returnType,returnMandatory),
FunctionDeclarationSyntax(...,returnType,returnMandatory) und
FunctionParameterSyntax(name,type,mandatory) bewahren die Pflichteigenschaft.
Werte sind nullfrei, immutable, parametergeordnet und snapshotgebunden wie
die bisherigen Typreferenzen. Verbraucher übergeben false nur bei tatsächlich
nicht pflichtigen Signaturen; es bestehen keine parallelen Konstruktoren.

E2 FORMAT ohne weitere öffentliche Signaturänderung: TypeRelations.canAssign und
canExtend verwenden denselben Prüfer wie Domänen-/Attributerweiterungen.
FormattedValues gehört nur intern zu interlis-model/guru.interlis.model.internal;
der bestehende Compilerverbraucher wird verschoben, keine Parallelimplementierung.
FormattedRelationContractTest prüft gebundene Präfixe/Overrides, verschachtelte
Grenzen, öffentliche Queries sowie Import-/Edit-/Wiederverwendungsfälle.

E2 numerische Vollständigkeit ergänzt die vorhandenen NumericType-, CoordinateType-,
DomainSymbol- und AttributeSymbol-Verträge ohne neue öffentliche Signatur. Legale
abstrakte Werte behalten ihren Typ und die Deklarationsflags; fehlerhafte explizite
Zielbindungen erhalten ErrorType mit vorhandenen Diagnosen. Aliaswerte bleiben
unveränderlich. Compiler und Querygegenfälle prüfen die veröffentlichten Werte.

E1 Wort-/Primitivprofile ändern keine öffentliche Signatur. SyntaxToken.kind,
Sprachabfragen und Rename beachten die gewählte Sprachversion. Standard-
Boolean-Ergebnisse tragen deren PredefinedOrigin, auch bei gemischten Modellen.
ANTLR-Klassifikation und Tokenvokabular bleiben intern.

E5 in API 0.2: SourceMetadata gehört zu interlis-compiler/guru.interlis.syntax
und besitzt verschachtelte Attribute(key,text,range) und Text(text,range).
SyntaxSnapshot erhält metadata als letzten Konstruktorparameter und eine
gleichnamige immutable Abfrage. MetaAttributeSet.Value und Documentation
gehören zu interlis-model/guru.interlis.model. Ergänzung L0B-3:
`MetaAttributeSet(Map<String,Value>,List<Entry>)`, `Entry(String,Value)`,
`entries(): List<Entry>` und `values(String): List<Value>` erhalten wiederholte
Funktionsparameter-Metadaten vollständig. Der einargumentige Map-Konstruktor und
`values(): Map<String,Value>` bleiben der Erstvorkommensindex. Signaturen,
Nullfreiheit, defensive Kopie, Reihenfolge, Konfliktprofil und Herkunft gemäss
Kapitel 05. FunctionDeclarationSyntax(...,explanation) und
FunctionSymbol(header,parameters,returnType,returnMandatory,explanation) werden
direkt angepasst. Leere Werte verwenden die vorhandenen empty-Fabriken bzw.
Optional.empty(); keine parallelen Alt-Fassaden. Quell-Metadaten besitzen die
Snapshotlebensdauer. Lesen und Binden erhält Abbruch/Diagnostik des Compilerlaufs.

`ConstraintDeclarationSyntax` (compiler/syntax) ergänzt `Optional<Plausibility>`
mit `DecimalLiteral percentage`, `BinaryOperator comparison` und Zahlenbereich
sowie `Optional<ExpressionSyntax> where` für SET. Kinder führen den Filter vor
der Hauptbedingung, unveränderlich und in ursprünglichen UTF-16-Bereichen.
`SetConstraint.SetConstraintScope` (model) hat GLOBAL/BASKET; GLOBAL ist keine
Beschränkung auf einen Transfer. `StructuredTranslationContractTest` prüft
Syntax, Modell, Korrespondenzen und Navigation; NumericLiteralBudgetContractTest
prüft kontrollierte Compiler-/Fragmentgrenzen.

Phase-2-Closure-Verträge werden zusätzlich durch `Phase2ClosureContractTest`
gesichert: übersetzte Rollen-ALL-OF-Korrespondenzen, ursprüngliche importierte
Metadaten-/Linienform-/Generic-Ziele und Reihenfolgen, gleichnamige getrennte
Generic-Domains, Fresh/Reuse nach Fehler und Erholung, geerbte Viewbasen mit
aktuellen Teilnehmerzielen sowie LineCoord-Quellabstraktion. Keine neue öffentliche API.

L3-1: compiler besitzt SemanticOverlays.OverlaySource und Target sowie die
inhaltlichen SemanticOverlay.TranslationOverlay(source,target,original),
CrsOverlay(source,target,crs) und MetaAttributeOverlay(source,target,attributes).
attributes ist Map<String,List<String>>, tief kopiert, Schlüssel lexikalisch,
Werte in Eingabereihenfolge. Target(QualifiedName,Optional<String> modelVersion,
Optional<SourceId> sourceHint) bindet eindeutig im tatsächlichen Inventar.
model besitzt Origin.OverlayOrigin und ModelTranslation.origin; die
Kompatibilitätskonstruktion mit fünf Parametern bleibt. Kapitel05/06 regeln
Herkunft/Präzedenz/Fehler/Fingerprints. SemanticOverlayContractTest prüft
öffentliche Wirkung, Fehler, Closure, Isolation und Fresh/Reuse.
