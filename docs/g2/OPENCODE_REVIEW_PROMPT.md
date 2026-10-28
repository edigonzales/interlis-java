# Unabhängiger Reviewauftrag: OpenCode / DeepSeek Flash 1017

> **Für den zweiten Agenten, nicht für Codex.** Lies zuerst den tatsächlichen Git-Stand und die Primärquellen. Du bist **Reviewer und Gegenbeispiel-Sucher**, nicht der Implementierer oder der Ausleger einer noch ungeklärten INTERLIS-Norm.
>
> Auftrag für `https://github.com/edigonzales/interlis-java`; ausführlicher Qualitätsvertrag: `docs/g2/EXECUTION_PLAN.md`. Alle Pfade relativ zum Repository-Root.

## 1. Review-Grenzen und Unabhängigkeit

Du prüfst **einen konkreten bereits committeten Implementierungsstand** (`head_sha`) gegen seinen Ausgangsstand (`base_sha`). Der **versionierte** Codex-Reviewauftrag liegt unter `docs/g2/review/<TICKET>-request.md` und enthält nur vor dem Commit bekannte Angaben; der **volle `head_sha` wird erst nach dem Commit** über eine separate Übergabenachricht oder `build/review/<TICKET>/handoff.yaml` übergeben. Ein SHA, das in seinem eigenen Commit enthalten sein soll, wäre zirkulär. Arbeite in einem **frischen Agentenkontext** auf einem **isolierten Git-Worktree oder Checkout** am angegebenen `head_sha`. Lies den Diff über `git diff <base_sha>..<head_sha>` selbst. Ein vom Implementierer verfasster PASS-Text ist **keine Tatsache**.

**Du musst:**

1. Den vollständigen `head_sha` und einen sauberen gecheckoutten Teststand prüfen und im Report nennen. Wenn die Revision fehlt oder der Reviewvertrag unvollständig ist: `NICHT_PRUEFBAR` statt imaginierter Prüfung.
2. Relevante Original-`.ili`-Dateien, den Produktions-/Werkzeugcode, die Normquelle und das tatsächliche Testverhalten **selbst** lesen. Für reine Werkzeugmigration keine erfundenen INTERLIS-Normzitate verlangen.
3. Relevante Tests und mindestens eine **selbst entworfene, neue, nicht von Codex gelieferte Gegenprobe** ausführen. Du darfst zusätzliche Proben in einem **separaten temporären Reviewverzeichnis bzw. `build/review/`** anlegen oder die Quelle in einer isolierten Kopie verändern; du darfst **keine getrackten Produktiv-, Test- oder Erwartungsdateien im geprüften Checkout committen oder verändern**.
4. Tatsächliche Kommandos, Prozess-Exitcodes, Reportorte, JUnit-Testnamen sowie unerwartete Ergebnisse dokumentieren. Ein Tool-/Netzwerk-/Browserausfall ist kein fachliches PASS.
5. Prüfen, dass Codex den Scope nicht durch gelockerte XFAILs, geänderte Originalmodelle, Test-Excludes, erwartete Diagnosen, neue stumme Skip-Pfade oder eingeschränkte Testselektion verkleinert hat.
6. Fachliche Fragen anhand Originalnorm (2.3 oder 2.4) und möglichst unabhängiger Referenzbeobachtung entscheiden, nicht anhand eines Compilermehrheitsentscheids. **Normative Nutzerfreigaben** erteilst du nicht. `ili2c`-Crash, `ilic-fork`-Absturz oder nicht vergleichbare Ausgabe als `INCONCLUSIVE` / `ERROR`, nicht `MATCH`.
7. Einen **knappen handlungsfähigen Reviewbericht** mit Befund-ID, Schwere, präzisem Reproduktionsweg, erwarteter Wirkung und Korrekturempfehlung abliefern. Wiederhole nicht alte historische Reviewkapitel.

Das gewünschte Modell ist **DeepSeek Flash 1017**, ausgeführt über OpenCode. Die genaue verfügbare **provider/model-ID** wird anhand der wirklichen OpenCode-Konfiguration ermittelt und im Review genannt. Ein vom Agenten selbst formulierter Modellname ist kein Nachweis. Wenn das Modell oder eine notwendige Ausführungsfähigkeit fehlt, die Einschränkung offen ausweisen; kein unbemerkter Wechsel auf Selbstreview.

