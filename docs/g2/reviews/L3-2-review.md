# Unabhängiger Review: L3-2

Lieferabschnitt / Phase / letztes Phasengate?: **L3-2, Phase 3, ja – letztes Phasengate**
Implementierungsagent: Codex
Prüfagent: OpenCode; tatsächliche Provider-/Modellkonfiguration: **OpenCode CLI v2.0.28**, Prozessaufruf `opencode run --standalone --model deepseek/deepseek-flash --agent build --format json --auto`, Provider `deepseek` direkt (`auth.json`: `deepseek.type=api`), **kein OpenRouter** (OpenRouter ist im Authstore vorhanden, wird von diesem Prozess aber nicht verwendet). Keine Geheimnisse gelesen/ausgegeben.
BASE_SHA: `c935b01ab96a201c613aea1ba6b845864d0f5fda`
PHASE_BASE_SHA: `1de1daa82428c5c89d1c721e5aa6567413569a85`
CANDIDATE_SHA (durch git überprüft): `1387ac887239ecc78ed42e688a81a0d00dfcba84`
Prüfumgebung / Toolchain: macOS arm64 (Monterrico), Java `25.0.3+9-LTS` (Temurin), Gradle `9.8.1`, Node `v24.22.0`, GraalVM `25.0.3` (Web Image), Binaryen `version_133`. Eigener sauberer Worktree `/tmp/interlis-java-L3-2-review-1387ac8` auf detached `1387ac8`; `git status --porcelain` vor und nach der Prüfung leer. Produkt-/Test-/Build-/Norm-/Suite-/Erwartungs-/Pindateien wurden nicht verändert; alle eigenen Prüfquellen liegen ausschliesslich unter `build/review/L3-2/` (ignoriert). Kein Commit, Push, Release, Phase 4 oder globale G2-Freigabe.

**Faktische redaktionelle Abschlusskontrolle (30.10.2026):** Die Detailzählung `RepositoryClientTest` wurde nach dem rohen XML von irrtümlich „6/6“ auf **3/3** korrigiert; die Transportprovenienz des ursprünglichen OpenCode-Prozesses wurde ergänzt. Es handelt sich nicht um einen neuen Produktreview: Kriterien, Belege, Prüfergebnisse und Urteil bleiben unverändert.

## Kriterienprüfung

| Kriterium-ID | Erfüllt / verletzt / nicht prüfbar | Eigener Test-/Code-/CI-Beleg | Finding-ID |
|---|---|---|---|
| **AC3-01** – echte unveränderliche Translation-/CRS-/Metadatenoverlays; tatsächliche Zielbindung, Mehrdeutigkeit/Konflikt, Herkunft und öffentliche Schemawirkung; kumulativ L3-1 am aktuellen SHA | **erfüllt** | `SemanticOverlays.java`, `OverlayBindings.java`, `GraphBuilder.java`, `ModelTranslation/Origin/MetaAttributeSet`; `SemanticOverlayContractTest` **12/12 PASS**, `OverlayWorkspaceContractTest` **3/3 PASS**, `OverlayProtocolContractTest` **2/2 PASS**, portable Overlay-Szenarien **PASS**; eigene idempotente Overlay-Probe **PASS**. Details unten. | — |
| **AC3-02** – Inhaltsfingerprints, Overlaywechsel, Alias-/Importisolation, Fresh/Reuse, alte Snapshots und verspätete Captures; Wechselwirkung mit L3-2 | **erfüllt** | `CompilationFingerprinter` (compilation-v3, feldweise), `CompilationRequest.fingerprint`, `SourceInventory` (inventory-v2), `ResolverCatalog` (resolver-catalog-v2), `ResolutionPolicy` (resolution-policy-v2), `DefaultWorkspace` (workspace-capture-v2), `acceptAnalysis`; `TranslationSelectionContractTest` **4/4 PASS**, `ResolutionBoundaryContractTest` **7/7 PASS**, `OverlayWorkspaceContractTest` **3/3 PASS**, `SemanticOverlayContractTest` **12/12 PASS**, portable Alt-Snapshot-/Fresh-Reuse-Szenarien **PASS**. | — |
| **AC3-03** – wirkliche Indizes, Quellauflösung, transitive Imports und Originale über lokale Pfade/file/HTTP; Compilerkern und SourceProvider ohne Host-IO | **erfüllt** | `RepositoryClient`/`RepositoryIndexReader` (echte `ilimodels.xml`), `FileSourceLoader` (Pfad + `file:`), `RoutingSourceLoader` (Datei/HTTP getrennt), `RepositoryClosureService` (echter Parser + reiner Resolver), transitive Imports und Originale; `RepositoryClosureContractTest` **15/15 PASS** (lokale Pfade, `file:`, echter lokaler HTTP-Server, Originale), `RepositoryIndexReaderTest` **6/6 PASS**, `PinnedRealClosureTest` **2/2 PASS**; Modulgrenzen und `ArchitectureBoundaryTest` **PASS**. | — |
| **AC3-04** – genaue Versionen/Mehrdeutigkeit, Offline/Pins/Locks, Budget/Abbruch und Änderungen während/nach Auflösung mit negativen Fällen; keine stille Latest-Auswahl, keine überschriebenen Editorquellen | **erfüllt** | `ResolutionPolicy` (exakte Selections, Prioritäten, Ambiguität), `RepositoryClosureService` (Pins, Lock, Offline, CHANGED_SOURCE, atomarer Batch), `ResolutionLock`/`ResolutionLockCodec`, `DirectoryContentStore`, `PreparedRepositoryUpdate`, `RepositoryCatalog.revision`; `RepositoryClosureContractTest` **15/15 PASS**, `ResolutionLockContractTest` **2/2 PASS**, `RepositoryCliContractTest` **4/4 PASS**, `ResolutionBoundaryContractTest` **7/7 PASS**, portable Policy-Szenarien **PASS**; eigene Raw-Version-/Pin-Probe **PASS**. | — |
| **AC3-05** – mindestens echte gepinnte 2.3-/2.4-Closures mit öffentlichen Modell-/Typ-/Member-/Ziel-/Reihenfolge-/Herkunftsassertions; nicht nur Exit 0 | **erfüllt** | `PinnedRealClosureTest` **2/2 PASS** mit unveränderten offiziellen 2.3-Roads- und 2.4-CHBase-Originalen und vollständigen Originalindizes; Original-SHA-256 vor Auflösung, öffentliche Import-/Basistyp-/Member-/Enum-/Constraint-/Herkunftsassertions, Fresh/Reuse. Eigene SHA-256-Kontrolle der Fixtures stimmt mit `pins.json` überein. | — |
| **AC3-06** – vollständige frische T1/T2/T3-Matrix und kumulative erneute Prüfung AC3-01–05 am selben Produkt-SHA; Urteil entscheidet über Phase 3 | **erfüllt** | Eigener vollständiger Pflichtlauf am Kandidaten: **Exit 0 / 4m19s / 71 von 71 Tasks ausgeführt**, **1025 JUnit-Fälle** mit **0 Failures/Errors/Skips**, T2 **593/610** (roh 577/15/1; Projekt 577/16/0; **21/21** Assessments), T3 **3499** je JS/WasmGC/Chromium/Firefox/WebKit/echtem Safari/Web Image, SDK 9 + Paket, LSP-Distribution/Prozesstest, Pythonfrei 1250 Inputs. | — |

