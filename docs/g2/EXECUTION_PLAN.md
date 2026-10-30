# INTERLIS Java – G2-Umsetzungsplan mit unabhängiger Abnahme

**Version 3.0 · 29. Oktober 2026 · Verbindliche Vorlage für Codex und OpenCode / DeepSeek Flash 1017**  
Repository: [`edigonzales/interlis-java`](https://github.com/edigonzales/interlis-java)  
Analysierte Ausgangsbasis: `29f079d558ac8acca411f9f2975e85d537672b10`. Der tatsächliche Arbeitsstand ist beim Start neu festzustellen.  
Verfahrensvorbild: [`gretl-runtime-umsetzungsplan.md`](https://github.com/edigonzales/gretl-runtime/blob/main/docs/implementation/project-subprojects/gretl-runtime-umsetzungsplan.md).

**Auftrag:** Den Compiler für **INTERLIS 2.3 und 2.4** fachlich korrekt bis zum bestehenden **G2 / WP-20–27** abschliessen und **sämtlichen aktiven Python-Code samt Python-Laufzeitabhängigkeiten aus dem Repository entfernen**. Codex implementiert; ein unabhängiger OpenCode-Agent prüft. Der nächste abnahmeabhängige Lieferabschnitt beginnt **erst nach `PASS`**. Ein grüner Build oder eine unbestätigte Selbstabnahme genügt nicht.

Dieser Plan ändert bewusst die **Arbeits- und Revieworganisation**, nicht die fachlichen Anforderungen. Das frühere starre A–I-Ausführungsverfahren in `docs/evidence/stabilization/g2-completion-plan.md` wird für diesen neuen Auftrag abgelöst; bestehende Normbezüge, Anforderungs-IDs, Originalfälle, Sicherheits- und Architekturverträge sowie historische Berichte bleiben erhalten. Ein Nutzerentscheid ist erforderlich, wenn eine fachliche Normerwartung, Referenzausnahme oder ein verbindlicher Scope verändert werden soll. **P3-Release-/Legacy-CLI-Abnahme, P4-Transfer/Validierung und P5-Datenbanken sind nicht Bestandteil dieses Auftrags.** Ihre bereits vorhandenen G2-relevanten Verbraucherregressionen bleiben bestehen.

## 1. Warum der Prozess wie bei GRETL aufgebaut ist

Das erfolgreiche GRETL-Verfahren ist die **einzige organisatorische Vorlage**:

- **Agent A / Codex:** Implementiert den beauftragten Umfang, liefert fachlich aussagekräftige Tests, führt die Pflichtprüfungen aus und erstellt einen **lokalen Kandidatencommit**.
- **Agent B / OpenCode mit DeepSeek Flash 1017:** Prüft in einem **frischen Kontext und sauberen separaten Worktree** ausschliesslich den konkreten Commit; liest Code, Norm und Testassertions selbst, führt die erforderlichen Prüfungen aus und entwickelt eigene Gegenproben. Er verändert den Kandidaten nicht.
- **Genau drei Reviewurteile:** `PASS`, `FAIL`, `BLOCKED`. Bei `FAIL` oder `BLOCKED` gibt es **keine Freigabe** des betroffenen Lieferabschnitts. Nach Korrektur folgt ein **neuer Commit und Review**.
- **Kennzeichnung:** Alle `AC...`-IDs sind **Abnahmekriterien dieses G2-Ausführungsplans** und nicht mit den ursprünglichen Produktphasen P0–P5 oder historischen K1–K6-Arbeitspaketen zu verwechseln.
- **Keine Doppelreviews:** Es gibt **einen unabhängigen Review je fachlich geschlossenem Lieferabschnitt**. Bei der **letzten Lieferung einer regulären Phase (ab 0b)** gehören die vollständigen Phasengates und die kumulative Prüfung aller bisherigen Lieferungen **zum selben Review**. Für den vorgeschalteten Pilot `L0A` gelten nur die expliziten Pilotkriterien. Ein zweiter rein administrativer Phasenreview entfällt.

`interlis-java` ist sprachlich wesentlich breiter als das aktuelle GRETL-Verbesserungsvorhaben. Darum sind seine Fachphasen in **kleinere, aber zusammenhängende Lieferabschnitte** aufgeteilt. Diese sind grösser als Einzelregeln und kleiner als eine gesamte Compilerphase. Ein einzelner JUnit-Fix innerhalb eines Lieferabschnitts benötigt nicht automatisch eine eigene OpenCode-Abnahme.

### 1.1 Verbindlicher Zyklus für jeden Lieferabschnitt

1. **Basis:** Prüfe `git status`, `git rev-parse HEAD`, Toolchain und die `PASS`-Abnahme des Vorgängers. Erhalte fremde Änderungen. Lies `AGENTS.md`, diesen Plan, die einschlägigen Spezifikationskapitel und die relevanten Originalnormen.
2. **Umfang:** Benenne die konkreten Kriterien-IDs aus den Tabellen unten und die betroffenen bisherigen Anforderungs-/Norm-IDs. Vorhandene `PLANNED`-/`CANDIDATE_ONLY`-Einträge gelten nicht als Abnahmen. Zusätzliche Funde werden als reproduzierbare Folgefälle dokumentiert; das Paket wird nicht unkontrolliert ausgeweitet.
3. **Implementieren:** Bei einem Compilerdefekt zuerst ein tatsächlich fehlschlagender Test (**RED**), dann kleinste korrekte Codeänderung (**GREEN**). Positiv-, Negativ- und soweit nötig öffentliche Metamodell-/Query-Assertions sowie relevante Varianten. Bei einer Python-Ablösung Alt-/Neu-Parität und negative Harness-Tests.
4. **Prüfen:** Während der Entwicklung gezielt. Vor der Übergabe alle **für diesen Lieferabschnitt** festgelegten Prüfungen frisch ausführen; bei der letzten Lieferung einer regulären Phase ab 0b zusätzlich das **vollständige Phasengate** (für Pilot `L0A` nur dessen festgelegte Checks). Keine übersprungenen Pflichtprüfungen oder manipulierten Erwartungen.
5. **Commit:** `git diff --check`, Code, Tests und einen **kurzen Implementierungsbericht** committen. Danach den **vollen SHA** mit `git rev-parse HEAD` ermitteln. Den Commit-SHA **nicht in denselben Commit hineinschreiben**.
6. **Handoff:** Dem Reviewer `LIEFERABSCHNITT`, `BASE_SHA`, `CANDIDATE_SHA`, die Kriterien-IDs, den Berichtsordner und den Reviewprompt geben. Liegt keine unabhängige OpenCode-Ausführung vor, lautet der Stand `AWAITING_REVIEW`; Codex darf kein `PASS` behaupten.
7. **OpenCode:** Eigenständig prüfen und **mindestens eine neue fachlich relevante Gegenprobe je risikotragendem Lieferabschnitt** ausführen. Bei `FAIL` konkrete Findings, bei `BLOCKED` konkrete fehlende Voraussetzung oder Nutzerentscheidung nennen.
8. **Nacharbeit:** Codex behebt ausschliesslich die begründeten Findings im selben Lieferabschnitt, committet einen neuen SHA und lässt ihn erneut prüfen. Erst ein `PASS` auf den **aktuellen Kandidaten-SHA** gibt den nächsten Abschnitt frei.

**Zustände:** `PLANNED`, `IN_PROGRESS`, `AWAITING_REVIEW`, `PASS`, `FAIL`, `BLOCKED`. `BLOCKED` wird zusätzlich begründet (`NORM_DECISION`, `INFRA`, `MISSING_EVIDENCE` usw.), ist aber **kein viertes Reviewurteil**. Bei einer Normentscheidung dürfen unabhängig umsetzbare Aufgaben innerhalb der aktuellen Phase weiterbearbeitet werden; **die betroffene Lieferung und die nächste Phase bleiben gesperrt**, bis die Abnahme vollständig möglich ist.

### 1.2 Was die drei Urteile bedeuten

| Urteil | Verbindliche Bedeutung | Konsequenz |
|---|---|---|
| **PASS** | Alle Kriterien dieses Lieferabschnitts sind am exakten SHA belegt; Pflichtprüfungen ausgeführt und erfolgreich; kein offenes Finding gegen ein Muss-Kriterium | Abschnitt freigeben; beim letzten Abschnitt zugleich Phasen-PASS |
| **FAIL** | Mindestens ein Kriterium ist nachweislich verletzt: Compilerfehler, falsch akzeptierter Fall, verlorene Prüfung, Schein-PASS, abgeschwächte Erwartung usw. | Innerhalb desselben Abschnitts korrigieren; neuen SHA erneut prüfen |
| **BLOCKED** | Ein erforderlicher Nachweis ist nicht möglich oder eine verbindliche Norm-/Nutzerentscheidung fehlt; ein fachliches PASS lässt sich nicht belastbar erteilen | Voraussetzung herstellen oder Nutzerentscheidung einholen; bis dahin keine Freigabe |

Nur die ausdrücklich **ausserhalb des beauftragten Umfangs** liegenden Leistungen (etwa P4/P5) dürfen als nicht erforderlich gelten. Ein vertraglich vorgeschriebener Browser-, Referenz-, Test- oder Modelllauf darf nicht nachträglich als „optional“ deklariert werden, um `PASS` zu erzielen.

## 2. Feststehende Teststrategie – vier Ebenen

Wir übernehmen aus [`ili2c`](https://github.com/claeis/ili2c) dessen kleinen verständlichen `.ili`-Regressionsfälle und aus [`ilic-fork`](https://github.com/edigonzales/ilic-fork) die Trennung zwischen gemeinsamen Conformance-Fällen und lokalen Komponententests. Unsere stärkeren **öffentlichen Semantik-, Diagnose-, Snapshot- und Portabilitätsprüfungen** bleiben erhalten.

| Ebene | Auftrag und fachliches Orakel | Ausführung |
|---|---|---|
| **T1 – JUnit-Vertragstests** | Reale kleine INTERLIS-Modelle; gültig/ungültig; korrektes `CompiledSchema`, Symbolziel, Typrelation, Vererbung, Transferreihenfolge, Diagnosen und Source-Ranges über öffentliche APIs | Bei jeder betroffenen Implementierungsänderung; gezielte Tests zuerst |
| **T2 – Gemeinsame Conformance** | Genau der **externe** [`interlis-compiler-testsuite`](https://codeberg.org/edigonzales/interlis-compiler-testsuite)-Runner `InterlisConformance.java`; festgelegte Fall-IDs und Rohklassifikation, getrennt davon unsere strengen Zusatzprüfungen zu normativen Ausnahmen | Gezielte Fälle während der Arbeit; vollständiger eingefrorener Lauf an Phasengates und bei breit wirkenden Änderungen |
| **T3 – Portable/Integrationsverträge** | JVM/TeaVM-JS/WasmGC, Node, Chromium/Firefox/WebKit/**echtes Safari**, Web Image sowie betroffene CLI-/SDK-/LSP-/Diagramm-Verbraucher | Betroffene Verträge gezielt; **vollständig bei jeder Phasenabnahme** |
| **T4 – Reale Modell-Closures** | Gepinnte echte 2.3-/2.4-Modelle mit transitiven Abhängigkeiten, Offline/Pins/Locks und öffentlichen Schema-Assertions; nicht nur Exitcode 0 | Beginn in Phase 3, **vollständiges Pflichtgate für G2-FINAL** |

**Gemeinsame Conformance-Basis:** In beiden neuen Compilerprojekten ist der Runner bereits Java. Der in `interlis-java` eingesetzte externe Suite-Commit lautet `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`; die archivierte Download-Prüfsumme lautet `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe`. Laut untersuchtem Projektbericht sind **593 von 610 Fällen ausgewählt**, zuletzt **574 PASS / 19 XFAIL** in der **Projektbewertung** (18 normative Abweichungen plus ein separat behandelter INTERLIS-1-Profilfall). Das ist eine historische Baseline, **kein in diesem Plan neu ausgeführter Testlauf**. **Roher Suitebericht und projektbezogene Ausnahmeprüfung bleiben getrennt.**

Ein standardmässiger Conformance-Runner ist **keine** Einladung, einen eigenen zweiten Java-Runner zu bauen: unser Python-Wrapper `tools/reference/conformance_check.py` ruft den bestehenden Runner heute bereits auf. Nur unsere **zusätzlichen** normativen Original-/Kontrollquellen-, Hash-, Diagnose- und Exitprüfungen müssen nach Python-Abbau als minimaler Java-/JUnit-Vertrag erhalten bleiben. Nie XFAIL oder Erwartung automatisch ändern; Referenzabstürze und Timeouts sind keine fachlich korrekten Ablehnungen.

**Fehlgrün-Canary:** Die Infrastruktur muss nachweislich scheitern, wenn z. B. eine Suite-Fall-ID fehlt oder doppelt vorkommt, ein JUnit-Fall aus der **falschen Klasse** mit gleichem Methodennamen stammt, ein Bericht alt/übersprungen ist, ein Originalhash nicht passt oder eine fehlgeschlagene Kontrolle fälschlich als Erfolg ausgewiesen wird. Wenige gezielte negative Tests genügen; kein neues allgemeines Evidenzframework.

### 2.1 Prüftakt ohne Testexplosion

- **Während des Fixes:** gezielte T1-Tests und lokal relevante T2-Fälle.
- **Vor jedem Lieferabschnittsreview:** relevante Modul-/Prozesstests, betroffene T2-Fälle und berührte T3-Verträge; grössere Parser-/Binder-/Typänderungen erhalten zusätzlich T2 vollständig. Genau benennen, welche Tests fällig und ausgeführt sind.
- **Beim letzten Lieferabschnitt von Phase 0b sowie Phasen 1–4:** `check`, volle T2-Conformance, `checkPortable`, `checkBrowser` inklusive realem Safari, Web Image und betroffene SDK-/LSP-/CLI-Verträge **frisch** auf demselben SHA. Ab Phase 0b zusätzlich `pythonFreeCheck` und die erforderlichen Python-freien Pfade. Die Prüfung früherer Lieferungen ist kumulativ. **Ausnahme `L0A`:** Dieser ausdrücklich kleine Pilot hat nur die Kriterien `AC0A-01` bis `AC0A-04` mit gezielten T1/T2- und bei Betroffenheit T3-Prüfungen; er behauptet keine vollständige Plattformabnahme. Vollständige technische Ausgangsgates werden spätestens in `L0B-3` nachgeholt.
- **Beim G2-FINAL:** T1–T4 und alle offenen Norm-/Abnahmekriterien. Eine alte Plattformprüfung belegt keinen neuen Kandidaten. `-x test`, stille Skips, alte Rohreports und nur als PASS beschriftete JSON-Dateien sind kein Ersatz.

Verfügbare Gradle-Einstiege vor der Migration: `:interlis-compiler:test`, `check`, `conformanceCheck`, `conformanceWrapperTest`, `g2EvidenceTest`, `checkPortable`, `checkBrowser`, `sdkProcessCheck`, `geoCorpus`, `jvmBenchmark`. Genaue aktuelle Task- und Plattformverfügbarkeit zu Beginn prüfen. `pythonFreeCheck` ist **neu zu implementieren**. Die Vollprofile werden um die tatsächlich erforderlichen Web-Image-Aufrufe ergänzt, soweit sie nicht bereits im jeweiligen Task enthalten sind. Ein fehlendes Pflicht-Tool ist `BLOCKED`, niemals `PASS`.

## 3. Phasenübersicht mit GRETL-kompatiblen Lieferabschnitten

| Phase | Inhalt | Lieferabschnitte | Freigabe |
|---|---|---|---|
| **0a – Pilotfix** | Prozess an einem echten Compilerdefekt beweisen | `L0A` | `PASS` nach unabhängiger Gegenprobe |
| **0b – Python entfernen** | Externer Java-Runner, aktive Evidenz-/Normprüfung, übrige Werkzeuge, Python-freier Build | `L0B-1` bis `L0B-3` | Letzter Review enthält vollständiges Python-freies Phasengate |
| **1 – Sprachfundament** | Predefined/Syntax/Import, Typen/Units/OID/Geometrie, Vererbung/Assoziationen | `L1-1` bis `L1-3` | Vollständige Sprachgrundlagenregression und Plattformgates |
| **2 – Erweiterte Semantik** | Ausdrücke/Funktionen/Constraints, Views, Übersetzungen/GENERIC/Grafik | `L2-1` bis `L2-3` | Öffentliche semantische Graphen korrekt, Gesamtregression |
| **3 – Overlays und Modellauflösung** | Echte Overlaynutzdaten und reproduzierbare Modellbeschaffung | `L3-1` bis `L3-2` | Fresh/Reuse, Versionen, Offline-/Lockprofile, reale Closures |
| **4 – Vollständiger G2-Abschluss** | Alle noch offenen Norm-/Assertion-Lücken, Differential-/T4-/Plattformabnahme | `L4-1` bis `L4-2` | Unabhängiges kumulatives `PASS` am endgültigen Produkt-SHA |

**Regel für Zuschnitt:** Die folgende Aufteilung ist verbindliche Arbeitsstruktur. Wenn ein Lieferabschnitt für eine seriöse Abnahme zu breit wird, darf Codex **vor der Umsetzung** begründet eine kleinere fachlich geschlossene Teillieferung vorschlagen; sie behält dieselben Muss-Kriterien und wird vor dem Start dokumentiert. **Nicht** allein für die Behebung eines einzelnen Findings einen neuen beliebigen Reviewabschnitt eröffnen. Ein Teilreview ersetzt keinen Abschluss der ursprünglichen Kriterien.

## 4. Phase 0a – Pilotfix `L0A`

**Ziel:** Den dokumentierten Lexerfehler bei verschachtelten Kommentaren beheben und das Commit/Review-Verfahren erproben. `testdata/g2/a2-unterminated-nested-comment.ili` ist ein bekannter Reproduktionskandidat; das aktuelle Ist-Verhalten selbst prüfen. Der vorherige `LexicalRecoveryContractTest` prüfte laut untersuchtem Commit nur den einfach offenen Block als unterminierten Gegenfall.

| ID | Verbindliche Abnahmebedingung |
|---|---|
| **AC0A-01** | Vorher-Nachweis: Offener äusserer Kommentar nach abgeschlossenem innerem Kommentar wird auf beiden Sprachversionen reproduziert; mindestens ein neuer Negativtest zeigt vor dem Fix tatsächlich `RED`. |
| **AC0A-02** | Einfache und verschachtelte gültige Kommentare bleiben verlustfrei; offener äusserer/innerer Kommentar wird in 2.3 und 2.4 korrekt mit Syntaxdiagnostik behandelt. STRICT/EDITOR, Quellrekonstruktion/UTF-16-Bereiche und Compile-Gate bleiben korrekt. |
| **AC0A-03** | Relevante JUnit-/Conformance-/bei Bedarf Parser-Portabilitätsprüfungen sind dokumentiert und ausgeführt; kein Originalpin, XFAIL oder Golden wurde gelockert. |
| **AC0A-04** | Sauberer Kandidatencommit, kurzer Bericht und nachprüfbarer Handoff; der Reviewer muss einen **eigenen neuen Lexer-Gegenfall** ausführen. Der aktive Einstieg liegt unter `docs/g2/`, historische Reviews bleiben historisch. Der unabhängige Reviewer entscheidet danach über `PASS`. |

**Erst nach `L0A: PASS` wird Phase 0b begonnen.** Das Pilot-PASS belegt nur den bezeichneten Fehlerfix und das unabhängige Verfahren, **nicht** die vollständigen Plattform- oder G2-Gates. Keine erneute grosse Norminventur als Vorbedingung dieses einfachen Fixes.

## 5. Phase 0b – Python vollständig entfernen

**Hartes Ziel:** Ausgehend von den beim untersuchten Commit **36 getrackten `.py`-Dateien** bleiben am Ende **null** getrackte Python-Dateien und **keine notwendige Python-/pip-Laufzeit** für aktiven Build, Tests, Conformance, Evidenz, Benchmarks, SDK und Reviews. Die Zahl zu Beginn mit `git ls-files '*.py'` erneut feststellen. **Keine mechanische 1:1-Portierung** aller 36 Dateien: den bestehenden Java-Runner wiederverwenden, wirksame Spezialverträge minimal nach Java/JUnit übernehmen und erwiesenermassen unnötige historische Generatoren entfernen, ohne Provenienz oder notwendige Reproduzierbarkeit zu verlieren.

Pro alter Datei wird in **einer knappen Migrationsmatrix** der konkrete Verwendungsort und genau eine Entscheidung festgehalten: `REUSE_EXTERNAL`, `PORT_MINIMAL`, `REPLACE_WITH_TEST`, `FREEZE_ARCHIVE` oder `DELETE_REDUNDANT`. Jede Entscheidung hat einen Code-/Testnachweis. Die Matrix ist ein einmaliger Arbeitsnachweis, kein neues dauernd zu aktualisierendes Inventarframework.

### `L0B-1`: Conformance und normative Sonderfälle nach Java

| ID | Verbindliche Abnahmebedingung |
|---|---|
| **AC0B-01** | `conformanceCheck` verwendet **direkt** den vorhandenen gepinnten `InterlisConformance.java` und die unveränderte Fallmenge/Runner-Baseline; kein zweiter eigener Runner und keine heimliche Filteränderung. Echter Prozesslauf und korrekte frische Rohberichte. |
| **AC0B-02** | Die 18 projektspezifischen Normabweichungen behalten ihre **separaten** Original-, Positiv-/Negativkontroll-, Hash-, Exit- und strukturierten Diagnoseprüfungen (einschliesslich Quellranges, Argumenten und Multiplizität); INTERLIS-1-Profilfall getrennt; Rohbilanz und Projektbilanz sichtbar. |
| **AC0B-03** | Altes und neues Verhalten an denselben gepinnten Fällen verglichen. Negative Canarys für fehlende/duplizierte Test-ID, geänderten Hash, XPASS, falsche/fehlende Kontrolle und internen Fehler führen verlässlich zu Testfehlschlägen. Die betroffenen Python-Dateien und Aufrufe sind beseitigt. |

### `L0B-2`: Aktive Evidenz/Normentests erhalten, Generatorballast abbauen

| ID | Verbindliche Abnahmebedingung |
|---|---|
| **AC0B-04** | Benötigte JUnit-XML-, Quellen-/Artefakt-Hash- und Assertion-Bindungen sind als **kleine Java-/JUnit-Tests** erhalten. Suite **und** Klasse **und** Methode, Fehler, Skips und Frische werden geprüft; falsche Klasse bei identischem Methodennamen wird erkannt. `g2EvidenceTest` bleibt ein wirksames Gate. |
| **AC0B-05** | Norm-/Grammatik-/Predefined-/Builtin-/Testkandidaten-Generatoren sind nach Gebrauch klassifiziert. Notwendige aktive Norm-/PDF-/Version-/Matrixverträge sind erhalten; rein historische Erzeugungsketten dürfen mit belegtem Grund entfallen. Gepinnte Evidenzen und Zitathashes werden nicht still verändert; insbesondere ist eine PDFBox-Extraktion nicht automatisch mit bisherigen `pypdf`-Offsets gleichzusetzen. |

### `L0B-3`: Übrige Werkzeuge und vollständiges Python-freies Phasengate

| ID | Verbindliche Abnahmebedingung |
|---|---|
| **AC0B-06** | SDK-/CLI-Prozessprüfungen, Referenz-/Corpus- und Performanceaufgaben (u. a. `geoCorpus`, `jvmBenchmark`) funktionieren über Java/Gradle und die bereits vorhandenen notwendigen Node-/Browserwerkzeuge; keine still deaktivierten oder nur scheinbar erfolgreichen Tasks. |
| **AC0B-07** | `git ls-files '*.py'` ist leer; aktive Gradle-/CI-/Shell-/Node-/Reviewpfade brauchen keine Python-/pip-Binaries. `pythonFreeCheck` prüft dies mit einer **absichtlich fehlschlagenden** Canary. Historische Texte dürfen ehemalige Befehle weiter zitieren. |
| **AC0B-08** | **Frischer Gesamtlauf ohne Python-Interpreter**: `check`, Conformance samt 18 Zusatzprüfungen, `g2EvidenceTest`, SDK und relevante Referenz-/Corpus-/Benchmark-Prüfungen, volle T3-Matrix inklusive Safari/Web Image und `pythonFreeCheck`. Der unabhängige Review dieser letzten Lieferung bewertet zugleich alle Kriterien der **Phase 0b**. |

Wenn ein unverändertes Altwerkzeug für den einmaligen Paritätsvergleich noch Python benötigt, geschieht das **vor dessen Entfernung in einer isolierten Vergleichsumgebung**. Nach dem letzten Migrationsticket ist Python auch nicht mehr Teil der offiziellen Nachweisroutine. Ein nicht ausführbarer Pflichtpfad ist `BLOCKED`, nicht „durch Dokumentation ersetzt“.

## 6. Phase 1 – Sprachfundament

Nutzerauftrag vom 29.10.2026: Phase 1 wird durchgehend bis zum unabhängigen
Phasen-PASS ausgeführt, ohne manuelle Stopps zwischen L1-1, L1-2 und L1-3.
Die Kandidatencommits, separaten OpenCode-/DeepSeek-Direktreviews und PASS-Gates
bleiben verbindlich. Technische Findings werden selbständig korrigiert und
erneut geprüft; nach PASS beginnt automatisch der nächste Abschnitt. Nur eine
neue fachliche Nutzerentscheidung oder ein ohne Nutzerhilfe unlösbarer Blocker
erfordert Rückfrage. Phase 2 beginnt dadurch nicht automatisch.

Der Schwerpunkt liegt auf realen Abweichungen und unvollständig nachgewiesenen Regeln aus bisherigen B–D-Schritten. `GraphBuilder.java` und `SyntaxTreeBuilder.java` bleiben bevorzugt Gegenstand **kleiner, gezielter Änderungen** statt eines spontanen Totalrefactorings.

| Lieferung | ID | Verbindliche Abnahmebedingung |
|---|---|---|
| `L1-1` Predefined/Syntax/Import | **AC1-01** | Vordefinierte Modelle, Domains, Klassen/Strukturen/Units und versionsabhängige Namen sind öffentlich korrekt gebunden; insbesondere die dokumentierten `SurfaceEdge`/`SurfaceBoundary`/`LineGeometry`-Fälle. |
|  | **AC1-02** | Lexer/Parser/Name- und Importregeln, Sichtbarkeit/Forward-References/Recovery sind für 2.3/2.4 mit positiven/negativen und öffentlichen Query-Fällen belegt; Fehler/Implementierungslücke/interner Fehler bleiben unterscheidbar. |
| `L1-2` Typen/Einheiten/Geometrie | **AC1-03** | Numerische Unterteilungen, Skalierung, exakte Einheitenrelationen, OID und gemeinsame Typkompatibilität erfüllen die jeweilige Norm; zu jedem strittigen Profil liegt ein entscheidungsfähiger Norm-/Gegenfall vor. |
|  | **AC1-04** | Text, Enum, FORMAT/Zeit, Coordinates/Geometrie, Collections/References und Typ-Queries erhalten tatsächliche Werte, Zielidentitäten und Grenzfälle in beiden Versionen. |
| `L1-3` Vererbung/Assoziationen – **Phasengate** | **AC1-05** | Modell-/Topic-/Klassen-/Strukturvererbung, ABSTRACT/FINAL/EXTENDED, Override und vererbte Eigenschaften sind fachlich korrekt und öffentlich abfragbar. |
|  | **AC1-06** | Association/Roles, Restriktionen, HIDING/EXTERNAL/OID/DEPENDS ON, effektive Ziele und Identitäten sind abgenommen. |
|  | **AC1-07** | Effektive Mitglieder, Herkunft und **Transferreihenfolge** bleiben bei Import-/Dateireihenfolge und Fresh/Reuse identisch. |
|  | **AC1-08** | Vollständige T1-/T2-/T3-Phasenregression und erneute Bewertung aller Kriterien `AC1-01` bis `AC1-07` auf dem neuen SHA; der unabhängige Reviewer entscheidet zugleich über **Phase 1**. |

## 7. Phase 2 – Ausdrücke, Views, Übersetzungen und weitere Semantik

Nutzerauftrag vom 30.10.2026: Die gesamte Phase 2 wird durchgehend bis zum
unabhängigen Phasen-PASS ausgeführt, ohne manuelle Stopps zwischen L2-1, L2-2
und L2-3. Kandidatencommits, unabhängige OpenCode-Reviews über die direkte
DeepSeek API und die PASS-Gates bleiben verbindlich. Technische Findings
werden selbständig korrigiert und am neuen Kandidaten erneut geprüft;
nach einem Teil-PASS beginnt automatisch der nächste Abschnitt. Echte neue
fachliche Entscheidungen werden mit Norm-/Gegenfall vorbereitet; unabhängige
Arbeit wird während einer Rückfrage fortgesetzt. Phase 3 beginnt dadurch nicht.

| Lieferung | ID | Verbindliche Abnahmebedingung |
|---|---|---|
| `L2-1` Ausdrücke/Funktionen/Constraints | **AC2-01** | Objekt-/Attribut-/Rollenpfade inklusive Indizes, Mengen und Spezialkontexten liefern korrekte gebundene Ausdruckstypen und Referenzen. |
|  | **AC2-02** | Alle relevanten 21 Builtins, ihre Signaturen/`UNDEFINED`-Semantik und die Constraintfamilien (inkl. SET/WHERE, UNIQUE, EXISTENCE, externe Constraints und Namensvererbung) sind öffentlich korrekt und mit Gegenfällen geprüft. Externe Funktionen werden nicht ausgeführt. |
| `L2-2` Views/Inspektionen | **AC2-03** | Projection/Selection/Join/Union/Aggregation, abgeleitete Sichten, Inspektionen und `ALL OF` entsprechen der Norm und den tatsächlichen Bindungsregeln. |
|  | **AC2-04** | Viewresultat enthält korrekte effektive Mitglieder, Typen, Zielidentitäten und Reihenfolgen; eine erfolgreiche Syntaxanalyse allein genügt nicht. |
| `L2-3` Translation/Generics/Metadaten – **Phasengate** | **AC2-05** | Übersetzte Import-Closures besitzen die richtigen Korrespondenzen, ursprünglichen Ziele und Reihenfolgen, auch bei konkurrierenden gleichnamigen Symbolen. |
|  | **AC2-06** | CONTEXT/GENERIC/DEFERRED, gebundene Metadaten, Linienformen sowie Grafik-/Signaturregeln sind fachlich verifiziert; `areAreas2/areAreas3` nur nach ausreichendem Normbeleg/Nutzerentscheidung. |
|  | **AC2-07** | Vollständige T1-/T2-/T3-Regression und kumulative Kriterienprüfung `AC2-01` bis `AC2-06` auf demselben SHA; dieser Review entscheidet zugleich über **Phase 2**. |

## 8. Phase 3 – Echte Overlays und Modellauflösung

Nutzerauftrag vom 30.10.2026: gesamte Phase3 autonom bis zum kumulativen
Phasen-PASS, ohne Stopps zwischen L3-1 und L3-2. Jede Lieferung erhält einen
unabhängigen OpenCode-Review über DeepSeek direkt am unveränderlichen
Kandidaten. Technische Findings werden selbständig korrigiert und am neuen
Kandidaten erneut geprüft; nach Teil-PASS wird automatisch fortgesetzt.
Nur tatsächlich neue fachliche Entscheidungen oder ohne Nutzerhilfe unlösbare
Blocker erfordern Rückfrage. Phase4 wird dadurch nicht beauftragt.

| Lieferung | ID | Verbindliche Abnahmebedingung |
|---|---|---|
| `L3-1` Overlaysemantik | **AC3-01** | `SemanticOverlays` verarbeitet **echte unveränderliche** Translation-/CRS-/Metaattribut-Daten statt nur Fingerprint-Platzhalter; Zielbindung, Mehrdeutigkeit, Konflikt, Herkunft und öffentliche Schemawirkung sind überprüft. |
|  | **AC3-02** | Inhaltliche Fingerprints, reine Overlaywechsel, Isolation von Alias-/Importzuständen und Fresh/Reuse-Invalidation sind deterministisch. |
| `L3-2` Modelldiscovery/Closure – **Phasengate** | **AC3-03** | Repositoryindizes, Quellauflösung und transitive Imports über lokale/Datei-/HTTP-Provider funktionieren ohne HTTP-/Dateisystemabhängigkeit des Compilerkerns. |
|  | **AC3-04** | Versionen/Mehrdeutigkeit, Offline/Pins/Locks, Abbruch und Quelle-ändert-sich-während-Auflösung haben genaue Resultate und negative Tests; kein stiller Latest-Fallback. |
|  | **AC3-05** | Mindestens repräsentative **gepinnt echte 2.3-/2.4-Closures** laufen mit öffentlichen Metamodell-Assertions; nicht bloss erfolgreichem Exitcode. |
|  | **AC3-06** | Vollständige T1-/T2-/T3-Phasenregression und kumulative Prüfung `AC3-01` bis `AC3-05` auf demselben SHA; dieser Review entscheidet zugleich über **Phase 3**. |

## 9. Phase 4 – Vollständiger G2-Abschluss

Die bestehende detaillierte G2-Matrix wird **abgearbeitet**, nicht erneut durch einen grossen Generator ersetzt. Laut Ausgangsbericht gibt es 369 Compilerregel-Bedingungen, 1'476 geplante Fälle, 80 weitere G2-Anforderungen und zahlreiche nicht abgenommene Kandidatenlinks. Die Zahlen sind **Arbeitsvorrat, keine Erfolgsmessung**. Nicht jede Grammatik-Occurrence braucht ein künstliches Vierertestset; aber **jede normative fachliche Bedingung benötigt einen tragfähigen, ausgeführten Wirksamkeitsnachweis oder eine ausdrückliche offene Entscheidung**.

| Lieferung | ID | Verbindliche Abnahmebedingung |
|---|---|---|
| `L4-1` Vollständigkeit und A3 | **AC4-01** | Sämtliche G2-pflichtigen Normbedingungen, Grammatikzweige, Builtins und 80 bislang offenen Anforderungen sind mit tatsächlichen Implementierungs-/JUnit-/öffentlichen Assertions verknüpft; `CANDIDATE_ONLY` gilt nicht als erledigt. Keine unerkannte Implementierungslücke. |
|  | **AC4-02** | JVM-/Portable-/Referenz-Semantikvergleich erkennt verlorene Member, falsch gleichnamige Ziele, vertauschte Reihenfolgen und falsches gültiges `CompiledSchema` anhand wirklich verfügbarer Daten; Nichtvergleichbares bleibt offen ausgewiesen. |
|  | **AC4-03** | Alle 18 Referenzabweichungen und strittigen Normprofile (u. a. TYPE MODEL, Numeric Scaling, `areAreas2/3`) sind mit passenden Normstellen, Original-/Gegenfällen und Empfehlung entscheidungsreif; keine Normentscheidung durch den Agenten. |
| `L4-2` Kumulative Gesamtfreigabe | **AC4-04** | Erforderliche Nutzerentscheidungen sind tatsächlich getroffen und unverfälscht umgesetzt; Rohreferenzabweichungen bleiben sichtbar. **Ohne nötige Freigaben kein G2-PASS.** |
|  | **AC4-05** | T4 führt reale gepinnte 2.3-/2.4-Closures mit transitiven Imports, Offline/Pins/Locks und öffentlichen Semantikassertions aus. |
|  | **AC4-06** | T1–T4, volle Conformance, echte Safari-/Web-Image- und SDK-/CLI-/LSP-Verträge, `pythonFreeCheck` und Python-freier Build sind am **endgültigen Produkt-SHA** frisch erfolgreich; keine ungeklärten `FAIL/ERROR/XPASS`, keine Pflichtskips. |
|  | **AC4-07** | Vollständiges unabhängiges Review der G2-Ergebnisse, Wechselwirkungen und Kriterien `AC4-01` bis `AC4-06` am **exakten Schluss-SHA**; ein `PASS` dieses Reviews ist die **G2-Freigabe**, nicht die Freigabe von P3/P4/P5. |

**Normentscheidungen früh vorbereiten:** Die 18 bestehenden Referenzabweichungen umfassen dokumentierte OID-/CONTRACTED-, Assoziations-, Ausdrucks-, View- und Geometriefragen. Bei sechs frühen OID-/CONTRACTED-Fällen sind die bislang abgedruckten Normauszüge unzureichend. Den Nutzer nachweisbasiert und rechtzeitig entscheiden lassen; nicht erst am allerletzten Tag. Die Regelentscheidung bleibt ein Nutzerakt und darf auch bei zwei übereinstimmenden Referenzcompilern nicht aus einem Mehrheitsvotum abgeleitet werden.

## 10. Einheitliche Berichte und Commitübergabe

**Keine zusätzliche Reviewplattform bauen.** Pro Lieferabschnitt reicht ein kurzer versionierter Implementierungsbericht und ein separater unabhängiger Prüfbericht. Rohlogs/JSON/XML/Artefakte in `build/reports/` oder CI-Artefakten, nicht als grosse Sammlung im Git-Repository. Eine einzige `docs/g2/STATUS.md` enthält aktuelle Lieferung, Status, letzten `PASS`-SHA, offene Blocker und nächsten Schritt. Die bestehenden historischen A–I-/Evidenzdateien werden nicht umgeschrieben.

Empfohlene Ablage:

```text
interlis-java/
└── docs/g2/
    ├── EXECUTION_PLAN.md
    ├── CODEX_STARTPROMPT.md
    ├── OPENCODE_REVIEW_PROMPT.md
    ├── STATUS.md
    ├── implementation/L1-2.md
    └── reviews/L1-2-review.md
```

**Implementierungsbericht (Agent A, vor dem Commit):**

```text
Lieferabschnitt / Phase:
Kriterien-IDs und betroffene Norm-/Spezifikations-IDs:
Basiscommit (voller SHA):
Bearbeitete Funktionen und nicht bearbeiteter Umfang:
Red-Test / Reproduktion vor dem Fix:
Geänderte Dateien und öffentliche Auswirkungen:
Eigene ausgeführte Kommandos mit Exitcodes:
Tests / Failures / Errors / Skips und Rohreportpfade:
Conformance: Fallmenge / Rohbilanz / Projektbilanz / Pins:
Fällige, aber nicht ausgeführte Prüfungen (mit Grund):
Offene Norm-/Technikfragen und bestehende Regressionen:
Nächster Schritt nach unabhängiger Abnahme:
```

**Erst nach dem Commit** werden der volle `CANDIDATE_SHA` und die Berichtsreferenz an OpenCode übergeben. Dieser Handoff steht in einer separaten Nachricht oder ignorierten Datei unter `build/review/`, damit kein selbstreferenzieller Commit entsteht. OpenCode speichert seinen eigenen Report getrennt. Ein ausschliesslicher Dokumentationscommit, der einen **unveränderten** Reviewbericht ablegt und `STATUS.md` nach dem Review auf `PASS` setzt, darf folgen; er ändert nicht rückwirkend den geprüften **Produktcommit**. Jede spätere Änderung an Code, Tests, Build, Abhängigkeiten oder ausführbaren Beispielen verlangt einen neuen Kandidaten und Review.

**Prüfbericht (Agent B, nach eigener Ausführung):**

```text
Lieferabschnitt / Phase / ist Phasengate? ja-nein:
Reviewer / tatsächlich gewählte OpenCode-provider/model-Konfiguration:
Basiscommit / exakt geprüfter Kandidatencommit:
Prüfumgebung und Toolchain:
Je Kriterium: ID | erfüllt / verletzt / nicht prüfbar | eigener Beleg | Finding:
Eigene neue Gegenprobe: Input, Hypothese, Kommando, tatsächlicher Output:
Ausgeführte Pflichtkommandos / Exitcodes / Tests / Skips:
Kumulierte Regressionen und vollständige Phasengates (falls fällig):
Norm-/Suite-/XFAIL-Unverändertheit und verbleibende Entscheidungen:
Offene BLOCKER / benötigte konkrete Nacharbeit:
Urteil: PASS / FAIL / BLOCKED
Begründung und nächster fachlicher Schritt:
```

Im Reviewer-Report sind in einer Tabelle **sämtliche Muss-Kriterien des Lieferabschnitts** zu behandeln. Beim letzten Lieferabschnitt zusätzlich **alle Muss-Kriterien der gesamten Phase**. Ein Reviewsatz „alle Tests grün“ ohne Kriterienbezug reicht nicht.

## 11. Operativer Einstieg

1. ZIP im Repository-Root so entpacken, dass die drei Dateien unter `docs/g2/` liegen.
2. Codex den Text in `docs/g2/CODEX_STARTPROMPT.md` als Auftrag geben. Es beginnt **nur mit `L0A`**, nicht mit einer erneuten grossen Architekturplanung.
3. Nach seinem lokalen Implementierungscommit den **vollen `CANDIDATE_SHA`** und `docs/g2/OPENCODE_REVIEW_PROMPT.md` in einem **separaten** OpenCode-Kontext mit DeepSeek Flash 1017 verwenden.
4. Bei `FAIL`/`BLOCKED` nacharbeiten oder Voraussetzungen herstellen; bei `PASS` zum nächsten Lieferabschnitt wechseln. Ein Phasenwechsel benötigt das `PASS` des letzten Phasenabschnitts einschliesslich kumulativer Gates.

**Erfolgsmass:** Korrekte, unabhängig nachgewiesene INTERLIS-Semantik bei steigender Normabdeckung, vollständige Pythonfreiheit und reproduzierbare Testprozesse. **Nicht:** Zahl der Dokumente, generierter Inventare oder grüner Tests ohne belastbare Assertions.

Nutzerentscheidung während L0B-3 (29.10.2026): Der notwendige Metadaten-
Kompatibilitätsfix wird samt API-/Spec-/Regressionstests vorgezogen. Ausserdem
werden INTERLIS-2.3-`OID AS`/`BASKET OID AS` ohne `CONTRACTED` für die verbreitete
Bestandskompatibilität bewusst zugelassen (Kapitel 06/15). Die sechs dadurch
regulär erfolgreichen Originalfälle bleiben unter den **18** strengen
Zusatzprüfungen mit Original-/Positiv-/Negativpins erhalten; zwölf normative
Referenzausnahmen bleiben offen. Corpus-/Suitepins und andere Normregeln werden
nicht geändert. Dies ist keine allgemeine Freigabe der übrigen Normentscheidungen.
