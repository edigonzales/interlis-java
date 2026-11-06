# Diagnostik und Laufbeobachtung

Strukturierte Diagnosen gehören zum fachlichen Ergebnis. Darstellung, Filter und Beobachtung gehören zum jeweiligen Host. Die stabile Semantik steht im versionierten Diagnosekatalog und in den zugehörigen Tests.

<a id="fachliche-diagnostik-typen"></a>
## Fachliche Diagnostik: Typen

<a id="identitäten-und-positionen"></a>
### Identitäten und Positionen

`DiagnosticCode(String value)` ist die veröffentlichte fachliche Klassifikation, etwa `ILI-SEM-0001`. Der Konstruktor prüft das Katalogformat und verbietet leere Werte. Der Code darf nach Veröffentlichung nicht mit einer anderen Bedeutung wiederverwendet werden.

`DiagnosticId(String value)` identifiziert eine konkrete Diagnose im zugehörigen Analyseergebnis. Gleiche Codes an zwei verschiedenen Referenzen ergeben verschiedene Diagnose-IDs. Die Identität ist kein dauerhaftes fachliches Objekt und kein Ersatz für `SymbolId`. Ein Adapter darf eine ID aus einem älteren Snapshot nicht ohne Prüfung im aktuellen Snapshot auflösen.

Die Erzeugung einer `DiagnosticId` MUSS deterministisch gegenüber der kanonischen Diagnoseeingabe sein; sie darf nicht von `RunId`, Uhrzeit, Thread, Objektadresse oder globalem Zähler abhängen. Die konkrete Codierung bleibt intern. Für Vergleiche unabhängiger Compiler implementiert der Testharness eine explizite Normalisierung nach Code, Quellbezug und Argumenten. Nicht reproduzierbare Fremd-IDs werden nicht als fachliche Unterschiede behandelt.

`RelatedLocation(SourceRange range, String messageKey, List<DiagnosticArgument> arguments)` beschreibt beispielsweise die frühere Deklaration bei einem doppelten Namen. Alle Collections werden defensiv kopiert. Eine globale Zusatzinformation ohne Quellbereich wird als Argument beziehungsweise Hilfetext modelliert; eine künstliche Position `0:0` ist nicht zulässig.

<a id="schweregrad-und-phase"></a>
### Schweregrad und Phase

`Severity` enthält genau `ERROR`, `WARNING`, `INFO` und `HINT`. Fachliche Fehler sind `ERROR` innerhalb der angeforderten Modellabhängigkeiten. Das Compilation-Gate berücksichtigt zusätzlich die Vollständigkeit der Prüfung und die ausdrücklich konfigurierte `WarningPolicy`. `REJECT_WARNINGS` verändert die ursprüngliche Severity einer Warnung nicht; es ergibt einen eigenen Ablehnungsgrund. Aus einem Host-Loglevel darf keine fachliche Severity abgeleitet werden.

`Phase` enthält die folgenden kanonischen Werte:

Ein Phase-Wert ist eine stabile Diagnosekategorie. Er verpflichtet die Implementierung nicht dazu, stets einen separaten vollständigen Baumdurchlauf auszuführen. Optimierte oder zusammengelegte interne Durchläufe müssen die fachliche Zuordnung erhalten.

<a id="typisierte-argumente"></a>
### Typisierte Argumente

`DiagnosticArgument` ist ein `sealed interface` mit `String name()`. Seine anfänglichen Varianten sind:

| Variante | Wert | Zweck |
|---|---|---|
| `TextArgument` | `String value` | Fachlicher Kurztext ohne Template-Interpretation |
| `IdentifierArgument` | `String value` | Ein einzelner dargestellter oder qualifizierter Name |
| `TokenArgument` | `String value` | Tatsächlich angetroffenes Token; keine unbegrenzte Quelltextkopie |
| `IntegerArgument` | `BigInteger value` | Exakte Anzahl oder ganzzahlige Grenze |
| `DecimalArgument` | `BigDecimal value`, optional ursprüngliches Literal | Exakter Dezimalwert mit gegebenenfalls erhaltenem Ursprung |
| `TypeNameArgument` | `String value` | Für Menschen lesbare Typbezeichnung |
| `IdentifierListArgument` | `List<String> values` | Geordnete Kandidaten, erwartete Namen oder Tokens |

Argumentnamen müssen innerhalb einer Diagnose eindeutig sein. Der Katalog legt Namen und Typen verbindlich fest. Renderer dürfen Werte escapen und begrenzt anzeigen, aber nicht verändern. Ein gekürztes Token erhält eine erkennbare Kürzungsmarkierung; seine Begrenzung darf niemals die eigentliche Syntaxprüfung beeinflussen.

JSON verwendet explizite Tags wie `{"name":"name","kind":"IDENTIFIER","value":"Building"}`. Exakte Ganz- und Dezimalzahlen werden als Strings übertragen. Argumente enthalten keine beliebigen Java-Objekte, keine `Throwable`-Instanzen und keine reflektiv zu serialisierenden Metamodellgraphen.

<a id="diagnostic"></a>
### Diagnostic

Die öffentliche unveränderliche Klasse beziehungsweise das Record `Diagnostic` bietet diese Accessoren:

```java
DiagnosticId id();
DiagnosticCode code();
Severity severity();
Phase phase();
String messageKey();
List<DiagnosticArgument> arguments();
Optional<SourceRange> primaryRange();
List<RelatedLocation> relatedLocations();
Optional<DiagnosticId> causeId();
List<DiagnosticId> dependsOn();
Optional<SymbolId> symbolId();
Optional<FailureId> failureId();
List<DiagnosticTag> tags();
```

