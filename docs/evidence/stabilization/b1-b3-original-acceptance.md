# B1–B3: Abnahme der 19 unveränderten Originalfälle

## Umfang und Erfolgskriterium

Die 19 bisher als `REFERENCE_PASS_ACCEPTANCE_PENDING` geführten Fälle werden
gegen ihre tatsächlichen Originalquellen geprüft. Ein Referenz-PASS allein
reicht nicht: Formation, Ziele, Typen, Herkunft, Reihenfolge, Ausdrucksstruktur
und öffentliche Queries müssen den Inhalt des jeweiligen Originals erhalten.
Zuordnung: WP-20–25; META-003/006/008/010/011/014, SEM-005/008–011/014/017,
SYN-004/013/015, TEST-011/012 und VIZ-001/008. Die dabei aufgedeckte
Wiederverwendungskorrektur betrifft zusätzlich INC-009/010/012.

Die lokalen [Originalquellen](../../../testdata/conformance/b1-b3-acceptance/)
stammen unverändert aus ili2c-Commit
`4ea207f388ef7d91b03d968cc1636f4de84e36ea`. `sources.properties` ordnet jede
Test-ID ihrem ursprünglichen Pfad und SHA-256 zu. Alle 19 sind INTERLIS 2.3.
Sie werden nicht durch eine Versionsersetzung zu vermeintlichen 2.4-Originalen:
Die versionsgerechten 2.3/2.4-Gegenproben der bestehenden Verträge bleiben
zusätzliche Nachweise, insbesondere wegen der unterschiedlichen NO-OID-Regeln.
Keiner dieser Fälle benötigt einen Import. Importreihenfolge und übersetzte
Importmodelle werden deshalb nicht als Eigenschaft dieser Originale behauptet.

## Einzelabgleich

Die drei neuen JVM-Testklassen heissen `B1OriginalAcceptanceTest`,
`B2OriginalAcceptanceTest` und `B3OriginalAcceptanceTest`. Jede Tabellenzeile
besitzt dort eine eigene Testmethode; deren genauer Name, die vollständige
Suite-ID und der Quellenpin stehen im Fallkatalog. Die fünf `*Complete`-Quellen enthalten jeweils
Originalmodell A und Übersetzungsmodell B; geprüft werden beide Modelle.

