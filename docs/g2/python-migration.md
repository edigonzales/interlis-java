# Python-Migration – einmalige Arbeitsmatrix

Basis: `c671ce3aa943afcc53579b4cd488773afbd0206e`, `git ls-files '*.py'`: **36 Dateien**.
Die Entscheidungen betreffen Funktionen, keine mechanischen 1:1-Ports. Nur die
beiden L0B-1-Zeilen werden in dieser Lieferung entfernt. Alle anderen Zeilen
sind geplante Zuordnungen; Verwendungs-/Ersatz- und Negativnachweise müssen vor
jeder späteren Löschung tatsächlich ausgeführt werden. Historische Reports und
Normdaten bleiben erhalten, frühere Skriptfassungen sind über die Basis in Git
wiederherstellbar. Diese Matrix ersetzt kein fachliches Gate.

Alle Pfade ohne Präfix liegen unter `tools/reference/`.

| Datei | Entscheidung / Lieferung | Verwendung → Ersatz und Negativnachweis |
|---|---|---|
| `conformance_check.py` | **REUSE_EXTERNAL / L0B-1** | Root `conformanceCheck` → direkter Gradle-Exec des identischen externen Java-Runners; nur Normausnahmen in CLI-Testhilfe. Alt-/Neu-Parität für 593 IDs, alle 18 Original-/Kontrollquellen; Hash-, ID-, Exit-, Diagnose-, Timeout-, XPASS-/ERROR-Canarys. |
| `test_conformance_check.py` | **REPLACE_WITH_TEST / L0B-1** | Root `conformanceWrapperTest` → `NormativeConformanceContractTest` und `OriginalViewFixtureContractTest`; sämtliche bisherigen Negativverträge und die 19 portablen Originalquellen erhalten. |
| `g2_evidence.py` | PORT_MINIMAL / L0B-2 | `g2EvidenceTest`, manuelle Reviewpakete → benötigte JUnit-XML-/Frische-/Hash-/Normprüfungen; falsche Klasse bei gleicher Methode, fehlende/alte/geskippte Reports müssen scheitern. Paketgenerator nicht erneut bauen. |
| `test_g2_evidence.py` | REPLACE_WITH_TEST / L0B-2 | `g2EvidenceTest` → Java-Verträge zu den aktiven Evidenzprüfungen, einschliesslich bestehender Manipulationen. |
| `g2_inventory.py` | PORT_MINIMAL / L0B-2 | Imports aus Inventar-/Predefinedtests → nur aktive Quell-/Norm-/Assertionbindungen; veränderte Quellhashes, Methodenbereiche und falsche Suite/Klasse/Methode ablehnen. |
| `test_g2_inventory.py` | REPLACE_WITH_TEST / L0B-2 | `g2EvidenceTest` → benötigte Java-Assertion-/Quell-/Normverträge; die vorhandenen Fehlerfälle bleiben wirksam. |
| `g2_predefined.py` | PORT_MINIMAL / L0B-2 | `g2_inventory`, Predefinedtests → aktive Deklarations-/Versions-/Signaturpins; manipulierte Fundstelle oder Wortprofil ablehnen. |
| `test_g2_predefined.py` | REPLACE_WITH_TEST / L0B-2 | `g2EvidenceTest` → Java-Prüfungen der versionierten Predefineddaten und ihrer Quellbindungen. |
| `g2_norm_inventory.py` | PORT_MINIMAL / L0B-2 | Normtests → Original-PDF-/Versions-/Exzerpt-/Offsetverträge, keine neue Vollinventur; falsche PDF/Version/Quote/Offsets ablehnen. Kein ungeprüfter Wechsel der Extraktion. |
| `test_g2_norm_inventory.py` | REPLACE_WITH_TEST / L0B-2 | `g2EvidenceTest` → Java-Verträge für notwendige Norm-/PDF-Belege mit bisherigen Negativfällen. |
| `g2_norm_crosswalk.py` | PORT_MINIMAL / L0B-2 | Crosswalktests → aktive Zuordnungs-/Vollständigkeitsvalidierung der vorhandenen Daten; fehlender/doppelter Absatz oder falscher Hash scheitert. |
| `test_g2_norm_crosswalk.py` | REPLACE_WITH_TEST / L0B-2 | `g2EvidenceTest` → entsprechende Java-Manipulationstests. |
| `g2_norm_conditions.py` | PORT_MINIMAL / L0B-2 | Bedingungstests → notwendige Daten-/Absatz-/Assertionbindungen; fehlende/duplizierte Bedingungen und falsche Annahmebestätigung ablehnen. |
| `test_g2_norm_conditions.py` | REPLACE_WITH_TEST / L0B-2 | `g2EvidenceTest` → entsprechende Java-Verträge. |
| `g2_grammar_inventory.py` | PORT_MINIMAL / L0B-2 | Grammatiktests → aktive Grammatik-/Owner-/Hashverträge; kein neuer Inventargenerator. Fremde Occurrence/Abnahmebehauptung muss scheitern. |
| `test_g2_grammar_inventory.py` | REPLACE_WITH_TEST / L0B-2 | `g2EvidenceTest` → Java-Prüfungen mit dem nach L0A gültigen Stand und Negativfällen. |
| `g2_builtin_inventory.py` | PORT_MINIMAL / L0B-2 | Builtintests → vorhandene Signatur-/Versions-/Pinbindungen; geänderte/fehlende Signatur scheitert. |
| `test_g2_builtin_inventory.py` | REPLACE_WITH_TEST / L0B-2 | `g2EvidenceTest` → Java-Verträge für aktive Builtinbelege. |
| `g2_spec_contracts.py` | PORT_MINIMAL / L0B-2 | Spec-Vertragstests → vorhandene Anforderungs-/Owner-/Quellpins; unbekannte/doppelte ID oder falsche Quellenbindung ablehnen. |
| `test_g2_spec_contracts.py` | REPLACE_WITH_TEST / L0B-2 | `g2EvidenceTest` → Java-Verträge zu aktiven Anforderungsbelegen. |
| `g2_test_candidates.py` | PORT_MINIMAL / L0B-2 | Kandidatentests → aktive Methoden-/Quellhashprüfung ohne neue Keywordinventur; erfundene/geänderte Methode und Scheinabnahme ablehnen. |
| `test_g2_test_candidates.py` | REPLACE_WITH_TEST / L0B-2 | `g2EvidenceTest` → Java-Verträge; CANDIDATE_ONLY bleibt getrennt von Abnahme. |
| `g2_normative_decisions.py` | PORT_MINIMAL / L0B-2 | Entscheidungspakettests → aktive Norm-/Original-/Kontroll-/Hashbindungen erhalten, keine Normfreigaben; manipuliertes Entscheidungspaket scheitert. |
| `test_g2_normative_decisions.py` | REPLACE_WITH_TEST / L0B-2 | `g2EvidenceTest` → Java-Verträge für die weiterhin offenen Nutzerentscheidungen. |
| `g2_a3_pairs.py` | PORT_MINIMAL / L0B-2 | A3-Paartests → benötigte Original-/JVM-/Portable-Bindungen und vorhandene Gegenchecks; falsches/fehlendes Paar scheitert. |
| `test_g2_a3_pairs.py` | REPLACE_WITH_TEST / L0B-2 | `g2EvidenceTest` → entsprechende Java-Prüfungen. |
| `author_g2_predefined.py` | DELETE_REDUNDANT / L0B-2 | Einmaliger Katalogautor, keine Gradle-Task; Erzeugnis und Provenienz bleiben. Vor Löschung aktive Importfreiheit und weiter grüne Katalog-/Negativtests belegen. |
| `generate_text_weights.py` | FREEZE_ARCHIVE / L0B-3 | Unicode-Gewichtstabellengenerator → gepinnte Daten/Lizenz und vorhandene semantische Textvergleichstests erhalten; Quellenhash-/Tabellenmanipulation muss scheitern. Kein vollständiger Generatorport. |
| `compare_original_views.py` | REPLACE_WITH_TEST / L0B-3 | Manuelle gepaarte Referenzprozesse → erforderliche originale View-Fixtures und öffentliche Assertions/Referenzprozesschecks; Lost-Member-/falsches-Ziel-/Reihenfolge-Gegenfälle. |
| `compare_views.py` | REPLACE_WITH_TEST / L0B-3 | Manuelle View-Vergleiche → benötigte Referenz-/JUnit-Verträge; falsche Annahme und falsche Ergebnisstruktur erkennen. |
| `compare_units.py` | REPLACE_WITH_TEST / L0B-3 | Manuelle Unit-Vergleiche → benötigte gepinnte Referenz-/JUnit-Verträge; falsches Verhältnis/Ziel/Prozessfehler erkennen. |
| `reference_check.py` | PORT_MINIMAL / L0B-3 | README-Referenzprofil → kleiner Java-Prozessvertrag mit denselben Artefakt-/Fixturepins; falsches JAR/Exit/Timeout/Diagnose scheitert. |
| `geo_corpus.py` | PORT_MINIMAL / L0B-3 | Root `geoCorpus` → minimaler Java-Hostprozess für denselben Corpus-/Closurezweck; fehlende/mehrdeutige/geänderte Quellen erkennen. |
| `tools/performance/benchmark_jvm.py` | PORT_MINIMAL / L0B-3 | Root `jvmBenchmark` → kleiner Java-Prozess-/Messvertrag; fehlgeschlagener Compiler/fehlende Messung darf nicht erfolgreich sein. |
| `tools/performance/probe_wasm_exit.py` | DELETE_REDUNDANT / L0B-3 | Manuelle historische Exitprobe; vorhandene Node-/Wasm-Verträge sollen den aktiven Zweck übernehmen. Vor Löschung Import-/Aufruffreiheit und tatsächlichen Fehlerpfad belegen. |
| `tools/sdk/check_distribution.py` | PORT_MINIMAL / L0B-3 | Root `sdkProcessCheck` → Java-Prozessvertrag gegen gebaute Distribution, gleiche Quellen/Exports/Hashes; kaputtes Paket oder falscher Prozess muss scheitern. |

L0B-1-Nachweise, tatsächliche Kommandos und Ergebnisse:
[Implementierungsbericht](implementation/L0B-1.md). Für L0B-2/L0B-3 sind die
angeführten Negativnachweise erst geplant. Der Python-freie Gesamtlauf und alle
Phaseninvarianten werden am letzten 0b-Kandidaten geprüft.
