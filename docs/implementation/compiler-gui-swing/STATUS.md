# Compiler GUI Swing

Branch `codex/compiler-gui-swing`, Startbasis `76a8182edea6d6b58d53c0f221c61a76724bad06`.
Separater Worktree. In der Startbasis und den sichtbaren lokalen Branches
wurde kein GUI-Produktmodul oder JavaFX-PoC gefunden.

## G0 – Foundation in Prüfung

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
diese Reviewer-Tools und den Desktop nicht. G1 ist bis zum unabhängigen
G0-PASS gesperrt.
