# Auftrag A — Jenesis-JVM-Buildparität auf POM-Basis

**Finale Spezifikation · 4. November 2026 · Fassung 2**

Für `edigonzales/interlis-java`. Diese acht Dateien ersetzen die frühere Auftrag-A-Fassung vollständig. Es handelt sich nicht um ein zusätzlich parallel anzuwendendes Addendum. Bereits vorhandene echte Implementierungs-/Test-/Reviewbelege nicht löschen oder rückwirkend ändern; zusätzliche Anforderungen der finalen Fassung gezielt abgleichen.

## Start in Codex

Dieses Verzeichnis nach `docs/implementation/jenesis-jvm-a/` im Repository übernehmen. Das ZIP enthält diesen Pfad bereits.

```text
Lies docs/implementation/jenesis-jvm-a/CODEX_STARTPROMPT.md vollständig.
Führe Auftrag A nach der finalen Spezifikation aus. Codex ist Primäragent.
Arbeite A0 bis A5 phasenweise mit notwendigen erfolgreichen Tests ab.

Erhalte den bestehenden VS-Code-/Gradle-Stagingpfad und prüfe die
unveränderte Extension gezielt mit Jenesis-LSP-JARs in einem isolierten
Schattenbundle. Keine dauerhafte Extension-/Runtime-Migration.

Keine JPMS-, Wasm-, Native-Image- oder APE-Implementierung.
Compiler-APE bleibt ein separater Folgeauftrag, ohne vorgeschaltete JPMS-Pflicht.

Erst nach allen Phasen und dem Kandidatencommit folgt der autonome
unabhängige Gesamtreview mit OpenCode 2.x und DeepSeek Flash 1017.
Bei Findings selbst korrigieren, nur betroffene Tests und notwendige
Verbraucher erneut prüfen, neu committen und frisch unabhängig reviewen.
```

## Was die Finalisierung festlegt

| Thema | Endgültige Grenze für Auftrag A |
|---|---|
| Build | Zehn JVM-Module mit Jenesis/POMs; gleicher Produktcode, gleiche Runtime-Abhängigkeiten und vollständige Testparität. Gradle bleibt vorübergehend Referenz/Gesamtbuild. |
| Extension | Bestehender `stage:core`-/F5-/Editorbundlepfad bleibt. Zusätzlich ein kleiner echter Verbrauchercheck mit Jenesis-LSP-JARs; keine normale Umschaltung oder dauerhafte Änderung im Extensionprojekt. |
| Start und Runtime | Compiler-CLI behält ihren bisherigen automatischen Module-Path-Launcher; der LSP der Extension bleibt auf dem Classpath. Bestehende Extension-JRE-/jlink-Paketierung bleibt, kein neuer Runtimebuilder im Core. |
| APE und JPMS | APE ist vorrangig ein späteres Ziel der Compiler-CLI, nicht des LSP. In A keine native Toolchain oder Konfiguration. Eine vollständige JPMS-Migration ist weder Teil von A noch Pflicht vor dem späteren APE-Versuch. |
| Qualitätssicherung | Jede Phase wird getestet; unabhängiger Gesamt-Review erst am Ende. Korrekturen lösen nur die risikobasiert nötigen neuen Tests aus. |

## Dateien und Verbindlichkeit

| Datei | Zweck |
|---|---|
| [CODEX_STARTPROMPT.md](CODEX_STARTPROMPT.md) | Ausführbarer Implementierungsauftrag, Grenzen und autonomer Ablauf |
| [SPECIFICATION.md](SPECIFICATION.md) | Architektur, POMs, Klassen-/Methodenverträge und JA-01 bis JA-36 |
| [BUILD_PARITY_MATRIX.md](BUILD_PARITY_MATRIX.md) | Zuordnung der bestehenden Buildfunktionen und Verbraucher |
| [EDITOR_COMPATIBILITY.md](EDITOR_COMPATIBILITY.md) | Bestehender Extensionvertrag, isoliertes Schattenbundle, echte Verbraucherprüfung, Schreib-/Runtimegrenzen |
| [EXECUTION_PLAN.md](EXECUTION_PLAN.md) | Phasen A0–A5, gezielte Testgates und abschliessender Review R |
| [TEST_AND_EVIDENCE_POLICY.md](TEST_AND_EVIDENCE_POLICY.md) | Testidentitäten, Integrität, Wiederverwendung und Impactmatrix für Reviewfixes |
| [OPENCODE_REVIEW_PROMPT.md](OPENCODE_REVIEW_PROMPT.md) | Autonomer unabhängiger Prüfauftrag, Modell-/Commitbindung und Korrekturschleife |
| [README.md](README.md) | Verwendung, finale Grenzen und Quellenbasis |

