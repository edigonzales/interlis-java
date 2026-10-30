# Unabhängiger Review: L3-1

Lieferabschnitt / Phase / letztes Phasengate?: **L3-1 (AC3-01/02), Phase 3, nicht letztes Phasengate** (L3-2 folgt).
Implementierungsagent: Codex
Prüfagent: OpenCode; tatsächliche Provider-/Modellkonfiguration: OpenCode-CLI-Prozess **v2.0.28** (`~/.opencode/bin/opencode run --standalone --model deepseek/deepseek-flash --agent build`); der aktive OpenCode-Modellkatalog liefert Provider-ID **`deepseek`**, Modell-ID **`deepseek/deepseek-flash`**, Anzeigename `DeepSeek V4.1 Flash 1017`. Es ist kein OpenRouter-Provider in der aktiven Konfiguration beteiligt. Die Identität ist durch das tatsächliche Prozesskommando und den Modellkatalog belegt, nicht durch eine Modellselbstauskunft.
BASE_SHA: `1de1daa82428c5c89d1c721e5aa6567413569a85`
CANDIDATE_SHA (durch git überprüft): `f839311dd0f2e3d3a3925a6edfaea44ddacbc866`
Prüfumgebung / Toolchain: macOS arm64 (Darwin 24.6.0), Temurin **25.0.3+9**, Gradle **9.8.1** (Wrapper), Node **v24.22.0**; separater Worktree `/private/tmp/interlis-java-L3-1-review-f839311`; `git rev-parse HEAD` und `git status --short` vor und nach der Prüfung sauber; keine Änderung am Kandidaten.
Geprüfter Diff `BASE..CANDIDATE`: **39 Dateien, +1399/−615**; Produktionsänderungen in Compiler/Model/Workspace/WASM-Bridge, drei **neue** Testklassen, Spezifikations-/Evidenz-Nachführung. Keine bestehende Testmethode, Builddatei, Normerwartung, Suite-/XFAIL-/Baseline- oder Pin-Datei wurde geändert.

## Kriterienprüfung

| Kriterium-ID | Erfüllt / verletzt / nicht prüfbar | Eigener Test-/Code-/CI-Beleg | Finding-ID |
|---|---|---|---|
| **AC3-01** – `SemanticOverlays` verarbeitet echte unveränderliche Translation-/CRS-/Metaattribut-Daten; tatsächliche Zielbindung, Mehrdeutigkeit/Konflikt, Herkunft und öffentliche Schemawirkung | **erfüllt** | Produktionscode `SemanticOverlays.java`, `OverlayBindings.java`, `GraphBuilder.java` (Metadaten, CRS, Translation), `ImportBinder.java`; `SemanticOverlayContractTest` **12/12 PASS**, `OverlayWorkspaceContractTest` **3/3 PASS**, `OverlayProtocolContractTest` **2/2 PASS**; vier portable Szenarien in JS und WasmGC; eigene Gegenproben **6/6 PASS**. Details unten. | — |
| **AC3-02** – Inhaltsfingerprints, reine Overlaywechsel, Isolation von Alias-/Importzuständen und Fresh/Reuse deterministisch; alte Snapshots nicht mutiert, verspätete Captures nicht aktuell | **erfüllt** | `CompilationFingerprinter.java` (compilation-v2, keine Reuse-SnapshotId, kanonisch sortierte Importe), `CompilationRun.java` (SnapshotId mit Parsemodus/Reuse-Provenienz), `CompilationGate.java` (schema-v2), `SourceInventory.java`/`ResolverCatalog.java` (längenpräfixierte UTF-16-Digests), `DefaultWorkspace.java` (Overlays im ResolutionRequest, Capture-Invalidierung); Fresh/Reuse-, A→B→A-, Altsnapshot- und Protokolltests; eigene Gegenproben **5/5 PASS**. Details unten. | — |

### AC3-01 – Detailnachweise

