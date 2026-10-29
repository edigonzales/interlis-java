# OpenCode-Reviewprompt – unabhängige Abnahme von INTERLIS Java G2

**Rolle: Agent B / unabhängiger Prüfer mit DeepSeek Flash 1017.** Dieser Auftrag wird in **einer von Codex getrennten Sitzung** gestartet. Deine Aufgabe ist **nicht** die Implementierung, sondern die unabhängige Prüfung des von Codex erstellten, **unveränderlichen Kandidatencommits** nach dem bei `gretl-runtime` bewährten Verfahren. Der massgebliche Plan liegt unter `docs/g2/EXECUTION_PLAN.md`.

## 0. Übergabedaten (vom Auftraggeber einzusetzen)

```text
Repository: edigonzales/interlis-java
Lieferabschnitt: <z. B. L0A, L0B-1, L1-3, L4-2>
BASE_SHA: <vollständiger Ausgangscommit>
CANDIDATE_SHA: <vollständiger Kandidatencommit>
Implementierungsbericht: docs/g2/implementation/<LIEFERABSCHNITT>.md
Verbindlicher Plan: docs/g2/EXECUTION_PLAN.md
Ist dies die letzte Lieferung einer Phase?: <ja/nein>
```

**Wenn Commit oder Lieferabschnitt nicht eindeutig sind, ist die Prüfung `BLOCKED`, nicht `PASS`.** Erfasse die tatsächliche in OpenCode gewählte Provider-/Modellkonfiguration (DeepSeek Flash 1017); eine blosse Selbstaussage des Modells über seinen Namen ist kein verlässlicher Identitätsnachweis.

## 1. Arbeitsregeln und Unabhängigkeit

1. Verwende einen **sauberen, eigenen Worktree oder Checkout** auf `CANDIDATE_SHA`. Prüfe `git rev-parse HEAD`, `git status --short`, `git diff --stat BASE_SHA..CANDIDATE_SHA` und den vollständigen relevanten Diff. Keine Änderungen an produktiven Quellen, Tests, Builddateien oder Abnahmekriterien des Kandidaten.
2. Lies zuerst `AGENTS.md`, `docs/g2/EXECUTION_PLAN.md`, die **für diese Lieferung geltenden Kriterien-IDs**, betroffene Norm-/Spezifikationsstellen und die tatsächlichen Produktions- und Testdateien. Codex' Bericht ist **eine Behauptung**, kein Prüfnachweis.
3. Prüfe **jedes** Muss-Kriterium einzeln anhand eigener Tests, Rohreports, Codestellen oder vollständig verifizierter CI-Artefakte für **exakt diesen SHA**. Insbesondere prüfe die tatsächliche Testfallanzahl, Failures, Errors, Skips und Exitcodes; ein grünes Zusammenfassungs-JSON allein genügt nicht.
4. **Mindestens eine selbst erfundene und ausgeführte, relevante Gegenprobe pro risikotragendem Lieferabschnitt.** Sie muss versuchen, die konkrete fachliche oder technische Behauptung von Codex zu widerlegen. Sie darf nicht nur einen vorhandenen Test umbenennen. Neue Prüfquellen dürfen im **separaten** Reviewverzeichnis entstehen, nicht im Produktkandidaten.
5. Bei `FAIL` konkrete, reproduzierbare Findings mit erwarteter und tatsächlicher Beobachtung liefern. Bei `BLOCKED` exakt die fehlende Ausführungsumgebung, Evidenz oder Nutzerentscheidung bezeichnen. **Nicht** neue Schein-Pflichtkriterien erfinden und nicht durch Aufweichen normativer Verträge `PASS` herstellen.
6. Du darfst **kein** XFAIL, keine normative Erwartung, keinen Conformance-Filter und keinen Suite-Pin freigeben oder verändern. Eine Normunklarheit geht als `BLOCKED (NORM_DECISION)` mit originaler Normstelle und minimalem Gegenbeispiel an den Nutzer; andere technische Fehler bleiben `FAIL`.
7. Kein Push, kein Merge, kein Release. Schreibe den Reviewbericht ausserhalb des zu prüfenden Produktcommits. Nach Korrekturen ist ein **neuer Kandidatencommit vollständig neu zu prüfen**. Falls ein späterer Commit ausschliesslich den unveränderten Reviewbericht speichert, muss dafür kein künstlicher zweiter Code-Review angelegt werden.

