## Business Rules

- Day Finished sets `finished: true` on the current day — month/week/day remain unchanged
- Next Day Started resets `finished: false` with the new day
- Keyed by calendarId (which equals gameId)

## Scenarios (GWTs)

### 1. given day started and finished, then show that day as finished

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
**Then**
:::element information
Current Day  
month: 1  
week: 1  
day: 1  
finished: true  
:::

### 2. given day finished and next day started, then show new day in progress

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
**Then**
:::element information
Current Day  
month: 1  
week: 1  
day: 2  
finished: false  
:::

## Implementation Guidelines

### Backend

- REST API: `GET /games/{gameId}/current-day`
- Same projection as the first View Current Day slice — shared read model
- `calendarId` equals `gameId` — derive it from the path variable, do not expose as a separate parameter
- Response: return the full read model as-is, no mapping needed
- Return 404 if no day has been started yet