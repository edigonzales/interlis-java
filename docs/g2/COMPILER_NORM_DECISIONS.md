# Compiler, ili2c und Norm – Abweichungen und Entscheidungen

Stand: 30. Oktober 2026, nach Abschluss von G2.

Die Übersicht zeigt den heutigen Stand, fachlich nach Regeln gebündelt. Mit
«ilic» aus der ursprünglichen Anfrage ist hier der geprüfte Referenzcompiler
**ili2c 5.6.9** gemeint. «Norm» bezeichnet die INTERLIS-Handbücher; unsere
**Projektspezifikation enthält inzwischen die gewählten Profile**. Mehrere
Suitefälle können derselben Tabellenzeile entsprechen. Dies ist eine
Zusammenfassung der verlinkten Nachweise, keine zusätzliche Vertragsquelle.
Historische Berichte behalten ihren damaligen Stand; der operative Abschluss
steht in [STATUS.md](STATUS.md).

Die Normlinks verweisen auf die gepinnten Ausgaben [INTERLIS 2.3, 13. April 2006][norm23] und [INTERLIS 2.4, eCH-0031 V2.1.0, 24. April 2024][norm24]. Angegeben sind Kapitel und PDF-Seiten; die Links springen zur einschlägigen Seite. Formulierungen ohne Anführungszeichen sind Zusammenfassungen beziehungsweise ausdrücklich bezeichnete Auslegungen.

## Bewusst gewählte Kompatibilitätsprofile und Normauslegungen

