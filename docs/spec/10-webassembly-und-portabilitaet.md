# 10 – WebAssembly, Java-Portabilität und Browserprotokoll

## 1. Ziel und Beleglage

Der Compilerkern muss von Beginn an als Java-Bibliothek auf der JVM und als Java-abgeleitetes Browsermodul entwickelt werden. Die Portabilität umfasst Syntax, Semantik, Diagnostik, Workspace-Sitzungen und lesende Projektionen. JDBC, Dateisystem, Repository-HTTP und LSP-Transport bleiben Hostaufgaben.

Dieses Kapitel unterscheidet ausdrücklich zwischen dem bereits ausgeführten kleinen Versuch und den noch zu implementierenden Abnahmestufen. Die Belege des Versuchs liegen in [README](../evidence/wasm-spike/README.md) und [results.json](../evidence/wasm-spike/results.json); sein unverändertes Quellpaket liegt in [interlis-java-wasm-spike.zip](../evidence/interlis-java-wasm-spike.zip).

### 1.1 Tatsächlich ausgeführt am 18. Oktober 2026

| Gegenstand | Beobachtung |
|---|---|
| Java | Eclipse Temurin 25.0.4.1+1, `javac --release 25`, keine Preview-Features. |
| Parsergenerator und Laufzeit | ANTLR 4.13.2, jeweils derselbe Versionsstand. |
| Grammatik | Gesamte geostandards-Grammatik, Commit `f8f97238c2540a49915f8bb97ee99bbf434b74da`, unverändert. |
| Wasm-/JS-Compiler | TeaVM 0.16.0. |
| Laufzeitversuche | JVM, TeaVM-WasmGC in Node 24.19.0, TeaVM-JavaScript in derselben Node-Version. |
| Gemeinsame Prüfungen | Kleine Syntaxmodelle 2.3/2.4, Recovery, Vollverbrauch, Records, Sealed Types, Record Patterns, ausgewählte genaue Zahloperationen, Unicode-RegEx und Positionsabbildung. |
| Interop | JavaScript-String hinein, Java-Analyse, JSON-String zurück. |
| Artefakte | Wasm 686'741 Bytes, Wasm-JS-Runtime 17'268 Bytes, JS-Backend 749'532 Bytes; unkomprimiert. |
| Nicht ausgeführt | Vollständiger Compiler, vollständige Konformität, Imports/Binding/Typprüfung, echte Browser, GraalVM Web Image, Langzeitsitzungen und aussagekräftige Performancebenchmarks. |

Der erfolgreiche 2.3-Fall beweist nur das getestete kleine Modell. Die übernommene Grammatik besitzt noch keine vollständige 2.3-/2.4-Freigabelogik. Ihr fehlendes `EOF` wurde im Adapter durch expliziten Vollverbrauch geprüft. Lexer- und Parserfehler wurden beide gesammelt. Die ANTLR-Warnung 154 zu `viewDef` bleibt als zu untersuchender Grammatikbefund dokumentiert.

Der historische Versuch verwendet Maven. Das ist kein Zielbuildvertrag. P0 muss ihn in den gemeinsamen Gradle-Build übertragen und die Ergebnisse neu nachweisen. Ein Coding-Agent darf das historische Messergebnis weder als Ergebnis des neuen Gradle-Builds noch als bestandenen Browser-/Semantiktest ausgeben.

### 1.2 Anforderungen

