# Artefaktübergabe — Jenesis, Wasm und Editor

## 1. Problem und verbindliche Lösung

Heute erhalten die Backendprojekte gemeinsame Java-Artefakte über Gradle-`project(...)`; das Editorbundle liest `interlis-lsp/build/install/...`. Beide Pfade werden durch eine **explizite, vollständige, immutable Übergabe** ersetzt. Ein kopiertes einzelnes Compiler-JAR ohne transitiven Kontext genügt nicht.

Jenesis erzeugt normale JARs/POMs/Sources. Ein kleiner Exporter materialisiert daraus einen **buildlokalen Mavenbaum** und ein zusätzliches Integritätsmanifest. Dazu ist weder Maven installiert noch ein Upload oder `mavenLocal()` nötig. Der Export verwendet verifizierte Engineergebnisse, keine selbst erfundene POM-Abhängigkeitstabelle.

Eine Snapshotidentität ist ein Inhaltsdigest der normierten relevanten Inputs und der tatsächlich gelieferten Artefaktinventare. Zeitstempel und absolute Hostpfade gehören nicht in diesen Digest. Run-/Commit-/Dirty-Herkunft wird zusätzlich protokolliert. Ein Digest ist keine digitale Signatur; das Modell schützt Konsistenz gegen Verwechslung und nachträgliche Mutation relativ zur vertrauenswürdigen Producerreferenz, nicht gegen einen Angreifer, der sämtliche Referenzen austauschen kann.

## 2. Vorgeschlagene Ausgabe

```text
target/jenesis-jvm/consumers/<snapshot-digest>/
  consumer-manifest.json
  maven/guru/interlis/<artifact>/<version>/...
  metadata/runtime-closures.json
  metadata/producer-receipt.json
  resources/...                 # nur tatsächlich benötigte eingefrorene Inputs
```

Das Verzeichnis wird frisch in einem privaten Stagingordner aufgebaut, geprüft und dann atomar unter seinem Digestnamen verfügbar gemacht. Ein existierender identischer Snapshot wird verifiziert wiederverwendet, nie überschrieben. Ein unvollständiger Versuch bleibt kein gültiger Snapshot. Alle Paths im Manifest sind relativ, separatorunabhängig, frei von Traversal und innerhalb des realen Snapshotroots; Symlinks dürfen die Grenze nicht verlassen.

Für grosse unveränderte externe JARs ist eine sichere effiziente Kopie möglich. Keine Hardlinks, deren Mutation das Originalprodukt oder einen anderen Snapshot verändert. Externe Dependencycaches bleiben Downloadcaches, keine Autorität für eigene Projektartefakte.

## 3. Datenvertrag

Schematisch, keine bereits vorhandene Datei:

```json
{
  "schemaVersion": 1,
  "kind": "interlis-jvm-consumer-snapshot",
  "producer": {
    "builder": "jenesis",
    "coreCommit": "<ACTUAL_COMMIT>",
    "coreTree": "<ACTUAL_TREE>",
    "dirty": true,
    "productInputDigest": "<SHA256>",
    "enginePinDigest": "<SHA256>",
    "resultInventorySha256": "<SHA256>"
  },
  "projectVersion": "<ACTUAL_VERSION>",
  "snapshotDigest": "<SHA256_OF_CANONICAL_PAYLOAD>",
  "artifacts": [
    {
      "projectId": "interlis-model",
      "coordinate": "guru.interlis:interlis-model:<ACTUAL_VERSION>",
      "moduleName": "guru.interlis.model",
      "descriptorKind": "explicit",
      "jar": {"path": "maven/<ACTUAL_JAR_PATH>", "sha256": "<SHA256>", "bytes": 123},
      "pom": {"path": "maven/<ACTUAL_POM_PATH>", "sha256": "<SHA256>"}
    }
  ],
  "closures": {
    "teavm": ["<ORDERED_COORDINATES>"],
    "webimage": ["<ORDERED_COORDINATES>"],
    "lsp": ["<ORDERED_COORDINATES>"]
  }
}
```

Platzhalter und Bytegrösse sind nur Darstellung. B-I darf für noch automatische eigene Module den tatsächlichen Status beschreiben; nach B5 sind eigene Module `explicit`. Keine vorzeitige Behauptung. Externe automatische Module erhalten ihren realen Descriptorstatus.

