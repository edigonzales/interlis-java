# 07 – Workspace, inkrementelle Analyse und LSP

## 1. Geltung und Ziel

Dieses Kapitel spezifiziert den zustandsbehafteten Arbeitsbereich und seine IDE-Anbindung. Es ist eine Implementierungsvorgabe. Die Compiler- und Modelltypen stammen aus [04](04-quelltext-syntax-und-parser.md), [05](05-metamodell-und-typensystem.md) und [06](06-kompilierung-und-semantik.md). Modellbeschaffung ist in [08](08-modellaufloesung-und-repositories.md) geregelt, Diagnostik in [09](09-diagnostik-logging-und-telemetrie.md), die Browseranbindung in [10](10-webassembly-und-portabilitaet.md).

Der Workspace darf bei fehlerhaften Dokumenten weiterhin sinnvoll abfragbar sein. Er muss zugleich verhindern, dass alte oder nur teilweise geprüfte Ergebnisse als aktuelle, gültige Kompilierungsergebnisse erscheinen. Fehlertoleranz, Teilanalyse und Wiederverwendung sind drei getrennte Fähigkeiten; ihre Abnahme erfolgt getrennt.

Die erste inkrementelle Implementierung parst eine geänderte Datei vollständig neu, verwendet unveränderte Syntax wieder und analysiert konservativ die betroffene Abhängigkeitshülle. Eine spätere Verfeinerung auf Deklarationen oder semantische Abfragen darf die öffentlichen Verträge beibehalten. Teilbaumparsing ist kein P1-Gate.

### 1.1 Verbindliche Anforderungen

| ID | Normative Anforderung | Phase | Abnahme |
|---|---|---|---|
| INC-001 | Der Workspace MUSS versionierte In-Memory-Quellen ohne Dateisystem und Netzwerk verwalten können. | P1 | Dieselbe Sitzung auf JVM und Wasm mit virtuellen URIs. |
| INC-002 | Änderungen MÜSSEN atomar sein und die erwartete Ausgangsrevision prüfen. | P1 | Konflikt- und ungültige Edit-Tests zeigen unveränderten Ausgangszustand. |
| INC-003 | Quellrevision, Workspace-Revision und LSP-Dokumentversion MÜSSEN getrennte Werte sein. | P1 | No-op-Änderung und Schliessen/Wiederöffnen eines Dokuments. |
| INC-004 | Veröffentlichte Snapshots MÜSSEN unveränderlich sein und ihren Quellstand vollständig identifizieren. | P1 | Alter Snapshot bleibt nach weiteren Änderungen identisch abfragbar. |
| INC-005 | EDITOR-Analyse MUSS an unvollständigen Deklarationen brauchbare Nachbarelemente erhalten. | P1 | Editfolge mit fehlendem Attributtyp; Nachbarklasse navigierbar. |
| INC-006 | Unveränderte Dateien SOLLEN ab P1 nicht erneut geparst werden; fehlerhafte Dateien dürfen separat erneut geparst werden. | P1 | Zähler für Parse-Aufrufe belegen Wiederverwendung ohne Timing-Annahme. |
| INC-007 | Cache-Schlüssel MÜSSEN alle fachlich wirksamen Eingaben berücksichtigen. | P1 | Änderungen von Sprachversion, Optionen, Imports und Overlays invalidieren. |
| INC-008 | Abhängigkeiten auf fehlende oder mehrdeutige Namen und Modelle MÜSSEN erfasst werden. | P1 | Später hinzugefügtes Modell oder zweite Kandidatendeklaration aktualisiert Analyse. |
| INC-009 | Eine inkrementelle Analyse MUSS fachlich einer frischen Analyse desselben Zustands entsprechen. | P1–P3 | Normalisierter Vergleich nach jeder Editfolge. |
| INC-010 | Symbolidentitäten MÜSSEN für eindeutig unveränderte Deklarationen innerhalb derselben Sitzung wiederverwendet werden. | P1 | Kommentar-/Nachbaränderung verändert die betroffenen SymbolIds nicht. |
| INC-011 | Identitätszuordnung DARF bei Mehrdeutigkeit keine falsche Kontinuität erfinden. | P1 | Duplikate, Verschieben und Löschen/Neuanlegen erzeugen nötigenfalls neue IDs. |
| INC-012 | Alte SymbolRefs und SourceRanges MÜSSEN gegen den jeweiligen Snapshot beziehungsweise die Revision geprüft werden. | P1 | Cross-Snapshot- und Cross-Revision-Abfragen schlagen typisiert fehl. |
| INC-013 | Abbruch MUSS die Quelle erhalten, unveröffentlichte Teilergebnisse verwerfen und einen gültigen Folgelauf erlauben. | P1 | Abbruch an jeder Phasengrenze, danach frische/incrementelle Ergebnisgleichheit. |
| INC-014 | Cache-Speicher MUSS begrenzt werden; gehaltene veröffentlichte Snapshots bleiben gültig. | P2 | Kleine Limits erzwingen Eviction ohne Ergebnisverlust. |
| INC-015 | Ein Analysefehler in einem unabhängigen Workspace-Modell DARF eine gültige angeforderte Wurzelhülle nicht blockieren. | P1 | Zwei unabhängige Wurzeln, eine davon ungültig. |
| INC-016 | Fachliche Modell-Overlays und Betriebscaches MÜSSEN getrennt bleiben. | P2 | Overlay-Fingerprint verändert Ergebnis; Cache-Eviction verändert es nicht. |
| INC-017 | Feingranulare semantische Wiederverwendung SOLL erst nach Messungen und korrekter Abhängigkeitsinstrumentierung eingeführt werden. | P3 | Benchmark und Gleichheitsnachweis vor Aktivierung einer Optimierung. |
| INC-018 | Veraltete Ergebnisse DÜRFEN aktuelle Diagnostik, Quellverweise und Schreiboperationen nicht ersetzen. | P1 | Ergebnis von Revision n trifft nach Revision n+1 ein. |
| INC-019 | Ein Konfigurations- oder Resolverwechsel MUSS als Workspace-Änderung behandelt werden. | P1 | Gleicher Quelltext, anderer Importstand führt zu neuer Analyse. |
| INC-020 | Sitzungen MÜSSEN ohne globale Compiler-Locks, globale Listener und globale mutierbare Namensregister isoliert sein. | P1 | Zwei Sitzungen mit gleichnamigen, unterschiedlichen Modellen. |
| INC-021 | Gemeinsam zu übernehmende Quellen MÜSSEN über einen atomaren bedingten Batch installierbar sein. | P1 | Ein Konflikt in einer von drei Quellen verhindert jede Teilübernahme. |
| INC-022 | Eine ausserhalb der Workspace-Ownerqueue berechnete Analyse MUSS bedingt als Wiederverwendungsstand übernommen werden können; veraltete Ergebnisse dürfen weder Caches noch aktuelle Veröffentlichung ersetzen. | P1 | Capture, paralleles Quellupdate, verspätete Analyse und anschliessender Reuse-Lauf. |

## 2. Zustandsmodell und Eigentümerschaft

### 2.1 Klassen und Verantwortlichkeiten

| Klasse/Interface | Paket | Verantwortung |
|---|---|---|
| `Workspace` | `guru.interlis.workspace` | Öffentliche synchrone API für Quellen, Analysen und Kompilierung. |
| `Workspaces` | `guru.interlis.workspace` | Öffentliche Fabrik für einen unmittelbar nutzbaren leeren Workspace. |
| `DefaultWorkspace` | `guru.interlis.workspace.internal` | Zustandsautomat, Cachebesitz und Orchestrierung; keine Host-I/O. |
| `WorkspaceState` | `guru.interlis.workspace` | Unveränderliche Übersicht über Revision, Quellrevisionen und Konfigurationsfingerprint. |
| `SourceUpdate` | `guru.interlis.workspace` | Eine bedingte Änderung genau einer Quelle. |
| `SourceExpectation` | `guru.interlis.workspace` | `Absent` oder `At(SourceRevision)` als optimistische Vorbedingung. |
| `SourceChange` | `guru.interlis.workspace` | `Replace(String)` oder `ApplyEdits(List<TextEdit>)`. |
| `TextEdit` | `guru.interlis.source` | Ersetzung eines halb offenen UTF-16-Bereichs im dokumentierten Ausgangstext. |
| `UpdateReceipt` | `guru.interlis.workspace` | Tatsächlich angenommene Revisionen, Änderungsstatus und Invalidierungszusammenfassung. |
| `AnalysisRequest` | `guru.interlis.workspace` | Wurzelauswahl, Analyseumfang und erwarteter Workspace-Stand. |
| `WorkspaceCompileRequest` | `guru.interlis.workspace` | Wurzelauswahl und erwarteter Stand für das Produktions-Gate. |
| `WorkspaceCapture` | `guru.interlis.workspace` | Eingefrorener Eingabesatz für einen einzelnen Lauf. |
| `AnalysisAcceptance` | `guru.interlis.workspace` | `ACCEPTED` oder `STALE_NO_COMMIT` für die bedingte Übernahme eines abgeschlossenen Analyselaufs. |
| `DependencyIndex` | `guru.interlis.workspace.internal` | Vorwärts- und Rückwärtsabhängigkeiten samt negativen Lookups. |
| `AnalysisCache` | `guru.interlis.workspace.internal` | Begrenzte Wiederverwendung vollständiger, fingerprintgeprüfter Ergebnisse. |
| `PublicationController` | JVM-/Browser-Hostadapter | Vergleicht Ergebnisstand mit aktuellem Dokumentstand vor Veröffentlichung. |
| `LanguageQueries` | `guru.interlis.compiler.query` | Reine IDE-Abfragen auf einem expliziten `AnalysisSnapshot`. |

Der Workspace gehört genau einem orchestrierenden Aufrufer. Seine mutierenden Methoden und Methoden, die interne Caches ändern, sind in P1 nicht reentrant und nicht gleichzeitig auf derselben Instanz aufrufbar. Der JVM-Host serialisiert solche Aufrufe. Mehrere Instanzen dürfen unabhängig parallel laufen. Unveränderliche Snapshots dürfen gleichzeitig gelesen werden.

