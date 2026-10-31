# Unabhängiger kumulativer P3-/G3-Gesamtreview R2 – Kandidat `4b586b1e09a2f1cbd200f7d00876e44c81df2169`

**Fachliches Urteil: PASS für AC-P3-01 bis AC-P3-20 und G0–G3.** Der vollständige
`releaseCheckP3` ist am exakten sauberen Kandidaten frisch und regulär mit
`RELEASE_CHECK_P3_EXIT=0` abgeschlossen (`BUILD SUCCESSFUL in 35m 1s`, 100
Tasks). Alle verpflichtenden funktionalen, portablen, Browser-/Safari-,
WebImage-, Conformance-, Performance-, SDK-, Reproduzierbarkeits-, Plattform-
und Nachweisgates sind grün. Die drei MUSS-Lücken aus R1 (F1–F3) sind mit den
konkreten Originalmutationen und zusätzlichen eigenen Gegenproben geschlossen:
der alte Validator akzeptiert die mutierten Belege, der aktuelle lehnt sie ab.
Es gibt keine offenen MUSS-Findings.

**Vorbehalt der formalen G3-Abnahme:** Dieser Bericht belegt den fachlichen
PASS und den regulären Produktprozessabschluss. Der äussere Transportexit und
das endgültige Manifest des geerbten OS-Schutzes werden erst nach dem
tatsächlichen OpenCode-Ende separat geprüft. Ein PASS-Bericht allein ersetzt
diesen äusseren Nachweis nicht. Der Produktcheckout war vor und nach dem
Review sauber und unverändert.

- **Kandidat:** `4b586b1e09a2f1cbd200f7d00876e44c81df2169`
  (`feat: close independent G3 evidence completeness findings`).
- **Checkout:** `/private/tmp/interlis-java-P3-5-r2`, `git status --porcelain=v1`
  leer, `git diff --check` leer, HEAD vor/nach unverändert.
- **Quellfingerprint:** `794d6e69437121e28a3705f44b869f92deee606741bd7b13c64dcb500db78c46`,
  `dirty=false`.
- **Bericht:** `/Users/stefan/sources/interlis-java/build/review/P3-5-r2/review.md`
  (dieser Text; nach dem Schreiben nicht mehr verändert).
- **Eigene Proben/Rohlogs:** `build/review-final/` im Kandidatencheckout,
  kopiert nach `/Users/stefan/sources/interlis-java/build/review-final/P3-5-r2/`.
- **Unabhängiger Kontext:** neuer OpenCode-R2-Kontext, direkte DeepSeek API,
  Provider `deepseek/deepseek-flash`; keine Native-Subagents.
- **Produktänderungen:** keine. Kein Commit, Push, Merge oder Release.

## 1. Vorbedingungen und kumulativer Umfang

Unverändert gelesen und als Vorbedingung geprüft:

| Lieferung | Unabhängiger PASS | Produkt-SHA | Bericht-SHA-256 |
|---|---|---|---|
| P3-1 | `docs/p3/reviews/P3-1-review.md` | `9108b0387d9f1b82f506d8dc64e301e8522137dd` | `9133ef5e8499f67f966e593be9b968f59510e2d0ee0a18a61fff8fcf608a5db7` |
| P3-2 | `docs/p3/reviews/P3-2-review.md` | `5eac6b64fb352982376f054a765597688d5f975b` | `12249d8c9120eb108fadebcb1850e8c377ca41e289423cf3fecd904c523b0c1d` |
| P3-3 | `docs/p3/reviews/P3-3-review.md` | `253d9ca837d05cc86959caf04fb2f4bab34c74c0` | `7b57aeaf6ffefde51e3f23be5a1236148c9dc8c605b218e679f0a7882ef188da` |
| P3-4 | `docs/p3/reviews/P3-4-review.md` | `280589065a7900e6b3bb2a338f2cd012865091e1` | `f5cb2399cab9a595ecf9ac637749a2319e6bfff05b5c66c56037225ac3cfdc65` |

Der R1-Bericht `docs/p3/reviews/P3-5-f341d7b-FAIL.md` wurde unverändert gelesen,
SHA-256 `7fb05c73e70b011fd3dc35a4813a84d0f698b7673f7e11fd2417f89c8d70a697`.
Sein Kandidat `f341d7bd3baf8e137985723b6216c30d6580384b` und alle dortigen
Läufe sind ausschliesslich Eltern-/Vorgängerevidenz. Sie ersetzen keinen
aktuellen G3-Nachweis.

Der Diff `2805890..4b586b1` enthält **keine** Änderung in
`interlis-compiler/src/main`, `interlis-model/src/main`,
`interlis-workspace/src/main`, `interlis-projection/src/main` oder den
Produktionsquellen von CLI/LSP/Wasmgrenze. Er enthält die P3-5-Verifikations-
und Releaseharnesses, das Browsermanifest, die TeaVM-Toolkorrektur und die
Dokumentation. Damit bleiben Compiler-/Modell-/Workspace-/Projektionssemantik,
die bestätigten Normprofile und die fachlichen Erwartungen unverändert.

Eingefrorene Corpus-/Pin-Dateien sind bytegleich:

| Datei | SHA-256 |
|---|---|
| `docs/evidence/p3/full-corpus/manifest.json` | `5d8a0fd384142d00cfb858307ff7a9dd74b0d0833f350a4015fa27d691001e0a` |
| `docs/evidence/p3/full-corpus/sources.zip` | `b5f0057cefbfed24312213657ee6043553bf3815ddac5127651c668a610c64c4` |
| `docs/evidence/p3/full-corpus/assessment.json` | `3dc2250a759027b1f7ec7de5ddba01bcfb8294098a5ecc23fadd5aaf6baead9f` |
| `docs/evidence/p3/full-corpus/budgets.json` | `e9f02473567dc446d51c18a3fa607e38a45b9b7183c099d9890a07b8b8804a4e` |

