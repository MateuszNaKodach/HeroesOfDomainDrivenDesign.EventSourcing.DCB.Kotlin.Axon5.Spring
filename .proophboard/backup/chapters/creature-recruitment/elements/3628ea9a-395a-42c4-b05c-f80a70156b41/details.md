## Example

```json
{
  "armyId": "hero-catherine-army",
  "creatureId": "angel",
  "quantity": 5
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
      "description": "Creature type identifier"
    },
    "quantity": {
      "type": "integer",
      "minimum": 1,
      "description": "Number of troops to add"
    }
  },
  "required": ["armyId", "creatureId", "quantity"]
}
```