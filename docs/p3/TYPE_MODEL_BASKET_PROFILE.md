# P3-4 – neues Kompatibilitätsprofil: Referenzsystemkörbe in TYPE MODEL

**Nutzerentscheid vom 31. Oktober 2026: wie ili2c zulassen und in
COMPILER_NORM_DECISIONS.md dokumentieren. Implementiert und gezielt geprüft; unabhängige P3-4-Abnahme offen.** Die bestehenden G2-Freigaben für Strukturen und
abstrakte Klassen sowie die Verbote für Topics und konkrete Klassen bleiben
massgeblich. Es geht hier um eine zusätzliche, bisher nicht entschiedene Form.

| Regel | Norm | ili2c5.6.9 | Unser Compiler | Vorschlag |
|---|---|---|---|---|
| REFSYSTEM BASKET unmittelbar in TYPE MODEL, 2.3/2.4 | [2.3 §2.5.1, S.26](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=26); [2.4 §3.5.1, S.32](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf#page=32). Die abschliessende Aufzählung nennt Masseinheiten, Wertebereiche, Funktionen und Linienformen. | Akzeptiert die echte CHBase-Closure und die minimierten Kontrollen in beiden Sprachversionen. | Lehnt mit ILI-SEM-0010 / TYPE_MODEL_CONTENT ab. | Als ausdrückliches Bestandsprofil wie ili2c zulassen; nur diese Korbart ergänzen. |

Die offizielle unveränderte Datei CHBase_Part1_GEOMETRY_V1.ili enthält
TYPE MODEL GeometryCHLV03_V1 und GeometryCHLV95_V1 mit REFSYSTEM BASKET BCoordSys.
Der vollständige erste Indexmodellfall ist gegen ili2c mit topologisch
geordneten unveränderten Abhängigkeiten erfolgreich; unser Gate scheitert am
TYPE_MODEL_CONTENT-Befund der angeforderten GeometryCHLV95_V1-Closure.
Der erste Referenzversuch mit dem Wurzelmodell vor seinen Abhängigkeiten war
ungültig und bleibt als Fehlversuch erhalten.

Minimierung (für beide Sprachversionen geprüft):

```ili
INTERLIS 2.4;
REFSYSTEM MODEL R AT "https://example.org" VERSION "1" =
  TOPIC T =
    CLASS S EXTENDS INTERLIS.REFSYSTEM = END S;
  END T;
END R.
TYPE MODEL F AT "https://example.org" VERSION "1" =
  IMPORTS R;
  REFSYSTEM BASKET B ~ R.T OBJECTS OF S: obj;
END F.
```

Beide minimierten Referenzprozesse Exit0. Unser CLI-Prozess jeweils Exit1 mit
genau einer TYPE_MODEL_CONTENT-Diagnose auf B. Ein vorheriges Miniaturbeispiel
mit relativem Herausgeber x ist für ili2c ungeeignet und bleibt getrennt.

Eigene Rohbelege unter build/reports/p3/P3-4/corpus-exploration/:
first-reference-topological.json und type-model-basket/{reference-absolute,
ours-absolute}.json samt unveränderten Quellen/Hashes. Das sind
Entscheidungsbelege, keine neue grüne Corpusbaseline. Originalbytes, bestehende
Normausnahmen und die bisherigen dreizehn historischen Corpusfälle bleiben erhalten.

Gezielte Implementierung: Nur MetadataBasketSymbol.Kind.REFSYSTEM ist zusätzlich
zulässig. ModelKindContractTest prüft beide Sprachversionen, öffentliche
Korb-/Objekt-/Themenbezüge und Navigation sowie weiterhin verbotene SIGN BASKETs.
compiler-red-r3.log belegt die unveränderte Ablehnung vor dem Fix mit korrekten
expliziten Importbindings; compiler-green-r2.log besteht zusammen mit den
URI-/NAME-/BOOLEAN-Bindungsregressionen. Frühere ungültige Unitvorbereitungen
(fehlende Core-Importbindung beziehungsweise Java-Lambdavariablenkonflikt)
bleiben als Fehlversuche erhalten und sind keine fachlichen Red-Nachweise.
