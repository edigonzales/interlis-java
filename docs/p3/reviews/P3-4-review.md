# Unabhängiger P3-4-Kandidatenreview – vollständiger Corpus und Belastung

**Fachliches Urteil: PASS für AC-P3-12–16/WP-32 am Kandidaten
`280589065a7900e6b3bb2a338f2cd012865091e1`; keine offenen MUSS-Findings.**
Die kumulativ geforderten P3-1-/P3-2-/P3-3-Gates sind am selben Kandidaten
grün. P3-5 und G3 bleiben ausdrücklich offen. Der äussere Transport- und
Manifestendstatus wird in diesem Bericht nicht vorweggenommen; die formale
Abnahme setzt den nach Prozessende geprüften Transport/Manifest voraus.

- **Kandidat:** `280589065a7900e6b3bb2a338f2cd012865091e1`
  (`fix: run additional Web Image regressions after compiler loading`).
- **Elternstand:** `79a2040f2cefbf56d50f93c039ec7fbba032a20c`.
  `git diff 79a2040..HEAD` zeigt ausschliesslich die dokumentierte
  Zwei-Zeilen-Verschiebung in `interlis-wasm-webimage/src/web/index.html`;
  JVM-/TeaVM-Compilerprodukt und Benchmarkverfahren sind unverändert.
- **Separater sauberer Checkout:** `/private/tmp/interlis-java-P3-4-r1`
  (real: `/private/tmp/interlis-java-P3-4-r1`), ausschliesslich dort gearbeitet.
- **Bericht:** `/Users/stefan/sources/interlis-java/build/review/P3-4-r1/review.md`
  (ausserhalb des Kandidatencheckouts).
- **Rohbelege:** `build/review-r1/` im Kandidatencheckout, einschliesslich
  eigener Proben, JUnit-Zählung, Umgebungsnachweis und Performance-Rohberichte.
- **Sauberkeit vorher/nachher:** HEAD unverändert, `git status --porcelain=v1`
  leer, `git diff --check` leer. Keine Commits, kein Push und keine Änderung
  versionierter Dateien. Zusätzliche Dateien liegen nur unter ignorierten
  `build/`- und `node_modules/`-Pfaden.
- **Python-Sperre:** Kein Python/pip/uv ausgeführt, auch nicht als
  Versionssondierung. Der vorhandene Nachweis
  `build/reports/python-free/environment.json` wurde gelesen: Profil
  `(deny process-exec … python/pip/uv)`, alle Proben `ENOENT` oder `EPERM`,
  Scope „No reachable interpreter“. Der Build meldet nur
  `PYTHON_FREE_SOURCE_PASS 1411 inputs`.
- **Unverändert gelesen:** Root-`AGENTS.md`, `docs/spec/AGENTS.md`,
  `docs/p3/EXECUTION_PLAN.md`, `docs/p3/STATUS.md`,
  `docs/p3/CORPUS_ASSESSMENT.md`, `docs/p3/implementation/P3-4.md`,
  `docs/g2/COMPILER_NORM_DECISIONS.md`, der unveränderte P3-3-Bericht sowie
  die für P3-4 massgeblichen Abschnitte aus 03/04/06/07/09/10/11/13/16/17.

## 1. Umgebung und Werkzeuge

| Bestandteil | Tatsächlich verwendet |
|---|---|
| Hardware | MacBook Air, Apple M1, 8 logische CPUs, 16 GiB (17179869184 Bytes) |
| OS | macOS 15.8.1, Darwin 24.6.0, arm64 |
| Java | Temurin 25.0.3+9 (JVM 25.0.3) |
| Node/npm | Node v24.22.0, npm 11.19.1 (gepinnt) |
| Gradle | Wrapper 9.8.1, `--no-daemon`, Java-25-Launcher |
| Browser | Chromium 156.0.8078.4, Firefox 157.0, Playwright-WebKit 27.2, echtes Safari 27.0 |
| Referenzcompiler | ili2c 5.6.9-bf16026e83f2377153b2e818a4f3838f0a423722, JAR-SHA-256 `37d94573d476fb7a187bf190dc2d5d3130050f46f90e8ab916af95bbffbfd49e` |
| GraalVM/Binaryen | Oracle GraalVM 25.0.3+9.1, Binaryen version_133 |

