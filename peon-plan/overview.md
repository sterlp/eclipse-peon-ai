# Plan: Queue Messages During Active LLM Tasks

## 🛠 Dev Agent Workflow (Read First)
1. **Docs-first:** Create `docs/queued-user-messages.md` exactly as specified below. All rules start marked `❌`. This file *is* the backlog.
2. **Slice 1 — Core:** Implement `UserMessageQueue` in `llmpeon-core` + tests. Run `mvn clean verify -pl llmpeon-core` to sync JARs. Follow `/org.sterl.llmpeon.core/AGENTS.md`.
3. **Slice 2 — Plugin Wiring:** Update `AIChatView.java` & `UserInputWidget.java`. Wire queue, follow-up job, handleDoneChatResponse fix, dedicated Stop button split (replaces Send/Stop swap). Follow `/org.sterl.llmpeon/AGENTS.md`.
4. **Verify & Flip ✅:** Run plugin tests + manual verification. Flip `❌ → ✅` in the doc once each rule's BDD passes. 
5. Reconcile docs.

Compress session after you finished one part of the work and update the state in the plan files - hand over the plan path if you compact the session or batch compact and read plan in one tool call.

## Context & Goal
Currently, if the user sends a message while the LLM is processing, it's silently queued into the agent's memory and processed later. The goal is to make this explicit: show an immediate AI acknowledgment, queue the text, and automatically chain follow-up requests once the current task finishes. Queued messages are consumed FIFO, batched on rapid input (≤10s window, ≤300 combined chars), and preserved visibly on cancel/stop without burning quota.

## Design Decisions
- **Core Abstraction:** `UserMessageQueue` in `llmpeon-core` (`org.sterl.llmpeon.queuedmessages`) handles storage, batching, and FIFO draining. Thread-safe via `synchronized`.
- **Consumption:** FIFO, one-by-one. Success branch checks `cr != null` to guarantee a genuine LLM response before auto-firing the next queue item.
- **Abort/RateLimit Safety:** Any abort (cancel, rate limit, error) is treated identically: drain queue to memory immediately, show preservation count, and stop processing. Never auto-fire on failure.
- **UI Display:** Before each queued submission, a TOOL message `"Looking in the User message queue: ... <message>"` is appended. Dedicated Stop button appears/enables while working; Send and Mic remain active for queuing.
- **Compaction Survival:** Queue lives in `AIChatView`, outside `AiAgent.getMemory()`, surviving `compactSession` intact.
- **Standing Orders:** `submitFollowUpJob` calls `standingOrders.build()` before `active.call()`.

## Affected Files
- `/llmpeon-core/src/main/java/org/sterl/llmpeon/queuedmessages/UserMessageQueue.java` (new)
- `/llmpeon-core/src/test/java/org/sterl/llmpeon/queuedmessages/UserMessageQueueTest.java` (new)
- `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/AIChatView.java`
- `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/widget/UserInputWidget.java` (for Stop button split)

## Step-by-Step Changes
1. **Create `UserMessageQueue` (`llmpeon-core`)**
   ```java
   package org.sterl.llmpeon.queuedmessages;
   
   import java.util.ArrayDeque;
   import java.util.Deque;
   
   public class UserMessageQueue {
       private final Deque<String> queue = new ArrayDeque<>();
       private volatile long batchStartTime = 0;
       
       public synchronized void add(String message) {
           if (message == null || message.isBlank()) return;
           long now = System.currentTimeMillis();
           
           boolean startNewBatch = queue.isEmpty() || (now - batchStartTime > 10_000);
           String combined = message;
           
           // Only merge short messages into other short messages
           if (!startNewBatch && message.length() <= 120) {
               String last = queue.removeLast();
               if (last.length() <= 120) {
                   int newLen = last.length() + 1 + message.length();
                   if (newLen <= 300) {
                       combined = last + " " + message;
                   } else {
                       queue.addLast(last); // cap exceeded, restore & add separate
                   }
               } else {
                   queue.addLast(last); // long msg acts as divider, don't merge into it
               }
           }
           
           queue.addLast(combined);
           batchStartTime = now; // sliding window: reset timer on every merge to allow continuous rapid-fire sequencing
       }
       
       public synchronized String pollNext() { return queue.pollFirst(); }
       
       public synchronized String drainAll() {
           if (queue.isEmpty()) return null;
           String combined = String.join(System.lineSeparator(), queue);
           queue.clear();
           batchStartTime = 0;
           return combined;
       }
       
       public synchronized int size() { return queue.size(); }
       public synchronized void clear() { queue.clear(); batchStartTime = 0; }
   }
   ```