## Detailnachweise je Kriterium

### AC3-01 – Overlaysemantik (kumulativ L3-1)

- **Unveränderlichkeit/Kopien:** `SemanticOverlays` kopiert die Overlayliste; `MetaAttributeOverlay` kopiert Schlüssel/Wertlisten tief, sortiert Schlüssel lexikalisch und behält die Wertreihenfolge; `OverlaySource`, `Target` und alle Overlay-Records sind nullfrei. `OverlayBindings` arbeitet ausschliesslich lauf-lokal, ohne globale Mutation.
- **Zielbindung/Mehrdeutigkeit/Konflikt:** `OverlayBindings.resolve` bindet über kanonischen `QualifiedName` plus exakte Modellversion/SourceId. Fehlende Ziele, ungeeignete Zielarten und gleichrangige widersprüchliche Werte ergeben `ILI-SEM-0011` (ERROR/BINDING) und verhindern das gemeinsame Gate. Fehlende/mehrdeutige konfigurierte Originale bleiben getrennt `ILI-RES-0005`/`ILI-RES-0006` und `MISSING_INPUT`/`AMBIGUOUS_INPUT`, ohne erfundene Modellposition.
- **Öffentliche Wirkung/Herkunft:** `GraphBuilder` stellt wirksame externe Metadaten vor die unveränderten Quellvorkommen; der First-Occurrence-Index liefert den wirksamen Wert. `Origin.OverlayOrigin(id,priority,range)` bleibt auch für verdrängte Vorkommen erhalten. CRS wird nur an `CoordinateType`-Zielen akzeptiert, auch über den Metaoverlay-Schlüssel `CRS`; eine Koordinatentransformation findet nicht statt.
- **Translation:** `TranslationOverlay` nimmt Original und dessen Imports in die Prüfclosure auf, ohne dessen Namensraum zu importieren. `ModelTranslation.sourceTarget`/`origin` und die Korrespondenzen stammen aus dem gemeinsamen Graphen; widersprechende `TRANSLATION OF`-Deklarationen und Zyklen bleiben Fehler.
- **Frische Belege:** `SemanticOverlayContractTest` 12/12 (Metadaten, Translation, CRS, Missing/Ambiguous/Conflict, Deep Immutability, Overlay-only Fresh/Reuse, exakte Version/Source, Cancellation/Budget, alte Snapshots), `OverlayWorkspaceContractTest` 3/3 (externe Originalimporte ohne Namespaceleck, Capture-Invalidierung, A→B→A), `OverlayProtocolContractTest` 2/2; portable L3-1-Overlay-Szenarien in JS und WasmGC/Node **PASS**.
- **Eigene Probe:** `OverlayIdempotenceProbe` bestätigt am Kandidaten: zwei gleichrangige identische Overlaywerte ergeben den wirksamen Wert `x`, drei öffentliche Vorkommen (2× Overlay-Origin, 1× Declared-Origin); der wirksame First-Occurrence-Index ist idempotent, die getrennten Vorkommen/Herkünfte bleiben erhalten. Das entspricht der präzisierten L3-1-Formulierung und ist keine Kriterienaufweichung.

