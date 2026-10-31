# Unabhängiger P3-2-Kandidatenreview (Runde 3) – LSP und gemeinsame Sprachabfragen

**Status: FAIL für P3-2. Kein P3-2-PASS, kein G3-Abschluss.**

- Kandidat: `f960ceaacbd907fd2c377a964c339b4f662a9fb2` (`Keep unregistered LSP source URIs stable across platforms`)
- Vergleichsbasis: `fbcbc768cf4b87d29f565973c6f19a3903b57187` (P3-1-Dokumentabschluss)
- P3-1-Produkt: `9108b0387d9f1b82f506d8dc64e301e8522137dd`
- Separater Checkout: `/tmp/interlis-java-P3-2-r3-f960cea` (real: `/private/tmp/interlis-java-P3-2-r3-f960cea`)
- Bericht: `/Users/stefan/sources/interlis-java/build/review/P3-2-r3/review.md` (ausserhalb des Checkouts)
- Sauberkeit vorher: `git rev-parse HEAD` lieferte exakt den Kandidaten; `git status --porcelain=v1` war leer; `git diff --check` war leer.
- Sauberkeit nachher: `git rev-parse HEAD` unverändert; `git status --porcelain=v1` leer; `git diff --check` leer. Keine Änderungen an versionierten Dateien, keine Commits, kein Push.
- Eigene Quellen und Rohlogs ausschliesslich unter dem ignorierten `build/review/P3-2-r3/` des Checkouts.
- OS-Python-Sperre eingehalten: kein Python/pip/uv, auch nicht ad hoc. Verwendet wurden Java 25.0.3 (Temurin), Node 24.22.0, der Gradle-Wrapper 9.8.1 und Shell.
- Provider: direkte DeepSeek-API über OpenCode, nicht OpenRouter.

## 1. Kurzurteil

Die fünf Befunde des ersten Reviews sind in ihren ursprünglichen Reproduktionen geschlossen: symlinkter Projektroot, Watcher-/Realpfad-Aliase, Ressourcen-/Projektlimits, FailureId und übergrosse Dokumentupdates verhalten sich jetzt kontrolliert. Meine 76-Punkte-stdio-Probe, die 21-Punkte-Grenzprobe, die 12-Punkte-FailureId-Probe, die unveränderten Reviewerproben der ersten Runde (Hauptprobe 48/48 sowie F1/F2/F3/F5) und die JVM-/Portable-/SDK-/Conformance-Läufe sind grün.

Dennoch ist P3-2 nicht abnahmefähig: Die neue URI-Korrektur schützt unregistrierte Client-URIs, bildet aber einen **physischen Case-Alias einer bereits aufgenommenen Projektquelle bei `didOpen` nicht** auf die bestehende logische SourceId ab. Auf einem case-insensitiven Host (hier macOS APFS) entsteht dadurch eine dritte Compilerquelle mit demselben Modell; `compile` wird mit `AMBIGUOUS_INPUT` abgelehnt, Rename ist unvollständig. Das ist ein reproduzierbares MUSS-Finding (F6) und betrifft AC-P3-05.

| Kriterium | Verdikt | Kern |
|---|---|---|
| AC-P3-05 – Scheduling, Synchronisation, Quellen, Watcher | **FAIL** | F6: Case-/physischer Alias bei `didOpen` erzeugt zweite Quellidentität; F1/F3/F5 und Watcherpfade sonst grün |
| AC-P3-06 – Mehrdatei-Rename | **PASS** | direkte Rename-Verträge (Mehrdatei, Versionen, Disk-/Schreibschutz, Kollision, Originalsegmente) grün; F6 kann vorgelagert zu `CONTENT_MODIFIED` führen |
| AC-P3-07 – Completion, Tokens, Folding, Formatierung, Diagrammanker | **PASS** | Scope/Vererbung/Qualifizierer/Sprachversion/Range/Incomplete, gebundene Tokens, Folding, Formatierung, Hover und Snapshotanker grün |
| AC-P3-08 – Lifecycle, stdio, Fehlerarten | **PASS** | F3/F4 geschlossen; Lifecycle, stdio, Protokollfehler, null Exceptiondaten und Sitzung nach Abbruch grün |
| Kumulativ P3-1 | **PASS** | `check`, Portable 3548/3548, SDK 9, normative Conformance-Auswertung 577/16/0 grün |
| P3-3/P3-4/P3-5/G3 | offen | nicht Gegenstand dieses Teilreviews |

## 2. Umgebung und tatsächlich ausgeführte Prüfungen

