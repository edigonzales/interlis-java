# Compiler-APE — Untersuchung vom 05.11.2026

**Machbarkeitsentscheidung: NO_GO unter den freigegebenen Grenzen.**
Die unveränderte gepinnte CosmoGVM-Implementierung von
`System.currentTimeMillis()` liest `CLOCK_MONOTONIC` statt Unix-Wallzeit.
Der reine Java-HTTPS-Reproducer lehnt deshalb ein tatsächlich gültiges Zertifikat
als noch nicht gültig ab. TLS-Logger/Instant zeigen gleichzeitig die richtige
UTC-Zeit; SSLSession speichert die monotone Zahl als Erzeugungszeit. Das ist kein
Compiler-, Test-CA- oder bloss fehlender Runnerfehler. Eigene JDK-/GraalVM-
Substitutionen sind nach SPECIFICATION.md Abschnitt2 ausdrücklich ein
Scope-/Machbarkeitsblocker. Es wurden keine solchen Patches vorgenommen.

**Reviewurteil: vor dem Kandidatencommit noch offen.** Ein späteres Review-PASS
bestätigt gegebenenfalls die Untersuchung, keine erfolgreiche APE-Auslieferung.

F0/F1/F2 bestanden; F3 nicht bestanden. Nach minimiertem Vendorblocker gilt der
terminale F5-Zweig aus EXECUTION_PLAN.md Abschnitt7. Die abhängigen F3-/F4-
Produktgates bleiben `NOT_RUN_BLOCKED`, niemals PASS. Fehlende native Linux-/
Windowsrunner sind ein zusätzlicher Infrastrukturvorbehalt; sie begründen für
sich allein nur INCONCLUSIVE und sind nicht die Begründung dieses NO_GO.

## Erzeugter und tatsächlich getesteter Umfang

F0/F1/F2 wurden mit gezielten Gates abgeschlossen. Der vorhandene Jenesis-
JVM-Build erzeugte die echte CLI-Closure: CLI, Compiler, Workspace, Repository,
Projection, Model und ANTLR4.13.2. Einstieg `guru.interlis.cli.Main`, Java 25 ohne
Preview. Kein Gradleprodukt, Fat-JAR, Madura-Anwendung oder JVM-Fallback.
Zwei native Compilerbilder wurden aus exakt denselben sieben JARs erzeugt und
auf Kopien mittels Vendor-fixupobj/apelink fusioniert. Keine Produktklasse,
Semantik, Formatierungsregel oder normale Buildpflicht wurde geändert.

K01–K09 und F01–F06 bestehen auf echtem macOS arm64 gegen die JVM derselben
JARs. Kernfälle bestehen zusätzlich auf dem x64-Slice und der fusionierten Datei
unter emuliertem Linux x64. H01–H04 bestehen mit deklarierter Loaderlieferung,
relokierter Installation, Unicode-/Leerzeichenpfaden, anderem CWD, frischem
HOME/Temp und tatsächlicher Sandboxsperre für JDK/SDK/Quellzugriff sowie JVM-/
Compilerstart. Die Denialkontrolle liest eine echte JAR und scheitert erwartbar.
H05 belegt nur die gezielte Beendigung des eigenen Prozesses mit SIGKILL, keinen
fachlichen Cancellationvertrag. Originalquellen und Originaldateien bleiben
bei geprüften Formatfehlern erhalten.

Die erlaubten Vergleichsunterschiede sind eng: die eine tatsächliche Java-
Laufzeitversionszeile und bei F06 ausschliesslich die numerische Zufallskomponente
des bekannten temporären IO-Dateinamens. Rohstreams bleiben unverändert;
Diagnosen, Arrayreihenfolge, Unicode und die Dezimalzahl
`9007199254740993.99` werden nicht gerundet oder entfernt. Ein zusätzlicher
JVM-Baselinefehler `TEXT*;` liefert beiderseits Exit 3. Er bleibt als FAILED
Originalfall erhalten; der Syntaxgatefall verwendet den bestehenden TRAILING-
Vertrag. Das ist kein Produktfix und keine Freigabe dieses Fehlers.

## Netzwerkbefund und begrenzte Ursachenanalyse

1. Finaler Compilerkandidat: JVM-N01 kompiliert über kontrolliertes HTTP;
   APE liefert Exit3 ohne Request. `--no-cache` und emuliertes Linux widerlegen
   Cachezustand und einen ausschliesslichen Mac-Hosteffekt.
