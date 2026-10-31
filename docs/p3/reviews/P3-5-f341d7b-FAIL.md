# Unabhängiger kumulativer P3-/G3-Gesamtreview – Kandidat `f341d7bd3baf8e137985723b6216c30d6580384b`

**Fachliches Urteil: FAIL für AC-P3-01–20/WP-30–33 und G3.** Der positive
Gesamtlauf `releaseCheckP3` ist am exakten sauberen Kandidaten vollständig grün
(Exit 0, 30m47s). Die verpflichtenden eigenen Negativproben decken jedoch drei
MUSS-Lücken im Fail-Closed-Verhalten von `releaseEvidenceCheck` auf:
übersprungene Browser-/Safari-Fälle, eine partielle Reproduzierbarkeitsliste
und ein partielles Browsermanifest werden als `passed` akzeptiert. Damit ist
die Anforderung «fehlende, alte, partielle, fremde, übersprungene oder
fehlerhafte Belege ablehnen» nicht erfüllt. Es wurden keine Produkt-, Test-,
Build- oder Erwartungsdateien geändert.

- **Kandidat:** `f341d7bd3baf8e137985723b6216c30d6580384b`
  (`fix: make TeaVM compiler products reproducible across clean builds`).
- **Checkout:** `/private/tmp/interlis-java-P3-5-r1`, sauber; HEAD vor und nach
  dem Review unverändert, `git status --porcelain=v1` leer, `git diff --check`
  leer. Kein Commit, Push oder Release.
- **Bericht:** `/Users/stefan/sources/interlis-java/build/review/P3-5-r1/review.md`
  (dieser Text; nach dem Schreiben nicht mehr verändert).
- **Eigene Proben/Rohlogs:** `build/review-final/` im Kandidatencheckout,
  kopiert nach `/Users/stefan/sources/interlis-java/build/review-final/P3-5-r1/`.
- **Unabhängiger Kontext:** neuer OpenCode-Lauf, direkte DeepSeek-API,
  Provider `deepseek/deepseek-flash`; keine Native-Subagents.
- **Ausführungsschutz:** Der gesamte Prozess erbte die äussere
  OS-Ausführungssperre. `guardPid` war während des Laufs aktiv
  (`build/reports/python-free/environment.json`, `status=running`,
  `guardPid=17660`, Proben mit `ENOENT`/`EPERM`, mindestens eine absolute
  `EPERM`-Probe). Es wurde kein Python/pip/uv ausgeführt; ein versehentlicher
  Shell-Aufruf von `python3` wurde von der geerbten Umgebung nicht gestartet
  (`command not found`/ENOENT). Der äussere Transportexit und das endgültige
  Manifest werden erst nach dem regulären OpenCode-Ende geprüft; dieser Bericht
  nimmt sie nicht vorweg.

## 1. Vorbedingungen und kumulativer Umfang

Unverändert gelesen und als Vorbedingung geprüft:

| Lieferung | Unabhängiger PASS | Produkt-SHA | Bericht-SHA-256 |
|---|---|---|---|
| P3-1 | `docs/p3/reviews/P3-1-review.md` | `9108b0387d9f1b82f506d8dc64e301e8522137dd` | `9133ef5e8499f67f966e593be9b968f59510e2d0ee0a18a61fff8fcf608a5db7` |
| P3-2 | `docs/p3/reviews/P3-2-review.md` | `5eac6b64fb352982376f054a765597688d5f975b` | `12249d8c9120eb108fadebcb1850e8c377ca41e289423cf3fecd904c523b0c1d` |
| P3-3 | `docs/p3/reviews/P3-3-review.md` | `253d9ca837d05cc86959caf04fb2f4bab34c74c0` | `7b57aeaf6ffefde51e3f23be5a1236148c9dc8c605b218e679f0a7882ef188da` |
| P3-4 | `docs/p3/reviews/P3-4-review.md` | `280589065a7900e6b3bb2a338f2cd012865091e1` | `f5cb2399cab9a595ecf9ac637749a2319e6bfff05b5c66c56037225ac3cfdc65` |

Der Diff `2805890..f341d7b` enthält keine Änderung in
`interlis-compiler/src/main`, `interlis-model/src/main`,
`interlis-workspace/src/main` oder `interlis-projection/src/main`. Er enthält
die P3-5-/SDK-/Release-/Plattformharnesses, das Browsermanifest, die
deterministische TeaVM-Toolkorrektur und die zugehörige Dokumentation. Der
eingefrorene Corpus und die historischen Pins sind unverändert:

| Datei | SHA-256 |
|---|---|
| `docs/evidence/p3/full-corpus/manifest.json` | `5d8a0fd384142d00cfb858307ff7a9dd74b0d0833f350a4015fa27d691001e0a` |
| `docs/evidence/p3/full-corpus/sources.zip` | `b5f0057cefbfed24312213657ee6043553bf3815ddac5127651c668a610c64c4` |
| `docs/evidence/p3/full-corpus/assessment.json` | `3dc2250a759027b1f7ec7de5ddba01bcfb8294098a5ecc23fadd5aaf6baead9f` |
| `docs/evidence/p3/full-corpus/budgets.json` | `e9f02473567dc446d51c18a3fa607e38a45b9b7183c099d9890a07b8b8804a4e` |

`testdata/g2/completion-links.json` wurde nur in zwei `sha256`-Locatorwerten
mechanisch nachgeführt; die fachlichen Auflösungen und Normerwartungen sind
unverändert. `testdata/g2/corpus-expectations.json`, die 13er-/Suite-Pins,
`testdata/conformance` und `gradle/conformance-suite.properties` sind
bytegleich. Die 21 Normbewertungen sind im aktuellen Conformancebericht
vollständig vorhanden.

## 2. Umgebung und tatsächlich verwendete Werkzeuge

| Bestandteil | Tatsächlich verwendet |
|---|---|
| Hardware | MacBookAir10,1, Apple M1, 8 logische CPUs, 16 GiB (17179869184 Bytes) |
| OS | macOS 15.8.1, Darwin 24.6.0, arm64 |
| Java | Temurin 25.0.3+9 |
| Node/npm | Node v24.22.0, npm 11.19.1; `npm ci` im TeaVM-Modul: 14 Pakete, Lockfile unverändert |
| Gradle | Wrapper 9.8.1, `--no-daemon`, Java-25-Launcher |
| GraalVM/Binaryen | Oracle GraalVM 25.0.3+9.1, Binaryen version_133 |
| Browser | Chromium 156.0.8078.4, Firefox 157.0, Playwright-WebKit 27.2, echtes Safari 27.0 |
| Referenzcompiler | ili2c 5.6.9-bf16026e83f2377153b2e818a4f3838f0a423722, JAR-SHA-256 `37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e` |
| GitHub | `gh` 2.102.0, read-only `gh api`; Lauf `41205494674` |

Der Lauf wurde serialisiert ausgeführt; während `releaseCheckP3` liefen keine
konkurrierenden lokalen Compiler. `caffeinate -diu` war als temporäre
Schutzassertion aktiv; es wurden keine globalen Energieeinstellungen geändert.

## 3. Frischer positiver Gesamtlauf

```bash
cd /private/tmp/interlis-java-P3-5-r1
(cd interlis-wasm-teavm && npm ci)
node tools/release/platform-evidence.cjs
./gradlew --no-daemon releaseCheckP3 \
  -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node
```

- Plattform-Collector: `GITHUB_PLATFORM_EVIDENCE_PASS 41205494674 f341d7b…`,
  Exit 0. Run: `.github/workflows/p3-platforms.yml`, `completed/success`,
  `headSha=f341d7b…`; Jobs Linux-x64 `124549613450` und Windows-x64
  `124549613681`, beide `completed/success`.
- `releaseCheckP3`: `BUILD SUCCESSFUL in 30m 47s`, `RELEASE_CHECK_P3_EXIT=0`,
  100 Tasks ausgeführt, `P3_RELEASE_EVIDENCE_PASS`.
- Release-Ergebnis: `build/reports/p3/release/result.json`, SHA-256
  `cd783983e65c275bca023c15a57a290df9f9e61b7d1724398f1bd3ed415e7f92`.
- Quellfingerprint: `cafe11ce3af9b5f8d540ac9ad89881f1a56110bad8bce0e69b4672d54fb57c39`,
  `dirty=false`.
- Rohlog: `build/review-final/logs/releaseCheckP3.log`, SHA-256
  `1d01005aacceb12ea8ced18a8f7da3580f15bd1535d576a6e53df2539c558d35`.

Hinweis zur Prozesstreue: Der negative Vorprobe «fehlender aktueller
CI-Nachweis» startet absichtlich `releaseCheckP3` ohne
`build/reports/p3/platform-evidence/result.json`. Der produktive Preflight
entfernt dabei `build/reports/p3/release/result.json`. Der Reviewer hat die
zuvor vom positiven Validator zurückgegebene, bytegleiche Struktur wieder
abgelegt; sie ist mit dem Elternergebnis am selben Kandidaten bytegleich
(SHA `cd78…`). Die gültigen Plattform-Rohberichte blieben unverändert
(`build/reports/p3/platform-evidence/result.json`, SHA
`ad2a1af62ffa50023b643e2c9855e4a2953ee04e2efc1f455079c58fbd11f3d0`).

