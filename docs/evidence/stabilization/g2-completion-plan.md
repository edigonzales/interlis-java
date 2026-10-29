# Verbindlicher G2-Abschluss – Ausführungs- und Reviewvertrag

**Historischer Arbeitsablauf:** Für den Auftrag vom 29.10.2026 gilt organisatorisch
[docs/g2/EXECUTION_PLAN.md](../../g2/EXECUTION_PLAN.md), mit dem operativen Stand in
[STATUS.md](../../g2/STATUS.md). Die folgenden A–I-Schritte und bisherigen Reviews
bleiben historische Evidenz; fachliche Anforderungen werden dadurch nicht aufgehoben.

Auftrag vom 27.10.2026: vollständige INTERLIS-2.3/2.4-Compilerabnahme gemäss
WP-20–27. Die Folge A–I ersetzt alle früheren Fortsetzungsempfehlungen.
Anforderungs-/Fall-IDs und historische Evidenz bleiben erhalten.

## Arbeitsgrenzen

G2 umfasst Sprachsemantik, öffentliche Modelle/Queries, echte Translation-/CRS-/
Metaattribut-Overlays und reproduzierbare Modellbeschaffung mit Offline/Locks.
P3-CLI-Kompatibilität, vertiefte Produktintegration, Plattform-/Performanceabnahme,
P4/P5, Veröffentlichung und Release gehören nicht zu diesem Abschluss.
Vorhandene Browser-/SDK-/Adapterverträge bleiben Regressionspflicht.

Ausgangsbehauptung, in A1 erneut zu prüfen: 688 JVM-/20 Wrappertests,
3249 portable Fälle, 574 PASS / 19 XFAIL / 0 FAIL/ERROR/XPASS.
Die 193 OPEN / 7 IMPLEMENTED / 132 VERIFIED der bisherigen Matrix sind eine
Arbeitsbilanz; keine vollständige Norm- oder unabhängige Abnahme.

## Verbindliche Reihenfolge

