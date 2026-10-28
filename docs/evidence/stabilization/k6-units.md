# K6: Sechs Einheitenregressionen

## Auftrag, Ausgangsstand und Abnahme

Bearbeitet werden `2.3-zero-factor`, `2.3-zero-divisor`,
`2.3-numeric-unit-mismatch` und ihre drei 2.4-Entsprechungen.
Sie sind zusätzliche erforderliche Regressionen ausserhalb der eingefrorenen
593er-Suite, keine sechs weiteren Suite-FAIL. Die ursprünglichen Quellen und
Beobachtungen bleiben im [historischen Bericht](units-follow-on-probes-2026-10-21.json).
Vor der Implementierung wurden alle sechs erneut reproduziert: eigener Compiler
Exit 0, ili2c 5.6.9 Exit 1. Der bisherige vollständige Stand ist im
[K5-Manifest](verification-2026-10-21-k5-graphics.json) dokumentiert.

Zuordnung: WP-20/22/25; META-003/006/009/011/014/016,
SEM-005/010/014, TEST-011/012. Erfolg verlangt eine gezielte Quellablehnung,
positive Kontrollen, öffentliche Modell-/Querywerte und einen vollständigen
regulär beendeten Prüfstand. **P2/P3 und G2/G3 bleiben offen.**

## Fachliche Änderungen

`UnitDeclarationSyntax` und `UnitSymbol` erhalten dieselben unveränderlichen
`ConversionFactor`-Werte: Operator, DECIMAL/PI/LNBASE, optionaler exakter
`DecimalLiteral` und UTF-16-Konstantenbereich mit Revision. FUNCTION-Erklärungen
sind getrennt. `[m]` ohne Faktoren bleibt Identitätsumrechnung. Signierte
Ganzzahlkonstanten werden von der Grammatik wie Dezimalkonstanten erfasst.

Jede Konstante wird einzeln geprüft; ein Gesamtprodukt oder Quotient wird nicht
berechnet. Das verhindert Rundung, Unterlauf und künstliche Grenzen einer
Double-Repräsentation. Nullwerte bleiben im fehlerhaften Analysemodell sichtbar;
sie werden nicht durch einen erfolgreichen Ersatzwert ersetzt.

| Originalfälle, jeweils 2.3/2.4 | Aktuelle Ablehnung |
|---|---|
| `Z = 0 [m]` | `ILI-SEM-0010` / `UNIT_CONVERSION_ZERO_FACTOR` am Nullfaktor |
| `Z = 1 / 0 [m]` | `ILI-SEM-0010` / `UNIT_CONVERSION_ZERO_DIVISOR` am Nullteiler |
| `D = 0..100 [m]; X EXTENDS D = 0..10 [s]` | `ILI-SEM-0010` / `NUMERIC_UNIT_EXTENSION` |

Alle sechs Originalquellen sind unverändert unter
[testdata/conformance/k6-units](../../../testdata/conformance/k6-units/) aufgenommen;
ihre SHA-256 stimmen mit den historischen Quellen überein. Fehlende
Umrechnungsziele behalten erfasste Faktoren im Teilmodell und ergeben einen
Bindungsfehler, keinen internen Konstruktorfehler.

Skalare numerische Erweiterungen übernehmen eine ausgelassene Einheit vor der
Ausdrucksbindung aus ihrer effektiven Basis. Das gilt für Domains und explizite
numerische Attributüberschreibungen; referenzierte Domainwerte werden dabei
nicht mutiert. Eine gemeinsame interne Regel wird auch von `canExtend` benutzt:
konkrete Einheit unverändert, abstrakte Einheit passend spezialisiert,
konkreter einheitenloser Bereich weiterhin einheitenlos. Meter → Kilometer ist
als Erweiterung unzulässig, obwohl eine Umrechnung existiert. Die allgemeine
Zuweisungsprüfung wird dadurch nicht pauschal verschärft.

Normgrundlagen: [2.3 §2.8.5 und §2.9.1–2.9.2](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf)
und [2.4 §3.8.5 und §3.9.1–3.9.2](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf).
Die Nullprüfung folgt der dort beschriebenen Umrechenbarkeit; die Behauptung,
das Handbuch enthalte einen wörtlichen expliziten Nullverbotssatz, wird nicht
aufgestellt. Der Nullfaktor zerstört diese Umrechenbarkeit; der Nullteiler ist
arithmetisch undefiniert. Alle sechs Ablehnungen stimmen mit ili2c überein.

Einheitentranslationen vergleichen gebundene Ziele und strukturierte Faktoren.
Auch die Unterscheidung abstrakter und konkreter Einheiten wird am Modell geprüft.
Der alte zusätzliche Einheitentextvergleich ist entfernt; der übrige bestehende
Übersetzungsfallback interpretiert diese Deklarationen nicht erneut.
`1000 / 2` und `1000.0 / 2.00` besitzen dieselben Umrechnungswerte. Die
Definitionstexte und Konstantenbereiche bleiben auch nach ergänzenden Unicode-
Zeichen und CRLF korrekt; die bisherige direkte Codepoint-Substringbildung im
Definitionstext wurde an die vorhandene UTF-16-Abbildung angeschlossen.

## Nachweise

