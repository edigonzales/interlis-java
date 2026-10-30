# 13 – Tests, Kompatibilitätsnachweise und Qualitätsgates

L4-1 ergänzt mit `./gradlew semanticComparison` den tatsächlichen A3-Vergleich:
vier identische2.3-/2.4-Quellen über JVM, gepinntes ili2c-Metamodell, TeaVM-JS und
WasmGC. Verglichen werden Compile-Outcome, geordnete effektive Member und
vollständig qualifizierte Rollenzielwurzeln dreier ausgewählter Viewables.
Der Vergleicher muss entfernte Member, ein falsches gleichnamiges Ziel,
vertauschte Reihenfolge und falschen Erfolg an ungültiger Quelle erkennen.
Der Bericht benennt nicht vergleichbare Eigenschaften ausdrücklich; dieser
kleine Vergleich ist keine vollständige Sprach- oder Transferabnahme.
Die neuen öffentlichen Memberprojektionen werden zusätzlich im gemeinsamen
portablen Vertrag auf allen fälligen Browser-/Web-Image-Zielen geprüft.

Die ausdrücklich gewählte strikte äussere Typnamensregel gilt auch im
historischen13-Modelle-Corpus. Metadata/FileFormat und Gesuchsteller/Gesuchsteller
bleiben bytegleich und werden mit exakten strukturierten Quellfehlern gemessen;
die übrigen elf Compilefälle und alle13Formatierungen bleiben erfolgreich.
`testdata/g2/corpus-expectations.json` bindet die zwei Negativausgänge und
separat umbenannte Positivkontrollen an Originalhashes. Die Kontrollschemas
müssen tatsächliche Basisbindungen und geordnete Member bei Fresh/Reuse zeigen.
Ein beliebiger Fehler oder ein fälschlich erfolgreicher Compile genügt nicht.
Bestehende P3-Messausnahmen werden sichtbar erhalten; sie ersetzen keine
verpflichtenden G2-Verträge in Browsern einschliesslich Safari und Web Image.

L1-3 prüft AC1-05–08 mit tatsächlichen dreistufigen Klassen-/Struktur-/
Assoziationsgraphen, vollständigen Besitzerpfaden und Herkunftsarten,
finalen importierten Basistypen in beiden Quellenordnungen, finalen und
verwaisten EXTENDED-Attributen, gemeinsamen Assoziationsattributregeln sowie
öffentlichen Definitionsqueries nach Importrevisionen. Die kumulative
Phase-1-Abnahme verlangt zusätzlich alle AC1-01–07 am selben Kandidaten und
frische volle T1-/T2-/T3-Prüfungen einschliesslich realem Safari und Web Image.
Ein erfolgreicher Node-Lauf belegt diese Browserprüfungen nicht. Die bestehenden
19 strengen Conformance-Assessments und sämtliche Norm-/Suitepins bleiben aktiv.

Für den Nutzerauftrag zum vollständigen G2-Abschluss vom 29.10.2026 gilt der
[Ausführungs- und Reviewvertrag](../g2/EXECUTION_PLAN.md) mit dem
[operativen Status](../g2/STATUS.md). Er ersetzt organisatorisch den früheren
[A–I-Vertrag](../evidence/stabilization/g2-completion-plan.md). Jede Lieferung
benötigt einen unabhängigen OpenCode-Review am Kandidatencommit; die letzte
Lieferung einer regulären Phase umfasst zugleich das kumulative Phasengate.
L0A hat seinen ausdrücklich engeren Pilotumfang. Ausgeführte Prüfungen und
Reviewabnahme bleiben getrennte Zustände. Normative Erwartungen und neue
Referenzausnahmen benötigen weiterhin die ausdrückliche Nutzerfreigabe.

L1-2 prüft Unterteilungsregeln mit öffentlichen `NumericRange`-/`UnitSymbol`-/
Format-/Mitgliederwerten, exakten Diagnosebereichen, Domainqueries und
Fresh/Reuse. Abstrakte Spezialisierung, unveränderte gegenüber geänderter
Vererbung, symbolische Kürzung, nicht abbrechende Dezimalbrüche sowie grosse
und tiefe Einheitenabhängigkeiten erhalten gezielte Fälle. Ein gültiger
aufwendiger Vergleich muss mit kleinem Budget kontrolliert abbrechen und
mit grösserem Budget bestehen können. Dieselben neuen funktionalen Fälle
laufen über den gemeinsamen portablen Vertrag; ein Node-Erfolg bleibt vom
vollständigen Browser-/Safari-/Web-Image-Phasengate getrennt.

Der aktuelle L1-2-Abgleich ergänzt genau eine CIRCULAR-Referenzausnahme zu den
18 unveränderten früheren Bewertungen. Alle 19 verlangen weiterhin gepinnte
Original-/Kontrollquellen und exakte strukturierte Diagnosen beziehungsweise
sauberen Erfolg. Die Bestätigung der fortgeltenden CIRCULAR-Regel, die konkrete
Normabweichung des Fixtures und die unabhängige Prüfung bleiben im Bericht
erkennbar; keine neue Quelle oder globale XFAIL-Ausnahme wird still aufgenommen.

L2-1 ergänzt nach der ausdrücklichen SET-Kontextentscheidung zwei weitere
streng geprüfte Referenzfälle: `ili23.Constraint23Test.setCstrt` und
`ili23.InterlisConversion23Test.iliconversion_ConversionOfSETCONSTRAINT`.
Die unveränderten Originale greifen im Hauptausdruck auf individuelle
Attributwerte zu; die Kontrollfassungen verwenden diese Werte im WHERE-Filter.
Alle **21** Assessments verlangen Original-/Kontrollhashes, exakten Outcome/Exit,
Diagnosecode, Severity, Phase, Argumente, UTF-16-Bereiche und Multiplizität.
Die ursprünglichen19 Manifestobjekte bleiben exakt erhalten; der vollständige
frühere Manifestinhalt ist mit seinem ursprünglichen SHA zusätzlich archiviert.
Das eingefrorene globale19-Fälle-Entscheidungspaket bleibt unverändert. Die
Evidenzprüfung validiert es gegen den historischen Byte-Pin und prüft zugleich
die vollständige Gleichheit aller19 aktuellen Objekte sowie ausschliesslich
die zwei ausdrücklich auf das bestätigte SET-Profil gestützten Ergänzungen.
Manipulierte Originalbewertungen, fehlende Ergänzungen, fremde Fall-IDs oder
erfundene Profilfreigaben lassen die Prüfung scheitern. Rohreferenz und
Projektbewertung bleiben getrennt; es gibt keine globale XFAIL-Freistellung.

