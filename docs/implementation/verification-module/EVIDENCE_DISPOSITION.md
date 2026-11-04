# Disposition der bisherigen Evidenzprüfungen

**Status:** begründete Zielentscheidungen auf Basis des untersuchten Codes; in V0 am tatsächlichen Checkout methodengenau vervollständigen. Kein Test ist allein durch dieses Dokument schon entfernt oder ersetzt worden.

## 1. Warum kein neues `evidenceTest`?

Der Name beschreibt den Gegenstand nicht ausreichend. Im selben Source-Set liegen derzeit Validator-Unit-Tests, aktuelle Compilerabdeckungsprüfungen und die Sicherung eingefrorener G2-Arbeitsstände. Beispielsweise verlangt `NormEvidence` teilweise ausdrücklich Zustände wie `PLANNED_NOT_ACCEPTED` oder `SECTION_LEVEL_PENDING`. Diese Anforderungen schützen einen damaligen Arbeitsstand, beweisen aber nicht, dass der heutige Compiler richtig arbeitet.

Umgekehrt sind die Ablehnung falscher SDK-Exitcodes, manipulierter JUnit-Berichte und verlorener aktueller Testbindungen keine überflüssige Bürokratie. Sie bleiben erhalten. Deshalb gilt: **historische Kontrollinfrastruktur reduzieren, gegenwärtige Qualitätskontrollen nicht ersatzlos entfernen.**

Es wird kein pauschales Ersatz-Source-Set und kein allgemeiner `historicalEvidenceTest` angelegt. Archivprüfer werden nur dann aktiv erhalten, wenn ein konkreter gegenwärtiger Verbraucher und ein entsprechender Abnahmevertrag nachgewiesen sind. Andernfalls genügen unveränderte Originaldateien und ihre ursprünglichen versionierten Validatoren am dokumentierten historischen Commit.

## 2. Entscheidungsregeln

Jede Testmethode und jeder parameterisierte Fall gehört zu genau einer oder zu mehreren ausdrücklich aufgeteilten Entscheidungen:

| Kürzel | Bedeutung | Nachweis |
|---|---|---|
| KEEP-UNIT | dauerhafte Prüfung kleiner reiner Validatorlogik | neue Klasse/Methode unter normalem `test`, Schutzfälle identisch |
| KEEP-CONTRACT | aktueller Repository-/Ausführungsvertrag | neue Klasse/Methode mit `compiler-contract`, Inputs/Produzenten explizit |
| MOVE-PRODUCT | fachlicher Produktvertrag | zuständiges Produkt-Testmodul, keine Rückdependency |
| MERGE | echte Doppelung | genauer bestehender Zieltest und eigener äquivalenter Negativfall |
| RETIRE-HISTORY | ausschliesslich historischer Arbeitsstand | unveränderte Originalbelege, ursprünglicher Commit, kein aktiver Verbraucher |
| SPLIT | eine alte Methode vermischt mehrere Zwecke | jede Teilassertion erhält eine der obigen Entscheidungen |

`RETIRE-HISTORY` ist keine Löschung historischer Daten. `MERGE` ist keine pauschale Behauptung „wird wohl schon irgendwo getestet“. Ein entfallender Parameterfall braucht denselben konkreten Nachweis wie eine entfallende Methode.

Das Arbeitsdokument `EVIDENCE_DECISIONS.md` hat pro Methode mindestens diese Spalten:

```text
oldPath | oldClass#method | parameterCases | oldPurpose | activeConsumers |
classification | newClass#method/task | replacementProbe |
retainedHistoricalInputs | reason | actualTestEvidence
```

Die Menge der erfassten alten `@Test`-/`@ParameterizedTest`-Methoden muss mit dem V0-Quellinventar übereinstimmen. `@MethodSource`-Fälle, dynamische Tests oder weitere Annotationen werden berücksichtigt, falls lokal vorhanden. Für einfache Inventarisierung reicht AST-/Quellinspektion plus JUnit-Discovery; keine eigene Java-Parserplattform entwickeln.

