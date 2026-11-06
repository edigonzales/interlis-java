# Klassen- und Methodenentwurf

## 1. Leitlinie

Die folgenden Namen sind ein präziser Zielentwurf, keine Behauptung bereits vorhandener Klassen. Kleine, eng zusammengehörige Hilfsrecords dürfen zusammengelegt werden; Verantwortlichkeiten, Verträge und Tests dürfen nicht verschwinden. Keine Klassen pro einzelner Whitespace-Regel, kein ServiceLoader-Pluginframework, keine neue Dependency.

Öffentliche API bleibt unter `guru.interlis.format`. Implementierungsdetails vorzugsweise unter `guru.interlis.format.internal` ohne JPMS-Export. Vorhandene `SyntaxServices`, `SourceText`, `SyntaxSnapshot`, SourceRanges, Cancellation und Diagnoseinfrastruktur verwenden. Architekturcode ist English; Javadocs erläutern Offsets, Ownership, Nullfreiheit und Fehlerverhalten.

## 2. DefaultModelFormatter als Orchestrator

```java
FormatOutcome format(SourceText source, FormatOptions options, CancellationToken cancellation)
```

Ablauf: Argumente prüfen → Cancellation → vollständig strikt parsen → Coverage und Syntaxdiagnosen prüfen → Quell-/Layout-/Triviaverzeichnis erstellen → Layoutplan → Ausgabe → Cancellation → Ausgabe strikt reparsen → Quelltreue prüfen → Ergebnis.

Keine Filesystem-/Netz-/Localezugriffe, kein Cache vergangener fremder Requests, kein Compiler- oder Repositorylauf. Pro Aufruf isolierter Zustand. Reine unveränderliche Defaultregeln dürfen geteilt sein. Thread-safe durch Zustandsisolation, nicht durch globalen Lock.

Ungültige Quelle: `Rejected` mit originalen Syntaxdiagnosen. Cancellation: bestehende `Cancelled`-Variante. Interne Verletzung der Quelltreue: kein Ergebnis und keine stillschweigende Rückgabe des Originals. Als interner Formatterfehler über die bestehenden Hostfehlerpfade behandeln, etwa interne `FormatterInvariantException extends IllegalStateException`; nicht als fachlichen INTERLIS-Quellfehler verkleiden. Hosttests unterscheiden diesen Fall von gewöhnlichem `Rejected`.

Eine unterstützte und parsebare Syntaxfamilie darf nicht aufgrund fehlenden Formattercodes pauschal zurückgewiesen werden. Ein unerwarteter Parser-/AST-Abdeckungsfehler wird in F0 oder den Tests sichtbar gemacht und gezielt behoben, nicht per Catch-all ignoriert.

## 3. FormatSourceIndex

Zweck: Eine gemeinsame Originalsicht mit Tokenordinalen und UTF-16-Offets; mehrfache Volltextscans vermeiden.

Vorgesehene Operationen:

```java
static FormatSourceIndex create(SourceText source, SyntaxSnapshot syntax, CancellationToken token)
List<SourceAtom> significantAtoms()
List<TriviaSpan> triviaBetween(int leftOrdinal, int rightOrdinal)
String original(SourceRange range)
TokenSlice slice(SourceRange range)
int lineAt(int utf16Offset)
String lineEndingAt(int lineIndex)
OptionalInt ordinalAtOrAfter(int utf16Offset)
```

`SourceAtom` enthält ordinal, TokenKind, exakten Text, originalen SourceRange. EOF, gewöhnliche Trivia und Sprach-/Werttokens sauber unterscheiden. Metaattribute sind trotz lexikalischem Kommentarstatus eigene werttragende Einheiten. Ein Ordinal meint die Position im unveränderten Eingabetokenstrom, keine instabile AST-Node-ID.

