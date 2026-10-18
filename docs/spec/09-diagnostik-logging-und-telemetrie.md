# 09 – Diagnostik, strukturiertes Logging und Telemetrie

## 1. Zweck, Geltung und Leitentscheidung

Dieses Dokument spezifiziert die fachliche Diagnostik und die Beobachtbarkeit des gesamten geplanten INTERLIS-Stacks. Die erste Umsetzung betrifft Compiler, Workspace, CLI und WASM-Adapter. Datentransfer, Validierung und Datenbankoperationen verwenden später dieselben Grundverträge mit eigenen Ereignissen und Diagnosefamilien. Die im Folgenden beschriebenen Klassen und Methoden sind normative Entwurfsverträge, keine Behauptung einer bereits vorhandenen Implementierung.

**Fachliche Diagnosen sind strukturierte Ergebnisse einer Analyse. Betriebsereignisse beschreiben deren Ausführung.** Eine Diagnose darf nicht erst durch das Parsen einer Logzeile entstehen. Umgekehrt dürfen Zeitmessungen, Cache-Ereignisse und Ausnahmen nicht als gewöhnliche INTERLIS-Sprachfehler in das Metamodell gelangen.

Die gemeinsame Foundation liegt in `interlis-model`, insbesondere in `guru.interlis.diagnostics` und `guru.interlis.execution`. Sie besitzt keine Abhängigkeit zu SLF4J, Logback, Log4j, OpenTelemetry, LSP4J, HTTP, JDBC oder ANTLR. `guru.interlis.execution` enthält `CancellationToken`, `CancellationReason`, `OperationCancelledException`, `RunObserver`, `RunEvent`, `RunId`, `RunContext`, `RunContextFactory`, `FailureId`, die neutrale Zeitquelle `ClockSource` und den kleinen neutralen `RunLifecycle`; dafür entsteht kein zusätzliches Modul. Implementierungen von Konsolen-, JSON-, Datei- und Telemetrieausgaben liegen in Host-Adaptern. Das Modell soll nicht zu einem allgemeinen Logging-Framework ausgebaut werden.

Verwandte Verträge: [Kompilierung](06-kompilierung-und-semantik.md), [Workspace und LSP](07-workspace-inkrementalitaet-und-lsp.md), [WASM](10-webassembly-und-portabilitaet.md), [Tests und Kompatibilität](13-tests-kompatibilitaet-und-qualitaet.md), [CLI und SDK](18-cli-sdk-und-kompatibilitaetsadapter.md).

| Klassengruppe | Modul / Paketgrenze |
|---|---|
| Unveränderliche Diagnosewerte, Katalogdefinitionen, reine Anzeigeprojektion | `interlis-model`, `guru.interlis.diagnostics` |
| Neutrale Ausführungswerte und Schnittstellen | `interlis-model`, `guru.interlis.execution` |
| Laufgebundener Collector und Compilerfehlererfassung | `interlis-compiler`, interne Pakete; keine öffentlichen Builder in Snapshots |
| Neutraler Lifecycle-Zustandsautomat und Kontextfactory | `interlis-model`, `guru.interlis.execution`; ausschliesslich lokale Ereignisstrukturierung, keine Ausgabesenke |
| Legacy-Renderer, CLI-Statuszuordnung und konkrete Ausgabe | `interlis-cli`, `guru.interlis.cli` und werkzeugspezifische Adapter |
| LSP-Diagnoseabbildung und Quick-Fix-Protokollanbindung | `interlis-lsp`; reine fachliche Vorschläge dürfen compilerseitige Querydienste nutzen |
| JSON-/WASM-Protokollserialisierung | Zieladapter; nur bei tatsächlicher gemeinsamer Implementierung eigenes kleines gemeinsames Paket |
| OpenTelemetry-/Datei-/Dienstintegration | Optionaler Host-Adapter; keine transitive SDK-Abhängigkeit des Kerns |

## 2. Anforderungen und Abnahme