| ID | Normative Anforderung | Phase | Abnahme |
|---|---|---|---|
| WASM-001 | Der portable Kern MUSS denselben fachlichen Java-Quellcode auf JVM und TeaVM verwenden. | P0–P2 | Gemeinsame Module, keine zweite INTERLIS-Semantik in JavaScript. |
| WASM-002 | Der vorhandene Syntaxversuch MUSS unter Gradle reproduziert werden. | P0 | Frische Build-/Laufmanifestdatei mit Befehlen und Exitcodes. |
| WASM-003 | TeaVM-/Web-Image-Annotationen und Laufzeittypen DÜRFEN nicht in Modell-, Syntax- oder Semantik-APIs gelangen. | P0 | Abhängigkeits-/Importprüfung. |
| WASM-004 | Sprache und verwendete JDK-APIs MÜSSEN getrennt auf Portabilität geprüft werden. | P0–P2 | Feature-/Bibliotheksmatrix mit ausführbaren Prüfungen. |
| WASM-005 | P1 MUSS eine echte semantische Strecke mit Imports, Vererbung, Domäne, Constraint und Fehlerzustand portieren. | P1 | Gleichheit normalisierter JVM-/Wasm-Ausgaben. |
| WASM-006 | Der ausgewählte TeaVM-Produktionspfad MUSS für G1 die P1-Vertragssuite in echtem Chrome, Firefox und Safari erfolgreich ausführen; Node allein genügt nicht. | P1 | Gepinnte Browser-/Versionskombinationen jeweils PASS; fehlende Umgebung hält G1 offen. |
| WASM-007 | Ein realer GraalVM-Web-Image-Vergleich MUSS vor der endgültigen Adapterentscheidung versucht und dokumentiert werden. | P1 | Build-/Laufresultat oder reproduzierbarer konkret belegter Blocker. |
| WASM-008 | Das Browserprotokoll MUSS versioniert sein und grosse Zahlen, Revisionen und IDs verlustfrei übertragen. | P1 | Grenzwerte jenseits 2^53, Dezimalskalen, Unicode und JSON-Roundtrip. |
| WASM-009 | Browser-Sitzungen MÜSSEN Änderungen, Analysen, Abfragen, Snapshotfreigabe und Schliessen unterstützen. | P1 | Durchgängige Mehrdatei-Editfolge im Worker. |
| WASM-010 | Lange Analysen DÜRFEN den UI-Hauptthread nicht belegen. | P1 | Standardclient führt Compiler in eigenem Worker aus. |
| WASM-011 | Abbruch und veraltete Antworten MÜSSEN mit dem tatsächlichen Ausführungsmodell vereinbar sein. | P1 | Laufender Worker kann beendet und aus Hostzustand neu aufgebaut werden. |
| WASM-012 | Wasm und zugehörige Runtime-/Loaderdateien MÜSSEN zusammen versioniert ausgeliefert werden. | P0 | Frische Installation benötigt keine zufällig vorhandene alte Runtime. |
| WASM-013 | Wasm-/JS-/JVM-Vergleiche MÜSSEN denselben Eingabestand und dieselben Optionen verwenden. | P1–P2 | Input-Fingerprint und Fixture-Pins im Testmanifest. |
| WASM-014 | Kein Ziel DARF bei fehlender Verbraucherfähigkeit gültige Sprachelemente stillschweigend weglassen. | P1–P2 | Explizite Capabilities und Unsupported-Projektionsresultate. |
| WASM-015 | Langzeitsitzungen MÜSSEN begrenzte Snapshot-/Cachehaltung und explizite Freigabe besitzen. | P2 | Wiederholte Edits mit begrenztem Speicherwachstum. |
| WASM-016 | Native Image, Web Image und GraalWasm MÜSSEN in Code und Dokumentation korrekt unterschieden werden. | P0 | Benannte Tasks/Artefakte bezeichnen ihr reales Ziel. |
| WASM-017 | Browser-Repositoryauflösung MUSS im Host liegen; der Kern erhält Quellen und Bindungen. | P1 | Compilerfixture funktioniert ohne Netzberechtigung. |
| WASM-018 | Performanceaussagen MÜSSEN als Messungen, Ziele oder offene Hypothesen gekennzeichnet sein. | P0–P3 | Rohdaten, Hardware-/Runtimeversion und Messmethode für jede veröffentlichte Zahl. |

## 2. Zieladapter und Entscheidungskriterien

### 2.1 TeaVM als erster laufender Adapter

