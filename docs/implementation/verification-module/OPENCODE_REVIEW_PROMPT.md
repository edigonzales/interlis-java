# Autonomer unabhängiger Gesamt-Review

## A. Operative Anweisung an Codex

### A1. Zeitpunkt und Umfang

Starte diesen Review **erst nach erfolgreichem Abschluss V0–V4** und nach dem Commit des geprüften Kandidaten. Kein unabhängiger Review zwischen einzelnen Implementierungsphasen. Der Reviewer beurteilt Zielerreichung, Code, Testentfernungen, Testorganisation und Nachweisqualität für den **gesamten kumulativen Diff** vom Ausgangsstand zum Kandidaten.

Nutze OpenCode **2.x** und **DeepSeek Flash 1017 über die direkte DeepSeek-API**. Kein Codex-Subagent, kein anderer Anbieter, kein anderer Modellstand und kein Reviewer, der nur Codex' Zusammenfassung bestätigt.

### A2. Installation und Modellbindung

1. Vorhandenes `opencode` über PATH bzw. vorhandenen Desktop-CLI-Einstieg ermitteln. Bekannte Pfade sind Hinweise, keine Voraussetzung: `~/.opencode/bin/opencode`, `/Applications/OpenCode.app/Contents/Resources/opencode-cli`.
2. `--version`, `run --help`, `models --help` und `models` der installierten Version prüfen. Keine unbestätigte Option wie `models --refresh` voraussetzen.
3. Bestehende direkte DeepSeek-Anmeldung weiterverwenden. Keine neue Umgebungsvariable/API-Key-Eingabe verlangen, wenn die vorhandene OpenCode-Anmeldung genügt. Keine Secrets lesen, exportieren oder protokollieren.
4. Verfügbare Provider-/Modellkennung mit dem gewünschten Stand abgleichen. Der untersuchte bestehende Starter unter `docs/implementation/http-network-policy/review/run-review.cjs` verwendet `deepseek/deepseek-flash`. Dieser Alias ist **nicht von sich aus** ein Beleg für 1017.
5. Ein Alias ist zulässig, wenn dessen tatsächliche Zuordnung zu Flash 1017 über den vorhandenen Provider-/Modellvertrag nachvollziehbar belegt wird. Angefragte Kennung, aufgelöste Konfiguration und gegebenenfalls berichtete Laufzeitkennung getrennt protokollieren. Keine Backendversionsbehauptung erfinden, wenn die API nur einen Alias ausweist.
6. Bei nicht verlässlich auswählbarem gewünschten Modell: `BLOCKED`, kein stiller Fallback. Keine globalen Installationen, Updates oder Konfigurationsänderungen.

Die aktuellen v2-Dokumente beschreiben `run`, `--model`, `--format json`, `--file`, `--standalone` und `--auto`; die tatsächlich installierte CLI-Hilfe entscheidet über die verwendbare Kombination. V2 verwendet ein `permissions`-Array sowie die Actions `shell` und `subagent`, nicht das alte V1-Schema.

### A3. Isolierter Kandidat

Erzeuge einen separaten sauberen detached Worktree oder eine geeignete isolierte lokale Kopie exakt auf `CANDIDATE_SHA`. Der Reviewer darf die versionierten Kandidatendateien nicht verändern. Schreibbar sind nur notwendige Buildausgaben und ein separates Scratch-/Reportverzeichnis.

Vorgesehene externe Struktur:

```text
<review-root>/<candidate-sha>/round-01/
  request.json
  source-state-before.json
  review-prompt.md
  launch.json
  opencode.jsonl
  stderr.log
  process-result.json
  source-state-after.json
  output/
    review.md
    verdict.json
    probes/
    logs/
```

Dieser Pfad liegt ausserhalb der versionierten Produktquellen und wird nicht durch deren `clean` gelöscht. Bei neuer Runde neue unverwechselbare Pfade verwenden. Fehlgeschlagene Runden nicht überschreiben.

