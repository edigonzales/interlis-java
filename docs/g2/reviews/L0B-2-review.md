# Unabhängiger Review: L0B-2

Lieferabschnitt / Phase / letztes Phasengate?: **L0B-2**; Phase **0b**;
**kein** letztes Phasengate (das kumulative 0b-Gate ist L0B-3).
Implementierungsagent: Codex
Prüfagent: OpenCode v2.0.28; tatsächliche Provider-/Modellkonfiguration:
**DeepSeek direkt**, `provider=deepseek`, `model=deepseek-flash`
(UI-Bezeichnung „DeepSeek V4.1 Flash 1017“), **kein OpenRouter**. Die Angabe
stammt aus der Sitzungs-/Nutzerkonfiguration, nicht aus einer
Modellselbstauskunft.
BASE_SHA: `2d33c71c7afde03b8f43a7923e0eaeb1a69d9045`
CANDIDATE_SHA (durch git überprüft): `411585a650b4da42cc09dd0efbf3157697dcc178`
Prüfumgebung / Toolchain: sauberer Worktree
`/private/tmp/interlis-java-L0B-2-review-411585a`; macOS; Temurin
**25.0.3+9**; Gradle-Wrapper **9.8.1**; Node **v24.22.0** (nur vorhandene
Umgebung, für L0B-2 nicht erforderlich); Python **3.9.6** ausschliesslich für
den zulässigen einmaligen Basis-/pypdf-Abgleich, pypdf **6.10.0** über `uv`.
`git status --porcelain` war vor und nach der Prüfung leer; der Kandidat wurde
nicht verändert.

## Kriterienprüfung

| Kriterium-ID | Erfüllt / verletzt / nicht prüfbar | Eigener Test-/Code-/CI-Beleg | Finding-ID |
|---|---|---|---|
| **AC0B-04** | **erfüllt** | `./gradlew check conformanceCheck --rerun-tasks` → Exit **0**, 47/47 Tasks ausgeführt, **728** reguläre JVM-Tests + **136** Java-Evidenztests + **15** frische Compiler-Assertionmethoden, je **0** Errors/Failures/Skips. Die gebundene Assertion-XML wurde auf Suite/Klasse/Methode, Zähler, Zeitstempel und Hashes geprüft; `g2EvidenceTest` lief als Teil von `check`. Eigene End-to-End-Gegenprobe „richtige Methode, falsche Klasse“ mit aktualisiertem Hash → Exit **1**, `Wrong JUnit testcase class` (`AssertionEvidence.java:57`); danach `g2EvidenceTest` wieder Exit **0**. | — |
| **AC0B-05** | **erfüllt** | Eigener pypdf-6.10.0-Originallauf gegen die beiden Cache-PDFs: **435** Seiten, **2721** Absätze, **311** Abschnitte, **369** Compilerbedingungen, **299** Exzerpte; alle exakten Codepoint-Bereiche, Zitate und Seiten-/Zitathashes stimmen. PDF-/Extraktor-/Versionsbindung, Quell-/Inventar-/Matrixpins und die 25 Python-Löschungen geprüft; nur der dokumentierte Kapitel-03-Pin und aktuelle Zeilenlocatoren samt umschliessendem Matrixhash sind nachgeführt. Falscher Cache-PDF-Canary → Exit **1**. | — |

## Eigene Gegenprobe

### Gegenprobe 1 (AC0B-04): richtige Methode aus falscher Klasse

**Hypothese und fachliches Risiko:** Das Gate könnte einen Testfall nur über den
Methodennamen akzeptieren und die Klassenidentität nicht wirksam binden,
insbesondere wenn ein manipulierter Report mit passendem Hash vorliegt.

**Neu erstellter Testinput / exakter Pfad:** Ich habe die frische Datei
`interlis-compiler/build/test-results/g2AssertionTest/TEST-guru.interlis.compiler.A2R3CaseSensitivityCounterProbeTest.xml`
in eine Kopie gesichert, im Testcase `classname` von
`guru.interlis.compiler.A2R3CaseSensitivityCounterProbeTest` auf
`guru.interlis.compiler.WrongCounterProbeClass` geändert, den Methodennamen
`exactCaseControlsIdentityBindingAndPublicNavigation()` unverändert gelassen und
den Hash in `interlis-compiler/build/g2-assertions.json` auf die manipulierten
Bytes aktualisiert. Produktquellen wurden nicht verändert; die Änderung liegt
nur unter `build/`. Sicherung:
`build/review-l0b2/saved-assertion-xml/`,
`build/review-l0b2/g2-assertions.saved.json`.