| Thema | Norm / ursprünglicher Vertrag | ili2c | Unser Compiler heute | Unsere Entscheidung |
|---|---|---|---|---|
| **`OID AS` / `BASKET OID AS` ohne `CONTRACTED`, 2.3** | [2.3 §§2.5.1–2.5.2, S.28–29][ref-norm23-p28]: OID-Definitionen sind nur im Rahmen von Kontrakten zulässig; das Modell ist als `CONTRACTED` zu kennzeichnen. | Akzeptiert. | Akzeptiert. | **Nutzerentscheid: Bestandskompatibilität.** Ausnahme gilt ausschliesslich für diese OID-Voraussetzung. |
| **Strukturen und abstrakte Klassen in `TYPE MODEL`, 2.3/2.4** | [2.3 §2.5.1, S.26][ref-norm23-p26]; [2.4 §3.5.1, S.32][ref-norm24-p32]: Die Aufzählung für `TYPE MODEL` nennt nur Masseinheiten, Wertebereiche, Funktionen und Linienformen; Strukturen und abstrakte Klassen fehlen. | Akzeptiert beide. | Akzeptiert beide. | **Nutzerentscheid: wie ili2c.** Topics und konkrete Klassen bleiben verboten. |
| **Skalierte numerische Bereichsgrenzen** | [2.3 §2.8.5, S.41–42][ref-norm23-p41]; [2.4 §3.8.5, S.52][ref-norm24-p52]: Bei Skalierung sind beide Grenzen in Mantissenform anzugeben; der untere Exponent muss kleiner sein. | Akzeptiert auch gleiche/absteigende Exponenten und gemischte Schreibweisen. | Akzeptiert diese bestehenden Formen. | **Nutzerentscheid: breiteres Profil erhalten.** Werteordnung, Genauigkeit und Mantissenprüfungen bleiben wirksam. |
| **Exponentialschreibweise bei Konstanten und Einheitenfaktoren**, etwa `1.0e1`, `0.01e1` | [2.3 §2.2.4, S.24][ref-norm23-p24]; [2.4 §3.2.4, S.28][ref-norm24-p28] (`Dec`/`Float`), ergänzt durch [2.3 §2.9.2, S.56][ref-norm23-p56]; [2.4 §3.9.2, S.73][ref-norm24-p73] (Einheitenfaktoren): enge Float-Grammatik, aber widersprüchlich weitere Beispiele wie `1E2`. | Lehnt die geprüften Formen ab. | Akzeptiert und berechnet sie exakt. | **Nutzerentscheid: unser breiteres Profil erhalten.** |
| **Kontinuierliche Unterteilung ohne bestimmbaren Umrechnungsfaktor** | [2.3 §2.6.1, S.31][ref-norm23-p31]; [2.4 §3.6.1, S.38–39][ref-norm24-p39]: Bei `CONTINUOUS` muss die Bereichsdifferenz zum Einheitenfaktor passen. [2.3 §2.9.2, S.56][ref-norm23-p56]; [2.4 §3.9.2, S.73][ref-norm24-p73] erlauben auch nur durch `FUNCTION` erläuterte Umrechnungen; deren Faktor ist nicht aus einer Zahlenformel bestimmbar. | Akzeptiert. | Akzeptiert unbekannte Faktoren; lehnt nachweisbare Faktorfehler ab. | **Nutzerentscheid: bestehende Fälle erhalten.** Beschriebene Funktionen werden nicht ausgeführt. |
| **`CONTINUOUS SUBDIVISION` ohne `CIRCULAR`** | [2.3 §2.8.5, S.41][ref-norm23-p41]; [2.4 §3.8.5, S.52][ref-norm24-p52]: Verlangt `CIRCULAR`; kurzes Zitat aus 2.4: «muss der Typ als zirkulär definiert sein.» | Akzeptiert das geprüfte Original trotzdem. | Lehnt ab. | **Nutzerentscheid: Normprüfung behalten**, konkrete Referenzausnahme mit erfolgreicher `CIRCULAR`-Kontrolle dokumentieren. |
| **Modellweite Klasse als Rollenziel, 2.3** | [2.3 §2.5.3, S.30][ref-norm23-p30]; [2.4 §3.5.3, S.36–37][ref-norm24-p36]: 2.3 verbietet Assoziationen zu direkt im Modell definierten Klassen ausdrücklich; 2.4 enthält dieses Verbot nicht mehr. | Akzeptiert. | Akzeptiert. | **Nutzerentscheid: bisherige Kompatibilität erhalten.** |
| **Lokale Überdeckung äusserer Typnamen**, 2.3/2.4 | [2.3 §2.5.4, S.30–31][ref-norm23-p30]; [2.4 §3.5.4, S.38][ref-norm24-p38]: Äussere Namenskategorien werden übernommen; gleiche Typnamen dürfen damit nicht lokal kollidieren. | Akzeptiert die geprüfte Überdeckung. | Lehnt sie ab. | **Nutzerentscheid: auch äussere Typnamen strikt sperren.** |
| **Relativer Herausgeber-URI**, etwa `AT "x"` | [2.3 §2.5.1, S.27][ref-norm23-p27]; [2.4 §3.5.1, S.33][ref-norm24-p33] (Herausgeber nach `AT`) und [2.3 §2.8.1, S.38][ref-norm23-p38]; [2.4 §3.8.1, S.48][ref-norm24-p48] (URI-Familie, RFC2396, 1023 Zeichen). Die Zulassung relativer URI-Referenzen ist das ausdrücklich gewählte Auslegungsprofil. | Verlangt einen absoluten URI. | Erlaubt relative URI-Referenzen. | **Nutzerentscheid: relatives Profil erhalten**, mit Syntaxprüfung, ASCII und höchstens 1023 Zeichen; keine Hostauflösung. |
| **`areAreas2` / `areAreas3`, 2.4** | [2.4 §3.14, S.87][ref-norm24-p87] verlangt `areAreas2` mit Objektmenge und zeigt `ALL`; [2.4 Anhang A, S.122][ref-norm24-p122] definiert `areAreas2` mit Einzelobjekt und zusätzlich `areAreas3` mit Objektmenge. **Widerspruch innerhalb der Norm.** | Akzeptiert `areAreas2` mit `ALL` oder `THIS`; kennt `areAreas3` nicht. | Beide Funktionen besitzen `OBJECTS OF ANYCLASS`; Einzelobjekte dürfen als Singleton zugewiesen werden. | **Nutzerentscheid: bestehendes Profil für beide Namen erhalten.** In 2.3 bleiben beide verboten. |
| **Funktionen mit `OBJECTS`-Parameter in MANDATORY-/Plausibilitätsbedingungen, 2.4** | [2.4 §3.12, S.77][ref-norm24-p77]: Objektbezogene MANDATORY-/Plausibilitätsbedingungen dürfen keine Funktion mit einem `OBJECTS OF`-Argument verwenden. | Akzeptiert sie. | Akzeptiert sie. | **Nutzerentscheid: Nutzung wie ili2c erhalten.** |
| **Einzelobjektwerte im Hauptausdruck von `SET CONSTRAINT`**, 2.3/2.4 | [2.3 §2.12, S.60][ref-norm23-p60]; [2.4 §3.12, S.78][ref-norm24-p78]: Der SET-Hauptausdruck hat keinen individuellen Objektkontext. Kurzes Zitat aus 2.4: «nie die Werte von Attributen angesprochen werden.» Der `WHERE`-Filter bleibt objektbezogen. | Akzeptiert beispielsweise direkte Attributwerte. | Lehnt direkte Attribut-/Rollen-/`THIS`-Werte dort ab; erlaubt sie im `WHERE`. | **Nutzerentscheid: Normkontext durchsetzen.** |
| **Überlappungstoleranz `0.0001` bei drei Koordinatennachkommastellen, 2.3** | [2.3 §2.8.12.2, S.50][ref-norm23-p50]: Toleranz muss grösser null sein; hier keine Präzisionsuntergrenze. [2.4 §3.8.12.2, S.64][ref-norm24-p64] fordert dagegen bei expliziter Toleranz dieselbe numerische Stellenzahl wie bei den Stützpunktkoordinaten. | Lehnt das geprüfte 2.3-Original ab. | Akzeptiert es; Null bleibt verboten. | **Nutzerentscheid: versionsbezogene Normauslegung behalten.** Die 2.4-Regel wird nicht auf 2.3 übertragen. |

