# Unabhängiger Review: L4-2 – kumulative G2-Gesamtfreigabe

**Lieferabschnitt / Phase / letztes Phasengate?:** L4-2 / Phase 4 / **ja** (AC4-04–AC4-07, kumulativ AC4-01–AC4-07 sowie AC0A/AC0B/AC1/AC2/AC3).  
**Implementierungsagent:** Codex.  
**Prüfagent:** OpenCode CLI **2.0.28**; tatsächliche Konfiguration: Provider **`deepseek`**, Modell **`deepseek-flash`** (DeepSeek direkt, kein OpenRouter). Beleg: `build/review/L4-2/launch.json` des äusseren Launchers (`"provider":"deepseek","model":"deepseek-flash","candidate":"1371841…"`). Die Modellselbstauskunft ist kein Identitätsbeweis.  
**Phase4-BASE_SHA:** `4a84068624cffde4549186c53cf23b9aaad7b179`  
**Kumulative G2-Ausgangsbasis:** `865c99bab6ccea4ca3900d897381fa3a2a41fe39`  
**Letzter unabhängig abgenommener L4-1-Produktstand:** `e3a57c072bf93ff2351d6aa6fc551f4e562c4437`  
**CANDIDATE_SHA (durch `git rev-parse HEAD` verifiziert):** `1371841572828a387c1369ae84f6fa6cb4ddffc0`  
**Worktree:** `/private/tmp/interlis-java-L4-2-review-1371841-nopy` (sauber; `git status --porcelain` leer; `git diff --check` leer; eigene Proben ausschliesslich unter ignoriertem `build/`).  
**Bericht:** `/Users/stefan/sources/interlis-java/build/review/L4-2/review.md` (ausserhalb des Kandidaten). Rohkopien: `/Users/stefan/sources/interlis-java/build/review/L4-2/raw/`.

## Prüfumgebung / Toolchain

| Werkzeug | Tatsächlich verwendet |
|---|---|
| Java | Temurin **25.0.3+9-LTS** (`JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem`) |
| Node | **v24.22.0** (`/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node`) |
| Gradle | Wrapper **9.8.1**, direkter Aufruf `./gradlew --no-daemon` (nicht der without-python-Wrapper) |
| GraalVM | **25.0.3** (`/Users/stefan/.sdkman/candidates/java/25.0.3-graal`) |
| Binaryen | **version_133** (`/Users/stefan/.cache/interlis-java/runtime/binaryen-version_133`) |
| Browser | Chromium **156.0.8078.4**, Firefox **157.0**, WebKit **27.2**, echtes Safari **27.0 (20625.1.29.18.28)**; Playwright **1.64.1** |
| Python | **nicht für Auswertung/Dateibearbeitung verwendet.** PATH nur `/var/folders/.../interlis-without-python-*/bin`; `python/python3/pip/pip3/pypy/uv` ENOENT, absolute Interpreter EPERM (Manifest des äusseren Launchers). |

**Transporttrennung:** Der unten genannte **Gradle-Exit 0** ist ausschliesslich der Exit des Gradle-Pflichtlaufs. Der OpenCode-CLI-Transportexit wird vom äusseren Launcher protokolliert; ich habe ihn nicht beobachtet und leite ihn nicht aus dem grünen Gradle-Lauf ab. Während der Sitzung war `build/reports/python-free/environment.json` mit exaktem `gitHead` `1371841…`, leerem `dirty` und den ENOENT/EPERM-Proben `status: running`; die finale Status-/Kopierprovenienz nach `outer-environment.json` erfolgt nach CLI-Ende durch den Launcher.

## Kriterienprüfung AC4-01 bis AC4-07

