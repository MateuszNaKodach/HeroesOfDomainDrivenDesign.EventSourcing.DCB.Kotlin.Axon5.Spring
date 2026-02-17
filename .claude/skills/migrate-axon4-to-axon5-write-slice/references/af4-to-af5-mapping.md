# AF4 to AF5 Write Slice: Detailed Mapping

Concept-by-concept transformation rules. AF4 examples may be Java or Kotlin. AF5 target is the project's target language
and conventions.

## Table of Contents

1. [Architecture: Aggregate to Vertical Slice](#1-architecture-aggregate-to-vertical-slice)
2. [Command Class](#2-command-class)
3. [State Management](#3-state-management)
4. [decide() Function](#4-decide-function)
5. [evolve() Function](#5-evolve-function)
6. [Event Sourced Entity](#6-event-sourced-entity)
7. [Command Handler](#7-command-handler)
8. [Event Publishing](#8-event-publishing)
9. [Domain Rules](#9-domain-rules)
10. [Events](#10-events)
11. [REST API](#11-rest-api)
12. [Configuration and Feature Flags](#12-configuration-and-feature-flags)
13. [Simple Pattern: Full Example](#13-simple-pattern-full-example)
14. [Advanced Pattern: Multi-Tag DCB](#14-advanced-pattern-multi-tag-dcb)
15. [Testing](#15-testing)

---

## 1. Architecture: Aggregate to Vertical Slice

**AF4**: One `@Aggregate` class handles ALL commands for the aggregate. Each command has its own file for the command
class, but `@CommandHandler` and `@EventSourcingHandler` methods live together in the aggregate.

**AF5**: Each command gets its own vertical slice file (`FeatureName.Slice.kt`). The slice contains ONLY the handlers
and state relevant to that ONE command. Multiple slices can listen to the same events but maintain independent state
projections.

Key insight: In AF4, if `Dwelling` aggregate handles `BuildDwelling`, `IncreaseAvailableCreatures`, and
`RecruitCreature`, in AF5 these become three separate files, each with their own `State`, `decide()`, `evolve()`, and
entity class.

## 2. Command Class

**AF4:**

```java
public record BuildDwelling(
    @TargetAggregateIdentifier DwellingId dwellingId,
    CreatureId creatureId,
    Resources costPerTroop
) implements DwellingCommand { }
```

**AF5:**

```kotlin
data class BuildDwelling(
    @get:JvmName("getDwellingId")
    val dwellingId: DwellingId,
    val creatureId: CreatureId,
    val costPerTroop: Resources,
)
```

Changes:

- Remove `@TargetAggregateIdentifier` - AF5 uses tag-based routing, not aggregate identifier routing
- Remove command marker interface (`DwellingCommand`) - no longer needed since there's no single aggregate to route to
- Add `@get:JvmName("get...")` on ID properties that conflict with Kotlin's generated getter names (needed when property
  name matches the type name pattern)
- Command is `public` (not `private`) - it's the slice's public API

## 3. State Management

**AF4**: Mutable fields in the aggregate class.

```java
@Aggregate
public class Dwelling {
    @AggregateIdentifier
    public DwellingId dwellingId;
    public CreatureId creatureId;
    public Resources costPerTroop;
    public Amount availableCreatures;
}
```

**AF5**: Immutable `private data class` + `initialState` val.

```kotlin
private data class State(val isBuilt: Boolean)

private val initialState = State(isBuilt = false)
```

Changes:

- State includes ONLY fields needed by THIS slice's `decide()` function
- State is immutable (`data class` with `copy()`)
- Each slice projects its own view of state - don't copy the entire aggregate's fields
- Mark `private` - state is internal to the slice

## 4. decide() Function

**AF4**: `@CommandHandler` method inside aggregate, mutates state via `AggregateLifecycle.apply()`.

```java
@CommandHandler
@CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
void decide(BuildDwelling command) {
    new OnlyNotBuiltBuildingCanBeBuild(dwellingId).verify();
    apply(DwellingBuilt.event(command.dwellingId(), command.creatureId(), command.costPerTroop()));
}
```

**AF5**: Pure `private fun` outside any class, returns events.

```kotlin
private fun decide(command: BuildDwelling, state: State): List<HeroesEvent> {
    if (state.isBuilt) {
        return emptyList()
    }
    return listOf(
        DwellingBuilt(
            dwellingId = command.dwellingId,
            creatureId = command.creatureId,
            costPerTroop = command.costPerTroop
        )
    )
}
```

Changes:

- Standalone pure function, not a method on an aggregate class
- Takes `(command, state)` as parameters, returns `List<Event>` (or single event)
- No `AggregateLifecycle.apply()` - just return events
- No `@CommandHandler` annotation - that goes on the separate handler class
- No `@CreationPolicy` - AF5 uses `@EntityCreator` on the entity constructor
- Domain rule verification becomes inline logic (if/throw or if/return empty)

## 5. evolve() Function

**AF4**: `@EventSourcingHandler` methods inside the aggregate, mutate fields.

```java
@EventSourcingHandler
void evolve(DwellingBuilt event) {
    this.dwellingId = new DwellingId(event.dwellingId());
    this.creatureId = new CreatureId(event.creatureId());
    this.costPerTroop = Resources.fromRaw(event.costPerTroop());
    this.availableCreatures = Amount.zero();
}
```

**AF5**: Pure `private fun` outside any class, returns new state.

```kotlin
private fun evolve(state: State, event: DwellingEvent): State = when (event) {
    is DwellingBuilt -> state.copy(isBuilt = true)
    else -> state
}
```

Changes:

- Standalone pure function, returns new `State`
- Uses `when` expression with pattern matching on event types
- Only handles events relevant to THIS slice's state
- Uses `state.copy()` for immutable updates
- `else -> state` ignores irrelevant events

## 6. Event Sourced Entity

**AF4**: `@Aggregate` class IS the entity. One class, all handlers.

```java
@Aggregate(snapshotTriggerDefinition = "dwellingSnapshotTrigger")
public class Dwelling {
    @AggregateIdentifier
    public DwellingId dwellingId;
    Dwelling() { } // required by Axon
}
```

### Simple Pattern

**AF5 (single tag)**: Separate `@EventSourced` class wrapping the `State`.

```kotlin
@EventSourced(tagKey = EventTags.DWELLING_ID) // consistency boundary
private class BuildDwellingEventSourcedState private constructor(val state: State) {
    @EntityCreator
    constructor() : this(initialState)

    @EventSourcingHandler
    fun evolve(event: DwellingBuilt) = BuildDwellingEventSourcedState(evolve(state, event))
}
```

### Advanced Pattern

**AF5 (multi-tag / DCB)**: `@EventSourcedEntity` with `@EventCriteriaBuilder`.

```kotlin
@EventSourcedEntity
private class RecruitCreatureEventSourcedState private constructor(val state: State) {
    @EntityCreator
    constructor() : this(initialState)

    @EventSourcingHandler
    fun evolve(event: DwellingBuilt) = RecruitCreatureEventSourcedState(evolve(state, event))
    @EventSourcingHandler
    fun evolve(event: AvailableCreaturesChanged) = RecruitCreatureEventSourcedState(evolve(state, event))

    companion object {
        @JvmStatic
        @EventCriteriaBuilder
        fun resolveCriteria(compositeId: CompositeId) =
            EventCriteria.either(
                EventCriteria.havingTags(Tag.of(EventTags.TAG_A, compositeId.idA.raw))
                    .andBeingOneOfTypes(EventA::class.java.getName(), EventB::class.java.getName()),
                EventCriteria.havingTags(Tag.of(EventTags.TAG_B, compositeId.idB.raw))
                    .andBeingOneOfTypes(EventC::class.java.getName(), EventD::class.java.getName())
            )
    }
}
```

Changes:

- `@Aggregate` becomes `@EventSourced(tagKey = ...)` or `@EventSourcedEntity`
- `@AggregateIdentifier` is removed - routing is via tags
- No-arg constructor uses `@EntityCreator` returning `initialState`
- Each `@EventSourcingHandler` returns a NEW instance (immutable) wrapping `evolve(state, event)`
- Entity class is `private`
- Entity has ONE public property: `val state: State`

## 7. Command Handler

**AF4**: `@CommandHandler` methods are inside the aggregate.

**AF5**: Separate `@Component` class (or plain class for advanced pattern).

### Simple Pattern

```kotlin
@Component
private class BuildDwellingCommandHandler {
    @CommandHandler
    fun handle(
        command: BuildDwelling,
        metadata: AxonMetadata,
        @InjectEntity(idProperty = EventTags.DWELLING_ID) eventSourced: BuildDwellingEventSourcedState,
        eventAppender: EventAppender
    ): CommandHandlerResult = resultOf {
        val events = decide(command, eventSourced.state)
        eventAppender.append(events.asEventMessages(metadata))
        events.toCommandResult()
    }
}
```

### Advanced Pattern

```kotlin
private class RecruitCreatureCommandHandler {  // no @Component!
    @CommandHandler
    fun handle(
        command: RecruitCreature,
        metadata: AxonMetadata,
        @InjectEntity(idProperty = "recruitmentId") eventSourced: RecruitCreatureEventSourcedState,
        eventAppender: EventAppender
    ): CommandHandlerResult = resultOf {
        val events = decide(command, eventSourced.state)
        eventAppender.append(events.asEventMessages(metadata))
        events.toCommandResult()
    }
}
```

Key elements:

- `@InjectEntity(idProperty = ...)` - replaces `@TargetAggregateIdentifier` routing. The `idProperty` names the command
  property used to resolve the entity.
- `EventAppender` - replaces `AggregateLifecycle.apply()`
- `AxonMetadata` - injected automatically, forwarded to events
- `CommandHandlerResult` return type - wraps success/failure
- `resultOf { }` - catches exceptions and converts to `CommandHandlerResult.Failure`
- For single event: `events.asEventMessage(metadata)`. For list: `events.asEventMessages(metadata)`

## 8. Event Publishing

**AF4:**

```java
apply(DwellingBuilt.event(...));  // AggregateLifecycle.apply()
```

**AF5:**

```kotlin
// Multiple events
eventAppender.append(events.asEventMessages(metadata))

// Single event
eventAppender.append(event.asEventMessage(metadata))
```

The `EventAppender` is injected into the `@CommandHandler` method. Events carry metadata (gameId, playerId, etc.) via
the `AxonMetadata` parameter.

## 9. Domain Rules

**AF4**: Separate `DomainRule` record classes with `isViolated()`, `message()`, and `verify()`.

```java
public record OnlyNotBuiltBuildingCanBeBuild(DwellingId dwellingId) implements DomainRule {
    @Override public boolean isViolated() { return dwellingId != null; }
    @Override public String message() { return "Only not built building can be build"; }
}
// Usage:
new OnlyNotBuiltBuildingCanBeBuild(dwellingId).verify();
```

**AF5**: Inline in `decide()` function. Two patterns:

*Idempotent (return empty):*

```kotlin
if (state.isBuilt) {
    return emptyList()
}
```

*Failure (throw):*

```kotlin
if (!state.isBuilt) {
    throw IllegalStateException("Only built dwelling can have available creatures")
}
```

When to use which:

- **Return empty**: For idempotent operations (building an already-built dwelling)
- **Throw**: For genuine rule violations (operating on non-existent entity)
- **Return FailureEvent**: When failure should be recorded as an event (check target project conventions)

## 10. Events

**AF4**: Events use primitives, static factory methods.

```java
public record DwellingBuilt(String dwellingId, String creatureId, Map<String, Integer> costPerTroop)
    implements DwellingEvent { }
```

**AF5**: Events use value objects, `@EventTag` annotation, sealed interface hierarchy.

```kotlin
data class DwellingBuilt(
    override val dwellingId: DwellingId,  // value object, not String
    val creatureId: CreatureId,
    val costPerTroop: Resources
) : DwellingEvent

sealed interface DwellingEvent : HeroesEvent {
    @get:EventTag(EventTags.DWELLING_ID)
    val dwellingId: DwellingId
}
```

Changes:

- Primitives become value objects (`String` -> `DwellingId`, `Map<String, Integer>` -> `Resources`)
- `@EventTag` annotation on the tag property (on the sealed interface and/or event class)
- Sealed interface hierarchy: `DwellingEvent : HeroesEvent : DomainEvent`
- Events stored in `events/` package within the bounded context

## 11. REST API

**AF4:**

```java
@RestController
@RequestMapping("games/{gameId}")
class BuildDwellingRestApi {
    private final CommandGateway commandGateway;
    @PutMapping("/dwellings/{dwellingId}")
    CompletableFuture<Void> putDwellings(...) {
        var command = BuildDwelling.command(...);
        return commandGateway.send(command, GameMetaData.with(gameId, playerId));
    }
}
```

**AF5:**

```kotlin
@RestController
@RequestMapping("games/{gameId}")
private class BuildDwellingRestApi(private val commandGateway: CommandGateway) {
    @JvmRecord
    data class Body(val creatureId: String, val costPerTroop: Map<String, Int>)

    @PutMapping("/dwellings/{dwellingId}")
    fun putDwellings(
        @RequestHeader(Headers.PLAYER_ID) playerId: String,
        @PathVariable gameId: String,
        @PathVariable dwellingId: String,
        @RequestBody requestBody: Body
    ): CompletableFuture<ResponseEntity<Any>> {
        val command = BuildDwelling(
            DwellingId(dwellingId),
            CreatureId(requestBody.creatureId),
            Resources.of(requestBody.costPerTroop)
        )
        val metadata = GameMetadata.with(GameId(gameId), PlayerId(playerId))
        val message = command.asCommandMessage(metadata)
        return commandGateway.send(message)
            .resultAs(CommandHandlerResult::class.java)
            .toResponseEntity()
    }
}
```

Changes:

- Class is `private`
- Return `CompletableFuture<ResponseEntity<Any>>` instead of `CompletableFuture<Void>`
- Use `command.asCommandMessage(metadata)` to attach metadata
- Use `commandGateway.send(message).resultAs(CommandHandlerResult::class.java).toResponseEntity()`
- Construct command directly with value objects (no static factory)
- Add `@ConditionalOnProperty` matching the slice config

## 12. Configuration and Feature Flags

### Simple Pattern

No explicit `@Configuration` needed. Spring component scanning picks up the `@Component` handler and `@EventSourced`
entity. Add `@ConditionalOnProperty` on each class:

```kotlin
@ConditionalOnProperty(prefix = "slices.boundedcontext", name = ["write.featurename.enabled"])
```

### Advanced Pattern

Explicit configuration required:

```kotlin
@ConditionalOnProperty(prefix = "slices.boundedcontext", name = ["write.featurename.enabled"])
@Configuration
internal class FeatureNameWriteSliceConfig {

    @Bean
    fun featureNameSliceState(): EntityModule<*, *> =
        EventSourcedEntityModule.autodetected(
            CompositeIdType::class.java,
            FeatureNameEventSourcedState::class.java
        )

    @Bean
    fun featureNameSlice(): CommandHandlingModule =
        CommandHandlingModule.named(CommandName::class.simpleName!!)
            .commandHandlers()
            .annotatedCommandHandlingComponent { FeatureNameCommandHandler() }
            .build()
}
```

## 13. Simple Pattern: Full Example

Source AF4 command: `BuildDwelling` handled by `Dwelling` aggregate.

```kotlin
package com.example.write.builddwelling

// imports...

////////////////////////////////////////////
////////// Domain
///////////////////////////////////////////

data class BuildDwelling(
    @get:JvmName("getDwellingId")
    val dwellingId: DwellingId,
    val creatureId: CreatureId,
    val costPerTroop: Resources,
)

private data class State(val isBuilt: Boolean)

private val initialState = State(isBuilt = false)

private fun decide(command: BuildDwelling, state: State): List<HeroesEvent> {
    if (state.isBuilt) return emptyList()
    return listOf(DwellingBuilt(
        dwellingId = command.dwellingId,
        creatureId = command.creatureId,
        costPerTroop = command.costPerTroop
    ))
}

private fun evolve(state: State, event: DwellingEvent): State = when (event) {
    is DwellingBuilt -> state.copy(isBuilt = true)
    else -> state
}

////////////////////////////////////////////
////////// Application
///////////////////////////////////////////

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["write.builddwelling.enabled"])
@EventSourced(tagKey = EventTags.DWELLING_ID)
private class BuildDwellingEventSourcedState private constructor(val state: State) {
    @EntityCreator
    constructor() : this(initialState)

    @EventSourcingHandler
    fun evolve(event: DwellingBuilt) = BuildDwellingEventSourcedState(evolve(state, event))
}

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["write.builddwelling.enabled"])
@Component
private class BuildDwellingCommandHandler {
    @CommandHandler
    fun handle(
        command: BuildDwelling,
        metadata: AxonMetadata,
        @InjectEntity(idProperty = EventTags.DWELLING_ID) eventSourced: BuildDwellingEventSourcedState,
        eventAppender: EventAppender
    ): CommandHandlerResult = resultOf {
        val events = decide(command, eventSourced.state)
        eventAppender.append(events.asEventMessages(metadata))
        events.toCommandResult()
    }
}

////////////////////////////////////////////
////////// Presentation
///////////////////////////////////////////

@ConditionalOnProperty(prefix = "slices.creaturerecruitment", name = ["write.builddwelling.enabled"])
@RestController
@RequestMapping("games/{gameId}")
private class BuildDwellingRestApi(private val commandGateway: CommandGateway) {
    @JvmRecord
    data class Body(val creatureId: String, val costPerTroop: Map<String, Int>)

    @PutMapping("/dwellings/{dwellingId}")
    fun putDwellings(
        @RequestHeader(Headers.PLAYER_ID) playerId: String,
        @PathVariable gameId: String,
        @PathVariable dwellingId: String,
        @RequestBody requestBody: Body
    ): CompletableFuture<ResponseEntity<Any>> {
        val command = BuildDwelling(DwellingId(dwellingId), CreatureId(requestBody.creatureId), Resources.of(requestBody.costPerTroop))
        val metadata = GameMetadata.with(GameId(gameId), PlayerId(playerId))
        return commandGateway.send(command.asCommandMessage(metadata))
            .resultAs(CommandHandlerResult::class.java)
            .toResponseEntity()
    }
}
```

## 14. Advanced Pattern: Multi-Tag DCB

Use when `decide()` needs state from events across multiple tags/streams.

Example: `RecruitCreature` needs dwelling events (by `dwellingId`) AND army events (by `armyId`).

Key additions vs simple pattern:

1. Command has a composite ID: `data class RecruitmentId(val dwellingId: DwellingId, val armyId: ArmyId)`
2. Entity uses `@EventSourcedEntity` (not `@EventSourced(tagKey=...)`)
3. Entity has `@EventCriteriaBuilder` companion method defining which tags/event-types to query
4. Handler class has NO `@Component` - registered via `@Configuration`
5. `@InjectEntity(idProperty = "recruitmentId")` - points to composite ID property

```kotlin
// In the command
data class RecruitCreature(...) {
    data class RecruitmentId(val dwellingId: DwellingId, val armyId: ArmyId)
    val recruitmentId = RecruitmentId(dwellingId, armyId)
}

// Entity
@EventSourcedEntity
private class RecruitCreatureEventSourcedState private constructor(val state: State) {
    @EntityCreator
    constructor() : this(initialState)

    // ... @EventSourcingHandler for each event type ...

    companion object {
        @JvmStatic
        @EventCriteriaBuilder
        fun resolveCriteria(id: RecruitCreature.RecruitmentId) =
            EventCriteria.either(
                EventCriteria.havingTags(Tag.of(EventTags.DWELLING_ID, id.dwellingId.raw))
                    .andBeingOneOfTypes(DwellingBuilt::class.java.getName(), AvailableCreaturesChanged::class.java.getName()),
                EventCriteria.havingTags(Tag.of(EventTags.ARMY_ID, id.armyId.raw))
                    .andBeingOneOfTypes(CreatureAddedToArmy::class.java.getName(), CreatureRemovedFromArmy::class.java.getName())
            )
    }
}

// Configuration (required for advanced pattern)
@Configuration
internal class RecruitCreatureWriteSliceConfig {
    @Bean
    fun recruitCreatureSliceState(): EntityModule<*, *> =
        EventSourcedEntityModule.autodetected(
            RecruitCreature.RecruitmentId::class.java,
            RecruitCreatureEventSourcedState::class.java
        )

    @Bean
    fun recruitCreatureSlice(): CommandHandlingModule =
        CommandHandlingModule.named(RecruitCreature::class.simpleName!!)
            .commandHandlers()
            .annotatedCommandHandlingComponent { RecruitCreatureCommandHandler() }
            .build()
}
```

## 15. Testing

### Unit Test (no Spring context)

```kotlin
internal class FeatureNameUnitTest {
    private lateinit var sliceUnderTest: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        val sliceConfig = FeatureNameWriteSliceConfig()
        sliceUnderTest = axonTestFixture(
            configSlice {
                registerEntity(sliceConfig.featureNameSliceState())
                registerCommandHandlingModule(sliceConfig.featureNameSlice())
            }
        )
    }

    @Test
    fun `given no prior activity, when command, then event`() {
        sliceUnderTest
            .given().noPriorActivity()
            .`when`().command(TheCommand(...))
            .then()
            .resultMessagePayload(CommandHandlerResult.Success)
            .events(ExpectedEvent(...))
    }
}
```

### Spring Integration Test

```kotlin
@TestPropertySource(properties = ["slices.boundedcontext.write.featurename.enabled=true"])
@HeroesAxonSpringBootTest  // project-specific annotation
internal class FeatureNameSpringSliceTest @Autowired constructor(configuration: AxonConfiguration) {
    private val sliceUnderTest: AxonTestFixture = springTestFixture(configuration)

    @Test
    fun `given prior event, when command, then new event`() {
        sliceUnderTest
            .given().event(PriorEvent(...))
            .`when`().command(TheCommand(...))
            .then()
            .resultMessagePayload(CommandHandlerResult.Success)
            .events(ExpectedEvent(...))
    }
}
```

### Common Test Scenarios

Cover these for each write slice:

- **Happy path**: No prior state -> command -> expected event(s)
- **Idempotency**: Same command twice -> no events on second attempt
- **Rule violations**: Invalid state -> command -> `CommandHandlerResult.Failure` and no events
- **State transitions**: Prior events change state -> command behaves differently
