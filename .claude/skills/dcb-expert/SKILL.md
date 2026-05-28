---
name: dcb-expert
description: Subject matter expert on Dynamic Consistency Boundary (DCB) — a technique for enforcing consistency in event-driven systems without rigid transactional boundaries. Use when the user asks about DCB concepts, specification, patterns, examples, or compares DCB with traditional Event Sourcing approaches (aggregates, event streams, sagas). Covers topics including tags, projections, decision models, append conditions, queries, and the DCB-compliant Event Store interface. Implementation-agnostic — explains concepts without adhering to any specific library or language.
---

# DCB Expert

Act as a subject matter expert on Dynamic Consistency Boundary (DCB). Answer questions based on the specification and examples. Do not promote any specific implementation — remain technology- and language-agnostic.

## What is DCB

Dynamic Consistency Boundary is a technique for enforcing consistency in event-driven systems without relying on rigid transactional boundaries. Introduced by Sara Pellegrini in "Killing the Aggregate" (2023).

Core idea: allow events to be assigned to **multiple** domain concepts and dynamically enforce consistency amongst them. One event can affect multiple entities/concepts in the same bounded context.

Key distinction from traditional Event Sourcing: DCB does **not** split events into per-aggregate streams. Instead, there is a single global event sequence per bounded context, with events correlated via **tags**.

## Core Pattern: Read → Decide → Append

1. **Read** — query the Event Store for relevant events (filtered by event type and/or tags)
2. **Decide** — build a Decision Model (in-memory projection) from those events and evaluate business invariants
3. **Append** — persist new event(s) with an Append Condition that ensures no conflicting events were stored since the read

## Key Concepts

### Event Store Interface (Specification)

A DCB-compliant Event Store provides two operations:

```
read(query: Query, options?: ReadOptions): SequencedEvents
append(events: Events, condition?: AppendCondition): void
```

- `read` MUST filter events by type and/or tags
- `append` MUST atomically persist events; MUST fail if Append Condition is violated

For full specification details, see [references/specification.md](references/specification.md).

### Tags

Tags are explicit references to domain concepts involved in consistency rules. They correlate events with specific instances (e.g., `course:c1`, `student:s1`, `product:laptop-x1`).

- Event types tell *what happened*; tags tell *to whom/what*
- Tags enable precise event selection without payload inspection
- Tags must be derivable from event payload
- Use prefixes for disambiguation (e.g., `customer:c123`)
- Avoid personal data in tags — hash sensitive values

For deep dive, see [references/tags.md](references/tags.md).

### Query

Describes constraints for filtering events. Contains Query Items combined with OR. Each Query Item specifies:
- **types** — event types (OR within item)
- **tags** — tags (AND within item, i.e., event must have ALL specified tags)

### Append Condition

Enforces optimistic concurrency:
- `failIfEventsMatch: Query` — the Event Store MUST fail if any event matches this query after the specified position
- `after?: SequencePosition` — ignore events before this position

Typically, the `failIfEventsMatch` query is the same query used to build the Decision Model.

### Projections and Decision Models

A projection is `(state, event) => state` — a pure fold over events.

DCB projections include:
- `initialState` — starting state
- `handlers` — map of event type to handler function
- `tagFilter` — tags for filtering

Projections can be **composed** via `composeProjections` to build Decision Models that enforce multiple constraints in a single consistency boundary.

For implementation patterns and examples, see [references/projections.md](references/projections.md).

### Sequence Position

Assigned by the Event Store when an event is appended:
- MUST be unique
- MUST be monotonically increasing
- MAY contain gaps

## Common Misconceptions

- **DCB is NOT about weaker consistency.** Consistency boundaries are always enforced immediately.
- **DCB does NOT kill Aggregates.** It offers an alternative approach; you can still use Aggregates with DCB.
- **DCB does NOT promote strong consistency everywhere.** It makes switching between strong and eventual consistency easier.
- **DCB does NOT scatter business logic.** It enables structuring code around use cases.
- **DCB does NOT increase lock collisions.** It reduces them by narrowing consistency boundaries.
- **DCB requires an ordered event store.** Events must be ordered; partitioning is not easily possible.

## Relation to Aggregates

DCB does not reject the Aggregate pattern — it evolves it. Instead of hardwired static boundaries (Event Streams per Aggregate), DCB constructs a Decision Model dynamically per operation. The term "Decision Model" is preferred over overloading "Aggregate."

For full analysis, see [references/aggregates.md](references/aggregates.md).

## Examples

The following examples demonstrate DCB patterns. For full details with code and test cases, see [references/examples.md](references/examples.md).

| Example | Pattern |
|---------|---------|
| Course subscriptions | Cross-entity constraints (course capacity + student max courses) |
| Unique username | Global uniqueness enforcement, username release, retention periods |
| Dynamic product price | Price validation with grace periods, multi-product orders |
| Event-sourced aggregate | Using traditional Aggregate pattern with DCB Event Store |
| Invoice number | Monotonic gapless sequences |
| Opt-in token (OTP) | Double opt-in without Read Models or cryptography |
| Prevent record duplication | Idempotency via dedicated token tags |

## References

- [references/specification.md](references/specification.md) — full DCB Event Store specification
- [references/aggregates.md](references/aggregates.md) — Aggregate pattern analysis and relation to DCB
- [references/projections.md](references/projections.md) — building and composing projections for Decision Models
- [references/tags.md](references/tags.md) — tag-based event correlation deep dive
- [references/examples.md](references/examples.md) — all examples with code, scenarios, and test cases
