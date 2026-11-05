# Spezifikation — begrenzter lokaler CosmoGVM-Clock-Fix

## 1. Produktziel und Ausgangslage

Gegenstand ist der optionale Compiler-APE-Build von `interlis-java`. Die bestehenden Java-Produktartefakte aus Jenesis werden unverändert konsumiert. Der lokale Patch repariert die Kalenderzeitquelle von `System.currentTimeMillis()` im CosmoGVM-Ziel, damit sichere TLS-Prüfungen und andere Verbraucher dieser Zeitquelle korrekt arbeiten können.

Untersuchungsbasis: `codex/compiler-ape-feasibility` bei `d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87`. Die ursprüngliche Untersuchung dokumentiert funktionierende lokale Compilerfälle, einen durch Buildzeitwahl von Poll konfigurierbaren Selectorfehler und den getrennten Runtime-Uhrfehler. Der letzte vollständig gebaute historische Compilerkandidat stammt noch aus der Konfiguration vor dem erfolgreichen separaten Poll-Reproducer. Diese Grenzen bleiben bestehen, bis neue echte Nachweise vorliegen. [S1–S5 in SOURCES.md]

Die beobachtete Cosmo-Substitution verwendet für `currentTimeMillis()` eine monotone Uhr, während die andere Zeitimplementierung `CLOCK_REALTIME` nutzt. Der öffentliche Quellstand begründet die Diagnose; er ist **kein Nachweis**, dass alle internen APIs des gepinnten Binärpakets mit diesem Quellstand identisch sind. [S6–S8]

## 2. Enge Ausnahme vom bisherigen Patchverbot

Erlaubt ist nur die Runtimekorrektur:

| Stelle | Erlaubte Änderung |
|---|---|
| `Target_java_lang_System_Cosmo.currentTimeMillis()J` bzw. die gezielt an ihre Stelle tretende Substitution | Aufruf der Kalenderzeitquelle `CLOCK_REALTIME` anstelle von `CLOCK_MONOTONIC` |
| Fehlerliteral in genau dieser Methode | Tatsächlich verwendete Clock korrekt benennen |
| APE-Build-/Prüfadapter | Patch isoliert erzeugen, auswählen, binden, nachweisen und später entfernen können |

Die Umrechnung in Millisekunden, native Datenstruktur, Statusprüfung und Uninterruptible-/Safepointanforderungen sind aus dem **tatsächlich verwendeten Vendorbytecode** zu übernehmen. Keine neue Zeitsemantik, keine feste Epoche, kein Offset auf Bootzeit, keine Speicherung eines Buildzeitdatums.

Unzulässig sind eine neue Implementierung des HTTP-Clients, Änderungen an TrustManager/PKIX/JSSE, pauschale `--initialize-at-build-time`-Regeln, das Abschalten von Verifikation, Änderungen anderer JDK-Substitutionen und zusätzliche Vendorfixes. Ein weiterer Defekt ist ein neuer Befund, keine automatisch freigegebene Patchserie.

Kein Toolchainupgrade zur Umgehung eines Guards. Keine Produktcodeänderung, keine neue Laufzeitdependency, keine zusätzliche JVM im APE, kein Gradle-/Maven-Fallback. Keine neuen Formate, JPMS-, LSP-, VSIX-, TeaVM- oder Web-Image-Arbeiten.

## 3. Route und Entscheidungslogik

### 3.1 Bevorzugt: Zusatz-JAR

Originale Toolchain + originale sieben Compiler-JARs + genau erfasstes Kompatibilitäts-JAR → neuer Native-Build.

Die einzelne Vendor-Ersatzmethode wird gezielt deaktiviert. Eine neue Ersatzklasse implementiert ausschliesslich `currentTimeMillis()`, beschränkt auf `--libc=cosmo`. Keine komplette `System`-Ersatzklasse und keine Deaktivierung aller Methoden der Vendor-Substitutionsklasse.

Kandidat für die Option, zuerst am Vendorbinary zu bestätigen:

```text
-H:DisableSubstitution=com.oracle.svm.core.posix.cosmo.Target_java_lang_System_Cosmo.currentTimeMillis
```

Es wird der **annotierte Vendor-Methodenname** adressiert, nicht `java.lang.System.currentTimeMillis`. Weder Schreibweise noch Singular/Plural anhand fremder Versionen raten. Hilfe, Bytecode des tatsächlichen Prozessors und ein kleines echtes Native-Experiment sind die Abnahme. [S7]