Der Kandidatencheckout enthielt zunächst keine `node_modules`. Der erste
`checkPortable`-Versuch endete deshalb bei `buildBrowserTools` mit
`Cannot find module 'esbuild'` (Exit 1, Rohlog
`build/review-r1/logs/checkPortable.log`). Mit dem unveränderten Lockfile
wurde `npm ci` ausgeführt (14 Pakete, keine Lockfile-Änderung); der zweite
Lauf ist der gewertete Lauf. Das ist ein Umgebungsfehlversuch, kein
Produktbefund, und bleibt als solcher erhalten.

## 2. Vollständigkeit und Provenienz

### 2.1 Eingefrorene Dateien

| Datei | SHA-256 |
|---|---|
| `manifest.json` | `5d8a0fd384142d00cfb858307ff7a9dd74b0d0833f350a4015fa27d691001e0a` |
| `sources.zip` | `b5f0057cefbfed24312213657ee6043553bf3815ddac5127651c668a610c64c4` |
| `assessment.json` | `3dc2250a759027b1f7ec7de5ddba01bcfb8294098a5ecc23fadd5aaf6baead9f` |
| `budgets.json` | `e9f02473567dc446d51c18a3fa607e38a45b9b7183c099d9890a07b8b8804a4e` |

Der eingefrorene Index `ilimodels.xml` hat unabhängig nachgerechnet
SHA-256 `ff9561b4e248da29c0bde4f65e77c1e22e00e45a4f7c8538756debd11451d93c`
(223013 Bytes) und liegt als einziger zusätzlicher Eintrag im Archiv. Damit
stimmt der historische Indexpin. Der im Manifest dokumentierte
Docker-Image-Manifestdigest
`sha256:29c906322b091c2eccaefc3e69855bc08292c6d3b649f8d2d5d11c13985ef3ba`
wurde gelesen; ein erneuter Image-Pull war mangels lokalem Docker nicht
möglich. Die eigentliche Indexprovenienz ist über den unabhängig geprüften
Indexhash und die 347 Root-MD5-Werte belegt.

### 2.2 Eigene unabhängige Eingangsprüfung

`build/review-r1/probes/verify-corpus.cjs` prüfte nicht nur Soll/Ist-Zahlen,
sondern die Bytes und die dekodierten Texte:

- **27/27 Prüfungen PASS**, 0 Fehler:
  `build/review-r1/logs/verify-corpus.log`,
  `build/review-r1/raw/corpus-provenance.json`.
- 353 Indexrecords: 347 `ili2_3`/`ili2_4`-Wurzeln und 6 INTERLIS-1-Wurzeln
  ausserhalb des Produktumfangs. Manifestwurzeln und Indexwurzeln stimmen
  als Mengen exakt überein (inklusive MD5, Version, Sprache und Datei).
- 366 Manifestdateien, 367 Archiveinträge (366 Quellen + Index), keine
  fehlenden, zusätzlichen oder doppelten Pfade.
- 357 UTF-8- und 9 strikt dekodierbare Windows-1252-Dateien. Jede Datei
  wurde mit `TextDecoder(..., {fatal:true})` dekodiert; Rohbyte-SHA-256,
  Bytezahl, UTF-16-Länge und SHA-256 über UTF-16BE stimmen für alle 366
  Dateien. Kein Ersatzzeichen- oder Fallbackpfad.
- Maximale Closure: 11 Quellen, 247130 UTF-16-Einheiten.
- 347/347 Root-MD5-Werte stimmen mit dem Index und dem Manifest überein.
- 821 Diagnostics negativer Wurzeln: IDs eindeutig, Argumente vorhanden,
  `startOffset`/`endOffset` liegen im dekodierten Quelltext, und die
  `diagnosticIds` stimmen mit den Diagnostics überein. Alle Diagnostics
  liegen innerhalb der jeweiligen expliziten Closure.
- Closure-Vollständigkeit: Für jede der 347 Wurzeln wurde die
  Importrelation innerhalb der Closure nachgeprüft. Keine Wurzel hat eine
  im Korpus auflösbare, aber fehlende lokale Abhängigkeit; der einzige
  externe Importname ist das vordefinierte `INTERLIS`
  (`build/review-r1/raw/closure-completeness.json`).

### 2.3 Eigene negative Eingangsproben

