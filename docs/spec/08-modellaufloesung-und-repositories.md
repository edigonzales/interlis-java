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

Der Konstruktor von `SourceInventory` erhält ab Vorab-API 0.2 neben Revision,
Quellen und Katalog eine `Map<SourceId, SyntaxSnapshot>` mit bereits geparsten
Quellen. Er führt keine Parserarbeit aus. Für jede Quelle müssen ID, Revision
und Originaltext mit dem Snapshot übereinstimmen; fehlende beziehungsweise
widersprüchliche Eingaben werden abgewiesen. `syntax()` stellt die defensiv
kopierte, unveränderliche Zuordnung bereit. Die Header werden daraus in
kanonischer Quellen- und anschliessender Deklarationsreihenfolge abgeleitet.
Parsing und Closurevorbereitung finden ausserhalb des Workspace-Monitors statt;
die Übernahme von Wiederverwendungsdaten bleibt an `acceptAnalysis` gebunden.

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
5. Im reinen Workspace-Resolver bleiben innerhalb einer gleichrangigen Auswahlgruppe mehrere unterschiedliche passende Definitionen mehrdeutig. Gleichheit wird durch geprüften Inhalt und Modellidentität entschieden, nicht nur Dateiname.
6. Der JVM-Beschaffungshost verwendet standardmässig das unten beschriebene ili2c-5.6.9-Profil. Explizite Auswahlen und Version-1-Locks behalten ihre eigenen Einschränkungen.

Die endgültige Rootliste und Importauswahl werden in der Laufprovenienz festgehalten. Es gibt keinen aus Versionsstrings geratenen „neuestes Modell“-Fallback; der Host folgt der expliziten Vorgängerkette. Eine Suchfunktion darf mehrere Versionen anzeigen; die Kompilierung muss ihre konkrete Auswahl offenlegen.

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

`FetchPolicy` benennt `offline`, `maxBytes`, `connectTimeout`, `readTimeout` und optionalen `requestTimeout`. Die Defaults und Methodenbedingungen stehen in §15. Redirects sind auf fünf begrenzt; Cache und Proxy werden separat im Host konfiguriert. Der Kern kennt keine HTTP-Authentisierung. Tokens, Cookies oder signierte Querywerte dürfen weder zu DiagnosticArgument noch in öffentliche ResolutionTrace-Werte gelangen.

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
| RES-013 | Der JVM-CLI-Host MUSS standardmässig einen persistenten Repositorycache verwenden, online gemäss §17 Cachefristen und veröffentlichte Modell-MD5 vor einer Beschaffung prüfen und explizit offline auch ohne Lockfile aus dem lokalen Repositorystand auflösen können. | P2 | Erstdownload, 24h-/7Tage-Fristen, MD5 vor Download, Abschaltung, Offline ohne Netz, Lockreplay und korrupte/parallel geschriebene Einträge. |

## 10. Konkrete Vertragstests

`ResolutionContractTest` enthält mindestens: explizite Quelle schlägt gleichnamigen Serverkandidaten; Workspace-Override schlägt gespeicherten Text; zwei gleichrangige vorhandene Workspaceversionen ohne Auswahl sind mehrdeutig; exakte Version wählt genau eine Definition; ein gefundener Importname mit falschem Header wird beanstandet; zwei Modelle in einer Datei teilen Textbesitz, behalten eigene Positionen; missingImport wird nach SourceUpdate aufgelöst; eine entfernte Abhängigkeit wird über die Closure invalidiert; verspäteter Download überschreibt keine Editoränderung; gleiche Texte behalten Revisionen; sourceRange mit alter Revision wird nicht verwendet.

HTTP-Integrationstests verwenden einen lokalen kontrollierten Server. Der Test des Auswahlalgorithmus verwendet nur SourceInventory. Dadurch ist klar, ob ein Fehler aus IO, Modellbeschaffung oder Sprachbindung stammt.

`TRANSLATION OF` begründet eine Prüfabhängigkeit auf das Originalmodell und
dessen Import-Closure, ohne dessen Namensraum implizit zu importieren.
Die Vorbereitung verwendet die vorhandenen `SourceInventory.syntax()`-Snapshots;
es erfolgt kein zusätzliches Parsing. Positive, fehlende und mehrdeutige
Originale erhalten `ResolutionDependency`-Einträge ohne künstliche ImportSites.
Fehlende Originale bleiben als `PendingSourceRequest` sichtbar. Ein später
hinzugefügtes Original oder Änderungen seiner Imports werden neu vorbereitet
(RES-003/006). `WorkspaceTest` prüft Quellen in getrennten Dateien, Fehler und
anschliessende Korrektur sowie das spätere Hinzufügen des Originals.

L3-1 ergänzt ResolutionRequest.overlays mit SemanticOverlays; der bisherige
vierparametrige Konstruktor verwendet empty(). Workspace-Captures reichen
dieselbe Compilerkonfiguration an die Vorbereitung weiter. Extern konfigurierte
Translationen erzeugen wie Quelltranslationen Originalabhängigkeiten samt
transitiven Imports, ohne Originalnamensräume zu importieren. Auswahl und
negative Abhängigkeiten berücksichtigen exakte Version/SourceHint; der
Compiler überprüft die tatsächliche fachliche Zielbindung selbst.

L3-1: SourceInventory.fingerprint ist eine längenpräfixierte UTF-16-Inhaltsidentität
(inventory-v2) aus SourceIds, Quellrevisionen/-inhalten und ResolverCatalog; der
allgemeine WorkspaceRevision-Zähler gehört nicht hinein. Captures prüfen ihn
weiterhin getrennt. ResolverCatalog.fingerprint verwendet ebenfalls sortierte
längenpräfixierte Inhalte statt kollisionsanfälliger key=value-Verkettung.
Overlay-Rückkehr A→B→A bei unveränderten Quellen stellt dieselbe fachliche
Identität wieder her, ohne ein verspätetes Capture akzeptieren zu dürfen.

