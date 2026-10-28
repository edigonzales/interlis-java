# A1-Review Versuch 2: ABGENOMMEN

## Urteil und Grenze

Der technische A1-Umfang für TEST-001, TEST-004, TEST-011 und TEST-012 ist
abgenommen: Quellen, Normdateien, Werkzeuge und Pins sind eingefroren, der
vollständige Ausgangslauf ist mit unveränderten Erwartungen reproduziert, und
die Evidenzhilfe schliesst die beiden Fehlgrün-Pfade aus dem ersten Review.

Diese Abnahme gilt ausschliesslich für A1 als Baseline- und Evidenzschritt. Sie
ist keine Abnahme der INTERLIS-Sprachsemantik, der vorhandenen `VERIFIED`-Zeilen,
der 18 normativen Referenzausnahmen, von P3 oder von G2 insgesamt. Eine
Normfreigabe wurde weder benötigt noch erteilt. Der explizite Modellparameter
`gpt-5.6-sol` wurde beim Start dieses unabhängigen Reviewers vom Tool akzeptiert;
daraus wird keine Selbstauskunft zur Modellidentität abgeleitet.

Massgeblich war das eingefrorene Paket
`build/reports/g2/A1/packet-v2/manifest.json` mit SHA-256
`73c1753bfb1d7d56f4c7a1114009d7254f7db3a801639faf9eff54ebc907486f`
und der After-Source-Digest
`49c76c5aabf479d5722601c7d0f4b6bf6d0107cd98702ebac4558f540556c180`.

## Geschlossene Befunde aus Versuch 1

### A1-R1: geschlossen

Die Hilfe prüft nicht mehr nur grüne Statusfelder. Das eingefrorene Profil
bindet die exakte Konformitätsauswahl, 3249 portable Fälle, Vertragswerte,
Browser-/Toolversionen und die geordnete Liste der neun SDK-Kommandos. Der rohe
Referenzbericht wird aus den 593 Detailfällen neu gezählt und mit dem
Projektwrapper gekreuzt. Der neue Neunertestsatz lehnt insbesondere alle acht
früher akzeptierten leergrünen Reportvarianten ab.

### A1-R2: geschlossen

Vor dem Lauf werden alte JVM-XMLs und relevante Artefakte entfernt. Akzeptiert
wird nur die im Profil festgelegte Menge von 144 Suites; jede XML muss nach
Laufbeginn geschrieben sein, exakt die erwartete Zahl an `testcase`-Elementen
enthalten und ohne Fehler, Fehlschlag oder Skip enden. Alle 144 XMLs liegen im
Runpaket mit Hash und Original-mtime vor. Unabhängig nachgezählt ergeben sie 688
Testfälle ohne Fehler, Fehlschlag oder Skip.

Die Profilmenge stimmt exakt mit allen 144 eingefrorenen Java-`*Test.java`-
Quellen überein. Das Log enthält die `test`-Tasks aller zehn eingebundenen
Module: acht mit Tests, TeaVM und Web Image erwartungsgemäss `NO-SOURCE`.

## Verifizierte Evidenz

- Die Hashkette für Pre-Change-, After-, Run- und Paketmanifest sowie Patch,
  neun JSON-Rohberichte, 144 JVM-XMLs, Gradle-Log, fünf Pins, 17 Artefakte und
  beide Norm-PDFs stimmt.
- Der Patch bildet 1069 Pre-Change-Dateien mit genau 14 deklarierten Änderungen
  vollständig auf alle 1075 After-Dateien ab.
- Normative Erwartungen, Konformitätsbaseline, Wrapperpin, Versionskatalog und
  `gradle.properties` sind unverändert.
- Der eingefrorene Volllauf meldet Exit 0, `BUILD SUCCESSFUL in 3m 56s` und
  `59 actionable tasks: 59 executed`.
- Roh nachgezählt: 574 PASS, ein bestehendes XFAIL, 18 FAIL und insgesamt 593
  Fälle. Der Projektwrapper weist 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS aus;
  seine 18 Bewertungen entsprechen genau den 18 rohen FAIL-IDs.
- Alle zehn portablen Runtime-/Browserausführungen melden je 3249 Fälle mit den
  erwarteten Vertragswerten. Playwright Chromium/Firefox/WebKit, echter Safari,
  TeaVM-JS, WasmGC, Web Image Node und Web-Image-Browser sind enthalten. Der
  SDK-Bericht enthält die erwarteten neun Kommandos und Exitcodes.
- Der A1-Diff berührt keine Produktions-API oder Sprachsemantik. Er ergänzt
  einen direkten Python-Helfer, ein technisches JSON-Profil, Negativtests, einen
  Gradle-Verifikationstask und nachvollziehbare Plan-/Statusverweise. Eine
  unnötige Abstraktion oder ein zusätzliches Buildsystem wurde nicht eingeführt.

## Eigene Gegenproben

Zusätzlich zum eingefrorenen Neunertestsatz wurden echte archivierte Reports
mutiert:

1. Vertauschte erste zwei SDK-Kommandos bei unveränderter Anzahl wurden mit
   `Changed SDK command/exit profile` abgelehnt.
2. Eine doppelte normative Bewertung, die eine andere Bewertung verdrängt,
   wurde mit `Missing or duplicate normative assessments` abgelehnt.
3. `diagramNodes=3` statt `2` im TeaVM-Browserreport wurde mit
   `Incomplete browser contracts` abgelehnt.

## Ausgeführte Prüfungen

1. `python3 tools/reference/g2_evidence.py verify --snapshot ../../input-v2`
   in der extrahierten Quelle: bestanden; erwarteter Source-Digest.
2. `python3 -m unittest discover -s tools/reference -p test_g2_evidence.py -v`:
   neun Tests bestanden.
3. `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem ./gradlew
   g2EvidenceTest --rerun-tasks --console=plain` in der extrahierten Quelle:
   neun Tests, `BUILD SUCCESSFUL in 2s`; keine Root-Buildausgaben erzeugt.
4. Patchanwendung auf eine isolierte Pre-Change-Kopie und vollständiger
   Dateihash-/Modusvergleich gegen das After-Manifest: bestanden.
5. `python3 audit.py`: alle Manifest-, Archiv-, Patch-, Report-, XML-, Pin- und
   Artefaktprüfungen, Suite-/Task-Recounts sowie die drei eigenen Gegenproben
   bestanden. Das maschinenlesbare Ergebnis liegt in `audit.json`.

Der komplette vierminütige Plattformlauf wurde nicht nochmals ausgeführt; er
liegt bereits als vollständig gehashter, mit Exit 0 abgeschlossener Run-v2 samt
Rohberichten vor. Unabhängig neu ausgeführt wurden die gezielten Evidenztests und
Gegenproben. Für A1 bleibt kein technischer Blocker. Nächster Schritt ist A2;
A3 und sämtliche fachlichen beziehungsweise normativen Abnahmen bleiben offen.
