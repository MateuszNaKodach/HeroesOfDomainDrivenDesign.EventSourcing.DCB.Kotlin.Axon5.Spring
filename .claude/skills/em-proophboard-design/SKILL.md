---
name: em-proophboard-design
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

### Create a New Chapter
1. `add_chapter` with name and mode (`event-modeling`)
2. This creates default lanes (user-lane, information-flow, system) and initial slices
3. Rename lanes to match actual user roles and system contexts
4. Add slices for each step in the flow

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

### Track Implementation Progress
Update slice status to reflect code implementation state:
```
planned → in-progress → ready → deployed
```
Use `update_slice_status` with both `old_status` and `new_status`.

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