`ReviewCorpusNegative.java` ruft die produktive
`FullCorpus.prepare`-Prüfung mit mutierten Kopien auf. **10/10 PASS**
(`build/review-r1/logs/corpus-negative.log`):

- unveränderte Kopie wird akzeptiert;
- entfernte Wurzel, entfernte Datei, geänderter Textpin, geänderter
  Indexpin, geänderter Archivpin, zusätzlicher Archiveintrag, geänderter
  Root-MD5, geänderte UTF-16-Länge und nicht unterstütztes Encoding werden
  jeweils mit `IllegalArgumentException` abgelehnt.

Damit sind stille Decoder-/Archiv-/Indexänderungen und ein stilles
Modellauslassen im Eingangspfad tatsächlich negativ abgesichert.

## 3. Fachliche Corpusbewertung (AC-P3-12/13)

Die Bewertung ist nicht als Ist-gleich-Erwartung übernommen. Gelesen und
geprüft wurden die Regelbegründungen, die Norm-/Projektentscheidungen, die
öffentlichen Kontrolltests und die tatsächlichen Negativquellen. Die
Kontrollquellen in `assessment.json` wurden mit ihren SHA-256 geprüft:
**13/13 aktuell**.

| Regelgruppe | Wurzeln | Entscheidung / Kontrolle |
|---|---:|---|
| Äussere Typnamen | 56 | bestätigtes striktes Profil; `ScopeNamespaceContractTest` |
| NO OID als Referenzziel | 12 | 2.4-Normprofil, auch CHBase-2-Metaklassen; `NoOidReferenceContractTest` |
| Inkompatible deklarierte Funktionsargumente | 9 | Geometrie→`OBJECTS` bzw. Objektmenge→`ANYSTRUCTURE`; `StructureValueContractTest` |
| Widersprüchliche skalare Metadaten | 7 | `comment`/`ili2db.dispName` bleiben skalar; `SourceMetadataContractTest` |
| Fehlendes `=` in `!!@…` | 5 | echte Parsingdiagnose `ILI-SYN-0002`; `SourceMetadataContractTest` |
| Toleranz null | 3 | Null verboten, positive 2.3-Toleranz bleibt gültig; `GeometryRulesContractTest` |
| Obligatorische undefinierte Struktur | 2 | `ABSTRACT`/konkrete Struktur erforderlich; `StructureValueContractTest` |
| Fehlendes `CIRCULAR` | 2 | kontinuierliche Unterteilung; `NumericSubdivisionContractTest` |
| Obligatorisches UNION-Attribut fehlt in Basis | 2 | jede Basis muss den Wert liefern; `ViewBindingContractTest` |
| SET-Objektkontext | 1 | Hauptausdruck ohne Individualobjekt, `WHERE` mit; `SetConditionContextContractTest` |
| Echte Rollennamenskollision | 1 | tatsächlicher Namensraum, keine topicweite Ausnahme; `RoleNamespaceContractTest` |

Die 13 Regeldefinitionen enthalten zusätzlich die beiden
Namensraum-Richtungen `ROLE_ATTRIBUTE_CONFLICT` und
`ATTRIBUTE_ROLE_CONFLICT` als positive Kontrollen; sie kommen im aktuellen
Corpus nicht vor. Die 11 tatsächlich vorkommenden Gruppen ergeben 100
Regel-Wurzel-Zuordnungen bei 85 negativen Wurzeln; die Zahlen sind wegen
Überlappungen **nicht** addierbar.

Das bewertete Profil erwartet **262 erfolgreiche Compiles und 85 strikte
Quellablehnungen**, alle mit `reasons=[SOURCE_ERRORS]`. Die JVM-/Wasm-/
Browserberichte zeigen am Kandidaten exakt diese Verteilung.

### 3.1 Referenzlauf

Die 347 unveränderten Originaltexte wurden für den Referenzvergleich
explizit dekodiert und mit dem gepinnten ili2c 5.6.9 und `--no-auto`
topologisch geordnet übergeben. Eigener vollständiger Lauf:
**347/347 akzeptiert, Exit 0**, `build/review-r1/logs/ili2c-all.log`,
`build/review-r1/raw/ili2c-all-results.json`. Ein erster Versuch mit
Wurzel-vor-Abhängigkeiten war für ili2c ungültig (fehlende Modelle) und
ist als Fehlversuch erhalten; er ist kein Produktbefund. Der Referenzlauf
belegt die Nicht-Gleichsetzung von ili2c-Akzeptanz und unserem bewerteten
Profil, nicht eine automatische Lockerung.

