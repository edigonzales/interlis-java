# CLI, SDK und Kompatibilitätsadapter

## 1. Ziel

CLI und SDK sind dünne Zugänge zu derselben Engine. Die CLI besitzt keine zweite Sprachprüfung. Der SDK-Aufrufer muss keine Dateien erzeugen, um Strings zu analysieren. Das Compilerprogramm heisst `interlis-compiler`, das Gradle-Modul `interlis-compiler-cli`; die Bibliothek bleibt `interlis-compiler`.

Der Folgeauftrag [Compiler-CLI](../implementation/compiler-cli-modernization/EXECUTION_PLAN.md) ersetzt die frühere ili2c-Bedienkompatibilität. Es gibt keine alten Launcher, Aliase, automatische Profilerkennung oder Legacyartefakte. Der echte ili2c bleibt ausschliesslich externer Sprach-/Metamodell-Testreferenzcompiler. Spätere Fachwerkzeuge erhalten eigene CLI-Module.

## 2. CLI-Klassen und Signaturen

Paket `guru.interlis.cli`. Das Modul verwendet den einzigen Prozesseinstieg `Main`.

Die folgenden Signaturauszüge zeigen Rollen ohne Bodies; die konkreten
immutable Ergebnistypen und additiven Konstruktoren stehen im P3-1-Abschnitt.

```java
public interface CliApplication {
    CliExecutionResult run(List<String> arguments,
                           CliEnvironment environment,
                           CancellationToken cancellation);
}

public interface CommandLineParser {
    CliParseOutcome parse(List<String> arguments, CommandSpec specification);
}

public final class ExitCodePolicy {
    public int exitCode(CliTermination termination);
}

public final class OutputPlanner {
    public OutputPlan plan(ParsedCommand command, CliEnvironment environment);
}
```

Diese Signaturen sind normative Rollenverträge. Implementierungen sind final, soweit keine dokumentierte Erweiterung nötig ist. Die bereits in Kapitel 09 definierten Diagnose-/Laufdatentypen werden wiederverwendet, nicht dupliziert.

| Klasse | Zentrale Methoden | Verantwortung |
|---|---|---|
| `Main` | `public static void main(String[] args)` | nur Umgebung/Abbruchsignal aufbauen, Anwendung aufrufen, Flush/Prozessende |
| `DefaultCompilerCli` | `run(...)` | Eingabe auflösen, Closure vorbereiten, Engine aufrufen, Ergebnisse rendern/exportieren |
| `DefaultCommandLineParser` | `parse(...)` | Optionen/Argumente gemäss CommandSpec parsen, noch keine IO/Modellanalyse |
| `CommandSpec` | Optionsdefinitionen, Positionsargumente, Konfliktregeln, Hilfetextordnung | versioniertes Werkzeugprofil |
| `CliEnvironment` | Arbeitsverzeichnis, stdin/stdout/stderr, immutable Environmentwerte, CompilerServices (Uhr/RunObserver/RunContextFactory) | vom Host übergebene Ressourcen; keine globalen Ersatzstreams |
| `ConfigurationLoader` | `load(ParsedCommand, CliEnvironment, CancellationToken)` | Konfigurationsdateien lesen, Präzedenz und Herkunft offenlegen |
| `DefaultCompilerCli` | `prepare` beziehungsweise Kommandoplanung | explizite Root-/Quellen-/Resolver-/Outputplanung |
| `CliOutputWriter`; Diagnose-/Versionsrenderer | `write(Path,byte[],Optional<byte[]>,CancellationToken)`; `render`/`write` | atomare Dateien; getrennte Text-/JSON-Kanäle und Reihenfolge; gemeinsamer letzter Flush in der CLI |
| `CliOutcomeMapper` | `classify(CompilationOutcome)` | Ergebnisvariante und RejectionSummary zu CliTermination abbilden; Compilererfolg ist noch kein Ausgabeerfolg |

`CliParseOutcome` unterscheidet `ParsedCommand`, `HelpRequested`, `VersionRequested` und `InvalidCommandLine`. Eine Hilfe-/Versionsanforderung benötigt weder Modellserver noch Compilerinitialisierung. Globale `--help`/`-h` und `--version` sind harmlose Standardaufrufe. Fehlende/unbekannte Unterbefehle ergeben Exit 2 mit einem Beispiel `interlis-compiler check <File.ili>`, vor Host-IO.

