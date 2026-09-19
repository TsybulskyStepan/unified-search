#!/usr/bin/env bash
# Stop: don't let Claude finish a turn with code that fails formatting or doesn't compile.
# Tests are not run here (Testcontainers suites are slow); `./gradlew check` is the definition of done.
set -uo pipefail

input=$(cat)
# Already continuing because of this hook: let it stop rather than loop.
[[ $(jq -r '.stop_hook_active // false' <<<"$input") == true ]] && exit 0

cd "$(git rev-parse --show-toplevel)"
# Nothing build-relevant changed since the last commit.
[[ -z $(git status --porcelain -- src build.gradle settings.gradle 2>/dev/null) ]] && exit 0

if ! output=$(./gradlew --quiet spotlessCheck compileTestJava 2>&1); then
  echo "Build verification failed. Fix before finishing:" >&2
  tail -40 <<<"$output" >&2
  exit 2
fi