- **Echte Daten statt Platzhalter:** `SemanticOverlays` enthält typisierte `TranslationOverlay(source,target,original)`, `CrsOverlay(source,target,crs)` und `MetaAttributeOverlay(source,target,attributes)`. `OverlaySource` trägt Kennung, Priorität und optionalen externen `SourceRange`; `Target` trägt kanonischen `QualifiedName`, optionale exakte Modellversion und optionalen `SourceId`-Hinweis.
- **Unveränderlichkeit/Kopien:** `MetaAttributeOverlay` kopiert die Attributmap und die Wertlisten tief, sortiert Schlüssel lexikalisch und behält die Wertreihenfolge; die Overlayliste ist unveränderlich kopiert. Die eigene Gegenprobe zur Schlüsselreihenfolge bestätigt die stabile Identität.
- **Keine IO/CRS-Ausführung im Kern:** Der Compiler bindet nur deklarative Werte; es gibt keine Datei-, Netzwerk- oder Koordinatentransformationsausführung in `SemanticOverlays`/`OverlayBindings`.
- **Zielbindung:** `OverlayBindings.resolve` bindet im tatsächlichen `IndexedDeclarations`-Index; der eigene Namespace-/Aliasweg wird nicht verwendet. Höhere Priorität gewinnt, niedrigere bleibt mit `Origin.OverlayOrigin` erhalten.
- **Mehrdeutigkeit/Konflikt:** Fehlende Ziele, ungeeignete Zielarten und gleichrangige widersprechende Werte erzeugen globales `ILI-SEM-0011` (ERROR/BINDING) mit `overlay`/`target`/`reason`; eigene Gegenprobe bestätigt, dass ein höherer Gewinner einen niedrigrangigen Konflikt **nicht** maskiert. `CompilationGate` bewertet diese Diagnose auch ohne Modelltextbereich.
- **Fehlende/mehrdeutige Originale:** Getrennt davon erzeugen fehlende konfigurierte Originalmodelle `ILI-RES-0005`/`MISSING_INPUT`, mehrdeutige `ILI-RES-0006`/`AMBIGUOUS_INPUT` mit `model` und ggf. `candidates` (ERROR/SOURCE_RESOLUTION, optionaler Bereich). Der Test `missingAndAmbiguousConfiguredOriginalsRemainResolutionRejections` und die eigene Workspace-Gegenprobe (spätere Lieferung des Originals) bestätigen die Trennung und die erneute normale Bindung.
- **Öffentliche Metadatenwirkung:** `GraphBuilder.header` stellt die wirksamen Overlaywerte vor die unveränderten Quellvorkommen; der First-Occurrence-Index liefert den wirksamen Wert, alle Vorkommen behalten ihre Herkunft. `SemanticOverlayContractTest.metadataChangesArePublicAndPreserveBothEffectiveAndSourceOrigins` prüft beide Sprachen, effektiven Wert, Quellwert, `OverlayOrigin`/`DeclaredOrigin` und Unveränderlichkeit. Die eigene Gegenprobe prüft zusätzlich die Prioritätsreihenfolge unabhängig von der Overlaylisten-Reihenfolge.
- **CRS:** `OverlayBindings.validateCrs` akzeptiert CRS nur an `CoordinateType`-Deklarationen; der vorhandene Übersetzungsvergleich in `GraphBuilder` vergleicht die wirksamen CRS-Werte. Der Vertragstest prüft Original-/Übersetzungs-CRS und die Nicht-Koordinaten-Ablehnung; die eigene Gegenprobe prüft zusätzlich **Attribute** (nicht nur Domains) über `CrsOverlay` und über den Schlüssel `CRS` in `MetaAttributeOverlay`.
- **Translation:** `TranslationOverlay` bindet zwei vorhandene Modelle, nimmt Original und dessen Imports in die Prüfclosure auf, ohne den Originalnamensraum zu importieren. `ModelTranslation` erhält `sourceTarget` und `origin`; der strukturelle Übersetzungsprüfer erzeugt die tatsächlichen Korrespondenzen. Widersprechende explizite `TRANSLATION OF`-Deklarationen und Zyklen bleiben Fehler. Die eigene Gegenprobe bestätigt, dass eine Referenz auf `Original.P` ohne Import als `ILI-SEM-0001`/`SOURCE_ERRORS` abgelehnt wird und nicht als fehlende Eingabe durchgeht.
- **Öffentliche Protokollwirkung:** Die additive Query `semanticProperties` serialisiert Metadatenherkunft, `ModelTranslation`-Originalziel und Korrespondenzen aus dem gemeinsamen Graphen; `OverlayProtocolContractTest` prüft echte Werte, Herkunft und Sessionisolation, vier portable Szenarien prüfen zusätzlich Konflikt, Entfernung und alte Snapshots in JS und WasmGC.