| Suite-Test-ID | Testklasse | Konkret nachgewiesener Modell-/Queryinhalt |
|---|---|---|
| `ili23.Expressions23Test.expressions_BasenamePathElement` | B1 | Expliziter Alias BaseName mit eigener Identität; Projektion auf B; abgeleitetes a als TEXT*20; Pfad zu B.Attr1 und Navigation beider Pfadsegmente. |
| `ili23.Expressions23Test.expressions_BasenameToUnrenamedViewable` | B1 | Implizite Basis B bleibt von der Klasse unterscheidbar; Navigation auf die Quellklasse; Attr1-Ziel und TEXT*20 erhalten. |
| `ili23.View23Test.abstractView_AbstractBaseInConcretViewDef` | B1 | Konkreter Join über abstrakte Basis; nur konkrete Attr2/Attr3 in Quellreihenfolge, TEXT*10/30, ALL-OF-Herkunft, Topic-Abhängigkeit und B2-Überschreibung. |
| `ili23.View23Test.abstractView_AcceptAbstractBaseInAbstractJoinDef` | B2 | Abstrakter Join; beide Basen; Attr1/2/3 mit Typen, Herkunft und Reihenfolge; Attr1 bleibt abstrakt. Entferntes ABSTRACT wird gezielt abgelehnt. |
| `ili23.View23Test.view_AcceptBasicSelectionViewDef` | B1 | Projektion und Topic-Abhängigkeit; ALL OF liefert Attr1/2, TEXT*20/10 und Ableitungen mit kanonischer Quellherkunft. Das Original enthält keine WHERE-Bedingung. |
| `ili23.View23Test.view_AcceptSimpleBaseExtentionInView` | B1 | Formation bleibt auf B; gesonderte Erweiterungszuordnung auf C; C erbt B; a führt über C zu C.Attr2 als TEXT*30. |
| `ili23.ViewDef23Test.viewProjectionComplete` | B1 | Alle zehn Views; ABSTRACT/TRANSIENT/FINAL/EXTENDED; explizite und Topic-Vererbung; ALL OF, Selektions- und Constraintziele, effektive Herkunft/Vererbungspfade und Basiserweiterungen in beiden Modellen. Falsches gleich typisiertes Selektionsziel der Übersetzung wird abgelehnt. |
| `ili23.View23Test.view_AcceptBasicJoinDef` | B2 | Basenreihenfolge B/C, OR NULL an C, Boolesche WHERE mit genauen Operandenzielen; ALL OF mit drei geordneten Attributen und Quellherkunft. |
| `ili23.View23Test.view_AcceptJoinDefWithoutBasenames` | B2 | Dasselbe Basen-/Optionalitäts-/Mitgliedsmodell ohne explizite Aliase; Quellnavigation führt zu B/C. Das Original enthält keine WHERE-Bedingung. |
| `ili23.View23Test.view_AcceptBasicUnionDef` | B2 | Beide Basen; Ergebnis TEXT*30; beide geordneten Ableitungsalternativen mit tatsächlichen Quellzielen und TEXT*10/30 bleiben erhalten. |
| `ili23.ViewDef23Test.viewAggregationComplete` | B2 | Je Modell eigene Basis und tatsächliches EQUAL-Attribut; öffentlich zugängliches AGGREGATES-BAG mit richtigem Elementziel, ausserhalb der Transfermitglieder. Leere explizite Mitgliedsliste ist beabsichtigt. Falsches gleich typisiertes EQUAL-Ziel wird abgelehnt. |
| `ili23.ViewDef23Test.viewJoinComplete` | B2 | Je Modell zwei geordnete Basen und OR NULL an der zweiten; getrennte Zielidentitäten. Ohne ATTRIBUTE/ALL OF ist die Ergebnisliste bewusst leer. Entferntes OR NULL der Übersetzung wird abgelehnt. |
| `ili23.ViewDef23Test.viewUnionComplete` | B2 | Je Modell zwei Ergebnisattribute mit je zwei geordneten Ableitungsalternativen; genaue vier Quellattribute und numerische Typen. Falsches gleich typisiertes Alternativziel wird abgelehnt. |
| `ili23.Association23Test.association_DerivedAssociationDef` | B3 | Abgeleitete Assoziation mit Joinbasis und geordneten Rollen, Rollenzielen und Ableitungspfaden; boolescher Intersect-Aufruf mit den beiden gebundenen Flächenargumenten. |
| `ili23.View23Test.view_AcceptBasenameOfRenamedInspectionDef` | B3 | Basis-Quellklasse B getrennt von Inspektionselement Struktur A und Parent B; BAG-Pfad; expliziter Alias C und C.Attr1-Ziel mit Navigation. |
| `ili23.View23Test.view_AcceptBasenameOfUnrenamedInspectionDef` | B3 | Gleiches Inspektionsmodell mit implizitem Basisnamen B; kontextueller Pfad führt zu A.Attr1, Navigation des impliziten Namens zur Quellklasse B. |
| `ili23.View23Test.view_AcceptBasicInspectionDef` | B3 | ALL OF kopiert A.Attr1 und seine Herkunft, nicht das gleichnamige B.Attr1; BAG/Element/Parent getrennt; keine Übernahme von B.Attr2 oder der UNIQUE-Bedingung der Quellklasse. |
| `ili23.View23Test.view_ObjectPathWithRolename` | B3 | Alle drei Projektionen und Constraintausdrücke; direkte Assoziationsrollen, inverser Einzelzugang und mehrwertiger Zugang zu objectCount; Zielidentitäten, Kardinalitäten, Referenzqueries und ALL-OF-Herkunft. |
| `ili23.ViewDef23Test.viewInspectionComplete` | B3 | Je Modell korrekte LIST-Dekomposition a0/b0, Struktur-/Parentziele und Aliasbindung; das gleich typisierte alternative a1/b1 ersetzt das Ziel nicht. |

