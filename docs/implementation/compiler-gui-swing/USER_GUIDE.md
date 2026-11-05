# INTERLIS Compiler für den Desktop

Die GUI prüft INTERLIS-2.3- und -2.4-Modelle. Die native App ist für
macOS auf Apple Silicon gebaut und enthält Java. Öffne
`INTERLIS Compiler.app` im Finder oder starte ihren Launcher aus einem
Terminal.

## Modelle prüfen

1. Öffne **Check** und wähle **Add files…** oder ziehe `.ili`-Dateien
   ins Fenster. Die Modellliste zeigt die erkannten Modelle. Mit
   **Use selected roots** wählst du einzelne Modelle aus, auch wenn
   mehrere in derselben Datei stehen.
2. Wähle **Compile**. **Cancel** bricht den laufenden Auftrag ab. Der
   Status zeigt das Ergebnis des letzten Laufs. Nach einer Änderung
   an Eingaben oder Optionen ist ein erneuter Lauf nötig.
3. Wähle in **Problems** eine Zeile, um Code, Meldung und die
   markierte Stelle in der schreibgeschützten Quellvorschau zu sehen.
   Verwandte Fundstellen lassen sich einzeln öffnen. Filter ändern
   die sichtbaren Zeilen. Die Gesamtzahlen gehören zum gesamten Lauf.

Öffne **Dependencies**, um Wurzeln und direkte Importe auszuklappen.
Der Inspector zeigt Herkunft, direkt importierende Modelle und den
erfassten Auflösungsweg. **Show source** öffnet die Quelle des Laufs.
**Show problems** wechselt zur passenden Auswahl in **Check**. Bei
unaufgelösten Importen kann der Baum unvollständig sein.

## Aus einem Repository prüfen

1. Öffne **Repositories** und füge mit **Add repository…** ein lokales
   Repositoryverzeichnis oder eine HTTP(S)-Basis hinzu. Lade die
   Indexe mit **Reload**. Die Liste zeigt die Reihenfolge, in der die Auflösung
   die aktivierten Repositorys durchsucht.
2. Wähle ein konkretes Indexvorkommen. Mit **Preview source** kannst
   du den Modelltext lesen. **Compare metadata** vergleicht zwei
   Einträge. Versionen werden so angezeigt, wie sie im Index stehen.
3. Wähle **Add exact root** und anschliessend **Compile** in
   **Check**. Eine lokale Datei ist für diesen Lauf nicht nötig.
   Einträge mit `browseOnly` sind von der Rootübernahme ausgeschlossen.

Aktiviere **Offline**, um Netzwerkzugriffe zu unterbinden. Lokale
Repositorys und zuvor im Cache gespeicherte HTTP(S)-Inhalte bleiben
verfügbar. **Settings…** enthält Cache, Proxy, Timeouts, Theme und
Diagnosesprache. Einstellungen werden unter
`~/.interlis/gui/settings.properties` gespeichert.

Die GUI umfasst weder Editor noch Diagramme, Format Preview,
Transferdatenvalidierung oder Datenbankimport. CLI und LSP haben
eigene Startprogramme.

## Lokal bauen und prüfen

Aus dem Repository mit einem Java-25-JDK ausführen. Der Builder ist
Jenesis 0.18.1. Die native Abnahme lief mit Temurin 25.0.3+9 auf
macOS 15.8.1 ARM64. Der Headful-Pakettest benötigt einen angemeldeten
grafischen Desktop und die Swift-Kommandozeilenwerkzeuge von Apple
für die Prüfung von Fenster und Quit.

```sh
java build/InterlisJvmBuild.java build --module interlis-compiler-gui
java build/InterlisJvmBuild.java gui-run
java build/InterlisJvmBuild.java gui-runtime
java build/InterlisJvmBuild.java gui-package
java build/InterlisJvmBuild.java gui-check --profile packaged
```

`gui-package` gibt den genauen Pfad zur App unter
`target/jenesis-jvm/gui/<run-id>/macos-arm64/` aus. Dieser Befehl startet
kein Fenster. Er verpackt das für die GUI erzeugte jlink-Image und
schreibt das JAR- und Modulinventar neben die App. Die sichtbare
macOS-Bundleversion ist `1`, da jpackage keine führende Null zulässt.
Das Java-Projekt bleibt bei `0.2.0-SNAPSHOT`.

`closure.json` enthält die verwendeten JARs und SHA-256-Digests.
`modules.stdout` und `mac-modules.stdout` halten den tatsächlichen
Modulbestand fest. Die Anwendungsclosure enthält die GUI, Modell,
Compiler, Workspace und Repository sowie FlatLaf 3.7 und ANTLR 4.13.2.
Die Pins stehen in der [Modul-POM](../../../interlis-compiler-gui/pom.xml).
Jenesis ergänzt für das gelinkte Image den expliziten ANTLR-Descriptor.
Die App enthält unter `Contents/Licenses` die INTERLIS-, ANTLR- und
FlatLaf-Lizenztexte. Die JDK-Lizenzen liegen in der Runtime unter
`Contents/runtime/Contents/Home/legal`.

Der Pakettest startet zuerst eine unveränderte Kopie über den nativen
Launcher aus einem Verzeichnis mit Leerzeichen ausserhalb des
Checkouts. Er prüft den sichtbaren Prozess, `java.home`, die geladene
FlatLaf-ARM64-Bibliothek und reguläres Beenden über macOS. Anschliessend
verwendet er in derselben Kopie eine testseitige Launcher-Konfiguration
und einen Swing-Treiber. Der Treiber ruft den regulären GUI-Einstieg
auf, bedient alle drei Tabs und schliesst das Fenster. Der native
Launcher und das eingebettete Modulimage sind bytegleich mit der
erzeugten App. Prozessbelege und Aufnahmen bleiben im Run-Verzeichnis.

jpackage erzeugt die App mit lokaler Ad-hoc-Signatur. Eine
Developer-ID-Signierung, Notarisierung und ein Gatekeeper-Test nach
Download gehören nicht zur Abnahme. Die native Auslieferung wurde
für macOS ARM64 geprüft. Für Intel-Macs, Windows und Linux liegt
kein Paketnachweis vor.
