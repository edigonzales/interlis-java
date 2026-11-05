# Compiler-APE — Machbarkeitsauftrag für Codex

**Fassung:** 4. November 2026. **Auftrag:** `compiler-ape-feasibility`.
**Primäragent:** Codex. **Unabhängiger Reviewer:** OpenCode 2.x / DeepSeek Flash 1017 über die direkte DeepSeek-API.

## Ziel und Abgrenzung

Prüfe anhand eines echten Prototyps, ob die bestehende INTERLIS-Compiler-CLI aus Jenesis-JARs als Actually Portable Executable (APE) mit einer gepinnten Cosmopolitan-fähigen GraalVM-Toolchain gebaut und sinnvoll ohne Java-Installation verteilt werden kann. Das Ziel ist primär **der Compiler**, nicht der LSP.

Eine erfolgreich gebaute Linux-Datei und ein funktionierendes `version` genügen nicht. Untersucht werden Compiler-/CLI-Verhalten, Formatierung, Ressourcen, Repositoryzugriff, TLS, Dateisystem und die Ausführung desselben kombinierten Artefakts auf Linux x64, Windows x64 und macOS arm64. Es ist eine Machbarkeitsabnahme, keine Produktionsfreigabe.

Jenesis-Auftrag A wird verwendet, nicht wiederholt. Keine JPMS-Migration, keine Wasm-/Web-Image-Migration, keine Extensionmigration und keine Arbeit am besprochenen Solothurn-Formatterstil. Der bestehende Formatter wird lediglich als CLI-Funktion geprüft.

## Einordnung der gelesenen Ausgangslage

Die Recherche beruht auf `edigonzales/interlis-java` Commit **`4c5e133400da4643a33976c19fb7ab7f18d89cfb`** (`Add pinned Jenesis JVM build parity and isolated editor compatibility checks`). Dort existieren insbesondere:

- `build/InterlisJvmBuild.java`: expliziter Projekteinstieg für `build`, `contracts`, `sdk-check`, `parity`, `editor-check`.
- `build/interlis/JenesisBridge.java`: tatsächliche Engine-Ergebnisse und Runtime-Classpaths.
- `build/interlis/ModuleArtifacts.java`: identifizierte, gehashte Runtime-Artefakte.
- `build/interlis/JvmBuildInputs.java`: Quell-/Inputfingerprints und Schutz der unveränderten Jenesis-Engine.
- `build/interlis/ExistingJvmDistribution.java`: bisherige JVM-Distributionen; der APE-Auftrag soll nicht unnötig auch LSP/SDK paketieren.

Die eingecheckte `docs/implementation/jenesis-jvm-a/STATUS.md` beschreibt die lokalen Phasen A0–A5 als abgeschlossen, den externen finalen Review aber noch nicht als nachgewiesen. Das kann wegen extern aufbewahrter Reviewbelege korrekt sein. Codex soll den tatsächlichen lokalen Abschluss feststellen, keinen PASS erfinden und keine pauschale neue Abnahme von A starten. Ein konkret relevanter offener Defekt muss vor seiner Nutzung geklärt werden.

Neuere lokale Produktstände werden bewahrt und als tatsächlicher `BASE_SHA` erfasst. Die obige SHA ist eine Recherchebasis, kein Resetauftrag.

## Dokumente

| Datei | Zweck |
|---|---|
| [CODEX_STARTPROMPT.md](CODEX_STARTPROMPT.md) | Unmittelbarer Implementierungsauftrag und autonome Arbeitsregeln |
| [SPECIFICATION.md](SPECIFICATION.md) | Architektur bis auf Klassen-/Methodenebene und 32 Abnahmekriterien |
| [TOOLCHAIN_AND_PORTABILITY.md](TOOLCHAIN_AND_PORTABILITY.md) | Konkrete Toolchain-Referenz, Zwei-Architektur-APE, Loader, TLS und Grenzen |
| [EXECUTION_PLAN.md](EXECUTION_PLAN.md) | Phasen F0–F5, erfolgreiche Gates, begründete negative Abschlusszweige |
| [TEST_MATRIX.md](TEST_MATRIX.md) | Kleine feste CLI-/Netzwerk-/Plattformmatrix und Gegenproben |
| [TEST_AND_EVIDENCE_POLICY.md](TEST_AND_EVIDENCE_POLICY.md) | Testökonomie, Cache-/Artefaktbindung, Wiederverwendung und Urteilsregeln |
| [OPENCODE_REVIEW_PROMPT.md](OPENCODE_REVIEW_PROMPT.md) | Autonomer abschliessender Gesamt-Review mit Korrektur-/Review-Schleife |