### 3.2 Bedingt: Builder-JAR-Ableitung

Route B ist nur nach dokumentierter technischer Untauglichkeit von A freigegeben. Gezielte Instruktionsänderung an `currentTimeMillis()J` im tatsächlichen, gehashten Original-Classfile, plus Fehlerliteral. Keine rohe Byte-/Stringersetzung; keine Änderung gemeinsam genutzter Constant-Pool-Einträge zulasten anderer Methoden. Nur eine kopierte, inhaltsadressierte Installation. Originalbaum unverändert und weiterhin prüfbar.

Signierte/anderweitig integritätsgeschützte Bestandteile dürfen nicht durch Entfernen ihrer Prüfmetadaten „repariert“ werden. Ist die Ableitung unter den bestehenden Integritätsbedingungen nicht sauber nutzbar, ist B blockiert. Kein kompletter Quellneubau von GraalVM/JDK/Cosmopolitan.

### 3.3 Genau ein Ergebnisweg

Im aktiven Code und `patch.json` steht nach C1 genau eine Strategie: `SUBSTITUTION_JAR` oder `DERIVED_BUILDER_JAR`. Keine automatische Laufzeit- oder Buildfallbacklogik. Die nicht gewählte Implementierung bleibt nicht als Wartungsaltlast erhalten. Diagnostische ungepatchte Mini-Proben bleiben erlaubt, aber es gibt keinen stillen ungepatchten Produktpfad innerhalb des korrigierten APE-Profils.

## 4. Eigentümer und Grenzen

| Komponente | Besitzer / Regel |
|---|---|
| Compiler-JARs und deren Versionsauflösung | Bestehendes Jenesis-Profil; unverändert |
| Original-CosmoGVM und cosmocc | Bestehender Vendorlock und Inventarprüfer; unverändert |
| Lokaler Fix | `tools/ape/compat/cosmogvm-clock/`; nur optionaler APE-Build |
| Patchorchestrierung | Schmale Ergänzung der vorhandenen Node-/Java-APE-Adapter |
| Laufzeitproben | Bestehende `tools/ape`-Harnesses, kleine gezielte Ergänzungen |
| Release-/Integritätsaussagen | Konkrete neue Manifeste und Rohbelege; keine pauschale Erfolgsbehauptung |

Keine neue Gradle-/Jenesis-Teilprojektregistrierung für den Patch. Keine eigene Dependencyauflösung; zum Kompilieren der Substitute nur die passenden verifizierten Vendor-JARs heranziehen. Keine Klassenkopien aus einer fremden GraalVM-Version.

## 5. Verbindlicher Basispin

Im Referenzbranch stehen [S3]:

| Input | Referenz |
|---|---|
| CosmoGVM | `25.2.4-dev`, Archivlinie `25.2i-r2` |
| Graal-Archiv SHA-256 | `c420fdfe510baa353290267e53821edf23f6e47127587b7ff0ee6ff7c288f704` |
| cosmocc-Archiv SHA-256 | `7b3c11802791037aa5d7d1c02c9f54b21b25ba6369e1f1cc222aca9698641a97` |
| Vollständiger Vendorinventarpin | `cc819e6dbbac1c4b4dd579d448c2cdd2d3a19c784a96e2006b13039be14a125f` |
| Builderimage | `sha256:063b17654e59a88128e193fcc8cc4a094d37921b20bbc07eed35aa0e01004aff` |

Die bestehende Lockdatei bleibt autoritativ; diese Tabelle ist der Dokumentabgleich. Unerwartete lokale Abweichungen klären, nicht überschreiben oder „aktualisieren“. Hashes der Originalklasse, des konkreten Builder-JARs und des erzeugten Patchartefakts müssen aus den **tatsächlichen Bytes** ermittelt und nach Verifikation in den Patchvertrag aufgenommen werden. Hier werden dafür keine Fantasiehashes vorgegeben.

Öffentliche Quellstände werden zum Verständnis genutzt, nicht als automatisch passende Kompilierbasis. Vorhandene Klassen-/Methodenannotationen, Modulnamen, Signaturen und `Uninterruptible`-Package sind per Binärinspektion festzustellen. Der bekannte Distributionsverzeichnisname ist kein Beweis eines öffentlich auflösbaren Sourcecommits.

## 6. Geplante Dateistruktur

