# Testledger Auftrag A

## A0 – ursprünglicher Stand477bc360 (Tree40ed15f9), sauber

Betroffen JA-01–05/12/36, Vorbereitung10/13/15/30/33–35.
Erwartung: Classpath-/White-Box-/BOM-/Filter-/Report-/Cachefähigkeiten ohne
Produktquellenänderung; echte normale Gradlebaseline.
Rohroot `/Users/stefan/sources/interlis-java-jenesis-evidence/a0`.

- `probe-first.log`: Java25.0.3, unveränderte gepinnte Engine, zwei POM-Kinder,
  drei erfolgreiche Fälle. `setup.cjs` enthält das tatsächliche externe Fixture.
- `probes.cjs`: repeat/method Exit0, missing/failure bewusst Exit1,
  ANTLR Exit0. Exakte Argumentlisten/Exits in gleichnamigen JSON-Dateien.
- `cache-probe.cjs`: drei echte Engineaufrufe Exit0; Eventlogs weisen
  executed/skipped/executed des betroffenen Testschritts nach.
- Leere Tagauswahl: Exit0 der Engine, echter leerer Native-Report; kein PASS
  einer erwarteten Testauswahl. Projekt muss diese Lücke explizit prüfen.
- Lesender Gradle-Inventarversuch1 scheitert an Projektlock; Versuch2 an
  versehentlich als Datei gehashten Projekt-Classdirs. Ursachen korrigiert;
  ursprüngliche Fehlerlogs bleiben unverändert extern.
- `gradle-baseline.log`: `JAVA_HOME=…/25.0.3-tem ./gradlew --no-daemon
  --init-script ../a0/gradle-inventory.gradle -Dassignment.output=…/a0/gradle-dependencies
  assignmentAInventory` plus jeweils `:<zehn-module>:test`.
  Exit0, 1297Tests, null Fehler/Skips. Native-XML und Hashreceipt erhalten.
  Kein Rootcheck, Wasm-/Browser-/Korpus-/Benchmark-/Releaseprofil ausgeführt.
- OpenCode technischer PREFLIGHT_OK, keine Tools/Review; finale Anbieter-
  und Modellbindung bleibt R vorbehalten.

Weitere Gates, normale Jenesistests, Contracts, SDK/CLI/LSP und Editor sind
noch nicht ausgeführt. Keine Wiederverwendung historischer PASS-Zahlen.

A0-Nachgate: `final-probe.cjs` Exit0; Gson2.14.0 als Runtime-only,
Projektconsumer und native Parameter-IDs bestätigt. `scope.receipt.json`,
`pure.receipt.json` Exit0; finale Rohreports/Hashes unter `final-capability/`
und `capability-reports.json`. `api.log`: echter öffentlicher
`Project.ofEnvironment(...).build()` mit nichtleerem Ergebnisinventar Exit0.

## A1 – POMs, Engine und Koexistenz

JA-01–07/12/24/25. Zehn deklarative POMs, unveränderte Enginequellen,
strikte Tool-/Dependencypins. Keine Produkt-/Testquellen geändert.

- `a1-pin.log`: kontrollierter einmaliger Engine-`pin`-Lauf, Exit0, Tests
  ausdrücklich übersprungen, kein Test-PASS. Eigener Targetbaum `pinning/`;
  keine Maven-/Gradledelegation und kein Export. JUnit-Tool1.11.4,
  ANTLR-Plugin0.2.0/Tool4.13.2 aus den erprobten Pins.
- Erster Modellaufruf scheiterte an noch fehlendem Target-Elternverzeichnis.
  Adapter legt es ausdrücklich an; Fehlerlog bleibt erhalten.
- `a1-model-final.log`, `a1/model-final/`: strict, Main-/Test-/Sources-JAR,
  24 bestehende Modelltests, keine Fehler/Skips, Exit0. Vollständige tatsächliche
  Discovery der zehn JVM-Projekte wird im Adapter geprüft.
