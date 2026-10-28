# EXISTENCE-Typfamilien und Collection-/Indexpfade

Stand 20. Oktober 2026; WP-20/22, META-006/014/016, SEM-008/010/014,
SRC-002–005 und LSP-012/013 im geprüften Teilumfang. P2/P3 bleiben offen.

## Bereichsauslegung

Grundlagen: [INTERLIS 2.3 §§2.12/2.13, S. 59–64](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf)
und [INTERLIS 2.4 §§3.12/3.13, S. 77–83](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf).

Die bisherige Implementierung verwendete für EXISTENCE die Zuweisungsprüfung:
Alle Werte des Quellbereichs mussten in den Zielbereich passen. Diese zusätzliche
statische Forderung wird zurückgenommen. Die Norm verlangt kompatible Attribute
und beschreibt das Vorkommen eines konkreten Wertes. Sie formuliert keine
allgemeine Zuweisung des gesamten Quellbereichs. Ihre Beschreibung für gleiche
oder erweiterte Wertebereiche ist keine ausdrückliche Verbotsregel für alle
anderen Intervallkombinationen. Insbesondere bedeutet Vererbung eines
Wertebereichs nicht einfach numerische Ausweitung seiner Grenzen.

Verwendete Auslegung: Numerische Grenzen und Textlängen allein verhindern
keine EXISTENCE-Deklaration. Ein nicht im Zielbereich vorkommender definierter
Wert verletzt die Datenbedingung. Auch disjunkte numerische Intervalle sind
daher kein Typfehler; daraus folgt keine Aussage, dass entsprechende Daten die
Bedingung erfüllen könnten. Das ist eine begründete Auslegung des Normtexts,
keine Behauptung eines dort ausdrücklich angegebenen vollständigen
Kompatibilitätsalgorithmus. Die Auslegung stimmt mit dem gepinnten
Referenzverhalten und dessen positivem Constraints24-Modell überein.

Die allgemeine Zuweisungsprüfung bleibt unverändert streng. Die Tests zeigen
für dieselben Zahlen-, Text- und Koordinatentypen erfolgreiche EXISTENCE und
abgelehnte Bereichszuweisung. Metadaten zu Einheiten, Richtung und Bezugssystem,
Koordinatenform sowie Strukturidentität werden weiterhin berücksichtigt.
Diese Teilprüfung ersetzt keine vollständige Einheiten-/Umrechnungsabnahme.

## Implementierter Teilumfang

Der bestehende Binder erhält die Auswahl als `PathIndex` und erzeugt nach dem
Attribut einen `PathStep.IndexStep`. Keine zweite Pfadsemantik, kein neuer Pass.
LIST-Auswahl führt mit dem Strukturelementtyp fort; Achsenauswahl liefert den
exakten numerischen Achsentyp. Index, Zwischentyp und Quellbereich bleiben im
Schema. Navigation/Rename beziehen sich weiterhin nur auf den Attributnamen.
Übersetzungsformen unterscheiden Auswahlart und Position.

Die einbasierte Auswahl prüft die statische Obergrenze. FIRST/LAST gelten für
LIST; BAG besitzt keinen solchen Zugriff. Indizes oberhalb der Mindestanzahl
bleiben erlaubt und können undefiniert liefern. Grosse Positionen werden exakt
gehalten; eine unbegrenzte LIST erhält keine künstliche `long`-Grenze. Ein mehrwertiger
Rollenpfad wird nicht durch eine nachfolgende Indexauswahl zu einem Einzelwert.

EXISTENCE unterstützt zusätzlich gleiche Textausrichtungen, gleiche Blackbox-
Varianten, gleiche Datum-/Zeitarten und gleich aufgebaute Aufzählungsbäume mit
gleichen Ordnungsmerkmalen. Variantenunterschiede bei Ausrichtung, Blackbox und
Zeit sind Typfehler. Singleton-Struktur-Collections können mit Einzelstrukturen
verglichen werden; ein explizit selektiertes LIST-Element behält seine
Strukturidentität. UNIQUE erlaubt dadurch keine Struktur als Endschlüssel.