Diese Einschränkung ist Teil der öffentlichen Javadoc. Sie darf nicht durch einen versteckten prozessweiten Lock umgesetzt werden. Ein interner `busy`-Guard darf fehlerhafte Reentranz früh als API-Vertragsverletzung erkennen; er ersetzt keine dokumentierte Threadsicherheit.

### 2.2 Revisionen

`SourceRevision` ist eine streng zunehmende, positive Revision je `SourceId`. Der Workspace vergibt sie. `WorkspaceRevision` beginnt bei 0 und steigt bei jeder tatsächlich wirksamen Quell- oder Konfigurationsänderung. Die Zähler sind sitzungslokal; ihre Werte begründen keine Gleichheit zwischen Sitzungen.

Entfernte Quellen behalten intern einen kleinen Revisions-Tombstone. Wird dieselbe URI erneut angelegt, darf ihre Revision nicht wieder bei 1 beginnen. Das verhindert die irrtümliche Akzeptanz alter Quellverweise. Ein Überlauf wird vor einer Mutation als `REVISION_EXHAUSTED` zurückgewiesen; ein Wrap-around ist verboten.

Identischer Text mit identischer fachlicher Konfiguration ist ein No-op: keine neue SourceRevision und keine neue WorkspaceRevision. Die eingehende LSP-Dokumentversion kann trotzdem steigen. Diese Zuordnung hält der LSP-Adapter separat.

Ein `SourceId` ist ein opaker kanonisierter URI-String. Der Workspace führt keine `realpath`-, Netz-, Gross-/Kleinschreibungs- oder Unicode-Normalisierung durch. Die hostseitigen URI-Regeln und die Herkunft der kanonisierten Werte stehen in Kapitel 08.

### 2.3 Öffentliche API

Die folgenden Signaturen bilden den verbindlichen API-Umfang. Imports und Implementierungen sind ausgelassen; die benannten Parametertypen werden in diesem Kapitel oder den verlinkten Grundlagen definiert.

```java
public interface Workspace extends AutoCloseable {
    WorkspaceRevision currentRevision();
    WorkspaceState state();
    Optional<SourceText> source(SourceId id);

    UpdateReceipt update(SourceUpdate update);
    BatchUpdateReceipt applyBatch(
        List<SourceUpdate> updates, WorkspaceRevision expectedRevision);
    UpdateReceipt remove(SourceId id, SourceRevision expectedRevision);
    ConfigurationReceipt configure(
        WorkspaceConfiguration configuration,
        WorkspaceRevision expectedRevision);

    WorkspaceCapture capture(AnalysisRequest request);
    WorkspaceCapture capture(AnalysisRequest request, CancellationToken token);
    AnalysisAcceptance acceptAnalysis(
        WorkspaceCapture capture, AnalysisSnapshot analysis);
    AnalysisSnapshot analyze(AnalysisRequest request, CancellationToken token);
    AnalysisSnapshot analyze(
        AnalysisRequest request, RunContext context, CancellationToken token);
    CompilationOutcome compile(
        WorkspaceCompileRequest request, CancellationToken token);
    CompilationOutcome compile(
        WorkspaceCompileRequest request,
        RunContext context, CancellationToken token);

    WorkspaceMetrics metrics();
    void close();
}
```

`WorkspaceConfiguration` besitzt folgenden verbindlichen öffentlichen Konstruktor und Default-Einstieg. `ResolutionPolicy` und `ResolverCatalog` stammen aus `guru.interlis.workspace.resolution` gemäss Kapitel 08.

```java
public record WorkspaceConfiguration(
    CompilerOptions compilerOptions,
    ResolutionPolicy resolutionPolicy,
    ResolverCatalog resolverCatalog,
    CacheLimits cacheLimits
) {
    public static WorkspaceConfiguration defaults() {
        return new WorkspaceConfiguration(
            CompilerOptions.defaults(), ResolutionPolicy.defaults(),
            ResolverCatalog.empty(), CacheLimits.defaults());
    }
}

public record CacheLimits(
    int maxSyntaxEntries,
    int maxAnalysisEntries,
    int maxQueryEntries,
    long maxRetainedSourceUtf16Units
) {
    public static CacheLimits defaults() {
        return new CacheLimits(128, 4, 1024, 16_777_216L);
    }
}
```

Die Implementierung ergänzt Nullprüfungen und defensive Übernahme unveränderlicher Policy-/Katalogwerte. Cachegrenzen sind nichtnegativ; 0 deaktiviert die jeweilige Cachekategorie. `maxRetainedSourceUtf16Units` begrenzt die konservativ aufsummierte Textmenge intern gehaltener Cacheeinträge. Gemeinsam referenzierter Text darf für diese einfache Zählung mehrfach gezählt werden. Ein zu grosser einzelner Eintrag wird nicht gecacht; seine fachliche Analyse kann trotzdem erfolgreich sein.

Die genannten Cachedefaults sind ausdrücklich anfängliche Betriebseinstellungen und keine gemessene Leistungs- oder Heapgarantie. Objektgraphen, laufende Berechnungen und vom Aufrufer gehaltene Snapshots sind zusätzliche Speicherverbraucher; AnalysisBudget und Browser-SessionLimits regeln ihre eigenen Grenzen. Eviction oder Abschalten eines Caches darf das fachliche Ergebnis nicht verändern.

Fachliche Konfiguration und reine Cachegrenzen erhalten getrennte Fingerprints. Ein Wechsel der Cachegrenze allein invalidiert keine semantische Aussage, kann aber Eviction auslösen. Ein Wechsel fachlicher Optionen oder des verfügbaren Resolverkatalogs erhöht die WorkspaceRevision. `defaults()` hat einen leeren Katalog, Standardauswahlregeln und keinerlei implizite Repository-I/O.

Die öffentliche SDK-Fabrik lautet `Workspaces.create(CompilerEngine engine, WorkspaceConfiguration configuration) -> Workspace`. Sie prüft alle Argumente, erzeugt einen leeren Workspace mit Revision 0 und verwendet den reinen Standard-`ModelResolutionService` aus Kapitel 08 auf eingefrorenen Inventaren. Sie führt keine Dateisuche und keinen Netzwerkzugriff aus. Die Engine erhält der Aufrufer beispielsweise aus `CompilerEngines.create()` gemäss Kapitel 06. Resolver-/Cacheinjektion für Implementierungstests kann intern bleiben; ein zweites öffentliches Factorysystem ist ohne konkreten Verbraucher nicht anzulegen.

`WorkspaceCapture` enthält `workspaceRevision`, `CompilationRequest`, `AnalysisReuse`, `PreparedClosure resolution` und einen vollständigen `InputFingerprint`. Es besitzt kopierte beziehungsweise unveränderlich geteilte Werte und keine Rückreferenz auf den lebenden Workspace. Es wird ausschliesslich durch `Workspace.capture` erzeugt und trägt intern eine sitzungslokale Herkunftskennung, damit `acceptAnalysis` einen Capture aus einem anderen Workspace zurückweisen kann. Diese Kennung ist kein öffentlich konstruierbares Compilerzertifikat. `PreparedClosure` liegt unter `guru.interlis.workspace.resolution`, ist der gemeinsame Untertyp von `ResolutionOutcome` für `ResolvedClosure` und `IncompleteClosure` und bietet `sourceSet()`, `pendingFetches()`, `trace()`, `dependencies()` sowie `negativeDependencies()`. Der Compiler nimmt keine Workspace-Typen entgegen. Der Host kann die enthaltenen Compilerwerte an die in Kapitel 06 spezifizierten Engine-Overloads übergeben und für Beschaffung ausschliesslich `capture.resolution().pendingFetches()` verwenden.

`capture.request().sources()` und `capture.resolution().sourceSet()` müssen denselben eingefrorenen Quellen-/Bindungsstand darstellen. Ein erfolgreich vollständig beschaffter Stand hat leere `pendingFetches()`. Eine IncompleteClosure bleibt als solche erkennbar, auch wenn ein fehlendes Modell mangels Locator derzeit gar keinen ausführbaren Downloadvorschlag besitzt. Die Bridge übernimmt diesen Bericht, statt Modellköpfe nochmals mit eigener Logik zu lesen.

`AnalysisReuse` stammt aus `interlis-compiler` und enthält einen optionalen früheren Analysesnapshot sowie frühere Syntaxsnapshots. Die Engine überprüft deren Eignung selbst. Der Workspace darf Wiederverwendung anbieten, aber keinen Cache-Treffer als semantisch richtig deklarieren.

### 2.3.1 Exakte Trägerverträge

