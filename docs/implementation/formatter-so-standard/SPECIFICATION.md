# Spezifikation — gemeinsamer Formatterauftrag F1 und F2

## 1. Produktziel

Ein Benutzer erhält über CLI, LSP und später das GUI dieselbe gut lesbare INTERLIS-Darstellung bei identischen effektiven Optionen. Der Stil respektiert die Solothurner Modellierungspraxis, erhält Dokumentation und Metadaten und strukturiert Constraints nachvollziehbar. Er ist kein Editor, Sprachprüferersatz, Modellgenerator oder Refactoringwerkzeug.

Der Erfolg bemisst sich nicht an möglichst vielen geänderten Zeilen, sondern an festgelegten Layoutregeln, nachgewiesener Quelltreue und reproduzierbarer Formatierung. Gut gepflegte Referenzmodelle sollen ausser begründeten Hausstilentscheidungen wenig Änderungen erhalten. Constraint-Wildwuchs darf sichtbar stärker normalisiert werden.

## 2. Ist-Stand und Konsequenzen

Die gelesene Implementierung `DefaultModelFormatter` ist zeilenorientiert. Sie schützt sämtliche Zeilen mehrzeiliger Kommentare, behält dadurch auch falsche Einrückung bei, erhält Leerzeilen weitgehend und entfernt das Leerzeichen vor `:`. AST-basierte Einrückungsereignisse berücksichtigen nur einen Teil der Blockarten. `FormatOptions` enthält Einrückung, EOL und Schlussnewline. Die öffentliche API ist bereits aus `guru.interlis.compiler` exportiert.

Die Syntax enthält typisierte Ausdrücke und Constraints, aber deren Felder bilden nicht blind jeden Grammatikzweig gleich ab: Die Grammatik erlaubt `UNIQUE ... WHERE`, während `ConstraintDeclarationSyntax.where` explizit SET vorbehält. Die Umsetzung muss die tatsächliche Zuordnung im Syntaxbuilder prüfen; es ist nicht erlaubt, nicht abgebildete Syntax wegzulassen oder wegen einer AST-Lücke als unbekannten Normalfall dauerhaft unverändert zu lassen.

Die LSP-Dokumentformatierung übernimmt Requesttabbreite, die bisherige `source.format`-Code-Action dagegen Defaults. Dieser zweite Formatierweg wird entfernt, statt eine nicht übermittelte Editorpräferenz zu erraten.

## 3. Umfang

### 3.1 Enthalten

F1: sichere Token-/Triviarepräsentation, vollständige Block-/Abschnittseinrückung, Kommentarverschiebung, Metadatenbindung, Hausstilleerzeichen, Deklarationsabstände, sichere EOL- und EOF-Behandlung.

F2: alle fünf Constraint-Familien und ihre zulässigen Varianten; vollständige Ausdruckserhaltung mit strukturierter Breitenentscheidung, Funktionsargumenten, logischen Operatoren, Pfaden und Kommentaren. `CONSTRAINTS OF` gehört schon zum F1-Strukturmodell.

Integration: öffentliche API, additive CLI-Breitenoption, Standard-LSP-Formatierung, gezielte API-/JPMS-/CLI-/LSP-/gegebenenfalls portable Verbrauchertests. Gepinntes Korpus, unabhängige Goldens, Diff-/Sicherheitsberichte, aktive Altlastenbereinigung und autonome Abschlussreview-Schleife.

### 3.2 Nicht enthalten

GUI, Format-Preview-Widgets, LSP-Range-/On-Type-Neuentwicklung, globale Format-on-Save-Aktivierung, .editorconfig-/Projektkonfigurationsframework, wählbare Stilfamilien, AST-Refactorings, neue INTERLIS-Sprachregeln, automatische Constraintvereinfachung, Modellauflösung zum Formatieren, Native-/APE-Arbeit, neue Runtime/Installer, Wasm- oder Jenesis-Migration, pauschale Neuformatierung des Java-Codes.

Keine Änderungen im Quellrepository `sogis/sogis-interlis-repository`. Dortige Dateien sind Referenzen, keine Schreibziele. Keine alten Compilerartefakte als Ersatz für die jetzige Verifikation.

## 4. Verbindliche Invarianten

