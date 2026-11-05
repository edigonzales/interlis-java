# Autonomer unabhängiger Gesamt-Review — Auftrag B

## 1. Auftrag und Zeitpunkt

Primäragent Codex. Reviewer: **frische OpenCode-2.x-Sitzung mit DeepSeek Flash 1017 über die direkte DeepSeek-API**. Kein Codex-Subagent, kein stiller anderer Provider, keine Fortsetzung einer früheren Implementierungs- oder Reviewsitzung.

Fachlicher Review **erst nach B0–B7 und den geprüften lokalen Kandidatencommits**. Technischer Modell-/CLI-/Rechtepreflight vorher erlaubt. Im Review zählen Zielerreichung, Codequalität, Erhalt der Verträge und echte Nachweise, nicht nur ein erfolgreicher Testbefehl.

Gegenstand ist das **Kandidatenpaar aus Core und Extension**, einschliesslich des vollständigen kumulativen Diffs seit den beiden BASE_SHAs. B-I und B-II gehören beide dazu. Pausierte APE-Arbeit ist ausserhalb des Scopes und darf nicht als heimliche Voraussetzung gestartet werden.

## 2. Technischer Preflight

Vorhandenen funktionierenden Reviewstarter bevorzugt eng anpassen. Installierte OpenCode-Version, `run --help`, Provider-/Modellliste und Rechtepolitik prüfen. Keine ungeprüfte OpenCode-1.x-Konfiguration verwenden, kein unbelegtes `models --refresh` erfinden.

Mögliche lokale Programmorte: PATH-Auflösung und die bestehende Desktopinstallation, etwa `/Applications/OpenCode.app/Contents/Resources/opencode-cli`. Pfade sind Hinweise, keine garantierte Umgebung. Keine automatische globale Installation oder Aktualisierung.

Die am 5. November 2026 gelesene offizielle DeepSeek-Dokumentation ordnet den API-Alias `deepseek-flash` der Variante DeepSeek-V4.1-Flash-1017 zu. Vor dem tatsächlichen Review erneut prüfen. Lokale OpenCodekennung, etwa `deepseek/deepseek-flash`, muss aus der realen Providerkonfiguration stammen. Ein Name allein beweist keinen direkten DeepSeek-Endpunkt oder eine unveränderte Version.

Launchkonfiguration, tatsächliche angefragte Modellkennung, sichere Provideridentität und verfügbare Antwort-/Sitzungsmetadaten aufzeichnen. Gibt die API nur einen Alias zurück, diese Grenze mit aktueller offizieller Zuordnung offen dokumentieren. Keine technische Revisionskennung erfinden. Ist 1017 nicht verfügbar oder eindeutig zuordenbar, Status BLOCKED statt Ersatzmodell.

Bestehende OpenCode-Anmeldung wiederverwenden; kein zusätzlicher API-Key als ENV erzwingen, wenn der Zugang bereits funktioniert. Keine Authdateien oder Tokens in Prompt/Log/argv. Keine globale Defaultprovideränderung. Reviewer-Worktree benötigt den tatsächlich funktionierenden Providerkontext; nicht automatisch von einer anderen Arbeitskopie ausgehen.

## 3. Kandidatenpaar und Isolation

Nach B7 Core geprüft committen; daraus Bundle erstellen/prüfen, danach passende Extensionänderungen/Locks committen. Extern festhalten:

```json
{
  "schemaVersion": 1,
  "assignment": "jenesis-migration-b",
  "repositories": {
    "core": {
      "baseSha": "<CORE_BASE_SHA>",
      "candidateSha": "<CORE_CANDIDATE_SHA>",
      "treeSha": "<CORE_TREE_SHA>",
      "worktree": "<ABSOLUTE_CORE_REVIEW_WORKTREE>",
      "diff": "<ABSOLUTE_CORE_CUMULATIVE_DIFF>"
    },
    "extension": {
      "baseSha": "<EXTENSION_BASE_SHA>",
      "candidateSha": "<EXTENSION_CANDIDATE_SHA>",
      "treeSha": "<EXTENSION_TREE_SHA>",
      "worktree": "<ABSOLUTE_EXTENSION_REVIEW_WORKTREE>",
      "diff": "<ABSOLUTE_EXTENSION_CUMULATIVE_DIFF>"
    }
  },
  "bundleManifestSha256": "<ACTUAL_SHA256>",
  "evidenceIndex": "<ABSOLUTE_EVIDENCE_INDEX>",
  "requirementsDirectory": "docs/implementation/jenesis-migration-b",
  "modelRequirement": {
    "provider": "direct DeepSeek API",
    "variant": "DeepSeek Flash 1017",
    "requestedModelId": "<VERIFIED_LOCAL_MODEL_ID>",
    "mappingEvidence": "<ACTUAL_MAPPING_REFERENCE>"
  },
  "previousRounds": [],
  "rules": {
    "trackedSourcesReadOnly": true,
    "repairProductCode": false,
    "spawnFurtherAgents": false,
    "pushOrPublish": false,
    "repeatBroadTestsWithoutReason": false
  }
}
```

