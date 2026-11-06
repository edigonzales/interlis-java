# Architektur

Der Compiler bildet aus versionierten Quellen einen getypten semantischen
Graphen. Workspace und Hosts verwalten Quellen, Revisionen, Beschaffung und
Veröffentlichung. Projektionen verwenden die öffentlichen semantischen
Sichten. Der Compiler benötigt dafür weder Dateisystem noch Netzwerk, JDBC,
Editortransport oder grafische Laufzeit.

## Module

Die folgende Tabelle nennt direkte Projektabhängigkeiten aus den
JPMS-Deskriptoren. Java-Pakete und Module verwenden die Basis `guru.interlis`.
Die zugehörigen POMs beschreiben die konkreten Abhängigkeiten und Scopes.

| JVM-Modul | Direkte Projektabhängigkeiten | Zuständigkeit |
|---|---|---|
| `interlis-model` | – | Quellenwerte, Diagnostik, Laufkontext, semantischer Graph und Schemasicht |
| `interlis-compiler` | model | Parseradapter, eigene Syntax, Bindung, Analyse und Gültigkeitsgate |
| `interlis-workspace` | compiler, model | Versionierte Änderungen, Resolver-SPI, Invalidierung und Sprachabfragen |
| `interlis-repository` | workspace, model | Datei-/HTTP-Host, Indexe, Beschaffung, Cache und Lock |
| `interlis-projection` | compiler, model | Diagramm-DTOs und reine Projektion |
| `interlis-documentation` | model, projection | Lesende Dokumentationsprojektion und Rendering |
| `interlis-transfer` | model | Formatunabhängige Transferpläne, Objektwerte und Streaming-Protokoll |
| `interlis-xtf23` | transfer, model | XML-Adapter für INTERLIS 2.3 |
| `interlis-compiler-cli` | compiler, model, repository, workspace, projection | CLI, Prozessgrenzen, Präsentation und Ausgabe |
| `interlis-compiler-gui` | model, compiler, workspace, repository | Desktop-Modellprüfung, Diagnosen, Abhängigkeiten und Repositoryexplorer |
| `interlis-lsp` | workspace, compiler, projection, model, repository, documentation | LSP-/Editorhost, Scheduling und Ausgaben |
| `interlis-wasm-bridge` | workspace, compiler, projection, model | Gemeinsames JSON-Protokoll und Sitzungen |
| `interlis-verification` | compiler, model, workspace, repository, wasm-bridge | Prüfwerkzeuge ausserhalb der SDK-Auslieferung |

Alle dreizehn JVM-Module werden über denselben Builder erzeugt. Die
jeweils ausgewählten JPMS-Closures stehen im
[Buildvertrag](development/build.md). TeaVM und Web Image konsumieren
denselben expliziten JVM-Snapshot. Browser und Editor teilen den neutralen
Renderer unter `frontend/diagram-ui`.

Artefaktkoordinate, Dateiname und Modulname sind getrennte Identitäten.
Die JVM-Deskriptoren bestimmen die tatsächlichen Packageexports.
`guru.interlis.model.internal` ist ausschliesslich für das Compilermodul
exportiert. Die Gson-Reflection ist für LSP- und interne
Verifikationsdatentypen qualifiziert geöffnet. ANTLR und interne
Compilerklassen bleiben in der Implementierung.

## Quellen, Identitäten und Zustände

`SourceText` enthält opake `SourceId`, Revision und exakten Text.
`SyntaxSnapshot` bewahrt Tokens, Knoten, Trivia und Syntaxdiagnostik.
`AnalysisSnapshot` bindet den Eingabestand an Syntaxsichten, Abdeckung,
Diagnosen, Graph und Abfrageindizes. Ein partieller Stand kann Navigation
ermöglichen, obwohl eine Bindung oder ein Typ noch ungeklärt ist.
`CompilationGate` veröffentlicht ein unveränderliches `CompiledSchema` für
eine vollständig geprüfte, gültige Closure.

Quellrevisionen steigen pro Quelle, Workspace-Revisionen pro Workspace.
`SnapshotId` schützt vor gemischten Abfragen. Symbolidentitäten sind opak und
bleiben innerhalb einer Sitzung bei eindeutiger Wiedererkennung stabil.
Typ-, Scope- und Ausdrucksidentitäten gehören zu ihrem Snapshot. Qualifizierte
Namen dienen der Sprachsemantik und ersetzen keine dauerhafte Identität.
Fachliche Reihenfolgen liegen in geordneten Werten. Maps dienen als Indizes.
Ein veröffentlichter Snapshot bleibt nach Schliessen des Workspace lesbar.

## Lauf und Hostgrenzen

1. Der Host erfasst Quellen und Konfiguration und beschafft fehlende
   Abhängigkeiten nach expliziter Policy.
2. Der Workspace hält eine unveränderliche Eingabesicht samt Revisionen,
   Resolverprovenienz und Overlay-Fingerprint fest.
3. Compilerphasen parsen, sammeln Deklarationen, binden Namen und prüfen
   Typen und fachliche Regeln. Die Engine publiziert Analyse und
   typisierten Ausgang. Das Gate entscheidet über die Schemasicht.
4. Der Host verwirft veraltete Ergebnisse vor Übernahme oder Veröffentlichung.
   Darstellung und Layout bleiben vom Compiler getrennt.

Laufbeobachtung und Abbruch gelten pro Operation. Ein interner Fehler erzeugt
einen eigenen Ergebniszustand. Projektoren weisen ihre Fähigkeiten explizit
aus und melden ununterstützte Elemente, bevor Daten verloren gehen. Reine
Projektionen und [künftige Verbraucherpläne](design/transfer-validation-database.md)
verwenden explizite Profile und unveränderliche Overlays. Ein Planfingerprint
bindet Modellsemantik, Sprachfassung, Profil, Overlay und Planversion.