`closures` bilden Produktabhängigkeiten ab, nicht die TeaVM-/Web-Image-Compilerplugins selbst. Backendtools, JSO und besondere Graal-API-Dependencies bleiben im Backendplan separat sichtbar. Ein vom Backend zusätzlich benötigtes JVM-Produkt gehört ausdrücklich in dessen Closure, nicht als zufällig vorhandenes JAR in `lib/`.

Ein manifestinterner `snapshotDigest` darf sich nicht selbst hashen. Die kanonische Payload umfasst Schema, konkrete Produkt-/Pinidentität, geordnete Closure und Dateiinventar, aber nicht dieses Feld, mutable Reports oder absolute Ausführungspfade. Der Hash der endgültigen Manifestdatei wird im separaten übergeordneten Übergabereceipt festgehalten. Versionswechsel am Schema müssen Erzeuger/Verbraucher gemeinsam übernehmen.

## 4. Java-Exporter: Klassen-/Methodenverträge

Vorgeschlagene `build.interlis.ConsumerSnapshot`:

```java
Snapshot create(Path root, JenesisBridge.BuildOutcome outcome,
                ConsumerSelection selection, Path outputRoot);
Snapshot read(Path manifest, String expectedManifestSha256);
void verify(Snapshot snapshot, VerifiedProducer producer);
Path materializeMavenRepository(Snapshot snapshot, Path stagingRoot);
List<ArtifactRef> closure(Snapshot snapshot, ConsumerKind kind);
```

- `ConsumerSelection` ist eine feste Auswahl TEAVM/WEBIMAGE/EDITOR, keine frei ausführbare Liste.
- `create` liest `ModuleArtifacts`, eigene POMs und externe Resolveridentitäten desselben tatsächlichen Ergebnisses. Keine Versionssuche im Internet und keine zweite Auflösung eigener Projekt-SNAPSHOTs.
- `verify` prüft erwartete Produzentenreferenz unabhängig vom veränderbaren Manifest, exakte Dateimenge/-grösse/-hash, Closurevollständigkeit, GAV-/Descriptoridentität und Ressourcen. Ein JAR plus passend manipulierte lokale Manifestzeile allein darf keinen neuen gültigen Producer erzeugen.
- `materializeMavenRepository` erhält Maven-Scopes/POM-Parents/BOMs, soweit für diese Closure nötig. Consumerauflösung in einem leeren isolierten Mavenkontext muss ohne implizit fehlenden Parent funktionieren. Kein Export von Test-/Verification-/Generatorartefakten.
- `closure` ist stabil geordnet und duplikatfrei. Abweichende Dateien unter gleicher Koordinate sind Fehler. Kein erster/letzter Treffer gewinnt.

Ein privates `handoff-receipt.json` bindet Manifest, Sources/Engineinputs und Exportresultat. Dieser Beleg behauptet keine Testausführung. `build`/`artifacts` und `tested` bleiben unterschiedliche Sachverhalte.

## 5. Gradleconsumer

Kanonischer direkter Gradleaufruf erhält einen expliziten absoluten Manifestpfad und dessen erwarteten Hash, z.B. als dokumentierte `-PjvmInputs=...` und `-PjvmInputsSha256=...`. Der Projektentry übergibt sie nach Producerprüfung automatisch. Die Namen sind Implementierungsvorgaben.

Im Gradlebuild wird ein kleiner gemeinsam verwendeter Verifier eingelesen. Aufgaben:

1. Manifest und Pfadgrenzen vor Resolver-/Codegenbenutzung prüfen.
2. Ausschliesslich `guru.interlis` aus dem angegebenen buildlokalen Mavenbaum zulassen (`exclusiveContent` oder gleichwertige verifizierte Filterung); kein Remote- oder `mavenLocal()`-Fallback für diese Gruppe.
3. Genaue Versionen aus der Übergabe verwenden. Keine `latest`, `+`, Snapshot-Updateprüfung bei fremden Repositories oder Abhängigkeitssubstitution auf Quellprojekte.
4. Nach Auflösung die tatsächlich verwendeten Dateien gegen die geplanten Hashes prüfen. Auch gleiche GAV und gleicher Modulname reichen ohne gleiche Bytes nicht.
5. Übergabehash, effektive Classpathliste, Backendtoolpins und erzeugte Ausgaben im Backendreceipt verbinden.
6. Eingaben vor/nach dem Lauf erneut prüfen; Snapshotmutation oder ein neuer Producerlauf darf nicht im laufenden Build unsichtbar bleiben.