`CliExecutionResult` enthält mindestens `CliTermination`, konkreten Exitcode, optionalen RunId, strukturierte Diagnosen und Artefaktinformationen. `CliTermination` unterscheidet `SUCCESS`, `SOURCE_REJECTED`, `USAGE_ERROR`, `OPERATIONAL_FAILURE`, `INCOMPLETE_ANALYSIS`, `INPUT_UNRESOLVED`, `WARNINGS_REJECTED`, `CANCELLED`, `INTERNAL_FAILURE`. Diese internen Status sind keine Behauptung bestimmter bisheriger numerischer Exitcodes. `CliOutcomeMapper.classify` verwendet die vom CompilationGate gelieferte RejectionSummary. ENVIRONMENT_FAILURE ergibt OPERATIONAL_FAILURE, INCOMPLETE_SUPPORT ergibt INCOMPLETE_ANALYSIS, MISSING_INPUT oder AMBIGUOUS_INPUT ergibt INPUT_UNRESOLVED, SOURCE_ERRORS ergibt SOURCE_REJECTED, eine alleinige WARNINGS_REJECTED-Ablehnung ergibt den gleichnamigen CLI-Status. Bei mehreren Gründen gilt diese Reihenfolge als Standardpriorität; alle Gründe bleiben im strukturierten Ergebnis erhalten. Der Adapter implementiert keine zweite Root-/Coverage-/Warnungsprüfung. Die endgültige SUCCESS-Einstufung erfolgt erst nach der vollständigen Ausgabe beziehungsweise Formatierung und deren erfolgreichem Flush. Ein Fehler in dieser Abschlussphase ergibt OPERATIONAL_FAILURE und nennt gegebenenfalls bereits entstandene Teilartefakte.

Für `check`, `compile`, `analyze`, `diagram` und `format` gilt `--diagnostic-language de|en`, Default `en` unabhängig vom Host. Ungültige Werte sind Bedienfehler vor IO. ConsoleDiagnosticRenderer erhält die Sprache explizit und verwendet den reinen Formatter aus Kapitel09; sein Komfortaufruf verwendet EN. `check`/`compile` und der Compile-Schritt von `diagram` verwenden `requestDiagnostics()`; `analyze` behält die vollständige Sicht. Diagramm-/Formatausgaben bleiben auf stdout, gewöhnliche Diagnosen auf stderr. Bei `--json` bleibt stdout ein einzelnes JSON-Ergebnis ohne Fremdtext.

CLI-JSON erhält additive Präsentationsfelder `message`/optional `hint`, strukturierte `relatedLocations` (Range, Key, Argumente, gerenderte Message) sowie vorhandene `causeId`/`dependsOn`. Die bestehenden Felder `id`, `code`, `severity`, `phase`, `messageKey`, `arguments`, `range` samt bisheriger Zahlenkodierung behalten ihre Bedeutung. Nur Präsentation ändert sich zwischen DE/EN. LSP-/WASM-Protokolle werden dadurch nicht umbenannt oder auf die CLI-Sicht reduziert.

## 3. Methodenverträge

### 3.1 CliApplication.run

**Vorbedingungen:** Argumente und Umgebung sind nullfrei; Streams sind offen; Das Standardschema ist bekannt. **Wirkung:** Darf über explizite Hostdienste Dateien/Repositories lesen und gewünschte Artefakte schreiben. **Nachbedingungen:** Liefert genau einen Abschlussstatus; strukturierter Laufstatus, Textmeldung und Exitcode stimmen überein. Die Methode ruft kein `System.exit` auf. **Besitz:** Sie schliesst keine vom Aufrufer bereitgestellten Standardstreams. Eigene Dateien/Responses werden geschlossen. **Abbruch:** laufende Beschaffung/Analyse/Ausgabe wird kontrolliert beendet; ein nur teilweise geschriebenes Artefakt wird nicht als vollständige Ausgabe angekündigt.

Die CLI erzeugt über ihre injizierte `RunContextFactory` einen Elternkontext für den gesamten angenommenen Aufruf. Vor der Engineausführung erzeugt sie `createChild(parent)` und verwendet `compile(request, reuse, child, token)`. Nur die Engine startet und beendet diesen Compilerkindlauf. Beschaffung und Ausgabe gehören zum Elternlauf oder erhalten bei Bedarf eigene klar zugeordnete Kinder. Der Elternlauf endet erst nach der verbindlichen Ausgabe und deren Flush; ein zuvor erfolgreicher Compilerkindlauf darf den späteren Ausgabefehler nicht verdecken. Hilfe und Version benötigen keinen künstlichen Compilerkindlauf. Kontextweitergabe erfolgt explizit gemäss Kapitel 09.

Ungültiger Quelltext ist ein regulärer `SOURCE_REJECTED`-Fall. Eine nicht lesbare Quelldatei ist ein IO-/Auflösungsfehler. Eine interne Invariante erzeugt `INTERNAL_FAILURE` und FailureId; die Anwenderdiagnose bleibt von technischem Stacktrace getrennt. Der technische Bericht kann auf ausdrücklichem Diagnoselevel den Stacktrace enthalten.

### 3.2 CommandLineParser.parse

Der Parser mutiert weder Argumentliste noch Umgebung. Er prüft unbekannte Optionen, fehlende Werte, gegenseitige Ausschlüsse, Mehrfachvorkommen und Positionsargumente. Reihenfolge und Wiederholungsverhalten stammen aus CommandSpec. Das Auftreten von `--` wird entsprechend dem Profil behandelt. Pfadstrings bleiben zunächst Strings; ihre Auflösung erfolgt in der Invocationplanung. Shell-Quoting wurde bereits durch die Shell verarbeitet und wird nicht erneut geraten.

