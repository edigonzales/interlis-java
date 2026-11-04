# Codex-Paket: `interlis-verification`

## Verwendung

Die Dateien dieses Verzeichnisses nach `docs/implementation/verification-module/` im lokalen Repository `edigonzales/interlis-java` übernehmen. Das ZIP enthält diesen Verzeichnispfad bereits.

In Codex folgenden Einstieg verwenden:

```text
Lies docs/implementation/verification-module/CODEX_STARTPROMPT.md vollständig.
Führe den darin beschriebenen Auftrag aus: Migration der drei zusätzlichen
CLI-Source-Sets in eine bereinigte Verifikationsstruktur, ausdrücklich noch
ohne Jenesis. Arbeite V0 bis V4 mit gezielten erfolgreichen Phasentests.
Starte den autonomen unabhängigen OpenCode-/DeepSeek-Flash-1017-Gesamtreview
erst nach allen Implementierungsphasen und dem Kandidatencommit. Behebe
berechtigte Findings selbständig und wiederhole den Review am neuen Commit.
```

## Dateien

| Datei | Inhalt |
|---|---|
| `CODEX_STARTPROMPT.md` | direkt verwendbarer Implementierungsauftrag, Autonomie und Abschlussbedingungen |
| `SPECIFICATION.md` | Zielarchitektur, Klassen-/Methodenverträge, Dependencies, Gradle-Tasks, Berichte und 18 Abnahmekriterien |
| `EVIDENCE_DISPOSITION.md` | konkrete methoden-/familienbezogene Bewertung des bisherigen `evidenceTest`, inklusive Stilllegungs- und Ersatzregeln |
| `EXECUTION_PLAN.md` | fünf Implementierungsphasen V0–V4, jeweilige Testgates, 22 gezielte Gegenproben und anschliessender Review R |
| `OPENCODE_REVIEW_PROMPT.md` | autonomer Aufruf, Modell-/Commitbindung, unabhängiger Prüfauftrag, maschinenlesbares Urteil und Korrekturschleife |
| `README.md` | Nutzung, Entscheidungen und Quellenbasis |

Die im Ausführungsplan genannten `STATUS.md`, `SOURCESET_INVENTORY.md`, `EVIDENCE_DECISIONS.md` und `TEST_LEDGER.md` sind **von Codex während der Implementierung zu erzeugende Ergebnisse**, keine fehlenden Dateien dieses Pakets. Sie werden nicht mit erfundenen Ausgangs- oder Testergebnissen vorgefüllt.

## Wesentliche Entscheidungen

Gradle bleibt; Versionen und Produktsemantik bleiben unverändert. Das interne Modul hat nur normale Source-Sets. Reine Compilerregressionen wechseln an den fachlich richtigen Testort. Die alte Evidenzsammelgruppe wird nicht eins zu eins neu aufgebaut: aktive Schutzwirkungen bleiben, ausschliesslich historische Arbeitsstandskontrollen werden begründet aus dem aktiven Build entfernt. Originalnachweise werden nicht neu geschrieben.

Normale schnelle Tests und explizite aktuelle `compiler-contract`-Tests werden getrennt. Root-Kommandos für SDK-, CLI-, Referenz- und Plattformprüfungen bleiben sinnvoll erreichbar. Berichtspfade, Quellfingerprints, Testzählungen und Releasekonsumenten werden gemeinsam migriert.

Codex prüft jede Phase. Erst nach V4 folgt der unabhängige Gesamt-Review durch OpenCode 2.x/DeepSeek Flash 1017. Danach gibt es bei Fehlern eine Korrektur-, Test-, Commit- und erneute unabhängige Reviewrunde. Breite Tests werden nicht aus Formalismus wiederholt.

## Quellenbasis und Grenzen

Die Spezifikation beruht auf dem gelesenen Repositorystand
`ff30c7edffbf0ee57ba95e44b630c08f4d4a8934`. Sie berücksichtigt den bereits entfernten TeaVM-Patch. Lokale weitere Änderungen müssen in V0 abgeglichen und erhalten werden.

Zentrale Primärquellen:

- [Referenzcommit](https://github.com/edigonzales/interlis-java/commit/ff30c7edffbf0ee57ba95e44b630c08f4d4a8934)
- [Host-Verifikationsbuild](https://github.com/edigonzales/interlis-java/blob/ff30c7edffbf0ee57ba95e44b630c08f4d4a8934/gradle/host-verification.gradle)
- [Bisheriger Evidenzbuild](https://github.com/edigonzales/interlis-java/blob/ff30c7edffbf0ee57ba95e44b630c08f4d4a8934/gradle/evidence.gradle)
- [Evidenzquellen](https://github.com/edigonzales/interlis-java/tree/ff30c7edffbf0ee57ba95e44b630c08f4d4a8934/interlis-compiler-cli/src/evidenceTest/java/guru/interlis/cli/evidence)
- [Bisherige Verifikationstests](https://github.com/edigonzales/interlis-java/tree/ff30c7edffbf0ee57ba95e44b630c08f4d4a8934/interlis-compiler-cli/src/verificationTest/java)
- [Vorhandener OpenCode-Starter](https://github.com/edigonzales/interlis-java/blob/ff30c7edffbf0ee57ba95e44b630c08f4d4a8934/docs/implementation/http-network-policy/review/run-review.cjs)
- [Gradle Java-Tests](https://docs.gradle.org/current/userguide/java_testing.html)
- [OpenCode v2 CLI](https://opencode.ai/v2/docs/cli/commands/)
- [OpenCode v2 Rechte](https://opencode.ai/v2/docs/permissions/)

Es wurden für dieses Paket Quellen gelesen und Dokumente erstellt, **keine Repositorymigration, Produktbuilds oder OpenCode-Reviews ausgeführt**. Die Klassendesigns sind Implementierungsvorgaben. Die Test- und Reviewergebnisse müssen Codex und der unabhängige Reviewer tatsächlich erzeugen.

In der aktuellen Anfrage waren keine separat lesbaren Markdown-Anhänge verfügbar. Das Paket orientiert sich an der bekannten Startprompt/Plan/Review-Struktur und an dem im Repository vorhandenen Reviewstarter; ältere absolute Benutzerpfade und Modellaliase werden nicht ungeprüft übernommen.
