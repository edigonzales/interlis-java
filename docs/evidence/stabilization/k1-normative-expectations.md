# K1 — geprüfte eigene Erwartungen

Umfang: 14 strittige Fälle aus dem unveränderten Ausgangslauf mit 556 PASS,
1 XFAIL, 36 FAIL (593 Fälle). Zuordnung: WP-20–25, SEM-005/008/010/014/020,
META-003/006/014, TEST-011/012. P3 bleibt offen.

## Entscheidungen

Die neun bisherigen und fünf zusätzlichen Unterschiede sind fachlich begründet.
Die [versionierte Erwartungsliste](../conformance/normative-expectations.json)
enthält alle Suite-IDs, Sprachversionen, Originalhashes, Normbelege, eigene
Erwartungen und exakten zulässigen Diagnosen samt Argumenten und Quellbereichen.
Die beiden Textdiagnosen unterscheiden nun `TEXT*20` von `TEXT*10` und
unbeschränktes `TEXT` von `TEXT*80`.

| Fälle | Normgrund | Positive Gegenprobe |
|---|---|---|
| Sechs OID-/CONTRACTED-Fälle | 2.3 §2.5.1–2.5.2: OID-Verträge setzen CONTRACTED voraus. In 2.4 entfällt diese Pflicht. | Modellköpfe einschliesslich Übersetzungen ergänzen. |
| `association_AssociationAsTarget` | 2.3 §2.7.1: Assoziation als Rollenziel braucht eigene Identität. | Assoziation mit OID. |
| `FormattedType23Test.simple + baseDomainRestricted` | 2.3 §2.8.7, Anhang A und §4.3.11.5: XMLDate benutzt zweistellige Monate/Tage. | Beide Grenzen auffüllen. |
| `attributeRef_RefAttrInClass` | 2.4 §3.5.3/3.6.4: implizites NO OID ist kein Referenzziel. | Zielklasse mit OID AS. |
| `assocComplete` | 2.3 §2.7.1: Mehrfachlinks derselben Teilnehmer brauchen OID. | Beide übersetzten Assoziationen ergänzen. |
| `roleComplete` | 2.3 §2.7.1: nicht versteckende Geschwister für dieselbe Zielklasse widersprechen HIDING. | Beide Geschwister ebenfalls verstecken. |
| `expressions_ParentPathElement` | 2.3 §2.6: Zuweisung muss in den Zielbereich passen. | TEXT*20 als Ziel. |
| `view_AcceptBasicViewProjectionDef` | 2.3 §2.6: Funktionssignatur bestimmt Ergebnisbereich. | TEXT*80 als Ergebnis. |
| `iliconversion_CheckViewDefWithALLOF` | 2.3 §2.6: numerischer Wert passt nicht in Boolean. | Boolesches Basisattribut verwenden. |

Quellen: [Referenz 2.3](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf),
[Referenz 2.4](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf).

## Auswertungsvertrag

Der externe Runner und seine rohe Erwartung bleiben unverändert.
`build/conformance/report.json` enthält weiterhin den reinen Referenzvergleich.
Der bestehende Wrapper schreibt zusätzlich `normative-assessment.json` und einen
Projektbericht unter `build/reports/conformance/conformance-summary.json`.
Darin sind `referenceSummary`, Referenzerwartung, tatsächliches Ergebnis und
eigene Bewertung getrennt sichtbar. Ein nur nach diesen Prüfungen verdienter
Referenz-XFAIL ist kein ungelöster Implementierungsfehler.

Für jede Ausnahme werden Original und positive Gegenprobe mit der aktuellen
CLI und `--json` erneut geprüft. Nur eine Quellfehler-Ablehnung ohne fehlende
Implementierung, mit exakt erwarteten semantischen Diagnosen und passendem
Originalpin, zusammen mit einer fehlerfreien, gepinnten Gegenprobe, erlaubt
`FAIL` → `XFAIL` im Projektbericht. Eine unerwartete Annahme, weitere Diagnose,
falsche Quelle, fehlendes Ergebnis, Prozessfehler oder Abbruch verhindert Erfolg.
Das bisherige INTERLIS-1-XFAIL bleibt separat in der unveränderten Baseline.

Der Wrapper prüft vollständige/duplikatfreie Fallmengen, Berichtszähler und
Prozessstatus. Vor einem Lauf entfernt er alte Ergebnisdateien. Seine
Fehlerpfade sind mit Python-unittest geprüft und über `conformanceWrapperTest`
an `check` und `conformanceCheck` angeschlossen. Es gibt keine automatische
Übernahme neuer Erwartungen.

## Nachweise und Grenzen

Alle 14 Originale werden von ili2c 5.6.9 angenommen und vom eigenen Compiler
mit der begründeten Diagnose abgelehnt. Alle 14 gezielt korrigierten Originale
werden von beiden Compilern angenommen (56 Prozessläufe). Öffentliche Typen
und Ableitungsausdrücke der beiden Textfälle werden in
`NormativeViewAssignmentTest` für 2.3/2.4 geprüft; die portable Matrix enthält
acht entsprechende positive/negative Fälle.

Vollständiger Lauf: 346 JVM-Tests, 15 Wrappertests und 1200 portable Fälle
bestehen auf JVM/TeaVM-JS/WasmGC/Node, Chromium/Firefox/WebKit und echtem Safari.
Web-Image besteht in Node und drei Browsern; SDK und neun CLI-Prozessfälle bestehen.
Nur `conformanceCheck` schlägt weiterhin fehl: Projektbilanz 556 PASS, 15 XFAIL,
22 FAIL, 0 ERROR/XPASS; rohe Referenzbilanz unverändert 556/1/36. Die 14 eigenen
Normerwartungen bestehen. [Verifikationsmanifest und Artefakthashes](verification-2026-10-21-k1.json),
[56 Referenz-/Gegenprobenprozesse](k1-reference-comparison-2026-10-21.json). K2–K6 und die zusätzlichen Sprach-/Verbraucherpflichten
bleiben offen. Der unabhängige Fehler bei übersetzten Importmodellen bleibt
vorgemerkt und wird nicht vor C1–C4 gezogen.