### 3.3 ConfigurationLoader.load

Der neue explizite Standard ist CLI-Argument vor expliziter Konfigurationsdatei vor Profildefault. Environmentvariablen sind nur wirksam, wenn sie dokumentiert sind; zufällig gesetzte globale Proxy-/Localevariablen dürfen die Compilersemantik nicht ändern. Jede aufgelöste Einstellung besitzt eine Herkunft, damit ein Report erklären kann, warum sie aktiv war. Geheimnisse werden als vertrauliche Werte behandelt und nicht in Klartext serialisiert.

### 3.4 OutputPlanner.plan

Der Planner trennt Fachartefakt, fachliche Diagnostik und Betriebsereignisse. Er erkennt kollidierende Dateipfade und unvereinbare stdout-Nutzung vor dem Schreiben. Ein Fehler darf nicht ein bereits vorhandenes Nutzerartefakt mit einer leeren Datei ersetzen. Soweit das Ausgabeformat atomar erzeugbar ist, wird zunächst eine temporäre Datei im Zielverzeichnis geschrieben und anschliessend ersetzt; Ausnahmen für Streaming werden im konkreten Ausgabevertrag dokumentiert.

## 4. Standardaufruf und Lieferung

Unterbefehle: check, compile, analyze, diagram, format, help, version, capabilities. Ohne Rootauswahl sind explizite Dateien Roots; mit --root sind zusätzliche Dateien verfügbare Abhängigkeiten. Es gibt keine implizite Nachbarschaftssuche; lokale Importdateien werden ausdrücklich angegeben oder über ein indexiertes Repository aufgelöst. -- beendet Optionen, die Shellargumente werden nicht erneut gequotet.

Die Installation liegt unter interlis-compiler-cli/build/install/interlis-compiler, das SDK enthält cli/bin/interlis-compiler und die Windowsvariante .bat. Das CLI-JAR heisst interlis-compiler-cli-<version>.jar, die Compilerbibliothek interlis-compiler-<version>.jar. Keine Testhilfen oder Referenzcompiler werden mitgeliefert.

Standard-Exits: 0 Erfolg, 1 fachliche/operative Ablehnung, 2 Bedien-/Konfigurationsfehler, 3 interner Fehler, 4 Abbruch. check/compile halten stdout leer, Diagnosen gehen auf stderr; --json liefert genau ein JSON-Dokument auf stdout. format/diagram behalten ihre Fachausgaben.

## 5. Neue strukturierte Optionen

Strukturierte Ausgabe ist eine Erweiterung, deren Namen im CLI-Schema festzulegen sind. Vorgeschlagen sind getrennte Optionen für Diagnoseformat (`text`, `json`) und Betriebslogformat (`text`, `json`). Ihre exakten Namen dürfen nicht mit bestehenden Optionen kollidieren und müssen in Hilfe/Tests erscheinen.

JSON-Diagnostik und JSON-Logs besitzen jeweils ein dokumentiertes Schema. Falls beide denselben Stream verwenden sollen, müssen alle Zeilen einen eindeutigen Ereignistyp tragen und dieselbe NDJSON-Rahmung verwenden. Andernfalls verlangt der OutputPlan getrennte Senken. Formatierte Quellausgaben dürfen nicht mit Laufereignissen vermischt werden. Bei LSP bleiben die Regeln von Kapitel 07 massgeblich; CLI-Logging darf dessen stdout-Protokoll nicht verschmutzen.

Textdarstellung verwendet kanonische Diagnosedaten. Diagnosecode und fachliche Ursache werden nicht aus einem Textvergleich abgeleitet. Zusätzliche Spalten-/Bereichsinformationen können strukturiert verfügbar sein, selbst wenn die Legacydarstellung nur Datei und Zeile ausgibt.

## 6. Java-SDK

Der kleinste Bibliotheksfall benötigt `interlis-compiler` und dessen model-Abhängigkeit: SourceText/SourceSet aus Strings aufbauen, CompilationRequest erstellen, `CompilerEngine.analyze` oder `compile` aufrufen und Resultat abfragen. Ein Workspace ist nur für Sitzungszustand/Wiederverwendung erforderlich. Repository- und LSP-Abhängigkeiten sind optional.

Die endgültigen Signaturen stehen in Kapitel 06/16. Beispielabläufe müssen im Repository als tatsächlich kompilierende Tests oder kleine Anwendungen vorliegen. Ein README-Snippet, das nicht mit den veröffentlichten Artefakten kompiliert, ist ein SDK-Fehler.

SDK-Versionierung unterscheidet Binär-/Quellkompatibilität der Java-API, semantische Verhaltensänderungen, Diagnosekatalog und Serialisierungs-/Bridgeversionen. Interne Packages `*.internal` sind nicht Teil des SDK-Vertrags. Selbst bei unveränderter Methodensignatur ist eine geänderte Attributreihenfolge oder Fehlersemantik eine relevante Verhaltensänderung. Vor stabiler Version 1.0 können API-Änderungen häufiger sein, benötigen aber Migrationshinweis und aktualisierte Beispiele.

