## Business Rules

- The read model is keyed by month and week — each (month, week) pair has exactly one symbol
- Returns the proclaimed symbol (weekOf creature and growth) for a given week

## Scenarios (GWTs)

### 1. given week symbol proclaimed, then show symbol for that week

**Given**
:::element event
Week Symbol Proclaimed  
month: 1  
week: 2  
weekOf: angel  
growth: 5  
:::
**Then**
:::element information
Week Symbol  
month: 1  
week: 2  
weekOf: angel  
growth: 5  
:::

### 2. given multiple weeks proclaimed, then show symbol for queried week

**Given**
:::element event
Week Symbol Proclaimed  
month: 1  
week: 1  
weekOf: imp  
growth: 2  
:::
:::element event
Week Symbol Proclaimed  
month: 1  
week: 2  
weekOf: angel  
growth: 5  
:::
**Then**
:::element information
Week Symbol  
month: 1  
week: 2  
weekOf: angel  
growth: 5  
:::

### 3. given no proclamation for queried week, then no result

**Given**
NOTHING
**Then**
:::element information
Week Symbol  
NOTHING  
:::

## Implementation Guidelines

### Backend

- REST API: `GET /games/{gameId}/week-symbol/{month}/{week}`
- Path variables: `month` (int) and `week` (int) identify which week symbol to retrieve
- `astrologersId` equals `gameId` — derive it from the path variable, do not expose as a separate parameter
- Response: return the full read model as-is, no mapping needed
- Return 404 if no symbol has been proclaimed for the given month/week