Die bestehenden Gegenproben bleiben Teil des vollständigen `check`-Laufs:
`ViewBindingContractTest` (Typen, Abhängigkeiten, Kollisionen),
`ViewBaseExtensionContractTest` (unverwandte Erweiterung),
`ViewInheritanceTranslationContractTest` (Überschreibungen, Domänenidentität,
Vererbung, Imports und deren Reihenfolge), `InspectionFactorContractTest`
(falscher Kontext, Ziel und gleich typisierte Übersetzung),
`AssociationAccessContractTest` und `QualifiedAssociationPathContractTest`
(Einzel-/Mengenzugänge). Sie ersetzen die originalbezogenen positiven
Assertions nicht.

## Gemeinsame Verbraucher- und Wiederverwendungsnachweise

`OriginalViewAcceptanceSupport` prüft die Quellpins, vollständige Coverage,
leere Diagnostik, aufgelöste Ausdrücke, `expression`, `effectiveMembers` und
`resolveReference`. Die Einzeltests ergänzen konkrete Erwartungswerte und
`symbolAt`/Referenzabfragen statt lediglich Anzahl oder erfolgreiche Annahme.
Öffentlich existiert die Übersetzungsdeklaration am Modell; die Tests prüfen
dazu die tatsächlichen getrennten Zielidentitäten in beiden Modellgraphen.
Eine neue öffentliche Übersetzungstabelle wurde nicht eingeführt.

`OriginalViewReuseTest` vergleicht für alle 19 Quellen fünf Zustände:
Original, unverändert wiederholt, angehängter Kommentar, fehlender Schluss-Punkt,
Reparatur. Frische und wiederverwendete Analyse liefern dieselbe Diagnostik,
Coverage und sämtliche Modell-/Typ-/Ausdrucks-/Referenzwerte. Snapshotlokale
Symbol- und Scope-IDs werden für den Vergleich durch ihre fachlichen Besitzer
und Namen ersetzt; Inhalte, Quellrevisionen und Reihenfolgen bleiben erhalten.
Alle eindeutig unveränderten Deklarationen, einschliesslich Constraints,
behalten zusätzlich ihre Symbolidentität bei Wiederholung und angehängtem Kommentar. Reale
Namensbereiche tragen die aktuelle Revision, alle gebundenen Referenzsegmente
navigieren mit aktuellem Cursor; veraltete Cursor werden vertragsgemäss abgewiesen.

Dabei wurde ein notwendiger Grundlagenfehler korrigiert: `DeclarationIndexer`
verwendete denselben Zähler für neu vergebene IDs und Deklarationsreihenfolge.
Wiederverwendete IDs verfälschten so die Reihenfolge. Beide Zähler sind nun
getrennt. Der Modellanker verwendet zudem wie die Wiederverwendung Ordinal 0,
statt den Quelloffset als Ordinal zu verwenden. Constraintanker verwenden auch
bei Wiederverwendung ihren Besitzer und den separaten Constraintnamensraum;
das gilt ebenso für unbenannte Constraints ohne öffentlichen qualifizierten
Namen. So bleiben ihre eindeutigen Identitäten erhalten. Diese internen
Korrekturen ändern weder Module noch öffentliche API.

`OriginalViewProjectionTest` prüft die gleichen 19 Quellen über den
Diagrammverbraucher: vollständiges Dokument, Snapshot/Revisionen, Viewknoten,
Mitglieder-/Constraintidentitäten und Reihenfolge, Vererbungsherkunft sowie
sämtliche zugesagten Basis- und Ableitungsbeziehungen. Inspektionen behalten
ihre Darstellung über die Quellbasis; fachliches Element und Parent bleiben
im Schema getrennt zugänglich.

