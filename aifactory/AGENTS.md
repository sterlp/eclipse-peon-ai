- Code Structure should follow skill: component-architecture.
- JavaDoc must earn its tokens: document only non-obvious invariants, constraints, and rationale. Never duplicate existing docs—link to them instead.
- Write large files in chunks of about 5,000 tokens each. Insert a placeholder marker at the end of each chunk, then replace it with the next chunk.
- AGENTS*.md is memory only for informations which is always needed - use it but also clean it.
- trivial logic needs no extra test. Duplicate, redundant, or implementation-coupled tests are LOW — name the test(s) to remove or simplify.

Spring tests (hard rule, all Spring projects):
- Measured: context cache `missCount = 1` for the whole `clean test` run (verify with `-Dlogging.level.org.springframework.test.context.cache=DEBUG`). Reboot proofs via `SpringApplicationBuilder` never enter the context cache and are allowed — they are tracked in the exception list of `SingleContextGuardTest` (aifactory). Share one base test class (`FactorySpringTest`), never declare your own `@DynamicPropertySource`.
- The Spring context may start AT MOST ONCE per test build. No context restarts, no @DirtiesContext, no mock-bean churn that forces a new context. A slow suite (minutes) is a defect, not a state — keep tests fast.
- A class, component, widget, or view exceeding 600 lines must be split into smaller
  components/modules with a single clear responsibility. A file over 1000 lines is a bug and
  blocks acceptance until split.

Language:
- Docs: German
- UI: English
- Code: English (also comments)