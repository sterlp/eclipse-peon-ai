# Peon Memory — User Guide

## What is it?

Peon Memory lets you teach the AI workspace-specific rules and preferences that persist across sessions. Once stored, every conversation automatically includes your guidelines so the AI respects them without repetition.

Available since **v1.7.5**.

## How to use it

### Trigger phrases

Use any of these keywords in a chat message to save something:

| Phrase | Example |
|--------|---------|
| `remember this` | "Remember this: always format Java files with 4-space indentation" |
| `always do` | "Always do a clean build before running tests" |
| `never do` | "Never do inline comments in production code" |

The AI extracts the guideline and stores it automatically — no manual configuration needed.

### Managing memory

You can also ask the AI directly to manage stored guidelines:

- **View**: "Show me my workspace memory" or "What do you remember?"
- **Remove**: "Remove guideline #3" (refers to the numbered list shown by the AI)
- **Edit**: "Change guideline #2 to ..." 
- **Reset**: "Clear all memory" (requires explicit confirmation)

### How it works

Guidelines are injected as a `Memory:` block at the start of every conversation. The AI sees them before processing your request and follows them whenever they apply.

Storage is workspace-scoped — each Eclipse workspace maintains its own independent set of guidelines. A maximum of 500 entries is supported; when full, the oldest guideline is automatically removed.
