# FORMAT-Präzision, verschachtelte Grenzen und OID-AS-Bindung

Stand: 20. Oktober 2026. Teilumfang S2a/S2b, WP-20/22;
META-006/014/016, SEM-008/010/014, SRC-002–005, LSP-012/013.
P2/P3 und G2/G3 bleiben offen. Dieser Folgenachweis ersetzt keine vollständige
Normmatrix. Der [vorherige Stand](enum-format-oid-collections.md) bleibt als
historischer Nachweis erhalten.

## Fachliche Grundlage

Grundlagen sind die [2.3-Referenz, 13. April 2006](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf)
und die [2.4-Referenz, eCH-0031 V2.1.0](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf).
2.4 §§3.8.5–6 bestimmen numerische Genauigkeit, Auffüllung und die Struktur
formatierter Werte. Die Interpretation für FORMAT lautet: exakte Anzahl
Nachkommastellen der numerischen Komponente, mindestens die mit `/n`
verlangten Vorkommastellen und bei skalierten Komponenten normalisierte
Mantisse mit Exponent. Vorzeichen zählt nicht zur Stellenzahl. Vergleiche
verwenden exakte Dezimalwerte, keine Gleitkommarundung.

Verschachtelte Komponenten erfüllen ihre eigenen Bereichsgrenzen; die
Basiskette bleibt auch bei angehängten FORMAT-Teilen wirksam. Der gemeinsame
numerische Präfix bestimmt die Vererbungsverengung. XMLDate verlangt
zweistellige Monate/Tage (§4.3.11.5). XMLTime/XMLDateTime erlauben im hier
geprüften Profil Sekunden ohne Bruch oder mit bis zu drei Nachkommastellen
(Anhang A, XMLTimeSeconds); dieses Profil ersetzt keine vollständige
XML-Schema-/Kalenderprüfung.

OID-Zuordnungen beziehen sich auf Domänenidentitäten (§§3.5.2–3, 3.8.9).
Basket- und Objektregel sind verschieden. Klassen dürfen den Topic-Default
explizit ersetzen; eine geerbte Klassenzuordnung bleibt verbindlich.
NO OID → ANYOID → konkreter OID-Typ sind die vorgesehenen Schritte.
ANYOID verlangt eine abstrakte Deklaration. In **2.3** verlangt OID AS
CONTRACTED (§2.5.2); **2.4** hat die Vertragspflicht aufgehoben.

## Umsetzung und öffentliche Verträge

- `FormattedValues` interpretiert gebundene FORMAT-Teile. Deklarationsgrenzen
  und Ausdruckskonstanten verwenden denselben Prüfer. Verschachtelung und
  Basisketten werden mit Zyklenschutz und Arbeitsbudgetprüfungen durchlaufen.
  Der frühere pauschale Guard `FORMAT_NESTED_BOUNDS` entfällt.
- `DecimalLiteral` bewahrt die Nachkommastellen der lexikalischen Mantisse:
  `1.2e3` hat eine, `0.12e+3` zwei. Exponent und exakter Wert bleiben separat.
  Das bisherige Entfernen sämtlicher Pluszeichen entfällt; `1+2` ist ungültig.
  Der alte Modelltest mit Erwartung null Nachkommastellen wurde anhand dieser
  Bedeutung korrigiert, nicht zur Anpassung an einen gerundeten Wert.
- `OidRuleSyntax` bewahrt den deklarierten Namen und UTF-16-Quellbereich.
  `OidRule` enthält das gebundene Domänenziel und die ursprüngliche Herkunft.
  `SymbolHeader.oidRules()` enthält effektive Regeln, Basket vor Objekt.
  Keine Textsuche in verschachtelten Deklarationen; ungelöste Namen werden
  nicht als NO OID ausgegeben.
- OID-Regeln von Klassen, Topics und Assoziationen werden nach der
  Vererbungsbindung aufgelöst. Klassen übernehmen ausschliesslich die
  Objektregel des Topics. Konkrete Regeln dürfen nicht gegen eine andere
  Domäne ausgetauscht werden. Auch implizites geerbtes NO OID wird geprüft;
  Strukturen erhalten keinen OID-Default.
  Eine abstrakte Klasse verlangt eine konkrete Spezialisierung im Topic
  oder einen abstrakten Topic; eine unabhängige konkrete Klasse genügt nicht.
  Diesen echten Fehler deckte der erste Referenzvergleich auf. Die neue
  positive Variante setzt den Topic ausdrücklich abstrakt, die ursprüngliche
  negative Variante wird jetzt von beiden Compilern abgelehnt.
- Assoziationskardinalität wird nach der OID-Bindung geprüft. Ein gebundenes
  OID AS erfüllt die Identifikationsvoraussetzung mehrfacher Links; NO OID
  tut dies nicht. Der bestehende OID-Modifikator bleibt berücksichtigt.
- Navigation und Rename greifen auf dieselben Namensreferenzen zu. Die
  Schema-Closure enthält die OID-Domänen. Übersetzungen vergleichen
  korrespondierende Domänenidentitäten, auch bei gleichen Trägertypen;
  vordefinierte Domänen werden über ihre kanonische Identität abgeglichen.
  Der bestehende Übersetzungsvergleich respektiert jetzt das exklusive
  Modellende auch bei direkt anschliessenden Modellen. Seine übrige
  Token-/Quelltext-Ersatzsemantik bleibt ein offener S2c-Punkt.

