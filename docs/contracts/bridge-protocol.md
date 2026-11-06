# Compiler-Bridge und portabler Host

Die gemeinsame Bridge bedient TeaVM und Web Image mit dem Protokoll `interlis.compiler/1.0`. Browser- und Buildanleitungen stehen in [Browserclient](../usage/browser.md) und [Build](../development/build.md).

<a id="gemeinsame-bridge-und-klassengrenzen"></a>
## Gemeinsame Bridge und Klassengrenzen

Ein Browser muss nicht den gesamten Java-Objektgraphen spiegeln. Er verwaltet Quellen und Sitzungen und fragt geeignete, versionierte Projektionen ab. Das Protokoll ist zugleich für Tests und andere Hosts verwendbar.

| Klasse | Verantwortung | Wesentliche Methoden |
|---|---|---|
| `BridgeDispatcher` | Validiert und verteilt eine Protokollanfrage. | `String handle(String requestJson)` |
| `BridgeCodec` | Explizite, getestete JSON-Kodierung ohne notwendige Runtime-Reflection. | `decodeRequest`, `encodeResponse`, `encodeDiagnostic` |
| `SessionRegistry` | Begrenzter Besitz von Workspaces pro geladener Runtime. | `open`, `require`, `close`, `closeAll` |
| `CompilerSessionFacade` | Domainoperationen einer Sitzung. | `updateSource`, `updateSources`, `removeSource`, `setConfiguration`, `analyze`, `compile`, `query`, `diagram`, `releaseSnapshot` |
| `SnapshotRegistry` | Hält abfragbare Snapshots und deren Referenz-/Evictionstatus. | `retain`, `require`, `release`, `evictUnpinned` |
| `TeaVmEntryPoint` | TeaVM-spezifischer Export der Bridge. | `main`, exportiertes `handle` |
| `WebImageEntryPoint` | Web-Image-spezifischer Bootstrap derselben Bridge. | `main`, Bootstrap des `handle`-Callbacks |
| `CompilerClient` | TypeScript-/JavaScript-Client im Host. | Promise-basierte Sitzungs-/Querymethoden und Worker-Lebenszyklus. |
| `CompilerWorkerHost` | Worker-Queue, Epochennummer, Abbruch und Wiederherstellung. | `dispatch`, `cancel`, `restart`, `dispose` |

Die Bridge ist ein eigenes Modul. Sie verwendet Workspace und Projektion. Beide Backendadapter verwenden dieselbe Bridge.

`SessionRegistry.open` benutzt dieselbe öffentliche Konstruktion wie JVM-Verbraucher: `CompilerEngines.create(...)` und `Workspaces.create(engine, configuration)`. Die Bridge darf keinen zweiten verkürzten Compiler oder eine durch Gate-Umgehung erzeugte Schemainstanz besitzen. Eine leere Sitzung bleibt bis zu explizit übergebenen Quellen ohne I/O.

Für einen Request mit Auflösungsbericht verwendet die Facade `workspace.capture(request, token)`, die Engine mit `capture.request()` und `capture.reuse()` und danach `workspace.acceptAnalysis(capture, analysis)` gemäss [Workspace-Vertrag](workspace-and-lsp.md). Sie übernimmt bei regulär abgeschlossener Analyse sowie bei Compile-`Succeeded` und `Rejected`; Abbruch und interner Fehler übernehmen keinen vollständigen Reuse-/Abhängigkeitsstand. Der synchrone Bridgepfad serialisiert die Sitzung bereits. Ein späterer asynchroner Host muss `STALE_NO_COMMIT` wie der LSP behandeln und darf daraus keinen aktuellen Snapshotzeiger veröffentlichen. Das Registrierungssystem kann einen ausdrücklich angeforderten historischen Snapshot weiter halten, muss dessen unveränderten Herkunftsstand mitliefern.

Die Facade übergibt einen frischen `RunContext` ausdrücklich an `engine.analyze(request, reuse, context, token)` beziehungsweise den Compile-Overload. Nur die Engine erzeugt Start und Abschluss dieser RunId. Für einen eigenen übergeordneten Bridge-/Beschaffungslauf erzeugt der Host einen separaten Kindkontext über `RunContextFactory.createChild(parent)`. Request-ID, Session-ID und Worker-Epoche werden nach dem begrenzten Korrelationsvertrag aus [Diagnosevertrag](diagnostics.md) zugeordnet; weder ThreadLocals noch ein bereits gestarteter Hostkontext dienen als verborgener Engineparameter. Der Conveniencepfad darf die Kontextfabrik der Engine nutzen.

