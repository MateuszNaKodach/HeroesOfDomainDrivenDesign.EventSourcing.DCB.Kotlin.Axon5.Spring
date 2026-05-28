# Aggregates and DCB

## What is an Aggregate?

> "A cluster of associated objects that we treat as a unit for the purpose of data changes"
> — Eric Evans, 2003

Primary role: enforce consistency by creating a Decision Model to ensure business invariants are enforced under concurrent operations.

## Consistency Mechanisms

### Pessimistic Locking

Lock a resource when accessed, preventing others from modifying it until released.

Problems:
- Potential deadlocks (multiple locks without consistent ordering)
- Prevents ANY other update to locked scope (even unrelated changes)

### Optimistic Locking

Multiple users read and attempt to modify data freely. Updates permitted only if data unchanged since read. Achieved with a version number — `update(course, course.version)` fails if version changed.

## The Aggregate Pattern

Formalizes consistency boundaries by modeling them within the Domain Model. Related entities grouped into an Aggregate with one Aggregate Root as exclusive access point.

Example: `Order` Aggregate where adding/removing line items goes through the `Order` root, ensuring total price invariants.

### Limitations

By design, state changes are confined to a single Aggregate instance per transaction:

> "Any rule that spans Aggregates will not be expected to be always up to date."
> — Eric Evans, 2003

This breaks down when an operation affects invariants of **two** Aggregates. Example:
- Course: cannot be overbooked (capacity limit)
- Student: cannot enroll in more than 10 courses

Subscribing a student affects both. Solutions like larger aggregates (reduces scalability) or sagas (adds complexity, invalid intermediate states) have significant drawbacks.

## Event-Sourced Aggregate

With Event Sourcing, consistency uses optimistic concurrency:
1. Load relevant Events, remember last Event position
2. Decide based on projected state
3. Append new Event with remembered position
4. Event Store appends only if no new Event in the stream since that position
5. On failure, retry

### Event Streams

To enable parallel execution, events are partitioned into sub-streams. Each Event is assigned to a single Stream with a position in that Stream plus a global position.

The Event Store appends only if the last Event *in the same stream* matches the specified position. This makes the Event Stream a natural match for Aggregates.

### Consequences

Event-Sourced Aggregates have even more rigid boundaries than traditional ones — materialized in Event Stream structure. This rigidity makes it harder to adapt the design later. Aggregates also grow as Events accumulate, impacting performance (mitigations like snapshots add complexity).

## Relation to DCB

DCB offers an alternative: enforce consistency for a specific use case rather than hardwiring it into a static Aggregate structure. The boundary is established at **runtime**, tailored to a single interaction.

Only Events necessary to evaluate invariants are loaded. DCB constructs a Decision Model dynamically, just for the duration of an operation.

### "Killing the Aggregate"

DCB is not a rejection of the Aggregate pattern but an **evolution**. It preserves the core intent when viewed from a single interaction's perspective. The construct is called a **Decision Model** to avoid overloading the term "Aggregate."

> *The Aggregate is dead, long live the Aggregate*

## Conclusion

- Think about consistency boundaries and hard vs soft constraints
- The Aggregate pattern makes consistency part of the Domain Model
- It is rigid — especially with Event-Sourced Aggregates
- DCB enforces explicit boundaries per use case
- DCB is more flexible because Decision Models are composed from Projections
- DCB can be used without breaking the Aggregate pattern
