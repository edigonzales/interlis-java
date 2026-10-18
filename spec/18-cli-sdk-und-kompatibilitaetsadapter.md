# CLI, SDK und Kompatibilitätsadapter

## 1. Ziel

CLI und SDK sind dünne Zugänge zu derselben Engine. Die CLI besitzt keine zweite Sprachprüfung. Der SDK-Aufrufer muss keine Dateien erzeugen, um Strings zu analysieren. Vertraute Toolnamen bleiben möglich, während neue Java-Pakete einen sauberen dauerhaften Namensraum erhalten.

`ili2c` ist das erste CLI-Profil. Spätere `ilivalidator`-/`ili2pg`-/`ili2gpkg`-Profile verwenden dieselben Grundverträge für Konfiguration, Diagnostik und Laufabschluss, mit ihren eigenen fachlichen Diensten. Eine bisherige Option darf nicht still ignoriert werden. Optionen ausserhalb des jeweils freigegebenen Umfangs ergeben einen deutlichen Fehler.

## 2. CLI-Klassen und Signaturen

Paket `guru.interlis.cli`, mit werkzeugspezifischen Adaptern unter `guru.interlis.cli.ili2c`.

```java
public interface CliApplication {
    CliExecutionResult run(List<String> arguments,
                           CliEnvironment environment,
                           CancellationToken cancellation);
}

public interface CommandLineParser {
    CliParseOutcome parse(List<String> arguments, CommandSpec specification);
}

public interface ExitCodePolicy {
    int exitCode(CliTermination termination, LegacyCliProfile profile);
}

public interface OutputPlanner {
    OutputPlan plan(ParsedCommand command, CliEnvironment environment,
                    LegacyCliProfile profile);
}
```

Diese Signaturen sind normative Rollenverträge. Implementierungen sind final, soweit keine dokumentierte Erweiterung nötig ist. Die bereits in Kapitel 09 definierten Diagnose-/Laufdatentypen werden wiederverwendet, nicht dupliziert.

| Klasse | Zentrale Methoden | Verantwortung |
|---|---|---|
| `Ili2cMain` | `public static void main(String[] args)` | nur Umgebung/Abbruchsignal aufbauen, Anwendung aufrufen, Flush/Prozessende |
| `DefaultCompilerCli` | `run(...)` | Eingabe auflösen, Closure vorbereiten, Engine aufrufen, Ergebnisse rendern/exportieren |
| `DefaultCommandLineParser` | `parse(...)` | Optionen/Argumente gemäss CommandSpec parsen, noch keine IO/Modellanalyse |
| `CommandSpec` | Optionsdefinitionen, Positionsargumente, Konfliktregeln, Hilfetextordnung | versioniertes Werkzeugprofil |
| `CliEnvironment` | Arbeitsverzeichnis, stdin/stdout/stderr-Abstraktionen, Uhr, RunObserver, RunContextFactory, Repositorydienste, Profillader | vom Host übergebene Ressourcen; keine globalen Ersatzstreams |
| `ConfigurationLoader` | `load(ParsedCommand, CliEnvironment, LegacyCliProfile)` | Konfigurationsdateien lesen, Präzedenz und Herkunft offenlegen |
| `InvocationPlanner` | `prepare(ResolvedConfiguration, token)` | explizite Root-/Quellen-/Resolver-/Outputplanung |
| `CliOutputWriter` | `writeDiagnostics(...)`, `writeArtifacts(...)`, `writeSummary(...)` | getrennte Ausgabekanäle, definierte Reihenfolge und Fehler |
| `CliOutcomeMapper` | `classify(CompilationOutcome)` | Ergebnisvariante und RejectionSummary zu CliTermination abbilden; Compilererfolg ist noch kein Ausgabeerfolg |
| `LegacyExitCodePolicy` | `exitCode(...)` | aus Referenzprofil abgeleitete Zuordnung, keine geratenen Legacywerte |
| `ToolVersionRenderer` | `render(VersionManifest, LegacyCliProfile)` | Werkzeugversion, optionaler erweiterter Build-/Capabilitynachweis |

`CliParseOutcome` unterscheidet `ParsedCommand`, `HelpRequested`, `VersionRequested` und `InvalidCommandLine`. Eine Hilfe-/Versionsanforderung benötigt weder Modellserver noch Compilerinitialisierung, sofern das Referenzprofil dies nicht anders verlangt. Kompatibilitätsdetails werden empirisch erfasst.

