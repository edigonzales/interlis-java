# Verbindliche Fortsetzung K1–K6

Der neue Auftrag ersetzt die bisherige Empfehlung, als Nächstes übersetzte
Importmodelle zu bearbeiten. Ausgangslauf: **556 PASS, 1 XFAIL, 36 FAIL**;
die sechs bekannten Einheitenregressionen bleiben zusätzlich verbindlich.
Aktuell nach K6 und dem Originalabgleich: **574 PASS, 19 XFAIL, 0 FAIL**,
keine ERROR/XPASS; roh **574 PASS, 1 XFAIL, 18 FAIL**. Die sechs zusätzlichen
K6-Einheitenregressionen bleiben geschlossen. Die 19 früheren B1–B3-Referenz-PASS
besitzen jetzt jeweils eine konkrete öffentliche Modell-/Queryabnahme anhand
der unveränderten Originalquelle, zusätzlich Wiederverwendungs-/Diagramm-
und portable Nachweise. Keine neue Referenzausnahme und kein neuer Referenz-PASS.
[Originalabgleich](b1-b3-original-acceptance.md),
[Manifest](verification-2026-10-22-b1-b3-originals.json).
Unabhängige Normprüfung, weitere Sprachfunde und P2/P3 bleiben offen.
Als Nächstes: übersetzte Imports, geometrische Toleranzpräzision und
Flächentyp-Vollständigkeit, eigene Inspektionssegmente, verantwortliche
Signaturklasse bei Wiederverwendung einer Basissignatur.
Die ursprüngliche Lieferung A–D und die älteren Abschnittsbilanzen unten bleiben
historische Zuordnungen; ihre damaligen nächsten Schritte werden durch diese
aktuelle Reihenfolge ersetzt.

| Lieferung | Verbindlicher Umfang | Abnahme |
|---|---|---|
| K1 | Neun bisherige Normabweichungen und fünf B-Restfälle. | Jede Ausnahme mit Originalpin, Normbeleg, exakter JSON-Diagnose und positiver Gegenprobe; rohe Referenzbilanz sichtbar. |
| K2 / C1 | Fünf Parameter-/Klassenfunktionsfälle. | Typisierte Parameter, getrennte Namensräume, PARAMETER/Klassenwerte/isOfClass/isSubClass/myClass; öffentliche Identitäten, Imports und Übersetzungen. |
| K3 / C2 | Sechs CONTEXT-Fälle. | Strukturierte Zuordnung, GENERIC/DEFERRED, keine globale Regex-Semantik. |
| K4 / C3 | Acht Metadaten-/Linienformfälle, danach zwei Geometrieinspektionen. | Gebundene Baskets/Metaobjekte/Referenzsysteme/Linienformen und vollständige Segmentstruktur; ALL-OF-Regel separat entscheiden. |
| K5 / C4 | Kombinierter roadsExgm2ien-Fall. | Grafikmodell und alle zusätzlichen Zielbindungs-/OID-Ursachen abschliessen. |
| K6 | Sechs Einheitenregressionen und Gesamtabnahme. | Im vereinbarten Fallumfang geprüft: exakte Faktoren, Null-/Divisionsprüfung, gebundene skalare Einheitenvererbung; vollständiger Konformitätslauf ohne ungeklärte FAIL/ERROR/XPASS. P2/P3 und unabhängige Normprüfung bleiben offen. |

P3 bleibt unabhängig davon offen. Unabhängige neue Funde erhalten Reproduktion
und Folgezuordnung; nur nachgewiesene Blocker eines Katalogfalls werden vorgezogen.
Referenzerwartungen und Quellenpins bleiben unverändert. Fehlende Implementierung
ist keine normative Ausnahme. Pro Lieferung folgen Original-/Gegenfälle,
öffentliche Modelltests, volle Konformität und JVM/TeaVM/Browser einschliesslich
Safari; zum Gesamtabschluss zusätzlich die Web-Image-Prüfung.

K1-Entscheidungen und Auswertungsvertrag: [Nachweis](k1-normative-expectations.md).

---

# Konformitätslieferungen A–D

Beauftragter Umfang: die 54 katalogisierten FAIL (45 Implementierungsfälle,
neun bisher dokumentierte Normabweichungen) und sechs Einheitenregressionen.
P2/P3 und G2/G3 bleiben offen. Anforderungen: WP-20–25,
META-003/006/008/010/011/013/014, SEM-005/008–011/014/017/019/020,
SYN-004/013/015, TEST-011/012.