1. **Fachliche Tokenfolge:** Art, lexikalischer Text und Reihenfolge aller Sprach-/Werttokens bleiben identisch. Signierte Zahlen, Exponenten, Quotes, Escapes und Klammern werden nicht neu serialisiert.
2. **Reparse:** Vollständiges striktes Parsing der Ausgabe; keine neuen Lexer-/Parserfehler und keine verlorenen Teilbäume.
3. **Kommentarvertrag:** Kein Wechsel zwischen `/*`, `/**`, `!!`, `!!@` oder `//...//`. Nur ausdrücklich zulässige gemeinsame Randeinrückung gewöhnlicher Blockkommentare und gewählte EOL-Normalisierung; kein Umbrechen, sprachliches Korrigieren oder Entfernen von Inhalt.
4. **Bindung:** Kommentar-/Dokumentations-/Metadatenreihenfolge und Anker bleiben erhalten. Ein Kommentar zwischen zwei Tokens darf nicht an einem anderen Tokenpaar landen. Doppelte Metaattribute bleiben doppelt und in derselben Reihenfolge.
5. **Werte:** `SourceMetadata.Attribute.text`, Erklärungen `//...//` und sonstige werttragende Mehrzeilenspannen bleiben exakt, einschliesslich bedeutungstragender Whitespace-/EOL-Zeichen.
6. **Idempotenz:** `format(format(s,o),o)` liefert exakt denselben String. Keine zweite Phase, die erst im nächsten Aufruf wirksam wird.
7. **Determinismus:** Ergebnis hängt nur von SourceText, effektiven Optionen und dokumentierter Formatterversion ab, nicht von Uhrzeit, Locale, Filesystem, HashMap-Iteration, Thread oder Netz.
8. **Abbruch:** Kein erfolgreicher Teiltext. `Cancelled` bei wirksamer Cancellation; vor, während und nach aufwendigen Phasen prüfen.
9. **Ablehnung:** Ungültige oder nicht sicher formatierbare Quelle ergibt kein Ergebnis zum Schreiben. Ein Formatterdefekt wird nicht fälschlich als INTERLIS-Sprachfehler ausgegeben.
10. **Ownership:** Jeder originale fachliche Token und jede Kommentar-/Metadatenspanne wird genau einmal ausgegeben. Generiert werden nur erlaubte Whitespace-/EOL-Zeichen und freigegebene Kommentarrandverschiebungen.

Ein Tokenvergleich allein genügt nicht, weil Metadaten zum Teil als Kommentare lexiert werden und durch Positions-/Abstandsänderungen andere Anker erhalten könnten. Ein semantischer Vergleich allein genügt ebenfalls nicht: Er könnte Kommentare, Zahlenlexeme oder redundante Originalklammern verlieren.

## 5. Öffentliche API

`ModelFormatter.format(SourceText, FormatOptions, CancellationToken)`, `ModelFormatters.create()` und die drei Varianten von `FormatOutcome` bleiben. Kein neuer Pflichtparameter für bestehende Aufrufer.

Vorgesehene additive Erweiterung:

```java
public record FormatOptions(
        int indentWidth,
        LineEnding lineEnding,
        boolean finalNewline,
        int maxLineWidth) {
    public FormatOptions(int indentWidth, LineEnding lineEnding, boolean finalNewline) {
        this(indentWidth, lineEnding, finalNewline, 120);
    }
    public static FormatOptions defaults() { /* 2, PRESERVE, true, 120 */ }
}
```

Dies ist ein Signaturentwurf, kein vollständiger einzufügender Quelltext. Nullprüfung und Validierung implementieren. Der bisherige Bereich `indentWidth=0..16` bleibt aus Kompatibilitätsgründen erhalten; 0 bedeutet ausdrücklich keine strukturelle Einrückung. Der LSP akzeptiert weiterhin nur sinnvolle Tabbreiten 1..16. `maxLineWidth` wird als Ganzzahl 40..240 akzeptiert, Default120. Kein `0=unbegrenzt`-Sondermodus.

Alle bisherigen Accessoren bleiben. Dreierkonstruktor und Defaults erhalten reale Consumer-Tests. Eine zusätzliche Record-Komponente verändert Recordstruktur und `toString/equals`; vorhandene Record-Patterns, serialisierte Optionen, Bridgecode und reflektierende Verbraucher suchen. Keine unbelegte vollständige Binär-/Record-Pattern-Kompatibilität versprechen. Die im Repository tatsächlich betroffenen Verbraucher gezielt anpassen; kein generelles Versionsupgrade anderer Produkte.