`causeId` ist die bevorzugte unmittelbare Ursache; `dependsOn` erlaubt weitere bekannte Voraussetzungen. Beide referenzieren ausschliesslich Diagnosen desselben Analyseberichts. Der Ursachengraph muss azyklisch sein. `DiagnosticTag` enthält zunächst `RECOVERY_ARTIFACT`, `DEPRECATED` und `UNNECESSARY`; eine Erholung aus einem Syntaxfehler wird dadurch nicht zur gültigen Syntax.

`failureId` ist ausschliesslich für intern fehlgeschlagene Operationen vorgesehen. Stacktrace, technische Klassen und Dateisystemdetails liegen im technischen Fehlerbericht, nicht im fachlichen Diagnoseobjekt.

<a id="erzeugung-sammlung-und-darstellung"></a>
## Erzeugung, Sammlung und Darstellung

<a id="diagnosticcatalog"></a>
### DiagnosticCatalog

`DiagnosticCatalog` ist unveränderlich und besitzt:

```java
DiagnosticDefinition require(DiagnosticCode code);
List<DiagnosticDefinition> definitions();
void validate(DiagnosticDraft draft);
```

`require` liefert die Definition oder signalisiert einen internen Programmierfehler für einen unbekannten, vom Compiler selbst erzeugten Code. `validate` prüft Namen und Typen der Argumente sowie die erlaubte Phase. Es führt keine I/O durch. Eingehende Protokollwerte werden vorher im Adapter validiert; ein fremder Code ist dort eine ungültige Protokolleingabe und kein Compilerdefekt.

`DiagnosticDefinition` enthält Code, Bedeutung, Standard-Severity, erlaubte Phasen, `messageKey`, Argumentdefinitionen, Positionsregel und eine Katalogversion. Ein Alias darf nur einen ausdrücklich dokumentierten historischen Code auf eine unveränderte Bedeutung abbilden.

<a id="diagnosticcollector"></a>
### DiagnosticCollector

Der phasenübergreifend verwendete Collector ist laufgebunden und nicht öffentlich veränderlicher Bestandteil eines Snapshots. Seine Schnittstelle lautet:

```java
DiagnosticId emit(DiagnosticDraft draft);
Map<DiagnosticId, DiagnosticId> importAll(List<Diagnostic> diagnostics);
boolean hasErrors();
List<Diagnostic> freeze();
```

**Vorbedingungen:** Der Collector ist offen; Ursachendiagnosen wurden bereits registriert; Quellpositionen referenzieren bekannte Quellrevisionen; der Draft entspricht dem Katalog. Intern erzeugte Vorwärtsverweise benötigen eine explizite Reservierung und Abschlussvalidierung, falls sie später tatsächlich gebraucht werden. Die erste Version verwendet ausschliesslich bereits bekannte Ursachen.

**Nachbedingungen:** `emit` liefert die ID einer kanonisch registrierten Diagnose. Exakt gleiche Wiederholungen werden dedupliziert. Verschiedene Quellen, Positionen, Argumente oder Ursachen werden nicht versehentlich zusammengeführt. `freeze` liefert eine defensive, kanonisch sortierte Liste und schliesst den Collector; weitere Emissionen sind ein interner Programmierfehler.

`DiagnosticDraft` enthält dieselben fachlichen Felder wie `Diagnostic`, jedoch noch keine eigene ID. `importAll` übernimmt beispielsweise die `SourceSet.resolutionDiagnostics()` aus der vorgelagerten Auflösung. Es validiert Quellrevisionen und Ursachengraph, erzeugt die Diagnoseidentitäten des aktuellen Berichtes und liefert die Zuordnung alter zu neuer IDs. Ursache und `dependsOn` werden innerhalb dieses Imports konsistent umgeschrieben. Fremde, nicht mitgelieferte Ursachen werden nicht still als aktuelle IDs übernommen. Bereits vorhandene Ursachendiagnosen können über eine explizit zuvor erzeugte Zuordnung referenziert werden. Eine alte Diagnose über einen inzwischen geänderten Quellstand wird nicht durch blosse ID-Umschreibung aktuell.

**Fehler und Begrenzung:** Eine Grenzüberschreitung stoppt die weitere Diagnoseexpansion kontrolliert. Das Ergebnis kennzeichnet die unvollständige Analyse und darf kein `CompiledSchema` ausstellen. Es darf weder stilles Abschneiden mit Erfolg noch eine Speichererschöpfung als normale Kontrollstrategie geben. Gemäss Kompilierungsvertrag liefert `compile` dafür `Cancelled` mit `CancellationReason.RESOURCE_LIMIT` und optional einer konsistent gefrorenen Teilsicht. `analyze` wirft die gemeinsame `OperationCancelledException` mit demselben Grund. `hasErrors` ist kein Ersatz für das abschliessende Gate.

Die kanonische Sortierung verwendet zuerst `SourceId.value()` in der von Java `String.compareTo` definierten lexikografischen UTF-16-Codeeinheitenordnung, dann Quellbereich, fachliche Phase, Code und einen stabilen Argumentvergleich. Sie verwendet weder Locale-Kollation noch Map-Einfügereihenfolge. Globale Diagnosen ohne Quelle stehen vor quellgebundenen Diagnosen und werden nach Phase, Code und Argumenten geordnet. Damit besitzt auch das mapbasierte `SourceSet` aus [Compilervertrag](compiler-and-model.md) eine eindeutige Diagnoseordnung. Eine Legacy-Darstellung darf bei Bedarf eine eigene dokumentierte Reihenfolge verwenden; sie verändert die gespeicherte Liste nicht. Semantisch relevante Deklarations- und Transferreihenfolgen innerhalb der Modelle bleiben davon unberührt.

<a id="diagnosticpresentation"></a>
### DiagnosticPresentation

