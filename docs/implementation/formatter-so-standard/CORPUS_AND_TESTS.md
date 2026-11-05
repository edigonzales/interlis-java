# Referenzkorpus, Sollausgaben und Testmatrix

## 1. Zwei unterschiedliche Quellen von Wahrheit

**Stilreferenz:** Gepflegte Solothurner Originaldateien zeigen Layoutpraxis. Sie sind nicht automatisch fehlerfreie Syntax-/Semantiktestfälle und nicht jede vorhandene Formatierung soll kanonisch übernommen werden.

**Vertragsfixtures:** Kleine vollständig parsebare und bei Bedarf kompilierbare Modelle prüfen genau eine Regel. Ihre Sollausgabe wird aus STYLE_GUIDE/CONSTRAINT_LAYOUT festgelegt, nicht durch das gerade entwickelte Programm erzeugt und ungeprüft als Golden übernommen.

Originalkorpus, absichtlich verschlechterte Layoutvarianten und Sollausgaben sind drei getrennte Dateikategorien. Tests dürfen keine davon still überschreiben.

## 2. Gepinnte Referenzbasis

Repository: `sogis/sogis-interlis-repository`.
Commit der Recherche: `630e3550a93288a99dd9eb4d2d6dc804f9027107`.

Mindestens die folgenden vier Kernreferenzen untersuchen:

| Datei | Schwerpunkt |
|---|---|
| `models/AGI/SO_AGI_Datahub_20240403.ili` | Dokumentationsblöcke, Versionshistorie, dichte Attribute, Klassenabstände |
| `models/ADA/SO_ADA_Denkmal_20250114.ili` | Absätze, Dokumentation und unterschiedliche Kommentarlängen |
| `models/AWJF/SO_AWJF_Waldplan_20250312.ili` | AND/OR/NOT, Metaattribute, CONSTRAINTS OF, Pfade |
| `models/AFU/SO_AFU_Naturgefahren_20240515.ili` | Viele Constraints, lange Calls, verschiedene Kommentarformen |

Ergänzung auf etwa acht bis zwölf gezielt ausgewählte eigene Modelle, nicht nach einer zufälligen Gesamtanzahl. Kandidaten sind `models/HBA/SO_HBA_Immobilienportfolio_20260713.ili`, `models/AVT/SO_AVT_OevKov_20260727.ili`, `models/AGI/SO_AGI_MOpublic_20240711.ili`, `models/AFU/SO_AFU_Gewaesserraum_20260526.ili`. Exakte Existenz, Sprachversion und Eignung am Pin in F0 prüfen; fehlende Kandidaten begründet durch einen am selben Pin vorhandenen ersetzen. Keine neue Homepageversion unter denselben Herkunftsnamen mischen.

INTERLIS2.3 und2.4, LF/CRLF, Kopfvarianten, Klassen/Structures/Associations/Views, Domains/Enums, Kommentare mit und ohne `*`, Versionshistorien und Metaattribute abdecken. Gespiegelte fremde Modelle und `/replaced/` nicht als primäre SO-Stilnorm werten; bei konkreter Randfrage separat kennzeichnen.

## 3. Erwerb und Manifest

F0 darf Referenzen ausdrücklich aus dem gepinnten Repository beziehen. Danach Tests offline. Keine Netzabfrage bei jedem JUnit-Lauf und kein Formatieren in einem fremden Arbeitsbaum.

Ablagevorschlag unter `testdata/formatter/so-standard/`:

```text
corpus-manifest.json
originals/<source-path>.ili
variants/<case-id>.ili
golden/<case-id>.ili
cases/<self-contained-case>.ili
README.md
```

Originaldateien nur nach Prüfung der vorhandenen Nutzungs-/Lizenzhinweise mit Herkunft übernehmen; nicht selbst eine Lizenz erfinden. Für kleine abgeleitete Fixtures Herkunft und eigene Änderungen angeben. Keine font-/Binär-/ganze Repositoriumskopie im Testbaum.