## 3. `AssertionEvidence` und `AssertionEvidenceTest`

### 3.1 Helfermethoden

| Alt | Ziel/Entscheidung | Begründung |
|---|---|---|
| `recorded(JsonObject)` | SPLIT | Mischt aktuelle Quell-/Methodenprüfung mit historischem `freshSelectedRun`, `extent=PARTIAL` und `remainingGap`. Historischen Teil nicht zur heutigen Fertigstellungspflicht machen. |
| `xml(Path,String,Instant)` | KEEP-UNIT, als `JUnitReportAssertions.readVerifiedXml` | Falsche Suite, Hashes, leere Fälle, Fehler, Skips und veraltete Reports müssen weiterhin scheitern. |
| `fresh(Set<Binding>,Path,Instant,JsonObject)` | KEEP-UNIT/CONTRACT, als `requireFreshInvocations` | Frische, vollständige, eindeutig zugeordnete Testausführung bleibt ein aktueller Vertrag. |
| `Binding` | kleiner neutraler `TestInvocation` oder bestehender Typ | Keine Namensbindung an G2; Semantik der Fallidentität erhalten. |

### 3.2 Testmethoden

| Bestehende Methode | Entscheidung und neues Ziel |
|---|---|
| `recordedAssertionsBindToFreshActualCompilerMethodsAndHashedXml` | SPLIT. Die aktuelle Ausführung gebundener Tests bleibt im `CompilerCoverageContractTest`. Die historische Zahl 15 und damalige Partial-Felder nur übernehmen, wenn sie noch exakt den aktuellen Vertrag definieren; ansonsten historisch sichern und durch nachvollziehbare aktuelle Auswahl ersetzen. |
| `exactXmlIdentityAndHashesPass` | KEEP-UNIT → `JUnitReportAssertionsTest.acceptsExactSuiteAndHash`. |
| `wrongClassMethodSuiteCountersDetailsAndTimestampFail` | KEEP-UNIT; alle durch `badXml()` gelieferten Mutationen erhalten. |
| `missingStaleOrChangedXmlNeverPasses` | KEEP-UNIT; fehlende Datei, falscher Hash und veralteter Lauf getrennt prüfen. |
| `brokenSourceOrRecordedSignatureFails` | SPLIT. Traversierung, erfundene Methode und falsche aktuelle Quellbindung erhalten; ausschliesslich alte Report-/Partial-Felder nur mit verbleibendem Formatverbraucher erhalten. |
| `obsoleteOrUnhashedSuiteFails` | KEEP-UNIT; fremde bzw. im Manifest nicht enthaltene XML und fehlende erwartete XML weiterhin ablehnen. |
| `codePointOffsetsAndArtifactHashesAreExact` | KEEP-UNIT → `ContractFilesTest`; Supplementary Unicode, ungültige Bereiche, Hashfehler und Ausbruch erhalten. |

Ein synthetischer Test muss wegen seines eigenen Fixtures scheitern, nicht zufällig wegen eines zuvor fehlenden Norm-PDFs oder einer ungesetzten Rootproperty. Gezielt erwarteten Fehlertyp bzw. Ablehnungsgrund prüfen.

## 4. `CompletionLinksTest`: aktuelle Abdeckung nicht wegwerfen

`allExistingIdsHaveConcreteCurrentSourceAndExecutedPublicAssertions` liest nicht bloss ein historisches PASS. Der Test bindet konkrete Anforderungen, Quellen und echte JUnit-Berichte. Seine aktuelle Schutzwirkung ist zu erhalten.

Ziel ist `CompilerCoverageContractTest` mit klar getrennten Methoden:

- `currentRequirementsHaveExecutableBindings`: Zuordnung und eindeutige IDs, ohne Behauptung automatischer fachlicher Abnahme.
- `boundTestSourcesExistAndMatch`: passende Quell-/Methodenidentität; nur tatsächlich aktuelle Quellenpins.
- `boundAssertionsWereExecutedSuccessfully`: echte aktuelle Reports und erforderliche Invocations.
- `missingOrInventedBindingsAreRejected`: kleine normale Unit-Gegenproben aus dem bisherigen `inventedOrLostLinksCannotAppearComplete`.

