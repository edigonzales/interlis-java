# 04 – Quelltext, Syntax und Parser

## 1. Zweck und Verbindlichkeit

Dieses Dokument spezifiziert den quelltextnahen Teil des neuen Compilers. Es ist ein Implementierungsauftrag. Die Anforderungen gelten für die JVM und für die in [10 – WebAssembly](10-webassembly-und-portabilitaet.md) beschriebenen Zielplattformen identisch. Die Begriffe MUSS, SOLL und KANN haben die in [00](00-auftrag-und-agentenregeln.md) festgelegte Bedeutung. P1 bezeichnet die durchgängige erste fachliche Strecke; P2 die vollständige Sprachabdeckung. Ein in P1 noch nicht unterstütztes Konstrukt ist ausdrücklich sichtbar zu machen und verhindert einen behaupteten erfolgreichen Produktionscompile, wenn es zur angeforderten Modellmenge gehört.

Die Syntaxschicht erhält bereits dekodierten Text. Sie beschafft keine Dateien, lädt keine Modelle nach und schreibt keine Logs. Dateidekodierung, URI-Normalisierung und Repositoryzugriff sind Hostaufgaben. Der Syntaxkern besitzt keine erforderliche Abhängigkeit auf `Path`, `File`, `URL`, HTTP, LSP4J oder ein Logging-Backend.

**Entscheidung:** ANTLR 4 erzeugt Lexer und Parser. Eine anschliessende interne Abbildung erzeugt eigene Syntaxobjekte. Kein öffentlicher Vertrag verwendet ANTLR-Typen. Lexer und Parser enthalten keine Metamodellmutationen und keine fachliche Typprüfung.

