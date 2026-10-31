# Unabhängiger P3-3-Kandidatenreview R2 – Browser und Diagramm

**Status: PASS für P3-3.** Die vier MUSS-Befunde des ersten Reviews (F1–F4)
sind am Kandidaten geschlossen und mit neuen, eigenen ausführbaren Proben
reproduzierbar grün. AC-P3-09, AC-P3-10 und AC-P3-11 sind erfüllt; die kumulative
P3-1-/P3-2-Regression ist grün. **Kein G3-Abschluss.**
P3-4, P3-5 und G3 bleiben offen.

- **Kandidat:** `eab87de4e79833c1943fc45d2a6b3cf74e94d5b8`
  (`docs: preserve independent P3-3 review failure evidence`; Produktkorrektur
  `7428686`).
- **Vergleichsbasis:** `cafcee4` (reiner P3-2-Abnahmedokumentcommit, als Vorfahr
  bestätigt). Der unabhängige P3-2-PASS am Produkt
  `5eac6b64fb352982376f054a765597688d5f975b` bleibt unverändert; dieses Review
  wiederholt ihn nicht, sondern prüft die kumulative Regression am Kandidaten.
- **Erster P3-3-Review:** `docs/p3/reviews/P3-3-7440fb9-FAIL.md`, unverändert,
  SHA-256 `c681ba4527c536f8b3d41fe5515b78416defb02f4cc150c84f01a3cf1baa46ed`
  (selbst nachgerechnet).
- **Separater Checkout:** `/private/tmp/interlis-java-P3-3-r2`
  (real: `/private/tmp/interlis-java-P3-3-r2`), ausschliesslich dort gearbeitet.
- **Bericht:** `/Users/stefan/sources/interlis-java/build/review/P3-3-r2/review.md`
  (ausserhalb des Kandidatencheckouts).
- **Sauberkeit vorher:** HEAD = Kandidaten-SHA, `git status --porcelain=v1`
  leer, `git diff --check` leer.
- **Sauberkeit nachher:** HEAD unverändert, `git status --porcelain=v1` leer,
  `git diff --check` leer. Keine Commits, kein Push, keine Reparatur, keine
  Änderung versionierter Dateien. Alle zusätzlichen Dateien liegen unter
  ignorierten `build/`-, `.gradle/`- und `node_modules/`-Pfaden.
- **Elterncheckout:** `/Users/stefan/sources/interlis-java` war nicht der
  Kandidatencheckout und trug bereits fremde Änderungen (u. a.
  `InterlisParser.g4`, `SyntaxTreeBuilder.java`, `GraphBuilder.java`,
  `docs/spec/04-...md`, `docs/p3/TYPE_MODEL_BASKET_PROFILE.md`). Sie wurden
  weder als Kandidatenstand bewertet noch verändert. Die P3-4-Compileränderungen
  sind ausdrücklich **nicht** Teil dieses sauberen Kandidaten.
- **Provider:** direkte DeepSeek-API über OpenCode (`launch.json`:
  `provider: deepseek`, `model: deepseek-flash`), kein OpenRouter.
- **Python-Sperre:** `python3`, `python`, `pip` und `uv` sind im Environment
  nicht vorhanden; kein Python-Interpreter wurde ausgeführt. Ein einmaliger
  Shell-Sondierungsaufruf `python3 --version` endete ohne Interpreter/Ausgabe
  und wurde nicht weiter verwendet. Der Gradle-Lauf meldet lediglich
  `PYTHON_FREE_SOURCE_PASS 1377 inputs`.
- **Transport/äusseres Manifest:** Der äussere Prozess war zum Zeitpunkt der
  Berichtserstellung noch nicht beendet. Dieser Bericht nimmt keinen späteren
  Transport- oder Manifestendstatus vorweg.
- **Unverändert gelesen:** Root-`AGENTS.md`, `docs/spec/AGENTS.md`,
  `docs/p3/EXECUTION_PLAN.md`, `docs/p3/STATUS.md`,
  `docs/p3/implementation/P3-3.md`, `docs/p3/DIAGRAM_ADAPTER.md`,
  `docs/p3/diagram-adapter-pin.json`, `docs/p3/reviews/P3-3-7440fb9-FAIL.md`
  sowie die vollständigen relevanten Kapitel 04/07/09/10/11/13/14/16/17.

## 1. Kurzurteil

Die Produktkorrektur `7428686` schliesst die vier im ersten Review
reproduzierten MUSS-Verstösse:

