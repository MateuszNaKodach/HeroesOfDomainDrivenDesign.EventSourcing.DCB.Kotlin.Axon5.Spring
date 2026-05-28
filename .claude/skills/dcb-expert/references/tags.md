# Tags: The Key to Flexible Event Correlation

## TL;DR

- Tags are explicit references to domain concepts involved in consistency rules
- They enable precise, performant event selection without relying on payload inspection
- While tags add a conceptual layer, their benefits for scalability and correctness outweigh the added complexity

## Understanding the Role of Tags

Two different aspects of events:
- **Event types** — the kind of state change (e.g., `order placed`, `inventory reserved`)
- **Tags** — correlate events with specific instances (e.g., `product:laptop-x1`, `customer:alice-smith`)

Event types tell *what happened*; tags tell *to whom/what*.

A **tag** is a **reference** to a **unique instance** of a **concept** involved in a **domain integrity rule**.

## The Problem: Precise Event Selection

Consider: "A product's available inventory cannot go below zero."

To enforce this invariant, we need exactly the events that affected a specific product's stock levels.

### Missing Events = Broken Invariants

Failing to include relevant events risks violating business rules (e.g., overselling).

### Too Many Events = Scalability Problems

Including too many events creates unnecessarily broad consistency boundaries that block parallel, unrelated decisions.

## Why Not Query by Payload?

Filtering events by payload properties has drawbacks:

1. **Opaque payloads** — Event payloads should remain opaque to the Event Store (could be binary or encrypted)
2. **Complexity overhead** — A query language adds implementation and usage complexity
3. **Performance** — Dynamic queries against schemaless events make indexing difficult
4. **Feature incompleteness** — Any query language will have limitations

DCB query with tags is simpler:
```json
[{
  "event_types": ["AccountRegistered", "AccountClosed", "UsernameChanged"],
  "tags": ["<username-tag>"]
}]
```

## Tag Inference

Tags and event types can be inferred automatically from types using interfaces or dedicated types.

C# example using interfaces:
```csharp
interface ICourseEvent : IDomainEvent { string CourseId { get; } }
interface IStudentEvent : IDomainEvent { string StudentId { get; } }

sealed record StudentSubscribedToCourse(string StudentId, string CourseId)
    : IStudentEvent, ICourseEvent;

// ExtractTags produces: ["student:s1", "course:c1"]
```

TypeScript example using custom types:
```typescript
interface Tagged {
  readonly value: string
  readonly __tagPrefix: string
}
interface CourseId extends Tagged { readonly __tagPrefix: "course" }
interface StudentId extends Tagged { readonly __tagPrefix: "student" }

// extractTags produces: ["student:s1", "course:c1"]
```

## Guidelines for Good Tags

- Tags MUST be derivable from the event payload
- Use prefixes for disambiguation (e.g., `customer:c123`, `order:o-1234`)
- Avoid personal data — hash or anonymize sensitive values like usernames and emails
- When a hard constraint depends on an event, the necessary tags must be present
- Proactively tag values that might become relevant for future constraints

## Benefits

1. **Precise consistency boundaries** — include all necessary data, exclude irrelevant events
2. **Performance optimization** — predictable patterns enable efficient indexing
3. **Automatic inference** — libraries can infer tags from domain model definitions

## Limitations

- The term "tags" carries connotations from other domains (HTML tags, hashtags) that don't perfectly align
- Tags stem from a technical requirement rather than emerging from domain modeling
- They add conceptual overhead to Event Store design
- Tags represent the current best compromise for the event correlation problem, balancing precision, performance, complexity, and flexibility
