# Prüfung und Evidenz

Wähle Prüfungen anhand der betroffenen [Anforderungen](../contracts/requirements.md),
Verbraucher und Risiken. Ein JVM-Test belegt weder einen Browserlauf noch einen
CLI-Prozess. Ein erfolgreicher Parserlauf belegt keine vollständige semantische
Abdeckung. Wiederverwendbare Belege brauchen identische relevante Eingaben,
ausgeführte Prozesse, Rohberichte und bekannte Skips.

## Schnelle Prüfungen

- `java build/InterlisJvmBuild.java build --module interlis-compiler --tests '.*VersionScannerTest'`
  wählt gezielt einen JUnit-Vertrag.
- `java build/InterlisJvmBuild.java contracts` prüft die aktuellen
  Bindungen aus dem [Compilerkatalog](../../testdata/verification/compiler-contracts.json).
  Der Katalog kennt Suite, Methode und erforderliche Invocation.
  Producerreports und originale JUnit-XMLs müssen zum tatsächlichen Lauf
  gehören. Ausgelassene, umbenannte oder doppelte erforderliche Invocations
  machen die Abnahme ungültig.
- `host --profile reference` verwendet das gepinnte
  [ili2c-Profil](../../testdata/verification/reference-profile.json).
  `reference-originals`, `reference-views` und `reference-units` prüfen
  konkrete Quellbytes und Erwartungen. Abweichungen werden fachlich begründet.
  Differenzen zwischen Compiler und Referenzprozess werden separat erfasst.
- `host --profile conformance` verwendet die gepinnte externe Testsuite.
  Die [Erwartungen](../../testdata/conformance/normative/expectations.json)
  und [Baseline](../../testdata/conformance/baseline.json) binden Originale,
  Diagnosen und ausdrücklich begründete XFAILs. XPASS, neue Fehler und
  fehlende Pflichtfälle brauchen eine Bewertung.
- `host --profile portable` prüft JVM, TeaVM JS/WasmGC und Web Image.
  `host --profile browser` führt den Transport, die UI- und Browsernachweise
  einschliesslich Safari aus. Der gemeinsame portable Runner führt standardmässig
  alle registrierten Gruppen aus. Gezielte Proben wählen Suite- und Fall-IDs.

Die Befehle mit `host` werden mit `java build/InterlisJvmBuild.java`
aufgerufen. Ein echtes installiertes Beispiel nutzt `sdk-check`. Ein
kleiner Node-Editorlauf verwendet `EDITOR_SMOKE=1` und
`host --profile full-corpus-editor-wasm`. Er erzeugt ausschliesslich einen
Smoke-Bericht und braucht keine Browser- oder Safari-Läufe.

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
[Sprachentscheidungen](../contracts/norm-decisions.md) und die
[Normpins](../../testdata/reference/norms/documents.json) geben den Massstab.
Robustheitsläufe brauchen begrenzte Eingaben, Zeit- und Ressourcenbudgets,
reproduzierbare Seeds sowie explizite Grenzen für Exits und Skips.

## Freigabeevidenz

Versionierte Eingaben und Budgets stehen bei den Prüfartefakten in
`testdata`. Rohberichte gehören in den jeweiligen Output- oder externen
Belegbereich. Halte Toolchain, Plattform, Eingaben, Produkt-SHA, Kommandos,
Exits und Vollständigkeit fest. Der
[Freigabevertrag](release.md) beschreibt die kumulative Abnahme.