`AnalysisSnapshot.diagnostics()` enthält die vollständige kanonische Liste. `requestDiagnostics()` ist die gemeinsame, im Compiler einmal ausgewählte Auftragssicht gemäss [Compilervertrag](compiler-and-model.md); das ist keine Anzeigeunterdrückung. IDE-/Workspaceverbraucher behalten die Gesamtsicht. Auf dem jeweiligen fachlichen Eingangsbestand arbeitet:

```java
DiagnosticView project(
    List<Diagnostic> diagnostics,
    DiagnosticDisplayPolicy policy);
```

`DiagnosticView` enthält `visibleDiagnostics()` und `suppressedDiagnostics()`. Jeder `SuppressedDiagnostic` nennt ID, Grund und sichtbare Ursachendiagnose. Mögliche Gründe sind `DEPENDENT_ON_VISIBLE_ERROR`, `DUPLICATE_PRESENTATION` und `USER_FILTER`. Das Original bleibt unverändert.

Die erste Ursachenregel ist konservativ: Eine abhängige Diagnose darf nur verdeckt werden, wenn ihre Aussage nachweislich aus der bereits sichtbaren Ursache folgt. Räumliche Nähe oder gleiche Worte genügen nicht. Eine unaufgelöste Referenz hinter einem fehlenden Import kann dazugehören. Ein eigenständiger ungültiger Zahlenbereich im selben Modell gehört nicht dazu.

Ein Anzeige-Limit muss als Anzeige-Limit erkennbar bleiben, etwa über `hiddenCount`; die fachliche Analysevollständigkeit darf daraus nicht abgeleitet werden. Das Gate liest keine `DiagnosticView`.

<a id="renderer-und-quick-fixes"></a>
### Renderer und Quick-Fixes

Die nullfreien Typen `DiagnosticLanguage { DE, EN }`, `DiagnosticMessage(String text, Optional<String> hint)` und `DiagnosticMessageFormatter` gehören zu `interlis-model/guru.interlis.diagnostics`. `format(Diagnostic, DiagnosticLanguage)` und `formatRelated(RelatedLocation, DiagnosticLanguage)` bilden ausschliesslich strukturierte Werte auf Text ab: kein IO, kein veränderlicher Sprachzustand, keine Norm-/Binderheuristik. Message und Hinweis sind immutable; der Formatter besitzt nur immutable Templates. Unbekannte externe Codes/Keys liefern einen neutralen Fallback, fehlende Argumente ein sichtbares `?`; null ist ein Aufruferfehler. Alle aktuellen Katalogcodes und Argumentplätze müssen durch sinnvolle DE-/EN-Templates abgedeckt sein. Zahlen bleiben exakt; Steuerzeichen, ANSI und Zeilen-/Bidi-Steuerung in Argumenten werden sichtbar escaped. Die normale Prosa enthält keine rohen Keys oder Recorddumps.

Namenskonflikte tragen die tatsächliche kollidierende Deklaration als `RelatedLocation` mit `diagnostic.related.conflictingDeclaration` und Identifierargument `name`. Primärargumente, Regelkennung und Akzeptanz bleiben unverändert. `SCOPE_OUTER_TYPE_NAME` wird als Namenskonflikt mit Modellierungshinweis erklärt, ohne behaupteten Normabschnitt. Hostrenderer berechnen 1-basierte UTF-16-Zeile/Spalte nur aus explizit bereitgestellten passenden Revisionen; sonst nennen sie URI, Revision und Offsets. Verwandte Stellen verändern die Rootauswahl nicht.

`ConsoleDiagnosticRenderer.render(Diagnostic diagnostic, Map<SourceId, SourceText> sources, DiagnosticLanguage language)` gehört zu `interlis-compiler-cli/guru.interlis.cli`, liefert einen String und verändert keinen Diagnosezustand. Der Komfortaufruf ohne Sprache verwendet EN. Alle Eingaben sind nullfrei; Quellen bleiben im Besitz des Aufrufers und werden nur bei passender Revision zur Positionsberechnung verwendet. Der Renderer hat keine Zugriffsmöglichkeiten auf Dateisystem, Repository oder Datenbank.

Das Compiler-CLI verwendet ausschliesslich ConsoleDiagnosticRenderer und JsonDiagnosticWriter mit kanonischen Diagnosedaten; LegacyCliProfile und LegacyDiagnosticRenderer entfallen. Externe Sprachreferenzprozesse bleiben eigenständige Tests.

`JsonDiagnosticWriter.write(List<Diagnostic> diagnostics, DiagnosticLanguage language)` liegt im selben CLI-Adapter und liefert ein JSON-Array ohne IO. Der Komfortaufruf `write(List<Diagnostic>)` verwendet EN. Nullfreie Eingaben, immutable kanonische Werte und ihre Listenreihenfolge bleiben erhalten; Strings werden korrekt escaped, Severity und Codes bleiben maschinenlesbar, exakte Zahlen behalten ihre bestehende Stringkodierung. CLI-Ergebnisobjekt und Streams regelt [CLI-Vertrag](../usage/cli.md). Die fachliche Klassifikation entspricht dem [WASM-Vertrag](bridge-protocol.md); dessen Protokoll wird nicht umbenannt.

`QuickFixProvider.suggest(DiagnosticId id, AnalysisSnapshot snapshot)` liegt ausserhalb der Foundation. Es erzeugt versionierte Vorschläge und keine sofortigen Änderungen. Eine tatsächliche Anwendung prüft alle betroffenen Dokumentrevisionen. Der Diagnosekern darf keine LSP-Klassen oder beliebige ausführbare Closures in ein Diagnoseobjekt aufnehmen.

<a id="laufereignisse-und-zustandsautomat"></a>
## Laufereignisse und Zustandsautomat

<a id="neutrale-schnittstellen"></a>
### Neutrale Schnittstellen

`RunObserver` besitzt genau eine fachlich neutrale Methode:

```java
void onEvent(RunEvent event);
```