## 4. Kriterienurteil AC-P3-01 bis AC-P3-20

| Kriterium | Urteil | Wesentliche Beobachtung |
|---|---|---|
| AC-P3-01 – CLI-001–004, COMPAT-001–003, LOG-005 | **PASS** | 39/39 eigene CLI-/Formatproben; ili2c-5.6.9-Profil, getrennte Streams/Exits, 32 Generator-/Hostoptionen abgelehnt, Referenzabweichungen belegt |
| AC-P3-02 – CLI-005–007, DIAG/LOG | **PASS** | Konfigurationspräzedenz CLI > Datei > Default mit Herkunft; Redaktion; IO-/Schreib-/Flushfehler ohne Erfolg; 1186 JUnit inkl. Lifecycle-/Prioritätstests |
| AC-P3-03/04 – FMT-001–004 | **PASS** | Idempotente öffentliche Formatierung, Semantikerhalt, atomare `--write`-Fehler, Syntax-/IO-/Schreibfehler ohne Zielbeschädigung |
| AC-P3-05 – INC-018/019/022, LSP-003/004/006/011/015 | **PASS** | Echte stdio-Probe 7/7: nicht blockierende geordnete Änderungen, neuester Text, Abbruch `-32800`, geschlossene Quelle, Hard-/Symlink-Alias, Resync nach kanonischer Quelle |
| AC-P3-06 – LSP-012 | **PASS** | Mehrdatei-Rename mit 2 Dateien/2 Edits; Kollisions-/Versionstests im Gate |
| AC-P3-07 – LSP-007/010/013/014 | **PASS** | Completion, Symbole, Tokens, Folding, Formatierung, Hover/Definition und Diagrammanker aus demselben Snapshot |
| AC-P3-08 – LSP-001/002/008/009/016 | **PASS** | stdio sauber, Shutdown/Exit 0, Capabilityliste implementiert, echte Prozessprüfung |
| AC-P3-09 – WASM-009–011/017 | **PASS** | 5/5 eigene Clientproben: Restart mit 3 Quellen/vollem Konfigurationsstand, neue Handles, alte Session-/Snapshot-Handles abgelehnt |
| AC-P3-10 – WASM-008/015, INC-012/018 | **PASS** | Begrenzte Snapshots (4), 996 Freigaben, 1000 alte Revisionen abgelehnt, 0 alte Handles akzeptiert; Abbruch/Erholung |
| AC-P3-11 – VIZ-001–018, WASM-014 | **PASS** | Explizite Optionen, leere Auswahl abgelehnt, `maxNodes`/`maxMembersPerNode` LIMITED, `showTypes=false`, TRANSLATED, NEIGHBOURHOOD 0/1, verspätetes UI-Layout verworfen (Gate 3/3) |
| AC-P3-12 – TEST-007/010, RES | **PASS** | 347 Wurzeln/366 Dateien/9 Windows-1252/357 UTF-8; 262/85, 11 Gruppen, 100 Zuordnungen, 821 Diagnosen; ili2c 347/347 |
| AC-P3-13 – NFR-004, INC-009/014/017 | **PASS** | 2×1000 echte Edits auf 247130 UTF-16; JVM p95 72.255ms, Wasm p95 824.178ms; Retention 4, 996 Freigaben |
| AC-P3-14 – TEST-008, WASM-006/018 | **PASS** | 347 auf JVM, Node-WasmGC, Chromium, Firefox, WebKit und echtem Safari; 0 Skips/LIMITED/Traps |
| AC-P3-15 – NFR-005, WASM-011/015 | **PASS** | JVM-Abbruch nach 1001 Checkpoints, Erholung; Worker-Abbruch, RuntimeFailure, Dispose in Client/Browsern |
| AC-P3-16 – LOG-009/010 | **PASS** | 1000 Läufe, Queue 32, 32 retained, 10000 offered, 9968 exakt verworfen, zweite Instanz unbeeinflusst |
| AC-P3-17 – SDK-001–003, API-001–006, BUILD-006/009 | **PASS** | SDK-Consumer nur aus entpacktem ZIP in Pfad mit Leerzeichen, `javac`/Run/Launcher, Versionen gleich CLI, keine Test-/Gson-/TeaVM-Leaks |
| AC-P3-18 – BUILD-008, CLI-004, TEST-008 | **PASS** | Releaseprofil scheitert ohne/bei unbekanntem Profil und ohne aktuellen CI-Nachweis konkret; Plattformprozesse 1186/223 |
| AC-P3-19 – BUILD-001–008, TEST-001–013 | **PASS** | Gradle-only, reproduzierbare JVM/SDK/TeaVM/Browserbytes; 84/84 Dateien zweier sauberer Erzeugungen bytegleich |
| AC-P3-20 – kumulativ AC-P3-01–19 und G0–G3 | **FAIL** | `releaseEvidenceCheck` akzeptiert übersprungene und partielle Belege (F1–F3); die positiven Einzelgates sind grün, aber das Fail-Closed-Gate ist nicht erfüllt |