Die privaten Methoden `sameIds` und `links` können unverändert als kleine Hilfsfunktionen bleiben. Die bisherige grosse `validate(document, reports)` wird entlang der drei tatsächlich verschiedenen Verträge aufgeteilt, nicht in eine universelle Evidence-Engine umgebaut.

Zu korrigieren ist eine ausdrücklich zu prüfende Annahme: Der alte Code leitet zwei Parameteraufrufe aus `body.startsWith("@ParameterizedTest")` ab. Die neue aktuelle Bindung muss die konkret erforderlichen Invocations nachvollziehbar benennen oder aus vorhandenen verlässlichen Metadaten ableiten. Das Entfernen eines Parameterfalls muss weiterhin scheitern. Kein pauschales „mindestens ein Fall reicht“.

Die breit gestarteten Produkt-`test`-Tasks sind kein notwendiges Merkmal dieser Prüfung. Selektierte `contractAssertionsTest`-Produzenten dürfen dieselbe benötigte Beobachtung mit geringerem Laufumfang liefern.

## 5. `CatalogueEvidenceTest`: zwingend aufteilen

Diese Klasse ist der wichtigste Kandidat gegen blindes Verschieben. Einige Methoden prüfen echte Schutzbedingungen; andere erzwingen historische Inventarzahlen und alte Planungszustände.

