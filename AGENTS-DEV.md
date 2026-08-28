# AGENTS-DEV.md — implementing (the HOW)

Project-specific additions for the dev phase — the method lives in the Jon skill, the build
rules in the base `AGENTS.md`.

- `mvn clean install` makes the artifacts available for partial module builds.
- **Never write to `docs/`** — owned by the PO + the user; the story's ❌ → ✅ flip is left to
  the docs owner. Track progress only in the plan file and the task files you create.
- **User docs (homepage / VitePress):** `homepage/` is the published user documentation,
  separate from `docs/`. Source in `homepage/src` (`srcDir` in `homepage/.vitepress/config.ts`);
  build via `homepage/build-docs.sh`. New page → update the sidebar/nav in
  `homepage/.vitepress/config.ts`. A user-facing page is added only once the feature ships
  (rule ✅) — never document unbuilt behaviour to users.
