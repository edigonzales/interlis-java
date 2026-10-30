# Gepinnte echte Phase-3-Closures

Unveränderte Originalbytes, am 30.10.2026 ausdrücklich von den offiziellen
Modellservern beschafft. Quellen und SHA-256 stehen in `pins.json`;
`PinnedRealClosureTest` prüft die Originalbytehashes vor der Auflösung.

- INTERLIS2.3: RoadsExdm2ien2017-04-05 → RoadsExdm2ben2005-06-16,
  zwei getrennte Dateien aus models.interlis.ch.
- INTERLIS2.4: LocalisationCH_V2 → Localisation_V2 → InternationalCodes_V2,
  jeweils2021-01-06, drei Modelle derselben unveränderten CHBase-Datei von
  models.geo.admin.ch. Die Datei enthält zusätzlich zwei nicht ausgewählte
  Dictionaries-Modelle; diese werden durch die gewählte Closure nicht zertifiziert.
- Vollständige Originalindizes beider Server. Der grosse geo.admin.ch-Index ist
  ausschliesslich verlustfrei gzip-komprimiert; der SHA-256 gilt für die
  dekomprimierten Originalbytes, der Artefakthash steht zusätzlich im Manifest.

Keine Aktualisierung bestehender Suite-/Corpus-/Normpins und keine neue
Referenzausnahme. Die Tests verwenden die wirklichen Indizes und Inhalte,
keine synthetisch veränderten Positivkopien. Zusätzlich zu erfolgreicher
Kompilierung werden öffentliche Importziele, Basistypen, effektive Member,
Enum-Reihenfolge, Constraints, Typen, Herkunft und Fresh/Reuse geprüft.
