# Unabhängiger P3-2-Kandidatenreview (r2) – LSP und gemeinsame Sprachabfragen

**Status: FAIL für P3-2. Kein P3-2-PASS, kein G3-Abschluss.**

- Kandidat: `1c19acbbe021f56192f7dbbc6173aaf4cecbfeeb` (`Fix P3 LSP review findings for file identity and safe failures`)
- Vergleichsbasis: `fbcbc768cf4b87d29f565973c6f19a3903b57187` (P3-1-Dokumentabschluss)
- P3-1-Produkt: `9108b0387d9f1b82f506d8dc64e301e8522137dd`
- Erster unabhängiger P3-2-Review: `docs/p3/reviews/P3-2-466790c-FAIL.md`, SHA-256 `a4ec9ca453398ad5776106130a0e5eaf2d377bd967c0eb5ef60ea29f1b40f01b` (unverändert gelesen)
- Separater Checkout: `/tmp/interlis-java-P3-2-r2-1c19acb` (real: `/private/tmp/interlis-java-P3-2-r2-1c19acb`)
- Sauberkeit vorher und nachher: `git rev-parse HEAD` lieferte exakt den Kandidaten; `git status --porcelain=v1` war vor Beginn und nach allen Proben leer; `git diff --check` war leer.
- Keine Änderungen an versionierten Dateien, keine Commits, kein Push. Eigene Quellen und Rohlogs ausschliesslich unter dem ignorierten `build/review/P3-2-r2/` im Checkout; der Bericht liegt ausserhalb des Checkouts.
- Umfang: AC-P3-05 bis AC-P3-08 vollständig, erneute Prüfung von F1–F5 aus dem unveränderten Erstbericht, kumulative Regression zu P3-1 sowie die berührten Verträge aus Kapitel 05/07/09/13/14/16/17.

## 1. Kurzurteil

F1 bis F5 aus dem ersten FAIL-Bericht sind am Korrekturkandidaten mit eigenen echten Prozess-/RPC-Proben **geschlossen**:

- F1: symlinkter Projektroot wird real traversiert und mit logischen SourceIds aufgenommen; Compile sieht die geschlossenen Quellen.
- F2: der ursprüngliche Watcher-/`/var`-Alias-Fall und offene Quellen ohne Root erzeugen keine zweite Compilerquelle und überschreiben keinen ungespeicherten Clienttext.
- F3: erschöpftes Analysebudget liefert `RequestCancelled` mit `RESOURCE_LIMIT`; übermässige Projektquellen liefern einen sicheren `RequestFailed` mit `SOURCE_LIMIT_EXCEEDED`; `initializationOptions` wirken vor der Discovery.
- F4: die `FailureId` einer `CompilerInternalException` bleibt über den echten JSON-RPC-Pfad in Antwort und Betriebsmeldung erhalten; Exceptiondaten bleiben `null`.
- F5: abgelehnte übergrosse `didOpen`/`didChange` führen zu einem globalen sicheren Unsync-Schutz, liefern `ContentModified`, halten den Riesentext nicht und erlauben eine vollständige Resynchronisation.

Dennoch ist P3-2 nicht abnahmefähig. Es bleibt ein reproduzierbarer MUSS-Befund zur physischen Dateiidentität:

**F6 – In-Projekt- und unbekannte Dateialiasse erzeugen weiterhin eine zweite Quelle oder verlieren die Primärquelle.** Ein symlinkter Alias im Projekt kann als kanonische Quelle gewählt werden; wird er gelöscht, verschwindet die weiterhin vorhandene Primärdatei aus dem Compilerbild (`MISSING_INPUT`). Ein Hardlink im Projekt erzeugt zwei Quellen (`AMBIGUOUS_INPUT`). Ein `didOpen` über einen unbekannten Symlink-Alias erzeugt eine zweite Modellquelle (`SOURCE_ERRORS`).

Damit ist **AC-P3-05 FAIL**. AC-P3-06 und AC-P3-07 sind für die direkt geprüften Verträge PASS. AC-P3-08 ist nach F3/F4 PASS. Die kumulative lokale P3-1-Regression (macOS arm64) ist PASS; die externe Plattformmatrix am Kandidaten ist jedoch **nicht** PASS: Linux x64 bestand, Windows x64 schlug im Schritt „Verify JVM, CLI and installed SDK processes“ fehl. P3-3 bis P3-5 und G3 bleiben offen.

| Kriterium | Verdikt | Kern |
|---|---|---|
| AC-P3-05 – Scheduling, Synchronisation, Quellen, Watcher | **FAIL** | F6: physische Aliasidentität bei Discovery, Aliaslöschung und unbekanntem `didOpen` unvollständig; F1/F2/F5 im ursprünglichen Umfang geschlossen |
| AC-P3-06 – Mehrdatei-Rename | **PASS** | Mehrdatei, geschlossene Quelle/ungespeicherter Text, Versionen, Disk-/Schreibschutz, Kollision, fehlende Revision, Originalsegmente grün; F6 bleibt vorgelagerte AC05-Störung |
| AC-P3-07 – Completion, Tokens, Folding, Formatierung, Diagrammanker | **PASS** | Scope/Vererbung/Qualifizierer/Sprachversion/Ersetzungsbereich/Incomplete, gebundene Tokens, Folding, Formatierung, Plaintext-Hover und Snapshotanker grün |
| AC-P3-08 – Lifecycle, stdio, Fehlerarten | **PASS** | F3/F4 geschlossen; Lifecycle, stdio, Protokollfehler, null Exceptiondaten und Sitzung nach Abbruch grün |
| Kumulativ P3-1 (lokal JVM/macOS) | **PASS** | `check`, `checkPortable`, SDK 9, normative Conformance 577/16/0 |
| Externe Plattformmatrix (P3-5/G3) | **offen/FAIL** | Linux x64 PASS, Windows x64 FAIL; Ursache ohne authentifizierte Logs nicht bestimmbar |
| P3-3/P3-4/P3-5/G3 | offen | nicht Gegenstand dieses Teilreviews |

