# G2 – operativer Status

- Aktuelle Lieferung: **L0B-3**, AC0B-06 bis AC0B-08 und kumulatives Phase-0b-Gate.
- Status: **AWAITING_REVIEW** – neun verbleibende Pythondateien entfernt; Java-
  Hostprüfungen, Pythonfreiheitscanarys und die freigegebenen Kompatibilitätsfixes
  implementiert. Vollständiger abschliessender Lauf erfolgreich; unabhängiger Review offen.
- Letzter unabhängiger PASS-SHA: `411585a650b4da42cc09dd0efbf3157697dcc178`
  (L0B-2), [Prüfbericht](reviews/L0B-2-review.md).
- L0B-3-Basis: `abde84ef666184e128827894d63e176709e7c3db`.
- Nutzerentscheidungen vom 29.10.2026: Metadaten-Kompatibilitätsfix samt API/Spec
  vorziehen; 2.3-OID/BASKET-OID ohne CONTRACTED bewusst zulassen. Andere
  Normregeln und die verbleibenden zwölf Referenzausnahmen bleiben unverändert.
- Quellenpins des 13-Modell-Corpus unverändert; fehlende Originale einschliesslich
  des exakten Index wurden aus dem offiziellen Image `1.2.1817` wiederhergestellt.
- Eigene Abschlussprüfung: JVM-Benchmark **13/13**; `check` und volle T2 grün,
  Projekt **580 PASS / 13 XFAIL**, alle **18** Zusatzprüfungen weiter aktiv.
- Python: **0** getrackte `.py`. Tatsächlicher Gesamtphasennachweis mit
  vererbter OS-Ausführungssperre erfolgreich: **5m 27s**, 79 Tasks ausgeführt.
  JVM **730** regulär, **136** Evidenz, **14** Host-/Kompatibilitätstests und
  **15** frische Assertionmethoden; alle ohne Fehler/Skips. Portable-/Browser-/
  Safari-/Web-Image-Läufe jeweils **3279** Fälle erfolgreich.
- Nachweise und Grenzen: [Implementierungsbericht](implementation/L0B-3.md).
- Reviewprovider bleibt **DeepSeek direkt**, `deepseek/deepseek-flash`
  („DeepSeek V4.1 Flash 1017“), OpenCode v2.0.28 in frischem Kontext.
- Nächster Schritt: Kandidatencommit unabhängig prüfen lassen.
  **L1-1 erst nach PASS.**

Verbindlich: [Plan](EXECUTION_PLAN.md), [Reviewanweisung](OPENCODE_REVIEW_PROMPT.md).
