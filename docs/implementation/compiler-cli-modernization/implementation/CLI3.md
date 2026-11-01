# CLI3 – kumulative Produktabnahme

**Abgeschlossen mit unabhängigem PASS für alle 28 Kriterien CLI1-01–09,
CLI2-01–11 und CLI3-01–08.** Geprüfter Produktcommit:
`0136aec461b60c668c63344f7a30ee4bcec4fd78`.
Phasenbasis bleibt `f24ce11a5616b64d6c912c230a781558ad0fae09`, Auftragsbasis
`c3d736ac82365524835adef8df40789f3fa3ae60`. CLI1-/CLI2-Abnahmen und historische
G2-/P3-Nachweise behalten ihre jeweiligen tatsächlichen Produktstände.

## Umsetzung und Beobachtung

Die echte Plattformmatrix umfasst 26 namentlich geprüfte CLI-Prozesse statt 19:
zusätzlich Root-Isolation, fremde/relevante Warnungsablehnung, DE-/JSON-Diagnostik,
beschädigte angeforderte Quelle ohne Modell und ungültige Sprachwahl vor IO.
Der semantische Prozess verwendet syntaktisch gültigen Code mit unaufgelöstem
Attributtyp und verlangt ausdrücklich ILI-SEM-0001. Plattform-/Releasechecker
verlangen mindestens 1217 Tests/227 XML-Suites; fehlende neue Fälle, Suites und
XML-Nachweise werden adversarial abgelehnt. Aktive README, CLI-/SDK-Profile und
Diagnoseverträge beschreiben die tatsächlichen neuen Einstiege und API-Namen.

Die Windows-CI auf a01c5f874421 scheiterte am Emoji im Argument. Java25 wandelt
Windows-argv über CP_ACP um (OpenJDK JDK-8337506). Der erzeugte Windows-Launcher
übergibt deshalb jedes Argument über Unicode-Umgebungsvariablen an Main;
Delayed-/CALL-Expansion der Werte bleibt ausgeschaltet. Der tatsächliche
Windows-Aufruf mit `-Gültig 😀 & [x]!.ili` besteht mit Exit0 und leeren Streams.
Keine Zusatzruntime, keine Änderung der Sprach- oder Pfadregeln.

Der zusätzliche ältere 13-Modell-Benchmark deckte einen veralteten strikten
JSON-Vergleich auf. CorpusExpectations trennt jetzt Prosa vom Maschinenvergleich
und prüft weiterhin sämtliche Maschinenfelder, Anzahl, URI und Revision, nun
auch die wirklichen RelatedLocations. Fachlicher Beleg CLI2-08/09: Im bytegepinnten
Metadata-Original liegt STRUCTURE FileFormat bei UTF-16 [1135,1145), im
Gesuchsteller-Original TOPIC Gesuchsteller bei [635,648). Nur die neuen
cliRelatedLocations ergänzen das Erwartungsdokument; entfernt man diese zwei
Eigenschaften, ist es strukturell identisch mit dem vorherigen Stand.
Original-/Kontrollpins, Normentscheidung, Negativausgänge, Primärbereiche und
Argumente bleiben unverändert. Browser-/Bridge-Erwartungen bleiben identisch.
Die bestehenden Tests verwenden jetzt wirkliches CLI-JSON aus beiden Originalen
und 15 gezielte Manipulationen. verificationTest erhält explizit den Main-Output.
Vertragstests und 13er-Benchmark: Exit0/38s vor dem Produktcommit; im abschliessenden
vollständigen Profil und in der CI erneut am exakten Kandidaten ausgeführt.

## Tatsächliche vollständige Qualifikation