Ranges müssen zur SourceId und SourceRevision passen, in Grenzen liegen und keine unmöglichen Überlappungen enthalten. Alle Absatz-, Token- und Kommentaransichten verweisen auf dieselbe Quelle. Keine UTF-8-Bytes mit Java-UTF-16 verwechseln. CRLF ist ein Zeilenende, kein leeres zusätzliches Dokumentsegment.

## 4. SyntaxLayoutIndex

Zweck: Vollständige Layoutstruktur ohne zweite Fachsemantik. Zunächst vorhandene typisierte Syntax verwenden. Konstrukte, deren Syntaxdarstellung nicht alle Klauseln differenziert, erhalten einen kleinen internen strukturellen Adapter.

```java
static SyntaxLayoutIndex create(SyntaxSnapshot syntax, FormatSourceIndex source, CancellationToken token)
LayoutRegion root()
List<LayoutRegion> childrenInSourceOrder(LayoutRegion region)
ConstraintLayoutInput constraint(ConstraintDeclarationSyntax node)
HeaderLayoutInput header(LayoutRegion region)
OperatorSpan operatorSpan(ExpressionSyntax expression)
void verifyCoverage()
```

`LayoutRegion` ist ein Quellenintervall mit Rolle, Kindern in Originalreihenfolge und Level-/Separatorregeln. `ConstraintLayoutInput` enthält die Familienkennung, Originalkopf, vorhandenen Namen, Klauseln, Key-/Path-/Expressionranges, Originalseparatoren und Schlusssemikolon. Keine aufgelösten Modelltypen nötig.

**Wichtige Inspektion:** Wo liegt die UNIQUE-WHERE-Expression im aktuellen Syntaxbuilder? Die Antwort in BASELINE dokumentieren und die passende bestehende Range übernehmen. Falls nötig Layoutdaten intern beim existierenden Parse-/Syntaxaufbau erfassen. Keine public ANTLR-Typen in SDK-Signaturen und kein SyntaxAPI-Feld mit falscher Bedeutung füllen. Eine zusätzliche gezielte syntaktische Ableitung ist erlaubt, eine unabhängige zweite Sprachgrammatik nicht.

`verifyCoverage()` meldet jede nicht vertretene fachliche Tokenrange. Nicht nur MODEL/TOPIC/CLASS berücksichtigen. Blocks, Abschnitte, namenlose ENDs, Funktionsdefinitionen, Enumerationen und gemischte Bereiche abdecken. Reine generische Tokenregionen sind für unveränderte seltene Sprachformen zulässig, solange ihre strukturrelevanten Grenzen tatsächlich bekannt und die Zielregeln nicht umgangen sind.

## 5. TriviaBindingIndex

Zweck: Kommentare und Metadaten bleiben an ihrer ursprünglichen syntaktischen Grenze, auch wenn sich Zeilen ändern.

```java
static TriviaBindingIndex create(FormatSourceIndex source, SyntaxSnapshot syntax, CancellationToken token)
TriviaBundle leading(LayoutRegion declaration)
List<BoundTrivia> between(int leftOrdinal, int rightOrdinal)
List<BoundTrivia> trailing(int ordinal)
MetadataFingerprint fingerprint()
```

`BoundTrivia` erfasst Originalrange, Kommentarart, linkes/rechtes Tokenordinal, ggf. Bindung an die folgende Deklaration, Inline/Standalone/Trailing-Klassifikation und geschützten Wertstatus. Keinen neuen Bindungsalgorithmus erfinden, wo `SourceMetadata` bereits verbindliche Zuordnung liefert.

Der Fingerprint enthält Meta-Schlüssel/-Werte/-Reihenfolge inklusive Duplikate und Bindungsanker. Bei Dokumentation zusätzlich Originaltext plus die nach STYLE_GUIDE erlaubte starre Randtransformation. `documentation.text` kann durch Einrückung beeinflusst sein; nur den nachweislich verschobenen gemeinsamen Rand normalisieren, nicht beliebige Zeilen trimmen. Erklärungstokens bleiben exakt. Mehrzeilige Metawerte sind vollständig geschützt.

