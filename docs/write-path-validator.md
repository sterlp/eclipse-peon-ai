# Write-Path Validator

**2026-09-04 (✅ done):** Pfad-Normalisierung vor Glob-Match (Bug-Hunt #9) — Traversal-Bypass
geschlossen, BDD R1 um Traversal-Fall erweitert.
**2026-09-04 (follow-up #9):** Normalisierung = `\`→`/` **und** `.`/`..`-Auflösung — unbedingt auf
allen Plattformen (Validator vergleicht nur `/`-Glob mit `/`-Pfad), gemischte Separator-Traversals
geschlossen, BDD R1 um Backslash-Fall.

## Purpose

Constrain **where an agent may write** without giving it bespoke, agent-specific file tools. The write
tools (disk + Eclipse workspace) stay shared; each agent contributes a **`WriteValidator`** — the same
way it contributes a tool filter — that vets the raw path the model passes before any write happens.

This is what keeps **Peon-PO (Jon)** inside `docs/` while reusing the normal write tools. It supersedes
the "decorator" framing of [ADR-0022](adr/0022-write-path-allowlist-decorator.md): the effect is
identical, but a validator injected at the write tool's path choke-point fits the `@Tool`-reflection
model better than a wrapping decorator.

## How it works

- `WriteValidator` is a tiny interface: `void validate(String path)` — it throws
  `IllegalArgumentException` when the path is not allowed (so the normal `SmartToolExecutor` path turns
  it into an AI-visible tool error **and** an `onProblem` for the user). `WriteValidator.ALLOW_ALL` is
  the no-op default.
- `AllowlistWriteValidator` holds a set of glob patterns, matched against the **normalized path** —
  the model-supplied path with `\` converted to `/` and `.`/`..` segments resolved, unconditionally on
  every platform (the globs are always `/`-based, so only like-with-like matching is meaningful; raw
  matching would allow path traversal, e.g. `docs/../../secret.txt` or `a/docs/..\..\secret.txt`). Globs are translated to regex (`*` → `.*`), compiled **once** and cached in the
  shared `RegexUtils`. The constant `WriteValidator.DOCS` = `AllowlistWriteValidator("*/docs/*", "*.md")`.
- `AiAgent.getWriteValidator()` returns `ALLOW_ALL` by default (a `default` method on the `AiAgent`
  interface). Only `AiPoAgent` (Jon) overrides it to `DOCS`. The validator rides on `ToolLoopRequest`,
  set in `AbstractAgent.doCall` alongside the tool filter, so the **shared** write-tool instances stay
  shared (KV-cache safe) and the check is per-agent-per-request.
- The write tools call the validator through the shared helper `AbstractTool.validateWrite(path)`
  (a no-op when there is no request, e.g. direct unit-test calls). `DiskFileWriteTool` calls it once at
  its single choke-point `resolve(String)`; the Eclipse workspace write tool has no single resolve, so
  it calls `validateWrite(path)` at the top of each write method (write/edit/replace/insert/delete, and
  both paths of rename). Read tools are never gated.
- Allowed patterns are also stated in Jon's system prompt so he self-restricts; the pattern set is a
  constant today and becomes user-editable config later.

## Use Case: an agent may only write where its validator allows

### R1 — Write tools consult the request's validator before every write

* GIVEN an agent whose `getWriteValidator()` returns `DOCS` (`*/docs/*`, `*.md`)
  WHEN the model calls a write tool with path `docs/feature-x.md`
  THEN the write succeeds.
* GIVEN the same agent
  WHEN the model calls a write tool with path `src/main/java/Foo.java`
  THEN `validate` throws, no file is written, the model receives the rejection as a tool error, and the
  user sees it via `onProblem`.
* GIVEN the same agent
  WHEN the model calls a write tool with path `a/docs/../../secret.txt`
  THEN `validate` throws — the path is normalized before glob matching
  (`a/docs/../../secret.txt` → `secret.txt`), so traversal cannot bypass the allowlist
  (Bug-Hunt #9, 2026-09-04).
* GIVEN the same agent
  WHEN the model calls a write tool with path `a/docs/..\..\secret.txt` (mixed separators)
  THEN `validate` throws — backslashes are normalized to `/` before segment resolution
  (`a/docs/..\..\secret.txt` → `secret.txt`), so mixed-separator traversal cannot bypass the allowlist
  (follow-up to Bug-Hunt #9, 2026-09-04).

### R2 — Default is allow-all

* GIVEN an agent that does not override `getWriteValidator()` (e.g. Peon-Dev)
  WHEN it calls any write tool
  THEN the write proceeds unchanged.

### R3 — Reads are never gated

* GIVEN the `DOCS` validator
  WHEN a read tool reads `src/main/java/Foo.java`
  THEN it succeeds — only tools reporting `isEditTool() == true` are validated.

### R4 — Patterns are compiled once and cached

* GIVEN the `DOCS` validator used across many writes
  WHEN it validates repeatedly
  THEN each glob is compiled to a regex exactly once (cached in `RegexUtils`).

## Notes

- Same mechanism for disk and Eclipse: both validate the **normalized** model-supplied path string, so the
  check is project-agnostic (no `IProject` needed to decide "allowed or not").
- Glob base nuance (from ADR-0022): matched against the normalized path, `*/docs/*` covers `docs` at depth
  while `*.md` covers any Markdown file; further base refinement when the pattern set becomes configurable.
