# CLI: Repositories und optionale Diagnosedetails

Stand 01.11.2026: beauftragter Umfang implementiert und gezielt verifiziert.
Ausgangsstand `dfbf5f5782f30998a69a49d41f7ff6bf16e38525`, sauberer Arbeitsbaum
vor Beginn. Produkt, Tests und Dokumentation einschliesslich des unveränderten
Reviewberichts und der anschliessenden Hilfekorrektur werden auf Nutzerauftrag
gemeinsam versioniert. Dafür werden keine Testläufe wiederholt; kein Push oder Release.
Historische CLI-/COLOR-/G2-Abnahmen behalten ihren ursprünglichen Produktstand.

## Verträge und Umsetzung

- CLI-001–007, RES-001/004/007–009/011, LOG-002/003/005: `check`, `compile`,
  `analyze` und `diagram` verwenden eine gemeinsame CLI-interne Vorbereitung
  für Konfiguration, Dateien, Roots, Repositorybeschaffung und atomare Übernahme.
  Der vollständige Optionssatz umfasst Repositories, Modell-/Quellenpins,
  Cache, Offlinebetrieb, Locks, Konfiguration, Herkunftserklärung und Warnungspolicy.
  Benannte Roots funktionieren ohne Dateiargumente; keine Defaultrepositories.
- `analyze` behält vollständige Analysediagnostik ohne Compilegate; `diagram`
  benötigt weiterhin ein gültiges Schema. Aktuelle Repositorytexte werden auch
  an die Diagnosepräsentation übergeben. Fehler-/Abbruch-/Konfliktpfade und
  Kollisionsschutz bleiben gemeinsam. `format` arbeitet rein syntaktisch.
- Locks werden nach erfolgreicher Kompilierung, erfolgreicher Diagrammprojektion
  beziehungsweise abgeschlossener Analyse geschrieben. Bei `analyze` dürfen
  fachliche Diagnosen vorliegen; der Lock dokumentiert ausschliesslich Quellen.
- DIAG-001–003/006/009, CLI-004/006: fünf Diagnosebefehle akzeptieren einmalig
  `--verbose`. Ohne Flag erscheint nur die Kopfzeile, mit Flag zusätzlich Frame,
  verwandte Fundstellen und Hinweise. JSON, Diagnosedaten, Sprache, Farbe und
  Fachartefakte bleiben unabhängig davon. Kein weiterer Betriebslogmodus.
- `RenderOptions` ergänzt `boolean verbose`. Bestehende Vierparameterkonstruktion,
  Defaults und Renderer-Overloads bleiben ausführlich; das CLI wählt den kompakten
  Default ausdrücklich. Keine Compiler-/Modell-/Wire-Vertragsänderung.

Synchronisiert: README, CLI_PROFILE sowie Spezifikationskapitel08/16/17/18.
Bestehende Office-/COLOR-Textprüfungen verlangen für ausführliche Details nun
explizit `--verbose`; Originalmodelle und fachliche Diagnoseerwartungen bleiben
unverändert.

## Ausgeführte Abnahmen

macOS / Darwin arm64, Temurin25.0.3+9, Repository-Wrapper Gradle9.8.1:

```sh
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem ./gradlew \
  :interlis-compiler-cli:test :interlis-repository:test \
  :interlis-compiler-cli:verificationTest \
  compilerCliCheck sdkProcessCheck sdkArchiveCheck \
  --rerun-tasks --console=plain
```

Ergebnis: **BUILD SUCCESSFUL, Exit0, 27s, alle32 Tasks ausgeführt**.

| Prüfung | Tatsächliches Ergebnis |
|---|---|
| CLI-JUnit | 112 Tests, 0 Failures/Errors/Skips |
| Repository-JUnit | 41 Tests, 0 Failures/Errors/Skips |
| CLI-Verifikationsprüfer | 25 Tests, 0 Failures/Errors/Skips |
| compilerCliCheck | 55 reale CLI-Fälle und 6 PTY-/Redirectfälle bestanden |
| sdkProcessCheck | 9 reale SDK-Kommandos bestanden |
| sdkArchiveCheck | Externer Archivverbraucher bestanden |
| Dokumentprüfung | `git diff --check`, lokale neue Verweise und Codefences geprüft |

