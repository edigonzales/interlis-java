# Gezielte Tests und Belege

Belegroot `/Users/stefan/sources/interlis-java-ape-evidence`. Prozessreceipts
enthalten argv/CWD, Beginn/Ende, echten Exit/Signal/Timeout, Hostdaten und getrennte
gehashte stdout/stderr. Kein Credentialdump. Originale werden nicht überschrieben.

| Umfang | Tatsächliche Prüfung | Ergebnis / Beleg |
|---|---|---|
| F0/AP-02–04 | `java build/InterlisJvmBuild.java ape prepare` mit explizitem Temurin25.0.3 | erster Lauf erkennt eigene numerische JSON-Kanonisierungsabweichung; Korrektur, neuer Lauf0; `f0/prepare*.json` |
| F0 | `node tools/ape/f0-gate.cjs <java25> <manifest> <neuer Output>` | gate-01/-02 korrigieren falsche Harness-Feldannahmen anhand echter CLI-Ausgabe; gate-03 PASS mit echter version/check gültig/check ungültig; `f0/gate-03/` |
| F0/U02/U10 | InputChecks, genaue Closure plus Scratchmutationen | fehlende/fremde/umsortierte JARs, falscher Digest/Fingerprint, Extra-JAR, geänderte Kopie und Verification-Leak abgelehnt; normale Parserfehler erhalten |
| F1/AP-05–07 | explizites `ape toolchain --lock <absolut>` | beide Downloads hashgleich; vollständiges Inventar geprüft; `f1/acquire*`, `f1/install*`, externe Archive unter `toolchains/` |
| F1/U01/U05/U07 | `node --test tools/ape/validators.test.cjs` | fünf Tests0, keine Skips: Lock/Hash/Archivpfad/Symlink, Casevollständigkeit, exakter Comparator; `f1/validators.*` |
| F1/AP-07/08 | `ape probe --lock <absolut>` | erster Run beide Architekturen1 wegen privatem noexec-Temp; gezielte C-Kontrollen0 nach passenden HOME/exec-Einstellungen; korrigierter Run beide Nativebuilds0 und JVM-Kontrolle0; `f1/probe-4k5SJU/`, `f1/probe-pbnFgk/`, `f1/c-control/` |
| F1/AP-16/18 | x64-Probe direkt in Laufkopie ohne Vendor-/JDK-Mount |0, Ressource/Datei/Thread/Exception korrekt, Hash unverändert; `f1/x64-runtime/` |
| F1/AP-08/18 | `node tools/ape/finish-probe.cjs <lock> <probe-run>` | fixup-Kopien und apelink0, fusionierte Probe läuft unter emuliertem Linux x64 und macOS arm64-Shell; Mac-Direktstart ENOEXEC separat; `f1/fused-probe-KSbjMS/` |
| F1/Inputschutz | erneute Inventarpininstallation während APE-Probe | bewusst durch bestehenden Lock abgelehnt; nach Buildabschluss0. Gleiche Vendorbytes, kein erneuter Nativebuild; `f1/pinned-inventory-install*.json` |
| F2/JVM-Bindung | erneutes `ape prepare`, aktuelles `f0-gate.cjs` einschliesslich EntryChecks | PASS, gezielte kleine Prozesse, normale Profile/Selektoren erhalten; `f2/jvm-input-gate/` |
| F2/Inputgleichheit | Hash-/Reihenfolgenvergleich beider Compilerclosure-Snapshots | alle sieben JARs gleich, Adapteränderungen separat im Sourcefingerprint; `f2/jar-input-parity.json` |
| F2/Validatoren | fünf kleine Nodefälle nach Inventarpin erneut | PASS, keine Skips; `f2/validators.*` |

## Wiederverwendung und Grenzen