## 6.1 Quellformatierung

Die Formatierung ist kein Modellgenerator und kein Ersatz für die semantische Prüfung. Sie arbeitet auf einer bereits gelesenen `SourceText`-Revision und verändert ausschliesslich die Darstellung, nicht die fachliche Bedeutung. Die öffentliche API liegt im Compiler-Modul unter `guru.interlis.format` und führt keine IO aus:

```java
public interface ModelFormatter {
    FormatOutcome format(SourceText source, FormatOptions options,
                         CancellationToken cancellation);
}
```

`FormattingNotice` ist ein unveränderlicher Hinweis mit Code, sicherem Text und
optionalem `SourceRange`; er ist kein Sprachfehler. `FormatOutcome` unterscheidet
mindestens `Formatted(String text, List<FormattingNotice>)`,
`Rejected(List<Diagnostic>)` und `Cancelled`. `FormatOptions` enthält mindestens
Einrückung, Zeilenende und die Regel für den finalen Zeilenumbruch. Dateisystemzugriffe
und das Schreiben von Dateien bleiben Hostaufgabe.

Die Formatierung MUSS:

- Kommentare, Zeichenketten, Unicode und die fachliche Tokenfolge erhalten;
- deterministisch und idempotent sein;
- syntaktisch fehlerhafte Quellen ohne Teilprodukt ablehnen;
- die formatierte Quelle wieder parsebar machen;
- keine zweite Compilersemantik ausführen.

Die CLI bietet:

```text
interlis-compiler format [--check] [--write] [--indent N] [--line-ending lf|crlf] <files...>
```

Ohne Ausgabemodus geht das Ergebnis nach stdout. `--write` schreibt atomar in die
Quelldatei; `--check` schreibt nicht und signalisiert Änderungsbedarf über den
Exitcode. Bei Syntaxfehlern entsteht keine Zieldatei. Der Befehl verwendet dieselbe
Formatter-API wie das SDK.

## 7. Zurückgestellter Legacy-Adapter

Ein Adapter für die alte mutierbare Java-API (`TransferDescription` und verwandte
Alttypen) ist **nicht Bestandteil von P3**. Die frühere Anforderung bleibt als
zurückgestellt dokumentiert; eine spätere Wiederaufnahme benötigt einen eigenen
Auftrag, echte Verbrauchertests und eine isolierte Abhängigkeitsschicht. Der Kern
bleibt ohne diesen Adapter nutzbar.

## 8. Anforderungen

| ID | Normative Anforderung | Phase | Abnahme |
|---|---|---|---|
| CLI-001 | CLI und SDK MÜSSEN dieselbe CompilerEngine verwenden. | P1 | Fachlich identische normalisierte Resultate. |
| CLI-002 | Nur der Prozesseinstieg DARF System.exit verwenden; die Anwendung MUSS als Bibliothek testbar sein. | P1 | In-Process-Tests fangen Resultate und Streams ohne JVM-Ende auf. |
| CLI-003 | Die Standardkommandos und Optionen MÜSSEN explizit geparst werden; alte/unbekannte Aufrufe werden vor Host-IO abgelehnt. | P3 | Standardprozessmatrix; Exit 2 ohne Compilerstart. |
| CLI-004 | Exitcodes, Streams und Meldungen MÜSSEN zum freigegebenen Profil passen. | P3 | Prozessvergleich auf zugesagten Plattformen. |
| CLI-005 | Konfigurationspräzedenz MUSS eindeutig und erklärbar sein. | P1/P3 | Konfliktfälle und Herkunftsreport. |
| CLI-006 | Fachartefakte, Diagnostik und Betriebslogs MÜSSEN eindeutig getrennt bzw. gerahmt sein. | P1 | Formatierte Quellausgaben und Diagnostik bleiben trennbar; NDJSON ohne Fremdtext. |
| CLI-007 | Ein fehlgeschlagener Lauf DARF kein unvollständiges Artefakt als Erfolg ausweisen. | P1/P3 | IO-Fehler/Abbruch mit vorhandener Zieldatei. |
| SDK-001 | Der SDK-Grundfall MUSS rein aus Strings und expliziten Optionen ausführbar sein. | P1 | Kompilierender Beispiel-/Vertragstest ohne Dateisystem. |
| SDK-002 | Öffentliche SDK-Beispiele MÜSSEN mit den ausgelieferten Artefakten getestet werden. | P1/P3 | Beispielkompilierung in CI. |
| SDK-003 | API-, Diagnose- und Protokollversionen MÜSSEN getrennt nachvollziehbar sein. | P3 | Versionsmanifest/Migrationsfall. |
| SDK-004 | Modellgeneratoren sind nicht Bestandteil von P3; die Anforderung ist durch die Formatierungsanforderungen FMT-001..004 ersetzt. | entfällt (P3-Scope-Entscheidung) | Keine Generator-API; Entscheidung und Ersatz sind dokumentiert. |
| FMT-001 | Die Formatierung MUSS als deterministische, idempotente öffentliche API ohne IO verfügbar sein. | P3 | API-Vertrag, Wiederholungslauf und Unveränderlichkeit der fachlichen Tokenfolge. |
| FMT-002 | Die Formatierung MUSS Kommentare, Zeichenketten, Unicode und die fachliche Bedeutung erhalten und fehlerhafte Quellen ohne Teilprodukt ablehnen. | P3 | Positive/negative Formatfixtures, Re-Parse und semantischer Vergleich. |
| FMT-003 | Die CLI MUSS `format` mit stdout, `--write`, `--check` und dokumentierten Exitcodes anbieten. | P3 | Prozessfixtures für Ausgabe, In-Place-Schreiben und Check-Modus. |
| FMT-004 | In-Place-Formatierung MUSS atomar sein und bei Fehlern die vorhandene Datei unverändert lassen. | P3 | IO-Fehler-/Abbruchtest mit vorhandener Zieldatei. |
| LEGACY-001 | Ein Legacy-Java-API-Adapter ist derzeit nicht Bestandteil von P3. | zurückgestellt (nicht P3) | Eigener Auftrag mit echten Verbrauchertests erforderlich. |
| LEGACY-002 | Ein alter mutabler Objektgraph DARF das neue Schema nicht verändern. | zurückgestellt (nicht P3) | Gilt erst bei Wiederaufnahme des Adapterauftrags. |