## 2. Genau drei mögliche Urteile

| Urteil | Vergaberegel |
|---|---|
| **`PASS`** | Alle Kriterien des Lieferabschnitts tatsächlich erfüllt; alle fälligen Pflichtprüfungen sind für `CANDIDATE_SHA` belegbar; mindestens eine eigene Gegenprobe ausgeführt; keine offenen Muss-Findings. Bei letzter Lieferung auch das komplette kumulative Phasengate erfüllt. |
| **`FAIL`** | Mindestens ein Kriterium ist nachweislich verletzt: falscher Compilerentscheid, falscher öffentlicher Graph, Schein-PASS, verlorene Prüfung, deaktivierter Test, unberechtigte Baseline-/Normänderung, verborgene Python-Abhängigkeit oder anderer reproduzierbarer Fehler. |
| **`BLOCKED`** | Eine erforderliche Prüfung ist mangels Voraussetzung nicht belastbar durchführbar oder eine Nutzer-Normentscheidung fehlt. Präzisiere `INFRA`, `MISSING_EVIDENCE` oder `NORM_DECISION`; diese Zusätze sind **keine eigenen Urteile**. |

Ein noch nicht ausgeführter **Pflichttest** ist `BLOCKED`, nicht `PASS`. Ein tatsächlich fehlschlagender Pflichttest ist `FAIL` (ausser es handelt sich eindeutig um einen unabhängig nachgewiesenen Infrastrukturblocker). P3-/P4-/P5-Leistungen ausserhalb dieses Auftrags sind **keine** Pflichtprüfungen. Trenne die Gründe sauber.

## 3. Fachliche Compilerprüfung (T1/T2)

Prüfe bei Lieferabschnitten `L0A`, `L1-*`, `L2-*`, `L3-*` und `L4-*` je nach Fachregel:

- **Originalnorm:** Richtige exakte INTERLIS-2.3-/2.4-Normstelle und tatsächliche Quellsyntax. Referenzcompilerbeobachtungen sind Evidenz, ersetzen aber kein Normurteil.
- **Positivfall:** Reales gültiges `.ili`-Modell kompiliert und erzeugt, wenn die Closure vollständig ist, ein **inhaltlich korrektes öffentliches `CompiledSchema`**. Bei relevanten Features die richtigen Symbole, Typen, Zielreferenzen, effektiven Member, Transferreihenfolge, Herkunft, Übersetzung und Source-/UTF-16-Ranges prüfen.
- **Negativfall:** Minimal ungültige Quelle erhält **fachlich passende Ablehnung und Diagnose**; kein akzeptiertes Schema und keine Verwechslung mit `INCOMPLETE_SUPPORT`, `INTERNAL_FAILURE` oder einem beliebigen Prozessfehler.
- **Varianten nach Bedarf:** Beide Sprachen, Import-/Dateireihenfolge, gleiche Namen mit verschiedenen Identitäten, Fresh/Reuse, EDITOR/STRICT, Overlaywechsel und inkrementelle Änderungen. Keine Testfall-Kreuzprodukte ohne fachlichen Mehrwert.
- **Regression:** Neue Assertions wirklich gegen den Compiler prüfen; bestehende Tests zu Nachbarregeln dürfen nicht verschwinden. Bei Red/Green-Verträgen gegebenenfalls selbst auf `BASE_SHA` den Vorherfehler reproduzieren.
- **Conformance:** Den **gepinnten externen** Java-Runner und die fälligen Fälle bzw. das volle Profil selbst ausführen. Prüfen, dass **Roh- und Projektbewertung** getrennt bleiben, alle Fall-IDs vollständig sind und keine Baseline/XFAIL-Änderung auf Fehlgrün hinausläuft.

**Mögliche neue Gegenproben (selbst konstruieren, nicht blind übernehmen):** ein offener äusserer Kommentar bei gültig beendetem inneren Block; ein gleichnamiges, aber falsch gebundenes importiertes Ziel; ein korrekter Name mit falscher Vererbungs-/Transferreihenfolge; geänderte Imports nach einem inkrementellen Cachetreffer; ein korrekt typisierter, aber der falschen Translation zugeordneter Ausdruck; eine zulässige 2.4-Quelle, die fälschlich an 2.3-Regeln scheitert. Prüfe das **öffentliche** Ergebnis, nicht nur den Exitcode.