`handle` arbeitet synchron und führt keine Callback-I/O aus. Es gibt genau einen JSON-String zurück. Fachlich fehlerhaftes INTERLIS ist eine erfolgreiche Protokolloperation mit Diagnostik. Fehler beim Dekodieren oder bei Sitzungs-/Revisionsverträgen sind Bridgefehler. Unerwartete interne Ausnahmen werden mit FailureId protokolliert und als `INTERNAL_FAILURE` zurückgegeben, ohne beliebige Javaobjekte oder Stacktraces in die fachliche Antwort zu serialisieren.

Ein fataler Runtime-Trap oder eine nicht mehr sicher behandelbare Speichererschöpfung kann eine JSON-Antwort verhindern. Der Host muss diesen Ausgang als `RuntimeFailure` der konkreten Anfrage erfassen und die Workerinstanz verwerfen; er darf keine erfolgreiche leere Antwort erfinden. Dieser Hostausgang ist von einem regulär kodierten Bridgefehler zu unterscheiden.

Alle Operationen einer Sitzung werden serialisiert. Eine Runtime darf mehrere isolierte Sitzungen halten; das stellt keine Multicore-Ausführungsgarantie dar. Eine Sitzung darf keine referenzierten Javaobjekte einer anderen Sitzung akzeptieren.

<a id="protokoll-interliscompiler-version-10"></a>
## Protokoll `interlis.compiler` Version 1.0

<a id="nachrichtenhülle"></a>
### Nachrichtenhülle

Eine Anfrage hat exakt diese Pflichtfelder und abhängig von der Operation eine Sitzung:

```json
{
  "protocol": "interlis.compiler",
  "protocolVersion": "1.0",
  "requestId": "r-17",
  "sessionId": "s-4",
  "operation": "analyze",
  "params": {
    "expectedWorkspaceRevision": "12",
    "roots": [{"kind": "byName", "modelName": "Survey"}],
    "mode": "editor"
  }
}
```

`roots` wird als `ModelSelectorDto` validiert. Variante `byName` enthält `modelName` als Pflichtfeld und optional `modelVersion` sowie `sourceHint`. Variante `bySource` enthält `source` und wählt die in [Compilervertrag](compiler-and-model.md) definierte Modellmenge dieser Quelle. `modelVersion` ist die fachliche Modellversion. Die erlaubten Sprachversionen 2.3/2.4 stehen unabhängig davon in `CompilerOptions.allowedLanguages`. Der Decoder bildet exakt auf den Selectorvertrag aus [Compilervertrag](compiler-and-model.md) ab; fehlende Optionals werden durch abwesende Felder dargestellt.

Eine erfolgreiche Antwort:

```json
{
  "protocol": "interlis.compiler",
  "protocolVersion": "1.0",
  "requestId": "r-17",
  "sessionId": "s-4",
  "status": "ok",
  "result": {
    "workspaceRevision": "12",
    "snapshotId": "snap-8",
    "analysisState": "partial",
    "sources": [{"sourceId": "memory:/Survey.ili", "revision": "3"}],
    "diagnostics": [],
    "coverage": {"requestedRootsChecked": false},
    "missingModels": []
  }
}
```

Dieses kleine Antwortbeispiel illustriert die Hülle; eine echte partielle Analyse muss ihre konkrete Coverage und ihre Gründe nach [Compilervertrag](compiler-and-model.md) übertragen. Eine leere Diagnoseliste ist für sich allein kein Gültigkeitsbeweis. `requestedRootsChecked` darf nur nach dem tatsächlichen Gate true sein und wird bei gewöhnlicher EDITOR-Analyse nicht als Ersatz für dieses Gate verwendet.

Bei `status: "error"` steht anstelle von `result` ein Objekt `error` mit `code`, `messageKey`, getaggten Argumenten und optionalen strukturierten Details. Bei `status: "cancelled"` enthält `cancellation` einen Grund und den letztbekannten unveränderten Workspace-Stand. `null` wird nicht als unspezifischer Sammelwert für Fehler, fehlende Quelle und nicht analysierten Inhalt verwendet.

`protocolVersion` wird getrennt von Tool-, Sprach-, Diagramm- und Datenbankprofilversionen geführt. Unbekannte Majorversionen werden abgelehnt. Innerhalb Major 1 dürfen additive optionale Antwortfelder ignoriert werden. Unbekannte Operationsnamen und unbekannte diskriminierende Varianten in Pflichtdaten werden als Fehler behandelt. JSON-Schemas und TypeScript-Typen sind neben den Java-DTOs zu pflegen; Vertragstests verhindern deren Auseinanderlaufen.

<a id="operationen-und-methodenkontrakte"></a>
### Operationen und Methodenkontrakte