### AC3-02 – Detailnachweise

- **CompilationFingerprint:** `compilation-v2` enthält die tatsächlichen Optionen (inklusive Overlayfingerprint), Root-Selektoren, Quellinhalte, kanonisch sortierte Importstellen und den Resolver-Fingerprint, aber **keine** vorherige `SnapshotId`. Der eigene Vergleich frischer und wiederverwendender Analyse bestätigt gleiche fachliche Fingerprints.
- **SnapshotId:** Sie enthält zusätzlich Parsemodus und die konkrete Wiederverwendungsprovenienz; gleiche fachliche Inhalte mit anderer Reuse-Kette können verschiedene Graph-/Symbol-IDs besitzen. Fremde Snapshotreferenzen bleiben abgewiesen (`SnapshotMismatchException` im Vertragstest).
- **SchemaFingerprint:** `schema-v2` hängt von der fachlichen Analyseidentität ab, nicht von vorherigen Snapshot-/Symbol-IDs. Cache-Eviction und Warm-Lauf ändern den Schemafingerprint nicht; der Workspace-Test prüft dies.
- **SourceInventory/ResolverCatalog:** Beide verwenden versionierte, längenpräfixierte UTF-16-Inhaltsdigests; der allgemeine `WorkspaceRevision`-Zähler ist aus dem Inventarfingerprint entfernt und bleibt separat für Stale-Checks. Die eigene Gegenprobe bestätigt: Workspace-Revision 1 vs. 99 bei identischen Quellen ergibt denselben Inventarfingerprint; eine echte Inhaltsänderung ändert ihn.
- **Overlaywechsel/Fresh-Reuse:** `overlayOnlyEditsMatchFreshAnalysisAndDoNotMutatePublishedSnapshots` prüft A→B→A, frische vs. wiederverwendende Analyse, unterschiedliche SnapshotIds, unveränderte alte Snapshots und Overlayentfernung. `anOverlayRoundTripRestoresContentIdentityWithoutReusingOldResults` prüft die Wiederherstellung der fachlichen Identität nach A→B→A.
- **Isolation:** `externalOriginalImportsArePreparedWithoutLeakingTheirNamespace` prüft die Originalimporte und dass der Originalnamensraum nicht als Import des Zielmodells erscheint; die eigene Compiler-Gegenprobe prüft die Ablehnung einer solchen Referenz. `overlayOnlyChangesInvalidateCaptures...` prüft die Capture-Invalidierung bei reiner Konfigurationsänderung und die semantische Neutralität reiner Cache-Änderungen. `OverlayProtocolContractTest` prüft die Sessionisolation und falsche Wire-Formen ohne Sessionverbrauch.
- **Verspätete Captures:** `acceptAnalysis` weist Captures mit veralteter Workspace-Revision als `STALE_NO_COMMIT` ab; die Workspace-Gegenprobe und der Vertragstest decken Konfigurations- und Quelländerungen ab. Alte Snapshots bleiben unverändert abfragbar (portable Szenarien und Workspace-Test).
- **Fingerprint-Gegenprobe:** Die eigene Gegenprobe bestätigt die Unempfindlichkeit gegen Map-Einfügereihenfolge, die Empfindlichkeit gegen externe `SourceRange`-Revisionen und die kollisionssichere Trennung von Resolver-Katalogschlüsseln (`"a=bc"` vs. `"a" → "=bc"`). Die eigene Workspace-Gegenprobe bestätigt den vierparametrigen `ResolutionRequest`-Kompatibilitätskonstruktor mit `SemanticOverlays.empty()`.

## Eigene Gegenprobe

Die neuen Prüfquellen wurden ausschließlich unter dem ignorierten `build/`-Verzeichnis des Review-Worktrees angelegt, nicht im Produktkandidaten:

- `build/review/L3-1/probe-compiler/guru/interlis/compiler/OverlayIndependentProbeTest.java`
- `build/review/L3-1/probe-workspace/guru/interlis/workspace/OverlayWorkspaceProbeTest.java`
- `build/review/L3-1/probe.init.gradle` (fügt die Quellen nur für den Reviewlauf per Init-Skript hinzu)

**Hypothesen und fachliches Risiko:** Die Proben versuchen, die konkreten Behauptungen zu widerlegen: (a) ein höherer Prioritätsgewinner könnte einen gleichrangigen Konflikt verdecken; (b) die wirksame Metadatenreihenfolge könnte von der Overlaylisten-Reihenfolge statt von der Priorität abhängen; (c) eine Translation könnte den Originalnamensraum sichtbar machen; (d) CRS könnte nur auf Domains, nicht auf Attributen geprüft werden; (e) identische gleichrangige Overlays könnten einen zusätzlichen wirksamen Wert erzeugen; (f) Overlayfingerprints könnten von Map-Reihenfolge abhängen oder externe Revisionsherkunft verlieren; (g) der Inventarfingerprint könnte weiter am Workspace-Zähler hängen; (h) der Kompatibilitätskonstruktor könnte Overlays nicht leeren; (i) Resolver-Katalogschlüssel könnten kollidieren; (j) ein später geliefertes konfiguriertes Original könnte nicht erneut gebunden werden.

**Erwartetes Verhalten:** Konflikt → `Rejected`/`SOURCE_ERRORS` mit `ILI-SEM-0011`; Prioritätsreihenfolge → hoher Wert zuerst, unabhängig von der Listenreihenfolge; Originalnamensraum → `ILI-SEM-0001`/`SOURCE_ERRORS`, nicht `MISSING_INPUT`; Attribut-CRS → Erfolg bzw. `ILI-SEM-0011` für Text; identische Overlays → wirksamer Index unverändert; Fingerprint → reihenfolgestabil und revisionsempfindlich; Inventar → unabhängig von der Workspace-Revision, aber inhaltsabhängig; 4-Argumente-Konstruktor → leere Overlays; Katalog → unterschiedliche Digests; spätere Original-Lieferung → Erfolg mit gebundener Translation und Overlay-Origin.

**Kommandos / Exitcodes / beobachteter Output:**

```sh
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
  ./gradlew :interlis-compiler:test \
  --tests 'guru.interlis.compiler.OverlayIndependentProbeTest' \
  -I build/review/L3-1/probe.init.gradle --rerun-tasks
# Exit 0; BUILD SUCCESSFUL; 6 Tests, 0 Failures, 0 Errors, 0 Skips
# XML: build/review/L3-1/probe-results/TEST-guru.interlis.compiler.OverlayIndependentProbeTest.xml

JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
  ./gradlew :interlis-workspace:test \
  --tests 'guru.interlis.workspace.OverlayWorkspaceProbeTest' \
  -I build/review/L3-1/probe.init.gradle --rerun-tasks
# Exit 0; BUILD SUCCESSFUL; 5 Tests, 0 Failures, 0 Errors, 0 Skips
# XML: build/review/L3-1/probe-results/TEST-guru.interlis.workspace.OverlayWorkspaceProbeTest.xml
```

**Einzelresultate:**

