# Unabhängiger P3-3-Kandidatenreview R3 – Browser und Diagramm

**Status: FAIL für P3-3.** AC-P3-10 ist im unten belegten Umfang PASS.
AC-P3-09 ist wegen eines reproduzierbaren MUSS-Verstosses im bestätigten
Konfigurationsspiegel FAIL: ein No-op-`setConfiguration` ändert den exponierten
`configurationFingerprint` von der fachlichen auf die vollständige Form (F7).
AC-P3-11 ist wegen zweier reproduzierbarer MUSS-Verstösse im
Diagramm-Optionsvertrag FAIL: `maxMembersPerNode` wird nicht als gemeinsames
Knotenbudget durchgesetzt (F5), und `showTypes=false` wird für
DETAILS-/CONSTRAINT-Mitglieder still ignoriert (F6). Die vier Befunde des
ersten Reviews (F1–F4) sind am Kandidaten mit neuen eigenen Proben geschlossen;
die neuen Übersetzungs-, Nachbarschafts-, Root-Selector- und UI-Verträge sind
grün. **Kein G3-Abschluss.** P3-4, P3-5 und G3 bleiben offen.

- **Kandidat:** `1ec148babdd0179a86e05d5051f55626ced63031`
  (`feat: project translated labels and bounded neighbourhoods safely`).
- **Vergleichsbasis:** `cafcee4` (reiner P3-2-Abnahmedokumentcommit; als
  Vorfahr bestätigt). Der unabhängige P3-2-PASS am Produkt
  `5eac6b64fb352982376f054a765597688d5f975b`
  ([Review](../docs/p3/reviews/P3-2-review.md)) bleibt unverändert; dieses
  Review wiederholt ihn nicht, sondern prüft die kumulative Regression am
  Kandidaten.
- **Separater Checkout:** `/private/tmp/interlis-java-P3-3-r3`
  (real: `/private/tmp/interlis-java-P3-3-r3`), ausschliesslich dort gearbeitet.
- **Bericht:** `/Users/stefan/sources/interlis-java/build/review/P3-3-r3/review.md`
  (ausserhalb des Kandidatencheckouts).
- **Sauberkeit vorher:** HEAD = Kandidaten-SHA, `git status --porcelain=v1`
  leer, `git diff --check` leer.
- **Sauberkeit nachher:** HEAD unverändert, `git status --porcelain=v1` leer,
  `git diff --check` leer. Keine Commits, kein Push, keine Reparatur, keine
  Änderung versionierter Dateien. Zusätzliche Dateien liegen ausschliesslich
  unter ignorierten `build/`-, `.gradle/`- und `node_modules/`-Pfaden.
- **Elterncheckout:** `/Users/stefan/sources/interlis-java` war nicht der
  Kandidatencheckout und trug bereits fremde P3-4-Compileränderungen. Sie
  wurden weder bewertet noch verändert; die P3-4-Änderungen sind ausdrücklich
  **nicht** Bestandteil dieses sauberen Kandidaten.
- **Provider:** `launch.json` nennt `provider: deepseek`, `model: deepseek-flash`
  (direkte DeepSeek-API über OpenCode, kein OpenRouter).
- **Python-Sperre:** Kein Python/pip/uv ausgeführt, auch nicht `--version` oder
  als Sondierung. Die vorhandene Launcher-Probe
  `build/reports/python-free/environment.json` wurde gelesen: Profil
  `(deny process-exec ... python/pip/uv)`; alle Proben enden mit `ENOENT` bzw.
  `EPERM`, Scope „No reachable interpreter“. Die Gradle-Läufe melden lediglich
  `PYTHON_FREE_SOURCE_PASS 1380 inputs`.
- **Transport/äusseres Manifest:** Der äussere Prozess war zum Zeitpunkt der
  Berichtserstellung noch nicht beendet (`process-result.json` noch nicht
  vorhanden). Dieser Bericht nimmt keinen späteren Transport- oder
  Manifestendstatus vorweg.
- **Unverändert gelesen:** Root-`AGENTS.md`, `docs/spec/AGENTS.md`,
  `docs/p3/EXECUTION_PLAN.md`, `docs/p3/STATUS.md`,
  `docs/p3/implementation/P3-3.md`, `docs/p3/DIAGRAM_ADAPTER.md`,
  `docs/p3/diagram-adapter-pin.json`,
  `docs/p3/reviews/P3-3-7440fb9-FAIL.md` (SHA-256
  `c681ba4527c536f8b3d41fe5515b78416defb02f4cc150c84f01a3cf1baa46ed`
  selbst nachgerechnet) sowie die vollständigen Kapitel 10/11/14 und die für
  P3-3 massgeblichen Abschnitte aus 04/07/09/13/16/17. Der R2-Bericht
  `/Users/stefan/sources/interlis-java/build/review/P3-3-r2/review.md` wurde
  gelesen; seine SHA-256 ist
  `3b18058d3ed03b53487051c4c8ef31a601a0c3aef606509f7765d633ac1c743a`.
  R2 ist wegen Transportexit 1/Manifest failed **keine gültige Abnahme** und
  wird hier nicht als PASS verwendet.

## 1. Kurzurteil

Die breite technische Strecke ist am Kandidaten vorhanden und in eigenen
Proben grün:

- echte TeaVM-WasmGC-/JavaScript- und GraalVM-Web-Image-Ausführung;
- Mehrdatei-/Konfigurationsspiegelung mit Ausnahme des Fingerprintfehlers F7;
  Worker-Neustart aller Sitzungen, neue Session-/Snapshot-/Schema-Handles,
  Epochenschutz auch bei deterministisch gleichen Core-IDs;
- queued/running Cancel, RuntimeFailure, Bootstrapfehler, disposal und
  begrenzte Queues;
- atomare Quittungsannahme und explizite Freigabe abgelehnter Compiles;
- Root-Selector-DTOs ohne Java-`toString`/`Optional`-Werte;
- echte mehrstufige Übersetzung mit unveränderten IDs, `canonicalName` und
  SourceAnchors;
- begrenzte Nachbarschaft mit Tiefe 0..32, Fokus ohne Geschwisterexpansion,
  Budget-/Abbruchpfad und expliziten externen Randstubs;
- echte SVG-/ELK-/Sprotty-Erzeugung, Escaping, beidseitige Navigation,
  Stale-Schutz und Verwerfen einer verspäteten realen Layoutantwort;
- reale Browser Chromium/Firefox/WebKit sowie echtes Safari, einschliesslich
  einer eigenen Safari-UI-Probe für Übersetzung/Fokus/Tiefe/Navigation.

Die Abnahme scheitert dennoch an drei konkreten, unabhängig reproduzierten
Vertragsverstössen:

| Nr. | Schwere | Bereich | Kern |
|---|---|---|---|
| F5 | **MUSS** | Diagrammbudget | `maxMembersPerNode=1` liefert drei Mitglieder über DETAILS/ATTRIBUTES/CONSTRAINTS, `completeness=COMPLETE` und `notices=[]`; kein `LIMITED`, keine Reduktion. |
| F6 | **MUSS** | Diagrammoptionen | `showTypes=false` entfernt Attributtypen, lässt aber DETAILS (`declared type: TEXT`) und CONSTRAINT (`MandatoryConstraint`) mit Typlabel stehen; die Option wird für diese Mitglieder still ignoriert. |
| F7 | **MUSS** | Konfigurationsspiegel | Ein No-op-`setConfiguration` meldet `changed=false`, ändert aber den vom Client exponierten `configurationFingerprint` von der fachlichen auf die vollständige Form; `same=false`. |

| Kriterium | Verdikt | Kern |
|---|---|---|
| AC-P3-09 – WASM-009/010/011/017 | **FAIL** | Die Mehrdatei-/Neustart-/Cancel-/RuntimeFailure-/Bootstrap-/Dispose-/Limitpfade und echten Browser/Safari-Läufe sind grün; F7 verletzt jedoch den bestätigten fachlichen Konfigurationsfingerprint im öffentlichen Client-/Bridge-Pfad. |
| AC-P3-10 – WASM-008/015, INC-012/018 | **PASS** | Exakte Revision-/Kardinalitätsstrings jenseits 2^53, Unicode/UTF-16, Cross-Snapshot-/Revisionsprüfung, begrenzte Snapshot-/Cachehaltung, idempotente Freigabe, auch abgelehnter Compiles; verspätete Layout-/Worker-Antworten ersetzen den aktuellen Stand nicht. |
| AC-P3-11 – VIZ-001–018, WASM-014 | **FAIL** | F5 (Budget/VIZ-016, Kap.11 §2.2) und F6 (`showTypes`, Kap.11 §2.2/VIZ-009). Die übrigen geprüften Visualisierungsverträge sind grün. |
| Kumulativ P3-1/P3-2 | **PASS der ausgeführten Suite; F7 offen** | `check` 1170 XML-Fälle ohne Fehler/Skips, Portable JS/WasmGC je 3548, Browser/WebImage/Safari je 3548, SDK 9/Paket, normative Conformance 577 PASS/16 XFAIL/0 FAIL. Der rohe Referenzlauf bleibt separat FAIL. F7 ist ein zusätzlicher gemeinsamer Workspace-/Bridge-Konfigurationsvertrag und wird nicht als P3-2-PASS umetikettiert. |
| P3-3 gesamt | **FAIL** | F5/F6/F7; keine Abnahme, kein G3. |
| P3-4/P3-5/G3 | **offen** | Nicht Gegenstand; keine Plattform-/Gesamtmatrix behauptet. |

## 2. Neue MUSS-Findings

### F5 – `maxMembersPerNode` ist kein gemeinsames Knotenbudget

**Anforderung:** Kapitel 11 §2.2 definiert `budget` als
`ProjectionBudget(maxNodes,maxEdges,maxMembersPerNode)` mit „ausdrücklicher
Begrenzung mit LIMITED-Meldung“. VIZ-016 verlangt: „Eine Diagrammbegrenzung
MUSS ihren reduzierten Umfang ausdrücklich melden.“ Der Auftrag prüft
ausdrücklich Budget, Filter und ungültige Zahlen.

**Beobachtung:** Mit `ProjectionBudget(10,10,1)` auf einem echten Modell
`DOMAIN D=TEXT; CLASS C=aa:TEXT; MANDATORY CONSTRAINT R1:#true;` liefert der
Projektor:

```text
MAX_MEMBERS_REQUEST=1
MAX_MEMBERS_COMPLETENESS=COMPLETE
MAX_MEMBERS_NOTICES=[]
MAX_MEMBERS_MEMBER node=D compartment=DETAILS label=declared type type=Optional[TEXT]
MAX_MEMBERS_MEMBER node=C compartment=ATTRIBUTES label=aa type=Optional[TEXT]
MAX_MEMBERS_MEMBER node=C compartment=CONSTRAINTS label=R1 type=Optional[MandatoryConstraint]
MAX_MEMBERS_TOTAL=3
RESULT=FAIL
```

Damit hat Knoten C zwei Mitglieder und der Gesamtgraph drei, obwohl das
Budget ein Mitglied pro Knoten verlangt. Das Dokument behauptet `COMPLETE`,
nicht `LIMITED`, und enthält keinen `PROJECTION_LIMIT_REACHED`-Hinweis.

Dieselbe Verletzung ist über den öffentlichen Wire-/Clientpfad reproduzierbar:

- `R3BridgeProbe.log`: `wire maxMembersPerNode=1 bounds each node
  (observed max 2)`; das Dokument bleibt `COMPLETE`.
- `own-client-probe.log`: `client maxMembersPerNode bounds members
  (observed 2)` und `client maxMembersPerNode overrun is LIMITED/reported
  (observed COMPLETE)`.

**Ursache im Kandidaten:** `DefaultDiagramProjector.compartments` prüft
Attribute und Rollen jeweils separat gegen `maxMembersPerNode`
(`attributes.size() > max || roles.size() > max`). `addAttribute`/`addRole`
begrenzen nur ihre eigene Liste; Constraints werden in `addConstraints` ohne
jede Budgetprüfung angehängt; der DETAILS-Eintrag einer Domäne wird
unbedingt erzeugt. Damit ist `maxMembersPerNode` weder ein gemeinsames
Knotenbudget noch werden alle Mitgliedsarten erfasst.

**Erwartet:** Bei `maxMembersPerNode=1` darf kein Knoten mehr als ein
Mitglied enthalten; ein konsistenter reduzierter Graph muss `LIMITED` und
`PROJECTION_LIMIT_REACHED` melden. Falls das Projekt eine andere Semantik
(pro Compartment) beabsichtigt, müsste dies im Optionsvertrag ausdrücklich
definiert werden; der aktuelle Feldname und Kapitel 11 sagen „per node“.

**Rohlogs/Quellen:**
`build/review-r3/own/R3FindingsProbe.java` /
`R3FindingsProbe.log` (SHA-256 unten),
`build/review-r3/own/R3ProjectionProbe.log`,
`build/review-r3/own/R3BridgeProbe.log`,
`build/review-r3/own/own-client-probe.log`.

### F6 – `showTypes=false` wird für DETAILS- und CONSTRAINT-Mitglieder ignoriert

**Anforderung:** Kapitel 11 §2.2 definiert `showTypes` als „Typdarstellung in
Mitgliedern“. Die Auftragsklarstellung verlangt, sämtliche Diagrammoptionen
typgeprüft, versioniert und tatsächlich angewandt oder als unsupported zu
melden. VIZ-009 verlangt, dass Filter aus denselben Optionen abgeleitet
werden.

**Beobachtung:** Mit `showTypes=false` auf demselben echten Modell:

```text
SHOW_TYPES_MEMBER node=D compartment=DETAILS label=declared type type=Optional[TEXT]
SHOW_TYPES_MEMBER node=C compartment=ATTRIBUTES label=aa type=Optional.empty
SHOW_TYPES_MEMBER node=C compartment=CONSTRAINTS label=R1 type=Optional[MandatoryConstraint]
SHOW_TYPES_ANY_TYPE_LABEL=true
RESULT=FAIL
```

Der Attributtyp wird korrekt ausgeblendet, aber der DETAILS-Eintrag der Domäne
zeigt weiterhin `TEXT`, und das Constraint-Mitglied zeigt weiterhin
`MandatoryConstraint`. Damit ist die Option nur teilweise wirksam; sie wird
für zwei Mitgliedsarten still ignoriert. Dasselbe Ergebnis zeigt der
Wire-Pfad (`R3BridgeProbe.log`: `showTypes false removes type labels` FAIL)
und der gebaute TypeScript-Client (`own-client-probe.log`:
`client showTypes false removes all type labels` FAIL).

**Ursache im Kandidaten:** In `DefaultDiagramProjector.compartments` wird der
DETAILS-Eintrag der Domäne mit
`Optional.of(typeLabel(domain.typeId()))` unabhängig von `showTypes` erzeugt.
`addConstraints` setzt `typeLabel` ebenfalls unabhängig auf den
Constraint-Klassennamen. `showTypes` wird nur in `addAttribute`,
`addRole`/`addEffectiveMembers` und bei Parametern berücksichtigt.

**Erwartet:** Bei `showTypes=false` enthalten alle `DiagramMember`-Einträge
kein `typeLabel`; alternativ muss die Option für die betroffenen Mitglieder
ausdrücklich als nicht angeboten/unsupported gemeldet werden. Ein stilles
Teil-Anwenden ist kein vertragskonformer Filter.

**Rohlogs/Quellen:**
`build/review-r3/own/R3FindingsProbe.java` /
`R3FindingsProbe.log`,
`build/review-r3/own/R3BridgeProbe.log`,
`build/review-r3/own/own-client-probe.log`.

### F7 – No-op-`setConfiguration` ändert den exponierten Konfigurationsfingerprint