Manifestdaten: schemaVersion, repository, commit, relativeOriginalPath, lokaleFixture, tatsächlicher SHA-256 der unveränderten Bytes, Bytezahl, beobachtete EOLs/Encoding/BOM, Sprachversion, Prüfrollen und erwartete Baselinesyntax. Eventuelle Auszüge zusätzlich durch Originalbereich und eigene Änderungen kenntlich machen. Git-Blob-SHA ist nicht der SHA-256 der Datei; nicht verwechseln.

Beispiel eines Schemas, mit echten Werten zu füllen, kein fertiger Nachweis:

```json
{
  "schemaVersion": 1,
  "repository": "sogis/sogis-interlis-repository",
  "commit": "630e3550a93288a99dd9eb4d2d6dc804f9027107",
  "files": [
    {
      "sourcePath": "models/AGI/SO_AGI_Datahub_20240403.ili",
      "fixturePath": "originals/AGI/SO_AGI_Datahub_20240403.ili",
      "sha256": "<ACTUAL_FILE_SHA256>",
      "bytes": "<ACTUAL_INTEGER>",
      "roles": ["comments", "declarations", "metadata"],
      "baselineSyntax": "<ACCEPTED_OR_REJECTED_WITH_EVIDENCE>"
    }
  ]
}
```

Vor einem Testlauf Hashes überprüfen. Ein geändertes Original ist ein Fehler, keine automatische Manifestaktualisierung. Nicht erfolgreiche Fixtureauswahl löschen, um die Bilanz zu verschönern; ursprünglichen Befund dokumentieren.

## 4. Baseline und Golden-Abnahme

F0 führt den vorhandenen Formatter auf dem ausgewählten Korpus nur in ein neues Ausgabeverzeichnis aus. Erfassen: parsefähig/nicht parsefähig, alter Output, Differenz, bekannte Defizite, Kommentar-/Metadatenbindung. Danach konkrete Sollfälle festlegen.

Bei einer bereits vor dem Auftrag nicht parsebaren Referenzdatei bleibt die Baselineablehnung sichtbar. Sie ist kein positiver Formatierfall. Kleine reproduzierbare Ursache dokumentieren; nicht die gesamte Sprache als Nebenauftrag reparieren. Dennoch mindestens geeignete parsebare Gegenstücke und alle Pflichtfamilien mit vollständigen Fixtures abdecken. Systematische Lücken, die die Kernanforderung verhindern, bleiben echte Blocker.

Goldens aus nachvollziehbaren Regeln erstellen. Ein Review im Primäragenten ist hier ein interner Selbstcheck, kein vorgezogener unabhängiger OpenCode-Review. Unabhängiger Reviewer prüft später Beispiele und Diffs selbst. Keine binäre Behauptung „wie SO“ ohne konkreten Solltext.

## 5. Testmatrix

