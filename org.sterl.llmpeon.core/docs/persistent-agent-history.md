# Persistent Agent History

## Goal

Persist the current chat memory for Peon-Dev, Peon-Plan, and custom agents across Eclipse/plugin restarts.

## Rules

- ✅ Peon-Dev, Peon-Plan, and all custom agents persist their current `ThreadSafeMemory`.
- ✅ Scaffold, Search sub-agent, and Compressor history are not persisted.
- ✅ History is stored as one JSONL file per agent under `<configDir>/state/<safe-agent-name>-history.jsonl`.
- ✅ Agent names keep display case and replace every character outside `[A-Za-z0-9._-]` with `_`; blank sanitized names become `_agent`.
- ✅ Each JSONL line is one LangChain4j `ChatMessage` serialized with `ChatMessageSerializer.messageToJson`.
- ✅ Message order on disk matches `ThreadSafeMemory.getCopy()`.
- ✅ Normal message additions append; user-message merge, `replaceAll`, compaction, and clear persist/delete to mirror current memory.
- ✅ `Clear` deletes only the active agent history and queue state; workspace guideline memory remains separate.
- ✅ Corrupt history is logged, deleted, ignored, and startup continues with empty history.
- ✅ If a save fails, the error is surfaced once and `ThreadSafeMemory` disables history persistence for that session.
- ✅ Token totals are not persisted.
- ✅ On history restore, `totalTokenUsed` is estimated as ~1/3 of `ChatMessageUtil.getTokenCount()` on the loaded messages (not 0), so the Compact UI shows a realistic value immediately.

## BDD

### Scenario 1 — Dev agent history survives restart

GIVEN a `FileAgentHistoryStore` with `configDir=/tmp/.peon`  
AND Dev history contains `UserMessage("hello")` and `AiMessage("world")`  
WHEN the store persists messages for `Peon-Dev`  
AND a new agent-scoped store loads messages for `Peon-Dev`  
THEN it returns both messages in original order  
AND the file exists as `state/Peon-Dev-history.jsonl`.

Status: ✅ `FileAgentHistoryStoreTest.loadReturnsMessagesWrittenAsSingleAgentFile`

### Scenario 2 — JSONL persist after shrink/compaction

GIVEN a saved agent history with four messages  
WHEN the store persists a compacted two-message memory for the same agent  
THEN `state/<agent>-history.jsonl` contains exactly those two message lines  
AND no stale message lines remain.

Status: ✅ `FileAgentHistoryStoreTest.persistReplacesJsonlToMatchCurrentMemory`

### Scenario 3 — Clear deletes active agent persisted history only

GIVEN Dev and Plan both have persisted JSONL files  
AND Dev is the active agent instance under test  
WHEN `devAgent.clear()` is called  
THEN Dev in-memory messages and queue are empty  
AND Dev JSONL file is deleted or absent  
AND Plan JSONL file remains unchanged.

Status: ✅ `AbstractAgentTest.clearDeletesOnlyThisAgentsPersistedHistory`

### Scenario 4 — Custom agent history persists by default

GIVEN a config directory with `agents/docs/AGENT.md`  
AND an `AgentService` constructed with a history config directory  
WHEN the custom `docs` agent adds a user message and assistant response  
AND a new `AgentService` is constructed with the same config directory  
THEN the `docs` custom agent starts with saved memory loaded  
AND no `history: true` frontmatter is required.

Status: ✅ `AgentServiceTest.customAgentHistoryPersistsWithoutHistoryFlag`

### Scenario 5 — Built-in Plan and Dev are persistent, Scaffold is not

GIVEN `AgentService` is created with built-in agents and a history config directory  
WHEN Dev and Plan receive messages  
THEN both write `state/Peon-Dev-history.jsonl` and `state/Peon-Plan-history.jsonl`  
AND no Scaffold history is written by this service wiring.

Status: ✅ `AgentServiceTest.enablesHistoryForPlanDevAndCustomOnly`

### Scenario 6 — ThreadSafeMemory triggers store operations on mutations

