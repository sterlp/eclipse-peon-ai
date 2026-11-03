- code structure should follow skill: component architecture.
- JavaDoc must earn its tokens: document only non-obvious invariants, constraints, and rationale. Never duplicate existing docs—link to them instead.
- Write large files in chunks of about 5,000 tokens each. Insert a placeholder marker at the end of each chunk, then replace it with the next chunk.
- Plans are structured as vertical increments, each resulting in running software.

Spring tests (hard rule, all Spring projects):
- The Spring context may start AT MOST ONCE per test build. No context restarts, no @DirtiesContext, no mock-bean churn that forces a new context. A slow suite (minutes) is a defect, not a state — keep tests fast.

Language:
- Docs: German
- UI: English
- Code: English (also comments)