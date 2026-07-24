# ADR-0007: Use System.lineSeparator() in Strings Sent to LLM

**Status:** Accepted

## Context

Tools like `diskReplaceLines` require the LLM to produce output with the correct line endings. If the LLM sees `\n` in the input but the system uses `\r\n` (or vice versa), it may produce mismatched line endings, causing the tool to fail or produce corrupted content.

The LLM sees the actual bytes of the string we send. If we hardcode `\n` in strings that contain file paths, directory listings, or multi-line content going to the LLM, it will only ever see `\n` — regardless of the host OS. When it then generates text to be used by tools like `diskReplaceLines`, it will use `\n`, which may not match the actual file's line endings on Windows or macOS.

## Decision

Use `System.lineSeparator()` (or `\n` only where the LLM must interpret it as a logical newline in a context-free way, e.g. inside markdown formatting) instead of hardcoding `"\n"` in all strings that are sent to the LLM as part of tool output or system prompts where the LLM needs to reproduce the line ending.

Specifically:
- **Tool output** (directory listings, file content previews, error messages) — use `System.lineSeparator()`
- **Multi-line system prompts** — already use raw Java strings which preserve the file's line endings; no change needed
- **Markdown content** inside tool descriptions — keep `\n` where it's purely formatting (the LLM knows markdown uses `\n`)
- **File paths with separators** — use `File.separator` or `Path`'s `toString()` which already uses the OS separator

## Consequences

- **Positive:** LLM sees consistent line endings matching the host system, reducing line-ending mismatch bugs in `diskReplaceLines`, `diskEditFile`, etc.
- **Positive:** Tool output strings are naturally consistent with what `Files.readString()` returns on the same OS.
- **Risk:** Tests running on CI (Linux) will see `\n`; tests running on Windows will see `\r\n`. Test assertions must use `System.lineSeparator()` or normalize before comparing.
- **No-op for most existing code:** `String.join("\n", ...)` in tool output can be replaced with `String.join(System.lineSeparator(), ...)` — trivial change.
