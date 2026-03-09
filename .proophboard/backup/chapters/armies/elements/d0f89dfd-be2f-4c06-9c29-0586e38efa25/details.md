## Example

```json
{
  "armyId": "hero-catherine-army",
  "creatureId": "angel",
  "quantity": 3
}
```

## JSON Schema

```json
{
  "type": "object",
  "properties": {
    "armyId": {
      "type": "string",
      "minLength": 1,
      "description": "Unique identifier of the army"
    },
    "creatureId": {
      "type": "string",
      "minLength": 1,
      "description": "Creature type removed"
    },
    "quantity": {
      "type": "integer",
      "minimum": 1,
      "description": "Number of troops removed"
    }
  },
  "required": ["armyId", "creatureId", "quantity"]
}
```