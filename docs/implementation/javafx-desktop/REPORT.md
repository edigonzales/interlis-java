# JavaFX Desktop: Abnahme vom 05.11.2026

**Ergebnis:** Die eigenständige `INTERLIS Compiler.app` wurde für macOS ARM64
gebaut. Sie startet aus einem anderen Verzeichnis mit ihrer eigenen Runtime,
kompiliert lokale Modelle und rendert bzw. exportiert das Ergebnis offline.
Ausgangscommit: `c02c9112f93cd69c113faf52d6267f601433e0fd`; Branch:
`codex/javafx-desktop-prototype`. Der Prototyp ändert keinen gemeinsamen
Compiler-, Projektions- oder Renderer-Vertrag.

| Phase | Beobachtung und Nachweis |
|---|---|
| 1 – Renderer (VIZ-001/004/010/011/014/018, ARCH-008) | `gui-check`, Exit 0: vollständiges ESM-Bundle, echter ELK-Web-Worker, neun Knoten aus vorhandenem Java-DiagramDocument, Zoom, Pan, Selektion, SVG/PNG und Brücke in JavaFX WebView. [WebView-Rohresultat](phase-1-webview.txt). Derselbe Renderer und Testdatensatz: vorhandener Chromium-Komforttest 1/1, Exit 0, ohne Skips. [Kontrolllauf](phase-1-chromium.txt). |
| 2 – Packaging (BUILD-001–004) | Native Jenesis-Schritte `JLink` und `JPackage` erzeugten Runtime und `.app`. Die verschobene App lief unter einem Loopback-Sandboxprofil und minimierter Umgebung mit `PATH=/usr/bin:/bin`. `java.home` zeigte ins Bundle, der Renderer-Nachweis bestand, Exit 0. [Rohresultat](phase-2-packaged.txt). |
| 3 – Compiler (DIAG-002/003/006, VIZ-001/004/018, Workspace-/Closure-Vertrag) | `gui-package`, Exit 0: zwei `.ili`-Dateien über den Dateieingabepfad auf einem Hintergrundthread kompiliert, 0 Diagnosen, gebundener Import `GuiExample → Interactive`. Neue `CHECKED_SCHEMA`-Projektion mit zehn Knoten in echtem Sprotty-SVG. Beide Exporte über die Java-Brücke geschrieben. `Broken.ili` wurde mit `ILI-SEM-0001` bei 5:16 abgelehnt, Problems zeigte die Meldung, das alte Diagramm war entfernt. [Rohresultat](phase-3-packaged.txt), [Build und Hashes](phase-3-build.txt). |

Umgebung: macOS 27.0.1 (26A434) ARM64, Temurin JDK 25.0.2+10, OpenJFX
25.0.2+4 (WebKit 622.1), Jenesis 0.18.1, Node 24.22.0, Sprotty 1.4.0,
elkjs 0.11.0; Kontrollbrowser Chromium 156.0.8078.4 / Playwright 1.64.1.
Bundle-Version `1.0` (numerische macOS-Anforderung); die JVM-Artefaktversion
ist `0.2.0-SNAPSHOT`. Launcher laut `file`: Mach-O arm64. App-Grösse laut
`du -sk`: 179 676 KiB (rund 175 MiB). Das normale Bundle liegt in
`target/jenesis-gui/packaging/package/output/packages/INTERLIS Compiler.app`.
Der abschliessende PNG-Export misst 2095 × 656 Pixel; in `target/jenesis-gui/`
liegen 69 650 Byte PNG und 151 305 Byte SVG.

Der reguläre Jenesis-Artefaktlauf in `gui-package` baute die bestehende JVM-Linie
inklusive Compiler, CLI und LSP. Es wurden keine Compiler-/CLI-/LSP-Testsuiten
oder WASM-Backends erneut ausgeführt; deren Produktquellen sind unverändert.
Alle 41 Rendererdateien im Bundle sind bytegleich zum Build aus dem bestehenden Lockfile.
Der Offlinetest erlaubte nur Loopback; die Gegenprobe mit `sandbox-exec` und
`nc -v -G 1 -z 192.0.2.1 443` endete mit Exit 1 und `Operation not permitted`.
Die App verwendete ihre eingebettete Runtime. Ein separater Rechner ohne
installiertes JDK wurde nicht benötigt oder getestet.

Die Probe belegt Zoom/Pan/Selektion über die echten WebView-Canvas-APIs und
exportiert echte Dateien. Beim zusätzlichen Start via macOS-Launcher war das
native Hauptfenster sichtbar; die UI-Automation brach danach beim Auslesen mit
Timeout ab. Maus-/Tastaturgesten und die nativen Datei-/Speicherdialoge sind
nicht separat als manuell geprüft gewertet. Die automatisierte Abnahme läuft
über denselben Dateieingabe- und Kompilierpfad nach dem Dialog.

WebAssembly und `crypto.subtle` waren in dieser WebView nicht verfügbar. Der
verwendete interaktive Modus routet mit ELK. Der optionale statische
libavoid-Wasm-Router wurde nicht aktiviert; er ist kein Bestandteil des
abgenommenen Routingmodus. Die Prüfung bezieht sich auf macOS ARM64.

Die Vorläufe deckten fehlerhafte Classifier-Pins, eine ungültige
Loopback-Sandboxschreibweise, den fehlenden Workspace-Aufruf für lokale
Importbindungen und eine von macOS abgelehnte App-Version (`0.2.0-SNAPSHOT`,
danach `0.2.0`) auf. Der parallele Start mit bereits geöffneter App zeigte zudem
den Konflikt des gemeinsamen WebView-Datenverzeichnisses. Der abschliessende
Paketlauf mit instanzeigenem Verzeichnis bestand bei weiterhin geöffneter erster
App; er verwendete die unveränderte jlink-Runtime wieder und erzeugte die App
neu. Der erste Chromium-Kontrolllauf startete mangels passender Browserrevision nicht; nach
Installation des gepinnten Chromium-Shells bestand derselbe gezielte Fall.

[Build und Bedienung](README.md). Nächster fachlicher Schritt bei Ausbau:
manuelle Qualifikation der nativen Dialoge und Gesten; danach entscheiden,
ob ausserhalb dieses Prototyps Repositoryauflösung oder weitere Plattformen
benötigt werden.
