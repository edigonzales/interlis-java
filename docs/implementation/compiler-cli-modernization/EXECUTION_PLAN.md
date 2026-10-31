# INTERLIS Compiler CLI – Umsetzungsplan und verbindliche Spezifikation

**Stand:** 31. Oktober 2026 · **Auftrag:** CLI-Bereinigung, Umbenennung und verständliche, auftragsbezogene Diagnostik · **Implementierung:** Codex · **unabhängige Abnahme:** OpenCode 2 mit DeepSeek-V4.1-Flash-1017 über die direkte DeepSeek API.

Dieser Folgeauftrag wird im bestehenden Repository `edigonzales/interlis-java` umgesetzt. Er ist **kein Neustart von G2/P3** und kein Auftrag für Transfer, Validator oder Datenbankwerkzeuge. Der Nutzer hat ausdrücklich beschlossen: Das Modul `interlis-cli` wird `interlis-compiler-cli`, das ausführbare Programm wird `interlis-compiler`. Die ili2c-Bedienkompatibilität entfällt. **Es werden weder alte Launcher noch Modulaliase, Fallbacks, Übergangsprofile oder Deprecation-Schichten erhalten.** Es wurde noch nichts veröffentlicht; eine Kompatibilität zu `interlis-cli` ist nicht erforderlich.

## 1. Verbindliche Entscheidungen und Grenzen

### 1.1 Ziel

Am Ende gibt es ein eigenständiges, eindeutig bedienbares Compiler-CLI. Es verwendet dieselbe Compilerengine wie das SDK, ermittelt seine Diagnosen aus dem tatsächlichen Prüfauftrag und erklärt Fehler verständlich auf Deutsch oder Englisch. Text- und JSON-Ausgabe haben getrennte, überprüfbare Verträge. Die ausgelieferte Anwendung enthält keinen ili2c-Bedienmodus und keine Kopie des Referenzcompilers.

Die verbindlichen Ziele sind:

1. **Ein Modul, ein Programmeinstieg, ein Bedienvertrag:** `interlis-compiler-cli` / `interlis-compiler`.
2. **Keine ili2c-CLI-Emulation:** alte Optionsprofile, `%ILI_DIR`-/`%JAR_DIR`-Legacyplanung, automatische Profilerkennung und zusätzliche ili2c-Auslieferungsartefakte entfernen.
3. **Prüfauftrag statt Verzeichnisrauschen:** Nur angeforderte Rootmodelle und ihre erforderlichen Abhängigkeiten bestimmen den Compile-Ausgang und dessen gewöhnliche Diagnoseausgabe. Nicht ausgewählte Modelle dürfen weiterhin im Analyse-/Workspacebestand existieren.
4. **Verständliche Fehlermeldungen:** Codes behalten, technische Nachrichtenschlüssel nicht als Benutzertexte ausgeben, passende Parameter und verwandte Quellstellen anzeigen.
5. **Bewährte Qualität erhalten:** gemeinsame externe Java-Conformance-Suite, normative Original-/Kontrollfälle, semantische Vergleichstests, echte CLI-Prozesse, SDK-/LSP-/Browserverträge und Pythonfreiheit bleiben bestehen.

### 1.2 Was ausdrücklich nicht beauftragt ist

Keine Änderungen an der INTERLIS-Sprachakzeptanz, keine Freigabe neuer Normausnahmen, keine neuen XFAILs, kein automatisches Ändern von Kundenmodellen. Kein universelles CLI-/Pluginframework, kein neuer Conformance-Runner, kein Validator, kein DB-CLI, kein JAR-API-Legacyadapter, kein Native-Image-Projekt und keine allgemeine Aufteilung des gesamten Compilerkerns. Keine pauschale Umbenennung des Repositories, der Compilerbibliothek oder sämtlicher Java-Packages.

Das Java-Package `guru.interlis.cli` kann für diesen Auftrag bestehen bleiben. Die vom Nutzer beschlossene Umbenennung betrifft **Gradle-Modul, CLI-Artefakt, Distribution und Programmnamen**, nicht zwingend jedes Java-Package. Das ist keine Kompatibilitätszusage, sondern vermeidet ein zweites, fachlich unabhängiges Refactoring. Künftige Fachwerkzeuge erhalten eigene CLI-Module und verwenden Bibliotheken, niemals dieses Compiler-CLI als fachliche Abhängigkeit. Noch keine leeren Zukunftsmodule anlegen.

Ein brauchbares Modellauflösungs-, Logging- oder Fehlerverhalten darf nicht deshalb verschwinden, weil es bisher auch durch Legacytests geprüft wurde. Solche Tests werden auf den neuen Programmeinstieg übertragen. Nur tatsächlich aufgegebene Bedienverträge entfallen.

### 1.3 Rangfolge und Spezifikationspflege

Dieser ausdrücklich beauftragte Folgeplan ersetzt die bisherigen **aktiven Verpflichtungen zur ili2c-Kommandozeilenkompatibilität** und die alten CLI-/Modulnamen. Alle übrigen Architektur-, Sprach-, Diagnose-, Ressourcen-, API- und Plattformverträge gelten weiter. `AGENTS.md` und bei Spezifikationsänderungen `docs/spec/AGENTS.md` lesen. Widersprechende aktive Stellen in `docs/spec/03`, `09`, `13`, `14`, `16`, `18`, Root-Anleitungen und P3-Profilbeschreibungen konsistent nachführen.

Historische Implementierungs- und Reviewberichte bleiben unverändert. Ihre früheren PASS gelten für die dort genannten Commits und Profile. Die neue Abnahme ist ein **Folge-PASS für den ausdrücklich geänderten Umfang**, kein rückwirkendes Umschreiben von G2/G3. Frühere Fundstellen und Quellenhashes nicht durch globale Textersetzungen beschädigen.

## 2. Ausgangslage und nachgewiesene Beobachtungen

Analysierte Referenzrevision: `a996ef0a5e915eb76c3e96a98b8f445f7005e851` auf `main`. Der örtliche Arbeitszweig kann neuer sein. Vor Beginn `git status`, tatsächlichen HEAD und Abweichungen feststellen; nie auf diesen Referenzstand zurücksetzen. Die Quellen am Ende dieses Dokuments belegen die Ausgangslage, nicht die Durchführung dieses neuen Auftrags.

Am untersuchten Stand:

- `Main` wählt bei bekannten Unterbefehlen den Standardweg, sonst `LegacyCliProfile.ili2c569()`.
- `DefaultCompilerCli` besitzt Legacyzweige für Aufrufplanung, Hilfe, Version, Ausgabe und Exitcodes.
- `Ili2cInvocationPlanner` inventarisiert über die alten Suchpfade weitere lokale `.ili`-Dateien.
- `DefaultCompilerCli.diagnostics(...)` gibt bei Erfolg und Ablehnung die vollständige `AnalysisSnapshot.diagnostics()`-Liste zurück. `CompilationGate` filtert seine Entscheidungsdiagnosen dagegen anhand der gewählten Closure.
- `ConsoleDiagnosticRenderer` druckt `messageKey` und Argumente; `LegacyDiagnosticRenderer` setzt nochmals einen Schweregradpräfix davor.
- Der CLI-Build erzeugt zusätzliche `ili2c`-Startskripte und `compat/ili2c.jar`. Gradle-, Prüf- und SDK-Werkzeuge referenzieren den bisherigen Modul-/Distributionspfad.

### 2.1 Reales Regressionsbeispiel des Nutzers

Der alte Aufruf ohne Unterbefehl lieferte neben einem Befund in der angeforderten Datei zahlreiche Meldungen aus anderen Modellen desselben Verzeichnisses. Der explizite Aufruf

```sh
./cli/bin/interlis-cli check ../SO_AGI_DataCatalog_Base_20260529.ili
```

lieferte nur noch:

```text
...SO_AGI_DataCatalog_Base_20260529.ili:279:5: ERROR ILI-SEM-0010 diagnostic.semantics.inheritance (element=Office, rule=local type names must not shadow an enclosing type name/SCOPE_OUTER_TYPE_NAME)
```

Das Original liegt mit diesem Paket unter:

`testdata/cli-modernization/original/SO_AGI_DataCatalog_Base_20260529.ili`

SHA-256: `fbb1bccc0e84795284f9fb1a3c0be9e6db516c3c4b8ae43ad2f856cf70948431`.

Es enthält `TOPIC Office` und darin `CLASS Office`. Das ist ein **real beobachteter Ablehnungs- und Darstellungsfall**. Die Datei bytegenau erhalten. Nicht das Topic im Original umbenennen, um einen grünen Test zu erhalten. Für einen positiven Vergleich eine gesondert benannte Kontrollkopie oder ein minimales Modell erstellen; Erwartung und Bezug ausdrücklich dokumentieren.

Dieser Auftrag bestätigt **keine neue Norminterpretation**. Die bisherige Sprachentscheidung bleibt unverändert. Ein konkreter Normabschnitt oder ein Änderungshinweis darf im Renderer nur behauptet werden, wenn die zugehörige Regel tatsächlich belegt ist. Stellt der Agent einen fachlichen Widerspruch fest, separat reproduzieren und als Normfrage melden, nicht im CLI-Refactoring die Sprachregel ändern.

Der einzelne erfolgreiche Wechsel zu `check` ist **kein Beweis allgemeiner Closure-Isolation**: Wenn mehrere Quellen explizit geladen und nur einzelne Roots gewählt werden, muss die Abgrenzung weiterhin getestet werden.

## 3. Zielnamen, Artefakte und CLI-Vertrag

### 3.1 Verbindliche Namensmatrix