### 3.2 Diagnostics ausserhalb der angeforderten Closure

Eigene CLI-Probe `closure-mixed.ili`: Die angeforderte Wurzel
`ReviewClosureGood` kompiliert mit Exit 0, obwohl im selben Quelltext das
nicht angeforderte Modell `ReviewClosureBad` einen `INTERLIS.NO_SUCH_TYPE`-
Fehler enthält und dieser als Diagnose sichtbar ausgegeben wird.
`closure-root-bad` mit derselben Datei und der fehlerhaften Wurzel endet
mit Exit 1. Das entspricht dem Closurevertrag: sichtbare Fremddiagnose,
aber keine nachträgliche Gateänderung. Dasselbe Muster ist in den
JVM-/Wasm-/Browser-Captures sichtbar (z. B. CHBase-Diagnostics bei
erfolgreichen Wurzeln).

### 3.3 Nutzerfreigabe

Die Freigabe gilt ausschliesslich `REFSYSTEM BASKET` unmittelbar in
`TYPE MODEL` wie ili2c. Eigene Proben bestätigen positiv
`REFSYSTEM BASKET` in `TYPE MODEL` und negativ `SIGN BASKET` in
`TYPE MODEL`; Topics, konkrete Klassen und die NO-OID-Regel bleiben
gesperrt. Es gibt keine pauschale Korb-/Topic-/Klassenausnahme.

## 4. Geprüfte Korrekturen und öffentliche Fehlerpfade

Alle folgenden Proben sind neu geschrieben und sprechen die öffentliche
CLI bzw. die öffentlichen Verträge an; die Erwartungen stammen aus Norm,
Projektentscheidung und vorhandenen Kontrolltests.

| Probe | Erwartung | Ergebnis |
|---|---|---|
| `qualified24` / `qualified23` | `INTERLIS.URI/NAME/BOOLEAN` in 2.3 und 2.4 | Exit 0, Exit 0 |
| `qualified-unknown` | unbekannter qualifizierter Typ | Exit 1, `ILI-SEM-0001` |
| `type-model-basket` | REFSYSTEM BASKET in TYPE MODEL | Exit 0 |
| `type-model-sign-basket` | SIGN BASKET in TYPE MODEL | Exit 1 |
| `timesystems` | `TIMESYSTEMS.CALENDAR`/`TIMEOFDAYSYS` | Exit 0 |
| `timesystems-unknown` | unbekanntes Timesystem | Exit 1 |
| `ns-valid` | unabhängige Rollen-/Attributnamen, eigenes Ziel | Exit 0 |
| `ns-conflict` | echter Attribut-/Rollenkonflikt | Exit 1, `ATTRIBUTE_ROLE_CONFLICT` |
| `allof-forward` / `allof-reverse` | vollständige importierte `ALL OF`-Vorfahrenkette, beide Quellenreihenfolgen | Exit 0, Exit 0 |
| `generic-ref-ok` | generische numerische Referenz mit CONTEXT/DEFERRED/ROTATION/Einheit | Exit 0 |
| `generic-ref-wrong-unit` | falsche Winkeleinheit | Exit 1, `REFSYSTEM_DIRECTION_UNIT` |
| `generic-ref-missing-context` | fehlender generischer CONTEXT | Exit 1, `GENERIC_CONTEXT_MISSING` |
| `comment-100k-doc` | 100000 Satzzeichen in `!!`-Trivia | Exit 0 |
| `closure-root-good` / `closure-root-bad` | Fremddiagnose vs. Gate | Exit 0 / Exit 1 |

**17/17 eigene CLI-Gegenproben PASS**, `build/review-r1/raw/cli-probes/summary.txt`.
Die Grammatikinventur wurde unabhängig geprüft: 1228 Vorkommen, drei neue
IDs `predefined-URI`, `predefined-NAME`, `predefined-BOOLEAN`, alte IDs
unverändert (`docs/evidence/stabilization/g2-grammar-inventory.json`).