## 11. L3-2: durchgehende, bedingte Hostbeschaffung

`ResolutionPolicy(boolean preferWorkspaceSource, Map<String,ModelSelector.ByName>
modelSelections, Map<SourceId,Integer> sourcePriorities)` ist unveränderlich und
nullfrei. Der bisherige Einparameterkonstruktor bleibt erhalten. `qualify` ergänzt
fehlende Einschränkungen; `select` schneidet angeforderte und konfigurierte exakte
Version/Quelle und wählt erst danach die niedrigste Prioritätszahl. Nicht gerankte
Workspacequellen haben bei `preferWorkspaceSource=true` Rang -1, sonst 0.
Unterschiedliche Definitionen derselben Gewinnergruppe bleiben mehrdeutig.
`BySource` bezeichnet weiterhin sämtliche Modelle der Datei. Für eindeutige
benannte Roots übergibt der Workspace die tatsächlich gewählte Version/SourceId
an den Compiler. Weder Modellversionsstrings noch Sprachausgaben werden als
„Latest“ interpretiert; beide unterstützten Sprachfassungen bleiben indexierbar.

Der reine Resolver erzeugt auch für fehlende/mehrdeutige benannte Roots negative
Abhängigkeiten und PendingSourceRequests. Seine Header-/Kandidatenscans und
Closureiteration prüfen Abbruch und `maxSemanticWorkUnits`; Überschreitung ergibt
`ResolutionCancelled(RESOURCE_LIMIT)`. Negative Abhängigkeiten gehören auch zur
vollständigen `dependencies`-Sicht der unvollständigen Closure. Der resultierende
`resolved-input-v2`-Fingerprint enthält längenpräfixiert Inventar, Policy, Roots
und Overlays. Die reine Inhaltserkennung des Inventars bleibt davon getrennt.

**Originalbindungen:** `guru.interlis.compiler.TranslationBinding(translated,
original)` enthält zwei `ModelLocation`-Werte. `SourceSet.translations()` und die
fünfparametrige `SourceSet.of`-Fabrik transportieren diese unveränderlichen Werte;
bisherige Fabriken verwenden eine leere Liste. Der Resolver erzeugt sie sowohl
für `TRANSLATION OF` als auch für externe Translation-Overlays. Der Compiler
prüft Quelle, Revision, Name, Version und tatsächlichen Deklarationsoffset sowie
den erklärten/konfigurierten Zusammenhang erneut. Veraltete, widersprüchliche
oder erfundene Bindungen ergeben `ILI-RES-0007` und `MISSING_INPUT`, ohne stillen
Namensfallback. Eine geprüfte Originalbindung importiert keinen Namensraum;
dieselbe Identität erreicht `ModelTranslation.sourceTarget` und die gemeinsame
Korrespondenzprüfung. Unaufgelöste/mehrdeutige Originale behalten ihre bisherigen
Diagnosen. `CompilationRequest.fingerprint()` nutzt dieselbe kanonische
Compileridentität wie die Engine (`compilation-v3`, einschliesslich tatsächlicher
Originalbindungen, unabhängig von Parsemodus/Wiederverwendung).

`RepositoryClosureService(SourceLoader,ContentStore).resolve(Workspace,
AnalysisRequest,RepositoryResolveOptions,CancellationToken)` liegt ausschliesslich
im JVM-Hostmodul. Es nimmt einen Capture, verarbeitet Quellen in einem privaten
Workspace mit dem gemeinsamen Parser/Resolver und lädt Indizes erst nach, wenn
Quellen fehlen. Bereits vollständige eingefrorene Closures benötigen auch offline
keinen Providerzugriff. Neue Texte werden erst mit dem zurückgegebenen Batch im
Zielworkspace wirksam. Erfolgreiche Updates veröffentlichen keine provisorischen Revisionen. Ablehnungen können unveränderliche heruntergeladene Texte mit ihren privaten Quellrevisionen zur Diagnoseanzeige liefern; ursprüngliche Quellen behalten dabei ihre Originalrevision.

Alle folgenden Typen liegen unter `guru.interlis.repository`; Maps/Listen sind
defensiv kopiert, nullfrei und unveränderlich:

| Typ | Inhalt / Ergebnis |
|---|---|
| `ModelPin` | Modellname, exakte rohe Version, kanonische SourceId, SHA-256 der Originalbytes; `selector()` liefert den passenden ByName-Selektor |
| `ResolutionLock` | Map Modellname→unveränderliche Liste sämtlicher ausgewählter Versions-/Quellenpins und Map Index-SourceId→ContentDigest; zusätzliche benötigte Remoteabhängigkeiten sind beim Replay verboten |
| `RepositoryResolveOptions` | explizite RepositoryConfig, Modellpins, Quellen-/Indexpins und optionaler Lock |
| `RepositoryResolutionOutcome.Prepared` | `PreparedRepositoryUpdate`; Beschaffung vollständig, kein Schema-/Sprachgültigkeitsnachweis |
| `.Rejected` | nichtleere Liste typisierter `RepositoryProblem`-Werte; NOT_FOUND, AMBIGUOUS, VERSION_MISMATCH, INVALID_SOURCE, INDEX_FAILURE, LOAD_FAILURE, PIN_MISMATCH, LOCK_MISMATCH oder CHANGED_SOURCE; Ladeursache separat |
| `.Cancelled` | tatsächlicher CancellationReason, kein Teilbatch |
| `.Stale` | erwartete/aktuelle WorkspaceRevision, kein Teilbatch |
| `PreparedRepositoryUpdate` | erwartete Revision, SourceUpdates, vollständige Konfiguration, Lock und sichere Trace; an den ursprünglichen Workspace gebunden |

