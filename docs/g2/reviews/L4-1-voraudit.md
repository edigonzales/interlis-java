# Unabhängiger VORAUDIT L4-1 — AC4-01 und AC4-03

**Kein Lieferungs-PASS. Kein G2-PASS.**

| Feld | Wert |
|---|---|
| Auftrag | Unabhängiger Vollständigkeitsabgleich AC4-01 und entscheidungsreifes AC4-03 |
| Geprüfter Basisstand | `4a84068624cffde4549186c53cf23b9aaad7b179` |
| Worktree | `/tmp/interlis-java-L4-audit-4a84068` (sauber, `git status --porcelain` leer) |
| Prüfer | OpenCode CLI 2.0.28, `deepseek/deepseek-flash` über DeepSeek direkt (kein OpenRouter) |
| Toolchain | Java 25.0.3 (Temurin, `JAVA_HOME`), Node 24.22.0, Gradle-Wrapper |
| Änderungen | Keine Produkt-, Test-, Build-, Norm-, Baseline- oder Erwartungsänderung. Eigene Proben ausschliesslich unter `build/review/L4-audit/`. Kein Commit, Push oder Release. |
| Python | Nicht verwendet. |

Dieser Bericht ist ein **Vorab-Audit**. Er ersetzt weder den L4-1-Review noch die G2-Gesamtfreigabe. Er belegt, dass AC4-01 am eingefrorenen Basisstand **nicht erfüllt** ist und welche konkreten Lücken dafür ursächlich sind. AC4-03 wird fallbezogen entscheidungsreif gemacht.

---

## 1. Ausgangsprüfung und tatsächlich ausgeführte Läufe

```
git rev-parse HEAD
  4a84068624cffde4549186c53cf23b9aaad7b179

git status --porcelain
  (leer)

./gradlew :interlis-compiler:test --console=plain
  EXIT=0
  133 Testklassen, 710 Tests, 0 Failures, 0 Errors, 0 Skips

./gradlew :interlis-cli:installDist --console=plain
  EXIT=0
```

Der grüne Compiler-Testlauf ist **keine** Vollständigkeitsaussage. Er zeigt nur, dass die vorhandenen 710 Assertions am Basisstand halten. Die eigentliche AC4-01-Frage ist, ob **jede** der 369 Compilerbedingungen und jede der 80 offenen Anforderungen an eine tatsächlich ausgeführte öffentliche Assertion gebunden ist. Das ist sie nicht.

Zusätzlich geprüft wurden 855 `@Test`/`@ParameterizedTest`-Methoden über alle Module (Compiler 707, CLI 37, Repository 41, Workspace 20, LSP 16, Model 16, Projection 12, Bridge 6). Produktionsseitig wurden insbesondere `GraphBuilder` (Regel-Emissionen und Diagnosegründe), `RoleSymbol`/`AssociationSymbol`/`SemanticGraph`, der Diagnosekatalog sowie die öffentlichen `CompiledSchema`-/`TypeRelations`-Verträge gelesen. Die 614 `CANDIDATE_ONLY`-Links wurden nur als Suchhinweis benutzt; jede Zuordnung wurde gegen die tatsächliche Methode geprüft. Wo der Vorschlag nachweislich falsch war (z. B. `SYN-008`, `SEM-007`), wurde er verworfen und die Lücke offen ausgewiesen.

---

## 2. AC4-01 — Vollständigkeitsabgleich

### 2.1 Zahlenbild

Quelle: `docs/evidence/stabilization/g2-norm-conditions.json` (Status dort: `PLANNED_NOT_ACCEPTED`, `implementation: NOT_ASSESSED` für alle 777 Bedingungen) und `docs/evidence/stabilization/g2-test-candidate-index.json` (Status dort: `CANDIDATE_ONLY`, ausdrücklich „Keyword candidates only; no assertion, execution or acceptance claim“).

Die 369 `COMPILER_RULE`-Bedingungen verteilen sich auf **139 Abschnitte** (2.3: 155, 2.4: 214). Nach Prüfung der tatsächlichen Paragraphen, Java-Testmethoden und Produktionsquellen ergibt sich:

| Status | Anzahl | Bedeutung |
|---|---:|---|
| `COVERED` | 184 | Mindestens eine echte öffentliche JUnit-Assertion prüft den genannten Vertrag. |
| `PARTIAL` | 87 | Ein Teil des Paragraphenclusters ist assertiert; mindestens eine Teilaussage ist nicht durch eine öffentliche Assertion gebunden. |
| `GAP` | 5 | Keine öffentliche Assertion; bei 2.7.2/3.7.2 zusätzlich eine reale Normverletzung. |
| `NOT_COMPILER` | 93 | Keine Compilerpflicht; begründet reklassifiziert (64 erklärend, 25 Laufzeit/Transfer, 4 ausserhalb). |

Damit sind **184 von 369** Bedingungen wirklich assertiert, **87 nur teilweise** und **5 gar nicht**. 93 Zeilen sind keine Compilerpflicht. Selbst wenn man die 93 Reklassifikationen vollständig akzeptiert, bleiben **276 Compilerbedingungen**, von denen **87 teilweise und 5 vollständig offen** sind. AC4-01 verlangt „sämtliche … mit tatsächlichen … Assertions verknüpft“; das ist nicht erreicht.

Die vollständige, maschinenlesbare Zuordnung steht in [`mappings.json`](mappings.json). Jeder Eintrag enthält `id`, `version`, `section`, `classification`, `status`, `evidence[{suite,method,assertion}]` und `note`. Die folgende Tabelle zeigt alle Abschnitte mit offenen oder teilweisen Bedingungen.

| Version | Abschnitt | Bedingungen | COVERED | PARTIAL | GAP | nicht Compilerpflicht |
|---|---|---:|---:|---:|---:|---:|
| 2.3 | §2.2.1 | 1 | 0 | 1 | 0 | 0 |
| 2.3 | §2.5.1 | 2 | 1 | 1 | 0 | 0 |
| 2.3 | §2.5.2 | 2 | 0 | 2 | 0 | 0 |
| 2.3 | §2.5.3 | 3 | 2 | 1 | 0 | 0 |
| 2.3 | §2.7.2 | 1 | 0 | 0 | 1 | 0 |
| 2.3 | §2.7.4 | 1 | 0 | 0 | 1 | 0 |
| 2.3 | §2.8 | 3 | 0 | 2 | 0 | 1 |
| 2.3 | §2.8.2 | 5 | 0 | 4 | 0 | 1 |
| 2.3 | §2.8.12.2 | 5 | 1 | 4 | 0 | 0 |
| 2.3 | §2.12 | 6 | 0 | 4 | 0 | 2 |
| 2.3 | §2.13 | 6 | 0 | 6 | 0 | 0 |
| 2.3 | §2.14 | 5 | 4 | 1 | 0 | 0 |
| 2.3 | §2.16 | 17 | 1 | 3 | 0 | 13 |
| 2.3 | §APPENDIX:B | 4 | 0 | 4 | 0 | 0 |
| 2.4 | §3.2.1 | 1 | 0 | 1 | 0 | 0 |
| 2.4 | §3.5.1 | 3 | 0 | 3 | 0 | 0 |
| 2.4 | §3.5.2 | 4 | 0 | 4 | 0 | 0 |
| 2.4 | §3.5.3 | 4 | 3 | 1 | 0 | 0 |
| 2.4 | §3.7.2 | 2 | 0 | 0 | 2 | 0 |
| 2.4 | §3.7.4 | 1 | 0 | 0 | 1 | 0 |
| 2.4 | §3.8 | 4 | 0 | 2 | 0 | 2 |
| 2.4 | §3.8.2 | 7 | 0 | 6 | 0 | 1 |
| 2.4 | §3.8.12.2 | 7 | 1 | 6 | 0 | 0 |
| 2.4 | §3.9.1 | 3 | 0 | 3 | 0 | 0 |
| 2.4 | §3.12 | 8 | 0 | 6 | 0 | 2 |
| 2.4 | §3.13 | 10 | 0 | 10 | 0 | 0 |
| 2.4 | §3.14 | 9 | 8 | 1 | 0 | 0 |
| 2.4 | §3.16 | 20 | 0 | 5 | 0 | 15 |
| 2.4 | §APPENDIX:D | 6 | 0 | 6 | 0 | 0 |

