# Unabhängiger Review: Standardcache und Offlinebeschaffung

## 1. Prüfgegenstand

| Feld | Wert |
|---|---|
| Kandidat | `cf585ec1ea2270bafed4083a8ebfa2e68de05133` |
| Basis | `fd07e194f14c8ee46debe930871a6c18fb72242d` |
| Worktree | `/Users/stefan/sources/interlis-java/build/review/repository-cache/r1/worktree` |
| HEAD vor/nach Prüfung | `cf585ec1ea2270bafed4083a8ebfa2e68de05133` |
| Arbeitsbaum | sauber; keine getrackten Änderungen |
| Distribution | `build/reviewer/distribution` |
| JDK | Temurin `25.0.3+9-LTS` |
| Reviewer | OpenCode 2.0.28, DeepSeek V4.1 Flash 1017, `deepseek/deepseek-flash`, `api.deepseek.com` |

Gelesen wurden Root-`AGENTS.md`, `docs/spec/AGENTS.md`, die betroffenen
Spezifikationskapitel 08, 09, 13–18, der vollständige Diff
`fd07e19..cf585ec`, die Produktions- und Testquellen sowie die Rohbelege unter
`docs/implementation/repository-cache/evidence`.

**Prüfgrenzen:** Kein Gradle, kein vollständiger Korpus, keine Benchmarks, keine
Browser-, Plattform- oder Releaseprüfung. Kein Python/pip/uv. Keine Änderungen an
getrackten Dateien, keine Commits/Pushes, keine Delegation. Scratchcode, Inputs
und Logs liegen ausschliesslich unter `build/reviewer`. Netzwerkzugriffe erfolgten
nur gegen lokale Testserver (`127.0.0.1`); der DeepSeek-Transport wurde von
OpenCode ausgeführt.

## 2. Nachweisaudit der Originalbelege

### 2.1 JUnit-Rohbelege

Die Original-XMLs unter `evidence/interlis-model`, `evidence/interlis-repository`
und `evidence/interlis-compiler-cli` wurden unabhängig ausgezählt:

| Modul | Tests | Suites | Failures | Errors | Skips |
|---|---:|---:|---:|---:|---:|
| `interlis-model` | 2 | 1 | 0 | 0 | 0 |
| `interlis-repository` | 63 | 12 | 0 | 0 | 0 |
| `interlis-compiler-cli` | 36 | 6 | 0 | 0 | 0 |
| **Total** | **101** | **19** | **0** | **0** | **0** |

Das entspricht der behaupteten Bilanz. Die Zeitstempel der finalen gezielten
Läufe (`RepositoryCacheContractTest` und `RepositoryClosureContractTest` um
16:24) passen zu den Gradle-Logs; die übrigen Suites sind ältere, unveränderte
Belege. Es wurden keine Skips oder Fehler gefunden. Die Testanzahl allein ist
kein PASS-Beleg; die fachliche Prüfung erfolgt in Abschnitt 3 und 4.

### 2.2 CLI-Prozessbelege

`cli-processes.json` enthält 10 Fälle, `validator-processes.json` 5 Fälle;
alle tatsächlichen Exits entsprechen den erwarteten Exits. Die HTTP-Zähler
belegen Erstdownload, 304-Revalidierung, Offline ohne HTTP und die
Validatorfälle. Die JAR-Hashes der drei Produktstände unterscheiden sich
nachvollziehbar:

| Stand | `interlis-repository`-JAR |
|---|---|
| 10 ursprüngliche Prozesse | `70eb37191324434075372774697536a73c7e8528a37ef928ca0ccc80d66e4884` |
| 5 Validatorprozesse | `770543939887fb08e976e6fb34380a5d8e72961f4aa698d91fd18af534c61f34` |
| finaler Produktstand | `e8f490a5df37518a71d5db35820d36edd3821ff2e922d7f7421510e429a572f9` |