| Operation | Eingabe | Ergebnis und Vertrag |
|---|---|---|
| `capabilities` | Keine sessionId; gewünschte Protokollversion. | Tool-/Bridgeversion, Runtimeziel, unterstützte Operations-/Query-/Projektionsarten, Sprachabdeckung, Limits. Kein Sitzungszustand. |
| `openSession` | Konfiguration, Limits, optional fertiges Quellenpaket. | Neue sessionId, WorkspaceRevision `0` bei leerem Bestand oder `1` nach atomarer nichtleerer Initialladung, angenommene Limits. Schlägt Initialvalidierung fehl, wird keine Teilsitzung gehalten. |
| `updateSource` | SourceId, Expectation (`absent`/`at`), `replace` oder `edits`. | UpdateReceipt entsprechend [Workspace-Vertrag](workspace-and-lsp.md); exakte Revisionen als Strings. |
| `updateSources` | Erwartete WorkspaceRevision, geordnete Liste von SourceUpdates. | `BatchUpdateReceipt` aus dem atomaren `Workspace.applyBatch`; kein Teilcommit. |
| `removeSource` | SourceId und erwartete SourceRevision. | Atomare Entfernung oder Versionsfehler. |
| `setConfiguration` | Erwartete WorkspaceRevision, vollständig neue Konfiguration. | Neue Revision/Fingerprints oder No-op; keine implizite Repositoryabfrage. |
| `analyze` | Erwartete WorkspaceRevision, Wurzeln, Analyseumfang. | SnapshotId, Coverage, Diagnostik und explizit fehlende Modellanforderungen. Kein Produktionsschema. |
| `compile` | Erwartete WorkspaceRevision, Wurzeln, Compileroptionen gemäss Konfiguration. | `outcome: succeeded` mit schemaId/snapshotId oder `outcome: rejected` mit snapshotId/Diagnostik und unveränderter RejectionSummary des Gates. Interner Fehler und Abbruch bleiben getrennt. |
| `query` | SnapshotId und getaggte Query aus [Workspace-Vertrag](workspace-and-lsp.md). | Getaggtes QueryResult; keine verdeckte Neuanalyse eines anderen Stands. |
| `diagram` | SnapshotId oder geprüfte schemaId, versionierte Diagrammoptionen. | DiagramDocument aus [Projektionsvertrag](projection.md) oder explizite Capability-/Inputfehler. |
| `releaseSnapshot` | SnapshotId. | Idempotente Freigabe des Client-Halteanspruchs; danach darf der Snapshot entfernt sein. |
| `closeSession` | SessionId. | Idempotenter Abschluss; alle gehaltenen Handleansprüche dieser Sitzung erlöschen. |

Alle sitzungsgebundenen Antworten enthalten die sessionId. Queryantworten enthalten die SnapshotId. Antworten aus einer anderen Worker-Epoche werden vom Host verworfen. `schemaId` ist ein opaker Handle auf ein erfolgreiches `CompiledSchema`, kein serialisierter Objektzeiger. Seine Lebensdauer ist an den dazugehörigen gehaltenen Snapshot gebunden.

`result.rejection` ist bei `outcome: rejected` Pflicht und bildet die `RejectionSummary` aus [Compilervertrag](compiler-and-model.md) verlustfrei ab:

| Wire-Feld | Typ und Vertrag |
|---|---|
| `reasons` | Nichtleeres, kanonisch sortiertes Array ohne Duplikate aus `SOURCE_ERRORS`, `WARNINGS_REJECTED`, `MISSING_INPUT`, `AMBIGUOUS_INPUT`, `ENVIRONMENT_FAILURE`, `INCOMPLETE_SUPPORT`. |
| `diagnosticIds` | Geordnete IDs der relevanten kanonischen Diagnosen des gelieferten Snapshots. |
| `requestedRoots` | Ursprüngliche ModelSelectorDto-Liste des öffentlichen Compile-Aufrufs, ohne Ergänzung oder Umformung. |
| `resolvedRoots` | ModelSelectorDto-Liste aus `RejectionSummary.requestedRoots`: die vom Workspace für den Engine-Aufruf konkretisierten Selektoren, einschliesslich aufgelöster Version/Quellenhinweise. |
| `unsupportedFeatureIds` | Stabile Feature-IDs der blockierenden noch nicht implementierten Spracheigenschaften; andernfalls leeres Array. |

