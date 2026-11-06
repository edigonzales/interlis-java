# Eingefrorene Unicode-17-Eingaben

Die unveränderten Unicode-Originale und die kompilierten Tabellenwerte sind
gepinnt. `UnicodeWeightsTest` prüft die Tabellenwerte als Big-Endian-32-Bit-Zahlen
mit SHA-256 `ac144cbb01f6e608b767d0f4016882b92dacadd38d7617319e766b1109dd6687`.

| Entpackte Datei | Bestehender SHA-256 |
|---|---|
| UnicodeData.txt | `2e1efc1dcb59c575eedf5ccae60f95229f706ee6d031835247d843c11d96470c` |
| NormalizationTest.txt | `5019ffd530751a741900c849c0e010332f142a3612234639bd200b82138a87db` |

Java/JUnit prüft die Originalhashes und **100170** explizite NFD-Orakelvergleiche
gegen die eingefrorene Tabelle. Kompositions-/CCC-/Hangulverhalten und originale
UTF-16-Quellpositionen bleiben unverändert. Es wird kein neuer Generator und
keine Laufzeit-Normalisierung eingeführt. Lizenz: `UNICODE-LICENSE.txt`.