Auftrag A wird nicht wiederholt. Externer A-Review des exakten früheren
4c5e133-SHA und dessen Nachvalidierung sind vorhanden. Gradle-Clean-Guard und
Defaultprofile bleiben unverändert; `build/interlis/` ist bereits geschützt.
Eine neue Nativepflicht wurde ausschliesslich durch den ersten Parameter `ape`
opt-in ergänzt. Aktuelle positive/negative Parsergegenproben ersetzen einen
unbegründeten Gesamtlauf. Keine JVM-Vollsuite, Browser-, Wasm-, LSP-, Extension-,
Vollkorpus-, Benchmark- oder Releaseabnahme für diesen additiven Buildadapter.

Die ergänzte Toolchaininventarprüfung ändert keine Vendorbytes und keine
wirksamen Nativeoptionen. Originale F1-Lock-/Runbindungen bleiben unverändert;
die neue Installation bestätigt deren vollständige Inhaltsgleichheit. Keine
Übertragung von Laufbelegen auf eine veränderte Binary.

F2-Compiler-Native-Build und darauf folgende Produktgates sind noch in Arbeit.
Ein Prozess- oder Methodenkompiliererfolg ist kein CLI-/Netzwerk-/Plattform-PASS.

## Tatsächlich abgeschlossene Ergänzungen F2/F3/F5

| Bereich | Originalbelege unter Belegroot | Beobachtung |
|---|---|---|
| Compiler x64 / ARM64 | `f2/x86_64-drehEN/`, `f2/aarch64-Herc6v/` | Nativebuilds 19m15s / 19m20s, beide Exit 0; identische sieben Jenesis-JARs, `-O0`, 5GiB/2 Threads, unveränderte Vendorbytes |
| Fusionsbindung | `f2/fused-SU7MpB/`, `f2/slice-mutations/checks.json` | fixup nur Kopien, apelink0; Binary-/Plan-/Inputmischung inklusive neu gehashtem Manipulationsversuch abgelehnt |
| Producerseal | `f2/x64-post-build-seal.json` | x64-Seal nach Build aus unveränderten Originalen ergänzt, ARM beim Abschluss; keine rückdatierte Provenienz |
| Kernparität | `f2/core-x64-03/`, `f2/core-fused-mac-01/`, `f2/core-fused-linux-01/` | je 16 feste K01–05/F01–03-Fälle PASS; Linux emuliert, macOS nativ; Originalstreams und präzise Versionszeilennormalisierung |
| Baselineabweichungen | `f2/core-x64-01/`, `f2/core-x64-02/`, `f2/baseline-text-length.json` | tatsächliche JVM-/Nativeversion unterscheidet sich; `TEXT*;` verursacht beiderseits internen Fehler3, bleibt zusätzlicher FAILED-Befund; TRAILING-Syntaxfall aus bestehendem Vertrag ersetzt keine Diagnose im Original |
| Zusätzliche CLI | `f3/cli-host-01/`, `f3/cli-host-02/` | 15 unveränderte PASS-Fälle anhand gehashter Rohreceipts wiederverwendet; F06 gezielt neu, nur exakt bekannter zufälliger IO-Tempdateiname normalisiert |
| Host | `f3/cli-host-02/` | H01–H04 PASS nativ macOS arm64; neue Installation mit Leerzeichen/Unicode, echter Sandbox-Lese-/Execbarriere für Quellen/JDK/SDK und erfolgreicher Denialkontrolle, frisches HOME/Temp, nicht beschreibbares Temp |
| Prozessabbruch | `f3/cli-host-02/H05-native.json` | gezielt eigener Prozess mit SIGKILL nach150ms beendet, `timedOut:true`, Exitnull; Prozesskontrolle, ausdrücklich kein Compilererfolg oder Cancellationvertrag |
| HTTP-Erstversuch | `f3/network-01/` | JVM N01 erfolgreich, Compiler-APE Exit 3 ohne einen Request; Harness FAILED, abhängige N-Fälle nicht gelaufen |
| HTTP-Kontrollen | `f3/mac-no-cache/`, `f3/linux-no-cache/`, `f3/mac-poll-provider/` | finaler unveränderter Kandidat weiterhin3 ohne Request; Cachehypothese und reiner Mac-Hosteffekt widerlegt; Linux emulierte Diagnose |
| Minimalreproducer | `f3/http-probe-build.*`, `f3/http-probe-run/` | nur Standard-Java HttpClient, ARM-Nativebuild0; JVM HTTP200, nativ1 mit `IOException: Invalid argument` aus `EPoll.create` |
| Laufzeitprovider | `f3/http-probe-poll/`, `f3/vendor-provider-classes.*`, `f3/selector-substitution.javap` | Poll-Klasse im Vendor vorhanden; Laufzeiteigenschaft ändert vorinitialisierten Provider nicht, identischer EPoll-Stack; tatsächliche Vendor-Substitution verlangt Initialisierung während Imageerzeugung |
| Liefertransport | `f3/transport/` | echtes tar.gz gepackt/entpackt, gesamte Inventar-/Byte-/Executableprüfung PASS; keine zusätzliche Plattformzusage |
| F5-Validatoren | `f5/validators.*`, `f5/report-mutations/` |10 Nodefälle PASS ohne Skips; zusätzliche5 Reportgegenproben: Hashwechsel, fehlender/zusätzlicher Case, falscher Exit, leere Auswahl abgelehnt |
| Report und Adapter | `f5/report.*`, `f5/prepare.*`, `f5/jar-parity.json`, `f5/entry-input-gate/` | expliziter Report ohne Produkttests; neuer Reportadapter führt zu neuem Quellfingerprint, erneute echte Jenesisclosure enthält bytegleich dieselben sieben JARs; historische Nativeinputs bleiben unverändert |