**Erwartetes Verhalten:** Das Gate muss die falsche Klasse trotz gleichem
Methodennamen und trotz passendem Hash ablehnen.

**Kommando / Exitcode / beobachteter Output:**
`JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem ./gradlew :interlis-cli:evidenceTest -x :interlis-compiler:g2AssertionTest --rerun-tasks`
→ Exit **1**; XML-Fehler:
`java.lang.IllegalArgumentException: Wrong JUnit testcase class` bei
`AssertionEvidence.java:57`. Rohlog:
`build/review-l0b2/counter-wrong-class.log`.
Wiederherstellung mit
`./gradlew g2EvidenceTest --rerun-tasks` → Exit **0**, danach wieder **136**
Evidenztests und **15** Assertiontests, 0 Fehler/Skips
(`build/review-l0b2/evidence-restored.log`).

**Ergebnis und Schlussfolgerung:** Die Klassenidentität wird tatsächlich
geprüft; das Gate ist nicht auf einen Methodennamen allein reduzierbar.
AC0B-04 ist damit auch negativ belegt.

### Gegenprobe 2 (AC0B-05): unabhängiges pypdf-Originextraktionsorakel

**Hypothese und fachliches Risiko:** Die eingefrorenen Gzip-Seitentexte,
Codepoint-Offsets oder Zitate könnten gegenüber den Original-PDFs veraltet
sein, Java könnte stillschweigend eine andere Offset-Einheit verwenden, oder
Pins könnten unbemerkt geändert worden sein.

**Neu erstellter Testinput / vollständige Quelle:** Eigenes, nicht aus dem
Repository kopiertes Skript
`build/review-l0b2/norm_deep_oracle.py` (und das kürzere
`build/review-l0b2/norm_parity.py`). Es liest die beiden Original-PDFs direkt
mit **pypdf 6.10.0**, extrahiert alle Seiten neu und prüft unabhängig:

- PDF-SHA gegen `g2-rule-matrix.json`;
- jede Seite gegen Gzip-JSON und die aufgezeichneten Seitenhashes;
- jeden der 2721 Absätze als exakten Codepoint-Slice inklusive Seiten-/Zitathash;
- vollständige Nicht-Whitespace-Abdeckung jeder Seite;
- alle 311 Abschnittslocators;
- alle 369 Compilerbedingungen und ihre 1476 geplanten Fälle, inklusive
  zusammengesetzter Zitate und Hashes;
- alle aktiven Matrix-/Katalog-/Alternativ-/Worttabellen-Exzerpte
  (299 Referenzen) und deren `checkedExcerptIds`-Reihenfolge.

**Erwartetes Verhalten:** Nur bei exakter Übereinstimmung aller Bereiche,
Zitate und Hashes darf das Orakel mit Exit 0 enden; jede Abweichung muss
scheitern.

**Kommando / Exitcode / beobachteter Output:**
`uv run --no-project --with 'pypdf==6.10.0' python build/review-l0b2/norm_deep_oracle.py`
→ Exit **0**:

```text
2.3 pages 195 paragraphs 1118 sections 139 compilerConditions 155 excerpts 144 OK
2.4 pages 240 paragraphs 1603 sections 172 compilerConditions 214 excerpts 155 OK
TOTAL pages=435 paragraphs=2721 sections=311 compilerConditions=369 all exact pypdf slices/quotes/hashes OK
```

`uv run --no-project --with 'pypdf==6.10.0' python build/review-l0b2/norm_parity.py`
→ Exit **0**: frische pypdf-Seiten sind byte-/textgleich zu den Gzip-JSONs und
zu den aufgezeichneten Seitenhashes. Rohlogs:
`build/review-l0b2/norm-deep-oracle.log`,
`build/review-l0b2/norm-parity.log`.