Java25.0.3, Gradle9.8.1, Node24.22.0, GraalVM25.0.3+9.1, Binaryen133,
echtes Safari27.0; originale geerbte Python-Sperre, keine zweite Guardinstanz.
Der Nutzer autorisierte Branchpush und die bestehende Windows-/Linux-CI.
[Run41267880218](https://github.com/edigonzales/interlis-java/actions/runs/41267880218)
besteht für exakt 0136aec461b6; Collector und Reviewer prüften GitHub-Digests,
Archivinhalt, RohXML und Commit-/Quellbindung selbst.

Eigener und unabhängiger Aufruf enthielten releaseCheckP3 mit --rerun-tasks,
referenceCheck, originalViewReferenceCheck, viewReferenceCheck, unitReferenceCheck,
semanticComparison, jvmBenchmark und :interlis-compiler-cli:distZip.
Tatsächlich beobachteter Graph: 193 Tasks; 106 ausführbare Tasks ausgeführt.
Eigener Lauf **Exit0/31m36s**, unabhängiger Gradle-Lauf **32m20s erfolgreich**.
Beide äusseren Original-Sperrmanifest-/Prozessabschlüsse sind **passed/Exit0**.

- JUnit: 1217 Tests/227 Suites, null Failures/Errors/Skips; 26 CLI-/9 SDK-Prozesse
  auf macOS arm64 sowie echtem Linux x64 und Windows x64, identischer Kandidat.
- Conformance: 577 PASS/16 XFAIL/0 FAIL/XPASS/ERROR, 593 Fälle, 21 Normbewertungen.
  Externer ili2c: 5 Fälle; Original-Views19/19; Views134 eigene Erwartungen,
  102 Referenzübereinstimmungen/6 Referenzprozessfehler; Units123/121/0.
  Semantikvergleich: 4 Fälle, 8 portable Projektionen und 8 Mutationen.
- JS/WasmGC/Web Image jeweils 3560+24; Chromium/Firefox/WebKit und echtes Safari
  mit vollständigen vorgesehenen Client-/Transport-/Neustart-/UI-Verträgen.
- Alle 347 gepinnten Closures/366 Dateien auf JVM, Node/WasmGC, Chromium, Firefox,
  WebKit und Safari: vollständig, null Skips und null semantische Abweichungen.
  Beide Läufe enthalten 2000 echte Editoränderungen, feste Performance-/Speicher-/
  Abbruch-/Retentionsbudgets und erfolgreiche performanceCheck/releaseEvidenceCheck.
- Zwei saubere Builds liefern 76 bytegleiche Dateien. SDK-ZIP: 11 Einträge,
  SHA256 `b95ec7acb0692d6776ed68dc278d399e87622051dcaf4a688ea3f0b23d093ac8`.
  Modul-ZIP: `2e74cee28920129fd6ce8a2bc0b6a8a6154b55faabc05c51b708fe159c264de3`.
- Eigene Archivgegenproben: 10 SDK-/4 Modulprozesse ausserhalb des Checkouts,
  7 manipulierte Inventare und 4 tatsächlich beschädigte SDK-ZIPs abgelehnt.
  Unabhängige neue CRLF-/Emoji-/Root-/Import-/Warnungs-/Sprach-/Archiv-/Liefer-
  und CI-Gegenproben bestanden; alle 28 Kriterien haben eigene Belege.

Quellfingerprint: `80cb0627db82b0b5b91cc54202465b74e632afba1078ddcf64b4d4eab394b071`.
Office-Original unverändert: `fbb1bccc0e84795284f9fb1a3c0be9e6db516c3c4b8ae43ad2f856cf70948431`.
Keine Änderung der Sprach-/Normerwartungen, Suitepins, XFAILs oder Budgets.
Die drei optionalen Emscripten-/Disassembly-Tasks sind keine Pflichtprüfungen;
keine fällige Qualitätsprüfung wurde ausgelassen.

## Reviewabschluss und erhaltene Zwischenbefunde

[Unveränderter CLI3-R2-PASS](../reviews/CLI3-r2-0136aec461b6-PASS.md), SHA256
`84bad97078c1250aea35e5abe1d4fface02e1739c9f0315b1b07d6279f694035`.
OpenCode2.0.28, DeepSeek direkt, deepseek-flash/DeepSeek-V4.1-Flash-1017;
Standalonesitzung ses_e6a4762deffelYicWMzBlH76C0, tatsächlicher ProzessExit0,
originales äusseres Guardmanifest passed/0, Kandidat/Review-HEAD sauber.
Strikter Audit prüft Identitäten, alle28 Kriterien, RohXML, vollständige Captures,
CI-/SDK-/Repro-Hashes und unveränderte Berichtskopie.

[CLI3-R1-BLOCKED](../reviews/CLI3-r1-0136aec461b6-BLOCKED.md), unverändert,
SHA256 `9a9e91e1e4c49a38ef1da95e7d70092a61f921225015879498779d747283325a`:
26 Kriterien erfüllt, CLI3-05/08 nicht nachgewiesen, keine Produktfindings.
Zwei vollständige Versuche endeten bei gesperrtem Mac nach321/347 Safari-Roots
vor Root322; isolierter Root322 bestand. Frühere eigene Fehlläufe auf a01c5f8
und f950863 bleiben ebenfalls erhalten. Nach dem tatsächlichen Entsperren durch
den Nutzer bestehen derselbe unveränderte Produktstand und der volle Safari-
Langlauf sowohl eigenständig als auch im frischen unabhängigen Review.
Keine Transportänderung, kein Snapshotreset, kein Root-Skip und keine längere
Operationsdeadline wurden als Ersatz eingeführt. Der Wachhalter blieb
prozessgebunden; keine Authentifizierung oder globale Einstellung wurde geändert.

Der unabhängige Reviewer dokumentiert zusätzlich einen Fehler seines zsh-
Log-Wrappers nach dem erfolgreichen Gradle-Ende (readonly status, kein gate.exit).
Die unveränderten Rohresultate und sein Bericht trennen diesen Wrapperfehler
vom erfolgreichen Gradle-/Releasegate. Der tatsächliche äussere Standalone- und
Guardabschluss wurde anschliessend von Codex separat mit Exit0/passed geprüft.

Rohbelege: build/review/compiler-cli/CLI3/release-unlocked.log und
local-acceptance-audit.json; CLI3-r1-0136aec461b6/blocked-audit.json sowie
CLI3-r2-0136aec461b6/acceptance-audit.json und jeweilige saubere Reviewworktrees.
Auftrag CLI1–CLI3 abgeschlossen. Keine offenen Muss-Findings, Pflichtnachweise
oder Blocker. Der abschliessende Commit enthält ausschliesslich Statusnotizen
und unveränderte Berichte gemäss EXECUTION_PLAN §9.2; Produktnachweise beziehen
sich weiterhin ausdrücklich auf 0136aec461b60c668c63344f7a30ee4bcec4fd78.