| Nr. | Erster Befund | Unabhängige R2-Beobachtung |
|---|---|---|
| F1 | Partielle Analyse erschien als `COMPLETE`, ohne Stub/Importkante/Hinweis | Fehlender Import liefert `PARTIAL`, `UNRESOLVED`-Stub ohne Symbol/Anker, `MODEL_IMPORT`-Kante, `UNRESOLVED_TARGET`; betroffene Deklaration `PARTIAL`, gültiger Nachbar `RESOLVED`. Fehlerhafte echte Deklaration ebenso. |
| F2 | Explizit leere Auswahl wurde still durch Wurzelmodelle ersetzt | `selection: []` liefert `INVALID_REQUEST`; fehlende Auswahl verwendet weiterhin belegte Wurzeln; `null`/falscher Typ werden an der Bridge abgelehnt. |
| F3 | `options` wurde im Browserpfad still ignoriert | `DiagramOptionsDto`/Schema 1.0 wird transportiert; Budget/Filter/Flags wirken; ungültige Typen/Zahlenfelder `INVALID_REQUEST`; unbekannte Version/Scope/Label-Policy `UNSUPPORTED_OPERATION`; `COMPACT_BINARY` fällt mit Hinweis auf ENTITY zurück. |
| F4 | Zweiter `closeSession` lieferte `SESSION_NOT_FOUND`/`SESSION_EXPIRED` | Wiederholter und gleichzeitiger Abschluss desselben Clienthandles ist idempotent und koalesziert zu genau einem Wire-Aufruf; fremde Handles bleiben ungültig; andere Sitzung bleibt nutzbar. |

| Kriterium | Verdikt | Kern |
|---|---|---|
| AC-P3-09 – WASM-009/010/011/017 | **PASS** | Mehrdatei-/Konfigurationsspiegel, Worker-Neustart aller Sitzungen, neue Handles, laufender/queued Cancel, RuntimeFailure, Bootstrapfehler, dispose, begrenzte Queue; Compiler im echten Worker; keine externen Netzrequests im Fixture. |
| AC-P3-10 – WASM-008/015, INC-012/018 | **PASS** | Exakte Zahlen über 2^53, Unicode/UTF-16, Cross-Snapshot-/Revisionsprüfung, begrenzte Snapshot-/Cachehaltung, explizite Freigabe auch abgelehnter Compiles. |
| AC-P3-11 – VIZ-001–018, WASM-014 | **PASS** | Echte Compilerprojektion, eindeutige IDs/effectiveOwner, n-äre attributierte Assoziationen, alle geforderten Beziehungsarten, Validator, SVG/ELK/Sprotty, Escaping, beidseitige ID-Navigation, Stale-Schutz, LIMITED/Notices. `NEIGHBOURHOOD`/`TRANSLATED` sind ausdrücklich nicht angeboten und werden nicht still ersetzt. |
| Kumulativ P3-1/P3-2 | **PASS** | `check` 1165 Fälle ohne Fehler/Skips, Portable JS/WasmGC je 3548, Browser-/WebImage-Matrix je 3548, Safari 3548, SDK 9/Paket. Normative Conformance in dieser Runde nicht erneut ausgeführt; der P3-2-PASS 577/16/0 bleibt Referenz, der rohe Referenzlauf bleibt separat FAIL. |
| P3-3 gesamt | **PASS** | Keine offenen MUSS-Findings F1–F4; Fähigkeitsgrenzen sind explizit. |
| P3-4/P3-5/G3 | **offen** | Nicht Gegenstand; keine Plattform-/Gesamtmatrix behauptet. |

## 2. F1–F4 mit neuen eigenen Proben

Alle Proben sind neu für R2 geschrieben, sprechen öffentliche Verträge an und
liegen unter dem ignorierten
`/Users/stefan/sources/interlis-java/build/reports/p3/P3-3/r2-own/`
(bzw. kopiert unter `build/reports/p3/P3-3/r2/own/`). Keine Probe ist eine
blosse Umbenennung einer Elternprobe.

### F1 – Partielle Analyse

**Java-Projektion** (`R2ProjectionProbe`, 73/73):
Eine echte `AnalysisSnapshot` mit `IMPORTS Missing` wird über
`CompilerEngines`/`Workspace` erzeugt. Geprüft:

- `coverage` enthält `BINDING:BLOCKED_BY_ERROR`;
- Dokument `inputKind=ANALYSIS`, `completeness=PARTIAL`, nicht `COMPLETE`;
- Stub `Missing`: `EXTERNAL_REFERENCE`, `UNRESOLVED`, `symbol=null`,
  `anchor=null`, keine erfundenen Compartments/Modifier;
- `MODEL_IMPORT`-Kante zeigt auf den Stub;
- `UNRESOLVED_TARGET`-Hinweis vorhanden;
- `C` (Typ `Missing.D`) `PARTIAL`, `Good` `RESOLVED`;
- kein erfundener Knoten `D`;
- Validator akzeptiert das Dokument; Wire-JSON behält `PARTIAL`/`UNRESOLVED`.

Zweite Probe mit einer fehlerhaften echten Deklaration
(`CLASS Broken = x : NoSuchType`) liefert `PARTIAL`, `Broken` `PARTIAL`,
`Fine` `RESOLVED`, keinen erfundenen `NoSuchType`-Knoten. Eine saubere
Analyse bleibt `COMPLETE` ohne Notices.

