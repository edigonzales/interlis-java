# P3 – Compilerersatz und Integration

Nutzerauftrag vom 30. Oktober 2026: gesamten Meilenstein P3 autonom bis zum
G3-Gate durchführen, einschliesslich unabhängiger OpenCode-Reviews über die
direkte DeepSeek API, ohne Stopps nach Teilabschnitten. Basis: `0e20e6b23fbcbdc3315f58d6e3c467fea8ee3d94`.
G2 bleibt am Produktcommit `1371841572828a387c1369ae84f6fa6cb4ddffc0` unabhängig
abgenommen; seine Nutzerprofile und Originalpins bleiben erhalten.

Fachlicher Umfang: WP-30 bis WP-33 aus [Spezifikation Kap.14](../spec/14-implementierungsplan-und-arbeitspakete.md#5-p3-compilerersatz-und-integration).
Verträge aus Kap.03/07/09/10/11/13/16/18 gelten kumulativ. Historische Nachweise
unter docs/evidence/p3 bleiben unverändert; sie ersetzen keine aktuelle Abnahme.
Operativer P3-Stand: ausschliesslich [STATUS.md](STATUS.md), zusammengefasst im
[Projektstatus](../implementation-status.md).

## Lieferabschnitte und beobachtbare Kriterien

Die folgenden AC-P3-IDs sind lokale Abnahmekriterien dieses Auftrags; sie
ersetzen und nummerieren keine Anforderungs-ID der Spezifikation um.

| Lieferung | Kriterien und Anforderungen | Erwartete Beobachtung / passende Abnahme |
|---|---|---|
| P3-1 CLI und Formatierung (WP-30) | AC-P3-01: CLI-001–004, COMPAT-001–003, LOG-005; AC-P3-02: CLI-005–007, DIAG/LOG-Laufvertrag; AC-P3-03/04: FMT-001–004 | Gepinntes tatsächliches ili2c-5.6.9-Prüfprofil mit Optionsinventar, expliziten Ablehnungen, getrennten Streams/Exits; Konfiguration mit Präzedenz/Herkunft, gemeinsame Host-/Compilerläufe und ehrliche Capabilities. Formatter-API und CLI sind semantikerhaltend/idempotent; Syntax-/IO-/Abbruch-/Flushfehler liefern keinen Erfolg und beschädigen keine vorhandene Zieldatei. Prozess-, API- und Fehlerpfadtests. |
| P3-2 LSP (WP-31) | AC-P3-05: INC-018/019/022, LSP-003/004/006/011/015; AC-P3-06: LSP-012; AC-P3-07: LSP-007/010/013/014; AC-P3-08: LSP-001/002/008/009/016 | Dokumentnotifications blockieren nicht auf Compilerarbeit; neuester Auftrag gewinnt, Abbruch und bedingte Annahme funktionieren. Mehrdatei-/geschlossene Quellen, Konfiguration, Watcher, vollständiges Rename, semantische Completion/Tokens/Folding/Formatierung und Diagrammanker über echte stdio-Prozesse geprüft. |
| P3-3 Browser und Diagramm (WP-31) | AC-P3-09: WASM-009–011/017; AC-P3-10: WASM-008/015, INC-012/018; AC-P3-11: VIZ-001–018, WASM-014 | Worker-Neustart erhält gesamten Mehrdatei-/Konfigurationsstand und verwirft alte Antworten; Handles/Snapshots werden auch nach Ablehnung/Benchmark freigegeben. Gepinnter TypeScript-/Diagrammschemaadapter mit echten Navigationszielen und n-ären Beziehungen; Node plus reale Browser inklusive Safari. |
| P3-4 Corpus und Belastung (WP-32) | AC-P3-12: TEST-007/010, RES; AC-P3-13: NFR-004, INC-009/014/017; AC-P3-14: TEST-008, WASM-006/018; AC-P3-15: NFR-005, WASM-011/015; AC-P3-16: LOG-009/010 | Eingefrorener offizieller Geo-Korpus einschliesslich transitiver Closure und exakter fachlicher Erwartungen. Kalt-/Warm-/Batch-/Editor-/Abbruch-/1000-Edit-/Retention- und Artefaktgrössenmessungen mit Hardware/Versionen/Rohwerten; Sollbudgets getrennt und maschinell geprüft. Alle ausgewählten verpflichtenden Modelle auf JVM und zugesagten Browsern; bisherige Stackausnahmen sind offene Probleme. Ereignisdruck bleibt begrenzt. |
| P3-5 SDK, Plattformen und Gesamtgate (WP-33) | AC-P3-17: SDK-001–003, API-001–006, BUILD-006/009; AC-P3-18: BUILD-008, CLI-004, TEST-008; AC-P3-19: BUILD-001–008, TEST-001–013; AC-P3-20: kumulativ AC-P3-01–19 und G0–G3 | Ausgelieferte JARs/Launcher/SDK-Beispiele, getrennte Versionen und reproduzierbare Pakete. Echte Distribution-/CLI-/SDK-Prozesse auf Linux x64, Windows x64 und macOS arm64. Releaseprofil prüft frische passende Nachweise und scheitert bei Fehlbelegen. Vollständige aktuelle JVM-/Portable-/Browser-/Safari-/WebImage-/Conformance-/SDK-/Performance-/Plattformgates und unabhängiger Gesamtreview am sauberen Schlussprodukt-SHA. |

## Durchführung und unabhängige Reviews

Je Lieferung: konkrete Lücke/Red-Nachweis, kleinste vollständige Umsetzung,
zielgerichtete Green-Prüfung, kurzer Implementierungsbericht, sauberer Commit.
Erst danach unabhängiger neuer OpenCode-Kontext am exakten Kandidaten in einem
separaten sauberen Checkout, Provider deepseek/deepseek-flash direkt. Der ganze
Reviewprozess erbt die OS-Python-Ausführungssperre; kein Python/pip/uv, auch
nicht ad hoc. Eine neue eigene Reviewer-Gegenprobe und die Kriterienliste
sind verpflichtend. Findings werden selbständig behoben und neu geprüft.
Nach Teil-PASS beginnt automatisch die nächste Lieferung. Der letzte Review
prüft zusätzlich sämtliche P3-Kriterien und Wechselwirkungen kumulativ.

Reviewbericht bleibt unverändert. Ein anschliessender reiner Status-/Bericht-
commit darf folgen; Produkt-/Test-/Buildänderungen benötigen einen neuen
Kandidaten. Rohlogs/XML/JSON/Transportexit/Umgebungsmanifest bleiben unter build/.
Nutzerfreigabe vom 30. Oktober 2026: fehlende Windows-/Linux-Runner über eine neue GitHub-Action bereitstellen. Dafür benötigte Commits/Workflowausführung auf dem Arbeitsbranch sind gedeckt; kein Merge, Release oder sonstige Veröffentlichung.
P4/P5, Generatoren/IlisMeta, Legacy-Java-API, C/C++/iox bleiben ausserhalb P3.

## Offene Voraussetzungen

- Plattformvertrag bleibt Linux x64, Windows x64, macOS arm64. Nutzerantwort:
  «Verwende einen Runner in den Github Actions. Die Action gibt es aber noch nicht.»
  Ein neuer Workflow führt die Distribution-/CLI-/SDK-Prozesse auf Windows
  und Linux aus; macOS läuft zusätzlich lokal. Fehlende/fehlerhafte Nachweise
  halten G3 offen.
- Konkrete Performancebudgets werden anhand der spezifizierten Zielprofile und
  ersten tatsächlichen Messung getrennt von Istwerten dokumentiert; kein
  nachträgliches stilles Anheben zum Erzwingen von PASS.
