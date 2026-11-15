# Abweichungen der swisstopo-Validatorsuite

Stand der Originalsuite ist `f307111`. Der vollständige
Lauf für INTERLIS 2.3 und XTF 2.3 ergab 211 bestandene Originaltests und
12 bekannte Abweichungen. Alle 223 Originaltests wurden ausgeführt.
Die Originaltests, Modelle und XTF-Dateien sind unverändert.

| Originalfälle | Erwartung und beobachtetes Ergebnis | Begründung |
| --- | --- | --- |
| `RSU.T16a` | Die Suite erwartet ein freies Randende und eine Selbstüberschneidung. Der Validator akzeptiert den gelieferten Kurvenrand ohne Befund. | Auch ilivalidator 1.15.1 akzeptiert die Originaldatei. Der erwartete Befund war mit beiden Validatoren nicht reproduzierbar. Der Referenzvergleich allein entscheidet die Normfrage nicht. |
| `RSU.T18a`, `RSU.T18b`, `RSU.T18c`, `RSU.T18d`, `RSU.T18e` | Die obligatorische Bedingung mit `INTERLIS_ext.areAreas2` bleibt ungeprüft. Der Runner beobachtet `UNSUPPORTED_EXPRESSION` für genau diese Funktion. | Das Originalmodell deklariert eine externe Legacy-Funktion mit dem TEXT-Pfad `Geom->Geom1->Geom2`. Für diese Funktion fehlt im verwendeten Validator eine Laufzeitbindung. |
| `RSU.T19a`, `RSU.T19b`, `RSU.T19c`, `RSU.T19d`, `RSU.T19e` | Das `SET CONSTRAINT` mit `INTERLIS_ext.areAreas3` bleibt ungeprüft. Die Abdeckung meldet `UNSUPPORTED` für genau diese Funktion. | Auch hier fehlt die Laufzeitbindung für die externe Legacy-Funktion. Sie prüft mit `ALL` eine Objektmenge und verwendet denselben TEXT-Pfad. |
| `RPO.T02c` | Die Suite erwartet einen gültigen Linienzug. Der Validator meldet viermal `GEOMETRY_SELF_INTERSECTION` am selben Stützpunkt. | Die vertikale und die diagonale Passage kreuzen sich am wiederholten Stützpunkt. Für den als `WITHOUT OVERLAPS` definierten Linienzug gilt das Selbstschnittverbot nach §2.8.12. Die vier Meldungen bezeichnen dieselbe Kreuzung. |

Die [Abweichungsliste des Runners](../../build/interlis/validator-conformance/deviations.tsv)
enthält pro Originaltest die erwartete Beobachtung. Im nativen JUnit-Bericht
bleiben die 12 Fälle als Fehler sichtbar. Der Runner scheitert bei neuen
Fehlern, technischen Störungen, geänderten Befunden und inzwischen
bestandenen Fällen mit noch vorhandenem Eintrag. Den lokalen Aufruf
beschreibt die [Testanleitung](testing.md#swisstopo-validator-konformität).

[INTERLIS-2.3-Referenzhandbuch](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf)
