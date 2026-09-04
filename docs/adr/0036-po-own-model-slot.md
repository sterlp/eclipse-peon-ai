# ADR-0036 — PO-Agent bekommt einen eigenen Model-Slot (supersedes ADR-0023)

**Status:** Accepted (2026-09-03) — supersedes [ADR-0023](0023-po-model-plan-slot.md)

## Context

[ADR-0023](0023-po-model-plan-slot.md) ließ Jon (Peon-PO) den **Plan-Slot** mitbenutzen, um
einen weiteren Config-Slot zu sparen. Seit dem Config-Umbau (2a/2b/2c) ist ein Slot billig:
`llm.agent.<id>.<field>` ist ein generisches Key-Schema, `AgentModelConfigSection` ein
wiederverwendbares Widget, `EffectiveConnection` löst pro Agent auf.

Der geteilte Slot verhindert aber eine real gewünschte Konfiguration: der User fährt
PO = Claude, Plan = GPT-5, Dev = Sonnet/Kimi, Compact = Sonnet, Search = Kimi/Sonnet. PO und
Plan sind unterschiedliche Rollen (Design/Verhandlung vs. Zerlegung/Slicing) mit
unterschiedlichem Modellprofil — sie teilen sich zu Unrecht eine Einstellung.

## Decision

1. `AgentModelConfig.PO = "po"` als fünfter Core-Agent-Slot, vollwertig (model/url/apiKey/
   think/extraBody), Keys `llm.agent.po.*`.
2. Fünfte Section in `AiAdvancedPreferenceView`, Reihenfolge PO, Dev, Plan, Search, Compact.
3. **Fallback = Base-Config, nicht Plan** — identisch zum Dev-Agenten. Der implizite
   PO→Plan-Bezug entfällt ersatzlos.
4. **Clean Break, keine Migration** (konsistent zum Rebuild-Prinzip aus dem Config-Umbau):
   bestehende Installationen starten mit leerem PO-Slot, also auf Base.

## Consequences

- Die gewünschte Modell-Matrix ist konfigurierbar; PO- und Plan-Kosten/Latenz werden getrennt
  steuerbar.
- Ein User, der bisher bewusst über den Plan-Slot auch den PO umgestellt hatte, muss den
  PO-Slot einmalig neu setzen (dokumentiert auf der Homepage).
- Der Connection-Cache profitiert: gleiche Identität → weiterhin geteilte Verbindung, ein
  eigener Slot erzeugt also nur dann eine zusätzliche Connection, wenn er wirklich abweicht.
- Regeln + BDD: [advanced-configuration.md](../advanced-configuration.md) R-PO1…R-PO4.