## 6. Kleine Layoutrepräsentation

Ein begrenzter immutable `LayoutDoc`-Baum genügt. Beispielsweise:

```java
sealed interface LayoutDoc { /* Atom, Trivia, Concat, Break, Nest, Group, Verbatim */ }
record Atom(int ordinal) implements LayoutDoc {}
record Concat(List<LayoutDoc> parts) implements LayoutDoc {}
record Break(BreakKind kind, BreakOrigin origin) implements LayoutDoc {}
record Nest(int levels, LayoutDoc body) implements LayoutDoc {}
record Group(LayoutDoc flatCompatibleBody) implements LayoutDoc {}
record Verbatim(SourceRange range, PreservationReason reason) implements LayoutDoc {}
```

`BreakKind` unterscheidet feste Space-, weiche Space/Newline-, weiche Empty/Newline-, harte Newline- und strukturelle Blankline-Grenzen. Line-Suffix-Trivia muss eine harte nächste Zeilengrenze erzwingen. `BreakOrigin` trägt die Zuordnung zu einer ursprünglichen Tokengrenze für PRESERVE.

Die konkreten Recordnamen dürfen angepasst werden; nötig sind echte Tokenreferenzen und eine explizite EOL-Herkunft. Ein beliebiges `Text(String)` darf nicht zur Hintertür für neue Sprachzeichen werden. Generierte Strings sind auf validierten Whitespace beschränkt; Kommentarausgaben kommen aus dem kontrollierten CommentLayout mit Transformationsbeleg. Kein öffentliches allgemeines DocSDK.

Flat-Messung darf Hardlines nicht ignorieren. Verbatim mit internen Newlines ist nicht flat. Bei Gruppenbruch gelten die Familien-/Ausdrucksregeln, nicht ein beliebiger globaler Charcounter. Ein Schliessen von Klammern steht bei gebrochener Gruppe auf der Öffnerebene. Renderer stellt keine neue Syntax her.

## 7. DeclarationLayout und WhitespacePolicy

```java
LayoutDoc DeclarationLayout.format(LayoutRegion region, FormatContext context)
LayoutDoc DeclarationLayout.formatSequence(List<LayoutRegion> siblings, FormatContext context)
Separator DeclarationSpacing.between(LayoutRegion left, LayoutRegion right, TriviaBindingIndex trivia)
SpaceDecision WhitespacePolicy.between(SourceAtom left, SourceAtom right, SyntacticRole role)
```

`FormatContext` enthält nur unveränderliche Optionen, Indentebene, Indizes und den Requesttoken; keine Hostservices. `formatSequence` setzt Trennzeilen vor das gesamte führende Bündel. `DeclarationSpacing` kann eine kleine Hilfsklasse oder Policy innerhalb von DeclarationLayout sein.

WhitespacePolicy unterscheidet Textgrösse vs Multiplikation, Sprachenoperatoren vs Einzelzeichen, signierte Zahl vs unärer Operator, Range vs Kardinalität, Attribut-/Constraint-/Pfaddoppelpunkt. Sicherheit vor Stil: eine Space-Entscheidung muss nach Relexing dieselbe Tokensequenz ergeben. Eine Lexingabweichung ist kein Anlass für pauschales Entfernen der Vergleichsprüfung.

## 8. CommentLayout

```java
CommentLayoutResult format(BoundTrivia trivia, int targetColumn, FormatContext context)
Optional<IndentShift> safeShift(BoundTrivia block, int targetColumn)
boolean forcesLineBreak(BoundTrivia trivia)
```

`CommentLayoutResult` enthält Doc, ggf. Notice und erlaubte `IndentShift`-Transformation. `IndentShift` benennt Originalrange, delta in Spaces, betroffene Nichtleerzeilen und originalen Randnachweis. Reviewer/Testoracle kann diese Operation unabhängig nachvollziehen.

