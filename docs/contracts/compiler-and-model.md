# Compiler und Modell

Der portable Compiler analysiert explizite Quellen und Modellbindungen. Die Engine unterscheidet partielle Analyse, abgelehnte Closure, fehlende Voraussetzung, Abbruch und internen Fehler. Die gültige Schemasicht entsteht ausschliesslich über das Gate. Sprachregeln stehen in [Sprachsemantik](language-semantics.md) und [Normentscheidungen](norm-decisions.md).

<a id="öffentliche-eingabe-api"></a>
## Öffentliche Eingabe-API

<a id="compilationrequest-und-wurzelselektion"></a>
### `CompilationRequest` und Wurzelselektion

Alle in diesem Abschnitt benannten Eingabetypen liegen in `guru.interlis.compiler`, sofern nicht ausdrücklich ein Foundationtyp genannt ist.

Die Wurzelliste ist nichtleer und defensiv kopiert. `ByName` bezeichnet einen exakten Modellnamen; eine Modellversion ist ein wörtlicher fachlicher Wert, keine automatisch interpretierte Semver-Range. `sourceHint` schränkt die Auswahl ein und darf einen Konflikt nicht still übersteuern. `BySource` selektiert alle ursprünglichen Modelle der expliziten Quelle. Bei einem fehlerhaften Header oder einer Quelle ohne gültig erkennbares Modell darf daraus nicht „null ausgewählte Modelle, also Erfolg“ werden.

Ein Dokument kann mehrere Modelle enthalten. Eine Rootauswahl über einen Namen selektiert nicht implizit alle Nachbarmodelle derselben Datei. Syntaxfehler im gemeinsam geparsten Dokument müssen jedoch verlässlich einem Bereich beziehungsweise einer globalen Dokumentbedingung zugeordnet werden. Kann ein Fehler nicht sicher von der Root abgegrenzt werden, gilt er konservativ als blockierend für die betroffene Quelle. Fehler werden nie bloss anhand von Stringpräfixen einer FQN weggefiltert.

<a id="sourceset"></a>
### `SourceSet`

Ein Mapkey muss der `SourceText.id()` entsprechen. Pro Quelle ist genau eine Revision enthalten. Collections werden defensiv übernommen. Die kanonische Quellenreihenfolge ist `SourceId.value()` nach `String.compareTo`, also lexikografisch über UTF-16-Codeeinheiten, unabhängig von Locale, Mapkonstruktion oder Netzwerkantwortzeit. Dieselbe Ordnung gilt für den Quellenanteil der kanonischen Diagnosereihenfolge nach [Diagnosevertrag](diagnostics.md). Innerhalb einer Quelle bleiben Originaloffset und fachliche Deklarationsordnung massgeblich. Ein Legacyrenderer darf die explizit profilierte historische Anzeigeordnung herstellen; er ändert weder Bindung noch kanonische Diagnoseidentität.

`ImportBinding`, `ImportSite`, `ImportResolution`, `ModelLocation` und `ResolutionProblem` sind in [Repositoryvertrag](repositories.md) definiert. Eine Bindung nennt die originale Importstelle und eine Zielquelle/-revision/-deklaration oder einen expliziten ungelösten Zustand. Der Compiler verifiziert diese Angaben gegen die tatsächlich geparsten Modellköpfe. Der Name der tatsächlich adressierten Modelldeklaration muss mit dem Namen an der geparsten Importstelle übereinstimmen.

Der übergebene `ResolutionFingerprint` ist Resolverprovenienz und kein alleiniger Beweis der Identität des SourceSet. `CompilationFingerprinter.fingerprint(request)` berechnet zusätzlich selbst aus den tatsächlich vorliegenden Quellen, ihren exakten Textfingerprints/Revisionen, Rootselektoren, Importstellen/-zielen/-zuständen, Optionen, Sprach-/Grammatikständen und relevantem Resolverstand die tatsächliche Eingabeidentität. Kanonische Längenpräfixierung und stabile Reihenfolgen verhindern uneindeutige Verkettungen. Ein Aufrufer kann durch Wiederverwendung desselben behaupteten Resolverhashs bei geändertem Text oder Ziel keine alte Compileranalyse gültig machen.

Nicht gefundene, mehrdeutige oder technisch blockierte Imports bleiben Daten im SourceSet. Die Syntax und die erreichbaren Teile können trotzdem analysiert werden. Ob daraus `Rejected` oder eine technisch blockierte Aufbereitung im Host entsteht, legt der Resolververtrag fest; ein vollständiger erfolgreicher Compile darf bei fehlender Pflichtabhängigkeit niemals entstehen.

Die vorhandenen `resolutionDiagnostics` werden über `DiagnosticCollector.importAll` nach [Diagnosevertrag](diagnostics.md) in den aktuellen Lauf übernommen. Dabei werden Diagnose-IDs und Ursachen-/Related-Bezüge konsistent auf die aktuelle Diagnosemenge abgebildet. Unverändert kopierte IDs eines fremden Snapshots dürfen keine zufälligen Verknüpfungen mit aktuellen Fehlern erzeugen. `ResolutionProblem`-Verweise werden über dieselbe Abbildung angepasst.

Das vordefinierte Modell `INTERLIS` wird versionsbezogen vom Compiler bereitgestellt. Eine normale Importquelle namens `INTERLIS` darf dieses nicht still ersetzen. Auflösung von Modellmetadaten und Download von Quelltexten ist Aufgabe08; Typauflösung innerhalb dieser Texte ist Aufgabe dieses Dokuments.

Qualifizierte Funktions- und ALL-OF-Domänenreferenzen behalten sämtliche
Namenssegmente und den gewählten Namensraum. Ein falscher Präfix darf nicht
schrittweise entfernt werden, bis ein lokaler Suffix zufällig passt. Unbekannte
`INTERLIS.*`-Domänen erhalten reguläre Bindungsdiagnosen am Originalbereich;
sie sind weder Implementierungssperren noch interne Fehler. Die drei einfachen
Domänen aus [Modellvertrag](compiler-and-model.md) verwenden die vorhandenen Modell-/Queryverträge.
Erweiterungen von FINAL-Domänen liefern `DOMAIN_FINAL` über die strukturierte
Regeldiagnostik. Die Abnahme prüft positive Bindungen, falsche Präfixe,
Originalbereiche, Imports und frische/wiederverwendende Analyse.

Klassenwert-Einschränkungen der Syntax (CLASS/STRUCTURE RESTRICTION) bleiben
am Klassenwerttyp gebunden, auch wenn die gemeinsame Grammatik sie über eine
äussere Strukturreferenz-Produktion erfasst. Sie dürfen weder als blosse
Domänenreferenz erscheinen noch zu einem unbeschränkten Klassenwert werden.
Vordefinierte Klassenziele verwenden denselben Binder wie übrige Signaturziele.

Die leere Funktionsliste unterliegt dem gemeinsamen Syntaxprofil aus [Quellen und Syntax](sources-and-syntax.md). Ein in 2.4 gültiger parameterloser Aufruf behält Funktionsziel und deklarierten
Ergebnistyp; zusätzliche oder fehlende tatsächliche Argumente bleiben Quellfehler.
Vordefinierte Attribute dürfen bei der Überschreibungsprüfung nicht wegen eines
fehlenden Quelldeklarationsindex übergangen werden. Für gebundene Struktur- und
Collectiontypen verwendet die Prüfung dieselbe Ziel-/Kardinalitätsbeziehung wie
die öffentlichen Typabfragen; Pflichtangaben dürfen nicht entfernt werden.

<a id="compileroptions"></a>
### `CompilerOptions`

`defaults()` erlaubt 2.3 und 2.4 und verwendet das dokumentierte normative Profil `interlis-2.3-2.4/v1`. Dieses Profil ist an konkrete Norm-/Grammatikstände gebunden; die Kennung ist keine wechselnde Bezeichnung „latest“. Ein unbekanntes Profil wird als ungültige API-Konfiguration zurückgewiesen. Eine Einschränkung auf eine Sprachversion prüft nicht nur Rootheader, sondern alle gemäss Sprachregeln relevanten beteiligten Modelle.

`SemanticOverlays` ist ein unveränderliches typisiertes Aggregat mit `empty()`, einer geordneten Overlayliste und einem Fingerprint. Anfangs muss nur `empty()` produktiv akzeptiert werden; spätere Overlaytypen werden mit eigenen Semantiktests freigeschaltet. Ein unbekannter Overlaytyp darf nicht ignoriert werden. CRS-/Metadaten-/Translationsoptionen, die fachliche Bedeutung verändern, gehören in diese Eingabe oder in den ausdrücklich consumerseitigen Planvertrag aus12. Temporäre Laufzeitwerte und Caches gehören nicht hinein.

`AnalysisBudget` enthält positive Obergrenzen für Quelltextmenge, Tokens, Syntaxknoten, Recoveryereignisse, Verschachtelung, semantische Arbeitseinheiten und Diagnosen. Dokumentierte Defaultwerte werden mit realistischen Modellen geprüft. Die Werte dürfen durch den Host erhöht werden. Ein Limit ist eine Ressourcengrenze, keine Sprachregel. Die Diagnose muss daher erklären, dass das Ergebnis nicht vollständig geprüft wurde.

Die Defaultbudgets sind in den Einheiten des jeweiligen Limits angegeben. Änderungen brauchen Grenztests und realistische Korpuseingaben. Budgets sind Betriebsgrenzen.

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

<a id="engine-ergebnisse-und-wiederverwendung"></a>
## Engine, Ergebnisse und Wiederverwendung

<a id="öffentliche-konstruktion-und-dienste"></a>
### Öffentliche Konstruktion und Dienste

Die SDK muss ohne Kenntnis interner Implementierungen instanziierbar sein:

`CompilerEngines.create()` entspricht `create(CompilerServices.defaults())`. Der Defaultobserver ist `RunObserver.noop()`. Die Defaultzeitquelle verwendet die für die Zielplattform geprüfte Standardzeit; alternativ injiziert der Host eine eigene `ClockSource`. `RunContextFactory` stammt aus `guru.interlis.execution` nach [Diagnosevertrag](diagnostics.md) und stellt `createRoot()` sowie `createChild(RunContext parent)` bereit. Die Defaultfabrik verwendet ihren eigenen Namespace und instanzlokale, überlaufgeprüfte Zähler; keine globale Zähler-/ThreadLocal-Pflicht. Tests injizieren kontrollierte IDs und Zeitwerte. Betriebs-Run-IDs und Zeitwerte beeinflussen weder Sprachentscheidungen noch semantische Symbol-/Snapshotidentitäten oder fachliche Fingerprints.

Die Fabrik führt keine Repositorysuche, Pluginentdeckung oder Netzoperation aus. Builtins, Phasen und Sprachprofile werden intern explizit registriert. Ein allgemeines DI-Framework oder `ServiceLoader` ist dafür nicht erforderlich. Der erzeugte Engine-Einstieg ist reentrant: Jeder Aufruf besitzt eigene veränderliche Laufdaten. Ein über `CompilerServices` gemeinsam verwendeter Observer, eine gemeinsam verwendete Uhr und eine gemeinsam verwendete RunContextFactory müssen ihrerseits threadsicher sein oder durch den Host vor den betreffenden gleichzeitigen Aufrufen serialisiert werden. Das rechtfertigt keinen globalen Compiler-Lock.

Fehler der optionalen Beobachtungssenke werden durch den `SafeRunObserver`-Vertrag aus09 isoliert. Die Factory veröffentlicht keine API, mit der ein Consumer fachliche Pflichtprüfungen ausschalten oder ein `CompiledSchema` direkt erzeugen kann.

<a id="kernmethoden"></a>
### Kernmethoden

Der einfache Aufruf ist äquivalent zu `AnalysisReuse.none()`. `analyze` arbeitet mit dem EDITOR-Syntaxmodus, soweit kein bereits nachgewiesen gleichwertiges striktes Syntaxergebnis wiederverwendet wird. `compile` verwendet STRICT-Akzeptanz. Wiederverwendung von EDITOR-Ergebnissen ist nur zulässig, wenn die vollständige strikte Akzeptanz nachgewiesen ist; erneutes striktes Parsen der sichere Default.

Overloads ohne `RunContext` erzeugen nach Prüfung offensichtlicher API-Argumentfehler über die injizierte Factory einen neuen Rootkontext und delegieren auf die volle Variante. Nur diese vollständige Ausführung besitzt den Compiler-Lifecycle; Conveniencewrapper emittieren keinen zusätzlichen Anfang oder Abschluss. Der volle Overload erhält einen frischen, noch nicht für eine andere Ausführung verwendeten Kontext. Er startet und beendet unter dessen RunId genau den eigenen Lauf nach [Diagnosevertrag](diagnostics.md).

`RunContext` enthält unveränderliche Lauf-/Elternidentität und begrenzte Korrelationsattribute; er ist kein bereits laufender geliehener Scope und enthält keinen Workspace oder Metamodellgraph. Der Workspace beziehungsweise ein Host kann einen solchen Kontext ausdrücklich bis zur Engine durchreichen, darf dann aber nicht unter derselben RunId einen zweiten Lifecycle erzeugen. Wenn der Host einen echten übergreifenden Lauf für Beschaffung, Compiler und anschliessenden Export beobachtet, erhält die Engine einen über `createChild(parent)` erzeugten eigenen Kindkontext. Damit sind Elternbeziehung und Abschlussbesitz ausdrücklich, ohne ThreadLocal, globale aktive Contexts oder konkurrierende Sequenzzähler derselben RunId.

Die Engine benötigt pro Lauf einen neuen `CompilationRun`. Sie besitzt keine veränderlichen Quellen, die ein Aufrufer zwischen Phasen austauschen kann. Die Engine kann konzeptionell gleichzeitig für mehrere unabhängige Requests verwendet werden; der Workspace organisiert seine Mutationen seriell. Eine Implementierung darf interne immutable Grammatik-/Builtinstrukturen teilen, aber keine Lexerinstanzen oder semantischen Builder.