Das Beispiel ist kein tatsächlicher Beleg. Alle Platzhalter vor dem Start durch reale Werte ersetzen, SHAs/Hashes/Dateien vorvalidieren. Keine fehlerhafte Kombination aus aktuellem Core und alter Extension akzeptieren.

Beide Kandidaten in getrennten sauberen detached Worktrees bzw. gleichwertig isolierten Kopien. Produktquellen und Originalbelege nur lesbar; Build-/Review-/Scratchoutputs explizit beschreibbar. Für Mutationsproben separate Kopien, nicht das Kandidatenoriginal bearbeiten.

Eine Edit-Tool-Sperre allein schützt nicht vor Shellwrites. Geeignete tatsächliche Sandbox-/Dateirechte/Command- und Pfadgrenzen einsetzen und vor/nachher Status-/Quellhashkontrolle durchführen. Unerklärte Kandidatenmutation macht den Review ungültig. Normale erlaubte Proben dürfen nicht auf eine interaktive Benutzerbestätigung warten; dazu Rechte im Voraus eng passend konfigurieren, keine Sicherheitsverbote pauschal ausschalten.

## 4. Belegpaket und Aufruf

Externe Clean-sichere Ablage:

```text
<evidence-root>/reviews/<candidate-pair-id>/round-01/
  request.json
  REVIEW_REQUEST.md
  core.diff
  extension.diff
  evidence-index.json
  launch.json
  opencode.jsonl
  stderr.log
  process-result.json
  output/review.md
  output/verdict.json
  scratch/
```

Belegindex enthält Originallogpfade/Hashes, verwendete Produkt-/Testinputs, tatsächlichen Umfang und Status. Statische Sourceprüfung, Mock-/Guardtest, echte JARausführung, Codegen, echter Browser/Extension-Host und CI-Lauf ausdrücklich unterscheiden. Eine Markdownerfolgsaussage ohne zugängliche Rohbelege ist kein gleichwertiger Ersatz.

Aufrufmuster, **an der installierten Hilfe zu bestätigen**:

```sh
opencode run \
  --standalone \
  --auto \
  --model <VERIFIED_PROVIDER_MODEL_ID> \
  --format json \
  --file <ABSOLUTE_REQUEST_JSON> \
  --file <ABSOLUTE_REVIEW_REQUEST> \
  'Prüfe Auftrag B unabhängig am exakten Core-/Extension-Kandidatenpaar. Lies den vollständigen Auftrag, beide kumulativen Diffs und Originalbelege. Führe eigene gezielte Gegenproben aus. Ändere keine Produktdateien. Schreibe review.md und verdict.json und beende den Lauf mit einem begründeten Urteil.'
```

Nur tatsächlich unterstützte Flags verwenden. Argumente als Liste, nicht als interpolierte ungequotete Shellzeichenkette. `--auto` ist keine Erlaubnis für verbotene Aktionen. Kein `--continue`, keine alte `--session`. Ein vorhandener geeigneter lokaler Reviewagent ist zulässig, sein Modell- und Rechtebezug muss stimmen.

Auf regulären echten Prozessabschluss warten. Exitcode, Signal, Timeout und JSONL-Vollständigkeit speichern. Erfolgreicher Start, Textstück „PASS“ vor Abbruch oder leerer Output sind kein gültiges Urteil. Kein versprochenes Hintergrundresultat.

## 5. Direkt an den Reviewer zu übergebender Auftrag

