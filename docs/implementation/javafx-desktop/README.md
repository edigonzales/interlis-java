# INTERLIS Compiler für macOS ARM64

Lokaler Prototyp auf `codex/javafx-desktop-prototype` nach der
[Auftragsspezifikation](REQUEST.md). Er lädt ausgewählte INTERLIS-Dateien,
kompiliert sie im selben Prozess und zeigt Diagnosen, aufgelöste Abhängigkeiten
und das interaktive Sprotty-Klassendiagramm.

## Bauen und starten

Voraussetzungen: macOS ARM64, JDK 25 ohne Preview und Node **24.22.0** gemäss
Repositorypin. Die [Gluon JavaFX-JMODs](https://gluonhq.com/products/javafx/)
für macOS ARM64 müssen als Original-ZIP bereitstehen. Version und SHA-256 sind
fest: `openjfx-25.0.2_osx-aarch64_bin-jmods.zip`,
`4cd258001c75af7047005c5c891e2400ed11d24fbb09412324c0cbaf8b503c5a`.
Die JavaFX-JARs werden separat mit derselben Version und ihren Prüfsummen in
der Modul-POM deklariert. POMs sind Metadaten; Jenesis 0.18.1 baut alles.

```sh
export INTERLIS_NODE=/absolute/path/to/node-v24.22.0-darwin-arm64/bin/node
java build/InterlisJvmBuild.java gui-package \
  --javafx-jmods /absolute/path/to/openjfx-25.0.2_osx-aarch64_bin-jmods.zip
open "$(cat target/jenesis-gui/app-path.txt)"
```

`gui-package` baut die App und startet den automatisierten Ablauf aus einer
verschobenen Kopie mit ausschliesslich lokalem Loopback-Netz. Der Pfad zum
Bundle, die Rohresultate sowie SVG/PNG liegen unter `target/jenesis-gui/`.
`gui-run` startet direkt vom Jenesis-Modulpfad, `gui-check` prüft denselben
Ablauf ohne Packaging. Beide benötigen nur JDK und Node. Die
App selbst benötigt weder diese Werkzeuge noch das Repository.

**Example** lädt zwei mitgelieferte Modelle. Anschliessend **Compile** drücken.
**Open** ersetzt die aktuelle Auswahl mit einer oder mehreren `.ili`-Dateien.
Für importierte Modelle die betreffenden Dateien gemeinsam auswählen. Der
Prototyp verwendet lokale Eingaben und die eingebauten INTERLIS-Definitionen;
er durchsucht keine Modellrepositories. Die Diagrammleiste enthält Zoom und
SVG-/PNG-Export. Ziehen auf dem Diagrammhintergrund verschiebt die Ansicht;
ein Klick selektiert ein Diagrammelement.

## Architektur und Prüfgrenzen

- Optionales, explizites JPMS-Modul `guru.interlis.compiler.gui`. Die vorhandenen
  zehn JVM-Module, CLI-/LSP-Starts und Backend-Buildpfade behalten ihren Umfang.
  Das GUI verwendet nur Modell, Compiler, Workspace und Projektion. Jenesis bindet deren
  JARs/POMs aus der tatsächlichen Producerinvocation und vergleicht die
  aufgelösten Runtimebytes mit dem Inventar.
- Die Dateiauswahl liefert unveränderliche Eingaben an eine JavaFX `Task` auf
  einem virtuellen Thread. Der Compiler entscheidet einmal über die Closure.
  Die Oberfläche zeigt dessen gemeinsame Diagnosen und echte Modellbindungen.
  Ein Eingabewechsel verwirft alte Resultate und bricht einen laufenden Task ab.
- `DiagramJsonCodec` und `DiagramCanvas` bleiben die Besitzer von Drahtformat
  und Darstellung. Das unveränderte gemeinsame Bundle, ELK-Worker, CSS und
  Lizenzen liegen in der GUI-JAR. Ein HTTP-Server auf einem zufälligen Port von
  `127.0.0.1` liefert ausschliesslich diese Ressourcen. Jede Instanz besitzt ein temporäres
  WebView-Datenverzeichnis. Es gibt keine CDN-Zugriffe.
- Jenesis benutzt seine Schritte `JLink` und `JPackage`. Die Runtime enthält
  JavaFX und die benötigten JDK-Module. Die unveränderten Produkt-JARs und das
  automatische ANTLR-Modul liegen auf dem Modulpfad der App. Die Moduldateien
  werden von `jpackage` mitgeliefert. Kein neuer Renderer, kein Wasm- oder LSP-Host.

Zuordnung: WP-15/WP-31 (Verbraucher), VIZ-001/004/010/011/014/018, ARCH-008,
BUILD-001–004; Phase 3 verwendet zusätzlich DIAG-002/003/006 und die vorhandenen
Compile-/Closure-Verträge. Die erwartete Beobachtung und die ausgeführten
Phasenabnahmen stehen im [Ergebnisbericht](REPORT.md).
