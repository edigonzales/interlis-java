# Tatsächliche Ausgangslage

Auftrag A, A0 am 04.11.2026. Ausgangscommit
`477bc360349dbbd3fda42c055a05577d180a56b4`, Tree
`40ed15f9578eaf6fb0e3fa824ee324df64c70cd9`; ursprünglicher Worktree sauber.
Referenz: separater detached Worktree unter
`/Users/stefan/sources/interlis-java-jenesis-evidence/gradle-reference`.
Alle weiteren hier referenzierten Rohbelege liegen unter dessen Geschwister `a0/`.
Historische Ergebnisse werden nicht als neue Läufe ausgegeben.

Java: explizit Temurin 25.0.3, Gradle-Wrapper 9.8.1, Produkt 0.2.0-SNAPSHOT.
Die anfängliche Shellauswahl Java21/Node22 ist keine Produkttesttoolchain.
ANTLR4.13.2, JUnit5.11.4, LSP4J0.23.1, Gson2.14.0 bleiben unverändert.
`source-inventory.json` inventarisiert sämtliche getrackten Originalinputs mit SHA256.
`gradle-dependencies/*.json` erfasst die effektiven Compile-/Runtime-/Test-/ANTLR-
Konfigurationen mit Koordinaten, Pfaden und Fremdartefakthashes. Projekt-Classdirs
sind ausdrücklich keine Fremd-JAR-Hashes; deren Klassen-/JAR-Inventare folgen A4.

## Reale normale Testbaseline

`gradle-baseline.log`: Exit0, 53 ausgeführte Tasks, 32s. Aufruf im Rohlog/
Testledger; ausschliesslich zehn normale JVM-Testtasks und lesende Inventartasks.
Original-XMLs unter `gradle-raw/`; Herkunft und Hashes in
`gradle-baseline-receipt.json`. P und V werden anhand der Modulzuordnung getrennt.

| Modul | Fälle | Fehler | Skips |
|---|---:|---:|---:|
| model | 24 | 0 | 0 |
| compiler | 788 | 0 | 0 |
| workspace | 23 | 0 | 0 |
| repository | 112 | 0 | 0 |
| projection | 22 | 0 | 0 |
| documentation | 8 | 0 | 0 |
| wasm-bridge (JVM) | 24 | 0 | 0 |
| compiler-cli | 141 | 0 | 0 |
| lsp | 76 | 0 | 0 |
| verification | 79 | 0 | 0 |

Summe1297; Identitätsvergleich einschliesslich Parameterinvocations ist A4,
keine Freigabe aus der Zahl allein. C/S bleiben getrennte explizite Profile;
Auswahlquelle ist `completion-links.json` plus `current-bindings.json`, wie
`gradle/verification-contracts.gradle` sie kombiniert.

## Verbraucher

Extensioncheckout `/Users/stefan/sources/interlis-vscode`:
`a5b774a4d593ca5424212c8a733fe816106639f7`; unversionierte `.DS_Store`-Dateien
bleiben erhalten. Stage startet weiterhin `gradlew editorBundle --no-daemon`.
Compilerlauncher verwendet automatische Module, Extension-LSP `java -cp`.
Der vorhandene Extension-Hostrunner bestätigt `INTERLIS_TEST_EXTENSION_ROOT`,
`INTERLIS_TEST_CODE`, `INTERLIS_JAVA`; ein isolierter vorhandener VS-Codehost
liegt unter `interlis-vscode-evidence/gui-preflight/storage/Visual Studio Code.app`.
Vorhandener Hosttest prüft Aktivierung/Definition/Dirty-Restart; Diagnoseprobe
muss A4 ergänzen. Noch kein E-01–07-PASS.