### 2.2 Reklassifikationen — nicht ohne Begründung aus der Compilerpflicht genommen

Die 93 `NOT_COMPILER`-Zeilen sind keine stille Streichung. Sie sind in `mappings.json` einzeln mit Grund versehen. Zusätzlich sind 22 Zeilen als `PREDEFINED` klassifiziert; sie bleiben statusmässig `COVERED`, weil der Inhalt über Predefined-Tests gebunden ist. Die Kategorien:

| Kategorie | Anzahl | Beispiele | Begründung |
|---|---:|---|---|
| `EXPLANATORY` | 64 | 2.1/3.1 EBNF-Metasyntax; Kapitelüberschriften 2.5/2.6/2.7/2.8/2.8.12/2.8.13/3.5/3.6/3.7/3.8/3.8.12/3.8.13; Figuren 2.8.13.1; Beispielmodelle 2.16 C04–C18 und 3.16 C06–C20; Beispiele 2.15 C08/C09 und 3.15 C11/C12; areAreas-Beispiele 2.12 C02/C03 und 3.12 C03/C04 | Die Passage beschreibt Notation, Figuren oder ein Beispiel, nicht eine selbständige prüfbare Sprachregel. Die eigentliche Regel steht im selben Kapitel und ist über die dort genannten Tests gebunden. |
| `LATER_EXECUTION` | 25 | 2.8.12.1, 2.8.13.1, 3.8.12.1, 3.8.13.1; 3.2.1/3.2.2; 4.2.1/4.2.2 | Geometrische Glattheit/Stetigkeit/Flächenzusammenhang sowie Transfer-Ableitbarkeit und Lesen erweiterter Modelle sind Laufzeit-/Validierungssemantik (P4), nicht Compilerprüfung. Der Compiler prüft nur Typ, Form, Domain, Kardinalität und Toleranz. |
| `OUTSIDE_COMPILER` | 4 | 2.3 Anhang D (C01–C04) | Anhang D ist laut Norm ausdrücklich **nicht normativer** Standard-Erweiterungsvorschlag. |
| `PREDEFINED` | 22 | 2.8.7 C02/C03, 2.8.12.2 C02, 2.9.1 C02, 2.10.3 C02, 2.14 C02–C05, 3.8.7 C02/C03, 3.8.12.2 C02, 3.10.3 C02, 3.14 C02–C09 | Predefined-Inhalte (Strukturen, Einheiten, Funktionssignaturen), keine eigenständige Regel. Sie sind – soweit verwendet – über `PredefinedTemporalContractTest`, `ComposedUnitContractTest`, `BuiltinSignatureFoundationContractTest` und `MetadataBindingContractTest` gebunden. |

Beispiele tatsächlich falsch als `COMPILER_RULE` geführter Zeilen:

| Bedingung | Zitat (Auszug) | Tatsächliche Klasse |
|---|---|---|
| `NORM:2.3:2.1:C02` | „Auswahl a \| b \| c … Option [ a ] … Fakultative Wiederholung { a }“ | EBNF-Metasyntax, keine Sprachregel. |
| `NORM:2.3:2.8.12.1:C03` | „Figur 13: Beispiele von ebenen Mengen, die nicht Linienzüge sind …“ | Figurenerklärung/Laufzeitgeometrie. |
| `NORM:2.3:2.8.13.1:C05` | „Figur 23: Nicht erlaubte Linien von Flächen.“ | Figurenerklärung/Laufzeitgeometrie. |
| `NORM:2.4:3.8.13.1:C07` | „Abbildung 23: Nicht erlaubte Linien von Flächen.“ | Figurenerklärung/Laufzeitgeometrie. |
| `NORM:2.3:2.16:C18` | „END GrafikPlus. INTERLIS 2 - Referenzhandbuch … 76“ | Beispiel-/Seitenfragment. |
| `NORM:2.4:3.16:C20` | „Seite 97 von 240 … END PunktGrafikPlus; END PunktGrafikenPlusTop;“ | Beispiel-/Seitenfragment. |
| `NORM:2.3:APPENDIX:D:C01` | „Die folgende Spezifikation ist nicht normativer Bestandteil von INTERLIS.“ | Ausdrücklich nicht normativ. |

**Nicht** reklassifiziert wurden die fachlich strittigen oder laufzeitnahen Stellen, die der Auftrag ausdrücklich im Compiler belässt: OID/NO OID, CONTRACTED, Numerik/CIRCULAR, Toleranz, AREA/SURFACE, Sicht-/Grafikregeln, Constraints und Ausdrücke. Sie bleiben `SEMANTIC_RULE`, auch wenn sie nur teilweise assertiert sind.

### 2.3 Die fünf echten GAP-Bedingungen

| Bedingung | Abschnitt | Befund |
|---|---|---|
| NORM:2.3:2.7.2:C01 | 2.3 §2.7.2 | Keine JUnit-Assertion fuer AGGREGATION/COMPOSITION. Probe RuleProbe zeigt ausserdem eine reale Normverletzung: eine Aggregation (-<>) in einer dreirolligen Assoziation wird akzeptiert, obwohl 2.7.2 nur zweirollige Aggregationen erlaubt (F1). |
| NORM:2.3:2.7.4:C01 | 2.3 §2.7.4 | ORDERED wird als RoleSymbol.ordered gespeichert, aber keine Testmethode assertiert eine ORDERED-Rolle oder ihre Transferreihenfolge. roleComplete enthaelt zwar a4_0 (ORDERED), wird aber nicht als JUnit-Fall ausgewertet. |
| NORM:2.4:3.7.2:C01 | 2.4 §3.7.2 | Keine JUnit-Assertion. Probe RuleProbe: dreirollige Aggregation wird akzeptiert (F1). |
| NORM:2.4:3.7.2:C02 | 2.4 §3.7.2 | Keine JUnit-Assertion fuer die Aggregations-/Kompositionsstaerke; dreirollige Aggregation wird akzeptiert (F1). |
| NORM:2.4:3.7.4:C01 | 2.4 §3.7.4 | ORDERED-Rollen nicht assertiert. |

**Wichtig:** Die beiden GAP-Abschnitte 2.7.2/3.7.2 sind nicht nur „ungetestet“. Die eigene Probe zeigt eine **reale Normverletzung**: eine Aggregation `-<>` in einer dreirolligen Assoziation wird akzeptiert, obwohl die Norm Aggregationen nur in zweirolligen Beziehungen erlaubt. Das ist eine unerkannte Implementierungslücke im Sinne von AC4-01.

### 2.4 Zwei reale, reproduzierte Implementierungslücken (eigene Gegenproben)

Die Proben liegen unter `build/review/L4-audit/probes/`; Quellcode und Rohausgabe sind erhalten.

#### F1 — Aggregation in einer dreirolligen Assoziation wird akzeptiert

Norm 2.3 §2.7.2 / 2.4 §3.7.2:

> „Aggregationen sind nur in Beziehungen mit zwei Rollen erlaubt.“

Probe `RuleProbe` (Java, öffentliche Compiler-API):

```ili
INTERLIS 2.3;
MODEL M AT "https://example.org" VERSION "1" =
  TOPIC T = CLASS A = END A; CLASS B = END B; CLASS C = END C;
    ASSOCIATION X = w -<> B; q -- A; r -- C; END X;
  END T;
END M.
```

Tatsächliche Ausgabe:

```
== aggregation in three-role association [2.3] => Succeeded
```

Erwartet: `Rejected` mit einer Aggregations-/Rollen-Diagnose. Das Modell ist nach der zitierten Norm ungültig. Die gleiche Probe zeigt, dass `-<#>` mit `{2}`/`{0..2}` korrekt abgelehnt wird (`COMPOSITION_CARDINALITY`) und zwei Aggregationsrollen korrekt abgelehnt werden (`MULTIPLE_AGGREGATION`). Die Lücke ist damit eng: **nur die Rollenanzahl der Aggregation**.

#### F2 — Assoziation zu einer Klasse ausserhalb eines Themas wird akzeptiert

Norm 2.3 §2.5.3:

> „Klassen, die ausserhalb von Themen (also direkt im Modell) definiert sind, dürfen keine Referenzattribute enthalten. Es ist auch nicht zulässig, Assoziationen zu solchen Klassen zu definieren.“