| Kriterium-ID | Urteil | Eigener Beleg | Finding |
|---|---|---|---|
| **AC4-01** Vollständigkeit, Grammatikzweige, Builtins, 80 Anforderungen | **erfüllt** | Eigener struktureller Vollaudit `node build/review-l4-2/probes/link-audit.cjs .` → **Exit 0**, `failures=0`: **369** Bedingungen (285 `ASSERTIONS_LINKED` / 84 `OUTSIDE_G2_COMPILER`), **80** Anforderungen, **321** Produktionen mit **1225** Occurrences in 30 Gruppen, **21** Builtins, **130** Predefined, **21** Sonderwerte/-typen, **264** Bindings, 6 SourcePins. Geprüft: alle IDs gegen `g2-norm-conditions.json`/`g2-test-candidate-index.json`/`g2-grammar-inventory.json`/`g2-builtin-inventory.json`/`predefined-catalogue.json`; alle 369 Quote-Hashes; jede Bindung: realer Testquellpfad, `suite`=Klasse, `method`=Slice, `sourceSha256`, `methodSha256`, Testannotation, Beobachtung; jede Referenz auflösbar; Anforderungs-IDs exakt; Grammatik-Occurrence-Mengen exakt; historischer Kandidatenindex **1254× `CANDIDATE_ONLY`**, Normbedingungen `PLANNED_NOT_ACCEPTED`. Frisch: `CompletionLinksTest` **7/7**, `g2EvidenceTest` **144/144**, alle **264** gebundenen Methoden im frischen regulären JUnit-XML vorhanden (`missingBindings=[]`, eigener `junit-audit.cjs`). Semantische Einzelprüfung der risikotragenden und aller geänderten Zeilen: `NORM:2.3:3.2.1:C01` bleibt Transfer/P4-`OUTSIDE`, `NORM:2.3:2.2.1:C01` und `NORM:2.4:3.2.1:C01` sind die jeweiligen Whitespace-Absätze; areAreas-Zeilen binden den Körper mit tatsächlichen `areAreas`, `areAreas2/3`, `areAreasWithoutGaps`-Aufrufen; `NORM:2.3:2.8.13.1:C04`/`NORM:2.4:3.8.13.1:C05` binden die statische Toleranz, `C06` bleibt Topologie/P4-`OUTSIDE`; `NORM:2.4:3.6.1:C03` bindet sieben tatsächlich öffentlich assertierende Methoden (MANDATORY-Fortsetzung, DomainRef, BAG/LIST/Kardinalität, direkter LIST-OF-TEXT, ANYSTRUCTURE/Restriction, REFERENCE-Pfad, negative Quellfehler). Die drei 2.3-Domain-/Typ-/Konstantenzeilen wurden gelesen: `DomainRestriction…` prüft `Derived EXTENDS Required` und die effektive TEXT*20-Bindung; `NumericAbstraction…` prüft `NUMERIC(ABSTRACT)`; `NumericConstantBinding…`/`ImportedDomainType…` prüfen exakte Einheiten-/Enumwerte. | – |
| **AC4-02** JVM-/Portable-/Referenz-Semantikvergleich | **erfüllt** | Frisches `semanticComparison`: **Exit 0**, `SEMANTIC_COMPARISON_PASS 4 actual reference/JVM/JS/WasmGC cases; 8 mutation checks`. Vier echte 2.3-/2.4-Quellen (gültig/ungültig), drei Viewables `F.Child`, `F.T.Link`, `F.U.Link`, geordnete geerbte Member und voll qualifizierte Rollen-Ziele; portables Gegenstück `A3_PORTABLE_PASS 8 actual projections compared to fresh JVM/reference`. Acht Mutanten: fehlender Member, falsches gleichnamiges Ziel, vertauschte Reihenfolge, fälschlich gültiger Ausgang (je 2.3/2.4). Referenz-JAR `37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e` gepinnt. Grenzen im Rohbericht ausdrücklich: nur drei Viewables, nur Outcome/geordnete Member/qualifizierte Ziele gemeinsam; Origins, Diagnostikidentitäten, Ausdrucks-/Generics-/Translationsgraph **nicht** referenzgleich und separat über `SemanticMemberProtocolTest`, `ViewInheritanceTranslationContractTest`, `RemainingLanguageContractTest` u. a. geprüft. Eigene neue Probe P2/P3 (unten) bestätigt auf JVM/JS/WasmGC zusätzlich Translation, Vererbung, gleichnamige lokale/importierte Rollen-Ziele und View-Projektion an einem neuen Modell. | – |
| **AC4-03** Referenzabweichungen/Normprofile entscheidungsreif | **erfüllt** | `L4-1-decisions.json`: **12** Fälle, Top-Level-`userDecision` vom 30.10.2026, `expectationsChanged=false`; 11 Originale bleiben gezielt abgelehnt, `overlapTooSmallFail` (3 Nachkommastellen, Toleranz 0.0001) wird akzeptiert, `0.0000` über eine gepinnte Negativkontrolle abgelehnt. `L4-1-model-class-decision.json`: modellweite 2.3-Rollenziele wie ili2c, `userDecision` vorhanden. `L4-1-profile-questions.json`: `STRICT_OUTER_TYPE_NAMES`, `URI_REFERENCES_INCLUDING_RELATIVE` (ASCII, Syntax, 1023-Grenze), `userDecision`/`selected` vorhanden. Frühere Nutzerprofile (TYPE MODEL, Numerik/Scaling, Metadata, 2.3 OID/BASKET OID ohne CONTRACTED, CONTINUOUS/CIRCULAR, N1 areAreas, N2 OBJECTS-Funktionen, N3 SET) bleiben in den L1-/L2-Berichten belegt; es gibt keine neue XFAIL-Ausnahme. Rohreferenzabweichungen bleiben sichtbar: raw **577 PASS / 15 FAIL / 1 XFAIL**, Projekt **577 PASS / 16 XFAIL / 0 FAIL/ERROR/XPASS**. | – |
| **AC4-04** Nutzerentscheidungen tatsächlich getroffen und unverfälscht umgesetzt | **erfüllt** | Die drei Entscheidungspakete tragen den tatsächlichen Nutzerwortlaut und Datum; die 12 Original-/Kontrollquellen, Originalindizes und SHA-Pins sind unverändert (kein Diff unter `docs/evidence/conformance/`/`testdata/conformance/` gegenüber `4a84068`). Rohbilanz und 21 Assessments bleiben getrennt sichtbar. Die 2.3-Domain-/Typ-/Konstantenzeilen wurden nur redaktionell auf 2.3-Texte präzisiert; die 2.4-Domainconstraintkante bleibt bei den tatsächlichen 2.4-Bedingungen (`NORM:2.4:3.8:C01–C04`). Keine Reviewer-Norm wurde anstelle des Nutzers gesetzt. | – |
| **AC4-05** T4: reale gepinnte Closures | **erfüllt** | Frisches `PinnedRealClosureTest` **2/2 PASS** (`interlis-repository/build/test-results/test/TEST-guru.interlis.repository.PinnedRealClosureTest.xml`): 2.3 `RoadsExdm2ien` → `RoadsExdm2ben` (zwei Dateien) und 2.4 `LocalisationCH_V2` → `Localisation_V2` → `InternationalCodes_V2` (drei Modelle einer Datei), transitive Imports, Originalindizes, öffentliche Basen/Typen/Member/Enums/Constraints/Herkunft, Fresh/Reuse; andere Modelle derselben 2.4-Datei werden nicht als ausgewählte Closure ausgegeben (assertion auf `DictionariesCH_V2`). Repository-/CLI-Verträge frisch: 41 Repository-Tests, darunter `RepositoryClosureContractTest` (15), `ResolutionLockContractTest` (2), `RepositoryRefreshServiceTest` (2), `RepositoryClientTest` (3), `FileSourceLoaderTest` (3), `HttpSourceLoaderTest` (5), `ContentStoreTest` (3); HTTP-/Datei-/lokale Provider, Versionen/Mehrdeutigkeit, Offline/Pins/Locks, Abbruch, stale/atomare Übernahme und Originalbindungen. Unabhängige Byteprüfung: offizielle Downloads von `models.interlis.ch`/`models.geo.admin.ch` stimmen exakt mit den gepinnten Closures überein (unten). Compilerkern bleibt hostfrei (`ArchitectureBoundaryTest`). | – |
| **AC4-06** Vollständiger frischer T1–T4-/Conformance-/Safari-/WebImage-/SDK-/CLI-/LSP-/Pythonfrei-Lauf | **erfüllt** | Direkter Pflichtlauf am exakt sauberen Schluss-SHA: **Gradle-Exit 0**, `BUILD SUCCESSFUL in 11m 43s`, **80 actionable tasks: 80 executed** (3 optionale `SKIPPED`, 6 lifecycle-`UP-TO-DATE`; keine fällige Pflichtprüfung übersprungen). **1063 JUnit** in 199 frischen XMLs, davon **886 regulär** (735 Compiler, 41 Repository, 37 CLI, 22 Workspace, 7 Bridge, 16 Model, 16 LSP, 12 Projection), **144 Evidenz**, **15 Assertions**, **18 Host**; **0 Failures / 0 Errors / 0 Skips**, 199/199 Header frisch und konsistent. **3548** je TeaVM-JS, WasmGC, Chromium, Firefox, WebKit, echtem Safari, WebImage/Node und WebImage/Chromium/Firefox/WebKit. Conformance **593/610** (17 INTERLIS-1 ausgeschlossen), roh **577/15/1** und Projekt **577/16/0**, **21/21** Assessments PASS. SDK **9** Prozesskommandos, SDK-ZIP **17'030'394 Bytes**, LSP-`installDist`. Referenz **5/5**, OriginalViews **19/19**, Views **134/102/6**, Units **123/121/0**. Corpus **13** Modelle: 11 Erfolge, 2 exakte `SCOPE_OUTER_TYPE_NAME`-Ablehnungen, 13 Formatierungen; Messgrenzen 4 Wasm-Modelle, Chromium/Firefox je 12 gemessen/1 Stackausnahme, WebKit 1/12 (P3-Grenzen, keine G2-Browserpflichtskips). `pythonFreeCheck` **1290** Inputs, 0 getrackte `.py`, Canaries 2/2. | – |
| **AC4-07** Vollständiges unabhängiges Gesamturteil; G2-Freigabe | **erfüllt** | Alle AC4-01–06 sind oben am selben SHA belegt; die kumulative AC0A–AC3-Prüfung (unten) ist am selben SHA mit frischen XML-/Rohbelegen nachvollzogen; eigene neue Proben P1–P4 wurden tatsächlich ausgeführt. Kein offenes Muss-Finding. Das Urteil ist ausdrücklich **G2**, nicht P3/P4/P5. | – |

