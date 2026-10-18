# Modellauflösung und Repositories

## 1. Ziel und Grenze

Modellbeschaffung und Sprachbindung sind getrennte Aufgaben. Der Resolver beantwortet, welche konkrete Modellversion aus welcher Quelle einen Import erfüllen soll. Der Binder prüft anschliessend, ob diese Auswahl und ihre Verwendung nach INTERLIS zulässig sind. Resolvermetadaten sind kein Beweis sprachlicher Gültigkeit.

Die Compiler-Engine erhält ein unveränderliches `SourceSet`. Sie öffnet keine Datei und kontaktiert keinen Server. Der Workspace-Resolver arbeitet auf einem eingefrorenen Inventar. Ein Host kann fehlende Quellen ausdrücklich nachladen und anschliessend eine neue Analyse anfordern. Damit funktioniert derselbe Kern in JVM, Browser, Tests und Offline-Werkzeugen.

Die bisherigen Projekte zeigen den Nutzen einer expliziten Resolvergrenze: [ilic-fork Modulgrenzen](https://github.com/edigonzales/ilic-fork/blob/main/docs/architecture/module-boundaries.md), [GeoWerkstatt InterlisReader](https://github.com/geowerkstatt/GeoW.Interlis.Tools/blob/main/src/Geowerkstatt.Interlis.Compiler/InterlisReader.cs). Der folgende API-Vertrag ist der neue Java-Entwurf.

## 2. Identitäten und Versionsbegriffe

Es sind mindestens fünf verschiedene Begriffe zu erhalten:

| Begriff | Beispiel/Bedeutung | Behandlung |
|---|---|---|
| Sprachversion | INTERLIS 2.3 oder 2.4 | `LanguageVersion`, beeinflusst Grammatik und Semantik |
| Modellversion | Versionsangabe im MODEL-Kopf | Originalstring, keine automatische Semver-Interpretation |
| Quellenrevision | Änderung eines konkreten Dokuments | `SourceRevision`, vom Workspace vergeben |
| Repositoryrevision | Stand eines Indexes/Resolverkatalogs | opaker Fingerprint, beeinflusst Auswahl und Caches |
| Tool-/Profilversion | Compiler, Grammatik, Legacyauflösung | Bestandteil reproduzierbarer Eingaben |

Ein Dateiname ist nicht der Modellname. Eine Datei kann mehrere Modelle enthalten. Ein Modell kann auf mehreren Servern angeboten werden. Die Quellidentität ist die vom Host kanonisierte logische URI; ihre Gleichheit führt keine Netzwerkoperation aus. Dateipfadnormalisierung, Gross-/Kleinschreibung, Symlinks und Arbeitsverzeichnis sind explizite Hostregeln. Ein `file:`-URI wird nicht nach Windowsregeln auf Linux umgeschrieben. Eine `https:`-URI wird nicht durch ungeprüfte Queryparameterentfernung verändert.

## 3. Compiler-DTOs für aufgelöste Inputs

Folgende Typen liegen unter `guru.interlis.compiler`, damit compiler unabhängig vom workspace bleiben kann. `SourceRange`, `SourceText` und Diagnosewerte stammen aus model. Verschachtelte Recordvarianten sind in ihrer jeweiligen sealed interface zu deklarieren.

```java
public sealed interface ModelSelector {
    record ByName(String modelName, Optional<String> modelVersion,
                  Optional<SourceId> sourceHint) implements ModelSelector {}
    record BySource(SourceId source) implements ModelSelector {}
}

public record ModelLocation(SourceId source, SourceRevision revision,
                            String modelName, Optional<String> modelVersion,
                            int declarationOffset) {}

public record ImportSite(SourceRange nameRange, String importingModel,
                         String importedModel) {}

public record ImportBinding(ImportSite site, ImportResolution resolution) {}

public sealed interface ImportResolution {
    record Resolved(ModelLocation target) implements ImportResolution {}
    record Missing(ResolutionProblem problem) implements ImportResolution {}
    record Ambiguous(List<ModelLocation> candidates,
                     ResolutionProblem problem) implements ImportResolution {}
    record Blocked(ResolutionProblem problem) implements ImportResolution {}
}

public record ResolutionProblem(DiagnosticId diagnosticId,
                                ResolutionReason reason,
                                String safeDetail) {}
```

`ResolutionReason` besitzt mindestens `NOT_FOUND`, `AMBIGUOUS`, `OFFLINE`, `ACCESS_DENIED`, `IO_FAILURE`, `INVALID_SOURCE`, `VERSION_MISMATCH`, `STALE_SOURCE`, `BUDGET_EXCEEDED`. `safeDetail` ist für Benutzer und Logs geeignet und enthält keine Zugangsdaten. `Ambiguous` enthält mindestens zwei unterschiedliche zulässige Kandidaten in deterministischer Reihenfolge. `Blocked` bezeichnet einen technischen/policybezogenen Auflösungsfehler, nicht einen erfundenen fehlenden Modellnamen.

`SourceSet.of(Map<SourceId,SourceText> sources, List<ImportBinding> imports, List<Diagnostic> resolutionDiagnostics, ResolutionFingerprint fingerprint)` erstellt eine defensive unveränderliche Eingabemenge. Der Map-Key muss zur SourceId im Wert passen. Für `Resolved` muss die Zielquelle vorhanden und in derselben Revision enthalten sein. Die Engine prüft Deklarationsoffset, Modellname und Modellversion nochmals gegen die tatsächliche Syntax. Der vom Aufrufer übergebene ResolutionFingerprint ist Resolverprovenienz, kein vertrauenswürdiger Beweis der tatsächlichen Quellenidentität. SourceSet-/Compilation-Fingerprinter berechnen die Identität zusätzlich selbst aus konkreten Texten, Bindungen und Optionen. Gleicher behaupteter Resolverhash bei verändertem Text oder Importziel darf keinen Cachetreffer erzeugen. Veraltete oder unpassende Bindungen ergeben einen expliziten ungültigen Eingabestand; sie werden nicht an ähnlich benannte Deklarationen angepasst.

`BySource` wählt alle Originalmodelle einer expliziten Datei. Ein fehlerhafter Dateikopf darf dadurch nicht unsichtbar werden. `ByName` wählt einen Modellnamen mit optionaler exakter Modellversion und Quellenhinweis. Das Builtin-Modell INTERLIS stammt aus der passenden compilerinternen Sprachfassung und wird nicht unbemerkt aus einem Repository überschrieben.

## 4. Reiner Resolver im Workspace

Paket: `guru.interlis.workspace.resolution`.

```java
public interface SourceProvider {
    SourceInventory snapshot();
}

public interface ModelResolutionService {
    ResolutionOutcome prepare(ResolutionRequest request,
                              SourceProvider provider,
                              CancellationToken cancellation);
}
```

`SourceProvider.snapshot()` liefert ausschliesslich bereits vorhandene Daten. Es darf keine IO-Aktion verstecken. `SourceInventory` enthält eine feste `WorkspaceRevision`, unveränderliche `SourceText`-Werte, einen Kopfindex und einen eingefrorenen Resolverkatalog. Neue Texte erhalten ihre Revision über den Workspace; ein Provider darf keine Revision vergeben.

### 4.1 Trägerklassen

| Klasse | Felder bzw. Methoden | Vertrag |
|---|---|---|
| `SourceInventory` | `workspaceRevision()`, `sources()`, `headers()`, `catalog()`, `fingerprint()` | defensive unveränderliche Sicht; Kopfindex nennt exakte Quellrevisionen |
| `ResolutionRequest` | `roots()`, `policy()`, `budget()`, `expectedInventoryFingerprint()` | explizite Rootauswahl und Auswahlregeln |
| `ModelHeader` | Modellname, rohe Modellversion, Sprachversion, Deklarationsbereich, erkannte Imports, Kopfzustand | fehlertolerantes Indexergebnis; kein Semantikzertifikat |
| `ModelCandidate` | Name/Version, logische Quelle, Herkunft, optionaler Inhaltsdigest, Prioritätsgruppe | vollständiger Provenienzbezug; lokale Verfügbarkeit gesondert |
| `PendingSourceRequest` | gesuchtes Modell, mögliche Locator, Ursache, abhängig machende Importstellen | Vorschlag an Host, löst selbst nichts aus |
| `ResolutionTrace` | geordnete Auswahlentscheidungen mit Gründen | erklärt Auswahl/Mehrdeutigkeit; keine geheimen Headers |
| `ResolvedClosure` | `sourceSet()`, `dependencies()`, `negativeDependencies()`, `trace()` | alle zur fachlichen Prüfung benötigten Quellen vorhanden |
| `IncompleteClosure` | `partialSourceSet()`, `pendingFetches()`, `dependencies()`, `negativeDependencies()`, `trace()` | für Teilanalyse nutzbar; betroffene Roots nicht vollständig |

`ResolutionOutcome` ist sealed mit `PreparedClosure`, `ResolutionCancelled` und `ResolutionFailed`. `PreparedClosure` ist wiederum sealed mit `ResolvedClosure` und `IncompleteClosure`. Beide vorbereiteten Varianten besitzen `SourceSet sourceSet()`, `List<PendingSourceRequest> pendingFetches()`, `ResolutionTrace trace()`, `List<ResolutionDependency> dependencies()` und `List<ResolutionDependency> negativeDependencies()`. Bei vollständiger Auflösung ist pendingFetches leer; bei unvollständiger Auflösung bezeichnet sourceSet denselben partiellen Stand wie partialSourceSet(). `WorkspaceCapture.resolution()` stellt diese vorbereitete Closure dem Host und der Bridge öffentlich bereit. `ResolutionDependency` enthält die gesuchte ModelSelector-Auswahl, betroffene Importstellen, geprüfte Kandidaten/Zielauswahl, den Katalogfingerprint und die Art POSITIVE, NEGATIVE oder AMBIGUOUS. Alle Listen sind geordnet und unveränderlich. dependencies enthält alle Einträge; negativeDependencies ist daraus die Sicht auf negative und mehrdeutige Lookupabhängigkeiten.

`ResolvedClosure` sagt nur „Beschaffung vollständig“; Syntax-/Typfehler werden erst im Compiler beurteilt. Ein technisch fehlgeschlagener Resolverlauf erhält `FailureId` und strukturierte Diagnose statt einer leeren erfolgreichen Quellmenge.

### 4.2 prepare-Vertrag

**Vorbedingungen:** nullfreie unveränderliche Eingaben, Fingerprint passt zum Inventar, mindestens eine Rootauswahl, positive sinnvolle Limits. **Nachbedingungen:** Die Methode mutiert weder Provider noch Workspace; Auswahl und Trace sind deterministisch. Ein vollständiges Ergebnis enthält jede benötigte Quelle genau einmal, aber alle benötigten Modelle einer Datei. **Fehler:** ungültige API-Eingaben führen zu gezielter Argument-/Stale-Input-Ausnahme; fehlende Modelle und Mehrdeutigkeit sind fachliche Resultate. **Abbruch:** zwischen Kandidatenlisten, Headeranalysen und Graphschritten prüfen; Teilresultate werden nur als abgebrochene/unvollständige Variante ausgegeben. **Besitz:** Resultate besitzen unveränderliche Daten; keine offenen Streams oder HTTP-Responses.

### 4.3 Interne Klassen

| Klasse | Kernmethoden | Algorithmischer Vertrag |
|---|---|---|
| `ModelHeaderIndexer` | `index(SourceText, ParseOptions, CancellationToken)` | nutzt geprüfte Syntax-/Tokeninfrastruktur; unabhängige zweite Regexgrammatik ist unzulässig |
| `CandidateSelector` | `select(ModelQuery, List<ModelCandidate>, ResolutionPolicy)` | Filter nach exakt verlangter Version/Quelle, dann profilierte Priorität; gleichwertige Mehrdeutigkeit explizit |
| `ClosureBuilder` | `build(roots, inventory, policy, token)` | Iteration bis Fixpunkt; visited-Menge auf konkreten Quellen/Modellen, begrenzte Queue |
| `ImportBindingAssembler` | `bind(ImportSite, SelectionResult, SourceInventory)` | erzeugt nur überprüfbare ModelLocation-Werte aus vorhandenen Texten |
| `ResolutionFingerprinter` | `fingerprint(inventory, roots, policy, traceInputs)` | kanonische, längenpräfixierte Darstellung aller fachlich relevanten Auswahlinputs |

Importzyklen beenden die Beschaffung durch die visited-Menge; ihre sprachliche Zulässigkeit ist Aufgabe der Semantik. Ein rein technischer Zyklus im Repositoryindex erzeugt weder Endlosschleife noch pauschalen Sprachfehler.

## 5. Auswahlregeln

Für das neue Standardprofil gilt:

1. Ein geöffneter Workspace-Text überschreibt den gespeicherten Inhalt derselben logischen SourceId. Ein Download darf diese Bearbeitung nicht zurücksetzen.
2. Ein expliziter Quellenhinweis bindet an diese Quelle. Ein anderes gleichnamiges Modell wird nicht still als Ersatz genommen.
3. Eine explizite Modellversion wird exakt abgeglichen. Datums- oder Semver-Sortierung wird nicht aus dem String geraten.
4. Explizit übergebene Quellen und konfigurierte Repositoryprioritäten werden in der dokumentierten Reihenfolge ausgewertet.
5. Innerhalb einer gleichrangigen Auswahlgruppe bleiben mehrere unterschiedliche passende Definitionen mehrdeutig. Gleichheit wird durch geprüften Inhalt und Modellidentität entschieden, nicht nur Dateiname.
6. Ein Legacyprofil kann die Referenzauswahlregel abbilden. Es muss seine Prioritäten und Konfliktfälle in Tests belegen.

Die endgültige Rootliste und Importauswahl werden in der Laufprovenienz festgehalten. Es gibt keinen unkontrollierten automatischen „neuestes Modell“-Fallback. Eine Suchfunktion darf mehrere Versionen anzeigen; die Kompilierung muss ihre konkrete Auswahl offenlegen.

## 6. JVM-Host für Dateien und HTTP

Paket `guru.interlis.repository`, Modul `interlis-repository`. Diese APIs dürfen IO ausführen und werden durch CLI/LSP-Hosts bewusst aufgerufen.

| Klasse/Interface | Methode | Resultat/Fehlervertrag |
|---|---|---|
| `RepositoryClient` | `discover(ModelQuery, RepositoryConfig, CancellationToken)` | Kandidaten plus Provenienz; Netzwerkfehler getrennt von fachlich leerer Trefferliste |
| `SourceLoader` | `load(SourceLocator, FetchPolicy, CancellationToken)` | `LoadedSource` oder typisierter Ladefehler; keine Quellenrevision vergeben |
| `LoadedSource` | `sourceId()`, `text()`, `byteProvenance()`, `origin()` | text exakt dekodiert; schreibgeschützte Daten; kein Live-Stream |
| `FileSourceLoader` | implementiert `load` | feste Pfad-/Symlink-/Zeichensatzpolitik, begrenzte Dateigrösse |
| `HttpSourceLoader` | implementiert `load` | expliziter HTTP-Client, Timeout, Redirect-/Grössenlimits, Abbruch |
| `RepositoryIndexReader` | `read(IndexPayload, RepositoryBase)` | standardkonforme Indexauswertung und sichere relative URI-Auflösung |
| `ContentStore` | `lookup(ContentDigest)`, `put(VerifiedContent)` | Cache kann fehlen; Treffer nur bei passendem Digest, atomare Speicherung |
| `RepositoryRefreshService` | `refresh(RepositoryConfig, previousState, token)` | neuer unveränderlicher Katalog mit Revision; keine Mutation laufender Analysen |

`FetchPolicy` benennt `offline`, Connect-/Requesttimeout, maximale Bytes, Redirectlimit und Cacheverhalten. Die Standardwerte werden im JVM-Hostprofil dokumentiert und sind pro Anwendung konfigurierbar. Der Kern kennt keine HTTP-Authentisierung. Tokens, Cookies oder signierte Querywerte dürfen weder zu DiagnosticArgument noch in öffentliche ResolutionTrace-Werte gelangen.

Originalbytes und Java-Text haben getrennte Digests: `byteProvenance.sha256` gilt für geladene Bytes. `SourceText.contentFingerprint()` gilt gemäss Kapitel 04 für UTF-16-BE-Codeeinheiten ohne BOM. Dekodierungsfehler werden gemeldet, nicht durch stilles Ersetzen unbekannter Bytes im Modell behoben. BOM-/Zeichensatzregeln für Legacyprofile werden als Inputvertrag getestet.

## 7. Ablauf mit fehlendem Remote-Modell

1. Workspace enthält das bearbeitete Rootmodell. Der Host verwendet Workspace.capture(request,token); die vorbereitete Closure und ihre PendingSourceRequests sind über capture.resolution() öffentlich zugänglich.
2. `prepare` findet den Import, kann dessen Quelle noch nicht liefern und gibt `IncompleteClosure` plus `PendingSourceRequest` zurück.
3. Teilanalyse und LSP können den fehlenden Import sofort anzeigen.
4. Der Host verwendet `RepositoryClient`/`SourceLoader` ausserhalb einer Workspace-Mutationssperre.
5. Die geladenen Materialien werden mit erwarteter Revision atomar oder in überprüften Updates übernommen. Der Workspace vergibt die Quellenrevisionen. Bereits geänderte Editorquellen werden erhalten.
6. Der Host bildet ein neues Inventar und ruft `prepare` erneut auf.
7. Erst dessen eingefrorenes SourceSet wird kompiliert. Ein währenddessen veralteter Lauf darf weiter fertig werden, aber nicht als aktuell veröffentlicht werden.

`Workspace.applyBatch(List<SourceUpdate>, WorkspaceRevision expected)` ist die atomare Ergänzung zu einzelnen Updates: alle Erwartungen und Textänderungen zuerst prüfen, dann Revisionen vergeben und alle Änderungen gemeinsam übernehmen. Bei einem Konflikt bleibt der ganze Batch unverändert und der Host bereitet ihn anhand des aktuellen Stands neu vor. Ein Download braucht keine neue Revision, wenn der identische Text derselben Quelle bereits vorhanden ist. Eine Wiederanlage nach Löschen verwendet den Revisionszähler der Tombstone-Historie weiter.

## 8. Cache und negative Abhängigkeiten

Ein fehlgeschlagener Lookup speichert eine Abhängigkeit auf Modellname, explizite Version, Prioritäts-/Katalogstand und Suchscope. Wenn später ein passendes Modell hinzukommt, müssen betroffene Analysen invalidiert werden. Negative Caches benötigen entweder einen kataloggebundenen Fingerprint oder eine festgelegte Gültigkeit im Host; ein dauerhaftes `not found` ohne Revision ist unzulässig.

Ein Headercache wird durch exakten Textfingerprint, Quellrevision, Grammatik-/Parseprofil invalidiert. Resolvercaches berücksichtigen zusätzlich Wurzeln, Auswahlpolicy, Repositoryrevision und Workspace-Overrides. Compiler-Semantikcaches berücksichtigen die resultierenden Bindungen. Gleichbleibende öffentliche Namen allein reichen nicht aus: eine geänderte Domänengrenze oder ein Constraint kann nachgelagerte Ergebnisse verändern.

## 9. Anforderungen und Abnahmen

| ID | Normative Anforderung | Phase | Abnahme |
|---|---|---|---|
| RES-001 | Engine und SourceProvider-Snapshot DÜRFEN keine implizite IO ausführen. | P1 | In-Memory- und Browserlauf; fehlende Quelle wird als PendingSourceRequest sichtbar. |
| RES-002 | Sprache, Modellversion, Quellenrevision und Repositoryrevision MÜSSEN getrennt sein. | P1 | Modelle mit gleichem Namen/verschiedenen Versionsstrings werden korrekt gewählt oder als mehrdeutig gemeldet. |
| RES-003 | Mehrere Modelle je Datei MÜSSEN korrekt auswählbar sein. | P1 | ByName/BySource und Importbindung testen unterschiedliche Closure. |
| RES-004 | Auflösungsentscheidungen MÜSSEN deterministisch und erklärbar sein. | P1 | Gleicher Katalog trotz anderer Mapiteration ergibt gleichen Trace und Resultat. |
| RES-005 | Die Engine MUSS Resolverbindungen gegen die tatsächliche Quelle prüfen. | P1 | Manipulierter Offset oder falsche Revision wird verworfen. |
| RES-006 | Neue oder entfernte Modelle MÜSSEN positive und negative Lookupabhängigkeiten invalidieren. | P1 | Fehlender Import erscheint später ohne Edit des importierenden Textes. |
| RES-007 | Workspaceänderungen während eines Downloads MÜSSEN erhalten bleiben. | P1 | Veralteter Batch wird atomar abgewiesen; Editoroverride bleibt aktuell. |
| RES-008 | Offlinebetrieb MUSS mit eingefrorener Closure möglich sein. | P1 | Alle erforderlichen Modelle liegen vor; kein Netztestzugriff. |
| RES-009 | Datei-/HTTP-Adapter MÜSSEN Limits, Abbruch und konkrete Fehlerursachen besitzen. | P2 | Timeout, 404, 403, defekte Kodierung, Redirectzyklus und grosse Antwort separat geprüft. |
| RES-010 | Byte- und Textfingerprints MÜSSEN sauber getrennt und korrekt berechnet werden. | P1 | UTF-8/UTF-16/CRLF-/BOM-Provenienztests. |
| RES-011 | Repositoryindex-Leser MÜSSEN echte Indexfixtures und Referenzprofile unterstützen. | P2 | Gepinnte eigene/offizielle Indexdateien; keine ad-hoc HTML-Suche als Compilerresolver. |
| RES-012 | Zyklen und grosse Closures MÜSSEN terminieren oder am definierten Budget enden. | P1 | Kreisimport, Doppelimport und Modellzahlgrenze mit deterministischem Resultat. |

## 10. Konkrete Vertragstests

`ResolutionContractTest` enthält mindestens: explizite Quelle schlägt gleichnamigen Serverkandidaten; Workspace-Override schlägt gespeicherten Text; zwei gleichrangige Versionen ohne Auswahl sind mehrdeutig; exakte Version wählt genau eine Definition; ein gefundener Importname mit falschem Header wird beanstandet; zwei Modelle in einer Datei teilen Textbesitz, behalten eigene Positionen; missingImport wird nach SourceUpdate aufgelöst; eine entfernte Abhängigkeit wird über die Closure invalidiert; verspäteter Download überschreibt keine Editoränderung; gleiche Texte behalten Revisionen; sourceRange mit alter Revision wird nicht verwendet.

HTTP-Integrationstests verwenden einen lokalen kontrollierten Server. Der Test des Auswahlalgorithmus verwendet nur SourceInventory. Dadurch ist klar, ob ein Fehler aus IO, Modellbeschaffung oder Sprachbindung stammt.
