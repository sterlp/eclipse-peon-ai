# Streaming Response Display

**Goal:** Eliminate flicker, repeated markdown parsing, and unbounded content growth during long THINK/AI responses by deferring heavy rendering to the final message insert and using a lightweight status-bar overlay for live progress.

## Background

The previous incremental rendering (`md.render(fullAccumulatedText)` on every chunk) caused:
- DOM flicker on every chunk
- O(n²) performance degradation (full MD parse per chunk)
- Unbounded content growth ("black screen" for large THINK blocks)

## Business Rules

### R1 — Status bar shows elapsed time and throughput during streaming ✅

The status bar displays elapsed time, current phase, and tokens/second.

- **GIVEN** a streaming response is in progress **WHEN** a chunk arrives **THEN** `#live-status` shows `<elapsed>s | <phase>... | <tok/s>` where phase is `thinking`, `responding`, or `using tools`
- **GIVEN** a streaming response has just started **WHEN** the START chunk arrives **THEN** `#live-status` shows `waiting for AI...`
- **Tag:** unit (verify `ChatMarkdownWidget.updateRunningChunk` computes state string and tokPerSec)

### R2 — Token count always visible in overlay preview ✅

The second line of the overlay always shows the total tokens generated, even when live preview is disabled.

- **GIVEN** a streaming response is in progress **WHEN** chunks arrive **THEN** the overlay preview shows `<N> tokens` at minimum
- **GIVEN** `showRealtimeAiResponse` is disabled (default: enabled) **WHEN** THINK/ANSWER chunks arrive **THEN** the overlay shows `<N> tokens` (not the streamed text)
- **GIVEN** a TOOL call is in progress **WHEN** chunks arrive **THEN** the overlay shows `<N> tokens`
- **Tag:** unit (verify `updateRunningChunk` passes token count when `showRealtimeAiResponse` is false or type is TOOL)

### R3 — Live text preview replaces token count when enabled ✅

When `showRealtimeAiResponse` is enabled (default: `true`), the overlay shows the accumulated THINK or ANSWER text instead of the token count.

- **GIVEN** `showRealtimeAiResponse` is enabled (default) **WHEN** THINK chunks arrive **THEN** the overlay shows the accumulated THINK text
- **GIVEN** `showRealtimeAiResponse` is enabled (default) **WHEN** ANSWER chunks arrive **THEN** the overlay shows the accumulated ANSWER text
- **GIVEN** `showRealtimeAiResponse` is enabled (default) **WHEN** a TOOL call is in progress **THEN** the overlay shows `<N> tokens` (no text to preview)
- **Tag:** unit (verify `updateRunningChunk` passes accumulated text when `showRealtimeAiResponse` is true for THINK/ANSWER)

### R4 — Overlay preview is bounded and auto-scrolls ✅

The preview div doesn't grow unbounded; it scrolls internally to keep the latest text visible.

- **GIVEN** a large response streams > 300px of text **WHEN** chunks arrive **THEN** `.live-chunk` stays within `max-height: 300px` and auto-scrolls to show the latest text
- **Tag:** CSS/behavior verification (manual + CSS inspection: `.live-chunk { max-height: 300px; overflow-y: auto; }`)

### R5 — Line endings rendered as HTML breaks in preview ✅

Streamed text line endings are converted to `<br>` for correct display in the plain-text overlay.

- **GIVEN** a streamed chunk contains `\n` or `\r\n` **WHEN** the overlay renders the text **THEN** line breaks are displayed as `<br>` elements
- **Tag:** unit (verify JS `updateLiveResponse` calls `.replace(/\r\n/g, '<br>').replace(/\n/g, '<br>')`)

### R6 — THINK and AI messages appended once on completion ✅

THINK and AI messages are inserted into the chat exactly once when `onChatResponse` fires, with full markdown highlighting.

- **GIVEN** a response with thinking content **WHEN** `onChatResponse` is called with a THINK message **THEN** the message is appended to the chat with MD rendering
- **GIVEN** `showRealtimeAiResponse` is enabled or disabled **WHEN** `onChatResponse` is called with an AI message **THEN** the message is appended to the chat with MD rendering
- **Tag:** unit (verify `AIChatView.onChatResponse` always calls `appendMessage` for THINK/AI, no suppression logic)

