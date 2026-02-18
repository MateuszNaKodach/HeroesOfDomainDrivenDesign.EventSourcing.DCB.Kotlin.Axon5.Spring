# Migrating a Write Slice from Axon Framework 4 to Axon Framework 5

This document walks through the migration of the **Proclaim Week Symbol** write slice from the Astrologers bounded
context. It covers every concept that changed between AF4 and AF5, and clearly marks what comes from Axon Framework vs.
what is our own architectural style.

---

## Legend

| Label                    | Meaning                                                                             |
|--------------------------|-------------------------------------------------------------------------------------|
| **Axon Framework**       | Class/annotation provided by Axon Framework library                                 |
| **Project Style**        | Our own convention: vertical slice file layout, functional decide/evolve, REST, etc |
| **Spring / Spring Boot** | Spring Framework or Spring Boot annotation                                          |

---

## AF4 Source Structure (Java)

In AF4 the write slice was spread across multiple files following a traditional aggregate pattern:

```
astrologers/
  write/
    Astrologers.java              // @Aggregate with @CommandHandler + @EventSourcingHandler
    AstrologersId.java            // Value object (aggregate identifier)
    MonthWeek.java                // Value object
    WeekSymbol.java               // Value object
    proclaimweeksymbol/
      ProclaimWeekSymbol.java     // Command record with @TargetAggregateIdentifier
      OnlyOneSymbolPerWeek.java   // Domain rule class
  events/
    AstrologersEvent.java         // Marker interface
    WeekSymbolProclaimed.java     // Event record
```

## AF5 Target Structure (Kotlin)

In AF5, the write slice lives in a single `.Slice.kt` file with Domain / Application / Presentation sections:

```
astrologers/
  write/
    AstrologersId.kt             // Value object (Kotlin inline value class)
    MonthWeek.kt                 // Value object
    WeekSymbol.kt                // Value object
    proclaimweeksymbol/
      ProclaimWeekSymbol.Slice.kt // Single file: Command, State, decide(), evolve(), Entity, Handler, REST
  events/
    AstrologersEvent.kt          // Sealed interface
    WeekSymbolProclaimed.kt      // Event data class
```

> **Project Style**: The single-file vertical slice layout (`Domain` / `Application` / `Presentation` sections) is our
> convention, not an Axon requirement. Kotlin makes this practical thanks to file-private visibility (`private`
> top-level declarations) — the State, Entity, and Handler classes are invisible outside the file. In Java you would
> need separate files, but the principle remains the same.
>
> What matters is not the single file itself, but the fact that **there is no shared aggregate between slices**. In AF4,
> a single `Astrologers` aggregate class handled all commands for the bounded context. In AF5, each write slice has its
> own Entity with its own State — scoped to exactly the events that slice needs. Two slices in the same bounded context
> can read overlapping events from the same tags, but they each maintain independent state. This is what makes vertical
> slices truly independent and enables Dynamic Consistency Boundaries (DCB).

---

## Step-by-Step Migration

### 1. Aggregate Class -> Separated Entity + Command Handler

**AF4 (Axon Framework 4)**

In AF4, the aggregate class was the central concept. It combined state, command handling, and event sourcing in one
class:

```java
// All annotations below are from Axon Framework 4

import org.axonframework.commandhandling.CommandHandler;          // AF4
import org.axonframework.eventsourcing.EventSourcingHandler;      // AF4
import org.axonframework.modelling.command.AggregateIdentifier;   // AF4
import org.axonframework.modelling.command.CreationPolicy;        // AF4
import org.axonframework.spring.stereotype.Aggregate;             // AF4

@Aggregate                                                        // AF4 - marks this as an aggregate root
class Astrologers {

    @AggregateIdentifier                                          // AF4 - identifies the aggregate instance
    private AstrologersId astrologersId;
    private MonthWeek week;

    @CommandHandler                                               // AF4 - handles the command
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
        // AF4 - creates aggregate if not found
    void decide(ProclaimWeekSymbol command) {
        new OnlyOneSymbolPerWeek(command, week).verify();         // domain rule check
        apply(WeekSymbolProclaimed.event(...));                   // AF4 - AggregateLifecycle.apply()
    }

    @EventSourcingHandler
        // AF4 - rebuilds state from events
    void evolve(WeekSymbolProclaimed event) {
        this.astrologersId = new AstrologersId(event.astrologersId());
        this.week = new MonthWeek(event.month(), event.week());
    }
}
```

Key AF4 concepts:

- `@Aggregate` - Spring stereotype that registers the class as an event-sourced aggregate
- `@AggregateIdentifier` - marks the field used to route commands to the right aggregate instance
- `AggregateLifecycle.apply(event)` - publishes events from within the aggregate
- `@CreationPolicy(CREATE_IF_MISSING)` - auto-creates the aggregate on first command
- Command handling and state lived in the **same mutable class**

