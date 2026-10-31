# Unabhängiger P3-3-Kandidatenreview R4 – Browser und Diagramm

**Fachliches Urteil: PASS ohne offene MUSS-Findings.** Die drei MUSS-Befunde
des R3-Berichts (F5 gemeinsames Mitgliederbudget, F6 vollständiger
`showTypes`-Filter, F7 konsistenter Eröffnungs-/Konfigurationsfingerprint) sind
am Kandidaten mit neuen eigenen RED→GREEN-Proben geschlossen. Die früheren
Befunde F1–F4 bleiben geschlossen. AC-P3-09, AC-P3-10 und AC-P3-11 sind im unten
belegten Umfang PASS; die kumulative P3-1-/P3-2-Regression ist grün. P3-4,
P3-5 und G3 bleiben offen.

- **Kandidat:** `253d9ca837d05cc86959caf04fb2f4bab34c74c0`
  (`fix: enforce diagram member budgets and consistent session fingerprints`).
- **Vergleichsbasis:** `cafcee4` (reiner P3-2-Abnahmedokumentcommit; als Vorfahr
  bestätigt). Der unabhängige P3-2-PASS am Produkt
  `5eac6b64fb352982376f054a765597688d5f975b`
  ([Review](../../../docs/p3/reviews/P3-2-review.md)) bleibt unverändert; dieses
  Review wiederholt ihn nicht, sondern prüft die kumulative Regression am
  Kandidaten.
- **Separater sauberer Checkout:** `/private/tmp/interlis-java-P3-3-r4`
  (real: `/private/tmp/interlis-java-P3-3-r4`), ausschliesslich dort gearbeitet.
- **Bericht:** `/Users/stefan/sources/interlis-java/build/review/P3-3-r4/review.md`
  (ausserhalb des Kandidatencheckouts).
- **Sauberkeit vorher:** HEAD = Kandidaten-SHA, `git status --porcelain=v1`
  leer, `git diff --check` leer.
- **Sauberkeit nachher:** HEAD unverändert, `git status --porcelain=v1` leer,
  `git diff --check` leer. Keine Commits, kein Push, keine Reparatur, keine
  Änderung versionierter Dateien. Zusätzliche Dateien liegen ausschliesslich
  unter ignorierten `build/`, `.gradle/`- und `node_modules/`-Pfaden.
- **Elterncheckout:** `/Users/stefan/sources/interlis-java` war nicht der
  Kandidatencheckout und trug bereits fremde P3-4-Compileränderungen. Sie
  wurden weder bewertet noch verändert; die P3-4-Änderungen sind ausdrücklich
  **nicht** Bestandteil dieses sauberen Kandidaten.
- **Provider:** `launch.json` nennt `provider: deepseek`, `model:
  deepseek-flash` (direkte DeepSeek-API über OpenCode, kein OpenRouter).
- **Python-Sperre:** Kein Python/pip/uv ausgeführt, auch nicht `--version` oder
  als Sondierung. Die vorhandene Launcher-Probe
  `build/reports/python-free/environment.json` wurde gelesen: Profil
  `(deny process-exec … python/pip/uv)`, Proben enden mit `ENOENT` bzw. `EPERM`,
  Scope „No reachable interpreter“. Die Gradle-Läufe melden lediglich
  `PYTHON_FREE_SOURCE_PASS 1380 inputs`.
- **Transport/äusseres Manifest:** Zum Zeitpunkt der Berichtserstellung war der
  äussere Prozess noch nicht beendet. Dieser Bericht nimmt den späteren
  Transport-/Manifestendstatus nicht vorweg und behauptet ihn nicht.
- **Unverändert gelesen:** Root-`AGENTS.md`, `docs/spec/AGENTS.md`,
  `docs/p3/EXECUTION_PLAN.md`, `docs/p3/STATUS.md`,
  `docs/p3/implementation/P3-3.md`, `docs/p3/DIAGRAM_ADAPTER.md`,
  `docs/p3/diagram-adapter-pin.json` sowie die vollständigen Kapitel 10/11 und
  die für P3-3 massgeblichen Abschnitte aus 04/07/09/13/14/16/17. Gelesen wurden
  ausserdem der unveränderte R3-Bericht
  `docs/p3/reviews/P3-3-1ec148b-FAIL.md` (SHA-256
  `9882f89301386370bc0affe438dc40f289fe564a188ddf2203ceb26d94b195c4`), der
  R2-Bericht `/Users/stefan/sources/interlis-java/build/review/P3-3-r2/review.md`
  (SHA-256 `3b18058d3ed03b53487051c4c8ef31a601a0c3aef606509f7765d633ac1c743a`)
  und der R1-Bericht `docs/p3/reviews/P3-3-7440fb9-FAIL.md` (SHA-256
  `c681ba4527c536f8b3d41fe5515b78416defb02f4cc150c84f01a3cf1baa46ed`). Alle
  drei Hashes wurden selbst nachgerechnet.
