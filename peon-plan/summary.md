Zusammenfassung
PR #179 setzt R-ML5 korrekt um: Der Model-Selector zeigt und ändert das Modell des aktiven Agenten. Dev schreibt in die Base, PO/Plan/Review in ihren Slot, Custom Agents in AGENT.md; Leeren entfernt den Wert statt "" zu speichern, und die Modell-Liste kommt von der Verbindung des aktiven Agenten.

Der angebliche P1-Blocker ist kein Bug: Beim ↻-Reload wird nur die Modell-Liste neu geladen, die getippte Eingabe bleibt. Beim Agent-Reload wird eine offene, unbestätigte Eingabe verworfen — das ist gewolltes Verhalten. Beim Senden muss der Fokus zunächst ins Chat-Eingabefeld wechseln; dieser Fokuswechsel löst FocusOut aus und committet das Modell. Der Agent-Wechsel committet die Eingabe zusätzlich explizit.

Fazit: merge. Die P1-Einstufung des Flesh-Reviews ist zu streng bzw. beruht auf einer falschen Annahme zum Send-Pfad. Die übrigen Findings sind Qualitätshinweise, keine Merge-Blocker.