**AF5 (Axon Framework 5)**

In AF5, the aggregate concept is replaced by two separated components: an **Entity** (state container) and a
**Command Handler** (separate class). The entity is immutable and the command handler explicitly appends events.

```kotlin
// Entity — only holds state and evolves it
// Annotations below are from Axon Framework 5

import org.axonframework.eventsourcing.annotation.EventSourcingHandler   // AF5
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator // AF5
import org.axonframework.extension.spring.stereotype.EventSourced       // AF5 Spring extension

@EventSourced(tagKey = EventTags.ASTROLOGERS_ID)    // AF5 Spring - replaces @Aggregate + @AggregateIdentifier
private class ProclaimWeekSymbolEventSourcedState private constructor(val state: State) {

    @EntityCreator                                   // AF5 - replaces @CreationPolicy(CREATE_IF_MISSING)
    constructor() : this(initialState)

    @EventSourcingHandler                            // AF5 - same name, new package, returns NEW instance (immutable)
    fun evolve(event: WeekSymbolProclaimed) = ProclaimWeekSymbolEventSourcedState(evolve(state, event))
}
```

```kotlin
// Command Handler — separate class, receives entity via injection

import org.axonframework.messaging.commandhandling.annotation.CommandHandler // AF5
import org.axonframework.messaging.eventhandling.gateway.EventAppender       // AF5
import org.axonframework.modelling.annotation.InjectEntity                   // AF5

@Component                                                                   // Spring
private class ProclaimWeekSymbolCommandHandler {

    @CommandHandler                                                          // AF5 - same name, new package
    fun handle(
        command: ProclaimWeekSymbol,
        metadata: AxonMetadata,                                              // AF5 Kotlin extension
        @InjectEntity(idProperty = EventTags.ASTROLOGERS_ID) eventSourced: ProclaimWeekSymbolEventSourcedState, // AF5
        eventAppender: EventAppender                                         // AF5 - replaces AggregateLifecycle.apply()
    ): CommandHandlerResult = resultOf {                                      // Project Style
        val events = decide(command, eventSourced.state)                     // Project Style - functional decide()
        eventAppender.append(events.asEventMessages(metadata))               // AF5 - explicit event appending
        events.toCommandResult()                                             // Project Style
    }
}
```

**What changed (Axon Framework):**

| AF4 Concept                          | AF5 Replacement                                                        |
|--------------------------------------|------------------------------------------------------------------------|
| `@Aggregate`                         | `@EventSourced(tagKey = ...)` (Spring) or `@EventSourcedEntity` (core) |
| `@AggregateIdentifier`               | `tagKey` parameter on `@EventSourced`                                  |
| `@TargetAggregateIdentifier`         | `@InjectEntity(idProperty = ...)` on handler parameter                 |
| `AggregateLifecycle.apply(event)`    | `EventAppender.append(events)`                                         |
| `@CreationPolicy(CREATE_IF_MISSING)` | `@EntityCreator` constructor returning initial state                   |
| `@EventSourcingHandler` (mutates)    | `@EventSourcingHandler` (returns new immutable instance)               |
| `@CommandHandler` inside aggregate   | `@CommandHandler` in a separate class                                  |
| Command handler + state = one class  | Entity (state) and handler are separate classes                        |

**What changed (Project Style):**

| AF4 Style                                              | AF5 Style                                                          |
|--------------------------------------------------------|--------------------------------------------------------------------|
| Domain rule as separate class (`OnlyOneSymbolPerWeek`) | Inlined in `decide()` function                                     |
| Mutable aggregate state                                | Immutable `State` data class + functional `evolve()`               |
| Logic inside aggregate methods                         | Pure `decide(command, state)` and `evolve(state, event)` functions |

---

### 2. Command: No More @TargetAggregateIdentifier

**AF4**: The command carried `@TargetAggregateIdentifier` to route it to the correct aggregate:

```java
import org.axonframework.modelling.command.TargetAggregateIdentifier; // AF4

public record ProclaimWeekSymbol(
        @TargetAggregateIdentifier AstrologersId astrologersId,           // AF4 - routing annotation
        MonthWeek week,
        WeekSymbol symbol
) implements AstrologersCommand {

}
```

**AF5**: The command is a plain data class. Routing happens via `@InjectEntity(idProperty = ...)` on the handler side:

```kotlin
// No Axon annotations on the command at all — this is just a Kotlin data class
data class ProclaimWeekSymbol(
    @get:JvmName("getAstrologersId")   // Kotlin/JVM interop for inline value class
    val astrologersId: AstrologersId,
    val week: MonthWeek,
    val symbol: WeekSymbol,
)
```

