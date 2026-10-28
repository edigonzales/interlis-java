# K4 – Metadaten, Achsen und Standard-Geometrieinspektionen

Stand 21.10.2026. WP-20/23/24/25; META-003/006/008/010/011/014,
SEM-005/010/014/017, SYN-004/013/015, TEST-011/012 und VIZ-001/008.
Diese Lieferung schliesst die sieben nach der Linienformlieferung verbleibenden
K4-Katalogfälle. Sie behauptet keine vollständige Sprach- oder P3-Abnahme.

## Fallbezogener Umfang

| Suite-Test-ID | Vorherige Ursache | Ergebnis dieser Lieferung |
|---|---|---|
| `ili23.CoordType23Test.coordTypeComplete` | Basket-/Metaobjektbindung und Achsendaten fehlten | Implementiert; Achsen behalten Einheiten, Referenzziele, Indizes und Rotation, Übersetzungen vergleichen Identitäten. |
| `ili23.NumericType23Test.numericTypeComplete` | Numerische Referenzsysteme waren Text statt gebundener Ziele | Implementiert; Domain-/Metaobjektbezug und Achsenindex sind öffentliche Modellwerte. |
| `ili23.MetaDataBasket23Test.metaDataBasketComplete` | Baskets, Vererbung, Objektklassen und Übersetzung fehlten | Implementiert; lokale Reihenfolge und geerbte Identitäten bleiben erhalten. |
| `ili23.Models23Test.models_ImportedModelReferenceElements` | Importierte Basket-/Topic-Ziele fehlten | Implementiert; gemeinsame Bindung und Navigation. |
| `ili23.InterlisManual23Test.ilimanual_CoordSysAppendix` | Vordefinierte Hierarchie, Axis und Unit-Parameter fehlten | Implementiert; METAOBJECT/REFSYSTEM/COORDSYSTEM/SCALSYSTEM/AXIS, effektive Mitglieder und typisierte Unit-Überschreibungen. |
| `ili23.View23Test.view_AcceptPARENTPathElement` | Geometrieinspektion war pauschal gesperrt | Implementiert; konkrete Segmenttypen und StartSegment-Bedingung, PARENT-Pfade gebunden. |
| `ili23.Expressions23Test.expressions_ThisAreaThatArea` | Geometriesperre und strittiges ALL OF | Geometrie implementiert; ausschliesslich `VIEW_ALL_OF` verbleibt als begründete Normablehnung. Positive Kontrolle entfernt nur ALL OF. |

Die sechs akzeptierten Originalfälle stimmen mit ili2c 5.6.9 überein. Die neue
AREA-Ausnahme ist in `docs/evidence/conformance/normative-expectations.json`
mit unveränderten Suite-Quellenpins, Normstelle, exakter JSON-Diagnose und einer
gepinnten positiven Kontrolle verankert. Ein beliebiger Fehler genügt nicht.
Die ursprüngliche Referenzerwartung bleibt sichtbar. Die bisherigen 16
Normausnahmen und die separate INTERLIS-1-Ausnahme bleiben erhalten.

## Fachmodell und Einfachheit

Der Parser erfasst Baskets, Objektgruppen, Klassenparameter, numerische
Referenzsysteme und Rotation strukturiert. `MetadataBindings` ist ein konkreter
interner Helfer des vorhandenen GraphBuilder; er besitzt keinen eigenen Lauf,
keine Resolverarchitektur und kein paralleles Typsystem. Die alten globalen
Metaobjekt-/Koordinaten-RegEx und der künstliche REFSYSTEM-Name-Pfad entfallen.
Die vorhandenen Unit-Beziehungen, Referenzen, Übersetzung und effektiven
Mitglieder werden weiterverwendet.

