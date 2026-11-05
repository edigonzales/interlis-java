# SO_STANDARD — verbindlicher Stil für F1 und F2

Die Regeln sind ein vorgeschlagener Projektstandard auf Grundlage der Solothurner Modelle, keine Behauptung einer offiziellen INTERLIS-Normformatierung. Die vollständigen ausführbaren Testfixtures werden in F0 erstellt und gegen den tatsächlichen Parser geprüft. Beispiele hier zeigen Layout; gekürzte Namen sind keine realen Modellimporte.

## 1. Grundsatz: Darstellung ändern, Inhalte bewahren

Default: zwei Spaces pro Ebene, keine neuen Tabs, weiche Breite120. Die Breite ist ein Layoutziel, keine Syntaxbedingung. Lange Stringatome, URLs, Erklärungen, Pfade ohne sichere Umbruchstelle und Kommentarinhalte dürfen länger sein. Keine Trunkierung, automatische Stringverkettung, Namenänderung oder harte Textumbruchoperation.

Dieser Stil ist absichtlich nicht vollständig kanonisch für jeden möglichen Whitespace: ursprüngliche Kommentarform, sichere manuelle Kopfzeilenumbrüche und begründete Attributgruppen dürfen erhalten bleiben. Kanonisch sind insbesondere Blockeinrückung, deklarierte Separatoren, Tokenabstände und die unten definierten Constraintgruppen. Die Garantie lautet Idempotenz plus Konvergenz nur für die explizit kanonisierten Bereiche.

## 2. Blockstruktur und Einrückung

MODEL auf Ebene0, TOPIC üblicherweise1, darin CLASS/STRUCTURE/ASSOCIATION/VIEW2, deren Mitglieder3. Konkrete Tiefe folgt dem Syntaxbaum, nicht hart diesen Namen. `END Name;`, namenloses `END;` und `END Model.` gehören zur öffnenden Blockebene.

Auch `GRAPHIC`, `CONSTRAINTS OF`, verschachtelte Enumerationen, DOMAIN-/UNIT-/FUNCTION-/PARAMETER-/ATTRIBUTE-Abschnitte und die im unterstützten Parser vorhandenen Definitionsgruppen erfassen. Nicht jedes `=` eröffnet einen Block: Attributzuweisungen, Vergleiche und Enum-/Domaindefinitionen dürfen keine falschen Indentereignisse erzeugen. `END` in String, Kommentar oder Erklärung ist kein Blockende.

```ili
INTERLIS 2.4;

MODEL Example AT "https://example.org" VERSION "1" =

  TOPIC Data =

    /** Gebäude des Bestands.
     */
    CLASS Building =
      Name : TEXT*100;
      Number : TEXT*20;
    END Building;

    CLASS Address =
      Street : TEXT*100;
    END Address;

  END Data;

END Example.
```

In MODEL und TOPIC eine Leerzeile nach dem vollständigen Kopf und vor dem END, wenn der Körper nicht leer ist. In Klassen/Strukturen/Assoziationen/Views/Constraintblöcken keine zusätzliche Leerzeile direkt innen vor dem ersten oder nach dem letzten Mitglied. Leere Blöcke besitzen nur die notwendigen Kopf-/END-Zeilen, keine leere Zwischenzeile.

`IMPORTS`, `DEPENDS ON`, `OID AS` und ähnliche Präambelzeilen stehen auf der jeweiligen Körperebene. Keine Leerzeilen zwischen aufeinanderfolgenden Präambelzeilen erzwingen; eine Leerzeile vor der nächsten eigenständigen Deklarationsgruppe.

Mehrere Deklarationen auf einer Zeile auseinanderziehen, sofern kein geschützter Kommentar-/Wertbereich die sichere Aufteilung verhindert. Ein Mitglied pro eigener Zeile; keine Statements allein durch Suche nach `;` zerlegen, da Funktionsargumente und andere Konstrukte ebenfalls Semikolons enthalten.