| Gegenstand | Ziel |
|---|---|
| Gradle-Projekt und Verzeichnis | `:interlis-compiler-cli`, `interlis-compiler-cli/` |
| Java-CLI-JAR | `interlis-compiler-cli-<version>.jar` |
| Unix-Programm | `interlis-compiler` |
| Windows-Programm | `interlis-compiler.bat` |
| Prozesseinstieg | bestehendes `guru.interlis.cli.Main`, nur Standardanwendung |
| Installierte Moduldistribution | `interlis-compiler-cli/build/install/interlis-compiler/` |
| Startprogramm darin | `bin/interlis-compiler` bzw. `bin/interlis-compiler.bat` |
| CLI im bestehenden SDK-ZIP | `cli/bin/interlis-compiler` bzw. `.bat` |
| SDK-ZIP | weiterhin `interlis-java-sdk-<version>.zip` |
| Compilerbibliothek | weiterhin `:interlis-compiler` und ihr bisheriges JAR |
| CLI-Toolidentität in Hilfe/Version/Capabilities | `interlis-compiler` |
| Stack-/API-/Sprach-/Diagnoseversionen | vorhandene Versionen; nur begründete API-/Diagnoseänderungen separat versionieren |

`applicationName` **und** Distributionsbasis explizit setzen und das tatsächliche Ergebnis testen. Nicht annehmen, dass eine Verzeichnisumbenennung den Programmnamen automatisch richtig setzt. Keine Kollision zwischen Compilerbibliotheks-JAR und CLI-JAR erzeugen.

**Nicht ausliefern:** `interlis-cli`, `interlis-cli.bat`, `ili2c`, `ili2c.bat`, `compat/ili2c.jar`, ein alter Gradle-Projektalias oder ein Laufzeit-Kompatibilitätsmodul. Auch nicht als versteckte Symlinks, Wrapper oder zusätzliche Main-Class anbieten. Alte lokale Buildreste sind keine neuen Lieferartefakte; die Abnahme erfolgt aus sauberem Checkout und frisch erzeugtem Archiv.

### 3.2 Befehle und Eingabe

Die vorhandenen Standardkommandos `check`, `compile`, `analyze`, `diagram`, `format`, `help`, `version`, `capabilities` bleiben. `compile` bleibt im bisherigen Umfang Alias des Compile-Prüfpfads; keine Binärcodegenerierung erfinden.

```sh
interlis-compiler check Modell.ili
interlis-compiler check --root Root Root.ili Abhaengigkeit.ili
interlis-compiler check --repository https://models.interlis.ch/ --root Modellname
interlis-compiler check --diagnostic-language de Modell.ili
interlis-compiler check --json Modell.ili
interlis-compiler format --check Modell.ili
interlis-compiler diagram --root Root --format mermaid Root.ili Abhaengigkeit.ili
```

Die Beispiele mit fachlichen Namen sind Aufrufverträge, keine Behauptung, dass die genannten Modelle im Repositoryserver existieren.

- Ein fehlender Unterbefehl ist `USAGE_ERROR`, Exit 2, mit einem konkreten Hinweis auf `interlis-compiler check <Datei.ili>`. Kein impliziter Wechsel und keine automatische Annahme eines Modellnamens als Kommando.
- Ein unbekannter Unterbefehl ist ebenfalls Exit 2. Alte Optionen wie `-o0`, `--no-auto` oder `--modeldir` werden als nicht unterstützte Optionen abgelehnt; kein zweiter vollständiger Katalog alter Optionsnamen nötig.
- `--help` und `-h` dürfen als einfache Schreibweisen für die globale Hilfe, `--version` als Schreibweise für Version angeboten werden. Sie sind **normale Standard-CLI-Funktionen**, kein Legacyprofil. Vor Host-IO verarbeiten. Die vorhandenen Unterbefehle `help`/`version` bleiben kanonisch.
- Shell-Quoting nicht erneut interpretieren. `--` als Optionsende, Leerzeichen, Umlaute, Emoji, relative/absolute Pfade und führende Minuszeichen in Dateinamen testen.
- Bestehende Standardoptionen für Roots, Repositories, Pins, Locks, Offline, Cache, Konfiguration, Warnungspolitik und Ausgabe erhalten. Keine impliziten neuen Home-Konfigurationen oder Environmentdefaults einführen.
- Ohne `--root` sind die **explizit angegebenen Quelldateien** Roots. Mit `--root` sind die gewählten Modelle Roots; zusätzliche Dateien sind verfügbare Eingaben/Abhängigkeiten, nicht automatisch zusätzliche Roots.
- Ein gewöhnliches Dateiverzeichnis ist nicht automatisch ein Repository mit `ilimodels.xml`. In diesem Auftrag **keinen neuen allgemeinen Verzeichnis-Resolver bauen**: zusätzliche lokale Importdateien ausdrücklich angeben oder einen vorhandenen indexierten Repositorypfad nutzen. Die alte automatische Nachbarschaftssuche entfällt. Eine spätere eigene `--model-path`-Funktion wäre ein gesonderter Vertrag.

### 3.3 Exitcodes und Streams

Die vorhandene Standard-`ExitCodePolicy` erhalten:

| Exit | Bedeutung |
|---|---|
| 0 | Operation erfolgreich; bei `format --check` bereits korrekt formatiert |
| 1 | fachliche Ablehnung, fehlender Input, abgelehnte Warnungen, unvollständige Unterstützung, Formatcheck-Abweichung oder operativer Fehler entsprechend bestehender Statuszuordnung |
| 2 | Bedien-/Konfigurationsfehler |
| 3 | interner Fehler |
| 4 | kontrollierter Abbruch/Ressourcenabbruch entsprechend bestehendem Vertrag |

Nicht alle Fehler zu Exit 1 vereinheitlichen. Der strukturierte `CliTermination`-Status bleibt aussagekräftiger als die Zahl. Die CLI darf einen Ausgabe-/Flushfehler nicht als erfolgreichen Compilerlauf melden.

Für `check`/`compile`: normale Diagnosen auf stderr, bei Erfolg keine unerwarteten stdout-Inhalte. Bei `--json`: genau die dokumentierte JSON-Ausgabe auf stdout, keine Banner/Logs dazwischen. `format` und `diagram` behalten ihre fachlichen stdout-Ausgaben. Hilfe/Version bleiben getrennte harmlose Aufrufe ohne Modellauflösung. UTF-8 und die vorhandenen Windows-Streamtests erhalten.

## 4. Entfernung der Legacyoberfläche ohne Verlust fachlicher Tests

### 4.1 Konkrete Arbeitsorte

| Datei/Bereich im Ausgangsstand | Auftrag |
|---|---|
| `settings.gradle`, `interlis-cli/`, alle `project(':interlis-cli')`-/Taskpfade | Modul einschliesslich aller Source-Sets verschieben, Verbraucher vollständig nachführen |
| `Main` | immer Standardanwendung; keine heuristische Profilauswahl |
| `Ili2cMain`, `Ili2cInvocationPlanner`, `LegacyCliProfile`, `LegacyExitCodePolicy`, `LegacyDiagnosticRenderer` | nach Aufruferprüfung entfernen |
| `DefaultCompilerCli` | Legacykonstruktor/-feld/-zweige und ausschliesslich dafür gehaltene Invocationzustände entfernen; allgemeine Fähigkeiten nicht verlieren |
| `OutputPlanner`, `ToolVersionRenderer`, übrige CLI-Helfer | Legacyparameter/-abhängigkeiten entfernen oder die nur noch ungenutzte Klasse löschen; keine künstlichen leeren Profile hinterlassen |
| Modul-`build.gradle` | `ili2cStartScripts`, `ili2cJar` und `compat`-Einbindung entfernen; Zielnamen explizit konfigurieren |
| `gradle/conformance.gradle`, `evidence.gradle`, `host-verification.gradle` | neue Projekt-/Distributionspfade; `legacyCliCheck` entfernen, übrige Profile funktionsfähig halten |
| `LegacyCliProcesses` und zugehörige Prüfungen | reine Bedienparität entfernen; allgemeine Fehler-, Unicode-, Pfad-, Stream- und Prozessfälle in Standard-CLI-Tests übernehmen |
| `ReferenceProcesses`, `NormativeConformance`, `SemanticComparison` und deren Tests | erhalten; nur den **neuen Compileraufruf** auf `interlis-compiler check ...` umstellen |
| `SdkProcesses`, `SdkArchiveCheck`, `PlatformProcesses`, `ProfileFingerprint` | neue tatsächliche Artefakte/Startpfade und Standardverträge prüfen |
| `tools/release/`, `.github/workflows/`, Beispiele, Releaseinventare | neue Pfade und explizit geänderten Prüf-/Lieferumfang konsistent abbilden |

`src/verification`, `verificationTest`, `evidenceTest` und reguläre Tests sind Bestandteil der Modulverschiebung. Keine Testquellen versehentlich als Produktionscode ausliefern. Keine separate Migration der gesamten Prüfinfrastruktur in ein neues Modul beginnen.

### 4.2 Verbindliche Testklassifikation

In Phase CLI1 eine **kurze Delta-Tabelle**, keine neue Gesamtinventur:

- `REMOVE_LEGACY_ONLY`: prüft ausschliesslich alte Optionen, alte Banner, alten Launcher oder dessen besonderes Exitprofil.
- `MIGRATE_STANDARD`: prüft einen weiterhin benötigten fachlichen/technischen Fehlerpfad über einen bisherigen Legacyeinstieg.
- `KEEP_REFERENCE`: nutzt den echten ili2c ausschliesslich als unabhängiges Testorakel.
- `RELOCATE_ONLY`: unveränderter Vertrag, neuer Modul-/Artefaktpfad.

Ein Klassenname mit `Legacy` reicht als Löschgrund nicht. Insbesondere OID-, Sprach-, Mapping- oder Referenzmodelle sind nicht automatisch CLI-Altlasten. Die externen Suitepins, Originalquellen, Outcomes, XFAIL-Regeln und Normentscheidungen bleiben unverändert. Keine pauschale Massenlöschung von Tests und kein neues Referenztool in der Produktdistribution.

Historische Quellen-/Testpfade in aktiven Evidenzprüfungen brauchen gegebenenfalls eine **explizite, begrenzte Rename-Zuordnung**. Nicht den ganzen Evidenzcheck abschalten, weil `interlis-cli/...` nicht mehr existiert. Historische Reports werden nicht umetikettiert; neue Berichte nennen tatsächliche neue Quellen, Methoden und SHAs.

## 5. Auftragsbezogene Diagnostik – fachlicher und technischer Vertrag

