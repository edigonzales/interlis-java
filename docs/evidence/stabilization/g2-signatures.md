# G2 D: Verantwortliche Signaturklasse

Stand: 25. Oktober 2026. WP-24/25, META-003/006/010/014,
SEM-005/010/014/017 und TEST-011/012. **D ist für den hier beschriebenen
statischen Compilerumfang nachgewiesen. G2 und P3 bleiben offen.**

## Katalogfall und ursprüngliche Quellen

`GRAPHIC_RESPONSIBLE_SIGNATURE_CLASS` war ein zusätzlicher G2-Sprachfund ausserhalb
der eingefrorenen 593 Suitefälle. Die ursprüngliche Probe wählt ein Sign-Metaobjekt
der Klasse S, deklariert `OF Derived` und setzt A aus S sowie B aus Derived.
Die bisherige implizite Typprüfung verlangte unmittelbar ein Metaobjekt der
Klasse Derived und lehnte die passende Basissignatur mit
`GRAPHIC_METAOBJECT_TYPE` ab.

Die sechs unveränderten ursprünglichen Quellen (2.3/2.4, jeweils S, Derived und
Other) liegen unter `testdata/conformance/g2-signatures/`. Die Hashes stimmen
mit `openSignatureResponsibility` im historischen K5-Bericht überein; der
JVM-Vertrag prüft sie. Die S- und Derived-Fassungen werden jetzt akzeptiert.
Die unvereinbare Other-Fassung bleibt ein regulärer Quellfehler. ili2c 5.6.9
akzeptiert alle sechs Fassungen; diese Referenzbeobachtung wird getrennt von
der eigenen fachlichen Erwartung geführt. Es entsteht keine neue Suiteausnahme.

## Fachregel und einfache Umsetzung

Grundlage sind [2.3 §2.16, S. 71](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=71)
und [2.4 §3.16, S. 92–93](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=92).
Die Dokumentstände und Inhaltspins bleiben im Manifest erhalten.

Ausgangspunkt der Verantwortung ist die Klasse des gewählten Sign-Metaobjekts.
Tatsächlich verwendete Parameter können eine passende Unterklasse verantwortlich
machen, sofern die deklarierte OF-Klasse diese Klasse oder deren Erweiterung ist.
Die beteiligten Klassen müssen in einer verträglichen Vererbungskette liegen.
Eine bloss deklarierte, unbenutzte Unterklasse führt ihre zusätzlichen
Pflichtparameter nicht ein. Die tatsächliche Metaobjektklasse kann dagegen
zusätzliche Pflichtparameter verlangen.

Die Änderung bleibt im vorhandenen `GraphicBuilder`. Nach vollständiger
Parameter-/Override-Bindung werden die Verantwortlichkeit je gebundener
Sign-Alternative, die statischen Pflichtparameter und gegebenenfalls engere
Parametertypen geprüft. Originalwerte und Basisdeskriptoren werden nicht mutiert.
Ein unbedingter neuer Wert ersetzt frühere Werte desselben gebundenen Parameters
für den jeweiligen Prüfkontext; die öffentliche Basisregel bleibt erhalten.

Unbedingte Zuweisungen und einzelne bedingte Klauseln bilden statische
Zuweisungskontexte in Quellreihenfolge. Partielle bedingte Erweiterungen behalten
frühere bedingte Sign-Auswahlen. So löst ein bereits in derselben bedingten
Erweiterung ersetzter geerbter Wert keinen falschen Fehler gegen den engeren
Parametertyp aus. Es wird keine exponentielle Belegung aller Bedingungen erzeugt.
Der bestehende RunContext wird auch in den Kontextschleifen geprüft.

Die Pflichtparameterprüfung prüft statische Zuweisungsvorkommen. Sie beweist
keine Laufzeit-Definiertheit unter beliebigen Daten/Bedingungen. Bedingungen,
ACCORDING-Ausdrücke und Zeichenregeln werden nicht ausgewertet; Boolean-/Enum-
Bedingungen werden nicht gelöst. Ihre Reihenfolge, Selektoren, Bereiche und
gebundenen Ziele bleiben im Modell erhalten.

Explizite `METAOBJECT OF`-Zielbeschränkungen behalten ihre eigene Typprüfung,
auch bei geerbten Werten und einer erst in der Unterklasse verschärften
Beschränkung. METAOBJECT ohne OF behält den bestehenden Klassenbezug zum verantwortlichen
Zeichenregelkontext. Andere Metaobjektparameter werden nicht als der
vordefinierte Sign-Parameter behandelt und nicht unbeabsichtigt freigegeben.

Es gibt keine neuen öffentlichen Klassen, Konstruktoren oder Einstiegspunkte.
`DrawingRuleSymbol.signatureClass` bleibt die deklarierte OF-Klasse; ein einziges
Verantwortlichkeitsfeld an der Regel wäre bei mehreren Alternativen irreführend.
Öffentlich erhalten bleiben Metaobjektklasse, Parameter-Eigentümer/Overrides,
Bedingungen, ACCORDING-Fälle und Quellreferenzen. Spezifikation 05/06/16/17,
Javadoc und die Migrationserklärung sind synchronisiert.

## Abnahme und tatsächlich ausgeführte Prüfungen

`GraphicResponsibilityContractTest` prüft:

- die sechs gepinnten Originale und öffentliche Modell-/Queryziele einschliesslich
  Navigation zum Parameter B;