`analyze` liefert bei regulären Quellfehlern einen `AnalysisSnapshot`. Bei angefordertem Abbruch wirft dieser bequeme Queryeinstieg `OperationCancelledException`; Workspace-/WASM-Adapter übersetzen dies in ihre typisierten Abbruchantworten. Nicht abgefangene interne Fehler werden als `CompilerInternalException` mit `FailureId` übertragen. Wer alle Ausgänge ohne Exceptionpfad benötigt, verwendet `compile` beziehungsweise den entsprechenden Workspace-Outcome. Diese Differenz ist dokumentiert und muss in Adaptertests berücksichtigt werden.

<a id="compilationoutcome"></a>
### `CompilationOutcome`

`Succeeded.schema.snapshotId()` und `Succeeded.analysis.snapshotId()` stimmen überein. Ein vorhandener Analysestand in `Cancelled` ist eine konsistent gefrorene Teilsicht mit unvollständiger Coverage, niemals ein Erfolg. `Failed` enthält eine sichere benutzbare interne Fehlerdiagnose; Stacktrace und technische Ursachen gehen zusätzlich an den RunObserver aus09, nicht ungefiltert in Browsernachrichten.

Die Ablehnungszusammenfassung wird vom Gate erzeugt und beschreibt ausschliesslich die angeforderte Rootclosure:

`reasons` und `requestedRoots` sind nichtleer und defensiv kopiert. Die Gründe werden bei Anzeige und Serialisierung in deklarierter Enumreihenfolge geordnet. Diagnose-IDs gehören zum zugehörigen Analysesnapshot und erklären relevante Ablehnungsgründe; unabhängige Fehler ausserhalb der Auswahl werden nicht hineingenommen. `unsupportedFeatureIds` enthält die tatsächlich betroffenen stabilen Featurekennungen in kanonischer Reihenfolge und ist ohne `INCOMPLETE_SUPPORT` leer. Mehrere Gründe können gleichzeitig vorliegen.

`SOURCE_ERRORS` bezeichnet nachgewiesene Lexer-, Syntax- oder Semantikfehler des Quellmodells. Eine fehlende Quelle ist `MISSING_INPUT`, eine nicht eindeutige Auswahl `AMBIGUOUS_INPUT`, eine technisch blockierte erforderliche Beschaffung `ENVIRONMENT_FAILURE`. Fehlende eigene Compilerfähigkeit ist `INCOMPLETE_SUPPORT`. `WARNINGS_REJECTED` bezeichnet die bewusste Warnungspolitik. Dadurch wird eine Ablehnung nicht pauschal als Beweis sprachlicher Ungültigkeit missverstanden.

CLI, LSP und WASM transportieren beziehungsweise rendern diese Zusammenfassung. Sie dürfen die Closuregründe nicht selbst aus der ungefilterten gesamten Diagnoseliste ableiten oder eine zweite Variante des Compile-Gates implementieren. Ein API-Aufrufer kann die vollständige Analyse zusätzlich betrachten, erkennt aber an `RejectionSummary` die vom Gate tatsächlich entscheidenden Gründe.

`CancellationReason` ist ein gemeinsamer Foundationtyp mit mindestens `USER_REQUESTED`, `SUPERSEDED`, `DEADLINE_EXCEEDED`, `RESOURCE_LIMIT` und `HOST_SHUTDOWN`. `OperationCancelledException.reason()` verwendet denselben Typ. Der Host setzt die passenden Gründe für externe Abbrüche; der reine Kern erzeugt `RESOURCE_LIMIT` beim eigenen Budget. Weder Enum noch Ausnahme dürfen vom Workspace oder Compiler-Snapshottyp abhängen. Ein eventuell verfügbares Teilergebnis wird im Compile-Outcome transportiert, nicht durch einen zyklischen Snapshotverweis in der Foundation-Ausnahme.

Ungültige Java-Argumente werden vor dem Start per Exception zurückgewiesen. `Failed` ist kein Sammelbecken für Tippfehler, fehlende Imports oder nicht unterstützte Konstrukte. Letztere sind sichtbare Einschränkungen der Analyse und führen zu `Rejected`, wenn sie die Rootclosure betreffen. Fatale JVM-Fehler wie ein nicht mehr zuverlässig behandelbarer `OutOfMemoryError` werden nicht als gewöhnlicher Modellfehler kaschiert; der Host muss den Lauf als fehlgeschlagen erkennen.

<a id="analysissnapshot-und-coverage"></a>
### `AnalysisSnapshot` und Coverage

Die Snapshotklasse ist unveränderlich. `diagnostics()` enthält alle kanonischen Diagnosen, auch aufgrund einer gemeinsamen Ursache in einer UI nicht angezeigte Folgefehler. `requestDiagnostics()` ist der immutable, kanonisch geordnete Teilbestand derselben Werte/IDs für exakt die Roots, Optionen, Quellrevisionen und den Fingerprint dieses Snapshots. Die interne Auswahl erfolgt einmal vor dem Gate; Gate und Compileverbraucher verwenden diesen Teilbestand. Der öffentliche Konstruktor verlangt beide Listen ausdrücklich und verwirft Teilbestände, die keine geordnete Teilfolge sind. Die Darstellung wird in09 projiziert; das Gate darf keine darstellungsgefilterte Liste verwenden.

`AnalysisCoverage` enthält Einträge pro benötigter Deklaration/Modell/Phase. `CoverageStatus` unterscheidet `COMPLETE`, `BLOCKED_BY_ERROR`, `NOT_IMPLEMENTED`, `NOT_REQUESTED`, `CANCELLED` und `BUDGET_EXCEEDED`. Syntax und Semantik können in unterschiedlichen Zuständen sein. Ein leerer Eintrag ist keine implizite Vollständigkeit. Für jede ausgewählte Rootclosure muss das Gate das explizite Abschlussprädikat `isCompleteFor(ClosureSelection)` prüfen können.

Coverage und Diagnostik erfüllen unterschiedliche Aufgaben: Eine fehlende Implementierung bleibt auch dann blockierend, wenn der Diagnosecollector sein Ausgabelimit erreicht hat. Umgekehrt dürfen bekannte reine Warnungen einen vollständig geprüften Status besitzen. Jeder Fehler muss eine nachvollziehbare Zuordnung zu Source-/Symbol-/Globalbedingungen erhalten; unzuordenbare Eingabefehler werden konservativ als relevant behandelt.

Auftragsrelevanz umfasst Roots, Imports, Übersetzungsoriginale und Overlayabhängigkeiten. Fremde semantische Fehler und Warnungen, auch in derselben Datei, bleiben ausschliesslich im Gesamtbestand. Lexer-/Parserfehler in benötigten Dateien bleiben konservativ relevant, auch wenn keine Modelldeklaration rekonstruierbar ist: explizite BySource-Roots, explizite ByName-Quellhinweise und tatsächlich aufgelöste Importquellen behalten ihre Quellzuordnung bei beschädigter oder fehlender Deklaration. Vollständig fremde defekte Dateien belasten den Auftrag nicht. Ohne Modellidentität oder Resolverzuordnung wird keine Quelle anhand ihres Dateinamens als Importziel geraten; der fehlende Import bleibt eine sichtbare Auflösungsdiagnose. Root-/Closurediagnosen und globale, nicht zuordenbare Eingabefehler bleiben erhalten. Warnungsablehnung gilt nur für relevante Warnungen. Fresh/Reuse und Rootwechsel müssen dieselbe Auswahl erzeugen; Abbruch bleibt kontrolliert.

<a id="analysisreuse"></a>
### `AnalysisReuse`

Diese Eingabe ist eine unveränderliche Beschleunigungs- und Identitätshilfe. Sie wird nicht als bereits bestätigte Wahrheit akzeptiert. `ReusePlanner.plan(request,reuse)` prüft Text-, Quellen-/Revisions-, Grammatik-, Sprach-, Options-, Modus- und Abhängigkeitsfingerprints, bevor ein Ergebnis wiederverwendet wird. Nicht passende Einträge werden verworfen. Untereinander widersprüchliche vom API-Aufrufer selbst konstruierte Eingaben können als ungültige Argumente abgelehnt werden; ein normaler geänderter Text ist keine Exception.

`IdentityReconciler` verwendet nachweislich unveränderte, eindeutige Deklarationsanker samt Scope/Art und Textstruktur. Eine Einfügung vor einer Deklaration darf deren Offset ändern, ohne zwingend ihre ID zu ändern. Ein Name allein reicht nicht. Bei Duplikaten oder unklarer Zuordnung werden neue IDs vergeben. Die Relation alter/neuer Identitäten kann als eigenes Delta an Projektionen gehen; sie darf keine falsche semantische Gleichsetzung erzeugen.

Eine frische Kompilierung und eine wiederverwendende Kompilierung müssen fachlich gleich sein. Opaque IDs, Cachemetriken und Runmetadaten werden beim semantischen Vergleich normalisiert. Wenn dieselben Eingaben mit unterschiedlichen Wiederverwendungswegen verschiedene ID-Belegungen erzeugen, müssen Snapshotidentitäten dies unterscheiden. Ein Snapshotfingerprint darf deshalb nicht nur den Text hashieren und dadurch voneinander abweichende Handle-Räume als identisch ausgeben.

<a id="interne-pipeline-und-klassenverträge"></a>
## Interne Pipeline und Klassenverträge

<a id="laufbesitz"></a>
### Laufbesitz

`DefaultCompilerEngine` delegiert an `CompilationOrchestrator`. Dieser erzeugt `CompilationRun`, der Request, Modus, Budgetzähler, CancellationToken, Diagnosecollector, Observer und ausschliesslich laufbezogene Builder besitzt. Der Lauf wird nach Beendigung verworfen. Publiziert werden nur gefrorene Ergebnisse.

Die endgültige Graphfreeze-Operation überprüft Referenzintegrität und Collectionbesitz. Ein Fehler während Freeze darf keine halb veränderliche Sicht an LSP oder CLI herausgeben. Der Observer ist ein Port nach [Diagnosevertrag](diagnostics.md); der Kern kennt kein Loggingbackend.

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

<a id="phasenreihenfolge-und-erlaubte-rückkopplung"></a>
### Phasenreihenfolge und erlaubte Rückkopplung

1. **Eingabeprüfung und Syntax.** Vollständigen Quellvertrag prüfen, beide Fehlerquellen sammeln, Header und Recoveryzustände erfassen.
2. **Deklarationsindex.** Alle sicher erkennbaren Deklarationen mit Originalgrenzen und Ownern registrieren. Duplikate bleiben als getrennte Symbole erhalten.
3. **Wurzeln, Imports und Scopes.** Resolverangaben verifizieren, Rootclosure bestimmen und erlaubte Sichtbarkeit aufbauen. Negative Importbeziehungen bleiben für Invalidierung sichtbar.
4. **Referenzen und Abhängigkeiten.** Namen segmentweise binden. Für referenzierbare spätere Elemente werden nur jene Vorwärtsbezüge zugelassen, die das Sprachprofil erlaubt.
5. **Typen und Vererbung.** Domänen, Einheiten, Basistypen, Restriktionen, effektive Mitglieder und Kontexte auflösen. Abhängigkeitskomponenten verhindern naive Endlosrekursion.
6. **Ausdrücke und Fachregeln.** Pfade, Constraints, Views, Funktionen, Graphics, Übersetzungen und weitere Typfamilien nach [Modellvertrag](compiler-and-model.md) prüfen.
7. **Freeze und Gate.** Coverage, Diagnosen und Graphintegrität prüfen; nur danach Ergebnis veröffentlichen.

Dies ist eine logische Reihenfolge, keine Pflicht zu genau sieben Vollscans. Einzelne fachliche Abhängigkeiten können kontrollierte Unterphasen benötigen. Beispielsweise stehen Funktionssignaturen vor der Typprüfung ihrer Aufrufe bereit, während vollständige Constraintprüfung später erfolgt. Eine solche Rückkopplung wird als deklarierte Abhängigkeit oder Komponentenverarbeitung implementiert, nicht als „wiederhole alles, bis zufällig nichts mehr passiert“.

Für jede iterative Auflösung wird eine endliche Zustandsmenge beziehungsweise Fortschrittsbedingung festgelegt. Wird kein Fortschritt mehr erreicht, entstehen blockierte/unaufgelöste Zustände mit Diagnose. Der Compiler darf unvollständige Ergebnisse nicht durch willkürliche mehrfach erhöhte Iterationszahlen erfolgreich erklären.

<a id="fachliche-prüfdienste-auf-methodenebene"></a>
## Fachliche Prüfdienste auf Methodenebene

<a id="namen-imports-und-sichtbarkeit"></a>
### Namen, Imports und Sichtbarkeit

`ReferenceBinder.bind(NameReference reference, BindingContext context)` liefert `BindingResolution`. Es nimmt den tatsächlichen Verwendungsscope, die Sprachversion und die Quellposition entgegen. Es unterscheidet lokalen Namen, geerbten Namen, qualifizierten Import, UNQUALIFIED-Import und Builtin. Der globale Index dient Kandidatensuche, nicht der Überschreibung von Sprachregeln.

Mehrdeutigkeit bleibt ein eigener Ausgang. Ein expliziter Name darf nicht nach Dateireihenfolge, Netzwerkantwortzeit oder HashMapiteration aufgelöst werden. Ein fehlendes Ziel erzeugt eine Hauptdiagnose; davon abhängige Typfehler tragen denselben Ursachenzusammenhang. Die in09 definierte Darstellung kann Folgefehler reduzieren, ohne die Ursachen aus dem Snapshot zu löschen.

<a id="vererbung-und-restriktionen"></a>
### Vererbung und Restriktionen

`InheritanceChecker.check(SymbolId derived, SymbolId base, RuleContext context)` prüft zulässige Elementarten, Final-/Abstract-/Extended-Regeln, Topickontext, Zyklen und weitere versionierte Bedingungen. `AttributeOverrideChecker.check(...)` prüft den Bezug zur tatsächlichen Basiseigenschaft, Typ-/Kardinalitäts-/Definiertheitseinschränkungen und erlaubte Modifier. Ein gleicher Name allein ist kein bestätigtes Override.

