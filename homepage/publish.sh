#!/bin/bash
npm install
npm run docs:build

# Configuration
HOST="w0125542.kasserver.com"
USER="f01810c9"
PASSWORD=$(security find-generic-password -a "$USER" -s "$HOST" -w)
LOCAL_DIR=".vitepress/dist"
REMOTE_DIR="."

# Upload with mirror
lftp -u "$USER","$PASSWORD" "$HOST" <<EOF
mirror --reverse \
       --delete \
       --verbose \
       --parallel=8 \
       "$LOCAL_DIR" "$REMOTE_DIR"
quit
EOF