| ID | Normative Anforderung | Phase | Abnahme |
|---|---|---|---|
| DIAG-001 | Der Compiler MUSS Lexer-, Parser-, Bindungs-, Typ- und Semantikfehler als unveränderliche `Diagnostic`-Werte liefern. | P1 | Ein Syntax- und ein Semantikfehler sind ohne Logauswertung über die Java-API und WASM auslesbar. |
| DIAG-002 | Jede Diagnose MUSS einen stabil dokumentierten Code und typisierte Argumente besitzen. Ein gerenderter Text DARF NICHT ihre Identität sein. | P1 | Textänderung beeinflusst Code, Ursachenbezug und Tests auf Argumente nicht. |
| DIAG-003 | Quellbezogene Diagnosen MÜSSEN `SourceId`, `SourceRevision` und halb offene UTF-16-Bereiche verwenden. Globale Diagnosen MÜSSEN ohne erfundene Quellposition darstellbar sein. | P1 | Tests mit CRLF, Umlauten, Emoji und globalem Auflösungsfehler. |
| DIAG-004 | Die vollständige Diagnoseliste MUSS vom gefilterten Anzeigeergebnis getrennt bleiben. Das Compilation-Gate MUSS relevante Fehler, Warnungspolitik und Analyseabdeckung prüfen und eine typisierte Ablehnungszusammenfassung erzeugen. | P1 | Unterdrückte Folgefehler erzeugen keinen Erfolg; Warnungsablehnung wird nicht als Sprachfehler dargestellt. |
| DIAG-005 | Ursachen und abhängige Diagnosen MÜSSEN explizit referenziert werden. Unterdrückung allein anhand von Meldungstexten ist unzulässig. | P1 | Fehlender Import verdeckt passende Folgefehler, ein unabhängiger Literalfehler bleibt sichtbar. |
| DIAG-006 | Diagnosecodes, Argumentnamen und deren Bedeutung MÜSSEN versioniert und auf unbeabsichtigte inkompatible Änderungen geprüft werden. | P2 | Katalogvertrag und JSON-Vertragstests. |
| DIAG-007 | Sprachlich ungültige Benutzereingaben DÜRFEN KEINE ungefangenen internen Exceptions sein. Interne Defekte MÜSSEN als solche erkennbar bleiben. | P1 | Ungültige Modelle ergeben `Rejected`; injizierter interner Defekt ergibt `Failed` mit `FailureId`. |
| DIAG-008 | Gleichwertige Läufe mit denselben semantischen Eingaben MÜSSEN dieselbe kanonische Diagnosereihenfolge liefern. Laufzeit- und Run-IDs dürfen diese Reihenfolge nicht beeinflussen. | P1 | Wiederholung und Laufzeitvergleich mit definierter ID-Normalisierung. |
| DIAG-009 | Diagnoseerzeugung MUSS begrenzt sein. Ein überschrittenes Ressourcenlimit DARF NICHT als erfolgreiche oder vollständig geprüfte Analyse erscheinen. | P1 | Adversarische Eingabe überschreitet konfiguriertes Limit kontrolliert und zeitnah. |
| DIAG-010 | Quick-Fixes MÜSSEN versionierte Quellstände adressieren und separat vom Diagnosekern erzeugt werden. | P3 | Änderung an einem inzwischen modifizierten Dokument wird abgewiesen oder neu berechnet. |
| LOG-001 | Der portable Kern MUSS ohne konkrete Logging- und Telemetrieimplementierung funktionieren. | P0 | Abhängigkeitsprüfung und JVM-/WASM-Build. |
| LOG-002 | Jeder angenommene öffentliche Lauf MUSS genau einen Abschlusszustand besitzen: erfolgreich, abgelehnt, abgebrochen oder intern fehlgeschlagen. Der Ablehnungsgrund MUSS Quelle, fehlende Voraussetzung und unvollständige Implementierung unterscheiden. | P1 | Ereignisautomat einschliesslich Exception-, Coverage- und Abbruchpfaden. |
| LOG-003 | Eine intern kontrollierte Ausführung MUSS genau einen Abschluss-Emissionsversuch durchführen. Eine Exactly-once-Zustellung über Prozessgrenzen darf daraus nicht behauptet werden. | P1 | Testobserver zählt Abschlussversuche, einschliesslich Observerfehlern. |
| LOG-004 | Ein Fehler einer optionalen Beobachtungssenke DARF das fachliche Ergebnis nicht überschreiben und DARF keine rekursive Logging-Schleife auslösen. | P1 | Werfender Observer verändert das Compile-Ergebnis nicht. |
| LOG-005 | Legacy-CLI-Ausgabekanäle, Meldungstexte, Reihenfolge und Exitcodes MÜSSEN anhand benannter Referenzprofile aufgenommen werden. Unbekannte Werte dürfen nicht erfunden werden. | P3 | Gepinnte CLI-Fixtures mit getrennt erfasstem stdout, stderr und Exitcode. |
| LOG-006 | Der LSP-Adapter MUSS Diagnosen direkt projizieren. Bei stdio-Transport MUSS stdout ausschliesslich Protokollnachrichten enthalten. | P1 | LSP-Test mit parallelen Diagnosen und Betriebsereignissen. |
| LOG-007 | Strukturierte Ereignisse MÜSSEN ein dokumentiertes Schema mit Ereignisname, Laufbezug und geordneter Sequenz besitzen. | P1 | Schema- und Reihenfolgetests. |
| LOG-008 | Der Host MUSS die Vertraulichkeit von Credentials und optional von Quellinhalten wahren. Bekannte Geheimnisfelder dürfen nicht in Ereignisse übernommen werden. | P1 | Resolver- und JDBC-Tests mit markierten Testgeheimnissen. |
| LOG-009 | Häufige Ereignisse MÜSSEN ohne unbeschränkte Puffer und ohne unkontrolliert hohe Metrik-Kardinalität erfasst werden können. | P3 | Lasttest mit begrenzter Queue und dokumentierter Verwerfungsstatistik. |
| LOG-010 | CLI, JVM-Dienste und WASM MÜSSEN eigene Ausgabeadapter verwenden; globale Logger-Listener und Prozess-Systemproperties zur Steuerung einzelner Läufe sind unzulässig. | P1 | Zwei unabhängige Compilerinstanzen beeinflussen einander nicht. |