## 2. Zuerst Tickettyp bestimmen

**`COMPILER_RULE`:** Änderungen an ANTLR, Lowering, Typ-/Namensbindung, Ausdrücken, Constraints, Views, Übersetzungen, Overlays oder Modellauflösung. Schwerpunkt liegt auf **fachlicher öffentlicher Semantik** und neuen `.ili`-Gegenfällen.

**`JAVA_TOOLING`:** Ablösung eines Python-Skripts, Java-/JUnit-/Gradle-Integration, Evidenz-/Norm-/Corpus-/SDK-/Benchmark-Tool. Schwerpunkt liegt auf **Alt-/Neu-Verhalten, strengen Negativkontrollen, fehlendem Doppelrunner und tatsächlicher Python-Freiheit des migrierten Pfads**.

**`PHASE_GATE` / `G2_FINAL`:** Alle Features und Werkzeugtickets der Phase sind zuvor einzeln geprüft. Führe ein **neues phasenübergreifendes Review** der Wechselwirkungen und vorgeschriebenen vollständigen Prüfstufen durch. Die Einzelreviews allein ersetzen es nicht.

## 3. Checkliste für `COMPILER_RULE`

### 3.1 Norm und korrekte Akzeptanz

- Lies den originalen Testfall und **die exakte Normstelle der verwendeten Version**. Prüfe den tatsächlichen Wortlaut, nicht ein unpassendes historisches PDF-Zitat.
- **Positivfall:** Gültiges Modell ergibt `CompilationOutcome.Succeeded` und – wenn der Test das verlangt – das richtige öffentliche `CompiledSchema`.
- **Negativfall:** Ungültiges Modell ergibt eine fachliche Ablehnung mit passendem `RejectionReason`, diagnostischem Code, Argumenten und Quellbereich; **niemals** ein scheinbar gültiges Schema oder ein beliebiger Infrastrukturfehler.
- **Fachlicher Inhalt:** Prüfe, wo einschlägig, `SymbolId`/Zielidentität, Typen/Typrelationen, Vererbung, effektive Member-/Transferreihenfolge, View-/Constraint-Inhalte, Herkunft, Übersetzungskorrespondenz und Source-/UTF-16-Ranges **über öffentliche APIs/Queries**.
- **Varianten:** 2.3 vs. 2.4, umgekehrte Datei-/Importreihenfolge, gleichnamige konkurrierende Ziele, Fresh/Reuse, EDITOR vs. STRICT und unabhängige defekte Closures **nur soweit die konkrete Regel davon abhängt**. Eine künstliche Kreuzprodukt-Testexplosion ist unerwünscht.

### 3.2 Eigene Gegenprobe ist obligatorisch

Versuche gezielt, die behauptete Lösung zu widerlegen. Beispiele zur Orientierung, **nicht blind kopieren**:

- Offener **äusserer** Blockkommentar nach geschlossenem inneren Kommentar; verschachtelte zusätzliche Ebene, abweichende Zeilenenden und Folgeposition.
- Korrekt typisierter, aber **falsch gebundener** gleichnamiger Import oder Domain-/Rollenname.
- Ein geerbtes Member ist sichtbar, aber seine **Reihenfolge oder Herkunft** falsch.
- Übersetzung mit korrekten Namen, aber vertauschter fachlicher Korrespondenz.
- Eine gültige 2.4-Syntax wird unzulässig mit 2.3-Regeln geprüft oder umgekehrt.
- Nach inkrementeller Änderung wird ein veralteter Cachezustand als gültiges `CompiledSchema` veröffentlicht.

**Berichte:** Neuer Input, erwarteter Vertrag, tatsächliche Beobachtung, exaktes Kommando, Exitstatus und getroffene / nicht getroffene Assertion. Kann eine Gegenprobe technisch nicht ausgeführt werden, ist die fachliche Abnahme nicht allein durch Nachdenken erteilt.

### 3.3 Regression und Regressionsumfang