### AC3-02 – Inhaltsidentität, Isolation, Fresh/Reuse (kumulativ L3-1, Wechselwirkung L3-2)

- **Feldweise Compileridentität:** `CompilationFingerprinter` serialisiert `compilation-v3` aus Compiler-/Grammatikversion, Optionen, Roots, Quellen (ID/Revision/ContentFingerprint), Importbindungen (Range/Site/Resolution incl. Kandidaten und Problemen), `TranslationBinding`s, ResolutionDiagnostics und dem behaupteten Resolver-Fingerprint. Jede Komponente wird längenpräfixiert und feldweise geschrieben; Record-/Listen-`toString()` und Integerhashes sind keine Identität. `TranslationSelectionContractTest` prüft die reale `ModelLocation`-/`SourceHint`-/Diagnostic-Kollision (gleiche `toString()`-Darstellung, verschiedene Daten) und die Trennung der Argumentlisten.
- **Policy-/Inventar-/Capture-Identität:** `ResolutionPolicy.fingerprint` (`resolution-policy-v2`), `SourceInventory.fingerprint` (`inventory-v2`, Inhalte/Revisionen/Katalog, ohne Workspace-Zähler), `ResolverCatalog.fingerprint` (`resolver-catalog-v2`), `DefaultWorkspace.inputFingerprint` (`workspace-capture-v2`, Inventar + Optionen + Resolveridentität). `acceptAnalysis` vergleicht `capture.request().fingerprint()` mit der tatsächlichen Analyse-Fingerprint und prüft zusätzlich Quellenrevisionen/-inhalte und Options-Fingerprint. Snapshot-/Schemaidentitäten bleiben getrennt (Snapshot enthält Parsemodus/Reuse; Schema nutzt die fachliche CompilationFingerprint).
- **Isolation/alte Snapshots/verspätete Captures:** `OverlayWorkspaceContractTest.overlayOnlyChangesInvalidateCaptures...` und `anOverlayRoundTripRestoresContentIdentity...` belegen A→B→A, Cache-Neutralität und unveränderte alte Snapshots. `ResolutionBoundaryContractTest.analysisWithOtherRootSelectionCannotBeAcceptedIntoACapture` belegt, dass eine Analyse mit anderer Rootauswahl nicht in ein Capture übernommen wird. `selectionChangesAlterCaptureIdentity...` belegt reine Policy-Änderungen ohne Quell-/Overlay-Edit. Portable Szenarien prüfen alte Snapshots auch nach Policy-/Overlay-/Originalwechseln.
- **L3-2-Wechselwirkung:** `RepositoryClosureService` arbeitet mit einem privaten Staging-Workspace; `WorkspaceCapture.reuse()` wird nur über `acceptAnalysis` veröffentlicht. `PreparedRepositoryUpdate` trägt die erwartete Workspace-Revision und den vollständigen Konfigurationsfingerprint; `applyBatch` prüft beides vor jeder Mutation. `cacheOnlyConfigurationEditsAlsoPreventStaleBatchApplication` deckt die reine Cache-Konfigurationsänderung ab.

### AC3-03 – Indizes, Quellen, transitive Imports und Originale

- **Echte Indizes:** `RepositoryIndexReader` liest standardkonforme `ModelMetadata` einschliesslich `Name`, `SchemaLanguage`, `File`, `Version`, `precursorVersion`, `md5`; `RepositoryClient.catalog` löst relative Dateien gegen die Repositorybasis auf. Versionen bleiben rohe Strings (auch signifikante Leerzeichen). `RepositoryIndexReaderTest` 6/6 prüft reale Fixtures und dekodierte Traversal-/URI-Ausbrüche; `RepositoryClientTest` **3/3 PASS** prüft u. a. die Katalogrevision über Sprachdigests, Precursor, MD5 und FetchPolicy.
- **Lokale Pfade/file/HTTP:** `FileSourceLoader` verarbeitet Pfade und `file:`-URIs, begrenzt den tatsächlichen Streamread und meldet NOT_FOUND/ACCESS_DENIED/IO_FAILURE/INVALID_ENCODING/TOO_LARGE. `RoutingSourceLoader` trennt Datei- und HTTP(S)-Zugriff; `HttpSourceLoader` besitzt Timeout-, Redirect-, Grössen- und Abbruchgrenzen. `RepositoryClosureContractTest.preparesTransitiveLocalAndFileUriSources...` nutzt lokale Pfade und `file:`, `resolvesViaRealHttpAndReplaysLockOffline...` einen echten lokalen HTTP-Server (6 tatsächliche Lesezugriffe, danach Offline ohne Provideraufruf).
- **Transitive Imports/Originale:** Der `RepositoryClosureService` wiederholt den Staging-Capture bis zum Fixpunkt; ein Dateidownload kann mehrere Pending-Modelle bedienen. `externalOverlayOriginalAndItsImportsAreDownloadedByTheSameHostLoop` lädt Original plus dessen Import über denselben Hostloop; `namedMissingRootIsDiscoveredAndCyclicImportsTerminate...` löst einen benannten fehlenden Root und beendet einen Importzyklus ohne Schein-Schema.
- **Keine Host-IO im Kern:** `interlis-compiler` importiert weder `java.io`/`java.nio.file` noch `java.net.http`; `java.net.URI` wird nur zur Namensraumprüfung verwendet. `interlis-workspace` enthält keine Host-IO-Imports. Die Modulabhängigkeiten sind `compiler → model`, `workspace → compiler/model`, `repository → workspace/model`; `ArchitectureBoundaryTest` PASS.