### 5.1 Zwei unterschiedliche Sichten

`AnalysisSnapshot.diagnostics()` bleibt die vollständige kanonische Analysesicht des erfassten Eingabestands. IDE-/Workspaceverbraucher dürfen relevante Informationen nicht verlieren, nur damit die CLI weniger Meldungen zeigt.

Für `check`/`compile` und den Compile-Schritt von `diagram` braucht es zusätzlich die **Diagnostik des konkret ausgewählten Auftrags**. Sie darf nicht in der CLI mit Dateinamen-/Pfadfiltern rekonstruiert werden. Die gemeinsame Compilerlogik kennt bereits Roots, Imports, Translation-/Overlayabhängigkeiten und Gatezuordnung.

**Nicht ausreichend:** nur Diagnosen der Rootdatei anzeigen; nur ERROR anzeigen; nur `RejectionSummary.diagnosticIds()` nutzen; nur SourceIds des erfolgreichen Schemas betrachten. Solche Ansätze verlieren Warnungen, fehlende Imports, Diagnosen ohne Quellposition oder die Sicht auf abgelehnte Aufträge.

### 5.2 Vorgesehener kleiner API-Zuschnitt

Bestehende gleichwertige Funktionen bevorzugt verwenden. Wenn im aktuellen Stand weiterhin keine öffentliche auftragsbezogene Diagnosesicht existiert, folgenden begrenzten Zuschnitt implementieren:

```java
// In AnalysisSnapshot, neben der unverändert vollständigen diagnostics()-Liste:
public List<Diagnostic> requestDiagnostics();
```

Die Liste ist immutable, kanonisch geordnet und gehört zu **denselben Roots, Optionen, Quellenrevisionen und Fingerprints** wie dieser Snapshot. Sie enthält dieselben unveränderten Diagnostic-Werte beziehungsweise IDs wie die volle Liste, nur die ausdrücklich zugeordneten Einträge. Nicht als localeabhängige Textliste implementieren.

Intern die bisherige Auswahl aus `CompilationGate.evaluate(...)` in eine kleine gemeinsame Auswahlfunktion auslagern, beispielsweise:

```java
// package-private im Compiler; keine öffentliche Closure-API nötig.
final class RequestDiagnosticSelector {
    List<Diagnostic> select(List<Diagnostic> all, Closure closure, RootSelection roots);
}
```

Aufruf nach dem Sammeln der kanonischen Diagnosen in `CompilationRun`, vor der Snapshot-/Gateveröffentlichung. Der Snapshot erhält den gewählten Teilbestand; `CompilationGate` verwendet **dieselbe Auswahl** für Diagnosegründe. Die übrigen Gateprüfungen für Roots, Coverage, Syntax, Warnungspolitik und Schemaerzeugung bleiben dort. Die CLI trifft keine zweite Gültigkeitsentscheidung.

Die Snapshot-Konstruktion muss die Auswahl explizit erhalten; alle Erzeuger und Vertragstests aktualisieren. Keine Bequemlichkeitskonstruktion, die unbegründet `requestDiagnostics = all` setzt. Notwendige API-Anpassung in Kapitel 06/09/16 und SDK-Migration erklären. Ein alternativer gleichwertiger kleiner Zuschnitt ist zulässig, wenn Codex vor der Umsetzung begründet, wie **ein einziger Selektionsvertrag**, beide Sichten und sämtliche untenstehenden Fälle erhalten bleiben; kein separates Diagnostikframework bauen.

`DefaultCompilerCli` verwendet die auftragsbezogene Sicht für Compile-/Diagrammresultate und für deren JSON. `analyze` behält seine dokumentierte Analysesicht; sie wird nicht durch einen stillen globalen Filter eingeschränkt. `Cancelled`/`Failed` ohne vollständigen Snapshot behalten ihren echten Status und ihre sicheren auftragsbezogenen Ursachen. Abbruch oder interne Fehler dürfen nie verschwinden, nur weil keine PrimaryRange existiert.

### 5.3 Zuordnungsregeln

- Semantische Diagnosen sind **modellbezogen**, nicht bloss dateibezogen: zwei Modelle in derselben Datei können unterschiedliche Rootrelevanz besitzen.
- Tatsächlich importierte und für Übersetzung/Overlays erforderliche Modelle gehören dazu, auch wenn ihr Dateiname nicht ausdrücklich als Root angegeben wurde.
- Relevante Auflösungsfehler, mehrdeutige/missing Roots und auftragsweite Konfigurations-/Overlay-/Infrastrukturfehler ohne Range müssen sichtbar bleiben. Globale Gültigkeitsfehler nicht wegfiltern.
- Warnungen bleiben in der ausgewählten Closure sichtbar; `--warnings reject` wird durch relevante Warnungen, nicht durch Warnungen fremder Modelle ausgelöst.
- Lexer-/Parserfehler in tatsächlich benötigten Quelldateien dürfen nicht unterdrückt werden, nur weil eine fehlerhafte Range keiner sauber gebauten Modelldeklaration mehr zugeordnet werden kann. Die bisherige konservative Dateirelevanz für Syntaxfehler erhalten und testen. Keine umfassende Parser-Recovery-Neuentwicklung in diesem Auftrag.
- Eine überhaupt nicht zur Closure gehörende defekte Datei darf keinen Compilefehler erzeugen, nur weil sie als verfügbare Quelle vorhanden ist. Globale Ressourcen-/Inputkonsistenzgrenzen bleiben wirksam; kein Versprechen, beliebig grosse defekte Inventare kostenlos zu ignorieren.
- `RelatedLocation`-Hinweise gehören zur Primärdiagnose und dürfen auf eine andere Datei zeigen. Eine RelatedLocation macht allein noch kein fremdes Modell zum Root.
- Die neue Auswahl darf die kanonische Diagnostic-ID, Ursache und fachliche Reihenfolge nicht ändern. Presentation-Suppression ist keine Entfernung aus dem kanonischen Bestand.
- Rootwechsel und Fresh/Reuse müssen dieselbe fachliche Auswahl ergeben. Kein veralteter Teilbestand aus vorherigen Roots.

## 6. Menschenlesbare Diagnosen

### 6.1 Minimaler gemeinsamer Textformatter

Strukturierte Diagnosen bleiben die Quelle der Wahrheit. Der Renderer interpretiert weder INTERLIS-Syntax noch Normregeln erneut. In `interlis-model` beziehungsweise dem bestehenden reinen Diagnostikbereich einen kleinen, hostunabhängigen Formatter bereitstellen; keine weitere Modulschicht und keine neue i18n-Laufzeit.

Vorgesehene Verträge, soweit noch kein gleichwertiger Formatter vorhanden ist:

```java
public enum DiagnosticLanguage { DE, EN }

public record DiagnosticMessage(String text, Optional<String> hint) { }

public final class DiagnosticMessageFormatter {
    public DiagnosticMessage format(Diagnostic diagnostic, DiagnosticLanguage language);
    public String formatRelated(RelatedLocation location, DiagnosticLanguage language);
}
```

Reine Abbildung von Code/MessageKey/strukturierten Argumenten und bekannten Regelkennungen auf Text. Keine Datei-/Netzwerkzugriffe, kein `Locale.setDefault`, kein globaler veränderlicher Sprachzustand. Für die vorhandenen JVM-/TeaVM-Pfade geeignete einfache unveränderliche Templates verwenden. Fehlende Host-ResourceBundles dürfen keinen Unterschied zwischen JVM und Browser erzeugen.

Die neue CLI-Option heisst **`--diagnostic-language de|en`** und gilt bei `check`, `compile`, `analyze`, `diagram`, `format`. Default **`en`**, unabhängig vom Betriebssystem. Explizites `de` liefert deutsche Diagnostik. Ungültige Werte sind Bedienfehler vor Compiler-/Netzwerkzugriff. Die Option steuert Diagnoseprosa und Hinweise, nicht INTERLIS-Namen, Codes, numerische Werte, JSON-Feldnamen oder die Sprachversion des Modells. Keine neue automatische Locale-Erkennung. Hilfe darf englisch bleiben; keine Vollübersetzung aller CLI-Texte als Nebenprojekt.

`ConsoleDiagnosticRenderer.render(...)` erhält die Sprachwahl explizit. Sein bestehender Komfortaufruf kann intern EN verwenden; keine neue Profildoppelung. `JsonDiagnosticWriter` erhält dieselbe Textabbildung für ein additives `message`-Feld; maschinelle Identität bleibt davon unabhängig.

### 6.2 Darstellungsqualität und Abdeckung

- Jeder aktuell registrierte Diagnosecode und Nachrichtenschlüssel erhält eine sinnvolle DE-/EN-Grundvorlage. Ein Test iteriert den tatsächlichen Katalog und prüft Parameter-/Templateabdeckung. Nicht nur den Office-Fall hübsch machen.
- Bekannte häufige Ursachen wie fehlender Name, fehlender Import, Mehrdeutigkeit, Syntaxfehler, Typkonflikt, Namenskollision, Dateifehler und Ressourcenabbruch werden unterscheidbar erklärt. Behauptungen dürfen nur so spezifisch sein wie die vorhandene strukturierte Ursache.
- Bei `ILI-SEM-0010` die bekannte Regelkennung `SCOPE_OUTER_TYPE_NAME` gezielt formatieren. Eine Meldung über Namenskonflikte darf nicht bloss «Vererbungsfehler» sagen. Die übrigen bestehenden Regelvarianten erhalten mindestens einen verständlichen, ursachentreuen Text; keine nackten Slash-Kennungen als Benutzerprosa.
- Es wird kein Rohdump wie `diagnostic.semantics.inheritance`, `DiagnosticArgument[...]`, `RejectionSummary[...]` oder `SCOPE_...` als normale Erklärung ausgegeben. Stabile Codes wie `[ILI-SEM-0010]` bleiben sichtbar.
- Namen und Werte werden korrekt eingesetzt und gequotet. Exakte Dezimalwerte bleiben exakt; keine localeabhängige Rundung. Steuerzeichen/ANSI-Escapes und Zeilenumbrüche in externen Argumenten sicher darstellen, damit sie keine gefälschten Diagnosen oder Terminalbefehle erzeugen.
- Unbekannte externe Diagnosen/fehlende Argumente erzeugen einen ehrlichen, sicheren Fallback und keinen Formatterabsturz. Für den **eigenen aktuellen Katalog** ist fehlende Übersetzung ein fehlschlagender Test, kein routinemässiger Fallback.
- Unaufgelöster Name bedeutet nicht automatisch «Import fehlt». Solange der Binder die Ursache nicht genauer liefert, neutral formulieren: «Der Name … kann an dieser Stelle nicht aufgelöst werden.» Keine neue Binderheuristik für bessere Prosa erfinden.
- Keine neue Warnungs-/Fehlerunterdrückung einführen. Vorhandene darstellungsbezogene Ursachenunterdrückung nur mit unverändertem kanonischem Ergebnis und sichtbarem Unterdrückungsumfang einsetzen.