Beide Wurzellisten sind strukturierte DTOs (`byName` mit optionalen `modelVersion`/`sourceHint` beziehungsweise `bySource` mit `source`), keine Java-Strings. `resolvedRoots` erhält die unveränderte Wurzelliste der Gate-Summary; `requestedRoots` erhält zusätzlich die Eingangsmetadaten des öffentlichen Aufrufs. Der Adapter trifft dafür keine fachliche Gateentscheidung.

Diese Summary wird ausschliesslich vom CompilationGate erzeugt und ist auf die angeforderte Wurzelhülle bezogen. Die Bridge und der Client dürfen Gründe weder aus globalen Diagnosen noch aus lokalisierten Meldungstexten neu berechnen. Insbesondere bleiben ein Modellfehler, wegen Policy abgelehnte Warnungen, fehlende Quellen, Umgebungsfehler und unvollständige Compilerunterstützung unterscheidbar. Eine protokolltechnisch erfolgreiche `compile`-Operation kann deshalb `status: ok` und zugleich `outcome: rejected` tragen.

Fehlende Modellanforderungen und deren Locator/Provenienz stammen aus `WorkspaceCapture.resolution().pendingFetches()` beziehungsweise dessen PreparedClosure-Bericht nach [Workspace-Vertrag](workspace-and-lsp.md) und [Repositoryvertrag](repositories.md). Dieser Bericht muss zu demselben eingefrorenen Eingabestand wie die Analyseantwort gehören. Die Bridge betreibt keine zweite Header-/Resolverimplementierung, um `missingModels` zu erzeugen.

`query.params.query.kind` ist eine feste Union: `symbolAt`, `definition`, `semanticProperties`, `hover`, `completion`, `references`, `prepareRename`, `rename`. Jede Variante besitzt ihr eigenes JSON-Schema. Der Server kündigt nur implementierte Varianten an. Ein Positionquery enthält SourceId, SourceRevision und UTF-16-Offset; ein Symbolquery enthält SnapshotId und SymbolId. Nicht unterstützte P3-Queries werden als `UNSUPPORTED_OPERATION` abgelehnt und niemals mit einer irreführend leeren Trefferliste beantwortet.

Für `diagram` ist genau eines von `snapshotId` und `schemaId` erforderlich.
Eine fehlende `selection` verwendet die belegten Wurzelmodelle dieses Eingangs;
eine ausdrücklich leere, nullwertige oder falsch typisierte Auswahl ist ungültig.
Das optionale Feld `options` folgt dem Schema
`interlis-wasm-bridge/src/main/resources/guru/interlis/bridge/diagram-options-1.0.schema.json`.
Ist es vorhanden, verlangt es `schemaVersion: "1.0"`. Es übernimmt `scope`,
`visibleKinds`, `inheritedMembers`, `showTypes`, `showRoleCardinalities`,
`showConstraints`, `showImports`, `labelPolicy`, `associationPresentation`
`translationModel`, `neighbourhoodDepth` und `budget` aus dem gemeinsamen `DiagramOptions`-Vertrag in [Projektionsvertrag](projection.md);
ausgelassene Felder verwenden ausschliesslich dessen Defaults.
Unbekannte Felder, falsche Typen und ungültige Grenzen sind `INVALID_REQUEST`.
Unbekannte Optionsversionen und ausdrücklich nicht angebotene Darstellungsoptionen
liefern `UNSUPPORTED_OPERATION`; sie werden nicht still ignoriert.
`capabilities.diagramOptions` nennt die angebotene Version und Darstellungsprofile.
Ein angenommener Budgetwert kann `LIMITED` erzeugen, niemals eine verdeckt
verkleinerte vollständige Ausgabe. Die TypeScript-Form heisst `DiagramOptionsDto`. `translationModel` ist ein SymbolRefDto mit `snapshot`/`symbol`; der Client übersetzt auch diesen öffentlichen Snapshot-Handle in den aktuellen Worker-Handle. Fremde oder alte Handles werden vor der Übernahme abgelehnt. `neighbourhoodDepth` ist eine JSON-Ganzzahl von0 bis32. Beide Modi verlangen die expliziten Parameter aus [Projektionsvertrag](projection.md).

`CompilerClient.closeSession` ist für dasselbe eigene Handle idempotent,
auch bei gleichzeitig angeforderten Abschlüssen. Die Implementierung benötigt
keine stark gehaltene unbeschränkte Liste abgeschlossener Sitzungs-IDs.
Andere Operationen akzeptieren ein geschlossenes Handle nicht; fremde Handles
bleiben ungültig. Nach `dispose` gilt weiterhin der geschlossene Clientvertrag.

<a id="beispiel-einer-bedingten-quelländerung"></a>
### Beispiel einer bedingten Quelländerung