2. Reines Java-`HttpProbe` mit identischem Vendor-JDK: JVM HTTP200, natives ARM-
   Bild wirft `IOException: Invalid argument` in `EPoll.create`.
3. Laufzeitwahl des Poll-Providers ändert den vorinitialisierten Provider nicht.
   Vendor-Javap belegt Imagezeitinitialisierung. Ein separater, tatsächlich
   erfolgreicher Nativebuild mit
   `-Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.PollSelectorProvider`
   liefert HTTP200. Dieser erste Fehler ist damit konfigurierbar und **nicht**
   die NO_GO-Begründung.
4. Derselbe Poll-Reproducer mit isoliertem Testtruststore und kontrolliertem
   HTTPS scheitert bei der positiven Kontrolle: `CertificateNotYetValidException`.
   JVM, Host und Dockeruhr bestätigen Gültigkeit. Native TLS-Debugausgabe zeigt
   korrekte UTC-Zeit über Instant und zugleich monotone SSLSessioncreationTime.
   Das bytegenau gepinnte svm.jar enthält in
   `Target_java_lang_System_Cosmo.currentTimeMillis()` direkt
   `Time.CLOCK_MONOTONIC()`; `CosmoPlatformTimeUtils.javaTimeSystemUTC0()` nutzt
   dagegen `CLOCK_REALTIME()`. Keine Metadaten-/Provideroption ersetzt diesen
   fest implementierten Clock-Aufruf. Debugdaten betreffen ausschliesslich
   öffentliche lokale Testzertifikate, keine privaten Konten.
5. Die zusätzliche direkte ClockProbe trennt System.currentTimeMillis von
   Instant ohne HTTP/TLS/INTERLIS. Ihre Originalbelege stehen unter `f3/clock-probe-run/`:
   JVM `SystemMillis=1793863435263`, `InstantMillis=1793863435264` (Delta1ms),
   nativ `SystemMillis=111016494`, `InstantMillis=1793863435277`
   (Delta1793752418783ms), JVM0/native AssertionError1. Der übernommene
   Clock-Nativecontainer beendet den eigentlichen Build tatsächlich mit0;
   der ursprüngliche äussere Timeout bleibt unverändert ein Fehlerbeleg.

Die JVM-Referenz besteht alle17 Netzwerkvarianten sowie die zusätzliche
beschädigte Offlinecachekontrolle:18 ausgewählte Fragen sind fachlich geprüft.
Das verleiht dem nativen Compiler keinen Netzwerk-PASS. Beim nativen ersten
Kandidaten ist N01 FAILED; N02–N09 bleiben NOT_RUN_BLOCKED. Die native positive
TLS-Minimalprobe ist FAILED; eine generelle Verbindungsablehnung wird niemals
als bestandene negative CA-/Hostnameprüfung ausgegeben. Der Standardkandidat
enthält keine Test-CA und es gibt weder Trust-all noch deaktivierte
Zertifikats-/Hostnameprüfung.

Der nach Poll-Nachweis gestartete neue x64-Compilerbuild wurde nicht als gültiges
Bild fertiggestellt. Der äussere Recorder hatte irrtümlich sein Standardlimit
60s und beendete den Starter, während dessen Dockercontainer weiterarbeitete.
Der Originalrun bleibt TIMED_OUT/SIGKILL ohne PASS. Nach der unabhängig erkannten
Clockursache wurde genau dieser eigene Container ausdrücklich entfernt;
`f3/aborted-poll-compiler.json` und Cleanupreceipt erklären die Aufgabe.
Die separate Clockprobe hatte denselben äusseren Starterfehler; der bereits
laufende eigene Container wird über einen neu begrenzten `docker wait` mit
tatsächlichem Exit übernommen, ohne den alten Timeout umzuschreiben.

Der Recorder unterstützt nun explizite längere CLI-Limits und registriert eigene
benannte Dockercontainer auch über verschachtelte Recorder. Eine reale kleine
Gegenprobe erzwingt einen äusseren Timeout und bestätigt Containerentfernung.
Der Timeout selbst bleibt ein Fehlerbeleg. Historische erfolgreiche Produktläufe
brauchen deshalb keinen neuen Compilerlauf: ihre Rawstreams, argv und Exits sind
unverändert und vollständig, die Korrektur betrifft die Timeoutbereinigung.

## Tatsächliche Plattform- und Startabdeckung

