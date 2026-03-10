---
name: em2code-slice
description: >
  Implement a single Event Modeling slice from proophboard — discover slices via MCP, let the user
  choose which one, delegate to the matching implementation skill (write/read/automation), run quality
  gates, handle git branching, and ask the user how to finalize (merge or PR).
  Use when: (1) user says "implement slice", "implement next slice", "/em2code-slice",
  (2) user wants to pick and implement a slice from the event model board,
  (3) user provides a slice ID or name and asks to implement it,
  (4) user says "what slices are planned" and wants to implement one.
---

# Implement Slice from Event Model

Implement one slice at a time from a proophboard Event Model. Delegate to project skills — never implement slice logic directly.

## Context Scope

Do NOT read the entire codebase. The slice definition from proophboard is your complete context. Focus only on:
- The proophboard slice data (elements, GWT scenarios, details)
- Existing files in the same bounded context (only when the delegated skill needs them)

The slice is a self-contained unit of work — trust that and stay focused.

## Step 1: Identify the Target Slice

### If slice was specified in arguments
- Parse the slice ID, or chapter+slice name from the arguments.
- Read `.proophboard/workspace.json` to get `workspace_id`.
- Call `mcp__proophboard__list_chapters`, then `mcp__proophboard__get_chapter` to find the matching slice.

### If no slice was specified
1. Read `.proophboard/workspace.json` to get `workspace_id`.
2. Call `mcp__proophboard__list_chapters` to list all chapters.
3. For each chapter, call `mcp__proophboard__get_chapter` to get slices and elements.
4. Classify each slice:
   - **Write**: has `command` elements in the `information-flow` lane
   - **Read**: has `information` elements in the `information-flow` lane
   - **Automation**: has `automation` element in the `user-lane`
5. Find all slices with status `"planned"`.
6. Sort by chapter index, then slice index.
7. If no planned slices exist, inform the user and stop.
8. **Recommend the highest-priority slice.** Priority reasoning:
   - Write slices that unblock other slices (automations, read models) rank highest
   - Write slices that produce events consumed by many downstream slices rank higher
   - Automations and read slices that depend on already-implemented events rank next
   - Within equal priority, prefer lower chapter+slice index (natural order)
9. Present planned slices to the user via `AskUserQuestion`:
   - Show each slice with: chapter name, slice label, type (Write/Read/Automation), index
   - Explain WHY you recommend the top pick (e.g., "this write slice produces events needed by 3 downstream slices")
   - Let the user pick one (or confirm the recommendation)

## Step 2: Check for Shared Implementation

Before implementing, check if other planned slices share the same read model or projection as the selected one (e.g., two Read slices reacting to different events but projecting into the same view). If so, inform the user and suggest grouping them. Implement grouped slices together as a single unit.

## Progress Tracking (Session Recovery)

Write a progress file to `.ai/temp/<feature-branch-name>/progress.md` so a new session can resume if interrupted. Create it after Step 3 (when slice and parent branch are known), update it as steps complete.

Format:
```markdown
# Slice: <Slice Label>
- **Type**: Write | Read | Automation
- **Chapter**: <chapter name>
- **Slice ID**: <proophboard slice ID>
- **Parent branch**: <parent-branch>
- **Feature branch**: feature/<slice-label-kebab-case>
- **Current step**: <step number and name>
- **Status**: in-progress | quality-gate | committing | finalizing
```

**On resume**: If `.ai/temp/feature-<name>/progress.md` exists for the current branch, read it and continue from the recorded step instead of starting over. Ask the user to confirm before resuming.

**Cleanup**: Delete the progress file (and its directory) after successful finalization in Step 7.

## Step 3: Determine Parent Branch

1. Check the current git branch.
2. If NOT on `main`, ask the user via `AskUserQuestion`:
   > You're currently on branch `<current-branch>`. Should I:
   > - **Switch to main** — use `main` as the parent branch (standard flow)
   > - **Stay on `<current-branch>`** — use this branch as the parent instead
3. Remember the chosen **parent branch** — the feature branch is created from it, and merges/PRs target it in Step 7.

