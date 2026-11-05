# Compiler-APE — lokaler CosmoGVM-Uhrpatch

**Ausführbarer Folgeauftrag für Codex · Fassung 1.0 · 5. November 2026**

Dieses Paket beauftragt die Implementierung eines eng begrenzten lokalen Patches für den nachgewiesenen CosmoGVM-Fehler in `System.currentTimeMillis()`. Es ist kein erneuter Jenesis-Auftrag, keine vollständige GraalVM-Portierung und keine Wiederholung der bisherigen APE-Machbarkeitsuntersuchung.

## Ziel

Die Compiler-CLI soll mit korrekter Kalenderzeit als APE laufen und dadurch die bisher blockierten sicheren HTTP-/HTTPS-Prüfungen fortsetzen können. Der Patch darf ausschliesslich die fehlerhafte Clockauswahl in der Cosmo-Implementierung von `System.currentTimeMillis()` sowie deren zugehörigen Fehlertext korrigieren. `nanoTime()`, Zertifikatsprüfung, Compilersemantik und normale JVM-Builds bleiben unverändert.

**Primäragent:** Codex. **Unabhängiger Reviewer:** OpenCode 2.x mit DeepSeek Flash 1017 über die direkte DeepSeek-API. Codex prüft jede Implementierungsphase. Der unabhängige Gesamt-Review beginnt erst nach allen Phasen und dem Kandidatencommit. Berechtigte Findings führen zu Korrektur, gezielten Tests, neuem Commit und frischem Review.

## Ausgangspunkt

Untersucht wurde `edigonzales/interlis-java`, Branch `codex/compiler-ape-feasibility`, Commit:

```text
 d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87
```

Der tatsächliche lokale Ausgangsstand ist bei Beginn neu festzustellen; lokale neuere Arbeiten bleiben erhalten. Kein Reset auf diesen Referenzcommit.

Vorhanden sind der Jenesis-Compilerinputpfad, sieben Produkt-JARs, die optionale `ape`-Orchestrierung, Native-Build/Fusion, Loaderpaketierung, gezielte CLI-/Netzwerktests, Originalbelege und die Clock-/HTTP-Minimalreproducer. Die bisherigen Laufresultate sind Projektbelege, keine mit diesem Dokument neu ausgeführten Tests. Details und Quellen: [SOURCES.md](SOURCES.md).

## Die Entscheidung über den Patchweg

**Route A ist bevorzugt:** Unveränderte Vendor-Distribution plus separates Kompatibilitäts-JAR. Die einzelne fehlerhafte Vendor-Substitution wird beim Native-Build deaktiviert und durch eine eng gleichartige Implementierung mit `CLOCK_REALTIME` ersetzt.

**Route B ist bedingt autorisiert:** Nur wenn Route A am exakten Vendorbinary nach begrenzter Ursachenklärung technisch nicht sauber funktioniert, darf eine isolierte, inventarisierte Ableitung des Builder-JARs dieselbe Korrektur an genau einer Methode vornehmen. Kein vollständiger Toolchainfork, keine Signaturentfernung, keine zusätzlichen Runtimefixes.

Es bleibt am Ende **genau ein aktiver Patchweg**. Kein automatischer Fallback zwischen beiden Varianten, keine dauerhafte Wahlmatrix und kein allgemeines Patchframework. Fehlversuche werden als Belege erhalten, nicht als alternative aktive Implementierungen.

Die methodengenaue Deaktivierung ist im untersuchten öffentlichen Graal-Quellcode erkennbar. Ihre konkrete Verfügbarkeit und sichere Verwendung in CosmoGVM `25.2.4-dev` muss Codex zuerst am gepinnten Binary nachweisen. Eine angenommene CLI-Option ist kein Implementierungserfolg.

## Umfangsgrenzen

Der Auftrag ersetzt das frühere lokale Substitutions-/Vendorpatchverbot **nur für diese Uhrkorrektur**. Die übrigen Grenzen aus `compiler-ape-feasibility` gelten weiter: keine TLS-Abschwächung, kein Produktsemantikfix, keine JPMS-/Wasm-/LSP-/Extensionmigration, kein Toolchainupgrade auf Verdacht, kein Push, kein Release, keine Nachricht an Upstream.