Die Quellenreihenfolge-Grenze ist zusätzlich durch die unveränderte
`RoleNamespaceContractTest` und die öffentlichen Namensraumproben gedeckt:
Eine später deklarierte Assoziation macht eine vorher deklarierte
Unterklasse nicht rückwirkend ungültig; ein echter Konflikt mit einer
bereits verfügbaren Rolle bleibt Fehler. Die unveränderte Conformance
besteht nach der Korrektur wieder 577/16/0.

Lange Satzzeichenkommentare wurden auf JVM (100000 `!!`-Trivia) und
portabel (3548+24 Fälle) geprüft; WebKit und echtes Safari führten die
vollständigen 347 Wurzeln ohne Stackausnahme aus.

## 5. Belastung und Performance (AC-P3-14–16)

**Eigener vollständiger `performanceCheck` am exakten sauberen Kandidaten:
PASS, Exit 0, 24m23s**, `build/review-r1/logs/performanceCheck.log`.
Der Lauf wurde mit `/usr/bin/caffeinate -diu` serialisiert ausgeführt; keine
parallelen Gradle- oder Compilerläufe.

```text
FULL_CORPUS_PERFORMANCE_PASS
{"status":"passed","gitCommit":"280589065a7900e6b3bb2a338f2cd012865091e1",
 "dirty":false,
 "sourceFingerprint":"750b4f5857c6a974c790e0965a9ce670078604d7feb1be264500b5cdab92290e",
 "manifestSha256":"5d8a0fd384142d00cfb858307ff7a9dd74b0d0833f350a4015fa27d691001e0a",
 "roots":347,"runtimes":6,"realCorpusEdits":2000,"skipped":0,"semanticMismatches":0}
```

Der Quellenfingerprint ist frisch für `2805890`; der Elternfingerprint
`c260d5…` wurde nicht übernommen oder umetikettiert. Die 2-Zeilen-Änderung
gegenüber `79a2040` ändert den Fingerprint erwartungsgemäss.

### 5.1 Sechs Laufzeiten, 347 Wurzeln, drei Warmcompiles

Jeder Lauf enthält alle 347 Wurzeln, 262 Erfolge/85 Ablehnungen, die
vollständigen öffentlichen Compiles, Diagramme und ankergebundenen
semantischen Properties. JVM-/Wasm-/Browser-Semantik ist exakt gleich;
keine `LIMITED`-Projektion, keine Skips, keine Traps, keine externen
Browseranfragen. Alle Warmwiederholungen sind vorhanden und innerhalb der
Budgets.

| Laufzeit | Roots | First p95 / max (ms) | Warm p95 / max (ms) | Kalt (ms) | Speicher |
|---|---:|---:|---:|---:|---:|
| JVM | 347 | 60.884 / 344.424 | 47.163 / 130.792 | 675.378 | Heap 1852334440 B |
| WasmGC/Node | 347 | 409.938 / 863.613 | 402.170 / 1121.886 | 102.180 | RSS 1190723584 B |
| Chromium 156.0.8078.4 | 347 | 373 / 749.2 | 364.6 / 796.2 | 51.8 | – |
| Firefox 157.0 | 347 | 786 / 1658 | 555 / 1484 | 1515 | – |
| WebKit 27.2 | 347 | 488 / 927 | 440 / 903 | 100 | – |
| Safari 27.0 | 347 | 469 / 907 | 435 / 907 | 271 | – |

Die JVM-Heapzahl ist die Summe der Peak-Nutzungen aller `HEAP`-Memory-
Pools; für Wasm/Node wird Prozess-RSS ausgewiesen. Für Browser werden
bewusst keine erfundenen RAM-Werte behauptet. Alle Werte liegen unter den
unveränderten Sollbudgets (`docs/evidence/p3/full-corpus/budgets.json`);
kein Budget wurde erhöht.

### 5.2 Editor-, Abbruch- und Retentionsmessungen

| Lauf | Edits | Closure UTF-16 | p95 / max (ms) | Snapshots | Freigaben | alte Revisionen | alte Handles | Abbruch/Erholung |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| JVM | 1000 | 247130 | 104.215 / 697.239 | 4 | 996 | 1000 abgelehnt | 0 akzeptiert | 1001 Checkpoints, 27.98 ms, erholt |
| Wasm/Node | 1000 | 247130 | 823.135 / 1234.669 | 4 | 996 | 1000 abgelehnt | 0 akzeptiert | – |
| Wasm/Node RSS | – | – | – | – | – | – | – | 815906816 B |

