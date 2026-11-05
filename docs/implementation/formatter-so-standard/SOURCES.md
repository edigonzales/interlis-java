# Quellenbasis und Grenzen

Recherche- und Spezifikationsstand: 5. November2026. Die folgenden Primärquellen wurden zur Einordnung verwendet. Dies dokumentiert keine ausgeführten Produktbuilds. Aus den Quellen abgeleitete Ist-Aussagen und in diesem Paket neu vorgeschlagene Zielregeln werden getrennt behandelt.

## 1. Aktiver Corestand nach Auftrag B

Repository/Commit:

https://github.com/edigonzales/interlis-java/tree/c02c9112f93cd69c113faf52d6267f601433e0fd

Direkt gelesene Dateien:

- README und aktive Build-/CLI-Einstiege: https://github.com/edigonzales/interlis-java/blob/c02c9112f93cd69c113faf52d6267f601433e0fd/README.md
- Compiler-Moduldeskriptor: https://github.com/edigonzales/interlis-java/blob/c02c9112f93cd69c113faf52d6267f601433e0fd/interlis-compiler/src/main/java/module-info.java
- Bestehender Formatter: https://github.com/edigonzales/interlis-java/blob/c02c9112f93cd69c113faf52d6267f601433e0fd/interlis-compiler/src/main/java/guru/interlis/format/DefaultModelFormatter.java
- Optionen: https://github.com/edigonzales/interlis-java/blob/c02c9112f93cd69c113faf52d6267f601433e0fd/interlis-compiler/src/main/java/guru/interlis/format/FormatOptions.java
- LSP-Formatierung und bisherige Code-Action: https://github.com/edigonzales/interlis-java/blob/c02c9112f93cd69c113faf52d6267f601433e0fd/interlis-lsp/src/main/java/guru/interlis/lsp/InterlisTextDocumentService.java
- Parsergrammatik: https://github.com/edigonzales/interlis-java/blob/c02c9112f93cd69c113faf52d6267f601433e0fd/interlis-compiler/src/main/antlr/InterlisParser.g4
- Lexergrammatik: https://github.com/edigonzales/interlis-java/blob/c02c9112f93cd69c113faf52d6267f601433e0fd/interlis-compiler/src/main/antlr/InterlisLexer.g4
- Constraintpayloads: https://github.com/edigonzales/interlis-java/blob/c02c9112f93cd69c113faf52d6267f601433e0fd/interlis-compiler/src/main/java/guru/interlis/syntax/ConstraintDeclarationSyntax.java
- Metadatenvertrag: https://github.com/edigonzales/interlis-java/blob/c02c9112f93cd69c113faf52d6267f601433e0fd/interlis-compiler/src/main/java/guru/interlis/syntax/SourceMetadata.java

Die API-/CLI-/Testdetails aus dem Gespräch wurden durch aktuelle zentrale Quellen ergänzt. Nicht jede im Paket als zu prüfen genannte Datei wurde in dieser Spezifikationsrunde vollständig gelesen. Deshalb ist die F0-Callsite-/Selektoren-/Verbraucheraufnahme verbindlich. Referenz-SHA ist keine Behauptung, dass ein späterer Checkout unverändert geblieben ist.

## 2. Solothurner Modellreferenzen

Repository/Commit:

https://github.com/sogis/sogis-interlis-repository/tree/630e3550a93288a99dd9eb4d2d6dc804f9027107

Am Pin erneut gelesene Ausschnitte:

- AGI Datahub, Kommentare/Metadaten/Deklarationen: https://github.com/sogis/sogis-interlis-repository/blob/630e3550a93288a99dd9eb4d2d6dc804f9027107/models/AGI/SO_AGI_Datahub_20240403.ili
- AWJF Waldplan, Constraints und CONSTRAINTS OF: https://github.com/sogis/sogis-interlis-repository/blob/630e3550a93288a99dd9eb4d2d6dc804f9027107/models/AWJF/SO_AWJF_Waldplan_20250312.ili
- AFU Naturgefahren, lange externe Calls und Metaattribute: https://github.com/sogis/sogis-interlis-repository/blob/630e3550a93288a99dd9eb4d2d6dc804f9027107/models/AFU/SO_AFU_Naturgefahren_20240515.ili

Weitere in CORPUS_AND_TESTS genannte Kandidaten stammen aus der vorherigen Modellanalyse bzw. dem Repositoryinventar. F0 verifiziert Existenz, Bytes, Sprache und Eignung am Pin. Keine erfundenen Dateihashes oder Testresultate. Die Auswahl ist eine Stilreferenz, keine quantitative Behauptung über alle kantonalen Modelle.

## 3. Reviewwerkzeuge

Offizielle OpenCode2-CLI-Dokumentation:

https://opencode.ai/v2/docs/cli/commands/

Sie dokumentiert `run`, Modellauswahl, JSONausgabe, Dateianhänge, Standalonebetrieb und automatische Freigabe nicht ausdrücklich verbotener Aktionen. Die tatsächlich installierte Hilfe hat Vorrang für verfügbare Flags. Keine automatische Installation/Aktualisierung im Auftrag.

Offizielle DeepSeek-Modellzuordnung:

https://api-docs.deepseek.com/quick_start/pricing/

Gelesene Zuordnung: APIalias `deepseek-flash` → `DeepSeek-V4.1-Flash-1017`. Diese zeitabhängige Zuordnung muss vor dem tatsächlichen Review erneut geprüft werden. Sie beweist für sich allein weder die konfigurierte lokale Provideradresse noch eine unveränderliche vom APIalias veröffentlichte Binaryrevision.

## 4. Eigener Zielentwurf

Die konkreten SO_STANDARD-Regeln, Layoutklassen, Phasengates, Kriterien, Breitenpolitik und gezielten Retestregeln sind Vorschläge dieses Auftrags. Sie wurden nicht als offizieller INTERLIS-Styleguide oder bestehende Jenesisfunktion recherchiert. Das Layoutsystem ist eine kleine eigene Struktur aus Gruppen, Einrückung, Atomen und Breaks; keine fremde Bibliothek wird übernommen.

Weder die vollständigen Formattergoldens noch die neue API-/Hostimplementierung wurden hier kompiliert oder ausgeführt. Alle Code-/ILIbeispiele müssen im Implementierungsauftrag an vollständigen Fixtures verifiziert werden. Die Dateipaketprüfung kontrolliert Dokumentstruktur, Verweise, Schemaschnipsel und Prüfsummen, nicht Compilerkorrektheit.
