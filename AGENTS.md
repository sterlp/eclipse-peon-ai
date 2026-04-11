# how to build

- in eclipse clean build
- or non eclipse ide `mvn clean verify` in the shell

Verify your changes after code changes.

# Structure

This is an eclipse plugin RCP project in java.

- doc - mkdocs defining the docs and spec of the project
- org.sterl.llmpeon.core - non eclipse specific code and tests
- org.sterl.llmpeon - eclipse plugin code
- org.sterl.llmpeon.test - eclipse plugin tests

## Dependency Management

- External JARsare copied to `lib/` via `maven-dependency-plugin`
- `MANIFEST.MF` `Bundle-ClassPath`, `build.properties` `bin.includes`, and `.classpath` must all list the **same** JARs
- Only whitelist needed groupIds via `includeGroupIds` - do NOT copy all transitive deps
- Platform-provided JARs (jakarta, osgi, jna, asm, jetty, felix, etc.) must NOT be in `lib` - they come from the target platform

## Error Handling in Tools

- non eclipse IDEs cannot build the project use clean package