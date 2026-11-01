# COLOR2 – sichere farbige Textdiagnosen mit Quellkontext

BASE_SHA: `792811e2bb1b7186c3eecc4a1a4f3930469a1c19` (unabhängiges COLOR1-PASS).
Umfang C2-01–11 / DIAG-001–003/006/009, CLI-004/006.
Implementiert und lokal geprüft; unabhängiger COLOR2-Review steht aus.

## Änderung und Grenzen

Der bestehende ConsoleDiagnosticRenderer verwendet explizite immutable
RenderOptions. Alte Overloads bleiben ohne ANSI; die CLI übergibt die Optionen
run-lokal. Severity, Ort, Code, Zeilennummern, Marker und Hintlabel verwenden
feste SGRs mit Reset je Bereich. Prosa, kanonische Diagnosen, RelatedLocations,
Hints, Auswahl, Reihenfolge, Exits und JSONquelle bleiben unverändert.

DiagnosticSnippetRenderer liest nur übergebene SourceTexte gleicher ID und
Revision mit gültigen UTF-16-Grenzen. Fehlende/alte Quellen behalten den
Revision-/Offsetheader ohne Frame. Maximal3 Quellzeilen und160 sichtbare Spalten
inklusive Gutter, begrenzte Fenster auch bei Millionenzeichenzeilen. Tabs haben
Viererspalten; C0/C1/ANSI/OSC/Bidi werden vor Styling druckbar escaped. Einzelne
Surrogate werden escaped, printable Unicode bleibt erhalten. Wo dessen Breite
vor/in der Spanne unsicher ist, ersetzt ein sichtbarer UTF-16-Hinweis den Caret.
Keine falsche Unicodepräzision, keine neue Abhängigkeit oder Host-IO im Renderer.
ASCII und escaped Kontrollen erhalten korrekte ^/~; Einfügepunkte einen Caret.
Mehrzeilige Spannen markieren nur den ersten sichtbaren Abschnitt mit Hinweis.

Die bestehenden aktiven Dokumentbindungen wurden eng nachgeführt: zwei
Spec-Dateihashes im g2-source-inventory, dessen Hash in der rule-matrix sowie
drei Specprozessbindungen in completion-links. Grund sind ausschliesslich die
angehängten COLOR1/2-API-Verträge in Kapiteln16/17/18. Historische Normen,
Anforderungszeilen, Semantik-Goldens, XFAILs und Reviews sind unverändert.

## Tatsächliche Prüfungen

Temurin25.0.3+9, Gradle9.8.1, macOS arm64; akzeptierte Nachweise unter bestehender
OS-Python-Sperre. Rohpfade relativ zu `build/review/compiler-cli-color/`.

- RED: SourceFrameRedTest auf unveränderter Auftragsbasis,1 Test/1 Failure,
  Exit1 wegen fehlendem Frame (`preflight/red-frame.log`).
- Sieben neue DiagnosticSnippetRendererTest-Methoden: ASCII/Tab/CRLF, leere und
  mehrzeilige Ranges, Unicode-/Surrogat-/Combining-/CJK-Fallback, aggressive
  ANSI/OSC/C0/C1/Bidi-Angriffe aus URI/Argument/Quelle, alle Severities/DE/EN,
  stale/missing/foreign sources,2-Millionen-Zeile und10'000 Escapezeichen vor dem
  tatsächlichen Fehler, strenge SGR-/Plainparität und Breitenlimits.
- `./gradlew check compilerCliCheck pythonFreeCheck --no-parallel --max-workers=2
  --console=plain`: Exit0/40s (`color2-gate-final.log`). Zuvor frischer vollständiger
  Lauf mit --rerun-tasks: HTTP-Ladertest mit2s-Frist unter paralleler Hostlast
  Timeout; bei begrenzter Parallelität bestanden. Dieser Lauf fand anschliessend
  die zwei veralteten Dokumentbindungen. Nach gezielter Hashnachführung besteht
  check ohne deaktivierte Tests oder geänderte Fristen. Rohfehlläufe bleiben erhalten.
- Nach letztem neuen Gegenprobentest zusätzlich
  `./gradlew :interlis-compiler-cli:test :interlis-model:test pythonFreeCheck
  --no-parallel --max-workers=2 --console=plain`: Exit0/16s,
  CLI100 Tests ohne Fehler/Skips, Pythonquellprüfung1463 Inputs
  (`color2-final-targeted.log`, `color2-targeted-guard.json`).
- Gesamter vorhandener XML-Bestand:1250 Tests/233 Suites,0 Failures/Errors/Skips
  (`color2-junit.json`). Das ist ein Bestandsinventar aus diesen Teilläufen,
  ausdrücklich kein vollständiger frischer COLOR3-Releasebeleg.
- Frisch gebauter installDist-Launcher:38 echte Prozessaufrufe, alle5 Befehle,
  DE/EN, ALWAYS/NEVER, JSONparität, unveränderte format-/diagram-stdoutausgabe,
  bösartige Originalsteuerzeichen in Quellkommentaren und unverändertes
  Office-Original inklusive tatsächlicher RelatedLocation/Quellframe/Exit1.
  `color2-process-final.log`: COLOR2_REAL_PROCESS_PASS38, regulärer Exit0,
  `color2-process-guard.json`: passed/0. Strenger SGR-Strip ergibt vollständige
  bytegleiche Textausgabe. Office-SHA256 bleibt
  fbb1bccc0e84795284f9fb1a3c0be9e6db516c3c4b8ae43ad2f856cf70948431.
  Explorative Proben hatten zunächst eine falsche Exit1-Annahme für analyze;
  die abschliessende Probe bewahrt korrekt dessen bestehenden Exit0-Vertrag.
- Bestehender compilerCliCheck:26 reale Fälle bestanden. `git diff --check`: Exit0.

COLOR1-Berichtpräzisierung: Nur stderr-Interaktivität ist nötig; die dortige
Formulierung „nur bei beiden Terminalkanälen“ war zu eng. Unabhängiger Reviewer
bewies bereits AUTO mit stdout-Datei und stderr-PTY. Die eigentliche Policy war korrekt.

Kein COLOR3-Release-/Browser-/Plattformnachweis vorweggenommen. Nächster Schritt:
sauberer Kandidatencommit und frischer unabhängiger COLOR2-Review für alle11 IDs.
