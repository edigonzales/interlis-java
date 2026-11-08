# LSP und Editorintegration

Der JVM-Server verwendet LSP über stdin/stdout. `sdk-package` erzeugt den
modularen Launcher unter `target/jenesis-jvm/delivery/lsp/bin/interlis-lsp`.
Unter Windows heisst er `interlis-lsp.bat`. Der Editor besitzt den
Clientprozess und dessen Konfiguration. Server-Logs verwenden die vom
Protokoll getrennten Kanäle.

Der Editor sendet versionierte Dokumentänderungen. Diagnosen erscheinen
für den aktuellen Stand. Navigation, Hover, Vervollständigung, Rename und
Formatierung verwenden die [Workspace-Verträge](../contracts/workspace-and-lsp.md).
Repositoryquellen sind im Editor lesbar und die Auflösung läuft über die
explizite Hostkonfiguration. Die Diagnoseprosa kann auf Deutsch oder
Englisch konfiguriert werden.

`java build/interlis/EditorBundleRun.java` erzeugt die explizite Übergabe für Editorclients.
Das Paket enthält die benötigten Verträge und Produktartefakte mit
Prüfsummen. Revisionstreue Editorbefehle können kompilieren, unveränderliche
Diagramme abfragen und geprüfte Dokumentationsausgaben anfordern.
Der gemeinsame Viewer behandelt Navigation und Layout in der UI.