Die vordefinierten Typen besitzen kanonische Identitäten pro Sprachversion.
METAOBJECT enthält sein obligatorisches Name-Attribut und die tatsächliche
UNIQUE-Bedingung. Unit-Parameter enthalten aufgelöste Typen und Override-Ziele.
Die Koordinatenachsen verlieren weder Einheiten noch Referenzsysteme.
Ein Bezug ohne Achse auf eine Koordinatendomain mit ROTATION ist ein
Richtungsbezug; die zwischenzeitliche Ablehnung des gültigen
`iliconversion_CheckConversionOfDomainname` wurde behoben und separat abgesichert.

Inspektionen verwenden bestehende Structure-/Attribute-/Expression-Werte.
Kontextbezogene Segmenttypen behalten die kanonische Vorlage als Herkunft,
die lokale Vererbung und den wirklichen Stützpunkttyp. LineGeometry enthält
`isOfClass(Segments[FIRST], StartSegment)` als gebundenen Ausdruck. SurfaceEdge
und SurfaceBoundary behalten fachliche Reihenfolge und Optionalität.
`LINE ATTRIBUTES` ist nur in 2.3 zulässig; nur dort enthält SurfaceEdge LineAttrs.
Diese Versionskorrektur ersetzt auch die zu breite Annahme der vorherigen
Linienformtests. SIGN-Parameter und GRAPHIC bleiben bis K5 gesperrt.

Sprachabfragen erhalten die neuen Symbolarten und Referenzen; die LSP-Abbildung
verwendet dieselben Daten. Diagramme zeigen Baskets/Objekte, Referenz- und
Basiskanten sowie deklarierte Klassenparameter. Fehlende externe Topic-Knoten
werden als Referenzknoten aufgenommen, damit Kanten gültige Endpunkte besitzen.
Vorab-API 0.2, Migration, Fachkapitel 04/05/11, Register 16 und Matrix 17 sind
synchronisiert; keine parallele Fassade. Unverändert bleiben die tragenden
SyntaxSnapshot-/AnalysisSnapshot-/CompiledSchema-Verträge.

## Normprüfung und direkte Gegenproben

Grundlagen sind die [2.3-Referenz](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf)
und die [2.4-Referenz](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf):
Numerik/Koordinaten §§2.8.5/2.8.8 bzw. §§3.8.5/3.8.8, Metadaten/Parameter
§§2.10 bzw. §§3.10, Inspektionen §§2.15 bzw. §§3.15 und die vordefinierten
Modelle in Anhang A. 2.4 entfernt Linienattribute ausdrücklich im Vorwort und
in der SurfaceEdge-Deklaration. AREA-Inspektionen verbieten ALL OF ausdrücklich.

32 Originalfälle der betroffenen Familien und 56 zusätzliche Versions-,
Import-, Übersetzungs- und Gegenproben wurden als reale Prozesse mit ili2c
verglichen. Die Zusatzproben umfassen bewusst offene Fälle; ihre Ablehnung
wegen einer Implementierungssperre ist keine Konformitätsabnahme.
Die Unterschiede ausserhalb der eingefrorenen Suite sind einzeln eingeordnet:

| Zusatzprobe (Index je 28er-Versionsblock) | Eigene Bewertung und Normbezug |
|---|---|
| 3: Meter statt Winkeleinheit bei Richtung | Ablehnung: Richtungsbezug der rotierten Koordinatendomain, Numerik/Koordinaten. |
| 5/6: Achse 4 bzw. 0 | Ablehnung: einsbasierte Indizes und deklarierte Axis-Liste `{1..3}`. |
| 9: Koordinaten-Richtungsbezug ohne ROTATION | Ablehnung: die Rotation legt erst Nullrichtung und Drehsinn fest. |
| 10: Metaobjektname doppelt | Ablehnung: eindeutiger Objektname im Basket, METAOBJECT.Name/UNIQUE. |
| 15/16: Unit unverträglich bzw. fehlt | Ablehnung: bei spezifischem Unit-Parameter muss die numerische Einheit vorhanden und verträglich sein; ANYUNIT ist die ausdrücklich erlaubte Ausnahme. |
| 17: freier Parameter Other in SCALSYSTEM | Ablehnung: Referenzsystem-/Achsenparameter dürfen nur die vorgesehene Unit spezialisieren. |
| 20: AREA ALL OF | Normablehnung; gleiche Regel wie die neu geprüfte Suite-Ausnahme. |
| 22: eigene Kurvensegmente in Inspektion | Eigene Implementierungslücke; `INSPECTION_CUSTOM_SEGMENT`, keine Normausnahme. |