## 9. Erweiterbarkeit für weitere Einbettungen

Ein späteres Java-Native-Image, Node-Addon, C-ABI oder Python-Binding kann dieselben expliziten Requests und Resultatprojektionen nutzen. Das ist keine P1-Lieferpflicht. Binäre Foreign-Function-Handles brauchen eine eigene Lebensdauer-/Fehler-/Threading-Spezifikation und dürfen die vorhandenen Snapshotverträge nicht umgehen. Der vorhandene JSON-/WASM-Vertrag ist eine mögliche fachliche Grundlage, aber keine automatische ABI-Kompatibilität zu C++-Verbrauchern.

## L3-2: explizite Repositoryoptionen des neuen Standardprofils

`check`, `compile`, `analyze` und `diagram` akzeptieren zusätzlich:

- `--repository DIR_OR_URI` mehrfach, in Prioritätsreihenfolge; Index jeweils
  `ilimodels.xml`. Lokale Verzeichnisse werden gegen das CliEnvironment aufgelöst.
- `--root MODEL` auch ohne explizite Quelldatei: der Host beschafft den benannten Root.
- `--model-pin NAME VERSION SOURCE SHA256` und `--source-pin SOURCE SHA256`;
  doppelte Schlüssel oder ungültige Digests sind Nutzungsfehler.
- `--read-lock FILE`, `--write-lock FILE`: striktes XML-v1 aus `ResolutionLockCodec`.
  Ein neuer Lock wird atomar geschrieben: bei `check`/`compile` nach erfolgreicher
  Kompilierung, bei `diagram` nach erfolgreicher Projektion, bei `analyze` nach
  abgeschlossener Analyse der erfolgreich aufgelösten Closure, auch mit fachlichen
  Diagnosen. Ein Lock bescheinigt die Quellenauswahl, keine semantische Gültigkeit.
  Quelle-/Index-/Modellkollisionen werden ohne Überschreiben abgewiesen.
- `--cache DIR`: expliziter persistenter digestverifizierter ContentStore.
  Ohne Option bleibt der Cache pro Aufruf im Speicher.
- `--offline`: fehlende Repositorymaterialien nur aus verifiziertem Cache mit
  Pin/Lock; vollständige Workspace-Closures brauchen keinen Provider.

Repositoryoptionen setzen `--repository` voraus; `format`, `help`, `version` und
`capabilities` lehnen sie ab. Es gibt keine impliziten Defaultrepositories.
Die bestehenden Optionen und bisherige Quelldateiaufrufe bleiben erhalten. Dies
sind neue Standardoptionen, keine Behauptung bereits qualifizierter ili2c-
Legacyalias-Kompatibilität. Die CLI delegiert Beschaffung vollständig an
`RepositoryClosureService` und ruft anschliessend dieselbe Workspace-Engine auf.

Fehlende/mehrdeutige/falsch gepinnte Auswahl ergibt INPUT_UNRESOLVED;
Index-/Ladefehler ergeben OPERATIONAL_FAILURE mit separat sichtbarer Ursache,
Abbruch CANCELLED und veraltete Eingaben INCOMPLETE_ANALYSIS. Bei `check`,
`compile` und `diagram` werden Sprachfehler und Compilerergebnisse weiterhin allein
über CompilationOutcome abgebildet; `analyze` liefert seine Analysediagnostik.
`--json` enthält bei Hostablehnung den konkreten typisierten Grund im Status;
Diagnosen werden nicht durch erfundene Quellpositionen ersetzt. Fehler beim
Locklesen/-schreiben erzeugen keinen erfolgreichen Gesamtstatus.