`PreparedRepositoryUpdate.apply(workspace)` führt keine IO aus. Quellen und
Konfiguration werden gemeinsam durch `Workspace.applyBatch` übernommen. Ein
fremder Workspace, ein veralteter Revisionsstand oder eine zwischenzeitlich
geänderte vollständige Konfiguration (auch Cachelimits) wird ohne Teilmutation
abgewiesen. Vorhandene Editorquellen werden nicht durch Downloads überschrieben.
Explizite Pins werden auch für ausgewählte vorhandene Texte geprüft.

Versionswerte im Index bleiben als rohe Strings einschliesslich signifikanter
Leerzeichen erhalten. Nachgeladene Auswahl wird nicht als neuer globaler Pin
materialisiert; konkrete Originalbindungen bleiben pro Übersetzung getrennt.
Locks können mehrere Versionen/Quellen eines Namens festhalten. Eine vollständig
beschaffte kombinierte Closure unterliegt weiterhin der Sprachregel
MODEL_NAME_UNIQUE; die Beschaffung erklärt deren Verletzung nicht zum Resolverfehler.

Der Host verifiziert tatsächliche UTF-8-Bytes, SHA-256, optional veröffentlichtes
MD5 sowie Name/Version/Sprachfassung im geparsten Header. Indexfehler und
Ladefehler sind getrennte Ursachen. Das Standardprofil setzt die Suche bei
unverfügbaren Repositorymetadaten mit einer sichtbaren Warnung fort; der alte
strikte Vertrag gilt weiter beim Replay eines Version-1-Locks. Eine bereits
ausgewählte Modelldatei wird bei einem Fehler nicht durch einen anderen Kandidaten ersetzt. `RepositoryClient.discover` behält seinen separaten partiellen Suchvertrag.
Online richtet sich die Wiederverwendung nach Cachealter und optionaler
Index-MD5 gemäss §17; eine zweite Online-Abschlussprüfung entfällt. Liefert die
normale Beschaffung widersprüchliche Bytes derselben Quelle, bleiben
CHANGED_SOURCE bzw. verletzte Pins/Locks Fehler. Spätere Workspaceänderungen
werden beim Apply erkannt. IO und Parserarbeit liegen ausserhalb des Monitors
des Zielworkspaces.

Offline lädt der Host ausschliesslich digestverifizierte Cacheinhalte. Bekannte
Pins/Locks haben Vorrang vor Herkunftsmetadaten; für diesen Pfad wird der Loader
nicht aufgerufen. Ohne Pin/Lock darf der explizite Cachehost den letzten lokal
gespeicherten URL-Stand verwenden; ein uncached Loader lehnt dies ab. Ohne
erforderlichen Treffer ist die Ladeursache OFFLINE. Kein Offlinepfad führt HTTP
aus. Workspaceinputs und
Cachehits erhalten weiterhin getrennte Text-/Byteidentitäten. Lokale Roots sind
separate Eingaben, ausser sie wurden ausdrücklich gepinnt. Ein Lock friert keine
ungepinnten Editoränderungen ein.

`RoutingSourceLoader` verarbeitet lokale Pfade, `file:` und HTTP(S). Datei-URIs
werden im Host lexikalisch auf absolute normalisierte Pfade kanonisiert; keine
Symlinkauflösung, Querybereinigung oder plattformfremde Pfadumschreibung.
`FileSourceLoader` begrenzt auch den tatsächlichen Streaming-Read, nicht nur eine
vorherige Dateigrösse. Indexauflösung vergleicht URI-Komponenten und dekodierte
Pfade und weist auch kodierte `..`-Ausbrüche ab. `RepositoryCatalog.revision`
(`repository-catalog-v3`, einschliesslich `browseOnly`) enthält längenpräfixiert alle Modellmetadaten,
Prioritäten, Indexherkünfte, Fehler und die FetchPolicy.

`DirectoryContentStore(Path[,maxEntryBytes])` ist ein optionaler atomarer,
digestadressierter Cache. Fehler, übergrosse oder korrupte Einträge sind Misses;
Treffer werden erneut gegen den Digest geprüft. Die Defaultgrenze ist 16 MiB pro
Eintrag. `InMemoryContentStore` bleibt verfügbar. `ResolutionLockCodec.encode` /
`decode` verwendet deterministisches UTF-8-XML `interlis-resolution-lock`, Version
1 beziehungsweise 2; sortierte Indizes/Modelle, keine DTD/externen Entities, keine unbekannten Felder,
doppelten vollständigen Modellidentitäten oder stillen Formatupgrades.
Verschiedene Versionen/Quellen desselben Namens bleiben getrennte Listeneinträge,
kanonisch nach rohem Versionsstring und SourceId sortiert; dies ist keine Latest-Auswahl. Decode begrenzt die Eingabelänge
auf 8 Mi UTF-16-Einheiten; Dateiadapter begrenzen zusätzlich gelesene Bytes.

Ausführbare Verträge: `ResolutionBoundaryContractTest`,
`TranslationSelectionContractTest`, `RepositoryClosureContractTest`,
`ResolutionLockContractTest`, `RepositoryCliContractTest` und
`ResolutionPolicyProtocolTest`; portable Szenarien verwenden dieselbe Policy.
`PinnedRealClosureTest` prüft die exakten offiziellen 2.3-Roads- und
2.4-CHBase-Closures mit tatsächlichen öffentlichen Import-/Basistyp-/Member-/Enum-
und Herkunftsassertions sowie Fresh/Reuse. Originalmodelle und Originalindizes
liegen mit SHA-256 in `interlis-repository/src/test/resources/closures/`;
der grosse unveränderte geo.admin.ch-Index ist verlustfrei gzip-komprimiert.