- **R2/R3-Abgrenzung:** R2 (`3b18058…`) erklärt fachlich PASS, endet aber mit
  Transportexit 1 und äusserem Manifest failed und ist deshalb **keine gültige
  Abnahme**; seine Akzeptanz eines nicht angebotenen `TRANSLATED` genügt
  VIZ-017 nicht. R3 (`1ec148b`) endete laut unverändertem Bericht/Handoff mit
  Transportexit 0 und äusserem Manifest passed, fachlich aber FAIL (F5–F7);
  dieser Bericht bleibt unverändert und wird nicht umetikettiert.

## 1. Kurzurteil

Die breite technische Strecke ist am Kandidaten vorhanden und in eigenen
Proben grün:

- echte TeaVM-WasmGC-/JavaScript- und GraalVM-Web-Image-Ausführung;
- Mehrdatei-/Konfigurationsspiegelung, Worker-Neustart aller Sitzungen, neue
  Session-/Snapshot-/Schema-Handles und Epochenschutz;
- queued/running Cancel, RuntimeFailure, Bootstrapfehler, Dispose und begrenzte
  Queues;
- atomare Quittungsannahme und explizite Freigabe abgelehnter Compiles;
- Root-Selector-DTOs ohne Java-`toString`/`Optional`-Werte;
- echte mehrstufige Übersetzung mit unveränderten IDs, `canonicalName` und
  SourceAnchors;
- begrenzte Nachbarschaft mit Tiefe 0..32, Fokus ohne Geschwisterexpansion,
  Budget-/Abbruchpfad und expliziten externen Randstubs;
- echte SVG-/ELK-/Sprotty-Erzeugung, Escaping, beidseitige Navigation,
  Stale-Schutz und Verwerfen einer verspäteten realen Layoutantwort;
- reale Browser Chromium/Firefox/WebKit sowie echtes Safari, einschliesslich
  eigener Safari-UI-Probe für Übersetzung/Fokus/Tiefe/Navigation.

| Kriterium | Verdikt | Kern |
|---|---|---|
| AC-P3-09 – WASM-009/010/011/017 | **PASS** | Mehrdatei-/Neustart-/Cancel-/RuntimeFailure-/Bootstrap-/Dispose-/Limitpfade, echte Browser/Safari; F7 geschlossen: Eröffnung und `setConfiguration`-Quittung tragen denselben vollständigen Fingerprint, No-op/Restart bleiben stabil. |
| AC-P3-10 – WASM-008/015, INC-012/018 | **PASS** | Exakte Revision-/Kardinalitätsstrings jenseits 2^53, Unicode/UTF-16, Cross-Snapshot-/Revisionsprüfung, begrenzte Snapshot-/Cachehaltung, idempotente Freigabe; verspätete Layout-/Worker-Antworten ersetzen den aktuellen Stand nicht. |
| AC-P3-11 – VIZ-001–018, WASM-014 | **PASS** | F5 (gemeinsames Knotenbudget über alle Compartments) und F6 (`showTypes=false` überall) geschlossen; VIZ-017 mit echten ein- und mehrstufigen Übersetzungen; n-äre ENTITY-Basis, Kardinalitäten, Escaping, Validator, Capabilities. |
| Kumulativ P3-1/P3-2 | **PASS der ausgeführten Suite** | `check` 1171 XML-Fälle ohne Fehler/Skips, Portable JS/WasmGC je 3548, Browser/WebImage/Safari je 3548, SDK 9/Paket, normative Conformance 577 PASS/16 XFAIL/0 FAIL. Der rohe Referenzlauf bleibt separat FAIL. |
| P3-3 gesamt | **PASS (fachlich)** | F1–F7 geschlossen, keine neuen MUSS-Findings. Formale Abnahme zusätzlich abhängig vom nach Prozessende bewerteten Transport/Manifest; dieser Bericht nimmt ihn nicht vorweg. |
| P3-4/P3-5/G3 | **offen** | Nicht Gegenstand; keine Plattform-/Gesamtmatrix behauptet. |

## 2. Die R3-MUSS-Befunde F5–F7 – neu geprüft

Alle Proben sind für R4 neu geschrieben, sprechen öffentliche Verträge an und
sind keine Umbenennungen von Elternproben. Der RED-Vergleich lief mit
demselben Probequelltext gegen den Vorgänger `1ec148b` im vorhandenen
Referenzcheckout `/private/tmp/interlis-java-P3-3-r3`.

### F5 – ein gemeinsames Mitgliederbudget pro Knoten – **PASS**

**Anforderung:** Kapitel 11 §2.2 definiert
`ProjectionBudget(maxNodes,maxEdges,maxMembersPerNode)` als ausdrückliche
Begrenzung mit LIMITED-Meldung; VIZ-016 verlangt die ausdrückliche Meldung des
reduzierten Umfangs.

**Neue Beobachtung (Kandidat):** Das Fixture enthält einen Domänen-DETAILS-
Eintrag, zwei Attribute, zwei Constraints, eine n-äre Assoziation mit zwei
Rollen und eine getrennt geprüfte Parameterliste. Für die Grenzen 1 bis 6 gilt
in jeder Projektion:

- kein Knoten hat mehr als `maxMembersPerNode` Mitglieder, über alle
  Compartments ATTRIBUTES/ROLES/CONSTRAINTS/DETAILS/PARAMETERS hinweg;
- `LIMITED` und `PROJECTION_LIMIT_REACHED` erscheinen genau dann, wenn
  tatsächlich Mitglieder gekürzt wurden; bei ausreichendem Budget bleibt das
  Dokument `COMPLETE` und ohne Limit-Hinweis;
- der `DiagramValidator` akzeptiert jede begrenzte Ausgabe;
- eine Auswahl nur der Assoziation `L` mit Grenze 1 liefert höchstens ein
  Rollenmitglied und `LIMITED`;
- eine Auswahl nur der Klasse `A` mit Grenze 1 liefert genau ein Mitglied und
  `LIMITED`, ohne Geschwister zu expandieren.

**Rohbeobachtung Kandidat:** `BUDGET limit=... completeness=... remaining=...`
im Log; bei 1–3 bleibt der reduzierte Graph `[A, D]`, bei 4–6 `[A, B, D, L]`.

**RED am Vorgänger `1ec148b`:** Dieselbe Probe meldet 12 Fehlschläge, darunter
„every node has at most one shared budget across all compartments“ für die
Grenzen 1, 2 und 3 sowie fehlendes `LIMITED`/`PROJECTION_LIMIT_REACHED` bei
Grenze 2 und 3. Das ist die exakte R3-Beobachtung.

**Nebenwirkung, ausdrücklich bewertet:** `MemberBudget.admit()` setzt das
dokumentweite `limited`-Flag. Dadurch kann ein kleines Mitgliederbudget eines
frühen Knotens spätere, ebenfalls ausgewählte Knoten vollständig aus dem
reduzierten Graphen entfernen (im Fixture bei Grenze 1–3: `[A, D]` statt
`[A, B, D, L]`). Das ist eine Folge der explizit angeforderten Begrenzung, wird
als `LIMITED` mit `PROJECTION_LIMIT_REACHED` gemeldet und verletzt die
Knotenbudget-Grenze nicht. **Kein MUSS-Verstoss.** Nicht separat beziffert wird
die Anzahl der ausgelassenen Elemente; Kapitel 11 §8.1.7 nennt eine
„nachvollziehbare Anzahl ausgelassener Elemente“ als Abnahmeszenario, während
die normative VIZ-016 und ihr Indexeintrag in Kapitel 17 nur `LIMITED` und
keine scheinbar vollständige Ausgabe verlangen. Diese Beobachtung ist
nicht blockierend, wird aber als Restlücke im Szenario festgehalten.

### F6 – `showTypes=false` überall – **PASS**

**Neue Beobachtung (Kandidat):**

- `showTypes=false` entfernt sämtliche `typeLabel`-Werte, einschliesslich
  DETAILS des Domäneneintrags, CONSTRAINT-Mitgliedern, PARAMETERS,
  ATTRIBUTES und übersetzten Typlabels;
- Knoten-IDs, Mitglieds-IDs, Labels, `declaredSymbol`, `effectiveOwner`,
  `inherited`/`overridden`, SourceAnchors, Reihenfolge und Rollenkardinalitäten
  bleiben gegenüber `showTypes=true` unverändert;
- derselbe Effekt ist über den echten Bridge-/Wire-Pfad und den gebauten
  TypeScript-Client sichtbar;
- Budget und Typfilter komponieren: eine gekürzte Ausgabe bleibt ohne
  Typlabels und innerhalb der Mitgliedergrenze.

**RED am Vorgänger `1ec148b`:** Der Java-Probe meldet „showTypes=false removes
every type label“ und „type filter survives a cut“; der Client-Probe meldet
dieselben Fehler zusätzlich auf dem Wire, inklusive übersetzter Typlabels.

### F7 – Eröffnungs- und Konfigurationsfingerprint – **PASS**

**Neue Beobachtung (Kandidat):** Über den echten TeaVM-JS-Compiler und den
gebauten TypeScript-Client:

- zwei unabhängige Sitzungen mit identischer Konfiguration liefern denselben
  Eröffnungsfingerprint;
- ein No-op-`setConfiguration` meldet `changed=false`, die Quittung trägt exakt
  den Eröffnungsfingerprint, und der bestätigte Zustand bleibt unverändert;
- eine reine Cache-Limit-Änderung meldet `changed=true` und einen anderen
  vollständigen Fingerprint; die anschliessende Eröffnung mit derselben
  Konfiguration liefert genau den Quittungsfingerprint;
- die Rückkehr zur exakten Ausgangskonfiguration liefert wieder den
  Eröffnungsfingerprint;
- nach einem Worker-Neustart ist der Fingerprint der neuen Sitzung identisch;
  ein anschliessendes No-op bleibt stabil;
- eine semantische Änderung (`allowedLanguages`) liefert einen neuen
  Quittungsfingerprint, den eine frische Eröffnung exakt wiederholt.

