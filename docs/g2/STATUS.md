# G2 – operativer Status

- **Phase 4 und G2: PASS, vollständig abgeschlossen.** L4-2 ist unabhängig
  abgenommen, kumulativ **AC4-01–AC4-07** sowie AC0A/AC0B/AC1/AC2/AC3.
  Der Auftrag wurde ohne Teilphasenstopp bis zur Gesamtfreigabe ausgeführt.
- **Geprüfter Schlussproduktcommit:**
  `1371841572828a387c1369ae84f6fa6cb4ddffc0`.
  [Unveränderter G2-Gesamtreview](reviews/L4-2-review.md), SHA-256
  `770856335881b35da5deebfb070a5f25146deefe57cc6da21438a893de87179e`.
  [Abnahmeumfang und Nachweise](implementation/L4-2.md).
- OpenCode **2.0.28**, DeepSeek direkt **deepseek/deepseek-flash**, neue unabhängige
  Sitzung im sauberen Schluss-SHA-Worktree. Gesamter OpenCode-Prozess und alle
  Build-/Probeunterprozesse OS-Python-gesperrt; privater PATH mit ENOENT-Proben,
  absolute Interpreter mit EPERM. Äusseres Manifest nach CLI-Ende **passed**,
  **CLI-Exit 0**, Head exakt Schlussproduktcommit, dirty leer.
- Frischer voller Pflichtlauf: **Gradle-Exit 0, 11m43s, 80 von 80 Tasks ausgeführt**.
  **1063 JUnit-Fälle**, davon 886 regulär (735 Compiler, 41 Repository, 37 CLI,
  22 Workspace, 7 Bridge, 16 Model, 16 LSP, 12 Projection), 144 Evidenz,
  15 Assertions und 18 Host; **0 Failures/Errors/Skips**, alle 199 XMLs frisch.
- Je **3548 PASS**: TeaVM-JS/Node, WasmGC/Node, Chromium 156.0.8078.4,
  Firefox 157.0, WebKit 27.2 und echtes Safari **27.0 (20625.1.29.18.28)**.
  Frischer Web-Image-Build: Node und Chromium/Firefox/WebKit ebenfalls je 3548.
  SDK9-Prozesskommandos, SDK-ZIP, LSP-Distribution/Prozessverträge, Diagrammadapter
  und Worker-Neustart erfolgreich. Pythonfrei **1290 Inputs**, 0 getrackte .py.
- T2 unverändert **593/610** (17 INTERLIS-1 ausgeschlossen): roh **577 PASS /
  15 FAIL / 1 XFAIL**, Projekt **577 PASS / 16 XFAIL / 0 FAIL/ERROR/XPASS**;
  **21/21** strenge Original-/Kontroll-/Diagnoseassessments PASS. Suite-, Archiv-,
  Original-/Kontrollpins und historisches 19-Fälle-Bytearchiv unverändert.
- Vollständigkeitsnachweise: 369 Normcluster (285 Compiler / 84 ausserhalb),
  80 Anforderungen, 321 Produktionen/1225 Occurrences, 21 Builtins,
  130 Predefined, 21 Sondertypen, 264 reale Methodenbindungen. A3 vergleicht
  echte JVM/ili2c/JS/WasmGC-Member-/Ziel-/Reihenfolgewerte und erkennt 8 Mutanten;
  nicht gemeinsam vergleichbare Eigenschaften bleiben ausdrücklich abgegrenzt.
- T4: echte gepinnte 2.3-Roads- und 2.4-CHBase-Closures mit transitiven Imports,
  öffentlichen Semantikassertions, Fresh/Reuse, Offline/Pins/Locks und atomarer
  Annahme. Offizielle Originalbytes im Schlussreview unabhängig erneut geprüft.
- Neue unabhängige Gegenproben: **23 JVM- und 26 Portable-Checks** verbinden
  Translation, Imports, Vererbung, gleichnamige Rollenziele, View und Fresh/Reuse;
  negative Translation, strikte äussere Namen und 2.3-OID-Kompatibilität geprüft.
  Neuer Kommentar-Gegenfall in beiden Versionen sowie vollständiger Link-/XML-Audit.
