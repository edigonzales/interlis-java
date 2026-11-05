# Testökonomie und Belegregeln

## 1. Zentrale Regel

Ein neuer Commit, ein Phasenwechsel oder ein neuer Reviewer rechtfertigt **allein** keinen Volltest. Testumfang folgt den geänderten Inputs, der betroffenen Laufzeit und den konkreten Verbrauchern. Die normale Semantik-/Korpus-/Browser-/Performance-/Releaseprüfung ist nicht automatisch Teil dieses Runtimepatchs.

Ein neu erzeugtes APE ist jedoch ein neues Laufzeitartefakt. Alte native Erfolge dürfen nicht auf dessen Hash übertragen werden. Wiederverwendbar sind bei identischen relevanten Inputs beispielsweise JVM-Referenzresultate, reine Comparator-/Manifestunittests, ursprüngliche Fehlerbelege und unveränderte Fixturequalifikation.

## 2. Einmaliger Erstnachweis

Für die initiale Umstellung werden der Originaldefekt, die korrigierte Minimalruntime, der sichere TLS-Pfad und die tatsächlichen neuen Compilerartefakte geprüft. Das ist kein Anlass, sämtliche JVM-Tests aus Auftrag A neu auszuführen. Die zwei teuren Compiler-Native-Builds erst nach dem kleinen Runtimegate.

Native Probequellen können Clock/Selector/HTTP/Zertifikat in einem Programm zusammenfassen. Diese eine Binärdatei mehrmals auszuführen ist billiger und aussagekräftiger als je Testfall einen eigenen Native-Build. Nur andere Quell-/Metadaten-/Toolchaininputs erzwingen eine neue Probe.

Die beiden Compilerarchitekturen verwenden dieselben Produkt-JARs, denselben Patchdigest und dieselbe wesentliche Native-Konfiguration. Nicht eine Architektur mit einem experimentellen älteren Patch stehen lassen.

## 3. Retestmatrix nach Reviewfindings

| Änderung | Kleinster normalerweise ausreichender Retest | Nicht automatisch erforderlich |
|---|---|---|
| Rechtschreibung, Quellenlink, Reviewmetadaten | Dokument-/Schema-/Linkprüfung und unveränderter Produktinputabgleich | Native-Build, JVM-Suite, TLS-Neulauf |
| Patchguard, Receiptschema, Digest-/Pfadvalidator | Betroffene Unit-/Mutationstests; kleiner echter Produzent–Validatorpfad mit vorhandenen unveränderten Artefakten | Komplette Compilerneuerzeugung, sofern das effektive Native-Rezept gleich bleibt |
| Änderungen an Auswahl-/Classpath-/Disable-/Pollargumenten | Argumentgegenproben plus kleiner echter Runtime-Native-Build; bei geändertem tatsächlichem Compilerrezept neue Slices/Fusion und notwendige Laufgates | Vollständiger INTERLIS-Korpus/Browserlauf |
| Ersatzmethodenrumpf, Annotation, Bytecodetransformation oder Vendor-APIzugriff | Strukturprüfungen; neues Runtimebild mit Clock/TLS; danach neue Compilerbilder und gezielte native Integrations-/Zielgates | Beliebige unveränderte Buildtoolunittests |
| TLS-Fixture oder deren Gültigkeitsprofil | Unabhängige Fixturequalifikation, JVM-/native positive/negative Fälle mit vorhandener unveränderter Binary | Neuer Native-Build, solange Trust/Fixture nicht eingebrannt wird |
| Comparator/Reportparser | Alte unveränderte Rohbelege neu auswerten plus manipulierte Gegenprobe; nur fehlende reale Beobachtung neu erfassen | Alle ursprünglichen Prozesse erneut starten |
| Loader-/Paketierung nur ausserhalb Compilerbytes | Geänderten Start-/Transport-/Hermetikpfad auf betroffenen Hosts prüfen; normale unveränderte Compilersemantikbelege bleiben verwendbar | Neuer Compiler-Native-Build ohne tatsächliche Compilerinputänderung |
| Timeout-/Containercleanup | Kleine eigene Timeoutprobe mit separatem Testcontainer | Minutenlanger echter Compilerbau nur zur Bereinigungskontrolle |
| Grössere unerwartete Produkt-/Toolchainänderung | Scope zuerst klären, nicht still als Clockfix weiterführen | Pauschales Upgrade mit nachträglich angepassten Goldens |