**RED am Vorgänger `1ec148b`:** Der Client-Probe meldet neun Fehlschläge, davon
fünf im Konfigurationsbereich: „no-op receipt carries the opening fingerprint“,
„no-op replacement does not change the confirmed fingerprint“, „restoring the
exact configuration returns the original opening fingerprint“, „a fresh opening
matches the semantic setConfiguration receipt fingerprint“ und „no-op after
restart still matches the opening fingerprint“.

**Bewertung der verbleibenden Java-API-Schicht:** `WorkspaceState.state()` trägt
weiterhin den semantischen Fingerprint, während `ConfigurationReceipt` den
vollständigen Fingerprint liefert. Kapitel 07 §2.3 trennt fachliche
Konfiguration und reine Cachegrenzen ausdrücklich; die Nutzerklärung zu R4
verlangt die Übereinstimmung von Eröffnung und `setConfiguration`-Quittung und
ist erfüllt. Ein Java-Verbraucher, der `WorkspaceState` und `Receipt`
unmittelbar vergleicht, sieht nach einer reinen Cache-Limit-Änderung
unterschiedliche Werte. Das ist eine beobachtete Schichtdifferenz, kein
Verstoss gegen den in R4 bestätigten Client-/Bridge-Vertrag.

## 3. F1–F4 – kumulativ erneut geprüft

Die Proben sind neu für R4; sie sprechen öffentliche Verträge an und kopieren
keine Projekt-JUnit-Methode.

| Befund | Neue R4-Beobachtung | Ergebnis |
|---|---|---|
| F1 Partielle Analyse | Fehlender Import: `PARTIAL`, `UNRESOLVED`-Stub ohne Symbol/Anker, `MODEL_IMPORT`-Kante, `UNRESOLVED_TARGET`; abhängige Deklaration `PARTIAL`, gültiger Nachbar `RESOLVED`, kein erfundener Zielknoten; Validator leer. | **PASS** |
| F2 Explizit leere Auswahl | `selection: []` → `INVALID_REQUEST`; leere Java-Auswahl wird bereits typisiert abgelehnt; fehlende Auswahl verwendet die belegten Wurzelmodelle. | **PASS** |
| F3 Diagrammoptionen | Version 1.0; `visibleKinds`, `inheritedMembers`, `showTypes`, `showConstraints`, `showRoleCardinalities`, `showImports`, `labelPolicy`/`translationModel`, `neighbourhoodDepth`, `budget`, `associationPresentation` wirken; unbekannte Felder, falsche Typen, Zahlenstrings, Budget 0/negativ, Tiefe 33, Tiefe ohne NEIGHBOURHOOD, `translationModel` ohne TRANSLATED und fremde Snapshot-Handles werden typisiert abgelehnt; `COMPACT_BINARY` fällt mit `UNSUPPORTED_PRESENTATION` auf ENTITY zurück und behält drei Rollenkanten. | **PASS** |
| F4 Sitzungsabschluss | Gleichzeitige und wiederholte Abschlüsse desselben Handles koaleszieren; geschlossenes Handle `SESSION_EXPIRED`; fremder Handle `SESSION_EXPIRED`; andere Sitzung bleibt nutzbar. | **PASS** |

## 4. Kumulative Kriterienmatrix und Verträge

### AC-P3-09 – WASM-009/010/011/017

- `CompilerClient` führt den Compiler im Worker aus; die Semantiksuite läuft im
  echten Browser-Wasm-Worker, der Client-/Transportteil ist separat geprüft.
- Mehrdatei-/Konfigurationsspiegel, vollständiges Replay, neue Session-/
  Snapshot-/Schemahandles und Epochenschutz sind durch `browserClientTest`
  18/18 und die eigene Clientprobe (Mehrdatei, Konfiguration, Restart,
  alte Handles) belegt.
- Queued/running Cancel, RuntimeFailure, Bootstrapfehler mit Folgeanfragen,
  Dispose, begrenzte Queue und atomare Batchfehler sind in
  `worker-host.test.cjs`/`compiler-client.test.cjs` und im kumulativen
  Browserlauf grün.
- F7 ist geschlossen (siehe oben).
- `WASM-017`: Bridge und Compiler führen keine Netz-/Dateisuche aus; der
  Browserlauf verwendet einen lokalen statischen Server und den realen
  TeaVM-Wasm-Worker.

### AC-P3-10 – WASM-008/015, INC-012/018

- Exakte Revisionen und grosse fachliche Zahlen bleiben Dezimalstrings
  (`exactDecimal=true`, Kardinalität > 2^53 im Projektions-/Adaptertest).
- Snapshot-Freigabe ist idempotent; abgelehnte Compiles halten ihre Snapshots
  explizit; nach Freigabe liefert derselbe Schema-Handle `SNAPSHOT_EXPIRED`.
- Verspätete Layoutantworten ersetzen die aktuelle Ansicht nicht; die eigene
  UI-Probe hält eine echte Rendererantwort nach dem unveränderten Renderer
  zurück, ändert die bestätigte Quelle und beobachtet Verwerfen plus
  anschliessende gültige Navigation mit der neuen Revision.
