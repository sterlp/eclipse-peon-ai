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


## Chat history persistence

Peon also persists the current chat history for Peon-Dev, Peon-Plan, Jon (Peon-PO), and custom agents, so their conversation context survives Eclipse/plugin restarts.

History lives in the workspace metadata state — one file per agent at `<workspace>/.metadata/.plugins/org.sterl.llmpeon/state/<agent>-history.jsonl`. Because it is workspace-scoped, each workspace keeps its own history and multiple instances of the same project never collide. The shared config directory `~/.peon` now holds only configuration (Agents, Skills, Commands). On first start a one-time, automatic migration moves any existing `~/.peon/state` history into the workspace and removes the now-empty directory. If a file already exists at the target it is skipped — the existing file wins, the legacy copy is kept — and the skip is logged; the migration never overwrites data.

The **Clear** button deletes only the active agent's chat history and queued messages. It does not delete Peon Memory guidelines; use the memory reset command for that.

Guidelines are injected as a `Memory:` block at the start of every conversation. The AI sees them before processing your request and follows them whenever they apply.

Storage is workspace-scoped — each Eclipse workspace maintains its own independent set of guidelines. A maximum of 500 entries is supported; when full, the oldest guideline is automatically removed.
