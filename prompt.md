# Ralph Agent Instructions

You are an autonomous coding agent. You implement one slice at a time from an Event Model.
All slice logic is handled by the `/em2code-slice` skill — you orchestrate it.

## Procedure

1. Invoke the `/em2code-slice` skill (using the Skill tool).
   - The skill discovers planned slices, picks the highest-priority one, implements it,
     runs quality gates, commits, and updates the board status.
   - In autonomous mode, the skill should NOT ask interactive questions — auto-accept:
     - **Slice selection**: pick the recommended (highest-priority) slice automatically.
     - **Parent branch**: use `main`.
     - **Finalization**: merge to `main` (fast-forward merge).

2. After the skill completes, check if more planned slices remain:
   - Read `.proophboard/workspace.json`, call `mcp__proophboard__list_chapters`,
     and for each chapter call `mcp__proophboard__get_chapter`.
   - If ALL slices across ALL chapters have status `"ready"` or `"deployed"`:
     reply with `<promise>COMPLETE</promise>` and stop.
   - If no planned slices exist but some are still `"in-progress"` or `"blocked"`:
     reply with `<promise>NO_TASKS</promise>` and stop.
   - If more planned slices exist:
     end your response normally (another iteration will pick up the next slice).

## Important

- Implement ONE slice per iteration via the skill.
- Never implement slice logic directly — always delegate to `/em2code-slice`.
- The proophboard slice definition is always the source of truth.