## 1. Ziel und verbindliche Aussagegrenzen

Dieses Dokument legt fest, wie ein Coding-Agent Fortschritt und Korrektheit nachweist. Ein erfolgreicher Build, eine grüne kleine Testsuite oder ein lesbares Diagramm genügt nicht als Beleg für einen vollständigen INTERLIS-Compiler. Jede Aussage zur Sprach-, CLI-, Laufzeit- oder Datenbankkompatibilität braucht einen benannten Umfang, gepinnte Referenzen und tatsächlich ausgeführte Tests.

Die Tests sollen fachliche Risiken prüfen. Sie sollen keine umfangreiche Sammlung von Getter-Tests und Spiegelungen der Implementierung werden. Architekturgrenzen, Zustandsautomaten, komplizierte Semantik, fehlerhafte Eingaben und Interoperabilität benötigen dagegen gezielte Vertragstests. Der Build und die Gradle-Aufgaben werden in [Build und Repository](03-build-und-repository.md) festgelegt; dieses Dokument definiert deren inhaltliche Gates.

Die hier benannten Harness-/Recorder-/Comparator-Klassen sind Testwerkzeuge. Sie gehören zunächst in passende Testquellen beziehungsweise gemeinsam benötigte Test-Fixtures. Sie begründen keine zusätzlichen veröffentlichten Produktionsmodule und keine eigene allgemeine Testplattform. Gleiche Prozess-, Fixture- und Ergebnislogik wird wiederverwendet; ein spezialisiertes Harness enthält nur die Unterschiede seiner Prüfaufgabe.

**Bereits vorhandene Evidenz:** Ein kleiner Java-25-/ANTLR-Versuch wurde auf JVM, TeaVM-WasmGC und TeaVM-JavaScript ausgeführt. Er prüft Syntaxbeispiele und ausgewählte Java-/Zahlen-/Unicode-Funktionen. Er implementiert und prüft keine vollständige INTERLIS-Semantik, kein feingranulares inkrementelles System, keinen vollständigen Browservergleich und keine Datenbankkompatibilität. Seine genaue Evidenz gehört zu [WASM und Portabilität](10-webassembly-und-portabilitaet.md).

Alle übrigen hier definierten Ergebnisse sind geplante Abnahmekriterien. Ein Agent MUSS unerreichte Kriterien als offen oder fehlgeschlagen kennzeichnen und darf sie nicht aufgrund dieser Spezifikation als erledigt markieren.

## 2. Anforderungen und Abnahme

| ID | Normative Anforderung | Phase | Abnahme |
|---|---|---|---|
| TEST-001 | Jede implementierte normative Anforderung MUSS auf einen Test, eine reproduzierbare Inspektion oder eine begründete andere Abnahme abgebildet werden. | P0 | Aktualisierte Anforderungs-/Abdeckungsmatrix. |
| TEST-002 | Referenztools, Grammatik, Testkorpora und externe Suites MÜSSEN über unveränderliche Stände und Integritätsnachweise festgelegt werden. | P0 | Manifest und überprüfte Downloads; kein stilles `latest`. |
| TEST-003 | Sprachtests MÜSSEN 2.3 und 2.4 ausdrücklich unterscheiden; ausgeschlossene 1.0-/2.2-Fälle dürfen weder als bestanden noch als Regression der zugesagten Sprachen zählen. | P1 | Bericht mit ausgewähltem und ausgeschlossenem Umfang. |
| TEST-004 | Akzeptanz-/Ablehnungstests MÜSSEN durch semantische Vergleiche ergänzt werden. | P1/P2 | Typ-, Bindungs-, Reihenfolge- und Constraint-Goldens. |
| TEST-005 | Das endgültige Compiler-Gate MUSS vollständige Prüfung der angeforderten Root-Closure verlangen. Teilweise erkannte Syntax darf kein Erfolgsschema erzeugen. | P1 | Negativtests für Recovery und unvollständige Abdeckung. |
| TEST-006 | Der inkrementelle Stand MUSS nach jeder relevanten Editierfolge dem frischen Lauf auf denselben Quellen entsprechen. | P1 | Deterministische Editiersequenzen und normalisierter Ergebnisvergleich. |
| TEST-007 | Die portable Strecke MUSS fachlich identische Fixtures auf JVM und aktivem WASM-Ziel ausführen. | P1 | Maschinenlesbarer Paritätsbericht. |
| TEST-008 | Browserunterstützung DARF nur für tatsächlich getestete Browser-/Versionenkombinationen behauptet werden. | P1/P3 | Ausführbare Browsermatrix mit Laufzeitvoraussetzungen. |
| TEST-009 | Fuzz- und Negativtests MÜSSEN begrenzte Laufzeit, Speicher und Ausgabe besitzen; Hänger und interne Defekte werden als Fehler klassifiziert. | P1/P2 | Reproduzierbare Seeds und kontrollierte Ressourcenlimits. |
| TEST-010 | Benchmarkresultate MÜSSEN Hardware, Laufzeit, Eingaben, Optionen, Warmup und Messmethode nennen. | P3 | Versionierter Messbericht, ohne aus kleinen Beispielen abgeleitete Produktionszusagen. |
| TEST-011 | Bekannte Abweichungen MÜSSEN einzeln begründet, einem Verantwortlichen beziehungsweise Issue und einem Umfang zugeordnet sein. | P1 | Überprüfter Baselineeintrag; kein automatisches Umwandeln aller FAILs in XFAIL. |
| TEST-012 | Normative Erwartungen MÜSSEN eine Referenz oder ausdrückliche offene Entscheidungsnotiz besitzen. Unsichere Normregeln dürfen nicht als erfundene Goldens festgeschrieben werden. | P1 | Fixture-Metadaten mit Abschnitt/Quelle oder offenem Reviewstatus. |
| TEST-013 | Reale Modellkorpora MÜSSEN lokal reproduzierbar und unabhängig von einem beweglichen Online-Repository testbar sein. | P2 | Gefrorener Quellenbestand mit vollständig aufgelöster Abhängigkeit. |
| TEST-014 | Abbruch, Fehler, Observerausfall und veraltete Publikation MÜSSEN die gleichen Qualitätsgates wie der Erfolgsfall erhalten. | P1 | Zustands- und Integrationstests. |
| TEST-015 | Architekturtests MÜSSEN verbotene Abhängigkeiten und nach aussen geleakte ANTLR-/Host-Typen erkennen. | P0/P1 | Abhängigkeits- und öffentliche API-Prüfung. |
| COMPAT-001 | Kompatibilität MUSS getrennt für Sprache, CLI, Diagnostik, Metamodell, Transfer, Java-API und DB ausgewiesen werden. | P0 | Benannte Profile und Matrix; kein pauschaler Kompatibilitätsclaim. |
| COMPAT-002 | CLI-Exitcodes und Ausgabekanäle MÜSSEN vom Referenztool erfasst werden; dieser Katalog darf keine unbekannten Werte fest erfinden. | P3 | Getrennte stdout-/stderr-/Exitcode-Fixtures. |
| COMPAT-003 | Ein semantischer Unterschied zum Altcompiler MUSS gegen Norm und Verbraucherwirkung bewertet werden. | P2 | Dokumentierte Entscheidung: Regression, bewusste Normkorrektur oder offener Fall. |
| COMPAT-004 | Ein Legacy-Java-Adapter ist derzeit nicht Bestandteil von P3/P4; bei Wiederaufnahme MUSS er echte verwendete Objektgraph- und gegebenenfalls API-/ABI-Verträge prüfen. | zurückgestellt (nicht P3/P4) | Eigener Auftrag mit gepinnten Verbrauchern; blosse Gettergleichheit genügt nicht. |
| COMPAT-005 | Datenbankkompatibilität MUSS bidirektionale Operationen gegen das bisherige Werkzeug umfassen. | P5 | Alt→Neu und Neu→Alt pro unterstütztem DB-Profil. |
| COMPAT-006 | Ein Profilwechsel MUSS ausdrücklich getestet und dokumentiert werden; bestehende Datenbanken dürfen nicht still einem neuen Profil zugeordnet werden. | P5 | Mehrdeutigkeits- und Migrationsfälle mit alten Metadaten. |
| COMPAT-007 | Nicht unterstützte Verbrauchermerkmale MÜSSEN als fehlende Fähigkeit sichtbar bleiben und dürfen nicht still aus Goldens entfernt werden. | P1/P4 | Gültiges Schema mit absichtlich begrenzter Projektion. |