Die vorhandene PollSelectorProvider-Konfiguration bleibt getrennt erhalten. Der bekannte macOS-Loaderweg wird nicht in eine neue Ein-Datei-Auslieferung umgebaut. Fehlende native Windows-/Linuxrunner werden nicht durch Emulation ersetzt oder als bestanden verbucht.

## Dateien und Lesereihenfolge

| Datei | Zweck |
|---|---|
| [CODEX_STARTPROMPT.md](CODEX_STARTPROMPT.md) | Gesamter Implementierungsauftrag und Autonomieregeln |
| [SPECIFICATION.md](SPECIFICATION.md) | Verbindliche Architektur, Schnittstellen und CP-01 bis CP-32 |
| [PATCH_DESIGN.md](PATCH_DESIGN.md) | Klassen-/Methodenverträge, Route A und begrenzte Alternative B |
| [EXECUTION_PLAN.md](EXECUTION_PLAN.md) | C0–C5 mit erfolgreichen Phasengates; danach R |
| [TEST_MATRIX.md](TEST_MATRIX.md) | Kleine verbindliche Guard-, Clock-, TLS-, Compiler- und Plattformfälle |
| [TEST_AND_EVIDENCE_POLICY.md](TEST_AND_EVIDENCE_POLICY.md) | Belegbindung, Wiederverwendung, Timeouts und minimale Retests |
| [OPENCODE_REVIEW_PROMPT.md](OPENCODE_REVIEW_PROMPT.md) | Autonomer unabhängiger Review samt Fix-/Review-Schleife |
| [SOURCES.md](SOURCES.md) | Untersuchte Primärquellen, Pins und Grenzen des Vorwissens |

Bei Widersprüchen gilt: aktueller Nutzerauftrag → die engen Scope- und Sicherheitsgrenzen in SPECIFICATION → PATCH_DESIGN → EXECUTION_PLAN → Prüf-/Reviewdetails. Frühere Spezifikationen bleiben für unveränderte Verträge relevant. Konflikte konsistent auflösen, nicht die angenehmste Auslegung wählen.

## Ausführung

In das Repository entpacken. Der neue Zielordner lautet:

```text
 docs/implementation/compiler-ape-clock-patch/
```

Keine alten Machbarkeitsberichte, Logs oder Reviewurteile überschreiben. Die vorliegende Fassung ist die Auftragsquelle, kein ausgeführter Statusbericht. Codex legt später in diesem Ordner kurze operative Dateien `STATUS.md`, `TEST_LEDGER.md` und `RESULT.md` an; umfangreiche Rohbelege bleiben in einer separaten ignorierten Ablage.

Startnachricht:

```text
Lies docs/implementation/compiler-ape-clock-patch/CODEX_STARTPROMPT.md vollständig
und führe den Folgeauftrag aus. Nutze den vorhandenen APE-Branch und die echten
Jenesis-Compilerartefakte. Implementiere nur den eng erlaubten Clock-Patch.
Prüfe C0 bis C5 phasenweise. Erst danach: geprüfter Kandidatencommit und autonomer
Gesamt-Review mit OpenCode 2.x / DeepSeek Flash 1017. Findings korrigieren, nur
betroffene Verträge nachtesten, neu committen und frisch unabhängig reviewen.
Trenne nachgewiesenen Patch, Compilerfunktion und vollständige Plattformfreigabe.
```

## Ergebnisbegriffe

`patchStatus` beschreibt ausschliesslich die Korrektur: `VALIDATED_ON_AVAILABLE_HOSTS`, `FAILED` oder `BLOCKED`.

`compilerApeStatus` beschreibt den Auslieferungsanspruch: `GO`, `GO_WITH_LIMITATIONS`, `NO_GO` oder `INCONCLUSIVE`. Ein funktionierender lokaler Patch mit fehlendem nativem Windowsnachweis ist kein vollständiges GO.

`reviewVerdict` ist separat `PASS`, `FAIL` oder `BLOCKED`. Ein PASS der sachlich korrekten Untersuchung ist keine Produktionsfreigabe und überschreibt keine nicht ausgeführten Produktfälle.