| Bestehende Methode/Familie | Zielentscheidung |
|---|---|
| `pinnedSourceAndAtomicMappingsMatchCurrentSources` | SPLIT. Tatsächlich aktuelle Regel-/Quellbindung erhalten. Globale historische Sourceinventar-Hashes nicht gegen neu organisierte aktuelle Dokumente erzwingen. |
| `allGrammarOccurrencesRemainOwnedAndUnaccepted` | RETIRE-HISTORY für eingefrorene Zahl/`unaccepted`-Arbeitsstand. Fachliche Grammatikabdeckung durch vorhandene Parser-/Compilerregressionen und aktuelle Coveragebindungen nachweisen. |
| `allSpecRequirementsAndExplicitLinesStayBound` | SPLIT. Aktuelle Anforderungs-IDs und fehlende Bindungen erhalten. Alte Zeilenpositionen und genaue historische Anzahl nicht als Formatierungsverbot aktiver Spezifikationen fortschreiben. |
| `allBuiltinSignaturesAndPendingDecisionsStayBound` | SPLIT. Builtin-Signaturen, Versionen und fachlich gültige Regeln gehören in echte Compilerverträge. Genau zwei historische `USER_DECISION_PENDING`-Einträge sind kein aktueller Produkttest. |
| `predefinedDeclarationsSpecialTypesAndVersionProfilesStayBound` | SPLIT. Öffentliche Typ-/Versionssemantik erhalten bzw. vorhandene Tests nachweisen. Alte G2-Katalogzahlen/Sourceanker nur bei aktuellem ausdrücklich benanntem Zweck. |
| `everyRecordedCandidateStillNamesTheExactTestMethod` | MERGE in aktuelle Coveragebindung, soweit die betreffenden Methoden heute noch Pflichtbeobachtungen sind. Historische Kandidatenlisten bleiben unverändert archiviert. |
| `a3JvmAndPortablePairBindingsRemainPresent` | Aktuellen JVM/Portable-Vergleichsvertrag erhalten; dokumentierte Marker allein sind kein Laufnachweis. Reine historische Markerprüfung nur mit aktiver Bedeutung behalten. |
| `all19DecisionCasesKeepExpectationsQuotesAndExplicitUserStatus` | SPLIT. Noch geltende genehmigte Expectations/Zitate schützen; „genau 19 damalige Fälle“ nicht ungeprüft zur ewigen Gesamtzahl machen. |
| `setProfileAdditionsCannotChangeHistoricalCasesOrInventApproval` | KEEP-UNIT/CONTRACT → `NormativeDecisionsContractTest`. Alte Erwartungen dürfen nicht unbemerkt verändert und Genehmigungen nicht erfunden werden. |
| `foreignOrAcceptedGrammarOccurrenceFails` | Für rein historischen Inventarvalidator gemeinsam mit ihm RETIRE-HISTORY. Aktuelle fehlende/duplizierte Grammatikbindung separat in Coverage-Gegenprobe erhalten. |
| `unknownDuplicateOrChangedSpecContractFails` | SPLIT. Unbekannte/duplizierte aktuelle IDs erhalten. Historische Zeilenhashänderung kein allgemeiner aktueller Fehler. |
| `changedOrMissingBuiltinSignatureFails` | MOVE-PRODUCT oder MERGE: mit tatsächlichen Kompilationen/Abfragen bzw. vorhandenen fachlichen Tests. Kein zweiter Regex-Compiler als dauerhafter Oracle. |
| `lostWeakenedOrChangedPredefinedMappingFails` | Schutzwirkung der verlorenen/geschwächten **aktuellen** Bindung erhalten; historische Ownersteps und Sourceanker abtrennen. |
| `wrongSpecialTypeScopeOrObjectUseFails` | Fachliche Scope-/Objektverwendung in Produktregressionen erhalten; nur echte doppelte synthetische Katalogprüfung entfernen. |
| `keywordContextAndFeatureChangesFail` | Reale Keyword-/Sprachprofiltests bevorzugen; die künstliche Änderung eines Java-Quelltextstrings ist nicht gleichwertig mit erfolgreicher Kompilation. Ersatz konkret nachweisen. |
| `multilineBuiltinSyntaxKeepsVersionRuleAndArgumentPositions` | Nur KEEP-UNIT, wenn der Quelltext-Signaturparser nach dem Audit noch einen echten Verbraucher hat. Bei entfallendem Parser zusammen RETIRE-HISTORY/MERGE. |
| `inventedChangedOrAcceptedTestCandidateFails` | Aktuelle erfundene/falsche Methodenbindung erhalten; vergangene `candidate`-/`accepted`-Statuslogik historisch isolieren. |
| `brokenA3PairFails` | Aktuelle Vergleichspaarvollständigkeit erhalten; reine historische Marker-/Methodentextpins nur bei weiterem Bedarf. |
| `manipulatedDecisionPacketFails` | Schutz für genehmigte aktuelle Nutzerentscheidungen und falsche Zitatbindung erhalten; historische Originalpakete nie automatisch neu schreiben. |

### 5.1 Helfer `CatalogueEvidence`

Die lokalen tatsächlichen Signaturen inventarisieren. Bekannte Methodenfamilien: `matrixPins`, `sources`, `grammar`, `specifications`, `builtins`, `predefined`, `candidates`, `pairs`, `decisions`, `decisionSources`, `languageProfile`, `signatures`.

Nicht alle Familien in eine umbenannte 1:1-Klasse kopieren. Nach Entscheidung der Testmethoden unbenutzte Methoden entfernen. Nur verbleibende eigenständige Verantwortlichkeiten erhalten Namen wie `NormativeDecisionAssertions` oder `CompilerCoverageAssertions`.

Ein neuer Helfer ist nur gerechtfertigt, wenn er mindestens einen tatsächlichen aktuellen Aufrufer hat. Für sechs unverändert benötigte Zeilen genügt eine private Methode. Keine Hierarchie `EvidenceProvider/Registry/Engine/Adapter`.

## 6. `NormEvidence` / `NormEvidenceTest`

### 6.1 Methodenbewertung

