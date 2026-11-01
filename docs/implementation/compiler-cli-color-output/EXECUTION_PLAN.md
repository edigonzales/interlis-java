# INTERLIS Compiler CLI – farbige Konsolendiagnosen und Quellkontext

**Verbindliche Implementierungs- und Abnahmespezifikation · 1. November 2026**  
**Repository:** `edigonzales/interlis-java` · **Implementierung/Orchestrierung:** Codex (Agent A) · **unabhängiger Reviewer:** OpenCode 2.x mit **DeepSeek V4.1 Flash 1017 via direkter DeepSeek API** (Agent B).  
**Analysierter `main`-Gitbaum:** `b8051493a8b93e0be4b12735785803d068df6f80` (kein Resetziel). Der im Auftrag erwähnte Link `edigonzales/interlis-guru` lieferte bei der Prüfung 404; die beschriebenen, bereits gemergten CLI1–CLI3-Änderungen sind im erreichbaren `edigonzales/interlis-java` nachgewiesen. Vor Arbeitsbeginn den tatsächlichen lokalen Remote, Branch, HEAD und Pfadbestand prüfen; ist der lokale Stand anders, konkrete Unterschiede abgleichen, **nicht** blind auf den Analysecommit zurücksetzen.

## 1. Ziel, Grenzen und Herkunft

### 1.1 Produktziel

Das bereits vorhandene eigenständige Programm **`interlis-compiler`** (Gradle-Modul `:interlis-compiler-cli`) soll auf interaktiven Terminals verständliche, zurückhaltend **farbige** Diagnosen ausgeben. Dazu gehören auf Wunsch Quelltextausschnitte mit einer optisch eindeutig markierten Fehlerstelle. Die Ausgabe bleibt in Logs, Pipes und JSON zuverlässig maschinenlesbar. Der CLI-Aufruf entscheidet über die Darstellung; die kanonischen Compilerdiagnosen bleiben unverändert.

Drei feste Entscheidungen:

1. **Kein altes `ili2c`-Bedienprofil** und keine Wiederherstellung von `interlis-cli`, `ili2c` oder entsprechenden Alias-/Kompatibilitätslaunchern.
2. **Keine zweite Diagnoseengine**: Der vorhandene `DiagnosticMessageFormatter` liefert bereits echte deutsche/englische Texte; `Diagnostic.relatedLocations()` enthält zusätzliche Positionen; `AnalysisSnapshot.requestDiagnostics()` liefert die auftragsrelevante Diagnosemenge. Neue Farbe und Quellkontext sind reine Darstellung.
3. **Keine Python-Abhängigkeit**: Änderungen, Tests und Reviews bleiben unter dem bestehenden Python-freien Build-/Verifikationsvertrag. Es entsteht kein zusätzliches Terminal- oder allgemeines UI-Framework ohne nachweisbaren Nutzen.

### 1.2 Explizit ausserhalb des Auftrags

Keine Compiler-/Parser-/Namensraum-/Normkorrektur, keine Modellerweiterung, kein Validator/DB-CLI, keine Änderung von XFAILs/Normentscheidungen oder Sprache 2.3/2.4, kein Umbau des LSP oder der JavaScript-/WASM-Bridge, keine Veröffentlichung und kein automatischer Push/Merge/Release. Keine ANSI-Ausgabe in API-Diagnosemodellen, Log-/Telemetryereignissen, JSON oder Schreibartefakten. Keine globale Änderung von `Locale`, `System.out`, `System.err` oder Terminalsitzungen. Auch kein neuer `ConsoleDiagnosticRenderer` neben dem bestehenden als konkurrierende Logik.

Die früheren G2-, P3-/G3- und CLI1–CLI3-Abnahmeberichte bleiben historische, unveränderte Prüfstände. Dieser Auftrag erhält ein **neues** kumulatives PASS für den geänderten, eng abgegrenzten CLI-Darstellungsvertrag.

### 1.3 Nachgewiesene Ausgangslage

| Bestehender Ort | Gegenwärtige Funktion | Folgerung für diesen Auftrag |
|---|---|---|
| `interlis-compiler-cli/src/main/java/guru/interlis/cli/Main.java` | Einstieg `interlis-compiler`; Windows-Unicode-Argumentübergabe | Nicht umbenennen oder Windows-Startskripte destabilisieren |
| `CliEnvironment.java` | Injiziert Streams/Umgebung/`CompilerServices`; Hoststreams UTF-8 | Terminalfähigkeit als **injizierbare, run-lokale** Eigenschaft ergänzen, ohne alte Konstruktoren zu brechen |
| `CommandSpec.java`, `DefaultCommandLineParser.java` | Explizite Optionsgrammatik; bisher separate `--diagnostic-language`-Option | Farboptionen ohne stillschweigende Umdeutung anderer Argumente ergänzen |
| `DefaultCompilerCli.java` | Befehle, Sprachauswahl und Ausgabe; Textdiagnosen auf `stderr`, JSON auf `stdout` | Farbentscheidung einmal pro Run, Ausgabeänderungen eng begrenzen |
| `ConsoleDiagnosticRenderer.java` | Reine DE/EN-Darstellung samt RelatedLocations/Hint und revisionstreuer Position | Hier Stil und Quellkontext ergänzen; bestehende Aufrufe weiterhin unterstützen |
| `interlis-model/.../DiagnosticMessageFormatter.java` | Sichere Textsubstitution mit Kontrollzeichen-/Bidi-Escaping | Weiterverwenden; keine semantischen Metadaten für Farbzwecke verändern |
| `JsonDiagnosticWriter.java` | Strukturierte Diagnoseausgabe | **Nicht** stylen, keine ANSI-Sequenzen, keine Vertragsänderung |
| `DiagnosticPresentationContractTest`, `CompilerCliIdentityContractTest`, `CompilerCliProcesses` | In-Process- sowie reale CLI-Prozesse | Erweitern, vorhandene Tests/Canaries behalten |
| `docs/implementation/compiler-cli-modernization/STATUS.md` | CLI1–CLI3 kumulativ PASS an SHA `0136aec461b60c668c63344f7a30ee4bcec4fd78` | Darauf aufbauen; keine Altberichte überschreiben |

