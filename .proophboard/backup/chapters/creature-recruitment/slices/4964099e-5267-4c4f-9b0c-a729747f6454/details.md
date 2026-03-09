## Business Rules

- Recruit creatures cannot exceed available creatures
- Can only recruit the creature type that the dwelling produces
- Expected cost must match actual cost (costPerTroop * quantity)

## Scenarios (GWTs)

### 1. dwelling not built, cannot recruit

**Given**
NOTHING
**When**
:::element command
Recruit Creature  
:::
**Then**
:::element hotspot
Exception  
Recruit creatures cannot exceed available creatures  
:::

### 2. dwelling built but no available creatures

**Given**
:::element event
Dwelling Built  
dwellingId: portal-of-glory  
creatureId: Angel  
costPerTroop: {GOLD: 3000, GEMS: 1}  
:::
**When**
:::element command
Recruit Creature  
dwellingId: portal-of-glory  
creatureId: Angel  
quantity: 1  
:::
**Then**
:::element hotspot
Exception  
Recruit creatures cannot exceed available creatures  
:::

### 3. dwelling with available creatures, recruit all

**Given**
:::element event
Dwelling Built  
dwellingId: portal-of-glory  
creatureId: Angel  
costPerTroop: {GOLD: 3000, GEMS: 1}  
:::
:::element event
Available Creatures Changed  
dwellingId: portal-of-glory  
changedBy: 1  
changedTo: 1  
:::
**When**
:::element command
Recruit Creature  
dwellingId: portal-of-glory  
creatureId: Angel  
quantity: 1  
expectedCost: {GOLD: 3000, GEMS: 1}  
:::
**Then**
:::element event
Creature Recruited  
dwellingId: portal-of-glory  
creatureId: Angel  
quantity: 1  
totalCost: {GOLD: 3000, GEMS: 1}  
:::
:::element event
Available Creatures Changed  
dwellingId: portal-of-glory  
changedBy: -1  
changedTo: 0  
:::

### 4. dwelling with 4 creatures, recruit 3, 1 remains

**Given**
:::element event
Dwelling Built  
dwellingId: portal-of-glory  
creatureId: Angel  
costPerTroop: {GOLD: 3000, GEMS: 1}  
:::
:::element event
Available Creatures Changed  
dwellingId: portal-of-glory  
changedBy: 3  
changedTo: 3  
:::
:::element event
Available Creatures Changed  
dwellingId: portal-of-glory  
changedBy: 1  
changedTo: 4  
:::
**When**
:::element command
Recruit Creature  
dwellingId: portal-of-glory  
creatureId: Angel  
quantity: 3  
expectedCost: {GOLD: 9000, GEMS: 3}  
:::
**Then**
:::element event
Creature Recruited  
dwellingId: portal-of-glory  
creatureId: Angel  
quantity: 3  
totalCost: {GOLD: 9000, GEMS: 3}  
:::
:::element event
Available Creatures Changed  
dwellingId: portal-of-glory  
changedBy: -3  
changedTo: 1  
:::

### 5. recruit more than available

**Given**
:::element event
Dwelling Built  
dwellingId: portal-of-glory  
creatureId: Angel  
:::
:::element event
Available Creatures Changed  
dwellingId: portal-of-glory  
changedBy: 5  
changedTo: 5  
:::
**When**
:::element command
Recruit Creature  
dwellingId: portal-of-glory  
quantity: 6  
:::
**Then**
:::element hotspot
Exception  
Recruit creatures cannot exceed available creatures  
:::

### 6. recruit creature not from this dwelling

**Given**
:::element event
Dwelling Built  
dwellingId: portal-of-glory  
creatureId: Angel  
:::
:::element event
Available Creatures Changed  
dwellingId: portal-of-glory  
creatureId: Angel  
changedBy: 1  
changedTo: 1  
:::
**When**
:::element command
Recruit Creature  
dwellingId: portal-of-glory  
creatureId: Black Dragon  
quantity: 1  
:::
**Then**
:::element hotspot
Exception  
Recruit creatures cannot exceed available creatures  
:::

### 7. all creatures already recruited, cannot recruit again

**Given**
:::element event
Dwelling Built  
dwellingId: portal-of-glory  
creatureId: Angel  
costPerTroop: {GOLD: 3000, GEMS: 1}  
:::
:::element event
Available Creatures Changed  
dwellingId: portal-of-glory  
changedBy: 3  
changedTo: 3  
:::
:::element event
Creature Recruited  
dwellingId: portal-of-glory  
creatureId: Angel  
quantity: 3  
:::
:::element event
Available Creatures Changed  
dwellingId: portal-of-glory  
changedBy: -3  
changedTo: 0  
:::
**When**
:::element command
Recruit Creature  
dwellingId: portal-of-glory  
creatureId: Angel  
quantity: 1  
:::
**Then**
:::element hotspot
Exception  
Recruit creatures cannot exceed available creatures  
:::

### 8. expected cost does not match actual cost

**Given**
:::element event
Dwelling Built  
dwellingId: portal-of-glory  
creatureId: Angel  
costPerTroop: {GOLD: 3000, GEMS: 1}  
:::
:::element event
Available Creatures Changed  
dwellingId: portal-of-glory  
changedBy: 1  
changedTo: 1  
:::
**When**
:::element command
Recruit Creature  
dwellingId: portal-of-glory  
creatureId: Angel  
quantity: 1  
expectedCost: {GOLD: 999999, GEMS: 0}  
:::
**Then**
:::element hotspot
Exception  
Recruit cost cannot differ than expected cost  
:::
