# Zusätzlicher 2.3-Fall: modellweite Klasse als Rollenziel

Status: **USER_APPROVED_COMPATIBILITY**, ausdrückliche Antwort vom30.10.2026:
„Bisherige Kompatibilität wie ili2c erhalten (empfohlen)“. Die bestätigten zwölf
Fälle bleiben unverändert. Keine22.Referenzausnahme. Die folgende Darstellung
hält die tatsächlich vorgelegten Alternativen und den Red-Lauf fest.

Der frische volle Conformancelauf deckt einen weiteren Positivfall auf:
`ili23.Association23Test.association_ClassesLevelTopicAndModel`. Er definiert
die abstrakte Klasse `KatalogElem` direkt im Modell und verwendet sie aus
`Kataloge.Hierarchie.Parent` als Rollenziel. ili2c und der bisherige Compiler
akzeptieren das unveränderte Original.

INTERLIS2.3 §2.5.3 S.30 verbietet Assoziationen zu Klassen ausserhalb von
Themen ausdrücklich. In2.4 §3.5.3 fehlt dieses ausdrückliche Verbot.
Der neue Normcheck liefert für das Original genau einen Fehler
`ILI-SEM-0010 / ROLE_TARGET_MODEL_CLASS` am Rollenbereich. Die21 bestehenden
strengen Assessments bestehen unverändert; Projektbilanz nun
576PASS /16XFAIL /1FAIL. Kein Pin oder Erwartungswert wurde angepasst.

Empfehlung: die bisherige Kompatibilität wieili2c für modellweite Klassen als
Rollenziele ausdrücklich erhalten. Die tatsächlich gebundene Zielklasse,
Kardinalität, Beziehungsstärke, Vererbung und OID-Regeln bleiben überprüft.
Das passt zur abgeschwächten2.4-Norm und erhält das bewusst benannte
Bestandsfixture. Alternativ kann die2.3-Normprüfung bleiben; dann braucht dieser
neue Fall eine ausdrücklich freigegebene22. Referenzausnahme mit unverändertem
Original, exakter Diagnose und erfolgreicher Kontrolle mit Klasse im Thema.

[Vollständige Originalbytes, SHA-256, Normabsatz und Beobachtung](L4-1-model-class-decision.json).
Eine minimale erfolgreiche Kontrolle mit lokaler Unterklasse ist bereits in
`AssociationStrengthContractTest.version23ModelLevelTargetsRetainTheApprovedCompatibilityProfile`
ausgeführt; die endgültige Testanforderung folgt der Nutzerentscheidung.