- `a1/adapter-tests.log`: acht Kontrollen/Gegenproben, Exit0: gültiger
  Projektstand und Artefakt, unbekanntes Profil/Modul, Filter ohne Modul,
  Versionsdrift, fehlendes Artefakt und fremdes Artefakt. Keine leere Freigabe.
- `a1/clean.log` und `clean-receipt.json`: wegwerfbare Kopie, Gradleclean und
  normaler Modelltest Exit0; alle neuen Buildquellhashes gleich, generierte
  Sentinels entfernt, externer Symlinkinhalt erhalten. Originalworktree nie
  gemeinsam bereinigt.
- `git check-ignore`: Engine/Adapter/Einstieg trackbar; target/.jenesis/
  Modulbuild ignoriert. `git diff --check` erfolgreich.
- `a1-reference-graph.log` / `a1-candidate-graph.log`: tatsächlicher Gradle-
  Dry-run für check/compilerContractCheck/sdkPackage/editorBundle, jeweils
  Exit0. 136 Tasks in exakt gleicher Reihenfolge (`a1/graph-comparison.json`).
  Graphbeleg ist kein Laufnachweis dieser Profile. Reale Editorprobe bleibt A4.

A1-Gate erfolgreich. Breitere Produkttests folgen sukzessive A2; Verification
und Contracts A3; Distributionen/Editor A4; Reproduzierbarkeit/Commit A5.

## A2 – neun Produktmodule und Generator

JA-03–05/08–13/19/20/25. Produktions-/Testpackages und Produktsources unverändert.
1218 normale Produktfälle erfolgreich (24Modell aus A1, 788Compiler,
23Workspace,22Projection,112Repository,8Documentation,24Bridge,141CLI,76LSP).
Rohlogs/-reports in externem `a2/`, Compiler zusätzlich `a2-compiler*.log`.
`a2-products.cjs` hält jeden tatsächlichen Prozess samt Zeiten/Exit fest und
sichert unveränderte Native-Reports vor dem nächsten Engineaufruf. Exit0 aller
sieben nachfolgenden Modulprozesse. Kein Verification- oder Contract-PASS.

- Modulbezogene externe Inputlisten und Tests-JVM-Arbeitsverzeichnisse erhalten
  die existierenden Dateizugriffe. Compiler-Unicodelizenz als Engine-Ressource
  gebunden. JAR/Sources-JARs tatsächlich erzeugt.
- ANTLR-Scratch: gültige Kontrolle, Lexeränderung und frischer Vergleich Exit0;
  sieben Mainklassen bytegleich. Nach Parsergrammatiklöschung Compiler-Exit1
  wegen fehlender WordsParser-Klasse, kein nutzbarer Altrest. Originalgrammatiken
  unangetastet. `a2/antlr/*` einschliesslich ursprünglichem Fehlerlog.
- Befund aus Generatorprüfung: geerbte Konfiguration lief zuvor auch im
  Testvariant. Dort nun deaktiviert (keine Testgrammatiken). Betroffener
  Compilerclasspath erneut vollständig788Fälle geprüft, Exit0; Test-JAR enthält
  keine generierten Lexer/Parser, behält den echten VersionScannerTest.
  Folgebeleg `a2/interlis-compiler-generator-fixed/`; Originalbeleg erhalten.
  Übrige Produkte/Tests nutzen unveränderte Main-JARs; deren Läufe weiter gültig.
- Reale Compiler-Cachegegenprobe im getrennten Checkout: erste Auswahl2Tests
  ausgeführt; identisch skipped; nur Modellfixturekommentar geändert executed;
  nur Hookimplementierung bei gleichem Verhalten geändert executed. Alle Exit0,
  `a2/cache-{first,unchanged,fixture,hook}.*`. Cachefingerprintlogik unverändert
  bei anschliessender enger Testgeneratorabschaltung; keine globale Run-ID.