- Sämtliche nötigen Nutzerentscheidungen sind tatsächlich gegeben und umgesetzt:
  insbesondere äussere Typnamen strikt und gültige relative URI-Referenzen erhalten.
  Corpus: 13 unveränderte Originale, elf Compileerfolge und zwei exakt erwartete
  Namensraumablehnungen mit minimal umbenannten Positivkontrollen, 13 Formate.
  Referenz-/Messgrenzen bleiben sichtbar: 6 View-Referenzprozessfehler;
  historische P3-Benchmark-Stackausnahmen 1/1/12, keine G2-Browserpflichtskips.
- **Keine offenen Muss-Findings, Blocker oder ausgelassenen fälligen Prüfungen.**
  F1–F5 und O1 geschlossen; frühere FAIL-Berichte unverändert erhalten.
  Nicht blockierende Hinweise O2/O3: Frischebindung der regulären Completion-XMLs
  künftig zusätzlich härten; historischen Zähler 615 deutlicher beschriften.
  Die Frische aller aktuellen XMLs ist unabhängig belegt, aktiv gelten 729 Methoden.
- **L4-1 PASS** am Produktcommit `e3a57c072bf93ff2351d6aa6fc551f4e562c4437`.
  [Unveränderter Review](reviews/L4-1-review.md), SHA-256
  `1c2aea5b446e956f34d9aa64d23c0cbc8f00d73002f96447c6b1bd8e14fefae5`.
- Der folgende Abschlusscommit speichert ausschliesslich Review und Status;
  er ändert den unabhängig geprüften Produktstand nicht. Nächster fachlicher
  Meilenstein ist P3 gemäss Spezifikation, separat zu beauftragen. Dieses PASS
  ist G2; P3/P4/P5 sind nicht freigegeben. Kein Push, Merge oder Release erfolgt.

- Auftrag **gesamte Phase3 autonom**: vollständig abgeschlossen, ohne manuelle
  Stopps zwischen L3-1 und L3-2. **L3-2 / PHASE3: PASS**, kumulativ
  **AC3-01–AC3-06**. Keine offenen Muss-Findings oder Phase-3-Blocker.
- Unabhängig geprüfter Schlussproduktcommit:
  `1387ac887239ecc78ed42e688a81a0d00dfcba84`.
  [Unveränderter kumulativer Review](reviews/L3-2-review.md), SHA-256
  `7a07eba4dcba66c5dc3b3ad44162af0bdb928732b9ab76ae4786c083dbad45f5`.
  OpenCode CLI2.0.28, DeepSeek direkt, `deepseek/deepseek-flash`,
  frischer sauberer Worktree; alle sechs Kriterien einzeln erneut bewertet.
- Unabhängiger voller Pflichtlauf **Exit0 / 4m19s / 71von71Tasks frisch ausgeführt**.
  **1025JUnit-Fälle**, davon **858reguläreJVM** (710Compiler,41Repository,
  37CLI,20Workspace,6Bridge,16Model,16LSP,12Projection),137Evidenz,
  15Assertions und15Host: jeweils **0Failures/Errors/Skips**. Git-Head des
  vollständigen OS-Laufmanifests exakt1387ac8,dirty leer. Keine fällige Prüfung ausgelassen.
- Je **3499PASS**: TeaVM-JS/Node,WasmGC/Node,Chromium156.0.8078.4,
  Firefox157.0,WebKit27.2 und **echtesSafari27.0(20625.1.29.18.28)**;
  frischerWebImage-Build,WebImage/Node undalle3Browser ebenfalls3499PASS.
  Diagrammadapter/Workerneustart,SDK9Prozesskommandos,SDK-Paket,
  LSP-Distribution/Prozesstests bestanden. Pythonfrei1250Inputs mit
  privatenPATH-ENOENT- undabsolutenEPERM-Proben; vollständigerLauf OS-gesperrt.
- T2 unverändert **593/610**: roh577PASS/15FAIL/1XFAIL; Projekt577PASS/16XFAIL
  mit0FAIL/ERROR/XPASS, **21/21** strengeAssessmentsPASS. Suite-/Archiv-/Original-/
  Kontrollpins,aktuelles21Objektmanifest undhistorisches19FälleBytearchiv unverändert.
