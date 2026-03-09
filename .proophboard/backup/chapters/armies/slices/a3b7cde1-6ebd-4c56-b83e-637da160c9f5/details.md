## Scenarios (GWTs)

### 1. creature added then partially removed, reduced quantity

**Given**
:::element event
Creature Added To Army  
armyId: hero-catherine-army  
creatureId: Angel  
quantity: 5  
:::
:::element event
Creature Removed From Army  
armyId: hero-catherine-army  
creatureId: Angel  
quantity: 2  
:::
**Then**
:::element information
Army Creatures  
armyId: hero-catherine-army  
stacks: [{creatureId: Angel, quantity: 3}]  
:::

### 2. creature added then fully removed, stack disappears

**Given**
:::element event
Creature Added To Army  
armyId: hero-catherine-army  
creatureId: Angel  
quantity: 5  
:::
:::element event
Creature Removed From Army  
armyId: hero-catherine-army  
creatureId: Angel  
quantity: 5  
:::
**Then**
:::element information
Army Creatures  
armyId: hero-catherine-army  
stacks: []  
:::