| Schritt | Lieferumfang und Abnahme |
|---|---|
| A1 | Schmutzigen Arbeitsstand vollständig sichern; Quellen, Werkzeuge, Normen und Pins einfrieren; vollständigen Ausgangslauf mit unveränderten Erwartungen reproduzieren; unabhängiges Review. |
| A2 | Pflichtabsätze beider Normen, jede Grammatikalternative, Builtins und Spezifikationsverträge einzeln zuordnen. Umsetzungslücke, Nachweislücke und Nutzerentscheidung getrennt. Jede Regel erhält zuständigen Schritt und konkrete Abnahme. Keine pauschalen Restpunkte. |
| A3 | Testexport und semantischen JVM-/TeaVM-/Referenzvergleich vervollständigen; verlorene Mitglieder, falsche Ziele und vertauschte Reihenfolgen als Gegenprüfungen. Reviewpakete reproduzierbar machen; keine neue öffentliche Bridge-API. |
| B1 | Alle vordefinierten Modellinhalte versionsgerecht und öffentlich vollständig bereitstellen. |
| B2 | Namenskategorien, Imports, Sichtbarkeit und Vorwärtsreferenzen abschliessen; TYPE-MODEL-Grenzen, Modellklassen und INTERLIS-/Benutzernamenkonflikte zur Nutzerentscheidung vorbereiten. |
| B3 | Alle Syntaxzweige und Recoverypfade behandeln; Quellfehler, fehlende Bindung, Implementierungslücke und internen Fehler zuverlässig unterscheiden. |
| C1 | Numerische Unterteilungen mit effektiven Vorgängern, Vererbung, Ganzzahligkeit, Positivität, Zirkularität und exakten gebundenen Einheitenverhältnissen. |
| C2 | Skalierungs-/Konstantenprofil und OID-Regeln einschliesslich MANDATORY-Erweiterung; strittige Regeln erst nach Nutzerfreigabe ändern. |
| C3 | Text, Enum, FORMAT/Datum/Zeit, Koordinaten, Geometrie, Collections und Referenztypen vollständig abgleichen; zusätzliche LineCoord-Kontexte explizit entscheiden lassen. |
| C4 | Gemeinsame Compiler-/Queryregeln für Zuweisung, Erweiterung, Vergleichbarkeit und Einheiten; vollständige öffentliche effektive Typinhalte. |
| D1 | Modell-/Topic-/Klassen-/Strukturvererbung; EXTENDED, FINAL, ABSTRACT und Überschreibungen. |
| D2 | Assoziationen, Rollen-Zielmengen, Restriktionen, HIDING, EXTERNAL, OID und DEPENDS ON. |
| D3 | Effektive Mitglieder, Herkunft und fachliche Transferreihenfolge unabhängig von Dateireihenfolge und Wiederverwendung. |
| E1 | Sämtliche Objekt-/Attributpfade, qualifizierte/n-stellige Zugänge, Mengen, Indizes und Spezialkontexte. |
| E2 | Alle Builtins/Funktionssignaturen mit Argument-, Ergebnis-, Kontext- und UNDEFINED-Regeln. Externe Funktionen werden nicht ausgeführt. |
| E3 | Alle Constraintfamilien, SET-Body/WHERE, vererbte Namen und externe Constraints. |
| F1 | Alle View-Formationen, Vererbungen, Selektionen, Ableitungen, Inspektionen und abgeleiteten Beziehungen. |
| F2 | Übersetzungen aller relevanten Inhalte und importierten Closures mit öffentlichen Korrespondenzen. |
| F3 | CONTEXT/GENERIC/DEFERRED, Metadaten, Achsen, Linienformen und Grafik-/Signaturregeln, einschliesslich offener Vorwärtsbindungsfragen. |
| G1 | Overlay-Fingerprint-Platzhalter durch unveränderliche Translation-/CRS-/Metaattribut-Nutzdaten ersetzen. |
| G2 | Eindeutige Overlayziele, Bindung, Konflikte, Herkunft und Übersetzungsabhängigkeiten. Identische Angaben erlaubt; fehlende ergänzen; widersprüchliche ablehnen. |
| G3 | Vollständige Fingerprints, reine Overlaywechsel, Alias-Isolation und Fresh/Reuse prüfen. |
| H1 | Typisierten Workspacekatalog und wirksame Auswahlpolicy; vorhandene Repositorykandidaten explizit adaptieren. |
| H2 | Pending-Anforderungen mit Datei-/Index-/HTTP-Beschaffung ausserhalb des Compilerkerns verbinden. |
| H3 | Offline, Pins, Locks, eindeutige Fehler, Abbruch und Quelländerungen während der Beschaffung. Kein impliziter Neueste-Version-Fallback. |
| I1 | Gepinnte reale 2.3-/2.4-Closures aus dem eingefrorenen geo.so.ch-Index samt transitiven Abhängigkeiten. |
| I2 | Vollständige Normmatrix, semantische Differentialfälle, JVM-/TeaVM-Parität und alle G2-Regressionspflichten. |
| I3 | Unabhängige Gesamtabnahme und G2-Prüfgate; P3 bleibt offen. |

Der erste ausführbare Schritt wird bearbeitet. Bestehende Implementierungen
werden nachgewiesen statt pauschal neu geschrieben. Nur ein reproduzierbarer
Blocker dieses Schritts wird vorgezogen. Andere neue Funde erhalten Fall-ID,
Quelle, Ursache und zuständigen Schritt; keine freie Umpriorisierung.

## Review je Schritt und Phase

Ein Paket enthält Schritt/Regeln/Anforderungen, Umfang/Nichtziele, eingefrorene
Ausgangs-/Ergebnisquellen, Diff, Normbelege, Original-/Gegenfälle, öffentliche
Modell-/Queryprüfungen, Kommandos, Rohberichte, Werkzeuge/Pins/Artefakthashes,
API-/Verbraucheränderungen und verbleibende Ursachen.

Der Reviewer wird in frischem Kontext explizit mit `gpt-5.6-sol` angefordert.
Das akzeptierte Tool-Aufrufprotokoll ist der Konfigurationsbeleg; eine erratene
Selbstauskunft des Modells ist kein Identitätsbeleg. Fehlende Ausführbarkeit
wird als fehlender Reviewnachweis erfasst, niemals durch Selbstreview ersetzt.
Der Reviewer verändert keine Repoquellen. Eigene Gegenproben und Testergebnisse
entstehen nur unter Build-Ausgaben oder im isolierten Reviewverzeichnis.