Standalone-Regelfall muss echte Reindentierung erhalten. Inline-Kommentare bleiben in ihrem Tokenintervall. Für komplexe Originalregionen genaue Verbatimrange und Grund. Keine Schutzregion pauschal auf sämtliche Zeilen eines Dokuments ausdehnen. Kommentarnesting kommt vom Lexer.

Notices sind strukturiert mit Originalbereichen, werden einmal pro logischer Einheit erzeugt und nach Quellposition/Code stabil sortiert. Meldungstext enthält keine unkontrollierten Terminalsteuerzeichen.

## 9. ConstraintLayout und ExpressionLayout

```java
LayoutDoc ConstraintLayout.format(ConstraintLayoutInput input, FormatContext context)
LayoutDoc formatMandatory(...)
LayoutDoc formatPlausibility(...)
LayoutDoc formatExistence(...)
LayoutDoc formatUnique(...)
LayoutDoc formatSet(...)
LayoutDoc ExpressionLayout.format(ExpressionSyntax expression, ExpressionContext context)
LayoutDoc formatBinary(BinaryExpressionSyntax expression, ExpressionContext context)
LayoutDoc formatParenthesized(ParenthesizedExpressionSyntax expression, ExpressionContext context)
LayoutDoc formatCall(FunctionCallExpressionSyntax expression, ExpressionContext context)
```

Signaturen mit `...` bezeichnen interne Designplätze, keine fertig kompilierbaren Quellen. `ExpressionContext` trägt Elternrolle, Indent, Tokenbereich und Kommentaranker. Die Typen verwenden vorhandene AST-Klassen; kein semantisches Nachberechnen eines Operators.

Bei einem binären Ausdruck Quellreihenfolge left → Originaloperatorspanne → right erhalten. Gleichartige Ketten dürfen für effizientes Layout iterativ durchlaufen werden, aber weder AST-Präzedenz noch Originalklammern verlieren. EXPLICIT Parenthesized immer emitten. Calls verwenden Originalargumentreihenfolge/-separatoren; Syntaxfaktoren ohne gewöhnlichen Call behalten ihre spezifischen Quellenknoten.

`formatUnique` behandelt BASKET, tatsächlichen Namen, WHERE, global/local getrennt. `formatSet` erhält beide legalen Kopfvarianten. `formatExistence` darf alternatives OR nicht wie logische freie Assoziation behandeln. Spezifische Regeln stehen in CONSTRAINT_LAYOUT.

## 10. LayoutPrinter und LineEndingPolicy

```java
PrintedDocument print(LayoutDoc document, FormatSourceIndex source, FormatOptions options, CancellationToken token)
boolean fits(LayoutDoc group, int remainingColumns, LookaheadBudget budget)
String LineEndingPolicy.forBreak(BreakOrigin origin)
```

Iterativer Stack, begrenzter Lookahead, Flatbreiten mit frühzeitigem Abschneiden oberhalb der relevanten Breite; keine wiederholten kompletten Suffixscans pro Atom. Codepoints messen, UTF-16-Ranges erhalten. Ausgabe neben Text mit einem internen Emissionsnachweis versehen: jedes Atomordinal exakt einmal, jede Triviaeinheit einmal, bekannte geschützte Regionen und ausschliesslich erlaubte generierte Lücken.

Behalte nicht unnötig mehrere vollständige AST-/Stringschnappschüsse je Zwischenzeile. Performance gegen den bisherigen Parseraufwand messen; keine fragile Millisekundengrenze als universelle Zusage. Deterministische Operationszähler oder grössenbezogene Regressionen sind für Algorithmusfragen besser als zufällige absolute CI-Zeiten.

EOF-Behandlung ausserhalb der letzten geschützten Spanne. Keine pauschale `trim`-Operation. PRESERVE auch bei neuen Umbrüchen nach STYLE_GUIDE; atominterne EOLs separat. Cancellation vor und nach Rendering und in längeren Schleifen.

