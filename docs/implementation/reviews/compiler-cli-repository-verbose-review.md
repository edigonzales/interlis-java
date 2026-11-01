# Unabhängiger Review: Repository-Unterstützung und `--verbose`

- Phase: Repository/Verbose independent focused review
- Basis: `dfbf5f5782f30998a69a49d41f7ff6bf16e38525`
- Kandidat / Review-HEAD: `da3c1239f384a42b1b4f5d48ed11cbd03a7fe98e`
- Snapshot-Tree: `bc3c64e854f345d8dab085c98573cb10af59f36e`
- Reviewer-Laufzeit: OpenCode 2.0.28, Provider `deepseek`, Modell `deepseek/deepseek-flash`, API `api.deepseek.com`
- Arbeitsverzeichnis: separater Detached-Worktree `build/review/cli-repository-verbose/worktree`

## 1. Auftrag und Vorgehen

Der aktuelle Nutzerauftrag hat Vorrang: vorhandene Testnachweise unabhängig auditieren,
nur wenige gezielte Gegenproben ergänzen, keine vollständigen Testläufe wiederholen.
Eingehalten wurden insbesondere:

- keine Gradle-Aufrufe, keine vollständigen Test-/Korpus-/Benchmark-/Release-Läufe,
- keine Delegation, keine Produkt-/Test-/Dokumentänderung,
- keine Python-/pip-/uv-Aufrufe und keine Sperrprobe,
- lediglich kleine Scratch-Gegenproben mit der bereitgestellten Distribution unter
  `worktree/build/reviewer/counterprobes/`.

Geprüft wurden HEAD und Gitstatus des Worktrees (HEAD = Kandidat, Arbeitsbaum sauber,
Tree = `bc3c64e…`), der vollständige Diff `baseSha..candidateSha`, die anwendbaren
`AGENTS.md`, `docs/implementation/compiler-cli-repository-verbose.md`,
`docs/p3/CLI_PROFILE.md` sowie die betroffenen Spezifikationsstellen in Kapitel 08,
16, 17 und 18. Der Diff berührt ausschließlich CLI, Tests, Prozessprüfer und
Dokumentation; `interlis-compiler`, `interlis-model`, `interlis-projection` und
`interlis-workspace` bleiben unverändert (0 geänderte Dateien in deren `src`).

## 2. Unabhängiger Evidenz-Audit

### 2.1 Quellbindung und Snapshot

`source-binding.json` führt 952 Dateien. Ich habe **alle 952 Einträge** gegen den
Snapshot geprüft: 0 fehlend, 0 Hash-Abweichung.

Den `ProfileFingerprint` habe ich aus den tatsächlichen Dateien neu berechnet, nicht
aus dem Bericht übernommen. Dazu habe ich den Algorithmus aus
`ProfileFingerprint.java` unabhängig in Node nachgebaut (sortierte inkludierte
Dateinamen, `Dateiname + NUL + SHA256(Datei) + LF`, SHA-256 über die
Gesamtzeichenkette, UTF-8):

```
count: 952
recomputed: 42ffa09de752f0e5ff69772a44e127bfd25c0c93997f12dc1799c3e22b2372e2
sourceFingerprint: 42ffa09de752f0e5ff69772a44e127bfd25c0c93997f12dc1799c3e22b2372e2
reportFingerprint: 42ffa09de752f0e5ff69772a44e127bfd25c0c93997f12dc1799c3e22b2372e2
```

Die beiden zuvor ungetrackten Testklassen `SemanticRepositoryCommandsTest.java` und
`VerboseDiagnosticsContractTest.java` sind in der Bindung enthalten. Die
Rohberichte nennen korrekt `gitCommit=dfbf5f5…` (damaliger dirty Arbeitsbaum auf
`baseSha`). Die Gleichheit des Quellfingerabdrucks bindet diese Rohbelege an den
geprüften Snapshot-Inhalt; der Kandidaten-Commit ist ein reiner Snapshot desselben
Inhalts (Parent = `baseSha`). Die Abweichung `reportGitCommit` vs. `candidateSha`
ist damit transparent erklärt und kein Datenbindungsfehler.

### 2.2 Evidence-Manifest

`evidence-manifest.json` listet 53 Dateien mit Bytes und SHA-256. Gegenprobe:
**53/53 vorhanden, Bytes und SHA-256 identisch, 0 Abweichungen.** Enthalten sind
Gate-Log, Prozessbericht, SDK-Berichte, die installierte Distribution (Launcher,
`.bat`, 7 JARs) und alle JUnit-XMLs/bines.

