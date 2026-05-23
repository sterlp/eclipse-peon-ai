# Spec driven development
The `doc/docs/design` contains the application design. The `HOW` 
AI changes only allowed here with user approval!

# Planmode
Interview me relentlessly about every aspect of this plan until we reach a shared understanding. Flag any doc inconsistencies or conflicts for resolution during planning. Walk down each branch of the design tree, resolving dependencies between decisions one-by-one. For each question, provide your recommended answer.

Ask the questions one at a time.

If a question can be answered by exploring the docs, explore `doc/docs/*.md` instead.
If a question can be answered by exploring the codebase, explore the codebase instead.

# how to build

- in eclipse `clean build`
- non eclipse IDE `mvn clean verify` in the shell

# Structure eclipse plugin RCP

- `doc/docs` - mkdocs defining the docs and spec of the project
- org.sterl.llmpeon.core - non eclipse specific code and tests
- org.sterl.llmpeon - eclipse plugin code
- org.sterl.llmpeon.test - eclipse plugin tests

## Dependency Management

- External JARsare copied to `lib/` via `maven-dependency-plugin`
- `MANIFEST.MF` `Bundle-ClassPath`, `build.properties` `bin.includes`, and `.classpath` must all list the **same** JARs
- Only whitelist needed groupIds via `includeGroupIds` - do NOT copy all transitive deps
- Platform-provided JARs (jakarta, osgi, jna, asm, jetty, felix, etc.) must NOT be in `lib` - they come from the target platform

# Docs

- Always update `/doc/.vitepress/config.ts` sidebar/nav when adding new pages to `docs/`