## 5. P3-1 – CLI und Formatierung

Eigene Proben (`build/review-final/logs/cli-format-review.log`), 39/39:

- `bin/interlis-cli` und `bin/ili2c`; `version --json` mit API `0.2`,
  Protokoll `interlis.compiler/1.0`, Diagnose `interlis-diagnostics/v1`,
  Format `1.0`, Diagrammschema `1.0`.
- Gültiger Compile Exit 0 mit leerem stdout; Syntaxfehler Exit 1 mit
  `ILI-`-Diagnostik; fehlende Datei Exit 1 `NOT_FOUND`; unbekannte Option
  Exit 1 vor IO; `-o1` Exit 1 «unsupported option».
- `--explain-config`: `--root` Herkunft `CLI`, `--warnings`
  `CONFIGURATION_FILE`, `--offline` `PROFILE_DEFAULT`; URI-Zugangsdaten und
  Query-Geheimnisse redigiert.
- Formatierung: `--check` Exit 1 ohne Schreiben, `--write` idempotent
  (`52f2b640…`), Re-Parse/Compile vor/nach semantisch gleich, stdout gleich
  Datei, Syntaxfehler und IO-Fehler ohne Zieländerung. `/dev/full` war nicht
  verfügbar; ein echter Schreibfehler über `ulimit -f 1` endet mit Exit 1 und
  maximal 512 Bytes Teilausgabe.
- Referenzprofil: `--no-auto` fehlende Datei Referenz Exit 0 vs. Ersatz Exit 1;
  unbekannte Option Referenz Exit 0 vs. Ersatz Exit 1; `compat/ili2c.jar`
  startet mit Exit 0.

## 6. P3-2 – LSP und Sprachabfragen

Echte stdio-Prozessprobe (`build/review-final/logs/lsp-stdio-review.log`), 7/7:

- Initialize mit 13 Fähigkeiten; zwei Dateien geöffnet, zwei `didChange`-
  Versionen, `documentSymbol` sieht den neuesten Text `extra2`.
- Completion, Semantic Tokens (130), Folding (3), Formatting, Hover,
  Definition; Mehrdatei-Rename mit 2 Dateien/2 Edits.
- `didClose` der Base: Definition bleibt aufgelöst; Rename der geschlossenen
  Quelle liefert keinen falschen Erfolg.
- Hardlink- und Symlink-Alias öffnen und kompilieren mit `succeeded`,
  `modelCount=3`; nach Schliessen der Aliasse und Änderung der kanonischen
  Base auf ein unauflösbares `DOMAIN E` liefert Compile `rejected`
  (`SOURCE_ERRORS`), nach Wiederherstellung wieder `succeeded`.
- Laufender Compile auf 2000 Klassen: `$/cancelRequest` ergibt `-32800`;
  Shutdown/Exit 0.

## 7. P3-3 – Browser, Worker und Diagramm

Eigene portable Clientprobe (`build/review-final/logs/compiler-client-review.log`),
5/5:

1. Restart erhält drei Quellen und vollständige Konfiguration; neue Session-
   und Snapshot-Handles; alte Session-/Snapshot-Handles typisiert abgelehnt.
2. Freigabe nach Erfolg/Ablehnung: `maxRetainedSnapshots=1`, doppelte Freigabe
   idempotent, danach neuer Snapshot möglich; alte Schema-Handles
   `SNAPSHOT_EXPIRED`.
3. Diagramm: explizit leere Auswahl `INVALID_REQUEST`; `maxNodes=2` →
   `LIMITED`; `maxMembersPerNode=1` → `LIMITED` und ≤1 Mitglied je Knoten;
   `showTypes=false` ohne Typlabels; TRANSLATED ändert Labels, nicht IDs/
   Anker; NEIGHBOURHOOD 0/1; Tiefe 33 und fremder Übersetzungs-Snapshot
   abgelehnt.
4. Laufender Abbruch startet Worker neu, alte und wartende Arbeit `CANCELLED`,
   bestätigter Stand wiederhergestellt.
5. Antwort einer alten Worker-Epoche wird nicht als Ergebnis akzeptiert.

