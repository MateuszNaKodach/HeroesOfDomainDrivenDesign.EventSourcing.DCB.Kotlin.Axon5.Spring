---
name: em2code-axon5-write-slice
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

# Axon Framework 5 Write Slice Implementation

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

**Optionally**, the slice details may contain:
- `## Business Rules` — invariants and preconditions for `decide()` implementation
- `## Scenarios (GWTs)` — Given-When-Then acceptance criteria using `:::element` blocks

When GWT scenarios are present, each numbered scenario (e.g., `### 1. build for the first time`) maps 1:1 to a test method. Properties in `:::element` blocks are **only rule-relevant** — fill remaining constructor params with test fixture values (random IDs, hardcoded domain values).

If the slice details contain `## Implementation Guidelines`, **follow them** — they describe specific technical requirements (e.g., integrate with a payment provider, create an S3 bucket, use a specific library) that go beyond the standard slice pattern.

### Input: Axon Framework 4 Code

Read the AF4 source: command class, aggregate class, events, domain rules, REST API.
See [references/af4-input-mapping.md](references/af4-input-mapping.md) for concept-by-concept translation.

**If requirements are unclear, ask the user before proceeding.**

### Determine Interaction Trigger

If the input does not make it clear how the command will be triggered, use `AskUserQuestion` to ask:

> How will this command be triggered?
> - **REST API** — exposed via HTTP endpoint (add Presentation section + REST API test)
> - **Automation only** — dispatched internally by an event handler (no REST API, no Presentation section)
> - **Both** — exposed via REST API and also dispatched by automations

This determines whether to include the Presentation section (`@RestController`) and REST API test. Commands used only
by automations don't need an HTTP endpoint. Skip the Presentation section and REST API test in that case.

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
////////// Presentation (only if REST API trigger)
///////////////////////////////////////////
// 8. @RestController (Body DTO, sends command via CommandGateway)
```

Key rules for each component:

**Command**: No `@TargetAggregateIdentifier`. Plain data class. Public. Add `@get:JvmName` on properties whose names
match their type pattern. Annotate with `@Command(namespace = "<BoundedContext>", name = "<CommandName>", version = "1.0.0")` — import from `org.axonframework.messaging.commandhandling.annotation.Command`.

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

## Value Objects with Kotlin `value class`

When command or event properties represent constrained domain concepts (e.g. day 1–7, week 1–4, month ≥ 1), prefer
wrapping them in `@JvmInline value class` types with validation in the `init` block. This pushes invariant enforcement
to construction time — invalid values cannot exist — and makes the domain model self-documenting.

```kotlin
@JvmInline
value class Day(val raw: Int) {
    init {
        require(raw in 1..7) { "Day must be between 1 and 7, got $raw" }
    }
    val isLast: Boolean get() = raw == 7
    fun next(): Day = Day((raw % 7) + 1)
}
```

**When to introduce a value class:**

- The property has validation constraints (range, format, non-blank)
- The same concept appears in command, event, and state — a value class avoids duplicating validation
- Using primitives would allow invalid states (e.g. `day = 99`)

**Add domain operations (`next()`, `isLast`, etc.) to value classes** so that `decide()` works entirely with value
objects and never unwraps to `.raw`. The `decide()` function is pure domain logic — it should speak the domain language,
not escape to primitives. Reserve `.raw` for boundaries: REST layer, cross-BC mapping, serialization.

**Where to place them:**

- In the bounded context's write package (e.g. `calendar.write.Day`) by default
- Move to `shared.domain.valueobjects` only when 3+ bounded contexts use them — same threshold as `Resources`/`Quantity`
- Generic names (`Day`, `Week`, `Month`) risk collisions across BCs with different semantics, so keep them scoped until
  reuse is proven

## Step 4: Ensure Events Exist

Check target project's `events/` package. If events don't exist, create them following project conventions:

- Sealed interface hierarchy (e.g., `DwellingEvent : HeroesEvent`)
- `@EventTag` on tag properties
- Value object types for properties
- Annotate each event with `@Event(namespace = "<BoundedContext>", name = "<EventName>", version = "1.0.0")` — import from `org.axonframework.messaging.eventhandling.annotation.Event`. The namespace is the bounded context name (e.g., `"CreatureRecruitment"`, `"Calendar"`, `"Armies"`), the name matches the class name.

## Step 5: Add Feature Flag

Add `@ConditionalOnProperty(prefix = "slices.{context}", name = ["write.{feature}.enabled"])` to entity,
handler/@config, and REST classes. Update ALL of these files:

- `application.yaml` — set `enabled: true`
- `application-test.yaml` — set `enabled: false` (slices disabled by default in tests; individual tests opt-in via
  `@TestPropertySource`)
- `META-INF/additional-spring-configuration-metadata.json` — add property entry with name, type (`java.lang.Boolean`),
  and description

## Step 6: Implement Tests

### 6a. Slice Tests (domain logic via Given-When-Then)

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

#### Mapping Event Model GWT Scenarios to Tests

When the slice details contain `## Scenarios (GWTs)`, map each scenario to a test method:

| GWT Element | Test Code |
|---|---|
| Scenario name (e.g., `### 1. build for the first time`) | Test method name: `` `given not built dwelling, when build, then built` `` |
| `NOTHING` in Given | `noPriorActivity()` |
| `:::element event` in Given | `event(EventClass(...))` in `Given { }` block |
| `:::element command` in When | `command(CommandClass(...), gameMetadata)` in `When { }` block |
| `:::element event` in Then | `events(EventClass(...))` + `resultMessagePayload(Success)` |
| `:::element hotspot` in Then | `resultMessagePayload(Failure("message"))` |
| Failure event in Then (e.g., `PaymentRejected`) | `events(PaymentRejected(...))` — domain failure event, not exception |
| `NOTHING` in Then | `noEvents()` + `resultMessagePayload(Success)` — idempotent |

**Property mapping**: GWT properties are rule-relevant only. Fill remaining constructor params with test fixtures.

Example — GWT scenario "try to build already built" shows only `dwellingId: portal-of-glory`:
```kotlin
// GWT has dwellingId only — creatureId and costPerTroop are test fixtures
val dwellingId = DwellingId.random()
val creatureId = CreatureId("angel")          // not in GWT, fixture value
val costPerTroop = Resources.of(...)          // not in GWT, fixture value

Given { events(DwellingBuilt(dwellingId, creatureId, costPerTroop)) }
When  { command(BuildDwelling(dwellingId, creatureId, costPerTroop)) }
Then  { resultMessagePayload(Success); noEvents() }
```

When the same property value appears in Given and When (e.g., `dwellingId: portal-of-glory`), use the same variable to make the relationship explicit.

### 6b. REST API Tests (presentation layer)

Tests the REST controller in isolation — mocked `CommandGateway`, no Axon Server, no event store. Uses
`@RestAssuredMockMvcTest` + `@AxonGatewaysMockTest` with RestAssured MockMvc.

Cover two scenarios:

- **Command success** → `204 No Content`
- **Command failure** → `400 Bad Request` with JSON error body `{"message": "..."}`

```kotlin
@RestAssuredMockMvcTest
@AxonGatewaysMockTest
@TestPropertySource(properties = ["slices.{context}.write.{feature}.enabled=true"])
internal class FeatureNameRestApiTest @Autowired constructor(val gateways: AxonGatewaysMock) {

    private val gameId = GameId.random()
    private val playerId = PlayerId.random()

    @Test
    fun `command success - returns 204 No Content`() {
        gateways.assumeCommandReturns<FeatureCommand>(Success)

        Given {
            pathParam("gameId", gameId.raw)
            // other path params...
            header(Headers.PLAYER_ID, playerId.raw)
            contentType(ContentType.JSON)
            body("""{ ... }""")
        } When {
            async().post("/games/{gameId}/...")  // or put(), matching the slice's HTTP method
        } Then {
            statusCode(HttpStatus.NO_CONTENT.value())
        }
    }

    @Test
    fun `command failure - returns 400 Bad Request`() {
        gateways.assumeCommandReturns<FeatureCommand>(Failure("Error message"))

        Given {
            // same setup...
        } When {
            async().post("/games/{gameId}/...")
        } Then {
            statusCode(HttpStatus.BAD_REQUEST.value())
            contentType(ContentType.JSON)
            body("message", equalTo("Error message"))
        }
    }
}
```

Key points:

- `@RestAssuredMockMvcTest` — composes `@WebMvcTest` + RestAssured setup (no `@BeforeEach` boilerplate)
- `@AxonGatewaysMockTest` — provides mocked `CommandGateway`, `QueryGateway`, `Clock` via `AxonGatewaysMock` bean
- `gateways.assumeCommandReturns<T>(result)` — stubs by command type, covers all AF5 `CommandGateway` dispatch styles
- `async().post(...)` / `async().put(...)` — required because controllers return `CompletableFuture`
- Request body uses raw JSON (primitives, not value objects) — mirrors what the HTTP client sends
- Test file lives next to the slice test: `{feature}/FeatureNameRestApiTest.kt`

## References

- [AF5 Write Slice Patterns](references/af5-write-slice-patterns.md) - Complete simple and advanced patterns with full
  examples and testing
- [AF4 Input Mapping](references/af4-input-mapping.md) - When input is Axon Framework 4 code: concept-by-concept
  translation guide