- Cross-Snapshot-/Revisionsprüfungen und unveränderte alte Snapshots sind in
  den bestehenden Vertragstests grün.

### AC-P3-11 – VIZ-001–018, WASM-014

- **VIZ-017 (MUSS):** echte englische/französische/deutsche Modelle; ein- und
  mehrstufige Korrespondenzen ändern Anzeigename, Mitglieds- und Typlabel,
  während IDs, `canonicalName`, `SymbolRef`, Kardinalität und SourceAnchor
  unverändert bleiben. Falsche, veraltete, unbekannte und fremde
  Übersetzungsmodelle werden typisiert abgelehnt. `showTypes=false` wirkt auch
  auf übersetzte Typlabels. Alte Konstruktorüberladung bleibt erhalten;
  NEIGHBOURHOOD/TRANSLATED ohne Pflichtparameter werden abgelehnt.
- **VIZ-016/F5:** gemeinsames Mitgliederbudget über alle Compartments; jede
  Kürzung ist `LIMITED` und `PROJECTION_LIMIT_REACHED`; keine scheinbar
  vollständige Ausgabe.
- **F6/VIZ-009:** derselbe Optionssatz steuert die Typdarstellung in allen
  Mitgliedsarten; keine still ignorierte Option.
- **N-äre/attributierte Assoziationen:** ENTITY bleibt die Basis; drei
  Rollenkanten, Rollenname, exakte Kardinalität und Assoziationsattribute
  bleiben erhalten; `COMPACT_BINARY` meldet den erlaubten ENTITY-Fallback.
- **Partielle/limitierte Daten:** fehlender Import als Stub und Kante,
  betroffene Deklaration `PARTIAL`, gültige Nachbarn `RESOLVED`.
- **Renderpfad:** unveränderter gepinnter ELK-/SVG-/Sprotty-Renderer; echte
  SVG-Ausgabe; Navigation über IDs/Symbolidentitäten, nicht über Namen;
  gleicher Snapshot, gleiche Quellrevision und gleicher bestätigter Text.
- **Darstellungsgrenzen:** ältere 1.0-Produzenten ohne Modifikatoren und
  fehlende Aggregationsdiamanten werden als `UNSUPPORTED_PRESENTATION`
  ausgewiesen; keine stille Auslassung.

### Kumulativ P3-1/P3-2

Der Kandidat ist Nachfahre von `cafcee4`; die P3-1-/P3-2-JUnit- und
Evidenzprüfungen sind im `check`-Lauf enthalten. Die normative
Conformance-Bewertung bleibt 577 PASS/16 XFAIL/0 FAIL; der rohe Referenzlauf
bleibt unverändert 577 PASS/1 XFAIL/15 FAIL und wird nicht umetikettiert.
Die P3-2-Korrektur des gemeinsamen Compile-Capture-Budgets ist im Kandidaten
enthalten und in der Suite grün.

## 5. Umgebung, tatsächliche Kommandos und Zähler

| Bestandteil | Tatsächlich verwendet |
|---|---|
| Betriebssystem | macOS 15.8.1 (24H32), arm64 |
| JDK | Temurin `25.0.3+9-LTS`, `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem` |
| Gradle | Repository-Wrapper 9.8.1, jeweils `--no-daemon` |
| Node | `v24.22.0` unter `/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node` |
| npm | 11.19.1; `npm ci` im Modul `interlis-wasm-teavm` (14 Pakete, 0 Vulnerabilities, kein `@ilic`-Paket) |
| Playwright | 1.64.1 |
| Browser | Chromium 156.0.8078.4, Firefox 157.0, WebKit 27.2, echtes Safari 27.0 (`safaridriver`: `Included with Safari 27.0 (20625.1.29.18.28)`) |
| WebImage | GraalVM `25.0.3+9.1`, Binaryen `wasm-opt 133` |
| Conformance | Projektbewertung 577 PASS/16 XFAIL/0 FAIL; roher Referenzlauf 577 PASS/1 XFAIL/15 FAIL, unverändert FAIL |
| Python | nicht verwendet; kein Python/pip/uv |

Alle Kommandos ausschliesslich im separaten Kandidatencheckout. Die
Ergebnislogs liegen unter `build/review-r4/` (ignoriert) und wurden nicht in
versionierte Dateien geschrieben.

