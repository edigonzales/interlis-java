# HTTP-Netzwerkpolicy

Basis: 958f186a92361c3b827a5f0de13a8afee3fe0294; Arbeitsbaum zu Beginn sauber.
Cache-Logging ist bereits enthalten und bleibt erhalten. WP-26/30, RES-009,
CLI-001–007, API-001–006; gezielte Cache-/Diagnose-/SDK-Regression.

- NET-01: FetchPolicy mit Connect-/Read-/optionalem Requestlimit; 15/40/off,
  vollständige API-Migration und validierte positive, in Nanosekunden darstellbare Dauern.
- NET-02: Monotone Zeitmessung, Header-/Body-Inaktivität, Requestgesamtlimit,
  neue Budgets pro Redirect/Cache-Retry, Abbruch und keine Teilcachepublikation.
- NET-03: Systemdefault, direkt und expliziter HTTP-Proxy pro Instanz; HTTP und
  HTTPS-CONNECT, parallele Isolation, keine globalen Produktionsmutationen.
- NET-04: CLI/Properties, Herkunft, Konflikte und Vorabvalidierung; reale Prozesse.
- NET-05: Gezielt Cache/Offline/Pins/Locks/Diagnosen prüfen. Keine pauschalen
  Korpus-/Benchmark-/Browserläufe oder Wiederholungen durch Reviewer.

Review: fester Snapshotcommit, sauberer separater Checkout, Hashbindung von
Quellen/Tests/Distribution/Rohbelegen. Neue OpenCode-Sitzung mit
deepseek/deepseek-flash direkt über api.deepseek.com und Python-freiem Guard.
Originalbelegaudit und eigene gezielte Gegenproben; tatsächliche Prozess-/
Guardexits, Bericht, Urteil und Modellbindung aufbewahren. Abschluss nur PASS
ohne offene Muss-Findings. Kein Push/Release.
