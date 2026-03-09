## Scenarios (GWTs)

### 1. dwelling built, then visible with zero available creatures

**Given**
:::element event
Dwelling Built  
dwellingId: portal-of-glory  
creatureId: Phoenix  
costPerTroop: {GOLD: 2000, MERCURY: 1}  
:::
**Then**
:::element information
Dwelling  
dwellingId: portal-of-glory  
creatureId: Phoenix  
costPerTroop: {GOLD: 2000, MERCURY: 1}  
availableCreatures: 0  
:::

### 2. two dwellings built, then both returned

**Given**
:::element event
Dwelling Built  
dwellingId: portal-of-glory  
creatureId: Phoenix  
costPerTroop: {GOLD: 2000, MERCURY: 1}  
:::
:::element event
Dwelling Built  
dwellingId: cursed-temple  
creatureId: Phoenix  
costPerTroop: {GOLD: 2000, MERCURY: 1}  
:::
**Then**
:::element information
Dwelling  
dwellingId: portal-of-glory  
creatureId: Phoenix  
costPerTroop: {GOLD: 2000, MERCURY: 1}  
availableCreatures: 0  
:::
:::element information
Dwelling  
dwellingId: cursed-temple  
creatureId: Phoenix  
costPerTroop: {GOLD: 2000, MERCURY: 1}  
availableCreatures: 0  
:::
