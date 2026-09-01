# Purpose

## Origin

Adapted as a lightweight project workflow from *WikiSkill: Compiling Agent Experience into Persistent Knowledge for Skill Evolution* (Tang et al., 2026; arXiv:2608.27454).

## Patterns addressed

- Useful lessons disappearing in archived plans or chat history.
- Repeating rejected skill changes because their outcome was not retained.
- Turning one-off project decisions into overly broad global instructions.
- Loading accumulated diagnostic history into agents that only need an active procedure.

## Local adaptation

FORgE reuses plans and focused evidence as its raw layer, `skills/wiki/` as its compact knowledge layer, and project `skills/*/SKILL.md` as its executable layer. It deliberately omits automated benchmarks and multi-agent optimization until their cost is justified.