| Umgebung | Beobachtung | Grenze |
|---|---|---|
| macOS/Darwin24.6.0 arm64 | finale kombinierte Datei, deklarierter Loader,16 Kernfälle plus21 CLI-/Hostfragen; direkte und Shellstartbeobachtung | primärer echter Host, F3-Netzwerkblocker; keine vollständige F4-Abnahme |
| Docker Linuxx64 auf Macarm64 | gleicher Universalhash,16 Kernfälle mit geliefertem Loader; separate x64-Sliceprobe | x64-Emulation als Build-/Diagnosehilfe, kein nativer Linuxnachweis |
| natives Linuxx64 | kein verfügbarer Runner festgestellt | P01–P09 NOT_RUN_BLOCKED |
| natives Windowsx64 | kein verfügbarer Runner festgestellt | P01–P09 NOT_RUN_BLOCKED |

Abhängige Plattform-Netzwerkfälle wurden nach dem technischen Blocker nicht
vorgetäuscht. Keine kostenpflichtige Infrastruktur, globaler Loader, binfmt-
Änderung oder fremdes Cloudkonto beschafft. macOS-Version wird durch die
originale Darwin-/Hostinventarisierung bestimmt; keine zusätzliche Plattform
allein aus dem Dateisuffix oder dem Buildernamen abgeleitet.

## Identität des letzten vollständig gebauten Untersuchungskandidaten

Die folgende Datei gehört zur ursprünglichen F2-Konfiguration. Sie wurde nach
der Diagnose nicht still mit Poll-Code überschrieben. Aktive Buildkonfiguration
und separater Poll-Reproducer sind ausdrücklich von diesem historischen
Compilerartefakt getrennt; kein vollständig gebauter korrigierter Compiler
oder positiver TLS-Paketweg wird behauptet.

| Datei | SHA-256 |
|---|---|
| kombinierter Compiler, 91 556 403 Bytes | `db9775eb7cf3270c7c55c48c41441a159bddaabd1c2a854356df99423fb83408` |
| x64-Originalslice | `07a50373a66dbf7a96fc8432267dd2b17b5481381d2ee771da8512fc2f787e78` |
| ARM64-Originalslice | `458700195aeed070616d60a97f3ac69fd97627657c0fadab190008ec98ef7d73` |
| Bundlemanifest | `29c0ac020cfabef50e0a540a3fd32393765a03268f85e85a2201193bd448ed8f` |
| mitgelieferter Linux x64-Loader | `bf65f331e87544761b8ed3a2c03a61c1b61072c8776b689a67b06f50eefd546f` |
| mitgelieferter Mac arm64-Loader | `ec8a16b0faca4a0c230a007118155f80c211a46c890d800a9505ea219f1b8609` |
| reales Transportarchiv, 36 807 481 Bytes | `a653d801936ef437f0a3357b71372460af8de3d71a1998e3311079ccd2991a26` |

Die Loader sind notwendige deklarierte Paketbestandteile. Linux verwendet die
unveränderte gepinnte Vendor-ELF. Der Macloader wurde beim Paketbau aus exakt
unverändertem Vendor-`ape-m1.c` mit vorhandenem Apple Clang und SDK15.5 erzeugt;
Quell-/Compiler-/SDK-Hashes, Versionsausgabe und argv stehen im Manifest und
Fusionsrun. Das ist kein selbst gepatchter Graal-/Cosmopolitan-Fork. Der Lauf
braucht dank ausgeliefertem Loader keinen C-Compiler. Der getrennt tatsächlich geprüfte Shellbootstrap der finalen Datei benötigt
beim frischen ersten Start `cc`; das wird nicht
als hermetischer Paketweg ausgegeben. 264 Notice-Dateien sind inventarisiert.
Der direkte Kindprozessstart der finalen Datei endet auf macOS mit ENOEXEC
(`spawn Unknown system error -8`); `/bin/sh` startet dieselben Bytes erfolgreich
und erzeugt im frischen Temp `.ape-1.10.c` sowie den dort kompilierten Loader
(`f3/final-start-modes/`).
Keine pauschale Lizenz-, Signatur-, Sicherheits- oder Produktionsfreigabe.

Realer Transport packt und extrahiert das ganze Bundle und verifiziert exakte
Dateimenge, Bytes und Executablebits. Zusätzliche Laufzeitdatei, fehlende Datei,
verlorenes Executablebit und manipulierte/neu gehashte Kandidaten scheitern in
Scratchgegenproben. Das Manifest bleibt als ursprünglicher Erzeugungsstand
`UNTESTED`; spätere echte Laufbelege stehen daneben und schreiben keine
historische Provenienz um.

