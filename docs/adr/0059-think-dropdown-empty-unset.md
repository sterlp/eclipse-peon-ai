# ADR-0059: Think — Checkbox raus, leer = unset (Issue #149)
> **Note (2026-09-29):** Die Off-Token-Semantik für String-Provider (Omission bei OPEN_AI & Co.) ist
> durch [ADR-0064](0064-verbatim-think-values.md) superseded — „leer = unset" bleibt.

**Status:** Accepted (2026-09-27, Paul)

## Context

Issue #149: Peon 2.12.2 sendet an Ollama nie `"think": false` — egal welche Kombination der
Think-Checkboxen. Ursache (IST-Kette): `ThinkValueSupport.booleanValue(false)` → `""` →
`LlmConfigSaver.saveOrRemove` entfernt den Key („empty = unset") → Loader `stripToNull` → `null`
→ `toOllamaThink(null)` → Feld weggelassen. Der Boolean-Checkbox-Wert und das Unset-Signal
kollidierten in derselben Kodierung (`""`) — für die Boolean-Form war `think:false` über die UI
strukturell unerreichbar. Reporter zusätzlich verwirrt über zwei Optionen („supports thinking" vs.
„wants thinking").

## Decision

1. **Eine Kontrolle:** per-agent Think = ein editierbares Dropdown, keine Checkbox.
   `ThinkSupport.Boolean` entfällt ersatzlos (Clean Break). Ollama-Items: `""`, `true`, `false`.
2. **Leer = unset = nichts senden** — für alle Provider einheitlich (AGENTS.md „Empty means
   unset"). Wer aus stellen will, wählt explizit `false`/`none`. Das Modell entscheidet bei unset.
3. **Off-Tokens** (`false`/`FALSE`/`none`/`no`/`off`, case-insensitive) = explizites off — Ollama
   `think:false`, LM Studio `reasoning=off`, OpenAI/Anthropic ohne off-Konzept lassen reasoning weg.
4. **Basis-Checkbox „Default model supports thinking" raus** — funktional tot (kein Request-Einfluss);
   `isThinkSupported()` leitet sich aus dem Think-Wert ab. „Show and resend thinking" (Transport,
   langchain4j build-time) bleibt.
5. **Custom Agents:** `think_supported` (und `think_on_string`/`think_off_string`) fliegen
   **komplett raus** (Paul 2026-09-27) — abgeleitet wird ausschließlich aus dem Think-Level
   (`think`-Frontmatter, ein String). Nicht gesetzt → nichts senden. Legacy-Keys leskompatibel,
   Migrate-on-write zu `think`.

## Consequences

- Der Issue-#149-Bug ist durch Vereinfachung gefixt: `"false"` ist nicht leer → Saver behält den
  Key → Loader → `think:false`. Zusätzlich Roundtrip-Test als Regression (UC-THINK-2).
- Bestehende Configs mit leerem Think ändern nichts (unset bleibt unset). Wer vorher die Checkbox
  off hatte, bekommt jetzt unset (Modell denkt default) — er muss `false` wählen. Verhaltensänderung
  bewusst akzeptiert; Homepage-Doku nennt sie.
- `ThinkResolver.toOllamaThink("")` ändert `FALSE` → `null` (pinnierter Test angepasst); `isOff`
  behandelt unset/off für Omit-Entscheidungen identisch, `toOllamaThink` unterscheidet.