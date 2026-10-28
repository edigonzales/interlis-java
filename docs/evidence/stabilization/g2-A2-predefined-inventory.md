# A2-R2 – Vordefinierte Inhalte und Sprachprofile

**Stand: A2-R2 unabhängig geschlossen.** A2 bleibt in
Nacharbeit; R3/R4 und die vollständige A2-Abnahme werden hier nicht geschlossen.
Anforderungen: META-003/006/011/014/015, SEM-005/008/010/017,
SYN-004/013 sowie TEST-011/012. Massgeblich bleibt der Plan A–I.

## Umfang und konkrete Zuordnung

Der [explizite Katalog](../../../testdata/g2/predefined-catalogue.json) erfasst
130 Deklarationen beziehungsweise Mitglieder aus den gepinnten Normen:

| Familie | Einzelobjekte |
|---|---:|
| Modell INTERLIS | 1 |
| Linienformen | 2 |
| Einheiten einschliesslich Kurzzeichen und Zeitumrechnungen | 26 |
| Domänen einschliesslich OID, Alignment, LineCoord und Zeitformaten | 16 |
| Enumwerte in ihrer fachlichen Reihenfolge | 10 |
| Klassen / Strukturen | 8 / 12 |
| Topic / Metadatenbasket / Metaobjekte | 1 / 1 / 2 |
| Attribute / Parameter / Constraints | 21 / 5 / 4 |
| Standardfunktionen, einschliesslich nur im Funktionskapitel deklarierter Namen | 21 |

Jede Zeile enthält die exakte Normdeklaration beider anwendbaren Versionen,
ihre Seiten-/Texthashes, Quellanker der tatsächlichen Fabriken,
Namens-/Kurzzeichenbeziehungen und eine geplante positive, negative sowie
Modell-/Queryabnahme. Die Quellenanker sind Implementierungskandidaten, kein
Beleg, dass gewöhnliche Bindung oder sämtliche Eigenschaften funktionieren.
Die meisten Inhalte gehören B1; Funktionssignaturen E2, Namenssichtbarkeit B2.

Einzelne Versionsunterschiede bleiben sichtbar: NOOID nur in 2.4, LineAttrs
an SurfaceEdge nur in 2.3, verschiedene FORMAT-Breiten und OID-Basen sowie
Funktionssignaturen. `areAreas2`/`areAreas3` behalten ihre offene Normfrage.
Die Normanhänge sind ausdrücklich nicht kompilierbare Illustrationen; dieser
Katalog behauptet deshalb keine direkt kompilierbare INTERLIS-Quelldatei.

Daneben sind ANYCLASS, ANYSTRUCTURE, UNDEFINED, PI und LNBASE sowie die
16 internen Funktions-Typvarianten einzeln als Sonderwerte/-typen erfasst.
Sie werden nicht als erfundene gewöhnliche Domänen ausgegeben.

## Lexikalische Wirkung und Abnahmegrenze

Die reproduzierbare [Quellinventur](g2-source-inventory.json) enthält:

- jedes der 154 beziehungsweise 168 reservierten Wörter als eigenen Eintrag,
  mit Normtabellenbezug und geplanter Identifier-/Fragment-/Renameprüfung;
- alle 13 Featurezweige, ihre tatsächliche Verfügbarkeit, tokenbasierte und
  AST-basierte Verwendungen sowie zuständige fachliche Folgeschritte;
- jeden der elf kontextabhängigen Tokens mit Versions- und Lookaheadbedingung;
- Identifier-, EOF-/Hidden-Token-, Feature- und Tokenklassifikationsregeln;
- alle gefundenen Erzeuger von PredefinedOrigin in Compiler/Modell, damit
  zusätzliche Erzeuger oder Kontextkopien nicht hinter einem Fabriknamen fehlen.

Die beiden reservierten Quelltabellen stimmen vollständig mit den gepinnten
Normtabellen überein. Das belegt den Tabelleninhalt, noch nicht die korrekte
Anwendung in jedem Kontext. Die konkreten Fall-/Assertionsnachweise bleiben
Teil von A2-R3 und den späteren B1–B3-/E2-Abnahmen.

## Bereits sichtbare offene B1-Ursachen

Der Katalog weist die tatsächlichen Aufbauwege aus. SurfaceEdge,
SurfaceBoundary und LineGeometry werden in ViewBuilder als kontextgebundene
Inspektionsstrukturen aufgebaut. Ihre gewöhnliche Bindung als vordefinierte
Strukturen ist dadurch nicht belegt. Gleiches gilt für die direkte Bindung des
Modells INTERLIS und der Topic-/Basket-/Metaobjektnamen des Zeitmodells.

