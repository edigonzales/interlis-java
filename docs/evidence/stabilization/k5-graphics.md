# K5: Grafikfall roadsExgm2ien

## Auftrag und Fallzuordnung

Bearbeitet: `ili24.Refhb24Test.roadsExgm2ien`, der letzte offene Katalogfall nach
K4. Ausgangsnachweis: [K4-Manifest](verification-2026-10-21-k4-metadata.json),
Projektbilanz 574 PASS / 18 XFAIL / 1 FAIL, roh 574 PASS / 1 XFAIL / 18 FAIL.
Die Ursachen waren GRAPHIC-/Signatur-Sperren, verkürzte INTERLIS-Zielnamen,
qualifizierte geerbte Topic-Mitglieder, Objektpfade und NO-OID-Diagnosen.

Zuordnung: WP-20/23/24/25; META-003/006/008/010/011/014,
SEM-005/010/014/017, SYN-004/013/015, TEST-011/012, VIZ-001/008.
Erwartete Beobachtung: öffentlich nutzbare Grafiken und Zeichenregeln mit
gebundenen Parametern, Bedingungen und Werten. Eine verbleibende Normablehnung
darf ausschliesslich die belegten OID-Verstösse betreffen.

## Implementierung und notwendige Grundlagen

- Strukturierte `GraphicDeclarationSyntax` statt erneuter Regex-Auswertung
  vollständiger Modelltexte. WHERE ist für bedingte Klauseln erforderlich.
- `GraphicSymbol` mit Basis, Vererbung, geordneten Selektionen und lokalen sowie
  effektiven Regeln; `DrawingRuleSymbol` mit Signaturklasse, Override,
  Bedingungen und geordneten Zuweisungen. ACCORDING behält gebundenen Selektor,
  Enum-Pfade, Intervalle und Werte. Es gibt keine Ausführung oder Renderingengine.
- Signaturparameter verwenden `ParameterSymbol`; `MetaObjectType` erhält eine
  optionale OF-Zielklasse. METAOBJECT und Transferreferenzen bleiben fachlich
  getrennt. Vererbung, FINAL/EXTENDED, Typen, fehlende Pflichtparameter und
  falsche Objektklassen erhalten gezielte Diagnosen.
- INTERLIS.SIGN und INTERLIS.METAOBJECT behalten ihre vollständigen Syntaxnamen.
  Qualifizierte geerbte Topic-Mitglieder werden über deklarierte Basen gebunden,
  ohne Symbole zu kopieren. Modellqualifizierungspflichten für Grafikbasis und Signaturklasse werden geprüft;
  der bestehende EXISTENCE-Gegenfall sichert diese Grenze.
- Attribut-/Rollenzugänge verwenden den Grafik-Basiskontext; Funktionen,
  Laufzeitparameter und Metaobjekte den lexikalischen Grafikscope. DEPENDS ON
  wird anhand gebundener Topic-Identitäten geprüft.
- Gemeinsame Zuweisungsprüfung für abstrakte Koordinaten-/Geometrieparameter
  einschliesslich gebundener Stützpunktdomains; öffentliche Typqueries verwenden
  dieselbe Regel. Keine separate Typsemantik im Grafikhelfer.
- Übersetzungsvergleich erhält Basis-, Signatur-, Parameter-, Metaobjekt- und
  Ausdrucksidentitäten. Quellqueries navigieren auch von Zuweisungsparametern.
  Schemaauswahl behält sämtliche referenzierten Grafik-Ausdrücke.
- Diagrammprojektion erhält Grafik-/Zeichenregelknoten sowie Basis-, Signatur-,
  Besitzer- und Vererbungsbeziehungen. LSP-Completion und API-Migration sind
  angepasst. Keine neuen Module oder allgemeinen Abstraktionsschichten.

## OID-Entscheidung und positive Kontrolle