Die Nutzerprofile in `docs/g2/COMPILER_NORM_DECISIONS.md` bleiben verbindlich.
P4/P5, Generatoren/IlisMeta, Legacy-Java-API, C/C++/iox und der historische
13er-Corpus sind ausserhalb dieses P3-Auftrags.

## 2. Umgebung und tatsächlich verwendete Werkzeuge

| Bestandteil | Tatsächlich verwendet |
|---|---|
| Hardware | MacBookAir10,1, Apple M1, 8 logische CPUs, 16 GiB (17179869184 Bytes) |
| OS | macOS 15.8.1, Darwin 24.6.0, arm64 |
| Java | Temurin 25.0.3+9 |
| Node/npm | Node v24.22.0, npm 11.x; `npm ci` im TeaVM-Modul: 14 Pakete, Lockfile unverändert |
| Gradle | Wrapper 9.8.1, `--no-daemon`, Java-25-Launcher |
| GraalVM/Binaryen | Oracle GraalVM 25.0.3+9.1, Binaryen version_133 |
| Browser | Playwright 1.64.1: Chromium 156.0.8078.4 (rev 1248), Firefox 157.0 (rev 1555), WebKit 27.2 (rev 2370); echtes Safari 27.0 über `safaridriver` |
| Referenzcompiler | ili2c 5.6.9-bf16026e83f2377153b2e818a4f3838f0a423722, JAR-SHA-256 `37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e` |
| GitHub | `gh` 2.102.0, read-only `gh api`; Lauf `41214503758` |

Temporäres `caffeinate -diu` war aktiv (PID 39668). Es wurden keine globalen
Energieeinstellungen verändert. Der ererbte OS-Ausführungsschutz war während
des gesamten Reviewprozesses aktiv (`build/reports/python-free/environment.json`,
`guardPid=39705`, `status=running`, `gitHead=4b586b1…`, `dirty=''`,
Proben `ENOENT` für nicht auflösbare Namen und `EPERM` für absolute
Interpreterpfade).

## 3. Abgebrochener Vorlauf und neuer vollständiger Gesamtlauf

Der erste Aufruf in diesem R2-Kontext wurde mit einer Zwischenmeldung beendet;
dabei wurde der private Standalone-Server beendet und der damalige
Hintergrund-Gate-Lauf abgebrochen. Das alte Log endete ohne regulären Abschluss
bei `:conformanceCheck`. Es wurde unverändert als
`build/review-final/logs/releaseCheckP3-interrupted.log` erhalten
(SHA-256 `cdac20e3e2f0d36147965f75d21a9d4bb2bcaa3c457414423cb7ff6f9b294d75`).
Dies ist eine unvollständige Ausführung, kein Produktbefund und keine Abnahme.

Der vollständige Lauf wurde am unveränderten Kandidaten neu gestartet:

```bash
cd /private/tmp/interlis-java-P3-5-r2
./gradlew --no-daemon --console=plain releaseCheckP3 \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node
```

Ergebnis:

- `BUILD SUCCESSFUL in 35m 1s`, `RELEASE_CHECK_P3_EXIT=0`,
  100 actionable tasks: **42 executed, 58 up-to-date**. Die UP-TO-DATE-
  Eingänge stammen aus dem abgebrochenen Lauf desselben Kandidaten mit
  unveränderten Quellen und wurden als solche deklariert; alle fälligen Gates
  schlossen im neuen Lauf regulär erfolgreich ab, keine Testfälle wurden
  übersprungen. Die drei plugin-seitigen TeaVM-Hilfstasks
  (`emscriptenStubWasmGC`, `emscriptenWasmGC`, `disasmWasmGC`) sind keine
  P3-Gates.
- `P3_RELEASE_EVIDENCE_PASS 4b586b1e09a2f1cbd200f7d00876e44c81df2169`.
- Release-Ergebnis `build/reports/p3/release/result.json`, SHA-256
  `3e37fa076f91541604f9661c41cf1e1a05590bd2b297170b58bc280250442ade`,
  `status=passed`, `profile=P3`, `sourceFingerprint=794d6e69…`.
- Rohlog `build/review-final/logs/releaseCheckP3.log`, SHA-256
  `827034adca66ae88ccbaed8f8eeb73b8c475cc69c233e915583cb98948fd9fd2`.
- Lokales Plattformresultat `build/reports/p3/platform/result.json`, SHA-256
  `42f572b7ec81686a458a0b1e5c4fc8d323d571bebc2e76f4cd0930deeffabac8`.

Nach den negativen Releasekonfigurationsproben und den Inventurproben wurde der
echte, aktuelle Nachweis erneut aus den vorhandenen vollständigen Dateien
abgeleitet:

```bash
node tools/release/evidence.cjs
# P3_RELEASE_EVIDENCE_PASS 4b586b1e09a2f1cbd200f7d00876e44c81df2169
```

Das Ergebnis blieb bytegleich (`3e37fa07…`). Es wurde kein PASS-Bericht
synthetisch zurückgeschrieben und kein Rohbericht umetikettiert.

## 4. Aktueller CI-Collector und Plattformmatrix