Vor Änderungen aktuelle Dateiinhalte, `settings.gradle`, Root-Tasks (`releaseCheckP3`, `check`, `pythonFreeCheck`, CLI-Process-Check), CI-Matrix und `docs/spec/AGENTS.md` prüfen. **Eine Dateiliste aus dieser Spezifikation ist kein Beweis, dass alle Pfade in einem späteren Checkout unverändert sind.** Äquivalente vorhandene APIs wiederverwenden.

## 2. UX- und Protokollvertrag

### 2.1 Kommandos und Geltung

Für Befehle, die menschenlesbare **Diagnosen** ausgeben (`check`, `compile`, `analyze`, `diagram`, `format`), gelten zwei gleichwertige Schreibweisen:

```sh
interlis-compiler check --color=auto Modell.ili
interlis-compiler check --color always Modell.ili
interlis-compiler check --color=never --diagnostic-language de Modell.ili
interlis-compiler check --json --color=always Modell.ili
```

- Gültige Werte **`auto`**, **`always`**, **`never`**, exakt kleingeschrieben; Standard **`auto`**.
- Ein unbekannter oder fehlender Wert, ein doppeltes `--color` (auch in gemischter Schreibweise) oder eine an falscher Stelle stehende Option ist `USAGE_ERROR` **Exit 2**, ohne Modell-/Repositoryzugriff.
- `--color=...` ist eine **bewusst nur für diese Option** zugelassene `--name=value`-Form. Die bestehende Grammatik und `--`-Semantik bleiben; nicht pauschal beliebige Optionen neu interpretieren.
- Die Farboption ist **ausschliesslich eine Textdiagnoseoption**. Hilfe dokumentiert sie. `help`, `version`, `capabilities`, `--explain-config` und andere rein maschinenbezogene Ausgaben werden durch diese Arbeit nicht farbig gemacht; bei diesen Befehlen darf `--color` ausdrücklich abgelehnt werden, wenn das bisherige Schema keine Diagnoseoptionen zulässt.
- Existing `--diagnostic-language de|en`, `--json`, Warnungs- und Repositoryoptionen behalten ihre Bedeutung. Keine Konfigurationsdatei- oder Environment-Optionen zusätzlich erfinden.

### 2.2 Farbauswahl – verbindliche Priorität

| Bedingung | Erwartung |
|---|---|
| Maschinenoutput (`--json`, JSON-`version`, Formatergebnis, Diagramm/Mermaid/PlantUML/JSON auf stdout, Konfiguration, Datei-/Lockausgabe) | **Immer ohne ANSI**, selbst mit `--color=always`. Die Option beeinflusst allenfalls separate menschenlesbare Diagnosen auf stderr. |
| Explizit `--color=never` | **Nie ANSI** in Diagnoseausgabe |
| Explizit `--color=always` | **ANSI nur auf dem menschenlesbaren Diagnosekanal**, auch bei Pipe, `NO_COLOR` oder `TERM=dumb` |
| `auto` und `NO_COLOR` nichtleer vorhanden | Ohne ANSI |
| `auto` und `TERM=dumb` (case-insensitive) | Ohne ANSI |
| `auto` und stderr **sicher** interaktives, ANSI-fähiges Terminal | ANSI verwenden |
| `auto` und stderr umgeleitet, unbekannt oder nicht sicher erkennbar | Ohne ANSI (fail closed) |

