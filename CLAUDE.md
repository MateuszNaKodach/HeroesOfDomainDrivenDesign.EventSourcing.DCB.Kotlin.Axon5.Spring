# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Educational Heroes of Might & Magic III implementation demonstrating Domain-Driven Design, Event Sourcing, and Event
Modeling patterns using Kotlin, Axon Framework 5, and Spring Boot 3.

## Build & Run Commands

```bash
# Install dependencies
./mvnw install -DskipTests

# Run all tests (Maven profile `test` is active by default, sets spring.profiles.active=test)
./mvnw test

# Run tests without Axon Server (uses testcontainers profile only, no axonserver profile)
./mvnw test -P '!test,test-without-axonserver'

# Run single test class
./mvnw test -Dtest=BuildDwellingUnitTest

# Run single test method
./mvnw test -Dtest=BuildDwellingUnitTest#given_not_built_dwelling_when_build_then_built

# Run application
./mvnw spring-boot:run

# Start Axon Server (required for running app or integration tests)
docker compose up
```

## Architecture

### Vertical Slice Architecture

Each feature is a self-contained "slice" in a single file with three sections:

```kotlin
////////////////////////////////////////////
////////// Domain
///////////////////////////////////////////
// Commands (data classes), State, decide() and evolve() functions

////////////////////////////////////////////
////////// Application
///////////////////////////////////////////
// @EventSourced entity, @CommandHandler

////////////////////////////////////////////
////////// Presentation
///////////////////////////////////////////
// @RestController and request DTOs
```

### Event Sourcing Pattern

- **decide(command, state) → List<Event>**: Pure function for command validation and event generation
- **evolve(state, event) → State**: Pure function for state reconstruction from events
- **@EventSourced(tagKey = ...)**: Defines consistency boundary via event tags
- **@InjectEntity(idProperty = ...)**: Injects reconstructed state into command handler
- **EventAppender**: Appends events with metadata to event store

### Bounded Contexts

Located in `com.dddheroes.heroesofddd.*`:

- `armies/` - Army management
- `creaturerecruitment/` - Dwelling building and creature recruitment
- `resourcespool/` - Resource management
- `scenario/` - Game scenarios
- `shared/` - Cross-cutting concerns (domain primitives, metadata, REST headers)

### Event Tags and Metadata

- **EventTags.kt**: Constants for consistency boundaries (`DWELLING_ID`, `ARMY_ID`, `RESOURCES_POOL_ID`)
- **GameMetadata**: Correlation context (`gameId`, `playerId`) attached to commands and events
- **@EventTag annotation**: On event properties to enable tag-based filtering in Axon Server

## Testing Patterns

Unit tests use Axon Test Fixtures with a Kotlin DSL (`Given { } When { } Then { }`):

```kotlin
private val sliceUnderTest = AxonTestFixture.with(
    UnitTestAxonApplication.configurer(
        { registerCommandHandlingModule { BuildDwellingWriteSliceConfig().buildDwellingSlice() } },
        { axonServerEnabled = false }
    ))

@Test
fun `given not built dwelling, when build, then built`() {
    sliceUnderTest.Scenario {
        Given {
            noPriorActivity()
        } When {
            command(BuildDwelling(...), gameMetadata)
        } Then {
            resultMessagePayload(CommandHandlerResult.Success)
            events(DwellingBuilt(...))
        }
    }
}
```

The DSL extensions are in `org.axonframework.test.fixture.AxonTestFixtureDsl.kt`.

## Axon Framework 5 Source Code

When you need to read Axon Framework 5 source code, look for it on this machine rather than reading compiled `.class`
files. The source is available at:

```
/Users/mateusznowak/GitRepos/AxonFramework/AxonFramework5
```

## Key Conventions

- All domain events implement `HeroesEvent` marker interface
- Module-specific events have their own interface (e.g., `DwellingEvent`)
- Slice files named as `FeatureName.Slice.kt`
- State classes are `internal`, commands are `public`
- REST endpoints follow: `games/{gameId}/resource/{resourceId}` pattern
- Player ID passed via `X-Player-ID` header

### Game-scoped singletons in REST API
When a bounded context has exactly one instance per game (e.g., Calendar, Astrologers), do NOT expose its domain ID as a path variable. Use a fixed path segment (e.g., `/calendar/` not `/calendars/{calendarId}/`) and derive the domain ID from `gameId` inside the controller (`CalendarId(gameId)`).

### Exhaustive `when (event)` in evolve()
- **When adding a new slice**, look at ALL events in the bounded context's sealed interface and
  decide for each one: does this event affect my slice's state? Every event needs an explicit branch.
- **Always** use `when (event: SealedType)` in `evolve()` — never `else ->`.
- List EVERY sealed subtype explicitly. No-op branches (`is SomeEvent -> state`) are required
  to document a conscious decision: "this event exists but doesn't affect my state here."
- This forces a compile error when a new event is added to the sealed interface,
  requiring a deliberate decision in every existing slice that uses it.
- **`@EventSourcingHandler` is ONLY added for events that mutate state** — branches that
  return `state` unchanged must NOT have a corresponding handler.
- **When a branch mutates state, add a test for that state transition.**
- For cross-module slices over non-sealed `HeroesEvent`: use overloaded `evolve()` functions
  (one per concrete event type) instead of a `when` expression.

### Spring
- Prefer constructor injection over field injection, even in tests.

### Test Profiles

Spring profiles for tests are activated automatically — do NOT add `@ActiveProfiles` to test classes.

The Maven profile `test` (active by default) sets `spring.profiles.active=test` via Surefire.
Spring Boot then expands the group defined in `application.yaml`:

| Spring profile activated  | Expands to                     |
|---------------------------|--------------------------------|
| `test`                    | `testcontainers`, `axonserver` |
| `test-without-axonserver` | `testcontainers`               |

- `testcontainers` — enables `TestcontainersConfiguration` beans (Axon Server + Postgres containers)
- `axonserver` — enables Axon Server connection configuration

`TestcontainersConfiguration` uses `@Configuration` (not `@TestConfiguration`) so Spring Boot's
component scan picks it up automatically. `@TestConfiguration` is intentionally excluded from
scanning and would require explicit `@Import` on every test class.

## Commit Conventions

See `/commit` skill (`.claude/skills/commit/SKILL.md`) for full commit conventions (Conventional Commits + Gitmoji).
