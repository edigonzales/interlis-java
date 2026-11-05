# Auftrag B — Jenesis als führender JVM-Build und explizite Java-Module

**Spezifikationsstand: 5. November 2026. Primäragent: Codex.**

Dieses Paket beauftragt **den ganzen Auftrag B**, in zwei aufeinanderfolgenden Abschnitten: **B-I: Buildbesitz/Artefaktverbraucher bereinigen** und **B-II: JPMS, modulare Starts und Auslieferungsprüfung**. B-II ist kein automatisch vertagtes optionales Epic. B-I erhält einen eigenen erfolgreich geprüften Zwischenstand; der Gesamtauftrag ist erst nach B-II und dem unabhängigen Gesamt-Review abgeschlossen.

Die Compiler-APE-Machbarkeit und der lokale CosmoGVM-Uhrpatch sind **pausiert**. Sie werden weder fortgesetzt noch verworfen. Es gibt in B keinen Native-Build, keine Cosmo-Toolchainbeschaffung und keine neue Runtime-Substitution. Änderungen an normalen modularen JVM-JARs sind erlaubt und erwartbar; ein künftiger APE-Auftrag muss deren neue Identität ausdrücklich übernehmen.

## Ausgangsstände

| Repository | Gelesener Stand | Rolle |
|---|---|---|
| `edigonzales/interlis-java` | `main`, `2a2b0272761caa2b65d559c07909970f4a1290ff` | Core, Jenesis-Auftrag A, Verification, JVM-/Wasm-Builds |
| `edigonzales/interlis-vscode` | `main`, `a5b774a4d593ca5424212c8a733fe816106639f7` | tatsächlicher Editorverbraucher, Staging, Start, Runtime/VSIX |
| Jenesis, in A gebunden | `0.18.1`, `177014053cce311924f6724d4e4a7fe5689ed9b6` | zunächst unveränderte Engine |
| Pausierte APE-Untersuchung | zuletzt besprochen `codex/compiler-ape-feasibility`, `d9f2a2f9b55ff278fb53aa1b3c28bdb367647d87` | nicht als B-Ausgangsbranch voraussetzen oder mergen |

Die SHAs sind Lesereferenzen, keine Anweisung für Reset oder Checkout über fremde Änderungen. Codex ermittelt und protokolliert die tatsächlichen lokalen Baselines beider Arbeitskopien. Quellen und Grenzen der Untersuchung stehen in [SOURCES.md](SOURCES.md). Aktuelle Anweisungen in diesem Paket übersteuern ausschliesslich die für B ausdrücklich geänderten Übergangsregeln von A.

## Ergebnis

- Genau ein aktiver Produzent für die zehn JVM-Projekte: die unveränderte gepinnte Jenesis-Engine mit den notwendigen kleinen Projektadaptern.
- Gradle bleibt für die zwei **Backendprojekte** TeaVM und GraalVM Web Image. Deren eigene Entry-Point-/Adapterquellen darf Gradle weiterhin kompilieren, aber nicht nochmals Compiler, Model, Workspace, Projection oder Bridge aus Quellen.
- JVM-, Editor- und Wasm-Verbraucher nutzen explizite, gehashte Artefaktübergaben; kein `mavenLocal()`, keine Auswahl irgendeiner zuletzt erzeugten JAR.
- Alle zehn eigenen JVM-Projekte erhalten explizite `module-info.java`. Auslieferung bleibt kompatibel mit Classpath-Verbrauchern, soweit deren bestehender Vertrag dies verlangt.
- CLI und LSP starten regulär als benannte Module. Die Extension bleibt JVM-basiert, mit ihrer vorhandenen Runtime-Paketierung; kein nativer LSP.
- White-Box-Tests verlieren keine Assertions und erzwingen keine öffentlichen Compilerinternas. Ein enger, im Jenesis-Build registrierter Testadapter darf JDK-`--patch-module` verwenden. Das ist **Testkonfiguration**, kein Vendorpatch.
- Bestehende Release-/Evidence-Garantien bleiben erhalten. TeaVM-Bytegleichheit wird nicht wieder eingeführt.
- Kleine tatsächliche `jlink`-/`jpackage`-Eignungsprüfungen, ohne neue Installer-/Publishingplattform oder Fremdbibliotheks-Forks.

## Dokumente und Lesereihenfolge

1. [CODEX_STARTPROMPT.md](CODEX_STARTPROMPT.md) — unmittelbar ausführbarer Auftrag.
2. [SPECIFICATION.md](SPECIFICATION.md) — Umfang, Architektur und JB-01 bis JB-40.
3. [BUILD_OWNERSHIP_AND_TASKS.md](BUILD_OWNERSHIP_AND_TASKS.md) — Taskmigration und Löschregeln.
4. [ARTIFACT_HANDOFF.md](ARTIFACT_HANDOFF.md) — JVM/Wasm/Editor-Verträge und Integritätsgrenzen.
5. [JPMS_AND_TESTING.md](JPMS_AND_TESTING.md) — Modulgraph, Schnittstellen, Testmechanik.
6. [EDITOR_AND_RUNTIME.md](EDITOR_AND_RUNTIME.md) — zweites Repository, Start- und Paketierungskontrakte.
7. [EXECUTION_PLAN.md](EXECUTION_PLAN.md) — B0 bis B7, dann R.
8. [CI_AND_RELEASE.md](CI_AND_RELEASE.md) — CI-Topologie, Conformance und fail-closed Releaseverträge.
9. [TEST_AND_EVIDENCE_POLICY.md](TEST_AND_EVIDENCE_POLICY.md) — selektive Tests und Belegwiederverwendung.
10. [OPENCODE_REVIEW_PROMPT.md](OPENCODE_REVIEW_PROMPT.md) — autonomer unabhängiger Review.
11. [SOURCES.md](SOURCES.md) — Primärquellen und untersuchter Zustand.

Nach dem Entpacken nur diese Spezdateien unter `docs/implementation/jenesis-migration-b/` ablegen. Vorhandene Status-/Testbelege nicht mit Vorlagen überschreiben. Die Implementierung erstellt dort bei Bedarf **BASELINE.md, DECISIONS.md, STATUS.md, TEST_LEDGER.md und CRITERIA_EVIDENCE.md**; Rohbelege und Reviewerurteile bleiben in einem separaten, Clean-sicheren Belegverzeichnis. Keine leeren Scheinergebnisse vorab anlegen.

## Start in Codex

```text
Lies docs/implementation/jenesis-migration-b/CODEX_STARTPROMPT.md vollständig
und führe Auftrag B aus. B-I und B-II gehören beide zum Auftrag.

Arbeite B0 bis B7 mit erfolgreichen gezielten Phasengates ab. Jenesis wird
der alleinige JVM-Builder; Gradle bleibt nur für die zwei Wasm-Backends.
Migriere danach die eigenen Module und regulären CLI-/LSP-Starts auf JPMS.
Beziehe interlis-vscode als echten Verbraucher ein. APE/Uhrpatch bleiben pausiert.

Nach allen Phasen: geprüfte Kandidaten beider Repositories lokal committen,
dann autonomer unabhängiger Gesamt-Review mit OpenCode 2.x und DeepSeek Flash 1017.
Findings korrigieren, nur betroffene Verträge/Verbraucher nachtesten,
neue Kandidaten committen und frisch unabhängig reviewen. Kein Push oder Release.
```

**Dieses Paket enthält eine Spezifikation, keine bereits ausgeführten Builds, Tests oder Code-Reviews.**
