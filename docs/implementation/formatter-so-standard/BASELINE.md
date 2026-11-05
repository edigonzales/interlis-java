# F0-Baseline

Ausgangscommit `f43a7a2baf6203a2fe82493088d91613d2329f55`, sauberer
`main`. Eigener Branch `codex/formatter-so-standard` im separaten Worktree
`../interlis-java-formatter-so-standard`. Temurin 25.0.3+9 über SDKMAN,
Jenesis 0.18.1, ANTLR 4.13.2, Node 22.13.0 und macOS arm64. Formatversion 1.0
kommt aus `VersionManifest.FORMAT_VERSION`.

Acht Originale vom angegebenen Pin, mit MIT-Lizenz und Bytehashes im
Fixturebaum. Alle acht akzeptiert. Die Positivfälle `declarations`,
`values` und `constraints` und das Deklarationsgolden sind strikt parsebar.
Die negativen Minimalproben liefern ILI-SYN-0001, ILI-SYN-0003 und ILI-SYN-0004
für Lexerfehler, unvollständige Syntax und Resttokens. Das Altformat lässt
unter anderem kompakte Block-/Mitgliedszeilen zusammen und erhält falsch
eingerückte mehrzeilige Kommentare.

`java build/InterlisJvmBuild.java build --module interlis-compiler --tests '.*Formatter.*Test'`
ergab 7 Tests, 0 Fehler, 0 Skips. Rohlogs, Baselineoutputs und Diffs liegen in
`../interlis-java-formatter-evidence/f0/`. OpenCode 2.0.28 listet
`deepseek/deepseek-flash`; eine neue technische Standalone-Probe mit dieser
Kennung ergab CONNECTED und Exit 0. Die Providerdokumentation ordnet den Alias
Flash 1017 zu. Der Gesamt-Review erhält eine separate Sitzungsbindung.

Aktive Produktionsaufrufe: `DefaultCompilerCli` (format, check, write)
und `InterlisTextDocumentService` (Standardformatierung und bisherige
Format-Code-Action). Keine Dreier-Record-Patterns, serialisierten
FormatOptions oder Aufrufe in Wasm-Bridge/TeaVM/Web Image im versionierten
Stand gefunden. Das Compiler-JAR selbst gehört zur Backendclosure.
Testklassen im Core: `FormatterContractTest` und `P3FormatterPolicyContractTest`.
Prozessabnahmen verwenden die vorhandenen modularen Launcher und
`LspProcessTest`.

| Parserzweig | Syntaxdarstellung und Layoutinformation |
| --- | --- |
| MANDATORY | optionaler Name, Ausdruck in `expression`, Kopfgrenze am Ausdruck |
| CONSTRAINT | optionaler Name, `plausibility.percentageRange`, Vergleich und Ausdruck; Prozentlexem aus Quelle |
| EXISTENCE | `existence.source` und geordnete `targets`; REQUIRED/IN/OR aus der Parserstruktur |
| UNIQUE global/LOCAL | WHERE in `expression`, Keys in `uniqueKeys`, LOCAL-Pfad in `localPrefix`, `basket`; Kopf- und Klauselseparatoren aus der Grammatik |
| SET | Bedingung in `expression`, Vorbedingung in `where`, optionaler Name/BASKET; die beiden Namenspositionen bleiben Tokenreihenfolge |
| CONSTRAINTS OF | `ExternalConstraintSyntax`, namenloses END aus der Grammatik |

Die kompakte Constraintfixture enthält alle Familien, beide
Prozentvergleiche, UNIQUE WHERE/LOCAL/BASKET und beide SET-Namenspositionen.
Die verbindlichen Umbruchregeln sind in CONSTRAINT_LAYOUT.md festgelegt.