- Echte unveränderliche Overlays, Ziel-/Prioritäts-/Konfliktbindung und Herkunft;
  feldweise Inhaltsidentität und sichere Snapshotannahme; durchgehende lokale/
  file-/HTTP-Beschaffung, exakte Versionen/Pins/Mehridentitätslocks, Offlinecache,
  atomare/stale-sichereÜbernahme und erneut geprüfte Originalbindungen.
  Unveränderte gepinnte2.3-Roads- und2.4-CHBase-Closures mit öffentlichen
  Modell-/Typ-/Ziel-/Member-/Reihenfolge-/Herkunftsassertions undFresh/Reuse.
  [Implementierung/Nachweise](implementation/L3-2.md).
- Zwei eigene neue Reviewer-Gegenproben PASS: roheVersion `"1 "` gegenüber
  falschemTrim-Pin `"1"` einschliesslich öffentlichemModelSymbol/Lock;
  identischeOverlaywerte mit idempotentemIndex und erhaltenenProvenienzen.
  Rohbelege dauerhaft unter `build/review/L3-2/evidence/` kopiert.
- Transportprovenienz getrennt vom Prüfurteil: ursprüngliche OpenCode-Sitzung
  endete mit CLI-Exit1 nach frühem `provider.transport`-ECONNRESET (HTTP200).
  Danach folgten479Ereignisse mit vollständigemGateExit0,Gegenproben,Bericht/
  Hashkontrolle undPASS-Schlussantwort. Kein erfolgreicher Original-CLI-Exit
  wird behauptet. Die anschliessende rein redaktionelle unabhängige Kontrolle
  korrigiert RepositoryClientTest auf3/3 und ergänzt dieseProvenienz;
  sie endete mitCLI-Exit0. Produkt,Kriterien,Prüfungen undUrteil unverändert.
- **L3-1 PASS**, AC3-01/02, Produktcommit
  `f839311dd0f2e3d3a3925a6edfaea44ddacbc866`.
  [Unveränderter Review](reviews/L3-1-review.md), SHA-256
  `1ee5dec10b4447e35b0a72425a816f9aaa5b23779ab3dd7efe2b3bc36cd05188`.
  [Implementierung](implementation/L3-1.md). Alle Kriterien beim L3-2-Gate kumulativ neu geprüft.
- **Historischer Phase3-Abschluss:** Phase4 war damals noch nicht begonnen;
  der aktuelle Phase4-Auftrag und Stand stehen oben. Kein Push,Release oder globaleG2-/P3-/P4-/P5-Freigabe;
  GlobalD2 bleibt getrennt **PENDING_USER_REVALIDATION**. Der folgende Abschluss-
  commit speichert ausschliesslich Review/Status und ändert den geprüften Produktstand nicht.

- Auftrag **gesamte Phase 2 autonom**: abgeschlossen, ohne manuelle Stopps
  zwischen den Lieferungen. **L2-3 / PHASE 2: PASS**, kumulativ **AC2-01–AC2-07**.
- Unabhängig geprüfter Schlussproduktcommit:
  `d358e3c5e26b4db646124648f9b876ffa0d8b90b`.
  [Unveränderter kumulativer Review](reviews/L2-3-review.md), SHA-256
  `3b758ab895551e04912efc5ddb0fc7f7e6deba66a9addfd0d038f31e22e5561b`.
  OpenCode v2.0.28, Provider DeepSeek direkt, `deepseek/deepseek-flash`,
  neuer sauberer Worktree. Alle sieben Kriterien einzeln neu bewertet.
- Unabhängiger voller Pflichtlauf: **Exit0 / 4m53s / 71 von71 Tasks ausgeführt**.
  **970 JUnit-Fälle**, davon **803 reguläre JVM** (694Compiler),137Evidenz-,
  15Assertion-,15Hosttests, jeweils **0Failures/Errors/Skips**. SDK9,
  SDK-Paket/LSP-Distribution, Pythonfrei1213Inputs mit PATH-ENOENT/absolutem EPERM.
  Git-Head des vollständigen Laufmanifests ist exakt der Schlussproduktcommit,
  dirty leer. Keine fällige Phase-2-Prüfung ausgelassen.
