# Unabhängiger Gesamt-Review — OpenCode 2.x / DeepSeek Flash 1017

## 1. Auftrag und Zeitpunkt

Dieses Dokument richtet sich sowohl an Codex als autonomen Reviewkoordinator als auch an den unabhängigen Reviewer.

**Primäragent: Codex. Reviewer: eine frische unabhängige OpenCode-2.x-Sitzung mit DeepSeek Flash 1017 über die direkte DeepSeek-API.** Kein Codex-Subagent als Ersatz. Kein zweiter Text aus derselben Implementierungssitzung.

Die fachliche Validierung startet **erst nach A0–A5 und dem Commit des geprüften Gesamtkandidaten**. Vorher darf die Verfügbarkeit von CLI, Provider und Modell technisch geprüft werden. Kein eigenständiges Review nach jeder Phase.

Reviewgegenstand sind Zielerreichung, Code, Build-/Testkonfiguration, Dokumentation und die belastbaren Nachweise von Auftrag A. Nicht Gegenstand sind eine JPMS-, Wasm-, neue jlink-/jpackage-, APE-/Cosmopolitan- oder vollständige P3-Migration. Die begrenzte Editor-Kompatibilität nach `EDITOR_COMPATIBILITY.md` ist dagegen ausdrücklich Teil des Reviews; keine dauerhafte Extensionmigration.

## 2. Technischer Preflight durch Codex

### 2.1 Tatsächliche Installation

Bestehendes `opencode` über PATH und vorhandene bekannte Projektstarter untersuchen. Als mögliche zusätzliche lokale Installationsorte kommen die vorhandene OpenCode-App/CLI oder `~/.opencode/bin/` in Frage; keinen Pfad blind fest voraussetzen.

Vor dem echten Review tatsächlich prüfen:

- genaue OpenCode-Version ist 2.x;
- `run --help` und relevante Modell-/Providerabfrage der installierten Version;
- unterstützte nichtinteraktive Flags, Dateianhänge, JSON-Ausgabe und Sitzungsmetadaten;
- Verhalten im separaten Reviewarbeitsverzeichnis;
- aktuelle gültige Berechtigungskonfiguration für genau diese Version.

Kein globales Update, keine zusätzliche Installation und keine ungeprüfte OpenCode-1.x-Konfiguration. Das Fehlen eines aus einer älteren Anleitung kopierten Flags rechtfertigt nicht den Abbruch, solange die installierte Version einen geeigneten unterstützten Weg hat.

### 2.2 Modell und Provider

Die Spezifikationsrecherche vom 4. November 2026 fand in der direkten DeepSeek-Dokumentation die Zuordnung `deepseek-flash` → **DeepSeek-V4.1-Flash-1017**. Für den tatsächlichen Lauf ist die Zuordnung erneut zu bestätigen.

Ein bestehender Projektstarter verwendet `deepseek/deepseek-flash`. Die Gross-/Kleinschreibung und Provider-ID müssen mit der realen lokalen Modellausgabe übereinstimmen. Nicht aus einem Anzeigenamen einen erfundenen API-Modellnamen ableiten.

Nachweisen und ohne Secrets protokollieren:

- ausgewählter lokaler OpenCode-Provider;
- direkter DeepSeek-Endpoint, kein transparenter Wechsel zu einem anderen Anbieter;
- angefragte tatsächliche Modellkennung;
- belastbare Zuordnung dieser Kennung zur verlangten 1017-Variante;
- tatsächliche Sitzungs-/Runmetadaten, soweit die CLI sie liefert.

Wenn die API nur einen Alias zurückliefert, wird das offen angegeben. Eine aktuelle offizielle Aliaszuordnung kann die gewählte Modellvariante belegen; eine fehlende technische Revisionskennung wird nicht erfunden. Ist der Alias inzwischen anders belegt und keine passende 1017-Auswahl verfügbar, ist das ein konkreter `BLOCKED`-Grund.

Vorhandene OpenCode-Authentifizierung wiederverwenden. Keinen zusätzlichen `DEEPSEEK_API_KEY` verlangen, wenn die vorhandene Anmeldung genügt. Keine Credentials exportieren, keine sensitiven Auth-/Konfigurationsdateien in Reviewanhänge aufnehmen und keine vollständigen Umgebungen protokollieren. Keine automatische Ausweichwahl eines anderen Modells.

### 2.3 Beispielaufruf, lokal zu bestätigen

Der folgende Aufruf ist ein Muster, kein Anspruch auf identische Flags jeder 2.x-Version:

```sh
opencode run \
  --standalone \
  --auto \
  --agent build \
  --model <LOKAL_VERIFIZIERTE_PROVIDER_MODELLKENNUNG> \
  --format json \
  --file <ABSOLUTER_PFAD_REQUEST_JSON> \
  --file <ABSOLUTER_PFAD_REVIEWAUFTRAG> \
  'Prüfe Auftrag A unabhängig am exakten Kandidaten. Lies alle Anforderungen, den kumulativen Diff und Originalbelege. Führe eigene gezielte Gegenproben aus. Ändere keine Produktdateien. Schreibe review.md und verdict.json und beende den Lauf mit einem begründeten Urteil.'
```

Nur bestätigte Flags einsetzen; Argumente als Argumentliste an `ProcessBuilder`/`spawn` übergeben, nicht in eine ungequotete Shellzeichenkette bauen. `--auto` ersetzt keine expliziten Rechteverbote. Ein eigener frischer Prozess/Sitzungskontext ist erforderlich; keine Fortsetzung einer Implementierungs- oder alten Reviewsitzung.

Bestehende geeignete Projektstarter wiederverwenden oder schmal anpassen. Es wird kein allgemeines neues Agentenorchestrierungsframework entwickelt. Node oder Java nutzen; keine neue Python-Voraussetzung.

## 3. Kandidat, Isolation und Belegpaket

### 3.1 Exakter Kandidat

Codex erzeugt nach A5 einen sauberen detached Review-Worktree oder eine gleichwertige isolierte lokale Kopie auf **CANDIDATE_SHA**. Keine Übernahme uncommitteter Implementierungsänderungen.

Festhalten:

- `BASE_SHA` und Ausgangstree;
- `CANDIDATE_SHA` und `CANDIDATE_TREE_SHA`;
- vollständiger kumulativer Diff `BASE_SHA..CANDIDATE_SHA`, einschliesslich Löschungen/Umbenennungen;
- Versionen/Pins des Projekt-JDKs und der Jenesis-Engine;
- Input-/Quell-/Artefaktinventare und ursprüngliche Rohlogs;
- notwendige Aufrufkonfiguration ohne Zugangsdaten.

Ein eigener Reviewordner ausserhalb des versionierten Produktbaums erhält beispielsweise:

```text
<externes-belegroot>/reviews/<candidate-sha>/round-01/
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

Keine vertrauten absoluten Benutzerpfade voraussetzen. Die tatsächlichen Pfade werden von Codex ermittelt. Reviewbelege müssen Clean-Aufrufe überleben.

### 3.2 Schreibgrenzen

Der Reviewer darf versionierte Produktdateien ausschliesslich lesen. Ausgaben, Logs und eigene Gegenproben dürfen in begrenzten Scratch-/Buildverzeichnissen entstehen.

Er darf nicht Produktcode reparieren, Tests/Spezifikationen verändern, committen, pushen, releasen, Credentials lesen oder weitere Agenten starten. Diese Schreibgrenze gilt ebenso für das bereitgestellte Extension-Checkout und dessen Original-Release-Locks; nur separate Testkopien sind für Gegenproben beschreibbar. Notwendige Dependencyauflösung mit bestehenden Pins ist zulässig, aber kein Toolchainupdate oder unkontrolliertes Installationsskript.

Eine reine Sperre des Edit-Tools schützt nicht vor Shellschreibzugriffen. Nutze geeignete vorhandene Sandbox-/OS-/OpenCode-Berechtigungsmechanismen und explizite Pfadgrenzen. Globale Konfiguration nicht unnötig verändern. Vorher sicherstellen, dass der autonome Lauf nicht mitten in einer normalen erlaubten Gegenprobe auf eine Benutzerfreigabe wartet.

Mutationstests brauchen eine **separate beschreibbare Scratchkopie**, nicht Änderungen am geprüften Kandidaten. Codex kann diese vorher bereitstellen. Für einen Clean-Test die zu löschenden Testausgaben vorab abgrenzen; keine fremden Verzeichnisse löschen.

Vor und nach dem Lauf SHA/Tree, Gitstatus und relevante Quellhashes prüfen. Unerklärte Änderung versionierter Kandidaten- oder Originalbelegdateien macht den Review ungültig. Ein Sandbox- oder Berechtigungsfehler ist kein fachlicher PASS.

### 3.3 Nachweise für den Reviewer

Der Reviewer erhält alle acht Auftragsdateien, den aktuellen Status, Capability-/Baseline-/Paritätsbericht, Editor-Kompatibilitätsbericht, Testledger und die Originalnachweise. Der tatsächlich verwendete Extension-Quellstand und seine Hashes werden read-only mitbereitgestellt. Die Implementiererzusammenfassung ist ein Wegweiser, nicht die Wahrheit, die nur bestätigt werden muss.

`evidence-index.json` referenziert tatsächliche Dateien mit SHA-256, Herkunft, Testumfang und Bedeutung. Beispielsweise unterscheiden:

- echte neue Testausführung;
- nachvollziehbare Wiederverwendung eines älteren Laufs;
- native Reports und daraus abgeleitete Formatprojektion;
- Fixture-/Validatorprobe gegenüber tatsächlicher Produktintegration;
- Taskgraphnachweis gegenüber ausgeführtem Task;
- notwendige gegenüber ausserhalb des Auftrags liegenden Profile.

Keine alten Rohbelege umetikettieren. Erhaltene reale Fehlerläufe bleiben vorhanden; ihre späteren Korrekturen werden referenziert.

## 4. Maschinenlesbare Reviewanfrage

Folgendes JSON beschreibt die Form; Platzhalter sind durch tatsächliche Werte zu ersetzen. Es ist selbst kein ausgeführter Reviewauftrag und kein Nachweis:

```json
{
  "schemaVersion": "1.0",
  "assignment": "jenesis-jvm-a",
  "baseSha": "<BASE_SHA>",
  "candidateSha": "<CANDIDATE_SHA>",
  "candidateTreeSha": "<CANDIDATE_TREE_SHA>",
  "workingDirectory": "<ABSOLUTER_REVIEW_WORKTREE>",
  "reviewDirectory": "<ABSOLUTER_OUTPUTORDNER>",
  "scratchDirectory": "<ABSOLUTER_SCRATCHORDNER>",
  "requirementsDirectory": "docs/implementation/jenesis-jvm-a",
  "diffFile": "<ABSOLUTER_KUMULATIVER_DIFF>",
  "evidenceIndex": "<ABSOLUTER_EVIDENCE_INDEX>",
  "requiredCriteria": [
    "JA-01", "JA-02", "JA-03", "JA-04", "JA-05", "JA-06", "JA-07", "JA-08",
    "JA-09", "JA-10", "JA-11", "JA-12", "JA-13", "JA-14", "JA-15", "JA-16",
    "JA-17", "JA-18", "JA-19", "JA-20", "JA-21", "JA-22", "JA-23", "JA-24",
    "JA-25", "JA-26", "JA-27", "JA-28", "JA-29", "JA-30", "JA-31", "JA-32",
    "JA-33", "JA-34", "JA-35", "JA-36"
  ],
  "modelRequirement": {
    "provider": "direct DeepSeek API",
    "variant": "DeepSeek Flash 1017",
    "requestedModelId": "<VERIFIZIERTE_LOKALE_MODELLKENNUNG>",
    "mappingEvidence": "<NACHWEIS_DER_TATSAECHLICHEN_ZUORDNUNG>"
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

Folgerunden referenzieren die Originalanfragen, Urteile, Findings und gezielten Korrektur-/Testbelege unter `previousRounds`. Die gesamte Anforderungsmenge und der kumulative Diff bleiben enthalten.

## 5. Inhaltlicher Auftrag an den unabhängigen Reviewer

Diesen Abschnitt zusammen mit den genannten Dateien an die frische Sitzung übergeben:

> Du bist der unabhängige Reviewer für Auftrag A im Repository interlis-java. Prüfe den exakten Kandidatencommit gegen JA-01 bis JA-36. Codex hat implementiert; du musst Zielerreichung, Code und Qualität der Nachweise selbst beurteilen. Eine grüne Zusammenfassung, eine hohe Testanzahl oder ein Prozess-Exitcode genügt nicht.
>
> Lies SPECIFICATION.md, BUILD_PARITY_MATRIX.md, EDITOR_COMPATIBILITY.md, EXECUTION_PLAN.md und TEST_AND_EVIDENCE_POLICY.md vollständig. Prüfe den kumulativen Diff und die betroffenen unveränderten Verbraucher. Die Einhaltung des Auftrags ist wichtiger als das Nachbauen der Lösung, die du selbst bevorzugt hättest.
>
> Kontrolliere insbesondere:
>
> **Scope:** Genau zehn JVM-Module, POM-/Classpath-Parität, unveränderte Enginepins, keine JPMS-/Wasm-/Frontend-/jpackage-/APE-Migration. Der Editor-Verbrauchercheck ist eine eng begrenzte ausdrückliche Ausnahme zur ansonsten unveränderten Extensionpipeline; der bestehende jlink-Pfad darf nicht entfernt werden. Gradle bleibt lauffähige Referenz. Keine heimliche Gradle-/Maven-Delegation und keine Verwendung alter eigener Gradle-/SNAPSHOT-Artefakte.
>
> **Build:** Effektive Dependencies/Scopes, Generatorisolierung, ANTLR-Inputs und inkrementelle Sauberkeit, Javaoptionen, Ressourcen/Sources/Lizenzen, Launcher-/Archivform und interne Artefaktgrenzen. Prüfe, dass eine Enginefunktion im tatsächlich gepinnten Stand existiert und benutzt wird.
>
> **Tests:** Alle ursprünglichen normalen JVM-Testidentitäten einschliesslich Parameterfällen sind wirklich erhalten; neue Adaptertests ersetzen keinen verlorenen Fall. Getaggte Contractmethoden und normale Negativtests derselben Klasse bleiben richtig ausgewählt. Package-private Tests wurden nicht für den Builder umgeschrieben. Externe Fixture-/Hook-/Konfigurationsänderungen invalidieren tatsächliche betroffene Testschritte, nicht nur einen separaten Berichtsgenerator.
>
> **Contracts und Reports:** Frische selektierte Produzenten vor Consumern, korrekte Run-/Tree-/Input-/Artefaktbindung und echte Native-Reports. Formatprojektionen müssen auf ausgeführte Fälle zurückführbar sein. Keine Herkunftsfälschung, kein Ignorieren verlorener Parameter oder Skips, keine bloss aus der Sollauswahl erzeugten Erfolgsreports.
>
> **Integration und Reproduzierbarkeit:** SDK-/CLI-/LSP-Prozesse verwenden wirklich Jenesisprodukte. Die bestehende Modulpfadstartersemantik bleibt erhalten. Vergleich unabhängiger JVM-Erzeugungen muss Produktdifferenzen ablehnen; ein Diagnosemodus mit Exit 0 reicht nicht. Buildbezogene variable Berichte werden nicht mit Produktbytes verwechselt.
>
> **Koexistenz:** Root-Clean erhält versionierte Engine-/Adapterquellen. Gradle-Defaults, Taskgraphen und Pfadregeln bleiben funktionsfähig. Explizites Jenesislayout darf nicht auf Gradleprodukte zurückfallen. Historische Nachweise bleiben unverändert.
>
> **Extension und Folgegrenze:** Bestehendes Gradle-editorBundle, stage:core/F5 und LSP-Classpathstart bleiben funktionsfähig. Das Schattenbundle enthält ausschliesslich die vollständige Jenesis-LSP-JAR-Closure plus korrekt zugeordnete unveränderte Renderer-/Protokollressourcen. Echte Extension-Launch-/Hashprüfung und kleiner Extension-Host-Smoke sind belegt; ein normaler LSP-Smoke allein reicht nicht. Keine ursprünglichen Release-Locks oder Benutzerprofile verändert und kein früheres akzeptiertes Core-/Extension-Paar umetikettiert. Bestehende JRE-/jlink-Paketierung unverändert, kein zweiter Runtimebuilder. Compiler-APE ist separat und JPMS dafür kein in A eingeführtes Pflichtgate.
>
> **Testökonomie:** Verifiziere die Originalbelege und ihre Wiederverwendungsbegründung. Fordere keine vollständige Suite allein wegen eines neuen Commits, einer Dokumentkorrektur oder einer Reviewrunde. Fordere breitere Tests nur bei konkreten geänderten Inputs oder unbeantworteten Risiken. Ein ausserhalb von Auftrag A liegender Wasm-/P3-/APE-/JPMS-Lauf ist kein pauschaler Abschlussblocker. Der ausdrücklich verlangte kleine Extension-Host-Smoke darf aber nicht mit dieser Begründung entfallen.
>
> Führe eigene gezielte Gegenproben aus. Wähle die kleinste ausreichende Kombination aus engem echtem Engine-/Testlauf, Manipulationsproben an Reportfixtures, Cache-/Scope-/Clean-Grenzprüfungen und einem passenden realen Prozessconsumer. Unveränderte teure Korpus-, Browser-, Benchmark- oder volle JVM-Läufe nicht blind wiederholen. Begründe die Auswahl; blosse Sichtprüfung und Übernahme des Codex-PASS genügen nicht.
>
> Lies die ursprünglichen Logs, nicht nur nachträglich zusammengefasste Zahlen. Prüfe bekannte Fehlerpfade mit zunächst gültigen Kontrollen, damit eine Negativprobe nicht aus einem unbeteiligten Grund erfolgreich erscheint.
>
> Repariere keine Produktdateien. Lege eigene kleine Proben nur im erlaubten Scratchbereich an. Schreibe review.md und verdict.json. Benenne jedes Finding mit Kriterium, Datei/Fundstelle, konkreter Auswirkung und reproduzierbarem Nachweis. Trenne echte Pflichtverletzungen von unverbindlichen Stilvorschlägen.
>
> PASS nur bei erfüllten fachlichen Kriterien und ausreichenden echten Belegen; FAIL bei konkretem zu behebendem Defekt; BLOCKED bei einer notwendigen nicht ausführbaren oder nicht prüfbaren Voraussetzung. Keine weiteren Agenten starten und nicht auf routinemässige Benutzerentscheidungen warten.

## 6. Reviewer-Gegenproben: Mindestcharakter, keine Volltestpflicht

Mindestens eine echte eigene Ausführung der betroffenen Build-/Testspur und aussagekräftige eigene Negativprüfungen sind erforderlich. Die konkrete Auswahl folgt den Risiken des Diffs. Geeignete Kombinationen:

- Kleiner Jenesislauf eines Moduls mit unveränderten White-Box-Tests; tatsächliche Optionen, JAR-/Inputherkunft und selektierte Fälle prüfen.
- Eine Parameterinvocation oder Fehlerzeile im Reportfixture entfernen/verändern; der Adapter/Validator muss scheitern. Das Fixture muss vorher gültig sein.
- Producerbericht eines anderen Runs oder veränderte Native-Reportbytes unterlegen; keine Freigabe.
- Nur einen relevanten externen Fixture-/Adapterinput in Scratch ändern; betroffener tatsächlicher Testschritt darf nicht als unverändert gecacht gelten.
- Clean-/Discovery-/Fallbackprobe mit klar begrenzten Scratchdateien; kein Lesen fremder Buildprodukte und kein Löschen versionierter Buildquellen.
- Kleinen realen SDK-/CLI-/LSP-Consumer gegen das geprüfte Jenesisartefakt ausführen, wenn der Diff dessen Verarbeitung oder Herkunft betrifft.
- An einer getrennten Schattenkopie den tatsächlichen Extension-Inventarvalidator gegen fehlende/veränderte/zusätzliche JARs prüfen; vorher gültige Kontrolle. Den unabhängigen echten Extension-Host-Bericht auf geladene Jenesisartefakte, Runtime und Umfang prüfen.

Der Reviewer darf belastbar nachgewiesene grosse Integrationsläufe des Primäragenten nach Prüfung wiederverwenden. Er muss nicht jede der 24 Kategorien aus TEST_AND_EVIDENCE_POLICY nochmals ausführen. Insbesondere genügt bei unveränderten Editorproduktbytes die Prüfung des echten ursprünglichen Extension-Host-Belegs zusammen mit eigener schmaler Integritäts-/Launchgegenprobe; kein erneuter kompletter GUI-Lauf aus Formalismus. Er muss allerdings alle relevanten Schutzwirkungen im Gesamturteil beurteilen, statt ausschliesslich die letzte Korrektur anzusehen.

## 7. Urteil und Plausibilitätsprüfung

`review.md` enthält Umfang, Codebefunde, eigene Proben, geprüfte Belege, offene Fragen und Gesamturteil.

`verdict.json` enthält mindestens folgende Struktur. Das Beispiel ist ausdrücklich **kein fertiges PASS**:

```json
{
  "schemaVersion": "1.0",
  "assignment": "jenesis-jvm-a",
  "verdict": "<PASS|FAIL|BLOCKED>",
  "baseSha": "<BASE_SHA>",
  "candidateSha": "<CANDIDATE_SHA>",
  "candidateTreeSha": "<TREE_SHA>",
  "session": {
    "opencodeVersion": "<TATSAECHLICHE_VERSION>",
    "sessionId": "<TATSAECHLICHE_SITZUNG_ODER_EXPLIZIT_NICHT_GELIEFERT>",
    "providerId": "<TATSAECHLICHER_PROVIDER>",
    "requestedModelId": "<TATSAECHLICHE_MODELLKENNUNG>",
    "modelMappingEvidence": "<QUELLE_UND_ZEITPUNKT>"
  },
  "criteria": [
    {
      "id": "JA-01",
      "status": "<PASS|FAIL|BLOCKED>",
      "reason": "<BEGRUENDUNG>",
      "evidence": ["<DATEI_ODER_ORIGINALBELEG>"]
    }
  ],
  "ownProbes": [
    {
      "purpose": "<KONKRETE_PRUEFFRAGE>",
      "command": ["<PROGRAMM>", "<ARGUMENT>"],
      "workingDirectory": "<ABSOLUTER_PFAD>",
      "exitCode": 0,
      "log": "<PFAD_ZUM_ECHTEN_LOG>",
      "observation": "<TATSAECHLICHE_BEOBACHTUNG>"
    }
  ],
  "checkedEvidence": [
    {
      "path": "<ORIGINALBELEG>",
      "sha256": "<TATSAECHLICHER_SHA256>",
      "assessment": "<HERKUNFT_UMFANG_UND_AUSSAGEKRAFT>"
    }
  ],
  "findings": [],
  "blockedChecks": [],
  "trackedSourcesUnchanged": true
}
```

Die tatsächliche `criteria`-Liste muss **alle 36 Kriterien genau einmal** enthalten. Kriterien zur Reviewkorrekturschleife sind ohne Findings mit ausdrücklicher Begründung „kein Korrekturfall angefallen“ erfüllt, nicht mit fiktiven Testläufen. JA-32 prüft zusätzlich der Koordinator nach dem Lauf: gültiges Urteil, echte Gegenproben, unveränderter Kandidat, keine nachträgliche Veränderung. Diese Nachkontrolle ist keine Selbstfreigabe des Produktcodes durch Codex.

Codex wartet auf den tatsächlichen regulären Prozessabschluss und prüft:

- Exitcode/Signal und keine abgebrochene oder fehlgeschlagene API-Sitzung;
- Provider-/Modellbezug und passende Installation;
- SHA/Tree und unveränderte versionierte Quellen/Belege;
- vollständige Kriteriumsabdeckung, eigene Proben und echte Logpfade;
- konsistentes Urteil ohne ignorierte notwendige offene Prüfungen;
- keine selbst von Codex erzeugte oder veränderte Reviewerbewertung.

Ein Exitcode 0 bedeutet nur, dass der Prozess erfolgreich endete. Er ist weder unabhängig von `verdict.json` noch als Ersatz für dessen Inhalt ein PASS.

## 8. Findings, schmale Korrekturen und erneuter Review

Bei FAIL bearbeitet **Codex** die Findings. Für jedes berechtigte Finding entsteht ein Impacteintrag gemäss TEST_AND_EVIDENCE_POLICY:

- Fehlervertrag und relevante geänderte Inputs;
- gezielte Reproduktion;
- Korrektur;
- notwendige neue Tests;
- bewusst wiederverwendete alte Belege mit ursprünglichen Hashes/SHAs;
- Begründung, weshalb grössere Läufe nicht erforderlich sind oder doch werden.

Nach erfolgreicher Korrekturprüfung neuen Commit erzeugen und eine frische Reviewrunde auf diesem Commit starten. Die Runde sieht den gesamten Auftrag und kumulativen Diff sowie frühere Findings. Das ist ein **erneuter Gesamt-Review, keine Verpflichtung zu erneuten Volltests**.

Unbegründete Findings nicht still ignorieren: konkrete Gegenbelege liefern und den Reviewer die Auflösung bestätigen lassen. Reine Stilvorschläge sind von verletzten Abnahmekriterien zu trennen; kein endloser Refactoringauftrag aus Geschmacksfragen.

Bei BLOCKED behebbare lokale Aufruf-/Pfad-/Berechtigungsfehler selbst lösen. Keine Sicherheitsgrenze umgehen, kein Modell wechseln, keine fehlenden Berichte erfinden und keine identischen Fehleraufrufe endlos wiederholen. Ein nicht selbst behebbarer konkreter Blocker beendet den Auftrag mit ehrlicher Zustandsmeldung, nicht mit Erfolg.

Finaler Kandidat erst bei gültigem unabhängigem PASS abgenommen. Reviewartefakte bleiben extern; kein neuer ungeprüfter Commit allein zum Einchecken des finalen Urteils. Nach PASS nicht still Code, Pins, POMs oder Dokumentation ändern.