> Du bist der unabhängige Reviewer für Auftrag B, nicht sein Implementierer. Prüfe das genaue Kandidatenpaar in request.json gegen JB-01 bis JB-40. Lies alle Auftragsdokumente, den vollständigen kumulativen Diff beider Repositories, relevante unveränderte Verbraucher und die Originalbelege selbst. Die Zusammenfassung des Primäragenten ist ein Wegweiser, kein Beweis.
>
> Beurteile B-I und B-II getrennt und den Gesamtauftrag. Kontrolliere, dass Jenesis der einzige Produzent gemeinsamer JVM-Produktquellen ist. Gradle darf die eigenen zwei Backendadapter bauen, nicht dieselben Compiler-/Bridgequellen erneut. Keine mavenLocal-, SourceSet-, includeBuild- oder Altoutput-Hintertür. Keine reentranten Buildlock-/Gradle→Jenesis-Zyklen.
>
> Prüfe die Normalisierung der Engineergebnisse und die unveränderte Enginebindung. Keine erzeugte POM mit falschen GAVs/Scopes, keine Testversion, die die Runtime heimlich ersetzt. Snapshot-/Manifest-/Hashprüfung muss komplette tatsächliche Artefakte und den unabhängigen Producerbezug erfassen; nicht nur ein frei neu gehashtes Inventar. Prüfe warmen SNAPSHOT-Cache, sichere Pfade und fehlende/zusätzliche Dateien.
>
> Kontrolliere echte JPMS-Kompilierung aller eigenen JVM-Projekte, Modulnamen, requires/transitive/static, services, exports und opens. Kein dekorativer Descriptor auf einem faktisch nichtmodularen Produkt. Automatische Fremdmodule sind zulässig, wenn explizit inventarisiert; automatische eigene Module im Endzustand nicht.
>
> Prüfe die White-Box-Strategie besonders: test-only patch-module ist zulässig, aber kein eigener Produktneubau, keine produktive Klassenschattenkopie, kein verlorener Testfall und kein öffentlicher Compilerinternal. JUnit muss real entdecken und ausführen, einschliesslich Parameter-/Fehler-/Empty-Kontrollen. Die echte unmodifizierte Produktgrenze muss zusätzliche benannte Consumer-/Nichtzugriffsproben bestehen. Produktstarts und Lieferungen dürfen keine Testpatches oder breiten Zugriffsflags enthalten.
>
> Prüfe den tatsächlichen Extension-Build- und Startpfad, nicht nur Corefixtures. Corebundle aus Jenesis, reiner Import ohne Builder, passende Version/Manifestbindung, echte gebündelte JVM und modularer LSP mit unverändertem stdout-/LSP-Vertrag. Keine stillen Classpathfallbacks. CLI-Unicode-/Argument-/Exitverträge erhalten. Jlink/jpackage-Eignungsentscheidungen dürfen weder fehlende Services kaschieren noch eine nicht ausgeführte Plattformfreigabe behaupten.
>
> Prüfe Wasmconsumer an den echten neuen JARbytes, nicht nur deren compileClasspath-Konfiguration. Descriptoren nicht still strippen und keine zweite nichtmodulare JAR bauen. Prüfe Report-/Contract-/Conformance-/Releasepfade und fail-closed Gegenproben. Alte G2-/A-/APE-Belege bleiben Originale, keine Retrospektiv-PASS-Umschreibung. Vollständige P3-Veröffentlichung ist nicht dieser Auftrag; fällige lokale Backend-/Consumerabnahme ist es hingegen.
>
> Die APE-/Cosmo-Arbeit ist pausiert: kein Nativebuild, Patch, Vendorupdate oder Branchmerge dafür. Prüfe aktive Altlastenbereinigung ohne fremde Quellen-/Beleglöschung. Prüfe Clean und Default-JVM-Unabhängigkeit von Gradle/Node/Browser.
>
> Führe eigene gezielte Gegenproben durch: mindestens eine Modulgrenzenverletzung, einen manipulierten/verwechselten Snapshot-/Inventarfall und einen realen schmalen CLI-/LSP-/Consumerlauf auf verfügbarer passender Umgebung. Begründe jede zusätzliche grosse Suite. Bereits belastbare unveränderte breite Codex-Belege prüfen statt automatisch wiederholen. Fehlende fällige Nachweise ausdrücklich nennen.
>
> Findings müssen Datei/Fundstelle, betroffenen Vertrag, konkrete Auswirkung und eigenen Nachweis bzw. reproduzierbaren Gegenfall enthalten; nenne den kleinsten sinnvollen Retest. Bewerte die Auftragserfüllung, nicht nur deine bevorzugte Architektur. Ändere keine versionierten Kandidatendateien, repariere nicht selbst, committe/pushe nichts, starte keine weiteren Agenten. Schreibe ein begründetes review.md und verdict.json und beende den Prozess regulär.