Vor einem Retest kurz erfassen: geänderte Dateien, geänderte effektive Inputs, betroffene Verträge, neue erforderliche Kommandos und wiederverwendete Belege. Kein eigenes Framework dafür; ein kurzer Ledgerabschnitt genügt.

## 4. Herkunft und Ergebnisbindung

Jeder verwendete Laufbeleg enthält mindestens Zweck/Fall-ID, Source-/Konfigurations-/Fixtureidentität, Vendorlock, Patchreceipt/-digest, getestete Binary, tatsächliches argv/CWD, relevante erlaubte Environmentwerte, Host-/Architektur-/Emulationskennzeichnung, Exitcode/Signal/Timeout und Rawstream-Hashes.

Zeit, Dauer und lokale Absolutpfade sind Ausführungsmetadaten. Sie gehören nicht in die logische Gleichheitsidentität gleicher Patchinputs. Umgekehrt dürfen Toolchain-, Quell-, Metadaten-, Classpath- oder effektive Flagänderungen nicht aus dem Digest herausnormalisiert werden.

Der Testtreiber prüft Binaryhash vor und nach dem Lauf. Die finale fusionierte Datei ist ein eigener Kandidat; zwei erfolgreich getestete Zwischenbilder ersetzen nicht den Test der Fusion. Eine getestete Datei später durch Neubau desselben Commits zu ersetzen, erfordert neue passende Laufbelege.

Einen ursprünglichen `UNTESTED`-Erzeugungsmanifeststatus nicht in historische Erfolgsaussage umschreiben. Neue Run- und Abnahmereceipts referenzieren dieses Artefakt separat.

## 5. Alte Untersuchung erhalten

Die bisherigen Vendorarchive, alten Compilerbilder, Clock-/TLS-Fehler, verlorenen Timeoutläufe und ursprünglichen Machbarkeits-/Reviewurteile bleiben unverändert. Ein neuer Status nach erweitertem Patchscope sagt nicht, dass das frühere NO_GO unter Patchverbot falsch war.

Unveränderte alte Rohbelege dürfen **als historische/Referenzbelege** zitiert werden, nachdem ihre Bindung geprüft wurde. Sie dürfen keine neue korrigierte Runtime oder fehlende Zielplattform bescheinigen. Fehlende lokale Rohdateien offen nennen, nicht aus einem Markdownbericht rekonstruieren und als Original ausgeben.

Die eingecheckten alten Spezifikationen behalten historischen Charakter. Aktuelle README/Statushinweise dürfen knapp auf den neuen Auftrag verweisen. Keine flächendeckende Textumschreibung aller früheren „kein Patch“-Aussagen; nur aktive widersprüchliche Anweisungen gezielt aktualisieren und Scope der Ausnahme nennen.

## 6. Vertrauensgrenzen

Test-CA/Leafschlüssel sind ausdrücklich öffentliche, wegwerfbare Testfixtures. Sie sind keine privaten Credentials. Dagegen Providerkeys, persönliche Keystores, Authfiles und vollständige Umgebungsdumps gehören nicht in Belege oder Modellprompts.

Ein isolierter Testtruststore ist ein Testlaufinput. Der normale Compilerkandidat erhält die vorher deklarierte Standardtrustbasis. Der Native-Build darf nicht unbemerkt einen gesetzten Entwickler-Truststore oder eine private CA übernehmen. Effektive relevante Builder-/Laufparameter prüfen; keine Secrets vollständig ausgeben.

Mindestens ein positiver Handshake muss funktionieren, bevor negative Fälle als bestanden interpretiert werden. Ein generisches Timeout/EPoll-/Clockversagen ist keine erfolgreiche Hostnamen- oder CA-Ablehnung. Causeketten am Minimalprogramm erfassen; bei abstrahierter Produktdiagnose tatsächlichen Netzwerkverlauf plus den passenden Minimalnachweis verwenden.

Kein TLS-all, kein deaktiviertes Hostnamechecking, keine Fakedate für die öffentliche Zertifikatsprüfung, kein globales Setzen der Systemzeit. Eine korrigierte Uhr muss normale gültige Zertifikate akzeptieren und ungültige weiter ablehnen.