Belege: [OID und Metadaten](implementation/L0B-3.md),
[TYPE MODEL](implementation/L1-1.md),
[numerische Profile und CIRCULAR](implementation/L1-2.md),
[Funktions- und SET-Profile](implementation/L2-1.md),
[Rollenziele](implementation/L4-1-model-class-decision.md),
[Namensraum und URI](implementation/L4-1-profile-questions.md),
[Überlappungstoleranz](implementation/L4-1-decisions.md).

## Weitere historische Fälle: ausdrücklich der Norm folgen

Die folgenden Regeln gehören zu dem vom Nutzer bestätigten Paket der zwölf
historischen Fälle; die positive Überlappungstoleranz steht bereits oben.

| Thema | Norm | ili2c | Unser Compiler heute | Unsere Entscheidung |
|---|---|---|---|---|
| **Assoziation als Rollenziel ohne eigene OID** | [2.3 §2.7.1, S.35][ref-norm23-p35]: Nur mit eigener Objektidentifikation kann eine Beziehung selbst in Rollen als Bezug verwendet werden. | Akzeptiert das Original. | Lehnt ab; OID-Kontrolle besteht. | **Norm beibehalten.** |
| **Mehrere Links zwischen denselben Teilnehmern ohne OID** | [2.3 §2.7.1, S.35][ref-norm23-p35]: Mehrere Beziehungsinstanzen zwischen derselben Objektkombination verlangen eine Kardinalität mit Obergrenze grösser eins und eine eigene OID. | Akzeptiert das Original. | Lehnt ab. | **Norm beibehalten.** |
| **`HIDING` mit widersprüchlicher Geschwisterrolle** | [2.3 §2.7.1, S.34][ref-norm23-p34]: `HIDING` verdeckt die allgemeinere Beziehung; eine nicht verdeckende Geschwisterbeziehung mit demselben Ziel ist unzulässig. | Akzeptiert das Original. | Lehnt ab. | **Norm beibehalten.** |
| **Ableitung `TEXT*20` → `TEXT*10`** | [2.3 §2.6.1, S.31][ref-norm23-p31] (Ableitung/Zuweisung) und [2.3 §2.8.1, S.38][ref-norm23-p38] (TEXT-Bereiche): Der abgeleitete Wert muss in den Zielbereich passen; `TEXT*20` passt nicht in `TEXT*10`. | Akzeptiert das Original. | Lehnt ab. | **Strikte Zuweisungskompatibilität.** |
| **Unbeschränktes TEXT-Funktionsergebnis → `TEXT*80`** | [2.3 §2.6.1, S.31][ref-norm23-p31] und [2.3 §2.8.1, S.38][ref-norm23-p38]: Unbeschränktes TEXT passt nicht in den engeren Zieltyp `TEXT*80`. | Akzeptiert das Original. | Lehnt ab. | **Strikte Zuweisungskompatibilität.** |
| **Numerische Ableitung → BOOLEAN** | [2.3 §2.6.1, S.31][ref-norm23-p31] und [2.3 §2.8.4, S.41][ref-norm23-p41]: Eine numerische Ableitung ist nicht dem booleschen Attributtyp zuweisungskompatibel. | Akzeptiert das Original. | Lehnt ab. | **Strikte Zuweisungskompatibilität.** |
| **Laufzeitparameter ohne `CONTRACTED`, 2.3** | [2.3 §2.11, S.59][ref-norm23-p59] zusammen mit [2.3 §2.5.1, S.28][ref-norm23-p28]: Laufzeitparameter dürfen nur in Modellen mit Kontrakten definiert werden; `CONTRACTED` ist erforderlich. | Akzeptiert die zwei Originalfälle. | Lehnt ab. | **Norm beibehalten.** Die OID-Ausnahme wird nicht auf Laufzeitparameter ausgedehnt. |
| **`ALL OF` bei AREA-Inspektionen** | [2.3 §2.15, S.69][ref-norm23-p69]; [2.4 §3.15, S.91][ref-norm24-p91]: `ALL OF` ist bei Gebietsinspektionen ausdrücklich unzulässig. | Akzeptiert auch die direkte Form. | Lehnt direkte und über Views geerbte Formen ab. | **Norm beibehalten und Umgehung schliessen.** |
| **Referenzen/Beziehungen auf Ziele mit `NO OID`, 2.4** | [2.4 §3.5.3, S.36][ref-norm24-p36] und [2.4 §3.6.3, S.41][ref-norm24-p41]: Ohne Klassen-/Topic-OID gilt gegebenenfalls implizit `NO OID`; Referenzen und Beziehungen auf solche Klassen sind verboten. | Akzeptiert die geprüften Originale, darunter `RoadsExgm`. | Lehnt ab; ausschliesslich um OIDs korrigierte Kontrollen bestehen. | **Norm beibehalten**, trotz Folgen für bestehende Modelle. |

