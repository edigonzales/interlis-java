# Java SDK

`java build/interlis/ExistingJvmDistribution.java sdk` erzeugt
`target/jenesis-jvm/delivery/sdk.zip`. Es enthält den modularen CLI-Launcher,
die Bibliotheksclosure und kompilierbare Beispiele. `java build/interlis/ExistingJvmDistribution.java check` führt echte
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

Für [lokale Datenvalidierung](transfer.md#lokale-datenvalidierung) fügt der
Host die separate Bibliothek `guru.interlis:interlis-validation` hinzu.
Sie wird wie die Transferadapter unabhängig vom Compiler-SDK-ZIP gebaut.
Die Validator-API und `TextLengths` ergänzen SDK 0.4. Bereits veröffentlichte
Signaturen, Modulexports und Wire-Verträge bleiben kompatibel; dafür ist
keine Erhöhung der Vertragsversion nötig.


## Getrennte Versionen

| Vertrag | Wert | Bedeutung |
|---|---|---|
| Werkzeug und Artefakte | `0.2.0-SNAPSHOT` | Entwicklungsstand der ausgelieferten Module |
| Öffentliche Java-API | `0.5` | Öffentliche Packages und Typen von `guru.interlis` |
| Diagnosekatalog | `interlis-diagnostics/v4` | Stabile Diagnosecodes und typisierte Argumente |
| Bridge | `interlis.compiler/1.0` | Nachrichtenhülle, Ergebnisse und Abfragen |
| Quellformatierung | `2.0` | Formatierungsoptionen und Ergebnisvertrag |
| Diagramm und Diagrammoptionen | `1.0` | Diagrammstruktur, Identitäten und Optionsschema |
| Sprachprofile | INTERLIS 2.3 und 2.4 | [Geltende Sprachauslegung](../contracts/norm-decisions.md) |

`version --json`, Bridge-Capabilities und das mitgelieferte
Versionsmanifest stimmen in den gemeinsamen Feldern überein. Snapshot-
und Schemahandles gehören zu ihrer Sitzung und Generation. Sie werden
weder persistiert noch zwischen Clients übertragen.


API 0.4 ergänzt bei `TransferValue.XmlFragment` die unveränderliche
`namespaceBindings()`-Map. Sie erhält auch den Namespacekontext textueller
QNames ausserhalb von Kindelementen. Der Einargument-Konstruktor erzeugt eine
leere Zusatzmap. Record-Patterns verwenden die beiden Komponenten `xml` und
`namespaceBindings`; die zusätzliche Komponente begründet die Erhöhung der
Java-API-Vertragsversion.

API 0.5 ergänzt `ValidationCoverage` um `datasetRules()`. Der bisherige
Konstruktor erzeugt weiterhin einen Bericht für lokale Prüfungen. Record-Patterns
müssen die zusätzliche Komponente aufnehmen. Ein erschöpfender Switch über
`PlanningProblem.Code` muss den neuen Fall `EXPRESSION_LIMIT_EXCEEDED` behandeln.
`ValidationCoverage.ConstraintStatus` weist `UNSUPPORTED_EXPRESSION` für
tatsächlich ausgewählte, nicht ausführbare generische Domainregeln aus.
