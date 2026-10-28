# A2 – Regelinventar in Arbeit

**Abnahme: fehlender Nachweis.** A1 ist separat abgenommen; A2-R1/R2 sind inzwischen unabhängig geschlossen, A2 und Phase A
sind offen. Diese Inventur ändert keine Compilerregel, Referenzerwartung oder
XFAIL-Ausnahme. Massgeblich bleibt der [Plan A–I](g2-completion-plan.md).

## Erfasste Quellen

Die [Quellinventur](g2-source-inventory.json) enthält sämtliche 125 Parser- und
196 Lexerproduktionen. Verschachtelte Alternativen und Wiederholungen werden
mit eigenem Quellbereich und Texthash erfasst: 1218 Syntaxstellen. Literale,
Zeichenklassen, Kommentare und Lexerkommandos werden dabei unterschieden.
Diese Zahl bezeichnet weder unterschiedliche Normregeln noch nachgewiesene
Fähigkeiten. Identisch geschriebene Alternativen bleiben einzeln sichtbar.

Hinzu kommen die 289 nummerierten Anforderungen aus ihren Fachkapiteln und
21 tatsächlich deklarierte Standardfunktionen. Kapitel 17 wird als abgeleiteter Index
nicht erneut als Vertragsquelle gezählt; ADR-Entscheidungen sind keine weiteren
Anforderungs-IDs. Die unnummerierten Fach-/Methodenverträge fehlen noch im
einzelnen Abgleich und werden nicht durch die 289 IDs ersetzt.

Für beide gepinnten Norm-PDFs sind alle Seitenextrakte gehasht und die
gedruckten Inhaltsverzeichnisse einschliesslich Anhängen erfasst: 139 Locator
für 2.3, 172 für 2.4. Ein Locator ist noch kein auditierter Normabsatz. Insbesondere
werden Transferkapitel und Anhänge dadurch weder ausgeschlossen noch abgenommen.
Die PDF-Autorenprüfung verwendet pypdf 6.10.0; daraus entsteht keine Abhängigkeit
des Java-Compilers oder seines normalen Builds.

Die bestehende [Regelmatrix](g2-rule-matrix.json) behält alle 332 historischen
IDs und Zustände. Jede Zeile besitzt nun einen zuständigen Schritt B–H.
Die frühere E1–E5-Zuordnung bleibt als historische Gruppierung erhalten.
`atomicRules` beginnt mit zehn einzelnen Bedingungen und konkreten positiven,
negativen und Modell-/Queryabnahmen. Ihre Zitate werden exakt gegen beide PDFs
geprüft; keine dieser Bedingungen ist dadurch unabhängig abgenommen.
`candidateTests` sind zu prüfende Zuordnungen, kein neuer Abdeckungsnachweis.

## Reproduktion und Grenze

`python3 tools/reference/g2_inventory.py --out build/reports/g2/A2/source-scan.json`
erzeugt ausschliesslich Build-Ausgaben. Mit `--norm23 build/norms/ili23.pdf
--norm24 build/norms/ili24.pdf` werden zusätzlich die gepinnten PDFs mit einem
Python mit pypdf gelesen. Konkrete Werkzeuge/Kommandos stehen im A2-Nachweis.
Die versionierte Quellinventur wird ausdrücklich übernommen; der Scan verändert
keine Matrix, Baseline oder normative Erwartung automatisch.

`./gradlew g2EvidenceTest` prüft die technischen Fehlerpfade der Evidenz- und
Inventurhilfe. Ein grüner Hilfstest ist keine A2- oder Sprachenabnahme.

## Bis zur A2-Abnahme konkret offen

- Jeden Sachabsatz beider Sprachkapitel, die compilerrelevanten Regeln der
  Grundprinzipien/Transferkapitel und alle normativen Anhänge in einzelne
  Bedingungen zerlegen; rein spätere Ausführung mit konkretem Vertragsgrund
  kennzeichnen. Die bisherigen TOC-Locator reichen dafür nicht.
- Die unnummerierten Verträge in 04–10, 13 und 16 sowie den Featurekatalog 05
  einzeln zuordnen. Für Verbraucherpflichten den G2-Kernanteil und die späteren
  P3-/P4-/P5-Anteile ausdrücklich trennen.
- Jede Syntaxstelle auf die zuständige Normbedingung und konkrete strukturierte
  AST-/Modellprüfung beziehen. Annahme/Ablehnung oder eine Testklasse allein
  genügen nicht. Die zehn Anfangsbedingungen ersetzen die restliche Sprache nicht.
- Für die 18 vorhandenen normativen Referenzausnahmen jeweils beide Versionen,
  Original-/Gegenfälle, tatsächliche Diagnosen und Empfehlung fertig prüfen und
  dem Nutzer vorlegen. Der separate INTERLIS-1-Profilfall bleibt getrennt.
- Vollständigkeit des fertigen Inventars unabhängig prüfen lassen. Erst danach
  A3 und das Phasenreview abschliessen; keine Sprachkorrektur vorziehen.