### 6.3 Primärstelle und RelatedLocation

Für den Office-Konflikt und analoge Namenskonflikte die **tatsächliche kollidierende Deklaration** im Compiler erfassen. `GraphBuilder.validateTypeNamespaces()` hat dafür den Deklarationsindex; es darf nicht nur ein namenloses Set als einzigen Konfliktnachweis weiterreichen. Die passende ursprüngliche Deklaration deterministisch ermitteln, ohne Reihenfolge oder Akzeptanzregel zu ändern.

Den vorhandenen `DiagnosticDraft`-/`RelatedLocation`-Mechanismus verwenden. Die RelatedLocation enthält den echten Range der übergeordneten beziehungsweise kollidierenden Deklaration und einen eigenen formatierbaren Hinweis. SourceId, SourceRevision und UTF-16-Offsets erhalten. Keine hartcodierten Zeilen 272/279 im Produktionscode, keine Suche nach einem zufällig gleichnamigen Token im Renderer.

**Beispiel einer geforderten Darstellungsqualität, kein bytegenaues Golden:**

```text
SO_AGI_DataCatalog_Base_20260529.ili:279:5: Fehler [ILI-SEM-0010]
  Der lokale Typname 'Office' kollidiert mit einem Namen im umschliessenden Namensraum.
  Verwandte Deklaration: SO_AGI_DataCatalog_Base_20260529.ili:272:9 ('Office').
  Hinweis: Verwende unterschiedliche Namen für die kollidierenden Deklarationen.
```

Sind Deklarationsarten bereits strukturiert verfügbar, darf der Text ausdrücklich «Klasse» und «Topic» nennen. Nicht aus Dateinamen oder Strings raten. Der Hinweis ist eine Modellierungshilfe, keine automatische Änderung bestehender Modell-/Transfernamen.

Die Ausgabe verwendet menschenlesbare 1-basierte Zeile/Spalte aus der passenden SourceRevision. Intern bleiben UTF-16-Offsets. Relative Anzeige zum expliziten Arbeitsverzeichnis ist erlaubt; vollständige SourceIds bleiben im JSON. Ohne Quelltext muss ein sinnvoller URI-/Offsethinweis möglich sein, statt Positionen zu erfinden. Kein Nachladen von Dateien im Renderer. Ein kurzer Quellausschnitt ist optional; genaue Underline-/Terminalbreitenberechnung ist kein separates Mussprojekt.

### 6.4 JSON, stabile Daten und andere Verbraucher

Die vorhandenen maschinellen Felder `id`, `code`, `severity`, `phase`, `messageKey`, `arguments`, `range` erhalten ihre bisherige Bedeutung. Für CLI-JSON darf `message` additiv ergänzt werden. RelatedLocations und vorhandene Ursachenbeziehungen werden als strukturierte Zusatzfelder ausgegeben; keine Textzerlegung für Maschinen voraussetzen. Exakte Zahlendarstellung und JSON-Escaping erhalten.

Eine neue Sprache ändert **nur die Präsentationsfelder**, niemals Code, Arguments, Range, ID, Severity, Rootauswahl oder Compilerentscheidung. Technische `messageKey`s bleiben im JSON ausdrücklich zulässig; das Verbot betrifft die menschliche Standarddarstellung. Der bestehende JSON-Statusvertrag wird in diesem Auftrag nicht zusätzlich umfassend umgebaut.

Die RelatedLocation-Ergänzung darf vorhandene kanonische Primärargumente nicht unbemerkt ersetzen. Nur wenn ein zusätzliches strukturiertes Argument fachlich nötig ist, die Diagnosekatalog-/Schemaänderung explizit dokumentieren und die betroffenen exakten Erwartungsdaten **gezielt** migrieren. Erwartete Sprachoutcomes, Original-/Kontrollquellen und Normpins bleiben unverändert. Keine pauschale Lockerung von `NormativeConformance`, um neue Payloads irgendwie zu akzeptieren.

LSP-/WASM-/Browser-Protokolle nicht beiläufig umbenennen. Der reine Formatter darf wiederverwendbar sein; dieser Auftrag verlangt zunächst verständliche **CLI-Ausgabe**, keine Neugestaltung der IDE. Betroffene gemeinsame Diagnosedaten und Portableparität jedoch vollständig testen.

## 7. Phasenplan mit eindeutigen Abnahmen

**Drei aufeinanderfolgende Phasen, ein unabhängiger Review je Phase.** Codex arbeitet innerhalb einer Phase mit kleinen Commits und zielgerichteten Tests. Es gibt weder einen Pflichtreview nach jeder Klasse noch einen zweiten administrativen Phasenreview. Phase CLI3 enthält zugleich die kumulative Gesamtabnahme. Nach PASS einer Phase wird ohne erneute Benutzerbestätigung automatisch die nächste begonnen.

Baseline, kleine Testmigrationsliste und OpenCode-Preflight gehören zur ersten Phase, nicht zu einer endlosen Vorbereitungsphase. Bereits vorhandene korrekte Implementierungen werden nachgewiesen statt neu geschrieben.

### CLI1 – Neuer Name und nur noch ein CLI-Vertrag

**Ergebnis:** gebautes und startbares `interlis-compiler`, kein Legacy-Fallback und kein altes Auslieferungsartefakt. Alle notwendigen Prüfrunner kennen die neuen Pfade.

Arbeiten:

1. Ausgangscommit, Arbeitsbaum und bestehende Prüfungen festhalten; die fünf Legacyklassen und ihre tatsächlichen Verbraucher prüfen. OpenCode-Provider und autonome Ausführung vor aufwendiger Implementierung verifizieren.
2. Modul mit allen Source-Sets umbenennen. Launcher-/Distributionsnamen explizit setzen. CLI-Toolidentität von Stack-/API-Versionen trennen.
3. Bedienkompatibilität entfernen und Standardfehlerverhalten für fehlende/ungültige Aufrufe implementieren. Vollständige Funktion der bisherigen Standardkommandos erhalten.
4. Testdelta klassifizieren und allgemeine Prozess-/Fehlerprüfungen migrieren. Externe Referenzprüfungen erhalten. Keine Reparatur des später gelöschten Legacyplanners mehr verlangen.
5. Build-, SDK-, Plattform-, Release- und aktive Dokumentationspfade im selben Abschnitt nachführen. Releaseprüfer dürfen nicht mehr die ausdrücklich entfernten ili2c-Artefakte verlangen, müssen aber die neue tatsächliche Auslieferung vollständig prüfen.

| ID | Verbindliches Erfolgskriterium |
|---|---|
| CLI1-01 | Neues Gradle-Modul enthält alle bisherigen aktiven Source-Sets; kein altes Projektalias; Bibliotheksmodul `interlis-compiler` unverändert benannt. |
| CLI1-02 | Frisch erzeugte Installation und SDK-ZIP enthalten ausschliesslich die neuen Compiler-Launcher; kein `interlis-cli`-/`ili2c`-Launcher oder `compat/ili2c.jar`; CLI-JAR und Compilerbibliotheks-JAR sind eindeutig getrennt. |
| CLI1-03 | Kein Laufzeit-Legacyprofil, keine automatische Profilerkennung, keine alte Invocationplanung; gemeinsame CLI-Klassen benötigen keinen Legacyparameter mehr. |
| CLI1-04 | Fehlender/ungültiger Unterbefehl, alte Optionen, Hilfe und Version zeigen das neue definierte Verhalten; Bedienfehler vor Host-IO und ohne Compilerstart; Exitcodes 0/1/2/3/4 bleiben korrekt unterschieden. |
| CLI1-05 | `check`, `compile`, `analyze`, `diagram`, `format` und bestehende Standardoptionen funktionieren über das gebaute Programm, nicht nur über Testklassen. |
| CLI1-06 | Die kleine Testdelta-Tabelle erklärt jede Entfernung und Migration; fachliche Sprach-, Pfad-, Unicode-, Stream-, IO- und Abbruchabdeckung bleibt erhalten. |
| CLI1-07 | Gemeinsame Conformance-/Norm-/Semantikprüfungen verwenden den neuen Aufruf und denselben externen Java-Runner; Outcomes, Suite-/Quellenpins und freigegebene Normprofile unverändert. |
| CLI1-08 | SDK-/Plattform-/Releasewerkzeuge und aktive Dokumentation verwenden reale neue Pfade und den neuen Produktumfang; historische Berichte bleiben erhalten; Pythonfreiheit bleibt aktiv. |
| CLI1-09 | Vollständiges Phase-CLI1-Gate und eine eigene Reviewer-Gegenprobe am exakten Kandidaten bestehen; kein Muss-Finding, kein fehlender Pflichtnachweis. |

**Eigene Gegenprobe des Reviewers:** Aus dem frischen Archiv in einem fremden Arbeitsverzeichnis mit Leerzeichen/Unicode starten; alten Dateiaufruf und eine ungültige Option testen; sicherstellen, dass dabei kein Netz-/Resolverzugriff erfolgt und kein versteckter Alias ausgeliefert wird. Mindestens ein gültiger und ein fachlich ungültiger neuer Standardaufruf als echte Prozesse.

### CLI2 – Auftragsrelevanz und verständliche Diagnoseausgabe