Der Reviewer erhält Zugriff auf ursprüngliche Codex-Rohbelege, möglichst schreibgeschützt bzw. mit gespeicherten Hashes, und darf sie selbst prüfen. Eine ihm übergebene Zusammenfassung ist keine Wahrheit, die er ungeprüft übernehmen muss.

### A4. Rechte und autonomer Betrieb

Konfiguriere die installierte OpenCode-Version lokal für diesen Reviewer: Lesen von Auftrag/Kandidat/Belegen, gezielte Build-/Testkommandos sowie Schreiben unter dem freigegebenen Scratch-/Reportpfad erlauben; Produktänderungen, Commit, Push, Veröffentlichung, Credentialzugriffe, Benutzerfragen und weitere Agentendelegation untersagen.

`--auto` bestätigt nur nicht ausdrücklich verbotene Aktionen. Keine pauschale Aufhebung aller Verbote. Prüfe die tatsächlich wirksamen Regeln statt nur eine neue JSON-Datei anzulegen. Es darf keinen Ablauf geben, der mitten im Review auf eine Benutzerfreigabe wartet.

Eine Edit-Tool-Sperre ist keine vollständige Betriebssystem-Sandbox: Shellbefehle können ebenfalls schreiben. Nutze vorhandene passende Schutzmechanismen, möglichst schreibgeschützte Kandidatenquellen und eine enge Kommandoauswahl. Prüfe zusätzlich Kandidaten-SHA, Tree-SHA und versionierte Dateiinhalte vor und nach dem Lauf. Unerklärte Änderungen machen den Review ungültig. Keine neue allgemeine Sandboxplattform als Nebenprojekt bauen.

Vorhandene projektspezifische Guard-/Reviewstarter dürfen angepasst wiederverwendet werden. Keine alten Benutzerpfade, JDK-Pfade, Kandidaten-SHAs oder Modellannahmen blind übernehmen. Vermeide globale Änderungen am OpenCode-Hintergrundservice; ein bestätigter Standalone-Aufruf ist vorzuziehen.

### A5. Startmuster

Nach Bestätigung durch die lokale Hilfe etwa:

```sh
opencode run --standalone --auto \
  --model '<verifizierte direkte DeepSeek-Modellkennung>' \
  --format json \
  --file '<absolut>/request.json' \
  --file '<absolut>/review-prompt.md' \
  'Prüfe den vollständigen Kandidatenauftrag unabhängig. Schreibe review.md und verdict.json in den freigegebenen output-Pfad. Ändere keine versionierten Dateien.'
```

Ein geeigneter bestätigter Agent muss echte gezielte Tests und Scratchdateien ausführen können. Kein `--continue`, keine alte Session. Ein vorhandener `--agent build` ist nur mit den tatsächlich wirksamen Reviewerbeschränkungen geeignet.

Starte den Prozess selbst, überwache ihn und warte auf seinen wirklichen Abschluss. Keine Aussage „Review läuft im Hintergrund“ als Ersatz für die Validierung. Timeout, Signal, API-Fehler, hängende Berechtigungsfrage oder fehlende Berichte sind nicht PASS.

### A6. Kleiner Runner, falls nötig

Bevorzuge den vorhandenen funktionierenden Starter. Nur wenn eine Anpassung erforderlich ist, darf ein auftragsspezifisches kleines Node-Skript ausserhalb der Produktquellen entstehen. Kein neues Reviewframework.

Die folgenden Verantwortlichkeiten müssen unabhängig von der Implementierungsform abgedeckt sein:

```text
preflightOpenCode()
  -> Version, unterstützte Flags, Provider/Modellbeleg; keine Secrets

prepareReviewInput(baseSha, candidateSha, treeSha)
  -> request.json, kumulativer Diff, Criteria IDs, unveränderte Originalbelegpfade

captureTrackedState(worktree)
  -> HEAD, Tree, Status und Hashes der versionierten Kandidatenquellen

launchReview(command, args, cwd, outputDirectory)
  -> Prozesshandle; stdout/stderr in Dateien; kein shell:true/unsicherer Shellstring

awaitReviewExit(handle)
  -> echter Exitcode, Signal, Start/Ende; Timeout als eigener Fehler

validateReviewResult(request, processResult, beforeState, afterState)
  -> Schema-/SHA-/Modell-/Criteria-/Probe-/Findingprüfung; niemals PASS erfinden
```

