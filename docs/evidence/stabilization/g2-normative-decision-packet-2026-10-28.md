# Normative Referenzausnahmen – Nutzerentscheidung

L1-2-Ergänzung vom 29.10.2026: Das aktive JSON enthält jetzt 19 Einträge.
Die bisherigen 18 bleiben unverändert; ergänzt ist ausschliesslich
`ili23.Attribute23Test.attributeComplete` mit zwei fehlenden CIRCULAR-Markern,
exakten Diagnosen und gepinnter erfolgreicher CIRCULAR-Kontrolle. Die im
freigegebenen numerischen Profil ausdrücklich erhaltene CIRCULAR-Prüfung ist
die Grundlage; eine gesonderte explizite Freigabe des Fixture-Namens wird nicht
behauptet. Die folgenden älteren Darstellungen bleiben historisch; aktueller
Lieferstand und Abnahme stehen in [STATUS](../../g2/STATUS.md).

Status: **PENDING_USER_REVALIDATION**. Keine Erwartung wurde geändert.

**Nachweisprüfung offen:** Bei den ersten sechs OID-/CONTRACTED-Fällen beziehen
sich die unten abgedruckten Auszüge auf die erlaubten Inhalte eines TYPE MODEL.
Sie belegen die jeweilige OID-/CONTRACTED-Aussage nicht. Die einschlägigen
Normstellen und Gegenproben müssen vor einer begründeten Nutzerentscheidung
fallbezogen ergänzt und unabhängig geprüft werden. Die Empfehlungen in der
Tabelle sind deshalb keine bereits überprüften Normfreigaben.

Für diese sechs Fälle enthält das zugehörige JSON bereits die relevanteren
Absätze `NORM:2.3:2.5.1:p28:001` (Kennzeichnung kontraktpflichtiger Modelle)
und `NORM:2.3:2.5.2:p29:001` (OID-Definitionen und Kontrakte). Ihre Anwendung
auf die einzelnen Originalquellen und der 2.4-Vergleich sind noch unabhängig
zu prüfen; die automatische Auswahl des ersten Absatzes für diese Ansicht
ersetzt diese Prüfung nicht.