**Ergebnis:** korrekter auftragsbezogener Diagnoseteilbestand, eine menschenlesbare DE-/EN-Ausgabe und unverfälschte maschinelle Daten. Das Originalmodell ist reproduzierbarer UX-Regressionsfall.

Arbeiten:

1. Die Relevanzlücke mit einem minimalen Multi-Source-/Single-Root-Fall reproduzieren. Zuerst tatsächliches RED zeigen, dann den gemeinsamen Selektionsvertrag implementieren. Kein Fix nur durch Weglassen weiterer Eingaben im Test.
2. Die vollständige Analyse erhalten und Gate/CLI an dieselbe auftragsbezogene Diagnosesicht binden. Warnings, Auflösungsfehler ohne Range, Import-/Translationabhängigkeiten und zwei Modelle in einer Datei prüfen.
3. Den reinen Textformatter und explizite Sprachwahl implementieren; aktuelle Katalogabdeckung und verständliche Standardfehler testen.
4. Namenskonflikte um tatsächliche RelatedLocations ergänzen; Office-Original und getrennte positive Kontrolle benutzen. Keine Sprachentscheidung verändern.
5. JSON kontrolliert um Präsentation/RelatedLocations ergänzen; bestehende Maschinenfelder und Protokollverbraucher erhalten. Gemeinsame API-/Diagnoseverträge dokumentieren.

| ID | Verbindliches Erfolgskriterium |
|---|---|
| CLI2-01 | Request-Diagnostik entsteht genau einmal anhand des Compilerauftrags; Gate und Compile-/Diagramm-CLI verwenden dieselbe Auswahl; keine zweite Semantik in der CLI. |
| CLI2-02 | Volle `AnalysisSnapshot.diagnostics()`-/Workspace-/IDE-Sicht bleibt erhalten; die neue Sicht ist immutable, snapshot-/root-/revisionsgebunden und deterministisch. |
| CLI2-03 | Fremde semantische Fehler und Warnungen beeinflussen weder Ergebnis noch gewöhnliche Compile-Ausgabe; mehrere Quellen und mehrere Modelle je Datei sind abgedeckt. |
| CLI2-04 | Echte Import-/Translation-/Overlayabhängigkeiten, fehlende/mehrdeutige Roots/Imports und globale Auftragsfehler bleiben sichtbar; keine unzulässige Unterdrückung von Lexer-/Parserfehlern in benötigten Quellen. |
| CLI2-05 | Fresh/Reuse, Rootwechsel, Fehler-/Warnungspolitik und Abbruch liefern korrekte Zustände; fremde Diagnosen lösen kein `WARNINGS_REJECTED` aus. |
| CLI2-06 | Alle aktuellen Diagnosecodes/-keys besitzen sinnvolle DE-/EN-Vorlagen; Default EN und explizites `--diagnostic-language` sind deterministisch; kein roher Key-/Recorddump als normale Benutzererklärung. |
| CLI2-07 | Der Office-Fall bleibt im geltenden Sprachprofil abgelehnt, wird als Namenskonflikt erklärt und zeigt beide echten Deklarationsstellen. Unverändertes Original und separate positive Kontrolle sind geprüft. |
| CLI2-08 | Verwandte Stellen, SourceRevision, UTF-16/Unicode/CRLF, fehlender Quelltext, exakte Zahlen und sicheres Escaping funktionieren ohne Renderer-IO und ohne Formatterabsturz. |
| CLI2-09 | JSON bleibt parsebar und stabil in den kanonischen Feldern; nur Präsentation ändert sich mit DE/EN; keine Sprach-/XFAIL-/Normlockerung zur Anpassung von Tests. |
| CLI2-10 | stdout/stderr, Ausgabe-/Flushfehler, `format`-/`diagram`-Fachausgaben und strukturierte Laufabschlüsse bleiben korrekt; kein Erfolg trotz interner/operativer Störung. |
| CLI2-11 | Das Phase-CLI2-Gate und mindestens eine neue fachliche Reviewer-Gegenprobe bestehen; sämtliche CLI1-Verträge bleiben erfüllt. |

**Eigene Gegenprobe des Reviewers:** Eine andere Kombination als Codex verwenden, etwa zwei Modelle in einer Datei mit genau einem ausgewählten Root, oder ein Rootwechsel nach einem Cachetreffer. Dazu ein Fehler in einer echten importierten Abhängigkeit und eine deutsche Meldung mit Emoji/CRLF. Nicht bloss Codex' Office-Test erneut benennen.

### CLI3 – Produkt- und Gesamtabnahme

**Ergebnis:** getestete, reproduzierbare Distribution mit eindeutigem Namen, unveränderter Sprachqualität und vollständig qualifiziertem neuen Bedienvertrag.

Arbeiten:

1. Sämtliche CLI-Regressionen gegen frisch gebaute und entpackte Kandidatenartefakte ausführen. Keine alte lokale Installation, kein eingeschleuster Testclasspath.
2. Anpassungen an den Release-/Artefaktprüfern adversarial testen: falscher Launchername, ausgelassener neuer Launcher, verbliebenes Legacyartefakt, unvollständige/stale Fremdberichte müssen scheitern. Erwartete historische Dateizahlen nicht blind heruntersetzen.
3. Vollständiges aktualisiertes P3-Produktprofil einschliesslich Referenz-/Corpus-/Browser-/Plattform-/SDK-/Reproduzierbarkeitsnachweisen durchführen. Die explizit gestrichene **CLI-Bedienparität** ist die einzige entfernte Qualitätsverpflichtung.
4. Aktive Spezifikation, Quickstart, SDK-Beispiele, Ausgabe-/Sprachoptionen und Releaseinventar abschliessen. Eine knappe Umstellungsnotiz genügt; keine alten Programme erhalten.
5. OpenCode prüft den finalen Stand kumulativ gegen alle Kriterien CLI1–CLI3. Kein zusätzlicher vierter Freigabereview.

| ID | Verbindliches Erfolgskriterium |
|---|---|
| CLI3-01 | Frische Module-/SDK-Archive starten ausschliesslich als `interlis-compiler`; Hilfe, Version, Capabilities und dokumentierte Beispiele passen zu tatsächlichen Artefakten. |
| CLI3-02 | Echte CLI-/SDK-Prozesse auf macOS arm64, Linux x64 und Windows x64 sind für exakt denselben finalen Produktcommit nachgewiesen; Pfade, UTF-8 und Exit-/Streamverhalten korrekt. |
| CLI3-03 | Gesamte vorhandene JVM-, Conformance-, Norm- und semantische Vergleichsmatrix besteht mit unveränderten fachlichen Erwartungen; keine ausgeblendeten Fehler oder automatisch geänderten Baselines. |
| CLI3-04 | TeaVM-JS/WasmGC, Node, Browser einschliesslich echtem Safari, Web Image, LSP und Diagramm-/SDK-Verbraucher erfüllen das aktualisierte vollständige Produktprofil. |
| CLI3-05 | Gepinnte reale Closures, Performance-/Ressourcenbudgets und reproduzierbare Lieferdateien bleiben nachgewiesen; SDK-/Releaseinventare prüfen tatsächliche Dateien und keine bloss angepassten Zähler. |
| CLI3-06 | Produkt, Build, Tests und Review sind Python-frei; bestehende Sperr-/Prüfpfade bleiben aktiv. Keine Geheimnisse, lokalen Zugangsdaten oder Testhilfen in der Distribution. |
| CLI3-07 | Aktive Dokumente und Verträge sind konsistent; verbleibende alte Namen sind begründet historische Belege, externe Referenzen oder gezielte Negativtests – keine aktiven Alias-/Bedienwege. |
| CLI3-08 | Unabhängiges kumulatives OpenCode-PASS für CLI1–CLI3 am finalen SHA; tatsächlich beendeter Reviewprozess, vollständiger unveränderter Bericht, eigene Gegenprobe und alle Pflichtnachweise vorhanden. |

**Eigene Gegenprobe des Reviewers:** Archiv tatsächlich auspacken und benutzen, in einer isolierten Kopie einen unerlaubten alten Launcher hinzufügen oder den neuen entfernen und prüfen, dass der Lieferchecker scheitert. Dazu einen neuen auftragsbezogenen Diagnosefall über den entpackten Launcher ausführen. Keine Mutation im Produktkandidaten selbst.

## 8. Testmatrix, Orakel und ausführbare Gates

### 8.1 Regressionsfälle für den normalen Benutzerablauf

Die Szenarien sind Mindestabdeckung, keine Pflicht für exakt ebenso viele Testmethoden. Kleine Fälle parametrisieren, wenn Assertions dadurch verständlich bleiben. Modellsyntax gegen vorhandene gültige Fixtures beziehungsweise den Referenzcompiler verifizieren; keine Erwartungen aus derselben Implementierung generieren.

