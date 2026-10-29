# Python-Migration – einmalige Arbeitsmatrix

Basis: `c671ce3aa943afcc53579b4cd488773afbd0206e`, `git ls-files '*.py'`: **36 Dateien**.
Die Entscheidungen betreffen Funktionen, keine mechanischen 1:1-Ports. L0B-1 entfernt zwei Dateien; L0B-2 entfernt weitere **25** Dateien, L0B-3 die letzten **neun**.
Damit bleiben **null** getrackte Pythondateien. Die nötigen Altprüfungen erfolgten vor der Löschung; die
Ersatz- und Negativnachweise stehen unten. Unabhängiges Urteil und geprüfter SHA stehen in [STATUS.md](STATUS.md). Historische Reports und
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
[Implementierungsbericht](implementation/L0B-1.md). Für L0B-2 siehe den tatsächlichen
[Implementierungsbericht](implementation/L0B-2.md). L0B-3 samt tatsächlich ausgeführtem Python-freiem Gesamtlauf:
[Implementierungsbericht](implementation/L0B-3.md). Das unabhängige Phasenurteil bleibt in STATUS getrennt.

## L0B-2: ausgeführte Ersatz- und Entbehrlichkeitsnachweise

Der alte Root-Task importierte nur die zwölf `test_g2_*.py` und deren Hilfen.
`rg` über Gradle, Shell, Node, YAML und die verbleibenden Pythondateien zeigt
nach dem Ersatz **keinen** Aufruf/Import dieser 25 Dateien. Der Katalogautor
hatte ausschliesslich einen manuellen Einstieg. Der alte Gate-Lauf vor Löschung
lief mit **59 Tests, Exit 0**, auch mit einem leeren separaten Python-Bytecodecache.
Die alten Testnamen sind im Rohbeleg `legacy-test-index.txt` aufgezeichnet.

Alle Ersatzklassen liegen unter
`interlis-cli/src/evidenceTest/java/guru/interlis/cli/evidence/`.

