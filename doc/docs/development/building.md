---
title: Building from Source
description: How to build Eclipse Peon AI
---

# Building from Source

## Prerequisites

- Java 21
- Maven 3.9+
- Eclipse 2025-12 with PDE (Plugin Development Environment)

## Build Commands

```bash
# Clone the repository
git clone https://github.com/sterl/eclipse-peon-ai.git
cd eclipse-peon-ai

# Build the complete project
mvn clean verify

# Build without tests
mvn clean package
```

## Development Launch

To run the plugin in development mode:

1. Import the project into Eclipse
2. Create an Eclipse Application launch configuration
3. Add arguments: `-clean -clearPersistedState`
4. Run the launch configuration

## Known Issues

### Incremental Build Bug

Eclipse 4.38 has a PDE/JDT bug where incremental builds produce broken `.class` files after the first launch.

**Workaround**: Use **Project > Clean** before re-launching the Eclipse Application.