### 2.3 Testumfang, Fehler und Skips

Aus den XML-Rohberichten ausgezählt (nicht aus dem Bericht übernommen):

| Modul | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| `interlis-compiler-cli:test` | 112 | 0 | 0 | 0 |
| `interlis-repository:test` | 41 | 0 | 0 | 0 |
| `interlis-compiler-cli:verificationTest` | 25 | 0 | 0 | 0 |
| **Summe** | **178** | **0** | **0** | **0** |

Das deckt die im Implementierungsbericht genannten 178 JUnit-Tests. Die neuen
Klassen tragen 7 (`SemanticRepositoryCommandsTest`) bzw. 3
(`VerboseDiagnosticsContractTest`) Tests bei; alle XMLs sind vorhanden. Die
`system-err`-Ausgabe der malformed-index-Fälle enthält erwartete XML-Parserfehler,
keine Testfehler.

### 2.4 Gate-Log, Prozess- und SDK-Belege

`gates.log`: `BUILD SUCCESSFUL in 27s`, `32 actionable tasks: 32 executed`,
`COMPILER_CLI_PROCESSES_PASS 55`, `SDK_PROCESS_PASS 9 commands`,
`SDK_ARCHIVE_CONSUMER_PASS 24b007e7…`. Der Zeitstempel passt zu den XML-Läufen.

`processes.json`: `status=passed`, 55 Fälle, alle mit Exitcode; 6 reale
PTY-/Redirectfälle (`auto/always/never-stderr-pty|file`) mit Exit 0. Die
Repository-HTTP-Fälle für `check`, `compile`, `analyze` und `diagram` sind
vorhanden; `repository-http-diagram` liefert `classDiagram`, die drei anderen
Exit 0. `compact`/`verbose` belegen Einzeilen-Kopfzeile vs. Frame. Die 7 im
Prozessbericht genannten Distributionsartefakte stimmen mit den
Manifest-Hashes überein (7/7).

`build/reports/sdk/*`: 9 Kommandos, `status=passed`. Die negativen Fälle haben
die erwarteten Nicht-Null-Exits (`check broken` = 1, `format --check` = 1,
`check --no-such-option` = 2); die übrigen sind 0. Der externe
Archivverbraucher listet 11 Einträge, deren Hashes mit dem Manifest
übereinstimmen.

## 3. Code-Review (Kandidat)

- **R1 (gemeinsame Vorbereitung):** `DefaultCompilerCli.execute` leitet
  `check`, `compile`, `analyze`, `diagram` auf eine gemeinsame Methode
  `semantic(...)`; `format` bleibt getrennt. Konfiguration, Root-/Dateiauswahl,
  Repositorybeschaffung über `RepositoryClosureService` und atomare Übernahme
  (`PreparedRepositoryUpdate.apply`) sind geteilt. `VERSION_CONFLICT` wird als
  `INCOMPLETE_ANALYSIS`, andere `UpdateRejectedException` weiterhin als Fehler
  behandelt.
- **R2 (Optionssatz):** `CommandSpec.standard` fügt den vollständigen
  Repository-Optionssatz für alle vier semantischen Befehle hinzu; `--root`
  erfordert kein `--repository`. `analyze`/`diagram` akzeptieren damit
  `--repository`, Pins, Cache, Offline, Locks, `--config`, `--explain-config`,
  `--warnings`. `format` lehnt Repositoryoptionen ab. Kein Defaultrepository.
- **R3 (Gatepflichten):** `analyze` ruft `workspace.analyze` ohne Compilegate auf
  und liefert Analysediagnostik; `diagram` verlangt `CompilationOutcome.Succeeded`
  und projiziert danach mit unveränderter Selektion
  (`rootModels().getFirst()`). Keine zweite Gateentscheidung.
- **R4 (Fehlerpfade):** Rejected/Ambiguous/Missing/Pins/Cache/Offline/Cancel/
  Stale/Collision/Write-Fehler werden typisiert abgebildet. Diagrammfehler
  liefern keinen stdout-Inhalt und keinen Lock.
- **R5 (Lockzeitpunkte):** `writeLock` wird für `check`/`compile` nach
  `Succeeded`, für `diagram` nach erfolgreicher Projektion und für `analyze`
  nach abgeschlossener Analyse aufgerufen. Der Lock enthält die aufgelösten
  Quellen (`Base`, `Mid`, `Root`) und dokumentiert keine semantische Gültigkeit.
