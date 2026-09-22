# Session-Stand — 2026-09-21 (F2 + get_exception + Linter-Mini-Zyklus)

## Wo wir stehen

**Branch `story/f2-debug-linter-2026-09-21`** (abgezweigt von `analysis/tool-evolution`, Story-C-Code
dort noch ungemerged — Merge/Squash bleibt Paul).

- ✅ **Zyklus gebaut & reviewed:** I1 Linter `7e8fb41` (idPattern-@P Full-Match, R-DL-21 Textblock,
  R-DL-22 MANUELL-Marker) · I2 Debugger F2 `5c57422` (statics-Block, evaluate Tiefe 2) · I3
  `get_exception` `75eb9ac` · G1-Fix `348d521` (Name-Heuristik-Grenze dokumentiert + Pin-Test).
  Core 923/0, Plugin 261/0 (Da Dok verifiziert). Review: CONCERNS → G1 (a) gelöst, G2 (inventory) +
  Flips durch Jon erledigt.
- ✅ Docs: R-JD-9/10 + UC-JD-10/11/12 ✅, R-DL-21/22 + UC-DL-64/65/66 ✅, tool-descriptions-inventory
  aktualisiert (58/59 neu, 67 get_exception), open-points 🔒-Abschnitte.
- ⚠️ **Eigenes lintDocsAndTests läuft auf stale Bundle** (Eclipse-Instanz lädt altes llmpeon-core-jar):
  zeigt noch `VERWAIST UC-DL-99` + `UNBELEGT UC-JD-2…6`. **Nach Eclipse-Restart prüfen:** UC-JD-2…6
  als MANUELL-Zeilen, UC-DL-99 weg; das ist der letzte Bestätigungsschritt.
- Da Mek wartet auf Freigabe `planImplemented` (Plan-Archiv) — nach Pauls OK/Neustart-Check.

## Nächste Schritte

1. **Paul:** Konzept-Punkt 4 (Agent „aktiv" beim Compact + Queue/Block) — Konzept unten, Feedback
   bitte; danach ggf. eigene Story.
2. Eclipse-Restart → Dogfood-Lint prüfen → dann Da Mek `planImplemented`.
3. Merge/Squash der Branches = Paul (tool-evolution + dieser).
4. Geparkt: ApiRetry-Follow-up, Issue #142-ADR, build.properties-Warnung, DL-Test-ID-Sweep (~33).

## Konzept Punkt 4 (Stand, Paul-Feedback offen)

IST-Fakten (searchAgent, 2026-09-21): `AbstractAgent.working` (AtomicBoolean) deckt nur `call()` —
`compact()` setzt es NICHT (`AbstractAgent.java:178/209` vs `:270-299`); Roster 🟢 leuchtet nur bei
working-Leaf (`AiAgentStatusModel.java:53`); Slave-Compact = Job in `AIChatView.doCompressAgent:540-563`
(inkrementiert `inFlightTurns`, disabled Buttons); Send während Compact geht NICHT in die Queue
(`resolveOutgoingMessage:617-626` prüft nur `isWorking()`) → dokumentierter Memory-Race
(chat-job-lifecycle.md:145-149, Backlog). Queue existiert (`UserMessageQueue`, FIFO-Chain,
drain-on-abort) — sie verarbeitet nach dem Compact, aber nur wenn der Agent `isWorking()` war.

Konzept-Skizze: (A) „aktiv"-Anzeige: compact setzt einen Compact-Status (z. B. `compacting` am
NamedAgent-Status-Modell, nicht `working` — sonst Queue-Semantik koppeln), Roster zeigt 🟡/⚙️ beim
kompaktierenden Agenten. (B) Eingabe während Compact: Queue-Pfad — UserMessage wird gequeued (wie
heute bei working) und läuft NACH dem Compact als nächste Message in EINEM Job (Queue lebt außerhalb
der Memory, ADR-0017/0018). Alternativ Input sperren (einfacher, aber UX-Schlechter). Offen bei
Paul: Queue oder Block?

## Was nicht neu aufgemacht wird

CR-1/2/7 abgelehnt · CR-5/CR-6 geparkt · Copilot-Server nicht decompilieren · keine Confirmations für
Debugger · Target-Rollback nur IDE.

## Lektionen (Zyklus)

1. Eigene Dogfood-Tools in der laufenden Eclipse-Instanz testen STALE Code — Disk-Grep + Suite sind
   der Beweis, nicht der Live-Tool-Lauf (neu!).
