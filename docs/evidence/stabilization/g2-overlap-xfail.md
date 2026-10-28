# Explizite XFAIL-Einstufung der 2.3-Toleranzabweichung

Stand: 25. Oktober 2026. TEST-011/012, WP-22/25 / G2 C.

Auf ausdrücklichen Nutzerwunsch wird
`ili23.LineType23Test.overlapTooSmallFail` als XFAIL im Projektbericht geführt.
Die unabhängige Normprüfung bleibt offen. Die vorherige Entscheidung, den Fall
bis dahin als FAIL zu führen, ist durch diesen gezielten Auftrag ersetzt.

Die gepinnte Originalquelle und ihre negative Referenzerwartung bleiben
unverändert. Der eigene Compiler akzeptiert `0.0001` bei drei Nachkommastellen
der Koordinaten. Grundlage der eigenen Erwartung bleibt die dokumentierte
Auslegung von 2.3 §2.8.12.2, S. 50: positive, unveränderliche Toleranz in gleicher
Einheit ohne die dort nicht ausdrücklich genannte Präzisionsuntergrenze. Die
zusätzliche Stellenzahlregel in 2.4 wird nicht auf 2.3 übertragen. Diese Notiz
schliesst die unabhängige Normprüfung nicht ab.

Der vorhandene Wrapper unterstützt nun auch diese Richtung einer
Referenzabweichung. Er prüft für den neuen Eintrag:

- Originalannahme mit Exitcode 0 und leerer JSON-Diagnostik;
- Annahme der gepinnten positiven Kontrollfassung mit Toleranz `0.001`;
- Ablehnung der gepinnten Nulltoleranz-Kontrolle mit Exitcode 1, ausschliesslich
  SOURCE_ERRORS und der exakten strukturierten Diagnose `OVERLAP_POSITIVE`.

Quellenpins, Diagnosen einschliesslich Argumenten/Bereichen, Sprachversion,
ursprüngliche Referenzerwartung und Prozessresultate werden geprüft. Beliebige
Ablehnungen oder Erfolge, Warnungen, Implementierungssperren und Prozessfehler
verdienen keine Ausnahme. Der unveränderte rohe Referenzbericht bleibt FAIL;
nur die zusätzliche Projektbewertung weist den einzeln geprüften Unterschied
als XFAIL aus. Es gibt keine Änderung an der Compilersemantik.

Ausgeführt:

```sh
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem ./gradlew conformanceWrapperTest conformanceCheck --console=plain
```

**20 Wrappertests bestehen. Alle 593 ausgewählten Konformitätsfälle wurden
neu ausgeführt: 574 PASS / 19 XFAIL / 0 FAIL / 0 ERROR / 0 XPASS.**
Der Gradle-Aufruf endet erfolgreich mit Exitcode 0. Der rohe Referenzrunner endet
weiterhin mit Exitcode 1 und 574 PASS / 1 XFAIL / 18 FAIL. Die 19 Projekt-XFAIL
bestehen aus 18 Referenzabweichungen und der separaten INTERLIS-1-Ausnahme.

Die JVM-/Browser-/Web-Image-Compilerverträge wurden für diese reine
Wrapper-/Erwartungsänderung nicht erneut ausgeführt. Die Artefakthashes stimmen
mit dem vorherigen C-Nachweis (471 JVM-Tests, 1676 portable Fälle) überein.
Die Quellabstraktion zusätzlicher LineCoord-Mitglieder, D–G und die übrigen
P3-Pflichten bleiben offen. **G2 und P3 sind nicht abgeschlossen.**

[Verifikationsmanifest und Prozessausgaben](verification-2026-10-25-overlap-xfail.json),
[Fallkatalog](conformance-open-cases.json), [G2-Matrix](g2-rule-matrix.json).
Die früheren C-Berichte bleiben als historische Evidenz erhalten.
