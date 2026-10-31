# P3 – Browserclient und Diagrammadapter

Der [Pin](diagram-adapter-pin.json) bindet das aktuelle vollständige
DiagramDocument-1.0-Schema, die unverändert übernommenen Rendererquellen,
Typverträge, Lizenzen und npm-Versionen. Die historische Schemaevidenz unter
`docs/evidence/p3/` wird nicht überschrieben.

`@ilic/diagram`0.1.2 und dessen Compiler-Wasm0.10.0 sind im npm-Register nicht
veröffentlicht. Deshalb liegen die Quellen aus den im Pin genannten Gitcommits
unter `interlis-wasm-teavm/src/main/typescript/vendor/ilic-diagram/`.
Der MIT-Renderer verwendet elkjs0.11.0 und erzeugt wirklich SVG und ein
Sprotty-Modell; es wird keine zweite INTERLIS-Semantik implementiert.
Die einzige Typkorrektur betrifft einen optionalen Arrayzugriff in der
übernommenen ELK-Deklaration; die Runtime bleibt unverändert. Es gibt kein
`skipLibCheck`. Die generierten öffentlichen Deklarationen werden zusätzlich
mit einem separaten strengen SDK-Verbraucher ohne interne Pfadaliases geprüft.

Der Build erfolgt über `:interlis-wasm-teavm:buildBrowserTools` mit den per
Lockfile gepinnten npm-Abhängigkeiten (`npm ci` im Modul; Node24.22.0).
`browserClientTest` und `diagramAdapterTest` prüfen Host/Client und echte
Compilerprojektionen unter Node. `prepareBrowserHarness` liefert ESM,
Deklarationen, Worker, Wasm, Lizenzen und Renderer aus; `browserContractTest`
prüft Chromium/Firefox/WebKit, `safariContractTest` zusätzlich echtes Safari.
Ein Node-Test oder TypeScript-Build ist keine Browserabnahme.

`CompilerClient` hält nur bestätigte Quellen und vollständige Konfigurationen.
Ein laufender synchroner Wasm-Aufruf wird durch Worker-Neustart abgebrochen;
alle bestätigten Sitzungen werden wiederhergestellt. Die Wiederherstellung
meldet neue Sessionhandles. Alte Session-/Snapshot-/Schemahandles sind danach
ungültig. Wartende Aufrufe können ohne Neustart entfernt werden. Revisionen und
exakte Zahlen bleiben Strings, Edits und Quellbereiche verwenden UTF-16.
Der Client begrenzt seine Queue, die Bridge prüft sechs angenommene
Sitzungsgrenzen und setzt Text-/Batch-/Konfigurationsänderungen atomar um.
Ergebnisse müssen explizit freigegeben werden; Ablehnungen zählen ebenfalls.

Der Diagrammadapter validiert Schema und Identitäten, erhält das vollständige
Core-Dokument unveränderlich und projiziert in den gepinnten Renderer-Vertrag.
N-äre Assoziationen behalten ihre eigenen Knoten, Attribute und sämtlichen
Rollenkanten. Geerbte Anzeigen haben eigene IDs und gemeinsame Ursprungssymbole.
Navigation verlangt denselben Snapshot, dieselbe Quellrevision und denselben
bestätigten Text. Die Textcursor-Auswahl verwendet Symbolidentitäten.
UTF-8-Byteoffsets werden ausschliesslich als zusätzliche Koordinaten des
Unicode-Editortexts abgeleitet; sie behaupten keine ursprünglichen Dateibytes.

Darstellungsgrenzen werden als `UNSUPPORTED_PRESENTATION` angezeigt: Ältere
1.0-Produzenten ohne Modifikatoren belegen keine Abstraktdarstellung; der
gepinnten Renderer zeichnet zudem keine Aggregations-/Kompositionsdiamanten.
Die betroffenen Kanten und exakten semantischen Endinformationen bleiben im
Dokument und in der Ansicht vorhanden. Layout und Scrollposition gehören dem
Host. Eine aktuelle Antwort kann eine frühere Ansicht ersetzen; eine verspätete
Antwort oder ein alter Navigationsanker kann das nicht. Reine unveränderte
Geometrie kann den letzten angenommenen Layoutstand wiederverwenden.

libavoid-js0.5.0-beta.5 bleibt als austauschbares externes ESM/Wasm-Modul samt
LGPL-Lizenz erhalten; die HTML-Importmap bindet seinen lokalen Pfad. MIT-/ELK- und
weitere Abhängigkeitslizenzen werden mit ausgeliefert. Es wird kein Netzrenderer
aufgerufen. Diese Buildbeschreibung allein belegt noch keinen P3-3-PASS;
aktuelle Läufe und Abnahme stehen im [Implementierungsbericht](implementation/P3-3.md)
und [Status](STATUS.md).

Der Diagrammrequest akzeptiert options nach DiagramOptionsDto/Schema1.0; nur
fehlende Optionen verwenden gemeinsame Defaults. Leere Auswahl, unbekannte
Felder und ungültige Optionen liefern explizite Fehler. Partielle Eingänge
behalten betroffene Knoten, fehlende Importstubs und ihre tatsächliche
Vollständigkeit. Idempotentes closeSession verwendet im Client schwache
Handleidentität und hält keine unbegrenzte Liste abgeschlossener Sitzungen.

`TRANSLATED` ist jetzt mit explizitem `translationModel: SymbolRefDto` nutzbar;
der Client prüft und übersetzt auch dessen Snapshot-Handle. Die vorhandenen
gebundenen TranslationLink-Paare bestimmen Anzeigenamen und benannte Typlabels.
IDs, canonicalName und Originalanker bleiben gleich. `NEIGHBOURHOOD` verlangt
`neighbourhoodDepth` von0 bis32 und folgt dem gebundenen Referenzgraphen gemäss
Kapitel11. Eigentümerrahmen expandieren keine unbeteiligten Geschwister mehr.
Die Demo bietet Fokus-, Scope-, Tiefen- und Übersetzungswahl. Neue reale
UI-Prüfungen lassen den unveränderten gepinnten Renderer tatsächlich arbeiten,
verzögern danach seine Antwort und belegen deren Verwerfen nach einem Edit.