### AC3-04 – Exakte Versionen, Pins/Locks/Offline, Abbruch, Änderungsfenster

- **Exakte Version/keine Latest-Auswahl:** `ResolutionPolicy.select` filtert exakte Modellversion und SourceHint, schneidet konfigurierte Einschränkungen und wählt danach die niedrigste Prioritätsgruppe; gleichrangige unterschiedliche Definitionen bleiben mehrdeutig. `RepositoryClosureService` vergleicht Indexversion, Modellkopfversion und optional veröffentlichtes MD5 exakt und liefert bei keiner exakten Übereinstimmung `VERSION_MISMATCH` bzw. `AMBIGUOUS`, nie eine „neueste“ Version. `versionsNeverUseLatestAndExactPinsBindTheRealSelectedDefinition` PASS.
- **Mehrere Versionen/Quellen eines Namens:** `ResolutionLock.models` ist `Map<String,List<ModelPin>>`, hält mehrere Versionen/Quellen getrennt und sortiert kanonisch nach roher Version/URI (keine Latest-Regel). `twoExplicitOriginalVersionsRetainTheirIdentitiesAndBothReplayOffline` (2.3 und 2.4) und `twoExplicitOriginalSourcesWithOneVersionRemainSeparatePerTranslation` PASS; die kombinierte Closure bleibt erwartungsgemäss `SOURCE_ERRORS`/`MODEL_NAME_UNIQUE` (keine Normänderung).
- **Offline/Pins/Locks/Cache:** `VerifiedLoader` nutzt offline ausschliesslich digestverifizierte Cacheinhalte, ruft den Provider nicht auf und meldet ohne Treffer `OFFLINE`; vollständige eingefrorene Closures benötigen auch offline keinen Index/Provider (`completeFrozenClosureNeedsNoIndexOrProvider...`). `ResolutionLockCodec` ist deterministisches UTF-8-XML v1, verbietet DTD/Entities/Unbekanntes/doppelte Identitäten und erlaubt mehrere Versionen/Quellen eines Namens; `ResolutionLockContractTest` 2/2. `DirectoryContentStore` behandelt korrupte/übergrosse/unlesbare Einträge als Misses.
- **Abbruch/Budget:** Der reine Resolver zählt Header-/Kandidaten-/Overlayarbeit und liefert bei Überschreitung `ResolutionCancelled(RESOURCE_LIMIT)`; `resolutionBudgetControlsHeaderAndCandidateScans` PASS. Der Host prüft Abbruch zwischen Fetch-Schritten und liefert `Cancelled` ohne Teilbatch; `editingDuringFetchReturnsStaleAndCancellationReturnsNoBatch` PASS.
- **Änderungen während/nach IO:** Online werden tatsächlich verwendete Quellen und Indizes am Ende erneut gelesen; `sourceChangesDuringResolutionAndStaleBatchesNeverOverwriteEditors` belegt `CHANGED_SOURCE` bei Quellenänderung und atomare Ablehnung eines veralteten Batches. `cacheOnlyConfigurationEditsAlsoPreventStaleBatchApplication` und der Fremd-Workspace-Test belegen, dass weder Cache- noch fremde Workspace-Änderungen überschrieben werden. Editorquellen werden nicht durch Downloads ersetzt (`workspaceOverridesKeepIdentityAndAreNotReplacedByDownloads`).
- **CLI:** `RepositoryCliContractTest` 4/4 prüft Online→Lock→Offline über getrennte CLI-Instanzen nach Löschen der Ursprungsdateien, Cachekorruption → `OPERATIONAL_FAILURE`/OFFLINE, Ambiguität/Pins → `INPUT_UNRESOLVED`, Ausgabekollisionen → `USAGE_ERROR`, Lock erst nach erfolgreicher Kompilation. Die bestehenden Optionen bleiben erhalten; kein Legacyalias-Qualifikationsclaim.
- **Eigene Probe (RawVersionProbe):** siehe unten.

