# G2 C: Effektive Geometrie und eigene Inspektionssegmente

Stand: 25. Oktober 2026. **C hat einen geprüften Implementierungsumfang, aber
keine vollständige Abnahme; G2 und P3 bleiben offen.**

Zuordnung: WP-22/24/25, META-003/006/009/014,
SEM-005/008/010/014/017 sowie TEST-011/012. Die übrigen G2-Lieferungen D–G,
die vollständige Regelmatrix, detaillierte JVM-/TeaVM-Graphparität und reale
gefrorene Modellclosures bleiben erforderlich.

## Bearbeitete Katalogursachen

| Fund | Korrektur und aktuelle Grenze |
|---|---|
| `GEOMETRY_OVERLAP_PRECISION` | Feste Grenze `0.001` entfernt. Exakte positive Toleranz, 2.4-Präzision und unveränderliche etablierte Toleranz werden geprüft. Das alte 2.4-Original `0.00001` bei sechs Nachkommastellen ist normativ ungültig; die Kontrolle `0.000010` bleibt gültig. Die zusätzliche 2.3-Suitefrage unten bleibt offen. |
| `GEOMETRY_TYPE_COMPLETENESS` | Konkrete Formen/Vertexdomäne erforderlich; abstrakte und legale 2.4-GENERIC-Kontexte getrennt. 2.3-Flächen benötigen explizite/geerbte Toleranz; 2.4-Flächen dürfen implizite Spurbreite verwenden. Die historische pauschale Ablehnungserwartung für beide Versionen ist damit korrigiert. |
| `INSPECTION_CUSTOM_SEGMENT` | Beide unveränderten ursprünglichen Fälle liefern ein Schema mit kontextbezogenen geerbten und lokalen Mitgliedern. Die pauschale Sperre entfällt. Zusätzliche Modelltests erhalten Vererbung, Overrides, Ableitungen, Constraints, verschachtelte Strukturmengen, Herkunft und unabhängige fremde Ziele. Die gesonderte Quellabstraktionsfrage unten ist keine erledigte Normregel. |

Die acht historischen Reproduktionen liegen mit unveränderten Bytes unter
`testdata/conformance/g2-geometry/`; `pins.json` und der JVM-Vertrag prüfen ihre
Hashes. Ihre historischen Berichte werden nicht umgeschrieben.

## Fachliche und technische Umsetzung

`LineForms` stellt effektive Geometrieeigenschaften vor der Ausdrucksbindung
her. Aliasse/Basisdeskriptoren werden gelesen, nicht mutiert. Eine explizite
POLYLINE ohne DIRECTED bleibt ungerichtet; MANDATORY allein übernimmt den Typ.
Die gemeinsame Erweiterungsprüfung begrenzt Formen und Vertex, erlaubt
SURFACE → AREA und erhält etablierte Richtung und Toleranz. In 2.3 dürfen
Linienattribute entfallen oder ihre Struktur eingeschränkt werden. Compiler
und öffentliche `canExtend`-Abfragen verwenden dieselbe Regel.

`GeometryType.overlap()` enthält innerhalb API 0.2 jetzt `Tolerance` mit
`Explicit(DecimalLiteral)` oder `TrackWidth(SymbolId coordinateDomain)`.
Die symbolische Toleranz `q * sqrt(2) / 2` behält die bestimmende Domäne auch
bei Vererbung. Ohne konkrete Koordinatenpräzision wird noch keine implizite
Toleranz festgelegt. Zuweisung vergleicht positive Toleranzen nötigenfalls über
exakte quadrierte Grössen; eine gerundete Quadratwurzel wird nicht gespeichert.
Migration sowie Spezifikation 05/06/16/17 sind angepasst.

Ein konkreter interner `SegmentContextualizer` ersetzt die bisher gesperrten
Custom-Segmente und die getrennte Standardsegment-Kopierlogik. Er kopiert nur
abhängige Strukturen/Domänen und ihre gebundenen Inhalte pro Inspektionskontext.
Kanonische Vorlagen, unabhängige fremde Ziele und Quellreferenzen bleiben erhalten.
Die StartSegment-Bedingung bleibt ein gebundener Ausdruck. Zwei Inspektionen
desselben importierten Segments mit 2D-/3D-Vertextypen beeinflussen sich nicht.
Es gibt keine neue öffentliche Segmentfassade oder Pass-Infrastruktur.

Einheitenbeziehungen laden gebundene Einheiten bei Bedarf über den vorhandenen
Builder. Die neue 2.4-Kartesischprüfung darf gültige importierte Längeneinheiten
nicht wegen der Dateireihenfolge ablehnen. Umgekehrte Quellenreihenfolge und
mehrstufige Umrechnung sind ausdrücklich geprüft. Eine beliebige externe
CRS-Klassifizierung ist damit nicht belegt.

## Normative Einordnung und offene Ursachen

Gepinnte Quellen und Dokumenthashes stehen im Manifest:

- [2.3-Referenz, 2006-04-13](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf), §§2.8.12.2–3 (S. 49–51), 2.8.13.4 (S. 55).
- [2.4-Referenz, eCH-0031 2.1.0, 2024-04-24](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf), §§3.8.12.2–3 (S. 63–66), 3.8.13.4 (S. 71).

