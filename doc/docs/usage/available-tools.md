---
title: Available Tools
description: Tools available to the AI assistant
---

# Available Tools

The AI assistant has access to several tools to interact with your Eclipse workspace.

## File Operations

### Read File
Reads the complete contents of any file in your workspace.

### Write File
Creates new files or modifies existing ones. Supports atomic writes.

### Search Files
Searches for text patterns across your project using regex.

## Context Tools

### Read Selected File
Quickly reads the file currently selected in the editor or Project Explorer.

### Update Selected File
Applies changes to the currently selected file (useful for refactoring).

## Usage Examples

```
User: "Read the main.java file"
AI:  [uses ReadFileTool]

User: "Create a new test file for MyService"
AI:  [uses CreateFileTool]

User: "Find all uses of deprecatedAPI"
AI:  [uses SearchFilesTool]

User: "What is in the file I have open?"
AI:  [uses ReadSelectedFileTool]
```

## Tool Descriptions

The AI automatically selects appropriate tools based on your request. You don't need to invoke them manually - just describe what you want to accomplish.