## Reihenfolge und Abnahme

| Lieferung | Umfang | Abnahme / Stand |
|---|---|---|
| A | Unveränderten Korpus erneut prüfen; alle Fälle und Mehrfachursachen zuordnen. | Ausgangslauf 21.10.2026: 538 PASS, 1 XFAIL, 54 FAIL, 0 ERROR/XPASS, 593 Fälle. Gradle-Exit 1 wegen der fachlichen FAIL. Pins/Baseline unverändert. |
| B1 | Strukturierte View-Bindung, Projektionen, Selektionen, ALL OF, Ableitungen, Vererbung und Basiserweiterungen. | PARTIAL: strukturierte Bindung, Typen, Referenzen, Herkunft, Reihenfolge und Gegenfälle implementiert/geprüft. Restabnahme siehe View-Nachweis. |
| B2 | JOIN/OR NULL, UNION, AGGREGATION ALL/EQUAL. | PARTIAL: Formationen, Schemata, OR-NULL-/Unionregeln und mehrteilige EQUAL-Pfade implementiert/geprüft. Vollständige Abnahme offen. |
| B3 | Inspektionen, PARENT/THISAREA/THATAREA, Rollenpfade, abgeleitete Beziehungen. | PARTIAL: Strukturinspektion, Kontext-/Rollenpfade und abgeleitete Rollen gebunden. Struktur-Inspektionsfaktoren und OF-Einschränkungen geprüft; Geometrie bleibt gezielt gesperrt (C1/C3). Weitere Abnahmefälle offen. |
| C1 | Laufzeit-/Signaturparameter und Klassenfunktionen. | OPEN. Getrennte Namensräume, gebundene Typen, Imports und Übersetzungen. |
| C2 | CONTEXT, GENERIC/DEFERRED aus gebundener Syntax. | OPEN. Sechs Korpusfälle plus Gegenfälle; legale Aufschiebung erhalten. |
| C3 | Metadaten/Baskets/Metaobjekte, Referenzsysteme und Linienformen. | OPEN. Gebundene Ziele, Vererbung, Übersetzung, Reihenfolge. |
| C4 | GRAPHIC, Zeichenregeln, Signaturzuordnungen; kombiniertes Roads-Modell. | OPEN. Vollständige Modellinhalte ohne Renderer. |
| D | Sechs Einheitenfehler; fallweise normative Erwartungen und Schlussvergleich. | OPEN. Keine Implementierungslücke als Normabweichung verstecken. |

Fälle mit mehreren Features gehören zu mehreren Lieferungen. Geschlossen ist
jeweils der ganze Fall, nicht bloss eine entfernte Sperrdiagnose.
Grundlagenfehler, die eine Lieferung blockieren, werden darin behoben;
unabhängige Funde erhalten Reproduktion und eine explizite Folgelieferung.
Die sechs Fehler aus [Einheitenfolgeproben](units-follow-on-probes-2026-10-21.json)
bleiben verbindlich in D: Faktor null, Division durch null und Meter→Sekunde bei
numerischer Erweiterung, jeweils in 2.3/2.4.

## Norm- und Referenzerwartung

Historischer A–D-Plan: Die neun bisher begründeten Abweichungen sollten bis
zur Bewertung in D als rohe Referenz-FAIL sichtbar bleiben. Der neue K1-Auftrag
zieht diese Bewertung vor; die rohe Referenzbilanz bleibt weiterhin sichtbar. Sechs betreffen CONTRACTED/OID
in 2.3, je einer XMLDate-Breite, Assoziationsidentität in 2.3 und NO OID in 2.4.
Die bestehenden Fachbelege werden weitergeführt:
[OID](oid-followups-and-abstract-format.md),
[FORMAT](format-precision-and-oid-binding.md),
[NO OID](no-oid-reference-targets.md).
Die Zuordnung im [Fallkatalog](conformance-open-cases.json) ist keine neue
XFAIL-Baseline. Eine Normentscheidung benötigt eigenen Positiv-/Negativnachweis.

View-Grundlagen: Referenzhandbuch
[2.3 §2.15](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf)
und [2.4 §3.15](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf).
Syntax wird einmal erfasst. Gebundene Attribute, Typen und Ausdrücke werden
wiederverwendet; keine SQL-Engine, keine Geometrieauswertung, keine neue
Pass-Infrastruktur oder parallele API-Fassade.