## Reproduzierter Referenzvergleich

[56 Fälle je Compiler](existence-reference-comparison-2026-10-20.json) enthalten
Originaleingaben, SHA-256, Kommandos, Exitcodes und Ausgaben. Referenz ist
ili2c 5.6.9-bf16026e83f2377153b2e818a4f3838f0a423722. Der untersuchte Quellstand
ist der vorhandene Pin `4ea207f388ef7d91b03d968cc1636f4de84e36ea`.

- Bereichsvergleiche stimmen nun überein, einschliesslich disjunkter Intervalle.
- Die Referenz prüft in `ExistenceConstraint.addRequiredIn` überwiegend die
  Java-Typklasse. Unterschiedliche Ausrichtungs-/Blackbox-/Zeitvarianten und
  Koordinatendimensionen werden dort akzeptiert; unser Vergleich unterscheidet
  diese fachlichen Wertearten. Unterschiedliche Enum-Bäume bleiben bei uns
  vorerst eine Implementierungslücke, keine behauptete allgemeine Ungültigkeit.
- `parser/interlis24.g:attributeRef` konstruiert Strukturindizes ohne Prüfung
  von Ordnung und Obergrenze. Reproduziert: BAG[FIRST], LIST[0] und LIST[4] bei
  maximal drei Elementen werden von ili2c akzeptiert, hier normgemäss abgelehnt.
- Der Referenzparser verwirft bei Koordinaten `idx >= dimensions.length`;
  `AxisAttributeRef.getDomain` verwendet zusätzlich den einbasierten Index
  direkt als Arrayindex. Die zweite Achse einer zweidimensionalen Koordinate
  scheitert in beiden Sprachversionen. Hier sind beide Achsen korrekt zugänglich.
- Sehr grosse gültige LIST-Indizes scheitern im Referenzprozess an dessen
  Ganzzahlgrenze. Hier bleibt der Wert exakt und innerhalb der Quellbudgets.

Die alten 14 Vergleiche und das Constraints-Verifikationsmanifest bleiben als
historische Evidenz erhalten. Suitepins, Baseline und XFAILs werden nicht geändert.

## Abnahme und offene Arbeit

`ConstraintPathBindingContractTest` prüft zusätzlich Typen, Zwischenwerte,
Schema-Closure, Navigation/Rename, Bereichsabgrenzung zur Zuweisung sowie
negative Auswahlfälle. 230 JVM-Tests und 394 gemeinsame portable Fälle bestehen:
TeaVM-JavaScript/WasmGC unter Node 24.22.0, tatsächliche Worker in Chromium,
Firefox, WebKit und Safari sowie Web Image unter Node und den drei Playwright-
Browsern. SDK-Beispiel und neun Distributionsprozesse bestehen auf macOS arm64.
Konformität: 547 PASS, 1 XFAIL, 45 FAIL, keine ERROR/XPASS; der kombinierte Lauf
endet regulär mit Exitcode 1 nur wegen der offenen Konformitätsfälle.
Nicht ausgeführt: vollständiges `releaseCheck`, Performance-/Retentionabnahme
und Linux-/Windows-Distribution. Tatsächliche Kommandos und Hashes stehen im
[neuen Verifikationsmanifest](verification-2026-10-20-existence.json).

Noch offen in WP-20/22: vollständige Enum-Vererbung und Übersetzung,
FORMAT-Strukturbindung, OID-Details, weitergehende Geometrievergleiche,
Einheiten-/Bezugssystemvollständigkeit, ganze mehrwertige Collection-Endwerte
(`EXISTENCE_COLLECTION`) und allgemeine Mengen-/Inspektionspfade. Nicht
modellierte Familienbeziehungen bleiben `EXISTENCE_TYPE_COMPATIBILITY`.
Die neuen Positivfälle sind keine pauschale Freigabe ihrer gesamten Typfamilie.
Auch Constraint-Kontextregeln für OBJECTS-Funktionen, Views und S2c bleiben
separate Pflichtarbeit. Performance-/Retention- und Plattformabnahmen wurden
hier nicht ersetzt; G2/G3 bleiben offen.