**Wire-/Clientpfad** (`R2BridgeProbe` 84/84, `R2ClientWireProbe` 86/86):
Dieselben Zustände über den echten TeaVM-JS-Compiler und den gebauten
TypeScript-Client. Zusätzlich: ein abgelehnter Compile (`outcome=rejected`)
liefert einen Snapshot, der als `ANALYSIS`/`PARTIAL` visualisierbar ist.

### F2 – Auswahl

- Fehlende `selection`: belegte Wurzelmodelle, `ok`.
- `selection: []`: `INVALID_REQUEST` (Bridge und Client).
- `selection: null`: `INVALID_REQUEST` an der Bridge.
- `selection` falscher Typ: `INVALID_REQUEST` an der Bridge.
- Fremde Snapshot-Referenz: `SNAPSHOT_MISMATCH`.
- Gleichzeitig `snapshotId` und `schemaId`: `INVALID_REQUEST`.
- Kein Eingang: `INVALID_REQUEST`.
- Unbekanntes Feld im `diagram`-Params: `INVALID_REQUEST`.

### F3 – Versionierte Optionen

Gegen den echten Compiler, Schema `Opt` mit zwei Quellen:

- `budget.maxNodes=1` → `LIMITED`, genau ein Knoten,
  `PROJECTION_LIMIT_REACHED`.
- `maxNodes` als String `"1"`, `0`, `-1`, `1.5` → `INVALID_REQUEST`.
- Unbekanntes Optionsfeld → `INVALID_REQUEST`.
- Fehlendes `schemaVersion` → `INVALID_REQUEST`.
- `schemaVersion:"2.0"` → `UNSUPPORTED_OPERATION`.
- `scope:"NEIGHBOURHOOD"` → `UNSUPPORTED_OPERATION`.
- `labelPolicy:"TRANSLATED"` → `UNSUPPORTED_OPERATION`.
- `visibleKinds` unbekannt/dupliziert → `INVALID_REQUEST`.
- `visibleKinds:["DOMAIN"]` filtert tatsächlich auf Domänenknoten.
- `inheritedMembers:"HIDE"` entfernt das geerbte Mitglied.
- `showTypes:false` entfernt Typlabels.
- `showRoleCardinalities:false` entfernt Mitgliedskardinalitäten; die
  semantischen Endinformationen der Kante bleiben im Dokument.
- `showConstraints:false/true` entfernt/behält Constraint-Compartments.
- `showImports:false` entfernt `MODEL_IMPORT`-Kanten.
- `COMPACT_BINARY` → erfolgreiche ENTITY-Darstellung mit
  `UNSUPPORTED_PRESENTATION`-Hinweis.
- Die vollständige Dokumentation aller Optionen zusammen wird akzeptiert und
  angewandt.
- `capabilities.diagramOptions` nennt Schema 1.0, `SELECTED`/
  `WITH_DEPENDENCIES`, `CANONICAL`, `ENTITY`; nicht angebotene Werte werden
  nicht beworben.

### F4 – Sitzungsabschluss

- Zwei gleichzeitige `closeSession`-Aufrufe plus ein weiterer Abschluss
  ergeben genau **einen** Wire-`closeSession`-Aufruf.
- `currentSessions()` enthält danach nur die andere Sitzung.
- `state`/`compile`/`diagram` auf dem geschlossenen Handle:
  `SESSION_EXPIRED`.
- Ein fremder Client mit demselben Handle erhält `SESSION_EXPIRED`.
- Die andere Sitzung bleibt vollständig nutzbar.
- Ein unbekannter Session-Identifier wird an der Bridge idempotent mit
  `closed:true` beantwortet; andere Operationen darauf bleiben
  `SESSION_NOT_FOUND`.
- Nach `dispose` lehnt der Client neue Sitzungs-/Analyseoperationen mit
  `DISPOSED` ab; `dispose` ist wiederholbar.

## 3. Tatsächlich ausgeführte Kommandos und Resultate

Alle Kommandos mit `--no-daemon` im separaten Checkout. Rohlogs unter
`build/reports/p3/P3-3/` (Kandidat) und
`/Users/stefan/sources/interlis-java/build/reports/p3/P3-3/r2/` (Eltern).