## 2. Umgebung und tatsächlich ausgeführte Prüfungen

| Bestandteil | Tatsächlich verwendet |
|---|---|
| Betriebssystem | macOS 15.8.1, Darwin 24.6.0, arm64 |
| JDK | Temurin `25.0.3+9-LTS`, `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem` |
| Gradle | Repository-Wrapper 9.8.1 |
| Node | `v24.22.0` unter `/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node` |
| Python | kein Python/pip/uv ausgeführt. Ein versehentlicher Shell-Aufruf von `python3` wurde mit `command not found` abgewiesen; es lief kein Python-Prozess. |
| Browser/Safari/WebImage | in diesem Lauf nicht ausgeführt; für den JVM-LSP nicht erforderlich |
| Linux/Windows | nicht lokal; externe GitHub-Actions-Matrix am Kandidaten abgefragt (siehe §7) |

Ausgeführte Kommandos und Resultate:

```text
git rev-parse HEAD
-> 1c19acbbe021f56192f7dbbc6173aaf4cecbfeeb

git status --porcelain=v1
-> (leer, vor und nach allen Proben)
git diff --check
-> (leer)

JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
  ./gradlew check :interlis-lsp:installDist \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
  --console=plain
-> Exit 0, BUILD SUCCESSFUL in 25s
-> 214 XML-Suites, 1136 Tests, 0 Failures, 0 Errors, 0 Skips
-> interlis-compiler: 759 Testfälle (744 + 15 G2-Assertions)
-> interlis-lsp: 42 Testfälle
-> interlis-cli: 237 Testfälle, darunter 145 Evidenzfälle
-> interlis-workspace: 22, interlis-repository: 41, interlis-model: 16,
   interlis-projection: 12, interlis-wasm-bridge: 7
-> PYTHON_FREE_SOURCE_PASS 1341 inputs
-> SDK PASS snap-ca6702cc5434ba6f340711adc82de4ad557aa4248a6b5f14d3da13cfee0078e7 models=1

JAVA_HOME=... ./gradlew sdkProcessCheck conformanceCheck checkPortable \
  -PnodeExecutable=... --console=plain
-> Exit 0, BUILD SUCCESSFUL in 2m 21s
-> SDK_PROCESS_PASS 9 commands
-> normative Conformance: selected=593 result=PASS
   {"PASS":577,"XFAIL":16,"FAIL":0,"XPASS":0,"ERROR":0,"TOTAL":593}
-> roher Referenzlauf: Overall result: FAIL
   (PASS=577, XFAIL=1, FAIL=15, XPASS=0, ERROR=0)
-> js-contract-result.json: teavm-js, stabilizationCases 3548, status passed
-> wasm-contract-result.json: teavm-wasm-gc, stabilizationCases 3548, status passed

JAVA_HOME=... node build/review/P3-2-r2/probe/probe-r2.mjs
-> Exit 0, PROBE R2 PASS checks=93 failures=0
-> Rohlog: build/review/P3-2-r2/probe-r2.log

JAVA_HOME=... javac -cp "interlis-lsp/build/install/interlis-lsp/lib/*" \
  -d build/review/P3-2-r2/probe build/review/P3-2-r2/probe/F4Probe.java
JAVA_HOME=... java -cp "build/review/P3-2-r2/probe:interlis-lsp/build/install/interlis-lsp/lib/*" F4Probe
-> Exit 0, F4_PROBE_PASS failureId=failure-p3-r2-independent-20261031
   code=-32603 data=absent log=present
-> Rohlog: build/review/P3-2-r2/f4-probe.log

JAVA_HOME=... node build/review/P3-2-r2/probe/finding-f6.mjs
-> Exit 1, F6 REPRODUCED checks=8 failures=7
-> Rohlog: build/review/P3-2-r2/finding-f6.log
```

Der rohe Conformance-Lauf bleibt `FAIL` (577/1/15); die normative Auswertung mit den freigegebenen Ausnahmen ist `PASS` (577/16/0). Die Rohausgabe wird hier nicht als Produkterfolg umetikettiert.

`check` ist ein Aggregat der vorhandenen XML-Resultate; einzelne Tasks waren `UP-TO-DATE`. Die Zähler oben sind keine Behauptung, jeder Modultask sei in genau diesem Prozess frisch gelaufen. Die LSP-, Compiler-, CLI-Evidenz- und Portable-Tasks wurden in den genannten Läufen tatsächlich ausgeführt.

## 3. Eigene unabhängige Gegenproben

Alle eigenen Quellen und Rohlogs liegen unter dem ignorierten `build/review/P3-2-r2/` des Kandidaten. Die Proben sind keine Wiederholung bestehender JUnit-Tests; sie sprechen den installierten LSP-Prozess über echtes stdio an oder prüfen öffentliche Randverträge.

### 3.1 Hauptprobe `build/review/P3-2-r2/probe/probe-r2.mjs`

