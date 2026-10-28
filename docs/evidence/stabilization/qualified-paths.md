# N-stellige, qualifizierte und mehrwertige Objektpfade

Abschnitt WP-21/22; META-003/006/008/014, SEM-008/010/014; Stand 20. Oktober 2026.
P2/P3 bleiben offen. Dieser Abschnitt betrifft die gemeinsame Ausdrucksbindung,
keine vollständige Abnahme aller Constraint- und View-Pfadformen.

## Fachliche Entscheidungen

- `rolle[Assoziation]` bezeichnet die Ausgangsrolle und führt auf
  Assoziationsinstanzen. Rollenname und Qualifikator erhalten getrennte
  Bindungen und Quellbereiche. Die bestehende geschlossene `PathStep`-Familie
  erhält dafür `AssociationSourceStep`, ohne künstliche Gegenrolle.
- Eine einzelne Teilnehmeridentität einer n-stelligen Beziehung bindet nicht
  die Kombination aller anderen Teilnehmer. Rollenkardinalität `{1}` reicht
  deshalb nicht für einen Einzelobjektzugang. Die statische Grenze ist `0..*`;
  binäre Zugänge behalten ihre präzisere Rollen-/Linkkardinalität.
- Eine Objektmenge bleibt über Rollen, Assoziationsinstanzen, `THIS` und
  Referenzattribute hinweg eine Menge. Fortsetzungen müssen für alle möglichen
  Zielklassen gelten. Eine mehrwertige skalare Attributauswahl wird nicht als
  einzelnes Boolean-/Funktionsargument akzeptiert.
- Qualifikation umgeht weder HIDING noch EXTERNAL/Topic-Sichtbarkeit.
  Mehrere passende Ausgangsteilnehmer bleiben mehrdeutig; ein expliziter
  Ausgangsrollenname kann sie unterscheiden.

Grundlage: [Referenz 2.3 §§2.7/2.13/2.14](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_d.pdf)
und [Referenz 2.4 §§3.7.3/3.7.5/3.13/3.14](https://www.interlis.ch/download/interlis2/STAN_d_DEF_2024-04-24_eCH-0031_V2.1.0_INTERLIS_2-Referenzhandbuch.pdf).
Die konservative Typfortführung ist eine daraus abgeleitete Compilerregel;
sie führt keine Laufzeitfilterung von Objektmengen ein.

## Referenzvergleich

[Zwölf reale Prozesse je Compiler](path-reference-comparison-2026-10-20.json)
enthalten Quellen, Inhaltshashes, Kommandos, Exitcodes, stdout/stderr und die
bestätigte ili2c-Version 5.6.9. Beide Sprachversionen zeigen dieselben Ergebnisse:

| Fall | Aktueller Compiler | ili2c 5.6.9 |
|---|---|---|
| Qualifizierte Ausgangsrolle | Annahme mit zwei Bindungen | Ablehnung der Syntax |
| Unqualifizierte Ausgangsrolle | Annahme auf Assoziationsinstanzen | Ablehnung und interner Nullfehler |
| Ternärer Zugang an `OBJECT OF` | Ablehnung als Menge | Annahme |
| Ternärer Zugang an `OBJECTS OF` | Annahme | Annahme |
| Mengenpfad über weitere Rolle | Annahme als Menge | Annahme |
| Mehrwertiges Boolean-Attribut als einzelner Constraint | Ablehnung | Annahme |

Diese Abweichungen werden einzeln beibehalten; Pins, Goldens und XFAILs der
externen Suite werden nicht angepasst. Der erste Vergleich mit ungültiger
Issuer-URI `AT "x"` war nicht aussagekräftig und wurde mit gültiger URI wiederholt.
Nur die korrigierten Läufe sind übernommen.

## Abnahme und Grenzen

`QualifiedAssociationPathContractTest` prüft fünf JVM-Verträge mit positiven
und negativen Fällen für beide Sprachversionen: Kardinalität, Mengenflag,
öffentliche Typabfrage, Rollen-/Assoziationsidentitäten, Schema-Closure,
Navigation und Rename, HIDING/EXTERNAL, mehrdeutige Teilnehmer, Zielalternativen
und Referenzattributfortsetzung. 63 zusätzliche Fälle verwenden denselben
portablen Vertrag auf TeaVM-JavaScript, WasmGC und Web Image.

Offen bleiben allgemeine Strukturmengen-, Index- und Inspektionspfade sowie
die vollständige Kombination mit Views, Übersetzung und Transferreihenfolge.
Insbesondere besitzen UNIQUE-Schlüssel und EXISTENCE-Pfade noch separate,
verkürzte Bindung: UNIQUE übernimmt teilweise nur den ersten Namen; EXISTENCE
reduziert Operatoren auf Namenssegmente. Diese Pfade sind durch diesen
Ausdrucksabschnitt **nicht** vollständig geprüft und dürfen nicht als abgenommen
erscheinen. Nächster Schritt ist ihre Überführung auf die gemeinsame strukturierte
Pfadbindung einschliesslich expliziter Sperre noch nicht darstellbarer Formen.
Die allgemeine RefSystem-`Name`-Sonderbehandlung besitzt weiterhin keinen eigenen
Attributschritt; bei mehrwertigen Zugängen wird sie jetzt nicht als Skalar geöffnet.

Aktuelle Prüfkommandos und Rohberichte stehen im
[Verifikationsmanifest](verification-2026-10-20-paths.json).