## CLI-Verbraucher der Repository-Closure

RES-001/004/007–009/011: `check`, `compile`, `analyze` und `diagram` beschaffen
benannte Roots und transitive Imports über dieselbe explizite Hostvorbereitung
mit RepositoryClosureService. Der CLI-Options-/Konfigurations- und Lockvertrag
steht in Kapitel 18. `format` benötigt nur Syntax und führt keine Beschaffung
aus. Weder Compilerkern noch CLI besitzen implizite Defaultrepositories.
`SemanticRepositoryCommandsTest` prüft lokale und HTTP-Closures, explizite
Quellen, mehrere Repositories, Pins, Locks, Offlinebetrieb, Fehler und Abbruch.


## 12. Repositorybesuche und Versionsketten im Standardhost

RES-001–009/011/012: Die Beschaffung MUSS ohne Lock standardmässig dem
Profil `ili2c-5.6.9` folgen. Referenzstand und Originalquellen mit Prüfsummen:
[Referenzmanifest](../implementation/repository-resolution/reference.json).
Die CLI aktiviert keine impliziten Standardrepositories.

Je Modellabfrage werden zuerst sämtliche konfigurierten Repositories in ihrer
Reihenfolge besucht. Danach folgen die Elternketten in einer FIFO-Warteschlange,
anschliessend die Unterrepositories in einer FIFO-Warteschlange; nach einem
Unterrepository werden zunächst dessen Elternketten abgearbeitet. Elternbesuche
fügen keine zusätzlichen Unterrepositoryzweige hinzu. Ein besuchtes Repository
wird pro Abfrage nur einmal verarbeitet. Der erste passende Treffer beendet die
Suche; dessen `ilisite.xml` und spätere Repositories werden nicht mehr geladen.
Dokumente werden innerhalb eines Beschaffungslaufs wiederverwendet. Ein leerer
Index mit gültigem RepositoryIndex-Profil darf als Mutterrepository dienen.

`RepositorySiteReader.read(IndexPayload,RepositoryBase)` liefert `Site` mit
geordneten unveränderlichen `parents()` und `subsidiaries()`. XML-Entities und
DTDs sind verboten. Relative Links werden an der Basis aufgelöst; HTTP(S) und
lokale Datei-Repositories sind zulässig. Ein Remoteindex darf keine lokalen
Dateizugriffe über Siteverknüpfungen einführen. Dateiidentitäten werden wie bei
Modelldateien kanonisiert; HTTP und HTTPS bleiben verschiedene Identitäten.

`RepositoryModelMetadata` ergänzt `boolean browseOnly`; der bisherige
Konstruktor setzt `false`. Die Auswahl erhält die originale Indexreihenfolge.
Für Imports gilt die Sprachfassung des importierenden Modells; bei einer
unqualifizierten benannten Root wird zuerst 2.4, dann 2.3 gesucht.
Nach Filtern nach Modellname und Sprachfassung werden `browseOnly`-Einträge
ignoriert. Exakte Version-/Quellenanforderungen werden vor der Kettenreduktion
angewendet und dürfen historische Versionen auswählen.
Ohne solche Einschränkung wird die erste Version ohne Vorgänger gewählt und
die Kette über `precursorVersion` verfolgt. Weitere Starts beziehungsweise
Nachfolger desselben Vorgängers werden mit Warnung ignoriert. Eine unterbrochene
Kette liefert die letzte erreichbare Version mit Warnung; ohne Startversion
wird das Modell mit Warnung ignoriert. Versionsstrings werden nicht sortiert.

`RepositorySearchStep(modelName,repository,kind,detail)` ist eine geordnete,
unveränderliche, geheimnisbereinigte Entscheidung. `LOOKUP` und `SELECTED`
bezeichnen Besuche und Auswahl; die übrigen Varianten begründen Warnungen.
`RepositoryDiagnostics` projiziert diese Fakten gemeinsam für alle Verbraucher.
`RepositoryProblem` ergänzt die konkrete optionale Quelle und vollständige
Kandidatenmetadaten. `RepositoryResolutionOutcome.Rejected` ergänzt
`diagnostics`, `sources` und `steps`; sein alter Konstruktor bleibt erhalten.
Die Diagnosen verwenden die tatsächliche Importstelle einschliesslich UTF-16-
Bereich und Quellrevision. Benannte Roots ohne Quellstelle bleiben global.

`WorkspaceConfiguration` ergänzt eine defensiv kopierte Liste globaler
`resolutionDiagnostics`; der bisherige Konstruktor setzt eine leere Liste.
Sie beeinflusst den Konfigurationsfingerprint und wird beim Capture in das
`SourceSet` übernommen. Repositorywarnungen erreichen so das einzige
Compiler-Gate und dessen bestehende Warnungspolitik. Keine zweite
Schemafreigabe im Host. Erfolgsbatches bleiben atomar und workspacegebunden.

Neue Locks haben `formatVersion=2`, `selectionProfile=ili2c-5.6.9`, geordnete
`steps` und eine Map `unavailable` mit konkreten Ladeursachen. `indexes` enthält
sämtliche tatsächlich verwendeten Modell- und Siteindizes mit SHA-256.
Die bestehenden Zweiparameterkonstruktoren erzeugen weiterhin Version-1-Locks;
`ResolutionLock.current` erzeugt Version 2. Codec und Replay lesen beide
Versionen ohne stilles Upgrade. Version 1 behält die explizite flache Suche.
Version 2 friert die Suchentscheidungen und ausgewählten Quellen ein. Replay
prüft die vollständigen eingefrorenen Metadaten gegen ihre Digests und bindet
die ausgewählten Identitäten direkt aus diesen Indizes, ohne eine neue Suche
oder neue Kettenentscheidung. Es akzeptiert keine zusätzlichen unbekannten
Metadaten. Dadurch bleiben auch Locks erweiterter, bereits geladener
Workspace-Closures in einem frischen Workspace reproduzierbar. Offline
werden nur digestgeprüfte Cachetexte und eingefrorene Nichtverfügbarkeiten
verwendet. Online werden gelesene Inhalte sowie beobachtete Nichtverfügbarkeiten
am Ende erneut geprüft. Fehlende Cachebytes sind `OFFLINE`, kein stiller Miss.

