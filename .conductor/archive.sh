#!/usr/bin/env bash
# Conductor archive script — runs before the workspace is archived.
# Tears down this workspace's docker compose stack (compose project name is
# derived from the workspace directory) including volumes. Skips gracefully
# when the Docker daemon is not running so archiving never fails on it.
# Docs: https://conductor.build/docs/reference/scripts
set -euo pipefail

if docker info >/dev/null 2>&1; then
  docker compose down -v --remove-orphans
else
  echo "Docker daemon not running — skipping compose teardown."
fi