### AC3-05 – Echte gepinnte 2.3-/2.4-Closures

- **2.3:** `RoadsExdm2ien` 2017-04-05 → `RoadsExdm2ben` 2005-06-16 aus zwei unveränderten offiziellen Dateien und dem vollständigen offiziellen `ilimodels.xml`. `PinnedRealClosureTest` prüft Original-SHA-256 vor der Auflösung, Importziel, Basistyp `Roads.StreetAxis`, effektive Member `Geometry`/`Precision`, `MANDATORY`, Enum-Reihenfolge `precise`/`unprecise`, Source-Range-Herkunft und `LanguageVersion.ILI23`. Zusätzlich Fresh- und Reuse-Kompilation mit identischem Schemafingerprint.
- **2.4:** `LocalisationCH_V2` → `Localisation_V2` → `InternationalCodes_V2` (2021-01-06) aus drei Modellen derselben unveränderten offiziellen CHBase-Datei und dem vollständigen offiziellen (verlustfrei gzip-komprimierten) geo.admin.ch-Index. Geprüft werden Importkette, Basistyp, Constraint-Anzahl, effektive Member `Language`/`Text`, Texttyp, Enum-Anfang `de,fr,it,rm,en` und Gesamtzahl 139, Source-Range-Herkunft, `LanguageVersion.ILI24`, Fresh/Reuse sowie die ausdrückliche Nichtzertifizierung der beiden zusätzlichen Dictionaries-Modelle derselben Datei.
- **Unabhängige Bytekontrolle:** `shasum -a 256` der Fixtures ergab exakt `c12ec563…84c97` (RoadsExdm2ben), `6a18f8a3…2cdc` (RoadsExdm2ien), `1b592fce…e0857` (CHBase), `2defccf0…7853` (2.3-Index) sowie dekomprimiert `c2ce3bfc…af5` (geo.admin.ch-Index); der Gzip-Artefakthash ist `d6aa1753…59e`. Alle stimmen mit `pins.json` überein. `.gitattributes` setzt `*.ili`, `*.xml`, `*.gz` auf `-text`, sodass die absichtlichen CRLF-/Originalbytes erhalten bleiben.

### AC3-06 – Vollständige frische T1/T2/T3-Matrix und kumulativer Phasenabschluss

