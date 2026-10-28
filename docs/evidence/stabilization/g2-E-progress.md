# Selbständige Umsetzung E1–E5

Der Nutzer hat die gesamte Folge E freigegeben. Die Arbeit läuft in der
vereinbarten Reihenfolge E1–E5, ohne neue Zustimmung pro Lieferung. F/G und
P3-Verbraucher-/Plattform-/Performancegates bleiben davon getrennt offen.
Keine fehlende Implementierung wird als XFAIL klassifiziert.

## Aktueller Nachweisstand

27.10.2026: 688 JVM-/20 Wrappertests und 3249 portable Fälle je Laufzeit,
574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS. Massgebliches Manifest:
verification-2026-10-27-g2-E4-constraint-names.json. Verifizierte Folgeschritte:
Collectiontypen, Geometrie-Übersetzung, vordefinierte Namensbindung, skalierte
Typgrenzen und direkte Constraintnamen. Der aktuelle Fallkatalog trennt die
verbliebenen Ursachen. Nächster Schwerpunkt: numerische Unterteilungen anhand
effektiver Vorgänger und Einheitenbeziehungen. E/G2/P3 bleiben offen.

Die folgenden Abschnitte bilden die historische Arbeitschronologie. Ihre
früheren „Weiter“-Angaben sind durch den aktuellen Stand und nextAction im
Fallkatalog ersetzt; sie sind keine parallelen aktuellen Arbeitsaufträge.

## Historische E1-Lieferung – Modellkopfoptionen

SYN-004/013/015, META-003/006/011/014/015/017, SEM-005/010/014/017,
TEST-011/012. Regeln MODEL.header.NOINCREMENTALTRANSFER / CHARSET / XMLNS
und der vorgemerkte CONTRACTED-Übersetzungsfall 2.4 werden zusammen geprüft.

Umfang: strukturierte explizite Angaben mit Originalbereichen, richtiges
2.3/2.4-Profil, gebundener XML-Namensraum einschliesslich kanonischer
Übersetzungsidentität, CHARSET gegen gepinntes IANA-Register statt Hostcodecs.
Kein CHARSET bedeutet den normativen Anhang-D-Zeichenvorrat, nicht UTF-8.
Namespaces sind URI-Referenzen; relative Referenzen sind laut XML-Norm
veraltet, aber nicht allein deshalb ungültig. Reservierte/leere Namen sind
unzulässig. Default 2.4: http://www.interlis.ch/xtf/2.4/<Originalmodell>;
2.3: http://www.interlis.ch/INTERLIS2.3.

Abnahme: öffentliche unveränderliche Werte, Herkunft, Profile, ungültige
Angaben, Defaults, Übersetzungen und Konflikte, Imports, Quellenreihenfolge,
frisch/wiederverwendet sowie JVM-/portable/Browser-/Referenzprüfungen.
Keine Zeichencodierung, Transformation oder Netzwerkbeschaffung im Compiler.

Abgeschlossen geprüft: verification-2026-10-25-g2-E-headers.json.
511 JVM-Tests, 20 Wrappertests, 1872 portable Fälle auf TeaVM-JS/WasmGC,
Chromium/Firefox/WebKit/Safari und Web Image bestehen. Gesamtlauf Exitcode 0.
24 direkte Prozesspaare erreichen die eigenen Erwartungen; 13 stimmen roh
mit ili2c überein. Die übrigen elf betreffen neue gezielte Normkontrollen,
keine geänderten Suitefälle oder automatischen Ausnahmen.
Konformität unverändert 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS.
Ein erster Web-Image-WebKit-Timeout bleibt im Buildbericht erhalten.
Die unveränderte Grenze besteht nach isolierter Wiederholung und nach
Serialisierung der funktionalen Probeläufe im abschliessenden Gesamtlauf.
Dies ist keine Performance-Abnahme.

Die drei Modellkopfregeln und die 2.4-CONTRACTED-Übersetzungsregel sind
nachgewiesen. Die 238-zeilige Matrix hat 41 VERIFIED / 1 IMPLEMENTED / 196 OPEN.
Register: IANA character-sets-1.csv (258 Einträge), Pin und Herkunft stehen im
Manifest. APIs/Verbraucher/Spezifikation/Migration sind gemeinsam angepasst.

## Historische Fortsetzung nach Modellkopfoptionen

E1: Recovery-Klassifikation von MISSING_ATTRIBUTE_TYPE, zulässige Modellarten,
öffentliche deklarierte Modellsprache und Modifierregeln. Danach E2–E5 nach
den konkreten bestehenden Matrixregeln; keine neue Zustimmung je Lieferung.
Eine bereits grüne Annahmeprüfung ersetzt nicht den öffentlichen Modellnachweis.
Die übrigen Normabsätze/Produktionsalternativen sind weiterhin einzeln zu prüfen.
E/G2/P3 bleiben offen.

E1-Profilkontrollen: Der erste Gesamtlauf war rot (573 PASS / 19 XFAIL /
1 FAIL sowie Safari-WebDriver-Timeout). Die neue TYPE-Sperre für STRUCTURE
hat uniqueLocal_DefinedInStructure verändert. Sie wird vorerst entfernt,
kein XFAIL hinzugefügt. §2.5.1/§3.5.1 nennen Strukturen nicht ausdrücklich;
Referenz und etablierte Strukturtypen benötigen eine gesonderte Einordnung.
Die übrigen Inhaltsregeln sind davon getrennt geprüft. Safari wird mit
unveränderter Frist erneut ausgeführt. Rohstand: build/reports/stabilization/
g2-e1-profiles/failed-first. Dies ist keine erfolgreiche E1-Abnahme.

