# EclipseLLMPeon

EclipseLLMPeon is an Eclipse RCP plugin that integrates a lightweight, context-aware LLM assistant directly into the Eclipse workbench to support developers in their daily workflow.

The project focuses on a pragmatic, embedded approach without p2 wrapping, keeping the setup simple and transparent.

---

## Requirements

- Java 21
- Maven (with Tycho)
- Eclipse 2024-12 target platform

---

## Build

Build the project from the repository root:

```bash
mvn clean verify
```

### Libs
- https://github.com/markdown-it/markdown-it
- https://highlightjs.org/