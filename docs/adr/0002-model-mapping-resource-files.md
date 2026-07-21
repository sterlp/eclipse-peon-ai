# ADR 0002 — The provider/model think mapping lives in resource files, not code

**Status:** Accepted

**Context:** In auto mode Peon must translate a generic "on" into the right value for the concrete
provider **and** model (OpenAI reasoning models → `high`; Opus → `adaptive`; other Claude → `enabled`;
an unknown gateway model → nothing). Hardcoding this in `AiProvider` grows an if-ladder and needs a
code change (and release) for every new model.

**Decision:** `ThinkModelMapping` loads one resource file per provider from
`org.sterl.llmpeon.core/src/main/resources/thinking/<PROVIDER>` (named after the `AiProvider` enum
constant), same loading path as the bundled prompts. Line format `pattern | on | off` (`#` = comment;
substring match, case-insensitive; first match wins; no match / no file → omit).

**Consequences:** New models are onboarded by editing a text file, no code change. An unknown OpenAI
model resolves to omit, so a non-reasoning gateway model works out of the box in auto mode. Only the
generic-on case consults the mapping; a concrete user value bypasses it.
