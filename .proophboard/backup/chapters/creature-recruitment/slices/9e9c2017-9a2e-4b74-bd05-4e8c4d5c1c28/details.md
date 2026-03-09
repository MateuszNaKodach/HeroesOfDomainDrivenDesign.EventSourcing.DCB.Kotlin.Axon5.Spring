## Business Rules

- Only a built dwelling can have available creatures increased
- Available creatures are accumulated (changedTo = previous total + increaseBy)

## Scenarios (GWTs)

### 1. dwelling not built, cannot increase

**Given**
NOTHING
**When**
:::element command
Increase Available Creatures  
:::
**Then**
:::element hotspot
Exception  
Only built dwelling can have available creatures  
:::

### 2. dwelling built, increase for the first time

**Given**
:::element event
Dwelling Built  
dwellingId: portal-of-glory  
:::
**When**
:::element command
Increase Available Creatures  
dwellingId: portal-of-glory  
increaseBy: 3  
:::
**Then**
:::element event
Available Creatures Changed  
dwellingId: portal-of-glory  
changedBy: 3  
changedTo: 3  
:::

### 3. dwelling with existing creatures, increase accumulates

**Given**
:::element event
Dwelling Built  
dwellingId: portal-of-glory  
:::
:::element event
Available Creatures Changed  
dwellingId: portal-of-glory  
changedBy: 1  
changedTo: 1  
:::
**When**
:::element command
Increase Available Creatures  
dwellingId: portal-of-glory  
increaseBy: 2  
:::
**Then**
:::element event
Available Creatures Changed  
dwellingId: portal-of-glory  
changedBy: 2  
changedTo: 3  
:::
