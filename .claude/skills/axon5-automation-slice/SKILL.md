---
name: axon5-automation-slice
description: >
  Implement stateless automation slices (Event to Command) using Axon Framework 5, Vertical Slice Architecture,
  and Event Modeling patterns. An automation is: an Event Handler that reacts to an event by dispatching a command
  via CommandGateway — no internal state, no event sourcing.
  Use when: (1) implementing a new automation / event-to-command reactor in an AF5 project,
  (2) migrating/porting an automation from Axon Framework 4 (Java or Kotlin) to AF5,
  (3) user provides a specification, Event Modeling artifact, or natural language description of an
  event-to-command reaction and asks to implement it,
  (4) user says "implement", "create", "add", "migrate", "port" an automation, event handler,
  reactor, or event-to-command flow in an Axon Framework 5 / Vertical Slice Architecture project.
  Understands AF4 @EventHandler/@ProcessingGroup input as one possible source format.
---

# Axon Framework 5 Automation Slice Implementation

An automation reacts to an event by dispatching a command. No internal state, no event sourcing — just Event -> Command. In Event Modeling: the orange stripe.

## Step 0: Discover Target Project Conventions

Read the target project's CLAUDE.md and explore existing slices. Look for:

- File splitting conventions (single `.Slice.kt` vs separate files per class)
- Visibility modifiers (`private` on processor/configuration classes)
- Metadata handling (`GameMetadata`, `@MetadataValue`)
- Feature flag patterns (`@ConditionalOnProperty` prefix structure)
- `additional-spring-configuration-metadata.json` entries
- YAML config files (`application.yaml`, `application-test.yaml`)

## Step 1: Understand the Input

Extract these elements regardless of input format:

| Element                | What to extract                                                        |
|------------------------|------------------------------------------------------------------------|
| **Trigger event**      | Which event triggers the automation, and which condition filters it     |
| **Target command**     | Which command to dispatch, with what properties                        |
| **Mapping logic**      | How event properties map to command properties                         |
| **Strategy/calculator**| Any injectable strategy for deriving command properties from event data |
| **Metadata**           | Which metadata keys to propagate from event to command                 |

### Input: Axon Framework 4 Code

```
AF4                                  AF5
─────────────────────────────────    ────────────────────────────────────
@ProcessingGroup("name")             (not needed — Spring Boot auto-config)
@DisallowReplay                      (not needed in AF5)
@Component                           @Component
@EventHandler                        @EventHandler (different package)
commandGateway.sendAndWait(cmd, m)   commandGateway.send(cmd, metadata)
@MetaDataValue("key")                @MetadataValue("key")
Function<A, B> interface             fun interface Name : (A) -> B
```

**If requirements are unclear, ask the user before proceeding.**

## Step 2: Implement the Automation

An automation slice has up to 3 files (adapt to project conventions on splitting):

### File 1: Strategy Interface (if needed)

A `fun interface` for injectable logic deriving command properties from event data:

```kotlin
fun interface WeekSymbolCalculator : (MonthWeek) -> WeekSymbol
```

Skip if the mapping from event to command is trivial/direct.

### File 2: Configuration (if strategy exists)

```kotlin
@ConditionalOnProperty(prefix = "slices.{context}.automation", name = ["{automationname}.enabled"])
@Configuration
private class {AutomationName}Configuration {

    @Bean
    fun weekSymbolCalculator(): WeekSymbolCalculator =
        WeekSymbolCalculator { _ -> WeekSymbol(weekOf = CreatureId("angel"), growth = (1..5).random()) }
}
```

### File 3: Processor

```kotlin
@ConditionalOnProperty(prefix = "slices.{context}.automation", name = ["{automationname}.enabled"])
@Component
private class {AutomationName}Processor(
    private val commandGateway: CommandGateway,
    private val calculator: WeekSymbolCalculator  // if strategy exists
) {

    @EventHandler
    fun react(
        event: {TriggerEvent},
        @MetadataValue(GameMetadata.GAME_ID_KEY) gameId: String,
        @MetadataValue(GameMetadata.PLAYER_ID_KEY) playerId: String
    ) {
        if ({condition}) {
            val command = {TargetCommand}(...)
            val metadata = GameMetadata.with(GameId(gameId), PlayerId(playerId))
            commandGateway.send(command, metadata)
        }
    }
}
```

Key rules:

- **No domain/application section markers** — automation is too simple for layers
- **AF5 imports**: `org.axonframework.messaging.core.annotation.MetadataValue`, `org.axonframework.messaging.eventhandling.annotation.EventHandler`
- **`commandGateway.send(command, metadata)`** — not `sendAndWait`
- **Metadata propagation** — extract via `@MetadataValue`, reconstruct via `GameMetadata.with()`
- **Constructor injection** — for `CommandGateway` and any strategy interfaces

## Step 3: Add Feature Flag

Add `@ConditionalOnProperty` to both processor and configuration. Update ALL of:

- `application.yaml` — `slices.{context}.automation.{name}.enabled: true`
- `application-test.yaml` — `slices.{context}.automation.{name}.enabled: false`
- `META-INF/additional-spring-configuration-metadata.json` — add entry

## Step 4: Implement Tests

Spring Boot integration test with `AxonTestFixture` Kotlin DSL.

**Critical**: Enable BOTH the automation AND its target write slice in `@TestPropertySource`.

If the automation uses a strategy, override it with a deterministic `@TestConfiguration` bean.

### AxonTestFixture API for Automations

**Assert command dispatched** (async — must `await`):

```kotlin
fixture.Scenario {
    Given {
        event({TriggerEvent}(...), gameMetadata)
    } Then {
        await({ it.commands(expectedCommand) }, Duration.ofSeconds(5))
    }
}
```

**Assert no commands** (condition not met):

```kotlin
fixture.Scenario {
    Given {
        event({TriggerEvent}(... /* condition not met */), gameMetadata)
    } Then {
        await({ it.noCommands() }, Duration.ofSeconds(5))
    }
}
```

### Test Cases to Cover

1. **Happy path**: Event matching condition -> expected command dispatched
2. **Condition not met**: Event not matching condition -> no commands dispatched

## References

- [Automation Test Example](references/automation-test-example.md) — Complete working test with deterministic strategy override