Beide Läufe verwenden die grösste Originalclosure (247130 UTF-16-
Einheiten), führen echte Textänderungen und echte Compiles aus und halten
höchstens vier Snapshots. Der JVM-Lauf bricht nach tatsächlichen
Compiler-Checkpoints kooperativ ab und kompiliert anschliessend eine frische
Quelle erfolgreich. Die Wasm-Zeitbudgets sind eingehalten; der frühere
konkurrierende Node-Pilot mit p95 ~3920 ms bleibt als Fehlversuch erhalten
und wurde nicht durch Budgetanhebung „grün“ gemacht.

### 5.3 Beobachtungsdruck

1000 echte Compilerläufe, Queuekapazität 32, 32 retained, 10000 offered,
9968 exakt verworfen; eine zweite unabhängig injizierte Instanz bleibt
unbeeinflusst. Status `passed`, `secondInstanceUnaffected=true`
(`observation-pressure.json`). Run-IDs und Sequenzen bleiben getrennt und
geordnet.

### 5.4 Artefakte

- Compiler-Wasm: 4732430 Bytes raw / 1348017 Bytes gzip,
  SHA-256 `3156202d7d8d804394b0775f2ccbc50dfaf6c3b038c7095100220dd74615fc08`.
- Gesamter Browser-Payload: 9899535 Bytes raw / 2337869 Bytes gzip
  (38 Dateien), innerhalb 20971520/6291456.
- SDK-ZIP: `build/releases/interlis-java-sdk-0.2.0-SNAPSHOT.zip`,
  SHA-256 `5a607b4eb03b5ea46fc9b4840960c793a4c72eeaba6a4a1e97366a28ec254170`
  – identisch mit dem Plattformartefakt auf Windows und Linux.

### 5.5 Eigene negative Performance-Checkerproben

Mit den echten Rohberichten und dem produktiven `evaluate` wurden 18
gezielte Mutationen plus eine Positivkontrolle geprüft: **19/19 PASS**
(`build/review-r1/logs/perf-negative.log`). Abgelehnt werden unter anderem
vertauschte Roots, geänderte Diagnoseargumente, `LIMITED`-Diagramm,
fehlende Warmprobe, falsche Retention/Freigabe, falsche Drop-/Retained-
Zahlen, zu grosse Artefakt-/RSS-Werte, falscher JVM-Patch, fehlende
Safari-Version, externe Browseranfrage, fehlender Abbruch, kalter
Nullprozess, geänderte Assessment-Summe und 346 statt 347 Roots. Die
Positivkontrolle mit den tatsächlichen Berichten besteht.

## 6. Kumulative P3-1-/P3-2-/P3-3-Regression

Alle Läufe am selben sauberen Kandidaten:

| Gate | Tatsächliches Ergebnis |
|---|---|
| `--no-daemon check` | **Exit 0**, 223 JUnit-XML-Dateien, **1186 Testfälle**, 0 Fehler, 0 Skips; u. a. Compiler 770, CLI 241, LSP 53 (`build/review-r1/raw/junit-counts.json`) |
| `checkPortable` | **Exit 0**; TeaVM-JS und WasmGC je **3548+24** Fälle, NodeClient 18, Diagrammadapter 4, strenger TypeScript-/Consumer-Build 24 Outputs |
| `conformanceCheck` | **Exit 0**; Bewertung **577 PASS / 16 XFAIL / 0 FAIL / 0 XPASS / 0 ERROR**; roher Suiteexit separat **1** mit `577/1 XFAIL/15 FAIL` (`build/conformance/runner-exit.txt`, `summary.json`) |
| `checkBrowser` | **Exit 0**; Chromium/Firefox/WebKit je **3548+24**, UI **3/3**, Transport 29/Client 7; echtes **Safari 27.0** mit 29/7 und 3548+24; WebImage Chromium/Firefox/WebKit je 3548+24 |
| `:interlis-wasm-webimage:webImageProbe` | **Exit 0**; GraalVM Web Image, **3548+24**, Status `ok` |
| `sdkProcessCheck` / `sdkPackage` | **Exit 0**; **9** echte SDK-Prozesse, Paket gebaut |
| `legacyCliCheck` | **Exit 0**; **54** echte Legacy-Ersatzprozesse, 44 inventarisierte Optionen |
| `performanceCheck` | **Exit 0**, PASS (Abschnitt 5) |

