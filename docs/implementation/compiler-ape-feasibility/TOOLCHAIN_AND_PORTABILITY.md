# Toolchain und Portabilität — konkret zu erprobender Weg

## 1. Welche Technik gemeint ist

Ziel ist kein Shellskript mit angehängter JAR, kein JVM-Downloader, kein `jpackage`-Verzeichnis und kein einfach umbenanntes Linux-Native-Image. Der untersuchte Weg ist:

1. Bestehende Java25-Produkt-JARs durch Jenesis herstellen.
2. Mit einem Cosmopolitan-fähigen GraalVM Native Image zwei native Zielarchitekturen bauen.
3. Die geeigneten Zwischenbilder mit dem dazugehörenden `fixupobj` und `apelink` zusammenführen.
4. Die **fertige kombinierte Datei** auf den zugesagten Betriebssystemen prüfen.

Nicht jede Standard-GraalVM unterstützt diesen Weg. Als konkrete Referenz dient der gelesene Madura-Stand **`88cd910650f2e0db4968c0ad6c38089a8ef6b78d`**. Dieser enthält einen CosmoGVM-/cosmocc-Build von Elide, native x86-64-/aarch64-Erzeugung und einen expliziten Fusionierungsschritt.

Das ist ein technischer Ausgangspunkt, kein Zertifikat für INTERLIS und keine Garantie für die Funktionsgleichheit aller Java-Bibliotheken.

## 2. Aus Primärquellen gelesene Ausgangspins

| Teil | Referenzwert |
|---|---|
| Madura-Referenzcommit | `88cd910650f2e0db4968c0ad6c38089a8ef6b78d` |
| GraalVM-Kennung im Setup | `25.2.4-dev` |
| Builderarchiv | `https://static.elideusercontent.com/cosmogvm/25.2i-r2/graalvm-ce-25.2.4-dev-linux-x86_64.tar.gz` |
| Builderarchiv SHA-256 | `c420fdfe510baa353290267e53821edf23f6e47127587b7ff0ee6ff7c288f704` |
| Cosmopolitan-Werkzeugarchiv | `https://static.elideusercontent.com/cosmogvm/25.2i-r2/cosmocc.zip` |
| Werkzeugarchiv SHA-256 | `7b3c11802791037aa5d7d1c02c9f54b21b25ba6369e1f1cc222aca9698641a97` |

Quelle: `scripts/setup-graalvm.sh` und `scripts/setup-cosmocc.sh` am obigen Commit, verlinkt im README.

**Prüfgrenze:** Diese Werte wurden aus den versionierten Skripten gelesen. Die Archive wurden bei der Erstellung dieser Spezifikation nicht heruntergeladen oder ausgeführt. Codex muss Download, Hash, Archivinhalt, Version und tatsächliche Cosmo-Fähigkeit prüfen. Ein Archivhash schützt vor unbemerktem Bytewechsel, ersetzt aber keinen Nachweis der Vertrauenswürdigkeit des Herstellers oder eine Signaturprüfung.

Eine auf der URL später ausgetauschte Datei darf nicht durch Neuberechnung des erwarteten Hashs einfach akzeptiert werden. Abweichung dokumentieren und anhand nachvollziehbarer Vendorquellen eine neue feste Version auswählen oder `INCONCLUSIVE` melden. Keine automatische Ausweich-URL aus unbekannter Quelle.

## 3. Zwei JDK-Rollen, keine globale Umschaltung

| Rolle | Werkzeug |
|---|---|
| Jenesis, reguläre Java-Kompilierung, JVM-Referenz | Die tatsächlich gepinnte Java25-Installation aus Auftrag A |
| Native Maschinenkodegenerierung | Separater verifizierter CosmoGVM-Builder |
| C-/Libc-/APE-Werkzeuge | Dazu passende verifizierte cosmocc-/Loader-/CAP-Artefakte |

Absolute Programmpfade und Kindprozessumgebungen verwenden. `JAVA_HOME` des Anwenders nicht global verändern. Keine Vermischung mit der vorhandenen GraalVM-Web-Image-Installation. Eine `25.0.x`- oder andere lokal vorhandene Installation ist nicht automatisch ein gleichwertiger APE-Builder.