## Step 4: Implement

1. Update slice status to `"in-progress"`: call `mcp__proophboard__update_slice_status`.
2. Create git branch `feature/<slice-label-kebab-case>` from the **parent branch** (if it doesn't exist). Switch to it.
3. Based on slice type, invoke the matching skill using the `Skill` tool:
   - Write  -> `em2code-write-slice-axon5kotlin`
   - Read   -> `em2code-read-slice-axon5kotlin`
   - Automation -> `em2code-automation-slice-axon5kotlin`
4. Pass the proophboard chapter and slice data as context to the skill.
   If multiple slices are grouped, pass all of them.

## Step 5: Quality Gate

5. Run `./mvnw install -DskipTests` (compile check).
6. Run `./mvnw test` (all tests must pass).
7. If checks fail, fix issues and re-run until they pass.

## Step 6: Commit

8. Invoke the `/commit` skill. The commit type MUST be `feat` and the scope should reflect the slice.

## Step 7: Finalize

9. Update slice status to `"ready"` for ALL implemented slices: call `mcp__proophboard__update_slice_status` for each.
10. **Update CLAUDE.md with learnings** — before committing, check if any edited files revealed knowledge worth preserving:
    - Patterns or conventions specific to that bounded context or module
    - Gotchas or non-obvious requirements discovered during implementation
    - Dependencies between files or slices that aren't obvious from the board
    - Testing approaches, fixture patterns, or configuration needed for that area
    - Integration details (MCP tools, Axon config, Spring beans, feature flags)
    - Only add genuinely new and useful learnings — don't duplicate what's already there
11. Ask the user via `AskUserQuestion` how to finalize (target = **parent branch** from Step 3):
    - **Merge to `<base-branch>`** — fast-forward merge (pull/rebase first if needed), then delete the feature branch
    - **Open a pull request** — push the branch and create a PR targeting `<base-branch>` via `gh pr create`
    - **Leave on branch** — do nothing further, leave changes on the feature branch

## Conflict Resolution (Rebase)

When merging or rebasing onto the parent branch, conflicts may occur — especially when multiple slices were implemented in parallel. The most common conflicts are:

- **Event/command classes** — two slices introduced the same event or command (e.g., both need `DwellingBuilt`). One branch created the file, the other also created it. Resolution: keep the version that matches the proophboard event definition (source of truth). If both are identical, just accept either.
- **Sealed interface files** — e.g., `DwellingEvent.kt` got a new event added by both branches. Resolution: merge both additions — the sealed interface should list all events from both slices.
- **Exhaustive `when` blocks** — after merging new events into a sealed interface, any `when` expression over that interface (e.g., in `evolve()`) will fail to compile because the new event type from the other branch isn't handled. Resolution: add the missing branch to the `when`. Check `evolve()` functions and any other exhaustive matches in the same bounded context.
- **EventTags.kt** — both slices added a new tag constant. Resolution: keep both constants.
- **Feature flag configs** (`application.yaml`, `application-test.yaml`, `additional-spring-configuration-metadata.json`) — both slices added entries. Resolution: keep all entries from both sides.

**Resolution principle**: The proophboard Event Model is the source of truth. When in doubt about property names, types, or structure — check the slice definition on the board, not the conflicting code.

**Rebase conflict workflow**: Resolve conflicts using `git add <resolved-files> && git rebase --continue`. Do NOT create additional merge commits — conflict resolution must be part of the rebased commit itself.

**After resolving conflicts**: Re-run the quality gate (compile + tests) before finalizing.

**Clean state before finalization**: After merging to the parent branch, verify `git status` is clean. If any uncommitted or untracked files remain (e.g., from conflict resolution artifacts), stage them and use `/commit` to create a proper commit before signaling completion.

## Important

- Work on ONE slice per invocation, unless multiple slices share the same implementation (e.g., shared read model) — then group and implement them together.
- Commit only when quality gate passes.
- Never implement slice logic directly — always delegate to the matching skill.
- The proophboard slice definition (elements, GWT scenarios) is always the source of truth.
