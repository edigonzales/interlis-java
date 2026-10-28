# Ausführbarer Startprompt für Codex: `interlis-java` G2 + Python-Ablösung

> **Diesen Prompt als ausdrücklichen Implementierungsauftrag an Codex übergeben.** Er setzt voraus, dass `INTERLIS_G2_UMSETZUNGSPLAN.md` unter `docs/g2/EXECUTION_PLAN.md` und `OPENCODE_REVIEW_PROMPT_INTERLIS_G2.md` unter `docs/g2/OPENCODE_REVIEW_PROMPT.md` im Repository liegt. Alle folgenden Dateipfade beziehen sich auf den Root von `https://github.com/edigonzales/interlis-java`.

Du bist **Codex, der verantwortliche Implementierungsagent**. Ziel ist die vollständige, korrekte **INTERLIS-2.3/2.4-Compilersemantik (G2 / WP-20–27)**. Daneben ist die **vollständige Entfernung aller Python-Dateien und Python-Laufzeitabhängigkeiten** aus dem aktiven Repository zwingend. Du hast den Auftrag, **konkreten Code und echte Tests zu liefern**, nicht noch einmal einen umfassenden neuen Projektplan oder ein weiteres Inventarsystem zu entwerfen.

**Massgeblicher Ablauf:** `docs/g2/EXECUTION_PLAN.md`. Dieser neue Nutzerauftrag ersetzt hinsichtlich **Arbeitsreihenfolge, Reviewmodell und Prüfungstakt** die alte A–I-Fortsetzung (`docs/evidence/stabilization/g2-completion-plan.md`). Fachliche Anforderungen, Originalnormen, Spezifikationsverträge, `AGENTS.md`, historische Rohberichte, gepinnte Referenzen und prohibierte Erwartungslockerungen bleiben gültig. P3–P5 sind nicht automatisch beauftragt. Schütze alle fremden uncommitteten Änderungen.

## 1. Arbeitsprinzipien, gegen die du niemals verstösst

1. **Eine kleine fachliche Regel je Ticket.** Eine neue Test-/Evidenzinfrastruktur ist nur zulässig, wenn ein aktueller konkreter Fehlgrün-Pfad sie notwendig macht. Bevorzuge kleine vorhandene `.ili`-Fixtures wie bei `ili2c`, aber prüfe anschliessend **tiefe öffentliche Semantik** wie bei unseren bestehenden JUnit-Verträgen.
2. **Red → Green → Regression → Commit → unabhängiger Review.** Keine Behauptung „fertig“ allein aufgrund eines grünen Builds, einer gezählten Testmethode oder eines Inventarlinks.
3. **Öffentliche Verhaltensverträge** prüfen: `CompilationOutcome`, `CompiledSchema`, diagnostische Gründe und Source-/UTF-16-Bereiche, Query-Bindungen, Symbole, Typrelationen, Vererbung, effektive fachliche Reihenfolgen. Relevant ist fachlich beobachtbares Verhalten.
4. **Keine versteckten Änderungen der Wahrheit:** Kein Testfall, Suite-Pin, XFAIL, Expected-Diagnostic, Golden, Normzitat oder Conformance-Filter wird zur Erreichung eines grünen Ergebnisses still angepasst. Normkonflikte dem Nutzer mit Originalquelle, Referenzbeleg und konkretem Gegentest vorlegen.
5. **Python ist zu eliminieren, nicht zu verstecken.** Kein Python-Skript hinter einem Java-, Node-, Shell- oder Gradle-Wrapper; nach Phase 0b gibt es keine `.py`-Dateien und keine Python-Pflicht im gesamten aktiven Entwicklungs-/Testpfad.
6. **Conformance nicht nachprogrammieren.** `interlis-compiler-testsuite` hat bereits `InterlisConformance.java`. Verwende den **identischen gepinnten externen Runner** auch nach der Python-Migration. Nur die zusätzlichen projektspezifischen **18 normativen Ausnahmeprüfungen** brauchen einen kleinen eigenen Java-Validator.
7. **Unabhängigkeit ernst nehmen:** Nach jeder fachlich abgeschlossenen Änderung **lokalen Commit erstellen**, dann OpenCode/DeepSeek Flash 1017 in einem **frischen Kontext und isolierten Worktree auf dem vollen Commit-SHA** prüfen lassen. OpenCode muss echte Tests und mindestens eine **selbst entworfene Gegenprobe** ausführen. Du darfst eine eigene Prüfung nicht als unabhängige Abnahme deklarieren.
8. **Kleine Diffs, ehrliche Resultate.** Kein unnötiger Refactor von `GraphBuilder` oder `SyntaxTreeBuilder`; keine ungetestete neue Architektur. Nicht ausgeführte Pflichtprüfungen als `NOT_EXECUTED` inklusive Grund. Ein externer `ili2c`-/`ilic-fork`-Crash ist kein fachlich passender Reject.
9. **Keine unendlichen Review-/Dokumentationsschleifen.** Bei `NACHARBEIT` nur konkrete Findings mit neuem reproduzierendem Test beheben, erneut committen, erneut unabhängig prüfen. Status auf maximal einer kurzen Arbeitskarte und einer knappen Queue pflegen.
10. **Kein Hintergrundversprechen und keine erfundenen Toolaufrufe.** Wenn OpenCode in deiner tatsächlichen Umgebung nicht verfügbar/konfiguriert ist, den konkreten Commit und einen unmittelbar ausführbaren Reviewauftrag ausgeben und den Status `AWAITING_INDEPENDENT_REVIEW` setzen. Nicht darüber hinweggehen.

