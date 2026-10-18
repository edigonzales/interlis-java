# API-Register und gemeinsame Vertragstests

## 1. Zweck und Autorität

Dieses Register ordnet die gemeinsamen Typen, Fabriken und Dienste eindeutig einem Modul und Fachvertrag zu. Die vollständigen Signaturen und Methodenbedingungen stehen im verlinkten Kapitel. Eine Tabellenbeschreibung hier ist kein Auftrag, daneben eine zweite anders benannte Implementierung einzuführen. Nicht öffentliche Hilfsmethoden dürfen im jeweiligen Arbeitspaket angepasst werden, sofern die beobachtbaren Verträge und Abnahmen erhalten bleiben.

Java-Blöcke in der Spezifikation zeigen Signaturen und Varianten; Bodies und Imports sind teilweise absichtlich ausgelassen. Das Repository MUSS zusätzlich tatsächlich kompilierende API-Beispiele enthalten. Die Spezifikation behauptet keine erfolgreiche Kompilierung der nur deklarativ abgebildeten Klassenblöcke.

## 2. Module, Packages und grundlegende Typen

| Modul | Package | Kanonische Typen | Definition |
|---|---|---|---|
| model | `guru.interlis.source` | `SourceId`, `SourceRevision`, `SourceText`, `SourceRange`, `SourcePosition`, `LineMap`, `TextEdit` | [04](04-quelltext-syntax-und-parser.md), Editvertrag [07](07-workspace-inkrementalitaet-und-lsp.md) |
| model | `guru.interlis.model` und fachliche Unterpackages | `SnapshotId`, `SymbolId`, `TypeId`, `ExpressionId`, `ScopeId`, `ReferenceId`, jeweilige `…Ref`, `SemanticGraph`, `CompiledSchema`, `SchemaFingerprint`, `SchemaProvenance` | [05](05-metamodell-und-typensystem.md) |
| model | `guru.interlis.model.query` | `QueryResult<T>`, `SemanticQueries`, `TypeRelations`, typisierte Gründe und Ergebnisse | [05](05-metamodell-und-typensystem.md) |
| model | `guru.interlis.diagnostics` | `Diagnostic`, `DiagnosticId`, `DiagnosticCode`, `DiagnosticArgument`, `RelatedLocation`, `Severity`, `Phase`, Katalogverträge | [09](09-diagnostik-logging-und-telemetrie.md) |
| model | `guru.interlis.execution` | `CancellationToken`, `CancellationReason`, `OperationCancelledException`, `RunObserver`, `RunEvent`, `RunId`, `RunContext`, `RunContextFactory`, `RunLifecycle`, `FailureId`, `ClockSource` | [09](09-diagnostik-logging-und-telemetrie.md), Ergebnisverwendung [06](06-kompilierung-und-semantik.md) |
| compiler | `guru.interlis.syntax` | `SyntaxServices`, `SyntaxService`, `ParseOptions`, `ParseMode`, `SyntaxSnapshot` und eigene Syntaxknoten | [04](04-quelltext-syntax-und-parser.md) |
| compiler | `guru.interlis.compiler` | `CompilerEngines`, `CompilerServices`, `CompilerEngine`, `CompilationRequest`, `CompilerOptions`, `AnalysisBudget`, `AnalysisSnapshot`, `AnalysisCoverage`, `AnalysisReuse`, `CompilationOutcome`, `RejectionSummary`, `RejectionReason` | [06](06-kompilierung-und-semantik.md) |
| compiler | `guru.interlis.compiler` | `ModelSelector`, `SourceSet`, `ImportSite`, `ImportBinding`, `ImportResolution`, `ModelLocation`, `ResolutionProblem`, `ResolutionFingerprint` | [06](06-kompilierung-und-semantik.md), [08](08-modellaufloesung-und-repositories.md) |
| compiler | `guru.interlis.compiler.query` | `LanguageQueries`, Completion-/Hover-/Definition-/Rename-Verträge | [07](07-workspace-inkrementalitaet-und-lsp.md) |
| workspace | `guru.interlis.workspace` | `Workspaces`, `Workspace`, `WorkspaceRevision`, `WorkspaceConfiguration`, `WorkspaceState`, `SourceUpdate`, `SourceExpectation`, `SourceChange`, `WorkspaceCapture`, `AnalysisAcceptance`, Receipts/Requests/Metrics | [07](07-workspace-inkrementalitaet-und-lsp.md) |
| workspace | `guru.interlis.workspace.resolution` | `SourceProvider`, `SourceInventory`, `ModelResolutionService`, `ResolutionRequest`, `ResolutionOutcome`, `PreparedClosure`, Kandidaten/Closure/Trace | [08](08-modellaufloesung-und-repositories.md) |
| repository | `guru.interlis.repository` | `RepositoryClient`, `SourceLoader`, `LoadedSource`, `FetchPolicy`, `ContentStore`, Index-/Refreshdienste | [08](08-modellaufloesung-und-repositories.md) |
| projection | `guru.interlis.projection` und Unterpackages | `DiagramProjectors`, `DiagramProjector`, `DiagramOptions`, `DiagramDocument`, Diagramm-/Notice-Typen | [11](11-visualisierung-und-projektionen.md) |
| projection | `guru.interlis.projection.export` | `ModelExporter`, `ModelExporters`, `ExporterSelection`, `ExportFormat`, `ExportProfile`, `ExportOptions`, `ExportOutcome`, `ExportDocument` | [18](18-cli-sdk-und-kompatibilitaetsadapter.md) |
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
| Modellexport | `ModelExporters.forFormat(format, profile)` | konkrete Registryauswahl mit explizit ausgewiesenem nicht unterstütztem Format |
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