## Kumulative G2-Prüfung (AC0A bis AC3) am selben SHA

| Kriterium | Urteil | Aktueller Source-/Testbeleg für `1371841…` |
|---|---|---|
| **AC0A-01** Offener äusserer Kommentar nach geschlossenem innerem, RED vor Fix | erfüllt | Historischer RED im unveränderten Bericht `docs/g2/implementation/L0A.md`; am Schluss-SHA bindet `NestedBlockCommentContractTest` (6 Ausführungen, 2.3/2.4) den gepinnten Fall `testdata/g2/a2-unterminated-nested-comment.ili` und verlangt `SOURCE_ERRORS`. Eigene neue Lexerprobe P4: offener äusserer Kommentar wird in 2.3/2.4 mit `ILI-SYN-0002` an der äusseren Öffnung abgelehnt. |
| **AC0A-02** Gültige einfache/verschachtelte Kommentare; STRICT/EDITOR, Rekonstruktion, UTF-16, Compile-Gate | erfüllt | `NestedBlockCommentContractTest` 6/6 und `LexicalRecoveryContractTest` 2/2 frisch, 0 Fehler: beide Parse-Modi, `\n`/`\r\n`, Trivia-Ranges/Rekonstruktion, gültige Compile-Gates; eigene P4-Balanced-Probe 2.3/2.4 akzeptiert. |
| **AC0A-03** Relevante JUnit-/Conformance-/Portabilitätsprüfungen; keine Lockerung | erfüllt | Frischer Gesamtlauf (oben); kein Diff unter Suite-/Original-/Kontrollpins; keine XFAIL-/Golden-Änderung. |
| **AC0A-04** Sauberer Kandidat, Bericht, eigener neuer Lexer-Gegenfall | erfüllt | Worktree/Commit sauber; eigener neuer Lexerfall P4 (nicht aus `testdata/g2`) ausgeführt; unabhängiges Urteil hier. |
| **AC0B-01** Direkter gepinnter externer `InterlisConformance.java`, unveränderte Fallmenge | erfüllt | `runConformanceSuite` direkt; Suite-Commit `88576ffc…`, Archiv-SHA **6a80b7e8…** (eigener Hash des Cache-Archivs); 610 Fälle, 593 ausgewählt, 17 ILI-1 ausgeschlossen. |
| **AC0B-02** 18 Normabweichungen separat; Original-/Kontroll-/Hash-/Exit-/Diagnoseprüfungen; ILI-1 getrennt | erfüllt | `NormativeConformanceContractTest` 18/18 (Negativcanaries); `conformance-summary.json`: 21 Assessments, Original-/Positiv-/Negativkontrollen, Roh-/Projektbilanz getrennt; ILI-1-Baseline separat. |
| **AC0B-03** Alt/Neu-Parität, Negativcanaries, Python-Aufrufe beseitigt | erfüllt | `NormativeConformanceContractTest` (ID/Hash/XPASS/fehlende Kontrolle/Timeout/Exit/Diagnose), `OriginalViewFixtureContractTest`; `git ls-files '*.py'` 0; Matrix ohne Python. |
| **AC0B-04** JUnit-XML/Quellen-/Assertion-Bindungen als Java-Tests; Suite/Klasse/Methode/Fehler/Skips/Frische; falsche Klasse erkannt | erfüllt | `g2EvidenceTest` 144/144; `CompletionLinksTest` 7/7; `AssertionEvidenceTest` 25, `CatalogueEvidenceTest` 56, `NormEvidenceTest` 31, `RuntimeReportsTest` 25; `AssertionEvidenceTest` prüft frische `g2AssertionTest`-XMLs, Marker/Hashes, falsche Klasse/Methode; eigener Frischeaudit aller 199 XMLs. |
| **AC0B-05** Aktive Norm-/PDF-/Version-/Matrixverträge; keine stille Pinänderung | erfüllt | `prepareG2Norms` verifiziert PDF-Pins (`ili23.pdf` `8ab1c6…`, `ili24.pdf` `a8fa26…`); `NormEvidenceTest`/`CatalogueEvidenceTest` prüfen Zitate, Absätze, Owner, Versionen; `g2-rule-matrix`/`g2-source-inventory` nur mechanisch nachgeführt, keine Bedingung/Erwartung geändert. |
| **AC0B-06** SDK-/CLI-/Referenz-/Corpus-/Performancepfade über Java/Gradle/Node | erfüllt | SDK 9/9, ZIP, LSP; `referenceCheck` 5/5, `originalViewReferenceCheck` 19/19, Views 134/102/6, Units 123/121/0; `geoCorpus` 13, JVM 13, Wasm 4, Browser 3×13 mit sichtbaren Stackgrenzen; keine stillen Erfolge. |
| **AC0B-07** 0 getrackte `.py`, aktive Pfade ohne Python, `pythonFreeCheck` mit Fehlgründungs-Canary | erfüllt | `git ls-files '*.py'` = 0; `PYTHON_FREE_SOURCE_PASS 1290 inputs`; `PythonFreeCheckTest` 2/2 (absolute/env/xargs/Shell/Manifest/Canary); PATH/EPERM-Manifest. |
| **AC0B-08** Frischer Gesamtlauf ohne Python inkl. T3 | erfüllt | Der obige 80-Task-Lauf ist dieser Gesamtlauf; 199 XMLs, 3548 je Backend/Browser/Safari/WebImage, SDK/Referenz/Corpus/Bench, `pythonFreeCheck`; OS-Sperre geerbt. |
| **AC1-01** Predefined/Domains/Klassen/Strukturen/Units/Versionen | erfüllt | `PredefinedBindingContractTest` 7, `LanguageFoundationContractTest` 9, `PredefinedNameBindingContractTest` 2, `UnitTypeContractTest`, `ComposedUnitContractTest`; frische XMLs 0 Fehler; SurfaceEdge/SurfaceBoundary/LineGeometry in `LanguageFoundationContractTest`/`GeometryInspectionContractTest`. |
| **AC1-02** Lexer/Parser/Name/Import/Sichtbarkeit/Forward/Recovery 2.3/2.4 | erfüllt | `LexicalProfileContractTest` 2, `ScopeNamespaceContractTest` 5, `ImportBindingContractTest` 1, `ImportDiagnosticContractTest`, `FragmentSyntaxContractTest`, `SyntaxContractTest`; positive/negative/öffentliche Query-Fälle, typisierte Fehler. |
| **AC1-03** Numerik/Skalierung/Einheiten/OID/Typkompatibilität | erfüllt | `NumericUnitConversionContractTest` 13, `NumericSubdivisionContractTest`, `UnitRelationContractTest` 6, `TypeFamilyContractTest` 1, `OidRuleContractTest`, `OidFollowupAbstractFormatContractTest`; exakte Faktoren, Grenzen, 2.3-OID-Kompatibilität. |
| **AC1-04** Text/Enum/FORMAT/Zeit/Koordinaten/Collections/References | erfüllt | `FormatPrecisionOidBindingContractTest` 10, `DomainEnumerationContractTest`, `GeometryTypeContractTest` 1, `CollectionTypeContractTest` 1, `NamedCollectionElementContractTest`, `NoOidReferenceContractTest`; tatsächliche Werte/Typen/Zielidentitäten. |
| **AC1-05** Vererbung/ABSTRACT/FINAL/EXTENDED/Override | erfüllt | `InheritanceFoundationContractTest` 12, `ModifierContractTest`, `SharedExtensionContractTest`, `MandatoryOverrideTypeContractTest`; öffentliche effektive Member/Override-Ziele. |
| **AC1-06** Association/Roles/Restriktionen/HIDING/EXTERNAL/OID/DEPENDS ON | erfüllt | `AssociationStrengthContractTest` 5, `RoleTargetInheritanceContractTest` 6, `AssociationAttributeContractTest`, `AssociationRulesContractTest`, `TopicDependencyContractTest`, `NoOidReferenceContractTest`; effektive Ziele/Identitäten. |
| **AC1-07** Effektive Member/Herkunft/Transferreihenfolge bei Import-/Dateireihenfolge und Fresh/Reuse | erfüllt | `IdentityReuseContractTest` 3, `OriginalViewReuseTest` 1, `CanonicalSourceOrderTest`, `ViewInheritanceTranslationContractTest` 10; frische öffentliche Projektionen; eigene P2/P3-Reihenfolge `za,zm`, Rollen-Ziele, Fresh/Reuse. |
| **AC1-08** Volle T1/T2/T3-Regression und kumulative AC1-01–07 | erfüllt | Der 80-Task-/1063-JUnit-/3548-Lauf oben; alle AC1-Tests frisch, 0 Fehler; eigene Probe P2/P3. |
| **AC2-01** Objekt-/Attribut-/Rollenpfade/Indizes/Mengen/Spezialkontexte | erfüllt | `ConstraintPathBindingContractTest`, `QualifiedAssociationPathContractTest`, `AssociationAccessContractTest`, `ExpressionConstraintContractTest` 7; gebundene Ausdruckstypen/Referenzen, Negativfälle. |
| **AC2-02** 21 Builtins/Signaturen/UNDEFINED/Funktions-/Constraintfamilien | erfüllt | `BuiltinSignatureFoundationContractTest` 2 (alle 21 Signaturen, gebundene Aufrufe), `ObjectFunctionContractTest`, `UndefinedFunctionContractTest`, `ExternalConstraintContractTest`, `UniqueConstraintContractTest`, `SetConstraintScopeContractTest`; externe Funktionen werden nicht ausgeführt. |
| **AC2-03** Projection/Selection/Join/Union/Aggregation/Inspektionen/ALL OF | erfüllt | `ViewBindingContractTest` 12, `ViewFormationSemanticsContractTest`, `ViewContractTest`, `ViewDependencyContractTest`, `InspectionFactorContractTest`, `NominalViewAssignmentTest`; öffentliche View-Member/Typen. |
| **AC2-04** Viewresultat mit effektiven Membern/Typen/Zielen/Reihenfolgen | erfüllt | `ViewBindingContractTest`/`ViewInheritanceTranslationContractTest` prüfen `effectiveMembers`, Position/Origin/InheritancePath/Overrides; eigene P2/P3-View `ZUseView` `label@0`. |
| **AC2-05** Übersetzte Import-Closures/Korrespondenzen/Originalziele/Reihenfolgen | erfüllt | `TranslationMappingContractTest` 3, `StructuredTranslationContractTest` 2, `ViewInheritanceTranslationContractTest` 10, `ImportedTranslationContractTest`; eigene P2: `ZTrans`→`ZBase`, 15 Korrespondenzen, qualifiziertes `ZBase.ZT.ZAlpha`↔`ZTrans.ZT.ZAlpha`, negative Typabweichung `TRANSLATION_REFERENCE`. |
| **AC2-06** CONTEXT/GENERIC/DEFERRED/Metadaten/Linienformen/Grafik/Signatur | erfüllt | `GenericContextContractTest`, `MetadataBindingContractTest` 9, `LineFormBindingContractTest`, `GraphicBindingContractTest` 10, `GraphicResponsibilityContractTest`; `areAreas2/3` nur nach belegter N1-Entscheidung. |
| **AC2-07** Volle T1/T2/T3-Regression und kumulative AC2-01–06 | erfüllt | Der obige frische Gesamtlauf; alle AC2-Tests 0 Fehler; keine offenen Muss-Findings. |
| **AC3-01** Echte unveränderliche Translation-/CRS-/Metaattribut-Overlays | erfüllt | `SemanticOverlayContractTest` 12, `OverlayProtocolContractTest`, `OverlayWorkspaceContractTest`, `SemanticOverlayContractTest`; Zielbindung/Mehrdeutigkeit/Konflikt/Herkunft/öffentliche Schemawirkung. |
| **AC3-02** Inhaltsfingerprints, reine Overlaywechsel, Isolation, Fresh/Reuse | erfüllt | `SemanticOverlayContractTest`/`OverlayWorkspaceContractTest` plus P2-Fresh/Reuse (gleicher Fingerprint und gleiche öffentliche Projektion); keine globalen Zustände. |
| **AC3-03** Repositoryindizes/Quellauflösung/transitive Imports lokal/Datei/HTTP ohne Compilerkern-HostIO | erfüllt | `RepositoryClosureContractTest` 15, `FileSourceLoaderTest` 3, `HttpSourceLoaderTest` 5, `ContentStoreTest` 3; `ArchitectureBoundaryTest` (kein Host-/ANTLR-Leak). |
| **AC3-04** Versionen/Mehrdeutigkeit/Offline/Pins/Locks/Abbruch/stale | erfüllt | `RepositoryClosureContractTest` (Versionen nie Latest, Priorität/Ambiguität, falscher Digest ohne Fallback, stale/atomar, Cancellation, Lock-Änderungen, Offline-Replay), `ResolutionLockContractTest` 2, `RepositoryRefreshServiceTest` 2, `RepositoryClientTest` 3. |
| **AC3-05** Repräsentative gepinnte echte 2.3-/2.4-Closures mit öffentlichen Assertions | erfüllt | `PinnedRealClosureTest` 2/2; offizielle Bytes (unten) und öffentliche Modell-/Typ-/Member-/Enum-/Constraint-/Herkunftsassertions. |
| **AC3-06** Volle T1/T2/T3-Regression und kumulative AC3-01–05 | erfüllt | Der obige frische Gesamtlauf; AC3-Tests 0 Fehler; AC4-05-Closures zusätzlich in diesem Review. |