Die Schnittstelle deklariert keine `IOException` und keine Netzwerkabhängigkeit. `RunObserver.noop()` liefert einen wirkungslosen Observer. Eine konkrete Senke darf intern asynchron arbeiten, muss ihre Queue aber begrenzen. Der Compiler darf nicht unkontrolliert auf einen entfernten Logdienst warten.

`RunEvent` ist ein unveränderlicher getaggter Ereignistyp. Jedes Ereignis enthält einen `RunEventHeader` mit `RunId`, optionaler `parentRunId`, lauflokaler monotoner `sequence`, Schemanummer und Host-Zeitangaben. Die Zeitquelle ist injizierbar:

```java
long wallTimeEpochMillis();
long monotonicNanos();
```

Wandzeit dient der Korrelation. Dauern werden aus der monotonen Zeitquelle abgeleitet. Der portable Kern darf weder globale Zeitmessungszustände noch eine spezielle JVM-Management-API voraussetzen. Ein Browseradapter kann seine eigene Zeitquelle bereitstellen.

<a id="kontextwerte-erzeugung-und-besitz"></a>
### Kontextwerte, Erzeugung und Besitz

Die öffentliche Kontextübergabe besteht aus kleinen neutralen Werten und einer injizierbaren Factory:

Die Darstellung benennt den Vertrag; Konstruktorprüfung, defensive Kopie und Factoryimplementierung sind zu ergänzen. `RunContext` ist unveränderliche Metadateninformation, kein bereits gestarteter Span und kein veränderlicher Lifecycle. Er enthält weder Observer noch Uhr, Threads, Workspace, Compilerrequest oder Snapshot. `correlationAttributes` ist eine begrenzte nullfreie Map kurzer Strings. Zulässige Schlüssel und Grössenlimits werden vom Hostprofil festgelegt; Credentials und Quelltext gehören nicht hinein. Eltern- und eigene RunId dürfen nicht gleich sein.

`createRoot()` entspricht einer Wurzel mit leeren Korrelationsattributen. `createRoot(attributes)` kopiert die Attribute und erzeugt eine frische RunId. `createChild(parent)` erzeugt eine frische RunId, setzt die Parent-ID und übernimmt die unveränderlichen Korrelationsattribute. Es startet kein Ereignis und verändert den Elternkontext nicht. Geschwister erhalten verschiedene IDs.

Die ergänzende statische Methode `RunContextFactory.defaults()` erzeugt eine neue Defaultfactory. Diese verwendet einen frischen UUID-basierten Factory-Namensraum und einen threadsicheren instanzlokalen monotonen Zähler. Ein Überlauf wird erkannt; IDs werden nicht wiederverwendet. Der Zähler ist kein prozessglobaler Symbol- oder Quellenzähler und beeinflusst keine fachlichen Ergebnisse. Laufidentität und Abschlussbedingungen werden durch `LifecycleBoundaryContractTest` und `CliRunLifecycleContractTest` geprüft. Die portablen Hostverträge verwenden dieselbe Kontextfabrik. Ein Host oder Test kann eine eigene Factory mit festgelegtem eindeutigem Namensraum injizieren; bei einem Zielruntimeproblem wird dieser Adapterweg benutzt und nicht auf globale Variablen oder Uhrzeit als vermeintlich eindeutige ID ausgewichen. Kryptografische Geheimhaltung ist kein Zweck einer RunId.

`CompilerServices` enthält gemäss [Compilervertrag](compiler-and-model.md) `RunObserver observer`, `ClockSource clock` und `RunContextFactory runContexts`. Die kurzen Engine-Methoden erzeugen mit dieser Factory einen neuen Wurzelkontext und delegieren an genau einen der vollen Aufrufe:

```java
AnalysisSnapshot analyze(CompilationRequest request, AnalysisReuse reuse,
                         RunContext context, CancellationToken cancellation);
CompilationOutcome compile(CompilationRequest request, AnalysisReuse reuse,
                           RunContext context, CancellationToken cancellation);
```

**Der volle Engine-Aufruf ist der einzige Besitzer seines Compiler-Lifecycles.** Er übernimmt einen frischen Kontext, beginnt genau einen Lauf und beendet ihn genau einmal. Der übergebene Kontext darf nicht bereits als anderer Lauf gestartet oder für einen früheren Engine-Aufruf verbraucht worden sein. Diese Einmaligkeit ist ein Aufrufervertrag; dafür wird keine unbeschränkt wachsende globale Historie sämtlicher RunIds geführt. Convenience-Methoden emittieren keine zusätzliche Start-/Abschlussfolge vor oder nach ihrem delegierten vollen Aufruf.

Workspace-Overloads mit explizitem Kontext reichen diesen nach Capture/Auflösung an den vollen Engine-Aufruf weiter. Sie starten unter derselben RunId keinen zweiten Compilerlauf. Unterphasen erhalten innerhalb der Engine denselben laufgebundenen Lifecycle und Sequenzgeber. Sie erzeugen keine eigene Engine-Root.

Ein CLI-/Browser-/Diensthost kann einen umfassenderen Elternlauf für Konfigurationsauflösung, Beschaffung, Engine und Exporte öffnen. In diesem Fall erzeugt er für den Engine-Aufruf `createChild(parentContext)`. Der Host besitzt und beendet seinen Elternlauf, die Engine besitzt und beendet den Kindlauf. Der Host darf seinen bereits gestarteten Elternkontext nicht als neuen Enginekontext weiterreichen. Dadurch kann die Kompilierung erfolgreich sein und ihr Kindlauf korrekt enden, während der umspannende CLI-Lauf später wegen eines Export-/Flushfehlers scheitert.

Es gibt weder implizite ThreadLocal-Kontexte noch einen globalen „aktuellen Lauf“. Jede Korrelation zwischen unabhängigen Schichten ist im Wert oder in der Übergabe sichtbar. Sequenznummern sind jeweils lokal zum tatsächlichen Lifecycle; Parent- und Child-Läufe verwenden unterschiedliche RunIds.