GIVEN a `ThreadSafeMemory` with an agent-scoped `FileAgentHistoryStore`  
WHEN a simple message is added  
THEN `store.append(message)` is called  
WHEN consecutive `UserMessage`s are merged  
THEN `store.persist(snapshot)` is called  
WHEN `addResult(ChatResponse, toolResults)` is called  
THEN `store.append(messages)` is called for the assistant message and tool results in order  
WHEN `clear()` is called  
THEN `store.clear()` is called.

Status: ✅ `ThreadSafeMemoryTest.storeReceivesAppendPersistAndClearOperations`

### Scenario 7 — Restore must not merge adjacent user messages

GIVEN a persisted message list containing two adjacent `UserMessage`s  
WHEN `replaceAll(messages)` restores it  
THEN `getCopy()` returns the same two user messages as separate entries  
AND `messageFlow()` reflects the restored list, not `add()` merge behavior.

Status: ✅ `ThreadSafeMemoryTest.replaceAllRestoresExactMessageListWithoutMerge`

### Scenario 7b — UserMessage merge rewrites JSONL to avoid stale appended line

GIVEN a persistent `ThreadSafeMemory` backed by `FileAgentHistoryStore`  
AND the JSONL file contains an `AiMessage` followed by `UserMessage("U1")`  
WHEN `memory.add(UserMessage("U2"))` merges the last user message into `"U1\nU2"`  
THEN the full JSONL file is persisted, not appended  
AND loading the file returns exactly `AiMessage`, merged `UserMessage("U1\nU2")`  
AND no stale separate `UserMessage("U1")` or `UserMessage("U2")` line remains.

Status: ✅ `FileAgentHistoryStoreTest.userMessageMergePersistsJsonlWithoutStalePreMergeLine`

### Scenario 8 — Persistence survives tool messages

GIVEN history includes an `AiMessage` with a `ToolExecutionRequest`  
AND the matching `ToolExecutionResultMessage`  
WHEN the store saves and reloads the agent history  
THEN the tool request/result messages are restored in order  
AND `ThreadSafeMemory.messageFlow()` remains valid.

Status: ✅ `FileAgentHistoryStoreTest.roundTripsToolExecutionMessages`

### Scenario 9 — Corrupt history is deleted and agent starts empty

GIVEN `<configDir>/state/Peon-Dev-history.jsonl` contains invalid JSON  
WHEN `FileAgentHistoryStore.load("Peon-Dev")` runs  
THEN it returns empty history  
AND deletes `Peon-Dev-history.jsonl`  
AND agent startup is not blocked.

Status: ✅ `FileAgentHistoryStoreTest.corruptHistoryIsDeletedAndReturnsEmptyHistory`

### Scenario 10 — Save failure disables session persistence and throws once

GIVEN a persistent `ThreadSafeMemory` backed by `FileAgentHistoryStore`  
AND an existing valid `<agent>-history.jsonl`  
WHEN append fails after a new message is added  
THEN the failure is surfaced  
AND persistence is disabled for that memory instance  
AND following memory mutations continue in memory without more store writes.

Status: ✅ `FileAgentHistoryStoreTest.firstAppendFailureDisablesFurtherPersistenceAndThrows`

### Scenario 12 — Plan temperature uses plan preference

GIVEN `LlmConfig.builder().planTemperature(1.0).devTemperature(0.6).build()`  
WHEN `planAgentConfig()` is built  
THEN its temperature is `1.0`  
AND Dev config temperature remains `0.6`.

Status: ✅ `LlmConfigTest.planAgentConfigUsesPlanTemperatureNotDevTemperature`

### Scenario 13 — AiPlanAgent reports plan temperature

GIVEN an `AiPlanAgent` with `planTemperature=1.0` and `devTemperature=0.6`  
WHEN `getTemperature()` is called  
THEN it returns `1.0`.

Status: ✅ `AiPlanAgentTest.getTemperatureReturnsPlanTemperature`

### Scenario 14 — Token estimation on history restore

GIVEN a persisted JSONL file with 5000 tokens of chat messages  
WHEN `ThreadSafeMemory` is constructed with a `FileAgentHistoryStore` that loads these messages  
THEN `totalTokenUsed` is set to approximately 1/3 of the loaded token count (≈1667)  
AND the Compact UI shows a realistic non-zero value immediately.

Status: ✅ `ThreadSafeMemoryTest.constructorEstimatesTokenCountFromLoadedMessages`
