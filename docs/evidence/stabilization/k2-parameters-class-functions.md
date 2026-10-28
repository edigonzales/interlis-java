# K2: Laufzeitparameter und Klassenfunktionen

## Umfang und Bilanz

WP-22/24/25; META-003/011/014, SEM-005/009/010/014/017,
SYN-004/013/015 und TEST-011/012. Ausgehend von K1 mit 22 offenen Projekt-FAIL:

| Originalfall | Ergebnis |
|---|---|
| ili23.Expressions23Test.equals_runtimeParam | PASS; gebundener PARAMETER-Ausdruck mit deklariertem Typ. |
| ili23.Expressions23Test.logical_function | PASS; explizite Klassenfunktionssignaturen und gebundene Klassenwerte. |
| ili23.Attributes23Test.attributePathType_ClassTypeRestriction | PASS; myClass-Rückgabetyp, Rollenpfad und typisiertes ATTRIBUTE OF. |
| ili23.Models23Test.models_DomainAndRuntimeParam_SameName | Fachlich implementiert; überprüfte CONTRACTED-Referenzausnahme für 2.3. |
| ili23.RuntimeParameter23Test.runtimeParameterComplete | Fachlich implementiert einschliesslich Übersetzungen; dieselbe CONTRACTED-Entscheidung. |

[2.3 §2.11](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf)
verlangt CONTRACTED für Laufzeitparameter. In 2.4 besteht diese Pflicht nicht mehr.
Die beiden Originale fehlen darin; minimal korrigierte vollständige Quellen
bestehen bei beiden Compilern. Die Ausnahmen prüfen exakt PARAMETER_CONTRACTED
über JSON, beide Quellpins und erfolgreiche Gegenproben. Andere Fehler bleiben
blockierend. Syntax-, Import-, Übersetzungs- und Typprüfungen sind implementiert,
bevor die Ausnahmen aufgenommen wurden.

## Umsetzung und notwendige Grundlagenkorrekturen

Parameter besitzen einen eigenen Namensraum und typisierte Deklarationen.
PARAMETER-, Klassen- und Attributpfadkonstanten enthalten gebundene Identitäten.
myClass, isOfClass und isSubClass verwenden explizite Signaturen und die gemeinsamen
Typregeln. ClassValueType hält SymbolId-Restriktionen. Parameter- und Funktions-
übersetzungen vergleichen Typen und Referenzziele. Quellqueries, Rename und LSP
verwenden diese Bindungen. Geänderte Vorab-APIs sind in Spec 04/05/16/17 und der
Migration dokumentiert.

Die K2-Sichtbarkeitstests fanden das Übertragen von UNQUALIFIED auf nachfolgende
Importnamen. Dies wurde je Import korrigiert. Der Ersatz des generischen
Attributpfad-Platzhalters erforderte die typisierte Bindung von @Argumenten,
OF-Ausdrücken und >>Konstanten sowie die Aufnahme der OF-Ausdrücke in die
Schema-Closure. Vier zuvor erfolgreiche AttributePath-/Konvertierungsfälle
sind damit weiterhin PASS; sie wurden separat gegen ili2c 5.6.9 geprüft.

## Tatsächliche Prüfungen

[Manifest](verification-2026-10-21-k2.json) enthält Quellstand, Artefakthashes,
Werkzeuge, Kommandos und Rohberichte. 356 JVM-Tests (zehn neue K2-Methoden),
15 Wrappertests, 1274 portable Fälle (74 neue K2-Fälle) bestehen.
TeaVM-JavaScript/WasmGC unter Node, reale Browser Chromium/Firefox/WebKit und
Safari sowie Web-Image unter Node/Chromium/Firefox/WebKit bestehen mit regulärem
Prozessende. SDK-Beispiel und neun CLI-Prozesse bestehen auf macOS arm64.
Der vollständige kombinierte Gradle-Lauf endet mit Exitcode 1 ausschliesslich
wegen der 17 verbleibenden Konformitätsfälle.

Roher Referenzvergleich: **559 PASS, 1 XFAIL, 33 FAIL**.
Projektbewertung: **559 PASS, 17 XFAIL, 17 FAIL**, jeweils 0 ERROR/XPASS.
16 normative Erwartungen und die separate INTERLIS-1-Ausnahme bleiben sichtbar.
[22 gezielte Referenzprozesse](k2-reference-comparison-2026-10-21.json) enthalten
fünf Originalfälle, vier bestehende PASS und zwei positive Normkontrollen bei
jeweils beiden Compilern. Normative Erwartungen werden nicht automatisch erzeugt.

## Verbleibende Ursachen und nächste Lieferung

K3: sechs CONTEXT-Fälle. K4: acht Metadaten-/Linienformfälle und zwei davon
abhängige Geometrieinspektionen. K5: roadsExgm2ien mit GRAPHIC und weiteren
Ziel-/OID-Ursachen. K6: sechs bekannte numerische Einheitenregressionen.
Der unabhängige Fehler übersetzter Importmodelle bleibt vorgemerkt.

Die vollständige abhängige Typprüfung von ATTRIBUTE OF @Argument bei Aufrufen,
weitere Attributpfadoperationen und Signatur-/METAOBJECT-Parameter sind keine
vollständige K2-Familienabnahme; sie bleiben bei ihren Sprach-/Deklarationspflichten.
Die Katalogfälle sind im beschriebenen Umfang geschlossen. P3 bleibt offen,
einschliesslich Plattform-, Performance-, Workspace-, LSP- und CLI-Pflichten.
