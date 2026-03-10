# Ralph Loop

Autonomous AI agent orchestrator that implements Event Modeling slices from proophboard using Claude Code CLI.

## Quick Start

```bash
# Using the shell wrapper (recommended)
./eventmodeling-loop.sh

# Sequential (legacy mode)
node .ai/ralph/ralph.mjs --max-iterations 5

# Parallel — 3 concurrent worktrees
node .ai/ralph/ralph.mjs --max-worktrees 3 --max-iterations 10 --finalize merge

# Parallel with streaming output
node .ai/ralph/ralph.mjs --max-worktrees 3 --stream

# Fresh start (wipe all previous state)
node .ai/ralph/ralph.mjs --fresh --max-worktrees 3
```

> `eventmodeling-loop.sh` runs: `--max-worktrees 3 --max-iterations 10 --finalize merge --discover every`

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
| `--conflict-commit separate\|squash` | `separate` | How to commit conflict resolution (separate commit vs squash into slice commit) |

## Modes

### Sequential (`--max-worktrees 1`, default)

Agent runs in the repo root on the parent branch. Implements one slice per iteration, commits directly on the parent branch. The orchestrator handles push/finalization after each iteration:
- `merge` mode: pushes the parent branch to remote
- `pr`/`none`: pushes the parent branch to remote

### Parallel (`--max-worktrees > 1`)

Each slice gets its own isolated git worktree. Up to N slices run concurrently as separate Claude agents. **Agents only commit and push** — all finalization (squash, rebase, merge/PR) is handled by the orchestrator's finalization pipeline.

## Architecture

### Separation of Concerns

```
[Parallel Worktrees]                [Ready Queue]              [Finalization Pipeline]
Agent 1 → implement → commit+push → ┐                           (runs in main repo)
Agent 2 → implement → commit+push → ├→ persistent queue  →  process sequentially:
Agent 3 → implement → commit+push → ┘   in registry           1. squash to 1 commit
                                                               2. rebase onto parent
                                                               3. Claude resolves conflicts
                                                               4. ff-merge / PR / none
                                                               5. push to remote
                                                               6. cleanup worktree+branch
```

Agents focus on implementation. The orchestrator owns finalization. This prevents race conditions on the parent branch and keeps conflict resolution centralized.

### Worktree = Slice

Each worktree is dedicated to one slice and lives only for the duration of that slice's implementation + finalization. Worktrees are never reused.

```
.claude/worktrees/
  ralph-build-dwelling/       ← worktree for "Build Dwelling"
  ralph-recruit-creature/     ← worktree for "Recruit Creature"
  ralph-view-dwelling/        ← worktree for "View Dwelling"
```

### Worktree Lifecycle

1. **Create** — `git worktree add .claude/worktrees/ralph-<slice-kebab> <parent-branch>`
2. **Maven install** — `./mvnw install -DskipTests -q` compiles all dependencies
3. **Claude runs** — implements slice, runs quality gates, commits, pushes to remote
4. **Signal** — Claude outputs `SLICE_DONE` or `SLICE_BLOCKED`
5. **Ready Queue** — orchestrator enqueues for finalization
6. **Finalization** — orchestrator squashes, rebases, merges/creates PR, cleans up

### Ready Queue & Finalization Pipeline

When an agent signals `SLICE_DONE`, the slice is added to a persistent `readyQueue` in the registry. A concurrent finalization loop processes entries one at a time:

1. **Remove worktree** — free the branch lock so finalization can checkout it
2. **Squash** — collapse feature branch commits to a single commit
3. **Rebase** — rebase the single commit onto the (updated) parent branch
4. **Rebase resolution** — if rebase fails, spawn a lightweight Claude agent:
   - **Conflicts**: resolve merge conflicts using project-specific rules (sealed interfaces, `when` blocks, EventTags)
   - **Non-conflict failures**: diagnose (dirty state, lock files, etc.), fix, and retry
   - Output logged to `.ai/temp/ralph-rebase-<slice>.log`
   - Post-rebase: run `./mvnw test`, create fix commit only if tests fail
5. **Finalize** — based on `--finalize` mode:
   - `merge`: fast-forward merge into parent + push to remote
   - `pr`: force-push rebased branch + `gh pr create`
   - `none`: leave branch as-is
6. **Cleanup** — delete branch (if merge mode)

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
| `pr` | Orchestrator rebases + pushes branch, creates PR via `gh pr create` |
| `merge` | Orchestrator rebases + fast-forward merges to parent branch, pushes, deletes feature branch |
| `none` | Changes left on the feature branch — user decides later |

### Conflict Commit Modes

Controls how post-rebase test fixes are committed (only applies when tests fail after rebase — if tests pass, no extra
commit is created):

| Mode                 | Behaviour                                                    |
|----------------------|--------------------------------------------------------------|
| `separate` (default) | Post-rebase fixes are a separate descriptive commit          |
| `squash`             | Post-rebase fixes are squashed into the slice commit (amend) |

### Output & Logs

Claude output is streamed in real-time:

| Destination            | Path                                      |
|------------------------|-------------------------------------------|
| Worker log             | `.ai/temp/ralph-<slice-kebab>.log`        |
| Worktree progress file | `<worktree>/.ai/temp/claude-output.md`    |
| Rebase resolution log  | `.ai/temp/ralph-rebase-<slice-kebab>.log` |

With `--stream`, output is also interleaved on the console prefixed with `[slice-name]` (workers) or
`[conflict-<slice>]` / `[rebase-<slice>]` (rebase resolution).

## Status Table

Printed on worktree start, completion, error, every 60 s heartbeat, and final summary. Uses `console.table` for aligned columns:

```
  🤖 Ralph │ 3/10 done │ 2/3 active │ 8m 32s │ finalize: merge │ parent: main
┌─────────┬────┬──────────────────────┬────────────────────────────┬────────┬──────────────────────────┬────────┐
│ (index) │    │ Slice                │ Branch                     │ Time   │ Status                   │ Tokens │
├─────────┼────┼──────────────────────┼────────────────────────────┼────────┼──────────────────────────┼────────┤
│    0    │ 🔨 │ 'Build Dwelling'     │ 'feature/build-dwelling'   │ '3m 12s' │ 'implementing'         │ ''     │
│    1    │ 📦 │ 'View Resources'     │ 'feature/view-resources'   │ '4m 30s' │ 'queued-for-finalization' │ '110k'│
│    2    │ 🔀 │ 'Start Scenario'     │ 'feature/start-scenario'   │ '4m 10s' │ 'finalizing'           │ '98k'  │
│    3    │ ✅ │ 'Recruit Creature'   │ 'feature/recruit-creature' │ '5m 45s' │ '→ merged'             │ '143k' │
└─────────┴────┴──────────────────────┴────────────────────────────┴────────┴──────────────────────────┴────────┘
  Queue: 2 │ Ready: 1 │ Finalizing: 1 │ Total tokens: 351k
```

## Signals

| Signal                                  | Context             | Meaning                                  |
|-----------------------------------------|---------------------|------------------------------------------|
| `<promise>SLICE_DONE:<id></promise>`    | Worker (parallel)   | Slice implemented, committed, and pushed |
| `<promise>SLICE_BLOCKED:<id></promise>` | Worker (parallel)   | Slice cannot be implemented              |
| `<promise>COMPLETE</promise>`           | Worker (sequential) | All slices done                          |
| `<promise>NO_TASKS</promise>`           | Worker (sequential) | No planned slices available              |
| `<promise>REBASE_RESOLVED</promise>`    | Rebase resolution   | Rebase completed + tests pass            |
| `<promise>REBASE_FAILED</promise>`      | Rebase resolution   | Rebase could not be completed            |

## Slice Statuses

| Icon | Status | Meaning |
|------|--------|---------|
| 🔨 | implementing | Claude agent is actively working on the slice |
| 📦 | queued-for-finalization | Agent done, awaiting orchestrator finalization |
| 🔀 | finalizing | Orchestrator is rebasing/merging the slice |
| ✅ | completed | Slice done and finalized |
| 🚫 | BLOCKED | Slice cannot be implemented (missing deps, unclear spec) |
| ❓ | STALLED | Claude tried to ask an interactive question — needs human attention |

## Crash Recovery

On startup with an existing registry, Ralph checks:

1. **Interrupted finalization** (`finalizingSlice` non-null):
   - Abort any in-progress rebase
   - Checkout parent branch
   - Move slice back to front of `readyQueue`
   - Clear `finalizingSlice`

2. **Dead worker processes** (each active slice's PID):
   - **Process alive** — leave it running
   - **Process dead, worktree exists** — remove worktree, re-queue slice
   - **Process dead, no worktree** — re-queue slice

3. **Ready queue items** — processed normally by the finalization pipeline

The registry file is **preserved after completion** (not deleted) — it serves as a run log and enables disaster recovery
if the process is restarted.

The `em2code-slice` skill has its own progress recovery via `.ai/temp/feature-*/progress.md`.

## Files

| File                                   | Purpose                                             |
|----------------------------------------|-----------------------------------------------------|
| `eventmodeling-loop.sh`                | Shell entry point with project defaults             |
| `.ai/ralph/ralph.mjs`                  | Orchestrator script                                 |
| `.ai/ralph/prompt.md`                  | Base prompt injected into every Claude agent        |
| `.ai/temp/ralph-registry.json`         | Active slices, ready queue, history (parallel mode) |
| `.ai/temp/ralph-state.json`            | Iteration state (sequential mode)                   |
| `.ai/temp/ralph-<slice>.log`           | Per-slice Claude worker output log                  |
| `.ai/temp/ralph-rebase-<slice>.log`    | Rebase resolution Claude output log                 |
| `<worktree>/.ai/temp/claude-output.md` | Live Claude output inside each worktree             |
| `.claude/worktrees/ralph-*/`           | Per-slice git worktrees (gitignored)                |

## Fresh Start

`--fresh` wipes all previous state before starting a clean run:
- Deletes `ralph-registry.json` and `ralph-state.json`
- Deletes all `ralph-*.log` files
- Removes all `ralph-*` worktrees
- Deletes all `feature-*/progress.md` files