### R7 — No duplicate messages ✅

The chat history contains exactly one THINK and one AI message per tool-loop response.

- **GIVEN** a response with thinking and answer **WHEN** streaming completes and `onChatResponse` fires **THEN** the chat has one THINK + one AI message (not two of each)
- **Tag:** integration (verify no incremental JS functions insert messages during streaming)

### R8 — Status bar hides on END ✅

The live status bar (including preview) is hidden when streaming ends.

- **GIVEN** the overlay is visible during streaming **WHEN** a streaming chunk with type END arrives **THEN** `hideLiveStatus()` is called and the overlay disappears
- **Tag:** unit (verify `ChatMarkdownWidget.onStreamingChunk` calls `hideLiveStatus` on END)

### R9 — Status bar hides on final message append ✅

The live status bar is hidden when a final message is appended to the chat.

- **GIVEN** the overlay is visible during streaming **WHEN** `appendMessage` is called for a non-TOOL message **THEN** `hideLiveStatus()` is called before the message is appended
- **Tag:** unit (verify JS `appendMessage` calls `hideLiveStatus()` at the start)

### R10 — Page scrolls to bottom when live preview first appears ✅

When the live preview overlay becomes visible after a message was appended, the page scrolls to the bottom so the latest message is fully visible.

- **GIVEN** a message was just appended and the page scrolled to bottom **WHEN** the live preview overlay becomes visible **THEN** the page scrolls to bottom again to show the overlay
- **GIVEN** the live preview is already visible **WHEN** new chunks arrive **THEN** the page does not scroll (only the chunk div auto-scrolls)
- **Tag:** unit (verify JS `updateLiveResponse` tracks `wasHidden` and scrolls only when transitioning from hidden to visible)

### R11 — No incremental message updates during streaming ✅

No messages are inserted into the chat during streaming; all rendering happens once on completion.

- **GIVEN** a streaming response is in progress **WHEN** THINK or ANSWER chunks arrive **THEN** no new messages are added to the chat container (only the overlay is updated)
- **Tag:** unit (verify `updateLastThinkingMessage` and `updateLastAnsweringMessage` no longer exist in chat.html)

### R12 — Accumulators reset on new streaming session ✅

The THINK and ANSWER text accumulators are cleared when a new streaming session starts.

- **GIVEN** a previous streaming session accumulated text **WHEN** a new START chunk arrives **THEN** both `thinkText` and `answerText` accumulators are cleared
- **Tag:** unit (verify `ChatMarkdownWidget.updateRunningChunk` calls `setLength(0)` on START)


### R13 — StreamingBridge captures partial results and throws specific exceptions 🚧

**Rule:** `StreamingBridge` accumulates THINK and ANSWER chunks during streaming. On abort or error, it throws a dedicated exception **only when partial content is available**.

Two dedicated exception types carry the partial payload:
- **`PartialStreamCanceled`** — thrown on user abort/Stop **if chunks arrived**. Contains the accumulated partial text (thinking + answer) up to the cancel point.
- **`PartialStreamError`** — thrown on provider error/rate-limit mid-stream **if chunks arrived**. Contains the accumulated partial text plus the root error.

Both expose `getPartialAiMessage()` (thinking + text) so the caller can append it to history or surface it to the UI. If no chunks arrived, the standard `CancellationException` / original error is thrown (no partial wrapper needed).

- **GIVEN** a streaming response is in progress and THINK/ANSWER chunks have arrived **WHEN** the call is canceled **THEN** `StreamingBridge` throws `PartialStreamCanceled` carrying the accumulated partial text
- **GIVEN** a streaming response is in progress and THINK/ANSWER chunks have arrived **WHEN** a provider error occurs mid-stream **THEN** `StreamingBridge` throws `PartialStreamError` carrying the accumulated partial text and the root error
- **GIVEN** no chunks arrived yet **WHEN** the call is canceled or errors **THEN** the standard `CancellationException` / original error is thrown (no partial exception)
- **GIVEN** a partial exception is caught **WHEN** the agent updates memory **THEN** `getPartialAiMessage()` is appended to history so the LLM can resume from the partial result
- **Tag:** core (verify `StreamingBridge` accumulators; verify dedicated exceptions thrown only when partial content exists)