Private `SNAPSHOT`-Versionen benötigen eine ausdrückliche Cachepolitik: einmalige content-addressed Repository-URL, immutable Snapshot, ggf. eng begrenzte `changing`-Cachekonfiguration. Keine globale Cacheabschaltung. Zwei aufeinanderfolgende unterschiedliche Snapshotwurzeln mit gleicher GAV werden bei warmem Gradlecache getestet; ein stale Ergebnis wird weder benutzt noch als Erfolg ausgegeben. Ein tatsächliches Cachinghindernis eng lösen, nicht öffentliche Produktversionen improvisiert ändern.

Gradle darf seinen eigenen Dependencygraph für Tool-/Backendabhängigkeiten haben. Überschneidungen mit der Produktclosure müssen auf die erwartete Version und Bytes aufgelöst sein. Konfliktauflösung darf ANTLR oder andere Produktdependencies nicht still ersetzen. Vollständigen **effektiven** Backendclasspath prüfen, nicht nur direkt deklarierte Dependencies.

## 6. Wasm-spezifische Konsequenzen

`generateTestGrammarSource` wird nicht blind durch einen Dummy-Task ersetzt. B0 ermittelt, ob der Codegenerator tatsächlich Testgrammatiken liest oder ob es nur ein Orderingrest ist. In der derzeit dokumentierten A-Implementierung gibt es keine separat benötigten Testgrammatiken. Fehlt ein echter Input, den Rest entfernen; sonst den benötigten Generatorinput beim richtigen Jenesis-Produzenten erfassen.

`prepareBrowserHarness` darf nicht mehr `sdkProcessCheck` aus Gradle starten. Es bekommt die erforderlichen geprüften JVM-/SDK-Referenzdaten über Snapshot/Receipt. `buildBrowserTools` verwendet den bereits vorbereiteten Node-Renderer oder einen reinen Nodeaufruf mit festem Input. Kein Root-Gradle-JVM-Zyklus.

`compileWebImageProbe` darf eigene `src/webImage/java` weiterhin gegen Backend-API und die übergebenen Produkt-JARs kompilieren. Es darf nicht `interlis-compiler/src` als source hinzufügen. Node-/Browserverträge und Lizenzressourcen bleiben erhalten.

Die modularen JARs gehen byteidentisch in den Backendclasspath. Deskriptoren nicht vorsorglich strippen, JARs nicht shading/mergen und keine alten nichtmodularen Zweit-JARs bauen. Falls ein Backend das reale Descriptorformat nicht akzeptiert, mit einer kleinen Probe isolieren und als konkreten Blocker berichten; nicht den Compiler semantisch oder die Vendorversionen still ändern.

## 7. Pflichtgegenproben

Die schnellen Fixture-/Resolvertests müssen mindestens erkennen:

- Manifest fehlt, erwarteter Hash falsch, unbekanntes Schema oder leerer Pflichtumfang.
- Eigenes JAR fehlt oder ist nach Export geändert, Zusatz-JAR eingeschleust.
- JAR und seine lokale Hashzeile geändert, aber Producer-/übergeordnete Referenz unverändert.
- Snapshot A mit POM/Closure aus B; gleicher Artefaktname aber falsche Bytes.
- Externe Dependencykonfliktauflösung liefert andere Produktversion.
- Verlorener Parent/BOM oder Sources-/Licensepflichtinhalt.
- Zwei warme Builds mit gleichem SNAPSHOT-GAV und verschiedenen gültigen Snapshotwurzeln.
- Pfadtraversal, absolutes Inventarpath, Symlinkausbruch und unsichere staged Kopie.
- Vorhandener alter Gradleoutput oder globaler Maven-SNAPSHOT rettet einen absichtlich defekten Consumer nicht.

Reale Integration: einmal echte TeaVM-JS-/WasmGC-Erzeugung und schmale Laufzeitfälle; einmal Web-Image-Erzeugung/-Probe aus der neuen Übergabe. Nach der späteren Descriptoränderung werden genau diese Verbraucherrisiken erneut am neuen relevanten JARsatz geprüft, nicht alle vollständigen Browser-/Performanceprofile.