- **V1/V2/V3:** `--verbose` ist ein einmaliges Nullwert-Flag für alle fünf
  Diagnosebefehle. `ConsoleDiagnosticRenderer` gibt ohne `verbose` nach der
  Kopfzeile zurück; mit Flag folgen Frame, Related Locations und Hints. Die
  Texte stammen aus `texts(workspace)`, also auch aus nachgeladenen
  Repositorymodellen. JSON umgeht den Renderer und bleibt unverändert.
- **V4:** `RenderOptions` ergänzt `boolean verbose`; der bisherige
  Vierparameterkonstruktor delegiert mit `verbose=true`, `defaults(boolean)` und
  die Renderer-Komfortoverloads bleiben ausführlich. `ConfigurationLoader`
  enthält keinen neuen `verbose`-Properties-Schlüssel.
- **D1:** README, `CLI_PROFILE`, Kapitel 08/16/17/18 und
  `docs/implementation-status.md` sind synchron. Die bestehenden
  Diagnose-/Farbprüfungen wurden nicht abgeschwächt, sondern verlangen für die
  ausführlichen Details ausdrücklich `--verbose`.

## 4. Eigene gezielte Gegenproben

Alle mit der eingefrorenen Distribution
`evidence/interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler`
und `JAVA_HOME=…/25.0.3-tem`. Rohausgaben liegen unter
`worktree/build/reviewer/counterprobes/out/`.

### G1 – Lokale transitive Closure, Locks und Offline-Wiederholung

| Kommando | Exit | Beobachtung |
|---|---:|---|
| `diagram --repository repo --root Root --cache repo-cache --write-lock repo.lock` | 0 | `classDiagram` auf stdout; Lock enthält `Base`, `Mid`, `Root` |
| `analyze --repository repo --root Root --cache repo-cache --write-lock analyze.lock` | 0 | Lock geschrieben |
| nach Entfernen aller Repo-Quellen: `diagram … --read-lock repo.lock --offline` | 0 | identische `classDiagram`-Ausgabe, kein stderr |
| nach Entfernen aller Repo-Quellen: `analyze … --read-lock analyze.lock --offline` | 0 | erfolgreich, kein stderr |

### G2 – Diagnosen aus Repositorymodellen, Kompakt/Verbose, JSON-Parität, Lockgrenzen

Repositorymodell `BaseBad.ili` mit Namenskonflikt (`Office`), geladen über
`--repository repo2 --root RootBad` (keine lokalen Dateien):

| Kommando | Exit | Beobachtung |
|---|---:|---|
| `check …` | 1 | genau 1 stderr-Zeile, kein `" \| "`, keine Related-/Hint-Zeile |
| `check … --verbose` | 1 | Kopfzeile + Frame aus `repo2/BaseBad.ili`, Related Location, Hint |
| `check … --json` vs. `--json --verbose` | 1/1 | `cmp` erfolgreich: **bytegleich** |
| `analyze … --verbose --write-lock analyze-bad.lock` | 0 | Diagnose vorhanden, Frame vorhanden, Lock geschrieben |
| `diagram … --write-lock diagram-bad.lock` | 1 | stdout 0 Bytes, **kein** Lock |

### G3 – Konfigurationspräzedenz, Root ohne Dateien, Flagvalidierung vor IO

| Kommando | Exit | Beobachtung |
|---|---:|---|
| `analyze --config settings.properties` (`repository`, `root=RootBad`) | 0 | Root ohne Dateien; Diagnose + Verbose-Frame |
| `analyze --config settings.properties --root Missing` | 1 | CLI-Root überschreibt Config-Root → `NOT_FOUND` |
| `diagram --config settings.properties --root RootBad --explain-config` | 0 | JSON zeigt `--root` mit `origin=CLI`, `--repository` mit `origin=CONFIGURATION_FILE` |
| `check --verbose --verbose missing.ili` | 2 | `duplicate option: --verbose` (vor Datei-IO) |
| `check --verbose=true missing.ili` | 2 | `unsupported option: --verbose=true` |
| `format --repository repo2 missing.ili` | 2 | `unsupported option: --repository` |
| `help --verbose` | 2 | `unsupported option: --verbose` |
| `diagram --json missing.ili` | 2 | `--json is not supported by diagram; use --format json` |

### G4 – HTTP-Closure, Verbose-Texte über HTTP und Offline ohne Serverkontakt

Lokaler Node-HTTP-Server, danach gestoppt. Request-Log: **20 vor** den
Offline-Läufen, **20 nach** den Offline-Läufen (keine neuen Zugriffe).