2. **Create `UserMessageQueueTest` (`llmpeon-core`)**
   - Batching: ≤10s & ≤120 chars → joined. Cap at 300 combined chars forces new entry.
   - Sliding window reset: message at 9s resets timer, allowing another merge within the next 10s. Gap >10s → new entry.
   - Long message divider: >120 char msg starts fresh, subsequent short msg does NOT merge into it.
   - FIFO: `pollNext()` returns in order.
   - `drainAll()`: joins all with newline, clears queue & timestamp.

3. **Update `AIChatView` Fields**
   ```java
   private final UserMessageQueue waitingMessages = new UserMessageQueue();
   private volatile boolean acknowledgedPendingBatch = false;
   ```

4. **Update `resolveOutgoingMessage` (UI Thread)**
   - In the `actionsBar.isWorking()` branch:
     - `waitingMessages.add(trailing)`.
     - If `!acknowledgedPendingBatch`: append AI acknowledgment `chatHistory.appendMessage(new SimpleMessage(Type.AI, "Noted, I will respond to this as soon as I finished this task …"))` and set `acknowledgedPendingBatch = true`.
     - Return `SendDecision.Skip()`.
   - Remove the old `active.getMemory().add(UserMessage.from(trailing))`.

5. **Add `submitFollowUpJob(String message)`**
   - Calls `lockWhileWorking(true)` synchronously on UI thread (no gap).
   - Appends TOOL message: `chatHistory.appendMessage(new SimpleMessage(Type.TOOL, "Looking in the User message queue: " + message))`.
   - Schedules a `Job` that **exactly mirrors `submitAiJob`'s try/catch/finally structure** to guarantee abort-and-drain safety on follow-up failures:
     ```java
     Job.create("Peon AI follow-up", monitor -> {
         monitor.beginTask("Follow-up request", 100);
         monitorRef.set(monitor);
         Exception ex = null;
         ChatResponse cr = null;
         try {
             var active = aiService.getActiveAgent();
             active.setUserContextInformations(this.standingOrders.build());
             cr = active.call(message, this);
         } catch (Exception e) {
             ex = handleChatException(e); // ensures rate-limits/cancels drain queue safely
         } finally {
             handleDoneChatResponse(cr, monitor, ex);
         }
         return PeonConstants.status("Follow-up", ex);
     }).schedule();
     ```
   - Skips `resolveOutgoingMessage()` and UI echoing. Reuses `monitorRef` / `handleDoneChatResponse`.

6. **Update `handleDoneChatResponse`**
   ```java
   private void handleDoneChatResponse(ChatResponse cr, IProgressMonitor monitor, Exception ex) {
       boolean wasCanceled = monitor.isCanceled(); // Capture BEFORE resetting monitorRef
       if (aiService.getConfig().isDebugMode()) {
           LOG.info("Chatreponse: " + (cr == null ? "null" : cr.aiMessage()));
       }
       monitor.done();
       monitorRef.set(new NullProgressMonitor());
       EclipseUtil.runInUiThread(parent, () -> {
           lockWhileWorking(false);
           // Only auto-fire on genuine success (cr != null), never on abort/rate-limit/error
           if (ex == null && cr != null && !wasCanceled) {
               String next = waitingMessages.pollNext();
               if (next != null) {
                   acknowledgedPendingBatch = false;
                   submitFollowUpJob(next);
               }
           } else {
               // Covers: real errors, rate limits, and cancellation
               int preservedCount = waitingMessages.size();
               String combined = waitingMessages.drainAll();
               if (combined != null) {
                   aiService.getActiveAgent().getMemory().add(UserMessage.from(combined));
                   chatHistory.appendMessage(new SimpleMessage(Type.TOOL, 
                       preservedCount + " queued message(s) preserved for your next request."));
               }
               acknowledgedPendingBatch = false;
           }
       });
   }
   ```
   - Update signature to accept `Exception ex`. Update callers (`submitAiJob`, `doCompressContext`).