**93 von 93 Prüfungen PASS, Exit 0; Rohlog `build/review/P3-2-r2/probe-r2.log`.**

Die Probe startet `guru.interlis.lsp.Main` als eigenen Java-Prozess und deckt unter anderem ab:

- S1 – symlinkter Workspace-Root: `initialize` erfolgreich; `interlis.compile` liefert `succeeded` mit genau zwei Modellen; offene Quelldatei über die logische Symlink-URI symbolisierbar. Watcher über den realen Pfad aktualisiert die Diagnostik der offenen abhängigen Datei, ohne den ungespeicherten Text zu überschreiben; kein zweites Basismodell. Hardlink-Alias `Changed`/`Deleted` dupliziert oder löscht die Primärquelle nicht. Mehrdatei-Rename nutzt logische Client-URIs, exakte offene Version, `null` für geschlossene Quelle und schreibt keine Datei.
- S2 – offene Quellen ohne Workspace-Root: Watcher über einen unbekannten realen Alias erzeugt keine zweite Quelle; der ungespeicherte Puffer bleibt erhalten; Löschmeldung bei vorhandener Datei entfernt die Quelle nicht; Rename umfasst beide offenen Quellen über Client-URIs.
- S3 – übergrosse `didChange`: `documentSymbol` und Compile liefern `ContentModified` (-32801) ohne Exceptiondaten; Betriebsmeldung nennt `SOURCE_LIMIT_EXCEEDED` und enthält den Riesentext nicht; kein Stacktrace; ein Delta vor der Resynchronisation bleibt verweigert; ein vollständiger Ersatz resynchronisiert und Compile ist danach erfolgreich.
- S4 – übergrosse initiale `didOpen`: sicherer Unsync-Zustand, `SOURCE_LIMIT_EXCEEDED` im Betriebskanal, kein Stacktrace; nach vollständigem Ersatz ist die Quelle analysierbar und Compile erfolgreich.
- S5 – Ressourcengrenzen: 2,2-MB-Projektquelle über dem Standardlimit ergibt beim `initialize` `RequestFailed` (-32803) mit `SOURCE_LIMIT_EXCEEDED`, ohne Exceptiondaten und ohne Stacktrace. Dasselbe Projekt mit explizitem erhöhtem `initializationOptions`-Budget wird vor der Discovery übernommen und kompiliert. Ein gültiges kleines Analysebudget nach dem Öffnen ergibt für Symbol/Compile `RequestCancelled` (-32800) mit `RESOURCE_LIMIT`; Zurücknehmen des Budgets stellt die Abfragen wieder her. Ungültige `initializationOptions` ergeben `InvalidParams` ohne Stacktrace.
- S6 – gemeinsame Abfragen und Lifecycle: Pre-Initialize-Antwort `ServerNotInitialized`; doppeltes `initialize` `InvalidRequest`; geerbtes Attribut im `Child`, lokales Attribut daneben; am Dokumentanfang kein `inherited` und `isIncomplete=true`; qualifizierte Completion `Base.Common` mit Ersetzungsbereich nach dem Punkt und über den ganzen Namen; gebundene Tokens (`inherited` als `property`, Kommentar als `comment`), zeilenbegrenzte UTF-16-Spannen; mehrzeiliger Kommentar faltbar; Formatierung mit `tabSize=3` (sechs Leerzeichen auf Klassenebene); Hover `plaintext`; Diagramm mit SnapshotId parsebar, veralteter Snapshot `InvalidParams`, falsch typisierte Argumente `InvalidParams` ohne Daten; unbekanntes Dokument `InvalidParams`; 20 schnelle Vollersetzungen – neuester Stand `M20` und Diagnostikversion 21; No-op-Text mit Version 22; Abbruch eines Compiles beantwortet kontrolliert, Sitzung bleibt nutzbar; `shutdown`/`exit` Status 0; Request nach Shutdown `InvalidRequest`; kein Stacktrace im gesamten Lauf.

### 3.2 F4-Probe `build/review/P3-2-r2/probe/F4Probe.java`

**`F4_PROBE_PASS`, Exit 0; Rohlog `build/review/P3-2-r2/f4-probe.log`.**

Die Probe erzeugt einen echten LSP4J-JSON-RPC-Framepfad über Pipes, injiziert einen `CompilerEngine`-Proxy, dessen `compile` eine `CompilerInternalException` mit `FailureId=failure-p3-r2-independent-20261031` wirft, und ruft den Compile über `workspace/executeCommand` auf. Ergebnis:

```text
F4_PROBE_PASS failureId=failure-p3-r2-independent-20261031 code=-32603 data=absent log=present
```

Die RPC-Fehlermeldung enthält exakt die Compiler-FailureId; `data` fehlt bzw. ist `null`; der technische Text `PRIVATE_FAILURE_DETAIL` erscheint weder in der Antwort noch in der Betriebsmeldung. Das ist eine neue, unabhängige Probe gegenüber dem bestehenden `LspErrorContractTest`.

### 3.3 Negative Gegenprobe F6 `build/review/P3-2-r2/probe/finding-f6.mjs`

**`F6 REPRODUCED`, Exit 1; Rohlog `build/review/P3-2-r2/finding-f6.log`.**

Die Probe ist absichtlich negativ und dokumentiert den offenen MUSS-Befund in §5. Sie prüft drei öffentliche Prozesspfade:

| Fall | Erwartung | Beobachtung |
|---|---|---|
| F6a Hardlink im Projekt (`HardBase.ili` -> `Base.ili`) | eine Quelle, Compile `succeeded`/`modelCount=2` | `rejected`, `SOURCE_ERRORS`, `AMBIGUOUS_INPUT` |
| F6b Symlink-Alias im Projekt (`AliasBase.ili` -> `Base.ili`, sortiert vor `Base.ili`), danach Löschen des Alias | Primärquelle `Base.ili` bleibt; Compile `succeeded` | nach Löschen `rejected`, `SOURCE_ERRORS`, `MISSING_INPUT`; auch ein Watcher auf `Base.ili` stellt die Quelle nicht wieder her |
| F6c `didOpen` über unbekannten Symlink-Alias (`AliasRoot.ili` -> `Root.ili`) nach Öffnen von `Root.ili` | eine physische Quelle, Compile `succeeded` | zweite Root-Quelle, `rejected`, `SOURCE_ERRORS` |

Zusätzliche Einzel logs derselben Reproduktionen: `edge-project-aliases.log`, `edge-alias-delete.log`, `edge-didopen-alias.log`.

## 4. Schliessung von F1–F5 im Einzelnen

| Finding | Anforderung | Eigene Beobachtung | Verdikt |
|---|---|---|---|
| F1 symlinkter Projektroot leer | Kap. 07 §6.3; AC-P3-05 | S1: `initialize` mit Symlink-Root; Compile `succeeded`, `modelCount=2`; offene Quelldatei über logische URI symbolisierbar | **geschlossen** |
| F2 Alias wird zweite Quelle | Kap. 07 §6.3; LSP-003/015; AC-P3-05 | S1/S2: realer Watcher-Alias und unbekannter Alias ohne Root erzeugen keine zweite Quelle; ungespeicherter Text bleibt; Löschmeldung bei vorhandener Datei entfernt nicht | **im ursprünglichen Watcher-/bekannten-Alias-Umfang geschlossen**; F6 bleibt als Erweiterung offen |
| F3 Ressourcenlimit als intern | Kap. 07 §2.5/§6.3; LSP-016; AC-P3-05/08 | S5: Budget `-32800`/`RESOURCE_LIMIT`; Projektlimit `-32803`/`SOURCE_LIMIT_EXCEEDED`; `initializationOptions` vor Discovery; keine Stacktraces | **geschlossen** |
| F4 FailureId verloren | Kap. 09 DIAG-007; LSP-016; AC-P3-08 | F4-Probe: exakte FailureId in RPC-Antwort und Log; `data=null`; technischer Text nicht geleakt | **geschlossen** |
| F5 übergrosser Clienttext still verworfen | LSP-004; Kap. 07 §6.3; AC-P3-05 | S3/S4: `ContentModified`, `SOURCE_LIMIT_EXCEEDED`, kein Halten des Riesentexts, globaler Unsync-Schutz, vollständige Resynchronisation | **geschlossen** |

Die Fixbehauptungen des Kandidaten werden damit nicht nur gelesen, sondern über eigene Prozess-/RPC-Proben bestätigt. Die ursprünglichen Reviewerquellen wurden nicht als Ersatz verwendet.

## 5. Reproduzierbarer MUSS-Befund F6

### F6 – Physische Aliasidentität ist für Discovery und unbekannte `didOpen`-Aliasse unvollständig (MUSS)

**Anforderung:** Kapitel 07 §6.3 verlangt: „Die Dateiidentität wird auf der I/O-Seite mit realen Pfaden beziehungsweise Dateischlüsseln bestimmt. Bekanntes logisches SourceId-Mapping bleibt erhalten; die exakte offene Client-URI wird getrennt für Diagnostik, Navigation und Versionsedits benutzt. Alias-Watcher erzeugen keine zweite Compilerquelle und überschreiben keine offene Clientquelle. Ein symlinkter Workspace-Root wird über seinen realen Ordner durchsucht, mit logischen Pfaden für die aufgenommenen Dateien. Hardlink-Aliasse behalten das explizite editierbare Primärmapping. Bei Löschmeldungen wird der aktuelle Zustand über den I/O-Adapter geprüft; das Entfernen eines Alias allein entfernt keine weiterhin vorhandene Primärdatei.“ AC-P3-05 (geschlossene Projektquellen, URI-Identität, Watcher) und LSP-003/015.

**Reproduktion F6a – Hardlink im Projekt:**

1. Projektordner mit `Base.ili` (`MODEL Base`), `Root.ili` (`IMPORTS Base`) und `HardBase.ili` als Hardlink auf `Base.ili`.
2. `initialize` mit diesem Ordner.
3. `workspace/executeCommand interlis.compile`.

Beobachtung: `rejected`, `reasons=[SOURCE_ERRORS, AMBIGUOUS_INPUT]`, drei Diagnosen; nicht `succeeded`/`modelCount=2`. Die beiden Hardlink-Pfade werden als zwei Quellen mit demselben Modell installiert.

**Reproduktion F6b – Löschen eines In-Projekt-Symlink-Alias:**

1. Projektordner mit `Base.ili`, `Root.ili` und `AliasBase.ili` als Symlink auf `Base.ili`. `AliasBase.ili` sortiert vor `Base.ili`.
2. `initialize`; `interlis.compile` -> `succeeded`, `modelCount=2`.
3. `AliasBase.ili` löschen; `Base.ili` bleibt vorhanden.
4. `workspace/didChangeWatchedFiles` mit `Deleted` für `AliasBase.ili`; danach `interlis.compile`.