`CliExecutionResult` enthält mindestens `CliTermination`, konkreten Exitcode, optionalen RunId, strukturierte Diagnosen und Artefaktinformationen. `CliTermination` unterscheidet `SUCCESS`, `SOURCE_REJECTED`, `USAGE_ERROR`, `OPERATIONAL_FAILURE`, `INCOMPLETE_ANALYSIS`, `INPUT_UNRESOLVED`, `WARNINGS_REJECTED`, `CANCELLED`, `INTERNAL_FAILURE`. Diese internen Status sind keine Behauptung bestimmter bisheriger numerischer Exitcodes. `CliOutcomeMapper.classify` verwendet die vom CompilationGate gelieferte RejectionSummary. ENVIRONMENT_FAILURE ergibt OPERATIONAL_FAILURE, INCOMPLETE_SUPPORT ergibt INCOMPLETE_ANALYSIS, MISSING_INPUT oder AMBIGUOUS_INPUT ergibt INPUT_UNRESOLVED, SOURCE_ERRORS ergibt SOURCE_REJECTED, eine alleinige WARNINGS_REJECTED-Ablehnung ergibt den gleichnamigen CLI-Status. Bei mehreren Gründen gilt diese Reihenfolge als Standardpriorität; alle Gründe bleiben im strukturierten Ergebnis erhalten. Ein Legacyprofil kann seine belegte Exitcodezuordnung darüberlegen. Der Adapter implementiert keine zweite Root-/Coverage-/Warnungsprüfung. Die endgültige SUCCESS-Einstufung erfolgt erst nach sämtlichen verpflichtenden Exporten und deren erfolgreichem Flush. Ein Fehler in dieser Abschlussphase ergibt OPERATIONAL_FAILURE und nennt gegebenenfalls bereits entstandene Teilartefakte.

## 3. Methodenverträge

### 3.1 CliApplication.run

**Vorbedingungen:** Argumente und Umgebung sind nullfrei; Streams sind offen; Profile sind geladen oder reproduzierbar ladbar. **Wirkung:** Darf über explizite Hostdienste Dateien/Repositories lesen und gewünschte Artefakte schreiben. **Nachbedingungen:** Liefert genau einen Abschlussstatus; strukturierter Laufstatus, Textmeldung und Exitcode stimmen überein. Die Methode ruft kein `System.exit` auf. **Besitz:** Sie schliesst keine vom Aufrufer bereitgestellten Standardstreams. Eigene Dateien/Responses werden geschlossen. **Abbruch:** laufende Beschaffung/Analyse/Export wird kontrolliert beendet; ein nur teilweise geschriebenes Artefakt wird nicht als vollständige Ausgabe angekündigt.

Die CLI erzeugt über ihre injizierte `RunContextFactory` einen Elternkontext für den gesamten angenommenen Aufruf. Vor der Engineausführung erzeugt sie `createChild(parent)` und verwendet `compile(request, reuse, child, token)`. Nur die Engine startet und beendet diesen Compilerkindlauf. Beschaffung und Export gehören zum Elternlauf oder erhalten bei Bedarf eigene klar zugeordnete Kinder. Der Elternlauf endet erst nach der verbindlichen Ausgabe und deren Flush; ein zuvor erfolgreicher Compilerkindlauf darf den späteren Exportfehler nicht verdecken. Hilfe und Version benötigen keinen künstlichen Compilerkindlauf. Kontextweitergabe erfolgt explizit gemäss Kapitel 09.

Ungültiger Quelltext ist ein regulärer `SOURCE_REJECTED`-Fall. Eine nicht lesbare Quelldatei ist ein IO-/Auflösungsfehler. Eine interne Invariante erzeugt `INTERNAL_FAILURE` und FailureId; die Anwenderdiagnose bleibt von technischem Stacktrace getrennt. Der technische Bericht kann auf ausdrücklichem Diagnoselevel den Stacktrace enthalten.

### 3.2 CommandLineParser.parse

Der Parser mutiert weder Argumentliste noch Umgebung. Er prüft unbekannte Optionen, fehlende Werte, gegenseitige Ausschlüsse, Mehrfachvorkommen und Positionsargumente. Reihenfolge und Wiederholungsverhalten stammen aus CommandSpec. Das Auftreten von `--` wird entsprechend dem Profil behandelt. Pfadstrings bleiben zunächst Strings; ihre Auflösung erfolgt in der Invocationplanung. Shell-Quoting wurde bereits durch die Shell verarbeitet und wird nicht erneut geraten.

### 3.3 ConfigurationLoader.load

