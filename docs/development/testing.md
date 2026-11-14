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
  Die Suite ist noch nicht Teil der CI. Der Modellblocker und die
  verbleibenden Abweichungen sind unten beschrieben.
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

INTERLIS 2.3 §2.5.4 (S. 30–31) ordnet Themen und Klassen derselben
Namenskategorie zu. Das Thema übernimmt die Typnamen seines
übergeordneten Modells. Die Klasse `GebaeudeRegister` im gleichnamigen
Thema kollidiert deshalb mit dem übernommenen Themennamen. Sie ist keine
explizite Erweiterung einer geerbten Klasse. Die strikte Ablehnung
`SCOPE_OUTER_TYPE_NAME` entspricht [Entscheidung 09](../contracts/norm-decisions.md#decision-09).
[INTERLIS 2.3 Referenzhandbuch](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=31)

Beim vollständigen Originallauf mit dem untersuchten Upstream-Stand
`f307111` bestanden 17 von 223 Originaltests. 194 Fälle scheiterten am
Modellgate, zehn an ungeprüften Aufrufen von `INTERLIS_ext.areAreas2`
oder `areAreas3`. Zwei weitere Originaltests (`RAt.T11a/b`) erwarten,
dass ein transferlokales `BID` oder `TID` ohne `OIDSPACES` als stabile
UUID geprüft wird. Für XTF 2.3 FULL verwendet der Validator die
[Unterscheidung zwischen Transfer-ID und stabiler OID](../usage/transfer.md#lokale-datenvalidierung).

Eine getrennte Diagnosekopie mit umbenannter Klasse, angepasster
Modellreferenz und entsprechenden XTF-Klassentags brachte 198 der 223
Tests zum Bestehen. Die 25 verbleibenden Fehlschläge umfassen die zwölf
bereits im Originallauf erreichbaren Abweichungen sowie Fälle zu
Ordnungswerten, Constraints, Geometrie, Beziehungen und Attributen. Die
Diagnosekopie ist kein Konformitätslauf der Originalsuite.

Sobald der Modellblocker für einen unveränderten Upstream-Checkout
beseitigt und die verbleibenden Ergebnisse fachlich geklärt sind, gehört
der vollständige Lauf in den bestehenden Linux-CI-Job neben die
Codeberg-Suite. Die Modellblockade zählt bis dahin als Fehler des
Originallaufs.
