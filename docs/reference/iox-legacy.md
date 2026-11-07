# IOX-Referenzwissen für Transfer und Validierung

Erhebung P4.0-A vom 6. November 2026 auf `interlis-java`
`953b84dcae7c0000abff18fc84133fe36d5b9b5b`. Die Befunde gelten für
INTERLIS 2.3 und 2.4. Einzelne Klassen tragen ITF im Namen, weil XTF die
Kurven- und Poolinfrastruktur wiederverwendet.

Der [Fallindex](../../testdata/reference/iox-issue-index.json) ordnet
Problemfälle, Quellstände, Belege und Gegenproben den Verbraucherphasen zu.
Die Einstufung unterscheidet fachlich korrektes Verhalten, historische
Kompromisse, bekannte Fehler und ungeklärte Fälle. Der Belegstand unterscheidet
Quellbefund, Upstream-Bericht und lokal ausgeführte Probe. Ein geschlossener
Issue kann weiterhin einen Regressionsfall liefern. Die
[Normpins](../../testdata/reference/norms/documents.json) und
[Sprachentscheidungen](../contracts/norm-decisions.md) bleiben massgeblich.
Phasenbezeichnungen nach P4.1 bezeichnen fachliche Arbeitsschritte, ohne
ihnen eine neue Paketnummer zu geben.

## Gelesene Upstream-Stände

