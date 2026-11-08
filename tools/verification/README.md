# Gezielte Prüfwerkzeuge

Die JVM-Artefakte erzeugt Jenesis. Für Node-Prüfungen die in
`gradle.properties` gepinnte Node-Version verwenden.

## Consumer-Snapshot

```sh
java build/InterlisJvmBuild.java wasm-export
INTERLIS_SNAPSHOT_HANDOFF="$PWD/target/jenesis-jvm/reports/wasm-export.json" \
  node --test tools/verification/consumer-snapshot.test.cjs
```

Das Node-Testmodul übergibt den veröffentlichten Manifesthash an
`ConsumerSnapshot` und prüft positive und manipulierte Snapshots mit dem
JVM-Verifizierer. Die Mutationen laufen in temporären Kopien.

## Installierte Prozesse

```sh
java build/InterlisJvmBuild.java sdk-package
node tools/verification/lsp-lifecycle.cjs target/jenesis-jvm/delivery/lsp
node tools/verification/repository-cache-processes.cjs target/jenesis-jvm/delivery/compiler/bin/interlis-compiler
node tools/verification/http-network-processes.cjs target/jenesis-jvm/delivery/compiler/bin/interlis-compiler
node tools/verification/http-redirect-processes.cjs target/jenesis-jvm/delivery/compiler/bin/interlis-compiler
```

Der LSP-Test verwendet den installierten Launcher und prüft Initialisierung,
Shutdown und den Prozess-Exit. Die CLI-Prüfer betreiben einen lokalen HTTP-Server
und speichern ihre isolierten Läufe unter `target/verification`. Auf Windows
den passenden `.bat`-Launcher auswählen.

## Build- und Editor-Adapter

`layout.test.cjs` prüft die expliziten Rollenpfade für die Host- und
Releaseprofile. `node-junit.test.cjs` prüft das Einlesen der Node-JUnit-Berichte.
Beide sind über `node --test` ausführbar. Die portablen Buildprofile verwenden
`backend-smoke.cjs`, `node-contract.cjs` und `runtime-evidence.cjs` mit den
erzeugten Artefakten und der zugehörigen Layout- und Snapshot-Evidenz.

`editor-check.cjs` gehört zum Jenesis-Einstieg
`editor-check --extension-dir ABSOLUT --reference-bundle ABSOLUT`.
Die vorbereitete Extension und das isolierte VS-Code-Programm werden über
`INTERLIS_EDITOR_PREPARED` und `INTERLIS_TEST_CODE` ausgewählt. Die Extension
besitzt den Host-Harness. Die Pythonfreiheitsprüfung läuft im JVM-Gesamtbuild
über `without-python.cjs` und die JVM-Quellenprüfung.

## Repositoryhygiene

```sh
node tools/verification/repository-hygiene.cjs
node --test tools/verification/repository-hygiene.test.cjs
```

Die Prüfung validiert aktive Markdown-Pfade und Anker, deklarierte
Testinputs sowie gepinnte Referenzpfade. Sie erkennt wieder eingeführte
abgeschlossene Dokumentationsablagen und phasenbezogene Testklassennamen.
