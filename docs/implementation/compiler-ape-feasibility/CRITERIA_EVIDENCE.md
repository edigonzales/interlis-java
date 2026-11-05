# Zuordnung der 32 Untersuchungskriterien

Dies ist ein Wegweiser zu Originalbelegen, kein eigener Review und kein
Produkt-PASS. Belegpfade sind relativ zu
`/Users/stefan/sources/interlis-java-ape-evidence`. Die fachliche Entscheidung
und offene Produktfälle stehen in FEASIBILITY_REPORT.md.

| Kriterien | Umsetzung / Beobachtung | Originalnachweis |
|---|---|---|
| AP-01/06/20/22 | Nur optionaler Compileradapter; keine Produktsemantik, globalen Installationen oder Forks; normaler Jenesis-/Gradleweg unverändert | kumulativer Git-Diff, `f5/entry-input-gate/`, Lock und tatsächliche argv |
| AP-02/03/04 | sieben geordnete Jenesis-JARs, Sourcebindung, echte JVM-Referenz, Drift-/Leak-/Manipulationsablehnung | `f0/gate-03/`, `f2/jvm-input-gate/`, `f2/source-binding/`, `f5/jar-parity.json`, `f5/entry-input-gate/` |
| AP-05/07 | geprüfte Archive, vollständiger extrahierter Inventarpin, Java 25-Probe für beide Architekturen vor Compilerbau | `toolchains/`, `f1/`, TOOLCHAIN_REPORT.md |
| AP-08 | zwei echte Compilerbilder, fixup-Kopien, echte Fusion; keine umbenannte Einzelarchitekturdatei | `f2/x86_64-drehEN/`, `f2/aarch64-Herc6v/`, `f2/fused-SU7MpB/` |
| AP-09/10 | keine spekulativen Registrierungen; lokale Funktionen erfolgreich, HTTP scheitert vor Request; lokale Builderzugriffssperre tatsächlich wirksam | `f2/core-fused-mac-01/`, `f3/cli-host-02/`, `f3/http-probe-run/`, `f3/clock-probe-run/` |
| AP-11/12/13 | K01–09/F01–06 exakt gegen JVM, grosse Dezimalzahl/Unicode/Diagnosefolge erhalten; Pfade/Exit/Streams echt | `f2/core-fused-mac-01/`, `f3/cli-host-01/`, `f3/cli-host-02/` |
| AP-14/15 | EPoll konfigurierbar, Clock-/TLS-Vendorblocker minimiert; native TLS-/Cache-/Proxy-/Timeoutfähigkeit nicht durch pauschale Fehlerablehnung vorgetäuscht | `f3/network-01/`, `f3/http-probe-tls-debug-02/`, `f3/clock-probe-run/`, terminale Disposition im Machbarkeitsbericht |
| AP-16/18 | deklarierte Vendorloader; Mac-Direktstart und Shellbootstrap getrennt; lokaler Compiler mit ausgeliefertem Loader ohne JDK/SDK/Quellzugriff | `f1/fused-probe-KSbjMS/`, `f3/cli-host-02/H03-*`, Bundlemanifest |
| AP-17 | echter Mac arm64; Linux x64 emuliert, Windows/natives Linux fehlen; keine erfundenen Zielreports | BASELINE.md, Hostdaten in Rohreceipts, Plattformtabelle im Machbarkeitsbericht |
| AP-19 | vollständige Case-/Stream-/Source-/Hashprüfung; keine Annahme von fehlenden oder veränderten Ergebnissen | `f2/slice-mutations/`, `f5/validators.*`, `f5/report-mutations/` |
| AP-21 | `target/compiler-ape/` isoliert, externe Belege, bestehender Root-Clean-Guard schützt gesamtes build/interlis; kein Clean des Rootbuildbaums | unverändertes build.gradle:7, Git-Diff, H03-Relokation, TEST_LEDGER.md |
| AP-23 | gezielte Phasengates und Kontrollen; keine Korpus-/Benchmark-/Browserdoppelung; gleiche JAR-/Binarybytes geprüft | TEST_LEDGER.md, Originalreceipts und `f5/jar-parity.json` |
| AP-24 | echte Bytegrössen, Nativebuildzeit und Ressourcenverbrauch; keine Beschleunigungs-/Produktionsbehauptung | native stdout/receipts, Bundle-/Transportmanifest |
| AP-25/32 |264 Notice-Dateien und zwei Loader inventarisiert, reales Archiv mit unveränderten Bytes/Modi geprüft | Bundlemanifest, `f3/transport/`, `f5/validators.*` |
| AP-26/28 | technische Ursache und fehlende Plattformumgebung getrennt; terminale Produktcases bleiben offen | FEASIBILITY_REPORT.md, Diagnose-Originale |
| AP-27 | explizite ape-Befehle, echte Fehlerpfade, Report führt keine Tests aus | tools/ape/README.md, `f5/report.*`, EntryChecks |
| AP-29/30/31 | erst geprüfter Commit, danach frische direkte DeepSeek/OpenCode-Sitzung; Findings-Schleife falls nötig; finale Belege extern | erst nach Commit vorhandenes externes Reviewverzeichnis; kein vorweggenommenes PASS |
