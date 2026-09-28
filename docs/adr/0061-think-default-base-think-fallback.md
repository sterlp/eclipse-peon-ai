# ADR 0061 — Think-Default: leerer Agent-Think erbt den Base-Think (Dev-Slot)

**Status:** Accepted (2026-09-28, Paul) · **Fachdoc:** [per-agent-think.md](../per-agent-think.md) (R-THINK-10) · **Verwandt:** [ADR-0059](0059-think-dropdown-empty-unset.md) (leer = unset, Issue #149), [ADR-0060](0060-model-config-widget-live-widget-reads.md) (Basic-Page-Widget)

## Context

Seit der Per-Agent-Trennung (ADR-0001) liest jeder Agent seinen Think **verbatim** — leer = unset
= nichts senden (ADR-0059). Mit der Entfernung der Base-Think-Checkbox in #149 gab es damit
**keinen Default mehr**: wer 6 Agenten mit demselben Think-Level fahren will, konfiguriert 6 Felder.
Paul (2026-09-28): „der Think sollte auch ein Default geben, den gab es mal, der ist verloren
gegangen. Offline local default will ich mit der einfachen Config durchkommen — nur für Compact
und Search definiere ich ein anderes Think."

## Decision

Fallback-Kette bei der Think-Resolution, einheitlich für alle Agenten (Core + Custom):

```
Agent-Think gesetzt (auch off)  → verbatim
Agent-Think leer                → Base-Think (Dev-Record, llm.agent.dev.think)
beide leer                      → unset → nichts senden
```

- Der Dev-Slot trägt die Base-Semantik bereits (IST: `LlmConfig.devAgentConfig()` liest den
  DEV-Record, `LlmConfigSaver` schreibt DEV-Model nach `llm.model` und DEV-Think nach
  `llm.agent.dev.think`) — kein neuer Key, keine Migration.
- Explizites off gewinnt immer (Escape aus dem Default). Ollama bleibt vom R-THINK-2-Fix
  unberührt: `think:false` überlebt die Persistenz.
- Umsetzung im Core (`LlmConfig.agent*Config`-Methoden + Custom-Agent-Path) — ein zentraler
  Resolve-Punkt, keine Fallback-Logik in den Agenten.

## Consequences

- „Empty = nichts senden" (ADR-0059/R-THINK-1) gilt nur noch auf der untersten Stufe; auf
  Agent-Ebene bedeutet leer jetzt „erbe den Default". Wer trotz Default nichts senden will:
  explizites off. Homepage-Doku (configuration.md, advanced-configuration.md) muss die Kette
  nennen.
- Basic-Page = Default-Editor (Dev-Slot), Advanced = Ausnahmen — die Zweiteilung der Config-UI
  (Pauls Intention) wird semantisch sichtbar.
- Kein neues Persistenz-Format, Clean-Break-Prinzip verletzt nichts (gleiche Keys, neue
  Resolution).
