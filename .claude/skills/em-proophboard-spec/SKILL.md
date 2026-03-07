---
name: em-proophboard-spec
description: "Work with proophboard Event Modeling boards via MCP. Read, create, and modify event models — chapters, lanes, slices, and elements (commands, events, information, automations, UI, hotspots). Use when: (1) user asks to read, explore, or understand an event model on proophboard, (2) user asks to create or modify chapters, slices, or elements on the board, (3) user asks to model a feature, flow, or bounded context using Event Modeling, (4) user says 'proophboard', 'event model', 'board', 'add slice to board', 'model this on proophboard', 'update the board', (5) translating between code and event model (syncing implementation status with board slices)."
---

# proophboard Event Modeling

Work with proophboard boards via MCP to read, create, and modify event models.

## Before You Start

### 1. Resolve Workspace ID
Read `.proophboard/workspace.json` from the repository root.
- **If the file exists**: use the `workspace_id` from it.
- **If the file does not exist**:
  1. Call `mcp__proophboard__list_workspaces` to list all available workspaces
  2. Ask the user which workspace to use via `AskUserQuestion` (present workspace names as options)
  3. Write the selected workspace to `.proophboard/workspace.json`:
     ```json
     {
       "workspace_id": "<selected-id>",
       "workspace_name": "<selected-name>"
     }
     ```

### 2. Load Chapter Context
1. Call `mcp__proophboard__list_chapters` to discover available chapters
2. Call `mcp__proophboard__get_chapter` to load full chapter structure (lanes, slices, elements)

Always `get_chapter` before any modifications — all update operations require current `old_*` values.

For Event Modeling conventions and rules, read [references/guidelines.md](references/guidelines.md).
For MCP API parameters, use ToolSearch to discover `mcp__proophboard__*` tools on demand.

## Core Concepts

### Board Structure
```
Chapter (a user journey / business process path)
├── Lanes (horizontal rows)
│   ├── user-lane     → UI, Automation, HotSpot
│   ├── information-flow → Command, Information, HotSpot
│   └── system        → Event, HotSpot
└── Slices (vertical columns = steps)
    ├── Write slice   → exactly 1 Command (no Information)
    └── Read slice    → 1+ Information (no Commands)
```

### Element Placement Rules (STRICT)
| Element | `type` value | Allowed lane type |
|---|---|---|
| Command | `command` | `information-flow` |
| Event | `event` | `system` |
| Information | `information` | `information-flow` |
| Automation | `automation` | `user-lane` |
| UI | `ui` | `user-lane` |
| Hot Spot | `hotspot` | any |

**Never place commands and information in the same slice.**

### Business Rules Placement (STRICT)
- **Do NOT use HotSpots for business rules.** HotSpots are for open questions, bottlenecks, and unresolved issues only.
- Business rules, validation constraints, and invariants belong in the **command's `details` field** alongside Example and JSON Schema.

### Cross-Chapter Triggers (STRICT)
- When a write slice is triggered by events from **another chapter** (e.g., an automation in Creature Recruitment dispatches a command to Armies), do **NOT** add an Automation or UI element on the write slice in this chapter.
- The triggering context is modeled in the **other** chapter, not this one. This chapter only shows the command, event, and resulting state.

### Write-Read Slice Ordering (STRICT)
- Every write slice should be followed by a read slice so that events auto-connect to information via orange arrows.
- Standard chapter pattern: **Write → Read → Write → Read → ...**
- This ensures every event visibly feeds a read model on the board.

### Shared UI Naming
- When a UI element serves as both a read view and a trigger for a subsequent write slice, use the **same name** on both slices (e.g., "Army Creatures" on the read slice UI and the next write slice UI) to indicate they represent the same screen.

### Automatic Arrows (no manual wiring needed)
Connections are drawn automatically when elements are placed correctly:
- UI/Automation → Command (same slice)
- Command → Event (same slice)
- Event → Information (next slice)
- Information → UI/Automation (same slice)

## Workflows