| Kommando | Exit | Tatsächliches Ergebnis |
|---|---|---|
| `cd interlis-wasm-teavm && npm ci` (Node 24.22.0) | 0 | `added 14 packages`, 0 vulnerabilities; kein `@ilic/diagram` im Lock/`node_modules`, keine fingierte Installation. |
| `./gradlew check --no-daemon -PnodeExecutable=…` | 0 | `BUILD SUCCESSFUL in 39s`, 46 Tasks. JUnit: 744 Compiler + 53 LSP + 72 CLI + 16 Model + 15 Projection + 41 Repository + 21 Bridge + 23 Workspace = **985**; Evidenz **145**, G2-Assertions **15**, Verification **20**; zusammen **1165**, 0 Failures/Errors/Skips. |
| `./gradlew :interlis-wasm-teavm:browserClientTest :interlis-wasm-teavm:diagramAdapterTest --no-daemon` | 0 | `browserClientTest` **15/15**, `diagramAdapterTest` **4/4**, 0 Skips. |
| `./gradlew checkPortable --no-daemon` | 0 | `BUILD SUCCESSFUL in 59s`; TeaVM JS **3548**, TeaVM WasmGC **3548**; `BROWSER_TOOLS_PASS` TypeScript 7.0.2 strict; SDK-Modell **1**. |
| `./gradlew checkBrowser --no-daemon` (mit `GRAALVM_HOME`, `BINARYEN_HOME`, `MOZ_DISABLE_CONTENT_SANDBOX=1`) | **1** | WebImage Chromium/Firefox/WebKit je **3548**; TeaVM Chromium **3548** (5.8s), Firefox **3548** (13.4s), WebKit **3548** (13.3s); `SAFARI NOT_RUN Error: safaridriver did not become ready: "TypeError: fetch failed"`. Aggregat rot, weil Safari-Treiber nicht bereit wurde. |
| `./gradlew :interlis-wasm-teavm:safariContractTest --no-daemon` (gezielt, gleicher Kandidat) | 0 | `SAFARI PASS`, echtes Safari 27.0 / `safaridriver` `Included with Safari 27.0 (20625.1.29.18.28)`, **3548** Fälle, 20 Transport-/7 Clientprüfungen, 14892 Requests, `elapsedMs=52832`. |
| `node safari-contract.cjs` direkt (gleicher Build, vor dem gezielten Task) | 0 | `SAFARI PASS`, **3548**, `elapsedMs=32056`; kein Produktunterschied. |
| `./gradlew :interlis-wasm-webimage:webImageProbe sdkProcessCheck sdkPackage --no-daemon` | 0 | GraalVM Web Image Node: `stabilizationCases=3548`, `runtime=graalvm-web-image`; `SDK_PROCESS_PASS 9 commands`; SDK-ZIP erzeugt. |
| `R2ProjectionProbe` (Java) | 0 | **73/73** Checks, 0 Failures. |
| `R2BridgeProbe` (Java, package `guru.interlis.bridge`) | 0 | **84/84** Checks, 0 Failures. |
| `R2ClientWireProbe.cjs` (Node, echter TeaVM-JS-Compiler + gebauter TS-Client/Adapter) | 0 | **86/86** Checks, 0 Failures. |
| eigener Consumer-Typecheck ohne `paths` (`tsc -p tsconfig.json`) | 0 | Import über `@interlis/compiler-browser` und `.../diagram`, strikt, `noEmit`, keine internen Pfadaliases. |

### 3.1 Browserläufe: genaue Abgrenzung

Es gibt **keinen** einzelnen Aggregatlauf, der in dieser Umgebung alle
Browser gleichzeitig grün beendet. Das ist ein Runner-/Lastbefund, kein
Produktbefund, und wird nicht als grüner `checkBrowser`-Exit ausgegeben:

1. **Erster vollständiger `checkBrowser`:** WebImage und TeaVM
   Chromium/Firefox/WebKit je 3548 PASS; Safari-Treiber nicht bereit
   (`fetch failed`), Exit 1.
2. **Gezielter Safari-Task:** PASS, 3548, 52.8s.
3. **Späterer gezielter Matrixlauf** (dieselben Tasks, keine Produktänderung):
   WebImage 3× PASS, Chromium PASS, **Firefox 60s-Timeout**, WebKit PASS;
   der Task endet vor Safari mit Exit 1.
4. **Späterer direkter `browser-contract.cjs`-Lauf:** Chromium PASS,
   Firefox 60s-Timeout, WebKit „target closed“; der Prozess wurde nach
   15 Minuten abgebrochen. Load average lag bei ca. 6.6 auf der
   macOS-arm64-Maschine.
5. Es gab **keine** automatische Retry-/Skiplogik, keine Verlängerung des
   60-s-Limits und keine Produkt-/Browserpinänderung. Die Fehlläufe bleiben
   erhalten.

Damit gilt: Jeder geforderte reale Browser hat am unveränderten Kandidaten
mindestens einen tatsächlichen PASS-Lauf (Chromium/Firefox/WebKit im ersten
`checkBrowser`; Safari im gezielten Task; WebImage in beiden Läufen). Die
späteren Timeouts sind reproduzierbare Umgebungs-/Lastfehler, keine
semantischen Abweichungen. Die früheren Firefox-`browser.newPage`-Abstürze
bleiben als Fehlversuche dokumentiert.

## 4. Neue eigene Proben: Quellen, Umfang, Rohlogs

