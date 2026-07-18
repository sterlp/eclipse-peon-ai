# Per-Agent Think

Thinking/reasoning is resolved and sent **per request**, so every agent — **Dev** (the default),
**Plan**, and each **Custom** agent — decides its own think value for its own provider and model.

The concrete failure this fixes: with one global think value you cannot plan with **GPT**
(`reasoning.effort=high`) and implement with **DeepSeek** (an OpenAI-compatible gateway that rejects
`reasoning.effort`) at the same time — the shared value reaches both providers and DeepSeek errors.
Per-agent values let the plan agent send `high` while the dev agent sends nothing.

Each agent owns three values plus one shared "send" flag:

| Value | Type | Meaning |
|-------|------|---------|
| `think_enabled` | boolean | Is thinking on for this agent right now? Flipped by the brain toggle. |
| `think_on_string` | string | Value sent when **enabled**. Empty → the heuristic decides (auto mode). |
| `think_off_string` | string | Value sent when **disabled**. Empty → send nothing. |
| `think_send` | boolean | Show the model's own reasoning and resend it next turn. **Global**, independent of the toggle. |

## Rules

Status: **✅ done** (BDD test green) · **❌ not done** (backlog). All rules below are ✅.

1. ✅ **Think is resolved per agent, per request.** Each agent produces one effective think **string**
   from its `(think_enabled, think_on_string, think_off_string)`. `AiProvider` translates that string
   into the provider's fixed attribute when the request is built. Agents never share a value — nothing
   is inherited between agents (empty means auto/nothing, never "copy another agent")
   ([0001](../adr/0001-per-agent-think-string.md)).

2. ✅ **`think_enabled` selects a string; it is not a capability gate.** "Off" means *send the
   off-value*, which is nothing only when `think_off_string` is empty. This lets a boolean provider
   send an explicit off (Ollama `think:false`, LM Studio `reasoning:off`).

3. ✅ **Auto vs. manual is all-or-nothing.** Both value strings empty → **auto**: the built-in
   provider/model heuristic applies. Setting **either** string → **manual**: the heuristic is off in
   **both** directions and the active string is used verbatim (empty active string → omit).
   The resolved value is:

   ```
   Search / Compact                      -> OMIT
   auto = think_on_string empty AND think_off_string empty
   think_enabled = true :  auto -> HEURISTIC(provider, model)   ; else -> SEND(think_on_string)
   think_enabled = false:  auto -> OMIT                         ; else -> SEND(think_off_string)
   ```
   `SEND(v)` translates `v` per provider; an empty `v` is OMIT.

4. ✅ **The attribute name is fixed per provider; the string sets only the value.** Empty vs. an explicit
   off-token is the key distinction: boolean providers **send** the explicit off, effort providers have
   no "off" and collapse it to OMIT.

   | Provider | Fixed attribute | `SEND(v)` | Empty / off-token |
   |----------|-----------------|-----------|-------------------|
   | OpenAI family (OpenAI, OpenAI-official/Azure, GitHub Models, GitHub Copilot) | `reasoning.effort` | level `high`/`medium`/`low`/`minimal` | **OMIT** (no valid "off") |
   | Ollama | `think` | `think:true` | empty → OMIT; off-token → **`think:false`** |
   | Anthropic | thinking type (+ budget) | `adaptive` / `enabled` | **OMIT** (no "off" type) |
   | LM Studio (custom) | `reasoning` | `reasoning:on` | empty → OMIT; off-token → `reasoning:off` |
   | Google Gemini / Mistral | build-time only | on/off at build time | off |

5. ✅ **The auto heuristic is data, not code.** `ThinkModelMapping` loads one file per provider from
   `org.sterl.llmpeon.core/src/main/resources/thinking/<PROVIDER>` (line format `pattern | on | off`;
   `#` = comment; substring, case-insensitive; first match wins; no match / no file → OMIT). An
   unknown OpenAI model → OMIT, so a non-reasoning gateway model works out of the box
   ([0002](../adr/0002-model-mapping-resource-files.md)). Current data:
   - `thinking/OPEN_AI` — `gpt`, `o1`, `o3`, `o4` → `high` (whole OpenAI family).
   - `thinking/ANTHROPIC` — `opus-4-8` / `opus-4-7` / `mythos` → `adaptive`, `claude` → `enabled`.

