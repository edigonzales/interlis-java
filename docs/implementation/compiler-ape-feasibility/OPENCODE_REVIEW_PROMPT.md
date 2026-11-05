# Unabhängiger Gesamt-Review — OpenCode 2.x / DeepSeek Flash 1017

## 1. Zeitpunkt und Gegenstand

Dieses Dokument ist zugleich Anleitung für Codex als Reviewkoordinator und Auftrag an den unabhängigen Reviewer.

**Primäragent:** Codex. **Reviewer:** frische unabhängige OpenCode-2.x-Sitzung mit DeepSeek Flash 1017 über die direkte DeepSeek-API. Kein Codex-Subagent, keine zweite Antwort derselben Implementierungssitzung und keine Fortsetzung eines alten Reviews als Ersatz.

Der fachliche Review startet erst nach F0–F5 auf der positiven Route bzw. nach dem korrekt dokumentierten terminalen F5-Abschluss und dem Kandidatencommit. Kein Review nach jeder Einzelphase. Vorher ist nur der technische CLI-/Auth-/Modellpreflight zulässig.

Reviewgegenstand: tatsächliche Zielerreichung, Code und Konfiguration des Prototyps, Sicherheits-/Scopegrenzen, Nachweisqualität und die Richtigkeit der erklärten Machbarkeitsentscheidung. Nicht Gegenstand: pauschale Produktionsfreigabe, komplette P3-/Browser-/LSP-/Extensionabnahme oder eine unbeauftragte Toolchainportierung.

## 2. Technischer Preflight

### Installation und Aufruf

Vorhandene OpenCode-Installation und funktionierende lokale Reviewstarter verwenden. PATH, App-Bundle oder vorhandenes `~/.opencode/bin/` untersuchen, aber keinen absoluten Benutzerpfad als garantiert voraussetzen.

Tatsächlich feststellen:

- CLI-Version ist 2.x;
- verfügbare `run`-Optionen der installierten Version;
- Modell-/Providerkennung und Authentifizierung im separaten Reviewarbeitsverzeichnis;
- nichtinteraktive Ausführung, Dateianhänge, JSON-/Sitzungslogs;
- gültige Berechtigungsregeln genau dieser Version.

Keine globale Aktualisierung, keine neue Installation und keine ungeprüfte 1.x-Konfiguration. Falls ein historisch verwendetes Flag fehlt, unterstützten gleichwertigen Aufruf verwenden statt eine neue CLI-Version zu erzwingen.

### Modellbindung

Die am 4. November 2026 gelesene direkte DeepSeek-Dokumentation ordnet `deepseek-flash` **DeepSeek-V4.1-Flash-1017** zu. Für den tatsächlichen Lauf aktuelle Zuordnung prüfen und belegen. Lokale OpenCode-Provider-ID und Gross-/Kleinschreibung aus der realen Installation entnehmen.

Die API kann nur einen Alias zurückmelden; dann exakt Alias plus offizielle Zuordnungsquelle/Zeitpunkt dokumentieren und keine nicht gelieferte Revisionskennung erfinden. Wird der Alias später anders bedient und existiert kein verifizierbarer 1017-Zugriff, ist das `BLOCKED`, kein Grund für einen stillen Modellwechsel.

Vorhandene OpenCode-Anmeldung nutzen. Keinen zusätzlichen API-Key in ENV verlangen, sofern nicht nötig. Keine Credentials lesen/exportieren, keine sensitiven Authfiles oder vollständige Prozessumgebungen protokollieren. Kein Fallback auf OpenCode-Zen, andere Anbieter oder andere DeepSeek-Varianten.

### Aufrufmuster

Nur nach Prüfung der tatsächlich unterstützten Optionen verwenden:

```sh
opencode run \
  --standalone \
  --auto \
  --agent build \
  --model <LOKAL_VERIFIZIERTE_PROVIDER_MODELLKENNUNG> \
  --format json \
  --file <ABSOLUTES_REQUEST_JSON> \
  --file <ABSOLUTER_REVIEWAUFTRAG> \
  'Prüfe den Compiler-APE-Machbarkeitsauftrag unabhängig am exakten Commit. Lies Anforderungen, kumulativen Diff und Originalbelege. Führe eigene gezielte Gegenproben aus. Ändere keine Produktdateien. Schreibe review.md und verdict.json mit getrenntem Reviewurteil und Machbarkeitsentscheidung.'
```