| Probe | Quelle (SHA-256) | Umfang / Rohlog |
|---|---|---|
| `R2ProjectionProbe.java` | `1123c187186d706a156df7a5ad7c03206fdfba4d9cab0c329aedda90c72ed877` | 73 Checks; `r2-projection-probe.log` |
| `R2BridgeProbe.java` | `95fa8c65f803a38fa59de2b12e7283e000815270072690e8640b82928a2343de` | 84 Checks; `r2-bridge-probe.log` |
| `R2ClientWireProbe.cjs` | `422ce31de377bbac48d0244e35282555d81bb4559032a92286581b240cf73b73` | 86 Checks; `r2-client-wire-probe.log` |

`R2ProjectionProbe` deckt zusätzlich ab:

- vollständige Schemaform, eindeutige Container-/Knoten-/Compartment-/
  Mitglieds-/Kanten-IDs;
- geerbte Mitglieder mit eigener ID, `declaredSymbol` der Basiserklärung,
  `effectiveOwner` des anzeigenden Subtyps; lokale Mitglieder mit lokalem
  Owner;
- n-äre attributierte Assoziation mit Rollen, Assoziationsattribut `note`,
  exakten Endinformationen und Kardinalität `9007199254740993` (untere und
  obere Grenze) im Java-Wert und im JSON;
- `INHERITANCE`, `ASSOCIATION_ROLE`, `REFERENCE`, `STRUCTURE_COMPOSITION`,
  `DOMAIN_USE`, `MODEL_IMPORT`, `TRANSLATION`;
- `TRANSLATION` verbindet Modellrahmen, nicht Klassen;
- `DiagramValidator` akzeptiert echte Dokumente und lehnt doppelte
  Knotenidentitäten ab;
- fremde Snapshot-Auswahl und unbekannte Symbole werden `Rejected`;
- `NEIGHBOURHOOD`/`TRANSLATED` werden `Rejected`, nicht approximiert;
- kleines Budget → `LIMITED` plus `PROJECTION_LIMIT_REACHED`, Dokument bleibt
  valide;
- Kommentaränderung und Einfügen einer fremden Assoziation erhalten
  bestehende Knoten-/Mitglieds-/Kanten-IDs (VIZ-005).

`R2ClientWireProbe` deckt zusätzlich ab:

- echte SVG-/ELK-/Sprotty-Erzeugung aus der realen Compilerprojektion;
- Escaping eines rendererähnlichen Labels (`<script>…` erscheint nicht roh);
- n-äre Gruppierung im Adapter;
- Navigation über Knoten-/Mitglieds-IDs, Rückrichtung über SymbolRefs;
- gleicher Snapshot, gleiche Revision und gleicher bestätigter Text
  erforderlich; geänderter Text/Revision/fremder Snapshot → `stale`;
- synthetischer Partial-Stub bietet keinen erfundenen Textsprung;
- Aggregations-/Kompositionsendmarker werden als
  `UNSUPPORTED_PRESENTATION` gemeldet, die exakten Endinformationen bleiben
  im Dokument;
- ältere 1.0-Produzenten ohne `modifiers` werden als
  `UNSUPPORTED_PRESENTATION` gemeldet;
- vollständige Mehrdatei-/Konfigurationsspiegelung, Neustart aller Sitzungen,
  neue Session-/Snapshot-/Schemahandles trotz identischer deterministischer
  Core-Snapshot-ID, alte Handles `SESSION_EXPIRED`;
- gequeuete abhängige Edits (Revision 1→2→3) und atomare
  Batch-Quittungsannahme;
- beschädigte Batch-Quittung → `RUNTIME_FAILURE`, vollständige
  Wiederherstellung des bestätigten Spiegels, kein Teilcommit;
- begrenzte Queue, Dispose settled alle angenommenen Requests, Bootstrapfehler
  und Folgeanfragen ohne Hänger, expliziter Neustart;
- Snapshot-Retention, explizite Freigabe (auch abgelehnter Compiles),
  Nachrichtenlimit und atomare Initialquellenlimits;
- keine Java-/JS-Stackdaten als Sprachdiagnose; `RUNTIME_FAILURE` bleibt von
  `CANCELLED`, `SESSION_EXPIRED`, `SNAPSHOT_EXPIRED` und Modellfehlern
  getrennt.

**Fehlversuche der Reviewerproben (keine Produktbefunde):** Die erste
Java-Fixture verwendete eine unzulässige Attributkardinalität bzw. einen
zweiten `INTERLIS`-Header und kompilierte deshalb nicht; die erste
Escape-Erwartung verlangte `&gt;`, obwohl der Renderer korrekt nur `<`/`&`
escapet; ein Client-Check übergab `null`/falschen Typ an einer strikt
typisierten API und ein anderer erwartete eine feste Queue-Reihenfolge. Diese
Proben wurden korrigiert; die Bridge-Verträge für `null`/falschen Typ wurden
separat in `R2BridgeProbe` geprüft. Die finalen Rohlogs sind die oben
genannten grünen Läufe.

## 5. Kriterienmatrix AC-P3-09/10/11

### AC-P3-09 – WASM-009/010/011/017

