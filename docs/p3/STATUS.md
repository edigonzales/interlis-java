# P3 – operativer Status

- **IN ARBEIT**, Auftrag gesamte P3-Folge autonom; kein G3-PASS behauptet.
- Aktuelle Lieferung: **P3-1 – CLI-Profil und Formatierung**, WP-30, AC-P3-01–04.
- Basis: `0e20e6b23fbcbdc3315f58d6e3c467fea8ee3d94`, Arbeitsverzeichnis bei Beginn sauber.
- [Plan, Kriterien und Anforderungen](EXECUTION_PLAN.md).
- Unabhängiger [P3-Voraudit](reviews/P3-voraudit.md) abgeschlossen: **AUDIT**,
  15 konkrete P3-Musslücken, kein G3-PASS. OpenCode 2.0.28, direkter DeepSeek-
  Provider deepseek/deepseek-flash, exakter sauberer Basischeckout, gesamter
  Prozess OS-Python-gesperrt; CLI-Exit 0, äusseres Manifest passed. SHA-256
  `ef7ac8ac673710d28c0139915af5f3fcc4043a3fe427e9228c71e46ee5c311d2`. Rohpfad build/review/P3-audit/.
- CLI-/Formatter-Umsetzung und tatsächliche RED→GREEN-Nachweise:
  [P3-1-Bericht](implementation/P3-1.md), [versioniertes Aufrufprofil](CLI_PROFILE.md).
  Parser, Konfiguration/Herkunft, Eltern-/Kindlauf, Gate-Priorität, atomare
  Dateien und sichtbare Teilartefakte sind ergänzt. Noch keine unabhängige
  P3-1-Abnahme.
- Erster tatsächlicher ili2c-Aufrufkatalog unter
  build/reports/p3/preparation/legacy-reference-initial.json; Hilfe/Version
  schreiben nach stderr. Leere headful Invocation läuft in die GUI und wurde
  nach 20 Sekunden abgebrochen; Timeout ist kein fachlicher Referenzexit.
- Plattformweg für P3-5 vom Nutzer freigegeben: neue GitHub-Action mit
  Windows-x64- und Linux-x64-Runnern implementiert und mit actionlint 1.7.7
  geprüft; tatsächlicher Lauf nach Kandidatenpush noch offen.
  macOS arm64 und Docker Linux aarch64 vorhanden; Linux x64 ist separat
  auszuführen. Keine Plattformgleichheit aus Cross-Compilation ableiten.
- Breite erste Regression: JVM/Evidenz, Portable, Conformance und SDK bestanden;
  Chromium/WebKit TeaVM sowie Web Image in Chromium/Firefox/WebKit bestanden.
  TeaVM-Firefox meldet einen echten 60-Sekunden-Timeout; Safari wurde deshalb
  nicht gestartet. Gesamtlauf Exit 1; kein Browser-PASS. Getrennte Gegenprüfung
  folgt. Neue abschliessende CLI-/Metadatenänderungen erhalten einen eigenen
  JVM-/Prozesslauf.
- Nächster Schritt: aktuelle CLI-Prüfung, Firefox-/Safari-Gegenprobe,
  Kandidatencommit/-push und unabhängiger P3-1-Review; anschliessend P3-2.
