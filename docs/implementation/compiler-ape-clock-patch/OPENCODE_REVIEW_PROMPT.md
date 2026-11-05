# Autonomer unabhängiger Gesamt-Review — lokaler APE-Clock-Patch

Dieses Dokument enthält sowohl die Ablaufanweisung für Codex als Koordinator als auch den in Abschnitt 5 direkt übergebbaren Reviewerauftrag.

## 1. Zeitpunkt, Identität und Umfang

Primäragent Codex. Zweiter Agent: neue unabhängige OpenCode-2.x-Sitzung mit **DeepSeek Flash 1017 über die direkte DeepSeek-API**. Kein Codex-Subagent, kein anderer Provider als stiller Ersatz, keine Fortsetzung der Implementierungssitzung.

Fachlicher Review erst nach C0–C5 und dem Kandidatencommit, oder nach einem korrekt belegten terminalen Abschluss. Kein Review nach jeder Phase. Der Reviewer prüft den kompletten Auftrag, den kumulativen Diff, die erlaubte Patchwirkung und die tatsächliche Zielerreichung. Vorwissen des Primäragenten ist ein Hinweis, keine verbindliche Wahrheit.

CP-01 bis CP-32 sind massgeblich. Der Reviewer muss ausdrücklich `patchStatus`, `compilerApeStatus` und sein eigenes `verdict` unterscheiden.

## 2. Technischer Preflight und Modellbindung

Vorhandenen funktionierenden Reviewstarter bevorzugt wiederverwenden; enge Anpassung statt neues Agentenframework. OpenCode-Version, tatsächlich unterstützte `run`-Argumente, Projekt-/Providerkontext und nichtinteraktive Rechte vor dem ersten fachlichen Review prüfen.

Die am 5. November 2026 gelesene direkte DeepSeek-Dokumentation ordnet API-Modell `deepseek-flash` der Variante **DeepSeek-V4.1-Flash-1017** zu. Für den tatsächlichen Review Zeitpunkt und offizielle Zuordnung neu verifizieren. Lokale OpenCode-Provider-/Modell-ID aus realer Installation entnehmen. Beispiel kann `deepseek/deepseek-flash` sein, ist aber keine Annahme über die Userkonfiguration. [S12–S14]

Ein Alias ist kein unveränderlicher Versionspin. Wenn die API nur den Alias meldet, diesen und die offizielle Zuordnung ehrlich protokollieren, keine erfundene 1017-Revision als APIantwort ausgeben. Hat sich die Zuordnung geändert und ist 1017 nicht verfügbar, Review `BLOCKED` statt anderes Modell.

Bestehende OpenCode-Authentifizierung verwenden. Kein separater API-Key in Environment verlangen, wenn die vorhandene Anmeldung funktioniert. Keine Authfiles lesen/dumpen, keine Tokens in Prompt, argv oder Logs. Keine globale Neuinstallation/Aktualisierung oder Änderung der persönlichen Standardprovider. Der separate Worktree muss den tatsächlichen Providerzugang haben; einen Zugang aus einem anderen Projekt nicht einfach voraussetzen.

CLI-Grundmuster nach Prüfung der installierten Hilfe:

```sh
opencode run \
  --standalone \
  --auto \
  --model <VERIFIZIERTE_LOKALE_PROVIDER_MODELL_ID> \
  --format json \
  --file <ABSOLUTES_REQUEST_JSON> \
  --file <ABSOLUTER_REVIEWAUFTRAG> \
  'Prüfe den lokalen Compiler-APE-Clock-Patch unabhängig am exakten Kandidatencommit. Lies den ganzen Auftrag und Originalbelege. Führe eigene kleine Gegenproben aus. Ändere keine Produktdateien. Schreibe review.md und verdict.json; trenne Patchkorrektheit, Compiler-APE-Status und Reviewurteil.'
```

Das ist ein Aufrufmuster, kein bereits getestetes lokales Kommando. Unsupported Flags nicht blind setzen; unterstützten gleichwertigen Ablauf wählen. Argumente als Liste übergeben. Kein `--continue` und keine alte `--session`. Auswahl eines geeigneten lokalen Reviewagents ist erlaubt, dessen tatsächliche Modell- und Rechtepolitik muss stimmen.

## 3. Isolation, Rechte und Prozessabschluss

Kandidat in einem separaten sauberen detached Worktree oder gleichwertig isolierter Kopie exakt auf `CANDIDATE_SHA`. `BASE_SHA`, Candidate-Tree, vollständiger Diff und alle neun Spezdokumente zugänglich machen. Ursprüngliche Belege/Toolchain/Artefakte nur lesbar; eigener beschreibbarer Review-/Scratchbereich.

