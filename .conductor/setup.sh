#!/usr/bin/env bash
# Conductor setup script — runs once when a workspace is created (from the workspace dir).
# Docs: https://conductor.build/docs/reference/scripts/setup
set -euo pipefail

# Keep the root checkout current (documented Conductor pattern); local-only and
# never fails setup when the root branch can't fast-forward.
if [ "${CONDUCTOR_IS_LOCAL:-1}" = "1" ] && [ -n "${CONDUCTOR_ROOT_PATH:-}" ]; then
  git -C "$CONDUCTOR_ROOT_PATH" fetch --prune origin && git -C "$CONDUCTOR_ROOT_PATH" pull --ff-only || true
fi

# Warm the Maven repo and compile sources + tests so the first agent run is fast.
./mvnw -B -q -DskipTests dependency:go-offline test-compile