Beobachtung:

```text
vor Löschen:  {"outcome":"succeeded","modelCount":2}
nach Löschen: {"outcome":"rejected","reasons":["SOURCE_ERRORS","MISSING_INPUT"],
               "diagnostics":["diag-...","diag-..."]}
```

Ein anschliessender `Changed`-Watcher auf die weiterhin vorhandene `Base.ili` stellt die Quelle in derselben Sitzung nicht wieder her; Compile bleibt `rejected[MISSING_INPUT]`. Die Primärdatei existiert, ist aber aus dem Compilerbild verschwunden.

**Reproduktion F6c – `didOpen` über unbekannten Symlink-Alias:**

1. Kein Workspace-Root; `Base.ili` und `Root.ili` anlegen; `AliasRoot.ili` als Symlink auf `Root.ili`.
2. `didOpen` für `Base.ili`, `Root.ili` und anschliessend `AliasRoot.ili` mit demselben `Root`-Text.
3. `interlis.compile`.

Beobachtung: `rejected`, `reasons=[SOURCE_ERRORS]`; die Alias-URI hat eine zweite Root-Quelle erzeugt statt die bestehende physische SourceId wiederzuverwenden.

**Wirkung:** Ein Hardlink oder ein sortierungsabhängiger Symlink im Projekt kann den gesamten Compile in `AMBIGUOUS_INPUT` treiben. Das Löschen eines Alias entfernt eine weiterhin vorhandene Primärquelle; abhängige Analyse, Referenzsuche und Rename-Grundlage sehen danach `MISSING_INPUT`. Ein `didOpen` über einen unbekannten physischen Alias erzeugt eine zweite Modellquelle. Das verletzt die ausdrückliche Alias-/Primärmapping- und Löschregel sowie die verlangte Dateiidentität über reale Pfade/Dateischlüssel.

**Ursache/Datei:**

- `ProjectSourceLoader.scan` (`interlis-lsp/src/main/java/guru/interlis/lsp/ProjectSourceLoader.java:40-47`) sammelt alle `.ili`-Pfade und dedupliziert nicht über `fileKey`. `read` ermittelt zwar den `fileKey` (`:64-70`), aber die Discovery nutzt ihn nicht zur Identitätszuordnung.
- `InterlisTextDocumentService.rememberFile` (`:230-237`) bildet nur `path` und `realPath` auf eine URI ab. Bei Hardlinks ist `toRealPath()` der jeweilige Linkpfad, nicht der gemeinsame Inode; bei einem sortiert ersten Symlink wird `realPath` auf die Alias-URI abgebildet und die Primärdatei später als Duplikat übersprungen.
- `installProject` (`:282-290`) erzeugt für jeden nicht deduplizierten Pfad eine eigene `SourceUpdate`/`SourceId`.
- `InterlisWorkspaceService.reloadFile` (`:103-121`) kanonisiert die Watcher-URI und liest anschliessend `files.read(uri, …)` über die kanonische Alias-URI statt über den angefragten, weiterhin existierenden Pfad. Nach dem Löschen des Alias liest der Watcher auf `Base.ili` daher den gelöschten Alias-Pfad.
- `InterlisTextDocumentService.didOpenNow` (`:323-335`) verwendet `canonicalUriNow` (`:227-229`), das nur die bereits gefüllte `fileIdentities`-Map nachschlägt. Für einen unbekannten Symlink-/Hardlink-Alias erfolgt keine `realPath`-/`fileKey`-Auflösung; die vorhandene Identitätsprüfung `openSource(...)` greift deshalb nicht.
- `forgetFileAlias` (`:215-220`) entfernt nur den Alias-Pfad, nicht die auf den Alias zeigende Primärzuordnung; es gibt keine Neuelection eines überlebenden Pfads vor `deleteExternalNow`.

**Gegenempfehlung:**

1. Discovery über `fileKey` (und `realPath`) deduplizieren. Für jeden Inode genau eine primäre logische SourceId wählen, vorzugsweise einen nicht-symlinkten Pfad innerhalb des realen Roots; alle weiteren Symlink-/Hardlink-Pfade in `fileIdentities` auf diese SourceId abbilden.
2. `rememberFile` um den `fileKey` erweitern und vorhandene Identitäten darüber finden, nicht nur über `realPath`.
3. `reloadFile` für die I/O-Seite den angefragten Pfad (oder einen nachweislich noch existierenden Primärpfad) verwenden; bei einer Löschmeldung zuerst prüfen, ob ein anderer Alias/Primärpfad denselben `fileKey` noch liefert, und erst dann die Quelle entfernen. Die Quelle darf erst verschwinden, wenn kein überlebender Pfad mehr existiert.
4. `didOpen` für unbekannte Datei-URIs über einen begrenzten I/O-Schritt auf `realPath`/`fileKey` abbilden und bei bereits bekannter offener Quelle entweder dieselbe SourceId verwenden oder den zweiten Open sicher mit einer Betriebsmeldung ablehnen. Keine synchrone unbegrenzte I/O auf der Ownerqueue.
5. Prozess-/Vertragstests ergänzen: (a) Hardlink im Projekt -> `succeeded`, keine `AMBIGUOUS_INPUT`; (b) Symlink-Alias sortiert vor Primärdatei, Alias löschen -> Primärquelle bleibt und Compile bleibt `succeeded`; (c) `didOpen` über Symlink-/Hardlink-Alias einer bereits offenen Quelle -> keine zweite Modellquelle.