`tools/release/platform-evidence.cjs` wurde mit Node 24 und
`GH_EXECUTABLE=/Users/stefan/apps/gh/bin/gh` ausgeführt; nach dem Gesamtlauf
wurde derselbe Collector erneut ausgeführt. Beide Läufe endeten mit
`GITHUB_PLATFORM_EVIDENCE_PASS 41214503758 4b586b1…`, Exit 0. Das Ergebnis
`build/reports/p3/platform-evidence/result.json` ist bytegleich
(SHA-256 `eb1b485a48ad513ffcd8da7e04257b0be01bfd442e11d451e29e6d3bada64a22`).
Der Collector prüft ausschliesslich read-only GitHub-Daten und die
autoritativen Archivdigesten; es gab keine Nachricht und keine Veröffentlichung.

| Plattform | Job | Job-ID | Archivdigest | Bericht-SHA-256 | Tests/Suites | SDK/Legacy | SDK-ZIP |
|---|---|---|---:|---|---:|---:|---|
| Linux x64 | `completed/success` | `124576089716` | `sha256:1f33b7d0e29e17be6fd4bdd6e36800b9b2202c4533bb2d647b5162df6653cf1b` | `bbf7a5e14c399d71767b3b1df3c6912827afc001dafc5b58a8c95f759d61e006` | 1186/223 | 9/54 | `5a607b4e…` |
| Windows x64 | `completed/success` | `124576089556` | `sha256:08bccfc46048145baebce3c9749f626fc4d4d77b8d8fcd9c9c9b12ea696dad44` | `af7d74e55b53b767bbd0b8f18e82be3a071deb3c5ae6e763f280e38489258070` | 1186/223 | 9/54 | `5a607b4e…` |
| macOS arm64 | lokal | – | – | `42f572b7…` | 1186/223 | 9/54 | `5a607b4e…` |

Lauf `41214503758` ist `completed/success`, `head_sha=4b586b1…`,
`path=.github/workflows/p3-platforms.yml`, `attempt=1`. Beide tatsächlichen
Jobs sind `completed/success`; die eingebetteten Plattformberichte sind an
denselben Commit und denselben Quellfingerprint gebunden.

## 5. Kriterienurteil AC-P3-01 bis AC-P3-20

| Kriterium | Urteil | Wesentliche aktuelle Beobachtung |
|---|---|---|
| AC-P3-01 – CLI-001–004, COMPAT-001–003, LOG-005 | **PASS** | Eigene 43/43-CLI-/Formatproben; ili2c-5.6.9-Profil, getrennte Streams/Exits, 32 Generator-/Hostoptionen abgelehnt; Referenzprofil getrennt |
| AC-P3-02 – CLI-005–007, DIAG/LOG | **PASS** | Eigene Präzedenz-/Herkunfts-/Redaktionsprobe (CLI > Datei > Default), Duplikatfehler, IO-/Schreib-/Flushfehler ohne Erfolg; 1186 JUnit inkl. Lifecycle-/Prioritätstests |
| AC-P3-03/04 – FMT-001–004 | **PASS** | Öffentliche Formatierung idempotent, semantikerhaltend, Re-Parse/Compile vor/nach gleich; `--check` schreibt nicht; echter Schreibfehler lässt Ziel unverändert |
| AC-P3-05 – INC-018/019/022, LSP-003/004/006/011/015 | **PASS** | Eigene echte stdio-Probe 22/22: geordnete Änderungen, neuester Text, Abbruch `-32800`, geschlossene Quelle, Hard-/Symlink-Alias, Resync nach kanonischer Quelle |
| AC-P3-06 – LSP-012 | **PASS** | Mehrdatei-Rename mit 2 Dateien/2 Edits; Kollisions-/Versions-/Stale-Tests im Gate; Rename auf geschlossener Quelle liefert keinen falschen Erfolg |
| AC-P3-07 – LSP-007/010/013/014 | **PASS** | Completion, Symbole, Tokens, Folding, Formatierung, Hover/Definition und Diagrammanker aus demselben Snapshot |
| AC-P3-08 – LSP-001/002/008/009/016 | **PASS** | Echter stdio-Prozess, 13 Capabilities, reiner Protokollstdout, Shutdown/Exit 0 |
| AC-P3-09 – WASM-009–011/017 | **PASS** | Eigene 31/31-Clientprobe: Restart mit 3 Quellen und vollständigem Konfigurationsstand, neue Handles, alte Session-/Snapshot-Handles typisiert abgelehnt |
| AC-P3-10 – WASM-008/015, INC-012/018 | **PASS** | Begrenzte Snapshots (4), 996 Freigaben, 1000 alte Revisionen abgelehnt, 0 alte Handles akzeptiert; Release nach Rejection/Benchmark; Abbruch/Erholung |
| AC-P3-11 – VIZ-001–018, WASM-014 | **PASS** | Explizite Optionen, leere Auswahl `INVALID_REQUEST`, `maxNodes`/`maxMembersPerNode` `LIMITED`, `showTypes=false`, TRANSLATED-IDs/Anker stabil, NEIGHBOURHOOD 0/1, Tiefe 33 abgelehnt |
| AC-P3-12 – TEST-007/010, RES | **PASS** | 347 Wurzeln/366 Dateien/9 Windows-1252/357 UTF-8; 262/85, 11 Gruppen, 100 Zuordnungen, 821 Diagnosen; ili2c 347/347 |
| AC-P3-13 – NFR-004, INC-009/014/017 | **PASS** | 2×1000 echte Edits auf 247130 UTF-16; JVM-p95 132.822 ms, Wasm-p95 865.384 ms; Retention 4, 996 Freigaben |
| AC-P3-14 – TEST-008, WASM-006/018 | **PASS** | 347 auf JVM, Node-WasmGC, Chromium, Firefox, WebKit und echtem Safari; je 3548+24; 0 Skips/LIMITED/Semantikabweichungen |
| AC-P3-15 – NFR-005, WASM-011/015 | **PASS** | JVM-Abbruch nach 1001 Checkpoints, Erholung; Worker-Abbruch, RuntimeFailure, Dispose in Client/Browsern |
| AC-P3-16 – LOG-009/010 | **PASS** | 1000 Läufe, Queue 32, 32 retained, 10000 offered, 9968 exakt verworfen, zweite Instanz unbeeinflusst |
| AC-P3-17 – SDK-001–003, API-001–006, BUILD-006/009 | **PASS** | SDK-Consumer ausschliesslich aus entpacktem ZIP in Pfad mit Leerzeichen, `javac`/Run/Launcher, Versionen gleich CLI, keine Test-/Gson-/TeaVM-/LSP-Leaks |
| AC-P3-18 – BUILD-008, CLI-004, TEST-008 | **PASS** | Kein/unknown Releaseprofil und fehlender CI-Nachweis scheitern konkret; Plattformprozesse 1186/223 je Ziel |
| AC-P3-19 – BUILD-001–008, TEST-001–013 | **PASS** | Gradle-only; zwei saubere Erzeugungen 84/84 bytegleich, inkl. SDK-ZIP, TeaVM-JS/Wasm/Runtime und 38 Browserressourcen |
| AC-P3-20 – kumulativ AC-P3-01–19 und G0–G3 | **PASS** | Alle Einzelgates grün; `releaseEvidenceCheck` lehnt fehlende, alte, partielle, fremde, übersprungene und fehlerhafte Belege ab (F1–F3 geschlossen) |

