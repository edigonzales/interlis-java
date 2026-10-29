# Eingefrorene Unicode-17-Eingaben

Diese unveränderten Originaltexte waren bereits die gepinnten Eingaben von
`generate_text_weights.py` am Basiscommit
`abde84ef666184e128827894d63e176709e7c3db`. Der einmalige Generator ist entfernt;
seine Ausgabe `TextLengths.java` bleibt bytegleich und weiterhin gepinnt:
`47e90b02842b0305ff9ef63fc20a8eb9478d2c9f44385408392a9381eaecd207`.

| Entpackte Datei | Bestehender SHA-256 |
|---|---|
| UnicodeData.txt | `2e1efc1dcb59c575eedf5ccae60f95229f706ee6d031835247d843c11d96470c` |
| NormalizationTest.txt | `5019ffd530751a741900c849c0e010332f142a3612234639bd200b82138a87db` |

Java/JUnit prüft die Originalhashes und **100170** explizite NFD-Orakelvergleiche
gegen die eingefrorene Tabelle. Kompositions-/CCC-/Hangulverhalten und originale
UTF-16-Quellpositionen bleiben unverändert. Es wird kein neuer Generator und
keine Laufzeit-Normalisierung eingeführt. Lizenz: `../UNICODE-LICENSE.txt`.
