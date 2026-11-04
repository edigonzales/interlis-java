# A0: Jenesis-Fähigkeiten

Unveränderte Quellen von https://github.com/jenesis/jenesis.git,
Releasekandidat0.18.1, Commit `177014053cce311924f6724d4e4a7fe5689ed9b6`.
Git-Tree `sources/build/jenesis`: `0a6f95c63bcc1513fd00b22a072b55a636c62e12`.
Ausführung mit `/Users/stefan/.sdkman/candidates/java/25.0.3-tem/bin/java`.
Rohbelegroot: `/Users/stefan/sources/interlis-java-jenesis-evidence/a0`.
Die Releasequellen und deren `skill/migrate`/`skill/registry` wurden gelesen;
ihre Ausgabe liegt unverändert in `skill-*.txt`. Scopeentscheidungen dieses
Auftrags haben Vorrang vor deren vorgeschlagenem Gradleabbau/JPMS-Folgephase.

## Praktisch bestätigte Schnittstellen

- POM-Aggregator mit zwei Kindern, lokaler Projektdependency und importierter
  JUnit-BOM5.11.4; gewöhnliche Klassen und Tests auf Classpath.
- `jenesis.test.tag=-compiler-contract`, `.reporting=true`, `.jars=false`.
  `probe-first.log`: package-private White-Box-Test und zwei Parameterfälle
  erfolgreich; gemischte getaggte Fehler-Methode bleibt ausgeschlossen.
- Modulfilter `lib/probe.ValueTest#normal`: `method.log`, Exit0.
  Fehlende Klasse: `missing.log/json`, Exit1. Getaggter absichtlicher Fehler:
  `failure.log/json`, Exit1. Keine geänderten Produkttests.
- `process-java.properties` setzt die tatsächlich im Test behauptete Property.
  Testspezifische Datei unter `lib/src/test/build.jenesis` hat Vorrang.
  Das spätere Projekt verwendet vollständige Prozessdateien je Spezialisierung.
- Native Legacy-XML ist enginegruppiert, zusätzlich Open-Test-Reporting-XML.
  Engineinventar `inventory/output/inventory.properties` nennt Report-, Artefakt-,
  Prozess-, Graph- und Runtimepfade ausdrücklich. Kein Latest-JAR-Suchen nötig.
- ANTLR offizielles Plugin0.2.0 mit eigenem Engineartefakt0.18.0 als Plugin-API,
  Tool4.13.2 und vollständig aus dem Release-Demo übernommenen Toolpins.
  `antlr.log/json`: getrennte Lexer-/Parsergrammatik, gemeinsames tokenVocab,
  Package/Visitor/no-listener über `@grammars/antlr=src/main/antlr` erfolgreich.
  Toolgruppe `antlr:tool` bleibt von der Produktruntime getrennt.
- `fixture-first/repeat/changed.events.jsonl`: tatsächlicher Testschritt
  executed/skipped/executed. Fixtureinhalt wird als SHA256 in der wirksamen
  testspezifischen Prozesskonfiguration eingebunden. Nur Rootproperty genügt nicht.
- `jenesis.test.skip=true` erlaubt reine Erzeugung; normale Tests werden über
  `.test.force=true` frisch gewählt. `Project.build` gibt Engineergebnisse zurück;
  `Make.settings`/`Project.ofEnvironment` sind öffentliche Integrationspunkte.

## Erforderliche Projektprüfungen

`empty-tag.log`: Engine Exit0 bei null Tests; `empty-tag-raw/` bewahrt den
Originalreport. Dieser Erfolg ist **keine** erfüllte Auswahl. A1/A3 müssen
leere erwartete Auswahlen und verlorene Invocations explizit zurückweisen.
Enginefehler selbst werden nicht umgedeutet. Ebenso ist `project.reproduce`
kein hartes Artefaktdifferenzgate; das Vergleichsgate bleibt Projektaufgabe.
A0 behauptet keine bereits fertige Produktparität oder Reportprojektion.

## Reviewerpreflight (kein Review)

OpenCode v2.0.28, installierte `run --help` und `models --standalone` geprüft;
Modell `deepseek/deepseek-flash` vorhanden. Reiner technischer Aufruf ohne Tools
antwortet PREFLIGHT_OK (`opencode-preflight.jsonl`), keine Produktprüfung.
Offizielle Aliaszuordnung: https://api-docs.deepseek.com/api_samples/chat_python_v3.1_terminus/
nennt DeepSeek-V4.1-Flash-1017 (04.11.2026 abgerufen). Endpoint und finale
Runmetadaten werden vor R erneut geprüft, keine Credentials protokolliert.
