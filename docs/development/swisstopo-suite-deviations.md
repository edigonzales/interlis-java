# Abweichungen der swisstopo-Validatorsuite

Stand der Originalsuite ist `f307111`. Der vollständige
Lauf für INTERLIS 2.3 und XTF 2.3 ergab 222 bestandene Originaltests und
eine bekannte Abweichung. Alle 223 Originaltests wurden ausgeführt.
Die Originaltests, Modelle und XTF-Dateien sind unverändert.

| Originalfälle | Erwartung und beobachtetes Ergebnis | Begründung |
| --- | --- | --- |
| `RSU.T16a` | Die Suite erwartet ein freies Randende und eine Selbstüberschneidung. Unser Validator und ilivalidator 1.15.1 akzeptieren den Originalfall. | Abweichung der Suite-Erwartung. Der Rand ist geschlossen und die geometrische Nachrechnung ergibt keinen Selbstschnitt. Siehe [Geometrie von `RSU.T16a`](#geometrie-von-rsut16a). |

Die [Abweichungsliste des Runners](../../build/interlis/validator-conformance/deviations.tsv)
enthält pro Originaltest die erwartete Beobachtung. Im nativen JUnit-Bericht
bleibt dieser Fall als Fehler sichtbar. Der Runner scheitert bei neuen
Fehlern, technischen Störungen, geänderten Befunden und inzwischen
bestandenen Fällen mit noch vorhandenem Eintrag. Den lokalen Aufruf
beschreibt die [Testanleitung](testing.md#swisstopo-validator-konformität).

## Geometrie von RSU.T16a

Der einzige Randzug von `RSU.T16a` beginnt und endet exakt bei
`(2657436.933, 1250036.269)`. Damit erfüllt er den Randschluss nach
§ 3.3.11.13. Zum Vergleich enthält `RSU.T16b` einen offenen Ausschnitt
derselben Randpartie. Seine Endpunkte liegen rund 291 m auseinander.
[Original `RSU.T16a`](https://github.com/geostandards-ch/suite-interlis/blob/f30711184d2374feacb81c5742382d35597164ca/data/RSU.T16a.xtf) ·
[Original `RSU.T16b`](https://github.com/geostandards-ch/suite-interlis/blob/f30711184d2374feacb81c5742382d35597164ca/data/RSU.T16b.xtf) ·
[INTERLIS 2.3, § 3.3.11.13](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=92)

Eine kurze Gerade zwischen zwei Kreisbögen führt beinahe auf dem
vorherigen Bogen zurück. Zwei beteiligte Stützpunkte liegen jeweils
rund 0,8 mm vom benachbarten Bogen entfernt. Die Nachrechnung der
363 Segmente, darunter 11 Kreisbögen, ergibt weder mit den gelieferten
Koordinaten noch mit auf Modellpräzision gerundeten Segmentendpunkten
einen Selbstschnitt. Der historische Grund für die Fehlererwartung
ist in der Suite nicht dokumentiert.
[Bogenfolge im Original](https://github.com/geostandards-ch/suite-interlis/blob/f30711184d2374feacb81c5742382d35597164ca/data/RSU.T16a.xtf#L1309)

§ 2.8.13.1 verlangt eine geometrisch gültige Flächenbegrenzung. Das
`WITHOUT OVERLAPS > 0.10` im Modell begrenzt nach § 2.8.12.2 die
Pfeilhöhe bestimmter tatsächlicher Überschneidungen mit einem Kreisbogen.
Für den Abstand zwischen Randstücken gibt diese Angabe keinen Mindestwert vor.
[INTERLIS 2.3, § 2.8.13.1](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=51) ·
[INTERLIS 2.3, § 2.8.12.2](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf#page=49)