Der gemeinsame portable Vertrag enthält dieselben 19 bytegleichen Quellen
und alle Modellroots je Quelle. Der vorhandene Python-Testwrapper prüft die
Identität dieses eingebetteten Datenblocks mit den Quellpins. Diese Ergänzung
belegt die Annahme über die realen portablen Pfade; sie wird nicht als zweiter
öffentlicher Graphvertrag ausgegeben. Die detaillierten Modell-/Querywerte
werden im JVM-Vertrag geprüft.

## Ausgeführte Prüfungen und Grenzen

Alle 19 Einzelfallabnahmen sind geschlossen. Ausgeführt und erfolgreich:

- `check` mit **434 JVM-Tests**, SDK-Beispiel und Diagramm-/LSP-Verträgen;
- **16 Wrappertests**, darunter Byte-/Pinabgleich der portablen Originale;
- **1565 portable Fälle** pro dokumentiertem Laufzeitpfad: TeaVM-JavaScript und
  WasmGC unter Node, WasmGC in Chromium/Firefox/WebKit und echtem Safari,
  Web-Image unter Node und in Chromium/Firefox/WebKit;
- SDK-/CLI-Prozessprüfung mit neun Kommandos und Diagramm-TypeScript-Adapter;
- vollständiger eingefrorener Konformitätslauf: **574 PASS / 19 XFAIL /
  0 FAIL/ERROR/XPASS**, roh **574 PASS / 1 XFAIL / 18 FAIL**;
- **19 Originalpaare / 38 reguläre Prozessabschlüsse**: eigene JSON-Annahme
  ohne Diagnosen und ili2c-Annahme in jedem Fall, keine Prozessfehler/Timeouts.

`check` und der kombinierte Gradle-Lauf endeten mit Exit 0. Auch WasmGC
beendete seinen Prozess regulär. Die Rohreferenz bleibt sichtbar; ihr Exit 1
wegen der 18 Normabweichungen wird durch die expliziten Projektprüfungen
bewertet. Kein ursprünglicher Suitepin, keine Referenzerwartung und keine
Normausnahme wurde geändert. Es wurden **keine zusätzlichen Referenz-PASS**
erzeugt, sondern die 19 früheren fachlichen Abnahmelücken geschlossen.

[Verifikationsmanifest](verification-2026-10-22-b1-b3-originals.json) und
[Original-Prozessvergleich](b1-b3-original-reference-comparison-2026-10-22.json)
enthalten Einzelresultate, Kommandos, Werkzeugversionen, Hardware und
Quellen-/Artefakthashes. Die Rohberichte und Logs liegen unter
`build/reports/stabilization/b1-b3-original-*` sowie den im Manifest genannten
Modulpfaden. Reproduzierbarer Prozessvergleich nach gebauter Distribution:

```sh
python3 tools/reference/compare_original_views.py \
  --java-home /Users/stefan/.sdkman/candidates/java/25.0.3-tem \
  --ili2c /Users/stefan/.cache/interlis-java/ili2c.jar
```

Die Einzelfallabnahme erweitert keine Sprache um zusätzliche Formationen und
entscheidet keine neue Normausnahme. Die bestehenden 18 Normausnahmen bleiben
Projektentscheidungen mit ausstehender unabhängiger fachlicher Prüfung;
INTERLIS 1 bleibt separat ausgenommen. Offen bleiben insbesondere
`VIEW_TRANSLATED_IMPORT_CLOSURE`, `GEOMETRY_OVERLAP_PRECISION`,
`GEOMETRY_TYPE_COMPLETENESS`, `INSPECTION_CUSTOM_SEGMENT` und
`GRAPHIC_RESPONSIBLE_SIGNATURE_CLASS` sowie die übrigen S0–S6-Pflichten.
Keine vollständige P2-/P3-Normabdeckung, kein `releaseCheck`, keine Linux-/Windows-
Plattformabnahme und keine Editor-/Retention-/Abbruch-Performanceabnahme werden
mit dieser Lieferung behauptet. **P3 sowie G2/G3 bleiben offen.**
