# ADR-0011: Agent Template as System Prompt Resource

**Status:** Accepted

## Context
The scaffold agent needs guidance on how to create AGENT.md, SKILL.md, and command files — including frontmatter structure, naming conventions, and file placement.

## Decision
`scaffold-agent.txt` is loaded as a resource in the scaffold agent's `getSystemPrompt()` via `PromptLoader.withDefault()`. Contains concise frontmatter examples for agents, skills, and commands — plus directory structure instructions (e.g. `~/.peon/agents/<name>/AGENT.md`).

## Consequences
Agent gets built-in structural guidance. Existing files on disk serve as additional examples the agent can reference via disk tools.
