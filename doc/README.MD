# Eclipse Peon AI Documentation

This directory contains the MkDocs-based documentation for Eclipse Peon AI.

## Setup

### Prerequisites
- Python 3.7 or higher
- pip (Python package manager)

### Installation

```bash
pip install -r requirements.txt
```

## Commands

### Development
- `mkdocs serve` - Start the live-reloading docs server at http://127.0.0.1:8000
- `mkdocs serve --dirtyreload` - Faster reload during development

**Windows alternative** (if mkdocs not in PATH):
```bash
python -m mkdocs serve
python -m mkdocs serve --dirtyreload
```

### Build
- `mkdocs build` - Build the documentation site to `site/` directory
- `mkdocs build --clean` - Build with clean output directory

**Windows alternative** (if mkdocs not in PATH):
```bash
python -m mkdocs build
python -m mkdocs build --clean
```

## Publishing

To publish changes to GitHub Pages:
```bash
./publish.sh
```