Die untersuchte Ausgangsgrammatik ist [geostandards-ch/interlis-antlr4](https://github.com/geostandards-ch/interlis-antlr4/tree/f8f97238c2540a49915f8bb97ee99bbf434b74da). Ihr Einstieg verlangt kein `EOF`, und die angegebene Versionszahl allein erzwingt nicht alle Unterschiede zwischen 2.3 und 2.4. [ili2ogc/runtime/parse.py](https://github.com/geostandards-ch/ili2ogc/blob/dcc23fac1f879e8ad3340ed1b208072b1a245564/src/interlis/runtime/parse.py) enthält ergänzende Tokenbehandlung. Deshalb wird die Grammatik als nachvollziehbarer Ausgangspunkt übernommen, nicht als bereits nachgewiesene vollständige Implementierung der beiden Sprachversionen.

## 2. Normative Anforderungen

| ID | Normative Anforderung | Phase | Abnahme |
|---|---|---|---|
| SRC-001 | Jede Quelle MUSS eine opake `SourceId`, eine Revision und den unveränderten Java-String besitzen. | P1 | Zwei Revisionen derselben Quelle bleiben getrennt adressierbar. |
| SRC-002 | Alle internen Quellbereiche MÜSSEN halb-offene UTF-16-Offsets verwenden. | P1 | Tests mit Umlauten, Supplementary Characters, CRLF und EOF. |
| SRC-003 | Zeilen- und Spaltenumrechnung MUSS deterministisch und ohne Plattform-Newline-Annahmen sein. | P1 | Roundtrip der gültigen Positionen und dokumentierte Behandlung von Zeilenumbrüchen. |
| SRC-004 | Ein Offset oder Bereich ausserhalb der bezeichneten Quellrevision MUSS als API-Fehlgebrauch zurückgewiesen werden. | P1 | Negative Offsets, falsche Quelle, falsche Revision und Ende hinter Textlänge. |
| SRC-005 | Inhalt, Kommentare und Zwischenräume MÜSSEN aus einem Syntaxsnapshot exakt rekonstruierbar bleiben. | P1 | Identischer UTF-16-String nach Rekonstruktion für jedes Syntaxfixture. |
| SRC-006 | Kanonisierung einer URI MUSS vor Eintritt in den Kern erfolgen; Gleichheit von `SourceId` ist Stringgleichheit. | P1 | Keine plattformabhängige Auflösung und keine Netz-/Dateizugriffe bei Konstruktion. |
| SRC-007 | Content-Fingerprints MÜSSEN Algorithmus und Eingabekodierung festlegen und dürfen keine Prozessidentitäten enthalten. | P1 | Gleiche Inhalte erhalten auf allen Zielplattformen denselben Fingerprint. |
| SYN-001 | Der produktive Einstieg MUSS die gesamte Eingabe bis EOF prüfen. | P1 | Gültiges Modell plus nachlaufender Text wird abgelehnt. |
| SYN-002 | Lexerfehler und Parserfehler MÜSSEN gesammelt werden; Standardlistener auf stderr sind zu entfernen. | P1 | Ein unzulässiges Zeichen ist sichtbar, auch wenn der Parser den Rest akzeptiert. |
| SYN-003 | Ausschliesslich INTERLIS 2.3 und 2.4 sind zulässige Sprachversionen. | P1 | 1.0, 2.2 und unbekannte Versionsangaben erhalten eine erklärende Diagnose. |
| SYN-004 | Versionsabhängige Schlüsselwörter und Konstrukte MÜSSEN über einen expliziten Sprachprofilvertrag geprüft werden. | P2 | Positive und negative versionsbezogene Tests; keine blosse Prüfung des Headers. |
| SYN-005 | `STRICT` und `EDITOR` MÜSSEN dieselbe Sprachgültigkeit verwenden. | P1 | Recovery allein erzeugt in keinem Modus ein gültiges Compile-Ergebnis. |
| SYN-006 | Editor-Recovery MUSS noch erkennbare umgebende Deklarationen und ihre Bereiche erhalten. | P1 | `height :` zerstört nicht die umgebende Klasse oder die folgende Klasse. |
| SYN-007 | Fehlende, übersprungene und synthetische Syntax MUSS ausdrücklich von ursprünglicher Syntax unterscheidbar sein. | P1 | Fehlstellen mit Nullbreite; Recoverysegment mit originalem Textbereich. |
| SYN-008 | Kein öffentlicher Typ DARF von ANTLR-Bäumen, Tokens oder RecognitionExceptions abhängen. | P1 | Architekturtest und öffentliche API-Prüfung. |
| SYN-009 | Parsen MUSS bei Fehlern und bei Budgetüberschreitung terminieren. | P1 | Wiederholte fehlerhafte Eingaben und tiefe Verschachtelung liefern definiertes Ergebnis. |
| SYN-010 | Fragmentanalyse MUSS expliziten Fragmenttyp und einen unveränderten Quellbereich besitzen. | P2 | Ausdruck, Objektpfad und Attributfragment mit korrektem Offset; Restinhalt wird erkannt. |
| SYN-011 | Verlustfreie Syntax und fachliche Modellprojektion MÜSSEN getrennte Repräsentationen bleiben. | P1 | Syntax enthält Fehlerknoten; Metamodell enthält keinen ANTLR-Kontext. |
| SYN-012 | Grammatikstand, Generatorversion und Runtimeversion MÜSSEN fest gepinnt sein. | P0 | Reproduzierbarer Gradlebuild, dokumentierte Herkunft und Lizenz. |
| SYN-013 | Die AST-Abbildung MUSS jeden erkannten Produktionszweig behandeln oder sichtbar als noch nicht unterstützt kennzeichnen. | P1/P2 | Coverage-Test; kein `default -> null` und kein stilles Überspringen. |
| SYN-014 | Identitäten für Syntaxknoten DÜRFEN keinen statischen Zähler und keine FQN als alleinige Identität verwenden. | P1 | Sitzungsparallelität, Duplikate und Einfügen einer vorangehenden Deklaration. |
| SYN-015 | Grammatikänderungen MÜSSEN Gültigkeit, Operatorbindung und Recovery gemeinsam regressionsprüfen. | P1 | Gültige, ungültige und Editorfixtures für jede betroffene Regel. |

## 3. Quelltexttypen in `interlis-model`

### 3.1 `guru.interlis.source.SourceId`

`public record SourceId(String value)` bezeichnet die bereits vom Host kanonisierte Identität. `value` MUSS nichtleer sein. Der Konstruktor prüft lediglich die lokale Form des Vertrags, führt aber keine `toRealPath`-Auflösung, Gross-/Kleinschreibungskorrektur oder URI-Protokollbehandlung durch. `memory:`, `file:` und geeignete Web-URIs sind möglich. Der Compiler darf nicht aus dem Schemapräfix eine Beschaffungsstrategie ableiten.

Die Identität ist weder der Modellname noch der Inhaltshash. Mehrere Modelle können aus derselben Quelle stammen; zwei Quellen können denselben Text enthalten. Symlink-, Redirect- und Grossschreibungsfragen gehören zur Resolverstrategie in [08](08-modellaufloesung-und-repositories.md).

### 3.2 `SourceRevision`, `SourceRange` und `SourcePosition`

```java
public record SourceRevision(long value) {}

public record SourceRange(
    SourceId source,
    SourceRevision revision,
    int startOffset,
    int endOffset
) {}

public record SourcePosition(int line, int character) {}
```

Konstruktorinvarianten: Revision nichtnegativ, Quelle/Revision nichtnull, `0 <= startOffset <= endOffset`, Zeile und Zeichenposition nichtnegativ. Die Prüfung gegen die tatsächliche Textlänge erfolgt durch die zugehörige `SourceText`-Instanz, weil ein Bereich selbst keinen Quelltext besitzt. Ein leerer Bereich ist zulässig; er bezeichnet beispielsweise eine Einfügeposition oder ein fehlendes Semikolon. `endOffset` ist ausgeschlossen. Ein Bereich am EOF ist `[text.length(), text.length())`.

LSP-Dokumentversion, Workspace-Revision und Source-Revision sind unterschiedliche Konzepte. Die Syntaxschicht behandelt die Revision als Wert; die monotonen Aktualisierungsregeln setzt [07](07-workspace-inkrementalitaet-und-lsp.md) durch. IDs und Offsets sind keine druckbaren Benutzerpositionen. CLI-Ausgaben können einsbasierte Zeilen darstellen, ohne den internen Vertrag zu ändern.

### 3.3 `SourceText`

`public final class SourceText` ist unveränderlich. Öffentlich erforderlich:

```java
public SourceText(SourceId id, SourceRevision revision, String text);
public SourceId id();
public SourceRevision revision();
public String text();
public int length();
public LineMap lineMap();
public SourceRange range(int startOffset, int endOffset);
public String slice(SourceRange range);
public ContentFingerprint contentFingerprint();
```

`slice` validiert Quelle und Revision vor der Bereichsprüfung. Es darf niemals Text aus einer anderen Revision anhand zufällig passender Zahlen ausschneiden. Falsche Argumente lösen eine dokumentierte `IllegalArgumentException` beziehungsweise spezialisierte Unterklasse aus; Benutzer-Syntaxfehler werden dadurch nicht dargestellt.

`SourceText` übernimmt einen Java-String, verändert ihn nicht und normalisiert weder Newlines noch Unicode. Ob eine BOM bereits beim Dekodieren entfernt wurde, ist Teil des Eingabeprofils; die Entscheidung muss samt Offsetwirkung dokumentiert werden. Der kanonische Kernvertrag ist der tatsächlich übergebene String. Nicht wohlgeformte Surrogatfolgen bleiben rekonstruierbar, lösen aber eine Lexer-/Quelldiagnose aus und dürfen nicht unbemerkt durch Ersatzzeichen verändert werden.

`contentFingerprint()` verwendet einen projektweit festgelegten Algorithmus. Für den kanonischen Java-String wird SHA-256 über die UTF-16-Codeeinheiten in Big-Endian-Reihenfolge ohne BOM vorgeschlagen und hier als Default festgelegt. Dadurch bleiben auch fehlerhafte Surrogatfolgen eindeutig und plattformgleich. Ein Hash dient dem Cachevergleich, nicht dem Nachweis fachlicher Gültigkeit. Ein Fingerprint-Typ enthält die Algorithmuskennung, damit spätere Änderungen keine stillen Cachetreffer erzeugen.

### 3.4 `LineMap`

`LineMap` ist unveränderlich und gehört zu exakt einer Quelle und Revision. Es speichert Zeilenanfänge und erforderliche Grenzen, keine zweite Vollkopie des Textes.

| Methode | Vertrag |
|---|---|
| `int lineCount()` | Mindestens eine Zeile, auch bei leerem Text. Abschliessender Zeilenumbruch erzeugt eine abschliessende leere Zeile. |
| `SourcePosition positionAt(int offset)` | Akzeptiert Offsets von 0 bis Textlänge; liefert nullbasierte UTF-16-Position. |
| `int offsetAt(SourcePosition position)` | Strikte Konvertierung; keine stillschweigende Begrenzung auf Zeilenende. |
| `int lineStart(int line)` | Offset des ersten Zeichens der Zeile. |
| `int lineContentEnd(int line)` | Offset vor dem Zeilentrenner; bei letzter Zeile EOF. |
| `SourceRange lineRange(int line)` | Inhalt ohne Zeilentrenner, an ursprüngliche Quelle/Revision gebunden. |

CRLF ist ein einzelner Zeilentrenner mit zwei UTF-16-Codeeinheiten. Zusätzlich werden LF und CR unterstützt. Für Offsets innerhalb eines Trenners gilt eine dokumentierte interne Position auf der vorhergehenden Zeile; Adapter, die nur Positionen im Zeileninhalt zulassen, müssen auf eine kanonische Einfügeposition abbilden. `offsetAt` akzeptiert keine Position nach dem Zeileninhalt. Deshalb ist ein perfekter Roundtrip für interne Offsets innerhalb von CRLF nicht versprochen. Für alle Positionen im Zeileninhalt und EOF MUSS der Roundtrip exakt sein.

Ein Index innerhalb eines Surrogatpaares ist als UTF-16-Offset ausdrückbar. Syntaxdiagnostik MUSS jedoch auf Codepointgrenzen zeigen, sofern sie nicht gerade einen fehlerhaften Surrogatteil diagnostiziert. LSP-Adapter dürfen vom Client gelieferte Positionen nicht ungeprüft in semantische Aktionen übernehmen.

### 3.5 ANTLR-Indexumrechnung

`guru.interlis.compiler.internal.antlr.AntlrOffsetMap` übersetzt ANTLR-Codepointindizes in interne UTF-16-Offsets. Er wird aus `SourceText` erzeugt, nicht aus einem normalisierten oder neu kodierten Text. Die konkreten Methoden sind:

```java
int utf16Offset(int codePointBoundary);
SourceRange tokenRange(int startCodePoint, int stopCodePointInclusive);
SourceRange eofRange();
```

Der Adapter prüft die tatsächlichen ANTLR-Konventionen des gepinnten CharStreams. Ein Stopindex inklusive wird genau einmal in das halb-offene Ende umgerechnet. EOF und synthetische Tokens besitzen separate Behandlung. Lexerpositionen sind kein Ersatz für diesen Adapter. Ein Emoji vor einer Diagnose darf deren Spalte nicht um eine UTF-16-Einheit verschieben.

## 4. Öffentliche Syntax-API in `interlis-compiler`

### 4.1 Einstieg und Optionen

```java
public interface SyntaxService {
    SyntaxSnapshot parse(
        SourceText source,
        ParseOptions options,
        CancellationToken cancellation
    );

    FragmentSnapshot parseFragment(
        SourceText source,
        SourceRange fragment,
        FragmentKind kind,
        LanguageVersion languageVersion,
        CancellationToken cancellation
    );
}

public enum ParseMode { STRICT, EDITOR }
public enum FragmentKind { EXPRESSION, OBJECT_PATH, ATTRIBUTE_DECLARATION }
```

`ParseOptions` enthält `ParseMode mode`, `Optional<LanguageVersion> expectedVersion` und das in [06](06-kompilierung-und-semantik.md) definierte `AnalysisBudget`. Die Budgetwerte sind reine Zahlen; keine Uhr, kein Threadpool und kein globales Timeout werden hineingesteckt. In P1 genügt vollständiges Dateiparsen. Fragmentmethoden werden erst als implementiert veröffentlicht, wenn ihre Tests vorliegen; ein öffentliches leeres Ergebnis als Platzhalter ist unzulässig.

`SyntaxServices.create()` ist die öffentliche statische Fabrik für einen eigenständig verwendbaren `SyntaxService`. Sie liefert dieselbe Syntaximplementierung, die die Compiler-Engine intern benutzt, und führt keine Quellenbeschaffung aus. Jeder Parseaufruf besitzt eigene Lexer-/Parserzustände. Ein Sprachdienst muss für reine Syntaxfragen keine internen ANTLR-Klassen instanziieren und keinen vollständigen Modellcompile erzwingen.

`expectedVersion` bedeutet eine Anforderung des Aufrufers. Der erkannte Header bleibt eine eigene Information. Eine Abweichung ist diagnostizierbar und darf nicht durch stilles Umschalten verborgen werden. Bei fehlendem Header kann der EDITOR-Modus ein erwartetes Sprachprofil für Recovery verwenden, muss dessen Herkunft als `ASSUMED_FOR_RECOVERY` kennzeichnen. Ohne beweisbaren gültigen Header entsteht kein `CompiledSchema`.

Fehlerhafte Nutzereingabe liefert einen Snapshot. Abbruch wirft im Syntaxdienst `OperationCancelledException`; der öffentliche Compiler-Einstieg übersetzt dies in sein Ergebnisobjekt. Budgetüberschreitungen liefern eine ausdrückliche Diagnose samt unvollständiger Coverage, soweit ein konsistenter Snapshot möglich ist. Interne Programmierfehler werden nicht in „Modell unbekannt“ umgewandelt.

Ein wegen Budgetüberschreitung konsistent erzeugter Syntaxsnapshot bleibt ausdrücklich unvollständig. Im vollständigen Compilerlauf wird dieser Zustand nach Kapitel 06 zu `Cancelled(RESOURCE_LIMIT)` beziehungsweise bei `analyze` zur entsprechenden `OperationCancelledException`. Er ist kein abschliessendes Sprachgültigkeitsurteil.

### 4.2 `SyntaxSnapshot`

`public final class SyntaxSnapshot` enthält:

| Methode | Ergebnis und Zusicherung |
|---|---|
| `SourceText source()` | Originaltext und Revision; kein temporärer Ersatztext. |
| `ParseMode mode()` | Verwendete Recoverystrategie, kein Gültigkeitsurteil. |
| `LanguageDetection language()` | Erkannte Version, roher Headerbereich und Erkennungszustand. |
| `DocumentSyntax root()` | Eigene Wurzel, auch bei leerer/fehlerhafter Eingabe. |
| `List<SyntaxToken> tokens()` | Unveränderliche Quellreihenfolge einschliesslich relevanter Hidden Tokens und EOF-Marker. |
| `List<TriviaSpan> trivia()` | Kommentare und nicht als Token repräsentierte Zwischenräume. |
| `List<Diagnostic> diagnostics()` | Alle kanonischen Lexer-/Parserdiagnosen nach [09](09-diagnostik-logging-und-telemetrie.md). |
| `SyntaxCoverage coverage()` | Verarbeitete Bereiche, Recoverybereiche und Abbruch-/Budgetzustand. |
| `boolean consumedEntireInput()` | Lexikalisch/technisch bis EOF verarbeitet, unabhängig von Fehlerfreiheit. |
| `SyntaxFingerprint fingerprint()` | Text-, Grammatik-, Profil-, Optionen- und Modusidentität. |
| `Optional<SyntaxNode> nodeAt(int offset, NodeSelection selection)` | Deterministisch kleinster/grösster passender Knoten laut Auswahl. |
| `String reconstruct()` | Exakt `source().text()`; keine Pretty-Print-Ausgabe. |

`consumedEntireInput()` kann trotz Syntaxfehlern `true` sein. Es darf daher in keinem Gate als alleiniger Erfolgsindikator verwendet werden. Umgekehrt darf ein leeres oder wegen eines Limits abgebrochenes Ergebnis nicht als fehlerfreier leerer Modellbestand erscheinen.

### 4.3 Eigene Knoten, Tokens und Trivia

Die geschlossene Syntaxhierarchie verwendet `sealed interface SyntaxNode`. Jeder Knoten besitzt `SyntaxNodeId id()`, `SyntaxKind kind()`, `SourceRange range()` und eine unveränderliche geordnete Kindliste. Ein fehlender Knoten besitzt einen Nullbreitenbereich, eine erwartete Syntaxkategorie und die Diagnose-ID. Übersprungener Quelltext wird über `SkippedSyntax` mit originalem Bereich und Ursache repräsentiert. `RecoveredSyntax` enthält sowohl bekannte Teilknoten als auch den Recoverygrund.

Die Wurzel ist `DocumentSyntax`; darunter mindestens `HeaderSyntax`, `ModelSyntax`, `TopicSyntax`, `DeclarationSyntax`, `TypeSyntax`, `ExpressionSyntax`, `NameSyntax` und die expliziten Fehlerkategorien. `DeclarationSyntax`, `TypeSyntax` und `ExpressionSyntax` sind jeweils geschlossene Unterhierarchien. Ihre vollständige Variante folgt dem Fachkatalog aus [05](05-metamodell-und-typensystem.md); eine generische Liste beliebiger Properties ersetzt diese Varianten nicht.

`SyntaxToken` enthält Tokenart, Originalbereich, Kanal und Herkunft `SOURCE`/`INSERTED`. Der Text kann über den Bereich ausgelesen werden; es ist keine obligatorische Stringkopie pro Token nötig. Ein eingefügtes Token ist kein Bestandteil der verlustfreien Rekonstruktion. `TriviaSpan` unterscheidet mindestens Dokumentationskommentar, sonstigen Kommentar und Whitespace-Gap. Wenn die Grammatik Whitespace überspringt, werden die Lücken zwischen Quellspans aus dem Originaltext erfasst. Der AST muss kein Objekt pro Leerzeichen anlegen.

Kommentare werden zunächst genau einmal quelltextnah erfasst. Eine spätere Zuordnung zu Deklarationen ist ein eigener deterministischer Schritt, etwa `DocumentationBinder.bind(DocumentSyntax)`. Kommentare zwischen zwei Deklarationen dürfen dabei nicht verschwinden oder beiden Deklarationen als vollständiger Text zugeschlagen werden. Formatierung und Dokumentation können unterschiedliche Zuordnungsansichten erhalten, teilen aber dieselben ursprünglichen Spans.

## 5. Strikte Syntax und Editor-Recovery

### 5.1 Gemeinsame Gültigkeit

`STRICT` darf reguläre ANTLR-Recovery einsetzen, um mehrere Fehler zu melden. Jeder eingefügte oder verworfene relevante Token muss jedoch als Diagnose beziehungsweise Recoveryzustand sichtbar sein. „Parser ist bis EOF gelangt“ ist kein Akzeptanzkriterium.

`EDITOR` darf zusätzliche deklarationsorientierte Synchronisation verwenden. Es darf unter anderem beim nächsten sicher erkannten `END`, bei einer folgenden Deklaration oder an einem passenden Semikolon fortsetzen. Es darf nicht einfach Text abschneiden, künstlich schliessen und das reparierte Ergebnis als Quellwahrheit publizieren.

Beide Modi arbeiten auf denselben versionsbezogenen gültigen Produktionen. Toleranz wird durch den Fehlermodellierungsvertrag ergänzt. Falls sich Parserregelvarianten als notwendig erweisen, sind ihre Unterschiede explizit auf Recovery beschränkt und durch Tests nachzuweisen.

### 5.2 Interne Klassen

| Klasse | Verantwortlichkeit und zentrale Methode |
|---|---|
| `AntlrSyntaxService` | Implementiert `SyntaxService`; erzeugt pro Parseaufruf frische Lexer-/Parserzustände. |
| `VersionHeaderScanner` | `detect(SourceText)`; begrenzte headerbezogene Erkennung mit Positionen, keine zweite Vollgrammatik. |
| `LanguageProfile` | Gepinnte erlaubte Tokens, Syntaxfähigkeiten und Versionsregeln; immutable. |
| `VersionedTokenAdapter` | Versionsabhängige, quellbereichserhaltende Tokenanpassungen, sofern durch Sprachtests nötig. |
| `CollectingLexerErrorListener` | Liefert `ILI-SYN-0001` nach Katalog09 und arbeitet ohne stderr. |
| `CollectingParserErrorListener` | Liefert unerwartete/fehlende Tokens mit Originalbereichen. |
| `EditorRecoveryStrategy` | Deklarationsorientierte Recovery mit Fortschrittsgarantie und Ereignisprotokoll. |
| `SyntaxTreeBuilder` | `build(...)`; totale Abbildung des Parse-Ergebnisses auf eigene Knoten. |
| `SyntaxCoverageBuilder` | Markiert unverarbeitete, rekonstruierte und synthetische Teile. |
| `SyntaxIdentityReconciler` | Wiederverwendet eindeutige unveränderte Anker aus bereitgestellten Vorversionen. |

Jede Recoveryiteration muss entweder mindestens einen Originaltoken verbrauchen, den Parserzustand nachweisbar ändern oder die Wiederherstellung beenden. Eine Kette von Zero-Width-Einfügungen ohne Fortschritt wird durch Budget/Schutzgrenze beendet. Der Grenzwert und das dabei sichtbare Diagnoseverhalten sind konfigurierbar, aber Defaultwerte werden im Testfixture festgeschrieben.

### 5.3 Versionsregeln und Grammatikpflege

Die Hauptregel wird für vollständige Dokumente mit EOF abgeschlossen. Der in der Ausgangsgrammatik vorhandene INTERLIS-1-Einstieg wird für den neuen Compiler entfernt oder ausdrücklich als unzulässige Versionsfamilie diagnostiziert. Der versionsbezogene Header ist keine frei akzeptierte Dezimalzahl.

Eine maschinenlesbare Tabelle `language-features.json` oder eine typisierte Java-Tabelle verbindet `LanguageFeature`, erlaubte Versionen, Normreferenz und positive/negative Tests. Sie wird in P1 für implementierte Features eingeführt und in P2 vollständig. Es darf keine unausgefüllte Versionszelle für eine als fertig deklarierte Fähigkeit geben. Neue 2.4-Schlüsselwörter dürfen Namen gültiger 2.3-Modelle nicht unbeabsichtigt sperren.

Die Grammatik muss die Operatorbindung aus dem Referenzhandbuch abbilden. Die im Ausgangsprojekt vorgefundenen Alternativen und Präzedenzstufen werden geprüft; allein die erfolgreiche ANTLR-Generierung gilt nicht als Nachweis. Signed-Number-Lexing muss insbesondere `a-1`, `a - 1`, negative Literale, Exponenten und benachbarte Relationsoperatoren unterscheiden. String- und Kommentarinhalt darf von einer solchen Tokenkorrektur nicht betroffen sein.

Dokumentierte Grammatikwarnungen sind offene Fehler oder begründete Ausnahmen. Die im früheren Spike beobachtete Warnung über eine optional leer passende Alternative in `viewDef` muss vor Abschluss der entsprechenden P2-Fähigkeit aufgelöst oder mit nachvollziehbarer Begründung und Regressionstest akzeptiert werden. Warnungen dürfen nicht nur durch Buildoptionen ausgeblendet werden.

## 6. Fragmentanalyse und Identität

`parseFragment` arbeitet auf einem Bereich einer bereits existierenden `SourceText`. Es darf intern eine Textsicht verwenden, muss die Ergebnisbereiche aber auf die Originalquelle abbilden. Eine selbst erzeugte Wrapperklasse oder ein Wrappermodell darf keine sichtbaren Benutzerpositionen vortäuschen. Falls Wrapper zur Wiederverwendung von Parserregeln nötig sind, erhalten sie synthetische Herkunft, und die Offsettransformation wird explizit getestet.

`FragmentSnapshot` enthält Fragmenttyp, vollständigen Originalbereich, eigenes Wurzelobjekt, Diagnostik, `consumedEntireFragment()` und die verwendete Sprachversion. Es ist kein eigenständiger Modellcompile. Semantische Fragmentanalyse erfolgt mit einem `AnalysisSnapshot` und Scope aus [06](06-kompilierung-und-semantik.md).

Knotenidentitäten sind innerhalb eines Snapshots eindeutig. Der Compiler darf sie aus einer monotonen, **laufbezogenen** Sequenz vergeben, sofern der Snapshot selbst eine eindeutige Identität trägt; verboten ist ein globaler ungeschützter Prozesszähler. Für Workspace-Wiederverwendung liefert [06](06-kompilierung-und-semantik.md) einen `AnalysisReuse`-Kontext. Unveränderte eindeutig passende Deklarationsanker können ihre Identität behalten. Bei Ambiguität wird eine neue Identität vergeben. Korrektheit ist wichtiger als maximale Layoutstabilität.

## 7. Verbindliche Beispiele und Abnahmetests

Die vollständigen Modellfixtures liegen unter der in [03](03-build-und-repository.md) vereinbarten Teststruktur. Ausschnitte in diesem Dokument sind Erläuterungen, keine Behauptung, dass allein der Ausschnitt eine gültige Modelldatei ist.

### 7.1 Unvollständiges Attribut

```ili
CLASS Building =
  height :
END Building;
CLASS Owner =
  name : TEXT*100;
END Owner;
```

Erwartung: `Building` und `Owner` werden als getrennte Deklarationen erkannt. `height` hat einen fehlenden Typknoten mit passender Bereichs- und Ursacheninformation. Das Ende von `Building` darf nicht als Typname für `height` gebunden werden. `Owner.name` bleibt für die Analyse verfügbar. Der betroffene Modellcompile ist abgelehnt. Eine eventuell unterdrückte Folgefehlermeldung ändert nichts an diesem Ergebnis.

### 7.2 Nachlaufender Inhalt und lexikalischer Fehler

Ein vollständig gültiges Fixture plus `GARBAGE` nach dem abschliessenden Modellpunkt muss `ILI-SYN-0004` erzeugen. Ein unzulässiges Zeichen innerhalb des Fixtures muss `ILI-SYN-0001` erzeugen, auch wenn ANTLR das Zeichen verwirft. Der Sammelvertrag muss beide Kategorien unterscheiden können. `STRICT` und `EDITOR` dürfen bekannte Modellinformationen behalten, aber beide blockieren das Compile-Gate.

### 7.3 Positionskonstanz

Ein Fixture enthält vor einer gezielt fehlerhaften Deklaration den Kommentar `!! Grüezi 😀` sowie CRLF-Zeilenumbrüche. Die Diagnose zeigt im Java-Aufruf, in LSP-UTF-16 und im Wasm-JSON auf dieselbe ursprüngliche Zeichenfolge. Zusätzlich werden ein abschliessender Zeilenumbruch, eine leere letzte Zeile, ein Fehler unmittelbar am EOF und eine unvollständige Zeichenkette geprüft.

### 7.4 Testinventar auf Methodenebene

| Test | Wesentliche Behauptung |
|---|---|
| `SourceTextTest.sliceRejectsForeignRevision` | Keine zufällig passenden Bereiche aus anderer Revision. |
| `LineMapTest.crlfAndSupplementaryCharacters` | Richtige UTF-16-Positionen und definierter Roundtrip. |
| `AntlrOffsetMapTest.inclusiveStopBecomesExclusiveEnd` | Kein Off-by-one an BMP-, Supplementary- und EOF-Grenzen. |
| `SyntaxSnapshotTest.reconstructsExactInput` | Kommentare/Whitespace, ungültige Eingaben und Unicode bleiben erhalten. |
| `SyntaxServiceTest.rejectsTrailingTokens` | Der volle Inputvertrag ist unabhängig vom ANTLR-Startzustand. |
| `SyntaxServiceTest.collectsLexerAndParserDiagnostics` | Beide Listener aktiv; keine unerwartete stderr-Ausgabe. |
| `LanguageProfileTest.keywordCompatibility23And24` | Jedes versionsspezifische Wort besitzt positive und negative Fixtures. |
| `EditorRecoveryTest.missingAttributeTypeKeepsFollowingClass` | Keine Kaskade, die den restlichen Modellaufbau unbrauchbar macht. |
| `SyntaxCoverageTest.unmappedProductionCannotDisappear` | Eine noch nicht implementierte Baumvariante wird sichtbar blockiert. |
| `SyntaxBudgetTest.recoveryAlwaysMakesProgress` | Keine Endlosschleife bei verketteten Fehlstellen. |
| `FragmentParserTest.retainsOriginalSourceCoordinates` | Wrapper oder Ausschnitte verändern sichtbare Herkunft nicht. |
| `SyntaxIdentityTest.duplicateDeclarationsAreNotMerged` | Identitätswiederverwendung vereinigt keine gleichnamigen Deklarationen. |

Die Syntaxschicht ist erst abgeschlossen, wenn alle deklarierten Features diese Nachweise besitzen. Eine erfolgreiche Parsergenerierung, eine erfolgreiche Ausführung auf Wasm oder ein Test mit nur einem fehlerfreien Modell erfüllt diese Abnahme nicht.