Madura verwendet zusätzlich `jlink`, `lib/modules` und `ct.sym`, weil sein Produkt **javac** enthält. Unser Produkt kompiliert INTERLIS, nicht Java. Diese javac-spezifischen Runtimebestandteile werden **nicht vorsorglich mitgeliefert**. Falls unser Prototyp solche Daten verlangt, die tatsächliche Ursache untersuchen statt den JDK-Baum einzupacken.

Ebenso werden Elide, Kotlin, Bun, mise und Madura selbst nicht zu neuen Produktbuildabhängigkeiten. Wir verwenden die geeignete native Werkzeugkette und den nachgewiesenen Aufrufvertrag, nicht Maduras kompletten Projektbuild.

## 4. Builderumgebung

Die gelesene Vendorinstallation ist ein **Linux-x86-64-Builder**. Auf einem Mac arm64 ist sie nicht einfach ein nativer macOS-Befehl.

Priorität:

1. Bereits vorhandene geeignete Linux-x64-Umgebung benutzen.
2. Einen bereits verfügbaren lokalen Container-/VM-Betrieb verwenden, gegebenenfalls emuliert und klar gekennzeichnet.
3. Bereits autorisierte Projekt-Runner nutzen, soweit tatsächlich zugänglich.

Kein neues Cloudkonto, kein automatisches Buchen von Hardware und kein unautorisierter Push, um einen Workflow auszulösen. Ein isolierter `workflow_dispatch`-Workflow darf als optionales Folge-/Reproduktionswerkzeug vorbereitet werden, aber ohne automatische Release-/Publish- oder Pushtrigger. Fehlende Ausführungsrechte werden als fehlende Umgebung ausgewiesen.

Ein Containerbild muss mit tatsächlichem Digest oder vollständig nachvollziehbarem Buildrezept gebunden werden. Keine erfundenen `ghcr.io`-Images, keine beweglichen `latest`-Tags. Builder-Ein-/Ausgabemounts begrenzen; kein Host-Docker-Socket innerhalb des Builders, keine Host-SSH-Schlüssel oder API-Credentials.

Auf Apple Silicon emuliertes Linux-x64 kann einen realen Build ermöglichen, beweist aber weder native Linux-x64-Ausführung noch macOS-Portabilität. Native Ausführungsnachweise sind eigene Schritte.

## 5. Native-Image-Aufruf: kleine belegte Konfiguration

Aus Madura sind folgende Cosmo-spezifische Bausteine erkennbar; **vor Verwendung mit der konkreten Version prüfen**:

```text
--libc=cosmo
-H:TargetPlatform=linux-amd64        # x86-64
-H:TargetPlatform=linux-aarch64      # ARM64
-H:CLibraryPath=<graal-libs>/<arch>,<labsjdk-libs>/<arch>
-H:+UseCAPCache
-H:CAPCacheDir=<graal-caps>/<arch>
```

Die Verzeichnisbezeichnungen im Referenzprojekt verwenden teilweise `x86_64`/`aarch64`, die Plattformoption teilweise `amd64`. Das muss explizit abgebildet werden, nicht über unkontrollierte Stringersetzung.

Ein Buildplan muss darüber hinaus nachweisen:

- Mainklasse `guru.interlis.cli.Main`;
- vollständigen geordneten Classpath der Jenesis-Compilerclosure;
- Ressourcen-/Reflectionkonfiguration mit Begründung;
- kein JVM-Fallback (`--no-fallback` oder ein tatsächlich belegtes gleichwertiges Verhalten);
- CPU-Kompatibilitätsziel statt unabsichtlichem `-march=native`;
- nur tatsächlich unterstützte GC-/Initialisierungs-/Optimierungsoptionen;
- kontrollierte Trustbasis und keine privaten Builderzustände.