| Gruppe/ID | Fall | Erwarteter Nachweis |
|---|---|---|
| T01 | Gewöhnlicher Block und Docblock falsch eingerückt | Beide verschoben, gleicher erlaubter Randdelta, Inneninhalt unverändert |
| T02 | ASCII-Tabelle, Stern-/Nichtsternstil, leere Innenzeilen | Keine Reflow-/Trimänderung |
| T03 | Verschachtelter Blockkommentar | Originalspanne einmal, keine ungeschlossene Restsyntax |
| T04 | Mehrzeiliger Kommentar mit Code nach `*/` | Kein Verlust/keine Verdopplung des folgenden Tokens |
| T05 | Inlineblock zwischen CLASS und Namen/zwischen Ausdrucksatomen | Identischer Anker |
| T06 | `!!` standalone/trailing/EOF/innerhalb eines Ausdrucks | Text unverändert, nächster Code nicht verschluckt |
| T07 | `!!@` mit Duplicates, Quotes, Leerzeichenvarianten | Exakte Werte, Reihenfolge und Bindung |
| T08 | Mehrzeiliger Metawert mit LF/CRLF | Wert-EOL trotz angeforderter Normalisierung erhalten |
| T09 | `// Erklärung //` mit Strukturwörtern | Als Wertatom erhalten |
| T10 | Klassen/Structs/Associations/Views mit führenden Bündeln | Genau richtige Leerzeilenposition |
| T11 | MODEL/TOPIC/GRAPHIC/CONSTRAINTS OF und namenloses END | Korrekte strukturelle Einrückung |
| T12 | DOMAIN/ENUM/UNIT/FUNCTION/PARAMETER und Semikolons in Argumenten | Kein globales Statement-Splitting |
| T13 | `TEXT*100` vs `A * B`, signed numbers, ranges/cardinality | Kontextsensitive Abstände, gleiche Lexerfolge |
| T14 | `->`, `==`, `<>`, `=>`, Namenspfade und Doppelpunkte | Ungeteilte Sprachzeichen, gleiche Tokens |
| T15 | Kurze/lange/namentliche MANDATORY | Flat-/Breakgolden |
| T16 | CONSTRAINT Prozentzahl, beide Vergleiche | Exakte Zahllexeme, richtiger Kopf |
| T17 | EXISTENCE mit mehreren REQUIRED-IN-Alternativen | Reihenfolge, Separatorrollen, Flat/Break |
| T18 | UNIQUE global/named/BASKET/multiple keys | Vollständig, Reihenfolge stabil |
| T19 | UNIQUE WHERE und LOCAL mit Pfaden/Feldern | Vorbedingung und alle Doppelpunkte erhalten |
| T20 | SET beide Namens-/WHERE-Varianten und BASKET | Originalklauselreihenfolge |
| T21 | Explizite Klammern, AND/OR/NOT, gemischte AST-Präzedenz | Keine Normalisierung/Entklammerung |
| T22 | Nested calls, ALL/PARAMETER/INSPECTION/Pfade | Kompletter akzeptierter Faktorumfang |
| T23 | String mit `END`, `!!`, Operatorsymbolen, Emoji und grosser Dezimalzahl | Werte und UTF-16-Spannen korrekt |
| T24 | Breite39/40/120/240/241 und genaue Schwelle | Grenzvalidierung; Softlimit ohne Atomveränderung |
| T25 | LF/CRLF/CR/mixed, EOF false/true, BOM | Definierter EOL-/Dateivertrag, Idempotenz |
| T26 | Incomplete syntax, Lexerfehler, trailing junk | Rejected, keine Teilquelle |
| T27 | Cancellation vorher/mittendrin, grosse akzeptierte Quelle | Kein erfolgreicher Teiltext, begrenzte Reaktion |
| T28 | Gleichzeitige Aufrufe mit unterschiedlichen Optionen | Keine Shared-State-Verwechslung |
| T29 | Gezielte Mutation von Token/Metawert/Anker im Ausgabepfad | Sicherheitsoracle erkennt tatsächliche Veränderung |
| T30 | CLI stdout/check/write/mehrere Dateien/Fehlerkonflikt | Echte Prozesse und Dateiintegrität |
| T31 | LSP Optionen/readonly/stale/Unicode/Abbruch | Standardformatierung, gültige Editranges |
| T32 | Wegfall redundanter Format-Code-Action | Kein Default-Editpfad; andere Quickfixes erhalten |
| T33 | Öffentlicher JPMS-Consumer mit altem Dreierkonstruktor und neuer Breite | Echte unveränderte Produkt-JARs |
| T34 | API/CLI/LSP mit gleichen Optionen | Bytegleiche Ausgabe |
| T35 | Gesamter ausgewählter Referenzkorpus | Reparse/Token/Wert/Bindung/Idempotenz und Diffs |
| T36 | Lokale semantische Vorher-/Nachhermodelle | Gleiche fachliche Ergebnisse nach genau definierter Positionsprojektion |

Die Tabelle fordert fachliche Fälle, keine künstliche Zahl einzelner Testmethoden. Sinnvolle Parameterisierung ist erlaubt. Ein Test ohne Assertions oder ein vom Framework übersprungener Fall erfüllt den Eintrag nicht. Positive und negative Syntax getrennt halten.

## 6. Unabhängige Orakel

Der Produktverifier darf nicht das einzige Testoracle sein. Tests lesen vor/nachher selbst über SyntaxServices und vergleichen eigene Darstellungen von Tokenart/-text, Metawertfolge und Anker. Mindestens einige Goldenfälle mit exakt festgelegtem Text. Mindestens eine bewusste Tokenmutation, Metawertänderung und falsch verschobene Kommentarbindung gegen das Testoracle nachweisen.