## 7. Prozesslimits und Ressourcen

Bestehende `record.cjs`-/Containerroutinen nutzen. Der Referenzrecorder hat einen kurzen Default; ein langer Native-Prozess darf nicht versehentlich von einem äusseren 60-Sekunden-Wrapper beendet werden. Für jede Ebene explizite Fristen setzen: äussere Frist grösser als innere maximale Laufzeit plus begrenzte Cleanupreserve. Konkrete Limits aus bestehendem funktionierendem Profil übernehmen, nicht beliebig unendlich setzen.

Nur eindeutig eigene benannte `ape-`-Container bereinigen. Kein `docker system prune`, kein Töten fremder JVMs/Container. Private `/tmp`-/HOME-Verzeichnisse, read-only Vendorinputs, Buildnetzwerk weiter deaktiviert und Ausgabebereiche begrenzt. Unveränderte funktionierende Ressourcenlimits nicht als Optimierungsaufgabe behandeln.

Ein Timeout bleibt TIMED_OUT/FAILED. Wenn ein bereits laufender eigener Container anschliessend kontrolliert übernommen wird, ist das ein neuer ausdrücklich verknüpfter Beleg mit wirklichem Exit und Artefaktprüfung, kein Umschreiben des alten Timeouts.

## 8. Nachweisablage

Vorhandenen externen oder ignorierten Belegroot verwenden. Beispiel:

```text
<evidence-root>/compiler-ape-clock-patch/<run-id>/
  baseline/
  inspection/
  compatibility/
  runtime-probes/
  compiler-builds/
  compiler-cases/
  platforms/
  evidence-index.json
  reviews/<candidate-sha>/round-01/
```

Keine Pflicht, alle Binärdateien mehrfach zu kopieren. Inhaltsadressierte Originale read-only referenzieren; der Reviewer erhält deren tatsächliche Bytes oder eine verifizierte zugängliche Kopie. Veraltete absolute Pfade nach Worktreewechsel gezielt auflösen; niemals einfach Hashprüfungen deaktivieren.

`TEST_LEDGER.md` bleibt kurz: Frage → Input → echtes Kommando → Ergebnis → Originalbeleg. Erforderliche Cases und Nichtläufe auflisten. Keine grossen JSON-Logs, Toolchainarchive oder fertigen APE-Dateien ins Git.

## 9. Ergebnisstatus

| Ebene | Werte | Regel |
|---|---|---|
| Patch | `VALIDATED_ON_AVAILABLE_HOSTS`, `FAILED`, `BLOCKED` | Nur echte Korrektur-/Clock-/TLS-Nachweise tragen einen positiven lokalen Status. |
| Compiler-APE | `GO`, `GO_WITH_LIMITATIONS`, `NO_GO`, `INCONCLUSIVE` | GO verlangt alle Pflichtfunktionen/-plattformen. Deklarierter Loader kann eine getestete Einschränkung sein; fehlende Pflichtplattformen oder sichere TLS-Funktion nicht. |
| Review | `PASS`, `FAIL`, `BLOCKED` | Bezieht sich auf Code-/Auftragsqualität und Wahrheit der Ergebnisbehauptung, nicht automatisch Produktionsreife. |

Ein neuer belegter Laufzeitdefekt erlaubt NO_GO nur mit klar begrenztem Geltungsbereich. Fehlender Runner/Netzzugang/Berechtigung begründet INCONCLUSIVE. Kein offener notwendiger Test wird durch „nicht relevant“ oder `GO_WITH_LIMITATIONS` kaschiert.

## 10. Commit und Reviewwiederholung

Geprüften Implementierungsstand committen, dann separaten Reviewer starten. Vorcommitbelege behalten ihre echte Herkunft; getestete relevante Dateien/Inputs gegen den Candidate-Tree abgleichen. Gegebenenfalls nur ein ausdrücklich sauberer Commit verlangendes Gate nach Commit erneut ausführen.

Bei Fixcommit bleibt der kumulative Diff ab BASE_SHA Reviewgegenstand. Alte unveränderte Belege dürfen wiederverwendet werden, das alte Reviewerurteil dagegen nicht als Review des neuen Commits. Jeder finale Kandidat braucht ein frisches unabhängiges Urteil. Danach keine ungeprüften Abschlussänderungen einschieben.