| Kommando | Exit | Tatsächliches Ergebnis |
|---|---|---|
| `cd interlis-wasm-teavm && npm ci` (Node 24.22.0) | 0 | 14 Pakete, 0 Vulnerabilities; keine `@ilic`-Installation, keine fingierte npm-Installation. |
| `./gradlew check --no-daemon …` | 0 | `BUILD SUCCESSFUL in 1m 11s`, 46 Tasks. XML selbst gezählt: **1171 Tests, 0 Failures, 0 Errors, 0 Skipped** (220 Suiten). Aufteilung: 991 Modul-JUnit (Compiler 744, LSP 53, CLI 72, Model 16, Projection 19, Repository 41, Bridge 23, Workspace 23) + Evidenz 145 + G2-Assertions 15 + Verification 20. |
| `./gradlew :interlis-wasm-teavm:browserClientTest :interlis-wasm-teavm:diagramAdapterTest --no-daemon …` | 0 | `browserClientTest` **18/18**, `diagramAdapterTest` **4/4**, keine Skips. |
| `./gradlew checkPortable --no-daemon …` | 0 | TeaVM JS **3548**, TeaVM WasmGC **3548**; `BROWSER_TOOLS_PASS` TypeScript 7.0.2 strict, 24 Ausgaben; `PYTHON_FREE_SOURCE_PASS 1380 inputs`; `BUILD SUCCESSFUL in 2m 16s`. |
| `./gradlew checkBrowser --no-daemon …` (GRAALVM_HOME, BINARYEN_HOME, `MOZ_DISABLE_CONTENT_SANDBOX=1`) | 0 | WebImage Chromium/Firefox/WebKit je **3548**; TeaVM Chromium/Firefox/WebKit je **3548** mit **29 Transport-/7 Clientprüfungen**, 14892 Requests; `uiProjectionTest` **3/3**; echtes Safari **3548**, 29/7, 14892 Requests; `BUILD SUCCESSFUL in 3m 2s`. |
| `./gradlew :interlis-wasm-webimage:webImageProbe sdkProcessCheck sdkPackage --no-daemon …` | 0 | WebImageNode **3548**, `runtime=graalvm-web-image`; `SDK_PROCESS_PASS 9 commands`; SDK-ZIP `build/releases/interlis-java-sdk-0.2.0-SNAPSHOT.zip` (34'209'227 Bytes). |
| `./gradlew conformanceCheck --no-daemon …` | 0 | `selected=593 result=PASS rawExit=1`; Projektbewertung `{"PASS":577,"XFAIL":16,"FAIL":0,"XPASS":0,"ERROR":0,"TOTAL":593}`; roher Referenzlauf `Overall result: FAIL (PASS=577, XFAIL=1, FAIL=15, XPASS=0, ERROR=0)`. Keine Umetikettierung. |

### 5.1 Browser- und Safari-Aussagegrenzen

- Alle Browserläufe verwendeten `--no-daemon`; keine automatische
  Retry-/Skiplogik, keine Produkt-/Browserpinänderung und keine
  Timeout-Verlängerung.
- Echtes Safari (Safari 27.0) und Playwright-WebKit 27.2 sind getrennt
  ausgewiesen; Safari wird nicht aus WebKit abgeleitet.
- Die Semantiksuite läuft im echten Browser-Wasm-Worker; der öffentliche
  Worker-/Transportclient hat separate echte Browserprüfungen für Cancel,
  Fehler, Wiederherstellung, Konfiguration, mehrere Quellen, Limits und
  Navigation. Ergebnismanifest: `same TeaVM WasmGC compiler inside a real
  browser Worker; transport tested separately`.
- Der UI-Test verzögert die Antwort **nach** dem unveränderten gepinnten
  ELK-/SVG-Renderer, ändert danach den Quelltext und prüft Verwerfen plus
  anschliessende gültige Navigation. Die eigene UI-Probe beobachtet denselben
  Ablauf in Chromium/Firefox/WebKit; die eigene echte Safari-UI-Probe prüft
  Übersetzung/Fokus/Tiefe/Navigation ohne künstliche Rendererverzögerung.

## 6. Eigene neue positive und negative Gegenproben

Alle Quellen und Rohlogs liegen unter
`/private/tmp/interlis-java-P3-3-r4/build/review-r4/own/` und sind nicht
versioniert. Keine Probe ist eine blosse Umbenennung einer Elternprobe.

| Probe | Quelle (SHA-256) | Ergebnis Kandidat | Ergebnis Vorgänger `1ec148b` | Rohlog |
|---|---|---|---|---|
| `R4ProjectionProbe` (Java, echte Compiler-/Projektion) | `57a5850b6094245230449f1b11efff6092d82e951350a0e962589ddeffd03d94` | **127 bestanden, 0 FAIL** | **115 bestanden, 12 FAIL** (F5/F6) | `R4ProjectionProbe.log` `9b41046af063edc7b26e824c1fa22cd309ed2b7e600501be6ae81bca9d331895`; `R4ProjectionProbe-red-r3.log` `90ee02220eeb0199c816cce9520ccf3bd7c2cc17b7152122c25cea677c56342f` |
| `r4-client-probe.cjs` (Node, echter TeaVM-JS-Compiler + gebauter TS-Client) | `377859867f74b1a2d60073b87c1680df447c5d1b42bab72726b6fd2af12416a8` | **76 bestanden, 0 FAIL** | **65 bestanden, 9 FAIL** (F5/F6/F7) | `r4-client-probe.log` `65ecc21376f8096494e7b59daffa74bfd4d5991e9553f503ddda2cf9c04e6df4`; `r4-client-probe-red-r3.log` `51350478d2aa0c135113ee1898eab631b52ca66454898db8a29027af97250341` |
| `r4-ui-probe.cjs` (Playwright, echter UI-/Rendererpfad) | `55db1556be868e604d0417f6fb68de8eb2121b26af53d652502bc91aaba87b6f` | **3/3 PASS** (Chromium/Firefox/WebKit) | nicht Teil des RED (neue UI-Beobachtung) | `r4-ui-probe.log` `84f42c64acb587efd243947bf31cc5989cc11ca78b2bd58bd69c10c5a8120ebf`; `r4-ui-results.json` `9a65d014d417ede468a426bc6990e73cd3c95384fc2ec632e50470578a7a674b` |
| `r4-safari-ui-probe.cjs` (echtes Safari/WebDriver) | `2cc8d917277e887739da3c208ba6ce1d7c2fededda6f970d511725dbeb0dc527` | **PASS** (Safari 27.0) | nicht Teil des RED | `r4-safari-ui-probe.log` `b3596c3c55afda49650a78cf423b888cd7ce619ca30e38ff15664c018acd902c`; `r4-safari-ui-result.json` |