## 6. P3-1 – CLI und Formatierung

Eigene Probe `build/review-final/logs/cli-format-review.log`, **43/43**:

- `bin/interlis-cli` und `bin/ili2c`; `version --json` mit API `0.2`,
  Protokoll `interlis.compiler/1.0`, Diagnose `interlis-diagnostics/v1`,
  Format `1.0`, Diagrammschema `1.0`; identisch mit
  `build/reports/sdk/version-manifest.json`.
- Gültiger Compile Exit 0 mit leerem stdout; Syntaxfehler Exit 1 mit
  `ILI-`-Diagnostik und leerem stdout; fehlende Datei Exit 1 mit typisiertem
  Fehler ohne Stacktrace; unbekannte Option Exit 2; `-o1` über `bin/ili2c`
  Exit 1 «unsupported option».
- Leerer Aufruf: Headless-Nutzungsfehler Exit 1, wie im Profil dokumentiert.
- `--explain-config`: `--root` Herkunft `CLI`, `--warnings` `CLI`,
  `--offline` `CONFIGURATION_FILE`; URI-Zugangsdaten und Query-Geheimnisse
  redigiert; doppelter Konfigurationsschlüssel Exit 2.
- Formatierung: `--check` Exit 1 ohne Schreiben; `--write` mit denselben
  Optionen idempotent; stdout gleich Datei; Re-Parse/Compile vor/nach
  semantisch gleich; Syntaxfehler und echter Schreibfehler (`ulimit -f 1` bei
  einer >512-Byte-Ausgabe) Exit 1 mit unveränderter Zieldatei und ohne
  Temp-Reste.
- Gate-seitig: 54 echte Legacy-Ersatzprozesse, 44 inventarisierte
  Optionsschreibweisen, 32 ausdrücklich abgelehnte Optionen, Exit 0
  (`build/reports/p3/legacy/cli-comparison.json`, SHA-256 `1502574d…`).

## 7. P3-2 – LSP und Sprachabfragen

Eigene echte stdio-Prozessprobe `build/review-final/logs/lsp-stdio-review.log`,
**22/22** gegen `interlis-lsp/build/install/interlis-lsp/lib/*`:

- Initialize mit 13 Capabilities; zwei Dateien geöffnet, vier `didChange`-
  Versionen in schneller Folge; `documentSymbol` sieht den neuesten Text.
- Compile succeeded; laufender Compile über 5000 Klassen wird mit
  `$/cancelRequest` als `-32800` abgebrochen; Sitzung bleibt nutzbar.
- `didClose` der importierten Base: Definition bleibt aufgelöst; Rename auf
  der geschlossenen Quelle liefert `-32602`, keinen falschen Erfolg.
- Hardlink und Symlink geöffnet; Compile `succeeded` (3 Modelle); nach
  Schliessen der Aliasse und Änderung der kanonischen Base auf ein
  unauflösbares `DOMAIN E` → `rejected` mit `SOURCE_ERRORS`; nach
  Wiederherstellung wieder `succeeded`.
- Shutdown/Exit 0; stdout enthält ausschliesslich gerahmte JSON-RPC-Nachrichten
  (26 Nachrichten), stderr getrennt (0 Bytes).

## 8. P3-3 – Browser, Worker und Diagramm

Eigene portable Clientprobe `build/review-final/logs/client-diagram-review.log`,
**31/31**:

1. Restart erhält drei Quellen und vollständige Konfiguration (inkl.
   Cache-/Sessionlimits); neue Session-/Snapshot-Handles; alte Session- und
   Snapshot-Handles `SESSION_EXPIRED`/`SNAPSHOT_EXPIRED`.
2. Eine reale verspätete Antwort der alten Worker-Epoche wird ignoriert; der
   Restart bricht die laufende Anfrage mit `CANCELLED` ab; neue Epoche
   kompiliert weiter.
3. Rejection mit `SOURCE_ERRORS`; Freigabe nach Rejection; begrenzte
   Snapshots (`maxRetainedSnapshots=1`) erzwingen `LIMIT_EXCEEDED`; doppelte
   Freigabe ist idempotent und schafft Kapazität.