## Eigene neue Gegenproben (tatsächlich erfunden und ausgeführt)

Alle Proben liegen ausschliesslich im ignorierten `build/review-l4-2/`; Rohkopien unter `/Users/stefan/sources/interlis-java/build/review/L4-2/raw/`.

### P1 – Vollständiger Link-/Quoten-/Methodenintegritätsaudit (AC4-01)

- **Input:** `testdata/g2/completion-links.json` (SHA-256 `3cebef51a1f687f555f98bbadf1f7f6bac63df0fcd4ddca507a20d0d13d85b35`) + die sechs SourcePins + Norm-/Grammatik-/Builtin-Inventare + Kandidatenindex.
- **Hypothese:** Jede der 285 Compilerbedingungen hat mindestens einen realen, ausführbaren Test; die 84 `OUTSIDE`-Cluster haben keine Scheinlinks; alle 264 Bindings stimmen mit Quell-/Methodenbytes überein; kein historischer Kandidat wurde als akzeptiert ausgegeben.
- **Kommando:** `node build/review-l4-2/probes/link-audit.cjs .` → **Exit 0**, `failures=0`.
- **Beobachtung:** `369/285/84/80/321/1225/21/130/21/264`, 6 SourcePins, alle Quote-Hashes, alle Grammatik-Occurrence-Mengen, alle Referenzen; `candidateStatus.topLevel=CANDIDATE_ONLY`, 1254 Kandidaten; 0 Fehler. Die drei geänderten 2.3-Zeilen wurden zusätzlich semantisch gelesen (siehe AC4-01).
- **Ergebnis:** Kein Scheinlink, keine verlorene Bedingung; die O1-Präzisierung ist korrekt und minimal.

