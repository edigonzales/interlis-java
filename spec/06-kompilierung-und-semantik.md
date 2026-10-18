# 06 – Kompilierung und Semantik

## 1. Auftrag und Erfolgskriterium

Der Compiler ist ein synchron aufrufbarer, portabler Bibliothekskern. Er analysiert explizit bereitgestellte Quelltexte und Modellbindungen. Er kann einen unveränderlichen partiellen Analysestand liefern oder für eine genau definierte Modellmenge ein geprüftes `CompiledSchema` ausstellen. Das Ganze ist die gemeinsame Grundlage für CLI, Workspace, LSP, WebAssembly und spätere Consumerpläne.

Ein Erfolg bedeutet: Für die angeforderte Wurzelmenge und ihre fachlich erforderlichen Abhängigkeiten sind Syntax, Bindung, Typen und semantische Regeln vollständig geprüft und akzeptiert. Ein Erfolg bedeutet nicht, dass bereits Daten validiert, Dateien geschrieben, Datenbankschemata erzeugt oder externe Funktionsimplementierungen geladen wurden.

**Der Coding-Agent MUSS die Unterscheidung zwischen Benutzerfehler, unvollständiger Implementierung, Abbruch und internem Compilerfehler bis in API, Diagnostik, CLI und Tests erhalten.** Eine Exception zu fangen und ein leeres Modell als Erfolg zurückzugeben ist ausdrücklich unzulässig.