Probe `RuleProbe`:

```ili
INTERLIS 2.3;
MODEL M AT "https://example.org" VERSION "1" =
  CLASS A = END A;
  TOPIC T = CLASS B = END B;
    ASSOCIATION X = r -- A; s -- B; END X;
  END T;
END M.
```

Tatsächliche Ausgabe:

```
== association from class outside topic [2.3] => Succeeded
```

Erwartet: `Rejected`. Die gleiche Probe zeigt, dass ein Referenzattribut auf eine solche Klasse korrekt abgelehnt wird (`REFERENCE_ATTRIBUTE_CLASS`). Die Lücke betrifft damit **Assoziationen** zu modellweiten Klassen, nicht Referenzattribute.

Beide Befunde sind minimale, reproduzierbare Gegenfälle. Sie sind **keine** blosse Evidenzlücke und dürfen nicht als `CANDIDATE_ONLY` verbucht werden.

### 2.5 Weitere festgestellte Evidenzlücken ohne aktuell belegten Fehler

Die folgenden Verträge funktionieren in der eigenen Probe, sind aber in keiner JUnit-Methode öffentlich assertiert. Sie bleiben AC4-01-Lücken, auch wenn die Implementierung im Probefall korrekt erscheint:

| Vertrag | Probe | Ergebnis | Fehlende Assertion |
|---|---|---|---|
| `AGGREGATION`/`COMPOSITION`-Bindung | `AuditProbe` | `Agg.g aggregation=AGGREGATION`, `Comp.c aggregation=COMPOSITION`, `Comp.c cardinality={1}` | Keine JUnit-Assertion für `RoleSymbol.aggregation()`. |
| `ORDERED`-Rolle | `AuditProbe` | `Ord.o ordered=true` | Keine JUnit-Assertion für `RoleSymbol.ordered()`. |
| CIRCULAR-Enum-Erweiterung | `AuditProbe` | `ENUM_CIRCULAR` und `ENUM_VALUE_UNIQUE` | Keine JUnit-Assertion für `ENUM_CIRCULAR`/`ENUM_ORDER`. |
| Operator-Präzedenz | `PrecedenceProbe` | `OR(#true, AND(y,z))` | Keine JUnit-Assertion der Baumform. |

Die Proben belegen, dass diese vier Punkte **nicht** als Implementierungsfehler zu melden sind; sie belegen aber, dass die behauptete Vollständigkeit nicht durch ausführbare Tests gedeckt ist.

### 2.6 Grammatikzweige und Builtins

- **Grammatikzweige:** Es gibt Parser-/Fragmenttests für zahlreiche Zweige, aber keinen Nachweis, der die 369 Bedingungen oder die Grammatikproduktionen systematisch auf ausgeführte Zweige abbildet. Die vorhandene Abdeckung ist stichprobenartig. Ein „alle Regeln grün“ ist damit nicht belegbar.
- **Builtins:** `BuiltinSignatureFoundationContractTest` prüft alle in der jeweiligen Version verfügbaren Builtins auf Signatur und gebundene Aufrufe; `AreaFunctionContractTest`, `E4EnumOperatorContractTest`, `UndefinedFunctionContractTest` und `ConvertUnitFunctionContractTest` ergänzen Familien. Die **Laufzeitbedeutung** von `areAreas`/`areAreas2/3`/`objectCount` wird normativ nicht ausgeführt; das ist zulässig, muss aber als Grenze sichtbar bleiben. Die 21-Builtin-Aussage ist damit typseitig, nicht laufzeitseitig belegt.

### 2.7 AC4-01-Urteil

**AC4-01 ist verletzt.** Begründung:

1. 5 Bedingungen haben keine öffentliche Assertion; zwei davon enthalten zusätzlich eine reale Normverletzung (F1, F2).
2. 87 Bedingungen sind nur teilweise assertiert; die Teilung ist in `mappings.json` je Bedingung ausgewiesen.
3. Die 369er-Liste ist als Paragraph-Cluster-Worklist angelegt und enthält 93 Zeilen ohne Compilerpflicht. Auch nach deren berechtigter Reklassifikation bleiben 92 offene/teilweise Compilerbedingungen.
4. `CANDIDATE_ONLY` wurde an keiner Stelle als Nachweis übernommen.

---

## 3. AC4-03 — die 12 noch nicht ausdrücklich entschiedenen historischen Fälle

### 3.1 Vorgehen und Abgrenzung

Das aktuelle Manifest `docs/evidence/conformance/normative-expectations.json` enthält **21** Fälle: die **19 historischen** Objekte plus zwei SET-Fälle. Ausdrücklich entschieden sind:

- **6 Fälle** `LEGACY_OID_23_WITHOUT_CONTRACTED` (2.3 `OID AS`/`BASKET OID AS` ohne `CONTRACTED`): genehmigt am 29.10.2026. Es sind `ili23.Class23Test.classComplete`, `ili23.Classes23Test.class_AcceptingOidDefinition`, `ili23.Topic23Test.topicComplete`, `ili23.Topics23Test.topics_ExplicitBasketOidDefinition`, `ili23.Topics23Test.topics_ExplicitTopicOidDefinition` und `ili23.ViewableGetAttributesAndRoles2Test.baseClass`.
- **1 Fall** `ili23.Attribute23Test.attributeComplete` (CIRCULAR): mit der bestätigten Numerik-/CIRCULAR-Regel am 30.10.2026 ausdrücklich freigegeben.
- **2 SET-Fälle** (N3-Kontextprofil): bestätigt.
Die sechs OID-Fälle stützen sich auf 2.3 §2.5.2: „Definitionen betreffend Objektidentifikationen (BASKET OID AS, OID AS) sind nur im Rahmen von Kontrakten zulässig.“ Die Freigabe ist eine bewusste Bestandskompatibilitäts-Abweichung; sie ist **keine** rückwirkende Normbestätigung. Der CIRCULAR-Fall stützt sich auf 2.3 §2.8.5: „Ist das Attribut als eine kontinuierliche Unterteilung des Vorgängerattributs definiert … muss der Typ als zirkulär definiert sein.“ Dort bleibt der Compiler normstreng; die Referenz akzeptiert das ungültige Original.

- **Weitere bestätigte Profile**, die nicht zu den 12 gehören und hier nicht neu bewertet werden: TYPE MODEL mit Strukturen/abstrakten Klassen (TOPIC verboten), das breitere numerische Profil (gleiche/absteigende/gemischte Exponenten, allgemeine Konstanten/Einheitenfaktoren), kontinuierliche Unterteilungen ohne bestimmbaren Faktor, `areAreas2/areAreas3`, N2-OBJECTS-Parameterfunktionen und die N3-SET-Regel. Quellen: `docs/spec/15-entscheidungen-risiken-und-quellen.md`, `docs/g2/STATUS.md`, `docs/g2/implementation/L1-2.md`. Sie bleiben als bewusste Nutzerentscheidungen ausserhalb dieses Vollständigkeitsabgleichs.

Damit bleiben **12** historische Fälle ohne ausdrückliche Entscheidung. Für diese wurden die im Repo vorhandenen **positiven Kontrollfassungen** gelesen und – soweit eine rekonstruierbare Kontrolle vorliegt – die im `README.md` dokumentierten Korrekturen **rückwärts** angewandt, um die Originalsituation wiederherzustellen. Die so rekonstruierten Originale liegen unter `build/review/L4-audit/probes/originals/` und wurden mit dem gebauten CLI (`check --root … --json`) tatsächlich ausgeführt. Die Originaldateien selbst sind nicht im Repository; die Rekonstruktion ist deshalb als solche gekennzeichnet.

**Wichtig:** Die im alten Entscheidungspaket `g2-normative-decision-packet-2026-10-28.md` abgedruckten Kurzzitate der ersten sechs OID-Fälle waren bekanntermassen unzureichend. Für diesen Bericht wurden die exakten Absätze aus `g2-norm-conditions.json` verwendet; die Zitate unten sind daraus wörtlich übernommen.

### 3.2 Ergebnis in einem Satz