| Alte Funktion / Dateien aus obiger Matrix | Tatsächlicher Ersatz / Negativnachweis |
|---|---|
| `g2_evidence.py`, `test_g2_evidence.py` | `AssertionEvidence{,Test}` prüft frische reale XML-Bytes, vollständige Suite/Klasse/Methode, Hashes, Fehler/Skips, Testdetails und unerwartete Suites. `RuntimeReports{,Test}` erhält Reportformat-, Browsermatrix-, Versions-, Portable-, SDK- und Roh-/Projektbilanz-Verträge inklusive der acht leeren Fehlgrünvarianten. Der alte Snapshot/Tar/Patch-Generator entfällt: unabhängiger Review nutzt bereits sauberen eigenen Checkout am exakten SHA. Seine drei Archiv-/Modus-Tests haben keinen aktiven Aufrufer mehr. Kein neues Paket-/Snapshotframework. |
| `g2_inventory.py`, `test_g2_inventory.py` | `CatalogueEvidence.sources`, `AssertionEvidence`, `NormEvidence` mit jeweiligen JUnit-Tests prüfen aktuelle Quellenpins, unveränderte Regel-/Anforderungs-/Assertionzuordnung, Methodensignaturen, Fragmente, historische Laufhashbindung und frische XML. Die fünf Tests des eigenen ANTLR-Tokenizer-/Branch-Generators und der TOC-Generator entfallen mit diesem Autorwerkzeug. Quellenhashes, alle 1225 vorhandenen Occurrence-Bereiche und ihre vollständige Arbeitsliste bleiben aktiv geprüft; das echte ANTLR-/Compiler-Testgate bleibt erhalten. |
| `g2_predefined.py`, `test_g2_predefined.py` | `CatalogueEvidence.predefined`, `languageProfile`, `signatures`, `signatureUses` samt Tests: alle 130 Deklarationen, 21 Sondertypen, Anker, Akzeptanztexte, Profile, neue Aufruf-/Origin-Produzenten, Worttabellen, tatsächliche Signaturpositionen und Versionen; OBJECT bleibt unbenutzt und entscheidungsabhängig. Verlust, Dublette, falscher Owner/Hash, schwächere Assertions, falsches Profil/Versions-/Nutzungsfeld scheitern. |
| `g2_norm_inventory.py`, `test_g2_norm_inventory.py` | `NormEvidence{,Test}` bindet Original-PDF-SHA/Version und unveränderte pypdf-Seiten; exakte Codepoint-Bereiche, Zitate, Absatzhashes und vollständige Nicht-Whitespace-Abdeckung. Erzeugung neuer Absätze/Überschriften entfällt; fehlender Absatz, kaputter Bereich/Hash, falsche PDF/Version/Extraktor und Scheinabnahme scheitern. |
| `g2_norm_crosswalk.py`, `test_g2_norm_crosswalk.py` | `NormEvidence.crosswalk` und `paragraphs`: alle 311 Abschnittslocators, Owner, Disposition und SECTION_LEVEL/PENDING-Grenze. Fehlender/doppelter/falscher Abschnitt, Owner oder Abnahmebehauptung scheitert. Automatische Owner-Vorschläge und das Umschreiben von Inventaren entfallen; die vorhandenen Daten bleiben unverändert. |
| `g2_norm_conditions.py`, `test_g2_norm_conditions.py` | `NormEvidence.conditions`: 777 Bedingungen, 369 Compilerbedingungen, 1476 geplante Fallarten; Absatz-/Abschnittsdeckung, eindeutige Bindung, exaktes Zitat/Hash, Owner, offene/teilweise Evidenz. Fehlende Compilerbedingung, doppelter Absatz, falscher Owner/Kind/Hash und behauptete Ausführung scheitern. Clustering-/Vorschlagserzeugung entfällt. |
| `g2_grammar_inventory.py`, `test_g2_grammar_inventory.py` | `CatalogueEvidence.grammar`: alle 321 Produktionen/1225 Occurrences mit Quellbereich/Hash, Reihenfolge, Owner und fehlender Abnahme; fremde, fehlende oder als VERIFIED markierte Occurrence scheitert. Kein neuer Inventargenerator. |
| `g2_builtin_inventory.py`, `test_g2_builtin_inventory.py` | `CatalogueEvidence.builtins` samt Tests: 21 tatsächliche Signaturen, Argumente/Resultat, Version/Regel, 84 PLANNED-Fälle, beide offenen Entscheidungen. Multiline-Parsing und unbekannte Signatursyntax geprüft; geänderte/fehlende Signatur oder Scheinabnahme scheitert. Worklisterzeugung entfällt. |
| `g2_spec_contracts.py`, `test_g2_spec_contracts.py` | `CatalogueEvidence.sources`, `specifications`: exakte 289 Anforderungsverträge, 12 explizite unnummerierte Zeilen, Quellenhashes und zulässiger Owner/Status. Fehlende/doppelte/unbekannte ID, geänderter Vertrag und falscher Zeilenhash scheitern. Neue Text-/Owner-Inventur entfällt. |
| `g2_test_candidates.py`, `test_g2_test_candidates.py` | `CatalogueEvidence.candidates`: jede referenzierte Methode mit Klasse, @Test, Codepoint-Bereich und Körperhash sowie Entdeckungszahl bleibt gebunden; erfundene Methode, falsche Klasse/Hash und Abnahmebehauptung scheitern. Die erneute Keyword-Rangfolge ist nur Autorenheuristik, kein Beweis, und entfällt. Der eingefrorene Index ist unverändert CANDIDATE_ONLY; weder dieser noch eine historische Punktzahl wird als Semantikabnahme verwendet. |
| `g2_normative_decisions.py`, `test_g2_normative_decisions.py` | `CatalogueEvidence.decisions`: alle 18 Erwartungen, beide Normversionen, offene Nutzerentscheidung und jedes tatsächlich zitierte Originalfragment. Manipulierte Erwartung/Quote/Hash oder fehlender Fall scheitert. Die Präsentations-/Headerbereinigung entfällt; JSON und Markdown bleiben erhalten. Vorhandene leere Locator-Einträge bleiben offene Lücken, keine Abnahme. |
| `g2_a3_pairs.py`, `test_g2_a3_pairs.py` | `CatalogueEvidence.pairs`: dieselben drei JVM-Methoden mit vollständigem Quellen-/Körperhash und portable Marker. Fehlendes/falsches Paar, falscher JVM-Hash oder fehlender Marker scheitert. Der alte Portable-Gesamthash war bereits historisch und wurde auch vom Altvalidator nicht als aktueller Hash geprüft; keine neue Portable-Abnahme. |
| `author_g2_predefined.py` | **DELETE_REDUNDANT** bestätigt: kein aktiver Aufrufer/Import nach Entfernung; erzeugter Katalog und alle obigen Positiv-/Negativprüfungen erhalten. |

