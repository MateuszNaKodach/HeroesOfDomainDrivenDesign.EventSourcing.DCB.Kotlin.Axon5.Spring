## Example

```json
{
  "dwellingId": "portal-of-glory",
  "creatureId": "angel",
  "armyId": "hero-army-1",
  "quantity": 1,
  "expectedCost": { "GOLD": 3000, "GEMS": 1 }
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
      "description": "Unique identifier for the dwelling to recruit from"
    },
    "creatureId": {
      "type": "string",
      "minLength": 1,
      "description": "Identifier of the creature type to recruit"
    },
    "armyId": {
      "type": "string",
      "minLength": 1,
      "description": "Identifier of the army receiving the creatures"
    },
    "quantity": {
      "type": "integer",
      "minimum": 1,
      "description": "Number of creatures to recruit"
    },
    "expectedCost": {
      "type": "object",
      "description": "Expected total resource cost for the recruitment",
      "additionalProperties": {
        "type": "integer",
        "minimum": 0
      }
    }
  },
  "required": ["dwellingId", "creatureId", "armyId", "quantity", "expectedCost"]
}
```


## Scenarios (GWTs)

### Scenario #1

#### Given
NOTHING

#### When
#:::element command
Recruit Creature
:::

#### Then
#:::element hotspot
Dwelling not built!
:::