| Anforderung | Beobachtung | Verdikt |
|---|---|---|
| WASM-009: Änderungen, Analysen, Abfragen, Snapshotfreigabe, Schliessen; Mehrdatei-Editfolge | `browserClientTest` 15/15; eigene Clientprobe: zwei Quellen, Konfiguration, mehrere Sitzungen, Replay, Edits, Query/Diagramm, Release, Close; echte Browser-Transportprüfungen 20. | PASS |
| WASM-010: Compiler im eigenen Worker, UI-Thread frei | `worker.js` lädt WasmGC im Worker; Browserresultat `execution: same TeaVM WasmGC compiler inside a real browser Worker`; keine synchrone Compilierung im Hauptthread. | PASS |
| WASM-011: Abbruch/veraltete Antworten | Laufender Cancel → `workerRestarted`, queued Cancel → `notStarted`, vorab abgebrochenes Signal startet nichts; alte Epoche wird verworfen; neue Handles; `WORKER_RESTARTED`/`SESSION_EXPIRED`. | PASS |
| WASM-017: Repositoryauflösung im Host, Fixture ohne Netz | Browser-Contract blockiert alle fremden Origins und besteht; `externalRequests=[]`; Bridge führt kein `fetch`/Dateisystem aus. | PASS |

Zusätzlich geprüft: RuntimeFailure, Bootstrapfehler, `dispose`, begrenzte
Queue, Sitzungs-/Snapshot-/Nachrichtenlimits, ungültige Initialdaten,
Batch-/Konfigurationsfehler atomar, explizite Freigabe.

### AC-P3-10 – WASM-008/015, INC-012/018

| Anforderung | Beobachtung | Verdikt |
|---|---|---|
| WASM-008: versioniertes Protokoll, grosse Zahlen/Revisionen/IDs verlustfrei | Exakte Kardinalität `9007199254740993` in Java und JSON; Revisionen/IDs als Strings; Unicode/Emoji/CRLF-Anker; `SNAPSHOT_MISMATCH` bei fremder Revision. | PASS |
| WASM-015: begrenzte Snapshot-/Cachehaltung, explizite Freigabe | `maxRetainedSnapshots=1`: zweite Analyse `LIMIT_EXCEEDED`; Release idempotent; Kapazität wiederverwendbar; abgelehnter Compile belegt Kapazität und ist freigebbar; Benchmark schliesst Sitzung im Fehlerpfad. | PASS |
| INC-012: alte SymbolRefs/SourceRanges typisiert fehlschlagen | Fremder Snapshot/Symbol → `Rejected`/`SNAPSHOT_MISMATCH`; alte Navigation → `stale`; alte Schema-/Snapshot-Handles → `SNAPSHOT_EXPIRED`. | PASS |
| INC-018: veraltete Ergebnisse ersetzen nichts | Host verwirft alte Epochen; Client prüft Generation/Handle/Text; Diagramm-/Navigationsprüfung verlangt gleichen Snapshot, gleiche Revision und gleichen Text; keine alten Offsets. | PASS |

### AC-P3-11 – VIZ-001–018, WASM-014

| ID | Beobachtung | Verdikt |
|---|---|---|
| VIZ-001 | Eine rendererunabhängige Projektion; dieselbe Struktur wird in SVG/ELK/Sprotty und im Dokument verwendet. | PASS |
| VIZ-002 | Modell/Topic/Klasse/Domäne/Vererbung im echten Compilerfixture; Navigation vorhanden. | PASS |
| VIZ-003 | Partielle Analyse mit Stub/Importkante/Hinweis; keine erfundene Zieldeklaration. | PASS |
| VIZ-004 | `snapshotId`, `sourceRevisions`, Ankerrevisionen im Dokument und in der Navigation geprüft. | PASS |
| VIZ-005 | Kommentaränderung und fremde Assoziation erhalten bestehende IDs (eigene Probe). | PASS |
| VIZ-006 | Identitäten sind Symbol-/Element-IDs, nicht Namen; gleichnamige Elemente würden nicht über Stringvergleich verbunden. | PASS |
| VIZ-007 | N-äre, attributierte Assoziation mit allen Rollen und Endinformationen. | PASS |
| VIZ-008 | Geerbte/lokale Mitglieder, effektive Reihenfolge, eigener `effectiveOwner`. | PASS |
| VIZ-009 | Filter wirken über denselben Optionsvertrag; `visibleKinds`, Typen, Kardinalitäten, Constraints, Imports, Inherited. | PASS |
| VIZ-010 | Layout/Scroll ändern keine Dokument-/Quellwerte; `render` liefert eine neue unveränderliche Ansicht. | PASS |
| VIZ-011 | Fehlende Rendererfähigkeiten (Aggregationsdiamanten, ältere Produzenten ohne Modifier) werden explizit als `UNSUPPORTED_PRESENTATION` gemeldet; semantische Endinformationen bleiben erhalten. | PASS |
| VIZ-012 | Escaping im echten Renderer geprüft; Modelltext kann keine Direktiven einschleusen. | PASS |
| VIZ-013 | Deterministische Mermaid-/PlantUML-Ausgabe durch Projektvertragstest (`DiagramProjectionTest`) abgedeckt; JSON-Ausgabe deterministisch. | PASS |
| VIZ-014 | Gepinnter Sprotty-/elkjs-Renderer wird tatsächlich verwendet; keine neue Fachlogik im Renderer. | PASS |
| VIZ-015 | Kein GLSP-Server als Voraussetzung; lesende Projektion ohne GLSP. | PASS |
| VIZ-016 | Budget erzeugt `LIMITED` mit `PROJECTION_LIMIT_REACHED`, keine scheinbar vollständige Ausgabe. | PASS |
| VIZ-017 | Kanonische Namen bleiben Identität; `TRANSLATED` ist **nicht angeboten** und wird ausdrücklich mit `UNSUPPORTED_OPERATION` abgelehnt, nicht still ersetzt. Damit ist die Option im R2-Sinn „angewandt oder unsupported“ behandelt; die eigentliche P2-Sprachumschaltung ist als deklarierte Fähigkeitsgrenze nicht implementiert. | PASS (Option nicht angeboten und explizit unsupported; kein stilles Default) |
| VIZ-018 | Navigation verlangt gleichen Snapshot, gleiche Revision und gleichen bestätigten Text; alte Offsets werden nicht angewandt; verspätete Layoutantworten werden an der Generation/Handle/Text-Prüfung verworfen. | PASS |
| WASM-014 | Keine stillen Auslassungen: nicht angebotene Optionen/Capabilities werden gemeldet; Aggregations-/Modifier-Grenzen explizit. | PASS |