| Methode/Familie | Bewertung |
|---|---|
| `pages` | Bindung erhaltener Extraktion an PDF, Version und Extraktor. Für aktuelle Zitatverträge sinnvoll; keine eigentliche PDF-Analyse. Nur bei aktuellem Bedarf behalten. |
| `excerpt` | Kleine exakte Prüfung von Dokument, Seite, Codepoint-Bereich, Zitat-/Seitenhash. Bei verbleibenden normativen Zitaten KEEP-UNIT. |
| `paragraphs` | Im Referenzstand stark an historische Vollinventarisierung und G2-Status gebunden. Als Standardprüfung entfernen; aktuelle Vollabdeckung darf nicht durch bloss gleiche Zählung behauptet werden. |
| `crosswalk` | Historische Abschnitts-/Ownerstep-Zuordnung. RETIRE-HISTORY, sofern kein aktueller Abnahmevertrag sie benötigt. |
| `conditions` | Mischt Zitatzuordnung mit historischen Planungszuständen. Aktuelle Zitat-/ID-Konsistenz bei Bedarf extrahieren; alte `PLANNED`-/`PARTIAL`-Pflicht abtrennen. |
| `plannedCases` | Rein historische Forderung an Planungszustände. Nicht als Produktregression fortführen. |

### 6.2 Konkrete Tests

- `bothOriginalPdfsAllPagesParagraphsConditionsAndExactExcerptsStayBound`: SPLIT. Die Zahlen 2721 Paragraphen, 311 Abschnitte und 369 damalige Compilerbedingungen kennzeichnen den Bestand, nicht die heutige Compilerkorrektheit. Nur aktuelle Norm-/Zitatbindungen in ein eigenes präzises Contractprofil übernehmen; vollständiges historisches Inventar unverändert aufbewahren.
- `movedWeakenedOrWrongVersionExcerptFails`: bei verbleibendem aktuellen Excerptvalidator behalten; kleine synthetische Seiten reichen für den Unit-Test.
- `changedParagraphMissingCoverageAndFalseAcceptanceFail`: zusammen mit dem historischen Vollinventarvalidator entfernen, soweit keine aktuelle Nutzung; nicht gleichzeitig die aktuelle Coverage-Gegenprobe entfernen.
- `invalidCrosswalkFails`: mit historischer Crosswalk-Infrastruktur stilllegen, sofern nicht aktuell erforderlich.
- `incompleteOrAcceptedConditionFails`: historische Planungsfelder stilllegen; aktuelle fehlende Bedingungen/duplizierte Zitatbindung anderweitig weiterhin prüfen, falls verwendet.
- `wrongPdfHashVersionAndExtractorAreRejected`: erhalten, wenn ein aktueller Test echte Norminputs konsumiert. Nicht sinnlos einen externen Download behalten, nur damit ein unbenutzter Validator getestet werden kann.

Der Task `prepareG2Norms` wird entfernt, falls nach dem Audit kein aktiver Verbraucher bleibt. Andernfalls wird er minimal zu `prepareNormativeInputs` umbenannt und ausschliesslich an das erforderliche explizite Contractprofil gebunden. Keine Netzwerk-I/O während Gradle-Konfiguration und kein impliziter Download beim gewöhnlichen `check`.

## 7. `RuntimeReports`, `RuntimeReportsTest`, `RuntimeReportPathsTest`

Dieser Bereich enthält besonders wertvolle Gegenproben, aber auch einen ausdrücklich historischen Formatvertrag. Vor einer Zusammenlegung prüfen, ob aktive Validatoren dieselben Fehler **wirklich** erkennen.

### 7.1 Erhaltene Schutzwirkungen

- Ein Bericht mit nur `status: passed` und ansonsten leeren Daten wird abgelehnt.
- Keine fehlenden, doppelten oder falsch benannten Browser in einer erforderlichen Matrix.
- Ein Playwright-WebKit-Ergebnis darf nicht als echter Safari-Nachweis auftreten.
- Falsche Laufzeit-/Tool-/Nodeversionen und fehlende tatsächliche Resultate werden abgelehnt.
- Referenz-Rohsummen und Projektbewertungen stimmen mit ihren Details und genehmigten Ausnahmen überein.
- SDK-Aufrufe, Reihenfolge, Exitcodes und Streamnachweise stimmen mit dem verlangten Profil überein.
- Windows-/Unix-/UNC-Reportpfade werden unabhängig vom Host korrekt bewertet.

