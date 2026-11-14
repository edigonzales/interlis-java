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
  und nicht erfüllte Originalassertionen lassen den Befehl fehlschlagen.
  Die Suite ist noch nicht Teil der CI. Die verbleibenden Abweichungen
  sind unten beschrieben.
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
Der Runner verwendet die unveränderten Originalmodelle und XTF-Dateien.
Beim Upstream-Stand `f307111` laufen alle 223 Originaltests ohne
Namensraumblockade. Der aktuelle Stand besteht 205 Tests.

Die Regressionen für behälterübergreifende XTF-2.3-Referenzen
(`RBe.T01b/d`), fehlende Rollen eigenständiger Links
(`RRiO.T03d`, `RBk.T03c`), leeren Beziehungszugang in `DEFINED`
(`RKo.T11b`) und Randkontakt ohne gemeinsamen Stützpunkt
(`RSU.T03a/b`) prüfen die Originaldateien und die konkreten Diagnosecodes.

Folgende Fälle bleiben offen:

- `RKo.T09a`: UNIQUE über die Rollenpfade einer eingebetteten Beziehung.
- `RSU.T05a/b`: ein Randzug mit Berührpunkt, der Aussenrand und
  Enklave als zusammenhängende Folge kodiert (INTERLIS 2.3 §2.8.13.1).
- `RSU.T16a`: erwarteter Geometriebefund für den gelieferten Kurvenrand.
- `RSU.T18a–e`, `RSU.T19a–e`: ungeprüfte Hostfunktionen
  `INTERLIS_ext.areAreas2` und `areAreas3`.
- `RPO.T02c`: Selbstberührung an einem wiederholten Stützpunkt.
- `RAt.T09e`: die Suite erwartet Abrundung vor der Bereichsentscheidung.
  Der Validator vergleicht den gelieferten Wert exakt (§3.3.11.4).

Bei `RAt.T11a/b` erwartet die Originalsuite, dass transferlokale
`BID` und `TID` ohne `OIDSPACES` als stabile UUIDs geprüft werden.
Für XTF 2.3 FULL unterscheidet der Validator zwischen Transfer-ID
und stabiler OID. Die Suite bleibt bis zur Klärung der verbleibenden
Abweichungen ausserhalb des verbindlichen Linux-CI-Jobs.