Die entsprechenden positiven Kontrollen und Gegenfälle werden in beiden
Sprachversionen geprüft. Die Zusatzvergleiche erzeugen keine weiteren Suite-XFAILs.
Quelltexte, Hashes, Befehle, Exitcodes und Diagnoseausgaben stehen im
[Vergleichsbericht](k4-metadata-reference-comparison-2026-10-21.json).

## Verifikation und verbleibende Grenzen

Verifiziert: **389 JVM-Tests, 15 Wrappertests, 1394 portable Fälle** auf Node,
TeaVM-JavaScript/WasmGC, Chromium/Firefox/WebKit/echtem Safari sowie Web-Image;
SDK und neun CLI-Prozesse bestehen. Projektbilanz **574 PASS / 18 XFAIL / 1 FAIL**,
roh **574 PASS / 1 XFAIL / 18 FAIL**, jeweils keine ERROR/XPASS. `check` endet
mit Exit 0; der kombinierte Lauf mit Exit 1 ausschliesslich wegen K5.

Die tatsächlich übernommenen Prüfungen, Quell-/Artefakthashes, Werkzeugversionen,
Hardware, Prozessabschlüsse und Konformitätsbilanzen stehen im
[Manifest](verification-2026-10-21-k4-metadata.json). Rohberichte und Logs liegen
unter `build/reports/stabilization/`. Zwischenläufe mit Fehlern wurden nicht als
Abnahme übernommen; insbesondere ist der zwischenzeitliche ClassNotFound-Fehler
während eines parallelen Distributionsumbaus kein fachlicher Referenznachweis.
Die abschliessenden Prozesse verwenden die fertig gebaute Distribution.

`MetadataBindingContractTest` prüft öffentliche Modellinhalte, Quellnavigation,
Einheiten/Achsen, Vererbung, Übersetzungen, Imports, umgekehrte Dateireihenfolge
und frische gegen wiederverwendende Analyse. `GeometryInspectionContractTest`
prüft Segmentherkunft/Hierarchie, Ausdrucksstruktur, tatsächliche Koordinatentypen,
Versionsunterschiede und die verbleibende Kurvensegmentsperre.
`DiagramProjectionTest` prüft Knoten, gültige Kanten und Parameterkompartimente.

Offen bleiben K5 (`roadsExgm2ien`: GRAPHIC, Signaturparameter, weitere Zielbindungen
und jede NO-OID-Diagnose einzeln) und die sechs K6-Einheitenregressionen.
Nach K6 folgen die vorgemerkten unabhängigen Sprachfunde: übersetzte Imports,
geometrische Toleranzpräzision, vollständige Inspektion eigener Segmente und
fehlende Vollständigkeitsprüfung konkreter Flächentypen ohne WITHOUT OVERLAPS.
Letzterer wurde mit 2.3/2.4-AREA/SURFACE-Proben reproduziert: eigener Compiler
akzeptiert, ili2c lehnt den unvollständigen Typ ab. Die Reproduktionen bleiben
separat im Vergleichsbericht erhalten; positive Inspektionstests verwenden eine
explizite Toleranz. Es entsteht dafür keine Referenzausnahme.

Vollständiger releaseCheck, reale Editor-/Retention-/Abbruchbudgets sowie
Linux-/Windows-Distribution und die übrigen S0–S6-Verpflichtungen sind hier
nicht abgenommen. **P3 bleibt offen.**
