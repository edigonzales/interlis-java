# G2 – operativer Status

- Auftrag: **gesamte Phase 1** autonom bis zum unabhängigen Phasen-PASS,
  ohne manuelle Stopps zwischen L1-1, L1-2 und L1-3. Die Kandidatencommits,
  unabhängigen Reviews und PASS-Gates bleiben verbindlich. Phase 2 beginnt nicht.
- Aktuelle Lieferung: **L1-2**, AC1-03/AC1-04, **IN_PROGRESS**.
- Basis: L1-1-Code `6cbf8de422cd8c0c8c9ec1ea900d729053ea660f`;
  anschliessender Dokumentcommit speichert nur den unveränderten Bericht/Status.
- Letzter unabhängiger PASS: **L1-1**, AC1-01/AC1-02,
  [unveränderter Bericht](reviews/L1-1-review.md).
  OpenCode v2.0.28, **DeepSeek direkt**, `deepseek/deepseek-flash`
  (UI: DeepSeek V4.1 Flash 1017), sauberer separater Worktree am exakten SHA.
- Reviewer: frischer vollständiger L1-1-Lauf **Exit 0 / 2m 37s / 58 Tasks**;
  **739** reguläre JVM-, **136** Evidenz-, **15** Assertion- und **15** Hosttests,
  alle ohne Fehler/Skips. Node-JS/WasmGC jeweils **3299** Fälle; SDK **9**
  Prozesskommandos. Neue eigene Gegenprobe **409 Checks**, Exit 0.
- L1-1: gewöhnliche vordefinierte Geometrien, kanonische Modellowner,
  Import-/Präfixqueries, eindeutige Kontextorigins und Anhang-A-Mitgliederfolge.
  Beide früheren Findings sind unabhängig als behoben bestätigt; die
  unveränderten FAIL-Berichte bleiben unter [Reviewhistorie](reviews/) erhalten.
- T2: **593/610** ausgewählt; roh **580 PASS / 12 FAIL / 1 XFAIL**;
  Projekt **580 PASS / 13 XFAIL / 0 FAIL/ERROR/XPASS**, alle **18** Assessments PASS.
  Suite-/Archiv-/Original-/Normzitatpins und die sechs freigegebenen
  OID-/CONTRACTED-Erwartungen unverändert. **0** getrackte `.py`;
  OS-Interpreterproben ENOENT/EPERM, `pythonFreeCheck` erfolgreich.
- L1-2 vorbereitet: Unterteilungsregeln und CONTINUOUS-ohne-SUBDIVISION sind
  als aktuelle Falschakzeptanzen reproduziert; Referenzparität allein genügt
  für die Unterteilungsregeln nicht. Implementierung und Abnahme stehen aus.
- Ausdrückliche Nutzerentscheidungen, beide Sprachen:
  - TYPE MODEL: Strukturen und abstrakte Klassen wie ili2c zulassen.
  - Skalierte Grenzen: bestehende gleiche/absteigende/gemischte Exponentenfälle
    weiter zulassen; Werteordnung, Genauigkeit und Mantissenprüfungen erhalten.
  - Allgemeine Konstanten/Einheitenfaktoren: breiteres bestehendes
    Exponentialprofil erhalten, unabhängig von Typgrenzen.
  - Kontinuierliche Unterteilungen: Fälle ohne bestimmbaren Faktor erhalten;
    nachweisbare Faktorfehler ablehnen. Numerischer Vorgänger, Ganzzahligkeit,
    nichtnegative Grenzen und CIRCULAR werden geprüft; bekannte numerische
    Faktoren exakt verglichen; FUNCTION-Erklärungen nicht ausgeführt.
- Nachweise: [L1-1-Implementierungsbericht](implementation/L1-1.md).
- Nächster Schritt: L1-2 mit RED/GREEN-Regressionsfällen, öffentlichen
  Modell-/Queryinhalten und freigegebenen Profilen umsetzen, Kandidat committen
  und unabhängig prüfen. Nach PASS automatisch L1-3 beginnen.
- Vollständige Browser-/Safari-/Web-Image-Regression und kumulative
  **AC1-01–08** sind im selben L1-3-Review fällig. **Phase 1 hat noch keinen PASS.**

Verbindlich: [Plan](EXECUTION_PLAN.md), [Reviewanweisung](OPENCODE_REVIEW_PROMPT.md).
