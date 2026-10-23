# Known-benign warnings — llmpeon (accepted exceptions)

Read this BEFORE triaging build/IDE warnings: everything listed here is accepted — fix real NEW
warnings only. Cleanup 2026-09-10 (commits `51f43d2`/`a9124f1`/`56e9cc5`), 64 → 12; the remaining 13
are the accepted exceptions below. Keep this list current when a new exception is accepted.

- Plugin ×11 null-type-safety on method refs (`AIChatView:175-176`, `PeonAiService:469-470,508,639`,
  `AiAdvancedPreferenceView:120`, `ModelComboWidget:128`, `EclipseUtil:318`,
  `EclipseWorkspaceReadFileTool:158`, `StatusLineWidget:188`) — method refs to `@NonNull`-parameter
  functional interfaces; internal callers never pass null. (2026-09-12: `AiAgentStatusModel:48`
  moved with the agent-status module into core — now part of the core IDE-scope warnings below.
  2026-10-17 codegraph build: the list carried 9, the build shows 11 — `PeonAiService clearAll:
  AiAgent::clear` and `AiAdvancedPreferenceView:120` are pre-existing (verified at HEAD), just never
  listed. Line numbers drift with edits.)
- Plugin ×1 `resources/` class-folder (`.classpath` mirrors `Bundle-ClassPath`) — **must stay**:
  `ChatMarkdownWidget` loads `chat.html` via classloader AND OSGi `FileLocator`; dropping the entry
  risks breaking the chat view at IDE runtime.
- Core ×1 `MockLlmServer:98` unused TWR variable — needed for auto-close; variable-less
  try-with-resources is invalid Java (JLS 14.20.3). **javac-lint only — not visible in the Eclipse
  problems view.**
- Core IDE scope note (2026-09-10, Da-Dok review): the `llmpeon-core` IDE view additionally carries
  ~150 **pre-existing** JDT warnings (null-type-safety/unused-import) that are NOT part of the
  cleanup scope above — they never appear in the Maven gate. Sweep = own micro-cycle decision
  (PO + user), not an obligation of every cycle.
- `-Xlint`-diagnostic only (not in default build/IDE): this-escape ×10 (7 classes, none subclassed —
  intentional constructor-delegates-to-refresh pattern); opennlp-tools manifest `Class-Path` slf4j
  path quirk (upstream packaging).
