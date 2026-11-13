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

Für [lokale Datenvalidierung](transfer.md#lokale-datenvalidierung) und
[Datensatzvalidierung](transfer.md#datensatzvalidierung) fügt der Host
`guru.interlis:interlis-validation` hinzu. Das optionale JVM-Artefakt
`guru.interlis:interlis-validation-mvstore` liefert den dateigestützten
Datensatzspeicher. Diese Module werden wie die Transferadapter unabhängig
vom Compiler-SDK-ZIP gebaut. Der lokale Validator und `TextLengths` wurden
mit SDK 0.4 eingeführt.


## Getrennte Versionen

| Vertrag | Wert | Bedeutung |
|---|---|---|
| Werkzeug und Artefakte | `0.2.0-SNAPSHOT` | Entwicklungsstand der ausgelieferten Module |
| Öffentliche Java-API | `0.9` | Öffentliche Packages und Typen von `guru.interlis` |
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

`TransferReader.transferPlan()` gibt den gebundenen Plan für den
Revisionsabgleich einer Datensatzsession zurück.
`stableIdentityNamespaces()` stellt die vom Reader gelesenen Aliasbindungen
bereit. Diese Methoden erweitern die Reader-API. Die Datensatzvalidierung
ergänzt ausserdem `ValidationCode` um Diagnosen zu mehrdeutiger Identität sowie
fehlenden, typfremden und basketfremden Referenzzielen.

API 0.6 ergänzt `DatasetLimits` um `maxRelationWork()`. Der bisherige
Konstruktor setzt das neue Limit auf 10 Millionen Arbeitsschritte.
Record-Patterns müssen die neue Komponente aufnehmen. Ein erschöpfender Switch
über `ValidationCode` muss auch `ORDER_POSITION_DUPLICATE`,
`ORDER_POSITION_GAP`, `COMPOSITION_MULTIPLE_OWNERS` und
`COMPOSITION_CYCLE` behandeln.

Die Standardkardinalität einer Assoziation ohne `CARDINALITY` ist `{0..1}`
pro Teilnehmerkombination. INTERLIS 2.3 und 2.4 erlauben bei einer
Kompositionsrolle `{0..1}` und `{1}`. Ohne Angabe gilt `{0..1}`.

API 0.7 ergänzt `DatasetLimits` um `maxConstraintWork()` und
`maxKeyBytes()`. Die bisherigen Konstruktoren setzen dafür 10 Millionen
Arbeitsschritte und 8 MiB pro kanonischem Schlüssel. Record-Patterns müssen die
beiden Komponenten aufnehmen. Erschöpfende Switches über `ValidationCode`
und `DatasetRuleCoverage.Outcome` müssen die neuen Fälle behandeln.

`DATASET_UNIQUE_DUPLICATE` erscheint einmal pro Dublettengruppe mit
ausgewähltem Subjekt. Die Argumente enthalten Eingabe, Basket und Kennung
eines weiteren Gruppenmitglieds sowie die Gruppengrösse. Teilen zwei
virtuelle Zeilen denselben Datenort, folgen als fünftes und sechstes Argument
die einsbasierten Zeilennummern von Subjekt und Zeuge in der
Auswertungsreihenfolge der Sicht. Die Zeilennummern gelten nur für den
jeweiligen Validierungslauf. Diese optionale Erweiterung erhält die ersten
vier Argumente und die SDK-API-Version 0.8. Ein partieller Lauf kann konkrete Dubletten im verfügbaren Bestand belegen. `EXISTENCE_TARGET_MISSING` setzt voraus, dass sämtliche relevanten
Zielalternativen geschlossen und prüfbar sind. Eine beobachtete Übereinstimmung
genügt auch bei offenen Alternativen. `UNSUPPORTED_VALUE_SEMANTICS` kennzeichnet
unter anderem Geometrievergleiche, die eine topologische Gleichheit voraussetzen.

API 0.8 verwendet `Optional<BasketId>` für `DataLocation.basket()`.
Der Konstruktor mit einem gelieferten `BasketId` bleibt verfügbar.
Globale Mengenbefunde können ohne Basket und ohne Objektkennung
ausgegeben werden. Ein erschöpfender Switch über `ValidationCode`
berücksichtigt auch `SET_CONSTRAINT_FALSE` und
`PLAUSIBILITY_CONSTRAINT_FALSE`.

Ein geschlossener Lieferbestand oder tatsächlich gelieferter leerer Basket
kann einen SET-Befund mit null Kandidaten und einer Auswertung erzeugen.
Für einen nicht gelieferten Basket weist die Coverage null Scopes aus.
Prozentbedingungen vergleichen Zähler und Nenner exakt. Die Argumente eines
Befunds enthalten erfüllte Instanzen, Gesamtzahl, Richtung und Grenzwert.

API 0.8 erhält zusätzlich `INVALID_FORMATTED_VALUE` für Verletzungen
gebundener nichttemporaler FORMAT-Layouts und Grenzen sowie
`DERIVED_VALUE_MISMATCH` bei einem gelieferten Wert, der von seiner
gebundenen Ableitung abweicht. Geprüfte Wertebereiche und Schlüssel
berücksichtigen formatierte Werte. Bestehende
Methodensignaturen und Diagnoseschlüssel sind kompatibel. Erschöpfende
Switches über `ValidationCode` müssen den zusätzlichen Code berücksichtigen.

Das optionale JVM-Artefakt `interlis-validation-mvstore` erweitert API 0.8
um `MvStoreValidationStorage.open(Path, MvStoreOptions)`. Der Host stellt
ein vorhandenes Temp-Wurzelverzeichnis und eine Dateiquota bereit. Das Backend
erzeugt ein privates Laufverzeichnis. Der Host schliesst den Speicher nach
dem Lauf. Cache und Schreibpuffer sind MVStore-Konfigurationen und keine
Obergrenze für den gesamten JVM-Heap. Die Dateiquota begrenzt die logische
Länge der Arbeitsdatei. `diskBytes()` meldet die aktuelle Länge und
`peakDiskBytes()` die höchste durch einen erfolgreichen Schreibvorgang
erreichte Länge. Eigene Speicherimplementierungen können bei einem
Budgetende den öffentlichen `ValidationLimitException(String)`-Konstruktor
verwenden. Das optionale Modul einschliesslich `peakDiskBytes()` und der zusätzliche
Konstruktor sind additive Erweiterungen von API 0.8. Der Validator übernimmt einen leeren Store exklusiv und schliesst alle
Schreibvorgänge vor Ausgabe des Berichts ab.
[H2 MVStore](https://h2database.github.io/html/mvstore.html)

### Laufzeitparameter und Hostfunktionen (P4.6)

Die zusätzliche Überladung von `ValidationPlanner.plan` nimmt immutable
`RuntimeBindings` entgegen. Schlüssel sind kanonische `SymbolRef`-Werte
des geprüften Snapshots. Eine Funktionsbindung trägt die gebundene Signatur,
Implementierungs-ID und Version. Ein neuer Parametersatz benötigt einen
neuen Plan. Der Host stellt vertrauenswürdige Java-Implementierungen bereit.

Fehlende Parameter ergeben fehlenden Laufzeitkontext. Für nicht registrierte
Funktionen weist die Planung fehlende Ausdrucksunterstützung aus. Eine
Providerexception oder ein unzulässiger Rückgabewert ergibt
`FunctionEvaluationException`. Abbruch und Ressourcenlimits behalten ihren
eigenen Fehlerzustand. Die neue Überladung und die Bindungstypen ergänzen
die SDK-API 0.8 additiv.

Datensatzvalidierung wählt virtuelle Sichten aus den angeforderten Modellroots. Die Transferpläne beschreiben weiterhin die
physisch gelieferten Objekte. Sichten werden aus den gebundenen Basen
ausgewertet. Befunde an Sichtbedingungen enthalten den Modellort der
Bedingung und den Datenort der zugrunde liegenden Lieferung.


### Validator und Datensatzlauf (API 0.9)

`ValidationPlanner.plan(transferPlan, profile, bindings)` verwendet die
ursprüngliche Schemainstanz des Transferplans. Datensatz-Reader verwenden
dieselbe Instanz. `DatasetOptions.defaults()` prüft den gelieferten Bestand,
`DatasetScope.PARTIAL_DATASET` lässt Aussagen über fehlende Populationen offen.
Die öffentliche Auswahl und Deklaration einzelner Extents entfällt.
`ValidationReport.datasetScope()` gibt den vereinbarten Laufumfang an.


API 0.9 liefert die XTF-Reader und -Writer gemeinsam als
`guru.interlis:interlis-xtf` im Modul `guru.interlis.xtf`.
Die Java-Packages `guru.interlis.xtf23` und `guru.interlis.xtf24` bleiben erhalten.
Dokumentationsprojektion und Renderer kommen aus `interlis-projection`.