Fehler beim Spawn ebenso erfassen wie Fehler beim Exit. Keine Tokens/komplette Umgebungen in `launch.json`. Ein Exitcode 0 bedeutet nur regulären Prozessabschluss, nicht erfolgreiche fachliche Abnahme.

### A7. Reviewerinput

`request.json` enthält mindestens:

```json
{
  "schemaVersion": "1.0",
  "task": "extract-interlis-verification",
  "baseSha": "<actual-base-sha>",
  "candidateSha": "<actual-candidate-sha>",
  "treeSha": "<actual-tree-sha>",
  "worktree": "<absolute-review-worktree>",
  "reviewDirectory": "<absolute-output-directory>",
  "scope": "complete cumulative diff; review only after implementation phases V0-V4",
  "criteria": ["VM-01", "VM-02", "VM-03", "VM-04", "VM-05", "VM-06", "VM-07", "VM-08", "VM-09", "VM-10", "VM-11", "VM-12", "VM-13", "VM-14", "VM-15", "VM-16", "VM-17", "VM-18"],
  "requestedModel": "DeepSeek Flash 1017 via direct DeepSeek API",
  "resolvedModelId": "<verified-real-cli-id>",
  "modelBindingEvidence": "<redacted provenance, not a secret>",
  "documents": ["<specification>", "<execution-plan>", "<evidence-disposition>", "<actual-inventory>", "<actual-decisions>", "<actual-test-ledger>"],
  "evidence": [{"path": "<actual-original-report>", "sha256": "<actual-hash>", "scope": "<what it proves>"}],
  "priorFindings": []
}
```

Platzhalter sind vor dem Aufruf durch tatsächliche Werte zu ersetzen. Eine leere Evidence-Liste oder ein künstlicher Hash ist keine akzeptable Abkürzung.

## B. Vollständiger Auftrag an den unabhängigen Reviewer

Du bist der unabhängige Reviewer, nicht der Implementierer. Prüfe den in `request.json` bezeichneten unveränderlichen Kandidaten von `edigonzales/interlis-java` gegen diesen Nutzerauftrag:

> Die drei zusätzlichen CLI-Source-Sets sollen in ein internes `interlis-verification`-Modul bzw. an ihren fachlich richtigen Testort überführt werden. Gradle bleibt. Keine Jenesis-Migration. Besonders ist zu prüfen, welche `evidenceTest`-Prüfungen heute noch nötig sind und wie sie klarer benannt und organisiert werden. Alle Implementierungsphasen wurden zuvor gezielt getestet. Du prüfst jetzt erst nach allen Phasen unabhängig die Zielerreichung und den Code.

### B1. Eigene Prüfung

Lies die vollständige Spezifikation, den kumulativen Diff und die methodengenaue Evidenzentscheidung. Prüfe anschliessend den tatsächlichen Code. Übernimm keine Erfolgsaussage von Codex ungeprüft.

Bewerte alle VM-01 bis VM-18. Besonders:

1. Hat das neue Modul nur normale Source-Sets, explizite Dependencies und keine Produkt-Rückdependency?
2. Sind alle alten Klassen und Testmethoden wirklich berücksichtigt? Sind Child-Classpaths, FQCN-Strings und relative Gradle-Verknüpfungen korrekt?
3. Sind direkte Compilerregressionen fachlich richtig zugeordnet? Sind Unicode-OID-/Metadatenfälle unverändert aussagekräftig?
4. Wurde `evidenceTest` tatsächlich vereinfacht und nicht nur umbenannt? Besitzt jede Entfernung einen belegten historischen Zweck oder einen wirklich äquivalenten aktiven Ersatz?
5. Wurden alte Norm-/G2-Belege unverändert erhalten, statt durch neue Hashes/Statuswerte eine Abnahme vorzutäuschen?
6. Sind aktuelle Coverage-, Normentscheidungs-, Report- und Manipulationsprüfungen noch wirksam?
7. Sind Unit-/Contractgruppen disjunkt, nicht leer und sinnvoll an Root-/Releaseaufgaben gebunden?
8. Sind Plattform-/SDK-Berichtspfade, Testzählungsdelta, Nodevalidatoren, CI und Fingerprints konsistent?
9. Verwendet ein erfolgreicher Validator wirklich aktuelle vollständige Berichte des Kandidaten, nicht alte Reports aus einem früheren Build?
10. Enthalten Produktdistributionen keine Verifikationsartefakte? Bleiben CLI/LSP-Produktverträge unverändert?
11. Sind behauptete Testläufe echt, passend und nachvollziehbar? Werden nicht ausgeführte Browser-/Plattform-/Releaseprofile ehrlich benannt?
12. Wurden OpenCode, direkte DeepSeek-Modellbindung und der richtige Kandidat tatsächlich verwendet?

### B2. Eigene Gegenproben

Führe selbst gezielte Gegenproben aus. Mindestens die folgenden Risikoklassen, zusammengefasst in wenige kleine Tests, sind unabhängig zu prüfen:

- Taskgraph/selektierte Gruppen: Root-`check` darf nicht wieder historische Norm-/G2-Vollprüfungen mitziehen; explizite Contracts dürfen nicht versehentlich leer sein.
- Berichtspfadmigration: Nur alte XML-Verzeichnisse reichen nicht; fehlende neue Berichte und Doppelzählung werden erkannt.
- Reportintegrität: Hash-/Suite-/Fall-/Skip-/Kandidatenmanipulation wird zurückgewiesen.
- Verlustgefahr beim Entfernen von Evidenztests: mindestens eine riskante SPLIT-/MERGE-/RETIRE-Entscheidung anhand des alten und neuen Codes plus konkreter Gegenprobe überprüfen; Auswahl selbst treffen.
- Modul-/Distributionsisolation: keine Verifikationsdependency oder -klasse in Produktartefakten.
- Eine kleine passende tatsächliche Laufzeitintegration des neuen Task-/Classpathpfads, soweit nicht bereits eine gleichwertige eigene Gegenprobe diese Beobachtung liefert.

Das sind Prüfziele, keine Aufforderung zu sechs teuren Suiten. Nutze kleine Fixtures, bestehende gezielte Tests und bei Bedarf eigene Scratchproben. Eine reine Wiederholung der vom Autor ausgewählten Kommandos ohne eigenen Befund genügt nicht.

Wiederhole keine vollständigen Korpus-/Benchmark-/Browser-/Plattformläufe ohne konkreten Anlass. Prüfe dafür gültige ursprüngliche Rohbelege, deren Kandidat/Inhalt, Eingaben, Toolchain, Artefakte, Exitcodes und Vollständigkeit. Ein alter Bericht eines anderen Produktinhalts ist kein gültiger Ersatz. Stelle keine aktualisierten Testzahlen oder Hashes selbst her, um einen Kandidaten passend zu machen.

### B3. Grenzen

Ändere keine versionierten Dateien. Keine Korrekturen, Commits, Pushs oder weitere Agenten. Scratchtests und Berichte nur im freigegebenen Bereich. Keine Benutzerfragen und keine interaktiven Berechtigungswartezeiten. Ein nicht behebbarer Zugriff-/Tool-/Modell-/Testblocker wird konkret als `BLOCKED` berichtet.

Unterscheide ein echtes unerfülltes Kriterium von einer optionalen Stilpräferenz. Erfinde keine neuen Produktanforderungen. Eine nicht durch diesen Auftrag verlangte vollständige P3-Neufreigabe ist kein automatischer Blocker. Eine für den geänderten Vertrag wirklich notwendige fehlende Prüfung ist dagegen offen.

### B4. Ausgaben

Schreibe `review.md` mit nachvollziehbarer Bewertung und `verdict.json` mindestens in dieser Struktur:

```json
{
  "schemaVersion": "1.0",
  "verdict": "PASS|FAIL|BLOCKED",
  "baseSha": "<actual>",
  "candidateSha": "<actual>",
  "treeSha": "<actual>",
  "reviewer": {
    "tool": "OpenCode",
    "version": "<actual-2.x>",
    "sessionId": "<actual-if-exposed>",
    "provider": "<actual-direct-deepseek-provider>",
    "requestedModelId": "<actual>",
    "reportedModelId": "<actual-or-null>",
    "modelBindingEvidence": "<actual-redacted-basis>"
  },
  "criteria": [{"id": "VM-01", "status": "PASS|FAIL|BLOCKED", "evidence": ["<file:line, test or raw report>"]}],
  "ownProbes": [{"purpose": "<actual>", "command": ["<actual>"], "exitCode": 0, "log": "<actual>"}],
  "inspectedOriginalEvidence": [{"path": "<actual>", "sha256": "<actual>", "assessment": "<actual>"}],
  "findings": [{"id": "R1", "severity": "high|medium|low", "criterion": "VM-06", "path": "<actual>", "location": "<actual>", "problem": "<actual>", "impact": "<actual>", "proof": "<actual>", "requiredCorrection": "<actual>"}],
  "limitations": [],
  "trackedSourcesUnchanged": true
}
```

Im wirklichen Ergebnis alle Criteria IDs aufführen, keine Platzhalter/Alternativwerte stehenlassen. Leere Findings bei echtem PASS sind korrekt; erfundene Probes sind es nicht. Ein PASS verlangt erfüllte Kriterien und keine offenen sachlich begründeten Fehler gegen diesen Auftrag. Ein Prozess-Exit 0 ist kein Beleg für die fachliche Abnahme.

## C. Codex: Urteil auswerten und Schleife schliessen

1. Prozess regulär beendet? Tatsächlicher gewünschter Provider/Modellbezug? SHA/Tree korrekt? Versionierte Dateien unverändert?
2. Beide Reportdateien vorhanden, parsebar, vollständig und inhaltlich konsistent? Alle Criteria IDs enthalten? Eigene Gegenproben dokumentiert?
3. `PASS` nur übernehmen, wenn keine Pflichtprüfung fehlt und keine berechtigten Fehler offenbleiben. Kein eigenes Umschreiben des Reviewerurteils.
4. Bei `FAIL` jedes sachlich begründete Finding beheben, einschliesslich kleiner funktionaler Fehler. Betroffene Tests plus nötige schmale Regressionen wiederholen, neuen Commit erstellen.
5. Frischen Reviewprozess auf dem neuen exakten Kandidaten starten. Vollständiger kumulativer Diff plus frühere Findings und deren Behandlung werden übergeben. Ein Review nur des letzten Fixhunks reicht nicht.
6. Unbegründete Findings mit konkreten Gegenbelegen an den unabhängigen Reviewer zur erneuten Bewertung zurückgeben, nicht einfach als erledigt markieren.
7. Keine endlose identische Fehlerschleife: bei fehlendem technischen Fortschritt Ursache untersuchen; echte unlösbare Blocker offen berichten.
8. Nach gültigem PASS die externen Reportpfade und den **tatsächlich geprüften** finalen Commit mitteilen. Keine anschliessende unbeurteilte Änderung, auch kein stiller Abschluss-Dokumentationscommit.

## D. Primärquellen und vorhandenes Muster

- [OpenCode v2 CLI-Kommandos](https://opencode.ai/v2/docs/cli/commands/)
- [OpenCode v2 Berechtigungen](https://opencode.ai/v2/docs/permissions/)
- Referenzcommit: `docs/implementation/http-network-policy/review/run-review.cjs`.

Die Webdokumentation ist Orientierung; die installierte 2.x-Hilfe und die tatsächlich wirksame Provider-/Rechtekonfiguration müssen vor dem Aufruf geprüft werden. Das vorhandene Muster ist wiederzuverwenden, nicht mit seinen alten absoluten Pfaden und Modellannahmen zu kopieren.