**Fähigkeitsgrenzen, offen ausgewiesen:**

- `NEIGHBOURHOOD` und `TRANSLATED` sind nicht implementiert; die
  Capabilities listen nur `SELECTED`/`WITH_DEPENDENCIES` und `CANONICAL`.
  Beide werden mit `UNSUPPORTED_OPERATION` abgelehnt. Das ist kein stilles
  Default und kein ignoriertes Feld.
- Der gepinnte Renderer zeichnet keine Aggregations-/Kompositionsdiamanten;
  die Kante und die exakten Endinformationen bleiben im Dokument, der
  Adapter meldet `UNSUPPORTED_PRESENTATION`.
- Ältere 1.0-Produzenten ohne `modifiers` werden als
  `UNSUPPORTED_PRESENTATION` gemeldet; `ABSTRACT` wird nicht aus Labels
  erfunden.
- `COMPACT_BINARY` fällt mit Hinweis auf `ENTITY` zurück; das ist die in
  Kapitel 11 §4.2 vorgesehene verlustfreie Standarddarstellung.

## 6. TypeScript, Pins, Lizenzen, Rendererquellen

- **Strikter Build:** `tsconfig.json` mit `strict`, `noEmit`, ohne
  `skipLibCheck`; `build-browser-tools.cjs` prüft, emittiert Deklarationen
  und bundelt ESM/CJS. `BROWSER_TOOLS_PASS` mit TypeScript 7.0.2, esbuild
  0.28.2, ajv 8.20.0, elkjs 0.11.0, sprotty-protocol 1.4.0, libavoid-js
  0.5.0-beta.5.
- **Separater Consumer:** eigener `consumer.ts` importiert
  `@interlis/compiler-browser` und `.../diagram` über die Paket-Exports,
  ohne `paths` und ohne interne Aliasnamen; `tsc` Exit 0. Die vom Build
  erzeugten `types/compiler-client.d.ts`, `types/diagram-adapter.d.ts` und
  Vendor-Deklarationen sind enthalten.
- **Npm-Lockpins:** ajv 8.20.0, elkjs 0.11.0, esbuild 0.28.2,
  libavoid-js 0.5.0-beta.5, playwright 1.64.1, sprotty-protocol 1.4.0,
  typescript 7.0.2 — jeweils exakt im Lock.
- **Keine fingierte Installation:** `@ilic/diagram`/`@ilic/compiler-wasm`
  fehlen im Lock und in `node_modules`; `npm view @ilic/diagram@0.1.2`
  liefert E404. Die Quellen sind aus den gepinnten Gitcommits vendort.
- **Vendor-Pins:** Alle sieben `vendoredFiles` stimmen mit `sha256` und
  `gitBlob` überein. Zusätzlich wurden die Upstream-Rohdateien an den
  gepinnten Commits geladen: `index.ts`, `libavoid-router.ts`,
  `libavoid-js.d.ts`, `compiler-contract.d.ts` und das Root-`LICENSE`
  stimmen bytegenau; `ELK-LICENSE.md` stimmt mit `elkjs@0.11.0/LICENSE.md`.
- **ELK-Korrektur:** Diff gegen `node_modules/elkjs/lib/elk-api.d.ts` ist
  genau eine Zeile (`T['children'][number]` →
  `NonNullable<T['children']>[number]`); Runtime unverändert. Kein
  `skipLibCheck`.
