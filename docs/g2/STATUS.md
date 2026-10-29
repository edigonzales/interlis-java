# G2 – operativer Status

- Aktuelle Lieferung: **L0B-3 / Phase 0b abgeschlossen**.
- Status: **PASS** – unabhängiger OpenCode-/DeepSeek-Review hat **AC0B-01 bis
  AC0B-08** einschliesslich des kumulativen Python-freien Phasengates geprüft.
- Unabhängig geprüfter Code-SHA:
  `d67b0a669ac41261078fe02dafe1544e2c1ef3bb`.
  [Unveränderter Prüfbericht](reviews/L0B-3-review.md), SHA-256
  `611d610d21c76ce9565f0220dbaa076808c54d3ba51ba16d0b9b8cda96dbc6ba`.
- L0B-3-Basis: `abde84ef666184e128827894d63e176709e7c3db`.
  [Implementierungsbericht](implementation/L0B-3.md),
  [Migrationsmatrix](python-migration.md).
- Erster Kandidat `7547fc88fd096753a89fed93bc16a2489542e12f` blieb **FAIL**,
  [unveränderter erster Prüfbericht](reviews/L0B-3-review-7547fc8.md).
  F1 (Scanner übersah Env-Aufruf) korrigiert und mit neuen Gegenproben geprüft;
  F2 (unterbrochener Reviewlauf) durch vollständigen frischen Lauf behoben.
- Unabhängiger Gesamtlauf am sauberen Code-SHA: **Exit 0, 5m 33s, 79 Tasks**,
  `--no-daemon`, privater PATH und vererbte OS-Ausführungssperre. Interpreter-
  Proben über PATH ENOENT, absolute Python-/pip-Starts EPERM; Hostinstallationen
  bleiben vorhanden. Firefox verwendet nur in dieser Umgebung die dokumentierte
  Sandboxanpassung; die äussere Ausführungssperre bleibt aktiv.
- JVM: **730** reguläre Tests, **136** Evidenztests, **15** frische
  Assertionmethoden und **15** Host-/Kompatibilitätstests; alle **0**
  Failures/Errors/Skips. Portable-/Browser-/Safari-/Web-Image-Läufe jeweils
  **3279** Fälle erfolgreich, keine funktionalen Skips.
- T2: **593/610** selektiert, roh **580 PASS / 12 FAIL / 1 XFAIL**;
  Projekt **580 PASS / 13 XFAIL / 0 FAIL/ERROR/XPASS**. Alle **18** zusätzlichen
  Original-/Kontroll-/Diagnoseprüfungen erfolgreich. Suitepins unverändert.
- Nutzerentscheidungen vom 29.10.2026: Metadaten-Kompatibilitätsfix samt API/Spec
  vorziehen; 2.3-OID/BASKET-OID ohne CONTRACTED bewusst zulassen. Genau sechs
  betroffene Suitefälle sind reguläre PASS. Die übrigen zwölf Normausnahmen
  und offene spätere Normentscheidungen bleiben davon unberührt.
- Python: **0** getrackte `.py`; alle **36** Ausgangsdateien klassifiziert und
  entfernt. Eigene Reviewer-Canarys gegen Scanner/Gradle und die echte OS-Sperre
  belegen die Fehlpfade. Der Reviewer legt einen versehentlichen leeren Python-
  Hostaufruf ausserhalb der Gates offen; er erzeugte keine Evidenz. Die Gates
  selbst liefen vollständig unter der Interpreter-Ausführungssperre.
- SDK **9** Prozesse, Referenz **5/5**, Original-Views **19/19**, Corpus und JVM-
  Benchmark **13**. Quellen-/Indexpins unverändert; Seed enthält exakt dieselben
  14 Originaldateien. Views **134** eigene Erwartungen / **102** Referenz-
  übereinstimmungen / **6** separat sichtbare Referenzprozessfehler; Units
  **123/121/0**. Wasmbenchmark **4** Modelle; Browsermessungen **12/12/1** mit
  historischen Benchmarkskips **1/1/12**. Keine P3-Performanceabnahme.
- Reviewprovider: **DeepSeek direkt**, `deepseek/deepseek-flash`
  („DeepSeek V4.1 Flash 1017“), OpenCode v2.0.28, frischer separater Worktree.
- Keine offenen MAJOR-/BLOCKER-Findings oder nicht ausgeführten fälligen Gates.
  Nicht blockierende Beobachtungen **O1–O3** bleiben im Prüfbericht: historische
  Entscheidungspaket-Prosa, Scanner-Randform `- run:` in YAML (aktuell unbenutzt),
  fehlende explizite Fallzahlassertion in Live-Browsercontracts. Kein zusätzlicher
  fachlicher Abschluss aus dem Phase-0b-PASS abgeleitet.
- Nächster fachlicher Schritt: **L1-1** nach Plan; noch nicht begonnen.
  G2 insgesamt und P3/P4/P5 sind nicht abgeschlossen.

Dieser nachfolgende Commit speichert nur den unveränderten Reviewbericht und
diesen Status; er verändert den geprüften Produktcode nicht.

Verbindlich: [Plan](EXECUTION_PLAN.md), [Reviewanweisung](OPENCODE_REVIEW_PROMPT.md).
