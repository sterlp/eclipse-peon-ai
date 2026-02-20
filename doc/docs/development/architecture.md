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
├── releng/
│   ├── llmpeon-target/             # Target platform (Eclipse 2025-12)
│   ├── llmpeon-feature/           # Eclipse feature
│   └── llmpeon-update-site/       # p2 repository
└── doc/                           # Documentation
```

## Technology Stack

- **Eclipse RCP / e4**: Pure e4 application model
- **Java 21**: Modern Java features
- **Maven + Tycho**: Build system for Eclipse plugins
- **LangChain4j**: LLM integration framework

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