| Probe | Beobachtung | Schluss |
|---|---|---|
| `lowerPriorityConflictCannotHideBehindHigherPriorityWinner` | `Rejected`, Reasons `{SOURCE_ERRORS}`, Diagnose `ILI-SEM-0011` | Konflikt wird nicht durch höheren Gewinner maskiert; AC3-01 bestätigt |
| `effectiveMetadataOrderDependsOnPriorityNotOnOverlayListOrder` | beide Listenreihenfolgen: Werte `["winner","loser"]`, erste Herkunft `OverlayOrigin` mit Priorität 7 | Priorität, nicht Listenreihenfolge, bestimmt die öffentliche Wirkung |
| `translationDependencyDoesNotMakeOriginalNamespaceVisible` | `Rejected` mit `ILI-SEM-0001`, **kein** `MISSING_INPUT` | Kein Originalnamensraumleck; AC3-01/02-Isolation bestätigt |
| `crsBindsCoordinateAttributesThroughBothOverlayForms` | `CrsOverlay` und `MetaAttributeOverlay`-Schlüssel `CRS` auf Attribut erfolgreich; Textattribut → `ILI-SEM-0011` | CRS-Prüfung deckt echte Attribute ab, nicht nur Domains |
| `identicalSamePriorityValuesRemainIdempotentForTheEffectiveIndex` | wirksamer Index `"x"` unverändert; öffentliche Vorkommensliste enthält **2** identische Vorkommen (`PROBE identical-overlay occurrences=2`) | wirksamer Wert idempotent; Vorkommensliste behält beide Provenienzen. Nicht blockierende Präzisierungsbeobachtung, siehe OBS-1 |
| `overlayFingerprintIsOrderStableAndRevisionSensitive` | gleiche Map-Inhalte in verschiedener Einfügereihenfolge → gleicher Fingerprint; `SourceRange`-Revision 1 vs. 2 → verschiedener Fingerprint | Inhalts-/Herkunftsbindung des Fingerprints bestätigt |
| `inventoryFingerprintDoesNotContainTheWorkspaceCounter` | Workspace-Revision 1 vs. 99, gleiche Quelle/Katalog/Syntax → gleicher Fingerprint; Revisionen verschieden | AC3-02-Trennung bestätigt |
| `inventoryFingerprintStillTracksContent` | echte Inhaltsänderung → verschiedener Fingerprint | Kein Schein-PASS durch zu schwache Identität |
| `fourArgumentResolutionRequestStillMeansNoOverlays` | `ResolutionRequest` mit vier Argumenten → `SemanticOverlays.empty()` | Kompatibilitätsvertrag bestätigt |
| `resolverCatalogFingerprintIsSeparatorSafe` | `{"a=bc":""}` vs. `{"a":"=bc"}` → verschiedene Fingerprints | längenpräfixierte Katalogidentität bestätigt |
| `configuredOriginalCanBeDeliveredAfterAnIncompleteFirstCompile` | erster Compile `Rejected`/`MISSING_INPUT`; nach Hinzufügen des Originals `Succeeded` mit `ModelTranslation.origin == OverlayOrigin` und `sourceTarget` | spätere Lieferung bindet erneut; AC3-01/AC3-02 bestätigt |

**Ergebnis und Schlussfolgerung:** Keine der Gegenproben widerlegt die AC3-01/02-Behauptungen. Der erste Workspace-Probelauf kompilierte wegen eines Typparameters **im Review-Probetest** nicht; nach Korrektur des Probetests liefen 5/5 Tests grün. Das war kein Produktfehler und keine Produktänderung. Die Beobachtung OBS-1 betrifft nur die Anzahl identischer Vorkommen bei redundant doppelt konfiguriertem Overlay, nicht den wirksamen Index.

## Pflichtprüfungen

**Ausgeführtes Kommando (frisch am Kandidaten, nach den Reviewproben, ohne konkurrierenden CLI-/Distributionsneubau):**

```sh
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
tools/verification/without-python.cjs ./gradlew checkPortable g2EvidenceTest \
conformanceCheck sdkProcessCheck pythonFreeCheck --continue --rerun-tasks \
-PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node
```

