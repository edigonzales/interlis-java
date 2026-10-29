# G2 – operativer Status

- Aktuelle Lieferung: **L0A**, AC0A-01 bis AC0A-04.
- Status: **AWAITING_REVIEW** – RED für beide Sprachen reproduziert; Fix, 706 JVM-Tests,
  volle T2 und je 3267 TeaVM-JS-/WasmGC-Fälle ausgeführt. Siehe [Bericht](implementation/L0A.md).
- Letzter unabhängiger PASS-SHA nach dem neuen Plan: **keiner**.
- Fehlende Voraussetzungen: unabhängiges OpenCode-Urteil für den Kandidaten,
  einschliesslich eigener neuer Lexer-Gegenprobe. Kein selbst erteiltes PASS.
- Kandidat: `a36bca9e6a71bcd1a15c8f5577d97a74b11b9fc1`, Basis
  `865c99bab6ccea4ca3900d897381fa3a2a41fe39`. Sauberer separater Review-Worktree:
  `/tmp/interlis-java-L0A-review-a36bca9`.
- Technischer Reviewblocker: OpenCode v2.0.28 mit
  `openrouter/deepseek/deepseek-v4.1-flash-1017` wurde zweimal in frischem Kontext
  aufgerufen. Beide Prozesse endeten mit Exit 1, OpenRouter **HTTP 402 / provider.quota**
  vor jeder fachlichen Prüfung: 256000 angeforderte Ausgabetokens übersteigen das
  Kreditlimit (10767 finanzierbare Tokens). Der versuchte aufruflokale
  `max_tokens=4096`-Override wurde nicht wirksam. Kein unabhängiger Prüfbericht
  und kein Urteil liegen vor. Logs und kopierbarer Handoff unter `build/review/L0A/`.
- Nächster Schritt: Antwortlimit bzw. Providerzugang in OpenCode herstellen und den
  unabhängigen Review am genannten Kandidaten samt eigener Gegenprobe ausführen.
  Bei FAIL dieselbe Lieferung nacharbeiten.
  **L0B-1 beginnt erst nach PASS.**

Verbindlich: [Plan](EXECUTION_PLAN.md), [Reviewanweisung](OPENCODE_REVIEW_PROMPT.md).