## 4. Python-Migration gesondert prüfen (`L0B-1` bis `L0B-3`)

**Ziel ist vollständige Pythonfreiheit, nicht eine 1:1-Portierung aller Skripte.** Lies für den betroffenen Umfang die alte Python-Implementierung, ihre Negativtests, Gradle-/CI-Aufrufer und den neuen Java- oder externen Ersatz. Für jede Datei muss die Entscheidung `REUSE_EXTERNAL`, `PORT_MINIMAL`, `REPLACE_WITH_TEST`, `FREEZE_ARCHIVE` oder `DELETE_REDUNDANT` begründet sein. Prüfe, ob aktive Verträge und Fehlerpfade tatsächlich erhalten wurden.

### `L0B-1` – externe Conformance statt Doppelrunner

- Der vorhandene externe **`InterlisConformance.java`** wird direkt verwendet. Sein gepinnter Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`, archivierter SHA-256 und die selektierte Testfallmenge dürfen nicht still geändert werden. **Keine neue allgemeine Runner-/Statusmaschine in Java.**
- Die **18 zusätzlichen projektspezifischen normativen Ausnahmeprüfungen** sind weiter aktiv und strenger als eine simple XFAIL-Liste: gleiche Original-/Kontrollquellen und SHA, erwarteter Outcome/Exit, diagnostische Codes/Severity/Phase/Argumente/Source-Ranges und Multiplizität. Der separate INTERLIS-1-Profilfall bleibt sichtbar. Rohe und projektspezifische Bewertung dürfen nicht vermischt werden.
- Führe Alt-/Neu-Paritätsprüfungen auf identischen Eingaben aus. **Eigene Manipulation:** z. B. doppelte Fall-ID bei fehlender anderer, veränderter Originalhash, ausgelassener Kontrolllauf, falscher Exitcode oder verdeckter XPASS. Das neue Tool muss korrekt scheitern.

### `L0B-2` – aktive Evidenzprüfungen erhalten

- JUnit-XML muss die richtige `testsuite@name`, `testcase@classname` **und** `testcase@name` belegen, einschliesslich Report-Frische, Errors/Failures/Skips und Quellen-/Reporthashes.
- Eigene Negativprobe mit **richtiger Methode aus falscher Klasse**, stale Report oder fehlender/gebrochener Signatur. Keine neue grosse Snapshot-/PDF-/Inventurplattform verlangen.
- Bei archivierten Norminventaren überprüfen, dass **notwendige aktive** Norm-/PDF-/Version-/Hash-Verträge bestehen bleiben. Bisherige `pypdf`-Offsets dürfen nicht ungeprüft als PDFBox-equivalent betrachtet werden.

### `L0B-3` – Python-freies Phasengate

- Alle getrackten `.py`-Dateien sind entfernt; aktive Gradle-/CI-/Shell-/Node-/Reviewwege rufen kein Python oder pip mehr auf. Ein `pythonFreeCheck`-Canary scheitert bei absichtlich hinzugefügter Python-Abhängigkeit.
- `check`, `conformanceCheck`, `g2EvidenceTest`, SDK-/Referenz-/Corpus-/Benchmark-Pfade und die volle T3-/Browser-/Web-Image-Matrix **tatsächlich in einer Umgebung ohne Python-Interpreter** ausführen oder mit sauberer reproduzierbarer CI-Evidenz **für diesen SHA** verifizieren. Ein reines Grep-Gate genügt nicht.
- Historische Reports/Normdaten bleiben lesbar, obwohl ihre früheren Erzeugungsskripte nach begründeter Entscheidung gelöscht wurden. Keine stille Testdeaktivierung und kein Fake-PASS.

## 5. Phasengate und kumulative Abnahme

**Wenn der aktuelle Abschnitt die letzte Lieferung einer fachlichen/vollständigen Phase ist** (`L0B-3`, `L1-3`, `L2-3`, `L3-2`, `L4-2`), prüfst du zusätzlich **alle Kriterien der gesamten Phase** und die vollständigen vorgeschriebenen Regressionen am **gleichen Kandidaten-SHA**. Das ist zugleich die **einzige** unabhängige Phasenabnahme. Kein zweiter separater Phasenreview. **Sonderfall `L0A`:** Die Pilotabnahme umfasst ausschliesslich `AC0A-01` bis `AC0A-04`, gezielte T1/T2 und betroffene T3-Verträge. Sie ist ausdrücklich **keine** vollständige Browser-/Plattform-/G2-Abnahme; diese ist spätestens bei `L0B-3` fällig.

- Vollständiges `check`, gemeinsamer T2-Conformance-Lauf mit exakten Fallmengen und sauberer Roh-/Projektbilanz, `checkPortable`, `checkBrowser` mit **echtem Safari**, Web Image und relevante CLI-/SDK-/LSP-Verträge. Nach P0b bleibt `pythonFreeCheck` verpflichtend.
- Alle für die Phase nötigen öffentlichen Modell-/Query- und Fehlpfadtests, relevante Importreihenfolge/Fresh-Reuse sowie frühere Lieferungen und deren Wechselwirkungen. **Nicht** nur alte Einzelreviewberichte referenzieren.
- Bei `L4-2`: volle T1–T4-Matrix, echte gepinnte 2.3/2.4-Modell-Closures, alle normativen G2-Bedingungen und 80 offene Anforderungen gegen tatsächliche Assertions, alle erforderlichen **ausdrücklichen Nutzerentscheidungen** und abschliessend Pythonfreiheit.

**Wichtig:** Auch ein `PASS` für einen früheren Lieferabschnitt ist nicht automatisch ein `PASS` für die neue zusammengeführte Revision. Fehlende Pflichtausführungen bei Phasengates sind `BLOCKED`. Normative Fälle, für die noch eine Nutzerentscheidung gebraucht wird, werden nicht durch den Reviewer freigegeben.

## 6. Verbindliche Vorlage für den unabhängigen Prüfbericht

```markdown
# Unabhängiger Review: <LIEFERABSCHNITT>

