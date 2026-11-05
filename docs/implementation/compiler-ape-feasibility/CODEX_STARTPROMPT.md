# Startprompt — Compiler-APE-Machbarkeit

Du bist **Codex**, der primäre implementierende Agent im lokalen Repository `edigonzales/interlis-java`. Führe den Auftrag praktisch durch. Ein weiterer Plan oder eine Sammlung ungetesteter Buildflags ist kein Abschluss.

## Lesen und Priorität

Lies vollständig `SPECIFICATION.md`, `TOOLCHAIN_AND_PORTABILITY.md`, `EXECUTION_PLAN.md`, `TEST_MATRIX.md`, `TEST_AND_EVIDENCE_POLICY.md` und `OPENCODE_REVIEW_PROMPT.md` im Verzeichnis `docs/implementation/compiler-ape-feasibility/`. Lies geltende `AGENTS.md` und die tatsächliche Umsetzung von Jenesis-Auftrag A.

Dieser Auftrag erlaubt einen **zusätzlichen, optionalen Compiler-APE-Prototyp** auf Basis des vorhandenen Jenesis-JVM-Builds. Er ersetzt insoweit frühere Ausschlüsse nativer Arbeit in Auftrag A. Er aktiviert keine anderen alten Startprompts oder Folgeaufträge.

## Verbindliches Ziel

Baue die bestehende Compiler-CLI aus den tatsächlich von Jenesis erzeugten JARs mit einer gepinnten Cosmopolitan-fähigen GraalVM-Toolchain. Erprobe einen kombinierten x86-64-/ARM64-APE-Prototyp `interlis-compiler.com` und vergleiche ihn mit der JVM-Ausführung derselben Artefakte. Verwende zunächst `guru.interlis.cli.Main` als identischen Einstieg.

Der Prototyp muss mehr als `version` können: echte lokale Modellkompilierung, Fehlerdiagnostik, die vorhandenen CLI-Befehle einschliesslich Formatierung, lokale und HTTPS-Repositories, Cache/Offline, Proxy/Timeouts und kritische Hostpfade sind gezielt zu prüfen. Der Compiler prüft dabei INTERLIS-Modelle; er wird nicht zu einem Transferdatenvalidator oder Java-Compiler.

Die entscheidende Portabilitätsfrage lautet: Läuft **dieselbe kombinierte Datei** mit derselben SHA-256 auf Linux x64, Windows x64 und macOS arm64 ohne vorinstallierte JVM und ohne heimliche Buildumgebungsabhängigkeit? Shellstart, direkter Kindprozessstart und benötigte Loader sind getrennt nachzuweisen. Emulation ist als Buildhilfe zulässig, aber kein nativer Zielplattformnachweis.

## Harte Grenzen

- Keine JPMS-Migration, keine neuen Produkt-`module-info.java`, keine Package-/Sichtbarkeitsänderungen für dieses Ziel.
- Kein nativer LSP, keine Extension-/VSIX-/jlink-Paketierungsänderung.
- Kein TeaVM-/Wasm-/Web-Image-Build. `interlis-wasm-bridge` wird nicht vorsorglich in den APE hineingezogen.
- Keine Verbesserung des Formatierungsstils; der vorhandene `format`-Befehl bleibt funktional unverändert.
- Keine neue Compilersemantik, kein Verwerfen bestehender Parser-/CLI-Verträge.
- Kein GraalVM-/Cosmopolitan-/Jenesis-Fork und keine selbst gepatchten Toolchainbinärdateien. Der dokumentierte `fixupobj`-/`apelink`-Produktionsschritt ist kein solcher Fork.
- Kein Elide-/Bun-/Maven-/Gradle-Zweitbuild für den Compiler. Madura ist Referenz für die native Werkzeugkette, keine einzubauende Anwendung oder neue Buildvoraussetzung.
- Kein JVM-Fallback, keine umbenannte ELF-/PE-Datei als behauptetes APE, keine drei unterschiedlichen Dateien als behauptete Universaldatei.
- Kein automatischer Native-Build bei normalem `build`, `check`, `contracts`, `sdk-check` oder Extensionstart.
- Kein Release, Push, Registryupload, neues Cloudkonto, automatische kostenpflichtige Runnerbeschaffung oder ungefragte globale Installation.

## Ablauf und Tests