Die P3-3-Produktdateien sind gegenüber dem abgenommenen Vorfahren
`253d9ca` unverändert: `git diff 253d9ca..HEAD` zeigt für
`interlis-projection/src/main`, `interlis-wasm-teavm/src/main/typescript`,
`interlis-wasm-bridge/src/main`, `client.js`, `worker.js` und die
P3-3-Vertragstests keine Änderung. Die 79a-Integration hat die bereits
abgenommenen P3-3-Dateien erhalten. Der P3-3-Bericht
`docs/p3/reviews/P3-3-review.md` hat unverändert SHA-256
`7b57aeaf6ffefde51e3f23be5a1236148c9dc8c605b218e679f0a7882ef188da`.

Historische 13er-Pins, Suiteoriginale und die 21 Normbewertungen wurden
nicht verändert; die neuen P3-4-Dateien liegen getrennt daneben. Der rohe
Referenzlauf bleibt ausdrücklich ein separater FAIL und wird nicht als
Produkt-PASS umetikettiert.

## 7. Plattform-Zusatznachweis (nicht P3-5-Abnahme)

Der vorhandene GitHub-Lauf **41196296230** am Vorfahren `79a2040` wurde
anhand der Metadaten und der extrahierten ZIPs geprüft:

- Windows-Job `124523032485` und Linux-Job `124523032314`, beide
  `completed/success`.
- Je 223 XML-Dateien, **1186 Testfälle**, 0 Failures, 0 Errors, 0 Skips.
- SDK-ZIP auf beiden OS byteidentisch
  `5a607b4eb03b5ea46fc9b4840960c793a4c72eeaba6a4a1e97366a28ec254170`.
- Metadaten: `platform-jobs-41196296230.json`,
  `platform-artifacts-41196296230.json` im Elternreport.

Das ist ein Zusatzbeleg am Vorfahren, **keine** Plattformmatrix am
Kandidaten `2805890` und **keine** P3-5-/G3-Abnahme. Die finale
Windows-/Linux-/macOS-Matrix am Schlussprodukt bleibt offen.

## 8. Bewertung der Kriterien

| Kriterium | Urteil | Kern |
|---|---|---|
| AC-P3-12 – TEST-007/010, RES | **PASS** | Vollständiger eingefrorener Korpus, 347/366/9/11/247130 unabhängig geprüft; 262/85 fachlich begründet; ili2c-Referenz 347/347; Closuregrenze korrekt |
| AC-P3-13 – NFR-004, INC-009/014/017 | **PASS** | 1000-Edit-Läufe JVM/Wasm auf 247130 UTF-16, Frisch-/Reuse-Verträge und Cachegrenzen 4, 996 Freigaben, 1000 alte Revisionen abgelehnt |
| AC-P3-14 – TEST-008, WASM-006/018 | **PASS** | 347 Wurzeln auf JVM, Node-WasmGC, Chromium, Firefox, WebKit und echtem Safari; keine Skips/LIMITED/Traps; Budgets und Rohwerte dokumentiert |
| AC-P3-15 – NFR-005, WASM-011/015 | **PASS** | Kooperativer JVM-Abbruch nach 1001 Checkpoints, Erholung; Worker-Abbruch/RuntimeFailure/Dispose in Portabel und Browsern; keine gültigen Teilergebnisse |
| AC-P3-16 – LOG-009/010 | **PASS** | 1000 Läufe, Queue 32, 9968 exakte Drops, getrennte Instanzen; keine globalen Listener |
| Kumulativ P3-1/P3-2/P3-3 | **PASS der ausgeführten Gates** | 1186 JUnit, 3548+24 portabel/Browser/Safari/WebImage, 577/16/0, SDK 9/Paket, Legacy 54 |
| P3-4 gesamt | **PASS (fachlich)** | Keine offenen MUSS-Findings; formale Abnahme abhängig vom nach Prozessende geprüften Transport/Manifest |
| P3-5/G3 | **offen** | Nicht Gegenstand und nicht vorweggenommen |

## 9. Nicht blockierende Beobachtungen und Restgrenzen