### Read an Event Model
1. Resolve workspace (see "Before You Start")
2. `list_chapters` → pick chapter
3. `get_chapter` → full structure with all lanes, slices, elements
4. Present to user organized by slices (steps), showing the flow

### Plan and Model a New Chapter

Before creating anything, **plan the full chapter structure**:

1. **Count the slices** you'll need and prepare them upfront
2. **Remember the rules**: command and information must NOT be in the same slice
3. **Prepare the lanes**:
   - Who participates in the chapter? (user roles → `user-lane`)
   - Which system contexts are involved? (→ `system` lanes)
4. **Determine the chapter's starting point**:
   - Is the outcome of another chapter the starting point? (event from another chapter)
   - Or is it started by a user role triggering a command in a UI?
5. **Identify cross-chapter dependencies**: Do we need information from another chapter for validation/business rules?
6. **Map the information flow** for the entire chapter before touching the board
7. **Validate the information flow** against the guidelines strictly
8. **Check if the chapter already exists**:
   - Yes → model on the existing chapter
   - No → `add_chapter` and model on the new one

#### Creating the Chapter
1. `add_chapter` with name and mode (`event-modeling`)
2. This creates default lanes (user-lane, information-flow, system) and initial slices
3. Rename lanes to match actual user roles and system contexts
4. Add/rename/remove slices for each step in the flow (Write → Read → Write → Read pattern)
5. **Verify ordering**: call `get_chapter` after adding slices — new slices may not insert at the expected index. Use `reorder_slices` to fix ordering **before** placing any elements.

### Add a Write Slice (Command → Event)
1. `get_chapter` to find lane IDs
2. `add_slice` with descriptive label at the right index
3. `add_element` — `automation` or `ui` in the `user-lane`
4. `add_element` — `command` in the `information-flow` lane, same slice
5. `add_element` — `event` in the `system` lane, same slice

### Add a Read Slice (Event → Information)
1. `add_slice` with descriptive label at the right index (after the write slice whose events feed it)
2. `add_element` — `information` in the `information-flow` lane
3. `add_element` — `ui` or `automation` in the `user-lane` (what consumes this info)

### Add an Automation (Event → Command)
Two patterns:
- **Direct reaction**: Place `automation` in user-lane of the write slice that it triggers
- **Task list**: Add a read slice with `information` (task list), then `automation` in user-lane of next write slice

### Updating Existing Models

When modifying an existing model, follow these rules strictly:

1. **Favor REORDERING over deletion and recreation** — use `reorder_slices` and `reorder_lanes` tools
2. **Don't rush** — plan ahead what you want to achieve so you don't mess up the model
3. **Always prepare the slice and lane structure first**
4. **Check that chapter details look correct after preparation** (`get_chapter`)
5. **Only when the basic slice ordering is correct**, start placing elements in slices

### Adding Details to Elements

After the flow structure is modeled and validated, enrich elements with details:

1. **Element description with properties** (optional): Add property names with example values or types directly in the element description via `update_element_description`. This makes properties visible on the board card itself. **Ask the user** whether to add description properties — it's not required.
2. **Element details**: Add detailed descriptions, JSON examples, JSON Schema, validation rules, and business rules via `update_element_details` / `update_element_description`
3. **UI mockups**: Add ASCII mockups to the `details` field of UI elements
4. **Slice details**: Add business rules and Given-When-Then scenarios to slice details via `update_slice_details` (see [Slice GWT Scenarios](#slice-gwt-scenarios) below)

#### Element Description Properties (Optional)

Element descriptions can list properties with example values or types. These are shown directly on the board card, making the data shape visible at a glance. Use `update_element_description` to set this.

**Format**: each property on its own line. End each line with **two trailing spaces** (`  `) to force a line break in markdown.

Example for a "Build Dwelling" command:
```
dwellingId: uuid
creatureId: Angel
costPerTroop: {gold: 3000, gems: 1}
```

Example for a "Dwelling Built" event:
```
dwellingId: uuid
creatureId: Angel
costPerTroop: {gold: 3000, gems: 1}
```

**Guidelines:**
- Use domain-meaningful example values (e.g., `Angel` not `string`, `{gold: 3000, gems: 1}` not `object`)
- Use `uuid` as a type hint for identifiers
- This is **optional** — always ask the user if they want properties added to element descriptions
- Keep it concise — this is a summary, not a full schema (use `details` for that)

#### Slice GWT Scenarios (Optional)

Slice details (set via `update_slice_details`) can contain Given-When-Then scenarios — executable acceptance criteria that map directly to test methods. This is **optional** — ask the user whether to add GWT scenarios when documenting slices.

Use [references/SLICE_DOCUMENTATION.md](references/SLICE_DOCUMENTATION.md) as the starting template when a slice has no documentation.

**Element block syntax** inside GWT scenarios:
```
:::element <type>
<Element Name>··
<optional key: value properties — only rule-relevant>··
:::
```
Where `<type>` is: `command`, `event`, `information`, `hotspot`, `automation`.

**CRITICAL — Trailing double spaces**: End **every line** inside `:::element` blocks with **two trailing spaces** to force markdown line breaks. This includes the element name, each property line, and description lines. Without trailing spaces, all lines render on a single line on proophboard. This is the **most common mistake** — always double-check before submitting to proophboard.

In the examples below, `··` at end of line marks where two trailing spaces MUST be added. The actual content sent to proophboard must use real spaces, not the `··` marker.

**Key principle**: Only include properties that are **relevant to the business rule** being tested. E.g., BuildDwelling idempotency scenario only shows `dwellingId` (the uniqueness key) — `creatureId` and `costPerTroop` are omitted because they don't influence the rule.

##### Idempotency Consideration

When documenting a write slice, consider whether the command should be **idempotent** (repeating the same command produces no additional events). Ask the user about idempotency behavior for failure cases — e.g., should "remove something not present" be a silent no-op (NOTHING) or an exception? Common idempotent pattern: if the desired end state is already achieved, return Success with no events.

##### GWT Patterns by Slice Type

**Write slice** — `Given (events) → When (command) → Then (events | hotspot | NOTHING)`:

```markdown
## Business Rules

<!-- WILL BE DERIVED FROM GWT Scenarios -->
- Dwelling can only be built once (idempotent)

## Scenarios (GWTs)

### 1. build for the first time

**Given**
NOTHING
**When**
:::element command
Build Dwelling··
:::
**Then**
:::element event
Dwelling Built··
:::

### 2. try to build already built

**Given**
:::element event
Dwelling Built··
dwellingId: portal-of-glory··
:::
**When**
:::element command
Build Dwelling··
dwellingId: portal-of-glory··
:::
**Then**
NOTHING
```

**Read slice** — `Given (events) → Then (information)` — no When block:

When a read slice returns **multiple items**, show each as a **separate `:::element information` block** in the Then section. Never wrap results in an `items` array property.

```markdown
## Scenarios (GWTs)

### 1. given creatures added, then show army

**Given**
:::element event
Creature Added To Army··
creatureId: Angel··
quantity: 5··
:::
**Then**
:::element information
Army Creatures··
creatureId: Angel··
quantity: 5··
:::

### 2. given two dwellings built, then both returned

**Given**
:::element event
Dwelling Built··
dwellingId: portal-of-glory··
:::
:::element event
Dwelling Built··
dwellingId: cursed-temple··
:::
**Then**
:::element information
Dwelling··
dwellingId: portal-of-glory··
:::
:::element information
Dwelling··
dwellingId: cursed-temple··
:::
```

**Automation** — `Given (events) → Then (command | hotspot | NOTHING)` — no When block:

```markdown
## Scenarios (GWTs)

### 1. when week symbol proclaimed, increase available creatures

**Given**
:::element event
Week Symbol Proclaimed··
:::
**Then**
:::element command
Increase Available Creatures··
:::
```

##### Failure / Rejection in Then

Two forms:
- **Hotspot** — exception or broken business rule:
  ```
  :::element hotspot
  Exception
  Can have max 7 different creature stacks in the army
  :::
  ```
- **Failure event** — an explicit domain event representing a rejected outcome (e.g., `PaymentRejected`). This is a valid event, not an exception — model it as `:::element event`.

##### NOTHING Keyword

Use `NOTHING` for:
- **Empty Given** — no prior events (fresh state)
- **Empty Then** — no events produced (idempotent write) or no command dispatched (automation doesn't react)

**Do NOT use `Given NOTHING → Then NOTHING`** for read slices. An empty-state-produces-empty-result scenario is not meaningful — it conveys no business rule. Only include read slice scenarios where events produce observable read model state.

##### Implementation Guidelines

Slice documentation may include a `## Implementation Guidelines` section with Backend and/or Frontend subsections. These contain specific technical requirements for the slice (e.g., integrate with a payment provider, create an S3 bucket, use a specific library). When present, these guidelines **must be followed** during implementation — they take priority over generic patterns.

#### Element Details Format

The `details` field supports markdown and has no strict structure — it can contain whatever is useful for the element. Common content for commands and events includes:

- **Example** — a concrete JSON instance showing what the message looks like
- **JSON Schema** — formal schema describing the message structure
- **Business rules** — validation constraints, invariants
- **Description** — free-text explanation of purpose or behavior
- Any other relevant documentation

Example of details for a command element:

````markdown
## Example

```json
{
  "dwellingId": "portal-of-glory",
  "creatureId": "angel",
  "costPerTroop": { "GOLD": 3000, "GEMS": 1 }
}
```

## JSON Schema

```json
{
  "type": "object",
  "properties": {
    "dwellingId": {
      "type": "string",
      "minLength": 1,
      "description": "Unique identifier for the dwelling"
    },
    "creatureId": {
      "type": "string",
      "minLength": 1,
      "description": "Identifier of the creature type"
    },
    "costPerTroop": {
      "type": "object",
      "description": "Resource cost per single troop",
      "additionalProperties": {
        "type": "integer",
        "minimum": 0
      }
    }
  },
  "required": ["dwellingId", "creatureId", "costPerTroop"]
}
```
````

**Guidelines for element details:**
- Event modeling typically happens **before code exists** — propose domain-meaningful example values and ask the user to confirm or adjust before writing
- In the less common case where code/tests already exist, derive example values from them for consistency. Read value objects and event classes to understand the full data shape — unwrap `value class` / `@JvmInline` wrappers to their raw type, and flatten nested data classes (e.g., `MonthWeek(month, week)` becomes separate `month` and `week` fields in JSON)
- Use meaningful IDs (e.g., `"portal-of-glory"` not `"uuid-123"`)
- JSON Schema should match the intended data structure (value classes unwrap to their raw type)
- When updating details, always pass the **complete new content** to `update_element_details` — partial string replacements cause conflicts on proophboard

### Track Implementation Progress

Update slice status to reflect code implementation state:
```
planned → in-progress → ready → deployed
```
Use `update_slice_status` with both `old_status` and `new_status`.

Derive implementation tasks from the event model — **one task per slice**. Update progress on proophboard using slice status as implementation proceeds.

### Monitor Board Changes

Use `list_changelog_events` to see recent updates made to the board by any user.

### Search Across the Board
Use `search_elements` to find elements by name or type across all chapters:
- Find all commands: `element_type: "command"`
- Find by name: `query: "Recruit"`
- Combine filters: `query: "Dwelling", element_type: "event"`

## Naming Conventions
- **Commands**: imperative — "Build Dwelling", "Recruit Creature"
- **Events**: past tense — "Dwelling Built", "Creature Recruited"
- **Information**: nouns — "Dwelling Details", "Available Creatures"

## References
- **[references/guidelines.md](references/guidelines.md)** — Full Event Modeling guidelines from proophboard (element colors, flow patterns, best practices, freestyle mode)
- **MCP tools** — Use ToolSearch with `+proophboard` to discover tools and their parameters on demand