**11 der 12 Fälle benötigen keine neue normative Ausnahme; 1 Fall braucht eine echte Nutzerentscheidung.** In **11 Fällen akzeptiert der Referenzcompiler ein nach der Norm ungültiges Modell**; unser Compiler lehnt es richtig ab (10 davon durch eigene Rekonstruktion ausgeführt, `roadsExgm2ien` manifestbasiert). Im **12. Fall** (`overlapTooSmallFail`) ist die Sachlage anders: Die 2.3-Norm verlangt nur eine Toleranz `> 0`, nennt aber keine Präzisionsuntergrenze; die Referenz wendet eine strengere Stellenzahlregel an. Das ist eine echte Normfrage, keine blosse Referenzabweichung. Die Entscheidung ist daher: **bei den 11 Referenzdefekten eigene Ablehnung beibehalten, keine Ausnahme erweitern; beim Toleranzfall die Originaldatei beschaffen und die Präzisionsfrage ausdrücklich entscheiden.** Von den 19 historischen Fällen bleiben damit die sechs genehmigten OID-Fälle und der genehmigte CIRCULAR-Fall als bewusste Abweichungen; 11 sind Referenzdefekte, 1 ist offen.

### 3.3 Fallübersicht

| Fall | Version | Ist (Probe) | Referenz | Empfehlung |
|---|---|---|---|---|
| `ili23.Association23Test.association_AssociationAsTarget` | 2.3 | REFERENCE_ASSOCIATION_OID: association target a2b requires its own OID | accept | KEEP_OWN_REJECT |
| `ili24.Attribute24Test.attributeRef_RefAttrInClass` | 2.4 | REFERENCE_TARGET_NO_OID: reference target ClassA has NO OID | accept | KEEP_OWN_REJECT |
| `ili23.Assoc23Test.assocComplete` | 2.3 | ASSOCIATION_CARDINALITY_OID fuer assocA3/assocB3 | accept | KEEP_OWN_REJECT |
| `ili23.Role23Test.roleComplete` | 2.3 | ROLE_HIDING_CONFLICT fuer assocA2p/assocB2p | accept | KEEP_OWN_REJECT |
| `ili23.Expressions23Test.expressions_ParentPathElement` | 2.3 | ILI-SEM-0005 TEXT*20 -> TEXT*10 | accept | KEEP_OWN_REJECT |
| `ili23.View23Test.view_AcceptBasicViewProjectionDef` | 2.3 | ILI-SEM-0005 TEXT -> TEXT*80 | accept | KEEP_OWN_REJECT |
| `ili23.InterlisConversion23Test.iliconversion_CheckViewDefWithALLOF` | 2.3 | ILI-SEM-0005 NUMERIC -> BOOLEAN | accept | KEEP_OWN_REJECT |
| `ili23.Models23Test.models_DomainAndRuntimeParam_SameName` | 2.3 | PARAMETER_CONTRACTED fuer param | accept | KEEP_OWN_REJECT |
| `ili23.RuntimeParameter23Test.runtimeParameterComplete` | 2.3 | PARAMETER_CONTRACTED fuer paramA1/paramA3/paramB1/paramB3 | accept | KEEP_OWN_REJECT |
| `ili23.Expressions23Test.expressions_ThisAreaThatArea` | 2.3 | VIEW_ALL_OF: ALL OF is not allowed in UNION or AREA INSPECTION | accept | KEEP_OWN_REJECT |
| `ili23.LineType23Test.overlapTooSmallFail` | 2.3 | zero-Kontrolle: Rejected OVERLAP_POSITIVE; Original nicht im Repo; Manifest: eigene akzeptiert, Referenz lehnt ab | reject | PENDING_USER_NORM_DECISION |
| `ili24.Refhb24Test.roadsExgm2ien` | 2.4 | 51 Diagnosen, ueberwiegend REFERENCE_TARGET_NO_OID auf NO-OID-Ziele | accept | KEEP_OWN_REJECT fuer die NO-OID-Referenzen; Parameter-/Grafikreferences separat pruefen. Keine pauschale Ausnahme. |

### 3.4 Fallbezogene Normstellen, Original-/Kontrollbedingung und Empfehlung

#### 1. `ili23.Association23Test.association_AssociationAsTarget` (2.3, D2)

- **Original:** `ASSOCIATION a2b = …` ohne `OID`; `a2b` wird in einer zweiten Assoziation als Rollenziel verwendet.
- **Ist:** `Rejected`, `REFERENCE_ASSOCIATION_OID` — „association target a2b requires its own OID“.
- **Exakte Norm:** 2.7.1 Abs. 4: „Wird eine eigene Objektidentifikation verlangt, kann die Beziehung auch selbst wieder in Rollen als Bezug verwendet werden.“
- **Kontrolle:** `a2b (OID)` wird akzeptiert.
- **Empfehlung:** `KEEP_OWN_REJECT`. Die Referenz akzeptiert eine Assoziation ohne eigene OID als Rollenziel. Das ist ein Referenzdefekt, keine Abweichung unseres Compilers. Keine neue Ausnahme.

#### 2. `ili24.Attribute24Test.attributeRef_RefAttrInClass` (2.4, D2)

- **Original:** `CLASS ClassA = END ClassA;` ohne OID; `CLASS ClassB = attr : REFERENCE TO ClassA;`.
- **Ist:** `Rejected`, `REFERENCE_TARGET_NO_OID` — „reference target ClassA has NO OID“.
- **Exakte Norm:** 3.5.3 Abs. 1: „Fehlt die Definition auch beim Thema, gilt implizit NO OID. … Als Folge ist es weder möglich, Objekte dieser Klasse inkrementell nachzuliefern, noch Referenzen (Beziehungen, Beziehungsattribute) auf diese Klasse zu definieren.“
- **Kontrolle:** `ClassA` mit `OID AS INTERLIS.STANDARDOID` wird akzeptiert.
- **Empfehlung:** `KEEP_OWN_REJECT`. Referenzdefekt; keine Ausnahme.

#### 3. `ili23.Assoc23Test.assocComplete` (2.3, D2)

- **Original:** `assocA3`/`assocB3` haben `CARDINALITY = {1..3}`, aber kein `OID`.
- **Ist:** `Rejected`, `ASSOCIATION_CARDINALITY_OID` für beide.
- **Exakte Norm:** 2.7.1 Abs. 4: „Mehrere Beziehungsinstanzen zwischen derselben Objektkombination sind nur zulässig, wenn für die Beziehung explizit eine Kardinalität mit Obergrenze grösser eins verlangt wird (CARDINALITY). In diesem Fall muss auch eine Objektidentifikation (mittels Property OID) verlangt werden.“
- **Kontrolle:** OID an `assocA3`/`assocB3`; die Kontrolle wird – wegen der bereits genehmigten 2.3-OID-Kompatibilität – akzeptiert.
- **Empfehlung:** `KEEP_OWN_REJECT`. Die Referenz akzeptiert einen klaren Normverstoss. Die OID/CONTRACTED-Kompatibilität darf **nicht** still auf die Kardinalitäts-OID-Pflicht ausgedehnt werden.

#### 4. `ili23.Role23Test.roleComplete` (2.3, D2)

- **Original:** `assocA2p` setzt `HIDING`; die Geschwisterrolle `assocA2q` setzt es nicht.
- **Ist:** `Rejected`, `ROLE_HIDING_CONFLICT` für `assocA2p`/`assocB2p`.
- **Exakte Norm:** 2.7.1 Abs. 2: „Dies ist allerdings nur zulässig, wenn für K1 keine anderen aus A erweiterten Beziehungen definiert sind, bei denen die Rolle K nicht als verdeckend gekennzeichnet ist.“
- **Kontrolle:** Auch `assocA2q`/`assocB2q` setzen `HIDING`; dann akzeptiert.
- **Empfehlung:** `KEEP_OWN_REJECT`. Eine nicht verdeckende Geschwisterrolle existiert; HIDING ist normwidrig. Referenzdefekt.

#### 5. `ili23.Expressions23Test.expressions_ParentPathElement` (2.3, C4)

- **Original:** `Attr2 : TEXT*10 := PARENT->attr;`, wobei `attr : TEXT*20` ist.
- **Ist:** `Rejected`, `ILI-SEM-0005` mit `TEXT*20`/`TEXT*10`.
- **Exakte Norm:** 2.6.1 Abs. 2: „Wird der Attributwert mittels eines Faktors festgelegt, muss dessen Ergebnistyp zuweisungskompatibel zum definierten Attribut sein, d.h. es muss den gleichen Wertebereich oder einen erweiterten, d.h. spezialisierten Wertebereich aufweisen.“
- **Kontrolle:** `Attr2 : TEXT*20`; dann akzeptiert.
- **Empfehlung:** `KEEP_OWN_REJECT`. Die Referenz akzeptiert eine unzulässige Verengung.