**Ergebnis und Schlussfolgerung:** Die konservierten Texte sind echte
pypdf-6.10.0-Seiten der gepinnten Original-PDFs; die Codepoint-Offsets und
Zitate sind exakt. Es wird keine PDFBox-Äquivalenz angenommen. AC0B-05 ist
positiv und negativ belegt.

## Pflichtprüfungen

Ausgeführte Kommandos, tatsächlicher Exitcode, Tests/Fehler/Skips:

| Kommando | Exit | Ergebnis |
|---|---:|---|
| `git rev-parse HEAD` | 0 | `411585a650b4da42cc09dd0efbf3157697dcc178` (entspricht Handoff) |
| `git status --porcelain=v1` vor/nach Prüfung | 0 | leer; keine Produktänderung |
| `git diff --stat BASE..CANDIDATE` | 0 | 46 Dateien, +1386/−4238; 25 Python-Löschungen; keine Compiler-/Produktquellenänderung |
| `git diff --check BASE..CANDIDATE` | 0 | keine Whitespace-/Konfliktfehler |
| `JAVA_HOME=... ./gradlew check conformanceCheck --rerun-tasks` | **0** | `BUILD SUCCESSFUL in 1m 41s`, 47/47 Tasks; SDK PASS; T2 siehe unten |
| `JAVA_HOME=... ./gradlew g2EvidenceTest --rerun-tasks` (nach Gegenprobe) | **0** | 136 Evidenztests + 15 Assertiontests, 0 Fehler/Skips |
| `python3 -m unittest discover -s tools/reference -p 'test_g2_*.py'` im separaten Basisarchiv | 0 | **59** Alt-Tests, 0 Fehler (Basischeckout `2d33c71…`) |
| pypdf-Parität/Orakel (siehe oben) | 0 | 435 Seiten/2721 Absätze/311 Abschnitte/369 Bedingungen/299 Exzerpte exakt |
| Cache-Canary `INTERLIS_NORM_CACHE=build/review-l0b2/bad-norm-cache ./gradlew prepareG2Norms --rerun-tasks` | **1** | `Changed norm PDF: 2.3 (...)`; falsche Originalbytes werden abgelehnt |
| Wrong-Class-Canary (siehe oben) | **1** | `Wrong JUnit testcase class` |

Rohberichte / Artefakte und Bindung an den Kandidaten (alle eigenen Pfade unter
`/private/tmp/interlis-java-L0B-2-review-411585a/`):

- `build/review-l0b2/full-check.log` (frischer Gesamtlauf, Exit 0)
- `build/review-l0b2/counts.log` (728/136/15, je 0/0/0)
- `interlis-*/build/test-results/test/*.xml` (149 Dateien, 728 Testcases)
- `interlis-cli/build/test-results/evidenceTest/*.xml` (4 Suiten, 136 Testcases)
- `interlis-compiler/build/test-results/g2AssertionTest/*.xml` (11 Suiten, 15 Testcases)
- `interlis-compiler/build/g2-assertion-start.txt` und
  `interlis-compiler/build/g2-assertions.json` (Frischemarker und Hashes; alle
  11 XML-Hashes stimmen mit der Map überein)
- `build/conformance/report.json` und
  `build/reports/conformance/conformance-summary.json`
- `build/review-l0b2/counter-wrong-class.log`,
  `build/review-l0b2/evidence-restored.log`,
  `build/review-l0b2/cache-canary.log`,
  `build/review-l0b2/norm-deep-oracle.log`,
  `build/review-l0b2/norm-parity.log`
- `build/review-l0b2/norm_deep_oracle.py`, `build/review-l0b2/norm_parity.py`
- separater Basisarchiv-Checkout: `/private/tmp/interlis-java-L0B-2-review-base`
  (nur Alt-/pypdf-Abgleich; nicht der Kandidat)

Die XML-Frische wurde unabhängig geprüft: der Assertion-Startmarker liegt bei
`2026-10-29T16:22:29.852351Z` (Wiederherstellungslauf), alle Assertion-XML-
Zeitstempel liegen danach (`16:22:30.1`–`16:22:30.6`Z), alle Dateien sind neuer
als der Marker, und alle Bytes entsprechen den aufgezeichneten SHA-256-Werten.
Die 4 Evidenz-XML-Suiten (136 Testcases) wurden im Wiederherstellungslauf
ebenfalls neu erzeugt (0 Fehler/Skips).

