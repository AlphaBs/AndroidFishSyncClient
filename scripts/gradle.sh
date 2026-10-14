#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -n "${FISHSYNC_JAVA_HOME:-}" ]]; then
  export JAVA_HOME="$FISHSYNC_JAVA_HOME"
elif [[ -f .local/tools/java-path ]]; then
  export JAVA_HOME="$(cat .local/tools/java-path)"
fi
exec ./gradlew "$@"
