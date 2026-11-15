# Tests

Wähle Prüfungen anhand der betroffenen [Anforderungen](../contracts/requirements.md),
Verbraucher und Risiken. Ein JVM-Test belegt weder einen Browserlauf noch einen
CLI-Prozess. Ein erfolgreicher Parserlauf belegt keine vollständige semantische
Abdeckung. Wiederverwendbare Belege brauchen identische relevante Eingaben,
ausgeführte Prozesse, Rohberichte und bekannte Skips.

## Direkte Prüfungen

Führe JVM-Tests mit einem normalen Benutzerkonto aus. Die Dateirechtetests
prüfen tatsächliche Schreibverbote des Betriebssystems.

- `java build/jenesis/Make.java` führt die regulären JVM-Tests aus.
- `java build/jenesis/Make.java +interlis-model` prüft ein Modul.
- `java '-Djenesis.test.filter=.*VersionScannerTest' build/jenesis/Make.java +interlis-compiler`
  wählt eine JUnit-Klasse. `#method` schränkt die Auswahl auf eine Methode ein.
- `java build/interlis/ExistingJvmDistribution.java check` prüft installierte CLI, SDK und LSP.
  Der LSP-Prozesscheck benötigt Node.
- `java build/interlis/ConformanceRun.java` führt die gepinnte Codeberg-Suite aus.
  `--filter '^ili23\.Class23Test\.classComplete$'` prüft einen gezielten Fall.
  Die [Erwartungen](../../testdata/conformance/normative/expectations.json)
  und [Baseline](../../testdata/conformance/baseline.json) halten die begründeten Abweichungen fest.
  Native Reports liegen unter `target/jenesis-jvm/conformance/`.
