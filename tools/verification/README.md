# Gezielte Prüfwerkzeuge

Die JVM-Artefakte erzeugt Jenesis. Für Node-Prüfungen die in
`gradle.properties` gepinnte Node-Version verwenden.

## Installierte Prozesse

```sh
java build/interlis/ExistingJvmDistribution.java sdk
node tools/verification/lsp-lifecycle.cjs target/jenesis-jvm/delivery/lsp
node tools/verification/repository-cache-processes.cjs target/jenesis-jvm/delivery/compiler/bin/interlis-compiler
node tools/verification/http-network-processes.cjs target/jenesis-jvm/delivery/compiler/bin/interlis-compiler
node tools/verification/http-redirect-processes.cjs target/jenesis-jvm/delivery/compiler/bin/interlis-compiler
```

Der LSP-Test verwendet den installierten Launcher und prüft Initialisierung,
Shutdown und den Prozess-Exit. Die CLI-Prüfer betreiben einen lokalen HTTP-Server
und speichern ihre isolierten Läufe unter `target/verification`. Unter Windows
prüft `java build/interlis/ExistingJvmDistribution.java check` den installierten
`.bat`-Launcher einschliesslich einer lokalen HTTP-Gegenstelle.

## Build- und Editor-Adapter

`editor-check.cjs` gehört zum Jenesis-Einstieg
`java build/interlis/EditorCompatibilityRun.java EXTENSION BUNDLE`. Beide Pfade sind absolut.
Die vorbereitete Extension und das isolierte VS-Code-Programm werden über
`INTERLIS_EDITOR_PREPARED` und `INTERLIS_TEST_CODE` ausgewählt. Die Extension
besitzt den Host-Harness.

## Repositoryhygiene

```sh
node tools/verification/repository-hygiene.cjs
node --test tools/verification/repository-hygiene.test.cjs
```

Die Prüfung validiert aktive Markdown-Pfade, Anker und deklarierte Testinputs. Sie erkennt wieder eingeführte
abgeschlossene Dokumentationsablagen und phasenbezogene Testklassennamen.
