# F2 — vollständiges Constraint- und Ausdruckslayout

## 1. Keine zweite Grammatik

Verwende den bestehenden Parser, typisierte Syntax und die exakten Originaltokens. Die geprüfte `InterlisParser.g4` besitzt mandatoryConstraint, plausibilityConstraint, existenceConstraint, uniquenessConstraint, setConstraint und den Block constraintsDef. Syntaxvalidität und versionsabhängige Zulässigkeit kommen von diesem Sprachstack, nicht vom Layouter.

Vor Implementierung in F0 alle tatsächlich akzeptierten Formen den vorhandenen Syntaxfeldern/-ranges zuordnen. Insbesondere:

- `ConstraintDeclarationSyntax.where` dokumentiert SET; die UNIQUE-Vorbedingung darf daraus nicht übersehen werden. Prüfe auch `expression` und den tatsächlichen Syntaxbuilder.
- SET erlaubt verschiedene Positionen von Name und WHERE. Der Formatter erhält deren Originalreihenfolge.
- Zusammengesetzte Operatoren können aus mehreren Lexertokens bestehen.
- Die Grammatik besitzt Erklärungstokens, Spezialpfade, BASKET-Prädikate, INSPECTION, PARAMETER und ALL-Argumente. Nicht alles ist ein gewöhnlicher Funktionsaufruf.

Wenn eine benötigte Quellstruktur im aktuellen AST tatsächlich fehlt, ergänze eine minimale formatterinterne Layoutansicht aus den vorhandenen Parser-/Tokenranges. Kein öffentliches Semantikrecord umdeuten, keine zweite Parsergrammatik, keine Stringzerlegung nach AND/OR. Eine notwendige interne Parser-/Syntaxadapterergänzung erhält eigene schmale Regressionen.

## 2. Einheitlicher Flat-/Break-Vertrag

Zuerst die vollständige syntaktische Gruppe mit Originaltokens und Kommentaren modellieren. Der flache Kandidat enthält die vorgesehenen Spaces. Passt er inklusive Basiseinrückung bis zur weichen Breite und enthält keine harten Umbrüche, bleibt er einzeilig. Ein vorheriger manueller Constraintumbruch allein erzwingt nicht dieselbe Form: Constraintlayout ist bewusst kanonischer als der übrige Stil.

Muss eine Gruppe brechen, verwende ihre definierte mehrzeilige Alternative. Nicht „bei Spalte120 schneiden“, nicht möglichst viele Argumente zufällig in die letzte Zeile quetschen und nicht jeden Token auf eine Zeile legen. Untergruppen dürfen flach bleiben, wenn sie passen und kein Kommentar eine andere Form erzwingt.

Breite120 ist Default, Breite40..240 konfigurierbar. Breite zählt Codepoints samt Einrückung; kein Font-/Localezugriff. Unteilbare Atome dürfen überlaufen. Begründete Überlänge ist kein Formatterfehler und darf nicht durch Tokenänderung beseitigt werden.

## 3. MANDATORY CONSTRAINT

Kurzer Fall:

```ili
MANDATORY CONSTRAINT DEFINED(Name);
```

Bei umgebrochenem Ausdruck steht der vollständige Kopf (einschliesslich optionalem Namen und `:`) auf eigener Zeile. Der Ausdruck beginnt eine Ebene tiefer:

```ili
      !!@ name=Check_Schutzwald
      MANDATORY CONSTRAINT
        NOT (
          DEFINED(Schutzwald_R)
          AND NOT (
            Waldfunktion_R->Funktion == #Schutzwald
            OR Waldfunktion_R->Funktion == #Schutzwald_Biodiversitaet
          )
        );
```

Dies ist die festgelegte Sollpolitik für den brechenden Fall, auch wenn bisherige Repositorybeispiele `NOT (` noch auf der Kopfzeile hatten. `!!@ name` nicht in einen sprachlichen Constraintnamen umwandeln.

Optionaler echter Name bleibt beispielsweise `MANDATORY CONSTRAINT Check_Name : ...;`; ist der Kopf allein überbreit, nicht den Namen zerlegen. Bestehende Klammern sämtlich erhalten. Der Formatter fügt keine Klammern hinzu, auch wenn er eine andere Schreibweise verständlicher fände.

## 4. Logische Ausdrücke und Präzedenz

Bei brechenden gleichartigen Ketten stehen AND, OR beziehungsweise die tatsächliche Implikation am Anfang der Fortsetzungszeile. Die Folge der Originaltokens bleibt vollständig gleich. Flachwerden und Aufbrechen sind Layoutentscheidungen, keine algebraische Assoziierung.

Explizite ParenthesizedExpressionSyntax bildet eine eigene Gruppe. Öffner auf der Zeile des vorangehenden Operators/Funktionskontexts, Inhalt eine Ebene tiefer, schliessende Klammer auf der Öffnerebene. Klammern dürfen nicht entfernt werden, selbst wenn der Compiler sie für semantisch redundant hält.