## 6. Urteilsschema

`verdict.json` enthält mindestens:

```json
{
  "schemaVersion": 1,
  "assignment": "jenesis-migration-b",
  "verdict": "PASS|FAIL|BLOCKED",
  "candidatePair": {
    "coreSha": "<ACTUAL_SHA>", "coreTree": "<ACTUAL_TREE>",
    "extensionSha": "<ACTUAL_SHA>", "extensionTree": "<ACTUAL_TREE>"
  },
  "scope": {
    "buildOwnership": "PASS|FAIL|BLOCKED",
    "jpmsMigration": "PASS|FAIL|BLOCKED",
    "consumerIntegration": "PASS|FAIL|BLOCKED",
    "fullP3Release": "NOT_REQUESTED|NOT_PROVEN"
  },
  "reviewer": {
    "toolVersion": "<ACTUAL_OPENCODE_2_VERSION>",
    "sessionId": "<ACTUAL_SESSION_ID>",
    "provider": "<ACTUAL_PROVIDER>",
    "requestedModel": "<ACTUAL_MODEL>",
    "modelMappingEvidence": "<REFERENCE>"
  },
  "criteria": [{"id": "JB-01", "status": "PASS|FAIL|BLOCKED", "evidence": ["<ACTUAL_REF>"], "reason": "<REASON>"}],
  "ownChecks": [{"command": ["<ACTUAL_ARGV>"], "exitCode": 0, "receipt": "<ACTUAL_FILE>"}],
  "reviewedOriginalEvidence": ["<ACTUAL_REF>"],
  "findings": [],
  "missingRequiredEvidence": [],
  "platformLimitations": []
}
```

Vor realem Gebrauch genau einen Wert je Enum verwenden und **alle 40 Kriterien** ausfüllen. Ergebnis stammt vom echten Reviewer, nicht vom Primäragenten. Codex validiert Schema, eindeutige Kriterienmenge, SHAs/Trees, Modellbezug, eigene Gegenproben und Prozessabschluss. Mindestens ein gelesenes eigenes Testlog ist kein Ersatz für die ganze Prüfung.

Review-PASS gilt für die vertraglich definierte Implementierungsabnahme, nicht für alle historischen P3-Plattformversprechen. Fehlt aber ein explizites B-Mindestgate, ist PASS unzulässig. Technische Unzugänglichkeit ist BLOCKED, eine belegte Verletzung FAIL. Ein tolerierbarer ausserhalb des Auftrags liegender Verbesserungsvorschlag ist als solcher zu kennzeichnen, nicht als willkürliche Freigabescheibe.

## 7. Autonome Korrektur-/Review-Schleife

Bei FAIL prüft Codex Findings, korrigiert berechtigte Befunde, dokumentiert die Testimpactentscheidung und führt nur notwendige Tests aus. Neue Kandidaten lokal committen und neue Runde mit frischer OpenCode-Sitzung starten. Request enthält weiterhin den ganzen Auftrag und die kumulativen Diffs, zusätzlich frühere Findings und deren Behandlung.

Ein unbegründetes Finding wird mit technischen Gegenbelegen dem Reviewer erneut vorgelegt, nicht von Codex eigenmächtig als erledigt markiert. Ein Fehler des Reviewstarters darf eng korrigiert werden; das ist kein Anlass für Engine-/Produkt-Volltests oder für ein Modellfallback.

Bei PASS danach keine stillen Produkt-/Dokumentationscommits erzeugen. Reviewerurteil extern speichern, Endantwort nennt Kandidatenpaar und Pfade. Falls danach doch relevante Quellen/Verträge ändern, neue Kandidatenbindung und erneuter Review. Keine unendliche Self-SHA-Updatekette.

Bei nicht behebbaren echten Umgebungsblockern Ergebnis und fehlende Prüfung konkret ausweisen. Kein PASS erfinden, keine spätere Hintergrundvalidierung versprechen und keine fremden globalen Einstellungen ändern.
