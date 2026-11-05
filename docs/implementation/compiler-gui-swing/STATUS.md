# Compiler GUI Swing

Branch `codex/compiler-gui-swing`, Startbasis `76a8182edea6d6b58d53c0f221c61a76724bad06`.
Separater Worktree. In der Startbasis und den sichtbaren lokalen Branches
wurde kein GUI-Produktmodul oder JavaFX-PoC gefunden.

## G0 – Foundation bestanden

GUI-01 bis GUI-06 sowie GUI-46 und GUI-47. Das JPMS-Modul
`guru.interlis.compiler.gui` zeigt mit FlatLaf 3.7 ein Fenster mit Check,
Dependencies, Repositories und Sitzungsstatus. Noch nicht verfügbare
Compileraktionen sind deaktiviert. Die GUI-Auslieferung löst ihre eigene
Compiler- und Hostclosure auf. Das jlink-Image erhält den expliziten
ANTLR-Descriptor aus Jenesis’ Modularisierungsschritt. Die reguläre
ANTLR-Abhängigkeit der übrigen Produkte bleibt unverändert.

Aus dem Produktworktree mit Java 25 ausführen:

```sh
java build/InterlisJvmBuild.java build --module interlis-compiler-gui
java build/InterlisJvmBuild.java gui-run
java build/InterlisJvmBuild.java gui-runtime
java build/InterlisJvmBuild.java gui-check --profile shell
```

`gui-runtime` schreibt ein separates Image samt Closure- und Prozessbelegen
unter `target/jenesis-jvm/gui/<run-id>/runtime`. Start mit
`runtime/bin/java --module guru.interlis.compiler.gui/guru.interlis.gui.GuiMain`.
`gui-check --profile shell` startet auf dem Desktop sowohl über den Modulpfad
als auch aus dem Image und schreibt zwei Aufnahmen der Fensterinhalte.

Für den OpenCode-Starter mit Python und jsonschema dient
`uv run --no-project --with jsonschema==4.25.1 python docs/implementation/compiler-gui-swing/review/run.py --help`.
Er benötigt die vorhandene DeepSeek-Anmeldung oder einen autorisierten
`DEEPSEEK_API_KEY`, ein externes Testledger und ein neues Reviewverzeichnis.
Die Prüfmaschine ist macOS ARM64. Die normalen Jenesis-Tests benötigen
diese Reviewer-Tools und den Desktop nicht.

G0-PASS für `3c8d5bea3cc591f16ad8f296859eb336a9397386`. OpenCode 2.0.28
verwendete `deepseek/deepseek-flash` über `https://api.deepseek.com`. Die
offizielle Modellzuordnung für diese Runde war DeepSeek-V4.1-Flash-1017.
Der reguläre Prozessabschluss und das unveränderte Urteil liegen lokal unter
`../interlis-java-compiler-gui-g0.TVRLc5/G0/3c8d5bea3cc591f16ad8f296859eb336a9397386/round-04/`.
Die drei früheren Runden bleiben als zwei Authfehler und ein Abbruch
wegen fehlerhafter Leserechte dokumentiert.

## G1 – Compilation in Prüfung

GUI-07 bis GUI-13. Check unterstützt ausdrücklich hinzugefügte lokale Dateien,
Dateilisten-Drop, Entfernen und explizite Modell-Roots auch in Mehrmodell-Dateien.
Der Compilerlauf friert Eingaben und Optionen ein, verwendet die gemeinsame
Repositoryvorbereitung und prüft über den Workspace. Resultate behalten
Quellrevisionen, typisierte Ausgänge, Diagnosen und verfügbare Provenienz.
Das EDT-Modell trennt Cancel, tatsächliche Beendigung und alte Resultate.

`java build/InterlisJvmBuild.java gui-check --profile compile` prüft die
Bedienung im real geöffneten Fenster mit Dateilisten-Transfer und kontrolliertem
Hintergrundauftrag. G2 folgt nach gültigem G1-PASS.