| Typ | Felder/Varianten |
|---|---|
| `SourceUpdate` | `SourceId source`, `SourceExpectation expectation`, `SourceChange change`. |
| `SourceExpectation` | Sealed Varianten `Absent()` und `At(SourceRevision revision)`. |
| `SourceChange` | Sealed Varianten `Replace(String text)` und `ApplyEdits(List<TextEdit> edits)`. |
| `TextEdit` | `SourceRange range`, `String replacement`; liegt in der Foundation unter `guru.interlis.source`. |
| `UpdateReceipt` | `SourceId source`, `Optional<SourceRevision> previousSourceRevision`, `Optional<SourceRevision> sourceRevision`, `WorkspaceRevision previousWorkspaceRevision`, `WorkspaceRevision workspaceRevision`, `boolean changed`, `List<InvalidationReason> reasons`. |
| `BatchUpdateReceipt` | Alte/neue WorkspaceRevision, `List<UpdateReceipt> updates` in Eingabereihenfolge, `List<InvalidationReason> reasons`. |
| `WorkspaceState` | WorkspaceRevision, unveränderliche Map SourceId→SourceRevision, fachlicher Konfigurationsfingerprint, Offen/Geschlossen-Zustand. |
| `AnalysisRequest` | `WorkspaceRevision expectedWorkspaceRevision`, nichtleere `List<ModelSelector> roots`. P1 analysiert die erreichbare Wurzelhülle; ein noch nicht unterstützter Analyseumfang wird nicht als wirkungsloses Flag eingeführt. |
| `WorkspaceCompileRequest` | `WorkspaceRevision expectedWorkspaceRevision`, nichtleere `List<ModelSelector> roots`; striktes Gate statt EDITOR-Akzeptanz. |
| `WorkspaceCapture` | `WorkspaceRevision workspaceRevision`, `CompilationRequest request`, `AnalysisReuse reuse`, `PreparedClosure resolution`, `InputFingerprint fingerprint`. |
| `AnalysisAcceptance` | Enum `ACCEPTED`, `STALE_NO_COMMIT`; kein neues Analyse- oder Kompilierungsoutcome. |
| `ConfigurationReceipt` | Alte/neue WorkspaceRevision, alter/neuer fachlicher Konfigurationsfingerprint, `boolean changed`, Invalidierungsgründe. |
| `InputFingerprint` | Algorithmuskennung und kanonischer Digest aller Capture-Eingaben; vom Snapshot-/Handle-Namensraum getrennt. |
| `WorkspaceMetrics` | Unveränderliche Zähler für Parses, Reuse, semantische Neuanalysen, Cacheeinträge/-Evictions und Abbrüche. Laufzeiten und verworfene Veröffentlichungen erfasst der zuständige Host-Observer beziehungsweise PublicationController. |

`parses` zählt tatsächliche Parseraufrufe des Workspace, `syntaxReuses` tatsächlich
übernommene passende Syntaxsnapshots. `semanticAnalyses` zählt vom Workspace
gestartete Engineaufrufe einschliesslich fehlgeschlagener/abgebrochener Aufrufe.
Extern ausgeführte Capture-Analysen misst der Host; ihre Annahme zählt unter
`acceptedAnalyses` beziehungsweise `staleAnalyses`. Eine Annahme darf nicht als
zusätzliche Neuanalyse gezählt werden. Cachezähler bezeichnen tatsächlich
gehaltene Einträge, keine Anzahl vorhandener Quelldateien.

Collections sind defensiv kopiert, null ist in Pflichtfeldern oder Optional-Containern unzulässig. `UpdateReceipt.sourceRevision` ist bei Entfernung leer; der interne Tombstone wird dadurch nicht offengelegt. Bei Batchreceipts beziehen sich alle Workspace-Revisionsfelder auf denselben atomaren Übergang. API-Träger dürfen keine offenen Ressourcen und keine mutierbaren Builder enthalten.

### 2.4 Methodenverträge für Änderungen

| Methode | Vorbedingungen | Nachbedingungen | Fehler und Abbruch |
|---|---|---|---|
| `update(Absent, Replace)` | Quelle aktuell abwesend; gültige URI-/Textgrenzen. | Neue Quelle mit frischer Revision; WorkspaceRevision steigt einmal. | `SOURCE_ALREADY_PRESENT`; keine Teilmutation. |
| `update(At(r), Replace)` | Quelle vorhanden und Revision genau r. | Exakter Text übernommen oder No-op; abhängige Caches markiert. | `SOURCE_MISSING`, `VERSION_CONFLICT`, `SOURCE_LIMIT_EXCEEDED`. |
| `update(At(r), ApplyEdits)` | Alle Edits beziehen sich auf r; Bereiche innerhalb der Quelle, nicht überlappend. | Alle Edits atomar angewandt; höchstens eine neue Quellrevision. | Ungültiger einzelner Edit verwirft den gesamten Änderungsaufruf. |
| `remove(id,r)` | Quelle vorhanden mit Revision r. | Quelle aus aktivem Zustand entfernt; Tombstone fortgeführt; Dependents invalidiert. | Fehlende Quelle und falsche Revision werden unterschieden. |
| `configure(config,r)` | WorkspaceRevision genau r; Konfiguration konsistent. | Vollständig neue Konfiguration oder unveränderter Zustand bei No-op. | Konflikt oder ungültige Konfiguration ohne Teilübernahme. |
| `source(id)` | Workspace offen. | Optionaler unveränderlicher Text des aktuellen Stands; kein Laden. | Kein Fehler für unbekannte URI. |
| `close()` | Keine besondere Vorbedingung; laufender synchroner Aufruf vorher beendet. | Interne Caches und Referenzen freigegeben; idempotent. | Bestehende externe Snapshots bleiben gültig; neue Operationen melden `WORKSPACE_CLOSED`. |

`SourceUpdate` enthält keine vom Aufrufer frei gewählte neue SourceRevision. Konflikte werden als `UpdateRejectedException` mit stabilem `UpdateRejectionCode`, erwarteter und beobachteter Revision gemeldet. Sie sind API-/Synchronisationsprobleme, keine INTERLIS-Compilerdiagnosen. Ausnahmeobjekte enthalten keinen vollständigen Quelltext.

`applyBatch(updates, expectedRevision)` übernimmt mehrere Quellen atomar. Es prüft zuerst die WorkspaceRevision und sämtliche SourceExpectations gegen denselben Ausgangszustand. Jede SourceId darf höchstens einmal vorkommen. Erst nach erfolgreicher Prüfung werden alle wirksamen Änderungen übernommen; die WorkspaceRevision steigt insgesamt einmal, jede tatsächlich geänderte Quelle erhält ihre eigene nächste Revision. Ein einziger Konflikt verwirft den gesamten Batch. Eine leere Liste oder ausschliessliche No-ops ändert keine Revision. `BatchUpdateReceipt` enthält alte/neue WorkspaceRevision, geordnete einzelne UpdateReceipts und die zusammengeführte Invalidierung. Dieser Pfad wird unter anderem für gemeinsam nachgeladene Importquellen verwendet; er ist kein unversionierter Seiteneingang in den Workspace.

`TextEdit` besteht aus `SourceRange range` und `String replacement`. In einem Core-`ApplyEdits` beziehen sich sämtliche Bereiche auf denselben Ausgangstext. Überlappende Ersetzungen und mehrere Einfügungen am gleichen Offset werden als mehrdeutig abgelehnt. Nicht überlappende Edits können intern absteigend nach Offset angewandt werden. Leere Editlisten sind No-ops.