**Anforderung:** Kapitel 07 §2.3 definiert `ConfigurationReceipt` mit
„altem/neuem fachlichem Konfigurationsfingerprint“ und `WorkspaceState` mit
einem „fachlichen Konfigurationsfingerprint“. Kapitel 10 verlangt für
`openSession`/`setConfiguration` einen bestätigten Konfigurationsstand; der
TypeScript-Client führt diesen als bestätigten Hostspiegel.

**Beobachtung:** Ein `setConfiguration` mit **exakt derselben** Konfiguration
liefert `changed=false`, aber der vom Client exponierte
`state().configurationFingerprint` wechselt von der fachlichen Form auf die
vollständige Form:

```json
{
  "before": "profile=interlis-2.3-2.4/v1;languages=[2.3];warnings=REJECT_WARNINGS;budget=...;overlays=...",
  "receipt": "profile=interlis-2.3-2.4/v1;languages=[2.3];warnings=REJECT_WARNINGS;budget=...;overlays=...|CacheLimits[...]",
  "after": "profile=interlis-2.3-2.4/v1;languages=[2.3];warnings=REJECT_WARNINGS;budget=...;overlays=...|CacheLimits[...]",
  "changed": false,
  "same": false
}
```

Damit widersprechen sich `changed=false` und der geänderte bestätigte
Fingerprint. Nach einem Worker-Neustart würde `openSession` wieder die
fachliche Form liefern; ein Verbraucher kann den Fingerprint daher nicht
zuverlässig über Öffnen, No-op-Ersatz und Neustart vergleichen. Der Fehler ist
am gebauten TypeScript-Client mit dem echten TeaVM-JS-Compiler reproduziert;
er betrifft die gemeinsame Workspace-/Bridge-Quittung und wird nicht als
blosser Anzeigewert abgetan.

**Ursache im Kandidaten:** `DefaultWorkspace.configure` gibt
`oldFull`/`newFull` als `ConfigurationReceipt`-Fingerprints zurück.
`CompilerSessionFacade.setConfiguration` reicht diese unverändert weiter, und
`CompilerClient.setConfiguration` speichert den Wert als bestätigten
`configurationFingerprint`. `SessionRegistry.open` verwendet dagegen
`configuration.semanticFingerprint()`. Die beiden öffentlichen Pfade
verwenden damit unterschiedliche Fingerprintformen.

**Erwartet:** Beide Pfade liefern denselben fachlichen Fingerprint; ein No-op
ändert ihn nicht. Falls zusätzlich ein vollständiger Cache-/Konfigurationswert
benötigt wird, muss er als eigenes, klar benanntes Feld übertragen werden, nicht
als Ersatz des fachlichen Fingerprints.

**Rohlogs/Quellen:**
`build/review-r3/own/fingerprint-probe.cjs` /
`fingerprint-probe.log` (SHA-256 unten); ergänzend
`build/review-r3/own/own-client-probe.log`.

## 3. R1 F1–F4 – kumulativ erneut geprüft

Alle Proben sind neu für R3 geschrieben; sie sprechen öffentliche Verträge an
und kopieren keine Projekt-JUnit-Methode.

| Befund | Neue R3-Beobachtung | Ergebnis |
|---|---|---|
| F1 Partielle Analyse | Fehlender Import: `PARTIAL`, `UNRESOLVED`-Stub ohne Symbol/Anker, `MODEL_IMPORT`-Kante, `UNRESOLVED_TARGET`; betroffene Deklaration `PARTIAL`, gültiger Nachbar `RESOLVED`, kein erfundener Zielknoten. Fehlerhafte echte Deklaration ebenso. | **PASS** (`R3ProjectionProbe`, `own-client-probe`) |
| F2 Explizit leere Auswahl | `selection: []` → `INVALID_REQUEST`; fehlende Auswahl verwendet die belegten Wurzelmodelle; `null`/falscher Typ werden abgelehnt. | **PASS** (Java-Bridge und Client) |
| F3 Diagrammoptionen | Version 1.0, `visibleKinds`, `inheritedMembers`, `showConstraints`, `showImports`, `showRoleCardinalities`, `labelPolicy`/`translationModel`, `neighbourhoodDepth`, `associationPresentation` und `maxNodes` wirken; unbekannte/falsche Typen, Zahlenstrings, Version 2.0, fehlende Tiefe und fremde Übersetzungs-Snapshots werden typisiert abgelehnt. `COMPACT_BINARY` fällt mit `UNSUPPORTED_PRESENTATION` auf ENTITY zurück. **Aber F5/F6.** | **F3 im ursprünglichen Umfang PASS; Optionsvertrag insgesamt FAIL** |
| F4 Sitzungsabschluss | Zwei gleichzeitige plus ein weiterer Abschluss desselben Handles ergeben genau einen Wire-Aufruf; geschlossenes Handle `SESSION_EXPIRED`; fremder Client `SESSION_EXPIRED`; andere Sitzung bleibt nutzbar. | **PASS** (`own-client-probe`) |

Damit sind die vier ursprünglichen R1-Befunde geschlossen. Die neuen F5–F7
sind keine Wiederholung von F1–F4; sie betreffen drei bisher nicht geprüfte
Options- bzw. Konfigurationspfade.

## 4. R2, TRANSLATED und Root-Selector-Klarstellung

- R2 (`3b18058…`) erklärt fachlich PASS, endet aber mit Transportexit 1 und
  äusserem Manifest failed; das ist **keine gültige Abnahme**. Der Bericht
  bleibt unverändert und wird hier nicht umetikettiert.