`RepositoryCliContractTest` führt den vollständigen Online-/Lock-/Cache-/Offline-
Ablauf über getrennte CLI-Anwendungsinstanzen aus, entfernt die Originaldateien,
prüft Cachekorruption, Ambiguität, Pins, ungültige Optionen und Ausgabekollisionen.
Die programmatischen SDK-Verträge einschliesslich Closure-/Modellassertions
stehen in den Repository- und Workspace-Vertragstests aus Kapitel 08 §11.

## P3-1: explizite Kommando- und Hostgrenzen

CLI-003/005/007 und LOG-002/003/005: `CommandSpec` besitzt den Kommandonamen,
eine geordnete immutable Optionsliste und die Positionsargumentregel.
`Option(name,valueCount,repeatable)` beschreibt Flags beziehungsweise feste
Wertgruppen. `DefaultCommandLineParser.parse(arguments,specification)` ist rein,
prüft unbekannte/doppelte Optionen, fehlende Werte und `--` und liefert
`ParsedCommand`, `HelpRequested`, `VersionRequested` oder `InvalidCommandLine`.
`ParsedCommand` hält die Optionsvorkommen tief kopiert und in Eingabereihenfolge;
Dateiargumente werden nicht erneut shell-gequotet oder geladen. `help` wird aus
diesem selben Schema erzeugt.
Die für den erklärenden Bedienfehler erkannte Option `diagram --json` wird
in der Hilfe ausgelassen; diese empfiehlt ausdrücklich `diagram --format json`.
Der Fehlaufruf behält Exit2 und den Hinweis auf das unterstützte Ausgabeformat.

Für das neue Standardprofil von `check`/`compile`/`analyze`/`diagram` lädt `ConfigurationLoader`
ausschliesslich `--config FILE`: UTF-8-Java-Properties, höchstens 1 MiB, bekannte
Schlüssel `root`, `repository`, `cache`, `read-lock`, `write-lock`, `offline`,
`warnings`. Doppelte/ unbekannte Schlüssel und leere ungültige Werte sind
Nutzungsfehler. Pro Dateischlüssel gibt es einen Wert; wiederholte CLI-Optionen
bleiben entsprechend ihrem Schema möglich. CLI ersetzt den ganzen Dateiwert
vor Profildefault. Relative Pfade beziehen sich auf das injizierte
Arbeitsverzeichnis. Es gibt keine impliziten Home-Dateien oder Environmentwerte.
`warnings=allow|reject` beziehungsweise `--warnings allow|reject` werden an die
bestehende Compiler-WarningPolicy übergeben. `offline=true|false` ist exakt.
Java-Properties-Escapes gelten auch für Windows-Pfade. `--explain-config` liefert
statt einer Kompilierung deterministisches JSON mit wirksamen Werten und Herkunft
CLI/CONFIGURATION_FILE/PROFILE_DEFAULT; URI-Zugangsdaten und Queries werden
redigiert. Das ist eine neue Standardoption, keine Behauptung einer alten
ili2c-Konfigurationsoption.

`CliEnvironment` erhält additiv `CompilerServices services`; der bisherige
Fünfparameterkonstruktor erzeugt isolierte Defaults. Der CLI-Elternlauf wird mit
diesen Diensten gestartet; Compilerarbeit erhält einen frischen Kindkontext.
`CliExecutionResult` erhält `Optional<RunId> runId`; der bisherige
Vierparameterkonstruktor bleibt für noch nicht zugeordnete Resultate erhalten.
Der Elternabschluss folgt erst nach Ausgabe und Flush. Ein Ausgabefehler kann
daher einen erfolgreichen Compilerkindlauf mit OPERATIONAL_FAILURE abschliessen.
Kein aktueller Lauf wird auf dem wiederverwendbaren CLI-Objekt gespeichert.
`CliEnvironment.system()` kodiert stdout/stderr ausdrücklich als UTF-8, auch
bei abweichender nativer Windows-Konsole. Die bestehende globale Streaminstanz
wird weder ersetzt noch geschlossen. Injizierte Streams bleiben beim Aufrufer.
Externe Referenzprozesse verwenden dokumentiert UTF-8-JVM-Streamoptionen;
der Vergleich behauptet keine Gleichheit nativer Konsolencodepages.

