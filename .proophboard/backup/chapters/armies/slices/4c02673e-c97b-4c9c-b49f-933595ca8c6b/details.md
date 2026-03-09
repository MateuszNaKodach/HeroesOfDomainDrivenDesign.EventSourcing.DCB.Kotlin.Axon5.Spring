## Business Rules

- Can only remove up to the quantity present in the army
- Removing a creature not present in the army is a no-op (idempotent)

## Scenarios (GWTs)

### 1. army has creature, remove exact quantity

**Given**
:::element event
Creature Added To Army  
creatureId: Centaur  
quantity: 5  
:::
:::element event
Creature Added To Army  
creatureId: Bowman  
quantity: 99  
:::
**When**
:::element command
Remove Creature From Army  
creatureId: Centaur  
quantity: 5  
:::
**Then**
:::element event
Creature Removed From Army  
creatureId: Centaur  
quantity: 5  
:::

### 2. army has creature, remove partial quantity

**Given**
:::element event
Creature Added To Army  
creatureId: Centaur  
quantity: 5  
:::
**When**
:::element command
Remove Creature From Army  
creatureId: Centaur  
quantity: 3  
:::
**Then**
:::element event
Creature Removed From Army  
creatureId: Centaur  
quantity: 3  
:::

### 3. creature not present in army (idempotent no-op)

**Given**
:::element event
Creature Added To Army  
creatureId: Centaur  
quantity: 5  
:::
**When**
:::element command
Remove Creature From Army  
creatureId: Angel  
quantity: 1  
:::
**Then**
NOTHING

### 4. remove more than available

**Given**
:::element event
Creature Added To Army  
creatureId: Centaur  
quantity: 5  
:::
**When**
:::element command
Remove Creature From Army  
creatureId: Centaur  
quantity: 6  
:::
**Then**
:::element hotspot
Exception  
Cannot remove more creatures than present in army  
:::

### 5. creature already fully removed (idempotent replay)

**Given**
:::element event
Creature Added To Army  
creatureId: Centaur  
quantity: 5  
:::
:::element event
Creature Removed From Army  
creatureId: Centaur  
quantity: 5  
:::
**When**
:::element command
Remove Creature From Army  
creatureId: Centaur  
quantity: 5  
:::
**Then**
NOTHING