### P2 – Neues JVM-Modell: Translation + Vererbung + gleichnamige Rollen + View + Fresh/Reuse (AC4-02/AC1/AC2/AC3)

- **Input:** neue Quelle `build/review-l4-2/probes/phase4-interaction.ili` (nicht aus `testdata/g2/a3`): 2.4-Modelle `ZBase` (Struktur `ZAlpha`/`ZMid EXTENDS`, Association `ZRel`, View `ZView`), `ZTrans` (`TRANSLATION OF ZBase`) und `ZUse` (Import, lokale Klasse `ZTarget` **gleichen Namens** wie importiertes `ZTrans.ZT.ZTarget`, Association `ZLink`/`ZMidLink`, View `ZUseView`).
- **Hypothese:** Die JVM-Projektion bindet das importierte Ziel voll qualifiziert (nicht das gleichnamige lokale), erhält geerbte Memberreihenfolge und Translation-Korrespondenzen, und Fresh/Reuse liefern denselben öffentlichen Graph. Eine Typabweichung in der Translation muss als `TRANSLATION_REFERENCE` abgelehnt werden; äussere Namensüberdeckung muss `SCOPE_OUTER_TYPE_NAME` liefern; 2.3-`OID AS` ohne `CONTRACTED` bleibt kompatibel.
- **Kommando:** `javac -cp 'interlis-cli/build/install/interlis-cli/lib/*' …` und `java -cp 'build/review-l4-2/probes/classes:interlis-cli/build/install/interlis-cli/lib/*' Phase4InteractionProbe` → **Exit 0**, `PHASE4_INTERACTION_PROBE checks=23 failures=0`.
- **Beobachtung:** `ZMid` = `[za, zm]` in Reihenfolge; `ZLink`-Rollen `owner→ZUse.ZU.ZHolder`, `local→ZUse.ZU.ZTarget`, `target→ZTrans.ZT.ZTarget` (gleichnamiges lokales und importiertes Ziel getrennt); `ZUseView` = `label`; Translation `ZBase` mit 13 eindeutigen Namenspaaren, darunter qualifiziert `ZBase.ZT.ZAlpha→ZTrans.ZT.ZAlpha`; Fresh/Reuse gleicher Fingerprint/Projektion. Negativ: Translationstyp `TEXT*10` vs `TEXT*5` → `SOURCE_ERRORS`/`TRANSLATION_REFERENCE`; `DOMAIN Local` + `CLASS Local` → `SCOPE_OUTER_TYPE_NAME`; 2.3 `TOPIC T = OID AS …` ohne CONTRACTED → `succeeded`.
- **Ergebnis:** Cross-Phase-Interaktion (Translation/Vererbung/Rollen/View/Reuse) ist öffentlich korrekt; der Compiler verwechselt gleichnamige Ziele nicht.

