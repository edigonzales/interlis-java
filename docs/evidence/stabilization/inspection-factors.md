# Inspektionsfaktoren und weitere B1–B3-Gegenfälle

21. Oktober 2026. Fortsetzung des [View-Ausbaus](view-bindings.md).
WP-22/23; META-006/008/011/014, SEM-008/010/014, SYN-013/015, TEST-011/012.
B1–B3 und P3 bleiben teilweise umgesetzt beziehungsweise offen.
Fortsetzung: [View-Vererbung und Übersetzungen](view-inheritance-translations.md).

## Fachliche Entscheidung und Umsetzung

Die Norm beschreibt Inspektionsfaktoren als Mengen von Strukturelementen:
[2.3 §2.13/2.15](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf),
[2.4 §3.13/3.15](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf).
Eine OF-Einschränkung verlangt ein einzelnes Objekt der Ausgangsklasse oder
einer Unterklasse. Skalare und Objektmengen erfüllen diesen Vertrag nicht.

`InspectionExpressionSyntax` erfasst den gesamten Faktor einschliesslich
benannter Sicht oder Inline-Formation und optionalem OF-Pfad. Zuvor konnte
der Parser bei vorhandenem OF-Pfad die Inspektion vollständig übergehen.
`InspectionExpression` erhält Ausgangs-/Elementidentitäten, Zerlegungspfad,
optionale Sicht, Einschränkung und einen BAG-Typ. Die benannte Sicht bleibt
für Selektionen und Ableitungen erreichbar. Die Schemaauswahl erhält beide
Ausdruckskinder und ihre Typen. Es gibt keinen zweiten Pfadbinder.

Die View-Formation und der Faktor verwenden `ViewBuilder.bindInspection`.
Der Ausgangstyp, verschachtelte Elemente und Elternstruktur werden gemeinsam
gebunden. Der Referenzkontext bleibt der deklarierende Ort. Topic-Abhängigkeiten
werden erst nach Aufbau aller Topics geprüft; die frühere Prüfung während des
Aufbaus führte bei gültigem DEPENDS ON zu falschen Ablehnungen.

Ungültige PARENT-/Gebietskontexte sind Sprachfehler und erhalten keine
Implementierungssperre. Ein Pfad darf nach einem skalaren Wert den Kontext
nicht über PARENT wieder öffnen. Fehlende Geometrie-/Segmentinhalte bleiben
hingegen mit `INSPECTION_GEOMETRY_SCHEMA` ausdrücklich unvollständig.

## Weitere B1/B2-Korrekturen

- EXTENDED ohne geerbtes View-Attribut wird gezielt abgelehnt.
- Der FINAL-Gegenfall verwendet jetzt zusätzlich identische Typen; er prüft
  die FINAL-Regel unabhängig von einer unzulässigen Typverbreiterung.
- Übersetzungen vergleichen auch die gebundenen Referenzziele in
  Aggregationsschlüsseln, Inspektionspfaden und Basiserweiterungen. Zwei
  gleich typisierte Attribute dürfen dabei nicht vertauscht werden.

## Ausführbare Nachweise

`InspectionFactorContractTest` enthält zehn neue JVM-Testmethoden, jeweils
mit 2.3/2.4-Fällen. Geprüft werden öffentliche Modellinhalte, Ausdruckskinder,
BAG-Elementtyp, abgeleitete Inspektionen, negative Restriktionen, Mehrwertigkeit,
Navigation, importierte Quellen in beiden Dateireihenfolgen und frische gegen
wiederverwendete Analyse nach drei Quellständen. Ein geänderter bestehender
Pfadtest prüft nun eine Sprachdiagnose für PARENT ausserhalb einer Inspektion.

Die gemeinsame portable Sammlung erhält 42 zusätzliche Fälle.
`tools/reference/compare_views.py` prüft nun 94 Prozesspaare. Prozessfehler
werden ausdrücklich erfasst und weder als erwartete fachliche Ablehnung noch
als Referenzübereinstimmung gezählt. Bei einer geerbten Inspektionssicht
verursacht ili2c 5.6.9 in beiden Sprachversionen eine `ClassCastException`;
unsere Auflösung der geerbten Formation wird zusätzlich am öffentlichen Modell
geprüft. Insgesamt zeigen zehn Prozesse einen internen ili2c-Abbruch, darunter
acht negative Fälle mit zuvor scheinbar übereinstimmendem Exitcode. Bereinigt
bleiben 66 fachliche Übereinstimmungen und 18 abweichende Entscheidungen.
Unsere 94 Erwartungen bestehen ohne Prozessfehler. Weitere Unterschiede betreffen inkompatible OF-Einschränkungen,
Menge→TEXT und die isolierte FINAL-Regel. Suitepins und Referenzerwartungen
bleiben unverändert; dies ist keine neue XFAIL-Baseline.

Aktuelle Kommandos, Resultate, Werkzeugstände, Quellbaumdigest und Artefakthashes
stehen im [Verifikationsmanifest](verification-2026-10-21-inspections.json).
Der [Prozessvergleich](inspections-reference-comparison-2026-10-21.json) erhält
Quellen, Inhaltshashes, Ausgaben und Exitcodes. Der
[Fallkatalog](conformance-open-cases.json) trennt diese neuen Regressionen
von den ursprünglichen 54 Referenz-FAIL und dem zusätzlichen myClass-Fall.

## Verbleibende Abnahme

Inspektionsfaktoren auf Strukturmengen sind im genannten Umfang implementiert.
Noch offen bleiben vollständige Geometrieinspektionen (C1/C3), die vollständigen
Vererbungs-/Übersetzungs-/Transferkombinationen sowie weitere Rollen- und
Strukturmengenpfade. Die globalen Übersetzungsheuristiken sind durch die neuen
Identitätsprüfungen nicht vollständig ersetzt. Die übrigen View-Fälle mit
Textverengung, numerisch→Boolean, OID/HIDING oder Metadaten/GRAPHIC behalten
ihre eigenen Ursachen; dieser Abschnitt klassifiziert sie nicht um.
C1–C4/D, sechs Einheitenregressionen und die übrigen S0–S6-Pflichten bleiben offen.

## Resultat des vollständigen Laufs

332 JVM-Tests: PASS, keine Fehler/übersprungenen Tests. 1160 gemeinsame Fälle
unter TeaVM-JavaScript und WasmGC/Node sowie Chromium, Firefox, WebKit und
echtem Safari: PASS. Web-Image-Build, Node und drei Browser: PASS.
SDK-Beispiel und neun CLI-Prozesse auf macOS arm64: PASS.
Der kombinierte Gradle-Lauf endet regulär mit Exitcode 1 ausschliesslich wegen
der offenen Konformität: 556 PASS, 1 XFAIL, 36 FAIL, 0 ERROR/XPASS.
Kein bisheriger Suite-FAIL wurde in dieser Fortsetzung geschlossen; es gibt
auch keinen neuen Suite-FAIL. Keine vollständige Performance-/Plattformabnahme.