E1-Profillieferung abschliessend geprüft: 518 JVM-Tests, 20 Wrappertests,
1908 portable Fälle und echte Browser einschliesslich Safari, Web Image,
SDK/CLI/Diagramm bestehen. 36 zusätzliche Prozesspaare sind erhalten.
Konformität wieder 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS, Gesamtlauf Exit 0.
Manifest: verification-2026-10-25-g2-E1-profiles.json. TYPE/STRUCTURE bleibt offen. Weiter E2; E/G2/P3 offen.

## Laufend E2 – Einheiten, numerische Einschränkung und MANDATORY-Domänen

Die Einheitenquery verwendet gebundene Ableitungen/Komponenten statt nur IDs;
Referenzsysteme verwenden dieselbe Umrechnungsrelation. Konkrete Typen behalten
ihre Einheit bei Erweiterung. Numerische Grenzen und Präzision sowie
MANDATORY-Domänen und CLASS-Restriktionen sind ergänzt. Error-/Pending-Typen
bleiben in Queries unterscheidbar. Gezielte JVM-Prüfungen bestehen; der
vollständige E2-Lauf steht noch aus. 46 portable Prozessquellen sind ergänzt.
Weitere numerische Skalierungs-/Abstraktions-/Vererbungsregeln bleiben offen,
ebenso E3–E5. Es gibt keine neue Suiteausnahme.

E2-Teillieferung geprüft: 533 JVM-Tests / 20 Wrappertests / 1960 portable Fälle;
TeaVM, echte Browser einschliesslich Safari, Web Image, SDK/CLI/Diagramm grün.
52 eigene Prozesskontrollen bestehen, 40 stimmen roh mit ili2c überein.
Konformität unverändert 574 PASS / 19 XFAIL, Gesamtaufruf Exit 0.
Das erste E2-Ergebnis (572 PASS / 19 XFAIL / 2 FAIL) bleibt als Fehlernachweis
erhalten. 2.3 erhält nun die eigene Rundungsregel, 2.4 unveränderte Präzision.
Keine neue Ausnahme. Manifest: verification-2026-10-25-g2-E2-types.json.
Weiter E2-Text-/Skalierungs-/Abstraktionsregeln, danach E3–E5. E/G2/P3 offen.

## Laufend E2 – TEXT-Längen und Steuerzeichen

2.4 §3.8.1 zählt CCC=0-Zeichen nach NFD. Die bisherige Codepoint-Anzahl
verwirft kanonisch äquivalente zerlegte Literale fälschlich. Ein interner
93-zeiliger Helfer verwendet 250 gepinnte Gewichtungsintervalle und die
Hangul-Zerlegung, ohne Normalizer oder neue Abhängigkeit. Literaltext und
UTF-16-Quellbereiche bleiben erhalten. Unicode 17.0.0 ist explizit gepinnt;
100170 unabhängige Prüfungen gegen die expliziten NFD-Spalten der offiziellen
NormalizationTest-Datei bestehen auch mit dem generierten Java-Helfer.
Vorhandenes 2.3-Codepointverhalten bleibt erhalten; seine vollständige
Unicode-/Zeichenvorratabnahme wird dadurch nicht behauptet.

Zusätzlich werden deklarierte TEXT/MTEXT*0 als Quellfehler erkannt, und Literale
mit CR/LF/TAB sind MTEXT statt einer fälschlich passenden TEXT-Konstante.
JVM-/Modell-/Query-/Import-/Editkontrollen bestehen. 30 portable Quellen und
30 direkte Prozesspaare sind ergänzt (23 rohe Übereinstimmungen mit ili2c).
Vollständiger Abschlusslauf noch ausstehend; keine neue Suiteausnahme.

E2-TEXT abschliessend geprüft: 538 JVM-Tests / 20 Wrappertests / 1990 portable
Fälle, alle Runtimes und echte Browser einschliesslich Safari, Web Image, SDK/CLI/
Diagramm bestehen. 100170 unabhängige Java-Normalisierungskontrollen bestehen.
30 direkte Prozesspaare, davon 23 Übereinstimmungen; keine neue Ausnahme.
Konformität weiterhin 574 PASS / 19 XFAIL, Gesamtlauf Exit 0. Manifest: verification-2026-10-25-g2-E2-text.json.
Weiter strukturierte ANYSTRUCTURE-/RESTRICTION-Typen und übrige E2–E5-Regeln.

## Laufend E3 – Strukturwerte, Vererbung und Funktionskontext

ANYSTRUCTURE und Restriktionen sind strukturiert gebunden, ohne leere
Ersatzstruktur. Der bestehende Typ und seine Verbraucher sind in API 0.2
angepasst; ein kleiner konkreter Helfer vereint Bindung/Verfügbarkeit im
Compilerlauf. BAG→LIST, gemeinsame versus nur alternative Mitglieder,
konkrete verfügbare Wurzeln, Pflicht-/Abstraktionsregeln, Funktionskontext,
Übersetzungsidentitäten, Imports, Quellenreihenfolge und Edits sind geprüft.
Vererbungs-/Aliasabfragen schneiden lange Ketten nicht bei 16/32/64 ab.

