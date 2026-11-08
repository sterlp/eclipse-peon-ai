---
name: antd
description: Use for any Ant Design (antd) work in factory-ui - components, props, Design Tokens, theming, deprecations, migrations. Supplies the official agent workflow plus the @ant-design/cli for offline API knowledge.
---

# Ant Design (antd)

`factory-ui` builds its UI on **antd v6** (`antd ^6.7.x`, `@ant-design/icons ^6.x`, React 19,
Vite, vitest + jsdom). antd's API drifts between majors: what an LLM memorized from v4/v5 is
often deprecated or removed in v6. Never write antd code from memory.

## Mandatory before writing antd code

1. Read https://ant.design/docs/react/for-agents.md
2. Read https://raw.githubusercontent.com/ant-design/ant-design-cli/main/skills/antd/SKILL.md
3. Verify the exact API of every component touched with the CLI (`antd info <Component>`) or the
   per-component doc `https://ant.design/components/<name>.md`.
4. Take deprecation warnings seriously: if the CLI/lint marks a prop deprecated, use the
   replacement, do not "keep it because it still works".

If skills can be installed in this environment: `npx skills add ant-design/ant-design-cli`.

## CLI - offline knowledge and project tools

`@ant-design/cli` ships all metadata locally (props, tokens, demos, changelogs for antd
v3/v4/v5/v6), queried offline in milliseconds.

```bash
npm install -g @ant-design/cli
```

| Command | Purpose |
| --- | --- |
| `antd list` | all components with versions |
| `antd info Button` | props, types, defaults |
| `antd doc Button` | full markdown docs |
| `antd demo Select basic` | runnable demo source |
| `antd token DatePicker` | Design Token values (v5+) |
| `antd design.md` | design-language document |
| `antd semantic Table` | `classNames` / `styles` structure |
| `antd changelog 5.0.0 6.0.0 Select` | API diff across versions |
| `antd doctor` | diagnose project issues |
| `antd env` | env info for bug reports |
| `antd usage ./src` | analyze antd imports |
| `antd lint ./src` | deprecated APIs & best practices |
| `antd migrate 5 6` | migration guide |
| `antd migrate 5 6 --apply ./src` | agent-ready migration prompt |
| `antd mcp` | MCP server for IDE integration |
| `antd setup --client claude` | set up MCP/skill for agents |
| `antd upgrade` | upgrade the CLI |

Use `antd lint ./factory-ui/src` before declaring antd changes done; use `antd usage` to find
remaining old-API imports during a migration.

## Design language

`https://ant.design/design.md` describes the default light theme of antd v6 (colors, typography,
4px spacing grid, radius rules, elevation, component archetypes, Do's/Don'ts). Our own sheet is
[`ant-design.md`](ant-design.md), next to this file - read it before hand-writing styles or
picking colors. It is the single owner of our measures (`factory-ui/src/index.css` and
`docs/chat.md` cite it): link it, never fork a copy.
Key constraints for this repo: tokens over hard-coded hex (`#FFF`/`#FAFAFA` are forbidden in
product code), 4px grid only, weights 400/600 only, preset palette reserved for tags/charts,
one `primary` action per screen, theming via `ConfigProvider` (`theme.token`,
`theme.algorithm`, `theme.components`), never via ad-hoc CSS that bypasses tokens.

## Docs for LLM context

| File | Content |
| --- | --- |
| https://ant.design/llms.txt | navigation to all docs/components |
| https://ant.design/llms-full.txt | full component docs (EN) |
| https://ant.design/design.md | design-language description |
| `https://ant.design/components/<name>.md` | single component docs |

## MCP (optional IDE integration)

```json
{
  "mcpServers": {
    "antd": { "command": "npx", "args": ["-y", "@ant-design/cli", "mcp"] }
  }
}
```