<a id="öffentliche-operationen-und-abbruch"></a>
### Öffentliche Operationen und Abbruch

`CancellationReason` unterscheidet `USER_REQUESTED`, `SUPERSEDED`, `DEADLINE_EXCEEDED`, `RESOURCE_LIMIT` und `HOST_SHUTDOWN`. `OperationCancelledException.reason()` liefert diesen typisierten Grund; sie enthält keinen compilerabhängigen Snapshot. `CancellationToken` besitzt die gemeinsamen Methoden `isCancellationRequested()` und `throwIfCancellationRequested()`. Der Host kann einen zu seinem Ausführungsmodell passenden Token bereitstellen. Eine interne Budgetprüfung kann denselben kontrollierten Abbruch mit `RESOURCE_LIMIT` auslösen. Ein Abbruchgrund und ein Diagnosecode sind unterschiedliche Klassifikationen.

Vor der Annahme eines Laufs werden offensichtliche API-Vertragsfehler geprüft, etwa ein `null`-Request. Solche Programmierfehler erzeugen keinen vorgetäuschten fachlichen Compilerlauf. Nach `run.started` MUSS jeder kontrolliert verlassene Ausführungspfad einen Abschluss haben.

Der kleine neutrale `RunLifecycle` besitzt:

```java
static RunLifecycle create(RunContext context,
                           RunObserver observer, ClockSource clock);
void start(RunMetadata metadata);
PhaseHandle beginPhase(Phase phase, PhaseMetadata metadata);
void finish(RunCompletion completion);
```

Der Zustand läuft von `NEW` über `RUNNING` zu genau einem Terminalzustand. Die Factory erzeugt nur den lokalen Zustand und führt noch keine Emission aus. Die volle Engine-Orchestrierung besitzt diesen Zustand; für einen umspannenden Elternlauf kann der Host dieselbe neutrale Implementierung mit eigener RunId verwenden. Der Lifecycle ist laufgebunden, nicht Bestandteil eines Snapshots und nicht unter mehreren unabhängigen Operationen zu teilen. `finish` wird zentral vom jeweiligen Eigentümer aufgerufen. Ein zweiter Aufruf ist ein interner Fehler, wird nicht als zweites Abschlussereignis ausgegeben und wird in Tests erkannt. Ein `PhaseHandle` meldet genau einen Phasenabschluss; ein Abbruch beendet auch offene Phasen mit entsprechendem Status. Der Lifecycle verwendet ausschliesslich neutrale Metadaten und Ereignisse und hängt nicht von `CompilationOutcome` oder anderen Compilerklassen ab; die Orchestrierung bildet diese auf `RunCompletion` ab.

`RunCompletion` ist ein abgeschlossener Typ mit `Succeeded`, `Rejected`, `Cancelled` und `Failed`. Er enthält geeignete Zähler und optional Snapshot- beziehungsweise Failure-Bezüge, aber keinen gesamten Metamodellgraphen. `Rejected` bedeutet, dass kein vollständig geprüftes Erfolgsschema ausgestellt wurde. Die `RejectionSummary` aus [Compilervertrag](compiler-and-model.md) enthält die vom Gate festgestellten Gründe, betroffene Diagnose-IDs der angeforderten Closure, angeforderte Roots und nicht unterstützte Features. Gründe sind `SOURCE_ERRORS`, `WARNINGS_REJECTED`, `MISSING_INPUT`, `AMBIGUOUS_INPUT`, `ENVIRONMENT_FAILURE` und `INCOMPLETE_SUPPORT`. Mehrere Gründe können zugleich gelten. Die Variante allein behauptet keine sprachliche Ungültigkeit; Logging und Adapter übernehmen diese Zusammenfassung statt das Gate nochmals anhand der gesamten Workspace-Diagnoseliste zu implementieren. `Cancelled` und `Failed` bleiben davon getrennt. `analyze` kann einen vollständig ausgeführten Analyselauf mit fachlichen Diagnosen abschliessen; die Ereigniszusammenfassung MUSS deshalb zusätzlich `errorCount`, `analysisComplete`, Coveragegründe und `compiledSchemaProduced` ausweisen. Ein Host darf aus dem Ausführungsstatus allein keine Sprachgültigkeit ableiten.

Die Übernahme in `RunCompletion` ist eine neutrale Datenprojektion: katalogisierte Reasonnamen, Diagnose-IDs, Feature-IDs und Anzahlen. `RunCompletion` importiert weder `RejectionSummary` noch `ModelSelector` oder andere Compilerklassen in die Foundation. Die Originalzusammenfassung bleibt unverändert im fachlichen Ergebnis. Ein späterer Verbraucher kann eigene katalogisierte Ablehnungsgründe projizieren; daraus entsteht keine zusätzliche Interpretation seiner Fachregeln im Logger.

<a id="anfangsereignisse"></a>
### Anfangsereignisse

| Ereignisname | Wesentliche Felder | Häufigkeit / Zweck |
|---|---|---|
| `interlis.run.started` | Operation, Sprachprofile, Rootanzahl, Optionsfingerprint | Genau einmal nach Annahme |
| `interlis.phase.started` | Phase, bearbeitete Einheit oder aggregierte Anzahl | Grobe Phasenstruktur |
| `interlis.phase.finished` | Phase, Status, Dauer, Anzahlen | Genau einmal pro gestarteter Phase |
| `interlis.cache.lookup` | Cacheart, HIT/MISS, begrenzter Reason-Code | Aggregierbar; standardmässig kein Eintrag je AST-Knoten |
| `interlis.workspace.invalidated` | Revision, Grund, Anzahl betroffener Einheiten | Erklärt Neuberechnung |
| `interlis.source.resolved` | bereinigte SourceId, Strategie, Cachebezug, Dauer | Host-Resolver, keine Credentials |
| `interlis.run.cancel_requested` | Grund und anfordernder Adapter | Optional; keine Behauptung bereits erfolgten Abbruchs |
| `interlis.run.internal_failure` | FailureId, begrenzte technische Ursachen, Stackinformationen soweit verfügbar | Interner Fehlerbericht, getrennt von sicherer fachlicher Diagnose |
| `interlis.run.finished` | Terminalstatus, Dauer, Zähler, SnapshotId/FailureId | Ein Abschluss-Emissionsversuch je angenommenem Lauf |
| `interlis.observer.degraded` | Senkentyp, Ausfallklasse, verworfene Anzahl | Nur Host-Fallback; nicht an dieselbe kaputte Senke rekursiv senden |