```text
tools/ape/
  compat.cjs                         # neue schmale Orchestrierung/Validierung
  compat.test.cjs                    # gezielte Gegenproben
  compat/cosmogvm-clock/
    README.md
    patch.json                       # konkrete Pins und gewählte Strategie
    inspector/InspectCosmoClock.java
    src/guru/interlis/ape/compat/Target_java_lang_System_ApeClock.java  # nur A
    transformer/PatchCosmoClockJar.java                              # nur B
  probes/RuntimeProbe.java            # kombiniert Clock/Selector/HTTP/Zertifikat
  runtime-probes.cjs                 # kleiner Build-/Runharness, vorhandene Helfer nutzen
  runtime-probes.test.cjs
  compiler-native.cjs                # eng erweitern
  fixture-server.cjs                 # nur fehlende TLS-Fixtureprofile
  network-probes.cjs                 # korrekte Identität und konkrete Negativursachen

build/interlis/ape/
  CompilerApe.java                   # COMPAT + explizites --compat
  toolchain.lock.json                # Basisbytes/Pins nicht ändern
```

Die genannten neuen Klassen sind **Zielverträge, keine vorhandenen Dateien**. Sachlich gleichwertige kleinere Aufteilung ist zulässig; keine Umbenennungsorgie. Nicht benötigte Alternativklassen nicht anlegen. `ClockProbe.java` und `HttpProbe.java` dürfen als schlanke ursprüngliche Reproducer erhalten bleiben. Bestehende weitere Helfer sollen nicht ohne aktuellen Grund umgeschrieben werden.

Erzeugnisse liegen unter `target/compiler-ape/compat/<digest>/`; Originalbelege und Reviewausgaben ausserhalb regulärer Clean-Ziele. Keine generierten Toolchainbinaries ins Git.

## 7. Integration in den bestehenden Einstieg

`CompilerApe.Operation` erhält `COMPAT`. `parse(String[])` kennt die exakten Optionsmengen und weiterhin absolute, bestehende Dateien. Vorschlag für den Zielvertrag:

```sh
java build/InterlisJvmBuild.java ape compat --lock /abs/toolchain.lock.json --patch /abs/patch.json
java build/InterlisJvmBuild.java ape build --inputs /abs/compiler-inputs.json --arch x86_64 --lock /abs/toolchain.lock.json --compat /abs/compat-receipt.json
java build/InterlisJvmBuild.java ape build --inputs /abs/compiler-inputs.json --arch aarch64 --lock /abs/toolchain.lock.json --compat /abs/compat-receipt.json
java build/InterlisJvmBuild.java ape fuse --x64 /abs/x64/slice.json --arm64 /abs/arm/slice.json --lock /abs/toolchain.lock.json --compat /abs/compat-receipt.json
```

Diese Kommandos beschreiben die einzuführende Schnittstelle; sie sind nicht im Ausgangsbranch schon gültig. Bestehende Tests/aktuelle README-Aufrufe gemeinsam anpassen. Keine dauerhaften Legacyaliases für das alte ungepatchte Produktrezept. Historische Run-argv bleiben unverändert.

`COMPAT` erzeugt nach Guards nur das Patchartefakt, **keinen Compiler**. Keine impliziten Toolchaindownloads, kein impliziter Strategiewechsel. Lockreihenfolge APE → JVM aus dem bestehenden Adapter erhalten; der Native-Build hält nicht unnötig den JVM-Lock. Separate Probeläufe benutzen die gleichen geprüften Kompatibilitätsargumente, nicht kopierte Konfiguration auf gut Glück.

## 8. Daten- und Methodenverträge

### 8.1 `patch.json` — eingecheckte Absicht

Enthält Schema, Patch-ID, genau eine Strategie, Basislock-/Archiv-/Klassenbindung, exakten Klasseneintrag, Methodenname/Descriptor, erwarteten ursprünglichen Clockaufruf und gegebenenfalls Deaktivierungsziel. Dazu die zulässigen Quelldateien und Guardregeln. Keine Hostpfade, Secrets, leeren Pins oder `latest`.

Die Datei beschreibt **erwartete** Identität. Das Patchwerkzeug darf nicht bei jedem Lauf automatisch die gerade vorgefundene Klasse als neuen erwarteten Pin übernehmen. Ersteinrichtung in C0/C1 nach Inspektion und Originalreproducer; später strikt prüfen.

### 8.2 `compat-receipt.json` — tatsächliches Erzeugnis