Beleg: [Nutzerbestätigung mit allen zwölf Originalfällen und Kontrollen](implementation/L4-1-decisions.md).

## Weitere dokumentierte Unterschiede und Spezifikationskorrekturen

Hier handelt es sich überwiegend um die Umsetzung bestehender Normverträge;
eine gesonderte Nutzerentscheidung war dafür nicht jeweils erforderlich.

| Thema | Norm / Projektspezifikation | ili2c | Unser Compiler heute / gewählte Umsetzung |
|---|---|---|---|
| **Rollen bei `ALL OF`, 2.4** | [2.3 §2.15, S.69][ref-norm23-p69]; [2.4 §3.15, S.91][ref-norm24-p91]: 2.3 nennt bei `ALL OF` Attribute; 2.4 ausdrücklich Attribute und Rollen. | Lässt die geprüften 2.4-Rollen im Ergebnis weg. | Übernimmt in 2.4 auch Rollen mit Typ, Ziel, Reihenfolge und Herkunft. **Versionsbezogen der Norm folgen.** |
| **Reservierte Wörter und `XML` als Name** | [2.3 §2.2.7, S.25][ref-norm23-p25]; [2.4 §3.2.7, S.29–30][ref-norm24-p29] enthalten die vollständigen reservierten Worttabellen. [2.3 §2.8.10, S.46][ref-norm23-p46]; [2.4 §3.8.10, S.59][ref-norm24-p59] verwenden `XML` im Kontext `BLACKBOX XML`, nicht als allgemein reservierten Namen. | Erlaubt mehrere weiterhin reservierte Legacywörter als Namen und sperrt `XML` allgemein. | **Normtabellen durchsetzen**; `XML` ausserhalb seines Schlüsselwortkontexts erlauben. |
| **Namen mit mehr als 255 Zeichen** | [2.3 §2.2.2, S.23][ref-norm23-p23]; [2.4 §3.2.2, S.27][ref-norm24-p27]: Namen sind auf 255 Zeichen begrenzt. | Akzeptiert die geprüften 256-Zeichen-Namen. | Lehnt sie ab. **Normgrenze durchsetzen.** |
| **Struktur-/Listenindizes** | [2.3 §2.13, S.63–64][ref-norm23-p64]; [2.4 §3.13, S.82–83][ref-norm24-p83]: FIRST/LAST und numerische Auswahl gelten für geordnete Unterstrukturen; erster Index ist 1, die maximale Kardinalität darf nicht überschritten werden. | Akzeptiert etwa `BAG[FIRST]`, `LIST[0]` und `LIST[4]` bei maximal drei Elementen. | Lehnt diese Formen ab. **Typ und Grenzen prüfen.** |
| **Koordinatenachsen und grosse gültige Listenindizes** | [2.3 §2.13, S.63–64][ref-norm23-p64]; [2.4 §3.13, S.82–83][ref-norm24-p83]: Koordinatenachsen und LIST-Elemente werden einbasiert adressiert; die Norm legt keine zusätzliche Java-`int`-/`long`-Grenze fest. Es gelten die fachlichen Grenzen und unsere dokumentierten Ressourcenbudgets. | Scheitert in geprüften Fällen an Indexfehlern beziehungsweise Ganzzahlgrenzen. | Bindet die gültigen Zugriffe korrekt und rechnet exakt. **Referenzfehler nicht übernehmen.** |
| **EXISTENCE-Typkompatibilität** | [2.3 §2.12, S.59–60][ref-norm23-p59]; [2.4 §3.12, S.77–78][ref-norm24-p77] verlangen kompatible Attribute. [2.3 §2.6.4, S.33][ref-norm23-p33]; [2.4 §3.6.4, S.41–42][ref-norm24-p41] unterscheiden Strukturwerte und geordnete/ungeordnete Collections. Die genaue Collection-Kompatibilität ist unsere dokumentierte Auslegung der Wertsemantik; kein dort vollständig vorgegebener Algorithmus. | Akzeptiert mehrere inkompatible Kombinationen anhand grober Typklassen. | Prüft die tatsächlichen Typmerkmale. **Keine pauschale Gleichsetzung von BAG/LIST oder Strukturtypen.** |
| **`ANYOID` als Attribut und im passenden EXISTENCE-Vergleich** | [2.3 §2.8.9, S.45–46][ref-norm23-p46]; [2.4 §3.8.9, S.58–59][ref-norm24-p59] erlauben `ANYOID` als Attributwertebereich unter Mitführung der konkreten OID-Domäne; für den Vergleich zusätzlich [2.3 §2.12, S.59][ref-norm23-p59]; [2.4 §3.12, S.77][ref-norm24-p77]. | Lehnt die geprüften Fälle ab. | Akzeptiert sie unter Erhaltung der konkreten Trägerart. **Normzulassung umsetzen.** |
| **FORMAT-Präzision, Auffüllung und Grenzen** | [2.3 §§2.8.5–2.8.6, S.41–44][ref-norm23-p41]; [2.4 §§3.8.5–3.8.6, S.52–55][ref-norm24-p52]: Numerische Genauigkeit, lexikalische Auffüllung, Formatbestandteile und Vererbungsgrenzen müssen eingehalten werden. | Toleriert mehrere ungültige Formen; scheitert umgekehrt an einzelnen gültigen verschachtelten Formaten. | Prüft die Regeln exakt und unterstützt die gültigen Fälle. **Norm statt Referenzfehler.** |
| **`len(THIS)` bei numerischem THIS** | [2.3 §2.14, S.65–66][ref-norm23-p65]; [2.4 §3.14, S.86][ref-norm24-p86] sowie die jeweiligen normativen Anhänge A: Die Signatur von `len` verlangt TEXT. | Akzeptiert die geprüften numerischen Fälle. | Lehnt ab. **Signatur prüfen.** |
| **Konkretisierung von `NUMERIC [ANYUNIT]` auf eine lokale Metereinheit** | [2.3 §2.9.1, S.55][ref-norm23-p55]; [2.4 §3.9.1, S.72][ref-norm24-p72]: Alle Einheiten erben direkt oder indirekt von `ANYUNIT`, auch ohne explizite Deklaration. | Lehnt die geprüften Konkretisierungen ab. | Akzeptiert sie. **Normative Einheitenhierarchie umsetzen.** |
| **Eigene Linienform mit gewöhnlicher Struktur als Segmenttyp** | [2.3 §2.8.12.3, S.51][ref-norm23-p51]; [2.4 §3.8.12.3, S.66][ref-norm24-p66]: Die Linienstruktur muss eine Erweiterung von `LineSegment` sein. | Akzeptiert die geprüfte gewöhnliche Struktur. | Lehnt ab. **LineSegment-Vertrag durchsetzen.** |
| **Eigene Linienform ohne `CONTRACTED`, 2.4** | [2.4 §3.8.12.3, S.66][ref-norm24-p66] enthält im Unterschied zu [2.3 §2.8.12.3, S.51][ref-norm23-p51] keine Kontraktpflicht. Die Aufhebung der Kontrakte ist zusätzlich im [2.4 Vorwort – Erweiterungen gegenüber 2.3, S.8][ref-norm24-p8] dokumentiert. | Verlangt sie in der geprüften Gegenprobe weiterhin. | Akzeptiert. **2.4-Regel anwenden.** |
| **Fremde Signaturklasse bei Grafikregeln** | [2.3 §2.16, S.71][ref-norm23-p71]; [2.4 §3.16, S.92–93][ref-norm24-p92]: Die verantwortliche Signaturklasse ergibt sich aus Metaobjekt und verwendeten Parametern; sie muss mit der deklarierten OF-Klasse in einer passenden Vererbungskette liegen. | Akzeptiert auch die geprüfte fremde Klasse. | Akzeptiert passende Basis-/Unterklassen, lehnt die fremde Klasse ab. **Verantwortlichkeit fachlich prüfen.** |
| **Wiederholte Metadaten und mehrzeilige `!!@`-Strings** | Projektprofil: [Spezifikation Kap.04, Abschnitt E5 – Quell-Metaattribute und Dokumentation](../spec/04-quelltext-syntax-und-parser.md#e5-quell-metaattribute-und-dokumentation) und [L0B-3 – Umsetzung und erhaltene Fehlerpfade](implementation/L0B-3.md#umsetzung-und-erhaltene-fehlerpfade). Die Normkapitel [2.3 §2.2.8.1, S.25][ref-norm23-p25]; [2.4 §3.2.8.1, S.31][ref-norm24-p31] definieren Zeilenkommentare, aber keinen öffentlichen `fn.param`-Mehrfachwertvertrag. Unser ursprüngliches Kommentarprofil war enger. | Kein gleichwertiger öffentlicher Mehrfachwertvertrag wurde als Referenzgleichheit abgenommen. | **Vom Nutzer freigegebener Kompatibilitätsfix:** mehrere unterschiedliche `fn.param` an Funktionen und Stringfortsetzungen erlauben; sonstige Skalarkonflikte bleiben Fehler. |

Belege: [ALL OF](implementation/L2-2.md),
[lexikalisches Profil](../spec/04-quelltext-syntax-und-parser.md),
[Indizes und EXISTENCE](../evidence/stabilization/existence-types-and-indices.md),
[OID-/FORMAT-Regeln](../evidence/stabilization/enum-format-oid-collections.md),
[FORMAT-Präzision](../evidence/stabilization/format-precision-and-oid-binding.md),
[len-Signatur](../evidence/stabilization/imported-domain-types.md),
[Einheiten](../evidence/stabilization/k6-units.md),
[Linienformen](../evidence/stabilization/k4-line-forms.md),
[Grafiksignaturen](../evidence/stabilization/g2-signatures.md),
[Metadaten](implementation/L0B-3.md).

## Konkrete Folge für den Corpus

Die strikte Namensraumentscheidung führt dazu, dass zwei unveränderte
Corpusmodelle heute wegen `FileFormat` beziehungsweise `Gesuchsteller`
abgelehnt werden. Das ist dokumentiertes Sollverhalten; separat minimal
umbenannte Kontrollen bestehen. Die übrigen elf Corpusmodelle kompilieren
unverändert. [Nachweis und Original-/Kontrollpins](implementation/L4-1-profile-questions.md).

## Normquellen

- [INTERLIS 2.3 – Referenzhandbuch, Ausgabe 13. April 2006][norm23].
- [INTERLIS 2.4 – eCH-0031 V2.1.0, genehmigt am 24. April 2024][norm24].

Kapitel, Seiten und kurze Zitate wurden gegen das vorhandene
[gehashte Norminventar](../evidence/stabilization/g2-norm-inventory.json) und die
[archivierten Textextraktionen](../evidence/stabilization/norm-text/README.md)
geprüft. Die Extraktionen werden hier nicht verändert.

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
