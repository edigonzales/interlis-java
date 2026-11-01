# Unabhängiger Cache-Review – FAIL

Geprüfter Commit: `cf585ec1ea2270bafed4083a8ebfa2e68de05133` auf Basis
`fd07e194f14c8ee46debe930871a6c18fb72242d`, am 01.11.2026.
Reviewer: OpenCode 2.0.28 mit `deepseek/deepseek-flash` direkt über
`https://api.deepseek.com`. Die [offizielle Modelltabelle](https://api-docs.deepseek.com/quick_start/pricing/)
ordnet diesen API-Namen zum Prüfzeitpunkt DeepSeek V4.1 Flash 1017 zu.
Die exportierten Assistantnachrichten beider Sitzungen nennen ausschliesslich
Provider deepseek und Modell deepseek-flash. Keine Provider-/Modellfallbacks.

## Ergebnis und nächste Korrekturen

**FAIL: zwei reproduzierte P2-Findings.** Die Implementierung wurde nicht
geändert. Die neuen Prüfergebnisse ersetzen kein Implementierungsartefakt;
sie belegen offene Korrekturen am geprüften Commit.

1. **P2 – Schwache HTTP-Validatoren können bytegenaue Prüfungen umgehen.**
   `HttpSourceLoader.java:68–97` akzeptiert 304 auch bei schwachen ETags und
   Last-Modified. Ein legitimer Server kann andere Bytes bereitstellen, während
   diese Validatoren noch passen. Der Cache liefert dann alte Bytes: Ein
   Online-Lauf mit Lock meldet Erfolg, derselbe Aufruf mit `--no-cache` erkennt
   LOCK_MISMATCH. Auch CHANGED_SOURCE innerhalb der Beschaffung kann unentdeckt
   bleiben. Bei Last-Modified wurde eine semantische Modelländerung reproduziert.
   Bytegenaue Pin-/Lock-/Abschlussprüfungen benötigen einen ausreichend starken
   Nachweis oder eine unbedingte Beschaffung; dazu gezielte Regressionstests und
   die Präzisierung der 304-Regel in Kapitel 08.
   [Unveränderter Reviewerbericht](weak-validators/review.md),
   [Urteil](weak-validators/verdict.json).

2. **P2 – Fehlendes optionales ilisite.xml wird als erforderliche Quelle behandelt.**
   Bei Offlineauflösung ohne Lock, gecachtem Index und einem dort nicht
   vorhandenen Modell wird der optionale Site-Miss aus der gemeinsamen
   unavailable-Map als LOAD_FAILURE/OFFLINE hochgestuft
   (`RepositoryClosureService.java:237–241`). Erwartet ist eine sichtbare
   Suchwarnung mit NOT_FOUND für das Modell. Betroffen sind Diagnosecode,
   gemeldete Quelle und die in-process CLI-Klassifikation; beide Prozesse
   verwenden weiterhin Exit 1. Optionale Site-Ausfälle von erforderlichen
   Index-/Modell-Misses trennen und den Fall als Regressionstest ergänzen.
   [Unveränderter Reviewerbericht](r1/review.md), [Urteil](r1/verdict.json).

## Durchführung und Nachweise

- Beide Prüfungen liefen auf demselben sauberen detached Worktree und unverändertem
  Kandidaten. Die Zusatzfrage zu schwachen Validatoren wurde nach Prüfung von
  RFC 9110 durch den Koordinator gezielt gestellt; ein eigener frischer
  OpenCode-/DeepSeek-Kontext hat sie unabhängig bewertet und reproduziert.
- Allgemeiner Review: Sitzung `ses_e680e980bffefzeu8HyV4e4y5B`.
  Zehn Kriterien, davon CACHE-04/07 verletzt. Die vorhandenen 101 JUnitfälle in
  19 Suites und 15 Prozessbelege wurden unabhängig auditiert, nicht pauschal
  wiederholt. 288 Produktions-/Buildinput-Hashes, 19 Testquellen und sieben
  finale JARs stimmen. 61 zusätzliche Standardprüfungen bestehen; eine gesonderte
  Gegenprobe reproduziert den optionalen Sitefehler.
- Validatorreview: Sitzung `ses_e6803ef45ffeWklH04mM2Ajpd3`. WEAK-01 verletzt.
  Beide Serverfälle verwenden korrekte schwache Validatoren, keine erfundenen
  unveränderten starken ETags. Aufbewahrt sind Originalbytes, SHA-256,
  HTTP-Anfragen/Antwortzähler, tatsächliche Exits und Lockdateien.
- Beide OpenCode-Prozesse sind regulär mit Exit 0 beendet. Das ist der
  Prozessstatus; **beide fachlichen Urteile lauten FAIL**. Die jeweiligen
  Prozessguards melden passed/Exit 0, der Worktree ist am Ende sauber und auf
  dem Kandidaten geblieben. Keine Korpus-, Benchmark-, Browser-, Plattform-
  oder Releasevollprüfung; kein Gradle durch die Reviewer.
- Berichte, Urteile und ausgewählte Gegenbelege sind unverändert in r1 bzw.
  weak-validators gespeichert. `run-manifest.json` je Verzeichnis bindet die
  kopierten Dateien per SHA-256 an Prozess-, Guard- und Sessionnachweise.
  Vollständige ursprüngliche Transkripte und Scratchdaten verbleiben unter
  `build/review/repository-cache/`.

## Einordnung kleiner Nachweisungenauigkeiten

Der erste Reviewer weist auf die bereits später qualifizierte Formulierung
«am finalen Produkt» im ursprünglichen Implementierungsbericht hin: Die fünf
früheren Validatorprozesse liefen vor einer letzten Diagnosepräzisierung.
Die Produktstände und JAR-Hashes sind dort getrennt; die neuen eigenen Proben
verwenden die finale Distribution. Dies ist kein weiteres Produktfinding.

Im generierten `weak-validators/counterprobes/evidence-manifest.json` wurden zwei
Kontrollkommandos fälschlich mit --cache statt --no-cache transkribiert.
Originalmanifest und Reviewerberichte bleiben unverändert. Die ursprünglichen
Toolbefehle und Ergebnisse sind zusätzlich in
`weak-validators/counterprobes/executed-command-blocks.json` aufbewahrt; sie
belegen tatsächlich --no-cache. Das unveränderte verdict.json nennt diese
beiden Kommandos ebenfalls korrekt. Diese Transkriptionsungenauigkeit wurde
beim Abschlussaudit erkannt und verändert keinen der reproduzierten Befunde.

Keine Korrektur, kein Commit und kein Push im Reviewauftrag ausgeführt.
Nächster fachlicher Schritt: beide P2-Fälle gezielt beheben und die betroffenen
Gegenproben unabhängig am neuen Produktstand prüfen.
