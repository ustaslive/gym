# Exercise Data Editor

Local browser editor for `docs/data/exercise-bundle.json`.

## Run

```bash
python3 tools/exercise_editor/server.py --host 127.0.0.1 --port 8787
```

Open:

```text
http://127.0.0.1:8787/
```

## Behavior

- Edits the canonical shared exercise bundle.
- Validates the bundle before saving.
- Writes the file atomically.
- Creates a backup in `docs/data/backups/` before each successful save.
- Supports session, section, exercise-entry, catalog-item, and raw JSON editing.
- Catalog items include `Main muscle groups`, used by clients to suggest or highlight good next exercises.