**Rohpfade:** `build/review/P3-2-r2/finding-f6.log`, `build/review/P3-2-r2/edge-project-aliases.log`, `build/review/P3-2-r2/edge-alias-delete.log`, `build/review/P3-2-r2/edge-didopen-alias.log`, Quelle `build/review/P3-2-r2/probe/finding-f6.mjs`.

## 6. Kriterienmatrix AC-P3-05 bis AC-P3-08

### AC-P3-05 – Dokumentnotifications, neueste Generation, Abbruch, bedingte Annahme, Quellen/Watcher

| Anforderung | Beobachtung | Verdikt |
|---|---|---|
| INC-018/022 – veraltete Ergebnisse, Capture/paralleles Update, bedingte Annahme | `P3AnalysisSchedulingTest`, 20-Edit-Probe, Compile-Abbruch: blockierte Engine hält Notifications nicht auf; alte Antwort wird verworfen; `acceptAnalysis`/`STALE_NO_COMMIT` in der Workspace-Suite grün | PASS im Kern |
| INC-019 – Konfigurations-/Resolverwechsel als Workspace-Änderung | `configurationChanged` erhöht Revision und rescheduled; `P3PresentationConfigurationTest` und S5 zeigen echte Budgetwirkung und Wiederherstellung | PASS |
| LSP-003 – Watcher überschreiben offenen Clienttext nicht | S1/S2: gleiche URI und realer Alias; ungespeicherter Text bleibt; Diagnostik wird aktualisiert | PASS im ursprünglichen Umfang |
| LSP-004 – atomare mehrteilige `didChange` | `P3DocumentSynchronizationTest` und Hauptprobe: abhängige Deltas, ungültiger Batch, Vollersatz, Resync | PASS |
| LSP-006 – leere Diagnostik ersetzt frühere | `P3ProjectRenameTest` und Hauptprobe: leere Liste nach Korrektur; No-op-Version exakt | PASS |
| LSP-011 – Abbruch in Engine/Worker, Sitzung danach nutzbar | `LspProtocolCancellationTest`, Hauptprobe: kontrollierte Antwort, Sitzung nutzbar | PASS |
| LSP-015 – Repositoryänderung aktualisiert abhängige offene Analyse | S1: reale Dateiänderung über realen Alias aktualisiert Diagnostik, ohne Clienttext zu überschreiben | PASS im ursprünglichen Umfang |
| Dateiidentität/geschlossene Quellen/Aliaslöschung | F6a/b/c: Hardlink-Discovery, Aliaslöschung, unbekanntes `didOpen` | **FAIL (F6)** |
| Bedingte Cacheannahme / neueste Generation | `P3AnalysisSchedulingTest`, 20-Edit-Probe: finaler Stand und Version 21 korrekt | PASS |

**AC-P3-05: FAIL** wegen F6. F1/F2/F5 im ursprünglichen Umfang sind geschlossen.

### AC-P3-06 – Workspace-weites Rename (LSP-012)

| Prüfung | Beobachtung | Verdikt |
|---|---|---|
| Mehrdatei-Rename offen/geschlossen | `P3ProjectRenameTest` und S1/S2: `Base.ili` und ungespeichertes `Root.ili` gemeinsam; keine Datei geschrieben | PASS |
| `documentChanges`/Clientversionen | offene Quelle exakte Version, geschlossene `null`; ohne Capability `changes` | PASS |
| Diskhash/Pfadziel/Schreibrecht | unangekündigte Diskänderung -> `ContentModified`; Schreibschutz -> kontrollierter Fehler; keine Datei verändert | PASS |
| Kollision/Originalsegmente/Revisionen | `P3CompletionScopeContractTest`; Revision fehlt -> `CONTENT_MODIFIED`; Originalsegmentprüfung im Plan | PASS |
| Fehlende/unvollständige Quelle | `LspFeatureTest`, `P3CompletionScopeContractTest`: unvollständige Suche/fehlende Quelle -> kein vollständiger Plan | PASS |

**AC-P3-06: PASS** für die direkt geprüften Rename-Verträge. F6 bleibt eine vorgelagerte Quellidentitätsstörung in AC-P3-05; sie ist kein Rename-Vertragsfehler im engeren Sinn.

### AC-P3-07 – gemeinsame Completion/Tokens/Folding/Formatierung/Diagrammanker

| Prüfung | Beobachtung | Verdikt |
|---|---|---|
| Scope, Import, Vererbung | S6: `inherited` in `Child`, `inh` lokal; am Dokumentanfang nicht sichtbar; `P3CompletionScopeContractTest` | PASS |
| Qualifizierer/Member | S6: `Base.` -> `Base.Common`; keine Fremdmitglieder; `membersOf`/`visibleSymbols` | PASS |
| Sprachversion/Keywords | `P3CompletionScopeContractTest`: `MULTICOORD` nur 2.4; Completion nutzt Sprachversion | PASS |
| Ersetzungsbereich/InsertText/Incomplete | `P3CompletionProtocolTest`, S6: ganzer Name, 100-Trunkierung, `isIncomplete`; Scope ohne Kandidaten `incomplete` | PASS |
| Gebundene Tokens/Zeilen | S6: `inherited` property, Kommentar comment, zeilenbegrenzte UTF-16-Spannen | PASS |
| Folding | S6: mehrzeiliger Kommentar; Deklarationsbereiche in `P3PresentationConfigurationTest` | PASS |
| Formatierung | S6: `tabSize=3`; ungültige Quelle Fehler; `P3PresentationConfigurationTest` | PASS |
| Hover/Diagrammanker | S6: Hover `plaintext`; Diagramm-SnapshotId; veralteter Snapshot `InvalidParams`; `LspProcessTest` | PASS |

