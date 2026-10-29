# G2 – operativer Status

- Aktueller Auftrag: **gesamte Phase 1**, ohne manuelle Stopps zwischen L1-1,
  L1-2 und L1-3. Kandidatencommits und unabhängige Reviews bleiben verbindlich;
  technische Findings werden selbständig korrigiert. Phase 2 ist nicht begonnen.
- Aktuelle Lieferung: **L1-1**, AC1-01/AC1-02, **AWAITING_REVIEW**.
- Basis: `b0032e9627fc76128aade70c9cd98985cb0b2b98`.
- Letzter unabhängiger PASS: **L0B-3 / Phase 0b**, Code
  `d67b0a669ac41261078fe02dafe1544e2c1ef3bb`,
  [unveränderter Bericht](reviews/L0B-3-review.md).
- L1-1 implementiert: gewöhnliche vordefinierte Geometriestrukturen,
  kanonische sprachabhängige INTERLIS-Modellowner, Import-/Präfixqueries und
  eindeutige kanonische gegenüber kontextbezogener Herkunft.
- Nutzerentscheidung 29.10.2026: **TYPE MODEL akzeptiert Strukturen und
  abstrakte Klassen wie ili2c**, in 2.3 und 2.4. Topics, konkrete Klassen,
  Laufzeitparameter und Metadatenbehälter bleiben verboten. Die vorher
  freigegebene OID-/CONTRACTED-Kompatibilität bleibt erhalten.
- Eigene Prüfungen: frisches `check --rerun-tasks` **Exit 0 / 21s / 45 Tasks**;
  **737** reguläre JVM-, **136** Evidenz-, **15** Assertion- und **15** Hosttests,
  alle ohne Fehler/Skips. Node-JS/WasmGC jeweils **3299** gemeinsame Fälle unter
  der OS-Interpreter-Ausführungssperre erfolgreich. Alle **6** gepinnten
  Geometrie-Originalquellen mit aktuellem Compiler und ili2c **Exit 0**.
- T2: **593/610**, roh **580 PASS / 12 FAIL / 1 XFAIL**;
  Projekt **580 PASS / 13 XFAIL / 0 FAIL/ERROR/XPASS**, alle **18** Zusatzprüfungen
  erfolgreich. Suite-/Archiv-/Original-/Normzitatpins unverändert.
- Python: **0** getrackte `.py`; `pythonFreeCheck` erfolgreich.
- Nachweise und Grenzen: [L1-1-Implementierungsbericht](implementation/L1-1.md).
  Die vollständige Browser-/Safari-/Web-Image-Phasenregression ist L1-3 fällig.
- Review: OpenCode v2.0.28, **DeepSeek direkt**, `deepseek/deepseek-flash`
  („DeepSeek V4.1 Flash 1017“), frischer separater Worktree am exakten SHA.
- Nächster Schritt: L1-1 unabhängig prüfen; nach aktuellem **PASS automatisch
  L1-2** beginnen. Bei FAIL im selben Abschnitt korrigieren und neu reviewen.
- Keine Vorabfreigabe für L1-2/L1-3, Phase 1 insgesamt oder G2. Die drei nicht
  blockierenden Phase-0b-Beobachtungen bleiben im zugehörigen Review dokumentiert.

Verbindlich: [Plan](EXECUTION_PLAN.md), [Reviewanweisung](OPENCODE_REVIEW_PROMPT.md).
