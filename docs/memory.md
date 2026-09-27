# Session-Stand — 2026-09-27 (Nested-Usage-Leak gemerged; Issue #149 Think-Dropdown)

> Achtung: docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts
> im Chat, Jon schreibt. (Da Mek hatte 2026-09-27 memory.md überschrieben — nicht wiederholen.)

## Branch
- **`story/issue-149-think`** (neu, von main, Issue #149 Think/Ollama). Vorher von Paul gemeldet:
  alles auf main gemerged; alte lokale Branches gelöscht (compact-input-budget `f6ce9211`,
  agent-tool-filter `271ca847` — Mek hat Unmerged-Inhalt verneint, diff vs. main = 0). Merge → main = Paul.

## Compact-Zyklus (Nested-Usage-Leak, Details in compact.md) — ABGESCHLOSSEN, gemerged (#148)
- ✅ Nested-Loop erbt Parent-Agent — `SearchAgentTool` `.agent(null)`, compactSession aus
  Sniffa-Filter, per-request Hint-Check, Paul-Hotfix „Compact geht nur mit Agent" (ToolService:216),
  Spam-Guard-Inversion gefixt. Surefire 1022/0/0/0, lint 0. TODO-REMOVE-Diagnostik raus.
- ⏳ offen (open-points.md): Bug B (CompactSessionTool: agent.getMemory() statt req.getMemory()),
  compactSession-Exposition bei agent==null (→ bundle mit R-CC-16), StreamingBridge-Race (ADR-0058).
- R-CC-16 ❌ (Da Scribe: Compressor-Context-Größe ins onTool, Result-Line chat-sichtbar) — Backlog.

## In Arbeit — Issue #149 (Think/Ollama, `story/issue-149-think`, AUTONOMER MODUS)
- **Paul weg, keine Fragen mehr** (2026-09-27): Ich entscheide, alle Entscheidungen am Ende als
  Review-Liste vorlegen. Homepage-Update im Inkrement. Nach dem Fix: Da Dok reviewt ALLE Provider
  auf weitere Think-Bugs. Merge → main = Paul.
- **SOLL festgezogen (Paul):** Checkbox raus, nur Think-Dropdown; **leer = unset = nichts senden**
  (alle Provider); Off-Tokens `false`/`FALSE`/`none`/`no`/`off` case-insensitive → Ollama
  `think:false`; Ollama-Items `""`/`true`/`false`; **`think_supported` fliegt komplett raus**
  (auch Custom-Agent-Frontmatter — nur noch `think`, Legacy leskompatibel, migrate-on-write);
  Basis-Checkbox „supports thinking" raus. Docs: per-agent-think.md UC-THINK-1…6 ❌ + ADR-0059.
- **IST-Wurzel (Mek-Analyse):** Boolean-off→`""`→Saver entfernt Key→Loader stripToNull→null→Feld
  weg. Mapper korrekt. Test-Gap: kein Roundtrip-Test über Saver→Loader→Request.
- **Inkremente:** Inc-1 core (Semantik + Roundtrip rot-erst, ThinkResolver/ThinkValueSupport/
  LlmConfig-CustomAgent-Clean-Break, UC-IDs an Bestands-Tests) → Inc-2 UI (Boolean-Form raus,
  Ollama-Combo, Basis-Checkbox raus) → Inc-3 Homepage → Da-Dok-Provider-Review.
- **Entscheidungen für Pauls Review sammeln** (Liste am Session-Ende in memory.md ergänzen).

## Offen (Paul)
1. **⏳ Option A** — Standalone-Peon-Review behält memory*/askUser. 2. **⏳ Docs-SOLL-Hygiene-Sweep**.
3. **❓ Fixture-Drift** test_project (pom/Dockerfile). 4. **❓ Header 0k estimate** (Jon, open-points).
5. **Merge → main** — Paul. 6. **R-CC-7** 🚧 Retry/Fehlerklassen-Backlog.