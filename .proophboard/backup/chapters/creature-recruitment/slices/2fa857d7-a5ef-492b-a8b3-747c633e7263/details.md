## Scenarios (GWTs)

### 1. creature recruited, then add to army

**Given**
:::element event
Creature Recruited  
dwellingId: portal-of-glory  
creatureId: Angel  
toArmy: hero-catherine-army  
quantity: 1  
:::
**Then**
:::element command
Add Creature To Army  
armyId: hero-catherine-army  
creatureId: Angel  
quantity: 1  
:::

### 2. multiple creatures recruited, then add all to army

**Given**
:::element event
Creature Recruited  
dwellingId: portal-of-glory  
creatureId: Angel  
toArmy: hero-catherine-army  
quantity: 3  
:::
**Then**
:::element command
Add Creature To Army  
armyId: hero-catherine-army  
creatureId: Angel  
quantity: 3  
:::