| Bestandteil | Tatsächlich verwendet |
|---|---|
| Betriebssystem | macOS 15.8.1, Darwin arm64; temporäres Dateisystem **case-insensitiv** (`CASE_INSENSITIVE` verifiziert) |
| JDK | Temurin `25.0.3+9-LTS`, `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem` |
| Gradle | Repository-Wrapper 9.8.1 |
| Node | `v24.22.0` unter `/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node` |
| Python | nicht verwendet; kein Python/pip/uv |
| Browser | kein Chromium/Firefox/WebKit/Safari-Lauf in diesem Review; für den JVM-LSP nicht erforderlich |

Ausgeführte Kommandos und Resultate:

```text
git rev-parse HEAD
-> f960ceaacbd907fd2c377a964c339b4f662a9fb2

git status --porcelain=v1
-> (leer, vor und nach allen Proben)
git diff --check
-> (leer)

JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
  ./gradlew check \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
  --console=plain
-> Exit 0, BUILD SUCCESSFUL in 26s, 45 actionable tasks executed
-> 214 XML-Suites, 1137 Tests, 0 Failures, 0 Errors, 0 Skips
-> interlis-compiler: 759 (744 + 15 G2-Assertion-Fälle)
-> interlis-lsp: 43
-> interlis-cli: 237 (davon 145 Evidenzfälle)
-> interlis-workspace: 22, interlis-repository: 41, interlis-model: 16,
   interlis-projection: 12, interlis-wasm-bridge: 7
-> PYTHON_FREE_SOURCE_PASS 1341 inputs
-> SDK PASS snap-ca6702cc5434ba6f340711adc82de4ad557aa4248a6b5f14d3da13cfee0078e7 models=1

JAVA_HOME=... ./gradlew :interlis-lsp:installDist sdkProcessCheck sdkPackage --console=plain
-> Exit 0, BUILD SUCCESSFUL
-> SDK_PROCESS_PASS 9 commands

JAVA_HOME=... ./gradlew checkPortable -PnodeExecutable=... --console=plain
-> Exit 0, BUILD SUCCESSFUL in 1m 3s
-> js-contract-result.json: runtime teavm-js, stabilizationCases 3548, status passed
-> wasm-contract-result.json: runtime teavm-wasm-gc, stabilizationCases 3548, status passed

JAVA_HOME=... ./gradlew conformanceCheck -PnodeExecutable=... --console=plain
-> Exit 0, BUILD SUCCESSFUL in 1m 40s
-> normative Auswertung: selected=593 result=PASS rawExit=1
   {"PASS":577,"XFAIL":16,"FAIL":0,"XPASS":0,"ERROR":0,"TOTAL":593}
-> Roher Referenzlauf: Overall result: FAIL
   (PASS=577, XFAIL=1, FAIL=15, XPASS=0, ERROR=0)
```

Der rohe Conformance-Lauf bleibt `FAIL`; die normative Auswertung ist `PASS` (577/16/0). Der rohe Lauf wird hier nicht als Produkterfolg umetikettiert.

Nicht ausgeführt: `checkBrowser`, `releaseCheck`, `performanceCheck`, WebImage-/Chrome-/Firefox-/WebKit-/Safari-Läufe, `npm ci`, Linux-x64-/Windows-x64-Läufe. Diese Pfade sind für den JVM-LSP nicht erforderlich und bleiben P3-3/P3-5/G3.

## 3. Eigene unabhängige Gegenproben

Alle Quellen und Rohlogs liegen unter `build/review/P3-2-r3/probe/`. Die Proben sprechen den installierten LSP-Prozess über echtes stdio an oder prüfen öffentliche Serviceverträge. Sie wiederholen keine bestehende JUnit-Methode.

### 3.1 Hauptprobe `probe/independent-r3.mjs`

**76 von 76 Prüfungen PASS, Exit 0; Rohlog `probe/independent-r3.log`.** Zusätzlich fünf Beobachtungen (E1–E3, D6).

Geprüft wurden unter anderem:

- Pre-Initialize-Request `ServerNotInitialized` ohne Exceptiondaten; Capabilities für inkrementelle Synchronisation, Prepare-Rename und UTF-16.
- Symlinkter Projektroot mit zwei geschlossenen Quellen: `compile` `succeeded` (F1).
- `didOpen` über den realen Single-Slash-Pfad einer über den Symlink aufgenommenen Quelle: Analyse, Diagnostik und exakte Client-URI; keine zweite Quelle.
- Vererbung/Qualifizierer/Sprachversion/Ersetzungsbereich/`isIncomplete`; gebundene zeilenrichtige Tokens; Kommentar-Folding; Formatierung mit `tabSize`; Hover `plaintext`; staler Diagramm-Snapshot `InvalidParams`.
- Mehrdatei-Rename mit geschlossener und offener Quelle, `documentChanges`, offene Clientversion, geschlossene `null`, keine Datei geschrieben.
- Watcher auf dem realen Basispfad aktualisiert die offene abhängige Analyse und überschreibt den ungespeicherten Clienttext nicht.
- Hardlink-Alias (Change und Deletion) dupliziert bzw. löscht die kanonische Projektquelle nicht; `compile` bleibt `succeeded`.
- Unangekündigte Diskänderung und Schreibschutz verwerfen den gesamten Rename mit sicherem Fehler ohne Exceptiondaten.
- Kleines Analysebudget: `RequestCancelled(-32800)` mit `RESOURCE_LIMIT`, kein Notification-Stacktrace; Rückkehr durch grösseres Budget.
- Übergrosses `didChange`: sichere `SOURCE_LIMIT_EXCEEDED`-Meldung, kein Echo des Payloads, `ContentModified` statt alter Antwort, Deltas bis Vollersatz verweigert, Vollersatz resynchronisiert.
- No-op-Text mit exakter neuer Diagnostikversion; Abbruch eines Compile-Requests; Sitzung danach nutzbar; unbekannte Methode `MethodNotFound`; unbekanntes Dokument `InvalidParams`; `exit` nach `shutdown` Status 0.
- `initialize.initializationOptions` vor der Discovery: 2,1-MB-Projektquelle wird mit explizit erhöhtem Limit aufgenommen und kompiliert (B1–B3).
- Standard-Projektquellenlimit: `RequestFailed(-32803)` mit `SOURCE_LIMIT_EXCEEDED`, keine Exceptiondaten (C1–C4).
- Unregistrierte Client-URI `file:/p3-r3-unregistered-client.ili`: exakte SourceId, Analyse, Rename-URI und -Version, Schliessen/Wiederöffnen mit neuer Version (D1–D7).

### 3.2 Grenzprobe `probe/sync-edge-r3.mjs`

**21 von 21 Prüfungen PASS, Exit 0; Rohlog `probe/sync-edge-r3.log`.**

- Zwei unabhängige Dokumente; übergrosses `didOpen` eines dritten Dokuments wird sicher abgelehnt und blockiert **alle** Sprachabfragen (`ContentModified`), nicht nur das betroffene Dokument.
- Teil-Delta bleibt bis zum Vollersatz verweigert; Vollersatz resynchronisiert das Dokument und hebt die globale Sperre für die anderen Dokumente auf.
- 40 aufeinanderfolgende Vollersetzungen: nur die letzte Generation (`Gen39`, Version 49) wird beantwortet; Diagnostik trägt Version 49.
- Abbruch eines Compiles, danach nutzbare Sitzung; unbekannte Methode ohne Exceptiondaten; kein Stacktrace auf stderr; sauberer Exit.

### 3.3 FailureId-Probe `probe/InternalFailureR3.java`

**12 von 12 Prüfungen PASS, Exit 0; Rohlog `probe/internal-failure-r3.log`.**

Injizierte `CompilerInternalException` mit `FailureId=failure-r3-compile-20261031` bzw. `failure-r3-analyze-20261031`:

- Compile-Pfad und abgetrennter Analyse-Pfad liefern `-32603` mit derselben FailureId, ohne zufällige `lsp-`-Kennung, ohne Exceptiondaten und ohne technische Ursache (`PRIVATE_...`).

### 3.4 Case-Alias-Probe `probe/CaseAliasProbe.java`

**2 PASS, 2 FAIL; Exit 1; Rohlog `probe/case-alias-service.log`.**

Auf einem case-insensitiven Host:

```text
PASS initial project has two sources
PASS canonical open does not add a source
FAIL case-variant didOpen maps to the existing logical source :: expected=2 actual=3
FAIL compile succeeds after a case-variant open
  :: {outcome=rejected, reasons=[SOURCE_ERRORS, AMBIGUOUS_INPUT], ...}
SOURCES [
  .../Base.ili,
  .../Root.ili,
  .../base.ili
]
```

Das ist die Reproduktion von F6 (Abschnitt 5). Der Effekt ist auf macOS (case-insensitives APFS) unabhängig belegt; das Windows-Verhalten wurde hier nicht unabhängig ausgeführt, weil `WindowsPath`-Gleichheit case-insensitiv sein kann. Die Symlink-/Hardlink-Varianten mit unterschiedlichen Pfaden sind davon unabhängig.

### 3.5 Zusätzliche Beobachtung `probe/hardlink-open-r3.mjs`

Rohlog `probe/hardlink-open-r3.log`. Auf einem case-insensitiven Host:

```text
case-open before open compile {"modelCount":2,"outcome":"succeeded"}
case-open open alias diagnostics [{"code":"ILI-SEM-0010", ...
  "model name must be unique in the selected closure/MODEL_NAME_UNIQUE"}]
case-open after open compile {"reasons":["SOURCE_ERRORS","AMBIGUOUS_INPUT"],"outcome":"rejected"}
case-open rename {"code":-32803,"message":"analysis is incomplete"}
```

Dieselbe Wirkung tritt bei `didOpen` über einen unregistrierten Symlink- oder Hardlink-Alias einer Projektquelle auf (`sym-open`, `hard-open`). Die Watcher-Variante dagegen ist grün (`probe/case-alias-r3.log`: Compile `succeeded`, Rename zwei Einträge).

### 3.6 Wiederholung der unveränderten Reviewerquellen der ersten Runde

Die Originaldateien wurden unverändert in `probe/first-*.mjs` übernommen und gegen die Distribution dieses Kandidaten ausgeführt:

| Probe | Beobachtung | Wertung |
|---|---|---|
| `first-probe-lsp.mjs` | 48/48 PASS, Exit 0 | PASS |
| `first-symlink-root.mjs` | `compile` `succeeded`, `modelCount=1` | PASS (F1) |
| `first-uri-identity.mjs` | nach Alias-Watcher `compile` `succeeded`, `modelCount=2`, stderr leer | PASS (F2 Watcher) |
| `first-uri-identity-open.mjs` | nach Alias-Watcher `compile` `succeeded`, `modelCount=2`, stderr leer | PASS (F2 Watcher, offene Quelle) |
| `first-budget.mjs` | Hover/Compile `-32800`, `RESOURCE_LIMIT`, nur sichere Betriebsmeldung, stderr leer | PASS (F3) |
| `first-project-limit.mjs` | `initialize` `-32803`, `SOURCE_LIMIT_EXCEEDED`, stderr leer | PASS (F3) |
| `first-source-limit.mjs` | `documentSymbol` `-32801`, sichere `SOURCE_LIMIT_EXCEEDED`-Meldung, stderr leer | PASS (F5) |
| `first-source-limit-change.mjs` | keine alte Textantwort, keine Stacktrace-Ausgabe, sichere `SOURCE_LIMIT_EXCEEDED`-Meldung; `ContentModified` zusätzlich durch A49/S6 belegt | PASS (F5) |

## 4. Schliessung der Befunde F1–F5

| Finding | Eigene echte Gegenprobe | Resultat |
|---|---|---|
| F1 symlinkter Projektroot leer | `independent-r3.mjs` A6; `first-symlink-root.mjs` | **geschlossen** |
| F2 Watcher-/Realpfad-Alias erzeugt zweite Quelle | A7/A26/A32–A36; `first-uri-identity*.mjs`; `case-alias-r3.mjs` (Watcher) | **für Watcher/registrierte reale Pfade geschlossen**; `didOpen`-Case-Alias bleibt F6 |
| F3 Ressourcen-/Projektlimit als interner Fehler | A41–A46, B1–B3, C1–C4; `first-budget.mjs`; `first-project-limit.mjs` | **geschlossen** |
| F4 FailureId geht verloren | `InternalFailureR3.java` (Compile und Analyse) | **geschlossen** |
| F5 übergrosse `didOpen`/`didChange` still verworfen | A47–A52; `sync-edge-r3.mjs` S2–S8; `first-source-limit*.mjs` | **geschlossen** |

F2 ist damit in seinen ursprünglichen Reproduktionen geschlossen, aber die im ersten Befund ausdrücklich verlangte Abbildung „vor jeder Aufnahme/Änderung“ über `realPath`/`fileKey` ist für `didOpen` noch unvollständig; daraus folgt F6.

## 5. Neues reproduzierbares MUSS-Finding F6 – Case-/physischer Alias bei `didOpen` erzeugt eine zweite Quellidentität

**Anforderung:** Kapitel 07 §6.3: „Die Dateiidentität wird auf der I/O-Seite mit realen Pfaden beziehungsweise Dateischlüsseln bestimmt. Bekanntes logisches SourceId-Mapping bleibt erhalten; eine noch nicht zugeordnete Client-URI bleibt bis zu belegter Dateizuordnung unverändert … die exakte offene Client-URI wird getrennt für Diagnostik, Navigation und Versionsedits benutzt.“ Kapitel 08: Dateipfadnormalisierung, Gross-/Kleinschreibung und Symlinks sind explizite Hostregeln. Der erste Review F2 verlangt die Abbildung eingehender URIs über `realPath`/`fileKey` „vor jeder Aufnahme/Änderung“ und Tests mit Case-/Symlink-/Hardlink-Aliasen. AC-P3-05.