## Konkrete zusätzliche Normfrage aus dem Builtin-Abgleich

Bei `areAreas2` widersprechen sich 2.4 §3.14 (S. 87: OBJECTS) und der normative
Anhang A (S. 122: OBJECT). Anhang A deklariert ausserdem `areAreas3` mit OBJECTS.
Acht [gehashte Prozesspaare](g2-A2-areas-signature-observations-2026-10-27.json)
zeigen: ili2c 5.6.9 akzeptiert areAreas2 auch mit 2.3-Header, kennt areAreas3
aber in keiner der beiden Fassungen. Der eigene aktuelle Compiler bietet beide
Namen nur in 2.4 an. Die 2.3-Deklarationsabschnitte enthalten keinen der Namen;
diese Beobachtung ersetzt keine vollständige normative Profilentscheidung.

`BUILTIN_AREAS2_AREAS3_NORM_PROFILE` gehört zu E2. Signaturinterpretation,
öffentliche Parameter-/Ergebnistypen und Gegenfälle bleiben dort offen.
Der ebenfalls offene E3-SET-Body-Kontext wird durch diese Namensproben nicht
abgenommen. Es gibt keine neue Erwartung, keinen neuen XFAIL und keine
vorweggenommene Aussage, dass ili2c hier die Norm verletzt.

## Unabhängiges Zwischenreview

Das [Review A2, Versuch 1](g2-reviews/A2-attempt1.md) urteilt **NACHARBEIT**.
A2-R1 betrifft ungesicherte Zuordnungs-/Abnahme-/Testlinkänderungen; dafür werden
nun tatsächliche Matrixschritte geprüft, die vollständigen Atomregel-Mappings
im Nachweis erhalten und Testlinks samt Quellen aufgelöst. Eine Änderung des
Aussagentexts bleibt eine manuell zu prüfende fachliche Änderung, verschwindet
aber nicht mehr aus dem Scan.

A2-R2 bis R4 bleiben offen: vollständige vordefinierte Inhalte/Sprachtabellen,
passende Vertrags-/Fallassertionen sowie der vollständige Absatz-/Anhangaudit.
Die falschen Kandidatzuordnungen für Gross-/Kleinschreibung und einzeilige
Strings wurden als fehlende Belege gekennzeichnet. Das senkt keinen Vertrag
und ersetzt keinen nötigen Test. Das [frische Review](g2-reviews/A2-attempt2.md) schliesst A2-R1 nach
20 technischen Tests und zwölf eigenen Gegenproben. Das Gesamturteil bleibt
NACHARBEIT wegen R2/R3/R4.

Die [Sprachprofil-Vorbereitung](g2-A2-language-profile-preparation-2026-10-27.json)
hält 154 reservierte Wörter für 2.3, 168 für 2.4 und 13 Featurezweige mit
Quellenhash fest. Sie ist ausdrücklich **kein** abgeschlossener R2-Nachweis.
Der nächste Arbeitspunkt bleibt das vollständige Einzelinventar der weiteren
vordefinierten Inhalte und lexikalischen Kontexte, anschliessend passende
Regel-/Fallassertionen und der vollständige Absatzabgleich.

## Aktueller Reviewstand nach R2

[R2 ist unabhängig geschlossen](g2-reviews/A2-R2-attempt2.md).
Der [vordefinierte Einzelkatalog](g2-A2-predefined-inventory.md) ersetzt den
früheren Platzhalter für übrige Builtins und Sprachprofile. Die weiter oben
beschriebenen R2-Lücken sind historischer Stand des ersten Zwischenreviews;
ihre beiden konkreten Nacharbeiten sind im frischen R2-Review geschlossen.
A2-R3/R4 und die A2-Gesamtabnahme bleiben offen. Die [R3-Vorbereitung](g2-A2-R3-assertion-preparation-2026-10-27.json)
enthält aktuelle Teilnachweise und ausdrücklich noch fehlende Assertions.

## A2-R3: Fortschritt vom 28. Oktober 2026

Sieben zusätzliche Bedingungen für String-Escapes, UTF-16 und Kommentare sind
mit exakten Auszügen beider Fassungen als geplante, noch nicht abgenommene
Regeln erfasst. Fünf bisherige Teilassertions sind mit der ursprünglichen
Testmethode und dem archivierten JVM-Bericht verknüpft; kein Testklassenname
allein zählt als vollständig. Die fachlichen Lücken bleiben in jeder Zeile
sichtbar. Der explizite Aufruf `g2_inventory.py --require-run-evidence --out
<build-Datei>` prüft zudem, dass die archivierten XMLs tatsächlich existieren,
zum Hash passen und die relevante Testmethode ohne Fehler oder Skip enthalten.
Der normale Quellcheck braucht diese generierten Dateien nicht; ein fehlender
Rohbericht kann somit keine erfolgreiche Reviewprüfung erhalten.

31 technische Prüfungen bestehen. [Fortschrittsnachweis](verification-2026-10-28-g2-A2-R3-progress.json).
Die Gesamtinventur und die unabhängige A2-Abnahme bleiben offen.