1. **Historische Zusammenfassung:** `docs/evidence/p3/p3-4-summary.json`
   trägt weiterhin `status: "VERIFIED"` mit dem alten 13er-Profil. Der
   aktuelle operative Status und `implementation/P3-4.md` sagen dagegen
   ausdrücklich „keine P3-4-Abnahme“; die neuen P3-4-Belege liegen unter
   `docs/evidence/p3/full-corpus/`. Das ist historische Evidenz, kein
   aktueller P3-4-PASS und kein MUSS-Finding.
2. **Docker-Image:** Ein erneuter Pull des offiziellen Images war ohne
   Docker nicht möglich. Verifiziert sind der gepinnte Image-Manifestdigest,
   der unabhängig nachgerechnete Indexhash und alle 347 Root-MD5-Werte.
3. **Plattformmatrix:** Der Zusatzbeleg stammt von `79a2040`, nicht vom
   Kandidaten `2805890`; die 2-Zeilen-Differenz betrifft nur den
   WebImage-Testharness. Die finale P3-5-Matrix bleibt fällig.
4. **Elterncheckout:** Eine SDK-Vorbereitung ausserhalb dieses
   Kandidaten (u. a. `205be89` auf einem Geschwisterzweig) wurde nicht als
   Kandidatenimplementierung bewertet.
5. **Performance-Checker-Härtung:** Der Checker verlangt vom Bericht ein
   Boolean-`dirty`, erzwingt aber nicht `dirty=false`. Der tatsächliche Lauf
   ist `dirty=false`; die Aussage ist daher gedeckt, die Härtung bleibt eine
   nicht blockierende Verbesserungsidee.
6. **Gzip-Differenz:** Der Compiler-Wasm-Rohhash ist stabil; die
   Gzip-Grösse weicht gegenüber der Elternvorprüfung um 2 Bytes ab und
   bleibt weit innerhalb des Budgets. Das ist keine Aussage über einen
   anderen Compilerstand.

## 10. Reproduktion und Rohbelege

Wesentliche Kommandos (jeweils im Kandidatencheckout, gepinnte Tools):

```bash
./gradlew --no-daemon -PnodeExecutable=$NODE check
./gradlew --no-daemon -PnodeExecutable=$NODE checkPortable
./gradlew --no-daemon -PnodeExecutable=$NODE conformanceCheck
/usr/bin/caffeinate -diu ./gradlew --no-daemon -PnodeExecutable=$NODE checkBrowser
./gradlew --no-daemon -PnodeExecutable=$NODE \
  :interlis-wasm-webimage:webImageProbe sdkProcessCheck sdkPackage
./gradlew --no-daemon -PnodeExecutable=$NODE legacyCliCheck
/usr/bin/caffeinate -diu ./gradlew --no-daemon -PnodeExecutable=$NODE performanceCheck
```

Eigene Proben und Rohlogs unter `build/review-r1/`:

- `logs/check.log`, `logs/checkPortable-2.log`, `logs/conformanceCheck.log`,
  `logs/checkBrowser.log`, `logs/webimage-sdk.log`,
  `logs/legacyCliCheck.log`, `logs/performanceCheck.log`.
- `logs/verify-corpus.log`, `logs/corpus-negative.log`,
  `logs/ili2c-all.log`, `logs/perf-negative.log`,
  `raw/cli-probes/summary.txt`.
- `raw/performance-summary.json`, `raw/junit-counts.json`,
  `raw/corpus-provenance.json`, `raw/closure-completeness.json`,
  `raw/ili2c-all-results.json`, `raw/environment.txt`, `raw/hashes.txt`.
- Performance-Rohberichte: `build/reports/performance/full-corpus/`
  (`jvm.json`, `wasm.json`, `chromium.json`, `firefox.json`, `webkit.json`,
  `safari.json`, `editor-jvm.json`, `editor-wasm.json`, `artifacts.json`,
  `observation-pressure.json`, `result.json`).
- Browser-/WebImage-Belege:
  `interlis-wasm-teavm/build/browser-results.json`,
  `ui-projection-results.json`, `safari-result.json`,
  `interlis-wasm-webimage/build/webimage-browser/browser-results.json`,
  `interlis-wasm-webimage/build/webimage/result.json`.

Der Bericht ist absichtlich unverändert abgelegt. Es wurden keine
Produkt-, Test-, Build- oder Erwartungsdateien geändert.