### P3 – Dieselbe neue Quelle auf TeaVM-JS und WasmGC (AC4-02/Portabilität)

- **Input:** dieselbe neue Quelle; frische Artefakte `interlis-wasm-teavm/build/generated/teavm/js/interlis-compiler.js` und `…/wasm-gc/interlis-compiler.wasm`.
- **Hypothese:** Öffentliche `semanticProperties` liefern auf beiden Backends dieselbe Translation, Memberreihenfolge, voll qualifizierten Rollen-Ziele und View-Projektion wie JVM.
- **Kommando:** `node build/review-l4-2/probes/portable-phase4-probe.cjs .` → **Exit 0**, `PORTABLE_PHASE4_PROBE_PASS runtimes=2 checks=26`.
- **Beobachtung:** JS und WasmGC je `translationSource=ZBase`, 15 Korrespondenzen, `midMembers=[za,zm]`, `targets={owner:[ZUse.ZU.ZHolder], local:[ZUse.ZU.ZTarget], target:[ZTrans.ZT.ZTarget]}`, `viewMembers=[label]`.
- **Ergebnis:** Kein Backend-spezifischer Verlust/falsches Ziel; die AC4-02-Grenzen (nur ausgewählte gemeinsame Eigenschaften) bleiben eingehalten.

### P4 – Neuer Lexerfall und offizielle Closure-Bytes (AC0A-04/AC4-05)

- **Lexer-Input:** neue Quellen `lexer-outer-open.ili`/`lexer-outer-open-23.ili` (offener äusserer Kommentar nach geschlossenem innerem) und `lexer-balanced.ili`/`lexer-balanced-23.ili`.
- **Hypothese:** Der offene äussere Fall wird in 2.3/2.4 mit Syntaxdiagnostik abgelehnt; der balancierte Fall wird akzeptiert.
- **Kommando/Beobachtung:** `interlis-cli check --json <datei>`: offen **Exit 1**, `SOURCE_ERRORS` + `ILI-SYN-0002` an Offset 68 (äussere Öffnung); balanciert **Exit 0**, `{"status":"ok","diagnostics":[]}` — jeweils beide Versionen.
- **Closure-Input:** offizielle Downloads von `https://models.interlis.ch/refhb23/RoadsExdm2ien-20170405.ili` (`6a18f8a3…`), `RoadsExdm2ben-20050616.ili` (`c12ec563…`), `https://models.geo.admin.ch/CH/CHBase_Part2_LOCALISATION_V2.ili` (`1b592fce…`), `ilimodels.xml` (`2defccf0…`) und `geo.admin.ch/ilimodels.xml` dekomprimiert (`c2ce3bfc…`). Alle SHA-256 stimmen exakt mit `pins.json`/`PinnedRealClosureTest` überein; das 13-Modell-Corpus-Seed-ZIP stimmt für alle 13 Modelle und den Index.
- **Ergebnis:** Keine synthetischen Positivkopien; T4-Closures und Corpus sind bytegenau die offiziellen Originale.

### P5 – Frische- und Headeraudit aller JUnit-XMLs (AC4-06/AC0B-04)

