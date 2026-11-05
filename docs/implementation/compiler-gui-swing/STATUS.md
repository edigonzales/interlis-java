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

## G1 – Compilation bestanden

GUI-07 bis GUI-13. Check unterstützt ausdrücklich hinzugefügte lokale Dateien,
Dateilisten-Drop, Entfernen und explizite Modell-Roots auch in Mehrmodell-Dateien.
Der Compilerlauf friert Eingaben und Optionen ein, verwendet die gemeinsame
Repositoryvorbereitung und prüft über den Workspace. Resultate behalten
Quellrevisionen, typisierte Ausgänge, Diagnosen und verfügbare Provenienz.
Das EDT-Modell trennt Cancel, tatsächliche Beendigung und alte Resultate.

`java build/InterlisJvmBuild.java gui-check --profile compile` prüft die
Bedienung im real geöffneten Fenster mit Dateilisten-Transfer und kontrolliertem
Hintergrundauftrag. G1-PASS für `364406a4289db4e257a6fbf4850e002c5825b5d6`.
Sieben headless GUI-Tests, ein CLI-Hosttest und der Headful-Compile-Smoke
bestanden auf macOS ARM64. Die unabhängige Mehrmodell-Gegenprobe und das
gültige Urteil liegen lokal unter
`../interlis-java-compiler-gui-g0.TVRLc5/G1/364406a4289db4e257a6fbf4850e002c5825b5d6/round-01/`.

## G2 – Diagnostics bestanden

GUI-14 bis GUI-20. Die Problemtabelle zeigt strukturierte Diagnosen des
letzten Laufs. Gruppierung und Filter belassen Gesamtzahlen und Ergebnis.
Ursachen, Folgediagnosen und verwandte Quellstellen verwenden ihre IDs
und exakten Bereiche. Die Vorschau liest den erfassten Snapshot und
bildet originale UTF-16-Offsets auf die sichtbaren Zeilenenden ab.
DE/EN und Copy message/details verwenden die gemeinsame Formatierung.

`java build/InterlisJvmBuild.java gui-check --profile diagnostics` startet
einen Headful-Lauf mit Fehlern in zwei Quellen, sortierter Auswahl,
verwandter Fundstelle, Kopieren und einem anschliessend erfolgreichen Lauf.
G2-PASS für `573e0c366a631bb93bbf886fa8e33c73681264ac`. Die erste Runde
wurde wegen fehlender globaler Kriterien im JSON verworfen. Der neue Review
mit unveränderten Produktquellen und vollständig validiertem Urteil liegt
unter `../interlis-java-compiler-gui-g0.TVRLc5/G2/573e0c366a631bb93bbf886fa8e33c73681264ac/round-02/`.

## G3 – Dependencies bestanden

GUI-21 bis GUI-28. Der Explorer zeigt konkrete Wurzeln und echte
Importbindungen aus der strikten Vorbereitung desselben Workspace-Laufs.
Gemeinsame Abhängigkeiten erscheinen pro Pfad. Zyklen werden als Referenz
beendet. Direktimporte und direkte Nutzer sind getrennt. Resolution zeigt
die erfassten Suchentscheidungen und die Workspace-Spur. Quelle und
Problemfilter verwenden die zum Lauf gehörenden Bereiche.

`java build/InterlisJvmBuild.java gui-check --profile dependencies` prüft
den Explorer im realen Fenster. G3-PASS für `c4f1439d2f2ee9e563dd3791aeac407075a5e261`.
Die GUI- und Workspace-Verträge, der CLI-Host-Smoke und beide unabhängigen
Gegenproben sind bestanden. Das Urteil liegt unter
`../interlis-java-compiler-gui-g0.TVRLc5/G3/c4f1439d2f2ee9e563dd3791aeac407075a5e261/round-01/`.

## G4 – Repositories bestanden

GUI-29 bis GUI-38. Repositories verwaltet explizit konfigurierte lokale
Verzeichnisse und HTTP(S)-Basen. Indexe und Quellenpreview werden asynchron
über den gemeinsamen Hostloader geladen. Die Tabelle hält Indexvorkommen
und Originalversionen getrennt. Vergleich und Quellenpreview ändern den
Kompilierauftrag nicht. Die ausdrückliche Rootübernahme erhält Name, Version
und SourceId. Die nächste Prüfung verwendet die sichtbare Prioritätsliste.

`java build/InterlisJvmBuild.java gui-check --profile repositories` prüft
Metadatenvergleich, Preview und exakte Rootübernahme im realen Fenster.
G4-PASS für `be632c74bbf55733f066103fc5885be2b4a8ed14`. Acht GUI-Tests,
der Headful-Smoke und zwei unabhängige Repository-Gegenproben sind bestanden.
Das Urteil liegt unter
`../interlis-java-compiler-gui-g0.TVRLc5/G4/be632c74bbf55733f066103fc5885be2b4a8ed14/round-01/`.

## G5 – Integration bestanden

GUI-39 bis GUI-44. Gemeinsame Compiler- und Hostoptionen sind im
Settingsdialog zugänglich. Die Einstellungen werden ausserhalb des EDT
geladen und atomar gespeichert. Beschädigte Werte und Schreibfehler sind
sichtbar. Der Laufzustand bietet Cancel, einen Hinweis auf veraltete
Resultate und ein begrenztes Laufprotokoll. Der Headful-Smoke
`java build/InterlisJvmBuild.java gui-check --profile lifecycle` prüft
Tastaturbedienung, Dark Theme, Fenstergrösse und das tatsächliche
Prozessende nach dem Schliessen während eines laufenden Auftrags.


G5-PASS für `3dfd48e80cf18b589d455533e3c90bb0168af905`. Der erste
Review beanstandete die Sichtbarkeit von markierten Quellbereichen.
Die korrigierte Vorschau erhält die Markierung beim Themewechsel.
15 gezielte JUnit-Tests sowie die Headful-Profile `diagnostics`,
`lifecycle` und `repositories` bestanden. OpenCode bestätigte
die gemalten UTF-16-Bereiche in beiden Themes und den Shutdown.
Das Urteil liegt unter
`../interlis-java-compiler-gui-g0.TVRLc5/G5/3dfd48e80cf18b589d455533e3c90bb0168af905/round-02/`.

## G6 – Delivery in Prüfung

GUI-45 und GUI-48 bis GUI-52, mit kumulativer Prüfung von GUI-01 bis GUI-52.
`gui-package` baut die native macOS-ARM64-App mit eingebettetem Java.
`gui-check --profile packaged` prüft den unveränderten nativen Start
und das reguläre Prozessende ausserhalb des Checkouts. Der Swing-Treiber
durchläuft Check, Repositories und Dependencies im nativen App-Prozess.
Die [Benutzer- und Bauanleitung](USER_GUIDE.md) nennt die Plattformgrenzen.
