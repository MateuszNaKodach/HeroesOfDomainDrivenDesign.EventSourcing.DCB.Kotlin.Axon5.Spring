# DCB Specification

This document defines the minimal feature set an Event Store must provide to be DCB compliant.

Implementations are not required to use the same terms or function/field names — as long as they offer equivalent functionality (e.g., `read()` could be `getEvents()` and `failIfEventsMatch` could be `referenceQuery`).

## Reading Events

The Event Store:
- MUST provide a way to filter Events based on their Event Type and/or Tags (see Query)
- SHOULD provide a way to read Events from a given starting Sequence Position
- MAY provide further filter options (ordering, limiting)

Pseudo-code interface:

```
EventStore {
  read(query: Query, options?: ReadOptions): SequencedEvents
}
```

`SequencedEvents` represents an iterable or reactive stream of Sequenced Events.

## Writing Events

The Event Store:
- MUST provide a way to atomically persist one or more Events
- MUST fail if the Event Store contains at least one Event matching the Append Condition, if specified

Pseudo-code interface:

```
EventStore {
  append(events: Events|Event, condition?: AppendCondition): void
}
```

## Concepts

### Query

Describes constraints that must be matched by Events in the Event Store. Filters Events by Type and/or Tags.

- MUST contain a set of Query Items with at least one item, or represent a query that matches all Events
- All Query Items are combined with OR (adding more items returns more Events)

Factory methods:

```
Query.fromItems(items)
Query.all()
```

### Query Item

Each item targets Events by Type and/or Tags. An Event matches a Query Item when:
- The Event Type matches ONE of the provided Types
- The Event Tags contain ALL of the specified Tags

Example query matching Events that are either:
- of type `EventType1` OR `EventType2`
- tagged `tag1` AND `tag2`
- of type `EventType2` OR `EventType3` AND tagged `tag1` AND `tag3`

```json
{
  "items": [
    { "types": ["EventType1", "EventType2"] },
    { "tags": ["tag1", "tag2"] },
    { "types": ["EventType2", "EventType3"], "tags": ["tag1", "tag3"] }
  ]
}
```

### Sequenced Event

Contains or embeds all information of the original Event plus its Sequence Position assigned during `append()`.

- MUST contain the Sequence Position
- MUST contain the Event
- MAY contain further fields (metadata)

```json
{
  "event": { ... },
  "position": 1234
}
```

### Sequence Position

Assigned when an Event is appended:
- MUST be unique in the Event Store
- MUST be monotonically increasing
- MAY contain gaps

### Events

A set of Event instances passed to `append()`:
- MUST not be empty
- MUST be iterable

### Event

- MUST contain an Event Type
- MUST contain Event Data
- MAY contain Tags
- MAY contain further fields (metadata)

```json
{
  "type": "SomeEventType",
  "data": "{\"some\":\"data\"}",
  "tags": ["tag1", "tag2"]
}
```

### Event Type

Type of the event, used to filter Events in the Query.

### Event Data

Opaque payload of an Event.

### Tags

A set of Tags:
- SHOULD not contain multiple Tags with the same value

### Tag

Adds domain-specific metadata to an event, allowing for custom partitioning. Usually represents a domain concept (e.g., `product:p123`).

- MAY represent a key/value pair (irrelevant to Event Store)

### Append Condition

Enforces consistency between building the Decision Model and appending events.

- MUST contain a `failIfEventsMatch` Query
- MAY contain an `after` Sequence Position
  - Represents the highest position the client was aware of while building the Decision Model
  - The Event Store MUST ignore Events before the specified position while checking the condition
  - When `after` is present, `failIfEventsMatch` is typically the same Query used for building the Decision Model
  - If omitted, no Events are ignored (fails if ANY Event matches)

```
AppendCondition {
  failIfEventsMatch: Query
  after?: SequencePosition
}
```