Der Bericht trennt diese Stände in den späteren Absätzen ausdrücklich. Die
Formulierung «Die weiteren fünf Prozesse prüfen am finalen Produkt» (Zeile 103)
ist allerdings unpräzise: Die fünf Validatorprozesse liegen gemäss Bericht
(Zeilen 105–110) vor der letzten, rein diagnostischen Quellprovenienzkorrektur
und verwenden nicht das finale JAR. Der spätere Absatz und die JAR-Hashes lösen
den Widerspruch auf; die Szenarien der fünf Prozesse betreten den korrigierten
Pinfehlerpfad nicht. Der finale JAR-Stand wurde durch die eigenen Gegenproben in
Abschnitt 4 zusätzlich abgedeckt.

### 2.3 Hash- und Manifestprüfung

- `files_sha256`: **288** Produktions-/Buildinput-Dateien, alle `OK`.
- `test_sources_sha256`: **19** Testquellen, alle `OK`.
- `final_runtime_jars_sha256`: **7** JARs, alle `OK` gegen die bereitgestellte
  Distribution.
- Die Distribution enthält exakt die im Manifest genannten sieben JARs; JDK und
  Startskript sind lauffähig.
- Reproduzierbare Prüfung: `build/reviewer/counterprobes/evidence-audit/`
  (`production.log`, `tests.log`, `jars.log`, `junit-counts.txt`).

Es fehlen keine im Auftrag genannten Pflichtbelege. Die Testabdeckung hat
allerdings eine Lücke, die zum Finding F1 führt: Der Fall «Offline ohne Lock,
Modell nicht im gecachten Index, optionales `ilisite.xml` nicht gecacht» wird von
den 101 JUnit-Fällen und den 15 Prozessen nicht geprüft.

## 3. Kriterienbewertung

| ID | Status | Belege |
|---|---|---|
| CACHE-01 | fulfilled | `CliEnvironment` mit injizierbarem Home, alte Konstruktoren, `system()` aus `user.home`; `--cache`/`--no-cache`, Properties-Präzedenz und gegenseitige Overrides; `--explain-config`; reine lokale Läufe und Erklärungen ohne Cacheanlage. Eigene Proben: Default-Explain ohne Home, Custom-Cache relativ, `--no-cache` ohne Home, Konflikt Exit 2, Config-Override. |
| CACHE-02 | fulfilled | SHA-256-ContentStore bleibt digestadressiert; `metadata-v1/<sha256(URL)>.xml` versioniert, 64 KiB begrenzt, DTD/Entities aus, Attribute/Child-Whitelist; Inhalt vor atomaren Metadaten; alte Inhaltsdateien bleiben; keine GC. Hash- und Dateiprüfungen bestätigen die Struktur. |
| CACHE-03 | fulfilled | ETag vor Last-Modified, 304 mit digestgeprüften Bytes, genau ein unbedingter Retry bei fehlendem Body, voller Download ohne Validatoren, Abschlussvergleich. Eigene Probe: zweiter Lauf 4 Requests/0 Bodies, `If-None-Match`; Last-Modified-Repo 4 Requests/0 Bodies mit `If-Modified-Since`; Online-Änderung wurde erkannt und neu geladen. |
| CACHE-04 | **violated** | Offline ohne Lock nutzt denselben Suchpfad und null HTTP; erforderliche Index-/Modell-Misses nennen die Quelle. **Aber:** Ein fehlendes optionales `ilisite.xml` wird bei nicht gefundenem Modell als erforderliche Quelle behandelt (Finding F1). |
| CACHE-05 | fulfilled | Exakte Pins/Locks: Nach Änderung der URL lieferte der Offline-Lock weiterhin die alten Bytes (0 HTTP); derselbe Lock online ergab Exit 1 mit Lock-Mismatch. MD5-/Sprachprüfung und Modellwahl bleiben im Closurepfad; der Cache akzeptiert kein Schema. |
| CACHE-06 | fulfilled | `no-store` und nicht unterstützte `Vary`-Profile erzeugen weder Metadaten noch Content; Offline danach Exit 1 ohne HTTP. Redirects, 401/403/404/andere Status, Grössen-, Zeit- und Abbruchgrenzen sind im Code und in den bestehenden Tests abgedeckt. |
| CACHE-07 | **violated** | Korrupte Metadaten (DOCTYPE/externe DTD, ungültige Validatoren) sind Misses und werden online repariert; 304 ohne Body wird unbedingt nachgeladen; fehlende Cachedateien führen offline zum Fehler. **Aber:** Die Fehlerklassifikation des fehlenden optionalen `ilisite.xml` ist falsch (Finding F1, gleiche Ursache wie CACHE-04). |
| CACHE-08 | fulfilled | Schreibfehler werden einmal pro Hostzugriff gemeldet, ohne den Online-Erfolg zu verhindern; eigene Probe mit blockiertem Cachepfad: Exit 0, genau eine `CACHE_WRITE_UNAVAILABLE`-Warnung. `--no-cache` und `no-store` persistieren nicht; atomare Veröffentlichung und alte Lockinhalte bleiben erhalten. |
| CACHE-09 | fulfilled | `RunLifecycle.cacheLookup` nutzt die vorhandene Sequenz und den RunContext; Ereignisse sind typisierte Enum-Werte ohne URLs/Header/Pfade. Beobachterfehler werden isoliert. Der Schreibfehler wird als operative Warnung gerendert, ohne die Compiler-Gateentscheidung zu ändern. |
| CACHE-10 | fulfilled mit Vorbehalt | Provenienz aus Originaldateien, Exits, Skips und Hashes wurde unabhängig geprüft; eigene Gegenproben liegen vor. Specs/API-Register/README sind untereinander konsistent; die Abweichung der Implementierung zu Kapitel 08 §13 ist F1. Vorbehalt: Die Testabdeckung verfehlt den Fall aus F1; die Formulierung «am finalen Produkt» in `REPORT.md:103` ist gegenüber den JAR-Hashes unpräzise, wird aber später qualifiziert. |

