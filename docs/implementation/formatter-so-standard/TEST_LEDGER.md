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

| Phase | Belege | Beobachtung |
| --- | --- | --- |
| F2a | `f2a/compiler-gate.log`, `f2a/gate-junit.xml` | 16 Tests, keine Skips. Ausdrucksgolden mit geschachteltem NOT und Funktionsaufruf, beide Prozentvergleiche, Codepoint-Breitenschwelle. Der erste als flach vorgesehene Kurzfall war über 40 Spalten; der kurze Fall verwendet nun den unbenannten Kopf. |
| F2b | `f2b/compiler.log`, `f2b/gate-junit.xml`, `f2b/corpus.log`, `f2b/second.log`, `f2b/diffs/` | 19 Tests, keine Skips. Alle fünf Familien, beide SET-Kopfformen und UNIQUE WHERE/LOCAL/BASKET. Waldplan und Naturgefahren strikt reparsbar und idempotent. |

## F3

- `f3/compiler-final-coverage.log` und `f3/interlis-compiler-junit.xml`
  enthalten 26 gezielte Tests. Der zweite Teil des Fixturetests fügte dem
  Deklarationsgolden einen Kommentar hinzu. Jenesis führte die Testklasse
  erneut aus und der exakte Vergleich schlug wie erwartet fehl.
  Das wiederhergestellte Golden entspricht bytegenau der vorab geprüften
  Datei. Der Folgelauf durfte dieses Resultat wiederverwenden.
- `f3/cli-final.log`, `f3/lsp-stable.log`, `f3/version-final.log` und die
  zugehörigen JUnitberichte belegen die Host- und Versionsverträge.
  Der ursprüngliche LSP-Lauf erkannte eine währenddessen bearbeitete
  Dokumentationsdatei im Inputbereich. Die Tests hatten ausgeführt.
  Der stabile Lauf übernahm das zu den gleichen Testinputs gehörende
  Resultat und endete mit Exit 0.
- `f3/host-smoke.log`, `f3/lsp.jsonl`, `f3/lsp-cancel.jsonl` und
  `f3/runtime-inventory.json` belegen die echten modulbasierten Prozesse.
- `f3/corpus-default/corpus-report.json` und
  `f3/corpus-narrow/corpus-report.json` erfassen acht Originale bei
  Breite 120 bzw. 40. Beide Läufe haben je acht erfolgreiche Reparses
  und Wiederholungsvergleiche, ohne Schutzhinweise im Korpus.
- Der damalige Test des Versionskonsumenten umfasste 81 Tests ohne Fehler
  oder Skips.

## Korrektur nach Reviewrunde 01

OpenCode meldete am Kandidaten `68e24bd` vier Findings. Die Korrektur
fasst UNIT-/CONTEXT-Folgedeklarationen zusammen, gibt SIGN-BASKET-Gruppen
und GRAPHIC-Zeichenklauseln eigene Layoutgrenzen, unterscheidet
Einheitenklammern von Pfadindizes und verschiebt gemischte mehrzeilige
Blockkommentare starr. Zwei neue Sollfixtures und eine Verifiermutation
prüfen die korrigierten Fälle.

Wegen der gemeinsamen Layout- und Schutzregeln wurden alle 29
Formattertests und der ausgewählte Korpus bei 120 und 40 erneut geprüft.
`fix01/junit.xml`, `fix01/corpus-default/` und `fix01/corpus-narrow/`
enthalten die aktuellen Nachweise. Die neue Modulintegration über SDK,
CLI und LSP prüft das Strukturfixture auf Bytegleichheit und liegt in
`fix01/host-smoke.log`. Die CLI-Schreib-/Fehlerpfade, LSP-Schedulerpfade,
Versionsprüfung und Release-Konsumenten stammen aus F3, da deren
Adapter- und Testquellen bei dieser Korrektur identisch sind.
