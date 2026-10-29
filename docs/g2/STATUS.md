# G2 – operativer Status

- Aktuelle Lieferung: **L0A**, AC0A-01 bis AC0A-04.
- Status: **PASS** – unabhängiger OpenCode-Review vom 29.10.2026,
  alle vier Kriterien erfüllt; [Prüfbericht](reviews/L0A-review.md),
  [Implementierungsbericht](implementation/L0A.md).
- Letzter unabhängiger PASS-SHA: `a36bca9e6a71bcd1a15c8f5577d97a74b11b9fc1`, Basis
  `865c99bab6ccea4ca3900d897381fa3a2a41fe39`. Sauberer separater Review-Worktree:
  `/tmp/interlis-java-L0A-review-a36bca9`.
- Reviewprovider: **DeepSeek direkt**, OpenCode v2.0.28 mit `deepseek/deepseek-flash`
  (Anzeige „DeepSeek V4.1 Flash 1017“), gemäss Nutzerkorrektur auch für weitere Reviews.
  Der frühere OpenRouter-Blocker ist damit behoben. Aufruf, Handoff und Rohlog:
  `build/review/L0A/{handoff-deepseek.txt,opencode-deepseek.jsonl}`.
- Unabhängig frisch geprüft: **706 JVM-Tests**, volle T2 (Projekt 574 PASS / 19 XFAIL;
  roh 574 PASS / 18 FAIL / 1 XFAIL), je **3267** TeaVM-JS-/WasmGC-Fälle,
  59 Evidenz-/20 Wrappertests, SDK-Beispiel. Eigene Reviewer-Gegenprobe:
  **328 Checks, 0 Fehler** am Kandidaten; 64 Fehler am Basiscommit.
- Fehlende Voraussetzungen für L0A: **keine**; keine offenen Reviewer-Findings.
  Das Pilot-PASS erteilt keine Norm-, Plattformgesamt- oder G2-Gesamtfreigabe.
- Nächster fachlicher Schritt: **L0B-1** nach Plan (Conformance/normative Sonderfälle
  nach Java). Noch nicht begonnen; dieser Auftrag hat nur den L0A-Review wiederholt.

Verbindlich: [Plan](EXECUTION_PLAN.md), [Reviewanweisung](OPENCODE_REVIEW_PROMPT.md).