Bei Auslegungskonflikten gilt: ausdrücklicher Nutzerumfang → SPECIFICATION → EDITOR_COMPATIBILITY für den Editorverbraucher → BUILD_PARITY_MATRIX → EXECUTION_PLAN → übrige Dokumente. Sicherheits-/Integritätsvorgaben und das Verbot erfundener Nachweise gelten durchgehend. Keines dieser Dokumente aktiviert einen Folgeauftrag.

## Was A liefert — und was danach offen bleibt

Jenesis baut die gleichen zehn JVM-Module anhand von POMs auf dem Classpath. Die bisherigen Testpackages und White-Box-Tests bleiben. Der bestehende Gradle-Build und seine Wasm-/Frontend-/P3-Aufgaben bleiben erhalten. Beide JVM-Spuren benutzen getrennte Ausgaben; die neue darf ihre Ergebnisse nicht verdeckt aus Gradle oder Maven beziehen. Ausdrücklich ausgewiesene Referenz-/Editor-Stagingläufe dürfen weiterhin Gradle verwenden, zählen aber nicht als Jenesis-Produzenten.

Die vorübergehende Doppelbeschreibung des JVM-Builds wird in A bewusst akzeptiert. Erst spätere Aufträge entscheiden über Entfernung des Gradle-JVM-Builds, Wasm-Artefaktschnittstelle, normale Umstellung der Extension oder explizite Java-Module. Die früher vorgeschlagene feste Reihenfolge eines grossen „Auftrags B“ ist **nicht** mehr verbindlich.

Priorität nach A: separat beauftragter **Compiler-APE-Machbarkeitsversuch**, dann bei Erfolg dessen abgesicherte Auslieferung. A liefert dafür lediglich die normalen Compiler-JARs und das vollständige gehashte Runtimeinventar. Keine CosmoGVM-/Cosmopolitan-Installation, keine APE-Tasks und kein Native-LSP auf Verdacht.

## Quellenbasis und Referenzen

**Corebasis:** `84fe4552099f791a40f726a7628f5878a1a665d4` (`fix: track all verification fixture inputs for incremental tests`), Grundlage der bisherigen analysierten Auftrag-A-Fassung. Sie ist kein Befehl zum Zurücksetzen neuerer lokaler Arbeit. Codex bestimmt in A0 seinen tatsächlichen `BASE_SHA` und berücksichtigt nachfolgende Änderungen.

**Extension-Verbraucher:** `a5b774a4d593ca5424212c8a733fe816106639f7`. Staging, Launchcode, Runtime-Linkkonfiguration und Extension-Host-Runner wurden für diese Finalisierung über den GitHub-Connector gelesen. Dies ist eine Kompatibilitätsreferenz, keine allgemeine Freigabe dieses Core-/Extensionpaars. Quellen und detaillierte Beobachtungen stehen in [EDITOR_COMPATIBILITY.md](EDITOR_COMPATIBILITY.md).

**Jenesis:** Startkandidat bleibt Release **0.18.1**, Tag `v0.18.1`, Commit `177014053cce311924f6724d4e4a7fe5689ed9b6` aus der vorangegangenen Releaseprüfung. Diese Finalisierung beauftragt kein Versionsupdate. A0 muss dessen erforderliche Fähigkeiten tatsächlich erproben. Laufende Webdokumentation kann dem Release voraus sein. Keine heimliche Verwendung von `main`, `latest` oder einer selbst gepatchten Engine.

**Reviewmodell:** Die am 4. November 2026 erneut gelesene direkte DeepSeek-Dokumentation ordnet `deepseek-flash` **DeepSeek-V4.1-Flash-1017** zu. Die lokale Providerkennung und die Zuordnung müssen unmittelbar vor dem echten Review erneut geprüft werden. Kein erfundener CLI-Modellname und kein stiller Modellfallback.

### Projektquellen

