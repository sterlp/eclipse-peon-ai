# Eclipse File Write Tool Problems — Lessons Learned

## Session: 2026-05-22 (Memory Feature Implementation)

### Problem 1: Cascading `replaceWorkspaceLine` Corruption

**Tool**: `replaceWorkspaceLine`  
**Affected file**: `AIChatView.java` (~780 lines)  
**Root cause**: Replacing a single line with multi-line content shifts all subsequent line numbers. The next `replaceWorkspaceLine` call targets the wrong absolute line, injecting duplicate/garbled fragments into unrelated methods.

**What happened**:
1. First replacement (line 48): Added import — OK, +1 line shift.
2. Second replacement (line 91): Added field — OK, +1 more shift.
3. Third replacement (line 172): Replaced tool registration — injected duplicate `addTool` calls and stray closing parens because the original block had already shifted.
4. Fourth replacement (line 584): Attempted to update `doSendMessage()` try-block — landed inside leftover garbage from step 3, splitting the lambda and breaking the entire class body.

Result: 20+ syntax errors across unrelated methods (`onClear`, `dispose`, `setFocus`), requiring a full git checkout + atomic rewrite.

**Lesson**:
- **Never chain multiple `replaceWorkspaceLine` calls on the same file.** Each call invalidates all subsequent line-number assumptions.
- For files >200 lines with 3+ changes, use `writeWorkspaceFile` (read entire file first, apply all edits in memory, write once).
- If using `editWorkspaceFile`, verify exact whitespace match — it failed twice due to trailing space differences.

### Problem 2: `editWorkspaceFile` Whitespace Sensitivity

**Tool**: `editWorkspaceFile`  
**Affected files**: `AIChatView.java`, `StandingOrdersBuilder.java`

Two calls failed with "not found" because the `oldString` had subtle whitespace mismatches (trailing spaces, different indentation). The tool requires an exact byte-for-byte match.

**Lesson**:
- Always read the target lines first to confirm exact content before calling `editWorkspaceFile`.
- Prefer `replaceWorkspaceLine` for small single-line edits where line number is known and no other edits will follow.

### Problem 3: Method Name Collision with Interface Default

**Tool**: N/A (code design, not tool)  
**Affected file**: `WorkspaceMemoryTool.java`

Named the LLM tool method `clearMemory()` which conflicted with `SmartTool.clearMemory()` (returns `boolean`). The `@Tool` annotation required a `void` return type. Renamed to `resetMemory()`.

**Lesson**:
- When extending tool hierarchies, check parent interface default methods for name collisions before writing code.

### Recommended Workflow for Multi-Edit Sessions

1. **Single small edit (<3 lines)**: Use `editWorkspaceFile` after verifying exact content via `readWorkspaceFile`.
2. **Multiple edits on same file**: Read the full file, apply all changes in memory, write once with `writeWorkspaceFile`.
3. **Large files (>500 lines) with 2+ changes**: Always use atomic rewrite — never chain line replacements.
4. **After any corruption**: Immediately `git checkout` to restore, then reapply cleanly.
