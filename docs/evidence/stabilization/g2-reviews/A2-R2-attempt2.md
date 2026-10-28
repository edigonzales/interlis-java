# A2-R2 Recheck: CLOSED; A2 bleibt NACHARBEIT

## Urteil

**A2-R2 ist geschlossen.** Die gezielte Nacharbeit beseitigt beide Blocker des
ersten R2-Reviews. **A2 insgesamt bleibt NACHARBEIT**, weil A2-R3 und A2-R4
weiter offen sind; danach bleibt ein frisches A2-Gesamtreview Pflicht.

Der explizite Modellparameter `gpt-5.6-sol` war Teil des von der
Orchestrierung akzeptierten Reviewauftrags. Das ist Konfigurationsevidenz,
keine Selbstauskunft über Modellidentität.

Massgeblich war
`build/reports/g2/A2/r2-rework-packet/manifest.json` mit SHA-256
`12e08755533798016516a8005b4ca5b271163e44dad2295ddacc35c7141e9b07`
und After-Source-Digest
`293bef845555da1a8687b8d252ffbec439ca452965767d42912a2cf1bf6e8ff4`.

## Geschlossene R2-Befunde

### A2-R2-SPECIAL-SCOPE: CLOSED

Alle 21 Sonderwert-/Typzeilen besitzen `languages=[2.3,2.4]`, eine explizite
Bedeutung dieses Source-/Factory-Scopes und mindestens eine Verknüpfung zu
einer tatsächlich vorhandenen Matrixregel. Bei den 16
`BuiltinFunctions.Value`-Varianten ist dieser Scope sauber von den realen
Signaturverwendungen getrennt. Eine unabhängige Auswertung aller 21
Factorysignaturen stimmt für Ergebnis-/Argumentpositionen und aktive Versionen
mit dem Katalog überein. Eine absichtlich falsche 2.3-Zuordnung von
`MULTIPOLYLINE` wurde erkannt.

### A2-R2-OBJECT-ASSERTION: CLOSED

`Value.OBJECT` hat in der eingefrorenen Factory null aktuelle Verwendungen,
keine aktive Signaturversion und den Zustand `NO_CURRENT_SIGNATURE_USES`. Die
Abnahme verbietet ausdrücklich einen erfundenen aktuellen OBJECT-Formaltest und
bindet jede künftige Assertion an
`BUILTIN_AREAS2_AREAS3_NORM_PROFILE`. Eine erfundene aktuelle Verwendung wurde
in der Gegenprobe zurückgewiesen.

Für `areAreas2` bleiben beide exakten 2.4-Belege direkt erhalten:

- Kapitel 3.14, Seite 87: `Objects: OBJECTS OF ANYCLASS`
- Anhang A, Seite 122: `Object: OBJECT OF ANYCLASS`

Die aktuelle Factory verwendet `OBJECTS, TEXT, TEXT`. Katalog, Abnahmetext und
Status wählen keine der beiden Normstellen als freigegebene Erwartung; die
Nutzerentscheidung bleibt offen.

## Reproduzierbarkeit und Prüfungen

Die Manifest-/Archiv-/Norm-/Patchkette ist vollständig: 1105 Before-Dateien,
1108 After-Dateien, genau elf Paketänderungen. Eine unabhängige
Patchrekonstruktion stimmt einschliesslich Hashes, Grössen und
Ausführbarkeitsbits mit dem After-Manifest überein. Die Paketberichte und die
unveränderten Pins für normative Erwartungen, Konformitätsbaseline,
A1-Profil, Build und Gradle-Properties stimmen.

Ausgeführt wurden:

1. Snapshotprüfung mit isoliertem Git-Index und `--sources`: PASS, erwarteter
   Source-Digest.
2. Direkte Python-Suite: 29/29 PASS.
3. `JAVA_HOME=...25.0.3-tem ./gradlew g2EvidenceTest --rerun-tasks`: 29/29
   PASS, `BUILD SUCCESSFUL` mit Gradle 9.8.1.
4. `author_g2_predefined.py` mit Python 3.12.14 / pypdf 6.10.0:
   byteidentischer Katalog `7183312f…d563e`.
5. `g2_inventory.py` mit beiden gepinnten PDFs: byteidentischer Scan
   `53add09d…e570` und erfolgreiche Prüfung beider `areAreas2`-Stellen.
6. Eigene Gegenprobe: unabhängige Signaturauswertung, falsche aktive Version,
   erfundene OBJECT-Verwendung und OBJECTS→OBJECT-Factorymutation wurden
   geprüft. Artefakt: `counterprobe.json`.

Keine Produktions-/öffentliche API, normative Erwartung, XFAIL-Ausnahme oder
Normentscheidung wurde geändert. Der volle Compiler-/Portable-/Browser-/SDK-
Phasenlauf und die vollständige Sprachprüfung wurden für diesen fokussierten
Evidenzfix nicht wiederholt.

## Offener Umfang und nächster Schritt

A2-R3 (regelgenaue Vertrag-/Fall-/Assertionszuordnung) und A2-R4
(vollständiger Absatz-/Anhang-/Vertrags-/Ausnahmenaudit) bleiben blockierend für
A2. Der nächste Schritt ist A2-R3; erst nach R4 und einem frischen
A2-Gesamtreview darf A3 folgen.