`maxSemanticWorkUnits`, `maxDiagnostics`, die kumulative Grenze
`maxTotalSourceUtf16Length` und die bestehenden Fetchlimits begrenzen die
Beschaffung. Abbruch oder Budgetüberschreitung veröffentlicht keinen Teilbatch.
`RepositoryClient.catalog/discover` bleiben separate explizite Katalogabfragen;
ihre rohen Kandidatenlisten sind keine automatische Kompilierauswahl.

Abnahmen: `RepositorySearchContractTest`, `RepositoryParityIntegrationTest`,
`RepositoryClosureContractTest`, `RepositoryDiagnosticsContractTest` und
`repositoryReferenceCheck`. Historische Tests mit pauschaler Hostmehrdeutigkeit
werden durch referenzbelegte Ketten-/Indexreihenfolgetests ersetzt; reine
Workspace-Mehrdeutigkeit bleibt eine eigene Abnahme.


## 13. Standardcache und HTTP-Revalidierung (RES-013)

Die nachfolgenden ETag-/304-/Abschlussprüfungs- und Validator-API-Angaben
sind durch den Cachefolgeauftrag nach ili2c (Kapitel08 §17 und die Ergänzung
am Kapitelende) ersetzt. Andere Verträge bleiben erhalten.

`HostRepositories.create(workingDirectory, RepositoryCacheConfiguration, observer)`
ist die explizite JVM-Komposition. `Access.loader()`, `contentStore()` und
`closureService()` teilen Inhaltscache und den lauflokalen Beobachtungskanal.
`RepositoryCacheConfiguration.defaults(home)` wählt `home/.interlis-cache`,
`at(path)` eine alternative Ablage und `disabled()` ausschliesslich Speicher.
Alle Werte sind nullfrei und immutable; Pfade sind absolut normalisiert.
Die Konstruktion erzeugt keine Dateien. Es gibt keinen globalen Cache oder Lauf.
`SourceLoader.loadCached(locator,offlinePolicy,token)` ist der explizite sichere
Offlineport: die Defaultmethode lehnt ohne Aufruf von `load` ab. Dadurch bleiben
bestehende funktionale eigene Loader offline frei von impliziten Hostzugriffen.
Nur cachefähige HTTP-Hosts überschreiben diesen Port; RoutingSourceLoader reicht
HTTP(S) entsprechend weiter. Ein nicht-offline FetchPolicy ist ein API-Fehler.
Die bisherigen expliziten Loader-/Store-/Closurekonstruktoren bleiben verfügbar;
insbesondere aktiviert `new HttpSourceLoader()` keine persistente Speicherung.

Bestehende SHA-256-Dateien bleiben direkt im Cacheverzeichnis. Der interne
`metadata-v1/<SHA-256 der UTF-8-Quell-URL>.xml`-Index ordnet die vollständige
SourceId dem Inhaltsdigest zu. `RepositoryCacheMetadata(source,responseSource,
digest,etag,lastModified)` ist ein nullfreier immutable Wert mit optionalen,
begrenzten HTTP-Validatoren; `responseSource` bezeichnet deren Redirectziel.
Queryparameter werden für die Cacheidentität nicht entfernt. Metadaten sind auf
64 KiB begrenzt, strikt versioniert, ohne DTD/externe Entities und werden erst
nach erfolgreicher Inhaltsspeicherung atomar veröffentlicht. Fehler sind Misses.
Alte Digestdateien benötigen keine Migration; ohne Metadaten bleiben sie über
Pins/Locks erreichbar. Es erfolgt keine automatische Bereinigung.

Online erfolgt bei jeder Beschaffung und bei der bestehenden Abschlussprüfung
wirklich ein HTTP-GET. Nur ein syntaktisch gültiger starker ETag darf als
`If-None-Match` an das zugehörige Redirectziel übergeben werden. Eine 304-Antwort
MUSS denselben starken ETag als genau einen ETag-Feldwert bestätigen, bevor
erneut digestgeprüfte Cachebytes verwendet werden. Fehlender, schwacher,
abweichender oder mehrdeutiger ETag sowie fehlende gültige Cachebytes lösen
genau einen unbedingten Wiederholungsversuch aus; eine weitere unbrauchbare 304
ist ein Ladefehler. 200 dekodiert und prüft die tatsächlich empfangenen Bytes.
Schwache ETags und Last-Modified allein garantieren keine Bytegleichheit und
führen deshalb zu vollständiger Übertragung, auch bei Pin-/Lock- und
Abschlussprüfungen. Last-Modified bleibt als kompatible Herkunftsmetadaten
les-/schreibbar, löst aber keinen If-Modified-Since-Request aus.
Dies erhält die bytegenauen Verträge auch bei semantisch äquivalenten
Darstellungen oder Änderungen innerhalb einer Last-Modified-Sekunde.
Nicht unterstützte `Vary`-Profile und `Cache-Control: no-store` werden nicht
persistiert; Teilantworten werden nicht als vollständige Quellen akzeptiert.
Der Herkunftseintrag wird bei 404 oder nicht speicherbaren Antworten entfernt.
`LoadedSource.origin` verwendet `http-no-store` für nicht persistierbare Antworten;
auch die nachgelagerte Closurebeschaffung darf diese Bytes nicht persistieren.
Es gibt keinen automatischen Rückfall auf alte Inhalte bei Netzfehlern.

