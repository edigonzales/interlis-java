# Ausführungsplan für Codex

## 1. Liefermodell

Arbeite in den Implementierungsphasen **V0 bis V4**. Die nächste Phase beginnt erst, wenn die gezielten Tests und die Zielkontrolle der vorherigen Phase erfolgreich abgeschlossen sind. Diese Phasenkontrolle führt Codex selbst aus.

**Kein OpenCode-Review nach V0, V1, V2 oder V3.** Erst nach der erfolgreichen letzten Implementierungsphase V4 und dem finalen Kandidatencommit folgt die unabhängige Gesamtprüfung R. Ein technischer OpenCode-/Modell-Preflight in V0 ist zulässig und erwünscht, aber kein inhaltlicher Vorabreview.

Bei Reviewerfindings korrigiert Codex den Code, prüft nur die betroffenen Verträge erneut, committet einen neuen Kandidaten und startet einen frischen unabhängigen Gesamt-Review. Bereits gültige umfangreiche Nachweise werden nicht schematisch doppelt erzeugt.

Die Spezifikation `SPECIFICATION.md`, die Evidenzentscheidungen `EVIDENCE_DISPOSITION.md` und der unabhängige Auftrag `OPENCODE_REVIEW_PROMPT.md` sind zusammen verbindlich.

## 2. Arbeitsregeln

Lies die geltenden `AGENTS.md` und die einschlägigen aktuellen Spezifikationskapitel. Der ausdrückliche Auftrag ersetzt widersprechende frühere organisatorische Vorgaben zu den drei Source-Sets und zu pauschalen Reviews nach jeder Phase. Andere Produktverträge bleiben bestehen.

Erhalte fremde Änderungen. Kein `reset --hard`, kein ungezieltes `clean`, kein Verwerfen/Stashen fremder Arbeit. Kein Push, keine Veröffentlichung, keine neue Dependency-/JDK-/Toolversion. Keine automatische globale OpenCode-Aktualisierung.

Phase, echte Kommandos, Exitcodes, relevante Inputs und Ergebnis werden in `STATUS.md`/`TEST_LEDGER.md` dokumentiert. Fachliche Implementierungstests und Schema-/Fixturetests müssen unterscheidbar bleiben. Fehlende Pflichtumgebung ist `BLOCKED`, nicht `PASS`.

Die Markdown-Dateien dieses Pakets sind der Auftrag, keine Ergebnisse. Die Vorlage darf nicht dadurch „erfüllt“ werden, dass Statusfelder ohne reale Arbeit auf erfolgreich gesetzt werden.

## 3. V0 – Inventar und konkrete Evidenzentscheidung

### 3.1 Arbeit

1. Lokalen Branch, HEAD, Dirtyzustand, Toolchain und geltende Regeln erfassen. Referenzstand der Spezifikation mit dem lokalen Stand vergleichen; nicht zurücksetzen.
2. Alle Dateien in `src/verification`, `src/verificationTest`, `src/evidenceTest` inventarisieren. Imports, Package-private-Zugriffe, `main`-Methoden, Child-FQCNs, Ressourcen und Reportpfade notieren.
3. Testmethoden samt Parameter-/MethodSource-Fällen erfassen. Keine Verzeichniszählung als Testanzahl verwenden.
4. Die Evidenzmethoden nach der Dispositionsdatei entscheiden. Dabei alle aktiven Verbraucher der JSON-/Berichtsformate prüfen, insbesondere Java- und Node-Releasevalidatoren.
5. Notwendige Produktregressionen von Tooltests trennen. `UnicodeWeightsTest`, OID-/Metadatenregressionen und die gemischte Corpus-CLI-Prüfung ausdrücklich behandeln.
6. Bestehende auftragsrelevante Nachweise auf Herkunft und Gültigkeit prüfen. Kleine Baselineprüfungen dort ausführen, wo sie einen späteren Vergleich ermöglichen. Keinen Voll-Release nur für eine Baseline starten.
7. OpenCode-Version, `run --help`, Modell-/Providerzugang und passende bestehende Reviewstarter prüfen. Noch keinen Reviewer mit einer fachlichen Prüfung beauftragen.