### 7.2 Konkrete Zielnamen

Vorschläge, soweit der jeweilige Validator erhalten bleibt:

| Alt | Neu |
|---|---|
| `RuntimeReportsTest` | `RuntimeReportValidationTest` |
| `RuntimeReportPathsTest` | `SdkReportPathValidationTest` |
| `RuntimeReports.report` | `RuntimeReportAssertions.validateReport` |
| `RuntimeReports.sdk` | `RuntimeReportAssertions.validateSdk` |
| `RuntimeReports.pair` | `RuntimeReportAssertions.validateRawAndAssessedPair` |
| `RuntimeReports.approvedCompatibility` | präzise genehmigte Kompatibilitätsentscheidung, ohne pauschalen „approved“-Shortcut |

Die vorhandene Methode `completePortableAndBrowserFormatPassesWithExplicitHistoricalProfile` zeigt, dass diese Java-Hilfe nicht automatisch der aktive Releasevalidator ist. Ihre Fixtures werden nicht unbesehen auf einen aktuellen Versionsstring geändert.

Wird ein historischer Reportzweig nicht mehr konsumiert und sind alle relevanten Schutzfälle durch aktuelle Node-/Java-Prüfer belegt, darf der Zweig samt Tests entfallen. Vorher mindestens einen korrekten Fall und jede relevante Mutation gegen den echten Ersatzvalidator ausführen. Nicht bloss Dateinamen oder ähnliche Assertions vergleichen.

`nativeWindowsAndUnixPathsKeepExactlyTheSameCommandContract` bleibt entweder als gleichwertiger Test des erhaltenen SDK-Validators oder als portierter Test des aktiven Ersatzes bestehen. Absolute Windows-Pfade sind nicht bloss Unix-Dateipfade mit ausgetauschten Slashes.

## 8. Daten und Berichte: was keinesfalls verloren geht

Folgende Dinge werden nicht als „Altlast“ pauschal gelöscht:

- Originalnormen bzw. deren Pins/Herkunft und unverändert erhaltene Textextraktionen.
- Genehmigte fachliche Ausnahmen und die ursprünglichen Nutzerentscheidungen.
- Positive/negative Compilerfixtures und normative Erwartungen.
- Ursprüngliche G2/P3-Abnahmeberichte und nachvollziehbare historische Commitbindungen.
- Aktuelle Korpus-/SDK-/Plattform-Berichtsformate, solange ein aktiver Verbraucher existiert.

Verwaiste **ausführbare** historische Hilfslogik darf weg, wenn die Matrix den Grund belegt. Alte Rawlogs oder Dokumente dürfen weiter auf alte FQCNs/Tasks verweisen; ihre Einordnung muss klar sein. Aktuelle Anleitungen dürfen diese alten Einstiegspunkte nicht weiter empfehlen.

## 9. Nachweis für das bewusste Entfernen

Für jede Stilllegung hält Codex fest:

1. Welches Risiko erkannte die alte Prüfung?
2. Ist dieses Risiko historischer Arbeitsstand oder aktuelles Produkt-/Releaseverhalten?
3. Wer konsumiert das geprüfte Format heute noch?
4. Bei aktuellem Risiko: welche konkret benannte Prüfung übernimmt es, mit welchem positiven und negativen Versuch?
5. Welche historischen Dateien bleiben unverändert erhalten und an welchen ursprünglichen Commit ist der alte Validator gebunden?
6. Welche aktuelle Build-/Report-/Dokumentationskante wurde entfernt oder geändert?

Damit darf das Ergebnis wesentlich kleiner sein als die alte Infrastruktur. Eine feste Zielzahl von gelöschten Tests oder Klassen gibt es nicht. Die Entscheidung richtet sich nach Schutzwirkung, nicht nach kosmetischen Reduktionsquoten.