## 3. Deklarationsabstände

| Übergang | Gewünschter Abstand |
|---|---|
| Zwei eigenständige Blockdeklarationen | Genau eine Leerzeile |
| TOPIC ↔ andere eigenständige Deklaration | Genau eine Leerzeile |
| Blockende → Dokumentation/Metaattribute der nächsten Deklaration | Eine Leerzeile VOR dem gesamten Bündel |
| Dokumentation/Metaattribute → zugehörige Deklaration | Keine neu eingefügte Leerzeile |
| Aufeinanderfolgende einfache Attribute/Rollen | Keine neue Leerzeile; bestehende bewusste Gruppe höchstens eine |
| Mitgliedsblock → erster Constraint | Genau eine Leerzeile vor dessen Anhangbündel |
| Zwei Constraints | Eine Leerzeile, wenn mindestens einer mehrzeilig oder mit eigenem Anhangbündel; sonst keine neu erzeugte Leerzeile, vorhandene Gruppe höchstens eine |
| Innerhalb einer Kommentar-/Metawertspanne | Nie strukturell normalisieren |
| Anfang/Ende ausserhalb aller Tokens | Keine führenden Leerzeilen; EOF nach Abschnitt10 |

Definition „Anhangbündel“: die vom vorhandenen Parser/Bindungsmodell der Deklaration zugeordneten vorangestellten Dokumentationskommentare und Metaattribute, mit unveränderter interner Reihenfolge. Nicht einfach jeden vorherigen Kommentar als Dokumentation interpretieren. Insbesondere ein trailing `!!` am vorherigen `END` bleibt dort.

Liegen freie Abschnittskommentare zwischen Deklarationen, bleibt ihre Position am selben Tokenintervall. Umgebende Freiräume dürfen auf höchstens eine Leerzeile reduziert werden, aber nur wenn die erneute Parserbindung unverändert ist. Keine section heading über eine Deklaration hinweg verschieben.

## 4. Tokenabstände: syntaxsensitiv

| Kontext | Beispiel |
|---|---|
| Attribut-/Argument-/Namensseparator | `Name : MANDATORY TEXT*100;` |
| Funktionsaufruf und eingebaute Prädikate | `DEFINED(Name)` bzw. `Functions.Check(A, B)` |
| Schlüsselwort mit Klammergruppe | `NOT (`; `UNIQUE (BASKET)`; `(LOCAL)` bleibt zusammen |
| Liste | Ein Space nach `,`; keiner davor |
| Satzende | Kein Space vor `;` oder dem Modellabschlusspunkt |
| Qualifizierter Name | `Model.Topic.Class` |
| Rollen-/Attributpfad | `Relation_R->Attribute`, ohne Spaces um den Pfeil |
| Wertebereich | `1 .. 100` |
| Kardinalität | `{0..*}`, `{1}`, ohne innere Spaces |
| Typgrösse | `TEXT*100`, `MTEXT*500` |
| Arithmetischer Operator | `A * B`, `A + B`, nicht nach Texttypregel behandeln |
| Vergleich/Implikation | `A == B`, `A <> B`, `A => B`; Originaloperator beibehalten |
| Enumwert | `#Status.active`, kein Space nach `#` |
| Klammern/Index | Keine Padding-Spaces direkt innen; Satzkontext bleibt massgeblich |
| Rolle/Assoziation | `Parent_R -<> {1} Parent;`, keine Zerlegung von Operatorzeichen |

ANTLR kann zusammengesetzte Sprachzeichen wie `==`, `->`, `<>`, `=>` als mehrere Tokens liefern. Layout darf innerhalb ihrer syntaktischen Einheit keinen Zeilenumbruch einfügen. Zusammenziehen darf die Folge von Lexerarten/-lexemen nicht verändern. `1 .. 2` darf nicht versehentlich als Float/anderer Tokenstrom entstehen; signierte Zahlen wie `-1` nicht zu anderer Tokenisierung umformen.