Die vorstehende Zeile zu F2 in Arbeit beschreibt einen früheren Zwischenstand.
Massgeblich sind diese Ergänzungen und STATUS.md. Breite Tests wurden nicht
wiederholt. Belege vor dem späteren Reportadapter behalten ihre ursprünglichen
Source-/Dirty-/Harnessangaben; `f2/source-binding/` sichert die vollständigen
unveränderten damaligen Buildquellen. Das neue Eingabemanifest ist kein
rückwirkend umgeschriebener Erzeugungsnachweis der vorhandenen Nativebilder.

| Ergänzung | Originalbeleg | Beobachtung |
|---|---|---|
| Finale Startarten | `f3/final-start-modes/` | dieselbe finale .com auf echtem macOS arm64: direkter Kindprozess ENOEXEC; `/bin/sh`0, frisches Temp enthält tatsächlichen Vendor-C-Quelltext und kompilierten Loader; keine F4-Gesamtabnahme |
| F06-Normalisierung enger | `f5/normalization.*`, `f5/f06-exact-normalization.json` | ein gezielter Nodefall PASS,7 andere bewusst ausgefiltert; Original-F06-Streams ohne Prozesswiederholung unter exakt bekannter LF-Endung erneut verglichen |

Ältere Native-Orchestratoren enthalten Sourcehash und vollständige tatsächliche
argv; nicht jede historische Skriptversion wurde zusätzlich als Datei eingefroren.
Ihre wirksamen Nativeargumente sind gegen die aktuelle Rezeptfunktion geprüft.
Spätere Recorder-/CLI-/Coreläufe sichern die tatsächlich geladenen Skriptbytes
zusätzlich. Diese Provenienzgrenze wird nicht durch neue Datierungen verdeckt.

## Endgültige F3-Diagnose und terminaler F5-Weg