Bei NAME, URI und BOOLEAN muss die vorhandene Schlüsselwort-/Typsemantik von
vollständig veröffentlichten DomainSymbol-Inhalten unterschieden werden.
Auch Einheitenbezeichnungen gegen Kurzzeichen und das im Code geschriebene
KILOGRAMM gegenüber KILOGRAM benötigen konkrete B1-Proben. Diese Beobachtungen
ändern keine Normregel und sind noch keine vollständig reproduzierten Defekte.
Sie bleiben bei B1, statt jetzt als freie Nebenbaustellen korrigiert zu werden.

## Reproduktion

`author_g2_predefined.py --out <build-Datei>` liest die gepinnten PDFs mit
pypdf 6.10.0 und erzeugt die explizite Deklarationsliste ausschliesslich unter
build/. Die Übernahme in testdata erfolgt bewusst. Normative Erwartungen,
Suitepins und Baselines werden dabei weder erzeugt noch geändert.

`g2_inventory.py --norm23 ... --norm24 ... --out <build-Datei>` prüft zusätzlich
alle exakten Normauszüge, Quellenanker und versionierten Zuordnungen. Die
normalen stdlib-Hilfstests brauchen kein PDF-Paket. Geprüft werden insbesondere
verlorene Zeilen, abgeschwächte Abnahmen, Quellen-/Zitatmanipulationen,
Schlüsselwortänderungen und die Trennung von Inventar und fachlicher Abnahme.

Kein Produktionscode, keine öffentliche API, kein XFAIL und keine normative
Erwartung werden mit diesem Abschnitt geändert. Ein vollständiger Phasenlauf
ist für die weiterhin offene Phase A noch nicht fällig; der letzte volle
Ausgangslauf bleibt A1. Die gezielten Prüfungen und das unabhängige Review
werden mit eigenem Quellstand festgehalten.

## Gezielte Nacharbeit nach erstem R2-Review

Das [unabhängige Review](g2-reviews/A2-R2-attempt1.md) bestätigt die 130
Deklarationen und lexikalischen Mengen, verlangt aber zwei Korrekturen bei den
Sonderwerten/Typvarianten. Jede dieser 21 Zeilen besitzt jetzt ausdrücklich
Sprachscope und Verknüpfungen zu vorhandenen Grammatik-/Fach-/Funktionsregeln.
Bei internen Factoryvarianten bleibt dieser Scope von tatsächlich verwendenden
Signaturen und deren Sprachversionen getrennt. Die Verwendung wird aus den
aktuellen Signaturdeklarationen mit Argumentposition beziehungsweise Ergebnis
nachgezählt und gegen den Katalog geprüft.

`Value.OBJECT` ist in keiner aktuellen Signatur verwendet. Die Zeile erhält
keine erfundene Funktionsabnahme: Sie dokumentiert Nullgebrauch und bindet jede
künftige OBJECT-Signaturassertion an die Nutzerentscheidung für areAreas2.
Die areAreas2-Zeile enthält jetzt beide exakten 2.4-Belege direkt: OBJECTS in
§3.14, OBJECT in Anhang A. Erwartung und Compiler werden nicht geändert.
Das [frische unabhängige Review](g2-reviews/A2-R2-attempt2.md) schliesst
beide R2-Befunde nach 29 Tests und drei eigenen Manipulationsproben. Das
Gesamturteil für A2 bleibt NACHARBEIT wegen R3/R4.

## Zusätzliche Beobachtungen und nächste Arbeit

16 [direkte Bindungs-Prozesspaare](g2-A2-predefined-binding-observations-2026-10-27.json)
halten Quellen, Artefaktpins, Kommandos und vollständige Diagnosen fest. Die
sechs gewöhnlichen Strukturverwendungen von SurfaceEdge, SurfaceBoundary und
LineGeometry bleiben beim eigenen Compiler ungelöst; ili2c akzeptiert sie.
Dies ist ein B1-Folgepunkt, keine neue Suiteausnahme. Die qualifizierten
Schreibweisen INTERLIS.NAME/URI/BOOLEAN lehnen dagegen beide Compiler ab.
Aus der Inventarzeile wird keine zusätzliche zulässige Quellsyntax abgeleitet.
Diese Prozessproben waren nicht Teil des eingefrorenen R2-Reviewpakets.

[R3-Vorbereitung](g2-A2-R3-assertion-preparation-2026-10-27.json) verbindet
fünf bisherige Kandidaten mit konkreten vorhandenen Assertions und nennt
verbleibende Lücken. Vier ausgewählte JVM-Testmethoden sind frisch erfolgreich
ausgeführt, ihre XMLs separat archiviert. Die fehlenden Fallfamilien und
Nutzerentscheidungen bleiben einzeln benannt; R3 ist dadurch nicht abgenommen.
