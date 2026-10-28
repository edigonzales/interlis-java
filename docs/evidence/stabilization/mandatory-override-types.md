# Frühe Typbindung bei MANDATORY-Überschreibungen

Stand: 21. Oktober 2026. WP-20/21/22; META-006/008/014/016,
SEM-008/010/014, SRC-002–005. S2a/S2b bleiben PARTIAL; P2/P3 und G2/G3 offen.
Der [vorherige Abhängigkeitsnachweis](topic-dependencies.md) bleibt historisch erhalten.

## Norm und Umsetzung

Die [PDF-Referenz 2.3](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf),
§2.6.1, und die [PDF-Referenz 2.4](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf),
§3.6.1, erlauben eine Attributüberschreibung mit ausschliesslich MANDATORY:
Der bisherige Typ bleibt bestehen; der Wert muss definiert sein. Eine explizite
Typangabe unterliegt weiterhin den Typregeln der Vererbung.

Die Syntaxerfassung behandelt diese Form jetzt zentral und unabhängig vom
EXTENDED-Marker. Dadurch kann die Semantik ein fehlendes EXTENDED oder eine
fehlende Basis fachlich ablehnen. Die bisher doppelte Spezialbehandlung konnte
sogar ein explizites NUMERIC als ausgelassenen Typ behandeln; diese Abkürzung
und die Umdeutung beliebiger nicht unterstützter EXTENDED-Typen sind entfernt.
Ein ausgelassener Funktionstyp wird nicht zum gültigen generischen Typ.

Klassen-/Strukturbasen werden vor ihren Mitgliedern gebunden, auch wenn ein
Attribut aus einer später einsortierten importierten Quelle angefragt wird.
Die gemeinsame Attributtypbindung übernimmt für MANDATORY die tatsächliche
TypeId des unmittelbar geerbten Attributes. Sie memoisiert in der vorhandenen
Typzuordnung und erkennt Zyklen. Es gibt keinen zusätzlichen veröffentlichten
Typ, keine neue Cacheebene und keine zweite Pfadsemantik.

Ausdruckspfade fragen diese Bindung direkt ab. Damit benutzen gewöhnliche und
externe Constraints, Funktionsargumente, UNIQUE/LOCAL/EXISTENCE, Referenz-
fortsetzungen und LIST-/Koordinatenindizes denselben Typ wie das spätere Schema.
Die nachträgliche Typersetzung beim Aufbau effektiver Mitglieder ist entfernt;
dort wird nur noch die Überschreibungsidentität ergänzt. Typidentität,
Referenzziele, Ursprung, unmittelbare Überschreibung und Quellnavigation sind
mit öffentlichen Modell-/Queryzugriffen geprüft.

Die strengere Basisbindung machte zwei ältere Probleme sichtbar:

- Eine akzeptierte Klasse mit Strukturbasis verlor bisher ihre Basiskante.
  Der Graph erhält und validiert nun die Strukturidentität; die Diagrammprojektion
  ist ebenfalls geprüft. Es gibt keine neue öffentliche Signatur.
- Bei INTERLIS.REFSYSTEM ging das reservierte zweite Namensegment in der
  Syntaxerfassung verloren. Der vollständige Name und sein Quellbereich bleiben
  jetzt erhalten. Das ersetzt nicht die noch fehlende vollständige Modellierung
  der vordefinierten Metaobjektklassen.

