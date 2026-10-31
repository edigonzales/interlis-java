# 04 – Quelltext, Syntax und Parser

L4-1 bindet die bereits konfigurierbaren Grenzen `maxParseRuleDepth`,
`maxSyntaxNodes` und `maxRecoveryEvents` an die tatsächliche Parserausführung:
aktive ANTLR-Regeltiefe, erzeugte eigene Syntaxknoten beziehungsweise gemeldete
Parser-Recovery-Ereignisse. Der Guard gehört ausschliesslich zum internen
ANTLR-Adapter. Eine Grenzüberschreitung bleibt Ressourcenabbruch und beweist
keine Sprachungültigkeit. Ganze Syntaxsnapshots tragen die Budgetdiagnose und
`budgetExceeded`; die Compilerpipeline liefert `Cancelled/RESOURCE_LIMIT`.
Fragmente verwenden die Defaultgrenzen und liefern eine sichtbare
`ILI-INF-0002`-Diagnose ohne erfolgreich konsumiertes Fragment. Expliziter
Nutzerabbruch wird weitergereicht, unerwartete Parserdefekte werden nicht als
gewöhnliche Syntaxfehler verschluckt. Abnahmen: `LifecycleBoundaryContractTest`
mit tiefer gültiger Quelle, wiederholten fehlerhaften Quellen, allen drei
Grenzen, Fragment und erfolgreichem Folgelauf.

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

Frühe Grenzen vor einem konsistenten Snapshotbau, insbesondere Quelllänge und
Tokenanzahl, verlassen den öffentlichen Syntaxdienst als
`OperationCancelledException(RESOURCE_LIMIT)`, niemals als interner Budgetfehler.
Ein Workspace-Capture übernimmt weder solche Abbrüche noch einen Snapshot mit
`budgetExceeded` in Resolver- oder Wiederverwendungsdaten; er meldet ebenfalls
`RESOURCE_LIMIT`, bevor eine fehlende Modellbindung daraus abgeleitet wird.

Leere Funktionsparameter- und Aufrufargumentlisten sind ein explizites
Sprachprofilmerkmal: 2.3 verlangt mindestens einen Eintrag, 2.4 erlaubt die leere
Liste (2.3 §2.13/2.14, 2.4 §3.13/3.14). STRICT, EDITOR und Ausdrucksfragmente
verwenden denselben Vertrag. Eine versionswidrige leere Liste liefert
`ILI-SYN-0006` am originalen Klammerbereich; erkannte Signaturen, Aufrufe und
folgende Deklarationen bleiben für Queries erhalten. Fehlende oder durch
Recovery eingesetzte Klammern dürfen keinen internen Fehler auslösen.
Die fachliche Anzahl-/Typprüfung der Argumente bleibt zusätzlich erforderlich.

### 4.2 `SyntaxSnapshot`

`public final class SyntaxSnapshot` enthält:

| Methode | Ergebnis und Zusicherung |
|---|---|
| `SourceText source()` | Originaltext und Revision; kein temporärer Ersatztext. |
| `ParseMode mode()` | Verwendete Recoverystrategie, kein Gültigkeitsurteil. |
| `ParseOptions options()` | Unveränderliche tatsächlich verwendete Optionen einschliesslich erwarteter Version und Arbeitsbudgets. |
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

Seit Vorab-API 0.2 nimmt der `SyntaxSnapshot`-Konstruktor `ParseOptions` anstelle
eines einzelnen `ParseMode` entgegen. `mode()` ist die Sicht auf `options().mode()`.
Wiederverwendung verlangt neben Quelle, Revision und Text dieselben Parseoptionen;
ein früherer Lauf mit grösserem Budget darf ein später verlangtes Limit nicht umgehen.

### 4.3 Eigene Knoten, Tokens und Trivia

Die geschlossene Syntaxhierarchie verwendet `sealed interface SyntaxNode`. Jeder Knoten besitzt `SyntaxNodeId id()`, `SyntaxKind kind()`, `SourceRange range()` und eine unveränderliche geordnete Kindliste. Ein fehlender Knoten besitzt einen Nullbreitenbereich, eine erwartete Syntaxkategorie und die Diagnose-ID. Übersprungener Quelltext wird über `SkippedSyntax` mit originalem Bereich und Ursache repräsentiert. `RecoveredSyntax` enthält sowohl bekannte Teilknoten als auch den Recoverygrund.

