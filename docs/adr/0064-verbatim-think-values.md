# ADR-0064: Verbatim-Think — String-Provider senden exakt was gesetzt ist, nur Toggle-Provider interpretieren

> **Status:** Accepted (Paul, 2026-09-29) · **Supersedes:** Teile von [ADR-0059](0059-think-dropdown-empty-unset.md) (Off-Token-Semantik für String-Provider) · Feature-Doc: [per-agent-think.md](../per-agent-think.md) (R-THINK-11/12)

## Context

Paul-Smoke 2026-09-29: `think=none` auf OPEN_AI (LM-Studio-Server) kam **nicht** im Request an —
`ThinkResolver` mappte Off-Tokens für die OpenAI-Familie auf Omission („OpenAI kennt kein
off-Konzept"), das Modell dachte trotzdem weiter. Config sagte „aus", Verhalten war „an" — die
genaue Lügen-Klasse, die wir nicht bauen. Diagnose (Da Mek + Da Sniffa, Log-evidenz
`.bak_0.log:11108`): kein Transport-Bug, dokumentiertes SOLL von UC-THINK-1 — aber das SOLL selbst
stand auf einer Prämisse, die der SDK-Wirklichkeit widerspricht (`ReasoningEffort.Known`
**enthält `none`**), und auf der Checkbox-Ära: Der Resolver mit Off-Set und On-Mapping
(`ThinkModelMapping.resolveOn`) war nötig, als eine Checkbox Booleans produzierte — die gibt es
nicht mehr (ADR-0059).

## Decision

**Ein String-Provider bekommt exakt den String, den der User setzt; nur Toggle-Provider
(Booleans-API) interpretieren.**

1. **Verbatim-Provider** (ThinkSupport ≠ Toggle — OPEN_AI-Familie OPEN_AI/OPEN_AI_OFFICIAL/
   GITHUB_MODELS/GITHUB_COPILOT + LM_STUDIO): gesetzt → unverändert in den provider-eigenen
   Think-Kanal (`reasoning_effort` / `reasoning` / Provider-Params). `"true"` → `reasoning_effort:
   "true"` as-is, `"none"` → `"none"`, `"xhigh"` → `"xhigh"`. **Leer = unset = nichts senden**
   (aus ADR-0059, unverändert).
2. **Toggle-Provider (OLLAMA):** String → Boolean interpretiert. On-Tokens
   `true/on/yes/ja`, Off-Tokens `false/off/no/nein/none` (case-insensitive); nicht-off und
   nicht-leer = on (bestehende Regel, erweitert um ja/nein). Unset (`null`) = Feld weg —
   bleibt vom expliziten `think:false` unterschieden.
3. **Gestorben:** der Off-Token-Omission-Pfad, das On-Mapping für String-Provider
   (`ThinkModelMapping.resolveOn` für OPEN_AI, `/thinking/OPEN_AI`), die `Off/Auto`-Pseudo-Entries
   in den Think-Combos (die Combo listet die echten Provider-Werte). Anthropic/Gemini
   behalten ihre **provider-eigene** Übersetzungsschicht (Budget/Config/Param-Namen) — die ist
   API-Format, keine Interpretation.

## Consequences

- **Verhaltensänderungen (Clean Break, keine Migration):** OPEN_AI Off-Tokens gehen jetzt als
  `reasoning_effort:"none"` (bzw. wörtlich) an den Gateway statt zu fehlen; LM_STUDIO `true`
  wird jetzt `"true"` gesendet statt `"on"`; ein Legacy-Frontmatter-Boolean (`think: false`) auf
  einem String-Provider geht als String `"false"` durch (Garbage in — sichtbar im Request-Log —
  garbage out; die Dropdowns sind der Guard).
- Der User kann „falsche" Strings senden — gewollt (Paul: „der User kann eingeben was richtig
  ist und wir senden genau was er setzt"). Keine Validierung, keine Stillkorrektur.
- Weniger Code: `ThinkResolver` schrumpft auf den Toggle-Zweig; open-points (b)/(c)
  (Off-Varianten, Copilot/Models-Param-Tests) werden mit der Neubewertung der Testbasis
  mitabgearbeitet.
- Off-Konzept auf einem Gateway, das nur `reasoning_effort` versteht: kein künstlicher
  „aus"-Zustand mehr — wer aus haben will, wählt einen Provider-Typ, der off kennt (LM_STUDIO),
  oder `none` (OpenAI-SDK-Konvention).