Der neue explizite Standard ist CLI-Argument vor expliziter Konfigurationsdatei vor Profildefault. Bestehende abweichende Präzedenz gehört in das Legacyprofil. Environmentvariablen sind nur wirksam, wenn sie dokumentiert sind; zufällig gesetzte globale Proxy-/Localevariablen dürfen die Compilersemantik nicht ändern. Jede aufgelöste Einstellung besitzt eine Herkunft, damit ein Report erklären kann, warum sie aktiv war. Geheimnisse werden als vertrauliche Werte behandelt und nicht in Klartext serialisiert.

### 3.4 OutputPlanner.plan

Der Planner trennt Fachartefakt, fachliche Diagnostik und Betriebsereignisse. Er erkennt kollidierende Dateipfade und unvereinbare stdout-Nutzung vor dem Schreiben. Ein Fehler darf nicht ein bereits vorhandenes Nutzerartefakt mit einer leeren Datei ersetzen. Soweit das Ausgabeformat atomar erzeugbar ist, wird zunächst eine temporäre Datei im Zielverzeichnis geschrieben und anschliessend ersetzt; Ausnahmen für Streaming werden im konkreten Exportvertrag dokumentiert.

## 4. Legacyprofil als prüfbare Eingabe

`LegacyCliProfile` enthält Werkzeugname, Referenzversion/Hash, Optionsschema, Defaultwerte, Konfigurationspräzedenz, ExitCodePolicy, Streamzuordnung, Meldungskatalogversion, Format-/Whitespaceanforderungen und genehmigte Abweichungen. Es referenziert die in Kapitel 13 erfassten Fallresultate.

Der Agent inventarisiert mindestens:

- Hilfe, Version, leere Invocation und unbekannte Option;
- gültige und fehlerhafte einzelne Datei;
- mehrere Dateien und mehrere Modelle pro Datei;
- Modellnamenauswahl und Modellverzeichnisse/Repositories;
- Warnungen, stille/ausführliche Modi und Fehlerlimits;
- Generator-/Modellinformationsausgaben;
- nicht lesbare Quelle, fehlender Import und unbeschreibbares Ausgabeziel;
- Abbruch und internes Scheitern soweit kontrolliert reproduzierbar;
- Leerzeichen, Umlaute und plattformtypische Pfade.

