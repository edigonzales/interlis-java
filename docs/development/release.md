# Compiler-Freigabeprüfung

`java build/InterlisJvmBuild.java release-check --profile compiler` prüft
einen sauberen, exakt gebundenen Kandidaten. Die Prüfung schreibt einen
internen Freigabenachweis. Eine Veröffentlichung braucht einen eigenen
Auftrag. Fehlende, veraltete, fremde, geänderte und übersprungene
Pflichtbelege halten das Gate geschlossen.

## Plattformen und Ablauf

Der Workflow [Compiler platform contracts](../../.github/workflows/compiler-platforms.yml)
prüft Linux x64 und Windows x64 mit Java 25.0.3. Die beiden Checknamen
lauten `JVM and distribution (linux-x64)` und
`JVM and distribution (windows-x64)`. Der Workflow liefert
`compiler-<plattform>-<commit>` mit Plattformbericht, Commitdatei,
Rohberichten und Prozessbelegen. Der
[Collector](../../tools/release/platform-evidence.cjs) liest erfolgreiche
Runs des exakten Kandidaten und verifiziert Identität, SHA-256, Roh-XML
und Quellfingerprint. Der lokale Host ergänzt macOS arm64. Kandidat,
Versionen, Toolchain und die tatsächliche Auslieferung sind gebunden.

Setze die [Backend-Toolchains](build.md) und installiere die gepinnten
Playwright-Browser. Für den vollständigen Nachweis braucht der macOS-Host
echtes Safari WebDriver sowie einen aktiven Interpreterguard. Der Guard
führt den Befehl in einer OS-Sandbox aus, protokolliert den tatsächlichen
Start und blockierte Interpreter und hält seine Quittung während des
Laufs bereit.

```sh
"$INTERLIS_NODE" tools/release/platform-evidence.cjs
"$INTERLIS_NODE" tools/verification/without-python.cjs \
  java build/InterlisJvmBuild.java release-check --profile compiler
```

Verwende einen verfügbaren GitHub-CLI-Zugang für den Collector. Das
Releaseprofil kopiert die aktuellen externen Belege in seinen expliziten
Run und prüft das Preflight vor den lokalen Aggregaten.

## Pflichtnachweise

- JVM- und Vertragstests mit originalen Reports und ohne Skips, echte
  installierte CLI-/SDK-Prozesse, korrekte Modulgrenzen und Farb-/Streamtests.
- Vollständige externe Conformance samt begründeten, gebundenen
  Erwartungsabweichungen. Der Checker erwartet 593 Fälle mit 579 PASS,
  14 XFAIL und 19 normativen Bewertungen.
- TeaVM-JavaScript, TeaVM-WasmGC, GraalVM Web Image und die tatsächlichen
  Browserläufe in Chromium, Firefox und Playwright-WebKit. Echtes Safari
  bleibt ein eigener Pflichtnachweis. Der Checker bindet Transport-,
  UI-, Neustart-, Präzisions- und Regressionsresultate an Artefaktbytes.
- Zwei unabhängige saubere Builds mit vollständigem Lieferinventar,
  Dateirechten und browserseitigen Ressourcen. Exakte Bytevergleiche
  gelten für reproduzierbare Dateien. Abweichende generierte TeaVM-Programme
  brauchen ihre eigenen gebundenen vollständigen Funktionsbelege.
- Das Performanceprofil umfasst 347 gepinnte Wurzeln, sechs Laufzeitpfade,
  vollständige Parität und 2’000 reale Editoränderungen. Es prüft
  Speicher-, Laufzeit-, Aufbewahrungs- und Artefaktbudgets aus
  [budgets.json](../../testdata/corpus/full/budgets.json), mit dokumentierter
  Referenzmaschine, Warmup und Messgrenzen. Es akzeptiert keine Skips oder
  semantischen Abweichungen.

Die Rollen unter `platform/`, `release/` und `reproducibility/` liegen
im jeweiligen Hostlayout. Synthetische Checkerfixtures belegen die
Prüfmechanik. Ihr Ergebnis ist keine Freigabe für einen Produktkandidaten.
