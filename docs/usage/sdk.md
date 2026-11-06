# Java SDK

`java build/InterlisJvmBuild.java sdk-package` erzeugt
`target/jenesis-jvm/delivery/sdk.zip`. Es enthält den modularen CLI-Launcher,
die Bibliotheksclosure und kompilierbare Beispiele. `sdk-check` führt echte
installierte Prozesse und den Archivconsumer aus.

[SdkExample.java](../../examples/sdk/SdkExample.java) erstellt mit der
öffentlichen Engine ein `SourceSet`, analysiert und kompiliert eine
explizite Closure. Das Beispiel zeigt den typisierten Umgang mit
`CompilationOutcome` und der freigegebenen Schemasicht.
[RepositoryNetworkExample.java](../../examples/sdk/RepositoryNetworkExample.java)
konfiguriert den Netzwerkhost mit expliziter Policy.

Host und eingebettete Anwendung besitzen ihre Quellen, den Laufkontext und
gegebenenfalls einen Workspace. Abfragen verwenden zum Snapshot gehörende
Referenzen. Snapshots, Eingabeoptionen und veröffentlichte Diagnosen bleiben
unveränderlich. Die [Compiler- und Modellverträge](../contracts/compiler-and-model.md)
definieren diese Gültigkeitsbereiche. Für Signaturen und Packageexports gilt
die Javadoc der jeweiligen JVM-Module. Bibliotheks-, Wire-, Katalog- und
Ausgabeformate haben getrennte Versionsidentitäten.