Eine Diagnose muss nicht zusätzlich als Betriebsereignis dupliziert werden. Wenn ein Host beides benötigt, kennzeichnet er die Diagnoseausgabe eindeutig, damit Zähler nicht doppelt summiert werden.

`FailureDetails` enthält `FailureId`, technische Exceptionklasse, bereinigte Nachricht, begrenzte Ursachenliste und Stackframes soweit die konkrete Laufzeit sie bereitstellt. Die Erfassung besitzt Grenzen für Tiefe, Frameanzahl und Textlänge. Fehlende Stackinformationen werden als fehlend gekennzeichnet; sie werden nicht ausgedacht. Ein generischer `Throwable`-Graph wird nicht ungeprüft per Reflection serialisiert. Der Host darf diese technischen Details an seine vertrauliche Betriebssenke weitergeben. CLI-/LSP-/WASM-Fachantworten erhalten die sichere Diagnose mit demselben Failure-Bezug, sofern kein ausdrücklich gewählter lokaler Debugmodus zusätzliche Details erlaubt.

<a id="observer-ausfall"></a>
### Observer-Ausfall

`SafeRunObserver` umschliesst die konfigurierte optionale Senke. Er fängt gewöhnliche Exceptions der Senke ab, markiert sie als beeinträchtigt und verwendet einen separat injizierten, begrenzten `ObservationFailureHandler`. Er darf nicht pauschal sämtliche `Throwable` abfangen, um einen kritischen VM-Zustand scheinbar zu heilen.

Der Failure-Handler hat keine Rückwirkung auf das fachliche Ergebnis. Der Host kann einen einmaligen Fallback auf stderr verwenden, sofern der aktive Ausgabe-/Protokollvertrag dies erlaubt. Ist auch der Fallback nicht verfügbar, wird der Zustand lokal begrenzt gezählt. Kein rekursiver Aufruf der fehlerhaften Senke.

**Die Garantie ist eine kontrollierte lokale Emission, keine garantierte Zustellung bei Prozessabsturz, Stromausfall oder Netzverlust.** Ein Exporter darf anhand `RunId + sequence` deduplizieren. Das Dokument schreibt kein Transaktionsprotokoll für Logs vor.

<a id="cli-lsp-dienst-und-browser"></a>
## CLI, LSP, Dienst und Browser

<a id="cli-kompatibilität"></a>
### CLI-Kompatibilität

Der Compiler verwendet den expliziten Standardbedienvertrag aus [CLI und SDK](../usage/cli.md). Die frühere ili2c-Bedienparität entfällt; der externe Referenzcompiler bleibt auf Testprozesse beschränkt.

`CliOutcomeMapper.classify(CompilationOutcome outcome)` liefert die passende `CliTermination` anhand der typisierten Ergebnisvariante und im Ablehnungsfall ihrer `RejectionSummary`. `ExitCodePolicy.exitCode(CliTermination termination)` bildet diese auf den festgelegten Standardwert ab. Die Anwendung verwendet den Renderer und den geplanten Ausgabekanal. Diese Schritte dürfen das semantische Ergebnis nicht verändern. Insbesondere ergibt `Rejected` wegen `INCOMPLETE_SUPPORT` keine Meldung „Quelltext ungültig“. `WARNINGS_REJECTED` bleibt eine Policyentscheidung bei unverändertem Warnungsschweregrad. Eine erneute Ableitung der Gateentscheidung aus sämtlichen, möglicherweise rootfremden Diagnosen ist unzulässig. Ein nachfolgender Export-/Flushfehler kann den gesamten CLI-Aufruf betrieblich scheitern lassen, obwohl die Kompilierung erfolgreich war; erst der vollständige Ablauf erzeugt das abschliessende `CliExecutionResult`.

Eine neue strukturierte Ausgabe wird als expliziter Modus angeboten. Ihre Option und die Behandlung gemischter Formate sind im CLI-Vertrag festzulegen. Ein JSON-Lines-Datenstrom enthält pro Zeile genau ein vollständiges Objekt; normale Textmeldungen dürfen nicht hineingemischt werden. Ungültige CLI-Optionen sind Bedienfehler des Adapters.

<a id="lsp"></a>
### LSP

Der LSP projiziert Code, Severity, Message, Bereich, Related Information und gegebenenfalls die Diagnose-ID in die Protokollstruktur. Er veröffentlicht nur Ergebnisse, deren Dokumentversionen noch passen. Veraltete Ergebnisse dürfen optional angezeigt werden, wenn die Oberfläche sie ausdrücklich als veraltet markiert; sie werden nicht als aktuelle Diagnosepublikation ausgegeben.

Betriebslogs gehen über erlaubte Protokollmechanismen oder getrennte Senken. Im stdio-Modus dürfen auch Bibliotheken keine Konsolenausgaben auf stdout erzeugen. Dieser Fall benötigt einen Integrationstest, nicht nur eine Codekonvention.

<a id="jvm-dienste-und-kubernetesopenshift"></a>
### JVM-Dienste und Kubernetes/OpenShift