| Fall | Aufbau | Erwartung |
|---|---|---|
| R01 | Gültiges Rootfile; nebenan defektes, nicht angegebenes Modell | Exit 0; keine fremde Diagnose; kein impliziter Verzeichnisscan |
| R02 | Root + fremdes semantisch defektes File ausdrücklich geladen; nur `--root Root` gewählt | Root erfolgreich; fremde Diagnose nicht in Compileausgabe; volle Analysesicht weiter überprüfbar |
| R03 | Root importiert ein ungültiges Modell | Ablehnung und echte Diagnose der Abhängigkeit sichtbar |
| R04 | Root importiert gültiges Modell; fremder Importeur desselben Modells ebenfalls verfügbar | Keine künstlichen unresolved-Meldungen des fremden Importeurs in Compileausgabe |
| R05 | Zwei syntaktisch gültige Modelle in einer Datei, nur eines mit semantischem Fehler | Auswahl des gültigen Modells erfolgreich; Auswahl beider abgelehnt |
| R06 | Syntaxfehler in tatsächlich benötigter Datei bzw. vollständig fremder Datei | Benötigter Syntaxfehler nicht versteckt; fremde Datei belastet Root nicht |
| R07 | Relevante Warnung, fremde Warnung, jeweils allow/reject | Richtige Sichtbarkeit und Gatewirkung; keine generelle Warning-Unterdrückung |
| R08 | Fehlender/mehrdeutiger Root oder Import; Diagnose ohne sinnvollen SourceRange | Klarer Fehler statt leerer Erfolgs-/Fehlerausgabe |
| R09 | Translationoriginal/Overlayziel ist erforderliche Abhängigkeit | Relevante Ursachen erhalten; unrelated Fehler bleiben getrennt |
| R10 | Gleicher Quellenbestand, andere Roots, Fresh/Reuse und geänderte Imports | Semantisch gleiche Ergebnisse wie frischer Lauf; keine stale Diagnoseauswahl |
| R11 | Office-Original und getrennte positive Kontrolle | Bestehender Outcome erhalten; verständlicher Namenskonflikt mit richtigen Primary-/RelatedRanges |
| R12 | Gleicher Fehler unter DE/EN und `--json` | Nur Präsentation verschieden; Codes, Argumente, Ranges, IDs und Exit identisch |
| R13 | CRLF, Umlaut-/Emoji-/Leerzeichenpfade, Steuerzeichen in Argumenten, fehlender Quelltext | Richtige Positionen, UTF-8, sichere Darstellung, kein Absturz |
| R14 | `format`/`diagram`, unwritable output, Flushfehler, kontrollierter Abbruch | Kein vermischter stdout und kein falscher Erfolg |
| R15 | Leerer Aufruf, nackte Datei, Tippfehler, alte Optionen, help/version | Neuer Bedienvertrag; keine Profilerkennung; erwartete Exits ohne unerwünschtes IO |
| R16 | Frisches SDK-Archiv ausserhalb des Checkouts; kein Testclasspath | Ausgeliefertes Produkt funktioniert; alte Namen/Artefakte fehlen |

`analyze` ist bewusst nicht gleich `check`: seinen vorhandenen Analyse-/Ergebnisvertrag explizit bewahren und testen. Dass dort zusätzliche Analysedaten verfügbar sind, ist kein Grund, sie beim Compile-Ergebnis an Benutzer auszugeben.

### 8.2 Vorgesehene Tests und Arbeitsorte

Nach Umbenennung die bestehenden `CliApplicationTest`, `P3CliBoundaryContractTest`, `P3ConfigurationContractTest`, `P3CliLifecycleContractTest`, `P3AtomicOutputContractTest`, `RepositoryCliContractTest` und SDK-/Plattformprüfungen weiterverwenden. Bei Bedarf kleine neue Tests, beispielsweise:

- `CompilerCliIdentityContractTest`: Namen, fehlender Unterbefehl, kein Legacy-Fallback, Toolmetadaten.
- `RequestDiagnosticScopeContractTest` im Compiler: beide Diagnosesichten, Gate, mehrere Modelle/Quellen, Fresh/Reuse.
- `CompilerCliDiagnosticContractTest`: echte Standardaufrufe mit Roots/Imports/DE/EN/JSON.
- `DiagnosticMessageFormatterTest` im Modell-/Diagnostikbereich: vollständiger Katalog, Formatierung und sichere Fallbacks.

Die Namen sind Vorschläge für **neu anzulegende** Tests, keine Behauptung bereits vorhandener Klassen. Wiederverwendung geht vor zusätzlichem Framework. Globale statische Code-/Regexprüfungen allein ersetzen keine Prozess- und API-Assertions.

### 8.3 Gateumfang pro Phase

Während der Entwicklung gezielte Modultests. Vor jeder Reviewübergabe frische Pflichtläufe am Kandidaten. Beim unabhängigen Reviewer eigenes sauberes Buildverzeichnis und keine unbesehen übernommenen lokalen Reports.

**CLI1 – Mindestgate:**

```sh
./gradlew --no-daemon check conformanceCheck pythonFreeCheck \
  sdkProcessCheck sdkArchiveCheck \
  :interlis-compiler-cli:installDist :interlis-compiler-cli:distZip \
  --rerun-tasks --console=plain
```

Dazu die betroffenen Release-/Evidenzchecker-Tests und ein echter Prozesslauf aus der neuen Distribution. Nach Erstellung des Kandidaten darf dieser Aufruf keine alten Projektpfade mehr benötigen. `sdkArchiveCheck` muss über den tatsächlichen Distributionsbuild verfügen, nicht über ein liegen gebliebenes SDK-ZIP.

**CLI2 – Mindestgate:**

```sh
./gradlew --no-daemon check checkPortable conformanceCheck pythonFreeCheck \
  semanticComparison sdkProcessCheck sdkArchiveCheck \
  --rerun-tasks --console=plain
```

Zusätzlich relevante LSP-/Bridge-/Formattertests, soweit nicht bereits durch `check` abgedeckt. Eine Änderung des gemeinsamen Selektions-/Diagnosedatenvertrags braucht portable Gegenproben; nur CLI-Textgoldens genügen nicht.

**CLI3 – vollständiges Schlussgate:**

```sh
./gradlew --no-daemon releaseCheckP3 --rerun-tasks --console=plain
```

Die Root-Tasks sind am untersuchten Stand vorhanden. Ihre aktuellen Abhängigkeiten und Preflights vor Ausführung lesen. `releaseCheckP3` wird in CLI1/CLI3 an den **neu beschlossenen Produktumfang** angepasst; nicht durch einen dünneren neuen Task ersetzt. Relevante bislang separat aufgerufene Referenz-/Semantik-/Archiv-/Reproduzierbarkeitstasks explizit ergänzen, falls der aktuelle aggregierte Task sie nicht ausführt. Einen tatsächlichen Task-DAG und die ausgeführten Reports prüfen, nicht den Tasknamen als Nachweis verwenden.

Für die vorhandene macOS-Prüfumgebung den bestehenden Runner benutzen:

```sh
node tools/verification/without-python.cjs \
  ./gradlew --no-daemon releaseCheckP3 --rerun-tasks --console=plain
```

Dieser Runner ist macOS-spezifisch; nicht als Linux-/Windowslösung ausgeben. Auf den anderen Plattformen gelten die vorhandenen Python-freien CI-Verträge. Die Compiler-/Java-/Gradle-/Node-/Browserpins nicht beiläufig aktualisieren.

Der abschliessende Windows-/Linux-Nachweis muss exakt den Kandidaten betreffen. Vorhandene berechtigte CI-Wege verwenden; niemals einen älteren grünen Lauf umetikettieren. Ein gewöhnlicher Coding-Agent darf nicht eigenmächtig veröffentlichen, mergen oder Zugangskonfiguration ändern. Fehlt die Berechtigung zum erforderlichen CI-Start und gibt es keine gleichwertige lokale Prüfplattform, ist dieser Nachweis `BLOCKED`, kein Grund für einen stillen Skip. Autonomie umfasst alle bereits autorisierten lokalen Implementierungs-, Commit-, Test- und Reviewaktionen; sie erfindet keine Remote-Berechtigung.

### 8.4 Grenzen von Testergebnissen

Keine fixe Testzahl als alleiniger Erfolgsmassstab. Nach bewusstem Entfernen reiner Legacytests darf die Zahl sinken, nach neuen Regressionen steigen. Test-ID-Menge, fachliche Kriterien, tatsächliche XML-Fälle, Failures/Errors/Skips, Prozess-Exits und Artefakte sind zu prüfen. Die frühere Zahl 54 Legacyprozesse ist **kein** neuer Mindestvertrag. Eine notwendige neue Prüfung darf nicht als Legacyprüfung etikettiert und gelöscht werden.

Berichte müssen zum Kandidaten, Lauf und Artefakt passen. Negative Canarys für alte/fremde/unvollständige/übersprungene Reports und falsche Dateiinventare erhalten. Keine neue selbsttragende Hash-/Evidenceplattform bauen; Git-SHA, vorhandene Rohreports und wenige gezielte Konsistenzprüfungen genügen.

## 9. Autonomer Codex-/OpenCode-Ablauf

### 9.1 Rollen, Entscheidungen und Nacharbeit

**Agent A / Codex** implementiert alle drei Phasen nacheinander. Er startet **selbst** den unabhängigen Review, liest Findings, korrigiert, committet einen neuen Kandidaten und lässt erneut prüfen. Er wartet nach einem gültigen PASS nicht auf eine weitere Benutzerfreigabe. Der Auftrag ist nicht bereits nach dem Schreiben eines Reviewprompts erledigt.

**Agent B / OpenCode + DeepSeek** prüft in einer eigenen frischen Sitzung und einem eigenen sauberen Worktree. Er hat weder die Produktänderung noch deren zugehörige Tests geschrieben. Er prüft Code, vollständigen Diff, Kriterien und eigene Gegenbeispiele. Er verändert den Produktkandidaten nicht.

Genau drei Urteile:

| Urteil | Bedeutung |
|---|---|
| `PASS` | Alle Pflichtkriterien der Phase am exakten SHA erfüllt, alle fälligen Nachweise vorhanden, mindestens eine eigene ausgeführte Gegenprobe, keine offenen Muss-Findings. CLI3 zusätzlich kumulativ. |
| `FAIL` | Ein Pflichtkriterium ist nachweislich verletzt. Findings innerhalb derselben Phase beheben, neuer Commit, neuer Review. |
| `BLOCKED` | Pflichtprüfung mangels Umgebung, Berechtigung, vollständiger Reviewausführung oder echter Nutzerentscheidung nicht möglich. Ursache konkret bezeichnen, nicht als Erfolg verbuchen. |

Ausführungszustände `IN_PROGRESS`/`AWAITING_REVIEW` sind keine weiteren Reviewurteile. Ein fehlender API-Key, unpassender Provider oder Transportabbruch ist kein fachliches Compiler-FAIL, aber verhindert eine Abnahme. Bei transienten Transportproblemen einen begrenzten erneuten Versuch mit derselben unveränderten Revision zulassen; z. B. höchstens zwei Wiederholungen. Danach den konkreten Blocker berichten statt endloser Wiederholungen oder Modellwechsel.

### 9.2 Exakter Prüfstand

