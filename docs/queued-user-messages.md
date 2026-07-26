# Queued User Messages

## Purpose
When the LLM is processing a task, user input is explicitly acknowledged, queued, and automatically chained as follow-up prompts once the current task completes. Rapid inputs are batched with length/window caps, consumption is FIFO, and cancel/stop preserves queued intent in memory without burning quota.

## Rules & Use Cases (BDD)

### 1. Join Short Burst Messages ✅
- **Rule:** Messages ≤120 chars sent within a sliding 10s window are joined into a single queue entry, capped at 300 combined chars. Long messages (>120) act as hard dividers and cannot be merged into. The timer resets on each new short message to allow continuous rapid-fire sequencing.
```
GIVEN a user has sent a message
WHEN the user sends a new message after 9 seconds AND the message is shorter than 120 chars
THEN we reset the 10s timer so the user may add again a short message in a sequence which are joined together

GIVEN a user sends messages in a short sequence (≤10s apart) AND each message is ≤120 chars
WHEN the messages are queued
THEN they are joined into a single entry until the combined length reaches 300 chars

GIVEN a message exceeds 120 chars OR the gap exceeds 10s from last activity
WHEN it is queued
THEN it starts a new separate entry in the queue, resetting the window

GIVEN a long message (>120 chars) is already in the queue
WHEN a short message arrives within the window
THEN the short message does NOT merge into the long one (long msgs are dividers)
```

### 2. Consume Waiting Queue (FIFO) ✅
- **Rule:** Queued messages are submitted one-by-one in FIFO order after the current task succeeds.
```
GIVEN we have waiting messages in the queue
WHEN the LLM finishes the work on the current message successfully
THEN the next message is submitted to the LLM and removed from the queue (FIFO)

GIVEN multiple messages are queued
WHEN the LLM processes them sequentially
THEN each is consumed individually, not batched into a single prompt
```

### 3. Display in UI ✅
- **Rule:** A TOOL message indicates when the LLM receives a queued message. Dedicated Stop button appears while working; Send/Mic remain active.
```
GIVEN we have messages in the waiting queue
WHEN the LLM gets a message from the queue
THEN the UI displays a TOOL message: "Looking in the User message queue: ... <message>"

GIVEN an LLM task is active
WHEN the user views the input area
THEN a dedicated Stop button is visible/enabled, while Send and Mic buttons remain fully functional
```

### 4. Drain Queue on STOP / Error / RateLimit ✅
- **Rule:** Any abort (cancel, error, rate limit) preserves all queued messages in memory without auto-firing. Never burns quota on failure chains.
```
GIVEN we have messages in the queue
WHEN the user hits STOP OR an API error occurs
THEN the current job stops AND the entire queue is added as a single UserMessage to the active agent's memory

GIVEN a RateLimitException occurs during processing
WHEN handleDoneChatResponse evaluates the result
THEN auto-fire is blocked (cr == null check) and queue drains safely to memory instead

GIVEN the queue was drained on STOP/error
WHEN the drain completes
THEN a TOOL message shows "N queued message(s) preserved for your next request." in the chat history
```

### 5. Compaction Survival & Clear Reset ✅
- **Rule:** Queue survives compaction; Clear empties it.
```
GIVEN queued messages exist
WHEN compactSession runs
THEN the queued messages are excluded from compaction and survive intact

GIVEN queued messages exist
WHEN the user clicks "Clear"
THEN the queue is emptied and no stale follow-up fires later
```

## Data Flow
```
AIChatView.resolveOutgoingMessage() → waitingMessages.add(trailing) [batching logic]
AIChatView.handleDoneChatResponse() → wasCanceled captured early
  → success (ex == null && cr != null && !wasCanceled): pollNext() → submitFollowUpJob(next) [FIFO]
  → abort/error/rate-limit: drainAll() → memory.add() + UI TOOL message
AIChatView.submitFollowUpJob() → lockWhileWorking(true) → UI TOOL msg → Job → standingOrders.build() → active.call()
```

## Components
- **`UserMessageQueue`** (`llmpeon-core`): thread-safe queue handling batching, FIFO polling, bulk draining. Outside `AiAgent.getMemory()` to survive compaction.
- **`AIChatView.waitingMessages`**: UI-side instance managing the queue.
- **`AIChatView.submitFollowUpJob()`**: schedules background job, shows queue TOOL message, re-locks UI synchronously to prevent race conditions.