Bisherige JVM-Prüfungen einschliesslich SDK/CLI/Diagramm/LSP bestehen. Der
alte positive AttributeModifier-Vertrag benötigte eine abstrakte Klasse und
ein abstraktes Thema; seine zuvor ungültige Form ist jetzt ein Negativfall.
Ein während der Lieferung entdeckter Nullzugriff bei Runtime-ANYSTRUCTURE
ist behoben und besitzt eine eigene Kontrolle. Fehlgeschlagener Erstvergleich
bleibt unter build/reports/stabilization/g2-e3-structures/failed-first erhalten.
52 neue portable Prozessquellen sind ergänzt; vollständiger Lauf steht aus.
E2-Restregeln und weitere E3–E5-Nachweise bleiben offen. Keine neue Ausnahme.

E3-Strukturwerte abschliessend geprüft: 549 JVM-Tests / 20 Wrappertests /
2042 portable Fälle, TeaVM-JS/WasmGC und echte Browser einschliesslich Safari,
Web Image, SDK/CLI/Diagramm bestehen. 52 eigene Prozesskontrollen bestehen;
32 rohe Übereinstimmungen mit ili2c. Konformität 574 PASS / 19 XFAIL, Exit 0.
Der erste Gesamtlauf bleibt rot archiviert: RESTRICTIONs machten im
roadsExgm2ien-Verwendungskontext FontSymbol zwei weitere NO-OID-Referenzen
auf Color/PolylineAttrs sichtbar. Alle bisherigen 49 Diagnosen bleiben
unverändert; die zusätzlichen zwei sind einzeln geprüft. Die unveränderte
OID-Kontrollfassung besteht. Der bestehende exakte Diagnosenachweis ist
gezielt ergänzt; keine neue Ausnahme, kein geänderter Suite-/Quellenpin.
Manifest: verification-2026-10-25-g2-E3-structures.json. E/G2/P3 bleiben offen.

Laufend E2 gemeinsame Erweiterungsregeln: Textdomänen und numerische
Collectionelemente verwenden dieselbe Erweiterung wie Attribute/Queries.
Koordinaten können Dimensionen ergänzen und erben Einheiten/Referenzen/Rotation
vor Ausdrücken; numerische Richtung/Referenz bleiben unverändert. Der Abgleich
des gepinnten 2.3-Handbuchs §2.8.1 bestätigt dieselbe NFD-Längenregel wie 2.4;
die verbliebene Codepoint-Zählung ist korrigiert. Frühere Berichte behalten den
damaligen Nachweis. Gezielte JVM-/Prozessprüfungen laufen; volle Abnahme offen.

E2-Erweiterungen abschliessend geprüft: 555 JVM-Tests / 20 Wrappertests /
2088 portable Fälle, alle Laufzeiten/Browser einschliesslich Safari, Web Image,
SDK/CLI/Diagramm bestehen. 46 Prozesspaare mit 35 rohen Übereinstimmungen.
Konformität 574 PASS / 19 XFAIL, Gesamtlauf Exit 0. Der erste rote Lauf
ist archiviert: MTEXT zu TEXT darf bei Erweiterung nicht gewechselt werden;
die gemeinsame Regel ist korrigiert, ohne neue Ausnahme oder geänderte Pins.
Die NFD-Länge gilt normativ bereits in 2.3 §2.8.1, nicht erst in 2.4.
Manifest: verification-2026-10-25-g2-E2-extensions.json. Weiter E4 und übrige Matrixregeln; E/G2/P3 offen.

Laufend E4: Operatorregeln sind mit TypeRelations vereinheitlicht. Der erste
breite JVM-Lauf deckte einen vorhandenen Parserfehler auf: <> war LT statt
NEQ. Der unveränderte Originalfall view_ObjectPathWithRolename ist korrekt;
beide Vergleiche sind jetzt explizit als NEQ geprüft. Keine neue Normausnahme.
ENUMVAL/ENUMTREEVAL und ALL OF sind öffentlich erhalten; konkrete Enum- und
FORMAT-Konstanten erhalten Typkontext, Inline-Enumwerte auch Symbolidentitäten.
Verwendete Standardfunktionen erhalten öffentliche Signaturen und gebundene
Aufrufnamen. Enumfunktionen und vier 2.4-Geometriefunktionen sind ergänzt.
Die bereits vorhandene Dateiendeprüfung im Syntaxdienst erhält zusätzliche
Gegenfälle (zweiter Kopf, weitere Tokens, Kommentarende). Die zunächst
vermutete fehlende EOF-Prüfung war kein Compilerfehler; der neue Test hatte
einen unzulässigen zweiten Kopf. Kein zusätzlicher Grammatikprüfpunkt; ein
unerreichbarer doppelter Prüfzweig wird entfernt, ILI-SYN-0004 bleibt erhalten.
Geometrie-/Enum-/Query-/Import-/Edit-/Übersetzungsprüfungen bestehen gezielt.
106 zusätzliche portable Quellen und zwei reale Querysitzungen sind vorbereitet;
volle Laufzeit-/Browser-/Konformitätsabnahme steht noch aus. convertUnit und
areAreas-Kontexte sowie weitere E1–E5-Regeln bleiben offen. E/G2/P3 offen.

Erster voller E4-Lauf: 572 PASS / 19 XFAIL / 2 FAIL. Die gemeinsamen
Operatorregeln hatten OID-Gleichheit noch nicht übernommen; sie ist ergänzt.
Die versuchsweise Zulassung linearer Vergleiche auf CIRCULAR wird verworfen:
zyklische Reihenfolge ist keine lineare ORDERED-Ordnung. Der vorhandene
Original-Gegenfall less_enum_circular_fail bleibt regulär abgelehnt. Beide
Fälle erhalten gemeinsame JVM-/Query-/portable Kontrollen; keine neue Ausnahme.
Rohstand: build/reports/stabilization/g2-e4-enums-operators/failed-first.
Aktuell 571 JVM-Tests erfolgreich; 106 Quellen plus zwei Querysitzungen werden
jetzt vollständig wiederholt. Der erste Lauf ist keine E4-Abnahme.

