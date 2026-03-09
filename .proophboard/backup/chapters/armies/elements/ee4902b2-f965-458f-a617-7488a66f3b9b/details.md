## Example

```json
{
  "armyId": "hero-catherine-army",
  "stacks": [
     {
      "creatureId": "angel",
      "quantity": 5
     },
     {
      "creatureId": "bowman",
      "quantity": 3
     }
  ]
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
    "stacks": {
      "type": "array",
      "description": "Creature stacks in the army, one entry per creature type",
      "items": {
        "type": "object",
        "properties": {
          "creatureId": {
            "type": "string",
            "minLength": 1,
            "description": "Creature type identifier"
          },
          "quantity": {
            "type": "integer",
            "minimum": 1,
            "description": "Total number of troops of this creature type"
          }
        },
        "required": ["creatureId", "quantity"]
      },
      "maxItems": 7
    }
  },
  "required": ["armyId", "stacks"]
}
```