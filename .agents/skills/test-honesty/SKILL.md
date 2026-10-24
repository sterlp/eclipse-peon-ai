---
name: test-honesty
description: Recurring shapes of dishonest tests in this repo — green-without-feature, falsifiability per test, surface pin inventories, green-before declarations, stub-LLM both directions — with origin stories. The one-liner rules stay in AGENTS.md/AGENTS-DEV.md.
---

# Test honesty — recurring shapes & origin stories (migrated from AGENTS-DEV.md, 2026-10-20)

**Trigger:** writing tests, reporting an increment green, mutation checks, test pin inventories.

The one-liner rules live in `AGENTS.md` / `AGENTS-DEV.md`; this skill carries the recurring shapes
and the origin stories. These bit us repeatedly in this repo — check them before reporting an
increment green.

## A test that would also pass without the feature is not a test

Recurring shapes here (2026-09-03, cycles 2b-1…3b):

- cleanup in `finally` running *before* the assertion
- assertions on a constant suffix only
- a test that builds the object graph *directly* and thereby bypasses the resolution chain it
  claims to prove

## Falsifiability per test

- **Prove falsifiability per test, naming the path the mutation hits** — not "all red under mutation
  X". A mutation that only flips the filter says nothing about the cropping.
- **New branch in the production code ⇒ one test per branch** — the plan's test inventory derives
  from the *branching of the NEW code* (null guard, case normalisation, default fallback, suffix
  match), not only from the doc's BDD bullets. Mutation proof per branch: each mutation (drop the
  null guard, drop `toLowerCase`, change the charset default, `endsWith` → `equals`) must turn
  **exactly its own test** red — a branch whose mutation leaves the suite green is unpinned,
  whatever the doc said. (Origin 2026-10-24, bug-triage T-2 R-WEB-13: 4 of 6 new branches unpinned,
  all green under mutation; Da-Dok review F1.)

## Surface format changed → pin inventory

- **Grep-inventory ALL test pins on that surface** — never trust the plan/doc list as complete
  (2026-09-19 read-numbers: plan named 4 raw-read pins, IST had 13; all adaptations purely
  mechanical).

## "Green before the change" declarations

- **Only for tests that can actually run against the pre-change state.** A new test coupled to the
  NEW type (e.g. asserts `instanceof Combo` where the old code had `CCombo`) is swap-falsifiable by
  construction and is declared as such — never as a regression guard that "was green before".
  (Origin 2026-09-12, config-cycle-2: all 3 R-A4 tests were red before the swap; the plan had
  declared 2 of them "green before and after".)
- If a test IS green before the fix, say so before building it — declare it a
  characterization/regression test in the plan; never present it as proof of the rule. Reporting this
  as a blocker is the correct move, not a failure.

## Stub/mocked-LLM tests: both directions

- **Assert BOTH directions: what is SENT and what is RECEIVED.** Assert the captured request payload
  (the actual built messages/parameters), not just that a call happened — whenever the send path
  contains logic (dedup, filtering, truncation, mapping), that logic IS the feature under test.
  (Origin 2026-09-11: `AiCompressorAgent` dedup was inverted → compact input always empty; both
  existing tests asserted only the system prompt / the response side and stayed green for the broken
  send path.)