- Je **3487 PASS**: TeaVM-JS/Node, WasmGC/Node, Web-Image-Node,
  Web Image auf Chromium/Firefox/WebKit sowie TeaVM auf Chromium156.0.8078.4,
  Firefox157.0/WebKit27.2 und **echtem Safari27.0 (20625.1.29.18.28)**.
  Frischer Web-Image-Build, Worker-Neustart und Diagrammadapter belegt.
- Eigene neue Reviewer-Gegenprobe: **22/22 Checks PASS** am Kandidaten;
  dieselbe Probe reproduziert **vier Vorherfehler** am L2-3-Ausgangspunkt
  (zweimal geerbte importierte Assoziationsbasis, zweimal LineCoord-Abstraktion).
  Keine offenen Muss-Findings oder Blocker. Konservativ nicht mandatory kopierte
  2.4-Rollen bleiben als nicht blockierende Beobachtung im Bericht sichtbar.
- Transportprovenienz getrennt vom Testurteil: Die OpenCode-Sitzung endete mit
  CLI-Exit1 nach einem zwischenzeitlichen `provider.transport`-Timeout
  (HTTP200, „Timed out waiting for response data“). Danach folgten231 weitere
  JSONL-Ereignisse mit vollständiger Prüfung/Berichtsspeicherung/Hashkontrolle
  und abschliessender PASS-Antwort. Der Pflichtlauf endete nachweislich mit0;
  ein erfolgreicher CLI-Exit wird nicht behauptet. Rohlog:
  `build/review/L2-3/opencode.jsonl`; der unveränderte Reviewbericht und dessen
  Schlussurteil sind vollständig vorhanden.
- Phase3 ist inzwischen ausdrücklich beauftragt; aktueller Stand oben. Dieses Phase-2-PASS ist
  keine globale G2-/P3-/P4-/P5-Freigabe; GlobalD2 bleibt getrennt
  PENDING_USER_REVALIDATION. [Implementierung/Nachweise](implementation/L2-3.md).
- **L2-1: PASS**, AC2-01/AC2-02. Unabhängig geprüfter Produktcommit
  `900706a177a047e6f7d0ffd34d9e4cb2853a2ad7`.
  [Unveränderter R3-/PASS-Bericht](reviews/L2-1-review.md), SHA-256
  `90bd0aad463d6e0e5d749f7251c666e39776caf21c0911c8abaf4506803ca43e`.
  OpenCode-CLI2.0.28, DeepSeek direkt, `deepseek/deepseek-flash`, neuer sauberer
  Worktree, vollständiger Pflichtlauf mit --rerun-tasks: Exit0/2m26s/58 Tasks.
  **788 reguläre JVM-Tests** (679Compiler),137Evidenz-,15Assertion-,15Hosttests,
  alle0 Failures/Errors/Skips. SDK9, Pythonfrei1207Inputs (ENOENT/EPERM),
  TeaVM-JS und WasmGC/Node je **3459 PASS**. Eigene neue Reviewer-Gegenprobe:
  **66 Checks PASS**, derselbe Test am Ausgangscommit **22 RED**.
- T2 **593/610**: roh577PASS/15FAIL/1XFAIL; Projekt577PASS/16XFAIL mit
  **0FAIL/ERROR/XPASS**, **21/21** strenge Assessments PASS. Die zusätzlichen
  zwei SET-Referenzfälle folgen der ausdrücklich gewählten N3-Regel; separate
  Freigaben ihrer Namen werden nicht behauptet. Alle ursprünglichen19 Objekte
  und die bytegenaue historische19-Fälle-Fassung sind unverändert erhalten;
  Suite-/Archiv-/Original-/Kontrollpins bleiben aktiv. GlobalD2 bleibt getrennt
  PENDING_USER_REVALIDATION/19Fälle. [Implementierung/Nachweise](implementation/L2-1.md).
