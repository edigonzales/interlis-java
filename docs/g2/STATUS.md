# G2 – operativer Status

- Aktuelle Lieferung: **L0B-1**, AC0B-01 bis AC0B-03.
- Status: **AWAITING_REVIEW** – direkter gepinnter Java-Runner und Java-/JUnit-Normprüfungen
  implementiert; 728 JVM-Tests, volle T2 und Alt-/Neu-Parität für 593 Fälle und
  alle 18 Normbewertungen geprüft. Manipuliertes Archiv wird abgelehnt.
  [Implementierungsbericht](implementation/L0B-1.md).
- Basis: `c671ce3aa943afcc53579b4cd488773afbd0206e`, beim Start sauber.
- Letzter unabhängiger PASS-SHA: **L0A**,
  `a36bca9e6a71bcd1a15c8f5577d97a74b11b9fc1`;
  [Prüfbericht](reviews/L0A-review.md).
- Reviewprovider: **DeepSeek direkt**, `deepseek/deepseek-flash`
  (Anzeige „DeepSeek V4.1 Flash 1017“), OpenCode v2.0.28, frischer Kontext und
  sauberer separater Worktree je Kandidat.
- Python: 36 Dateien an der Basis; zwei Dateien in L0B-1 entfernt, 34 verbleiben.
  Die [Migrationsmatrix](python-migration.md) erfasst alle 36; restliche Migration
  und Python-freies Phasengate bleiben L0B-2/L0B-3 vorbehalten.
- Fehlende Voraussetzungen: unabhängiges Urteil für den L0B-1-Kandidaten
  einschliesslich eigener neuer Manipulationsprobe. Kein selbst erteiltes PASS.
- Nächster Schritt: Bericht und Kandidat committen, OpenCode
  direkt über DeepSeek prüfen lassen. **L0B-2 beginnt erst nach PASS.**

Verbindlich: [Plan](EXECUTION_PLAN.md), [Reviewanweisung](OPENCODE_REVIEW_PROMPT.md).
