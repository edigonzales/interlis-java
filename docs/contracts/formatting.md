# Quellformatierung

Der Compiler formatiert eine exakte Quellenrevision. CLI und LSP setzen die Ausgabe- und Revisionsgrenzen. Formatversion und Compiler-/Bridgeversion sind getrennt.

<a id="formatter-kern"></a>
## Kernvertrag

Die Quellformatierung arbeitet auf einer unveränderlichen `SourceText`-Revision.
Der öffentliche Einstieg `ModelFormatters.create()` liefert einen zustandslosen
`ModelFormatter` aus dem Compiler-Modul `guru.interlis.format`.

Alle Eingaben sind nullfrei. `FormatOptions` enthält die Einrückung (0 bis 16
Spaces je Ebene), die EOL-Policy, die Schlussnewline-Regel und `maxLineWidth`
(40 bis 240 Unicode-Codepoints einschliesslich Einrückung). Defaults sind 2 Spaces, PRESERVE, ein Schlussnewline und 120 Codepoints.

Der Stil und die Abnahmefälle für die Formatversion 2.0 stehen im
[Formatterauftrag](../implementation/formatter-so-standard/SPECIFICATION.md).
Deklarationen, Kommentare und die fünf Constraintfamilien erhalten
syntaxabhängiges Layout. Einheitenqualifikationen verwenden einen Abstand
vor der eckigen Klammer, Pfadindizes stehen direkt am Pfadsegment.
Klammern enthalten am inneren Rand kein Padding. Eine starre Verschiebung
eines Blockkommentars erfasst auch dessen Schlusszeile, wenn dort weiterer
Code folgt. Unteilbare Atome dürfen die Zielbreite überschreiten.
Der Formatter MUSS strikt reparsen und die fachliche Tokenfolge, Kommentar-
und Metadatenanker sowie alle Wertlexeme prüfen, bevor er `Formatted` liefert.
Der Kern benötigt weder IO noch Modellauflösung. Die Ausgabe ist deterministisch
und idempotent.

`FormatOutcome` unterscheidet `Formatted(String text, List<FormattingNotice>)`,
`Rejected(List<Diagnostic>)` mit den ursprünglichen Syntaxdiagnosen und
`Cancelled` mit dem typisierten Grund. Alle Listen sind unveränderlich.
`FormattingNotice` verwendet sichere Meldungen und optionale UTF-16-Bereiche
der Originalrevision. Eine verletzte interne Quelltreueinvariante gelangt zum
internen Hostfehlerpfad. Ablehnung, Abbruch und interne Defekte liefern
keinen formatierten Teiltext.

```text
interlis-compiler format [--check | --write] [--indent N] [--line-ending lf|crlf] [--max-line-width N] <files...>
```

Ohne Ausgabemodus geht die Quelle nach stdout. Hinweise und Diagnosen stehen
auf stderr. `--write` ersetzt jede Datei atomar mit Prüfung des
Originalinhalts. `--check` meldet Änderungsbedarf mit Exitcode 1. Ungültige
Breiten sind Bedienfehler vor Dateizugriff. Der UTF-8-Loader meldet ungültige
Bytefolgen. Eine vom strikten Parser abgelehnte führende BOM bleibt beim
Schreiben im Original erhalten.

Explizites LF oder CRLF normalisiert Layoutumbrüche und Zeilenenden gewöhnlicher Kommentare. Zeilenenden in werttragenden Literalen
und mehrzeiligen Metaattributwerten bleiben exakt erhalten.
`VALUE_LINE_ENDING_PRESERVED` bezeichnet den betreffenden Originalbereich.
PRESERVE übernimmt erhaltene Umbrüche an derselben Token- oder
Triviagrenze. Neu eingeführte Umbrüche verwenden das dominierende EOL
ausserhalb geschützter Werte, bei Gleichstand das erste und ohne Vorlage LF.
Tests vergleichen Kommentarinhalt mit der zulässigen gemeinsamen
Randverschiebung und prüfen Meta- und Literalwerte separat.

Der gemeinsame atomare Hostschreiber prüft Abbruch nach Staging und unmittelbar
vor Ersetzung. Formatierungen vergleichen ausserdem die gelesenen Originalbytes;
ein beobachteter externer Edit bleibt erhalten. Das ist ein optimistisches
Lesefenster, kein Dateisystem-CAS. Es gibt keinen nichtatomaren Move-Fallback.
Bei Fehlern werden temporäre Dateien entfernt; bestehende POSIX-Rechte bleiben
bei erfolgreicher Ersetzung erhalten. Symbolische Ausgabeziele werden abgelehnt.
Mehrfach genannte identische Formatquellen sind Nutzungsfehler. Atomarität gilt
je Datei, nicht als Dateisystemtransaktion über mehrere Dateien.

<a id="quellformatierung-mit-requestoptionen"></a>
## Quellformatierung mit Requestoptionen

`textDocument/formatting` übernimmt `tabSize` (1 bis 16), verlangt
`insertSpaces=true` und akzeptiert optional `interlis.maxLineWidth` als
JSON-Ganzzahl von 40 bis 240. Ohne Zusatzfeld gilt 120. Die Formatierung
verwendet PRESERVE und ein Schlussnewline. Sie liefert bei unveränderter
Quelle eine leere Editliste. Die Ergebnisbereiche verwenden UTF-16 und
gehören zur erfassten Dokumentrevision. Schreibgeschützte, inzwischen
geänderte oder abgebrochene Dokumentanfragen liefern keine Edits.
Quickfixes werden weiterhin über Code Actions angeboten. Die vollständige
Formatierung verwendet den Standardprovider.
