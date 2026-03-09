## Scenarios (GWTs)

### 1. creature added, then one stack returned

**Given**
:::element event
Creature Added To Army  
armyId: hero-catherine-army  
creatureId: Angel  
quantity: 5  
:::
**Then**
:::element information
Army Creatures  
armyId: hero-catherine-army  
stacks: [{creatureId: Angel, quantity: 5}]  
:::

### 2. two different creatures added, then two stacks returned

**Given**
:::element event
Creature Added To Army  
armyId: hero-catherine-army  
creatureId: Angel  
quantity: 5  
:::
:::element event
Creature Added To Army  
armyId: hero-catherine-army  
creatureId: Bowman  
quantity: 3  
:::
**Then**
:::element information
Army Creatures  
armyId: hero-catherine-army  
stacks: [{creatureId: Angel, quantity: 5}, {creatureId: Bowman, quantity: 3}]  
:::

### 3. same creature added twice, quantities aggregated

**Given**
:::element event
Creature Added To Army  
armyId: hero-catherine-army  
creatureId: Angel  
quantity: 5  
:::
:::element event
Creature Added To Army  
armyId: hero-catherine-army  
creatureId: Angel  
quantity: 3  
:::
**Then**
:::element information
Army Creatures  
armyId: hero-catherine-army  
stacks: [{creatureId: Angel, quantity: 8}]  
:::

### 4. creatures from different armies, only requested army returned

**Given**
:::element event
Creature Added To Army  
armyId: hero-catherine-army  
creatureId: Angel  
quantity: 5  
:::
:::element event
Creature Added To Army  
armyId: hero-roland-army  
creatureId: Black Dragon  
quantity: 2  
:::
**Then**
:::element information
Army Creatures  
armyId: hero-catherine-army  
stacks: [{creatureId: Angel, quantity: 5}]  
:::
