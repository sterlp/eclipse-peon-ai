# Task 6: Docs Update — Design File + Commands Doc

**Feature Plan**: [commands-as-standing-orders.md](./commands-as-standing-orders.md)
**Depends on**: [Task 4](./04-aichat-view-replace-oneshot.md)

> Note: the doc tree moved. Design/HOW docs live in `docs/` (not in VitePress); published
> user docs live in `homepage/src`. The old `doc/...` paths are gone.

## Goal

Update user-facing documentation and create the design reference file.

## Files

| File | Action |
|------|--------|
| `docs/standing-orders-design.md` | Create new design file (purpose, data flow, components, notes). |
| `homepage/src/setup/commands.md` | Update "Usage" step 3 + "Effect": commands are standing orders that survive compaction, not system-prompt replacements. Skills behave the same. |

## Notes

- `docs/` is not part of VitePress → no `homepage/.vitepress/config.ts` change.
- `homepage/src/setup/commands.md` already has a sidebar entry → no config change.
- `homepage/src/setup/agents-and-skills.md` does not claim system-prompt replacement → no change needed.

## Verification

1. `docs/standing-orders-design.md` exists and reflects the mechanical re-injection via `clearMemory()`.
2. `homepage/src/setup/commands.md` "Effect"/"Usage" reflect standing-order + compaction survival.
3. `homepage/build-docs.sh` builds without dead-link errors.