### 6.1 Was die Proben tatsächlich prüfen

**`R4ProjectionProbe` (127/127):**
Options-API inklusive alter Konstruktorüberladung, Fingerprint, Tiefen 0..32,
33/-1, Tiefe ohne NEIGHBOURHOOD, Übersetzung ohne TRANSLATED, Budget 0/negativ,
leere Java-Auswahl; gemeinsames Mitgliederbudget über DETAILS/ATTRIBUTES/
CONSTRAINTS/ROLES/PARAMETERS mit Grenzen 1..6, Assoziationsrollen und
Einzelauswahl; vollständiger Typfilter mit Identitäts-, Label-, Owner-, Anker-
und Kardinalitätsinvarianz; Fokus ohne Geschwisterexpansion; Nachbarschaft
0/1/2, externe Randstubs, Budget und Abbruch; n-äre ENTITY-Basis mit
COMPACT_BINARY-Hinweisfallback; ein-/mehrstufige Übersetzung mit
ID-/`canonicalName`-/Symbol-/Anker-/Mitglieds-/Typlabel-Invarianz, falscher/
fremder/unbekannter Auswahl; partielle Analyse mit fehlendem Import und
fehlerhafter Deklaration; Validator.

**`r4-client-probe.cjs` (76/76):**
Eröffnungs-/Konfigurationsfingerprint über echten TeaVM-JS-Compiler und
gebauten TS-Client inklusive No-op, Cache-Limit-Änderung, semantischer
Änderung, Rückkehr, frischer Eröffnung und Worker-Neustart; Mehrdatei-/
Konfigurationsspiegel nach Neustart und alte Handle-Ablehnung; gemeinsames
Mitgliederbudget und vollständiger Typfilter über echten Bridge-/Wire-Pfad
inklusive nullbarer Typlabels; mehrstufige Übersetzung mit Snapshot-
Handleabbildung und Identitätsinvarianten; Fokus/Nachbarschaft; Typfehler,
Zahlenstrings, unbekannte Felder, Version 2.0, Budget 0/negativ, Tiefe 33;
explizit leere Auswahl; Root-Selector-Wire für byName mit Version/SourceHint
und bySource, unveränderte `requestedRoots`, strukturierte `resolvedRoots`,
sortierte Gründe, keine Java-`Optional`-/`toString`-Werte; negativer
Root-Fälle; idempotenter/fremder Sitzungsabschluss; Freigabe und
`SNAPSHOT_EXPIRED`.

**`r4-ui-probe.cjs` (3/3):** echter gebauter UI-Pfad, unveränderter gepinnter
Renderer über Query-URL, deutsche Übersetzung mit ID-/Anker-/`canonicalName`-
Invarianten und Mitglieds-/Typlabel, Fokus/Tiefe 0 ohne Geschwisterexpansion,
verspätete reale Rendererantwort nach Quellrevisionsänderung verworfen (alte
Ansicht bleibt, `data-stale=true`), anschliessende Navigation mit der neuen
bestätigten Revision.

**`r4-safari-ui-probe.cjs`:** dieselbe Übersetzung/Fokus/Tiefe/Navigation im
echten Safari 27.0; bewusst keine künstliche Rendererverzögerung, weil die
Probe über WebDriver und nicht über Playwright-Routing läuft. Die
Verzögerungsprüfung bleibt für Chromium/Firefox/WebKit belegt.

## 7. Weitere geprüfte Verträge

