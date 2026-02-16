# EclipseLLMPeon

EclipseLLMPeon is an Eclipse RCP plugin that integrates a lightweight, context-aware LLM assistant directly into the Eclipse workbench to support developers in their daily workflow.

The project focuses on a pragmatic, embedded approach without p2 wrapping, keeping the setup simple and transparent.

![Exsample](./exsample.png)

---

## Requirements

- Java 21
- Maven (with Tycho)
- Eclipse 2025-12 target platform

---

## Build

Build the project from the repository root:

```bash
mvn clean package
```

### Clean start

`-clean -clearPersistedState`

### Known: Incremental Build Issue

Eclipse 4.38 has a PDE/JDT bug where incremental builds produce broken `.class` files after the first launch. To work around this, do **Project > Clean** before re-launching the Eclipse Application.

See [eclipse-build-issues.md](eclipse-build-issues.md) for details.

### Libs
- https://github.com/markdown-it/markdown-it
- https://cdnjs.com/libraries/markdown-it
- https://highlightjs.org/

---

## Links

### Eclipse RCP / e4 Development
- [Vogella Eclipse RCP Tutorial](https://www.vogella.com/tutorials/EclipseRCP/article.html) - comprehensive, regularly updated
- [Vogella e4 Development Practices](https://www.vogella.com/tutorials/Eclipse4DevelopmentPractises/article.html) - naming conventions, model elements
- [Eclipse Wiki e4 Tutorials](https://wiki.eclipse.org/Eclipse4/Tutorials) - curated community tutorials
- [EclipseSource e4 Tutorial (PDF)](https://eclipsesource.com/tutorial-downloads/Eclipse_4_Tutorial.pdf) - core concepts from scratch

### LLM Integration
- [LangChain4j](https://docs.langchain4j.dev/) - Java LLM framework used in this project
- [Ollama](https://ollama.com/) - local LLM runtime