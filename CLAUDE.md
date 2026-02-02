# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Educational Heroes of Might & Magic III implementation demonstrating Domain-Driven Design, Event Sourcing, and Event
Modeling patterns using Kotlin, Axon Framework 5, and Spring Boot 3.

## Build & Run Commands

```bash
# Install dependencies
./mvnw install -DskipTests

# Run all tests
./mvnw test

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

Unit tests use Axon Test Fixtures with Given-When-Then:

```kotlin
private val sliceUnderTest = AxonTestFixture.with(
    UnitTestAxonApplication.configurer(
        { registerCommandHandlingModule { BuildDwellingWriteSliceConfig().buildDwellingSlice() } },
        { axonServerEnabled = false }
    ))

@Test
fun `given not built dwelling, when build, then built`() {
    sliceUnderTest
        .given().noPriorActivity()
        .`when`().command(BuildDwelling(...))
    .then().events(DwellingBuilt(...))
}
```

## Key Conventions

- All domain events implement `HeroesEvent` marker interface
- Module-specific events have their own interface (e.g., `DwellingEvent`)
- Slice files named as `FeatureName.Slice.kt`
- State classes are `internal`, commands are `public`
- REST endpoints follow: `games/{gameId}/resource/{resourceId}` pattern
- Player ID passed via `X-Player-ID` header