4. Diagramm: explizit leere Auswahl `INVALID_REQUEST`; `maxNodes=1` →
   `LIMITED`; gemeinsames `maxMembersPerNode=1` → `LIMITED` und ≤1 Mitglied je
   Knoten; `showTypes=false` entfernt alle Typlabels; TRANSLATED ändert Labels,
   nicht IDs/Anker; NEIGHBOURHOOD 0/1; Tiefe 33 und fremder
   Übersetzungs-Snapshot abgelehnt; freigegebener Snapshot `SNAPSHOT_EXPIRED`.

Gate-seitig: echte TeaVM-Browser je Chromium/Firefox/WebKit 3548+24,
echtes Safari 3548+24, Native-WebImage-Browser je 3548+24, Node-Client 18,
Diagramm 4, echte UI 3/3 mit verspätetem SVG-/ELK-Layout, Transport 29/Client 7,
externe Browseranfragen 0.

## 9. P3-4 – Corpus, Referenz und Belastung

Eigene Corpusproben:

- `corpus-review.log`, **31/31**: Archiv 367 Einträge (366 Quellen + Index),
  223013-Byte-Index SHA `ff9561b4…`, 9 Windows-1252/357 UTF-8, alle Rohbytes,
  UTF-16BE-Texte und Längen gepinnt; keine Duplikate; 347 Wurzeln; Quellen-
  mengen ≤11; Root-Hashes und Index-MD5 gleich.
- `corpus-parity-review.log`, **43/43**: je 347 Zeilen auf sechs Laufzeiten
  (2082 Zeilen), alle 262/85-Ausgänge, Modell/Version/Datei, JVM-Semantik und
  exakte Rejection-Gründe/-IDs/-Diagnoseargumente gleich; keine `LIMITED`,
  keine Skips/Fehler.
- Assessment: 262 Erfolge/85 ausdrücklich bewertete strikte Ablehnungen,
  11 vorkommende Gruppen, 100 Zuordnungen, 821 Diagnoseidentitäten; zwei
  deklarierte Gruppen kommen im Corpus nicht vor; jede Ablehnung trägt
  `SOURCE_ERRORS`.
- `corpus-reference-review.log`, **2/2**: unveränderter ili2c 5.6.9-JAR
  akzeptiert **347/347** Wurzeln in Abhängigkeitsreihenfolge. Das ist eine
  Referenzbeobachtung; es ändert kein bestätigtes striktes Nutzer-/Normprofil.

Die Budgets `docs/evidence/p3/full-corpus/budgets.json` sind bytegleich
(`e9f02473…`); keine Budget- oder Deadlineanhebung.

### 9.1 Performance-Rohwerte (frisch im Gesamtlauf)

Nearest-Rank-p95 über die jeweiligen Rohproben; max über alle Proben. JVM-Heap
und Node-Prozess-RSS sind getrennte Grössen; für Browser werden keine
erfundenen Heapwerte behauptet.

| Laufzeit | Roots | First p95 / max (ms) | Warm p95 / max (ms) | Kalt (ms) | Speicher |
|---|---:|---:|---:|---:|---:|
| JVM | 347 | 64.451 / 359.907 | 44.594 / 199.650 | 1110.418 Prozess | Heap 1921032184 B |
| WasmGC/Node | 347 | 410.065 / 821.269 | 389.129 / 873.127 | 70.231 Load | RSS 1262206976 B |
| Chromium 156.0.8078.4 | 347 | 363 / 757.9 | 383.9 / 774.7 | 47.7 | – |
| Firefox 157.0 | 347 | 629 / 1357 | 577 / 1487 | 1483 | – |
| WebKit 27.2 | 347 | 497 / 921 | 457 / 1058 | 89 | – |
| Safari 27.0 | 347 | 1140 / 3979 | 1163 / 4239 | 249 | – |

Alle Werte liegen innerhalb der festen Budgets: JVM first 5000/warm 2000/open
2000/cold 15000 ms, Heap ≤2147483648 B; Wasm first 10000/warm 5000/open 3000/
cold 15000 ms, RSS ≤2147483648 B; Browser first 15000/warm 10000/open 5000/
cold 30000 ms.

| Editor | Edits | Closure UTF-16 | p95 / max (ms) | Retention/Freigaben | alte Revisionen/Handles | Abbruch |
|---|---:|---:|---:|---|---|---|
| JVM | 1000 | 247130 | 132.822 / 679.008 | 4 / 996 | 1000 / 0 | 1001 Checkpoints, 26.390 ms, erholt |
| Wasm/Node | 1000 | 247130 | 865.384 / 1673.502 | 4 / 996 | 1000 / 0 | –; RSS 802914304 B |

Artefakte: Wasm 4511422 B raw / 1293283 B gzip / 801896 B brotli; Gesamtpayload
9687084 B raw / 2286396 B gzip (39 Dateien). Ereignisdruck: 1000 Läufe, Queue 32,
32 retained, 10000 offered, 9968 exakt verworfen, zweite Instanz unbeeinflusst.
`performanceCheck` lief als letztes Gate serialisiert; es liefen keine
konkurrierenden lokalen Compiler.

## 10. P3-5 – SDK, Reproduzierbarkeit, Browsermanifest und Plattformen

### 10.1 SDK-Consumer

