# G2 – operativer Status

- Auftrag: **gesamte Phase 1** autonom bis zum unabhängigen Phasen-PASS,
  ohne manuelle Stopps zwischen L1-1, L1-2 und L1-3. Die Kandidatencommits,
  unabhängigen Reviews und PASS-Gates bleiben verbindlich. Phase 2 beginnt nicht.
- Aktuelle Lieferung: **L1-3**, AC1-05–08 und kumulatives Phasengate, **IN_PROGRESS**.
- Basis: L1-2-Code `39f48ca239c44b27a81e5562a58c717c90253534`;
  anschliessender Dokumentcommit speichert nur den unveränderten Bericht/Status.
- Letzter unabhängiger PASS: **L1-2**, AC1-03/AC1-04,
  [unveränderter Bericht](reviews/L1-2-review.md), SHA-256
  `9850d14c22c1abbb5b46206fc6fc74521343a49780d81a41a2b93629a7989eae`.
  OpenCode v2.0.28, **DeepSeek direkt**, `deepseek/deepseek-flash`
  (UI: DeepSeek V4.1 Flash 1017), sauberer separater Worktree am exakten SHA.
- Historischer L1-1-Reviewer: frischer vollständiger Lauf **Exit 0 / 2m 37s / 58 Tasks**;
  **739** reguläre JVM-, **136** Evidenz-, **15** Assertion- und **15** Hosttests,
  alle ohne Fehler/Skips. Node-JS/WasmGC jeweils **3299** Fälle; SDK **9**
  Prozesskommandos. Neue eigene Gegenprobe **409 Checks**, Exit 0.
- L1-1: gewöhnliche vordefinierte Geometrien, kanonische Modellowner,
  Import-/Präfixqueries, eindeutige Kontextorigins und Anhang-A-Mitgliederfolge.
  Beide früheren Findings sind unabhängig als behoben bestätigt; die
  unveränderten FAIL-Berichte bleiben unter [Reviewhistorie](reviews/) erhalten.
- Historisches L1-1-T2: **593/610** ausgewählt; roh **580 PASS / 12 FAIL / 1 XFAIL**;
  Projekt **580 PASS / 13 XFAIL / 0 FAIL/ERROR/XPASS**, alle **18** Assessments PASS.
  Suite-/Archiv-/Original-/Normzitatpins und die sechs freigegebenen
  OID-/CONTRACTED-Erwartungen unverändert. **0** getrackte `.py`;
  OS-Interpreterproben ENOENT/EPERM, `pythonFreeCheck` erfolgreich.
- L1-2 implementiert und gezielt geprüft: Unterteilungsregeln, exakte Faktoren,
  CONTINUOUS-ohne-SUBDIVISION, Domainqueries und freigegebene Profile.
  **20** gezielte numerische Tests erfolgreich. Letzter OS-gesperrter Vollauf
  `checkPortable g2EvidenceTest conformanceCheck sdkProcessCheck pythonFreeCheck --continue
  --rerun-tasks`: **Exit 0 / 2m 16s / 58 Tasks**, **753** reguläre JVM-Tests
  (davon **644** Compiler), **136** Evidenz-/ **15** Assertion-/ **15** Hosttests,
  alle ohne Fehler/Skips. Aktuell Node-JS/WasmGC jeweils **3365** Fälle
  erfolgreich, darunter Ressourcenabbruch. Unabhängig bestätigt: Exit 0 /
  2m 31s / 58 Tasks, gleiche Testzahlen, SDK9; eigene Gegenproben bestanden
  (62 öffentliche Checks, 16 CLI-, 6 Exponenten-, 6 TYPE-MODEL- und 6 Dimensionsfälle).
- Früher L1-2-T2-Zwischenlauf: **FAIL**, Projekt **579 PASS / 13 XFAIL / 1 FAIL**.
  Neuer Konflikt: `ili23.Attribute23Test.attributeComplete` enthält kontinuierliche
  Unterteilungen ohne CIRCULAR. Originalhash `f42135b02ec698de9bc62059c01d6585bf57e2822605913f46ce30d4145c5443`,
  eigener Exit 1, ili2c Exit 0; Kontrolle mit ausschliesslich ergänztem CIRCULAR
  bei beiden Exit 0. Die bereits bestätigte fortgeltende CIRCULAR-Prüfung
  bleibt massgeblich. Genau dieser Gegenfall ergänzt die bisherigen 18
  unveränderten Bewertungen als strenge 19. Ausnahme mit Original-/Kontrollhash
  und exakt zwei Diagnosen. Gezielter neuer Assessmentlauf **PASS**;
  vollständiger aktueller Lauf **PASS**: **579 PASS / 14 XFAIL / 0 FAIL**,
  roh **579 PASS / 13 FAIL / 1 XFAIL**, alle **19** Assessments PASS.
  Die unabhängige Prüfung bestätigt die Herleitung und sämtliche Pins.
  Eine separate explizite Freigabe des einzelnen Fixture-Namens wird nicht behauptet.
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
- Nachweise: [L1-1-Implementierungsbericht](implementation/L1-1.md),
  [L1-2-Implementierungsbericht mit konkretem Konflikt](implementation/L1-2.md).
- Nächster Schritt: L1-3-Vererbung, Assoziationen, effektive Herkunft und
  Transferreihenfolge anhand öffentlicher Gegenproben vervollständigen. Danach
  volle T1/T2/T3-Matrix und unabhängige kumulative AC1-01–08-Abnahme.
- Vollständige Browser-/Safari-/Web-Image-Regression und kumulative
  **AC1-01–08** sind im selben L1-3-Review fällig. **Phase 1 hat noch keinen PASS.**

Verbindlich: [Plan](EXECUTION_PLAN.md), [Reviewanweisung](OPENCODE_REVIEW_PROMPT.md).