Die Wurzel ist `DocumentSyntax`; darunter mindestens `HeaderSyntax`, `ModelSyntax`, `TopicSyntax`, `DeclarationSyntax`, `TypeSyntax`, `ExpressionSyntax`, `NameSyntax` und die expliziten Fehlerkategorien. `DeclarationSyntax`, `TypeSyntax` und `ExpressionSyntax` sind jeweils geschlossene Unterhierarchien. Ihre vollständige Variante folgt dem Fachkatalog aus [05](05-metamodell-und-typensystem.md); eine generische Liste beliebiger Properties ersetzt diese Varianten nicht.

`ObjectArgumentTypeSyntax(id, range, base, restrictions, objects)` bewahrt
`OBJECT OF` (`objects=false`) und `OBJECTS OF` (`objects=true`) aus einer
Funktionssignatur. `base` ist ein optionaler
`NameSyntax` (leer bei `ANYCLASS`), `restrictions` eine unveränderliche Liste von
`NameSyntax` in Quellreihenfolge. Beide sind nullfrei. Die Namen bleiben Kinder
mit eigenen revisionsgebundenen UTF-16-Bereichen für Bindung und Navigation;
die Syntax enthält keine aufgelösten Symbolidentitäten.

`ObjectSetExpressionSyntax(id, range, selection)` erhält `ALL` als eigene
Ausdrucksvariante (`EXPRESSION_OBJECT_SET`). `selection` ist ein nullfreies
`Optional<ObjectArgumentTypeSyntax>`; leer bedeutet Kontextauswahl. Explizite
Basen und Einschränkungen bleiben geordnete Syntaxkinder mit Originalbereichen.
`PathExpressionSyntax.segments()` enthält immutable `Segment(name,
associationAccess, associationQualifier, index, range)`-Werte. Das Operatorflag erhält `\`, der Bereich des
`NameSyntax` umfasst nur den Namen für Navigation/Rename, der Segmentbereich
auch Operator und Klammerqualifikation. `associationQualifier` ist ein nullfreies
`Optional<NameSyntax>` für `rolle[Assoziation]` mit eigenem Navigationsbereich.
Kinder bleiben Rollenname, danach gegebenenfalls Qualifikator, in Pfadreihenfolge.
Die Qualifikation bezeichnet eine Ausgangsrolle und darf nicht als Listenindex
interpretiert werden. Alle Werte sind nullfrei, Bereiche an Quelle und Revision
gebunden. `index` ist ein nullfreies `Optional<PathIndex>` für FIRST, LAST oder
exakte numerische Positionen. Indizes sind keine Namensreferenzen und können
nicht gleichzeitig Assoziationsqualifikatoren sein. Nullindizes bleiben in der
Syntax darstellbar und werden bei der Bindung als Sprachfehler abgewiesen.
Index-/Inspektionsschritte dürfen nicht still verschwinden.
`ConstraintDeclarationSyntax.uniqueKeys()` enthält geordnete `ExpressionSyntax`-
Pfade statt blosser Namen; nicht darstellbare Operatoren bleiben explizite
`UnsupportedExpressionSyntax`. `localPrefix()` enthält optional den strukturierten
LOCAL-Präfix, `basket()` den syntaktischen Geltungsbereich. LOCAL und globale
Schlüssel sind alternative Formen. WHERE bleibt in `expression()`, der
Constraintname in `name()`. `ExistenceConstraintSyntax.source()` und die
Zielpfade sind ebenfalls Ausdruckssyntax; jede Zielalternative erhält zusätzlich
ihren eigenen `NameSyntax`-Viewablebezug. Syntaxkinder umfassen sämtliche Pfade,
Qualifikatoren und Zielreferenzen in Quellreihenfolge. Die Werte sind nullfrei
und immutable; Namens- und Pfadbereiche beziehen sich auf dieselbe Quellrevision.
UNIQUE-BASKET sowie benannte UNIQUE-/EXISTENCE-Constraints sind 2.4-Syntax.

`EnumerationTypeSyntax.finalSubtrees()` hält geschlossene Geschwisterlisten
als Elternpfade; der leere Pfad bezeichnet die Wurzel. `EnumerationValueSyntax`
unterscheidet mit `qualified` einen geschriebenen Punktnamen von der durch
Verschachtelung entstandenen Pfadform. Die Syntax enthält nur deklarierte
Werte, das Modell zusätzlich geerbte Werte.

`FormattedTypeSyntax` erhält `base`, `basedOnStructure`, `inheritance`,
geordnete `parts` und optionale lexikalische Grenzen. Ein `Part` ist entweder
ein Literal oder eine Attributreferenz mit optionaler exakter Stellenzahl
beziehungsweise Formatdomäne. Basis-, Attribut- und Formatnamen bleiben
`NameSyntax`-Kinder mit eigenen UTF-16-Bereichen. Grenzen ohne explizite Basis
beziehen sich auf den geerbten Formattyp. `OidTypeSyntax.valueType()` enthält
den optionalen strukturierten numerischen beziehungsweise textlichen
Trägertyp; OID ANY hat keinen konkreten Träger. Unqualifiziertes `UUIDOID`
bleibt eine aufzulösende Domänenreferenz, kein eingebauter Typalias.
Alle Werte sind nullfrei und unveränderlich; Bereiche gehören zur Quellrevision.
Collection-Elemente bewahren auch skalare Syntax, damit die Bindung deren
Zulässigkeit nach Sprachversion prüfen kann.

`ReferenceTypeSyntax(id, range, target, restrictions, external)` erhält den
Primärnamen (einschliesslich ANYCLASS) und alle Einschränkungsnamen in
Quellreihenfolge. `children()` liefert den Primärnamen vor den Restriktionen;
auch in Collection-Elementen bleiben deren eigene UTF-16-Bereiche erhalten.
Listen sind immutable und nullfrei. Semantische Zielauflösung erfolgt erst
im Compiler, nicht durch erneute Auswertung des Quelltexts.

`OidRuleSyntax(basket, domain, range)` erhält eine einzelne deklarierte OID-Regel.
`domain: Optional<NameSyntax>` ist ausschliesslich bei explizitem NO OID leer;
eine Basket-Regel benötigt einen Domänennamen. `TopicSyntax`,
`ClassDeclarationSyntax` und `AssociationDeclarationSyntax` enthalten
`oidRules(): List<OidRuleSyntax>` in Quellreihenfolge statt eines Rohtextstrings.
Nur direkt im jeweiligen Kopf deklarierte Regeln werden erfasst. Die Namen
sind Syntaxkinder vor den Mitgliedern und behalten ihre eigenen UTF-16-Bereiche
für Bindung und Rename. Alle Werte sind nullfrei und immutable.

`AssociationDeclarationSyntax.cardinality()` erhält eine explizite
Assoziationskardinalität als `Optional<Cardinality>`; leer bedeutet bei einer
Wurzel genau eine Beziehungsinstanz je Teilnehmerkombination, bei einer
Erweiterung die geerbte Grenze. Die Rollenmultiplizität bleibt davon
getrennt. Mehrere solche Beziehungsinstanzen verlangen eine OID-Deklaration.

`RoleDeclarationSyntax.targets()` enthält eine nichtleere, unveränderliche Liste
von `RoleDeclarationSyntax.Target(base, restrictions)` in Quellreihenfolge.
Jeder nullfreie Wert bewahrt den Basisnamen (einschliesslich `ANYCLASS`) und
alle Restriktionsnamen mit eigenen Quell-/Revisionsbereichen. Diese Namen sind
Syntaxkinder; `OR`-Alternativen dürfen nicht auf das erste Ziel reduziert werden.
`explicitCardinality` unterscheidet eine geschriebene Grenze von der bei einer
überschriebenen Rolle zu erbenden Kardinalität.

`FunctionDeclarationSyntax` enthält geordnete `FunctionParameterSyntax(name, type)`
mit `TypeSyntax type` und `TypeSyntax returnType`. Diese Typknoten sind Kinder
der Funktionsdeklaration und behalten ihre ursprünglichen UTF-16-Bereiche.
`FunctionCallExpressionSyntax.functionName()` liefert `NameSyntax` mit allen
qualifizierten Segmenten; seine Kindliste enthält zuerst den Namen und danach
die Argumente in Quellreihenfolge. Die Werte sind nullfrei und unveränderlich.

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

### Strukturierte Einheitenkomponenten

`UnitDeclarationSyntax` erhält neben Basisname und Anzeigetext `designation`,
`derivedFrom` und geordnete `Component(operator, unit)`-Werte. `name` und
`nameRange` bezeichnen das operative Kurzzeichen, falls angegeben, sonst den
Deklarationsnamen. Alle Namen sind Syntaxkinder mit exakten halb-offenen
UTF-16-Bereichen. Die Zusammensetzung verwendet gemäss beiden Normprofilen nur
Multiplikation und Division; ein Potenzoperator ist dort kein zulässiger Ersatz.
Die Abstraktions-/Erweiterungsprüfung gehört zur Semantik in Kapitel 05.

### Strukturierte View-Inhalte in Vorab-API 0.2

`ViewDeclarationSyntax` erhält typisierte Formation, geordnete Basen und
Basiserweiterungen, Selektionen, eine geordnete Folge aus `AllOf` und normalen
Attributdeklarationen sowie Constraints. Alias- und Zielnamen besitzen eigene
Bereiche. `AttributeDeclarationSyntax.derivations()` erhält alle Ausdrücke einer
Zuweisung in Quellreihenfolge. `children()` macht die fachlichen Kindknoten
zugänglich. Formation und Body werden nicht erneut aus Quelltextstrings
interpretiert. Der verlustfreie Originaltext bleibt in `SyntaxSnapshot`.

`AssociationDeclarationSyntax.derivedFrom` erhält die strukturierte lokale
Basis; `RoleDeclarationSyntax.derivation` erhält den optionalen Zuweisungsausdruck.
`AGGREGATION ... EQUAL` bewahrt sämtliche geordneten Pfade, auch bei mehreren
Komponenten. Keine dieser Angaben darf aus einem zweiten Quelltextscan
rekonstruiert werden.

`InspectionExpressionSyntax` enthält genau eine benannte Sicht (`view`) oder
Inline-Formation (`formation`) und eine optionale `restriction` für den
nachgestellten OF-Objektpfad. Nur INSPECTION/AREA INSPECTION mit je einer Basis
und einem geordneten Zerlegungspfad sind Inline-Formationen dieses Knotens.
Der Parser MUSS den ganzen Faktor vor einem optionalen OF-Pfad erfassen;
das Ersetzen des Faktors durch den OF-Pfad ist unzulässig. `children()` enthält
die Namen, Formationsteile und Einschränkung in Quellreihenfolge. Alle Werte
sind unveränderlich und nullfrei; Bereiche beziehen sich auf die Quellrevision.
`SyntaxKind.EXPRESSION_INSPECTION` kennzeichnet diese Ausdrucksart.


### Strukturierte Parameter- und Klassenwerte (API 0.2)

`ParameterDeclarationSyntax` bewahrt Name, Namensbereich, MANDATORY und TypeSyntax;
`ParameterExpressionSyntax` den qualifizierten PARAMETER-Namen. Namen und
Restriktionen von `ClassValueTypeSyntax` sind `NameSyntax` mit UTF-16-Quellbereichen.
`AttributePathTypeSyntax` trennt OF-Ausdruck, optionales @Argument und geordnete
Typrestriktionen. `AttributeConstantExpressionSyntax` trennt optionales Viewable
vom Attributnamen. Diese nullfreien, unveränderlichen Syntaxknoten gehören
`interlis-compiler` / `guru.interlis.syntax`; Kinder enthalten die Teilknoten
in fachlicher Reihenfolge. Der Syntaxsnapshot besitzt ihre Quellrevision.
Fehler gehen durch die bestehenden Recovery-/Diagnoseverträge. UNQUALIFIED
wirkt je Importname und wird nicht auf nachfolgende Namen übertragen.


### CONTEXT und DEFERRED, Vorab-API 0.2

`ContextDeclarationSyntax` in `guru.interlis.syntax` bewahrt Namen und
Namensbereich sowie geordnete `Mapping(genericDomain, alternatives)` mit
`NameSyntax`-Quellbereichen. `TopicSyntax.deferredGenerics` enthält ausschliesslich
die direkt deklarierte Liste in Quellreihenfolge. Der Parser trennt Mapping-Zeilen
strukturell. Domainmodifikatoren gelten nur für die jeweilige Deklaration, nicht
für den ganzen DOMAIN-Block. Alle Listen sind defensiv kopiert und nullfrei;
Knotenbereiche bleiben an Quelle, Revision und UTF-16 gebunden. Ungültige
Versionen und Namen folgen den bestehenden Diagnose-/Recoveryverträgen.

### Linienformen und Geometriekomponenten, Vorab-API 0.2

`LineFormDeclarationSyntax` erhält Name, Namensbereich und den Strukturverweis
als `NameSyntax`. `LineTypeSyntax.lineForms` enthält geordnete `NameSyntax`-Werte
auch für STRAIGHTS/ARCS; `lineAttributes` ist ein exakter Strukturverweis.
`directed` und `withoutOverlaps` bleiben getrennt von der optionalen
Toleranzlexeme erhalten. Kinder enthalten Linienformen, Stützpunktdomain und
Linienattributstruktur in dieser Quellreihenfolge. Die nullfreien Werte und
Listen sind unveränderlich; alle Bereiche gehören zur Syntaxrevision.


### K4: Metadaten und numerische Achsen, Vorab-API 0.2

`MetadataBasketDeclarationSyntax` (compiler/syntax) erhält Basketart, FINAL,
Topic- und Basisnamen sowie lokale `ObjectDeclaration`-Kinder in Quellreihenfolge.
Jedes Objekt behält seinen Namenbereich und den Klassennamen der OBJECTS-OF-Gruppe.
`NumericTypeSyntax.referenceSystem()` enthält optional `ReferenceSystem(target,
domain, axis)`: `target` ist `NameSyntax`, `domain` unterscheidet Winkelklammern
von Metaobjektbezügen, `axis` ist ein optionaler einsbasierter Index. Auch
unbegrenztes NUMERIC erhält Einheit, Richtung und Referenzsystem.
`CoordinateTypeSyntax.axes()` enthält vollständige `NumericTypeSyntax`-Kinder
statt vorzeitig vereinfachter Wertebereiche; `rotation()` erhält beide
Achsenindizes. `ParameterDeclarationSyntax.modifiers()` bewahrt die Marker
von Klassen-/Strukturparametern. Alle Listen sind nullfrei und unveränderlich;
Namenbereiche bleiben an die originale Quelle und Revision gebunden. Die
semantische Bindung folgt Kapitel 05, nicht einer erneuten Quelltextsuche.

### K5: Grafiken und Signaturzuweisungen, Vorab-API 0.2

`GraphicDeclarationSyntax` (compiler/syntax) bewahrt Basis und Vererbung getrennt,
Selektionsausdrücke sowie geordnete `Rule`-Kinder und den Endnamen. Jede Regel
enthält eine optionale Signaturklasse und `Clause`-Werte mit optionaler
WHERE-Bedingung. `Assignment` enthält den Parameternamen samt Bereich und eine
geschlossene Variante `ExpressionValue`, `MetaObjectValue` oder `AccordingValue`.
ACCORDING erhält den Selektor als strukturierten Ausdruck und geordnete
Enumzuweisungen mit Anfangs-/Endpfad und Quellbereich. `MetaObjectTypeSyntax`
erhält einen optionalen OF-Klassennamen. Alle Kinder, Listen und Bereiche sind
unveränderlich, nullfrei und an die Quellrevision gebunden. Semantik entsteht
über die Binder in Kapitel 05; kein vollständiger Quelltext wird erneut als
Grafikregel interpretiert.

### K6: Exakte Einheitenfaktoren, Vorab-API 0.2

`UnitDeclarationSyntax` ergänzt `conversionFactors` und `conversionFunction`
vor `definition`. Faktoren werden unmittelbar aus `derivedUnit` übernommen;
`UnitSymbol.ConversionFactor` (model, Kapitel 05) wird als unveränderlicher
Wert auch in der Syntax verwendet. Er enthält Operator, Konstantenart,
optionalen exakten Dezimalwert und den UTF-16-Bereich der Konstante mit
Quellrevision. Der erste Operator ist MULTIPLY, danach MULTIPLY oder DIVIDE.
PI/LNBASE bleiben symbolisch. Vorzeichenbehaftete ganzzahlige Konstanten sind
wie Dezimalzahlen zulässig. Syntax mit `[Einheit]` ohne Faktoren bedeutet
Faktor eins; FUNCTION besitzt stattdessen eine optionale Erklärung inklusive
Originalnotation. Keine Prüfung liest Zahlen erneut aus `definition`.
Alle Listen sind nullfrei und unveränderlich; ungültige Zahlen und fehlende
Ziele folgen den bestehenden Syntax-/Bindungsdiagnosen.

Modellkopfoptionen werden über `ModelSyntax.options(): ModelOptions` erhalten.
NOINCREMENTALTRANSFER, CHARSET und XMLNS sind 2.4-Profilmerkmale; 2.3 erhält
ILI-SYN-0006 am Originaltoken. Zeichenketten werden dekodiert, ihr Original-
Konstruktbereich bleibt über DeclaredOrigin erhalten. Fehlende Angaben bleiben
leer und werden nicht als tatsächlich deklarierte Defaults vorgetäuscht.

E1-Profile (SYN-004/013/015, SEM-005/010/014, META-003/014): DATE,
TIMEOFDAY und DATETIME sind 2.4-Syntax; die XML-Domänen bleiben in beiden
Versionen verfügbar. TYPE MODEL enthält die vorgesehenen Deklarationsfamilien;
SYMBOLOGY benötigt nur in 2.3 CONTRACTED. Dokumentarische Familienempfehlungen
für REFSYSTEM/SYMBOLOGY werden nicht als zusätzliche Quellfehler erzwungen.
Wiederholte Properties liefern ILI-SYN-0007 am zweiten Token; Warnungsablehnung
verwendet das gemeinsame Gate. EXTENDED und explizites EXTENDS am selben
Klassen-/Strukturkopf sind unzulässig. Fehlende Attributtypen nach Parser-Recovery
behalten die ursprüngliche Syntaxursache als ErrorType; sie erzeugen keine
Implementierungssperre. E1ProfileSafetyContractTest und ModelKindContractTest
prüfen diese Regeln; dies ist keine pauschale E1-/G2-Abnahme.

E2 konkretisiert META-003/006/009/014 und SEM-005/010/014 gemäss Kapitel 05/06:
DomainSymbol.mandatory(), gebundene CLASS-Domänen, unverfälschte fehlerhafte
NumericTypeSyntax und QueryResult<UnitRelation> mit NOT_A_UNIT. Verbraucher
verwenden die gemeinsame Typrelation. UnitRelationContractTest und
DomainRestrictionContractTest prüfen Modell, Queries, Fehlursachen und Edits.
Die weiterführende Normabnahme bleibt in g2-rule-matrix.json ausdrücklich offen.

### E3: Gebundene Strukturwerte und Restriktionen

`StructureValueType(id, origin, Optional<SymbolId> target, List<SymbolId> restrictions)`
ersetzt den einzelnen obligatorischen Zielwert in API 0.2. Leeres target bedeutet
ANYSTRUCTURE; ungelöste Namen erzeugen ErrorType. Restriktionen sind unveränderlich,
nullfrei und in Quellreihenfolge gebunden. targetRoots() liefert die eingeschränkten
Ziele beziehungsweise das Primärziel. StructureValueTypeSyntax erhält die Original-
Namen und UTF-16-Bereiche; es gibt keine künstliche universelle Strukturdeklaration.

Ein konkreter lauflokaler StructureBindings-Helfer verwendet den Aufbauzustand des
GraphBuilder. Compiler und TypeRelations.canAssign verwenden dieselbe Relation:
Strukturelemente passen zu ihren erlaubten Wurzeln; im FUNCTION_ARGUMENT-Kontext
passen auch Objekte, deren Klasse die geforderte Struktur erweitert. ANYSTRUCTURE
akzeptiert dort Strukturelemente und Einzelobjekte, keine Objektmengen oder Zahlen.
Normale eingebettete Wertzuweisung behält ihre eigene Objekt-/Strukturgrenze.
BAG kann bei Einschränkung zu LIST werden; die umgekehrte Richtung ist unzulässig.
Kardinalität und Elementrestriktionen werden gemeinsam geprüft.

Pflichtige beliebige Strukturen beziehungsweise Typen ohne verfügbare konkrete
Struktur verlangen ein abstraktes Attribut. Konkrete Unterstrukturen anderer Themen
werden nur über explizite passende Wurzeln berücksichtigt. Konkrete Klassen und
Strukturen dürfen keine effektiven abstrakten Attribute behalten. Normbezug:
2.3 §2.5.3/2.6.4/2.14; 2.4 §3.5.3/3.6.4/3.14. Nicht gemeinsame Mitglieder von
Alternativen sind über einen gemeinsamen Pfad nicht statisch ansprechbar.

Inspektionen, Kontextualisierung, Übersetzungen, generische Verwendungen, Diagramm-
labels und öffentliche Queries erhalten Grundziel und Restriktionen. Vorhandene
Konstruktorverbraucher und Tests werden direkt angepasst. Relevante Vererbungs-
und Aliasketten verwenden besuchte IDs statt stiller Abschneidung bei 16/32/64;
Abbruch und Arbeitsbudget bleiben aktiv. Keine neue Pass-/Plugin-Infrastruktur.

### E1/E4: Vollständiger Dateiverbrauch und typisierte Funktionsnamen

SYN-004/013/015: Der Syntaxdienst prüft den vollständigen Tokenverbrauch bis EOF;
Tokens nach dem letzten Modell erhalten ILI-SYN-0004. Kommentare und Zwischenräume bleiben zulässig.
Die beiden Ungleichheitsformen != und <> bilden beide NEQ ab; jede Relation
bewahrt Operator und UTF-16-Originalbereich. EnumArgumentTypeSyntax stellt
ENUMVAL/ENUMTREEVAL explizit dar. BuiltinFunctionExpressionSyntax erhält den
Funktionsnamen als NameSyntax mit Segmentbereichen statt als String.

### E4: Numerische Konstanten und Fragmentbereiche

SYN-004/013/015 und SRC-002–005: LiteralExpressionSyntax und
ConstantExpressionSyntax erhalten Optional<NameSyntax> unit. Numerische Lexeme
und exakte Dezimalwerte enthalten die Einheit nicht; der Gesamtbereich und das
Einheitenkind behalten die Originalbereiche. PI/LNBASE bleiben symbolisch.
Fragmentdiagnosen und geparste Stringpfade beziehen sich auf originale UTF-16-
Grenzen derselben Quelle/Revision, auch nach Unicode, CRLF und Escapes.
Lexerfehler dürfen beim Fragmentparsen nicht verschwinden.

FunctionDeclarationSyntax erhält returnMandatory als letzten Parameter, der
verschachtelte FunctionParameterSyntax mandatory als letzten Parameter. Beide
Booleanwerte stammen aus dem strukturierten ArgumentType; eine benannte
Domänenpflicht wird erst bei der semantischen Bindung ergänzt.

E1 Wortprofile (SYN-002/004/013): Massgeblich sind die vollständigen Tabellen
reservierter Wörter in 2.3 §2.2.7 und 2.4 §3.2.7. Auch weiterhin reservierte
Legacywörter dürfen keine Namen sein. Neue 2.4-Wörter bleiben in 2.3 zulässige
Namen, sofern sie dort nicht bereits reserviert sind; DATE ist bereits in 2.3
reserviert. XML ist kontextabhängig in BLACKBOX XML, kein allgemein reservierter
Name. Quelle, Fragment und Rename verwenden dasselbe Profil. Ungültige
2.4-Konstrukte in 2.3 bleiben Quellfehler; ihre Kennung kann vom Kontext abhängen.
Namen haben höchstens 255 Zeichen. REFSYS-Literale sind ein 2.4-Konstrukt.

### E5: Quell-Metaattribute und Dokumentation

META-010/014: SyntaxSnapshot.metadata() liefert eine unveränderliche Map von
Original-Tokenbereichen auf SourceMetadata. Dessen geordnete Attribute enthalten
Schlüssel, Textwert und Bereich; Dokumentation enthält geordnete Text- und
Bereichspaare. Alle Bereiche gehören zur Quelle/Revision des Snapshots.
Das Projektprofil übernimmt führende !!@-Kommentare als durch Semikolon
getrennte Schlüssel=Wert-Angaben; Werte sind einfache Tokens oder INTERLIS-
Strings mit Escapes. Ein geöffneter String darf über unmittelbar folgende
`!!@`-Kommentarzeilen fortgesetzt werden; die Fortsetzungsmarker gehören nicht
zum Textwert, physische Zeilenumbrüche und Einrückung bleiben erhalten.
Wert- und Diagnosebereiche beziehen sich weiterhin auf die unveränderte
Originalquelle in UTF-16, einschliesslich überspannter Marker. Ein nicht
geschlossener String bleibt ein Quellfehler; eine folgende Deklaration wird
nicht zur Fortsetzung. Leere Annotationen sind erlaubt; fehlerhafte Zuweisungen
erhalten Quellfehler. Dies ist ein dokumentiertes Kommentarprofil, kein neues
INTERLIS-Schlüsselwort. /** ... */ liefert Dokumentation; übrige Kommentare
bleiben unverändert als Trivia erhalten. Die Zuordnung gilt zum folgenden Token; reine Gruppenintroduktoren DOMAIN, UNIT,
ATTRIBUTE und PARAMETER werden bis zur ersten Deklaration übergangen.
Der Binder ordnet führende Gruppen vor Deklarationsbeginn und innerhalb des
Präfixes bis zum deklarierten Namen zu, einschliesslich Enumwerten.

FunctionDeclarationSyntax erhält Optional<SourceMetadata.Text> explanation als
letzten Parameter. Die native Explanation-Produktion wird einmal strukturiert
übernommen. Ihr Text und Bereich bleiben von Dokumentationskommentaren getrennt.

`ConstraintDeclarationSyntax.plausibility()` bewahrt den exakten Dezimalwert,
den Vergleichsoperator und den ursprünglichen Zahlenbereich der Prozentangabe.
`where()` enthält nur die SET-Vorbedingung; der Hauptausdruck bleibt getrennt.
`basket()` gilt für UNIQUE und SET. SET-Namen und `(BASKET)` gehören zu 2.4;
`SET CONSTRAINT (LOCAL)` ist in keinem Profil gültig. Prozentangaben erlauben
weder PI/LNBASE noch Einheiten. Fehlende Bedingungen aus der Parser-Recovery
sind Quellfehler und keine Implementierungslücken.
Die bestehenden Ziffern-/Exponentenbudgets gelten vor dem Aufbau exakter
Dezimalwerte. Überschreitungen erzeugen eine explizite Budgetdiagnose im Syntax-
resultat und RESOURCE_LIMIT beim Compilerlauf. Fragmentdiagnosen bewahren den
ursprünglichen Quellbezug; ein Grenzfall lässt keine unbehandelte Ausnahme entweichen.

### E2: Skalierte numerische Typgrenzen

SYN-004/013/015: Skalierte Grenzen der gemeinsamen numeric-Produktion verwenden
die Float-Mantisse aus 2.3 §2.2.4 S.24 / §2.8.5 S.41–42 und 2.4 §3.2.4 S.28 /
§3.8.5 S.52. Sie beginnt nach optionalem Vorzeichen mit 0.; die erste
Nachkommastelle ist von null verschieden oder die ganze Mantisse ist null.
0.00e1 bleibt zulässig. Fehler erhalten ILI-SYN-0002/PARSING am ursprünglichen
Literalbereich, auch in Fragmenten. Die Budgetprüfung erfolgt vor exakter
Zahlenauswertung. Die zuvor getrennt offenen Konstanten-/Faktorproduktionen
und das Exponentenverhältnis beider Grenzen sind durch die ausdrücklichen
L1-2-Nutzerentscheidungen in Kapitel 06/15 geklärt: Das bestehende breitere
Profil bleibt erhalten; die strengere Mantissenprüfung bleibt auf numerische
Typgrenzen beschränkt.

### L1-2: CONTINUOUS im Attributfragment

SYN-004/010/013/015 und SRC-002/005: CONTINUOUS ist nur zusammen mit
SUBDIVISION zulässig (2.3 §2.6.1/PDF31, 2.4 §3.6.1/PDF39). Die gemeinsame
Syntaxbaumprüfung meldet fehlendes SUBDIVISION als ILI-SYN-0002/PARSING am
folgenden Namen; die erwartete Tokenliste enthält SUBDIVISION. Die für
Recovery weiter gefasste ANTLR-Produktion bleibt unverändert. Das rekonstruierte
Attribut bewahrt die tatsächlich vorhandenen Marker. Vollquelle in EDITOR und
STRICT sowie ATTRIBUTE_DECLARATION-Fragmente verwenden dieselbe Prüfung und
die Original-UTF-16-Bereiche samt SourceRevision. Vollständiger
Fragmentverbrauch bedeutet keine Fehlerfreiheit; Diagnostik bleibt separat.
