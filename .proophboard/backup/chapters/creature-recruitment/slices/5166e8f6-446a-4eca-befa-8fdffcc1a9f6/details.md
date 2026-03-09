## Scenarios (GWTs)

### 1. dwelling built and creatures increased, then updated available creatures

**Given**
:::element event
Dwelling Built  
dwellingId: portal-of-glory  
creatureId: Phoenix  
costPerTroop: {GOLD: 2000, MERCURY: 1}  
:::
:::element event
Available Creatures Changed  
dwellingId: portal-of-glory  
creatureId: Phoenix  
changedBy: +5  
changedTo: 5  
:::
**Then**
:::element information
Dwelling  
dwellingId: portal-of-glory  
creatureId: Phoenix  
costPerTroop: {GOLD: 2000, MERCURY: 1}  
availableCreatures: 5  
:::