Historische Besonderheiten werden **nicht** durch Pinänderungen kaschiert:
`matrix.inventoryPins` enthält den alten A1-Parser; aktuell gelten
`latestGrammarSha256` und `sourceInventory.pins`. Einige historische Spec-
Zeilennummern liegen vor Dokumenteinfügungen; exakte Texte/Zeilenhashes und
aktuelle Quellenlocator bleiben geprüft. Der historische Markdownhash des
Entscheidungspakets liegt vor dem aktiven Planhinweis; der bereits bisher aktive
JSON-Paket-/Norm-/Erwartungsvertrag bleibt unverändert geprüft. Nur Kapitel 03
beschreibt jetzt den neuen Buildweg; sein Quellenpin, aktuelle Zeilenlocator
und der umschliessende Inventarhash werden sichtbar mechanisch nachgeführt.

Dieser L0B-2-Zwischenstand benötigte für das Evidenzgate kein Python/pypdf;
neun Werkzeuge und das volle Phasengate waren damals noch offen.

## L0B-3: ausgeführte Ersatz- und Entbehrlichkeitsnachweise

Java-Hosthilfen liegen unter `interlis-cli/src/verification/java/guru/interlis/cli/verification/`;
`gradle/host-verification.gradle` integriert sie ohne neue Runtimeabhängigkeit.
Die vorhandenen Node-/Browserprüfungen bleiben aktiv. Historische Berichte und
die alten Skriptfassungen im Basiscommit bleiben lesbar.