## 3. Fachliche Diagnostik: Typen

### 3.1 Identitäten und Positionen

`DiagnosticCode(String value)` ist die veröffentlichte fachliche Klassifikation, etwa `ILI-SEM-0001`. Der Konstruktor prüft das Katalogformat und verbietet leere Werte. Der Code darf nach Veröffentlichung nicht mit einer anderen Bedeutung wiederverwendet werden.

`DiagnosticId(String value)` identifiziert eine konkrete Diagnose im zugehörigen Analyseergebnis. Gleiche Codes an zwei verschiedenen Referenzen ergeben verschiedene Diagnose-IDs. Die Identität ist kein dauerhaftes fachliches Objekt und kein Ersatz für `SymbolId`. Ein Adapter darf eine ID aus einem älteren Snapshot nicht ohne Prüfung im aktuellen Snapshot auflösen.

Die Erzeugung einer `DiagnosticId` MUSS deterministisch gegenüber der kanonischen Diagnoseeingabe sein; sie darf nicht von `RunId`, Uhrzeit, Thread, Objektadresse oder globalem Zähler abhängen. Die konkrete Codierung bleibt intern. Für Vergleiche unabhängiger Compiler implementiert der Testharness eine explizite Normalisierung nach Code, Quellbezug und Argumenten. Nicht reproduzierbare Fremd-IDs werden nicht als fachliche Unterschiede behandelt.

`RelatedLocation(SourceRange range, String messageKey, List<DiagnosticArgument> arguments)` beschreibt beispielsweise die frühere Deklaration bei einem doppelten Namen. Alle Collections werden defensiv kopiert. Eine globale Zusatzinformation ohne Quellbereich wird als Argument beziehungsweise Hilfetext modelliert; eine künstliche Position `0:0` ist nicht zulässig.

### 3.2 Schweregrad und Phase

`Severity` enthält genau `ERROR`, `WARNING`, `INFO` und `HINT`. Fachliche Fehler sind `ERROR` innerhalb der angeforderten Modellabhängigkeiten. Das Compilation-Gate berücksichtigt zusätzlich die Vollständigkeit der Prüfung und die ausdrücklich konfigurierte `WarningPolicy`. `REJECT_WARNINGS` verändert die ursprüngliche Severity einer Warnung nicht; es ergibt einen eigenen Ablehnungsgrund. Aus einem Host-Loglevel darf keine fachliche Severity abgeleitet werden.

`Phase` enthält die folgenden kanonischen Werte:

```java
public enum Phase {
    SOURCE_RESOLUTION,
    LEXING,
    PARSING,
    DECLARATION_INDEXING,
    BINDING,
    TYPE_CHECKING,
    SEMANTIC_CHECKING,
    PROJECTION,
    TRANSFER,
    VALIDATION,
    DB_MAPPING,
    DB_EXECUTION,
    INFRASTRUCTURE
}
```

Ein Phase-Wert ist eine stabile Diagnosekategorie. Er verpflichtet die Implementierung nicht dazu, stets einen separaten vollständigen Baumdurchlauf auszuführen. Optimierte oder zusammengelegte interne Durchläufe müssen die fachliche Zuordnung erhalten.

### 3.3 Typisierte Argumente

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

### 3.4 Diagnostic

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

## 4. Erzeugung, Sammlung und Darstellung

### 4.1 DiagnosticCatalog

`DiagnosticCatalog` ist unveränderlich und besitzt:

```java
DiagnosticDefinition require(DiagnosticCode code);
List<DiagnosticDefinition> definitions();
void validate(DiagnosticDraft draft);
```

`require` liefert die Definition oder signalisiert einen internen Programmierfehler für einen unbekannten, vom Compiler selbst erzeugten Code. `validate` prüft Namen und Typen der Argumente sowie die erlaubte Phase. Es führt keine I/O durch. Eingehende Protokollwerte werden vorher im Adapter validiert; ein fremder Code ist dort eine ungültige Protokolleingabe und kein Compilerdefekt.

`DiagnosticDefinition` enthält Code, Bedeutung, Standard-Severity, erlaubte Phasen, `messageKey`, Argumentdefinitionen, Positionsregel und eine Katalogversion. Ein Alias darf nur einen ausdrücklich dokumentierten historischen Code auf eine unveränderte Bedeutung abbilden.

### 4.2 DiagnosticCollector

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