`SemanticRepositoryCommandsTest` prüft für alle vier Befehle transitive lokale
und HTTP-Imports, benannte Roots, explizite Quellen, mehrere Repositories,
Konfigurationspräzedenz und reine Konfiguration, Modell-/Quellenpins,
Mehrdeutigkeit, fehlende Modelle, defekte Indizes/Locks, Cachekorruption,
Offlinewiederholung ohne HTTP-Zugriffe, Abbruch und Lockkollisionen/-schreibfehler.
Neue Analyse-/Diagrammfälle sichern Diagnoseframes in nachgeladenen Quellen,
unterschiedliche Gatepflichten und Warnungspolicy.
`VerboseDiagnosticsContractTest` prüft fünf Befehle, DE/EN, alle Severitywerte,
Farbe, unveränderte Diagnosedaten/JSON/Fachartefakte und ungültige Flags vor IO.
Bestehende reine Quellframeprüfungen sichern Revisionen, UTF-16 und Sanitierung.
Der reale Prozessprüfer ergänzt vier HTTP-Verbraucher sowie Kompakt-/Verbosefälle;
seine bisherigen Farb-/JSON-/Artefaktfälle laufen ausdrücklich mit `--verbose`.

Lokale Rohbelege: `build/reports/cli-repository-verbose/gates.log`, JUnit-XMLs
unter den jeweiligen Modul-`build/test-results/`-Verzeichnissen sowie
`build/reports/p3/compiler-cli/processes.json` und SDK-Berichte unter `build/`.
Frühere Fehlversuche bei der Testanpassung: eine bestehende Office-Textprüfung
benötigte das neue Flag; ein neuer Test nutzte zunächst einen unvollständigen
Diagnostic-Konstruktor; eine beschädigte Closure wurde korrekt bereits vom
Resolver abgewiesen. Der abschliessende frische Lauf verwendet vollständige
Diagnosewerte und eine auflösbare Closure mit einem fachlichen Namenskonflikt.

## Grenzen und Abschluss

Nicht ausgeführt: Linux-/Windows-Prozesse, Portable-/Browsermatrix und voller
releaseCheckP3. Der Auftrag verändert CLI-Hostintegration und Textpräsentation;
Compilersemantik und portable APIs bleiben unverändert. Diese gezielten lokalen
Nachweise sind keine neue plattformübergreifende Releasequalifikation.
Keine ausgelassenen fälligen Prüfungen des beauftragten Plans und keine Blocker.
Kein offener Implementierungsschritt aus diesem Auftrag.

## Unabhängiger Folge-Review

Auf ausdrücklichen Nutzerauftrag prüfte OpenCode2.0.28 mit direktem Provider
`deepseek/deepseek-flash` den eingefrorenen Review-Snapshot
`da3c1239f384a42b1b4f5d48ed11cbd03a7fe98e` unabhängig. Der Snapshot wurde
über einen separaten Gitindex erzeugt; Arbeitsbranch und Nutzerindex blieben
unverändert. Der geprüfte Worktree ist sauber; alle952 gebundenen Quelldateien
stimmen mit den vorhandenen Prozessnachweisen überein (Fingerprint
`42ffa09de752f0e5ff69772a44e127bfd25c0c93997f12dc1799c3e22b2372e2`).

**Urteil PASS für R1–R5/V1–V4/D1**, keine blockierenden Findings oder fehlenden
Pflichtnachweise. Vier gezielte eigene Gegenprobengruppen prüfen lokale und
HTTP-Closures/Offlinewiederholung, Repository-Diagnosetexte/Kompakt/Verbose/JSON,
Lockgrenzen sowie Konfigurationspräzedenz und Optionsfehler. Kein Gradle-Aufruf
und keine vollständige Testwiederholung. Vorhandene178 JUnit-,55 CLI-,6 PTY- und
SDK-Rohbelege wurden unabhängig nachgezählt und53 Evidenzdateien hashgeprüft.

