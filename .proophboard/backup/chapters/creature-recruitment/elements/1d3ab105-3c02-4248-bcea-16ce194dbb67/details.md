## Example

```json
{
  "dwellingId": "portal-of-glory",
  "creatureId": "angel",
  "toArmy": "hero-army-1",
  "quantity": 1,
  "totalCost": { "GOLD": 3000, "GEMS": 1 }
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
      "description": "Identifier of the creature type recruited"
    },
    "toArmy": {
      "type": "string",
      "minLength": 1,
      "description": "Identifier of the army receiving the creatures"
    },
    "quantity": {
      "type": "integer",
      "minimum": 1,
      "description": "Number of creatures recruited"
    },
    "totalCost": {
      "type": "object",
      "description": "Total resource cost for the recruitment",
      "additionalProperties": {
        "type": "integer",
        "minimum": 0
      }
    }
  },
  "required": ["dwellingId", "creatureId", "toArmy", "quantity", "totalCost"]
}
```