- Vier effektive Scopes aller neun Module verglichen, keine verlorenen oder
  byteveränderten Dependencies und exakt gleiche Produktruntimes.
  `a2/scope-gate.json`:68 einzeln erklärte Zusatzsichtbarkeiten: bestehende
  ANTLR-Runtime über POM-Compileweitergabe; unveränderte JUnit-Runtime auch im
  Testcompile; gepinntes Console-/Reporting-/ApiGuardian-Testtooling.
  Keine pauschale Runtimefreistellung. Vorheriger Dumpfehler (Testinventar nutzt
  denselben module-Präfix) war Prüfhelferfehler, keine Produktabweichung.
- SDK-Example extern javac/java mit exakt drei Jenesis-Runtime-JARs Exit0;
  `a2/sdk/` enthält Argumentlisten, Classpath, stdout/stderr und Exits.
  Erstes Scratchprogramm hatte einen Java-Namenskonflikt; korrigiert,
  ursprüngliches Kompilierungsfehlerlog erhalten. Noch kein SDK-Archivtest.

A2-Gate erfolgreich. Vollständiger Identitäts-/Artefaktvergleich und
Verbraucherdistributionen A4; Verification/Contracts A3 folgen.

## A3 – Verification, Reports und frische Contracts

JA-10–17/19/22/24/25. Verification hat86 normale erfolgreiche Fälle:
79 bestehende plus6 Reportadapterfälle und1 neuer Layoutfall. Getaggte
Consumer laufen getrennt. Aktuelle Auswahl wird zwischen CompilerCoverage
und Buildkoordination über CurrentContractSelection geteilt; keine zweite
fachliche Auswahl. Historische Originale unverändert, bestehendes Overlay
nur für Root-Clean-/SDK-Layoutquellhashes nachvollziehbar aktualisiert.

- Gradle-Defaults: `a3/gradle-adapters.log` (10 Fälle) und
  `gradle-contract-defaults.log` (ausgewählte ursprüngliche Produzenten,
  vier Consumers und10 Adapter/Layout/Fingerprintfälle), jeweils Exit0.
  `gradle-fingerprint-markers.log`: schmale erneute Java/Node-Paritätsprobe
  nach Ergänzung der Discoverymarker, Exit0.
- Baseline hat54 Fälle in vier parametrisierten Verificationklassen mit
  teilweise methodenlosen Anzeigenamen. Ergänzender echter Gradlelauf nur
  dieser Klassen: Exit0,21Tasks davon20 up-to-date, originale Deskriptoren
  unter `a3/gradle-identities.jsonl`, keine mehrdeutigen Displaykeys. Der
  ursprüngliche Vollbaselinebericht wird weder überschrieben noch umetikettiert.
- Erster Verification-Zwischenlauf wegen anschliessender Adapteränderung
  korrekt durch Inputs-changed zurückgewiesen; kein PASS dieses Laufs.
- Erster Contractversuch scheiterte an fehlenden Parametertypen im JUnit-
  Methodenselektor. `parameter-method-control.log`: echte zwei Stringinvocations
  mit voll qualifizierter Signatur erfolgreich. Plan verwendet nun explizite
  Signaturen; normales Klassen-/Tagfiltern bleibt erhalten.
- Zwischenläufe mit bereits erfolgreichen Producer-/Consumerfällen wurden
  bei Ergänzung der Artefakt-/Inputarchivierung nicht als endgültiges Gate
  ausgegeben. Alle ursprünglichen Logs/Fehler bleiben extern erhalten.
- Abschliessend `a3/jenesis-contracts-source-bound.log`, Exit0:
  Run `contract-55568bad-043d-4db5-adc0-19632fa07a2c`,272 reale Invocations
  (Compiler240,CLI4,LSP4,Model8,Repository2,Bridge2,Workspace12), danach4
  echte Consumer. Keine Addition zu einzigartigen Normaltestzahlen.
  Unveränderte Native-Reports, Argumentdateien, Engineevents, Source-/Fixture-
  Einzelhashes, Cachepolicy, vollständige Main-/Testclosures und tatsächlich
  verwendete Testklassendateien gebunden. Projektionen behalten Identität,
  Fehlerdetails, Parameter und Herkunft. Normalreports werden vor Überschreiben
  archiviert; EXECUTED/REUSED und ursprüngliche Engine-Run-IDs bleiben unterscheidbar.
  Externe unveränderliche Kopie `a3/final-source-bound/{run,normal}`.
