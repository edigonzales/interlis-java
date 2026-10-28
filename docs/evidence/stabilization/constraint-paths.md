# Gemeinsame UNIQUE-/EXISTENCE-Pfadbindung

Stand 20. Oktober 2026; WP-22, META-006/014/016, SEM-008/010/014,
SRC-002–005 und LSP-012/013 im geprüften Teilumfang. P2/P3 bleiben offen.

Dieser historische Abschnitt dokumentiert die damalige Bereichsauslegung.
Die [Fortsetzung](existence-types-and-indices.md) korrigiert sie und ergänzt
weitere Typfamilien sowie LIST-/Achsenindizes; alte Resultate bleiben erhalten.

## Änderung und öffentliche Verträge

UNIQUE, LOCAL und EXISTENCE verwenden den vorhandenen strukturierten
Pfadbinder. Entfernt sind die Auswahl bloss des ersten Namens, die globale
Suche nach gleichnamigen Attributen/Rollen, leere LOCAL-Erfolgswerte und die
EXISTENCE-Prüfung auf einen Punkt im gesamten Deklarationstext. Es gibt keine
zusätzliche Pass-Infrastruktur und keine zweite Constraint-Pfadsemantik.

Syntax erhält vollständige Pfade und Operatoren, WHERE, Namen und BASKET.
LOCAL trennt den Strukturpräfix von seinen Feldern. Im Modell referenzieren
Schlüssel, Präfix, Felder und EXISTENCE-Alternativen `ExpressionId`s; dieselben
`PathExpression`-Werte bewahren Typen, Referenzen und Quellbereiche. Schema-
Selektion und Graphprüfung nehmen alle Ausdrücke auf. Übersetzungsvergleiche
berücksichtigen nun auch diese Pfadreferenzen und die Zielalternativen.
Spezifikation 04/05/16 und die API-Migration 0.2 sind synchronisiert.

Bei EXISTENCE bleibt der lexikalische Topic erhalten, während jeder Zielpfad
in seiner eigenen Klasse bindet. Abhängigkeiten werden über gebundene
Topic-Identitäten geprüft. Qualifizierte Viewable-Namen benötigen den
Modellnamen; `Topic.Klasse` allein ist kein Ersatz für `Modell.Topic.Klasse`.
Die bestehende Resolverlogik erhält dafür eine Option für vollständige
Qualifikation. Andere ältere Resolveraufrufe mit relativen Qualifikatoren
sind dadurch noch nicht allgemein bereinigt.

## Normbezug und Referenzunterschied

Grundlage sind [2.3 §2.12](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf)
und [2.4 §3.12 sowie ViewableRef in §3.15](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf).
UNIQUE-Schlüssel sind singulär und enden nicht auf Strukturwerten; LOCAL bindet
Felder innerhalb des ausgewählten Strukturelements. Die vollständige
Qualifikation ist eine syntaktische Vorgabe, keine Topic-Abhängigkeit.

Die EXISTENCE-Typprüfung interpretiert die Normbedingung gleicher oder
erweiterter Wertebereiche gerichtet: Der Quellbereich darf den Zielbereich
nicht erweitern. Strukturidentitäten und Spezialisierung werden beachtet;
Geometrie → Koordinate prüft den Vertexbereich, die Gegenrichtung ist nicht
zulässig. Zahlen-/Textgrenzen verwenden gemeinsame Zuweisungsregeln.
Das ist eine ausdrücklich dokumentierte Normauslegung, keine behauptete
ili2c-Kompatibilität für diesen Randfall.

[14 reale Vergleichsfälle je Compiler](constraint-reference-comparison-2026-10-20.json)
enthalten Quellen, Pins, Kommandos und beide Prozessausgaben. ili2c 5.6.9
akzeptiert auch `0..999` → `0..10` sowie `TEXT*20` → `TEXT*10`; der aktuelle
Compiler lehnt diese Erweiterungen ab. Der eingefrorene positive Fall
`ili24.Constraints24Test.constraintsCompile + Constraint-Metamodelltests`
enthält vier solche numerischen EXISTENCE-Regeln und wird deshalb zusätzlich
abgelehnt. Baseline und XFAILs bleiben unverändert: Dieser Unterschied bleibt
als offene Konformitätsabweichung sichtbar, statt als PASS umklassifiziert zu
werden. Die fachliche Auslegung und Referenzabweichung sind vor der vollständigen
P3-Abnahme nochmals im Normreview zu bestätigen.

Ein früher eigener Qualifikationstest verwendete irrtümlich `Topic.Klasse`.
Der Prozessvergleich deckte dies auf. Positive Fälle verwenden nun vollständige
Qualifikation; die verkürzte Form besitzt einen Negativtest. Der erste volle
Zwischenlauf ist daher kein Nachweis für den abschliessenden Quellstand.

## Prüfungen und weiterhin offene Punkte

Acht neue JVM-Verträge prüfen Schlüsselreihenfolge, WHERE, LOCAL-Präfixe und
Feldscopes, Typen, Schema-Closure, Navigation/Rename, Rollen und Qualifikatoren,
Topic-Abhängigkeiten, alle EXISTENCE-Alternativen, Strukturen, Geometrie und
explizite Lücken. 72 zusätzliche portable Fälle laufen mit den vorhandenen
250 Fällen auf denselben JVM-/TeaVM-/Web-Image-Verträgen.

Offen bleiben vollständige EXISTENCE-Kompatibilität für weitere Typfamilien,
ganze Collection-Attributwerte, allgemeine Strukturmengen-, Index- und
Inspektionspfade sowie sämtliche View-/Übersetzungs-/Transferkombinationen.
Nicht geprüfte Vergleiche innerhalb weiterer Typfamilien und Collectionwerte
melden `EXISTENCE_TYPE_COMPATIBILITY` beziehungsweise `EXISTENCE_COLLECTION`
als Implementierungslücke. Index-/Inspektionsoperatoren verschwinden nicht als
gewöhnliche Namen. Eine solche Lücke lässt kein gültiges Schema entstehen.

Die ergänzten Grenzen-/Koordinatenregeln sind keine vollständige Abnahme der
Einheiten-, Genauigkeits-, Enum- oder Geometriesemantik. Auch die Normregeln zur
Verwendung von OBJECTS-Funktionen in MANDATORY-/SET-Constraints bleiben separat
zu prüfen. Nächster fachlicher Schritt: die offenen EXISTENCE-Typfamilien und
Collection-/Indexpfade, danach die grossen View- und S2c-Konformitätsblöcke.

Aktuelle Messergebnisse und Rohberichtshashes stehen im
[Verifikationsmanifest](verification-2026-10-20-constraints.json).