Doppelpunkte haben verschiedene Bedeutungen, etwa Pfadbestandteil, Named Constraint, Typseparator oder LOCAL-/WHERE-Trenner. Die Attributregel ist keine globale Regex. Ein Pfad mit `Name:` erhält die zur Grammatik gehörige Darstellung und keinen dazwischengeschobenen Absatz.

Modifikatoren, Keywordschreibweise, Ziffern, Exponentenschreibweise und überflüssig wirkende Klammern nicht normalisieren. Zahlenlexeme wie `1.00`, `1E+3` und `9007199254740993.99` bleiben exakt.

## 5. Mehrzeilige Köpfe, Domains und Enumerationen

Vorhandene sichere Zeilenumbrüche im MODEL-Kopf zwischen Name/Sprache, AT, VERSION, TRANSLATION OF und `=` erhalten; fortgesetzte Kopfzeilen auf eine zusätzliche Ebene setzen. Keine neue einheitliche Kopfzeilenaufteilung über den ganzen Korpus erzwingen. Eine schon einzeln stehende `=`-Zeile bleibt eine Kopfzeile, kein leeres Statement.

Bei CLASS/STRUCTURE/VIEW/ASSOCIATION-Köpfen kurze kompakte Darstellung erlauben; vorhandene sichere Fortsetzung vor `EXTENDS` erhalten und eine Ebene einrücken. Nicht benachbarte Prefixes umordnen. Die Grenzen nach `=` richten sich nach dem tatsächlichen vollständigen Kopf, nicht dem ersten Gleichheitszeichen im Tokenstrom.

DOMAIN und andere Abschnittskeywords erhalten eine eigene Zeile. Deklarationen darin werden eine Ebene zusätzlich eingerückt; Kommentare zu einer Domaindefinition stehen auf deren Ebene. Vorhandene kleine Gruppen innerhalb eines Abschnitts respektieren, Mehrfachleerzeilen ausserhalb geschützter Regionen reduzieren.

Enumerationen mit vorhandener Mehrzeilenstruktur oder geschachtelten Werten werden ein Element pro Zeile und eine zusätzliche Ebene eingerückt. Kompakte kurze Enumerationen dürfen kompakt bleiben. Kein Umsortieren, kein neues trailing comma, keine Entfernung von ORDERED/CIRCULAR oder sonstigem Suffix. Kommentare zu einzelnen Werten bleiben am Wert. Lange oder kommentierte Listen erhalten sichere Umbrüche; Ein-/Ausklammerung und lexikalische Reihenfolge bleiben gleich.

## 6. Blockkommentare — höchste Priorität

Gleiche Regeln für `/* ... */` und `/** ... */`, Marker bleiben erhalten. Kein Umwandeln in einen anderen Javadoc-Stil, kein Ergänzen/Entfernen von Sternen, kein Wortumbruch. Einzeilig bleibt einzeilig, mehrzeilig bleibt mehrzeilig.

### 6.1 Standalone-Block

Ein eindeutig einer Deklaration/einem Mitglied zugeordneter standalone Kommentarblock wird als Ganzes auf dessen Zielspalte verschoben. Das ist eine starre Randverschiebung, keine Inhaltsformatierung.

Beispiel einer erlaubten Verschiebung:

```text
Vorher:
/** Beschreibung.
 *  A | B
 *  --+--
 *  1 | 2
 */

Nachher bei Ebene2:
    /** Beschreibung.
     *  A | B
     *  --+--
     *  1 | 2
     */
```

Die zwei Spaces nach `*` bleiben zwei, auch wenn sie ungewöhnlich wirken. Tabellen, Listen, Codebeispiele, Versionshistorien, leere Kommentarzeilen und `!!` innerhalb des Blocks nicht interpretieren.

