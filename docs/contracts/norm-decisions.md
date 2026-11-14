# Normauslegung und Referenzabweichungen

Die massgeblichen Normausgaben sind [INTERLIS 2.3, 13. April 2006][norm23] und [INTERLIS 2.4, eCH-0031 V2.1.0, 24. April 2024][norm24]. Die Seiten verweisen direkt auf die jeweiligen Stellen. Beobachtetes Verhalten von ili2c 5.6.9 dient dem gezielten Vergleich. Der aktuelle Vertrag gilt nach Massgabe der ausdrücklich genannten Kompatibilitätsprofile.

Die [Codeberg-Ausnahmen](../../testdata/conformance/normative/expectations.json) halten die fallbezogenen Kontrollen fest.


Die [Originalmodelle für Views und abgeleitete Assoziationen](../../testdata/conformance/views)
stammen unverändert aus den INTERLIS-2.3-Testdaten von
[ili2c im Quellstand 4ea207f](https://github.com/claeis/ili2c/tree/4ea207f388ef7d91b03d968cc1636f4de84e36ea/test/data/ili23).
Compiler-, Reuse- und Diagrammtests laden die Modellquellen direkt.

## Bewusst gewählte Kompatibilitätsprofile und Normauslegungen

| Sprachregel | Norm | ili2c 5.6.9 | Compilervertrag |
|---|---|---|---|
| <a id="decision-01"></a>**`OID AS` / `BASKET OID AS` ohne `CONTRACTED`, 2.3** | [2.3 §§2.5.1–2.5.2, S.28–29][ref-norm23-p28]: OID-Definitionen sind nur im Rahmen von Kontrakten zulässig; das Modell ist als `CONTRACTED` zu kennzeichnen. | Akzeptiert. | Akzeptiert. **Bestandskompatibilität.** Ausnahme gilt ausschliesslich für diese OID-Voraussetzung. |
| <a id="decision-02"></a>**Strukturen und abstrakte Klassen in `TYPE MODEL`, 2.3/2.4** | [2.3 §2.5.1, S.26][ref-norm23-p26]; [2.4 §3.5.1, S.32][ref-norm24-p32]: Die Aufzählung für `TYPE MODEL` nennt nur Masseinheiten, Wertebereiche, Funktionen und Linienformen; Strukturen und abstrakte Klassen fehlen. | Akzeptiert beide. | Akzeptiert beide. **wie ili2c.** Topics und konkrete Klassen bleiben verboten. |
| <a id="decision-03"></a>**REFSYSTEM BASKET in TYPE MODEL, 2.3/2.4** | [2.3 §2.5.1, S.26][ref-norm23-p26]; [2.4 §3.5.1, S.32][ref-norm24-p32]: Die abschliessende TYPE-MODEL-Aufzählung nennt Masseinheiten, Wertebereiche, Funktionen und Linienformen; Referenzsystemkörbe fehlen. | Akzeptiert unveränderte CHBase-Closure sowie minimierte 2.3/2.4-Kontrollen. | **Bestandskompatibilität.** `TYPE MODEL` lässt `REFSYSTEM BASKET` in beiden Sprachfassungen zu. Topics, konkrete Klassen und andere Korbarten bleiben ausgeschlossen. |
| <a id="decision-04"></a>**Skalierte numerische Bereichsgrenzen** | [2.3 §2.8.5, S.41–42][ref-norm23-p41]; [2.4 §3.8.5, S.52][ref-norm24-p52]: Bei Skalierung sind beide Grenzen in Mantissenform anzugeben; der untere Exponent muss kleiner sein. | Akzeptiert auch gleiche/absteigende Exponenten und gemischte Schreibweisen. | Akzeptiert diese bestehenden Formen. **breiteres Profil erhalten.** Werteordnung, Genauigkeit und Mantissenprüfungen bleiben wirksam. |
| <a id="decision-05"></a>**Exponentialschreibweise bei Konstanten und Einheitenfaktoren**, etwa `1.0e1`, `0.01e1` | [2.3 §2.2.4, S.24][ref-norm23-p24]; [2.4 §3.2.4, S.28][ref-norm24-p28] (`Dec`/`Float`), ergänzt durch [2.3 §2.9.2, S.56][ref-norm23-p56]; [2.4 §3.9.2, S.73][ref-norm24-p73] (Einheitenfaktoren): enge Float-Grammatik, aber widersprüchlich weitere Beispiele wie `1E2`. | Lehnt die geprüften Formen ab. | Akzeptiert und berechnet sie exakt. **unser breiteres Profil erhalten.** |
| <a id="decision-06"></a>**Kontinuierliche Unterteilung ohne bestimmbaren Umrechnungsfaktor** | [2.3 §2.6.1, S.31][ref-norm23-p31]; [2.4 §3.6.1, S.38–39][ref-norm24-p39]: Bei `CONTINUOUS` muss die Bereichsdifferenz zum Einheitenfaktor passen. [2.3 §2.9.2, S.56][ref-norm23-p56]; [2.4 §3.9.2, S.73][ref-norm24-p73] erlauben auch nur durch `FUNCTION` erläuterte Umrechnungen; deren Faktor ist nicht aus einer Zahlenformel bestimmbar. | Akzeptiert. | Akzeptiert unbekannte Faktoren; lehnt nachweisbare Faktorfehler ab. **bestehende Fälle erhalten.** Beschriebene Funktionen werden nicht ausgeführt. |
| <a id="decision-07"></a>**`CONTINUOUS SUBDIVISION` ohne `CIRCULAR`** | [2.3 §2.8.5, S.41][ref-norm23-p41]; [2.4 §3.8.5, S.52][ref-norm24-p52]: Verlangt `CIRCULAR`; kurzes Zitat aus 2.4: «muss der Typ als zirkulär definiert sein.» | Akzeptiert das geprüfte Original trotzdem. | Lehnt ab. **Normprüfung behalten**, konkrete Referenzausnahme mit erfolgreicher `CIRCULAR`-Kontrolle dokumentieren. |
| <a id="decision-08"></a>**Modellweite Klasse als Rollenziel, 2.3** | [2.3 §2.5.3, S.30][ref-norm23-p30]; [2.4 §3.5.3, S.36–37][ref-norm24-p36]: 2.3 verbietet Assoziationen zu direkt im Modell definierten Klassen ausdrücklich; 2.4 enthält dieses Verbot nicht mehr. | Akzeptiert. | Akzeptiert. **bisherige Kompatibilität erhalten.** |
| <a id="decision-09"></a>**Lokale Überdeckung äusserer Typnamen**, 2.3/2.4 | [2.3 §2.5.4, S.30–31][ref-norm23-p30]; [2.4 §3.5.4, S.38][ref-norm24-p38]: Äussere Namenskategorien werden übernommen; gleiche Typnamen dürfen damit nicht lokal kollidieren. | Akzeptiert die geprüfte Überdeckung. | Lehnt sie ab. **auch äussere Typnamen strikt sperren.** |
| <a id="decision-10"></a>**Relativer Herausgeber-URI**, etwa `AT "x"` | [2.3 §2.5.1, S.27][ref-norm23-p27]; [2.4 §3.5.1, S.33][ref-norm24-p33] (Herausgeber nach `AT`) und [2.3 §2.8.1, S.38][ref-norm23-p38]; [2.4 §3.8.1, S.48][ref-norm24-p48] (URI-Familie, RFC2396, 1023 Zeichen). Die Zulassung relativer URI-Referenzen ist das ausdrücklich gewählte Auslegungsprofil. | Verlangt einen absoluten URI. | Erlaubt relative URI-Referenzen. **relatives Profil erhalten**, mit Syntaxprüfung, ASCII und höchstens 1023 Zeichen; keine Hostauflösung. |
| <a id="decision-11"></a>**`areAreas2` / `areAreas3`, 2.4** | [2.4 §3.14, S.87][ref-norm24-p87] verlangt `areAreas2` mit Objektmenge und zeigt `ALL`; [2.4 Anhang A, S.122][ref-norm24-p122] definiert `areAreas2` mit Einzelobjekt und zusätzlich `areAreas3` mit Objektmenge. **Widerspruch innerhalb der Norm.** | Akzeptiert `areAreas2` mit `ALL` oder `THIS`; kennt `areAreas3` nicht. | Beide Funktionen besitzen `OBJECTS OF ANYCLASS`; Einzelobjekte dürfen als Singleton zugewiesen werden. **bestehendes Profil für beide Namen erhalten.** In 2.3 bleiben beide verboten. |
| <a id="decision-12"></a>**Funktionen mit `OBJECTS`-Parameter in MANDATORY-/Plausibilitätsbedingungen, 2.4** | [2.4 §3.12, S.77][ref-norm24-p77]: Objektbezogene MANDATORY-/Plausibilitätsbedingungen dürfen keine Funktion mit einem `OBJECTS OF`-Argument verwenden. | Akzeptiert sie. | Akzeptiert sie. **Nutzung wie ili2c erhalten.** |
| <a id="decision-13"></a>**Einzelobjektwerte im Hauptausdruck von `SET CONSTRAINT`**, 2.3/2.4 | [2.3 §2.12, S.60][ref-norm23-p60]; [2.4 §3.12, S.78][ref-norm24-p78]: Der SET-Hauptausdruck hat keinen individuellen Objektkontext. Kurzes Zitat aus 2.4: «nie die Werte von Attributen angesprochen werden.» Der `WHERE`-Filter bleibt objektbezogen. | Akzeptiert beispielsweise direkte Attributwerte. | Lehnt direkte Attribut-/Rollen-/`THIS`-Werte dort ab; erlaubt sie im `WHERE`. **Normkontext durchsetzen.** |
| <a id="decision-14"></a>**Überlappungstoleranz `0.0001` bei drei Koordinatennachkommastellen, 2.3** | [2.3 §2.8.12.2, S.50][ref-norm23-p50]: Toleranz muss grösser null sein; hier keine Präzisionsuntergrenze. [2.4 §3.8.12.2, S.64][ref-norm24-p64] fordert dagegen bei expliziter Toleranz dieselbe numerische Stellenzahl wie bei den Stützpunktkoordinaten. | Lehnt das geprüfte 2.3-Original ab. | Akzeptiert es; Null bleibt verboten. **versionsbezogene Normauslegung behalten.** Die 2.4-Regel wird nicht auf 2.3 übertragen. |
| <a id="decision-23"></a>**Referenzen und Beziehungen auf Klassenziele mit `NO OID`, 2.4** | [§3.5.3, S.36][ref-norm24-p36] verbietet Referenzen und Beziehungen auf Klassen mit effektivem `NO OID`. [§§4.3.7–4.3.9, S.104–106][ref-norm24-p104] unterscheiden transferweit eindeutige TIDs bei FULL von global eindeutigen OIDs bei INITIAL und UPDATE. [§4.3.11.16, S.112][ref-norm24-p112] nutzt dieselbe Referenzcodierung für Referenzattribute. | Akzeptiert die Originale, darunter `RoadsExgm2ien`. | **Bestandskompatibilität für FULL.** Rollen und Referenzattribute dürfen Klassen ohne stabile Objekt-OID auch über Behältergrenzen hinweg referenzieren. Die Originale `Roads` und `CoordSys` zeigen Assoziationen ohne Ziel-OID. Die Transfergemeinschaft legt die stabile Identität und den Einsatz inkrementeller Transfers fest. Der Compiler bewahrt die OID-Regeln, prüft die erforderlichen Topic-Deklarationen und verlangt weiterhin eine eigene Identität für Assoziationen als Referenzziel. |

## Numerische Datenauswertung

INTERLIS 2.4 §3.13 S.82 nennt IEEE-754 für Ausdrücke mit voller Genauigkeit
und verweist für Rundung auf §2.8. Die allgemeine Ausdrucksarithmetik ist
eine Neuerung von 2.4. Numerische Literale und Transferwerte bleiben in
beiden Sprachfassungen exakte Dezimalwerte.

Der Validator verwendet endliche Dezimalwerte. Addition, Subtraktion,
Negation, Multiplikation und terminierende Division bleiben innerhalb des
Grössenbudgets exakt. PI und LNBASE sowie nichtterminierende Quotienten
werden auf 50 signifikante Stellen mit HALF_EVEN gerundet. Das übernimmt
die bisher für Konstanten verfügbare Grössenordnung der Präzision und
liefert für Berechnung, Vergleich, Schlüssel und Funktionsargumente
denselben Wert. Die vorhandenen Grenzen für Dezimalskala und Koeffizient
führen zu einem technischen Fehler. Es gibt keinen Unterlauf auf null.

Damit ist `0.1 + 0.2 == 0.3` wahr, während `(1/3)*3 == 1` und
`PI == 3.141592653589793` falsch sind. Binary64, wie es iox-ili verwendet,
liefert für diese drei Einzelrechnungen falsch, wahr und wahr. Die
Attributrundung folgt weiterhin dem deklarierten Raster nach §2.8.
[INTERLIS-Referenzhandbuch][norm24]

## Weitere historische Fälle: ausdrücklich der Norm folgen

| Sprachregel | Norm | ili2c 5.6.9 | Compilervertrag |
|---|---|---|---|
| <a id="decision-15"></a>**Assoziation als Rollenziel ohne eigene OID** | [2.3 §2.7.1, S.35][ref-norm23-p35]: Nur mit eigener Objektidentifikation kann eine Beziehung selbst in Rollen als Bezug verwendet werden. | Akzeptiert das Original. | Lehnt ab; OID-Kontrolle besteht. **Norm beibehalten.** |
| <a id="decision-16"></a>**Mehrere Links zwischen denselben Teilnehmern ohne OID** | [2.3 §2.7.1, S.35][ref-norm23-p35]: Mehrere Beziehungsinstanzen zwischen derselben Objektkombination verlangen eine Kardinalität mit Obergrenze grösser eins und eine eigene OID. | Akzeptiert das Original. | Lehnt ab. **Norm beibehalten.** |
| <a id="decision-17"></a>**`HIDING` mit widersprüchlicher Geschwisterrolle** | [2.3 §2.7.1, S.34][ref-norm23-p34]: `HIDING` verdeckt die allgemeinere Beziehung; eine nicht verdeckende Geschwisterbeziehung mit demselben Ziel ist unzulässig. | Akzeptiert das Original. | Lehnt ab. **Norm beibehalten.** |
| <a id="decision-18"></a>**Ableitung `TEXT*20` → `TEXT*10`** | [2.3 §2.6.1, S.31][ref-norm23-p31] (Ableitung/Zuweisung) und [2.3 §2.8.1, S.38][ref-norm23-p38] (TEXT-Bereiche): Der abgeleitete Wert muss in den Zielbereich passen; `TEXT*20` passt nicht in `TEXT*10`. | Akzeptiert das Original. | Lehnt ab. **Strikte Zuweisungskompatibilität.** |
| <a id="decision-19"></a>**Unbeschränktes TEXT-Funktionsergebnis → `TEXT*80`** | [2.3 §2.6.1, S.31][ref-norm23-p31] und [2.3 §2.8.1, S.38][ref-norm23-p38]: Unbeschränktes TEXT passt nicht in den engeren Zieltyp `TEXT*80`. | Akzeptiert das Original. | Lehnt ab. **Strikte Zuweisungskompatibilität.** |
| <a id="decision-20"></a>**Numerische Ableitung → BOOLEAN** | [2.3 §2.6.1, S.31][ref-norm23-p31] und [2.3 §2.8.4, S.41][ref-norm23-p41]: Eine numerische Ableitung ist nicht dem booleschen Attributtyp zuweisungskompatibel. | Akzeptiert das Original. | Lehnt ab. **Strikte Zuweisungskompatibilität.** |
| <a id="decision-21"></a>**Laufzeitparameter ohne `CONTRACTED`, 2.3** | [2.3 §2.11, S.59][ref-norm23-p59] zusammen mit [2.3 §2.5.1, S.28][ref-norm23-p28]: Laufzeitparameter dürfen nur in Modellen mit Kontrakten definiert werden; `CONTRACTED` ist erforderlich. | Akzeptiert die zwei Originalfälle. | Lehnt ab. **Norm beibehalten.** Die OID-Ausnahme wird nicht auf Laufzeitparameter ausgedehnt. |
| <a id="decision-22"></a>**`ALL OF` bei AREA-Inspektionen** | [2.3 §2.15, S.69][ref-norm23-p69]; [2.4 §3.15, S.91][ref-norm24-p91]: `ALL OF` ist bei Gebietsinspektionen ausdrücklich unzulässig. | Akzeptiert auch die direkte Form. | Lehnt direkte und über Views geerbte Formen ab. **Norm beibehalten und Umgehung schliessen.** |

## Weitere dokumentierte Unterschiede und Spezifikationskorrekturen

| Sprachregel | Norm | ili2c 5.6.9 | Compilervertrag |
|---|---|---|---|
| <a id="decision-24"></a>**Rollen bei `ALL OF`, 2.4** | [2.3 §2.15, S.69][ref-norm23-p69]; [2.4 §3.15, S.91][ref-norm24-p91]: 2.3 nennt bei `ALL OF` Attribute; 2.4 ausdrücklich Attribute und Rollen. | Lässt die geprüften 2.4-Rollen im Ergebnis weg. | Übernimmt in 2.4 auch Rollen mit Typ, Ziel, Reihenfolge und Herkunft. **Versionsbezogen der Norm folgen.** |
| <a id="decision-25"></a>**Reservierte Wörter und `XML` als Name** | [2.3 §2.2.7, S.25][ref-norm23-p25]; [2.4 §3.2.7, S.29–30][ref-norm24-p29] enthalten die vollständigen reservierten Worttabellen. [2.3 §2.8.10, S.46][ref-norm23-p46]; [2.4 §3.8.10, S.59][ref-norm24-p59] verwenden `XML` im Kontext `BLACKBOX XML`, nicht als allgemein reservierten Namen. | Erlaubt mehrere weiterhin reservierte Legacywörter als Namen und sperrt `XML` allgemein. | **Normtabellen durchsetzen**; `XML` ausserhalb seines Schlüsselwortkontexts erlauben. |
| <a id="decision-26"></a>**Namen mit mehr als 255 Zeichen** | [2.3 §2.2.2, S.23][ref-norm23-p23]; [2.4 §3.2.2, S.27][ref-norm24-p27]: Namen sind auf 255 Zeichen begrenzt. | Akzeptiert die geprüften 256-Zeichen-Namen. | Lehnt sie ab. **Normgrenze durchsetzen.** |
| <a id="decision-27"></a>**Struktur-/Listenindizes** | [2.3 §2.13, S.63–64][ref-norm23-p64]; [2.4 §3.13, S.82–83][ref-norm24-p83]: FIRST/LAST und numerische Auswahl gelten für geordnete Unterstrukturen; erster Index ist 1, die maximale Kardinalität darf nicht überschritten werden. | Akzeptiert etwa `BAG[FIRST]`, `LIST[0]` und `LIST[4]` bei maximal drei Elementen. | Lehnt diese Formen ab. **Typ und Grenzen prüfen.** |
| <a id="decision-28"></a>**Koordinatenachsen und grosse gültige Listenindizes** | [2.3 §2.13, S.63–64][ref-norm23-p64]; [2.4 §3.13, S.82–83][ref-norm24-p83]: Koordinatenachsen und LIST-Elemente werden einbasiert adressiert; die Norm legt keine zusätzliche Java-`int`-/`long`-Grenze fest. Es gelten die fachlichen Grenzen und unsere dokumentierten Ressourcenbudgets. | Scheitert in geprüften Fällen an Indexfehlern beziehungsweise Ganzzahlgrenzen. | Bindet die gültigen Zugriffe korrekt und rechnet exakt. **Referenzfehler nicht übernehmen.** |
| <a id="decision-29"></a>**EXISTENCE-Typkompatibilität** | [2.3 §2.12, S.59–60][ref-norm23-p59]; [2.4 §3.12, S.77–78][ref-norm24-p77] verlangen kompatible Attribute. [2.3 §2.6.4, S.33][ref-norm23-p33]; [2.4 §3.6.4, S.41–42][ref-norm24-p41] unterscheiden Strukturwerte und geordnete/ungeordnete Collections. Die genaue Collection-Kompatibilität ist unsere dokumentierte Auslegung der Wertsemantik; kein dort vollständig vorgegebener Algorithmus. | Akzeptiert mehrere inkompatible Kombinationen anhand grober Typklassen. | Prüft die tatsächlichen Typmerkmale. **Keine pauschale Gleichsetzung von BAG/LIST oder Strukturtypen.** |
| <a id="decision-30"></a>**`ANYOID` als Attribut und im passenden EXISTENCE-Vergleich** | [2.3 §2.8.9, S.45–46][ref-norm23-p46]; [2.4 §3.8.9, S.58–59][ref-norm24-p59] erlauben `ANYOID` als Attributwertebereich unter Mitführung der konkreten OID-Domäne; für den Vergleich zusätzlich [2.3 §2.12, S.59][ref-norm23-p59]; [2.4 §3.12, S.77][ref-norm24-p77]. | Lehnt die geprüften Fälle ab. | Akzeptiert sie unter Erhaltung der konkreten Trägerart. **Normzulassung umsetzen.** |
| <a id="decision-31"></a>**FORMAT-Präzision, Auffüllung und Grenzen** | [2.3 §§2.8.5–2.8.6, S.41–44][ref-norm23-p41]; [2.4 §§3.8.5–3.8.6, S.52–55][ref-norm24-p52]: Numerische Genauigkeit, lexikalische Auffüllung, Formatbestandteile und Vererbungsgrenzen müssen eingehalten werden. | Toleriert mehrere ungültige Formen; scheitert umgekehrt an einzelnen gültigen verschachtelten Formaten. | Prüft die Regeln exakt und unterstützt die gültigen Fälle. **Norm statt Referenzfehler.** |
| <a id="decision-32"></a>**`len(THIS)` bei numerischem THIS** | [2.3 §2.14, S.65–66][ref-norm23-p65]; [2.4 §3.14, S.86][ref-norm24-p86] sowie die jeweiligen normativen Anhänge A: Die Signatur von `len` verlangt TEXT. | Akzeptiert die geprüften numerischen Fälle. | Lehnt ab. **Signatur prüfen.** |
| <a id="decision-33"></a>**Konkretisierung von `NUMERIC [ANYUNIT]` auf eine lokale Metereinheit** | [2.3 §2.9.1, S.55][ref-norm23-p55]; [2.4 §3.9.1, S.72][ref-norm24-p72]: Alle Einheiten erben direkt oder indirekt von `ANYUNIT`, auch ohne explizite Deklaration. | Lehnt die geprüften Konkretisierungen ab. | Akzeptiert sie. **Normative Einheitenhierarchie umsetzen.** |
| <a id="decision-34"></a>**Eigene Linienform mit gewöhnlicher Struktur als Segmenttyp** | [2.3 §2.8.12.3, S.51][ref-norm23-p51]; [2.4 §3.8.12.3, S.66][ref-norm24-p66]: Die Linienstruktur muss eine Erweiterung von `LineSegment` sein. | Akzeptiert die geprüfte gewöhnliche Struktur. | Lehnt ab. **LineSegment-Vertrag durchsetzen.** |
| <a id="decision-35"></a>**Eigene Linienform ohne `CONTRACTED`, 2.4** | [2.4 §3.8.12.3, S.66][ref-norm24-p66] enthält im Unterschied zu [2.3 §2.8.12.3, S.51][ref-norm23-p51] keine Kontraktpflicht. Die Aufhebung der Kontrakte ist zusätzlich im [2.4 Vorwort – Erweiterungen gegenüber 2.3, S.8][ref-norm24-p8] dokumentiert. | Verlangt sie in der geprüften Gegenprobe weiterhin. | Akzeptiert. **2.4-Regel anwenden.** |
| <a id="decision-36"></a>**Fremde Signaturklasse bei Grafikregeln** | [2.3 §2.16, S.71][ref-norm23-p71]; [2.4 §3.16, S.92–93][ref-norm24-p92]: Die verantwortliche Signaturklasse ergibt sich aus Metaobjekt und verwendeten Parametern; sie muss mit der deklarierten OF-Klasse in einer passenden Vererbungskette liegen. | Akzeptiert auch die geprüfte fremde Klasse. | Akzeptiert passende Basis-/Unterklassen, lehnt die fremde Klasse ab. **Verantwortlichkeit fachlich prüfen.** |
| <a id="decision-37"></a>**Wiederholte Metadaten und mehrzeilige `!!@`-Strings** | Projektprofil: [Quell-Metaattribute](sources-and-syntax.md#quell-metaattribute-und-dokumentation). Die Normkapitel [2.3 §2.2.8.1, S.25][ref-norm23-p25]; [2.4 §3.2.8.1, S.31][ref-norm24-p31] definieren Zeilenkommentare, aber keinen öffentlichen `fn.param`-Mehrfachwertvertrag. Unser ursprüngliches Kommentarprofil war enger. | Kein gleichwertiger öffentlicher Mehrfachwertvertrag wurde als Referenzgleichheit abgenommen. | **Vom Nutzer freigegebener Kompatibilitätsfix:** mehrere unterschiedliche `fn.param` an Funktionen und Stringfortsetzungen erlauben; sonstige Skalarkonflikte bleiben Fehler. |

## Lokale Datenwerte

Der Validator zählt TEXT und MTEXT gemäss 2.3 §2.8.1 und 2.4 §3.8.1
mit Unicode 17.0.0. Die Länge entspricht der Zahl der Zeichen mit
kanonischer Kombinationsklasse 0 nach kanonischer Zerlegung. Ein Wert mit
Länge 0 ist fachlich undefiniert. Die TEXT-/MTEXT-Steuerzeichenregel gilt unabhängig vom Repertoire.
Ein expliziter Modellzeichensatz beschreibt, welche Zeichen eine Software
verarbeiten können muss (2.4 §3.5.1). Liegt ein Zeichen ausserhalb dieses
Repertoires oder des Standardrepertoires nach 2.3 Anhang B bzw. 2.4 Anhang D,
bleibt die Repertoireprüfung ungeprüft. NAME folgt den reservierten Wörtern
der Sprachversion, URI der URI-Syntax von RFC 2396 ohne Netzwerkzugriff.

Numerische Transferwerte werden unverändert gegen inklusive Modellgrenzen
verglichen. Höhere Genauigkeit führt innerhalb des Bereichs zu keinem
Datenfehler (2.3 §3.3.11.4, 2.4 §4.3.11.4). Die Prüfung rundet keinen
Transferwert und rechnet keine Einheiten um.

Lokale Geometrieprüfungen verwenden die Achsenbereiche der wirksamen
Domäne und die deklarierten Standardsegmentformen. Eine konkrete Erweiterung
einer generischen Domäne behält ihre eigenen Achsen. Basket-Bindungen
konkretisieren generische Verwendungen. Gelieferte Bogenradien bleiben
unverändert und werden als ungeprüfter Teil der Geometrieregel ausgewiesen.

Eine vollständig lokal ausführbare MANDATORY-Bedingung, die wegen ihrer
Daten fachlich undefiniert ist, gilt nach 2.4 §3.12 als erfüllt. In 2.3
gilt das für die belegte ilivalidator-1.15.0-Kompatibilität von Vergleichen
mit undefiniertem Operand. Der Bericht kennzeichnet diese Fälle separat.
Nicht unterstützte Ausdrücke (unter anderem Funktionsaufrufe, Fremdobjektpfade
und Division) und fehlender Laufzeitkontext bleiben als ungeprüft erkennbar.

`UNIQUE (LOCAL)` prüft die über den gebundenen Präfix erreichbaren BAG-/LIST-
Strukturen pro Besitzerinstanz (2.3 und 2.4 §3.12). Geerbte Felder werden über
die effektiven Slots gelesen. Schlüssel aus Text, Zahlen, Aufzählungen,
Ausrichtungen und BOOLEAN werden in Deklarationsreihenfolge verglichen.
Zahlen mit gleichem exaktem Wert kollidieren auch bei unterschiedlicher
Schreibweise. Ein Element mit einer undefinierten Komponente, einschliesslich
TEXT mit normativer Länge null, nimmt nicht am Vergleich teil. Die Diagnose enthält den Pfad der
späteren Stelle sowie die Vorkommensindizes beider Stellen. Der Index endet
mit der Prüfung des Besitzers. WHERE, andere Schlüsseltypen, geometrische
Linienattribute in 2.3 und nicht unterstützte Pfade erscheinen als
ungeprüfte Regeln. Globales und basketweites UNIQUE bleiben ausserhalb des
lokalen Prüfumfangs.

## Quellen

[norm23]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf
[norm24]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf
[ref-norm23-p28]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=28
[ref-norm23-p26]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=26
[ref-norm24-p32]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=32
[ref-norm23-p41]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=41
[ref-norm24-p52]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=52
[ref-norm23-p24]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=24
[ref-norm24-p28]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=28
[ref-norm23-p56]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=56
[ref-norm24-p73]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=73
[ref-norm23-p31]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=31
[ref-norm24-p39]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=39
[ref-norm23-p30]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=30
[ref-norm24-p36]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=36
[ref-norm24-p38]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=38
[ref-norm23-p27]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=27
[ref-norm24-p33]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=33
[ref-norm23-p38]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=38
[ref-norm24-p48]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=48
[ref-norm24-p87]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=87
[ref-norm24-p122]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=122
[ref-norm24-p77]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=77
[ref-norm23-p60]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=60
[ref-norm24-p78]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=78
[ref-norm23-p50]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=50
[ref-norm24-p64]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=64
[ref-norm23-p35]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=35
[ref-norm23-p34]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=34
[ref-norm23-p59]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=59
[ref-norm23-p69]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=69
[ref-norm24-p91]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=91
[ref-norm24-p41]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=41
[ref-norm23-p25]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=25
[ref-norm24-p29]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=29
[ref-norm23-p46]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=46
[ref-norm24-p59]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=59
[ref-norm23-p23]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=23
[ref-norm24-p27]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=27
[ref-norm23-p64]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=64
[ref-norm24-p83]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=83
[ref-norm23-p33]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=33
[ref-norm23-p65]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=65
[ref-norm24-p86]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=86
[ref-norm23-p55]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=55
[ref-norm24-p72]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=72
[ref-norm23-p51]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=51
[ref-norm24-p66]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=66
[ref-norm24-p8]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=8
[ref-norm23-p71]: https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=71
[ref-norm24-p92]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=92
[ref-norm24-p31]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=31
[ref-norm24-p104]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=104
[ref-norm24-p112]: https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=112

### Topologie und Bogenradius

XTF-2.3-Radien kodieren die Umlaufrichtung im Vorzeichen (positiv im
Uhrzeigersinn, 2.3 §3.3.11.12). In XTF 2.4 muss der Radius positiv
sein und der Zwischenpunkt wählt den Bogen (2.4 §3.8.12.2 und
§4.3.11.14). Die neutrale Darstellung bewahrt Zahl und Konvention.
Der XTF-2.4-Reader erhält numerisch dekodierbare nichtpositive Radien.
Die Engine bewertet sie fachlich als ungültig, und der Writer weist
sie zurück. Ein verwendbarer Bogen bleibt auch bei kleiner Pfeilhöhe
analytisch. Die Werteprüfung bewahrt den ursprünglichen Transferwert.

AREA-Prüfungen verwenden die wirksame Attributdeklaration und
vergleichbare Koordinatentypen. Restflächen sind zulässig. Die
WithoutGaps-Varianten prüfen begrenzte unbelegte Regionen innerhalb
einer abgeschlossenen Auswahl. Bei den Standardfunktionen gilt für
Vergleiche in einer heterogenen Menge die grösste wirksame
Überlappungstoleranz ausgewählter Halter nach ilivalidator 1.15.1.
Jeder Einzelwert behält seine eigene Toleranz.

Der Kurvenpfad verwendet mathematische Kreisprimitive und JTS-Primitive
für Orientierung, Kreismittelpunkt, Geradenschnitt und räumliche Indizes.
Die iox-ili-1.24.6-Quellen und die jeweiligen Fixes dienten als
Vergleich. Aus iox-ili wurde kein Quellcode übernommen.