Die bisherige Mainklasse und ihre Konfiguration sind eine Referenz für diesen Vertrag: [ili2c Main](https://github.com/claeis/ili2c/blob/master/src/main/java/ch/interlis/ili2c/Main.java). Ihre globale interne Organisation wird nicht übernommen.

## 5. Neue strukturierte Optionen

Strukturierte Ausgabe ist eine Erweiterung, deren Namen im CLI-Schema festzulegen sind. Vorgeschlagen sind getrennte Optionen für Diagnoseformat (`legacy`, `json`) und Betriebslogformat (`text`, `json`). Ihre exakten Namen dürfen nicht mit bestehenden Optionen kollidieren und müssen in Hilfe/Tests erscheinen.

JSON-Diagnostik und JSON-Logs besitzen jeweils ein dokumentiertes Schema. Falls beide denselben Stream verwenden sollen, müssen alle Zeilen einen eindeutigen Ereignistyp tragen und dieselbe NDJSON-Rahmung verwenden. Andernfalls verlangt der OutputPlan getrennte Senken. Ein generierter Modell-XML-/JSON-Export darf nicht mit Laufereignissen vermischt werden. Bei LSP bleiben die Regeln von Kapitel 07 massgeblich; CLI-Logging darf dessen stdout-Protokoll nicht verschmutzen.

Meldungsähnlichkeit wird über einen profilabhängigen Renderer erreicht. Diagnosecode und fachliche Ursache werden nicht aus einem Textvergleich abgeleitet. Zusätzliche Spalten-/Bereichsinformationen können strukturiert verfügbar sein, selbst wenn die Legacydarstellung nur Datei und Zeile ausgibt.

## 6. Java-SDK

Der kleinste Bibliotheksfall benötigt `interlis-compiler` und dessen model-Abhängigkeit: SourceText/SourceSet aus Strings aufbauen, CompilationRequest erstellen, `CompilerEngine.analyze` oder `compile` aufrufen und Resultat abfragen. Ein Workspace ist nur für Sitzungszustand/Wiederverwendung erforderlich. Repository- und LSP-Abhängigkeiten sind optional.

Die endgültigen Signaturen stehen in Kapitel 06/16. Beispielabläufe müssen im Repository als tatsächlich kompilierende Tests oder kleine Anwendungen vorliegen. Ein README-Snippet, das nicht mit den veröffentlichten Artefakten kompiliert, ist ein SDK-Fehler.

SDK-Versionierung unterscheidet Binär-/Quellkompatibilität der Java-API, semantische Verhaltensänderungen, Diagnosekatalog und Serialisierungs-/Bridgeversionen. Interne Packages `*.internal` sind nicht Teil des SDK-Vertrags. Selbst bei unveränderter Methodensignatur ist eine geänderte Attributreihenfolge oder Fehlersemantik eine relevante Verhaltensänderung. Vor stabiler Version 1.0 können API-Änderungen häufiger sein, benötigen aber Migrationshinweis und aktualisierte Beispiele.

## 6.1 Modell- und Schemaexporte

Modellinformationen und andere zugesagte ili2c-Generatorausgaben sind eigenständige Verbraucher. Die reine Prüfung einer Quelldatei erfüllt diese Ausgabeverträge nicht. Ihre gemeinsame Rolle liegt in `interlis-projection` unter `guru.interlis.projection.export`:

```java
public interface ModelExporter {
    ExportFormat format();
    ExportCapabilities capabilities();
    ExportOutcome export(CompiledSchema schema, ExportOptions options,
                         CancellationToken cancellation);
}
```

`ExportOptions` enthält Format-/Profilversion, explizite Modell-/Themenauswahl, Sprach-/Übersetzungsoptionen und fachliche Formatparameter. Es enthält keinen offen gehaltenen Dateistream und keine versteckten globalen Einstellungen. `ExportOutcome` unterscheidet `Produced(ExportDocument, List<ProjectionNotice>)`, `Unsupported(List<UnsupportedFeature>)`, `Cancelled(CancellationReason)` und `Failed(FailureId)`. Unvollständige oder semantisch nicht unterstützte Ausgabe darf nicht als Produced erscheinen. `ExportDocument` enthält Medienformat, Zeichensatz, geordnete logische Ausgabeteile und Quell-/Profilprovenienz. Dateischreiben übernimmt die CLI-/Hostschicht. Für grosse mehrteilige Exporte kann eine explizite Streaming-Variante ergänzt werden; sie darf Fehlerstatus und Ressourcenbesitz nicht verschleiern.

`ModelExporters.forFormat(ExportFormat format, ExportProfile profile)` liefert `ExporterSelection`. Diese sealed Variante besitzt `Available(ModelExporter exporter)` und `UnsupportedFormat(ExportFormat format, ExportProfile profile, UnsupportedReason reason)`. Alle Argumente und Werte sind nullfrei. Die Auswahl führt keine Modellanalyse, keine Dateisuche und keine Ausgabe aus. Eine vorhandene Registryfähigkeit garantiert die Unterstützung der Format-/Profilkombination; konkrete fehlende Modellfeatures werden weiterhin durch `export` anhand des Schemas als `ExportOutcome.Unsupported` ausgewiesen. Ein vollständig beendeter Export erzeugt genau eine Ergebnisvariante; ein unbekanntes Format wird nicht durch einen ähnlich benannten Standardexporter ersetzt.

Eine kleine explizite Registry genügt; es besteht keine Reflection-/ServiceLoader-Pflicht. `ExportFormat` enthält mindestens die vorgesehenen IlisMeta07- und IlisMeta16-Modellaustauschziele. Andere im freigegebenen ili2c-Profil tatsächlich genutzte Generatorformate, einschliesslich gegebenenfalls XSD-/Modelltextausgaben, werden in WP-30 inventarisiert und konkret ergänzt. Die Inventarliste muss jedes bestehende Format als zugesagt, bewusst ausserhalb des Profils oder mit begründeter Abweichung ausweisen; ein fehlender Eintrag ist keine implizite Scopeeinschränkung.

IlisMeta07 und IlisMeta16 sind die offiziellen Modellinformationsformate für 2.3 beziehungsweise 2.4: [INTERLIS-Metamodell](https://www.interlis.ch/modelle/metamodell). Die interne IDE-Syntax wird nicht einfach als deren XML serialisiert. Ein `IlisMeta07Exporter` beziehungsweise `IlisMeta16Exporter` projiziert das geprüfte semantische Modell, hält Referenzen/Reihenfolgen und Versionen korrekt und meldet Unsupported-Features vor Ausgabe.

Zu jedem zugesagten Format gehören ein positives semantisches Golden, ein Negativ-/Unsupported-Fall, ein Fall mit Importen/Übersetzung und ein Roundtrip beziehungsweise ein externer formatgerechter Leser, soweit das Format dies zulässt. Ein Bytevergleich darf nur tatsächlich irrelevante dokumentierte Metadaten normalisieren. Die Methode export führt keine erneute Compilersemantik aus.

## 7. Optionaler Adapter für alte Java-Anwendungen

Eine ähnliche Klasse mit `getName()` erfüllt keine binärkompatible `TransferDescription`. Der optionale Adapter muss einen konsistenten Objektgraphen mit den tatsächlichen bisherigen Typen, Beziehungen, Reihenfolgen und fachlichen Methoden liefern oder einen eindeutig kleineren unterstützten Umfang dokumentieren.

`LegacyTransferDescriptionAdapter.materialize(CompiledSchema schema, LegacyApiProfile profile)` erzeugt, falls dieser Auftrag erteilt ist, das Legacyobjekt in einer isolierten Abhängigkeitsschicht. Vorbedingungen sind gültiges Schema und unterstützte Features; Ergebnis ist ein eigener alter Objektgraph. Eine Mutation dieses Graphen darf das neue immutable Schema nicht verändern. Ununterstützte Konstrukte werden vor Auslieferung des Graphen gemeldet.

Der Adapter darf alte Bibliotheken nur im dafür vorgesehenen Modul referenzieren. Er muss gegen echte Verbraucher wie ausgewählte iox-ili-/ili2db-Funktionen getestet werden. Classloader-/Package-/Artefaktkonflikte werden ausdrücklich geprüft. Ein alleiniger Methodennamenvergleich oder erfolgreiches Parsen genügt nicht. Der Kern bleibt auch ohne diesen Adapter nutzbar.

## 8. Anforderungen

| ID | Normative Anforderung | Phase | Abnahme |
|---|---|---|---|
| CLI-001 | CLI und SDK MÜSSEN dieselbe CompilerEngine verwenden. | P1 | Fachlich identische normalisierte Resultate. |
| CLI-002 | Nur der Prozesseinstieg DARF System.exit verwenden; die Anwendung MUSS als Bibliothek testbar sein. | P1 | In-Process-Tests fangen Resultate und Streams ohne JVM-Ende auf. |
| CLI-003 | Bestehende Optionen MÜSSEN erfasst und entweder unterstützt oder explizit abgelehnt werden. | P3 | Referenzmatrix; kein stilles Ignorieren. |
| CLI-004 | Exitcodes, Streams und Meldungen MÜSSEN zum freigegebenen Profil passen. | P3 | Prozessvergleich auf zugesagten Plattformen. |
| CLI-005 | Konfigurationspräzedenz MUSS eindeutig und erklärbar sein. | P1/P3 | Konfliktfälle und Herkunftsreport. |
| CLI-006 | Fachartefakte, Diagnostik und Betriebslogs MÜSSEN eindeutig getrennt bzw. gerahmt sein. | P1 | XML-/JSON-Export bleibt parsebar; NDJSON ohne Fremdtext. |
| CLI-007 | Ein fehlgeschlagener Lauf DARF kein unvollständiges Artefakt als Erfolg ausweisen. | P1/P3 | IO-Fehler/Abbruch mit vorhandener Zieldatei. |
| SDK-001 | Der SDK-Grundfall MUSS rein aus Strings und expliziten Optionen ausführbar sein. | P1 | Kompilierender Beispiel-/Vertragstest ohne Dateisystem. |
| SDK-002 | Öffentliche SDK-Beispiele MÜSSEN mit den ausgelieferten Artefakten getestet werden. | P1/P3 | Beispielkompilierung in CI. |
| SDK-003 | API-, Diagnose- und Protokollversionen MÜSSEN getrennt nachvollziehbar sein. | P3 | Versionsmanifest/Migrationsfall. |
| SDK-004 | Zugesagte Modellgeneratoren MÜSSEN einen konkreten Exportervertrag und eine vollständige Format-/Capabilityliste besitzen. | P3 | Formatgoldens, Unsupported-Fälle und Referenzvergleich je freigegebenem Format. |
| LEGACY-001 | Alte Java-API-Kompatibilität MUSS als eigener geprüfter Adapterumfang ausgewiesen werden. | optional/P3 | Echte Verbraucher benutzen materialisierten Graphen. |
| LEGACY-002 | Ein alter mutabler Objektgraph DARF das neue Schema nicht verändern. | optional/P3 | Mutations-/Isolationstest. |

## 9. Erweiterbarkeit für weitere Einbettungen

Ein späteres Java-Native-Image, Node-Addon, C-ABI oder Python-Binding kann dieselben expliziten Requests und Resultatprojektionen nutzen. Das ist keine P1-Lieferpflicht. Binäre Foreign-Function-Handles brauchen eine eigene Lebensdauer-/Fehler-/Threading-Spezifikation und dürfen die vorhandenen Snapshotverträge nicht umgehen. Der vorhandene JSON-/WASM-Vertrag ist eine mögliche fachliche Grundlage, aber keine automatische ABI-Kompatibilität zu C++-Verbrauchern.
