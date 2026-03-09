# Ralph Loop

Autonomous AI agent orchestrator that implements Event Modeling slices from proophboard using Claude Code CLI.

## Quick Start

```bash
# Using the shell wrapper (recommended)
./eventmodeling-loop.sh

# Sequential (legacy mode)
node .ai/ralph/ralph.mjs --max-iterations 5

# Parallel — 3 concurrent worktrees
node .ai/ralph/ralph.mjs --max-worktrees 3 --max-iterations 10 --finalize none

# Parallel with streaming output
node .ai/ralph/ralph.mjs --max-worktrees 3 --stream

# Fresh start (wipe all previous state)
node .ai/ralph/ralph.mjs --fresh --max-worktrees 3
```

> `eventmodeling-loop.sh` runs: `--max-worktrees 3 --max-iterations 10 --finalize none --discover every`

## CLI Arguments

| Argument | Default | Description |
|----------|---------|-------------|
| `--max-iterations N` | `10` | Total slice implementations before stopping |
| `--iterations N` | `10` | Alias for `--max-iterations` (backward compat) |
| `--max-worktrees N` | `1` | Max concurrent parallel slices. `1` = sequential mode |
| `--stream` | off | Stream Claude output to console with `[slice-name]` prefix |
| `--finalize pr\|merge\|none` | `pr` | How to finalize each completed slice |
| `--discover every\|once` | `every` | When to re-discover planned slices from proophboard |
| `--fresh` | off | Wipe all previous state before starting |

## Modes

### Sequential (`--max-worktrees 1`, default)

Identical to the original Ralph loop. One Claude CLI invocation at a time, running in the repo root. No worktrees, no registry, no discovery phase.

### Parallel (`--max-worktrees > 1`)

Each slice gets its own isolated git worktree. Up to N slices run concurrently as separate Claude agents.

## Architecture

### Worktree = Slice

Each worktree is dedicated to one slice and lives only for the duration of that slice's implementation. Worktrees are never reused.

```
.claude/worktrees/
  ralph-build-dwelling/       ← worktree for "Build Dwelling"
  ralph-recruit-creature/     ← worktree for "Recruit Creature"
  ralph-view-dwelling/        ← worktree for "View Dwelling"
```

### Worktree Lifecycle

1. **Create** — `git worktree add .claude/worktrees/ralph-<slice-kebab> <parent-branch>`
2. **Maven install** — `./mvnw install -DskipTests -q` compiles all dependencies so Claude can build and test immediately
3. **Claude runs** — full `em2code-slice` flow: feature branch → implement → quality gates → commit → finalize
4. **Signal** — Claude outputs `SLICE_DONE` or `SLICE_BLOCKED`
5. **Cleanup** — worktree removed, branch cleaned up

### Slice Discovery

A lightweight Claude invocation reads proophboard via MCP and returns planned slices as JSON sorted by priority (write slices that unblock others first).

| Mode | Behaviour |
|------|-----------|
| `--discover every` (default) | Re-discover before each new worktree spawn — picks up slices added mid-run |
| `--discover once` | Discover only at startup — faster, but won't see new slices added during the run |

### Slice Locking

No race conditions — the Node.js orchestrator is the sole assigner. The `activeSlices` map in the registry IS the lock. Slices are registered before Claude is spawned, so two agents can never pick the same slice.

### Finalization Modes

| Mode | Behaviour |
|------|-----------|
| `pr` | Claude creates a PR via `gh pr create` targeting the parent branch |
| `merge` | Claude rebases + fast-forward merges to parent branch, deletes feature branch |
| `none` | Changes left on the feature branch — user decides later |

### Output per Worker

Claude output is streamed in real-time to two places:

| Destination | Path |
|-------------|------|
| Orchestrator log | `.ai/temp/ralph-<slice-kebab>.log` |
| Worktree progress file | `<worktree>/.ai/temp/claude-output.md` |

With `--stream`, output is also interleaved on the console prefixed with `[slice-name]`.

## Status Table

Printed on worktree start, completion, error, every 60 s heartbeat, and final summary. Uses `console.table` for aligned columns:

```
  🤖 Ralph │ 3/10 done │ 2/3 active │ 8m 32s │ finalize: none │ parent: main
┌─────────┬────┬──────────────────────┬────────────────────────────┬────────┬──────────────┬────────┐
│ (index) │    │ Slice                │ Branch                     │ Time   │ Status       │ Tokens │
├─────────┼────┼──────────────────────┼────────────────────────────┼────────┼──────────────┼────────┤
│    0    │ 🔨 │ 'Build Dwelling'     │ 'feature/build-dwelling'   │ '3m 12s' │ 'implementing' │ ''   │
│    1    │ ✅ │ 'Recruit Creature'   │ 'feature/recruit-creature' │ '5m 45s' │ '→ on branch'  │ '143k'│
│    2    │ ✅ │ 'Start Scenario'     │ 'feature/start-scenario'   │ '4m 10s' │ '→ on branch'  │ '98k' │
└─────────┴────┴──────────────────────┴────────────────────────────┴────────┴──────────────┴────────┘
  Queue: 2 │ Total tokens: 241k
```

## Signals

| Signal | Mode | Meaning |
|--------|------|---------|
| `<promise>SLICE_DONE:<id></promise>` | Parallel | Slice implemented + finalized |
| `<promise>SLICE_BLOCKED:<id></promise>` | Parallel | Slice cannot be implemented |
| `<promise>COMPLETE</promise>` | Sequential | All slices done |
| `<promise>NO_TASKS</promise>` | Sequential | No planned slices available |

## Slice Statuses

| Icon | Status | Meaning |
|------|--------|---------|
| 🔨 | implementing | Claude is actively working on the slice |
| ✅ | completed | Slice done and finalized |
| 🚫 | BLOCKED | Slice cannot be implemented (missing deps, unclear spec) |
| ❓ | STALLED | Claude tried to ask an interactive question instead of proceeding autonomously — needs human attention, not re-queued |

## Crash Recovery

On startup with an existing registry, Ralph checks each active slice's PID:
- **Process alive** — leave it running
- **Process dead, worktree exists** — remove worktree, re-queue slice
- **Process dead, no worktree** — re-queue slice

The `em2code-slice` skill has its own progress recovery via `.ai/temp/feature-*/progress.md`.

## Files

| File | Purpose |
|------|---------|
| `eventmodeling-loop.sh` | Shell entry point with project defaults |
| `.ai/ralph/ralph.mjs` | Orchestrator script |
| `.ai/ralph/prompt.md` | Base prompt injected into every Claude agent |
| `.ai/temp/ralph-registry.json` | Active slice assignments + history (parallel mode) |
| `.ai/temp/ralph-state.json` | Iteration state (sequential mode) |
| `.ai/temp/ralph-<slice>.log` | Per-slice Claude output log |
| `<worktree>/.ai/temp/claude-output.md` | Live Claude output inside each worktree |
| `.claude/worktrees/ralph-*/` | Per-slice git worktrees (gitignored) |

## Fresh Start

`--fresh` wipes all previous state before starting a clean run:
- Deletes `ralph-registry.json` and `ralph-state.json`
- Deletes all `ralph-*.log` files
- Removes all `ralph-*` worktrees
- Deletes all `feature-*/progress.md` files