- `a3/report-counterprobes-source-bound.log`: zwei gültige echte Kontrollen
  Exit0, sechs gezielte Ablehnungen Exit1 mit spezifischer Ursache: fremder
  Run/Input, fehlender Sourceinput, veränderte XMLs, falscher Artefakthash,
  fehlende Parameterinvocation trotz korrigierter Summen/Hashbindung. Nur
  Scratchkopien verändert. Frühere erfolgreiche Negativserie bleibt mit
  ursprünglicher Herkunft unter `a3/report-counterprobes/` erhalten.
- Bestehende und neue Unitfälle prüfen DTD/Entities, leere/duplizierte/
  widersprüchliche Reports, Fehler/Skips, falsche Run-/Treezuordnung,
  Projection-/Rawhash-Manipulation und Layout-/Symlinkausbrüche.
- Echte schmale Verification-Cachefolge im separaten Worktree:
  `a3/cache-{first,unchanged,seed-fixture}.*`, jeweils Exit0;
  zwei reale CorpusOuterName-Fälle executed/skipped/executed. Ausschliesslich
  Seed-ZIP-Bytes in Scratch geändert, Entrybytes/Erwartungen unverändert.
  Original-Seed und historische Belege unangetastet.

A3-Gate erfolgreich. Noch offen A4: logischer vollständiger Identitätsvergleich,
Artefaktparität/Distributionen und echte Editorverbraucher. A5/R nicht gestartet.

## A4 — logische Parität und echte Verbraucher

JA-09–20/22/23/27/33–36. Nur Packaging-/Source-/internes Prüfdelta; ursprüngliche
A1–A3-Normalfälle bleiben mit ihrer Herkunft gültig. Keine neue Produktsyntax.
Rohwurzel unverändert, alle folgenden Logs dort; exits tatsächlich eingesammelt.

- `a4-gradle-artifacts.log`: Fehler duplicate InterlisLexer.interp. Referenz mit
  explizitem source-binding Init-Workaround, Exit0:
  `a4-gradle-artifacts-source-binding.log`; Quelle bleibt sauber BASE.
- Entfernte redundante Generatorbinding im Kandidaten:
  `JAVA_HOME=<JDK25> ./gradlew :interlis-compiler:sourcesJar :interlis-compiler:sdkExampleTest --no-daemon`,
  Exit0, `a4-gradle-source-fix-correct-task.log`. Der erste falsche Roottaskaufruf
  bleibt als Fehllog erhalten.
- `JAVA_HOME=<JDK25> java build/InterlisJvmBuild.java sdk-check`, Exit0,
  `a4-sdk-build-fixed.log`:9 SDK-/55 CLI-Hostprozesse, ZIP mit Commons-Compress-
  Unixrechten, separate entpackte javac/java-/Launcher-Nutzung. Artifactgeneration
  explizit tests NOT_RUN. Erster Shell-Java21-Fehler erhalten; Hostprozesse nutzen
  danach explizit die Build-Java-Runtime. Apache-Toolpin kontrolliert erzeugt,
  anschliessend strict; Cache liegt ausserhalb versionierter Buildquellen.
- `node tools/verification/lsp-distribution-smoke.cjs <java25> <Jenesis-lsp> <receipt>`,
  Exit0, `a4-lsp-process.log`/`a4/lsp-process.json`.
- Quellenparität erkannte zusätzliche Finderdatei im LSP-Sources-JAR. Native
  Bind.extensions(.java) plus bestehende Ressourcen/Generatorquellen behebt dies;
  frische Sourceerzeugung ohne Tests Exit0, `a4-source-java-only.log`.
- JvmBuildParityTest2 Fälle Exit0; nach enger Compile-/Manifest-/Typregelkorrektur
  jeweils nur diese Fälle geprüft. `a4-parity-unit-*.log` dokumentieren Auswahl.
  Zuletzt zusammen mit PythonFreeCheckTest2:4 Fälle, Exit0,
  `a4-python-free-adapter-tests.log`. Kein Wiederholen der1297 Normalfälle.