1. Codex implementiert Phase, Tests und kurzen Implementierungsbericht, führt Pflichtchecks aus und erstellt einen **lokalen Produktcommit**. Keine fremden Änderungen mitcommitten.
2. Danach `CANDIDATE_SHA=$(git rev-parse HEAD)` auslesen. Niemals versuchen, diesen SHA in genau denselben Commit hineinzuschreiben. `BASE_SHA` und ursprünglichen Auftragsbasis-SHA separat festhalten.
3. Eigenen detached Worktree für den Reviewer anlegen. Neuer Versuch hat eigenes Laufverzeichnis und frischen Kontext. Ein explizit zweckgebundener Unterordner in dessen `build/review/...` ist für Bericht, Request und Gegenproben erlaubt.
4. Reviewer führt Tests selbst aus. Plattformnachweise können durch ihn aus einem belastbaren CI-Lauf **genau dieses SHAs** mit Rohlogs/Artefakten verifiziert werden. Codex' Zusammenfassung ist kein Ersatz.
5. Reviewer liefert `review.md` und ein kleines `verdict.json`. Codex wartet auf das **tatsächliche Prozessende**, liest beide Dateien und prüft SHA, Phase, Kriterien, Prozessstatus und Nachweise.
6. Bei FAIL: Findings beheben, neuer Produktcommit, erneute Prüfung mit unverändertem Phasen-BASE_SHA, damit weiterhin die ganze Phase sichtbar ist. Bei PASS: Bericht unverändert in einem reinen Dokumentationscommit ablegen; nächste Phase automatisch beginnen.
7. Jede anschliessende Änderung an Produkt, Tests, Build, Abhängigkeiten, ausführbaren Beispielen oder Kriterien erzeugt einen neuen prüfpflichtigen Kandidaten. Nur die Ablage des unveränderten Berichts beziehungsweise eine reine Statusnotiz benötigt keinen zweiten Review. Der Bericht nennt weiterhin den tatsächlich geprüften Produkt-SHA.

Reviewer-Gegenproben entstehen unter ignorierten Buildpfaden oder in einer zusätzlichen isolierten Scratch-Kopie. Falls JUnit-Kompilation Testquellen erfordert, ein separates Testsource-Set per externer Initdatei oder eine Scratch-Kopie nutzen. Nicht die eingecheckten Kandidatentests ändern und anschliessend «sauber zurücksetzen», um die Mutation zu verbergen. Ein dauerhaft nötiger Regressionstest wird von Codex in einen neuen Kandidaten übernommen.

### 9.3 Modell, API und bestehende Anmeldung

Verbindlich ist **OpenCode 2 mit DeepSeek-V4.1-Flash-1017 über DeepSeek direkt**. Die im Projekt nachgewiesene Kennung lautet:

```text
OpenCode provider/model: deepseek/deepseek-flash
API-Modell:             deepseek-flash
API-Host:               api.deepseek.com
```

Das passt zur offiziellen DeepSeek-Dokumentation zum Stand dieses Plans. Der API-Alias ist kein unveränderlicher Modell-Snapshot; seine tatsächliche Zuordnung und lokale OpenCode-Konfiguration sind beim Review zu prüfen und zu protokollieren. Keine Identitätsbehauptung aufgrund einer Modell-Selbstauskunft. Bei bekannter abweichender Aliaszuordnung oder fehlender 1017-Verfügbarkeit nicht still ein anderes Modell verwenden.

Vorhandene funktionierende GUI-/CLI-Anmeldung verwenden. **Ein fehlendes `DEEPSEEK_API_KEY` in der Shell beweist nicht, dass keine Anmeldung vorliegt.** CLI-Version, Profil, `opencode auth list`, verfügbare Modelle und effektiven Endpoint prüfen, ohne Tokens auszugeben. Kein OpenRouter, OpenCode-Go/Zen-Ersatzprovider, LiteLLM-Proxy oder anderer Modellfallback. Keine API-Keys in Prompts, Gitdateien oder Rohlogs schreiben.

Die lokal installierte OpenCode-2-Variante ist massgeblich. Vor den konkreten Aufrufen `--help`/`run --help` lesen; weder Flags raten noch OpenCode ungefragt neu installieren/upgraden. Die aktuellen offiziellen V2-Dokumente beschreiben `run`, `--model`, `--format json`, Dateianhänge und einen Standaloneweg. Nicht versehentlich eine V1-CLI mit anderen Settings verwenden.

### 9.4 Isolierter, tatsächlich abgeschlossener Reviewprozess

Bevorzugt einen **privaten Standaloneprozess** im Reviewworktree starten. So hängen der Agent und seine Testprozesse nicht an einem fremden GUI-Server und erben die Python-Sperre. Wenn die vorhandene V2-Version stattdessen einen eigenen Server benötigt, diesen ausdrücklich unter derselben Sperre starten, auf Loopback begrenzen, seine Bereitschaft prüfen und bis zum Reviewende am Leben halten. Nicht an eine bereits laufende ungesperrte GUI-Session anhängen und dann eine vollständige Python-Sperre behaupten.

Beispiel des **nach lokalem Help-Check anzupassenden** Ablaufs auf der bisherigen macOS-Umgebung:

```sh
# Bash/zsh-Beispiel; aus dem Codex-Arbeitsbaum nach dem sauberen Kandidatencommit.
CANDIDATE_SHA=$(git rev-parse HEAD)
# BASE_SHA bleibt für alle Versuche dieser Phase der gleiche vollständige SHA.
# PHASE z. B. CLI1; ATTEMPT z. B. 1; OPENCODE_BIN ist der verifizierte absolute V2-Pfad.
REVIEW_WT="$(dirname "$PWD")/interlis-cli-review-${PHASE}-${ATTEMPT}-${CANDIDATE_SHA:0:12}"
git worktree add --detach "$REVIEW_WT" "$CANDIDATE_SHA"
REVIEW_DIR="$REVIEW_WT/build/review/compiler-cli/${PHASE}-${ATTEMPT}"
mkdir -p "$REVIEW_DIR"
# Codex erzeugt hier request.json mit absoluten Pfaden, SHAs, Phase und Kriterien-IDs.
# Keine Secrets; keine ausgedachten PASS-/Reviewdaten.
(
  cd "$REVIEW_WT" || exit 1
  node tools/verification/without-python.cjs "$OPENCODE_BIN" run \
    --standalone --model deepseek/deepseek-flash --format json \
    --file docs/implementation/compiler-cli-modernization/EXECUTION_PLAN.md \
    --file docs/implementation/compiler-cli-modernization/OPENCODE_REVIEW_PROMPT.md \
    --file "$REVIEW_DIR/request.json" \
    "Prüfe die beauftragte Phase unabhängig. Schreibe review.md und verdict.json in das übergebene Reviewverzeichnis."
) >"$REVIEW_DIR/opencode.jsonl" 2>"$REVIEW_DIR/opencode.stderr.log"
REVIEW_PROCESS_EXIT=$?
# Erst jetzt Bericht, verdict.json, Sperrmanifest und sauberen Kandidaten verifizieren.
```

Das Beispiel ist **kein hier ausgeführter Review** und keine ungeprüfte Installationsempfehlung. Ein Wrapper mit `set -e` muss den Reviewaufruf so kapseln, dass der Exit auch im Fehlerfall gespeichert wird. Bei Verwendung einer Pipe den Exit des OpenCode-/Sperrprozesses, nicht den von `tee`, auswerten. Keine Hintergrundausführung ohne anschliessendes Wait/Drain/Join und Ergebnisprüfung. Kein leeres Dokument vorab als erwartetes Ergebnis anlegen.

Der vorhandene Python-Guard verändert PATH. Deshalb einen tatsächlich funktionierenden absoluten OpenCode-Pfad verwenden und benötigte harmlose Laufzeitwerkzeuge nachweisen. Guard nicht abschalten, nur weil ein Tool sonst nicht gefunden wird. Die intentionalen Negativproben des bestehenden Guards sind zulässig; sonst **keine Python-/pip-Aufrufe**, auch nicht für ein kleines Hilfsskript oder «nur zur Versionsprüfung». Keine neue Orchestrierungssprache oder allgemeine Agentenplattform einführen.

Autonomie benötigt passende **projekt-/worktreespezifische** Berechtigungen für Lesen, Builds, Testprozesse und Schreiben in Review-/Buildpfaden. Der Reviewer darf keine Produktdateien ändern, pushen, veröffentlichen oder globale Einstellungen umkonfigurieren. Nicht alle Hostrechte pauschal freigeben; vorhandene geeignete Konfiguration verwenden. Bei fehlender Berechtigung konkret blockieren, statt eine manuelle GUI-Abnahme als automatisch ausgeführt darzustellen.

### 9.5 Maschinenlesbares Urteil und Fail-Closed-Regeln

`request.json` enthält einheitlich `phase`, `baseSha` (Basis der ganzen Phase), `assignmentBaseSha` (Basis dieses Folgeauftrags), `candidateSha`, `reviewWorktree`, `reviewDirectory`, `implementationReport`, `criteria` und `requiredChecks`. Die grossgeschriebenen Shellvariablen im Beispiel entsprechen diesen Feldern. Alle Commitwerte sind vollständige SHAs; Pfade zu Worktree und Reviewverzeichnis sind absolut.

`verdict.json` bleibt klein und referenziert den ausführlichen Bericht:

```json
{
  "schemaVersion": 1,
  "phase": "CLI1",
  "baseSha": "<40-stelliger SHA>",
  "assignmentBaseSha": "<40-stelliger SHA>",
  "candidateSha": "<40-stelliger SHA>",
  "verdict": "PASS|FAIL|BLOCKED",
  "reviewer": {
    "provider": "deepseek",
    "model": "deepseek-flash",
    "apiHost": "api.deepseek.com",
    "openCodeVersion": "<tatsaechlich>"
  },
  "criteria": [
    {"id": "CLI1-01", "result": "fulfilled|violated|unverified", "evidence": ["<Pfad/Beleg>"]}
  ],
  "counterprobes": [
    {"description": "<eigene Hypothese>", "evidence": ["<Input und Ergebnis>"]}
  ],
  "openMandatoryFindings": [],
  "missingRequiredChecks": [],
  "report": "review.md"
}
```