Der Hausstil wird nach Abschluss dieses Auftrags der normale Formatterstil. Alte erwartete Ausgabetexte dürfen sich deshalb bewusst ändern. Die Produkt-/Formatversionsangabe an ihrer tatsächlichen zentralen Quelle entsprechend dem bestehenden Vertrag anpassen, nicht eine zweite unabhängige Versionskonstante erfinden. Historische Ergebnisberichte behalten ihre alte Formatversion.

## 6. Architektur und Grenzen

[ARCHITECTURE.md](ARCHITECTURE.md) definiert die Verantwortlichkeiten und Methoden. Leitbild:

```text
SourceText
  → striktes SyntaxSnapshot + Originalspannen
  → FormatSourceIndex / SyntaxLayoutIndex / TriviaBindingIndex
  → DeclarationLayout + CommentLayout + ConstraintLayout
  → kleiner LayoutDoc-Baum
  → LayoutPrinter + LineEndingPolicy
  → Reparse + FormatVerifier
  → Formatted / Rejected / Cancelled
```

Internes Hilfspaket kann `guru.interlis.format.internal` sein; nicht exportieren oder öffnen. Interne öffentliche Klassen, die nur zwischen Implementierungspaketen benötigt werden, sind keine SDK-API. Der bestehende Export `guru.interlis.format` genügt.

Kein externer Pretty-Printer, kein neuer Runtime-Diffparser, kein zweiter ANTLR-Generator. Die kleine Layoutabstraktion dient nur diesem Formatter. Nicht nebenbei eine universelle Formatierungsplattform bauen.

## 7. Sicherheit, Umfang und Laufzeit

Vollständig parsebare, aber fachlich ungültige Quellen dürfen syntaktisch formatiert werden. Unaufgelöste Imports, unbekannte Funktionen und fehlende Modelldateien sind kein Anlass, das Netz zu kontaktieren. Vollständige Syntax ist dagegen Voraussetzung.

Prüfe alle Coverageindikatoren UND tatsächlich vorhandene Lexer-/Syntaxfehler; ein „complete“-Bit allein ist keine Erfolgsfreigabe. Bei Parserfehlern originale Diagnosen zurückgeben. Bei ungedecktem syntaktischem Layout oder Fatalverifierfehler fail-closed: keine `Formatted`-Variante mit einem still unveränderten Gesamtdokument als angeblichem Erfolg. Interne Layout-/Quelltreueverletzungen sind interne Fehler (etwa FormatterInvariantException) und laufen über den vorhandenen Hostfehlerpfad, nicht als vermeintliche INTERLIS-Quellfehler. Keinen neuen sealed Outcome-Zweig ohne Notwendigkeit einführen. Gegenbeispiel und technische Fehlerursache im Test-/Debugbeleg behalten.

Für bewusst geschützte einzelne Regionen ist `Formatted` mit stabiler Notice zulässig, sofern Inhalt/Bindung erhalten und alle übrigen Regeln erfüllt sind. Eine solche Ausnahme ersetzt keine Implementierung regulärer Constraint-Familien. Geschützte Fälle werden separat gezählt und begründet.

Keine exponentielle Layoutsuche oder Tiefenrekursion proportional zu sämtlichen Tokens. Breitenentscheidungen mit begrenztem Lookahead und zwischengespeicherten Flat-Längen; iterativer Renderstack bevorzugt. Bei notwendigem Ressourcenlimit anhand vorhandener Projektgrenzen deterministisch abbrechen und keine Teilquelle liefern. Keine willkürlichen grossen Memorylimits oder `StackOverflowError` als normale Steuerlogik.

## 8. Abnahmekriterien FF-01 bis FF-40

