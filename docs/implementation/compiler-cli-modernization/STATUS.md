# Compiler-CLI – operativer Status

- Auftrag CLI1–CLI3 nacheinander gemäss EXECUTION_PLAN.md; nach belegtem PASS automatisch weiter. Zweig codex/compiler-cli-modernization.
- Auftragsbasis: c3d736ac82365524835adef8df40789f3fa3ae60, ursprünglich sauberer main-Stand. P3/G3-Baseline bleibt auf ihrem historischen SHA erhalten.
- **CLI1: PASS**, alle CLI1-01–09 am Produkt **ed25d5311346e429d201f5792ff8ff464577f3e7**.
- Unveränderter unabhängiger Bericht: [CLI1-r3-ed25d5311346-PASS.md](reviews/CLI1-r3-ed25d5311346-PASS.md), SHA256 **3ae6cebb9a3eed70f2874ec439d26040f261295bbf84b9cfba1789718726a967**. OpenCode2.0.28 / DeepSeek direkt / deepseek-flash; ProzessExit0, Guardpassed/Exit0, Kandidat und identischer Scratch-Baum sauber.
- Unabhängiges Gate: Exit0/2m36s/64 Tasks ausgeführt; 1196 JUnit/224 Suites und Node95 ohne Fehler/Skips; Conformance577PASS/16XFAIL/0FAIL/ERROR/XPASS, Norm21, SDK9/CLI19. Direkter SDK-Start, manipulierte Archive und weitere neue Gegenproben bestanden.
- Frühere CLI1-FAILs: beca84286ba964de7c5ccbf941f4c48c2c3c0055 (F1 SDK-Modus/Checker, F2 aktive Legacy-APIs); 9a8c4087591a06f1f6b9a43a71dee07623ef7f12 (nur F3 Formatbeispiel). Unveränderte Berichte unter reviews/; F1–F3 in R3 unabhängig geschlossen.
- **Aktuelle Phase: CLI2 / IN_PROGRESS**, CLI2-01–11. Phasenbasis bd42bb940af646f938c74bf5f9768cf9ad7eda8a. Tatsächliches RED/GREEN mit mehreren Quellen, zwei Modellen je Datei und fremder Warnungsablehnung belegt. Request-Diagnostik, DE-/EN-Formatter und Office-RelatedLocation implementiert; gezielte Tests Exit0. Vollständiges CLI2-Gate Exit0/4m57s/72 Tasks, Guardpassed/0; JUnit1214/227 ohne Fehler/Skips, Node95+18+4, JS/WasmGC je3558+24, Conformance577PASS/16XFAIL, semantischer Vergleich bestanden. Nächste Aktion: sauberer Kandidat und unabhängiger OpenCode-Review; noch kein CLI2-PASS.
- Office-Original-SHA bestätigt: fbb1bccc0e84795284f9fb1a3c0be9e6db516c3c4b8ae43ad2f856cf70948431.
- Rohläufe/Reviewaudits: build/review/compiler-cli/. Letzter Review: CLI1-r3-ed25d5311346/worktree/build/review/compiler-cli/CLI1-r3/.
- CLI2/CLI3 nicht abgenommen; keine Veröffentlichung, kein Push/Merge/Release.