- [Core-Referenzcommit](https://github.com/edigonzales/interlis-java/commit/84fe4552099f791a40f726a7628f5878a1a665d4)
- [Verifikationsmodul](https://github.com/edigonzales/interlis-java/blob/84fe4552099f791a40f726a7628f5878a1a665d4/interlis-verification/build.gradle)
- [Contract-Orchestrierung](https://github.com/edigonzales/interlis-java/blob/84fe4552099f791a40f726a7628f5878a1a665d4/gradle/verification-contracts.gradle)
- [Coverage-/Ausführungsbindung](https://github.com/edigonzales/interlis-java/blob/84fe4552099f791a40f726a7628f5878a1a665d4/interlis-verification/src/test/java/guru/interlis/verification/contracts/CompilerCoverageContractTest.java)
- [CLI-Build und Launcher](https://github.com/edigonzales/interlis-java/blob/84fe4552099f791a40f726a7628f5878a1a665d4/interlis-compiler-cli/build.gradle)
- [Bestehendes Reviewmuster](https://github.com/edigonzales/interlis-java/blob/84fe4552099f791a40f726a7628f5878a1a665d4/docs/implementation/http-network-policy/review/run-review.cjs)

### Primärdokumentation für A0 und Reviewpreflight

- [Jenesis: Migration](https://jenesis.build/tool/migrating/)
- [Installation](https://jenesis.build/tool/getting-started/), [Release 0.18.1](https://github.com/jenesis/jenesis/releases/tag/v0.18.1)
- [Konfiguration](https://jenesis.build/tool/configuration/), [Build und Ausführung](https://jenesis.build/tool/building-and-running/)
- [Sourcegenerierung](https://jenesis.build/tool/generating-sources/), [Pinning](https://jenesis.build/tool/pinning/)
- [Tests](https://jenesis.build/tool/code-quality-and-testing/), [Erweiterungen](https://jenesis.build/tool/extending-the-build/), [Discovery](https://jenesis.build/tool/core-concepts/)
- [OpenCode-v2-Kommandos](https://opencode.ai/v2/docs/cli/commands/), [Berechtigungen](https://opencode.ai/v2/docs/permissions/)
- [DeepSeek-Modellzuordnung](https://api-docs.deepseek.com/quick_start/pricing/)

Die laufenden Jenesis-Migrations-/Packagingseiten waren bei der finalen Nachprüfung nicht erneut abrufbar. Deshalb bleiben die vorhandenen Releasebindungen und der praktische Capability-Nachweis in A0 massgeblich; es wurden daraus keine neuen unbestätigten Enginefunktionen verbindlich abgeleitet. Die neuen Projektadapter sind Implementierungsvorgaben, keine Behauptung eingebauter Jenesis-APIs.

## Zu erzeugende Nachweise und Grenzen dieses Pakets

Codex erzeugt im Verlauf `STATUS.md`, `BASELINE.md`, `CAPABILITY_REPORT.md`, `PARITY_REPORT.md`, `EDITOR_COMPATIBILITY_REPORT.md` und `TEST_LEDGER.md` in diesem Auftragsverzeichnis. Sie enthalten tatsächliche Entscheidungen und Ergebnisse, keine vorgefüllten Erfolgsmeldungen. Rohlogs und finale Reviewurteile liegen run-/kandidatenbezogen ausserhalb des versionierten Produktbaums oder in passenden ignorierten Ausgaben; finale Reviewbelege überleben `clean` und erfordern keinen Nachtragscommit.

Nach A5 wird der geprüfte Gesamtkandidat committet. Erst danach erfolgt der autonome unabhängige Gesamt-Review durch OpenCode 2.x/DeepSeek Flash 1017. Findings behebt Codex, prüft gezielt die betroffenen Verträge, committet neu und startet einen frischen Review. Nach einem gültigen PASS keine stillen Produkt-/Dokumentationsänderungen mehr.

Dieses Paket enthält **keine ausgeführte Repositorymigration, keinen Jenesis-Produktbuild, keinen Extension-Host-Testlauf und keinen unabhängigen Code-PASS**. Ein älterer offener Verification-Review wird dadurch nicht nachträglich für bestanden erklärt. Dokumentprüfung und Paketprüfung ersetzen diese Implementierungsnachweise nicht.