Enthält:

- Patch-ID/-Schema, Strategie und Hash der `patch.json`;
- Originalvendorlock/-inventar/-Builder-JAR/-Classfilehash;
- Quell-/Inspector-/Generatorhashes und tatsächlich verwendetes `javac`;
- Patch-JAR mit Grösse/Hash/Eintragsinventar **oder** abgeleiteten Builderbaum mit erlaubtem Delta;
- normalisierte Wirkung (`originalClock`, `replacementClock`, Methode), konkrete Buildargumente und Original-Prozessreceipts;
- eigenen logischen `compatibilityDigest`, ohne volatile Zeitstempel und Hostabsolutpfade in der logischen Identität.

Absolute lokale Erzeugnispfade dürfen im Ausführungsreceipt stehen, aber nicht die alleinige Identität bilden. Alle Dateipfade relativ zum versiegelten Erzeugnis prüfen; Traversal, externe Symlinks, fehlende/zusätzliche Dateien und doppelte JAR-Einträge ablehnen.

Ein Manifesthash allein ist kein Sicherheitsnachweis gegen einen Angreifer, der alle lokalen Dateien inklusive Hashes ändern kann. Beansprucht wird die konsistente Bindung an verifizierte Inputs und unveränderte Laufbelege. Gegenproben prüfen insbesondere, dass neu gehashte Mutation nicht ohne passenden Produzenten-/Policyabgleich akzeptiert wird.

### 8.3 `compat.cjs`

Die detaillierten Signaturen stehen in PATCH_DESIGN. Verbindliche Verantwortlichkeiten: `readPatchSpec`, `inspectVendor`, `prepareCompatibility`, `verifyCompatibilityReceipt`, `compatibilityArguments`, `verifyDerivedInstallation` nur bei B. Kein allgemeiner Pluginmechanismus.

Alle Guardfehler sind vor einem teuren Native-Build zu melden. Kein nur geloggtes Warning. Kein Erzeugen eines erfolgreichen Receipts nach fehlgeschlagenem Prozess, falscher Trefferzahl oder Timeout.

### 8.4 `compiler-native.cjs`

`build`, `nativeArguments`, `readSlice`, `fuse` erhalten eine explizite verifizierte Compatibility-Referenz. Produkt-`inputs()` bleibt bei den ursprünglichen sieben Runtime-JARs; das Patch-JAR wird separat dem Native-Image-Build zugeführt und nicht ins Produktinputverzeichnis kopiert.

`common` erhält `compatibilityDigest`, Strategie und alle dafür relevanten Identitäten. Der gesamte effektive Buildaufruf bleibt im `plan.json`. `readSlice` validiert Patchartefakt/Receipt und rekonstruiertes Rezept, nicht nur die frei behauptete Patch-ID. Fusion verlangt gleiche gemeinsame Inputs und dieselbe Strategie für beide Architekturen.

Beim kopierten Builderpfad B unterscheidet der Plan Basis- und effektive Toolchain. Bei A bleibt Basisbaum exakt unverändert. Native-Systemproperty Poll bleibt ausdrücklich gesetzt. Optimierungslevel, Heap, Parallelität und Ziel-CPU werden nicht nebenbei verändert.

### 8.5 Bundle und Nachweise

Das Endmanifest beschreibt `localRuntimePatch` als Bestandteil der Herkunft. Patchquellen, Hinweise und Erzeugnisidentität können als Dokumentation beigefügt werden; das Patch-JAR oder eine Builderinstallation sind **keine notwendigen Laufzeitbeigaben** und dürfen nicht heimlich zur Ausführung benötigt werden.

Bestehende Loader, Ressourcen und Noticeinventare erhalten. Bei übernommenem Vendorcode Herkunft/Änderung/Lizenzhinweise sauber dokumentieren; keine pauschale Rechts- oder Produktionsfreigabe. Prüfe, ob über den vorhandenen Noticepfad hinaus ein knapper Patchhinweis erforderlich ist.

## 9. Laufzeitverträge

### Uhr

`currentTimeMillis()` soll innerhalb einer konservativen, vorab festgelegten Toleranz zur Hostkalenderzeit und `Instant` liegen. Mehrere Samples vor/nach kurzer Wartephase und aus einem zweiten Thread. Wallzeit darf sich prinzipiell durch Uhrsynchronisierung ändern; **keinen monotonen Vertrag für Wallzeit erfinden**. Für `nanoTime()` nur Differenzen innerhalb desselben Prozesses, keine Epoche, keinen Vergleich absoluter Werte unterschiedlicher Prozesse verlangen. Keine Hostzeitsprünge herbeiführen. [S9]