- Der lokale Validator-Konformitätslauf verwendet einen separaten Checkout von
  [suite-interlis](https://github.com/geostandards-ch/suite-interlis).
  `java build/interlis/ValidatorConformanceRun.java --suite-dir ../suite-interlis`
  führt die Originaltests aus. `--filter '.*\.XmlTest\.RXW_T02a'` wählt
  eine Methode. Der reguläre Ausdruck muss auf den vollständigen Java-Namen
  `Paket.Klasse.Methode` passen. Berichte liegen unter
  `target/jenesis-jvm/validator-conformance/`. Die Originaltests verwenden
  den Herstellerwert `interlis-java`. Technische Fehler, eine leere Auswahl
  und unerwartete Originalfehler lassen den Befehl fehlschlagen. Der Linux-Job
  führt dieselbe Suite nach Codeberg aus.
- Die [Buildanleitung](build.md#portable-backends) zeigt die Node-Smokes für die
  erzeugten JS-/WasmGC-Artefakte. Der Portable-CI-Job führt `browserSmoke`
  und `validationBrowserSmoke` mit Chromium aus. Die Validatorprobe
  kompiliert ihr Modell und prüft neutrale Transferwerte im WasmGC-Artefakt.

## Testinhalte

Quelltexttests decken exakte Revisionen, UTF-16, astrale Zeichen, CR/LF,
Kommentare, Recovery und vollständigen Dateiverbrauch ab. Compilerprüfungen
unterscheiden gültige Closure, partielle Analyse, fehlende Eingaben,
abgelehnte Warnungen, Abbruch und interne Fehler. Semantische Goldens halten
Typen, Referenzen, effektive Reihenfolge, Übersetzungen, Rollen und Projektionen
fest. Nur Akzeptanz oder Exitcode genügen dafür nicht.

Workspace- und Editorprüfungen vergleichen frische und wiederverwendete
Analyse, späte Abhängigkeiten, Unicode-Deltas, Abbruch, stale Resultate,
Cachegrenzen und dokumentübergreifende Edits. Projektionsprüfungen kontrollieren
Identitäten, Filter, Budgetgrenzen und getrennte Layout-/Navigationseffekte.
Diagnosetests prüfen stabile Codes, strukturierte Argumente, Quellbereiche und
unterschiedliche Renderer. Laufbeobachtung muss deterministisch und isoliert
bleiben, auch bei einer fehlerhaften Senke.

Repositoryprüfungen verwenden lokale HTTP-Gegenstellen und feste Uhren.
Wichtige Gegenproben betreffen selektierte Version, negativen Treffer,
MD5-/SHA-Integrität, Fristgrenze, Offlinecache, Proxy, Redirect und Fehler.
CLI- und LSP-Verträge prüfen tatsächliche Streams, Status, Prozessabschluss,
Ausgabeatomizität, Revisionen und veröffentlichte Diagnosen.

Differentialtests unterscheiden normativ bestätigte Abweichungen,
Implementierungsunterschiede und Defekte der Referenzwerkzeuge. Die
[Sprachentscheidungen und ihre Normquellen](../contracts/norm-decisions.md)
geben den Massstab.
Robustheitsläufe brauchen begrenzte Eingaben, Zeit- und Ressourcenbudgets,
reproduzierbare Seeds sowie explizite Grenzen für Exits und Skips.

## Prüfergebnisse

JUnit-XML und Prozesslogs liegen in den Buildausgaben. Halte für eine Prüfung
Toolchain, Plattform, relevante Eingaben, Commit, Befehle, Exits und Skips fest.
Die [Freigabeprüfung](release.md) beschreibt die Auslieferungsprüfungen.

## swisstopo-Validator-Konformität

Die Klasse `GebaeudeRegister` darf den Namen ihres Topics tragen.
Der Compiler akzeptiert diese Modellierung in 2.3 und 2.4 gemäss
[Entscheidung 09](../contracts/norm-decisions.md#decision-09).
Der Runner verwendet die unveränderten Originaltests, Modelle und XTF-Dateien.
Die Suite prüft INTERLIS 2.3 und XTF 2.3.
Der Linux-Job klont den Upstream-Zweig `master` einmal und startet den
vollständigen Lauf nach Codeberg. Lokal lässt sich derselbe Runner mit
einem vorhandenen Suite-Checkout aufrufen.

```bash
java build/interlis/ValidatorConformanceRun.java --suite-dir ../suite-interlis
java build/interlis/ValidatorConformanceRun.java --suite-dir ../suite-interlis \
  --filter '.*ConstraintTest\.RKo_T09a'
```

Die native JUnit-XML-Datei, die Adapter-Reports und `summary.txt` liegen
unter `target/jenesis-jvm/validator-conformance/run-*/`. Die
[Abweichungsliste](../../build/interlis/validator-conformance/deviations.tsv)
dokumentiert den erwarteten Befund jedes betroffenen Originaltests.
Ein neuer Fehler, eine technische Störung, ein abweichender Befund oder
ein inzwischen bestandener Baselinefall beendet den Runner mit Fehler.

Am Upstream-Stand `f307111` bestehen 212 von 223 Originaltests.
`RKo.T09a` prüft UNIQUE über Rollenpfade eingebetteter Beziehungen.
`RSU.T05a/b` prüfen Aussenrand und berührende Enklave in einem Randzug
(INTERLIS 2.3 §2.8.13.1, Figur 22).
`RPO.T02a/b` prüfen unzulässige Überlagerungen und Schnitte im
Segmentinneren. Bei `RPO.T02c` ist die Kreuzung zulässig, weil beide
Durchgänge dort einen Segmentendpunkt haben. Die
[Normauslegung](../contracts/norm-decisions.md#gemeinsame-segmentendpunkte-bei-linienzügen)
dokumentiert diese Kompatibilitätsregel.

Bei zehn Fällen fehlt die Laufzeitbindung der Legacy-Funktionen
`INTERLIS_ext.areAreas2/3` mit TEXT-Pfaden. Bei `RSU.T16a` akzeptieren
der native Validator und ilivalidator 1.15.1 den gelieferten Kurvenrand,
für den das Original einen Fehler erwartet.
Die [Abweichungen im Detail](swisstopo-suite-deviations.md)
beschreiben die Originalerwartungen und die beobachteten Befunde.

`RAt.T09e/g` prüfen die numerische Bereichsprüfung nach Rundung auf
die Modellpräzision. `RAt.T11a/b` prüfen, dass der Validator ungültige BID und TID bei einem
wirksamen `UUIDOID`-Wertebereich auch in XTF 2.3 FULL ohne `OIDSPACES` meldet.