Die kanonische Sortierung verwendet zuerst `SourceId.value()` in der von Java `String.compareTo` definierten lexikografischen UTF-16-Codeeinheitenordnung, dann Quellbereich, fachliche Phase, Code und einen stabilen Argumentvergleich. Sie verwendet weder Locale-Kollation noch Map-Einfügereihenfolge. Globale Diagnosen ohne Quelle stehen vor quellgebundenen Diagnosen und werden nach Phase, Code und Argumenten geordnet. Damit besitzt auch das mapbasierte `SourceSet` aus Kapitel 06 eine eindeutige Diagnoseordnung. Eine Legacy-Darstellung darf bei Bedarf eine eigene dokumentierte Reihenfolge verwenden; sie verändert die gespeicherte Liste nicht. Semantisch relevante Deklarations- und Transferreihenfolgen innerhalb der Modelle bleiben davon unberührt.

### 4.3 DiagnosticPresentation

`AnalysisSnapshot.diagnostics()` enthält die vollständige kanonische Liste. Darauf arbeitet:

```java
DiagnosticView project(
    List<Diagnostic> diagnostics,
    DiagnosticDisplayPolicy policy);
```

`DiagnosticView` enthält `visibleDiagnostics()` und `suppressedDiagnostics()`. Jeder `SuppressedDiagnostic` nennt ID, Grund und sichtbare Ursachendiagnose. Mögliche Gründe sind `DEPENDENT_ON_VISIBLE_ERROR`, `DUPLICATE_PRESENTATION` und `USER_FILTER`. Das Original bleibt unverändert.

Die erste Ursachenregel ist konservativ: Eine abhängige Diagnose darf nur verdeckt werden, wenn ihre Aussage nachweislich aus der bereits sichtbaren Ursache folgt. Räumliche Nähe oder gleiche Worte genügen nicht. Eine unaufgelöste Referenz hinter einem fehlenden Import kann dazugehören. Ein eigenständiger ungültiger Zahlenbereich im selben Modell gehört nicht dazu.

Ein Anzeige-Limit muss als Anzeige-Limit erkennbar bleiben, etwa über `hiddenCount`; die fachliche Analysevollständigkeit darf daraus nicht abgeleitet werden. Das Gate liest keine `DiagnosticView`.

### 4.4 Renderer und Quick-Fixes

`DiagnosticRenderer.render(Diagnostic diagnostic, RenderingContext context)` liefert einen String und verändert keinen Diagnosezustand. `RenderingContext` enthält Sprache, Positionsformat und gegebenenfalls das Kompatibilitätsprofil. Er enthält keine Zugriffsmöglichkeiten auf Repository oder Datenbank.

`LegacyDiagnosticRenderer` verwendet den erfassten Katalog des aktiven `LegacyCliProfile`. Das fachliche `LegacyCompilerProfile` referenziert dieses CLI-Profil über `cliProfile()`, statt dessen Felder nochmals unabhängig zu definieren. Fehlt ein Mapping, wird eine dokumentierte allgemeine Darstellung verwendet und die Kompatibilitätslücke im Testbericht sichtbar. Ein fehlendes Mapping darf nicht durch erfundene historische Formulierung kaschiert werden.

`JsonDiagnosticWriter.write(Diagnostic diagnostic, JsonOutput output)` gehört in einen Adapter. Strings werden korrekt escaped; Severity und Codes bleiben maschinenlesbar. Die Klassifikation entspricht dem [WASM-Vertrag](10-webassembly-und-portabilitaet.md).

`QuickFixProvider.suggest(DiagnosticId id, AnalysisSnapshot snapshot)` liegt ausserhalb der Foundation. Es erzeugt versionierte Vorschläge und keine sofortigen Änderungen. Eine tatsächliche Anwendung prüft alle betroffenen Dokumentrevisionen. Der Diagnosekern darf keine LSP-Klassen oder beliebige ausführbare Closures in ein Diagnoseobjekt aufnehmen.

## 5. Anfänglicher Diagnosekatalog

Diese Codes sind **neue normative Codes des geplanten Projekts**. Sie sind keine Behauptung über Codes des bisherigen ili2c. Legacy-Meldungstexte werden zusätzlich anhand reproduzierbarer Referenzfälle aufgenommen. Die konkrete Semantik einer Sprachregel wird durch die Referenzhandbücher und entsprechende Tests bestimmt, nicht allein durch diese Tabelle.