- Bestätigte Profile: **N1** areAreas2/3 beide OBJECTS-Signaturen in2.4,
  allgemeine Singleton-Zuweisung erhalten, in2.3 beide unzulässig. **N2**
  OBJECTS-Parameterfunktionen bleiben in2.4-MANDATORY/Plausibility wieili2c
  zulässig. **N3** SET-Hauptausdruck ohne einzelne Attribut-/Rollen-/THIS-Werte,
  WHERE bleibt individuell;2.4 verlangt mindestens einen OBJECTS-Aufruf im
  Hauptausdruck. Typisierte SEM-0008-/SEM-0005-Fehler, keine abhängigen
  Typ-/Übersetzungsfehlerkaskaden. Keine offenen L2-1-Muss-Findings.
- Historische [R1](reviews/L2-1-review-r1.md) und [R2](reviews/L2-1-review-r2.md)
  bleiben unverändert. F1 wurde inR2 geschlossen; dessen damalige N1–N3-
  Blocker wurden durch die tatsächlichen Antworten und R3 erledigt.
- **L2-2: PASS**, AC2-03/04 am Produktcommit
  `30ef129ad648ec5d8522ed36226fec31f420d84a`. [Unveränderter Review](reviews/L2-2-review.md),
  SHA-256 `0d1b1feb02e3a7d0663e053792bd525e137e79afa837b8335a67589e7abb4e95`.
  OpenCode CLI2.0.28, DeepSeek direkt, frischer sauberer Worktree.
  Vollständiger Pflichtlauf Exit0/2m34s/58 ausgeführte Tasks;797 reguläre JVM
  (688Compiler),137Evidenz-,15Assertion-,15Hosttests, alle0Fehler/Skips.
  JS/WasmGC je3471PASS,SDK9,Pythonfrei1210Inputs,21/21Normprüfungen.
  Eigene neue Reviewer-Gegenprobe:76Checks/0Fehler. Nicht blockierende
  Beobachtungen zur geerbten Assoziationsbasis und konservativer Rollenpflicht
  bleiben im Bericht sichtbar. [Implementierung](implementation/L2-2.md).

- Auftrag **gesamte Phase 1 autonom**: abgeschlossen, ohne manuelle Stopps zwischen
  den Lieferungen. **L1-3 / PHASE 1: PASS**, kumulativ **AC1-01 bis AC1-08**.
- Unabhängig geprüfter Produktcommit: `62c0870a58db677a796143aa8f89578f2e3d7f43`.
  [Unveränderter kumulativer Review](reviews/L1-3-review.md), SHA-256
  `6c048687054a930482e5334a4a00ff855315f915081579f6950f7813d642dbba`.
- Prüfer: OpenCode **v2.0.28**, **DeepSeek direkt**,
  `deepseek/deepseek-flash` (UI DeepSeek V4.1 Flash 1017), kein OpenRouter.
  Neuer sauberer Worktree, gesamtes Phase-1-Gate frisch auf demselben SHA.
- Reviewer-Vollauf: **Exit 0 / 4m 20s / 71 Tasks ausgeführt**. **765** reguläre
  JVM-Tests, davon **656** Compiler; **136** Evidenz-, **15** Assertion- und
  **15** Hosttests; **0 Failures/Errors/Skips**. Eigener Vollauf: Exit0/4m12s,
  gleiche Testzahlen und vollständige Matrix.
- TeaVM-JS und WasmGC/Node jeweils **3405** Fälle PASS. Chromium156.0.8078.4,
  Firefox157.0, WebKit27.2 sowie **echtes Safari27.0 (20625.1.29.18.28)**
  jeweils3405 PASS. Web Image frisch gebaut, Nodeprobe und alle drei Browser
  jeweils3405 PASS. Diagrammadapter, SDK 9 Prozesskommandos, SDK-Paket,
  LSP-Distribution und LSP-Prozesstests bestanden.
- T2 **593/610**: roh **579 PASS / 13 FAIL / 1 XFAIL**; Projekt **579 PASS /
  14 XFAIL / 0 FAIL/ERROR/XPASS**, **19/19** strenge Assessments PASS.
  Alle19 Manifestobjekte, Original-/Kontrollquellen, Suite-/Archiv-/Baseline-
  und Normzitatpins seit L1-2 unverändert. Rohbilanz bleibt getrennt sichtbar.
- **Pythonfrei**:0 getrackte .py,1197 geprüfte Eingaben, PATH-Proben ENOENT,
  absolute Interpreter EPERM. Gesamter Pflichtlauf im OS-gesperrten Umfeld.