### TLS

Echte positive lokale TLS-Kontrolle vor und nach den Negativfällen. Unbekannte CA, falscher Hostname, abgelaufenes und noch nicht gültiges Leaf werden mit passender Ursache abgelehnt. Zertifikate mit kontrolliertem CA-/SAN-/EKU-/Zeitprofil; JVM und unabhängiger Host prüfen Fixturegültigkeit. Schlechte Testdaten sind keine Runtimefehlermeldung.

Isolierter Laufzeittruststore erlaubt. Standardkandidat ohne Test-CA. Ein öffentlicher HTTPS-Smoke wird separat von deterministischen Loopbackfällen betrachtet; DNS-/Proxy-/Internetblockaden offen melden. Keine fremden Daten/Tokens loggen.

### Compiler und Portabilität

Genau die getesteten neuen Artefakte verwenden. Neue Runtimebytes invalidieren alte native Laufzeitbelege, nicht aber unveränderte Comparatorunittests oder echte JVM-Referenzen identischer Produktinputs. Beide Architekturimages aus identischem Patch-/Produkt-/Vendorstand neu erzeugen und fusionieren. Alte Bilder nicht nachträglich binär patchen.

Auf echtem macOS arm64 vorhandene vollständige kleine CLI-/Formatter-/Netzwerkfälle ausführen. Auf verfügbaren nativen Linux-x64-/Windows-x64-Zielen den gezielten Pflichtausschnitt aus TEST_MATRIX. Emulation klar getrennt. Kein Aufbau neuer bezahlter Runner. Fehlende Pflichtplattform hält die Gesamtauslieferung auf `INCONCLUSIVE`, auch wenn die lokale Patchimplementierung erfolgreich ist.

## 10. Abnahmekriterien CP-01 bis CP-32