**AC-P3-07: PASS.**

### AC-P3-08 – Lifecycle, stdio, Fehlerarten

| Anforderung | Beobachtung | Verdikt |
|---|---|---|
| LSP-001 – dünner Adapter, keine zweite Binder-/Typsemantik | LSP-Modul nutzt Workspace/LanguageQueries; kein ANTLR/Binder/GraphBuilder im LSP-Modul; Inspektion | PASS |
| LSP-002 – `initialize`/`initialized`/`shutdown`/`exit`/Dokumentlebenszyklus | `LspProcessTest`, S6: Pre-Init, Init, doppeltes Init, Shutdown, Exit 0/1, Dokumentlebenszyklus | PASS |
| LSP-008 – stdio, stdout nur Protokoll | `LspProcessTest`, alle eigenen stdio-Proben: Frame-Parsing; Diagnostik versioniert; stderr nicht Protokoll; keine Stacktraces | PASS |
| LSP-009 – keine globalen Compile-Locks/Listener | Inspektion der Scheduler-Executors; zwei unabhängige Serverinstanzen in Tests | PASS |
| LSP-016 – Transport-/Compiler-/interne Fehler unterscheidbar | F3: `RequestCancelled`/`RESOURCE_LIMIT`, `RequestFailed`/`SOURCE_LIMIT_EXCEEDED`; F4: FailureId; ungültige Dokumente/Parameter `InvalidParams`; `data=null` | PASS |

**AC-P3-08: PASS.** F3/F4 sind geschlossen; F6 ist eine Quellidentitätsfrage, keine Fehlerartenverwechslung.

### Kumulative P3-1-Regression

| Prüfung | Beobachtung | Verdikt |
|---|---|---|
| `check` | Exit 0; 214 Suites/1136 Tests; 0 Failures/Errors/Skips; 759 Compiler-/42 LSP-/237 CLI-Tests; Evidenz 145 | PASS (lokal) |
| `checkPortable` | Exit 0; JS und WasmGC je 3548, Status `passed` | PASS (lokal) |
| `sdkProcessCheck` | Exit 0; 9 Kommandos | PASS (lokal) |
| `conformanceCheck` | Exit 0; normative Auswertung 577/16/0; roher Lauf 577/1/15 | PASS (normativ); Rohlauf FAIL |
| P3-1-CLI-/Formatter-Verträge | durch P3-2-Änderungen nicht verändert; der P3-1-PASS bleibt gültig | PASS |
| Externe Plattformmatrix | Linux x64 PASS; Windows x64 FAIL (siehe §7) | **nicht bestanden** |

**Kumulativ P3-1 (lokaler JVM-/Portable-/SDK-/Conformance-Umfang): PASS.** Der plattformübergreifende Nachweis ist am Kandidaten nicht erbracht.

## 7. Externe Plattformmatrix am Kandidaten

Die neue GitHub-Actions-Matrix am exakten Kandidaten ist **nicht mehr laufend**, sondern abgeschlossen:

- Lauf `41148744929`, Workflow „P3 platform contracts“, `head_sha=1c19acbbe021f56192f7dbbc6173aaf4cecbfeeb`
- Linux x64 Job `124379927101`: **success**
- Windows x64 Job `124379926853`: **failure**, Schritt 6 „Verify JVM, CLI and installed SDK processes“ (Exit 1); die übrigen Schritte einschliesslich Artefakt-Upload waren erfolgreich
- Die Workflow-Definition ruft auf Windows `gradlew.bat --no-daemon check legacyCliCheck sdkProcessCheck sdkPackage :interlis-lsp:installDist` auf.
- Die Job-Logs und Artefakte sind ohne authentifizierten GitHub-Zugriff nicht abrufbar (`403 Must have admin rights` bzw. `401 Requires authentication`). Die konkrete Fehlerursache ist daher **nicht** festgestellt; die neuen Symlink-/Hardlink-Tests sind ein naheliegender Prüfpunkt, aber nicht belegt.

Wirkung: Der Windows-x64-Nachweis am Korrekturkandidaten ist offen/fehlgeschlagen. Das ist kein AC-P3-05–08-Finding im engeren JVM-LSP-Sinn, aber ein offener P3-5/G3-Blocker und ein Regressionssignal gegenüber der ersten Matrix (`466790c`, beide Plattformen PASS). Es wird hier nicht als künftiger Erfolg oder als „laufend“ dargestellt.

## 8. Weitere Beobachtungen und Grenzen

**H1 – Symlinktes Unterverzeichnis wird nicht traversiert, wenn es der einzige Pfad ist.** Ein Workspace-Root mit ausschliesslich einem symlinkten Unterordner (`linkdir -> outside` mit `Base.ili`/`Root.ili`) wird als Projekt ohne Quellen aufgenommen; Compile meldet „workspace has no sources“. Die normative §6.3-Ergänzung nennt ausdrücklich den symlinkten **Root**, nicht Unterordner; die F1-Gegenempfehlung des Erstberichts sprach breiter von Roots und Unterordnern. Ich werte dies deshalb nicht als zusätzliches MUSS, empfehle aber `FOLLOW_LINKS` mit Zykluserkennung oder eine sichere Meldung, wenn Unterordner-Symlinks vorhanden sind. Rohlog: `edge-project-aliases.log` (`symlinked-subdir-only`).