#### 6. `ili23.View23Test.view_AcceptBasicViewProjectionDef` (2.3, C4)

- **Original:** `FUNCTION makeConstant(text : TEXT):TEXT;` und `Attr3 : TEXT*80 := makeConstant(...)`.
- **Ist:** `Rejected`, `ILI-SEM-0005` mit `TEXT`/`TEXT*80`.
- **Exakte Norm:** 2.8.1 Abs. 3: „… Werden nur die Schlüsselwörter TEXT oder MTEXT angegeben, ist die Anzahl Zeichen unbeschränkt. Im Rahmen einer Erweiterung kann die Länge verkürzt werden (eine Verlängerung würde zu einem Wertebereich führen, der mit dem Basis-Wertebereich nicht mehr verträglich ist).“
- **Kontrolle:** Rückgabetyp `TEXT*80`; dann akzeptiert.
- **Empfehlung:** `KEEP_OWN_REJECT`. Unbeschränktes `TEXT` ist nicht in `TEXT*80` zuweisbar.

#### 7. `ili23.InterlisConversion23Test.iliconversion_CheckViewDefWithALLOF` (2.3, C4)

- **Original:** `b : BOOLEAN := ClassDef6->attr4;`, wobei `attr4 : 1 .. 9999` numerisch ist.
- **Ist:** `Rejected`, `ILI-SEM-0005` mit `NUMERIC`/`BOOLEAN`.
- **Exakte Norm:** 2.13 Abs. 1: „Bei einem logischen Ausdruck (Logical-Expression) muss der Ergebnistyp Boolean sein.“
- **Kontrolle:** Zuweisung von `attr3` (BOOLEAN); dann akzeptiert.
- **Empfehlung:** `KEEP_OWN_REJECT`. Numerisch ist nicht boolesch zuweisbar.

#### 8. `ili23.Models23Test.models_DomainAndRuntimeParam_SameName` (2.3, E2)

- **Original:** Modell ohne `CONTRACTED`; `PARAMETER param : BOOLEAN;` und `DOMAIN param = TEXT*2;`.
- **Ist:** `Rejected`, `PARAMETER_CONTRACTED` für `param`.
- **Exakte Norm:** 2.11 Abs. 2: „Da Laufzeitparameter Voraussetzungen an die Systeme definieren, die über die Sprache INTERLIS 2 hinaus gehen, dürfen sie nur innerhalb von Modellen definiert werden, für die Kontrakte abgeschlossen sind.“
- **Kontrolle:** `CONTRACTED` vor dem Modellkopf; dann akzeptiert.
- **Empfehlung:** `KEEP_OWN_REJECT`. Der Namespace-Konflikt ist ein separates, bereits durch `ParameterClassFunctionContractTest.parameterNamespacesBoundTypesAndLanguageReferencesAgree` belegtes Thema; das Original ist aus einem anderen Grund ungültig. Referenzdefekt.

#### 9. `ili23.RuntimeParameter23Test.runtimeParameterComplete` (2.3, E2)

- **Original:** Zwei Modelle ohne `CONTRACTED` mit `PARAMETER`-Deklarationen.
- **Ist:** `Rejected`, `PARAMETER_CONTRACTED` für alle vier Parameter.
- **Exakte Norm:** 2.11 Abs. 2 (wie oben).
- **Kontrolle:** `CONTRACTED` vor beiden Modellköpfen; dann akzeptiert.
- **Empfehlung:** `KEEP_OWN_REJECT`. Referenzdefekt.

#### 10. `ili23.Expressions23Test.expressions_ThisAreaThatArea` (2.3, F1)

- **Original:** `AREA INSPECTION` mit `ALL OF BoFlaeche_Geometrie_Line;`; `THISAREA`/`THATAREA` bleiben.
- **Ist:** `Rejected`, `VIEW_ALL_OF` — „ALL OF is not allowed in UNION or AREA INSPECTION“.
- **Exakte Norm:** 2.15 Abs. 7: „Im Sinne einer Schreiberleichterung wird zusätzlich die Möglichkeit angeboten, alle Attribute einer Sichtbasis in der gleichen Reihenfolge zu übernehmen (ALL OF). Dies ist bei Vereinigungen und bei AREA-Inspektionen unsinnig und darum auch unzulässig.“
- **Kontrolle:** Nur das `ALL OF` entfernt; `THISAREA`/`THATAREA` bleiben und werden akzeptiert.
- **Empfehlung:** `KEEP_OWN_REJECT`. Referenzdefekt.

#### 11. `ili24.Refhb24Test.roadsExgm2ien` (2.4, F3)

- **Ist (Manifest, nicht neu ausgeführt):** eigener Compiler lehnt ab, Referenz akzeptiert; 51 Diagnosen betreffen Referenzen auf NO-OID-Ziele.
- **Exakte Norm:** 3.5.3 Abs. 1 (wie Fall 2); zusätzlich 3.6.3 für Referenzziele und 3.10.2.2/3.16 für Parameter.
- **Kontrolle:** Die im Repo liegende `roadsExgm2ien`-Fassung ist bereits die korrigierte Kontrolle; sie ergänzt OID-Deklarationen, ändert aber keine Grafik-/Parameterstruktur. Das Original ist nicht im Repo und wurde in diesem Vorab-Audit nicht neu beschafft.
- **Empfehlung:** `KEEP_OWN_REJECT` für die NO-OID-Referenzen; die Parameter-/Grafikreferences sind separat zu prüfen. Die Kontrollfassung ist keine allgemeine Freigabe der 49/51-Diagnosen. **Keine pauschale Ausnahme.** Wegen der fehlenden Originaldatei bleibt dieser Fall in der Entscheidungsreife hinter den 11 byte-rekonstruierten Fällen zurück.

#### 12. `ili23.LineType23Test.overlapTooSmallFail` (2.3, C3)

- **Original:** Die Upstream-Datei `test/data/ili23/lineType/OverlapTooSmallFail.ili` (SHA-256 `bea6bc…`) liegt nicht im Repository. Die Repo-Datei `testdata/conformance/normative/ili23.LineType23Test.overlapTooSmallFail.zero.ili` ist die **negative Kontrolle** (`> 0.0000`), nicht das Original.
- **Ist (zero-Kontrolle):** `Rejected`, `OVERLAP_POSITIVE` — „overlap tolerance must be greater than zero“. Die positive Kontrolle `> 0.001` wird akzeptiert.
- **Manifest:** eigene Erwartung historisch `accepted`, Referenz `reject`; der Fall ist als „user-authorized reference exception“ vom 25.10.2026 markiert, mit ausdrücklich offenem normativem Review.
- **Exakte Norm:** 2.8.12.2 Abs. 3: „Die Angabe der Toleranz muss in den gleichen Einheiten, wie die der Stützpunktkoordinaten erfolgen. Sie muss aus numerischen Gründen grösser null sein.“
- **Normfrage:** Die 2.3-Stelle nennt nur `> 0`. Die Referenz scheint zusätzlich eine Präzisions-/Stellenzahlregel anzuwenden, die in 2.3 nicht ausdrücklich steht (die explizite Regel steht in 2.4). Ob 2.3 diese Untergrenze implizit verlangt, ist mit der vorhandenen Evidenz nicht entschieden.
- **Empfehlung:** `PENDING_USER_NORM_DECISION`. Original beschaffen, tatsächlichen Toleranzwert und Referenzdiagnose feststellen. Dann entscheiden: (a) 2.3 nur `> 0` (eigene Akzeptanz beibehalten), oder (b) 2.4-Präzisionsregel auch für 2.3 anwenden (eigene Ablehnung). Keine stille XFAIL-Freigabe, keine automatische Übernahme der 2.4-Regel.

### 3.5 AC4-03-Urteil