Nicht ausgeführte fällige Prüfungen mit Grund: **keine für L0B-2**. Die volle
Safari-/Browser-/Web-Image-Matrix und der Python-freie Gesamtlauf sind
ausdrücklich L0B-3 und keine L0B-2-Pflicht; es wird keine Pythonfreiheit
behauptet.

Conformance-Fallmenge; rohe und projektspezifische Bilanz:

- Auswahl **593/610**, 17 INTERLIS-1.0-Fälle ausgeschlossen; Suite-Commit
  `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3` unverändert.
- Rohbilanz: **574 PASS / 18 FAIL / 1 XFAIL**, kein ERROR/XPASS,
  Runner-Roh-Exit **1**.
- Projektbilanz: **574 PASS / 19 XFAIL**, kein FAIL/ERROR/XPASS,
  Task-Exit **0**; **18/18** normative Zusatzbewertungen `PASS`. Jede der 18
  Prüfungen hat Original- und Positivkontrollkommando, erwarteten Exit
  (`0` bei `accepted`, `1` bei `rejected`) und Exit `0` der Kontrolle; die
  strukturierten Diagnosen enthalten Code, Severity, Phase, Quellbereich und
  Argumente, soweit der jeweilige Fall Diagnosen erwartet.
- Rohe und projektbezogene Bewertung bleiben getrennt; die 18 FAILs der
  Rohbilanz werden ausschliesslich über die 18 Assessments zu XFAIL
  reklassifiziert. Keine Baseline-/XFAIL-Änderung.

Norm-/Suite-/XFAIL-/Baseline-/Quellpin-Unverändertheit:

- `git diff` über `docs/evidence` zeigt nur:
  `g2-rule-matrix.json` mit geändertem `sourceInventory.sha256` (umschliessender
  Hash), `g2-source-inventory.json` mit dem Kapitel-03-Pin und zehn aktuellen
  Zeilenlocatoren (199–208 → 201–210), sowie die neu hinzugefügten
  `norm-text/*.gz`/`README.md`.
- Alle übrigen Evidenz-JSONs sind bytegleich zum Basiscommit; insbesondere
  `g2-norm-inventory.json`, `g2-norm-conditions.json`, `g2-norm-crosswalk.json`,
  `g2-grammar-inventory.json`, `g2-builtin-inventory.json`,
  `g2-spec-contract-inventory.json`, `g2-test-candidate-index.json`,
  `g2-a3-semantic-pairs.json`, `g2-normative-decision-packet-2026-10-28.json`,
  `normative-expectations.json` und `baseline.json`.
- Die PDF-Pins `2.3`/`2.4` sind unverändert; die Cache- und `build/norms`-Bytes
  stimmen mit ihnen überein.
- Alle 9 Matrix-Inventarpins, der Crosswalk-Pin, der R3-Laufpin, 21 Quellpins
  und 12 Kandidatentest-Quellhashes stimmen mit den tatsächlichen Dateien.
  `latestGrammarSha256` stimmt mit dem aktuellen Parser überein. Der historische
  `matrix.inventoryPins`-Eintrag ist weiterhin der alte A1-Parserhash und wird
  nicht als aktueller Pin ausgegeben; der aktuelle Parser ist über
  `latestGrammarSha256`/`sourceInventory.pins` gebunden. Das ist die
  dokumentierte historische Besonderheit, keine stille Pinänderung.
- Die 332 historischen Regel-/Schritt-/Assertion-Mappings wurden mit Pythons
  exakter `json.dumps(sort_keys=True, ensure_ascii=False, separators=(",", ":"))`-
  Kanonisierung nachgerechnet: 0 Abweichungen.
- Die 289 nummerierten Spec-Anforderungen stimmen mit den aktuellen
  `docs/spec`-Zeilen samt Zeilenhashes; die 12 unnummerierten Bindungen sind
  eindeutig. Die historischen Zeilennummern im Spec-Contract-Inventar weichen
  in 32 Fällen von den heutigen Locatoren ab, während Text und Zeilenhashes
  exakt gleich bleiben; das ist die dokumentierte, nicht als aktueller Locator
  ausgegebene historische Aufzeichnung.