## 2. Einstieg: nur die notwendigen Informationen lesen

1. Im aktuellen Repository `pwd`, `git status --short`, `git rev-parse HEAD`, Gradle-Tasks, JDK und vorhandene Artefakte prüfen. Fremde Änderungen erhalten. Der zuvor untersuchte Commit war `29f079d558ac8acca411f9f2975e85d537672b10`; wenn der tatsächliche Stand anders ist, aktuelle Beobachtungen verwenden.
2. Lies `AGENTS.md`, `docs/g2/EXECUTION_PLAN.md`, `docs/implementation-status.md` (aktuellen Teil), `docs/evidence/stabilization/phase-a-dashboard.md`, `docs/evidence/stabilization/g2-reviews/A2-R3-review-2026-10-28.md`, `docs/evidence/stabilization/g2-normative-decision-packet-2026-10-28.md`. Lies danach **nur** die für das erste Ticket relevanten Fachkapitel aus `docs/spec/`.
3. Den historischen G2-Ausführungsplan und die Agenten-Einstiege **minimal** auf `docs/g2/EXECUTION_PLAN.md` als nun aktiven Arbeitsablauf verweisen lassen. **Keine** rückwirkenden Änderungen an historischen Reviews und eingefrorenen Testresultaten.
4. Eine einzige knappe `docs/g2/WORK_QUEUE.md` mit Ticket-ID, Status, Voraussetzung, Fachregel, Testnachweis, Review-SHA und nächstem konkreten Schritt anlegen. Alle früher nur geplanten Regeln bleiben offen; bloss `CANDIDATE_ONLY` ist **nicht** verifiziert. Keine neue grosse Norm-/PDF-/Testinventur.
5. Vor dem ersten Fix nur die betroffenen Test- und Produktivdateien lesen: `interlis-compiler/src/main/antlr/InterlisLexer.g4`, `InterlisParser.g4`, `AntlrSyntaxService.java`, `SyntaxTreeBuilder.java`, `LexicalRecoveryContractTest.java` sowie die vorhandene reproduzierende Fixture. Tatsächliche Methoden/Versionen prüfen, keine Klassennamen erraten.

## 3. Sofort umsetzen: erster fachlicher Pilotfix `G2-LEX-COMMENT-OUTER`