**H2 – `didOpen` über einen unbekannten Alias ist nicht nur ein Projektproblem.** F6c zeigt denselben Identitätsbruch ohne Workspace-Root. Die vorhandene `openSource`-Sperre greift nur, wenn die SourceId bereits kanonisch aufgelöst wurde. Das ist Teil von F6, nicht eine separate Anforderung.

**H3 – Roher Conformance-Lauf bleibt FAIL.** Die 15 rohen Fehler sind die freigegebenen normativen Ausnahmen; `conformanceAssessment` prüft sie mit positiven Kontrollen und meldet 577/16/0. Kein neuer Produktbefund, aber der rohe Lauf darf nicht als PASS bezeichnet werden.

**H4 – Keine Browser-/Safari-/WebImage-Prüfung in diesem Review.** Für den JVM-LSP nicht erforderlich; die Elternnachweise unter `build/reports/p3/P3-2/` werden nicht als eigene Prüfung übernommen.

## 9. Quellbindung, Pins und Statuswahrheit

- `docs/p3/STATUS.md` bezeichnet P3-2 korrekt als „Review offen“ und G3 als offen; dieser Bericht ändert den Status nicht.
- `docs/p3/implementation/P3-2.md` nennt 42 LSP-Tests, 744 Compiler-Tests, 145 Evidenzfälle, SDK 9 und `installDist`. Die tatsächlichen Zähler dieses Laufs stimmen damit überein (Compiler 759 inklusive 15 G2-Assertions).
- Die aktiven G2-Quellpins, Methoden-/Range-/Zeilenlocator in `docs/evidence/stabilization/` und `testdata/g2/` sind mechanisch nachgeführt (Hashes, Offsets, Methodenbereiche). `docs/g2/` und `docs/evidence/p3/` sind zwischen `fbcbc76` und dem Kandidaten unverändert; die G2-Norm-/Kompatibilitätsentscheidungen und historischen Erwartungen bleiben erhalten.
- Die Kapitel-07-Ergänzung des Kandidaten ist die normative Grundlage für F1–F5 und F6; sie wurde nicht abgeschwächt. Insbesondere bleiben „Alias-Watcher erzeugen keine zweite Compilerquelle“, „Hardlink-Aliasse behalten das explizite editierbare Primärmapping“ und „das Entfernen eines Alias allein entfernt keine weiterhin vorhandene Primärdatei“ bestehen.

## 10. Nicht ausgeführt / Grenzen

- Kein `checkBrowser`, kein `releaseCheck`, kein `performanceCheck`, kein `sdkPackage`.
- Kein Chromium-/Firefox-/WebKit-/Safari- und kein WebImage-Lauf; `interlis-wasm-teavm/node_modules` wurde nicht mit `npm ci` aufgebaut. Für den JVM-LSP ist das nicht erforderlich.
- Keine lokale Linux-x64-/Windows-x64-Ausführung. Die externe Matrix wurde nur über die öffentliche GitHub-API ausgewertet; Windows-Logs/Artefakte waren ohne Authentifizierung nicht zugreifbar.
- `check` ist ein Aggregat der vorhandenen XML-Resultate; einzelne Tasks waren `UP-TO-DATE`. Die Zähler sind keine Behauptung, jeder Modultask sei in genau diesem Prozess neu gelaufen.
- Die Conformance-Rohausgabe bleibt `FAIL` (577/1/15); die normative Auswertung ist `PASS` (577/16/0). Die Rohausgabe wird nicht als Produkterfolg umetikettiert.
- Keine Reparatur am Kandidaten, keine Änderung versionierter Dateien, kein Commit/Push; eigene Dateien nur unter `build/review/P3-2-r2/`.
- Ein versehentlicher Shell-Aufruf von `python3` wurde mit `command not found` abgewiesen; es lief kein Python/pip/uv. Die geforderte OS-Python-Sperre blieb faktisch gewahrt.

## 11. Gesamturteil

**P3-2: FAIL.**

- AC-P3-05: **FAIL** (F6: In-Projekt-Hardlink/Symlink-Aliasidentität, Aliaslöschung, unbekanntes `didOpen`)
- AC-P3-06: **PASS** (direkte Rename-Verträge; F6 bleibt vorgelagerte AC05-Störung)
- AC-P3-07: **PASS** (Completion, Tokens, Folding, Formatierung, Hover, Diagrammanker)
- AC-P3-08: **PASS** (F3/F4 geschlossen; Lifecycle, stdio, Fehlerarten)
- Kumulativ P3-1 lokal: **PASS**; externe Plattformmatrix: **Linux PASS / Windows FAIL** -> plattformübergreifend offen
- P3-3/P3-4/P3-5/G3: **offen**

F1–F5 sind mit eigenen echten Gegenproben geschlossen. F6 ist ein reproduzierbarer MUSS-Befund, keine blosse Testnamen- oder Timingbeobachtung. Bis zur Korrektur der physischen Aliasidentität, der Aliaslöschung und der unbekannten `didOpen`-Aliasse sowie einer neuen unabhängigen Gegenprobe ist P3-2 nicht abgenommen. Ein Teil-PASS von AC-P3-06/07/08 darf nicht als P3-2- oder G3-Abschluss gelesen werden. G3 bleibt bis P3-5 offen.