TeaVM dokumentiert Java-25-Unterstützung und WasmGC-Coroutinen. Es verwendet eine eigene Implementierung eines Teils der Java-Klassenbibliothek; eine unterstützte Sprachsyntax garantiert deshalb noch nicht die gleiche Verfügbarkeit jeder JDK-Methode. Quellen: [Release 0.13](https://teavm.org/docs/release-notes/0.13.0.html), [Java-Klassenbibliothek](https://teavm.org/docs/runtime/java-classes.html).

Der Zieladapter heisst `interlis-wasm-teavm`. Er enthält nur Einstieg, Interop und Laufzeitintegration. Das JavaScript-Backend bleibt während der Portierungsphase als zusätzlicher Vergleich aktiv. Ob es dauerhaft als Fallback ausgeliefert wird, wird anhand der Browsermatrix und Wartungskosten entschieden.

Ein aktueller TeaVM-WASI-Produktpfad wird nicht eingeplant: Das ältere non-GC-/WASI-Backend wurde entfernt. Quelle: [Release 0.14](https://teavm.org/docs/release-notes/0.14.0.html). Ein späterer Wunsch nach WASI ist ein gesondertes Architekturvorhaben, kein Umbenennen des WasmGC-Artefakts.

### 2.2 GraalVM Web Image als echter Vergleich

Die offizielle [Web-Image-Dokumentation](https://www.graalvm.org/latest/reference-manual/web-image/) beschreibt ein experimentelles Backend ab Oracle GraalVM 25.1 mit zusätzlichen Werkzeugvoraussetzungen, unter anderem Binaryen. Der dokumentierte Einstieg lautet `native-image --tool:svm-wasm ...`; erzeugt werden Wasm und JavaScript-Unterstützung für Browser beziehungsweise Node. Ein gewöhnliches Native Image erzeugt dagegen ein Betriebssystemprogramm; GraalWasm ist eine Laufzeit zum Ausführen von Wasm.

Der Web-Image-Adapter erhält das gleiche Bridge-Protokoll und die gleichen Fixtures. Seine Implementierung folgt der zum gepinnten Toolchainstand tatsächlich vorhandenen API. Die [API-Dokumentation](https://www.graalvm.org/latest/reference-manual/web-image/api-guide/) weist derzeit etwa nicht implementierte `@JS.Import`-/`@JS.Export`-Annotationen aus und beschreibt einen Bootstrap über `@JS` und funktionale Interfaces. Der Agent darf keine ungetestete vermeintlich symmetrische TeaVM-Annotation in Graal-Code erfinden.

Ein Blockerbericht ist nur dann ausreichend, wenn der konkrete Stand, der minimale fehlschlagende Aufruf, die erste relevante Fehlermeldung, ein isolierendes Fixture und untersuchte Alternativen dokumentiert sind. Eine allgemeine Vermutung wie „Graal unterstützt ANTLR vermutlich nicht“ erfüllt WASM-007 nicht. Ein fehlendes Downloadrecht oder eine nicht verfügbare Toolchain wird als Umgebungsblocker vom Compilerproblem getrennt.

### 2.3 Entscheidung nach P1

Die Adapterentscheidung berücksichtigt Ergebnisgleichheit, API-Verfügbarkeit, reales Browserverhalten, Build-/Debuggingaufwand, Startzeit, Speicher und Analysezeit. Es gibt keine vorab erklärte Pflicht, dauerhaft zwei Produktionsadapter zu warten. Ein zweiter Adapter kann als reproduzierbarer Vergleich erhalten bleiben, wenn er keinen Produktvorteil bringt.

Die Entscheidung muss als ADR festhalten: bevorzugtes Ziel, überprüfter Toolchainstand, bekannte Grenzen, Gründe gegen beziehungsweise für den zweiten Adapter und Bedingungen für eine erneute Bewertung. Die Kernarchitektur darf dadurch keine Runtime-Annotationen aufnehmen.

## 3. Gradle-Integration

Der gesamte Build folgt [03](03-build-und-repository.md): Gradle Wrapper, Groovy DSL, Java 25, explizite Versionen. Das offizielle TeaVM-Plugin dokumentiert die ID `org.teavm`, die `wasmGC`-Konfiguration sowie die Tasks `generateWasmGC`, `copyWasmGCRuntime`, `buildWasmGC` und `generateJavaScript`. Die folgenden Einträge sind eine projektspezifische Minimalkonfiguration auf dieser dokumentierten API; sie wurden für die Spezifikation recherchiert, aber hier noch nicht als neuer Gradle-Build ausgeführt. Quelle: [TeaVM Gradle](https://teavm.org/docs/tooling/gradle.html).

**Der folgende Block beschreibt die P1-Konfiguration**, nachdem workspace und projection tatsächlich angelegt wurden. Für den P0-Parserspike werden nur die bereits vorhandenen compiler-/model-Projekte eingebunden. Abhängigkeiten auf noch nicht existierende Projekte dürfen den P0-Build nicht blockieren. Wird der gemeinsame Bridge-Code später in das reale Modul `interlis-wasm-bridge` verschoben, richten sich die direkten Projektabhängigkeiten nach Kapitel 02.

```groovy
plugins {
    id 'java'
    id 'org.teavm' version '0.16.0'
}

dependencies {
    implementation project(':interlis-workspace')
    implementation project(':interlis-projection')
    implementation teavm.libs.jso
}

teavm {
    all {
        mainClass = 'guru.interlis.wasm.teavm.TeaVmEntryPoint'
    }
    wasmGC {
        targetFileName = 'interlis-compiler.wasm'
        copyRuntime = true
        modularRuntime = true
    }
}
```

P0 muss diese Integration mit dem gepinnten Wrapper prüfen und gegebenenfalls eine dokumentierte, minimal angepasste Konfiguration verwenden. Zentral verwaltete Versionen können den sichtbaren Pluginpin im endgültigen Modul ersetzen. Zusätzliche Optimierungs-, Reflection- oder Shared-Memory-Optionen werden nur mit einem konkreten Bedarf aktiviert.

Vorgesehene Aufrufe:

```sh
./gradlew :interlis-wasm-teavm:buildWasmGC
./gradlew :interlis-wasm-teavm:generateJavaScript
./gradlew :interlis-wasm-teavm:wasmContractTest
./gradlew checkPortable
./gradlew checkBrowser
```

`wasmContractTest`, `checkPortable`, `checkBrowser` und `releaseCheck` sind **neu zu implementierende Projekttasks**, keine behaupteten TeaVM-Standardtasks. Ihr verbindlicher Zusammenhang lautet:

| Task | Pflichtumfang |
|---|---|
| `:interlis-wasm-teavm:wasmContractTest` | Vertragstests des gebauten TeaVM-Moduls unter der gepinnten Node-Version, einschliesslich Bridge und exakter Zahlen. |
| `checkPortable` | Gemeinsame JVM-Prüfungen und die Node-Vertragstests der für die Phase vorgesehenen Zielprofile. |
| `checkBrowser` | Reale gepinnte Chrome-/Firefox-/Safari-Matrix des ausgewählten TeaVM-Produktionspfads. |
| `releaseCheck` | Beide vorstehenden Aggregate ab G1 sowie der für P1 erforderliche Web-Image-Untersuchungsnachweis und weitere phasenbezogene Gates aus Kapitel 03/14. |

Browserprüfungen haben dokumentierte Laufzeitvoraussetzungen. Das einfache JVM-`check` darf keinen ungeplanten Browser-/SDK-Download auslösen. Ein lokaler Lauf darf fehlende Browser als NOT_RUN dokumentieren; dieser Bericht erfüllt G1 und den entsprechenden releaseCheck nicht. Ein realer, begründet blockierter Web-Image-Versuch erfüllt lediglich den gesonderten Untersuchungsauftrag und wird nicht zu PASS umetikettiert.

Der [WasmGC-Loader](https://teavm.org/docs/wasm-gc-backend/loader.html) benötigt die passende JavaScript-Runtime. `generateWasmGC` allein ist deshalb kein vollständiges Auslieferungsartefakt. Der Build muss den Runtime-Kopierschritt enthalten und die finalen Dateinamen anhand der tatsächlich erzeugten Ausgaben prüfen.

Für Web Image wird zunächst ein kleiner expliziter Gradle-Task verwendet, der eine konfigurierte Toolchain aufruft. Er deklariert Eingabe-JARs, Main-Klasse, Toolchainversion und Ausgabedateien. Keine erfundene offizielle Gradle-Web-Image-DSL ist Voraussetzung. Der Task darf nicht bei jeder normalen JVM-Kompilierung anlaufen.

## 4. Gemeinsame Bridge und Klassengrenzen

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

Der gemeinsame Bridge-Code benötigt Workspace und Projection und darf deshalb nicht in `interlis-model` oder in ein rückwärts abhängiges Projection-Modul geschoben werden. P0 kann den runtimefreien Code im TeaVM-Adapter halten. Sobald beide Zieladapter ihn tatsächlich nutzen, wird ein kleines `interlis-wasm-bridge` mit `guru.interlis.bridge` extrahiert. Dieses Modul hat dann echten gemeinsamen Code; es ist kein vorsorglich leeres Gerüst.

`SessionRegistry.open` benutzt dieselbe öffentliche Konstruktion wie JVM-Verbraucher: `CompilerEngines.create(...)` und `Workspaces.create(engine, configuration)`. Die Bridge darf keinen zweiten verkürzten Compiler oder eine durch Gate-Umgehung erzeugte Schemainstanz besitzen. Eine leere Sitzung bleibt bis zu explizit übergebenen Quellen ohne I/O.

Für einen Request mit Auflösungsbericht verwendet die Facade `workspace.capture(request, token)`, die Engine mit `capture.request()` und `capture.reuse()` und danach `workspace.acceptAnalysis(capture, analysis)` gemäss Kapitel 07. Sie übernimmt bei regulär abgeschlossener Analyse sowie bei Compile-`Succeeded` und `Rejected`; Abbruch und interner Fehler übernehmen keinen vollständigen Reuse-/Abhängigkeitsstand. Der synchrone Bridgepfad serialisiert die Sitzung bereits. Ein späterer asynchroner Host muss `STALE_NO_COMMIT` wie der LSP behandeln und darf daraus keinen aktuellen Snapshotzeiger veröffentlichen. Das Registrierungssystem kann einen ausdrücklich angeforderten historischen Snapshot weiter halten, muss dessen unveränderten Herkunftsstand mitliefern.

Die Facade übergibt einen frischen `RunContext` ausdrücklich an `engine.analyze(request, reuse, context, token)` beziehungsweise den Compile-Overload. Nur die Engine erzeugt Start und Abschluss dieser RunId. Für einen eigenen übergeordneten Bridge-/Beschaffungslauf erzeugt der Host einen separaten Kindkontext über `RunContextFactory.createChild(parent)`. Request-ID, Session-ID und Worker-Epoche werden nach dem begrenzten Korrelationsvertrag aus Kapitel 09 zugeordnet; weder ThreadLocals noch ein bereits gestarteter Hostkontext dienen als verborgener Engineparameter. Der Conveniencepfad darf die Kontextfabrik der Engine nutzen.

`handle` arbeitet synchron und führt keine Callback-I/O aus. Es gibt genau einen JSON-String zurück. Fachlich fehlerhaftes INTERLIS ist eine erfolgreiche Protokolloperation mit Diagnostik. Fehler beim Dekodieren oder bei Sitzungs-/Revisionsverträgen sind Bridgefehler. Unerwartete interne Ausnahmen werden mit FailureId protokolliert und als `INTERNAL_FAILURE` zurückgegeben, ohne beliebige Javaobjekte oder Stacktraces in die fachliche Antwort zu serialisieren.

Ein fataler Runtime-Trap oder eine nicht mehr sicher behandelbare Speichererschöpfung kann eine JSON-Antwort verhindern. Der Host muss diesen Ausgang als `RuntimeFailure` der konkreten Anfrage erfassen und die Workerinstanz verwerfen; er darf keine erfolgreiche leere Antwort erfinden. Dieser Hostausgang ist von einem regulär kodierten Bridgefehler zu unterscheiden.

Alle Operationen einer Sitzung werden serialisiert. Eine Runtime darf mehrere isolierte Sitzungen halten; das stellt keine Multicore-Ausführungsgarantie dar. Eine Sitzung darf keine referenzierten Javaobjekte einer anderen Sitzung akzeptieren.

## 5. Protokoll `interlis.compiler` Version 1.0

### 5.1 Nachrichtenhülle

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

`roots` wird als `ModelSelectorDto` validiert. Variante `byName` enthält `modelName` als Pflichtfeld und optional `modelVersion` sowie `sourceHint`. Variante `bySource` enthält `source` und wählt die in Kapitel 06 definierte Modellmenge dieser Quelle. `modelVersion` ist die fachliche Modellversion. Die erlaubten Sprachversionen 2.3/2.4 stehen unabhängig davon in `CompilerOptions.allowedLanguages`. Der Decoder bildet exakt auf den Selectorvertrag aus Kapitel 06 ab; fehlende Optionals werden durch abwesende Felder dargestellt.

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

Dieses kleine Antwortbeispiel illustriert die Hülle; eine echte partielle Analyse muss ihre konkrete Coverage und ihre Gründe nach Kapitel 06 übertragen. Eine leere Diagnoseliste ist für sich allein kein Gültigkeitsbeweis. `requestedRootsChecked` darf nur nach dem tatsächlichen Gate true sein und wird bei gewöhnlicher EDITOR-Analyse nicht als Ersatz für dieses Gate verwendet.

Bei `status: "error"` steht anstelle von `result` ein Objekt `error` mit `code`, `messageKey`, getaggten Argumenten und optionalen strukturierten Details. Bei `status: "cancelled"` enthält `cancellation` einen Grund und den letztbekannten unveränderten Workspace-Stand. `null` wird nicht als unspezifischer Sammelwert für Fehler, fehlende Quelle und nicht analysierten Inhalt verwendet.

`protocolVersion` wird getrennt von Tool-, Sprach-, Diagramm- und Datenbankprofilversionen geführt. Unbekannte Majorversionen werden abgelehnt. Innerhalb Major 1 dürfen additive optionale Antwortfelder ignoriert werden. Unbekannte Operationsnamen und unbekannte diskriminierende Varianten in Pflichtdaten werden als Fehler behandelt. JSON-Schemas und TypeScript-Typen sind neben den Java-DTOs zu pflegen; Vertragstests verhindern deren Auseinanderlaufen.

### 5.2 Operationen und Methodenkontrakte

| Operation | Eingabe | Ergebnis und Vertrag |
|---|---|---|
| `capabilities` | Keine sessionId; gewünschte Protokollversion. | Tool-/Bridgeversion, Runtimeziel, unterstützte Operations-/Query-/Projektionsarten, Sprachabdeckung, Limits. Kein Sitzungszustand. |
| `openSession` | Konfiguration, Limits, optional fertiges Quellenpaket. | Neue sessionId, WorkspaceRevision `0` bei leerem Bestand oder `1` nach atomarer nichtleerer Initialladung, angenommene Limits. Schlägt Initialvalidierung fehl, wird keine Teilsitzung gehalten. |
| `updateSource` | SourceId, Expectation (`absent`/`at`), `replace` oder `edits`. | UpdateReceipt entsprechend Kapitel 07; exakte Revisionen als Strings. |
| `updateSources` | Erwartete WorkspaceRevision, geordnete Liste von SourceUpdates. | `BatchUpdateReceipt` aus dem atomaren `Workspace.applyBatch`; kein Teilcommit. |
| `removeSource` | SourceId und erwartete SourceRevision. | Atomare Entfernung oder Versionsfehler. |
| `setConfiguration` | Erwartete WorkspaceRevision, vollständig neue Konfiguration. | Neue Revision/Fingerprints oder No-op; keine implizite Repositoryabfrage. |
| `analyze` | Erwartete WorkspaceRevision, Wurzeln, Analyseumfang. | SnapshotId, Coverage, Diagnostik und explizit fehlende Modellanforderungen. Kein Produktionsschema. |
| `compile` | Erwartete WorkspaceRevision, Wurzeln, Compileroptionen gemäss Konfiguration. | `outcome: succeeded` mit schemaId/snapshotId oder `outcome: rejected` mit snapshotId/Diagnostik und unveränderter RejectionSummary des Gates. Interner Fehler und Abbruch bleiben getrennt. |
| `query` | SnapshotId und getaggte Query aus Kapitel 07. | Getaggtes QueryResult; keine verdeckte Neuanalyse eines anderen Stands. |
| `diagram` | SnapshotId oder geprüfte schemaId, versionierte Diagrammoptionen. | DiagramDocument aus Kapitel 11 oder explizite Capability-/Inputfehler. |
| `releaseSnapshot` | SnapshotId. | Idempotente Freigabe des Client-Halteanspruchs; danach darf der Snapshot entfernt sein. |
| `closeSession` | SessionId. | Idempotenter Abschluss; alle gehaltenen Handleansprüche dieser Sitzung erlöschen. |

Alle sitzungsgebundenen Antworten enthalten die sessionId. Queryantworten enthalten die SnapshotId. Antworten aus einer anderen Worker-Epoche werden vom Host verworfen. `schemaId` ist ein opaker Handle auf ein erfolgreiches `CompiledSchema`, kein serialisierter Objektzeiger. Seine Lebensdauer ist an den dazugehörigen gehaltenen Snapshot gebunden.

`result.rejection` ist bei `outcome: rejected` Pflicht und bildet die `RejectionSummary` aus Kapitel 06 verlustfrei ab:

| Wire-Feld | Typ und Vertrag |
|---|---|
| `reasons` | Nichtleeres, kanonisch sortiertes Array ohne Duplikate aus `SOURCE_ERRORS`, `WARNINGS_REJECTED`, `MISSING_INPUT`, `AMBIGUOUS_INPUT`, `ENVIRONMENT_FAILURE`, `INCOMPLETE_SUPPORT`. |
| `diagnosticIds` | Geordnete IDs der relevanten kanonischen Diagnosen des gelieferten Snapshots. |
| `requestedRoots` | Ursprüngliche ModelSelectorDto-Liste dieser Kompilierung. |
| `unsupportedFeatureIds` | Stabile Feature-IDs der blockierenden noch nicht implementierten Spracheigenschaften; andernfalls leeres Array. |

Diese Summary wird ausschliesslich vom CompilationGate erzeugt und ist auf die angeforderte Wurzelhülle bezogen. Die Bridge und der Client dürfen Gründe weder aus globalen Diagnosen noch aus lokalisierten Meldungstexten neu berechnen. Insbesondere bleiben ein Modellfehler, wegen Policy abgelehnte Warnungen, fehlende Quellen, Umgebungsfehler und unvollständige Compilerunterstützung unterscheidbar. Eine protokolltechnisch erfolgreiche `compile`-Operation kann deshalb `status: ok` und zugleich `outcome: rejected` tragen.

Fehlende Modellanforderungen und deren Locator/Provenienz stammen aus `WorkspaceCapture.resolution().pendingFetches()` beziehungsweise dessen PreparedClosure-Bericht nach Kapitel 07/08. Dieser Bericht muss zu demselben eingefrorenen Eingabestand wie die Analyseantwort gehören. Die Bridge betreibt keine zweite Header-/Resolverimplementierung, um `missingModels` zu erzeugen.

`query.params.query.kind` ist eine feste Union: `symbolAt`, `definition`, `hover`, `completion`, `references`, `prepareRename`, `rename`. Jede Variante besitzt ihr eigenes JSON-Schema. Der Server kündigt nur implementierte Varianten an. Ein Positionquery enthält SourceId, SourceRevision und UTF-16-Offset; ein Symbolquery enthält SnapshotId und SymbolId. Nicht unterstützte P3-Queries werden als `UNSUPPORTED_OPERATION` abgelehnt und niemals mit einer irreführend leeren Trefferliste beantwortet.

### 5.3 Beispiel einer bedingten Quelländerung

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

### 5.4 Fehlercodes

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

Fehlerdetails dürfen erwartete/beobachtete Revision, zulässige Grenzen und Feldpfade enthalten. Quelltext oder unbekannte JSON-Inhalte werden nicht pauschal in Fehlermeldungen und Logs kopiert. Diagnoseargumente werden nach dem getaggten Vertrag aus Kapitel 09 kodiert.

`LIMIT_EXCEEDED` bezeichnet die Bridge-/Sitzungsgrenze vor einem Commit. Ein während der semantischen Analyse ausgeschöpftes `AnalysisBudget` folgt dagegen dem gemeinsamen Ausführungsvertrag: `status: cancelled` und `reason: RESOURCE_LIMIT`, gegebenenfalls mit ausdrücklich unvollständiger Coverage. Andere CancellationReasons sind `USER_REQUESTED`, `SUPERSEDED`, `DEADLINE_EXCEEDED` und `HOST_SHUTDOWN`. UI-Diagrammbegrenzungen nach Kapitel 11 haben den gesonderten Status LIMITED und dürfen nicht mit einer abgebrochenen Sprachprüfung verwechselt werden.

### 5.5 Präzision und JSON

IDs sind opake Strings. `SourceRevision`, `WorkspaceRevision`, Zähler mit Java-`long`-Wertebereich und beliebig grosse fachliche Ganzzahlen werden als Dezimalstrings übertragen. Sie werden nie zunächst in JavaScript-`Number` umgewandelt. UTF-16-Offsets, Zeilen, Spalten und kleine Enum-/Schemawerte bleiben geprüfte nicht negative JSON-Ganzzahlen innerhalb des Java-`int`-Bereichs.

Ein allgemeiner fachlicher Dezimalwert wird als `ExactDecimalDto` mit `unscaledValue` als Dezimalstring und `scale` als vorzeichenbehafteter 32-Bit-Ganzzahl übertragen. `raw` und die INTERLIS-spezifischen Genauigkeits-/Exponenteninformationen des `DecimalLiteral` aus Kapitel 05 bleiben zusätzliche typisierte Metadaten. So erzwingt ein sehr grosser Exponent nicht die Materialisierung einer riesigen ausgeschriebenen Dezimalzeichenkette.

```json
{
  "kind": "decimal",
  "unscaledValue": "90071992547409930010",
  "scale": 4,
  "raw": "9007199254740993.0010"
}
```

Dieser Wert darf auf der JavaScript-Seite zur Anzeige formatiert oder mit einer geeigneten Dezimalbibliothek verarbeitet werden. Eine Konversion zu `Number` ist keine zulässige Grundlage für Compilerentscheidungen, Gleichheitsprüfungen oder Golden-Vergleiche. Diagnoseargumente mit DECIMAL folgen dem eigenen Darstellungsschema aus Kapitel 09 und bleiben ebenfalls exakt.

JSON-Escaping, Surrogatpaare, Control Characters und Zeilenwechsel werden durch den Codec geprüft. Serialisierte Reihenfolgen sind deterministisch. Das Protokoll verwendet kein Java-Objektserialisierungsformat, keine Klassennamen als Typdiscriminator und keine Reflection-basierte polymorphe Instanziierung aus unkontrollierten Klassennamen.

## 6. Worker, Abbruch und externe Quellen

### 6.1 Host-API

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

`BatchSourceUpdateDto` enthält `expectedWorkspaceRevision` und die SourceUpdateDto-Liste. `RemoveSourceDto` enthält SourceId und erwartete SourceRevision. `SetConfigurationDto` enthält die erwartete WorkspaceRevision und die vollständige neue WorkspaceConfigurationDto, einschliesslich Compileroptionen, ResolutionPolicy, eingefrorenem ResolverCatalog und CacheLimits aus Kapitel 07. Revisionen werden jeweils als Dezimalstrings kodiert. Der Client enthält keine Sprachregeln. Er besitzt eine Hostkopie der angenommenen Quelltexte und Konfiguration, damit eine verlorene Workerinstanz wiederhergestellt werden kann.

Ohne vorgegebene `requestId` erzeugt der Client eine innerhalb der Worker-Epoche eindeutige Kennung. Der Aufrufer kann entweder eine eigene Kennung vorgeben und `cancel(id)` benutzen oder über `AbortSignal` abbrechen. So bleibt Abbruch auch erreichbar, obwohl das öffentliche Resultat eine Promise ist. Ein bereits abgebrochenes Signal startet keine Berechnung.

### 6.2 Tatsächliches Abbruchverhalten

Ein synchron laufendes Wasm-`handle` kann nicht allein deshalb eine spätere `postMessage`-Abbruchnachricht empfangen, weil Java einen CancellationToken besitzt. P1 verwendet deshalb folgende belastbare Grundstrategie:

1. Noch nicht gestartete Anfrage: aus der Worker-Queue entfernen und `cancelled` antworten.
2. Laufende Anfrage ohne geprüften kooperativen Kanal: Worker beenden.
3. Neue Worker-Epoche starten, Module laden und bestätigte Hostquellen/Konfiguration in neuer Sitzung aufbauen.
4. Alte Antwort-/Snapshot-/Schemahandles ungültig machen; neue IDs an den Client melden.
5. Aktuell benötigte Analyse erneut ausführen.

`CancelDisposition` unterscheidet `notStarted`, `cooperative`, `workerRestarted`, `alreadyCompleted` und `unknownRequest`. Bei einem Neustart darf keine Cross-Session-SymbolId-Stabilität behauptet werden. Die UI kann Layout über ausdrücklich als heuristisch gekennzeichnete fachliche Schlüssel wiederzuordnen versuchen; semantische Referenzen werden neu angefordert.

Später sind kooperative Yielding-Punkte oder ein geprüfter Shared-Memory-Abbruchkanal zulässig. SharedArrayBuffer darf keine versteckte Pflicht werden; benötigte Hosting-Header und die echte Browsermatrix müssen dann dokumentiert werden. Solche Optimierungen verändern den Cancellation-Vertrag nicht.

### 6.3 Modelle nachladen

Die Bridge führt weder `fetch` noch Dateidialoge aus. Eine Analyse mit fehlenden Quellen gibt konkrete Modellanforderungen und Diagnostik zurück. Der Host löst diese anhand einer ausdrücklich konfigurierten Repository-/Quellenstrategie auf, installiert die gefundenen Quellen und startet eine neue Analyse mit neuer WorkspaceRevision.

Ein solcher Ablauf besitzt Grenzen für Nachladerunden und erkennt wiederholte identische Anforderungen. Er darf bei zyklischen Imports oder einem unerreichbaren Modell keine Endlosschleife erzeugen. Später verfügbare Quellen invalidieren negative Abhängigkeiten nach Kapitel 07. Der Host protokolliert Quelle, Ergebnis, Cachezustand und Auflösungsgrund; Compilerdiagnostik bleibt deterministisch aus den übergebenen Eingaben ableitbar.

### 6.4 Speicher und Limits

`SessionLimits` besitzt mindestens `maxSourceUtf16Length`, `maxTotalSourceUtf16Length`, `maxSources`, `maxRetainedSnapshots`, `maxMessageUtf16Length` und `maxConcurrentSessions`. Jeder Wert wird bei `openSession` geprüft und in der Antwort als tatsächlich angenommenes Limit zurückgegeben.

Die Nachrichtenlänge wird vor dem vollständigen JSON-Dekodieren geprüft. Eine neue Quelle beziehungsweise eine Snapshotregistrierung wird erst nach allen zugehörigen Budgetprüfungen übernommen. Kann ein neues Ergebnis wegen ausschliesslich explizit gehaltener Snapshots nicht registriert werden, erhält der Client einen erklärten Limitfehler und kann einen alten Handle freigeben; gehaltene Handles werden nicht still zwangsweise entfernt.

P1 darf dokumentierte konservative Standardwerte wählen. Sie sind Betriebsgrenzen dieses Hosts, keine Sprachgrenzen von INTERLIS. Die Werte werden nach den ersten echten Modellsätzen festgelegt und als Konfiguration gespeichert; sie werden nicht als vermeintliche Browser-Maximalleistung verkauft. Grenzüberschreitungen liefern `LIMIT_EXCEEDED`, keine unvollständige scheinbar gültige Analyse.

Der Client gibt alte Snapshots nach Ablösung frei. Ein für eine laufende Query gehaltener Snapshot wird bis zum Ende dieser Query nicht entfernt. Die Freigabe eines Snapshots darf andere Snapshots nicht verändern. Vollständige Parse-Trees, Quelltextkopien, JSON-Strings und DTOs sind bei Speicheranalysen getrennt zu zählen; nur die Grösse des `.wasm`-Files zu messen genügt nicht.

## 7. Portabilitäts- und Browsergates

### 7.1 Prüfmatrix

| Bereich | Pflichtfälle |
|---|---|
| Java-Sprachmittel | Records einschliesslich Gleichheit, Sealed Types, Switch/Record Patterns, Exceptions, generische Collections. |
| Zahlen | Werte um 2^53 und Long-Grenzen, grosse BigInteger, negative/positive Skalen, exponentielle Literale, Erhalt fachlicher Genauigkeit. |
| Zeichen | Umlaute, zusammengesetzte Unicodefolgen, Emoji, CRLF/LF, JSON-Escaping, EOF nach letzter leerer Zeile. |
| Parser | 2.3-/2.4-Gating, Lexerfehler, Parserfehler, fehlendes Token, trailing input, grosse verschachtelte Ausdrücke. |
| Semantik | Imports, Mehrmodell-Datei, Vererbung, Typaliase, Constraints, Namenkonflikte, legale aufgeschobene Typen. |
| Sitzung | No-op, Versionskonflikt, negative Abhängigkeit, Änderung/Entfernung, alter Snapshot, Abbruch, Wiederaufbau. |
| Projektion | Identische Diagnosecodes/-bereiche, Typ-/Referenzprojektion, Diagramm-IDs innerhalb Sitzung. |
| Isolation | Zwei Sitzungen mit gleichnamigen unterschiedlichen Modellen; keine Zustandsübernahme. |
| Laufzeit | Node sowie echte Chrome-/Firefox-/Safari-Läufe; Loader und Worker aus ausgeliefertem Paket. |

Safari-Abnahme meint eine tatsächliche Safari-Ausführung mit dokumentierter Version. Ein automatisierter WebKit-Lauf kann zusätzlich nützlich sein, darf aber nicht ohne Weiteres als Safari-Test etikettiert werden. Ein fehlender Browsernachweis bleibt sichtbar offen; ein übersprungener Test wird nicht als bestanden gezählt.

**G1 verlangt PASS in allen drei echten Browsern für den ausgewählten TeaVM-Produktionspfad.** Die konkreten Versionen werden in P1 gepinnt und im Ergebnismanifest geführt. Eine nicht verfügbare Safari-/macOS-Umgebung hält G1 offen; sie beweist weder einen Fehler des Compilers noch erfolgreiche Browserunterstützung. Die Arbeit an anderen Abschnitten kann weitergehen, aber die Browserauslieferung beziehungsweise G1 darf noch nicht als abgenommen bezeichnet werden. Für Web Image gilt bewusst ein anderer Auftrag: ein tatsächlich ausgeführter Vergleichsversuch mit Resultat oder reproduzierbarem begründetem Blocker. Ein solcher Blocker ist kein erfolgreicher Web-Image-Produktionsnachweis.

### 7.2 Deterministischer Ergebnisvergleich

`PortableFixtureRunner` nimmt dieselben Fixturebytes, Wurzeln, Optionen und Änderungsschritte entgegen. Er erzeugt eine normalisierte fachliche Projektion. Snapshot- und Symbol-IDs dürfen zwischen unabhängigen Sitzungen variieren und werden anhand eindeutiger fachlicher Testanker normalisiert. Quellbereiche, Deklarationsreihenfolgen, Typwerte, Diagnosecodes, Argumente und Ursachebeziehungen werden nicht weg-normalisiert.

Zeitstempel, Runtimeziel und Laufzeiten liegen ausserhalb der fachlichen Golden-Datei. Fehler werden nicht dadurch verborgen, dass gesamte Diagnoselisten oder komplizierte Modellteile aus dem Vergleich entfernt werden. Eine bewusst noch nicht unterstützte Spracheigenschaft benötigt eine explizite Phasen-/Capability-Erwartung.

### 7.3 Messungen und Ergebnismanifest

Jeder Portabilitätslauf erzeugt ein maschinenlesbares Manifest mit Quellcommit, Toolchainständen, Artefakthashes, Fixture-/Konfigurationshash, Betriebssystem/Architektur, Browser-/Node-Version, Flags, Teststatus und Fehlerdetails. Der Status unterscheidet `passed`, `failed`, `blocked` und `notRun`.

P1 misst mindestens kalten Modulstart, erste Analyse, wiederholte unveränderte Analyse, Ein-Datei-Änderung und mehrfaches Editieren einer Sitzung. Median und Streuung werden bei ausreichend wiederholten Läufen angegeben. Der Vergleich nennt Messgrenzen: Download, Modulinstanziierung, JSON-Kodierung, Workerkommunikation und reine Compilerzeit sind getrennte Abschnitte.

Erst danach werden sinnvolle Antwortzeit- und Speicherbudgets in Kapitel 13 verbindlich eingetragen. Der Agent darf keine mit kleiner Syntaxprobe gemessene Zeit als Leistungszusage für vollständige DMAV-/CHBase-Modelle verwenden.

## 8. Abnahmeaufträge je Phase

**P0:** Historischen Parserversuch unter Gradle reproduzieren; Dependencies und Main-Klasse isolieren; Wasm plus Runtime vollständig paketieren; neues Ergebnismanifest ablegen. Die ursprünglichen Belege bleiben unverändert.

**P1:** Gemeinsame semantische Strecke und Session-Bridge implementieren; Browserworker mit Quellen, Diagnostik und Diagramm bedienen; reale Browser prüfen; Web Image anhand desselben Fixtures bauen beziehungsweise reproduzierbar untersuchen; Adapterentscheidung begründen.

**P2:** Vollständige unterstützte 2.3-/2.4-Semantik durch dieselbe Plattformmatrix schicken, grosse reale Modellbestände und Langzeitsitzungen prüfen. Fehlende Bibliotheksfunktionen mit kleinem reproduzierbarem Test isolieren, statt fachliche Regeln zielabhängig abzuschwächen.

**P3:** Verfeinerte Abbruchmechanik, kompaktere Projektionen und gemessene Performanceverbesserungen. Jede Optimierung muss weiter den inkrementell/frisch- und JVM/Browser-Vergleich bestehen. Ein neuer binärer Transport ist nur bei einem gemessenen JSON-Engpass gerechtfertigt und erhält eine eigene versionierte Spezifikation.

