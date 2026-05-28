# Projections: Building Decision Models

## What is a Projection

Minimal definition (Greg Young, 2013):

```typescript
type Projection<S, E> = (state: S, event: E) => S
```

A pure fold over events. Must be side-effect-free for deterministic results.

## DCB Projection Structure

A DCB projection has:

```js
{
  initialState: <default value>,
  handlers: {
    EventType1: (state, event) => newState,
    EventType2: (state, event) => newState,
  },
  tagFilter: [`concept:${id}`],
}
```

- `handlers` — map of event type to handler function. Event types are derived from handler keys for querying.
- `tagFilter` — tags for filtering events related to specific instances
- `initialState` — starting state before any events

## Filtering Events

### By Type

Handler keys define which event types are relevant. Only events matching handler keys need to be loaded:

```js
const projection = {
  initialState: 0,
  handlers: {
    CourseDefined: (state, event) => state + 1,
    CourseArchived: (state, event) => state - 1,
  }
}
// Only loads CourseDefined and CourseArchived events
```

### By Tags

Tags filter events to specific entity instances. Without tags, ALL events of matching types would be loaded:

```js
const CourseExistsProjection = (courseId) => ({
  initialState: false,
  handlers: {
    CourseDefined: (state, event) => true,
    CourseArchived: (state, event) => false,
  },
  tagFilter: [`course:${courseId}`],
})
```

## Projection Factory Pattern

Since tagFilter depends on the specific entity instance, create factories:

```js
const CourseExistsProjection = (courseId) =>
  createProjection({
    initialState: false,
    handlers: {
      CourseDefined: (state, event) => true,
      CourseArchived: (state, event) => false,
    },
    tagFilter: [`course:${courseId}`],
  })
```

The resulting projection object provides:
```typescript
type Projection<S> = {
  get initialState(): S
  apply(state: S, event: SequencedEvent): S
  get query(): Query  // derived from handlers + tagFilter
}
```

## Composing Projections

Avoid creating monolithic projections that answer multiple questions. Instead, compose small projections:

**Bad** — greedy projection that consumes more events than needed:
```js
const CourseProjection = (courseId) =>
  createProjection({
    initialState: { courseExists: false, courseCapacity: 0 },
    handlers: {
      CourseDefined: (state, event) => ({
        courseExists: true,
        courseCapacity: event.data.capacity,
      }),
      CourseCapacityChanged: (state, event) => ({
        ...state,
        courseCapacity: event.data.newCapacity,
      }),
    },
    tagFilter: [`course:${courseId}`],
  })
```

**Good** — compose small focused projections:
```js
const compositeProjection = composeProjections({
  courseExists: CourseExistsProjection("c1"),
  courseTitle: CourseTitleProjection("c1"),
})
```

The composite state is an object with a key for every sub-projection. The query matches only events relevant to at least one sub-projection.

## Building Decision Models

`buildDecisionModel` composes projections dynamically for a DCB Event Store:

```js
const { state, appendCondition } = buildDecisionModel(eventStore, {
  courseExists: CourseExistsProjection("c1"),
  courseCapacity: CourseCapacityProjection("c1"),
})

// state.courseExists → boolean
// state.courseCapacity → number
// appendCondition → pass to eventStore.append() for consistency
```

The `appendCondition` can be passed directly to `append()` to enforce consistency.

## Why Composition Matters

1. **Reduced complexity** — each projection answers one question
2. **Precise boundaries** — avoids consuming more events than needed
3. **Reusability** — same projection used in multiple Decision Models
4. **Dynamic boundaries** — compose different projections per use case (this is the "dynamic" in DCB)