**Reproduktion:** `build/review/P3-2-r3/probe/CaseAliasProbe.java` (bzw. `hardlink-open-r3.mjs`, Szenario `case-open`). Ein Projektordner enthält `Base.ili` und `Root.ili`. Nach `initialize` und einem kanonischen `didOpen` von `Root.ili` öffnet der Client dieselbe Datei als `base.ili` (nur andere Gross-/Kleinschreibung) mit demselben Text.

**Beobachtung:**

```text
PASS initial project has two sources
PASS canonical open does not add a source
FAIL case-variant didOpen maps to the existing logical source :: expected=2 actual=3
FAIL compile succeeds after a case-variant open
  :: {outcome=rejected, reasons=[SOURCE_ERRORS, AMBIGUOUS_INPUT]}
SOURCES [ .../Base.ili, .../Root.ili, .../base.ili ]
```

`documentSymbol` für `base.ili` liefert `ILI-SEM-0010` „model name must be unique in the selected closure/MODEL_NAME_UNIQUE“; `compile` wird mit `SOURCE_ERRORS, AMBIGUOUS_INPUT` abgelehnt; `textDocument/rename` antwortet `-32803 analysis is incomplete`. Der Watcher-Pfad für dieselbe Case-Variante ist dagegen grün (`case-alias-r3.log`: `compile` `succeeded`, Rename zwei Einträge).

Die neue Regel „eine noch nicht zugeordnete Client-URI bleibt unverändert“ ist kein Gegenargument: `base.ili` ist keine neue opake URI, sondern löst auf demselben Host auf dieselbe reale Datei auf; die Zuordnung ist über `toRealPath`/`Files.isSameFile` belegbar. Die bewusste Erhaltung wirklich unbekannter URIs darf nicht mit dem Ignorieren bekannter Dateialiasse verwechselt werden.

**Wirkung:** Eine bereits aufgenommene Projektquelle wird unter einer zweiten logischen URI installiert. Dadurch entstehen ein doppeltes Modell, ein falscher Mehrdeutigkeitsfehler, ein abgelehnter Compile und eine unvollständige Rename-Grundlage, obwohl der Client nur eine andere Schreibweise derselben Datei geöffnet hat. Das ist genau die im ersten Review beanstandete Wirkung von F2; sie tritt hier über `didOpen` statt über den Watcher auf. Auf einem case-sensitiven Linux-Host ist der Fall ein anderer (die Datei existiert unter dieser Schreibweise nicht). Auf macOS arm64 (Zielplattform, case-insensitives APFS) ist er real und unabhängig reproduziert; das Windows-Verhalten wurde hier nicht unabhängig ausgeführt. Die Symlink-/Hardlink-Varianten mit unterschiedlichen Pfaden sind plattformunabhängig.

**Ursache/Datei:** `interlis-lsp/src/main/java/guru/interlis/lsp/InterlisTextDocumentService.java:222-229` (`filePath`, `canonicalUriNow`) sucht nur nach dem rohen `Path`-Schlüssel in `fileIdentities`; `:323-336` (`didOpenNow`) verwendet diesen Wert direkt als `SourceId`. `:230-237` (`rememberFile`) registriert nur `file.path()` und `file.realPath()`, nicht den Case-Alias und keinen `fileKey`-Index. `ProjectSourceLoader.java:61-76` ermittelt zwar `realPath` und `fileKey`, aber `installProject` (`InterlisTextDocumentService.java:278-293`) und `didOpenNow` nutzen diese Identität nicht. Die I/O-seitige `sameFile`-Abbildung existiert nur im Watcher-Pfad (`InterlisWorkspaceService.java:103-121`).

**Gegenempfehlung:** Vor der Aufnahme einer lokalen Datei in `didOpenNow` die eingehende URI auf der begrenzten I/O-Queue über `toRealPath`, `Files.isSameFile` beziehungsweise `fileKey` gegen die bekannten geschlossenen und offenen Quellen abbilden; bei einem Treffer die bestehende logische `SourceId` verwenden und die exakte Client-URI nur getrennt für Diagnostik, Navigation und Versionsedits führen. Echte unregistrierte URIs (z.B. `file:/p3-unregistered-client.ili`) weiterhin unverändert lassen. Denselben `fileKey`-Index bei der Discovery verwenden (siehe H4). Prozess-/Vertragstest: Case-Alias, Symlink-Datei und Hardlink einer bereits aufgenommenen Quelle per `didOpen`; erwartet wird unveränderte Quellenzahl, `compile` `succeeded` und ein vollständiger Rename. Die bestehende positive Kontrolle für die unregistrierte opaque URI muss erhalten bleiben.

