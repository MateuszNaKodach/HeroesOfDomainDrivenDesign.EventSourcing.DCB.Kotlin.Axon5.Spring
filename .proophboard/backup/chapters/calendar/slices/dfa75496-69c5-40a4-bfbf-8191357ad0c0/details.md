## Business Rules

- The read model tracks the current day from the latest Day Started event
- Each Day Started sets `finished: false`
- Day Finished sets `finished: true` but keeps the same month/week/day
- Keyed by calendarId (which equals gameId)

## Scenarios (GWTs)

### 1. given day started, then show current day in progress

**Given**
:::element event
Day Started  
month: 1  
week: 1  
day: 1  
:::
**Then**
:::element information
Current Day  
month: 1  
week: 1  
day: 1  
finished: false  
:::

### 2. given multiple days started, then show the latest day

**Given**
:::element event
Day Started  
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
**Then**
:::element information
Current Day  
month: 1  
week: 1  
day: 2  
finished: false  
:::

### 3. given no day started, then no result

**Given**
NOTHING
**Then**
:::element information
Current Day  
NOTHING  
:::

## Implementation Guidelines

### Backend

- REST API: `GET /games/{gameId}/current-day`
- `calendarId` equals `gameId` — derive it from the path variable, do not expose as a separate parameter
- Response: return the full read model as-is, no mapping needed
- Return 404 if no day has been started yet