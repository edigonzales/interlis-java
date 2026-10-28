# Gebundene zusammengesetzte Einheiten

Stand: 21. Oktober 2026. WP-20; META-001/003/006/007/008/014/015,
SEM-008/010/014 und SRC-002–005. S2a bleibt PARTIAL; P2/P3 und G2/G3 offen.
Der [vorherige Importnachweis](imported-domain-types.md) bleibt historisch erhalten.

## Normauslegung und Umsetzung

Die [Referenz 2.3](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf),
§2.9, und die [Referenz 2.4](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf),
§3.9, verwenden dieselben Regeln für die hier bearbeiteten Einheiten:
Der Zugriff erfolgt über das Kurzzeichen, die volle Bezeichnung dokumentiert
die Einheit. Abstrakte Zusammensetzungen verwenden abstrakte Komponenten;
konkrete verwenden konkrete. Die Erweiterung einer Zusammensetzung spezialisiert
die jeweiligen Einheiten und bewahrt deren Multiplikations-/Divisionsfolge.
EXTENDS verlangt eine abstrakte Basis. Die Grammatik lässt auch eine einfache
Einheit mit abstrakter zusammengesetzter Basis und ohne eigene Formel zu;
diese gültige Form bleibt erlaubt. Ein Potenzoperator gehört nicht zur
Kompositionsgrammatik.

`UnitDeclarationSyntax` erfasst Kurzzeichen, Dokumentationsbezeichnung,
EXTENDS-Basis, Umrechnungsziel und Komponentennamen samt Operator strukturiert.
Die Namen behalten ihre exakten Bereiche. Einwertige Enums bleiben über den
DOMAIN-Abschnitt von Einheiten getrennt. Die Grammatik unterscheidet eine
ABSTRACT-Angabe von einem expliziten Kurzzeichen und erlaubt bei Kompositionen
nur Multiplikation oder Division.

`UnitSymbol` erhält `designation`, `derivedFrom` und `components`. Die
Komponenten sind geordnete immutable Paare aus Operator und gebundener Unit-ID.
Die erste Komponente multipliziert; der Konstruktor prüft diese Bedingung. `base` bleibt von der Umrechnungsreferenz
getrennt; die gesamte Abhängigkeitsmenge wird auf Zyklen geprüft. Eine konkrete
abgeleitete Einheit kann über ihr Umrechnungsziel die verlangte abstrakte
Komponentenbasis spezialisieren. Abgeleitete Einheiten dürfen selbst nicht
abstrakt sein und keine abstrakte Einheit als Umrechnungsziel verwenden.

Die Bindung verwendet den bestehenden Deklarationsindex und Resolver. Sie
hängt nicht davon ab, ob die importierte Einheit schon als Symbol aufgebaut
wurde. Falsche Qualifikationen, Dokumentationsnamen statt Kurzzeichen,
artfalsche Ziele und doppelte Kurzzeichen bleiben Fehler. Die globale
Regex-Suche nach einem beliebigen passenden Einheitenalias ist entfernt.
Numerische Typen binden ihre Einheit über denselben Weg; bekannte INTERLIS-
Basiseinheiten besitzen synthetische IDs und gebundene abstrakte Basen.
Unbekannte INTERLIS-Namen werden nicht mehr still als einheitenlos behandelt.

Die Prüfung erfolgt nach dem Symbolaufbau anhand dieser Bindungen. Anzahl,
Reihenfolge, Operatoren und Spezialisierung jeder Komponente sind explizit.
Abhängigkeitstraversierung und Spezialisierung benötigen keinen rekursiven
Java-Aufrufstapel und prüfen Arbeitsbudget und Abbruch. Die Graphvalidierung
prüft die Zielarten; Navigation verwendet die Komponentenreferenzen und das
Kurzzeichen als Deklarationsbereich. Kompositionsübersetzungen vergleichen
gebundene Ziele und Operatoren; Ziffern in übersetzten Komponentennamen
verändern die Kompositionsform nicht. Die bestehende umfassendere Übersetzungs-
Textprüfung ist damit noch nicht vollständig ersetzt.

Die notwendige Konstruktoränderung gehört zur laufenden unveröffentlichten
Vorab-API 0.2. [Migration](../../api-migration-0.2.md), Spec 04/05/16/17 und
interne Verbraucher sind angepasst. Es entstehen kein neues Modul, keine Pass-Infrastruktur und keine parallele
Fachsemantik in Adaptern. Die kleinen
Komponentenrecords und ihr Operator sind am bestehenden Einheitentyp gekapselt.