Maduras umfangreiche Flags **nicht blind kopieren**: insbesondere javac-Ressourcenbündel, JRT-Filesystemoptionen, pauschale Filesystem-Buildzeitinitialisierung, deaktiviertes Runtime-Optionsparsing und deaktivierte Toolchainprüfungen sind nicht ohne Nachweis INTERLIS-Anforderungen. Ein notwendiger Vendorworkaround ist zu dokumentieren und gezielt gegenzutesten. Sicherheitsprüfungen nicht beliebig abstellen, um einen grünen Build zu erhalten.

Für den ersten Prototyp kein PGO, kein aufwendiges Optimierungstuning und keine neue Garbage-Collector-Auswahlstudie. Eine sichere unterstützte Basiskonfiguration genügt.

## 6. Echte Fusionierung

Der referenzierte Madura-Workflow führt sinngemäss aus:

```sh
"$COSMO_HOME/bin/fixupobj" compiler.x86_64
"$COSMO_HOME/bin/fixupobj" compiler.aarch64
"$COSMO_HOME/bin/apelink" \
  -l "$COSMO_HOME/bin/ape-x86_64.elf" \
  -l "$COSMO_HOME/bin/ape-aarch64.elf" \
  -M "$COSMO_HOME/bin/ape-m1.c" \
  -o interlis-compiler.com \
  compiler.x86_64 compiler.aarch64
```

Diese Zeilen sind eine **referenzbasierte Skizze**, noch kein ausgeführter INTERLIS-Build. Codex muss prüfen, dass die tatsächlich erzeugten Zwischenbilder die erwartete Form besitzen und die gepinnten Werkzeuge diese Optionen unterstützen.

`fixupobj` darf nur Arbeitskopien verändern. Dokumentiere rohe und bearbeitete Zwischenbildhashes, beide Architekturkonfigurationen, Loaderdateihashes und den finalen APE-Hash. Eine fehlerhafte Fusion mit zufällig weiterhin laufender Hosthälfte darf nicht als Universalität durchgehen.

`file`, Headeranalyse oder eine Zeichenkette mit „APE“ sind ergänzende Strukturbelege. Entscheidend bleibt die tatsächliche Ausführung beider Architekturen auf den realen Zielsystemen. Die APE-Datei nicht nach dem Test strippen, signieren, patchen oder durch einen anderen Packschritt austauschen.

## 7. Portabilitätsmatrix und Startarten

| Ziel | Pflicht für positives plattformübergreifendes Ergebnis | Nachweis |
|---|---|---|
| Linux x64 | Ja | Echter x64-Linux-Prozess, kein ausschliesslicher QEMU-/Rosetta-Nachweis |
| Windows x64 | Ja | Echter Windows-Prozess; POSIX-Linuxlauf in WSL ist kein Windowsnachweis |
| macOS arm64 | Ja | ARM64-Prozess auf macOS; x64-Rosetta-Modus ist separat auszuweisen |
| Linux arm64 / macOS x64 | Optional | Nur bei tatsächlicher Ausführung als bestätigt nennen |

Pro Pflichtziel wird **dieselbe finale Datei** geprüft, nicht das jeweilige Zwischenbild. Vor und nach Ausführung SHA-256 messen. Wenn ein Loader die Originaldatei verändert, das als wesentliches Ergebnis behandeln; nicht still den ursprünglichen Hash weiterverwenden. Private Cacheextraktion darf das gelieferte Original nicht austauschen.

Je Ziel erfassen:

1. **Normaler Terminalstart:** normale verfügbare Shell bzw. Windows-Startumgebung, mit tatsächlichen Argumenten und Exitcode.
2. **Kindprozessstart ohne Shellinterpolation:** z.B. Node `spawn`/`execFile` mit `shell:false`; absolute und relative Modellpfade, Streams und Beenden.
3. **Expliziter Loaderweg, nur falls erforderlich:** mitgelieferter Loader und exakt dokumentierter Aufruf. Separat von einem erfolgreichen Direktstart ausweisen.
4. **JVM-freie, buildbaumfreie Ausführung:** die Anwendung kann keine Builderinstallation, JARs oder unbemerkte Javafallbacks erreichen.