| Umfang | Beleg | Tatsächliches Resultat |
|---|---|---|
| Poll-Buildzeitprovider | `f3/http-probe-poll-build-02.*`, `f3/http-probe-gQci26/`, `f3/http-probe-image-poll-run/` | separater Nativebuild14m, Exit0; reines HTTP nativ200, Ursache EPoll konfigurierbar; kein NO_GO allein daraus |
| JVM-Netzwerkbaseline | `f3/network-jvm-baseline/`, `f3/corrupt-cache-jvm/` |17 Varianten plus beschädigter Offlinecache PASS; nur JVM-Referenz, keine Nativeabnahme |
| TLS-Minimalprobe | `f3/http-probe-tls/positive.*`, `f3/http-probe-tls-debug-02/` | positive native Kontrolle FAILED mit CertificateNotYetValidException; Instant/TLS-Logger zeigen richtige UTC-Zeit, SSLSessioncreationTime ist monotone Laufzeit |
| Vendorursache | `f3/cosmo-time-substitution.javap`, `f3/SSLSessionImpl.java`, `f3/PKIX.java`, gepinntes svm.jar | currentTimeMillis ruft unbedingt CLOCK_MONOTONIC, Instant nutzt CLOCK_REALTIME; keine eigene JDK-Substitution eingeführt |
| Direkte Uhrprobe | `f3/clock-probe-run/`, `f3/clock-probe-IhlXx7/`, `f3/clock-probe-adopted-wait.*` | JVM0/Delta1ms; nativ AssertionError1/Delta1793752418783ms. Nativebuild endet tatsächlich0 nach5m45s, eigene Containerübernahme nach äusserem Startertimeout ehrlich erfasst |
| Bewusster Abbruch | `f3/build-poll-x64.*`, `f3/abort-poll-compiler.*`, `f3/aborted-poll-compiler.json` | äusserer Starter irrtümlich nach60s TIMED_OUT/SIGKILL; losgelöster Dockerbuild danach ausdrücklich entfernt, kein fertiggestelltes neues Bild, kein PASS |
| Prozessfix | `f5/recorder-cleanup-unit.*`, `f5/recorder-container-guard/`, `f5/recorder-container-guard.*` | Unitfall0; echte verschachtelte Timeoutprobe entfernt ausschliesslich registrierten eigenen Dockercontainer und bestätigt Abwesenheit; äusserer Timeout bleibt als Timeout erfasst |
| Werkzeugrechte | `f3/tool-mode-guards.*`, `f3/tool-mode-real.*` | ein gezielter Inventarfall inkl. entzogenem Executablebit0, übrige bewusst ausgefiltert; echte unveränderte Vendorinstallation inklusive Werkzeugrechten verifiziert |
| Konfigurationsbindung | `f3/native-config-invalidation.json` | aktive Poll-Konfiguration invalidiert ursprüngliche Nativebuildseals; keine alten Laufbelege auf ungebauten korrigierten Compiler übertragen |
| Enddisposition | `f5/terminal-disposition.json` | F0/F1/F2 Originalkonfiguration bestanden, F3 Vendorblocker, F4 NOT_RUN_BLOCKED, F5 negativer Abschluss; Review noch nicht vorweggenommen |

Bei den zwei äusseren Startertimeouts wurden weder Rawreceipts nachgeschrieben
noch tatsächliche Exits erfunden. Der Clockcontainer wurde durch ein begrenztes
`docker wait` übernommen, dessen echter Status0 und stdout0 den Containerexit
belegen. Vorhandene komplette Buildstdout-/Binär-/Quellbytes sind separat
archiviert. Der Compilerneubau wurde nach Vendorursachenbefund aufgegeben.
Die neuen Timer-/Containerregeln ändern keine historischen erfolgreichen
Produktbeobachtungen; deren Source-/Harnessbindungen bleiben erhalten.

F3/F4 sind keine bestandenen Produktgates. NO_GO beruht auf dem Clock-/TLS-
Vendorfehler unter dem Substitutionsverbot; fehlende native Linux-/Windowsrunner
bleiben ein gesonderter Nachweisvorbehalt. Keine breite JVM-/Korpus-/Benchmark-/
Browser-/Releasekampagne wurde nach dem technischen Blocker begonnen.

Letzte gezielte CLI-Timerprüfung: `f5/explicit-timeout-guard.*` — ein neuer
Test PASS (ein anderer bewusst ausgefiltert), explizites100ms-Limit wird
übernommen, tatsächlicher SIGKILL/Timeout erfasst; ungültiger Wert scheitert
vor Prozessstart. `f5/final-report.*` validiert die selektierten unveränderten
Kern-/CLI-/Diagnose-/Transportbelege erfolgreich ohne Testwiederholung.
