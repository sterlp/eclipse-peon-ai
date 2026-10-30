# Release Notes — 2026-10-30 (Origin im Core · Namensalignment)

## Agenten heißen jetzt Jon · Plan · Dev · Review

Die Built-in-Agents tragen ihre Anzeigenamen direkt: **Jon** (Orchestrator, bisher „Peon-PO"),
**Plan**, **Dev**, **Review** — und die Sub-Loops **Search** (Such-Agent) und **Compact**
(Komprimierung). Die Ork-Namen (Da Boss, Da Thinka, Da Mek, Da Dok, Da Sniffa, Da Scribe) sind
komplett entfernt — Header, Roster, Verläufe und Prompts zeigen überall denselben Namen.
Konfigurations-Keys (`llm.agent.*`, Modell-Slots `po`/`plan`/`dev`/`review`) sind **unverändert**.

**Agenten-Verläufe beginnen neu.** Die Verlaufsdateien folgen dem Namen: `Dev-history.jsonl`
statt `Peon-Dev-history.jsonl` usw. Alte Dateien werden **nicht** gelesen und nicht migriert —
werden sie gebraucht, einfach umbenennen (`Peon-Dev-history.jsonl` → `Dev-history.jsonl`).

**`agent-order.txt`:** Der Default-Match ist jetzt `^Jon$`. Eine bestehende eigene Datei mit
`^Peon-PO$` matcht **nicht** mehr — Jon erscheint bis zur Anpassung alphabetisch statt zuerst.

**`AGENTS-<Agent>.md` für Jon:** Jon lädt jetzt `AGENTS-JON.md` (bzw. `AGENTS-Jon.md`/
`agents-jon.md`) — eine bestehende `AGENTS-PO.md` wird **nicht** mehr gelesen; bei Bedarf in
`AGENTS-JON.md` umbenennen.

## Wer spricht, steht jetzt an jeder Zeile

Der Core stempelt jede AI-Zeile mit dem sprechenden Agenten (`origin`):

- **Eclipse-Plugin:** Der Chat-Header zeigt statt „Peon" den echten Absender — Jon, Plan, Dev,
  Review, Custom-Agents, Search/Compact. Zeilen ohne Agenten-Bezug tragen keinen Kopf; eine
  AI-Gruppe verschmilzt nur, wenn Rolle **und** Absender gleich sind.
- **Web-Factory:** Der Chat zeigt nur Jon und die finalen Antworten seines Teams — die
  Zuschreibung folgt dem gestempelten Absender, nicht mehr dem Werkzeugnamen. Mitglieder-Interna
  (Denken, eigene Werkzeug-Aufrufe) bleiben unsichtbar.

## Technical notes

- Mechanik: `OwnerMonitor` stempelt `origin` fill-if-null in den Core-Monitor (ADR-0078), die
  Loop-Zuordnung läuft über `LoopOwner` — keine Tool-Call-Id-Buchführung, kein zweiter Speicher.
- Namensentscheidung: ADR-0079 (Anzeigenamen ohne Ork-Namen), Clean Break statt Alias-Migration.