Der Gate-Lauf bestätigt zusätzlich 3548+24 Fälle je Chromium/Firefox/WebKit
und echtem Safari, Transport 29/Client 7, echte UI 3/3 mit verspätetem
SVG-/ELK-Layout, Native Web Image 3/3.

## 8. P3-4 – Corpus und Belastung

Eigene Prüfung (`build/review-final/logs/corpus-performance.log`), 9/9
Positiv- und 12/12 Negativkontrollen:

- Archiv 367 Einträge (366 Quellen + Index), Index 223013 Bytes, SHA
  `ff9561b4…`; 9 Windows-1252/357 UTF-8, alle Rohbytes und UTF-16BE-Texte
  gehasht; keine Duplikate.
- 347 Wurzeln, 262 Erfolge, 85 Ablehnungen, alle mit `SOURCE_ERRORS`,
  Diagnose-IDs/Argumente/Bereiche (821 Diagnosen) innerhalb der dekodierten
  Quellen; 11 vorkommende Regelgruppen, 100 Zuordnungen.
- Referenzlauf: **347/347 akzeptiert, 0 Fehler**, `build/review-final/raw/ili2c-all-results.json`.
- `performanceCheck`: 6 Laufzeiten, 347 Wurzeln, 2000 echte Edits, 0 Skips,
  0 Semantikabweichungen; Budgets unverändert.

### 8.1 Rohwerte (nearest-rank p95, Wandzeit)

| Laufzeit | Roots | First p95 / max (ms) | Warm p95 / max (ms) | Kalt (ms) | Speicher |
|---|---:|---:|---:|---:|---:|
| JVM | 347 | 60.112 / 337.341 | 42.937 / 125.875 | 752.765 | Heap 1853631544 B |
| WasmGC/Node | 347 | 385.740 / 769.747 | 385.601 / 787.592 | 66.195 | RSS 1358315520 B |
| Chromium 156.0.8078.4 | 347 | 388.1 / 747.2 | 367.8 / 778.3 | 48.2 | – |
| Firefox 157.0 | 347 | 660 / 1259 | 550 / 1204 | 1348 | – |
| WebKit 27.2 | 347 | 470 / 902 | 436 / 890 | 83 | – |
| Safari 27.0 | 347 | 496 / 921 | 436 / 1018 | 230 | – |

JVM-Heap und Node-RSS sind getrennte Grössen; für Browser werden keine
erfundenen Heapwerte behauptet. Budgets: JVM-Heap ≤2147483648 B,
Wasm-RSS ≤2147483648 B, Wasm raw ≤6291456 B/gzip ≤2097152 B,
Browserpayload raw ≤20971520 B/gzip ≤6291456 B.

| Editor | Edits | Closure UTF-16 | p95 / max (ms) | Retention/Freigaben | alte Revisionen/Handles | Abbruch |
|---|---:|---:|---:|---|---|---|
| JVM | 1000 | 247130 | 72.255 / 679.340 | 4 / 996 | 1000 / 0 | 1001 Checkpoints, 37.043ms, erholt |
| Wasm/Node | 1000 | 247130 | 824.178 / 1168.865 | 4 / 996 | 1000 / 0 | – |

Artefakte: Wasm 4511422 B raw/1293283 B gzip; Gesamtpayload 9687084 B
raw/2286395 B gzip (39 Dateien). Ereignisdruck: 1000 Läufe, Queue 32,
32 retained, 10000 offered, 9968 exakt verworfen, zweite Instanz unbeeinflusst.

## 9. P3-5 – SDK, Reproduzierbarkeit, Browsermanifest und Toolkorrektur

### 9.1 SDK-Consumer

Eigene Probe (`build/review-final/logs/sdk-consumer-review.log`): SDK-ZIP
`5a607b4eb03b5ea46fc9b4840960c793a4c72eeaba6a4a1e97366a28ec254170`
in einen Pfad mit Leerzeichen entpackt. `javac --release 25` ausschliesslich
gegen `cli/lib/*.jar` (12 JARs), Ausführung `SDK PASS … models=1`, Launcher
`interlis-cli version --json` semantisch gleich
`version/version-manifest.json`, Repository-CLI ebenfalls gleich. Die
Negativkontrolle ohne Compiler-JAR scheitert mit `package guru.interlis.compiler
does not exist`. Keine JUnit-/Gson-/TeaVM-JARs oder -Pakete im SDK.

### 9.2 Zwei saubere Erzeugungen

