# Codex-Auftrag — lokaler CosmoGVM-Clock-Patch für die Compiler-APE

Du bist Codex, der implementierende Primäragent im lokalen Repository `edigonzales/interlis-java`. Führe diesen Auftrag aus; liefere nicht nur einen weiteren Plan.

## 1. Verbindliches Ziel

Behebe den isolierten CosmoGVM-Uhrfehler für unseren optionalen Compiler-APE-Build mit einem einzigen eng begrenzten lokalen Kompatibilitätspatch. Stelle danach anhand kleiner echter Laufprüfungen fest, ob die bisher blockierten sicheren HTTP-/HTTPS- und Compiler-Repositoryfälle funktionieren. Verwende die bereits implementierten Jenesis-Compilerinputs und die vorhandene APE-Infrastruktur.

Die einzige freigegebene semantische Runtimeänderung ist:

```text
Cosmo-Substitution von java.lang.System.currentTimeMillis()J:
CLOCK_MONOTONIC → CLOCK_REALTIME
plus passend korrigierter Fehlertext derselben Methode.
```

Alle übrigen Runtimefunktionen bleiben unverändert. Insbesondere kein Umbau von `nanoTime()`, `Instant`, TLS, TrustManager, HostnameVerifier, Zertifikatsgültigkeit, Compiler oder Repositorytransport.

## 2. Dokumente und Baseline

Lies vollständig README, SPECIFICATION, PATCH_DESIGN, EXECUTION_PLAN, TEST_MATRIX, TEST_AND_EVIDENCE_POLICY und OPENCODE_REVIEW_PROMPT aus diesem Ordner. SOURCES ist der Belegindex. Lies zusätzlich geltende `AGENTS.md`, relevante aktuelle Spezifikationen und die tatsächlichen Ergebnisse unter `docs/implementation/compiler-ape-feasibility/`.

Referenz: Branch `codex/compiler-ape-feasibility`, Commit `d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87`. Erfasse deinen tatsächlichen `BASE_SHA`, Tree und lokale Änderungen. Kein Reset auf den Referenzcommit; keine fremden Änderungen verwerfen, stagen, stashen oder committen. Verwende den bestehenden Arbeitsbranch oder einen daraus abgeleiteten lokalen Arbeitsbranch ohne Umschreiben vorhandener Historie.

Vorhandene normale JVM-Quellen unter Root-`build/` sind keine wegwerfbaren Outputs. Kein ungezieltes `clean`, kein `git clean -fdx`, kein globaler Cache-/Toolchainumbau.

## 3. Eng begrenzte Erlaubnis

Das bisherige Patch-/Substitutionsverbot wird für genau diesen Clock-Fix ersetzt. Route A zuerst: separates APE-Kompatibilitäts-JAR und methodengenaue Deaktivierung der Vendor-Substitution. Belege dies zuerst am tatsächlichen gepinnten Vendorbinary, nicht nur an einer neueren GitHub-Quelldatei.

Wenn Route A technisch nicht sauber möglich ist, darfst du ohne neue Benutzerinteraktion auf die in PATCH_DESIGN beschriebene Route B wechseln: kopierte Vendorinstallation, Änderung genau einer Methode in genau einem Builder-Classfile, vollständige Original-/Ableitungsprüfung. Dokumentiere die Ursache und den Entscheidungspunkt. Fehlende Imports, eine falsch geschriebene Option oder ein gewöhnlicher Testfehler rechtfertigen keinen vorschnellen Routenwechsel.

Am Ende genau ein aktiver Weg. Kein dauerhaftes `--strategy auto`, kein Fallback auf ungepatchte Compilerbilder, kein eigener GraalVM-Quellfork, keine zusätzlichen Workarounds. Beide unzulänglich: belastbaren Blocker dokumentieren, keine Scopeausweitung.

Nicht beauftragt sind Toolchain-/Dependencyupgrades, neue Buildsysteme, geänderte Produkt-JARs, neue Formatierungsregeln, JPMS, LSP, Extension, TeaVM, Web Image, jpackage, eine neue Loadertechnik oder allgemeine Infrastrukturhärtung. Nur die unmittelbar für Patchbindung, Fixtureisolation und valide Tests nötigen Anpassungen sind enthalten.

## 4. Phasen und Tests

Arbeite C0 bis C5 in EXECUTION_PLAN ab. Jede Phase muss ihr positives Gate erfüllen, bevor die nächste implementierende Phase beginnt. Ein absichtlich falscher Testinput gilt als bestandene Gegenprobe nur, wenn der Harness genau die erwartete Ablehnung überprüft.

Technischer OpenCode-/Modellpreflight darf früh erfolgen. Keine unabhängigen fachlichen Reviews nach Einzelphasen. Der frische unabhängige Gesamt-Review folgt erst am committeten Kandidaten nach C5 beziehungsweise nach dem ehrlich dokumentierten terminalen Blockerabschluss.

Prüfe zuerst Clock-/Selector-/TLS-Minimalprogramme. Erzeuge die beiden grossen Compilerbilder erst, wenn die kleine korrigierte Runtimeprobe die fälligen positiven und negativen Kontrollen besteht. Baue die Compiler-JARs nicht neu, wenn ihre unveränderten vollständigen Inputs verifiziert wiederverwendbar sind.