- **Exitcode 0**, `BUILD SUCCESSFUL in 2m 36s`, **58 von 58 Tasks ausgeführt** (`58 actionable tasks: 58 executed`), keine Pflichtprüfung als fehlend übersprungen. Der `without-python.cjs`-Wrapper ergänzt intern `--no-daemon` und den privaten PATH; der Lauf ist der finale, vollständig frische Lauf.
- **JUnit-XML:** 180 XML-Dateien, **987 Tests**, **0 Failures**, **0 Errors**, **0 Skips**; Suite-/Klassen-/Methoden-XML mit Kandidatenpfad; Zeitstempel **2026-10-30T11:03:27Z bis 11:03:56Z** (Lauf 11:03:17Z–11:05:53Z). Kopie des finalen XML-Stands: `build/review/L3-1/final-xml/` (180 Dateien, identische Summe 987).
- **Reguläre JVM: 820** – Compiler **706**, CLI 33, LSP 16, Model 16, Projection 12, Repository 19, WASM-Bridge 5, Workspace 13. Zusätzlich **137 Evidenz** (25+56+31+25 in vier Suites), **15 Assertion** (11 Suites) und **15 Host** (5 Suites); jeweils 0 Failures/Errors/Skips.
- **Neue L3-1-Vertragstests im finalen Lauf:** `SemanticOverlayContractTest` **12/12**, `OverlayWorkspaceContractTest` **3/3**, `OverlayProtocolContractTest` **2/2**; alle 0/0/0. Die Probe-XMLs sind separat unter `build/review/L3-1/probe-results/` abgelegt und nicht Teil der 987.
- **Portable:** TeaVM-JS **3491 PASS** (`interlis-wasm-teavm/build/js-contract-result.json`, `status=passed`, Node v24.22.0) und TeaVM-WasmGC/Node **3491 PASS** (`wasm-contract-result.json`, `status=passed`, Node v24.22.0); vier neue gemeinsame Overlay-Szenarien sind in beiden Backends enthalten.
- **SDK:** `sdkProcessCheck` **9 Kommandos PASS** (`build/reports/sdk/sdk-process-comparison.json`, `status=passed`; CLI-Artefaktpfade und Hashes auf den Kandidatenworktree).
- **Pythonfreiheit:** `pythonFreeCheck` **1220 Inputs PASS** (`build/reports/python-free/source-check.json`, `status=passed`). `build/reports/python-free/environment.json`: `gitHead=f839311dd0f2e3d3a3925a6edfaea44ddacbc866`, `dirty=""`, `exitCode=0`; private PATH; `python`, `python3`, `pip`, `pip3`, `pypy`, `uv` → **ENOENT**; absolute `/usr/bin/python3`, `/usr/bin/pip3` → **EPERM**. Kein Python/pip/uv aufgerufen.
- **T2-Conformance:** Suite `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`, Archiv-SHA-256 `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe`; Filter `^(ili23|ili24)\.`, **610** Suite-Fälle, **593** ausgewählt, 17 ausgeschlossen (nur 1.0). Roh (Referenz): **577 PASS / 15 FAIL / 1 XFAIL**; Projektbewertung: **577 PASS / 16 XFAIL / 0 FAIL / 0 ERROR / 0 XPASS**; `overallResult=PASS`, Roh-Exitcode 1 ist der erwartete Referenzlauf. JUnit-Rohreport `tests=593 failures=15 errors=0 skipped=1` (der eine Skip ist der gepinnte Referenz-XFAIL, kein übersprungener Pflichttest). **21/21** strenge normative Assessments `result=PASS`.
- **Frische/Kandidatenbindung:** `python-free/environment.json` bindet den Lauf an `gitHead=f839311...` und `dirty=""`; Conformance-/SDK-Reports nennen den Kandidaten-CLI-Pfad; alle XML-Zeitstempel liegen nach dem Laufstart 11:03:17Z. Keine alten Reports wurden übernommen. `git rev-parse HEAD` war vor und nach der Prüfung `f839311...`; `git status --short` blieb leer. Kopien der Rohbelege (finale XML-Suite, Gate-Logs, Probe-XML/-Quellen, Parser) liegen unter `/Users/stefan/sources/interlis-java/build/review/L3-1/evidence/`; der Bericht selbst unter `/Users/stefan/sources/interlis-java/build/review/L3-1/review.md`.
- **Nicht ausgeführt / nicht fällig:** volle Browsermatrix, echtes Safari und Web Image sind laut Übergabe/Plan erst beim **L3-2-Phasengate** fällig; sie wurden hier nicht neu beansprucht. P3/P4/P5, T4-/Corpus-/Benchmark-/`releaseCheck`-Pflichten wurden nicht erfunden. L3-2 wurde nicht begonnen. GlobalD2 bleibt `PENDING_USER_REVALIDATION`.
- **Keine Pflichtskips:** Die JUnit-Tests haben 0 Skips; der einzelne Referenz-XFAIL des externen Runners ist eine gepinnte Erwartung, kein Projekt-Skip. Der im Buildlog sichtbare `:interlis-wasm-teavm:disasmWasmGC SKIPPED` ist ein nicht-testender Build-Task, kein Test-Skip.
- **Kumulative Phasenregression:** Nicht fällig. L3-1 ist nicht das letzte Phasengate; AC3-03 bis AC3-06 und die vollständige T1/T2/T3-Phasenregression bleiben L3-2 vorbehalten.