Arbeite F0 → F1 → F2 → F3 → F4 → F5. Ein erfolgreiches Phasengate ist Voraussetzung für die nächste implementierende Phase. Triff Routineentscheidungen selbständig.

Prüfe die Toolchain zuerst an einem kleinen separaten Java-Probeprogramm, bevor du viel Native-Konfiguration für den Compiler schreibst. Halte alle Toolpins, Architekturvarianten, Classpaths, Ressourcen und generierten Produkte sauber auseinander.

Nach einem relevanten Fix führe nur die erforderlichen Tests und Verbraucherproben aus. Keine pauschale Wiederholung der gesamten JVM-Suite, des Vollkorpus oder der Browsermatrix. Ein neuer Commit allein macht alte sachlich unveränderte Belege nicht ungültig. Eine tatsächlich geänderte finale APE-Datei benötigt dagegen neue Ausführungsbelege für ihre zugesagten Zielplattformen; alte Binärnachweise dürfen nicht auf sie übertragen werden.

## Begrenzte Machbarkeitsuntersuchung statt endloser Portierung

Ein nachgewiesenes technisches Hindernis ist ein zulässiges Ergebnis dieser Untersuchung. Nach reproduzierbarem Blocker und begrenzter Ursachenanalyse darfst du direkt in den dokumentierten negativen Abschluss F5 wechseln. Das ist **kein** bestandenes Produktgate der ausgelassenen Phasen.

Unterscheide:

- `GO`: die festgelegte positive Machbarkeit ist tatsächlich belegt;
- `GO_WITH_LIMITATIONS`: der nutzbare, vollständig getestete Paketweg hat konkret benannte zulässige Einschränkungen, etwa einen mitgelieferten Loader; keine fehlenden Pflichtplattformtests verbergen;
- `NO_GO`: ein reproduzierbarer technischer/struktureller Blocker widerspricht dem Ziel unter den freigegebenen Grenzen;
- `INCONCLUSIVE`: nötige Umgebung, Toolchain oder Belege fehlen; keine Behauptung technischer Unmöglichkeit.

Der unabhängige Review bewertet zusätzlich die Qualität und Vollständigkeit der Untersuchung. Ein begründet negatives Machbarkeitsergebnis kann ein Review-PASS erhalten. Es darf niemals in „APE erfolgreich umgesetzt“ umgedeutet werden.

## Git und Review

Erhalte fremde Änderungen und alle bestehenden Artefakt-/Reviewnachweise. Kein Reset, ungezieltes Clean oder blindes Staging. Versionierte Quellen liegen auch unter `build/`; den Rootordner niemals als Ganzes löschen. Nutze ausschliesslich abgegrenzte Ausgabe- und Scratchpfade.

Nach allen tatsächlich erforderlichen Phasen und der eigenen Prüfung: Ergebnis lokal committen, Kandidaten-SHA und Tree erfassen. Dann **ein abschliessender unabhängiger Gesamt-Review**, nicht nach jeder einzelnen Phase.

Verwende eine frische **OpenCode-2.x-Sitzung mit DeepSeek Flash 1017 über die direkte DeepSeek-API**. Vorhandene Anmeldung nutzen, tatsächliche CLI-/Modellkennung und Aliaszuordnung prüfen, keine Secrets protokollieren, kein Modellfallback. Reviewer erhält den exakten Commit, den kumulativen Diff, alle Anforderungen und ursprüngliche Rohbelege und führt eigene kleine Gegenproben aus. Er repariert nichts am Produkt.

Bei berechtigten Findings: Codex korrigiert, führt gezielte notwendige Tests aus, committet erneut und startet einen frischen unabhängigen Gesamt-Review. Wiederhole bis zum gültigen PASS oder einem konkret belegten nicht selbst behebbaren Blocker. Finale Reviewbelege extern aufbewahren; nach PASS keinen stillen Nachtragscommit erstellen.

## Abschluss

Berichte Machbarkeitsentscheidung und Reviewurteil getrennt, erreichten Umfang, exakt getestete Plattformen und Startarten, endgültige Artefakthashes, Toolchainpins, Laufzeitbeigaben, durchgeführte/wiederverwendete/offene Prüfungen, finalen Commit und Rohbelegpfade. Keine Produktionsreife, APE-Portabilität, Leistungsverbesserung oder Sicherheitsfreigabe behaupten, die nicht geprüft wurde.

Beginne jetzt mit F0.