E4 Enum-/Operator-/Geometriesignaturen vollständig als Teillieferung geprüft:
571 JVM-Tests / 20 Wrappertests / 2196 portable Fälle je Laufzeit. Die zwei
zusätzlichen Querysitzungen pro Laufzeit prüfen echte Enum-Navigations- und
Funktionsidentitäten. TeaVM-JS/WasmGC, Chromium/Firefox/WebKit/Safari, Web Image,
SDK/CLI/Diagramm bestehen. 106 direkte Prozesspaare, 84 rohe Übereinstimmungen.
Konformität 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS; Gesamtlauf Exit 0.
Manifest: verification-2026-10-25-g2-E4-enums-operators.json. Keine neue Ausnahme/keine veränderten Suitepins.
Weiter convertUnit, ATTRIBUTE-/areAreas-Kontexte und übrige E-Regeln.

Laufend E4 Pfad-/Funktionskontexte: Der Fragmentparser verwendet jetzt eine
Abbildung lokaler ANTLR-Codepointgrenzen auf originale UTF-16-Bereiche. Er
verliert keine Lexerfehler mehr; EOF-Diagnosen bleiben im Fragment. Ein
gemeinsamer Stringdecoder ersetzt die beiden bisherigen Decoder und erhält
Escape-Grenzen für spätere gebundene Stringpfade. Vier neue JVM-Verträge für
Unicodepräfixe, CRLF/bare CR, Lexerfehler und Fragment-EOF bestehen gezielt.
Die areAreas-/convertUnit-Bindung und die dazugehörige volle Abnahme stehen
noch aus; letzter vollständiger Nachweis bleibt E4-enums-operators mit
571 JVM-/2196 portablen Fällen. Keine Gate-Schliessung aus diesen Teiltests.

E4 Funktionskontexte laufend: Zahlenkonstanten erhalten gebundene Einheiten und
DeclaredOrigin. PI/LNBASE bleiben symbolisch, ohne gerundete exakte Ersatzwerte.
Alle bisher gesperrten Builtins und beide WithoutGaps-Varianten sind nun typisiert
implementiert. ATTRIBUTE OF @ verwendet die tatsächlichen Aufrufargumente;
Stringpfade verwenden Fragmentparser und gemeinsamen Pfadbinder. Verwendete
Pfade, Syntaxschritte, Abhängigkeiten und Werttypen bleiben öffentlich erhalten.
Schemaauswahl, Segmentkontextualisierung, Views und Übersetzungen traversieren
die zusätzlichen Bindungen. Ein leerer Fragmentpfad verursacht keinen internen
Fehler mehr. Typabfragen unterscheiden einen benötigten Aufrufkontext von
falschen Werttypen. convertUnit prüft Quelle/Ziel im Verwendungskontext ohne
Wertberechnung. Gezielte JVM-/Import-/Query-/Edit-/Übersetzungsprüfungen bestehen;
check und installDist bestehen. 90 zusätzliche portable Quellen und zwei
Querysitzungen pro Laufzeit sind vorbereitet; vollständige Läufe folgen.
E/G2/P3 bleiben offen.

Erster voller Funktionslauf besteht mit 594 JVM-/2288 portablen Fällen und
574 PASS / 19 XFAIL. Die zusätzlichen Prozesspaare haben aber drei positive
Konstanten-Fixtures mit einem nicht als ABSTRACT deklarierten NUMERIC-Attribut
aufgedeckt. Dessen Akzeptanz ist eine zusätzliche E2-/E3-Abstraktionslücke,
keine begründete Referenzabweichung. Positive Kontrollen erhalten jetzt einen
konkreten Bereich samt Einheit; die Lücke ist für die unmittelbar folgende
Abstraktionslieferung reproduziert. Rohstand bleibt unter
build/reports/stabilization/g2-e4-function-contexts/first-controls erhalten.
Keine neue Ausnahme. Endgültige Funktionsabnahme wird wiederholt.

E4-Funktionskontexte als Teillieferung vollständig geprüft: 594 JVM-Tests,
20 Wrappertests, 2288 portable Fälle je Laufzeit. TeaVM-JS/WasmGC, Chromium,
Firefox, WebKit, echter Safari und Web Image bestehen; SDK/CLI/Diagramm ebenfalls.
90 Prozesspaare liefern alle erwarteten eigenen Resultate, 51 rohe ili2c-
Übereinstimmungen. Konformität 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS;
Gesamtlauf Exit 0 in 3m37s. Suitepins und Ausnahmen unverändert. Zwei weitere
Querysitzungen pro Laufzeit prüfen tatsächliche Attribute-/Escape-Pfad- und
Builtinidentitäten; vier zusätzliche Sitzungen insgesamt. Manifest: verification-2026-10-25-g2-E4-function-contexts.json.
Weiter allgemeine UNDEFINED-/Argumentregeln, E2 FORMAT/Abstraktion und übrige
E1/E3/E5-Regeln. Implementierte Builtins sind keine vollständige Normabdeckung.
E/G2/P3 bleiben offen.

