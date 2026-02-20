# Eclipse Peon AI

Eclipse Peon AI is an Eclipse RCP plugin that integrates a lightweight, context-aware LLM assistant directly into your Eclipse workbench. It helps developers with coding tasks by providing AI-powered assistance while keeping full control over your code.

## Features

- **Chat Interface**: Interactive AI chat with syntax-highlighted code blocks and markdown rendering
- **File Operations**: Read, write, search, and modify files directly from the chat
- **Context Awareness**: Understands your current workspace and selected files
- **Local LLM Support**: Works with Ollama for privacy-focused local inference
- **Multi-Provider**: Supports various LLM providers through LangChain4j

![example](./assets/example.png)

## Installation

### Update Site (Recommended)

Install directly from Eclipse:

1. Go to **Help > Install New Software**
2. Add the update site: `https://github.com/sterlp/eclipse-peon-ai`
3. Select "Eclipse Peon AI" and follow the installation wizard
4. Restart Eclipse

### Requirements

- Eclipse 2025-12 or newer
- Java 21
- An LLM provider (Ollama recommended for local use)

## Configuration

After installation, configure the plugin via **Window > Preferences > Peon AI**:

1. **Provider**: Select your LLM provider (Ollama, OpenAI, Anthropic, etc.)
2. **Model**: Enter the model name (e.g., `llama3`, `codellama` for Ollama)
3. **Base URL**: Set the API endpoint (e.g., `http://localhost:11434` for Ollama)
4. **API Key**: Enter your API key if required

## Usage

### Opening the Chat View

1. Go to **Window > Show View > Other...**
2. Search for "Peon AI" or "AI Chat"
3. The chat view will appear in your workbench

### Available Tools

The AI assistant has access to several tools:

- **Read File**: Read contents of any file in your workspace
- **Write File**: Create or modify files
- **Search Files**: Search for text across your project
- **Read Selected File**: Quick access to the currently selected file
- **Update Selected File**: Modify the currently selected file

### Example Workflows

1. **Explain Code**: Select a file and ask "What does this code do?"
2. **Generate Tests**: Ask "Write unit tests for the selected class"
3. **Refactor**: Select code and ask "How can I improve this?"
4. **Debug**: Paste error messages and ask "What's causing this?"

## Architecture

- **Main Plugin** (`org.sterl.llmpeon`): Eclipse e4 plugin with UI components
- **Core** (`org.sterl.llmpeon.core`): Standalone module for LLM integration
- **Target Platform** (`releng/llmpeon-target`): Eclipse 2025-12 based target platform
- **Features** (`releng/llmpeon-feature`): Eclipse feature definition

### Technology Stack

- Eclipse RCP / e4
- Java 21
- Maven + Tycho
- LangChain4j for LLM integration
- Markdown-it for markdown rendering
- Highlight.js for syntax highlighting
- Diff2Html for diff visualization

## Building from Source

```bash
# Clone the repository
git clone https://github.com/sterl/eclipse-peon-ai.git
cd eclipse-peon-ai

# Build the project
mvn clean verify

# Run in development mode
# Use Eclipse Application launch with -clean -clearPersistedState
```

### Known Issues

- **Incremental Build Bug**: Eclipse 4.38 has a PDE/JDT bug where incremental builds produce broken `.class` files. Use **Project > Clean** before re-launching.

## Links

- [GitHub Repository](https://github.com/sterl/eclipse-peon-ai)
- [Issue Tracker](https://github.com/sterl/eclipse-peon-ai/issues)
- [LangChain4j Documentation](https://docs.langchain4j.dev/)
- [Ollama](https://ollama.com/)