| Code | Bedeutung / Standard-Severity | Phase | Verbindliche Argumente und Quellbezug |
|---|---|---|---|
| `ILI-SYN-0001` | Lexikalisch ungültige Eingabe / ERROR | LEXING | `token: TOKEN`; tatsächlicher problematischer Bereich |
| `ILI-SYN-0002` | Unerwartetes Token / ERROR | PARSING | `actual: TOKEN`, `expected: IDENTIFIER_LIST`; tatsächliches Token |
| `ILI-SYN-0003` | Erwartetes Token fehlt / ERROR | PARSING | `expected: IDENTIFIER_LIST`; nullbreiter Einfügebereich |
| `ILI-SYN-0004` | Unerwarteter Inhalt nach vollständiger Starteingabe / ERROR | PARSING | `actual: TOKEN`; erster nicht akzeptierter Restbereich |
| `ILI-SYN-0005` | Nicht unterstützte Sprachversion / ERROR | PARSING | `actual: TEXT`, `supported: IDENTIFIER_LIST`; Versionsliteral |
| `ILI-SYN-0006` | Syntaxkonstrukt in gewählter Sprachversion unzulässig / ERROR | PARSING | `construct: TEXT`, `language: TEXT`; konkretes Konstrukt |
| `ILI-RES-0001` | Angefordertes oder importiertes Modell fehlt / ERROR | SOURCE_RESOLUTION | `model: IDENTIFIER`; Importposition, falls vorhanden |
| `ILI-RES-0002` | Mehrdeutige Modellauflösung / ERROR | SOURCE_RESOLUTION | `model: IDENTIFIER`, `candidates: IDENTIFIER_LIST`; Importposition oder global |
| `ILI-RES-0003` | Quelle konnte nicht bereitgestellt werden / ERROR | SOURCE_RESOLUTION | `source: TEXT`, `reason: TEXT`; URI ohne Credentials, optional global |
| `ILI-RES-0004` | Angeforderte Modellidentität passt nicht zum Quelleninhalt / ERROR | SOURCE_RESOLUTION | `expected: IDENTIFIER`, `actual: IDENTIFIER_LIST`; Modelldeklaration |
| `ILI-SEM-0001` | Name im erforderlichen Scope unaufgelöst / ERROR | BINDING | `name: IDENTIFIER`; jeweiliges Namenssegment |
| `ILI-SEM-0002` | Name an dieser Stelle mehrdeutig / ERROR | BINDING | `name: IDENTIFIER`, `candidates: IDENTIFIER_LIST`; Referenz plus Kandidaten als Related Locations |
| `ILI-SEM-0003` | Unzulässige doppelte Deklaration / ERROR | DECLARATION_INDEXING | `name: IDENTIFIER`; zweite Deklaration, erste als Related Location |
| `ILI-SEM-0004` | Referenz verletzt Sichtbarkeits- oder Reihenfolgeregel / ERROR | BINDING | `name: IDENTIFIER`, `rule: TEXT`; Referenz und Deklaration |
| `ILI-SEM-0005` | Typ für die geprüfte Operation unvereinbar / ERROR | TYPE_CHECKING | `actual: TYPE_NAME`, `expected: TYPE_NAME`; Ausdruck beziehungsweise Deklaration |
| `ILI-SEM-0006` | In dieser Beziehung unzulässiger Zyklus / ERROR | SEMANTIC_CHECKING | `relationship: TEXT`, `cycle: IDENTIFIER_LIST`; verantwortliche Kante plus Related Locations |
| `ILI-SEM-0007` | Numerischer Bereich oder deklarierte Genauigkeit ungültig / ERROR | TYPE_CHECKING | `reason: TEXT`; Bereichs- beziehungsweise Genauigkeitsdeklaration |
| `ILI-SEM-0008` | Ungültiger Schritt in einem Objektpfad / ERROR | TYPE_CHECKING | `segment: IDENTIFIER`, `reason: TEXT`; Pfadsegment |
| `ILI-SEM-0009` | Übersetzungsbeziehung verletzt eine geprüfte Regel / ERROR | SEMANTIC_CHECKING | `element: IDENTIFIER`, `rule: TEXT`; Element und Gegenstück |
| `ILI-SEM-0010` | Vererbung oder Überschreibung verletzt eine geprüfte Regel / ERROR | SEMANTIC_CHECKING | `element: IDENTIFIER`, `rule: TEXT`; betroffene Deklarationen |
| `ILI-PROJ-0001` | Gültiges Modellmerkmal wird vom gewählten Verbraucher noch nicht unterstützt / ERROR des Projektionsvorgangs | PROJECTION | `consumer: TEXT`, `feature: TEXT`; Merkmal soweit lokalisierbar |
| `ILI-INF-0001` | Interner Fehler / ERROR | INFRASTRUCTURE | `failureId: TEXT`; optional lokalisiert, keine ungefilterte Exceptionnachricht |
| `ILI-INF-0002` | Explizites Ressourcenlimit erreicht / ERROR des betroffenen Vorgangs | INFRASTRUCTURE | `resource: TEXT`, `limit: INTEGER`; Analyseabdeckung muss unvollständig bleiben |

`ILI-PROJ-0001` gehört zum Ergebnis des Verbrauchers. Es darf ein vorher gültiges `CompiledSchema` nicht rückwirkend ungültig machen. Netzwerkfehler und Ressourcenlimits sind ebenfalls kein Beweis dafür, dass ein INTERLIS-Modell sprachlich ungültig ist; der Gesamtstatus und die Phase erhalten diesen Unterschied.

WARNINGS, INFO und HINTS werden nur mit fachlich begründeten, getesteten Katalogeinträgen ergänzt. Es gibt keine pauschalen Warnungen für lediglich ungewöhnliche, aber gültige Modellierung. Eine alte Warnung erhält erst nach Referenzaufnahme ein Kompatibilitätsmapping.

## 6. Laufereignisse und Zustandsautomat

### 6.1 Neutrale Schnittstellen

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

### 6.2 Kontextwerte, Erzeugung und Besitz

