## Project State (Feb 2026)

EclipseLLMPeon is an Eclipse RCP plugin that integrates a lightweight, context-aware LLM assistant into the Eclipse workbench.

The project uses a pragmatic setup with embedded third-party libraries (no p2 wrapping) and is built using Maven + Tycho against the Eclipse 2024-12 target platform.

---

## Key Technical Decisions

- Eclipse RCP / e4 application
- Java 21
- Maven + Tycho build
- Target platform defined in `llmpeon-target/`
- Third-party libraries embedded via `Bundle-ClassPath`
- Runtime JARs in `lib/`
- Source JARs in `sources/`
- Source attachment handled explicitly via `.classpath`
- `.classpath` is intentionally committed
- No license defined yet (all rights reserved)

---

## Current Focus

- Stable OSGi wiring and classloading
- Clean build with Tycho
- Reliable IDE source navigation for embedded libraries
- Incremental development of the LLM chat view and UI integration
