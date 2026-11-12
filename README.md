# interlis-java

Java-Werkzeuge für INTERLIS 2.3 und 2.4. Das Repository enthält den Compiler,
die Java-SDK-Schnittstellen, eine CLI, einen LSP-Server, eine Swing-Anwendung
zur Modellprüfung und einen Browserclient für Compiler und Diagramm-DTOs. TeaVM erzeugt
JavaScript und WasmGC. Ein separater GraalVM-Web-Image-Adapter dient dem
Backendvergleich. Der Transferkern und die Streaming-Codecs für XTF 2.3 FULL
und XTF 2.4 FULL sind als Java-Bibliotheken verfügbar. Eine separate Bibliothek
[prüft lokale INTERLIS-Datenregeln](docs/usage/transfer.md#lokale-datenvalidierung).
Transferweite Prüfungen und Datenbankabbildung sind
[geplant](docs/design/transfer-validation-database.md).

## Ein Modell prüfen

Mit einem Java-25-JDK im Repository ausführen.

```sh
java build/interlis/ExistingJvmDistribution.java sdk
target/jenesis-jvm/delivery/compiler/bin/interlis-compiler check \
  testdata/models/base24.ili testdata/models/survey24.ili
```

Unter Windows heisst der Launcher `interlis-compiler.bat`. Der JVM-Build
verwendet Jenesis 0.18.1. `java build/jenesis/Make.java` baut und prüft
alle JVM-Module. Node.js 24.22.0 und der Gradle-Wrapper werden für
die portablen Backends benötigt.

- [Compiler-Anleitung](docs/usage/compiler.md), [CLI-Referenz](docs/usage/cli.md),
  [Java SDK](docs/usage/sdk.md), [Transfer](docs/usage/transfer.md), [LSP](docs/usage/lsp.md), [Desktop](docs/usage/desktop.md) und [Browser](docs/usage/browser.md)
- [Build](docs/development/build.md), [Tests](docs/development/testing.md) und
  [Freigabeprüfung](docs/development/release.md)
- [Architektur](docs/architecture.md) und [Anforderungsregister](docs/contracts/requirements.md)