## 6. Weitere Beobachtungen ohne eigenständiges MUSS

**H1 – Hardlink-Aliasse bei der Projektaufnahme.** `independent-r3.log` E1/E2: Ein Projekt mit `M.ili` und dem Hardlink `M-hard.ili` liefert schon vor jedem `didOpen` `compile` `rejected` mit `ILI-SEM-0010/MODEL_NAME_UNIQUE`; der Rename ist unvollständig. Das ist dieselbe `fileKey`-Lücke wie F6, aber die Vertragsformulierung „realen Pfaden beziehungsweise Dateischlüsseln“ lässt auch die realen (unterschiedlichen) Hardlink-Pfade als Identität zu; deshalb hier als offene Grenzfrage und nicht als separates MUSS geführt. Empfehlung: `fileKey`-Dedupe bereits in der Discovery, damit Hardlink-Aliasse wie gefordert ein Primärmapping behalten.

**H2 – Verschachtelte Verzeichnis-Symlinks.** `independent-r3.log` E3: Ein realer Projektroot mit `models -> extern` und einem dort liegenden importierten Modell ergibt `MISSING_INPUT`, weil `walkFileTree` ohne `FOLLOW_LINKS` den verschachtelten Symlink nicht traversiert. Der normative Text verlangt ausdrücklich nur den symlinkten **Root**; deshalb keine MUSS-Wertung. Falls verschachtelte Symlink-Modellordner zum Projektprofil gehören, ist `FOLLOW_LINKS` mit expliziter Zykluserkennung und derselben logischen Pfadabbildung erforderlich.

**H3 – Speicherhaltung abgelehnter Riesentexte.** Verhalten geprüft: Der abgelehnte Text wird nicht in die Diagnostik/Meldung übernommen, nicht als alter Text beantwortet und nicht als Quelltext publiziert. Eine direkte Heap-/Retention-Messung habe ich nicht durchgeführt; die Aussage „nicht gehalten“ stützt sich auf den Codezustand (`RejectedOpen` speichert nur `SourceId`/Version) und das beobachtete Verhalten.

**H4 – Roher Conformance-Lauf bleibt `FAIL`.** Die 15 rohen Fehler sind die freigegebenen normativen Ausnahmen; die Auswertung `conformanceAssessment` meldet 577/16/0. Kein neuer Produktbefund, aber der rohe Lauf darf nicht als PASS bezeichnet werden.

**H5 – Plattformmatrix.** Die Linux-/Windows-Matrix am Kandidaten `f960cea` lief in diesem Review nicht; die Elternhinweise auf eine laufende Matrix ersetzen keinen eigenen Nachweis. Der Case-Alias-Fall wurde hier auf macOS arm64 (case-insensitives APFS) reproduziert; Windows wurde nicht unabhängig ausgeführt und wird nicht als PASS oder FAIL abgeleitet.

## 7. Kriterienmatrix AC-P3-05 bis AC-P3-08

### AC-P3-05 – Dokumentnotifications, neueste Generation, Abbruch, bedingte Annahme, Quellen/Watcher

| Anforderung | Beobachtung | Verdikt |
|---|---|---|
| INC-018/022 – veraltete Ergebnisse, Capture/paralleles Update, bedingte Annahme | `P3AnalysisSchedulingTest`; `sync-edge-r3.mjs` S9/S10; `independent-r3.mjs` A52 | PASS |
| INC-019 – Konfigurations-/Resolverwechsel als Workspace-Änderung | Budget-Konfiguration A41–A46; Watcher-Refresh A32; `P3PresentationConfigurationTest` | PASS |
| LSP-003 – Watcher überschreiben offenen Clienttext nicht | A32/A33; `case-alias-r3.mjs` (Case-Watcher); `P3ProjectRenameTest` | PASS (Watcher) |
| LSP-004 – atomare mehrteilige `didChange` | A47–A52; `sync-edge-r3.mjs` S2–S8; `P3DocumentSynchronizationTest` | PASS |
| LSP-006 – leere Diagnostik ersetzt frühere | A32; No-op-Version A53; `P3ProjectRenameTest` | PASS |
| LSP-011 – Abbruch in Engine/Worker, Sitzung danach nutzbar | A54/A55; S11/S12; `LspProtocolCancellationTest` | PASS |
| LSP-015 – Repositoryänderung aktualisiert abhängige offene Analyse | A32/A33; `case-alias-r3.mjs` | PASS |
| Bedingte Cacheannahme / neueste Generation | `P3AnalysisSchedulingTest`; 40-Edit-Grenzprobe S9/S10 | PASS |
| Quellidentität / geschlossene Projektquellen / `didOpen`-Alias | F6: Case-Alias erzeugt dritte Quelle; Symlink-Root und Watcher-Aliase grün | **FAIL** |

