## Example

```json
{
  "month": 1,
  "week": 2,
  "day": 3,
  "finished": false
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
      "description": "Current month number (1-based)"
    },
    "week": {
      "type": "integer",
      "minimum": 1,
      "maximum": 4,
      "description": "Current week of the month (1-4)"
    },
    "day": {
      "type": "integer",
      "minimum": 1,
      "maximum": 7,
      "description": "Current day of the week (1-7)"
    },
    "finished": {
      "type": "boolean",
      "description": "Whether the current day has been finished"
    }
  },
  "required": ["month", "week", "day", "finished"]
}
```