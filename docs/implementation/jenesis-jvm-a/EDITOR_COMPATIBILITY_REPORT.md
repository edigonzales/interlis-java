# Unveränderte VS-Code-Extension mit Jenesis-LSP

A4, macOS arm64. Originalverbraucher:
`/Users/stefan/sources/interlis-vscode`, Commit
`a5b774a4d593ca5424212c8a733fe816106639f7`. Getrackte Quellen/Locks sauber;
vorhandene ungetrackte Finderdateien erhalten. Core-BASE
`477bc360349dbbd3fda42c055a05577d180a56b4`, geprüfter Vorcommitstand ehrlich dirty.
Rohwurzel: `/Users/stefan/sources/interlis-java-jenesis-evidence/`.

| Fall | Beobachtung und Originalbeleg |
|---|---|
| E-01 | Unverändertes `scripts/prepare-dev.cjs --core-dir /Users/stefan/sources/interlis-java` in isolierter Git-Archive-Kopie; npm ci mit unverändertem Lock, reales Gradle editorBundle und unverändertes stageCore(development:true), Build/Typecheck. Exit0, `a4-editor-prepare-correct-copy.log`, `a4/editor/consumer/build/dev-preparation.json`. |
| E-02 | Vollständige12-JAR-LSP-Closure ausschliesslich aus Jenesis, neues leeres server/lib, echtes kompiliertes resolveLaunchPlan akzeptiert. `a4-editor-integrity.log`, `a4/editor/shadow.receipt.json`. |
| E-03 | Echter VS Code1.140.0 Extension-Host startet unveränderte Extension0.1.0 und Java25-LSP. Exit0, `a4-editor-host-correct-cwd.log`, `a4/editor/host-receipt.json`. |
| E-04 | Echte Definition von Height auf Zeile4; ungespeicherter UnknownDomain erzeugt ILI-SEM-0001 mit passendem UTF16-Bereich7:15–28; Wiederherstellung löscht Diagnose. Gleiches Hostreceipt. |
| E-05 | stopServer löscht Diagnosen; restartServer liefert erneute ILI-SEM-0001 im erhaltenen Dirtypuffer; nach Reparatur wieder Definition, abschliessend stop. Host verlässt mit Exit0. |
| E-06 | Gültige Kontrolle zuerst; fehlendes JAR, veränderte Bytes und zusätzliches JAR werden vor Java-Start durch den echten Extensionvalidator abgelehnt; wiederhergestellte Kontrolle grün. Integritätsreceipt. |
| E-07 | Originalquell-/Lockhashes und Gitstatus vor/nach gleich; sämtliche final geladenen Schattenbytes erneut gehasht. `a4/editor/original-before.json`, `isolation-after.json`. Keine dauerhafte Extensionänderung. |

Die12 JARs sind sieben eigene Module und fünf gepinnte externe Libraries:
LSP4J/jsonrpc0.23.1, Gson2.14.0, ErrorProneAnnotations2.48.0, ANTLR-Runtime4.13.2.
`target/jenesis-jvm/delivery/runtime-inventory.json` bindet jede Datei an tatsächlichen
nativen Produzentenpfad, GAV und SHA256. Dieses Inventar ist im Schattenreceipt
archiviert. Keine Gradle-JARs, Verification, JUnit oder Generatoren beigemischt.
Alle Nichtserverdateien sind gegenüber dem realen Referenzmanifest vollständig
hashgleich; Diagramm-/Schemas-/Typesquellen blieben im Kandidaten unverändert.
Das Original-Release-Lock wurde weder überschrieben noch auf einen anderen Core
umgebunden. Nur Scratch-Testlocks stammen aus neuen tatsächlichen Schattenbytes.

Zusätzlich wurde der fertige Projekteinstieg real ausgeführt:

```sh
INTERLIS_NODE=/Users/stefan/.cache/interlis-java/runtime/node-v24.22.0-darwin-arm64/bin/node \
INTERLIS_EDITOR_PREPARED=/Users/stefan/sources/interlis-java-jenesis-evidence/a4/editor/consumer/build/dev-extension \
INTERLIS_TEST_CODE='/Users/stefan/sources/interlis-vscode-evidence/gui-preflight/storage/Visual Studio Code.app/Contents/MacOS/Code' \
/Users/stefan/.sdkman/candidates/java/25.0.3-tem/bin/java build/InterlisJvmBuild.java editor-check \
  --extension-dir /Users/stefan/sources/interlis-vscode \
  --reference-bundle /Users/stefan/sources/interlis-java/build/editor-bundle
```

Exit0, `a4-editor-profile.log`; vollständiger eigener Scratchlauf
`target/jenesis-jvm/editor-compat/run-6MIw7y/receipt.json`, `host-command.json`,
`host-exit.json`, `host.log`. Der zweite Hostlauf prüft die neu fertiggestellte
Profilintegration; er wird nicht als zusätzliche JVM-Testmenge gezählt.
`editor-check` ruft kein Gradle auf: das ausdrücklich vorgängig vorbereitete
Developmentergebnis, dessen Sources gegen das Original geprüft werden, wird in
eine neue Testkopie übernommen. Kein stageCore-Aufruf nach der Schatteninjektion.

Runtime-Modus ist **nicht paketierte Development-Extension mit absolutem Java25-
Override**: Temurin25.0.3, `/Users/stefan/.sdkman/candidates/java/25.0.3-tem/bin/java`.
Der unveränderte reale Launcher prüft Java-Version und komplette Serverhashes.
Node24.22.0 stammt aus dem vorhandenen gepinnten Cache. Die vorhandene Extension-
JRE-/jlink-/VSIX-Pipeline wird weder verändert noch erneut implementiert. Dies
belegt den kleinen API-Extension-Host-Verbrauch auf diesem Host; keine neue
VSIX-, GUI-Gesamtmatrix-, Windows-/Linux- oder Runtime-Paketfreigabe.

Fehlversuche bleiben erhalten: unvollständige erste Archiveextraktion und falsches
Runner-CWD endeten vor dem Hoststart; die korrigierten Läufe sind oben benannt.
Der reguläre Corepfad build/editor-bundle und Gradles installDist enthalten
weiterhin Gradleprodukte. Unabhängiger Review steht bis A5/R aus.