## 11. FormatVerifier

```java
VerificationResult verify(FormatSourceIndex before, SyntaxSnapshot after,
                          PrintedDocument printed, FormatOptions options,
                          CancellationToken token)
void verifyAtoms(...)
void verifyTriviaAndMetadata(...)
void verifyOwnership(...)
```

Verifiziere wirklichen Reparse, Tokenarten/-lexeme/-reihenfolge, Kommentar-/Metawertanker und Emissionsbesitz. Instabile IDs/Offsets durch Tokenordinal-/Strukturpfadbezug ersetzen. Ein reiner SHA-Vergleich von zwei selbst erzeugten frei veränderlichen Manifesten ist kein Nachweis; tatsächliche Vorher-/Nachherwerte prüfen.

`VerificationResult` und detaillierte `FormatViolation` bleiben intern. `Formatted` nur bei vollständigem Erfolg. Bei unveränderter Ausgabe keine Notice erfinden. Produktion muss nicht zusätzlich eine zweite vollständige Formatierung durchführen; Idempotenz liegt in Testabnahme. Echte Semantikkompilierung bleibt im Testwerkzeug, nicht im Formatter.

## 12. Test- und Korpuswerkzeuge

Im Compiler-Testbaum fachliche Tests pro Vertragsbereich, z.B.:

- `FormatterCommentLayoutTest`, `FormatterDeclarationSpacingTest`, `FormatterLexicalSafetyTest`;
- `FormatterConstraintFamiliesTest`, `FormatterExpressionLayoutTest`, `FormatterMetadataBindingTest`;
- `FormatterIdempotenceTest`, `FormatterCancellationTest`, `FormatterOptionsCompatibilityTest`.

Namen können an lokale Konventionen angepasst werden, aber keine neue SourceSet-Erfindung. Bestehende `FormatterContractTest`/`FormatterPolicyContractTest` methodengenau migrieren oder vereinigen; relevante Fälle nicht verlieren.

Ein kleines explizites `FormatterCorpusCheck` unter `interlis-verification` ist zulässig, wenn es die vollständigen Korpusdateien und Diffs ausführt. Vorgesehene Verantwortlichkeiten:

```java
CorpusRun run(CorpusManifest manifest, Path output, FormatOptions options, CancellationToken token)
CorpusCaseResult checkCase(CorpusCase entry, FormatOptions options, CancellationToken token)
void writeReport(CorpusRun run, Path output)
```

Es lädt nur gepinnte lokale Referenzbytes, schreibt nur in den ausgewählten Ausgabebereich, führt Formatier-/Reparse-/Idempotenzkontrollen aus und erzeugt diffbare Outputs. Kein Netzwerk im Testlauf und kein `--accept-all`-Schalter, der Goldens aus Istwerten macht. Herkunftssammlung erfolgt explizit in F0. Named Consumerprobe gegen den Produktmodulpfad als kleiner vorhandener Testadapter, nicht als neues veröffentlichtes Produkt.

## 13. Entfernbarkeit und Altlasten

Nach Erreichen der Gates alte zeilenweise Schutzlogik und deren nicht mehr genutzte Helfer entfernen. Keine `DefaultModelFormatterV2`, `legacyMode`, parallelen ASTs oder gedoppelten Testhilfsbibliotheken stehen lassen. Allgemeine Syntax-/Diagnoseklassen nicht nebenbei refaktorieren. Bei Umbenennungen aktiven Test-/Contractselektoren, Reports, Javadocs und Methodenreferenzen folgen.

Eine kleine öffentliche APIerweiterung benötigt keine neuen exports/opens. Produktionsmodulpfad darf keine Testpatches übernehmen. Jenesis-Pins und Buildengine unverändert. Ein neuer Fixturepfad darf präzise in bestehende Testinputfingerprints aufgenommen werden; keine pauschale Abhängigkeit von jedem historischen Dokument.
