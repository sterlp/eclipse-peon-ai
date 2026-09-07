# Ask User Tool — Design

## Business Requirements

- The LLM can pause mid-task and ask the user a clarifying question with optional predefined answer choices.
- The user can always override predefined choices with free text.
- One question per tool call; displayed inline in the chat view (no dialog).
- Cancelling (Stop button or job interruption) returns `"[canceled]"` to the LLM so it can react gracefully.
- **Queue-Safety:** The `AskUserTool` blocks the agent thread via `CountDownLatch`, guaranteeing the next user input is treated as the direct answer — preventing queued messages from interrupting the LLM's line of thought or misaligning with the question.

## Interaction Design

While a question is pending the normal input area is replaced by the question widget:

```
┌─────────────────────────────────────────────┐
│ [Question text label]                       │
│                                             │
│ ○ Predefined answer A                       │
│ ○ Predefined answer B                       │
│ ○ Enter own answer                          │  ← always present
│                                             │
│ [Text input, auto-grow]        [Answer]     │
└─────────────────────────────────────────────┘
```

Selecting a radio pre-fills the text field; the field stays editable so the user can refine or append.
The **Answer** button (and `Ctrl/Cmd+Enter`) always submits whatever is in the text field.
On submit the normal input reappears and the LLM receives the answer string.

## UI Rules

### R-UI1: Frage vollständig sichtbar ✅ (User 2026-09-06, E2E-Regression — gebaut inc-3 `402b922`)
Wenn das Question-Widget erscheint, ist die Frage-Nachricht im Chat **vollständig sichtbar** —
nichts davon wird vom (potenziell höheren) Widget verdeckt.

- **GIVEN** das Question-Widget wird angezeigt **WHEN** die frage tragende Chat-Nachricht
  gerendert ist **THEN** das Widget (mit Optionen) wird **nach** der Nachricht aufgebaut und
  der Chat scrollt ans Ende — die letzte Zeile der Frage ist lesbar
- **GIVEN** das Widget inkl. Optionen ist höher als der freie Bereich unter der Frage
  **WHEN** gerendert **THEN** endet der View auf der Frage, nicht auf dem Widget — kein
  verdeckter Teil der Frage
- **Umsetzung (gebaut):** finaler Scroll-to-bottom **nach** Widget-Completion über das
  Chat-Widget (`ScrollToBottomCommand` → chat.html `scrollToBottom()`, aufgerufen in
  `AIChatView.showQuestion` nach den Layout-Calls). Kein automatisierter Test möglich
  (SWT-Browser, kein Harness) — manuelle Verifikation: Frage mit Widget > Input-Höhe
  erzeugen, letzte Zeile der Frage muss lesbar sein.

**Regression-Historie:** Beim Question-Widget-Erscheinen wurde die Nachricht erneut in
chat.html eingefügt und die Optionen danach gebaut — ragten die Optionen über die
Input-Höhe hinaus, verdeckten sie einen Teil der Frage.

## Key Technical Decisions

| Concern | Decision |
|---------|----------|
| **Package — tool** | `org.sterl.llmpeon.parts.tools` — `AskUserTool` |
| **Package — widgets** | `org.sterl.llmpeon.parts.widget` — `UserQuestionResponseWidget`, `TextInputWidget` |
| **Package — orchestration** | `org.sterl.llmpeon.parts.question` — `QuestionOrchestrator` (zentrale Widget-Swap-Steuerung) |
| **Thread sync** | `CountDownLatch(1)` blocks the LangChain4j background thread; the UI `onAnswer` callback releases it |
| **Widget swap** | `QuestionOrchestrator` kapselt `showQuestion()`/`hideQuestion()` — ersetzt ~30 Zeilen verteilte exclude/visible/layout-Logik in `AIChatView` |
| **Cancel path** | `lockWhileWorking(false)` → `QuestionOrchestrator.cancelSilently()` → fires `"[canceled]"` → releases latch |
| **Shell-Approval** | `ShellApprovalService` (`org.sterl.llmpeon.parts.shell`) — stateless, leses Preferences, konfiguriert `ShellTool` ConfirmationProvider; keine SWT-Dependency. Ersetzt ~35 Zeilen aus `AIChatView`. |
| **Icon buttons** | `UserQuestionResponseWidget` nutzt flache Icon-Buttons (Answer/Cancel) — konsistent zum Haupt-Input-Widget |
| **Tool registration** | `AIChatView.createPartControl` via `aiService.getToolService().addTool(new AskUserTool(...))` |
