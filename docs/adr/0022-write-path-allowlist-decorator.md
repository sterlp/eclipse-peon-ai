# ADR-0022: Scope an agent's writes via an agent-provided write-path validator (glob allowlist)

**Status** · Accepted — realised as a validator, not a wrapping decorator (see Decision).

## Context
Peon-PO (Jon) must be kept from writing anywhere but the docs, but he should **reuse the existing
Eclipse-workspace and disk write tools** rather than get bespoke, Jon-only file tools. "Where may this
agent write" is a cross-cutting concern — a decorator + config keeps it out of the tools themselves and
lets other (custom) agents reuse it. It also fits the existing sandbox stance
([ADR-0015](0015-eclipse-sandbox-boundary.md)): the write tools already carry a boundary; this narrows
it per agent.

## Decision
Each agent contributes a **`WriteValidator`** the same way it already contributes a tool filter:
`AiAgent.getWriteValidator()` (a `default` method returning `ALLOW_ALL`, overridden only by `AiPoAgent`),
carried on `ToolLoopRequest`. The existing write tools (Eclipse-workspace write, disk write) call it
through the shared helper `AbstractTool.validateWrite(path)` before writing, and reject a non-match by
throwing. Disk write has a single choke-point (`resolve`); the Eclipse write tool calls `validateWrite`
at the top of each write method. This is a **validator injected at the choke-point, not a wrapping decorator** —
the effect is identical, but it fits the `@Tool`-reflection model (a delegating wrapper would not carry
the annotated methods cleanly) and keeps the **shared** write-tool instances shared (KV-cache safe).

The allowlist is a set of globs matched (OR) against the **raw path string the model supplied**,
translated to regex and compiled once + cached in the shared `RegexUtils`. In this first increment the
set is a **constant** `WriteValidator.DOCS` = `*/docs/*`, `*.md`; making it a **user-editable config
field** is a later increment. Reads are **not** gated. Glob semantics:

- `*/docs/*` (default) — a `docs/` folder at any depth. In Eclipse matched against the
  **project-root-relative** path (`<project-name>/docs/...`, workspace VFS); for disk tools against the
  working-dir / given path.
- `docs/*` — only at the (project) root; the leading segment is the sole difference from `*/docs/*`.
- `*.md` — any Markdown file, anywhere.

## Consequences
- No Jon-specific file tools; the docs scope is an agent-provided validator → reusable for other agents.
- The validator vets the **raw model-supplied path string**, so the check is project-agnostic (no
  `IProject` needed) and identical for disk and Eclipse; both call it at their resolve choke-point.
- Only `AiPoAgent` overrides `getWriteValidator()`; every other agent inherits `ALLOW_ALL`, so behaviour
  is unchanged for them and no per-instance mutation of the shared tools is required.
- A denied write throws `IllegalArgumentException`, which the existing `SmartToolExecutor` path surfaces
  as an AI-visible tool error **and** an `onProblem` — the agent can self-correct, the user sees why.
- The default `*/docs/*` + `*.md` gives Jon docs-anywhere; tightening/widening becomes a config change
  once the pattern set moves from constant to user-editable (later increment).
- The underlying write tool keeps auto-creating missing sub-paths, so an allowed `docs/` path
  materialises on the first write — no explicit "create the docs root" step is needed.
- Realised in [Write-Path Validator](../write-path-validator.md) (feature doc with the BDD rules).
