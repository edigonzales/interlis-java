# Test- und Evidenzpolicy

## 1. Grundsatz

**Teste die Auswirkung einer Änderung, nicht die Existenz einer neuen Phase, eines neuen Commits oder einer Reviewrunde.** Ein erneuter unabhängiger Gesamt-Review bedeutet keine erneute vollständige Build-/Testkampagne.

Die erste positive Machbarkeit benötigt trotzdem die vollständige kleine vereinbarte Matrix und echte Zielplattformbelege. Diese darf nicht mit dem Hinweis auf „Testökonomie“ verkürzt werden.

## 2. Vier getrennte Identitäten

1. **Quell-/Untersuchungskandidat:** Git-SHA, Tree und Dirty-Zustand der Implementierung.
2. **Produktinput:** tatsächlich verwendete Jenesis-JARs, Closureordnung, native Metadaten, Toolchain und Buildargumente.
3. **Laufzeitartefakt:** endgültige Universal-APE-Bytes mit SHA-256 und vollständigem Runtimeinventar.
4. **Testlauf:** Fixture-/Harness-/Erwartungshashes, echte Hostumgebung, argv, Startmodus, Originallogs und Einzelresultate.

Gleiche Git-SHA beweist nicht gleiche Buildinputs. Gleiche Artefaktbezeichnung beweist nicht gleiche Bytes. Ein neuer Kommentarcommit verändert nicht automatisch die getestete Binary. Ein erfolgreiches Testfixture über einen Report ist kein ausgeführter Produktprozess.

## 3. Wiederverwendung

Ein Ergebnis darf weiterverwendet werden, wenn alle für seine Aussage relevanten Inputs unverändert oder deren Gleichwertigkeit ausdrücklich belegt sind. Dazu gehören je nach Fall Binary, Runtimebeigaben, Fixture, Harness, Erwartung, Toolchain und Konfiguration.

Originalberichte bleiben unverändert. Ein neuer `evidence-index.json` kann ihre ursprüngliche Herkunft und die geprüfte Gleichheit referenzieren. Kein Austausch alter Commit-/Tree-/Dirty-Felder und kein nachträglicher neuer Laufzeitstempel.

Bei einem unveränderten Binärhash, aber verändertem Comparator ist die originale rohe Prozessausgabe häufig erneut prüfbar, ohne den Prozess zu wiederholen. Der geänderte Comparator benötigt eigene Positiv-/Negativtests. Bei geändertem Prozessharness oder fehlenden nötigen Rohinformationen ist die betroffene Probe neu auszuführen.

## 4. Fix-Matrix

| Änderung nach Review | Minimal sinnvolle neue Prüfung | Normalerweise nicht erneut nötig |
|---|---|---|
| Rechtschreibung, Quellenlink, Statusformulierung | Dokument-/Verweisprüfung, Konsistenz der Aussage | Native-Build, Produktmatrix, Plattformruns |
| Argumentparser oder `ape`-Orchestrierung | Neue Parsergegenprobe, betroffener Dispatch, kleine bestehende JVM-Einstiegsprobe | Gesamte Compiler-/LSP-/Browsertests |
| Download-/Hash-/Archivvalidator | Positivkontrolle und manipulierte Archive/Hashes im Scratch | Echter Compiler-Neubuild bei identischen validierten Toolbytes |
| Report-/Entscheidungslogik | Synthetische Gegenproben plus erneute Validierung originaler Reports | Unveränderte echte Produktprozesse ohne zusätzliche Beobachtungsanforderung |
| Fixture oder ausgewählte Erwartung | Betroffene JVM- und APE-Fälle neu | Unbeteiligte Fälle und komplette Produkt-JUnit-Suite |
| Proxy-/TLS-Harness | Seine Positiv-/Negativkontrollen und betroffene Netzwerkfälle | Formatter-/Diagramm-/Vollkorpusprüfungen |
| Runtime-Truststore oder eingebettete Ressourcen | Native-Inputs neu binden, erforderliche Neubauten, Ressourcen-/TLSfälle; bei neuem finalem Hash kleine P-Matrix | Alle 1000+ JVM-Tests ohne Produktänderung |
| Reachability-/Initialisierungs-/Native-Image-Flags | Betroffener Trigger plus kleine K/F/N-Kernabnahme; finale veränderte Binary auf Pflichtplattformen | Browser/Extension/Web-Image |
| Nur `apelink`-/Loader-/Packagingkonfiguration | Fusion/Package neu, Start-/Hash-/Hermetikproben; bei neuem Binaryhash P-Matrix | Unveränderte Java-Unit-Prüfungen |
| Echte gemeinsame Produktcodeänderung | Konkreter neuer JVM-Regressionsfall, passende Verbraucher und native Matrix für geänderte Bytes | Unbegründete Vollrelease-/Vollkorpusabnahme |
| Toolchainwechsel | Capabilityprobe, beide Architekturen, kleine vollständige Native-Matrix und Pflichtplattformen | Automatisch alle sonstigen Build-/UI-Profile |

Eine grossflächige Produktänderung ist in diesem Auftrag ohnehin ein Scopewarnsignal. Nicht durch eine riesige Testkampagne eine unbeauftragte Portierung legitimieren.

## 5. Rohbelege

Pro realem Lauf mindestens:

- tatsächlicher Programm-/Werkzeugpfad und Version;
- vollständige nicht sensitive Argumentliste und Arbeitsverzeichnis;
- ausgewählte relevante Umgebungsvariablen, niemals gesamte Credentialumgebung;
- Beginn/Ende, Exitcode/Signal/Timeout;
- rohe stdout-/stderr-Dateien mit Hashes;
- echte Hostplattform/-architektur, Emulations-/Loaderstatus;
- Quell-/Input-/Artefakt-/Fixture-/Harnessbindung;
- deklarierte Cases und beobachtetes Ergebnis je Case;
- ausgewiesene Einschränkungen, Skips und fehlende Voraussetzungen.

Originäre Fehlerläufe aufbewahren. Nachfolgende korrigierte Ergebnisse verweisen auf sie; nicht nur die grüne Endversion zeigen. Toolchainarchive müssen nicht ins Gitrepository. Grosse Binärdateien extern oder unter ignorierten Outputs aufbewahren, mit konkretem unveränderlichem Hash.

## 6. Reviewstatus und Machbarkeitsentscheidung sind verschieden

| Achse | Werte | Aussage |
|---|---|---|
| Implementierungs-/Untersuchungsreview | `PASS`, `FAIL`, `BLOCKED` | Hat der unabhängige Reviewer Code, Ziele, Belege und korrekte Schlussfolgerung des tatsächlichen Untersuchungswegs bestätigt? |
| Technische Entscheidung | `GO`, `GO_WITH_LIMITATIONS`, `NO_GO`, `INCONCLUSIVE` | Was ist für die Compiler-APE-Auslieferung wirklich gezeigt? |
| Einzelprobe | `PASSED`, `FAILED`, `NOT_RUN_BLOCKED`, `NOT_APPLICABLE` | Was wurde tatsächlich beobachtet? `NOT_APPLICABLE` nur mit sachlicher Begründung für ausdrücklich nicht fällige Fälle. |

Beispiel: Reproduzierbarer Vendorblocker, sauber isoliert und unabhängig bestätigt → Review `PASS`, Machbarkeit `NO_GO`. Drei Plattformen fehlen → keinesfalls `GO`; mit vollständig ehrlicher Untersuchung kann das Urteil `INCONCLUSIVE` lauten. Eine fehlende unabhängige Reviewsitzung ist dagegen weiterhin Review `BLOCKED`, kein PASS.

Ein fehlender Pflichtnachweis wird nicht dadurch irrelevant, dass man den Fall als „optional“ umetikettiert. Bei frühem terminalem Abbruch bleiben kausal unerreichbare Fälle explizit offen.

## 7. Testauswahl nach einem Finding

Codex dokumentiert kurz:

1. Welche Behauptung oder welcher Vertrag war falsch?
2. Welche Dateien/Inputs/Artefakte ändern sich tatsächlich?
3. Welche konkreten Tests falsifizieren den Defekt und prüfen die Korrektur?
4. Welche Verbraucher können betroffen sein?
5. Welche früheren Belege bleiben warum gültig?

Dann gezielte Prüfung, neuer Commit, neuer unabhängiger Review. Keine Benutzerunterbrechung für normale Retestentscheidungen. Bei einem echten Blocker klare Ursache und tatsächlich erreichten Stand berichten.

## 8. Cache, Infrastruktur und Laufzeitgrenzen

Unveränderte gepinnte Downloads dürfen aus einem verifizierten Cache kommen. Die APE-Bytegenerierung darf bei gleichem vollständigem Buildkey wiederverwendet werden, aber der Report muss `REUSED` statt einer neuen Erzeugung melden. Keine neuen umfassenden Cacheframeworks für diesen Prototyp.

Bei Hash-/Pfad-/Architekturabweichung fail-closed. Testreports nicht aus einem „ähnlichen“ Run zusammensammeln. Eine bereits geöffnete Entwickler-JVM, ein global installierter APE-Loader oder vorhandene private Truststores dürfen den Hermetiktest nicht heimlich erleichtern.

Native-Runs benötigen begrenzte Timeouts und den eigenen Prozessbaum. Timeout ist keine gelungene negative Compilerprüfung. Bei Ressourcenmangel realen Bedarf und Hostlimit unterscheiden. Keine neuen teuren Hosts oder Änderungen globaler Sicherheitseinstellungen ohne ausdrücklichen Auftrag.

## 9. Git und abschliessender Review

Vor dem ersten fachlichen Review: kumulativen Diff prüfen, nur Auftragsdateien stagen, committen. Fremde Änderungen erhalten. Zwischencommits sind zulässig, die finale Prüfung bezieht sich auf einen exakten Gesamtkandidaten.

Vor dem Commit erzeugte Belege behalten ihre tatsächliche ältere SHA/Dirty-Angabe. Die Übereinstimmung ihrer relevanten Inputs mit dem Commit wird separat belegt. Falls ein konkretes Gate zwingend sauberen Commit verlangt, nur dieses Gate danach ausführen; keine Herkunftsfälschung.

Finale `review.md`, `verdict.json`, Sitzungslogs und Reviewanfragen extern aufbewahren. Damit muss ihr Ablegen nicht den geprüften Commit verändern. Keine Änderung nach PASS ohne erneute transparente Prüfung des neuen Stands.
