---
name: writing-skills
description: Creates and maintains Agent Skills following the agentskills.io specification. Use when authoring new skills, converting existing instructions into skill format, or reviewing skill compliance.
---

# Writing Agent Skills

Skills follow the open [Agent Skills](https://agentskills.io/specification) format.

## Skill Structure

A skill is a **folder** containing a `SKILL.md` file. The folder name must match the `name` field.

```
my-skill/
├── SKILL.md          # Required: YAML frontmatter + markdown instructions
├── scripts/          # Optional: executable code
├── references/       # Optional: detailed docs loaded on demand
└── assets/           # Optional: templates, data files
```

## SKILL.md Format

Every `SKILL.md` starts with YAML frontmatter, then markdown body:

````markdown
---
name: my-skill-name
description: Does X and Y. Use when the user needs to Z or mentions A.
---

# My Skill Name

## When to use
...

## How to do the thing
1. Step one
2. Step two

## Common pitfalls
...
````

### Required Frontmatter

| Field | Rules |
|---|---|
| `name` | 1-64 chars. Lowercase letters, numbers, hyphens only. No leading/trailing/consecutive hyphens. Must match folder name. |
| `description` | 1-1024 chars. Write in **third person**. Describe what it does AND when to use it. Include keywords for discovery. |

### Optional Frontmatter

| Field | Purpose |
|---|---|
| `license` | License name or reference to bundled file |
| `compatibility` | Environment requirements (e.g. "Requires git, docker") |
| `metadata` | Arbitrary key-value pairs (author, version, etc.) |
| `allowed-tools` | Space-delimited pre-approved tools (experimental) |

### Name Examples

```yaml
# Good
name: pdf-processing
name: code-review
name: eclipse-ifile-paths

# Bad
name: PDF-Processing    # uppercase
name: -pdf              # leading hyphen
name: pdf--processing   # consecutive hyphens
name: helper            # too vague
```

### Description Examples

```yaml
# Good - specific, third person, includes triggers
description: Extracts text and tables from PDF files, fills forms, merges documents. Use when working with PDF files or when the user mentions PDFs, forms, or document extraction.

# Bad - vague, wrong person
description: I help with PDFs.
```

## Body Content Guidelines

The markdown body has no required structure, but follow these principles:

1. **Be concise.** Claude is smart -- only add context it doesn't already have.
2. **One default, not many options.** Pick the best approach. Mention alternatives only when context demands it.
3. **Use consistent terminology.** Pick one term per concept and stick with it.
4. **Keep under 500 lines.** Move detailed reference material to separate files.
5. **No time-sensitive info.** Avoid "after August 2025, use the new API".
6. **Use forward slashes** in all file paths, even on Windows.

## Progressive Disclosure

Skills load in three stages to manage context efficiently:

1. **Discovery**: Only `name` + `description` are loaded at startup (~100 tokens per skill).
2. **Activation**: Full `SKILL.md` body is loaded when the task matches.
3. **Resources**: Referenced files (`references/`, `scripts/`, etc.) load only when needed.

Reference files from `SKILL.md` like this:

```markdown
**Form filling details**: See [references/forms.md](references/forms.md)
```

Keep references **one level deep** from SKILL.md. Avoid chains like A -> B -> C.

## Workflows

For multi-step tasks, provide a checklist Claude can track:

````markdown
## Migration workflow

```
- [ ] Step 1: Back up current state
- [ ] Step 2: Run migration script
- [ ] Step 3: Validate output
- [ ] Step 4: Clean up temporary files
```
````

## Quick Checklist

Before publishing & finishing a skill, verify:

- [ ] `SKILL.md` is inside a folder matching the `name` field
- [ ] Frontmatter has `name` and `description`
- [ ] `name` is lowercase, hyphenated, 1-64 chars - same as the folder name
- [ ] `description` is third person, specific, includes trigger keywords
- [ ] Body is under 500 lines
- [ ] File paths use forward slashes
- [ ] References are one level deep
- [ ] Consistent terminology throughout
