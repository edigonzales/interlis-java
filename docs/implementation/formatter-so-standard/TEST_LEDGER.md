# Gezielte Formattergates

Rohbelege liegen im Geschwisterverzeichnis `interlis-java-formatter-evidence`.
Die Phasenläufe vor den jeweiligen Commits prüfen den zu diesem Zeitpunkt
vorliegenden Arbeitsbaum. Frühere fehlgeschlagene Test- und Kompilierläufe
bleiben separat erhalten.

| Phase | Belege | Beobachtung |
| --- | --- | --- |
| F0 | `f0/compiler-baseline.log`, `f0/baseline-junit.xml`, `f0/corpus-baseline.log`, `f0/negative.log`, `f0/diffs/` | 7 Tests, 8 akzeptierte Originale, drei abgelehnte Negativfälle |
| F1a | `f1a/compiler-contract.log`, `f1a/contract-junit.xml` | 12 Tests, darunter APIbreite, Verifiermutationen, Cancellation und Parallelität |
| F1b | `f1b/compiler-gate.log`, `f1b/gate-junit.xml`, `f1b/diffs/` | 14 Tests, Deklarationsgolden und EOL-/Wertschutz; ausgewählte AGI-, AFU- und AWJF-Diffs |
| F1c | `f1c/corpus.log`, `f1c/second.log`, `f1c/diffs/`, `f1c/host-smoke.log`, `f1c/lsp.jsonl`, `f1c/lsp-junit.xml`, `f1c/cli-gate-stable.log`, `f1c/cli-junit.xml` | Alle acht Originale akzeptiert und idempotent; identischer SDK-/CLI-/LSP-Text bei vier Spaces und Breite 40; echte modulare Produkte |

Compilerselektor `.*Formatter.*Test`. CLIselektor
`.*(CliApplicationTest|P3AtomicOutputContractTest|P3CliBoundaryContractTest|P3ConfigurationContractTest|ColorOutputContractTest)`.
LSPselektor
`.*(LspProcessTest|LspFeatureTest|P3DocumentSynchronizationTest|P3AnalysisSchedulingTest|RepositoryEditorTest)`.
Alle über `java build/InterlisJvmBuild.java build --module … --tests …`.

Die modulare Launcherprobe verwendet `sdk-package` zur Artefakterstellung
und startet separat CLI, LSP und einen benannten Consumer gegen die erzeugten
JARs. Der Korpuslauf verwendet die strikte Parserprobe und den Produktverifier.
Vollständige Constraint-Goldens und unabhängiges Orakel für den ganzen Korpus
sind Bestandteil der folgenden Phasen.