Die bestehende [ili2c-Grammatik](https://github.com/claeis/ili2c/blob/cbccff35fc98fdfa370e42e3bb39bb4edd70a658/ili2c-core/src/main/antlr/ch/interlis/ili2c/parser/interlis24.g) und die Metamodellklassen enthalten heute gemeinsam semantische Regeln. Für den Neuentwurf werden diese Regeln in nachvollziehbare Phasen überführt. Die Phasentrennung aus [ilic-fork/module-boundaries.md](https://github.com/edigonzales/ilic-fork/blob/c601f1c9d88eb68f67991fd05fc4653884d104c7/docs/architecture/module-boundaries.md) ist eine konzeptionelle Vorlage; C++-Besitzverhältnisse und interne Klassenstrukturen werden nicht mechanisch kopiert.

## 2. Normative Anforderungen

| ID | Normative Anforderung | Phase | Abnahme |
|---|---|---|---|
| SEM-001 | Der Compiler MUSS nur explizite Sources/Bindings/Optionen verwenden und darf keine versteckten Quellen nachladen. | P1 | Kein Dateisystem-/HTTP-Zugriff aus Compiler- oder Modellmodul. |
| SEM-002 | Analyse, Ablehnung, Abbruch und interner Fehler MÜSSEN typisierte Ergebnisse besitzen. | P1 | Je ein real auslösender Testfall pro Ergebniskategorie. |
| SEM-003 | Das Erfolgsgate MUSS vollständige Coverage und alle kanonischen Fehler der angeforderten Closure prüfen. | P1 | Unterdrückte oder nicht dargestellte Fehler erzeugen keinen Erfolg. |
| SEM-004 | Ein Fehler in unabhängigen Workspacequellen DARF eine gültige Rootclosure nicht blockieren. | P1 | Zwei unabhängige Modelle, eines fehlerhaft; gezielte Kompilierung des anderen gelingt. |
| SEM-005 | Unaufgelöste Pflichtbindungen, Error-/Pending-Typen und unbekannte Implementierungszweige MÜSSEN den betroffenen Compile blockieren. | P1 | Kein Erfolg durch `null`, Defaulttyp oder ausgelassene Deklaration. |
| SEM-006 | Rechtmässige Generic-/Deferred-Bindungen und externe Funktionssignaturen MÜSSEN von Fehlern getrennt bleiben. | P2 | Gültiger Fall kompiliert ohne Runtimeimplementierung beziehungsweise Datensatzkonkretisierung. |
| SEM-007 | Jede Phase MUSS deterministische Eingaben, Ausgaben, Zustandsbesitz und Abbruchpunkte haben. | P1 | Wiederholung und Plattformvergleich liefern gleiche fachliche Resultate. |
| SEM-008 | Vorabindexierung DARF sprachlich verbotene Vorwärtsreferenzen oder Sichtbarkeiten nicht erlauben. | P1/P2 | Ziel vorhanden im globalen Index, aber im fraglichen Scope unzulässig: Diagnose. |
| SEM-009 | Zyklen MÜSSEN fachlich klassifiziert und endlich bearbeitet werden. | P1/P2 | Alias-/Vererbungszyklus und erlaubter Referenzzyklus besitzen getrennte Ergebnisse. |
| SEM-010 | Typen, Constraints, Views, Übersetzungen und effektive Eigenschaften MÜSSEN nach Katalog05 vollständig geprüft werden. | P2 | Vollständige Abdeckungsmatrix mit positiven und negativen Fällen. |
| SEM-011 | Wiederverwendung MUSS semantisch optional sein; ohne Cache muss dasselbe fachliche Resultat entstehen. | P1 | Fresh-versus-reuse nach Editfolgen. |
| SEM-012 | Veraltete, fremde oder nicht passende Cacheeingaben MÜSSEN ignoriert oder als API-Fehlgebrauch zurückgewiesen werden. | P1 | Geänderte Sprachoption oder Importrevision verursacht keinen falschen Cachetreffer. |
| SEM-013 | Kein global veränderlicher Logger, Modellkatalog, Proxywert, Nameindex oder Fehlerstatus ist zulässig. | P1 | Mehrere Engines/Sitzungen ohne gegenseitige Beeinflussung. |
| SEM-014 | Ressourcenlimits und Abbruch MÜSSEN als nicht erfolgreiche Ausgänge sichtbar sein. | P1 | Limit reduziert Ergebnisabdeckung; Gate bleibt geschlossen. |
| SEM-015 | Teilergebnisse MÜSSEN ihren tatsächlichen Analysezustand ausweisen. | P1 | Completion kann bekannte Symbole nutzen; Typabfrage unbekannter Teile liefert `NotAnalyzed`. |
| SEM-016 | Externe Configoverlays MÜSSEN als immutable Eingaben und Fingerprintbestandteile behandelt werden. | P1/P4 | Zwei Overlaykonfigurationen verwechseln keine Resultate oder Laufzeitcaches. |
| SEM-017 | Norm- und Legacyabweichungen MÜSSEN explizit begründet und getestet sein; automatische Baselineanpassung ist verboten. | P1/P2 | Jede akzeptierte Abweichung besitzt Referenz, Fall und bewusste Entscheidung. |
| SEM-018 | Jeder produktive Aufruf von `DefaultCompiledSchema` MUSS durch `CompilationGate` erfolgen. | P1 | Architekturtest und gezielter Gate-Negativtest. |
| SEM-019 | Die Frontendpipeline MUSS denselben fachlichen Code auf JVM und Wasm verwenden. | P1 | Identische semantische Fixtureausgabe; kein nur für Wasm vereinfachtes Modell. |
| SEM-020 | CLI/LSP/Projektionen DÜRFEN keine zweite unabhängige Namens- oder Typsemantik implementieren. | P1/P3 | Adapterdelegation und Vergleich an identischen Modellfällen. |

## 3. Öffentliche Eingabe-API

### 3.1 `CompilationRequest` und Wurzelselektion

Alle in diesem Abschnitt benannten Eingabetypen liegen in `guru.interlis.compiler`, sofern nicht ausdrücklich ein Foundationtyp genannt ist.

```java
public record CompilationRequest(
    List<ModelSelector> roots,
    SourceSet sources,
    CompilerOptions options
) {}

public sealed interface ModelSelector
        permits ModelSelector.ByName, ModelSelector.BySource {

    record ByName(
        String modelName,
        Optional<String> modelVersion,
        Optional<SourceId> sourceHint
    ) implements ModelSelector {}

    record BySource(SourceId source) implements ModelSelector {}
}
```

Die Wurzelliste ist nichtleer und defensiv kopiert. `ByName` bezeichnet einen exakten Modellnamen; eine Modellversion ist ein wörtlicher fachlicher Wert, keine automatisch interpretierte Semver-Range. `sourceHint` schränkt die Auswahl ein und darf einen Konflikt nicht still übersteuern. `BySource` selektiert alle ursprünglichen Modelle der expliziten Quelle. Bei einem fehlerhaften Header oder einer Quelle ohne gültig erkennbares Modell darf daraus nicht „null ausgewählte Modelle, also Erfolg“ werden.

Ein Dokument kann mehrere Modelle enthalten. Eine Rootauswahl über einen Namen selektiert nicht implizit alle Nachbarmodelle derselben Datei. Syntaxfehler im gemeinsam geparsten Dokument müssen jedoch verlässlich einem Bereich beziehungsweise einer globalen Dokumentbedingung zugeordnet werden. Kann ein Fehler nicht sicher von der Root abgegrenzt werden, gilt er konservativ als blockierend für die betroffene Quelle. Fehler werden nie bloss anhand von Stringpräfixen einer FQN weggefiltert.

### 3.2 `SourceSet`

```java
public final class SourceSet {
    public static SourceSet of(
        Map<SourceId, SourceText> sources,
        List<ImportBinding> imports,
        List<Diagnostic> resolutionDiagnostics,
        ResolutionFingerprint fingerprint
    );

    public Map<SourceId, SourceText> sources();
    public List<ImportBinding> imports();
    public List<Diagnostic> resolutionDiagnostics();
    public ResolutionFingerprint fingerprint();
}
```

Diese Darstellung zeigt Signaturen; Konstruktion und Methodenkörper sind zu implementieren. Ein Mapkey muss der `SourceText.id()` entsprechen. Pro Quelle ist genau eine Revision enthalten. Collections werden defensiv übernommen. Die kanonische Quellenreihenfolge ist `SourceId.value()` nach `String.compareTo`, also lexikografisch über UTF-16-Codeeinheiten, unabhängig von Locale, Mapkonstruktion oder Netzwerkantwortzeit. Dieselbe Ordnung gilt für den Quellenanteil der kanonischen Diagnosereihenfolge nach Kapitel 09. Innerhalb einer Quelle bleiben Originaloffset und fachliche Deklarationsordnung massgeblich. Ein Legacyrenderer darf die explizit profilierte historische Anzeigeordnung herstellen; er ändert weder Bindung noch kanonische Diagnoseidentität.

`ImportBinding`, `ImportSite`, `ImportResolution`, `ModelLocation` und `ResolutionProblem` sind in [08](08-modellaufloesung-und-repositories.md) definiert. Eine Bindung nennt die originale Importstelle und eine Zielquelle/-revision/-deklaration oder einen expliziten ungelösten Zustand. Der Compiler verifiziert diese Angaben gegen die tatsächlich geparsten Modellköpfe. Ein Resolver kann dem Compiler nicht durch einen behaupteten Namen eine andere Deklaration unterschieben.

Der übergebene `ResolutionFingerprint` ist Resolverprovenienz und kein alleiniger Beweis der Identität des SourceSet. `CompilationFingerprinter.fingerprint(request)` berechnet zusätzlich selbst aus den tatsächlich vorliegenden Quellen, ihren exakten Textfingerprints/Revisionen, Rootselektoren, Importstellen/-zielen/-zuständen, Optionen, Sprach-/Grammatikständen und relevantem Resolverstand die tatsächliche Eingabeidentität. Kanonische Längenpräfixierung und stabile Reihenfolgen verhindern uneindeutige Verkettungen. Ein Aufrufer kann durch Wiederverwendung desselben behaupteten Resolverhashs bei geändertem Text oder Ziel keine alte Compileranalyse gültig machen.

Nicht gefundene, mehrdeutige oder technisch blockierte Imports bleiben Daten im SourceSet. Die Syntax und die erreichbaren Teile können trotzdem analysiert werden. Ob daraus `Rejected` oder eine technisch blockierte Aufbereitung im Host entsteht, legt der Resolververtrag fest; ein vollständiger erfolgreicher Compile darf bei fehlender Pflichtabhängigkeit niemals entstehen.

Die vorhandenen `resolutionDiagnostics` werden über `DiagnosticCollector.importAll` nach Kapitel 09 in den aktuellen Lauf übernommen. Dabei werden Diagnose-IDs und Ursachen-/Related-Bezüge konsistent auf die aktuelle Diagnosemenge abgebildet. Unverändert kopierte IDs eines fremden Snapshots dürfen keine zufälligen Verknüpfungen mit aktuellen Fehlern erzeugen. `ResolutionProblem`-Verweise werden über dieselbe Abbildung angepasst.

Das vordefinierte Modell `INTERLIS` wird versionsbezogen vom Compiler bereitgestellt. Eine normale Importquelle namens `INTERLIS` darf dieses nicht still ersetzen. Auflösung von Modellmetadaten und Download von Quelltexten ist Aufgabe08; Typauflösung innerhalb dieser Texte ist Aufgabe dieses Dokuments.

### 3.3 `CompilerOptions`

```java
public record CompilerOptions(
    SemanticProfileId semanticProfile,
    Set<LanguageVersion> allowedLanguages,
    WarningPolicy warningPolicy,
    AnalysisBudget budget,
    SemanticOverlays overlays
) {
    public static CompilerOptions defaults();
}

public enum WarningPolicy { ALLOW_WARNINGS, REJECT_WARNINGS }
```

`defaults()` erlaubt 2.3 und 2.4 und verwendet das dokumentierte normative Profil `interlis-2.3-2.4/v1`. Dieses Profil ist an konkrete Norm-/Grammatikstände gebunden; die Kennung ist keine wechselnde Bezeichnung „latest“. Ein unbekanntes Profil wird als ungültige API-Konfiguration zurückgewiesen. Eine Einschränkung auf eine Sprachversion prüft nicht nur Rootheader, sondern alle gemäss Sprachregeln relevanten beteiligten Modelle.

`SemanticOverlays` ist ein unveränderliches typisiertes Aggregat mit `empty()`, einer geordneten Overlayliste und einem Fingerprint. Anfangs muss nur `empty()` produktiv akzeptiert werden; spätere Overlaytypen werden mit eigenen Semantiktests freigeschaltet. Ein unbekannter Overlaytyp darf nicht ignoriert werden. CRS-/Metadaten-/Translationsoptionen, die fachliche Bedeutung verändern, gehören in diese Eingabe oder in den ausdrücklich consumerseitigen Planvertrag aus12. Temporäre Laufzeitwerte und Caches gehören nicht hinein.

`AnalysisBudget` enthält positive Obergrenzen für Quelltextmenge, Tokens, Syntaxknoten, Recoveryereignisse, Verschachtelung, semantische Arbeitseinheiten und Diagnosen. Für P1 werden konservative dokumentierte Defaultwerte implementiert und mit realistischen Modellen geprüft. Die Werte dürfen durch den Host erhöht werden. Ein Limit ist eine Ressourcengrenze, keine Sprachregel. Die Diagnose muss daher erklären, dass das Ergebnis nicht vollständig geprüft wurde.

Folgende benannte Felder und Startwerte sind für `AnalysisBudget.defaults()` festgelegt. Sie sind eine **konservative Startkalibrierung für die Implementierung**, keine gemessene Speicher-/Laufzeitzusage, keine Normgrenze und kein bereits erfülltes Legacy-Freigabeprofil. P1 misst sie mit dem echten Modellkorpus; eine spätere Anpassung wird mit Ergebnismanifest und Grenztests dokumentiert.

| Feld | Java-Typ und Einheit | Startwert | Prüfort |
|---|---|---:|---|
| `maxSourceUtf16Length` | `int`, UTF-16-Codeeinheiten je Quelle | 2’097’152 | Vor CharStream-/Tokenanlage einer Quelle. |
| `maxTotalSourceUtf16Length` | `long`, UTF-16-Codeeinheiten des tatsächlich analysierten Quellsatzes | 8’388’608 | Vor Start; Addition überlaufgeprüft. |
| `maxTokens` | `long`, erzeugte Tokens pro Lauf einschliesslich gezählter Hidden Tokens | 500’000 | Tokenproduktion und Wiederverwendungsinventar. |
| `maxSyntaxNodes` | `long`, eigene Syntaxknoten pro Lauf | 750’000 | Vor weiterer Knotenerzeugung. |
| `maxRecoveryEvents` | `int`, Einfüge-/Verwerfungs-/Synchronisationsereignisse pro Lauf | 10’000 | Recoverystrategie. |
| `maxParseRuleDepth` | `int`, gleichzeitig aktive Parserregeln | 256 | Beim Regeleintritt vor tieferer Verarbeitung. |
| `maxSemanticWorkUnits` | `long`, definierte semantische Arbeitseinheiten pro Lauf | 10’000’000 | Graph-/Bindungs-/Typregeloperationen. |
| `maxDiagnostics` | `int`, kanonische Diagnosen pro Lauf | 1’000 | Diagnosecollector einschliesslich importierter Diagnosen. |
| `maxNumericLiteralDigits` | `int`, Ziffern der Mantisse beziehungsweise Ganzzahl vor numerischer Materialisierung | 4’096 | Literalparser vor `BigInteger`-/`BigDecimal`-Aufbau. |
| `maxAbsoluteDecimalExponent` | `int`, absoluter Dezimalexponent | 100’000 | Exponentprüfung vor grossen Zahlen-/Stringallokationen. |

Alle Limits sind positiv. Totalquellgrenze muss mindestens die Einzelquellgrenze erlauben; Hosts dürfen ein insgesamt strengeres Sessionbudget besitzen. Die effektiven akzeptierten Grenzen werden im Host beziehungsweise der WASM-Capabilities-Antwort offengelegt. `maxNumericLiteralDigits` zählt keine Vorzeichen und Trennzeichen; Exponentziffern werden gesondert begrenzt beziehungsweise vor Überlauf geprüft. Ein übergrosser Exponent wird nicht zuerst in einen überlaufenden `int` gelesen. Literale und Diagnostik verwenden bei grossen Exponenten eine kompakte exakte Darstellung, keine automatisch vollständig ausgeschriebene Dezimalzeichenkette.

`SemanticWorkCounter.charge(SemanticWorkKind kind, long amount)` zählt mindestens neu besuchte Referenz-/Abhängigkeitskanten, begonnene Typrelationen, Ausdrucksknoten und Regelprüfungen. Die Zählerdefinition ist versioniert; unterschiedliche Runtimegeschwindigkeit verändert sie nicht. Eine Änderung dieser Zählung kann Abbruchgrenzen verändern und benötigt aktualisierte Budgetfixtures. Bereits wiederverwendete immutable Daten werden für Speicher-/Umfangsgrenzen berücksichtigt, auch wenn kein neuer Parseaufruf nötig ist. Ein Cache darf deshalb ein zu kleines angefordertes Budget nicht still umgehen.

Die Werte werden als normale Java-Zahlkonstanten ohne typografische Tausenderzeichen implementiert. Sie sind Teil des Options-/Budgetfingerprints. Grenztests prüfen jeden Schutz bei `Limit - 1`, `Limit` und `Limit + 1` mit echten Arbeitseinheiten beziehungsweise kontrollierten kleinen Testlimits. Ein Ressourcenabbruch ist auch bei syntaktisch gültigem Text erlaubt und muss ausdrücklich als solcher berichtet werden. Bei zu kleinem Budget wird kein sprachlicher Negativtest künstlich grün erklärt.

Eine erreichte Ressourcengrenze wird öffentlich als Abbruchgrund `RESOURCE_LIMIT` klassifiziert: `compile` liefert `Cancelled`, `analyze` wirft `OperationCancelledException` mit diesem Grund. Ein gegebenenfalls eingefrorener Teilsnapshot besitzt `BUDGET_EXCEEDED`-Coverage. Der Abbruch darf nicht als endgültiger Beweis eines ungültigen INTERLIS-Modells erscheinen.

Zeitbasierte Hostdeadlines werden über `CancellationToken` geliefert; der reine Kern benötigt dafür keine Uhr. `warningPolicy` verändert nicht die ursprüngliche Schwere einer Warnung. Beim Gate wird gegebenenfalls ausdrücklich wegen Warnungen abgelehnt. Die semantische und die Betriebsdiagnostik bleiben dadurch nachvollziehbar.

## 4. Engine, Ergebnisse und Wiederverwendung

### 4.0 Öffentliche Konstruktion und Dienste

Die SDK muss ohne Kenntnis interner Implementierungen instanziierbar sein:

```java
public final class CompilerEngines {
    public static CompilerEngine create();
    public static CompilerEngine create(CompilerServices services);
}

public record CompilerServices(
    RunObserver observer,
    ClockSource clock,
    RunContextFactory runContexts
) {
    public static CompilerServices defaults();
}
```

Dies sind Methodensignaturen; die Fabrik und Recordvalidierung sind zu implementieren. `CompilerEngines.create()` entspricht `create(CompilerServices.defaults())`. Der Defaultobserver ist `RunObserver.noop()`. Die Defaultzeitquelle verwendet die für die Zielplattform geprüfte Standardzeit; alternativ injiziert der Host eine eigene `ClockSource`. `RunContextFactory` stammt aus `guru.interlis.execution` nach Kapitel 09 und stellt `createRoot()` sowie `createChild(RunContext parent)` bereit. Die Defaultfabrik verwendet ihren eigenen Namespace und instanzlokale, überlaufgeprüfte Zähler; keine globale Zähler-/ThreadLocal-Pflicht. Tests injizieren kontrollierte IDs und Zeitwerte. Betriebs-Run-IDs und Zeitwerte beeinflussen weder Sprachentscheidungen noch semantische Symbol-/Snapshotidentitäten oder fachliche Fingerprints.

Die Fabrik führt keine Repositorysuche, Pluginentdeckung oder Netzoperation aus. Builtins, Phasen und Sprachprofile werden intern explizit registriert. Ein allgemeines DI-Framework oder `ServiceLoader` ist dafür nicht erforderlich. Der erzeugte Engine-Einstieg ist reentrant: Jeder Aufruf besitzt eigene veränderliche Laufdaten. Ein über `CompilerServices` gemeinsam verwendeter Observer, eine gemeinsam verwendete Uhr und eine gemeinsam verwendete RunContextFactory müssen ihrerseits threadsicher sein oder durch den Host vor den betreffenden gleichzeitigen Aufrufen serialisiert werden. Das rechtfertigt keinen globalen Compiler-Lock.

Fehler der optionalen Beobachtungssenke werden durch den `SafeRunObserver`-Vertrag aus09 isoliert. Die Factory veröffentlicht keine API, mit der ein Consumer fachliche Pflichtprüfungen ausschalten oder ein `CompiledSchema` direkt erzeugen kann.

### 4.1 Kernmethoden

```java
public interface CompilerEngine {
    AnalysisSnapshot analyze(CompilationRequest request,
                             CancellationToken cancellation);

    AnalysisSnapshot analyze(CompilationRequest request,
                             AnalysisReuse reuse,
                             CancellationToken cancellation);

    AnalysisSnapshot analyze(CompilationRequest request,
                             AnalysisReuse reuse,
                             RunContext context,
                             CancellationToken cancellation);

    CompilationOutcome compile(CompilationRequest request,
                               CancellationToken cancellation);

    CompilationOutcome compile(CompilationRequest request,
                               AnalysisReuse reuse,
                               CancellationToken cancellation);

    CompilationOutcome compile(CompilationRequest request,
                               AnalysisReuse reuse,
                               RunContext context,
                               CancellationToken cancellation);
}
```

Der einfache Aufruf ist äquivalent zu `AnalysisReuse.none()`. `analyze` arbeitet mit dem EDITOR-Syntaxmodus, soweit kein bereits nachgewiesen gleichwertiges striktes Syntaxergebnis wiederverwendet wird. `compile` verwendet STRICT-Akzeptanz. Wiederverwendung von EDITOR-Ergebnissen ist nur zulässig, wenn die vollständige strikte Akzeptanz nachgewiesen ist; in P1 ist erneutes striktes Parsen der sichere Default.

Overloads ohne `RunContext` erzeugen nach Prüfung offensichtlicher API-Argumentfehler über die injizierte Factory einen neuen Rootkontext und delegieren auf die volle Variante. Nur diese vollständige Ausführung besitzt den Compiler-Lifecycle; Conveniencewrapper emittieren keinen zusätzlichen Anfang oder Abschluss. Der volle Overload erhält einen frischen, noch nicht für eine andere Ausführung verwendeten Kontext. Er startet und beendet unter dessen RunId genau den eigenen Lauf nach Kapitel 09.

`RunContext` enthält unveränderliche Lauf-/Elternidentität und begrenzte Korrelationsattribute; er ist kein bereits laufender geliehener Scope und enthält keinen Workspace oder Metamodellgraph. Der Workspace beziehungsweise ein Host kann einen solchen Kontext ausdrücklich bis zur Engine durchreichen, darf dann aber nicht unter derselben RunId einen zweiten Lifecycle erzeugen. Wenn der Host einen echten übergreifenden Lauf für Beschaffung, Compiler und anschliessenden Export beobachtet, erhält die Engine einen über `createChild(parent)` erzeugten eigenen Kindkontext. Damit sind Elternbeziehung und Abschlussbesitz ausdrücklich, ohne ThreadLocal, globale aktive Contexts oder konkurrierende Sequenzzähler derselben RunId.

Die Engine benötigt pro Lauf einen neuen `CompilationRun`. Sie besitzt keine veränderlichen Quellen, die ein Aufrufer zwischen Phasen austauschen kann. Die Engine kann konzeptionell gleichzeitig für mehrere unabhängige Requests verwendet werden; der P1-Workspace organisiert seine Mutationen seriell. Eine Implementierung darf interne immutable Grammatik-/Builtinstrukturen teilen, aber keine Lexerinstanzen oder semantischen Builder.

`analyze` liefert bei regulären Quellfehlern einen `AnalysisSnapshot`. Bei angefordertem Abbruch wirft dieser bequeme Queryeinstieg `OperationCancelledException`; Workspace-/WASM-Adapter übersetzen dies in ihre typisierten Abbruchantworten. Nicht abgefangene interne Fehler werden als `CompilerInternalException` mit `FailureId` übertragen. Wer alle Ausgänge ohne Exceptionpfad benötigt, verwendet `compile` beziehungsweise den entsprechenden Workspace-Outcome. Diese Differenz ist dokumentiert und muss in Adaptertests berücksichtigt werden.

### 4.2 `CompilationOutcome`

```java
public sealed interface CompilationOutcome
        permits CompilationOutcome.Succeeded,
                CompilationOutcome.Rejected,
                CompilationOutcome.Cancelled,
                CompilationOutcome.Failed {

    record Succeeded(CompiledSchema schema, AnalysisSnapshot analysis)
            implements CompilationOutcome {}

    record Rejected(AnalysisSnapshot analysis, RejectionSummary rejection)
            implements CompilationOutcome {}

    record Cancelled(Optional<AnalysisSnapshot> analysis,
                     CancellationReason reason)
            implements CompilationOutcome {}

    record Failed(FailureId failureId, Diagnostic error)
            implements CompilationOutcome {}
}
```

`Succeeded.schema.snapshotId()` und `Succeeded.analysis.snapshotId()` stimmen überein. Ein vorhandener Analysestand in `Cancelled` ist eine konsistent gefrorene Teilsicht mit unvollständiger Coverage, niemals ein Erfolg. `Failed` enthält eine sichere benutzbare interne Fehlerdiagnose; Stacktrace und technische Ursachen gehen zusätzlich an den RunObserver aus09, nicht ungefiltert in Browsernachrichten.

Die Ablehnungszusammenfassung wird vom Gate erzeugt und beschreibt ausschliesslich die angeforderte Rootclosure:

```java
public record RejectionSummary(
    Set<RejectionReason> reasons,
    List<DiagnosticId> diagnosticIds,
    List<ModelSelector> requestedRoots,
    List<String> unsupportedFeatureIds
) {}

public enum RejectionReason {
    SOURCE_ERRORS,
    WARNINGS_REJECTED,
    MISSING_INPUT,
    AMBIGUOUS_INPUT,
    ENVIRONMENT_FAILURE,
    INCOMPLETE_SUPPORT
}
```

`reasons` und `requestedRoots` sind nichtleer und defensiv kopiert. Die Gründe werden bei Anzeige und Serialisierung in deklarierter Enumreihenfolge geordnet. Diagnose-IDs gehören zum zugehörigen Analysesnapshot und erklären relevante Ablehnungsgründe; unabhängige Fehler ausserhalb der Auswahl werden nicht hineingenommen. `unsupportedFeatureIds` enthält die tatsächlich betroffenen stabilen Featurekennungen in kanonischer Reihenfolge und ist ohne `INCOMPLETE_SUPPORT` leer. Mehrere Gründe können gleichzeitig vorliegen.

`SOURCE_ERRORS` bezeichnet nachgewiesene Lexer-, Syntax- oder Semantikfehler des Quellmodells. Eine fehlende Quelle ist `MISSING_INPUT`, eine nicht eindeutige Auswahl `AMBIGUOUS_INPUT`, eine technisch blockierte erforderliche Beschaffung `ENVIRONMENT_FAILURE`. Fehlende eigene Compilerfähigkeit ist `INCOMPLETE_SUPPORT`. `WARNINGS_REJECTED` bezeichnet die bewusste Warnungspolitik. Dadurch wird eine Ablehnung nicht pauschal als Beweis sprachlicher Ungültigkeit missverstanden.

CLI, LSP und WASM transportieren beziehungsweise rendern diese Zusammenfassung. Sie dürfen die Closuregründe nicht selbst aus der ungefilterten gesamten Diagnoseliste ableiten oder eine zweite Variante des Compile-Gates implementieren. Ein API-Aufrufer kann die vollständige Analyse zusätzlich betrachten, erkennt aber an `RejectionSummary` die vom Gate tatsächlich entscheidenden Gründe.

`CancellationReason` ist ein gemeinsamer Foundationtyp mit mindestens `USER_REQUESTED`, `SUPERSEDED`, `DEADLINE_EXCEEDED`, `RESOURCE_LIMIT` und `HOST_SHUTDOWN`. `OperationCancelledException.reason()` verwendet denselben Typ. Der Host setzt die passenden Gründe für externe Abbrüche; der reine Kern erzeugt `RESOURCE_LIMIT` beim eigenen Budget. Weder Enum noch Ausnahme dürfen vom Workspace oder Compiler-Snapshottyp abhängen. Ein eventuell verfügbares Teilergebnis wird im Compile-Outcome transportiert, nicht durch einen zyklischen Snapshotverweis in der Foundation-Ausnahme.

Ungültige Java-Argumente werden vor dem Start per Exception zurückgewiesen. `Failed` ist kein Sammelbecken für Tippfehler, fehlende Imports oder nicht unterstützte P1-Konstrukte. Letztere sind sichtbare Einschränkungen der Analyse und führen zu `Rejected`, wenn sie die Rootclosure betreffen. Fatale JVM-Fehler wie ein nicht mehr zuverlässig behandelbarer `OutOfMemoryError` werden nicht als gewöhnlicher Modellfehler kaschiert; der Host muss den Lauf als fehlgeschlagen erkennen.

### 4.3 `AnalysisSnapshot` und Coverage

```java
public final class AnalysisSnapshot {
    public SnapshotId snapshotId();
    public SemanticGraph semanticGraph();
    public SemanticQueries queries();
    public Map<SourceId, SourceRevision> sourceRevisions();
    public Map<SourceId, SyntaxSnapshot> syntaxSnapshots();
    public Optional<SyntaxSnapshot> syntaxSnapshot(SourceId source);
    public List<Diagnostic> diagnostics();
    public AnalysisCoverage coverage();
    public CompilationFingerprint fingerprint();
    public AnalysisProvenance provenance();
}
```

Die Snapshotklasse ist unveränderlich. `diagnostics()` enthält alle kanonischen Diagnosen, auch aufgrund einer gemeinsamen Ursache in einer UI nicht angezeigte Folgefehler. Die Darstellung wird in09 projiziert; das Gate darf nicht die reduzierte sichtbare Liste verwenden.

`AnalysisCoverage` enthält Einträge pro benötigter Deklaration/Modell/Phase. `CoverageStatus` unterscheidet `COMPLETE`, `BLOCKED_BY_ERROR`, `NOT_IMPLEMENTED`, `NOT_REQUESTED`, `CANCELLED` und `BUDGET_EXCEEDED`. Syntax und Semantik können in unterschiedlichen Zuständen sein. Ein leerer Eintrag ist keine implizite Vollständigkeit. Für jede ausgewählte Rootclosure muss das Gate das explizite Abschlussprädikat `isCompleteFor(ClosureSelection)` prüfen können.

Coverage und Diagnostik erfüllen unterschiedliche Aufgaben: Eine fehlende Implementierung bleibt auch dann blockierend, wenn der Diagnosecollector sein Ausgabelimit erreicht hat. Umgekehrt dürfen bekannte reine Warnungen einen vollständig geprüften Status besitzen. Jeder Fehler muss eine nachvollziehbare Zuordnung zu Source-/Symbol-/Globalbedingungen erhalten; unzuordenbare Eingabefehler werden konservativ als relevant behandelt.

### 4.4 `AnalysisReuse`

```java
public record AnalysisReuse(
    Optional<AnalysisSnapshot> previousAnalysis,
    Map<SourceId, SyntaxSnapshot> previousSyntax
) {
    public static AnalysisReuse none();
}
```

Diese Eingabe ist eine unveränderliche Beschleunigungs- und Identitätshilfe. Sie wird nicht als bereits bestätigte Wahrheit akzeptiert. `ReusePlanner.plan(request,reuse)` prüft Text-, Quellen-/Revisions-, Grammatik-, Sprach-, Options-, Modus- und Abhängigkeitsfingerprints, bevor ein Ergebnis wiederverwendet wird. Nicht passende Einträge werden verworfen. Untereinander widersprüchliche vom API-Aufrufer selbst konstruierte Eingaben können als ungültige Argumente abgelehnt werden; ein normaler geänderter Text ist keine Exception.

`IdentityReconciler` verwendet nachweislich unveränderte, eindeutige Deklarationsanker samt Scope/Art und Textstruktur. Eine Einfügung vor einer Deklaration darf deren Offset ändern, ohne zwingend ihre ID zu ändern. Ein Name allein reicht nicht. Bei Duplikaten oder unklarer Zuordnung werden neue IDs vergeben. Die Relation alter/neuer Identitäten kann als eigenes Delta an Projektionen gehen; sie darf keine falsche semantische Gleichsetzung erzeugen.

Eine frische Kompilierung und eine wiederverwendende Kompilierung müssen fachlich gleich sein. Opaque IDs, Cachemetriken und Runmetadaten werden beim semantischen Vergleich normalisiert. Wenn dieselben Eingaben mit unterschiedlichen Wiederverwendungswegen verschiedene ID-Belegungen erzeugen, müssen Snapshotidentitäten dies unterscheiden. Ein Snapshotfingerprint darf deshalb nicht nur den Text hashieren und dadurch voneinander abweichende Handle-Räume als identisch ausgeben.

## 5. Interne Pipeline und Klassenverträge

### 5.1 Laufbesitz

`DefaultCompilerEngine` delegiert an `CompilationOrchestrator`. Dieser erzeugt `CompilationRun`, der Request, Modus, Budgetzähler, CancellationToken, Diagnosecollector, Observer und ausschliesslich laufbezogene Builder besitzt. Der Lauf wird nach Beendigung verworfen. Publiziert werden nur gefrorene Ergebnisse.

Die endgültige Graphfreeze-Operation überprüft Referenzintegrität und Collectionbesitz. Ein Fehler während Freeze darf keine halb veränderliche Sicht an LSP oder CLI herausgeben. Der Observer ist ein Port nach Kapitel 09; der Kern kennt kein Loggingbackend.

| Klasse | Zentrale Methode | Verantwortung und Nachbedingung |
|---|---|---|
| `CompilationOrchestrator` | `run(request,reuse,mode,token)` | Koordiniert Phasen; klassifiziert Ergebnisse, hält keine globale Sitzung. |
| `CompilationRun` | `execute()` | Besitzt alle veränderlichen Laufdaten und Fortschritts-/Budgetzähler. |
| `ReusePlanner` | `plan(request,reuse)` | Klassifiziert zulässige Wiederverwendung mit überprüfbaren Gründen. |
| `SourceSetVerifier` | `verify(SourceSet)` | Prüft IDs/Revisionen/Importstellen, bevor Bindinginformationen verwendet werden. |
| `SyntaxPhase` | `parseSources(runInputs)` | Liefert Syntaxsnapshots und Coverage je Quelle. |
| `DeclarationIndexer` | `index(syntaxSnapshots,builtins)` | Deklarationen, Owner, Namen, Quellanker, Duplikate; löst keine unzulässige Sichtbarkeit vorzeitig auf. |
| `RootSelector` | `select(roots,index)` | Liefert genaue Rootmenge und Diagnose für fehlende/mehrdeutige/leere Auswahl. |
| `ImportBinder` | `bind(importSites,sourceSet,index)` | Verifiziert Ziele und baut Abhängigkeitskanten mit Importursprung. |
| `ScopeBuilder` | `build(declarations,imports,profile)` | Explizite Scopes mit Sichtbarkeit und Deklarationsreihenfolge. |
| `ReferenceBinder` | `bind(reference,context)` | Geprüftes Ziel je Namenssegment oder typisierter Fehlerzustand. |
| `DependencyAnalyzer` | `analyze(graphDraft)` | Fachliche Abhängigkeiten, Komponenten und unzulässige Zyklen. |
| `TypeResolver` | `resolve(typeSyntax,context)` | Eigene Typdeskriptoren; Alias-/Generic-/Errorzustände getrennt. |
| `EffectiveMemberResolver` | `resolve(viewable,context)` | Effektive Reihenfolge, Überschreibungs-/Vererbungsherkunft. |
| `ExpressionTypeChecker` | `check(expression,context)` | Typisierte Ausdrücke und Pfade; keine Datenobjektauswertung. |
| `SemanticRuleEngine` | `check(declaration,context)` | Fachliche Regeln der jeweiligen Deklarationsart. |
| `TranslationChecker` | `check(translation,base,context)` | Korrespondenz und erlaubte Unterschiede. |
| `GraphFreezer` | `freeze(draft,coverage,provenance)` | Vollständige unveränderliche Graphintegrität. |
| `CompilationGate` | `evaluate(snapshot,request,closure)` | Erzeugt entweder geprüfte Sicht oder dokumentierte Ablehnungsgründe. |

Kontextobjekte sind kleine interne Klassen: `BindingContext`, `TypeCheckingContext` und `RuleContext`. Sie verweisen auf den laufbezogenen Builder, Sprachprofil, Scope, Diagnosecollector und Budget/Cancellation. Sie dürfen nicht jeweils eine versteckte zweite Kopie des ganzen Modells aufbauen.

### 5.2 Phasenreihenfolge und erlaubte Rückkopplung

1. **Eingabeprüfung und Syntax.** Vollständigen Quellvertrag prüfen, beide Fehlerquellen sammeln, Header und Recoveryzustände erfassen.
2. **Deklarationsindex.** Alle sicher erkennbaren Deklarationen mit Originalgrenzen und Ownern registrieren. Duplikate bleiben als getrennte Symbole erhalten.
3. **Wurzeln, Imports und Scopes.** Resolverangaben verifizieren, Rootclosure bestimmen und erlaubte Sichtbarkeit aufbauen. Negative Importbeziehungen bleiben für Invalidierung sichtbar.
4. **Referenzen und Abhängigkeiten.** Namen segmentweise binden. Für referenzierbare spätere Elemente werden nur jene Vorwärtsbezüge zugelassen, die das Sprachprofil erlaubt.
5. **Typen und Vererbung.** Domänen, Einheiten, Basistypen, Restriktionen, effektive Mitglieder und Kontexte auflösen. Abhängigkeitskomponenten verhindern naive Endlosrekursion.
6. **Ausdrücke und Fachregeln.** Pfade, Constraints, Views, Funktionen, Graphics, Übersetzungen und weitere Typfamilien nach Kapitel 05 prüfen.
7. **Freeze und Gate.** Coverage, Diagnosen und Graphintegrität prüfen; nur danach Ergebnis veröffentlichen.

Dies ist eine logische Reihenfolge, keine Pflicht zu genau sieben Vollscans. Einzelne fachliche Abhängigkeiten können kontrollierte Unterphasen benötigen. Beispielsweise stehen Funktionssignaturen vor der Typprüfung ihrer Aufrufe bereit, während vollständige Constraintprüfung später erfolgt. Eine solche Rückkopplung wird als deklarierte Abhängigkeit oder Komponentenverarbeitung implementiert, nicht als „wiederhole alles, bis zufällig nichts mehr passiert“.

Für jede iterative Auflösung wird eine endliche Zustandsmenge beziehungsweise Fortschrittsbedingung festgelegt. Wird kein Fortschritt mehr erreicht, entstehen blockierte/unaufgelöste Zustände mit Diagnose. Der Compiler darf unvollständige Ergebnisse nicht durch willkürliche mehrfach erhöhte Iterationszahlen erfolgreich erklären.

## 6. Fachliche Prüfdienste auf Methodenebene

### 6.1 Namen, Imports und Sichtbarkeit

`ReferenceBinder.bind(NameReference reference, BindingContext context)` liefert `BindingResolution`. Es nimmt den tatsächlichen Verwendungsscope, die Sprachversion und die Quellposition entgegen. Es unterscheidet lokalen Namen, geerbten Namen, qualifizierten Import, UNQUALIFIED-Import und Builtin. Der globale Index dient Kandidatensuche, nicht der Überschreibung von Sprachregeln.

Mehrdeutigkeit bleibt ein eigener Ausgang. Ein expliziter Name darf nicht nach Dateireihenfolge, Netzwerkantwortzeit oder HashMapiteration aufgelöst werden. Ein fehlendes Ziel erzeugt eine Hauptdiagnose; davon abhängige Typfehler tragen denselben Ursachenzusammenhang. Die in09 definierte Darstellung kann Folgefehler reduzieren, ohne die Ursachen aus dem Snapshot zu löschen.

### 6.2 Vererbung und Restriktionen

`InheritanceChecker.check(SymbolId derived, SymbolId base, RuleContext context)` prüft zulässige Elementarten, Final-/Abstract-/Extended-Regeln, Topickontext, Zyklen und weitere versionierte Bedingungen. `AttributeOverrideChecker.check(...)` prüft den Bezug zur tatsächlichen Basiseigenschaft, Typ-/Kardinalitäts-/Definiertheitseinschränkungen und erlaubte Modifier. Ein gleicher Name allein ist kein bestätigtes Override.

`EffectiveMemberResolver` arbeitet danach auf einer nachweislich verwertbaren Vererbungskette. Bei ungültiger Basis kann es die sicher bekannten lokalen Mitglieder für IDE-Zwecke ausgeben, muss aber die vollständige effektive Sicht als blockiert beziehungsweise ungültig markieren. Es darf nicht eine lokale Liste als vollständige Transferreihenfolge ausgeben.

### 6.3 Typen und Ausdrücke

`TypeResolver.resolve` delegiert auf kleine Fachregeln wie `NumericTypeRules`, `EnumerationRules`, `GeometryTypeRules`, `ReferenceTypeRules`, `FormattedTypeRules` und `GenericBindingRules`. Diese Dienste rufen die Relationselemente aus05 auf und ergänzen kontextbezogene Diagnostik. Methoden und Tests werden je Fachfamilie benannt; eine monolithische mehrere tausend Zeilen lange Visitor-Methode ist zu vermeiden.

`ExpressionTypeChecker.check(ExpressionSyntax syntax, ExpressionContext context)` ermittelt Ergebnisart und Definiertheits-/Kardinalitätswirkung. `ObjectPathResolver.resolve(...)` prüft jeden Pfadschritt. `FunctionCallChecker.check(...)` prüft Aufrufziel, Argumentanzahl, Sonderargumentformen, Typverträglichkeit und Rückgabetyp. Ein unbekanntes Argument wird nicht automatisch als `ANY` passend gemacht.

Jede Regel gibt bekannte Teilinformationen zurück, wenn diese unabhängig vom Fehler sicher sind. Sie darf aber keine provisorische Schlussfolgerung als bestätigt speichern. Zum Beispiel kann das Ziel einer Funktion bekannt sein, während der Typ eines Arguments noch blockiert ist; Hover darf die Signatur zeigen, während die Gesamtprüfung unvollständig bleibt.

### 6.4 Constraints, Views, Translation und Graphics

`ConstraintChecker.check(ConstraintSymbol,RuleContext)` dispatcht exhaustiv über die Constraintvarianten aus05. Es prüft den erlaubten Ausführungskontext, boolesche Bedingungen, Pfad-/Zielverträglichkeit, lokale beziehungsweise globale Schlüssel und versionsabhängige Optionen. Es zählt keine realen Transferobjekte.

`ViewChecker.check(ViewSymbol,RuleContext)` besitzt getrennte Methoden für Projection, Join, Union, Aggregation und Inspection. Es prüft Basisaliase, Selektionen, Attributableitungen, Ergebnistypen und zulässige Erweiterungen. Für einen gültigen aber noch nicht implementierten Viewzweig wird eine `NOT_IMPLEMENTED`-Coverage erzeugt, kein leerer View als Erfolg.

`TranslationChecker` bindet Original und Übersetzung anhand überprüfter fachlicher Korrespondenz. Eine Übersetzung darf nicht durch globalen Stringersatz „geprüft“ werden. `GraphicChecker` prüft Signaturklassen, Parameter und bedingte Zuordnungen; es zeichnet kein UML-Diagramm. Diese Fachprüfungen gehören zum vollständigen Compiler, auch wenn ein erster Consumer sie nicht verwendet.

### 6.5 Regeln als überprüfbare Einzelverträge

Die folgenden Methoden sind zusätzlich zu den Orchestrierungsmethoden verbindliche fachliche Verantwortlichkeiten. Sie können als Package-private Methoden der genannten Dienste umgesetzt werden; pro Methode eine neue öffentliche Klasse ist nicht verlangt. `RuleResult` enthält `RuleStatus { SATISFIED, VIOLATED, BLOCKED }`, erzeugte Diagnose-IDs und die für das Ergebnis benutzten Abhängigkeiten. `SATISFIED` darf nur nach Prüfung aller benötigten Eingaben entstehen. `BLOCKED` ist kein bestandenes Ergebnis und verweist auf seine Ursache.

| Dienst/Methode | Erforderliche Prüfung und Ergebnisinformation |
|---|---|
| `ModelRules.checkEndName(model,closingName)` | Anfangs- und Endname vergleichen; Fehler nennt beide Originalbereiche. Recovery darf den falschen Endnamen nicht automatisch ersetzen. |
| `ModelRules.checkTranslationTarget(model,target)` | Modellart, explizite Zielversion und erlaubte Translationbeziehung prüfen; Zielversion niemals nach Semver raten. |
| `TopicRules.checkDependencyUse(use,targetTopic)` | Kontext der Verwendung gegen deklarierte/zulässig geerbte Topicabhängigkeiten prüfen; Import allein ist kein allgemeiner Zugriffsnachweis. |
| `ModifierRules.check(kind,modifiers,profile)` | Doppelte und unzulässige Modifierkombinationen erkennen. Der Parser muss die ursprünglichen Vorkommen dafür erhalten. |
| `InheritanceChecker.checkAcyclic(component)` | Einen unzulässigen Zyklus mit geordnetem Referenzpfad diagnostizieren; unabhängige Komponenten weiterbearbeiten. |
| `AttributeOverrideChecker.checkInheritedTarget(attribute,scope)` | Tatsächliche Basiseigenschaft identifizieren; gleichnamige fremde Eigenschaft gilt nicht als Override. |
| `NumericTypeRules.checkBounds(type)` | Unter-/Obergrenze mit exakter Zahlendarstellung vergleichen; Präzision und Referenzsystemangaben getrennt ausweisen. |
| `NumericTypeRules.checkRestriction(derived,base)` | Erlaubte Bereichs-, Genauigkeits-, Einheiten- und Richtungsänderung je Profil prüfen; keine alleinige Doublebereichsprüfung. |
| `UnitRules.resolveDimension(unit)` | Basis-/abgeleitete/zusammengesetzte Einheit als exakte beziehungsweise symbolische Dimension herleiten; zyklische Definition blockieren. |
| `UnitRules.checkConversion(unit)` | Umrechnungsdefinition auf erlaubte Operanden und Basisbezug prüfen; eine externe Funktionsbeschreibung nicht als ausführbaren Java-Code interpretieren. |
| `EnumerationRules.mergeExtension(derived,base)` | Ergebnisbaum mit Deklarationsherkunft und Reihenfolge erzeugen; doppelte vollständige Elementpfade und unerlaubte Erweiterung finaler Teile diagnostizieren. |
| `EnumerationRules.checkOrdering(derived,base,profile)` | ORDERED/CIRCULAR- und Erweiterungsregeln gesondert prüfen. Eine sortierte Blattliste ersetzt den Baum nicht. |
| `ReferenceTypeRules.checkTargets(type,scope)` | Zulässige Zielarten, Restriktionen und EXTERNAL-/Topicgrenzen prüfen; nicht existente Ziele bleiben blockierte Bindungen. |
| `CollectionRules.checkCardinality(type)` | Lower/Upper-Invarianten und kontextabhängige Vorgaben prüfen; unbounded explizit erhalten. |
| `CollectionRules.checkElementRestriction(type,base)` | Struktur-/Referenzzielrestriktionen anhand tatsächlicher Vererbung prüfen; Textname allein reicht nicht. |
| `GeometryTypeRules.checkCoordinateAxes(type,profile)` | Achsentypen, erlaubte Dimension, Rotation, Achsenindex und Referenzsystembezüge prüfen; jede Ableitung nennt ihre Basisdomäne. |
| `GeometryTypeRules.checkLineDefinition(type,profile)` | Vertexdomäne, erlaubte Segmentformen, Richtung, Linienattribute und Überlappungsangaben prüfen; keine Datengeometrie auswerten. |
| `FormattedTypeRules.checkFormatBindings(type)` | Jedes Formatteilstück auf erlaubtes Basisattribut/Domainformat binden; fehlende und nicht passende Teile lokalisieren. |
| `GenericBindingRules.checkContext(context)` | Generic-Domäne, zulässige konkrete Alternativen und Kontextzuordnung prüfen; Kontext dupliziert keine Typinstanz durch Mutation. |
| `GenericBindingRules.checkDeferred(topic)` | Rechtmässigkeit der aufgeschobenen Bindung prüfen und deren offene fachliche Bedingung im Modell speichern. |
| `AssociationRules.checkRoles(association)` | Rollenarten, Ziele, Kardinalitäten, Ordnungs-/External-/Kompositionsbedingungen, Vererbung und n-stellige Form nach Profil gemeinsam prüfen. |
| `OidRules.checkOwnerAndRestriction(owner,oid)` | OID-Regel an erlaubtem Modell-/Topic-/Klassen-/Assoziationskontext prüfen; Strukturen nicht mit erfundener Objekt-ID versehen. |
| `ConstraintChecker.checkMandatory(constraint)` | Kontext und boolesches Ergebnis prüfen; ein unbekannter Operand ergibt BLOCKED, kein stilles `true`. |
| `ConstraintChecker.checkExistence(constraint)` | Quellpfad und alle erlaubten Zielpfade vollständig auflösen; vergleichbare Werte und erlaubte Zielkontexte nachweisen. |
| `ConstraintChecker.checkUniqueness(constraint)` | Schlüsselpfade, Kombination, LOCAL/BASKET-Kontext und optionale Vorbedingung prüfen; späteren Validierungsscope deklarieren. |
| `ConstraintChecker.checkPlausibility(constraint)` | Prozentangabe und Relationsrichtung sowie booleschen Ausdruck prüfen; keine Datenanteile während des Compiles zählen. |
| `ConstraintChecker.checkSet(constraint)` | Mengen-/Basket-/Lokalkontext und Vorbedingung getrennt typisieren; erforderlichen späteren Ausführungsumfang erhalten. |
| `ViewChecker.checkUnion(view)` | Basen und zulässige Entsprechung resultierender Eigenschaften prüfen; Herkunft jedes abgeleiteten Attributs erhalten. |
| `ViewChecker.checkJoin(view)` | Eindeutige Basisaliase und fachliche Bedeutung optionaler Basen prüfen; Definiertheit nicht mit einem SQL-Default erfinden. |
| `ViewChecker.checkAggregation(view)` | Gruppierungs-/ALL-Form, Schlüsselpfade und aggregatbezogene Referenzkontexte prüfen. |
| `TranslationChecker.compareElements(original,translated)` | Korrespondenz von Typ, Reihenfolge, Kardinalität, Rolle, Constraint und weiterem Elementinhalt über den normativen Vergleich prüfen. |
| `GraphicChecker.checkParameterAssignment(assignment)` | Signaturparameter, Ausdruckstyp beziehungsweise Metaobjekt und bedingte Enumzuordnung binden und prüfen. |

Die Versionstabelle aus04 hinterlegt für jeden versionsabhängigen Einzelvertrag den Referenzhandbuchabschnitt und die konkreten Parameter/zulässigen Kombinationen. Der Coding-Agent muss diese Tabelle vor Freigabe des betreffenden Features mit den Originalregeln befüllen. Unbekannte Parameter sind kein frei wählbarer Default. Das ist insbesondere für numerische Genauigkeit, Koordinatendimensionen, Vererbungsrestriktionen, Assoziationsbedingungen und Übersetzungsgleichheit vorgeschrieben. Eine „bislang keine Fehler gefunden“-Methode ohne solche Daten und Negativfixtures ist nicht implementiert.

Die Fachregeln dürfen ein gemeinsames Resultatformat verwenden, aber kein frei konfigurierbares Plugin-/Regel-Framework ist für P1 erforderlich. Normative Compilerregeln werden als normaler, testbarer Java-Code aufgerufen. Eine durch spätere Consumer gewünschte Sonderregel darf die Sprachgültigkeit nicht unbemerkt ändern.

## 7. Gültigkeitsgate und Veröffentlichung

`CompilationGate.evaluate` prüft in dieser Reihenfolge:

1. Die Rootauswahl ist vorhanden, eindeutig und nicht versehentlich leer.
2. Jede benötigte Source-/Importbindung bezeichnet eine verfügbare, passende Revision.
3. Die Rootclosure ist abgeschlossen; unbekannte Pflichtabhängigkeiten sind sichtbar blockierend.
4. Alle relevanten Dokumente/Bereiche erfüllen die strikte Syntaxakzeptanz inklusive EOF und Lexerfehlerfreiheit.
5. Alle erforderlichen semantischen Phasen und Features besitzen vollständige Coverage.
6. Es gibt in dieser Closure keine fachlichen Errors, Error-/Pending-Typen oder unaufgelösten Pflichtreferenzen.
7. Legale Generic-/Deferred-Zustände erfüllen ihre sprachlichen Gültigkeitsbedingungen.
8. Die konfigurierte Warnungspolitik ist erfüllt.
9. Graphintegrität und Herkunft sind konsistent, und kein Abbruch wurde vor Publikation angefordert.

Erst dann wird `DefaultCompiledSchema` erzeugt. `GateResult` ist eine interne sealed Ergebnishierarchie `Accepted(CompiledSchema)` beziehungsweise `Denied(List<GateBlocker>, RejectionSummary)`. Jeder Blocker besitzt Art, betroffene Closurestelle und Diagnose-/Coveragebezug. `CompilationGate` erzeugt daraus die öffentliche Zusammenfassung mit den ausschliesslich relevanten Gründen. Der Orchestrator übernimmt sie unverändert in `CompilationOutcome.Rejected`. Das Gate hängt keine neuen willkürlichen Folgefehler an, sondern benennt die bereits vorhandenen Gründe und ergänzt lediglich bisher fehlende erforderliche Abschlussdiagnostik.

Die ausgestellte Graph-/Querysicht ist auf die geprüfte Closure beschränkt, wie in05 festgelegt. Ein umfassender Analysesnapshot kann daneben unabhängige fehlerhafte Modelle besitzen. Das Gate darf diese nicht durch einen einfachen Verweis auf einen ungefilterten Gesamtgraphen als scheinbar geprüfte Schemainhalte ausgeben. Die Auswahl teilt unveränderliche Graphdaten und benötigt keine erneute fachliche Kompilierung.

Ein Workspace darf den so erzeugten Snapshot wegen einer inzwischen neueren Revision noch als veraltet kennzeichnen oder seine Publikation verwerfen. Das verändert nicht seine Aussage über den eingefrorenen alten Stand. Das Protokoll zur aktuellen Anzeige und zu Schreibaktionen ist in07 festgelegt.

## 8. Abbruch, Budgets und Laufbeobachtung

`CancellationToken` ist der gemeinsame Foundationvertrag `isCancellationRequested()` und `throwIfCancellationRequested()`. Jeder Phaseneinstieg und jede längere Schleife besitzt Prüfpunkte. Tokenisierung, Knotenerzeugung, Indexieren und Graphdurchläufe zählen Arbeitseinheiten. Überprüfungen in inneren Schleifen werden ausreichend regelmässig gesetzt, ohne eine harte nicht gemessene Millisekundengarantie zu behaupten.

Die gemeinsamen Ausführungstypen liegen in `guru.interlis.execution` innerhalb von `interlis-model`: `CancellationToken`, `CancellationReason`, `OperationCancelledException`, `FailureId`, `RunContext`, `RunContextFactory` und die Beobachtungsverträge aus09. Es entsteht dafür kein zusätzliches Modul und keine Rückabhängigkeit auf den Compiler. Contexts werden pro Aufruf explizit übergeben; kein ThreadLocal stellt vermeintlich den richtigen Lauf bereit.

Bei Diagnosebudgetüberschreitung werden Ausgabe und Coverage getrennt behandelt. Das letzte sichtbare Ereignis erklärt das Limit; weitere tatsächliche Fehler können als Zähler-/Blockerzustand erfasst werden. Das Gate sieht weiterhin, dass kein vollständiger Erfolg bewiesen ist. Für tiefe Verschachtelung wird der Schutz vor Stackoverflow früh an Lexer-/Parser-/Buildergrenzen eingeplant; ein Schutz erst nach dem rekursiven Durchlauf ist wirkungslos.

Wenn das Analyse-/Diagnosebudget die vollständige Prüfung verhindert, beendet der Orchestrator den Lauf mit `Cancelled(RESOURCE_LIMIT)` beziehungsweise der oben beschriebenen Analyseausnahme. Ein lediglich UI-seitiges Limit der dargestellten Diagnosen ist davon verschieden: Es verändert weder den kanonischen Snapshot noch den Abschlussstatus. Die Tests müssen diese zwei Grenzen auseinanderhalten.

Observerereignisse verwenden die Phasen aus09: `SOURCE_RESOLUTION`, `LEXING`, `PARSING`, `DECLARATION_INDEXING`, `BINDING`, `TYPE_CHECKING`, `SEMANTIC_CHECKING` und gegebenenfalls `PROJECTION`. Compilerphasen erfinden keine abweichenden Enumbezeichnungen. Fachliche Diagnosen bleiben Daten; Laufzeiten, Cachetreffer und Abbruchgründe werden als Betriebsereignisse gemeldet.

## 9. Verbindliche Testfälle und Implementierungsreihenfolge

### 9.1 Erste fachliche Strecke P1

P1 implementiert vollständige Syntaxverarbeitung für den erprobten Teil sowie eine echte kleine Semantik: Modellköpfe, Import, Topic, Klasse/Struktur, TEXT, numerische Domäne mit exaktem Wert, Attribut, einfache Vererbung und eine boolesche Mandatory-Bedingung mit typisiertem Pfad beziehungsweise Vergleich. Für jede dabei verwendete Variante muss es einen Fehlerfall geben. Die genaue freigegebene Featureliste wird maschinenlesbar ausgewiesen.

Ein P1-Compile auf ein im Parser erkennbares, aber semantisch nicht implementiertes Konstrukt muss `Rejected` mit `NOT_IMPLEMENTED` ergeben. Eine allgemeine P1-Aussage „INTERLIS 2.3/2.4 unterstützt“ ist unzulässig; die beiden Syntaxprofile und die implementierte fachliche Teilmenge werden separat beschrieben.

### 9.2 Testtabelle

| Test | Eingriff | Verbindliches Ergebnis |
|---|---|---|
| `CompilerEngineTest.validImportedDomainAndConstraint` | Zwei Modelle, importierte Domäne, Vererbung und gültiger Constraint | Erfolg; Zielreferenz, effektiver Typ und typisierter Ausdruck abfragbar. |
| `CompilerEngineTest.unresolvedDomainRejectsClosure` | Domänenname gezielt falsch | `Rejected`; Hauptursache am Namen; kein erfundener Defaulttyp. |
| `CompilerEngineTest.invalidConstraintOperandTypes` | TEXT mit fachlich unzulässigem numerischem Operator | Konkrete Typdiagnose und `Rejected`, obwohl Syntax gültig ist. |
| `CompilerEngineTest.unrelatedBrokenModelDoesNotBlock` | Unabhängige fehlerhafte Quelle zum SourceSet hinzufügen | Dieselbe gültige Rootclosure kompiliert; unabhängige Analyse bleibt sichtbar fehlerhaft. |
| `CompilerEngineTest.resolverBindingRevisionMismatch` | Zielrevision der Importbindung passt nicht zum Text | Sichtbarer Auflösungs-/Vertragsfehler; kein Erfolg. |
| `ReferenceBinderTest.respectsForwardReferenceRules` | Global vorhandenes Ziel ist am Verwendungsort sprachlich unzulässig | Ablehnung trotz erfolgreicher Indexsuche. |
| `CompilationGateTest.hiddenDiagnosticStillBlocks` | Folgefehlerdarstellung unterdrückt Haupt-/Folgeansicht nach Testpolicy | Gate prüft kanonische Fehler und bleibt geschlossen. |
| `CompilationGateTest.unimplementedFeatureCannotPass` | Reale Parsevariante ohne fertig implementierte Semantik | `NOT_IMPLEMENTED`, sichtbare Diagnose, `Rejected`. |
| `CompilationGateTest.rejectionSummaryUsesOnlyRelevantClosure` | Eine Root scheitert wegen fehlendem Input; ein unabhängiges Modell hat Syntaxfehler | Summary enthält `MISSING_INPUT`, ohne den fremden Syntaxfehler zu `SOURCE_ERRORS` der Root zu machen. |
| `CompilationGateTest.distinguishesSourceAndImplementationFailures` | Ein echter Typfehler und eine nicht implementierte erforderliche Fähigkeit im Rootmodell | Summary enthält `SOURCE_ERRORS` und `INCOMPLETE_SUPPORT` samt passenden Diagnosen/Feature-IDs. |
| `CompilationGateTest.legalDeferredBindingIsNotErrorType` | Normativ gültiger Generic-/Deferred-Fall | Gültiges Schema mit expliziter späterer Bindungsbedingung. |
| `ReusePlannerTest.changedImportInvalidatesDependents` | Importtext ändern, Roottext gleich lassen | Neue abhängige Semantik; fachlich gleich zu Freshcompile. |
| `ReusePlannerTest.claimedResolverHashCannotMaskInputChange` | Resolverfingerprint absichtlich gleich lassen, tatsächlichen Quelltext oder Importziel ändern | Kein falscher Cachetreffer; eigener Eingabefingerprint und fachliches Resultat passen zum neuen Zustand. |
| `IdentityReconcilerTest.insertionPreservesUnchangedDeclaration` | Kommentar oder vorige Deklaration einfügen | Sichere bestehende IDs wiederverwendbar; SourceRanges aktuell. |
| `CompilerCancellationTest.cancelBetweenBindingAndTypes` | Kontrolliertes Token löst nach Bindung aus | `Cancelled`; keine Erfolgsmeldung oder teilweise gültige Schemaausgabe. |
| `CompilerBudgetTest.diagnosticLimitCannotTurnFailureIntoSuccess` | Viele unabhängige Fehler, sehr kleines Budget | Begrenzte Ausgabe, unvollständige Coverage, kein Erfolg. |
| `CompilerBudgetTest.rejectsOversizedNumberBeforeAllocation` | Mantisse/Exponent knapp unter, an und über einem kleinen Testlimit | Exakte Werte bis zur Grenze; darüber `Cancelled(RESOURCE_LIMIT)` ohne riesige Zwischenallokation. |
| `CompilerIsolationTest.concurrentIndependentRuns` | Unterschiedliche Modelle/Profile in unabhängigen Läufen | Keine vermischten Diagnosen, Builtins, IDs oder Quellen. |
| `CompilerRunContextTest.explicitContextHasOneLifecycle` | Voller Overload mit Hostkontext sowie Convenienceaufruf und echter Kindlauf | Pro RunId genau ein Compilerstart/-abschluss; Elternbezug korrekt, kein ThreadLocal-Kontextverlust. |
| `CompilerRuntimeParityTest.semanticSliceAcrossTargets` | Identische Fixtureeingaben JVM/TeaVM | Kanonisch identische Typen, Referenzen, Diagnosen und Werte. |
| `CompilerFailureTest.unexpectedInternalDefectIsFailed` | Kontrolliert injizierter echter interner Fehler | `Failed` mit FailureId, interner Ursachenmeldung und ohne „Modell ungültig“-Vortäuschung. |

Die Injektion interner Fehler wird ausschliesslich an einem ausdrücklich testbaren internen Port vorgenommen; Produktionscode erhält keinen öffentlichen „erzeuge Erfolg/Fehler“-Schalter. Testdoubles dürfen reale Namensauflösung oder Typregeln in End-to-End-Abnahmen nicht ersetzen.

### 9.3 P2 und weitere Verbraucher

P2 erweitert diese Pipeline nach dem vollständigen Featurekatalog05 und der Sprach-/Konformitätsmatrix13. Jedes neue Fachfeature wird bis zur Query-/Metamodelloberfläche umgesetzt. Erst danach ist „Syntax akzeptiert“ eine vollständige Fähigkeit. Der spätere Transfer-, Validator- und Datenbankaufbau erhält eigene Pläne; er fordert fehlende fachliche Informationen als neue Modell-/Queryverträge an, statt private Compilerfelder auszulesen.

Feingranulare semantische Wiederverwendung wird nach der ersten korrekten Pipeline und den Editvergleichstests aus07 verfeinert. Ein Cache ist eine Optimierung dieser Verträge. Er darf keine alternative Bedeutung des Programms und keine zweite Fehlerpolitik einführen.