```json
{
  "protocol": "interlis.compiler",
  "protocolVersion": "1.0",
  "requestId": "r-18",
  "sessionId": "s-4",
  "operation": "updateSource",
  "params": {
    "sourceId": "memory:/Survey.ili",
    "expectation": {"kind": "at", "revision": "3"},
    "change": {
      "kind": "edits",
      "edits": [{"startOffset": 120, "endOffset": 126, "text": "Building"}]
    }
  }
}
```

Diese Edits folgen dem Core-Vertrag: alle beziehen sich auf Revision 3. Der Browserclient darf eine LSP-Änderungsliste nicht ungeprüft in diese Form kopieren. Änderungen müssen auch nach einer Worker-Wiederherstellung mit der neu aufgebauten SourceRevision abgeglichen werden.

Die Transportebene verspricht keine magische Exactly-once-Semantik. Request-IDs sind im Host pro Worker-Epoche eindeutig. Parallel doppelt eingereichte IDs werden zurückgewiesen. Bedingte Mutationen verhindern doppelte Anwendung nach unklarer Antwort: Eine Wiederholung mit bereits überholter Ausgangsrevision erhält `VERSION_CONFLICT`; der Host prüft den aktuellen Zustand, statt blind eine neue Ausgangsrevision einzusetzen.

<a id="fehlercodes"></a>
### Fehlercodes

Bridgefehler sind getrennt von Compilerdiagnosen wie `ILI-SEM-0001`:

| Code | Bedeutung | Mutation erlaubt? |
|---|---|---|
| `INVALID_JSON` | Eingabe ist kein gültiges JSON. | Nein. |
| `INVALID_REQUEST` | Pflichtfelder, Typen, Bereiche oder Operationsparameter ungültig. | Nein. |
| `UNSUPPORTED_PROTOCOL` | Nicht unterstützte Protokollversion. | Nein. |
| `UNSUPPORTED_OPERATION` | Bekannte, derzeit nicht verfügbare Fähigkeit oder unbekannte Operation. | Nein. |
| `SESSION_NOT_FOUND` | Sitzung fehlt oder ist abgeschlossen. | Nein. |
| `VERSION_CONFLICT` | Erwarteter Quell-/Workspace-Stand stimmt nicht. | Nein. |
| `SNAPSHOT_EXPIRED` | Snapshot wurde freigegeben oder aus dem ungeschützten Cache entfernt. | Nein. |
| `SNAPSHOT_MISMATCH` | Ref oder Quelle gehört zu anderem Snapshot/Stand. | Nein. |
| `LIMIT_EXCEEDED` | Konfiguriertes Nachrichten-, Quellen- oder Sitzungsbudget überschritten. | Nein beziehungsweise kein Commit der begonnenen Operation. |
| `INTERNAL_FAILURE` | Unerwarteter Implementierungsfehler mit FailureId. | Keine Veröffentlichung eines halben Ergebnisses. |

Fehlerdetails dürfen erwartete/beobachtete Revision, zulässige Grenzen und Feldpfade enthalten. Quelltext oder unbekannte JSON-Inhalte werden nicht pauschal in Fehlermeldungen und Logs kopiert. Diagnoseargumente werden nach dem getaggten Vertrag aus [Diagnosevertrag](diagnostics.md) kodiert.

`LIMIT_EXCEEDED` bezeichnet die Bridge-/Sitzungsgrenze vor einem Commit. Ein ausgeschöpftes `AnalysisBudget` folgt dagegen dem gemeinsamen Ausführungsvertrag: `analyze` liefert `status: cancelled` und `reason: RESOURCE_LIMIT`, gegebenenfalls mit ausdrücklich unvollständiger Coverage. `compile` liefert gemäss dem geschlossenen `CompilationOutcome` aus [Workspace-Vertrag](workspace-and-lsp.md) eine erfolgreiche Protokollhülle (`status: ok`) mit `outcome: cancelled` und demselben ursprünglichen Abbruchgrund. Dies gilt auch für frühe Capture-Abbrüche vor dem Engine-Einstieg; es entsteht kein Schema. Andere CancellationReasons sind `USER_REQUESTED`, `SUPERSEDED`, `DEADLINE_EXCEEDED` und `HOST_SHUTDOWN`. UI-Diagrammbegrenzungen nach [Projektionsvertrag](projection.md) haben den gesonderten Status LIMITED und dürfen nicht mit einer abgebrochenen Sprachprüfung verwechselt werden.

<a id="präzision-und-json"></a>
### Präzision und JSON