**AC4-03 ist entscheidungsreif vorgelegt, aber nicht „abgenommen“.** Die 12 Fälle sind mit exakten Normstellen, Original-/Kontrollbedingung und – soweit möglich – tatsächlicher Probe belegt. In 11 Fällen ist die Entscheidung **keine Normabweichung**, sondern die Feststellung eines Referenzdefekts. In 1 Fall (`overlapTooSmallFail`) liegt eine echte, noch nicht entschiedene Normfrage vor; sie ist mit beiden Optionen und den jeweiligen Folgen beschrieben. Der Nutzer muss daher (a) die 11 Referenzdefekte als solche bestätigen und (b) die Toleranzfrage entscheiden. Eine pauschale Freigabe der 12 ist nicht zulässig.

---

## 4. Die 80 offenen Anforderungen

Von den 80 Anforderungen aus `g2-test-candidate-index.json` sind nach Prüfung der tatsächlichen Methoden:

| Status | Anzahl |
|---|---:|
| `COVERED` | 51 |
| `PARTIAL` | 24 |
| `GAP` | 5 |

Die 5 GAPs sind:
- `SYN-009` — wiederholte fehlerhafte Eingaben/tiefe Verschachtelung ohne eigenen Test.
- `SYN-012` — Build-/Reproduzierbarkeitsnachweis nicht als JUnit-Vertrag.
- `INC-006` — kein Parse-Aufrufzähler für Wiederverwendung.
- `INC-014` — keine Eviction-/Cachelimit-Prüfung.
- `INC-017` — kein Benchmark-/Gleichheitsnachweis vor Optimierungsaktivierung.

Die 24 `PARTIAL`-Fälle sind in der Tabelle einzeln mit dem fehlenden Teil benannt. Die vollständige Tabelle:

| ID | Status | Beleg (oeffentliche Assertion) |
|---|---|---|
| SRC-001 | COVERED | QueryOwnership.oldRefIsRejectedByNewSnapshotAndGraphIsReadOnly — old SymbolRef in new snapshot throws SnapshotMismatchException<br>ImportBindingContract.staleTargetRevisionIsRejected — stale target revision rejected<br>SourceMetadataContract.annotationsBindToActualDeclarationKindsAndPreserveTheirExactOrigin — revision-bound origin ranges<br>SourceText.sliceRejectsForeignRevisionAndInvalidOffsets — foreign revision rejected |
| SRC-006 | COVERED | CanonicalSourceOrder.sourceMapOrderDoesNotChangeSnapshotOrDiagnostics — source map order does not change snapshot/diagnostics<br>QueryOwnership.sourceInsertionOrderDoesNotChangeSemanticResult — insertion order does not change semantic result |
| SRC-007 | COVERED | FingerprintContract.sameSemanticInputHasSameSnapshotIdAndChangedContentChangesIt — same content same snapshot, changed content changes it<br>SourceText.contentFingerprintIsStableAndExact — stable content fingerprint |
| SYN-001 | COVERED | CompilerEngine.trailingContentRejectsCompilation — trailing content rejected<br>SyntaxContract.rejectsTrailingContentAndUnsupportedVersion — trailing content and unsupported version |
| SYN-003 | COVERED | VersionScanner.detectsUnsupportedVersion — unsupported version detected<br>SyntaxContract.rejectsTrailingContentAndUnsupportedVersion — unsupported version diagnostic<br>VersionScope.interlis23ModelCompiles — 2.3/2.4 version scope |
| SYN-007 | COVERED | FragmentSyntaxContract.missingFragmentTokensPointToTheFragmentEnd — missing fragment token points to fragment end<br>FragmentSyntaxContract.pathsRetainAbsoluteUtf16OffsetsAfterUnicodeAndCrLfPrefixes — absolute UTF-16 offsets |
| SYN-008 | PARTIAL | ArchitectureBoundary.publicModelAndCompilerApiDoNotLeakForbiddenTypes — no ANTLR/forbidden types in public API |
| SYN-009 | GAP | — |
| SYN-011 | COVERED | SyntaxContract.editorRecoveryKeepsFollowingDeclarations — error nodes + following declarations<br>E1ProfileSafetyContract.syntaxRecoveryDoesNotHideInvalidFunctionArguments — recovery does not hide invalid arguments |
| SYN-012 | GAP | — |
| SYN-014 | PARTIAL | QueryOwnership.sourceInsertionOrderDoesNotChangeSemanticResult — insertion order<br>CanonicalSourceOrder.sourceMapOrderDoesNotChangeSnapshotOrDiagnostics — map order<br>WorkspaceIsolation.parallelWorkspacesKeepIndependentRevisionsAndAnalyses — parallel sessions |
| META-001 | COVERED | SnapshotOwnership.mutatingCallerCollectionsDoesNotChangeCompilation — caller collection mutation does not change result<br>QueryOwnership.oldRefIsRejectedByNewSnapshotAndGraphIsReadOnly — published graph read-only<br>SemanticOverlayContract.contentFingerprintsAreUnambiguousAndDeeplyImmutable — deep immutability |
| META-002 | COVERED | OutcomeContract.successRejectionAndCancellationRemainDistinct — only success has schema<br>ApiInvariant.analysisSnapshotRejectsMismatchedGraphSnapshot — mismatched snapshot rejected |
| META-004 | COVERED | QueryOwnership.oldRefIsRejectedByNewSnapshotAndGraphIsReadOnly — old SymbolRef typed failure |
| META-012 | PARTIAL | CompiledSchemaSelection.unrelatedBrokenModelStaysOutsideTheCompiledClosure — closure isolation<br>SemanticOverlayContract.overlayOnlyEditsMatchFreshAnalysisAndDoNotMutatePublishedSnapshots — immutable published snapshots |
| META-013 | COVERED | ViewContract.projectionPublishesBoundFormationAndMembers — valid view publishes schema<br>Phase2ClosureContract.abstractLineCoordFieldsAndExternalParametersStayLegalAndCanonical — view not rejected for missing exporter |
| SEM-001 | COVERED | ArchitectureBoundary.publicModelAndCompilerApiDoNotLeakForbiddenTypes — no filesystem/HTTP types in compiler/model API |
| SEM-004 | COVERED | CompilerEngine.unrelatedBrokenModelDoesNotBlock — broken unrelated model does not block<br>CompiledSchemaSelection.unrelatedSemanticErrorInSameFileDoesNotBlockSelectedModel — same-file unrelated error does not block |
| SEM-006 | COVERED | GenericContextContract.concreteAndAbstractBindingsAreDifferentFromInvalidDeferral — concrete/abstract/deferred distinct |
| SEM-007 | COVERED | EngineContract.reuseIsOptionalAndFreshSemanticsMatch — fresh/reuse same symbols and diagnostics<br>OriginalViewReuse.originalGraphsAndQueriesSurviveUnchangedCommentAndInvalidEdits — edit sequence fresh/reuse |
| SEM-009 | COVERED | ImportedTranslationContract.translationCyclesHaveNoCanonicalIdentity — translation cycle<br>InheritanceFoundationContract.cyclicTopicClassAndStructureInheritanceCannotProduceACompiledSchema — inheritance cycle |
| SEM-011 | COVERED | OriginalViewReuse.originalGraphsAndQueriesSurviveUnchangedCommentAndInvalidEdits — fresh vs reuse across edits |
| SEM-012 | COVERED | ModelHeaderContract.editsOfOnlyOptionsAgreeFreshAndReusedAndRecover — option edits invalidate<br>GenericContextContract.importsAreOrderIndependentAndFreshAnalysisMatchesReuse — import edits |
| SEM-013 | COVERED | WorkspaceIsolation.parallelWorkspacesKeepIndependentRevisionsAndAnalyses — independent workspaces/engines<br>EngineContract.reuseIsOptionalAndFreshSemanticsMatch — two engines |
| SEM-015 | PARTIAL | FunctionContextContract.attributeRelationsKeepValueErrorsSeparateFromARequiredCallContext — NotAnalyzed PENDING for call context |
| SEM-016 | COVERED | SemanticOverlayContract.overlayOnlyEditsMatchFreshAnalysisAndDoNotMutatePublishedSnapshots — overlay isolation<br>OverlayWorkspaceContract.overlayOnlyChangesInvalidateCapturesWhileCachePolicyDoesNotChangeSemantics — overlay cache invalidation |
| SEM-018 | COVERED | ArchitectureBoundary.publicModelAndCompilerApiDoNotLeakForbiddenTypes — architecture boundary |
| SEM-019 | COVERED | OriginalViewReuse.originalGraphsAndQueriesSurviveUnchangedCommentAndInvalidEdits — semantic fixture equality<br>EngineContract.reuseIsOptionalAndFreshSemanticsMatch — normalized equality |
| INC-001 | PARTIAL | BridgeDispatcher.protocolRoundTripFromOpenToDiagram — bridge/session same model<br>Workspace.compileFixAndIncrementalEquality — JVM workspace |
| INC-002 | COVERED | Workspace.batchConflictLeavesWorkspaceUnchanged — conflict leaves workspace unchanged<br>Workspace.staleDetachedAnalysisIsNotCommitted — stale analysis not committed |
| INC-003 | COVERED | Workspace.compileFixAndIncrementalEquality — no-op/fix/reuse<br>LspFeature.oldDocumentVersionsAreIgnoredAndClosingRestoresDiskText — close/reopen restores disk text |
| INC-004 | PARTIAL | EngineContract.reuseIsOptionalAndFreshSemanticsMatch — old result remains comparable<br>QueryOwnership.oldRefIsRejectedByNewSnapshotAndGraphIsReadOnly — old snapshot query |
| INC-005 | PARTIAL | SyntaxContract.editorRecoveryKeepsFollowingDeclarations — following declaration survives missing type<br>LspFeature.openChangeDiagnosticsAndQueries — editor diagnostics/queries |
| INC-006 | GAP | — |
| INC-007 | PARTIAL | OverlayWorkspaceContract.overlayOnlyChangesInvalidateCapturesWhileCachePolicyDoesNotChangeSemantics — overlay invalidation<br>ModelHeaderContract.editsOfOnlyOptionsAgreeFreshAndReusedAndRecover — option edits |
| INC-008 | COVERED | Workspace.missingImportAppearsLaterWithoutEdit — later model appears without edit<br>Workspace.missingTranslationOriginalCanBeAddedWithoutEditingTheTranslation — translation original added without edit |
| INC-009 | COVERED | Workspace.compileFixAndIncrementalEquality — normalized equality after edits<br>OriginalViewReuse.originalGraphsAndQueriesSurviveUnchangedCommentAndInvalidEdits — semantic equality |
| INC-010 | COVERED | OriginalViewReuse.originalGraphsAndQueriesSurviveUnchangedCommentAndInvalidEdits — comment/neighbor edits keep identity<br>RenameSafety.renameDoesNotTouchSameNameInAnotherTopicOrComments — rename isolation |
| INC-011 | PARTIAL | QueryOwnership.sourceInsertionOrderDoesNotChangeSemanticResult — insertion order<br>DuplicateNameContract.classAndDomainWithSameNameAreRejected — duplicate handling |
| INC-012 | COVERED | QueryOwnership.oldRefIsRejectedByNewSnapshotAndGraphIsReadOnly — cross-snapshot typed failure<br>SourceText.sliceRejectsForeignRevisionAndInvalidOffsets — cross-revision failure<br>ApiInvariant.analysisSnapshotRejectsMismatchedGraphSnapshot — mismatched snapshot |
| INC-013 | PARTIAL | CancelledAnalysis.cancelledAnalysisTerminatesWithoutSchema — cancellation no schema<br>EngineContract.cancellationIsTypedAndNeverProducesSchema — typed cancellation |
| INC-014 | GAP | — |
| INC-015 | COVERED | CompilerEngine.unrelatedBrokenModelDoesNotBlock — two roots, one invalid<br>CompiledSchemaSelection.unrelatedBrokenModelStaysOutsideTheCompiledClosure — closure isolation |
| INC-016 | PARTIAL | SemanticOverlayContract.overlayOnlyEditsMatchFreshAnalysisAndDoNotMutatePublishedSnapshots — overlay fingerprint changes result<br>OverlayWorkspaceContract.anOverlayRoundTripRestoresContentIdentityWithoutReusingOldResults — overlay content identity |
| INC-017 | GAP | — |
| INC-018 | PARTIAL | Workspace.staleDetachedAnalysisIsNotCommitted — late analysis not committed |
| INC-019 | PARTIAL | Workspace.missingImportAppearsLaterWithoutEdit — changed import stand triggers new analysis |
| INC-020 | COVERED | WorkspaceIsolation.parallelWorkspacesKeepIndependentRevisionsAndAnalyses — same-named different models in two workspaces |
| INC-021 | COVERED | Workspace.batchConflictLeavesWorkspaceUnchanged — one conflict prevents partial batch |
| INC-022 | PARTIAL | ResolutionBoundaryContract.analysisWithOtherRootSelectionCannotBeAcceptedIntoACapture — stale capture rejected<br>OverlayWorkspaceContract.overlayOnlyChangesInvalidateCapturesWhileCachePolicyDoesNotChangeSemantics — capture invalidation |
| DIAG-001 | PARTIAL | DiagnosticCatalog.initialCatalogIsStableAndUnique — codes stable<br>CliApplication.jsonOutputIsMachineReadable — CLI JSON diagnostics<br>LspServer.openChangeDiagnosticsAndQueries — LSP diagnostics |
| DIAG-002 | COVERED | DiagnosticPresentation.dependentSuppressionUsesExplicitCauseIdsNotText — cause ids not text<br>DiagnosticOrder.canonicalOrderIsIndependentOfEmissionOrder — stable order |
| DIAG-003 | COVERED | SourceText.crlfAndSupplementaryCharactersUseUtf16Offsets — CRLF/emoji offsets<br>DiagnosticOrder.canonicalOrderIsIndependentOfEmissionOrder — ordered ranges |
| DIAG-004 | PARTIAL | DiagnosticPresentation.fullListRemainsAvailableWhenPolicyDoesNotSuppress — suppressed follow-ups do not hide failure<br>E1ProfileSafetyContract.repeatedPropertiesAreVisibleWarningsRatherThanHiddenSetDeduplication — warning vs error |
| DIAG-005 | PARTIAL | ImportDiagnosticContract.missingImportProducesResolverDiagnosticAndNoSchema — missing import<br>ExpressionConstraintContract.externalConstraintCannotBindAnUnrelatedSameNamedClassOrMissingTarget — independent literal remains visible |
| DIAG-006 | COVERED | DiagnosticCatalog.initialCatalogIsStableAndUnique — catalog stable/unique<br>CliApplication.jsonOutputIsMachineReadable — JSON diagnostic shape |
| DIAG-007 | PARTIAL | OutcomeContract.successRejectionAndCancellationRemainDistinct — Rejected vs Cancelled<br>E1ProfileSafetyContract.syntaxRecoveryDoesNotHideInvalidFunctionArguments — no internal failure for user error |
| DIAG-008 | COVERED | DiagnosticOrder.canonicalOrderIsIndependentOfEmissionOrder — deterministic order/ids |
| DIAG-009 | COVERED | NumericLiteralBudgetContract.oversizedThresholdsRangesAndConstantsUseTheExistingBudgetOutcome — controlled budget<br>EngineContract.resourceLimitIsCancellationAndNotAFalseNegativeModel — RESOURCE_LIMIT typed |
| DIAG-010 | COVERED | LspFeature.renameIsRejectedWhenSourceChangedAfterAnalysis — changed document rejected<br>Workspace.staleDetachedAnalysisIsNotCommitted — stale analysis rejected |
| LOG-001 | PARTIAL | ArchitectureBoundary.publicModelAndCompilerApiDoNotLeakForbiddenTypes — dependency/API boundary |
| LOG-002 | COVERED | RunEventContract.oneOrderedTerminalEventAndIndependentObservers — ordered terminal event<br>EngineContract.cancellationIsTypedAndNeverProducesSchema — cancellation event |
| LOG-003 | COVERED | RunEventContract.throwingObserverStillGetsExactlyOneTerminalAttempt — observer error terminal attempt |
| LOG-004 | COVERED | EngineContract.failingObserverDoesNotChangeFactualResult — throwing observer does not change result |
| LOG-005 | PARTIAL | CliApplication.checkPositiveAndNegativeModels — CLI stdout/exit<br>CliApplication.jsonOutputIsMachineReadable — JSON output |
| LOG-006 | PARTIAL | LspServer.openChangeDiagnosticsAndQueries — parallel diagnostics/events<br>LspFeature.configurationAndWatchedFilesRefreshAreAccepted — operations |
| LOG-007 | COVERED | DiagnosticCatalog.initialCatalogIsStableAndUnique — schema/catalog<br>DiagnosticOrder.canonicalOrderIsIndependentOfEmissionOrder — order |
| LOG-008 | PARTIAL | RepositoryIndexReader.rejectsMissingMetadataAndMalformedXml — resolver diagnostics<br>HttpSourceLoader.mapsAccessEncodingSizeAndOffline — host errors |
| LOG-009 | COVERED | BoundedObservationQueue.loadTestKeepsBoundAndCountsDiscards — bounded queue/discard count<br>BoundedObservationQueue.drainPreservesOrder — order |
| LOG-010 | COVERED | WorkspaceIsolation.parallelWorkspacesKeepIndependentRevisionsAndAnalyses — independent instances<br>EngineContract.reuseIsOptionalAndFreshSemanticsMatch — two engines |
| API-001 | COVERED | ArchitectureBoundary.publicModelAndCompilerApiDoNotLeakForbiddenTypes — register/module/import boundary<br>PublicSdkConstruction.minimalPublicApiCompile — public API compiles |
| API-002 | COVERED | PublicSdkConstruction.minimalPublicApiCompile — public SDK construction |
| API-003 | PARTIAL | ApiInvariant.requestsRejectEmptyRootsAndMismatchedSourceKeys — constructor invariants<br>OutcomeContract.successRejectionAndCancellationRemainDistinct — outcome contract |
| API-004 | COVERED | OutcomeContract.successRejectionAndCancellationRemainDistinct — outcome matrix<br>CliApplication.checkPositiveAndNegativeModels — CLI outcome<br>BridgeDispatcher.missingModelsAndInvalidProtocolAreTyped — bridge outcome |
| API-005 | COVERED | QueryOwnership.oldRefIsRejectedByNewSnapshotAndGraphIsReadOnly — selection/ownership<br>CompiledSchemaSelection.unrelatedBrokenModelStaysOutsideTheCompiledClosure — selection<br>ModelSelectorContract.byNameWithVersionAndSourceHintSelectsOneModel — model selector |
| API-006 | PARTIAL | ArchitectureBoundary.publicModelAndCompilerApiDoNotLeakForbiddenTypes — API surface check |
| FMT-001 | COVERED | FormatterContract.formattingIsIdempotentAndCanUseLf — idempotent, LF |
| FMT-002 | COVERED | FormatterContract.formatsIndentationAndPreservesCommentsAndUnicode — comments/Unicode preserved<br>FormatterContract.multilineAndInterleavedCommentsKeepTheirTextAndPosition — multiline comments<br>FormatterContract.preservesTokensAndIsIdempotentAcrossVersionsAndLineEndings — tokens/versions |
| FMT-003 | COVERED | CliApplication.formatWritesStdoutChecksAndWritesAtomically — stdout/--check/--write/exit<br>FormatterContract.formattingIsIdempotentAndCanUseLf — API formatting |
| FMT-004 | COVERED | CliApplication.formatWritesStdoutChecksAndWritesAtomically — atomic write<br>CliApplication.formatDoesNotWriteInvalidSource — invalid source leaves file unchanged |


