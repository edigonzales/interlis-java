# Strukturierte View-Bindung: Zwischenlieferung A/B1/B2/B3

Fortsetzung und aktueller Nachweis: [Inspektionsfaktoren](inspection-factors.md).
Die folgende Zwischenlieferung bleibt als damaliger Stand erhalten.

21. Oktober 2026. **Keine vollständige Abnahme von B1–B3 oder P3.**
A bestätigt den unveränderten Ausgangslauf 538 PASS / 1 XFAIL / 54 FAIL.
Der aktuelle [Fallkatalog](conformance-open-cases.json) behält alle ursprünglichen
54 Fälle samt damaliger Diagnose und ergänzt die aktuelle Beobachtung.
Referenz-PASS und vollständige fachliche Abnahme werden getrennt ausgewiesen.

## Implementiert und gezielt geprüft

- Strukturierte Syntax und geschlossene Formationen ersetzen die bisherige
  View-Textauswertung. Ein konkreter interner `ViewBuilder` verwendet dieselben
  Typ-, Ausdrucks-, Pfad- und Referenzbinder wie übrige Deklarationen.
- Projektionen, Selektionen, lokale Basen, ALL OF, Attribute samt
  Ableitungsausdrücken, Constraints, Vererbung und Basiserweiterungen erhalten
  gebundene Identitäten, Typen, Herkunft und Reihenfolge.
- JOIN mit optionalen Basen, UNION und AGGREGATION ALL/EQUAL sind typisiert.
  EQUAL erfasst mehrere Pfade. AGGREGATES bleibt ausserhalb der transferierten
  Mitglieder. Nichtboolesche Selektionen, Alias-/Attributkollisionen,
  inkompatible Ableitungen und obligatorische Werte aus optionalen Basen
  erzeugen gezielte Diagnosen.
- Strukturinspektionen erhalten Element- und Elternidentitäten. PARENT und
  die Gebietskontexte sind explizite Pfadschritte. Projektionen behalten die
  Rollenpfade ihrer Ausgangsklasse/-assoziation. Abgeleitete Assoziationen
  erhalten eine gebundene Basis und typgeprüfte Rollenzuweisungen.
- Diagramme zeigen View-Vererbung, Formationsbasen, abgeleitete Beziehungen
  und effektive Attribute. Imports, umgekehrte Dateireihenfolge, Aliasnavigation,
  Rename sowie frische/wiederverwendete Analyse nach Änderungen sind geprüft.

Die neuen JVM-Nachweise liegen in `ViewBindingContractTest` und
`DiagramProjectionTest`, gemeinsam portable Fälle in
`interlis-wasm-teavm/stabilization-contract.cjs`. Sie ersetzen weder den
vollständigen Normkatalog noch sämtliche Kombinationen einer Formation.
Anforderungen: WP-20–25, META-003/006/008/010/014, SEM-005/008–011/014/017/019/020,
SYN-004/013/015, TEST-011/012. API 0.2 sowie Spec 04/05/16/17 sind synchronisiert.

## Gefundene Grundlagenfehler

Die Referenzprüfung nahm bisher an, jedes effektive Strukturmitglied sei im
Quellindex enthalten. Synthetische Inspektionsstrukturen lösten dadurch einen
internen Fehler aus. Die Prüfung findet jetzt ihren deklarierenden
Quellkontext über die Besitzerkette und prüft weiterhin die tatsächlichen
Mitglieder. Der Regressionstest kontrolliert auch die partielle Analyse.

Abgeleitete Attribute ausserhalb von Views werden nun ebenfalls gebunden.
Damit ist `ili23.Attributes23Test.attributePathType_ClassTypeRestriction` neu
als FAIL sichtbar: `INTERLIS.myClass(a)` war vorher übergangen worden.
Dies ist **kein geschlossener Fall**; C1 muss Klassenwerte und die drei
zusammengehörigen Builtins vollständig binden. Eine feste Erfolgsantwort
würde den Fehler wieder verbergen.

## Noch offene Ursachen und Folgearbeit

1. **B1/B2 abschliessen:** weitere Vererbungs-/Überschreibungskombinationen,
   strukturidentische Übersetzungen mit unterschiedlichen Referenzzielen,
   Abstraktion und die vollständige Fallabnahme. Die ergänzte Übersetzungsbindung
   ersetzt noch nicht alle bisherigen globalen Übersetzungsheuristiken.
2. **B3:** Inspektionsfaktoren innerhalb von Ausdrücken und vollständige
   Strukturmengen-/Spezialpfadkombinationen. Geometriestrukturen bleiben mit
   `INSPECTION_GEOMETRY_SCHEMA` gesperrt: vordefinierte Segmenthierarchie,
   StartSegment-Bedingung und gebundene Linienmetadaten fehlen. Teilstrukturen
   sind in der Analyse navigierbar, ergeben aber kein `CompiledSchema`.
   Die Abhängigkeiten zu C1/C3 sind ausdrücklich im Katalog erfasst.
3. **Verbleibende View-Fälle:** Textverengung in
   `view_AcceptBasicViewProjectionDef` und `expressions_ParentPathElement`,
   numerisch→Boolean in `iliconversion_CheckViewDefWithALLOF`, verbotenes ALL OF
   bei `expressions_ThisAreaThatArea`, OID-Regeln in `assocComplete`, HIDING in
   `roleComplete` sowie die Metadaten-/Grafik-/Importkombination des Roads-Falls.
   Diese Ursachen bleiben sichtbar und sind nicht pauschal erwartete Abweichungen.
4. **C1–C4 und D bleiben offen:** Parameter, Klassenfunktionen, CONTEXT,
   Metadaten, Linienformen, GRAPHIC, sechs numerische Einheitenregressionen und
   die fallweise normative Bewertung. Die neun bisher dokumentierten
   Normabweichungen bleiben unverändert rohe Referenz-FAIL.

## Norm und Prozessvergleich

Grundlagen sind [2.3 §2.13–2.15](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf)
und [2.4 §3.13–3.15](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf).
Die Regeln verlangen insbesondere passende Ableitungstypen und verbieten
obligatorische Joinwerte aus Leerobjekten sowie unvollständige obligatorische
Unionzuweisungen. Die Kurzform einer Attributableitung ist final.

`tools/reference/compare_views.py` führt dieselben 52 neuen portablen Fälle
als reale Prozesse mit unserer CLI und ili2c 5.6.9 (`-o0`) aus. Jede Quelle,
Hash, Erwartung, Ausgabe und jeder Exitcode stehen im übernommenen
[Vergleich](views-reference-comparison-2026-10-21.json).
Unterschiede betreffen die genannten Join-/Union-/FINAL-Regeln,
inkompatible Teilnehmer bei Rollenzuweisungen und die bewusst noch gesperrte
Geometrieinspektion. Der letzte Punkt ist eine Implementierungslücke,
**keine Normabweichung**. Der FINAL-Gegenfall enthält zugleich eine
Typverbreiterung; für D ist zusätzlich ein isolierter FINAL-Gegenfall erforderlich.

Der [aktuelle Nachweis](verification-2026-10-21-views.json) enthält Quellbaumdigest,
Artefakthashes, Werkzeuge, Kommandos und Rohresultate. Ein Zwischenlauf nach
Ergänzung der Geometriesperre hatte noch ältere Artefakte geladen und ist
explizit als überholt gekennzeichnet. Nur der abschliessende vollständige
Neulauf gilt für den aktuellen Stand. Keine Pins, Suiteerwartungen oder XFAILs
wurden angepasst. Kein Release und keine vollständige Performance-/Plattformabnahme.
