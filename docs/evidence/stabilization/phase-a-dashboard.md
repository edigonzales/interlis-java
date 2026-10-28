# G2-Phase A – Status, Evidenz und offene Entscheidungen

Stand: **28. Oktober 2026**. Dieses Dashboard fasst den ausführbaren Stand von
Phase A zusammen. Es ist **keine Abnahme**; unabhängige Reviews und normative
Nutzerentscheidungen bleiben getrennt.

## 0. Abschluss der agentseitigen Arbeit

Die bisher erstellten Phase-A-Artefakte, Gates und Reviewpakete liegen vor.
Der vollständige A3-Testexport und semantische Vergleich fehlen weiterhin.
**Nicht** behauptet wird die unabhängige A2-/A3-Abnahme; offen sind die Reviews,
die 18 normativen Referenzausnahmen und die zwei Normprofile.
Die Ausführung der R4-Planfälle gehört zu den späteren fachlichen Schritten
B–I und ist hier als Arbeitsliste, nicht als Abnahme, gebunden.

## 1. Abgeschlossen bzw. agentseitig geliefert

| Schritt | Stand | Nachweis |
|---|---|---|
| A1 | unabhängig abgenommen | [A1-Nachweis](verification-2026-10-27-g2-A1.json) |
| A2-R1/R2 | unabhängig geschlossen | [R2-Review](g2-reviews/A2-R2-attempt2.md) |
| A2-R3 | Evidenz und Reviewpaket v3 geliefert; Review ausstehend | [R3-Paket v3](../../../build/reports/g2/A2/r3-20261028-packet-v3/manifest.json), [R3-Review](g2-reviews/A2-R3-review-2026-10-28.md) |
| A2-R4 | strukturelle Inventur v5 geliefert; Ausführung/Review offen | [R4-Paket v5](../../../build/reports/g2/A2/r4-20261028-packet-v5/manifest.json), [Bedingungen](g2-norm-conditions.json) |
| A3 | Gegenprüfungen portiert und gepaart; vollständiger Export/Review offen | [A3-Paket v2](../../../build/reports/g2/A3/a3-20261028-packet-v2/manifest.json), [A3-Nachweis](verification-2026-10-28-g2-A3-portable-counterchecks.json), [Paar-Artefakt](g2-a3-semantic-pairs.json) |

## 2. Aktuelle Gates

| Prüfung | Ergebnis |
|---|---|
| `check` | **700 JVM-Tests**, 0 Failures/Errors/Skipped |
| `checkPortable` | **3257 portable Fälle** auf TeaVM-JS und WasmGC, Node 24.22.0, PASS |
| `checkBrowser` | **3257 Fälle** auf Chromium, Firefox, WebKit und echtem Safari, PASS |
| Web Image | Chromium/Firefox/WebKit mit 3257 Fällen, PASS |
| `conformanceCheck` | 593 ausgewählt: **574 PASS / 19 XFAIL / 0 FAIL / 0 ERROR / 0 XPASS** |
| `g2EvidenceTest` | **58 Tests** |

Nachweise: [Plattformgates](verification-2026-10-28-g2-A2-platform-gates.json),
[Konformität](verification-2026-10-28-g2-A2-conformance.json),
[A3-Gegenprüfungen](verification-2026-10-28-g2-A3-portable-counterchecks.json).

## 3. R4-Inventur

- **311** TOC-Abschnitte, **2721** Absätze mit exakten PDF-Slices und SHA-256
- **777** Bedingungen, davon **369** Compilerregel-Bedingungen, **245**
  vordefinierte Inhaltszeilen im A2-Katalog und **7** erklärende Fragmente
- **1476** geplante Fälle; **19** Bedingungen mit atomaren Assertions,
  **369** mit `CANDIDATE_ONLY`-Testkandidaten
