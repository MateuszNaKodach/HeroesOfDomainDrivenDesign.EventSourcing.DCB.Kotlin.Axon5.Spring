---
name: em-proophboard-status
description: "Show implementation status of Event Modeling slices from proophboard. For each chapter, display a table mapping board slices (write/read/automation) to their codebase implementation and tests. Read-only — no board or code modifications. Use when: (1) user asks about implementation status, progress, or coverage of event model slices, (2) user says 'status', 'overview', 'what is implemented', 'what is missing' in context of proophboard or event model, (3) user wants to see which slices have code and tests, (4) user says 'em-proophboard-status' or '/em-proophboard-status'."
---

# proophboard Implementation Status

Show a per-chapter table mapping proophboard Event Modeling slices to codebase implementations and tests.

**Read-only** — do not modify the board or any code.

## Procedure

### 1. Resolve Workspace

Read `.proophboard/workspace.json` from the repository root to get `workspace_id`.
If the file does not exist, stop and tell the user to configure it first (or invoke the `em-proophboard-spec` skill).

### 2. Select Chapters

Call `mcp__proophboard__list_chapters` with the `workspace_id`.

- If the user specified a chapter name → match it from the list.
- If not specified → use `AskUserQuestion` with chapter names as options plus an **"All chapters"** option. Allow single select.

### 3. Understand Project Conventions

Before searching for implementations, learn how the project maps slices to code:

1. **Read the project `CLAUDE.md`** — look for file naming patterns, directory structure, bounded context locations, and slice file conventions.
2. **Explore the codebase** — use `Glob` to discover slice files (e.g., `**/*.Slice.kt`, `**/*Slice*.kt`, `**/*Handler*`, `**/*Projection*`) and test files. Look at the directory tree to understand how write/read/automation slices are organized.
3. **If conventions are still unclear** — ask the user how slices map to code files.

Build a mental model of: where slice implementations live, how they are named, and where tests are located.

### 4. Fetch and Classify Slices

For each selected chapter, call `mcp__proophboard__get_chapter`.

Classify each slice by the element types it contains:

| Slice type | Element pattern |
|---|---|
| **Write** | Has `command` element(s) in `information-flow` lane, no `information` elements |
| **Read** | Has `information` element(s) in `information-flow` lane, no `command` elements |
| **Automation** | Has `automation` element in `user-lane` AND has `command` or `information` elements (connects events to commands) |

Determine the **bounded context** for each slice from the lane that holds its key element:
- **Write**: the `system` lane containing the command's events → lane label = context
- **Read**: the `information-flow` lane is shared, so derive context from the system lane whose events feed this read slice
- **Automation**: the `system` lane containing the automation's triggering event → lane label = context

If the chapter has only one `system` lane, all slices share that context.

**Element similarity:** In proophboard, elements with the same `name`, `type`, and `context` are considered **similar** — they represent the same concept even if they appear in different slices or chapters. The board uses copies to keep processes visually clean, but similar elements share documentation and identity. When multiple slices share a similar `information` element but have different input events, they are **distinct slices that project into the same read model** (see Step 7).

Extract the message flow from elements:
- **Write**: `CommandName` → `EventName(s)`
- **Read**: (`EventName(s)`) → `InformationName`
- **Automation**: `EventName` → `CommandName`

For events feeding a read slice, look at the *preceding* write slice(s) in the chapter — the events produced there flow into the read slice.

### 5. Search for Implementations

For each slice, search the codebase using the conventions learned in step 3:

1. **Slice implementation file** — Glob for files matching the command/query/automation name using project naming patterns.
2. **Test files** — Glob for test files (e.g., `*Test.kt`, `*Spec.kt`, `*IT.kt`) matching the slice name.
3. **Cross-bounded-context slices** — Some slices (e.g., automation triggering a command in another context) may span multiple bounded contexts. Search across the full source tree.

Mark each slice as:
- **Implementation found** → show relative file path
- **NOT IMPLEMENTED** → show clearly

Mark tests as:
- **Tests found** → show relative file path(s)
- **No tests** → show clearly

### 6. Output Table

For each chapter, output a markdown table:

```
## Chapter Name

| # | Type | Context | Slice Name | Messages | Board Status | Code Status | Implementation | Tests |
|---|------|---------|-----------|----------|--------------|-------------|----------------|-------|
| 0 | Write | Creature Recruitment | Build Dwelling | `BuildDwelling` → `DwellingBuilt` | planned | Implemented | `path/to/file.kt` | `path/to/test.kt` |
| 1 | Read | Creature Recruitment | View Dwelling | (`DwellingBuilt`) → `Dwelling` | planned | Implemented | `path/to/file.kt` | No tests |
| 2 | Automation | Armies | ... | `EventName` → `CommandName` | planned | NOT IMPLEMENTED | — | — |
```

Column details:
- **#**: Slice index in chapter order
- **Type**: Write / Read / Automation
- **Context**: Bounded context — derived from the `system` lane label where the slice's events live
- **Slice Name**: From the board slice label
- **Messages**: The command/event/information flow using backtick formatting
- **Board Status**: The slice `status` field from proophboard (planned / in-progress / ready / deployed)
- **Code Status**: Derived from codebase search — "Implemented" if code exists, "NOT IMPLEMENTED" if not. Flag mismatches: if code exists but board status is still `planned`, highlight that the board status should be at least `in-progress` or `ready`
- **Implementation**: Relative path to slice file, or "—"
- **Tests**: Relative path(s) to test file(s), or "No tests", or "—" if not implemented

After the table, add a brief **Summary** listing counts: how many slices implemented, how many with tests, how many missing.

### 7. Identify Shared Projections

Multiple read slices can project into the **same read model** — e.g., two slices both named "View Current Day" with different input events (`DayStarted` vs `DayFinished`) but the same `information` element (same name+type+context).

These are **distinct slices** (different event inputs = different event handlers), NOT duplicates. Show each as its own row in the table. However, they share a single projection implementation file.

When shared projections are detected:
1. **Keep every slice as a separate row** — preserve the board's structure and slice ordering.
2. **Mark the relationship** — in the Code Status column of subsequent slices, show `= shared with #N` referencing the first slice's row number. This indicates they belong to the same implementation file.
3. **Implementation guidance** — when these slices are implemented, they must be built together in the same file (same projection class handles all input events). They should NOT be implemented in parallel as separate tasks.

Example output:

```
| 1 | Read | Calendar | View Current Day | (`DayStarted`) → `Current Day`  | planned | NOT IMPLEMENTED |
| 3 | Read | Calendar | View Current Day | (`DayFinished`) → `Current Day` | planned | = shared with #1 |
```