Es entstehen zwei fachliche Records und ein interner FORMAT-Helfer; keine
neuen Module, Pass-Infrastruktur oder parallele API-Fassaden. Die noch nicht
veröffentlichte API 0.2 und Spec 04/05/16 sind gemeinsam aktualisiert;
[Migration](../../api-migration-0.2.md) beschreibt den Konstruktor-/Accessorbruch.

## Prüfungen und Referenzabweichungen

Die abschliessenden Resultate und exakten Kommandos stehen im
[Verifikationsmanifest](verification-2026-10-20-format-oid.json).
Der letzte Lauf besteht mit **248 JVM-Tests und 599 portablen Fällen**,
einschliesslich echtem Safari. Web Image und WasmGC enden regulär.
`conformanceCheck` bleibt rot: **540 PASS, 1 XFAIL, 52 FAIL, 0 ERROR/XPASS**;
neun SDK-Prozesse bestehen auf macOS arm64. Vollständiges `releaseCheck` und
die Performance-/Plattformabnahme wurden nicht durchgeführt.

`FormatPrecisionOidBindingContractTest` prüft positive und negative Fälle in
beiden Sprachversionen sowie Typmetadaten, Domänen-IDs, Herkunft, Closure,
Navigation/Rename und Übersetzung. Die zusätzlichen portablen Fälle laufen
auf TeaVM-JavaScript, WasmGC und Web Image sowie in den realen Browserpfaden.

Die [Prozessvergleiche](format-oid-reference-comparison-2026-10-20.json)
enthalten 98 Fälle je Compiler: **65 übereinstimmende und 33 abweichende
Ergebnisse**, keine unerwarteten Ergebnisse des aktuellen Compilers. Vollständige
Quellen, Inhaltshashes, beide Kommandos, Exitcodes und Diagnosen sind enthalten. ili2c 5.6.9 bleibt unverändert gepinnt. Abweichende Fälle werden
individuell bewertet. Insbesondere toleriert die Referenz verschiedene
lexikalische Präzisions-/Auffüllfehler, unzulässige OID-Wechsel und fehlendes
CONTRACTED. Umgekehrte Grenzen oberhalb 2^53 werden im neuen Compiler exakt
abgelehnt. Ein normgerechtes verschachteltes FORMAT-Beispiel löst bei ili2c
einen Indexfehler aus; skalierte FORMAT-Lexeme werden dort abgelehnt.

In der unveränderten externen Suite kommen gegenüber dem vorherigen Stand
sieben Ablehnungen hinzu: sechs 2.3-Fälle mit OID AS ohne CONTRACTED sowie
`FormattedType23Test.simple + baseDomainRestricted` mit ungepolsterten
XMLDate-Grenzen. Pins, Erwartungen und XFAILs wurden nicht verändert.
Der [aktuelle Fallkatalog](conformance-open-cases-2026-10-20-format-oid.json)
trennt diese Normabweichungen von fehlender Implementierung. Das rote Gate
wird nicht durch Umdeklaration der Erwartungen verborgen.

## Offene Punkte und nächster Abschnitt

- Vollständige FORMAT-Relationen, Einheiten-/Referenzsystemkombinationen und
  Erweiterungen numerischer Komponenten sind noch nicht abgenommen.
  Abstrakte numerische FORMAT-Komponenten bleiben als
  `FORMAT_NUMERIC_PRECISION` gesperrt. Vordefinierte interne Strukturen sind
  noch nicht vollständig als gewöhnliche gebundene Strukturen verfügbar;
  deren Verwendung als verschachtelte FORMAT-Basis ist gesondert zu ergänzen.
- Kalendergültigkeit, Zeitzonen und alle XMLTime-Lexemvarianten sind kein
  Ergebnis dieses Compilerabschnitts. Zahlenbreiten werden ohne entsprechende
  Speicherallokation verglichen; Abbruchlatenz und Aufwand adversarialer
  FORMAT-Eingaben sind noch nicht gemessen.
- Die vollständigen OID-Folgeregeln für Referenzen auf Klassen ohne stabile
  Identität und alle Topic-/EXTENDED-/Übersetzungskombinationen bleiben offen.
  Das geprüfte Binden einer Headerregel ist keine vollständige Transferabnahme.
  Insbesondere die unqualifizierte Sichtbarkeit vordefinierter OID-Domänen
  via `IMPORTS UNQUALIFIED INTERLIS` ist noch nicht abgenommen.
- Enum-Übersetzung/Funktionsrelationen, alle EXISTENCE-Collectionelemente,
  allgemeine Mengen-/Inspektionspfade, Views und die übrigen P2-Deklarationen
  bleiben offen. Die vollständige P0–P3-Normmatrix fehlt weiterhin.
- Keine aktuelle M1-Editor-/Batch-/Abbruch-/1'000-Edit-Abnahme, keine Linux- oder
  Windows-Prozessmatrix, keine vollständige geo.so.ch-Closure, kein fertiges
  Legacy-CLI-/LSP-Profil und kein bestandenes vollständiges `releaseCheck`.

Nächster fachlicher Schritt: OID-Folgeregeln und noch gesperrte FORMAT-
Komponenten anhand kleiner Normfälle klären; danach Enum-Übersetzungen und
verbleibende Wert-/Pfadfamilien. Offene Pflichtfälle halten P3 offen.
