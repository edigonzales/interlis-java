# K4 – gebundene Linienformen (Teilabnahme)

## Bearbeitete Katalogfälle

- `ili23.LineFormType23Test.lineFormTypeComplete`: eigene Deklaration, Strukturziel,
  Segmentvererbung und Übersetzung statt LINE_FORM-Sperre.
- `ili23.LineType23Test.lineTypeComplete`: gebundene Linienformen, Stützpunktdomain
  und Linienattributstruktur; Richtung und exakte Überlappungstoleranz bleiben erhalten.
- `ili23.LineType23Test.lineTypeLineFormOrdering`: Reihenfolge öffentlich erhalten;
  Übersetzung vergleicht korrespondierende Identitäten als Menge.

Zuordnung: WP-20/24/25; META-003/006/008/011/014/015, SEM-010/014/017,
SYN-004/013/015, TEST-011/012, VIZ-001/008. Keine neue Referenzausnahme.

## Implementiert

Der Parser liefert `LineFormDeclarationSyntax` und exakte `NameSyntax`-Referenzen
für WITH, VERTEX und LINE ATTRIBUTES. `LineFormSymbol` und `GeometryType` enthalten
die gebundenen Ziele. Der konkrete interne Helfer `LineForms` verwendet den
bestehenden Binder und denselben Graphzustand. Die bisherige Regex-Auswertung
von Linienformnamen/-Strukturzielen und der zusätzliche textuelle WITH-Vergleich
bei Übersetzungen sind entfernt. Es entsteht keine weitere Pass-Infrastruktur.

Eigene Linienstrukturen erben tatsächlich von INTERLIS.LineSegment. Dessen
SegmentEndPoint sowie ArcPoint/Radius, ABSTRACT/FINAL, Basiskanten und effektive
Reihenfolge sind im Schema erreichbar. Unbekannte oder falsche Ziele und
unzulässige FINAL-Erweiterungen sind Sprachfehler. 2.3 verlangt CONTRACTED,
2.4 nicht. Eine mehrfach genannte Form bleibt erlaubt und in Quellreihenfolge
sichtbar; für eine zusätzliche Ablehnungsregel besteht kein geprüfter Normbeleg.

Vordefinierte Segmenttypen und Einheiten besitzen gemeinsame Identitäten je
Sprachversion und `PredefinedOrigin`. Das war notwendig, damit importierte und
übersetzte Geometrien dieselben vordefinierten Ziele verwenden. Die Schemaauswahl
erhält diese Identitäten und ihre Mitglieder; sie nimmt keine fremden
quellgebundenen Inspektionsstrukturen auf. Ein eigener Auswahltest prüft ein
unabhängiges, wegen unvollständiger Geometrieinspektion abgelehntes Modell.

Quellqueries und Completion verwenden die Bindungen. Die Diagrammprojektion
zeigt deklarierte LINE_FORM-Knoten mit ihrer Segmentreferenz und die
Strukturvererbung. JSON-Schema 1.0 und TypeScript-Adapter erlauben diese Kind-Werte
bereits; ihre Pins bleiben unverändert. Migration und Spec 04/05/11/16/17 sind
synchronisiert.

## Norm und direkte Referenzvergleiche

Grundlage: [2.3-Referenz, §2.8.12.2–3 und Anhang A](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf)
und [2.4-Referenz, §3.8.12.2–3, Anhang A und Aufhebung der Kontrakte](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf).
Die Norm verlangt explizit eine Erweiterung von LineSegment. Eigene Ablehnung
einer gewöhnlichen Struktur bleibt deshalb korrekt, obwohl ili2c 5.6.9 diese
Gegenprobe in beiden Sprachversionen akzeptiert. ili2c verlangt in der zusätzlichen
2.4-Gegenprobe weiterhin CONTRACTED; unsere Annahme folgt dessen Aufhebung.
Diese drei Unterschiede betreffen keine eingefrorenen Suitefälle und erzeugen
keine zusätzliche XFAIL-Regel.

Die zwei feinen Toleranzproben zeigen einen bestehenden eigenen Fehler:
Koordinatenpräzision 0.000001 mit Toleranz 0.00001 wird wegen der festen
Compilergrenze 0.001 abgelehnt, von ili2c angenommen. Quellen, Pins und tatsächliche
Diagnosen stehen im Prozessbericht. Dieser unabhängige Fund erhält die Zuordnung
`GEOMETRY_OVERLAP_PRECISION` nach K6; er verdrängt die K4-Metadatenfälle nicht.
Die allgemeine Präzisions-/Vererbungsprüfung geometrischer Typen ist damit
weiterhin offen, keine vollständig abgenommene Typfamilie.

## Prüfungen und verbleibender Umfang

Verifiziert: 375 JVM-Tests, 15 Wrappertests und 1338 portable Fälle; Node/TeaVM,
Chromium/Firefox/WebKit/echter Safari, Web-Image und SDK/neun CLI-Prozesse bestehen.
Projektbilanz: 568 PASS, 17 XFAIL, 8 FAIL; roh 568 PASS, 1 XFAIL, 24 FAIL;
keine ERROR/XPASS. Der kombinierte Gradle-Lauf endet mit Exit 1 ausschliesslich
wegen `conformanceCheck`. 14 Originalfälle stimmen in 28 direkten Prozessen
vollständig mit ili2c überein; die 16 weiteren Gegenproben dokumentieren die
oben getrennt bewerteten Unterschiede. Kein vollständiger releaseCheck-,
Performance- oder Linux-/Windows-Nachweis wird hier beansprucht.

Der aktuelle Verifikationsstand steht im
[Manifest](verification-2026-10-21-k4-lines.json), die direkten Prozesse im
[Vergleichsbericht](k4-lines-reference-comparison-2026-10-21.json).
`LineFormBindingContractTest` prüft öffentliche Ziele, Mitglieder, Reihenfolgen,
Richtung/Toleranz, Quellnavigation, falsche Zielarten, Versionsregeln,
Übersetzungsziele, Importreihenfolge, Schemaauswahl und Fresh-vs-Reuse nach einer
Änderung des importierten Strukturziels. `DiagramProjectionTest` prüft die neuen
Knoten/Kanten; 30 zusätzliche portable Fälle prüfen beide Sprachversionen.

K4 bleibt offen für fünf Metadaten-/Koordinaten-/Numerikfälle und zwei
Geometrieinspektionen. Deren Sperre bleibt bestehen: Kontextualisierung von
LineCoord, vollständige StartSegment-Bedingung und Linienmetadaten sind noch
nicht vollständig abgenommen. Anschliessend folgen K5 (kombinierter Grafikfall)
und K6 (sechs Einheitenregressionen). Der übersetzte-Import-Fund bleibt separat
vorgemerkt. P3 und die weiteren S0–S6-Pflichten bleiben offen.