### 3.2 Ausgaben

`SOURCESET_INVENTORY.md`, `EVIDENCE_DECISIONS.md`, initiale `TEST_LEDGER.md` und `STATUS.md`. Darin:

- Ausgangs-SHA und untersuchte Änderungen;
- jede zu verschiebende/zu entfernende Klasse;
- methodengenaue Schutzwirkungszuordnung;
- beabsichtigte neuen Reportpfade und Taskbesitzer;
- Testzählungs-/Suite-Delta als vorläufige Rechnung;
- konkrete Risiken und jeweils kleinste aussagekräftige Gegenprobe.

### 3.3 V0-Gate

Inventar deckt alle alten Dateien/Testmethoden ab; alle strittigen Evidenzbereiche besitzen eine umsetzbare Entscheidung. Kleine vorhandene Baseline-Unit-Tests der Prozess-/Berichtprüfer sind bestanden oder ein belegter vorbestehender Defekt ist sauber abgegrenzt. Erforderliche neue Arbeiten dürfen nicht durch einen unbekannten Ausgangsfehler als erfolgreich erklärt werden.

Eine reine Bestandsphase benötigt keinen künstlichen Vollkorpuslauf. Dokumentations-/Inventarvalidierung und gezielte Baselineprüfungen sind hier angemessen.

## 4. V1 – Hostprogramme und normale Tests auslagern

### 4.1 Arbeit

1. `interlis-verification` in `settings.gradle` aufnehmen, `build.gradle` mit normalen Java-Konventionen und expliziten Dependencies anlegen.
2. Mainprogramme nach `src/main/java/guru/interlis/verification` verschieben. Imports, package-private Verbindungen, `mainClass` und Child-FQCN-Strings gemeinsam ändern.
3. Die zugehörigen Tooltests nach `src/test` verschieben.
4. Die drei direkten Produktregressionen gemäss Zuordnung ins Compiler-Testmodul zurückführen; lokale Abhängigkeiten von `LegacySourceMetadataTest` zuvor prüfen.
5. `repo.root` und nötige Unicode-Inputs an den neuen Testbesitzern konfigurieren.
6. `VerificationLayout` klein und gezielt einführen; Produktdistributionen nicht umbenennen.
7. JavaExec-Tasks und Rootdelegation auf das neue Modul schalten; CLI-lokale relative `installDist`-Abhängigkeiten vollständig qualifizieren.
8. Alte `verification`-/`verificationTest`-Source-Sets und ihre Konfiguration entfernen. `evidenceTest` bleibt nur bis V2 als klar identifizierter Übergangsbestand.
9. Bereits von diesem Schritt betroffene aktive Reportverbraucher minimal konsistent anpassen. Keine wissentlich falschen Child-Classpaths oder leeren Rootdelegationstasks hinterlassen.

### 4.2 Gezielte Tests

- `:interlis-verification:compileJava` und `:interlis-verification:compileTestJava`.
- Die normalen Tests des neuen Moduls; gegebenenfalls zuerst `HostChecksTest`, `CompilerCliProcessesTest`, `PythonFreeCheckTest` und die konkreten weiteren geänderten Testklassen selektieren.
- Gezielt die verschobenen Produktklassen, beispielsweise:

```sh
./gradlew :interlis-compiler:test \
  --tests 'guru.interlis.compiler.LegacyOidCompatibilityTest' \
  --tests 'guru.interlis.compiler.LegacySourceMetadataTest' \
  --tests 'guru.interlis.compiler.internal.UnicodeWeightsTest'
```

Die tatsächlichen Zielnamen vor Ausführung prüfen. Das Beispiel ist kein Grund, eine lokal anders erforderliche Klasse zu ignorieren.