| Testfall | Schritt | Version | Eigene | Referenz | Normstellen | Empfehlung |
|---|---|---|---|---|---|---|
| `ili23.Class23Test.classComplete` | C2 | 2.3 | rejected | accept | 2.5.1–2.5.2 | KEEP_EXISTING_PENDING_USER |
| `ili23.Classes23Test.class_AcceptingOidDefinition` | C2 | 2.3 | rejected | accept | 2.5.1–2.5.2 | KEEP_EXISTING_PENDING_USER |
| `ili23.Topic23Test.topicComplete` | C2 | 2.3 | rejected | accept | 2.5.1–2.5.2 | KEEP_EXISTING_PENDING_USER |
| `ili23.Topics23Test.topics_ExplicitBasketOidDefinition` | C2 | 2.3 | rejected | accept | 2.5.1–2.5.2 | KEEP_EXISTING_PENDING_USER |
| `ili23.Topics23Test.topics_ExplicitTopicOidDefinition` | C2 | 2.3 | rejected | accept | 2.5.1–2.5.2 | KEEP_EXISTING_PENDING_USER |
| `ili23.ViewableGetAttributesAndRoles2Test.baseClass` | C2 | 2.3 | rejected | accept | 2.5.1–2.5.2 | KEEP_EXISTING_PENDING_USER |
| `ili23.Association23Test.association_AssociationAsTarget` | D2 | 2.3 | rejected | accept | 2.7.1 | KEEP_EXISTING_PENDING_USER |
| `ili24.Attribute24Test.attributeRef_RefAttrInClass` | D2 | 2.4 | rejected | accept | 3.5.3; 3.6.4 | KEEP_EXISTING_PENDING_USER |
| `ili23.Assoc23Test.assocComplete` | D2 | 2.3 | rejected | accept | 2.7.1 | KEEP_EXISTING_PENDING_USER |
| `ili23.Role23Test.roleComplete` | D2 | 2.3 | rejected | accept | 2.7.1 | KEEP_EXISTING_PENDING_USER |
| `ili23.Expressions23Test.expressions_ParentPathElement` | C4 | 2.3 | rejected | accept | 2.6; 2.8.1 | KEEP_EXISTING_PENDING_USER |
| `ili23.View23Test.view_AcceptBasicViewProjectionDef` | C4 | 2.3 | rejected | accept | 2.6; 2.8.1 | KEEP_EXISTING_PENDING_USER |
| `ili23.InterlisConversion23Test.iliconversion_CheckViewDefWithALLOF` | C4 | 2.3 | rejected | accept | 2.6; 2.8.10 | KEEP_EXISTING_PENDING_USER |
| `ili23.Models23Test.models_DomainAndRuntimeParam_SameName` | E2 | 2.3 | rejected | accept | 2.11; 2.5.1 | KEEP_EXISTING_PENDING_USER |
| `ili23.RuntimeParameter23Test.runtimeParameterComplete` | E2 | 2.3 | rejected | accept | 2.11; 2.5.1 | KEEP_EXISTING_PENDING_USER |
| `ili23.Expressions23Test.expressions_ThisAreaThatArea` | F1 | 2.3 | rejected | accept | 2.15, S. 69: ViewAttributes / ALL OF | KEEP_EXISTING_PENDING_USER |
| `ili24.Refhb24Test.roadsExgm2ien` | F3 | 2.4 | rejected | accept | 3.5.2–3.5.3; 3.10.2.2; 3.16 | KEEP_EXISTING_PENDING_USER |
| `ili23.LineType23Test.overlapTooSmallFail` | C3 | 2.3 | accepted | reject | 2.8.12.2, S. 50 | KEEP_EXISTING_PENDING_USER |

## Begründungen

### `ili23.Class23Test.classComplete`

OID AS und BASKET OID AS benötigen in 2.3 ein CONTRACTED-Modell; in 2.4 entfällt diese Pflicht.

> 2.5.1 Modelle  
Als Modell wird eine in sich geschlossene, vollständige Definition bezeichnet. Je nach Art des Modells 
kann es verschiedene Konstrukte enthalten.  
Ein reines Typenmodell (TYPE  MOD EL) darf nur Masseinheiten, Werteb ereiche, Funktionen und Linie n-
formen deklarieren.  
Ein Referenzsystem -Modell (REFSYSTEM  MOD EL) soll nebst den Definitionen eines Typenmodells nur 
Themen und Klassen deklarieren, die im Bezug zu Erweiteru ngen der vordefinierten Klassen AXIS bzw. 
REFSYS TEM 