Ein bloss funktionierendes `./interlis-compiler.com` in einer speziell vorbereiteten Entwicklershell genügt nicht. Manche Shells/Loader können ENOEXEC unterschiedlich behandeln. Kein systemweites `binfmt_misc`, `/usr/bin/ape`, Xcode/Command Line Tools oder externer C-Compiler als ungenannte Laufzeitvoraussetzung.

macOS-ARM-Loader können besondere Bootstrapbedingungen besitzen. Im ersten Start aus einer frischen privaten Umgebung prüfen, ob `cc`/`clang`, vorhandene Loadercaches, Downloads oder besondere Privilegien benötigt werden. Erforderlicher Cache-/Temppfad und Verhalten bei nicht beschreibbarem Tempverzeichnis erfassen. Den globalen Anwendercache nicht löschen, sondern eigene HOME-/Temp-/Cachebereiche verwenden.

Keine Abschaltung von Gatekeeper, SIP, Defender oder Zertifikatsprüfung. Signierung, Notarisierung und Downloadquarantäne sind im Bericht als spätere Releasefragen abzugrenzen; eine umgangene Schutzsperre ist kein normaler erfolgreicher Zielstart.

## 8. Runtimebeigaben und Hermetik

Vorrangiges Ziel: eine ausführbare Datei plus Dokumentation/Notices, keine erforderlichen JARs und keine JVM. Zusätzliche Runtimebeigaben müssen funktional notwendig, reproduzierbar benannt und in `bundle-manifest.json` enthalten sein.

Nicht verwechseln:

- normal vorhandene Betriebssystembibliotheken sind keine mitgelieferte JVM;
- ein Testtreiber mit Node/JDK ist nicht automatisch eine Compilerabhängigkeit;
- `JAVA_HOME` zu löschen beweist nicht, dass der Compiler kein absolutes Builder-JDK liest;
- ein Container mit installiertem JDK ist keine JVM-freie Gegenprobe;
- ein separat gehashtes Testtruststore ist eine Testvoraussetzung, nicht automatisch eine Produktionsbeigabe.

Bevorzugter Nachweis: Candidate-/Fixturekopie in einer frischen VM/Containerumgebung ohne JDK, Quellbaum und Builder. Alternativ eine wirksame OS-/Prozesssandbox mit gesperrtem Zugriff und nachvollziehbaren Zugriffsdaten. Nur PATH bereinigen ist höchstens eine zusätzliche Probe. Der Kandidat darf beim Start keine Runtime herunterladen.

Bei TLS das final eingebettete/mitgelieferte Trustmaterial offen dokumentieren. Die Erneuerung dieses Materials ist eine spätere Wartungsaufgabe; „native“ bedeutet nicht wartungsfreie Zertifikate.

## 9. Wartbarkeit, Lizenzen und Betriebsgrenzen

Ein gepinntes unverändertes Vendorpaket ist im Scope. Ein von uns dauerhaft gepflegter Toolchainfork ist es nicht. Der Referenzbuilder trägt eine Dev-Versionskennung; das ist im Ergebnis als Wartungs-/Stabilitätsrisiko zu nennen, nicht hinter dem Wort „GraalVM“ zu verbergen.

Erfasse die tatsächlich verwendeten OpenJDK-/GraalVM-/Cosmopolitan-/ANTLR-/Produktbestandteile und ihre vorhandenen Lizenz-/Noticequellen. Builder und eingebettete Runtimebestandteile unterscheiden. Keine pauschale Übernahme von Maduras Lizenztext, weil dessen Produkt zusätzlich javac/Kotlin enthält. Keine neuen rechtlichen Zusagen ohne konkrete Basis.

Grösse, Builderzeit/RAM und Start-/Kompiliermessungen sind beobachtete Werte. Keine Behauptung, APE müsse kleiner oder schneller als die JVM-Distribution sein. Ein unerwartet grosses Ergebnis ist ein Entscheidungskriterium, kein Anlass, notwendige Funktionen aus dem Prototyp zu entfernen.
