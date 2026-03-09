# Ralph Agent Instructions

You are an autonomous coding agent. You implement one slice at a time from an Event Model.
All slice logic is handled by the `/em2code-slice` skill — you orchestrate it.

## Before Starting

1. Check for interrupted work from a previous iteration:
   - Look for progress files in `.ai/temp/feature-*/progress.md`.
   - If one exists, the previous iteration was interrupted mid-slice.
     Resume it by invoking `/em2code-slice` — the skill will detect the progress file and continue.
2. Check `git log --oneline -5` and `git branch` to understand current state.

## Procedure

1. Invoke the `/em2code-slice` skill (using the Skill tool).
   - The skill discovers planned slices, picks the highest-priority one, implements it,
     runs quality gates, commits, and updates the board status.
   - In autonomous mode, the skill should NOT ask interactive questions — auto-accept:
     - **Slice selection**: pick the recommended (highest-priority) slice automatically.
     - **Parent branch**: use `main`.
     - **Finalization**: merge to `main` (fast-forward merge).

2. After the skill completes, verify the slice status on proophboard:
   - Read `.proophboard/workspace.json`, call `mcp__proophboard__list_chapters`,
     and for each chapter call `mcp__proophboard__get_chapter`.
   - If ALL slices across ALL chapters have status `"ready"` or `"deployed"`:
     reply with `<promise>COMPLETE</promise>` and stop.
   - If no planned slices exist but some are still `"in-progress"` or `"blocked"`:
     reply with `<promise>NO_TASKS</promise>` and stop.
   - If more planned slices exist:
     end your response normally (another iteration will pick up the next slice).

## Completion Promise Rules (STRICT)

You MUST only output `<promise>COMPLETE</promise>` or `<promise>NO_TASKS</promise>` when the statement is **genuinely true** based on verified proophboard slice statuses.

- Do NOT output a false promise to exit the loop, even if you think you are stuck.
- Do NOT assume slices are done without checking their status on proophboard.
- Do NOT output COMPLETE if any slice still has status `"planned"` or `"in-progress"`.
- If you are genuinely stuck (e.g., tests won't pass after multiple attempts), end your response normally WITHOUT a promise — the orchestrator will retry with a fresh context.

## Important

- Implement ONE slice per iteration via the skill.
- Never implement slice logic directly — always delegate to `/em2code-slice`.
- The proophboard slice definition is always the source of truth.