Pflichtauftrag: Originalnorm und Umfang prüfen; Code/öffentliche Wirkung lesen;
gezielte Tests selbst ausführen; mindestens eine eigene relevante Gegenprobe;
Erwartungsabschwächung, Nachweislücke, Scopeabweichung und unnötige Abstraktion
prüfen. Urteil: ABGENOMMEN, NACHARBEIT oder WARTET AUF NORMFREIGABE.
Fehlende Ausführung ist kein fachliches Urteil.

Nach allen Schritten einer Phase folgt ein neuer Reviewkontext für ihre gesamte
Regelmenge, Wechselwirkungen, vollständigen Phasenlauf und Vollständigkeit.
Geänderte Eingaben entwerten betroffene Nachweise/Reviews. Erst nach beiden
Ebenen ist die Phase abgeschlossen.

## Prüfung und Normentscheidungen

Pro Schritt: gezielte JVM-/Modell-/Query-/portable Fälle und relevante
Original-/Referenzfälle; Versionen, Imports, Dateireihenfolge und Fresh/Reuse,
soweit für die Regel relevant. Pro Phase: check, vollständige eingefrorene
Konformität, checkPortable, checkBrowser mit echtem Safari, Web Image und
betroffene SDK-/Adapterverträge. Ein eigenes kleines G2-Gate folgt in I;
releaseCheck bleibt das weitergehende P3-Gate.

Lauflokale IDs dürfen normalisiert werden, fachliche Reihenfolgen/Identitäten
nicht. Nicht vergleichbare Referenzfelder oder Referenzabstürze werden gemeldet,
nicht als Übereinstimmung gezählt. Test-/Erwartungspins werden niemals automatisch
geändert. Quellen und Rohberichte bleiben unter Build-Ausgaben erhalten;
übernommene Evidenz muss dorthin mit überprüfbaren Hashes zurückverweisen.

Die 18 normativen Referenzausnahmen werden einzeln neu vorbereitet; INTERLIS 1
bleibt separat. Der Nutzer entscheidet allein über normative Erwartungen und
Ausnahmen. Bis zur expliziten Freigabe keine neue XFAIL-Ausnahme und keine neue
normative Erwartung. Eine betroffene Abnahme bleibt offen; der nächste
unabhängige Schritt der Reihenfolge darf weiterlaufen.

Aktuelle Abnahmezustände: abgenommen, Nacharbeit, fehlender Nachweis,
ausstehende Nutzerentscheidung. Ausführungszustand wird davon getrennt geführt.
G2 bleibt offen, bis alle Pflichtregeln/Phasen unabhängig abgenommen, reale
Closures reproduzierbar und alle notwendigen Nutzerentscheidungen getroffen
sind. Keine ungeklärten FAIL/ERROR/XPASS oder Implementierungslücken dürfen
übrigbleiben. Benutzerfreigegebene rohe Referenzabweichungen bleiben sichtbar.

## Reproduzierbarer A1-Einstieg

`tools/reference/g2_evidence.py` besitzt `snapshot`, `verify` und `run`.
`snapshot --out <Buildordner>` sichert alle vorhandenen Git-/nichtignorierten
Quellen einschliesslich uncommitteter Änderungen und die gepinnten Norm-PDFs.
`verify --snapshot <Ordner> --sources` vergleicht Archiv, Manifest und aktuelle
Quellen. `run` erhält Snapshot/Ausgabeordner sowie explizite Java-/Node-/GraalVM-/
Binaryenpfade, führt das vorhandene vollständige Gradleprofil aus und sichert
frische Rohberichte samt Ergebnissen, Versionen und Hashes. Kein /tmp-Skript
ist dafür erforderlich. Konkrete Aufrufe stehen im A1-Reviewpaket.

Das technische Profil `testdata/g2/a1-evidence-profile.json` pinnt für A1 die
144 bestehenden JVM-Suites/688 Tests, 3249 portablen Fälle und die Auswahl der
593 Konformitätsfälle. Es enthält keine neuen normativen Erwartungen und wird
bei normalen Läufen nicht aktualisiert. Ein anderes Schrittprofil muss Teil
des eingefrorenen Reviewinputs sein. Der Lauf entfernt alte Report-/Artefakt-
ausgaben, prüft Testdetails/Suitemenge/Laufzeitbezug und archiviert JVM-XMLs mit
Hashes. JSON-Reports werden auf Profilvollständigkeit und Roh-/Wrapperkonsistenz
geprüft. Reine grüne Statusfelder sind kein vollständiger Nachweis.