## 4. Eigene Gegenproben

Alle Gegenproben liefen gegen die bereitgestellte Distribution mit JDK 25.0.3
und schrieben nur unter `build/reviewer`.

### 4.1 Vertragsproben der finalen Distribution

```sh
cd /Users/stefan/sources/interlis-java/build/review/repository-cache/r1/worktree
node build/reviewer/counterprobes/final-probes/run.cjs
```

Ergebnis: **61 Prüfungen, 0 Fehlschläge**, `exit 0` des Skripts. Abgedeckt:
Defaultcache, Homeinjektion, Erstdownload, 304, Offline ohne/mit Lock (jeweils
0 HTTP), `--no-cache` (kein Home, 4 Bodies, Offline Exit 1), Custom-Cache,
`--explain-config`, ETag-Präferenz, Last-Modified-Fallback, `no-store`, `Vary`,
Manipulation der Metadaten mit externer DTD (Offline Miss, kein DTD-Abruf),
Lock-Vorrang nach URL-Änderung, Konflikt Exit 2 und Config-Präzedenz. Die sieben
Distributions-JARs stimmen mit dem finalen Manifest überein.

### 4.2 Optionales `ilisite.xml` offline

```sh
node build/reviewer/counterprobes/optional-site/run.cjs
```

Ergebnis (Auszug aus `build/reviewer/counterprobes/optional-site/results.json`):

- `warm-cache-online`: Exit 0; Index- und Modellmetadaten vorhanden,
  `ilisite.xml` bewusst nicht vorhanden.
- `offline-missing-model`: Exit 1, 0 HTTP:
  `WARNING [ILI-RES-0008] ... repository links unavailable; continuing search.`
  danach
  `ERROR [ILI-RES-0003] The source 'http://.../repo/ilisite.xml' could not be provided: verified content is unavailable offline.`
- `online-missing-model`: Exit 1, 2 HTTP:
  `ERROR [ILI-RES-0001] The requested or imported model 'Absent' could not be found.`

Das ist die Reproduktion von F1: Dasselbe Repository und dieselbe fehlende
Modellanfrage werden online als «Modell nicht gefunden», offline dagegen als
«erforderliche Quelle `ilisite.xml` nicht verfügbar» klassifiziert.

### 4.3 Schreibfehlerwarnung

```sh
JAVA_OPTS="-Duser.home=$PWD/build/reviewer/counterprobes/final-probes/writefail/home" \
  build/reviewer/distribution/bin/interlis-compiler check \
  build/reviewer/counterprobes/final-probes/writefail/work/Projekt.ili \
  --repository "$PWD/build/reviewer/counterprobes/final-probes/writefail/work/" \
  --cache "$PWD/build/reviewer/counterprobes/final-probes/writefail/work/blocked"
```

