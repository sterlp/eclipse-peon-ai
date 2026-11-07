# AF-ADR-003 · Die Referenz-UI ist Geber, nicht Quelle

**Status:** Accepted (2026-11-06)

## Context

Paul will das Fabrik-UI auf **Ant Design** umstellen und sagt zur Referenz
`/Users/sterlp/dev/workset/ai-schulung/app/frontend`: „chat könnten wir eigentlich komplett
übernehmen, as is". Diese App ist React 18 + Vite + AntD 5, hat einen funktionierenden Markdown-
 und Werkzeug-Ablauf, Auto-Grow-Input und eine Timeline — aber ein anderes Anforderungsprofil:
**keine** Queue, **kein** geteilter Multi-User-Chat, **keine** Diffs, **keine** Team-Kacheln,
**keine** Zeitstempel-Pflicht und ein Verlaufsmodell „eine Liste, `done` liest nicht nach".

Genau an der letzten Stelle haben wir in diesem Zyklus C-5 repariert (R-WP-37: Anker je Turn,
Read-Sequenz, Wasserlinie **je Absender**, 149 UI-Tests). Ein 1:1-Tausch des Chat-Fensters hätte
diesen Fix still zurückgebaut — Pauls Nachsatz: „Auf keinen Fall den Fix wegwerfen, das UI-Projekt
soll beim Design nur helfen."

## Decision

Die Referenz ist **Geber von Mustern und Widgets**, nie Quelle des Zustands:

* **übernommen**: AntD als Design-Basis (Bootstrap verlässt die App, [R-WP-39](../ui.md)),
  Auto-Grow-Input mit Refocus ([R-WP-41](../chat.md)), Markdown-Rendering nachweisbar inkl. GFM
  ([R-WP-28](../chat.md)), Timeline für Werkzeuge und Delegationen
  ([R-WP-40](../chat.md)), Projektbaum im Sider ([R-WP-38](../ui.md)).
* **unangetastet**: `chat.page.tsx`/`chat.live.ts`/`chat.pending.ts` mit Anker, Read-Sequenz und
  Bestätigung je Absender, das SSE-Protokoll, Queue, Diffs, Kacheln, Verbatim-Anzeige
  ([AF-ADR-002](002-origin-in-core-display-in-factory.md)).
* **Zeiten bleiben live** (Paul 2026-11-06): Dauer aus dem Turn-Ende, Uhrzeit aus dem
  Browser-Augenblick; nach Reload zeigt der Verlauf, was der Memory enthält. Kein Parsen von
  Core-Text nach Zeiten, kein zweiter Speicher ([R-WP-11](../chat.md)).

## Consequences

* Jeder Port-Schritt ist ein **eigenes, kleines Inkrement** und muss die bestehenden C-5-Tests
  grün lassen; ein Test, der eine Referenz-Form nachbildet, nennt die erzeugende Core-Regel
  (AGENTS-DEV.md, Leitsatz „modellierte Draht-Form").
* Rund 60 der 149 UI-Tests sind Blatt-Belege (Klassennamen, CSS-Maße) und werden bei der
  Umstellung **mitsamt ihrer Behauptung** neu gefasst — das ist erwartete Arbeit, kein Rückfall,
  und geschieht je Inkrement mit benannter Abweichung.
* Die Referenz bleibt **outside the repo**: gelesen, nicht verlinkt, nicht kopiert — gepflegt wird,
  was wir verstehen.
* Wächst der Bedarf, Zeiten wirklich zu behalten, ist das eine **Core-Story** (Zeitstempel je
  Nachricht), nicht ein Fabrik-Patch — siehe [Resolved Points](../resolved-points.md).
