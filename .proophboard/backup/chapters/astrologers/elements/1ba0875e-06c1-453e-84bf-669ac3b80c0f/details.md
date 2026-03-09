## Example

```json
{
  "dwellingId": "portal-of-glory",
  "creatureId": "angel",
  "increaseBy": 3
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
    "increaseBy": {
      "type": "integer",
      "minimum": 0,
      "description": "Number of creatures to add to available pool"
    }
  },
  "required": ["dwellingId", "creatureId", "increaseBy"]
}
```