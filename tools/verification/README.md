# Gezielte Prüfwerkzeuge

Die JVM-Artefakte erzeugt Gradle. Für Node-Prüfungen die in
`gradle.properties` gepinnte Node-Version verwenden.

## Installierte Prozesse

```sh
./gradlew :interlis-compiler-cli:installDist :interlis-lsp:installDist
node tools/verification/lsp-lifecycle.cjs interlis-lsp/build/install/interlis-lsp
node tools/verification/repository-cache-processes.cjs interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler
node tools/verification/http-network-processes.cjs interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler
node tools/verification/http-redirect-processes.cjs interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler
```

Der LSP-Test verwendet den installierten Launcher und prüft Initialisierung,
Shutdown und den Prozess-Exit. Die CLI-Prüfer betreiben einen lokalen HTTP-Server
und speichern ihre isolierten Läufe unter `build/verification`. Unter Windows
prüft `gradlew.bat packageTest` den installierten
`.bat`-Launcher einschliesslich einer lokalen HTTP-Gegenstelle.

## Repositoryhygiene

```sh
node tools/verification/repository-hygiene.cjs
node --test tools/verification/repository-hygiene.test.cjs
```

Die Prüfung validiert aktive Markdown-Pfade und Anker. Sie erkennt wieder
eingeführte abgeschlossene Dokumentationsablagen und phasenbezogene
Testklassennamen.