## Toolchain und Reproduktionsgrenzen

Aktueller Lock-SHA
`c6f7cc137aa25b1e43591152408e10579d179ad2dc6bedb57d477fdb57e34df6`.
CosmoGVM25.2.4-dev/25.2i-r2-Archiv:
`c420fdfe510baa353290267e53821edf23f6e47127587b7ff0ee6ff7c288f704`.
cosmocc25.2i-r2-Archiv:
`7b3c11802791037aa5d7d1c02c9f54b21b25ba6369e1f1cc222aca9698641a97`.
Vollständiger extrahierter Inventarpin:
`cc819e6dbbac1c4b4dd579d448c2cdd2d3a19c784a96e2006b13039be14a125f`.
Referenz Madura `88cd910650f2e0db4968c0ad6c38089a8ef6b78d`.
Unverändertes Dockerimage
`sha256:063b17654e59a88128e193fcc8cc4a094d37921b20bbc07eed35aa0e01004aff`.

Graal-JDK25.0.4+7-jvmci-25.2-b20, JVM-Referenz Temurin25.0.3+9-LTS,
Node 22.13.0. Builder: Docker Desktop auf Mac arm64, vier CPUs, x64-Emulation,
Container7GiB, Nativeheap5GiB/2 Threads. Compiler-Nativebuilds19m15s/19m20s,
PeakRSS3.52/3.64GiB. O0/compatibility ohne Optimierungsiteration.
Das sind lokale Builderbeobachtungen, keine Startzeit-/Beschleunigungs- oder
Benchmarkaussagen. Kein Performancegate oder breiter Benchmark ausgeführt.

Ursprüngliches Compilerinputmanifest:
`inputs/bfd34cd90646e5336f4f355ff0a6e58c5d711d9a322080744c848dd95c589557/`,
Manifest-SHA `c38fabb4773a4bb60f09c68efd3bc4e423bfc772d16de2fd3d02265df958543d`.
Nach reinem Reportadapterzusatz entsteht ein neuer Quellfingerprint; echte neue
Jenesis-Erzeugung bestätigt bytegleich dieselben sieben JARs (`f5/jar-parity.json`).
Damals vollständige Buildquellen sind in `f2/source-binding/` gesichert und
bleiben wie alle Nativepläne/Seals/Receipts unverändert. Ein aktueller Neubau
muss neue Inputs vorbereiten; der Validator akzeptiert keine stale Quellliste.

## Nachweise, Bedienung und unabhängiger Review

Rohbelegroot `/Users/stefan/sources/interlis-java-ape-evidence`; Kandidatenkopie
unter `candidates/<Compiler-SHA>/`, native Runs unter `f2/`, originale JARs unter
`inputs/`, Archive unter `toolchains/`. TEST_LEDGER.md und CRITERIA_EVIDENCE.md
ordnen die AP-01–32-Verträge den Originalen zu. Explizite Befehle und
Vorbedingungen stehen in `tools/ape/README.md`. `ape report` validiert eine
explizite Auswahl anhand exakter Hashes, tatsächlicher Receipts und vollständiger
Casekataloge; es führt keine Tests aus und erzeugt kein automatisches GO.

Machbarkeitsentscheidung und Reviewurteil sind getrennt. Der unabhängige
Gesamt-Review startet erst nach dem geprüften lokalen Kandidatencommit mit
frischer OpenCode 2.0.28-/direkter DeepSeek-Flash1017-Sitzung. Ein endgültiges
Reviewurteil ist vor diesem Lauf nicht vorhanden. Finale Reviewbelege bleiben
extern und werden im Chatabschluss mit exaktem Commit/Tree genannt. Kein Push,
Release oder stiller Nachtragscommit nach finalem PASS.

## Nächster fachlicher Schritt

Ein unveränderter Vendorrelease müsste System.currentTimeMillis korrekt aus
Wallzeit ableiten; danach zuerst Clock-/HTTP-/TLS-Minimalproben, erst bei Erfolg
beide Compilerbilder und die betroffenen kleinen K/F/N-/Plattformprofile erneut.
Keine ungeprüfte Versionsroulette und kein eigener Fork. Native Linuxx64-/
Windowsx64-Runs bleiben unabhängig davon erforderlich. Ein solcher Folgeauftrag
und Releasearbeit wurden nicht begonnen.
