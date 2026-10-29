# G2 – operativer Status

- Aktuelle Lieferung: **L0B-1**, AC0B-01 bis AC0B-03.
- Status: **PASS** – unabhängiger OpenCode-Review vom 29.10.2026,
  alle drei Kriterien erfüllt; [Prüfbericht](reviews/L0B-1-review.md),
  [Implementierungsbericht](implementation/L0B-1.md).
- Letzter unabhängiger PASS-SHA: `74f504405e726bf9922d19a228f424e3ff76421c`,
  Basis `c671ce3aa943afcc53579b4cd488773afbd0206e`.
  Eigener Review-Worktree: `/tmp/interlis-java-L0B-1-review-74f5044`.
- Reviewprovider: **DeepSeek direkt**, `deepseek/deepseek-flash`
  (Anzeige „DeepSeek V4.1 Flash 1017“), OpenCode v2.0.28, frischer Kontext.
  Handoff und Rohlog: `build/review/L0B-1/{handoff-r3.txt,opencode-r3.jsonl}`.
- Unabhängig frisch geprüft: **728 JVM-Tests**, 0 Fehler/Skips; vollständige
  T2 mit **593/610** Fällen (roh 574 PASS / 18 FAIL / 1 XFAIL;
  Projekt 574 PASS / 19 XFAIL), **18/18** strenge Normbewertungen,
  **22 Original-/23 Kontrollhashes**, eigene Alt-/Neu-Parität,
  reale Cache-/UTF-16-/Kontrollhash-Manipulationsproben. Beide Cachekorrekturen bestätigt.
- Python: **36 → 34** getrackte Dateien; die beiden Conformance-Pythondateien
  sind entfernt. [Migrationsmatrix](python-migration.md) für alle 36 Basisdateien.
  Keine Pythonfreiheitsbehauptung; der übrige aktive Pythonpfad bleibt L0B-2/L0B-3.
- Fehlende Voraussetzungen für L0B-1: **keine**, keine offenen Muss-Findings.
  Normentscheidungen und die vollständigen Safari-/Browser-/Web-Image-/Pythonfreiheitsgates
  bleiben offen; L0B-3 ist das kumulative 0b-Phasengate.
- Nächster fachlicher Schritt: **L0B-2** (aktive Evidenz-/Normverträge nach Java).
  Noch nicht begonnen.

Verbindlich: [Plan](EXECUTION_PLAN.md), [Reviewanweisung](OPENCODE_REVIEW_PROMPT.md).
