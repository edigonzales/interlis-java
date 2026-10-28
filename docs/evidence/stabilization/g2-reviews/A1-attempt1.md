# A1-Review: NACHARBEIT

## Umfang und Quellenbasis

Geprüft wurde ausschliesslich Schritt A1 für TEST-001, TEST-004, TEST-011 und
TEST-012: der belegte Ausgangslauf, die eingefrorenen Quellen/Normen/Pins, die
Paketprovenienz und die Evidenzhilfe. Dieses Urteil ist keine Abnahme der
vollständigen INTERLIS-Sprachsemantik, keine P3-Abnahme und keine Normfreigabe.

Massgebliche Quellenbasis war
`build/reports/g2/A1/input/source.tar.gz` mit Source-Digest
`d851fd7f6212579490945ee311fdb0d9f1cce260b1151386db056a65555976d3`,
nicht der später veränderbare Repository-Arbeitsstand. Der vorgegebene Hash des
Paketmanifests
`7903f43d2116e814e8eb18c1ae7d0aaa0d68bb6011e76802b7f41c91f8e62987`
stimmt. Der explizite Modellparameter `gpt-5.6-sol` wurde beim Start dieses
Reviewers vom Tool akzeptiert; daraus wird keine Selbstauskunft zur
Modellidentität konstruiert.

## Blockierende Befunde

### A1-R1: Unvollständige Erfolgsreports werden akzeptiert

`tools/reference/g2_evidence.py:136-150` validiert bei Projekt-, Portable-,
Safari- und SDK-Reports fast nur Statusfelder. Der rohe Referenzreport wird ohne
Inhaltsprüfung akzeptiert; eine Konsistenzprüfung zwischen Rohbericht und
Projektwrapper fehlt.

Eine eigene Gegenprobe übergab dem eingefrorenen Helfer:

- einen Projektbericht mit `TOTAL=0` und null FAIL/ERROR/XPASS,
- JavaScript-, WasmGC- und Web-Image-Reports ohne `stabilizationCases`,
- Safari- und Browserreports ohne die 3249 Fälle und ohne Vertragswerte,
- einen SDK-Bericht mit `status=passed`, aber leerer `commands`-Liste.

Alle acht unvollständigen Varianten wurden akzeptiert. Damit kann die Hilfe
einen grünen Evidenzlauf ausgeben, obwohl Fälle oder die zu prüfenden
Vertragswerte fehlen.

Erforderliche Nacharbeit: exakte Schemas und Profilinvarianten prüfen,
insbesondere Auswahl und Fallzahlen, Detailfall-Recount und
Roh-/Wrapperkonsistenz, 3249 Portable-Fälle samt benötigten Vertragsfeldern je
Runtime/Browser, Browser-/Toolversionen sowie die vollständige erwartete
SDK-Kommandoliste mit Exitprofil. Nullfälle, fehlende Felder und widersprüchliche
Reports brauchen eigene Negativtests.

### A1-R2: Alte JVM-Resultate werden akzeptiert; Rohbeleg für 688 fehlt

`tools/reference/g2_evidence.py:122-133` aggregiert jedes vorhandene
`interlis-*/build/test-results/test/TEST-*.xml` ohne Laufbezug oder erwartete
Task-/Suite-Menge. Vor dem Lauf werden gemäss Zeilen 162-164 nur die neun
JSON-Reports gelöscht, nicht die JVM-XMLs oder die später gehashten Artefakte.

Eine eigene Gegenprobe legte genau ein auf den 1. Januar 2000 datiertes
`TEST-stale.xml` mit `tests=688` an. `jvm_summary` akzeptierte es als vollständig
bestandene JVM-Abnahme. Im A1-Paket wurden die XML-Rohreports ausserdem nicht
gesichert; enthalten ist nur die aggregierte Zahl im Run-Manifest. Der
Gradle-Log belegt ausgeführte Testtasks und einen erfolgreichen Prozess, aber
nicht die Zahl 688.