- Kleine FQCN-/Mainmethoden-/Child-Classpath-Gegenprobe für alle umgezogenen JavaExec-Einstiege. Ein Langzeitprogramm dafür nicht vollständig starten.
- `--dry-run` für `sdkProcessCheck`, `sdkArchiveCheck`, `semanticComparison`, `platformCheck`: Taskbesitzer, Abhängigkeiten und Zyklen prüfen. Dry-run ist kein Laufzeitnachweis.

### 4.3 V1-Gate

Alle migrierten Klassen kompilieren, ausgewählte Tooltests und alle tatsächlich verschobenen Produktregressionen laufen erfolgreich. Keine umgekehrte Produktdependency. Root-Kommandos zeigen auf existierende neue Klassen. Die temporär noch alte Evidenzstruktur wird nicht bereits als final bereinigt bezeichnet.

## 5. V2 – Evidenztests fachlich zerlegen und bereinigen

### 5.1 Arbeit

1. Entscheidungen aus V0 umsetzen, nicht neue globale Löschregeln erfinden.
2. Wiederverwendbare Validatorlogik und deren kleine Gegenproben nach normalem `test` überführen; präzise Namen verwenden.
3. Aktuelle echte Repository-/Ausführungsbindungen als `compiler-contract`-Tests gruppieren.
4. Gemischte alte Testmethoden in schnelle Negativtests und eigentliche Repository-Contracts aufteilen. Methoden- statt Klassentags verwenden, wenn sonst schnelle Negativtests ausgeblendet würden.
5. Kleine synthetische Fixtures von alten kompletten G2-Berichten entkoppeln, ohne historische Reports umzuschreiben.
6. Tatsächlich historische Validatoren und anschliessend ungenutzte Helfer entfernen. Jede Entfernung in `EVIDENCE_DECISIONS.md` mit Schutzwirkungsnachweis abschliessen.
7. Nachgewiesene Doppelungen mit bestehenden aktiven Node-/Java-Validatoren zusammenführen. Deren relevante Negativfälle tatsächlich ausführen.
8. `contractTest` und Root-`compilerContractCheck` definieren; notwendige selektierte `contractAssertionsTest`-Produzenten einrichten.
9. `prepareG2Norms`/`g2AssertionTest` nur bei weiterem aktuellen Bedarf durch neutral benannte gezielte Tasks ersetzen, sonst entfernen.
10. `evidenceTest`, `g2EvidenceTest`, ihre Source-Sets, unbenutzten Dependencies und alte aktive Aufrufe entfernen. `gradle/evidence.gradle` auflösen; keine leere Kompatibilitätsdatei zurücklassen.
11. Betroffene aktuelle Vertragsdaten klein und nachvollziehbar migrieren; alte historische Originaldateien unverändert lassen.

### 5.2 Gezielte Tests

- Alle geänderten Validator-Unit-Tests, inklusive fehlendem/falschem/stalem XML, falschen Hashes, verlorenen IDs, falschen SDK-Exitcodes und Windows-/UNC-Pfaden.
- Testgruppen: `test` darf Contracttests nicht ausführen; `contractTest` muss seine konkrete Gruppe finden und darf keine leere Erfolgsmeldung liefern.
- Ein aktueller echter ausgewählter Assertionslauf samt Contractleser. Dabei positive Ausführung und negative Gegenproben gegen fehlenden, alten und manipulierten Bericht.
- Bei aktiver Normbindung: gezielte Hash-/Zitat-/Versionskontrolle; anderenfalls kein künstlicher PDF-Download.
- Alle als Ersatz benannten Produkt-/Node-Prüfungen in dem hierfür notwendigen schmalen Umfang.

### 5.3 V2-Gate

Die drei alten Source-Sets sind weg. Jede alte Evidenztestmethode ist zugeordnet. Das neue normale Unit-Profil läuft ohne Norm-PDF-/Historienprüfung; explizite aktuelle Vertragsprüfung funktioniert mit realen Inputs. Alte G2-Rohbelege und Normerwartungen wurden nicht global rebaselined. Kein verloren gegangener aktiver Schutzfall bleibt unbeantwortet.

