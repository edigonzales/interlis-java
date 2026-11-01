# Cachemeldungen pro Datei

Basis 59141fa; WP-26/30, RES-013, DIAG-002/003/006, LOG-002/003/005/008,
CLI-001–007. Auftrag: sichere Quellenbeobachtung und gemeinsame INFO-Darstellung,
anschliessend unabhängiger OpenCode-/DeepSeek-1017-Review am festen Kandidaten.

Abnahmekriterien:

- CL-01: Quellenbezogene Ereignisse für HTTP und beide Offlinepfade; alte
  Konstruktoren und Enum-Callbacks kompatibel, keine Duplikate, Observerfehler isoliert.
- CL-02: Benutzerinformationen, Querywerte, Fragmente und Header fehlen in
  veröffentlichten Beobachtungen; Renderer schützt zusätzlich vor Terminalkontrollen.
- CL-03: INFO/Information [ILI-RES-0010], gemeinsame DE-/EN-/Farb-/Rendererlogik;
  jede tatsächliche Beobachtung einschliesslich Abschlussprüfung sichtbar.
- CL-04: Nur bei Verbose und ohne Diagnose-JSON; keine zusätzliche Infomeldung
  für WRITE_UNAVAILABLE. Keine Änderung an Compilerdiagnosen, Gate, Warnungspolitik,
  JSON-Schema, Fachartefakten oder Exits.
- CL-05: Cachetexte korrekt bei bestätigtem starkem 304, Offline, Download,
  no-store/no-cache, kaputtem Cachekörper und unbestätigter/zweiter 304.
  CACHE_MISS behauptet weder zwingend fehlende Bytes noch einen weiteren Retry.
- CL-06: Die sieben jüngsten Cachekorrekturtests bleiben grün, optionale Sites
  bleiben optional; gezielte Unit-/HTTP-/Distributionsprozesse liefern Rohbelege.
- CL-07: Katalog v3, API-/Fachverträge und Status synchron; schmale portable
  Versions-/Fähigkeitsgegenprobe. Keine Korpus-/Benchmark-/Browservollwiederholung.
- CL-08: Tatsächlich abgeschlossener unabhängiger OpenCode-Review mit DeepSeek
  V4.1 Flash 1017 direkt über api.deepseek.com, sauberer separater Checkout,
  Rohbelegaudit und eigene gezielte Gegenproben, PASS ohne offene Muss-Findings.

Reviewzyklus: lokale Kandidatencommits, neuer Kandidat nach Produktkorrekturen,
unveränderte Reviewerberichte und tatsächliche Prozess-/Guardexits aufbewahren.
