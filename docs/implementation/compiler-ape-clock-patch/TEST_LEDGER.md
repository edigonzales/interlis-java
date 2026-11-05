# Gezielte Prüfungen

Originalbelegroot `/Users/stefan/sources/interlis-java-clock-patch-evidence`.
Keine alten Machbarkeits-/Reviewdateien umgeschrieben.

| Frage | Kommando / Umfang | Ergebnis / Originalbeleg |
|---|---|---|
| C0, exakter Ausgangsstand | git status/HEAD/tree und Quelldiff seit Referenz | sauber, nur neue Spezifikation; `c0/baseline.json` |
| Vendor unverändert | bestehendes verifiedInstallation plus beide verifyArchive | vollständige Pins stimmen; `c0/baseline.json`, `expected-target.json` |
| Methode/Annotation/Option | javap -p -c -v von System-Cosmo, Guard, Prozessor, Helpers; native-image --expert-options-all | eindeutiges Ziel, CLOCK_MONOTONIC, shared.Uninterruptible, onlyWith CosmoLibCSupplier; methodengenaue Disable-Auswahl vorhanden; `c0/` |
| Originaldefekt wiederverwenden | originale Rawreceipts, Binary-/Quellhash geprüft | JVM0, nativ1 mit Clockdiskrepanz; `c0/original-reuse.json` |
| Jenesisinputs aktuell | Java25 ape verify --inputs ursprüngliches0f3-Manifest |0; `c0/input-verification.*` |
| Technischer Reviewpreflight | vorhandenes OpenCode2.0.28, neue direkte Flash-Sitzung ohne Tools/Review |0, TECHNICAL_PREFLIGHT_OK; `c0/preflight/`, offizielle Mappingseite neu gespeichert |
| C1 Inspector | javac25 + strukturelle Class-File API, vollständiger Vendorpin | erster Inspectorfall lehnt noch nicht behandelte POP-Instruktion ab; StackInstruction ergänzt; danach0; `c1/inspect-vendor*` |
| C1 Zusatzklasse | Vendorjavac mit unveränderten Builder-APIs | --release25 blendet Graal-Systemmodule aus, Modulfehler2; konkrete Korrektur zu source/target25, kein Routenausstieg; `c1/compile-a-discovery*` |

Native Runtime-, TLS-, Compiler-, Plattform- und Gesamt-Reviewgates sind noch
nicht bestanden. C0-Output ist eine Baseline, kein Patch-PASS.

C1: erster Nativeversuch scheitert an konkretem Modulzugriff auf Cosmo-Header;
zwei eng begrenzte Paketexporte für die Gegenprobe ergänzt. Zweiter Nativebau
auf Nutzerwunsch abgebrochen, eigener Container erfolgreich entfernt. Kein
korrigiertes Runtime-PASS. Beleg: `c1/user-abort.json`. Der Zwischenstand wird auf Folgeanweisung lokal
committet; dafür keine weiteren Tests, Builds oder Reviews gestartet.