- **T1:** Eigener vollständiger `check`-Lauf über alle Module: 1025 JUnit-Fälle, 0 Failures, 0 Errors, 0 Skips. Aufschlüsselung unten.
- **T2:** Gepinnter externer `InterlisConformance.java` mit Suite-Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`, Archiv-SHA-256 `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe`, Filter `^(ili23|ili24)\.`, **610** Suite-Fälle, **593** selektiert, **17** INTERLIS-1.0 ausgeschlossen; roh **577 PASS / 15 FAIL / 1 XFAIL**, Projekt **577 PASS / 16 XFAIL / 0 FAIL/ERROR/XPASS**, `overallResult=PASS`, Runner-Rohexit **1** (erwarteter Referenzlauf), **21/21** strenge Assessments `result=PASS`.
- **T3:** TeaVM-JavaScript/Node **3499 PASS**, TeaVM-WasmGC/Node **3499 PASS**, TeaVM-Browser Chromium **156.0.8078.4**, Firefox **157.0**, WebKit **27.2** je **3499 PASS**, echtes **Safari 27.0 (20625.1.29.18.28)** via `safaridriver` **3499 PASS**, frischer Web-Image-Build mit GraalVM/Binaryen, Web-Image/Node **3499 PASS** und Web-Image/Chromium/Firefox/WebKit je **3499 PASS**; Diagrammadapter und Worker-Neustart PASS.
- **SDK/LSP/Python:** `SDK_PROCESS_PASS 9 commands`, SDK-Zip `interlis-java-sdk-0.2.0-SNAPSHOT.zip` erzeugt, LSP-Distribution gebaut und `LspProcessTest` PASS; Pythonfreiheit 1250 Inputs, private PATH, `python`/`python3`/`pip`/`pip3`/`pypy`/`uv` → ENOENT, absolute `/usr/bin/python3` und `/usr/bin/pip3` → EPERM, Gesamtlauf Exit 0.
- **Kumulativ:** AC3-01/02 wurden mit denselben frischen Testklassen und portablen Szenarien erneut am Kandidaten geprüft; AC3-03/04/05 mit den neuen Repository-/Workspace-/CLI-/Compiler-Tests und den echten Closures. Eigene Gegenproben liefen am selben SHA. Kein Widerspruch zwischen Einzel- und Phasenbewertung.

## Eigene Gegenproben

### Gegenprobe 1 – `RawVersionProbe` (AC3-04, exakte rohe Version/kein stiller Fallback)

**Hypothese und fachliches Risiko:** Der Kandidat behauptet, Modellversionsstrings blieben rohe exakte Strings einschliesslich signifikanter Leerzeichen, und ein exakter Modellpin führe nie zu einer stillen „Latest“-/Trim-Auswahl. Risiko: Eine Normalisierung (Trim) würde einen falsch geschriebenen Pin auf die einzige vorhandene Definition abbilden und damit eine falsche Identität zertifizieren.

**Neu erstellter Testinput / Pfad:** `build/review/L3-2/probe/RawVersionProbe.java` (eigener `main`, kein umbenannter Produkttest). Index `Version` = `"1 "` (signifikanter Leerzeichen), Modellkopf `VERSION "1 "`, ein exakter Pin `"1 "` und ein negativer Pin `"1"` (getrimmt); zusätzlich Lock-Codec-Roundtrip mit `"1 "` und `"1"`.

**Erwartetes Verhalten:** (1) Pin `"1 "` wird beschafft, der Lock hält `"1 "`, die öffentliche `ModelSymbol.modelVersion()` ist `"1 "`. (2) Pin `"1"` wird als `VERSION_MISMATCH` abgelehnt, ohne die vorhandene `"1 "`-Definition zu wählen. (3) Der Lock-Codec erhält beide Versionen getrennt und kanonisch.

**Kommando / Exitcode / tatsächlicher Output:**
```text
javac -cp <repository-test-runtime-classpath> -d build/review/L3-2/probe/classes build/review/L3-2/probe/RawVersionProbe.java
java  -cp <...>:build/review/L3-2/probe/classes RawVersionProbe
PROBE1 fetchedRawPin version=[1 ] equals=[true]
PROBE1 publicVersion=[1 ] equals=[true]
PROBE2 trimmedPin outcome=Rejected reason=VERSION_MISMATCH
PROBE3 lockVersions=[1, 1 ] canonical=true
PROBE RESULT PASS
Exit 0
```

**Ergebnis und Schlussfolgerung:** Die Behauptung hält. Kein Trim-/Latest-Fallback, keine falsche Identität; der Lock bewahrt beide rohen Versionen getrennt. Kein Finding.

### Gegenprobe 2 – `OverlayIdempotenceProbe` (kumulativ AC3-01/OBS-1)

**Hypothese und fachliches Risiko:** Die L3-1-Präzisierung behauptet, gleichrangige identische Overlaywerte seien im wirksamen Wert/Index idempotent, während getrennte Vorkommen und Herkünfte in der öffentlichen Liste erhalten bleiben. Risiko: Eine Deduplizierung könnte Provenienz verlieren oder der wirksame Wert könnte von der Vorkommenszahl abhängen.

**Neu erstellter Testinput / Pfad:** `build/review/L3-2/probe/OverlayIdempotenceProbe.java` (eigener `main`). Zwei identische `MetaAttributeOverlay`s (gleiche ID, Priorität 5, Ziel `F.P`, Wert `x`) plus Quellvorkommen `!!@ label=source`.

**Erwartetes Verhalten:** Wirksamer Wert `x`; drei öffentliche Vorkommen (2× `OverlayOrigin`, 1× `DeclaredOrigin`); First-Occurrence-Index bleibt `x`.

**Kommando / Exitcode / tatsächlicher Output:**
```text
java -cp <...>:build/review/L3-2/probe/classes OverlayIdempotenceProbe
PROBE effective=[x] occurrences=3 overlayOrigins=2 sourceOrigins=1
PROBE RESULT PASS
Exit 0
```

**Ergebnis und Schlussfolgerung:** Die präzisierte L3-1-Aussage ist am L3-2-Kandidaten korrekt; keine Kriterienaufweichung, kein Provenienzverlust. Kein Finding.

## Pflichtprüfungen

**Ausgeführte Kommandos, tatsächlicher Exitcode, Anzahl Tests/Fehler/Skips:**

```sh
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
tools/verification/without-python.cjs ./gradlew checkPortable checkBrowser \
:interlis-wasm-webimage:webImageProbe g2EvidenceTest conformanceCheck \
sdkProcessCheck sdkPackage :interlis-lsp:installDist pythonFreeCheck \
--continue --rerun-tasks \
-PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
-PgraalVmHome=/Users/stefan/.sdkman/candidates/java/25.0.3-graal \
-PbinaryenHome=/Users/stefan/.cache/interlis-java/runtime/binaryen-version_133
```

- **Exit 0**, **BUILD SUCCESSFUL in 4m 19s**, **71 actionable tasks: 71 executed**, `--no-daemon` durch den Wrapper ergänzt, `--rerun-tasks` wirksam.
- **1025 JUnit-Fälle**, **0 Failures / 0 Errors / 0 Skips**. Aufschlüsselung aus den rohen `build/test-results/` (187 XML-Dateien):

| Modul / Task | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| interlis-compiler / test | 710 | 0 | 0 | 0 |
| interlis-repository / test | 41 | 0 | 0 | 0 |
| interlis-cli / test | 37 | 0 | 0 | 0 |
| interlis-workspace / test | 20 | 0 | 0 | 0 |
| interlis-wasm-bridge / test | 6 | 0 | 0 | 0 |
| interlis-model / test | 16 | 0 | 0 | 0 |
| interlis-lsp / test | 16 | 0 | 0 | 0 |
| interlis-projection / test | 12 | 0 | 0 | 0 |
| **reguläre JVM gesamt** | **858** | **0** | **0** | **0** |
| interlis-cli / evidenceTest | 137 | 0 | 0 | 0 |
| interlis-compiler / g2AssertionTest | 15 | 0 | 0 | 0 |
| interlis-cli / verificationTest | 15 | 0 | 0 | 0 |
| **Gesamt** | **1025** | **0** | **0** | **0** |

- Frische: alle XML-Timestamps liegen zwischen `2026-10-30T12:39:41.568Z` und `2026-10-30T12:40:44.024Z`, also nach dem Gate-Start `2026-10-30T12:39:31.311Z`; der Gesamtlauf endete `2026-10-30T12:43:51.625Z`.
- Die neuen relevanten Klassen sind frisch enthalten: `TranslationSelectionContractTest` 4, `SemanticOverlayContractTest` 12, `RepositoryClosureContractTest` 15, `PinnedRealClosureTest` 2, `RepositoryCliContractTest` 4, `ResolutionBoundaryContractTest` 7, `OverlayWorkspaceContractTest` 3, `OverlayProtocolContractTest` 2, `ResolutionPolicyProtocolTest` 1, `ResolutionLockContractTest` 2, `RepositoryIndexReaderTest` 6 – alle 0/0/0.

**Rohberichte / CI-Artefakte und Bindung an CANDIDATE_SHA:**

- `build/reports/python-free/environment.json`: `gitHead=1387ac887239ecc78ed42e688a81a0d00dfcba84`, `dirty=""`, `status=passed`, `exitCode=0`, `startedAt=2026-10-30T12:39:31.311Z`, `finishedAt=2026-10-30T12:43:51.625Z`, privater PATH, Probes ENOENT/EPERM.
- `build/reports/python-free/source-check.json`: `files=1250`, `status=passed`.
- `build/reports/conformance/conformance-summary.json`, `build/conformance/{report.json,report.junit.xml,runner-exit.txt,normative-assessment.json}`.
- `build/reports/sdk/{version-manifest.json,sdk-process-comparison.json}`, `build/releases/interlis-java-sdk-0.2.0-SNAPSHOT.zip`.
- `interlis-wasm-teavm/build/{js-contract-result.json,wasm-contract-result.json,browser-results.json,safari-result.json}` und `interlis-wasm-webimage/build/{webimage/result.json,webimage-browser/browser-results.json}`.
- `git status --porcelain` im Reviewworktree vor und nach der Prüfung leer; die Artefakte liegen ausschliesslich unter ignoriertem `build/`.

**Conformance-Fallmenge; rohe und projektspezifische Bilanz:**

- Suite-Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`, Archiv-SHA-256 `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe`, Filter `^(ili23|ili24)\.`.
- **610** Fälle, **593** selektiert, **17** INTERLIS-1.0 ausgeschlossen; Runner-Rohexit **1**.
- Roh: **577 PASS / 15 FAIL / 1 XFAIL / 0 XPASS / 0 ERROR** (`referenceResult=FAIL`).
- Projekt: **577 PASS / 16 XFAIL / 0 FAIL / 0 XPASS / 0 ERROR**, `overallResult=PASS`.
- **21/21** strenge Assessments `result=PASS`; die Roh- und Projektbilanz bleiben getrennt sichtbar.

