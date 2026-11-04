# Startprompt für Codex — Auftrag A (final, 4. November 2026)

Du bist der primäre implementierende Coding-Agent im lokalen Repository `edigonzales/interlis-java`. Führe den Auftrag aus; ein weiterer Plan allein genügt nicht.

## Zuerst lesen

Lies diese Dateien vollständig:

1. `docs/implementation/jenesis-jvm-a/SPECIFICATION.md`
2. `docs/implementation/jenesis-jvm-a/BUILD_PARITY_MATRIX.md`
3. `docs/implementation/jenesis-jvm-a/EDITOR_COMPATIBILITY.md`
4. `docs/implementation/jenesis-jvm-a/EXECUTION_PLAN.md`
5. `docs/implementation/jenesis-jvm-a/TEST_AND_EVIDENCE_POLICY.md`
6. `docs/implementation/jenesis-jvm-a/OPENCODE_REVIEW_PROMPT.md`

Lies die geltenden `AGENTS.md`-Dateien und die betroffenen aktuellen Build-/Qualitätsverträge. Der ausdrückliche Auftrag erlaubt für Auftrag A einen parallelen Jenesis-JVM-Build und ersetzt insoweit ältere „nur Gradle“-Regeln. Andere historische Aufträge, Projektpläne oder deren Startprompts werden dadurch nicht aktiviert.

## Ziel

Erreiche JVM-Buildparität mit Jenesis auf POM-Basis für diese zehn Module:

`interlis-model`, `interlis-compiler`, `interlis-workspace`, `interlis-repository`,
`interlis-projection`, `interlis-documentation`, `interlis-wasm-bridge`,
`interlis-compiler-cli`, `interlis-lsp`, `interlis-verification`.

Erhalte Produktquellen, APIs, Sichtbarkeiten, Testpackages und Produktversionen. Migriere ANTLR-Generierung, Java-Kompilierung, Ressourcen, Dependencies, JARs/Sources-JARs und die vorhandenen JVM-Tests. Erhalte die Trennung zwischen normalen Tests und expliziten Compiler-Contracts. Weise kleine reale SDK-/CLI-/LSP-Integrationen gegen **Jenesis-erzeugte** Artefakte nach.

Keine JPMS-Umstellung und keine neue `module-info.java` für eigene Produkt- oder Testmodule. Keine `--patch-module`-/Reflection-Umbauten der Tests. Keine **neue** `jlink`-/`jpackage`-/Native-Image-/APE-Integration im Core und kein neues Auslieferungsformat. Die vorhandene JRE-/jlink-/VSIX-Paketierung der Extension bleibt unverändert erhalten; sie wird nicht entfernt oder verdoppelt. Bestehende automatische Module-Path-Launcher bleiben funktional erhalten. Keine TeaVM-/GraalVM-Web-Image-/Browsermigration. `interlis-wasm-bridge` ist trotzdem im JVM-Scope.

Gradle bleibt lauffähige Referenz und bisheriger Gesamtbuild. Kein Entfernen der Java-Gradle-Konfiguration. Kein Wechsel des Wasm-Builds auf Jenesis-Artefakte. Diese Schritte benötigen eigene spätere Aufträge. Die frühere Bündelung in „Auftrag B“ ist nicht verbindlich: Nach A hat ein Compiler-APE-Prototyp Priorität; explizites JPMS ist dafür keine Voraussetzung und bleibt eine getrennte spätere Entscheidung.

Die neue JVM-Spur darf weder Gradle noch Maven als versteckten Ausführer verwenden. Fehlende Jenesis-Ergebnisse werden nicht aus alten Gradle-Ausgaben oder `~/.m2` ergänzt. Separat ausgewiesene Gradle-Referenzläufe und die Prüfung des bestehenden Extension-Stagingpfads sind erlaubt, aber keine Jenesis-Produzenten.

## VS-Code-Verbraucher und spätere Compiler-Auslieferung

`edigonzales/interlis-vscode` ist ein bestehender, nicht mitzumigrierender Verbraucher. Erhalte `gradlew editorBundle`, `build/editor-bundle`, den bisherigen `stage:core`-/F5-Pfad, das Java-Classpath-Startformat, die Hash-/Lockprüfung und die bestehende Runtime-Paketierung.

Ergänze einmalig einen isolierten Editor-Kompatibilitätsnachweis nach `EDITOR_COMPATIBILITY.md`: echte Jenesis-LSP-JARs in einem separaten Schattenbundle, bestehender unveränderter Extension-Verbraucher, echter kleiner Extension-Host-Smoke und Manipulationsgegenproben. Keine dauerhaften Änderungen oder Commits im Extension-Repository, keine vorhandenen Release-Locks überschreiben, kein automatischer Wechsel der normalen Extension auf Jenesis. Die Nachweise dürfen nicht bloss erneut Gradle-JARs testen.

Das spätere APE-Ziel ist **primär die Compiler-CLI**, nicht der LSP. A liefert dafür nur die normalen Compiler-JARs, Ressourcen und die vollständige gehashte Runtimeclosure. Kein CosmoGVM-/Cosmopolitan-Download, kein APE-Prototyp, keine Reflection-/Native-Konfiguration auf Verdacht und keine neue native LSP-Startart in diesem Auftrag.

## Vorgehen