FMT-001–004: Explizite LF-/CRLF-Wahl normalisiert auch gewöhnliche mehrzeilige
Kommentare. Zeilenenden **innerhalb werttragender Literale oder mehrzeiliger
Metaattributwerte** bleiben dagegen erhalten: ihre Änderung würde den
freigegebenen Metadatenvertrag und die fachliche Bedeutung ändern. In diesem
Fall meldet `Formatted.notices` den Hinweis `VALUE_LINE_ENDING_PRESERVED` mit
Originalbereich; die CLI schreibt den Hinweis getrennt nach stderr. PRESERVE
bewahrt alle ursprünglichen Zeilenenden. Diese Präzisierung ändert keinen
String-/Metadatenwert und erlaubt keine stille semantische Formattransformation.
Die entsprechenden Kommentar-Tokenvergleiche normalisieren ausschliesslich
Kommentarzeilenenden bei expliziter Wahl; Literalwerte werden separat exakt
geprüft. Die Formate bleiben idempotent und wieder parsebar.

Der gemeinsame atomare Hostschreiber prüft Abbruch nach Staging und unmittelbar
vor Ersetzung. Formatierungen vergleichen ausserdem die gelesenen Originalbytes;
ein beobachteter externer Edit bleibt erhalten. Das ist ein optimistisches
Lesefenster, kein Dateisystem-CAS. Es gibt keinen nichtatomaren Move-Fallback.
Bei Fehlern werden temporäre Dateien entfernt; bestehende POSIX-Rechte bleiben
bei erfolgreicher Ersetzung erhalten. Symbolische Ausgabeziele werden abgelehnt.
Mehrfach genannte identische Formatquellen sind Nutzungsfehler. Atomarität gilt
je Datei, nicht als Dateisystemtransaktion über mehrere Dateien.

`OutputPlanner.plan(ParsedCommand,CliEnvironment)`
liefert einen immutable `OutputPlan(fileTargets,writesStdout)` und prüft
Ausgabemodus, doppelte Formatquellen und offensichtliche Quell-/Lockkollisionen
vor dem Schreiben. Pfade sind absolute normalisierte Hostpfade in Eingabereihenfolge.
Nach Repositoryauflösung werden Kollisionen erneut gegen die tatsächliche
Closure geprüft. `CliOutputWriter.write(Path,byte[],Optional<byte[]>,CancellationToken)`
führt synchron die oben beschriebene atomare Ersetzung aus; alle Eingaben sind
nullfrei, Arrays bleiben beim Aufrufer und dürfen während des Aufrufs nicht
verändert werden. IO-Fehler sind `IOException`, Abbruch ist typisiert.
`CliExecutionResult.artifacts` ist die geordnete immutable Liste tatsächlich
fertiggestellter Dateien mit Art FORMATTED_SOURCE/RESOLUTION_LOCK, absoluter URI
und Bytezahl. Bei späterem Batch- oder Flushfehler bleiben zuvor fertiggestellte
Dateien aufgeführt; eine atomare Mehrdateitransaktion wird nicht behauptet.
`rejectionSummary` bewahrt gegebenenfalls die vollständige typisierte
Compiler-Gate-Ablehnung zusätzlich zum priorisierten CLI-Status, auch wenn ein
späterer Hostausgabefehler den endgültigen Status bestimmt.

COMPAT-001–003: Sprach-/Metamodellreferenzen und freigegebene Normprofile bleiben erhalten. Das aktuelle Standardbedienprofil steht in [CLI_PROFILE.md](../p3/CLI_PROFILE.md); die frühere ili2c-Bedienparität ist durch den ausdrücklich beauftragten CLI-Folgeplan aufgehoben. CompilerCliIdentityContractTest und compilerCliCheck sichern den neuen Entry und die migrierten Pfad-/Stream-/Fehlerverträge.

## COLOR1: explizite Farbpolitik der Textdiagnosen

C1-01–08 / CLI-003/004/006: Die fünf Diagnosebefehle akzeptieren zusätzlich
`--color=auto|always|never` sowie `--color MODE`, einmalig und exakt kleingeschrieben.
Ungültige Werte werden vor Host-IO abgewiesen. Nur diese Option erlaubt `=`;
`--` beendet weiterhin alle Optionen. Help/Version/Capabilities bleiben farbfrei.
`ColorPolicy` im CLI-Modul wertet nullfreie immutable Eingaben ohne IO aus:
Maschinenoutput und NEVER bleiben plain, ALWAYS übersteuert Environmentdefaults;
AUTO benötigt sichere stderr-Fähigkeit, leeres/fehlendes NO_COLOR und TERM ungleich
dumb (case-insensitive). Keine Änderung kanonischer Diagnosen oder Exits.

`CliEnvironment` ergänzt `TerminalCapabilities terminalCapabilities` als nullfreien
immutable Recordwert. Die bisherigen fünf/sechs Parameterkonstruktoren bleiben
quellkompatibel und setzen unbekannte stderr-Fähigkeit auf false. Der Host prüft
auf POSIX direkt den geerbten FD2 mit dem festen `/bin/test -t 2`, ohne Shell oder
Modellargumente; Fehler, Timeout, Unterbrechung und andere Hosts ergeben false.
Das Ergebnis bleibt beim Kopieren der Laufumgebung erhalten. Auf Windows ist
AUTO konservativ farbfrei; ALWAYS ist unabhängig von Hostdetektion verfügbar.
`AnsiStyler` erzeugt nur feste SGR-Sequenzen nach bestehender safeText-Sanitierung
und schliesst jeden Bereich mit Reset. Alte Renderer-Overloads bleiben plain;
ein expliziter boolescher Stilparameter ermöglicht die reine Farbprojektion.
Abnahme: ColorOutputContractTest und reale CLI-/PTY-/Redirectprozesse.