| ID | Nachzuweisende Aussage |
|---|---|
| FF-01 | Aktueller B-Stand, fremde Änderungen, Baseline und getrennte Scopegrenzen erhalten. |
| FF-02 | Repräsentativer, unveränderlich gepinnter Referenzkorpus mit Herkunft und echten Dateihashes. |
| FF-03 | Verbindliche Stilbeispiele/Goldens unabhängig vom Implementierungsergebnis festgelegt. |
| FF-04 | Ein Formatterkern; keine zweite Semantik, Engine oder Runtime-Dependency. |
| FF-05 | Bestehende APIaufrufe funktionieren; Breitenoption und Grenzen ausdrücklich getestet. |
| FF-06 | FORMAT ohne IO/Netz/Modellauflösung, mit wohldefiniertem Input. |
| FF-07 | Alle relevanten Block-/Abschnittsarten einschliesslich CONSTRAINTS OF korrekt eingerückt. |
| FF-08 | Hausstilleerzeichen kontextsensitiv; Tokenarten und Lexeme bleiben gleich. |
| FF-09 | Deklarationsleerzeilen sitzen vor dem angehängten Kommentar-/Metadatenbündel. |
| FF-10 | Blockkommentare gemeinsam verschoben; relative Innenstruktur und Text bleiben erhalten. |
| FF-11 | Inline-, verschachtelte und Zeilenkommentare korrekt verankert; keine verschluckten Tokens. |
| FF-12 | Metaattribute in Reihenfolge, Wert, Duplikaten und Bindung erhalten. |
| FF-13 | Erklärungen und werttragende Mehrzeilen-/Unicodeinhalte unverändert. |
| FF-14 | LF/CRLF/CR/PRESERVE/EOF/BOM-Verträge eindeutig und idempotent. |
| FF-15 | Gemeinsamer Layoutmechanismus mit deterministischen Flat-/Break-Entscheidungen. |
| FF-16 | MANDATORY einschliesslich Name, Kommentaren und verschachtelten Ausdrücken. |
| FF-17 | Plausibilitäts-CONSTRAINT mit exakter Prozentzahl und Originalvergleich. |
| FF-18 | EXISTENCE mit REQUIRED IN, Alternativen und Pfaden ohne Umordnung. |
| FF-19 | UNIQUE mit Name, BASKET, WHERE, LOCAL und mehreren Schlüsseln vollständig. |
| FF-20 | SET mit Name-/WHERE-Varianten und BASKET in Originalreihenfolge. |
| FF-21 | Explizite Klammern, Operatoren, Präzedenz und Ausdrucksreihenfolge bleiben erhalten. |
| FF-22 | Lange Calls, weitere Ausdrucksformen, Pfade, Zahl-/Stringatome korrekt behandelt. |
| FF-23 | Kommentare in Constraints begrenzen Umbrüche sicher, nicht pauschal unverändert. |
| FF-24 | Alle Ausgaben strikt reparsbar; unabhängiger Token-/Bindungs-/Wertvergleich. |
| FF-25 | F(F(s))=F(s) und Variantenkonvergenz im ausdrücklich kanonisierten Bereich. |
| FF-26 | Syntaxfehler, Parserlücken, Abbruch und Ressourcenfehler ohne erfolgreichen Teiltext. |
| FF-27 | Kleine adversariale Skalierungs-/Parallelitäts-/Cancellationprüfungen bestanden. |
| FF-28 | CLI stdout/--check/--write/--max-line-width und bestehende Exitverträge korrekt. |
| FF-29 | Dateischreiben bleibt atomar je Datei, inhaltsgeprüft; keine stille BOM-/Datenänderung. |
| FF-30 | LSP-Standardformatierung mit richtigen Optionen, UTF-16, readonly-/Stalebindung. |
| FF-31 | Redundanter defaultbasierter Format-Code-Action-Pfad beseitigt, Quickfixes erhalten. |
| FF-32 | Gleiche effektive Optionen ergeben gleiche API-/CLI-/LSP-Ausgabe. |
| FF-33 | Echte JPMS-JAR-/Consumerprüfung ohne produktive Testzugriffsflags. |
| FF-34 | Tatsächlich betroffene portable/Editorverbraucher schmal geprüft oder Nichtbetroffenheit belegt. |
| FF-35 | Gesamter gepinnter Formatierkorpus geprüft, Änderungen klassifiziert, Safetyfehler null. |
| FF-36 | Gezielte semantische Vorher-/Nachherkontrollen mit geschlossener lokaler Fixtureclosure. |
| FF-37 | Aktive Dokumentation, alte Stiltests, toter Formattercode und Pfade konsistent bereinigt. |
| FF-38 | Test-/Belegpolitik eingehalten; keine fingierten Nachweise und keine reflexhaften Volläufe. |
| FF-39 | Geprüfter Kandidatencommit und autonomer echter OpenCode-/DeepSeek-Review am exakten Stand. |
| FF-40 | Findings vollständig behandelt, neue Kandidaten neu reviewed; finales Urteil/Scope ehrlich. |

## 9. Fertigstellung

F1 allein ist kein vollständiger Abschluss dieses Auftrags. F2 darf nicht auf `MANDATORY` reduziert werden. Keine Pflichtfamilie mit NOTICE, XFAIL oder „später“ grün markieren. Erfolgreich ist der Auftrag erst mit FF-01..FF-40 und gültigem unabhängigen Gesamt-PASS. Echte technische Blocker werden vollständig berichtet; sie rechtfertigen keine stillschweigende Scopeverkleinerung.