Ergebnis: Exit 0, genau eine Warnung `CACHE_WRITE_UNAVAILABLE`; der Download
blieb nutzbar. Die Warnung enthält keinen Pfad und keine Zugangsdaten.

### 4.4 Evidenzaudit

```sh
shasum -a 256 -c build/reviewer/counterprobes/evidence-audit/production.sha256
shasum -a 256 -c build/reviewer/counterprobes/evidence-audit/tests.sha256
shasum -a 256 -c build/reviewer/counterprobes/evidence-audit/jars.sha256
```

Ergebnis: 288 Produktions-/Buildinput-Hashes `OK`, 19 Testquell-Hashes `OK`,
7 finale JAR-Hashes `OK`.

## 5. Finding

### F1 (P2): Fehlendes optionales `ilisite.xml` wird offline als erforderliche Quelle behandelt

**Dateien/Zeilen**

- `interlis-repository/src/main/java/guru/interlis/repository/RepositorySearch.java:136-139`
  – `site()` legt den `OFFLINE`-Grund des optionalen `ilisite.xml` in dieselbe
  `unavailable`-Map wie erforderliche Indizes und Modelle und meldet zusätzlich
  eine Warnung.
- `interlis-repository/src/main/java/guru/interlis/repository/RepositoryClosureService.java:237-241`
  – Bei leerem `matching` wird irgendein Nicht-`NOT_FOUND`-Eintrag aus
  `search.unavailable()` als erforderlicher Quellenfehler gewählt. Der erste
  solche Eintrag ist hier das optionale `ilisite.xml`.

**Reproduktion**

```sh
cd /Users/stefan/sources/interlis-java/build/review/repository-cache/r1/worktree
node build/reviewer/counterprobes/optional-site/run.cjs
```

Erwartet: Das optionale `ilisite.xml` darf fehlen und erzeugt eine sichtbare
Meldung; das nicht im gecachten `ilimodels.xml` vorhandene Modell wird als
`NOT_FOUND` (`ILI-RES-0001`) gemeldet, wie im Onlinefall.

Beobachtet: Offline Exit 1 mit `ILI-RES-0003` und der Quelle
`.../ilisite.xml`; online Exit 1 mit `ILI-RES-0001` für dasselbe Modell.

**Auswirkung**

Der Cachezustand (fehlendes optionales Metadatum) verändert die
Fehlerklassifikation und die CLI-Terminierung (`OPERATIONAL_FAILURE` statt
`INPUT_UNRESOLVED`). Das widerspricht CACHE-04 («Optionale Suchmetadaten dürfen
fehlen mit nachvollziehbarer Meldung») und CACHE-07 («fehlende Cachedateien und
Fehlerklassifikation»). Die Spezifikation sagt ausdrücklich: «Fehlende optionale
Metadaten bleiben sichtbare Suchausfälle, erforderliche fehlende Dokumente
ergeben OFFLINE mit Quellidentität.» Hier wird das optionale Metadatum als
erforderliches Dokument behandelt. Nutzer und Automatisierung sehen eine falsche
Quelle; der letzte lokal bekannte Stand des Modells wird nicht als «nicht
gefunden» gewertet.

**Empfehlung**

Optionale Site-Ausfälle von den erforderlichen Index-/Modell-Misses trennen:
Warnung beibehalten, aber bei fehlendem Modell im gecachten Index `NOT_FOUND`
melden; nur fehlende erforderliche `ilimodels.xml`-/Modellquellen als
`LOAD_FAILURE`/`OFFLINE` mit Quelle behandeln. Dafür einen Regressionstest
ergänzen: Offline ohne Lock, gecachtes `ilimodels.xml`, fehlendes
`ilisite.xml`, Modell nicht im Index. Keine Korrektur wurde in diesem Review
implementiert.

## 6. Ergebnis

**Urteil: FAIL.** CACHE-04 und CACHE-07 sind durch F1 verletzt; die übrigen
Kriterien sind erfüllt. Es gibt keinen offenen Blocker und keine fehlenden
Pflichtbelege. Der nächste fachliche Schritt ist die Korrektur der
Offline-Fehlerklassifikation für optionale Suchmetadaten mit einem gezielten
Regressionstest.