`sdkReproducibilityCheck`: zwei separate saubere Git-Worktrees, unabhängige
Java-/ANTLR-/TeaVM-/TypeScript-Erzeugung, **84/84 ausgelieferte Dateien
bytegleich**, darunter SDK-ZIP, alle JVM-Dateien, TeaVM-JavaScript
`a6eb9ae8…`, Wasm `5daa40ec…`, Runtime und Browsermanifest
`a5d41111…`. Ergebnis:
`build/reports/p3/P3-5/reproducibility/result.json`, SHA
`4fd99e2bd14da1ee0bdef7a32aabf6a5149529560990cce3455b8c4092a9489f`.
Eigene Gegenprüfung aller 84 Hashes gegen die Hauptausgabe: 84/84.

### 9.3 Browsermanifest und TeaVM-Toolkorrektur

`interlis-wasm-teavm/build/web/build-manifest.json` (38 Ressourcen) bindet
Commit `f341d7b…`, `dirty=false`, Quellfingerprint `cafe11ce…`, getrennte
Versionen (Werkzeug `0.2.0-SNAPSHOT`, API `0.2`, Diagnose
`interlis-diagnostics/v1`, Bridge `interlis.compiler/1.0`, Format `1.0`,
Diagramm `1.0`, Sprachen `2.3, 2.4`), tatsächliche Compilerfähigkeiten
(`runtime=teavm-wasm-gc`, Toolversion/Protokoll), alle 38 Ressourcenhashes und
Bytezahlen. Eigene Probe 7/7, u. a. `compiledCoreSha256=
c10f5d7c6bec1fc81faa07313decbf9efcdd793f6b62e27bf388ee5a410d6326` gleich dem
tatsächlichen korrigierten Core-JAR.

Eigene Toolkorrekturprüfung (`build/review-final/logs/teavm-patch.log`), 5/5:

- Originalpins: Quellarchiv `687cabf2…`, Core-JAR `c9453991…`,
  `TeaVM.java` `4680ff43…`.
- Genau drei Ersetzungen (Analyse, Lazy, Eager); Rücktransformation ergibt
  bytegleich den Originalquelltext; keine weitere Änderung.
- Alle Nicht-`TeaVM*`-Core-Einträge bytegleich; 6/6 ersetzte TeaVM-Klassen
  stimmen mit den kompilierten Klassen überein.
- Original-Core scheitert an derselben `orderedTeaVmCheck`-Assertion; die
  Korrektur besteht (80 Permutationen/40 Klassen im Gate).
- Das korrigierte Tool-JAR ist nicht im SDK enthalten.

Negative Eingangsproben (`negative-teavm-inputs.log`), 3/3: fremdes
Quellarchiv, fremdes Core-JAR und doppelter Core-Eintrag schliessen den Build
mit den erwarteten Meldungen.

### 9.4 Plattformmatrix

Der Collector prüfte die tatsächlichen GitHub-Artefakte über `sha256:`-Digests
und die eingebetteten Plattformberichte:

| Plattform | Job | Archivdigest | Bericht-SHA-256 | Tests/Suites | SDK/Legacy | SDK-ZIP |
|---|---|---|---|---:|---:|---|
| Linux x64 | `124549613450` | `sha256:aa4b2753…` | `51d25653…` | 1186/223 | 9/54 | `5a607b4e…` |
| Windows x64 | `124549613681` | `sha256:1c26f002…` | `42628b75…` | 1186/223 | 9/54 | `5a607b4e…` |

Lokal macOS arm64: `PLATFORM_PROCESSES_PASS … tests=1186
sdkArchive=5a607b4e…`; drei aktuelle JVM-Ziele Linux/Windows/macOS, gleiche
SDK-ZIP, keine Fehler/Skips.

### 9.5 Releaseprofil-Negativproben

`negative-release-config.log`, 3/3: `releaseCheck` ohne Profil scheitert mit
«releaseCheck requires -PreleaseProfile=P3»; unbekanntes Profil `P9`
scheitert mit «Unknown release profile; supported: P3»; fehlender aktueller
CI-Nachweis scheitert im Preflight mit `P3 release preflight failed` und
konkreter `ENOENT`-Ursache. Der gültige Plattformbericht wurde danach mit
identischem SHA wiederhergestellt.

## 10. Eigene Negativproben und MUSS-Findings

### F1 (MUSS) – Übersprungene Browser-/Safari-Belege werden akzeptiert

**Reproduktion:** `build/review-final/probes/verify-release-evidence.cjs`,
Fälle `skipped-browser-case` und `safari-skipped`. Die tatsächlichen
Browser-/Safari-Berichte werden als Kopie geladen und nur um
`result.skipped=1` ergänzt. `tools/release/evidence.cjs#validate` liefert
`{status:'passed'}`; die Logzeilen lauten `NEGATIVE_ACCEPTED
skipped-browser-case` und `NEGATIVE_ACCEPTED safari-skipped`.