The `idProperty = "astrologersId"` in `@InjectEntity` tells AF5 which command property to extract for entity lookup.
This replaced AF4's `@TargetAggregateIdentifier`.

---

### 3. Aggregate Identifier -> Event Tags

**AF4**: Used `@AggregateIdentifier` to define the aggregate's identity. Events were stored in a stream named by
aggregate type + id (e.g., `Astrologers-<uuid>`). The `AstrologersId` value object even prepended the type:

```java
public record AstrologersId(String raw) {

    private final static String AGGREGATE_TYPE = "Astrologers";

    public AstrologersId {
        raw = id.startsWith(AGGREGATE_TYPE + ":") ? id : AGGREGATE_TYPE + ":" + id;  // type prefix!
    }
}
```

**AF5**: Uses **event tags** instead of aggregate streams. Tags are key-value pairs attached to events, enabling
Dynamic Consistency Boundaries (DCB). No type prefix needed:

```kotlin
// Value object — no aggregate type prefix, just a simple wrapper
@JvmInline
value class AstrologersId(val raw: String) {       // Project Style - Kotlin inline value class
    init {
        require(raw.isNotBlank()) { "Astrologers ID cannot be empty" }
    }
}
```

```kotlin
// Event tag definition — Axon Framework annotation on the event
import org.axonframework.eventsourcing.annotation.EventTag   // AF5

sealed interface AstrologersEvent : HeroesEvent {
    @get:EventTag(EventTags.ASTROLOGERS_ID)                  // AF5 - tags events with astrologersId
    val astrologersId: AstrologersId
}
```

```kotlin
// Tag key constant — Project Style (centralized tag registry)
object EventTags {
    const val ASTROLOGERS_ID = "astrologersId"
}
```

The entity declares which tag defines its consistency boundary:

```kotlin
@EventSourced(tagKey = EventTags.ASTROLOGERS_ID)  // AF5 Spring - "load events with this tag"
```

**Key insight**: In AF4, the aggregate stream was rigid (one stream per aggregate type+id). In AF5, tags are flexible -
an entity can load events by any combination of tags, enabling cross-stream consistency (DCB).

---

### 4. Events: Sealed Interface + @EventTag

**AF4**:

```java
public sealed interface AstrologersEvent permits WeekSymbolProclaimed {

    String astrologersId();  // plain getter, no tag annotation
}

public record WeekSymbolProclaimed(
        String astrologersId,    // primitive types
        Integer month,
        Integer week,
        String weekOf,
        Integer growth
) implements AstrologersEvent {

}
```

**AF5**:

```kotlin
import org.axonframework.eventsourcing.annotation.EventTag  // AF5

sealed interface AstrologersEvent : HeroesEvent {           // Project Style - extends HeroesEvent marker
    @get:EventTag(EventTags.ASTROLOGERS_ID)                 // AF5 - declares this property as a tag
    val astrologersId: AstrologersId                        // typed value object instead of String
}

data class WeekSymbolProclaimed(
    override val astrologersId: AstrologersId,              // value object type
    val month: Int,
    val week: Int,
    val weekOf: CreatureId,                                 // value object type
    val growth: Int
) : AstrologersEvent
```

**What changed**: `@EventTag` (AF5) is added to event properties that should be indexed as tags in the event store. This
enables tag-based event filtering — the core mechanism behind DCB.

---

### 5. Domain Logic: From Aggregate Methods to Pure Functions

**AF4**: Business rules lived inside the aggregate or as separate rule objects:

```java
// Inside the aggregate
@CommandHandler
void decide(ProclaimWeekSymbol command) {
    new OnlyOneSymbolPerWeek(command, week).verify();  // rule object
    apply(WeekSymbolProclaimed.event(...));
}

// Separate DomainRule class
public record OnlyOneSymbolPerWeek(ProclaimWeekSymbol command, MonthWeek lastlyProclaimed) implements DomainRule {

    @Override
    public boolean isViolated() {
        return lastlyProclaimed != null && lastlyProclaimed.weekNumber() >= command.week().weekNumber();
    }
}
```

**AF5 (Project Style)**: Pure functions outside any class. No domain rule objects — the logic is inlined:

```kotlin
// Project Style — standalone pure function, no class needed
private fun decide(command: ProclaimWeekSymbol, state: State): List<HeroesEvent> {
    val lastProclaimed = state.lastProclaimed
    if (lastProclaimed != null && lastProclaimed.weekNumber >= command.week.weekNumber) {
        throw IllegalStateException("Only one symbol can be proclaimed per week")
    }
    return listOf(WeekSymbolProclaimed(...))
}

// Project Style — standalone pure function
private fun evolve(state: State, event: AstrologersEvent): State = when (event) {
    is WeekSymbolProclaimed -> state.copy(lastProclaimed = MonthWeek(event.month, event.week))
}
```

