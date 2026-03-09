# Ralph Loop

Autonomous AI agent orchestrator that implements Event Modeling slices from proophboard using Claude Code CLI.

## Quick Start

```bash
# Sequential (default, identical to legacy behavior)
node .ai/ralph/ralph.mjs --max-iterations 5

# Parallel — 3 concurrent worktrees
node .ai/ralph/ralph.mjs --max-worktrees 3 --max-iterations 10

# Parallel with streaming output
node .ai/ralph/ralph.mjs --max-worktrees 3 --stream

# Fresh start (wipe all previous state)
node .ai/ralph/ralph.mjs --fresh --max-worktrees 3
```

## CLI Arguments

| Argument | Default | Description |
|----------|---------|-------------|
| `--max-iterations N` | `10` | Total slice implementations before stopping |
| `--iterations N` | `10` | Alias for `--max-iterations` (backward compat) |
| `--max-worktrees N` | `1` | Max concurrent parallel slices. `1` = sequential mode |
| `--stream` | off | Stream Claude output to console with `[slice-name]` prefix |
| `--finalize pr\|merge\|none` | `pr` | How to finalize completed slices |
| `--discover every\|once` | `every` | When to re-discover planned slices from proophboard |
| `--fresh` | off | Wipe all previous state before starting |

## Modes

### Sequential (`--max-worktrees 1`, default)

Identical to the original Ralph loop. One Claude CLI invocation at a time, running in the repo root. No worktrees, no registry, no discovery phase.

### Parallel (`--max-worktrees > 1`)

Each slice gets its own git worktree. Up to N slices run concurrently.

## Architecture

### Worktree = Slice

Each worktree is dedicated to one slice and lives for the duration of that slice's implementation. Worktrees are never reused.

```
.claude/worktrees/
  ralph-build-dwelling/           ← worktree for "Build Dwelling"
  ralph-recruit-creature/         ← worktree for "Recruit Creature"
  ralph-view-dwelling/            ← worktree for "View Dwelling"
```

### Worktree Lifecycle

1. **Create**: `git worktree add .claude/worktrees/ralph-<slice-kebab> <parent-branch>`
2. **Maven install**: `./mvnw install -DskipTests -q` — compiles dependencies in the fresh worktree so Claude can build and test immediately
3. **Claude runs**: Full em2code-slice flow (branch, implement, quality gates, commit, finalize)
4. **Signal**: Claude outputs `SLICE_DONE` or `SLICE_BLOCKED`
5. **Cleanup**: Worktree removed, branch cleaned up

### Slice Discovery

A lightweight Claude invocation reads proophboard via MCP and returns planned slices as JSON.

- `--discover every` (default): Re-discover before each new worktree spawn
- `--discover once`: Discover only at startup

### Slice Locking

No race conditions — the Node.js orchestrator is the sole assigner. The `activeSlices` map in the registry IS the lock. Slices are registered before Claude is spawned, so two agents can never pick the same slice.

### Finalization Modes

| Mode | Behavior |
|------|----------|
| `pr` (default) | Claude creates a PR via `gh pr create` targeting parent branch |
| `merge` | Claude rebases + fast-forward merges to parent branch |
| `none` | Changes left on feature branch, worktree kept |

## Signals

| Signal | Mode | Meaning |
|--------|------|---------|
| `<promise>SLICE_DONE:<id></promise>` | Parallel | Slice implemented + finalized |
| `<promise>SLICE_BLOCKED:<id></promise>` | Parallel | Slice cannot be implemented |
| `<promise>COMPLETE</promise>` | Sequential | All slices done |
| `<promise>NO_TASKS</promise>` | Sequential | No planned slices available |

## Status Table

Printed on worktree start/complete/error, every 60s, and at final summary:

```
══════════════════════════════════════════════════════════════════════════
  Ralph Status | Completed: 3/10 | Active: 2/3 worktrees | Elapsed: 8m 32s
  Finalize: pr | Parent: main
══════════════════════════════════════════════════════════════════════════
  🔨 "Build Dwelling"        feature/build-dwelling        [3m 12s]  implementing
  ✅ "Recruit Creature"      feature/recruit-creature       [5m 45s]  → PR #42        143k tokens
  ✅ "Start Scenario"        feature/start-scenario         [4m 10s]  → merged         98k tokens
══════════════════════════════════════════════════════════════════════════
```

## Crash Recovery

On startup with an existing registry, Ralph checks each active slice's PID:
- **Process alive**: Leave it running
- **Process dead, worktree exists**: Remove worktree, re-queue slice
- **Process dead, no worktree**: Re-queue slice

The em2code-slice skill has its own progress recovery via `.ai/temp/feature-*/progress.md`.

## Files

| File | Purpose |
|------|---------|
| `.ai/ralph/ralph.mjs` | Orchestrator script |
| `.ai/ralph/prompt.md` | Base prompt for Claude agents |
| `.ai/temp/ralph-registry.json` | Active assignments + history (parallel mode) |
| `.ai/temp/ralph-state.json` | Iteration state (sequential mode) |
| `.ai/temp/ralph-<slice>.log` | Per-slice Claude output logs |
| `.claude/worktrees/ralph-*/` | Per-slice git worktrees |

## Fresh Start

`--fresh` wipes all previous state:
- Deletes `ralph-registry.json`, `ralph-state.json`
- Deletes all `ralph-*.log` files
- Removes all `ralph-*` worktrees
- Deletes all `feature-*/progress.md` files