**Ursache:** `validate` prüft für Browser-/Safari-Zeilen
`stabilizationCases`, `corpusRegressionCases`, Transport/Client und
Artefaktbindung, aber nicht `skipped===0`/`failures===0` auf der Suite.
`main()` ruft dieselbe Funktion auf; der Task akzeptiert den Beleg ebenfalls.

**Fixtures:** `build/review-final/probes/negative-fixtures/review-negative-skipped-browser-case.json`,
`.../review-negative-safari-skipped.json`. Die Originalberichte wurden nicht
verändert.

### F2 (MUSS) – Partielle Reproduzierbarkeitsliste wird akzeptiert

**Reproduktion:** Fall `repro-file-count` entfernt aus den tatsächlichen 84
`reproducibility.artifacts` den SDK-ZIP-Eintrag und dekrementiert
`shippedFiles` auf 83. `validate` akzeptiert (nur `shippedFiles>30` und
`shippedFiles===Object.keys(artifacts).length`). `main()` prüft den SDK-ZIP
separat, verlangt aber nicht, dass er in der Reproduzierbarkeitsliste
enthalten ist; 83/84 passiert.

**Ursache:** Es gibt keine erwartete vollständige Dateimenge (84) und keine
Prüfung, dass jeder ausgelieferte JVM-/SDK-/TeaVM-/Browserbestandteil in
`reproducibility.artifacts` vorkommt.

**Fixture:** `.../review-negative-repro-file-count.json`.

### F3 (MUSS) – Partielles Browsermanifest wird akzeptiert

**Reproduktion:** Fall `browser-manifest-partial` entfernt 17 der 38
Ressourcen aus `browserManifest.resources` (21 bleiben). `validate` akzeptiert,
weil nur `Object.keys(resources).length>20` geprüft wird; `main()` iteriert
ausschliesslich die im Manifest genannten Ressourcen und vergleicht nicht mit
dem tatsächlichen Verzeichnis `interlis-wasm-teavm/build/web` (38 Dateien).

**Ursache:** Keine Sollmenge/Verzeichnisabgleich; ein unvollständiges
Manifest kann als vollständig gelten.

**Fixture:** `.../review-negative-browser-manifest-partial.json`.

Die tatsächlich ausgelieferten Dateien sind vollständig: eigene Prüfung
7/7, 38/38 Ressourcen bytegleich, Manifest-SHA `a5d41111…`. F3 betrifft die
Fail-Closed-Eigenschaft des Prüfers, nicht die aktuelle Lieferung.

### Weitere Negativproben ohne Befund

- `verify-release-evidence`: 28/32 Mutationen korrekt abgelehnt, darunter
  falscher Commit/Fingerprint, fremdes Artefakt, alte/fehlgeschlagene
  JS-/WASM-/Native-/Client-/Diagrammberichte, fehlende Browser/Safari/UI,
  Performance-Roots/Skip/Semantik, Conformance-FAIL/Assessment, fehlende
  Plattform, fremde Plattform, GitHub-Failure/-Artefakt, SDK-Version,
  Repro-CleanBuilds, Manifest-Version/-Commit/-Tool, inaktiver Guard.
- Corpus/Performance: 12/12 Mutationen abgelehnt (Assessment, fehlende/
  veraltete Laufzeit, Skip, fehlende Warmprobe, LIMITED, Retention,
  Stale-Handle, Artefaktbudget, Observation-Drop, falsche Hardware,
  Budgetanhebung).
- UI: partielle Beobachtungsliste wird abgelehnt.
- TeaVM: fremde Quellen/Core/Doppel-Core abgelehnt.
- Releasekonfiguration: kein/unknown Profil und fehlender CI-Nachweis
  abgelehnt.

## 11. Kumulative Tests und G0–G3

| Gate | Tatsächliches Ergebnis |
|---|---|
| G0 Build/Parser/TeaVM/Pins | `check` Exit 0; 1186 JUnit/223 XML, 0 Fehler/Skips; `orderedTeaVmCheck`; zwei saubere Builds 84/84 bytegleich; Toolpins/Ersetzungen geprüft |
| G1 Semantik/IDE/Browser/Diagramm/WebImage | 3548+24 je JS/WasmGC/Chromium/Firefox/WebKit/Safari/WebImageNode/WebImage-Browser; UI 3/3; Transport 29/Client 7; echte stdio-LSP 7/7 |
| G2 2.3/2.4-Normmatrix | Conformance 577 PASS/16 XFAIL/0 FAIL/0 XPASS/0 ERROR, 21 Normbewertungen; roher Referenzlauf separat 577/1/15 FAIL; Corpus 262/85 regelweise bewertet; ili2c 347/347 |
| G3 CLI/SDK/Integration/Plattform | CLI 39/39, SDK 9/9+ZIP, Legacy 54, Linux/Windows 1186/223, macOS 1186/223, SDK-ZIP bytegleich; **aber `releaseEvidenceCheck`-Fail-Closed F1–F3 offen → G3 FAIL** |