**Norm-/Suite-/XFAIL-/Baseline-/Quellpin-Unverändertheit:**

- `git diff --name-only 1de1daa..1387ac8` enthält keine Datei unter `testdata/conformance`, `docs/evidence/conformance`, keine `conformance-suite.properties`, keine `baseline.json`, keine XFAIL-/Erwartungsdatei und keine Suite-Pin-Datei. `gradle/conformance-suite.properties` bleibt unverändert.
- Das aktuelle 21-Objekt-Manifest und das historische 19-Fälle-Bytearchiv `docs/evidence/conformance/normative-expectations-before-set-context.json` sind bytegenau unverändert; eigener SHA-256 des Archivs: `67244a8138bbe2c17e0feaecd7c08885be981904cb88d33cfaad980123a2e6c1` (wie dokumentiert), `cases=19`.
- `testdata/g2/predefined-catalogue.json` wurde ausschliesslich in Quellankern/Hashes geändert: nach Entfernen von `sourceAnchors`/`sourceHashes` sind Basis- und Kandidateninhalt identisch; beide haben 151 Anker. `g2-rule-matrix.json` ändert nur Inventar-/Kandidatenhashes und `testMethods` 688→704; `g2-test-candidate-index.json` nur `testMethods` 688→704 und Status bleibt `CANDIDATE_ONLY`.
- **704** Compiler-`@Test`-Methoden (plus 3 `@ParameterizedTest`, die 6 zusätzliche Fälle erzeugen → 710 JUnit-Fälle); 15 `PredefinedOrigin`-Produzenten unverändert. Keine Testmethode entfernt, keine Testdatei gelöscht; die Teständerungen seit der Phasenbasis sind rein additiv.
- `CANDIDATE_ONLY` bleibt offen; `GlobalD2` bleibt `PENDING_USER_REVALIDATION`. Keine Normentscheidung wurde durch Referenzmehrheit ersetzt.