Argumente als Liste an `spawn`/`ProcessBuilder` übergeben. `--auto` ersetzt keine expliziten Rechteverbote. Den Prozess bis zum tatsächlichen Abschluss beobachten; Exitcode, Signal und Timeout erfassen. Ein abgebrochener Stream mit dem Wort PASS ist kein Urteil.

Vorhandene kleine Reviewstarter wiederverwenden oder schmal anpassen. Kein neues allgemeines Agentenframework und keine Python-Voraussetzung.

## 3. Kandidat und Schreibschutz

Ein sauberer detached Worktree oder eine gleichwertige isolierte Kopie liegt exakt auf `CANDIDATE_SHA`. Der Reviewer erhält `BASE_SHA`, Candidate-Tree, vollständigen kumulativen Diff sowie alle acht Auftragsdokumente.

Separate externe Ablage:

```text
<belegroot>/reviews/<candidate-sha>/round-01/
  request.json
  REVIEW_REQUEST.md
  cumulative.diff
  evidence-index.json
  launch.json
  opencode.jsonl
  stderr.log
  process-result.json
  output/
    review.md
    verdict.json
    probes/
```

Versionierte Kandidatenquellen, Vendorarchive, ursprüngliche Rawreports und bestehende Originalbelege sind nur lesbar. Der Reviewer darf eigene Skripte/Fixtures in einem abgegrenzten Scratchverzeichnis erstellen, dort Mutationstests durchführen und gegebenenfalls Buildausgaben für seine kleinen Gegenproben erzeugen. Er darf Produktdateien weder reparieren noch committen/pushen/veröffentlichen. Keine weiteren Agenten starten.

Eine Edit-Tool-Sperre alleine schützt nicht vor Shellschreibzugriffen. Geeignete vorhandene Sandbox-/Berechtigungsmechanismen nutzen; normale erlaubte Proben dürfen nicht mitten im autonomen Lauf auf Benutzerfreigaben warten. Keine globalen Userkonfigurationen oder Sicherheitsmechanismen deaktivieren.

Vor/nach dem Review Git-SHA, Tree, Status und relevante Quell-/Originalbeleghashes vergleichen. Unerklärte Änderungen machen den Review ungültig. Mutationstests erhalten eigene beschreibbare Kopien, nicht die Originalbelege.

## 4. Belegpaket

Codex stellt bereit:

- sämtliche Anforderungen und die 32 Kriterien;
- tatsächliche operative Baseline, Toolchainbericht, Status, Testledger und Machbarkeitsbericht;
- Compilerinput-, Toolchain-, Architektur-/Fusions- und finales Bundlemanifest;
- Rohlogs und tatsächliche Ausführungsbelege, getrennt nach JVM-Referenz, APE-Kandidat und eventuellem Testkandidaten;
- Belege des nativen Plattform-/Start-/Hermetik-/TLS-Verhaltens;
- explizite Wiederverwendungsbegründungen für ältere unveränderte Runs;
- alle bisherigen Findings und Korrekturbelege in Folgerunden.

`evidence-index.json` enthält Pfad, SHA-256, Herkunft, Aussage und Bedeutung jeder verwendeten Datei. Eine Implementiererzusammenfassung ist ein Wegweiser, kein Beweis. Geheimnisse, vollständige private Umgebungen und unnötige grosse Archive nicht an das Modell senden; grosse Dateien read-only zugänglich machen und nur benötigte Metadaten/Hashes einlesen lassen.

## 5. Maschinenlesbare Anfrage

Die Form ist ein Beispiel mit Platzhaltern, kein ausgeführter Review:

```json
{
  "schemaVersion": "1.0",
  "assignment": "compiler-ape-feasibility",
  "baseSha": "<BASE_SHA>",
  "candidateSha": "<CANDIDATE_SHA>",
  "candidateTreeSha": "<TREE_SHA>",
  "workingDirectory": "<ABSOLUTER_WORKTREE>",
  "reviewDirectory": "<ABSOLUTER_OUTPUTORDNER>",
  "scratchDirectory": "<ABSOLUTER_SCRATCHORDNER>",
  "requirementsDirectory": "docs/implementation/compiler-ape-feasibility",
  "diffFile": "<ABSOLUTER_KUMULATIVER_DIFF>",
  "evidenceIndex": "<ABSOLUTER_EVIDENCE_INDEX>",
  "claimedFeasibility": "<GO|GO_WITH_LIMITATIONS|NO_GO|INCONCLUSIVE>",
  "artifactSha256": "<TATSAECHLICHER_HASH_ODER_NULL_BEI_KEINEM_KANDIDATEN>",
  "requiredCriteria": [
    "AP-01", "AP-02", "AP-03", "AP-04", "AP-05", "AP-06", "AP-07", "AP-08",
    "AP-09", "AP-10", "AP-11", "AP-12", "AP-13", "AP-14", "AP-15", "AP-16",
    "AP-17", "AP-18", "AP-19", "AP-20", "AP-21", "AP-22", "AP-23", "AP-24",
    "AP-25", "AP-26", "AP-27", "AP-28", "AP-29", "AP-30", "AP-31", "AP-32"
  ],
  "modelRequirement": {
    "provider": "direct DeepSeek API",
    "variant": "DeepSeek Flash 1017",
    "requestedModelId": "<VERIFIZIERTE_LOKALE_KENNUNG>",
    "mappingEvidence": "<OFFIZIELLE_ZUORDNUNG_MIT_ZEITPUNKT>"
  },
  "previousRounds": [],
  "rules": {
    "trackedSourcesReadOnly": true,
    "repairProductCode": false,
    "spawnFurtherAgents": false,
    "pushOrPublish": false,
    "repeatBroadTestsWithoutConcreteNeed": false
  }
}
```

Bei tatsächlich fehlendem Artefakt `artifactSha256` als JSON `null` ausgeben, nicht die Platzhalterzeichenkette oder einen erfundenen Hash. Früh blockierte Phasen im Belegpaket kausal erklären.

## 6. Direkt an den Reviewer zu übergebender Auftrag

> Du bist der unabhängige Reviewer, nicht der Implementierer. Prüfe den exakten Kandidaten gegen den vollständigen Compiler-APE-Machbarkeitsauftrag AP-01 bis AP-32. Lies kumulativen Diff und Originalbelege selbst. Übernimm weder die Erfolgsaussagen noch die Blockerdiagnose von Codex ungeprüft.
>
> Prüfe besonders, dass echte Jenesis-Compiler-JARs verwendet werden; kein Gradle-/Maven-/JVM-Fallback existiert; Toolchainpins und beide Architekturinputs stimmen; ein wirklicher APE-Zusammenbau vorliegt; die final getestete Binary identisch ist; native Initialisierung, Ressourcen, TLS und Hostpfade nicht die Builderumgebung einschleppen; die Extension, der LSP und normale Builds unverändert bleiben.
>
> Unterscheide eine funktionierende Hauptarchitektur, einen zusammengesetzten APE und tatsächlich getestete Plattformen. WSL, Rosetta, QEMU, Dateisuffixe, Headerprüfung und Herstellerbehauptungen sind kein Ersatz für die fehlende Zielausführung. Prüfe Direktstart, Shell-/Loaderverhalten und den Nachweis ohne erreichbare JVM/Buildartefakte.
>
> Lies die CLI-Paritätsdaten und deren Normalisierung. Ein semantischer Fehler, eine andere Diagnosefolge oder eine falsche Zahl darf nicht entfernt oder gerundet sein. Prüfe, dass die positive TLS-Kontrolle wirklich funktioniert, während untrusted CA und falscher Host abgelehnt werden. Eine Test-CA darf nicht unbemerkt im Standardkandidaten enthalten sein.
>
> Prüfe die Machbarkeitsentscheidung unabhängig. NO_GO braucht einen reproduzierbaren technischen Befund; fehlende Infrastruktur ist INCONCLUSIVE. GO_WITH_LIMITATIONS darf keine fehlenden Pflichtplattformtests, JVM-Fallbacks, unsichere TLS-Abkürzungen oder ausgefallenen Pflichtfunktionen kaschieren. Ein sauber belegtes negatives Ergebnis kann als Untersuchung PASS sein; das ist kein positiver Produktnachweis.
>
> Führe mindestens eine eigene kleine Artefakt-/Manifestmanipulationsprobe und eine passende Comparator-/Casevollständigkeitsgegenprobe aus. Bei frühem Blocker zusätzlich dessen minimalen Reproducer oder eine unabhängige Prüfung der belastbaren Fehlbelege. Eine kleine reale Ausführung auf einem tatsächlich verfügbaren Host ist sinnvoll; wiederhole nicht ohne konkrete Frage beide teuren Native-Builds oder die gesamte Plattformmatrix.
>
> Unveränderte Originalbelege dürfen nach geprüftem Inhaltsabgleich wiederverwendet werden. Ein neuer Git-SHA alleine erzwingt keinen Volltest; ein neuer Binaryhash erlaubt aber keine Übertragung alter Laufzeitresultate. Ordne Findings nach Wirkung und nenne Datei/Fundstelle, Reproduktion, betroffenen Vertrag und kleinsten sinnvollen Retest.
>
> Ändere keine versionierten Produktdateien, keine Originalbelege und keine Vendorartefakte. Starte keine weiteren Agenten. Schreibe review.md und verdict.json in das vorgegebene Ausgabeverzeichnis und beende den Prozess regulär.