Die öffentliche Kontextübergabe besteht aus kleinen neutralen Werten und einer injizierbaren Factory:

```java
public record RunContext(
    RunId runId,
    Optional<RunId> parentRunId,
    Map<String, String> correlationAttributes
) {}

public interface RunContextFactory {
    RunContext createRoot();
    RunContext createRoot(Map<String, String> correlationAttributes);
    RunContext createChild(RunContext parent);
}
```

Die Darstellung benennt den Vertrag; Konstruktorprüfung, defensive Kopie und Factoryimplementierung sind zu ergänzen. `RunContext` ist unveränderliche Metadateninformation, kein bereits gestarteter Span und kein veränderlicher Lifecycle. Er enthält weder Observer noch Uhr, Threads, Workspace, Compilerrequest oder Snapshot. `correlationAttributes` ist eine begrenzte nullfreie Map kurzer Strings. Zulässige Schlüssel und Grössenlimits werden vom Hostprofil festgelegt; Credentials und Quelltext gehören nicht hinein. Eltern- und eigene RunId dürfen nicht gleich sein.

`createRoot()` entspricht einer Wurzel mit leeren Korrelationsattributen. `createRoot(attributes)` kopiert die Attribute und erzeugt eine frische RunId. `createChild(parent)` erzeugt eine frische RunId, setzt die Parent-ID und übernimmt die unveränderlichen Korrelationsattribute. Es startet kein Ereignis und verändert den Elternkontext nicht. Geschwister erhalten verschiedene IDs.

Die ergänzende statische Methode `RunContextFactory.defaults()` erzeugt eine neue Defaultfactory. Diese verwendet einen frischen UUID-basierten Factory-Namensraum und einen threadsicheren instanzlokalen monotonen Zähler. Ein Überlauf wird erkannt; IDs werden nicht wiederverwendet. Der Zähler ist kein prozessglobaler Symbol- oder Quellenzähler und beeinflusst keine fachlichen Ergebnisse. Die Portabilität der verwendeten UUID-/Zählerimplementierung wird im P0/P1-Vertragstest geprüft. Ein Host oder Test kann eine eigene Factory mit festgelegtem eindeutigem Namensraum injizieren; bei einem Zielruntimeproblem wird dieser Adapterweg benutzt und nicht auf globale Variablen oder Uhrzeit als vermeintlich eindeutige ID ausgewichen. Kryptografische Geheimhaltung ist kein Zweck einer RunId.

`CompilerServices` enthält gemäss Kapitel 06 `RunObserver observer`, `ClockSource clock` und `RunContextFactory runContexts`. Die kurzen Engine-Methoden erzeugen mit dieser Factory einen neuen Wurzelkontext und delegieren an genau einen der vollen Aufrufe:

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

### 6.3 Öffentliche Operationen und Abbruch

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

`RunCompletion` ist ein abgeschlossener Typ mit `Succeeded`, `Rejected`, `Cancelled` und `Failed`. Er enthält geeignete Zähler und optional Snapshot- beziehungsweise Failure-Bezüge, aber keinen gesamten Metamodellgraphen. `Rejected` bedeutet, dass kein vollständig geprüftes Erfolgsschema ausgestellt wurde. Die `RejectionSummary` aus Kapitel 06 enthält die vom Gate festgestellten Gründe, betroffene Diagnose-IDs der angeforderten Closure, angeforderte Roots und nicht unterstützte Features. Gründe sind `SOURCE_ERRORS`, `WARNINGS_REJECTED`, `MISSING_INPUT`, `AMBIGUOUS_INPUT`, `ENVIRONMENT_FAILURE` und `INCOMPLETE_SUPPORT`. Mehrere Gründe können zugleich gelten. Die Variante allein behauptet keine sprachliche Ungültigkeit; Logging und Adapter übernehmen diese Zusammenfassung statt das Gate nochmals anhand der gesamten Workspace-Diagnoseliste zu implementieren. `Cancelled` und `Failed` bleiben davon getrennt. `analyze` kann einen vollständig ausgeführten Analyselauf mit fachlichen Diagnosen abschliessen; die Ereigniszusammenfassung MUSS deshalb zusätzlich `errorCount`, `analysisComplete`, Coveragegründe und `compiledSchemaProduced` ausweisen. Ein Host darf aus dem Ausführungsstatus allein keine Sprachgültigkeit ableiten.

Die Übernahme in `RunCompletion` ist eine neutrale Datenprojektion: katalogisierte Reasonnamen, Diagnose-IDs, Feature-IDs und Anzahlen. `RunCompletion` importiert weder `RejectionSummary` noch `ModelSelector` oder andere Compilerklassen in die Foundation. Die Originalzusammenfassung bleibt unverändert im fachlichen Ergebnis. Ein späterer Verbraucher kann eigene katalogisierte Ablehnungsgründe projizieren; daraus entsteht keine zusätzliche Interpretation seiner Fachregeln im Logger.

### 6.4 Anfangsereignisse

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

### 6.5 Observer-Ausfall