`NO_COLOR` ist nach [no-color.org](https://no-color.org/) bei **nichtleerem** Wert aktiv; ein ausdrücklich gewähltes CLI-Argument darf den Umgebungsdefault übersteuern. Für den ersten Umfang werden **keine** weiteren FORCE_COLOR-/CLICOLOR-/CI-Prioritäten zugesagt. Andere Dienste ändern diese Umgebungsvariablen nicht. Keine globale Locale, keine Shellbefehle mit Benutzereingaben. Ausgabe bleibt UTF-8.

**TTY-Falle:** `System.console() != null` bzw. `Console.isTerminal()` unter Java 25 ist **kein vollständiger Beleg für stderr-FD 2**; auf POSIX prüft die JDK-Implementierung primär stdin/stdout. `interlis-compiler check X.ili 2>error.log` darf bei `auto` deshalb **nicht** wegen eines interaktiven stdout farbig in die Datei schreiben. Agent A muss eine verlässliche Strategie für die tatsächlich verwendete stderr-Senke finden und mit echten Prozessen belegen. Die injizierte Capability ist die testbare Policy-Grenze; wenn zuverlässige Host-Erkennung auf einer Plattform nicht verfügbar ist, konservativ **ohne** Farbe statt unsicherer positiver Annahme. Eine kleine separate Terminal-Abhängigkeit ist nur nach konkretem Vergleich/Beleg zulässig; kein FFM-/native-access-Zwang und kein neues allgemeines CLI-Framework als Reflexlösung. `--color=always` funktioniert unabhängig davon.

### 2.3 Farben und Barrierearmut

Nur **ANSI-SGR** (z. B. `ESC[31m` und `ESC[0m`), keine OSC-Links, Cursorbewegungen, Bildschirm-Löschsequenzen, Alternate-Screen, unsichtbare Hyperlinks oder Truecolorpflicht. Verwende eine zurückhaltende 8-/16-Farben-Palette:

| Rolle | Darstellung | Text muss auch ohne Farbe klar sein |
|---|---|---|
| `ERROR` | Rot / eventuell fett | Sprachgerechtes `Fehler` / `ERROR` |
| `WARNING` | Gelb | `Warnung` / `WARNING` |
| `INFO`, `HINT` | Blau/Cyan | Vollständige Bezeichnung |
| Quellort | Cyan oder zurückhaltend hervorgehoben | `file:line:column` sichtbar |
| Diagnosecode, Zeilennummern | Grau/normal | Codes und Zeilennummern bleiben lesbar |
| Markierte Quellspanne | Rot/Gelb in derselben Fehlerschwere | Zusätzlich `^`/`~`-Markierung; Farbe nicht alleiniger Träger |

Weder Farbe noch zusätzliche Ausrichtung dürfen Severity, Exit, Diagnosetext, ID, Zeilen-/Spaltenwerte oder Diagnosereihenfolge verändern. Jede Stilsequenz wird vor Ausgabeabschluss **ordnungsgemäss geschlossen**; auch ein Fehler darf keine nachfolgenden Shellzeichen dauerhaft einfärben. Die Palette ist statisch und testspezifiziert, aber keine öffentliche fachliche API.

### 2.4 Beispiel, kein Goldstandard für jede Breite

```text
SO_AGI_DataCatalog_Base_20260529.ili:279:5: Fehler [ILI-SEM-0010]
  Der lokale Typname 'Office' kollidiert mit einem Namen im umschliessenden Namensraum.
  278 |     !!@ dcatap.class=foaf:Agent
  279 |     CLASS Office =
      |     ^^^^^^^^^^^^
  280 |       identifier : MANDATORY TEXT*255;
  Verwandte Stelle: ...:272:9: Kollidierende Deklaration von 'Office'.
  Hinweis: Verwende unterschiedliche Namen für die kollidierenden Deklarationen.
```

Die tatsächlich markierte Spanweite kommt aus der `SourceRange` der Diagnose; **nicht** per Modellname/`Office` oder fest verdrahtetem Spaltenwert aus diesem Beispiel. Das Beispiel beschreibt die Form; konkrete AST-Ranges und Quellspalten bestimmen die echte Ausgabe. Interaktive Ausgabe verwendet die Palette, ansonsten denselben Text ohne SGR.

## 3. Klassen-/Methodenvertrag (vorgeschlagener enger Zuschnitt)

Keine redundanten Rendererklassen oder globalen Dienste. Namen können bei besserer Einbettung angepasst werden, **Verhalten und Tests dürfen nicht entfallen**.

### 3.1 Neue kleine Typen im Modul `guru.interlis.cli`

```java
public enum ColorMode { AUTO, ALWAYS, NEVER }

public record TerminalCapabilities(boolean stderrInteractive) {
    // Keine globalen statischen Mutable-Zustände.
}

public final class ColorPolicy {
    public boolean useAnsi(ColorMode mode, TerminalCapabilities terminal,
                           Map<String, String> environment, boolean machineOutput);
}

public enum AnsiRole { ERROR, WARNING, INFO, HINT, LOCATION, CODE, SOURCE_MARKER }

public final class AnsiStyler {
    public String style(AnsiRole role, String alreadySanitizedText, boolean enabled);
}
```

- `ColorPolicy` ist pure, deterministische Logik, die **alle** Prioritäten aus §2.2 ohne IO auswertet.
- `AnsiStyler` darf **keine** untrusted Strings selbst als bereits sicher deklarieren. Die einzige Benutzung erfolgt an der Grenze **nach** der Sanitierung durch `DiagnosticMessageFormatter.safeText` bzw. den Source-Snippet-Escaper. Styling nur feste SGR-Sequenzen, keine willkürlichen Sequenzen aus Modelleingaben.
- `TerminalCapabilities` wird über den Host-Einstieg erzeugt und pro `CliEnvironment` mitgegeben. Bei künstlichen `PrintStream`s / Tests Default **stderrInteractive=false**. Systemseitige Detektion wird einmalig vor Run-Beginn erzeugt. In `DefaultCompilerCli.run` muss sie beim Kopieren der Umgebung unverändert weitergereicht werden.
- `CliEnvironment` darf dafür einen zusätzlichen Recordbestandteil erhalten, **muss aber** die existierenden 5- und 6-Argument-Konstruktoren quellkompatibel behalten; weitere vorhandene Konstruktoraufrufe prüfen. Besser keine Änderung am Compiler-/Model-Modul. Programmatische Aufrufer benötigen keinen Host/TTY-Zugriff.

### 3.2 Parser und Aufruf

`CommandSpec.standard(...)` fügt `--color` nur für fachlich zulässige Diagnosebefehle ein. `DefaultCommandLineParser.parse(...)` verarbeitet `--color=VALUE` als enges Äquivalent zu `--color VALUE`, hält bisherigen `--`-Modus ein und verweigert Doppelnennung. `ParsedCommand` stellt den Wert wie bisher mit `value("--color")` bereit.

`DefaultCompilerCli.execute(...)` liest zu Beginn, nach der Befehlsprüfung, den `ColorMode` und bestimmt **einmalig** den effektiven Stilmodus aus der bestehenden `CliEnvironment`. Die Sprache bleibt `DiagnosticLanguage`. Beim Start einer `--json`-Operation wird die menschliche ANSI-Projektion gar nicht aufgerufen. `print(...)` verwendet die bereits selektierten `requestDiagnostics()` unverändert, übergibt SourceTexte und Stilkontext an den bestehenden Renderer. Keine Änderung des `CliOutcomeMapper`, des `CompilationGate` oder des `JsonDiagnosticWriter` für Farbe.

### 3.3 Bestehenden `ConsoleDiagnosticRenderer` erweitern

```java
// Bestehende öffentliche Overloads bleiben: render(Diagnostic, Map<...>)
// und render(Diagnostic, Map<...>, DiagnosticLanguage).
public String render(Diagnostic diagnostic,
                     Map<SourceId, SourceText> sources,
                     DiagnosticLanguage language,
                     RenderOptions options);

public record RenderOptions(boolean ansiEnabled,
                            int contextLinesBefore,
                            int contextLinesAfter,
                            int maxVisibleColumns) { }
```

`RenderOptions` sind lokale Textpräsentationsoptionen, keine Compileroptionen; empfohlene Defaultwerte 1 Zeile davor, 1 danach, maximal 160 sichtbare Spalten. Für bestehende Overloads wird **ohne ANSI** gerendert; sie dürfen keine impliziten System-/TTY-Abfragen ausführen. Bestehende JSON- und IDE-Verbraucher bleiben unverändert.

### 3.4 `DiagnosticSnippetRenderer` (testseitig ausführbares Hilfsobjekt)

```java
final class DiagnosticSnippetRenderer {
    Optional<String> renderPrimary(Diagnostic diagnostic,
                                   Map<SourceId, SourceText> sources,
                                   RenderOptions options,
                                   AnsiStyler styler);
}
```

Regeln:

1. Keine IO-Zugriffe, kein Lesen von Pfaden aus der Diagnose; ausschliesslich die **übergebenen** `SourceText`-Objekte. Nur exakt passende SourceId, SourceRevision und gültiger `SourceRange` erzeugen einen Ausschnitt; bei fehlender/alter Quelle wie bisher `@revision (UTF-16 a..b)` anzeigen und **keinen erfundenen Quelltext**.
2. Das eigentliche Modell ist **untrusted**. ANSI-/C0-/C1-/OSC-Kontrollen, Zeilen-/Bidi-Steuerzeichen und gefährliche Escape-Folgen aus Dateiname, Quelle, Token und Diagnoseargumenten müssen vor Anordnung/Styling als druckbarer Text escaped werden. Bestehende `safeText`-Logik wiederverwenden und für C1 gegebenenfalls gezielt ergänzen. Kein raw `ESC` aus Quellen.
3. Quellpositionswerte bleiben **UTF-16-basiert** (LSP-Kontrakt). Für den **visuellen Marker** dagegen Displaybreite der bereits escaped/dargestellten Zeichen beachten; Tab-Expansion mit deterministischem Tabstop (z. B. 4), CRLF, Surrogatpaare/Emoji, kombinierende Zeichen, CJK und breitere Codepoints durch passende Tests abdecken. Bei nicht sicher darstellbarer Breite konservativ `line:column` zeigen und Marker weglassen oder auf zuverlässige Stelle begrenzen – niemals einen falsch positionierten Pfeil als exakt behaupten.
4. Primärspanne möglichst als `^` und `~` darstellen, **ohne** die Quelle umzuformatieren. Bei mehrzeiligen Ranges nur den ersten relevanten Abschnitt plus knappen Hinweis; keine unbeschränkte Rahmung. Ein Null-Längenbereich wird als Einfügepunkt markiert.
5. Maximal **3 sichtbare Kontextzeilen** und maximal **160 Displayspalten** je Zeile; sehr lange Zeilen um die Fehlerposition ausschnittweise zeigen und Ellipsen kenntlich machen. Keine quadratische Verarbeitung, keine ungebundenen Allokationen, keine vollständige Ausgabe riesiger Dateien. Numerische Limits testen.
6. Existing `relatedLocations()` und `DiagnosticMessageFormatter.formatRelated()` bleiben als eigene Hinweise sichtbar; für verwandte Orte optional **keinen** zweiten Quelltextblock, um Redundanz zu vermeiden. Kein Duplikat derselben Fehlermeldung, keine ungeprüfte Normquelle oder automatisch erfundene Korrekturen.

### 3.5 Keine Entmischung von Kanälen

- Textdiagnosen bleiben auf **stderr**. JSON-Diagnosen weiter auf **stdout**, stderr frei soweit der bestehende Vertrag es verlangt; `--color=always --json` muss strikt gültiges JSON ohne ANSI ergeben.
- `format`-Quelltext und `diagram`-Mermaid/PlantUML/JSON auf stdout bleiben **bytegleich** zu vor der Änderung für identische Eingaben (ausser deterministisch begründete bereits vorhandene Versionstexte). Output-Dateien bleiben bytegleich. Farben erscheinen nur auf getrenntem menschenlesbarem Diagnosekanal.
- `CliExecutionResult`, `Diagnostic`, SourceRanges, IDs, Cause/Related-Verknüpfungen, Exitcodes, Telemetrie und Public SDK bleiben semantisch unverändert. Keine versteckte zweite Diagnosefilterung oder Änderung an Sprachakzeptanz.
- An sämtlichen Ausgabewegen dürfen ANSI und Textfehler einen fehlgeschlagenen Flush nicht verdecken; der bestehende `CliTermination.OPERATIONAL_FAILURE`-Vertrag bleibt.

## 4. Phasen und eindeutige Abnahmen

**Wichtig:** Genau ein unabhängiger Review am Ende **jeder der drei Phasen**, nicht pro Klasse/Test. `COLOR3` überprüft kumulativ `COLOR1`/`COLOR2` im selben Review; **kein zusätzlicher rein administrativer Review**. Alle Kriterien sind Muss-Kriterien; nicht erfüllte oder nicht ausführbare Pflichtpunkte erzeugen `FAIL` bzw. `BLOCKED`, nie PASS.

### COLOR1 – Farbpolitik, Terminal- und Optionsvertrag

Lieferumfang: `ColorMode`, `ColorPolicy`, `AnsiStyler`, host-/injection-taugliche stderr-Terminalfähigkeit, CLI-Optionsgrammatik und Tests. In COLOR1 ist mindestens die Severity-Einfärbung bereits an die echte Textdiagnoseausgabe anzuschliessen, damit die `always`-/`never`-Prozesse echte, unterschiedliche Terminalbytes beweisen. Der ausführliche Quelltextframe folgt in COLOR2.

| ID | Verbindliche Abnahme |
|---|---|
| C1-01 | Die zwei Schreibweisen `--color=MODE` und `--color MODE` liefern für alle fünf Diagnosebefehle identische Parserresultate; `--` und andere Optionen bleiben gleich. |
| C1-02 | `auto/always/never`, Missing/Invalid/Duplicate, Help und Exit2 vor IO sind geprüft. |
| C1-03 | Policy-Priorität nach §2.2 ist an einer vollständigen deterministischen Kombinationstabelle geprüft, inklusive leerem/nichtleerem `NO_COLOR` und `TERM=dumb`. |
| C1-04 | `CliEnvironment` kann `stderrInteractive` injizieren; bestehende Konstruktoren/Streams und UTF-8 bleiben funktionsfähig. |
| C1-05 | `auto` basiert auf nachweisbarer stderr-FD-2-Fähigkeit, nicht bloss stdout oder `System.console()`. Unklare/umgeleitete Senken ergeben `false`. |
| C1-06 | `AnsiStyler` erzeugt nur erlaubte SGR-Sequenzen und einen sauberen Reset; mit disabled genau den Originaltext. |
| C1-07 | Reale Prozesse: `auto` in Pipe und `2>file` enthalten keine SGRs; `always` färbt auf demselben Pfad; `never` tut es nie. Falls ein Host hierfür keine sichere interaktive Autoerkennung bietet, wird das dokumentierte konservative Verhalten geprüft. |
| C1-08 | Kein neues Terminal-/CLI-Framework ohne belegten Bedarf, keine Python-/Interpreterabhängigkeit, keine Änderung an Compiler-/LSP-/Bridge-Verträgen. |

Pflichtgate: `:interlis-compiler-cli:test` plus gezielte Parser-/Hostprozess-/UTF-8-Proben, `pythonFreeCheck`, `git diff --check`, **unabhängiges COLOR1-PASS**. Folgerisiken in betroffenen Modulen gezielt prüfen. Grössere Plattform-/FullReleasegates sind in COLOR3 gebündelt.

**Besondere Reviewer-Gegenprobe:** Baue eine bisher nicht vorhandene Kombination aus `NO_COLOR`, `TERM`, gemockter stderr-Terminalfähigkeit und explizitem `--color=always`; führe zusätzlich einen realen Prozess mit `2>file` und `auto` aus. Falscher positiver TTY-Nachweis ist FAIL.

### COLOR2 – Farbige Diagnosen und sicherer Quelltextausschnitt

Lieferumfang: Erweiterung des bestehenden Text-Renderers, bounded Source-Frames und Eingabehärtung; keine Änderung der kanonischen Diagnosemodelle.

| ID | Verbindliche Abnahme |
|---|---|
| C2-01 | DE/EN-Texte, Fehlercodes, SourceId/Revision, Primär- und RelatedLocations, Hinweise bleiben vorhanden und inhaltlich identisch. |
| C2-02 | ERROR/WARNING/INFO/HINT, Orte, Codes und Marker sind differenziert und ausschliesslich mit erlaubten SGR-Sequenzen gestaltet. |
| C2-03 | Für gleiche Fälle gilt `stripAllowedSgr(ansiOutput) == plainOutput` **bytegenau**, abgesehen von vereinbarten reinen Stilsequenzen. Kein verloren gegangener Inhalt. |
| C2-04 | Primärquelltext und Caret erscheinen aus exakt passendem SourceText; ohne/staler SourceRevision erscheint kein Frame. Keine Host-IO. |
| C2-05 | ASCII, Tabs, CRLF, UTF-16-Surrogate/Emoji, kombinierende Zeichen und CJK verschieben den Marker nicht falsch; unsichere Breiten werden nicht als exakt markiert. |
| C2-06 | Mehrzeilig, Nullspanne, lange Zeilen und extreme Rangegrenzen bleiben begrenzt (3 Kontextzeilen, 160 Displayspalten) und crashfrei. |
| C2-07 | ANSI-/OSC-/C0-/C1-/Bidi-Injection aus Modelltext, Identifikator, URI oder Diagnoseargument wird neutralisiert; keine Displaysteuerung aus untrusted Quellen. |
| C2-08 | JSON bleibt vollständig ohne ANSI und mit identischen maschinellen Daten bei `always/auto/never`, auch mit `--diagnostic-language de`; stderr frei nach bisherigem Vertrag. |
| C2-09 | `format`/`diagram`-stdout, Ausgabe-/Lockdateien, Version/Capabilities/Help und fachliche Exitcodes bleiben unverändert. |
| C2-10 | Office-Original `testdata/cli-modernization/original/SO_AGI_DataCatalog_Base_20260529.ili` bleibt bytegenau; die bestehende echte Name-Conflict-Diagnose samt RelatedLocation wird lesbar markiert, ohne hardcodierte Dateinamen/Zeilen. |
| C2-11 | Vollständige vorhandene CLI-Regressionen einschliesslich Root-Isolation, Foreign-Warnings, Missing-Import und Renderer-/UTF-8-Canaries bleiben grün. |

Pflichtgate: alle relevanten CLI-, Model- und Hosttests; echte `interlis-compiler`-Prozesse aus frisch gebauter lokaler Distribution in Auto/Always/Never und JSON; `pythonFreeCheck`; **unabhängiges COLOR2-PASS**.

**Besondere Reviewer-Gegenprobe:** Eine eigene bösartige `.ili`-Quelle mit `ESC[2J`, OSC 8, Bidi-Isolaten, CRLF, Tab + Emoji vor dem Fehler; teste `--color=always` gegen `--color=never`, prüfe vollständige Rücksetzsequenzen, Positionskorrektheit und unverändertes JSON. Falscher Marker, echter Steuerbefehl oder ANSI im JSON ist FAIL.

### COLOR3 – Distribution, Plattformen, Gesamtregression

Keine neue Architektur. Letzte Dokumentation, Integration der Prozesstests in die vorhandenen Releasegates und kumulative unabhängige Abnahme.

| ID | Verbindliche Abnahme |
|---|---|
| C3-01 | Gebaute/entpackte Distribution liefert `bin/interlis-compiler` und unter Windows `.bat`; auch ausserhalb des Checkouts ohne Testclasspath, mit UTF-8-Pfaden. Keine alten Launcher/Legacyprofile. |
| C3-02 | Interaktive, umgeleitete und erzwungene Farbe wird als **echter Prozess** nachgewiesen; ein Redirect von stderr bei weiter interaktivem stdout ist ein eigener Negativtest. |
| C3-03 | `check`, `compile`, `analyze`, `diagram`, `format` bewahren Ausgabekanäle, Exit- und Datei-/Abbruchverträge; Styling nur der Textdiagnosen. |
| C3-04 | DE/EN, Text/JSON, NO_COLOR, TERM, TTY/Non-TTY und alle Farbbetriebsarten sind in Prozess- und Unit-Tests, mit echten Positiv-/Negativfällen, vollständig abgedeckt. |
| C3-05 | `./gradlew releaseCheckP3` (oder dokumentierter tatsächlich gleichwertiger nachfolgender Gate-Einstieg) vollständig und frisch am finalen SHA mit echten Test-/Reportzahlen, 0 unerklärten Skips und unveränderten Norm-/Conformance-Pins. |
| C3-06 | Die fälligen JVM/CLI/SDK/LSP/TeaVM/Browser inkl. echter Safari/Web-Image-/Korpus-/Pythonfreiheits-/Plattformverträge behalten ihre vorherigen Pflichtbudgets; keine Tests deaktivieren. |
| C3-07 | Reale macOS-, Linux- und Windows-Prozessprüfungen oder korrekt zugeordnete CI-Evidenz für **denselben finalen Produkt-SHA**, ohne erfundene Auto-TTY-Erfolge im headless CI. |
| C3-08 | Mutierte/fremde/unvollständige/übersprungene Berichte, falsche Kandidaten-SHAs und ANSI in JSON/Dateien werden von aktiven Checkern abgewiesen; wenigstens ein eigener negativer Nachweis des Reviewers. |
| C3-09 | `README.md`, CLI-Hilfe, aktuelle CLI-/SDK-/Prüfanleitungen und eigene Folgeauftrag-Statusdatei stimmen mit dem implementierten Verhalten überein; historische CLI1–CLI3-Berichte bleiben unberührt. |
| C3-10 | Unabhängiger OpenCode-Review bewertet **C1-01..08, C2-01..11 und C3-01..10 kumulativ** mit `PASS`; kein ungeklärtes Muss-Finding/Blocker. |

Pflichtgate: `releaseCheckP3`, ggf. explizit ausgelagerte fällige Zusatzgates, geprüfte Plattformprozesse und **ein finaler kumulativer OpenCode-Review**. Wird die Terminal-Interaktivität auf einem headless Host nicht echt prüfbar, durch autorisierte PTY-/Terminal-/CI-Nachweise ergänzen; nicht „in einem Unit-Test auf true gesetzt“ als realen TTY-Beleg ausgeben. `--color=always` deckt die unabhängig davon ausführbare Funktion ab.

## 5. Teststrategie – kompakt, aber belastbar

| Testsorte | Arbeitsort/Prüfung | Orakel |
|---|---|---|
| T1 – Pure Unit | `ColorPolicyTest`, `AnsiStylerTest`, `DiagnosticSnippetRendererTest` | Explizite Farbtabelle, SGR-Whitelist, sichere Sanitierung und Grenzen |
| T2 – In-Process CLI | `DiagnosticPresentationContractTest`, `CompilerCliIdentityContractTest`, neue Farboption-/Renderertests | Quelle und Exit unverändert; COLOR-Modus/DE/EN/JSON, explizite `CliEnvironment` |
| T3 – Echte CLI-Prozesse | `CompilerCliProcesses`, bestehende Prozesschecker und separate PTY-/Redirectproben | Reale stderr-Bytes, stdout-/JSON-Byteverträge, echte gebaute Launcher |
| T4 – Kumulativ | Vorhandene G2/P3- und `releaseCheckP3`-Gates | Keine Sprache-/Korpus-/Plattform-/SDK-/LSP-/Buildregression |

Alle Tests, die einen synthetischen farbigen Renderer prüfen, verwenden **echte formatierte Diagnosen oder vollständig bezeichnete kanonische Fixtures**. Kein Test darf durch blosse Prüfung auf das Wort `red`/`warning`, durch `contains("\u001b[")` ohne inhaltliche Kontrolle oder durch an der richtigen Stelle fehlendes SourceText grün sein. Verwende für ANSI-Parität einen engen SGR-Stripper, der **unerwartete Steuersequenzen abweist** statt sie still zu entfernen. Negativcanaries beweisen, dass ein falsches Exit, ANSI-Leck, fehlender Reset, nicht passender SHA und ein falscher Markeroffset den Checker tatsächlich scheitern lassen.

### 5.1 Fällige Kommandos ermitteln statt erfinden

Vor dem ersten Phasenende mindestens:

```sh
./gradlew :interlis-compiler-cli:test
./gradlew pythonFreeCheck
```

Vor COLOR3 die tatsächlich vorhandenen Tasks mit `./gradlew tasks --all` prüfen und den dokumentierten, für den neuen Stand passenden vollständigen Profileinstieg ausführen, aktuell `./gradlew releaseCheckP3`. Falls der aktuelle Root-Task zusätzliche Qualifikationen vorsieht, diese einschliesslich Browser/SDK/Conformance beibehalten. Ein grüner lokaler Node-Test ersetzt keinen echten Safari-/Web-Image-/Windows-Vertrag. Selbständiger Reviewer führt die fälligen Prüfungen frisch aus oder evaluiert belastbare, commitgenau verknüpfte CI-Belege; nicht einfach Agent-A-Logs akzeptieren.

## 6. Verbindlicher autonomer Codex-/OpenCode-Zyklus (GRETL-Muster)

### 6.1 Rollen und Unabhängigkeit

**Agent A (Codex):** Implementiert und committet die jeweilige Phase, führt eigene Tests aus, erstellt kurzen Implementierungsbericht, startet einen **separaten** OpenCode-Prozess und verarbeitet dessen tatsächliches Urteil. Darf die eigene Phase **nicht** freigeben. **Agent B (OpenCode/DeepSeek):** Frischer Kontext und **sauberer separater Worktree** am exakten Kandidatencommit, prüft Plan/Code/Diff selbst, führt Tests und eigene Gegenproben aus. Ändert **keinen Produktcode und keine produktiven Tests** des Kandidaten. Scratch-/Build-/Reviewartefakte nur in separatem Reviewbereich. Ein zusätzlicher eigener Gegentest kann später von Codex in den Produktcode übernommen werden, dann neuer Kandidatencommit und neue Prüfung.

Die Arbeit ist **autonom**: Codex führt COLOR1, Review, eventuelle Nacharbeit, COLOR2, Review, COLOR3 und Gesamtreview ohne manuelle Haltepunkte fort, solange reale Berechtigungen und Voraussetzungen vorliegen. Kein stilles Überspringen der unabhängigen Prüfung. Fehlende Credentials, blockierte Safari-Automation, fehlende CI-Berechtigung oder nicht erfüllbare externe Abnahme sind **`BLOCKED`**; weder Selbst-Review noch erzwungener alternativer Provider sind erlaubt. Keine Benutzer-Meldung als bereits vollzogene Abnahme ohne tatsächlich ausgeführte Prüfschritte.

### 6.2 Preflight / Modellidentität

Vor Beginn: `pwd`, `git status --short`, `git rev-parse HEAD`, Branch/Remote, JDK/Gradle/Node/Browser sowie geltende `AGENTS.md`-Regeln prüfen. Die autonome Reviewumgebung vor grösserer Codex-Arbeit testen. Im Repository wurde zuletzt **OpenCode 2.0.28**, direkter Provider **`deepseek/deepseek-flash`** (API-Modell `deepseek-flash`), API-Host `api.deepseek.com`, Zuordnung **DeepSeek V4.1 Flash 1017** verwendet. **Diese Zuordnung ist lokal erneut zu verifizieren**; nicht bloss Modell-Selbstaussage glauben. Keine OpenRouter-/Zen-/Go-/Fallback-Provider. Bereits vorhandene OpenCode-Credentials/GUI-Konfiguration verwenden; keine API-Schlüssel in Prompts, Reports oder ENV-Dumps kopieren. OpenCode-CLI-Pfad und verfügbare `run`-Optionen tatsächlich prüfen.

`tools/verification/without-python.cjs` ist im bestehenden Projekt die Python-Sperre für relevante Prüfläufe. Für diesen Auftrag die Review- und Testsitzungen weiter unter derselben ausdrücklich bestehenden Sperre ausführen, sofern mit dem aktuellen Ausführungsweg kompatibel. Kein neues Python als Hilfsskript und keine Sicherheitsprüfung nur durch Textsuche. Den tatsächlichen Guard-/Prozessstatus nach Abschluss auswerten.

### 6.3 Liefer- und Reviewablauf für jede Phase

1. **Phase starten** auf einem Stand mit gültigem Vorgänger-PASS; `BASE_SHA` dokumentieren, an aktueller Phase arbeiten. `COLOR1` startet am vorhandenen sauberen Arbeitsstand. Vorherige Fehlerberichte bleiben erhalten.
2. **Implementieren** mit Tests/echtem Input. Für nachweislich bestehende Verhaltenslücken zuerst RED, nach Fix GREEN; keine erfundenen RED-Läufe. Nicht unnötig auf andere Phasen ausweiten.
3. **Eigene Pflichtprüfungen** der Phase frisch durchführen, echte Exits, Testfälle/Skips, SHA, Reportpfade und Toolchain dokumentieren. Einen kurzen Implementierungsbericht unter `docs/implementation/compiler-cli-color-output/implementation/COLOR<N>.md` schreiben. Keine grossen generierten Rohlogs committen.
4. **Commit** von Produkt, Tests und Bericht. `git diff --check`, sauberer `git status`, **vollständigen** `CANDIDATE_SHA` nach dem Commit auslesen. Nicht den SHA in denselben Commit schreiben.
5. **Reviewauftrag** als `request.json` im ignorierten `build/review/compiler-cli-color/COLOR<N>/...` erzeugen: `phase`, `baseSha`, `candidateSha`, `assignmentBaseSha`, `criteriaIds`, Plan-/Reviewerpromptpfad, absolute getrennte Review-/Worktree-Ausgabewege, fällige Prüfungen, erwartetes Providerprofil. Keine vorweggenommenen PASS-Werte, keine Secrets.
6. **Reviewer wirklich starten**: OpenCode 2.x über den verifizierten CLI-Weg, frischer Standalone-Kontext, eigener sauberer detached Worktree auf `CANDIDATE_SHA`, direkter DeepSeek-Provider. Typischer Aufruf nach Prüfung von `opencode run --help`:

   ```sh
   opencode run --standalone --model deepseek/deepseek-flash --format json \
     --file docs/implementation/compiler-cli-color-output/EXECUTION_PLAN.md \
     --file docs/implementation/compiler-cli-color-output/OPENCODE_REVIEW_PROMPT.md \
     --file /ABSOLUTE/PATH/TO/request.json \
     'Prüfe die bezeichnete COLOR-Phase unabhängig; liefere review.md und verdict.json.'
   ```

   Die Dateien müssen aus dem **Reviewworktree** erreichbar sein. Den Review unter `without-python.cjs` bzw. bestehender Guard-Orchestrierung kapseln; keine fremde laufende GUI-Session übernehmen, keine globale macOS-/Windows-Terminaleinstellung verändern.
7. **Echtes Ende abwarten**: ProzessExit, Guard-/Sperrmanifest, vollständige Session, `review.md` und `verdict.json`, tatsächlicher Worktree-HEAD und `git status --porcelain`, tatsächlich ausgeführte Prüfbefehle/CI-Berichte prüfen. Zwischenlog `PASS` genügt nicht. Transportfehler getrennt behandeln; höchstens zwei begründete reine Transportrückläufe auf **demselben** Kandidaten, sonst BLOCKED. Fachliche FAILs nie durch bloßes Wiederholen wegtesten.
8. **Urteil akzeptieren** nur `PASS`, `FAIL` oder `BLOCKED`. Bei FAIL die belegten Findings in **derselben Phase** korrigieren, neu committen und einen neuen, selbständigen Review starten. Bei BLOCKED autonome technische Vorbedingungen beheben; echte Nutzer-/OS-/Berechtigungsentscheidung sauber melden. Nach PASS auf **exakten** Kandidaten **ohne Benutzerstopp** zur nächsten Phase. Letzter COLOR3-PASS ist zugleich einzige Gesamtfreigabe.

### 6.4 Urteile und Maschinenvertrag

`verdict.json` (unabhängig vom Reviewer, ausserhalb des Kandidatenbaums):

```json
{
  "phase": "COLOR1",
  "baseSha": "<full-40-character-sha>",
  "candidateSha": "<full-40-character-sha>",
  "reviewHeadSha": "<full-40-character-sha>",
  "verdict": "PASS",
  "reviewer": {
    "tool": "opencode",
    "version": "<observed>",
    "provider": "deepseek",
    "model": "deepseek/deepseek-flash",
    "apiHost": "api.deepseek.com"
  },
  "criteria": [{"id":"C1-01","status":"fulfilled","evidence":"..."}],
  "ownCounterchecks": [{"name":"...","command":"...","result":"..."}],
  "commands": [{"command":"...","exitCode":0,"report":"...","skipped":0}],
  "missingRequiredEvidence": [],
  "blockingFindings": [],
  "reportPath": "<absolute path to review.md>"
}
```

JSON ist eine **Schema-Vorlage**, kein vorab gültiges Urteil. Prüfe SHA-Vollständigkeit, vollständige Kriterienliste, unverändert sauberen Worktree, tatsächlich gemeldeten Provider/Sessionbeleg, Exitcode/Guard und Nachweise pro ID. Nicht fällige Phase-3-Prüfungen dürfen früher explizit als `future-gate` markiert werden, nicht als bereits bestanden. `PASS` verlangt alle Pflichtkriterien erfüllt, reale Tests beendet und kein offenes Muss-Finding. `FAIL` = belegte Verletzung; `BLOCKED` = erforderlicher Nachweis nicht möglich. Unbekannte Reviewzustände oder unvollständige Berichte als BLOCKED behandeln. Im Reviewbericht keine Annahmen über noch nicht ausgeführte Checks als PASS ausgeben.

### 6.5 Schlanke Dokumentation

```text
docs/implementation/compiler-cli-color-output/
  EXECUTION_PLAN.md
  CODEX_STARTPROMPT.md
  OPENCODE_REVIEW_PROMPT.md
  STATUS.md                                 # von Codex zu führen
  implementation/COLOR1.md ... COLOR3.md   # kurze Berichte
  reviews/COLOR1-review.md ...             # unveränderte Reviewerberichte
```

Rohlogs, Build-Outputs, Screenshots falls benötigt, Guardmanifest, `request.json`, `verdict.json` und echte Testartefakte unter ignoriertem `build/review/compiler-cli-color/...` oder CI-Artefakten. Historische Abnahmen und andere Projekte nicht umschreiben. Nach gültigem Review darf dessen **unveränderte** Berichtskopie als reiner Dokumentationscommit übernommen werden; das Urteil referenziert weiterhin den tatsächlich geprüften Produkt-SHA. **Jede weitere Änderung an Produkt-, Test-, Build- oder Pflichtkriterium nach Review erzeugt einen neuen reviewpflichtigen Kandidaten.** Kein Push/Merge ohne gesonderten Auftrag.

## 7. Definition of Done

COLOR1, COLOR2, COLOR3 erhalten jeweils einen unabhängigen gültigen PASS. Die finale CLI zeigt im interaktiven Terminal farbige, gut lesbare und sichere Diagnosen samt Quellkontext; Umleitung ist in `auto` farbfrei, `always` kann ANSI erzwingen, `never` unterdrückt es. JSON und sonstige Maschinen-/Ausgabeformate bleiben unverändert. Compiler/Workspace/Repository/LSP/Browser, normative Profile und Pythonfreiheit sind regressionsgeprüft. Keine neue Abhängigkeit ohne Begründung, keine versteckten alten `ili2c`-CLI-Pfade, keine Veröffentlichung. Keine fälligen Tests mit Skips als Erfolg deklariert. Der vollständige Abnahmebericht verweist auf den exakten Produktcommit.

## 8. Quellbasis (bei Umsetzung gegen den neuen HEAD prüfen)

- `AGENTS.md`, `docs/spec/09-*`, `docs/spec/13-*`, `docs/spec/18-*` und `docs/spec/AGENTS.md`.
- `docs/implementation/compiler-cli-modernization/{EXECUTION_PLAN,STATUS,OPENCODE_REVIEW_PROMPT}.md` (historischer abgeschlossener Folgeauftrag; dessen Reviewverfahren wiederverwenden).
- `interlis-compiler-cli/src/main/java/guru/interlis/cli/{CliEnvironment,CommandSpec,DefaultCommandLineParser,DefaultCompilerCli,ConsoleDiagnosticRenderer,JsonDiagnosticWriter,Main}.java`.
- `interlis-model/src/main/java/guru/interlis/diagnostics/{DiagnosticMessageFormatter,DiagnosticPresentation,Diagnostic}.java`.
- `interlis-compiler-cli/src/{test,verification,verificationTest}/...` insbesondere `DiagnosticPresentationContractTest`, `CompilerCliIdentityContractTest`, `CompilerCliProcesses`.
- [Java 25 Console.isTerminal](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/io/Console.html) – erklärt das eingeschränkte stdin/stdout-basierte Terminalindiz, nicht separate stderr-Garantie.
- [NO_COLOR](https://no-color.org/) – Environmentdefault und expliziter CLI-Override.

**Abnahmehinweis:** Neue Tests beweisen den realen Farbausgabevertrag; eine hübsche ANSI-Demo oder ein grün getaggtes Markdown ist für sich allein kein Abnahmebeleg.