7. **Update `onClear()`**
   - Add `waitingMessages.clear()` and `acknowledgedPendingBatch = false`.

8. **UI Button Split & Locking Adjustment**
   - Refactor `UserInputWidget` (or input block layout) to split the current Send/Stop swap into two distinct controls:
     - **Send button:** Always visible/enabled, retains default submit function.
     - **Mic button:** Always visible/enabled, retains default recording function.
     - **Stop button:** Initially hidden/disabled. Becomes visible/enabled when `actionsBar.isWorking()` is true. Calls `getIProgressMonitor().setCanceled(true)` on click.
   - Update `lockWhileWorking(boolean value)`:
     - Remove `chatInput.setWorking(value)` input locking (or modify it to only toggle the Stop button visibility without disabling Send/Mic).
     - Keep `actionsBar.lockWhileWorking(value)`.
     - Preserve `questionWidget.cancel()` logic on `!value`.
   - No header spinner needed; the dedicated Stop button serves as the working indicator.

## Documentation: `docs/queued-user-messages.md`
*(Create this file first. All rules start ❌ until verified by tests/manual checks.)*

```markdown
# Queued User Messages

## Purpose
When the LLM is processing a task, user input is explicitly acknowledged, queued, and automatically chained as follow-up prompts once the current task completes. Rapid inputs are batched with length/window caps, consumption is FIFO, and cancel/stop preserves queued intent in memory without burning quota.

## Rules & Use Cases (BDD)

### 1. Join Short Burst Messages ❌
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

### 2. Consume Waiting Queue (FIFO) ❌
- **Rule:** Queued messages are submitted one-by-one in FIFO order after the current task succeeds.
```
GIVEN we have waiting messages in the queue
WHEN the LLM finishes the work on the current message successfully
THEN the next message is submitted to the LLM and removed from the queue (FIFO)

GIVEN multiple messages are queued
WHEN the LLM processes them sequentially
THEN each is consumed individually, not batched into a single prompt
```

### 3. Display in UI ❌
- **Rule:** A TOOL message indicates when the LLM receives a queued message. Dedicated Stop button appears while working; Send/Mic remain active.
```
GIVEN we have messages in the waiting queue
WHEN the LLM gets a message from the queue
THEN the UI displays a TOOL message: "Looking in the User message queue: ... <message>"

GIVEN an LLM task is active
WHEN the user views the input area
THEN a dedicated Stop button is visible/enabled, while Send and Mic buttons remain fully functional
```

### 4. Drain Queue on STOP / Error / RateLimit ❌
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

### 5. Compaction Survival & Clear Reset ❌
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
```

## Verification
- Send `/command` or plain text while LLM is streaming → UI shows "Noted..." immediately (once), input clears, dedicated Stop button becomes visible/enabled.
- Rapid-fire messages ≤120 chars within 10s → joined into single queue entry until 300 char cap. Long msg (>120) acts as divider.
- When streaming finishes, LLM automatically receives the next queued message (FIFO).
- UI shows TOOL message "Looking in the User message queue: ..." before each queued submission.
- Chat history does NOT duplicate queued text when follow-up fires.
- **STOP/Cancel:** Hit STOP while queue has messages → job cancels, entire queue drains as one UserMessage to agent memory, TOOL message confirms count, no auto-fire.
- **RateLimit/Error:** Trigger rate limit or error with queued msgs → `cr == null` blocks auto-fire, queue drains safely to memory instead of burning quota.
- **Compaction:** Queue has messages → task compacts context → queue survives intact → next send consumes FIFO correctly.
- Hit "Clear" while messages are queued → queue is cleared, no stale follow-up fires later.
- Run `llmpeon-core` tests for `UserMessageQueue`.
- **UI Flicker Check:** During rapid chained follow-ups, verify the Stop button doesn't flash (unlock → lock) between items. The single-runnable design in Step 6 should prevent this; if a frame flickers occurs, consider suppressing `lockWhileWorking(false)` in `onCallCompleted` when queue chaining is active.
- Run existing plugin tests to ensure no regression in normal send/cancel flows.