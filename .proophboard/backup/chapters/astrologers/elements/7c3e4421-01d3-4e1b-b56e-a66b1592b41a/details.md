## Example

```json
{
  "month": 1,
  "week": 2,
  "weekOf": "angel",
  "growth": 5
}
```

## JSON Schema

```json
{
  "type": "object",
  "properties": {
    "month": {
      "type": "integer",
      "minimum": 1,
      "description": "Month number (1-based)"
    },
    "week": {
      "type": "integer",
      "minimum": 1,
      "maximum": 4,
      "description": "Week of the month (1-4)"
    },
    "weekOf": {
      "type": "string",
      "minLength": 1,
      "description": "Creature type identifier for the week symbol"
    },
    "growth": {
      "type": "integer",
      "description": "Growth modifier for the creature population"
    }
  },
  "required": ["month", "week", "weekOf", "growth"]
}
```