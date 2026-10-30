# L4-1 – konkrete verbleibende Normentscheidungen

Status: **USER_APPROVED_EXISTING_PROFILE**, ausdrückliche Antwort vom30.10.2026:
„Bestehendes Profil für alle zwölf bestätigen (empfohlen)“. Keine Erwartung oder Pinänderung.
Die unabhängige Endabnahme bleibt bis zu L4-1/L4-2 offen.
Das [fallbezogene Paket](L4-1-decisions.json) enthält die zwölf aktiven
Manifestobjekte unverändert, mit Original-/Kontrollhashes und Diagnoseverträgen,
sowie die einschlägigen vollständigen gehashten Normabsätze. Die früheren sechs
OID-/CONTRACTED-Freigaben und die ausdrücklich bestätigte CIRCULAR-Ausnahme
sowie TYPE-MODEL-, Zahlen-, Funktions- und SET-Profile werden nicht erneut vorgelegt.

Empfehlung: das bestehende Profil für diese zwölf Fälle bestätigen. Elf
Originale bleiben gezielt abgelehnt, das positive 2.3-Toleranzoriginal bleibt
akzeptiert. Die Rohabweichungen gegenüber ili2c bleiben vollständig sichtbar.

| Fall | Beobachtung und empfohlene Regel | Original / passende Kontrolle |
|---|---|---|
| `ili23.Association23Test.association_AssociationAsTarget` | §2.7.1 S.35: Eine als Rollenziel verwendete Assoziation braucht eine eigene OID. | Original ohne OID abweisen; Kontrolle mit OID zulassen. |
| `ili24.Attribute24Test.attributeRef_RefAttrInClass` | §3.5.3 S.36: Fehlt auch beim Thema eine OID, gilt NO OID; Referenzen darauf sind ausdrücklich unzulässig. | Original ohne Ziel-OID abweisen; Kontrolle mit OID zulassen. |
| `ili23.Assoc23Test.assocComplete` | §2.7.1 S.35: Mehrere Links derselben Teilnehmer brauchen neben CARDINALITY eine OID. | Zwei betroffene Assoziationen ohne OID abweisen; OID-Kontrolle zulassen. |
| `ili23.Role23Test.roleComplete` | §2.7.1: HIDING erlaubt keine nicht verdeckende Geschwisterbeziehung auf dieselbe Zielklasse. | Originalen Geschwisterkonflikt abweisen; reine Konfliktkorrektur zulassen. |
| `ili23.Expressions23Test.expressions_ParentPathElement` | §2.6.1: Abgeleiteter Wert muss zum Attributtyp zuweisungskompatibel sein. | TEXT*20 → TEXT*10 abweisen; ausreichend breites Ziel zulassen. |
| `ili23.View23Test.view_AcceptBasicViewProjectionDef` | §2.6.1 / §2.8.1: Unbeschränktes TEXT passt nicht in TEXT*80. | Unbeschränktes Funktionsergebnis am engen Ziel abweisen; typkompatible Kontrolle zulassen. |
| `ili23.InterlisConversion23Test.iliconversion_CheckViewDefWithALLOF` | §2.6.1: Numerisch ist nicht BOOLEAN. | Numerische Ableitung am booleschen Attribut abweisen; typkorrigierte Kontrolle zulassen. |
| `ili23.Models23Test.models_DomainAndRuntimeParam_SameName` | §2.11 S.59: Laufzeitparameter verlangen in2.3 CONTRACTED. | Original ohne CONTRACTED abweisen; Kontrolle mit CONTRACTED zulassen. Der Namensraumfall selbst bleibt zulässig. |
| `ili23.RuntimeParameter23Test.runtimeParameterComplete` | Dieselbe ausdrückliche CONTRACTED-Regel für Laufzeitparameter. | Beide Originalmodelle abweisen; CONTRACTED-Kontrolle zulassen. Die frühere OID-Kompatibilität erweitert diesen Vertrag nicht automatisch. |
| `ili23.Expressions23Test.expressions_ThisAreaThatArea` | §2.15 S.69: ALL OF ist bei AREA-Inspektionen ausdrücklich unzulässig. | Original mit ALL OF abweisen; nur ALL OF entfernen, THISAREA/THATAREA erhalten. |
| `ili24.Refhb24Test.roadsExgm2ien` | §3.5.3: dieselbe2.4-NO-OID-Regel für Referenzen und Beziehungen. | Unveränderte fünfteilige Originalclosure mit51 exakt gepinnten Diagnosen abweisen; ausschliesslich OIDs korrigierende Kontrolle zulassen. Metaobjektparameter bleiben zulässig. |
| `ili23.LineType23Test.overlapTooSmallFail` | §2.8.12.2 S.50 verlangt eine positive Toleranz, keine Präzisionsuntergrenze. Die explizite2.4-Stellenzahlregel wird nicht auf2.3 übertragen. | Gepinntes Original: Koordinaten mit3 Nachkommastellen, Toleranz0.0001. In2.3 zulassen, obwohl ili2c es ablehnt. Kontrolle0.001 zulassen;0.0000 strikt abweisen. |

Die Entscheidung hat eine konkrete Kompatibilitätsfolge: Bestandsmodelle mit
den genannten elf normwidrigen Formen bleiben abgelehnt, auch wenn ili2c sie
akzeptiert. Eine gewünschte Kompatibilitätsausnahme kann deshalb je Regel
statt der Empfehlung gewählt werden. Der unabhängige Vorab-Audit hat die
Regeln und Kontrollbedingungen gelesen und Gegenproben ausgeführt; dessen
teilweise rekonstruierten Originale ersetzen nicht die fällige vollständige
Conformance-Ausführung an den tatsächlichen gepinnten Originalbytes.