## 6. V3 – Plattform-, Release- und Berichtsintegration

### 6.1 Arbeit

1. `PlatformProcesses` auf neue Unit-/Contract-Reportproduzenten umstellen und den Reportleser mit Fixtures testbar machen.
2. Test-/Suite-Delta aus tatsächlichen Ergebnissen abschliessen. Alle Java-/Node-Konsumenten und Fixtures mit demselben Profilvertrag aktualisieren.
3. `tools/release/evidence.cjs`, `test-fixtures.cjs`, `platform-evidence.cjs`, CI-Uploadpfade und alle weiteren tatsächlichen Verbraucher prüfen.
4. Root-Taskgraph vollständig konsistent machen; `platformCheck`/Releaseprofil erhalten die nötige `compilerContractCheck`-Kante, nicht `check`.
5. `ProfileFingerprint` und Node-Fingerprint auf die neue Quellenmenge prüfen; belegte Abweichungen minimal beheben und testen.
6. Laufzeit-/Verteilungsinventare auf Ausschluss von `interlis-verification` absichern.
7. Alle FQCN-Strings, Reportpfade, taskselektierenden Skripte und aktiven Reviewstarter/Anleitungen auf neue Pfade umstellen. Historische abgeschlossene Reviewskripte nicht als aktive Einstiege behandeln.

### 6.2 Gezielte Tests

- Neue bzw. extrahierte `PlatformProcesses`-Unit-Tests mit kleinen XML-Fixtures.
- Positive Fixture: alle erwarteten neuen Reportverzeichnisse, aktuelle Suiteidentitäten und passende Hashes.
- Negative Fixtures: nur alter Pfad vorhanden; neue Suite fehlt; alter und neuer Bericht doppelt gezählt; leerer Bericht; Hashänderung; nachträglich manipuliertes Detail; falscher Kandidat.
- Betroffene Node-Tests, insbesondere Release-Evidence-/Distribution-/Fixturetests. `artifact-comparison.test.cjs` nur zusätzlich, wenn deren Vertrag/Fixture wirklich berührt wurde.
- Java-/Node-Fingerprint-Parität am gleichen relevanten Inputbestand, plus Änderung einer neuen Verifikationsquelle als Gegenprobe.
- Gesamte relevante Taskgraphen mit `--dry-run`, besonders `check`, `compilerContractCheck`, `sdkArchiveCheck`, `platformCheck` und erforderliche Releasezweige. Falls ein Releasepreflight bereits bei Konfiguration externe Nachweise verlangt, keine Fake-Nachweise einsetzen; Untergraphen und Validatoren separat prüfen und die Grenze notieren.

### 6.3 V3-Gate

Neue Berichte werden tatsächlich gefunden; alte Berichte können kein fehlendes neues Ergebnis ersetzen. Testmengenänderung ist erklärbar. Keine Zyklen, keine Doppelzählung, kein Verifikationscode in Produkt-Dependencies. Releasevalidierung wurde nicht gelockert, nur organisatorisch konsistent umgestellt.

## 7. V4 – Kleine echte Integration, Cleanup und finaler Commit

### 7.1 Arbeit