Eine Edit-Tool-Sperre schützt nicht vor Shellschreibzugriff. Vorhandene Sandbox/Dateirechte und eine enge geeignete Shellerlaubnis nutzen. OpenCode 2.x verwendet seine eigene Policyform; keine ungeprüfte V1-Konfiguration übernehmen. `--auto` darf nur erwartete erlaubte Aktionen autonom freigeben, nicht Sicherheitsverbote aushebeln. Der autonome Lauf soll bei normalen erlaubten Proben nicht auf Benutzerfragen warten. [S13]

Erlaubt: lesen, Diff/Quellen prüfen, gezielte Tests und native kleine Proben auf verfügbaren Hosts ausführen, Scratchfixtures/-skripte erstellen, Mutationstests an **Kopien**, eigene Ergebnisse schreiben.

Nicht erlaubt: versionierte Produktquellen/Specs/Tests reparieren, Originalbelege oder Vendorbytes verändern, fremde Prozesse/Container berühren, committen/pushen/veröffentlichen, Credentials lesen, weitere Agenten starten oder andere Modelle nutzen.

Hash-/Gitstatusvergleich vor und nach Review. Unerklärte Quell-/Artefaktmutationen invalidieren den Lauf. Keine blossen „readonly“-Behauptungen ohne passende Kontrolle. Temporäre Paketierungs-/Testinputs erhalten eigene private Kopien, keine Bearbeitung der Originale.

Auf echten regulären Prozessabschluss warten. JSONL, stderr, Sitzungs-/Modellmetadaten, Exitcode, Signal, Timeout und eigene Unterprozesslogs erfassen. Ein Stream mit `PASS` vor Abbruch oder ein erfolgreicher Prozessstart ist kein gültiges Reviewerurteil.

## 4. Belegpaket

Externe Ablage, beispielsweise:

```text
<evidence-root>/reviews/<candidate-sha>/round-01/
  request.json
  REVIEW_REQUEST.md
  cumulative.diff
  evidence-index.json
  launch.json
  opencode.jsonl
  stderr.log
  process-result.json
  output/review.md
  output/verdict.json
  scratch/
```

Das Paket enthält relevante Originalbelege für Vendorinspektion, Routenauswahl, Guards, Patchproduktion, korrigierte und Originalclock, TLS positiv/negativ, Compilerbildproduktion/Fusion, echte Produkt-/Plattformfälle und begründete Wiederverwendungen. Originaldateien für Nachprüfung zugänglich machen; keine riesigen Binärdaten im Prompt.

Jeder Indexeintrag benennt Aussage, tatsächliche Herkunft, Pfad, SHA-256 und Geltungsbereich. Eine Markdownzusammenfassung ohne Rohbelege ist kein Ersatz. Keine stillen Referenzen auf verschwundene lokale Pfade; wenn Originale fehlen, Einschränkung ausweisen.

In Folgerunden vorherige Findings, Korrekturen und gezielte Retestbelege zusätzlich liefern, aber der **gesamte kumulative Diff seit BASE_SHA** bleibt Gegenstand.

## 5. Direkt an den Reviewer zu übergebender Auftrag