Gemischte Operatoren: AST-Bindung verwenden. Bei fehlenden expliziten Klammern keine neuen Klammern erfinden. Stärker bindende gebrochene Untergruppen dürfen zusätzlich eingerückt werden, aber nur mit unveränderter Tokenfolge. Beispielsweise kann das reine Layout von `A AND B OR C AND D` lauten:

```text
A
  AND B
OR C
  AND D
```

Das ist eine Darstellung von Teilgruppen, keine Neuschreibung des Ausdrucks. Die tatsächlichen INTERLIS-Vorgaben und die Parserstruktur haben Vorrang vor aus Java/SQL erinnerten Präzedenzen. **Insbesondere niemals eine allgemeine Fremdsprachen-Operatorrangliste einsetzen.**

Vergleiche und arithmetische Ausdrücke bleiben möglichst kompakte Untergruppen. Bei notwendigem Brechen vor dem jeweiligen binären Operator umbrechen und den rechten Operanden angemessen einrücken. Unäre Vorzeichen, signed-number Tokens, Pfadpfeile und `NOT (` nicht durch eine generische binäre Operatorregel zerlegen.

## 5. Funktionsaufrufe und Argumente

Kurzer Aufruf: `Functions.Check(A, B)`.

Langer Aufruf: Öffner nach dem unveränderten qualifizierten Funktionsnamen; jedes Argument auf einer eigenen eingerückten Zeile, Komma hinter dem Argument, Schlussklammer auf der Aufrufebene. Keine neuen trailing commas.

```ili
MANDATORY CONSTRAINT
  GeoW_FunctionsExt.IsInsideExternalXtfResource(
    "ch.so.agi.av.hoheitsgrenzen.xtf",
    "SO_Hoheitsgrenzen_Publikation_20170626.Hoheitsgrenzen.Kantonsgrenze.Geometrie",
    "c6db23e0-0536-4055-a50c-11d2d1e4c6ef",
    THIS,
    "Geometrie"
  );
```

Die lange Zeichenkette bleibt ein Atom. Verschachtelte Calls rekursiv als Layoutgruppen, aber Renderer und Breitenberechnung ohne unbeschränkte Java-Stacktiefe. Funktionsdefinitionen verwenden andere Separatoren (`;` zwischen Argumentdefinitionen) als Calls (` , ` zwischen Argumenten); diese Grammatikformen nicht vermischen.

`DEFINED(...)`, ALL/ALL OF, `(BASKET)`, PARAMETER, INSPECTION und sonstige unterstützte Faktoren bekommen eine passende strukturtreue Darstellung oder einen exakt begrenzten Atom-/Pfadknoten. Ein fehlender besonderer Printer darf nicht zum Wegfall von Quelltokens führen.

## 6. Plausibilitätsbedingungen

Die vorhandene Syntax lautet **CONSTRAINT**, optional Name, `<=` oder `>=`, exakte Prozentzahl, `%`, Ausdruck. Kein `PLAUSIBILITY CONSTRAINT` generieren.

Kompakter Beispielschnipsel:

```ili
CONSTRAINT >= 95% DEFINED(Name);
```

Beim Brechen verbleiben `CONSTRAINT`, optionaler Name, Vergleich und Prozentzahl mit `%` im Kopf. Der Ausdruck folgt eine Ebene tiefer. `95`, `95.0`, `9.5E1` nicht ineinander umrechnen; weder `double` noch Localeformatierung verwenden. Die normative Gültigkeit einer gegebenen lexikalischen Zahl gehört zum bestehenden Parser/Compiler.

Mindestens unbenannter/namentlicher Fall, beide Vergleichsrichtungen, exakte Dezimallexeme und Kommentar zwischen Kopf und Ausdruck testen. Literalvalidität von Prozentangaben nicht neu implementieren.

## 7. EXISTENCE CONSTRAINT

Das Layout trennt Zielpfad, `REQUIRED IN` und Alternativen. Lange Form:

```ili
EXISTENCE CONSTRAINT
  ExternalId
  REQUIRED IN Catalogue.Entry : Id
  OR OtherCatalogue.Entry : ExternalId;
```

Jede `OR viewableRef : attributePath`-Alternative bildet eine Einheit. Das OR hier ist der Grammatikseparator der Existenzbedingung, kein frei umformbares Bool-OR. Reihenfolge und qualifizierte Pfade erhalten; keine Sortierung, Zusammenlegung oder neue Imports.

Kurze gesamte Bedingung bleibt flach. Ist eine einzelne Referenz-/Pfadzeile unteilbar zu lang, gezielt überlaufen lassen. Optionalen Namen sowie Kommentare an `REQUIRED IN` und Alternativen abdecken.

## 8. UNIQUE

Unterstütze unabhängig voneinander und in den tatsächlich zulässigen Kombinationen: optionaler Name, `(BASKET)`, `WHERE expression :`, globales Keyset, `(LOCAL) path : fields`.

Einfach:

```ili
UNIQUE Municipality, Number;
```

