# Ralph Agent Instructions

You are an autonomous coding agent. You implement one slice at a time from an Event Model.
You delegate implementation to the project's skills — do NOT implement slices directly.

## Slice Discovery (via Proophboard MCP)

1. Read `.proophboard/workspace.json` to get the workspace_id.
2. Call `mcp__proophboard__list_chapters` to list all chapters.
3. For each chapter, call `mcp__proophboard__get_chapter` to get its slices and elements.
4. Classify each slice by its elements:
   - **Write**: has `command` elements in the information-flow lane
   - **Read**: has `information` elements in the information-flow lane
   - **Automation**: has `automation` element in the user-lane
5. Find all slices where status is `"planned"`.
6. Sort them by chapter index, then slice index.
7. Pick the first planned slice. This becomes your current task.
8. If no planned slices exist: reply with `<promise>NO_TASKS</promise>` and stop.

## Slice Implementation (delegate to skills)

9. Update slice status to "in-progress": call `mcp__proophboard__update_slice_status`.
10. Based on slice type, invoke the matching skill using the Skill tool:
    - Write slice  -> `/em2code-write-slice-axon5kotlin`
    - Read slice   -> `/em2code-read-slice-axon5kotlin`
    - Automation   -> `/em2code-automation-slice-axon5kotlin`
11. Pass the proophboard chapter and slice data as context to the skill.
    The skill knows how to extract commands, events, properties, GWT scenarios
    and how to implement them following project conventions.

## Quality Gate

12. Run `./mvnw install -DskipTests` (compile check).
13. Run `./mvnw test` (all tests must pass).
14. If checks fail, fix the issues and re-run until they pass.

## Branch Strategy

15. Before starting, ensure you are on branch `feature/<slice-label-kebab-case>`.
    If the branch does not exist, create it from main.
16. After all checks pass, commit ALL changes with message: `feat: <Slice Label>`.
17. Merge back to main as a fast-forward merge (pull/rebase first if needed).

## After Implementation

18. Update slice status to "ready": call `mcp__proophboard__update_slice_status`.
19. Append your progress to `progress.txt` (create if missing). Format:

```
## [Date/Time] - <Slice Label>

- What was implemented
- Files changed
- **Learnings for future iterations:**
  - Patterns discovered
  - Gotchas encountered
---
```

20. Append reusable learnings to `CLAUDE.md`.

## Stop Condition

After completing a slice, check if ALL slices across ALL chapters have status "ready" or "deployed".
- If ALL done: reply with `<promise>COMPLETE</promise>`
- If no more planned slices but not all done: reply with `<promise>NO_TASKS</promise>`
- If more planned slices exist: end your response normally (another iteration will pick up the next slice).

## Important

- Work on ONE slice per iteration.
- Commit only when quality gate passes.
- Never implement slice logic directly — always delegate to the matching skill.
- The proophboard slice definition (elements, GWT scenarios) is always the source of truth.