- Die R2-Akzeptanz von nicht angebotenem `TRANSLATED` genügt VIZ-017 nicht.
  Am Kandidaten ist `translationModel: Optional<SymbolRef>` implementiert und
  mit echten ein- und mehrstufigen Übersetzungen geprüft:
  - Anzeigenamen (`Road`→`Route`→`Strasse`),
  - Mitglieds-/Typlabels (`name`→`nom`→`bezeichnung`,
    `Identifier`→`Identifiant`→`Kennung`),
  - unveränderte IDs, `canonicalName`, `SymbolRef` und SourceAnchors,
  - nicht zugeordnete externe Namen bleiben kanonisch,
  - falsches/veraltetes/unbekanntes Übersetzungsmodell wird typisiert
    abgelehnt,
  - Fingerprint enthält Übersetzungs-Snapshot/-Symbol und Tiefe,
  - alte Konstruktorüberladung bleibt erhalten; `NEIGHBOURHOOD`/`TRANSLATED`
    ohne Pflichtparameter werden typisiert abgelehnt.
- `requestedRoots` ist die unveränderte strukturierte
  `RootSelectorDto`-Eingangsliste; `resolvedRoots` stammt aus
  `RejectionSummary.requestedRoots`. Geprüft sind positive/negative,
  ByName mit Version/SourceHint, BySource mit Feld `source`, Reihenfolge,
  kanonisch sortierte Duplikatfreiheit der Gründe sowie das Fehlen von
  Java-`Optional`-/`toString`-Werten in Java- und TypeScript-Ausgaben.
- Die neue UI-/Gradle-Prüfung ist in `checkBrowser` eingebunden; eigene
  Browserproben (unten) bestätigen die reale Renderer-/Verzögerungs- und
  Navigationsbeobachtung unabhängig.

## 5. Umgebung, tatsächliche Kommandos und Zähler

| Bestandteil | Tatsächlich verwendet |
|---|---|
| Betriebssystem | macOS 15.8.1 (24H32), arm64 |
| JDK | Temurin `25.0.3+9-LTS`, `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem` |
| Gradle | Repository-Wrapper 9.8.1, jeweils `--no-daemon` |
| Node | `v24.22.0` unter `/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node` |
| npm | 11.19.1; `npm ci` im Modul `interlis-wasm-teavm` |
| Playwright | 1.64.1 |
| Browser | Chromium 156.0.8078.4, Firefox 157.0, WebKit 27.2, echtes Safari 27.0 (`safaridriver`: `Included with Safari 27.0 (20625.1.29.18.28)`) |
| WebImage | GraalVM `25.0.3+9.1`, Binaryen `wasm-opt 133` |
| Conformance | Projektbewertung 577 PASS/16 XFAIL/0 FAIL; roher Referenzlauf 577 PASS/1 XFAIL/15 FAIL, unverändert FAIL |
| Python | nicht verwendet; kein Python/pip/uv |

Alle Kommandos ausschliesslich im separaten Kandidatencheckout. Die
Ergebnislogs liegen unter `build/review-r3/` (ignoriert) und wurden nicht in
versionierte Dateien geschrieben.