- **321** Grammatikproduktionen / **1218** Occurrences
- **21** Builtins / **84** geplante Fälle
- **289** nummerierte Spezifikationsanforderungen: 45 mit Matrixlink, 80 G2 mit
  vorgeschlagenem Owner, 164 ausserhalb G2; zusätzlich 12 explizit erkannte
  unnummerierte Normzeilen
- **18** normative Referenzausnahmen als
  [Nutzerentscheidungspaket](g2-normative-decision-packet-2026-10-28.md).
  Bei den ersten sechs OID-/CONTRACTED-Fällen belegen die dort abgedruckten
  TYPE-MODEL-Auszüge die angegebene Begründung nicht; sie müssen ergänzt werden.


### Ausführungs-Backlog nach Owner-Step

| Step | Bedingungen | geplante Fälle | atomar verknüpft | Kandidaten |
|---|---:|---:|---:|---:|
| B2 | 27 | 108 | 2 | 27 |
| B3 | 51 | 204 | 14 | 51 |
| C1 | 14 | 56 | 0 | 14 |
| C3 | 112 | 448 | 3 | 112 |
| D1 | 3 | 12 | 0 | 3 |
| D2 | 21 | 84 | 0 | 21 |
| D3 | 19 | 76 | 0 | 19 |
| E1 | 16 | 64 | 0 | 16 |
| E2 | 14 | 56 | 0 | 14 |
| E3 | 14 | 56 | 0 | 14 |
| F1 | 21 | 84 | 0 | 21 |
| F3 | 57 | 228 | 0 | 57 |
| **Summe** | **369** | **1476** | **19** | **369** |

Die 245 B1-Zeilen sind vordefinierte Modellinhalte und werden über den
[A2-Katalog](g2-A2-predefined-inventory.md) statt über generische Vier-Fall-
Bedingungen geführt.

B1 (vordefinierte Inhalte) ist der grösste Block; die Kandidaten sind dort
besonders sorgfältig gegen die tatsächlichen Assertions zu prüfen.

## 4. A3-Gegenprüfungen

Die drei geforderten Gegenprüfungstypen sind JVM-/portable gepaart:

| Typ | JVM | Portable |
|---|---|---|
| verlorenes/geerbtes Mitglied | `LineFormBindingContractTest…` | Completion: `inherited` sichtbar |
| falsches gleichnamiges Ziel | `E4EnumOperatorContractTest…` | Enum-Ziele bleiben getrennt |
| vertauschte Reihenfolge | `StructuredTranslationContractTest…` | vertauscht abgelehnt / passend angenommen |

Grenze: Portable Completion prüft die **Sichtbarkeit**, nicht die effektive
Mitglieder-Reihenfolge. Dafür wurde keine neue Bridge-API eingeführt.

## 5. Offene Punkte

1. **A2-R4-Ausführung:** 1476 geplante Fälle für 369 Compilerbedingungen
   fachlich prüfen; die 369 Kandidatenbindungen sind keine Assertionsnachweise.
2. **A3-Rest:** vollständiger Testexport/semantischer Vergleich und Review;
   Entscheidung, ob die Lost-Member-Reihenfolge portabel geprüft werden muss.
3. **Nutzerentscheidungen:** 18 Referenzausnahmen, `TYPE_MODEL_STRUCTURE_BOUNDARY`,
   `NUMERIC_SCALING_PAIR_AND_CONSTANT_PROFILE`. Die A3-Lost-Member-Grenze ist
   eine offene technische Abnahmefrage.
4. **Unabhängige Reviews:** R3 v3, R4 v5, A3 v2, danach A2-Gesamtreview und
   Phasenreview.
5. **P3/G3** bleiben ausserhalb dieses Auftrags und offen.

## 6. Reproduktion

Die Reviewpakete enthalten Vorher-/Nachher-Snapshots, Patches, Manifeste,
Artefakthashes und Prüfaufträge. Die Patches reproduzieren den jeweiligen
Nachherstand dateigenau. Unabhängige Reviews dürfen keine Repoquellen ändern.