Das beiliegende `SHA256SUMS.txt` bindet die acht Spezifikationsdateien dieses Pakets; es ist kein Produkt-/APE-Nachweis.

Operative Ergebnisdateien wie `STATUS.md`, `BASELINE.md`, `TOOLCHAIN_REPORT.md`, `TEST_LEDGER.md` und `FEASIBILITY_REPORT.md` erzeugt Codex während der Umsetzung. Sie sind hier absichtlich nicht mit angenommenen Ergebnissen vorgefüllt. Rohbelege und finale Reviewerurteile bleiben ausserhalb des versionierten Produktbaums.

## Start in Codex

```text
Lies docs/implementation/compiler-ape-feasibility/CODEX_STARTPROMPT.md vollständig
und führe den Compiler-APE-Machbarkeitsauftrag aus.

Nutze den implementierten Jenesis-JVM-Build aus Auftrag A. Arbeite F0–F5 mit
gezielten erfolgreichen Gates. Kein JPMS-, LSP-, Extension-, Wasm- oder
Formatter-Stilumbau. Kein eigener GraalVM-/Cosmopolitan-Fork.

Committe den geprüften Kandidaten und starte erst danach den autonomen
unabhängigen Gesamt-Review mit OpenCode 2.x und DeepSeek Flash 1017.
Bei Findings: korrigieren, nur notwendige Tests, neuer Commit, frischer Review.
Unterscheide eine saubere Machbarkeitsuntersuchung ausdrücklich von einem
positiven Portabilitätsnachweis oder einer Produktionsfreigabe.
```

## Quellenbasis

Gelesen am 4. November 2026; zukünftige Toolversionen nicht still übernehmen:

- [Core-Recherchecommit](https://github.com/edigonzales/interlis-java/commit/4c5e133400da4643a33976c19fb7ab7f18d89cfb)
- [JenesisBridge](https://github.com/edigonzales/interlis-java/blob/4c5e133400da4643a33976c19fb7ab7f18d89cfb/build/interlis/JenesisBridge.java)
- [ModuleArtifacts](https://github.com/edigonzales/interlis-java/blob/4c5e133400da4643a33976c19fb7ab7f18d89cfb/build/interlis/ModuleArtifacts.java)
- [CLI Main](https://github.com/edigonzales/interlis-java/blob/4c5e133400da4643a33976c19fb7ab7f18d89cfb/interlis-compiler-cli/src/main/java/guru/interlis/cli/Main.java)
- [Madura-Referenzstand](https://github.com/elide-dev/madura/tree/88cd910650f2e0db4968c0ad6c38089a8ef6b78d)
- [Madura Buildablauf / apelink](https://github.com/elide-dev/madura/blob/88cd910650f2e0db4968c0ad6c38089a8ef6b78d/.github/workflows/job.build.yml)
- [Madura Native-Image-Konfiguration](https://github.com/elide-dev/madura/blob/88cd910650f2e0db4968c0ad6c38089a8ef6b78d/elide.pkl)
- [Madura CosmoGVM-Pin](https://github.com/elide-dev/madura/blob/88cd910650f2e0db4968c0ad6c38089a8ef6b78d/scripts/setup-graalvm.sh)
- [Madura cosmocc-Pin](https://github.com/elide-dev/madura/blob/88cd910650f2e0db4968c0ad6c38089a8ef6b78d/scripts/setup-cosmocc.sh)
- [Cosmopolitan / Startbesonderheiten](https://www.justine.lol/cosmopolitan/)
- [APE Loader](https://justine.lol/apeloader/)
- [GraalVM Native Image](https://www.graalvm.org/latest/reference-manual/native-image/)
- [GraalVM Reachability Metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/)
- [GraalVM Zertifikatsverwaltung](https://www.graalvm.org/dev/reference-manual/native-image/dynamic-features/CertificateManagement/)
- [Jenesis Referenz](https://jenesis.build/tool/reference/)
- [OpenCode 2.x CLI](https://opencode.ai/v2/docs/cli/commands/)
- [OpenCode 2.x Berechtigungen](https://opencode.ai/v2/docs/permissions/)
- [DeepSeek Modellzuordnung](https://api-docs.deepseek.com/quick_start/pricing/)

Die Quellen belegen Ansatz und Ausgangscode, **keinen bereits ausgeführten INTERLIS-APE-Build**. Die beim Madura-Projekt veröffentlichten Archivhashes wurden hier gelesen; die Archive wurden für dieses Dokumentpaket nicht heruntergeladen, ausgeführt oder unabhängig vermessen. Aussagen zu Lizenzen sind Inventarisierungsaufträge, keine pauschale rechtliche Freigabe.
