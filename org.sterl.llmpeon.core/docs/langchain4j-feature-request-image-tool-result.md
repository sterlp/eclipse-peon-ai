# Feature request: images in tool results for every provider

**Summary**

Allow a `ToolExecutionResultMessage` containing `ImageContent` to be sent to providers whose API
does not accept non-text tool results, by transparently re-shaping it in the provider mapper.

**Current behaviour (checked 1.22.0 and main 1.24.0-beta34-SNAPSHOT)**

- Anthropic, Google AI Gemini and the OpenAI Responses API accept `ImageContent` inside a tool
  result.
- OpenAI Chat Completions (`OpenAiUtils`), Ollama (`InternalOllamaHelper`) and Mistral
  (`MistralAiMapper`) throw `UnsupportedFeatureException` ("does not support non-text content in
  tool results").
- Consequence: a tool that returns an image works on some providers and poisons the chat memory on
  others — every later request fails as long as the message stays in memory. Applications must
  build provider-specific workarounds.

**Proposal**

When a provider cannot carry images in a tool result, the mapper converts it on send:

1. `tool` message keeps the text parts; if there are none, a placeholder such as
   `(see attached image)`.
2. After the last tool result of the turn, append a `user` message containing the images (with a
   short text like `Attached image(s) from tool result:`).
3. Optionally (configurable, for APIs that reject `user` directly after `tool`), insert a short
   synthetic `assistant` message between them.

Images in the chat memory stay canonical (inside the tool result); only the wire format differs.
Ideally the behaviour is opt-in or opt-out via a builder flag, so existing applications are not
surprised.

**Why**

- Tools that read screenshots, UI test results or diagrams are common in coding agents.
- Vision support is a property of the model, not of the "tool result" API shape — most of these
  models do understand images in a `user` message.
- Other agent frameworks already do this re-shaping in their provider adapters.

**Workaround we use today**

The tool returns text only; the application appends `AiMessage` + `UserMessage(ImageContent)` to
the memory itself.
