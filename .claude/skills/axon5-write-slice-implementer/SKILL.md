---
name: axon5-write-slice-implementer
description: >
  Implement Event Sourcing write slices using Axon Framework 5, Vertical Slice Architecture, and
  Event Modeling patterns. A write slice is: Command to decide to Events to evolve to State.
  Use when: (1) implementing a new write slice / command handler in an AF5 project,
  (2) migrating/porting a write slice from Axon Framework 4 (Java or Kotlin) to AF5,
  (3) user provides a specification, Event Modeling artifact, existing tests, or natural language
  description of a command and asks to implement it,
  (4) user says "implement", "create", "add", "migrate", "port" a write slice, command handler,
  or aggregate behavior in an Axon Framework 5 / Vertical Slice Architecture project.
  Understands AF4 aggregate-based input as one possible source format.
---

# Axon Framework 5 Write Slice Implementer

## Step 0: Discover Target Project Conventions

Before writing any code, read the target project's CLAUDE.md and explore at least one existing write slice. Conventions
vary. Look for:

- File naming (`FeatureName.Slice.kt` vs separate files)
- Section markers (Domain / Application / Presentation comment blocks)
- Visibility modifiers on State, Command, handler, REST classes
- Event definitions (value objects vs primitives, marker interfaces, `@EventTag`)
- Metadata handling (`GameMetadata`, `AxonMetadata`)
- REST conventions (URL patterns, headers, response types)
- Feature flag patterns (`@ConditionalOnProperty`)
- Test patterns (unit test fixtures vs Spring integration tests)
- Imports and package structure

## Step 1: Understand the Input

Input can arrive in many forms. Extract these elements regardless of format:

| Element                  | What to extract                                                      |
|--------------------------|----------------------------------------------------------------------|
| **Command**              | Name, properties, which property identifies the consistency boundary |
| **Events**               | Names, properties, which events this command produces                |
| **Business rules**       | Preconditions, invariants, idempotency behavior                      |
| **State needed**         | What prior events must be replayed to evaluate rules                 |
| **Consistency boundary** | Single tag (one stream) or multi-tag (DCB across streams)            |

### Input: Specification / Natural Language

Extract command name, events, and business rules directly from the description.

### Input: Existing Tests

Analyze test file to understand expected behavior: commands sent, events asserted, failure cases.

### Input: Event Modeling Artifact

The write slice (blue stripe) shows: Command on left, Events on right, State (read model) below.

### Input: Axon Framework 4 Code

Read the AF4 source: command class, aggregate class, events, domain rules, REST API.
See [references/af4-input-mapping.md](references/af4-input-mapping.md) for concept-by-concept translation.

**If requirements are unclear, ask the user before proceeding.**

## Step 2: Choose the AF5 Pattern

**Spring Boot** — entity and handler auto-discovered by Spring:

- `@EventSourced(tagKey = "tagName")` on entity (single tag) or `@EventSourcedEntity` + `@EventCriteriaBuilder` (
  multi-tag)
- `@Component` on handler class
- Auto-registered by Spring
- Tested with Spring Boot test (`@HeroesAxonSpringBootTest`)
- **Default choice** when the project uses Spring Boot

**Explicit Registration** — entity and handler registered manually via `@Configuration`:

- `@EventSourcedEntity` on entity
- `@EventCriteriaBuilder` companion method on entity (single-tag: takes id value object; multi-tag: takes composite ID)
- `@Configuration` class with `EntityModule` + `CommandHandlingModule` beans
- Handler class is NOT `@Component`
- Tested with non-Spring Boot unit test (`axonTestFixture` + `configSlice`)
- Use when: user explicitly asks for unit tests without Spring context

Both patterns support single-tag and multi-tag (DCB). The difference is registration mechanism, not tag cardinality.

See [references/af5-write-slice-patterns.md](references/af5-write-slice-patterns.md) for complete patterns.

## Step 3: Implement the Slice File