---

## 5. Findings

| ID | Schwere | Befund | Minimaler Beleg |
|---|---|---|---|
| F1 | **FAIL / Muss** | Aggregation `-<>` in einer dreirolligen Assoziation wird akzeptiert; Norm erlaubt Aggregationen nur in zweirolligen Beziehungen (2.7.2/3.7.2). | `RuleProbe`: `aggregation in three-role association [2.3] => Succeeded`; `probes/rule-probe.out`. |
| F2 | **FAIL / Muss** | Assoziation zu einer Klasse direkt im Modell (ausserhalb eines Themas) wird akzeptiert; 2.3 §2.5.3 verbietet sie. | `RuleProbe`: `association from class outside topic [2.3] => Succeeded`; `probes/rule-probe.out`. |
| F3 | **Muss (AC4-01)** | 5 Bedingungen ohne öffentliche Assertion; 87 nur teilweise. | `mappings.json`, Status `GAP`/`PARTIAL`; Abschnitt 2.1. |
| F4 | **Muss (AC4-01)** | `AGGREGATION`/`COMPOSITION`, `ORDERED`, `ENUM_CIRCULAR`/`ENUM_ORDER` und Operator-Präzedenz sind implementiert, aber nicht durch JUnit assertiert. | `AuditProbe` und `PrecedenceProbe`; Abschnitt 2.5. |
| F5 | **Muss (AC4-01)** | Kein systematischer Grammatikzweig-/Produktionsabgleich; Stichproben ersetzen keine Vollständigkeit. | `mappings.json` `SYNTAX_RULE`-Einträge; Abschnitt 2.6. |
| F6 | **Entscheidungsreif (AC4-03)** | 11 der 12 offenen historischen Fälle sind Referenzdefekte, kein Normbedarf; 1 Fall (`overlapTooSmallFail`) ist eine offene Normfrage. | Abschnitt 3; `probes/originals/` + CLI-Proben. |
| F7 | **Beobachtung** | Das alte Entscheidungspaket enthielt für die ersten sechs OID-Fälle unzureichende/ falsche Kurzzitate; die exakten Normstellen sind in `g2-norm-conditions.json` vorhanden und hier verwendet. | Abschnitt 3.1/3.4. |

**Kein Finding** ist die blosse Existenz von `CANDIDATE_ONLY`: Das ist ein Inventarstatus, kein Nachweis. Die Findings F1–F5 belegen die fehlende Vollständigkeit unabhängig davon.

---

## 6. Nicht ausgeführt / Grenzen

- Keine vollständige Browser-/Safari-/Web-Image-Matrix. Für diesen Vorab-Audit nicht erforderlich; sie bleibt für L4-2/AC4-06 fällig.
- Keine Conformance-Suite-Vollläufe (593/610) und keine Neuberechnung der 21 Assessments. Die 21 Manifestobjekte und die 19 historischen Byte-Pins wurden nicht verändert.
- Keine externen Originaldateien aus `ili2c` beschafft; **10 der 12 Originale** wurden aus den dokumentierten Kontrollkorrekturen rückwärts rekonstruiert und tatsächlich ausgeführt. Das ist für die Entscheidungsreife dieser 10 Fälle ausreichend, weil die Kontrollkorrekturen im Repository offen dokumentiert sind; es ist **kein** Byte-Pin-Nachweis der Upstream-Originale. Die beiden übrigen Fälle wurden nicht als Original ausgeführt: `roadsExgm2ien` (Original nicht im Repo; manifest- und kontrollbasiert) und `overlapTooSmallFail` (Original nicht im Repo; nur die zero-Kontrolle wurde ausgeführt).
- Keine WASM-/Node-Ausführung der Proben. Die Proben nutzen die öffentliche JVM-Compiler-API.
- Keine Änderung an Produkt, Tests, Build, Norm, Baseline oder Erwartungen. Kein Commit/Push/Release.

---

## 7. Gesamturteil

| Kriterium | Urteil |
|---|---|
| **AC4-01** Vollständigkeit | **VERLETZT.** 5 GAPs, 87 PARTIAL, zwei reale Normverletzungen (F1/F2). Kein PASS. |
| **AC4-03** Entscheidungsreife | **Entscheidungsreif vorgelegt.** 12 Fälle mit exakten Normstellen, Original-/Kontrollbedingung und tatsächlicher Probe; 11 Referenzdefekte, 1 offene Normfrage (`overlapTooSmallFail`). Keine pauschale Ausnahme. |
| **G2-PASS** | **Nein.** Dieser Vorab-Audit erteilt keinen Lieferungs- oder G2-PASS. |

**Berichtspfade**

- Dieser Bericht: `build/review/L4-audit/audit.md`
- Vollständige Zuordnung: `build/review/L4-audit/mappings.json`
- Eigene Proben: `build/review/L4-audit/probes/` (Java-Quellen, Rohausgaben, rekonstruierte Originale)
- Compiler-Testlauf: `build/review/L4-audit/work/compiler-test.log`
- Arbeitsinventar: `build/review/L4-audit/work/conditions-gen.json`, `requirements-gen.json`, `rules-compact.tsv`
