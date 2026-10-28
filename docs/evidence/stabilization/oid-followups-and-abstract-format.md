# OID-Folgeregeln und abstrakte FORMAT-Komponenten

Stand: 20. Oktober 2026. WP-20/22; META-006/014/016, SEM-008/010/014.
S2a/S2b bleiben PARTIAL, P2/P3 und G2/G3 offen. Dieser Nachweis ergänzt den
[vorherigen Stand](format-precision-and-oid-binding.md); dessen Läufe bleiben
historisch erhalten. Insbesondere korrigiert er die dort zu weit gefasste
Übertragung der NOOID-Regeln auf 2.3.

## Fachliche Grundlage und Entscheidungen

Massgeblich sind die [2.3-Referenz](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf),
Abschnitte 2.5.2/2.5.3, 2.6, 2.8.1/2.8.5/2.8.6/2.8.9 und Anhang A, sowie die
[2.4-Referenz](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf),
Abschnitte 3.5.2/3.5.3, 3.6, 3.8.1/3.8.5/3.8.6/3.8.9 und Anhang A.

| Regel | Umsetzung und beobachtbare Abnahme |
|---|---|
| Verfügbarkeit von NOOID | `INTERLIS.NOOID` existiert nur in 2.4. In 2.3 erzeugen weder OID AS noch ein Attributtyp ein erfundenes Builtin. ANYOID hat dort keine synthetische NOOID-Basis. |
| Fehlende gegenüber expliziter Festlegung | In 2.3 kann eine noch fehlende OID-Festlegung einer Basisklasse ergänzt werden. Explizites NO OID bleibt dagegen fest. In 2.4 ist fehlende Festlegung implizites NO OID, mit dem erlaubten Weg NO OID → ANYOID → konkrete OID. |
| Topic-Vererbung | Eine bereits gesetzte Topic-OID-Zuordnung bleibt in beiden Versionen fest, auch bei ANYOID. Klassen-Konkretisierung ist keine Erlaubnis zur Änderung der Topic-Zuordnung. |
| OID-Domain-Erweiterung | Eigene konkrete OID-Domänen können ANYOID konkretisieren, aber weder NOOID direkt überspringen noch eine konkrete OID-Domäne erweitern. Die eingebaute NOOID→ANYOID-Beziehung bleibt intern explizit repräsentiert. |
| Abstrakte FORMAT-Komponente | Fehlender numerischer Bereich bedeutet fehlende Präzision. Er bleibt als unbeschränkter `NumericType` im gebundenen Graph erhalten; ein korrekt als ABSTRACT deklarierter Formatbereich wird nicht mehr als fehlende Implementierung abgelehnt. |
| Verwendung und Verschachtelung | Noch unbestimmte Komponenten verlangen ABSTRACT an Domain bzw. verwendendem Attribut. Dies wird durch verschachtelte Formate verfolgt. Eine konkrete Verwendung ohne Konkretisierung erhält `FORMAT_ABSTRACT`. |
| Spezialisierung | INHERITANCE behält Layout, Breite und Herkunft, bindet geerbte Teile aber auf das wirksame Attribut der spezialisierten Struktur. Dessen exakte Präzision gilt für Grenzen und Konstanten. |
| Grenzen abstrakter Formate | Aus der allgemeinen Abstraktionsregel und 3.8.6 abgeleitete Auslegung: Grenzen dürfen bereits exakte Komponentenwerte einschränken, setzen aber keine fehlende Präzision fest. Auffüllbreite, Reihenfolge und geerbte Grenzen gelten weiterhin. Eine konkrete Erweiterung muss zugleich die spätere Präzision erfüllen. |

Keine neue öffentliche Signatur, kein neues Modul und keine neue Pass-Infrastruktur.
Die bestehenden FORMAT-Teile und referenzierten numerischen Typen reichen aus.
Deklarationsherkunft und effektives Attributziel dürfen bei INHERITANCE
unterschiedlich sein; beide bleiben snapshotgebunden und immutable.
Spec 05/16, Javadoc und Migration 0.2 beschreiben diesen Unterschied.

## Ausführbare Nachweise