Ein Container-Host kann strukturierte JSON-Ereignisse an stdout/stderr ausgeben und die Einsammlung seiner Betriebsplattform überlassen. Alternativ kann ein OpenTelemetry-Adapter verwendet werden. Beides wird nicht gleichzeitig zwingend für dieselben Ereignisse aktiviert.

OpenTelemetry bietet unter anderem Zeitstempel, Severity, Body, Resource- und Ereignisattribute sowie Trace-/Span-Bezüge. Die Zuordnung orientiert sich an dessen offiziellem [Log-Datenmodell](https://opentelemetry.io/docs/specs/otel/logs/data-model/). Es ist ein Host-Integrationsvertrag; eine OpenTelemetry-SDK-Abhängigkeit im Compilerkern ist ausgeschlossen.

Geeignete Metriken sind Dauer und Anzahl von Kompilierungen, Phasendauern, Cache-Treffer, analysierte Quellen, Abbrüche und interne Fehler. `SourceId`, Modellname, vollständiger Diagnosewert und `RunId` sind keine Standard-Metriklabels. Andernfalls wächst die Zahl der Zeitreihen mit dem Datenbestand.

Die kleine Bibliothek implementiert weder einen Kubernetes-Client noch einen eigenen Healthserver. Ein Dienstadapter kann Betriebszustand, Queue und Ressourcenverbrauch separat bereitstellen. Fachlich abgelehnte Modelle bedeuten nicht automatisch, dass der Pod ungesund ist.

<a id="wasm"></a>
### WASM

Der Browseradapter liefert Diagnosen als DTOs. Optionale Betriebsereignisse können über einen Callback oder gesammelt im Ergebnis übertragen werden. Dieser Kanal muss eine definierte Begrenzung haben. Der Compilerkern darf kein `console.log` als vertragliche Diagnoseausgabe verwenden.

Revisionen und andere 64-Bit-Werte werden entsprechend dem [WASM-Protokoll](bridge-protocol.md) verlustfrei codiert. Betriebsereignisse und fachliche Ergebnisse erhalten jeweils ihre eigene Schemanummer. Ein neuer optionaler Betriebswert darf die fachliche Kompatibilität nicht brechen.

<a id="konkrete-szenarien-und-vertragstests"></a>
## Konkrete Szenarien und Vertragstests

<a id="fehlendes-importiertes-modell"></a>
### Fehlendes importiertes Modell

Ein Modell importiert `BaseModel`, dessen Quelle fehlt. Der Resolver liefert `ILI-RES-0001`. Die Bindung erfasst abhängige unaufgelöste Referenzen mit `causeId` auf die Importdiagnose. Die Editoransicht zeigt die Importursache und unabhängige Fehler. Die vollständigen Daten bleiben abfragbar; `compile` erzeugt kein Schema.

Wird `BaseModel` später ergänzt, invalidiert der Workspace auch die vormals negative Auflösung. Die neue Diagnosemenge darf keine IDs des alten Berichts als aktuelle Ursachen enthalten. Frische Kompilierung und inkrementeller Stand werden kanonisch verglichen.

<a id="interner-fehler-und-fehlerhafte-logsenke"></a>
### Interner Fehler und fehlerhafte Logsenke

Ein Test injiziert einen internen Fehler in eine Semantikphase und gleichzeitig einen werfenden Observer. Der Aufrufer erhält `CompilationOutcome.Failed` mit `FailureId` und einer passenden Fehlerdiagnose. Der Observerfehler ersetzt diese Ursache nicht. Der Lifecycle versucht genau einen Abschluss zu emittieren. Ein technischer Hostbericht enthält den ursprünglichen Stacktrace und denselben Failure-Bezug; sensible Testwerte werden nicht ausgegeben.

<a id="abbruch-nach-einer-neuen-editorrevision"></a>
### Abbruch nach einer neuen Editorrevision

Revision 20 wird analysiert, während Revision 21 eingeht. Der Host kann für 20 Abbruch anfordern. Falls 20 trotzdem noch kontrolliert fertig wird, wird das Ergebnis wegen der veralteten Publikationsrevision verworfen. Es erhält seinen tatsächlich erreichten Laufabschluss; ein abgeschlossener Erfolg wird nicht nachträglich in `Cancelled` umgeschrieben. Die aktuelle Oberfläche erhält ausschliesslich den passenden Stand von 21.

<a id="pflichtprüfungen-auf-klassenebene"></a>
### Pflichtprüfungen auf Klassenebene

| Test | Vertrag |
|---|---|
| `DiagnosticCatalogContractTest` | Codes eindeutig, Argumentnamen eindeutig, Katalogeinträge vollständig, unbekannte interne Codes erkannt |
| `DiagnosticIdentityTest` | Wiederholung deterministisch; unterschiedliche Referenzstellen verschieden; RunId und Uhrzeit ohne Einfluss |
| `DiagnosticCauseGraphTest` | Keine Zyklen, keine fremden Snapshot-IDs, unabhängige Fehler bleiben sichtbar |
| `DiagnosticRangeTest` | Nullbreite Fehlstelle, CRLF und UTF-16 einschliesslich Surrogatpaaren korrekt |
| `DiagnosticJsonContractTest` | Exakte Zahlen, Escaping, optionale Bereiche und Related Locations verlustfrei |
| `RunLifecycleTest` | Alle Terminalpfade, bereits angeforderter Abbruch, Phasenabbruch, doppeltes Finish |
| `RunContextFactoryTest` | Frische Root-/Child-IDs, richtige Parent-ID, defensive Attributkopie, deterministisch injizierbare Factory, kein Zählerüberlauf |
| `RunContextPropagationTest` | Convenience und voller Aufruf emittieren genau einen Engine-Lauf; Workspace verdoppelt ihn nicht; zwei parallele Eltern mit getrennten Enginekindern vermischen keine Korrelation |
| `ObserverFailureIsolationTest` | Senkenausfall überdeckt weder gültiges Ergebnis noch ursprünglichen internen Fehler |
| `LspStdoutPurityTest` | Keine Nicht-Protokollausgabe auf stdout unter realer Compilerintegration |
| `CompilerCliIdentityContractTest` / `compilerCliCheck` | Standardentry, Reihenfolge, Kanäle und Exitcodes |
| `RejectionPresentationTest` | Warnungspolitik, unvollständige Implementierung, Umgebungsfehler und Quellfehler bleiben typisiert; rootfremde Fehler verändern die Gatezusammenfassung nicht |
| `ObservationBudgetTest` | Begrenzte Queue, dokumentiertes Verwerfen, keine unbeschränkten Strings oder Metriklabels |

<a id="erfolgreiche-hostoperation-ohne-erfolgsschema"></a>
### erfolgreiche Hostoperation ohne Erfolgsschema

`RunCompletion.Succeeded` besitzt additiv das boolesche Feld
`compiledSchemaProduced`. Die vierparametrige Konstruktion benennt ausdrücklich,
ob tatsächlich ein geprüftes Schema produziert wurde; true verlangt
`analysisComplete=true`. Der bisherige Dreiparameterkonstruktor bleibt für
bereits vorhandene erfolgreiche Compiler-Gate-Läufe erhalten. Hilfe, Version
und Formatierung verwenden false und erfinden keinen Compilerkindlauf oder
Schemaerfolg. CLI-Eltern-/Kindbezug und endgültiger Flushabschluss werden im
Hostvertrag aus [CLI-Vertrag](../usage/cli.md) geprüft. Dies ist Betriebsmetadatenkorrektheit und
keine zweite fachliche Gateentscheidung.

<a id="repositorydiagnosen-im-gemeinsamen-diagnoseweg"></a>
## Repositorydiagnosen im gemeinsamen Diagnoseweg

DIAG-001–009 / LOG-002/003/005/008: Katalog `interlis-diagnostics/v2` ergänzt
`ILI-RES-0008` (Warnung bei Repositoryanpassungen) und `ILI-RES-0009`
(Ablehnung wegen Versions-/Identitäts-/Pin-/Lockkonflikten). Codes 0001–0003
behalten ihre Argumentbedeutung und erlauben zusätzlich ein optionales
`TextArgument(detail)` für sichere ausführliche Suchinformationen.
0008 besitzt `model:IDENTIFIER` sowie `repository`, `reason`, `detail:TEXT`;
0009 besitzt `model:IDENTIFIER` sowie `reason`, `detail:TEXT`.
Die Werte sind Daten; DE-/EN-Texte und Hinweise erzeugt allein der gemeinsame
Formatter. Fehlende Modelle, Mehrdeutigkeit und Ladefehler verwenden weiterhin
0001, 0002 und 0003. Modellversionen und Kandidatenquellen bleiben im
Repositoryergebnis strukturiert zugänglich.

Repositoryablehnungen sind strukturierte Hostresultate, keine behaupteten
Compiler-Gate-Resultate. Kein Compilerstart und kein Erfolgsschema bei
unvollständiger Beschaffung. Anpassungswarnungen einer vollständigen Beschaffung
werden dagegen als globale SourceSet-Diagnosen vom Compiler-Gate bewertet.
Alle angenommenen CLI-Läufe erhalten weiterhin genau einen Hostabschluss.

Qualifizierte Namensfehler, deren Modellqualifikator einem tatsächlich
fehlgeschlagenen Import im selben Modell entspricht, erhalten dessen
`causeId` und `dependsOn`. Textdarstellung unterdrückt diese abhängigen
Fehler über `DiagnosticPresentation`; die vollständige Liste und JSON behalten
sie. Unabhängige Namen werden nicht aufgrund eines fehlenden Imports verdeckt.

Repositorycache (RES-013): Der explizite Hostcallback aus [Repositoryvertrag](repositories.md) meldet nur
getypte Statuswerte, niemals URLs, Header, Pfade oder Antwortinhalte.
`RunLifecycle.cacheLookup(cache,hit,reason)` verwendet im laufenden CLI-Elternlauf
die vorhandene CacheLookup-Variante mit dessen Kontext und Sequenz. DOWNLOAD,
NOT_MODIFIED, OFFLINE_HIT/MISS, CACHE_MISS und WRITE_UNAVAILABLE bleiben erklärbar.
Ein Schreibfehler rendert einmal pro Hostzugriff eine operative ILI-RES-0008 mit
reason CACHE_WRITE_UNAVAILABLE auf stderr; sie ersetzt keine Quelle und
verändert keine Compiler-Gateentscheidung. Die kanonischen Suchwarnungen bleiben
weiterhin am Gate wirksam. Observerfehler ändern kein Ladeergebnis.

<a id="cacheinformationen-im-gemeinsamen-meldungsstil"></a>
## Cacheinformationen im gemeinsamen Meldungsstil

Cacheinformationen beschreiben die tatsächliche Beschaffung. `ILI-RES-0014`
bezeichnet einen lokalen Online-Treffer innerhalb der Frist. Die Darstellung
enthält den sicheren angefragten Ursprung und den Cachezustand in DE oder EN.
`ILI-RES-0013` bleibt reserviert. Die [Repositoryregeln](repositories.md#cache-nach-ili2c)
definieren Fristen, Inhaltsprüfung und Offlinezugriffe.

Bei ausführlichen CLI-Läufen kommen Modelllookup, Auswahl, Download, Treffer
und Cachefehler einmal in der Reihenfolge des tatsächlichen Zugriffs an.
Provenienz aus einem gelesenen Lock ist keine neue Suchmeldung. Der
Diagnosekatalog v4 bleibt die Quelle für Codes und Argumente.
