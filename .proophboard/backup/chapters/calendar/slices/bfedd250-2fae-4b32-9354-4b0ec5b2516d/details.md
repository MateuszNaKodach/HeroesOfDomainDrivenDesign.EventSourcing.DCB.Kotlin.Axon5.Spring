## Business Rules

- Days cannot be skipped — must follow sequential order (day 1, 2, ..., 7)
- When last day of week (7) finishes, next day rolls over to day 1 of the next week
- When last week of month (4) finishes, next day rolls over to week 1 of the next month
- First day ever can be started without prior events

## Scenarios (GWTs)

### 1. start first day ever

**Given**
NOTHING
**When**
:::element command
Start Day  
month: 1  
week: 1  
day: 1  
:::
**Then**
:::element event
Day Started  
month: 1  
week: 1  
day: 1  
:::

### 2. previous day finished, start next day

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
Start Day  
month: 1  
week: 1  
day: 2  
:::
**Then**
:::element event
Day Started  
month: 1  
week: 1  
day: 2  
:::

### 3. cannot skip days

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
Start Day  
month: 1  
week: 1  
day: 3  
:::
**Then**
:::element hotspot
Exception  
Cannot skip days  
:::

### 4. last day of week finished, start first day of next week

**Given**
:::element event
Day Started  
month: 1  
week: 1  
day: 7  
:::
:::element event
Day Finished  
month: 1  
week: 1  
day: 7  
:::
**When**
:::element command
Start Day  
month: 1  
week: 2  
day: 1  
:::
**Then**
:::element event
Day Started  
month: 1  
week: 2  
day: 1  
:::

### 5. last day of month finished, start first day of next month

**Given**
:::element event
Day Started  
month: 1  
week: 4  
day: 7  
:::
:::element event
Day Finished  
month: 1  
week: 4  
day: 7  
:::
**When**
:::element command
Start Day  
month: 2  
week: 1  
day: 1  
:::
**Then**
:::element event
Day Started  
month: 2  
week: 1  
day: 1  
:::
