---
name: repo-api-traps
description: Verified repo-specific API traps — langchain4j customParameters produces a duplicate JSON key (not an override), CompletableFuture.get() on a self-cancelled future throws an unwrapped CancellationException. Read before touching those surfaces; don't re-derive.
---

# Repo-specific API traps (verified, don't re-derive; migrated from AGENTS-DEV.md, 2026-10-20)

**Trigger:** langchain4j, CompletableFuture/Cancel, JSON extra body.

- **langchain4j serializes `customParameters` via `@JsonAnyGetter` NEXT TO the typed fields:** the
  same key in the extra body produces a **duplicate JSON key**, not an override. "User body wins"
  must be implemented explicitly by clearing the typed field (see
  `docs/adr/0039-temperature-body-precedence.md`).
- **`CompletableFuture.get()` on a future YOU cancelled yourself throws `CancellationException`
  UNWRAPPED**, not wrapped in `ExecutionException` — a catch on `ExecutionException` silently misses
  it (this hid the model-list race, see
  `docs/adr/0040-model-list-single-flight-secret-masking.md`).