> This is entirely **Project Style**. Axon Framework 5 does not require functional `decide`/`evolve`. You could put
> all logic inside the command handler. We chose this pattern because pure functions are easier to test and reason
> about.

---

### 6. Immutable State

**AF4**: The aggregate was mutable. `@EventSourcingHandler` methods mutated fields in place:

```java

@EventSourcingHandler
void evolve(WeekSymbolProclaimed event) {
    this.astrologersId = new AstrologersId(event.astrologersId());
    this.week = new MonthWeek(event.month(), event.week());         // mutating field
}
```

**AF5**: The entity is immutable. `@EventSourcingHandler` returns a **new instance**:

```kotlin
// AF5 requirement: @EventSourcingHandler must return a new entity instance
@EventSourcingHandler
fun evolve(event: WeekSymbolProclaimed) = ProclaimWeekSymbolEventSourcedState(evolve(state, event))
```

The state itself is an immutable data class (Project Style):

```kotlin
private data class State(val lastProclaimed: MonthWeek?)

private val initialState = State(lastProclaimed = null)
```

---

### 7. Event Publishing: AggregateLifecycle.apply() -> EventAppender

**AF4**: Events were published from within the aggregate using a static method:

```java
import static org.axonframework.modelling.command.AggregateLifecycle.apply;  // AF4

apply(WeekSymbolProclaimed.event(command.astrologersId(),command.

week(),command.

symbol()));
```

**AF5**: Events are appended explicitly via `EventAppender`, injected into the command handler:

```kotlin
import org.axonframework.messaging.eventhandling.gateway.EventAppender       // AF5
import org.axonframework.extensions.kotlin.asEventMessages                   // AF5 Kotlin extension

eventAppender.append(events.asEventMessages(metadata))
```

This is a fundamental shift: events are no longer published from inside the entity — the handler controls when and how
events are appended to the store.

---

### 8. Package Changes

Almost every Axon annotation moved to a new package:

| Concept                      | AF4 Package                                 | AF5 Package                                                           |
|------------------------------|---------------------------------------------|-----------------------------------------------------------------------|
| `@CommandHandler`            | `org.axonframework.commandhandling`         | `org.axonframework.messaging.commandhandling.annotation`              |
| `@EventSourcingHandler`      | `org.axonframework.eventsourcing`           | `org.axonframework.eventsourcing.annotation`                          |
| `@Aggregate`                 | `org.axonframework.spring.stereotype`       | Removed. Use `@EventSourced` or `@EventSourcedEntity`                 |
| `@AggregateIdentifier`       | `org.axonframework.modelling.command`       | Removed. Use `tagKey` on `@EventSourced`                              |
| `@TargetAggregateIdentifier` | `org.axonframework.modelling.command`       | Removed. Use `@InjectEntity(idProperty = ...)`                        |
| `CommandGateway`             | `org.axonframework.commandhandling.gateway` | `org.axonframework.messaging.commandhandling.gateway`                 |
| N/A (new in AF5)             | —                                           | `org.axonframework.eventsourcing.annotation.EventTag`                 |
| N/A (new in AF5)             | —                                           | `org.axonframework.messaging.eventhandling.gateway.EventAppender`     |
| N/A (new in AF5)             | —                                           | `org.axonframework.modelling.annotation.InjectEntity`                 |
| N/A (new in AF5)             | —                                           | `org.axonframework.eventsourcing.annotation.reflection.EntityCreator` |

---

## Summary

The migration from AF4 to AF5 touches two layers:

**Axon Framework changes** (you must do these):

1. Replace `@Aggregate` with `@EventSourced(tagKey = ...)` or `@EventSourcedEntity`
2. Replace `@AggregateIdentifier` with tag-based identity (`@EventTag` on events, `tagKey` on entity)
3. Remove `@TargetAggregateIdentifier` from commands, use `@InjectEntity(idProperty = ...)` on handler
4. Replace `AggregateLifecycle.apply()` with `EventAppender.append()`
5. Replace `@CreationPolicy(CREATE_IF_MISSING)` with `@EntityCreator` constructor
6. Make `@EventSourcingHandler` return new entity instances (immutability)
7. Separate command handler into its own class (no longer inside the aggregate/entity)
8. Update all import packages

**Project Style choices** (our conventions, not required by AF5):

1. Single `.Slice.kt` file with Domain / Application / Presentation sections
2. Pure `decide(command, state)` and `evolve(state, event)` functions
3. Immutable `State` data class with `initialState` companion
4. `CommandHandlerResult` return type with `resultOf { }` wrapper
5. `@ConditionalOnProperty` feature flags per slice
6. REST controller in the same file
7. Kotlin inline value classes for identifiers