E4-Definiertheit laufend: Gemeinsame Typregeln behandeln UNDEFINED und leere
TEXT-Konstanten als undefinierte Werte, ohne Fehler-/Pending-Ziele zu verdecken.
Funktionssyntax und öffentliche Signaturen erhalten direktes und geerbtes
MANDATORY für Parameter und Resultate. Explizit undefinierte Argumente,
Attributableitungen und Signaturzuweisungen werden bei Pflichtigkeit abgelehnt.
Öffentliche Zuweisungsabfragen erhalten Pflichtdomänen über Aliasreferenzen.
UNDEFINED ist kein Typ für eine Erweiterung. UndefinedExpression trägt die
richtige Sprachversion. Die interne complete-Flag-/Sperrdiagnostik für Builtins
entfällt, nachdem alle registrierten Funktionsfamilien typisiert sind; die
übrige Normmatrix bleibt offen. 605 JVM-Tests bestehen mit Imports,
Quellenreihenfolge, Edit-/Wiederverwendung, Queries und Übersetzungen.
84 zusätzliche portable Quellen sind vorbereitet; vollständige Läufe folgen.
Das zwischenzeitliche Alias-Fixture Alias=Required war selbst syntaktisch
unzulässig und wurde durch die legale geerbte MANDATORY-Form ersetzt.
Hierfür wird keine Compilerregel erfunden.

E4-Definiertheit vollständig als Teillieferung geprüft: 605 JVM-/20 Wrappertests,
2372 portable Fälle je Laufzeit, TeaVM-JS/WasmGC, Chromium/Firefox/WebKit/Safari,
Web Image, SDK/CLI/Diagramm bestehen. Gesamtaufruf Exit 0 in 3m35s.
84 Prozesspaare, alle eigenen Erwartungen erfüllt, 60 rohe Übereinstimmungen.
Konformität unverändert 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS. Keine neue
Ausnahme oder geänderte Pins. Manifest: verification-2026-10-25-g2-E4-undefined.json.
Weiter E2 FORMAT/Abstraktion sowie die noch offene leere SurfaceBag-Wurzelauswahl,
danach übrige E1-/E3-/E5-Regeln. E/G2/P3 bleiben offen.

Laufend E2 FORMAT-Relationen (META-006/014, SEM-005/010/014): Die bestehende
Auswertung gebundener FORMAT-Werte liegt jetzt gemeinsam in interlis-model;
Compiler und öffentliche Queries verwenden identische Präfix-/Bereichsregeln.
Komponentengrenzen, verschachtelte und geerbte Grenzen werden berücksichtigt.
Die pauschale FORMAT-Freigabe bei Attributüberschreibungen ist entfernt.
Ungültige Bindungen liefern ErrorType mit wirklichen Diagnosen. Inline-Overrides
binden ihre geerbte Attributidentität vor dem Typaufbau. Gezielte JVM-/Query-/
Import-/Edit-/Wiederverwendungstests bestehen. Auch die leere SurfaceBag-Konstante
verwendet jetzt die gemeinsame UNDEFINED-Wurzelauswahl, mit falschem Eigentümer
als Gegenfall. Vollständige Konformitäts-/portable-/Browserabnahme folgt.

E2 FORMAT vollständig als Teillieferung nachgewiesen: 614 JVM-/20 Wrappertests,
2415 portable Fälle je Laufzeit, TeaVM-JS/WasmGC, Chromium/Firefox/WebKit/Safari,
Web Image, SDK/CLI/Diagramm bestehen. Gesamtaufruf Exit 0 in 3m36s. 43 weitere
Prozesspaare erfüllen die eigenen Erwartungen; 33 rohe ili2c-Übereinstimmungen.
Konformität 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS. Keine neue Ausnahme oder
veränderte Pins. Manifest: verification-2026-10-25-g2-E2-format-relations.json.
Weiter numerische/Koordinaten-Abstraktion und explizite Einheiten-/Zielbindungen.
Die bereits vorhandene ABSTRACT-Klasse-/Topic-Prüfung wird als bestehend erfasst;
weitere Deklarationsfamilien bleiben zu prüfen. E/G2/P3 offen.

Laufend E2 numerische Vollständigkeit/Zielbindung (META-003/006/014,
SEM-005/010/014): Unvollständige konkrete Numeric-/Coordinate-Deklarationen
werden abgelehnt; eigene ABSTRACT-Attribute, formale NUMERIC-Signaturen und
legale generische 2.4-Koordinaten bleiben unterscheidbar. Abstrakte Einheiten
sind bei konkreten Grenzen auch nach Vererbung unzulässig. Nicht gebundene
Einheiten-/Referenzziele erhalten ErrorType mit vorhandenen Diagnose-IDs.
Der erste breite Test war bei erschlossenen View-Ergebnissen und LineCoord-
Platzhaltern eigener Segmente zu streng; diese Kontexte werden erhalten und
von expliziten unvollständigen Deklarationen getrennt. Bestehende View-/
Inspektionsverträge sowie acht neue JVM-Verträge bestehen gezielt. 62 neue
portable Quellen sind vorbereitet; vollständige Abnahme folgt.

E2 numerische Vollständigkeit vollständig als Teillieferung geprüft: 622 JVM-/
20 Wrappertests, 2477 portable Fälle pro Laufzeit, TeaVM-JS/WasmGC,
Chromium/Firefox/WebKit/Safari, Web Image, SDK/CLI/Diagramm bestehen.
Gesamtaufruf Exit 0 in 3m25s. Alle 62 zusätzlichen Prozessfälle stimmen mit
ili2c 5.6.9 überein. Konformität 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS.
Keine neue Ausnahme oder veränderten Pins. Manifest:
verification-2026-10-25-g2-E2-numeric-abstraction.json.
Weiter versionsgebundene vordefinierte Identitäten/Wortprofile in E1 sowie
übrige E2-/E3-/E4-/E5-Regeln. E/G2/P3 bleiben offen.

