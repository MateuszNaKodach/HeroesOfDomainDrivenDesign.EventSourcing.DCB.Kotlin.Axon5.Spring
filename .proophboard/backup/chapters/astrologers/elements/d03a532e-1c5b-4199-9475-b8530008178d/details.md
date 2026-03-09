## Example

```json
{
  "dwellingId": "portal-of-glory",
  "creatureId": "angel",
  "costPerTroop": { "GOLD": 3000, "GEMS": 1 }
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
      "description": "Identifier of the creature type this dwelling produces"
    },
    "costPerTroop": {
      "type": "object",
      "description": "Resource cost per single troop recruitment",
      "additionalProperties": {
        "type": "integer",
        "minimum": 0
      }
    }
  },
  "required": ["dwellingId", "creatureId", "costPerTroop"]
}
```