IDs sind opake Strings. `SourceRevision`, `WorkspaceRevision`, Zähler mit Java-`long`-Wertebereich und beliebig grosse fachliche Ganzzahlen werden als Dezimalstrings übertragen. Sie werden nie zunächst in JavaScript-`Number` umgewandelt. UTF-16-Offsets, Zeilen, Spalten und kleine Enum-/Schemawerte bleiben geprüfte nicht negative JSON-Ganzzahlen innerhalb des Java-`int`-Bereichs.

Ein allgemeiner fachlicher Dezimalwert wird als `ExactDecimalDto` mit `unscaledValue` als Dezimalstring und `scale` als vorzeichenbehafteter 32-Bit-Ganzzahl übertragen. `raw` und die INTERLIS-spezifischen Genauigkeits-/Exponenteninformationen des `DecimalLiteral` aus [Modellvertrag](compiler-and-model.md) bleiben zusätzliche typisierte Metadaten. So erzwingt ein sehr grosser Exponent nicht die Materialisierung einer riesigen ausgeschriebenen Dezimalzeichenkette.

```json
{
  "kind": "decimal",
  "unscaledValue": "90071992547409930010",
  "scale": 4,
  "raw": "9007199254740993.0010"
}
```

Dieser Wert darf auf der JavaScript-Seite zur Anzeige formatiert oder mit einer geeigneten Dezimalbibliothek verarbeitet werden. Eine Konversion zu `Number` ist keine zulässige Grundlage für Compilerentscheidungen, Gleichheitsprüfungen oder Golden-Vergleiche. Diagnoseargumente mit DECIMAL folgen dem eigenen Darstellungsschema aus [Diagnosevertrag](diagnostics.md) und bleiben ebenfalls exakt.

JSON-Escaping, Surrogatpaare, Control Characters und Zeilenwechsel werden durch den Codec geprüft. Serialisierte Reihenfolgen sind deterministisch. Das Protokoll verwendet kein Java-Objektserialisierungsformat, keine Klassennamen als Typdiscriminator und keine Reflection-basierte polymorphe Instanziierung aus unkontrollierten Klassennamen.

<a id="worker-abbruch-und-externe-quellen"></a>
## Worker, Abbruch und externe Quellen

<a id="host-api"></a>
### Host-API

Der TypeScript-Client bietet mindestens diese Methoden mit Promise-Ergebnissen:

```typescript
interface CompilerClient {
  openSession(options: SessionOptions): Promise<SessionHandle>;
  updateSource(session: SessionHandle, update: SourceUpdateDto): Promise<UpdateReceiptDto>;
  updateSources(session: SessionHandle, request: BatchSourceUpdateDto): Promise<BatchUpdateReceiptDto>;
  removeSource(session: SessionHandle, request: RemoveSourceDto): Promise<UpdateReceiptDto>;
  setConfiguration(session: SessionHandle, request: SetConfigurationDto): Promise<ConfigurationReceiptDto>;
  analyze(session: SessionHandle, request: AnalysisRequestDto, run?: RunOptions): Promise<AnalysisResultDto>;
  compile(session: SessionHandle, request: CompileRequestDto, run?: RunOptions): Promise<CompilationResultDto>;
  query(session: SessionHandle, request: QueryRequestDto, run?: RunOptions): Promise<QueryResultDto>;
  diagram(session: SessionHandle, request: DiagramRequestDto, run?: RunOptions): Promise<DiagramDocumentDto>;
  releaseSnapshot(session: SessionHandle, snapshotId: string): Promise<void>;
  closeSession(session: SessionHandle): Promise<void>;
  cancel(requestId: string): Promise<CancelDisposition>;
  dispose(): Promise<void>;
}

interface RunOptions {
  requestId?: string;
  signal?: AbortSignal;
}
```

`BatchSourceUpdateDto` enthält `expectedWorkspaceRevision` und die SourceUpdateDto-Liste. `RemoveSourceDto` enthält SourceId und erwartete SourceRevision. `SetConfigurationDto` enthält die erwartete WorkspaceRevision und die vollständige neue WorkspaceConfigurationDto, einschliesslich Compileroptionen, ResolutionPolicy, eingefrorenem ResolverCatalog und CacheLimits aus [Workspace-Vertrag](workspace-and-lsp.md). Revisionen werden jeweils als Dezimalstrings kodiert. Der Client enthält keine Sprachregeln. Er besitzt eine Hostkopie der angenommenen Quelltexte und Konfiguration, damit eine verlorene Workerinstanz wiederhergestellt werden kann.

Ohne vorgegebene `requestId` erzeugt der Client eine innerhalb der Worker-Epoche eindeutige Kennung. Der Aufrufer kann entweder eine eigene Kennung vorgeben und `cancel(id)` benutzen oder über `AbortSignal` abbrechen. So bleibt Abbruch auch erreichbar, obwohl das öffentliche Resultat eine Promise ist. Ein bereits abgebrochenes Signal startet keine Berechnung.