- `a4-parity-final-adapters.log`, Exit0:1297 ursprüngliche Identitäten,7 separate
  Adapterfälle. `a4/parity-counterprobes.json`: Kontrolle0,3 echte Negative1.
  Parityprofilintegration Exit0 `a4-parity-profile.log`.
- Tatsächliche vier Scopeinventare aller10 Module `a4/all-scopes.tsv`;
  `a4/staged-pom-audit.json`:9 öffentliche Produkt-POMs,36 Stagefiles, direkte
  deklarierte Koordinaten/Versionen/Scopes gleich; kein Verification/Testtool.
- `a4-python-free.log` weist den ersten Scannerbefund in unverändertem Upstream
  aus. `a4-python-free-pinned.log`, Exit0:2352 eigene Inputs geprüft; nur die
  vollständige byteverifizierte unveränderte Engine separat inventarisiert.
- `a4-current-bindings-regression.log`, Exit0:6 echte Parameterfälle nach dem
  dokumentierten Compiler-Buildhashupdate; keine fachliche Bindingänderung.
- Elf Host-/CLI-Konformitätseinstiege aus tatsächlichen Main-/Test-JARs auflösbar,
  `a4/consumer-main-resolution.log`, `a4/other-consumer-mains.log`; keine breiten
  Korpus-/Benchmark-/Fremdcompilerprozesse gestartet.
- Beide Richtungen des Editorvertrags, E-01–07 und fertiger editor-check, Exit0;
  vollständige Kommandos/Herkunft siehe EDITOR_COMPATIBILITY_REPORT.md. Host
  VSCode1.140.0/macOS arm64, Extension a5b774a, Java25 Developmentoverride.

Summenkorrektur: die bisherige1397/1318-Angabe war arithmetisch falsch; originale
Rohberichte ergeben1297 normale Gesamtfälle bzw.1218 Produktfälle. Keine XMLs,
Goldens, Skips, Eingaben oder Erwartungen hierfür verändert. A4 geschlossen.
A5 und unabhängige Gesamtabnahme bleiben bis zu ihren tatsächlichen Gates offen.

## A5 — unabhängige Erzeugungen und Kandidatenvorbereitung

JA-07/19–29/33–36; keine neuen Produktverträge. Rohbelege `a5/`.

- Erste frische Worktrees generation-1/-2 scheitern am fehlenden ArchiveWriter:
  die bisherige generische **/build/-Ignoreregel verschluckte dessen Java-
  Packageverzeichnis. Keine erzeugten Quellen als getrackte behauptet. Enge
  Ausnahme ergänzt; `git ls-files --others --exclude-standard` und anschliessend
  Stageinventar erfassen nun auch diese echte Quelle. Fehllogs bleiben erhalten.
- Neue getrennte BASE-Worktrees generation-3/-4 mit identischem vollständigem
  Quelloverlay und frisch leeren Target-/Gradleoutputs; nur offizielle Dependency-
  Cachedateien kopiert. `generation-{3,4}-setup.json`, `source-inputs.json`.
  Beide `java build/GenerateArtifacts.java`: Exit0, `generation-{3,4}.log`.
  Das externe kleine Harness ruft nur ExistingJvmDistribution.generate auf:
  echte Module/Source-JARs/CLI/LSP/SDK plus ein realer version--json-Prozess;
  normale Tests und die bereits erfolgreichen55 CLI-/9 SDK-Fälle NOT_RUN.
- Eigener Abschlussaudit verschärft GAV-/Bytebindung: jede eigene aufgelöste
  Dependency muss zum tatsächlichen Produzenten-JAR beziehungsweise dessen
  Classdir passen. Nur JvmBuildParityTest2 neu ausgeführt, Exit0,
  `own-artifact-provenance-tests.log`; vollständiger logischer Vergleich
  `own-artifact-parity.log`, Exit0. Manipuliertes eigenes JAR mit richtig
  aktualisiertem Dependencyhash scheitert dennoch am Produzentenvergleich,
  `foreign-own-result.json`, Exit1. Die beiden unabhängigen Outputs erhielten
  nur die betroffene frische Verificationkompilierung und Runtimeaudits,
  `generation-{3,4}-verification-refresh.{json,log}`, jeweils Exit0. Die neun
  unveränderten Produkte und deren bestandene Normalfälle wurden nicht neu getestet.