FINAL wird anhand der gebundenen Deklaration geprüft, auch wenn ein importierter
Basistyp in der kanonischen Quellenordnung erst später aufgebaut wird.
Finale Topics, Klassen und Strukturen dürfen nicht erweitert, finale Attribute
nicht überschrieben werden. EXTENDED an einem Attribut verlangt eine tatsächliche
geerbte Basiseigenschaft. Eine Attributübersteuerung mit EXTENDED und einer unmittelbaren Konstante
ist effektiv FINAL. Der Symbolheader trägt die wirksame Finalität. Die
Syntaxsicht enthält die im Quelltext geschriebenen Modifikatoren.
Assoziationsattribute verwenden dieselbe gemeinsame
Override-, MANDATORY- und Typprüfung wie Klassen-/Strukturattribute; konkrete
Assoziationen müssen geerbte abstrakte Attribute konkretisieren. Die bestehende
Behandlung geerbter abstrakter Rollendeklarationen wird dadurch nicht erweitert
oder verschärft: Deren ursprüngliche Marker und Identitäten bleiben sichtbar.

`EffectiveMemberResolver` arbeitet danach auf einer nachweislich verwertbaren Vererbungskette. Bei ungültiger Basis kann es die sicher bekannten lokalen Mitglieder für IDE-Zwecke ausgeben, muss aber die vollständige effektive Sicht als blockiert beziehungsweise ungültig markieren. Es darf nicht eine lokale Liste als vollständige Transferreihenfolge ausgeben.

<a id="typen-und-ausdrücke"></a>
### Typen und Ausdrücke

Die Vorbindung von Domänenbasen verarbeitet lange EXTENDS-Ketten mit einem
laufgebundenen Arbeitsstapel. Aktive Deklarationen erkennen Rückkanten auch
bei Wiedereintritt aus einem Typkörper. Domänenconstraints werden anschliessend
an den abgeschlossenen Typ gebunden. Die Schemasicht verfolgt die Basistypen
effektiver Enumerationen iterativ. Arbeitsbudget und Benutzerabbruch gelten
für die Vorbindung und den Typabschluss.

`TypeResolver.resolve` delegiert auf kleine Fachregeln wie `NumericTypeRules`, `EnumerationRules`, `GeometryTypeRules`, `ReferenceTypeRules`, `FormattedTypeRules` und `GenericBindingRules`. Diese Dienste rufen die Relationselemente aus05 auf und ergänzen kontextbezogene Diagnostik. Methoden und Tests werden je Fachfamilie benannt; eine monolithische mehrere tausend Zeilen lange Visitor-Methode ist zu vermeiden.

Seit API 0.3 umfasst `BinaryOperator` bei Arithmetik die Operatoren
`PLUS`, `MINUS`, `MUL` und `DIV`. Die bisherige Potenzoperation `POW` entfällt
aus Syntax und semantischem Modell. Die Versionsänderung betrifft den
öffentlichen Java-Modellvertrag.

`ExpressionTypeChecker.check(ExpressionSyntax syntax, ExpressionContext context)` ermittelt Ergebnisart und Definiertheits-/Kardinalitätswirkung. `ObjectPathResolver.resolve(...)` prüft jeden Pfadschritt. `FunctionCallChecker.check(...)` prüft Aufrufziel, Argumentanzahl, Sonderargumentformen, Typverträglichkeit und Rückgabetyp. Ein unbekanntes Argument wird nicht automatisch als `ANY` passend gemacht.

Jede Regel gibt bekannte Teilinformationen zurück, wenn diese unabhängig vom Fehler sicher sind. Sie darf aber keine provisorische Schlussfolgerung als bestätigt speichern. Zum Beispiel kann das Ziel einer Funktion bekannt sein, während der Typ eines Arguments noch blockiert ist; Hover darf die Signatur zeigen, während die Gesamtprüfung unvollständig bleibt.

<a id="constraints-views-translation-und-graphics"></a>
### Constraints, Views, Translation und Graphics

`ConstraintChecker.check(ConstraintSymbol,RuleContext)` dispatcht exhaustiv über die Constraintvarianten aus05. Es prüft den erlaubten Ausführungskontext, boolesche Bedingungen, Pfad-/Zielverträglichkeit, lokale beziehungsweise globale Schlüssel und versionsabhängige Optionen. Es zählt keine realen Transferobjekte.

`ViewChecker.check(ViewSymbol,RuleContext)` besitzt getrennte Methoden für Projection, Join, Union, Aggregation und Inspection. Es prüft Basisaliase, Selektionen, Attributableitungen, Ergebnistypen und zulässige Erweiterungen. Für einen gültigen aber noch nicht implementierten Viewzweig wird eine `NOT_IMPLEMENTED`-Coverage erzeugt, kein leerer View als Erfolg.

`TranslationChecker` bindet Original und Übersetzung anhand überprüfter fachlicher Korrespondenz. Eine Übersetzung darf nicht durch globalen Stringersatz „geprüft“ werden. `GraphicChecker` prüft Signaturklassen, Parameter und bedingte Zuordnungen; es zeichnet kein UML-Diagramm. Diese Fachprüfungen gehören zum vollständigen Compiler, auch wenn ein erster Consumer sie nicht verwendet.

<a id="regeln-als-überprüfbare-einzelverträge"></a>
### Regeln als überprüfbare Einzelverträge

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

Die Fachregeln dürfen ein gemeinsames Resultatformat verwenden, aber kein frei konfigurierbares Plugin-/Regel-Framework ist erforderlich. Normative Compilerregeln werden als normaler, testbarer Java-Code aufgerufen. Eine durch spätere Consumer gewünschte Sonderregel darf die Sprachgültigkeit nicht unbemerkt ändern.

<a id="gültigkeitsgate-und-veröffentlichung"></a>
## Gültigkeitsgate und Veröffentlichung

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

<a id="abbruch-budgets-und-laufbeobachtung"></a>
## Abbruch, Budgets und Laufbeobachtung

`CancellationToken` ist der gemeinsame Foundationvertrag `isCancellationRequested()` und `throwIfCancellationRequested()`. Jeder Phaseneinstieg und jede längere Schleife besitzt Prüfpunkte. Tokenisierung, Knotenerzeugung, Indexieren und Graphdurchläufe zählen Arbeitseinheiten. Überprüfungen in inneren Schleifen werden ausreichend regelmässig gesetzt, ohne eine harte nicht gemessene Millisekundengarantie zu behaupten.

Die gemeinsamen Ausführungstypen liegen in `guru.interlis.execution` innerhalb von `interlis-model`: `CancellationToken`, `CancellationReason`, `OperationCancelledException`, `FailureId`, `RunContext`, `RunContextFactory` und die Beobachtungsverträge aus09. Es entsteht dafür kein zusätzliches Modul und keine Rückabhängigkeit auf den Compiler. Contexts werden pro Aufruf explizit übergeben; kein ThreadLocal stellt vermeintlich den richtigen Lauf bereit.

Bei Diagnosebudgetüberschreitung werden Ausgabe und Coverage getrennt behandelt. Das letzte sichtbare Ereignis erklärt das Limit; weitere tatsächliche Fehler können als Zähler-/Blockerzustand erfasst werden. Das Gate sieht weiterhin, dass kein vollständiger Erfolg bewiesen ist. Für tiefe Verschachtelung wird der Schutz vor Stackoverflow früh an Lexer-/Parser-/Buildergrenzen eingeplant; ein Schutz erst nach dem rekursiven Durchlauf ist wirkungslos.

Wenn das Analyse-/Diagnosebudget die vollständige Prüfung verhindert, beendet der Orchestrator den Lauf mit `Cancelled(RESOURCE_LIMIT)` beziehungsweise der oben beschriebenen Analyseausnahme. Ein lediglich UI-seitiges Limit der dargestellten Diagnosen ist davon verschieden: Es verändert weder den kanonischen Snapshot noch den Abschlussstatus. Die Tests müssen diese zwei Grenzen auseinanderhalten.

Observerereignisse verwenden die Phasen aus09: `SOURCE_RESOLUTION`, `LEXING`, `PARSING`, `DECLARATION_INDEXING`, `BINDING`, `TYPE_CHECKING`, `SEMANTIC_CHECKING` und gegebenenfalls `PROJECTION`. Compilerphasen erfinden keine abweichenden Enumbezeichnungen. Fachliche Diagnosen bleiben Daten; Laufzeiten, Cachetreffer und Abbruchgründe werden als Betriebsereignisse gemeldet.

<a id="identitäten-referenzen-und-abfragen"></a>
## Identitäten, Referenzen und Abfragen

<a id="werttypen-und-gültigkeitsbereich"></a>
### Werttypen und Gültigkeitsbereich

Strings sind nichtleer, opak und nicht als FQN zu parsen. Die genaue Kodierung ist eine interne Entscheidung, aber die öffentlichen Semantiken sind verbindlich. Ein `SymbolId` kann bei nachweislich derselben Deklaration innerhalb einer Sitzung über Revisionen stabil bleiben. Ein `SymbolRef` bleibt trotzdem an seinen Snapshot gebunden. Cross-Session-Gleichheit ist nicht zugesichert.

Graphinterne Kanten verwenden die kleineren IDs. An einer öffentlichen Abfragegrenze werden Handles mit Snapshot verwendet. Ein Aufrufer, der eine stabile ID bewusst im neuen Snapshot nachschlagen will, muss dies explizit über `rebindSymbol(SymbolId)` tun. Diese Methode prüft die Existenz; sie behauptet keine Gleichheit der alten und neuen fachlichen Eigenschaften.

`SnapshotMismatchException extends IllegalArgumentException` bezeichnet API-Fehlgebrauch. Ein alter Handle darf nicht zufällig das neue Symbol gleichen Namens zurückgeben. Fehlende Namen im Benutzermodell sind dagegen fachliche Diagnostik und keine Java-Ausnahme.

<a id="gültiges-generisches-queryresult"></a>
### Gültiges generisches `QueryResult`

Die folgenden Java-Signaturen sind normativ. Die verschachtelten Records sind unmittelbare Implementierungen desselben generischen Interface; es werden keine rohen Typen und keine nicht zulässigen generischen `permits`-Sonderfälle verwendet.

Die Implementierung ergänzt Nullprüfungen und defensive Kopien. `Known.value` ist niemals null; `Invalid.diagnosticIds` ist nichtleer und referenziert Diagnosen desselben Snapshots. `Unresolved` bedeutet, dass die Abfrage versucht wurde, aber keine eindeutige fachliche Bindung vorliegt. `NotAnalyzed` bedeutet fehlende Analyseabdeckung, etwa wegen Abbruch, nicht unterstützter Fähigkeit oder einer noch nicht angeforderten Teilanalyse. Eine rechtmässige aufgeschobene Domäne ist `Known<GenericType>`, nicht `Unresolved`.

`UnresolvedReason` und `NotAnalyzedReason` sind geschlossene Reason-Enums oder typisierte Varianten mit stabiler Kennung. Meldungstexte sind keine Reason-Identitäten. Ob ein Name fehlt oder mehrdeutig ist, muss für Completion und Diagnose unterscheidbar sein. Kandidaten besitzen Quell- beziehungsweise Symbolprovenienz; es erfolgt keine willkürliche Auswahl des ersten Kandidaten.

<a id="semanticqueries"></a>
### `SemanticQueries`

`SemanticQueries` ist eine read-only Sicht auf genau einen `SemanticGraph`. Die folgenden Methoden bilden die erste verbindliche Oberfläche; zusätzliche Komfortmethoden müssen auf denselben Indizes beruhen.

| Signatur | Bedeutung |
|---|---|
| `SnapshotId snapshotId()` | Identität des gebundenen Graphen. |
| `QueryResult<Symbol> symbol(SymbolRef ref)` | Vollständiger aktueller Symboldeskriptor oder definierter Zustand. |
| `QueryResult<TypeDescriptor> type(TypeRef ref)` | Typ mit etwaigen legalen Bindungsbedingungen. |
| `QueryResult<TypedExpression> expression(ExpressionRef ref)` | Typisierter Ausdruck samt Ursprung. |
| `QueryResult<TypeDescriptor> declaredType(SymbolRef ref)` | Expliziter oder abgeleiteter deklarierter Typ; keine stille Aliasauflösung. |
| `QueryResult<EffectiveType> effectiveType(SymbolRef ref)` | Aufgelöste Typwirkung mit Alias-/Restriktionspfad. |
| `QueryResult<List<EffectiveMember>> effectiveMembers(SymbolRef viewable)` | Fachliche, deterministische Reihenfolge von Attributen und Rollen. |
| `QueryResult<SymbolRef> resolveReference(ReferenceId reference)` | An diese Querysicht gebundene Referenzauflösung. |
| `QueryResult<List<SymbolRef>> visibleSymbols(ScopeRef scope, NamePrefix prefix)` | Sichtbarkeit nach Sprachregeln; kein globales FQN-Matching. |
| `QueryResult<List<SymbolRef>> membersOf(SymbolRef owner)` | Nur Mitglieder des Qualifizierers; effektive Viewable-Mitglieder und Topic-Vererbung, ohne Aufstieg in äussere Scopes. |
| `List<SymbolRef> findQualifiedName(QualifiedName name)` | Alle Kandidaten; ein Duplikat ist kein Erfolg mit dem ersten Treffer. |
| `Optional<SymbolRef> rebindSymbol(SymbolId id)` | Explizite ID-Suche im aktuellen Snapshot. |
| `List<NameReference> referencesTo(SymbolRef target)` | Originale Referenzvorkommen, einschliesslich Namenssegmenten. |

Eine nackte `ReferenceId` ist in `resolveReference` ausdrücklich lokal zur bereits gebundenen Querysicht. Wird sie ausserhalb dieser Sicht transportiert, muss sie zusammen mit deren `SnapshotId` übertragen werden; Browser-/LSP-DTOs enthalten diesen Kontext. Diese Regel gilt für alle kompakten ID-Listen in Resultaten.