Wähle bei jeder Änderung den kleinsten ausreichenden Testumfang. Wiederhole keine vollständigen Korpus-, Browser-, Performance-, Gradle- oder Jenesis-Suiten allein wegen eines Phasen- oder Commitwechsels. Nach einem neuen finalen APE-Hash sind die erforderlichen Laufzeitbelege für diese Bytes zu erneuern, nicht beliebige unveränderte Unittests.

Erhalte die PollSelectorProvider-Wahl beim Native-Build. Verwende dieselbe Patch-/Runtimekonfiguration bei minimaler Probe und Compiler. Test-CA nur zur Laufzeit isoliert, nie in den Standardkandidaten einbacken. Keine Netzsicherheitsprüfung abschwächen.

## 5. Evidenz und Infrastruktur

Erhalte unveränderte Vendorarchive und ursprüngliche Installationsinventare. Route A verändert keinen Vendorbyte. Route B erhält eine klar getrennte abgeleitete Installation; nur eine erlaubte Classfile-/JAR-Änderung darf vom Original abweichen.

Die sieben Produkt-JARs bleiben Produktinputs. Eine zusätzliche Substitutionsklasse ist ein separater Buildinput mit eigener Hashbindung, nicht die achte Produktbibliothek. Auch im Fall Route B wird die Ableitung im Common-Digest beider Slices erfasst.

Alte `NO_GO`-, Timeout- und Fehlbelege nicht umetikettieren. Ein neues Resultat gehört zur neuen tatsächlichen Konfiguration. Übernimm keine alte Run-ID, keinen alten Commit und keinen alten Artefakthash als neue Ausführung.

Normale macOS-Loaderbeigabe bleibt offen ausgewiesen. Native Linux-/Windowsrunner nur verwenden, wenn sie bereits legitim verfügbar sind. Keine kostenpflichtigen Hosts, fremden Accounts, globalen Loader-/binfmt-Installationen oder Änderungen der Hostzeit beschaffen. Fehlende Infrastruktur getrennt erfassen und verfügbare sinnvolle Arbeit abschliessen.

Alle Prozesse sind zeitlich und ressourcenmässig begrenzt. Beachte den bestehenden 60-Sekunden-Default im äusseren Recorder. Längere native Aufrufe erhalten explizite, ineinandergreifende Timeouts und saubere Beendigung nur der eigenen Container. Ein Timeout wird nie in PASS umgeschrieben.

## 6. Commit und unabhängiger Review

Nach erfolgreichem C5-Gate den geprüften Stand lokal committen. Kein Push oder Release. Vorher Diff, `git diff --check`, Stagingumfang, Secrets, unerwünschte Binaries und Quell-/Vendor-/Produktdrift prüfen. Exakten Candidate-SHA und Tree erfassen.

Starte in einem separaten sauberen Worktree eine neue OpenCode-2.x-Sitzung mit **DeepSeek Flash 1017 über die direkte DeepSeek-API**. Nutze vorhandene Authentifizierung. Keine globale Installation/Aktualisierung, keine exportierten Keys und kein Modellfallback. Tatsächliche CLI-Syntax und Modellzuordnung prüfen; nicht die Zeichenkette `deepseek-flash-1017` als vermeintlich gültige API-ID erfinden.

Der Reviewer liest den gesamten Auftrag, den kumulativen Diff und die Originalbelege selbst. Er prüft Code und Zielerreichung, führt eigene gezielte Gegenproben aus und schreibt `review.md` und `verdict.json`. Er darf keine Produktdateien ändern, nicht committen und nicht weiterdelegieren. Keine blosse Prüfung einer Codex-Zusammenfassung.

Bei berechtigten Findings: selbst korrigieren, Änderungswirkung bestimmen, nur notwendige Tests ausführen, neu committen und frischen unabhängigen Gesamt-Review starten. Ein Review des alten Commits gilt nicht für neue Änderungen. Frühere korrekte Testbelege können nach geprüftem Inputabgleich wiederverwendet werden.

Ein fehlendes oder blockiertes Review wird als solches ausgewiesen, niemals als PASS. Auth-/Runnerprobleme sind kein Grund für stille Fallbacks. Keine Endlosschleifen mit denselben fehlgeschlagenen Kommandos.

## 7. Abschluss

Lege `STATUS.md`, `TEST_LEDGER.md` und `RESULT.md` kurz und nachvollziehbar an. Reiche Originalbelege extern oder in bestehender ignorierter Ablage mit Index/Hashes mit. Keine neue Berichtsbürokratie und keine grosse Kopie alter Logs im Git.

Berichte: tatsächlich gewählter Patchweg, exakte Änderung, Vendor-/Patch-/Binaryidentitäten, erfolgreiche und offene Cases, native/emulierte Plattformen, unveränderte normale Builds, finaler Commit, echtes unabhängiges Urteil und Belegpfade. Trenne `patchStatus`, `compilerApeStatus` und `reviewVerdict`.

Ein technisch nicht lösbarer neuer Defekt darf minimiert und unabhängig geprüft als terminaler Blocker enden. Ein gewöhnlicher behebbarer Implementierungsfehler darf nicht durch die Bezeichnung „Machbarkeitsauftrag“ legitimiert werden.

Beginne jetzt mit C0 und führe den Auftrag bis zum geprüften, committeten und unabhängig reviewten Ergebnis aus.
