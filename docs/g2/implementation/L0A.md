# L0A – verschachtelte Blockkommentare

Lieferung: L0A / Pilot 0a, kein reguläres kumulatives Phasengate.
Basis: `865c99bab6ccea4ca3900d897381fa3a2a41fe39` (sauberer Arbeitsbaum beim Start).
Stand: Implementierung vorhanden; unabhängiges Urteil ausstehend.

## Umfang und Vertrag

AC0A-01–04; SRC-002/005, SYN-002/005/006/015, SEM-003 und TEST-001/011/012.
Normbezug: INTERLIS 2.3 §2.2.8.2, S.26
(`NORM:2.3:2.2.8.2:p26:002`) und 2.4 §3.2.8.2, S.31
(`NORM:2.4:3.2.8.2:p31:006`), vorhandene gepinnte Auszüge in
`docs/evidence/stabilization/g2-norm-inventory.json`: Blockkommentare besitzen
Öffner/Abschluss; Verschachtelung ist erlaubt. Jeder Öffner benötigt seinen Abschluss.

Der gewöhnliche Zeichen-Zweig der rekursiven Lexerregel darf weder `/*` noch
`*/` konsumieren. Zwei Lookahead-Guards erzwingen dies ausschliesslich in
`InterlisLexer.g4`. Keine Änderung an GraphBuilder, SyntaxTreeBuilder, Gate oder
öffentlichen Signaturen. Unvollständige Kommentare fallen wie bisher auf die
normalen Tokens zurück: ILI-SYN-0002 / ERROR / PARSING am originalen Öffnungsschrägstrich;
zusätzliche Recoverydiagnosen sind möglich. Es wird kein neuer Diagnosecode eingeführt.

`NestedBlockCommentContractTest` ergänzt sechs JUnit-Invocations (drei Methoden,
je 2.3/2.4): Originalfixture und Compile-Ablehnung, einfache/dreifach verschachtelte
Positivfälle samt überlappungsnahen Slash-/Sternfolgen, offene innere/äussere
Kommentare. LF/CRLF, Supplementary Unicode vor/im Kommentar, STRICT/EDITOR,
Rekonstruktion, Trivia-/Tokenbereiche mit Quelle/Revision, folgende Klasse und
öffentliches Schema bzw. SOURCE_ERRORS werden geprüft. Die beiden ursprünglichen
Methoden in `LexicalRecoveryContractTest` bleiben bytegenau erhalten.
Der gemeinsame portable Vertrag erhält zehn zusätzliche Compile-Fälle.

Aktive Einstiege verweisen auf `docs/g2/EXECUTION_PLAN.md` und `STATUS.md`.
Die bestehenden Quell-/Grammatikinventare werden nur an die geänderten Quellen
angepasst: Hashes, Offsets, Zeilennummern, BlockComment-Vorkommen und zwei
Hashreferenzen in der Matrix. Der bestehende einfache Quellscanner zählt auch
Klammern/Fragezeichen der Java-Prädikate; dadurch steigen seine strukturellen
Vorkommen von 1218 auf 1225 (BlockComment 4 auf 11). Der exakte Zähltest wird
entsprechend erhöht und zusätzlich gegen die Matrix geprüft. 321 Produktionen,
alle fachlichen Erwartungen, Norm-/Suitepins, XFAILs und offenen Abnahmestände
bleiben erhalten. Dies ist keine neue Inventur oder fachliche Vollständigkeitsabnahme.

## RED vor Produktionsänderung (AC0A-01)

```sh
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem ./gradlew :interlis-compiler:test --tests '*LexicalRecoveryContractTest.openOuterCommentAfterClosedInnerCommentNeverProducesCompiledSchema' --rerun-tasks
```

Exit **1**, zwei ausgeführte Tests, zwei Failures, keine Errors/Skips:
`CompilationOutcome.Succeeded` statt `Rejected`, sowohl 2.3 als auch 2.4.
Quelle ist `testdata/g2/a2-unterminated-nested-comment.ili`; nur der Header wird
für 2.4 ersetzt. Rohlog und XML: `build/reports/g2/L0A/red.log`, `red.xml`.
Der neue Test wurde anschliessend unverändert in die eigene Klasse verschoben,
damit die bestehenden methoden-/offsetgebundenen Evidenzen gültig bleiben.
Auf der Basis lässt sich RED mit der neuen Testklasse und dem gleichen
`--tests '*NestedBlockCommentContractTest.openOuterCommentAfterClosedInnerCommentNeverProducesCompiledSchema'`
reproduzieren.

## Ausführung und Rohberichte (AC0A-02/03)

Umgebung: macOS 15.8.1 aarch64; Temurin **25.0.3+9**, Gradle-Wrapper **9.8.1**,
gepinnter Node **v24.22.0** unter
`/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node`.
Default-PATH beim Einstieg: Java 21.0.7, Node v22.13.0; deshalb explizite Pfade.
Vorhandene Tasks wurden mit `./gradlew tasks --all` ermittelt (Exit 0),
`build/reports/g2/L0A/tasks.txt`.

