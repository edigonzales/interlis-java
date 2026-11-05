# INTERLIS Compiler GUI — Swing / FlatLaf

Spezifikations- und Ausführungspaket, 5. November 2026.

**Auftrag:** eine eigenständige Compileroberfläche mit Dependency Explorer, Repository Browser und intelligenter Fehlerliste implementieren. Ausschliesslich Swing und FlatLaf; kein JavaFX, keine Diagramme und keine Format Preview. Die Implementierung erfolgt durch Codex in einem eigenen Branch. Jede Phase benötigt gezielte erfolgreiche Tests und ein unabhängiges, autonomes Review durch OpenCode 2.x mit DeepSeek Flash 1017 über die direkte DeepSeek-API.

## Ablage im Repository

Die Markdown-Dateien dieses Pakets unter folgendem Pfad ablegen:

```text
docs/implementation/compiler-gui-swing/
```

Die enthaltene `review-verdict.schema.json` gehört ebenfalls in dieses Verzeichnis. Nicht mit den Formatter- oder JavaFX-PoC-Aufträgen zusammenführen. Dieses Paket erteilt keinen Auftrag zur Umsetzung anderer vorhandener Spezifikationen.

## Lesereihenfolge

| Datei | Inhalt |
|---|---|
| `CODEX_STARTPROMPT.md` | Ausführbarer Arbeitsauftrag für Codex, Branchregeln und Abnahmebedingungen |
| `SPECIFICATION.md` | Produktumfang, Fensteraufbau, Verhalten und 52 Abnahmekriterien |
| `ARCHITECTURE.md` | Modulgrenzen, Klassen-/Methodenverantwortung, Datenfluss, Nebenläufigkeit und Packaging |
| `EXECUTION_PLAN.md` | Sieben sequenzielle Phasen, Tests, Lieferobjekte und unabhängige Gates |
| `TEST_AND_EVIDENCE_POLICY.md` | Risikobasierte Testauswahl, echte GUI-Prüfungen und Nachweisbindung |
| `OPENCODE_REVIEW_PROMPT.md` | Autonomer Reviewer, OpenCode-2.x-Preflight, Gegenproben und Urteil |
| `SOURCE_BASELINE.md` | Geprüfter Repositorystand, reale Anschlussstellen und externe Primärquellen |
| `review-verdict.schema.json` | Struktur des Reviewerurteils; zusätzliche semantische Gateprüfungen bleiben erforderlich |

## Produktentscheidung in einem Satz

Ein gemeinsames Fenster enthält die drei Arbeitsbereiche **Check**, **Dependencies** und **Repositories**. Alle beziehen sich auf eine explizite Prüfsitzung; Browsen verändert weder die aktuelle Kompilierung noch deren Resultate. Ein Dependency-Baum ersetzt ausdrücklich kein UML-Diagramm, sondern macht die tatsächlich verwendeten Imports und ihre Herkunft lesbar.

## Start für Codex

```text
Lies AGENTS.md und anschliessend
  docs/implementation/compiler-gui-swing/CODEX_STARTPROMPT.md.
Implementiere den dort beschriebenen Auftrag phasenweise in einem separaten
Branch codex/compiler-gui-swing. Jede Phase benötigt erfolgreiche gezielte
Tests und einen autonomen unabhängigen OpenCode-2.x-Review mit DeepSeek
Flash 1017 über die direkte DeepSeek-API. Kein Phasenwechsel ohne gültiges
PASS. Keine Implementierung von JavaFX, Diagrammen oder Format Preview.
```

## Stand dieses Pakets

Die Dateien sind eine Spezifikation, keine bereits implementierte oder getestete Anwendung. Weder der Produktbranch noch ein OpenCode-Review wurden durch die Erstellung dieses Pakets ausgeführt. Vorhandene lokale PoC-Arbeiten und neuere Repositoryänderungen müssen im Preflight berücksichtigt werden.