## COLOR2: sichere Quellframes

C2-01–11: `ConsoleDiagnosticRenderer.render(diagnostic,sources,language,RenderOptions)`
erweitert die bestehende reine Darstellung. Die bisherigen Overloads bleiben
verfügbar und standardmässig ohne ANSI. `RenderOptions(ansiEnabled,
contextLinesBefore,contextLinesAfter,maxVisibleColumns,verbose)` ist immutable und
nullfrei; vor/nach müssen nichtnegativ sein und zusammen höchstens2 ergeben,
Breite24..160 einschliesslich Framegutter. Defaults1/1/160. Ungültige Grenzen
werfen IllegalArgumentException vor Rendering. Keine impliziten Hostabfragen.

Der interne `DiagnosticSnippetRenderer` liest ausschliesslich übergebene
SourceTexte; nur gleiche ID/Revision und gültige UTF-16-Grenzen ergeben Frames.
Die Verarbeitung ist linear, der zusätzliche Speicher und die Ausgabe je Frame
sind begrenzt. Höchstens3 Quellzeilen, lange Zeilen zeigen ein Fenster um die
Spanne mit ASCII-Ellipsen. Tabs werden deterministisch auf Viererspalten expandiert;
Kontrollen/Bidi werden vor Styling durch safeText druckbar escaped, einzelne
Surrogate ebenfalls. Printable Unicode bleibt sichtbar. Wo seine Displaybreite
vor/in der Spanne unsicher ist, wird kein exakter Caret behauptet, sondern
`[UTF-16 ...; marker omitted]` angezeigt. ASCII-/escaped-Spannen verwenden ^/~;
leere Bereiche einen Caret, mehrzeilige Bereiche nur den ersten Abschnitt und
einen begrenzten Fortsetzungshinweis. Headerpositionen bleiben immer UTF-16.
Severity, Orte, Codes, Zeilennummern, Marker und Hintlabel verwenden die feste
SGR-Palette mit Reset je Bereich. Prosa, Diagnosereihenfolge, RelatedLocations,
Hints und JSONdaten bleiben fachlich identisch. JSON erhält nie RenderOptions.
Hints und verwandte Stellen werden in der Textausgabe nur mit `--verbose` angezeigt.
Abnahme: DiagnosticSnippetRendererTest, ColorOutputContractTest, bestehende
Office-/Root-/Sprach-/UTF-8-Verträge und echte Launcher-Gegenproben.


## Repository- und Ausführlichkeitsintegration

CLI-001–007, RES-001/004/007–009/011, DIAG-001–003/006/009 und LOG-002/003/005:
Alle vier semantischen Befehle nutzen dieselbe CLI-interne Vorbereitung mit
Konfigurationspräzedenz, Rootauswahl, RepositoryClosureService und atomarer
Workspaceübernahme. `analyze` bleibt Analyse ohne zusätzliches Compilegate;
`diagram` verlangt unverändert ein erfolgreiches CompiledSchema. Die Diagnose-
Präsentation erhält aktuelle Texte einschliesslich aller nachgeladenen Quellen.
`--warnings reject` konfiguriert dieselbe Compilerpolicy; `analyze` führt kein Gate
zur Warnungsablehnung aus. `--explain-config` führt keine Modellverarbeitung aus.

Die fünf Diagnosebefehle akzeptieren einmalig `--verbose` ohne Wert. Ohne Flag
MUSS die Textausgabe pro Diagnose auf Fundstelle, Schweregrad, Code und Meldung
beschränkt bleiben. Mit Flag werden die bisherigen Quellframes, verwandten
Fundstellen und Hinweise ergänzt. Farbe, Sprache, Reihenfolge, strukturierte
Diagnosen, Exitcodes und Fachartefakte bleiben unabhängig davon. JSON bleibt
vollständig; das Flag aktiviert keine weiteren Betriebslogs und keinen neuen
Properties-Schlüssel.

`RenderOptions.verbose()` steuert nur diese reine Projektion. Der bisherige
Vierparameterkonstruktor, `defaults(boolean)` und Renderer-Komfortoverloads
behalten die ausführliche Darstellung und bestehende Nullfreiheits-/Grenzregeln.
Das CLI übergibt ausdrücklich `verbose=false`, solange das Flag fehlt.
Abnahmen: `SemanticRepositoryCommandsTest`, `VerboseDiagnosticsContractTest`,
die bestehenden Repository-/Diagnose-/Farbverträge sowie `compilerCliCheck`,
`sdkProcessCheck` und `sdkArchiveCheck` mit getrennten Streams.