Gezieltes GREEN nach Lexerfix: `:interlis-compiler:test --tests '*LexicalRecoveryContractTest'`,
Exit **0**, acht Invocations vor Verschiebung der sechs neuen Invocations in die
neue Klasse. Ein Zwischenlauf hatte zwei Testfehler wegen des Vergleichs eines
`DeclaredName` mit String; auf `.value()` korrigiert, keine Erwartung geändert.

Der erste vollständige Prüflauf scheiterte an den veralteten Evidenzhashes und
Methodenoffsets; nach Trennung der neuen Tests und Aktualisierung der vorhandenen
Quellverweise blieb zunächst der genaue alte Grammatikzähler rot (1218 statt 1225).
Logs: `regression.log`, `regression-final.log` unter `build/reports/g2/L0A/`.
Diese Zwischenfehler werden nicht als erfolgreiche Verifikation ausgegeben.

Finaler Lauf (nach allen Produktions-/JVM-/Portable-Änderungen), Exit **0**:

```sh
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem ./gradlew check conformanceCheck :interlis-wasm-teavm:jsContractTest :interlis-wasm-teavm:wasmContractTest -PnodeExecutable=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node --rerun-tasks
```

Log: `build/reports/g2/L0A/verification.log`, Dauer 2m 9s, 49 Tasks ausgeführt.

| Prüfung | Tatsächlicher Umfang / Ergebnis | Rohbericht |
|---|---|---|
| JVM | **706 Tests**, 0 Failures/Errors/Skips; davon Compiler 621 und neue Klasse 6 | `interlis-*/build/test-results/test/TEST-*.xml` |
| SDK-Beispiel | öffentlicher JAR-Prozess, SDK PASS, ein Rootmodell | `verification.log` |
| Conformance-Wrapper | 20 Tests, erfolgreich | `verification.log` |
| G2-Evidenz | 59 Tests, erfolgreich | `verification.log` |
| T2 vollständig | 593/610 ausgewählt, 17 ausgeschlossen; Projekt **574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS** | `build/conformance/normative-assessment.json` |
| T2 Rohbewertung | Runner Exit **1**, **574 PASS / 18 FAIL / 1 XFAIL / 0 ERROR/XPASS** | `build/conformance/summary.json`, `report.json`, `report.junit.xml`, `conformance.log` |
| TeaVM JavaScript / Node | **3267 Fälle**, erfolgreich, inklusive zehn neuer Kommentarvarianten | `interlis-wasm-teavm/build/js-contract-result.json` |
| TeaVM WasmGC / Node | **3267 Fälle**, erfolgreich, inklusive zehn neuer Kommentarvarianten | `interlis-wasm-teavm/build/wasm-contract-result.json` |

Die 18 bestehenden normativen Referenzabweichungen und der separate INTERLIS-1-
Profilfall werden unverändert durch den vorhandenen Wrapper bewertet. Die
Nutzerentscheidungen bleiben offen; der Pilot erteilt dafür keine Normfreigabe.
Suite-Commit `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3`, Archiv-SHA-256
`6a80b7e8faf4bdfda8561478cbf1f5538fc959e743e3ccfc158ae23b7c9337fe` unverändert.
Die breit wirksame Lexeränderung wurde bewusst gegen die gesamte T2-Suite geprüft.
Der nicht prüfende Disassembly-Task `disasmWasmGC` ist konfigurationsbedingt SKIPPED;
kein JUnit- oder portabler Pflichttest wurde übersprungen.

Nach der abschliessenden Konsistenzkorrektur von Matrixzähler und zusätzlicher
Zählerassertion: `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem ./gradlew g2EvidenceTest`;
Exit **0**, 59 Tests, 0 Fehler/Skips; `build/reports/g2/L0A/evidence-final.log`.
`git diff --check`, JSON-Parsebarkeit der drei angepassten Datenbestände,
relative Dokumentlinks und `node --check interlis-wasm-teavm/stabilization-contract.cjs`
wurden vor Commit erfolgreich geprüft (Exit 0). Keine fällige L0A-Implementierungsprüfung ausgelassen.

## Abnahmegrenze (AC0A-04)

AC0A-04 benötigt zusätzlich den unabhängigen OpenCode-Review am nach dem Commit
ausgelesenen vollen Kandidaten-SHA sowie mindestens einen eigenen neuen Lexerfall
des Reviewers. Der SHA gehört ausschliesslich in den nachgelagerten Handoff.
Kein selbst erteiltes PASS. Keine Arbeit an L0B-1 oder Pythonentfernung in L0A.
Safari, Browsermatrix und Web Image sind kein L0A-Pilotgate; ihre vollständigen
Gates bleiben spätestens für L0B-3 offen. P3/P4/P5 sind nicht bearbeitet.