**Problem:** `testdata/g2/a2-unterminated-nested-comment.ili` enthält einen äusseren, nach einem geschlossenen inneren Block noch offenen Kommentar. Laut bisheriger Statusdokumentation akzeptiert der eigene Compiler diesen Fall fälschlich. Dagegen prüft der bestehende `LexicalRecoveryContractTest` im Negativteil nur den einfachen offenen Kommentar. Vor der Änderung Ist-Verhalten selbst reproduzieren, nicht ausschliesslich den Bericht übernehmen.

**Geforderte Fälle (INTERLIS 2.3 und 2.4):**

- Gültige zwei- und mehrstufig geschachtelte Blockkommentare bleiben gültig; Quelltextrekonstruktion, Trivia und UTF-16-Bereiche bleiben exakt.
- Einfacher offener Blockkommentar und offener **äusserer** Kommentar trotz geschlossenem inneren Block werden mit relevanter Syntax-/Lexerdiagnose abgelehnt.
- Offener innerer Kommentar wird separat geprüft, ebenso nachfolgende unabhängige Deklarationen, soweit der konkrete Recoveryvertrag dies erlaubt.
- `ParseMode.STRICT` scheitert korrekt. `ParseMode.EDITOR` darf eine partielle Syntaxsicht anbieten, muss den Fehler jedoch sichtbar machen; kein gültiges `CompiledSchema` bei Fehler im angeforderten Closure.
- Eine vorgegebene positive Kontrolle, in der lediglich das fehlende `*/` korrekt ergänzt wurde, wird angenommen. Teste nicht nur `diagnostics().isEmpty()`, sondern öffentlichen Compile-/Gate-Effekt.

**Arbeitsweise:**

1. Erste neue Negativprobe **vor** der Produktionsänderung ausführen und das beobachtete `RED` beweisen.
2. Falls strittig die exakten gepinnten 2.3-/2.4-Normstellen nachschlagen; keine Regel aus einem alleinigen `ili2c`-Resultat ableiten.
3. Den eigentlichen Fehler **minimal** in der passenden Lexer-/Syntaxschicht beheben. Keine allgemeine neue Parser-Architektur.
4. Positive/negative JUnit-Regressionen und öffentliche Ergebniskontrollen dauerhaft einchecken.
5. Betroffene schnelle JVM-Tests und **regelbezogene** Conformance-Fälle ausführen; bei einer breit wirkenden Lexer-/Parseränderung auch die ganze gepinnte Suite. Vorhandene XFAIL-/Baseline-Dateien bleiben unverändert. Plattformspezifische Pflichten nach dem risikobasierten Plan; fällige, aber technisch nicht ausgeführte Läufe benennen.
6. `git diff --check` und tatsächlichen Teststatus dokumentieren. **Vor dem Commit** `docs/g2/review/G2-LEX-COMMENT-OUTER-request.md` mit `base_sha`, Normstelle, Fixture, Kommandos/Exitcodes und Reportorten (noch ohne eigenen `head_sha`) erstellen; **Fix, Tests und Reviewantrag gemeinsam lokal committen**. **Nach dem Commit** `git rev-parse HEAD` auslesen und den vollständigen `head_sha` im ignorierten `build/review/G2-LEX-COMMENT-OUTER/handoff.yaml` oder als separate Übergabenachricht erfassen. Niemals den Commit-SHA in den Commit selbst hineinschreiben.
7. Review in OpenCode mit `docs/g2/OPENCODE_REVIEW_PROMPT.md` beauftragen; mindestens eine **nicht von dir formulierte** neue Lexer-/Recovery-Gegenprobe verlangen. Auf `ABGENOMMEN` für **denselben SHA** bestehen; andernfalls gezielt nacharbeiten und neuen SHA erneut prüfen.

**Phase 0a ist nicht abgeschlossen, solange kein unabhängiger Review des Pilotfixes vorliegt.** Dieser Pilotfix dient gerade dazu, die neue Qualitätskette an einem echten Compilerdefekt zu beweisen.

## 4. Direkt nach Pilotabnahme: Phase 0b – Python vollständig entfernen