**Context:** Currently only the UI monitor sees partial chunks. On cancel/error, the already-streamed text is lost. Dedicated exceptions make the partial result first-class: clean abort paths, resume after rate-limit, and future "save partial on error" without monitor-hacks.

### R14 — Diff wird genau einmal mit korrektem Theme gerendert ✅

Ein Diff wird exakt einmal in den Chat eingefügt und verwendet das aktuelle Farbschema.

- **GIVEN** a diff is provided **WHEN** `showDiff` is called **THEN** the diff is appended exactly once using the current theme
- **Tag:** unit (verify `ChatMarkdownWidget.showDiff` calls `postMessage` exactly once with theme parameter)

### R15 — UI Message Bridge aligned with Test Harness (postMessage) ✅

Java und Test Harness verwenden identische `postMessage`-Verdrahtung. Content und Steuerimpulse sind strikt getrennt:
- **Chat-Content:** wird als `SimpleMessage` (`role` + `message`) direkt durchgereicht. `chat.html` rendert diese standardmäßig.
- **Steuerimpulse:** werden als dedizierte, typsichere Java-Klassen (`UiCommand`-Hierarchie) serialisiert. Jede Klasse repräsentiert exakt ein Kommando (z. B. `SetThemeCommand`, `HideLiveStatusCommand`).

- **GIVEN** a chat message needs to be sent **WHEN** Java calls the widget **THEN** it posts a `SimpleMessage` JSON payload
- **GIVEN** a UI control signal is needed **WHEN** Java calls the widget **THEN** it posts a typed `UiCommand` JSON payload (e.g., `SetThemeCommand`)
- **GIVEN** a `SetThemeCommand` is used **WHEN** the theme needs to change **THEN** one of two static instances (`LIGHT`, `DARK`) is sent (private constructor, enum-like)
- **Tag:** integration (verify test-chat.html covers all `UiCommand` types Java sends)

Java und Test Harness verwenden identische `postMessage`-Verdrahtung für alle UI-Kommunikation.

- **GIVEN** a message or diff needs to be sent to the UI **WHEN** Java calls the widget **THEN** it uses `browser.postMessage()` (via MessageEvent dispatch) with a JSON payload, identical to the test harness approach
- **GIVEN** a typed command (setTheme, hideLiveStatus, clearMessages) is sent **WHEN** the message arrives **THEN** `chat.html` dispatches it via the `message` event listener using the `type` field
- **GIVEN** a SimpleMessage (with `role` field) is sent **WHEN** the message arrives **THEN** it is routed to `appendMessage` directly
- **Tag:** integration (verify test-chat.html covers all message types Java sends)


### R16 — Message Queue prevents loss during HTML load & agent switch ✅

Alle UI-Nachrichten werden in einer Queue gepuffert, bis die HTML-Seite vollständig geladen und bereit ist (`browserReady = true`). Dies verhindert den Verlust von Aufrufen und Nachrichten, insbesondere beim Agentenwechsel oder nach `clear()`.

- **GIVEN** the HTML page is not yet loaded (`browserReady == false`) **WHEN** a message is sent to the widget **THEN** the JSON payload is added to `pendingMessages` queue
- **GIVEN** messages are queued and the HTML page finishes loading **WHEN** the `javaReady` title event fires **THEN** all queued messages are dispatched to the browser in order
- **GIVEN** the chat is cleared during an agent switch **WHEN** `clear()` is called **THEN** the `pendingMessages` queue is cleared to prevent stale messages from the previous agent
- **Tag:** unit (verify `ChatMarkdownWidget.postMessage` queues when not ready; verify `TitleListener` flushes queue)

**Context:** Ohne Queue gehen Nachrichten beim Agentenwechsel oder nach einem `clear()` verloren, da die HTML-Seite kurzzeitig nicht empfangsbereit ist. Die Queue garantiert, dass jede Nachricht exakt einmal und in der richtigen Reihenfolge ankommt.