# proophboard Event Modeling Guidelines

> Source: `mcp__proophboard__guidelines` tool. Call it for the latest version.

## Overview

Event modeling visualizes information flow through a system. It maps how users and automations interact by giving commands and viewing information. When the system handles a command, the result is recorded as an event, which updates state.

## Core Concepts

### Chapter
- A path/subpath through the system combining user journey and business process
- Each chapter goes in one specific direction
- Alternative/parallel paths go in separate chapters
- Modes: `event-modeling` or `freestyle`

### Lanes (Horizontal Rows)

| Lane Type | Purpose | Allowed Elements |
|-----------|---------|------------------|
| `user-lane` (User Role) | Information flow from user perspective | UI, Automation, Hot Spot |
| `information-flow` | Commands flowing in, information flowing out | Command, Information, Hot Spot |
| `system` (System Context) | Events showing which system part handles commands | Event, Hot Spot |

### Slices (Vertical Columns)
- Cut vertically through lanes
- Each slice = one step in the chapter
- **Write slice**: contains one and only one command
- **Read slice**: contains one or more information elements
- **Commands and information are NEVER in the same slice**

### Elements (Sticky Notes)

| Element Type | API `type` | Color | Lane Restriction |
|--------------|-----------|-------|------------------|
| Command | `command` | Blue (#26C0E7) | `information-flow` only |
| Event | `event` | Orange (#FF9F4B) | `system` only |
| Information | `information` | Green (#73dd8e) | `information-flow` only |
| Automation | `automation` | Purple (#EABFF1) | `user-lane` only |
| Hot Spot | `hotspot` | Red (#f31d30) | Any lane |
| UI | `ui` | Gray | `user-lane` only |

## Automatic Connection Rules

Arrows are drawn automatically based on element placement:

1. UI/Automation → first command in **same slice** (blue arrow)
2. Command → first event in **same slice** (blue arrow)
3. First event → last information in **next slice** (orange arrow)
4. Information → UI/Automation in **same slice** (green arrow)

## Event Flow Patterns

### Standard User Action
```
[UI] → [Command] → [Event] → [Information] → [UI]
```

### Automation Pattern
```
[Automation] → [Command] → [Event] → [Information] → [UI]
```

### System-to-System (Task List)
```
[Command] → [Event] → [Information: tasklist] → [Automation] → [Command]
```

### System-to-System (Direct Reaction)
```
[Command] → [Event] → [Automation] → [Command]
```

## Naming Conventions

- **Commands**: imperative verbs — "Register User", "Process Payment"
- **Events**: past tense — "User Registered", "Payment Processed"
- **Information**: nouns — "User Profile", "Order Summary"

## Slice Status Tracking

`planned` → `in-progress` → `blocked` / `ready` → `deployed`

## Modeling Best Practices

1. Always check current chapter details before adding new elements
2. Each slice follows write/read pattern — never mix commands and information
3. After adding an element, verify model structure
4. One command per write slice
5. Multiple information elements are OK in a single read slice
6. Use Hot Spots for bottlenecks, open questions, critical issues

## Cross-Chapter References

- Use shared event names to connect chapters
- An outcome (event) in one chapter can start another chapter
- Maintain naming consistency across chapters

## Freestyle Mode

- Allows flexible placement without lane restrictions
- No automatic arrow connections
- Good for brainstorming
- Not shown in chapter overview