Erstelle **keinen 1:1-Port aller Skripte**. Führe genau **einmal** `git ls-files '*.py'` aus und führe eine **kurze vollständige Migrationsliste** mit `REUSE_EXTERNAL`, `PORT_MINIMAL`, `REPLACE_WITH_TEST`, `FREEZE_ARCHIVE` oder `DELETE_REDUNDANT`. Jede Datei benötigt nachweislich einen Zielzustand und einen Verwendungsnachweis. Bisheriger Stand: 36 Dateien; verifiziere aktuelle Zahl erneut.

Arbeite in folgender, jeweils separat zu committender und unabhängig zu prüfender Reihenfolge:

### `G2-JAVA-TOOLS-01` – Direkter Java-Conformance-Runner und normative Zusatzkontrollen

1. Lies `tools/reference/conformance_check.py`, `test_conformance_check.py`, `docs/evidence/conformance/normative-expectations.json`, `baseline.json`, `docs/evidence/conformance/README.md` und `build.gradle`. Prüfe exakt, welche Funktion der externe Java-Runner bereits erfüllt.
2. Pin: `interlis-compiler-testsuite` Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`; bisher archivierter SHA-256 `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe`. **Nicht still aktualisieren.** Die externe Suite **direkt** aus Java/Gradle ausführen (z. B. Gradle-`Exec`/Java-Toolchain), keinen neuen Testfall-Runner bauen.
3. `conformanceCheck` muss exakt die fachlich gleiche 2.3/2.4-Auswahl und dieselben Roh-/Projektergebnisse bewahren. Der aktuelle dokumentierte Projektstand: 593 ausgewählt, 574 PASS, 19 XFAIL – **historisch**, frisch verifizieren.
4. Portiere nur unsere **zusätzliche** Normausnahmeprüfung minimal nach Java/JUnit: exakt 18 IDs, Original- und Positiv-/Negativkontrollen, Quellenpins, Exitcodes, strukturierte Diagnosesignaturen, keine internen Fehler als fachliche Rejection. Der separate INTERLIS-1-Profilfall bleibt getrennt. Der externe Runner verwaltet weiterhin normale PASS/XFAIL/FAIL/XPASS/ERROR-Zustände.
5. Nutze kleine, negative Canarys: fehlende/duplizierte Suite-ID, unerwartete XPASS-Bewertung, verändertem Quellhash, fehlende Kontrolle, Systemabsturz oder fehlerhafter Exitcode muss den Build ablehnen. Reproduzierbare alte Python-Fehlerpfade prüfen.
6. Gleichzeitiger Alt-/Neu-Prozessvergleich auf identischen Quellen, danach Python-Dateien des bereits abgenommenen Umfangs löschen und direkte Gradle-Einstiege wiederherstellen. Alles selbst testen und unabhängig prüfen lassen.

### `G2-JAVA-TOOLS-02` – Nur notwendige Evidenzverträge nach Java

- `g2_evidence.py`, `g2_inventory.py`, `g2_a3_pairs.py`, `g2_test_candidates.py` sowie deren Python-Tests funktional klassifizieren.
- Laufend benötigte Snapshot-, Hash-, JUnit-XML- und Assertion-Verträge mit kleinen Java-/JUnit-Checks erhalten; **keine neue allgemeine Evidence-Plattform**.
- `g2EvidenceTest` als ausführbares Gradle-Gate erhalten. Negative Tests für **falsche Suite, falsche Klasse bei gleichem Methodennamen, falsche/fehlende/alte/geskippte XML-Reports, gebrochene SHA-Bindung**.
- Historische G2-R3-Reviewartefakte/Hashes nicht durch die Migration heimlich ändern.

### `G2-JAVA-TOOLS-03` – Norminventare gezielt bewahren, Generatoren entfernen

- Die alten Python-Norm-, Grammatik-, Builtin-, Predefined-, Spec- und Testkandidaten-Generatoren **nicht mechanisch übersetzen**.
- Noch benötigte fachliche Invarianten als Java-Tests erhalten. Einmalig erzeugte und gepinnte Norminventare dürfen als unveränderliche archivalische Eingaben bleiben, **wenn** der Reviewer bestätigt, dass keine aktuelle Reproduzierbarkeits- und Normprüfung verloren geht.
- Wenn PDFs neu ausgelesen werden müssen, das alte `pypdf`-Text-/Codepoint-/Offsetprofil nicht fälschlich als zu PDFBox kompatibel behandeln. Keine stillen Änderungen der Normzitate, Referenzbewertungen oder Ausnahmen.

### `G2-JAVA-TOOLS-04` – SDK, reale Modelle, Benchmarks, übrige Werkzeuge

- `geo_corpus.py`, `generate_text_weights.py`, `benchmark_jvm.py`, `probe_wasm_exit.py`, `tools/sdk/check_distribution.py` und alle restlichen `.py`-Dateien klassifizieren und ihren **aktiven** Zweck mit Java/Gradle/JUnit abdecken oder nach belegter Redundanz entfernen.
- Bestehendes Node bleibt für reale Browser-/WASM-Verträge erlaubt, darf jedoch keine versteckte Python-Orchestrierung übernehmen.
- Bestehende Tasknamen (`sdkProcessCheck`, `geoCorpus`, `jvmBenchmark` usw.) nicht durch Fake-PASS oder dauerhaftes Skip ersetzen; Test- und Fehlerverhalten erhalten.

### `G2-JAVA-TOOLS-05` – Python-free-Gate

- **Null** getrackte `.py`-Dateien und **null** notwendige aktive Python-/pip-Aufrufe in Gradle, CI, Hilfsskripten und Reviewanleitungen. Historische Markdown-Nachweise dürfen frühere Python-Kommandos zitieren, ohne aktive Anleitungen zu sein.
- `pythonFreeCheck` als neuer Gradle-Task implementieren und sinnvoll in `check` integrieren; negative Canary durch beabsichtigten Python-Datei-/Taskaufruf.
- Alle Python-ersetzten Pfade **wirklich** in Python-freier Laufzeitumgebung ausführen, einschliesslich `check`, `conformanceCheck`, `conformanceWrapperTest`, `g2EvidenceTest`, `sdkProcessCheck`, aktive Corpus-/Benchmark-/Referenzprüfungen. Toolchains JDK/Node/Browser gesondert bereitstellen und dokumentieren.
- Frischen unabhängigen **P0b-Gesamtreview** auf dem exakten Commit bestehen. **Ohne dieses Gate nicht zu Phase 1 übergehen.**

### Grundsatz bei allen Python-Tickets

Nicht nach Datei-/Zeilenanzahl portieren. **Alte wirkliche Fachsemantik und Fehlerpfade erhalten, unnötige Mechanik entfernen.** Jede abgebaute Python-Funktion hat einen nachgewiesenen Java-Ersatz, einen vorhandenen gemeinsam genutzten Runner, eine geprüfte Testabdeckung oder eine ausdrücklich begründete Entbehrlichkeit. Keine historische Nachweisquelle ohne Grund löschen. Keine Änderung der ursprünglichen 18 Normabweichungen zur grünen Migration.

## 5. Danach die fachlichen Phasen 1–4 ohne neue Grossplanung durchführen

| Phase | Auftragsfamilien | Nur nach vollständiger Abnahme weiter |
|---|---|---|
| **1 – Sprachfundament** | Predefined-Inhalte (`SurfaceEdge`, `SurfaceBoundary`, `LineGeometry`), Binding/Imports/Syntax/Recovery, Typen/Units/OID/Geometrie, Vererbung/Assoziationen/Rollen/effektive Reihenfolge (B–D) | Öffentliche positive/negative Regeltests; Conformance, Portable/Browser-Gates; neuer unabhängiger Phasenreview |
| **2 – Erweiterte Semantik** | Objektpfade, 21 Builtins, Constraints, Views, Übersetzungen/GENERIC/DEFERRED, Grafik/Metadaten (E–F) | Exakte Targets, TypeRelations und Reihenfolgen, nicht nur Compile-Erfolg; neuer Phasenreview |
| **3 – Overlays/Modelle** | Echte immutable Translation-/CRS-/Metaattribut-Overlays statt Fingerprint-Platzhalter; Resolver mit Offline, Pins, Locks, transitiven Abhängigkeiten (G–H) | Fresh/Reuse, Overlay-Invalidation und echte Import-Closures; neuer Phasenreview |
| **4 – G2-Schluss** | Alle offenen Normbedingungen/Anforderungen, A3-Semantikvergleich, reale gepinnte 2.3/2.4-Closures, 18 Normentscheidungen, vollständige Plattformmatrix (I) | Nur Nutzer kann offene Normprofile freigeben; OpenCode prüft abschliessend den **exakten** Gesamt-SHA |

Pro Fachregel zuerst Red-Test und minimaler Fix. Bei unklaren Referenzabweichungen dürfen optional `ili2c`, `ilic-fork` und `interlis-java` denselben gepinnten Fall prüfen. Dies ist **kein Mehrheitsentscheid über die Norm**; interne Fehler und Timeouts separat kennzeichnen. `ilic-fork` ist **keine** harte Laufzeitabhängigkeit jedes Tickets.

## 6. Was du nach **jedem** Ticket ablieferst

Eine kompakte Reviewanforderung in `docs/g2/review/<TICKET>-request.md`, bestehend aus:

- Ticket-ID, fachliche Normstellen/Anforderungen und erwarteter öffentlich beobachtbarer Vertrag; bei Python-Ticket alte/ersetzte Funktion und Grund für die gewählte Migrationsklasse.
- Im **versionierten Request** `base_sha`, betroffene Dateien und stabile Testangaben. Den **vollen `head_sha` erst nach dem Implementierungscommit** in einem nichtversionierten Handoff unter `build/review/<TICKET>/handoff.yaml` oder der Übergabenachricht erfassen. Diese Trennung verhindert einen zirkulären Git-SHA.
- Originäre `.ili`-Quelle und neue Gegenfälle oder identische Alt-/Neu-Werkzeuginputs mit Pins.
- **Echte** Testkommandos, Exitcodes, neue Rohberichtpfade/Hashes, klar getrennte `NOT_EXECUTED`-Pflichtprüfungen.
- Unveränderte Referenz-/Norm-/XFAIL-Stände, offene Entscheidungen und nicht geschlossene Folgeprobleme.
- Exakte Anweisung zur unabhängigen Prüfung mit `docs/g2/OPENCODE_REVIEW_PROMPT.md`, isoliertem Worktree und tatsächlicher OpenCode-provider/model-Konfiguration.

Reviewerurteile: `ABGENOMMEN`, `NACHARBEIT`, `WARTET_AUF_NORMFREIGABE`, `NICHT_PRUEFBAR`. Nur **`ABGENOMMEN` des letzten geprüften SHA** schliesst ein Ticket. Ein reiner Reviewbericht ohne eigenen erfolgreichen Prüflauf ist keine Abnahme. Am Ende jeder Phase zusätzlich ein frischer Review aller Wechselwirkungen und G2-Gates.

**Wenn automatische OpenCode-Ausführung fehlt:** Beende den konkreten Implementierungsschritt mit Commit und einem vollständigen Reviewauftrag; behaupte keine Abnahme, gehe nicht zur nächsten Phase über. Wenn OpenCode verfügbar und ausdrücklich eingerichtet ist, führe die Übergabe selbständig durch. Nutze deine Zeit für echte Compilerarbeit, nicht für weitere historische Berichte.

## 7. Deine unmittelbare nächste Aktion

**Jetzt den beschriebenen Pilotfehler `G2-LEX-COMMENT-OUTER` reproduzieren, als dauerhaften JUnit-Fehlertest erfassen, minimal korrigieren, relevante Tests wirklich ausführen und einen überprüfbaren Commit samt OpenCode-Reviewanforderung liefern.** Danach bei unabhängigem `ABGENOMMEN` die Python-Migration `G2-JAVA-TOOLS-01` starten. Kein alternativer Gesamtplan und kein erneutes 1'476-Fälle-Inventar.
