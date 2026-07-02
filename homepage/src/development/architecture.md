---
title: Architecture
description: Eclipse Peon AI architecture overview
---

# Architecture

Eclipse Peon AI follows the Eclipse RCP / e4 application model.

## Module Structure

```
llm-peon/
├── pom.xml                         # Parent POM with Tycho configuration
├── org.sterl.llmpeon/              # Main Eclipse plugin (e4)
│   └── src/org/sterl/llmpeon/
│       ├── parts/                  # UI components
│       │   ├── AIChatView.java     # Main chat view
│       │   ├── widget/             # SWT widgets
│       │   ├── tools/              # LLM tools
│       │   └── config/             # Preferences
│       └── ...
├── org.sterl.llmpeon.core/         # Standalone Maven JAR (non-OSGi)
├── org.sterl.llmpeon.test/         # Test module for core library
├── releng/
│   ├── llmpeon-target/             # Target platform (Eclipse 2025-12)
│   ├── llmpeon-feature/           # Eclipse feature
│   └── llmpeon-update-site/       # p2 repository
└── doc/                           # Documentation
```

## Projects Overview

| Project | Type | Purpose |
|---------|------|--------|
| `llmpeon-parent` | Maven Parent POM | Build configuration root with Tycho setup |
| `org.sterl.llmpeon.core` | Java/Maven JAR | Non-OSGi core library (pure Java) |
| `org.sterl.llmpeon` | PDE Plugin Bundle | Main Eclipse RCP/e4 plugin with UI components |
| `org.sterl.llmpeon.test` | Test Project | EPL-qualified test module for `llmpeon-core` using JUnit 5 |

## Technology Stack

- **Eclipse RCP / e4**: Pure e4 application model
- **Java 21**: Modern Java features
- **Maven + Tycho**: Build system for Eclipse plugins
- **LangChain4j**: LLM integration framework  
- **JUnit 5**: Testing framework for core module validation

## UI Components

### Chat View
The main view implemented as an e4 Part with SWT controls.

### Chat Widget
Custom SWT widget for displaying chat messages with markdown support.

### Tools
LLM-powered tools that can interact with the Eclipse workspace:
- File reading/writing
- Search
- Context awareness

## Dependency Management

Third-party libraries are embedded via `Bundle-ClassPath` into the `lib/` folder. The target platform provides OSGi, SWT, JFace, and other Eclipse runtime components.