## 7. Urteil

`review.md` erläutert Codebefunde, eigene Proben, geprüfte Originalbelege, erfüllte/offene Kriterien und die fachliche Schlussfolgerung.

Beispielform für `verdict.json`; keine vorgefüllten Erfolge übernehmen:

```json
{
  "schemaVersion": "1.0",
  "assignment": "compiler-ape-feasibility",
  "verdict": "<PASS|FAIL|BLOCKED>",
  "feasibility": "<GO|GO_WITH_LIMITATIONS|NO_GO|INCONCLUSIVE>",
  "baseSha": "<BASE_SHA>",
  "candidateSha": "<CANDIDATE_SHA>",
  "candidateTreeSha": "<TREE_SHA>",
  "artifactSha256": null,
  "session": {
    "opencodeVersion": "<TATSAECHLICHE_VERSION>",
    "sessionId": "<TATSAECHLICHE_ID_ODER_EXPLIZIT_NICHT_GELIEFERT>",
    "providerId": "<TATSAECHLICHER_PROVIDER>",
    "requestedModelId": "<TATSAECHLICHE_KENNUNG>",
    "modelMappingEvidence": "<QUELLE_UND_ZEITPUNKT>"
  },
  "criteria": [
    {"id": "AP-01", "status": "<PASS|FAIL|BLOCKED>", "reason": "<BEGRUENDUNG>", "evidence": ["<BELEG>"]}
  ],
  "ownProbes": [
    {"purpose": "<PRUEFFRAGE>", "command": ["<PROGRAMM>", "<ARGUMENT>"], "exitCode": 0,
     "log": "<ECHTES_LOG>", "observation": "<TATSAECHLICHE_BEOBACHTUNG>"}
  ],
  "platformEvidence": [],
  "findings": [],
  "limitations": [],
  "conclusion": "<BEGRUENDETES_URTEIL>"
}
```

Bei negativem terminalem Abschluss darf ein **Untersuchungskriterium** PASS sein, weil die Nichterfüllung korrekt belegt und eingeordnet ist. Der zugehörige nicht ausgeführte Produktcase bleibt dennoch `NOT_RUN_BLOCKED`; der Reviewer muss das im Grundtext und den referenzierten Nachweisen klar unterscheiden.

Codex prüft mechanisch: tatsächliche Reviewerprovenienz, regulären Prozessabschluss, richtige Modellzuordnung, exakten Commit/Tree, vollständige Behandlung aller Kriterien, wirkliche eigene Proben, Übereinstimmung von Review- und Machbarkeitsurteil sowie unveränderte Originalquellen. Keine leeren oder widersprüchlichen Urteile akzeptieren. Codex darf den Reviewertext nicht selbst in PASS umschreiben.

## 8. Korrekturen und Folgerunden

Berechtigte Findings behebt Codex selbst. Vor Retests Auswirkungsmatrix aus `TEST_AND_EVIDENCE_POLICY.md` anwenden. Geänderte Quellen/Artefakte und betroffene Verbraucher benennen. Nur nötige Tests ausführen; gültige ursprüngliche Nachweise behalten.

Neuen Stand committen, neuen Kandidaten-/Treehash erfassen und einen frischen unabhängigen Gesamt-Review starten. Dieser erhält den gesamten kumulativen Diff sowie frühere Findings und ihre Behandlung. Ein Review des alten Commits gilt nicht automatisch für den neuen.

Unbegründete Findings nicht einfach streichen: Gegenbeleg liefern und Auflösung vom Reviewer bestätigen lassen. API-/Sandbox-/Umgebungsfehler beheben, soweit im Scope. Kein Modellfallback und keine erfundenen Logs. Nicht behebbarer Reviewblocker bleibt sichtbar.

Nach finalem PASS keine stille Produkt-/Dokumentationsänderung. Reviewbelege extern belassen und ihre Pfade im Abschlussbericht nennen. Kein Push oder Release.