Arbeite **A0 → A1 → A2 → A3 → A4 → A5**. Jede Phase muss ihr gezieltes Testgate erfolgreich passieren, bevor die nächste beginnt. Routineentscheidungen autonom treffen; nach einer grünen Phase nicht auf Benutzerfreigabe warten.

Erprobe in A0 zuerst die tatsächlichen Fähigkeiten des gepinnten Jenesis-Releases. Nimm nicht an, dass heutige Webdokumentation zum Release identisch ist. Bevorzuge die unveränderte Engine und vorhandene deklarative Funktionen. Nur kleine projektbezogene Adapter sind zulässig, kein neues Buildframework und kein Jenesis-Fork.

Verwende für Referenz-/Gegenproben getrennte Worktrees und für beide Buildspuren strikt getrennte Ausgaben. Ein `gradlew clean` darf weder die versionierte Jenesis-Engine noch eigene versionierte Buildquellen löschen. Diese Sicherheit ist **vor** dem ersten gemeinsamen Clean nachzuweisen.

## Tests, besonders bei Reviewkorrekturen

Wähle Tests nach geändertem Vertrag, Eingaben und Abhängigkeiten, nicht nach einem Ritual. Für die erste vollständige Buildparität müssen alle vorhandenen normalen JVM-Testfälle der zehn Module unter Jenesis mindestens einmal erfolgreich nachgewiesen werden. Das rechtfertigt nicht, nach jedem kleinen Fix wieder alle Tests auszuführen.

Nach jedem Reviewerfinding:

1. Betroffenen Vertrag, Code-/Konfigurations-/Inputänderungen und abhängige Verbraucher benennen.
2. Den konkreten Fehler mit einer Gegenprobe nachweisen, soweit sinnvoll.
3. Korrigieren.
4. Nur die direkt betroffenen Tests und erforderliche schmale Verbraucherregressionen ausführen.
5. Gültige unveränderte Nachweise mit ursprünglicher Herkunft wiederverwenden.
6. Neuen Kandidaten committen und erneut unabhängig reviewen lassen.

Ein Source-/Toolchain-/Classpathwechsel kann breitere Tests erfordern. Eine Dokumentationskorrektur, ein neuer SHA oder eine weitere Reviewrunde allein tut das nicht. Cachetreffer, Dry-runs und Validatorfixtures nicht als neu ausgeführte Produktprüfungen ausgeben. Keine künstlichen PASS-Dateien, Hashumschreibungen oder reduzierten Testmengen.

## Unabhängiger Review

**Kein unabhängiger Review nach jeder Phase.** Erst nach A5 und nach dem Commit des geprüften Kandidaten startest du selbständig den abschliessenden unabhängigen Gesamt-Review.

Verwende **OpenCode 2.x mit DeepSeek Flash 1017 über die direkte DeepSeek-API**. Prüfe die vorhandene Installation und Authentifizierung früh, aber starte den fachlichen Review erst nach allen Phasen. Vorhandene Anmeldung wiederverwenden; keinen zusätzlichen API-Key in ENV verlangen, falls nicht nötig. Keine Secrets auslesen oder protokollieren.

Der aktuell dokumentierte API-Alias `deepseek-flash` kann verwendet werden, wenn seine Zuordnung zu 1017 für den tatsächlichen Lauf belegt ist. Lokale Provider-ID, installierte Flags und Modellzuordnung prüfen, nicht erraten. Kein Modell-/Providerfallback.

Der Reviewer erhält den vollständigen Auftrag, den kumulativen Diff, den exakten Kandidaten und ursprüngliche Rohbelege in einem separaten unveränderlichen Arbeitsstand. Er prüft Zielerreichung und Code selbst und führt eigene gezielte Gegenproben aus. Er verändert keine Produktdateien und startet keine weiteren Agenten.

Bei berechtigten Findings korrigierst du selbst und wiederholst die Commit-/Review-Schleife bis zum gültigen PASS oder einem konkret belegten nicht selbst behebbaren Blocker. Der neue Review betrachtet weiterhin den Gesamtauftrag, muss aber unveränderte grosse Testläufe nicht wiederholen.

## Git, Nachweise und Abschluss

Erhalte fremde Änderungen. Kein Reset, ungezieltes Clean, blindes Staging, Push, Merge oder Release. Committe nur Änderungen dieses Auftrags. Zwischencommits sind erlaubt; vor dem unabhängigen Review muss der vollständige geprüfte Kandidat committet sein.

Halte Status und Testledger ehrlich aktuell. Alte Rohbelege behalten ihre SHA-/Dirty-/Toolchainangaben. Finale Reviewbelege extern speichern, damit ihr Ablegen keinen neuen ungeprüften Abschlusscommit erfordert. Nach PASS keine stillen Änderungen mehr.

Berichte am Ende den erfüllten Umfang, die tatsächlich verwendete Jenesis-Version, ausgeführte und begründet wiederverwendete Tests, bewusste Ausschlüsse, den finalen Commit und die Pfade zu unabhängigem `review.md`/`verdict.json`. Erfolg nur bei erfüllten JA-01 bis JA-36. Benenne zusätzlich den getesteten Extension-Commit, Runtime-Modus und Editor-Kompatibilitätsbericht. APE-/JPMS-Folgearbeiten sind nicht Teil dieser Abnahme.

Beginne jetzt mit A0 und führe Auftrag A bis zum geprüften, committeten und unabhängig validierten Ergebnis aus.
