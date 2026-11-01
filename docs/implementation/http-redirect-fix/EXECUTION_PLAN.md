# Ungültige HTTP-Redirects – gezielter Fix

WP-26/30, RES-009, CLI-001–007; Ausgangsbefund OBS-02 aus dem unabhängigen
HTTP-Netzwerkpolicy-Review auf 53eb35743ee3e51afa809f74daea575c5a32ccf5.
Benutzerbranch weiterhin 958f186; die bereits implementierte Netzwerkpolicy
liegt unverändert im Arbeitsbaum. Vorhandene Änderungen bleiben erhalten.

- REDIR-01: Syntaktisch ungültige Location und nicht anfragbare HTTP-Ziele ergeben
  SourceLoadException(IO_FAILURE), keine rohe IllegalArgumentException. Dieselbe
  enge Fehlerabbildung gilt für ungültige initiale HTTP-Adressen; Programmierfehler
  ausserhalb der URI-/Requestkonstruktion bleiben sichtbar.
- REDIR-02: Fehlertexte und öffentliche Diagnosen enthalten keine Locationwerte,
  Zugangsdaten oder Querygeheimnisse. Ein fehlerhafter Redirect publiziert weder
  Cacheinhalt noch Download-Erfolgsereignis.
- REDIR-03: Gültige relative Redirects, Unsupported-Scheme-, Redirectlimit-,
  Timeout-/Abbruch- und Cacheverträge bleiben erhalten. Gezielte Loader-/Cachefälle.
- REDIR-04: CLI-Befehle liefern einen Beschaffungsfehler/Exit1 statt internem
  Fehler/Exit3. Echte Distributionsprozesse prüfen Fehler- und Erfolgsfälle.
- REDIR-05: Unabhängiger OpenCode/deepseek-flash-Review direkt über api.deepseek.com
  am festen Snapshot im sauberen Checkout; Rohbelegaudit, eigene Gegenprobe,
  regulärer ProzessExit0, Guard passed und PASS ohne offene Muss-Findings.

Java25 einschliesslich Gradle-Launcher/Daemon, Repositorywrapper. Nur gezielte
Tests; kein Korpus-/Benchmark-/Browser-/Releasevollauf oder Push/Release.
Historische Reports und der ursprüngliche OBS-02 bleiben unverändert erhalten.