Create `FeatureName.Slice.kt` with three sections (adapt to project conventions):

```
////////////////////////////////////////////
////////// Domain
///////////////////////////////////////////
// 1. Command data class (public)
// 2. State data class (private) + initialState
// 3. decide(command, state): List<Event>  -- pure function
// 4. evolve(state, event): State          -- pure function

////////////////////////////////////////////
////////// Application
///////////////////////////////////////////
// 5. @EventSourced entity class (wraps State, has @EventSourcingHandler methods)
// 6. @CommandHandler component (calls decide, appends events via EventAppender)
// 7. @Configuration if advanced pattern

////////////////////////////////////////////
////////// Presentation
///////////////////////////////////////////
// 8. @RestController (Body DTO, sends command via CommandGateway)
```

Key rules for each component:

**Command**: No `@TargetAggregateIdentifier`. Plain data class. Public. Add `@get:JvmName` on properties whose names
match their type pattern.

**State**: Private. Immutable data class. Contains ONLY fields needed by `decide()`. Companion `initialState` val.

**decide()**: Private standalone function. Takes `(command, state)`, returns event(s). No side effects. Enforce rules
here: throw `IllegalStateException` for violations, return `emptyList()` for idempotent no-ops.

**evolve()**: Private standalone function. Takes `(state, event)`, returns new State. Uses `when` + `state.copy()`.
Handles ALL event types this slice needs for state reconstruction.

**Entity**: Private class wrapping `val state: State`. Constructor with `@EntityCreator` returns `initialState`. Each
`@EventSourcingHandler` returns new entity instance via `evolve()`.

**Handler**: `@CommandHandler` method receives `(command, AxonMetadata, @InjectEntity entity, EventAppender)`. Returns
`CommandHandlerResult` via `resultOf { }`. Calls `decide()`, appends events.

**REST**: Private class. `CompletableFuture<ResponseEntity<Any>>` return type. Constructs command with value objects,
sends via `commandGateway.send(command.asCommandMessage(metadata)).resultAs(...).toResponseEntity()`.

## Step 4: Ensure Events Exist

Check target project's `events/` package. If events don't exist, create them following project conventions:

- Sealed interface hierarchy (e.g., `DwellingEvent : HeroesEvent`)
- `@EventTag` on tag properties
- Value object types for properties

## Step 5: Add Feature Flag

Add `@ConditionalOnProperty(prefix = "slices.{context}", name = ["write.{feature}.enabled"])` to entity,
handler/@config, and REST classes. Update ALL of these files:

- `application.yaml` — set `enabled: true`
- `application-test.yaml` — set `enabled: false` (slices disabled by default in tests; individual tests opt-in via
  `@TestPropertySource`)
- `META-INF/additional-spring-configuration-metadata.json` — add property entry with name, type (`java.lang.Boolean`),
  and description

## Step 6: Implement Tests

Two test approaches exist (see [references/af5-write-slice-patterns.md](references/af5-write-slice-patterns.md) "
Testing" section):

- **Spring Boot test** — uses `@HeroesAxonSpringBootTest` + `springTestFixture(configuration)`. Works with the Spring
  Boot pattern (`@EventSourced` + `@Component`). Requires `@TestPropertySource` to enable the slice.
- **Non-Spring Boot test** — uses `axonTestFixture(configSlice { ... })`. Works with the Explicit Registration pattern (
  `@EventSourcedEntity` + `@Configuration`). No Spring context needed.

Cover these scenarios:

- **Happy path**: no prior state, command produces expected events
- **Idempotency**: duplicate command produces no events
- **Rule violations**: invalid state returns `CommandHandlerResult.Failure`
- **State transitions**: prior events change behavior

## References

- [AF5 Write Slice Patterns](references/af5-write-slice-patterns.md) - Complete simple and advanced patterns with full
  examples and testing
- [AF4 Input Mapping](references/af4-input-mapping.md) - When input is Axon Framework 4 code: concept-by-concept
  translation guide
