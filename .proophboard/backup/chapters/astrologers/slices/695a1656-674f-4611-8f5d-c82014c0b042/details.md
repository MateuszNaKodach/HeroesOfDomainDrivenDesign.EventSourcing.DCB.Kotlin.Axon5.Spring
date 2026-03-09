## Business Rules

- Only one symbol can be proclaimed per week — proclamations must progress forward in time
- A symbol for a later week can always be proclaimed (even across months)
- Proclaiming for the same or earlier week is rejected

## Scenarios (GWTs)

### 1. proclaim first week symbol

**Given**
NOTHING
**When**
:::element command
Proclaim Week Symbol  
month: 1  
week: 1  
weekOf: angel  
growth: 3  
:::
**Then**
:::element event
Week Symbol Proclaimed  
month: 1  
week: 1  
weekOf: angel  
growth: 3  
:::

### 2. proclaim for next week in same month

**Given**
:::element event
Week Symbol Proclaimed  
month: 1  
week: 1  
:::
**When**
:::element command
Proclaim Week Symbol  
month: 1  
week: 2  
weekOf: angel  
growth: 5  
:::
**Then**
:::element event
Week Symbol Proclaimed  
month: 1  
week: 2  
weekOf: angel  
growth: 5  
:::

### 3. proclaim for next month

**Given**
:::element event
Week Symbol Proclaimed  
month: 1  
week: 4  
:::
**When**
:::element command
Proclaim Week Symbol  
month: 2  
week: 1  
weekOf: angel  
growth: 2  
:::
**Then**
:::element event
Week Symbol Proclaimed  
month: 2  
week: 1  
weekOf: angel  
growth: 2  
:::

### 4. try to proclaim for the same week

**Given**
:::element event
Week Symbol Proclaimed  
month: 1  
week: 1  
:::
**When**
:::element command
Proclaim Week Symbol  
month: 1  
week: 1  
:::
**Then**
:::element hotspot
Exception  
Only one symbol can be proclaimed per week  
:::

### 5. try to proclaim for an earlier week

**Given**
:::element event
Week Symbol Proclaimed  
month: 1  
week: 2  
:::
**When**
:::element command
Proclaim Week Symbol  
month: 1  
week: 1  
:::
**Then**
:::element hotspot
Exception  
Only one symbol can be proclaimed per week  
:::