# Compiler-CLI – operativer Status

**CLI1–CLI3 vollständig abgeschlossen; unabhängiges kumulatives PASS für alle
28 Kriterien.** Zweig codex/compiler-cli-modernization. Massgeblich bleibt
EXECUTION_PLAN.md. Auftragsbasis c3d736ac82365524835adef8df40789f3fa3ae60;
frühere G2-/P3-Abnahmen behalten ihre tatsächlichen historischen Produktstände.

| Phase | Geprüfter Produktcommit | Unveränderter unabhängiger Bericht |
|---|---|---|
| CLI1-01–09: PASS | ed25d5311346e429d201f5792ff8ff464577f3e7 | [CLI1-R3-PASS](reviews/CLI1-r3-ed25d5311346-PASS.md) |
| CLI2-01–11: PASS | dfd442c4a90d04a489bc48f1a6cbd52fc623435e | [CLI2-R2-PASS](reviews/CLI2-r2-dfd442c4a90d-PASS.md) |
| CLI3-01–08 und kumulativ CLI1/CLI2: PASS | 0136aec461b60c668c63344f7a30ee4bcec4fd78 | [CLI3-R2-PASS](reviews/CLI3-r2-0136aec461b6-PASS.md) |

CLI1-Bericht SHA256: 3ae6cebb9a3eed70f2874ec439d26040f261295bbf84b9cfba1789718726a967.
CLI2-Bericht SHA256: 1a6162944dec6a3ba4edcb511dede84a9d48bb6ac1cee5995c038de6efafbe97.
CLI3-Bericht SHA256: 84bad97078c1250aea35e5abe1d4fface02e1739c9f0315b1b07d6279f694035.

CLI3-Phasenbasis bleibt f24ce11a5616b64d6c912c230a781558ad0fae09;
CLI2-Phasenbasis bleibt bd42bb940af646f938c74bf5f9768cf9ad7eda8a.
OpenCode2.0.28 / DeepSeek direkt / deepseek-flash, bestätigte Zuordnung
DeepSeek-V4.1-Flash-1017. Jeder akzeptierte Review tatsächlich beendet mit
ProzessExit0 und originalem äusserem Guardmanifest passed/Exit0; jeweiliger
Kandidat/Review-HEAD unverändert und sauber. CLI3-R2-Sitzung:
ses_e6a4762deffelYicWMzBlH76C0. Keine offenen Muss-Findings oder Pflichtnachweise.

## Schlussqualifikation am exakten Produkt 0136aec461b6

- Eigener vollständiger releaseCheckP3 einschliesslich zusätzlicher Referenz-/
  Semantik-/13-Modell-Benchmark-/Modul-ZIP-Aufgaben: Exit0/31m36s; unabhängig
  erfolgreich32m20s. Tatsächlicher Graph193 Tasks,106 ausführbare Tasks ausgeführt.
- 1217 JUnit/227 Suites, null Failures/Errors/Skips; Node-Releasechecker98 und
  Performancechecker24; 26 echte CLI-/9 SDK-Prozesse auf macOS arm64, Linux x64
  und Windows x64. [Exakte CI41267880218](https://github.com/edigonzales/interlis-java/actions/runs/41267880218),
  beide Jobs success; Collector und Reviewer verifizierten Digest, RohXML und SHA.
- Conformance577PASS/16XFAIL/0FAIL/ERROR/XPASS, Norm21; Referenz5, Original-Views19/19,
  Views134/102/6 sichtbare Referenzprozessfehler, Units123/121/0;
  Semantik4 Fälle/8 portable Projektionen/8 Mutationen.
- JS/WasmGC/Web Image sowie reale Browser und Safari: je3560+24, vollständige
  Client-/Transport-/Neustart-/UI-Pflichten. Alle347 Closures/366 Dateien auf
  sechs Laufzeiten vollständig; null Skips und null semantische Abweichungen.
  2000 echte Editoränderungen, feste Performance-/Speicher-/Abbruch-/Retentionsbudgets.
- Zwei saubere Builds,76 bytegleiche Lieferdateien; SDK11 Einträge. Eigene externe
  Archive:10 SDK-/4 Modulprozesse,7 manipulierte Inventare/4 beschädigte SDK-ZIPs;
  unabhängige zusätzliche Diagnose-/Archiv-/Liefer-/CI-Gegenproben bestanden.
- SDK-ZIP SHA256: b95ec7acb0692d6776ed68dc278d399e87622051dcaf4a688ea3f0b23d093ac8.
  Quellfingerprint: 80cb0627db82b0b5b91cc54202465b74e632afba1078ddcf64b4d4eab394b071.
  Office-Original: fbb1bccc0e84795284f9fb1a3c0be9e6db516c3c4b8ae43ad2f856cf70948431,
  bytegleich erhalten, Namenskonflikt mit tatsächlicher verwandter Deklaration.

## Erhaltene Nacharbeit und Zwischenbefunde

CLI1-FAILs beca84286ba9 (SDK-Modus/Checker, aktive Legacy-APIs) und9a8c4087591a
(Formatbeispiel) sind in R3 unabhängig geschlossen. CLI2-R1-FAIL65ad26529bde
(no-model-Syntaxquelle/Bidi-Marken) bleibt unverändert erhalten, SHA256
bca0bf5ff4d29fcdf14bf21a951aee77849240a3d52fcaec629ddd3458fd551d;
F1/F2 in R2 unabhängig geschlossen.

[CLI3-R1-BLOCKED](reviews/CLI3-r1-0136aec461b6-BLOCKED.md) bleibt unverändert,
SHA2569a9e91e1e4c49a38ef1da95e7d70092a61f921225015879498779d747283325a.
26 Kriterien erfüllt, CLI3-05/08 offen, keine Produktfindings: zwei volle Safari-
Versuche bei gesperrtem Host endeten nach321/347 Roots; Root322 isoliert bestand.
Nach tatsächlichem Entsperren durch den Nutzer bestanden derselbe unveränderte
Kandidat und alle347 Safari-Roots sowohl eigenständig als auch im frischen R2.
Kein Timeout-/Budget-/Root-/Transportwechsel oder Snapshotreset als Ersatz.
Der R2-Bericht legt einen nachgelagerten zsh-Log-Wrapperfehler offen; der erfolgreiche
Gradle-/Releasegate und der danach separat geprüfte äussere Prozess-/Guardabschluss
sind davon getrennt dokumentiert. Keine globale Einstellung oder Authentifizierung
wurde verändert. Pins, Normen, XFAILs und Sprach-/Kontrollausgänge bleiben erhalten.

[CLI3-Umsetzung und vollständige Nachweise](implementation/CLI3.md).
Rohbelege/Audits unter build/review/compiler-cli/CLI3/ und
CLI3-r2-0136aec461b6/acceptance-audit.json; tatsächlicher Reviewworktree
CLI3-r2-0136aec461b6/worktree/build/review/compiler-cli/CLI3-r2/.
Der abschliessende reine Dokumentationscommit enthält Statusnotizen und unveränderte
Berichtskopien gemäss Plan§9.2. Nachweise benennen weiterhin den geprüften Produkt-SHA.
Der beauftragte Umfang ist abgeschlossen; kein nächster offener CLI-Arbeitsschritt.