1. Einen sauberen Zustand der betroffenen generierten Klassen/Reports herstellen, nicht globale Caches löschen.
2. Verifikation aus den neuen Quellen kompilieren und die relevante normale Testgruppe ausführen.
3. Echte Compiler-CLI-Distribution bauen und über den neuen Hostprüfer testen.
4. SDK-ZIP erzeugen und mit dem migrierten Konsumenten prüfen. Keine Verifikationsklassen/JARs in der Auslieferung.
5. LSP-Distribution erzeugen bzw. deren relevante bestehende Distributions-/Abhängigkeitstests verwenden; Protokolltests nur wiederholen, wenn deren tatsächlicher Vertrag berührt ist.
6. Das aktuelle `compilerContractCheck` einmal mit den selektierten echten Produzenten abnehmen, sofern dafür noch kein gültiger identischer Nachweis aus V2/V3 vorliegt.
7. Alle aktiven Altlasten und Dokuverweise bereinigen. `SOURCESET_INVENTORY`, `EVIDENCE_DECISIONS` und `TEST_LEDGER` abschliessen.
8. `git diff --check`, Diff-/Scope-/Secret-/Artefaktkontrolle durchführen.
9. Ausschliesslich Auftragsänderungen committen. Keine globale Stageoperation über fremde Änderungen. Beispielmessage: `refactor: extract internal verification module and simplify contract tests`.
10. `BASE_SHA`, `CANDIDATE_SHA`, `TREE_SHA` und kumulativen Diff sichern. Danach unabhängige Gesamtprüfung R starten.

### 7.2 Passende reale Einstiegskommandos

Am tatsächlichen Taskgraph prüfen; nicht alle blind wiederholen:

```sh
./gradlew :interlis-verification:test
./gradlew sdkProcessCheck
./gradlew sdkArchiveCheck
./gradlew :interlis-lsp:installDist
./gradlew compilerContractCheck
```

Die SDK-Tasks besitzen bereits Abhängigkeiten; doppelte separate Aufrufe vermeiden, wenn ein gemeinsamer Taskaufruf dieselben Produzenten genau einmal selektiert. Vollständiges `compilerCliCheck` nur bei berührtem Prozessprüfer oder fehlendem ausreichendem Nachweis; ansonsten kleiner bestehender SDK-Prozessvertrag plus gezielte Prüfer-Unit-Tests.

### 7.3 V4-Gate

VM-01 bis VM-17 sind nachgewiesen. Die gezielten realen Prozess-/Distributionsprüfungen sind erfolgreich. Kein vollständiger Browser-/Wasm-/Web-Image-/Performance-/Mehrplattform-/P3-Release wird behauptet, wenn er hier nicht fällig war und nicht lief.

Der Gesamtzustand ist committet und bereit für eine unabhängige Prüfung. **VM-18 ist bis zum tatsächlichen Review noch offen.**

## 8. Testmatrix: Mindestgegenproben und bewusste Grenzen

| ID | Frage | Kleinster sinnvoller Nachweis |
|---|---|---|
| T01 | Kompiliert der neue Main-/Test-Classpath ohne fremde Source-Sets? | explizite Compiletasks und Dependencyprüfung |
| T02 | Landen Prüfklassen im Produkt? | CLI-/LSP-Runtime-Graph plus tatsächlich erzeugte JAR-/ZIP-Inhalte |
| T03 | Gehen Rootargumente, Unicode oder Windows-Launcherpfade verloren? | Layout-/HostChecks-Unit-Tests, tatsächlicher lokaler CLI-Prozess |
| T04 | Ist der Child-FQCN nach dem Umzug noch ladbar? | kleine echte Child-Classpath-/Main-Smokeprobe, kein Langzeitlauf |
| T05 | Laufen Tooltests im neuen Modul? | neue normale Testgruppe mit echten Testzahlen |
| T06 | Bleiben die Produktregressionen erhalten? | die drei verschobenen Klassen gezielt ausführen |
| T07 | Überschneiden sich Unit-/Contractgruppen? | Discovery/Reports auf eindeutige Mengen prüfen |
| T08 | Ist eine Contractgruppe versehentlich leer? | leere/falsche Auswahl als expliziter Fehler |
| T09 | Ersetzt ein alter Bericht einen fehlenden neuen? | Fixture mit nur altem Verzeichnis muss scheitern |
| T10 | Werden zwei Suiteorte doppelt gezählt? | doppelte/fremde Reportfixture ablehnen |
| T11 | Wird XML-Manipulation erkannt? | Suite/Klasse/Methode/Hash/Skip/Fehler/Frische mutieren |
| T12 | Sind Parameterinvocations vollständig? | gezielt einen Pflichtaufruf entfernen |
| T13 | Können falsche SDK-Aufrufe gültig wirken? | Exit-/Arg-/Pfad-/Stream-Negativfixtures |
| T14 | Kann eine beliebige PASS-JSON bestehen? | leere und unvollständige Berichte ablehnen |
| T15 | Gehen aktuelle Coveragebindungen verloren? | fehlende/fremde/doppelte ID bzw. Methode ablehnen |
| T16 | Wird historische Evidenz umetikettiert? | historisches Dateiinventar vor/nach vergleichen; erlaubte aktuelle Änderungen separat |
| T17 | Sind Java-/Node-Fingerprints konsistent? | derselbe Inputbestand; Änderung neuer Verifikationsquelle |
| T18 | Ist der Taskgraph zyklusfrei und schmal? | Dry-run/Abhängigkeitskontrolle der relevanten Einstiege |
| T19 | Funktioniert SDK-Verifikation wirklich? | echter `sdkProcessCheck` und Archivkonsum |
| T20 | Entspricht jede Testentfernung ihrer Begründung? | methodengenaue Matrix plus Ersatzgegenprobe |
| T21 | Wurde versehentlich der TeaVM-Patch/Toolpin berührt? | Diff/Classpath-Konfigurationskontrolle, kein Wasm-Vollbuild nötig |
| T22 | Ist der finale Review wirklich unabhängig und modellrichtig? | Prozess-/Sessionbelege, SHA/Treebindung, eigenes Reviewerurteil |