| Kommando | Exit | Beobachtung |
|---|---:|---|
| `diagram --repository http://…/repo/ --root Root --cache http-cache --write-lock …` | 0 | `classDiagram` |
| `analyze --repository http://…/repo2/ --root RootBad --verbose …` | 0 | Diagnose + Frame aus `http://…/repo2/BaseBad.ili` |
| `check --repository http://…/repo2/ --root RootBad --verbose` | 1 | Frame, Related Location, Hint über HTTP |
| Server gestoppt: `diagram … --read-lock … --offline` | 0 | identische `classDiagram`-Ausgabe |
| Server gestoppt: `analyze … --read-lock … --offline` | 0 | erfolgreich, kein Serverkontakt (20→20) |

Damit sind die riskantesten Pfade (transitive HTTP-Closure, Offline-Replay,
Repository-Diagnosetexte, Kompakt-/Verbose-Parität, JSON-Parität, Lockzeitpunkte,
Flagvalidierung vor IO) unabhängig von den vorhandenen Tests bestätigt.

## 5. Kriterienbewertung

| ID | Status | Begründung |
|---|---|---|
| R1 | **pass** | Gemeinsame `semantic`-Vorbereitung für alle vier Befehle; geteilter Resolver und atomare Übernahme; `format` getrennt. |
| R2 | **pass** | Vollständiger Optionssatz im Schema; `--root` ohne Dateien und ohne `--repository` funktioniert; Config-Präzedenz belegt; kein Defaultrepo. |
| R3 | **pass** | `analyze` ohne Compilegate (Exit 0 mit ERROR-Diagnose); `diagram` nur bei `Succeeded` mit unveränderter Selektion. |
| R4 | **pass** | Tests decken Pins, Mehrdeutigkeit, fehlende Modelle, defekte Indizes/Locks, Cachekorruption, Offline, Abbruch, Kollisionen, Schreibfehler; eigene Proben bestätigen Offline, Fehler, fehlende Diagrammartefakte. |
| R5 | **pass** | Lock nach Kompilierung/Projektion/Analyse; Analyse-Lock auch mit fachlichen Diagnosen; Diagrammfehler ohne Lock. |
| V1 | **pass** | Alle fünf Befehle akzeptieren einmalig `--verbose`; kompakt genau eine Kopfzeile; verbose Frame/Related/Hints. |
| V2 | **pass** | Repository-Texte werden für Frames verwendet (Datei- und HTTP-Repo belegt); bestehende Sanitierungs-/Revisionsprüfungen bleiben. |
| V3 | **pass** | JSON bytegleich mit/ohne Flag (eigene `cmp`-Probe und Vertragstest); übrige Ausgaben unverändert. |
| V4 | **pass** | Fünfte `RenderOptions`-Komponente; Vierparameterkonstruktor/Defaults bleiben ausführlich; kein neuer Properties-Schlüssel; ungültige/mehrfache Flags Exit 2 vor IO. |
| D1 | **pass** | Hilfe, README, CLI_PROFILE, Kapitel 08/16/17/18 und Status stimmen mit der Umsetzung überein; Goldens nicht abgeschwächt. |

## 6. Nicht blockierende Beobachtung

Die generierte Hilfezeile für `diagram` listet weiterhin `[--json]`, obwohl
`diagram --json` bewusst mit Exit 2 und dem Hinweis auf `--format json`
abgelehnt wird. Das ist **vorbestehend** (auch in `baseSha`), in
`docs/evidence/p3/cli-options.md` ausdrücklich als abgelehnte Option dokumentiert
und nicht durch diesen Auftrag eingeführt. Es ist kein blockierender Befund für
R1–R5/V1–V4/D1, wird aber als optionale spätere Hilfebereinigung notiert.

## 7. Grenzen

- Keine vollständigen Testwiederholungen, keine Gradle-/Korpus-/Benchmark-/
  `releaseCheckP3`-Läufe (auftragsgemäß).
- Keine neue Linux-/Windows-/Browser-/Portable-Releasequalifikation; diese ist
  für den geprüften Snapshot nicht gefordert.
- Die vorhandenen Rohbelege wurden auf Inhalt, Hashbindung, Zählungen, Skips,
  Exits und Streams geprüft; sie wurden nicht neu erzeugt.

## 8. Urteil

**PASS.** Alle geforderten Kriterien sind durch vorhandene, korrekt gebundene
Rohbelege und durch eigene gezielte Gegenproben belegt. Es gibt keinen
blockierenden Befund und keine fehlende Pflichtevidenz im beauftragten Umfang.
