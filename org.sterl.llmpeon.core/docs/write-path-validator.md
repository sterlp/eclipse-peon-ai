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
the "decorator" framing of [ADR-0022](../../docs/adr/0022-write-path-allowlist-decorator.md): the effect is
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
- **Fehler-Transport (R5):** Das Ergebnis eines UI-Thread-Aufrufs wird über den geteilten Helfer
  `EclipseUtil.await(future, timeout, what)` entpackt — `ExecutionException`/`CompletionException`
  werden auf die echte Ursache zurückgeführt und diese wird weitergeworfen. Nur ein echtes
  `TimeoutException` erzeugt den „Timeout"-Text; `InterruptedException` setzt das Interrupt-Flag.
  Zweite Verteidigungslinie: `SmartToolExecutor` behandelt ein `IllegalArgumentException` **an
  beliebiger Stelle** der Cause-Kette als Tool-Error.

**Dynamische Roots (✅ 2026-09-08, erweitert 2026-10-23 — [project-skills.md](project-skills.md) R10/R17 +
[ADR-0043](../../docs/adr/0043-scaffold-write-validator.md)):** der Scaffold-Agent bekommt als zweiter
Validator-Nutzer **dynamische Roots** (Supplier auf `configDir` + `~/.agents/skills` + `.agents/skills` der
offenen Projekte — zur Call-Zeit aufgelöst), kein statisches Glob-Set. `AllowlistWriteValidator` wird dafür
erweitert (statische Globs von Jon bleiben unverändert). Wiring-Nachweis an der Composition Root:
`PeonAiServiceTest.scaffoldWriteValidator_acceptsHomeUserAgentsRoot` (User-Root + Projekt-Root akzeptiert,
fremde Home-Pfade abgelehnt — Mutations-Nachweis `roots.add(agentsSkillsDir)`).

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

### R5 — Eine Ablehnung erreicht das Modell unverfälscht (✅ done, 2026-10-21, `fix/write-denied-wrapped`)

Ein abgelehnter Write ist eine **erwartete Antwort** an das Modell — kein Fehlerfall und erst recht kein
Timeout. Die Begründung des Validators erreicht den LLM als Tool-Ergebnis **wörtlich**, also auch dann,
wenn sie über Wrapper gelaufen ist (Future/UI-Thread, `ToolExecutionException`, `ExecutionException`).

* GIVEN ein Agent mit `DOCS` (`*/docs/*`, `*.md`)
  WHEN der Editor-Write (`eclipseUpdateOpenFile`) auf `/ui-automate/config.toml` läuft
  THEN lautet das Tool-Ergebnis `Write denied: '/ui-automate/config.toml' is outside this agent's
  allowed paths …`, die Datei ist unverändert, und weder „Timeout" noch
  `java.util.concurrent.ExecutionException` kommen darin vor.
* GIVEN dieselbe Ablehnung, verpackt als `IllegalStateException` → `ExecutionException` →
  `IllegalArgumentException`
  WHEN der Tool-Loop sie ausführt
  THEN erkennt er das `IllegalArgumentException` **irgendwo** in der Cause-Kette als Tool-Error und
  gibt dessen Message zurück — kein generisches `Tool failed - check why`.
* GIVEN der UI-Thread antwortet wirklich nicht innerhalb des Timeouts (2 min)
  WHEN der Tool-Aufruf darauf wartet
  THEN nennt der Fehler das Timeout — „Timeout" wird nur bei einem echten Timeout behauptet.

## Notes

- Same mechanism for disk and Eclipse: both validate the **normalized** model-supplied path string, so the
  check is project-agnostic (no `IProject` needed to decide "allowed or not").
- Glob base nuance (from ADR-0022): matched against the normalized path, `*/docs/*` covers `docs` at depth
  while `*.md` covers any Markdown file; further base refinement when the pattern set becomes configurable.