- Die neu hinzugefügten Gzip-Dateien haben die im README genannten SHA-256-
  Werte (`0fdf67…` für 2.3, `9ef53d…` für 2.4) und enthalten Extraktor
  `pypdf 6.10.0`, Einheit `Unicode code points`, 195 bzw. 240 Seiten.

Python-Migrations-/Phaseninvarianten soweit relevant:

- `git ls-files '*.py'` liefert **9** verbleibende Dateien (L0B-3-Werkzeuge);
  der Basiscommit hatte 34. Die 25 L0B-2-Löschungen sind in
  `docs/g2/python-migration.md` vollständig klassifiziert (12 `g2_*.py`,
  12 zugehörige Tests, `author_g2_predefined.py`).
- Ein Scan über aktive Gradle-/CI-/Shell-/Node-/YAML- und verbleibende
  Pythonpfade findet keine Referenz auf die gelöschten 25 Dateien. Die drei
  verbleibenden `python3`-Aufrufe in `build.gradle` betreffen ausschliesslich
  die ausdrücklich für L0B-3 vorgesehenen Aufgaben `geoCorpus`,
  `jvmBenchmark` und `sdkProcessCheck`.
- Die 59 Alt-Tests liefen im separaten Basisarchiv mit separatem
  `PYTHONPYCACHEPREFIX` erfolgreich; die Löschung ist damit nicht als
  Testverlust ohne Ersatzbeleg erkennbar. Die entfernten Snapshot-/Tar-/
  Grammar-Autorentests sind in der Migrationsmatrix als historische
  Erzeugungsschritte begründet; aktive Quell-/Norm-/Assertionbindungen sind
  durch die neuen Java-Tests erhalten.
- Keine Pythonfreiheitsbehauptung; L0B-3 bleibt das kumulative 0b-Phasengate.

Kumulative Phasenregression, wenn fällig: **nicht fällig** (L0B-2 ist nicht die
letzte Lieferung der Phase 0b). Die L0B-3-Gates wurden nicht begonnen.

## Findings

Keine offenen Muss-Findings.

Beobachtungen ohne Finding (historisch, unverändert und nicht als aktuelle
Abnahme verwendet):

- `matrix.inventoryPins` enthält absichtlich den alten A1-Parserhash; aktuell
  gelten `latestGrammarSha256` und `sourceInventory.pins`.
- 32 historische Spec-Zeilennummern liegen vor früheren Dokumenteinfügungen;
  die aktuellen Zeilenlocatoren und -hashes im Quellinventar sind geprüft.
- Der historische Markdown-Präsentationshash des Entscheidungspakets ist nicht
  der aktive Vertrag; der unveränderte JSON-Paket-/Norm-/Erwartungsvertrag ist
  aktiv geprüft.
- Leere Locator-Einträge im offenen Entscheidungspaket sind bestehende
  Normpaketlücken und keine Annahmefreigabe; die 18 Normentscheidungen bleiben
  offen, ebenso `CANDIDATE_ONLY`.

## Urteil

**PASS**

Beide Muss-Kriterien `AC0B-04` und `AC0B-05` sind am exakten Kandidaten-SHA
`411585a650b4da42cc09dd0efbf3157697dcc178` durch eigene, tatsächlich
ausgeführte Prüfungen belegt. Der frische Gesamtlauf war grün
(728/136/15 Tests, 0 Fehler/Skips), die T2-Roh-/Projektbilanz ist korrekt
getrennt, die Normtexte und Zitate sind unabhängig gegen pypdf 6.10.0 verifiziert,
und zwei eigene Negativproben zeigen die Wirksamkeit von Klassenbindung und
PDF-Pin. Es gibt keine offenen Muss-Findings und keine ausgelassene fällige
L0B-2-Pflichtprüfung.

Nächster konkreter Schritt: **L0B-3** erst nach diesem PASS beginnen; das
kumulative Python-freie 0b-Gate inklusive Safari/Browser/Web Image bleibt dort
fällig. Der Bericht liegt ausserhalb des geprüften Commits; der Kandidat wurde
nicht verändert, es gab keinen Commit/Push und keinen Beginn von L0B-3.
