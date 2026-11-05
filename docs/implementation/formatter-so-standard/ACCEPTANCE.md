# Kandidatenabnahme F0–F3

Die lokalen Gates und die unabhängige Gesamtprüfung werden getrennt bewertet.
Der Kandidat bindet die vorab geprüften Produkt- und Fixturebytes.
Die Reviewausgaben bleiben extern am exakten Commit.

| Kriterien | Geprüfte Verträge und Belege |
| --- | --- |
| FF-01–03, FF-38 | Getrennter Worktree, Baseline, Originalmanifest mit acht SHA-256-Werten, Solltexte und Phasenprotokoll. Siehe BASELINE und TEST_LEDGER. |
| FF-04–06 | Ein zustandsloser Formatter im Compiler-Modul. Alter Dreierkonstruktor, neue Grenzen und unabhängige Requestoptionen geprüft. Reale Syntax ohne importierte Modelldateien formatiert. |
| FF-07–14 | Deklarationsgolden, Kommentar-/Wert-/EOL-Tests, Kopfzeilen, externe Constraints, geschützter Kommentarrand und CLI-BOM-/UTF-8-Gegenproben. |
| FF-15–23 | Alle fünf Constraintfamilien, Kopf-/Klauselalternativen, Originaloperatoren und Klammern, geschachtelte Calls, Pfade, Spezialfaktoren und Kommentare. Siehe cases/golden und FormatterSoStandardTest. |
| FF-24–27 | Strikter Reparse, separater Token-/Kommentar-/Metadatenvergleich, Verifiermutationen, gemischte EOLs, Breitenrand, 500er Boolkette mit Verschachtelung, Abbruch und parallele Aufrufe. |
| FF-28–32 | CLI-Check/Write/Conflict und interner Defekt, Standard-LSP über stdio mit strikter Optionsdekodierung, Readonly, veraltetem Request, UTF-16 und Abbruch. Bytegleichheit von API, CLI und LSP bei Breite 40. |
| FF-33–34 | Benannter SDK-Verbraucher sowie aktuelle CLI-/LSP-Launcher gegen modulare Produkt-JARs. Formatter-API ist in Bridge/TeaVM/Web Image und der Extension nicht erreichbar. Die Extension verwendet den LSP-Standardprovider. |
| FF-35 | Acht Originale bei 120 und 40 Codepoints. Reparse, Token-/Wert-/Bindungsschutz und Idempotenz. Klassifizierte Diffs in `f3/corpus-default/` und `f3/corpus-narrow/`. |
| FF-36 | Lokale 2.3-/2.4-Closures vor und nach Formatierung. Symbolordnung, Metawerte, Diagnosen und ausgewählte gebundene Ausdrucks- und Constraintpayloads verglichen. |
| FF-37 | Formatversion 2.0 im zentralen Manifest, aktive CLI-/LSP-/SDK-Verträge und Versionskonsumenten aktualisiert. Fixture-Invalidierung durch ein geändertes Golden geprüft. |
| FF-39–40 | Unabhängige Abnahme separat. Der lokale Belegindex ersetzt das OpenCode-Urteil nicht. |

Produktprofile für vollständige Konformität, Browser, Wasm, Performance
und Release sind für diese Formatteränderung nicht ausgeführt. Das
Release-Evidenzskript erhielt seine gezielte Versionstestabnahme.