`ScopeId.forOwner(SymbolId)` erzeugt den kanonischen Lookupanker einer Deklaration;
`ScopeRef` ergänzt die Snapshotidentität. Ein unbekannter Anker ergibt
`Unresolved(UNKNOWN_ID)`, eine fremde Snapshotidentität die bestehende
`SnapshotMismatchException`. `visibleSymbols` betrachtet zuerst lokale und
effektive Mitglieder, danach äussere Scopes, unqualifizierte Importe und
sprachversionspassende vordefinierte Namen. Verdeckte äussere Namen werden
nicht zusätzlich angeboten; mehrdeutige gleichrangige Kandidaten bleiben
getrennt. Die Reihenfolge ist lokal vor aussen und innerhalb eines Rangs
deterministisch nach Name/Identität. `membersOf` erhält deklarierte Reihenfolge
und ergänzt geerbte Mitglieder; lokale Topic-Mitglieder verdecken geerbte
Namen derselben Namenskategorie. Beide Ergebnisse sind unveränderlich,
nullfrei und an diese Querysicht gebunden; es erfolgen keine Mutation oder I/O.

<a id="semantischer-graph-und-symbolhierarchie"></a>
## Semantischer Graph und Symbolhierarchie

<a id="semanticgraph"></a>
### `SemanticGraph`

`public final class SemanticGraph` besitzt unveränderliche Indizes für Symbole, Typen, Ausdrücke, Scopes, Referenzen und Herkunft. Öffentliche Getter geben read-only Sichten zurück. Die Struktur wird in einem laufbezogenen Builder aufgebaut und beim Freeze vollständig validiert. Der Builder darf nach dem Freeze keine Arrays oder Collections mehr besitzen, mit denen die veröffentlichte Sicht veränderbar bleibt.

Graphinvarianten:

- Jede eigene ID ist in ihrer Kategorie eindeutig.
- Jede vorhandene ID-Kante zeigt auf ein vorhandenes Element desselben Graphen.
- Noch nicht aufgelöste Kanten werden durch Auflösungszustände beschrieben, nicht durch erfundene IDs.
- Containment besitzt genau einen fachlichen Eigentümer, ausser expliziten Wurzel-/Builtin-Elementen; Containmentzyklen sind verboten.
- Semantisch zulässige Referenzzyklen sind darstellbar. Unzulässige Vererbungs-/Typaliaszyklen werden diagnostiziert und nicht durch Rekursion bis zum Stackoverflow verarbeitet.
- Jede SourceRange verweist auf eine im Snapshot bekannte Revision oder auf eine ausdrücklich synthetische Herkunft ohne vorgetäuschten Originalbereich.

`SemanticGraphBuilder` kann im Modellmodul als ausdrücklich interne Aufbauhilfe liegen, damit das Compiler-Modul Graphen ohne umgekehrte Abhängigkeit erzeugen kann. Diese Aufbauhilfe ist keine alternative fachliche Compiler-API. Der Graph darf partielle Daten enthalten; nur das Gate in [Compilervertrag](compiler-and-model.md) darf ihnen die Eigenschaft eines `CompiledSchema` verleihen.

<a id="gemeinsamer-symbolkopf"></a>
### Gemeinsamer Symbolkopf

`SymbolHeader` enthält `SymbolId id`, `SymbolKind kind`, `DeclaredName name`, `Optional<SymbolId> owner`, `int declarationOrder`, `Origin origin`, `Documentation documentation`, `MetaAttributeSet metaAttributes` und typisierte Modifier. Die Implementierung verhindert unzulässige Kombinationen bereits soweit lokal entscheidbar. Beispielsweise darf ein Symbol nicht gleichzeitig zwei unabhängige Owner haben.

`DeclaredName` unterscheidet einen wirklichen Quellnamen von einem synthetischen Anzeigenamen. Eine unbenannte Assoziation bekommt eine stabile interne Identität, aber keinen erfundenen Benutzerquellnamen. `QualifiedName` ist eine geordnete Segmentliste für Anzeige und Namensindex. Sie ist keine Identität.

`Origin` ist eine geschlossene Hierarchie: `DeclaredOrigin(SourceRange)`, `DerivedOrigin(SymbolId declaredAt, DerivationKind)`, `PredefinedOrigin(LanguageVersion, String key)` und `SyntheticOrigin(SyntheticReason, Optional<SourceRange>)`. Bei einer geerbten Eigenschaft bleiben Deklarationsort und effektiver Besitzer getrennt.

<a id="klasseninventar"></a>
### Klasseninventar

Die folgenden Typen sind echte fachliche Varianten. Eine leere generische `Element`-Basisklasse mit später beliebigen Properties erfüllt den Auftrag nicht.

| Typ | Wesentliche Felder zusätzlich zum Kopf | Zentrale Invarianten/Abfragen |
|---|---|---|
| `ModelSymbol` | Sprachversion, Modellversionsliteral, Sprachkennung, Issuer/AT, Modellart, Imports, Translationbezug, geordnete Mitglieder | Sprach- und Modellversion getrennt; mehrere Modelle je Quelle möglich. |
| `TopicSymbol` | Basistopic, Dependencies, OID-/BasketOID-Regeln, deferred generics, Mitglieder | Vererbungs-/Dependencyregeln versionsbezogen. |
| `ClassSymbol` | Basisklasse oder Basisstruktur, Attribute, Constraints, OID-Regel | Gebundene Identität; Struktur-/Klassenunterschied bleibt explizit. |
| `StructureSymbol` | Basistyp, Attribute, Constraints | Keine erfundene Objektidentität; Einbettung separat. |
| `AssociationSymbol` | optionale Originalbezeichnung, Rollen, Attribute, Constraints, OID-/Kardinalitätsregel, Derived-From | N-stelligkeit und Assoziationen mit Attributen bleiben ausdrückbar. |
| `ViewSymbol` | Viewformation, Basen/Aliase, Selektionsausdrücke, abgeleitete Attribute | Formation als geschlossener Typ, keine beliebigen SQL-Fragmente. |
| `AttributeSymbol` | declared TypeId/Typzustand, Ableitungsausdrücke, Modifier, Überschreibungsbezug | Mandatory, transient, extended und effektive Reihenfolge getrennt. |
| `RoleSymbol` | Ziele/Restriktionen, Aggregationsart, Kardinalität, Ordnung, external/hiding, Ableitung | Rollen sind keine normalen Referenzattribute. |
| `DomainSymbol` | Typbeschreibung, Basistyp, Generic-Markierung, Domänenconstraints | Alias und einschränkende Domänenerweiterung unterscheiden. |
| `UnitSymbol` | Dimension, Basisbezug, Definition/Umrechnung, Symbolname | Exakte oder symbolische Umrechnung; keine obligatorische Double-Normalisierung. |
| `EnumElementSymbol` | Elternpfad, Kinder, Deklarations-/Ordnungsposition, Erweiterungsbezug | Baum und flache Blattliste getrennte Sichten. |
| `FunctionSymbol` | Parameter, Rückgabetyp, Erklärung, externer Funktionsschlüssel | Compiler prüft Signatur; er lädt keine Implementierungsklasse. |
| `ParameterSymbol` | Parameterart, Typ, Scope, optionaler Override | Funktions-, Laufzeit- und Signaturparameter getrennt. |
| `ConstraintSymbol` | `ConstraintDefinition`, Owner, optionaler Name, Herkunft | Kein untypisierter Ausdruckstext als einzige Repräsentation. |
| `ContextSymbol` | geordnete Generic-zu-Konkrettyp-Zuordnungen | Auswahlbedingungen und erlaubte Alternativen erhalten. |
| `MetadataBasketSymbol` / `MetaObjectSymbol` | Referenztopic/-klasse, geerbter Basket, Objektdeklarationen | Modellinformationen nicht mit transferseitigen Datenbaskets verwechseln. |
| `LineFormSymbol` | Name und referenzierte Segmentstruktur | Benutzerdefinierte Kurvenformen bleiben semantisch referenziert. |
| `GraphicSymbol` / `DrawingRuleSymbol` | Basis, Selektionen, bedingte Signaturparameterzuordnungen | Fachliche GRAPHIC-Sprache; unabhängig von der späteren UML-Visualisierung. |

`Symbol` ist sealed. `ViewableSymbol` ist die gemeinsame sealed Unterhierarchie für Klasse, Struktur, Assoziation und View. Records werden verwendet, wenn ihr struktureller Wertcharakter passend ist; graphübergreifende Beziehungen bleiben IDs, damit `equals()` keine zyklischen Objektgraphen durchläuft.

Die öffentliche API konkretisiert Laufzeitparameter als
`ParameterSymbol(header, typeId, mandatory, overrides)` in `interlis-model` /
`guru.interlis.model`. Der Header enthält Modell- oder Klassen-/Struktur-Scope, Quellursprung und
Deklarationsreihenfolge. `overrides` enthält optional die geerbte Parameteridentität. Funktionsargumente bleiben `FunctionParameter`;
Signatur-/METAOBJECT-Parameter verwenden denselben Parametervertrag und den unten beschriebenen MetaObjectType.
`SymbolValueExpression` enthält die gebundene `targetSymbolId` eines
Laufzeitparameters, einer Klassen- oder Attributpfadkonstante. Die Zielart ist
am Symbol ablesbar; fehlende Bindungen erzeugen Fehlerausdrücke statt Scheinziele.
`ClassValueType.restrictions()` liefert geordnete `SymbolId` statt Namenstrings.
`structureValue` unterscheidet STRUCTURE (auch Klassen) von CLASS (nur Klassen).
`AttributePathType` bewahrt geordnete Typrestriktionen und optional einen
`Owner`: `ClassAttribute(ExpressionId path)`, `Argument(SymbolId function,
int index)` oder `Viewable(SymbolId target)` für Konstanten. Der Index ist
nullbasiert in der unveränderlichen Funktionsparameterliste. OF-Ausdrücke
bleiben auch im ausgewählten `CompiledSchema` erreichbar. Alle Werte sind
nullfrei, unveränderlich und an denselben Graphsnapshot gebunden. Die Konstruktoren
kopieren Listen; fehlerhafte Bindungen verhindern das Schema. Es werden keine
Laufzeitwerte erzeugt. `ParameterClassFunctionContractTest` prüft öffentliche
Inhalte, Namespaces, Imports, Versionen, Übersetzungen und negative Typ-/Scopefälle.
Bei Aufrufen liefert `ATTRIBUTE OF @argument` den Bindungskontext einer
unqualifizierten Attributpfadkonstante aus dem tatsächlichen Argument,
wenn dieses eine konkrete Wurzel liefert. Bei einem nicht bestimmbaren
Owner (unbeschränkter Klassenwert oder UNDEFINED) bleibt der ebenfalls
normativ zulässige lexikalische Konstantenkontext erhalten; es wird keine
Klasse für das tatsächliche Argument erfunden.
Verkettete Abhängigkeiten werden vor der abhängigen Konstante gebunden;
die veröffentlichte Argumentliste bleibt dennoch in Quellreihenfolge.
Dies gilt auch für eine zulässige Abhängigkeit auf ein späteres Argument.
Explizit angegebene Klassenziele bleiben unverändert und werden exakt gegen
den erforderlichen Owner geprüft; ein bloss verwandter Klassenname genügt
nicht (2.3 §2.8.11/PDF46–47, 2.4 §3.8.11/PDF59–60: keine Subklasse).
Ein fehlgeschlagenes abhängiges Argument liefert keinen Ersatzkontext aus
dem aufrufenden Owner. Allgemeine ATTRIBUTE-Parameter ohne OF behalten
den gewöhnlichen lexikalischen Kontext. Diese Aufrufbindung ist kein
Vollständigkeitsnachweis für sämtliche Attributpfadoperationen.
Ein tatsächlich übergebener ATTRIBUTE-Wert mit `OF` auf genau denselben
gebundenen klassenwertigen Ausdruck erfüllt den abhängigen Ownervertrag;
eine gleiche Typrestriktion allein ersetzt diese Wertidentität nicht.
Bei einem abhängigen ATTRIBUTE-Rückgabetyp erhält ein erfolgreicher Aufruf
mit bindbarem Owner einen eigenen gebundenen Typ: konstante Klassenziele bleiben
`Viewable`, klassenwertige tatsächliche Ausdrücke bleiben als
`ClassAttribute(ExpressionId)` im selben Snapshot erreichbar. Die formale
Rückgabetypdeklaration und ihre `Argument`-Abhängigkeit bleiben unverändert.
Externe Funktionen werden dabei nicht ausgeführt.
Liefert ein zulässiges tatsächliches Argument keinen bindbaren Owner
(UNDEFINED oder eine Objektmenge mit unbeschränkter ANYCLASS-Wurzel), bleibt
der formale `Argument`-Bezug im Ergebnistyp erhalten. `FunctionCallExpression`
bewahrt das tatsächliche Funktionsziel und die geordneten tatsächlichen
Argumente im selben Snapshot. Dies ist eine legale wertabhängige Bindung,
kein Quellfehler, kein fehlender Implementierungszweig und kein erfundener
konkreter Owner. `ExpressionTypeState.RESOLVED` bezeichnet hier den geprüften
statischen Vertrag, nicht einen ausgeführten oder bestimmten Laufzeitwert.

Die API repräsentiert `FunctionParameter(String name, TypeId typeId, boolean mandatory)`
einen Parameter; `FunctionSymbol.returnType()` liefert ebenfalls `TypeId`.
Beide Typreferenzen gehören zum selben semantischen Graphen wie die Funktion.
Die Parameterliste ist defensiv kopiert, nullfrei und in Deklarationsreihenfolge.
Fehlerhafte oder noch nicht implementierte Typen werden ausdrücklich als solche
modelliert und dürfen keinen gültigen Compileabschluss erzeugen.
Aufrufe prüfen Argumentanzahl und Zuweisungsverträglichkeit über dieselben
Typregeln wie `TypeRelations.canAssign`; der Ausdruck übernimmt den deklarierten
Rückgabetyp. Die Signatur beschreibt keine Ausführung externer Funktionen.

Versionsregel: INTERLIS 2.3 verlangt `CONTRACTED` für Modelle mit Funktionen
([Referenz 2.3, Abschnitt 2.14](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf)).
In INTERLIS 2.4 hat der Modellmodifikator keine Bedeutung mehr
([Referenz 2.4](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf)).
Sein Fehlen ist dort kein Fehler, seine Anwesenheit kein Warnungsgrund.
Die syntaktische Information bleibt erhalten.