Technisch wird die tatsächlich entfernte/ergänzte gemeinsame Randspalte als erlaubte Transformation verzeichnet. Bei Linksverschiebung dürfen nur nachweislich vorhandene reine Randspaces entfernt werden; unterschiedliche Tabs, fehlender gemeinsamer Rand oder Zeilen links vom Öffner erfordern besondere Vorsicht. Keine beliebige `trim`-/`stripIndent`-Normalisierung. Kann die starre Verschiebung nicht sicher nachgewiesen werden, bleibt der Block samt erforderlicher Umgebung unverändert und erhält eine präzise, seltene Notice. Diese Ausnahme darf nicht alle üblichen mehrzeiligen Kommentare erfassen.

Kommentarinterne Leerzeilen und trailing Spaces werden nicht pauschal gestrippt. Gewählte EOL-Umstellung ist bei gewöhnlichen Kommentaren erlaubt, aber separat vom Text-/Einrückungsnachweis zu prüfen. Ein Kommentar mit geschachtelten `/* ... */` bleibt ein Originalobjekt gemäss Lexer, keine manuelle Regex-Zerlegung.

### 6.2 Inline- und gemischte Blöcke

`CLASS /* Hinweis */ C =` bleibt zwischen CLASS und C. `Attr : TEXT; /* Hinweis */` bleibt nach demselben Semikolon. Kommentartext bleibt vollständig. Bei mehrzeiligen gemischten Kommentar-/Codezeilen zuerst genaue Token-/Triviagrenzen analysieren: nicht erneut die ganze Quellzeile emittieren und dadurch das folgende TOPIC oder einen Token doppelt ausgeben.

Eine komplizierte gemischte Originalregion darf konservativ geschützt werden, aber nur als lokaler Bereich. Der Rest des Dokuments wird weiter formatiert. Schutz ist keine Erlaubnis, fachliche Tokens auf derselben Zeile zu verlieren. Quellbesitz und Reparse prüfen dies ausdrücklich.

## 7. Gewöhnliche Zeilenkommentare `!!`

Standalone `!!`-Zeile entsprechend ihrem syntaktischen Umfeld einrücken, Text nach dem Marker unverändert. Marker nicht in `!!@` umwandeln. Ein trailing Kommentar bleibt nach seinem ursprünglichen Token/Statement; genau ein trennendes Space vor dem Kommentar ist das Defaultlayout. Bereits zum Kommentarinhalt gehörende Leerzeichen bleiben bestehen.

Nach einem `!!`-Kommentar ist ein echter Zeilenumbruch erforderlich, wenn danach weiterer Quellcode folgt. Kein Flat-Layout darf das nächste Token in den Kommentar ziehen. Am Dateiende ohne nachfolgenden Code gilt allein die EOF-Option.

Innerhalb eines mehrzeiligen Constraints bleiben trailing Kommentare an derselben Atom-/Operatorgrenze. Umbruchentscheidungen werden dort hart, nicht optional. Kein Umwandeln von Kommentararten, keine automatischen Absätze.

## 8. Metaattribute `!!@`

Metaattribute sind nicht bloss dekorative Kommentare. Schlüssel, lexikalische Schreibweise, Quotes, Duplikate, Reihenfolge und der interpretierte Wert bleiben erhalten. Keine Vereinheitlichung von `name = "X"` zu `name=X` und kein Sortieren.

Einzeilige Metadatenzeilen dürfen nur vor dem Marker gemeinsam mit ihrem Deklarationsbündel eingerückt werden. Mehrzeilige Metawerte werden als vollständige geschützte logische Einheit behandelt. Zeilenpräfixe, die in den Wert eingehen, und ihre originalen EOLs bleiben exakt. Auch bei explizitem `--line-ending lf|crlf` darf sich der Wert nicht ändern; bestehende Notice `VALUE_LINE_ENDING_PRESERVED` weiterverwenden.