## Norm-/Suite-/XFAIL-/Baseline-/Quellpin-Unverändertheit

- `git diff --name-only 1de1daa..f839311` enthält **keine** Datei unter `testdata/conformance`, `docs/evidence/conformance/baseline.json`, keine Suite-/Archiv-Pins und keine XFAIL-/Erwartungsdatei.
- Es wurden **keine bestehenden Testmethoden** geändert; die drei neuen Testklassen sind additiv. Die 700 Compiler-`@Test`-Methoden sind mechanisch nachgeführt; die 15 `PredefinedOrigin`-Produzenten sind unverändert vorhanden.
- `testdata/g2/predefined-catalogue.json` und `docs/evidence/stabilization/g2-source-inventory.json` enthalten nur mechanische Quellenanker-/Zähleränderungen (u. a. 700 Compiler-Testmethoden), keine fachliche Erwartung.
- Suite-Commit, Archiv-SHA-256, Filter, 610/593/17-Fallmenge und die 21 strengen Assessments stimmen mit der Übergabe überein. Keine XPASS, keine Baseline-Aufweichung, keine neue Normausnahme.
- `CANDIDATE_ONLY` bleibt ausdrücklich kein pauschaler G2-Nachweis.

## Findings

**Keine BLOCKER- oder MAJOR-Findings.** AC3-01 und AC3-02 sind erfüllt.

**OBS-1 (nicht blockierend, Präzisierung):** Bei zweimal identisch konfiguriertem, gleichrangigem Metadaten-Overlay enthält die öffentliche Vorkommensliste zwei identische Einträge (Gegenprobe: `PROBE identical-overlay occurrences=2`), während der wirksame First-Occurrence-Index unverändert bleibt. Kapitel 06 formuliert „Gleichrangige identische Werte sind idempotent“. Die effektive Wirkung ist idempotent; die Vorkommensliste behält beide Provenienzen. Empfehlung: entweder die Formulierung auf „wirksamer Wert/Index idempotent“ präzisieren oder identische gleichrangige Vorkommen deduplizieren, sofern die Provenienzerhaltung dies zulässt. Kein AC3-01/02-Muss-Finding, da Konflikt-, Präzedenz- und Herkunftsverträge erfüllt sind.

**OBS-2 (nicht blockierend, Dokumentation):** Das `@param`-Javadoc von `MetaAttributeSet` spricht weiterhin von „occurrences in source order“, obwohl Overlay-Vorkommen jetzt vor den Quellvorkommen stehen; die Klassenbeschreibung ist korrekt. Reine Dokumentationspräzisierung, keine Verhaltensabweichung.

**Nicht verwendete Behauptungen:** Die im Implementierungsbericht genannten Red-Logs (`overlays-red.log`, `workspace-overlays.log`, `overlay-roundtrip-red.log`) liegen nur im Build-Verzeichnis des Implementierers und sind nicht Teil des unveränderlichen Kandidaten; sie wurden nicht als Nachweis übernommen. Maßgeblich sind der frische Kandidatenlauf und die eigenen Gegenproben.

## Urteil

**PASS** für **AC3-01** und **AC3-02**.

L3-1 ist **nicht** das letzte Phasengate; eine kumulative Phase-3-Abnahme (AC3-03 bis AC3-06) ist hier nicht fällig und wurde nicht behauptet. Der nächste fachliche Schritt ist L3-2 (Modelldiscovery/Closure, Phasengate) gemäß Auftrag; L3-2 wurde nicht begonnen. Kein Commit, Push, Release, keine globale G2-/P3-/P4-/P5-Freigabe. GlobalD2 bleibt `PENDING_USER_REVALIDATION`.