Fehlerhafte Objektpfade dürfen in einer Analyse `PathStep.UnresolvedStep(name,
range)` enthalten. Dieser nullfreie unveränderliche Schritt besitzt kein
Referenzziel und darf nicht als `THIS` interpretiert werden. Ein solcher
Bindungsfehler verhindert ein gültiges Schema für die betroffene Closure.

<a id="referenzsegmente"></a>
### Referenzsegmente

`NameReference` enthält `ReferenceId`, ursprünglichen Scope, Verwendungsart, eine geordnete Liste `ReferenceSegment`, den Gesamtbereich und `BindingResolution`. Jedes Segment besitzt den originalen Textbereich und seinen eigenen optionalen fachlichen Zielbezug. Ein vollständiger Pfad `Model.Topic.Class` kann deshalb separat auf Modell, Topic und Klasse navigieren.

`BindingResolution` unterscheidet `BoundTarget(SymbolId)`, `UnresolvedTarget(reason,candidates)`, `InvalidTarget(diagnosticIds)` und `NotAnalyzedTarget(reason)`. Felder werden nicht mit null markiert. Synthetische Bezüge, beispielsweise abgeleitete Vererbungskanten, sind keine vorgetäuschten Textreferenzen. Diese Gestaltung greift die quellsegmentbezogene Referenzidee in [GeoWerkstatt/Reference.cs](https://github.com/geowerkstatt/GeoW.Interlis.Tools/blob/02f89943b08030b898c56da6eb9b9cb7bdc361f7/src/Geowerkstatt.Interlis.Compiler/AST/Reference.cs) auf.

<a id="typensystem-und-exakte-werte"></a>
## Typensystem und exakte Werte

<a id="geschlossene-typvarianten"></a>
### Geschlossene Typvarianten

`TypeDescriptor` ist sealed und besitzt eine `TypeId`, Typart und Herkunft. Die folgende Menge ist als fachlicher Mindestumfang verbindlich; Hilfsvarianten dürfen ergänzt werden, wenn sie einen nachgewiesenen Sprachunterschied ausdrücken.

| Typfamilie | Erforderliche Varianten/Informationen |
|---|---|
| Text | `TextType` mit TEXT/MTEXT/NAME/URI-Semantik, optionaler Längenbegrenzung und Erweiterungsbedingungen. |
| Bool und Ausrichtung | `BooleanType`, `AlignmentType` mit horizontaler/vertikaler Art und definierten Werten. |
| Numerik | `NumericType` mit exakten Grenzen, Genauigkeit, Einheit, Zirkularität, Richtung und Referenzsystembezug. |
| Aufzählungen | `EnumerationType`, `EnumTreeValueType`; Baum, ORDERED/CIRCULAR, FINAL-Teile und Erweiterung. |
| Formatierte Werte | `FormattedType`; Basisstruktur/-domäne, Formatteile, Teilattributbezüge und Grenzen. |
| Zeit | Vordefinierte `DomainSymbol`-/`FormattedType`-Werte mit gebundenen Basisstrukturen und Attributen; `TemporalKind` bezeichnet ihre Zeitart. Die 2.4-Schlüsselwörter verwenden dieselben Domänen. |
| Geometrie | `CoordinateType`, `MultiCoordinateType`, `LineType`, `MultiLineType`, `SurfaceType`, `MultiSurfaceType`, `AreaType`, `MultiAreaType`. |
| Identifikation | `OidType` mit ANY/numerisch/Text/UUID-Semantik und eigener Herkunft. |
| Blackbox | `BlackboxType` mit XML/BINARY-Art. |
| Strukturen/Collections | `StructureValueType`, `CollectionType` mit BAG/LIST, Kardinalität und Restriktionen. |
| Referenzen | `ReferenceType` mit Zielmenge, EXTERNAL-Regel und Restriktionen. |
| Klassen-/Pfadwerte | `ClassValueType`, `AttributePathType`; Werte sind fachliche Klassen/Attributpfade, keine normalen Objektverweise. |
| Funktionsargumente | `ObjectArgumentType` für einzelne Objekte und Objektmengen, `EnumValueArgumentType`, `EnumTreeValueArgumentType`. |

`ObjectArgumentType(TypeId, Origin, Optional<SymbolId> base, List<SymbolId> restrictions,
boolean objects)` verwendet eine gemeinsame Zielbeschreibung für `OBJECT OF` und
`OBJECTS OF`. `objects=false` bezeichnet ein einzelnes Objekt (`OBJECT_ARGUMENT`),
`true` eine Objektmenge (`OBJECT_SET`). Die leere Basis bezeichnet `ANYCLASS`;
die leere Einschränkungsliste bedeutet keine zusätzliche Einschränkung.
Andernfalls enthält sie zulässige Spezialisierungen in Quellreihenfolge.
Alle Werte sind nullfrei und unveränderlich; Symbol- und Typreferenzen gehören
zum selben Snapshot. Die Typbeziehung prüft gebundene Identitäten und deren
Vererbung. Ein einzelnes Objekt kann als Menge mit höchstens einem Element
übergeben werden; eine statisch mehrwertige Menge erfüllt `OBJECT OF` nicht.
Skalare erfüllen keine der beiden Varianten. Einschränkungen ausserhalb der
Basis ergeben eine Typdiagnose; sie sind kein nachträglicher Laufzeitfilter.
Compiler, `objectCount` und `TypeRelations.canAssign` verwenden dieselbe Regel.
Positive Fälle schliessen Unterklassen, Funktionsresultate und Mengen ein;
negative Fälle fremde Klassen, fehlende Ziele, Skalare, Mengen an Einzelargumenten
und zu weit gefasste Zieltypen. Einzelobjekte sind auf Identität vergleichbar
(`==`, `!=`, `<>`), nicht auf Ordnung. Objektmengen sind keine skalaren
Vergleichswerte. Öffentliche Abfragen und Ausdrucksprüfung müssen übereinstimmen.

`ObjectSetExpression(id, typeId, typeState, range, explicitSelection)` repräsentiert `ALL` mit einer
gebundenen Objektmengentypreferenz. Ohne explizite Auswahl ist die Basis die
Kontextklasse; sonst die angegebene Basis samt Einschränkungen. Die immutable,
nullfreie Variante besitzt keine Ausdruckskinder. Fehlerhafte Auswahl erhält
`ERROR` und einen `ErrorType`, niemals ein untypisiertes erfolgreiches `ALL`.
`explicitSelection` unterscheidet `ALL(Class)` vom kontextgebundenen
`ALL`, selbst wenn beide dieselbe gebundene Klasse haben. In einem SET
bezeichnet nur das kontextgebundene `ALL` die durch `WHERE` gefilterte
Population. Der Vier-Argument-Konstruktor bleibt für kontextgebundenes
`ALL` verfügbar. Das zusätzliche Merkmal erweitert die SDK-API 0.8.

| Alias/Generics | `AliasType`, `GenericType`, `DeferredTypeBinding`; deklarierte und effektive Sicht bleiben unterscheidbar. |
| Analysefehler | `ErrorType` mit Diagnoseursache, `PendingType` mit nicht analysiertem Zustand; niemals produktive fachliche Datentypen. |

`MANDATORY` wird als Null-/Definiertheitsbedingung des Werts beziehungsweise Attributes modelliert. BAG/LIST-Kardinalität und Elementdefiniertheit werden separat behandelt. Ein Defaultwert für Collectiongrenzen oder Rollenmultiplizität muss aus dem jeweiligen Sprachprofil kommen und darf nicht als allgemeines Java-Default geraten werden.

Domainreferenzen werden anhand des gebundenen Ziels ausgewertet, unabhängig
von der Sortierung der Quelldateien. `DomainReferenceType` erhält die Domain-ID;
Ausdrücke und öffentliche Typabfragen verwenden deren aufgelösten Descriptor.
Dies gilt auch für importierte Funktionssignaturen, geerbte Attribute,
Struktur-/Indexpfade, Enum-Basen und FORMAT-Komponenten. Eine noch nicht
aufgebaute Symbolinstanz ist kein Grund für einen generischen Ersatztyp oder
eine Suche nach gleichnamigen Deklarationen ausserhalb der Sichtbarkeit.
Typabhängigkeitszyklen erzeugen Diagnosen und verhindern das gültige Schema;
Domainbedingungen bleiben separat erhalten und werden ebenfalls geprüft.
`ImportedDomainTypeContractTest` prüft positive und negative Mehrdateifälle in
beiden Sortierungen, Typidentitäten, Navigation und frische gegenüber
wiederverwendeter Analyse.

Geometrietypen speichern Achsen, Dimension, Vertexdomäne, Rotation, Referenzsystem, erlaubte Linienformen, Richtung, Überlappungsparameter und gegebenenfalls Linienattribute. Es werden keine JTS-Geometrieobjekte erzeugt. Eine gültige Typbeschreibung ist unabhängig davon, ob ein späterer konkreter Geometriealgorithmus ihre Daten bereits verarbeiten kann.

`UnitSymbol` erhält die operative Kurzbezeichnung im Header und die dokumentarische
Bezeichnung separat als `designation`. `base` bezeichnet die deklarierte
EXTENDS-Basis, `derivedFrom` das Ziel einer Umrechnung. `components` enthält
immutable `Component(operator, unit)`-Werte in Quellreihenfolge; der erste
Operator ist MULTIPLY, die übrigen MULTIPLY oder DIVIDE. IDs gehören zum
selben Snapshot. Bei Basis-/abgeleiteten Einheiten ist die Komponentenliste leer.
Das Feld `definition` bleibt für Anzeige und symbolische Umrechnungsnotation;
die Bindung und Prüfung zusammengesetzter Einheiten darf daraus keine zweite
Semantik ableiten. Dimensionen und vollständige exakte Umrechnungen bleiben
Teil des vollständigen Sprachvertrags.

Abstrakte Zusammensetzungen verlangen ausschliesslich abstrakte Komponenten,
konkrete ausschliesslich konkrete. EXTENDS verlangt eine abstrakte Basis;
eine zusammengesetzte Erweiterung erhält Anzahl und Reihenfolge der Operatoren
und spezialisiert die jeweiligen Komponenten. Importierte Kurzzeichen werden
über sichtbare Deklarationsidentitäten gebunden, unabhängig von Dateisortierung.
Ein nicht sichtbarer Name darf nicht über gleichnamige globale Einheiten
ersetzt werden. Fehlende Ziele und Abhängigkeitszyklen verhindern ein gültiges
Schema; die Traversierung prüft Abbruch und Arbeitsbudget. Gebundene
Komponentennamen und Kurzzeichen behalten ihre Quellbereiche für Navigation
und Rename. `ComposedUnitContractTest` prüft positive/negative Fälle beider
Versionen und den öffentlichen Zugriff auf diese Werte.

<a id="exakte-dezimalzahlen-und-kardinalitäten"></a>
### Exakte Dezimalzahlen und Kardinalitäten

Der Literalparser prüft, dass Lexem, exakter Wert und Präzisionsdaten zusammenpassen. Das Lexem bleibt für Diagnostik und Modelltreue erhalten. Eine intern normalisierte Vergleichsform darf ergänzend existieren. Unabhängige Dokumentationstexte dürfen nicht als numerische Identität dienen.

`DecimalPrecision` ist hier die Speicherform der lexikalischen Angaben. Die fachliche INTERLIS-Genauigkeit wird von `NumericTypeRules` aus Lexem, Exponent und Domänenkontext berechnet und durch Legacy-/Normfixtures abgesichert. Der Coding-Agent darf `BigDecimal.scale()` nicht ohne diesen Schritt mit der INTERLIS-Genauigkeit gleichsetzen. `PI` und `LNBASE` werden als symbolische Konstanten modelliert; der Compiler muss sie nicht stillschweigend in einen gerundeten Double umwandeln.

Kardinalitätsgrenzen sind nichtnegativ. Ein endliches Maximum muss mindestens dem Minimum entsprechen. Unbegrenzt ist eine eigene Variante und weder `-1` noch `Long.MAX_VALUE`. Ob eine bestimmte Kardinalität in einem Sprachkontext erlaubt ist, prüft die Semantik. Numerische JSON-Projektionen verwenden bei möglichem Präzisionsverlust Strings beziehungsweise explizite exakte Zahlobjekte.

<a id="typrelationen"></a>
### Typrelationen

`TypeRelations` ist ein zustandsfreier Dienst über einen unveränderlichen Graphen. Er führt keine Datenvalidierung aus. Er benötigt folgende Operationen:

```java
QueryResult<EffectiveType> resolveAliases(TypeRef type);
QueryResult<TypeRelation> canAssign(TypeRef source, TypeRef target,
                                  AssignmentContext context);
QueryResult<TypeRelation> canExtend(TypeRef derived, TypeRef base,
                                  ExtensionContext context, LanguageVersion language);
QueryResult<TypeRelation> areComparable(TypeRef left, TypeRef right,
                                      ComparisonOperator operator);
QueryResult<UnitRelation> compareUnits(SymbolRef left, SymbolRef right);
```

`TypeRelation` enthält `RelationDecision { ALLOWED, DISALLOWED }`, einen stabilen Grund und die relevanten Herkunfts-/Typverweise. Noch nicht bekannte oder fehlerhafte Typen ergeben `NotAnalyzed` beziehungsweise `Invalid`; sie sind nicht automatisch „zueinander passend“. Kontextparameter unterscheiden etwa normale Zuweisung, Attributüberschreibung, Domänenerweiterung und Funktionsargument. Eine universelle `isSubtype()`-Booleanmethode würde diese fachlichen Unterschiede unzulässig zusammenziehen.

Aliaszyklen, Enum-Erweiterungen, eingeschränkte Zielklassen, Einheiten und numerische Grenzen werden mit besuchten Knoten beziehungsweise komponentenweiser Auswertung geprüft. Diagnoseerzeugung und reine Relationsberechnung sind getrennt, damit dieselbe Relation nicht bei jeder Query neue Meldungen anhängt.

<a id="ausdrücke-constraints-und-weitere-fachsprache"></a>
## Ausdrücke, Constraints und weitere Fachsprache

<a id="typisierte-ausdrücke"></a>
### Typisierte Ausdrücke

`TypedExpression` ist eine geschlossene Hierarchie mit ID, Quellursprung, Ergebnis-Typzustand und geordneten Kindern. Mindestens erforderlich sind Literal-, Namens-/Pfad-, Unary-, Binary-, Funktionsaufruf-, DEFINED-, Parameter-, Klassenkonstanten-, Attributpfadkonstanten-, ALL-/Objektmengen- und Inspection-Varianten. `UNDEFINED` ist eine sprachliche Konstante mit eigener Definiertheitssemantik. Sie ist kein `ErrorType` und kein Java-null.

Ein `ObjectPathExpression` besteht aus typisierten `PathStep`-Varianten: Attribut, Rolle, Assoziationspfad, THIS/PARENT, struktur-/listenbezogene Auswahl, Aggregates und die weiteren normativ zulässigen Kontextschritte. Jeder Schritt speichert Eingabe-/Ausgabetyp, Kardinalitätswirkung und aufgelöstes Ziel beziehungsweise Fehlerzustand. Ein vollständiger Pfad wird nicht nur als String gespeichert.

`THIS` übernimmt den Typ seines Kontextes: in einer Wertebereichsbedingung den
Wertebereichstyp, in einem Objektpfad den Objekt- beziehungsweise Strukturtyp.
Objektverweise sind keine Boolean-Werte. Nach einem skalaren Pfadwert ist kein
weiterer Attributzugriff zulässig. Assoziationszugänge (`\`), Indizes und
Inspektionsschritte dürfen nicht durch Weglassen des Operators als gewöhnliche
Namensschritte interpretiert werden.

`PathStep.AssociationAccessStep(name, association, sourceRole, targetRole,
associationObject, cardinality, resultType, range)` hält einen Zugang von einem
Klassenobjekt über eine Assoziation fest. Alle IDs und Typen gehören zum
umgebenden Snapshot. `sourceRole` verbindet das Ausgangsobjekt, `targetRole`
bezeichnet die angesprochene Gegenrolle. `associationObject=true` (`\rolle`)
selektiert die Assoziationsinstanzen, sonst deren Zielobjekte. Kardinalität und
Zwischentyp bleiben auch dann erhalten, wenn ein nachfolgendes Attribut den
Endtyp des Pfades bestimmt. Eine direkte `RoleStep` auf einer Assoziationsinstanz
selektiert dagegen je Assoziationsinstanz einen Beteiligten. Ausdrücke, Signaturen und
Zwischentypen müssen vollständig in der ausgewählten `CompiledSchema`-Closure
verfügbar bleiben. Graphkonstruktion prüft referenzierte Ziele und Typen;
fehlende IDs sind ein Vertragsfehler. Die Bindung muss Mehrdeutigkeit, Topic-
Sichtbarkeit, EXTERNAL und geerbte Zugänge beachten. Unvollständig unterstützte
Rollenkomposition darf kein gültiges Schema erzeugen.

`PathStep.AssociationSourceStep(name, association, sourceRole, cardinality,
resultType, range)` hält den Ausgangsrollenzugang auf Assoziationsinstanzen fest,
auch bei `rolle[Assoziation]`. Er besitzt keine künstliche Gegenrolle. Der
Qualifikator bindet die Assoziation, der Rollenname die Ausgangsrolle; beide
besitzen eigene `NameReference`-Bereiche. Alle Werte sind nullfrei und immutable,
IDs snapshotlokal, Bereiche an Quelle und Revision gebunden. Die Variante ist
Teil der geschlossenen `PathStep`-Familie.

Die Zugangskardinalität gilt je Ausgangsobjekt. Bei einer n-stelligen Beziehung
(n > 2) bindet eine einzelne Teilnehmeridentität nicht die Kombination aller
anderen Rollen; deren Rollenkardinalität darf deshalb keinen Singletonzugang
vortäuschen. Der statische Zugang verwendet konservativ `0..*`. Der Ergebnistyp
bewahrt zusätzlich eine bereits im vorherigen Schritt entstandene Objektmenge.
Weitere Rollen-, Assoziations- und Referenzattributschritte führen diese Menge
fort. Eine Folgebeziehung muss für sämtliche Zielalternativen passen. Skalare
Attributwerte mehrerer Objekte dürfen nicht still zu einem einzelnen Wert
werden. Allgemeine Strukturmengen-/Inspektionspfade bleiben eine eigene
verpflichtende Semantikabnahme. Positive und negative Vertragstests prüfen n-stellige
und qualifizierte Zugänge in 2.3/2.4, Typen, genaue Navigations-/Renamebereiche,
Mehrdeutigkeit, EXTERNAL, HIDING, Zielmengen und Mengenfortsetzungen.

`PathIndex(kind, position)` (model) enthält FIRST/LAST ohne Position oder
POSITION mit exaktem `BigInteger`. Werte sind immutable und nullfrei; nullbasierte
Indizes sind sprachlich ungültig. `PathStep.IndexStep(index, resultType, range)`
folgt auf den `AttributeStep`, dessen Namensbereich und Referenzziel separat
bleiben. Sein Quell-/Revisionsbereich umfasst Attribut und Klammern;
`resultType` gehört zum selben Snapshot und bleibt in der Schema-Closure.
LIST-Auswahl liefert den Elementtyp; Koordinatenauswahl den exakten Achsentyp.
Indizes beginnen bei 1 und dürfen eine endliche LIST-Obergrenze beziehungsweise
die Achsenzahl nicht überschreiten. FIRST/LAST gelten nur für LIST. Eine
Position oberhalb der Mindestkardinalität bleibt erlaubt und kann zur Laufzeit
undefiniert sein. BAG, skalare Werte und unselektierte Mengen eröffnen keinen
Indexzugang. Ungültige Auswahl erzeugt einen typisierten Fehler samt Diagnose.
Positive/negative Verträge prüfen Grenzwerte, sehr grosse Positionen, verschachtelte
Fortsetzungen, Zwischen-/Endtypen, Navigation und Rename in beiden Versionen.

UNIQUE- und EXISTENCE-Pfade verwenden dieselbe Bindung wie andere
`PathExpression`-Werte. `UniquenessConstraint.keys()` ist eine geordnete Liste
von `ExpressionId`; `where()` hält den optionalen booleschen Filter.
`LocalUniqueness(prefix, fields)` enthält eine Präfix-`ExpressionId` und geordnete
Feld-`ExpressionId`s. Der Präfix führt ausschliesslich über Strukturattribute;
die Felder binden im ausgewählten Strukturelement. Globale Schlüssel dürfen
keine mehrwertigen Zugänge oder Strukturwerte als Endwert besitzen. LOCAL
liefert keine leeren Ersatzschlüssel. `basket()` und `name()` bleiben erhalten.

`ExistenceConstraint.source()` und `ExistenceTarget.path()` sind ebenfalls
`ExpressionId`s; die Alternative erhält ihre gebundene `viewable()`-Identität.
Jeder Zielpfad bindet in seinem eigenen Objektkontext, bei unverändertem
lexikalischem Topic des Constraints. Abhängigkeiten werden über Topic-Identitäten
geprüft, nicht anhand der Schreibweise eines Namens. Keine ungültige Alternative
darf still weggelassen werden und dennoch ein gültiges Schema ergeben.

EXISTENCE prüft Vergleichbarkeit von Attributwerten, nicht die Zuweisbarkeit
des vollständigen Quellbereichs. Zahlenintervalle und Textlängen allein sind
keine Modellablehnungsgründe. Einheiten-/Richtungs-/Referenzsystemmetadaten,
Koordinatenform und Strukturidentitäten bleiben relevant. Die allgemeinen
Zuweisungsregeln behalten ihre Bereichsprüfung. Die begründete Auslegung und
Referenzunterschiede stehen im Normnachweis.
Höchstens einwertige Struktur-Collections sind als Einzelstruktur vergleichbar.
Ganze mehrwertige Collection-Endwerte behalten Collectionart, Kardinalität und
Elementtyp. Der Vergleich verwendet dieselbe Collectionart und kompatible
Elementwerte, nicht die vollständige Zuweisbarkeit der Kardinalitätsintervalle.
Er ist keine elementweise Pfadfortsetzung und flacht keine Mengen ab.
LIST-Auswahl liefert einen einzelnen Wert. Die begründete Auslegung und
Referenzabweichungen stehen im Folgenachweis.

Alle IDs gehören zum umgebenden Snapshot; Quellbereiche, Endtypen, Fehlerzustände
und Schritte sind über `SemanticGraph.expression(id)` zugänglich. Das Schema
bewahrt alle referenzierten Ausdrücke und Typen; Graphkonstruktion prüft fehlende
Ausdrucks-/Zielreferenzen. Listen sind nullfrei, immutable und in Quellreihenfolge.
Abbruch verwendet den bestehenden Laufkontext. Unbekannte Pfadformen verhindern
ein gültiges Schema mit einer expliziten Implementierungslücke. Positive und
negative Vertragstests prüfen innere/externe Constraints, WHERE, BASKET, LOCAL,
verschachtelte Pfade, Zielalternativen, Scope, Typen, Kardinalitäten und Rename.

`EnumerationType.values()` ist der effektive Baum in Präordnung mit stabiler
Geschwisterreihenfolge und ursprünglichen Bereichen geerbter Werte.
`baseType(): Optional<TypeId>` bezeichnet den unmittelbaren effektiven
Basistyp; `finalSubtrees(): Set<String>` enthält geschlossene Geschwisterlisten
einschliesslich geerbter Abschlüsse. `finalPart()` fragt nur die Wurzel ab.
Vererbung von Domänen und EXTENDED-Attributen verwendet dieselbe Baumregel;
Zuweisung und öffentliche Typqueries verwenden dieselbe Identitätskette.

Die einfachen vordefinierten Domänen `INTERLIS.INTERLIS_1_DATE`,
`INTERLIS.HALIGNMENT` und `INTERLIS.VALIGNMENT` sind `DomainSymbol`-Werte
mit `FINAL` und `PredefinedOrigin` je Sprachversion. Ihre Verwendung erhält
einen `DomainReferenceType`; das Ziel enthält `TEXT*8` beziehungsweise einen
horizontalen/vertikalen `AlignmentType`. Wiederholte Verwendungen teilen die
Domänenidentität im Graph; Quelldeklarationsbereiche werden nicht erfunden.
Typqueries lösen diese Domänen wie gewöhnliche Aliasse auf. Ein unbekannter
vordefinierter Name besitzt einen `ErrorType` mit Diagnoseursache und erhält
keinen produktiven Generic-Ersatz. FINAL verbietet die Erweiterung der Domäne,
nicht ihre Verwendung als Attributtyp. Dies vervollständigt noch nicht den
gesamten vordefinierten Modellinhalt aus Anhang A.

`FormattedType` enthält eine optionale Basisdomäne, Basisstruktur, vordefinierte
Zeitart, effektive geordnete `Part`-Werte und deklarierte lexikalische Grenzen.
Teile erhalten Attribut-/Formatdomänen-IDs, optionale exakte Stellenzahl,
Literal und Herkunft. `formatName` ist ausschliesslich Anzeigetext.
XMLDate, XMLTime und XMLDateTime besitzen dieselben gebundenen Teile wie
benutzerdefinierte Formate. Ihre GregorianDate-/UTC-Strukturen, GregorianYear,
numerischen Komponenten, Einheiten und Referenzsysteme bleiben im Graph
öffentlich erreichbar. `Origin.PredefinedOrigin` nennt die Sprachversion;
es wird keine Quelldeklaration vorgetäuscht. `predefined()` klassifiziert die
Zeitart und bestimmt keine zweite Wertprüfung. Breiten und Genauigkeit folgen
dem jeweiligen Anhang A: 2.3 hat bei XMLDate keine expliziten Breiten; 2.4
verwendet Year/4, Month/2 und Day/2. XMLTime/DateTime verwenden in 2.3 Hours/2,
in 2.4 zusätzlich Minutes/2 und Seconds/2. Beide verwenden die deklarierte
Sekundenpräzision 0.000..59.999. Zusätzliche Kalenderprüfungen gehören nicht
zum Compilervertrag. DATE/TIMEOFDAY/DATETIME werden in 2.4 als
`DomainReferenceType` auf diese Domänen gebunden. Der parallele `TemporalType`
entfällt.
Geerbte Teile stehen vor Ergänzungen; Grenzen der gesamten Basiskette bleiben
wirksam. `OidType.valueType(): Optional<TypeId>` erhält den numerischen oder
textlichen Träger; ANY hat keinen konkreten Träger. Vordefinierte OID-Domänen
besitzen explizite synthetische Herkunft, keine vorgetäuschte Quelldeklaration.
Alle IDs gehören zum umgebenden Snapshot; Listen und Mengen sind immutable
und nullfrei. Der Graph prüft Referenzen und bewahrt benötigte Typen in der
ausgewählten Closure. Konstruktion und Validierung benutzen den bestehenden
Laufkontext; ungelöste Referenzen ergeben Diagnosen, fehlende Implementierung
verhindert ein gültiges Schema. Vertragstests prüfen effektive Inhalte,
Herkunft, Queries, Navigation/Rename, Vererbungsfehler und portable Parität.
Diese Verträge ersetzen keine vollständige Typfamilienabnahme; deren aktuelle
Grenzen stehen im Folgenachweis.

FORMAT-Konstanten und Grenzen werden aus den gebundenen Bestandteilen geprüft:
Vorkommastellen berücksichtigen die explizite Auffüllbreite, Nachkommastellen
die lexikalische Mantissenpräzision des numerischen Bereichs. Exponent und
Mantissenpräzision bleiben getrennt; es gibt keine Rundung über `double`.
Verschachtelte Formate prüfen ihre eigenen und geerbten Grenzen, bevor der
äussere Wert verglichen wird. Vergleiche bleiben lexikografisch über die
numerischen Komponenten; ein geerbter Präfix behält seine Bereichsgrenzen.
Abstrakte numerische Formatkomponenten bleiben als unbeschränkte numerische
Attributtypen im Graph erhalten. Die fehlende Präzision ist legale Abstraktion,
keine Implementierungslücke. Eine darauf beruhende Domain oder ein verwendendes
Attribut muss ABSTRACT sein, auch bei verschachtelten Formaten. Explizite
FORMAT-Grenzen schränken die exakten Komponentenwerte ein; sie erfinden keine
numerische Präzision. Bei INHERITANCE über eine spezialisierte Struktur
verweisen die effektiven Teile auf deren wirksame Attribute. Reihenfolge,
Auffüllbreite und deklarierte Herkunft bleiben erhalten. Die konkretisierte
Präzision und alle geerbten Grenzen gelten gemeinsam für Grenzen und Konstanten.

`SymbolHeader.oidRules(): List<OidRule>` enthält gebundene effektive Regeln,
Basket vor Objekt. `OidRule(basket, domain, origin)` gehört zu
`guru.interlis.model`; alle Ziel-IDs gehören zum Snapshot. Eine leere Liste
enthält keine explizite oder übernommene Regel. Eine einzelne Objektregel mit
leerem `domain` bezeichnet explizites NO OID. Fehlgeschlagene Bindung darf
nicht als NO OID erscheinen. Vererbte Regeln und Topic-Defaults behalten ihre
ursprüngliche Herkunft; Klassen übernehmen ausschliesslich Objektregeln.
Strukturen erhalten keine OID-Regeln. Nur in 2.4 bedeutet eine fehlende
Klassen-/Topic-Festlegung implizites NO OID; dessen Vererbung folgt dann
denselben Konkretisierungsregeln wie explizites NO OID. INTERLIS.NOOID steht
nur in 2.4 zur Verfügung. In 2.3 darf eine noch fehlende Festlegung ergänzt,
ein ausdrücklich geerbtes NO OID jedoch nicht in ANYOID geändert werden.
In 2.4 ist die Klassen-Konkretisierung NO OID → ANYOID → konkrete OID möglich;
ein direkter Sprung von NO OID zur konkreten OID ist unzulässig. Eine einmal
zugewiesene Topic-OID bleibt in beiden Versionen unverändert. Die eingebaute
NOOID-Domäne kann nicht durch beliebige eigene OID-Domänen erweitert werden.
Domänenziele müssen OID-Typen sein. In 2.3 verlangen explizite OID-AS-Angaben
CONTRACTED; 2.4 hat diese Pflicht aufgehoben. Die Regeln für unveränderliche
konkrete OID-Zuordnung und legale Konkretisierung gelten auch bei Vererbung.
Syntax, Diagnose, Navigation/Rename und Schema-Closure benutzen dieselben
gebundenen Ziele. Fehler sperren das Schema, der Teilgraph bleibt inspizierbar.
`FormatPrecisionOidBindingContractTest` prüft positive/negative Regeln,
Getrenntheit von Basket/Objekt, Herkunft, Scope, Typen und öffentliche Abfragen.
`OidFollowupAbstractFormatContractTest` ergänzt Versionsunterschiede,
Topic-Zuordnungen, abstrakte Komponenten und effektive FORMAT-Attributziele.
`NoOidReferenceContractTest` prüft zusätzlich die NO-OID-Regel für
Referenzattribute und Rollenzielmengen. Die vollständige Semantikabnahme und alle
FORMAT-Relationen bleiben weiterhin offen.

`ReferenceType(id, origin, target, restrictions, external)` gehört zu
`interlis-model` / `guru.interlis.model`. `target: Optional<SymbolId>` ist bei
ANYCLASS leer; `restrictions: List<SymbolId>` enthält die disjunktiven
zugelassenen Unterhierarchien in Quellreihenfolge. `targetRoots()` liefert die
Restriktionen, sonst das Primärziel; leer bezeichnet uneingeschränktes ANYCLASS.
Alle Werte sind nullfrei, unveränderlich und snapshotgebunden. Klasse und
Assoziation sind zulässige Zielarten, Struktur nicht. Jede Restriktion muss
das Primärziel spezialisieren. Die gemeinsam genutzte Typverträglichkeit
prüft sämtliche möglichen Ziele; Erweiterungen dürfen nur einschränken und
EXTERNAL entfernen, nicht hinzufügen. Topicübergreifende Referenzziele verlangen
EXTERNAL, auch in Restriktionen und Collection-Elementen; geerbte Ziele im
Topic-Vererbungsweg bleiben davon ausgenommen. Namensreferenzen erhalten die gebundenen
Primär- und Einschränkungsziele für Navigation/Rename. Pfade verwenden diese
Menge und ihren gemeinsamen Kontext statt des ersten Listenelements.

Referenzattribute und Rollen dürfen Klassen mit effektivem explizitem oder
implizitem NO OID zum Ziel haben, auch über Behältergrenzen hinweg. Der
Compiler bewahrt die effektiven OID-Regeln und entscheidet unabhängig von
der Transferart. Im FULL-Transfer können transferweit eindeutige TIDs die
Zielobjekte identifizieren. Die Transfergemeinschaft legt fest, ob sie
stabile OIDs verwendet und inkrementelle Transfers unterstützt. Die
OID-Anforderungen für INITIAL und UPDATE werden beim konkreten Transfer
geprüft. Für 2.4 gilt [Normentscheidung 23](norm-decisions.md#decision-23).
Restriktionen ersetzen die zugelassene Basismenge; alle OR-Alternativen und
Restriktionswurzeln werden geprüft, einschliesslich Vererbung und lokalem
EXTENDED-Kontext. Eine Assoziation als Referenzziel benötigt in beiden
Versionen eine eigene Identität (Referenz 2.7.1/3.7.1), aus OID AS oder der
OID-Eigenschaft; ihre Rollenidentifikation allein genügt nicht.
Uneingeschränktes ANYCLASS bezeichnet eine offene polymorphe Zielmenge.
Der Compiler zählt dafür zufällig vorhandene Klassen nicht als geschlossene
Menge auf. Die Zulässigkeit konkreter Transferreferenzen ist Aufgabe der
späteren Datenvalidierung; explizite Restriktionen werden bereits statisch
geprüft. Fehler verhindern ein gültiges Schema der betroffenen Closure.

`TopicSymbol.dependencies()` bewahrt direkt deklarierte, gebundene Topic-IDs
in Quellreihenfolge. Die Liste ist nullfrei, immutable und snapshotgebunden;
geerbte Abhängigkeiten werden über `baseTopic()` ermittelt, nicht in diese Liste
kopiert. DEPENDS-ON-Namen haben eigene gebundene Referenzsegmente, einschliesslich
Modellqualifikation, für Navigation und Rename. Unauflösbare oder artfalsche
Ziele bleiben Diagnosen; Namenssuffixe sind kein Ersatz für Identitätsbindung.

Für Referenzattribute und Rollen gelten in 2.3 und 2.4 dieselben
Abhängigkeitsregeln: Ziele im eigenen Topic oder dessen Basis-Topic sind lokal.
Andere zugelassene Zielwurzeln benötigen EXTERNAL und eine explizite, direkt
oder aus dem Basis-Topic geerbte Abhängigkeit auf ihren Topic. Eine Kette von
DEPENDS-ON-Kanten ersetzt keine direkte Deklaration. Eine reine MANDATORY-Überschreibung übernimmt den gebundenen Typ der
Basis bereits vor der Ausdrucksbindung; ein generischer Platzhalter darf weder
Typ- noch Zielprüfung umgehen. Die gebundene `TypeId` ist dieselbe wie beim
geerbten Attribut, auch über mehrere Überschreibungen. Pfade, Funktionsargumente,
UNIQUE/EXISTENCE und öffentliche Typabfragen benutzen diese eine Bindung.
Fehlende geerbte Attribute und Zyklen sind fachliche Fehler. Ein expliziter Typ
wie `NUMERIC` darf nicht als ausgelassener Typ interpretiert werden.
`ClassSymbol.baseType()` erhält auch eine Basisstruktur gemäss den verwendeten
PDF-Referenzen 2.3 §2.5.3 und 2.4 §3.5.3; ein zuvor akzeptierter Basistyp darf
nicht aus dem Schema verschwinden. Der Widerspruch zur korrigierten Onlinefassung
2.4 ist im Fachnachweis
als offene Normprofilklärung festgehalten; keine pauschale Vollständigkeitszusage.
Die Prüfung umfasst alle
Restriktionen/OR-Alternativen, effektive geerbte Mitglieder und Referenzen in
verschachtelten Strukturwerten am verwendenden Topic. Identitäten entscheiden;
gleiche Klassen- oder Topic-Namen sind kein Sichtbarkeitsbeleg. Abhängigkeitszyklen
einschliesslich geerbter Selbstabhängigkeiten sind Fehler und sperren das Schema.
Die Traversierung prüft Arbeitsbudget/Abbruch und benötigt keinen rekursiven
Java-Aufrufstapel. `TopicDependencyContractTest` prüft positive und negative
Fälle beider Versionen sowie die gebundenen Ziele im öffentlichen Schema.

`RoleSymbol.targets()` enthält nichtleere, geordnete `RoleTarget(base,
restrictions)`-Alternativen. Alle IDs gehören zum selben Snapshot; Basis und
Restriktionen sind Klassen oder Assoziationen. `ANYCLASS` hat eine explizite
vordefinierte Identität. Eine leere Restriktionsliste erlaubt die ganze
Basishierarchie; sonst ist die Liste eine Disjunktion spezialisierter Hierarchien.
`allowedRoots()` beziehungsweise `RoleSymbol.targetRoots()` liefern diese
Wurzeln in Deklarationsreihenfolge, ohne Unterklassen aufzuzählen. Alle Werte
sind nullfrei und unveränderlich. OR-Basen dürfen nicht voneinander abgeleitet
sein. Ein Pfad oder Funktionsargument muss für sämtliche möglichen Ziele gelten;
ein Verbraucher darf nicht stellvertretend das erste Ziel wählen.

`AssociationSymbol.baseAssociation()` bewahrt die optionale gebundene Basis.
`cardinality()` enthält die effektive Linkmultiplizität; fehlende Angaben werden
aus der Basis übernommen, bei einer Wurzel gilt genau eins.
`roles()` und `attributes()` bleiben eigene Deklarationen. Die vorhandene
`effectiveMembers`-Abfrage liefert geerbte und überschriebene Mitglieder samt
Herkunft und stabiler Position. Eine Erweiterung darf keine Rollen hinzufügen,
Zielmengen oder Kardinalitäten erweitern, FINAL überschreiben oder EXTERNAL
ändern. Fehlende Kardinalität erbt die effektive Grenze. Bei Klassenzugängen
bleibt ohne HIDING der allgemeine Zugang erhalten; HIDING an der Ausgangsrolle
unterdrückt ihn für die spezialisierte Teilnehmerhierarchie. Widersprechende
Geschwistererweiterungen sind Fehler. Eine Qualifikation darf weder HIDING noch
Topic-Sichtbarkeit umgehen; fehlende Unterstützung muss das Schema sperren.

Der Objektkontext eines externen Constraints und dessen lexikalischer Topic
sind getrennt zu erhalten. Lokale `EXTENDED`-Klassen und -Assoziationen werden
über gebundene Vererbungsidentitäten im Topic-Vererbungsweg angewandt. Eine
unabhängige gleichnamige Klasse darf den Kontext nicht ersetzen. Positive und
negative Vertragstests prüfen 2.3/2.4, Zielreferenzen, effektive Reihenfolge,
Typabfragen, HIDING und Diagrammkanten für sämtliche Zielalternativen.

Wertebereichsbedingungen sind in 2.4 benannte, geordnete Bedingungen ihres
Wertebereichs; 2.3 lässt diese Syntax nicht zu. Ihre Ausdrücke bleiben als
`DomainConstraint` mit gebundenen Typen im Graph erhalten. Jeder Ausdruck muss
Boolean ergeben; Namen sind innerhalb der Wertebereichsdefinition eindeutig.
Ein Verbraucher findet die zugehörigen `ConstraintSymbol`-Werte über ihre
Owner-ID. Die Weitergabe und Prüfung geerbter Bedingungen bleibt Teil des
vollständigen Sprachvertrags.

Der Compiler prüft Operatorzulässigkeit, Operandentypen und Funktionssignaturen. Er wertet keine benutzerdefinierte Java-Funktion aus und liest keine Transferobjekte. Konstante Auswertung ist optional und nur zulässig, wenn sie exakt, deterministisch und fachlich gleichwertig ist; sie ersetzt nicht den ursprünglichen Ausdruck für Herkunft und Diagnostik.

<a id="vollständiger-fachlicher-featurekatalog"></a>
### Vollständiger fachlicher Featurekatalog

Jede Zeile erhält in der Implementierung mindestens einen positiven, einen relevanten negativen und einen Metamodell-/Querytest. Die Zulässigkeit wird je Sprachversion mit der normativen Versionstabelle aus04 verbunden. Eine aus der Ausgangsgrammatik erkennbare Form ist noch kein Nachweis, dass sie in beiden Versionen zulässig ist.

| Featuregruppe | Verbindlicher fachlicher Umfang |
|---|---|
| Modellkopf | Modellart, Sprachkennung, Issuer, Versionsliteral, CONTRACTED, Translation, erlaubte Headeroptionen und Endnamen. |
| Import/Scope | QUALIFIED/UNQUALIFIED-Sichtbarkeit, Mehrdeutigkeit, gesetzliche Vorwärtsreferenzregeln und Modell-/Topicgrenzen. |
| Topics | Vererbung, abstract/final, VIEW TOPIC, Dependencies, Objekt-/Basket-OIDs und deferred generics. |
| Klassen/Strukturen | Vererbung, abstract/extended/final, OID/NO OID, geerbte und lokale Eigenschaften. |
| Attribute | Mandatory, Ableitung, transient, Erweiterung, subdivision/continuous und restriktive Typen. |
| Assoziationen | Binär/n-stellig, Rollen, Aggregation/Komposition, External/Ordered/Hiding, Rollenrestriktionen, Kardinalitäten, eigene Attribute und abgeleitete Beziehungen. |
| Domänen | Alias, Erweiterung, abstract/final/generic, obligatorische Werte und Domänenconstraints. |
| Basistypen | Sämtliche Familien aus Abschnitt5 samt ihren Versionseinschränkungen und Erweiterungsregeln. |
| Aufzählung | Verschachtelung, Dotted-Names, Reihenfolge, Zirkularität, FINAL-Teilbäume, Erweiterungen, ALL OF und OTHERS. |
| Numerik/Einheiten | Genauigkeit, Grenzen, Kreissinn, Zirkularität, Basis-/abgeleitete-/zusammengesetzte Einheiten und symbolische Definitionen. |
| Referenzsysteme | Metaobjekt- und Domänenbezüge, Achsenbezüge, Koordinatenrotation und REFSYS-Angaben. |
| Linien/Flächen | VERTEX, STRAIGHTS/ARCS/eigene Formen, Überlappungsparameter, Richtung und Linienattribute; Multi-Varianten nach Version. |
| Formate/Zeit | FORMAT mit Basisstruktur/-domäne, Formatelemente und Grenzen; zeitbezogene Formen nach Version. |
| Generics/Context | Zulässige Generic-Domänen, konkrete Alternativen, Context-Zuordnung, aufgeschobene Konkretisierung und dessen Gültigkeitsbedingungen. |
| Metaobjekte | SIGN-/REFSYSTEM-Baskets, Vererbung, deklarierte Metaobjekte und referenzierte Klassen. |
| Parameter | Laufzeitparameter, Signaturparameter, METAOBJECT-Parameter und Funktionsargumente. |
| Mandatory | Boolesche Konsistenzbedingung, benannt oder unbenannt gemäss Version. |
| Plausibility | Prozentgrenze, Richtung und boolescher Ausdruck, mit getrenntem späterem Datenumfang. |
| Existence | Quellpfad, erlaubte Zielklassen/-views, alternative Zielpfade und Typverträglichkeit. |
| Uniqueness | Globale, lokale und Basket-bezogene Form, Vorbedingung/WHERE, zusammengesetzte Pfade. |
| Set Constraints | Menge und Kontext, LOCAL/BASKET soweit erlaubt, WHERE/Vorbedingung und eigentlicher Ausdruck. |
| Externe Constraints | CONSTRAINTS OF und Zuordnung zur erlaubten Zieldeklaration. |
| Ausdrücke | Normative Präzedenz, Relationen, Logik, Arithmetik, DEFINED, UNDEFINED, Konstanten und Mengenargumente. |
| Objektpfade | Direkte und inverse Pfade, Spezialschritte, Collectionselektion, Rollen-/Attributwechsel und Kontextregeln. |
| Funktionen | Deklaration, Argumentarten, Aufrufauflösung, Anzahl/Typen, Rückgabewert und externe Signaturidentität. |
| Views | Projection, Join einschliesslich optionaler Basis, Union, Aggregation, Inspection/Area Inspection, Aliasbasen, Base Extension, Selection und Viewattribute. |
| Graphics | GRAPHIC-Vererbung/Basis, Drawing Rules, Signaturparameterzuordnungen, bedingte Zuweisungen und Enumranges. |
| Übersetzungen | Referenzmodell/-version, strukturelle Zuordnung, Typ-/Constraint-/Rollenentsprechung und voneinander getrennte Namen. |
| Dokumentation/Metadaten | Quellkommentare, Metaattribute, Erläuterungen und nachvollziehbare Herkunft. |
| Vordefiniertes Modell | Normativ zugehörige Builtins je Sprachprofil; keine zufällige Abhängigkeit vom zuletzt verwendeten Modell. |

`ConstraintDefinition` verwendet hierfür konkrete Records `MandatoryConstraint`, `PlausibilityConstraint`, `ExistenceConstraint`, `UniquenessConstraint`, `SetConstraint` und `DomainConstraint`. Gemeinsame Merkmale dürfen in Hilfstypen liegen. Die spätere Validierungsreichweite wird ausdrücklich gespeichert, beispielsweise objektlokal, Basket oder Transfer; die endgültige Ausführungsplanung legt [Verbraucherentwurf](../design/transfer-validation-database.md) fest.

<a id="effektive-mitglieder-und-übersetzungen"></a>
## Effektive Mitglieder und Übersetzungen

`EffectiveMember` enthält das deklarierende Symbol, den effektiven Besitzer, den Überschreibungs-/Vererbungsweg, die fachliche Position und die Herkunftsart. Die Reihenfolge wird durch `EffectiveMemberResolver.resolve(SymbolRef)` aus den Sprach- und Transferregeln ermittelt. Eine alphabetische Sortierung oder eine zufällige Mapiteration ist unzulässig.

Der `inheritancePath` führt vom Besitzer der tatsächlich ausgewählten
Memberdeklaration über jede Zwischenstufe zum effektiven Besitzer, jeweils
einmal. Ein lokales Mitglied hat nur seinen eigenen Besitzer im Pfad; ein
überschriebenes Mitglied beginnt beim Besitzer der überschreibenden
Deklaration. Gegenüber dem abgefragten Besitzer ist ein geerbtes Override
`INHERITED`, während `overridden` dessen Überschreibungsbezug erhält. Ein dort
lokal deklariertes Override ist `OVERRIDDEN`. `AttributeSymbol.overrides`
bewahrt daneben die tatsächliche Basiseigenschaft. Diese Verträge gelten
gleich für Klassen, Strukturen und Assoziationen sowie für beide Sprachen.

Eine Überschreibung darf die durch die fachlichen Regeln definierte Position nicht unbemerkt verändern. Eingebettete Assoziationsrollen werden als solche markiert; sie werden nicht zu gewöhnlichen Attributen umetikettiert. Für eine Diagrammoption „nur eigene Attribute“ wird nach Herkunft gefiltert. Für Transfer-/DB-Pläne wird die vollständige effektive Information verwendet.

`TranslationLink` verbindet Original- und Übersetzungssymbole mit überprüfter Korrespondenz. Ein Transfername wird erst unter einem ausgewählten Transfer-/Sprachprofil bestimmt. Die Entscheidung ist nicht in `SymbolId` kodiert. Konflikte und fehlende Entsprechungen bleiben fachliche Diagnostik; eine Übersetzung ist keine globale Umbenennung aller Symbole im Graphen.

`ModelTranslation.sourceTarget(): Optional<SymbolId>` bindet das unmittelbar
angegebene Originalmodell in der gewählten Version. Eine leere Bindung bleibt
eine diagnostizierte fehlerhafte Analyse. `correspondences(): List<TranslationLink>`
enthält zunächst die eigenen Deklarationen und anschliessend die tatsächlich
benutzten Importkorrespondenzen in deterministischer Deklarationsreihenfolge.
`TranslationLink(original, translated)` erhält beide Identitäten; ein unverändert
importiertes Symbol kann auf sich selbst verweisen. Alle IDs gehören zum selben
Graph, die Liste ist immutable und nullfrei. Im gültigen Schema sind Struktur
und fachliche Inhalte geprüft; bei strukturell gescheiterter Bindung bleibt die
Liste leer. Vorhandene unbenutzte Sprachvarianten bestimmen keine Importwahl.
Transitive Zuordnungen werden konfliktgeprüft zusammengeführt; Modellpaare
begrenzen die Rekursion. Zyklen ohne Originalmodell sind Fehler.
Die Korrespondenzen enthalten auch deklarierte Enum-Knoten.
`EnumElementSymbol.ordinal()` bezeichnet deren Position im effektiven Baum
(`EnumerationValue.order()`), nicht die globale Deklarationsreihenfolge.
Gebundene Quellreferenzen werden pro Scope, Bereich, Verwendung und Ziel
einmal erfasst; ein früher und ein später Bindungszugriff erzeugen keine
doppelten Queryvorkommen.

<a id="compiledschema-als-geprüfte-sicht"></a>
## `CompiledSchema` als geprüfte Sicht

Die Produktionsimplementierung ist `guru.interlis.compiler.internal.DefaultCompiledSchema`, final und unveränderlich. Sie wird ausschliesslich durch `CompilationGate` aus06 erzeugt. Das Interface im Modellmodul vermeidet eine Abhängigkeit von `interlis-model` zurück zum Compiler. Ein Architekturtest verbietet weitere Produktionsimplementierungen innerhalb dieses Repositories.

Dies ist ein API-Vertrag, keine Java-Sicherheitsgrenze gegen Drittcode: Ein externer Aufrufer kann technisch ein Interface selbst implementieren. Die Gültigkeitsgarantie gilt für von `CompilerEngine` ausgegebene Instanzen. Ein Consumer, der fremde Implementierungen akzeptiert, muss seine Eingabegrenze ausdrücklich definieren; er darf deren Herkunft nicht allein aus dem Interfacenamen ableiten.

Die geprüfte Closure enthält keine fachlichen Error-/Pending-Typen und keine unaufgelösten verpflichtenden Bindungen. Rechtmässige Generic-/Deferred-Zustände und externe Funktionssignaturen bleiben zulässig. `SchemaProvenance` enthält Compiler-/Sprachprofil-/Grammatikstand, angeforderte Wurzeln, relevante Quellfingerprints, Resolverbindungen und semantisch wirksame Optionen/Overlays. Uhrzeit und Run-ID sind keine fachlichen Fingerprintbestandteile.

`semanticGraph()` und `queries()` des Schemas sind auf diese geprüfte Closure beschränkt. Sie dürfen unabhängige fehlerhafte Modelle aus dem umfassenderen `AnalysisSnapshot` nicht als eigene geprüfte Inhalte ausgeben. Die Implementierung teilt unveränderliche Symbole, Typen und Ausdrücke und verwendet eine unveränderliche Auswahl-/Indexsicht; ein zweiter tief kopierter Vollgraph ist nicht erforderlich. Referenzvorkommen gehören über den Besitzer ihres ursprünglichen Scopes zur Auswahl. Die Auswahl enthält alle fachlich benötigten Referenzziele und Owner. Ein Handle desselben Snapshots, der ausserhalb dieser Auswahl liegt, ergibt `NotAnalyzed(OUTSIDE_SELECTION)`; ein Handle eines anderen Snapshots bleibt ein `SnapshotMismatchException`-Fall. Dadurch bleiben die Garantien der vollständigen Analyse und der geprüften Schemaauswahl eindeutig.

`models()` enthält die Rootmodelle und deren benötigte Modellabhängigkeiten in deterministischer Reihenfolge. `sourceRevisions()` nennt die dafür verwendeten Quellen; die Texte können weitere unselektierte Nachbarmodelle enthalten, ohne dass diese Bestandteil der Schemasicht werden. Ein Syntaxfehler, der innerhalb einer gemeinsamen Quelldatei nicht zuverlässig von der Root abgegrenzt werden kann, bleibt nach dem konservativen Gatevertrag06 blockierend.

Das Schema besitzt keine JDBC-Verbindungen, Transferobjekte, veränderlichen CRS-Caches, Runtimeparameterwerte oder Rendererinstanzen. Verbraucher erzeugen eigene Kontexte und Pläne. Mehrere Validatoren oder Datenbankplaner dürfen dasselbe Schema gleichzeitig lesen.

<a id="konkrete-abnahmetests"></a>
## Konkrete Abnahmetests

1. **Alias und Genauigkeit:** Ein numerischer Domänentyp mit `9007199254740993.0010` wird über mindestens zwei Aliase verwendet. Deklarierter Typ, effektiver Typ, exakter Wert, Lexem und Präzision bleiben abfragbar. JSON-Projektion und Wasm liefern keinen gerundeten Wert.
2. **Vererbung und Rollen:** Eine Basisklasse, eine Erweiterung, eine überschriebene Eigenschaft und eine Assoziation ergeben eine fest erwartete effektive Reihenfolge. Eigene/geerbte Mitglieder sind unterscheidbar. Eine unzulässige Erweiterung liefert eine konkrete Typrelation und Diagnose.
3. **Teilmodell:** Ein fehlender Domänenname ergibt eine unaufgelöste Bindung mit Ursache; eine unabhängige bekannte Domäne bleibt abfragbar. Ein legal aufgeschobener Generic-Typ ist bekannt und fachlich verschieden vom Fehlerzustand.
4. **Namensidentität:** Zwei gleichnamige Deklarationen bleiben zwei Symbole mit unterschiedlichen IDs. `findQualifiedName` liefert beide, während die eindeutige Bindung eine Mehrdeutigkeit meldet.
5. **Snapshotgrenze:** Ein Handle aus Revision N wird auf N+1 zurückgewiesen. Explizites `rebindSymbol` kann ein erhaltenes Symbol neu adressieren, dessen Eigenschaften anschliessend aus N+1 gelesen werden.
6. **Übersetzung:** Original und Übersetzung besitzen überprüfte Korrespondenz; ein ausgewählter Transfername ändert nicht die kanonische Identität. Eine strukturell nicht passende Übersetzung blockiert die betroffene Closure.
7. **Unveränderlichkeit:** Collections und intern verwendete Builder werden nach Publikation verändert beziehungsweise Zugriffe versucht. Kein veröffentlichter Graph darf sich ändern; verbotene Operationen scheitern sichtbar.
8. **Grafische Sprache:** Ein INTERLIS-GRAPHIC-Element und ein UML-Diagrammexport sind unabhängig repräsentiert. Das Modellfeature verschwindet nicht, nur weil der UML-Renderer keine Signaturregeln zeichnet.
9. **Closure:** Eine gültige Root mit gültigen Imports kann ein Schema ergeben, während ein unabhängiges offenes Modell Fehler hat. Derselbe Fehler in einer importierten Quelle blockiert den Erfolg.
10. **Zyklen:** Alias-/Vererbungszyklen liefern endliche Diagnostik mit Referenzpfad und keinen Stackoverflow. Fachlich erlaubte Referenzzyklen bleiben abbildbar.

Erst die Kombination aus sprachlicher Konformität, Metamodell-Goldens und realen Consumerprojektionen beweist die Qualität dieser Modellschicht. Ein erfolgreicher JSON-Export mit Klassen- und Attributnamen genügt nicht.