- unbenutzte OF-Unterklassen, gezielte Verantwortungserweiterung über A/B/C,
  tatsächlich abgeleitete Metaobjektklassen und fehlende Pflichtparameter;
- unvereinbare Signaturen, explizite Metaobjektziele und kontextgebundene andere
  Metaobjektparameter, einschliesslich geerbter expliziter Zielrestriktion;
- geerbte Sign-/Parameterwerte, engere Parameterüberschreibungen, ersetzte
  Basiswerte, unbedingte und bedingte Erweiterungen;
- Bedingungen/ACCORDING mit erhaltenen Zielen und Reihenfolgen;
- Übersetzungen, importierte Signaturklassen, vertauschte Quellenreihenfolge
  sowie gültig → ungültig → gültig mit frischer/wiederverwendender Analyse.

Die Wiederverwendungsprüfung vergleicht Diagnosen und sämtliche Recordinhalte
von Symbolen, Typen und Ausdrücken mit dem vorhandenen Testnormalisierer für
snapshotlokale Symbolidentitäten. Compilerfehlerfälle verlangen SOURCE_ERRORS;
keine Implementierungssperre und kein interner Fehler verdient Erfolg.

**482 JVM-Tests und 20 Wrappertests bestehen.** `check` umfasst SDK-Beispiel,
CLI, Workspace, LSP, Projektion und die betroffenen Modell-/Queryverträge.
**1742 gemeinsame portable Fälle** bestehen auf TeaVM-JavaScript und WasmGC/Node,
Chromium, Firefox, WebKit und **echtem Safari** einschliesslich Worker-Neustart.
Der aktuelle Web-Image-Build sowie Node-/Chromium-/Firefox-/WebKit-Prüfungen
bestehen mit denselben 1742 Fällen. TeaVM-WasmGC bleibt der Produktionspfad.
Neun CLI-/SDK-Prozessprüfungen und die Diagramm-/Adapterprüfung bestehen.

**68 Prozesspaare mit ili2c 5.6.9 (66 D-Fälle und zwei E-Kontrollen):** Alle eigenen festgehaltenen Erwartungen
sind erreicht, 44 Referenzresultate stimmen überein. Der Bericht enthält sämtliche
Originale/Kontrollen, Quelltexte, Hashes, Befehle, Exitcodes und Ausgaben;
beobachtete Referenzunterschiede werden nicht pauschal als unabhängige
Normverletzungen gezählt.

Die vollständigen 593 eingefrorenen Fälle ergeben **574 PASS / 19 XFAIL /
0 FAIL / 0 ERROR / 0 XPASS**. Roh bleiben **574 PASS / 1 XFAIL / 18 FAIL**.
Der gesamte abschliessende Gradle-Aufruf endet erfolgreich mit Exitcode 0.

Der erste D-Gesamtlauf war grün. Zusätzliche Gegenproben zeigten danach die
Notwendigkeit getrennter bedingter Zuweisungskontexte und der Prüfung geerbter
expliziter Metaobjektziele. Diese Korrekturen sind im abschliessenden Lauf
enthalten. Frühere Logs bleiben unter Build-Ausgaben erhalten. Alle übernommenen
Berichte nennen Quelle, Toolversionen, Eingangspins und Artefakthashes.

## Verbleibende G2-Grenzen

Es wurden **keine bisherigen Suite-FAIL fachlich geschlossen**; D bearbeitet den
separat katalogisierten Sprachfund und seine Grundlagenkorrekturen. Die
Suitebilanz und die 19 XFAIL bleiben unverändert. Die 18 Referenzabweichungen
benötigen weiterhin die unabhängige Normprüfung; INTERLIS 1 bleibt separat.

Ein neuer unabhängiger Prüfpunkt ist **GRAPHIC_METAOBJECT_PARAMETER_FORWARD_TARGET**
(E1/E5): In den Kontrollen 11/41 akzeptiert der eigene Compiler die qualifizierte
METAOBJECT-OF-Zielklasse Colour auch nach der Parameterdeklaration; ili2c meldet
einen fehlenden/konfliktären Klassennamen. Die zusätzlichen Kontrollen 66/67
verschieben ausschliesslich diese Klasse vor S und werden von beiden Compilern
akzeptiert. Die normative Sichtbarkeits-/Vorwärtsbindungsregel ist noch zu prüfen.
Die Untersuchung verändert weder die D-Verantwortungsregel noch die Suiteausnahmen.

Nächster Abschnitt ist **E**: vollständiger Regel-/Implementierungs-/Nachweis-
Abgleich und die daraus folgenden konkreten Sprachlücken. Dazu gehören die
vorgemerkten Fragen zu Modellklassen, LineCoord-Abstraktion und CONTRACTED bei
2.4-Übersetzungen sowie die verbleibenden Builtin-/Typ-/Einheiten-/Metaattribut-
Pflichten. F (echte Overlays), G (Repository-/Offlinebeschaffung), vollständige
semantische JVM-/TeaVM-Graphparität und reale eingefrorene Modellclosures bleiben
offen. Die portablen Fälle prüfen die gemeinsame Annahme/Ablehnung; sie ersetzen
nicht diesen detaillierten Graphvergleich. Die übrigen P3-CLI-/Editor-/Plattform-/
Performancepflichten bleiben anschliessend bestehen.

[Verifikationsmanifest](verification-2026-10-25-g2-D.json),
[Prozessvergleich](g2-signatures-reference-2026-10-25.json),
[Fallkatalog](conformance-open-cases.json), [G2-Matrix](g2-rule-matrix.json).