**Normprofilklärung offen:** Das verwendete genehmigte 2.4-PDF erlaubt in §3.5.3
Struktur-zu-Klasse-Vererbung sowohl im Text als auch in der Grammatik. Die
[Onlinefassung](https://geostandards-ch.github.io/doc_refhb24/) hat dazu eine
abweichende Anmerkung mit [Issue 16](https://github.com/geostandards-ch/doc_refhb24/issues/16).
Dieser Abschnitt behält das bestehende PDF-Profil bei und korrigiert dessen
Modellrepräsentation. Vor der vollständigen P2-Abnahme muss das verbindliche
Errata-Profil einschliesslich dieser 2.4-Frage geklärt sein; die Anmerkung wird
nicht still als neue Normbasis übernommen.

## Prüfungen und Aussagegrenzen

`MandatoryOverrideTypeContractTest` ergänzt elf JVM-Tests für beide Versionen:
Referenzpfade, mehrstufige Typübernahme, skalare Typen und Domains,
Funktionsprüfung, Struktur-/Indexpfade, die gemeinsamen Constraintbinder,
EXTENDED-Topics, Assoziationsattribute, Strukturbasen, fehlende Basen, fehlendes
EXTENDED, Zyklen, fehlende Funktionstypen, vollständige Builtinnamen und zwei
Sortierungen importierter Quelldateien. Ein zusätzlicher Projektionstest prüft
die Basiskante zur Struktur. Die portable Suite ergänzt 58 Fälle, darunter
Referenzrestriktionen und Koordinatenindizes.

Abschliessend bestehen **289 JVM-Tests** ohne Fehler oder übersprungene Tests,
das SDK-Beispiel und neun macOS-CLI-Prozesse. **864 gemeinsame Fälle** bestehen
auf TeaVM-JavaScript/Node, WasmGC/Node, im TeaVM-Worker auf Chromium/Firefox/WebKit
und echtem Safari sowie auf Web Image unter Node/Chromium/Firefox/WebKit.
WasmGC endet regulär; der reale Web-Image-Build besteht. Keine erzwungene
Prozessbeendigung und kein Austausch des Produktionspfads.

Die eingefrorene Konformität bleibt bei **538 PASS, 1 XFAIL, 54 FAIL,
0 ERROR/XPASS** unter 593 ausgewählten Fällen. Der kombinierte Gradle-Lauf
endet mit Exitcode 1 ausschliesslich wegen `conformanceCheck`. Es verbleiben
45 Implementierungslücken und neun bereits dokumentierte Normabweichungen.
Der neue Prozessvergleich hat **44 AGREE, 14 DIFFER**, keine unerwartete
Entscheidung unseres Compilers. Die Unterschiede betreffen numerische
Boolean-Argumente, LIST-Obergrenzen, geerbte Boolean-Typen und Referenzrestriktionen.

Die tatsächlichen abschliessenden Resultate und ihre Provenienz stehen im
[Verifikationsmanifest](verification-2026-10-21-mandatory.json).
[Prozessvergleiche mit ili2c](mandatory-reference-comparison-2026-10-21.json)
enthalten sämtliche Quellen, Inhaltspins, Streams, Exitcodes und eine
fallbezogene Bewertung. Pins, Baseline und XFAILs der externen Konformitätssuite
werden nicht verändert. Ein Zwischenlauf mit verkürztem REFSYSTEM-Namen hatte
einen zusätzlichen falschen Konformitätsfehler; er ist kein Abschlussnachweis.
Quellbaumdigest, 14 Artefakthashes, acht Rohberichte, Prozessvergleichsquellen und
Loghashes sind abschliessend gegengeprüft. JSON-Dateien, 429 lokale Dokumentlinks,
Codeblockgrenzen und `git diff --check` bestehen. Diese Dokumentprüfungen ersetzen
keine Laufzeitabnahme.

## Offene Punkte

Die ursprüngliche [falsche Ablehnung von ref->flag](depends-follow-on-probe-2026-10-21.json)
ist geschlossen; die identische Eingabe wird in den
[aktuellen Folgeproben](mandatory-follow-on-probes-2026-10-21.json) erneut geprüft.
Allgemeine quellreihenfolgeunabhängige Domain-/Strukturtypbindung ist damit noch
nicht vollständig abgenommen. Die Folgeproben zeigen eine konkrete falsche Ablehnung in beiden Versionen:
Sortiert der Importeur vor dem Modell mit der Domain Flag = BOOLEAN, scheitert
`flag == #true` nach einer MANDATORY-Überschreibung mit DOMAIN_REFERENCE versus
BOOLEAN. Bei umgekehrter Sortierung besteht dieselbe Semantik; ili2c akzeptiert
beide. Dies ist der nächste konkrete Schritt und kein erfolgreicher portable Fall.

Weiter offen bleiben konkrete Zielunterklassen bei abstrakten Referenzmengen,
polymorphe Strukturrestriktionen, die vollständigen Regeln zur Übernahme von
Referenzen aus Strukturen in Klassen, sehr tiefe Vererbung, Domainbedingungen,
alle Builtins und übrigen P2-Familien. Für die Freigabe fehlen zudem die vollständige
Normmatrix, CLI-/LSP-Verbraucherprofile, geo.so.ch-Closure, M1-Performance-/Abbruch-/
1'000-Edit-Nachweise und Linux-/Windows-Prozessprüfungen. `releaseCheck` ist nicht
vollständig abgenommen. **P3 bleibt offen.**