| ID | Verbindlicher Nachweis |
|---|---|
| CP-01 | Tatsächliche Baseline, fremde Änderungen und der eng autorisierte Folgescope sind erfasst; frühere Ergebnisse bleiben zuordenbar. |
| CP-02 | Originalarchive, Basislock und Vendorinventar bleiben unverändert verifiziert; keine globale Installation. |
| CP-03 | Zielklasse/-methode, interne APIs, Deaktivierungsfähigkeit und Originalfehler wurden am tatsächlich gepinnten Binary geprüft. |
| CP-04 | Einzige Runtimeänderung ist Kalenderzeitquelle und Fehlertext von `currentTimeMillis()J`; keine Nebenfixes. |
| CP-05 | `nanoTime()`, `Instant`, andere `System`-Substitutionen und deren benötigte Metadaten bleiben unverändert. |
| CP-06 | Low-Level-/Uninterruptible-Verträge sind erhalten; kein `Instant`-/Offset-/Buildzeitersatz und keine gelockerten Builderprüfungen. |
| CP-07 | Route A wird zuerst sachgerecht geprüft; alte Methode gezielt deaktiviert und Ersatz nur für Cosmo wirksam, sofern A gewählt. |
| CP-08 | B nur mit begründetem A-Hindernis; genau eine aktive Strategie, kein allgemeines Patchframework oder stiller Fallback. |
| CP-09 | Falsche/mehrdeutige/already-fixed/doppelt gepatchte Inputs werden vor Native-Erzeugung sicher zurückgewiesen. |
| CP-10 | Manifest/Receipt/Generator-/Quell-/Erzeugnishashes binden tatsächliche Inputs; keine automatisch vertrauten neuen Pins. |
| CP-11 | Die sieben Produkt-JARs bleiben unverändert und getrennt von der Kompatibilitätskomponente. |
| CP-12 | Neue Operation und Argumente sind explizit, eng validiert, korrekt gelockt; normale JVM-/LSP-/Wasm-Profile bleiben nativfrei. |
| CP-13 | Buildkeys, Sliceprüfung und Fusion erzwingen denselben vollständigen Kompatibilitätsstand; gemischte Kandidaten scheitern. |
| CP-14 | Original-/abgeleitete Toolchain und lokale Patchwirkung sind in Plänen und Bundleherkunft korrekt ausgewiesen. |
| CP-15 | PollSelectorProvider ist weiterhin beim Build gewählt; Probe und Compiler verwenden denselben relevanten Runtimepatch. |
| CP-16 | Ungepatchter Originalreproducer bleibt Negativkontrolle; ein korrigierter nativer Minimallauf ist wirklich erfolgreich. |
| CP-17 | Hostzeitvergleich, mehrere Samples, Threadkontrolle und `nanoTime`-Differenzen sind angemessen geprüft. |
| CP-18 | Positive HTTP-/TLS-Kontrollen funktionieren mit erwartetem Response und nachweislicher Serverinteraktion. |
| CP-19 | Untrusted CA, falscher Host, abgelaufen/nicht gültig scheitern aus der erwarteten Ursache; Positive danach bleibt erfolgreich. |
| CP-20 | Testtrust ist nur isolierter Laufzeitinput; Standardkandidat enthält keine Fixture-CA, keine TLS-Abschwächung. |
| CP-21 | Beide neuen nativen Compilerbilder konsumieren dieselben verifizierten Produkt-/Patchinputs; echte Fusion und finale Datei sind geprüft. |
| CP-22 | Gezielte vorhandene CLI-/Formatierungs-/Dateiverträge stimmen mit der JVM überein; keine fachliche Normalisierung zur Kaschierung. |
| CP-23 | HTTP-/HTTPS-Imports, Cache/Offline, Proxy/CONNECT, Timeouts und Fehlerantworten sind für den neuen Kandidaten gezielt nachgewiesen. |
| CP-24 | Native, emulierte und fehlende Zielplattformen sind getrennt; kein vollständiges GO bei fehlendem Pflichtzielnachweis. |
| CP-25 | Bestehender Loader-/Relokations-/ohne-JVM-Vertrag ist erhalten; keine falsche Ein-Datei- oder Direktstartzusage. |
| CP-26 | Manipulierte/neu gehashte Artefakte, falsche Receipts und fehlende Cases werden durch konkrete Gegenproben abgelehnt. |
| CP-27 | Recorder/Container besitzen passende begrenzte Timeouts und sichere eigene Bereinigung; Abbruch bleibt Fehler. |
| CP-28 | Aktive Altversuche/Fallbacks sind bereinigt, historische Rohbelege unverändert; neuer Status widerspricht den alten Bedingungen nicht. |
| CP-29 | Entfernungspfad für einen korrigierten Vendor ist dokumentiert und fail-closed vorbereitet; keine dauerhafte Produktabhängigkeit. |
| CP-30 | Phasenprüfungen und Retests sind wirkungsbezogen; unveränderte Nachweise mit echter Herkunft statt pauschaler Vollwiederholungen. |
| CP-31 | Geprüfter Kandidat ist lokal committet; Scope sauber, keine Secrets/Binaries/fremden Änderungen und kein unbeauftragter Push. |
| CP-32 | Frischer autonomer OpenCode-2.x-/DeepSeek-Flash-1017-Gesamtreview des exakten Commits; Findings korrigiert und erneut geprüft. |

Strategiegebundene Unterpunkte werden für den nicht gewählten Weg als `NOT_APPLICABLE` begründet, nicht implementiert. Das Kriterium CP-08 selbst bleibt trotzdem fällig. Fehlende Plattformbelege sind nicht `NOT_APPLICABLE`, sondern `NOT_RUN_BLOCKED` für den entsprechenden Produktanspruch.

## 11. Abschluss und Grenzen

Ein vollständiger positiver Abschluss setzt passende echte Nachweise voraus. Bei korrekt integriertem, auf verfügbaren Hosts validiertem Patch kann der technische Implementierungsauftrag abgeschlossen werden, während der plattformübergreifende Gesamtanspruch `INCONCLUSIVE` bleibt. Der Review muss genau diese Trennung beurteilen.

Eine neue nicht freigegebene Runtimeinkompatibilität kann einen terminalen begründeten Abschluss auslösen. Dann keine weiteren grossen Builds und keine fiktiven späteren Gates. Bei behebbaren Fehlern innerhalb dieser Implementierung korrigieren, nicht als Vendorblocker ausgeben.

Es wird weder ein Release veröffentlicht noch eine Sicherheits-/Produktionszertifizierung erteilt. Die nachgewiesene Runtimekorrektur ist ein begrenzter Schritt zur Compiler-APE, nicht automatisch deren vollständige Abnahme.