[Unveränderter OpenCode-Bericht](reviews/compiler-cli-repository-verbose-review.md),
SHA256 `9941b17b68afac5c8fb1a72b121a0f6854e211854cf1137b66017ed3e77aef1f`.
Session `ses_e68a5364dffejVNvso5frl6U0M`; regulärer ProzessExit0, äusserer
Guard `passed`/Exit0, Review-HEAD exakt Snapshot, Gitstatus leer. Exportierte
Sitzung bestätigt Provider `deepseek` und Modell `deepseek-flash`.
Originale verdict.json, Session, Probeausgaben und Abschlussaudit unter
`build/review/cli-repository-verbose/` erhalten. Die ersten lokalen Starts
scheiterten vor dem Review an der Agentkonfiguration bzw. verschachtelten
Sandbox; sie sind separat erhalten und werden nicht als Abnahme gezählt.
Der abgeschlossene Lauf verwendete den Standardagenten mit explizitem
Reviewauftrag, schreibgeschützten Kandidatendateien und OS-Sperre für Python
und Gradle. Keine globale Konfiguration wurde verändert.

Einzige nicht blockierende Beobachtung: die bereits vorbestehende generierte
`diagram`-Hilfe listet `--json`, obwohl der Befehl `--format json` verlangt.
Keine Produktkorrektur durch diesen reinen Reviewauftrag. Die oben genannten
Plattform-/Releasegrenzen bleiben bestehen.

## Gezielte Hilfekorrektur nach dem Review

Auf Nutzerauftrag ist die nicht blockierende Hilfe-Beobachtung korrigiert:
Die generierte `diagram`-Zeile lässt das ausschliesslich für den erklärenden
Bedienfehler erkannte `--json` aus. Ein explizites Hilfebeispiel empfiehlt
`interlis-compiler diagram --format json`. Der Fehlaufruf behält Exit2 und
den Hinweis auf `--format json`; die JSON-Option anderer Befehle bleibt sichtbar.
CLI-003/004/006 und LOG-005; CLI_PROFILE und Kapitel18 synchronisiert.

Gezielte Verifikation auf Temurin25.0.3/macOS arm64:

```sh
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem ./gradlew \
  :interlis-compiler-cli:test \
  --tests 'guru.interlis.cli.CompilerCliIdentityContractTest.helpUsesNewIdentity' \
  --tests 'guru.interlis.cli.CompilerCliIdentityContractTest.shortHelpUsesNewIdentity' \
  --tests 'guru.interlis.cli.CompilerCliIdentityContractTest.longHelpUsesNewIdentity' \
  --tests 'guru.interlis.cli.VerboseDiagnosticsContractTest.jsonAndArtifactsAreIdenticalAndInvalidFlagsFailBeforeIo' \
  :interlis-compiler-cli:installDist --console=plain
```

Exit0, BUILD SUCCESSFUL in2s: **4 ausgewählte Tests, 0 Fehler/Skips**.
Zusätzlich5 echte Prozesse aus der aktualisierten Installation: `help`, `-h`,
`--help`, erfolgreicher JSON-Diagrammexport und `diagram --json missing.ili`
mit Exit2 vor Datei-IO. Alle5 bestanden. Rohbelege einschliesslich kopierter
Test-XMLs unter `build/reports/diagram-help/`; `git diff --check` bestanden.
Keine vollständigen Testläufe wiederholt. Keine offenen Punkte dieses Fixes.
Der unveränderte unabhängige PASS-Bericht bezeichnet weiterhin den Stand vor
dieser anschliessenden Hilfekorrektur; er wird nicht nachträglich umgeschrieben.