| Bereich | Beobachtung |
|---|---|
| TypeScript/Consumer | `buildBrowserTools`: TypeScript 7.0.2 strict, Deklarationen, Bundles, separater Consumer ohne interne Pfadaliases; `BROWSER_TOOLS_PASS` mit 24 Ausgaben. |
| Pins/Lizenzen | npm-Lock exakt (ajv 8.20.0, elkjs 0.11.0, esbuild 0.28.2, libavoid-js 0.5.0-beta.5, playwright 1.64.1, sprotty-protocol 1.4.0, typescript 7.0.2); keine `@ilic`-Installation; MIT (ilic-diagram), EPL-2.0 (ELK/Sprotty), LGPL-2.1-or-later (libavoid) vorhanden; alle vendorten SHA-256 stimmen mit `diagram-adapter-pin.json`; `elk-contract.d.ts` unterscheidet sich von `elkjs@0.11.0` in genau der dokumentierten `NonNullable`-Zeile; kein `skipLibCheck`. |
| libavoid | Externes ESM/Wasm unter `build/browser-tools/vendor/libavoid/`; Importmap bindet den lokalen Pfad; kein Netzrenderer. |
| Diagrammform | Schema 1.0 vollständig für Rahmen/Knoten/Compartments/Mitglieder/Endpunkte/Kantenenden/Kardinalität/Hinweise; UTF-16-Ranges; eindeutige IDs auch für geerbte Mitglieder; `effectiveOwner`; n-äre attributierte Assoziationen mit drei Rollenkanten und Endinformationen; exakte Kardinalität >2^53; DOMAIN_USE/REFERENCE/STRUCTURE_COMPOSITION/INHERITANCE/MODEL_IMPORT/TRANSLATION/VIEW_DERIVATION; Validator. |
| Navigation/Stale | IDs statt Namen; gleicher Snapshot, gleiche Revision und gleicher bestätigter Text; geänderter Text/Revision/Snapshot → `stale`; keine alten Offsets; beidseitige Auswahl über Symbolidentitäten; verspätete Layoutantwort ersetzt die aktuelle Ansicht nicht. |
| Kein zweiter Binder/Gate | Bridge und Client delegieren an Workspace/Engine; Übersetzung nutzt ausschliesslich gebundene `TranslationLink`-Paare und effektive Member; Root-Selektoren und Optionen sind reine DTO-Konvertierung; keine Sprachsemantik im TS-Client. |
| Interne Fehler | `INTERNAL_FAILURE` mit FailureId und sicherer Meldung; keine Java-/JS-Stackdaten als fachliche Diagnose in den geprüften Pfaden. |

## 8. Nicht ausgeführte oder nicht abgeschlossene Pflichten

- **P3-4, P3-5, G3:** nicht Gegenstand; keine Abnahme, keine Gesamtmatrix.
- **Windows x64 / Linux x64:** nicht ausgeführt; gehört P3-5. Keine fremde
  Plattformmatrix als eigene Beobachtung behauptet.
- **Finale Plattform-/Release-Matrix und `releaseCheck`:** nicht ausgeführt;
  gehört P3-5/G3.
- **Äusserer Transport/Manifest:** zum Berichtszeitpunkt nicht beendet; kein
  Endstatus vorweggenommen.
- **P3-4-Corpuscompileränderungen:** ausdrücklich nicht Teil des sauberen
  Kandidaten; nicht bewertet.
- **Safari-UI mit künstlicher Rendererverzögerung:** nicht ausgeführt; echte
  Safari-UI ohne Verzögerung sowie die Verzögerungsprüfung auf
  Chromium/Firefox/WebKit sind getrennt belegt.
- **WebImage-UI-Übersetzung:** nicht separat über die UI bedient; WebImage
  Node und drei echte WebImage-Browser sind je 3548 grün, die UI-Übersetzung
  ist über TeaVM-Browser/Safari belegt.
- **F5-Restbeobachtung:** Anzahl ausgelassener Elemente wird im
  `PROJECTION_LIMIT_REACHED`-Hinweis nicht separat beziffert
  (Kapitel 11 §8.1.7); als Szenariorest notiert, kein MUSS-Verstoss gegen
  VIZ-016/den Index in Kapitel 17.

## 9. Fazit

Der Kandidat besteht die breite JVM-/Portable-/Browser-/Safari-/WebImage-/
SDK- und Conformance-Regression und schliesst F1–F7 mit neuen eigenen
RED→GREEN-Proben:

1. **F5:** `maxMembersPerNode` ist jetzt ein gemeinsames Knotenbudget über alle
   Compartments; Überschreitung erzeugt einen konsistenten reduzierten Graphen
   mit `LIMITED` und `PROJECTION_LIMIT_REACHED`.
2. **F6:** `showTypes=false` entfernt sämtliche Typlabels, einschliesslich
   DETAILS- und CONSTRAINT-Mitgliedern, Parametern und übersetzten Typlabels.
3. **F7:** Eröffnungs- und `setConfiguration`-Fingerprint stimmen über No-op,
   Cache-Limit-Änderung, semantische Änderung und Worker-Neustart überein.

Damit sind **AC-P3-09, AC-P3-10 und AC-P3-11 fachlich PASS** und die
kumulative P3-1-/P3-2-Regression ist grün. Es wurden keine neuen
MUSS-Findings festgestellt. Die formale P3-3-Abnahme setzt zusätzlich den nach
Prozessende bewerteten Transport-/Manifestendstatus voraus; dieser Bericht
nimmt ihn nicht vorweg. P3-4, P3-5 und G3 bleiben offen.