| Datei / Entscheidung aus obiger Matrix | Tatsächlicher Ersatz und Gegenbeleg |
|---|---|
| `generate_text_weights.py` / FREEZE_ARCHIVE | Originale Unicode-17-Daten deterministisch archiviert und gegen bisherige SHA geprüft. `TextLengths.java` bytegleich. `UnicodeWeightsTest`: 100170 explizite NFD-Vergleiche, Quellen-/Tabellenhash und manipuliertes Gzip. Kein Generatorport. |
| `compare_original_views.py` / REPLACE_WITH_TEST | `originalViewReferenceCheck`: dieselben 19 gepinnten Originalquellen, aktuelle CLI und ili2c 5.6.9 jeweils erfolgreich; bestehende öffentliche Graph-/Portable-Originalassertions bleiben aktiv. |
| `compare_views.py` / REPLACE_WITH_TEST | `viewReferenceCheck`: dieselben gemeinsamen JS-Viewfälle über kleinen Node-Exporter; 134 eigene Erwartungen geprüft. Referenzbeobachtung separat: 102 Übereinstimmungen, 6 Referenzprozessfehler sichtbar. Keine Behauptung vollständiger Referenzparität. |
| `compare_units.py` / REPLACE_WITH_TEST | `unitReferenceCheck`: 117 gemeinsame JS-Fälle plus 6 gepinnte Originale, alle 123 eigenen Erwartungen; Referenz 121 Übereinstimmungen, 0 Prozessfehler. Falscher Prozess/Outcome/Diagnosestruktur wird über `HostChecksTest` abgelehnt. Beide alten Vergleichsskripte scheiterten schon vor Migration an veralteten JS-Blockmarkern; der neue Exporter verwendet die tatsächlichen vorhandenen Blöcke. |
| `reference_check.py` / PORT_MINIMAL | `referenceCheck`: identisches 5-Fall-Profil, ZIP-/JAR-Pins und ili2c-Version; aktuelle Fixturepins separat festgehalten. Alt-/Neu-Inputs, Argumente, Exits und Outcomes stimmen überein. Falsches JAR, defekte Distribution, Timeout/Exit und falsche Ergebnisstruktur scheitern. |
| `geo_corpus.py` / PORT_MINIMAL | `geoCorpus`: alle 13 Originalpins und vollständige Indexmetadaten; fehlende/falsche/mehrdeutige Metadaten scheitern. Originalindex und zwei geänderte Livequellen exakt aus offiziellem Image wiederhergestellt; `corpus-seed.zip` erhält dieselben 14 Originaldateien offline. Kein Repin. |
| `tools/performance/benchmark_jvm.py` / PORT_MINIMAL | `jvmBenchmark`: dieselben 13 Quellen und Check-/Formatprozesse, 1 Warmup und 3 Messungen; pro Kindprozess RSS mit expliziter Byteeinheit. Fehlender/fehlgeschlagener Prozess, Messung oder instabile Ausgabe scheitert. Der alte Pythonbenchmark scheiterte bereits an den Corpus-Metadaten; kein erfundener numerischer Paritätswert. |
| `tools/performance/probe_wasm_exit.py` / DELETE_REDUNDANT | Kein aktiver Import/Aufruf. Historische Probe vor Löschung mit 8 erfolgreichen Standard-/Liftoff-Lade-/API-/Semantikprozessen ausgeführt. Aktive Node-/Wasm-Verträge mit Erfolg und echter Ablehnung sowie Worker-Neustart frisch ausgeführt; kein paralleler manueller Runner nötig. |
| `tools/sdk/check_distribution.py` / PORT_MINIMAL | `sdkProcessCheck`: dieselben 9 echten Distributionskommandos, Version/API, positive/negative Quelle, Format/Write/Usage und Artefaktstabilität. Alt-/Neu-Exits, stdout-Hashes und stdout/stderr-Zeilenzahlen identisch (nur temporärer Quellpfad normalisiert). Kaputtes Paket, falsche Version/Antwort und Prozessfehler scheitern. |

`pythonFreeCheck` prüft getrackte und nicht ignorierte Eingaben samt aktiven
Gradle-/Shell-/Node-/Java-/CI-Aufrufen. Echte zusätzliche `.py`- und Shell-
Interpretercanarys lieferten jeweils Exit 1 und wurden entfernt. Der einzige
enge Scanner-Ausnahmepfad ist der negative Interpreterprobe-/OS-Sperr-Harness
`tools/verification/without-python.cjs`.

Der vollständige kumulative Lauf war erfolgreich in dessen privatem PATH mit
vererbter macOS-`process-exec`-Sperre: PATH-Proben ENOENT, absolute
`/usr/bin/python3` und `/usr/bin/pip3` EPERM. Installationen auf dem Host bleiben
vorhanden, sind in dieser Ausführungsumgebung nicht ausführbar. Firefox benötigt
darin `MOZ_DISABLE_CONTENT_SANDBOX=1`, weil sein zusätzlicher Sandboxstart sonst
mit EPERM scheitert; die äussere Interpreter-Ausführungssperre bleibt vererbt.
Dies ist im Umgebungsreport ausdrücklich dokumentiert. Der unabhängige Reviewer
muss den sauberen Kandidaten-SHA selbst prüfen.
