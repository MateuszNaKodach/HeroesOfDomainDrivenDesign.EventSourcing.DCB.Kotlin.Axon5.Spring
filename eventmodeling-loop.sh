#!/usr/bin/env bash
# Event Modeling Loop - runs Ralph to implement slices autonomously
# Usage: ./eventmodeling-loop.sh [--iterations N]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

exec node "$SCRIPT_DIR/.ai/ralph/ralph.mjs" --max-worktrees 3 --max-iterations 10 --finalize none --discover every "$@"
