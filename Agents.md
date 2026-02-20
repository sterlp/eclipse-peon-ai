# Agent Guidelines for EclipseLLMPeon

EclipseLLMPeon is an Eclipse RCP plugin that integrates a lightweight, context-aware LLM assistant into the Eclipse workbench.

The project uses a pragmatic setup with embedded third-party libraries (no p2 wrapping) and is built using Maven + Tycho against the Eclipse 2025-12 target platform.

---

## Skills

All project custom skills area in the `skills` folder. Read only the header packed into "---" name and description and reference them as needed.

## Architecture

- **Parent POM** (`pom.xml`): Aggregator with Tycho 5.x, defines target platform
- **`org.sterl.llmpeon`**: Main Eclipse plugin (e4), packaging `eclipse-plugin`
- **`releng/llmpeon-target`**: Target platform definition (Eclipse 2025-12)
- **`releng/llmpeon-feature`**: Eclipse feature wrapping the plugin
- **`releng/llmpeon-update-site`**: p2 repository / update site (deployed to GitHub Pages)
- **`org.sterl.llmpeon.core`**: Standalone Maven JAR module (not part of reactor, not an OSGi bundle)

## Workflow

- **The user commits and pushes themselves** — never create commits or push on behalf of the user
- Signal "ready for review" when changes are complete

## Key Technical Decisions

- Eclipse RCP / e4 application (pure e4, not legacy 3.x workbench)
- Java 21
- Maven + Tycho build
- Third-party libraries embedded via `Bundle-ClassPath` into `lib/`
- Source JARs in `sources/`
- Source attachment handled explicitly via `.classpath`
- `.classpath` is intentionally committed
- No license defined yet (all rights reserved)

---

## Dependency Management

- External JARs (langchain4j, jackson, etc.) are copied to `lib/` via `maven-dependency-plugin`
- `lib/` and `sources/` are NOT checked into git (`.gitignore` excludes `*.jar`, `lib/`, `sources/`)
- `MANIFEST.MF` `Bundle-ClassPath`, `build.properties` `bin.includes`, and `.classpath` must all list the **same** JARs
- Only whitelist needed groupIds via `includeGroupIds` - do NOT copy all transitive deps
- Platform-provided JARs (jakarta, osgi, jna, asm, jetty, felix, etc.) must NOT be in `lib/` - they come from the target platform

---

## SWT / e4 Patterns

- Views are Parts defined in `fragment.e4xmi`, using `@Inject` and `@PostConstruct`
- SWT resources (Image, Color, Font) created with `new` **MUST** be disposed (use `addDisposeListener`)
- Platform shared images (from `ISharedImages`, `DebugUITools`) must **NOT** be disposed - they are owned by the platform
- `org.eclipse.ui` and `org.eclipse.debug.ui` are in `Require-Bundle` for shared image access

---

## Build

- Build from root: `mvn clean verify`
- To populate `lib/` without full build: `mvn clean process-resources`
- Eclipse launch args: `-clean -clearPersistedState`

---

## Error Handling in Tools

- **Throw exceptions** on real errors (IO failures, workspace corruption, charset issues). The ChatWidget catches these and displays the error to the user.
- **Return error strings** only when the LLM can act on the information (e.g. "File not found" → LLM can retry with a different path, "No file is currently selected" → LLM can ask the user to select a file).
- Never swallow exceptions silently or return `"Error: ..."` strings for failures that the LLM cannot recover from. Let them propagate.
- ToolContext wraps checked exceptions (IOException, CoreException) in `RuntimeException`. Tools (`@Tool` methods) don't declare checked exceptions — the chat layer catches RuntimeException and displays the error.

---

## Eclipse RCP Documentation

When looking up how to develop e4 RCP widgets, views, or handlers, consult:
- Vogella Eclipse RCP Tutorial: https://www.vogella.com/tutorials/EclipseRCP/article.html
- Vogella e4 Development Practices: https://www.vogella.com/tutorials/Eclipse4DevelopmentPractises/article.html
- Eclipse Wiki e4 Tutorials: https://wiki.eclipse.org/Eclipse4/Tutorials
- EclipseSource e4 Tutorial (PDF): https://eclipsesource.com/tutorial-downloads/Eclipse_4_Tutorial.pdf