- **Lizenzen:** MIT (ilic-diagram), EPL-2.0 (ELK/Sprotty), LGPL-2.1
  (libavoid), MIT (ajv) werden mitgeliefert; libavoid liegt als externes
  ESM/Wasm samt `index.js`, `libavoid.wasm` und `LICENSE` vor. Die
  HTML-Importmap bindet `libavoid-js` auf den lokalen Pfad; kein
  Netzrenderer.
- **Schema-Pins:** DiagramDocument 1.0 `105e7eeb…`, DiagramOptions 1.0
  `abd41d39…`; beide werden im Build geprüft und ausgeliefert.
- **Rendererquellen unverändert:** `index.ts`, `libavoid-router.ts`,
  `libavoid-js.d.ts`, `compiler-contract.d.ts`, `LICENSE` entsprechen den
  gepinnten Upstreams; nur die dokumentierte ELK-Typkorrektur.

## 7. Kumulative P3-1-/P3-2-Regression

- `check` umfasst die vollständigen JVM-Module einschliesslich Compiler
  (744), LSP (53), CLI (72), Workspace (23), Projection (15), Repository
  (41), Bridge (21), Model (16), Evidenz (145), G2 (15), Verification (20):
  **1165 Fälle, 0 Failures/Errors/Skips**.
- Die unveränderte 3548er-Semantiksuite läuft auf TeaVM JS, TeaVM WasmGC,
  WebImageNode und in den realen Browserläufen; keine Fälle/Erwartungen
  reduziert.
- Explizite Client-/API-Guards: 15 Worker-/Client-Tests, 4 Adaptertests,
  20 echte Transportprüfungen pro Browser, 7 Client-UI-Prüfungen; eigene
  Proben 73 + 84 + 86 = **243 Checks** (ohne die bereits im ersten Lauf
  dokumentierten Probe-Autorenfehler).
- **Normative Conformance:** in dieser Runde **nicht erneut ausgeführt**.
  Der letzte unabhängige P3-2-PASS bleibt 577 PASS / 16 XFAIL / 0 FAIL; der
  rohe Referenzlauf bleibt separat FAIL und wird nicht umetikettiert.
- **SDK:** `sdkProcessCheck` 9 Kommandos und `sdkPackage` in dieser Runde
  erfolgreich; das ist eine Regressionsprüfung, keine P3-5-Abnahme.

## 8. Nicht ausgeführt oder nicht abgeschlossen

- **P3-4, P3-5, G3:** nicht Gegenstand; keine Abnahme, keine Gesamtmatrix.
- **Windows x64 / Linux x64:** nicht ausgeführt; gehört P3-5.
- **Finale Plattform-/Release-Matrix:** nicht behauptet.
- **Normative Conformance:** nicht erneut ausgeführt (siehe oben).
- **Ein einziger grüner `checkBrowser`-Aggregatlauf:** in dieser Umgebung
  nicht erreicht; die geforderten Browserläufe wurden einzeln/aufgeteilt
  tatsächlich bestanden. Die späteren Firefox-/WebKit-Fehler sind als
  Last-/Runnerfehler dokumentiert, nicht als Produkt-PASS umetikettiert.
- **Künstlich verzögerte Layoutantwort im echten Browser:** nicht separat
  instrumentiert; die Schutzlogik ist im Code und über die
  Snapshot-/Navigations-/Textproben geprüft, nicht mit künstlicher
  Rendererverzögerung.
- **Äusserer Transport/Manifest:** zum Berichtszeitpunkt nicht beendet; kein
  Endstatus vorweggenommen.

## 9. Fazit

Der Kandidat `eab87de4e79833c1943fc45d2a6b3cf74e94d5b8` besteht die
kumulative JVM-/Portable-/WebImage-/SDK-Regression und die eigenen
Diagramm-/Worker-Proben. Die vier MUSS-Befunde des ersten Reviews sind
geschlossen: partielle Analysen sind sichtbar partiell, die leere Auswahl wird
abgelehnt, versionierte Optionen wirken oder werden ausdrücklich abgelehnt,
und der Sitzungsabschluss ist idempotent. Es wurden keine neuen
reproduzierbaren MUSS-Verstösse gefunden. Nicht angebotene Darstellungsoptionen
(`NEIGHBOURHOOD`, `TRANSLATED`) und fehlende Rendererendmarker sind offen
gemeldet; sie werden nicht still durch Defaults ersetzt.

**Ergebnis: PASS für P3-3.** AC-P3-09, AC-P3-10 und AC-P3-11 sind erfüllt; die kumulative
P3-1-/P3-2-Regression ist grün. P3-4, P3-5 und
G3 bleiben offen. Der nächste fachliche Schritt ist die P3-4-Corpus-/
Belastungsarbeit bzw. die P3-5-Gesamt-/Plattformmatrix; dieser Bericht erteilt
keine G3-Abnahme.
