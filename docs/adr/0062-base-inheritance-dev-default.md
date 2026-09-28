# ADR-0062 — Base-Inheritance: Dev = Default, alle NULL-Slots erben Base

**Status:** Accepted (2026-09-28, mit Paul)
**Kontext:** [default-inheritance.md](../default-inheritance.md) · supersedes teilweise die
„model null = provider default"-Semantik aus dem Per-Agent-Slot-Umbau ([ADR-0036](0036-po-own-model-slot.md))

## Context

Im Per-Agent-Slot-Modell (ADR-0036) war `model=null` je Slot bewusst „provider default" — nur PO
erbt das Base-Model. Paul erwartete nach dem Widget-Zyklus: Default setzen → alle Agenten laufen.
Zusätzlich Regression aus #125 (`1e6aa745`): Runtime-Load liest nur InstanceScope, die JFace-Page
entfernt Instance-Keys, die dem DefaultScope-Default entsprechen → Base-URL null →
„baseUrl cannot be null or blank" (langchain4j), Advanced-Refresh tot.

## Decision

1. **dev bleibt der Default-Slot** — kein separater `default`-Slot (doppelte Semantik ohne Gewinn;
   Think-Fallback ADR-0061 nutzt denselben Slot).
2. **Vollvererbung:** jeder NULL-Slot (Core: PLAN/COMPACT/SEARCH; Custom: leeres `model:`) erbt
   Base-Model — wie PO schon heute. URL/Key erben bereits (effectiveConnectionFor).
3. **Base-URL-Fallback auf Provider-Default** (Ollama localhost:11434), sonst ehrlicher Fehler statt
   langchain4j-Exception.
4. **Sichtbarmachung:** Advanced-DEV-Sektion = „Dev (Default)", gespiegelte Basic-Felder, ganz oben.

## Consequences

- Ein Base-Model reicht für den Betrieb aller Agenten; Slot-Einträge sind echte Overrides.
- **Dev-Override-Keys enden (Clean Break):** `llm.agent.dev.url/apiKey/model` werden nie mehr
  gelesen, beim nächsten Save geräumt — dev ist Default-Slot, kein Override-Slot (sonst unsichtbarer
  Override gegen die UI).
- Wer LM-Studio-„provider default" ohne Base-Model will: Base-Model leer lassen → Guard greift
  (ehrlich, kein stiller Fallback).
- Die #125-Regression (DefaultScope unsichtbar für Runtime) wird im URL-Pfad über den
  Provider-Default entschärft; das generelle JFace-„Default entfernen"-Verhalten bleibt beobachtbar
  — Modell/Temperature bleiben bewusst „empty = unset" (AGENTS-Regel), keine Mischung.
- Fallstricke: JFace-Entfernungs-Semantik beim Schreiben von Default-Werten; `llm.agent.dev.model`
  bleibt ein nie-gelesener Key (Saver schreibt Base).