- **Input:** alle `*/build/test-results/*/TEST-*.xml` nach dem Lauf.
- **Kommando:** `node build/review-l4-2/probes/junit-audit.cjs . 2026-10-30T16:31:45Z` → **Exit 0**.
- **Beobachtung:** 199 XMLs, 1063 Tests, 0 Failures/Errors/Skips; 199/199 `testsuite@name = testcase@classname`, `tests` = Anzahl Testcases, alle Zeitstempel nach Buildstart; 264/264 Binding-Methoden im frischen regulären XML; keine fehlende Bindung. 19 gleiche Anzeigenamen sind erwartbare JUnit-Parameteranzeigen bzw. die absichtliche Zweitausführung der 15 Assertionsmethoden in `g2AssertionTest`, keine Duplikate im Sinn der Methodenbindung.
- **Ergebnis:** Der grüne Lauf beruht nicht auf alten/übersprungenen XMLs oder Zusammenfassungs-JSONs.

## Pflichtprüfungen: tatsächliches Kommando, Exits, Counts, Rohpfade

Tatsächlich direkt ausgeführt (nicht über den without-python-Wrapper):

```sh
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem ./gradlew --no-daemon \
  checkPortable checkBrowser :interlis-wasm-webimage:webImageProbe semanticComparison \
  g2EvidenceTest conformanceCheck sdkProcessCheck sdkPackage :interlis-lsp:installDist \
  pythonFreeCheck referenceCheck originalViewReferenceCheck viewReferenceCheck unitReferenceCheck \
  performanceCheck --continue --rerun-tasks \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
  -PgraalVmHome=/Users/stefan/.sdkman/candidates/java/25.0.3-graal \
  -PbinaryenHome=/Users/stefan/.cache/interlis-java/runtime/binaryen-version_133
```

| Prüfung | Tatsächliches Ergebnis |
|---|---|
| Gesamtlauf | **Gradle-Exit 0**, `BUILD SUCCESSFUL in 11m 43s`, **80 actionable tasks: 80 executed**, 3 optionale `SKIPPED` (`emscriptenStubWasmGC`, `emscriptenWasmGC`, `disasmWasmGC`), 6 lifecycle-`UP-TO-DATE`; keine fällige Pflichtprüfung übersprungen. Roh: `build/review-l4-2/mandatory-run.log` (SHA-256 `9d2ad161ea36b8b94c185443ad8218ed38896d86fc70c5eea471f292a626cf85`). |
| JUnit | **1063** Tests in **199** frischen XMLs: 886 regulär (735 Compiler, 41 Repository, 37 CLI, 22 Workspace, 7 Bridge, 16 Model, 16 LSP, 12 Projection), 144 Evidenz, 15 Assertions, 18 Host; **0 Failures/0 Errors/0 Skips**; Header 199/199 konsistent. Eigener Audit: `build/review-l4-2/raw/junit-audit.json` (SHA-256 `3ea938437683b98cde099f0a61d1343d81b6df2e071e9f0147accc1b6b6ac65e`). |
| `checkPortable` | TeaVM-JS/Node **3548 PASS**; WasmGC/Node **3548 PASS** (`interlis-wasm-teavm/build/js-contract-result.json`, `wasm-contract-result.json`). |
| `checkBrowser` | Chromium **3548**, Firefox **3548**, WebKit **3548**, echtes Safari **27.0** **3548** (`interlis-wasm-teavm/build/browser-results.json`, `safari-result.json`). |
| WebImage | Build frisch; WebImage/Node **3548**; Chromium/Firefox/WebKit je **3548** (`interlis-wasm-webimage/build/webimage/result.json`, `webimage-browser/browser-results.json`). |
| `semanticComparison` | **Exit 0**, 4 Fälle, **8** Mutationsprüfungen, Referenz-JAR `37d94573…`; portable 8 Projektionen (`build/reports/g2/semantic-comparison.json`, `a3-jvm.json`, `a3-portable.json`). |
| `g2EvidenceTest` | **144** Tests, 0 Fehler; `CompletionLinksTest` 7/7 (`interlis-cli/build/test-results/evidenceTest/`). |
| `conformanceCheck` | 593/610 (17 ILI-1 ausgeschlossen); roh **577 PASS / 15 FAIL / 1 XFAIL / 0 ERROR / 0 XPASS**; Projekt **577 PASS / 16 XFAIL / 0 FAIL/ERROR/XPASS**; **21/21** Assessments PASS; Runner-Rohexit **1** erwartet (`build/reports/conformance/conformance-summary.json`, `build/conformance/summary.json`, `runner-exit.txt`). |
| `referenceCheck` | **5/5** PASS (`build/reports/reference/reference-results.json`). |
| `originalViewReferenceCheck` | **19/19**, 0 Prozessfehler. |
| `viewReferenceCheck` | **134** eigene Erwartungen, **102** Übereinstimmungen, **6** sichtbare Referenzprozessfehler; keine Paritätsbehauptung (`views-reference-comparison.json`). |
| `unitReferenceCheck` | **123** eigene Erwartungen, **121** Übereinstimmungen, **0** Prozessfehler. |
| `pythonFreeCheck` | **1290** Inputs PASS; `git ls-files '*.py'` = **0**; `PythonFreeCheckTest` 2/2; `build/reports/python-free/source-check.json` (SHA-256 `632aea64ccc723d5a608baf66538252c2d14173988cce5c29ad3158584c6013b`). |
| SDK/LSP | `SDK_PROCESS_PASS 9 commands`; SDK-ZIP `build/releases/interlis-java-sdk-0.2.0-SNAPSHOT.zip` **17'030'394 Bytes**; `:interlis-lsp:installDist` erfolgreich. |
| `performanceCheck` | JVM **13** (11 Erfolge, 2 exakte `SCOPE_OUTER_TYPE_NAME`-Ablehnungen; 13 Formatierungen); WasmGC **4**; Chromium/Firefox je 13 (12 gemessen, 1 Stackausnahme), WebKit 13 (1 gemessen, 12 Stackausnahmen). Erwartungs-Hash `testdata/g2/corpus-expectations.json`; P3-Messgrenzen sichtbar, keine G2-Browserpflichtskips (dort alle 3548). |
| T4 | `PinnedRealClosureTest` **2/2**; offizielle Bytes oben; 41 Repository-Tests inkl. Lock/Cache/Stale/Abbruch/Offline. |

