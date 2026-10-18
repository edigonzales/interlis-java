# P1 evidence

This directory contains the executed P1 evidence for the new Gradle build. It
is not a rewrite of `docs/evidence/wasm-spike`, whose Maven/Node history keeps
its original status. See `test-summary.md` and `toolchain.json`.

- `wasm-contract-result.json` – TeaVM WasmGC under Node.
- `js-contract-result.json` – TeaVM JavaScript under Node.
- `browser-results.json` – Chromium, Firefox, WebKit via Playwright.
- `safari-result.json` – real Safari WebDriver browser contract; `passed`.
- `webimage-result.json` – GraalVM Web Image Node probe; PASS.
- `webimage-browser-results.json` – Web Image in Chromium, Firefox, WebKit; PASS.
- `webimage-comparison.json` – GraalVM 25.0.3 vs 25.4.4 Web Image sizes/outcomes.
- `antlr-warning.txt` – reproduced ANTLR 154 warning for the pinned grammar.
- `javadoc-summary.json` – measured API-003 Javadoc warning baseline; not a waiver.
- `api-review.md` – reviewed core API contracts and remaining API-003 gaps.
- `../reference/` – pinned ili2c 5.6.9 small-profile reference run.