Die in einer LSP-`didChange`-Nachricht enthaltenen Änderungen beziehen sich dagegen auf aufeinanderfolgende Zwischenstände. Der Adapter muss sie in ihrer empfangenen Reihenfolge materialisieren und anschliessend einen einzigen Core-`Replace` erzeugen; er darf sie nicht unverändert als Core-`ApplyEdits` weiterreichen. Diese LSP-Semantik ist in der [offiziellen didChange-Spezifikation](https://github.com/microsoft/language-server-protocol/blob/gh-pages/_specifications/lsp/3.17/textDocument/didChange.md) beschrieben.

### 2.5 Methodenverträge für Analyse und Veröffentlichung

`capture(request, token)` prüft die erwartete WorkspaceRevision, bereitet über den reinen Resolver die Wurzeln auf und friert sämtliche benötigten Quellen, Importbindungen, Optionen und den PreparedClosure-Bericht ein. Der Token wird an Headeranalyse und Closureaufbereitung weitergereicht; Budget- und Abbruchprüfungen beginnen vor dieser Arbeit. Fehlende Modelle werden als explizite fehlende Bindungen erfasst, nicht stillschweigend aus dem Request entfernt. I/O innerhalb der Methode ist verboten. Die Beschaffung benötigter Quellen erfolgt durch den Host gemäss Kapitel 08 vor beziehungsweise zwischen Analyseläufen.

`capture(request)` delegiert mit einem Token, der nie einen externen Abbruch anfordert. Die konfigurierten Ressourcenbudgets bleiben trotzdem wirksam. `ResolutionCancelled` wird in `OperationCancelledException` mit gleichem Grund übersetzt; eine interne `ResolutionFailed`-Fehlfunktion in den internen Fehlerpfad mit FailureId. Keine der beiden Varianten erzeugt eine scheinbar normale WorkspaceCapture. Regulär fehlende, mehrdeutige oder technisch blockierte Eingaben bleiben dagegen PreparedClosure-/SourceSet-Daten.

Die Modellauflösung arbeitet dabei auf einem unveränderlichen `SourceInventory` mit bereits vom Workspace vergebenen Revisionen. `SourceProvider.snapshot()` liefert ausschliesslich diesen eingefrorenen Bestand. `ModelResolutionService.prepare(...)` erzeugt daraus eine aufgelöste oder explizit unvollständige Closure samt möglichen `PendingSourceRequest`-Einträgen. Ein Repository-Host lädt fehlende `LoadedSource` ohne eigene Quellrevision und installiert deren Text durch `update` beziehungsweise `applyBatch`. Anschliessend werden Inventory und Closure neu gebildet. Ein alter Auflösungsstand darf nach einer Workspace-Änderung nicht mit neuen Quelltexten vermischt werden.

`analyze(request, token)` erzeugt über den tokenfähigen Capturepfad eine solche Eingabe und analysiert sie mit `ParseMode.EDITOR`. Es liefert einen konsistenten, unveränderlichen `AnalysisSnapshot` oder wirft `OperationCancelledException`. Ein Source- oder Semantikfehler ist Teil der Diagnostik und keine Exception. Der direkte Aufruf übernimmt den regulär abgeschlossenen Analyselauf intern über denselben `acceptAnalysis`-Vertrag, der dem abgetrennten Hostablauf zur Verfügung steht.

Unerwartete Enginefehler folgen `CompilerInternalException` mit FailureId aus Kapitel 06; der Workspace darf sie nicht als leeren Analysesnapshot verbergen. Die LSP-/Wasm-Adapter übersetzen diesen Pfad in ihre gesonderten internen Fehlerantworten.

`CancellationToken`, `OperationCancelledException` und `CancellationReason` stammen aus `guru.interlis.execution`. Die Gründe sind `USER_REQUESTED`, `SUPERSEDED`, `DEADLINE_EXCEEDED`, `RESOURCE_LIMIT` und `HOST_SHUTDOWN`. Ein ausgeschöpftes semantisches AnalysisBudget führt bei `analyze` zu `OperationCancelledException(RESOURCE_LIMIT)` und bei `compile` zu `Cancelled(RESOURCE_LIMIT)` mit höchstens einer ausdrücklich unvollständigen Teilsicht. Quellupdate-Vorbedingungen und unzulässige Argumente bleiben davon getrennte API-Fehler.

`compile(request, token)` arbeitet über die strikte Prüfung aus Kapitel 06. Sein Ergebnis ist `Succeeded`, `Rejected`, `Cancelled` oder `Failed`. Ein früher erfolgreiches Schema wird bei aktuellem Fehler nicht als Erfolg zurückgegeben. Eine nur rekonstruierte Fehlerinsel darf niemals das Produktions-Gate erfüllen.

`Rejected` enthält neben der Analyse die unveränderte `RejectionSummary` des CompilationGate. Workspace, LSP und Browser leiten die Ablehnungsgründe nicht aus einer ungefilterten globalen Diagnosemenge neu ab; die Summary ist auf die angeforderte Wurzelhülle bezogen.

Die vollen Workspace-Overloads `analyze(request, context, token)` und `compile(request, context, token)` reichen den frischen `RunContext` aus `guru.interlis.execution` an `engine.analyze(capture.request(), capture.reuse(), context, token)` beziehungsweise den gleich aufgebauten Compile-Overload weiter. Die Engine besitzt allein Start und Abschluss dieses Laufs. Der Workspace darf dieselbe RunId nicht zusätzlich starten oder beenden. Die Overloads ohne expliziten Kontext benutzen die Engine-Convenience-Overloads, die ihren Kontext gemäss Kapitel 06/09 erzeugen. Ein Host, der Auflösung, Engine und anschliessenden Export als eigenen übergeordneten Lauf erfassen will, übergibt der Engine einen frischen Kindkontext aus `RunContextFactory.createChild(parent)`; bereits gestartete Hostlaufkontexte dürfen nicht als Enginelauf wiederverwendet werden. Laufmetadaten sind keine semantischen Cacheeingaben.

### 2.6 Bedingte Übernahme einer abgetrennten Analyse

`acceptAnalysis(capture, analysis)` ist eine kurze, serialisierte Workspace-Mutation auf derselben Ownerqueue wie `update`, `configure` und `capture`. Die rechenintensive Enginearbeit findet vorher ausserhalb dieser Queue statt. Die Methode startet keine neue Auflösung, keine Parse-/Semantikrunde und keine I/O. Sie prüft vorhandene unveränderliche Fingerprints und Herkunftsdaten und übernimmt geeignete Wiederverwendungsdaten atomar.

Der verbindliche Ablauf lautet:

1. Workspace muss offen sein. Capture und Analyse müssen nicht null sein; der Capture muss von dieser Workspaceinstanz stammen. Fremde Captures werden auch bei zufällig gleicher Revision zurückgewiesen.
2. Die Analyse muss den im Capture enthaltenen Compilerrequest wiedergeben: dieselben Wurzelselektoren, Sprach-/Semantikoptionen, Overlays, eingefrorenen Quellrevisionen und Inhalte, Importbindungen sowie Grammatik-/Builtin-Fingerprints. Die Snapshotprovenienz und `CompilationFingerprint` werden gegen den nach Kapitel 06 aus dem tatsächlichen `capture.request()` gebildeten Compilerfingerprint geprüft. Der umfassendere Workspace-`InputFingerprint` ist davon getrennt; ein unmittelbares `equals` zwischen diesen verschiedenen Trägertypen ist kein gültiger Vergleich.
3. Der Lauf muss regulär abgeschlossen sein und die Snapshotinvarianten aus Kapitel 05/06 erfüllen. Quellfehler, nicht auflösbare Namen und ausdrücklich nicht implementierte Sprachmerkmale verhindern für sich genommen keinen Reuse. Ein durch Abbruch oder internen Fehler abgetrenntes Teilergebnis ist kein regulär abgeschlossener Analyselauf.
4. Stimmen aktueller Workspace-Stand, sämtliche erfassten Quellrevisionen/Inhaltsfingerprints und die fachlichen Konfigurations-/Resolverfingerprints nicht mehr mit dem Capture überein, lautet das Ergebnis `STALE_NO_COMMIT`. In P1 genügt bereits eine andere WorkspaceRevision für diesen Ausgang. Kein alter Abhängigkeitsindex, Syntaxcache, Querycache oder letzter Analysezeiger wird übernommen.
5. Bei aktuellem Stand werden die geeigneten Syntax-/Analyse-Reuse-Kandidaten und die vollständig erfassten positiven und negativen Abhängigkeiten gemeinsam installiert. Cachelimits dürfen Einträge dabei auslassen oder verdrängen. Das Ergebnis lautet `ACCEPTED`; SourceRevision und WorkspaceRevision bleiben unverändert.

Ein zum Capture inkonsistenter Snapshot, ein fremder Capture und ein nicht regulär abgeschlossenes Teilergebnis sind API-Vertragsverletzungen und führen zu `IllegalArgumentException` mit konkreter Fehlerbeschreibung. Sie dürfen nicht als normale Veraltung kaschiert werden. Der geschlossene Workspace verwendet den bestehenden `WORKSPACE_CLOSED`-Pfad. Diese Prüfungen und die Entscheidung finden vor jeder Cachemutation statt. Der Commit besitzt keinen eigenen Cancel-Parameter: Nach der Annahme eines abgeschlossenen Ergebnisses darf kein halber Cache-/Abhängigkeitsstand sichtbar werden.

Eine wiederholte Übernahme desselben Captures und desselben Snapshots ist bei weiterhin aktuellem Eingabestand idempotent: `ACCEPTED`, aber keine doppelten Einträge oder erneut gezählten semantischen Berechnungen. Metriken dürfen einen getrennten Annahmeversuch zählen, wenn dessen Bedeutung dokumentiert ist.

Der synchrone Komfortpfad `Workspace.analyze` verwendet intern diesen Commit. `Workspace.compile` verwendet ihn für die abgeschlossene Analyse in `Succeeded` und `Rejected`, während `Cancelled` und `Failed` keinen vollständigen Analyse-/Abhängigkeitscommit auslösen. Der Compiler darf den Erfolg eines alten eingefrorenen Requests weiterhin korrekt beschreiben; der Workspace entscheidet getrennt über aktuelle Wiederverwendung und Veröffentlichung.

`PublicationController.accept(capture, snapshot, currentState)` bleibt ein Hostvertrag für die Veröffentlichung und ersetzt `Workspace.acceptAnalysis` nicht. Der Host übernimmt zuerst die Analyse. Nur nach `ACCEPTED` prüft er zusätzlich Dokumentgeneration, LSP-Version beziehungsweise Worker-Epoche und sendet die darauf bezogene Darstellung. Zwischen Annahme und Einreihen der Veröffentlichung darf kein ungeprüftes Workspace-Update auftreten: Beide Schritte laufen in derselben Ownerqueue-Aktion, oder der Publisher prüft unmittelbar vor dem Versand nochmals den erfassten Stand. `STALE_NO_COMMIT` veröffentlicht keine aktuellen Diagnosen und erzeugt keinen aktuellen Diagramm-/Renamezustand.

Später darf eine unabhängig geänderte Quelle bei der Annahme oder Veröffentlichung ignoriert werden, falls der vollständige Abhängigkeitsfingerprint nachweislich unverändert ist. Diese Optimierung benötigt eigene Tests und muss auch negative Lookups und den Resolverkatalog berücksichtigen.

Ein veraltetes Ergebnis darf zur Diagnose interner Laufzeiten aufbewahrt werden. Eine Oberfläche darf es ausdrücklich als alten Modellstand anzeigen. Es darf weder aktuelle Fehler ersetzen noch als Grundlage für Rename oder andere Textänderungen dienen.

## 3. Abhängigkeiten und Caches

### 3.1 Cachearten

| Cache | Schlüssel | Wert | Unzulässige Abkürzung |
|---|---|---|---|
| Syntax | Exakter Inhaltshash, Sprachprofil, Grammatikbuild, ParseMode, parserwirksame Optionen. | Syntaxstruktur ohne unbeprüfte fremde Positionsbindung. | Dateipfad oder Änderungszeit allein. |
| Dateigebundener Syntaxsnapshot | Syntaxschlüssel plus SourceId und SourceRevision. | Vollständiger `SyntaxSnapshot` mit korrekten Bereichen. | Alten SourceRange in neuen Snapshot kopieren. |
| Analyse | Wurzelauswahl, alle Eingabefingerprints, Importe, Builtins, Overlays, semantische Optionen. | Vollständiger `AnalysisSnapshot` und Abhängigkeiten. | Nur Namen exportierter Klassen vergleichen. |
| Modell-/Root-Closure | Wurzeln plus relevante aufgelöste und negative Abhängigkeiten. | Geprüfte Auswahl und deterministische Abhängigkeitsreihenfolge. | Fehlende Imports nicht im Schlüssel führen. |
| IDE-Abfrage | SnapshotId, Querytyp, Position/Referenz, Optionen. | Unveränderliches Queryresultat. | Queryresultat allein per FQN sitzungsweit speichern. |

P1 darf einfachere, gröbere Schlüssel verwenden, sofern sie konservativ korrekt sind. Beispielsweise darf jede semantische Änderung einer importierten Quelle ihre gesamten Dependents invalidieren. Das ist einer unvollständigen vermeintlich präzisen Analyse vorzuziehen.

Ein Inhaltshash kann unabhängig von der URI gleich sein. Trotzdem ist ein Syntaxsnapshot mit Bereichen einer anderen SourceId nicht wiederverwendbar. Entweder werden quellungebundene Strukturen separat geteilt und korrekt neu gebunden, oder P1 verwendet die gröbere dateigebundene Variante.

### 3.2 Abhängigkeitsdatentypen

`DependencyIndex` arbeitet mit einer geschlossenen `DependencyKey`-Hierarchie:

- `SourceDependency(SourceId)` für gelesene Quelldaten;
- `ModelLookupDependency(ModelSelector, ResolverFingerprint)` für erfolgreiche, fehlende oder mehrdeutige Modellauflösung;
- `ScopeLookupDependency(ScopeId, NameKey, LookupKind)` für gelesene Namensmengen;
- `SymbolDependency(SymbolId, SemanticFacet)` für feinere spätere Abhängigkeiten;
- `ConfigurationDependency(ConfigurationFacet)` für Optionen, Builtins oder Overlays.

`SemanticFacet` unterscheidet in P3 mindestens Signatur, effektive Vererbung, Constraint-Inhalt, Übersetzungsbezug und Dokumentation. P1 darf `WHOLE_SYMBOL` verwenden. Die Darstellung muss die genauere Unterscheidung ermöglichen, ohne sie vorsorglich überall zu implementieren.

Interne Methoden:

```java
DependencySet dependenciesOf(AnalysisUnitId unit);
Set<AnalysisUnitId> dependentsOf(Set<DependencyKey> changed);
InvalidationPlan plan(ChangeSummary change, WorkspaceState state);
void replace(AnalysisUnitId unit, DependencySet completeDependencies);
void remove(AnalysisUnitId unit);
```

`replace` erfolgt erst für eine vollständig abgeschlossene, konsistente Analyseeinheit. Ein Abbruch darf keine halbe neue Abhängigkeitsmenge installieren. Der `InvalidationPlan` enthält betroffene Einheiten und maschinenlesbare Gründe. Reihenfolgen in Logs und Tests sind deterministisch sortiert.

Snapshotgebundene IDs in internen DependencyKeys sind immer im Kontext ihrer `AnalysisUnitId` samt Herkunftssnapshot zu interpretieren. Der Index darf zwei nackte ScopeIds aus unabhängigen Snapshots nicht gleichsetzen. Für den Vergleich eines alten und neuen Zustands verwendet er belegte Identitätszuordnung oder konservative Quell-/Scopeinvalidierung. In P1 genügt die gröbere Quellenebene; falsch zugeordnete feingranulare Schlüssel sind keine zulässige Optimierung.

### 3.3 Negative Abhängigkeiten

Beispiel: `Survey` importiert `Base`, aber `Base` fehlt. Die Analyse erzeugt `ILI-RES-0001` und vermerkt einen negativen Modelllookup. Wenn eine neue Quelle `Base` enthält, müssen die betroffenen Importe erneut aufgelöst werden. Ein alleiniger Graph aus erfolgreichen Importkanten kann diesen Fall nicht erkennen.

Dasselbe gilt innerhalb eines Scopes: Ein Ausdruck bindet den Namen `height` zunächst nicht. Wird `height` später hinzugefügt, müssen die bisher fehlgeschlagenen Lookups erneut ausgewertet werden. Wird ein zweiter passender Kandidat hinzugefügt, muss auch ein bisher erfolgreicher Lookup mehrdeutig werden können.

Ein Resolverfehler durch temporär fehlenden Netz-/Dateizugriff ist kein unbegrenzt gültiger Beweis, dass das Modell nicht existiert. Die Repositoryschicht liefert explizite Zustände und Fingerprints; Wiederholungs- oder Ablaufregeln liegen im Host. Der Compiler erhält den zu diesem Lauf gültigen Zustand als Eingabe.

### 3.4 Invalidierungsmatrix

| Änderung | P1-Verhalten | Spätere zulässige Verfeinerung |
|---|---|---|
| Kommentar/Whitespace | Geänderte Datei neu parsen; abhängige Analyse konservativ erneuern. | Dokumentation/Positionen aktualisieren, unveränderte fachliche Ergebnisse teilen. |
| Attributtyp | Datei und semantische Dependents neu analysieren. | Nur Signatur-/Typ-/Constraint-Dependents erneuern. |
| Constraint | Betroffene Hülle erneuern. | Validierungs- und Übersetzungsabhängigkeiten getrennt erneuern. |
| Import hinzugefügt/entfernt | Closure und Namensauflösung neu berechnen. | Nur betroffene Importscopes erneuern. |
| Neue Datei | Deklarationsindex sowie positive und negative Lookups prüfen. | Indexdifferenz zur Eingrenzung nutzen. |
| Quelle gelöscht | Alle bisherigen Verbraucher plus relevante Lookups invalidieren. | Gleiches semantisches Mindestverhalten. |
| Quelle verschoben | Hostseitig remove/add oder explizite spätere Rename-Transaktion. | Identitätsmigration nur bei belegbarer Zuordnung. |
| Sprachversion geändert | Syntax und gesamte betroffene Semantik erneuern. | Kein versionsübergreifender ungeprüfter Reuse. |
| Resolver-/Overlaywechsel | Betroffene Bindungen und fachliche Projektionen erneuern. | Granularer Konfigurationsfingerprint. |

## 4. Identität, Referenzen und Positionsgenauigkeit

Ein `SymbolRef` enthält `SnapshotId` und `SymbolId`. Auch wenn die SymbolId einer unveränderten Klasse erhalten bleibt, ist der Ref eines alten Snapshots kein Ref des neuen Snapshots. Querydienste prüfen beides. Eine explizite `rebind(previousRef, newSnapshot)`-Funktion darf später angeboten werden; sie muss `Unresolved` liefern, wenn die Identitätskontinuität nicht belegbar ist.

Der interne `IdentityReconciler` des Compilers vergleicht vorherige Syntax und Analyse mit dem neuen Stand. Geeignete Anker sind dokumentierte Deklarationsart, zugeordneter Container, eindeutige lokale Namen und editbedingte Positionszuordnung. Ein globaler Zähler oder der komplette FQN allein ist kein geeigneter Identitätsvertrag. Bei zwei gleichnamigen fehlerhaften Deklarationen darf der Reconciler nicht anhand einer zufälligen Map-Reihenfolge raten.

Öffentliche Positionsbereiche sind halb offen und in UTF-16-Codeeinheiten angegeben. Die Quelltextschicht stellt eine `LineMap` und die Abbildung von ANTLR-Codepoint-Offsets bereit. CRLF zählt als ein Zeilenwechsel; Originaltext und absolute Offsets bleiben unverändert. Endpositionen nach einem abschliessenden Zeilenwechsel müssen auf die vorhandene leere letzte Zeile zeigen können.

Die LSP-Positionskodierung wird bei `initialize` ausgehandelt. P1 bietet ausschliesslich UTF-16 an, das nach der [LSP-Positionsspezifikation](https://github.com/microsoft/language-server-protocol/blob/gh-pages/_specifications/lsp/3.17/types/position.md) der verpflichtende Standard ist. Andere Kodierungen dürfen erst nach vollständiger getesteter Umrechnung angeboten werden. Eine grössere LSP-Zeichenspalte wird im Adapter entsprechend dem Protokoll auf das Zeilenende begrenzt; der interne Bereichstyp bleibt streng und akzeptiert keine ungültigen Offsets.

Öffentliche Queries mit fremdem SymbolRef verwenden die `SnapshotMismatchException` aus Kapitel 05. Ungültige SourceRanges werden vor dem fachlichen Lookup anhand Quelle, Revision und tatsächlicher Textlänge zurückgewiesen. Ein Snapshot mit belegtem fachlichem Fehler liefert dagegen `Invalid` beziehungsweise `Unresolved`; das ist kein API-Fehlgebrauch.

Referenzen auf `Model.Topic.Class` benötigen getrennte Quellbereiche für jedes Namenssegment. Deklarationsname, Endname bei `END`, Referenzverwendungen und synthetische Standardmodellbezüge müssen unterscheidbar sein. Für synthetische Elemente darf kein erfundener editierbarer SourceRange entstehen.

## 5. Reine Sprachabfragen

`LanguageQueries` ist zustandslos; Eingabe ist immer ein expliziter Snapshot. Sie darf weder Repositoryzugriffe starten noch den Workspace verändern. Für die fachliche Antwort verwendet sie die `SemanticQueries` aus Kapitel 05 und den dort definierten `QueryResult<T>` mit `Known`, `Unresolved`, `Invalid` und `NotAnalyzed`.

```java
public interface LanguageQueries {
    QueryResult<SymbolRef> symbolAt(
        AnalysisSnapshot snapshot, SourceRange cursor, CancellationToken token);
    QueryResult<DefinitionTarget> definition(
        AnalysisSnapshot snapshot, SourceRange cursor, CancellationToken token);
    QueryResult<HoverInfo> hover(
        AnalysisSnapshot snapshot, SourceRange cursor, CancellationToken token);
    CompletionResult complete(
        AnalysisSnapshot snapshot, CompletionQuery query, CancellationToken token);
    ReferenceResult references(
        AnalysisSnapshot snapshot, SymbolRef symbol,
        ReferenceOptions options, CancellationToken token);
    RenamePreparation prepareRename(
        AnalysisSnapshot snapshot, SourceRange cursor, CancellationToken token);
    RenamePlan planRename(
        AnalysisSnapshot snapshot, RenameRequest request, CancellationToken token);
}
```

`cursor` ist ein SourceRange mit gleicher Start- und Endposition. Die Auswahlregel ist deterministisch: Liegt der Cursor innerhalb eines Bezeichners, wird dieser gewählt; an seinem rechten Ende wird er gewählt, sofern dort kein unmittelbar beginnender anderer Bezeichner liegt. Whitespace ohne angrenzenden identifizierbaren Namen ergibt `Unresolved`, keinen willkürlich nächstgelegenen Namen.

| Ergebnis | Erforderliche Daten | Vertrag |
|---|---|---|
| `DefinitionTarget` | SymbolRef, Deklarationsbereich, Namensbereich, Herkunft. | Keine Zielposition aus einem anderen Revisionstand. |
| `HoverInfo` | Typisierte Signatur, Dokumentation, Auflösungszustand, Quellbereich. | Keine HTML-Ausführung; Host rendert escaped Markdown/Plaintext. |
| `CompletionResult` | Kandidaten, Ersetzungsbereich, `isIncomplete`, betrachteter Snapshot. | Sichtbarkeit und Sprachversion berücksichtigen; unvollständiges Ergebnis ausdrücklich markieren. |
| `ReferenceResult` | Geordnete Verwendungen, Suchumfang, Vollständigkeit. | Unvollständig durchsuchten Workspace nicht als vollständige Referenzmenge darstellen. |
| `RenamePreparation` | Erlaubt/abgelehnt, editierbarer Namensbereich, Grund. | Keine Freigabe für mehrdeutige, synthetische oder schreibgeschützte Symbole. |
| `RenamePlan` | Erwartete Quellrevisionen, nicht überlappende Edits je Quelle, betroffene SymbolRef, Prüfergebnis. | Plant Änderungen; schreibt keine Dateien und mutiert keinen Workspace. |

### 5.1 Completion

P1 muss Modelle, Topics, Klassen, Attribute und Domänen der implementierten fachlichen Strecke im passenden Scope anbieten können. Syntaxvorschläge und semantische Kandidaten erhalten getrennte Provenienz. Die Sortierung ist stabil: passende Kategorie, lokaler Kontext, Präfixpassung, qualifizierter Name als deterministischer Tie-Breaker. Laufzeitabhängige Map-Reihenfolge ist verboten.

Die Engine darf bei kaputter Syntax einen nächstgelegenen belegten Scope benutzen, muss diese Abschwächung in `isIncomplete` beziehungsweise in der Kandidatenprovenienz ausdrücken. Sie darf keine erfundene semantische Bindung erzeugen. Automatische zusätzliche Imports sind eine spätere Code-Action-Fähigkeit mit eigenem RenamePlan-artigem Änderungsvertrag.

### 5.2 Rename

P3-Completion liefert den gesamten aktuellen Bezeichner als Ersetzungsbereich,
auch wenn der Cursor in dessen Mitte steht. Der Adapter erhält Inserttext,
stabile Rangfolge und Unvollständigkeit im `CompletionList`. Die ergänzten
`CompletionKind`-Werte `FUNCTION`, `UNIT`, `VIEW`, `VIEW_BASE`, `CONTEXT` und
`ENUM_ELEMENT` bleiben semantische Kategorien; Syntaxkandidaten bleiben
`KEYWORD` mit versionsbezogener Detailangabe. Qualifizierte Vervollständigung
verwendet gebundene Referenzsegmente und `membersOf`; eine Syntaxrecovery
benutzt dieselben veröffentlichten Sichtbarkeits-/Typfakten und markiert
unbestimmbare Qualifizierer ausdrücklich als unvollständig.

Workspace-weites Rename ist P3. Vor der Aktivierung müssen der neue Name unter der betreffenden Sprachversion lexikalisch gültig, relevante Scopes vollständig genug analysiert und Namenskollisionen geprüft sein. Änderungen betreffen gebundene Referenzen, Deklarationsnamen und zugehörige Endnamen. Gleich geschriebene Texte in Kommentaren, Zeichenketten oder fremden Scopes werden nicht textuell ersetzt.

Der Plan enthält `DocumentEdit(SourceId, SourceRevision expected, List<TextEdit>)`. Alle Edits einer Quelle beziehen sich auf denselben Snapshot. Vor Rückgabe wird geprüft, dass sich keine Edits überlappen und dass jedes Originalsegment tatsächlich dem gebundenen alten Namen entspricht. Bei zwischenzeitlicher Revisionänderung wird neu geplant oder mit `CONTENT_MODIFIED` abgelehnt.

Sind benötigte Referenzquellen nicht verfügbar oder schreibgeschützt, muss Rename den unvollständigen Umfang erklären und im anfänglichen sicheren Modus ablehnen. Der Agent darf keine scheinbar erfolgreiche Teilumbenennung anbieten. Modelle in verwalteten Repositories werden nicht ohne explizites editierbares Workspace-Mapping geändert.

Die [offizielle LSP-Rename-Spezifikation](https://github.com/microsoft/language-server-protocol/blob/gh-pages/_specifications/lsp/3.17/language/rename.md) beschreibt die Rückgabe als WorkspaceEdit, das der Client anwendet. Der Server schreibt deshalb bei einem Rename-Request nicht zusätzlich selbst dieselben Dateien.

## 6. LSP-Adapter

Das P3-Referenzprofil verweigert vollständige Referenz-/Renamezusagen bei
unvollständiger Abdeckung oder Fehlerdiagnostik. Jeder geänderte Quellstand
braucht einen expliziten passenden Eintrag in `RenameRequest.expectedRevisions`.
Kollisionsprüfung berücksichtigt den bestehenden strikten äusseren
Typnamensraum und veröffentlichte effektive/geerbte Mitglieder. Fehlende
Revisionen ergeben einen unvollständigen Plan mit `CONTENT_MODIFIED`.
Geschlossene lokale Projektquellen werden vor der Rückgabe auf unveränderten
Bytehash, reales Pfadziel und Schreibrecht geprüft. Mit Clientunterstützung
enthält `WorkspaceEdit.documentChanges` die genaue offene Clientversion;
bei geschlossenen Quellen bleibt die LSP-Version null. Der Server schreibt
keine Rename-Dateien. Ohne diese Clientfähigkeit bleibt `changes` verfügbar.

### 6.1 Anforderungen und Ausbau

| ID | Normative Anforderung | Phase | Abnahme |
|---|---|---|---|
| LSP-001 | Der JVM-LSP MUSS ein dünner LSP4J-Adapter um Workspace und LanguageQueries sein. | P1 | Keine zweite Binder-/Typprüfungsimplementierung im LSP-Modul. |
| LSP-002 | `initialize`, `initialized`, `shutdown`, `exit` und Dokumentlebenszyklus MÜSSEN protokollkonform funktionieren. | P1 | Prozess-/Protokolltest einschliesslich sauberem Shutdown. |
| LSP-003 | Geöffnete Dokumente MÜSSEN aus Clientinhalt analysiert werden; Dateiwatcher dürfen diesen nicht überschreiben. | P1 | Ungespeicherte Änderung und gleichzeitige Dateimeldung. |
| LSP-004 | `didChange`-Änderungen MÜSSEN sequenziell interpretiert und als atomarer SourceUpdate übernommen werden. | P1 | Mehrere abhängige Änderungen innerhalb einer Notification. |
| LSP-005 | Diagnostik MUSS aus Diagnostic-Daten entstehen; Logparsing ist verboten. | P1 | Gleiche Codes/Ranges in CLI-JSON, LSP und Browser. |
| LSP-006 | Leere aktuelle Diagnostik MUSS frühere Diagnostik ersetzen. | P1 | Fehler korrigieren; Client empfängt leere Liste. |
| LSP-007 | Navigation, Hover, Dokumentgliederung und elementare Completion MÜSSEN für die P1-Strecke funktionieren. | P1 | Import, Vererbung und fehlerhafter Nachbar im Editorfixture. |
| LSP-008 | Das Startkommando MUSS stdio als standardisierten Transport anbieten; stdout enthält nur Protokoll. | P1 | Logausgaben und Stacktraces verunreinigen den Kanal nicht. |
| LSP-009 | Globale Compile-Locks und Manipulation bestehender EhiLogger-Listener DÜRFEN nicht Teil der neuen Integration sein. | P1 | Architekturprüfung und parallele unabhängige Serverinstanzen. |
| LSP-010 | Capabilities DÜRFEN nur für tatsächlich implementierte und geprüfte Funktionen angekündigt werden. | P1–P3 | Capability-Snapshot je Phase. |
| LSP-011 | Request-Abbruch MUSS in den Compiler weitergeleitet oder durch dokumentierten Worker-Abbruch umgesetzt werden. | P1 | Abbruchantwort und anschliessend funktionsfähige Sitzung. |
| LSP-012 | Workspace-weite Referenzsuche und Rename MÜSSEN Vollständigkeit und Revisionskonsistenz prüfen. | P3 | Mehrdatei-Rename, Kollision, fehlende Quelle, Stale-Ergebnis. |
| LSP-013 | Semantic Tokens, Folding und Formatierung SOLLEN dieselbe Syntax-/Semantikbasis nutzen. | P3 | Keine Regex-Nachimplementierung semantischer Namensauflösung. |
| LSP-014 | Eine Diagrammabfrage MUSS denselben Snapshot wie ihre Navigation verwenden. | P1 | Modelländerung während Layout; alte Quellverweise abgelehnt. |
| LSP-015 | Repositoryänderungen MÜSSEN die bereits geöffnete abhängige Dokumentanalyse aktualisieren. | P2 | Importdatei extern aktualisiert, keine Clienttextänderung. |
| LSP-016 | Transport-, Compiler- und internes Fehlerverhalten MÜSSEN auseinandergehalten werden. | P1 | Ungültiges JSON-RPC, ungültiges INTERLIS und interner Fehler erhalten verschiedene Antworten. |

### 6.2 Klassen und Methoden

| Klasse | Öffentliche/adapterinterne Methoden | Zweck und wesentliche Grenze |
|---|---|---|
| `InterlisLanguageServer` | `initialize`, `initialized`, `shutdown`, `exit`, `connect` | LSP4J-Lebenszyklus und Capability-Verhandlung; keine Fachanalyse. |
| `InterlisTextDocumentService` | `didOpen`, `didChange`, `didClose`, `didSave`, `hover`, `definition`, `completion`, `documentSymbol` | Protokollobjekte in Domainrequests umwandeln. |
| `InterlisWorkspaceService` | `didChangeConfiguration`, `didChangeWatchedFiles`, spätere Workspace-Suche | Hostauflösung und Invalidierung anstossen. |
| `DocumentStore` | `open(uri,version,text)`, `change(uri,version,changes)`, `close(uri)`, `current(uri)` | Clientversion und Core-Quellrevision zuordnen; Clientinhalt besitzt Vorrang. |
| `AnalysisScheduler` | `schedule(source)`, `requestNow(query)`, `cancel(requestId)`, `close()` | Änderungen zusammenfassen, neueste Arbeit priorisieren, Revisionsstand erhalten. |
| `DiagnosticPublisher` | `publish(snapshot,documentVersions)`, `clear(uri)` | Clientfähigkeiten berücksichtigen; Full-Replacement und Stale-Prüfung. |
| `PositionMapper` | `toSourceRange`, `toLspRange`, `toOffset`, `toPosition` | URI, Revision, Zeilen und Kodierung prüfen; keine freie Positionsarithmetik in Handlern. |
| `LspErrorMapper` | `mapProtocolFailure`, `mapCancellation`, `mapInternalFailure` | Stabile Protokollfehler; keine Compilerfehler als Transportfehler ausgeben. |

P1 verwendet Push-Diagnostik. Pull-Diagnostik darf später ergänzt werden, wenn Result-IDs, Related Documents und Invalidierung sauber implementiert sind; beide Modi dürfen nicht unkoordiniert dieselben Diagnosen doppelt erzeugen.

Der DocumentStore behandelt die LSP-Version pro geöffnetem Dokumentlebenszyklus. Bei erneutem Öffnen darf der Client eine neue Versionsfolge beginnen; die Core-SourceRevision wird dadurch nicht zurückgesetzt. Asynchron geplante Arbeiten erhalten beim Einreihen eine interne Dokumentgeneration; Ergebnisse und noch nicht angewandte Hostaktionen einer abgeschlossenen Generation dürfen den neu geöffneten Text nicht verändern. Die LSP-Notifications selbst werden in empfangener Protokollreihenfolge verarbeitet. Ein No-op-Textupdate kann die LSP-Version erhöhen, obwohl die Core-Revision unverändert bleibt.

Die [LSP-Diagnostikspezifikation](https://github.com/microsoft/language-server-protocol/blob/gh-pages/_specifications/lsp/3.17/language/publishDiagnostics.md) verlangt, dass neue Diagnostik die frühere ersetzt. Der Publisher muss deshalb auch eine leere Liste senden. Eine empfangene `didClose`-Meldung übergibt die Inhaltshoheit zurück an die Quelle der URI; sie bedeutet bei einer projektbasierten Sprache nicht automatisch, dass das Dokument aus dem Workspace verschwindet. Siehe [didClose](https://github.com/microsoft/language-server-protocol/blob/gh-pages/_specifications/lsp/3.17/textDocument/didClose.md).

### 6.3 Scheduling und Fehlerfälle

Das gleiche Konfigurationsobjekt kann in `initialize.initializationOptions`
übergeben werden; es wird vor der Projektaufnahme validiert und übernommen.
Discovery-Grenzen (10000 Dateien/32 Millionen UTF-16-Einheiten) ersetzen nicht
die Compilerbudgets (standardmässig 2097152 Einheiten pro Quelle und 8388608
gesamt). Eine überschrittene Projektquellgrenze ergibt einen sicheren
`RequestFailed` mit `SOURCE_LIMIT_EXCEEDED`; eine erschöpfte Analysegrenze
`RequestCancelled` mit dem ursprünglichen `CancellationReason`, insbesondere
`RESOURCE_LIMIT`. Der Zustand kann durch passende explizite Konfiguration
beziehungsweise einen neuen Initialisierungsversuch mit neuen Serverressourcen
wiederhergestellt werden; Limits werden nicht automatisch angehoben.

Abgelehnte `didOpen`-/`didChange`-Updates erhalten ihre ursprüngliche sichere
Ablehnungskennung im Betriebskanal. Der abgelehnte Clienttext wird nicht als
angenommene Quelle ausgegeben oder zusätzlich für die Wiederaufnahme gehalten.
Bis zum gültigen Vollersatz, Wiederöffnen oder Schliessen verweigert das
anfängliche sichere Profil Sprachabfragen und Compile mit `ContentModified`;
es gibt keinen stillen Erfolg auf dem vorherigen Text. Clientversionen der
abgelehnten Generation bleiben für die anschliessende Resynchronisation bekannt.
Ein interner Compilerfehler behält dieselbe `FailureId` in Antwort und
Betriebsmeldung; technische Exceptions bleiben vom RPC-Datenobjekt ausgeschlossen.

Die Dateiidentität wird auf der I/O-Seite mit realen Pfaden beziehungsweise
Dateischlüsseln bestimmt. Bekanntes logisches SourceId-Mapping bleibt erhalten;
eine noch nicht zugeordnete Client-URI bleibt bis zu belegter Dateizuordnung
unverändert, auch bei plattformabhängiger Pfadsyntax.
Die exakte offene Client-URI wird getrennt für Diagnostik, Navigation und
Versionsedits benutzt. Alias-Watcher erzeugen keine zweite Compilerquelle und
überschreiben keine offene Clientquelle. Ein symlinkter Workspace-Root wird
über seinen realen Ordner durchsucht, mit logischen Pfaden für die aufgenommenen
Dateien. Hardlink-Aliasse behalten das explizite editierbare Primärmapping.
Bei Löschmeldungen wird der aktuelle Zustand über den I/O-Adapter geprüft;
das Entfernen eines Alias allein entfernt keine weiterhin vorhandene Primärdatei.
Die Identitätsablage ist auf 40000 Pfadzuordnungen begrenzt.
Die Projektsuche fasst physische Dateialiasse bereits vor der Quellaufnahme
zusammen und bevorzugt einen vorhandenen nicht-symbolischen Projektpfad.
Unbekannte Datei-URIs aus `didOpen` werden über die begrenzte I/O-Queue
zugeordnet; folgende Dokumentnotifications und Requests behalten dabei ihre
Reihenfolge. Ein zweites Öffnen derselben Quelle unter einer anderen URI wird
mit einer Betriebsmeldung abgelehnt und ersetzt keinen offenen Clienttext.
Verschwindet die Primärdatei, bleibt ein vorhandener Hardlink unter derselben
Core-Identität verwendbar; geschlossene Navigation und Edits benutzen dann
die nachgewiesene vorhandene Dateiadresse.
Das gilt auch, wenn ein Watcher den überlebenden Alias statt der gelöschten
Primäradresse meldet. Gespeicherte Dateischlüssel bleiben für diese Zuordnung
verwendbar; auf Providern ohne Dateischlüssel gilt die belegte Aliasregistrierung.
Ein gültiger Vollersatz oder ein Wiederöffnen hebt Ablehnungs-/Resynczustände
für dieselbe kanonische Quellidentität auf, auch bei gewechselter Client-URI.
Ein weiterhin gültiger anderer offener Client behält dagegen seine Autorität.

Das P3-Hostprofil benutzt eine begrenzte Ownerqueue, zwei Analyseplätze und
höchstens einen neuesten ausstehenden Capture. Explizite Compiles laufen
abgetrennt und besitzen eigene Abbruchtoken/Laufkontexte. Auch Anfragen,
einschliesslich Formatierung und Diagrammprojektion, laufen nach kurzem Capture
einer unveränderlichen Hostsicht auf zwei begrenzten Sprachquery-Workern.
Antworten prüfen beim Rückweg Generation, Revision und Konfiguration erneut.
Datei-I/O und ausstehende Compiles sind begrenzt; Überlast erhält einen
kontrollierten Fehler. Dateimeldungen für offene Dokumente überschreiben
keinen Clienttext. Ausstehende relevante Datei-I/O wird vor einer Sprachabfrage
abgeschlossen; zwischenzeitliche Dokumentänderung ergibt `ContentModified`.
Ein ungültiger Änderungsbatch verändert weder Quelltext noch Core-Revision;
weitere Deltas werden bis zur vollständigen Ersatznachricht oder Wiederöffnung
verweigert und über den Betriebskanal erklärt. Clientversionen erscheinen auch
bei No-op-Text in der vollständigen Diagnostikersetzung.

`workspace/didChangeConfiguration` akzeptiert ein Objekt `interlis` (oder das
Objekt direkt) mit optionalen Feldern `warningPolicy` (`ALLOW_WARNINGS` oder
`REJECT_WARNINGS`), `allowedLanguages` (`2.3`/`2.4`), `analysisBudget`,
`cacheLimits` und `resolutionPolicy`. Budget-/Cachefelder entsprechen den
gleichnamigen öffentlichen Records. Policy enthält `preferWorkspaceSource`,
`modelSelections` als Array mit `modelName`, optional `modelVersion` und
`sourceHint`, sowie `sourcePriorities` als Array mit `sourceId` und ganzzahligem
`priority`. Ausgelassene Felder erhalten den bisherigen Wert, angegebene Arrays
ersetzen die bisherige Auswahl. Validierung erfolgt vor der atomaren
`Workspace.configure`-Mutation; ungültige Typen, unbekannte Felder, doppelte
Auswahlen und unzulässige Werte lassen die gesamte bisherige Konfiguration
stehen und erzeugen eine sichere Betriebsmeldung. Es gibt keine impliziten
HTTP-/Dateizugriffe durch den Resolver.

Die initialen lokalen Workspaceordner werden über einen begrenzten Hostadapter
eingelesen. Symlink-/Realpfade entscheiden über die Editierbarkeit, während
logische Client-URIs die Quellidentität erhalten. `.git`, `.gradle`, `build`
und `node_modules` werden bei der Suche ausgelassen; Grenzen sind 10000 Quellen
und 32 Millionen UTF-16-Einheiten für eine Entdeckung. Ein fehlgeschlagener
Ladevorgang wird nicht als erfolgreiche leere Projektaufnahme dargestellt.

Semantic Tokens benutzen Syntaxspannen, Trivia und gebundene Symbolkategorien;
mehrzeilige Strings/Kommentare werden in einzelne UTF-16-Zeilenspannen geteilt.
Folding ergänzt mehrzeilige Kommentartrivia zu Deklarationsbereichen.
Formatierung verwendet die gemeinsame Format-API mit dem Client-`tabSize`
(1–16 Leerzeichen) und erhaltenen Zeilenenden; Syntaxablehnung und Abbruch
erzeugen einen Fehler statt einer scheinbar erfolgreichen leeren Editliste.
Hover liefert Klartext, damit Dokumentation keinen aktiven Markupinhalt erzeugt.
`initialize` läuft einmal, Vorabrequests erhalten `ServerNotInitialized`;
nach Shutdown werden neue Requests abgelehnt. Der stdio-Prozess beendet sich
bei `exit` selbständig, Status 0 nach Shutdown, sonst 1. Interne Fehler nennen
eine sichere Fehlerkennung und enthalten keine serialisierte Exception.

Eine konfigurierbare kurze Entprellzeit darf auf wiederholte Hintergrunddiagnostik angewandt werden. Die Korrektheit darf nicht von einer bestimmten Millisekundenzahl abhängen. Eine explizite Definition-/Completion-Abfrage berücksichtigt zuvor angenommene Dokumentänderungen und wartet nicht auf eine beliebig lange Hintergrund-Queue.

Der Scheduler verwirft supersedierte ausstehende Hintergrundläufe. Laufende Arbeiten werden nach Möglichkeit abgebrochen. JVM-Parallelität wird begrenzt; virtuelle Threads können für Host-I/O nützlich sein, ersetzen aber kein CPU-Budget für Parser und Semantik. Die portable Workspace-API erhält keine Thread-/Future-Pflicht.

Der P1-LSP verwendet ausdrücklich den abgetrennten Ablauf: auf der Ownerqueue `capture(request, token)` erstellen, danach `CompilerEngine.analyze(capture.request(), capture.reuse(), context, token)` auf dem begrenzten Analyseexecutor ausführen und schliesslich auf der Ownerqueue `acceptAnalysis(capture, analysis)` aufrufen. Nur ein angenommener, zur aktuellen Dokumentgeneration passender Stand geht an DiagnosticPublisher und Diagrammverbraucher. Quellupdates bleiben während der Engineberechnung bearbeitbar. Der Host hält keinen Workspace-Lock über den Engineaufruf und ruft nicht den synchronen Komfortpfad auf der Dokument-Notification-Queue auf. Abbruch- und interne Fehlerwege überspringen den vollständigen Commit; sie dürfen einen jüngeren erfolgreichen Stand nicht verdrängen.

Bei ungültigen Client-Änderungsbereichen darf der Server nicht raten und nachfolgende Deltas auf falschen Text anwenden. Er markiert den Dokumentzustand als unsynchronisiert, meldet den konkreten Fehler über den Betriebskanal und verlangt im eigenen Clientadapter eine vollständige Resynchronisation. Es wird keine nicht standardisierte LSP-Methode ohne passende Clientimplementierung erfunden. Bis zur Resynchronisation werden schreibende Sprachoperationen abgelehnt.

## 7. Durchgängige Edit- und Abnahmesequenzen

### 7.1 Unvollständiges Attribut

Ausgangsfixture: zwei Klassen `Building` und `Parcel` in derselben Datei, eine importierte Domäne und ein gültiges Attribut. Der Test verwendet ein vollständig gültiges Fixture aus der P1-Suite; die folgenden Fragmente beschreiben nur die Änderung.

1. Gültigen Stand analysieren; Definitionen und Diagrammknoten erfassen.
2. Attribut zu `height :` verkürzen, wobei `END Building;` erhalten bleibt.
3. Genau lokalisierte Syntaxdiagnose oder kleiner begründeter Diagnoseverbund; `Parcel` und Modell-/Topicstruktur bleiben verfügbar.
4. `CompiledSchema` für diese Wurzel wird nicht freigegeben.
5. Typ ergänzen, erneut analysieren; alte Diagnose wird entfernt.
6. Neue Analyse mit einer frischen Engine vergleichen; fachliche Daten und Diagnostik stimmen nach ID-Normalisierung überein.

### 7.2 Fehlender Import erscheint später

1. `Survey` mit Import auf nicht verfügbares `Base` analysieren.
2. Negativen Lookup und `ILI-RES-0001` nachweisen.
3. Quelle mit `Base` hinzufügen, ohne `Survey` zu ändern.
4. Import und abhängige Typen neu auflösen; unveränderte Survey-Syntax darf wiederverwendet werden.
5. Zweiten gleichrangigen Kandidaten hinzufügen; eine Mehrdeutigkeit muss erkannt werden.
6. Kandidaten entfernen; der erfolgreiche ursprüngliche Zustand muss wieder entstehen.

### 7.3 Gleichzeitiger neuer Stand und alte Antwort

1. Capture für WorkspaceRevision 21 erstellen.
2. Host nimmt anschliessend Revision 22 an.
3. Analyse von 21 endet nach der Analyse von 22.
4. `acceptAnalysis` für 22 liefert `ACCEPTED`; für 21 anschliessend `STALE_NO_COMMIT`. Der letzte Reuse-Stand und Abhängigkeitsindex bleiben auf 22; Client bleibt auf 22.
5. Ein RenamePlan aus 21 wird abgelehnt, auch wenn der betroffene Name im neuen Dokument zufällig gleich geschrieben ist.
6. Revision 23 ändert nur eine Nachbardeklaration. Der neue Capture bietet den akzeptierten Stand 22 zur Wiederverwendung an; unveränderte eindeutige SymbolIds bleiben erhalten. Ein frischer Lauf bestätigt das fachliche Ergebnis.
7. Ein Snapshot aus einem anderen Capture bei gleicher WorkspaceRevision wird wegen abweichender Wurzeln oder Optionen zurückgewiesen. Ein Capture aus einer anderen Workspaceinstanz wird auch bei identischem Text nicht angenommen.

### 7.4 Unicode und mehrere Deltas

Fixture enthält Umlaut, Emoji, CRLF und eine leere letzte Zeile. Zwei LSP-Änderungen derselben Notification verschieben zunächst Text und ändern danach einen Bereich im verschobenen Zwischenstand. Ergebnistext, Parserdiagnose, Hover und Diagrammanker müssen die exakten UTF-16-Offsets teilen. Der Test wird auf JVM und im Browser ausgeführt.

### 7.5 Speicher und Abbruch

Mit bewusst kleinen Cachelimits werden wiederholt Quellen geändert, Snapshots gehalten und wieder freigegeben. Die Anzahl intern gehaltener Einträge bleibt innerhalb des Limits; ein externer alter Snapshot bleibt lesbar. Abbruch wird vor Parse, nach Deklarationsindex, nach Binding und vor Gate ausgelöst. Anschliessende erfolgreiche Läufe dürfen keine Diagnosen oder Bindungen aus dem abgebrochenen Lauf übernehmen.

## 8. Orientierung an bestehenden Projekten

Die [CompilerSession von ilic-fork](https://github.com/edigonzales/ilic-fork/blob/main/include/ilic/Compiler.h) und dessen [inkrementelle Architektur](https://github.com/edigonzales/ilic-fork/blob/main/docs/architecture/incremental-compilation.md) sind Referenzen für Sitzung und Wiederverwendung. Der untersuchte [CompilationOrchestrator](https://github.com/edigonzales/ilic-fork/blob/main/source/core/CompilationOrchestrator.cpp) trennt vollständige Cachetreffer von semantischem Neuaufbau mit Parse-Reuse; diese Fähigkeit ist nicht mit allgemeinem Teilbaumparsing gleichzusetzen.

Der bestehende Java-LSP zeigt mit [LiveAnalysisService](https://github.com/edigonzales/interlis-lsp/blob/main/src/main/java/ch/so/agi/lsp/interlis/live/LiveAnalysisService.java) und [Ili2cUtil](https://github.com/edigonzales/interlis-lsp/blob/main/src/main/java/ch/so/agi/lsp/interlis/compiler/Ili2cUtil.java), welche Hostaufgaben heute neben dem Compiler liegen. Der neue Adapter übernimmt UI- und Protokollverhalten schrittweise, ersetzt jedoch die doppelte Sprachinterpretation durch gemeinsame Queries. Alte und neue Backends dürfen während des Vergleichs parallel existieren, müssen aber pro Request ausdrücklich ausgewählt werden.

Die segmentgenauen Referenzen in GeoWerkstatts [Reference.cs](https://github.com/geowerkstatt/GeoW.Interlis.Tools/blob/main/src/Geowerkstatt.Interlis.Compiler/AST/Reference.cs) sind eine fachliche Orientierung für Navigation und Rename. Die konkrete Java-Implementierung folgt den unveränderlichen Snapshot- und Identitätsverträgen dieses Projekts.


### L3-2: atomare Repositoryübernahme und Auswahlidentität

`Workspace.configuration()` liefert die aktuelle unveränderliche Konfiguration.
Die zusätzlichen `applyBatch(updates,expectedRevision,configuration)` und
`applyBatch(updates,expectedRevision,configuration,expectedConfigurationFingerprint)`
installieren Quellen und Konfiguration atomar. Im zweiten Fall muss auch die
vollständige bisherige Konfiguration übereinstimmen; so gehen Cacheänderungen
während eines Downloads nicht verloren. Alle Prüfungen erfolgen vor jeder
Mutation, neue Quellrevisionen vergibt ausschliesslich der Workspace. Die
bestehende Zweiparametervariante bleibt erhalten.

Der Capture-Fingerprint (`workspace-capture-v2`) enthält die tatsächliche
Resolveridentität einschliesslich Auswahlpolicy. `acceptAnalysis` prüft zusätzlich
`capture.request().fingerprint()` gegen die echte CompilationFingerprint der
Analyse. Gleiche Texte/Revisionen/Optionen mit anderer Root-/Import-/Originalbindung
sind damit kein zulässiger Ersatz. Cacheänderungen bleiben semantisch neutral;
veraltete Captures werden weiterhin über WorkspaceRevision abgewiesen.
