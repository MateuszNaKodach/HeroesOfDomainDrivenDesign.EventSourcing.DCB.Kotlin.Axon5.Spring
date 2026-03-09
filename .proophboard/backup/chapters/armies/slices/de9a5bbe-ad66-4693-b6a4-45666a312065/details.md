## Business Rules

- Army has a maximum of **7 creature type slots**
- Each `creatureId` represents a distinct creature type occupying one slot
- Adding troops of an **existing** creature type is always allowed (increases amount)
- Adding a **new** creature type when all 7 slots are occupied must be **rejected**

## Scenarios (GWTs)

### 1. given empty army, when add creature, then success

**Given**
NOTHING  
**When**
:::element command
Add Creature To Army
creatureId: Angel  
quantity: 1
:::
**Then**
:::element event
Creature Added To Army
creatureId: Angel  
quantity: 1
:::

### 2. given some creatures in the army, when add creature, then success 

**Given**
:::element event
Creature Added To Army
creatureId: Centaur  
quantity: 5
:::
:::element event
Creature Added To Army
creatureId: Bowman  
quantity: 3
:::
**When**
:::element command
Add Creature To Army
creatureId: Angel  
quantity: 1
:::
**Then**
:::element event
Creature Added To Army
creatureId: Angel  
quantity: 1
:::

### 3. given army with max creature stacks, when add creature, then failure

**Given**
:::element event
Creature Added To Army
creatureId: Centaur  
quantity: 5
:::
:::element event
Creature Added To Army
creatureId: Angel  
quantity: 1
:::
:::element event
Creature Added To Army
creatureId: ArchAngel  
quantity: 3
:::
:::element event
Creature Added To Army
creatureId: BlackDragon  
quantity: 9
:::
:::element event
Creature Added To Army
creatureId: RedDragon  
quantity: 15
:::
:::element event
Creature Added To Army
creatureId: Bowman  
quantity: 12
:::
:::element event
Creature Added To Army
creatureId: Behemoth  
quantity: 11
:::
**When**
:::element command
Add Creature To Army
creatureId: Phoenix  
quantity: 3
:::
**Then**
:::element hotspot
Exception
Can have max 7 different creature stacks in the army
:::

### 4. given army with max creature stacks, when add present creature, then success

**Given**
:::element event
Creature Added To Army
creatureId: Centaur  
quantity: 5
:::
:::element event
Creature Added To Army
creatureId: Angel  
quantity: 1
:::
:::element event
Creature Added To Army
creatureId: ArchAngel  
quantity: 3
:::
:::element event
Creature Added To Army
creatureId: BlackDragon  
quantity: 9
:::
:::element event
Creature Added To Army
creatureId: RedDragon  
quantity: 15
:::
:::element event
Creature Added To Army
creatureId: Bowman  
quantity: 12
:::
:::element event
Creature Added To Army
creatureId: Behemoth  
quantity: 11
:::
**When**
:::element command
Add Creature To Army
creatureId: ArchAngel  
quantity: 3
:::
**Then**
:::element event
Creature Added To Army
creatureId: ArchAngel  
quantity: 3
:::


### 5. given army with max creature stacks (after removal), when add present creature, then success

**Given**
:::element event
Creature Added To Army
creatureId: Centaur  
quantity: 5
:::
:::element event
Creature Added To Army
creatureId: Angel  
quantity: 1
:::
:::element event
Creature Added To Army
creatureId: ArchAngel  
quantity: 3
:::
:::element event
Creature Added To Army
creatureId: BlackDragon  
quantity: 9
:::
:::element event
Creature Added To Army
creatureId: RedDragon  
quantity: 15
:::
:::element event
Creature Added To Army
creatureId: Bowman  
quantity: 12
:::
:::element event
Creature Added To Army
creatureId: Behemoth  
quantity: 11
:::
:::element event
Creature Removed From Army
creatureId: Behemoth  
quantity: 11
:::
**When**
:::element command
Add Creature To Army
creatureId: Phoenix  
quantity: 3
:::
**Then**
:::element event
Creature Added To Army
creatureId: Phoenix  
quantity: 3
:::