Zwischen `ilivalid.msg`, `name` und dem zugehörigen Constraint keine neue Leerzeile erzeugen. Vor dem gesamten Constraintbündel darf die strukturell geforderte Leerzeile stehen. Dieselbe Regel gilt für Attribute, Enumerationswerte und andere tatsächliche Bindungsziele.

## 9. Erklärungen und Strings

`// ... //` ist im vorhandenen Lexer ein `Explanation`-Token, nicht der Java-/JavaScript-Zeilenkommentar. Seine Bytes bleiben erhalten. Gleiches gilt für Stringlexeme, Escapes und mehrzeilige werttragende Originalspannen. Beispiele wie `"/* END C; !!@ */"` erzeugen keine Strukturereignisse.

Keine Unicode-Normalisierung, keine Änderung an kombinierenden Zeichen oder Surrogatpaaren. UTF-16 bleibt das Format von SourceRange und LSP-Offsets. Für die rein optische Breitenentscheidung zählen Unicode-Codepoints; das ist keine Zusage exakter Pixel-/Graphembreite. Kein Teilen eines Codepoints und keine Abschneidung eines Atoms.

## 10. EOL, EOF und BOM

`LF`/`CRLF`: Nur nicht werttragende Layout-/gewöhnliche Kommentarzeilen entsprechend normalisieren. Original-EOLs in geschützten Werten bleiben; vorhandene Notice erhalten.

`PRESERVE`: Ein bestehender erhaltener Umbruch an derselben Token-/Triviagrenze behält sein originales EOL. Neu eingeführte Umbrüche verwenden den dokumentierten dominierenden EOL-Typ ausserhalb geschützter Werte; bei Gleichstand den ersten, ohne beobachteten Typ LF. Nach einem stabilen Layout werden vorhandene Umbrüche nicht bei jedem Durchgang neu klassifiziert. Gemischte Dateien müssen idempotent bleiben. Ein EOL-Plan muss die Zuordnung auch bei entfernten oder neuen Zeilen abbilden; blindes Übernehmen nach Zeilenindex ist falsch.

`finalNewline=true`: Genau ein abschliessender Layoutzeilenumbruch ausserhalb aller Tokens. `false`: Kein zusätzlicher abschliessender Layoutzeilenumbruch. Niemals pauschal den String trimmen, solange der Tail Teil eines Wertes oder Kommentarinhalts sein könnte. Keine führenden Leerzeilen. Unveränderte Leerdatei oder reine Kommentarquelle richtet sich nach dem unterstützten Syntaxvertrag: keine fiktive MODEL-Struktur hinzufügen.

Eine vorhandene UTF-8-BOM ist keine Stilentscheidung. Der CLI-Loader darf beim `--write` nicht heimlich eine BOM entfernen oder erzeugen. Tatsächlichen Loadervertrag untersuchen; entweder BOM verlustfrei getrennt erhalten oder den betreffenden Schreibfall klar ablehnen. Im Kern muss ein akzeptierter führender U+FEFF erhalten bleiben. Ungültig decodierte Bytes nicht mit Ersatzzeichen überschreiben.

## 11. Ausnahmen und Notices

Notices sind deterministisch, dedupliziert und durch Originalbereiche/Gründe erklärbar. Keine Notice für jedes normale Stringatom über120 Zeichen und keine Flut je Zeile eines Metawertes. Geeignete Kategorien: unveränderliche Metawert-EOL, nicht sicher verschiebbarer Kommentarrand, geschützte gemischte Region. IDs zentral definieren und CLI-/API-Darstellung stabil halten.

Eine reguläre Constraintart, eine lange Funktion oder eine normale ASCII-Tabelle darf nicht allein aus Bequemlichkeit unter „protected“ verschwinden. Schutzfälle werden in Korpusberichten mit Anzahl, Bereich und Grund ausgewiesen. Kein angebliches Gesamt-PASS, wenn die Kernanforderungen nur per Fallback umgangen werden.