**AC-P3-05: FAIL** wegen F6. F1/F3/F5 und die Watcher-/Schedulingpfade sind geschlossen.

### AC-P3-06 – Workspace-weites Rename (LSP-012)

| Prüfung | Beobachtung | Verdikt |
|---|---|---|
| Mehrdatei-Rename offen/geschlossen | A24–A31; `P3ProjectRenameTest`; Original-Hauptprobe | PASS |
| `documentChanges`/Clientversionen | offene exakte Version, geschlossene `null`; ohne Capability `changes` | PASS |
| Diskhash/Pfadziel/Schreibrecht | A37–A40; `P3ProjectRenameTest` | PASS |
| Kollision/Originalsegmente/Revisionen | `P3CompletionScopeContractTest` (äussere/geerbte Kollisionen); `LanguageQueryService.planRename` prüft Originalsegmente und `CONTENT_MODIFIED` | PASS |
| Fehlende/unvollständige Quelle | `P3CompletionScopeContractTest`, `LspFeatureTest`; Rename verweigert unvollständige Analyse | PASS |

**AC-P3-06: PASS** für die direkten Rename-Verträge. F6 ist eine vorgelagerte Quellidentitätsstörung (AC-P3-05), keine Verletzung der Rename-Einzelprüfungen.

### AC-P3-07 – gemeinsame Completion/Tokens/Folding/Formatierung/Diagrammanker

| Prüfung | Beobachtung | Verdikt |
|---|---|---|
| Scope, Import, Vererbung | A8–A12; `P3CompletionScopeContractTest` | PASS |
| Qualifizierer/Member | A13–A16; `membersOf`/`visibleSymbols` | PASS |
| Sprachversion/Keywords | `P3CompletionScopeContractTest`: `MULTICOORD` nur 2.4 | PASS |
| Ersetzungsbereich/InsertText/Incomplete | A15/A16; `P3CompletionProtocolTest` (100-Trunkierung, `isIncomplete`) | PASS |
| Gebundene Tokens/Zeilen | A17–A19; `P3PresentationConfigurationTest` | PASS |
| Folding | A20; `P3PresentationConfigurationTest` | PASS |
| Formatierung | A21; ungültige Quelle Fehler; `P3PresentationConfigurationTest` | PASS |
| Hover/Diagrammanker | A22/A23; `LspProcessTest` Snapshot/Stale; `LspErrorContractTest` | PASS |

**AC-P3-07: PASS.**

### AC-P3-08 – Lifecycle, stdio, Fehlerarten

| Anforderung | Beobachtung | Verdikt |
|---|---|---|
| LSP-001 – dünner Adapter, keine zweite Binder-/Typsemantik | Inspektion: LSP nutzt Workspace/LanguageQueries, kein ANTLR/Binder im LSP-Modul | PASS |
| LSP-002 – initialize/initialized/shutdown/exit/Dokumentlebenszyklus | A1/A2/A61/A62; `LspProcessTest`; Original-Hauptprobe | PASS |
| LSP-008 – stdio, stdout nur Protokoll | A59/A60; Framing und stderr-Prüfung | PASS |
| LSP-009 – keine globalen Compile-Locks/Listener | Inspektion Scheduler-Executors, `LanguageQueries.create`, zwei Serverinstanzen in Tests | PASS |
| LSP-016 – Transport-/Compiler-/interne Fehler unterscheidbar | A41–A45, C1–C3, A56–A58; `InternalFailureR3.java`; `LspErrorContractTest` | PASS |

**AC-P3-08: PASS** (F3/F4 geschlossen; Lifecycle, stdio und Fehlerpfade grün).

### Kumulative P3-1-Regression

- `check` Exit 0, 1137 Fälle ohne Fehler/Skips; 759 Compiler- (744 + 15 G2), 43 LSP- und 145 Evidenzfälle.
- `checkPortable` Exit 0; JS und WasmGC je 3548 Fälle, Status `passed`.
- `sdkProcessCheck` Exit 0, 9 Kommandos; `sdkPackage` und LSP-`installDist` Exit 0.
- `conformanceCheck` Exit 0; normative Auswertung 577 PASS / 16 XFAIL / 0 FAIL; roher Lauf wie oben erklärt `FAIL`.
- Die P3-1-CLI-/Formatter-Verträge wurden durch die P3-2-Änderungen nicht verändert; die P3-1-Abnahme aus `docs/p3/reviews/P3-1-review.md` bleibt gültig. Kein P3-1-Regressionsfinding in diesem Lauf.