## 3. Referenzregister und Profile

### 3.1 Gepinnte Ausgangsquellen

Die vorausgehende Analyse hat unter anderem diese Stände untersucht. Sie sind ein nachvollziehbarer Ausgangspunkt für die erste Referenzaufnahme. Der Coding-Agent muss die tatsächlich verwendeten Dateien und ausführbaren Artefakte zusätzlich reproduzierbar erfassen.

| Referenz | Untersuchte Revision | Zweck |
|---|---|---|
| [claeis/ili2c](https://github.com/claeis/ili2c/tree/cbccff35fc98fdfa370e42e3bb39bb4edd70a658) | `cbccff35fc98fdfa370e42e3bb39bb4edd70a658` | Semantik, Metamodell, CLI und Diagnostik |
| [claeis/iox-ili](https://github.com/claeis/iox-ili/tree/2807992c45bb10d18f44ea4a882fc19f6dd1e4cb) | `2807992c45bb10d18f44ea4a882fc19f6dd1e4cb` | Transfer-/Validierungsverbraucher |
| [claeis/ilivalidator](https://github.com/claeis/ilivalidator/tree/cee7ee7994062128f635f182592fbdfed62f9519) | `cee7ee7994062128f635f182592fbdfed62f9519` | CLI und Werkzeugorchestrierung |
| [claeis/ili2db](https://github.com/claeis/ili2db/tree/ed9e7c20c9148b02d42e1846e2c5c555341aa257) | `ed9e7c20c9148b02d42e1846e2c5c555341aa257` | DB-Abbildung und technische Metadaten |
| [edigonzales/ilic-fork](https://github.com/edigonzales/ilic-fork/tree/c601f1c9d88eb68f67991fd05fc4653884d104c7) | `c601f1c9d88eb68f67991fd05fc4653884d104c7` | Architektur, Diagnostik, Suite-Anbindung |
| [geostandards-ch/interlis-antlr4](https://github.com/geostandards-ch/interlis-antlr4/tree/f8f97238c2540a49915f8bb97ee99bbf434b74da) | `f8f97238c2540a49915f8bb97ee99bbf434b74da` | Ausgangsgrammatik und WASM-Versuch |
| [geostandards-ch/ili2ogc](https://github.com/geostandards-ch/ili2ogc/tree/dcc23fac1f879e8ad3340ed1b208072b1a245564) | `dcc23fac1f879e8ad3340ed1b208072b1a245564` | Zusätzliche Grammatikbehandlung und Modellfälle |
| [edigonzales/interlis-lsp](https://github.com/edigonzales/interlis-lsp/tree/65864b3cd74e7cd8a2ff8e066331e717cebc517e) | `65864b3cd74e7cd8a2ff8e066331e717cebc517e` | Fehlerhafte Editorstände und vorhandene Ausgaben |

Eine Quellrevision identifiziert noch nicht automatisch ein lokal ausgeführtes JAR. `ReferenceArtifact` erfasst zusätzlich Download- oder Buildweg, tatsächliche Versionsausgabe, SHA-256, Runtime, aufbewahrte Lizenz-/Herkunftsinformation und gegebenenfalls eigene Buildparameter. Der Agent darf keinen Hash erfinden, wenn ein Artefakt noch nicht beschafft wurde.

Die aktuellen und historischen Referenzhandbücher werden über die offizielle [INTERLIS-Dokumentation](https://www.interlis.ch/dokumentation/interlis-2) aufgenommen. Die konkrete Fassung und Abschnittsverweise gehören in das Fixture-Metadatum. Eine Browserseite mit veränderlichem Inhalt ist allein kein reproduzierbarer Normsnapshot.

### 3.2 Profilklassen

`LegacyCompilerProfile` enthält `ProfileId`, `ReferenceArtifact`, Sprachumfang, relevante semantische Flags und `cliProfile(): LegacyCliProfile`. Das generische CLI-Profil ist in [CLI und SDK](18-cli-sdk-und-kompatibilitaetsadapter.md) definiert und enthält Aufrufmuster, CLI-Defaults, Umgebung, Kanäle und die Regeln der Ausgabeaufnahme. Die vollständigen erfassten Exitcodewerte und Meldungsfixtures werden dort versioniert. Die beiden Profile dürfen diese Werte nicht unabhängig und widersprüchlich duplizieren.

`LegacyMetamodelProfile` enthält denselben Compilerbezug und einen Versionsstand des semantischen Extraktors. Es beschreibt ausdrücklich, welche Modellfelder vergleichbar sind. Das optionale `LegacyApiProfile` referenziert dieses Profil und ergänzt die tatsächlich zugesagten Java-Typen, API-/gegebenenfalls ABI-Verträge, Referenzartefakte und Verbraucher. `LegacyTransferProfile` bindet Reader-/Writer-/Validatorversionen und Transferoptionen. `DbCompatibilityProfile` wird in [Überbau und Datenbanken](12-transfer-validierung-und-datenbanken.md) spezifiziert.

Profil-IDs müssen eine konkrete, im Manifest vollständig beschriebene Menge von Annahmen bezeichnen. Ein Arbeitsname wie `ili2c-reference-2026-10-18` ist zulässig, wenn die tatsächlich ausgeführte Version samt Hash dahinter steht. Ein Tag `latest` ohne diesen Nachweis ist unzulässig.

### 3.3 Fixture-Metadaten

Jeder fachlich bedeutende Fall erhält mindestens:

| Feld | Inhalt |
|---|---|
| `id` | Stabile eindeutige Fall-ID |
| `languages` | Explizit `ILI23`, `ILI24` oder begründet ausgeschlossener Umfang |
| `sources` | Relative Dateien, Hashes, Modelldeklarationen und benötigte Abhängigkeiten |
| `roots` | Angeforderte Modellselektoren |
| `options` | Relevante explizite Compiler-/Profiloptionen |
| `expectation` | Akzeptanz, Diagnose-/Semantikmerkmale oder erwartete offene Entscheidung |
| `authority` | Normabschnitt, Referenztest, beobachtetes Legacyverhalten oder begründete Designentscheidung |
| `requirements` | Abgedeckte REQ-IDs |
| `provenance` | Herkunft, ursprünglicher Pfad und beibehaltene Lizenzinformation |

Der Agent darf keine Normerwartung allein deshalb festlegen, weil eine neue Implementierung das entsprechende Ergebnis liefert. Selbst erzeugte Regressionstests benötigen die fachliche Begründung des Fixes.

## 4. Externe Compiler-Testsuite

Die untersuchte [`ilic-fork`-Pipeline](https://github.com/edigonzales/ilic-fork/blob/c601f1c9d88eb68f67991fd05fc4653884d104c7/.github/workflows/ci.yml) bindet die externe [INTERLIS Compiler Testsuite](https://codeberg.org/edigonzales/interlis-compiler-testsuite) am Stand `88576ffc4cd491dd72d2375eb897ea9f3f55d2c3` ein. Sie ruft den Java-Runner `InterlisConformance.java` mit Suite-Datei, Compilerkommando und Baseline auf.

Die untersuchte [Conformance-Dokumentation](https://github.com/edigonzales/ilic-fork/blob/c601f1c9d88eb68f67991fd05fc4653884d104c7/docs/conformance.md) nennt **610 Ergebnisfälle einschliesslich 1.0, 2.3 und 2.4** sowie einen dokumentierten ilic-Stand von **599 PASS und 11 XFAIL**. Das ist eine Aussage dieser Referenzdokumentation. Die Analyse hat den vollständigen Lauf nicht erneut ausgeführt. Die Anzahl der tatsächlich auszuwählenden 2.3-/2.4-Fälle muss der Agent aus dem gepinnten Suite-Inhalt bestimmen.

Der neue Harness muss folgende Eigenschaften haben:

1. Die unveränderte gepinnte Suite wird reproduzierbar beschafft und aufbewahrt.
2. Eine explizite Auswahl erfasst den zugesagten 2.3-/2.4-Umfang.
3. Ausgeschlossene 1.0-/2.2-Fälle erscheinen mit Grund im Bericht.
4. Akzeptanz/Ablehnung wird vom tatsächlichen öffentlichen Compilerkommando geprüft.
5. Prozessfehler, Hänger, fehlende Dateien und unlesbare Resultate werden als Infrastrukturfehler erfasst, nicht als erwartete Modellablehnung.
6. Baselineabweichungen sind fallbezogen begründet.

Der Harness erhält intern `ConformanceRunner.run(ConformanceRunRequest request, CancellationToken token) -> ConformanceReport`. `ConformanceRunRequest` enthält Suite-Manifest, Sprachfilter, `CompilerCommand`, Umgebung, Zeit-/Speicherlimits und Baseline. `CompilerCommand` besteht aus ausführbarer Datei und einer Argumentliste, nicht aus einer frei zusammengesetzten Shellzeile.

`ConformanceReport` enthält ausgewählte, ausgeschlossene und ausgeführte Fälle sowie `PASS`, `XFAIL`, `FAIL`, `XPASS` und `ERROR`. Wenn der externe Runner andere Details verwendet, wird die Abbildung dokumentiert. `XPASS` ist kein still zu ignorierendes Glück: Die alte erwartete Abweichung muss geprüft und gegebenenfalls bewusst aus der Baseline entfernt werden. Der Gesamtlauf ist erst nach dieser Bewertung grün.

Eine Menge von XFAILs darf den Zwischenstand beschreiben. Sie ersetzt nicht das P2-Ziel vollständiger Unterstützung des vereinbarten Sprachumfangs. Bei einem normkonformen absichtlichen Unterschied gegenüber ili2c ist eine eigene Erwartung und keine unbegründete Dauer-XFAIL-Markierung nötig.

Der Projektwrapper darf einen Referenz-FAIL nur nach einer bewusst versionierten
Normerwartung als überprüfte Referenzausnahme ausweisen. Jeder Eintrag enthält
Suite-ID, Sprachversion, Original-/Gegenprobenhashes, Normstelle, eigene Erwartung
und die zulässigen strukturierten Diagnosen. Die aktuelle CLI prüft Original
und positive Gegenprobe zusätzlich mit JSON-Diagnostik. Erwartet das Projekt
die Annahme des Originals entgegen einer negativen Referenzerwartung, muss
zusätzlich eine gepinnte negative Gegenprobe mit exakt den zulässigen
strukturierten Diagnosen bestehen. Unerwartete Diagnosen,
Implementierungssperren, geänderte Quellen, fehlende Resultate, Prozessfehler und
unerwartete Annahmen verhindern Erfolg. Der rohe Referenzbericht bleibt erhalten;
Referenzerwartung, tatsächliches Ergebnis und eigene Bewertung sind im
Projektbericht getrennt sichtbar. Eine solche Ausnahme ersetzt keine fehlende
Implementierung. Die bestehende INTERLIS-1-Ausnahme bleibt separat.


## 5. Semantischer Differentialvergleich

### 5.1 Warum reine Akzeptanz nicht genügt

Zwei Compiler können dasselbe Modell akzeptieren und dennoch unterschiedliche effektive Attributreihenfolgen, Typen oder Transfernamen liefern. Gerade die spätere Datenbankkompatibilität hängt an solchen Ergebnissen. Die alte Metamodellmethode [`Viewable.getAttributesAndRoles2`](https://github.com/claeis/ili2c/blob/cbccff35fc98fdfa370e42e3bb39bb4edd70a658/ili2c-core/src/main/java/ch/interlis/ili2c/metamodel/Viewable.java) ist ein konkreter zu untersuchender Vertrag.

`LegacySemanticExtractor.extract(ReferenceInvocation invocation) -> CanonicalSchemaDocument` läuft mit der gepinnten alten Bibliothek. Wegen ihrer globalen Zustände darf der Harness sie in einem getrennten Prozess ausführen. Der neue Compiler wird über `CompiledSchemaCanonicalizer.export(CompiledSchema schema) -> CanonicalSchemaDocument` projiziert.

Der kanonische Export enthält mindestens:

- deklarierte Modelle, Topics, Elemente und ihre fachlichen Identitäten;
- deklarierte und effektive Vererbung-/Attribut-/Rollenbeziehungen;
- aufgelöste Typen, Aliase, Genauigkeit, Kardinalitäten, Enumstruktur und Geometrietypen;
- Referenzziele und typisierte Ausdrucks-/Pfadstruktur;
- Constraints, Views, Funktionen, Einheiten und Übersetzungsbeziehungen;
- relevante Metaattribute und vordefinierte Modellbezüge;
- explizite Angaben zu rechtmässig aufgeschobenen Bindungen.

Die Darstellung ersetzt lauflokale IDs durch fachlich begründete Vergleichsschlüssel. Bei fehlerhaften Modellen mit doppelten Namen benötigt sie zusätzliche Quell-/Deklarationsanker; qualifizierte Namen allein genügen nicht.

**Reihenfolgen dürfen nicht pauschal sortiert werden.** Semantisch ungeordnete Mengen werden kanonisiert. Geordnete Attribute, Enumglieder, Pfadschritte und Ausdrucksoperanden behalten ihre Bedeutung. Eine Normalisierung, die den gerade zu findenden Fehler entfernt, ist unzulässig.

### 5.2 Vergleichsstatus und Methoden

`SemanticComparator.compare(CanonicalSchemaDocument expected, CanonicalSchemaDocument actual, ComparisonPolicy policy)` liefert `SemanticDiff` mit Feldpfad, erwarteter/tatsächlicher Bedeutung und Einstufung.

`ComparisonPolicy` unterscheidet verpflichtend vergleichbare Felder, ausdrücklich nicht vergleichbare Felder und zulässige Normalisierungen. Beispielsweise darf ein alter Compiler mit nur Zeilenangaben nicht so behandelt werden, als hätte er präzise UTF-16-Spalten geliefert. Ein nicht vergleichbares Feld wird gemeldet, nicht als erfolgreich identisch gezählt.

Abweichungen werden klassifiziert als:

| Klasse | Bedeutung und Folge |
|---|---|
| Neue Regression | Neue Implementierung verletzt Norm oder zugesagtes Legacyprofil; Fix erforderlich |
| Bewusste Normkorrektur | Referenztool weicht von begründeter Normerwartung ab; Entscheidung und Verbraucherwirkung dokumentieren |
| Profilunterschied | Beide Ergebnisse sind an verschiedene explizite Optionen/Versionen gebunden; Testeingaben prüfen |
| Extraktorfehler | Vergleichswerkzeug erfasst die Semantik falsch; keine Compileränderung als Workaround |
| Ungeklärt | Aussage noch nicht belegt; kein grüner Kompatibilitätsclaim |

Ein unabhängiges .NET-Werkzeug kann als zusätzlicher Differentialpartner dienen. GeoWerkstatts [`CompilerComparison`](https://github.com/geowerkstatt/GeoW.Interlis.Tools/blob/main/tests/Geowerkstatt.Interlis.Compiler.Test/ToolComparison/CompilerComparison.cs) zeigt einen solchen Vergleich mit ausdrücklichen Abweichungserwartungen. Ein Mehrheitsvotum von Compilern ersetzt jedoch nicht die Normprüfung.

## 6. Syntax-, Semantik- und Metamodelltests

### 6.1 Syntax und Quelltext

Pflichtfälle sind leere und abgeschnittene Dateien, Lexerfehler, fehlende Tokens, zusätzlicher Inhalt hinter dem Modell, mehrere Modelle je Datei, Kommentare/Whitespace, LF/CRLF, Unicode vor einer Diagnose und versionsabhängige Schlüsselwörter. Die Sprache 2.3 darf nicht allein deshalb 2.4-Konstrukte akzeptieren, weil dieselbe Grammatik sie erkennt.

`LosslessSyntaxTest` prüft, dass Originaltext über Tokens, Lücken und Quellhaltung vollständig wiedergegeben werden kann. Es verlangt keine bestimmte interne Baumform. `ParseCoverageTest` prüft, dass synthetische und fehlerhafte Bereiche markiert bleiben. `AntlrBoundaryTest` prüft, dass öffentliche API-Signaturen keine generierten Parserklassen enthalten.

### 6.2 Fachliche Merkmalsmatrix

Der Agent führt pro Merkmal gültige, ungültige und editorunvollständige Fälle, soweit sinnvoll:

| Familie | Erforderliche Testdimensionen |
|---|---|
| Namen und Imports | Scopes, Sichtbarkeit, Reihenfolge, Mehrdeutigkeit, fehlende Quelle, mehrere Modelle je Datei |
| Vererbung und Rollen | Basis-/Spezialfälle, Überschreibung, effektive Reihenfolge, Assoziationen und Einbettung |
| Typen und Werte | Aliase, Genauigkeit, Grenzen, Einheiten, Kardinalitäten, Enumerationen und Geometriedomänen |
| Ausdrücke und Pfade | Operandtypen, Pfadschritte, Funktionen, Undefiniertsemantik und Constraints |
| Erweiterte Modelle | Views, Übersetzungen, Metadaten, vordefinierte Modelle, 2.4-spezifische Merkmale |
| Rechtmässige Spätbindung | GENERIC/DEFERRED getrennt von Fehler- und Unaufgelöstzuständen |

Konkrete Fälle und Normabschnitte werden mit der Implementierung einer Regel ergänzt. Ein Agent darf nicht alle Tests einer grossen Familie durch einen einzigen trivialen Positivfall als erledigt markieren.

### 6.3 Unveränderlichkeit und Gate

`CompiledSchemaGateTest` prüft, dass nur vollständig geprüfte angeforderte Modellabhängigkeiten als Schema veröffentlicht werden. Ein Fehler in einem unabhängigen Workspace-Modell blockiert diese Root-Closure nicht. Ein Fehler in einer benötigten Abhängigkeit blockiert sie auch dann, wenn die Anzeige den Folgefehler unterdrückt.

`RejectionSummaryTest` prüft die vom Gate erzeugte Ablehnungszusammenfassung. Es gibt getrennte Fälle für `SOURCE_ERRORS`, `WARNINGS_REJECTED`, `MISSING_INPUT`, `AMBIGUOUS_INPUT`, `ENVIRONMENT_FAILURE` und `INCOMPLETE_SUPPORT` sowie Kombinationen. `REJECT_WARNINGS` muss bei vollständiger Coverage und unveränderter `WARNING`-Severity ausdrücklich als Policygrund erscheinen. Die betroffenen Diagnose-IDs gehören zur angeforderten Closure; ein unabhängiger Workspacefehler darf nicht in ihre Ablehnungsursache geraten. CLI, WASM und Logs übernehmen dieselbe Zusammenfassung und implementieren keine eigene zweite Gateentscheidung.

`SnapshotIsolationTest` erzeugt zwei Analysestände und verändert danach Eingabesammlungen beziehungsweise interne Builder. Bereits veröffentlichte Snapshots müssen gleich bleiben. Die öffentliche API darf keine beschreibbaren Collections oder Runtimecaches herausreichen.

`SourceOrderDeterminismTest` übergibt dieselben Quellen in verschieden aufgebauten Maps. Die kanonische Verarbeitung und Diagnoseordnung verwendet `SourceId.value()` nach `String.compareTo`, also lexikografisch nach UTF-16-Codeeinheiten ohne Locale-Kollation. Ergebnisse bleiben gleich. Fachliche Deklarations-/Transferreihenfolgen innerhalb eines Modells werden durch diese Sortierung nicht umgeordnet; eine abweichende historische CLI-Darstellung ist ein gesonderter Profilvertrag.

`P3CliLifecycleContractTest` verwendet eine injizierte Factory und einen aufzeichnenden Observer. Kurze und volle Engine-Aufrufe erzeugen jeweils genau einen Lauf. Ein Workspace-Adapter emittiert unter der weitergereichten Engine-RunId keine zusätzliche Start-/Endfolge. Zwei parallele Host-Eltern mit jeweils frischen Enginekind-Kontexten behalten Parent-ID, Korrelationsattribute und lauflokale Ereignisreihenfolge. Eine erfolgreiche Kompilierung und ein anschliessender Ausgabe-/Formatierungsfehler dürfen korrekt einen erfolgreichen Kindlauf und fehlgeschlagenen Elternlauf ergeben. ThreadLocal- beziehungsweise globale aktuelle Kontexte sind kein zulässiger Implementierungstrick.

Ein Gettertest für jeden Record ist nicht erforderlich. Geprüft werden die tatsächlichen Grenzen: defensive Ownership, ID-Zugehörigkeit, Graphkonsistenz, geordnete Eigenschaften und Gate-Zugriff.

## 7. Workspace, LSP und Editierfolgen

`EditScenario` besteht aus Startquellen und einer geordneten Liste von `Update`, `Remove`, `Add`, `Analyze`, `Compile`, `Query` und gegebenenfalls `Cancel`-Schritten. Alle Revisionen und erwarteten Konflikte sind explizit.

`IncrementalParityRunner.run(EditScenario scenario)` erzeugt nach jedem relevanten Schritt sowohl den Workspace-Stand als auch eine frische Engineanalyse der gleichen Quellen, Optionen und Bindungen. Verglichen werden Diagnosen, semantische Abfragen, gültiger Compile-Status und ausgewählte Diagrammprojektionen. Implementierungsmetriken wie Cache-Treffer gehören in einen zusätzlichen Bericht.

Pflichtsequenzen umfassen:

1. Kommentar ändern: fachliche Semantik bleibt gleich; Positionen und Dokumentation passen zum neuen Stand.
2. Attributtyp ändern: relevante abhängige Typ-/Constraintprüfungen werden aktualisiert.
3. Fehlendes Modell hinzufügen: negative Auflösungsabhängigkeit wird invalidiert.
4. Import entfernen und wiederherstellen: keine alten Ziele bleiben als aktuelle Bindung erhalten.
5. Deklaration umbenennen: Referenzen und Diagrammidentitäten folgen dem definierten Identitätsvertrag.
6. Syntax mitten in einer Klasse abbrechen und reparieren: benachbarte brauchbare Elemente bleiben abfragbar; später entstehen keine Geisterdiagnosen.
7. Overlay, Sprachversion oder Resolverbindung ändern: passende Caches werden invalidiert.
8. Ergebnis eines älteren Standes verspätet fertigstellen: keine veraltete Publikation.

Die erste Implementierung darf die geänderte Datei vollständig neu parsen. Ein Test soll daher nicht vorzeitig exakt einen wiederverwendeten Teilbaum verlangen. Er prüft die zugesagte Cachegrenze und korrektes Ergebnis. Feingranulare Leistungszusagen erhalten erst nach ihrer Implementierung eigene Gates.

`LspContractHarness` startet den echten Server mit stdio, sendet `initialize`, Öffnen/Ändern/Schliessen und fachliche Requests und prüft Versionen, Bereiche, Abbruch und Protokollreinheit. Rename-Tests umfassen mehrere Namenssegmente, mehrere Dateien und einen Versionskonflikt zwischen Vorschlag und Anwendung. Diese Tests müssen semantisch begründete Zielreferenzen prüfen, nicht bloss Textvorkommen ersetzen.

## 8. JVM, WASM und Browser

`PortableFixtureRunner` nimmt denselben versionierten Eingabevertrag für JVM und die WASM-Bridge. Die neue Implementierung braucht einen kanonischen Ergebnisexport für Diagnosecodes/-argumente, relevante Modellinformationen, Coverage und Ergebnisstatus. Laufzeitdaten werden separat erfasst.

Die Paritätsmatrix enthält mindestens den JVM-Lauf und den aktiv entwickelten TeaVM-WasmGC-Lauf. TeaVM-JavaScript und Web Image bleiben explizite zusätzliche Zielprofile mit eigenem Status. Ein noch nicht gebautes Ziel wird `NOT_RUN` oder `BLOCKED` mit Grund, nicht `PASS`.

Browserberichte nennen Browsername, Version, Betriebssystem, verwendete Wasm-Funktionen und Startoptionen. Ein Node-Test beweist keine Safari- oder Firefox-Unterstützung. Ebenso belegt ein Parserbeispiel nicht, dass alle verwendeten späteren Klassenbibliotheksfunktionen funktionieren.

Pflichtbereiche sind exakte grosse Zahlen, Dezimalpräzision, Unicode, strukturierte Ausgaben, Importe als bereitgestellte Quellen, Fehlerdiagnostik, getypte Semantik und wiederholte Sitzungsänderungen. Zusatztests gelten für Workerabbruch, Speicherfreigabe, abgelaufene Snapshots und ungültige Protokolleingaben.

Performanceprofile messen Kaltstart einschliesslich Laden/Initialisieren getrennt von warmer Analyse. Downloadgrösse wird komprimiert und unkomprimiert angegeben. Der P3-Referenzkorpus ist der gepinnte Index `https://geo.so.ch/models/ilimodels.xml` samt ausgewählten 2.3/2.4-Modellen. Messungen auf kleinen Testmodellen dürfen nicht als vollständige Produktionswerte präsentiert werden.

## 9. Fuzzing, Ressourcen und Robustheit

Fuzzing beginnt mit reproduzierbaren Mutationen gültiger und bereits gezielt ungültiger Fixtures: Tokens löschen, Einfügepositionen variieren, Kommentar-/Stringgrenzen beschädigen, Verschachtelung erhöhen, Zahlen verlängern, Imports zyklisch beziehungsweise fehlend machen und ungültige Unicode-Konstellationen im jeweils zulässigen Eingabevertrag prüfen.

`FuzzCase` enthält Seed, Generatorversion, ursprüngliches Fixture, Mutation und Limits. `FuzzRunner.execute(FuzzCase testCase)` liefert Status, Dauer, Peak-Messwerte soweit zuverlässig verfügbar und minimierbaren Fehlerinput. Zufällige Flakiness ohne aufbewahrten Seed ist kein nützliches Gate.

Folgende Invarianten gelten für alle Fuzzläufe:

- Kein Hänger über dem konfigurierten Budget.
- Keine unbeschränkte Diagnose-/Logausgabe.
- Kein Erfolgsschema bei unvollständiger Prüfung.
- Keine fremden oder ausserhalb der Quelle liegenden Bereiche.
- Keine zyklischen Ursachen- oder ungültigen semantischen Referenzgraphen.
- Kontrollierter Abbruch bei überschrittenem Budget.
- Interne Defekte werden als Defekte erfasst und nicht als normale Syntaxablehnung umetikettiert.

Differentialfuzzing nutzt Legacycompiler ergänzend. Eine unterschiedliche Akzeptanz wird minimiert und fachlich bewertet. Die bekannte allgemeine Möglichkeit eines Fehlers rechtfertigt nicht, jeden Lauf pauschal mehrfach auszuführen; Wiederholungen dienen konkreten Nichtdeterminismus- oder Stabilitätsfragen.

Ressourcenlimits sind zunächst bewusst konservativ und konfigurierbar. Konkrete CI-Grenzen werden nach der ersten Messung festgelegt und dokumentiert. Dieses Dokument erfindet keine nicht gemessene Millisekundengarantie. Ein Benchmark darf wegen langsamer CI-Hardware nicht fälschlich zur Sprachnorm werden.

## 10. CLI- und Diagnosekompatibilität

`ReferenceCliRecorder.record(CliFixture fixture, LegacyCompilerProfile profile)` startet das Referenzwerkzeug mit Argumentliste und kontrollierter Umgebung. Es erfasst Exitcode, stdout und stderr getrennt sowie erzeugte Dateien und relevante Metadaten. Temporäre Pfade, Locale, Zeilenende und Laufzeitversion werden dokumentiert.

`CliCompatibilityComparator.compare(RecordedCliResult legacy, RecordedCliResult replacement, CliComparisonPolicy policy)` liefert Unterschiede je Kanal und Datei. Erlaubte Normalisierungen sind einzeln begründet, etwa ein temporärer absoluter Arbeitsordner. Sie dürfen keine Diagnoseposition, keinen Modellnamen, keine Reihenfolge oder fachlich relevante Meldung entfernen.

`P3OutcomePriorityContractTest` prüft die Abbildung der vom Compiler gelieferten `RejectionSummary` auf die internen CLI-Zustände und anschliessend auf profilierte Exitcodes. Fehlende Implementierung und abgelehnte Warnungen dürfen nicht als nachgewiesener ungültiger Quelltext ausgegeben werden. Ein erfolgreicher Compile mit anschliessendem Ausgabe-, Formatierungs- oder Flushfehler ergibt keinen erfolgreichen Gesamtaufruf. Bei mehreren Ablehnungsgründen gilt die in Kapitel 18 ausdrücklich festgelegte Priorität, während alle Gründe im strukturierten Resultat erhalten bleiben.

Zu erfassen sind mindestens gültiger Compile, Syntaxfehler, Semantikfehler, fehlende Datei, fehlender Import, ungültige Option, Hilfe-/Versionsaufruf und mehrere Eingabedateien. Formatierungsoptionen erhalten eigene Profile und Fixtures; ein nicht implementierter Formatierungsmodus darf nicht still als vorhandener CLI-Umfang gelten.

Die strukturierte neue Ausgabe erhält unabhängig davon Schema- und Roundtriptests. Legacytext kann bei gleicher fachlicher Diagnose anders gerendert werden. Gleichheit von Text allein beweist keine Gleichheit der zugrunde liegenden Typ-/Referenzinformation.

## 11. Transfer-, Validierungs- und DB-Gates

P4 erweitert die Matrix mit kleinen erklärbaren XTF-Fällen und grösseren realen Transfers. Der alte und neue Reader/Writer werden in beiden Richtungen kombiniert. Verglichen werden fachliche Objektidentitäten, Werte, Beziehungen, Reihenfolgen und gegebenenfalls genaue Formatverträge. Reine XML-Textgleichheit ist nur dort erforderlich, wo ausdrücklich zugesagt; eine Normalisierung darf semantisch relevante Reihenfolgen oder Undefiniertunterschiede nicht entfernen.

Validatorvergleiche prüfen Regelabdeckung, Befunde, Objekt-/Modellbezüge und Ergebnisse. Ein lokaler Modus wird gegen denselben geprüften Umfang verglichen und darf nicht mit vollständiger Validität gleichgesetzt werden. Geometrieoptimierungen benötigen einschlägige Edge-Cases und Belege unveränderter fachlicher Entscheidungen.

Für P5 gilt diese Mindestmatrix pro ausdrücklich unterstütztem DB-Profil:

| Erzeugung | Folgeoperationen | Pflichtprüfung |
|---|---|---|
| Alt erzeugt Schema | Neu importiert, exportiert und aktualisiert | Vorhandene Mappings korrekt gelesen; gespeicherte Struktur unverändert kompatibel |
| Neu erzeugt Schema | Alt importiert, exportiert und aktualisiert | Alte Werkzeuge erkennen Metadaten und Abbildung |
| Alt erzeugt und befüllt | Neu liest und exportiert | Identitäten, Referenzen, Geometrie und Namen erhalten |
| Neu erzeugt und befüllt | Alt liest und exportiert | Kein verstecktes neues Format unter altem Profil |
| Vorhandenes Schema beschädigt/verändert | Neu plant oder führt aus | Konkreter Konsistenz-/Vorbedingungsfehler; keine stille Neuzuordnung |

`DatabaseCompatibilityRunner.run(DatabaseCompatibilityScenario scenario)` orchestriert isolierte Testdatenbanken, gepinnte Tools und konfigurierte Dialektversionen. Es speichert strukturierte Schemainformationen, relevante Metadaten, Operationsergebnisse und normalisierte Transferinhalte. Für verfügbare Backends können Container dienen; die Tests dürfen keine produktiven Datenbanken voraussetzen.

`SchemaComparator` vergleicht Tabellen/Spalten, Datentypen, technische Metadaten, Fremdschlüssel und relevante Indizes nach Profil. Einige automatisch erzeugte Constraintnamen können profilabhängig ignorierbar sein; dies braucht eine explizite Begründung. Datenbankinterne OIDs oder zufällige physische Reihenfolgen werden nicht als fachlicher Vertrag erfunden.

## 12. CI-Gates nach Phase

| Phase | Verpflichtender Nachweis | Noch keine zulässige Behauptung |
|---|---|---|
| P0 | Gradle-Reproduzierbarkeit, Modulgrenzen, gepinnte Grundlagen, dokumentierter aktueller WASM-Versuch | Vollständiger Compiler oder Browserreife |
| P1 | Durchgängige semantische Strecke, beide Sprachprofile für implementierte Fälle, dirty Editorstände, frische/inkrementelle Gleichheit, JVM-/WASM-Parität | Vollständige 2.3-/2.4-Abdeckung |
| P2 | Gesamte zugesagte Sprachabdeckung, ausgewählte externe Suite, originale und zusätzliche Semantiktests, reale eingefrorene Korpora | Datenbankkompatibilität oder vollständige Validatorabdeckung |
| P3 | CLI-Profile, Quellformatierung, LSP-/Diagrammintegration, Performance auf dem Geo-Korpus und Browserberichte, publizierbare API-Verträge | Ungetestete Plattform-/Backendzusagen |
| P4 | Transfer-/Validierungspläne und Laufzeit, bidirektionale Transferfälle, Coverage- und Ressourcenberichte | Gesamte historische ili2db-Schemafamilie |
| P5 | Benannte DB-Profile mit bidirektionalen Operationen, DDL-/Metadatenvergleich, Abbruch-/Fehlerfälle | Nicht ausgeführte historische Versionen oder Backends |

LSP, Diagramm und WASM beginnen bereits in P1. Die Nennung von P3 bezeichnet Integrationstiefe, Leistung und Kompatibilitätsreife; diese Funktionen dürfen nicht bis dahin vollständig aufgeschoben werden.

Schnelle lokale Gates prüfen die direkt betroffene fachliche Strecke. Umfangreiche externe Compiler-/Browser-/DB-Matrizen werden nach definiertem Anlass oder in geeigneten CI-Jobs ausgeführt. Der Agent soll Tests nicht beliebig verbreitern, wenn die relevanten Risiken bereits ausreichend geprüft sind. Für einen Release gelten jedoch alle zugesagten Profilgates.

## 13. Berichte und Definition of Done

Jeder Arbeitspaketabschluss liefert:

1. Implementierten Umfang mit REQ-IDs und betroffenen Klassen-/Methodenverträgen.
2. Tatsächlich ausgeführte Kommandos beziehungsweise eindeutig benannte Gradle-Aufgaben.
3. Ergebnisse, einschliesslich fehlgeschlagener, nicht ausgeführter und begründet ausgeschlossener Fälle.
4. Verwendete Source-/Tool-/Runtimeversionen und Input-Fingerprints.
5. Bewusste Abweichungen, verbleibende Risiken und notwendige Folgearbeiten.

`VerificationReport` ist maschinenlesbar und enthält `runId`, Repositoryrevision, Toolchains, Gates, Fallzahlen und Artefaktbezüge. Menschliche Zusammenfassungen werden daraus abgeleitet. Ein grünes CI-Badge allein ist keine vollständige Kompatibilitätsbeschreibung.

Ein Arbeitspaket ist fertig, wenn seine zugesagten Verträge implementiert und geprüft sind, die Abdeckungsmatrix konsistent ist und keine offenen Fehler als bestandene Tests ausgegeben werden. Eine spätere Phase darf vorbereitet sein, muss aber als geplant sichtbar bleiben. Die zentrale [Abdeckungsmatrix und Review](17-abdeckungsmatrix-und-review.md) hält diese Unterschiede fest.