- `java build/interlis/JvmArtifactReproducibility.java <generation-3> <generation-4> <receipt>`:
  Exit0, `reproducibility-final.log/json`: **81 bytegleiche Artefakte**, inklusive
  aller20 Main-/Source-JARs,36 öffentlicher Maven-Dateien, CLI/LSP-Lieferdateien,
  vollständigem Runtimeinventar und SDK-ZIP. `current-artifact-binding.json`
  bindet sämtliche20 aktuellen JARs an genau diese unabhängigen Bytes.
- Mutationsprobe fügt nur dem zweiten Scratch-SDK Bytes hinzu: echter
  Reproprüfer Exit1, exakt sdk.zip als Differenz, `repro-negative.json/log`.
  Originalbytes anschliessend hashgleich wiederhergestellt (`repro-restoration.json`).
- A5s Erzeugungsprofil übernimmt direkt die kompakte tatsächliche CLI-version-
  JSON-Ausgabe; A4s bestehender SDK-Prüfer schreibt dieselben Felder eingerückt.
  Dies ist die einzige entpackte A4/A5-SDK-Differenz. Der A5-Consumercheck gegen
  die reale neue ZIP ist zusätzlich erfolgreich: `generated-sdk-consumer.log`
  und `.exit`0; separate javac/java-/Launcherprozesse, echte Unixrechte,
  semantisch gleiche Version. Keine Reparatur von Rechten nach dem Entpacken.
- 282 normale Produkt-Testklassen sind zusätzlich bytegleich zu Gradles
  tatsächlichen Testklassen: `a4/product-test-class-parity.json`.
- Finaler Gradle-Dry-run check/compilerContractCheck/sdkPackage/editorBundle:
  Exit0, `gradle-final-graphs.log`; identische136 Tasks in identischer Reihenfolge
  (`gradle-graph-comparison.json`). Nur Graphnachweis; kein behaupteter Testlauf.
- A1-Clean-Gegenprobe bleibt gültig: unveränderte vollständige Root-Löschmenge
  schützt interlis/ rekursiv, einschliesslich des neuen ArchiveWriterpackages.
  Die A5-Neuerzeugungen beweisen zusätzlich tatsächlich trackbare Sourceinputs
  und ausgegrenzte Outputs ohne vorhandene Gradleprodukte. Kein Root-build-Löschen.
- Vollständige immutable Artefaktkopien und ursprüngliche/abgeleitete Paritätspläne
  unter `a4/final/`; `artifact-origins.json` erhält Originalpfade und echte Hashes.
  Der archivierte Plan wurde erneut erfolgreich gelesen (`archived-parity.log`).

Keine Korpus-/Benchmark-/Browser-/Wasm-/P3-/Multi-OS-/APE-/JPMS-Gesamtläufe fällig.
Ein macOS-Development-Extension-Host ersetzt keine VSIX-/Runtimeveröffentlichung.
Nach Abschluss von Diff-/Stageprüfung wird der Kandidat lokal committet; erst
anschliessend startet der autonome unabhängige Gesamt-Review. Reviewbelege bleiben
extern und ändern den Kandidaten nach einem PASS nicht.

Abschlussprüfung vor Kandidatencommit: `git diff --cached --check` grün;
2356 eigene Inputs im abschliessenden Quellencheck (`a5/final-source-scan.log`),
byteverifizierte Engine separat. CRLF der unveränderten Windows-Launchertemplates
ist gezielt mit -text/cr-at-eol in .gitattributes erhalten. Keine Rohlog-/Cache-/
Tempquellen im Stage; ausschliesslich Auftrag-A-Änderungen. Der nachfolgende
lokale Kandidatencommit schliesst A5 und aktiviert erst dann R. Kein Push.
