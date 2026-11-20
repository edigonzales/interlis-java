# LSP und Editorintegration

Der JVM-Server verwendet LSP über stdin/stdout. `./gradlew :interlis-lsp:installDist` erzeugt den
modularen Launcher unter `interlis-lsp/build/install/interlis-lsp/bin/interlis-lsp`.
Unter Windows heisst er `interlis-lsp.bat`. Der Editor besitzt den
Clientprozess und dessen Konfiguration. Server-Logs verwenden die vom
Protokoll getrennten Kanäle.

Der Editor sendet versionierte Dokumentänderungen. Diagnosen erscheinen
für den aktuellen Stand. Navigation, Hover, Vervollständigung, Rename und
Formatierung verwenden die [Workspace-Verträge](../contracts/workspace-and-lsp.md).
Repositoryquellen sind im Editor lesbar und die Auflösung läuft über die
explizite Hostkonfiguration. Die Diagnoseprosa kann auf Deutsch oder
Englisch konfiguriert werden.

`./gradlew :interlis-lsp:distZip` erzeugt das Maven-Distributions-ZIP
`guru.interlis:interlis-lsp-distribution:0.2.0-SNAPSHOT@zip`. Es enthält die
LSP-Module unter `interlis-lsp/lib`, TypeScript-Verträge unter `types`, die
JSON-Schemas unter `schemas` sowie die Lizenzen. Der Server startet mit Java
25 und `--module-path interlis-lsp/lib --module guru.interlis.lsp/guru.interlis.lsp.Main`. Revisionstreue Editorbefehle können kompilieren, unveränderliche
Diagramme abfragen und geprüfte Dokumentationsausgaben anfordern.
Der gemeinsame Viewer behandelt Navigation und Layout in der UI.