| Kommando | Exit | Tatsächliches Ergebnis |
|---|---|---|
| `cd interlis-wasm-teavm && npm ci` (Node 24.22.0) | 0 | 14 Pakete, 0 Vulnerabilities; kein `@ilic/diagram` im Lockfile/`node_modules`, keine fingierte npm-Installation. |
| `./gradlew check --no-daemon …` | 0 | `BUILD SUCCESSFUL in 30s`, 46 Tasks. XML: **1170 Tests, 0 Failures, 0 Errors, 0 Skipped**. Aufteilung: Compiler 744, LSP 53, CLI 72, Model 16, Projection 18, Repository 41, Bridge 23, Workspace 23 (=990 JUnit) + Evidenz 145 + G2-Assertions 15 + Verification 20. |
| `./gradlew :interlis-wasm-teavm:browserClientTest :interlis-wasm-teavm:diagramAdapterTest --no-daemon …` | 0 | `browserClientTest` **17/17**, `diagramAdapterTest` **4/4**, keine Skips. |
| `./gradlew checkPortable --no-daemon …` | 0 | TeaVM JS **3548**, TeaVM WasmGC **3548**; `BROWSER_TOOLS_PASS` TypeScript 7.0.2 strict; `PYTHON_FREE_SOURCE_PASS 1380 inputs`; SDK-Modell 1. |
| `./gradlew checkBrowser --no-daemon …` (GRAALVM_HOME, BINARYEN_HOME, `MOZ_DISABLE_CONTENT_SANDBOX=1`) | 0 | WebImage Chromium/Firefox/WebKit je **3548**; TeaVM Chromium/Firefox/WebKit je **3548** mit **29 Transport-/7 Clientprüfungen**; `uiProjectionTest` **3/3**; echtes Safari **3548**, 29/7; `BUILD SUCCESSFUL in 2m 7s`. |
| `./gradlew :interlis-wasm-webimage:webImageProbe sdkProcessCheck sdkPackage --no-daemon …` | 0 | WebImageNode **3548**, `runtime=graalvm-web-image`; `SDK_PROCESS_PASS 9 commands`; SDK-ZIP `build/releases/interlis-java-sdk-0.2.0-SNAPSHOT.zip` erzeugt. |
| `./gradlew conformanceCheck --no-daemon …` | 0 | `selected=593 result=PASS rawExit=1`; Projektbewertung `{"PASS":577,"XFAIL":16,"FAIL":0,"XPASS":0,"ERROR":0,"TOTAL":593}`; roher Referenzlauf `referenceResult=FAIL` mit `{"PASS":577,"XFAIL":1,"FAIL":15,...}`. Keine Umetikettierung. |

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
  browser Worker; transport tested separately`; je Lauf 14892 Requests.
- Der neue UI-Test verzögert die Antwort **nach** dem unveränderten gepinnten
  ELK-/SVG-Renderer, ändert danach den Quelltext und prüft Verwerfen plus
  anschliessende gültige Navigation. Eigene unabhängige UI-Probe (unten)
  beobachtet denselben Ablauf in Chromium/Firefox/WebKit; eigene echte
  Safari-UI-Probe prüft Übersetzung/Fokus/Tiefe/Navigation ohne künstliche
  Rendererverzögerung.

## 6. Eigene neue positive und negative Gegenproben

Alle Quellen und Rohlogs liegen unter
`/private/tmp/interlis-java-P3-3-r3/build/review-r3/own/` und sind nicht
versioniert. Keine Probe ist eine blosse Umbenennung einer Elternprobe.

| Probe | Quelle (SHA-256) | Ergebnis | Rohlog |
|---|---|---|---|
| `R3ProjectionProbe` (Java, echte Compiler-/Projektion) | `R3ProjectionProbe.java` `4ec40cbff259e82b17abd20836da9676d402db148adc8a41a102bda8c3797d38` | **127 bestanden, 2 FAIL** (F5) | `R3ProjectionProbe.log` `82aac89357bd60faa62b39b40e7f2f1b16fa0bedd9c9df92de85b31aa6a0f2c6` |
| `R3BridgeProbe` (Java, echter Bridge/Wire) | `R3BridgeProbe.java` `b5f02cef984510b52aa0b1798133b408aeb21556e0148bb4a26e94ab836e76f5` | **68 bestanden, 2 FAIL** (F5/F6) | `R3BridgeProbe.log` `7aa936872609bf507af69aadfb9529ed92e81ecccd4f6d4f48a91cb95a184ea5` |
| `R3FindingsProbe` (Java, fokussierte Reproduktion) | `R3FindingsProbe.java` `7325bfde3a6cefe5f1068c05461b26bd7f904d1736d65c0f1706fbd93f1a3a07` | **FAIL**, exakte F5/F6-Evidenz | `R3FindingsProbe.log` `088a4863e83179d109c632763b6da827af0215e463ba53a7862f355f38bd83bd` |
| `own-client-probe` (Node, echter TeaVM-JS-Compiler + gebauter TS-Client) | `own-client-probe.cjs` `b5ff1565e592b723bc54f7860f88b99305a55db3128ca10ef38e9b7d79868150` | **83 bestanden, 3 FAIL** (F5/F6) | `own-client-probe.log` `ebf32429d1e35b6a08d9a652360f7dbcf674fbb61431767419545ae40b8c1921` |
| `fingerprint-probe` (Node, echter TeaVM-JS-Compiler + gebauter TS-Client) | `fingerprint-probe.cjs` `46e2b67022e317736d71900e5f76d834716a1fcf432921b26434f90641a2668a` | **FAIL** (F7) | `fingerprint-probe.log` `558e2f7dc6872586cbe91c1644ada629a6779c5df80a22f01337f121b4219c40` |
| `own-ui-probe` (Playwright, echter UI-/Rendererpfad) | `own-ui-probe.cjs` `a518b5b5993bc526db0cb5867501d669a9f8a48d3644fb623eb7df85a0129ebb` | **3/3 PASS** (Chromium/Firefox/WebKit) | `own-ui-probe.log` `4a22104019ff5125c7439da266110b2a6d4d970f9965a724275bfa252c45d25c`; `own-ui-results.json` `0b6cdd4775c5f95d62285dde2d2c72f206e0dc075fbb62c4d7c9da4ec7d999c3` |
| `own-safari-ui-probe` (echtes Safari/WebDriver) | `own-safari-ui-probe.cjs` `73775b95e1a2be46623f4fb70ca39894050723924df5d4681042b7112acc6dbb` | **PASS** (Safari 27.0) | `own-safari-ui-probe.log` `c8c9231193cf31fb3aee797f0a07e763555d05eb8ce6bc4f7120056a3725cfcb`; `own-safari-ui-result.json` `752970ad9188c906df336abbee858e0c865d2c048acca17741b7475be610d54f` |

### 6.1 Was die Proben tatsächlich prüfen

**`R3ProjectionProbe` (127/129):** alte und neue `DiagramOptions`-Form,
Fingerprint inkl. kanonischer `visibleKinds`-Reihenfolge, Tiefen 0..32 und
33/-1, Tiefe ohne `NEIGHBOURHOOD`, Übersetzung ohne `TRANSLATED`, leere
Java-Auswahl, Budget 0/negativ; ein-/mehrstufige Übersetzung mit
IDs/`canonicalName`/Symbol/Anker/Mitglieds-/Typlabels und unverändertem
kanonischem Dokument; nicht zugeordnete Namen; falsches/veraltetes/unbekanntes
Übersetzungsmodell; Nachbarschaft 0/1/2, externe Randstubs, Geschwister- und
Fokusgrenzen, Topic-Teilbaum, Rollenkanten, Budget, Abbruch, Tiefe 32;
partielle Analyse mit fehlendem Import und fehlerhafter Deklaration; volle
Schemaform, eindeutige IDs, `effectiveOwner`, n-äre attributierte Assoziation,
Kardinalität >2^53 im DTO und Wire-JSON, Vererbungs-/Domain-/Referenz-/
Kompositionskanten, Validator, Mermaid-/PlantUML-Determinismus/Escaping.
**F5 ist die einzige rote Gruppe.**

**`R3BridgeProbe` (68/70):** Root-Selector-Wire (`requestedRoots` unverändert,
`resolvedRoots` strukturiert, ByName/Version/SourceHint/BySource, Feld
`source`, sortierte Gründe, keine Java-`toString`-Werte, negative Formen);
Optionspfad mit Budget, `visibleKinds`, `inheritedMembers`, `showTypes`,
`showConstraints`, `showRoleCardinalities`, `showImports`,
`COMPACT_BINARY`-Fallback, Übersetzung, Nachbarschaft, leere Auswahl, falsche
Typen/Zahlenstrings, unbekannte Felder, Version 2.0, Tiefe 33,
Zwei-Eingaben/kein Eingang, unbekanntes Schema, Capabilities. **F5/F6 sind rot.**

**`own-client-probe` (83/86):** vollständige Mehrdatei-/Konfigurations-
spiegelung; queued abhängige Edits; atomarer Batch-Fehler; Quell-/
Quellenzahl-/Sitzungs-/Snapshot-/Nachrichten-/Grössenlimits; ungültige
Initialdaten ohne Teilsitzung; abgelehnter Compile mit Snapshotkapazität und
idempotenter Freigabe; zwei Sitzungen nach Neustart, alle Quellen/
Konfigurationen, neue Handles, alte Session-/Snapshot-/Schema-Handles
`SESSION_EXPIRED`/`SNAPSHOT_EXPIRED`, deterministisch gleiche Core-Snapshot-
IDs; Cancel queued/running; RuntimeFailure; Bootstrapfehler mit Folgeanfragen;
Dispose; begrenzte Queue; idempotenter/fremder Close; Root-Selector- und
Optionspfade; mehrstufige Übersetzung/Fokus/Tiefe; keine Stackdaten in
Diagnosen; grosse Revisionsstrings. **F5/F6 sind rot.**

**`fingerprint-probe`:** Öffnet eine Sitzung mit vollständiger Konfiguration,
ersetzt exakt dieselbe Konfiguration über `setConfiguration` und vergleicht
`state().configurationFingerprint` vor/nach der Quittung. Beobachtet wird
`changed=false`, aber `same=false`; der Wert wechselt von der fachlichen auf
die vollständige Form. **F7 ist rot.**

**`own-ui-probe` (3/3):** echter gebauter UI-Pfad, unveränderter gepinnter
Renderer über Query-URL, mehrstufige Übersetzung mit ID-/Anker-Invarianz,
Fokus ohne `Garage`-Geschwister, Tiefe 0/1, verspätete reale Rendererantwort
nach Quelltextänderung verworfen (alte Ansicht bleibt, `data-stale=true`),
anschliessende Navigation mit der neuen bestätigten Revision, Benchmark-
Erfolgs-/Fehlerpfad ohne Sitzungsleck.

**`own-safari-ui-probe`:** dieselbe Übersetzung/Fokus/Tiefe/Navigation im
echten Safari 27.0; bewusst keine künstliche Rendererverzögerung, weil die
Probe über WebDriver und nicht über Playwright-Routing läuft. Die
Verzögerungsprüfung bleibt für Chromium/Firefox/WebKit belegt.

## 7. Weitere geprüfte Verträge (grün, soweit nicht F5–F7)

| Bereich | Beobachtung |
|---|---|
| Mehrdatei-/Konfigurationsspiegel | Quellen und Konfigurationen aller Sitzungen werden wiederhergestellt; **F7** verletzt jedoch den fachlichen `configurationFingerprint` bei No-op-`setConfiguration`. |
| Atomare Quittungsannahme | Beschädigte Batch-Quittung führt zu `RUNTIME_FAILURE` und vollständiger Wiederherstellung; kein Teilcommit; Quell-/Sitzungslimits atomar. |
| Gequeuete abhängige Edits | Zwei aufeinander aufbauende Edits werden in Reihenfolge materialisiert; Revision 1→2→3; Text exakt. |
| Neue Handles/Epochschutz | Deterministische Core-Snapshot-ID bleibt gleich, öffentliche Session-/Snapshot-/Schema-Handles sind neu; alte Handles abgewiesen. |
| Cancel/RuntimeFailure/Bootstrap/Dispose/Queue | Queued/running Cancel, RuntimeTrap, Bootstrapfehler mit sofortiger Folgeablehnung, expliziter Neustart, Dispose, begrenzte Queue; keine Hänger. |
| Limits/Freigabe | Quell-/Gesamttext-/Quellenzahl-/Snapshot-/Nachrichten-/Sitzungsgrenzen; ungültige Initialdaten; abgelehnter Compile belegt Kapazität; Freigabe idempotent; Benchmark schliesst Sitzung im Fehlerpfad. |
| Kein zweiter Binder/Gate | Bridge und Client delegieren an Workspace/Engine; Root-Selektoren und Optionen sind reine DTO-Konvertierung; keine Sprachsemantik im TS-Client. |
| Interne Fehler | `INTERNAL_FAILURE` mit FailureId und sicherer Meldung; keine Java-/JS-Stackdaten als fachliche Diagnose. |
| TypeScript/Consumer | `buildBrowserTools`: TypeScript 7.0.2 strict, Deklarationen, Bundles, separater Consumer ohne `paths`; `BROWSER_TOOLS_PASS`; 24 Ausgaben. |
| Pins/Lizenzen | npm-Lock exakt (ajv 8.20.0, elkjs 0.11.0, esbuild 0.28.2, libavoid-js 0.5.0-beta.5, playwright 1.64.1, sprotty-protocol 1.4.0, typescript 7.0.2); keine `@ilic`-Installation; MIT (ilic-diagram), EPL-2.0 (ELK/Sprotty), LGPL-2.1 (libavoid) vorhanden; alle vendorten Hashes stimmen mit dem Pin; `elk-contract.d.ts` unterscheidet sich von `elkjs@0.11.0` in genau der dokumentierten `NonNullable`-Zeile; kein `skipLibCheck`. |
| libavoid | Externes ESM/Wasm unter `build/browser-tools/vendor/libavoid/`; Importmap bindet lokalen Pfad; Bundle importiert `libavoid-js` external, kein Netzrenderer. |
| Diagrammform | Schema 1.0 vollständig für Rahmen/Knoten/Compartments/Mitglieder/Endpunkte/Kantenenden/Kardinalität/Hinweise; UTF-16-Ranges; eindeutige IDs auch für geerbte Mitglieder; `effectiveOwner`; n-äre attributierte Assoziationen mit drei Rollenkanten und Endinformationen; exakte Kardinalität >2^53; DOMAIN_USE/REFERENCE/STRUCTURE_COMPOSITION/INHERITANCE/MODEL_IMPORT/TRANSLATION/VIEW_DERIVATION; Validator. |
| Navigation/Stale | IDs statt Namen; gleicher Snapshot, gleiche Revision und gleicher bestätigter Text; geänderter Text/Revision/Snapshot → `stale`; keine alten Offsets; beidseitige Auswahl über Symbolidentitäten; verspätete Layoutantwort ersetzt die aktuelle Ansicht nicht. |
| Partielle/limitierte Daten | Fehlender Import sichtbar, Nachbarn bleiben `RESOLVED`; `COMPACT_BINARY`-Fallback mit Hinweis; `maxNodes`/`maxEdges`/Nachbarschaftsbudget mit LIMITED. **`maxMembersPerNode` und `showTypes` siehe F5/F6; F7 betrifft den Konfigurationsspiegel.** |

## 8. Nicht ausgeführte oder nicht abgeschlossene Pflichten

- **P3-4, P3-5, G3:** nicht Gegenstand; keine Abnahme, keine Gesamtmatrix.
- **Windows x64 / Linux x64:** nicht ausgeführt; gehört P3-5. Keine fremde
  Plattformmatrix als eigene Beobachtung behauptet.
- **Finale Plattform-/Release-Matrix:** nicht behauptet.
- **Äusserer Transport/Manifest:** zum Berichtszeitpunkt nicht beendet; kein
  Endstatus vorweggenommen.
- **P3-4-Corpuscompileränderungen:** ausdrücklich nicht Teil des sauberen
  Kandidaten; nicht bewertet.
- **Safari-UI mit künstlicher Rendererverzögerung:** nicht ausgeführt; echte
  Safari-UI ohne Verzögerung sowie die Verzögerungsprüfung auf
  Chromium/Firefox/WebKit sind getrennt belegt.
- **F5/F6/F7:** nicht repariert (Reviewauftrag); F5/F6 blockieren AC-P3-11, F7 AC-P3-09.

## 9. Fazit

Der Kandidat besteht die breite JVM-/Portable-/Browser-/Safari-/WebImage-/
SDK- und Conformance-Regression und schliesst F1–F4 sowie die neuen
Übersetzungs-, Nachbarschafts-, Root-Selector- und UI-Verträge überzeugend.
Die Abnahme scheitert jedoch an drei konkreten Vertragsverstössen:

1. **F7:** Ein No-op-`setConfiguration` ändert den exponierten fachlichen
   Konfigurationsfingerprint und widerspricht dem gemeldeten `changed=false`.
2. **F5:** `maxMembersPerNode` ist kein gemeinsames Knotenbudget; das Dokument
   kann das Budget überschreiten und trotzdem `COMPLETE` ohne Hinweis melden.
3. **F6:** `showTypes=false` wird für DETAILS- und CONSTRAINT-Mitglieder still
   ignoriert.

Damit sind **AC-P3-09 und AC-P3-11 FAIL** und **P3-3 nicht abgenommen**. Der
nächste fachliche Schritt ist die Korrektur von F5–F7 mit neuen
RED→GREEN-Proben (Budget über alle Mitgliedsarten/Compartments; `showTypes`
für DETAILS und CONSTRAINTS; konsistenter fachlicher
Konfigurationsfingerprint) und ein neuer sauberer Kandidat mit anschliessendem
unabhängigem Review. Dieser Bericht bleibt unverändert und erteilt keine
Reparaturfreigabe.
