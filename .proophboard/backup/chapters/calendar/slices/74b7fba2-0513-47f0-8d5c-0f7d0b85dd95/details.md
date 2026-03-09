## Business Rules

- Can only finish the current day (the one that was started and not yet finished)
- Cannot finish a day that was never started
- Cannot finish a day that was already finished

## Scenarios (GWTs)

### 1. day started, finish that day

**Given**
:::element event
Day Started  
month: 1  
week: 1  
day: 1  
:::
**When**
:::element command
Finish Day  
month: 1  
week: 1  
day: 1  
:::
**Then**
:::element event
Day Finished  
month: 1  
week: 1  
day: 1  
:::

### 2. no day started, cannot finish

**Given**
NOTHING
**When**
:::element command
Finish Day  
month: 1  
week: 1  
day: 1  
:::
**Then**
:::element hotspot
Exception  
Can only finish current day  
:::

### 3. day started, try to finish different day

**Given**
:::element event
Day Started  
month: 1  
week: 1  
day: 1  
:::
**When**
:::element command
Finish Day  
month: 1  
week: 1  
day: 2  
:::
**Then**
:::element hotspot
Exception  
Can only finish current day  
:::

### 4. day already finished, cannot finish again

**Given**
:::element event
Day Started  
month: 1  
week: 1  
day: 1  
:::
:::element event
Day Finished  
month: 1  
week: 1  
day: 1  
:::
**When**
:::element command
Finish Day  
month: 1  
week: 1  
day: 1  
:::
**Then**
:::element hotspot
Exception  
Can only finish current day  
:::

### 5. second day started, finish second day

**Given**
:::element event
Day Started  
month: 1  
week: 1  
day: 1  
:::
:::element event
Day Finished  
month: 1  
week: 1  
day: 1  
:::
:::element event
Day Started  
month: 1  
week: 1  
day: 2  
:::
**When**
:::element command
Finish Day  
month: 1  
week: 1  
day: 2  
:::
**Then**
:::element event
Day Finished  
month: 1  
week: 1  
day: 2  
:::