`OidFollowupAbstractFormatContractTest` ergänzt acht JVM-Verträge mit
positiven und negativen Fällen in beiden Versionen. Er prüft neben Ergebnissen
auch Diagnosen, wirksame Attributziele, ABSTRACT und deklarierte Herkunft.
`FormatPrecisionOidBindingContractTest` korrigiert die beiden bisherigen
versionsfalschen Erwartungen. Die gemeinsame portable Suite ergänzt 54 Fälle;
die beiden früheren Erwartungen werden dort entsprechend korrigiert.
Eine zusätzliche Fehlerpfadprüfung reproduzierte einen internen Fehler bei
einer selbstreferenzierenden OID-Domain. Die korrigierte Nullbehandlung und
eine JVM-/portable Regression verlangen jetzt eine reguläre fachliche Ablehnung.

Der abschliessende Lauf besteht mit **256 JVM-Tests und 653 portablen Fällen**
auf TeaVM-JavaScript/Node, WasmGC/Node, den echten TeaVM-Workerpfaden in
Chromium/Firefox/WebKit/Safari und Web Image unter Node/Chromium/Firefox/WebKit.
Alle Prozesse enden regulär. SDK-Beispiel und neun macOS-CLI-Prozesse bestehen.
Konformität bleibt unverändert bei **540 PASS, 1 XFAIL, 52 FAIL** ohne ERROR/XPASS;
der kombinierte Gradle-Lauf endet ausschliesslich deswegen mit Exitcode 1.

Der [Prozessvergleich](oid-followups-reference-comparison-2026-10-20.json)
umfasst **152 Fälle pro Compiler**: 103 gleiche und
49 unterschiedliche Annahme-/Ablehnungsergebnisse, keine
unerwarteten Ergebnisse des aktuellen Compilers. In
26 Referenzfällen wird zusätzlich ein interner
Fehler beobachtet; gleiche Ablehnung allein bestätigt dann keine Fachregel.
Die unveränderten Suiteerwartungen wurden nicht an das neue Verhalten angepasst.

Das [Verifikationsmanifest](verification-2026-10-20-oid-followups.json) enthält
Quellbaumdigest, Artefakthashes, Werkzeuge, Host, Kommandos und Rohberichte.
Der [Fallkatalog](conformance-open-cases-2026-10-20-oid-followups.json) hält die
52 offenen Konformitätsfälle einzeln fest. Performance, Linux/Windows und das
vollständige releaseCheck wurden nicht abgenommen.

## Konkrete Restarbeit und Grenzen

1. **OID-Referenzziele (WP-20/22, nächste fachliche Arbeit):** Die 2.4-Regel
   in 3.5.3 verbietet Referenzen auf Klassen ohne stabile Identifikation.
   Sie ist noch nicht vollständig auf Referenzattribute und Rollen-Zielmengen
   angewandt. Explizites/implizites NO OID, Topic-Defaults, ANYCLASS,
   Restriktionen, EXTENDED und geerbte Ziele müssen gemeinsam geprüft werden.
   Die 2.3-Regel darf dabei nicht rückwirkend verschärft werden. Bestehende
   positive 2.4-Beziehungsfälle ohne OID belegen diese Abnahme ausdrücklich nicht.
2. **OID-Namen und Identitäten:** IMPORTS UNQUALIFIED INTERLIS muss für diese
   Builtins mit normaler Sichtbarkeit, Shadowing und Mehrdeutigkeit verbunden
   werden. Weitere Übersetzungs- und Basket-/Assoziationskombinationen sowie
   alle OID-Trägerregeln sind nicht vollständig abgenommen.
3. **FORMAT:** Vollständige öffentliche Typrelationen, abstrakte Attribute mit
   bereits konkretem Trägertyp, Konkretisierung verschachtelter Strukturattribute
   mit weiterhin abstrakter Formatdomäne, interne Zeitstrukturen sowie alle
   Übersetzungs-/Einheitenkombinationen bleiben offen. Die geprüfte
   Konkretisierung betrifft numerische Attributüberschreibungen in der explizit
   spezialisierten Basisstruktur; daraus folgt keine automatische Änderung
   der explizit gewählten verschachtelten Formatdomäne.
4. **Übergreifende Abnahme:** Die im Implementierungsstatus geführten View-,
   GENERIC-/Metadaten-, Resolver-, LSP-, Legacy-CLI-, Performance-, Korpus- und
   Linux-/Windows-Pflichten bleiben bestehen. Kein vollständiges releaseCheck
   und keine P3-Freigabe.