**Kumulativ P3-1: PASS** (lokal macOS arm64; keine Browser-/Plattformmatrix).

## 8. Quellbindung, Pins und Statuswahrheit

- `docs/p3/STATUS.md` bezeichnet P3-2 korrekt als offen; dieser Bericht ändert den Status nicht.
- `docs/p3/implementation/P3-2.md` nennt für den Korrekturkandidaten 42 LSP-Fälle (1c19acb) und 43 LSP-Fälle (f960cea); die tatsächlichen Zähler dieses Laufs (43 LSP, 759 Compiler inkl. 15 G2, 145 Evidenz) stimmen mit dem aktuellen Stand überein.
- Die aktiven G2-Quellpins, Methoden-/Range-/Zeilenlocator wurden mechanisch nachgeführt; die historischen Norm-/Status-/Originalerwartungen bleiben unverändert. `stabilization-contract.cjs` verlegt nur die Abfrageposition in die abgeleitete Klasse; die Erwartung `inherited` bleibt bestehen. Ich habe keine abgeschwächte Sprach-/Scopeerwartung gefunden.
- `testdata/g2/completion-links.json`, `predefined-catalogue.json` und die Evidence-Hashes enthalten mechanische Offset-/Hash-Nachführungen; die Evidenz-Suite ist grün. Keine G2-Norm-/Kompatibilitätsentscheidung wurde geändert.
- `docs/evidence/p3/`, die G2-Normprofile und die Originalpins wurden von mir nicht verändert.

## 9. Nicht ausgeführt / Grenzen

- Kein `checkBrowser`, kein `releaseCheck`, kein `performanceCheck`, kein WebImage-/Chrome-/Firefox-/WebKit-/Safari-Lauf, kein `npm ci`.
- Keine Linux-x64-/Windows-x64-Ausführung; die Plattformmatrix bleibt P3-5/G3. Die Elternhinweise auf laufende Matrizen ersetzen keinen eigenen Nachweis.
- `check` ist ein Aggregat der XML-Resultate; die Tasks wurden in diesem Lauf ausgeführt (45 executed), einzelne Ergebnisse können aus Modul-XML stammen. Die Zähler sind keine Behauptung, jeder Modultask sei in genau diesem Prozess neu gelaufen.
- Der rohe Conformance-Lauf bleibt `FAIL` (577/1/15), die normative Auswertung `PASS` (577/16/0). Die Rohausgabe wird hier nicht als Produkterfolg umetikettiert.
- Keine direkte Heap-/Retention-Messung für abgelehnte Riesentexte; nur Verhalten und Codeinspektion.
- Der Case-Alias-Befund wurde auf macOS arm64 (case-insensitives APFS) reproduziert; auf einem case-sensitiven Linux-Host ist die Datei unter der abweichenden Schreibweise nicht vorhanden. Windows wurde nicht unabhängig ausgeführt; aus dem macOS-Befund wird kein Windows-Urteil abgeleitet. Die Symlink-/Hardlink-Varianten mit unterschiedlichen Pfaden wurden zusätzlich auf macOS ausgeführt.
- Keine Reparatur am Kandidaten, keine Änderung versionierter Dateien, kein Commit/Push; eigene Dateien nur unter `build/`.

## 10. Gesamturteil

**P3-2: FAIL.**

- AC-P3-05: **FAIL** (F6: Case-/physischer Alias bei `didOpen` erzeugt eine zweite Quellidentität; F1/F3/F5 und Watcherpfade geschlossen)
- AC-P3-06: **PASS** (direkte Rename-Verträge)
- AC-P3-07: **PASS** (Completion, Tokens, Folding, Formatierung, Diagrammanker)
- AC-P3-08: **PASS** (Lifecycle, stdio, Fehlerarten; F3/F4 geschlossen)
- Kumulativ P3-1: **PASS** (JVM, Portable, SDK, normative Conformance-Auswertung)

F6 ist ein reproduzierbares MUSS-Finding, keine blosse Testnamen- oder Timingbeobachtung. Es betrifft die im ersten Review verlangte Quellidentität über `realPath`/`fileKey` und die neue URI-Korrektur: Unregistrierte opaque URIs bleiben korrekt unverändert, aber ein Case-/Symlink-/Hardlink-Alias einer bereits aufgenommenen Projektquelle wird bei `didOpen` nicht auf die bestehende logische SourceId abgebildet. Bis zu dessen Behebung und einer neuen unabhängigen Gegenprobe ist P3-2 nicht abgenommen. P3-3 bis P3-5 und G3 bleiben offen; ein Teil-PASS von AC-P3-06/07/08 darf nicht als P3-2- oder G3-Abschluss gelesen werden.