Der unveränderte Originalfall wird jetzt ausschliesslich wegen **49
REFERENCE_TARGET_NO_OID** abgelehnt: vier Diagnosen in `RoadsExdm2ben.ili`,
45 in `StandardSymbology.ili`. Keine Grafik-/Signatur-/Inspektionssperre und
keine ungelöste Zielbindung verbleibt in diesem Fall.

Normgrundlage: [INTERLIS 2.4, §3.5.2–3.5.3, §3.10.2.2 und §3.16](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf).
Ohne Klassen- oder Topiczuweisung gilt in 2.4 NO OID. Beziehungen und
Referenzattribute auf diese Klassen sind unzulässig. Der Parameter
`Sign: METAOBJECT` ist dagegen keine solche Transferreferenz. Eine Signatur
wird deshalb nicht allein wegen ihres impliziten NO OID verboten.

Die [Kontrollfassung](../../../testdata/conformance/normative/roadsExgm2ien/)
behält alle fünf Dateien, Grafikregeln, Inspektionen und Pfade. Ihre einzigen
inhaltlichen Änderungen sind zehn OID-Deklarationen:

1. STANDARDOID am Roads-Topic und am StandardSigns-Topic.
2. ANYOID an den vier abstrakten Signaturklassen als zulässige Erweiterung
   der impliziten NO-OID-Regel von INTERLIS.SIGN.
3. STANDARDOID an deren vier konkreten Erweiterungen.

Eine Topiczuweisung allein überschreibt die OID-Regel einer Klassenbasis
nicht. Daher enthält die Kontrolle explizite Deklarationen an den Klassen;
die bestehenden OID-Regeln wurden nicht abgeschwächt. Eigener Compiler und
ili2c 5.6.9 akzeptieren die Kontrolle. Der öffentliche Modelltest prüft darin
**fünf Grafiken, zehn Zeichenregeln und gebundene Assoziationspfade**.

Die bewusst versionierte [Normerwartung](../conformance/normative-expectations.json)
pinnt alle fünf Originale, alle fünf Kontrolldateien und genau die 49 erlaubten
JSON-Diagnosen einschliesslich Argumenten, Quellbereichen und Anzahl. Andere
Diagnosen, Prozessfehler, Implementierungssperren oder geänderte Pins verhindern
die Ausnahme. Die ursprüngliche Referenzerwartung und der rohe FAIL bleiben
unverändert sichtbar. Dies ist kein pauschales XFAIL für GRAPHIC.

## Zusätzlicher Referenzvergleich

54 Grafikproben werden in beiden Sprachversionen als echte Prozesse geprüft.
Alle 54 eigenen Erwartungen bestehen; 36 Entscheidungen stimmen mit ili2c
5.6.9 überein. Die 18 Unterschiede sind neun Gegenfälle je Version, die ili2c
akzeptiert und der eigene Compiler gezielt ablehnt:

| Probenindex je 27er-Versionsblock | Ursache / fachlicher Beleg |
|---|---|
| 4 | TEXT an numerischen Signaturparameter; Parameterdefinition und Typverträglichkeit. |
| 6 | Kein Wert für MANDATORY Pos in der konkreten Zeichenregel; notwendiger Signaturparameter. |
| 13 | Enumintervall auf ungeordneter Aufzählung; §3.16 erlaubt Intervalle nur geordnet. |
| 14 | Umgekehrtes Intervall c..a; kein gültiger aufsteigender Aufzählbereich. |
| 15 | TEXT statt Enumselektor bei ACCORDING; Syntax verlangt Enum-Attributpfad. |
| 16 | Nicht vorhandener Endknoten; keine Bindung im Enumtyp. |
| 20 | Erweiterung einer FINAL-Zeichenregel; FINAL verhindert Erweiterung. |
| 23/24 | Übersetzte Regel ändert Parameterwert bzw. Metaobjektidentität; Struktur-/Wertgleichheit der Übersetzung verletzt. |

