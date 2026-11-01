# CLI3 – kumulative Produktabnahme

Phasenbasis **f24ce11a5616b64d6c912c230a781558ad0fae09**, Auftragsbasis
c3d736ac82365524835adef8df40789f3fa3ae60. CLI1-PASS ed25d5311346,
CLI2-PASS dfd442c4a90d bleiben auf ihren exakten Produktständen erhalten.
Kriterien CLI3-01–08 und kumulativ CLI1-01–09/CLI2-01–11; zugehörige
CLI-/SDK-/BUILD-/DIAG-/LOG-/PERF-/PORT-Verträge bleiben verbindlich.

## Umsetzung und erwartete Beobachtung

CompilerCliProcesses prüft26 echte Plattformprozesse statt19: zusätzlich
Root-Isolation, fremde/relevante Warnungsablehnung, DE-/JSON-Diagnostik,
angeforderte beschädigte Quelle ohne Modell und ungültige Sprachwahl vor IO.
Die tatsächlichen Streams/Diagnosecodes/JSON-Messages werden ausgewertet;
kein Erfolg durch blosse Anpassung einer Anzahl. Der alte als „semantic“
bezeichnete Prozess verwendete eine bereits syntaktisch ungültige DOMAIN-
Eingabe; jetzt ist die Quelle syntaktisch gültig mit unaufgelöstem Attributtyp
und der Prozess muss ausdrücklich ILI-SEM-0001 liefern. Sprachakzeptanz und
Normerwartungen ändern sich dadurch nicht.

Plattform- und Releasechecker verlangen alle26 namentlichen Fälle und die
in CLI2-R2 tatsächlich belegte Mindestgrenze1217 Tests/227 XML-Suites.
Drei zusätzliche adversariale Checkerproben verwerfen fehlende neue JVM-
Fälle, eine fehlende Suite und einen fehlenden XML-Nachweis. Keine Anzahl
wurde als Ersatz für einen tatsächlichen Nachweis gesenkt.

README, CLI-/SDK-Profil und der aktive Diagnosevertrag nennen die tatsächlichen
Aufrufe, Default EN, DE-/JSON-Präsentation, Request-/Analysesichten und neue
Artefakte. Alte nicht vorhandene RenderingContext-/JsonOutput-Signaturbeispiele
sind im Fachvertrag auf die tatsächlichen Console-/JSON-Einstiege präzisiert.
Die README nennt die belegten GraalVM25.0.3-/Binaryen133-Pins; das npm-Beispiel
verwendet npm ci im Modul-Subshell und verlässt nicht das Repository.

## Ausgeführte gezielte Prüfungen

Java25.0.3, Gradle9.8.1, Node24.22.0; compilerCliCheck und
releaseEvidenceCheckerTest, CLI3/targeted.log: **Exit0/12s**,
**26 reale CLI-Fälle,98 Node-Gegenproben ohne Fehler/Skips**.
Die abschliessend verschärfte Suite-/XML-Untergrenze wird durch den fälligen
sauberen platformCheck und das vollständige Produktprofil erneut ausgeführt.
Toolchains tatsächlich gelesen: native-image25.0.3+9.1, Binaryen133,
Safari27.0 (20625.1.29.18.28). Keine Pins/Normen/XFAILs verändert.

## Plattformbefund und Korrektur

Der Nutzer hat den Push von a01c5f874421604e2451ed6ee9cb153595d38b59
und den CI-Start ausdrücklich autorisiert. Run41262729579: Linux erfolgreich,
Windows fehlgeschlagen beim unveränderten Prozessfall `option-end` mit
`-Gültig 😀.ili`. Java25 konvertiert Windows-argv über CP_ACP und ersetzt
das Emoji durch `?` (OpenJDK JDK-8337506; launcher/main.c, jdk-25-ga).
Der erzeugte Windows-Launcher übergibt deshalb seine Argumente einzeln über
Unicode-Umgebungsvariablen. Main liest sie nur im ausdrücklich vom Launcher
aktivierten Modus; kein neuer Bedienweg, keine Zusatzruntime und keine
Änderung der Sprach-/Pfad-/Stream-Erwartungen. Delayed expansion und CALL-
Expansion der Argumentwerte bleiben ausgeschaltet. Die tatsächliche Korrektur
ist erst mit erneuter Windows-CI am neuen sauberen Kandidaten belegt.

Der lokale Gesamtlauf auf a01c5f874421 hat nach35m56s bei Full-Corpus-Safari
abgebrochen:321/347 Roots, HTTP-Timeout vor Root322 SO_ARP_MJPNL_20240606.
Alle347 JVM-/Node-/Chromium-/Firefox-/WebKit-Roots waren vollständig erfasst;
die funktionalen Safari-/Web-Image-/Browserprofile bestanden. Root322 einzeln
und die unveränderte Sequenz320–322 bestanden im diagnostischen Scratchlauf.
Diese Teilproben ersetzen keinen vollständigen Safari-Nachweis. Timeout,
Rootliste, Erwartungen und Budgets sind unverändert. Zwei tatsächliche saubere
Reproduzierbarkeitsbuilds ergaben76 byteidentische Lieferdateien auf a01c5f874421.
Rohdaten einschliesslich Fehlläufen liegen unter build/review/compiler-cli/CLI3/.

## Plattformwege und noch offene Pflichten

Der vorhandene GitHub-Workflow p3-platforms.yml ist erreichbar; bestehende
Anmeldung hat Repo-/Workflowzugriff. Die letzten erfolgreichen echten Linux-/
Windowsläufe beziehen sich auf historische P3-SHAs (u.a.4b586b1e09a2,
Run41214503758). Sie dürfen nicht auf diesen Kandidaten umetikettiert werden.
Der vorhandene Dockerdaemon ist Linux/aarch64; die konfigurierte, ausgeschaltete
Fedora42-VM ist ARM64. Kein verfügbarer Windows-x64-Prüfhost ist nachgewiesen.

Der verbindliche releaseCheckP3-Preflight verlangt zuerst erfolgreiche echte
GitHub-Windows-/Linuxjobs des exakten sauberen Kandidaten und die originale
aktive Python-Sperre. Er wird nicht abgeschaltet oder durch einen dünneren
Erfolgstask ersetzt. Ein Remote-Push ist durch diesen Auftrag nicht pauschal
autorisiert; für den konkreten fertig vorbereiteten Kandidaten muss nötigenfalls
gezielt die Freigabe für Branchpush und CI-Start eingeholt werden. Alle davon
unabhängigen lokalen Pflichtprüfungen werden fortgesetzt.

Vollständige Schlussprüfung, reale Safari-/Web-Image-/Corpus-/Performance-
und Reproduzierbarkeitsnachweise sowie kumulative unabhängige CLI3-Abnahme
sind noch offen; bis zu deren tatsächlichem Abschluss kein CLI3-/Gesamt-PASS.
