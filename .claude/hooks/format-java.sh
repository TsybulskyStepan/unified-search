#!/usr/bin/env bash
# PostToolUse (Edit|Write): format the Java file Claude just changed, so style never reaches review.
set -euo pipefail

file=$(jq -r '.tool_input.file_path // empty')
[[ "$file" == *.java ]] || exit 0

cd "$CLAUDE_PROJECT_DIR"
./gradlew --quiet spotlessApply -PspotlessIdeHook="$file"