<a id="tatsächliches-abbruchverhalten"></a>
### Tatsächliches Abbruchverhalten

Ein synchron laufendes Wasm-`handle` kann nicht allein deshalb eine spätere `postMessage`-Abbruchnachricht empfangen, weil Java einen CancellationToken besitzt. Der Host verwendet deshalb diese Strategie.

1. Noch nicht gestartete Anfrage: aus der Worker-Queue entfernen und `cancelled` antworten.
2. Laufende Anfrage ohne geprüften kooperativen Kanal: Worker beenden.
3. Neue Worker-Epoche starten, Module laden und bestätigte Hostquellen/Konfiguration in neuer Sitzung aufbauen.
4. Alte Antwort-/Snapshot-/Schemahandles ungültig machen; neue IDs an den Client melden.
5. Aktuell benötigte Analyse erneut ausführen.

`CancelDisposition` unterscheidet `notStarted`, `cooperative`, `workerRestarted`, `alreadyCompleted` und `unknownRequest`. Bei einem Neustart darf keine Cross-Session-SymbolId-Stabilität behauptet werden. Die UI kann Layout über ausdrücklich als heuristisch gekennzeichnete fachliche Schlüssel wiederzuordnen versuchen; semantische Referenzen werden neu angefordert.

Später sind kooperative Yielding-Punkte oder ein geprüfter Shared-Memory-Abbruchkanal zulässig. SharedArrayBuffer darf keine versteckte Pflicht werden; benötigte Hosting-Header und die echte Browsermatrix müssen dann dokumentiert werden. Solche Optimierungen verändern den Cancellation-Vertrag nicht.

<a id="modelle-nachladen"></a>
### Modelle nachladen

Die Bridge führt weder `fetch` noch Dateidialoge aus. Eine Analyse mit fehlenden Quellen gibt konkrete Modellanforderungen und Diagnostik zurück. Der Host löst diese anhand einer ausdrücklich konfigurierten Repository-/Quellenstrategie auf, installiert die gefundenen Quellen und startet eine neue Analyse mit neuer WorkspaceRevision.

Ein solcher Ablauf besitzt Grenzen für Nachladerunden und erkennt wiederholte identische Anforderungen. Er darf bei zyklischen Imports oder einem unerreichbaren Modell keine Endlosschleife erzeugen. Später verfügbare Quellen invalidieren negative Abhängigkeiten nach [Workspace-Vertrag](workspace-and-lsp.md). Der Host protokolliert Quelle, Ergebnis, Cachezustand und Auflösungsgrund; Compilerdiagnostik bleibt deterministisch aus den übergebenen Eingaben ableitbar.

<a id="speicher-und-limits"></a>
### Speicher und Limits

`SessionLimits` besitzt mindestens `maxSourceUtf16Length`, `maxTotalSourceUtf16Length`, `maxSources`, `maxRetainedSnapshots`, `maxMessageUtf16Length` und `maxConcurrentSessions`. Jeder Wert wird bei `openSession` geprüft und in der Antwort als tatsächlich angenommenes Limit zurückgegeben.

Die Nachrichtenlänge wird vor dem vollständigen JSON-Dekodieren geprüft. Eine neue Quelle beziehungsweise eine Snapshotregistrierung wird erst nach allen zugehörigen Budgetprüfungen übernommen. Kann ein neues Ergebnis wegen ausschliesslich explizit gehaltener Snapshots nicht registriert werden, erhält der Client einen erklärten Limitfehler und kann einen alten Handle freigeben; gehaltene Handles werden nicht still zwangsweise entfernt.

Der Host verwendet dokumentierte konservative Standardwerte. Sie sind Betriebsgrenzen dieses Hosts, keine Sprachgrenzen von INTERLIS. Die Werte werden nach den ersten echten Modellsätzen festgelegt und als Konfiguration gespeichert; sie werden nicht als vermeintliche Browser-Maximalleistung verkauft. Grenzüberschreitungen liefern `LIMIT_EXCEEDED`, keine unvollständige scheinbar gültige Analyse.

Der Client gibt alte Snapshots nach Ablösung frei. Ein für eine laufende Query gehaltener Snapshot wird bis zum Ende dieser Query nicht entfernt. Die Freigabe eines Snapshots darf andere Snapshots nicht verändern. Vollständige Parse-Trees, Quelltextkopien, JSON-Strings und DTOs sind bei Speicheranalysen getrennt zu zählen; nur die Grösse des `.wasm`-Files zu messen genügt nicht.