Eigene Probe `build/review-final/logs/sdk-consumer-review.log`, **27/27**:
SDK-ZIP `5a607b4eb03b5ea46fc9b4840960c793a4c72eeaba6a4a1e97366a28ec254170`
in einen Pfad mit Leerzeichen entpackt (`/tmp/interlis-r2 sdk consumer.…`).
`javac --release 25` ausschliesslich gegen `cli/lib/*.jar` (12 JARs),
Ausführung `SDK PASS … models=1`, Launcher `interlis-cli version --json` und
`ili2c --version`. SDK-, Repository-CLI- und Versionsmanifest-Werte stimmen
überein. Keine JUnit-/Gson-/TeaVM-/LSP-/Test-JARs; im SDK-ZIP sind keine
Verifikationsklassen. Negativkontrolle ohne Compiler-JAR scheitert mit
`package guru.interlis.compiler does not exist`. Das korrigierte TeaVM-Tool-JAR
ist nicht im SDK.

### 10.2 Zwei saubere Erzeugungen

`sdkReproducibilityCheck`: zwei separate saubere Git-Worktrees, unabhängige
Java-/ANTLR-/TeaVM-/TypeScript-Erzeugung; **84/84 ausgelieferte Dateien
bytegleich**, inkl. SDK-ZIP, aller JVM-Dateien, TeaVM-JavaScript
`a6eb9ae8aa141fd9f89e31a378d72b0465d1a86ff9772e367a3aa05cd8aa8a5b`,
Wasm `5daa40eca1a153927e2e535c7a64d76a08075bd338e9d58f4bdb5d349738e7ed`,
Runtime `4fa1e0e0d686cc6e0a0d111e606cb876f7168bd695d9e3d639e7d5684453089b`
und Browsermanifest `f65ec65026077c2d5f5a3ae8b6eb487bd807aa8f27f4dd2d4c691c528e38ea55`.
Ergebnis `build/reports/p3/P3-5/reproducibility/result.json`, SHA-256
`c4c4e706c18d3c854b4b4f1c141a33ff974f1fbe2f8a1b5a68a4ad436a00d29e`,
`cleanBuilds=2`, `shippedFiles=84`. Eigene Gegenprüfung: beide
`first/artifacts.json` und `second/artifacts.json` enthalten exakt die 84
tatsächlich gelieferten Dateien; alle Byte-Hashes/Grössen stimmen mit der
aktuellen Lieferung überein; SDK-ZIP ist in beiden Listen enthalten.

### 10.3 Browsermanifest und TeaVM-Toolkorrektur

`interlis-wasm-teavm/build/web/build-manifest.json` (SHA-256 `f65ec650…`)
bindet Commit `4b586b1…`, `dirty=false`, Quellfingerprint `794d6e69…`, getrennte
Versionen (Werkzeug `0.2.0-SNAPSHOT`, API `0.2`, Diagnose
`interlis-diagnostics/v1`, Bridge `interlis.compiler/1.0`, Format `1.0`,
Diagramm `1.0`, Sprachen `2.3, 2.4`), tatsächliche Compilerfähigkeiten
(`protocol=interlis.compiler`, `protocolVersion=1.0`,
`runtime=teavm-wasm-gc`), **38 Ressourcen** mit Hash und Bytezahl. Die
tatsächliche Inventur aus dem Dateisystem ergibt 84 Lieferdateien und 38
Webressourcen; nur die Selbstreferenz des Manifests ist ausgelassen.

Eigene TeaVM-Prüfung `teavm-patch-review.log`, **22/22**:

- Originalpins: Quellarchiv `687cabf2…`, Core-JAR `c9453991…`,
  `TeaVM.java` `4680ff43…`; Patch-ID `teavm-0.16.0-ordered-class-source-v1`,
  `patchedSourceSha256=7497ffd1…`.
- Genau drei Ersetzungen (Analyse, Lazy, Eager); Rücktransformation ergibt
  bytegleich den Originalquelltext; keine weitere Änderung.
- 1909 Core-Einträge; nur 3 `TeaVM*`-Klassen unterscheiden sich, alle übrigen
  1906 Einträge sind bytegleich; keine Duplikate.
- Korrigiertes Tool-JAR `c10f5d7c6bec1fc81faa07313decbf9efcdd793f6b62e27bf388ee5a410d6326`
  stimmt mit dem Browsermanifest und dem tatsächlichen Build überein; nicht im
  Java-SDK.
- Original-Core tatsächlich RED: `java -cp check-classes:teavm-core-0.16.0.jar
  ClassOrderCheck` endet Exit 1 mit
  `AssertionError: TeaVM class traversal depends on unordered set iteration`.
  Korrigierter Core: `ORDERED_TEAVM_CONTRACT_PASS permutations=80 classes=40
  membership/identity/filtering/immutability`, Exit 0.

## 11. Schliessung der R1-Befunde F1–F3

Die Proben verwenden die **tatsächlichen** aktuellen Berichte als Ausgangspunkt.
Der alte Validator wurde ausschliesslich als Vergleichsmodul aus dem
Git-Objekt `f341d7b:tools/release/evidence.cjs` materialisiert
(SHA-256 `90c1bd8b71b15c27a66154bcb280a653679e2b157b3b0509b97844693ba39434`);
es wurden keine realen Berichte umetikettiert oder verändert. Mutationen
erfolgen im Speicher; die Inventurproben erzeugen nur temporäre Build-Dateien,
die vollständig entfernt werden.

### F1 (MUSS, geschlossen) – übersprungene Browser-/Safari-Belege

Reproduktion `evidence-inventory-review.log`, Fälle `R1-F1-browser-skipped`,
`R1-F1-safari-skipped`, zusätzlich `failures`, `errors`, `cancelled`, `todo`
sowie widersprüchliche `status`/`outcome`/`compiledOutcome`/`rejectedOutcome`.
Ergebnis:

- alter Validator akzeptiert alle sieben Skip-/Fehler-Fixtures;
- aktueller Validator lehnt jedes mit `failed/cancelled/skipped runtime cases`
  bzw. `contradictory semantic runtime outcomes` ab;
- zusätzlich lehnt er `status='failed'`, `compiledOutcome='rejected'`,
  `rejectedOutcome='succeeded'`, `outcome='failed'` und
  `workerRestartOutcome!='succeeded'` ab.

### F2 (MUSS, geschlossen) – partielle Reproduzierbarkeitsliste

Originalmutation: SDK-ZIP-Eintrag aus den tatsächlichen 84 Artefakten entfernt
und `shippedFiles` auf 83 dekrementiert. Der alte Validator akzeptiert; der
aktuelle lehnt mit `incomplete/foreign reproduced delivery inventory` ab.
Zusätzlich abgelehnt: entfernte öffentliche JAR, entfernter Launcher, fremder
Ersatz bei gleichem Zähler, selbstdeklarierte 83, fehlende unabhängige
Inventur, Duplikat, `cleanBuilds=1`, fremder Kandidat und veralteter
Fingerprint.

### F3 (MUSS, geschlossen) – partielles Browsermanifest

Originalmutation: 17 der 38 Ressourcen entfernt, 21 bleiben. Der alte
Validator akzeptiert; der aktuelle lehnt mit
`incomplete/foreign browser resource inventory` ab. Zusätzlich abgelehnt:
eine fehlende Ressource, fremde Ressource, falscher Hash, falsche Bytezahl,
fehlende unabhängige Ressourceninventur, veralteter Commit, Versionsdrift,
fehlender Patch-Pin und falscher Original-Core-Pin.

Die tatsächliche aktuelle Lieferung ist vollständig: 84 Dateien, 38
Webressourcen, Manifest- und Ressourcenhashes stimmen; der positive
Gesamtvalidator akzeptiert den unveränderten vollständigen Input.

## 12. Weitere eigene positive und negative Gegenproben

- `report-counts.log`, **79/79**: unabhängige XML-Auszählung 1186 Tests in 223
  Suites und Hashgleichheit mit dem Plattformbericht; Node-Client 18,
  Diagramm 4; drei Playwright-Browser je 3548+24, 29 Transport/7 Client,
  Safari, UI 3, Native-WebImage 3; SDK 9/Legacy 54; Conformance 577/16/0 und
  21 Kontrollen; Performance 347/6/2000; Artefakt-/Retention-/Observation-
  Budgets; Browsermanifest 38, Versionen/Capabilities/Patchbindung.
- `evidence-inventory-review.log`, **124/124**: aktueller vollständiger Input
  wird akzeptiert; 100+ Mutationen zu Commit-/Quell-/Artefaktbindung,
  Plattformen, Browsern, Safari, UI, Reproduzierbarkeit, Prozessabschluss,
  Versionsmanifest und Guard werden abgelehnt; alter Validator akzeptiert die
  drei R1-Originalmutationen.
- `inventory-mutation-review.log`, **11/11**: echte zusätzliche Datei in
  `build/web`, zusätzliche öffentliche JAR und entferntes SDK-ZIP werden vom
  realen `main()` abgelehnt; nach Wiederherstellung ist der PASS-Report
  bytegleich (`3e37fa07…`). Damit hängt die Vollständigkeit nicht von einem
  Selbstzähler im Bericht ab.
- `negative-release-config.log`, **12/12**: `releaseCheck` ohne Profil,
  unbekanntes Profil `P9` und fehlender aktueller CI-Nachweis scheitern
  konkret; Original-Plattformbericht wird bytegleich wiederhergestellt; echtes
  `node tools/release/evidence.cjs` läuft danach erfolgreich.
- CLI/LSP/Client/Corpus/TeaVM/SDK wie oben: **43/43, 22/22, 31/31, 31/31,
  43/43, 2/2, 22/22, 27/27**.
- Summe eigener aktueller Prüfungen: **447**; zusätzlich bestehen die zwei
  separaten TeaVM-Javaaufrufe (Original-Core RED, korrigierter Core GREEN)
  ausserhalb der 22 Patchproben. Keine offenen Fehler.

## 13. G0–G3

| Gate | Tatsächliches Ergebnis |
|---|---|
| G0 Build/Parser/TeaVM/Pins | `releaseCheckP3` Exit 0; 1186 JUnit/223 XML, 0 Fehler/Skips; `orderedTeaVmCheck`; zwei saubere Builds 84/84 bytegleich; Original-Core RED, korrigiert 80/40 PASS; Toolpins/Ersetzungen geprüft |
| G1 Semantik/IDE/Browser/Diagramm/WebImage | 3548+24 je JS/WasmGC/Chromium/Firefox/WebKit/Safari/WebImageNode/WebImage-Browser; UI 3/3; Transport 29/Client 7; echte stdio-LSP 22/22 |
| G2 2.3/2.4-Normmatrix | Conformance 577 PASS/16 XFAIL/0 FAIL/0 XPASS/0 ERROR, 21 Normbewertungen; roher Referenzlauf separat 577 PASS/1 XFAIL/15 FAIL; Corpus 262/85 regelweise bewertet; ili2c 347/347 |
| G3 CLI/SDK/Integration/Plattform | CLI 43/43, SDK 27/27, Reproduzierbarkeit 84/84, Plattform Linux/Windows/macOS je 1186/223/9/54, ReleaseEvidenceCheck 124/124 und realer PASS; **keine offenen MUSS-Findings** |

## 14. Offene/NOT_RUN-Pflichten und Aussagegrenzen