Keine Vergleichsfunktion, die Kommentare, sämtliche Whitespace oder alle Diagnosen pauschal entfernt. SourceRanges, SnapshotIDs und NodeIDs ändern sich durch Neuformatierung; nur solche erklärten Identitäten über Tokenordinal-/Strukturpfade normalisieren. Inhaltliche Diagnosticcodes, Schweregrade, Ursachen und genaue Zahlenwerte bleiben im Vergleich.

Semantikstichprobe: lokale vollständige Mini-Modelle für2.3/2.4 mit Klassen/Beziehungen, allen positiv zulässigen Constraint-Familien, Metaattributen und Wertefällen. Vor/nachher mit derselben geprüften Engine und identischen lokalen Abhängigkeiten kompilieren. Es darf nicht für den formatierten Stand heimlich eine andere Modellversion verwendet werden. Nicht alle SO-Originalimporte online nachladen; das ist keine Voraussetzung des Formatierens.

Ein vorhandener gepinnter ili2c-Referenzlauf kann für wenige konkrete Sprachzweifel genutzt werden, ist aber kein neuer vollständiger Korpusvergleich pro Phase.

## 7. Layoutvarianten und Stress

Aus denselben Originaltokens sichere Layoutvarianten ableiten: mehr/weniger Randeinrückung, überzählige erlaubte Leerzeilen, kompakte/gebrochene Constraints, sichere Tokenabstände. Mehrzeilige Metawerte und Strings nicht mutieren. Seeds festhalten; kein zufälliger unprotokollierter Fuzzer.

Nicht jede beliebige Whitespaceeinfügung erhält die Lexertokenisierung (signed numbers, zusammengesetzte Operatoren). Erst generierte Variante reparsen und Tokenidentität prüfen. Ungültige Generatorvarianten sind Generatorfehler oder gezielt negative Fixtures, keine still weggezählten Formatterfälle.

Konvergenz nur für kanonisierte Bereiche fordern; Kommentare mit bewusst erhaltener Innenstruktur müssen nicht zwischen unterschiedlichen Ausgangstexten identisch werden. Wiederholtes Formatieren derselben Ausgabe muss immer identisch sein.

Kleine Worst-case-Fälle: lange Boolkette, viele Kommentare, tiefe vom Parser noch akzeptierte Klammerung, grosser einzelner Literaltext. Messung mit Inputgrösse, Laufzeit und ggf. Operationszählern. Keine native Toolchain oder gesamte Performancekampagne nötig. Einen vorbestehenden Parser-Stackfehler nicht als neuen Formattererfolg tarnen; sichere Grenzen und Nichtbetroffenheit transparent abgrenzen.

## 8. Diff-/Korpusbericht

Ein expliziter Lauf erzeugt unveränderte Kopien/Hashes der Inputs, formatierte Outputs, Unified Diffs und einen maschinenlesbaren Bericht. Geänderte Regionen einer Kategorie zuordnen:

- structural-indent;
- declaration-spacing;
- token-spacing;
- comment-margin;
- constraint-layout;
- line-endings/eof;
- protected-region (keine Inhaltsänderung, Notice);
- unexplained-change (muss vor Abschluss geklärt werden).

Keine feste „höchstensX geänderte Zeilen“-Schwelle. Token-/Wert-/Bindungsänderungen sind immer Fehler; grosse reine Constraintdiffs können richtig sein. Schon gut formatierte Kommentare sollten ausser Randkorrekturen keine Diffhunks haben. Für jeden Pflichtreferenzfall wenigstens einen lesbaren Original/Ergebnisdiff im Abschlussindex anbieten.

Bericht erfasst pro Datei: Originalhash, Outputhash, Optionen/Formatversion, parseStatus, token/wert/bindingStatus, idempotenceStatus, Noticeanzahl/-gründe, Diffstatistik, Fallstatus. Korpusabdeckung und Baselineablehnungen getrennt summieren. Kein `PASSED` allein aufgrund gleicher Anzahl Dateien.