Zusätzliche aktuelle Zahlen: SDK-Prozesse 9, Legacy 54, ZIP
`5a607b4e…`; Node-Client 18, Diagramm 4; Native WebImage 3548+24; UI 3/3;
Safari 27.0; Browser extern 0 Anfragen.

## 12. Offene MUSS-/NOT_RUN-Pflichten

**Offene MUSS-Findings:** F1–F3 oben. Sie sind nicht durch eine
Erwartungsänderung, ein Umetikettieren oder eine Budgetanhebung geschlossen.
Der Produktcode wurde nicht geändert.

**NOT_RUN / nicht wiederholt:**

- Docker-Image-Pull des historischen Corpus: kein Docker lokal; die
  eingefrorenen Bytes, der Index `ff9561b4…`, 366 Originalquellen und 347
  Root-MD5 wurden unabhängig geprüft. Das ist keine aktuelle Image-Pull-Aussage.
- Native Windows-/Linux-Ausführung lokal: nicht möglich; die echten
  GitHub-Jobs/Artefakte desselben Commits wurden über den Collector und die
  Archivdigesten geprüft.
- `/dev/full` war auf macOS nicht verfügbar; der Schreib-/Flushfehler wurde
  mit `ulimit -f 1` als echter Fehlerpfad geprüft (Exit 1, Teilausgabe 512 B).
- Der äussere Transportexit und das endgültige OS-Manifest werden erst nach
  dem regulären OpenCode-Ende geprüft; dieser Bericht nimmt sie nicht vorweg.

## 13. Reproduktion der Proben

```bash
# positiver Gesamtlauf (oben)
./gradlew --no-daemon releaseCheckP3 -PnodeExecutable=…/node
# eigene Proben
node build/review-final/probes/verify-report-counts.cjs
node build/review-final/probes/verify-corpus-performance.cjs
node build/review-final/probes/verify-release-evidence.cjs
node build/review-final/probes/verify-browser-manifest.cjs
node build/review-final/probes/verify-teavm-patch.cjs
node build/review-final/probes/compiler-client-review.cjs
node build/review-final/probes/lsp-stdio-review.cjs
node build/review-final/probes/reference-ili2c-corpus.cjs
zsh  build/review-final/probes/sdk-consumer-review.sh
zsh  build/review-final/probes/cli-format-review.sh
zsh  build/review-final/probes/semantic-closure-review.sh
zsh  build/review-final/probes/negative-release-config.sh
zsh  build/review-final/probes/negative-teavm-inputs.sh
```

Rohlogs: `build/review-final/logs/` (u. a. `releaseCheckP3.log`,
`report-counts.log`, `corpus-performance.log`, `release-evidence-negative.log`,
`browser-manifest.log`, `teavm-patch.log`, `sdk-consumer-review.log`,
`compiler-client-review.log`, `lsp-stdio-review.log`, `cli-format-review.log`,
`semantic-closure-review.log`, `negative-release-config.log`,
`negative-teavm-inputs.log`, `reference-ili2c-corpus.log`); Rohdaten:
`build/review-final/raw/`; Negativfixtures:
`build/review-final/probes/negative-fixtures/`.

## 14. Schlussbewertung

Die funktionalen P3-1–P3-4-Gates, die SDK-/Reproduzierbarkeits-/Browser-/
Plattformnachweise und der vollständige Performancevertrag sind am exakten
sauberen Kandidaten tatsächlich grün. Der positive `releaseCheckP3`-Lauf ist
kein PASS für G3, weil der Nachweisprüfer die verlangte Fail-Closed-Eigenschaft
bei übersprungenen und partiellen Belegen nicht erfüllt. Damit lautet das
ehrliche Gesamturteil **FAIL** für AC-P3-20/G3; die drei MUSS-Findings F1–F3
sind konkret reproduziert und als eigene Negativefixtures erhalten.

Der Reviewprozess wurde ohne Produktänderung, ohne Commit/Push/Release und
ohne Native-Subagents ausgeführt. Der äussere Transport/Manifest ist nach dem
regulären Prozessende separat zu prüfen; er ersetzt weder die Findings noch
deren Reproduktionen.