`SafeRunObserver` umschliesst die konfigurierte optionale Senke. Er fängt gewöhnliche Exceptions der Senke ab, markiert sie als beeinträchtigt und verwendet einen separat injizierten, begrenzten `ObservationFailureHandler`. Er darf nicht pauschal sämtliche `Throwable` abfangen, um einen kritischen VM-Zustand scheinbar zu heilen.

Der Failure-Handler hat keine Rückwirkung auf das fachliche Ergebnis. Der Host kann einen einmaligen Fallback auf stderr verwenden, sofern der aktive Ausgabe-/Protokollvertrag dies erlaubt. Ist auch der Fallback nicht verfügbar, wird der Zustand lokal begrenzt gezählt. Kein rekursiver Aufruf der fehlerhaften Senke.

**Die Garantie ist eine kontrollierte lokale Emission, keine garantierte Zustellung bei Prozessabsturz, Stromausfall oder Netzverlust.** Ein Exporter darf anhand `RunId + sequence` deduplizieren. Das Dokument schreibt kein Transaktionsprotokoll für Logs vor.

## 7. CLI, LSP, Dienst und Browser

### 7.1 CLI-Kompatibilität

`LegacyCompilerProfile.cliProfile()` verweist auf das in [CLI und SDK](18-cli-sdk-und-kompatibilitaetsadapter.md) definierte `LegacyCliProfile`. Dieses bestimmt je Referenzversion die Aufrufe, Standardoptionen, Severity-Darstellung, stdout-/stderr-Zuordnung, Textnormalisierung, Reihenfolge und Exitcodes. Die Werte müssen aufgenommen werden, bevor sie implementiert werden. Insbesondere wird hier nicht angenommen, dass alle bisherigen Fehler immer Exitcode 1 oder alle Meldungen stderr verwenden.

`CliOutcomeMapper.classify(CompilationOutcome outcome)` liefert die passende `CliTermination` anhand der typisierten Ergebnisvariante und im Ablehnungsfall ihrer `RejectionSummary`. `ExitCodePolicy.exitCode(CliTermination termination, LegacyCliProfile profile)` bildet diese auf den erfassten numerischen Wert ab. `CliOutputWriter.writeDiagnostics(...)` verwendet den Renderer und den geplanten Ausgabekanal. Diese Schritte dürfen das semantische Ergebnis nicht verändern. Insbesondere ergibt `Rejected` wegen `INCOMPLETE_SUPPORT` keine Meldung „Quelltext ungültig“. `WARNINGS_REJECTED` bleibt eine Policyentscheidung bei unverändertem Warnungsschweregrad. Eine erneute Ableitung der Gateentscheidung aus sämtlichen, möglicherweise rootfremden Diagnosen ist unzulässig. Ein nachfolgender Export-/Flushfehler kann den gesamten CLI-Aufruf betrieblich scheitern lassen, obwohl die Kompilierung erfolgreich war; erst der vollständige Ablauf erzeugt das abschliessende `CliExecutionResult`.

Eine neue strukturierte Ausgabe wird als expliziter Modus angeboten. Ihre Option und die Behandlung gemischter Formate sind im CLI-Vertrag festzulegen. Ein JSON-Lines-Datenstrom enthält pro Zeile genau ein vollständiges Objekt; normale Textmeldungen dürfen nicht hineingemischt werden. Ungültige CLI-Optionen sind Bedienfehler des Adapters.

### 7.2 LSP

Der LSP projiziert Code, Severity, Message, Bereich, Related Information und gegebenenfalls die Diagnose-ID in die Protokollstruktur. Er veröffentlicht nur Ergebnisse, deren Dokumentversionen noch passen. Veraltete Ergebnisse dürfen optional angezeigt werden, wenn die Oberfläche sie ausdrücklich als veraltet markiert; sie werden nicht als aktuelle Diagnosepublikation ausgegeben.

Betriebslogs gehen über erlaubte Protokollmechanismen oder getrennte Senken. Im stdio-Modus dürfen auch Bibliotheken keine Konsolenausgaben auf stdout erzeugen. Dieser Fall benötigt einen Integrationstest, nicht nur eine Codekonvention.

### 7.3 JVM-Dienste und Kubernetes/OpenShift

Ein Container-Host kann strukturierte JSON-Ereignisse an stdout/stderr ausgeben und die Einsammlung seiner Betriebsplattform überlassen. Alternativ kann ein OpenTelemetry-Adapter verwendet werden. Beides wird nicht gleichzeitig zwingend für dieselben Ereignisse aktiviert.