**Offene MUSS-Findings:** keine.

**NOT_RUN / nicht lokal wiederholt:**

- Native Windows-/Linux-Ausführung lokal: nicht möglich; die echten
  GitHub-Jobs/Artefakte desselben Commits wurden über den Collector und die
  autoritativen Archivdigesten geprüft.
- Docker-Image-Pull des historischen Corpus: lokal kein Docker; die
  eingefrorenen Bytes, der Index `ff9561b4…`, 366 Originalquellen und die
  347 Root-Hashes wurden unabhängig geprüft. Das ist keine aktuelle
  Image-Pull-Aussage.
- `/dev/full` ist auf macOS nicht verfügbar; der echte Schreibfehler wurde
  mit `ulimit -f 1` bei einer Ausgabe >512 Byte geprüft (Exit 1,
  Zieldatei unverändert).
- Der äussere Transportexit und das endgültige OS-Sperrmanifest werden erst
  nach dem tatsächlichen OpenCode-Ende geprüft; dieser Bericht nimmt sie nicht
  vorweg.
- P4/P5, Generatoren/IlisMeta, Legacy-Java-API, C/C++/iox sind nicht
  Bestandteil dieses P3-Reviews.

## 15. Prozesstreue

- Keine Produkt-, Test-, Build- oder Erwartungsdatei wurde geändert; HEAD und
  Quellfingerprint blieben exakt. Kein Commit/Push/Release.
- Keine Native-Subagents; keine OpenRouter-Nutzung; keine ungeschützte
  Hintergrundinstanz.
- Der geerbte OS-Schutz blieb aktiv: `guardPid=39705` lebte während des
  gesamten Laufs, `status=running`, `dirty=''`, Proben mit `ENOENT` für
  nicht vorhandene Namen und `EPERM` für `/usr/bin/python3` und
  `/usr/bin/pip3`. Der private PATH und die Sperre wurden nicht durch einen
  zweiten Wrapper überschrieben.
- **Offengelegter Vorfall:** Bei einer fehlerhaften Shell-Hilfszeile wurde
  einmal `python3` mit leerem Heredoc aufgerufen. Der Aufruf wurde durch die
  geerbte Sperre beendet; es wurde kein Interpreter gestartet, es entstand
  keine Ausgabe und keine Evidenz. Der Vorfall beeinflusst keine Probe; er
  wird hier unverändert offengelegt.
- Die alten f341d7b-Berichte und der abgebrochene Vorlauf dieses R2-Kontexts
  wurden nicht als aktueller Nachweis verwendet. Der neue Gesamtlauf, die
  eigenen Proben und die tatsächlichen Rohdateien tragen das Urteil.

## 16. Reproduktion der Proben

```bash
cd /private/tmp/interlis-java-P3-5-r2
# positiver Gesamtlauf (oben; Log: build/review-final/logs/releaseCheckP3.log)
./gradlew --no-daemon --console=plain releaseCheckP3 \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node
# eigene Proben
node build/review-final/probes/report-counts-review.cjs
node build/review-final/probes/evidence-inventory-review.cjs
node build/review-final/probes/corpus-review.cjs
node build/review-final/probes/corpus-parity-review.cjs
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem \
  node build/review-final/probes/corpus-reference-review.cjs
node build/review-final/probes/teavm-patch-review.cjs
bash build/review-final/probes/sdk-consumer-review.sh
bash build/review-final/probes/cli-format-review.sh
LSP_CP="$PWD/interlis-lsp/build/install/interlis-lsp/lib/*" \
  node build/review-final/probes/lsp-stdio-review.cjs
node build/review-final/probes/client-diagram-review.cjs
bash build/review-final/probes/negative-release-config.sh
bash build/review-final/probes/inventory-mutation-review.sh
```

Rohlogs: `build/review-final/logs/` (u. a. `releaseCheckP3.log`,
`releaseCheckP3-interrupted.log`, `report-counts.log`,
`evidence-inventory-review.log`, `inventory-mutation-review.log`,
`negative-release-config.log`, `corpus-review.log`,
`corpus-parity-review.log`, `corpus-reference-review.log`,
`teavm-patch-review.log`, `teavm-original-red.log`,
`teavm-patch-green.log`, `sdk-consumer-review.log`,
`cli-format-review.log`, `lsp-stdio-review.log`,
`client-diagram-review.log`); Negativvergleich:
`build/review-final/probes/old/tools/release/evidence.cjs`.

## 17. Schlussbewertung

Der exakte saubere Kandidat `4b586b1e09a2f1cbd200f7d00876e44c81df2169`
besteht den vollständigen aktuellen `releaseCheckP3` regulär mit Exit 0. Alle
funktionalen P3-1–P3-4-Gates, die SDK-/Reproduzierbarkeits-/Browser-/Safari-/
WebImage-/Conformance-/Performance-/Plattformnachweise und die vollständige
Fail-Closed-Prüfung sind grün. F1–F3 sind mit den konkreten Originalmutationen
und zusätzlichen eigenen Gegenproben nachweislich geschlossen; die tatsächliche
Lieferinventur (84 Dateien/38 Webressourcen) wird unabhängig vom Bericht aus
dem Dateisystem bestimmt.

**Fachliches Gesamturteil: PASS für AC-P3-01–20 und G3 ohne offene
MUSS-Findings.** Die formale G3-Abnahme bleibt an den nach dem tatsächlichen
Prozessende separat geprüften Transportexit 0 und das äussere Manifest
`passed` gebunden; dieser Bericht kann diesen äusseren Nachweis nicht
vorwegnehmen. Der Produktcheckout blieb unverändert und sauber.
