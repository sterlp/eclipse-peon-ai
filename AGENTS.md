# Spec driven development
The `doc/docs/design` contains the application design. The `HOW` 
AI changes only allowed here with user approval!

# Global Rules

- **Thread Safety**: All code changes must be thread-safe (`Atomic*` / `ReentrantLock`). No single-threaded assumptions.
- **Testing Strategy**: See module guidelines below for runner specifics (Eclipse vs Shell).
- Write elegant, expressive code using modern Java (records, pattern matching, switch expressions) — readability like good prose; deduplicate via abstractions.

# Build
- command line `mvn clean verify` - use `verify` to run the eclipse plugin tests of org.sterl.llmpeon.test
- all other tests can be executed using the eclipse test tool runner

# Module Guidelines (Links)

Read these when working in specific modules:
- `/org.sterl.llmpeon/AGENTS.md` — Plugin UI & Logic (Error handling patterns, Job usage).
- `/llmpeon-core/AGENTS.md` — Core logic (Lombok conventions).
- `/org.sterl.llmpeon.test/AGENTS.md` — Test execution specifics.

# Structure eclipse plugin RCP

- org.sterl.llmpeon.core - non eclipse specific code and tests
- org.sterl.llmpeon - eclipse plugin code
- org.sterl.llmpeon.test - eclipse plugin tests

# Dependency Management

- External JARsare copied to `lib/` via `maven-dependency-plugin`
- `MANIFEST.MF` `Bundle-ClassPath`, `build.properties` `bin.includes`, and `.classpath` must all list the **same** JARs
- Only whitelist needed groupIds via `includeGroupIds` - do NOT copy all transitive deps
- Platform-provided JARs (jakarta, osgi, jna, asm, jetty, felix, etc.) must NOT be in `lib` - they come from the target platform

# Docs

- `doc/docs` - VitePress defining the docs and spec of the project
- Always update `/doc/.vitepress/config.ts` sidebar/nav when adding new pages to `docs/`
- Docs: canonical interaction-design.md lives in `design/`; user docs go in `usage/`. Use tooltips for hidden behavior hints.