## Norm-, Suite-, Pin- und Baseline-Unverändertheit

- **Kein Diff** unter `docs/evidence/conformance/` oder `testdata/conformance/` gegenüber `4a84068` (`git diff --stat` leer). Kein neuer XFAIL, kein XPASS, keine Erwartungsänderung.
- Suite-Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`; Cache-Archiv-SHA **6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe** (eigener Hash); 610 Fälle, 593 ausgewählt, 17 ILI-1.
- `baseline.json` weiterhin genau **1** INTERLIS-1-XFAIL; **21** aktive Assessments unverändert; historisches **19-Fälle-Bytearchiv** `normative-expectations-before-set-context.json` SHA-256 `67244a8138bbe2c17e0feaecd7c08885be981904cb88d33cfaad980123a2e6c1` = Paketpin.
- `testdata/g2/completion-links.json`: gegenüber `e3a57c0` **nur** die drei 2.3-Zeilen `NORM:2.3:2.8:C01/C02/C03` (Scope/Resolution plus Entfernung des 2.4-Domainconstraint-Links); Bindings, SourcePins und alle übrigen Zeilen byte-/semantisch unverändert. Die 2.4-Domainconstraintkante bleibt bei `NORM:2.4:3.8:C01–C04`.
- Kandidatenindex: **1254** Kandidaten (unverändert seit `e3a57c0`), alle `CANDIDATE_ONLY`; Normbedingungen `PLANNED_NOT_ACCEPTED`; keine Promotion historischer `OPEN`-/`CANDIDATE_ONLY`-Felder.
- L4-1-Review unverändert: SHA-256 `1c2aea5b446e956f34d9aa64d23c0cbc8f00d73002f96447c6b1bd8e14fefae5`; frühere FAIL-Berichte `40c9ced5…`/`76726599…` unverändert.
- Corpus-Seed: 13 Modellhashes und Index `ff9561b4…` stimmen; offizielle 2.3-/2.4-Closure-Downloads stimmen bytegenau.
- **Keine Produkt-/Test-/Build-/Norm-/Suite-/Baseline-/Erwartungsänderung durch diesen Review; kein Commit, Push, Merge oder Release.**

## Findings

### Geschlossen: O1 (redaktionelle Versionsbeschriftung)

Die drei 2.3-Zeilen `NORM:2.3:2.8:C01/C02/C03` tragen nun 2.3-Texte; sie behaupten keine 2.4-Domainconstraints mehr. Die 2.4-Domainconstraintkante bleibt an den tatsächlichen 2.4-Bedingungen. Kein Produkt-/Test-/Build-Diff. **Geschlossen.**

### Keine blockierenden Muss-Findings

Es gibt kein offenes Muss-Finding gegen AC4-01–07 oder die kumulierten AC0A–AC3.

### Nicht blockierende Beobachtungen (keine Abnahmewirkung)

- **O2 (Evidenzhärtung):** `CompletionLinksTest` prüft die 264 regulären Bindung-XMLs mit `Instant.EPOCH`; es prüft Suite/Klasse/Methode/Fehler/Skips/Hashes, aber nicht selbst die Report-Frische. In diesem Review wurde die Frische aller 199 XMLs unabhängig geprüft (P5; alle nach Buildstart, 0 alt), und der Pflichtlauf verwendet `--rerun-tasks`. Empfehlung für eine spätere Evidenzrevision: Frische auch für diese XMLs binden. Kein aktueller Fehlbeleg.
- **O3 (historische Metadaten):** `g2-test-candidate-index.json` enthält im historischen `validation.testMethods` den Wert 615, während der aktive `testMethods`-Zähler 729 ist; der aktive `g2EvidenceTest` prüft 729 und `CANDIDATE_ONLY`. Die 615 sind keine aktuelle Abnahmezahl. Empfehlung: bei einer späteren historischen Bereinigung klar als „damaliger Stand“ kennzeichnen.

## Urteil

**PASS**

AC4-01 bis AC4-07 sind am exakten Schluss-SHA `1371841572828a387c1369ae84f6fa6cb4ddffc0` einzeln belegt. Der frische Pflichtlauf endet mit **Gradle-Exit 0** (80 Tasks), 1063 JUnit ohne Fehler/Skips, 3548 je Portable/Browser/echtem Safari/WebImage, 21/21 Assessments, SDK9/ZIP/LSP, Referenz-/Corpus-/Messpfaden und 1290 Pythonfrei-Inputs. Die kumulative AC0A–AC3-Prüfung ist mit aktuellen Source-/Testbelegen und eigenen Proben nachvollzogen; T4-Closures und Corpus sind bytegenau offiziell. Es gibt kein offenes Muss-Finding; O1 ist geschlossen, O2/O3 sind nicht blockierende Evidenzhinweise.

**Nächster konkreter Schritt:** Dokumentationsabschluss mit unverändertem Review und operativem STATUS; keine Produktänderung. Jede spätere Änderung an Code, Tests, Build, Abhängigkeiten oder ausführbaren Beispielen benötigt einen neuen Kandidaten und einen neuen vollständigen Review. Dieses PASS ist **G2**, nicht P3/P4/P5. Kein Push, Merge oder Release.

**Transportprovenienz (strikt getrennt):** Der oben genannte Exit 0 ist der Exit des Gradle-Pflichtlaufs. Der OpenCode-CLI-Transportexit wird ausschliesslich vom äusseren Launcher finalisiert und nach `/Users/stefan/sources/interlis-java/build/review/L4-2/outer-environment.json` kopiert; aus dem grünen Gradle-Lauf wird kein erfolgreicher CLI-Transport abgeleitet. Das äussere Manifest zeigte während der Sitzung `status: running`, `gitHead` exakt `1371841…`, `dirty` leer und die ENOENT/EPERM-Interpreterproben.