**`OVERLAP_PRECISION_23_NORM_REVIEW` bleibt offen.** Die unveränderte negative
Suiteprüfung `ili23.LineType23Test.overlapTooSmallFail` erwartet die
Ablehnung von `0.0001` bei drei Nachkommastellen der Koordinaten. ili2c 5.6.9
lehnt dies ab; der eigene Compiler akzeptiert. §2.8.12.2, S. 50, nennt gleiche
Einheit, positive und unveränderliche Toleranz, aber keine solche
Präzisionsuntergrenze. Die zusätzliche explizite 2.4-Regel wird nicht rückwirkend
auf 2.3 übertragen. Der Nutzer hat eine **separate Normprüfung ohne automatische
Ausnahme** verlangt. Originalerwartung und Pins bleiben unverändert; dieser
Fall bleibt im Projektlauf **FAIL**. Er wird weder XFAIL noch stiller PASS.

**`INSPECTION_LINECOORD_MEMBER_ABSTRACTION` ist separat E2/E3 zugeordnet.**
Zusätzliche Proben mit selbst deklarierten `middle/copy: INTERLIS.LineCoord` oder
verschachteltem `Data.p` werden vom eigenen Compiler akzeptiert, von ili2c wegen
abstrakter/unvollständiger Mitglieder abgelehnt. Die beiden ursprünglichen
Custom-Segmente mit eigenem TEXT-Mitglied akzeptieren beide Compiler. Die
Quellabstraktion zusätzlicher Mitglieder muss gegen §§2.5.3/2.6.1 bzw.
§§3.5.3/3.6.1 und die Segment-Kontextregel abgegrenzt werden. Die erfolgreiche
Kontextkopie allein entscheidet diese Quellregel nicht. Keine neue Suiteausnahme.

Die weiteren beobachteten Referenzunterschiede (Nulltoleranz, explizite
2.4-Präzision, implizite 2.4-Flächentoleranz, Kartesischprüfung und
Toleranzüberschreibung) sind mit vollständigen Prozessausgaben im
[Vergleich](g2-geometry-reference-2026-10-25.json) sichtbar. Aus einem
Prozessvergleich folgt keine pauschale Behauptung über sämtliche Normkonformität
von ili2c. Die 17 bestehenden normativen Suiteausnahmen benötigen weiterhin die
unabhängige Prüfung; das INTERLIS-1-XFAIL bleibt separat.

## Tatsächlich ausgeführte Prüfungen

- **471 JVM-Tests**, ohne Fehler/übersprungene Fälle; **16 Wrappertests**.
- **1676 gemeinsame portable Fälle** auf TeaVM-JavaScript und WasmGC/Node,
  Chromium, Firefox, WebKit und **echtem Safari**; Worker-Neustart und Weiterarbeit.
- Aktueller Web-Image-Build sowie Node- und Chromium/Firefox/WebKit-Prüfungen
  mit denselben 1676 Fällen. Web-Image bleibt Untersuchungsnachweis; TeaVM-WasmGC
  bleibt der Produktionspfad.
- SDK-Beispiel, neun CLI-/SDK-Prozessprüfungen und Diagramm-/Adapterprüfungen.
- **60 Prozesspaare** mit ili2c 5.6.9: alle eigenen festgehaltenen Erwartungen
  erreicht, 38 gleiche Referenzresultate. Offene normative Bewertungen sind
  ausdrücklich markiert; diese Zahl ist keine Normvollständigkeitsaussage.
- Eingefrorene 593 Fälle: **574 PASS / 18 XFAIL / 1 FAIL / 0 ERROR / 0 XPASS**.
  Rohvergleich: **574 PASS / 1 XFAIL / 18 FAIL**. Die XFAIL-Liste ist unverändert.

Der vollständige Gradle-Aufruf endet mit Exitcode 1. Ausschliesslich
`conformanceCheck` scheitert im abschliessenden Lauf am offenen 2.3-Fall;
**der Gesamtlauf wird nicht als erfolgreich gemeldet**. Normative Erwartungen,
Suite- und Quellenpins sind nicht verändert; keine neue XFAIL-Ausnahme.
Frühere technische Fehlversuche (TeaVM-Konstante, veraltete lokale
Sperrerwartung, Identitätsvergleich im neuen Edit-Test) bleiben in den
Build-Logs erhalten. Der Edit-Vertrag normalisiert snapshotlokale Symbol-IDs mit
dem bereits vorhandenen Testhelfer und vergleicht alle Recordkomponenten und
Reihenfolgen der Symbole, Typen und Ausdrücke.

[Verifikationsmanifest](verification-2026-10-25-g2-C.json),
[Fallkatalog](conformance-open-cases.json), [G2-Matrix](g2-rule-matrix.json).
Rohberichte liegen ausschliesslich unter Build-Ausgaben; das Manifest übernimmt
die geprüfte Evidenz mit Quellstand, Toolversionen und Artefakthashes.

Nächster unabhängiger Abschnitt ist D (`GRAPHIC_RESPONSIBLE_SIGNATURE_CLASS`).
Die offene C-Normentscheidung und die separat zugeordnete Quellabstraktionsfrage
bleiben sichtbar; ein grüner Teiltest ersetzt ihre Abnahme nicht.
