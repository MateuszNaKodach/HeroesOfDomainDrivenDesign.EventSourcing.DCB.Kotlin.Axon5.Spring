## Example

```json
{
  "dwellingId": "portal-of-glory",
  "creatureId": "angel",
  "changedBy": 3,
  "changedTo": 3
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
    "changedBy": {
      "type": "integer",
      "description": "Delta of available creatures (positive = increase)"
    },
    "changedTo": {
      "type": "integer",
      "minimum": 0,
      "description": "New total of available creatures after the change"
    }
  },
  "required": ["dwellingId", "creatureId", "changedBy", "changedTo"]
}
```