`NumericUnitConversionContractTest` ergänzt 13 JVM-Testmethoden: Originale,
Nullschreibweisen und Positionen, genaue Fehlerbereiche, exakte grosse/kleine
Werte, PI/LNBASE, FUNCTION/Identität, öffentliche Erweiterungsqueries,
Imports/umgekehrte Reihenfolge, Domainvererbung und attributseitige
Domainreferenzen, Teilmodelle, Übersetzungen und gültig → ungültig → gültig
mit frischer sowie wiederverwendeter Analyse. `DOMAIN Alias = D` wurde nicht
als künstliche neue Sprachsyntax eingeführt; die Tests verwenden legale
Domainerweiterungen und bestehende `DomainReferenceType`-Werte.

98 neue portable Fälle ergänzen die bestehende Matrix. Der Prozessvergleich
prüft dieselben 98 Fälle und die sechs Originale, insgesamt **104 Paare / 208
reguläre Prozessabschlüsse**. Alle eigenen Erwartungen bestehen; **102 von 104**
Entscheidungen stimmen mit ili2c überein. Die beiden Unterschiede sind in 2.3/2.4
positive Konkretisierungen von `NUMERIC [INTERLIS.ANYUNIT]` auf eine lokale
Metereinheit. Beide Normfassungen bestimmen ANYUNIT als implizite direkte oder
indirekte Basis aller Einheiten, auch ohne explizite Deklaration. Die eigene
Annahme ist damit begründet; ili2c lehnt diese Proben ab. Die Beobachtungen werden
nicht in zusätzliche Suite-XFAILs umgewandelt.

Reproduzierbarer Vergleich nach gebauter CLI:

```sh
python3 tools/reference/compare_units.py \
  --java-home /Users/stefan/.sdkman/candidates/java/25.0.3-tem \
  --node /Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
  --ili2c /Users/stefan/.cache/interlis-java/ili2c.jar
```

Quellen, Pins, Befehle, Ausgaben und Einzelresultate stehen im
[Vergleichsbericht](k6-units-reference-comparison-2026-10-22.json), der gesamte
übernommene Lauf im [Manifest](verification-2026-10-22-k6-units.json).
Rohberichte und Logs liegen unter `build/reports/stabilization/`.
Zwischenläufe mit Fehlern beziehungsweise vor der abschliessenden
Übersetzungskorrektur werden nicht als Abnahme übernommen.

## Abschluss und verbleibende Grenzen

Aktuell verifiziert: **413 JVM-Tests, 15 Wrappertests und 1546 portable Fälle**
unter TeaVM-JavaScript/WasmGC, Node, Chromium/Firefox/WebKit/echtem Safari und
Web-Image. SDK und neun CLI-Prozessprüfungen bestehen. Beide abschliessenden
Gradle-Läufe enden regulär mit Exit 0; kein Prozess wird als Erfolgsersatz
zwangsweise beendet.

Die eingefrorene Projektbilanz bleibt **574 PASS / 19 XFAIL / 0 FAIL**, ohne
ERROR/XPASS; roh **574 PASS / 1 XFAIL / 18 FAIL**. K6 fügt keine Ausnahme hinzu.
Die 18 Normausnahmen bleiben dokumentierte und technisch abgesicherte
Projektentscheidungen; eine zusätzliche unabhängige Normprüfung ist weiterhin
ausstehend. Dazu kommt die getrennte INTERLIS-1-Ausnahme.

Die sechs zusätzlichen K6-Einheitenregressionen und ihre Kontrollen sind
abgenommen. Der vollständige Referenzlauf besteht unter den dokumentierten
Projektentscheidungen. Eine weitergehende Gesamtabnahme folgt daraus nicht:
**19 ältere B1–B3-Fälle** behalten den Status
`REFERENCE_PASS_ACCEPTANCE_PENDING`. Ihre Originalquellen liefern bereits PASS;
der ausdrückliche Abgleich mit öffentlichen Modell-/Querytests und verbleibenden
Kombinationsnachweisen steht noch aus. Diese Fälle werden im Katalog einzeln
benannt und beim K6-Abgleich nicht automatisch geschlossen. Diese Abnahme folgt
als Nächstes. Danach bleiben die folgenden bereits reproduzierten Sprachfunde
offen, in dieser Folge:

1. `VIEW_TRANSLATED_IMPORT_CLOSURE`: Symbolkorrespondenz übersetzter Imports.
2. `GEOMETRY_OVERLAP_PRECISION` und `GEOMETRY_TYPE_COMPLETENESS`:
   Toleranzpräzision/Vererbung sowie unvollständige konkrete Flächentypen.
3. `INSPECTION_CUSTOM_SEGMENT`: vollständige Inspektion eigener Kurvensegmente.
4. `GRAPHIC_RESPONSIBLE_SIGNATURE_CLASS`: Wiederverwendung einer Basissignatur.

Für K6 wurden keine neuen unabhängigen Implementierungsfehler vorgemerkt.
Koordinatenachsen, allgemeine Dimensionsrechnung, automatische numerische
Umrechnung und vollständige Präzisionsregeln sind nicht Teil dieser Lieferung.
Die übrigen S0–S6-Verpflichtungen, Linux-/Windows-Nachweise,
Editor-/Retention-/Abbruchbudgets und vollständiges `releaseCheck` bleiben offen.
**P3 bleibt offen.**
