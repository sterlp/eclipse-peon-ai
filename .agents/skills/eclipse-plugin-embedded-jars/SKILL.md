---
name: eclipse-plugin-embedded-jars
description: Embeds Maven dependencies into Eclipse plugin bundles. Use when adding third-party JARs not provided by the Eclipse target platform.
---

# Eclipse Plugin Embedded JARs

Eclipse plugins do not load Maven dependencies automatically. If a library is not provided by the target platform, copy its runtime JARs into the plugin `lib/` folder and list them in the PDE/OSGi metadata.

## Workflow

```
- [ ] 1. Add the Maven dependency to the module that uses it.
- [ ] 2. Run mvn dependency:tree -Dscope=runtime in that module.
- [ ] 3. Identify direct and transitive runtime JARs not provided by the target platform.
- [ ] 4. In the plugin pom.xml, add their groupIds to maven-dependency-plugin <includeGroupIds> so copy-dependencies copies them into lib/.
- [ ] 5. Run mvn clean install from the root or plugin project.
- [ ] 6. Add each copied lib/<artifactId>.jar to META-INF/MANIFEST.MF Bundle-ClassPath.
- [ ] 7. Add each copied lib/<artifactId>.jar to build.properties bin.includes.
- [ ] 8. Refresh/reimport the plugin project or add each lib/<artifactId>.jar to .classpath for Eclipse compilation.
```

## Rules

- **Target platform first**: Do not embed JARs already supplied by the Eclipse target platform; use OSGi dependencies for those.
- **Transitives**: Missing runtime transitive JARs cause `ClassNotFoundException` even if compilation succeeds.
- **MANIFEST.MF**: `Bundle-ClassPath` is the runtime classpath for embedded JARs.
- **build.properties**: `bin.includes` controls what is packaged into the plugin artifact.
- **.classpath**: Development-time only; it does not affect plugin runtime or packaging.
- **plugin.xml**: Do not edit for classpath/JAR embedding; it is for Eclipse extensions.
- **stripVersion**: This project copies JARs without versions, e.g. `langchain4j.jar`.
- **Removal**: Remove the dependency, copied JAR, `includeGroupIds`, `Bundle-ClassPath`, `bin.includes`, and `.classpath` entries.

## Files

| File | Purpose |
|------|---------|
| dependency module `pom.xml` | Declares what code uses |
| plugin `pom.xml` | Copies selected runtime JARs into `lib/` |
| plugin `META-INF/MANIFEST.MF` | OSGi runtime classpath |
| plugin `build.properties` | PDE packaging |
| plugin `.classpath` | Eclipse compiler visibility |
