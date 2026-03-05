# proophboard MCP API Reference

All tools are prefixed with `mcp__proophboard__`. Every mutating tool requires `workspace_id`.

## Table of Contents

- [Workspace](#workspace)
- [Chapters](#chapters)
- [Lanes](#lanes)
- [Slices](#slices)
- [Elements](#elements)
- [Search](#search)
- [Changelog](#changelog)
- [Playshots](#playshots)
- [Guidelines](#guidelines)

---

## Workspace

### `list_workspaces`
List all workspaces. Returns: id, name, role, created_at.
No parameters.

---

## Chapters

### `list_chapters`
List all chapters in a workspace. Returns: id, name, index, context, mode.
- `workspace_id` (string, required)

### `get_chapter`
Get chapter with all lanes, slices, and elements.
- `workspace_id` (string, required)
- `chapter_id` (string, required)

### `add_chapter`
Add new chapter with default lanes and slices.
- `workspace_id` (string, required)
- `name` (string, required)
- `mode` (string, optional) — `event-modeling` (default) or `freestyle`
- `context` (string, optional) — default: `App`

### `rename_chapter`
- `workspace_id`, `chapter_id`, `old_name`, `new_name` (all string, required)

### `remove_chapter`
Removes chapter and all its elements.
- `workspace_id`, `chapter_id` (string, required)

---

## Lanes

### `add_lane`
- `workspace_id`, `chapter_id` (string, required)
- `label` (string, required)
- `type` (string, required) — `user-lane`, `information-flow`, or `system`
- `index` (number, required) — position
- `height` (number, optional) — pixels, default 150

### `rename_lane`
- `workspace_id`, `chapter_id`, `lane_id` (string, required)
- `old_label`, `new_label` (string, required)

### `change_lane_icon`
- `workspace_id`, `chapter_id`, `lane_id` (string, required)
- `old_icon`, `new_icon` (string, required) — lucide-react icon names

### `resize_lane`
- `workspace_id`, `chapter_id`, `lane_id` (string, required)
- `old_height`, `new_height` (number, required)

### `reorder_lanes`
- `workspace_id`, `chapter_id` (string, required)
- `old_lane_ids`, `new_lane_ids` (string[], required)

### `remove_lane`
Removes lane and all elements in it.
- `workspace_id`, `chapter_id`, `lane_id` (string, required)

---

## Slices

### `add_slice`
- `workspace_id`, `chapter_id` (string, required)
- `label` (string, required)
- `index` (number, required) — position
- `status` (string, optional) — `planned` (default), `in-progress`, `blocked`, `ready`, `deployed`
- `width` (number, optional) — pixels, default 200

### `rename_slice`
- `workspace_id`, `chapter_id`, `slice_id` (string, required)
- `old_label`, `new_label` (string, required)

### `update_slice_details`
- `workspace_id`, `chapter_id`, `slice_id` (string, required)
- `old_details`, `new_details` (string, required) — markdown content

### `update_slice_status`
- `workspace_id`, `chapter_id`, `slice_id` (string, required)
- `old_status`, `new_status` (string, required) — `planned`, `in-progress`, `blocked`, `ready`, `deployed`

### `reorder_slices`
- `workspace_id`, `chapter_id` (string, required)
- `old_slice_ids`, `new_slice_ids` (string[], required)

### `remove_slice`
Removes slice and all elements in it.
- `workspace_id`, `chapter_id`, `slice_id` (string, required)

---

## Elements

### `add_element`
Add sticky note to a cell (lane × slice intersection).
- `workspace_id`, `chapter_id` (string, required)
- `name` (string, required)
- `type` (string, required) — `command`, `event`, `information`, `automation`, `hotspot`, `ui`
- `lane_id`, `slice_id` (string, required)
- `description` (string, optional)
- `details` (string, optional) — markdown content
- `index` (number, optional) — position within cell, default 0

### `rename_element`
- `workspace_id`, `chapter_id`, `element_id` (string, required)
- `old_name`, `new_name` (string, required)

### `update_element_description`
- `workspace_id`, `chapter_id`, `element_id` (string, required)
- `old_description`, `new_description` (string, required)

### `update_element_details`
- `workspace_id`, `chapter_id`, `element_id` (string, required)
- `old_details`, `new_details` (string, required) — markdown content

### `add_element_comment`
- `workspace_id`, `chapter_id`, `element_id` (string, required)
- `text` (string, required)

### `move_element`
- `workspace_id`, `chapter_id`, `element_id` (string, required)
- `old_lane_id`, `old_slice_id` (string, required)
- `old_index` (number, required)
- `new_lane_id`, `new_slice_id` (string, required)
- `new_index` (number, required)

### `copy_element`
- `workspace_id`, `chapter_id` (string, required)
- `source_element_id` (string, required)
- `name`, `type` (string, required)
- `lane_id`, `slice_id` (string, required)
- `description`, `details` (string, optional)
- `index` (number, optional)

### `reorder_elements`
Reorder within a cell.
- `workspace_id`, `chapter_id`, `lane_id`, `slice_id` (string, required)
- `old_element_ids`, `new_element_ids` (string[], required)

### `remove_element`
- `workspace_id`, `chapter_id`, `element_id` (string, required)

---

## Search

### `search_elements`
Search elements by name/type across chapters.
- `workspace_id` (string, required)
- `query` (string, optional) — partial name match
- `element_type` (string, optional) — `command`, `event`, `information`, `automation`, `hotspot`, `ui`
- `chapter_id` (string, optional) — filter by chapter

---

## Changelog

### `list_changelog_events`
List changelog events (newest first).
- `workspace_id` (string, required)
- `chapter_id`, `element_id`, `slice_id` (string, optional) — filters
- `limit` (number, optional) — default 20, max 100
- `offset` (number, optional) — default 0

---

## Playshots

Playshots are saved snapshots of the board state.

### `list_playshots`
- `workspace_id` (string, required)
- `newer_than` (string, optional) — ISO 8601 timestamp filter

### `get_playshot`
- `playshot_id` (string, required)

### `save_playshot`
Create or update a playshot.
- `playshot_id` (string, optional) — provide for updates, omit for creates
- `workspace_id` (string, optional) — required for creates
- `name` (string, optional)
- `playshot` (object, optional) — configuration JSON

### `delete_playshot`
- `playshot_id` (string, required)

---

## Guidelines

### `guidelines`
Returns event modeling guidelines documentation. No parameters.
Call this to get the latest best practices directly from proophboard.

---

## Key Patterns

### Update Operations Require Old Values
All rename/update tools require the `old_*` value alongside the `new_*` value. Always `get_chapter` first to read current values before updating.

### Element Placement Rules (Enforced)
| Element Type | Allowed Lane Type |
|---|---|
| `command` | `information-flow` |
| `event` | `system` |
| `information` | `information-flow` |
| `automation` | `user-lane` |
| `ui` | `user-lane` |
| `hotspot` | any |

### Slice Rules (Enforced)
- Write slice: exactly one command, no information
- Read slice: one or more information elements, no commands