> Du bist der unabhängige Reviewer des lokalen Compiler-APE-Clock-Patches, nicht sein Implementierer. Prüfe den exakten Candidate-SHA und Tree gegen SPECIFICATION und CP-01 bis CP-32. Lies die Anforderungen, den kumulativen Diff und die Originalbelege selbst. Die Zusammenfassung von Codex ist keine autoritative Erfolgsaussage.
>
> Prüfe zuerst die enge Scopeausnahme: Wirklich nur `currentTimeMillis()J` erhält `CLOCK_REALTIME`, der Fehlertext passt, `nanoTime`, `Instant`, TLS und andere Vendor-/Produktmethoden bleiben unverändert. Bestätige die tatsächlichen Uninterruptible-/StackValue-/NoTransitions-Verträge anhand der gepinnten Builderbytes, nicht nur eines öffentlichen neueren Sourcesnapshots.
>
> Prüfe bei Route A methodengenaue Deaktivierung und lokale Ersatzklasse, Cosmo-Guard, Discovery und Compile-only-Abhängigkeiten. Keine unkontrollierte doppelte Substitution und keine globale Deaktivierung. Bei B prüfe, warum A nicht sauber ging, die strukturierte einzige Methodendifferenz, alle anderen unveränderten JAR-Payloads und den getrennten abgeleiteten Vendorbaum. Keine Signaturentfernung. Es darf nur eine aktive Strategie geben.
>
> Prüfe die sieben unveränderten Produkt-JARs, vollständige Patch-/Vendor-/Generatorbindung, echte Optionalsemantik des Builds und die gemeinsame Identität beider nativer Architekturimages. Gemischte Slices, falsche/neu gehashte Receipts oder bereits korrigierte Vendorinputs müssen vor teuren Builds scheitern.
>
> Prüfe den echten Originalfehler und die korrigierte native Clockprobe gegen unabhängige Hostzeit; mehrere Samples/Thread und `nanoTime`-Differenzen. Ein HTTP-Success allein bestätigt keine sichere TLS-Implementierung. Positive TLS-Kontrolle vor/nach negatives, genaue Ablehnungsursachen für CA/Host/expired/future und Testtrust nur zur Laufzeit sind zentral.
>
> Prüfe die tatsächlichen neuen Compiler- und Repositoryfälle, nicht nur einen isolierten Probe-PASS. Keine semantischen Unterschiede wegnormalisieren. Neue APE-Bytes brauchen passende neue Laufzeitbelege. Unveränderte JVM-/Validator-/Comparatorbelege dürfen nach eigenem Inputabgleich weiter gelten.
>
> Unterscheide native macOS-/Linux-/Windows-Ausführung von Emulation, die bekannte Loaderbeigabe von echtem Direktstart und ein korrektes lokales Patchresultat von vollständiger Compiler-APE-Abnahme. Ein fehlender Pflichtzielrunner ist kein NO_GO-Beweis, aber auch kein GO. Alte NO_GO-/Fehler-/Timeoutbelege bleiben historische Originale.
>
> Führe eigene zielgerichtete Gegenproben aus: mindestens eine falsche Vendor-/Patchbindung, eine gemischte oder nachträglich manipulierte Artefakt-/Receiptvariante und eine echte kleine Clockausführung des vorhandenen korrigierten Artefakts auf verfügbarer Umgebung. Prüfe den Zusammenhang positiver/negativer TLS-Fälle selbst; ein kleiner eigener TLS-Lauf mit bestehenden Fixtures ist vorzuziehen, wenn ausführbar. Bei fehlender Ausführungsmöglichkeit Originalbelege unabhängig prüfen und den Umfang ausdrücklich begrenzen, niemals eigene Ausführung vortäuschen.
>
> Prüfe die Struktur der nicht betroffenen Mitglieder, insbesondere `nanoTime`. Wiederhole nicht ohne konkrete offene Frage beide grossen Native-Builds oder vollständige Korpus-/Benchmark-/Plattformläufe. Ein eigenständiger Reviewer braucht eigene Analyse und Gegenproben, keine schematische Testverdopplung.
>
> Findings enthalten Datei/Fundstelle, tatsächliche Auswirkung, betroffenen Vertrag, Nachweis/Reproduktion und kleinsten sinnvollen Retest. Nenne auch offene Nachweise. Ändere keine versionierten Produktdateien oder Originalbelege, starte keine weiteren Agenten. Schreibe `review.md` und `verdict.json` in den vorgegebenen Ordner und beende den Prozess regulär.

## 6. Anfrageformat

Beispiel mit Platzhaltern, kein tatsächliches Resultat:

```json
{
  "schemaVersion": "1.0",
  "assignment": "compiler-ape-clock-patch",
  "baseSha": "<BASE_SHA>",
  "candidateSha": "<CANDIDATE_SHA>",
  "candidateTreeSha": "<TREE_SHA>",
  "worktree": "<ABSOLUTER_REVIEW_WORKTREE>",
  "output": "<ABSOLUTER_REVIEW_OUTPUT>",
  "scratch": "<ABSOLUTER_SCRATCHORDNER>",
  "requirements": "docs/implementation/compiler-ape-clock-patch",
  "diff": "<ABSOLUTER_KUMULATIVER_DIFF>",
  "evidenceIndex": "<ABSOLUTER_EVIDENCE_INDEX>",
  "strategy": "<SUBSTITUTION_JAR|DERIVED_BUILDER_JAR>",
  "compatibilityDigest": "<SHA256_ODER_NULL>",
  "candidateApeSha256": "<SHA256_ODER_NULL>",
  "claimedPatchStatus": "<VALIDATED_ON_AVAILABLE_HOSTS|FAILED|BLOCKED>",
  "claimedCompilerApeStatus": "<GO|GO_WITH_LIMITATIONS|NO_GO|INCONCLUSIVE>",
  "model": {
    "providerRequirement": "direct DeepSeek API",
    "variantRequirement": "DeepSeek Flash 1017",
    "requestedModelId": "<TATSAECHLICHE_LOKALE_ID>",
    "mappingEvidence": "<OFFIZIELLE_QUELLE_UND_ZEITPUNKT>"
  },
  "requiredCriteria": [
    "CP-01", "CP-02", "CP-03", "CP-04", "CP-05", "CP-06", "CP-07", "CP-08",
    "CP-09", "CP-10", "CP-11", "CP-12", "CP-13", "CP-14", "CP-15", "CP-16",
    "CP-17", "CP-18", "CP-19", "CP-20", "CP-21", "CP-22", "CP-23", "CP-24",
    "CP-25", "CP-26", "CP-27", "CP-28", "CP-29", "CP-30", "CP-31", "CP-32"
  ],
  "previousRounds": [],
  "rules": {
    "trackedSourcesReadOnly": true,
    "originalEvidenceReadOnly": true,
    "repairCode": false,
    "spawnOtherAgents": false,
    "pushOrPublish": false,
    "repeatBroadTestsWithoutConcreteNeed": false
  }
}
```