Die Tabelle erzwingt keine neue Testklasse pro Zeile. Vorhandene geeignete Tests erweitern. Verschiedene Gegenproben dürfen in einer kleinen parametrisierten Suite liegen.

## 9. Wann breitere Tests gerechtfertigt sind

Breitere Läufe nur, wenn eine konkrete Änderung oder ein Befund sie erfordert. Beispiele: Änderung fachlicher Vergleichslogik kann die tatsächlich verglichenen Laufzeiten betreffen; Änderung eines Plattformlaunchers kann einen entsprechenden Plattformlauf erfordern.

Ein reiner Package-/Classpath-/Berichtspfadumzug verlangt dagegen nicht automatisch 347 Korpuswurzeln, sämtliche Browser, Safari, Web Image oder Langzeitbenchmarks. Genau diese Grenzen sind zu dokumentieren.

Codex und OpenCode führen keine vollständigen Korpus-/Benchmarkläufe doppelt aus. Der Reviewer prüft Rohbelege selbst und ergänzt eigene kleine Gegenproben. Eine neue Reviewrunde allein ist kein Grund, unveränderte teure Tests erneut zu starten.

## 10. R – Unabhängiger Gesamt-Review nach V4

Die operative Beschreibung steht in `OPENCODE_REVIEW_PROMPT.md`. Codex startet und überwacht den Prozess selbständig. Der Reviewer erhält den vollständigen kumulativen Diff, den Auftrag, die Test-/Evidenzentscheidungen und ursprüngliche Rohbelege.

Ablauf:

```text
V0 → V1 → V2 → V3 → V4 → geprüfter Kandidatencommit
                              ↓
                frischer OpenCode-/DeepSeek-Review
                     PASS ↙              ↘ FAIL
               Abschluss             Codex korrigiert
                                          ↓
                                 betroffene Tests
                                          ↓
                                  neuer Commit
                                          ↓
                             frischer Gesamt-Review
```

`BLOCKED` bedeutet weder PASS noch „Review wird später automatisch erledigt“. Codex löst behebbare lokale Probleme im erteilten Scope, andernfalls berichtet es den konkreten Blocker. Kein stiller Modellwechsel und kein erfundener Erfolg.

Nach einem PASS keine unbeurteilten Produkt- oder Dokumentationsänderungen committen. Das finale Reviewerurteil bleibt ausserhalb des Kandidatencommits, damit nicht allein durch dessen Aufnahme ein neuer ungeprüfter Commit entsteht.
