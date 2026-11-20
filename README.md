# interlis-java

Java-Werkzeuge für INTERLIS 2.3 und 2.4. Das Repository enthält den Compiler,
die Java-SDK-Schnittstellen, eine CLI, einen LSP-Server, eine Swing-Anwendung
zur Modellprüfung und einen Browserclient für Compiler und Diagramm-DTOs. TeaVM erzeugt
JavaScript und WasmGC. Ein separater GraalVM-Web-Image-Adapter dient dem
Backendvergleich. Der Transferkern und die Streaming-Codecs für XTF 2.3 FULL
und XTF 2.4 FULL sind als Java-Bibliotheken verfügbar. Eine separate Bibliothek
[prüft INTERLIS-Datenregeln](docs/usage/transfer.md#lokale-datenvalidierung).
Das [Datenbankkommando](docs/usage/database.md) erstellt ili2db-kompatible
Schemata in GeoPackage und PostgreSQL/PostGIS und tauscht XTF-Datensätze aus.

## Ein Modell prüfen

Mit einem Java-25-JDK im Repository ausführen.

```sh
./gradlew :interlis-compiler-cli:installDist
interlis-compiler-cli/build/install/interlis-compiler/bin/interlis-compiler check \
  testdata/models/base24.ili testdata/models/survey24.ili
```

Unter Windows verwende `gradlew.bat` und `interlis-compiler.bat`.
`./gradlew check` baut und prüft die JVM-Module. Die portablen
Backend-Prüfungen verwenden zusätzlich Node.js 24.22.0.

- [Compiler-Anleitung](docs/usage/compiler.md), [CLI-Referenz](docs/usage/cli.md),
  [Java SDK](docs/usage/sdk.md), [Transfer](docs/usage/transfer.md), [LSP](docs/usage/lsp.md), [Desktop](docs/usage/desktop.md) und [Browser](docs/usage/browser.md)
- [Build](docs/development/build.md), [Tests](docs/development/testing.md) und
  [Freigabeprüfung](docs/development/release.md)
- [Architektur](docs/architecture.md) und [Anforderungsregister](docs/contracts/requirements.md)
