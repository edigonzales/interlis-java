# Auftrag B — operativer Stand

Stand: 05.11.2026. **B-I und B-II/B0–B7 lokal implementiert; notwendige Phasengates bestanden.** Das erste Kandidatenpaar ist lokal committet und unabhängig reviewt (FAIL mit F-01–06); gezielte Korrekturen sind implementiert. Neue Kandidatenbindung und frischer unabhängiger Gesamt-Review folgen. Noch kein unabhängiges Gesamt-PASS und keine P3-Veröffentlichungsfreigabe.

| Phase | Status | Tatsächlicher Nachweis |
|---|---|---|
| B0 | bestanden | gepinnte native POM-/JPMS-/JUnit-Hookproben, Fehler/Empty/Parameter, Baselineaudit |
| B1 | bestanden | normalisierte echte Engineergebnisse, ModuleArtifacts, SDK-/CLI-Prozesse, frische Verträge |
| B2 | bestanden | immutable Snapshot/Mavenclosure, Cache-/Mutationsgegenfälle, tatsächlicher JS-/WasmGC-/Web-Image-Codegen und Nodefälle |
| B3 | bestanden | echter Jenesis-Editorproduzent, reiner Import, dev:prepare und isolierter VSCode-Host |
| B4 | bestanden | zehn JVM-Gradleprojekte entfernt, zwei Backendprojekte, frische JVM ohne fremde Builder/Node und Lean-Backend, Clean/Host/Release-Gegenfälle |
| B5 | bestanden | zehn native JPMS-Produkte, drei Modulbatches, logische Normalabdeckung1317, benannter API-/Classpathconsumer und Kapselungs-/Patchgegenfälle |
| B6 | bestanden | modulare CLI/LSP/SDK-Prozesse, gebündeltes jlink-VSIX mit echtem HTTP/HTTPS-LSP und VSCode-Restart; Compilerimage/SDK/HTTP/HTTPS und lokales app-image |
| B7 | bestanden | modulare Backends und frische272+4 Verträge, zwei saubere JVM-/SDK-Erzeugungen32 Lieferartefakte bytegleich, Originalreportadapter1593 Fälle/398 logische Suiten/139 Rohberichte mit Gegenprobe, Quellen-/Altlastenprüfung |
| R | Korrekturrunde | Runde1 FAIL mit sechs Findings; ursprünglicher Review und Quellen unverändert. Portable Layouts, SDK-Bytes, Evidenzzeiger, Altlasten, Deskriptorguards und Consumerlease korrigiert; neue Kandidaten und frischer kumulativer Gesamt-Review erforderlich |

Nachweise liegen clean-sicher unter `/Users/stefan/sources/interlis-java-jenesis-b-evidence/B0` bis `B7`. [TEST_LEDGER](TEST_LEDGER.md) trennt erfolgreiche Läufe, erhaltene Fehlversuche, wiederverwendete Originalbelege und Umfang. [CRITERIA](CRITERIA.md) ordnet JB-01–40 zu; [BASELINE](BASELINE.md), [DECISIONS](DECISIONS.md) und [TASK_DISPOSITION](TASK_DISPOSITION.md) enthalten Quellen-/Entscheidungsbezüge.

Die normalen1318 Invocations werden über die drei Modulbatches, den finalen CLI-Modulnamenslauf und die gezielten Verifikationskorrekturen abgedeckt. `B7/final-normal-reuse.json` und `B7/corrections-round01` prüft unveränderte Produkt-/Runtime-JARbytes und Test-/Produktquellen; Originalreports bleiben unverändert. Frische Verträge: `contract-750f4f31-634e-4b41-846a-30a345d4465e`. Nach dem Reviewfix wurden zusätzlich zwei neue separate saubere JVM-/SDK-Erzeugungen ohne Tests byteverglichen (`B7/corrections-round01/repro-result.json`); kein doppelter Normaltestlauf.

Vollständige Korpus-, Benchmark-, Browser-/Safari- und P3-Releasekampagnen sind für die hier unveränderten Fach-/Performanceverträge NOT_REQUIRED_FOR_B. Die vorhandenen Pflichtprofile und fehlgeschlossenen Releasevalidatoren bleiben erreichbar. Reale B-Mindestintegration wurde nicht ersetzt: TeaVM JS/WasmGC und Web Image liefen mit den neuen modularen Bytes; der Editor lief aus dem echten gebündelten VSIX. Windows/Linux und Remote-/Container-/SSH-Hosts wurden lokal nicht ausgeführt. Workflowcode ist keine CI-Abnahme; die vollständigen Releaseplattformgates bleiben offen.

Compiler-APE und CosmoGVM-Uhrpatch bleiben pausiert. Kein Push, Tag, Merge, Release, Installer, Signierung oder persönliches Extensionprofil. Finales Kandidatenpaar und Reviewurteil werden extern unter `reviews/` gebunden, um keine Self-SHA-Commitkette zu erzeugen.

Runde2 des Kandidatenpaars bf2b5c3/c96cdd4: unabhängiges PASS für alle40 Kriterien,21 eigene Gegenproben, reguläres Prozessende0, Quellen/Originale unverändert. Zwei nicht blockierende Hinweise O-01/O-02 zu aktiven Doku-Tasknamen und Extension-B-Branchtrigger werden redaktionell bereinigt; danach neue saubere Kandidatenbindung und frische vollständige Runde3. Keine Produkt-/Laufzeitsemantik geändert. Endgültiges Urteil bleibt extern.