Bei mehrzeiligem globalem Keyset ein Schlüssel pro Zeile, in Originalreihenfolge. Ein vorhandenes WHERE bleibt als eigener Klauselknoten; sein `:` gehört zur Klausel und darf nicht mit einem Typseparator verwechselt werden. Bei LOCAL bleiben `(LOCAL)` und der vorangestellte Strukturpfad zusammen. Lange WHERE-Ausdrücke verwenden denselben ExpressionLayout-Mechanismus, nicht eine reduzierte Sondergrammatik.

Schema einer langen Form (durch reale vollständige Fixtures in F0 bestätigen):

```ili
UNIQUE (BASKET) Check_Number :
  WHERE Status == #active :
    Municipality,
    Number;
```

Für `(LOCAL)` entsprechend die Originalstruktur erhalten, etwa `UNIQUE (LOCAL) Entries : Code, Language;`. Keine Umwandlung lokaler Eindeutigkeit in globale und keine Interpretation des Pfads als Rollenauflösung im Formatter.

Alle Grenzzeichen/Anker durch Originaltokenordinale festhalten. Named-Constraint-Doppelpunkt, WHERE-Ende und LOCAL-Ende sind unterschiedliche Rollen; ein Printer darf nicht nur am ersten `:` splitten.

## 9. SET CONSTRAINT

Unterstütze `(BASKET)`, benannte/unbenannte Formen, WHERE-Präbedingungen und den eigentlichen Mengen-/Setausdruck. Die Grammatik lässt den Namen in unterschiedlichen Positionen zu; **die vorgefundene Tokenreihenfolge bleibt**.

Kompakt:

```ili
SET CONSTRAINT INTERLIS.objectCount(ALL) > 0;
```

Bei langem Ausdruck Kopf, vorhandene WHERE-Klausel und Mengenbedingung als getrennte Layoutgruppen. Keine „Vereinheitlichung“ durch Versetzen des Namens vor oder hinter WHERE. Der WHERE-Ausdruck und der Setausdruck müssen im Layoutplan eigene Quellranges haben. Ein `:` in einem darunterliegenden Pfad ist nicht das Klauselende.

Mindestens beide unterstützten Namens-/WHERE-Reihenfolgen, `(BASKET)`, langes WHERE, langer Setausdruck und Kommentare zwischen Klauseln testen.

## 10. CONSTRAINTS OF und Kommentare

`CONSTRAINTS OF <ref> =` ist ein echter Block. Bedingungen eine Ebene tiefer, `END;` auf Kopfebene, Leerzeile zu benachbarten eigenständigen Deklarationen. Namenloses END nicht wie ein beschädigtes CLASS-Ende behandeln.

Ein `!!@ ilivalid.msg`-/`name`-Bündel gehört zum folgenden Constraint und wird nicht in dessen Ausdruck einsortiert. Ein `!!` innerhalb eines Ausdrucks erzwingt seinen Zeilenabschluss. Ein Blockkommentar zwischen zwei Operatortokens bleibt dort; er darf einen Gruppenbruch erzwingen oder eine eng begrenzte Originalregion schützen.

Beispiel (Anker bleiben unabhängig vom gewählten Layout):

```ili
MANDATORY CONSTRAINT
  DEFINED(Name) !! Begründung für genau diesen Teil
  AND DEFINED(Number);
```

Niemals die AND-Zeile hinter `!!` flach anhängen. Kommentar zwischen Schlüsselargumenten darf nicht zum Kommentar vor dem gesamten UNIQUE werden. Erklärung/Metawert mit Text „AND“, „OR“, „END“ oder `;` ist kein Trennsignal.

## 11. Vollständigkeit und Tests

F0 erstellt eine Coverage-Tabelle pro Parseralternative: vorhandene positive Fixture, Quell-/AST-Zuordnung, Flatgolden, Breakgolden, Kommentarvariante. Jede der fünf Familien benötigt mindestens einen kurzen, einen langen und einen kommentierten Fall. Zulässige Familien-/Versionskombinationen nach dem tatsächlichen bestehenden Vertrag wählen; keine in 2.3 unzulässige 2.4-Form als positive 2.3-Abnahme ausgeben.

Zusätzlich: Breiten knapp unter/auf/über der Schwelle; verschachtelte gleiche und gemischte Operatoren; redundante Originalklammern; Pfeilpfade; dezimale/exponentielle/extrem grosse Zahlen; unteilbare lange Strings; Cancellation; grosse flache Kette und tiefe aber akzeptierte Verschachtelung. Keine Performancebehauptung ohne Messung, keine vollständige Benchmarkkampagne nötig.

Ergebnis muss Token-/Meta-/Bindungsinvarianten, strikten Reparse und Idempotenz erfüllen. Die semantische Stichprobe verwendet dieselben lokalen Abhängigkeiten vor/nachher. Nicht einzelne Compilerdiagnosen löschen, nur weil deren Position im formatierten Text anders ist; anhand Token-/Symbolankern zuordnen.