Positive Kontrollen behalten jeweils denselben Kontext. Quellen, Hashes,
Befehle, Exitcodes und Diagnosen stehen im
[Vergleichsbericht](k5-graphics-reference-comparison-2026-10-21.json).
Die Zusatzproben erzeugen keine weiteren Suite-XFAILs. Ein erster Vergleich
hat die zu permissive Bindung von `Topic.Klasse` in Grafikreferenzen aufgedeckt;
Basis und Signatur verlangen nun `Modell.Topic.Klasse`. Vier portable Gegenfälle
sichern diese Korrektur. Der endgültige Bericht enthält die korrigierten Proben.

## Prüfungen und Grenzen

Verifiziert: **400 JVM-Tests, 15 Wrappertests, 1448 portable Fälle** auf
TeaVM-JavaScript/WasmGC unter Node, Chromium/Firefox/WebKit/echtem Safari sowie
Web-Image; SDK und neun CLI-Prozesse bestehen. Beide abschliessenden Gradle-Läufe
enden regulär mit Exit 0. Projektbilanz: **574 PASS / 19 XFAIL / 0 FAIL**,
roh **574 PASS / 1 XFAIL / 18 FAIL**, jeweils keine ERROR/XPASS. Die Projektbilanz
enthält 18 einzeln geprüfte Normausnahmen plus die separate INTERLIS-1-Ausnahme.
Es gibt keine erzwungene Prozessbeendigung als Erfolgsersatz.

Der aktuelle vollständige Lauf steht im
[Verifikationsmanifest](verification-2026-10-21-k5-graphics.json), einschliesslich
Quellbaumdigest, Artefakthashes, Werkzeugen, Hardware und exakten Kommandos.
Rohberichte liegen unter `build/reports/stabilization/`. Frühere Zwischenläufe
wurden durch die abschliessende Prüfung ersetzt. Ein unbenutzter alter
Namensheuristik-Helfer wurde entfernt.
`GraphicBindingContractTest` prüft beide Sprachversionen, öffentliche Inhalte,
gezielte Fehler, Vererbung, Übersetzung, importierte Signaturen, umgekehrte
Quellreihenfolge und frische gegen wiederverwendende Analyse. Die Projektions-
und gemeinsamen portablen Tests ergänzen die Verbraucherprüfung.

Zusätzlich wurden sechs Prozessepaare zur verantwortlichen Signaturklasse
geprüft (drei Varianten in 2.3/2.4). Der eigene Compiler akzeptiert ein Metaobjekt
der deklarierten abgeleiteten Signaturklasse, lehnt ein Objekt der Basisklasse
und einer fremden Signaturklasse ab; ili2c akzeptiert alle drei Varianten.
`GRAPHIC_RESPONSIBLE_SIGNATURE_CLASS` bleibt als neuer separater Fund offen:
§3.16 unterscheidet die deklarierte Signaturklasse von der anhand des Metaobjekts
und der verwendeten Parameter bestimmten verantwortlichen Klasse. Diese
Unterscheidung ist bei Wiederverwendung einer Basissignatur noch zu streng
gebunden. Die genaue Eigentümer-/Zielprüfung und Gegenfälle folgen nach K6;
die Annahme des fremden Metaobjekts durch ili2c ist dafür allein kein Normbeleg.
Alle sechs Quellen und Ergebnisse stehen als `openSignatureResponsibility` im
Vergleichsbericht. Für diesen Fund gibt es keine Referenzausnahme. Der geprüfte
Handbuchfall benötigt diese zusätzliche Kombination nicht.

K6 mit sechs Einheitenregressionen bleibt offen. Die separat vorgemerkten
Sprachfunde (übersetzte Imports, geometrische Toleranzpräzision, eigene
Inspektionssegmente und Vollständigkeit konkreter Flächentypen) werden nicht
zu Referenzausnahmen erklärt. Vollständige Normabdeckung, releaseCheck,
Linux/Windows, Performance-/Retention-/Abbruchbudgets und die übrigen
S0–S6-Pflichten sind hier nicht abgenommen. **P3 bleibt offen.**