> 3.5.1 Modelle 
Als Modell wird eine in sich geschlossene, vollständige Definition bezeichnet. Je nach Art des 
Modells kann es verschiedene Konstrukte enthalten. 
Ein reines Typenmodell (TYPE MODEL) darf nur Masseinheiten, Wertebereiche, Funktionen 
und Linienformen deklarieren. 
Ein Referenzsystem-Modell (REFSYSTEM MODEL) soll nebst den Definitionen eines Typen-
modells nur Themen und Klassen deklarieren, die im Bezug zu Erweiterungen der vordefinier-
ten Klassen AXIS bzw. REFSYSTEM stehen (vgl

Entscheidung: **PENDING_USER_REVALIDATION**.

### `ili23.Classes23Test.class_AcceptingOidDefinition`

OID AS und BASKET OID AS benötigen in 2.3 ein CONTRACTED-Modell; in 2.4 entfällt diese Pflicht.

> 2.5.1 Modelle  
Als Modell wird eine in sich geschlossene, vollständige Definition bezeichnet. Je nach Art des Modells 
kann es verschiedene Konstrukte enthalten.  
Ein reines Typenmodell (TYPE  MOD EL) darf nur Masseinheiten, Werteb ereiche, Funktionen und Linie n-
formen deklarieren.  
Ein Referenzsystem -Modell (REFSYSTEM  MOD EL) soll nebst den Definitionen eines Typenmodells nur 
Themen und Klassen deklarieren, die im Bezug zu Erweiteru ngen der vordefinierten Klassen AXIS bzw. 
REFSYS TEM 

> 3.5.1 Modelle 
Als Modell wird eine in sich geschlossene, vollständige Definition bezeichnet. Je nach Art des 
Modells kann es verschiedene Konstrukte enthalten. 
Ein reines Typenmodell (TYPE MODEL) darf nur Masseinheiten, Wertebereiche, Funktionen 
und Linienformen deklarieren. 
Ein Referenzsystem-Modell (REFSYSTEM MODEL) soll nebst den Definitionen eines Typen-
modells nur Themen und Klassen deklarieren, die im Bezug zu Erweiterungen der vordefinier-
ten Klassen AXIS bzw. REFSYSTEM stehen (vgl

Entscheidung: **PENDING_USER_REVALIDATION**.

### `ili23.Topic23Test.topicComplete`

OID AS und BASKET OID AS benötigen in 2.3 ein CONTRACTED-Modell; in 2.4 entfällt diese Pflicht.

> 2.5.1 Modelle  
Als Modell wird eine in sich geschlossene, vollständige Definition bezeichnet. Je nach Art des Modells 
kann es verschiedene Konstrukte enthalten.  
Ein reines Typenmodell (TYPE  MOD EL) darf nur Masseinheiten, Werteb ereiche, Funktionen und Linie n-
formen deklarieren.  
Ein Referenzsystem -Modell (REFSYSTEM  MOD EL) soll nebst den Definitionen eines Typenmodells nur 
Themen und Klassen deklarieren, die im Bezug zu Erweiteru ngen der vordefinierten Klassen AXIS bzw. 
REFSYS TEM 

> 3.5.1 Modelle 
Als Modell wird eine in sich geschlossene, vollständige Definition bezeichnet. Je nach Art des 
Modells kann es verschiedene Konstrukte enthalten. 
Ein reines Typenmodell (TYPE MODEL) darf nur Masseinheiten, Wertebereiche, Funktionen 
und Linienformen deklarieren. 
Ein Referenzsystem-Modell (REFSYSTEM MODEL) soll nebst den Definitionen eines Typen-
modells nur Themen und Klassen deklarieren, die im Bezug zu Erweiterungen der vordefinier-
ten Klassen AXIS bzw. REFSYSTEM stehen (vgl

Entscheidung: **PENDING_USER_REVALIDATION**.

### `ili23.Topics23Test.topics_ExplicitBasketOidDefinition`

OID AS und BASKET OID AS benötigen in 2.3 ein CONTRACTED-Modell; in 2.4 entfällt diese Pflicht.

> 2.5.1 Modelle  
Als Modell wird eine in sich geschlossene, vollständige Definition bezeichnet. Je nach Art des Modells 
kann es verschiedene Konstrukte enthalten.  
Ein reines Typenmodell (TYPE  MOD EL) darf nur Masseinheiten, Werteb ereiche, Funktionen und Linie n-
formen deklarieren.  
Ein Referenzsystem -Modell (REFSYSTEM  MOD EL) soll nebst den Definitionen eines Typenmodells nur 
Themen und Klassen deklarieren, die im Bezug zu Erweiteru ngen der vordefinierten Klassen AXIS bzw. 
REFSYS TEM 

> 3.5.1 Modelle 
Als Modell wird eine in sich geschlossene, vollständige Definition bezeichnet. Je nach Art des 
Modells kann es verschiedene Konstrukte enthalten. 
Ein reines Typenmodell (TYPE MODEL) darf nur Masseinheiten, Wertebereiche, Funktionen 
und Linienformen deklarieren. 
Ein Referenzsystem-Modell (REFSYSTEM MODEL) soll nebst den Definitionen eines Typen-
modells nur Themen und Klassen deklarieren, die im Bezug zu Erweiterungen der vordefinier-
ten Klassen AXIS bzw. REFSYSTEM stehen (vgl

Entscheidung: **PENDING_USER_REVALIDATION**.

### `ili23.Topics23Test.topics_ExplicitTopicOidDefinition`

OID AS und BASKET OID AS benötigen in 2.3 ein CONTRACTED-Modell; in 2.4 entfällt diese Pflicht.

> 2.5.1 Modelle  
Als Modell wird eine in sich geschlossene, vollständige Definition bezeichnet. Je nach Art des Modells 
kann es verschiedene Konstrukte enthalten.  
Ein reines Typenmodell (TYPE  MOD EL) darf nur Masseinheiten, Werteb ereiche, Funktionen und Linie n-
formen deklarieren.  
Ein Referenzsystem -Modell (REFSYSTEM  MOD EL) soll nebst den Definitionen eines Typenmodells nur 
Themen und Klassen deklarieren, die im Bezug zu Erweiteru ngen der vordefinierten Klassen AXIS bzw. 
REFSYS TEM 

> 3.5.1 Modelle 
Als Modell wird eine in sich geschlossene, vollständige Definition bezeichnet. Je nach Art des 
Modells kann es verschiedene Konstrukte enthalten. 
Ein reines Typenmodell (TYPE MODEL) darf nur Masseinheiten, Wertebereiche, Funktionen 
und Linienformen deklarieren. 
Ein Referenzsystem-Modell (REFSYSTEM MODEL) soll nebst den Definitionen eines Typen-
modells nur Themen und Klassen deklarieren, die im Bezug zu Erweiterungen der vordefinier-
ten Klassen AXIS bzw. REFSYSTEM stehen (vgl

Entscheidung: **PENDING_USER_REVALIDATION**.

### `ili23.ViewableGetAttributesAndRoles2Test.baseClass`

OID AS und BASKET OID AS benötigen in 2.3 ein CONTRACTED-Modell; in 2.4 entfällt diese Pflicht.

> 2.5.1 Modelle  
Als Modell wird eine in sich geschlossene, vollständige Definition bezeichnet. Je nach Art des Modells 
kann es verschiedene Konstrukte enthalten.  
Ein reines Typenmodell (TYPE  MOD EL) darf nur Masseinheiten, Werteb ereiche, Funktionen und Linie n-
formen deklarieren.  
Ein Referenzsystem -Modell (REFSYSTEM  MOD EL) soll nebst den Definitionen eines Typenmodells nur 
Themen und Klassen deklarieren, die im Bezug zu Erweiteru ngen der vordefinierten Klassen AXIS bzw. 
REFSYS TEM 

> 3.5.1 Modelle 
Als Modell wird eine in sich geschlossene, vollständige Definition bezeichnet. Je nach Art des 
Modells kann es verschiedene Konstrukte enthalten. 
Ein reines Typenmodell (TYPE MODEL) darf nur Masseinheiten, Wertebereiche, Funktionen 
und Linienformen deklarieren. 
Ein Referenzsystem-Modell (REFSYSTEM MODEL) soll nebst den Definitionen eines Typen-
modells nur Themen und Klassen deklarieren, die im Bezug zu Erweiterungen der vordefinier-
ten Klassen AXIS bzw. REFSYSTEM stehen (vgl

Entscheidung: **PENDING_USER_REVALIDATION**.

### `ili23.Association23Test.association_AssociationAsTarget`

Eine Assoziation als Rollenziel benötigt eine eigene OID.

> 2.7.1 Beschreibung von Beziehungen  
Eigentliche Beziehungen (im  Gegensatz zu R efer enzattr ibuten; v gl. Kapitel 2.6 Attribute ) werden als e i-
genständige Konstrukte beschrieben. Sie haben aber weitgehend gleiche Eigenschaften wie Klassen. So 
können sie selbst lokale Attribute und Konsistenz bedingungen aufweisen. Der Assoziationsname darf 
auch fehlen. Er wird dann implizit aus der Zusammensetzung der Rollennamen (erster, dann zweiter, 
usw.) gebildet. Die wichtigste Eigenschaft einer Bez

> 3.7.1 Beschreibung von Beziehungen 
Eigentliche Beziehungen (im Gegensatz zu Referenzattributen; vgl. Kapitel 3.6 Attribute) wer-
den als eigenständige Konstrukte beschrieben. Sie haben aber weitgehend gleiche Eigenschaf-
ten wie Klassen. So können sie selbst lokale Attribute und Konsistenzbedingungen aufweisen. 
Der Assoziationsname darf auch fehlen. Er wird dann implizit aus der Zusammensetzung der 
Rollennamen (erster, dann zweiter, usw.) gebildet. Die wichtigste Eigenschaft einer Beziehung 


Entscheidung: **PENDING_USER_REVALIDATION**.

### `ili24.Attribute24Test.attributeRef_RefAttrInClass`

Ein Referenzziel mit implizitem NO OID ist in 2.4 unzulässig.

> 3.5.3 Klassen und Strukturen 
Eine Klassendefinition (Schlüsselwort CLASS) deklariert die Eigenschaften aller zugehörigen 
Objekte. Eine Klasse wird in einem Thema abschliessend definiert. Klassendefinitionen sind er-
weiterbar, wobei eine Erweiterung primär sämtliche Attribute ihrer Basis-Klasse erbt. Deren 
Wertebereiche dürfen eingeschränkt werden. Zudem dürfen weitere Attribute oder zusätzliche 
Konsistenzbedingungen definiert werden. 
Der Wertebereich der Objektidentifikationen aller Objekt

> 2.5.3 Klassen und Strukturen  
Eine Klassendefinition (Schlüsselwort CLASS ) deklariert die Eigenschaft en aller zugehör igen Objekte. 
Klassendefinitionen sind erweiterbar, wobei eine Erweiterung primär sämtliche Attribute ihrer Basis -
Klasse erbt. Deren Wertebereiche dürfen eingeschränkt werden. Zudem dürfen weitere Attribute def iniert 
werden. 
Der Wertebereich der Objektidentifikationen aller Objekte dieser Klasse kann explizit festgelegt werden 
(OID AS ). Fehlt die Definition, gilt diej 

Entscheidung: **PENDING_USER_REVALIDATION**.

### `ili23.Assoc23Test.assocComplete`

Mehrere Links derselben Teilnehmer benötigen neben CARDINALITY eine eigene OID.

> 2.7.1 Beschreibung von Beziehungen  
Eigentliche Beziehungen (im  Gegensatz zu R efer enzattr ibuten; v gl. Kapitel 2.6 Attribute ) werden als e i-
genständige Konstrukte beschrieben. Sie haben aber weitgehend gleiche Eigenschaften wie Klassen. So 
können sie selbst lokale Attribute und Konsistenz bedingungen aufweisen. Der Assoziationsname darf 
auch fehlen. Er wird dann implizit aus der Zusammensetzung der Rollennamen (erster, dann zweiter, 
usw.) gebildet. Die wichtigste Eigenschaft einer Bez

> 3.7.1 Beschreibung von Beziehungen 
Eigentliche Beziehungen (im Gegensatz zu Referenzattributen; vgl. Kapitel 3.6 Attribute) wer-
den als eigenständige Konstrukte beschrieben. Sie haben aber weitgehend gleiche Eigenschaf-
ten wie Klassen. So können sie selbst lokale Attribute und Konsistenzbedingungen aufweisen. 
Der Assoziationsname darf auch fehlen. Er wird dann implizit aus der Zusammensetzung der 
Rollennamen (erster, dann zweiter, usw.) gebildet. Die wichtigste Eigenschaft einer Beziehung 


Entscheidung: **PENDING_USER_REVALIDATION**.

### `ili23.Role23Test.roleComplete`

HIDING ist bei einer nicht versteckenden Geschwisterrolle für dieselbe Zielklasse unzulässig.

> 2.7.1 Beschreibung von Beziehungen  
Eigentliche Beziehungen (im  Gegensatz zu R efer enzattr ibuten; v gl. Kapitel 2.6 Attribute ) werden als e i-
genständige Konstrukte beschrieben. Sie haben aber weitgehend gleiche Eigenschaften wie Klassen. So 
können sie selbst lokale Attribute und Konsistenz bedingungen aufweisen. Der Assoziationsname darf 
auch fehlen. Er wird dann implizit aus der Zusammensetzung der Rollennamen (erster, dann zweiter, 
usw.) gebildet. Die wichtigste Eigenschaft einer Bez

> 3.7.1 Beschreibung von Beziehungen 
Eigentliche Beziehungen (im Gegensatz zu Referenzattributen; vgl. Kapitel 3.6 Attribute) wer-
den als eigenständige Konstrukte beschrieben. Sie haben aber weitgehend gleiche Eigenschaf-
ten wie Klassen. So können sie selbst lokale Attribute und Konsistenzbedingungen aufweisen. 
Der Assoziationsname darf auch fehlen. Er wird dann implizit aus der Zusammensetzung der 
Rollennamen (erster, dann zweiter, usw.) gebildet. Die wichtigste Eigenschaft einer Beziehung 


Entscheidung: **PENDING_USER_REVALIDATION**.

### `ili23.Expressions23Test.expressions_ParentPathElement`

Ein abgeleiteter Wert muss in den Zielbereich passen; TEXT*20 ist nicht TEXT*10 zuweisbar.

Entscheidung: **PENDING_USER_REVALIDATION**.

### `ili23.View23Test.view_AcceptBasicViewProjectionDef`

Ein unbeschränktes TEXT-Funktionsergebnis ist nicht TEXT*80 zuweisbar.

Entscheidung: **PENDING_USER_REVALIDATION**.

### `ili23.InterlisConversion23Test.iliconversion_CheckViewDefWithALLOF`

Ein numerischer Wert ist keinem booleschen Attribut zuweisbar.

Entscheidung: **PENDING_USER_REVALIDATION**.

### `ili23.Models23Test.models_DomainAndRuntimeParam_SameName`

Laufzeitparameter sind in 2.3 nur innerhalb von CONTRACTED-Modellen zulässig; in 2.4 entfällt diese Pflicht.

> 2.11 Laufzeitparameter  
Nebst den eigentlichen Daten und den Metadaten können auch einz elne Datenelement e definiert werden,  
bei denen erwart et wird, dass sie von einem Bearbeitungs -, Auswerte- oder  Dar stellungssystem zur  Lauf-
zeit bereitgestellt werden. Sie heissen Laufzeitparameter.  
Syntaxregel: 
RunTimeParameterDef = 'PARAMETER' 
                        { RunTimeParameter -Name ':' AttrTypeDef ';' }.

> 3.11 Laufzeitparameter 
Nebst den eigentlichen Daten und den Metadaten können auch einzelne Datenelemente defi-
niert werden, bei denen erwartet wird, dass sie von einem Bearbeitungs-, Auswerte- oder Dar-
stellungssystem zur Laufzeit bereitgestellt werden. Sie heissen Laufzeitparameter. 
Syntaxregel: 
RunTimeParameterDef = 'PARAMETER' 
                        { RunTimeParameter-Name ':' AttrTypeDef ';' }.

Entscheidung: **PENDING_USER_REVALIDATION**.

### `ili23.RuntimeParameter23Test.runtimeParameterComplete`

Laufzeitparameter sind in 2.3 nur innerhalb von CONTRACTED-Modellen zulässig; in 2.4 entfällt diese Pflicht.

> 2.11 Laufzeitparameter  
Nebst den eigentlichen Daten und den Metadaten können auch einz elne Datenelement e definiert werden,  
bei denen erwart et wird, dass sie von einem Bearbeitungs -, Auswerte- oder  Dar stellungssystem zur  Lauf-
zeit bereitgestellt werden. Sie heissen Laufzeitparameter.  
Syntaxregel: 
RunTimeParameterDef = 'PARAMETER' 
                        { RunTimeParameter -Name ':' AttrTypeDef ';' }.

> 3.11 Laufzeitparameter 
Nebst den eigentlichen Daten und den Metadaten können auch einzelne Datenelemente defi-
niert werden, bei denen erwartet wird, dass sie von einem Bearbeitungs-, Auswerte- oder Dar-
stellungssystem zur Laufzeit bereitgestellt werden. Sie heissen Laufzeitparameter. 
Syntaxregel: 
RunTimeParameterDef = 'PARAMETER' 
                        { RunTimeParameter-Name ':' AttrTypeDef ';' }.

Entscheidung: **PENDING_USER_REVALIDATION**.

### `ili23.Expressions23Test.expressions_ThisAreaThatArea`

ALL OF ist für AREA-Inspektionen ausdrücklich unzulässig. Nach vollständiger Bindung der Geometrieinspektion verbleibt ausschliesslich VIEW_ALL_OF. Die positive Kontrolle entfernt nur diese Anweisung; THISAREA und THATAREA bleiben erhalten.

> 2.15 Sichten 
Sichten (Views) sind Klassen und Strukturen, deren Objekte nicht originär sondern virtuell sind, da sie 
aus Objekten anderer Sichten oder Klassen bzw. Strukturen abgeleitet werden. Sichten werden unter 
anderem dazu verwendet, die Grundlagen für Grafiken und spezielle Konsistenzbedingungen zu formuli e-
ren. Eine weitere Anwendung ist die Weitergabe der Daten an Empfängersysteme in einer abgeleiteten,  
in der Regel vereinfachten Form.

> 3.15 Sichten 
Sichten (Views) sind Klassen und Strukturen, deren Objekte nicht originär sondern virtuell sind, 
da sie aus Objekten anderer Sichten oder Klassen bzw. Strukturen abgeleitet werden. Sichten 
werden unter anderem dazu verwendet, die Grundlagen für Grafiken und spezielle Konsistenz-
bedingungen zu formulieren. Eine weitere Anwendung ist die Weitergabe der Daten an Empfän-
gersysteme in einer abgeleiteten, in der Regel vereinfachten Form. 
Sichten werden nur transferiert, wenn sie in 

Entscheidung: **PENDING_USER_REVALIDATION**.

### `ili24.Refhb24Test.roadsExgm2ien`

Ohne Klassen-/Topic-OID gilt in 2.4 NO OID; Beziehungen und Referenzattribute auf solche Klassen sind unzulässig. Alle 51 verbleibenden Diagnosen betreffen solche Ziele. Die Kontrollfassung ändert ausschliesslich OID-Deklarationen; Grafiken, Signaturparameter, Metaobjektwerte, Inspektionen und Pfade bleiben erhalten. METAOBJECT-Parameter sind keine Transferreferenzen und werden dadurch nicht verboten. E3 erhält die beiden RESTRICTION-Ziele von FontSymbol.Geometry; dadurch werden die Referenzen auf Color und PolylineAttrs auch in diesem Verwendungskontext geprüft. Die bisherigen 49 Diagnosen bleiben unverändert; zwei zusätzliche konkrete NO-OID-Diagnosen sind nachgeprüft.

> 3.5.2 Themen 
Ein Thema (Schlüsselwort TOPIC) enthält alle zur Beschreibung eines bestimmten sachlichen 
Teils der Realwelt nötigen Definitionen. Ein Thema kann auch Typen wie Masseinheiten, Wer-
tebereiche oder Strukturen definieren oder diese vom umhüllenden Modell oder einem impor-
tierten Modell benützen. In runden Klammern können die Vererbungseigenschaften definiert 
werden. Enthält ein Thema abstrakte Definitionen, die nicht im selben Thema konkretisiert wer-
den, muss das Thema als ABSTR

> 2.5.2 Themen  
Ein Thema (Schlüsselwort TOPIC ) enthält alle zur Beschre ibung eines bestimmten sac hlichen Teils der 
Realwelt nötigen Definitionen. Ein Thema kann auch Typen wie Masseinheit en, Werteb ereiche oder 
Strukturen definieren oder diese vom umhüllenden Modell oder einem importierten Modell benützen.  
In runden Klammern k önnen die Vererbungseigenschaften definiert werden. Da sich eine Erweiterung 
eines Themas immer auf ein Thema mit einem anderen Namen bezieht, macht EXTENDED  kei

Entscheidung: **PENDING_USER_REVALIDATION**.

### `ili23.LineType23Test.overlapTooSmallFail`

Die 2.3-Norm verlangt gleiche Einheiten, eine positive und unveränderliche Toleranz, nennt hier aber keine Präzisionsuntergrenze. Die explizite Stellenzahlregel in 2.4 wird nicht auf 2.3 übertragen.

> 2.8.12.2 Linienzug mit Streck en und Kreisbogen als vordefinierte Ku rvenstücke  
werden Linienzüge im Rahmen von Einzelfläc hen und Gebietseinteilungen (vgl. Kapit el 2.8.13 Einzelf lä-
chen und Gebietseinteilungen ) verwendet.  
Zur Definition eines konkreten Linienzug -Wertebereichs gehört immer die Angabe der erlaubten Kurve n-
stück-Formen mittels Aufzählung, z.B. Strecken (Schlüsselwort STRAIGHTS ), Kreisbogen (Schlüsselwort 
ARCS) oder weitere Möglichkeiten ( vgl. Kapitel 2.8.12.3 Weitere

> 3.8.12.2 Linienzug mit Strecken und Kreisbogen als vordefinierte Kurvenstücke 
Menge von ungerichteten (MULTIPOLYLINE) Linienzügen. Bei einem einzelnen Linienzug ([DI-
RECTED] POLYLINE) sind aber beim Transfer eines Ausschnittes auch MULTIPOLYLINE zu-
lässig (vgl. Kapitel 4.3.6 Codierung von Themen). Bei einer Menge von Linienzügen (MULTIPO-
LYLINE) müssen die einzelnen Linienzüge nicht miteinander verbunden sein. Zudem werden 
Linienzüge im Rahmen von Einzelflächen und Gebietseinteilungen (vgl.

Entscheidung: **PENDING_USER_REVALIDATION**.


## Nachtrag: Nutzerentscheidung vom 29.10.2026

Der vorstehende Text bleibt der ursprüngliche Entscheidungsentwurf. Die sechs
OID-/Basket-OID-Fälle sind inzwischen als bewusste Bestandskompatibilität ohne
CONTRACTED freigegeben; ihr aktueller Status und die unveränderten Original-
Normzitate stehen in der gleichnamigen JSON-Datei sowie in
`docs/evidence/conformance/normative-expectations.json`. Die übrigen zwölf Fälle
bleiben offen. Die neue Freigabe ist keine rückwirkende unabhängige Abnahme des
ursprünglichen Pakets und keine allgemeine Freigabe anderer Normabweichungen.
