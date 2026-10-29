# G2 – operativer Status

- Aktuelle Lieferung: **L0B-2**, AC0B-04 bis AC0B-05.
- Status: **AWAITING_REVIEW** – Implementierung und eigene Prüfungen abgeschlossen;
  [Implementierungsbericht](implementation/L0B-2.md),
  [Migrationsmatrix](python-migration.md). Noch kein unabhängiges L0B-2-Urteil.
- Letzter unabhängiger PASS-SHA: `74f504405e726bf9922d19a228f424e3ff76421c`
  (L0B-1), [Prüfbericht](reviews/L0B-1-review.md).
- L0B-2-Basis: `2d33c71c7afde03b8f43a7923e0eaeb1a69d9045`.
- Eigene tatsächlich ausgeführte Prüfung: **728** reguläre JVM-Tests,
  **136** Java-Evidenztests, zusätzlich **15** frisch gebundene Compiler-
  Assertionmethoden, 0 Fehler/Skips; volle T2 **593/610**, Projektbilanz
  **574 PASS / 19 XFAIL**, alle **18** strengen Zusatzbewertungen bestehen.
- Python: **34 → 9** getrackte Dateien. `g2EvidenceTest` verwendet Java;
  SDK-/Referenz-/Corpus-/Benchmarkpfade bleiben L0B-3. Keine Pythonfreiheitsbehauptung.
- Reviewprovider: **DeepSeek direkt**, `deepseek/deepseek-flash`
  (Anzeige „DeepSeek V4.1 Flash 1017“), OpenCode v2.0.28, frischer Kontext.
- Fehlende Voraussetzungen: unabhängiger Review des vollständigen Kandidaten-SHA.
  Keine fällige eigene L0B-2-Prüfung offen. Normentscheidungen und volle
  Safari-/Browser-/Web-Image-/Pythonfreiheitsgates bleiben offen.
- Nächster Schritt: unabhängiger L0B-2-Review; **L0B-3 erst nach PASS**.

Verbindlich: [Plan](EXECUTION_PLAN.md), [Reviewanweisung](OPENCODE_REVIEW_PROMPT.md).
