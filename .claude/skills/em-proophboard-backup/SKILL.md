---
name: em-proophboard-backup
description: "Backup proophboard Event Modeling workspace to local JSON files. Downloads all chapters (with lanes, slices, elements) into .proophboard/chapters/ and records backup metadata. Use when: (1) user says 'backup', 'sync', 'download', 'snapshot' in context of proophboard or event model, (2) user wants to save the current board state to git, (3) user says 'em-proophboard-backup' or '/em-proophboard-backup'."
---

# proophboard Backup

Download the full proophboard workspace into local `.proophboard/backup` JSON files.

## Procedure

Execute these steps in order. Do not skip any step.

### 1. Read workspace config
Read `.proophboard/workspace.json` from the repository root to get `workspace_id`.
If the file does not exist, stop and tell the user to configure it first (or invoke the `em-proophboard` skill).

### 2. List all chapters
Call `mcp__proophboard__list_chapters` with the `workspace_id`.
Write the result array to `.proophboard/backup/chapters/chapters.json` (overwrite if exists).

### 3. Download each chapter
For each chapter in the list, call `mcp__proophboard__get_chapter` with `workspace_id` and `chapter_id`.
Write each full chapter response to `.proophboard/backup/chapters/{chapter_id}.json` (overwrite if exists).

Call `get_chapter` for all chapters in parallel when possible.

### 4. Clean up removed chapters
List existing `.json` files in `.proophboard/backup/chapters/` (excluding `chapters.json`).
Delete any file whose name (without `.json`) is not in the current chapter ID list — these are chapters that no longer exist on the board.

### 5. Write backup metadata
Write `.proophboard/backup/backup.json` with:
```json
{
  "backed_up_at": "<ISO 8601 timestamp>",
  "workspace_id": "<workspace_id>",
  "workspace_name": "<workspace_name>",
  "chapters_count": <number of chapters backed up>
}
```

### 6. Report
Print a summary to the user:
- Workspace name
- Number of chapters backed up
- List of chapter names
- Timestamp