Erforderliche Nacharbeit: alle akzeptierten JVM-Reportpfade und zu hashenden
Laufartefakte vor dem Lauf entfernen oder in einer sauberen isolierten Quelle
bauen; die erwartete Testtask-/Suite-Menge prüfen; danach die frischen XMLs mit
Hashes ins Runpaket kopieren. Alte Zusatzmodule/-XMLs müssen zur Ablehnung
führen. Der vollständige A1-Lauf muss mit der korrigierten Hilfe neu erzeugt
werden.

## Positiv verifizierte Evidenz

- Paket-, Manifest-, Report- und Log-Hashketten sowie beide Norm-PDF-Hashes
  stimmen.
- Das Archiv enthält 1072 vollständig gegen das Manifest geprüfte Dateien.
  Das Pre-Change-Archiv enthält 1069 Dateien. Der Patch reproduziert mit genau
  den 11 deklarierten Änderungen sämtliche After-Dateihashes.
- `normative-expectations.json`, Konformitätsbaseline, Wrapperpin,
  Versionskatalog und `gradle.properties` sind unverändert.
- Der vorhandene Log endet mit Exit 0, `BUILD SUCCESSFUL in 3m 52s` und
  `59 actionable tasks: 59 executed`.
- Unabhängig nachgezählt: der Rohbericht enthält 574 PASS, 18 FAIL, ein
  bestehendes XFAIL und insgesamt 593 Detailfälle. Die 18 normativen
  Assessments entsprechen genau den 18 rohen FAIL-IDs; der Projektwrapper weist
  574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS aus.
- Alle archivierten Portable-Reports enthalten 3249 Fälle je Runtime/Browser;
  die drei Playwright-Browser, Safari und beide Web-Image-Wege sind im konkreten
  Paket vollständig. Der SDK-Report enthält neun Kommandos mit dem erwarteten
  Exitprofil.
- Die A1-Änderung bleibt einfach und im beschriebenen Umfang; unter den 11
  geänderten Dateien gibt es keine Sprachsemantik- oder Produktions-API-Änderung.

Diese konkreten positiven Beobachtungen heilen die generischen Fehlgrün-Pfade
der Evidenzhilfe und den fehlenden JVM-Rohbeleg nicht.

## Eigene Prüfungen und Gegenproben

Ausgeführt wurden:

1. `sha256sum` für Paket-/Input-/Pre-Change-/Run-Manifeste, alle archivierten
   Reports, `gradle.log` und beide Norm-PDFs: bestanden.
2. `python3 .../g2_evidence.py verify --snapshot build/reports/g2/A1/input`:
   bestanden, erwarteter Source-Digest.
3. `./gradlew g2EvidenceTest --rerun-tasks --console=plain` in der extrahierten
   Kopie unter `build/reports/g2/A1/review/frozen`: fünf Tests bestanden,
   `BUILD SUCCESSFUL`; keine geteilten Repository-Buildausgaben verändert.
4. Direkter `unittest`-Lauf derselben eingefrorenen Tests: fünf Tests bestanden.
5. Eigene Achtfach-Gegenprobe für minimale/leere grüne JSON-Reports: alle
   fälschlich akzeptiert.
6. Eigene Altresultat-Gegenprobe mit JVM-XML von 2000: fälschlich als 688 Tests
   akzeptiert.
7. Unabhängiger Recount und Kreuzvergleich der archivierten JSON-Reports:
   bestanden; Einschränkung: keine JVM-Rohreports im Paket.
8. Patchanwendung auf eine extrahierte Pre-Change-Kopie und vollständiger
   Hashvergleich gegen das After-Manifest: bestanden.

## Normstatus und Abschlussbedingung

Es wurde keine Normentscheidung getroffen. A1 verändert die gepinnten
normativen Erwartungen nicht; eine Nutzer-Normfreigabe ist für die beiden
technischen Nacharbeiten weder erforderlich noch erteilt.

A1 kann erneut vorgelegt werden, wenn A1-R1 und A1-R2 behoben, mit neuen
Negativtests belegt und der komplette Ausgangslauf aus demselben eingefrorenen
Quellenstand mit frischen, archivierten Rohreports wiederholt wurde.