Laufend E1 Wort-/Primitivprofile (SYN-002/004/013, META-015): Die vollständigen
reservierten Tabellen mit 154 (2.3) und 168 (2.4) Wörtern sind separat gepinnt.
Quelle, Fragment und Rename verwenden dieselbe lexikalische Klassifikation;
neue 2.4-Wörter bleiben in 2.3 legale Namen. XML bleibt kontextabhängig in
BLACKBOX XML. REFSYS als Koordinatenangabe ist 2.4; das frühere positive
2.3-Fixture war fachlich falsch und wird durch 2.4 plus einen Gegenfall ersetzt.
Generierte Boolean-Typen gehören zur Sprachversion ihres Besitzers, auch in
gemischten Closures. Ein unbenutzter Text-Primitivpfad entfällt.
Die Lexeränderung deckte ein veraltetes generiertes Parser-Tokenvokabular auf;
der ANTLR-Task räumt jetzt nur sein eigenes Ausgabeziel vor der Regeneration.
Eine erzwungene Wiederholung erzeugt identische Lexer-/Parser-/Vokabularhashes.
Tabellengegenfälle deckten zudem Recovery-Abstürze bei fehlenden Funktions-,
Grafik-, Metadaten-/View-Namen und leeren Namensknoten auf; diese sind gezielt
korrigiert. 629 JVM-Tests bestehen. 356 zusätzliche portable Quellen sind
vorbereitet; vollständige Konformitäts-/Laufzeitabnahme steht noch aus.

E1 Wort-/Primitivprofile vollständig als Teillieferung geprüft: 629 JVM-/
20 Wrappertests, 2833 portable Fälle je Laufzeit, TeaVM-JS/WasmGC,
Chromium/Firefox/WebKit/Safari, Web Image, SDK/CLI/Diagramm bestehen.
Gesamtaufruf Exit 0 in 3m39s. 356 weitere Prozesspaare erfüllen alle eigenen
Erwartungen; 292 rohe ili2c-Übereinstimmungen. Die Unterschiede betreffen
insbesondere normativ weiterhin reservierte Legacywörter; keine neue Ausnahme.
Konformität 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS, Pins unverändert.
Manifest: verification-2026-10-25-g2-E1-lexical-profiles.json. Lexer-/Parser-
Wiederholungshashes und die gepinnten Worttabellen sind zusätzlich belegt.
Weiter E5 Quell-Metaattribute/Dokumentation und deklarationsgebundener CRS-
Vergleich sowie übrige E1-/E2-/E3-/E4-/E5-Regeln. E/G2/P3 bleiben offen.

