
### V3: versionsgebundene Source→Diagrammselektion

`interlis/diagramSelect(DiagramSelectionParams): DiagramSelectionResult` ist eine
readonly Protokoll1-Anfrage. Sie nennt Server-/Diagramm-/Snapshotidentität,
versionierte Root- und Source-Dokumente sowie die ursprüngliche UTF-16-Position.
Die Java-`LanguageQueries.symbolAt`-Abfrage liefert das Symbol; ausschliesslich
publizierte Container-/Knoten-/Member-/Kantenidentitäten dieses Symbols werden in
Projektionsreihenfolge zurückgegeben. Unbekannte Cursorpositionen liefern eine
leere Auswahl, niemals einen Label-/Offset-Fallback. Root-/Sourceversion,
Workspace-/Snapshotstand und readonly Handle müssen aktuell sein; Abweichung
liefert ContentModified, ungültige UTF-16-Grenzen InvalidParams. Auch selektierte
Verwendungen beziehen sich auf das Java-gebundene Symbol. Listen werden kopiert.
Die Capability `experimental.interlis.diagramSelection=true` kündigt die Anfrage
an; ihre Selektion schreibt keine Quelle und erteilt keine Editbefugnis.