## Evidenzpflege

Nach jeder Lieferung: geschlossene bisherige FAIL, verbleibende Ursachen,
zusätzliche Grundlagenkorrekturen und neue offene Fälle separat berichten.
JVM-Verträge, gemeinsame portable Fälle, Browser einschliesslich Safari,
Prozessvergleich mit ili2c 5.6.9 und vollständiger Konformitätslauf gehören zur
Abnahme; am Abschnittsende auch die aktuelle Web-Image-Untersuchung.
Fehlende Prüfungen bleiben ausdrücklich offen. Korpus-, Plattform-, CLI-/LSP-
und Performancepflichten aus S0–S6 werden hierdurch nicht ersetzt.

## Aktueller Zwischenstand

[View-Nachweis](view-bindings.md) und
[Verifikationsmanifest](verification-2026-10-21-views.json):
322 JVM-Tests und 1118 portable Fälle bestehen, einschliesslich echtem Safari
und der Web-Image-Untersuchung. Vollständiger Referenzlauf:
**556 PASS / 1 XFAIL / 36 FAIL / 0 ERROR / 0 XPASS**.
19 der ursprünglichen 54 FAIL sind jetzt Referenz-PASS, 35 bleiben offen.
Ein zusätzlicher C1-Fall (`myClass` in einer Attributableitung) ist neu sichtbar.
Die Fallabnahme bleibt von dieser Annahmebilanz getrennt; B1–B3 sind PARTIAL,
C1–C4/D OPEN. Die sechs Einheitenregressionen sind weiterhin offen.

Fortsetzung: [Inspektionsfaktoren](inspection-factors.md),
[damaliger Nachweis](verification-2026-10-21-inspections.json).
332 JVM-Tests und 1160 portable Fälle; die Referenzbilanz bleibt 556/1/36.
Neue Grundlagenkorrekturen und geprüfte Faktoren schliessen die verbleibenden
Geometrie-/Übersetzungs-/Vererbungsfälle noch nicht. Kein weiterer ursprünglicher
Suite-FAIL wurde in diesem Abschnitt geschlossen; P3 bleibt offen.

Historischer Nachweis vor K1: [View-Vererbung und Übersetzungen](view-inheritance-translations.md),
[Verifikationsmanifest](verification-2026-10-21-view-inheritance.json).
344 JVM-Tests und 1192 portable Fälle bestehen einschliesslich echtem Safari
und Web-Image; SDK und neun CLI-Prozesse bestehen. Der vollständige Korpus
bleibt bei 556 PASS / 1 XFAIL / 36 FAIL. Kein weiterer bisheriger Suite-FAIL
wurde geschlossen. Neu ausserhalb der Suite: fehlende Symbolkorrespondenz
übersetzter Importmodelle in 2.3/2.4, mit eigener/Referenz-Ausgabe reproduziert.
Dieser unabhängige Fund ist für einen Folgeabschnitt nach K1–K6 vorgemerkt.
Vollständige Übersetzungs-/Transferkombinationen, abgeleitete Assoziationsvererbung
und weitere Rollen-/Strukturmengenpfade bleiben ebenfalls offen. Geometrie bleibt an C1/C3 gebunden.
P3 bleibt offen.


## K2-Abnahme 21.10.2026

Die fünf katalogisierten Parameter-/Klassenfunktionsfälle sind im vereinbarten
Umfang geprüft: drei neue Referenz-PASS, zwei verifizierte CONTRACTED-Ausnahmen.
[Nachweis und verbleibende Pflichten](k2-parameters-class-functions.md).
Nächste Lieferung ist K3 (sechs CONTEXT-Fälle), danach K4, K5 und K6.


## K3-Abnahme 21.10.2026

Sechs CONTEXT-FAIL durch Implementierung geschlossen, keine neue Ausnahme.
Projektbilanz 565 PASS / 17 XFAIL / 11 FAIL. [Nachweis](k3-generic-contexts.md).
Nächste Lieferung K4, danach K5 und K6; P3 und die separat vorgemerkten Funde bleiben offen.


## K4/Linienformen – geprüfte Teilabnahme