- Führe den geänderten JUnit-Test und seine nächsten Nachbarn aus. Prüfe, ob der Test mit seinem Negativfall **vor dem Fix** fachlich fehlschlug, sofern die Vorherrevision ausführbar ist.
- Prüfe den dokumentierten `check`-/Conformance-Lauf **selbst** für den vereinbarten Umfang; fällige Portable-/Browser-Gates nach Risikoprofil. Ein erfolgreiches `checkPortable` belegt nicht automatisch echtes Safari oder Web Image.
- Achte besonders bei grossen `GraphBuilder`-/`SyntaxTreeBuilder`-Änderungen darauf, dass bestehende Typ-/Referenz-/Constraintregeln nicht still verändert werden.
- Verifiziere Testevidenz tatsächlich am richtigen JUnit-XML-`testsuite@name`, `testcase@classname` **und** `testcase@name`, ohne Failures, Errors, Skips oder stale Reports.

## 4. Checkliste für `JAVA_TOOLING` (Python-Ablösung)

### 4.1 Nicht nur die neue Implementierung lesen

- **Lies zuerst** das alte Python-Skript samt Negativtests, die tatsächlichen Gradle- und CI-Aufrufe und die alten fachlichen Ergebnisse.
- Prüfe die Zuordnung `REUSE_EXTERNAL`, `PORT_MINIMAL`, `REPLACE_WITH_TEST`, `FREEZE_ARCHIVE`, `DELETE_REDUNDANT`. Die Entscheidung muss durch aktuelle Verwendung und fehlende bzw. ersetzte Funktion belegt sein.
- `FREEZE_ARCHIVE` ist nur erlaubt, wenn die historische Quelle/Hashes erhalten bleiben und keine notwendige **aktive** Wiederholbarkeits- oder Fehlgrünprüfung entfällt. Ein stiller Verlust von Norm-/PDF-Funktion ist ein Blocker.
- Wurde die alte `.py`-Datei im abgenommenen Umfang tatsächlich entfernt? Läuft kein Java- oder Node-Wrapper heimlich wieder Python? Greift Gradle beim frischen Task noch auf ein altes Script zu?

### 4.2 Sonderprüfung `G2-JAVA-TOOLS-01`: gemeinsame Conformance-Suite

**Kritisch:** `interlis-compiler-testsuite` verfügt bereits über den gepinnten **Java-Runner `InterlisConformance.java`**. Unser bisheriges `conformance_check.py` ruft ihn selbst auf. Der neue Code muss ihn **direkt wiederverwenden**, nicht Fallauswahl, Statusklassifikation oder den Suite-Runner selbst in einem zweiten System duplizieren.

Prüfe einzeln:

1. Suite-Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`, archivierter SHA-256 `6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe` und 2.3/2.4-Fallfilter: **unverändert**.
2. Genau dieselbe ausgewählte Fall-ID-Menge (historisch 593), keine Duplikate, keine unerklärten neuen/fehlenden Fälle, gültige frische Rohberichte und korrekte Exitcodes.
3. **Rohklassifikation des gemeinsamen Runners und gesonderte Projektbewertung** sind weiterhin sichtbar. Die 18 normativen Abweichungen bleiben unveränderte, einzeln überprüfte Fachfälle; der separate INTERLIS-1-Profilfall bleibt erkennbar. Eine neue pauschale XFAIL-Baseline als Ersatz ist nicht akzeptabel.
4. Bisherige Python-Normkontrollen sind weiterhin wirksam: Originalquelle und SHA, Version, positive/negative Kontrollquellen, tatsächliche CLI-Prozessresultate, strukturierte diagnostische **Code/Severity/Phase/Arguments/Source-Ranges/Multi-Menge**, keine beliebige Fehlablehnung und keine internen Referenzfehler als erfolgreiche Konformität.
5. Neue zusätzliche **negative Canarys**: fehlende Fall-ID, doppelte ID, geänderter Quellhash, verdeckte XPASS, falsch klassifizierter Exitcode, ausgelassene Kontrolle, absichtlich falsche Diagnose oder vorzeitig beendeter Runner. Der neue Java-Test muss dies erkennen.
6. **Altes gegen neues Werkzeug** auf denselben gepinnten Daten und Prozessen ausführen und **fachliche** Parität nachweisen. Normerwartungen/Filter nicht passend machen. Zulässige Normalisierung nur für nachweislich nichtfachliche volatile Angaben (Zeitstempel, Run-ID, absolute Cachepfade).

Falls Java statt des externen Runners eine eigene umfassende Conformance-Engine implementiert, **`NACHARBEIT`** – selbst bei 593 grün ausgegebenen Fällen.

### 4.3 Evidenz-, Norm-, SDK- und Benchmark-Migrationen

- **`G2-JAVA-TOOLS-02`:** JUnit-XML muss richtige Klasse **und** Methode nachweisen. Gegentest mit gleicher Methode aus falscher Klasse; stale/skip/failure/fehlender XML-Report; falscher SHA. `g2EvidenceTest` muss noch echte aktive Prüfungen ausführen.
- **`G2-JAVA-TOOLS-03`:** Keine mechanische Python→Java-Portierung historischer PDF-Generatoren. Aber alle weiterhin erforderlichen Norm-/Version-/Quellenhash-/Matrixinvarianten müssen nachgewiesen bleiben. `pypdf`-Text-/Offsetwerte niemals ohne Prüfung als PDFBox-Äquivalent ausgeben.
- **`G2-JAVA-TOOLS-04`:** Benchmark, SDK, reale Modelle und Referenztools müssen mit echten Prozessen/Fixtures und Fehlerzuständen abgenommen werden. Keine Taskdeaktivierung, kein „empty success“, kein zufälliges Herunterladen ungepinnter Modelle.
- **`G2-JAVA-TOOLS-05`:** `git ls-files '*.py'` leer; aktive Gradle-/CI-/Scriptreferenzen Python-frei; `pythonFreeCheck` **und seine eigene Negativprobe** erfolgreich; **zusätzlich** echte Python-freie Ausführungsumgebung. Das statische Guard-Script allein belegt keine fehlende Laufzeitabhängigkeit.

### 4.4 Eigene Werkzeugmanipulation ist obligatorisch

Erfinde und führe mindestens **eine** passende falsche Input-/Reportvariante aus, die ein unsicheres neues Tool als PASS behandeln könnte. Gute Kandidaten: zwei gleiche Fall-IDs statt einer fehlenden; JUnit-Klasse vertauscht bei gleichem Methodennamen; veränderter Originalhash; ein übersprungener Test; ein alter Report; ein Referenzcompiler-Crash; eine entfernte SDK-Prozessprüfung. Ein positives und ein **absichtlich fehlschlagendes** Toolergebnis als Rohreport zeigen.

Bei einer Teilmigration ist `ABGENOMMEN` höchstens für **dieses Ticket** zulässig. Das **gesamte Python-free-Gate** bleibt offen, bis alle 36 bzw. am tatsächlichen Stand vorhandenen `.py`-Dateien und Ausführungswege entfernt sind.

## 5. Phasen- und G2-Gesamtreview

Ein Phasenreview muss neu und unabhängig die **gesamte zusammengeführte Revision** bewerten – nicht nur alte Einzelreviewurteile referenzieren:

- Genaue akzeptierte Ticket-IDs, jede noch offene Norm-/Implementierungs-/Nachweislücke und vollständige notwendige Gradle-/Conformance-/Portable-/Browser-/Web-Image-/SDK-Tests.
- Relevante Wechselwirkungen zwischen zwei korrekt wirkenden Einzeländerungen (Importreihenfolge, Symbolidentität, effektiver Typ, Fresh/Reuse, Version).
- Keine verlorenen negativen Tests oder entschärften Baselines. Die **exakte Conformance-Fallmenge** und der rohe Bericht müssen erhalten sein.
- Bei P0b: tatsächlich kein Python-Code/Interpreterbedarf. Bei späteren Phasen bleibt `pythonFreeCheck` dauerhaft Pflicht.
- Bei G2-FINAL zusätzlich reale gepinnte 2.3/2.4-Modell-Closures, vollständige Regel-/Abnahmematrix und **ausdrücklich durch den Nutzer entschiedene** normative Ausnahmeprofile. Ohne nötige Nutzerentscheidung: G2 **nicht** als abgeschlossen ausgeben.

**Die 18 normativen Referenzabweichungen und die bekannten Profile** (`TYPE_MODEL_STRUCTURE_BOUNDARY`, `NUMERIC_SCALING_PAIR_AND_CONSTANT_PROFILE`, `areAreas2/areAreas3`) sind keine beliebigen technischen Stylefragen. Unzureichende Normbegründungen – insbesondere bei OID-/CONTRACTED-Fällen – ausdrücklich markieren.

## 6. Dein Ausgabeformat (verbindlich)

```markdown
# Independent review: <TICKET-ID> / <voller HEAD_SHA>