OpenTelemetry bietet unter anderem Zeitstempel, Severity, Body, Resource- und Ereignisattribute sowie Trace-/Span-Bezüge. Die Zuordnung orientiert sich an dessen offiziellem [Log-Datenmodell](https://opentelemetry.io/docs/specs/otel/logs/data-model/). Es ist ein Host-Integrationsvertrag; eine OpenTelemetry-SDK-Abhängigkeit im Compilerkern ist ausgeschlossen.

Geeignete Metriken sind Dauer und Anzahl von Kompilierungen, Phasendauern, Cache-Treffer, analysierte Quellen, Abbrüche und interne Fehler. `SourceId`, Modellname, vollständiger Diagnosewert und `RunId` sind keine Standard-Metriklabels. Andernfalls wächst die Zahl der Zeitreihen mit dem Datenbestand.

Die kleine Bibliothek implementiert weder einen Kubernetes-Client noch einen eigenen Healthserver. Ein Dienstadapter kann Betriebszustand, Queue und Ressourcenverbrauch separat bereitstellen. Fachlich abgelehnte Modelle bedeuten nicht automatisch, dass der Pod ungesund ist.

### 7.4 WASM

Der Browseradapter liefert Diagnosen als DTOs. Optionale Betriebsereignisse können über einen Callback oder gesammelt im Ergebnis übertragen werden. Dieser Kanal muss eine definierte Begrenzung haben. Der Compilerkern darf kein `console.log` als vertragliche Diagnoseausgabe verwenden.

Revisionen und andere 64-Bit-Werte werden entsprechend dem [WASM-Protokoll](10-webassembly-und-portabilitaet.md) verlustfrei codiert. Betriebsereignisse und fachliche Ergebnisse erhalten jeweils ihre eigene Schemanummer. Ein neuer optionaler Betriebswert darf die fachliche Kompatibilität nicht brechen.

## 8. Konkrete Szenarien und Vertragstests

### 8.1 Fehlendes importiertes Modell

Ein Modell importiert `BaseModel`, dessen Quelle fehlt. Der Resolver liefert `ILI-RES-0001`. Die Bindung erfasst abhängige unaufgelöste Referenzen mit `causeId` auf die Importdiagnose. Die Editoransicht zeigt die Importursache und unabhängige Fehler. Die vollständigen Daten bleiben abfragbar; `compile` erzeugt kein Schema.

Wird `BaseModel` später ergänzt, invalidiert der Workspace auch die vormals negative Auflösung. Die neue Diagnosemenge darf keine IDs des alten Berichts als aktuelle Ursachen enthalten. Frische Kompilierung und inkrementeller Stand werden kanonisch verglichen.

### 8.2 Interner Fehler und fehlerhafte Logsenke

Ein Test injiziert einen internen Fehler in eine Semantikphase und gleichzeitig einen werfenden Observer. Der Aufrufer erhält `CompilationOutcome.Failed` mit `FailureId` und einer passenden Fehlerdiagnose. Der Observerfehler ersetzt diese Ursache nicht. Der Lifecycle versucht genau einen Abschluss zu emittieren. Ein technischer Hostbericht enthält den ursprünglichen Stacktrace und denselben Failure-Bezug; sensible Testwerte werden nicht ausgegeben.

### 8.3 Abbruch nach einer neuen Editorrevision

Revision 20 wird analysiert, während Revision 21 eingeht. Der Host kann für 20 Abbruch anfordern. Falls 20 trotzdem noch kontrolliert fertig wird, wird das Ergebnis wegen der veralteten Publikationsrevision verworfen. Es erhält seinen tatsächlich erreichten Laufabschluss; ein abgeschlossener Erfolg wird nicht nachträglich in `Cancelled` umgeschrieben. Die aktuelle Oberfläche erhält ausschliesslich den passenden Stand von 21.

### 8.4 Pflichtprüfungen auf Klassenebene

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
| `LegacyDiagnosticProfileTest` | Referenztexte, Reihenfolge, Kanäle und Exitcodes für erfasste Profile |
| `RejectionPresentationTest` | Warnungspolitik, unvollständige Implementierung, Umgebungsfehler und Quellfehler bleiben typisiert; rootfremde Fehler verändern die Gatezusammenfassung nicht |
| `ObservationBudgetTest` | Begrenzte Queue, dokumentiertes Verwerfen, keine unbeschränkten Strings oder Metriklabels |

## 9. Fachliche Quellen und Übernahmegrenzen

Der bisherige Compiler verwendet unter anderem globale Logging-Anbindung und den mutablen Compilerlauf. Der bestehende Java-LSP kapselt dies mit Compile-Lock und Logbehandlung in [`Ili2cUtil`](https://github.com/edigonzales/interlis-lsp/blob/main/src/main/java/ch/so/agi/lsp/interlis/compiler/Ili2cUtil.java). Diese Kopplung begründet den neuen direkten Diagnosevertrag; die konkreten Texte bleiben Referenzmaterial für die Kompatibilität.

Die [`diagnostic-pipeline.md` von ilic-fork](https://github.com/edigonzales/ilic-fork/blob/main/docs/architecture/diagnostic-pipeline.md) ist eine wichtige Entwurfsreferenz für Codes, Ursachen und Unterdrückung von Folgefehlern. Ihre C++-Implementierung wird nicht als Java-API übernommen. Normative Aussagen dieses Dokuments gelten für das neue Projekt; externe Repositories bleiben anhand des Quellenregisters gepinnte Referenzen.