Die Werte im Schema sind Platzhalter, keine vorgegebenen Prüfergebnisse. Codex darf das Reviewerurteil nicht selbst schreiben oder nachträglich verbessern.

PASS wird nur übernommen, wenn:

- der Reviewprozess vollständig beendet ist, Transport-/äusserer Guard-Exit erfolgreich sind und das abschliessende Guardmanifest nicht mehr `running` ist;
- der tatsächliche Review-HEAD dem Kandidaten entspricht und keine produktiven Kandidatenänderungen vorliegen;
- Provider/Modell/Endpoint explizit stimmen, Bericht und JSON vorhanden, gültig und in Urteil/SHAs/Phase konsistent sind;
- **alle** vorgesehenen Kriterien vorkommen, jedes als erfüllt mit einem echten Beleg; keine fehlenden Pflichtchecks oder offenen Muss-Findings;
- eigene neue Gegenproben wirklich ausgeführt und Rohberichte/Artefakte für diesen SHA geprüft wurden;
- bei CLI3 alle CLI1-/CLI2-Kriterien zusätzlich kumulativ geprüft sind.

Ein HTTP-200, ein Prozess-Exit 0, ein Wort «PASS» im Stream oder ein selbst gezähltes grünes JSON allein genügt nicht. Umgekehrt ist ein fertiger PASS-Text bei abgebrochenem Transport noch keine akzeptierte automatische Abnahme: `BLOCKED(REVIEW_TRANSPORT)`, Zustand prüfen und sauber wiederholen. Ein neues Normproblem bleibt `BLOCKED(NORM_DECISION)`; keine autonome Änderung der Spracherwartung.

## 10. Schlanke Ablage und Fortsetzung

```text
docs/implementation/compiler-cli-modernization/
  EXECUTION_PLAN.md
  CODEX_STARTPROMPT.md
  OPENCODE_REVIEW_PROMPT.md
  STATUS.md                     # von Codex beim Start, kurz
  implementation/
    CLI1.md
    CLI2.md
    CLI3.md
  reviews/
    CLI1-r1-<shortsha>.md         # unverändert vom Reviewer
    CLI2-r1-<shortsha>.md
    CLI3-r1-<shortsha>.md

testdata/cli-modernization/original/
  SO_AGI_DataCatalog_Base_20260529.ili
```

Rohlogs, JSONL, kleine Verdictdateien, Prozessausgaben, XMLs und Probequellen unter dem jeweiligen Review-/Buildverzeichnis oder als bestehende CI-Artefakte. Im Repository nur kurze Berichte, Testfixtures, erforderliche aktive Vertragsdaten und Verweise. Keine riesigen neuen Norminventare, keine vollständigen Sourcearchive je Review und keine selbstgebauten Zertifizierungsketten.

`STATUS.md`: aktuelle Phase, Zustand, letzter unabhängiger Produkt-PASS-SHA, Pfad/Hash des unveränderten Reviewberichts, gegebenenfalls konkreter Blocker und nächste Aktion. Implementierungsberichte nennen Basis, Umfang, Kriterien, tatsächliche Commands/Exits und geänderte Verträge. Reviewerberichte nennen zusätzlich eigene Gegenproben und jedes Kriterium einzeln. Alte FAIL-Versuche bleiben als kurze Historie erhalten.

Keine Unterbrechung nach einem erfolgreichen Phasenreview verlangen. Stoppen nur bei Gesamterledigung oder einem nach angemessenen autonomen Versuchen verbleibenden echten Blocker. Remote-Push/Merge/Release, Produktionsmigrationen und Veränderungen fremder Arbeitsstände sind nicht pauschal erlaubt.

## 11. Definition of Done

Der Auftrag ist abgeschlossen, wenn **CLI1-01 bis CLI1-09**, **CLI2-01 bis CLI2-11** und **CLI3-01 bis CLI3-08** am abschliessenden geprüften Stand erfüllt sind, die unabhängige kumulative CLI3-Abnahme PASS lautet und keine fehlenden Pflichtausführungen bestehen.

Der Abschlussbericht zeigt kurz:

- neuen Modul- und Programmnamen samt tatsächlich gebautem SDK-Pfad;
- entfernte Legacywege und erhaltene Referenz-/Fachprüfungen;
- einen echten DE-/EN-Diagnosevergleich am Office-Original und einen Root-/Import-Isolationsfall;
- ausgeführte Phasengates, Plattformen, Artefakte und unabhängige Review-SHAs;
- die ausdrücklich unveränderten Sprach-/Normverträge und verbleibende, nicht beauftragte Zukunftsarbeit.

**Kein Release wird durch diesen Plan veranlasst. Keine Behauptung, dass das Entfernen von Legacylogik allein Compilerfehler oder Diagnosequalität bereits gelöst habe.**

## 12. Quellen und Einordnung

Die folgenden Quellen wurden für diesen Plan gelesen. Repositorylinks mit SHA beschreiben den **untersuchten Ausgangsstand**; Klassennamen für neue Funktionen in diesem Plan sind Entwurfsvorgaben. Es wurden für die Planerstellung keine Compiler-, Plattform- oder OpenCode-Abnahmen ausgeführt.

- **Q1 – Prozesseinstieg und CLI-Verzweigungen:** [Main](https://github.com/edigonzales/interlis-java/blob/a996ef0a5e915eb76c3e96a98b8f445f7005e851/interlis-cli/src/main/java/guru/interlis/cli/Main.java), [DefaultCompilerCli](https://github.com/edigonzales/interlis-java/blob/a996ef0a5e915eb76c3e96a98b8f445f7005e851/interlis-cli/src/main/java/guru/interlis/cli/DefaultCompilerCli.java), [LegacyCliProfile](https://github.com/edigonzales/interlis-java/blob/a996ef0a5e915eb76c3e96a98b8f445f7005e851/interlis-cli/src/main/java/guru/interlis/cli/LegacyCliProfile.java).
- **Q2 – Build und Lieferartefakte:** [CLI-Build](https://github.com/edigonzales/interlis-java/blob/a996ef0a5e915eb76c3e96a98b8f445f7005e851/interlis-cli/build.gradle), [Root-Build](https://github.com/edigonzales/interlis-java/blob/a996ef0a5e915eb76c3e96a98b8f445f7005e851/build.gradle), [Hostverifikation](https://github.com/edigonzales/interlis-java/blob/a996ef0a5e915eb76c3e96a98b8f445f7005e851/gradle/host-verification.gradle).
- **Q3 – Vollanalyse und Gate:** [AnalysisSnapshot](https://github.com/edigonzales/interlis-java/blob/a996ef0a5e915eb76c3e96a98b8f445f7005e851/interlis-compiler/src/main/java/guru/interlis/compiler/AnalysisSnapshot.java), [CompilationOutcome](https://github.com/edigonzales/interlis-java/blob/a996ef0a5e915eb76c3e96a98b8f445f7005e851/interlis-compiler/src/main/java/guru/interlis/compiler/CompilationOutcome.java), [CompilationGate](https://github.com/edigonzales/interlis-java/blob/a996ef0a5e915eb76c3e96a98b8f445f7005e851/interlis-compiler/src/main/java/guru/interlis/compiler/internal/CompilationGate.java).
- **Q4 – Diagnostik und Hostvertrag:** [JsonDiagnosticWriter](https://github.com/edigonzales/interlis-java/blob/a996ef0a5e915eb76c3e96a98b8f445f7005e851/interlis-cli/src/main/java/guru/interlis/cli/JsonDiagnosticWriter.java), [RelatedLocation](https://github.com/edigonzales/interlis-java/blob/a996ef0a5e915eb76c3e96a98b8f445f7005e851/interlis-model/src/main/java/guru/interlis/diagnostics/RelatedLocation.java), [ExitCodePolicy](https://github.com/edigonzales/interlis-java/blob/a996ef0a5e915eb76c3e96a98b8f445f7005e851/interlis-cli/src/main/java/guru/interlis/cli/ExitCodePolicy.java).
- **Q5 – Bisheriges enges Kompatibilitätsprofil und aktueller Auftrag:** [CLI_PROFILE](https://github.com/edigonzales/interlis-java/blob/a996ef0a5e915eb76c3e96a98b8f445f7005e851/docs/p3/CLI_PROFILE.md), [Implementierungsstatus](https://github.com/edigonzales/interlis-java/blob/a996ef0a5e915eb76c3e96a98b8f445f7005e851/docs/implementation-status.md).
- **Q6 – Bestehendes Review-/Pythonverfahren:** [G2-Reviewprompt](https://github.com/edigonzales/interlis-java/blob/a996ef0a5e915eb76c3e96a98b8f445f7005e851/docs/g2/OPENCODE_REVIEW_PROMPT.md), [without-python.cjs](https://github.com/edigonzales/interlis-java/blob/a996ef0a5e915eb76c3e96a98b8f445f7005e851/tools/verification/without-python.cjs). Der Nutzer hat dieses Verfahren und das entsprechende phasenweise Vorgehen bei GRETL als bewährt bestätigt.
- **Q7 – Tatsächlicher Nutzerfall:** mitgelieferte Originaldatei samt oben genanntem SHA-256 und die vom Nutzer bereitgestellten Terminalausgaben. Kein behaupteter lokaler Pfad des Nutzers wird im neuen Test hartcodiert.
- **Q8 – OpenCode V2, geprüft am 31.10.2026:** [CLI Commands](https://opencode.ai/v2/docs/cli/commands/), [Models](https://opencode.ai/v2/docs/models/). Die tatsächlich installierte Version/Help bleibt für ausführbare Flags massgeblich.
- **Q9 – DeepSeek-Modell/API, geprüft am 31.10.2026:** [Model Details](https://api-docs.deepseek.com/quick_start/pricing/), [Change Log](https://api-docs.deepseek.com/updates/), [1017-Veröffentlichung](https://api-docs.deepseek.com/news/news261017/). Alias/Endpoint vor realer Reviewausführung nochmals gegen die konkrete Konfiguration prüfen; keine zeitlose Modellpin-Garantie ableiten.
