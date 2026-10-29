# G2 – operativer Status

- Aktuelle Lieferung: **L0B-2**, AC0B-04 bis AC0B-05.
- Status: **PASS** – unabhängiger OpenCode-Review vom 29.10.2026,
  beide Kriterien erfüllt; [Prüfbericht](reviews/L0B-2-review.md),
  [Implementierungsbericht](implementation/L0B-2.md).
- Letzter unabhängiger PASS-SHA: `411585a650b4da42cc09dd0efbf3157697dcc178`,
  Basis `2d33c71c7afde03b8f43a7923e0eaeb1a69d9045`.
  Eigener Review-Worktree: `/tmp/interlis-java-L0B-2-review-411585a`.
- Reviewprovider: **DeepSeek direkt**, `deepseek/deepseek-flash`
  (Anzeige „DeepSeek V4.1 Flash 1017“), OpenCode v2.0.28, frischer Kontext,
  Prozess-Exit **0**. Handoff und Rohlog: `build/review/L0B-2/{handoff.txt,opencode.jsonl}`.
- Unabhängig frisch geprüft: **728** reguläre JVM-Tests, **136** Java-Evidenztests,
  zusätzlich **15** Compiler-Assertionmethoden (27 gebundene Evidenzlinks),
  0 Fehler/Skips; volle T2 **593/610**, Projektbilanz **574 PASS / 19 XFAIL**,
  alle **18** strengen Zusatzbewertungen bestehen.
- Eigene Review-Gegenproben: falsche JUnit-Klasse bei richtiger Methode und
  aktualisiertem Hash → Exit **1**; falsches Cache-PDF → Exit **1**.
  Originalextraktion unabhängig bestätigt: **435** Seiten, **2721** Absätze,
  **311** Abschnitte, **369** Compilerbedingungen und **299** Exzerpte exakt.
- Python: **34 → 9** getrackte Dateien; `g2EvidenceTest` verwendet Java.
  [Migrationsmatrix](python-migration.md) begründet alle 25 weiteren Löschungen.
  Keine Pythonfreiheitsbehauptung des Gesamtprojekts.
- Fehlende Voraussetzungen für L0B-2: **keine**, keine offenen Muss-Findings.
  Normentscheidungen und volle Safari-/Browser-/Web-Image-/Pythonfreiheitsgates
  bleiben offen; L0B-3 ist das kumulative 0b-Phasengate.
- Nächster fachlicher Schritt: **L0B-3** (übrige Werkzeuge und Python-freies
  Gesamtphasengate). Noch nicht begonnen.

Verbindlich: [Plan](EXECUTION_PLAN.md), [Reviewanweisung](OPENCODE_REVIEW_PROMPT.md).
