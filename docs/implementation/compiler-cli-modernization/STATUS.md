# Compiler-CLI – operativer Status

- Auftrag: CLI1–CLI3 nacheinander gemäss EXECUTION_PLAN.md; nach unabhängigem PASS automatisch weiter.
- Auftrags-/CLI1-Basis: `c3d736ac82365524835adef8df40789f3fa3ae60`, sauberer Arbeitsbaum, Zweig `codex/compiler-cli-modernization`.
- Aktuelle Phase: **CLI1 / AWAITING_REVIEW**, Kriterien CLI1-01–09.
- Baseline: P3/G3-PASS am Produkt `4b586b1e09a2f1cbd200f7d00876e44c81df2169`; historische Berichte bleiben unverändert.
- Office-Original: vor Änderung SHA-256 `fbb1bccc0e84795284f9fb1a3c0be9e6db516c3c4b8ae43ad2f856cf70948431` bestätigt.
- OpenCode-Preflight: v2.0.28, gespeicherte DeepSeek-Anmeldung, `deepseek/deepseek-flash`, Katalogendpoint `https://api.deepseek.com`; offizielle Modellzuordnung am 31.10.2026 als V4.1-Flash-1017 bestätigt. Frischer privater Standaloneaufruf antwortet READY, Exit 0; Python-Guard passed mit PATH-ENOENT und absoluten EPERM-Proben. Rohdaten `build/review/compiler-cli/preflight/`.
- Letzter unabhängiger Produkt-PASS dieses Auftrags: keiner.
- CLI1 implementiert und vollständiges Arbeitsstandgate verifiziert: Exit0/3m21s/64 Tasks, 1196 JUnit/224 Suites ohne Fehler/Skips, Node94, Conformance577PASS/16XFAIL, Norm21, SDK9/Standardprozesse19 und entpackter SDK-Consumer; Python-Guard passed. Details implementation/CLI1.md.
- Unabhängiger R1: **FAIL** am Kandidaten beca84286ba964de7c5ccbf941f4c48c2c3c0055. Bericht reviews/CLI1-r1-beca84286ba9-FAIL.md, SHA256 226081f6d0ca1490e975f9e264fb3a95306713f5cf62e2ba70a7436396624104; ProzessExit0/Guardpassed. F1 SDK-Launcher0644 im ZIP durch Checkerreparatur verdeckt, F2 zwei aktive Legacy-API-Stellen.
- F1/F2 korrigiert; tatsächlicher alter-SDK-RED und neuer-SDK-GREEN ohne chmod belegt. Vollständiges Nacharbeitsgate Exit0/2m33s/64 Tasks, 1196 JUnit/224 Suites, Node95 ohne Fehler/Skips, Conformance577/16, SDK9/Standard19, nativer SDK-Consumer und Guardpassed. Nächste Aktion: neuer Kandidatencommit, frischer unabhängiger R2. Nach gültigem PASS automatisch CLI2.

- Unabhängiger R2: **FAIL** am Produkt9a8c4087591a06f1f6b9a43a71dee07623ef7f12; F1/F2 geschlossen, nur F3 (format-Beispiel nennt Modul statt Programm) offen. Bericht reviews/CLI1-r2-9a8c4087591a-FAIL.md, SHA256 1c3ca08b55c31b4f03663690d3ab42d07ec115d896ea31cfc94a4eb0ecbfc86c; ProzessExit0/Guardpassed.
- F3 korrigiert und dokumentierter Aufruf tatsächlich mit Exit0 ausgeführt. Vollständiges neues Gate Exit0/3m21s/64 Tasks, JUnit1196/224 und Node95 ohne Fehler/Skips, Conformance577/16, Norm21, SDK9/CLI19 und nativer SDK-Consumer. Nächste Aktion: Kandidatencommit und unabhängiger R3; danach bei PASS automatisch CLI2.
