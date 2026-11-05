# Auftrag B — operativer Stand

Stand: 05.11.2026. **B-I und B-II/B0–B7 lokal implementiert; notwendige Phasengates bestanden.** Kandidatencommits und der anschliessende unabhängige Gesamt-Review sind der nächste Abschlussabschnitt. Noch kein unabhängiges Gesamt-PASS und keine P3-Veröffentlichungsfreigabe.

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
| R | ausstehend | erst nach beiden geprüften lokalen Kandidatencommits; frischer OpenCode2/direct DeepSeek Flash1017, kumulativer Gesamtdiff beider Repositories |

Nachweise liegen clean-sicher unter `/Users/stefan/sources/interlis-java-jenesis-b-evidence/B0` bis `B7`. [TEST_LEDGER](TEST_LEDGER.md) trennt erfolgreiche Läufe, erhaltene Fehlversuche, wiederverwendete Originalbelege und Umfang. [CRITERIA](CRITERIA.md) ordnet JB-01–40 zu; [BASELINE](BASELINE.md), [DECISIONS](DECISIONS.md) und [TASK_DISPOSITION](TASK_DISPOSITION.md) enthalten Quellen-/Entscheidungsbezüge.

Die normalen1317 Invocations werden über die drei Modulbatches und den finalen CLI-Modulnamenslauf abgedeckt. `B7/final-normal-reuse.json` prüft unveränderte Produkt-/Runtime-JARbytes und Test-/Produktquellen; Originalreports bleiben unverändert. Frische Verträge: `contract-750f4f31-634e-4b41-846a-30a345d4465e`. Die zwei separaten sauberen Produktgenerationen werden nach dem rein prüfenden module-check-Adapterfix zusätzlich über tatsächliche finale Mavenartefakthashes gebunden; kein doppelter Normaltestlauf.

Vollständige Korpus-, Benchmark-, Browser-/Safari- und P3-Releasekampagnen sind für die hier unveränderten Fach-/Performanceverträge NOT_REQUIRED_FOR_B. Die vorhandenen Pflichtprofile und fehlgeschlossenen Releasevalidatoren bleiben erreichbar. Reale B-Mindestintegration wurde nicht ersetzt: TeaVM JS/WasmGC und Web Image liefen mit den neuen modularen Bytes; der Editor lief aus dem echten gebündelten VSIX. Windows/Linux und Remote-/Container-/SSH-Hosts wurden lokal nicht ausgeführt. Workflowcode ist keine CI-Abnahme; die vollständigen Releaseplattformgates bleiben offen.

Compiler-APE und CosmoGVM-Uhrpatch bleiben pausiert. Kein Push, Tag, Merge, Release, Installer, Signierung oder persönliches Extensionprofil. Finales Kandidatenpaar und Reviewurteil werden extern unter `reviews/` gebunden, um keine Self-SHA-Commitkette zu erzeugen.