6. ✅ **Send-thinking is a separate, global switch.** `think_send` controls only whether the model's own
   reasoning is returned/shown and resent next turn (Qwen, DeepSeek, Mistral). `returnThinking =
   think_enabled OR think_send`; `sendThinking = think_send`. Because langchain4j exposes these only at
   build time, `think_send` is global (`PREF_SEND_THINKING_ENABLED`); the custom `think_send`
   frontmatter key is parsed but reserved ([0003](../adr/0003-send-thinking-independent.md)).

7. ✅ **The brain toggle acts on the selected agent and persists per agent.** Dev/Plan → preferences,
   Custom → its `AGENT.md`; no cascade.

8. ✅ **Search and Compact never think** — they always omit the attribute.

9. ✅ **Custom agents use `think_*` frontmatter; legacy `think:` is an alias** for `think_on_string`
   (implies enabled for an on-value).

## BDD asserts

- **Dev and Plan resolve independently** (rules 1, 3)
  GIVEN Dev `think_enabled=false` and Plan `think_enabled=true` with `think_on_string="high"`
  WHEN each agent builds its request
  THEN Dev sends no `reasoning.effort` AND Plan sends `reasoning.effort=high`.

- **Auto mode maps a known model, omits an unknown one** (rules 3, 5)
  GIVEN an OpenAI agent, both value strings empty, `think_enabled=true`
  WHEN the model is `gpt-5.5` THEN `reasoning.effort=high`
  WHEN the model is a non-reasoning gateway model THEN nothing is sent.

- **Manual mode disables the heuristic in both directions** (rule 3)
  GIVEN a custom agent with `think_off_string` set
  THEN the heuristic applies to neither on nor off
  AND an empty `think_on_string` while enabled sends nothing.

- **Off sends an explicit off-value on a boolean provider** (rules 2, 4)
  GIVEN an Ollama agent with `think_off_string="false"` and `think_enabled=false`
  THEN `think:false` is sent (not silence)
  WHILE an empty off-string would omit the attribute.

- **An agent can be turned fully off** (rules 2, 3)
  GIVEN an agent that currently thinks
  WHEN the user unchecks Thinking AND clears any off-value
  THEN `think_enabled=false`, `think_off_string` empty AND no thinking attribute is sent.

- **Send-thinking is independent of the toggle** (rule 6)
  GIVEN `think_send=true` AND a reasoning model that emits thinking even with the attribute omitted
  THEN the returned thinking is shown AND included in the next request.

- **The brain toggle persists on the selected agent only** (rules 1, 7)
  GIVEN a selected Custom agent
  WHEN the brain button is clicked
  THEN its `AGENT.md` gains `think_enabled:` AND no other agent changes.

- **Search and Compact never think** (rule 8)
  GIVEN any think configuration
  THEN the Search and Compact agents send no thinking attribute.

- **Legacy `think:` still works** (rule 9)
  GIVEN a custom agent with only `think: high`
  THEN it is enabled AND sends `high` as its on-value.

## Storage / keys

**Preferences (Dev = default/global, Plan):** `PREF_THINKING_ENABLED` (`llm.thinkingEnabled`, boolean),
`PREF_THINK_ON_STRING`, `PREF_THINK_OFF_STRING`, `PREF_PLAN_THINK_ENABLED`, `PREF_PLAN_THINK_ON_STRING`,
`PREF_PLAN_THINK_OFF_STRING`, `PREF_SEND_THINKING_ENABLED`. (Removed: `PREF_THINKING_ON/OFF/PLAN`.)

**Custom `AGENT.md` frontmatter:** `think_enabled`, `think_on_string`, `think_off_string`,
`think_send`; legacy `think:` read as an alias for `think_on_string`.

## ADRs

Only technical decisions live here; behavioural decisions are the Rules + BDD above.

- [0001](../adr/0001-per-agent-think-string.md) — per-agent String + resolver + per-provider translation.
- [0002](../adr/0002-model-mapping-resource-files.md) — auto mapping in resource files, not code.
- [0003](../adr/0003-send-thinking-independent.md) — `think_send` build-time/global (langchain4j limit).

## Known gaps

- **Per-agent `think_send`** — `returnThinking`/`sendThinking` are build-time only, so send is global
  (`PREF_SEND_THINKING_ENABLED`). The custom `think_send` key is parsed but not applied per request
  ([0003](../adr/0003-send-thinking-independent.md)).
- **Gemini / Mistral** — no per-request thinking subtype in the bundled langchain4j version; they
  follow the Dev/global `think_enabled` at build time only.
- **Provider-switch write-back** — when a boolean provider reads a leftover concrete value it is
  normalised in memory only; the stored preference is not rewritten (KISS).