<a id="deterministischer-ergebnisvergleich"></a>
### Deterministischer Ergebnisvergleich

`PortableFixtureRunner` nimmt dieselben Fixturebytes, Wurzeln, Optionen und Änderungsschritte entgegen. Er erzeugt eine normalisierte fachliche Projektion. Snapshot- und Symbol-IDs dürfen zwischen unabhängigen Sitzungen variieren und werden anhand eindeutiger fachlicher Testanker normalisiert. Quellbereiche, Deklarationsreihenfolgen, Typwerte, Diagnosecodes, Argumente und Ursachebeziehungen werden nicht weg-normalisiert.

Zeitstempel, Runtimeziel und Laufzeiten liegen ausserhalb der fachlichen Golden-Datei. Fehler werden nicht dadurch verborgen, dass gesamte Diagnoselisten oder komplizierte Modellteile aus dem Vergleich entfernt werden. Eine bewusst noch nicht unterstützte Spracheigenschaft benötigt eine explizite Phasen-/Capability-Erwartung.

<a id="messungen-und-ergebnismanifest"></a>
### Messungen und Ergebnismanifest

Jeder Portabilitätslauf erzeugt ein maschinenlesbares Manifest mit Quellcommit, Toolchainständen, Artefakthashes, Fixture-/Konfigurationshash, Betriebssystem/Architektur, Browser-/Node-Version, Flags, Teststatus und Fehlerdetails. Der Status unterscheidet `passed`, `failed`, `blocked` und `notRun`.

Das tatsächlich ausgelieferte Browserverzeichnis enthält ein
`build-manifest.json`: Commit und Quellfingerprint, Hash und Bytezahl jeder
ausgelieferten Ressource sowie getrennte Werkzeug-/API-/Diagnose-/Bridge-/
Formatierungs-/Diagrammschemaversionen. Fähigkeiten werden aus dem tatsächlich
gebauten Wasm-Modul gelesen und mit dem SDK-Versionsmanifest verglichen.
Aktuelle Laufzeitprüfungen schützen die geladenen Artefakthashes gegen
Änderungen während des Laufs. Die Compiler-Freigabeprüfung vergleicht
diese Lieferung zusätzlich mit zwei separaten sauberen Builds; ein Bericht
eines anderen Commits oder Artefakts genügt nicht.

Die Performanceprüfung misst mindestens kalten Modulstart, erste Analyse, wiederholte unveränderte Analyse, Ein-Datei-Änderung und mehrfaches Editieren einer Sitzung. Median und Streuung werden bei ausreichend wiederholten Läufen angegeben. Der Vergleich nennt Messgrenzen: Download, Modulinstanziierung, JSON-Kodierung, Workerkommunikation und reine Compilerzeit sind getrennte Abschnitte.

Die aktuelle [Freigabeprüfung](../development/release.md) bindet Antwortzeit- und Speicherbudgets an den vollen Modellkorpus. Ergebnisse kleiner Syntaxproben gelten nur für ihre Eingaben.

<a id="explizite-auswahlpolicy-in-der-wire-konfiguration"></a>
### explizite Auswahlpolicy in der Wire-Konfiguration

`openSession.configuration` und der vollständige Ersatz durch `setConfiguration`
akzeptieren zusätzlich `resolutionPolicy`. Dies ist reine DTO-Konvertierung;
Headerauswahl und Originalbindung erfolgen ausschliesslich im gemeinsamen
Workspace/Compiler. Beispiel des zusätzlichen Konfigurationswertes:

```json
{
  "resolutionPolicy": {
    "preferWorkspaceSource": true,
    "modelSelections": [
      {"modelName": "Dep", "modelVersion": "1", "sourceHint": "memory:/dep.ili"}
    ],
    "sourcePriorities": [
      {"sourceId": "memory:/dep.ili", "priority": 0}
    ]
  }
}
```

Modellversion/SourceHint sind optional, Ränge sind nichtnegative exakte Java-int-
Werte. Doppelte Modell-/Quellenschlüssel und falsche Formen sind INVALID_REQUEST
und verbrauchen keine Session. Fehlende Policy bedeutet Defaults, auch beim
vollständigen Konfigurationsersatz. Alte Snapshots behalten ihre ausgewählten
Definitionen; neue Konfigurationen erzeugen neue Captures. Gemeinsame portable
Regressionsszenarien prüfen beide Sprachen, genaue Definition-/Originalziele,
Priorität, fehlende transitive Abhängigkeiten, Entfernung und alte Snapshots.
Keine Datei-/HTTP-/Lockcache-Implementierung wird in die portable Engine gezogen.