Offline ohne Lock wird derselbe konfigurierte Repositorysuchpfad auf den zuletzt
gecachten Dokumenten ausgeführt; kein globales Scannen alter Modelle. Fehlende
optionale Metadaten bleiben sichtbare Suchausfälle. Nicht verfügbare oder
fehlerhafte ilisite.xml-Dokumente bleiben Warnungen; sie dürfen allein keine
LOAD_FAILURE-/INDEX_FAILURE-Ablehnung erzeugen. Fehlt das Modell in den
verfügbaren Indizes, bleibt NOT_FOUND beziehungsweise VERSION_MISMATCH erhalten.
Erforderliche fehlende Indizes und Modellquellen ergeben OFFLINE mit
Quellidentität. Alle Ausfälle, auch optionale Site-Ausfälle, bleiben in der
unavailable-Provenienz für Locks und die Online-Abschlussprüfung erhalten. Pins/Locks lesen dagegen ihren
exakten Digest unabhängig von einer inzwischen neueren URL-Zuordnung. Die
bestehenden Regeln für lokale Eingaben, Lockformate, MD5/Headerprüfung,
Workspaceupdates und Abschlussvergleiche bleiben erhalten.

`RepositoryCacheEvent` meldet DOWNLOAD, NOT_MODIFIED, OFFLINE_HIT, OFFLINE_MISS,
CACHE_MISS und WRITE_UNAVAILABLE ohne URLs/Validatoren. Der Callback ist am
Hostzugriff gebunden, Beobachterfehler beeinflussen kein Ladeergebnis. Fehler beim
persistenten Schreiben werden einmal pro Hostzugriff gemeldet; erfolgreich
heruntergeladene Bytes bleiben nutzbar. Die CLI projiziert diese Ereignisse über
RunLifecycle in den bestehenden RunContext und rendert einmalig eine strukturierte
operative Cachewarnung auf stderr; diese Warnung verändert keine Sprach-/Gateentscheidung.
Ein zusätzlicher Closurekonstruktor mit Consumer beobachtet auch digestgebundene
Offlinezugriffe. Alle Zeit-/Grössen-/Abbruchverträge gelten weiterhin.

Abnahmen: `RepositoryCacheContractTest`, `DefaultRepositoryCacheTest` sowie die
bestehenden Closure-, Lock-, HTTP- und CLI-Vertragstests. Die CLI-Bedienbeispiele
stehen in Kapitel 18 und README.


Korrekturabnahmen der beiden P2-Funde: RepositoryCacheRegressionTest und die
Offline-CLI-Gegenprobe in DefaultRepositoryCacheTest. HTTP-Semantik siehe
[RFC 9110 §13.1.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-13.1.2)
(schwacher If-None-Match-Vergleich) und
[§8.8.2.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-8.8.2.2)
(Last-Modified-Stärke). Die strengere Wiederverwendung ist eine Folgerung aus
den bytegenauen Projektverträgen, keine allgemeine HTTP-Cachepflicht.


## 14. Quellenbezogene Cachebeobachtung

RES-013 / LOG-005/008: `RepositoryCacheObservation(RepositoryCacheEvent event,
Optional<String> safeSource)` gehört zum JVM-Repositorymodul. Beide Komponenten
sind nullfrei und immutable. Die Anzeigeadresse entfernt Benutzerinformationen,
Query und Fragment vor Veröffentlichung; sie ist keine Loader- oder
Cacheidentität. Globale Schreibprobleme dürfen ohne Quelle beobachtet werden.
`HostRepositories.createObserved` und `RepositoryClosureService.observed` nehmen
einen detaillierten Observer entgegen. Alte Enum-Callbacks/Konstruktoren und
der Dreiparameterkonstruktor von Access bleiben über Adapter erhalten.
Jeder tatsächliche Zugriff veröffentlicht sein Ereignis einmal je angeschlossener
Schnittstelle. Observerfehler ändern die Beschaffung nicht; kein globaler Zustand.
HTTP- und beide Offlinepfade liefern bei jedem tatsächlichen Zugriff die
angefragte Quelle. Zusätzliche Online-Abschlussprüfungen und ETags entfallen
gemäss §17; optionale Sites, Pins und Locks bleiben erhalten.

## 15. HTTP-Netzwerkpolicy (RES-009, ARCH-005)

`FetchPolicy(boolean offline,int maxBytes,Duration connectTimeout,Duration readTimeout,
Optional<Duration> requestTimeout)` ist unveränderlich und nullfrei. Bytezahl und
alle gesetzten Dauern sind positiv; Dauern müssen in long-Nanosekunden darstellbar
sein. Ungültige Werte ergeben IllegalArgumentException, Nullwerte
NullPointerException. Defaults: online, 16 MiB, Connect 15 s, Read 40 s, kein
Requestgesamtlimit. Der frühere Dreiparameterkonstruktor und timeout() entfallen
auf ausdrücklichen Nutzerentscheid; sämtliche aktiven Verbraucher werden migriert.
Dateihosts verwenden weiterhin nur die für sie relevanten Limits.

Der Connecttimeout gilt für neue Verbindungen einschliesslich TLS-Aufbau über
HttpClient.Builder.connectTimeout. Ein Loader hält Clients nach Connecttimeout
getrennt. Der Readtimeout beginnt beim Absenden und wird beim Empfang der
Antwortheader sowie nicht leerer Bodydaten zurückgesetzt. Er begrenzt somit
Headerwartezeit und Leseinaktivität, nicht die Gesamtdownloadzeit. Der optionale
Requesttimeout gilt für einen vollständigen HTTP-Austausch einschliesslich Body.
Jeder Redirect und jeder bestehende Cache-Retry erhält neue Zeitbudgets; es gibt
kein Closuregesamtlimit. Monotone Zeitmessung, begrenzte Abbruchlatenz und
Abbruch des Requests/Bodyempfangs sind verpflichtend. Timeout bleibt TIMEOUT,
Nutzerabbruch bleibt OperationCancelledException, andere IO-Fehler bleiben getrennt.
Unvollständige Antworten werden nicht in den Cache veröffentlicht.

