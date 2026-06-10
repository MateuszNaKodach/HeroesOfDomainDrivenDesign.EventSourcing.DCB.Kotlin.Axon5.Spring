#!/usr/bin/env bash
# Conductor run script — started by the Run button (and after setup via
# auto_run_after_setup). Runs the test suite; Testcontainers gives each
# workspace isolated containers, so concurrent runs are safe.
# Docs: https://conductor.build/docs/reference/scripts/run
set -euo pipefail

./mvnw -B test
