---
name: em-proophboard-backup
description: "Backup proophboard Event Modeling workspace to local JSON files. Downloads all chapters (with lanes, slices, elements) into .proophboard/chapters/ and records backup metadata. Use when: (1) user says 'backup', 'sync', 'download', 'snapshot' in context of proophboard or event model, (2) user wants to save the current board state to git, (3) user says 'em-proophboard-backup' or '/em-proophboard-backup'."
---

# proophboard Backup

Download the full proophboard workspace into local `.proophboard/backup` JSON files.

## Procedure

Run the backup script:

```bash
node .proophboard/scripts/backup.mjs
```

The script reads `PROOPHBOARD_API_KEY` from `.proophboard/.env.local` (preferred, gitignored) or `.proophboard/.env`, falling back to the environment variable.

If the key is missing and `.proophboard/.env.local` does not exist, ask the user to provide it, then write it to `.proophboard/.env.local`:
```
PROOPHBOARD_API_KEY=pb_xxxx
```

Report the script's stdout output to the user.