`RepositoryProxyConfiguration` ist ein geschlossener unveränderlicher Hosttyp:
`SystemDefault()`, `Direct()`, `HttpProxy(String host,int port)`. Host und
Konfiguration sind nullfrei; ein manueller Host ist ein blosser Hostname oder
IP-Wert (IPv6 geklammert), ohne Schema, Zugangsdaten, Pfad, Query oder Fragment;
Port 1–65535. Validierung führt kein DNS/IO aus. Ungültige Werte werden abgewiesen.
SystemDefault übernimmt beim Erzeugen des Loaders den aktuellen JVM-ProxySelector
(bei null direkt); Direct verwendet NO_PROXY, HttpProxy einen eigenen HTTP-Selector
für HTTP und HTTPS-CONNECT. Der übernommene Selector ist eine Referenz auf die
Hostabhängigkeit, keine tiefe Kopie eines fremden mutierbaren Selectors.
Produktionscode verändert weder Systemproperties noch globale Selector-/
Authenticatorwerte. Eigene Authentisierung, PAC und Environmentinterpretation
sind nicht Teil dieses Abschnitts; SystemDefault delegiert an die JVM.

Neue öffentliche Einstiege: HttpSourceLoader(RepositoryProxyConfiguration),
HostRepositories.create(Path,RepositoryCacheConfiguration,Consumer<RepositoryCacheEvent>,RepositoryProxyConfiguration)
und createObserved mit Consumer<RepositoryCacheObservation> und demselben letzten
Parameter. Bisherige Einstiege verwenden SystemDefault. Hostinstanzen besitzen
ihre Clients und Proxywahl; Konstruktion löst keine Netzwerkzugriffe aus.
Offline lädt ausschliesslich verifizierte lokale Inhalte und kontaktiert keinen
Proxy. FetchPolicy-Felder gehen in die deterministische Katalogrevision v4 ein;
Proxywerte gehören weder zu Locks noch zum Compilerzustand.

Abnahme: NET-01–05 in
[EXECUTION_PLAN.md](../implementation/http-network-policy/EXECUTION_PLAN.md),
HttpNetworkPolicyTest, HttpSourceLoaderTest, RepositoryClientTest sowie die
gezielten Cache-/CLI- und Distributionsfälle. Kein neues Compiler-/Bridgeformat.

HTTP-Adressfehler (RES-009): Syntaktisch ungültige Redirect-Location und von der
HTTP-Requestkonstruktion abgewiesene Zieladressen ergeben IO_FAILURE mit festem,
wertfreiem Fehlertext. Dasselbe gilt für die initiale HTTP-Adresskonstruktion.
Die Abbildung fängt IllegalArgumentException ausschliesslich beim URI-Parsen,
beim Auflösen der Location und bei der Requestkonstruktion ab; ein allgemeiner
RuntimeException-Fang über den gesamten Loader ist nicht zulässig. Der ursprüngliche
Grund bleibt als interne Ursache erhalten, rohe Locationwerte gelangen nicht in
öffentliche Diagnosetexte. Relative HTTP(S)-Redirects und bisherige Scheme-/
Redirectlimit-, Timeout-, Abbruch- und Cacheverträge bleiben erhalten.
Abnahmen: HttpRedirectFailureTest, MalformedRepositoryRedirectTest sowie
[gezielte Distributionsprozesse](../implementation/http-redirect-fix/EXECUTION_PLAN.md).

## 16. Chronologische Beobachtung und feste HTTP-Darstellung

Die nachfolgenden ETag-/304-/Abschlussprüfungs- und Validator-API-Angaben
sind durch den Cachefolgeauftrag nach ili2c (Kapitel08 §17 und die Ergänzung
am Kapitelende) ersetzt. Andere Verträge bleiben erhalten.

WP-26/30, RES-009/013, LOG-004/005/008: RepositoryResolutionObservation im
Repositorymodul ist ein geschlossener unveränderlicher Typ mit SearchStep
(RepositorySearchStep step, nullfrei) und VerificationStarted(). Die additive
resolve(Workspace,AnalysisRequest,RepositoryResolveOptions,Consumer<RepositoryResolutionObservation>,CancellationToken)
Überladung beobachtet ausschliesslich den aktuellen Aufruf; der bisherige
Einstieg verwendet einen leeren Observer. Zustellung erfolgt synchron in
Erfassungsreihenfolge, LOOKUP vor Indexzugriff und SELECTED nach Kandidatenwahl.
Historische Lockschritte werden nicht wiedergegeben. RuntimeExceptions des
Observers bleiben ohne Einfluss; Abbruch erfolgt über den CancellationToken.
VerificationStarted erscheint einmal unmittelbar vor tatsächlicher Online-
Abschlussprüfung geladener oder zuvor nicht verfügbarer Quellen; nicht offline
oder ohne ausstehende Zugriffe. Keine globalen Observer oder neue Gateentscheidung.

