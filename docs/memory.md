# Session-Stand — 2026-10-16 Abend (Nachtzyklus 5, autonom)

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.
> **Nachtzyklus: keine askUser-Rückfragen, autonom bis zum Ende, Zusammenfassung für morgen früh.**

## Auftrag (Paul, 2026-10-16, Prio-Reihenfolge)

0. Branch: alles auf EINEN Branch ab `main` (`story/night-5-2026-10-16`); immer EIN Punkt nach
   dem anderen; memory.md/docs laufend schreiben.
1. **Bug #154 beheben** — https://github.com/sterlp/eclipse-peon-ai/issues/154
   (Inhalt: /tmp/issue-154.json).
2. **Token-Schätzung zu niedrig** (~110k statt 120k): chars×2/7 → /3 — Paul-Idee, „nicht so
   wichtig, mehr Info". Meine Einschätzung nötig, Umsetzung klein; als ⏳ dokumentieren.
3. **Bewerten:** `feature-request-ShellTool.md` + `long-shell.log` — TrimService-Gedanke
   („sollten wir auch bei anderen Tools einsetzen, rechtfertigt fast ein TrimService").
4. **Review Pauls Erstentwurf Async Agents:** `agent/tools/AgentsTool.java` + `agent/tools/AsyncMonitor.java`
   (untracked WIP) — Docs-Seite anlegen (WIP by Paul), Meinung + Top-3-Empfehlungen.
4.1 **Spanned/Tag-Idee:** `ToolsResult.java` — feste Tags (oder XML) um Tool-Results, Compact-Ausschluss
   via Tag, Huckepack: async Agent-Results als getaggte Nachricht ins Kontext. Meinung + Einordnung.

## Fortschritt

- [x] #154 ✅ `bc3b70b5` — toK ohne Tausender-Separatoren (rot-erst), Gate 1127/0, token-usage.md R5a.
- [x] Token-Estimate /3 ✅ `fbe2866f` — chars/3 + Umkehr ×3/n, Gate 1127/0, 2 Plan-Inventar-Nachzügler (D1 verifiziert), Docs gezogen (compact*, ADR-0055/56 Update-Note, index.md, tool-result-size).
- [x] ShellTool/TrimService-Bewertung ✅ docs/output-trim-service.md (🚧 WIP by Paul) — Request code-bestätigt, TrimService als kleine shared-Komponente empfohlen (nicht Service), Q1–Q4 für morgen.
- [x] Async-Agents Review-Doc + Top-3 ✅ docs/async-agents.md (🚧 WIP by Paul) — 5 Kernbefunde (u. a. dead asyncRunning-Counter, Same-Agent-Nebenläufigkeit, Stale-TurnContextSupplier), Top-3 + ToolsResult-Tag-Bewertung (XML-artig empfohlen), Q-Fragen für morgen.
- [ ] Da-Dok-Review der beiden Code-Inkremente (#154 `bc3b70b5`, Estimate `fbe2866f`) → dann planImplemented + Docs-Commit + Summary.
- [ ] End-Summary für Paul

## Notizen (aus Vortag, weiter gültig)
- eclipseEditFile/ReplaceLines-Fehlerquote hoch → line-based Edits IMMER re-readen; strukturierte
  Edits als Ganzdatei-Write.
- Paul arbeitet parallel im selben Tree — selektives git add; Gate-Blocker durch fremdes WIP →
  STOP-AND-ASK (nachtzyklus: skippen/dokumentieren statt fragen).
- Letzter Stand Vortag: Symbol-Rollout S1/S2 ✅, S3 offen (🗜 PoDelegateTool/AgentsTool),
  EclipseGrepTool IFile-Disclosure offen. Beide evtl. berührt von Async-Agents (AgentsTool!).