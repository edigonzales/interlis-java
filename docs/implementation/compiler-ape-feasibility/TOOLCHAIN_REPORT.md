# F1 — tatsächliche Werkzeugprobe

CosmoGVM25.2.4-dev / Runtime25.0.4+7-jvmci-25.2-b20 und cosmocc25.2i-r2
wurden aus den beiden Referenz-URLs geladen. Tatsächliche Archive:
556538140 und490089621Bytes. Beide SHA-256 entsprechen exakt den
Referenzpins in `build/interlis/ape/toolchain.lock.json`. Keine Vendoränderung,
keine globale Installation, kein Fork. Archive und vollständiges entpacktes
Datei-/Symlinkinventar sind gehasht. Der zusätzliche Inventarpin
`cc819e6dbbac1c4b4dd579d448c2cdd2d3a19c784a96e2006b13039be14a125f`
bindet auch Bibliotheken/CAP-Caches; fehlende/zusätzliche Dateien werden geprüft.

Die ursprüngliche Lockdatei (Hash
`5a714516500af52c664815f2899f73cc3de5d50676874c5e0409542d409de59b`)
ist extern erhalten. Die ergänzte Lockdatei hat Hash
`c6f7cc137aa25b1e43591152408e10579d179ad2dc6bedb57d477fdb57e34df6`.
Archiv-, Werkzeug- und Bibliotheksbytes sind identisch; nur die zusätzliche
Validatorbindung ändert sich. Native Probeläufe wurden deshalb nicht wiederholt.
Die erneute isolierte Installation bestätigt denselben vollständigen Dateibaum.

Builder: vorhandenes Dockerimage
`sha256:063b17654e59a88128e193fcc8cc4a094d37921b20bbc07eed35aa0e01004aff`,
Linux/amd64 emuliert auf Docker Desktop aarch64. Vier CPUs, Containerlimit7GiB,
Probeheap3GiB, zwei Builderthreads, maximal15Minuten je Nativeprozess.
Netzwerk deaktiviert; Vendorinput read-only; nur privater Run und Temp beschreibbar.
Keine Hostcredentials oder Dockersocket im Builder.

## Beobachtete Fehler und Kontrollen

`probe-4k5SJU`: beide Architekturversuche scheitern an `Permission denied`
für den privat extrahierten APE-Loader im standardmässig noexec gemounteten Temp.
Keine Toolchainprüfung deaktiviert. Gezielte Korrektur: privater tmpfs mit exec.
Separate C-Kontrollen erzeugen x86-64- und ARM64-Objekte erfolgreich. Ein erster
C-Kontrollaufruf ohne HOME-Override scheitert am read-only Image-HOME; der nächste
benutzt dasselbe private HOME wie die Nativeprobe. Originalfehler bleiben erhalten.
Ein gleichzeitiger Installationsversuch während des Builds wird vom APE-Lock
ordnungsgemäss zurückgewiesen; nach Prozessabschluss ist Installation erfolgreich.

`probe-pbnFgk`: Vendor-Java kompiliert die separate Java 25-Probe; JVM-Kontrolle
und beide Cosmo-Native-Builds erfolgreich. Record/Sealed/Patternswitch, UTF-8-
Ressource, Unicode-Datei, echter Thread und kontrollierte Ausnahme sind enthalten.
Native-Image `--expert-options-all` bestätigt die verwendeten Cosmo-/CAP-Optionen.
Keine pauschale Buildzeitinitialisierung oder Anwendungsmetadaten auf Verdacht.
`--no-fallback` wird von diesem Vendorstand als wirkungslos veraltet gemeldet;
die echten Nativeprogramme laufen ohne JDK-/Vendormount. Compilerbuilds verwenden
diese überflüssige Option nicht. `CheckToolchain` bleibt aktiv.

| Zwischenbild | SHA-256 | Buildbeobachtung |
|---|---|---|
| x86-64 | f962132a07f98bdfff6b9909394ce6b4c556f7a1b125b7895556c99706b3b1ab | 5:45min, PeakRSS1.16GiB, 9.41MiB |
| ARM64 | 72d9da6bb30e26a4e440dceba8535cba1e2b40f363168396b8262f781f0eb522 | 5:50min, PeakRSS1.10GiB, 8.94MiB |

`fixupobj` bearbeitet Kopien; Originalhashes bleiben gleich. `apelink` verwendet
beide gepinnten ELF-Loader und `ape-m1.c`. Kombinierte **Probe**, kein Compiler:
`a30b99235db3ed7e22b066a1130952f639f57a50e90b08219dc6f952d0b3bb3a`.
Sie besteht den Shelllauf unter emuliertem Linux x64 und auf echtem macOS arm64.
Der direkte macOS-Kindprozessstart scheitert mit `spawn Unknown system error -8`
(ENOEXEC). Der Shellweg mit frischem privaten HOME/Temp startet erfolgreich.
Das Bootstrapverhalten und dessen Compiler-/Loaderbedarf sind noch keine
Hermetik- oder Ein-Datei-Zusage für den späteren Compiler.

Gate: `target/compiler-ape/fused-probe-KSbjMS/gate.json`, extern zu archivieren.
Die x64-Zwischenprobe wurde zusätzlich ohne Vendor-/JDK-Mount direkt ausgeführt
(`f1/x64-runtime/`); Original- und Laufkopiehash bleiben gleich. Das ist ein echter
Maschinencodelauf unter Emulation, kein nativer Linux-Plattformnachweis.

Lizenzquellen sind in den unveränderten Archiven enthalten: GraalVM LICENSE.txt,
LICENSE_NATIVEIMAGE.txt, THIRD_PARTY_LICENSE.txt und legal/-Baum; cosmocc
README.md und GPL/LGPL-Lizenzdateien sowie eingebettete Runtimehinweise.
Builder- und Runtimeanteile sind für eine Lieferung getrennt zu inventarisieren;
keine pauschale rechtliche Freigabe wird abgeleitet. Die Dev-Version bleibt ein
Wartungsrisiko. F1 prüft weder Compilersemantik noch TLS-/CLI-Portabilität.