Drei ursprüngliche FAIL geschlossen: `lineFormTypeComplete`, `lineTypeComplete`
und `lineTypeLineFormOrdering`. Keine neue normative Referenzausnahme.
Aktuell 568 PASS/17 XFAIL/8 FAIL, roh 568 PASS/1 XFAIL/24 FAIL, jeweils keine
ERROR/XPASS. 375 JVM-Tests, 15 Wrappertests, 1338 portable Fälle auf Node,
TeaVM-WasmGC, Chromium/Firefox/WebKit/echtem Safari und Web-Image sowie SDK/neun
CLI-Prozesse bestehen. Der kombinierte Gradle-Lauf endet mit Exit 1 allein
wegen der verbleibenden Konformitätsfälle.

Die 14 Originalfälle der Linienform-/Linientypfamilie stimmen in 28 direkten
Prozessen mit ili2c überein. 16 zusätzliche Versions-/Gegenproben unterscheiden
drei belegte Normabweichungen von ili2c und zwei eigene, separat vorgemerkte
Toleranzpräzisionsfehler. Sie erzeugen keine zusätzlichen Suite-XFAILs.
[Teilumfang und nächste Ursachen](k4-line-forms.md),
[Manifest](verification-2026-10-21-k4-lines.json).

Nächster Abschnitt bleibt K4: gebundene Baskets/Metaobjekte, Referenzsysteme,
Achsen und Klassenparameter. Anschliessend die vollständigen Inspektionsstrukturen
mit kontextabhängigen Koordinaten und StartSegment-Bedingung. K5/K6 und P3 bleiben
offen; der zusätzliche Toleranzfehler wird nach K6 bearbeitet.


## K4/Metadaten und Inspektionen – Katalogabnahme

Die sechs restlichen Metadaten-/Numerik-/Koordinaten-/PARENT-Fälle sind durch
Implementierung geschlossen. Der THISAREA-/THATAREA-Fall besitzt nach
vollständiger Geometriebindung genau eine Normablehnung (AREA ALL OF) und eine
geprüfte positive Kontrolle. Neue Projektbilanz 574 PASS / 18 XFAIL / 1 FAIL;
17 überprüfte Normausnahmen plus die separate INTERLIS-1-Ausnahme.

389 JVM-Tests, 15 Wrappertests und 1394 portable Fälle bestehen auf Node,
TeaVM-WasmGC, Chromium/Firefox/WebKit/echtem Safari sowie Web-Image. SDK und
neun CLI-Prozesse bestehen. Der kombinierte Lauf endet regulär mit Exit 1
allein wegen des K5-Konformitätsfalls. 32 Originalvergleiche, 56 Zusatzproben,
vier separat offene Vollständigkeitsreproduktionen und die AREA-Kontrolle sind
als 186 reale Prozesse aufgezeichnet. [Nachweis](k4-metadata-inspections.md),
[Manifest](verification-2026-10-21-k4-metadata.json).

Nächster Abschnitt K5: `roadsExgm2ien` einschliesslich SIGN-/METAOBJECT-Parametern,
GRAPHIC, ungelösten Basen/Pfaden und separater Prüfung jeder NO-OID-Diagnose.
Danach K6 mit den sechs Einheitenregressionen. Inspektion eigener Kurvensegmente
und konkrete Flächentypen ohne Überlappungstoleranz werden nach K6 neben den
bereits vorgemerkten Sprachfunden weitergeführt. P3 bleibt offen.


## B1–B3-Originalabgleich 22.10.2026

Alle 19 zuvor offenen Einzelfallabnahmen sind geschlossen, ohne neue
Referenz-PASS oder Ausnahmen. Die unveränderten Quellen sind gehasht und mit
Formation, Zielidentitäten, Typen, Herkunft, Reihenfolge und öffentlichen
Queries abgeglichen. Ein dabei gefundener notwendiger Indexierungsfehler
(Reihenfolge bei wiederverwendeten IDs sowie Modell-/Constraintanker) ist gezielt korrigiert.

434 JVM-Tests, 16 Wrappertests, 1565 portable Fälle auf den dokumentierten
Laufzeiten einschliesslich echtem Safari/Web-Image, SDK/neun CLI-Prozesse und
19 direkte Originalpaare mit ili2c bestehen. Vollständige Projektbilanz
574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS.
[Einzelnachweise und offene Pflichten](b1-b3-original-acceptance.md).
P2/P3 bleiben offen; die ältere A–D-Tabelle ist keine aktuelle Vollständigkeitsbilanz.