Laufend E5 Quell-Metadaten (META-006/010/014/017, SEM-010/014/017):
!!@-Zuweisungen und /**-Dokumentation werden einmal aus Kommentartokens
strukturiert gelesen, an Deklarationen gebunden und mit exakten Bereichen
veröffentlicht. Gruppeneinleitungen werden korrekt übergangen, Enumwerte und
Constraints behalten eigene Angaben. Identische Doppelangaben sind erlaubt,
Konflikte und fehlerhafte Zuweisungen sind Quellfehler. FunctionSymbol und
FunctionDeclarationSyntax erhalten die bisher ausgelassene Erklärung; Hover
zeigt Dokumentation und Erklärung. Übersetzungen vergleichen CRS-Metaattribute
an den zugeordneten Koordinatendeklarationen; die globale Volltextsuche und zwei
unbenutzte Texthelfer entfallen. Text in Strings/gewöhnlichen Kommentaren/
Erklärungen erzeugt keine CRS-Semantik. Vertauschte CRS-Zuordnungen werden
gezielt am jeweiligen Ziel abgelehnt.
639 JVM-Tests bestehen mit öffentlichen Modell-/Query-, Herkunfts-, Import-,
Edit-/Wiederverwendungs- und Übersetzungsfällen. 50 portable Quellen plus zwei
Hover-Sitzungen pro Laufzeit sind vorbereitet; vollständige Abnahme läuft.
Externe Overlays bleiben F; E/G2/P3 offen.

E5 Quell-Metadaten als Teillieferung geprüft: 640 JVM-/20 Wrappertests,
2889 portable Fälle je Laufzeit einschliesslich sechs Query-/Hover-Sitzungen.
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit/Safari, Web Image und SDK/CLI/Diagramm
bestehen. Konformität 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS.
54 Prozesspaare erfüllen die eigenen Erwartungen, 28 rohe Referenzübereinstimmungen.
Der erste vollständige Lauf deckte die fehlende Prüfung der Erläuterungspräsenz
sowie eine WasmGC-Division durch null beim Zugriff auf die leere Metadaten-Map
auf. Beide Ursachen sind korrigiert und der vollständige Lauf regulär wiederholt.
Keine neue Ausnahme oder geänderte Quellenpins. Manifest: verification-2026-10-25-g2-E5-source-metadata.json.
Weiter: gebundener struktureller Übersetzungsvergleich ohne erneute Volltext-
Tokenisierung/globale Namensmap sowie übrige E-Regeln. E/G2/P3 bleiben offen.

Laufend E5 strukturierter Übersetzungsvergleich (SEM-010/014/017, META-006/010/014):
Volltext-Lexing, globale Namensmap und tiefenbegrenzte Typformstrings entfernt.
Alle Korrespondenzen werden einmal anhand gebundener Symbole verglichen, auch
Domain-Constraints. Typvarianten behalten ihre Eigenschaften und tatsächlichen
FORMAT-/Enum-/Referenzziele; Ausdrücke und Pfade werden strukturell verglichen.
Neue Kontrollen prüfen 2.3-Kontextnamen, dokumentarische Parameternamen,
Genauigkeit gegenüber Zahlenschreibweise, Enumtopologie/-konstanten, Collection-
Kardinalität/-art, FORMAT-Ziele und Edit-/Wiederverwendungsfolgen. Benannte
Constraints besitzen ihren tatsächlichen Namensbereich. 647 JVM-Tests und
`check` bestehen. 52 zusätzliche portable Quellen sind vorbereitet; vollständiger
Lauf und Prozessvergleich laufen. Keine neue Ausnahme. E/G2/P3 offen.

Der erste vollständige E5-Strukturlauf endete mit 570 PASS / 19 XFAIL / 4 FAIL.
Die vier ursprünglichen negativen Constraintfälle zeigten tatsächlich ausgelassene
Nutzdaten: Plausibilitätsoperator/-prozent und SET-WHERE wurden bisher durch
Platzhalter ersetzt. Diese Inhalte sind jetzt strukturiert erfasst und gebunden;
SET-BASKET und konzeptionell GLOBAL bleiben unterscheidbar. Keine neue Ausnahme.
Recovery leerer Bedingungen ist als Quellfehler klassifiziert; numerische
Ziffern-/Exponentenbudgets werden vor DecimalLiteral-Aufbau durchgesetzt und
als RESOURCE_LIMIT weitergegeben. Die portablen Kontrollen prüfen danach
die Weiterverwendbarkeit der Sitzung.
Unabhängige E4-Funde bleiben separat: SET-Body-Attributverbot und 2.4-OBJECTS-
Funktionspflicht, eindeutige Constraintnamen; keine automatische Normausnahme.
Zwei dazu betroffene bisherige Referenz-PASS sind mit Pins im Fallkatalog
aufgenommen. Die vollständige Korrekturabnahme steht noch aus.

E5 gebundene Übersetzungen als Teillieferung geprüft: 652 JVM-/20 Wrappertests,
2977 portable Fälle je Laufzeit, TeaVM-JS/WasmGC, Chromium/Firefox/WebKit/Safari,
Web Image, SDK/CLI/Diagramm bestehen. 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS.
82 weitere Prozesspaare erfüllen alle eigenen Erwartungen; 77 rohe ili2c-
Übereinstimmungen. Bestehende Enum- und Parameterregressionen wurden vollständig
erhalten. Kein geänderter Suitepin und keine neue Ausnahme. Manifest: verification-2026-10-26-g2-E5-bound-translations.json.
Weiter E1 vordefinierte Identitäten/Namensauflösung und übrige E-Regeln.
E/G2/P3 bleiben offen.

E1 kanonische OID-Domänen als Teillieferung geprüft: 658 JVM-/20 Wrappertests,
3007 portable Fälle je Laufzeit mit acht Query-/sechs Budget-Recovery-Sitzungen.
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit/Safari, Web Image und SDK/CLI/Diagramm
bestehen. Konformität 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS.
28 direkte Prozesspaare erfüllen die eigenen Erwartungen; 23 rohe Referenz-
übereinstimmungen. Die fünf rohen Differenzen (ANYOID-Attribute in 2.3/2.4, fehlendes NOOID
in ili2c 2.4 und zwei I32OID-/UUIDOID-Argumentfälle) bleiben mit vollständigen
Quellen/Prozessausgaben sichtbar. Allgemeine OID-Typrelationen bleiben E2.
Die ursprüngliche Ablehnung identischer UUIDOIDs über Importgrenzen ist behoben.
Testvorlagen verwenden ausschliesslich gültige Quellsyntax; Aliasauflösung wird
über die tatsächlichen DomainReferenceType-Attribute geprüft.
Manifest: verification-2026-10-26-g2-E1-oid-identities.json. Weiter E2 benannte Collectiontypen und E5 Geometriekontexte/
Toleranzen. E/G2/P3 bleiben offen.

Korrektur der Prozessbilanz E5-bound-translations vom 26.10.: Die 77 dort
gezählten gleichen Exitcodes enthalten zwei interne ili2c-Abbrüche bei PI als
Prozentwert. Fachlich sind es 75 Übereinstimmungen und vier interne
Referenzfehler (die beiden übrigen betreffen benannte Constraints). Historische
Quellen/Ausgaben und Manifeste bleiben erhalten; additive Nachrechnung in
reference-summary-corrections-2026-10-27.json. Interne Fehler gelten nicht als
fachliche Ablehnung oder Übereinstimmung. Die gefrorene Suite bleibt unverändert.

E2 benannte Collection-Elementtypen geprüft: 662 JVM-/20 Wrappertests,
3041 portable Fälle je Laufzeit, zehn Query-/sechs Budget-Recovery-Sitzungen.
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit/Safari, Web Image und SDK/CLI/Diagramm
bestehen. Konformität 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS.
32 Prozesspaare erfüllen die eigenen Erwartungen; 29 fachliche Referenzübereinstimmungen,
3 interne Referenzfehler (keine fachlichen Ablehnungsnachweise).
COLLECTION_NAMED_DOMAIN_24 geschlossen. Keine neue API/Klasse oder Ausnahme.
Eine OID-MANDATORY-Erweiterung bleibt separat reproduziert offen; die gültigen
Collection-Nachweise verwenden eigene OID-Domänen und vererbte TEXT-Domänen.
Manifest: verification-2026-10-27-g2-E2-named-collections.json. Weiter E5 Geometriekontexte/Toleranzen und übrige E1–E5.
E/G2/P3 bleiben offen.

Laufend E5 Geometrie-Übersetzung: Der lauflokale Kontextualisierer liefert seine
exakten Kopierbeziehungen; der Übersetzungsvergleich verbindet passende
Geometriekontexte konfliktgeprüft und veröffentlicht die abschliessenden Links.
Explizite Toleranzen vergleichen exakte Werte. Acht neue JVM-Verträge und
check bestehen; 36 portable Quellen/Prozesspaare sind ergänzt. Der volle
Laufzeit-/Browser-/Konformitätslauf steht noch aus. Ein zusätzliches JVM-Test-
assert verlangte irrtümlich den Kurznamen LineCoord; es prüft jetzt die tatsächliche
PredefinedOrigin des unveränderten kanonischen Ziels. Kein Produktionsfehler
oder geänderte Erwartung eines Norm-/Referenzfalls. E/G2/P3 bleiben offen.

E5 Geometriekontexte/Toleranzwert abschliessend geprüft: 670 JVM-/20 Wrappertests,
3077 portable Fälle je Laufzeit, zehn Query-/sechs Budget-Recovery-Sitzungen.
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit/Safari, Web Image und SDK/CLI/Diagramm
bestehen. Konformität 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS.
36 Prozesspaare erfüllen die eigenen Erwartungen; 20 fachliche Referenzübereinstimmungen,
0 interne Referenzfehler. Unterschiede stehen mit vollständigen Quellen/Ausgaben
im Prozessnachweis; es entsteht keine neue Ausnahme. Die früheren Ablehnungen
identischer geometrischer Inspektionen und gleichwertiger Toleranzschreibweisen
sind behoben. Modelllinks erhalten die geprüften Kontextkopien vollständig. Die Abnahme verwendet gewöhnliche TEXT-Zusatzattribute;
die ursprünglichen zusätzlichen LineCoord-Attribute bleiben separat zur Normprüfung
offen (LINECOORD_ADDITIONAL_ATTRIBUTE_CONTEXT). Der erste grüne Lauf mit diesen
weiteren Attributen bleibt historisch erhalten und gilt nicht als deren Normabnahme.
Manifest: verification-2026-10-27-g2-E5-geometry-context.json. Weiter E1 Namensbindung und E2 Bereichsmantissen; alle
übrigen E1–E5-Regeln, G2 und P3 bleiben offen.

E1 gemeinsame vordefinierte Namensbindung geprüft: 677 JVM-/20 Wrappertests,
3146 portable Fälle je Laufzeit, zwölf Query-/sechs Budget-Recovery-Sitzungen.
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit/Safari, Web Image und SDK/CLI/Diagramm
bestehen. Konformität 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS.
67 Prozesspaare erfüllen eigene und Referenzerwartungen, keine internen Fehler.
Die fehlenden UNQUALIFIED-Bindungen und das falsche Builtin-Fallback bei zwei
konkurrierenden Userimports sind behoben. Keine neue Klasse/API oder Ausnahme.
Der einzelne User-/INTERLIS-Importkonflikt behält sein bisheriges Verhalten und
bleibt ausdrücklich normativ offen; der strengere Vorentwurf wurde nicht übernommen.
Manifest: verification-2026-10-27-g2-E1-predefined-names.json. Weiter E2 Bereichsmantissen und übrige E1–E5. E/G2/P3 offen.

E2 skalierte Typgrenzen geprüft: 683 JVM-/20 Wrappertests, 3224 portable Fälle
je Laufzeit, zwölf Query-/sechs Budget-/zwei Syntaxbereich-Recovery-Sitzungen.
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit/Safari, Web Image und SDK/CLI/Diagramm
bestehen. Konformität 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS.
76 Prozesspaare erfüllen die eigenen Erwartungen, 74 stimmen mit ili2c überein.
Zwei bestehende allgemeine Konstanten-/Faktorabweichungen bleiben separat offen.
Die Reproduktion NUMERIC_SCALED_BOUND_FORM ist geschlossen; Nullmantissen bleiben
ausdrücklich gültig. Paarexponenten und allgemeines Konstantenprofil werden nicht
als mitabgenommen behauptet. Keine neue API/Klasse oder Ausnahme.
Manifest: verification-2026-10-27-g2-E2-scaled-boundaries.json. Weiter E4 Constraintnamen, numerische Unterteilungen und
übrige E1–E5-Regeln; Normmatrix/G2/P3 bleiben offen.

E4 direkte Constraintnamen geprüft: 688 JVM-/20 Wrappertests, 3249 portable
Fälle je Laufzeit, 13 Query-/sechs Budget-/zwei Syntaxbereich-Recovery-Sitzungen.
TeaVM-JS/WasmGC, Chromium/Firefox/WebKit/Safari, Web Image und SDK/CLI/Diagramm
bestehen. Konformität 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS.
24 eigene Prozess-Erwartungen erfüllt, 14 fachliche Referenzübereinstimmungen.
Zehn doppelte Namenskontrollen akzeptiert ili2c; die direkte 2.4-Eindeutigkeitsregel
§3.12 S.77 wird im eigenen Compiler durchgesetzt. Keine neue Suiteausnahme.
Externe Constraints erhalten ihren tatsächlichen Namenbereich für Queries.
Die vererbten Namensräume bleiben ausdrücklich offen. Ein fehlender Java-Import
im ersten Zielbuild ist korrigiert; der vollständige Wiederholungslauf besteht.
Manifest: verification-2026-10-27-g2-E4-constraint-names.json. Weiter E2 Unterteilungen und verbleibende E1–E5-Regeln.
Normmatrix/G2/P3 bleiben offen.