**Python-Migrations-/Phaseninvarianten:**

- Gesamter Pflichtlauf durch `tools/verification/without-python.cjs` mit privatem PATH und OS-weiter Prozessausführungsverweigerung; keine Python-/pip-/uv-Aufrufe. Absolute Interpreter → EPERM, PATH-Namen → ENOENT. `pythonFreeCheck` 1250 Inputs PASS. Keine getrackte `.py`-Datei und keine neue Python-Abhängigkeit.

**`git diff --check`:**

- `git diff --check 1de1daa..1387ac8` meldet 344 Trailing-Whitespace- und 2 „new blank line at EOF“-Warnungen. Sämtliche Warnungen liegen ausschliesslich in den unveränderten gepinnten Originalfixtures unter `interlis-repository/src/test/resources/closures/` (bewusst erhaltene CRLF-/Originalbytes). **Keine** Warnung in einer anderen Datei; die Fixtures wurden nicht bereinigt.

**Nicht ausgeführte fällige Prüfungen mit Grund:**

- Keine fällige Phase-3-Prüfung ausgelassen. `interlis-wasm-teavm:disasmWasmGC` ist ein nicht-testender Build-Task und kein fehlender Pflichttest; Emscripten/Disasm sind optional. T4-/Corpus-/Benchmark-/`releaseCheck`-Pflichten wurden nicht erfunden und nicht als Phase-3-Gate behandelt.

## Transportprovenienz des Reviewprozesses (getrennt vom Gate)

Der ursprüngliche OpenCode-Prozess wurde nach der vollständigen PASS-Schlussantwort mit **CLI-Exit 1** beendet. `build/review/L3-2/process-result.json` belegt `code=1`, `signal=null`, `finishedAt=2026-10-30T12:49:55.201Z`. Ein erfolgreicher Exit dieses Prozesses wird ausdrücklich **nicht** behauptet.

`build/review/L3-2/opencode.jsonl` enthält **486** Ereignisse. Ereignisindex **6** ist ein einzelnes `provider.transport`-Fehlerereignis: `ECONNRESET`, HTTP-Status **200**, Meldung „Connection lost while reading the response … socket connection was closed unexpectedly“. Danach folgten **479** weitere Ereignisse (Index 7 bis 485) mit dem vollständigen frischen Pflichtlauf (**Exit 0**), beiden Gegenproben, der Berichtsspeicherung/Hashkontrolle und der PASS-Schlussantwort. Die Socketstörung ist **keine** fehlgeschlagene Gradle-/fachliche Prüfung und ändert weder Kandidaten-SHA noch Gate-Ergebnis; sie wird hier ausschliesslich als Transportprovenienz getrennt ausgewiesen. Es wurden keine Geheimnisse ausgegeben.

## Findings

**Keine BLOCKER- oder MAJOR-Findings. Keine offenen Muss-Findings.**

- Die beiden L3-1-Beobachtungen OBS-1/OBS-2 sind rein dokumentarisch präzisiert: Kapitel 06 formuliert jetzt „im wirksamen Wert/Index idempotent“ bei erhaltenen Vorkommen/Herkünften; `MetaAttributeSet` dokumentiert die Reihenfolge „wirksame Overlaywerte, dann Quellvorkommen“. Die eigene `OverlayIdempotenceProbe` bestätigt die Wirkung am Kandidaten. Keine Kriterienaufweichung.
- Die absichtlichen Whitespace-Warnungen der gepinnten Originalfixtures sind kein Finding; sie sind durch `.gitattributes` (`-text`) und die Originalbyte-Anforderung begründet.

## Urteil

**PASS für L3-2.**
**PASS für Phase 3 (kumulativ AC3-01 bis AC3-06).**

Begründung: Alle sechs Kriterien sind mit eigenen frischen Ausführungen, rohen JUnit-/Conformance-/Portable-/Browser-/SDK-/LSP-Artefakten und zwei selbst erfundenen, tatsächlich ausgeführten Gegenproben am exakten Kandidaten `1387ac887239ecc78ed42e688a81a0d00dfcba84` belegt. Die Pflichtmatrix ist vollständig (T1 1025/0/0/0, T2 593/610 mit 21/21 Assessments, T3 3499 je Backend/Browser/Safari/Web Image, SDK 9, LSP, Pythonfrei 1250), die Suite-/Archiv-/XFAIL-/Baseline-/Normpins und das 19-Fälle-Archiv sind unverändert, und es gibt keine offenen technischen Findings. Der Reviewworktree ist unverändert sauber; es wurde nichts committet, gepusht oder released. Nächster Schritt: Übergabe des unveränderten Berichts an den Auftraggeber; **kein Beginn von Phase 4** und keine globale G2-Freigabe.