Fehlende Artefakte als echtes JSON `null`, nicht als Zeichenkette `"null"` oder erfundenen Hash. Bei terminalem Abschluss fehlende Strategie/Artefakte ebenfalls ehrlich abbilden. Echte Erfolgsanfragen enthalten keine Platzhalter.

## 7. Urteilformat und Akzeptanz

```json
{
  "schemaVersion": "1.0",
  "assignment": "compiler-ape-clock-patch",
  "verdict": "<PASS|FAIL|BLOCKED>",
  "patchStatus": "<VALIDATED_ON_AVAILABLE_HOSTS|FAILED|BLOCKED>",
  "compilerApeStatus": "<GO|GO_WITH_LIMITATIONS|NO_GO|INCONCLUSIVE>",
  "baseSha": "<BASE_SHA>",
  "candidateSha": "<CANDIDATE_SHA>",
  "candidateTreeSha": "<TREE_SHA>",
  "compatibilityDigest": null,
  "candidateApeSha256": null,
  "session": {
    "opencodeVersion": "<VERSION>",
    "sessionId": "<ECHTE_ID_ODER_EXPLIZIT_NICHT_GELIEFERT>",
    "providerId": "<PROVIDER>",
    "requestedModelId": "<MODELL>",
    "modelMappingEvidence": "<QUELLE_MIT_ZEITPUNKT>"
  },
  "criteria": [
    {"id": "CP-01", "status": "<PASS|FAIL|BLOCKED>", "reason": "<BEGRUENDUNG>", "evidence": ["<ORIGINALBELEG>"]}
  ],
  "ownProbes": [
    {"question": "<PRUEFFRAGE>", "command": ["<PROGRAMM>", "<ARGUMENT>"], "exitCode": null,
     "rawLog": "<BELEG>", "observation": "<TATSAECHLICHE_BEOBACHTUNG>"}
  ],
  "platformCoverage": [],
  "findings": [],
  "limitations": [],
  "conclusion": "<UNABHAENGIG_BEGRUENDETE_SCHLUSSFOLGERUNG>"
}
```

Tatsächliches Urteil behandelt alle CP-IDs, nicht nur die exemplarische erste Zeile. Nicht zutreffende Strategieunterpunkte im Grundtext kennzeichnen; fehlende notwendige Laufbelege nicht als N/A erklären. Bei terminaler sauberer Untersuchung kann Review PASS sein, während Produktcases offen/fehlgeschlagen bleiben; der Text muss dies eindeutig tragen.

Codex validiert: richtige echte Modellzuordnung, regulärer Prozessabschluss, passender Commit/Tree/Artefaktbezug, vollständige Kriterienbehandlung, reale eigene Proben, widerspruchsfreie Ergebnisstatus und keine unaufgelösten sachlich berechtigten Findings. Ein leeres oder generisches „alles gut“ ist ungültig. Codex darf das Urteil nicht selbst schreiben oder zu PASS umändern.

## 8. Fix-/Review-Schleife

Bei FAIL berechtigte Findings korrigieren. Unbegründete Findings mit technischen Gegenbelegen dem frischen Reviewer zur erneuten Beurteilung vorlegen, nicht still ignorieren. Retestumfang nach TEST_AND_EVIDENCE_POLICY. Neuer lokaler Commit, neue Sitzung, neuer kompletter Diff und frühere Runden dazu.

Ein BLOCKED wegen API-/Toolausfall erlaubt technische Behebung und erneuten Lauf. Keine globale Neukonfiguration auf Verdacht, kein anderer Anbieter. Bei verbleibendem echten Blocker den Implementierungscommit als noch nicht unabhängig validiert ausweisen.

Finale Reviewdateien bleiben extern dem geprüften Commit zugeordnet. Sie müssen nicht in genau den Commit aufgenommen werden, den sie beurteilen. So entsteht keine selbstreferenzielle Kette aus „Urteil committen, dadurch ungeprüften Commit erzeugen“. Nach einem PASS keine neuen Produkt-/Dokumentänderungen verschweigen.