## Urteil
ABGENOMMEN | NACHARBEIT | WARTET_AUF_NORMFREIGABE | NICHT_PRUEFBAR

## Identität und Umfang
- Repository / geprüfter Worktree / base_sha / head_sha:
- Wirklich eingestellter OpenCode-provider/model-Wert:
- Tickettyp und geprüfte fachliche Aussagen:
- Nicht geprüfte und nicht erforderliche Prüfungen (klar trennen):

## Primäre Prüfungen
| Prüfung | Kommando/Quelle | Exit/Ergebnis | Relevanter Nachweis |
|---|---|---|---|
| Original-Fall | ... | ... | ... |
| Gezielter JVM- oder Java-Tooltest | ... | ... | ... |
| Conformance/Portable/Plattform soweit fällig | ... | ... | ... |

## Eigene neue Gegenprobe
- Begründete Hypothese:
- Vollständiger neuer Testinput / reproduzierbarer Pfad:
- Erwartetes Verhalten:
- Beobachtetes Verhalten inkl. Exitstatus und relevantem Output:
- Schlussfolgerung:

## Findings
- <FINDING-ID> / BLOCKER|MAJOR|MINOR: <konkreter Defekt oder nachgewiesene Lücke>
  - Ort und Reproduktion:
  - Erwarteter fachlicher Vertrag:
  - Minimal erforderliche Korrektur:

## Integrität und offene Entscheidungen
- Originalfixtures, Testauswahl, Suite-/Normpins, Baselines/XFAILs unverändert?:
- Python-/Evidenz-/Reportinvarianten (wenn relevant):
- Echte Normentscheidungen an Nutzer statt vom Reviewer freigegeben:

## Fazit und nächster konkreter Schritt
- Ticket kann / kann nicht als ACCEPTED markiert werden.
- Bei Nacharbeit: exakte Bedingungen für neuen Review am nächsten Commit.
```

**Urteilskriterien:**

- `ABGENOMMEN`: Exakter SHA geprüft; relevante Tests **selbst ausgeführt**; eigene relevante Gegenprobe; keine offenen BLOCKER/MAJOR im geprüften Ticket; unveränderte Norm-/Suiteerwartungen bzw. explizit freigegebene Änderungen.
- `NACHARBEIT`: Konkreter fachlicher Fehler, Schein-PASS, fehlende notwendige Assertion, entfallene Pflichtprüfung, Doppelrunner oder gefährliche Testabschwächung. Reproduktion und minimalen Fix benennen.
- `WARTET_AUF_NORMFREIGABE`: Die einzig verbleibende Schranke für die betreffende Fachregel ist eine echte Nutzerentscheidung; kein automatischer Normentscheid. Technisch offene Fehler gesondert als `NACHARBEIT` behandeln.
- `NICHT_PRUEFBAR`: Verifikation konnte wegen fehlendem Commit, fehlerhaftem Reviewcheckout, nicht verfügbaren Tools/Referenzquellen oder nicht ausführbaren Pflichtprüfungen nicht vorgenommen werden. **Kein Ersatz durch alte grüne Reports.**

## 7. Grenzen deiner Autorität

Du darfst **keine** Test-Baselines, XFAIL-Ausnahmen, Normerwartungen, Produktivklassen oder Anforderungen eigenmächtig verändern. Keine Releases, keine Pushes und kein Merge. Ergebnisse im eigenen Reviewverzeichnis aufbewahren; Codex übernimmt nur begründete und reproduzierbare Nacharbeitsbefunde in einen **neuen** Implementierungscommit. Dein Review wird ungültig, soweit sich der geprüfte Quellstand anschliessend ändert.

**Wichtigste Frage für jedes Review:** „Welcher konkrete gültige oder ungültige INTERLIS-Fall beziehungsweise welche konkrete fehlgeschlagene Werkzeugkontrolle könnte die Behauptung von Codex widerlegen – und habe ich diesen Fall wirklich ausgeführt?“