Jeder HTTP-GET einschliesslich Redirects und Retries MUSS Accept-Encoding:
identity senden. Vary ist nur bei fehlendem/leeren Wert oder ausschliesslich
Accept-Encoding wiederverwendbar; Feldnamen werden ohne Grossschreibung über
alle Headerzeilen und Kommalisten geprüft. Vary:* und andere Felder sowie
no-store bleiben nicht persistierbar. Angenommene 200-/304-Antworten erlauben
nur fehlendes Content-Encoding oder identity; andere Codings ergeben vor
Dekodierung/Cacheveröffentlichung IO_FAILURE mit wertfreiem Text. Keine
automatische Dekomprimierung. Starke identische ETag-Bestätigung, Digestprüfung,
Redirectzielbindung, Retryregeln, Pins/Locks und Cacheformat bleiben erhalten.
Dies konkretisiert die bisher nicht unterstützten Vary-Profile in §13;
kein allgemeiner Varianten- oder Kompressionscache. Grundlage:
[RFC9110 §12.5.3](https://www.rfc-editor.org/rfc/rfc9110.html#section-12.5.3),
[RFC9111 §4.1](https://www.rfc-editor.org/rfc/rfc9111.html#section-4.1).

## 17. Cache nach ili2c: MD5 und Fristen (massgeblicher Folgevertrag)

WP-26/30, RES-009/013, CLI-004/006, LOG-005/008: Auf ausdrücklichen
Nutzerentscheid ersetzt dieser Abschnitt die ETag-/304- und Abschlussprüfungs-
Verträge aus §13/16. Metadaten haben24h, Modelldateien7Tage Cachefrist ab dem
letzten vollständigen erfolgreichen Download je URL; Treffer verlängern sie
nicht. Genau an der Grenze bleibt ein Eintrag gültig, zukünftige Zeitpunkte
sind online unzuverlässig. Innerhalb der Frist wird vor Modellbeschaffung
die vorhandene Datei digestgeprüft und gegen die optionale Index-MD5 verglichen.
Fehlende/defekte/abgelaufene oder MD5-abweichende Inhalte werden vollständig
geladen. Ohne MD5 entscheidet die Frist. Nach Download bleibt die veröffentlichte
MD5 verbindlich; SHA256/Pins/Locks werden zusätzlich geprüft. Keine bedingten
Header/ETags/Last-Modified, keine304-Wiederverwendung, kein zweiter Onlineabruf
geladener oder zuvor nicht verfügbarer Quellen, auch nicht mit Lock.
Onlinefehler bleiben Fehler, nur --offline verwendet alte verifizierte Inhalte.

RepositoryCacheRequest(Duration maxAge,Optional<String> expectedMd5) ist immutable
und nullfrei, Dauer nichtnegativ;0 bedeutet unbedingt laden. Fabriken metadata()
(24h), model(md5)(7Tage) und unconditional(). Neue SourceLoader.load-Überladung
mit Request vor CancellationToken; Default delegiert auf den bisherigen
abstrakten Einstieg. Routing-/HTTP-/verifizierender Loader reichen den Request
weiter, alle Repositoryverbraucher wählen Fristen explizit, ohne URL-Heuristik.
HTTP ohne Request lädt online unbedingt. Uhr ist explizit injizierbar.
RepositoryCacheMetadata(source,responseSource,digest,downloadedAt) ersetzt
Validatoren durch Instant downloadedAt; EPOCH kennzeichnet unbekanntes Alter.
metadata-v2 speichert atomar je URL, Inhaltsdateien/Lockformate bleiben erhalten.
V1 ist nur offline als Herkunftszuordnung nutzbar; online erfolgt einmaliger
Neudownload und v2-Veröffentlichung. Defekte v2-Einträge fallen nicht auf v1 zurück.
Keine Inhaltsbereinigung. Vary entscheidet nicht über den Anwendungscache;
no-store bleibt ausgeschlossen, identity und Content-Coding-Prüfung bleiben.
FRESH_HIT bezeichnet lokale Onlineverwendung, ohne304-Behauptung. NOT_MODIFIED,
VerificationStarted und ILI-RES-0013 bleiben reserviert, werden nicht emittiert.
Atomare Workspace-Revisionsprüfung und Gate bleiben erhalten; bereits während
der normalen Beschaffung erkannte widersprüchliche Inhalte bleiben Fehler.
Referenz: ili2c5.6.9, bf16026e83f2377153b2e818a4f3838f0a423722,
RepositoryAccess#getLocalFileLocation und IliManager#createConfig.

## 18. Eingefrorene Vorbereitung für den LSP-Host

`RepositoryClosureService.resolveCaptured(Workspace owner, WorkspaceCapture initial,
WorkspaceConfiguration configuration, RepositoryResolveOptions options,
CancellationToken token)` ist der additive Hintergrundpfad. Alle Argumente sind
nullfrei. Capture und Konfiguration werden gemeinsam auf der Ownerqueue erfasst;
der Aufrufer muss sie derselben Workspaceinstanz zuordnen. Die Methode liest oder
mutiert `owner` nicht, sondern bindet nur das resultierende bedingte Update daran.
Sie verwendet exakt die bisherige Such-/Cache-/Digest-/Locklogik. Zwischenzeitliche
Änderungen können daher erst beim Apply erkannt werden: `Prepared` bedeutet
vollständig vorbereitet, nicht weiterhin aktuell. `PreparedRepositoryUpdate.apply`
bleibt der einzige atomare Commit mit Revision und vollem Konfigurationsfingerprint.
Der bisherige synchrone `resolve`-Einstieg bleibt erhalten und prüft zusätzlich
nach der Vorbereitung die aktuelle WorkspaceRevision.

Die begrenzte LSP-IO-Queue verarbeitet diese Werte ausserhalb der Ownerqueue.
Supersedierung und Shutdown brechen den Token ab; veraltete Ergebnisse werden
vor Apply/Veröffentlichung verworfen. `CapturedRepositoryResolutionTest` prüft
auch, dass der Hintergrundpfad keine einzige Live-Owner-Methode aufruft und ein
Dirtyupdate während blockierter Beschaffung den gesamten Apply zurückweist.