## Abnahme und Evidenz

`ComposedUnitContractTest` ergänzt zehn JVM-Tests für öffentliche gebundene
Komponenten, Numerikeinheiten, beide Dateisortierungen, beide Sprachversionen,
Abstraktion, Erweiterungen, abgeleitete Komponenten, Sichtbarkeit, Builtins,
Zyklen, Kurzzeichen und Navigation sowie übersetzte Komponentennamen mit Ziffern. Frische und wiederverwendete Analyse nach
Änderungen importierter Komponenten liefern dieselben Diagnosen; alte Graphen
bleiben unverändert.

Die portable Suite ergänzt **116 Mehrdateifälle und zwei Übersetzungsfälle** mit denselben positiven und
negativen Entscheidungen. Die [Prozessvergleiche](units-reference-comparison-2026-10-21.json)
enthalten sämtliche Eingaben für unsere CLI und ili2c 5.6.9, Quellenpins,
Streams und Exitcodes. **118 AGREE, 0 DIFFER**, keine unerwartete Entscheidung
unseres Compilers. Die [identischen früheren Fehlerproben und Folgefälle](units-follow-on-probes-2026-10-21.json)
trennen geschlossene Abstraktionsfehler von weiter offenen Regeln.
Keine Änderung an Baseline, XFAILs oder Pins der eingefrorenen Suite.

Abschliessend bestehen **309 JVM-Tests**, das SDK-Beispiel und neun macOS-CLI-
Prozesse. **1’066 gemeinsame Fälle** bestehen auf TeaVM-JavaScript/Node,
WasmGC/Node, im TeaVM-Worker in Chromium/Firefox/WebKit und echtem Safari sowie
auf Web Image unter Node/Chromium/Firefox/WebKit. WasmGC endet regulär; der
reale Web-Image-Build besteht. Die eingefrorene Konformität bleibt bei
**538 PASS, 1 XFAIL, 54 FAIL, 0 ERROR/XPASS**. Der kombinierte Gradle-Lauf
endet mit Exitcode 1 ausschliesslich wegen `conformanceCheck`.

Die abschliessenden Werkzeugstände, Kommandos, Quellbaum- und Artefakthashes
sowie übernommenen Rohberichte stehen im
[Verifikationsmanifest](verification-2026-10-21-units.json). Der
[Konformitätsfallkatalog](conformance-open-cases-2026-10-21-units.json) bleibt
von den gezielten neuen Fällen getrennt. TeaVM-WasmGC bleibt der
Produktionspfad; ein Testinhalt ohne regulären Prozessabschluss wäre kein PASS.
Quellbaumdigest, 14 Artefakthashes, acht Rohberichte, Quellenpins und Loghashes
sind gegengeprüft. Die vier ursprünglichen Fehlerquellen und der offene
Konformitätsfallbestand stimmen exakt mit den vorherigen Nachweisen überein.
JSON-Dateien, 441 lokale Dokumentlinks, Codeblockgrenzen und
`git diff --check` bestehen.

## Verbleibender Umfang

Die vier ursprünglichen Abstraktionsfehler sind geschlossen. Die neuen
Folgeproben reproduzieren in beiden Sprachversionen falsche Annahmen von
Umrechnungsfaktor null, Division durch null und einem Wechsel von Meter zu
Sekunde in einer numerischen Domain-Erweiterung; ili2c lehnt alle sechs ab.
Diese Fälle sind keine freigegebenen Normabweichungen.

Die vollständige Einheitensemantik ist damit nicht abgenommen. Offen bleiben
exakte numerische Umrechnungsfaktoren, symbolische Funktionsumrechnungen,
Dimensions-/Einheitenverträglichkeit in numerischen Typrelationen,
vollständige Übersetzungen sowie weitere geerbte Topic- und Importkombinationen.
Definitionstext bei abgeleiteten Einheiten ist kein Nachweis einer ausgeführten
oder vollständig geprüften Umrechnung. Der nächste Fachschritt betrifft
numerische Umrechnungsregeln und Einheitenverträglichkeit; die Folgeproben
halten konkrete Unterschiede fest.

Unverändert offen sind die gesamte P2-Normmatrix, Views und übrige Deklarationen,
das CLI-Prüfprofil, Resolver-/LSP-Verträge, die vollständige geo.so.ch-Closure,
M1-Performance-/Abbruch-/1'000-Edit-Nachweise und Linux-/Windows-Prozessprüfungen.
Ein vollständiges `releaseCheck` wurde nicht abgenommen. **P3 bleibt offen.**
