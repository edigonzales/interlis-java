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

Der Dreierkonstruktor von `FormatOptions` verwendet weiterhin Breite 120.
Der Formatter benötigt weder IO noch Modellauflösung. Vollständig parsebare
Quellen lassen sich auch bei fachlichen Modellfehlern formatieren.

Der Formatter MUSS strikt reparsen und die fachliche Tokenfolge, Kommentar-
und Metadatenanker sowie alle Wertlexeme prüfen, bevor er `Formatted` liefert.
Tokenart, Lexem und Reihenfolge bleiben unverändert, einschliesslich
Originalklammern, Operatoren, Zahlenschreibweisen und Erklärungen `//...//`.
Kommentararten, Text und Anker bleiben erhalten. Metaattribute behalten auch
Duplikate und werttragenden Whitespace. Die Ausgabe ist deterministisch und
bei gleichen Optionen idempotent.

`FormatOutcome` unterscheidet `Formatted(String text, List<FormattingNotice>)`,
`Rejected(List<Diagnostic>)` mit den ursprünglichen Syntaxdiagnosen und
`Cancelled` mit dem typisierten Grund. Alle Listen sind unveränderlich.
`FormattingNotice` verwendet sichere Meldungen und optionale UTF-16-Bereiche
der Originalrevision. Eine verletzte interne Quelltreueinvariante gelangt zum
internen Hostfehlerpfad. Ablehnung, Abbruch und interne Defekte liefern
keinen formatierten Teiltext.

## Standardstil 2.0

Der Solothurner Standardstil verwendet Spaces. Die syntaktische Blocktiefe
bestimmt die Einrückung; `END` steht auf Höhe des Blockkopfs. Nichtleere
`MODEL`- und `TOPIC`-Blöcke setzen den Körper durch Leerzeilen ab. Andere
Blöcke erhalten am Innenrand keine zusätzliche Leerzeile. Abschnitte wie
`DOMAIN`, `UNIT` und `ATTRIBUTE` rücken ihre Deklarationen eine Stufe ein.
Sichere Kopfzeilenfortsetzungen werden eingerückt und bleiben erhalten.
Verschachtelte oder bereits mehrzeilige Enumerationen werden eingerückt,
mit einem Element pro Zeile.

Eigenständige Deklarationsblöcke trennt eine Leerzeile. Sie steht vor
angehängten Dokumentationskommentaren und Metaattributen; das Bündel bleibt
mit der Deklaration zusammen. Einfache Mitglieder bleiben ohne erzwungene
Leerzeilen. Kommentare bleiben zwischen denselben fachlichen Tokens; nach
`!!` beginnt weiterer Code auf einer neuen Zeile. Mehrzeilige Blockkommentare
werden bei nachweisbar gemeinsamem Rand als Ganzes verschoben, auch an
einer Schlusszeile mit weiterem Code. Wo eine solche Verschiebung unsicher
ist, kann eine Notice die geschützte Stelle bezeichnen.

Tokenabstände folgen dem Kontext: `Name : TEXT*100`, `1 .. 100`,
`{0..*}`, `A * B`, `A == B`, `Model.Topic.Class`, `Role->Attribute`,
`#Status.active` und `f(A, B)`. Klammern haben innen kein Padding.
Einheitenqualifikationen haben einen Abstand vor der eckigen Klammer;
Pfadindizes stehen direkt am Pfadsegment.

Die fünf Constraintfamilien bleiben bei ausreichender Breite und ohne
erzwungene Umbrüche einzeilig. Die Breite zählt Einrückung und Unicode-Codepoints.
Unteilbare Atome dürfen länger sein. Bei Umbrüchen:

- `MANDATORY CONSTRAINT` und Plausibilitäts-`CONSTRAINT` rücken den Ausdruck
  unter den vollständigen Kopf; Vergleich und Prozentlexem bleiben im Kopf.
- `EXISTENCE` gliedert Zielpfad, `REQUIRED IN` und `OR`-Alternativen.
- `UNIQUE` gliedert den Schlüsselblock und gegebenenfalls `WHERE`; bei
  `LOCAL` bleibt der Strukturpfad bei der lokalen Klausel.
- `SET CONSTRAINT` gliedert Kopf, `WHERE` und Mengenbedingung in der
  Originalreihenfolge, auch wenn der Name erst nach `WHERE` steht.

Gebrochene logische Ketten setzen ihre Operatoren an den Anfang der
Fortsetzungszeile. Klammergruppen und gebrochene Funktionsaufrufe rücken
Inhalt und einzelne Argumente ein. Syntaxbindung, Klammern, Pfade und
Kommentaranker bleiben erhalten. Der Formatvertrag verlangt ein stabiles
Ergebnis bei erneutem Formatieren; sichere manuelle Umbrüche und
Mitgliedsgruppen können weiterhin zu unterschiedlichen Ausgaben führen.

## CLI und Zeilenenden

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
Die Schlussnewline-Option setzt genau einen abschliessenden Layoutumbruch
oder lässt ihn weg; werttragende Zeichen bleiben davon unberührt.
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