- Neue eigene Reviewer-Gegenprobe: **436 öffentliche Checks PASS** am Kandidaten,
  derselbe Test reproduziert am Ausgangsstand14 tatsächliche Vorherfehler.
  Keine offenen Muss-Findings. Bericht unverändert abgelegt; seine vier
  nicht blockierenden Beobachtungen bleiben sichtbar.

| Lieferung | Unabhängig abgenommener Produkt-SHA | Bericht |
|---|---|---|
| L1-1 | `6cbf8de422cd8c0c8c9ec1ea900d729053ea660f` | [PASS](reviews/L1-1-review.md) |
| L1-2 | `39f48ca239c44b27a81e5562a58c717c90253534` | [PASS](reviews/L1-2-review.md) |
| L1-3 / Phase 1 | `62c0870a58db677a796143aa8f89578f2e3d7f43` | [Kumulativer PASS](reviews/L1-3-review.md) |

L1-1 vervollständigt vordefinierte Geometrien, kanonische INTERLIS-Owner,
Import-/Präfixqueries, eindeutige Kontextherkunft und Anhang-A-Reihenfolge.
L1-2 ergänzt numerische Unterteilungen mit exakten Faktoren, kontrollierten
Ressourcenabbrüchen und öffentlichen Typqueries. L1-3 korrigiert vollständige
Besitzerpfade/Herkunft, FINAL-/EXTENDED-Prüfungen, importierte finale Basen,
gemeinsame Assoziationsattributregeln und Topiczyklen. Zwölf neue öffentliche
JVM-Testmethoden und 40 portable Fälle in L1-3. Fachliche Berichte:
[L1-1](implementation/L1-1.md),[L1-2](implementation/L1-2.md),[L1-3](implementation/L1-3.md).

Fortgeltende ausdrückliche Nutzerentscheidungen für beide Sprachen:

- TYPE MODEL erlaubt Strukturen und abstrakte Klassen wie ili2c; TOPIC bleibt verboten.
- Gleiche/absteigende/gemischte Exponenten an Typgrenzen bleiben zulässig;
  Werteordnung, Genauigkeit und Mantissenprüfungen bleiben erhalten.
- Allgemeine Konstanten/Einheitenfaktoren behalten das breitere Exponentialprofil.
- Kontinuierliche Unterteilungen ohne bestimmbaren Faktor bleiben zulässig;
  nur nachweisbare Faktorwidersprüche werden abgelehnt. Numerischer Vorgänger,
  Ganzzahligkeit, nichtnegative Grenzen und CIRCULAR bleiben geprüft;
  bekannte Faktoren werden exakt verglichen, FUNCTION nie ausgeführt.
- Die frühere Metadatenfreigabe und die sechs freigegebenen 2.3-OID-/CONTRACTED-
  Fälle bleiben erhalten. Die 19. CIRCULAR-Referenzausnahme folgt der bestätigten
  Regel und ist seit **30.10.2026 auch für den konkreten Fall ausdrücklich
  freigegeben**; [Entscheidungsnachtrag](implementation/L1-2.md#ausdrückliche-einzelfreigabe-vom-30102026).

**Keine offenen Phase-1-Blocker oder ausgelassenen fälligen Prüfungen.**
Phase 2 wird nun mit L2-1 bearbeitet; der frühere Phase-1-Abschluss bleibt unverändert.
Die globale D2-Revalidierung bleibt PENDING_USER_REVALIDATION; dieser PASS ist
keine globale G2-/P3-/P4-/P5-Freigabe. Frühere FAIL-Berichte und Rohbelege bleiben erhalten.
Der Abschlusscommit und der spätere Entscheidungsnachtrag betreffen ausschliesslich
Bericht und Dokumentstatus; Produkt-/Test-/Buildquellen sowie die geprüften
Erwartungen bleiben unverändert. Die Reviewberichte behalten ihren tatsächlichen
Entscheidungsstand zum Prüfzeitpunkt.

Verbindlich: [Plan](EXECUTION_PLAN.md),[Reviewanweisung](OPENCODE_REVIEW_PROMPT.md).