| Repository | Quellstand | Untersuchungsbereich |
| --- | --- | --- |
| iox-api | [1.0.4 im Quellstand c9209c8](https://github.com/claeis/iox-api/tree/c9209c8ee78225b73b2460561e326b059c40a4ac) | IOM-Werte, Ereignisprotokoll, Besitz der Streams und API-Diskussion zu 2.4 |
| iox-ili | [1.24.4, 8e966a0](https://github.com/claeis/iox-ili/tree/8e966a086a72e7e2990a312b1229ee5878fd5994) | XTF-Reader, Validator, Kurvenalgorithmen, Dateipools und Tests |
| iox-ili | [Entwicklungsstand f8d7f56](https://github.com/claeis/iox-ili/tree/f8d7f5695a01a9c67f13deab7d0844d267d2803f) | Spätere Korrekturen und offene Fälle, separat vom Release |
| iox-wkf | [2.0.0, 2098c4b](https://github.com/claeis/iox-wkf/tree/2098c4b4851a6c8a9911ca82a4ffdf2798868d89) | JSON, GeoJSON, Shapefile, GeoPackage und begrenzte DB-Projektion |
| ilivalidator | [1.15.0, 27cd81f](https://github.com/claeis/ilivalidator/tree/27cd81fb0d0ef2103e7afd673a7d00dd2053763f) | CLI, Modell- und Referenzdatenbeschaffung, zweiter Durchlauf, Exitcodes |
| ilivalidator | [Entwicklungsstand cee7ee7](https://github.com/claeis/ilivalidator/tree/cee7ee7994062128f635f182592fbdfed62f9519) | Dokumentation und Einordnung neuerer Issue-Berichte |

ilivalidator 1.15.0 bindet iox-api 1.0.4, iox-ili 1.24.4 und ili2c 5.6.8.
Der iox-wkf-Tag hat seine eigene Closure mit iox-ili 1.21.6 und
Versionsbereichen für ili2pg/ili2gpkg. Aussagen zu iox-wkf beziehen sich
hier auf Quellbefunde. Für spätere ausführbare Profile braucht es eine
separat gepinnte Closure. [ilivalidator-Build][validator-build],
[iox-wkf-Build][wkf-build].

## Issue-Abdeckung von iox-ili

Der [Fallindex](../../testdata/reference/iox-issue-index.json) erfasst unter
`issueCoverage` alle 61 am 6. November 2026 per GitHub-API ermittelten Issues
von `claeis/iox-ili` (14 offen, 47 geschlossen). Aus dem unter `retrievedAt`
datierten Abruf wurden 105 Pull Requests herausgefiltert. Acht Issues sind
den bestehenden 35 Fallgruppen zugeordnet, 18 begründet ausgeschlossen
und 35 mit einer offenen Folgeaufgabe erfasst. Issue-Texte und vorhandene Kommentare
dienen der Einordnung. Der GitHub-Status und die fachliche Abdeckung sind
getrennt. Den Umfang einer ausgeführten oder quellengestützten Prüfung
geben die zugeordneten Fallgruppen an.
[Upstream-Issues](https://github.com/claeis/iox-ili/issues?q=is%3Aissue).

Die wichtigsten Geometrielücken betreffen Schnitt- und Ringdiagnosen,
die separaten SURFACE-Überlappungsproben, Bogen-Overlay und den
Zusammenbau mehrteiliger Ränder. Die alten
Bogen-Overlay-Tests zu #37 wurden mit `CurvePolygon.polygonOverlays`
entfernt. Ihre beiden Laufrichtungsfälle brauchen einen Abgleich mit dem
heutigen AREA-Pfad. Der ignorierte Test zu #89 betrifft ITF.
Die XTF-relevante POLYLINE-Prüfung verwendet ebenfalls die Routine zum
Entfernen zulässiger Selbstschnitte und braucht eine eigene Grenzprobe zum
Punktversatz. #94 betrifft doppelte Nahtpunkte bei der IOM-JTS-Konvertierung.
Die XTF-2.4-Flächendaten zu #146 wurden als Low-Level-Parserproben erläutert.
Für Geometrieabnahmen braucht es getrennte, konforme Flächentransfers.
[#37](https://github.com/claeis/iox-ili/issues/37),
[Entfernung der Overlay-Tests](https://github.com/claeis/iox-ili/commit/fe5a75d95783a9e339ffcfc6c5eb0c3dcd7a861e),
[#89](https://github.com/claeis/iox-ili/issues/89),
[POLYLINE-Prüfung im Pin](https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iox_j/validator/Validator.java#L4750-L4775),
[#94](https://github.com/claeis/iox-ili/issues/94),
[#146 mit Einordnung](https://github.com/claeis/iox-ili/issues/146#issuecomment-3532215860).

Der Cache-Fall #107 ist bereits abgedeckt. Der Ressourcenbedarf unter
repräsentativer Last bleibt offen. Die einschlägigen Noder- und
Spill-Belege stehen in den bestehenden Fallgruppen und stammen auch aus
Pull Requests und anderen Upstream-Repositories. Weitere offene
Transfer- und Validatorproben betreffen wiederholte BAG-Strukturen,
BLACKBOX-Werte, Objektpfade mit Rollenende und die Coverage bei
abgeschalteten Regeln.
[#107](https://github.com/claeis/iox-ili/issues/107).

## Transfer und Identitäten

- `IoxReader` und `IoxWriter` beschreiben Transferstart, null oder mehr
  Baskets mit null oder mehr Objekten und Transferende. `IoxReaderAlt`
  bietet getrennte Leseoperationen. Die Reader-Dokumentation unterscheidet
  eigene und übergebene Streams. Für P4.1 sind Ereignisbesitz, Ende,
  Abbruch und Close im jeweiligen Adapter explizit zu prüfen.
  [IoxReader][api-reader], [IoxReaderAlt][api-alt], [IoxWriter][api-writer].
- Ein Legacy-`IomObject` hält Tag und Objekt-OID separat von
  Referenz-OID, Referenz-BID und Referenzposition. Primitive Werte sind
  Zeichenketten, komplexe Werte rekursive IOM-Objekte. Die Implementierung
  legt Wertelisten pro Attribut an. Die Attribut-Enumeration basiert auf
  einer HashMap. Die fachliche Slotreihenfolge stammt aus dem Modell.
  `Iom_jObject` und das konkrete `StartBasketEvent` ergänzen die abstrakte
  API um `addattrvalue` und 2.4-Domänenbindungen.
  Für P4.1 sind Typisierung, Zahlengenauigkeit, Slotreihenfolge,
  Fehlwertzustände und Basketbindung einzeln abzunehmen.
  [IOM-Implementierung][iom], [2.4-Reader][xtf24], [API-Diskussion #1][api-1],
  [API-Inventar #2][api-2].
- Ein TID bezeichnet das Objekt im konkreten Transfer, eine OID dessen
  stabile Identität, wenn die Modell- und Transferregeln sie verlangen.
  Der neutrale Wert braucht bei Bedarf sowohl den aktuellen Basket als auch
  den Original- bzw. Zielbasket. In XTF 2.3 FULL teilen sich TIDs und BIDs einen
  Eindeutigkeitsraum. XTF 2.4 formuliert für FULL explizit die
  Eindeutigkeit der TIDs. Die allgemeine 2.4-Objektstromregel verlangt
  eindeutige Identifikationen für Behälter und Objekte über den Transfer.
  Der Validator 1.24.4 prüft eine BID im laufenden Transfer auch ohne
  Basket-OID-Domäne und behält BIDs mit deklarierter
  OID-Domäne zusätzlich zwischen Eingabetransfers. Der historische
  Akzeptanzbericht für doppelte BIDs betrifft 1.14.7. Eine reduzierte
  Gegenprobe wurde in 1.14.7 und 1.15.0 jeweils abgelehnt. Das
  Originalarchiv wurde nicht ausgeführt. Bei UUIDs normalisiert der
  Objektpool abhängig von der OID-Domäne, sodass die fachliche Identität
  von der Schreibweise zu unterscheiden ist. `BID`, `TID`, UUID-Schreibweise,
  Wiederverwendung über Eingaben und Originalbasket brauchen getrennte Fälle.
  [Normbelege zu Transferidentitäten](#normbelege),
  [BID-Prüfung][basket-validation], [Objektpool][object-pool],
  [BID-Diskussion #443][bid-discussion], [P4.0-B-Laufbelege][b-observed].
- Ein eingebetteter Link kann Rollenbezug und eigene Attribute in einem
  Unterobjekt tragen. Ein eigenständiger Link hat je Rolle einen Slot und
  kann nach Modell eine eigene OID besitzen. Der Legacy-Pool bildet bei
  Links ohne OID einen internen Schlüssel aus Rollenwerten. Diese
  Implementierungsidentität gehört weder in das Transferobjekt noch in eine
  spätere persistente fachliche Identität. Für eine einzelne Standalone-Rolle
  mit zusätzlichem `ili:bid` legt der 2.4-Reader im Pin zuerst einen REF
  mit OID an, fügt einen zweiten REF nur mit BID hinzu und setzt BID
  sowie vorhandenes `ili:order_pos` am ersten REF. Die ausgeführte Probe
  `read-24-standalone-bid` deckt die einzelne Rolle ab. Die Zuweisung an
  Index 0 bei wiederholtem Rollennamen bleibt ein separater statischer
  Verdacht. 2.4 §§4.3.9.2 und 4.4.4.7 führen bei Rollen `ref` und optional
  `order_pos`, während 2.3
  auch optionales BID aufführt. 2.4 §4.4.5 besitzt in `RoleDef` ein
  laxes `anyAttribute`. Das zusätzliche BID ist damit eine
  Robustheitsprobe ausserhalb der beschriebenen 2.4-Rollencodierung.
  Der Eventvergleich muss den zusätzlich erzeugten Eintrag sehen.
  [2.3-Referenzdecoder][xtf23-ref], [2.4-Referenzdecoder][xtf24-ref],
  [Reader-Tests 2.3][test-xtf23], [Reader-Tests 2.4][test-xtf24],
  [Normauslegung Assoziationen][association-norm],
  [Normbelege zur Rollencodierung](#normbelege), [P4.0-B-Laufbelege][b-observed].
- `iox-wkf` belegt, dass dieselbe Ereignisform unterschiedliche Profile
  aufnehmen kann. JSON/GeoJSON-Reader gruppieren aufeinanderfolgende
  Objekte anhand von BID und Topic und verwerfen die Rückkehr zu einer
  abgeschlossenen BID. Die JSON-Abbildung transportiert auch
  Referenzmetadaten. GeoJSON gibt lineare JTS-Koordinaten aus, wofür Bögen
  bereits an der Konvertierungsgrenze umgewandelt werden. Shapefile leitete
  sein Schema vom ersten Feature ab. Ein Fix bindet Multisurfaces daher
  unabhängig vom ersten Wert an `MultiPolygon`, ein weiterer vergibt
  kollidierende gekürzte Feldnamen vor der Ausgabe eindeutig. Die
  DB- und GeoPackage-Adapter haben eigene Identitäts- und
  Mappinggrenzen. Diese Befunde liefern spätere Adapterproben.
  [JsonReader][wkf-json], [Json2iox][wkf-json2], [GeoJSON-Konvertierung][wkf-geojson],
  [Shapefile #45][wkf-45], [Shapefile #46][wkf-46], [GeoPackage-Tests][wkf-gpkg],
  [Db2Ili #27][wkf-27].

### Linienattribute in XTF 2.3

Das Referenzhandbuch 2.3 beschreibt `LINEATTR` vor dem Startsegment
(§ 3.3.11.12) und verlangt das Strukturelement in jeder Randlinie einer
entsprechend modellierten SURFACE oder AREA (§ 3.3.11.13). Mit ilivalidator
1.15.0 und iox-ili 1.24.4 endet ein so kodierter Transfer im Standardreader
mit Exit 1 und `expected segment type`. Mit
`ILI_XTF23_READER=ch.interlis.iom_j.xtf.Xtf23Reader0` erreicht derselbe
Transfer die Geometriekonvertierung und endet mit Exit 1 und
`Lineattributes not supported`. Die Diagnosen belegen Grenzen der
Referenzwerkzeuge. Der Adapter prüft die Kodierung und die Werterhaltung
durch Roundtrips.
[Standardreader](https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iom_j/xtf/Xtf23Reader.java#L1200),
[Geometriekonvertierung](https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iox_j/jts/Iox2jtsext.java#L192).

## Validierungsphasen und Rollen

ilivalidator liest die Eingaben und gegebenenfalls Referenzdaten, steuert
den zweiten Durchlauf und meldet das Ergebnis. Der iox-ili-Validator
kopiert IOM-Objekte im ersten Durchlauf, prüft Werte und registriert
Objekte und Links. Er verarbeitet Referenzdaten über einen eigenen
Eingangspfad. `doSecondPass()` prüft modell- und objektbezogene Regeln
mit Kenntnis des Pools, anschliessend AREA und Plausibilität.
`singlePass` überspringt diese Phase. Das CLI verwendet Status 0 bzw. 1
für das boolesche Validierungsergebnis und Status 2 in eigenen
Aufrufpfaden. Die Auswertung braucht zusätzlich den Diagnose- und Coverage-Beleg. [CLI-Host][cli-host], [CLI-Main][cli-main],
[Validator-Eingang][validator-input], [Zweiter Durchlauf][validator-second].

| Bereich | Referenz und fachliche Abgrenzung | Abnahme für den Validator |
| --- | --- | --- |
| Rollen und inverse Kardinalität | Validator-`LinkPool` zählt je Objekt-ID und Rollenname, optional mit Tabellenanteil für ITF. Beim eingebetteten binären Link bucht er beide Seiten, beim eigenständigen Link bucht er die Gegenseite nur bei genau einem Gegenende. #232 beseitigte einen Absturz. #233 und #463 belegen offene Kardinalitätsfälle mit mehr als zwei Rollen. | Linkidentität, vollständiges Tupel, abgeleitete Rollen und Herkunft getrennt erhalten. Eigenständige und eingebettete Links, Mehrfachlinks mit eigener OID, Rolle-auf-Assoziation, fehlendes Ende und n-äre Mindestkardinalität prüfen. [LinkPool][link-pool], [#232][v232], [#233][v233], [#463][v463]. |
| EXTERNAL | Bei bekanntem Ziel sind Existenz, Zieltyp, Basketzuordnung und Objektpfad getrennte Fragen. Im Pin arbeitet `--allObjectsAccessible` bei EXISTENCE und in Teilen der Pfadauflösung. Die EXTERNAL-UNIQUE-Korrektur #147 ist enthalten. Die Diskussion #448 dokumentiert weiter unterschiedliche Pfade mit `CONSTRAINTS OF`, OID-Ende, Zielattribut und bereitgestellten Referenzdaten. #460 erhielt erst im späteren Entwicklungsstand eine Korrektur. | Vier Läufe aus tatsächlichem Vorhandensein und zugesicherter Verfügbarkeit des Zielbestands ableiten. Fachlich undefiniert, nicht entscheidbar, Verletzung und internen Fehler getrennt erfassen. [Rollenprüfung][role-validation], [EXTERNAL-UNIQUE #448][v448], [Fix #147][i147], [späterer #156][i156]. |
| UNIQUE und EXISTENCE | UNIQUE trennt lokale, Basket- und weitere Geltungsbereiche, berücksichtigt WHERE und überspringt undefinierte Schlüsselanteile. Der Pin löscht die Basket-Indizes beim Basketwechsel in Pass 2. EXISTENCE setzt `allObjectsAccessible` voraus. Laufzeit-Gesamtmenge und der normative, gegebenenfalls darüber hinausgehende Geltungsbereich sind getrennt zu belegen. | Gleicher Schlüssel in einem und in zwei Baskets, undefinierte Komponente, abgeleiteter Typ, Vorwärtsverweis und konkreter EXTERNAL-Endpunkt. Kein Objektpool-Zugriff nur zum Vergleich eines bereits vorliegenden, fachlich geeigneten Schlüssels. [Normbelege zu Constraints](#normbelege), [Basket-Verarbeitung][constraint-basket], [UNIQUE][constraint-unique], [UNIQUE-Schlüssel][unique-key], [EXISTENCE][constraint-existence]. |
| SET CONSTRAINT | Der Pin sammelt Kandidaten und wertet unter anderem für jeden Basket beziehungsweise für transferweite Mengen in Pass 2 aus. Die Pfade für leere Mengen, WHERE und Views sind auseinanderzuhalten. #118 wurde ohne Merge geschlossen und kann keine pauschale Aussage zur Pin-Version tragen. #439 dokumentiert eine verbesserte Teilprobe und einen abweichenden Fall über ein Validierungstopic. | Leerer Basket und durch WHERE leer gefilterte Menge, abgeleitete Typen, View im selben und in anderem Topic, fehlende externe Daten und explizites Scope-Ergebnis. [SET-Auswertung][constraint-set], [#118][i118], [#119][i119], [#439][v439]. |

## Geometrie und areAreas()

**Vor 2024.** 2018 kamen TIDs in Schnittmeldungen und die Rundung vor
der Topologieprüfung hinzu. Der historische Diff verschiebt in
`validateObject` die Auswertung der an der Klasse deklarierten UNIQUE-Regeln und die
Poolaufnahme hinter die Attributprüfung. Die geänderten numerischen Attributpfade schreiben
den gerundeten Wert ins IOM-Objekt zurück. Der ältere AREA-Pfad sammelt Randkurven, vergleicht sie mit
`CompoundCurveNoder` und sucht anschliessend vollständige Überdeckungen
bzw. Enthaltensein über Polygonhüllen und JTS-Prädikate. Im Pin ist dieser
Pfad über `ILI_LEGACYAREAREAS` erreichbar. `validate0` nimmt einen
Parameter `maxOverlapDummy` entgegen. Die alte AREA-Entscheidung
übernimmt daraus keine modellierte Toleranz. `CompoundCurveNoder` bleibt
unter anderem für Einzelkurven-/SURFACE-Topologie relevant. #126
ergänzte zur Kurvenhüllen-Suche einen STRtree für Segmenthüllen, den
der Noder bei einem Kurvenpaar aufbaut. Kandidaten werden vor der
Schnittberechnung in Segmentreihenfolge sortiert. Das PR-Log
berichtet eine Verbesserung von rund 222 auf 119 Sekunden an einer
Testdatei.
[Schnittmeldungen 2018][geometry-tids], [Rundungsdiff 2018][geometry-rounding],
[Reihenfolge im historischen Stand][rounding-order],
[Rückschreiben des numerischen Werts][rounding-write],
[Alter und neuer Einstieg][area-entry], [Noder][noder], [PR #126][i126],
[Commit f3d25fd][noder-commit].

**Umbau 2024.** `AreaValidator` setzt gültige Einzelpolygone voraus. Er
gruppiert gleiche Randsegmente, führt Linien über Knoten vom Grad zwei
zusammen und bewertet Randorientierung relativ zur Innenfläche von Shell
oder Loch. Bekannte gemeinsame Ränder können weitere Schnittvergleiche
einsparen. Ein STRtree begrenzt Kandidaten auf überlappende Flächenhüllen,
Segmentindizes und Kurven-Intersektionen behandeln verbleibende Ränder.
Falls nötig folgt ein Punkt-in-Fläche-Test mit einem bedarfsgerecht
erzeugten Locator. Bogenüberlappung ist an die Lage von Stützpunkten und
die modellierte Toleranz gebunden. Ein kleiner numerischer Wert allein
rechtfertigt keine Akzeptanz. Der Umbau kam mit iox-ili 1.23.1 in den
Releasezweig und korrigiert die in #366 gezeigte frühere Ablehnung.
`Area_2Boundaries_PermissibleOverlap.xtf` ist trotz Dateiname ein
Negativfall. Der CLI-Lauf meldete einen Schnitt ohne gemeinsamen
Stützpunkt bei der SURFACE-Prüfung und übersprang danach die
AREA-Gruppenprüfung. Auch `Area_2Boundaries_NotAllowedOverlap.xtf`
endete über diesen Skip-Pfad. Die AREA-Gruppenprüfung wurde im positiven
CLI-Fall `Area_1Boundary_PermissibleOverlap.xtf` erreicht.
`Area23OverlapTest` prüft zusätzlich die positive Regression zu #366.
`AreaValidatorTest` übt die Segment- und Toleranzentscheidungen direkt aus.
[Implementierung][area], [Rewrite 3b9a5b1][area-commit], [PR #127][i127],
[#366 mit Regression][v366], [Release-Zuordnung #384][v384],
[AreaValidator-Tests][test-area], [XTF-Toleranztests][test-overlap].

**Funktionspfad.** `INTERLIS.areAreas` wählt Objekte und extrahiert die
Flächen aus direkten Attributen oder einem Strukturpfad. Der
Standardpfad in 1.24.4 verwendet `validate1(0.0)`, während die
AREA-Typprüfung `type.getMaxOverlap()` verwendet. Der Unterschied in
#468 ist damit am Code des Pins nachvollziehbar. Wie die
Laufzeittoleranz für normativ typisierte Funktionspfade bestimmt werden
soll, bleibt separat zu entscheiden. Die TEXT-Pfad-Funktionen der
Legacy-Bibliothek laufen unter `INTERLIS_ext.areAreas2` und
`INTERLIS_ext.areAreas3`. Der Compilervertrag zu den Namen im
Standardmodell und zur Singleton-Zuordnung steht in Normentscheidung 11.
Das ist unabhängig von der fehlenden Laufzeitregistrierung der
Standardnamen im Legacy-Pin. Lückenfreiheit ist eine eigene Operation.
[Funktionsdispatch][areareas-dispatch], [TEXT-Funktionen][areareas-ext],
[Funktionsauswertung][areareas], [AREA-Typprüfung][area-type],
[#468][v468], [#161][i161], [#162][i162], [Normentscheidung 11][decision-11].

Der offene PR #166 schlägt für die Gruppenprüfung das Maximum der
modellierten Überlappungstoleranzen der beteiligten Flächenattribute vor.
Der Diff ermittelt die Toleranzen anhand der konkreten Klassen der
Objekte und Strukturelemente und ergänzt 2.3-Proben für gemischte
Toleranzen. Für die spätere Abnahme bleiben die Wahl der Gruppenregel
und ihr Zusammenspiel mit der Einzelprüfung zu klären.
[PR #166](https://github.com/claeis/iox-ili/pull/166).

**Wiederverwendung.** #308 beschreibt 2021 den Unterschied zwischen
Sammeln im ersten Durchlauf und wiederholter SET-Auswertung. 2022
beseitigte #106 wiederholtes Einfügen und Validieren derselben
Strukturflächen. Ein zu grober Funktionscache lieferte für `THIS` falsche
Ergebnisse. Er wurde entfernt und in #108 für den `ALL`-Fall über den
konkreten Aufruf wieder eingeführt. Die Maintainer-Diskussion fordert
zusätzlich konstante Pfadargumente. Im Pin prüft der Cachetreffer
`arguments[0] instanceof Objects` und den Aufrufschlüssel. Die Bedingung
einer sicheren Wiederverwendung ist für die neue Engine explizit
nachzuweisen, insbesondere über Basketwechsel, WHERE, Referenzbestand,
Pfadauflösung und Geometriepolicy. Positive und negative Ergebnisse
müssen frisch und wiederverwendet übereinstimmen.
[#308 mit Analyse][v308], [#106][i106], [Cache-Entfernung][cache-removal],
[#338][v338], [#107 mit Entscheidung][i107], [#108][i108], [Cache im Pin][func-cache],
[Tests][test-areareas].

**Diagnostik und Coverage.** Der Pin unterdrückt die AREA-Gruppenprüfung
eines Attributs bei fehlgeschlagener SURFACE-Topologie. Das schützt den
Gruppenalgorithmus, lässt aber mögliche weitere Fehler offen.
`AreaValidator` gibt Schnittdiagnosen bevorzugt aus und erzeugt
`polygons overlay`-Diagnosen ohne Koordinaten nur, wenn es insgesamt
keine Schnittbefunde gibt. Der Pfad für `areAreas()` kann Detailmeldungen
abschalten und verwendet trotzdem sein boolesches Resultat. Die
Detailmeldungen für `areAreas()` wurden 2020/21 um TIDs und Attributpfad
erweitert. Geometrische
Datenorte, Skips nach ungültiger Eingabe und Diagnosebegrenzung gehören in
getrennte Vertragsfelder. [#296][v296], [PR #112][i112],
[Ausgabe des Algorithmus][area-diagnostics], [#376][v376],
[areAreas-Diagnosen 2021][areareas-tids], [Funktionsauswertung][areareas].

## Spill, Speicher und spätere Abnahme

- **2017:** ObjectPoolManager erhielt Dateipools und Cache-/Schreibsteuerung.
  Im Pin gibt es drei Poolvarianten. `ObjPoolImpl2` hält den Schlüsselindex
  im Heap und schreibt serialisierte Werte in eine Datei.
  `BTreeImpl` hält Schlüssel im dateibasierten B-Baum und Werte in einer
  zweiten Datei. Beide haben kleine Zugriffscaches. Der Manager schaltet
  diese Wertcaches nach mehr als 10 Sekunden kumulierter GC-Zeit bei
  periodischer Kontrolle aus. Das ist eine nachgelagerte Heuristik.
  Die Rohzahl ist weder ein Heaplimit noch ein Spillbudget.
  [Dateiindex 2017][spill-2017], [GC-Änderung][spill-gc],
  [Poolmanager][pool-manager], [ObjPoolImpl2][pool-impl2], [BTreeImpl][btree-impl].
- **2020/21:** Java-Standardserialisierung scheiterte in einem
  Native-Image-Pfad. Konkrete Serializer ersetzten sie in betroffenen
  Pfaden. Nach einem OOM im Export mit aktivierter Validierung wechselte
  der Validator-LinkPool auf den dateibasierten B-Baum. Jeder Eintrag zählt
  eine Rolle. Die Implementierung prüft den Schlüssel, liest ggf. den
  aktuellen Zähler und schreibt den neuen Wert. `BTreeImpl` hängt Werte
  beim Put an und aktualisiert den Offset. Schreiblast und Datei-Wachstum
  hängen dadurch auch von der Updatehäufigkeit ab. #401 belegt, dass
  Serialisierung auch fehlerhafte Eingaben ohne Prozessabsturz verarbeiten
  können muss. [Native-Image #354][db354], [Serialisierung][spill-serializer],
  [Export-OOM #363][db363], [LinkPool-Wechsel][spill-link],
  [Serializer-Fehler #401][db401], [Korrektur][spill-null].
- **2022/23:** #335 enthält Logs eines Plattenplatzfehlers und einer
  späteren Aufschlüsselung der Last. In einem gemeldeten Lauf wurden für
  eine Eingabe von rund 10,0 GB etwa 5,56 GB ObjectPool,
  16,56 GB LinkPool-Schlüssel und 0,30 GB LinkPool-Werte beobachtet.
  Ein weiterer Fall hatte rund 2,29 GB `CompoundCurveNoder VAL`. Das
  sind Beobachtungen an historischen Läufen mit verschiedenen
  Randbedingungen. `--trace`-Änderungen ergänzten Pooldateigrössen und
  Updatezähler. Aufräumen erfolgt beim Schliessen, über `java.io.tmpdir`
  verstreute Dateien sind ohne eigenen Laufordner schwer zurechenbar.
  [#335 mit Logs und Diskussion][v335], [Dateigrössen][spill-size],
  [Updatezähler][spill-updates], [Poolmanager][pool-manager].
- **Im Pin:** Der neuere AREA-Pfad hält Polygone und Randgraph im Heap,
  während `ItfAreaPolygon2Linetable.addPolygon` bzw. `addMultiPolygon`
  gleichzeitig Linien für den alten Pfad in eine dateibasierte Sammlung
  schreibt. Ein Dateipool belegt daher keinen begrenzten Heap für den
  gesamten Lauf. `UNIQUE`-Maps, SET-Mitgliedschaft, Basketschlüssel,
  View-Zwischenstände und Diagnosevolumen brauchen eigene Budgets.
  [AREA-Sammlung][area-entry], [AREA-Algorithmus][area],
  [UNIQUE-Zustand][constraint-unique], [VIEW-Speicher #428][v428].
- **Für den späteren Ressourcenvertrag:** Mit `-Xmx2g` messen und
  Datensätze nach Objektgrösse, Rollen-Fan-out, Distinktheit von Schlüsseln,
  Bogen-/Randdichte und Diagnosezahl parametrisieren. Heap und
  Prozess-RSS getrennt, Spitzengrösse und verbleibende Bytes je
  laufgebundenem Temp-Verzeichnis sowie I/O-Zähler und Laufzeit erfassen.
  Schnelle Fälle, starke Schlüsselkonzentration, fehlender Temp-Platz,
  Abbruch und Fehler im Serializer gezielt prüfen. In-Memory und Spill
  müssen dieselben fachlichen Befunde liefern. Das Standardbetriebsziel
  unter `-Xmx2g` erfordert Messungen für die zugelassenen Workloads.
  [Entwurf zu Ressourcen und Geometrie][resource-contract].

### Lokale Geometrie-Baseline

Zwölf synthetische [Profile](../../testdata/reference/iox/geometry-cases.json)
liefen am 6. November 2026 mit ilivalidator 1.15.0, Temurin 25.0.2+10
und `-Xmx2g` auf macOS arm64. Sieben Transfers wurden akzeptiert,
fünf mit den erwarteten Diagnosen abgewiesen. AREA und `areAreas(ALL, …)`
akzeptierten den gemeinsamen geraden Rand und erkannten die Kreuzung.
Bei identischen XTF-2.4-Bytes akzeptierte AREA eine Bogenüberlappung von
rund 0,0481 m bei modellierten 0,050 m. `areAreas(ALL, …)` wies sie mit
seiner Nulltoleranz ab. Die normative Funktionspolicy bleibt offen.
Die separate Rundungsprobe vergleicht gemeinsame Kante und geradlinige
Überlagerung. Beim selbstschneidenden Ring belegt das Log den Ausfall
der AREA-Gruppenprüfung nach dem SURFACE-Fehler. [#468][v468]

Die zwölf Läufe erreichten bis 76,8 MiB abgetasteten Heap, 160,4 MiB RSS
und 1 ms kumulierte GC-Zeit. Die abgetastete Temp-Spitze stieg in beiden
Gittern von rund 57 KiB bei 100 Polygonen auf 574 KiB bei 1’000 Polygonen.
Beim JVM-Exit war das Temp-Verzeichnis in allen zwölf Läufen leer.
Je drei Wiederholungen der beiden 1’000er-Gitter ergaben Medianwerte von
328 ms für disjunkte Flächen und 316 ms für gemeinsame Ränder.
Nach einer am Ende des Messblocks beobachteten Lastspitze wurde der
letzte Lauf mit gemeinsamen Rändern gezielt wiederholt. Die Zeiten
enthalten den JVM-Start. Die Stichproben können
kurze Heap- und Temp-Spitzen verfehlen. Produktionsdaten und
Kandidatenzahlen blieben offen.
[Benchmarkmodus](../../tools/iox-reference-harness/README.md#geometrie-und-ressourcen-messen)

## Normbelege

| Aussage | Gepinnte PDF-Seiten und Auszüge |
| --- | --- |
| 2.3 Objektstrom und gemeinsame Eindeutigkeit von TID und BID in XTF FULL | [§3.2.6, S. 77][pdf-23-stream], [§3.3.7, S. 86][pdf-23-full], [Inventar Objektstrom][inv-23-stream], [Inventar FULL][inv-23-full] |
| 2.4 Objektstrom und Eindeutigkeit von TID in XTF FULL | [§4.2.5, S. 99][pdf-24-stream], [§4.3.7, S. 105][pdf-24-full], [Inventar Objektstrom][inv-24-stream], [Inventar FULL][inv-24-full] |
| 2.3 XML-Attribute einer nicht eingebetteten Rolle | [§3.3.9.2, S. 88][pdf-23-role], [Inventar Rollen][inv-23-role] |
| 2.4 Attribute einer nicht eingebetteten Rolle und lax erweiterbarer `RoleDef` | [§4.3.9.2, S. 106][pdf-24-role], [§4.4.4.7, S. 117][pdf-24-role-xsd], [§4.4.5, S. 118][pdf-24-role-def], [Inventar Rollen][inv-24-role], [Inventar XSD-Rollen][inv-24-role-xsd], [Inventar RoleDef][inv-24-role-def] |
| Constraints und Rundung | [2.3 §2.12, S. 60][pdf-23-constraint], [2.4 §3.12, S. 77][pdf-24-rounding], [2.4 §3.12, S. 78][pdf-24-constraint], [Inventar 2.3 Constraints][inv-23-constraint], [Inventar 2.4 Rundung][inv-24-rounding], [Inventar 2.4 Constraints][inv-24-constraint] |

## Selbstprüfung

Die sieben unten genannten unveränderten Testklassen aus dem
iox-ili-Tag 1.24.4 wurden gegen das veröffentlichte iox-ili-1.24.4-JAR
(SHA-256 `7c315dcb2f2f0ac6dfa535db06eb52bc100395b73d570a5612609acf7da5eeef`)
mit iox-api 1.0.4, ili2c-core/-tool 5.6.8, ehibasics 1.4.1, ANTLR
2.7.7, JTS 1.14.0, base64 2.3.9, JUnit 4.13.2 und Hamcrest 1.3
ausgeführt. Temurin 25.0.2, macOS arm64, `-Xmx2g`, eigenes
`java.io.tmpdir`. Befehle im Upstream-Checkout

```sh
javac -encoding UTF-8 -cp "$classpath" \
  -sourcepath src/test/java:jtsext/src/test/java -d "$classes" <Testquelldateien>
java -Xmx2g -Djava.io.tmpdir="$probe_tmp" -cp "$classes:$classpath" \
  org.junit.runner.JUnitCore <Testklassen>
```

| Testklassen | Ergebnis |
| --- | --- |
| `AreaValidatorTest`, `Area23OverlapTest`, `AreAreas23Test`, `ObjectPool24Test`, `Xtf23ReaderAssociationTest`, `Xtf24ReaderAssociationTest`, `CompoundCurveNoderTest` | `javac` Exit 0. JUnit Exit 0, `OK (148 tests)`, keine ignorierten Testmethoden in der Auswahl. |

Im Temp-Verzeichnis dieser Testausführung blieben nach dem Prozessende
170 Dateien mit zusammen 56 691 logischen Bytes. Die Auswahl umfasst
mehrere Direktaufrufe der Legacy-Pools. Der Befund misst den Restbestand
der Tests und erlaubt keine Aussage über die Spitzengrösse oder den
Schliesspfad der CLI.

Die [P4.0-B-Laufbelege][b-observed] ergänzen nach der gemeinsamen
Integration die drei als `probe` referenzierten Fälle im Fallindex.
Die Datei wird mit P4.0-B geliefert. Im isolierten A-Reviewarchiv
fehlt sie daher bewusst. Der jeweilige Lauf steht unter `cases[].id`.
`argv`, `exitCode` und `inputSha256` dokumentieren das Kommando, den Exit
und die Eingabebytes. Der Vergleich mit 1.14.7 steht unter
`historicalDuplicateBid` im selben Bericht.

Die offizielle Gradle-Suite, iox-wkf-Laufzeit, historische Issue-Anhänge und
breite Speicher-/Plattformtests waren ausserhalb dieses Prüfbereichs.
Die JUnit-Tests verwenden die genannte Runtime-Closure. Der Upstream-Build
nennt teilweise ältere Testversionen. Die Positiv- und Negativbefunde in
dieser Auswahl belegen einzelne Referenzfälle, keine Gesamtkompatibilität.

## Nächste Phasen

| Phase | Voraussetzungen aus der Referenz |
| --- | --- |
| P4.1 Transferkern und XTF-Adapter | Gepinnte 2.3-/2.4-Goldens mit Original-BID, Ziel-BID und aktuellem Basket, TID/OID-Domäne, eingebetteter und eigenständiger Rolle, ORDER_POS, primitiver Liste, Struktur, Geometriebogen und Domänenbindung. Normgerechtes Rollenereignis und Robustheitsprobe mit zusätzlichem BID getrennt belegen. |
| Referenzauflösung und globale Regeln | Nachgewiesene Modellinformationen für Rollenherkunft, Assoziationsidentität und Übersetzung. Scope und Verfügbarkeit deklarieren. Vorwärtsverweis, EXTERNAL, UNIQUE, EXISTENCE, SET, n-äre Rollen und View-Abweichungen gezielt prüfen. |
| Geometrie | Reproduzierbare Einzelpolygon- und Gruppenproben einschliesslich Loch, Kongruenz, Stützpunkt, Bogen und Toleranzgrenze. Policy für Funktions- und Typprüfung sowie Lückenfreiheit explizit entscheiden. |
| Ressourcen und Indizes | Gleichheit von Frischlauf, Cache und Spill und ein Stopppfad mit sauberem Close. Getrennte Messung für Objekt-, Link-, Geometrie- und Diagnosewachstum. |
| Weitere Formate und DB-Verbraucher | Eigene Fähigkeiten und Profile, insbesondere für nicht darstellbare Kurven, Feldkollisionen, Multigeometrien, Zuordnungsmetadaten und Identitäten. Historisches DB-Verhalten anhand separater Versionen und ausführbarer Gegenproben erfassen. |

[api-reader]: https://github.com/claeis/iox-api/blob/c9209c8ee78225b73b2460561e326b059c40a4ac/src/ch/interlis/iox/IoxReader.java#L52-L76
[api-alt]: https://github.com/claeis/iox-api/blob/c9209c8ee78225b73b2460561e326b059c40a4ac/src/ch/interlis/iox/IoxReaderAlt.java#L49-L59
[api-writer]: https://github.com/claeis/iox-api/blob/c9209c8ee78225b73b2460561e326b059c40a4ac/src/ch/interlis/iox/IoxWriter.java#L50-L78
[validator-build]: https://github.com/claeis/ilivalidator/blob/27cd81fb0d0ef2103e7afd673a7d00dd2053763f/build.gradle#L22-L31
[wkf-build]: https://github.com/claeis/iox-wkf/blob/2098c4b4851a6c8a9911ca82a4ffdf2798868d89/build.gradle#L21-L44
[iom]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iom_j/Iom_jObject.java#L64-L165
[xtf24]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iom_j/xtf/Xtf24Reader.java#L724-L779
[xtf23-ref]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iom_j/xtf/Xtf23Reader.java#L1028-L1091
[xtf24-ref]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iom_j/xtf/Xtf24Reader.java#L1181-L1246
[test-xtf23]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/test/java/ch/interlis/iom_j/xtf/Xtf23ReaderAssociationTest.java
[test-xtf24]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/test/java/ch/interlis/iom_j/xtf/Xtf24ReaderAssociationTest.java
[basket-validation]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iox_j/validator/Validator.java#L527-L582
[object-pool]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iox_j/validator/ObjectPool.java#L40-L133
[wkf-json]: https://github.com/claeis/iox-wkf/blob/2098c4b4851a6c8a9911ca82a4ffdf2798868d89/src/main/java/ch/interlis/ioxwkf/json/JsonReader.java#L119-L196
[wkf-json2]: https://github.com/claeis/iox-wkf/blob/2098c4b4851a6c8a9911ca82a4ffdf2798868d89/src/main/java/ch/interlis/ioxwkf/json/Json2iox.java#L89-L135
[wkf-geojson]: https://github.com/claeis/iox-wkf/blob/2098c4b4851a6c8a9911ca82a4ffdf2798868d89/src/main/java/ch/interlis/ioxwkf/json/Iox2geoJson.java#L80-L115
[wkf-gpkg]: https://github.com/claeis/iox-wkf/blob/2098c4b4851a6c8a9911ca82a4ffdf2798868d89/src/test/java/ch/interlis/ioxwkf/gpkg/GeoPackageReaderTest.java#L228-L253
[cli-host]: https://github.com/claeis/ilivalidator/blob/27cd81fb0d0ef2103e7afd673a7d00dd2053763f/src/org/interlis2/validator/Validator.java#L498-L619
[cli-main]: https://github.com/claeis/ilivalidator/blob/27cd81fb0d0ef2103e7afd673a7d00dd2053763f/src/org/interlis2/validator/Main.java#L240-L275
[validator-input]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iox_j/validator/Validator.java#L418-L476
[validator-second]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iox_j/validator/Validator.java#L723-L745
[link-pool]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iox_j/validator/LinkPool.java#L15-L68
[role-validation]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iox_j/validator/Validator.java#L2854-L2977
[constraint-basket]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iox_j/validator/Validator.java#L1161-L1174
[constraint-unique]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iox_j/validator/Validator.java#L1387-L1467
[constraint-existence]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iox_j/validator/Validator.java#L3203-L3265
[constraint-set]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iox_j/validator/Validator.java#L1482-L1627
[area-entry]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iom_j/itf/impl/ItfAreaPolygon2Linetable.java#L38-L190
[noder]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/jtsext/src/main/java/ch/interlis/iom_j/itf/impl/jtsext/noding/CompoundCurveNoder.java#L34-L165
[area]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/jtsext/src/main/java/ch/interlis/iom_j/itf/impl/jtsext/noding/AreaValidator.java
[test-area]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/test/java/ch/interlis/iox_j/coverage/AreaValidatorTest.java
[test-overlap]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/test/java/ch/interlis/iox_j/validator/Area23OverlapTest.java#L78-L145
[test-areareas]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/test/java/ch/interlis/iox_j/validator/AreAreas23Test.java
[areareas-dispatch]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iox_j/validator/Validator.java#L2051-L2072
[areareas-ext]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iox_j/validator/functions/Interlis_ext.java#L38-L129
[areareas]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iox_j/validator/Validator.java#L2590-L2663
[area-type]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iox_j/validator/Validator.java#L879-L918
[area-diagnostics]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/jtsext/src/main/java/ch/interlis/iom_j/itf/impl/jtsext/noding/AreaValidator.java#L204-L219
[pool-manager]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/ehi/iox/objpool/ObjectPoolManager.java#L36-L129
[pool-impl2]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/ehi/iox/objpool/impl/ObjPoolImpl2.java
[btree-impl]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/ehi/iox/objpool/impl/BTreeImpl.java
[func-cache]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iox_j/validator/functions/Interlis.java#L232-L318
[api-1]: https://github.com/claeis/iox-api/pull/1
[api-2]: https://github.com/claeis/iox-api/issues/2
[wkf-27]: https://github.com/claeis/iox-wkf/issues/27
[wkf-45]: https://github.com/claeis/iox-wkf/pull/45
[wkf-46]: https://github.com/claeis/iox-wkf/pull/46
[bid-discussion]: https://github.com/claeis/ilivalidator/issues/443#issuecomment-2903809438
[v308]: https://github.com/claeis/ilivalidator/issues/308#issuecomment-900882210
[v335]: https://github.com/claeis/ilivalidator/issues/335#issuecomment-1161662135
[v232]: https://github.com/claeis/ilivalidator/issues/232
[v233]: https://github.com/claeis/ilivalidator/issues/233
[v296]: https://github.com/claeis/ilivalidator/issues/296
[v338]: https://github.com/claeis/ilivalidator/issues/338
[v366]: https://github.com/claeis/ilivalidator/issues/366
[v376]: https://github.com/claeis/ilivalidator/issues/376
[v384]: https://github.com/claeis/ilivalidator/issues/384
[v428]: https://github.com/claeis/ilivalidator/issues/428
[v439]: https://github.com/claeis/ilivalidator/issues/439
[v448]: https://github.com/claeis/ilivalidator/issues/448
[v463]: https://github.com/claeis/ilivalidator/issues/463
[v468]: https://github.com/claeis/ilivalidator/issues/468
[i106]: https://github.com/claeis/iox-ili/pull/106
[i108]: https://github.com/claeis/iox-ili/pull/108
[i112]: https://github.com/claeis/iox-ili/pull/112
[i118]: https://github.com/claeis/iox-ili/pull/118
[i119]: https://github.com/claeis/iox-ili/pull/119
[i126]: https://github.com/claeis/iox-ili/pull/126
[i127]: https://github.com/claeis/iox-ili/pull/127
[i147]: https://github.com/claeis/iox-ili/pull/147
[i156]: https://github.com/claeis/iox-ili/pull/156
[i107]: https://github.com/claeis/iox-ili/issues/107
[i161]: https://github.com/claeis/iox-ili/issues/161
[i162]: https://github.com/claeis/iox-ili/issues/162
[db354]: https://github.com/claeis/ili2db/issues/354
[db363]: https://github.com/claeis/ili2db/issues/363
[db401]: https://github.com/claeis/ili2db/issues/401
[area-commit]: https://github.com/claeis/iox-ili/commit/3b9a5b12def8fb8bd175d82aa0554bb49935a031
[noder-commit]: https://github.com/claeis/iox-ili/commit/f3d25fd13f3d1702729a91937bdb6c8e81f50f6b
[cache-removal]: https://github.com/claeis/iox-ili/commit/2185b4be1c4a918b6a71436ca286c59948d03b5d
[spill-2017]: https://github.com/claeis/iox-ili/commit/b7f1500387eb1cd477ae4c2af25b8007668af954
[spill-gc]: https://github.com/claeis/iox-ili/commit/5d6f001684523ae9ef0899ac062edbe229286283
[spill-serializer]: https://github.com/claeis/iox-ili/commit/13c289555b04f69bfd60f16276ad6b68853e7919
[spill-link]: https://github.com/claeis/iox-ili/commit/87336c2999188e7dae6fa088e02deab5cdec7cf8
[spill-null]: https://github.com/claeis/iox-ili/commit/2589deda56a3b414e7cda8267a5ecbb5c44a0ce6
[spill-size]: https://github.com/claeis/iox-ili/commit/9f8d7d0ad5ba62c88af9dc69ebaa2275f56f2768
[spill-updates]: https://github.com/claeis/iox-ili/commit/7d068188ddd90b2380edb807d44a427ba7f65724
[decision-11]: ../contracts/norm-decisions.md#decision-11
[association-norm]: ../contracts/norm-decisions.md#decision-15
[resource-contract]: ../design/transfer-validation-database.md#geometrie-und-speicherbudget
[geometry-tids]: https://github.com/claeis/iox-ili/commit/6e8eccafc8a63e8e8438fcd35cb51c2f6b5e2b0f
[geometry-rounding]: https://github.com/claeis/iox-ili/commit/b8192c1aec4fff9b047ab49deeba1c8921d8ecb4
[areareas-tids]: https://github.com/claeis/iox-ili/commit/2f9f15b6249663629541153dc7da32df62aa6cd9
[unique-key]: https://github.com/claeis/iox-ili/blob/8e966a086a72e7e2990a312b1229ee5878fd5994/src/main/java/ch/interlis/iox_j/validator/Validator.java#L4015-L4059
[rounding-order]: https://github.com/claeis/iox-ili/blob/b8192c1aec4fff9b047ab49deeba1c8921d8ecb4/src/main/java/ch/interlis/iox_j/validator/Validator.java#L2399-L2510
[rounding-write]: https://github.com/claeis/iox-ili/blob/b8192c1aec4fff9b047ab49deeba1c8921d8ecb4/src/main/java/ch/interlis/iox_j/validator/Validator.java#L2926-L2934
[b-observed]: ../../testdata/reference/iox/observed.json
[pdf-23-stream]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=77
[inv-23-stream]: ../../testdata/reference/norms/norm-inventory.json#L6729-L6736
[pdf-23-full]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=86
[inv-23-full]: ../../testdata/reference/norms/norm-inventory.json#L8025-L8032
[pdf-24-stream]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=99
[inv-24-stream]: ../../testdata/reference/norms/norm-inventory.json#L29988-L29995
[pdf-24-full]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=105
[inv-24-full]: ../../testdata/reference/norms/norm-inventory.json#L30580-L30587
[pdf-23-role]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=88
[inv-23-role]: ../../testdata/reference/norms/norm-inventory.json#L8153-L8160
[pdf-24-role]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=106
[inv-24-role]: ../../testdata/reference/norms/norm-inventory.json#L30724-L30731
[pdf-24-role-xsd]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=117
[inv-24-role-xsd]: ../../testdata/reference/norms/norm-inventory.json#L32500-L32507
[pdf-24-role-def]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=118
[inv-24-role-def]: ../../testdata/reference/norms/norm-inventory.json#L32692-L32699
[pdf-23-constraint]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=60
[inv-23-constraint]: ../../testdata/reference/norms/norm-inventory.json#L4553-L4560
[pdf-24-rounding]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=77
[inv-24-rounding]: ../../testdata/reference/norms/norm-inventory.json#L26996-L27003
[pdf-24-constraint]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=78
[inv-24-constraint]: ../../testdata/reference/norms/norm-inventory.json#L27044-L27051