Lieferabschnitt / Phase / letztes Phasengate?:
Implementierungsagent: Codex
Prüfagent: OpenCode; tatsächliche Provider-/Modellkonfiguration:
BASE_SHA:
CANDIDATE_SHA (durch git überprüft):
Prüfumgebung / Toolchain:

## Kriterienprüfung
| Kriterium-ID | Erfüllt / verletzt / nicht prüfbar | Eigener Test-/Code-/CI-Beleg | Finding-ID |
|---|---|---|---|
| ... | ... | ... | ... |

## Eigene Gegenprobe
Hypothese und fachliches Risiko:
Neu erstellter Testinput / exakter Pfad oder vollständige Quelle:
Erwartetes Verhalten:
Kommando / Exitcode / beobachteter tatsächlicher Output:
Ergebnis und Schlussfolgerung:

## Pflichtprüfungen
Ausgeführte Kommandos, tatsächlich ermittelter Exitcode, Anzahl Tests/Fehler/Skips:
Rohberichte / CI-Artefakte und deren Bindung an CANDIDATE_SHA:
Nicht ausgeführte fällige Prüfungen mit Grund:
Conformance-Fallmenge; rohe und projektspezifische Bilanz:
Norm-/Suite-/XFAIL-/Baseline-/Quellpin-Unverändertheit:
Python-Migrations-/Phaseninvarianten soweit relevant:
Kumulative Phasenregression, wenn fällig:

## Findings
<FINDING-ID> / BLOCKER oder MAJOR:
Ort, Reproduktion, erwarteter Vertrag, beobachteter Fehler, nötige Nacharbeit:

## Urteil
PASS / FAIL / BLOCKED
Bei BLOCKED: INFRA / MISSING_EVIDENCE / NORM_DECISION und konkrete Voraussetzung:
Begründung und nächster konkreter Schritt:
```

**Jedes Muss-Kriterium des Lieferabschnitts bekommt eine eigene Zeile.** Beim letzten Phasenabschnitt zusätzlich die Muss-Kriterien aller Lieferungen dieser Phase. Erfinde keine Tests, die du nicht ausgeführt hast; eine Modellselbstauskunft ersetzt keine Toolkonfiguration. Falls OpenCode die Prüfung nicht ausführen kann, übergib ein präzises `BLOCKED`-Ergebnis und keinen fingierten Erfolg.

**Arbeitsziel:** Einen tatsächlich fehlerhaften INTERLIS-Compiler, einen schwachen Test oder eine fälschlich als abgeschlossen bezeichnete Python-Migration rechtzeitig zu erkennen – und nur korrekt implementierte, nachvollziehbar geprüfte Lieferungen mit `PASS` freizugeben.
