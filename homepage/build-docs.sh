#!/bin/bash

echo "Building VitePress documentation..."
cd "$(dirname "$0")"

# Install dependencies if not already installed
if [ ! -d "node_modules" ]; then
    echo "Installing npm dependencies..."
    npm install
fi

# Build the documentation
echo "Running VitePress build..."
npm run docs:build

# Check if build succeeded
if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
    echo "Output directory: .vitepress/dist"
    ls -la .vitepress/dist
else
    echo "❌ Build failed"
    exit 1
fi